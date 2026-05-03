package com.github.leonid353.transaction.repository;

import com.github.leonid353.transaction.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Transaction t
            SET t.status = 'COMPLETED', t.completedAt = :completedAt
            WHERE t.id IN :ids
            """)
    int markAsCompleted(@Param("ids") List<UUID> ids,
                        @Param("completedAt") Instant completedAt);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Transaction t
            SET t.status = 'FAILED', t.errorMessage = :reason, t.completedAt = :failedAt
            WHERE t.id IN :ids
            """)
    int markAsFailed(@Param("ids") List<UUID> ids,
                     @Param("reason") String reason,
                     @Param("failedAt") Instant failedAt);

    @Query("""
            SELECT t
            FROM Transaction t
            WHERE t.status = 'PENDING' AND t.createdAt < :threshold
            """)
    List<Transaction> findStuckPending(@Param("threshold") Instant threshold);

    List<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}
