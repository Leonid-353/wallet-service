package com.github.leonid353.wallet.controller;

import com.github.leonid353.wallet.dto.CreateWalletRequest;
import com.github.leonid353.wallet.dto.OperationRequest;
import com.github.leonid353.wallet.dto.OperationType;
import com.github.leonid353.wallet.dto.WalletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class WalletControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldCreateWallet() {
        CreateWalletRequest request = new CreateWalletRequest(new BigDecimal("100.00"));
        ResponseEntity<WalletResponse> response = restTemplate.postForEntity(
                "/api/v1/wallets", request, WalletResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        WalletResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldGetWallet() {
        CreateWalletRequest createRequest = new CreateWalletRequest(new BigDecimal("500.00"));
        ResponseEntity<WalletResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/wallets", createRequest, WalletResponse.class);

        WalletResponse createBody = createResponse.getBody();
        assertThat(createBody).isNotNull();
        UUID walletId = createBody.getId();

        ResponseEntity<WalletResponse> getResponse = restTemplate.getForEntity(
                "/api/v1/wallets/" + walletId, WalletResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        WalletResponse getBody = getResponse.getBody();
        assertThat(getBody).isNotNull();
        assertThat(getBody.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void shouldReturn404WhenWalletNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/wallets/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldProcessDeposit() throws Exception {
        CreateWalletRequest createRequest = new CreateWalletRequest(BigDecimal.ZERO);
        ResponseEntity<WalletResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/wallets", createRequest, WalletResponse.class);

        WalletResponse createBody = createResponse.getBody();
        assertThat(createBody).isNotNull();
        UUID walletId = createBody.getId();

        OperationRequest opRequest = new OperationRequest(walletId, OperationType.DEPOSIT, new BigDecimal("200.00"));
        ResponseEntity<String> opResponse = restTemplate.postForEntity(
                "/api/v1/wallet", opRequest, String.class);

        assertThat(opResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        Thread.sleep(1000);

        ResponseEntity<WalletResponse> getResponse = restTemplate.getForEntity(
                "/api/v1/wallets/" + walletId, WalletResponse.class);

        WalletResponse getBody = getResponse.getBody();
        assertThat(getBody).isNotNull();
        assertThat(getBody.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void shouldProcessWithdraw() throws Exception {
        CreateWalletRequest createRequest = new CreateWalletRequest(new BigDecimal("500.00"));
        ResponseEntity<WalletResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/wallets", createRequest, WalletResponse.class);

        WalletResponse createBody = createResponse.getBody();
        assertThat(createBody).isNotNull();
        UUID walletId = createBody.getId();

        OperationRequest opRequest = new OperationRequest(walletId, OperationType.WITHDRAW, new BigDecimal("100.00"));
        ResponseEntity<String> opResponse = restTemplate.postForEntity(
                "/api/v1/wallet", opRequest, String.class);

        assertThat(opResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        Thread.sleep(1000);

        ResponseEntity<WalletResponse> getResponse = restTemplate.getForEntity(
                "/api/v1/wallets/" + walletId, WalletResponse.class);

        WalletResponse getBody = getResponse.getBody();
        assertThat(getBody).isNotNull();
        assertThat(getBody.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    void shouldReturn400ForInvalidRequest() {
        OperationRequest opRequest = new OperationRequest(null, OperationType.DEPOSIT, null);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/wallet", opRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn404ForNonExistentWalletOperation() {
        OperationRequest opRequest = new OperationRequest(UUID.randomUUID(), OperationType.DEPOSIT, new BigDecimal("100.00"));
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/wallet", opRequest, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
