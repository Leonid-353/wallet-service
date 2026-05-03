package com.github.leonid353.wallet.buffer;

import com.github.leonid353.buffer.TransactionRecoveryService;
import com.github.leonid353.transaction.dto.TransactionStatus;
import com.github.leonid353.transaction.dto.TransactionType;
import com.github.leonid353.transaction.model.Transaction;
import com.github.leonid353.transaction.repository.TransactionRepository;
import com.github.leonid353.wallet.model.Wallet;
import com.github.leonid353.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class TransactionRecoveryServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    @Autowired
    private TransactionRecoveryService recoveryService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private WalletRepository walletRepository;
    @PersistenceContext
    private EntityManager em;
    private Wallet wallet;

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

        wallet = new Wallet();
        wallet.setBalance(new BigDecimal("100000.00"));
        wallet = walletRepository.save(wallet);
    }

    @Test
    @Transactional
    void shouldRecoverStuckPendingTransaction() {
        Transaction stuckTx = createPendingTransaction(0);
        setCreatedAtInPast(stuckTx.getId(), 10);

        recoveryService.recoverStuckTransactions();

        Transaction recovered = transactionRepository.findById(stuckTx.getId()).orElseThrow();
        assertThat(recovered.getRetryCount()).isEqualTo(1);
    }

    @Test
    @Transactional
    void shouldMarkAsFailedAfterMaxRetries() throws Exception {
        Transaction stuckTx = createPendingTransaction(3);
        setCreatedAtInPast(stuckTx.getId(), 10);

        recoveryService.recoverStuckTransactions();

        Thread.sleep(500);

        Transaction failed = transactionRepository.findById(stuckTx.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void shouldNotTouchRecentPendingTransactions() {
        Transaction recentTx = createPendingTransaction(0);

        recoveryService.recoverStuckTransactions();

        Transaction untouched = transactionRepository.findById(recentTx.getId()).orElseThrow();
        assertThat(untouched.getRetryCount()).isEqualTo(0);
    }

    private Transaction createPendingTransaction(int retryCount) {
        Transaction tx = new Transaction();
        tx.setWallet(wallet);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setType(TransactionType.DEPOSIT);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setRetryCount(retryCount);
        return transactionRepository.save(tx);
    }

    private void setCreatedAtInPast(UUID txId, int minutes) {
        em.createNativeQuery(
                        "UPDATE transactions SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", Instant.now().minus(minutes, ChronoUnit.MINUTES))
                .setParameter("id", txId)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
