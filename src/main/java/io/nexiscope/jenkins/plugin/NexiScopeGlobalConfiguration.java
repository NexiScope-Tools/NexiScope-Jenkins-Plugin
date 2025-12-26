package io.nexiscope.jenkins.plugin;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.Secret;
import io.nexiscope.jenkins.plugin.websocket.ConnectionTester;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor.FormException;
import hudson.util.ListBoxModel;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Global configuration for NexiScope integration.
 * 
 * Accessible via: Jenkins → Manage Jenkins → NexiScope Integration
 * (Dedicated management page, not in system configure)
 * 
 * @author NexiScope Team
 */
@Extension(optional = true)
public class NexiScopeGlobalConfiguration extends GlobalConfiguration {
    
    private static final Logger LOGGER = Logger.getLogger(NexiScopeGlobalConfiguration.class.getName());
    
    private static final String DEFAULT_INSTANCE_ID = "default-instance";
    
    private String platformUrl;
    private Secret authToken;
    private String instanceId = DEFAULT_INSTANCE_ID;
    private boolean enabled = true;
    private String logLevel = "INFO";
    private int queueMaxSize = 1000; // Default queue size
    private boolean logStreamingEnabled = false; // Default: disabled
    private String logLevelFilter = "INFO"; // Default: INFO and above
    private int maxLogLines = 1000; // Maximum log lines per build
    
    // Event filtering configuration
    private boolean eventFilteringEnabled = false; // Default: disabled
    private String jobIncludePatterns; // Regex patterns for jobs to include
    private String jobExcludePatterns; // Regex patterns for jobs to exclude
    private String branchIncludePatterns; // Regex patterns for branches to include
    private String branchExcludePatterns; // Regex patterns for branches to exclude
    private String allowedEventTypes; // Comma-separated list of allowed event types
    private String blockedEventTypes; // Comma-separated list of blocked event types
    
    // Certificate pinning configuration
    private boolean certificatePinningEnabled = false; // Default: disabled
    private String certificatePins; // Format: "hostname1:hash1,hash2;hostname2:hash3"
    
    // Event batching configuration (Performance optimization)
    private boolean eventBatchingEnabled = true; // Default: enabled for better performance
    private int batchSize = 50; // Default: 50 events per batch
    private int batchTimeoutMs = 1000; // Default: 1 second timeout
    
    @DataBoundConstructor
    public NexiScopeGlobalConfiguration() {
        load();
    }
    
    public static NexiScopeGlobalConfiguration get() {
        return GlobalConfiguration.all().get(NexiScopeGlobalConfiguration.class);
    }
    
    /**
     * Override to prevent this configuration from appearing in the system configure page.
     * It will only be accessible via the dedicated management link.
     * 
     * By returning a config file in a custom location (not the standard GlobalConfiguration location),
     * Jenkins won't automatically include it in the system configure page.
     */
    @Override
    public hudson.XmlFile getConfigFile() {
        // Return a config file in a custom location to hide from system configure page
        // The configuration is accessible via NexiScopeConfigurationAction instead
        return new hudson.XmlFile(new java.io.File(jenkins.model.Jenkins.get().getRootDir(), "nexiscope-config.xml"));
    }
    
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        LOGGER.info("NexiScopeGlobalConfiguration.configure() called");
        
        // Store old values to detect changes
        String oldPlatformUrl = platformUrl;
        String oldInstanceId = instanceId;
        boolean oldEnabled = enabled;
        boolean oldValid = isValid();
        
        LOGGER.info("Old config - URL: " + oldPlatformUrl + ", InstanceId: " + oldInstanceId + ", Enabled: " + oldEnabled + ", Valid: " + oldValid);
        
