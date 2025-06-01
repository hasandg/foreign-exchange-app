package com.hasandag.exchange.conversion.config;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.conversion.batch.ConversionItemProcessor;
import com.hasandag.exchange.conversion.batch.ConversionItemWriter;
import com.hasandag.exchange.conversion.batch.CsvConversionItemReader;
import com.hasandag.exchange.conversion.batch.JobCompletionNotificationListener;
import com.hasandag.exchange.conversion.batch.CurrencyConversionMongoItemWriter;
import com.hasandag.exchange.conversion.batch.CurrencyConversionPostgresItemWriter;
import com.hasandag.exchange.conversion.client.ExchangeRateFeignClient;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpStatusCodeException;
import feign.FeignException;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class BatchConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ExchangeRateFeignClient exchangeRateFeignClient;
    private final CurrencyConversionMongoRepository mongoRepository;
    private final CurrencyConversionPostgresRepository postgresRepository;

    @Bean
    public ItemReader<ConversionRequest> csvItemReader() {
        return new CsvConversionItemReader();
    }

    @Bean
    public ItemProcessor<ConversionRequest, ConversionResponse> batchConversionItemProcessor() {
        return new ConversionItemProcessor(exchangeRateFeignClient, postgresRepository, mongoRepository);
    }

    private CurrencyConversionPostgresItemWriter createPostgresItemWriter() {
        return new CurrencyConversionPostgresItemWriter(postgresRepository);
    }

    private CurrencyConversionMongoItemWriter createMongoItemWriter() {
        return new CurrencyConversionMongoItemWriter(mongoRepository);
    }

    @Bean
    public ItemWriter<ConversionResponse> batchConversionItemWriter() {
        List<ItemWriter<? super ConversionResponse>> delegates = new ArrayList<>();

        if (mongoRepository != null) {
            delegates.add(createMongoItemWriter());
        }
        delegates.add(createPostgresItemWriter());

        return new ConversionItemWriter(delegates);
    }

    @Bean
    public Step conversionCsvProcessingStep(
            ItemReader<ConversionRequest> reader,
            ItemProcessor<ConversionRequest, ConversionResponse> processor,
            ItemWriter<ConversionResponse> writer,
            @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor,
            @Value("${conversion.batch.chunk-size:100}") int chunkSize,
            @Value("${conversion.batch.skip-limit:1000}") int skipLimit,
            @Value("${conversion.batch.retry-limit:3}") int retryLimit) {

        log.info("Configuring conversionCsvProcessingStep with chunkSize={}, skipLimit={}, retryLimit={}",
                chunkSize, skipLimit, retryLimit);

        return new StepBuilder("conversionCsvProcessingStep", jobRepository)
                .<ConversionRequest, ConversionResponse>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(skipLimit)
                .skip(IllegalArgumentException.class)
                .skip(java.util.NoSuchElementException.class)
                .retryLimit(retryLimit)
                .retry(FeignException.class)
                .retry(HttpStatusCodeException.class)
                .noRetry(OptimisticLockingFailureException.class)
                .taskExecutor(taskExecutor)
                .build();
    }

    @Bean
    public Job bulkConversionJob(Step conversionCsvProcessingStep) {
        log.info("Configuring bulkConversionJob");
        return new JobBuilder("bulkConversionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .flow(conversionCsvProcessingStep)
                .end()
                .build();
    }

    @Bean
    @Qualifier("batchTaskExecutor")
    public TaskExecutor batchTaskExecutor(
            @Value("${conversion.batch.task-executor.core-pool-size:2}") int corePoolSize,
            @Value("${conversion.batch.task-executor.max-pool-size:4}") int maxPoolSize,
            @Value("${conversion.batch.task-executor.queue-capacity:100}") int queueCapacity,
            @Value("${conversion.batch.task-executor.thread-name-prefix:csv-batch-}") String threadNamePrefix) {

        log.info("Configuring batchTaskExecutor: corePoolSize={}, maxPoolSize={}, queueCapacity={}, threadNamePrefix={}",
                corePoolSize, maxPoolSize, queueCapacity, threadNamePrefix);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
} 