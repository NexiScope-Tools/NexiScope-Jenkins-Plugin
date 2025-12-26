package io.nexiscope.jenkins.plugin.errors;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized error messages with helpful context and actionable suggestions.
 * 
 * <p>This class provides a comprehensive catalog of error messages for the NexiScope
 * plugin. Each error includes a clear description, actionable suggestions for resolution,
 * and links to relevant documentation.</p>
 * 
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li><b>User-Friendly</b>: Clear, non-technical language where possible</li>
 *   <li><b>Actionable</b>: Specific steps users can take to resolve the issue</li>
 *   <li><b>Contextual</b>: Relevant information about why the error occurred</li>
 *   <li><b>Educational</b>: Links to documentation for deeper understanding</li>
 * </ul>
 * 
 * <h2>Error Categories:</h2>
 * <ul>
 *   <li><b>Connection Errors</b>: Network connectivity, timeouts, SSL issues</li>
 *   <li><b>Authentication Errors</b>: Token validation, permission issues</li>
 *   <li><b>Configuration Errors</b>: Invalid URLs, missing fields, format errors</li>
 *   <li><b>Event Errors</b>: Event sending failures, rate limits, validation</li>
 *   <li><b>System Errors</b>: Memory issues, internal errors, unexpected failures</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>
 * String errorMsg = ErrorMessages.get(ErrorType.CONNECTION_FAILED, 
 *     Map.of("url", platformUrl, "error", e.getMessage()));
 * return FormValidation.error(errorMsg);
 * </pre>
 * 
 * @author NexiScope Team
 * @since 1.0.0
 * @see ErrorHandler
 */
public class ErrorMessages {
    
    // Documentation base URL
    private static final String DOCS_BASE_URL = "https://docs.nexiscope.com/jenkins-plugin";
    
    /**
     * Error message templates with suggestions.
     */
    public enum ErrorType {
        // Connection errors
        CONNECTION_FAILED(
            "Failed to connect to NexiScope platform",
            "Check that:\n" +
            "1. The platform URL is correct and accessible\n" +
            "2. Your network allows outbound connections\n" +
            "3. The NexiScope platform is online\n" +
            "4. Firewall rules allow WebSocket connections",
            DOCS_BASE_URL + "/troubleshooting#connection-failed"
        ),
        
        CONNECTION_TIMEOUT(
            "Connection to NexiScope platform timed out",
            "This usually means:\n" +
            "1. The platform URL is unreachable\n" +
            "2. Network latency is too high\n" +
            "3. A firewall is blocking the connection\n" +
            "Try:\n" +
            "- Verify the URL is correct\n" +
            "- Check your network connectivity\n" +
            "- Contact your network administrator",
            DOCS_BASE_URL + "/troubleshooting#connection-timeout"
        ),
        
        // Authentication errors
        AUTH_FAILED(
            "Authentication failed",
            "Possible causes:\n" +
            "1. Invalid authentication token\n" +
            "2. Token has expired\n" +
            "3. Token doesn't have required permissions\n" +
            "To fix:\n" +
            "- Generate a new token from NexiScope platform\n" +
            "- Verify the token has 'jenkins-integration' permission\n" +
            "- Update the token in Jenkins configuration",
            DOCS_BASE_URL + "/configuration#authentication"
        ),
        
        AUTH_TOKEN_INVALID(
            "Authentication token format is invalid",
            "The token must:\n" +
            "1. Be at least 16 characters long\n" +
            "2. Contain only alphanumeric characters, dots, underscores, and hyphens\n" +
            "3. Not contain spaces or special characters\n" +
            "Get a valid token from: NexiScope Platform → Settings → API Tokens",
            DOCS_BASE_URL + "/configuration#authentication-token"
        ),
        
        // Configuration errors
        CONFIG_INVALID_URL(
            "Platform URL is invalid",
            "The URL must:\n" +
            "1. Start with https:// or wss:// (recommended)\n" +
            "2. Include a valid hostname\n" +
            "3. Not contain dangerous characters\n" +
            "Example: https://api.nexiscope.com",
            DOCS_BASE_URL + "/configuration#platform-url"
        ),
        
        CONFIG_MISSING_REQUIRED(
            "Required configuration is missing",
            "Please provide:\n" +
            "1. Platform URL - The NexiScope platform endpoint\n" +
            "2. Authentication Token - Your API token\n" +
            "3. Instance ID - A unique identifier for this Jenkins instance\n" +
            "Go to: Manage Jenkins → NexiScope Integration",
            DOCS_BASE_URL + "/configuration"
        ),
        
