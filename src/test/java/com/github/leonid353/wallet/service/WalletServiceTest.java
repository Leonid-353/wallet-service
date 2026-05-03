package com.github.leonid353.wallet.service;

import com.github.leonid353.buffer.WalletTransactionBuffer;
import com.github.leonid353.common.exception.WalletNotFoundException;
import com.github.leonid353.transaction.repository.TransactionRepository;
import com.github.leonid353.wallet.dto.CreateWalletRequest;
import com.github.leonid353.wallet.dto.OperationRequest;
import com.github.leonid353.wallet.dto.OperationType;
import com.github.leonid353.wallet.dto.WalletResponse;
import com.github.leonid353.wallet.model.Wallet;
import com.github.leonid353.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletTransactionBuffer buffer;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void shouldCreateWallet() {
        CreateWalletRequest request = new CreateWalletRequest(new BigDecimal("100.00"));

        Wallet savedWallet = new Wallet();
        savedWallet.setId(UUID.randomUUID());
        savedWallet.setBalance(new BigDecimal("100.00"));

        when(walletRepository.save(any(Wallet.class))).thenReturn(savedWallet);

        WalletResponse response = walletService.createWallet(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldGetWallet() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setBalance(new BigDecimal("500.00"));

        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getWallet(walletId);

        assertThat(response.getId()).isEqualTo(walletId);
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void shouldThrowWhenWalletNotFound() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWallet(walletId))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void shouldThrowWhenProcessingOperationForNonExistentWallet() {
        OperationRequest request = new OperationRequest(
                UUID.randomUUID(),
                OperationType.DEPOSIT,
                new BigDecimal("100.00"));
        when(walletRepository.existsById(request.getWalletId())).thenReturn(false);

        assertThatThrownBy(() -> walletService.processOperation(request))
                .isInstanceOf(WalletNotFoundException.class);
    }
}
