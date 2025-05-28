package com.hasandag.exchange.rate.client;

import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.common.exception.RateServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@Slf4j
public class ExchangeRateClient {

    private final WebClient webClient;

    private final int maxAttempts = 3;
    private final Duration backoffDelay = Duration.ofSeconds(1);
    private final Duration blockTimeout = Duration.ofSeconds(15);

    public ExchangeRateClient(@Qualifier("exchangeRateApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    Mono<ExchangeRateResponse> getExchangeRateMono(String sourceCurrency, String targetCurrency) {
        log.debug("Fetching exchange rate: {}-{}", sourceCurrency, targetCurrency);

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
                        log.debug("Fetched rate: {}-{} = {} (from {} total rates)", 
                                 sourceCurrency, targetCurrency, response.getRate(), rates.size());
                        return Mono.just(response);
                    } else {
                        log.error("Error from API: {}", body);
                        return Mono.error(new RateServiceException("Failed to get exchange rate from API: " + body.getOrDefault("error-type", "Unknown error")));
                    }
                })
                .retryWhen(Retry.backoff(maxAttempts, backoffDelay)
                        .filter(throwable -> throwable instanceof RateServiceException &&
                                ((RateServiceException) throwable).getMessage().contains("External API server error"))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> 
                                new RateServiceException("Retries exhausted for external API: " + retrySignal.failure().getMessage())))
                .doOnError(WebClientResponseException.class, e -> 
                    log.error("WebClientResponseException calling exchange rate API: Status {}, Body {}", e.getStatusCode(), e.getResponseBodyAsString(), e)
                )
                .doOnError(throwable -> !(throwable instanceof RateServiceException), e -> 
                    log.error("Non-WebClient error calling exchange rate API: {}", e.getMessage(), e)
                );
    }

    @Cacheable(value = "exchangeRates", key = "#sourceCurrency + '-' + #targetCurrency")
    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        return getExchangeRateMono(sourceCurrency, targetCurrency)
                .block(blockTimeout);
    }
}