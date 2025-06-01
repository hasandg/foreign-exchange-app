package com.hasandag.exchange.rate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates the differences between RestTemplate and RestClient
 * Shows why RestClient is the modern choice for Spring applications
 */
@Service
@Slf4j
public class RestTemplateVsRestClientComparison {

    public void explainDifferences() {
        log.info("=== RestTemplate vs RestClient Comparison ===");
        
        demonstrateApiDifferences();
        explainVirtualThreadCompatibility();
        showPerformanceComparison();
        explainMigrationPath();
    }

    private void demonstrateApiDifferences() {
        log.info("\n--- API Style Differences ---");
        
        log.info("RestTemplate (Traditional):");
        log.info("  RestTemplate restTemplate = new RestTemplate();");
        log.info("  String result = restTemplate.getForObject(url, String.class);");
        log.info("  ");
        log.info("  // For complex scenarios");
        log.info("  HttpHeaders headers = new HttpHeaders();");
        log.info("  headers.set(\"Authorization\", \"Bearer token\");");
        log.info("  HttpEntity<String> entity = new HttpEntity<>(headers);");
        log.info("  ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);");
        
        log.info("\nRestClient (Modern - Spring 6.1+):");
        log.info("  RestClient restClient = RestClient.create();");
        log.info("  String result = restClient.get().uri(url).retrieve().body(String.class);");
        log.info("  ");
        log.info("  // For complex scenarios - fluent API");
        log.info("  String result = restClient");
        log.info("    .get()");
        log.info("    .uri(url)");
        log.info("    .header(\"Authorization\", \"Bearer token\")");
        log.info("    .retrieve()");
        log.info("    .onStatus(status -> status.is5xxServerError(), (req, resp) -> { ... })");
        log.info("    .body(String.class);");
    }

    private void explainVirtualThreadCompatibility() {
        log.info("\n--- Virtual Thread Compatibility ---");
        
        log.info("Both RestTemplate and RestClient:");
        log.info("  ✅ Support blocking I/O (perfect for virtual threads)");
        log.info("  ✅ Use same underlying HTTP client (Apache HC, OkHttp, etc.)");
        log.info("  ✅ Benefit from connection pooling");
        log.info("  ✅ Work seamlessly with CompletableFuture and virtual thread executors");
        
        log.info("\nVirtual Thread Usage Pattern:");
        log.info("  CompletableFuture.supplyAsync(() -> {");
        log.info("    // This blocking call is efficient with virtual threads");
        log.info("    return restClient.get().uri(url).retrieve().body(String.class);");
        log.info("  }, virtualThreadExecutor);");
    }

    private void showPerformanceComparison() {
        log.info("\n--- Performance Comparison ---");
        
        log.info("Performance (same underlying HTTP client):");
        log.info("  RestTemplate: Baseline performance");
        log.info("  RestClient:   Same performance + better developer experience");
        log.info("  ");
        log.info("Memory Usage:");
        log.info("  RestTemplate: Standard object creation");
        log.info("  RestClient:   Slightly more efficient (builder pattern optimizations)");
        log.info("  ");
        log.info("Code Readability:");
        log.info("  RestTemplate: ⭐⭐⭐ (verbose for complex scenarios)");
        log.info("  RestClient:   ⭐⭐⭐⭐⭐ (fluent, readable API)");
    }

    private void explainMigrationPath() {
        log.info("\n--- Migration Path ---");
        
        log.info("When to use each:");
        log.info("  RestTemplate:");
        log.info("    - Legacy codebases");
        log.info("    - Spring Boot < 3.2");
        log.info("    - Team familiarity");
        log.info("  ");
        log.info("  RestClient:");
        log.info("    - New projects");
        log.info("    - Spring Boot 3.2+");
        log.info("    - Modern, readable code preferred");
        log.info("    - Complex HTTP scenarios");
        
        log.info("\nMigration Strategy:");
        log.info("  1. Keep existing RestTemplate code");
        log.info("  2. Use RestClient for new features");
        log.info("  3. Gradually migrate high-maintenance areas");
        log.info("  4. Both can coexist in same application");
    }

    public void demonstrateRealWorldScenarios() {
        log.info("\n=== Real-World Scenarios ===");
        
        demonstrateErrorHandling();
        demonstrateAuthenticationFlow();
        demonstrateRetryLogic();
    }

    private void demonstrateErrorHandling() {
        log.info("\n--- Error Handling ---");
        
        log.info("RestTemplate error handling:");
        log.info("  try {");
        log.info("    String result = restTemplate.getForObject(url, String.class);");
        log.info("  } catch (HttpClientErrorException e) {");
        log.info("    if (e.getStatusCode() == HttpStatus.NOT_FOUND) { ... }");
        log.info("  } catch (HttpServerErrorException e) { ... }");
        
        log.info("\nRestClient error handling (fluent):");
        log.info("  String result = restClient");
        log.info("    .get().uri(url)");
        log.info("    .retrieve()");
        log.info("    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {");
        log.info("      // Handle 4xx errors");
        log.info("    })");
        log.info("    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {");
        log.info("      // Handle 5xx errors");
        log.info("    })");
        log.info("    .body(String.class);");
    }

    private void demonstrateAuthenticationFlow() {
        log.info("\n--- Authentication Flow ---");
        
        log.info("RestTemplate with auth:");
        log.info("  HttpHeaders headers = new HttpHeaders();");
        log.info("  headers.setBearerAuth(token);");
        log.info("  HttpEntity<?> entity = new HttpEntity<>(headers);");
        log.info("  ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);");
        
        log.info("\nRestClient with auth (cleaner):");
        log.info("  String result = restClient");
        log.info("    .get().uri(url)");
        log.info("    .header(\"Authorization\", \"Bearer \" + token)");
        log.info("    .retrieve()");
        log.info("    .body(String.class);");
    }

    private void demonstrateRetryLogic() {
        log.info("\n--- Retry Logic with Virtual Threads ---");
        
        log.info("Both RestTemplate and RestClient with virtual threads:");
        log.info("  CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {");
        log.info("    for (int attempt = 1; attempt <= maxRetries; attempt++) {");
        log.info("      try {");
        log.info("        return httpClient.get().uri(url).retrieve().body(String.class);");
        log.info("      } catch (Exception e) {");
        log.info("        if (attempt == maxRetries) throw e;");
        log.info("        Thread.sleep(backoffTime * attempt); // Perfect for virtual threads!");
        log.info("      }");
        log.info("    }");
        log.info("  }, virtualThreadExecutor);");
        
        log.info("\nVirtual threads make retry logic simple:");
        log.info("  ✅ Thread.sleep() doesn't block carrier threads");
        log.info("  ✅ Can handle thousands of concurrent retries");
        log.info("  ✅ No need for complex reactive retry operators");
        log.info("  ✅ Simple, readable synchronous code");
    }
} 