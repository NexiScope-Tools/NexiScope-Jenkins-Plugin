package io.nexiscope.jenkins.plugin.errors;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized error handling for the NexiScope plugin.
 * 
 * Provides error categorization, user-friendly error messages, error metrics,
 * and graceful degradation strategies.
 * 
 * @author NexiScope Team
 */
public class ErrorHandler {
    
    private static final Logger LOGGER = Logger.getLogger(ErrorHandler.class.getName());
    
    // Error metrics
    private static final AtomicLong connectionErrors = new AtomicLong(0);
    private static final AtomicLong authenticationErrors = new AtomicLong(0);
    private static final AtomicLong eventSendingErrors = new AtomicLong(0);
    private static final AtomicLong configurationErrors = new AtomicLong(0);
    private static final AtomicLong unknownErrors = new AtomicLong(0);
    
    /**
     * Error categories for better error handling and reporting.
     */
    public enum ErrorCategory {
        CONNECTION("Connection Error", "Unable to connect to NexiScope platform"),
        AUTHENTICATION("Authentication Error", "Failed to authenticate with NexiScope platform"),
        EVENT_SENDING("Event Sending Error", "Failed to send event to NexiScope platform"),
        CONFIGURATION("Configuration Error", "Invalid or missing configuration"),
        NETWORK("Network Error", "Network-related error occurred"),
        SERVER("Server Error", "Error received from NexiScope server"),
        UNKNOWN("Unknown Error", "An unexpected error occurred");
        
        private final String displayName;
        private final String description;
        
        ErrorCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Error severity levels.
     */
    public enum ErrorSeverity {
        LOW("Low", "Non-critical error, operation can continue"),
        MEDIUM("Medium", "Error may affect functionality but operation can continue"),
        HIGH("High", "Error significantly affects functionality"),
        CRITICAL("Critical", "Error prevents core functionality from working");
        
        private final String displayName;
        private final String description;
        
