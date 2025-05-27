package com.hasandag.exchange.conversion.controller;

import com.hasandag.exchange.conversion.service.BatchJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Batch Jobs", description = "Batch processing operations for currency conversions")
public class CurrencyConversionBatchJobController {

    private final JobLauncher jobLauncher;
    private final Job bulkConversionJob;
    private final BatchJobService batchJobService;

    @PostMapping("/conversions")
    @Operation(summary = "Start a bulk conversion job", description = "Processes a file upload and starts a job")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job started successfully", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
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
    @Operation(summary = "Get job status", description = "Retrieves the status of a job")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job status retrieved successfully", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "Job not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
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
    @Operation(summary = "Get all jobs", description = "Retrieves a list of all jobs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Jobs retrieved successfully", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    public ResponseEntity<Map<String, Object>> getAllJobs() {
        Map<String, Object> response = batchJobService.getAllJobs();
        
        if (response.containsKey("error")) {
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/jobs/{jobName}")
    @Operation(summary = "Get jobs by name", description = "Retrieves paginated jobs filtered by job name")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Jobs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getJobsByName(
            @Parameter(description = "Job name to filter by", example = "bulkConversionJob")
            @PathVariable String jobName,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "10")
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
    @Operation(summary = "Get running jobs", description = "Retrieves all currently running batch jobs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Running jobs retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getRunningJobs() {
        Map<String, Object> response = batchJobService.getRunningJobs();
        
        if (response.containsKey("error")) {
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/statistics")
    @Operation(summary = "Get job statistics", description = "Retrieves statistical information about all batch jobs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getJobStatistics() {
        Map<String, Object> response = batchJobService.getJobStatistics();
        
        if (response.containsKey("error")) {
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversions/health")
    @Operation(summary = "Batch system health check", description = "Provides health status of the batch processing system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Batch system is healthy"),
        @ApiResponse(responseCode = "503", description = "Batch system is unhealthy")
    })
    public ResponseEntity<Map<String, Object>> getBatchHealthStatus() {
        Map<String, Object> response = new java.util.HashMap<>();
        
        try {
            // Get running jobs count
            Map<String, Object> runningJobsResponse = batchJobService.getRunningJobs();
            int runningCount = 0;
            if (!runningJobsResponse.containsKey("error")) {
                runningCount = (Integer) runningJobsResponse.get("count");
            }
            
            // Get statistics
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