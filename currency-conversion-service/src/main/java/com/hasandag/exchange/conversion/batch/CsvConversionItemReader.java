package com.hasandag.exchange.conversion.batch;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.conversion.service.FileContentStoreService;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
public class CsvConversionItemReader implements ItemReader<ConversionRequest>, ItemStream {

    private static final String FILE_CONTENT_KEY = "file.content.key";
    private static final String ORIGINAL_FILENAME_KEY = "original.filename";
    private static final String CURRENT_ITEM_COUNT_KEY = "csv.reader.current.item.count";

    @Autowired
    private FileContentStoreService fileContentStoreService;

    private String fileContent;
    private String originalFilename;
    private String contentKey;
    private CSVParser csvParser;
    private Iterator<CSVRecord> recordIterator;
    private int currentItemCount = 0;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.contentKey = stepExecution.getJobParameters().getString(FILE_CONTENT_KEY);
        this.originalFilename = stepExecution.getJobParameters().getString(ORIGINAL_FILENAME_KEY);
        
        // Retrieve content from centralized store using the key
        if (contentKey != null) {
            this.fileContent = fileContentStoreService.getContent(contentKey);
            if (fileContent == null) {
                log.error("File content not found in store for key: {}", contentKey);
                log.error("Available store stats: {}", fileContentStoreService.getStoreStats());
            }
        }
        
        log.info("CsvConversionItemReader initialized for file: {}. Content key: {}, Content length: {}",
                originalFilename, contentKey, (fileContent != null ? fileContent.length() : 0));
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (fileContent == null || fileContent.trim().isEmpty()) {
            log.warn("File content is null or empty for file: {}", originalFilename);
            recordIterator = java.util.Collections.emptyIterator();
            return;
        }

        if (executionContext.containsKey(CURRENT_ITEM_COUNT_KEY)) {
            currentItemCount = executionContext.getInt(CURRENT_ITEM_COUNT_KEY);
            log.info("Restarting CSV reader for file {} from item count: {}", originalFilename, currentItemCount);
        } else {
            currentItemCount = 0;
            log.info("Opening CSV reader for file {} from the beginning.", originalFilename);
        }

