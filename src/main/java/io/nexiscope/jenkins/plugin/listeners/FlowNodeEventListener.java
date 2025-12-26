package io.nexiscope.jenkins.plugin.listeners;

import hudson.Extension;
import hudson.model.TaskListener;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.events.ExecutionGraph;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import hudson.model.Queue;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Listener for flow node events (granular execution details).
 * 
 * Captures:
 * - Individual step execution
 * - Flow node transitions
 * - Execution graph construction
 * 
 * @author NexiScope Team
 */
@Extension
public class FlowNodeEventListener extends FlowExecutionListener {
    
    private static final Logger LOGGER = Logger.getLogger(FlowNodeEventListener.class.getName());
    
    // Track stage start times for duration calculation
    // Key: executionId:nodeId, Value: start time in milliseconds
    private static final Map<String, Long> stageStartTimes = new ConcurrentHashMap<>();
    
    // Track which stages we've already sent start events for
    // Key: executionId:nodeId, Value: true if start event sent
    private static final Map<String, Boolean> stageStartEventsSent = new ConcurrentHashMap<>();
    
    // Track active executions for periodic graph snapshots
    // Key: executionId, Value: WorkflowRun
    private static final Map<String, WorkflowRun> activeExecutions = new ConcurrentHashMap<>();
    
