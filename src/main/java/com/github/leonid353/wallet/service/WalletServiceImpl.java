package com.github.leonid353.wallet.service;

import com.github.leonid353.buffer.WalletTransactionBuffer;
import com.github.leonid353.common.exception.WalletNotFoundException;
import com.github.leonid353.transaction.dto.TransactionResponse;
import com.github.leonid353.transaction.dto.TransactionStatus;
import com.github.leonid353.transaction.dto.TransactionType;
import com.github.leonid353.transaction.model.Transaction;
import com.github.leonid353.transaction.repository.TransactionRepository;
import com.github.leonid353.wallet.dto.CreateWalletRequest;
import com.github.leonid353.wallet.dto.OperationRequest;
import com.github.leonid353.wallet.dto.OperationType;
import com.github.leonid353.wallet.dto.WalletResponse;
import com.github.leonid353.wallet.model.Wallet;
import com.github.leonid353.wallet.repository.WalletRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletServiceImpl implements WalletService {

    final WalletRepository walletRepository;
    final TransactionRepository transactionRepository;
    final WalletTransactionBuffer buffer;

    @Override
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        log.debug("Создание кошелька с начальным балансом: {}", request.getInitialBalance());
        Wallet wallet = new Wallet();
        wallet.setBalance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO);
        Wallet saved = walletRepository.save(wallet);
        log.debug("Кошелёк сохранён: id={}, баланс={}", saved.getId(), saved.getBalance());

        if (request.getInitialBalance() != null && request.getInitialBalance().compareTo(BigDecimal.ZERO) > 0) {
            Transaction tx = new Transaction();
            tx.setWallet(saved);
            tx.setAmount(request.getInitialBalance());
            tx.setType(TransactionType.DEPOSIT);
            tx.setStatus(TransactionStatus.COMPLETED);
            tx.setCompletedAt(Instant.now());
            transactionRepository.save(tx);
            log.debug("Начальная транзакция сохранена: id={}, сумма={}", tx.getId(), tx.getAmount());
        }

        return WalletResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {
        log.debug("Получение кошелька: id={}", walletId);
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> {
                    log.warn("Кошелёк не найден: id={}", walletId);
                    return new WalletNotFoundException(walletId);
                });
        return WalletResponse.from(wallet);
    }

    @Override
    public CompletableFuture<TransactionResponse> processOperation(OperationRequest request) {
        log.debug("Обработка операции: walletId={}, тип={}, сумма={}",
                request.getWalletId(), request.getOperationType(), request.getAmount());

        if (!walletRepository.existsById(request.getWalletId())) {
            log.warn("Кошелёк не найден для операции: walletId={}", request.getWalletId());
            throw new WalletNotFoundException(request.getWalletId());
        }

        BigDecimal amount = request.getOperationType() == OperationType.DEPOSIT
                ? request.getAmount()
                : request.getAmount().negate();

        TransactionType type = request.getOperationType() == OperationType.DEPOSIT
                ? TransactionType.DEPOSIT
                : TransactionType.WITHDRAW;

        log.debug("Добавление в буфер: walletId={}, сумма={}, тип={}", request.getWalletId(), amount, type);
        return buffer.addTransaction(request.getWalletId(), amount, type)
                .thenApply(txId -> {
                    log.debug("Транзакция создана в буфере: id={}, walletId={}", txId, request.getWalletId());
                    return new TransactionResponse(
                            txId,
                            request.getWalletId(),
                            request.getAmount(),
                            type,
                            TransactionStatus.PENDING);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(UUID walletId, Pageable pageable) {
        log.debug("Получение транзакций: walletId={}, страница={}, размер={}",
                walletId, pageable.getPageNumber(), pageable.getPageSize());

        if (!walletRepository.existsById(walletId)) {
            log.warn("Кошелёк не найден для истории транзакций: walletId={}", walletId);
            throw new WalletNotFoundException(walletId);
        }

        List<TransactionResponse> transactions = transactionRepository
                .findByWalletIdOrderByCreatedAtDesc(walletId, pageable).stream()
                .map(TransactionResponse::from)
                .toList();
        log.debug("Найдено {} транзакций для walletId={}", transactions.size(), walletId);
        return transactions;
    }
}
