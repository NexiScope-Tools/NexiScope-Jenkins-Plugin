package io.nexiscope.jenkins.plugin.listeners;

import hudson.Extension;
import hudson.model.TaskListener;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.util.logging.Logger;

/**
 * Listener for stage-level events.
 * 
 * Captures:
 * - Stage start
 * - Stage completion
 * - Stage failure
 * 
 * @author NexiScope Team
 */
@Extension
public class StageEventListener implements StepListener {
    
    private static final Logger LOGGER = Logger.getLogger(StageEventListener.class.getName());
    
    @Override
    public void notifyOfNewStep(Step step, StepContext context) {
        // Stage events are typically handled via FlowNodeEventListener
        // This listener can be extended for step-specific events
    }
    
    /**
     * Called when a stage starts.
     */
    public void onStageStarted(WorkflowRun run, FlowNode node, TaskListener listener) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String event = EventMapper.createStageStartedEvent(run, node);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.fine("Stage started event sent: " + node.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send stage started event: " + e.getMessage());
        }
    }
    
    /**
     * Called when a stage completes.
     */
    public void onStageCompleted(WorkflowRun run, FlowNode node, TaskListener listener) {
        onStageCompleted(run, node, listener, 0);
    }
    
    /**
     * Called when a stage completes with duration.
     */
    public void onStageCompleted(WorkflowRun run, FlowNode node, TaskListener listener, long durationMs) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String event = EventMapper.createStageCompletedEvent(run, node, durationMs);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.fine("Stage completed event sent: " + node.getDisplayName() + " (duration: " + durationMs + "ms)");
        } catch (Exception e) {
            LOGGER.warning("Failed to send stage completed event: " + e.getMessage());
        }
    }
    
    /**
     * Called when a stage fails.
     */
    public void onStageFailed(WorkflowRun run, FlowNode node, TaskListener listener, Throwable error) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String event = EventMapper.createStageFailedEvent(run, node, error);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.fine("Stage failed event sent: " + node.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send stage failed event: " + e.getMessage());
        }
    }
    
    private boolean isEnabled() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        return config != null && config.isEnabled() && config.isValid();
    }
}

