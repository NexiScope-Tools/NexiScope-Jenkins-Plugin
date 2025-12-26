package io.nexiscope.jenkins.plugin.listeners;

import hudson.Extension;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.metrics.PerformanceMetrics;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Listener for matrix build events.
 * 
 * Matrix builds are multi-configuration projects that run the same build
 * with different combinations of parameters (e.g., different OS, JDK versions).
 * 
 * This listener:
 * - Tracks the parent MatrixBuild
 * - Tracks individual MatrixRun (combination) builds
 * - Sends events for each combination
 * - Aggregates results when the matrix build completes
 * 
 * @author NexiScope Team
 */
@Extension
public class MatrixBuildListener extends RunListener<MatrixBuild> {
    
    private static final Logger LOGGER = Logger.getLogger(MatrixBuildListener.class.getName());
    
    // Track active matrix builds: buildId -> MatrixBuildInfo
    private static final Map<String, MatrixBuildInfo> activeMatrixBuilds = new ConcurrentHashMap<>();
    
    public MatrixBuildListener() {
        super(MatrixBuild.class);
    }
    
    @Override
    public void onStarted(MatrixBuild build, TaskListener listener) {
        LOGGER.info("MatrixBuildListener.onStarted called for: " + build.getFullDisplayName());
        
        if (!isEnabled()) {
            LOGGER.warning("Plugin is not enabled, skipping matrix build started event");
            return;
        }
        
        try {
            // Create and store matrix build info
            MatrixBuildInfo buildInfo = new MatrixBuildInfo(build);
            activeMatrixBuilds.put(getBuildId(build), buildInfo);
            
            // Send matrix build started event
            String event = EventMapper.createMatrixBuildStartedEvent(build);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.info("Matrix build started event sent: " + build.getFullDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send matrix build started event: " + e.getMessage());
            LOGGER.log(java.util.logging.Level.WARNING, "Exception details", e);
        }
    }
    
    @Override
    public void onCompleted(MatrixBuild build, TaskListener listener) {
        LOGGER.info("MatrixBuildListener.onCompleted called for: " + build.getFullDisplayName());
        
        if (!isEnabled()) {
            LOGGER.warning("Plugin is not enabled, skipping matrix build completed event");
            return;
        }
        
        try {
            // Get matrix build info
            MatrixBuildInfo buildInfo = activeMatrixBuilds.remove(getBuildId(build));
            
            // Collect all combination results
            List<MatrixRun> runs = build.getRuns();
            List<MatrixCombinationResult> combinationResults = new ArrayList<>();
            
            for (MatrixRun run : runs) {
                if (run != null) {
                    String combinationName = run.getParent().getName();
                    MatrixCombinationResult result = new MatrixCombinationResult(
                        combinationName,
                        run.getNumber(),
                        run.getResult() != null ? run.getResult().toString() : "UNKNOWN",
                        run.getDuration(),
                        run.getStartTimeInMillis()
                    );
                    combinationResults.add(result);
                }
            }
            
            // Send matrix build completed event with aggregated results
            String event = EventMapper.createMatrixBuildCompletedEvent(build, combinationResults);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.info("Matrix build completed event sent: " + build.getFullDisplayName() + 
                       " with " + combinationResults.size() + " combinations");
            
            // Record performance metrics
            try {
                PerformanceMetrics.recordBuildMetrics(build);
                
                // Send aggregated metrics event if enough samples collected
                String jobName = build.getParent().getFullName();
                PerformanceMetrics.AggregatedMetrics aggregated = PerformanceMetrics.calculateAggregatedMetrics(jobName);
                if (aggregated != null && aggregated.sampleCount >= 5) {
                    String metricsEvent = EventMapper.createPerformanceMetricsEvent(build, aggregated);
                    WebSocketClient.getInstance().sendEvent(metricsEvent);
                    LOGGER.fine("Performance metrics sent for matrix job: " + jobName);
                }
            } catch (Exception e) {
                LOGGER.fine("Failed to record/send performance metrics: " + e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send matrix build completed event: " + e.getMessage());
            LOGGER.log(java.util.logging.Level.WARNING, "Exception details", e);
        }
    }
    
    @Override
    public void onDeleted(MatrixBuild build) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            // Remove from active builds
            activeMatrixBuilds.remove(getBuildId(build));
            
            String event = EventMapper.createMatrixBuildDeletedEvent(build);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.fine("Matrix build deleted event sent: " + build.getFullDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send matrix build deleted event: " + e.getMessage());
        }
    }
    
    /**
     * Called when a matrix combination (MatrixRun) starts.
     * This is called by the MatrixRunListener extension.
     */
    public static void onMatrixRunStarted(MatrixRun run) {
        LOGGER.info("MatrixRun started: " + run.getFullDisplayName());
        
        if (!isEnabledStatic()) {
            return;
        }
        
        try {
            // Get parent matrix build
            MatrixBuild parentBuild = run.getParentBuild();
            if (parentBuild != null) {
                // Update matrix build info
                MatrixBuildInfo buildInfo = activeMatrixBuilds.get(getBuildId(parentBuild));
                if (buildInfo != null) {
                    buildInfo.addRun(run);
                }
                
                // Send matrix combination started event
                String event = EventMapper.createMatrixCombinationStartedEvent(run, parentBuild);
                WebSocketClient.getInstance().sendEvent(event);
                LOGGER.fine("Matrix combination started event sent: " + run.getFullDisplayName());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send matrix combination started event: " + e.getMessage());
        }
    }
    
    /**
     * Called when a matrix combination (MatrixRun) completes.
     * This is called by the MatrixRunListener extension.
     */
    public static void onMatrixRunCompleted(MatrixRun run) {
        LOGGER.info("MatrixRun completed: " + run.getFullDisplayName());
        
        if (!isEnabledStatic()) {
            return;
        }
        
        try {
            // Get parent matrix build
            MatrixBuild parentBuild = run.getParentBuild();
            if (parentBuild != null) {
                // Send matrix combination completed event
                String event = EventMapper.createMatrixCombinationCompletedEvent(run, parentBuild);
                WebSocketClient.getInstance().sendEvent(event);
                LOGGER.fine("Matrix combination completed event sent: " + run.getFullDisplayName());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send matrix combination completed event: " + e.getMessage());
        }
    }
    
    /**
     * Gets a unique ID for a build.
     */
    private static String getBuildId(Run<?, ?> build) {
        return build.getParent().getFullName() + "#" + build.getNumber();
    }
    
    /**
     * Static version of isEnabled() for use in static methods.
     */
    private static boolean isEnabledStatic() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        return config != null && config.isEnabled() && config.isValid();
    }
    
    /**
     * Checks if the plugin is enabled and configured.
     */
    private boolean isEnabled() {
        return isEnabledStatic();
    }
    
    /**
     * Information about an active matrix build.
     */
    private static class MatrixBuildInfo {
        final MatrixBuild build;
        final Map<String, MatrixRun> runs = new ConcurrentHashMap<>();
        final long startTime;
        
        MatrixBuildInfo(MatrixBuild build) {
            this.build = build;
            this.startTime = System.currentTimeMillis();
        }
        
        void addRun(MatrixRun run) {
            runs.put(run.getParent().getName(), run);
        }
    }
    
    /**
     * Result information for a matrix combination.
     */
    public static class MatrixCombinationResult {
        public final String combinationName;
        public final int buildNumber;
        public final String result;
        public final long duration;
        public final long startTime;
        
        public MatrixCombinationResult(String combinationName, int buildNumber, String result, 
                                      long duration, long startTime) {
            this.combinationName = combinationName;
            this.buildNumber = buildNumber;
            this.result = result;
            this.duration = duration;
            this.startTime = startTime;
        }
    }
}

