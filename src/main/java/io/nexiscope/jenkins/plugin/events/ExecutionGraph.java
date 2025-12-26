package io.nexiscope.jenkins.plugin.events;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the execution graph for a pipeline run.
 * 
 * Tracks:
 * - All flow nodes with their relationships
 * - Parent/child node graph
 * - Parallel branch execution
 * - Node to stage/step mapping
 * 
 * @author NexiScope Team
 */
public class ExecutionGraph {
    
    private static final Logger LOGGER = Logger.getLogger(ExecutionGraph.class.getName());
    
    // Graph storage: executionId -> GraphData
    private static final Map<String, GraphData> graphs = new ConcurrentHashMap<>();
    
    /**
     * Graph data for a single execution.
     */
    private static class GraphData {
        final WorkflowRun run;
        final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
        final Map<String, Set<String>> parentChildMap = new ConcurrentHashMap<>();
        final Map<String, Set<String>> childParentMap = new ConcurrentHashMap<>();
        final Map<String, String> nodeToStageMap = new ConcurrentHashMap<>();
        final Set<String> parallelBranches = ConcurrentHashMap.newKeySet();
        
        // Parallel branch tracking
        final Map<String, BranchInfo> branches = new ConcurrentHashMap<>(); // branchId -> BranchInfo
        final Map<String, String> nodeToBranchMap = new ConcurrentHashMap<>(); // nodeId -> branchId
        final Map<String, Set<String>> branchNodes = new ConcurrentHashMap<>(); // branchId -> Set<nodeId>
        
        final long startTime;
        
        GraphData(WorkflowRun run) {
            this.run = run;
            this.startTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Information about a parallel branch.
     */
    public static class BranchInfo {
        public final String branchId;
        public final String branchName;
        public final String parentNodeId; // The parallel container node
        public final long startTime;
        public Long endTime;
        public String status; // STARTED, COMPLETED, FAILED
        public final Set<String> nodeIds = ConcurrentHashMap.newKeySet();
        
        public BranchInfo(String branchId, String branchName, String parentNodeId) {
            this.branchId = branchId;
            this.branchName = branchName;
            this.parentNodeId = parentNodeId;
            this.startTime = System.currentTimeMillis();
            this.status = "STARTED";
        }
        
        public long getDuration() {
            if (endTime != null) {
                return endTime - startTime;
            }
            return System.currentTimeMillis() - startTime;
        }
    }
    
    /**
     * Information about a node in the graph.
     */
    public static class NodeInfo {
        public final String nodeId;
        public final String nodeName;
        public final String nodeType;
        public final long timestamp;
        public final List<String> parentIds;
        public final List<String> childIds; // Mutable to allow updates
        public final String stageName;
        public final boolean isParallel;
        
        public NodeInfo(String nodeId, String nodeName, String nodeType, long timestamp,
                       List<String> parentIds, String stageName, boolean isParallel) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.nodeType = nodeType;
            this.timestamp = timestamp;
            this.parentIds = parentIds != null ? new ArrayList<>(parentIds) : new ArrayList<>();
            this.childIds = new ArrayList<>(); // Initialize empty, will be populated
            this.stageName = stageName;
            this.isParallel = isParallel;
        }
    }
    
    /**
     * Gets or creates graph data for an execution.
     */
    private static GraphData getOrCreateGraph(WorkflowRun run) {
        String executionId = getExecutionId(run);
        return graphs.computeIfAbsent(executionId, k -> new GraphData(run));
    }
    
    /**
     * Generates a unique execution ID.
     */
    private static String getExecutionId(WorkflowRun run) {
        return run.getFullDisplayName() + "#" + run.getNumber() + "@" + run.getStartTimeInMillis();
    }
    
