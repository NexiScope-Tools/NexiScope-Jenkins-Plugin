package io.nexiscope.jenkins.plugin;

import hudson.Extension;
import hudson.model.ManagementLink;
import jenkins.model.Jenkins;

/**
 * Adds a dedicated management link for NexiScope configuration.
 * 
 * This makes the configuration accessible from:
 * Jenkins → Manage Jenkins → NexiScope Integration
 * 
 * @author NexiScope Team
 */
@Extension
public class NexiScopeManagementLink extends ManagementLink {
    
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
    public String getDescription() {
        return "Configure NexiScope platform integration settings, authentication, event filtering, and security options.";
    }
    
    @Override
    public String getUrlName() {
        // Link to the dedicated NexiScope configuration page
        return "nexiscope";
    }
    
    /**
     * Checks if the user has permission to access this link.
     */
    @Override
    public boolean getRequiresConfirmation() {
        return false;
    }
    
    /**
     * Gets the category for this management link.
     * Returns "CONFIGURATION" to group it with other configuration links.
     */
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }
}

