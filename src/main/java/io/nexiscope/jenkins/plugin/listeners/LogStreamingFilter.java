package io.nexiscope.jenkins.plugin.listeners;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Console log filter that captures and streams log lines to NexiScope.
 * 
 * Captures:
 * - Log lines with timestamps
 * - Associates logs with stages/nodes
 * - Filters by log level (INFO, WARN, ERROR, DEBUG)
 * - Buffers logs for high-volume pipelines
 * 
 * @author NexiScope Team
 */
@Extension
public class LogStreamingFilter extends ConsoleLogFilter implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(LogStreamingFilter.class.getName());
    
    // Log level patterns
    private static final Pattern ERROR_PATTERN = Pattern.compile("(?i)(error|exception|failed|failure|fatal)");
    private static final Pattern WARN_PATTERN = Pattern.compile("(?i)(warn|warning)");
    private static final Pattern DEBUG_PATTERN = Pattern.compile("(?i)(debug|trace)");
    private static final Pattern INFO_PATTERN = Pattern.compile("(?i)(info|information)");
    
    // Track current node for log association
    private static final ThreadLocal<FlowNode> currentFlowNode = new ThreadLocal<>();
    
    // Track log line counts per build to respect maxLogLines limit
    private static final Map<String, Integer> buildLogLineCounts = new ConcurrentHashMap<>();
    
    /**
     * Sets the current flow node for log association.
     * Called by FlowNodeEventListener when processing nodes.
     */
    public static void setCurrentFlowNode(FlowNode node) {
        currentFlowNode.set(node);
    }
    
    /**
     * Clears the current flow node.
     */
    public static void clearCurrentFlowNode() {
        currentFlowNode.remove();
    }
    
    /**
     * Clears the log line count for a build.
     */
    public static void clearBuildLogCount(String buildKey) {
        buildLogLineCounts.remove(buildKey);
    }
    
    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) throws IOException, InterruptedException {
        if (!isEnabled()) {
            return logger;
        }
        
        if (!(build instanceof WorkflowRun)) {
            return logger; // Only support WorkflowRun for now
        }
        
        WorkflowRun workflowRun = (WorkflowRun) build;
        
        return new LogStreamingOutputStream(logger, workflowRun);
    }
    
    /**
     * Checks if log streaming is enabled.
     */
    private boolean isEnabled() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config == null || !config.isEnabled() || !config.isValid()) {
            return false;
        }
        
        // Check if log streaming is enabled in configuration
        return config.isLogStreamingEnabled();
    }
    
    /**
     * OutputStream that captures log lines and streams them to NexiScope.
     */
    private static class LogStreamingOutputStream extends OutputStream {
        
        private final OutputStream delegate;
        private final WorkflowRun run;
        private final StringBuilder lineBuffer = new StringBuilder();
        private long lastFlushTime = System.currentTimeMillis();
        private static final long FLUSH_INTERVAL_MS = 1000; // Flush every second
        private int bufferedLines = 0;
        private static final int MAX_BUFFERED_LINES = 100; // Buffer up to 100 lines
        
        public LogStreamingOutputStream(OutputStream delegate, WorkflowRun run) {
            this.delegate = delegate;
            this.run = run;
        }
        
        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            
            if (b == '\n') {
                // End of line - process the buffered line
                String line = lineBuffer.toString();
                lineBuffer.setLength(0);
                
                if (!line.trim().isEmpty()) {
                    processLogLine(line);
                }
            } else if (b != '\r') {
                // Append to line buffer (ignore carriage returns)
                lineBuffer.append((char) b);
            }
        }
        
        @Override
        public void flush() throws IOException {
            delegate.flush();
            
            // Flush buffered lines periodically
            long now = System.currentTimeMillis();
            if (now - lastFlushTime > FLUSH_INTERVAL_MS && bufferedLines > 0) {
                flushBufferedLogs();
                lastFlushTime = now;
            }
        }
        
        @Override
        public void close() throws IOException {
            // Flush any remaining buffered lines
            if (lineBuffer.length() > 0) {
                String line = lineBuffer.toString();
                lineBuffer.setLength(0);
                if (!line.trim().isEmpty()) {
                    processLogLine(line);
                }
            }
            flushBufferedLogs();
            delegate.close();
        }
        
        /**
         * Processes a log line and sends it to NexiScope if it matches filters.
         */
        private void processLogLine(String line) {
            try {
                // Check if log level filtering is enabled
                NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
                if (config == null) {
                    return;
                }
                
                // Check max log lines limit per build
                String buildKey = run.getFullDisplayName() + "#" + run.getNumber();
                int currentCount = buildLogLineCounts.getOrDefault(buildKey, 0);
                int maxLines = config.getMaxLogLines();
                
                if (currentCount >= maxLines) {
                    // Limit reached, skip this line
                    return;
                }
                
                String logLevelFilter = config.getLogLevelFilter();
                if (logLevelFilter != null && !logLevelFilter.isEmpty()) {
                    if (!matchesLogLevel(line, logLevelFilter)) {
                        return; // Skip this line if it doesn't match filter
                    }
                }
                
                // Get current flow node for association
                FlowNode currentNode = currentFlowNode.get();
                
                // Create and send log event
                String event = EventMapper.createLogLineEvent(run, line, currentNode);
                WebSocketClient.getInstance().sendEvent(event);
                
                // Increment log line count for this build
                buildLogLineCounts.put(buildKey, currentCount + 1);
                
                bufferedLines++;
                
                // Flush if buffer is full
                if (bufferedLines >= MAX_BUFFERED_LINES) {
                    flushBufferedLogs();
                }
                
            } catch (Exception e) {
                // Don't let log streaming errors break the build
                LOGGER.fine("Failed to stream log line: " + e.getMessage());
            }
        }
        
        /**
         * Checks if a log line matches the specified log level filter.
         */
        private boolean matchesLogLevel(String line, String filter) {
            String upperLine = line.toUpperCase();
            
            switch (filter.toUpperCase()) {
                case "ERROR":
                    return ERROR_PATTERN.matcher(line).find();
                case "WARN":
                    return ERROR_PATTERN.matcher(line).find() || WARN_PATTERN.matcher(line).find();
                case "INFO":
                    return ERROR_PATTERN.matcher(line).find() || 
                           WARN_PATTERN.matcher(line).find() || 
                           INFO_PATTERN.matcher(line).find();
                case "DEBUG":
                    return true; // DEBUG includes all levels
                default:
                    return true; // Default: include all if filter is invalid
            }
        }
        
        /**
         * Flushes buffered logs (placeholder for future batching).
         */
        private void flushBufferedLogs() {
            bufferedLines = 0;
            // Future: Could batch multiple log lines into a single event
        }
    }
}

