package com.hasandag.exchange.conversion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FileContentStoreService {

    private static final Map<String, String> FILE_CONTENT_STORE = new ConcurrentHashMap<>();

    public void storeContent(String contentKey, String fileContent) {
        FILE_CONTENT_STORE.put(contentKey, fileContent);
        log.info("Stored file content with key: {} (size: {} chars)", contentKey, fileContent.length());
    }

    public String getContent(String contentKey) {
        String content = FILE_CONTENT_STORE.get(contentKey);
        if (content == null) {
            log.error("File content not found in store for key: {}", contentKey);
            log.debug("Available keys in store: {}", FILE_CONTENT_STORE.keySet());
        } else {
            log.debug("Retrieved file content for key: {} (size: {} chars)", contentKey, content.length());
        }
        return content;
    }

    public boolean removeContent(String contentKey) {
        String removedContent = FILE_CONTENT_STORE.remove(contentKey);
        if (removedContent != null) {
            log.info("Cleaned up file content for key: {} (size: {} chars)", contentKey, removedContent.length());
            return true;
        } else {
            log.warn("Attempted to remove non-existent content key: {}", contentKey);
            return false;
        }
    }

    public boolean hasContent(String contentKey) {
        return FILE_CONTENT_STORE.containsKey(contentKey);
    }

    public Map<String, Object> getStoreStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("storeSize", FILE_CONTENT_STORE.size());
        stats.put("availableKeys", FILE_CONTENT_STORE.keySet());
        stats.put("totalMemoryUsage", FILE_CONTENT_STORE.values().stream()
                .mapToInt(String::length)
                .sum());
        return stats;
    }

    public int clearAllContent() {
        int removedCount = FILE_CONTENT_STORE.size();
        FILE_CONTENT_STORE.clear();
        log.warn("Emergency cleanup performed - removed {} content entries", removedCount);
        return removedCount;
    }

    public String generateContentKey(String filename) {
        return "job_" + System.currentTimeMillis() + "_" + System.nanoTime() + "_" + filename;
    }
} 