package com.github.leonid353.wallet.repository;

import com.github.leonid353.wallet.model.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class WalletRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    @Autowired
    private WalletRepository walletRepository;
    private UUID walletId;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();

        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("100.00"));
        wallet = walletRepository.save(wallet);
        walletId = wallet.getId();
    }

    @Test
    void shouldUpdateBalanceWithDeposit() {
        int updated = walletRepository.updateBalance(walletId, new BigDecimal("50.00"));
        assertThat(updated).isEqualTo(1);

        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void shouldUpdateBalanceWithWithdraw() {
        int updated = walletRepository.updateBalance(walletId, new BigDecimal("-30.00"));
        assertThat(updated).isEqualTo(1);

        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void shouldNotUpdateBalanceWhenInsufficientFunds() {
        int updated = walletRepository.updateBalance(walletId, new BigDecimal("-200.00"));
        assertThat(updated).isEqualTo(0);

        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldNotUpdateBalanceWhenWalletNotFound() {
        int updated = walletRepository.updateBalance(UUID.randomUUID(), new BigDecimal("10.00"));
        assertThat(updated).isEqualTo(0);
    }

    @Test
    void shouldLockWallet() {
        Optional<Wallet> locked = walletRepository.findByIdWithLock(walletId);
        assertThat(locked).isPresent();
        assertThat(locked.get().getId()).isEqualTo(walletId);
    }

    @Test
    void shouldReturnEmptyWhenLockingNonExistentWallet() {
        Optional<Wallet> locked = walletRepository.findByIdWithLock(UUID.randomUUID());
        assertThat(locked).isEmpty();
    }

    @Test
    void shouldCheckWalletExists() {
        assertThat(walletRepository.existsById(walletId)).isTrue();
        assertThat(walletRepository.existsById(UUID.randomUUID())).isFalse();
    }
}