    /**
     * Adds a node to the execution graph.
     */
    public static void addNode(WorkflowRun run, FlowNode node) {
        try {
            GraphData graph = getOrCreateGraph(run);
            String nodeId = node.getId();
            
            // Get parent IDs
            List<String> parentIds = new ArrayList<>();
            try {
                for (FlowNode parent : node.getParents()) {
                    parentIds.add(parent.getId());
                    // Build parent-child relationships
                    graph.parentChildMap.computeIfAbsent(parent.getId(), k -> ConcurrentHashMap.newKeySet()).add(nodeId);
                    graph.childParentMap.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet()).add(parent.getId());
                }
            } catch (Exception e) {
                LOGGER.fine("Error getting parents for node " + nodeId + ": " + e.getMessage());
            }
            
            // Detect if this is a parallel branch container
            boolean isParallel = isParallelNode(node);
            if (isParallel) {
                graph.parallelBranches.add(nodeId);
            }
            
            // Detect and assign branch ID
            String branchId = assignBranchId(graph, node, parentIds);
            if (branchId != null) {
                graph.nodeToBranchMap.put(nodeId, branchId);
                graph.branchNodes.computeIfAbsent(branchId, k -> ConcurrentHashMap.newKeySet()).add(nodeId);
            }
            
            // Detect stage name
            String stageName = extractStageName(node);
            if (stageName != null) {
                graph.nodeToStageMap.put(nodeId, stageName);
            }
            
            // Create node info
            NodeInfo nodeInfo = new NodeInfo(
                nodeId,
                node.getDisplayName(),
                node.getClass().getSimpleName(),
                System.currentTimeMillis(),
                parentIds,
                stageName,
                isParallel
            );
            
            graph.nodes.put(nodeId, nodeInfo);
            
            // Update parent nodes' child lists
            for (String parentId : parentIds) {
                NodeInfo parentInfo = graph.nodes.get(parentId);
                if (parentInfo != null) {
                    if (!parentInfo.childIds.contains(nodeId)) {
                        parentInfo.childIds.add(nodeId);
                    }
                }
            }
            
            // Also update child-parent relationships for reverse lookup
            for (String parentId : parentIds) {
                graph.childParentMap.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet()).add(parentId);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Failed to add node to execution graph: " + e.getMessage());
        }
    }
    
    /**
     * Gets the complete graph structure for an execution.
     */
    public static GraphSnapshot getGraphSnapshot(WorkflowRun run) {
        GraphData graph = getOrCreateGraph(run);
        return new GraphSnapshot(graph);
    }
    
    /**
     * Checks if a node is part of a parallel branch.
     */
    private static boolean isParallelNode(FlowNode node) {
        try {
            String displayName = node.getDisplayName();
            if (displayName != null) {
                String upper = displayName.toUpperCase();
                if (upper.contains("PARALLEL") || upper.contains("BRANCH")) {
                    return true;
                }
            }
            
            // Check class name
            String className = node.getClass().getSimpleName();
            if (className.contains("Parallel") || className.contains("Branch")) {
                return true;
            }
            
            // Check if node has multiple parents (common in parallel execution)
            List<FlowNode> parents = node.getParents();
            if (parents != null && parents.size() > 1) {
                return true;
            }
            
        } catch (Exception e) {
            // Ignore errors
        }
        
        return false;
    }
    
    /**
     * Extracts stage name from a node.
     */
    private static String extractStageName(FlowNode node) {
        try {
            String displayName = node.getDisplayName();
            if (displayName != null && displayName.contains("Stage:")) {
                // Extract stage name from "Stage: Build" format
                int colonIndex = displayName.indexOf(":");
                if (colonIndex >= 0 && colonIndex < displayName.length() - 1) {
                    return displayName.substring(colonIndex + 1).trim();
                }
            }
            
            // Check if display name looks like a stage
            if (displayName != null && displayName.matches("(?i).*stage.*")) {
                return displayName;
            }
        } catch (Exception e) {
            // Ignore errors
        }
        
        return null;
    }
    
    /**
     * Assigns a branch ID to a node based on its position in the parallel execution graph.
     */
    private static String assignBranchId(GraphData graph, FlowNode node, List<String> parentIds) {
        // Check if any parent is a parallel container
        for (String parentId : parentIds) {
            NodeInfo parentInfo = graph.nodes.get(parentId);
            if (parentInfo != null && parentInfo.isParallel) {
                // This node belongs to a parallel branch
                // Find or create branch info for this parallel container
                String branchId = findOrCreateBranch(graph, parentId, node);
                return branchId;
            }
        }
        
        // Check if this node itself is a parallel container
        if (isParallelNode(node)) {
            // This is a parallel container - create a branch for it
            String branchId = findOrCreateBranch(graph, node.getId(), node);
            return branchId;
        }
        
        // Check if node is already assigned to a branch (inherited from parent)
        for (String parentId : parentIds) {
            String parentBranchId = graph.nodeToBranchMap.get(parentId);
            if (parentBranchId != null) {
                // Inherit branch from parent
                graph.nodeToBranchMap.put(node.getId(), parentBranchId);
                graph.branchNodes.computeIfAbsent(parentBranchId, k -> ConcurrentHashMap.newKeySet()).add(node.getId());
                return parentBranchId;
            }
        }
        
        return null; // Not part of a parallel branch
    }
    
