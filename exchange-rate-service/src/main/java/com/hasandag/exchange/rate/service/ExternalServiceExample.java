package com.hasandag.exchange.rate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExternalServiceExample {

    private final RestTemplate restTemplate;
    
    @Qualifier("externalServiceExecutor")
    private final Executor externalServiceExecutor;
    
    @Qualifier("thirdPartyApiExecutor")
    private final Executor thirdPartyApiExecutor;

    @Async("externalServiceExecutor")
    public CompletableFuture<String> callExternalService(String url) {
        log.info("Making external service call to {} using virtual thread: {}", 
                url, Thread.currentThread().getName());
        
        try {
            // Simulate external service call
            String response = restTemplate.getForObject(url, String.class);
            log.info("Received response from {}: {}", url, response != null ? response.substring(0, Math.min(100, response.length())) : "null");
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("Error calling external service {}: {}", url, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("thirdPartyApiExecutor")
    public CompletableFuture<String> callThirdPartyApi(String apiUrl, String endpoint) {
        log.info("Making third-party API call to {}/{} using virtual thread: {}", 
                apiUrl, endpoint, Thread.currentThread().getName());
        
        try {
            String fullUrl = apiUrl + "/" + endpoint;
            String response = restTemplate.getForObject(fullUrl, String.class);
            log.info("Third-party API response from {}: {}", fullUrl, response != null ? "Success" : "null");
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("Error calling third-party API {}/{}: {}", apiUrl, endpoint, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<String>> callMultipleServicesAsync(List<String> urls) {
        log.info("Making {} concurrent external service calls using virtual threads", urls.size());
        
        List<CompletableFuture<String>> futures = urls.stream()
                .map(this::callExternalService)
                .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    public CompletableFuture<Void> demonstrateVirtualThreadScaling() {
        log.info("Demonstrating virtual thread scaling with 1000 concurrent tasks");
        
        List<CompletableFuture<Void>> tasks = IntStream.range(0, 1000)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        log.debug("Task {} running on virtual thread: {}", i, Thread.currentThread().getName());
                        Thread.sleep(100); // Simulate some work
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Task {} interrupted", i);
                    }
                }, externalServiceExecutor))
                .toList();
        
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .thenRun(() -> log.info("All 1000 virtual thread tasks completed"));
    }
} 