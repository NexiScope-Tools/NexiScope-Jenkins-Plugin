package io.nexiscope.jenkins.plugin.metrics;

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import io.nexiscope.jenkins.plugin.events.ExecutionGraph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Collects and aggregates performance metrics for pipeline runs.
 * 
 * Tracks:
 * - Build duration (total, stages, steps)
 * - Queue time
 * - Stage durations
 * - Calculates percentiles
 * 
 * @author NexiScope Team
 */
public class PerformanceMetrics {
    
    private static final Logger LOGGER = Logger.getLogger(PerformanceMetrics.class.getName());
    
    // Metrics storage: jobName -> List of BuildMetrics
    private static final Map<String, List<BuildMetrics>> jobMetrics = new ConcurrentHashMap<>();
    
    // Maximum number of metrics to keep per job (for percentile calculation)
    private static final int MAX_METRICS_PER_JOB = 1000;
    
    /**
     * Metrics for a single build.
     */
    public static class BuildMetrics {
        public final String jobName;
        public final int buildNumber;
        public final long buildDuration; // Total build duration in milliseconds
        public final long queueTime; // Time spent in queue in milliseconds
        public final Map<String, Long> stageDurations; // stageName -> duration in milliseconds
        public final long timestamp;
        public final String result; // SUCCESS, FAILURE, ABORTED, etc.
        
        public BuildMetrics(String jobName, int buildNumber, long buildDuration, long queueTime,
                           Map<String, Long> stageDurations, String result) {
            this.jobName = jobName;
            this.buildNumber = buildNumber;
            this.buildDuration = buildDuration;
            this.queueTime = queueTime;
            this.stageDurations = stageDurations != null ? new HashMap<>(stageDurations) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
            this.result = result;
        }
    }
    
    /**
     * Aggregated metrics for a job.
     */
    public static class AggregatedMetrics {
        public final String jobName;
        public final int sampleCount;
        public final long avgBuildDuration;
        public final long minBuildDuration;
        public final long maxBuildDuration;
        public final long p50BuildDuration; // Median
        public final long p95BuildDuration;
        public final long p99BuildDuration;
        public final long avgQueueTime;
        public final long minQueueTime;
        public final long maxQueueTime;
        public final Map<String, StageMetrics> stageMetrics; // stageName -> StageMetrics
        public final long timestamp;
        
