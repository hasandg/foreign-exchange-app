package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.conversion.service.BatchJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
@Slf4j
public class CurrencyConversionBatchJobController {

    private final JobLauncher jobLauncher;
    private final Job bulkConversionJob;
    private final BatchJobService batchJobService;

    @PostMapping("/conversions")
    public ResponseEntity<Map<String, Object>> startBulkConversionJob(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = batchJobService.processFileUploadAndStartJob(file, jobLauncher, bulkConversionJob);
        
        if (response.containsKey("error")) {
            Integer httpStatus = (Integer) response.get("httpStatus");
            if (httpStatus != null) {
                response.remove("httpStatus");
                return ResponseEntity.status(httpStatus).body(response);
            }
            return ResponseEntity.badRequest().body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/{jobId}/status")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long jobId) {
        Map<String, Object> response = batchJobService.getJobStatus(jobId);
        
        if (response.containsKey("error") && "Job not found".equals(response.get("error"))) {
            return ResponseEntity.notFound().build();
        }
        
        if (response.containsKey("error")) {
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/jobs")
    public ResponseEntity<Map<String, Object>> getAllJobs() {
        Map<String, Object> response = batchJobService.getAllJobs();
        
        if (response.containsKey("error")) {
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
} 