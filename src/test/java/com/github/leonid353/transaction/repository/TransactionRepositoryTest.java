package com.github.leonid353.transaction.repository;

import com.github.leonid353.transaction.dto.TransactionStatus;
import com.github.leonid353.transaction.dto.TransactionType;
import com.github.leonid353.transaction.model.Transaction;
import com.github.leonid353.wallet.model.Wallet;
import com.github.leonid353.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    @Autowired
    private TestEntityManager em;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private WalletRepository walletRepository;
    private Wallet wallet;
    private Transaction tx1;
    private Transaction tx2;

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
        wallet.setBalance(new BigDecimal("1000.00"));
        wallet = walletRepository.save(wallet);

        tx1 = new Transaction();
        tx1.setWallet(wallet);
        tx1.setAmount(new BigDecimal("100.00"));
        tx1.setType(TransactionType.DEPOSIT);
        tx1.setStatus(TransactionStatus.PENDING);
        tx1 = transactionRepository.save(tx1);

        tx2 = new Transaction();
        tx2.setWallet(wallet);
        tx2.setAmount(new BigDecimal("50.00"));
        tx2.setType(TransactionType.WITHDRAW);
        tx2.setStatus(TransactionStatus.PENDING);
        tx2 = transactionRepository.save(tx2);
    }

    @Test
    void shouldMarkAsCompleted() {
        int updated = transactionRepository.markAsCompleted(
                List.of(tx1.getId(), tx2.getId()), Instant.now());
        assertThat(updated).isEqualTo(2);

        Transaction updatedTx1 = transactionRepository.findById(tx1.getId()).orElseThrow();
        assertThat(updatedTx1.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(updatedTx1.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldMarkAsFailed() {
        int updated = transactionRepository.markAsFailed(
                List.of(tx1.getId()), "Недостаточно средств", Instant.now());
        assertThat(updated).isEqualTo(1);

        Transaction failed = transactionRepository.findById(tx1.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("Недостаточно средств");
    }

    @Test
    void shouldFindStuckPendingTransactions() {
        tx1.setCreatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        em.persistAndFlush(tx1);

        em.getEntityManager()
                .createNativeQuery("UPDATE transactions SET created_at = ? WHERE id = ?")
                .setParameter(1, Instant.now().minus(10, ChronoUnit.MINUTES))
                .setParameter(2, tx1.getId())
                .executeUpdate();

        Instant threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<Transaction> stuck = transactionRepository.findStuckPending(threshold);

        assertThat(stuck).hasSize(1);
    }

    @Test
    void shouldNotFindRecentPendingTransactions() {
        Instant threshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<Transaction> stuck = transactionRepository.findStuckPending(threshold);

        assertThat(stuck).isEmpty();
    }
}
