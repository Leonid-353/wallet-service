package com.github.leonid353.buffer;

import com.github.leonid353.common.exception.BufferOverflowException;
import com.github.leonid353.common.exception.InsufficientFundsException;
import com.github.leonid353.common.exception.WalletNotFoundException;
import com.github.leonid353.transaction.dto.TransactionStatus;
import com.github.leonid353.transaction.dto.TransactionType;
import com.github.leonid353.transaction.model.Transaction;
import com.github.leonid353.transaction.repository.TransactionRepository;
import com.github.leonid353.wallet.model.Wallet;
import com.github.leonid353.wallet.repository.WalletRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletTransactionBuffer {

    final WalletRepository walletRepository;
    final TransactionRepository transactionRepository;

    final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<PendingOp>> buffers = new ConcurrentHashMap<>();

    @Value("${wallet.processing.buffer.max-buffer-size}")
    int maxBufferSize;

    @Value("${wallet.processing.max-retries}")
    int maxRetries;

    @Value("${wallet.processing.retry-delay-ms}")
    long retryDelayMs;

    @Value("${wallet.processing.buffer.empty-log-interval:500}")
    int emptyLogInterval;

    int emptyFlushCount = 0;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction saveTransaction(UUID walletId, BigDecimal amount, TransactionType type) {
        Wallet wallet = walletRepository.getReferenceById(walletId);
        Transaction tx = new Transaction();
        tx.setWallet(wallet);
        tx.setAmount(amount.abs());
        tx.setType(type);
        tx.setStatus(TransactionStatus.PENDING);
        Transaction saved = transactionRepository.save(tx);
        log.debug("Транзакция сохранена как PENDING: id={}, walletId={}, сумма={}, тип={}",
                saved.getId(), walletId, amount.abs(), type);
        return saved;
    }

    public CompletableFuture<UUID> addTransaction(UUID walletId, BigDecimal amount, TransactionType type) {
        Transaction tx = saveTransaction(walletId, amount, type);

        ConcurrentLinkedQueue<PendingOp> queue = buffers.computeIfAbsent(walletId, k -> new ConcurrentLinkedQueue<>());

        if (queue.size() >= maxBufferSize) {
            log.error("Буфер переполнен для кошелька: walletId={}, размер={}", walletId, queue.size());
            throw new BufferOverflowException("Буфер переполнен для кошелька: " + walletId);
        }

        CompletableFuture<UUID> future = new CompletableFuture<>();
        queue.add(new PendingOp(amount, future, tx.getId()));
        log.debug("Операция добавлена в буфер: walletId={}, transactionId={}, очередь={}",
                walletId, tx.getId(), queue.size());
        return future;
    }

    @Async
    @Transactional
    @Scheduled(fixedDelayString = "${wallet.processing.buffer.flush-interval-ms}")
    public void flushBuffers() {
        if (buffers.isEmpty()) {
            emptyFlushCount++;
            if (emptyFlushCount % emptyLogInterval == 0) {
                log.debug("Flush буферов: очереди пусты (проверка №{})", emptyFlushCount);
            }
            return;
        }
        emptyFlushCount = 0;
        log.debug("Запуск flush буферов, активных кошельков: {}", buffers.size());
        buffers.forEach((walletId, queue) -> {
            List<PendingOp> batch = drainQueue(queue);
            if (batch.isEmpty()) {
                return;
            }
            log.debug("Flush кошелька: walletId={}, операций в пакете={}", walletId, batch.size());
            boolean success = flushWithRetry(walletId, batch);
            if (!success) {
                queue.addAll(batch);
                log.warn("Flush не удался для walletId={}, операции возвращены в очередь", walletId);
            }
        });
    }

    private List<PendingOp> drainQueue(ConcurrentLinkedQueue<PendingOp> queue) {
        List<PendingOp> batch = new ArrayList<>();
        PendingOp op;
        while ((op = queue.poll()) != null) {
            batch.add(op);
        }
        return batch;
    }

    private boolean flushWithRetry(UUID walletId, List<PendingOp> batch) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return tryFlush(walletId, batch);
            } catch (Exception e) {
                log.warn("Попытка flush {}/{} не удалась для walletId={}: {}",
                        attempt, maxRetries, walletId, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        log.error("Все попытки flush исчерпаны для walletId={}, операции будут возвращены в очередь", walletId);
        return false;
    }

    private boolean tryFlush(UUID walletId, List<PendingOp> batch) {
        BigDecimal totalDelta = batch.stream()
                .map(PendingOp::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("Попытка обновления баланса: walletId={}, суммарное изменение={}", walletId, totalDelta);

        try {
            int updated = walletRepository.updateBalance(walletId, totalDelta);

            if (updated == 0) {
                log.debug("Batch не прошел целиком, обрабатываем частично: walletId={}", walletId);
                processPartialBatch(walletId, batch);
                return true;
            }

            markTransactionsCompleted(batch);
            log.info("Flush успешен: walletId={}, операций={}, сумма={}",
                    walletId, batch.size(), totalDelta);
            batch.forEach(op -> op.future().complete(op.transactionId()));
            return true;

        } catch (DataIntegrityViolationException e) {
            log.warn("Недостаточно средств при flush: walletId={}, сумма={}", walletId, totalDelta);
            processPartialBatch(walletId, batch);
            return true;
        }
    }

    private void processPartialBatch(UUID walletId, List<PendingOp> batch) {
        Wallet wallet = walletRepository.findById(walletId).orElse(null);
        if (wallet == null) {
            markTransactionsFailed(batch, "Кошелёк не найден");
            batch.forEach(op -> op.future().completeExceptionally(new WalletNotFoundException(walletId)));
            return;
        }

        // Разделяем на депозиты (всегда проходят) и снятия (требуют проверки)
        List<PendingOp> deposits = batch.stream()
                .filter(op -> op.amount().compareTo(BigDecimal.ZERO) >= 0)
                .toList();
        List<PendingOp> withdraws = batch.stream()
                .filter(op -> op.amount().compareTo(BigDecimal.ZERO) < 0)
                .toList();

        List<PendingOp> completed = new ArrayList<>();
        List<PendingOp> failed = new ArrayList<>();

        // Депозиты всегда проходят
        completed.addAll(deposits);

        // Снятия проверяем по балансу (с учетом депозитов)
        BigDecimal depositsSum = deposits.stream()
                .map(PendingOp::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal runningBalance = wallet.getBalance().add(depositsSum);

        for (PendingOp op : withdraws) {
            BigDecimal newBalance = runningBalance.add(op.amount());
            if (newBalance.compareTo(BigDecimal.ZERO) >= 0) {
                completed.add(op);
                runningBalance = newBalance;
            } else {
                failed.add(op);
            }
        }

        // Один UPDATE на все успешные операции
        if (!completed.isEmpty()) {
            BigDecimal totalDelta = completed.stream()
                    .map(PendingOp::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            walletRepository.updateBalance(walletId, totalDelta);
            markTransactionsCompleted(completed);
            completed.forEach(op -> op.future().complete(op.transactionId()));
            log.info("Частичный flush: walletId={}, выполнено={} (депозитов={}, снятий={}), пропущено снятий={}",
                    walletId, completed.size(), deposits.size(), completed.size() - deposits.size(), failed.size());
        }

        if (!failed.isEmpty()) {
            markTransactionsFailed(failed, "Недостаточно средств");
            failed.forEach(op -> op.future().completeExceptionally(
                    new InsufficientFundsException("Недостаточно средств на кошельке")));
        }
    }

    private void markTransactionsCompleted(List<PendingOp> batch) {
        List<UUID> ids = batch.stream().map(PendingOp::transactionId).toList();
        transactionRepository.markAsCompleted(ids, Instant.now());
        log.debug("Транзакции помечены как COMPLETED: ids={}", ids);
    }

    private void markTransactionsFailed(List<PendingOp> batch, String reason) {
        List<UUID> ids = batch.stream().map(PendingOp::transactionId).toList();
        transactionRepository.markAsFailed(ids, reason, Instant.now());
        log.debug("Транзакции помечены как FAILED: ids={}, причина={}", ids, reason);
    }

    private record PendingOp(BigDecimal amount, CompletableFuture<UUID> future, UUID transactionId) {
    }
}
