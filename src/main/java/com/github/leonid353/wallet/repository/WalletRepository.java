package com.github.leonid353.wallet.repository;

import com.github.leonid353.wallet.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w
            FROM Wallet w
            WHERE w.id = :walletId
            """)
    Optional<Wallet> findByIdWithLock(@Param("walletId") UUID walletId);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Wallet w
            SET w.balance = w.balance + :amount
            WHERE w.id = :walletId
            AND (:amount >= 0 OR w.balance + :amount >= 0)
            """)
    int updateBalance(@Param("walletId") UUID walletId,
                      @Param("amount") BigDecimal amount);

    boolean existsById(@NonNull UUID walletId);
}
