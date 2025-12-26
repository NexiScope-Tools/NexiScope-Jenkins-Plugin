package io.nexiscope.jenkins.plugin.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Rate limiter to prevent abuse and Denial-of-Service (DoS) attacks.
 * 
 * <p>This class implements a sliding window rate limiting algorithm to control
 * the frequency of operations like test connections, event sending, and configuration
 * saves. It provides protection against both accidental and malicious overuse.</p>
 * 
 * <h2>Features:</h2>
 * <ul>
 *   <li><b>Per-Operation Limits</b>: Different limits for different operations</li>
 *   <li><b>Sliding Window</b>: More accurate than fixed window, prevents burst attacks</li>
 *   <li><b>Per-User Tracking</b>: Separate limits for each user/identifier</li>
 *   <li><b>Automatic Cleanup</b>: Old entries are periodically removed to prevent memory leaks</li>
 *   <li><b>Thread-Safe</b>: Uses concurrent data structures for multi-threaded environments</li>
 * </ul>
 * 
 * <h2>Rate Limits:</h2>
 * <ul>
 *   <li><b>Test Connection</b>: 10 requests per minute</li>
 *   <li><b>Event Send</b>: 10,000 events per minute</li>
 *   <li><b>Config Save</b>: 30 saves per minute</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>
 * String userId = Jenkins.getAuthentication().getName();
 * if (!RateLimiter.isAllowed(Operation.TEST_CONNECTION, userId)) {
 *     return FormValidation.error("Rate limit exceeded. Please try again later.");
 * }
 * // Proceed with operation
 * </pre>
 * 
 * <h2>Implementation:</h2>
 * <p>Uses a sliding window algorithm where each request is tracked with a timestamp.
 * When checking if a request is allowed, expired requests (older than the time window)
 * are removed, and the remaining count is compared against the limit.</p>
 * 
 * @author NexiScope Team
 * @since 1.0.0
 * @see Operation
 */
public class RateLimiter {
    
    private static final Logger LOGGER = Logger.getLogger(RateLimiter.class.getName());
    
    // Rate limit configurations (requests per minute)
    private static final int TEST_CONNECTION_LIMIT = 10; // 10 test connections per minute
    private static final int EVENT_SEND_LIMIT = 10000; // 10,000 events per minute (very generous)
    private static final int CONFIG_SAVE_LIMIT = 30; // 30 config saves per minute
    
    // Time window in milliseconds
    private static final long TIME_WINDOW_MS = 60000; // 1 minute
    
    // Cleanup interval
    private static final long CLEANUP_INTERVAL_MS = 300000; // 5 minutes
    private static long lastCleanup = System.currentTimeMillis();
    
    // Rate limit trackers
    private static final Map<String, RateLimitTracker> trackers = new ConcurrentHashMap<>();
    
    /**
     * Operation types for rate limiting.
     */
    public enum Operation {
        TEST_CONNECTION("test_connection", TEST_CONNECTION_LIMIT),
        EVENT_SEND("event_send", EVENT_SEND_LIMIT),
        CONFIG_SAVE("config_save", CONFIG_SAVE_LIMIT);
        
        private final String key;
        private final int limit;
        
        Operation(String key, int limit) {
            this.key = key;
            this.limit = limit;
        }
        
        public String getKey() {
            return key;
        }
        
        public int getLimit() {
            return limit;
        }
    }
    
    /**
     * Checks if an operation is allowed under rate limits.
     * 
     * @param operation The operation type
     * @param identifier Unique identifier (e.g., user, IP, instance)
     * @return true if allowed, false if rate limit exceeded
     */
    public static boolean isAllowed(Operation operation, String identifier) {
        // Periodic cleanup
        periodicCleanup();
        
        String key = operation.getKey() + ":" + identifier;
        RateLimitTracker tracker = trackers.computeIfAbsent(key, k -> new RateLimitTracker(operation.getLimit()));
        
        boolean allowed = tracker.tryAcquire();
        
        if (!allowed) {
            LOGGER.warning("Rate limit exceeded for operation: " + operation.getKey() + 
                          ", identifier: " + identifier + 
                          ", limit: " + operation.getLimit() + " per minute");
        }
        
        return allowed;
    }
    
    /**
     * Checks if an operation is allowed (without identifier).
     * Uses operation key as identifier.
     * 
     * @param operation The operation type
     * @return true if allowed, false if rate limit exceeded
     */
    public static boolean isAllowed(Operation operation) {
        return isAllowed(operation, "global");
    }
    
