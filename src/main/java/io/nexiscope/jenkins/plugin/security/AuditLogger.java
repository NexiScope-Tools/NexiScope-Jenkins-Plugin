package io.nexiscope.jenkins.plugin.security;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Audit logger for tracking security-relevant events.
 * 
 * Features:
 * - Logs security-sensitive operations
 * - Includes timestamp, user, operation, and outcome
 * - In-memory circular buffer for recent events
 * - Thread-safe implementation
 * - Structured logging format
 * 
 * @author NexiScope Team
 */
public class AuditLogger {
    
    private static final Logger LOGGER = Logger.getLogger(AuditLogger.class.getName());
    
    // Circular buffer for recent audit events (last 1000 events)
    private static final int MAX_AUDIT_EVENTS = 1000;
    private static final ConcurrentLinkedQueue<AuditEvent> recentEvents = new ConcurrentLinkedQueue<>();
    
    // Date formatter for audit logs
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    
    /**
     * Audit event types.
     */
    public enum EventType {
        // Configuration events
        CONFIG_CHANGED("Configuration changed"),
        CONFIG_SAVED("Configuration saved"),
        CONFIG_LOADED("Configuration loaded"),
        
        // Connection events
        CONNECTION_TEST("Connection test"),
        CONNECTION_ESTABLISHED("Connection established"),
        CONNECTION_FAILED("Connection failed"),
        CONNECTION_CLOSED("Connection closed"),
        
        // Authentication events
        AUTH_SUCCESS("Authentication successful"),
        AUTH_FAILURE("Authentication failed"),
        AUTH_TOKEN_CHANGED("Authentication token changed"),
        
        // Rate limiting events
        RATE_LIMIT_EXCEEDED("Rate limit exceeded"),
        
        // Security events
        VALIDATION_FAILED("Input validation failed"),
        INVALID_REQUEST("Invalid request received"),
        
        // Plugin lifecycle
        PLUGIN_STARTED("Plugin started"),
        PLUGIN_STOPPED("Plugin stopped");
        
        private final String description;
        
        EventType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Audit event outcome.
     */
    public enum Outcome {
        SUCCESS,
        FAILURE,
        WARNING
    }
    
    /**
     * Logs an audit event.
     * 
     * @param eventType The type of event
     * @param outcome The outcome of the event
     * @param user The user who performed the action (or "system")
     * @param details Additional details about the event
     */
    public static void log(EventType eventType, Outcome outcome, String user, String details) {
        AuditEvent event = new AuditEvent(eventType, outcome, user, details);
        
        // Add to circular buffer
        recentEvents.offer(event);
        if (recentEvents.size() > MAX_AUDIT_EVENTS) {
            recentEvents.poll(); // Remove oldest
        }
        
        // Log to Jenkins logger
        String logMessage = formatAuditEvent(event);
        switch (outcome) {
            case SUCCESS:
                LOGGER.info("[AUDIT] " + logMessage);
                break;
            case WARNING:
                LOGGER.warning("[AUDIT] " + logMessage);
                break;
            case FAILURE:
                LOGGER.severe("[AUDIT] " + logMessage);
                break;
        }
    }
    
    /**
     * Logs a successful audit event.
     * 
     * @param eventType The type of event
     * @param user The user who performed the action
     * @param details Additional details
     */
    public static void logSuccess(EventType eventType, String user, String details) {
        log(eventType, Outcome.SUCCESS, user, details);
    }
    
    /**
     * Logs a failed audit event.
     * 
     * @param eventType The type of event
     * @param user The user who performed the action
     * @param details Additional details
     */
    public static void logFailure(EventType eventType, String user, String details) {
        log(eventType, Outcome.FAILURE, user, details);
    }
    
    /**
     * Logs a warning audit event.
     * 
     * @param eventType The type of event
     * @param user The user who performed the action
     * @param details Additional details
     */
    public static void logWarning(EventType eventType, String user, String details) {
        log(eventType, Outcome.WARNING, user, details);
    }
    