        public AggregatedMetrics(String jobName, int sampleCount, long avgBuildDuration,
                                long minBuildDuration, long maxBuildDuration,
                                long p50BuildDuration, long p95BuildDuration, long p99BuildDuration,
                                long avgQueueTime, long minQueueTime, long maxQueueTime,
                                Map<String, StageMetrics> stageMetrics) {
            this.jobName = jobName;
            this.sampleCount = sampleCount;
            this.avgBuildDuration = avgBuildDuration;
            this.minBuildDuration = minBuildDuration;
            this.maxBuildDuration = maxBuildDuration;
            this.p50BuildDuration = p50BuildDuration;
            this.p95BuildDuration = p95BuildDuration;
            this.p99BuildDuration = p99BuildDuration;
            this.avgQueueTime = avgQueueTime;
            this.minQueueTime = minQueueTime;
            this.maxQueueTime = maxQueueTime;
            this.stageMetrics = stageMetrics != null ? new HashMap<>(stageMetrics) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Metrics for a specific stage.
     */
    public static class StageMetrics {
        public final String stageName;
        public final int sampleCount;
        public final long avgDuration;
        public final long minDuration;
        public final long maxDuration;
        public final long p50Duration;
        public final long p95Duration;
        public final long p99Duration;
        
        public StageMetrics(String stageName, int sampleCount, long avgDuration,
                           long minDuration, long maxDuration,
                           long p50Duration, long p95Duration, long p99Duration) {
            this.stageName = stageName;
            this.sampleCount = sampleCount;
            this.avgDuration = avgDuration;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
            this.p50Duration = p50Duration;
            this.p95Duration = p95Duration;
            this.p99Duration = p99Duration;
        }
    }
    
    /**
     * Records metrics for a completed build (pipeline or freestyle).
     */
    public static void recordBuildMetrics(Run<?, ?> run) {
        try {
            String jobName = run.getParent().getFullName();
            int buildNumber = run.getNumber();
            
            // Get build duration
            long buildDuration = run.getDuration();
            if (buildDuration == 0) {
                // Build might still be running or just started
                return;
            }
            
            // Get queue time (time between creation and start)
            long queueTime = 0;
            try {
                long startTime = run.getStartTimeInMillis();
                long createdTime = run.getTimeInMillis(); // Time when run was created
                if (startTime > 0 && createdTime > 0) {
                    queueTime = startTime - createdTime;
                }
            } catch (Exception e) {
                LOGGER.fine("Could not calculate queue time: " + e.getMessage());
            }
            
            // Get result
            String result = run.getResult() != null ? run.getResult().toString() : "UNKNOWN";
            
            // Get stage durations from ExecutionGraph (only for pipeline runs)
            Map<String, Long> stageDurations = new HashMap<>();
            if (run instanceof WorkflowRun) {
                stageDurations = extractStageDurations((WorkflowRun) run);
            }
            // For freestyle builds, we don't have stages, so stageDurations remains empty
            
            // Create metrics
            BuildMetrics metrics = new BuildMetrics(jobName, buildNumber, buildDuration, queueTime, stageDurations, result);
            
            // Store metrics
            jobMetrics.computeIfAbsent(jobName, k -> new ArrayList<>()).add(metrics);
            
            // Limit metrics per job
            List<BuildMetrics> metricsList = jobMetrics.get(jobName);
            if (metricsList.size() > MAX_METRICS_PER_JOB) {
                // Remove oldest metrics
                metricsList.sort(Comparator.comparingLong(m -> m.timestamp));
                int toRemove = metricsList.size() - MAX_METRICS_PER_JOB;
                for (int i = 0; i < toRemove; i++) {
                    metricsList.remove(0);
                }
            }
            
            LOGGER.fine("Recorded metrics for build: " + jobName + "#" + buildNumber);
        } catch (Exception e) {
            LOGGER.warning("Failed to record build metrics: " + e.getMessage());
        }
    }
    
    /**
     * Extracts stage durations from ExecutionGraph.
     * Stage durations are tracked in FlowNodeEventListener, but we can also
     * calculate them from the execution graph structure.
     */
    private static Map<String, Long> extractStageDurations(WorkflowRun run) {
        Map<String, Long> stageDurations = new HashMap<>();
        
        try {
            // Get execution graph snapshot
            io.nexiscope.jenkins.plugin.events.ExecutionGraph.GraphSnapshot snapshot = 
                io.nexiscope.jenkins.plugin.events.ExecutionGraph.getGraphSnapshot(run);
            
            // Extract stage durations from node-to-stage mapping
            // Group nodes by stage name and calculate duration from first to last node
            Map<String, List<Long>> stageTimestamps = new HashMap<>();
            
            for (io.nexiscope.jenkins.plugin.events.ExecutionGraph.NodeInfo node : snapshot.nodes.values()) {
                if (node.stageName != null && !node.stageName.isEmpty()) {
                    stageTimestamps.computeIfAbsent(node.stageName, k -> new ArrayList<>())
                        .add(node.timestamp);
                }
            }
            
            // Calculate duration for each stage (last timestamp - first timestamp)
            for (Map.Entry<String, List<Long>> entry : stageTimestamps.entrySet()) {
                List<Long> timestamps = entry.getValue();
                if (timestamps.size() >= 2) {
                    timestamps.sort(Long::compareTo);
                    long duration = timestamps.get(timestamps.size() - 1) - timestamps.get(0);
                    stageDurations.put(entry.getKey(), duration);
                }
            }
            
        } catch (Exception e) {
            LOGGER.fine("Error extracting stage durations: " + e.getMessage());
        }
        
        return stageDurations;
    }
    
    /**
     * Records stage duration directly (called from FlowNodeEventListener).
     */
    public static void recordStageDuration(String jobName, String stageName, long duration) {
        // This is a helper method for direct stage duration recording
        // The main recording happens in recordBuildMetrics()
        // This can be used for real-time stage duration tracking if needed
    }
    
    /**
     * Calculates aggregated metrics for a job.
     */
    public static AggregatedMetrics calculateAggregatedMetrics(String jobName) {
        List<BuildMetrics> metrics = jobMetrics.get(jobName);
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }
        
        // Filter to only successful builds for metrics (optional - could include all)
        List<BuildMetrics> successfulBuilds = metrics.stream()
            .filter(m -> "SUCCESS".equals(m.result))
            .collect(Collectors.toList());
        
        if (successfulBuilds.isEmpty()) {
            successfulBuilds = metrics; // Use all builds if no successful ones
        }
        
        int sampleCount = successfulBuilds.size();
        
        // Calculate build duration statistics
        List<Long> buildDurations = successfulBuilds.stream()
            .map(m -> m.buildDuration)
            .sorted()
            .collect(Collectors.toList());
        
        long avgBuildDuration = (long) buildDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        long minBuildDuration = buildDurations.isEmpty() ? 0 : buildDurations.get(0);
        long maxBuildDuration = buildDurations.isEmpty() ? 0 : buildDurations.get(buildDurations.size() - 1);
        long p50BuildDuration = percentile(buildDurations, 50);
        long p95BuildDuration = percentile(buildDurations, 95);
        long p99BuildDuration = percentile(buildDurations, 99);
        
        // Calculate queue time statistics
        List<Long> queueTimes = successfulBuilds.stream()
            .map(m -> m.queueTime)
            .sorted()
            .collect(Collectors.toList());
        
        long avgQueueTime = (long) queueTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long minQueueTime = queueTimes.isEmpty() ? 0 : queueTimes.get(0);
        long maxQueueTime = queueTimes.isEmpty() ? 0 : queueTimes.get(queueTimes.size() - 1);
        
        // Calculate stage metrics
        Map<String, StageMetrics> stageMetrics = calculateStageMetrics(successfulBuilds);
        
        return new AggregatedMetrics(jobName, sampleCount, avgBuildDuration,
            minBuildDuration, maxBuildDuration, p50BuildDuration, p95BuildDuration, p99BuildDuration,
            avgQueueTime, minQueueTime, maxQueueTime, stageMetrics);
    }
    
