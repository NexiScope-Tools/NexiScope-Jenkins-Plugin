package io.nexiscope.jenkins.plugin.websocket;

import okhttp3.*;
import okio.ByteString;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Utility class for testing WebSocket connections to NexiScope platform.
 * 
 * Creates a temporary connection, validates authentication, and returns results.
 * 
 * @author NexiScope Team
 */
public class ConnectionTester {
    
    private static final Logger LOGGER = Logger.getLogger(ConnectionTester.class.getName());
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;
    private static final int AUTHENTICATION_TIMEOUT_SECONDS = 5;
    
    /**
     * Result of a connection test.
     */
    public static class TestResult {
        private final boolean success;
        private final String message;
        private final String errorDetails;
        
        public TestResult(boolean success, String message, String errorDetails) {
            this.success = success;
            this.message = message;
            this.errorDetails = errorDetails;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getErrorDetails() {
            return errorDetails;
        }
        
        public static TestResult success(String message) {
            return new TestResult(true, message, null);
        }
        
        public static TestResult failure(String message, String errorDetails) {
            return new TestResult(false, message, errorDetails);
        }
    }
    
    /**
     * Tests a WebSocket connection to the NexiScope platform.
     * 
     * @param platformUrl The platform URL
     * @param authToken The authentication token
     * @param instanceId The instance ID
     * @return TestResult with success status and message
     */
    public static TestResult testConnection(String platformUrl, String authToken, String instanceId) {
        if (platformUrl == null || platformUrl.trim().isEmpty()) {
            return TestResult.failure("Platform URL is required", "Platform URL cannot be empty");
        }
        
        // Token is required
        if (authToken == null || authToken.trim().isEmpty()) {
            return TestResult.failure("Authentication token is required", "Authentication token cannot be empty");
        }
        
        // Create final variables for use in inner class
        final String finalAuthToken = authToken;
        final String finalInstanceId = (instanceId == null || instanceId.trim().isEmpty()) 
            ? "default-instance" 
            : instanceId;
        
        // Validate URL format
        String url = platformUrl.trim().toLowerCase();
        if (!url.startsWith("https://") && !url.startsWith("wss://")) {
            return TestResult.failure(
                "Invalid platform URL format",
                "URL must start with https:// or wss://"
            );
        }
        
        // Convert to WebSocket URL
        String webSocketUrl = convertToWebSocketUrl(platformUrl.trim());
        
        // Get config for certificate pinning
        io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration config = 
            io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration.get();
        
        // Create OkHttpClient with timeout and certificate pinning (if enabled)
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(5, TimeUnit.SECONDS);
        
        // Apply certificate pinning if enabled
        if (config != null && config.isCertificatePinningEnabled() && config.getCertificatePins() != null) {
            io.nexiscope.jenkins.plugin.security.CertificatePinningConfig pinner = 
                io.nexiscope.jenkins.plugin.security.CertificatePinningConfig.fromConfig(config.getCertificatePins());
            okhttp3.CertificatePinner okHttpPinner = pinner.toOkHttpPinner();
            if (okHttpPinner != null) {
                clientBuilder.certificatePinner(okHttpPinner);
            }
        }
        
        OkHttpClient client = clientBuilder.build();
        
        // Synchronization objects
        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch authLatch = new CountDownLatch(1);
        AtomicReference<TestResult> resultRef = new AtomicReference<>();
        AtomicBoolean authenticated = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        
        Request request = new Request.Builder()
            .url(webSocketUrl)
            .build();
        
        WebSocket webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                LOGGER.fine("Test connection opened");
                connectionLatch.countDown();
                
                // Send authentication message
                String authMessage = createAuthMessage(finalAuthToken, finalInstanceId);
                webSocket.send(authMessage);
                
                // Set timeout for authentication
                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                executor.schedule(() -> {
                    if (!authenticated.get()) {
                        errorMessage.set("Authentication timeout - no response from server");
                        authLatch.countDown();
                        webSocket.close(1000, "Test timeout");
                    }
                }, AUTHENTICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                LOGGER.fine("Test connection received message: " + text);
                
                if (text.contains("AUTH_SUCCESS")) {
                    authenticated.set(true);
                    resultRef.set(TestResult.success("Connection test successful! Platform is reachable and authentication is valid."));
                    authLatch.countDown();
                    webSocket.close(1000, "Test complete");
                } else if (text.contains("AUTH_FAILED") || text.contains("NOT_AUTHENTICATED")) {
                    authenticated.set(false);
                    resultRef.set(TestResult.failure(
                        "Authentication failed",
                        "The server rejected the authentication token. Please verify your token is correct and has not expired."
                    ));
                    authLatch.countDown();
                    webSocket.close(1000, "Auth failed");
                } else if (text.contains("ERROR")) {
                    String error = extractErrorMessage(text);
                    resultRef.set(TestResult.failure(
                        "Server returned an error",
                        error != null ? error : "Unknown error from server"
                    ));
                    authLatch.countDown();
                    webSocket.close(1000, "Server error");
                }
            }
            
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                onMessage(webSocket, bytes.utf8());
            }
            
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                LOGGER.fine("Test connection closing: " + reason);
            }
            
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                LOGGER.fine("Test connection closed: " + reason);
                connectionLatch.countDown();
                authLatch.countDown();
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String errorMsg = t.getMessage();
                String details = buildFailureDetails(t, response);
                