    /**
     * Gets the current request count for an operation.
     * 
     * @param operation The operation type
     * @param identifier Unique identifier
     * @return Current request count in the time window
     */
    public static int getCurrentCount(Operation operation, String identifier) {
        String key = operation.getKey() + ":" + identifier;
        RateLimitTracker tracker = trackers.get(key);
        return tracker != null ? tracker.getCurrentCount() : 0;
    }
    
    /**
     * Gets the remaining requests before hitting the limit.
     * 
     * @param operation The operation type
     * @param identifier Unique identifier
     * @return Remaining requests
     */
    public static int getRemainingRequests(Operation operation, String identifier) {
        String key = operation.getKey() + ":" + identifier;
        RateLimitTracker tracker = trackers.get(key);
        return tracker != null ? tracker.getRemainingRequests() : operation.getLimit();
    }
    
    /**
     * Resets rate limits for an operation (for testing/admin purposes).
     * 
     * @param operation The operation type
     * @param identifier Unique identifier
     */
    public static void reset(Operation operation, String identifier) {
        String key = operation.getKey() + ":" + identifier;
        trackers.remove(key);
        LOGGER.info("Rate limit reset for operation: " + operation.getKey() + ", identifier: " + identifier);
    }
    
    /**
     * Clears all rate limit trackers.
     */
    public static void clearAll() {
        trackers.clear();
        LOGGER.info("All rate limit trackers cleared");
    }
    
    /**
     * Performs periodic cleanup of old trackers.
     */
    private static void periodicCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            int removed = 0;
            for (Map.Entry<String, RateLimitTracker> entry : trackers.entrySet()) {
                if (entry.getValue().isExpired()) {
                    trackers.remove(entry.getKey());
                    removed++;
                }
            }
            lastCleanup = now;
            if (removed > 0) {
                LOGGER.fine("Cleaned up " + removed + " expired rate limit trackers");
            }
        }
    }
    
    /**
     * Gets statistics about rate limiting.
     * 
     * @return RateLimitStats object
     */
    public static RateLimitStats getStats() {
        int totalTrackers = trackers.size();
        int activeTrackers = 0;
        long totalRequests = 0;
        
        for (RateLimitTracker tracker : trackers.values()) {
            if (!tracker.isExpired()) {
                activeTrackers++;
                totalRequests += tracker.getCurrentCount();
            }
        }
        
        return new RateLimitStats(totalTrackers, activeTrackers, totalRequests);
    }
    
    /**
     * Rate limit tracker using sliding window algorithm.
     */
    private static class RateLimitTracker {
        private final int limit;
        private final Map<Long, AtomicLong> requestCounts = new ConcurrentHashMap<>();
        private volatile long lastAccessTime = System.currentTimeMillis();
        
        public RateLimitTracker(int limit) {
            this.limit = limit;
        }
        
        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            lastAccessTime = now;
            
            // Clean up old time buckets
            cleanupOldBuckets(now);
            
            // Get current count
            int currentCount = getCurrentCount();
            
            // Check if limit exceeded
            if (currentCount >= limit) {
                return false;
            }
            
            // Increment count for current minute bucket
            long bucket = now / 60000; // 1-minute buckets
            requestCounts.computeIfAbsent(bucket, k -> new AtomicLong(0)).incrementAndGet();
            
            return true;
        }
        
        public int getCurrentCount() {
            long now = System.currentTimeMillis();
            long windowStart = now - TIME_WINDOW_MS;
            
            int count = 0;
            for (Map.Entry<Long, AtomicLong> entry : requestCounts.entrySet()) {
                long bucketTime = entry.getKey() * 60000;
                if (bucketTime >= windowStart) {
                    count += entry.getValue().get();
                }
            }
            
            return count;
        }
        
        public int getRemainingRequests() {
            return Math.max(0, limit - getCurrentCount());
        }
        
        public boolean isExpired() {
            long now = System.currentTimeMillis();
            return (now - lastAccessTime) > TIME_WINDOW_MS * 2; // Expired if no activity for 2x time window
        }
        
        private void cleanupOldBuckets(long now) {
            long windowStart = (now - TIME_WINDOW_MS) / 60000;
            requestCounts.entrySet().removeIf(entry -> entry.getKey() < windowStart);
        }
    }
    
    /**
     * Rate limit statistics.
     */
    public static class RateLimitStats {
        public final int totalTrackers;
        public final int activeTrackers;
        public final long totalRequests;
        
        public RateLimitStats(int totalTrackers, int activeTrackers, long totalRequests) {
            this.totalTrackers = totalTrackers;
            this.activeTrackers = activeTrackers;
            this.totalRequests = totalRequests;
        }
        
        @Override
        public String toString() {
            return String.format(
                "RateLimitStats{totalTrackers=%d, activeTrackers=%d, totalRequests=%d}",
                totalTrackers, activeTrackers, totalRequests
            );
        }
    }
}

