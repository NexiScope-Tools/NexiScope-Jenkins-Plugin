package io.nexiscope.jenkins.plugin.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.model.FreeStyleBuild;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.listeners.MatrixBuildListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Maps Jenkins internal events to NexiScope event format.
 * 
 * Ensures type safety and schema compliance with the shared protocol.
 * 
 * @author NexiScope Team
 */
public class EventMapper {
    
    private static final Logger LOGGER = Logger.getLogger(EventMapper.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Creates a pipeline started event.
     */
    public static String createPipelineStartedEvent(WorkflowRun run) {
        ObjectNode event = createBaseEvent("PIPELINE_STARTED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", "STARTED");
        data.put("startTime", run.getStartTimeInMillis());
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a pipeline completed event.
     */
    public static String createPipelineCompletedEvent(WorkflowRun run) {
        ObjectNode event = createBaseEvent("PIPELINE_COMPLETED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", run.getResult() != null ? run.getResult().toString() : "UNKNOWN");
        data.put("startTime", run.getStartTimeInMillis());
        data.put("duration", run.getDuration());
        data.put("buildNumber", run.getNumber());
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a pipeline aborted event.
     */
    public static String createPipelineAbortedEvent(WorkflowRun run) {
        ObjectNode event = createBaseEvent("PIPELINE_ABORTED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", "ABORTED");
        data.put("startTime", run.getStartTimeInMillis());
        data.put("duration", run.getDuration());
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a pipeline deleted event.
     */
    public static String createPipelineDeletedEvent(WorkflowRun run) {
        ObjectNode event = createBaseEvent("PIPELINE_DELETED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("buildNumber", run.getNumber());
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a freestyle build started event.
     */
    public static String createFreestyleBuildStartedEvent(FreeStyleBuild build) {
        ObjectNode event = createBaseEvent("FREESTYLE_BUILD_STARTED", build);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", "STARTED");
        data.put("startTime", build.getStartTimeInMillis());
        
        // Extract build parameters
        ObjectNode parameters = extractBuildParameters(build);
        if (parameters.size() > 0) {
            data.set("parameters", parameters);
        }
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a freestyle build completed event.
     */
    public static String createFreestyleBuildCompletedEvent(FreeStyleBuild build) {
        ObjectNode event = createBaseEvent("FREESTYLE_BUILD_COMPLETED", build);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", build.getResult() != null ? build.getResult().toString() : "UNKNOWN");
        data.put("startTime", build.getStartTimeInMillis());
        data.put("duration", build.getDuration());
        data.put("buildNumber", build.getNumber());
        
        // Extract build parameters
        ObjectNode parameters = extractBuildParameters(build);
        if (parameters.size() > 0) {
            data.set("parameters", parameters);
        }
        
        // Extract build steps info
        ObjectNode steps = extractBuildSteps(build);
        if (steps.size() > 0) {
            data.set("steps", steps);
        }
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a freestyle build deleted event.
     */
    public static String createFreestyleBuildDeletedEvent(FreeStyleBuild build) {
        ObjectNode event = createBaseEvent("FREESTYLE_BUILD_DELETED", build);
        ObjectNode data = mapper.createObjectNode();
        data.put("buildNumber", build.getNumber());
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a matrix build started event.
     */
    public static String createMatrixBuildStartedEvent(MatrixBuild build) {
        ObjectNode event = createBaseEvent("MATRIX_BUILD_STARTED", build);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", "STARTED");
        data.put("startTime", build.getStartTimeInMillis());
        
        // Add matrix configuration info
        ObjectNode matrixConfig = mapper.createObjectNode();
        int totalCombinations = build.getRuns().size();
        matrixConfig.put("totalCombinations", totalCombinations);
        
        // Extract axes (e.g., OS, JDK versions)
        com.fasterxml.jackson.databind.node.ArrayNode axesArray = mapper.createArrayNode();
        if (build.getParent() instanceof hudson.matrix.MatrixProject) {
            hudson.matrix.MatrixProject project = (hudson.matrix.MatrixProject) build.getParent();
            for (hudson.matrix.Axis axis : project.getAxes()) {
                ObjectNode axisNode = mapper.createObjectNode();
                axisNode.put("name", axis.getName());
                axisNode.put("type", axis.getClass().getSimpleName());
                com.fasterxml.jackson.databind.node.ArrayNode valuesArray = mapper.createArrayNode();
                for (Object valueObj : axis.getValues()) {
                    // AxisValue is an inner class, access via axis
                    String valueName = valueObj != null ? valueObj.toString() : "null";
                    valuesArray.add(valueName);
                }
                axisNode.set("values", valuesArray);
                axesArray.add(axisNode);
            }
        }
        matrixConfig.set("axes", axesArray);
        data.set("matrixConfig", matrixConfig);
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a matrix build completed event with aggregated results.
     */
    public static String createMatrixBuildCompletedEvent(MatrixBuild build, 
                                                         java.util.List<MatrixBuildListener.MatrixCombinationResult> combinationResults) {
        ObjectNode event = createBaseEvent("MATRIX_BUILD_COMPLETED", build);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", build.getResult() != null ? build.getResult().toString() : "UNKNOWN");
        data.put("startTime", build.getStartTimeInMillis());
        data.put("duration", build.getDuration());
        data.put("buildNumber", build.getNumber());
        
        // Aggregate combination results
        int totalCombinations = combinationResults.size();
        int successful = 0;
        int failed = 0;
        int unstable = 0;
        int aborted = 0;
        long totalDuration = 0;
        
        com.fasterxml.jackson.databind.node.ArrayNode combinationsArray = mapper.createArrayNode();
        for (MatrixBuildListener.MatrixCombinationResult result : combinationResults) {
            ObjectNode combinationNode = mapper.createObjectNode();
            combinationNode.put("name", result.combinationName);
            combinationNode.put("buildNumber", result.buildNumber);
            combinationNode.put("result", result.result);
            combinationNode.put("duration", result.duration);
            combinationNode.put("startTime", result.startTime);
            combinationsArray.add(combinationNode);
            
            // Count results
            if ("SUCCESS".equals(result.result)) {
                successful++;
            } else if ("FAILURE".equals(result.result)) {
                failed++;
            } else if ("UNSTABLE".equals(result.result)) {
                unstable++;
            } else if ("ABORTED".equals(result.result)) {
                aborted++;
            }
            totalDuration += result.duration;
        }
        
        ObjectNode summary = mapper.createObjectNode();
        summary.put("totalCombinations", totalCombinations);
        summary.put("successful", successful);
        summary.put("failed", failed);
        summary.put("unstable", unstable);
        summary.put("aborted", aborted);
        summary.put("averageDuration", totalCombinations > 0 ? totalDuration / totalCombinations : 0);
        data.set("summary", summary);
        data.set("combinations", combinationsArray);
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a matrix build deleted event.
     */
    public static String createMatrixBuildDeletedEvent(MatrixBuild build) {
        ObjectNode event = createBaseEvent("MATRIX_BUILD_DELETED", build);
        ObjectNode data = mapper.createObjectNode();
        data.put("buildNumber", build.getNumber());
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a matrix combination started event.
     */
    public static String createMatrixCombinationStartedEvent(MatrixRun run, MatrixBuild parentBuild) {
        ObjectNode event = createBaseEvent("MATRIX_COMBINATION_STARTED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", "STARTED");
        data.put("startTime", run.getStartTimeInMillis());
        data.put("combinationName", run.getParent().getName());
        
        // Add parent matrix build info
        ObjectNode parentInfo = mapper.createObjectNode();
        parentInfo.put("jobName", parentBuild.getParent().getFullName());
        parentInfo.put("buildNumber", parentBuild.getNumber());
        data.set("parentMatrixBuild", parentInfo);
        
        // Extract combination parameters (axis values)
        ObjectNode combinationParams = mapper.createObjectNode();
        if (run.getParent() instanceof hudson.matrix.MatrixConfiguration) {
            hudson.matrix.MatrixConfiguration config = (hudson.matrix.MatrixConfiguration) run.getParent();
            hudson.matrix.Combination combination = config.getCombination();
            if (combination != null) {
                // Combination is a Map<String, String> where key is axis name and value is axis value
                for (Map.Entry<String, String> entry : combination.entrySet()) {
                    combinationParams.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (combinationParams.size() > 0) {
            data.set("combinationParams", combinationParams);
        }
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a matrix combination completed event.
     */
    public static String createMatrixCombinationCompletedEvent(MatrixRun run, MatrixBuild parentBuild) {
        ObjectNode event = createBaseEvent("MATRIX_COMBINATION_COMPLETED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("status", run.getResult() != null ? run.getResult().toString() : "UNKNOWN");
        data.put("startTime", run.getStartTimeInMillis());
        data.put("duration", run.getDuration());
        data.put("buildNumber", run.getNumber());
        data.put("combinationName", run.getParent().getName());
        
        // Add parent matrix build info
        ObjectNode parentInfo = mapper.createObjectNode();
        parentInfo.put("jobName", parentBuild.getParent().getFullName());
        parentInfo.put("buildNumber", parentBuild.getNumber());
        data.set("parentMatrixBuild", parentInfo);
        
        // Extract combination parameters (axis values)
        ObjectNode combinationParams = mapper.createObjectNode();
        if (run.getParent() instanceof hudson.matrix.MatrixConfiguration) {
            hudson.matrix.MatrixConfiguration config = (hudson.matrix.MatrixConfiguration) run.getParent();
            hudson.matrix.Combination combination = config.getCombination();
            if (combination != null) {
                // Combination is a Map<String, String> where key is axis name and value is axis value
                for (Map.Entry<String, String> entry : combination.entrySet()) {
                    combinationParams.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (combinationParams.size() > 0) {
            data.set("combinationParams", combinationParams);
        }
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Extracts build parameters from a freestyle build.
     */
    private static ObjectNode extractBuildParameters(FreeStyleBuild build) {
        ObjectNode parameters = mapper.createObjectNode();
        
        try {
            ParametersAction paramsAction = build.getAction(ParametersAction.class);
            if (paramsAction != null) {
                for (ParameterValue param : paramsAction.getParameters()) {
                    String name = param.getName();
                    Object value = param.getValue();
                    
                    // Add parameter based on type
                    if (value == null) {
                        parameters.putNull(name);
                    } else if (value instanceof String) {
                        parameters.put(name, (String) value);
                    } else if (value instanceof Number) {
                        if (value instanceof Integer) {
                            parameters.put(name, (Integer) value);
                        } else if (value instanceof Long) {
                            parameters.put(name, (Long) value);
                        } else if (value instanceof Double) {
                            parameters.put(name, (Double) value);
                        } else if (value instanceof Float) {
                            parameters.put(name, (Float) value);
                        } else {
                            parameters.put(name, value.toString());
                        }
                    } else if (value instanceof Boolean) {
                        parameters.put(name, (Boolean) value);
                    } else {
                        // Convert other types to string
                        parameters.put(name, value.toString());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to extract build parameters: " + e.getMessage());
        }
        
        return parameters;
    }
    
    /**
     * Extracts build steps information from a freestyle build.
     */
    private static ObjectNode extractBuildSteps(FreeStyleBuild build) {
        ObjectNode steps = mapper.createObjectNode();
        
        try {
            com.fasterxml.jackson.databind.node.ArrayNode stepsArray = mapper.createArrayNode();
            
            // Get build actions that represent steps
            // Note: Freestyle builds don't have a direct step API like pipelines,
            // so we extract what we can from build actions and log
            if (build.getActions() != null) {
                int stepIndex = 0;
                for (hudson.model.Action action : build.getActions()) {
                    if (action instanceof hudson.tasks.BuildStep) {
                        ObjectNode step = mapper.createObjectNode();
                        step.put("index", stepIndex++);
                        step.put("type", action.getClass().getSimpleName());
                        step.put("displayName", action.getDisplayName());
                        stepsArray.add(step);
                    }
                }
            }
            
            steps.set("stepList", stepsArray);
            steps.put("stepCount", stepsArray.size());
        } catch (Exception e) {
            LOGGER.warning("Failed to extract build steps: " + e.getMessage());
        }
        
        return steps;
    }
    
    /**
     * Creates a stage started event.
     */
    public static String createStageStartedEvent(WorkflowRun run, FlowNode node) {
        ObjectNode event = createBaseEvent("STAGE_STARTED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("stageName", node.getDisplayName());
        data.put("nodeId", node.getId());
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a stage completed event.
     */
    public static String createStageCompletedEvent(WorkflowRun run, FlowNode node) {
        return createStageCompletedEvent(run, node, 0);
    }
    
    /**
     * Creates a stage completed event with duration.
     */
    public static String createStageCompletedEvent(WorkflowRun run, FlowNode node, long durationMs) {
        ObjectNode event = createBaseEvent("STAGE_COMPLETED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("stageName", node.getDisplayName());
        data.put("nodeId", node.getId());
        data.put("duration", durationMs);
        
        // Try to determine stage status
        try {
            // Check if we can determine status from node or run
            String status = "SUCCESS"; // Default
            data.put("status", status);
        } catch (Exception e) {
            // Ignore status determination errors
        }
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a stage failed event.
     */
    public static String createStageFailedEvent(WorkflowRun run, FlowNode node, Throwable error) {
        ObjectNode event = createBaseEvent("STAGE_FAILED", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("stageName", node.getDisplayName());
        data.put("nodeId", node.getId());
        if (error != null) {
            data.put("errorMessage", error.getMessage());
            data.put("errorType", error.getClass().getSimpleName());
        }
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a flow node event.
     */
    public static String createFlowNodeEvent(WorkflowRun run, FlowNode node) {
        ObjectNode event = createBaseEvent("FLOW_NODE", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("nodeName", node.getDisplayName());
        data.put("nodeId", node.getId());
        data.put("nodeType", node.getClass().getSimpleName());
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a log line event.
     */
    public static String createLogLineEvent(WorkflowRun run, String logLine, FlowNode node) {
        ObjectNode event = createBaseEvent("LOG_LINE", run);
        ObjectNode data = mapper.createObjectNode();
        data.put("logLine", logLine);
        data.put("timestamp", System.currentTimeMillis());
        
        // Associate with current node if available
        if (node != null) {
            data.put("nodeId", node.getId());
            data.put("nodeName", node.getDisplayName());
        }
        
        // Detect log level from line content
        String logLevel = detectLogLevel(logLine);
        if (logLevel != null) {
            data.put("logLevel", logLevel);
        }
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Detects log level from log line content.
     */
    private static String detectLogLevel(String logLine) {
        String upperLine = logLine.toUpperCase();
        
        if (upperLine.contains("ERROR") || upperLine.contains("EXCEPTION") || 
            upperLine.contains("FAILED") || upperLine.contains("FATAL")) {
            return "ERROR";
        } else if (upperLine.contains("WARN") || upperLine.contains("WARNING")) {
            return "WARN";
        } else if (upperLine.contains("DEBUG") || upperLine.contains("TRACE")) {
            return "DEBUG";
        } else if (upperLine.contains("INFO") || upperLine.contains("INFORMATION")) {
            return "INFO";
        }
        
        return null; // Unknown level
    }
    
    /**
     * Creates an execution graph event.
     */
    public static String createExecutionGraphEvent(WorkflowRun run, ExecutionGraph.GraphSnapshot snapshot) {
        ObjectNode event = createBaseEvent("EXECUTION_GRAPH", run);
        ObjectNode data = mapper.createObjectNode();
        
        // Graph metadata
        data.put("nodeCount", snapshot.nodeCount);
        data.put("graphAge", snapshot.graphAge);
        data.put("parallelBranchCount", snapshot.parallelBranches.size());
        
        // Nodes array
        com.fasterxml.jackson.databind.node.ArrayNode nodesArray = mapper.createArrayNode();
        for (ExecutionGraph.NodeInfo nodeInfo : snapshot.nodes.values()) {
            ObjectNode nodeObj = mapper.createObjectNode();
            nodeObj.put("nodeId", nodeInfo.nodeId);
            nodeObj.put("nodeName", nodeInfo.nodeName);
            nodeObj.put("nodeType", nodeInfo.nodeType);
            nodeObj.put("timestamp", nodeInfo.timestamp);
            nodeObj.put("isParallel", nodeInfo.isParallel);
            
            if (nodeInfo.stageName != null) {
                nodeObj.put("stageName", nodeInfo.stageName);
            }
            
            // Parent IDs
            com.fasterxml.jackson.databind.node.ArrayNode parentArray = mapper.createArrayNode();
            for (String parentId : nodeInfo.parentIds) {
                parentArray.add(parentId);
            }
            nodeObj.set("parentIds", parentArray);
            
            // Child IDs
            com.fasterxml.jackson.databind.node.ArrayNode childArray = mapper.createArrayNode();
            for (String childId : nodeInfo.childIds) {
                childArray.add(childId);
            }
            nodeObj.set("childIds", childArray);
            
            nodesArray.add(nodeObj);
        }
        data.set("nodes", nodesArray);
        
        // Parallel branches (node IDs)
        com.fasterxml.jackson.databind.node.ArrayNode parallelArray = mapper.createArrayNode();
        for (String branchId : snapshot.parallelBranches) {
            parallelArray.add(branchId);
        }
        data.set("parallelBranches", parallelArray);
        
        // Branch information
        com.fasterxml.jackson.databind.node.ArrayNode branchesArray = mapper.createArrayNode();
        for (ExecutionGraph.BranchInfo branch : snapshot.branches.values()) {
            ObjectNode branchObj = mapper.createObjectNode();
            branchObj.put("branchId", branch.branchId);
            branchObj.put("branchName", branch.branchName);
            branchObj.put("parentNodeId", branch.parentNodeId);
            branchObj.put("startTime", branch.startTime);
            branchObj.put("status", branch.status);
            if (branch.endTime != null) {
                branchObj.put("endTime", branch.endTime);
                branchObj.put("duration", branch.getDuration());
            }
            
            // Node IDs in this branch
            com.fasterxml.jackson.databind.node.ArrayNode branchNodesArray = mapper.createArrayNode();
            for (String nodeId : branch.nodeIds) {
                branchNodesArray.add(nodeId);
            }
            branchObj.set("nodeIds", branchNodesArray);
            
            branchesArray.add(branchObj);
        }
        data.set("branches", branchesArray);
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates an execution graph update event (for incremental updates).
     */
    public static String createExecutionGraphUpdateEvent(WorkflowRun run, ExecutionGraph.NodeInfo newNode) {
        ObjectNode event = createBaseEvent("EXECUTION_GRAPH_UPDATE", run);
        ObjectNode data = mapper.createObjectNode();
        
        // New node information
        ObjectNode nodeObj = mapper.createObjectNode();
        nodeObj.put("nodeId", newNode.nodeId);
        nodeObj.put("nodeName", newNode.nodeName);
        nodeObj.put("nodeType", newNode.nodeType);
        nodeObj.put("timestamp", newNode.timestamp);
        nodeObj.put("isParallel", newNode.isParallel);
        
        if (newNode.stageName != null) {
            nodeObj.put("stageName", newNode.stageName);
        }
        
        // Parent IDs
        com.fasterxml.jackson.databind.node.ArrayNode parentArray = mapper.createArrayNode();
        for (String parentId : newNode.parentIds) {
            parentArray.add(parentId);
        }
        nodeObj.set("parentIds", parentArray);
        
        data.set("node", nodeObj);
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a performance metrics event.
     */
    public static String createPerformanceMetricsEvent(Run<?, ?> run, io.nexiscope.jenkins.plugin.metrics.PerformanceMetrics.AggregatedMetrics metrics) {
        ObjectNode event = createBaseEvent("PERFORMANCE_METRICS", run);
        ObjectNode data = mapper.createObjectNode();
        
        data.put("jobName", metrics.jobName);
        data.put("sampleCount", metrics.sampleCount);
        data.put("timestamp", metrics.timestamp);
        
        // Build duration metrics
        ObjectNode buildDuration = mapper.createObjectNode();
        buildDuration.put("avg", metrics.avgBuildDuration);
        buildDuration.put("min", metrics.minBuildDuration);
        buildDuration.put("max", metrics.maxBuildDuration);
        buildDuration.put("p50", metrics.p50BuildDuration);
        buildDuration.put("p95", metrics.p95BuildDuration);
        buildDuration.put("p99", metrics.p99BuildDuration);
        data.set("buildDuration", buildDuration);
        
        // Queue time metrics
        ObjectNode queueTime = mapper.createObjectNode();
        queueTime.put("avg", metrics.avgQueueTime);
        queueTime.put("min", metrics.minQueueTime);
        queueTime.put("max", metrics.maxQueueTime);
        data.set("queueTime", queueTime);
        
        // Stage metrics
        com.fasterxml.jackson.databind.node.ArrayNode stagesArray = mapper.createArrayNode();
        for (io.nexiscope.jenkins.plugin.metrics.PerformanceMetrics.StageMetrics stage : metrics.stageMetrics.values()) {
            ObjectNode stageObj = mapper.createObjectNode();
            stageObj.put("stageName", stage.stageName);
            stageObj.put("sampleCount", stage.sampleCount);
            stageObj.put("avgDuration", stage.avgDuration);
            stageObj.put("minDuration", stage.minDuration);
            stageObj.put("maxDuration", stage.maxDuration);
            stageObj.put("p50Duration", stage.p50Duration);
            stageObj.put("p95Duration", stage.p95Duration);
            stageObj.put("p99Duration", stage.p99Duration);
            stagesArray.add(stageObj);
        }
        data.set("stageMetrics", stagesArray);
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a custom event from pipeline code.
     */
    public static String createCustomEvent(WorkflowRun run, String eventType, Map<String, Object> customData) {
        ObjectNode event = createBaseEvent(eventType, run);
        ObjectNode data = mapper.createObjectNode();
        
        // Add custom data fields
        if (customData != null) {
            for (Map.Entry<String, Object> entry : customData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                // Validate key (must be alphanumeric with underscores)
                if (key == null || !key.matches("^[a-zA-Z0-9_]+$")) {
                    LOGGER.warning("Invalid custom event key: " + key + ", skipping");
                    continue;
                }
                
                // Add value based on type
                if (value == null) {
                    data.putNull(key);
                } else if (value instanceof String) {
                    data.put(key, (String) value);
                } else if (value instanceof Number) {
                    if (value instanceof Integer) {
                        data.put(key, (Integer) value);
                    } else if (value instanceof Long) {
                        data.put(key, (Long) value);
                    } else if (value instanceof Double) {
                        data.put(key, (Double) value);
                    } else if (value instanceof Float) {
                        data.put(key, (Float) value);
                    } else {
                        data.put(key, value.toString());
                    }
                } else if (value instanceof Boolean) {
                    data.put(key, (Boolean) value);
                } else {
                    // Convert other types to string
                    data.put(key, value.toString());
                }
            }
        }
        
        // Add metadata
        data.put("isCustomEvent", true);
        data.put("customEventType", eventType);
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates a branch lifecycle event (started, completed, failed).
     */
    public static String createBranchEvent(WorkflowRun run, ExecutionGraph.BranchInfo branch, String eventType) {
        ObjectNode event = createBaseEvent("BRANCH_" + eventType, run, branch.branchId);
        ObjectNode data = mapper.createObjectNode();
        
        data.put("branchId", branch.branchId);
        data.put("branchName", branch.branchName);
        data.put("parentNodeId", branch.parentNodeId);
        data.put("startTime", branch.startTime);
        data.put("status", branch.status);
        
        if (branch.endTime != null) {
            data.put("endTime", branch.endTime);
            data.put("duration", branch.getDuration());
        }
        
        // Node IDs in this branch
        com.fasterxml.jackson.databind.node.ArrayNode nodeIdsArray = mapper.createArrayNode();
        for (String nodeId : branch.nodeIds) {
            nodeIdsArray.add(nodeId);
        }
        data.set("nodeIds", nodeIdsArray);
        
        event.set("data", data);
        return serializeEvent(event);
    }
    
    /**
     * Creates the base event structure.
     */
    private static ObjectNode createBaseEvent(String eventType, Run<?, ?> run) {
        return createBaseEvent(eventType, run, null);
    }
    
    /**
     * Creates the base event structure with optional branch ID.
     */
    private static ObjectNode createBaseEvent(String eventType, Run<?, ?> run, String branchId) {
        ObjectNode event = mapper.createObjectNode();
        
        // Event metadata
        event.put("eventId", UUID.randomUUID().toString());
        event.put("type", eventType);
        event.put("timestamp", Instant.now().toString());
        
        // Jenkins instance info
        ObjectNode jenkins = mapper.createObjectNode();
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        jenkins.put("instanceId", config != null ? config.getInstanceId() : "unknown");
        jenkins.put("url", Jenkins.get().getRootUrl());
        jenkins.put("version", Jenkins.get().getVersion().toString());
        event.set("jenkins", jenkins);
        
        // Pipeline info
        ObjectNode pipeline = mapper.createObjectNode();
        pipeline.put("jobName", run.getParent().getFullName());
        pipeline.put("runNumber", run.getNumber());
        pipeline.put("runId", run.getId());
        
        // Add branch ID if provided
        if (branchId != null) {
            pipeline.put("branchId", branchId);
        }
        
        // Extract branch and commit info from SCM
        ScmInfoExtractor.ScmInfo scmInfo = null;
        if (run instanceof WorkflowRun) {
            WorkflowRun workflowRun = (WorkflowRun) run;
            scmInfo = ScmInfoExtractor.extract(workflowRun);
        } else if (run instanceof FreeStyleBuild) {
            FreeStyleBuild freestyleBuild = (FreeStyleBuild) run;
            scmInfo = ScmInfoExtractor.extract(freestyleBuild);
        }
        
        if (scmInfo != null) {
            if (scmInfo.hasBranch()) {
                pipeline.put("branch", scmInfo.getBranch());
            }
            if (scmInfo.hasCommitHash()) {
                pipeline.put("commitHash", scmInfo.getCommitHash());
            }
            // Optionally include SCM type for debugging
            if (scmInfo.getScmType() != null && !"unknown".equals(scmInfo.getScmType())) {
                pipeline.put("scmType", scmInfo.getScmType());
            }
        }
        
        event.set("pipeline", pipeline);
        
        return event;
    }
    
    /**
     * Serializes the event to JSON string.
     */
    private static String serializeEvent(ObjectNode event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (Exception e) {
            LOGGER.severe("Failed to serialize event: " + e.getMessage());
            throw new RuntimeException("Event serialization failed", e);
        }
    }
}