        CONFIG_INVALID_INSTANCE_ID(
            "Instance ID is invalid",
            "The instance ID must:\n" +
            "1. Be 1-128 characters long\n" +
            "2. Contain only alphanumeric characters, dots, underscores, and hyphens\n" +
            "3. Be unique for this Jenkins instance\n" +
            "Example: jenkins-prod-01",
            DOCS_BASE_URL + "/configuration#instance-id"
        ),
        
        // Event errors
        EVENT_SEND_FAILED(
            "Failed to send event to NexiScope",
            "The event was queued for retry. This can happen when:\n" +
            "1. Connection is temporarily unavailable\n" +
            "2. Network is experiencing issues\n" +
            "3. Rate limit was exceeded\n" +
            "The plugin will automatically retry when connection is restored.",
            DOCS_BASE_URL + "/troubleshooting#event-send-failed"
        ),
        
        EVENT_QUEUE_FULL(
            "Event queue is full",
            "Too many events are queued. This means:\n" +
            "1. Connection has been down for a while\n" +
            "2. Events are being generated faster than they can be sent\n" +
            "To fix:\n" +
            "- Check connection status\n" +
            "- Increase queue size in configuration\n" +
            "- Enable event filtering to reduce volume",
            DOCS_BASE_URL + "/configuration#event-queue"
        ),
        
        EVENT_FILTERING_INVALID(
            "Event filtering configuration is invalid",
            "Check that:\n" +
            "1. Regex patterns are valid\n" +
            "2. Event type names are correct (uppercase with underscores)\n" +
            "3. No conflicting include/exclude patterns\n" +
            "Test your patterns at: https://regex101.com/",
            DOCS_BASE_URL + "/configuration#event-filtering"
        ),
        
        // Rate limiting errors
        RATE_LIMIT_EXCEEDED(
            "Rate limit exceeded",
            "You've made too many requests. Limits:\n" +
            "- Test Connection: 10 per minute\n" +
            "- Events: 10,000 per minute\n" +
            "- Config Save: 30 per minute\n" +
            "Please wait a moment before trying again.",
            DOCS_BASE_URL + "/security#rate-limiting"
        ),
        
        // Certificate pinning errors
        CERT_PINNING_FAILED(
            "Certificate pinning validation failed",
            "The server's certificate doesn't match the pinned certificate. This could mean:\n" +
            "1. The server certificate has changed\n" +
            "2. There's a man-in-the-middle attack\n" +
            "3. The pinned certificate is incorrect\n" +
            "To fix:\n" +
            "- Verify the server certificate is correct\n" +
            "- Update the pinned certificate in configuration\n" +
            "- Temporarily disable certificate pinning for testing",
            DOCS_BASE_URL + "/security#certificate-pinning"
        ),
        
        // Plugin errors
        PLUGIN_NOT_ENABLED(
            "NexiScope plugin is not enabled",
            "The plugin is currently disabled. To enable:\n" +
            "1. Go to: Manage Jenkins → NexiScope Integration\n" +
            "2. Check 'Enable Plugin'\n" +
            "3. Save configuration",
            DOCS_BASE_URL + "/getting-started#enabling-plugin"
        ),
        
        PLUGIN_INITIALIZATION_FAILED(
            "Plugin initialization failed",
            "The plugin failed to start. Check:\n" +
            "1. Jenkins logs for detailed error messages\n" +
            "2. Configuration is valid\n" +
            "3. Required dependencies are installed\n" +
            "4. Jenkins version is compatible (2.440.3+)",
            DOCS_BASE_URL + "/troubleshooting#initialization-failed"
        ),
        
        // General errors
        UNKNOWN_ERROR(
            "An unexpected error occurred",
            "An unknown error occurred. Please:\n" +
            "1. Check Jenkins logs for details\n" +
            "2. Verify your configuration\n" +
            "3. Try restarting Jenkins\n" +
            "4. Contact support if the issue persists",
            DOCS_BASE_URL + "/troubleshooting"
        );
        
        private final String message;
        private final String suggestion;
        private final String docUrl;
        
        ErrorType(String message, String suggestion, String docUrl) {
            this.message = message;
            this.suggestion = suggestion;
            this.docUrl = docUrl;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getSuggestion() {
            return suggestion;
        }
        
        public String getDocUrl() {
            return docUrl;
        }
    }
    
    /**
     * Gets a formatted error message with suggestions.
     * 
     * @param errorType The error type
     * @return Formatted error message
     */
    public static String getErrorMessage(ErrorType errorType) {
        return formatError(errorType, null, null);
    }
    
