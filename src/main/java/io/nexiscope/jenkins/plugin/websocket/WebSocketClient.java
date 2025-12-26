package io.nexiscope.jenkins.plugin.websocket;

import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.errors.CircuitBreaker;
import io.nexiscope.jenkins.plugin.errors.ErrorHandler;
import io.nexiscope.jenkins.plugin.events.EventQueue;
import io.nexiscope.jenkins.plugin.filters.EventFilter;
import io.nexiscope.jenkins.plugin.performance.MemoryOptimizer;
import io.nexiscope.jenkins.plugin.security.CertificatePinningConfig;
import okhttp3.*;
import okio.ByteString;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * WebSocket client for connecting to NexiScope platform.
 * 
 * Implements singleton pattern to maintain a single connection per Jenkins instance.
 * Handles connection management, authentication, and automatic reconnection.
 * 
 * @author NexiScope Team
 */
public class WebSocketClient {
    
    private static final Logger LOGGER = Logger.getLogger(WebSocketClient.class.getName());
    
    // Reconnection configuration
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 5;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 300; // 5 minutes
    private static final double BACKOFF_MULTIPLIER = 1.5;
    private static final int MAX_RECONNECT_ATTEMPTS = -1; // -1 means unlimited
    
    private static WebSocketClient instance;
    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private boolean connected = false;
    private boolean authenticated = false;
    private String webSocketUrl;
    
    // Reconnection management
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledFuture<?> reconnectFuture;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private int currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;
    
    // Metrics
    private final AtomicLong totalReconnectAttempts = new AtomicLong(0);
    private final AtomicLong successfulReconnections = new AtomicLong(0);
    private final AtomicLong failedReconnections = new AtomicLong(0);
    private volatile long lastReconnectAttemptTime = 0;
    private volatile long lastSuccessfulConnectionTime = 0;
    
    // Event queue
    private EventQueue eventQueue;
    
    // Event batcher for performance optimization
    private EventBatcher eventBatcher;
    
