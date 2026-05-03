package com.github.leonid353.buffer;

import com.github.leonid353.transaction.dto.TransactionStatus;
import com.github.leonid353.transaction.dto.TransactionType;
import com.github.leonid353.transaction.model.Transaction;
import com.github.leonid353.transaction.repository.TransactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransactionRecoveryService {

    final TransactionRepository transactionRepository;
    final WalletTransactionBuffer buffer;

    @Value("${wallet.processing.recovery.max-retries}")
    int recoveryMaxRetries;

    @Value("${wallet.processing.recovery.stuck-threshold-minutes}")
    long stuckThresholdMinutes;

    @Scheduled(fixedDelayString = "${wallet.processing.recovery.interval-ms}")
    @Transactional
    public void recoverStuckTransactions() {
        log.debug("Запуск восстановления зависших транзакций");
        Instant threshold = Instant.now().minus(stuckThresholdMinutes, ChronoUnit.MINUTES);
        List<Transaction> stuck = transactionRepository.findStuckPending(threshold);

        if (stuck.isEmpty()) {
            log.debug("Зависших транзакций не найдено");
            return;
        }

        log.warn("Найдено {} зависших транзакций старше {} минут", stuck.size(), stuckThresholdMinutes);

        for (Transaction tx : stuck) {
            if (tx.getRetryCount() >= recoveryMaxRetries) {
                tx.setStatus(TransactionStatus.FAILED);
                tx.setErrorMessage("Исчерпаны попытки восстановления");
                tx.setCompletedAt(Instant.now());
                transactionRepository.save(tx);
                log.warn("Транзакция помечена как FAILED: id={}, walletId={}", tx.getId(), tx.getWallet().getId());
            } else {
                tx.setRetryCount(tx.getRetryCount() + 1);
                transactionRepository.save(tx);
                log.info("Повторная попытка транзакции: id={}, walletId={}, попытка={}",
                        tx.getId(), tx.getWallet().getId(), tx.getRetryCount());
                BigDecimal amount = tx.getType() == TransactionType.DEPOSIT
                        ? tx.getAmount()
                        : tx.getAmount().negate();
                buffer.addTransaction(tx.getWallet().getId(), amount, tx.getType());
            }
        }
    }
}