    /**
     * Gets a formatted error message with additional context.
     * 
     * @param errorType The error type
     * @param context Additional context (e.g., URL, token length)
     * @return Formatted error message
     */
    public static String getErrorMessage(ErrorType errorType, String context) {
        return formatError(errorType, context, null);
    }
    
    /**
     * Gets a formatted error message with context and exception.
     * 
     * @param errorType The error type
     * @param context Additional context
     * @param exception The exception that occurred
     * @return Formatted error message
     */
    public static String getErrorMessage(ErrorType errorType, String context, Throwable exception) {
        return formatError(errorType, context, exception);
    }
    
    /**
     * Formats an error message with all details.
     * 
     * @param errorType The error type
     * @param context Additional context
     * @param exception The exception
     * @return Formatted error message
     */
    private static String formatError(ErrorType errorType, String context, Throwable exception) {
        StringBuilder sb = new StringBuilder();
        
        // Error message
        sb.append("❌ ").append(errorType.getMessage());
        
        // Context
        if (context != null && !context.trim().isEmpty()) {
            sb.append("\n\n📝 Details:\n").append(context);
        }
        
        // Exception details
        if (exception != null) {
            sb.append("\n\n🔍 Technical Details:\n");
            sb.append(exception.getClass().getSimpleName()).append(": ");
            sb.append(exception.getMessage() != null ? exception.getMessage() : "No message");
        }
        
        // Suggestions
        sb.append("\n\n💡 How to Fix:\n").append(errorType.getSuggestion());
        
        // Documentation link
        sb.append("\n\n📚 Documentation:\n").append(errorType.getDocUrl());
        
        return sb.toString();
    }
    
    /**
     * Gets a short error message (without suggestions).
     * 
     * @param errorType The error type
     * @return Short error message
     */
    public static String getShortMessage(ErrorType errorType) {
        return errorType.getMessage();
    }
    
    /**
     * Gets a short error message with context.
     * 
     * @param errorType The error type
     * @param context Additional context
     * @return Short error message with context
     */
    public static String getShortMessage(ErrorType errorType, String context) {
        if (context != null && !context.trim().isEmpty()) {
            return errorType.getMessage() + ": " + context;
        }
        return errorType.getMessage();
    }
    
    /**
     * Gets HTML-formatted error message for UI display.
     * 
     * @param errorType The error type
     * @param context Additional context
     * @return HTML-formatted error message
     */
    public static String getHtmlMessage(ErrorType errorType, String context) {
        StringBuilder html = new StringBuilder();
        
        html.append("<div style='padding: 15px; border-left: 4px solid #dc3545; background: #f8d7da; color: #721c24;'>");
        html.append("<strong style='font-size: 16px;'>❌ ").append(errorType.getMessage()).append("</strong>");
        
        if (context != null && !context.trim().isEmpty()) {
            html.append("<div style='margin-top: 10px; font-size: 13px;'>");
            html.append("<strong>Details:</strong><br>");
            html.append(escapeHtml(context).replace("\n", "<br>"));
            html.append("</div>");
        }
        
        html.append("<div style='margin-top: 10px; font-size: 13px;'>");
        html.append("<strong>How to Fix:</strong><br>");
        html.append(escapeHtml(errorType.getSuggestion()).replace("\n", "<br>"));
        html.append("</div>");
        
        html.append("<div style='margin-top: 10px; font-size: 12px;'>");
        html.append("<a href='").append(errorType.getDocUrl()).append("' target='_blank' style='color: #004085;'>");
        html.append("📚 View Documentation →</a>");
        html.append("</div>");
        
        html.append("</div>");
        
        return html.toString();
    }
    
    /**
     * Escapes HTML special characters.
     * 
     * @param text The text to escape
     * @return Escaped text
     */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }
    
    /**
     * Creates a user-friendly error summary.
     * 
     * @param errorType The error type
     * @param context Additional context
     * @return Error summary
     */
    public static ErrorSummary createSummary(ErrorType errorType, String context) {
        return new ErrorSummary(errorType, context);
    }
    
    /**
     * Error summary data class.
     */
    public static class ErrorSummary {
        public final ErrorType errorType;
        public final String context;
        public final String message;
        public final String suggestion;
        public final String docUrl;
        public final long timestamp;
        
        public ErrorSummary(ErrorType errorType, String context) {
            this.errorType = errorType;
            this.context = context;
            this.message = errorType.getMessage();
            this.suggestion = errorType.getSuggestion();
            this.docUrl = errorType.getDocUrl();
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return getErrorMessage(errorType, context);
        }
        
        public String toShortString() {
            return getShortMessage(errorType, context);
        }
        
        public String toHtml() {
            return getHtmlMessage(errorType, context);
        }
    }
}