        ErrorSeverity(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Handles an error with automatic categorization.
     * 
     * @param error The exception/error to handle
     * @param context Additional context about where the error occurred
     * @return ErrorInfo containing error details
     */
    public static ErrorInfo handleError(Throwable error, String context) {
        ErrorCategory category = categorizeError(error);
        ErrorSeverity severity = determineSeverity(category, error);
        
        // Update metrics
        updateMetrics(category);
        
        // Create user-friendly error message
        String userMessage = createUserFriendlyMessage(category, error, context);
        
        // Log error with appropriate level
        logError(category, severity, error, context, userMessage);
        
        return new ErrorInfo(category, severity, error, userMessage, context);
    }
    
    /**
     * Handles an error with explicit category.
     */
    public static ErrorInfo handleError(ErrorCategory category, Throwable error, String context) {
        ErrorSeverity severity = determineSeverity(category, error);
        updateMetrics(category);
        String userMessage = createUserFriendlyMessage(category, error, context);
        logError(category, severity, error, context, userMessage);
        return new ErrorInfo(category, severity, error, userMessage, context);
    }
    
    /**
     * Handles an error message (no exception).
     */
    public static ErrorInfo handleError(ErrorCategory category, String message, String context) {
        ErrorSeverity severity = determineSeverity(category, null);
        updateMetrics(category);
        String userMessage = createUserFriendlyMessage(category, message, context);
        logError(category, severity, null, context, userMessage);
        return new ErrorInfo(category, severity, null, userMessage, context);
    }
    
    /**
     * Categorizes an error based on its type and message.
     */
    private static ErrorCategory categorizeError(Throwable error) {
        if (error == null) {
            return ErrorCategory.UNKNOWN;
        }
        
        String errorMessage = error.getMessage();
        String errorClass = error.getClass().getName();
        
        // Connection errors
        if (error instanceof java.net.ConnectException ||
            error instanceof java.net.UnknownHostException ||
            error instanceof javax.net.ssl.SSLException ||
            (errorMessage != null && (
                errorMessage.contains("Connection refused") ||
                errorMessage.contains("Connection timed out") ||
                errorMessage.contains("Unable to connect") ||
                errorMessage.contains("Network is unreachable")
            ))) {
            return ErrorCategory.CONNECTION;
        }
        
        // Authentication errors
        if (errorMessage != null && (
            errorMessage.contains("Authentication failed") ||
            errorMessage.contains("Invalid token") ||
            errorMessage.contains("Unauthorized") ||
            errorMessage.contains("AUTH_FAILED")
        )) {
            return ErrorCategory.AUTHENTICATION;
        }
        
        // Network errors
        if (error instanceof java.io.IOException ||
            error instanceof java.net.SocketTimeoutException ||
            (errorMessage != null && (
                errorMessage.contains("timeout") ||
                errorMessage.contains("network")
            ))) {
            return ErrorCategory.NETWORK;
        }
        
        // Server errors
        if (errorMessage != null && (
            errorMessage.contains("Server error") ||
            errorMessage.contains("500") ||
            errorMessage.contains("502") ||
            errorMessage.contains("503")
        )) {
            return ErrorCategory.SERVER;
        }
        
        // Configuration errors
        if (errorMessage != null && (
            errorMessage.contains("Configuration") ||
            errorMessage.contains("Invalid configuration") ||
            errorMessage.contains("Missing configuration")
        )) {
            return ErrorCategory.CONFIGURATION;
        }
        
        return ErrorCategory.UNKNOWN;
    }
    
    /**
     * Determines error severity based on category and error details.
     */
    private static ErrorSeverity determineSeverity(ErrorCategory category, Throwable error) {
        switch (category) {
            case CONNECTION:
            case AUTHENTICATION:
                return ErrorSeverity.HIGH;
            case CONFIGURATION:
                return ErrorSeverity.CRITICAL;
            case EVENT_SENDING:
                return ErrorSeverity.MEDIUM;
            case NETWORK:
            case SERVER:
                return ErrorSeverity.MEDIUM;
            default:
                return ErrorSeverity.MEDIUM;
        }
    }
    
    /**
     * Creates a user-friendly error message with actionable guidance.
     */
    private static String createUserFriendlyMessage(ErrorCategory category, Throwable error, String context) {
        String baseMessage = category.getDescription();
        String errorDetails = error != null ? error.getMessage() : "Unknown error";
        
        StringBuilder message = new StringBuilder(baseMessage);
        
        if (context != null && !context.isEmpty()) {
            message.append(" (").append(context).append(")");
        }
        
        // Add specific guidance based on error category
        switch (category) {
            case CONNECTION:
                message.append(". Please check:");
                message.append(" 1) NexiScope platform URL is correct");
                message.append(" 2) Network connectivity is available");
                message.append(" 3) Firewall/proxy settings allow WebSocket connections");
                break;
                
            case AUTHENTICATION:
                message.append(". Please check:");
                message.append(" 1) Authentication token is correct");
                message.append(" 2) Token has not expired");
                message.append(" 3) Instance ID is correct");
                break;
                
            case CONFIGURATION:
                message.append(". Please check:");
                message.append(" 1) All required configuration fields are filled");
                message.append(" 2) URL format is correct (ws:// or wss://)");
                message.append(" 3) Configuration values are valid");
                break;
                
            case EVENT_SENDING:
                message.append(". Event has been queued and will be sent when connection is restored.");
                break;
                
            case NETWORK:
                message.append(". Network issue detected. The plugin will automatically retry.");
                break;
                
            case SERVER:
                message.append(". Server error received. The plugin will automatically retry.");
                break;
        }
        
        // Add error details for debugging (but keep it concise)
        if (errorDetails != null && !errorDetails.isEmpty() && errorDetails.length() < 200) {
            message.append(" Error: ").append(errorDetails);
        }
        
        return message.toString();
    }
    
    /**
     * Creates a user-friendly error message from a string message.
     */
    private static String createUserFriendlyMessage(ErrorCategory category, String message, String context) {
        String baseMessage = category.getDescription();
        StringBuilder userMessage = new StringBuilder(baseMessage);
        
        if (context != null && !context.isEmpty()) {
            userMessage.append(" (").append(context).append(")");
        }
        
        if (message != null && !message.isEmpty()) {
            userMessage.append(": ").append(message);
        }
        
        return userMessage.toString();
    }
    
    /**
     * Logs error with appropriate level based on severity.
     */
    private static void logError(ErrorCategory category, ErrorSeverity severity, Throwable error, 
                                  String context, String userMessage) {
        Level logLevel = getLogLevel(severity);
        String logMessage = String.format("[%s] %s", category.getDisplayName(), userMessage);
        
        if (error != null) {
            LOGGER.log(logLevel, logMessage, error);
        } else {
            LOGGER.log(logLevel, logMessage);
        }
    }
    
    /**
     * Gets appropriate log level based on severity.
     */
    private static Level getLogLevel(ErrorSeverity severity) {
        switch (severity) {
            case CRITICAL:
            case HIGH:
                return Level.SEVERE;
            case MEDIUM:
                return Level.WARNING;
            case LOW:
                return Level.INFO;
            default:
                return Level.WARNING;
        }
    }
    
    /**
     * Updates error metrics.
     */
    private static void updateMetrics(ErrorCategory category) {
        switch (category) {
            case CONNECTION:
                connectionErrors.incrementAndGet();
                break;
            case AUTHENTICATION:
                authenticationErrors.incrementAndGet();
                break;
            case EVENT_SENDING:
                eventSendingErrors.incrementAndGet();
                break;
            case CONFIGURATION:
                configurationErrors.incrementAndGet();
                break;
            default:
                unknownErrors.incrementAndGet();
                break;
        }
    }
    
    /**
     * Gets error statistics.
     */
    public static ErrorStatistics getStatistics() {
        return new ErrorStatistics(
            connectionErrors.get(),
            authenticationErrors.get(),
            eventSendingErrors.get(),
            configurationErrors.get(),
            unknownErrors.get()
        );
    }
    
    /**
     * Resets error statistics.
     */
    public static void resetStatistics() {
        connectionErrors.set(0);
        authenticationErrors.set(0);
        eventSendingErrors.set(0);
        configurationErrors.set(0);
        unknownErrors.set(0);
    }
    
    /**
     * Error information container.
     */
    public static class ErrorInfo {
        private final ErrorCategory category;
        private final ErrorSeverity severity;
        private final Throwable error;
        private final String userMessage;
        private final String context;
        
        public ErrorInfo(ErrorCategory category, ErrorSeverity severity, Throwable error, 
                        String userMessage, String context) {
            this.category = category;
            this.severity = severity;
            this.error = error;
            this.userMessage = userMessage;
            this.context = context;
        }
        
        public ErrorCategory getCategory() {
            return category;
        }
        
        public ErrorSeverity getSeverity() {
            return severity;
        }
        
        public Throwable getError() {
            return error;
        }
        
        public String getUserMessage() {
            return userMessage;
        }
        
        public String getContext() {
            return context;
        }
        
        public boolean isRetryable() {
            return category == ErrorCategory.CONNECTION ||
                   category == ErrorCategory.NETWORK ||
                   category == ErrorCategory.SERVER ||
                   category == ErrorCategory.EVENT_SENDING;
        }
    }
    
    /**
     * Error statistics container.
     */
    public static class ErrorStatistics {
        private final long connectionErrors;
        private final long authenticationErrors;
        private final long eventSendingErrors;
        private final long configurationErrors;
        private final long unknownErrors;
        
        public ErrorStatistics(long connectionErrors, long authenticationErrors, 
                              long eventSendingErrors, long configurationErrors, 
                              long unknownErrors) {
            this.connectionErrors = connectionErrors;
            this.authenticationErrors = authenticationErrors;
            this.eventSendingErrors = eventSendingErrors;
            this.configurationErrors = configurationErrors;
            this.unknownErrors = unknownErrors;
        }
        
        public long getTotalErrors() {
            return connectionErrors + authenticationErrors + eventSendingErrors + 
                   configurationErrors + unknownErrors;
        }
        
        public long getConnectionErrors() {
            return connectionErrors;
        }
        
        public long getAuthenticationErrors() {
            return authenticationErrors;
        }
        
        public long getEventSendingErrors() {
            return eventSendingErrors;
        }
        
        public long getConfigurationErrors() {
            return configurationErrors;
        }
        
        public long getUnknownErrors() {
            return unknownErrors;
        }
    }
}

