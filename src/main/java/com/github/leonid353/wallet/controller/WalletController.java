package com.github.leonid353.wallet.controller;

import com.github.leonid353.transaction.dto.TransactionResponse;
import com.github.leonid353.wallet.dto.CreateWalletRequest;
import com.github.leonid353.wallet.dto.OperationRequest;
import com.github.leonid353.wallet.dto.WalletResponse;
import com.github.leonid353.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Tag(name = "Wallet", description = "API для управления кошельками")
public class WalletController {

    final WalletService walletService;

    @Operation(summary = "Создать новый кошелёк", description = "Создаёт кошелёк с указанным начальным балансом")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Кошелёк создан"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос")
    })
    @PostMapping("/wallets")
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@Valid @RequestBody CreateWalletRequest request) {
        log.info("Создание кошелька, начальный баланс: {}", request.getInitialBalance());
        WalletResponse response = walletService.createWallet(request);
        log.info("Кошелёк создан: id={}", response.getId());
        return response;
    }

    @Operation(summary = "Получить баланс кошелька", description = "Возвращает текущий баланс кошелька по его UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Баланс получен"),
            @ApiResponse(responseCode = "404", description = "Кошелёк не найден")
    })
    @GetMapping("/wallets/{walletId}")
    public WalletResponse getWallet(
            @Parameter(description = "UUID кошелька", required = true)
            @PathVariable UUID walletId) {
        log.debug("Запрос баланса кошелька: id={}", walletId);
        return walletService.getWallet(walletId);
    }

    @Operation(summary = "Операция с кошельком",
            description = "Пополнение или снятие средств. " +
                    "Принимает запрос, возвращает 202 Accepted сразу, обработка асинхронная через буфер")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Операция принята в обработку"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос"),
            @ApiResponse(responseCode = "404", description = "Кошелёк не найден")
    })
    @PostMapping("/wallet")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<TransactionResponse> processOperation(@Valid @RequestBody OperationRequest request) {
        log.info("Операция по кошельку: walletId={}, тип={}, сумма={}",
                request.getWalletId(), request.getOperationType(), request.getAmount());
        return walletService.processOperation(request)
                .whenComplete((tx, error) -> {
                    if (error != null) {
                        log.error("Ошибка операции: walletId={}, тип={}, сумма={}, ошибка={}",
                                request.getWalletId(), request.getOperationType(),
                                request.getAmount(), error.getMessage());
                    } else {
                        log.info("Операция принята: transactionId={}, walletId={}, тип={}, сумма={}",
                                tx.getId(), request.getWalletId(), request.getOperationType(), request.getAmount());
                    }
                });
    }

    @Operation(summary = "История транзакций", description = "Возвращает список транзакций кошелька с пагинацией")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список транзакций"),
            @ApiResponse(responseCode = "404", description = "Кошелёк не найден")
    })
    @GetMapping("/wallets/{walletId}/transactions")
    public List<TransactionResponse> getTransactions(
            @Parameter(description = "UUID кошелька", required = true)
            @PathVariable UUID walletId,
            @Parameter(description = "Пагинация (page, size, sort)")
            Pageable pageable) {
        log.debug("Запрос истории транзакций: walletId={}, страница={}, размер={}",
                walletId, pageable.getPageNumber(), pageable.getPageSize());
        return walletService.getTransactions(walletId, pageable);
    }
}