                LOGGER.warning("Test connection failed: " + errorMsg);
                
                if (resultRef.get() == null) {
                    resultRef.set(TestResult.failure(
                        "Connection failed: " + getFailureMessage(t),
                        details
                    ));
                }
                
                connectionLatch.countDown();
                authLatch.countDown();
            }
        });
        
        // Wait for connection with timeout
        try {
            boolean connected = connectionLatch.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!connected) {
                webSocket.close(1000, "Connection timeout");
                return TestResult.failure(
                    "Connection timeout",
                    "Could not establish connection within " + CONNECTION_TIMEOUT_SECONDS + " seconds. " +
                    "Please check that the platform URL is correct and the server is reachable."
                );
            }
            
            // Wait for authentication with timeout
            boolean authComplete = authLatch.await(AUTHENTICATION_TIMEOUT_SECONDS + 2, TimeUnit.SECONDS);
            if (!authComplete) {
                webSocket.close(1000, "Authentication timeout");
                if (resultRef.get() == null) {
                    return TestResult.failure(
                        "Authentication timeout",
                        "No response from server after " + AUTHENTICATION_TIMEOUT_SECONDS + " seconds. " +
                        "The connection was established but authentication did not complete."
                    );
                }
            }
            
            // Return result if available
            TestResult result = resultRef.get();
            if (result != null) {
                return result;
            }
            
            // If we got here, connection opened but no auth response
            return TestResult.failure(
                "Authentication incomplete",
                "Connection was established but authentication did not complete. " +
                "Please check your authentication token."
            );
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            webSocket.close(1000, "Interrupted");
            return TestResult.failure(
                "Test interrupted",
                "The connection test was interrupted: " + e.getMessage()
            );
        } finally {
            // Ensure WebSocket is closed
            try {
                webSocket.close(1000, "Test complete");
            } catch (Exception e) {
                // Ignore
            }
            client.dispatcher().executorService().shutdown();
        }
    }
    
    /**
     * Converts HTTPS URL to WSS URL.
     */
    private static String convertToWebSocketUrl(String httpsUrl) {
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
     * Creates authentication message.
     */
    private static String createAuthMessage(String authToken, String instanceId) {
        return String.format(
            "{\"type\":\"AUTH\",\"timestamp\":\"%s\",\"payload\":{\"token\":\"%s\",\"instanceId\":\"%s\"}}",
            java.time.Instant.now().toString(),
            authToken,
            instanceId
        );
    }
    
    /**
     * Extracts error message from server response.
     */
    private static String extractErrorMessage(String message) {
        // Try to parse JSON error message
        try {
            if (message.contains("\"message\"")) {
                int start = message.indexOf("\"message\"") + 10;
                int end = message.indexOf("\"", start);
                if (end > start) {
                    return message.substring(start, end);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return null;
    }
    
    /**
     * Gets user-friendly failure message from exception.
     */
    private static String getFailureMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            return t.getClass().getSimpleName();
        }
        
        // Provide user-friendly messages for common errors
        if (message.contains("Connection refused") || message.contains("connect refused")) {
            return "Connection refused - server may be down or URL is incorrect";
        } else if (message.contains("timeout") || message.contains("Timeout")) {
            return "Connection timeout - server did not respond";
        } else if (message.contains("SSL") || message.contains("TLS") || message.contains("certificate")) {
            return "SSL/TLS error - certificate validation failed";
        } else if (message.contains("UnknownHostException") || message.contains("unknown host")) {
            return "Unknown host - URL hostname cannot be resolved";
        } else if (message.contains("Network is unreachable")) {
            return "Network unreachable - check network connectivity";
        }
        
        return message;
    }
    
    /**
     * Builds detailed failure information.
     */
    private static String buildFailureDetails(Throwable t, Response response) {
        StringBuilder details = new StringBuilder();
        
        if (response != null) {
            details.append("HTTP Status: ").append(response.code()).append(" ").append(response.message()).append("\n");
        }
        
        details.append("Error: ").append(t.getClass().getSimpleName()).append("\n");
        details.append("Message: ").append(t.getMessage());
        
        // Add common troubleshooting tips
        details.append("\n\nTroubleshooting tips:\n");
        details.append("- Verify the platform URL is correct\n");
        details.append("- Check that the server is reachable from this Jenkins instance\n");
        details.append("- Verify firewall rules allow outbound connections on port 443\n");
        details.append("- Check SSL certificate validity if using HTTPS/WSS");
        
        return details.toString();
    }
}

