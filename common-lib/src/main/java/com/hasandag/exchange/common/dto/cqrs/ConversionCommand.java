package com.hasandag.exchange.common.dto.cqrs;

import com.hasandag.exchange.common.validation.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ConversionCommand {
    
    @NotBlank(message = "Command ID is required")
    private String commandId;
    
    @NotBlank(message = "Source currency is required")
    @CurrencyCode
    private String sourceCurrency;
    
    @NotBlank(message = "Target currency is required")
    @CurrencyCode
    private String targetCurrency;
    
    @NotNull(message = "Source amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal sourceAmount;
    
    private LocalDateTime timestamp;
    private String correlationId;
    
    public ConversionCommand() {
        this.commandId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.correlationId = UUID.randomUUID().toString();
    }

    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }
    
    public String getSourceCurrency() { return sourceCurrency; }
    public void setSourceCurrency(String sourceCurrency) { this.sourceCurrency = sourceCurrency; }
    
    public String getTargetCurrency() { return targetCurrency; }
    public void setTargetCurrency(String targetCurrency) { this.targetCurrency = targetCurrency; }
    
    public BigDecimal getSourceAmount() { return sourceAmount; }
    public void setSourceAmount(BigDecimal sourceAmount) { this.sourceAmount = sourceAmount; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    
    @Override
    public String toString() {
        return "ConversionCommand{" +
                "commandId='" + commandId + '\'' +
                ", sourceCurrency='" + sourceCurrency + '\'' +
                ", targetCurrency='" + targetCurrency + '\'' +
                ", sourceAmount=" + sourceAmount +
                ", timestamp=" + timestamp +
                ", correlationId='" + correlationId + '\'' +
                '}';
    }
} 