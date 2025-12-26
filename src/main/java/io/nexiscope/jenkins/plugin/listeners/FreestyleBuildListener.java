package io.nexiscope.jenkins.plugin.listeners;

import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.metrics.PerformanceMetrics;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;

import java.util.logging.Logger;

/**
 * Listener for freestyle build events (start, completion, deletion).
 * 
 * Captures:
 * - Freestyle build execution start
 * - Freestyle build completion (success/failure/aborted)
 * - Freestyle build deletion
 * - Build parameters
 * - Build steps
 * 
 * @author NexiScope Team
 */
@Extension
public class FreestyleBuildListener extends RunListener<FreeStyleBuild> {
    
    private static final Logger LOGGER = Logger.getLogger(FreestyleBuildListener.class.getName());
    
    public FreestyleBuildListener() {
        super(FreeStyleBuild.class);
    }
    
    @Override
    public void onStarted(FreeStyleBuild build, TaskListener listener) {
        LOGGER.info("FreestyleBuildListener.onStarted called for: " + build.getFullDisplayName());
        
        if (!isEnabled()) {
            LOGGER.warning("Plugin is not enabled, skipping freestyle build started event");
            return;
        }
        
        try {
            String event = EventMapper.createFreestyleBuildStartedEvent(build);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.info("Freestyle build started event sent: " + build.getFullDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send freestyle build started event: " + e.getMessage());
            LOGGER.log(java.util.logging.Level.WARNING, "Exception details", e);
        }
    }
    
    @Override
    public void onCompleted(FreeStyleBuild build, TaskListener listener) {
        LOGGER.info("FreestyleBuildListener.onCompleted called for: " + build.getFullDisplayName());
        
        if (!isEnabled()) {
            LOGGER.warning("Plugin is not enabled, skipping freestyle build completed event");
            return;
        }
        
        try {
            String event = EventMapper.createFreestyleBuildCompletedEvent(build);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.info("Freestyle build completed event sent: " + build.getFullDisplayName());
            
            // Record performance metrics
            try {
                PerformanceMetrics.recordBuildMetrics((Run<?, ?>) build);
                
                // Send aggregated metrics event if enough samples collected
                String jobName = build.getParent().getFullName();
                PerformanceMetrics.AggregatedMetrics aggregated = PerformanceMetrics.calculateAggregatedMetrics(jobName);
                if (aggregated != null && aggregated.sampleCount >= 5) {
                    String metricsEvent = EventMapper.createPerformanceMetricsEvent(build, aggregated);
                    WebSocketClient.getInstance().sendEvent(metricsEvent);
                    LOGGER.fine("Performance metrics sent for freestyle job: " + jobName);
                }
            } catch (Exception e) {
                LOGGER.fine("Failed to record/send performance metrics: " + e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send freestyle build completed event: " + e.getMessage());
        }
    }
    
    @Override
    public void onDeleted(FreeStyleBuild build) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String event = EventMapper.createFreestyleBuildDeletedEvent(build);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.fine("Freestyle build deleted event sent: " + build.getFullDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send freestyle build deleted event: " + e.getMessage());
        }
    }
    
    @Override
    public void onFinalized(FreeStyleBuild build) {
        // Called when build is finalized (after completion)
        // Can be used for cleanup or final event sending
        super.onFinalized(build);
    }
    
    /**
     * Checks if the plugin is enabled and configured.
     */
    private boolean isEnabled() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config == null) {
            LOGGER.warning("NexiScopeGlobalConfiguration is null");
            return false;
        }
        boolean valid = config.isValid();
        if (!valid) {
            LOGGER.warning("NexiScope configuration is invalid. URL: " + config.getPlatformUrl() + 
                          ", Token: " + (config.getAuthToken() != null ? "***" : "null") +
                          ", InstanceId: " + config.getInstanceId());
        }
        return valid;
    }
}