    /**
     * Gets recent audit events.
     * 
     * @param limit Maximum number of events to return (0 = all)
     * @return List of recent audit events
     */
    public static List<AuditEvent> getRecentEvents(int limit) {
        List<AuditEvent> events = new ArrayList<>(recentEvents);
        if (limit > 0 && events.size() > limit) {
            return events.subList(events.size() - limit, events.size());
        }
        return events;
    }
    
    /**
     * Gets recent audit events of a specific type.
     * 
     * @param eventType The event type to filter by
     * @param limit Maximum number of events to return (0 = all)
     * @return List of matching audit events
     */
    public static List<AuditEvent> getRecentEventsByType(EventType eventType, int limit) {
        List<AuditEvent> filtered = new ArrayList<>();
        for (AuditEvent event : recentEvents) {
            if (event.eventType == eventType) {
                filtered.add(event);
                if (limit > 0 && filtered.size() >= limit) {
                    break;
                }
            }
        }
        return filtered;
    }
    
    /**
     * Gets recent audit events for a specific user.
     * 
     * @param user The user to filter by
     * @param limit Maximum number of events to return (0 = all)
     * @return List of matching audit events
     */
    public static List<AuditEvent> getRecentEventsByUser(String user, int limit) {
        List<AuditEvent> filtered = new ArrayList<>();
        for (AuditEvent event : recentEvents) {
            if (event.user.equals(user)) {
                filtered.add(event);
                if (limit > 0 && filtered.size() >= limit) {
                    break;
                }
            }
        }
        return filtered;
    }
    
    /**
     * Clears all audit events (for testing purposes).
     */
    public static void clear() {
        recentEvents.clear();
        LOGGER.info("[AUDIT] Audit log cleared");
    }
    
    /**
     * Gets audit statistics.
     * 
     * @return AuditStats object
     */
    public static AuditStats getStats() {
        int total = recentEvents.size();
        int successes = 0;
        int failures = 0;
        int warnings = 0;
        
        for (AuditEvent event : recentEvents) {
            switch (event.outcome) {
                case SUCCESS:
                    successes++;
                    break;
                case FAILURE:
                    failures++;
                    break;
                case WARNING:
                    warnings++;
                    break;
            }
        }
        
        return new AuditStats(total, successes, failures, warnings);
    }
    
    /**
     * Formats an audit event for logging.
     * 
     * @param event The audit event
     * @return Formatted string
     */
    private static String formatAuditEvent(AuditEvent event) {
        return String.format(
            "%s | %s | %s | User: %s | %s",
            FORMATTER.format(event.timestamp),
            event.eventType.name(),
            event.outcome.name(),
            event.user,
            event.details != null ? event.details : ""
        );
    }
    
    /**
     * Audit event data class.
     */
    public static class AuditEvent {
        public final Instant timestamp;
        public final EventType eventType;
        public final Outcome outcome;
        public final String user;
        public final String details;
        
        public AuditEvent(EventType eventType, Outcome outcome, String user, String details) {
            this.timestamp = Instant.now();
            this.eventType = eventType;
            this.outcome = outcome;
            this.user = user != null ? user : "system";
            this.details = details;
        }
        
        @Override
        public String toString() {
            return formatAuditEvent(this);
        }
    }
    
    /**
     * Audit statistics data class.
     */
    public static class AuditStats {
        public final int totalEvents;
        public final int successCount;
        public final int failureCount;
        public final int warningCount;
        
        public AuditStats(int totalEvents, int successCount, int failureCount, int warningCount) {
            this.totalEvents = totalEvents;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.warningCount = warningCount;
        }
        
        public double getSuccessRate() {
            return totalEvents > 0 ? (double) successCount / totalEvents * 100.0 : 0.0;
        }
        
        public double getFailureRate() {
            return totalEvents > 0 ? (double) failureCount / totalEvents * 100.0 : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "AuditStats{total=%d, success=%d (%.1f%%), failure=%d (%.1f%%), warning=%d}",
                totalEvents, successCount, getSuccessRate(), 
                failureCount, getFailureRate(), warningCount
            );
        }
    }
}

