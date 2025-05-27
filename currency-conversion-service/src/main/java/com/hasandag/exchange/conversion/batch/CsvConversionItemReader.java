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

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Iterator;

@Slf4j
public class CsvConversionItemReader implements ItemReader<ConversionRequest>, ItemStream {
    
    private static final String CURRENT_ITEM_COUNT = "current.item.count";
    private static final String FILE_CONTENT_KEY = "file.content";
    
    private String fileContent;
    private String originalFilename;
    private CSVParser csvParser;
    private Iterator<CSVRecord> recordIterator;
    private int currentItemCount = 0;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.fileContent = stepExecution.getJobParameters().getString(FILE_CONTENT_KEY);
        this.originalFilename = stepExecution.getJobParameters().getString("original.filename");
        log.info("Reading CSV content from memory, original filename: {}", originalFilename);
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            currentItemCount = executionContext.getInt(CURRENT_ITEM_COUNT, 0);
            log.debug("Current item count from execution context: {}", currentItemCount);

            if (fileContent == null || fileContent.trim().isEmpty()) {
                throw new ItemStreamException("CSV file content is null or empty");
            }

            StringReader stringReader = new StringReader(fileContent);
            csvParser = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build()
                    .parse(stringReader);
            
            recordIterator = csvParser.iterator();
            
            for (int i = 0; i < currentItemCount; i++) {
                if (recordIterator.hasNext()) {
                    recordIterator.next();
                }
            }
            
            log.info("CSV reader opened from memory, skipped {} records", currentItemCount);
        } catch (IOException e) {
            throw new ItemStreamException("Error parsing CSV content from memory", e);
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
        if (originalFilename != null) {
            executionContext.putString("original.filename", originalFilename);
        }
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (csvParser != null) {
                csvParser.close();
            }
            log.info("CSV reader closed after processing {} records from memory", currentItemCount);
        } catch (IOException e) {
            throw new ItemStreamException("Error closing CSV parser", e);
        }
    }
} 