package com.hasandag.exchange.conversion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncBatchJobService {

    private final JobLauncher jobLauncher;
    private final FileContentStoreService fileContentStoreService;
    
    private static final Map<String, Map<String, Object>> JOB_STATUS_STORE = new ConcurrentHashMap<>();

    public Map<String, Object> processJobAsync(MultipartFile file, Job bulkConversionJob) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("error", "File is empty");
                return response;
            }

            if (!isValidFile(file)) {
                response.put("error", "Invalid file type. Please upload a CSV file.");
                return response;
            }

            String fileContent = new String(file.getBytes(), "UTF-8");
            
            String contentKey = fileContentStoreService.generateContentKey(file.getOriginalFilename());
            fileContentStoreService.storeContent(contentKey, fileContent);
            
            String tempJobId = "async_" + System.currentTimeMillis();
            
            Map<String, Object> jobStatus = new HashMap<>();
            jobStatus.put("status", "SUBMITTED");
            jobStatus.put("filename", file.getOriginalFilename());
            jobStatus.put("fileSize", file.getSize());
            jobStatus.put("contentKey", contentKey);
            jobStatus.put("submitTime", System.currentTimeMillis());
            JOB_STATUS_STORE.put(tempJobId, jobStatus);
            
            launchJobInBackground(file, bulkConversionJob, contentKey, tempJobId);
            
            response.put("jobId", tempJobId);
            response.put("status", "SUBMITTED");
            response.put("message", "Job submitted successfully and will be processed asynchronously");
            response.put("filename", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("contentKey", contentKey);
            response.put("asyncMode", true);

            log.info("🚀 Job submitted immediately with temp ID: {}, File: {}", tempJobId, file.getOriginalFilename());
            return response;

        } catch (Exception e) {
            log.error("Error submitting async job", e);
            response.put("error", "Error submitting job: " + e.getMessage());
            return response;
        }
    }

    @Async
    public CompletableFuture<Void> launchJobInBackground(MultipartFile file, Job bulkConversionJob, 
                                                        String contentKey, String tempJobId) {
        try {
            log.info("🔄 Starting background job execution for: {}", tempJobId);
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("file.content.key", contentKey)
                    .addString("original.filename", file.getOriginalFilename())
                    .addLong("file.size", file.getSize())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution jobExecution = jobLauncher.run(bulkConversionJob, jobParameters);
            
            Map<String, Object> jobStatus = JOB_STATUS_STORE.get(tempJobId);
            if (jobStatus != null) {
                jobStatus.put("realJobId", jobExecution.getJobId());
                jobStatus.put("jobInstanceId", jobExecution.getJobInstance().getInstanceId());
                jobStatus.put("status", jobExecution.getStatus().toString());
                jobStatus.put("startTime", jobExecution.getStartTime());
                jobStatus.put("endTime", jobExecution.getEndTime());
            }
            
            log.info("✅ Background job completed for temp ID: {} -> real ID: {}, Status: {}", 
                    tempJobId, jobExecution.getJobId(), jobExecution.getStatus());
            
        } catch (Exception e) {
            log.error("❌ Background job failed for temp ID: {}", tempJobId, e);
            
            Map<String, Object> jobStatus = JOB_STATUS_STORE.get(tempJobId);
            if (jobStatus != null) {
                jobStatus.put("status", "FAILED");
                jobStatus.put("error", e.getMessage());
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }

    public Map<String, Object> getAsyncJobStatus(String tempJobId) {
        Map<String, Object> jobStatus = JOB_STATUS_STORE.get(tempJobId);
        if (jobStatus == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Job not found");
            return response;
        }
        return new HashMap<>(jobStatus);
    }

    public void cleanupCompletedJobs() {
        JOB_STATUS_STORE.entrySet().removeIf(entry -> {
            String status = (String) entry.getValue().get("status");
            return "COMPLETED".equals(status) || "FAILED".equals(status);
        });
    }

    private boolean isValidFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".csv");
    }
} 