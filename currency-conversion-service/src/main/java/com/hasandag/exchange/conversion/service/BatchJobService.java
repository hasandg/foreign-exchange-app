package com.hasandag.exchange.conversion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchJobService {

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
            List<String> jobNames = jobExplorer.getJobNames();
            List<Map<String, Object>> allJobs = new java.util.ArrayList<>();
            
            for (String jobName : jobNames) {
                List<JobInstance> jobInstances = jobExplorer.getJobInstances(jobName, 0, 50);
                
                for (JobInstance jobInstance : jobInstances) {
                    List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(jobInstance);
                    
                    for (JobExecution jobExecution : jobExecutions) {
                        Map<String, Object> jobInfo = new HashMap<>();
                        jobInfo.put("job_execution_id", jobExecution.getId());
                        jobInfo.put("job_instance_id", jobInstance.getInstanceId());
                        jobInfo.put("job_name", jobInstance.getJobName());
                        jobInfo.put("status", jobExecution.getStatus().toString());
                        jobInfo.put("start_time", jobExecution.getStartTime());
                        jobInfo.put("end_time", jobExecution.getEndTime());
                        jobInfo.put("create_time", jobExecution.getCreateTime());
                        jobInfo.put("exit_code", jobExecution.getExitStatus().getExitCode());
                        
                        Map<String, Object> parameters = new HashMap<>();
                        if (jobExecution.getJobParameters() != null) {
                            jobExecution.getJobParameters().getParameters().forEach((key, value) -> {
                                if (!"file.content".equals(key)) {
                                    parameters.put(key, value.getValue());
                                }
                            });
                        }
                        jobInfo.put("parameters", parameters);
                        
                        allJobs.add(jobInfo);
                    }
                }
            }
            
            allJobs.sort((a, b) -> Long.compare(
                (Long) b.get("job_execution_id"), 
                (Long) a.get("job_execution_id")
            ));
            
            List<Map<String, Object>> limitedJobs = allJobs.stream()
                    .limit(50)
                    .collect(java.util.stream.Collectors.toList());
            
            response.put("jobs", limitedJobs);
            response.put("totalJobs", limitedJobs.size());
            
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

            String fileContent = new String(file.getBytes(), "UTF-8");
            
            org.springframework.batch.core.JobParameters jobParameters = createJobParameters(file, fileContent);

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

    public Map<String, Object> getJobsByName(String jobName, int page, int size) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            int start = page * size;
            List<JobInstance> jobInstances = jobExplorer.getJobInstances(jobName, start, size);
            List<Map<String, Object>> jobs = new java.util.ArrayList<>();
            
            for (JobInstance jobInstance : jobInstances) {
                List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(jobInstance);
                
                for (JobExecution jobExecution : jobExecutions) {
                    Map<String, Object> jobInfo = createJobInfoMap(jobExecution, jobInstance);
                    jobs.add(jobInfo);
                }
            }
            
            int totalCount = (int) jobExplorer.getJobInstanceCount(jobName);
            
            response.put("jobs", jobs);
            response.put("totalJobs", totalCount);
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) totalCount / size));
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving jobs by name: {}", jobName, e);
            response.put("error", "Error retrieving jobs: " + e.getMessage());
            return response;
        }
    }

    public Map<String, Object> getRunningJobs() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> jobNames = jobExplorer.getJobNames();
            List<Map<String, Object>> runningJobs = new java.util.ArrayList<>();
            
            for (String jobName : jobNames) {
                Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(jobName);
                
                for (JobExecution jobExecution : runningExecutions) {
                    Map<String, Object> jobInfo = createJobInfoMap(jobExecution, jobExecution.getJobInstance());
                    runningJobs.add(jobInfo);
                }
            }
            
            response.put("runningJobs", runningJobs);
            response.put("count", runningJobs.size());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving running jobs", e);
            response.put("error", "Error retrieving running jobs: " + e.getMessage());
            return response;
        }
    }

    public Map<String, Object> getJobStatistics() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> jobNames = jobExplorer.getJobNames();
            Map<String, Object> statistics = new HashMap<>();
            
            for (String jobName : jobNames) {
                Map<String, Object> jobStats = new HashMap<>();
                
                int totalInstances = (int) jobExplorer.getJobInstanceCount(jobName);
                jobStats.put("totalInstances", totalInstances);
                
                Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(jobName);
                jobStats.put("runningCount", runningExecutions.size());
                
                List<JobInstance> recentInstances = jobExplorer.getJobInstances(jobName, 0, 100);
                int completedCount = 0;
                int failedCount = 0;
                
                for (JobInstance instance : recentInstances) {
                    List<JobExecution> executions = jobExplorer.getJobExecutions(instance);
                    if (!executions.isEmpty()) {
                        JobExecution latestExecution = executions.get(executions.size() - 1);
                        switch (latestExecution.getStatus()) {
                            case COMPLETED -> completedCount++;
                            case FAILED -> failedCount++;
                        }
                    }
                }
                
                jobStats.put("completedCount", completedCount);
                jobStats.put("failedCount", failedCount);
                
                statistics.put(jobName, jobStats);
            }
            
            response.put("statistics", statistics);
            response.put("totalJobTypes", jobNames.size());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error retrieving job statistics", e);
            response.put("error", "Error retrieving job statistics: " + e.getMessage());
            return response;
        }
    }

    private Map<String, Object> createJobInfoMap(JobExecution jobExecution, JobInstance jobInstance) {
        Map<String, Object> jobInfo = new HashMap<>();
        jobInfo.put("job_execution_id", jobExecution.getId());
        jobInfo.put("job_instance_id", jobInstance.getInstanceId());
        jobInfo.put("job_name", jobInstance.getJobName());
        jobInfo.put("status", jobExecution.getStatus().toString());
        jobInfo.put("start_time", jobExecution.getStartTime());
        jobInfo.put("end_time", jobExecution.getEndTime());
        jobInfo.put("create_time", jobExecution.getCreateTime());
        jobInfo.put("exit_code", jobExecution.getExitStatus().getExitCode());
        
        Map<String, Object> parameters = new HashMap<>();
        if (jobExecution.getJobParameters() != null) {
            jobExecution.getJobParameters().getParameters().forEach((key, value) -> {
                if (!"file.content".equals(key)) {
                    parameters.put(key, value.getValue());
                }
            });
        }
        jobInfo.put("parameters", parameters);
        
        Map<String, Object> progress = new HashMap<>();
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            progress.put("readCount", stepExecution.getReadCount());
            progress.put("writeCount", stepExecution.getWriteCount());
            progress.put("commitCount", stepExecution.getCommitCount());
            progress.put("skipCount", stepExecution.getSkipCount());
            break;
        }
        jobInfo.put("progress", progress);
        
        return jobInfo;
    }

    private boolean isValidFile(org.springframework.web.multipart.MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".csv");
    }

    private org.springframework.batch.core.JobParameters createJobParameters(org.springframework.web.multipart.MultipartFile file, String fileContent) {
        return new org.springframework.batch.core.JobParametersBuilder()
                .addString("file.content", fileContent)
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