package io.nexiscope.jenkins.plugin.listeners;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.events.ExecutionGraph;
import io.nexiscope.jenkins.plugin.listeners.LogStreamingFilter;
import io.nexiscope.jenkins.plugin.metrics.PerformanceMetrics;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.util.logging.Logger;

/**
 * Listener for pipeline-level events (start, completion, deletion).
 * 
 * Captures:
 * - Pipeline execution start
 * - Pipeline completion (success/failure/aborted)
 * - Pipeline deletion
 * 
 * @author NexiScope Team
 */
@Extension
public class PipelineEventListener extends RunListener<WorkflowRun> {
    
    private static final Logger LOGGER = Logger.getLogger(PipelineEventListener.class.getName());
    
    public PipelineEventListener() {
        super(WorkflowRun.class);
    }
    
    @Override
    public void onStarted(WorkflowRun run, TaskListener listener) {
        LOGGER.info("PipelineEventListener.onStarted called for: " + run.getFullDisplayName());
        
        if (!isEnabled()) {
            LOGGER.warning("Plugin is not enabled or configuration is invalid, skipping pipeline started event");
            return;
        }
        
        try {
            String event = EventMapper.createPipelineStartedEvent(run);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.info("Pipeline started event sent: " + run.getFullDisplayName());
            
            // Initialize execution graph for this run
            // Graph will be built incrementally as nodes are added
        } catch (Exception e) {
            LOGGER.warning("Failed to send pipeline started event: " + e.getMessage());
        }
    }
    
    @Override
    public void onCompleted(WorkflowRun run, TaskListener listener) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String event = EventMapper.createPipelineCompletedEvent(run);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.fine("Pipeline completed event sent: " + run.getFullDisplayName());
            
            // Send final execution graph snapshot
            // Note: FlowNodeEventListener.onCompleted() also sends the graph,
            // but this ensures it's sent even if FlowNodeEventListener doesn't trigger
            try {
                ExecutionGraph.GraphSnapshot snapshot = ExecutionGraph.getGraphSnapshot(run);
                if (snapshot.nodeCount > 0) {
                    String graphEvent = EventMapper.createExecutionGraphEvent(run, snapshot);
                    WebSocketClient.getInstance().sendEvent(graphEvent);
                    LOGGER.fine("Final execution graph sent: " + snapshot.nodeCount + " nodes");
                }
            } catch (Exception e) {
                LOGGER.fine("Failed to send final execution graph: " + e.getMessage());
            }
            
            // Record performance metrics
            try {
                PerformanceMetrics.recordBuildMetrics(run);
                
                // Send aggregated metrics event if enough samples collected
                String jobName = run.getParent().getFullName();
                PerformanceMetrics.AggregatedMetrics aggregated = PerformanceMetrics.calculateAggregatedMetrics(jobName);
                if (aggregated != null && aggregated.sampleCount >= 5) {
                    // Send metrics event every 5 builds or periodically
                    String metricsEvent = EventMapper.createPerformanceMetricsEvent(run, aggregated);
                    WebSocketClient.getInstance().sendEvent(metricsEvent);
                    LOGGER.fine("Performance metrics sent for job: " + jobName);
                }
            } catch (Exception e) {
                LOGGER.fine("Failed to record/send performance metrics: " + e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send pipeline completed event: " + e.getMessage());
        }
    }
    
    @Override
    public void onFinalized(WorkflowRun run) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            // Handle aborted pipelines
            if (run.getResult() != null && run.getResult().toString().equals("ABORTED")) {
                String event = EventMapper.createPipelineAbortedEvent(run);
                WebSocketClient.getInstance().sendEvent(event);
                LOGGER.fine("Pipeline aborted event sent: " + run.getFullDisplayName());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send pipeline finalized event: " + e.getMessage());
        }
    }
    
    @Override
    public void onDeleted(WorkflowRun run) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String event = EventMapper.createPipelineDeletedEvent(run);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.fine("Pipeline deleted event sent: " + run.getFullDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send pipeline deleted event: " + e.getMessage());
        }
    }
    
    private boolean isEnabled() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config == null) {
            LOGGER.warning("NexiScopeGlobalConfiguration is null");
            return false;
        }
        boolean enabled = config.isEnabled();
        boolean valid = config.isValid();
        if (!enabled || !valid) {
            LOGGER.warning("Plugin is disabled or invalid. Enabled: " + enabled + 
                          ", Valid: " + valid + 
                          ", URL: " + (config.getPlatformUrl() != null ? config.getPlatformUrl() : "null") +
                          ", Token: " + (config.getAuthToken() != null ? "***" : "null"));
        }
        return enabled && valid;
    }
}