        try {
            log.debug("CSV content preview (first 200 chars) for {}: {}",
                    originalFilename, fileContent.substring(0, Math.min(200, fileContent.length())));

            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .setAllowMissingColumnNames(true)
                    .setIgnoreEmptyLines(true)
                    .build();

            StringReader stringReader = new StringReader(fileContent);
            csvParser = new CSVParser(stringReader, csvFormat);
            recordIterator = csvParser.iterator();

            if (recordIterator.hasNext() && currentItemCount == 0) {
                log.info("Detected CSV headers for {}: {}", originalFilename, csvParser.getHeaderNames());
            }

            for (int i = 0; i < currentItemCount; i++) {
                if (recordIterator.hasNext()) {
                    recordIterator.next();
                } else {
                    log.warn("Attempted to skip {} records for restart, but only {} records were available in {}.",
                            currentItemCount, i, originalFilename);
                    break;
                }
            }
            log.info("CSV reader opened for file {}, skipped {} records for restart (if any).", originalFilename, currentItemCount);

        } catch (IOException e) {
            log.error("Error opening or parsing CSV content for file {}: {}", originalFilename, e.getMessage(), e);
            throw new ItemStreamException("Error opening CSV file: " + originalFilename, e);
        }
    }

    @Override
    public ConversionRequest read() {
        if (recordIterator == null || !recordIterator.hasNext()) {
            return null;
        }

        CSVRecord record;
        try {
            record = recordIterator.next();
        } catch (NoSuchElementException e) {
            log.info("No more CSV records available for file {}", originalFilename);
            return null;
        } catch (Exception e) {
            currentItemCount++;
            log.error("Error fetching next CSV record at line {} for file {}: {}. Record may be malformed.",
                    currentItemCount, originalFilename, e.getMessage(), e);
            throw new RuntimeException("Failed to read CSV record due to: " + e.getMessage(), e);
        }

        currentItemCount++;
        log.debug("Processing record #{} from {}: {}", currentItemCount, originalFilename, record.toList());

        try {
            Map<String, String> recordMap = record.toMap();

            String sourceAmountStr = getValueByPossibleNames(recordMap, "sourceAmount", "amount", "value");

            String sourceCurrencyStr = getValueByPossibleNames(recordMap, "sourceCurrency", "fromCurrency", "from", "source_currency");

            String targetCurrencyStr = getValueByPossibleNames(recordMap, "targetCurrency", "toCurrency", "to", "target_currency");

            if (sourceAmountStr == null && sourceCurrencyStr == null && targetCurrencyStr == null && record.size() >= 3) {
                log.warn("Header-based mapping failed for record #{} in {}. Attempting indexed mapping. Record: {}",
                        currentItemCount, originalFilename, record.toList());
                sourceAmountStr = record.get(0);
                sourceCurrencyStr = record.get(1);
                targetCurrencyStr = record.get(2);
                log.info("Indexed mapping for #{}: amount='{}', src='{}', tgt='{}'",
                        currentItemCount, sourceAmountStr, sourceCurrencyStr, targetCurrencyStr);
            }

            if (record.size() == 2 && (sourceCurrencyStr == null || targetCurrencyStr == null)) {
                log.warn("Record #{} in {} has 2 columns. Attempting to parse potentially malformed data: {}",
                        currentItemCount, originalFilename, record.toList());
                String field1 = record.get(0).trim();
                String field2 = record.get(1).trim();

                if (field1.length() == 6 && field1.matches("[A-Z]{6}")) {
                    sourceCurrencyStr = field1.substring(0, 3);
                    targetCurrencyStr = field1.substring(3, 6);
                    sourceAmountStr = field2;
                    log.info("Malformed 2-column (concat currencies): src='{}', tgt='{}', amt='{}'",
                            sourceCurrencyStr, targetCurrencyStr, sourceAmountStr);
                } else if (field2.length() == 6 && field2.matches("[A-Z]{6}")) {
                    sourceAmountStr = field1;
                    sourceCurrencyStr = field2.substring(0, 3);
                    targetCurrencyStr = field2.substring(3, 6);
                    log.info("Malformed 2-column (amount, concat currencies): amt='{}', src='{}', tgt='{}'",
                            sourceAmountStr, sourceCurrencyStr, targetCurrencyStr);
                }
            }

            if (sourceAmountStr == null || sourceAmountStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing or empty sourceAmount");
            }
            if (sourceCurrencyStr == null || sourceCurrencyStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing or empty sourceCurrency");
            }
            if (targetCurrencyStr == null || targetCurrencyStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing or empty targetCurrency");
            }

            String cleanedAmountStr = sourceAmountStr.trim().replaceAll("[,%$\s]", "");
            BigDecimal sourceAmount;
            try {
                sourceAmount = new BigDecimal(cleanedAmountStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid sourceAmount format: '" + sourceAmountStr + "' (cleaned: '" + cleanedAmountStr + "')", e);
            }

            if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("sourceAmount must be positive: " + sourceAmount);
            }

            String cleanedSourceCurrency = sourceCurrencyStr.trim().toUpperCase();
            String cleanedTargetCurrency = targetCurrencyStr.trim().toUpperCase();

            if (cleanedSourceCurrency.length() != 3) {
                throw new IllegalArgumentException("Invalid sourceCurrency: '" + sourceCurrencyStr + "'. Must be 3 characters.");
            }
            if (cleanedTargetCurrency.length() != 3) {
                throw new IllegalArgumentException("Invalid targetCurrency: '" + targetCurrencyStr + "'. Must be 3 characters.");
            }

            if (cleanedSourceCurrency.equals(cleanedTargetCurrency)) {
                throw new IllegalArgumentException("Source and target currency cannot be the same: " + cleanedSourceCurrency);
            }

            log.debug("Successfully parsed record #{} for {}: Amount={}, From={}, To={}",
                    currentItemCount, originalFilename, sourceAmount, cleanedSourceCurrency, cleanedTargetCurrency);

            return ConversionRequest.builder()
                    .sourceAmount(sourceAmount)
                    .sourceCurrency(cleanedSourceCurrency)
                    .targetCurrency(cleanedTargetCurrency)
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("Error parsing CSV record #{} in file {}: {}. Record: {}",
                    currentItemCount, originalFilename, e.getMessage(), record.toList());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing CSV record #{} in file {}: {}. Record: {}",
                    currentItemCount, originalFilename, e.getMessage(), record.toList(), e);
            throw new RuntimeException("Unexpected error processing record: " + e.getMessage(), e);
        }
    }

    private String getValueByPossibleNames(Map<String, String> recordMap, String... possibleNames) {
        for (String name : possibleNames) {
            if (recordMap.containsKey(name)) {
                String value = recordMap.get(name);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(CURRENT_ITEM_COUNT_KEY, currentItemCount);
        log.debug("Saving CsvConversionItemReader state for {}: currentItemCount={}", originalFilename, currentItemCount);
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (csvParser != null) {
                csvParser.close();
            }
            
            // Clean up file content from centralized store
            if (contentKey != null) {
                boolean removed = fileContentStoreService.removeContent(contentKey);
                if (removed) {
                    log.info("Successfully cleaned up file content from store for key: {}", contentKey);
                } else {
                    log.warn("Failed to cleanup content for key: {} (may have been already removed)", contentKey);
                }
            }
            
            log.info("CsvConversionItemReader closed for file {}. Processed {} items.", originalFilename, currentItemCount);
        } catch (IOException e) {
            log.error("Error closing CSV parser for file {}: {}", originalFilename, e.getMessage(), e);
            throw new ItemStreamException("Error closing CSV resources for " + originalFilename, e);
        }
    }
} 