        // Validate before binding
        if (json.optBoolean("enabled", true)) {
            // Configuration is enabled, validate required fields
            String platformUrlValue = json.optString("platformUrl", "").trim();
            if (platformUrlValue.isEmpty()) {
                throw new FormException("Platform URL is required when the plugin is enabled", "platformUrl");
            }
            
            String authTokenValue = json.optString("authToken", "").trim();
            if (authTokenValue.isEmpty()) {
                throw new FormException("Authentication token is required", "authToken");
            }
        }
        
        req.bindJSON(this, json);
        save();
        
        LOGGER.info("New config - URL: " + platformUrl + ", InstanceId: " + instanceId + ", Enabled: " + enabled + ", Valid: " + isValid());
        
        // Check if configuration changed or became valid
        boolean configChanged = !java.util.Objects.equals(oldPlatformUrl, platformUrl) ||
                               !java.util.Objects.equals(oldInstanceId, instanceId) ||
                               oldEnabled != enabled;
        boolean becameValid = !oldValid && isValid();
        
        LOGGER.info("Config changed: " + configChanged + ", Became valid: " + becameValid);
        
        // Check if connection exists
        boolean connectionExists = WebSocketClient.getInstance().isConnected();
        LOGGER.info("WebSocket connection exists: " + connectionExists);
        
        // Always trigger reconnection if:
        // 1. Config changed OR
        // 2. Config became valid OR
        // 3. Config is valid but no connection exists (e.g., after restart or initial setup)
        if (configChanged || becameValid || (isValid() && !connectionExists)) {
            if (isValid() && !connectionExists) {
                LOGGER.info("Configuration is valid but no connection exists, establishing WebSocket connection");
            } else {
                LOGGER.info("Configuration changed or became valid, triggering WebSocket reconnection");
            }
            try {
                WebSocketClient.getInstance().reconnect();
            } catch (Exception e) {
                LOGGER.warning("Failed to reconnect WebSocket after configuration change: " + e.getMessage());
                LOGGER.log(java.util.logging.Level.WARNING, "Exception details", e);
            }
        } else {
            LOGGER.info("Configuration did not change and connection already exists, skipping reconnection");
        }
        