    // Circuit breaker for connection failures
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    
    private WebSocketClient() {
        // Initialize HTTP client with certificate pinning (if enabled)
        httpClient = buildHttpClient();
        
        // Initialize reconnection executor
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NexiScope-WebSocket-Reconnect");
            t.setDaemon(true);
            return t;
        });
        
        // Initialize event queue
        initializeEventQueue();
        
        // Initialize event batcher
        initializeEventBatcher();
    }
    
    /**
     * Builds OkHttpClient with certificate pinning if enabled.
     */
    private OkHttpClient buildHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS);
        
        // Apply certificate pinning if enabled
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config != null && config.isCertificatePinningEnabled() && config.getCertificatePins() != null) {
            CertificatePinningConfig pinner = CertificatePinningConfig.fromConfig(config.getCertificatePins());
            okhttp3.CertificatePinner okHttpPinner = pinner.toOkHttpPinner();
            if (okHttpPinner != null) {
                builder.certificatePinner(okHttpPinner);
                LOGGER.info("Certificate pinning enabled with " + pinner.getPinCount() + " pin(s)");
            } else {
                LOGGER.warning("Certificate pinning is enabled but no valid pins found. Pinning disabled.");
            }
        }
        
        return builder.build();
    }
    
    /**
     * Rebuilds HTTP client when configuration changes (e.g., certificate pinning).
     */
    private void rebuildHttpClient() {
        httpClient = buildHttpClient();
        LOGGER.info("HTTP client rebuilt with updated configuration");
    }
    
    /**
     * Initializes or reinitializes the event batcher based on configuration.
     */
    private void initializeEventBatcher() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        
        if (config == null || !config.isEventBatchingEnabled()) {
            // Batching disabled, shutdown existing batcher if any
            if (eventBatcher != null) {
                LOGGER.info("Event batching disabled, shutting down batcher");
                eventBatcher.shutdown();
                eventBatcher = null;
            }
            return;
        }
        
        int batchSize = config.getBatchSize();
        int batchTimeoutMs = config.getBatchTimeoutMs();
        
        // If batcher exists and config changed, shutdown and recreate
        if (eventBatcher != null) {
            int currentBatchSize = eventBatcher.getMaxBatchSize();
            int currentTimeout = eventBatcher.getBatchTimeoutMs();
            
            if (currentBatchSize != batchSize || currentTimeout != batchTimeoutMs) {
                LOGGER.info("Batch configuration changed, reinitializing batcher");
                eventBatcher.shutdown();
                eventBatcher = null;
            }
        }
        
        // Create new batcher if needed
        if (eventBatcher == null) {
            eventBatcher = new EventBatcher(this::sendBatchedEvents, batchSize, batchTimeoutMs);
            LOGGER.info("Event batcher initialized with batchSize=" + batchSize + 
                       ", batchTimeoutMs=" + batchTimeoutMs);
        }
    }
    
    /**
     * Sends a batch of events as a single message.
     * Called by EventBatcher when a batch is ready to send.
     */
    private void sendBatchedEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        // Periodic memory check
        MemoryOptimizer.checkMemoryUsage();
        
        if (webSocket == null || !connected || !authenticated) {
            LOGGER.warning("Cannot send batch: not connected. Queueing " + events.size() + " events");
            // Queue each event individually
            events.forEach(this::queueEvent);
            return;
        }
        
        try {
            // Create batch message (array of events)
            StringBuilder batchJson = new StringBuilder("[");
            for (int i = 0; i < events.size(); i++) {
                if (i > 0) {
                    batchJson.append(",");
                }
                batchJson.append(events.get(i));
            }
            batchJson.append("]");
            
            String message = createBatchMessage(batchJson.toString());
            webSocket.send(message);
            LOGGER.fine("Batch of " + events.size() + " events sent to NexiScope platform");
        } catch (Exception e) {
            LOGGER.warning("Failed to send batch: " + e.getMessage());
            // Queue events for retry
            events.forEach(this::queueEvent);
        }
    }
    
    /**
     * Creates a batch message in the expected format.
     */
    private String createBatchMessage(String batchJson) {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        String instanceId = config != null ? config.getInstanceId() : "default-instance";
        
        return String.format("{\"type\":\"batch\",\"instanceId\":\"%s\",\"events\":%s}", 
                           instanceId, batchJson);
    }
    
    /**
     * Initializes or reinitializes the event queue based on configuration.
     */
    private void initializeEventQueue() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        int queueSize = config != null ? config.getQueueMaxSize() : 1000;
        
        // If queue exists and size changed, flush old queue and create new one
        if (eventQueue != null && eventQueue.getMaxSize() != queueSize) {
            LOGGER.info("Queue size changed from " + eventQueue.getMaxSize() + " to " + queueSize + ", reinitializing queue");
            // Note: Old events are lost when queue size changes
            // In future, we could migrate events to new queue
        }
        
        eventQueue = new EventQueue(queueSize);
        LOGGER.info("Event queue initialized with max size: " + queueSize);
    }
    
    /**
     * Gets the singleton instance.
     */
    public static synchronized WebSocketClient getInstance() {
        if (instance == null) {
            instance = new WebSocketClient();
        }
        return instance;
    }
    
    /**
     * Initializes the WebSocket client and establishes connection.
     */
    public void initialize() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config == null) {
            LOGGER.warning("NexiScopeGlobalConfiguration is null");
            return;
        }
        
        if (!config.isEnabled()) {
            LOGGER.info("NexiScope plugin is disabled");
            return;
        }
        
        if (!config.isValid()) {
            LOGGER.warning("NexiScope plugin configuration is invalid:");
            LOGGER.warning("  Platform URL: " + (config.getPlatformUrl() != null ? config.getPlatformUrl() : "null"));
            LOGGER.warning("  Auth Token: " + (config.getAuthToken() != null ? "***" : "null"));
            LOGGER.warning("  Instance ID: " + config.getInstanceId());
            return;
        }
        
        LOGGER.info("Configuration is valid, attempting connection...");
        connect();
    }
    
    /**
     * Establishes WebSocket connection.
     */
    private void connect() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config == null) {
            return;
        }
        
        // Check circuit breaker before attempting connection
        if (!circuitBreaker.allowConnection()) {
            LOGGER.warning("Circuit breaker is blocking connection attempts. Will retry after timeout.");
            // Schedule a reconnect attempt after circuit breaker timeout
            scheduleReconnect();
            return;
        }
        
        String platformUrl = config.getPlatformUrl();
        if (platformUrl == null || platformUrl.trim().isEmpty()) {
            ErrorHandler.handleError(
                ErrorHandler.ErrorCategory.CONFIGURATION,
                "Platform URL is not configured",
                "WebSocket connection"
            );
            return;
        }
        
        // Convert HTTPS URL to WSS URL
        webSocketUrl = convertToWebSocketUrl(platformUrl);
        
        Request request = new Request.Builder()
            .url(webSocketUrl)
            .build();
        
        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                connected = true;
                lastSuccessfulConnectionTime = System.currentTimeMillis();
                
                // Record success in circuit breaker
                circuitBreaker.recordSuccess();
                
                // Reset reconnection state on successful connection
                reconnecting.set(false);
                reconnectAttempts.set(0);
                currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;
                
                // Cancel any pending reconnection attempts
                cancelScheduledReconnect();
                
                LOGGER.info("WebSocket connection established");
                authenticate();
                
                // Flush queued events after authentication
                // We'll flush after authentication succeeds (in handleMessage)
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }
            
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                handleMessage(bytes.utf8());
            }
            
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                LOGGER.info("WebSocket closing: " + reason);
                connected = false;
                authenticated = false;
            }
            
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                LOGGER.info("WebSocket closed: " + reason);
                connected = false;
                authenticated = false;
                scheduleReconnect();
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connected = false;
                authenticated = false;
                
                // Check if this is a certificate pinning error
                if (t != null && isCertificatePinningError(t)) {
                    ErrorHandler.handleError(
                        ErrorHandler.ErrorCategory.CONNECTION,
                        t,
                        "Certificate pinning validation failed. The server's certificate does not match the pinned certificate(s)."
                    );
                    LOGGER.severe("Certificate pinning validation failed: " + t.getMessage());
                    LOGGER.severe("This may indicate a man-in-the-middle attack or certificate rotation. Please verify the certificate pins.");
                    // Don't retry on certificate pinning failures - this is a security issue
                    return;
                }
                
                ErrorHandler.ErrorInfo errorInfo = ErrorHandler.handleError(
                    ErrorHandler.ErrorCategory.CONNECTION, 
                    t, 
                    "WebSocket connection attempt"
                );
                
                // Record failure in circuit breaker
                circuitBreaker.recordFailure();
                
                // Only schedule reconnect if error is retryable and circuit breaker allows
                if (errorInfo.isRetryable() && circuitBreaker.allowConnection()) {
                    scheduleReconnect();
                } else {
                    if (!errorInfo.isRetryable()) {
                        LOGGER.severe("Connection error is not retryable. Manual intervention may be required.");
                    }
                    // If circuit breaker is blocking, it will allow retry after timeout
                }
            }
            
            /**
             * Checks if the error is related to certificate pinning validation.
             */
            private boolean isCertificatePinningError(Throwable t) {
                if (t == null) {
                    return false;
                }
                
                String message = t.getMessage();
                if (message == null) {
                    message = "";
                }
                message = message.toLowerCase();
                
                // Check for certificate pinning related errors
                return message.contains("certificate pinning") ||
                       message.contains("pin verification failed") ||
                       message.contains("certificate chain") ||
                       (t.getClass().getName().contains("SSL") && message.contains("pin"));
            }
        });
    }
    
    /**
     * Authenticates with the NexiScope platform.
     */
    private void authenticate() {
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config == null) {
            return;
        }
        
        String authMessage = createAuthMessage(config);
        if (webSocket != null && connected) {
            webSocket.send(authMessage);
            LOGGER.info("Authentication message sent");
        }
    }
    
    /**
     * Creates authentication message.
     */
    private String createAuthMessage(NexiScopeGlobalConfiguration config) {
        String token = config.getAuthToken();
        
        // Format: {"type":"AUTH","timestamp":"...","payload":{"token":"...","instanceId":"..."}}
        return String.format(
            "{\"type\":\"AUTH\",\"timestamp\":\"%s\",\"payload\":{\"token\":\"%s\",\"instanceId\":\"%s\"}}",
            java.time.Instant.now().toString(),
            token,
            config.getInstanceId()
        );
    }
    
    /**
     * Handles incoming WebSocket messages.
     */
    private void handleMessage(String message) {
        LOGGER.fine("Received message: " + message);
        
        // Parse message and handle different types (AUTH_SUCCESS, ACK, ERROR, PONG)
        if (message.contains("AUTH_SUCCESS")) {
            authenticated = true;
            successfulReconnections.incrementAndGet();
            LOGGER.info("Authentication successful");
            
            // Audit log successful authentication
            io.nexiscope.jenkins.plugin.security.AuditLogger.logSuccess(
                io.nexiscope.jenkins.plugin.security.AuditLogger.EventType.AUTH_SUCCESS,
                "system",
                "WebSocket authentication successful"
            );
            
            // Flush queued events now that we're authenticated
            flushQueuedEvents();
        } else if (message.contains("AUTH_FAILED") || message.contains("NOT_AUTHENTICATED")) {
            authenticated = false;
            
            // Audit log failed authentication
            io.nexiscope.jenkins.plugin.security.AuditLogger.logFailure(
                io.nexiscope.jenkins.plugin.security.AuditLogger.EventType.AUTH_FAILURE,
                "system",
                "WebSocket authentication failed: " + message
            );
            
            ErrorHandler.handleError(
                ErrorHandler.ErrorCategory.AUTHENTICATION,
                "Authentication failed: " + message,
                "WebSocket authentication"
            );
            // Schedule reconnection on auth failure (may be token issue, retry in case token was updated)
            scheduleReconnect();
        } else if (message.contains("ACK")) {
            LOGGER.fine("Event acknowledged");
        } else if (message.contains("ERROR")) {
            ErrorHandler.handleError(
                ErrorHandler.ErrorCategory.SERVER,
                "Server error: " + message,
                "WebSocket message handling"
            );
        } else if (message.contains("PONG")) {
            LOGGER.fine("Received pong");
        }
    }
    
    /**
     * Sends an event to the NexiScope platform.
     * If not connected, the event is queued for later transmission.
     * Events are filtered before sending if filtering is enabled.
     */
    public void sendEvent(String eventJson) {
        // Rate limiting check (very generous limit to prevent abuse)
        if (!io.nexiscope.jenkins.plugin.security.RateLimiter.isAllowed(
                io.nexiscope.jenkins.plugin.security.RateLimiter.Operation.EVENT_SEND)) {
            LOGGER.warning("Event send rate limit exceeded. Event will be queued.");
            queueEvent(eventJson);
            return;
        }
        
        // Apply event filtering if enabled
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config != null && config.isEventFilteringEnabled()) {
            EventFilter filter = EventFilter.fromConfig(config);
            if (!filter.shouldSendEvent(eventJson)) {
                LOGGER.fine("Event filtered out and not sent");
                return; // Event filtered, don't send or queue
            }
        }
        
        if (webSocket == null) {
            LOGGER.warning("WebSocket is null, queueing event. Connection not established.");
            queueEvent(eventJson);
            return;
        }
        
        if (!connected || !authenticated) {
            LOGGER.warning("Not connected or authenticated, queueing event. Connected: " + connected + ", Authenticated: " + authenticated);
            queueEvent(eventJson);
            return;
        }
        
        // Use batching if enabled
        if (config != null && config.isEventBatchingEnabled() && eventBatcher != null) {
            eventBatcher.addEvent(eventJson);
            LOGGER.finest("Event added to batch");
            return;
        }
        
        // Send event directly (batching disabled)
        try {
            String message = createEventMessage(eventJson);
            webSocket.send(message);
            LOGGER.fine("Event sent to NexiScope platform");
        } catch (Exception e) {
            ErrorHandler.ErrorInfo errorInfo = ErrorHandler.handleError(
                ErrorHandler.ErrorCategory.EVENT_SENDING,
                e,
                "Sending event to NexiScope"
            );
            // Queue event for retry (graceful degradation)
            queueEvent(eventJson);
        }
    }
    
    /**
     * Queues an event for later transmission.
     */
    private void queueEvent(String eventJson) {
        if (eventQueue == null) {
            initializeEventQueue();
        }
        
        boolean queued = eventQueue.enqueue(eventJson);
        if (queued) {
            LOGGER.fine("Event queued. Queue size: " + eventQueue.size());
        } else {
            LOGGER.warning("Failed to queue event (queue may be disabled or full)");
        }
    }
    
    /**
     * Flushes all queued events to the NexiScope platform.
     */
    private void flushQueuedEvents() {
        if (eventQueue == null || eventQueue.isEmpty()) {
            return;
        }
        
        LOGGER.info("Flushing " + eventQueue.size() + " queued events");
        
        List<EventQueue.QueuedEvent> events = eventQueue.flush();
        int sent = 0;
        int failed = 0;
        boolean connectionLost = false;
        
        for (int i = 0; i < events.size(); i++) {
            EventQueue.QueuedEvent queuedEvent = events.get(i);
            
            // Check connection before each send
            if (webSocket == null || !connected || !authenticated) {
                LOGGER.warning("Connection lost during queue flush, re-queueing remaining " + (events.size() - i) + " events");
                connectionLost = true;
                // Re-queue remaining events
                for (int j = i; j < events.size(); j++) {
                    eventQueue.enqueue(events.get(j).getEventJson());
                }
                break;
            }
            
            try {
                String message = createEventMessage(queuedEvent.getEventJson());
                webSocket.send(message);
                sent++;
            } catch (Exception e) {
                ErrorHandler.ErrorInfo errorInfo = ErrorHandler.handleError(
                    ErrorHandler.ErrorCategory.EVENT_SENDING,
                    e,
                    "Flushing queued event"
                );
                failed++;
                // Re-queue failed event if retryable, otherwise drop it
                if (errorInfo.isRetryable()) {
                    eventQueue.enqueue(queuedEvent.getEventJson());
                } else {
                    LOGGER.warning("Dropping non-retryable event from queue");
                }
            }
        }
        
        if (connectionLost) {
            LOGGER.warning("Queue flush interrupted due to connection loss: " + sent + " sent, " + failed + " failed, " + (events.size() - sent - failed) + " re-queued");
        } else {
            LOGGER.info("Queue flush complete: " + sent + " sent, " + failed + " failed");
        }
    }
    
    /**
     * Creates event message in protocol format.
     */
    private String createEventMessage(String eventJson) {
        // Format: {"type":"EVENT","timestamp":"...","payload":{...}}
        return String.format(
            "{\"type\":\"EVENT\",\"timestamp\":\"%s\",\"payload\":%s}",
            java.time.Instant.now().toString(),
            eventJson
        );
    }
    
    /**
     * Converts HTTPS URL to WSS URL.
     */
    private String convertToWebSocketUrl(String httpsUrl) {
        String url = httpsUrl.trim();
        if (url.startsWith("https://")) {
            url = url.replace("https://", "wss://");
        } else if (url.startsWith("http://")) {
            url = url.replace("http://", "ws://");
        }
        
        // Append /events endpoint if not present
        if (!url.endsWith("/events")) {
            url = url + (url.endsWith("/") ? "events" : "/events");
        }
        
        return url;
    }
    
    /**
     * Schedules reconnection attempt with exponential backoff.
     */
    private void scheduleReconnect() {
        // Check if plugin is still enabled
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config == null || !config.isEnabled()) {
            LOGGER.info("Plugin is disabled, not scheduling reconnection");
            return;
        }
        
        // Prevent multiple simultaneous reconnection attempts
        if (!reconnecting.compareAndSet(false, true)) {
            LOGGER.fine("Reconnection already scheduled, skipping");
            return;
        }
        
        // Check max reconnect attempts (if set)
        if (MAX_RECONNECT_ATTEMPTS > 0 && reconnectAttempts.get() >= MAX_RECONNECT_ATTEMPTS) {
            LOGGER.severe("Maximum reconnection attempts (" + MAX_RECONNECT_ATTEMPTS + ") reached. Stopping reconnection attempts.");
            reconnecting.set(false);
            return;
        }
        
        int attempt = reconnectAttempts.incrementAndGet();
        totalReconnectAttempts.incrementAndGet();
        lastReconnectAttemptTime = System.currentTimeMillis();
        
        LOGGER.info(String.format(
            "Scheduling reconnection attempt #%d in %d seconds (exponential backoff)",
            attempt,
            currentReconnectDelay
        ));
        
        // Schedule reconnection with current delay
        reconnectFuture = reconnectExecutor.schedule(() -> {
            try {
                LOGGER.info("Attempting to reconnect...");
                connect();
            } catch (Exception e) {
                ErrorHandler.handleError(
                    ErrorHandler.ErrorCategory.CONNECTION,
                    e,
                    "Reconnection attempt"
                );
                failedReconnections.incrementAndGet();
                
                // Calculate next delay with exponential backoff
                currentReconnectDelay = (int) Math.min(
                    currentReconnectDelay * BACKOFF_MULTIPLIER,
                    MAX_RECONNECT_DELAY_SECONDS
                );
                
                // Reset reconnecting flag and schedule next attempt
                reconnecting.set(false);
                scheduleReconnect();
            }
        }, currentReconnectDelay, TimeUnit.SECONDS);
    }
    
    /**
     * Cancels any scheduled reconnection attempt.
     */
    private void cancelScheduledReconnect() {
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
        reconnecting.set(false);
    }
    
    /**
     * Forces an immediate reconnection attempt.
     * Useful when configuration changes.
     */
    public void reconnect() {
        LOGGER.info("Forcing immediate reconnection");
        
        // Reinitialize event queue if queue size changed
        NexiScopeGlobalConfiguration config = NexiScopeGlobalConfiguration.get();
        if (config != null && (eventQueue == null || eventQueue.getMaxSize() != config.getQueueMaxSize())) {
            initializeEventQueue();
        }
        
        // Reinitialize event batcher if configuration changed
        initializeEventBatcher();
        
        // Rebuild HTTP client if certificate pinning configuration changed
        rebuildHttpClient();
        
        // Reset circuit breaker when reconnecting (configuration might have changed)
        circuitBreaker.reset();
        LOGGER.info("Circuit breaker reset for reconnection");
        
        // Close existing connection if any
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Reconnecting");
            } catch (Exception e) {
                LOGGER.warning("Error closing existing connection: " + e.getMessage());
            }
            webSocket = null;
        }
        
        connected = false;
        authenticated = false;
        
        // Reset reconnection state
        cancelScheduledReconnect();
        reconnectAttempts.set(0);
        currentReconnectDelay = INITIAL_RECONNECT_DELAY_SECONDS;
        
        // Attempt immediate connection
        connect();
    }
    
    /**
     * Closes the WebSocket connection and cleans up resources.
     */
    public void close() {
        LOGGER.info("Closing WebSocket client");
        
        // Clear memory optimizer caches
        MemoryOptimizer.clearCaches();
        
        // Flush and shutdown event batcher
        if (eventBatcher != null) {
            LOGGER.info("Shutting down event batcher");
            eventBatcher.shutdown();
            eventBatcher = null;
        }
        
        // Cancel any pending reconnection attempts
        cancelScheduledReconnect();
        
        // Close WebSocket connection
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Plugin shutdown");
            } catch (Exception e) {
                LOGGER.warning("Error closing WebSocket: " + e.getMessage());
            }
            webSocket = null;
        }
        
        connected = false;
        authenticated = false;
        
        // Shutdown executor
        if (reconnectExecutor != null && !reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdown();
            try {
                if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    reconnectExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Checks if the WebSocket is connected.
     */
    public boolean isConnected() {
        return connected && authenticated;
    }
    
    /**
     * Gets event queue metrics.
     * 
     * @return EventQueue.QueueMetrics object with current queue metrics, or null if queue not initialized
     */
    public EventQueue.QueueMetrics getQueueMetrics() {
        return eventQueue != null ? eventQueue.getMetrics() : null;
    }
    
    /**
     * Gets reconnection metrics.
     * 
     * @return ReconnectionMetrics object with current metrics
     */
    public ReconnectionMetrics getReconnectionMetrics() {
        return new ReconnectionMetrics(
            totalReconnectAttempts.get(),
            successfulReconnections.get(),
            failedReconnections.get(),
            reconnectAttempts.get(),
            currentReconnectDelay,
            lastReconnectAttemptTime,
            lastSuccessfulConnectionTime,
            reconnecting.get()
        );
    }
    
    /**
     * Reconnection metrics data class.
     */
    public static class ReconnectionMetrics {
        private final long totalAttempts;
        private final long successfulReconnections;
        private final long failedReconnections;
        private final int currentAttempt;
        private final int currentDelay;
        private final long lastAttemptTime;
        private final long lastSuccessfulTime;
        private final boolean isReconnecting;
        
        public ReconnectionMetrics(long totalAttempts, long successfulReconnections, 
                                  long failedReconnections, int currentAttempt, 
                                  int currentDelay, long lastAttemptTime, 
                                  long lastSuccessfulTime, boolean isReconnecting) {
            this.totalAttempts = totalAttempts;
            this.successfulReconnections = successfulReconnections;
            this.failedReconnections = failedReconnections;
            this.currentAttempt = currentAttempt;
            this.currentDelay = currentDelay;
            this.lastAttemptTime = lastAttemptTime;
            this.lastSuccessfulTime = lastSuccessfulTime;
            this.isReconnecting = isReconnecting;
        }
        
        public long getTotalAttempts() { return totalAttempts; }
        public long getSuccessfulReconnections() { return successfulReconnections; }
        public long getFailedReconnections() { return failedReconnections; }
        public int getCurrentAttempt() { return currentAttempt; }
        public int getCurrentDelay() { return currentDelay; }
        public long getLastAttemptTime() { return lastAttemptTime; }
        public long getLastSuccessfulTime() { return lastSuccessfulTime; }
        public boolean isReconnecting() { return isReconnecting; }
        
        @Override
        public String toString() {
            return String.format(
                "ReconnectionMetrics{attempts=%d, successful=%d, failed=%d, currentAttempt=%d, delay=%ds, reconnecting=%s}",
                totalAttempts, successfulReconnections, failedReconnections, currentAttempt, currentDelay, isReconnecting
            );
        }
    }
}