    // Scheduled executor for periodic graph snapshots
    private static final ScheduledExecutorService graphSnapshotExecutor = 
        Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "NexiScope-GraphSnapshot");
            t.setDaemon(true);
            return t;
        });
    
    static {
        // Start periodic graph snapshot task (every 30 seconds)
        graphSnapshotExecutor.scheduleAtFixedRate(
            FlowNodeEventListener::sendPeriodicGraphSnapshots,
            30, 30, TimeUnit.SECONDS
        );
    }
    
    // Get StageEventListener instance
    private static StageEventListener getStageEventListener() {
        hudson.ExtensionList<StageEventListener> list = hudson.ExtensionList.lookup(StageEventListener.class);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
    
    public void onNewHead(FlowNode node) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            FlowExecution execution = node.getExecution();
            if (execution != null) {
                Queue.Executable executable = execution.getOwner().getExecutable();
                if (executable instanceof Run) {
                    Run<?, ?> run = (Run<?, ?>) executable;
                    if (run instanceof WorkflowRun) {
                        WorkflowRun workflowRun = (WorkflowRun) run;
                        
                        // Set current flow node for log association
                        LogStreamingFilter.setCurrentFlowNode(node);
                        
                        // Track this execution for periodic snapshots
                        String executionId = getExecutionId(workflowRun, execution);
                        activeExecutions.put(executionId, workflowRun);
                        
                        // Add node to execution graph
                        ExecutionGraph.addNode(workflowRun, node);
                        
                        // Get branch ID for this node
                        ExecutionGraph.BranchInfo branchInfo = ExecutionGraph.getBranchForNode(workflowRun, node.getId());
                        String branchId = branchInfo != null ? branchInfo.branchId : null;
                        
                        // Send branch start event if this is a new branch
                        if (branchInfo != null && branchInfo.status.equals("STARTED") && branchInfo.nodeIds.size() == 1) {
                            // This is the first node in the branch - send branch started event
                            sendBranchEvent(workflowRun, branchInfo, "STARTED");
                        }
                        
                        // Send incremental graph update event
                        try {
                            ExecutionGraph.GraphSnapshot snapshot = ExecutionGraph.getGraphSnapshot(workflowRun);
                            ExecutionGraph.NodeInfo nodeInfo = snapshot.nodes.get(node.getId());
                            if (nodeInfo != null) {
                                String updateEvent = EventMapper.createExecutionGraphUpdateEvent(workflowRun, nodeInfo);
                                WebSocketClient.getInstance().sendEvent(updateEvent);
                            }
                        } catch (Exception e) {
                            LOGGER.fine("Failed to send graph update event: " + e.getMessage());
                        }
                        
                        // Check if this is a stage node
                        if (isStageNode(node)) {
                            handleStageNode(workflowRun, node, execution, branchId);
                        } else if (isSignificantNode(node)) {
                            // Send generic flow node event for other significant nodes
                            String event = EventMapper.createFlowNodeEvent(workflowRun, node);
                            WebSocketClient.getInstance().sendEvent(event);
                            LOGGER.fine("Flow node event sent: " + node.getDisplayName());
                        }
                        
                        // Check for branch completion/failure
                        checkBranchStatus(workflowRun, node, branchId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to send flow node event: " + e.getMessage());
        }
    }
    
    /**
     * Handles stage node lifecycle events.
     */
    private void handleStageNode(WorkflowRun run, FlowNode node, FlowExecution execution) {
        handleStageNode(run, node, execution, null);
    }
    
    /**
     * Handles stage node lifecycle events with branch ID.
     */
    private void handleStageNode(WorkflowRun run, FlowNode node, FlowExecution execution, String branchId) {
        String stageKey = getStageKey(execution, node);
        StageEventListener stageListener = getStageEventListener();
        
        if (stageListener == null) {
            LOGGER.warning("StageEventListener not found, falling back to generic flow node event");
            String event = EventMapper.createFlowNodeEvent(run, node);
            WebSocketClient.getInstance().sendEvent(event);
            return;
        }
        
        // Check if this is the first time we're seeing this stage (stage start)
        if (!stageStartEventsSent.containsKey(stageKey)) {
            // Stage start
            stageStartEventsSent.put(stageKey, true);
            stageStartTimes.put(stageKey, System.currentTimeMillis());
            
            try {
                stageListener.onStageStarted(run, node, null);
                LOGGER.fine("Stage started: " + node.getDisplayName());
            } catch (Exception e) {
                LOGGER.warning("Failed to send stage started event: " + e.getMessage());
            }
        }
        
        // Check if stage is complete (node has no pending children or execution is done)
        if (isStageComplete(node, execution)) {
            handleStageCompletion(run, node, stageKey, stageListener);
        }
        
        // Check if stage has failed
        if (isStageFailed(node)) {
            handleStageFailure(run, node, stageKey, stageListener);
            
            // If this stage is in a branch, mark branch as failed
            if (branchId != null) {
                ExecutionGraph.failBranch(run, branchId);
                ExecutionGraph.BranchInfo branchInfo = ExecutionGraph.getBranchForNode(run, node.getId());
                if (branchInfo != null) {
                    sendBranchEvent(run, branchInfo, "FAILED");
                }
            }
        }
    }
    
    /**
     * Checks branch status and sends branch lifecycle events.
     */
    private void checkBranchStatus(WorkflowRun run, FlowNode node, String branchId) {
        if (branchId == null) {
            return;
        }
        
        try {
            ExecutionGraph.BranchInfo branch = ExecutionGraph.getBranchForNode(run, node.getId());
            if (branch == null) {
                return;
            }
            
            // Check if branch should be marked as completed
            // This is a simplified check - in practice, you'd analyze if all nodes in the branch are complete
            FlowExecution execution = node.getExecution();
            if (execution != null && execution.isComplete()) {
                if (branch.status.equals("STARTED")) {
                    ExecutionGraph.completeBranch(run, branchId);
                    sendBranchEvent(run, branch, "COMPLETED");
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Error checking branch status: " + e.getMessage());
        }
    }
    
    /**
     * Sends branch lifecycle events.
     */
    private void sendBranchEvent(WorkflowRun run, ExecutionGraph.BranchInfo branch, String eventType) {
        try {
            String event = EventMapper.createBranchEvent(run, branch, eventType);
            WebSocketClient.getInstance().sendEvent(event);
            LOGGER.fine("Branch event sent: " + branch.branchId + " - " + eventType);
        } catch (Exception e) {
            LOGGER.warning("Failed to send branch event: " + e.getMessage());
        }
    }
    
    /**
     * Handles stage completion.
     */
    private void handleStageCompletion(WorkflowRun run, FlowNode node, String stageKey, StageEventListener stageListener) {
        // Only send completion event once per stage
        String completionKey = stageKey + ":completed";
        if (stageStartEventsSent.containsKey(completionKey)) {
            return; // Already sent completion event
        }
        
        stageStartEventsSent.put(completionKey, true);
        
        // Calculate duration if we have start time
        Long startTime = stageStartTimes.get(stageKey);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
        
        try {
            // Call the overloaded method that accepts duration
            stageListener.onStageCompleted(run, node, null, duration);
            LOGGER.fine("Stage completed: " + node.getDisplayName() + " (duration: " + duration + "ms)");
        } catch (Exception e) {
            LOGGER.warning("Failed to send stage completed event: " + e.getMessage());
        }
    }
    
    /**
     * Handles stage failure.
     */
    private void handleStageFailure(WorkflowRun run, FlowNode node, String stageKey, StageEventListener stageListener) {
        // Only send failure event once per stage
        String failureKey = stageKey + ":failed";
        if (stageStartEventsSent.containsKey(failureKey)) {
            return; // Already sent failure event
        }
        
        stageStartEventsSent.put(failureKey, true);
        
        try {
            // Try to extract error from node actions or context
            Throwable error = extractStageError(node);
            stageListener.onStageFailed(run, node, null, error);
            LOGGER.fine("Stage failed: " + node.getDisplayName());
        } catch (Exception e) {
            LOGGER.warning("Failed to send stage failed event: " + e.getMessage());
        }
    }
    
    /**
     * Determines if a FlowNode is a stage node.
     */
    private boolean isStageNode(FlowNode node) {
        String displayName = node.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            return false;
        }
        
        // Check class name for stage indicators
        String className = node.getClass().getSimpleName();
        
        // Common stage node class names in Jenkins workflow
        if (className.contains("Stage") || 
            className.contains("CpsStepContext") ||
            displayName.startsWith("Stage:") ||
            displayName.contains("Stage")) {
            return true;
        }
        
        // Check for stage-like patterns in display name
        // Stages typically have names like "Stage: Build", "Stage: Test", etc.
        return displayName.matches("(?i).*stage.*") && 
               !displayName.contains("Step") && // Exclude step nodes
               !displayName.contains("Parallel"); // Exclude parallel container nodes
    }
    
    /**
     * Determines if a stage is complete.
     * Uses execution completion status and node state.
     */
    private boolean isStageComplete(FlowNode node, FlowExecution execution) {
        if (execution == null) {
            return false;
        }
        
        try {
            // If execution is complete, all stages are complete
            if (execution.isComplete()) {
                return true;
            }
            
            // Check if node is in a terminal state
            // This is a simplified check - in practice, you'd analyze the execution graph
            // to determine if all children of this stage node are complete
            
            // For now, we'll rely on execution completion or explicit stage end detection
            // A more sophisticated implementation would track the execution graph structure
            return false;
        } catch (Exception e) {
            LOGGER.fine("Error checking stage completion: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Called when flow execution completes.
     * This is a better place to detect stage completions.
     */
    @Override
    public void onCompleted(FlowExecution execution) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            Queue.Executable executable = execution.getOwner().getExecutable();
            if (executable instanceof Run) {
                Run<?, ?> run = (Run<?, ?>) executable;
                if (run instanceof WorkflowRun) {
                    WorkflowRun workflowRun = (WorkflowRun) run;
                    
                    // Clear current flow node for log association
                    LogStreamingFilter.clearCurrentFlowNode();
                    
                    // Send final execution graph snapshot
                    try {
                        ExecutionGraph.GraphSnapshot snapshot = ExecutionGraph.getGraphSnapshot(workflowRun);
                        String graphEvent = EventMapper.createExecutionGraphEvent(workflowRun, snapshot);
                        WebSocketClient.getInstance().sendEvent(graphEvent);
                        LOGGER.fine("Execution graph sent: " + snapshot.nodeCount + " nodes");
                    } catch (Exception e) {
                        LOGGER.warning("Failed to send execution graph: " + e.getMessage());
                    }
                    
                    // Complete all active branches
                    try {
                        Map<String, ExecutionGraph.BranchInfo> branches = ExecutionGraph.getBranches(workflowRun);
                        for (ExecutionGraph.BranchInfo branch : branches.values()) {
                            if (branch.status.equals("STARTED")) {
                                ExecutionGraph.completeBranch(workflowRun, branch.branchId);
                                sendBranchEvent(workflowRun, branch, "COMPLETED");
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.fine("Failed to complete branches: " + e.getMessage());
                    }
                    
                    // Mark all tracked stages as complete
                    // This ensures we send completion events for all stages when execution finishes
                    completeAllStages(workflowRun, execution);
                    
                    // Remove from active executions
                    String executionId = getExecutionId(workflowRun, execution);
                    activeExecutions.remove(executionId);
                    
                    // Clean up graph data after a delay (to allow for final events)
                    // Note: In production, you might want to keep graph data for a period
                    // ExecutionGraph.removeGraph(workflowRun);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to handle execution completion: " + e.getMessage());
        }
    }
    
    /**
     * Completes all tracked stages when execution finishes.
     */
    private void completeAllStages(WorkflowRun run, FlowExecution execution) {
        StageEventListener stageListener = getStageEventListener();
        if (stageListener == null) {
            return;
        }
        
        // Iterate through all tracked stages and send completion events
        for (Map.Entry<String, Long> entry : stageStartTimes.entrySet()) {
            String stageKey = entry.getKey();
            String completionKey = stageKey + ":completed";
            
            // Skip if already sent completion event
            if (stageStartEventsSent.containsKey(completionKey)) {
                continue;
            }
            
            // Extract node ID from stage key
            String[] parts = stageKey.split(":");
            if (parts.length < 2) {
                continue;
            }
            
            try {
                // Find the node by ID
                String nodeId = parts[1];
                FlowNode node = findNodeById(execution, nodeId);
                
                if (node != null) {
                    Long startTime = entry.getValue();
                    long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
                    
                    stageListener.onStageCompleted(run, node, null, duration);
                    stageStartEventsSent.put(completionKey, true);
                    LOGGER.fine("Stage completed on execution finish: " + node.getDisplayName() + " (duration: " + duration + "ms)");
                }
            } catch (Exception e) {
                LOGGER.fine("Error completing stage " + stageKey + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Finds a FlowNode by its ID within an execution.
     */
    private FlowNode findNodeById(FlowExecution execution, String nodeId) {
        try {
            // Iterate through execution's heads to find the node
            for (FlowNode head : execution.getCurrentHeads()) {
                FlowNode found = findNodeInGraph(head, nodeId);
                if (found != null) {
                    return found;
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Error finding node by ID: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Recursively searches for a node by ID in the execution graph.
     */
    private FlowNode findNodeInGraph(FlowNode node, String nodeId) {
        if (node.getId().equals(nodeId)) {
            return node;
        }
        
        try {
            for (FlowNode parent : node.getParents()) {
                FlowNode found = findNodeInGraph(parent, nodeId);
                if (found != null) {
                    return found;
                }
            }
        } catch (Exception e) {
            // Ignore graph traversal errors
        }
        
        return null;
    }
    
    /**
     * Generates a unique execution ID.
     */
    private String getExecutionId(WorkflowRun run, FlowExecution execution) {
        if (execution != null) {
            try {
                return run.getFullDisplayName() + "#" + run.getNumber() + "@" + execution.hashCode();
            } catch (Exception e) {
                // Fallback
            }
        }
        return run.getFullDisplayName() + "#" + run.getNumber() + "@" + run.getStartTimeInMillis();
    }
    
    /**
     * Sends periodic graph snapshots for active executions.
     */
    private static void sendPeriodicGraphSnapshots() {
        if (!isEnabledStatic()) {
            return;
        }
        
        try {
            for (Map.Entry<String, WorkflowRun> entry : activeExecutions.entrySet()) {
                WorkflowRun run = entry.getValue();
                try {
                    ExecutionGraph.GraphSnapshot snapshot = ExecutionGraph.getGraphSnapshot(run);
                    if (snapshot.nodeCount > 0) {
                        String graphEvent = EventMapper.createExecutionGraphEvent(run, snapshot);
                        WebSocketClient.getInstance().sendEvent(graphEvent);
                        LOGGER.fine("Periodic graph snapshot sent: " + snapshot.nodeCount + " nodes");
                    }
                } catch (Exception e) {
                    LOGGER.fine("Failed to send periodic graph snapshot: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error in periodic graph snapshot task: " + e.getMessage());
        }
    }
    
    /**
     * Static version of isEnabled() for use in static methods.
     */
    private static boolean isEnabledStatic() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        return config != null && config.isEnabled() && config.isValid();
    }
    
    /**
     * Determines if a stage has failed.
     */
    private boolean isStageFailed(FlowNode node) {
        try {
            // Check if node has an error action
            ErrorAction errorAction = node.getError();
            if (errorAction != null) {
                return true;
            }
            
            // Check display name for failure indicators
            String displayName = node.getDisplayName();
            if (displayName != null && displayName.toLowerCase().contains("failed")) {
                return true;
            }
            
            // Additional checks could include:
            // - Checking for other error actions on the node
            // - Checking execution result
            // - Analyzing child nodes for failures
            return false;
        } catch (Exception e) {
            LOGGER.fine("Error checking stage failure: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Extracts error information from a stage node.
     */
    private Throwable extractStageError(FlowNode node) {
        try {
            // Get error action from node
            ErrorAction errorAction = node.getError();
            if (errorAction != null) {
                // Extract the Throwable from the ErrorAction
                return errorAction.getError();
            }
            
            // Could also check other error actions or child nodes for errors
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Generates a unique key for a stage node.
     */
    private String getStageKey(FlowExecution execution, FlowNode node) {
        String executionId = execution != null ? String.valueOf(execution.hashCode()) : "unknown";
        return executionId + ":" + node.getId();
    }
    
    /**
     * Determines if a flow node is significant enough to send an event.
     * Excludes stage nodes (handled separately).
     * 
     * @param node The flow node to check
     * @return true if the node should generate an event
     */
    private boolean isSignificantNode(FlowNode node) {
        // Don't process stage nodes here (handled in handleStageNode)
        if (isStageNode(node)) {
            return false;
        }
        
        // Filter out internal nodes, only include user-visible steps
        String displayName = node.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            return false;
        }
        
        // Include major steps (but not stages)
        return displayName.contains("Step") ||
               node.getClass().getSimpleName().contains("Step");
    }
    
    private boolean isEnabled() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        return config != null && config.isEnabled() && config.isValid();
    }
}