    /**
     * Calculates metrics for each stage.
     */
    private static Map<String, StageMetrics> calculateStageMetrics(List<BuildMetrics> builds) {
        Map<String, List<Long>> stageDurationsMap = new HashMap<>();
        
        // Collect all stage durations
        for (BuildMetrics build : builds) {
            for (Map.Entry<String, Long> entry : build.stageDurations.entrySet()) {
                stageDurationsMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(entry.getValue());
            }
        }
        
        // Calculate metrics for each stage
        Map<String, StageMetrics> stageMetrics = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : stageDurationsMap.entrySet()) {
            String stageName = entry.getKey();
            List<Long> durations = entry.getValue().stream().sorted().collect(Collectors.toList());
            
            if (durations.isEmpty()) {
                continue;
            }
            
            int sampleCount = durations.size();
            long avgDuration = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
            long minDuration = durations.get(0);
            long maxDuration = durations.get(durations.size() - 1);
            long p50Duration = percentile(durations, 50);
            long p95Duration = percentile(durations, 95);
            long p99Duration = percentile(durations, 99);
            
            stageMetrics.put(stageName, new StageMetrics(stageName, sampleCount, avgDuration,
                minDuration, maxDuration, p50Duration, p95Duration, p99Duration));
        }
        
        return stageMetrics;
    }
    
    /**
     * Calculates a percentile from a sorted list.
     */
    private static long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        
        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        
        double weight = index - lower;
        return (long) (sortedValues.get(lower) * (1 - weight) + sortedValues.get(upper) * weight);
    }
    
    /**
     * Gets all job names with metrics.
     */
    public static Set<String> getJobNames() {
        return new HashSet<>(jobMetrics.keySet());
    }
    
    /**
     * Clears metrics for a specific job.
     */
    public static void clearMetrics(String jobName) {
        jobMetrics.remove(jobName);
    }
    
    /**
     * Clears all metrics.
     */
    public static void clearAllMetrics() {
        jobMetrics.clear();
    }
}

