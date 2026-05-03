package com.github.leonid353.wallet.service;

import com.github.leonid353.transaction.dto.TransactionResponse;
import com.github.leonid353.wallet.dto.CreateWalletRequest;
import com.github.leonid353.wallet.dto.OperationRequest;
import com.github.leonid353.wallet.dto.WalletResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WalletService {

    WalletResponse createWallet(CreateWalletRequest request);

    WalletResponse getWallet(UUID walletId);

    CompletableFuture<TransactionResponse> processOperation(OperationRequest request);

    List<TransactionResponse> getTransactions(UUID walletId, Pageable pageable);
}
