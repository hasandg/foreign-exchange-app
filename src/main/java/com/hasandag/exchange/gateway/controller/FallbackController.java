package com.hasandag.exchange.gateway.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @GetMapping("/exchange-rates")
    public ResponseEntity<String> exchangeRatesFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Exchange Rate Service is currently unavailable. Please try again later.");
    }

    @GetMapping("/conversions")
    public ResponseEntity<String> conversionsFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Currency Conversion Service is currently unavailable. Please try again later.");
    }

    @PostMapping("/conversions")
    public ResponseEntity<String> conversionsPostFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Currency Conversion Service is currently unavailable. Please try again later.");
    }

    // 🔧 DEBUGGING ENDPOINTS - Circuit Breaker Status
    @GetMapping("/circuit-breaker/status")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Check all circuit breakers
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            Map<String, Object> cbStatus = new HashMap<>();
            cbStatus.put("state", cb.getState().toString());
            cbStatus.put("failureRate", cb.getMetrics().getFailureRate());
            cbStatus.put("numberOfCalls", cb.getMetrics().getNumberOfCalls());
            cbStatus.put("numberOfFailedCalls", cb.getMetrics().getNumberOfFailedCalls());
            status.put(cb.getName(), cbStatus);
        });
        
        return ResponseEntity.ok(status);
    }

    // 🔧 DEBUGGING ENDPOINTS - Reset Circuit Breaker
    @PostMapping("/circuit-breaker/{name}/reset")
    public ResponseEntity<String> resetCircuitBreaker(@PathVariable String name) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
            circuitBreaker.transitionToClosedState();
            return ResponseEntity.ok("Circuit breaker '" + name + "' has been reset to CLOSED state.");
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Failed to reset circuit breaker '" + name + "': " + e.getMessage());
        }
    }
} 