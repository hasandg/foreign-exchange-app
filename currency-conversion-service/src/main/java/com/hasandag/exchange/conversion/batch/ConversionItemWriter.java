package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.common.dto.cqrs.ConversionEvent;
import com.hasandag.exchange.conversion.kafka.producer.ConversionEventProducer;
import com.hasandag.exchange.conversion.model.CurrencyConversionDocument;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class ConversionItemWriter implements ItemWriter<ConversionResponse> {

    private final CurrencyConversionMongoRepository mongoRepository;
    private final ConversionEventProducer conversionEventProducer;

    @Override
    @Transactional
    public void write(Chunk<? extends ConversionResponse> chunk) {
        List<ConversionResponse> successfulWrites = new ArrayList<>();
        
        for (ConversionResponse response : chunk) {
            try {
                if (isTransactionAlreadyProcessed(response.getTransactionId())) {
                    log.debug("Skipping duplicate transaction: {}", response.getTransactionId());
                    successfulWrites.add(response);
                    continue;
                }
                
                if (mongoRepository != null) {
                    try {
                        CurrencyConversionDocument document = CurrencyConversionDocument.builder()
                                .transactionId(response.getTransactionId())
                                .sourceCurrency(response.getSourceCurrency())
                                .targetCurrency(response.getTargetCurrency())
                                .sourceAmount(response.getSourceAmount())
                                .targetAmount(response.getTargetAmount())
                                .exchangeRate(response.getExchangeRate())
                                .timestamp(response.getTimestamp())
                                .build();

                        mongoRepository.save(document);
                        log.debug("Saved conversion to Write Model (MongoDB): {}", response.getTransactionId());
                        successfulWrites.add(response);
                        
                    } catch (DuplicateKeyException e) {
                        log.debug("Duplicate detected in MongoDB (handled gracefully): {}", response.getTransactionId());
                        successfulWrites.add(response);
                    } catch (Exception e) {
                        log.error("Failed to save to MongoDB: {} - {}", response.getTransactionId(), e.getMessage());
                        throw e; 
                    }
                } else {
                    log.warn("MongoDB repository not available - skipping write model save");
                    successfulWrites.add(response);
                }

            } catch (Exception e) {
                log.error("Error processing conversion: {} - {}", 
                        response.getTransactionId(), e.getMessage());
                throw e;
            }
        }

        publishEvents(successfulWrites);
        log.info("Processed {} conversion records via CQRS pattern", successfulWrites.size());
    }

    private boolean isTransactionAlreadyProcessed(String transactionId) {
        if (mongoRepository != null) {
            try {
                return mongoRepository.existsByTransactionId(transactionId);
            } catch (Exception e) {
                log.warn("Error checking for duplicate in MongoDB: {} - {}", transactionId, e.getMessage());
                return false;
            }
        }
        return false;
    }

    private void publishEvents(List<ConversionResponse> conversions) {
        if (conversionEventProducer != null) {
            for (ConversionResponse conversion : conversions) {
                try {
                    ConversionEvent event = ConversionEvent.builder()
                            .transactionId(conversion.getTransactionId())
                            .sourceCurrency(conversion.getSourceCurrency())
                            .targetCurrency(conversion.getTargetCurrency())
                            .sourceAmount(conversion.getSourceAmount())
                            .targetAmount(conversion.getTargetAmount())
                            .exchangeRate(conversion.getExchangeRate())
                            .timestamp(conversion.getTimestamp())
                            .eventType(ConversionEvent.EventType.CONVERSION_CREATED)
                            .build();

                    conversionEventProducer.sendConversionEvent(event);
                    log.debug("Published CQRS event for Read Model update: {}", conversion.getTransactionId());
                } catch (Exception e) {
                    log.warn("Failed to publish CQRS event for conversion: {} - {}",
                            conversion.getTransactionId(), e.getMessage());
                }
            }
        } else {
            log.warn("Event producer not available - Read Model will not be updated");
        }
    }
} 