package com.github.leonid353.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OperationRequest {

    @NotNull(message = "walletId обязателен")
    UUID walletId;

    @NotNull(message = "operationType обязателен")
    OperationType operationType;

    @NotNull(message = "amount обязателен")
    @DecimalMin(value = "0.00", message = "Сумма не может быть отрицательной")
    BigDecimal amount;
}
