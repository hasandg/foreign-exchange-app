package com.hasandag.exchange.rate.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class ExchangeRateClientTest {

    private MockWebServer mockWebServer;
    private TestExchangeRateClient exchangeRateClient;

    @Slf4j
    static class TestExchangeRateClient {
        private final WebClient webClient;

        public TestExchangeRateClient(WebClient webClient) {
            this.webClient = webClient;
        }

        Mono<ExchangeRateResponse> getExchangeRateMono(String sourceCurrency, String targetCurrency) {
            return webClient.get()
                    .uri("/{baseCurrency}", sourceCurrency)
                    .retrieve()
                    .onStatus(HttpStatus.INTERNAL_SERVER_ERROR::equals,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(new RateServiceException("External API server error: " + errorBody))))
                    .onStatus(HttpStatus.TOO_MANY_REQUESTS::equals,
                            clientResponse -> Mono.error(new RateServiceException("External API rate limit exceeded.")))
                    .bodyToMono(Map.class)
                    .flatMap(body -> {
                        if (body == null) {
                            return Mono.error(new RateServiceException("Failed to get exchange rate from API: Empty response"));
                        }
                        if ("success".equals(body.get("result"))) {
                            Map<String, Object> rates = (Map<String, Object>) body.get("rates");
                            if (rates == null || !rates.containsKey(targetCurrency)) {
                                return Mono.error(new RateServiceException("Exchange rate not found for " + targetCurrency));
                            }
                            double rate = ((Number) rates.get(targetCurrency)).doubleValue();
                            ExchangeRateResponse response = ExchangeRateResponse.builder()
                                    .sourceCurrency(sourceCurrency)
                                    .targetCurrency(targetCurrency)
                                    .rate(BigDecimal.valueOf(rate))
                                    .lastUpdated(LocalDateTime.now())
                                    .build();
                            return Mono.just(response);
                        } else {
                            return Mono.error(new RateServiceException("Failed to get exchange rate from API: " + body.getOrDefault("error-type", "Unknown error")));
                        }
                    })
                    .retryWhen(Retry.backoff(3, Duration.ofMillis(10)) // Fast retries for testing
                            .filter(throwable -> throwable instanceof RateServiceException &&
                                    ((RateServiceException) throwable).getMessage().contains("External API server error"))
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    new RateServiceException("Retries exhausted for external API: " + retrySignal.failure().getMessage())));
        }

        public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
            return getExchangeRateMono(sourceCurrency, targetCurrency)
                    .block(Duration.ofSeconds(2));
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        exchangeRateClient = new TestExchangeRateClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testGetExchangeRateSuccess() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        String mockJsonResponse = String.format(
                "{\"result\":\"success\", \"base_code\":\"%s\", \"rates\":{\"%s\":0.85}}",
                sourceCurrency, targetCurrency
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        ExchangeRateResponse result = exchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency);

        assertNotNull(result);
        assertEquals(sourceCurrency, result.getSourceCurrency());
        assertEquals(targetCurrency, result.getTargetCurrency());
        assertEquals(BigDecimal.valueOf(0.85), result.getRate());
    }

    @Test
    void testApiError() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        String mockJsonResponse = "{\"result\":\"error\", \"error-type\":\"invalid-data\"}";

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        RateServiceException exception = assertThrows(RateServiceException.class, () -> exchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency));
        assertTrue(exception.getMessage().contains("invalid-data"));
    }

    @Test
    void testHttp500Error() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 1 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 2 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 3 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 4 Failed"));

        Mono<ExchangeRateResponse> mono = exchangeRateClient.getExchangeRateMono(sourceCurrency, targetCurrency);

        StepVerifier.create(mono)
                .expectErrorMatches(throwable ->
                        throwable instanceof RateServiceException &&
                                throwable.getMessage().startsWith("Retries exhausted for external API: ") &&
                                throwable.getMessage().contains("External API server error: Attempt 4 Failed")
                )
                .verify(Duration.ofSeconds(1));

        assertEquals(4, mockWebServer.getRequestCount());
    }

    @Test
    void testCurrencyNotFound() {
        String sourceCurrency = "USD";
        String targetCurrency = "INVALID";
        String mockJsonResponse = String.format(
                "{\"result\":\"success\", \"base_code\":\"%s\", \"rates\":{\"EUR\":0.85}}",
                sourceCurrency
        );

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockJsonResponse)
                .addHeader("Content-Type", "application/json"));

        RateServiceException exception = assertThrows(RateServiceException.class, () -> exchangeRateClient.getExchangeRate(sourceCurrency, targetCurrency));
        assertTrue(exception.getMessage().contains("Exchange rate not found for INVALID"));
    }

    @Test
    void testConnectionError() {
        try {
            mockWebServer.shutdown();
        } catch (IOException e) {
            fail("Failed to shutdown MockWebServer");
        }

        Mono<ExchangeRateResponse> mono = exchangeRateClient.getExchangeRateMono("USD", "EUR");

        StepVerifier.create(mono)
                .expectErrorMatches(throwable ->
                        throwable instanceof WebClientRequestException &&
                                throwable.getCause() instanceof ConnectException
                )
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void testRetryOnErrors() {
        String sourceCurrency = "USD";
        String targetCurrency = "EUR";

        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 1 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 2 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 3 Failed"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Attempt 4 Failed"));

        Mono<ExchangeRateResponse> mono = exchangeRateClient.getExchangeRateMono(sourceCurrency, targetCurrency);

        StepVerifier.create(mono)
                .expectErrorMatches(throwable ->
                        throwable instanceof RateServiceException &&
                                throwable.getMessage().startsWith("Retries exhausted for external API: ") &&
                                throwable.getMessage().contains("External API server error: Attempt 4 Failed")
                )
                .verify(Duration.ofSeconds(1));

        assertEquals(4, mockWebServer.getRequestCount());
    }
} 