package io.nexiscope.jenkins.plugin.security;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Comprehensive input validation utility for security-sensitive configuration fields.
 * 
 * <p>This class provides static validation methods for all user-provided inputs in the
 * NexiScope plugin configuration. It implements defense-in-depth security practices
 * including:</p>
 * 
 * <h2>Security Features:</h2>
 * <ul>
 *   <li><b>URL Validation</b>: Protocol whitelist (HTTPS/WSS preferred), length limits, dangerous character detection</li>
 *   <li><b>Token Validation</b>: Format checking, length requirements (16-512 chars), character whitelist</li>
 *   <li><b>Instance ID Validation</b>: Format checking, length limits (1-128 chars), alphanumeric enforcement</li>
 *   <li><b>XSS Prevention</b>: Sanitization of all text inputs to prevent cross-site scripting</li>
 *   <li><b>Regex Pattern Validation</b>: Syntax checking and complexity limits for user-provided patterns</li>
 * </ul>
 * 
 * <h2>Validation Levels:</h2>
 * <p>Validation results can be:</p>
 * <ul>
 *   <li><b>OK</b>: Input is valid and secure</li>
 *   <li><b>WARNING</b>: Input is valid but not recommended (e.g., HTTP instead of HTTPS)</li>
 *   <li><b>ERROR</b>: Input is invalid and must be corrected</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>
 * ValidationResult result = InputValidator.validatePlatformUrl("https://api.nexiscope.com");
 * if (result.isError()) {
 *     return FormValidation.error(result.getMessage());
 * }
 * </pre>
 * 
 * @author NexiScope Team
 * @since 1.0.0
 * @see ValidationResult
 * @see NexiScopeGlobalConfiguration
 */
public class InputValidator {
    
    private static final Logger LOGGER = Logger.getLogger(InputValidator.class.getName());
    
    // URL validation
    private static final Pattern VALID_PROTOCOLS = Pattern.compile("^(https?|wss?)://.*", Pattern.CASE_INSENSITIVE);
    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern DANGEROUS_URL_CHARS = Pattern.compile("[<>\"'`]");
    
    // Token validation
    private static final int MIN_TOKEN_LENGTH = 16;
    private static final int MAX_TOKEN_LENGTH = 512;
    private static final Pattern VALID_TOKEN_CHARS = Pattern.compile("^[A-Za-z0-9._\\-]+$");
    
    // Instance ID validation
    private static final int MIN_INSTANCE_ID_LENGTH = 1;
    private static final int MAX_INSTANCE_ID_LENGTH = 128;
    private static final Pattern VALID_INSTANCE_ID = Pattern.compile("^[A-Za-z0-9._\\-]+$");
    
    // Event type validation
    private static final Pattern VALID_EVENT_TYPE = Pattern.compile("^[A-Z_]+$");
    private static final int MAX_EVENT_TYPE_LENGTH = 64;
    
    // Job name validation
    private static final int MAX_JOB_NAME_LENGTH = 512;
    
    // Pattern validation
    private static final int MAX_PATTERN_LENGTH = 1024;
    
