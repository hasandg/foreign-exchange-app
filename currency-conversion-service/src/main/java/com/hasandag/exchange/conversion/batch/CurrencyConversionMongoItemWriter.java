package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.conversion.model.CurrencyConversionDocument;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.lang.NonNull;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(CurrencyConversionMongoRepository.class)
public class CurrencyConversionMongoItemWriter implements ItemWriter<ConversionResponse> {

    private final CurrencyConversionMongoRepository mongoRepository;

    @Override
    public void write(@NonNull Chunk<? extends ConversionResponse> chunk) throws Exception {
        for (ConversionResponse response : chunk.getItems()) {
            if (response == null) {
                continue;
            }
            
            try {
                CurrencyConversionDocument mongoDocument = mapToMongoDocument(response);
                mongoRepository.save(mongoDocument);
                log.debug("Saved to MongoDB: {}", response.getTransactionId());
            } catch (Exception e) {
                log.error("Error saving to MongoDB for transaction ID {}: {}", response.getTransactionId(), e.getMessage(), e);
                throw e;
            }
        }
        log.info("Successfully saved {} items to MongoDB", chunk.size());
    }

    private CurrencyConversionDocument mapToMongoDocument(ConversionResponse response) {
        return CurrencyConversionDocument.builder()
                .transactionId(response.getTransactionId())
                .sourceCurrency(response.getSourceCurrency())
                .targetCurrency(response.getTargetCurrency())
                .sourceAmount(response.getSourceAmount())
                .targetAmount(response.getTargetAmount())
                .exchangeRate(response.getExchangeRate())
                .timestamp(response.getTimestamp())
                .status("COMPLETED")
                .build();
    }
} 