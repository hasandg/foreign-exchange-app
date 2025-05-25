package com.hasandag.exchange.conversion.service.impl;

import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import com.hasandag.exchange.conversion.service.ConversionQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Slf4j
public class ConversionQueryServiceImpl implements ConversionQueryService {

    private final CurrencyConversionPostgresRepository repository;

    public ConversionQueryServiceImpl(CurrencyConversionPostgresRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<CurrencyConversionEntity> findConversions(String transactionId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        log.debug("Finding conversions - Transaction ID: {}, StartDate: {}, EndDate: {}", transactionId, startDate, endDate);

        if (transactionId != null) {
            log.debug("Querying by transaction ID: {}", transactionId);
            Optional<CurrencyConversionEntity> entityOptional = repository.findByTransactionId(transactionId);
            if (entityOptional.isPresent()) {
                return new PageImpl<>(Collections.singletonList(entityOptional.get()), pageable, 1);
            }
            throw new NoSuchElementException("Conversion not found for transaction ID: " + transactionId);
        }
        
        if (startDate != null && endDate != null) {
            log.debug("Querying by date range: Start: {}, End: {}", startDate, endDate);
            if (startDate.isAfter(endDate)) {
                log.warn("Start date {} is after end date {}. Returning empty page.", startDate, endDate);
                return getEmptyConversionsPage(pageable);
            }
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
            
            return repository.findByTimestampBetweenOrderByTimestampDesc(startDateTime, endDateTime, pageable);
        }

        log.warn("No valid query parameters provided (transactionId or date range).");
        throw new IllegalArgumentException("Query parameters invalid: Provide a transactionId or a valid date range (startDate and endDate).");
    }

    private Page<CurrencyConversionEntity> getEmptyConversionsPage(Pageable pageable) {
        log.debug("Returning empty conversions page");
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }
} 