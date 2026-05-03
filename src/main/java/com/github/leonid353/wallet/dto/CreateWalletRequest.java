package com.github.leonid353.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateWalletRequest {

    @DecimalMin(value = "0.00", message = "Начальный баланс не может быть отрицательным")
    BigDecimal initialBalance;
}
