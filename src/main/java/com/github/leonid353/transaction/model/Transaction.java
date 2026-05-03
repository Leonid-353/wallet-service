package com.github.leonid353.transaction.model;

import com.github.leonid353.transaction.dto.TransactionStatus;
import com.github.leonid353.transaction.dto.TransactionType;
import com.github.leonid353.wallet.model.Wallet;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    Wallet wallet;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    @DecimalMin(value = "0.01", message = "Сумма транзакции должна быть больше нуля")
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    @NotNull(message = "Тип транзакции обязателен")
    TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "error_message", length = 500)
    String errorMessage;

    @Column(name = "retry_count")
    int retryCount = 0;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