        return true;
    }
    
    @NonNull
    @Override
    public String getDisplayName() {
        return "NexiScope Integration";
    }
    
    public String getPlatformUrl() {
        return platformUrl;
    }
    
    @DataBoundSetter
    public void setPlatformUrl(String platformUrl) {
        this.platformUrl = platformUrl;
    }
    
    public String getAuthToken() {
        return authToken != null ? authToken.getPlainText() : null;
    }
    
    @DataBoundSetter
    public void setAuthToken(String authToken) {
        this.authToken = Secret.fromString(authToken);
    }
    
    public String getInstanceId() {
        return instanceId != null ? instanceId : DEFAULT_INSTANCE_ID;
    }
    
    @DataBoundSetter
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId != null && !instanceId.isEmpty() ? instanceId : DEFAULT_INSTANCE_ID;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getLogLevel() {
        return logLevel != null ? logLevel : "INFO";
    }
    
    @DataBoundSetter
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
    
    public int getQueueMaxSize() {
        return queueMaxSize > 0 ? queueMaxSize : 1000;
    }
    
    @DataBoundSetter
    public void setQueueMaxSize(int queueMaxSize) {
        // Ensure minimum size of 10 and maximum of 100000
        this.queueMaxSize = Math.max(10, Math.min(100000, queueMaxSize));
    }
    
    public boolean isLogStreamingEnabled() {
        return logStreamingEnabled;
    }
    
    @DataBoundSetter
    public void setLogStreamingEnabled(boolean logStreamingEnabled) {
        this.logStreamingEnabled = logStreamingEnabled;
    }
    
    public String getLogLevelFilter() {
        return logLevelFilter != null ? logLevelFilter : "INFO";
    }
    
    @DataBoundSetter
    public void setLogLevelFilter(String logLevelFilter) {
        // Validate log level filter
        if (logLevelFilter != null && 
            (logLevelFilter.equalsIgnoreCase("DEBUG") || 
             logLevelFilter.equalsIgnoreCase("INFO") ||
             logLevelFilter.equalsIgnoreCase("WARN") ||
             logLevelFilter.equalsIgnoreCase("ERROR"))) {
            this.logLevelFilter = logLevelFilter.toUpperCase();
        } else {
            this.logLevelFilter = "INFO"; // Default
        }
    }
    
    public int getMaxLogLines() {
        return maxLogLines > 0 ? maxLogLines : 1000;
    }
    
    @DataBoundSetter
    public void setMaxLogLines(int maxLogLines) {
        // Ensure minimum of 10 and maximum of 100000
        this.maxLogLines = Math.max(10, Math.min(100000, maxLogLines));
    }
    
    // Event filtering getters and setters
    
    public boolean isEventFilteringEnabled() {
        return eventFilteringEnabled;
    }
    
    @DataBoundSetter
    public void setEventFilteringEnabled(boolean eventFilteringEnabled) {
        this.eventFilteringEnabled = eventFilteringEnabled;
    }
    
    public String getJobIncludePatterns() {
        return jobIncludePatterns;
    }
    
    @DataBoundSetter
    public void setJobIncludePatterns(String jobIncludePatterns) {
        this.jobIncludePatterns = jobIncludePatterns;
    }
    
    public String getJobExcludePatterns() {
        return jobExcludePatterns;
    }
    
    @DataBoundSetter
    public void setJobExcludePatterns(String jobExcludePatterns) {
        this.jobExcludePatterns = jobExcludePatterns;
    }
    
    public String getBranchIncludePatterns() {
        return branchIncludePatterns;
    }
    
    @DataBoundSetter
    public void setBranchIncludePatterns(String branchIncludePatterns) {
        this.branchIncludePatterns = branchIncludePatterns;
    }
    
    public String getBranchExcludePatterns() {
        return branchExcludePatterns;
    }
    
    @DataBoundSetter
    public void setBranchExcludePatterns(String branchExcludePatterns) {
        this.branchExcludePatterns = branchExcludePatterns;
    }
    
    public String getAllowedEventTypes() {
        return allowedEventTypes;
    }
    
    @DataBoundSetter
    public void setAllowedEventTypes(String allowedEventTypes) {
        this.allowedEventTypes = allowedEventTypes;
    }
    
    public String getBlockedEventTypes() {
        return blockedEventTypes;
    }
    
    @DataBoundSetter
    public void setBlockedEventTypes(String blockedEventTypes) {
        this.blockedEventTypes = blockedEventTypes;
    }
    
    // Certificate pinning getters and setters
    
    public boolean isCertificatePinningEnabled() {
        return certificatePinningEnabled;
    }
    
    @DataBoundSetter
    public void setCertificatePinningEnabled(boolean certificatePinningEnabled) {
        this.certificatePinningEnabled = certificatePinningEnabled;
    }
    
    public String getCertificatePins() {
        return certificatePins;
    }
    
    @DataBoundSetter
    public void setCertificatePins(String certificatePins) {
        this.certificatePins = certificatePins;
    }
    
    // Event batching getters and setters
    
    public boolean isEventBatchingEnabled() {
        return eventBatchingEnabled;
    }
    
    @DataBoundSetter
    public void setEventBatchingEnabled(boolean eventBatchingEnabled) {
        this.eventBatchingEnabled = eventBatchingEnabled;
    }
    
    public int getBatchSize() {
        return batchSize > 0 ? batchSize : 50;
    }
    
    @DataBoundSetter
    public void setBatchSize(int batchSize) {
        // Ensure minimum of 1 and maximum of 1000
        this.batchSize = Math.max(1, Math.min(1000, batchSize));
    }
    
    public int getBatchTimeoutMs() {
        return batchTimeoutMs > 0 ? batchTimeoutMs : 1000;
    }
    
    @DataBoundSetter
    public void setBatchTimeoutMs(int batchTimeoutMs) {
        // Ensure minimum of 100ms and maximum of 10 seconds
        this.batchTimeoutMs = Math.max(100, Math.min(10000, batchTimeoutMs));
    }
    
    /**
     * Validates the configuration.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean isValid() {
        if (!enabled) {
            return true; // Configuration is valid if disabled
        }
        
        if (platformUrl == null || platformUrl.trim().isEmpty()) {
            return false;
        }
        
        // Token authentication is required
        if (authToken == null || authToken.getPlainText().trim().isEmpty()) {
            return false;
        }
        
        // Validate URL format
        // Accept both secure (wss/https) and non-secure (ws/http) URLs
        // Non-secure URLs are allowed for development/testing
        String url = platformUrl.trim().toLowerCase();
        if (!url.startsWith("https://") && 
            !url.startsWith("wss://") && 
            !url.startsWith("http://") && 
            !url.startsWith("ws://")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Tests the connection to the NexiScope platform.
     * Called by the "Test Connection" button in the configuration UI.
     * 
     * @param req The HTTP request
     * @param rsp The HTTP response
     * @param platformUrl The platform URL to test
     * @param authToken The authentication token to test
     * @param instanceId The instance ID to test
     * @throws IOException If response writing fails
     * @throws ServletException If servlet error occurs
     */
    public void doTestConnection(
            StaplerRequest req,
            StaplerResponse rsp,
            @QueryParameter("platformUrl") String platformUrl,
            @QueryParameter("authToken") String authToken,
            @QueryParameter("instanceId") String instanceId
    ) throws IOException, ServletException {
        
        LOGGER.info("Testing connection to NexiScope platform");
        
        // Rate limiting check
        String remoteAddr = req.getRemoteAddr();
        if (!io.nexiscope.jenkins.plugin.security.RateLimiter.isAllowed(
                io.nexiscope.jenkins.plugin.security.RateLimiter.Operation.TEST_CONNECTION, 
                remoteAddr)) {
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.getWriter().write("<div style='color: red; font-weight: bold; padding: 10px;'>✗ ERROR: Rate limit exceeded. Please wait before testing again (limit: 10 per minute)</div>");
            LOGGER.warning("Test connection rate limit exceeded for IP: " + remoteAddr);
            return;
        }
        
        // Validate inputs
        if (platformUrl == null || platformUrl.trim().isEmpty()) {
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.getWriter().write("<div style='color: red; font-weight: bold; padding: 10px;'>✗ ERROR: Platform URL is required</div>");
            return;
        }
        
        // Token authentication validation
        if (authToken == null || authToken.trim().isEmpty()) {
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.getWriter().write("<div style='color: red; font-weight: bold; padding: 10px;'>✗ ERROR: Authentication token is required</div>");
            return;
        }
        
        if (instanceId == null || instanceId.trim().isEmpty()) {
            instanceId = DEFAULT_INSTANCE_ID;
        }
        
        // Test connection with token
        ConnectionTester.TestResult result = ConnectionTester.testConnection(
            platformUrl.trim(),
            authToken.trim(),
            instanceId.trim()
        );
        
        // Audit log the connection test
        String user = jenkins.model.Jenkins.getAuthentication2().getName();
        if (result.isSuccess()) {
            io.nexiscope.jenkins.plugin.security.AuditLogger.logSuccess(
                io.nexiscope.jenkins.plugin.security.AuditLogger.EventType.CONNECTION_TEST,
                user,
                "Connection test successful to: " + platformUrl.trim()
            );
        } else {
            io.nexiscope.jenkins.plugin.security.AuditLogger.logFailure(
                io.nexiscope.jenkins.plugin.security.AuditLogger.EventType.CONNECTION_TEST,
                user,
                "Connection test failed to: " + platformUrl.trim() + " - " + result.getMessage()
            );
        }
        
        // Return result
        rsp.setContentType("text/html;charset=UTF-8");
        if (result.isSuccess()) {
            rsp.getWriter().write("<div style='color: green; font-weight: bold; padding: 10px;'>✓ SUCCESS: " + result.getMessage() + "</div>");
            LOGGER.info("Connection test successful: " + result.getMessage());
        } else {
            rsp.getWriter().write("<div style='color: red; font-weight: bold; padding: 10px;'>✗ ERROR: " + result.getMessage() + "</div>");
            if (result.getErrorDetails() != null && !result.getErrorDetails().trim().isEmpty()) {
                rsp.getWriter().write("<div style='margin-top: 10px; color: #666; font-size: 12px; padding: 0 10px 10px 10px;'>" + 
                    result.getErrorDetails().replace("\n", "<br>") + "</div>");
            }
            LOGGER.warning("Connection test failed: " + result.getMessage());
        }
    }
    
    /**
     * Fills the log level dropdown items.
     * Required by Jenkins for f:select fields with field="logLevel".
     * Returns ListBoxModel with static options (matching config.jelly).
     */
    public ListBoxModel doFillLogLevelItems(@QueryParameter String value) {
        ListBoxModel items = new ListBoxModel();
        items.add("INFO", "INFO");
        items.add("WARNING", "WARNING");
        items.add("SEVERE", "SEVERE");
        return items;
    }
    
    /**
     * Fills the log level filter dropdown items.
     * Required by Jenkins for f:select fields with field="logLevelFilter".
     * Returns ListBoxModel with static options (matching config.jelly).
     */
    public ListBoxModel doFillLogLevelFilterItems(@QueryParameter String value) {
        ListBoxModel items = new ListBoxModel();
        items.add("DEBUG (All logs)", "DEBUG");
        items.add("INFO (Info and above)", "INFO");
        items.add("WARN (Warnings and errors)", "WARN");
        items.add("ERROR (Errors only)", "ERROR");
        return items;
    }
    
    /**
     * Validates the platform URL format.
     * Called by Jenkins form validation.
     * 
     * @param value The platform URL to validate
     * @return FormValidation result
     */
    public hudson.util.FormValidation doCheckPlatformUrl(@QueryParameter String value) {
        io.nexiscope.jenkins.plugin.security.InputValidator.ValidationResult result = 
            io.nexiscope.jenkins.plugin.security.InputValidator.validatePlatformUrl(value);
        
        if (result.isError()) {
            return hudson.util.FormValidation.error(result.getMessage());
        } else if (result.isWarning()) {
            return hudson.util.FormValidation.warning(result.getMessage());
        } else {
            return hudson.util.FormValidation.ok();
        }
    }
    
    /**
     * Validates the authentication token.
     * Called by Jenkins form validation.
     * 
     * @param value The authentication token to validate
     * @return FormValidation result
     */
    public hudson.util.FormValidation doCheckAuthToken(@QueryParameter String value) {
        io.nexiscope.jenkins.plugin.security.InputValidator.ValidationResult result = 
            io.nexiscope.jenkins.plugin.security.InputValidator.validateAuthToken(value);
        
        if (result.isError()) {
            return hudson.util.FormValidation.error(result.getMessage());
        } else if (result.isWarning()) {
            return hudson.util.FormValidation.warning(result.getMessage());
        } else {
            return hudson.util.FormValidation.ok();
        }
    }
    
    /**
     * Validates the instance ID.
     * Called by Jenkins form validation.
     * 
     * @param value The instance ID to validate
     * @return FormValidation result
     */
    public hudson.util.FormValidation doCheckInstanceId(@QueryParameter String value) {
        io.nexiscope.jenkins.plugin.security.InputValidator.ValidationResult result = 
            io.nexiscope.jenkins.plugin.security.InputValidator.validateInstanceId(value);
        
        if (result.isError()) {
            return hudson.util.FormValidation.error(result.getMessage());
        } else if (result.isWarning()) {
            return hudson.util.FormValidation.warning(result.getMessage());
        } else {
            return hudson.util.FormValidation.ok();
        }
    }
}

