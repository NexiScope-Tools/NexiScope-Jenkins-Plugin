package io.nexiscope.jenkins.plugin;

import hudson.Plugin;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;

import java.util.logging.Logger;

/**
 * Main plugin class for NexiScope Jenkins Integration.
 * 
 * <p>This plugin provides real-time observability by streaming Jenkins pipeline
 * execution data to the NexiScope platform via WebSocket. It captures pipeline
 * lifecycle events, stage-level execution, and performance metrics.</p>
 * 
 * <h2>Features:</h2>
 * <ul>
 *   <li>Real-time event streaming via WebSocket</li>
 *   <li>Automatic reconnection with exponential backoff</li>
 *   <li>Event batching for performance</li>
 *   <li>Comprehensive event filtering</li>
 *   <li>Optional build log streaming</li>
 * </ul>
 * 
 * <h2>Lifecycle:</h2>
 * <p>The plugin is automatically started by Jenkins when the plugin is loaded.
 * It initializes the WebSocket client and registers event listeners via the
 * {@code @Extension} annotation mechanism.</p>
 * 
 * @author NexiScope Team
 * @version 1.0.0
 * @since 1.0.0
 * @see NexiScopeGlobalConfiguration
 * @see io.nexiscope.jenkins.plugin.websocket.WebSocketClient
 */
public class NexiScopePlugin extends Plugin {
    
    private static final Logger LOGGER = Logger.getLogger(NexiScopePlugin.class.getName());
    
    /**
     * Called by Jenkins when the plugin is started.
     * 
     * <p>Initializes the WebSocket client and prepares the plugin for operation.
     * Event listeners are automatically discovered and registered by Jenkins
     * via {@code @Extension} annotations.</p>
     * 
     * @throws Exception if plugin initialization fails
     */
    @Override
    public void start() throws Exception {
        super.start();
        LOGGER.info("Starting NexiScope Jenkins Plugin v1.0.0-SNAPSHOT");
        
        // Event listeners are automatically discovered and registered by Jenkins
        // via @Extension annotations. No manual registration needed.
        
        // Initialize WebSocket client
        initializeWebSocketClient();
        
        LOGGER.info("NexiScope Jenkins Plugin started successfully");
    }
    
    /**
     * Called by Jenkins when the plugin is stopped.
     * 
     * <p>Cleanly shuts down the WebSocket client and releases resources.
     * Event listeners are automatically cleaned up by Jenkins.</p>
     * 
     * @throws Exception if plugin shutdown fails
     */
    @Override
    public void stop() throws Exception {
        LOGGER.info("Stopping NexiScope Jenkins Plugin");
        
        // Close WebSocket connection
        cleanupWebSocketClient();
        
        // Event listeners are automatically cleaned up by Jenkins
        // No manual cleanup needed.
        
        super.stop();
        LOGGER.info("NexiScope Jenkins Plugin stopped");
    }
    
    /**
     * Initializes the WebSocket client for event streaming.
     * 
     * <p>Creates and configures the WebSocket client instance. If initialization
     * fails, the error is logged but does not prevent plugin startup.</p>
     */
    private void initializeWebSocketClient() {
        try {
            WebSocketClient.getInstance().initialize();
            LOGGER.info("WebSocket client initialized");
        } catch (Exception e) {
            LOGGER.warning("Failed to initialize WebSocket client: " + e.getMessage());
        }
    }
    
    /**
     * Cleans up the WebSocket client and closes the connection.
     * 
     * <p>Gracefully closes the WebSocket connection and releases associated
     * resources. Errors during cleanup are logged but do not prevent shutdown.</p>
     */
    private void cleanupWebSocketClient() {
        try {
            WebSocketClient.getInstance().close();
            LOGGER.info("WebSocket client closed");
        } catch (Exception e) {
            LOGGER.warning("Error closing WebSocket client: " + e.getMessage());
        }
    }
}

