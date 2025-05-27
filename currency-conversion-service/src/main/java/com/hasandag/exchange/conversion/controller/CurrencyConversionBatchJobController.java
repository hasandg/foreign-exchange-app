package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.conversion.service.BatchJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
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

    @GetMapping("/conversions/jobs/{jobName}")
    public ResponseEntity<Map<String, Object>> getJobsByName(
            @PathVariable String jobName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        if (size > 100) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Page size cannot exceed 100")
            );
        }
        
        Map<String, Object> response = batchJobService.getJobsByName(jobName, page, size);
        
        if (response.containsKey("error")) {
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/jobs/running")
    public ResponseEntity<Map<String, Object>> getRunningJobs() {
        Map<String, Object> response = batchJobService.getRunningJobs();
        
        if (response.containsKey("error")) {
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/statistics")
    public ResponseEntity<Map<String, Object>> getJobStatistics() {
        Map<String, Object> response = batchJobService.getJobStatistics();
        
        if (response.containsKey("error")) {
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/health")
    public ResponseEntity<Map<String, Object>> getBatchHealthStatus() {
        Map<String, Object> response = new java.util.HashMap<>();
        
        try {
            Map<String, Object> runningJobsResponse = batchJobService.getRunningJobs();
            int runningCount = 0;
            if (!runningJobsResponse.containsKey("error")) {
                runningCount = (Integer) runningJobsResponse.get("count");
            }
            
            Map<String, Object> statsResponse = batchJobService.getJobStatistics();
            
            response.put("status", "UP");
            response.put("runningJobs", runningCount);
            response.put("timestamp", java.time.Instant.now());
            
            if (!statsResponse.containsKey("error")) {
                response.put("totalJobTypes", statsResponse.get("totalJobTypes"));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking batch health status", e);
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            response.put("timestamp", java.time.Instant.now());
            return ResponseEntity.status(503).body(response);
        }
    }
} 