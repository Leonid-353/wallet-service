package com.github.leonid353.wallet.buffer;

import com.github.leonid353.buffer.WalletTransactionBuffer;
import com.github.leonid353.transaction.dto.TransactionStatus;
import com.github.leonid353.transaction.dto.TransactionType;
import com.github.leonid353.transaction.model.Transaction;
import com.github.leonid353.transaction.repository.TransactionRepository;
import com.github.leonid353.wallet.model.Wallet;
import com.github.leonid353.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class WalletTransactionBufferTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    @Autowired
    private WalletTransactionBuffer buffer;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    private UUID walletId;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();

        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("1000000.00"));
        wallet = walletRepository.saveAndFlush(wallet);
        walletId = wallet.getId();
    }

    @Test
    void shouldProcess1000DepositsOnSameWallet() throws Exception {
        int requests = 1000;
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        Instant start = Instant.now();

        for (int i = 0; i < requests; i++) {
            futures.add(buffer.addTransaction(walletId, new BigDecimal("1.00"), TransactionType.DEPOSIT));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        long completedCount = futures.stream()
                .filter(CompletableFuture::isDone)
                .filter(f -> !f.isCompletedExceptionally())
                .count();
        assertThat(completedCount).isEqualTo(requests);

        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("1001000.00"));

        List<Transaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(requests);
        assertThat(transactions).allMatch(tx -> tx.getStatus() == TransactionStatus.COMPLETED);

        System.out.printf("1000 депозитов обработано за %d мс (%.0f RPS)%n",
                duration.toMillis(),
                requests / (duration.toMillis() / 1000.0));
    }

    @Test
    void shouldProcess5000RequestsOnSameWallet() throws Exception {
        int requests = 5000;
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        Instant start = Instant.now();

        for (int i = 0; i < requests; i++) {
            futures.add(buffer.addTransaction(walletId, new BigDecimal("1.00"), TransactionType.DEPOSIT));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        long completedCount = futures.stream()
                .filter(CompletableFuture::isDone)
                .filter(f -> !f.isCompletedExceptionally())
                .count();
        assertThat(completedCount).isEqualTo(requests);

        System.out.printf("5000 запросов обработано за %d мс (%.0f RPS)%n",
                duration.toMillis(),
                requests / (duration.toMillis() / 1000.0));
    }

    @Test
    void shouldProcess10000RequestsOnSameWallet() throws Exception {
        int requests = 10000;
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        Instant start = Instant.now();

        for (int i = 0; i < requests; i++) {
            futures.add(buffer.addTransaction(walletId, new BigDecimal("1.00"), TransactionType.DEPOSIT));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        long completedCount = futures.stream()
                .filter(CompletableFuture::isDone)
                .filter(f -> !f.isCompletedExceptionally())
                .count();
        assertThat(completedCount).isEqualTo(requests);

        System.out.printf("10000 запросов обработано за %d мс (%.0f RPS)%n",
                duration.toMillis(),
                requests / (duration.toMillis() / 1000.0));
    }

    @Test
    void shouldProcess10000MixedRequestsWithPeriodicLoad() throws Exception {
        int totalRequests = 10000;
        int batchSize = 1000;
        int intervalMs = 1000; // каждую секунду
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        Instant start = Instant.now();

        for (int second = 0; second < totalRequests / batchSize; second++) {
            Instant batchStart = Instant.now();

            // Отправляем 1000 смешанных запросов
            for (int i = 0; i < batchSize / 2; i++) {
                futures.add(buffer.addTransaction(walletId, new BigDecimal("10.00"), TransactionType.DEPOSIT));
            }
            for (int i = 0; i < batchSize / 2; i++) {
                futures.add(buffer.addTransaction(walletId, new BigDecimal("-5.00"), TransactionType.WITHDRAW));
            }

            // Ждем до следующей секунды
            long elapsed = Duration.between(batchStart, Instant.now()).toMillis();
            if (elapsed < intervalMs) {
                Thread.sleep(intervalMs - elapsed);
            }
        }

        // Ждем завершения всех операций
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        long completedCount = futures.stream()
                .filter(CompletableFuture::isDone)
                .filter(f -> !f.isCompletedExceptionally())
                .count();
        assertThat(completedCount).isEqualTo(totalRequests);

        // Проверяем баланс: 5000 * 10 - 5000 * 5 = 25000
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("1025000.00"));

        System.out.printf("10000 смешанных запросов (по 1000/сек): обработано за %d мс (%.0f RPS)%n",
                duration.toMillis(),
                totalRequests / (duration.toMillis() / 1000.0));
    }

    @Test
    void shouldProcessMixedDepositsAndWithdraws() throws Exception {
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        Instant start = Instant.now();

        for (int i = 0; i < 500; i++) {
            futures.add(buffer.addTransaction(walletId, new BigDecimal("100.00"), TransactionType.DEPOSIT));
        }

        for (int i = 0; i < 500; i++) {
            futures.add(buffer.addTransaction(walletId, new BigDecimal("-10.00"), TransactionType.WITHDRAW));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        long completedCount = futures.stream()
                .filter(CompletableFuture::isDone)
                .filter(f -> !f.isCompletedExceptionally())
                .count();
        assertThat(completedCount).isEqualTo(1000);

        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("1045000.00"));

        System.out.printf("1000 смешанных операций обработано за %d мс (%.0f RPS)%n",
                duration.toMillis(),
                1000 / (duration.toMillis() / 1000.0));
    }

    @Test
    void shouldFailWithInsufficientFunds() throws Exception {
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        Instant start = Instant.now();

        for (int i = 0; i < 10; i++) {
            futures.add(buffer.addTransaction(walletId, new BigDecimal("-200000.00"), TransactionType.WITHDRAW));
        }

        buffer.flushBuffers();

        Thread.sleep(500);

        Instant end = Instant.now();

        long failedCount = futures.stream()
                .filter(CompletableFuture::isCompletedExceptionally)
                .count();
        assertThat(failedCount).isGreaterThan(0);

        long completedCount = futures.stream()
                .filter(CompletableFuture::isDone)
                .filter(f -> !f.isCompletedExceptionally())
                .count();
        assertThat(failedCount + completedCount).isEqualTo(10);

        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        System.out.printf("Тест недостаточности средств: %d упало, %d выполнено за %d мс%n",
                failedCount, completedCount, Duration.between(start, end).toMillis());
    }
}
