package com.hasandag.exchange.conversion.cqrs.command;

import com.hasandag.exchange.common.constants.KafkaConstants;
import com.hasandag.exchange.common.dto.cqrs.ConversionCommand;
import com.hasandag.exchange.common.dto.cqrs.ConversionEvent;
import com.hasandag.exchange.common.dto.ExchangeRateResponse;
import com.hasandag.exchange.conversion.client.ExchangeRateFeignClient;
import com.hasandag.exchange.conversion.model.CurrencyConversionDocument;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "cqrs.command-handler.enabled", havingValue = "true", matchIfMissing = false)
public class ConversionCommandHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversionCommandHandler.class);
    
    private final ExchangeRateFeignClient exchangeRateClient;
    private final CurrencyConversionMongoRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public ConversionCommandHandler(ExchangeRateFeignClient exchangeRateClient,
                                  CurrencyConversionMongoRepository repository,
                                  KafkaTemplate<String, Object> kafkaTemplate) {
        this.exchangeRateClient = exchangeRateClient;
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    @KafkaListener(
        topics = KafkaConstants.CONVERSION_COMMAND_TOPIC,
        groupId = KafkaConstants.COMMAND_HANDLER_GROUP,
        containerFactory = "conversionCommandListenerContainerFactory"
    )
    @Transactional
    public void handleConversionCommand(ConversionCommand command) {
        logger.info("Processing conversion command: {}", command.getCommandId());
        
        try {
            validateCommand(command);
            
            ExchangeRateResponse rateResponse = exchangeRateClient.getExchangeRate(
                command.getSourceCurrency(), 
                command.getTargetCurrency()
            );
            BigDecimal exchangeRate = rateResponse.getRate();
            
            BigDecimal targetAmount = command.getSourceAmount()
                .multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);
            CurrencyConversionDocument conversion = new CurrencyConversionDocument();
            conversion.setTransactionId(UUID.randomUUID().toString());
            conversion.setSourceCurrency(command.getSourceCurrency());
            conversion.setTargetCurrency(command.getTargetCurrency());
            conversion.setSourceAmount(command.getSourceAmount());
            conversion.setTargetAmount(targetAmount);
            conversion.setExchangeRate(exchangeRate);
            conversion.setTimestamp(LocalDateTime.now());
            conversion.setCommandId(command.getCommandId());
            conversion.setCorrelationId(command.getCorrelationId());
            
            CurrencyConversionDocument savedConversion = repository.save(conversion);
            
            ConversionEvent event = new ConversionEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setTransactionId(savedConversion.getTransactionId());
            event.setCommandId(command.getCommandId());
            event.setCorrelationId(command.getCorrelationId());
            event.setSourceCurrency(savedConversion.getSourceCurrency());
            event.setTargetCurrency(savedConversion.getTargetCurrency());
            event.setSourceAmount(savedConversion.getSourceAmount());
            event.setTargetAmount(savedConversion.getTargetAmount());
            event.setExchangeRate(savedConversion.getExchangeRate());
            event.setTimestamp(savedConversion.getTimestamp());
            event.setUserId(savedConversion.getUserId());
            event.setEventType(ConversionEvent.EventType.CONVERSION_CREATED);
            
            kafkaTemplate.send(KafkaConstants.CONVERSION_EVENT_TOPIC, event.getTransactionId(), event);
            
            logger.info("Conversion command processed successfully. Transaction ID: {}", 
                savedConversion.getTransactionId());
            
        } catch (Exception e) {
            logger.error("Error processing conversion command: {}", command.getCommandId(), e);
            
            publishFailureEvent(command, e.getMessage());
            throw e;
        }
    }
    
    private void validateCommand(ConversionCommand command) {
        if (command.getSourceCurrency() == null || command.getSourceCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Source currency is required");
        }
        if (command.getTargetCurrency() == null || command.getTargetCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Target currency is required");
        }
        if (command.getSourceAmount() == null || command.getSourceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Source amount must be greater than zero");
        }
        if (command.getSourceCurrency().equals(command.getTargetCurrency())) {
            throw new IllegalArgumentException("Source and target currencies cannot be the same");
        }
    }
    
    private void publishFailureEvent(ConversionCommand command, String errorMessage) {
        try {
            ConversionEvent failureEvent = new ConversionEvent();
            failureEvent.setEventId(UUID.randomUUID().toString());
            failureEvent.setCommandId(command.getCommandId());
            failureEvent.setCorrelationId(command.getCorrelationId());
            failureEvent.setSourceCurrency(command.getSourceCurrency());
            failureEvent.setTargetCurrency(command.getTargetCurrency());
            failureEvent.setSourceAmount(command.getSourceAmount());
            failureEvent.setTimestamp(LocalDateTime.now());
            failureEvent.setEventType(ConversionEvent.EventType.CONVERSION_FAILED);
            failureEvent.setErrorMessage(errorMessage);
            
            kafkaTemplate.send(KafkaConstants.CONVERSION_EVENT_TOPIC, command.getCommandId(), failureEvent);
            
        } catch (Exception e) {
            logger.error("Failed to publish failure event for command: {}", command.getCommandId(), e);
        }
    }
} 