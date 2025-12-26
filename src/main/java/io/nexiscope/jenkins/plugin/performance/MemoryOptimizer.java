package io.nexiscope.jenkins.plugin.performance;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Memory optimization utilities for the NexiScope plugin.
 * 
 * Features:
 * - String interning for frequently used strings
 * - Weak reference caching for execution graphs
 * - Memory usage monitoring
 * 
 * @author NexiScope Team
 */
public class MemoryOptimizer {
    
    private static final Logger LOGGER = Logger.getLogger(MemoryOptimizer.class.getName());
    
    // String interning cache for frequently used strings (event types, job names, etc.)
    private static final Map<String, String> internedStrings = new ConcurrentHashMap<>();
    
    // Weak reference cache for execution graphs (automatically cleaned up by GC)
    private static final Map<String, WeakReference<Object>> graphCache = new ConcurrentHashMap<>();
    
    // Memory usage tracking
    private static long lastMemoryCheck = System.currentTimeMillis();
    private static final long MEMORY_CHECK_INTERVAL_MS = 60000; // 1 minute
    
    /**
     * Interns a string to reduce memory usage for frequently used strings.
     * Uses a custom cache instead of String.intern() for better control.
     * 
     * @param str The string to intern
     * @return The interned string
     */
    public static String intern(String str) {
        if (str == null) {
            return null;
        }
        
        // For short strings, just use the original (not worth caching)
        if (str.length() < 3) {
            return str;
        }
        
        // Check cache first
        String interned = internedStrings.get(str);
        if (interned != null) {
            return interned;
        }
        
        // Add to cache (limit cache size to prevent memory leaks)
        if (internedStrings.size() < 10000) {
            internedStrings.put(str, str);
            return str;
        }
        
        // Cache full, return original string
        return str;
    }
    
    /**
     * Caches an execution graph with weak reference for memory efficiency.
     * The graph will be automatically cleaned up by GC when memory is low.
     * 
     * @param runId The run ID
     * @param graph The execution graph object
     */
    public static void cacheGraph(String runId, Object graph) {
        if (runId == null || graph == null) {
            return;
        }
        
        graphCache.put(runId, new WeakReference<>(graph));
        
        // Periodically clean up null references
        if (graphCache.size() > 100) {
            cleanupGraphCache();
        }
    }
    
    /**
     * Retrieves a cached execution graph.
     * 
     * @param runId The run ID
     * @return The cached graph, or null if not found or GC'd
     */
    public static Object getCachedGraph(String runId) {
        if (runId == null) {
            return null;
        }
        
        WeakReference<Object> ref = graphCache.get(runId);
        if (ref == null) {
            return null;
        }
        
        Object graph = ref.get();
        if (graph == null) {
            // Reference was GC'd, remove from cache
            graphCache.remove(runId);
        }
        
        return graph;
    }
    
    /**
     * Cleans up null weak references from the graph cache.
     */
    private static void cleanupGraphCache() {
        graphCache.entrySet().removeIf(entry -> entry.getValue().get() == null);
        LOGGER.fine("Cleaned up graph cache, size: " + graphCache.size());
    }
    
    /**
     * Clears all caches. Should be called on plugin shutdown.
     */
    public static void clearCaches() {
        internedStrings.clear();
        graphCache.clear();
        LOGGER.info("Memory optimizer caches cleared");
    }
    
    /**
     * Gets memory usage statistics.
     * 
     * @return MemoryStats object with current statistics
     */
    public static MemoryStats getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return new MemoryStats(
            usedMemory,
            totalMemory,
            maxMemory,
            internedStrings.size(),
            graphCache.size()
        );
    }
    
    /**
     * Checks memory usage and logs warning if high.
     * Should be called periodically from event processing code.
     */
    public static void checkMemoryUsage() {
        long now = System.currentTimeMillis();
        if (now - lastMemoryCheck < MEMORY_CHECK_INTERVAL_MS) {
            return; // Too soon since last check
        }
        
        lastMemoryCheck = now;
        
        MemoryStats stats = getMemoryStats();
        double usagePercent = stats.getUsagePercent();
        
        if (usagePercent > 90) {
            LOGGER.warning("High memory usage: " + String.format("%.1f%%", usagePercent) + 
                          " - Consider increasing heap size or reducing event volume");
            
            // Suggest GC if memory is very high
            if (usagePercent > 95) {
                LOGGER.warning("Critical memory usage, suggesting GC");
                System.gc();
            }
        } else if (usagePercent > 75) {
            LOGGER.info("Memory usage: " + String.format("%.1f%%", usagePercent));
        }
        
        LOGGER.fine("Memory stats: " + stats);
    }
    
    /**
     * Memory statistics data class.
     */
    public static class MemoryStats {
        public final long usedMemory;
        public final long totalMemory;
        public final long maxMemory;
        public final int internedStringsCount;
        public final int cachedGraphsCount;
        
        public MemoryStats(long usedMemory, long totalMemory, long maxMemory,
                          int internedStringsCount, int cachedGraphsCount) {
            this.usedMemory = usedMemory;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
            this.internedStringsCount = internedStringsCount;
            this.cachedGraphsCount = cachedGraphsCount;
        }
        
        /**
         * Gets memory usage as a percentage of max memory.
         * 
         * @return Usage percentage (0-100)
         */
        public double getUsagePercent() {
            return maxMemory > 0 ? (double) usedMemory / maxMemory * 100.0 : 0.0;
        }
        
        /**
         * Formats memory size in human-readable format.
         * 
         * @param bytes Memory size in bytes
         * @return Formatted string (e.g., "512 MB")
         */
        private String formatBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
        
        @Override
        public String toString() {
            return String.format(
                "MemoryStats{used=%s, total=%s, max=%s, usage=%.1f%%, " +
                "internedStrings=%d, cachedGraphs=%d}",
                formatBytes(usedMemory),
                formatBytes(totalMemory),
                formatBytes(maxMemory),
                getUsagePercent(),
                internedStringsCount,
                cachedGraphsCount
            );
        }
    }
}

