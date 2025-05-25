package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.conversion.model.CurrencyConversionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;


public interface ConversionQueryService {

    Page<CurrencyConversionEntity> findConversions(String transactionId, LocalDate startDate, LocalDate endDate, Pageable pageable);
}