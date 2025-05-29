package com.hasandag.exchange.conversion.config;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.conversion.batch.ConversionItemProcessor;
import com.hasandag.exchange.conversion.batch.ConversionItemWriter;
import com.hasandag.exchange.conversion.batch.CsvConversionItemReader;
import com.hasandag.exchange.conversion.batch.JobCompletionNotificationListener;
import com.hasandag.exchange.conversion.client.ExchangeRateFeignClient;
import com.hasandag.exchange.conversion.kafka.producer.ConversionEventProducer;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Slf4j
public class BatchConfiguration {

    private final ExchangeRateFeignClient exchangeRateFeignClient;
    private final CurrencyConversionPostgresRepository postgresRepository;
    private final CurrencyConversionMongoRepository mongoRepository;
    private final ConversionEventProducer conversionEventProducer;

    public BatchConfiguration(ExchangeRateFeignClient exchangeRateFeignClient,
                              CurrencyConversionPostgresRepository postgresRepository,
                              @Autowired(required = false) CurrencyConversionMongoRepository mongoRepository,
                              @Autowired(required = false) ConversionEventProducer conversionEventProducer) {
        this.exchangeRateFeignClient = exchangeRateFeignClient;
        this.postgresRepository = postgresRepository;
        this.mongoRepository = mongoRepository;
        this.conversionEventProducer = conversionEventProducer;
    }

    @Bean
    public Job bulkConversionJob(JobRepository jobRepository, Step conversionStep, 
                                JobCompletionNotificationListener listener) {
        return new JobBuilder("bulkConversionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(conversionStep)
                .build();
    }

    @Bean
    public Step conversionStep(JobRepository jobRepository, 
                              PlatformTransactionManager transactionManager,
                              ItemReader<ConversionRequest> reader,
                              ItemProcessor<ConversionRequest, ConversionResponse> processor,
                              ItemWriter<ConversionResponse> writer,
                              @Qualifier("taskExecutor") TaskExecutor taskExecutor,
                              @Value("${conversion.batch.chunk-size:100}") int chunkSize,
                              @Value("${conversion.batch.skip-limit:1000}") int skipLimit) {
        return new StepBuilder("conversionStep", jobRepository)
                .<ConversionRequest, ConversionResponse>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(skipLimit)
                .skip(IllegalArgumentException.class)
                .skip(NumberFormatException.class)
                .skip(RuntimeException.class)
                .noSkip(NullPointerException.class)
                .taskExecutor(taskExecutor)
                .build();
    }

    @Bean
    public TaskExecutor taskExecutor(
            @Value("${conversion.batch.task-executor.core-pool-size:2}") int corePoolSize,
            @Value("${conversion.batch.task-executor.max-pool-size:4}") int maxPoolSize,
            @Value("${conversion.batch.task-executor.queue-capacity:100}") int queueCapacity,
            @Value("${conversion.batch.task-executor.thread-name-prefix:batch-}") String threadNamePrefix) {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }

    @Bean
    public ItemReader<ConversionRequest> csvItemReader() {
        return new CsvConversionItemReader();
    }

    @Bean
    public ItemProcessor<ConversionRequest, ConversionResponse> conversionItemProcessor() {
        return new ConversionItemProcessor(exchangeRateFeignClient, mongoRepository, postgresRepository);
    }

    @Bean
    public ItemWriter<ConversionResponse> conversionItemWriter() {
        return new ConversionItemWriter(mongoRepository, conversionEventProducer);
    }
} 