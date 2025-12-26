package io.nexiscope.jenkins.plugin.steps;

import hudson.Extension;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Pipeline step for sending custom events to NexiScope platform.
 * 
 * <p>This step allows pipeline authors to send custom events with arbitrary data
 * to the NexiScope platform for tracking application-specific metrics, milestones,
 * or business events.</p>
 * 
 * <h2>Usage in Jenkinsfile:</h2>
 * <pre>
 * // Simple event
 * nexiscopeSendEvent(eventType: 'DEPLOYMENT_STARTED')
 * 
 * // Event with custom data
 * nexiscopeSendEvent(
 *   eventType: 'DEPLOYMENT_COMPLETED',
 *   data: [
 *     environment: 'production',
 *     version: '1.2.3',
 *     duration: 120
 *   ]
 * )
 * </pre>
 * 
 * <h2>Event Type Naming:</h2>
 * <ul>
 *   <li>Event types are automatically prefixed with {@code CUSTOM_} if not already present</li>
 *   <li>Must contain only uppercase letters, numbers, and underscores</li>
 *   <li>Examples: {@code DEPLOYMENT_STARTED}, {@code TEST_PASSED}, {@code QUALITY_GATE}</li>
 * </ul>
 * 
 * <h2>Rate Limiting:</h2>
 * <p>To prevent abuse, custom events are rate-limited to 100 events per build
 * within a 1-minute window. Exceeding this limit will cause events to be dropped
 * with a warning logged.</p>
 * 
 * <h2>Data Format:</h2>
 * <p>The {@code data} parameter accepts a map of key-value pairs. Values should be
 * serializable types (String, Number, Boolean). Complex objects will be converted
 * to strings.</p>
 * 
 * @author NexiScope Team
 * @since 1.0.0
 * @see EventMapper#createCustomEvent(WorkflowRun, String, Map)
 */
