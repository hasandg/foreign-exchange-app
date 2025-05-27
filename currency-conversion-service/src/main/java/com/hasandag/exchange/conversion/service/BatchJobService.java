package com.hasandag.exchange.conversion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobService {

    private final JdbcTemplate jdbcTemplate;
    private final JobExplorer jobExplorer;

    public Map<String, Object> getJobStatus(Long jobId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            JobExecution jobExecution = jobExplorer.getJobExecution(jobId);
            
            if (jobExecution == null) {
                response.put("error", "Job not found");
                return response;
            }
            
            response.put("jobId", jobExecution.getId());
            response.put("jobInstanceId", jobExecution.getJobInstance().getInstanceId());
            response.put("jobName", jobExecution.getJobInstance().getJobName());
            response.put("status", jobExecution.getStatus().toString());
            response.put("startTime", jobExecution.getStartTime());
            response.put("endTime", jobExecution.getEndTime());
            response.put("exitStatus", jobExecution.getExitStatus().getExitCode());
            
            Map<String, Object> progress = new HashMap<>();
            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                progress.put("readCount", stepExecution.getReadCount());
                progress.put("writeCount", stepExecution.getWriteCount());
                progress.put("commitCount", stepExecution.getCommitCount());
                progress.put("totalSkipCount", 
                    stepExecution.getReadSkipCount() + 
                    stepExecution.getWriteSkipCount() + 
                    stepExecution.getProcessSkipCount());
                progress.put("readSkipCount", stepExecution.getReadSkipCount());
                progress.put("writeSkipCount", stepExecution.getWriteSkipCount());
                progress.put("processSkipCount", stepExecution.getProcessSkipCount());
                break; 
            }
            response.put("progress", progress);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving job status for job ID: {}", jobId, e);
            response.put("error", "Error retrieving job status: " + e.getMessage());
            return response;
        }
    }

    public Map<String, Object> getAllJobs() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sql = """
                SELECT je.job_execution_id, je.job_instance_id, je.status, je.start_time, 
                       je.end_time, je.create_time, ji.job_name
                FROM batch_job_execution je
                JOIN batch_job_instance ji ON je.job_instance_id = ji.job_instance_id
                ORDER BY je.job_execution_id DESC
                LIMIT 50
                """;
            
            List<Map<String, Object>> jobs = jdbcTemplate.queryForList(sql);
            response.put("jobs", jobs);
            response.put("totalJobs", jobs.size());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving job list", e);
            response.put("error", "Error retrieving job list: " + e.getMessage());
            return response;
        }
    }

    public Map<String, Object> processFileUploadAndStartJob(org.springframework.web.multipart.MultipartFile file, 
                                                           org.springframework.batch.core.launch.JobLauncher jobLauncher,
                                                           org.springframework.batch.core.Job bulkConversionJob) {
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

            String filePath = saveUploadedFile(file);
            
            org.springframework.batch.core.JobParameters jobParameters = createJobParameters(file, filePath);

            org.springframework.batch.core.JobExecution jobExecution = jobLauncher.run(bulkConversionJob, jobParameters);
            response.put("jobId", jobExecution.getJobId());
            response.put("jobInstanceId", jobExecution.getJobInstance().getInstanceId());
            response.put("status", jobExecution.getStatus().toString());
            response.put("message", "Job started");
            response.put("filename", file.getOriginalFilename());
            response.put("fileSize", file.getSize());

            log.info("Batch job started: {}, File: {}", 
                    jobExecution.getJobId(), file.getOriginalFilename());

            return response;

        } catch (Exception e) {
            return handleJobException(e);
        }
    }

    private boolean isValidFile(org.springframework.web.multipart.MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".csv");
    }

    private String saveUploadedFile(org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        String uploadDirectory = "./uploads";
        java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDirectory);
        
        if (!java.nio.file.Files.exists(uploadPath)) {
            java.nio.file.Files.createDirectories(uploadPath);
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String savedFileName = timestamp + "_" + file.getOriginalFilename();
        java.nio.file.Path filePath = uploadPath.resolve(savedFileName);
        
        java.nio.file.Files.copy(file.getInputStream(), filePath);
        log.info("File saved: {}", filePath);
        
        return filePath.toString();
    }

    private org.springframework.batch.core.JobParameters createJobParameters(org.springframework.web.multipart.MultipartFile file, String filePath) {
        return new org.springframework.batch.core.JobParametersBuilder()
                .addString("input.file.path", filePath)
                .addString("original.filename", file.getOriginalFilename())
                .addLong("file.size", file.getSize())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
    }

    private Map<String, Object> handleJobException(Exception e) {
        Map<String, Object> response = new HashMap<>();
        
        if (e instanceof org.springframework.batch.core.repository.JobExecutionAlreadyRunningException) {
            response.put("error", "A bulk conversion job is already running. Please wait for it to complete.");
            response.put("httpStatus", 409);
        } else if (e instanceof org.springframework.batch.core.repository.JobRestartException || 
                   e instanceof org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException) {
            response.put("error", "Job cannot be restarted. This file may have already been processed.");
            response.put("httpStatus", 409);
        } else if (e instanceof org.springframework.batch.core.JobParametersInvalidException) {
            response.put("error", "Invalid job parameters: " + e.getMessage());
            response.put("httpStatus", 400);
        } else if (e instanceof java.io.IOException) {
            log.error("File processing error", e);
            response.put("error", "Error processing uploaded file: " + e.getMessage());
            response.put("httpStatus", 500);
        } else {
            log.error("Unexpected error in batch job", e);
            response.put("error", "Unexpected error: " + e.getMessage());
            response.put("httpStatus", 500);
        }
        
        return response;
    }
} 