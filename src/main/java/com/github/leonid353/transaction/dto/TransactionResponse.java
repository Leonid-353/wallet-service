package com.github.leonid353.transaction.dto;

import com.github.leonid353.transaction.model.Transaction;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransactionResponse {
    UUID id;
    UUID walletId;
    BigDecimal amount;
    TransactionType type;
    TransactionStatus status;
    Instant createdAt;
    Instant completedAt;

    public TransactionResponse(UUID id, UUID walletId, BigDecimal amount,
                               TransactionType type, TransactionStatus status) {
        this.id = id;
        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.status = status;
    }

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getWallet().getId(),
                tx.getAmount(),
                tx.getType(),
                tx.getStatus(),
                tx.getCreatedAt(),
                tx.getCompletedAt());
    }
}
