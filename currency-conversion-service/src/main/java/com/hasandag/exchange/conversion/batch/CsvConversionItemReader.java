package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Iterator;

@Slf4j
public class CsvConversionItemReader implements ItemReader<ConversionRequest>, ItemStream {
    
    private static final String CURRENT_ITEM_COUNT = "current.item.count";
    private static final String FILE_PATH_KEY = "input.file.path";
    
    private String filePath;
    private CSVParser csvParser;
    private Iterator<CSVRecord> recordIterator;
    private BufferedReader bufferedReader;
    private int currentItemCount = 0;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.filePath = stepExecution.getJobParameters().getString("input.file.path");
        log.info("Reading CSV file: {}", filePath);
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            currentItemCount = executionContext.getInt(CURRENT_ITEM_COUNT, 0);

            System.out.println("Current item count from execution context: " + currentItemCount);

            bufferedReader = new BufferedReader(new FileReader(filePath));
            csvParser = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build()
                    .parse(bufferedReader);
            
            recordIterator = csvParser.iterator();
            
            for (int i = 0; i < currentItemCount; i++) {
                if (recordIterator.hasNext()) {
                    recordIterator.next();
                }
            }
            
            log.info("CSV reader opened, skipped {} records", currentItemCount);
        } catch (IOException e) {
            throw new ItemStreamException("Error opening CSV file: " + filePath, e);
        }
    }

    @Override
    public ConversionRequest read() {
        if (recordIterator != null && recordIterator.hasNext()) {
            CSVRecord record = recordIterator.next();
            currentItemCount++;
            
            try {
                String sourceAmountStr = record.get("sourceAmount");
                String sourceCurrency = record.get("sourceCurrency");
                String targetCurrency = record.get("targetCurrency");
                if (sourceAmountStr == null || sourceAmountStr.trim().isEmpty()) {
                    log.error("Missing or empty sourceAmount at line {}", currentItemCount);
                    throw new IllegalArgumentException("Missing or empty sourceAmount");
                }
                if (sourceCurrency == null || sourceCurrency.trim().isEmpty()) {
                    log.error("Missing or empty sourceCurrency at line {}", currentItemCount);
                    throw new IllegalArgumentException("Missing or empty sourceCurrency");
                }
                if (targetCurrency == null || targetCurrency.trim().isEmpty()) {
                    log.error("Missing or empty targetCurrency at line {}", currentItemCount);
                    throw new IllegalArgumentException("Missing or empty targetCurrency");
                }
                
                BigDecimal sourceAmount;
                try {
                    sourceAmount = new BigDecimal(sourceAmountStr.trim());
                } catch (NumberFormatException e) {
                    log.error("Invalid sourceAmount '{}' at line {}: {}", sourceAmountStr, currentItemCount, e.getMessage());
                    throw new IllegalArgumentException("Invalid sourceAmount: " + sourceAmountStr);
                }
                
                return ConversionRequest.builder()
                        .sourceAmount(sourceAmount)
                        .sourceCurrency(sourceCurrency.trim().toUpperCase())
                        .targetCurrency(targetCurrency.trim().toUpperCase())
                        .build();
            } catch (Exception e) {
                log.error("Error parsing CSV record at line {}: {}", currentItemCount, e.getMessage());
                throw e;
            }
        }
        return null;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(CURRENT_ITEM_COUNT, currentItemCount);
        executionContext.putString(FILE_PATH_KEY, filePath);
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (csvParser != null) {
                csvParser.close();
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            log.info("CSV reader closed after processing {} records", currentItemCount);
        } catch (IOException e) {
            throw new ItemStreamException("Error closing CSV file resources", e);
        }
    }
} 