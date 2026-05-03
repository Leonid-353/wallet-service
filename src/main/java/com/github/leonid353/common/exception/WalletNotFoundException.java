package com.github.leonid353.common.exception;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(UUID walletId) {
        super("Кошелёк не найден: " + walletId);
    }
}