    /**
     * Validates a platform URL.
     * 
     * @param url The URL to validate
     * @return ValidationResult with status and error message
     */
    public static ValidationResult validatePlatformUrl(String url) {
        // Check null/empty
        if (url == null || url.trim().isEmpty()) {
            return ValidationResult.error("Platform URL is required");
        }
        
        String trimmedUrl = url.trim();
        
        // Check length
        if (trimmedUrl.length() > MAX_URL_LENGTH) {
            return ValidationResult.error("Platform URL is too long (max " + MAX_URL_LENGTH + " characters)");
        }
        
        // Check for dangerous characters (XSS prevention)
        if (DANGEROUS_URL_CHARS.matcher(trimmedUrl).find()) {
            return ValidationResult.error("Platform URL contains invalid characters");
        }
        
        // Check protocol
        if (!VALID_PROTOCOLS.matcher(trimmedUrl).matches()) {
            return ValidationResult.error("Platform URL must start with https://, wss://, http://, or ws://");
        }
        
        // Parse URL
        try {
            URL urlObj = new URL(trimmedUrl);
            
            // Validate protocol
            String protocol = urlObj.getProtocol().toLowerCase();
            if (!protocol.equals("https") && !protocol.equals("wss") && 
                !protocol.equals("http") && !protocol.equals("ws")) {
                return ValidationResult.error("Invalid URL protocol: " + protocol);
            }
            
            // Validate host
            String host = urlObj.getHost();
            if (host == null || host.isEmpty()) {
                return ValidationResult.error("Platform URL must include a hostname");
            }
            
            // Check for localhost/internal IPs in production (warning only)
            if (isInternalHost(host)) {
                return ValidationResult.warning(
                    "URL points to internal/localhost address. This is OK for testing but not recommended for production."
                );
            }
            
            // Recommend HTTPS/WSS for production
            if (protocol.equals("http") || protocol.equals("ws")) {
                return ValidationResult.warning(
                    "Using unencrypted protocol (" + protocol + "). Consider using https:// or wss:// for production."
                );
            }
            
        } catch (MalformedURLException e) {
            return ValidationResult.error("Invalid URL format: " + e.getMessage());
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * Validates an authentication token.
     * 
     * @param token The token to validate
     * @return ValidationResult with status and error message
     */
    public static ValidationResult validateAuthToken(String token) {
        // Check null/empty
        if (token == null || token.trim().isEmpty()) {
            return ValidationResult.error("Authentication token is required");
        }
        
        String trimmedToken = token.trim();
        
        // Check length
        if (trimmedToken.length() < MIN_TOKEN_LENGTH) {
            return ValidationResult.error(
                "Authentication token is too short (minimum " + MIN_TOKEN_LENGTH + " characters)"
            );
        }
        
        if (trimmedToken.length() > MAX_TOKEN_LENGTH) {
            return ValidationResult.error(
                "Authentication token is too long (maximum " + MAX_TOKEN_LENGTH + " characters)"
            );
        }
        
        // Check format (alphanumeric, dots, underscores, hyphens only)
        if (!VALID_TOKEN_CHARS.matcher(trimmedToken).matches()) {
            return ValidationResult.error(
                "Authentication token contains invalid characters. Only letters, numbers, dots, underscores, and hyphens are allowed."
            );
        }
        
        // Warn about weak tokens
        if (trimmedToken.length() < 32) {
            return ValidationResult.warning(
                "Token is relatively short. Consider using a longer token (32+ characters) for better security."
            );
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * Validates an instance ID.
     * 
     * @param instanceId The instance ID to validate
     * @return ValidationResult with status and error message
     */
    public static ValidationResult validateInstanceId(String instanceId) {
        // Check null/empty
        if (instanceId == null || instanceId.trim().isEmpty()) {
            return ValidationResult.error("Instance ID is required");
        }
        
        String trimmedId = instanceId.trim();
        
        // Check length
        if (trimmedId.length() < MIN_INSTANCE_ID_LENGTH) {
            return ValidationResult.error("Instance ID is too short");
        }
        
        if (trimmedId.length() > MAX_INSTANCE_ID_LENGTH) {
            return ValidationResult.error(
                "Instance ID is too long (maximum " + MAX_INSTANCE_ID_LENGTH + " characters)"
            );
        }
        
        // Check format
        if (!VALID_INSTANCE_ID.matcher(trimmedId).matches()) {
            return ValidationResult.error(
                "Instance ID contains invalid characters. Only letters, numbers, dots, underscores, and hyphens are allowed."
            );
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * Validates an event type.
     * 
     * @param eventType The event type to validate
     * @return ValidationResult with status and error message
     */
    public static ValidationResult validateEventType(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            return ValidationResult.error("Event type cannot be empty");
        }
        
        String trimmedType = eventType.trim();
        
        // Check length
        if (trimmedType.length() > MAX_EVENT_TYPE_LENGTH) {
            return ValidationResult.error(
                "Event type is too long (maximum " + MAX_EVENT_TYPE_LENGTH + " characters)"
            );
        }
        
        // Check format (uppercase letters and underscores only)
        if (!VALID_EVENT_TYPE.matcher(trimmedType).matches()) {
            return ValidationResult.error(
                "Event type must contain only uppercase letters and underscores"
            );
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * Validates a regex pattern.
     * 
     * @param pattern The regex pattern to validate
     * @return ValidationResult with status and error message
     */
    public static ValidationResult validatePattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return ValidationResult.ok(); // Empty patterns are OK (means no filtering)
        }
        
        String trimmedPattern = pattern.trim();
        
        // Check length
        if (trimmedPattern.length() > MAX_PATTERN_LENGTH) {
            return ValidationResult.error(
                "Pattern is too long (maximum " + MAX_PATTERN_LENGTH + " characters)"
            );
        }
        
        // Try to compile the regex
        try {
            Pattern.compile(trimmedPattern);
        } catch (Exception e) {
            return ValidationResult.error("Invalid regex pattern: " + e.getMessage());
        }
        
        return ValidationResult.ok();
    }
    
    /**
     * Sanitizes a string to prevent XSS attacks.
     * Removes or escapes dangerous characters.
     * 
     * @param input The input string
     * @return Sanitized string
     */
    public static String sanitizeForDisplay(String input) {
        if (input == null) {
            return null;
        }
        
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;");
    }
    
    /**
     * Checks if a hostname is internal/localhost.
     * 
     * @param host The hostname to check
     * @return true if internal, false otherwise
     */
    private static boolean isInternalHost(String host) {
        if (host == null) {
            return false;
        }
        
        String lowerHost = host.toLowerCase();
        
        // Check for localhost
        if (lowerHost.equals("localhost") || lowerHost.equals("127.0.0.1") || 
            lowerHost.equals("::1") || lowerHost.equals("0.0.0.0")) {
            return true;
        }
        
        // Check for private IP ranges
        if (lowerHost.startsWith("192.168.") || lowerHost.startsWith("10.") || 
            lowerHost.startsWith("172.16.") || lowerHost.startsWith("172.17.") ||
            lowerHost.startsWith("172.18.") || lowerHost.startsWith("172.19.") ||
            lowerHost.startsWith("172.20.") || lowerHost.startsWith("172.21.") ||
            lowerHost.startsWith("172.22.") || lowerHost.startsWith("172.23.") ||
            lowerHost.startsWith("172.24.") || lowerHost.startsWith("172.25.") ||
            lowerHost.startsWith("172.26.") || lowerHost.startsWith("172.27.") ||
            lowerHost.startsWith("172.28.") || lowerHost.startsWith("172.29.") ||
            lowerHost.startsWith("172.30.") || lowerHost.startsWith("172.31.")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Validation result class.
     */
    public static class ValidationResult {
        public enum Status {
            OK,
            WARNING,
            ERROR
        }
        
        private final Status status;
        private final String message;
        
        private ValidationResult(Status status, String message) {
            this.status = status;
            this.message = message;
        }
        
        public static ValidationResult ok() {
            return new ValidationResult(Status.OK, null);
        }
        
        public static ValidationResult warning(String message) {
            return new ValidationResult(Status.WARNING, message);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(Status.ERROR, message);
        }
        
        public Status getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isOk() {
            return status == Status.OK;
        }
        
        public boolean isWarning() {
            return status == Status.WARNING;
        }
        
        public boolean isError() {
            return status == Status.ERROR;
        }
        
        @Override
        public String toString() {
            return status + (message != null ? ": " + message : "");
        }
    }
}

