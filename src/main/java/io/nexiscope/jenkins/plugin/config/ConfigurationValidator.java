package io.nexiscope.jenkins.plugin.config;

import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.security.InputValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive configuration validator.
 * 
 * Features:
 * - Validates entire configuration at once
 * - Returns all validation issues (not just first error)
 * - Provides severity levels (error, warning, info)
 * - Suggests fixes for common issues
 * 
 * @author NexiScope Team
 */
public class ConfigurationValidator {
    
    /**
     * Validation issue severity.
     */
    public enum Severity {
        ERROR("Error", "Configuration is invalid and must be fixed"),
        WARNING("Warning", "Configuration may cause issues"),
        INFO("Info", "Suggestion for better configuration");
        
        private final String displayName;
        private final String description;
        
        Severity(String displayName, String description) {
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
     * Validation issue.
     */
    public static class ValidationIssue {
        private final Severity severity;
        private final String field;
        private final String message;
        private final String suggestion;
        
        public ValidationIssue(Severity severity, String field, String message, String suggestion) {
            this.severity = severity;
            this.field = field;
            this.message = message;
            this.suggestion = suggestion;
        }
        
        public Severity getSeverity() {
            return severity;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getSuggestion() {
            return suggestion;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s%s",
                severity.getDisplayName(),
                field,
                message,
                suggestion != null ? " → " + suggestion : ""
            );
        }
    }
    
    /**
     * Validation result.
     */
    public static class ValidationResult {
        private final List<ValidationIssue> issues;
        
        public ValidationResult() {
            this.issues = new ArrayList<>();
        }
        
        public void addIssue(ValidationIssue issue) {
            issues.add(issue);
        }
        
        public void addError(String field, String message, String suggestion) {
            issues.add(new ValidationIssue(Severity.ERROR, field, message, suggestion));
        }
        
        public void addWarning(String field, String message, String suggestion) {
            issues.add(new ValidationIssue(Severity.WARNING, field, message, suggestion));
        }
        
        public void addInfo(String field, String message, String suggestion) {
            issues.add(new ValidationIssue(Severity.INFO, field, message, suggestion));
        }
        
        public List<ValidationIssue> getIssues() {
            return new ArrayList<>(issues);
        }
        
        public List<ValidationIssue> getErrors() {
            List<ValidationIssue> errors = new ArrayList<>();
            for (ValidationIssue issue : issues) {
                if (issue.getSeverity() == Severity.ERROR) {
                    errors.add(issue);
                }
            }
            return errors;
        }
        
        public List<ValidationIssue> getWarnings() {
            List<ValidationIssue> warnings = new ArrayList<>();
            for (ValidationIssue issue : issues) {
                if (issue.getSeverity() == Severity.WARNING) {
                    warnings.add(issue);
                }
            }
            return warnings;
        }
        
        public boolean isValid() {
            return getErrors().isEmpty();
        }
        
        public boolean hasWarnings() {
            return !getWarnings().isEmpty();
        }
        
        public int getErrorCount() {
            return getErrors().size();
        }
        
        public int getWarningCount() {
            return getWarnings().size();
        }
        
        @Override
        public String toString() {
            if (isValid() && !hasWarnings()) {
                return "Configuration is valid ✓";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("Configuration Validation Results:\n");
            sb.append("Errors: ").append(getErrorCount()).append("\n");
            sb.append("Warnings: ").append(getWarningCount()).append("\n\n");
            
            for (ValidationIssue issue : issues) {
                sb.append(issue.toString()).append("\n");
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Validates the entire configuration.
     * 
     * @param config The configuration to validate
     * @return ValidationResult with all issues
     */
    public static ValidationResult validate(NexiScopeGlobalConfiguration config) {
        ValidationResult result = new ValidationResult();
        
        if (config == null) {
            result.addError("Configuration", "Configuration is null", "Initialize configuration first");
            return result;
        }
        
        // Validate platform URL
        validatePlatformUrl(config, result);
        
        // Validate authentication token
        validateAuthToken(config, result);
        
        // Validate instance ID
        validateInstanceId(config, result);
        
        // Validate queue configuration
        validateQueueConfig(config, result);
        
        // Validate log streaming configuration
        validateLogStreamingConfig(config, result);
        
        // Validate event filtering configuration
        validateEventFilteringConfig(config, result);
        
        // Validate certificate pinning configuration
        validateCertificatePinningConfig(config, result);
        
        // Validate performance settings
        validatePerformanceSettings(config, result);
        
        return result;
    }
    
    /**
     * Validates platform URL.
     */
    private static void validatePlatformUrl(NexiScopeGlobalConfiguration config, ValidationResult result) {
        String url = config.getPlatformUrl();
        
        if (url == null || url.trim().isEmpty()) {
            result.addError("Platform URL", "Platform URL is required", 
                "Enter the NexiScope platform URL (e.g., https://api.nexiscope.com)");
            return;
        }
        
        InputValidator.ValidationResult urlResult = InputValidator.validatePlatformUrl(url);
        if (urlResult.isError()) {
            result.addError("Platform URL", urlResult.getMessage(), 
                "Use a valid URL starting with https:// or wss://");
        } else if (urlResult.isWarning()) {
            result.addWarning("Platform URL", urlResult.getMessage(), 
                "Consider using HTTPS/WSS for production");
        }
    }
    
    /**
     * Validates authentication token.
     */
    private static void validateAuthToken(NexiScopeGlobalConfiguration config, ValidationResult result) {
        String token = config.getAuthToken();
        
        if (token == null || token.trim().isEmpty()) {
            result.addError("Authentication Token", "Authentication token is required", 
                "Generate a token from NexiScope Platform → Settings → API Tokens");
            return;
        }
        
        InputValidator.ValidationResult tokenResult = InputValidator.validateAuthToken(token);
        if (tokenResult.isError()) {
            result.addError("Authentication Token", tokenResult.getMessage(), 
                "Use a valid token (16+ characters, alphanumeric)");
        } else if (tokenResult.isWarning()) {
            result.addWarning("Authentication Token", tokenResult.getMessage(), 
                "Consider using a longer token (32+ characters) for better security");
        }
    }
    
    /**
     * Validates instance ID.
     */
    private static void validateInstanceId(NexiScopeGlobalConfiguration config, ValidationResult result) {
        String instanceId = config.getInstanceId();
        
        if (instanceId == null || instanceId.trim().isEmpty()) {
            result.addWarning("Instance ID", "Instance ID is empty, using default", 
                "Set a unique instance ID to identify this Jenkins instance");
            return;
        }
        
        InputValidator.ValidationResult idResult = InputValidator.validateInstanceId(instanceId);
        if (idResult.isError()) {
            result.addError("Instance ID", idResult.getMessage(), 
                "Use alphanumeric characters, dots, underscores, and hyphens only");
        }
        
        // Check if instance ID is too generic
        if (instanceId.equalsIgnoreCase("jenkins") || 
            instanceId.equalsIgnoreCase("default") ||
            instanceId.equalsIgnoreCase("test")) {
            result.addInfo("Instance ID", "Instance ID is generic", 
                "Consider using a more specific ID (e.g., jenkins-prod-01)");
        }
    }
    
    /**
     * Validates queue configuration.
     */
    private static void validateQueueConfig(NexiScopeGlobalConfiguration config, ValidationResult result) {
        int queueSize = config.getQueueMaxSize();
        
        if (queueSize < 10) {
            result.addWarning("Queue Size", "Queue size is very small (" + queueSize + ")", 
                "Increase to at least 100 for better reliability");
        } else if (queueSize > 100000) {
            result.addWarning("Queue Size", "Queue size is very large (" + queueSize + ")", 
                "Consider reducing to avoid excessive memory usage");
        } else if (queueSize < 100) {
            result.addInfo("Queue Size", "Queue size is small (" + queueSize + ")", 
                "Consider increasing to 1000+ for high-volume environments");
        }
    }
    
    /**
     * Validates log streaming configuration.
     */
    private static void validateLogStreamingConfig(NexiScopeGlobalConfiguration config, ValidationResult result) {
        if (!config.isLogStreamingEnabled()) {
            return; // Not enabled, skip validation
        }
        
        int maxLogLines = config.getMaxLogLines();
        if (maxLogLines < 10) {
            result.addWarning("Max Log Lines", "Max log lines is very small (" + maxLogLines + ")", 
                "Increase to at least 100 to capture meaningful logs");
        } else if (maxLogLines > 100000) {
            result.addWarning("Max Log Lines", "Max log lines is very large (" + maxLogLines + ")", 
                "Consider reducing to avoid excessive data transfer");
        }
        
        String logLevel = config.getLogLevelFilter();
        if (logLevel == null || logLevel.trim().isEmpty()) {
            result.addInfo("Log Level Filter", "Log level filter not set", 
                "Set a log level filter to reduce log volume (recommended: INFO)");
        }
    }
    
    /**
     * Validates event filtering configuration.
     */
    private static void validateEventFilteringConfig(NexiScopeGlobalConfiguration config, ValidationResult result) {
        if (!config.isEventFilteringEnabled()) {
            result.addInfo("Event Filtering", "Event filtering is disabled", 
                "Consider enabling event filtering to reduce unnecessary events");
            return;
        }
        
        // Validate patterns
        String jobInclude = config.getJobIncludePatterns();
        String jobExclude = config.getJobExcludePatterns();
        
        if (jobInclude != null && !jobInclude.trim().isEmpty()) {
            InputValidator.ValidationResult patternResult = InputValidator.validatePattern(jobInclude);
            if (patternResult.isError()) {
                result.addError("Job Include Patterns", patternResult.getMessage(), 
                    "Fix the regex pattern or remove it");
            }
        }
        
        if (jobExclude != null && !jobExclude.trim().isEmpty()) {
            InputValidator.ValidationResult patternResult = InputValidator.validatePattern(jobExclude);
            if (patternResult.isError()) {
                result.addError("Job Exclude Patterns", patternResult.getMessage(), 
                    "Fix the regex pattern or remove it");
            }
        }
        
        // Check for conflicting patterns
        if (jobInclude != null && !jobInclude.trim().isEmpty() && 
            jobExclude != null && !jobExclude.trim().isEmpty()) {
            result.addInfo("Event Filtering", "Both include and exclude patterns are set", 
                "Ensure patterns don't conflict (exclude takes precedence)");
        }
    }
    
    /**
     * Validates certificate pinning configuration.
     */
    private static void validateCertificatePinningConfig(NexiScopeGlobalConfiguration config, ValidationResult result) {
        if (!config.isCertificatePinningEnabled()) {
            result.addInfo("Certificate Pinning", "Certificate pinning is disabled", 
                "Consider enabling for additional security in production");
            return;
        }
        
        String pins = config.getCertificatePins();
        if (pins == null || pins.trim().isEmpty()) {
            result.addError("Certificate Pins", "Certificate pinning is enabled but no pins configured", 
                "Add certificate pins or disable certificate pinning");
        }
    }
    
    /**
     * Validates performance settings.
     */
    private static void validatePerformanceSettings(NexiScopeGlobalConfiguration config, ValidationResult result) {
        if (!config.isEventBatchingEnabled()) {
            result.addInfo("Event Batching", "Event batching is disabled", 
                "Enable event batching for better performance (recommended)");
            return;
        }
        
        int batchSize = config.getBatchSize();
        if (batchSize < 10) {
            result.addWarning("Batch Size", "Batch size is very small (" + batchSize + ")", 
                "Increase to at least 20 for better performance");
        } else if (batchSize > 500) {
            result.addWarning("Batch Size", "Batch size is very large (" + batchSize + ")", 
                "Consider reducing to 100-200 for better latency");
        }
        
        int batchTimeout = config.getBatchTimeoutMs();
        if (batchTimeout < 500) {
            result.addInfo("Batch Timeout", "Batch timeout is very short (" + batchTimeout + "ms)", 
                "Consider increasing to 1000ms for better batching efficiency");
        } else if (batchTimeout > 5000) {
            result.addInfo("Batch Timeout", "Batch timeout is very long (" + batchTimeout + "ms)", 
                "Consider reducing to 1000-2000ms for better responsiveness");
        }
    }
    
    /**
     * Quick validation check (only errors).
     * 
     * @param config The configuration to validate
     * @return true if valid (no errors), false otherwise
     */
    public static boolean isValid(NexiScopeGlobalConfiguration config) {
        return validate(config).isValid();
    }
    
    /**
     * Gets a summary of validation issues.
     * 
     * @param config The configuration to validate
     * @return Summary string
     */
    public static String getSummary(NexiScopeGlobalConfiguration config) {
        ValidationResult result = validate(config);
        
        if (result.isValid() && !result.hasWarnings()) {
            return "✓ Configuration is valid";
        }
        
        StringBuilder summary = new StringBuilder();
        if (!result.isValid()) {
            summary.append("✗ Configuration has ").append(result.getErrorCount()).append(" error(s)");
        } else {
            summary.append("⚠ Configuration is valid but has ").append(result.getWarningCount()).append(" warning(s)");
        }
        
        return summary.toString();
    }
}

