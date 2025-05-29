package com.hasandag.exchange.conversion.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobCompletionNotificationListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Starting batch job: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            long totalRead = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getReadCount).sum();
            long totalWritten = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getWriteCount).sum();
            
            log.info("Batch job completed - Read: {}, Written: {}", totalRead, totalWritten);
        } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error("Batch job failed with status: {}", jobExecution.getExitStatus().getExitCode());
        } else {
            log.warn("Batch job finished with status: {}", jobExecution.getStatus());
        }
    }
} 