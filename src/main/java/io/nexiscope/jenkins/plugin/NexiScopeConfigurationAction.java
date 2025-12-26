package io.nexiscope.jenkins.plugin;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Provides a dedicated configuration page for NexiScope Integration.
 * 
 * Accessible via: Jenkins → Manage Jenkins → NexiScope Integration
 * Or directly at: /manage/nexiscope
 * 
 * @author NexiScope Team
 */
@Extension
public class NexiScopeConfigurationAction implements RootAction {
    
    @Override
    public String getIconFileName() {
        // Return null to use default icon, or specify custom icon path if available
        // return "plugin/nexiscope-integration/images/48x48/nexiscope.png";
        return "setting.png"; // Use Jenkins default settings icon
    }
    
    @Override
    public String getDisplayName() {
        return "NexiScope Integration";
    }
    
    @Override
    public String getUrlName() {
        return "nexiscope";
    }
    
    /**
     * Gets the global configuration instance.
     */
    public NexiScopeGlobalConfiguration getConfig() {
        return NexiScopeGlobalConfiguration.get();
    }
    
    /**
     * Handles the configuration page view.
     * Stapler will automatically find and render the index.jelly view file.
     * We ensure config exists by initializing it if needed.
     */
    // No doIndex method needed - Stapler will automatically find index.jelly
    // The view will handle checking if config exists
    
    /**
     * Handles test connection request.
     * Delegates to the global configuration's test connection method.
     */
    public void doTestConnection(
            StaplerRequest req,
            StaplerResponse rsp,
            @QueryParameter("platformUrl") String platformUrl,
            @QueryParameter("authToken") String authToken,
            @QueryParameter("instanceId") String instanceId
    ) throws IOException, ServletException {
        // Check permissions
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        
        // Delegate to global configuration's test connection method
        NexiScopeGlobalConfiguration config = getConfig();
        if (config != null) {
            config.doTestConnection(
                req, rsp,
                platformUrl, authToken, instanceId
            );
        } else {
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.getWriter().write("<div style='color: red; font-weight: bold; padding: 10px;'>✗ ERROR: Configuration not found</div>");
        }
    }
    
    /**
     * Handles configuration submission.
     */
    @POST
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        // Check permissions
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        
        // Delegate to GlobalConfiguration's configure method
        NexiScopeGlobalConfiguration config = getConfig();
        if (config != null) {
            try {
                // Get submitted form and filter out problematic fields
                net.sf.json.JSONObject json;
                try {
                    json = req.getSubmittedForm();
                } catch (Exception e) {
                    // If getSubmittedForm fails, try to manually parse the form data
                    // This can happen when Jenkins includes "init" values in hidden fields
                    java.util.Map<String, String[]> parameterMap = req.getParameterMap();
                    json = new net.sf.json.JSONObject();
                    for (java.util.Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                        String key = entry.getKey();
                        String[] values = entry.getValue();
                        if (values != null && values.length > 0) {
                            String value = values[0];
                            // Skip "init" values and empty values that might cause issues
                            if (value != null && !value.equals("init") && !value.trim().isEmpty()) {
                                // Remove the "_.prefix" if present (Jenkins form field naming)
                                String cleanKey = key.startsWith("_.") ? key.substring(2) : key;
                                json.put(cleanKey, value);
                            }
                        }
                    }
                }
                
                // Remove any remaining fields that might cause JSON parsing issues
                // Jenkins sometimes includes hidden fields with "init" values for select fields
                java.util.Iterator keys = json.keys();
                java.util.List<String> keysToRemove = new java.util.ArrayList<>();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    Object value = json.get(key);
                    // Remove fields with "init" as value (these are hidden inputs from select fields)
                    if (value != null && (value.toString().equals("init") || value.toString().trim().isEmpty())) {
                        keysToRemove.add(key);
                    }
                }
                for (String key : keysToRemove) {
                    json.remove(key);
                }
                
                // Validate configuration before saving
                if (!validateConfiguration(json, config)) {
                    // Redirect back with error message
                    rsp.sendRedirect("?error=validation");
                    return;
                }
                
                config.configure(req, json);
                rsp.sendRedirect("?saved=true");
            } catch (hudson.model.Descriptor.FormException e) {
                // Form validation error
                rsp.sendRedirect("?error=" + java.net.URLEncoder.encode(e.getMessage(), "UTF-8"));
            } catch (Exception e) {
                java.util.logging.Logger logger = java.util.logging.Logger.getLogger(NexiScopeConfigurationAction.class.getName());
                logger.log(java.util.logging.Level.SEVERE, "Failed to save configuration", e);
                rsp.sendRedirect("?error=" + java.net.URLEncoder.encode("Failed to save configuration: " + e.getMessage(), "UTF-8"));
            }
        } else {
            rsp.sendRedirect("?error=config_not_found");
        }
    }
    
    /**
     * Validates the configuration before saving.
     * 
     * @param json The submitted form JSON
     * @param config The current configuration instance
     * @return true if valid, false otherwise
     */
    private boolean validateConfiguration(net.sf.json.JSONObject json, NexiScopeGlobalConfiguration config) {
        // Check if enabled
        boolean enabled = json.optBoolean("enabled", true);
        if (!enabled) {
            return true; // Configuration is valid if disabled
        }
        
        // Validate platform URL
        String platformUrl = json.optString("platformUrl", "").trim();
        if (platformUrl.isEmpty()) {
            return false;
        }
        
        // Validate token authentication
        String authToken = json.optString("authToken", "").trim();
        if (authToken.isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Delegates log level dropdown fill to GlobalConfiguration.
     * This allows f:select fields in included forms to work correctly.
     */
    public ListBoxModel doFillLogLevelItems(@QueryParameter String value) {
        NexiScopeGlobalConfiguration config = getConfig();
        if (config != null) {
            return config.doFillLogLevelItems(value);
        }
        return new ListBoxModel();
    }
    
    /**
     * Delegates log level filter dropdown fill to GlobalConfiguration.
     * This allows f:select fields in included forms to work correctly.
     */
    public ListBoxModel doFillLogLevelFilterItems(@QueryParameter String value) {
        NexiScopeGlobalConfiguration config = getConfig();
        if (config != null) {
            return config.doFillLogLevelFilterItems(value);
        }
        return new ListBoxModel();
    }
    
}