    /**
     * Finds or creates a branch info for a parallel container.
     */
    private static String findOrCreateBranch(GraphData graph, String containerNodeId, FlowNode node) {
        // Check if branch already exists for this container
        for (Map.Entry<String, BranchInfo> entry : graph.branches.entrySet()) {
            if (entry.getValue().parentNodeId.equals(containerNodeId)) {
                return entry.getKey();
            }
        }
        
        // Create new branch
        String branchId = "branch-" + containerNodeId + "-" + System.currentTimeMillis();
        String branchName = node.getDisplayName();
        if (branchName == null || branchName.isEmpty()) {
            branchName = "Branch " + (graph.branches.size() + 1);
        }
        
        BranchInfo branchInfo = new BranchInfo(branchId, branchName, containerNodeId);
        graph.branches.put(branchId, branchInfo);
        branchInfo.nodeIds.add(node.getId());
        
        return branchId;
    }
    
    /**
     * Gets branch information for a node.
     */
    public static BranchInfo getBranchForNode(WorkflowRun run, String nodeId) {
        GraphData graph = getOrCreateGraph(run);
        String branchId = graph.nodeToBranchMap.get(nodeId);
        if (branchId != null) {
            return graph.branches.get(branchId);
        }
        return null;
    }
    
    /**
     * Marks a branch as completed.
     */
    public static void completeBranch(WorkflowRun run, String branchId) {
        GraphData graph = getOrCreateGraph(run);
        BranchInfo branch = graph.branches.get(branchId);
        if (branch != null && branch.status.equals("STARTED")) {
            branch.endTime = System.currentTimeMillis();
            branch.status = "COMPLETED";
        }
    }
    
    /**
     * Marks a branch as failed.
     */
    public static void failBranch(WorkflowRun run, String branchId) {
        GraphData graph = getOrCreateGraph(run);
        BranchInfo branch = graph.branches.get(branchId);
        if (branch != null && branch.status.equals("STARTED")) {
            branch.endTime = System.currentTimeMillis();
            branch.status = "FAILED";
        }
    }
    
    /**
     * Gets all branches for an execution.
     */
    public static Map<String, BranchInfo> getBranches(WorkflowRun run) {
        GraphData graph = getOrCreateGraph(run);
        return new HashMap<>(graph.branches);
    }
    
    /**
     * Removes graph data for a completed execution.
     */
    public static void removeGraph(WorkflowRun run) {
        String executionId = getExecutionId(run);
        graphs.remove(executionId);
    }
    
    /**
     * Snapshot of the execution graph at a point in time.
     */
    public static class GraphSnapshot {
        public final WorkflowRun run;
        public final Map<String, NodeInfo> nodes;
        public final Map<String, Set<String>> parentChildMap;
        public final Map<String, String> nodeToStageMap;
        public final Set<String> parallelBranches;
        public final Map<String, BranchInfo> branches;
        public final Map<String, String> nodeToBranchMap;
        public final long graphAge;
        public final int nodeCount;
        
        GraphSnapshot(GraphData graph) {
            this.run = graph.run;
            this.nodes = new HashMap<>(graph.nodes);
            this.parentChildMap = new HashMap<>();
            graph.parentChildMap.forEach((k, v) -> this.parentChildMap.put(k, new HashSet<>(v)));
            this.nodeToStageMap = new HashMap<>(graph.nodeToStageMap);
            this.parallelBranches = new HashSet<>(graph.parallelBranches);
            this.branches = new HashMap<>(graph.branches);
            this.nodeToBranchMap = new HashMap<>(graph.nodeToBranchMap);
            this.graphAge = System.currentTimeMillis() - graph.startTime;
            this.nodeCount = graph.nodes.size();
        }
    }
}

