package com.hasandag.exchange.rate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

/**
 * Demonstrates performance differences between HTTP clients in virtual thread environment
 */
@Service
@Slf4j
public class HttpClientPerformanceDemo {

    private final RestTemplate simpleRestTemplate;
    private final RestTemplate apacheHttpClient5RestTemplate;
    private final RestTemplate virtualThreadOptimizedRestTemplate;
    private final Executor virtualThreadExecutor;

    public HttpClientPerformanceDemo(
            @Qualifier("simpleRestTemplate") RestTemplate simpleRestTemplate,
            @Qualifier("apacheHttpClient5RestTemplate") RestTemplate apacheHttpClient5RestTemplate,
            @Qualifier("virtualThreadOptimizedRestTemplate") RestTemplate virtualThreadOptimizedRestTemplate,
            @Qualifier("externalServiceExecutor") Executor virtualThreadExecutor) {
        this.simpleRestTemplate = simpleRestTemplate;
        this.apacheHttpClient5RestTemplate = apacheHttpClient5RestTemplate;
        this.virtualThreadOptimizedRestTemplate = virtualThreadOptimizedRestTemplate;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    /**
     * Compare performance of different HTTP clients with virtual threads
     */
    public void compareHttpClientPerformance() {
        String testUrl = "https://httpbin.org/delay/1"; // 1 second delay endpoint
        int concurrentRequests = 50;
        
        log.info("=== HTTP Client Performance Comparison ===");
        log.info("Test URL: {}", testUrl);
        log.info("Concurrent requests: {}", concurrentRequests);
        log.info("Each request has 1 second server delay");
        
        // Test Simple HTTP Client
        testHttpClientPerformance("Simple HTTP Client", simpleRestTemplate, testUrl, concurrentRequests);
        
        // Test Apache HTTP Components Client 5
        testHttpClientPerformance("Apache HTTP Components Client 5", apacheHttpClient5RestTemplate, testUrl, concurrentRequests);
        
        // Test Virtual Thread Optimized
        testHttpClientPerformance("Virtual Thread Optimized", virtualThreadOptimizedRestTemplate, testUrl, concurrentRequests);
    }

    private void testHttpClientPerformance(String clientName, RestTemplate restTemplate, String url, int requests) {
        log.info("\n--- Testing {} ---", clientName);
        
        Instant start = Instant.now();
        
        try {
            // Create concurrent requests using virtual threads
            List<CompletableFuture<String>> futures = IntStream.range(0, requests)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            String threadName = Thread.currentThread().getName();
                            log.debug("Request {} executing on thread: {}", i, threadName);
                            
                            // Make HTTP call
                            return restTemplate.getForObject(url, String.class);
                        } catch (Exception e) {
                            log.error("Request {} failed: {}", i, e.getMessage());
                            return "ERROR: " + e.getMessage();
                        }
                    }, virtualThreadExecutor))
                    .toList();
            
            // Wait for all requests to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // Calculate results
            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            
            long successCount = futures.stream()
                    .mapToLong(f -> f.join().startsWith("ERROR") ? 0 : 1)
                    .sum();
            
            log.info("Results for {}:", clientName);
            log.info("  Total time: {} ms", duration.toMillis());
            log.info("  Successful requests: {}/{}", successCount, requests);
            log.info("  Average time per request: {} ms", duration.toMillis() / requests);
            log.info("  Requests per second: {}", (requests * 1000.0) / duration.toMillis());
            
        } catch (Exception e) {
            log.error("Test failed for {}: {}", clientName, e.getMessage());
        }
    }

    /**
     * Demonstrate virtual thread scaling capabilities
     */
    public void demonstrateVirtualThreadScaling() {
        log.info("\n=== Virtual Thread Scaling Demonstration ===");
        
        int[] threadCounts = {100, 500, 1000, 2000};
        
        for (int threadCount : threadCounts) {
            log.info("\nTesting with {} virtual threads:", threadCount);
            
            Instant start = Instant.now();
            
            List<CompletableFuture<Void>> tasks = IntStream.range(0, threadCount)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            // Simulate work with blocking operation
                            Thread.sleep(100);
                            
                            if (i % 100 == 0) {
                                log.debug("Virtual thread {} completed work", i);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, virtualThreadExecutor))
                    .toList();
            
            // Wait for all tasks
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
            
            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            
            log.info("  {} virtual threads completed in {} ms", threadCount, duration.toMillis());
            log.info("  Theoretical minimum time: 100ms (due to sleep)");
            log.info("  Actual overhead: {} ms", duration.toMillis() - 100);
        }
    }

    /**
     * Explain the architecture differences
     */
    public void explainArchitecture() {
        log.info("\n=== RestTemplate + HTTP Client Architecture ===");
        
        log.info("1. RestTemplate Layer:");
        log.info("   RestTemplate restTemplate = new RestTemplate();");
        log.info("   restTemplate.getForObject(url, String.class);");
        log.info("   ↓");
        
        log.info("2. ClientHttpRequestFactory Layer:");
        log.info("   - SimpleClientHttpRequestFactory (default)");
        log.info("   - HttpComponentsClientHttpRequestFactory (Apache HC)");
        log.info("   - OkHttp3ClientHttpRequestFactory (OkHttp)");
        log.info("   ↓");
        
        log.info("3. Actual HTTP Client Layer:");
        log.info("   - HttpURLConnection (Simple)");
        log.info("   - Apache HTTP Components Client (Pooled)");
        log.info("   - OkHttp Client (Pooled)");
        log.info("   ↓");
        
        log.info("4. Network Layer:");
        log.info("   - TCP Connection");
        log.info("   - HTTP Protocol");
        log.info("   - TLS/SSL (for HTTPS)");
        
        log.info("\nConnection Pooling Impact:");
        log.info("Without pooling: Request → New TCP → HTTP → Close TCP");
        log.info("With pooling:    Request → Reuse TCP → HTTP → Keep TCP");
        log.info("Performance gain: 10x-50x faster for subsequent requests");
    }
} 