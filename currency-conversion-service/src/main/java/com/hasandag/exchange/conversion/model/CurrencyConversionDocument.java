package com.hasandag.exchange.conversion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "currency_conversions")
@CompoundIndex(name = "idx_timestamp_status", def = "{'timestamp': -1, 'status': 1}")
@CompoundIndex(name = "idx_user_timestamp", def = "{'user_id': 1, 'timestamp': -1}")
public class CurrencyConversionDocument {

    @Id
    private String id;

    @Field("transaction_id")
    @Indexed(unique = true)
    private String transactionId;

    @Field("source_currency")
    private String sourceCurrency;

    @Field("target_currency")
    private String targetCurrency;

    @Field("source_amount")
    private BigDecimal sourceAmount;

    @Field("target_amount")
    private BigDecimal targetAmount;

    @Field("exchange_rate")
    private BigDecimal exchangeRate;

    @Field("timestamp")
    @Indexed
    private LocalDateTime timestamp;
    
    @Field("user_id")
    @Indexed
    private String userId;
    
    @Field("command_id")
    private String commandId;
    
    @Field("correlation_id")
    private String correlationId;
    
    @Field("status")
    @Indexed
    private String status = "COMPLETED"; // PENDING, COMPLETED, FAILED
} 