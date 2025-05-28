package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.conversion.client.ExchangeRateFeignClient;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@RequiredArgsConstructor
@Slf4j
public class ConversionItemProcessor implements ItemProcessor<ConversionRequest, ConversionResponse> {

    private final ExchangeRateFeignClient exchangeRateFeignClient;
    private final CurrencyConversionMongoRepository mongoRepository;
    private final CurrencyConversionPostgresRepository postgresRepository;

    @Override
    public ConversionResponse process(ConversionRequest request) {
        String transactionId = generateTransactionId(request);
        
        if (mongoRepository != null && mongoRepository.existsByTransactionId(transactionId)) {
            log.debug("Skipping duplicate in Write Model (MongoDB): {}", transactionId);
            return null;
        }
        
        if (mongoRepository == null && postgresRepository.existsByTransactionId(transactionId)) {
            log.debug("Skipping duplicate in Read Model (PostgreSQL - fallback): {}", transactionId);
            return null;
        }

        try {
            ExchangeRateResponse rateResponse = exchangeRateFeignClient.getExchangeRate(
                    request.getSourceCurrency(),
                    request.getTargetCurrency());

            BigDecimal targetAmount = request.getSourceAmount()
                    .multiply(rateResponse.getRate())
                    .setScale(2, RoundingMode.HALF_UP);

            return ConversionResponse.builder()
                    .transactionId(transactionId)
                    .sourceCurrency(request.getSourceCurrency())
                    .targetCurrency(request.getTargetCurrency())
                    .sourceAmount(request.getSourceAmount())
                    .targetAmount(targetAmount)
                    .exchangeRate(rateResponse.getRate())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error processing conversion for transaction {}: {}", transactionId, e.getMessage());
            throw e;
        }
    }

    private String generateTransactionId(ConversionRequest request) {
        try {
            String requestString = String.format("%s-%s-%s", 
                    request.getSourceCurrency(),
                    request.getTargetCurrency(),
                    request.getSourceAmount().toString());

            LocalDateTime now = LocalDateTime.now();
            String timeComponent = String.format("%04d%02d%02d%02d%02d",
                    now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                    now.getHour(), now.getMinute());

            String combinedString = requestString + "-" + timeComponent;

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(combinedString.getBytes());
            return "BATCH-" + HexFormat.of().formatHex(hashBytes).substring(0, 16).toUpperCase();

        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 not available, using fallback transaction ID generation");
            return String.format("BATCH-%s-%s-%s-%d",
                    request.getSourceCurrency(),
                    request.getTargetCurrency(),
                    request.getSourceAmount().toString().replace(".", ""),
                    System.currentTimeMillis() / 60000);
        }
    }
} 