public class SendCustomEventStep extends Step implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private static final Logger LOGGER = Logger.getLogger(SendCustomEventStep.class.getName());
    
    // Rate limiting: track events per build
    private static final Map<String, RateLimitTracker> rateLimitTrackers = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_EVENTS_PER_BUILD = 100;
    private static final long RATE_LIMIT_WINDOW_MS = 60000; // 1 minute
    
    private String eventType;
    private Map<String, Object> data;
    
    /**
     * Creates a new custom event step.
     * 
     * @param eventType the type of event to send (required, will be prefixed with CUSTOM_ if needed)
     */
    @DataBoundConstructor
    public SendCustomEventStep(String eventType) {
        this.eventType = eventType;
    }
    
    /**
     * Gets the event type.
     * 
     * @return the event type
     */
    public String getEventType() {
        return eventType;
    }
    
    /**
     * Sets the custom data to include with the event.
     * 
     * @param data a map of key-value pairs to include in the event
     */
    @DataBoundSetter
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    /**
     * Gets the custom data.
     * 
     * @return the custom data map, or an empty map if no data was set
     */
    public Map<String, Object> getData() {
        return data != null ? data : Collections.emptyMap();
    }
    
    /**
     * Starts the step execution.
     * 
     * @param context the step execution context
     * @return a new execution instance
     * @throws Exception if execution cannot be started
     */
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }
    
    /**
     * Execution of the step.
     */
    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        
        private static final long serialVersionUID = 1L;
        
        // Store only serializable fields instead of the entire step object
        private final String eventType;
        private final Map<String, Object> data;
        
        Execution(SendCustomEventStep step, StepContext context) {
            super(context);
            // Extract only the fields we need (which are serializable)
            this.eventType = step.eventType;
            this.data = step.data != null ? step.data : Collections.emptyMap();
        }
        
        @Override
        protected Void run() throws Exception {
            // Check if plugin is enabled
            NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
            if (config == null || !config.isEnabled() || !config.isValid()) {
                LOGGER.warning("NexiScope plugin is not enabled or configured. Custom event not sent.");
                return null;
            }
            
            // Get current build
            WorkflowRun run = getContext().get(WorkflowRun.class);
            if (run == null) {
                LOGGER.warning("Could not get WorkflowRun from context. Custom event not sent.");
                return null;
            }
            
            // Check rate limiting
            String buildKey = run.getFullDisplayName() + "#" + run.getNumber();
            if (!checkRateLimit(buildKey)) {
                LOGGER.warning("Rate limit exceeded for custom events in build: " + buildKey);
                return null;
            }
            
            // Validate event type
            if (eventType == null || eventType.trim().isEmpty()) {
                throw new IllegalArgumentException("Event type is required");
            }
            
            // Validate event type format (should start with CUSTOM_ or be alphanumeric)
            String sanitizedEventType = eventType.trim().toUpperCase();
            if (!sanitizedEventType.matches("^[A-Z0-9_]+$")) {
                throw new IllegalArgumentException("Event type must contain only uppercase letters, numbers, and underscores");
            }
            
            // Ensure event type starts with CUSTOM_ prefix
            if (!sanitizedEventType.startsWith("CUSTOM_")) {
                sanitizedEventType = "CUSTOM_" + sanitizedEventType;
            }
            
            // Create and send custom event
            try {
                String event = EventMapper.createCustomEvent(run, sanitizedEventType, data);
                WebSocketClient.getInstance().sendEvent(event);
                LOGGER.fine("Custom event sent: " + sanitizedEventType + " for build: " + buildKey);
            } catch (Exception e) {
                LOGGER.warning("Failed to send custom event: " + e.getMessage());
                throw new RuntimeException("Failed to send custom event to NexiScope: " + e.getMessage(), e);
            }
            
            return null;
        }
        
        /**
         * Checks rate limiting for custom events.
         */
        private boolean checkRateLimit(String buildKey) {
            RateLimitTracker tracker = rateLimitTrackers.computeIfAbsent(buildKey, k -> new RateLimitTracker());
            
            long now = System.currentTimeMillis();
            
            // Reset if window expired
            if (now - tracker.windowStart > RATE_LIMIT_WINDOW_MS) {
                tracker.windowStart = now;
                tracker.eventCount.set(0);
            }
            
            // Check limit
            int maxEvents = getMaxEventsPerBuild();
            if (tracker.eventCount.get() >= maxEvents) {
                return false;
            }
            
            // Increment counter
            tracker.eventCount.incrementAndGet();
            return true;
        }
        
        /**
         * Gets maximum events per build from configuration.
         */
        private int getMaxEventsPerBuild() {
            // Could be made configurable in the future
            return DEFAULT_MAX_EVENTS_PER_BUILD;
        }
    }
    
    /**
     * Rate limit tracker for a build.
     */
    private static class RateLimitTracker {
        long windowStart = System.currentTimeMillis();
        AtomicInteger eventCount = new AtomicInteger(0);
    }
    
    /**
     * Descriptor for the custom event step.
     * 
     * <p>Provides metadata about the step for Jenkins, including the function name
     * used in pipelines and the required execution context.</p>
     */
    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        
        /**
         * Gets the function name used in Jenkinsfiles.
         * 
         * @return the function name "nexiscopeSendEvent"
         */
        @Override
        public String getFunctionName() {
            return "nexiscopeSendEvent";
        }
        
        /**
         * Gets the human-readable display name for the step.
         * 
         * @return the display name
         */
        @Override
        public String getDisplayName() {
            return "Send custom event to NexiScope";
        }
        
        /**
         * Gets the required execution context for this step.
         * 
         * @return a set containing {@link WorkflowRun} class
         */
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(WorkflowRun.class);
        }
        
        /**
         * Indicates whether this step takes an implicit block argument.
         * 
         * @return false, as this step does not take a block
         */
        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }
    }
}

