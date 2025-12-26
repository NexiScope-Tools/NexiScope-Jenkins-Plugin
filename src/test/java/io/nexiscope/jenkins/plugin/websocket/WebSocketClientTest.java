package io.nexiscope.jenkins.plugin.websocket;

import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketClient.
 * 
 * Note: OkHttpClient and WebSocket are final classes and cannot be mocked.
 * These tests focus on testable behavior without mocking network components.
 * 
 * @author NexiScope Team
 */
class WebSocketClientTest {
    
    private NexiScopeGlobalConfiguration mockConfig;
    private MockedStatic<NexiScopeGlobalConfiguration> configMock;
    
    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton instance
        resetSingleton();
        
        // Setup mocks
        mockConfig = mock(NexiScopeGlobalConfiguration.class);
        when(mockConfig.isEnabled()).thenReturn(true);
        when(mockConfig.isValid()).thenReturn(true);
        when(mockConfig.getPlatformUrl()).thenReturn("https://platform.nexiscope.com");
        when(mockConfig.getAuthToken()).thenReturn("test-token");
        when(mockConfig.getInstanceId()).thenReturn("test-instance");
        when(mockConfig.getQueueMaxSize()).thenReturn(1000);
        
        configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
        configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (configMock != null) {
            configMock.close();
        }
        // Note: We don't call getInstance() here as it creates OkHttpClient
        // which requires Kotlin runtime. Cleanup is handled by resetSingleton.
        resetSingleton();
    }
    
    /**
     * Resets the singleton instance using reflection.
     */
    private void resetSingleton() throws Exception {
        Field instanceField = WebSocketClient.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
    
    @Test
    void testReconnectionMetricsClass() {
        // Test the ReconnectionMetrics data class
        WebSocketClient.ReconnectionMetrics metrics = new WebSocketClient.ReconnectionMetrics(
            10, 5, 5, 3, 15, 1000L, 2000L, false
        );
        
        assertEquals(10, metrics.getTotalAttempts());
        assertEquals(5, metrics.getSuccessfulReconnections());
        assertEquals(5, metrics.getFailedReconnections());
        assertEquals(3, metrics.getCurrentAttempt());
        assertEquals(15, metrics.getCurrentDelay());
        assertEquals(1000L, metrics.getLastAttemptTime());
        assertEquals(2000L, metrics.getLastSuccessfulTime());
        assertFalse(metrics.isReconnecting());
        
        // Test toString
        String str = metrics.toString();
        assertNotNull(str);
        assertTrue(str.contains("ReconnectionMetrics"));
    }
    
    @Test
    void testReconnectionMetricsWithReconnecting() {
        WebSocketClient.ReconnectionMetrics metrics = new WebSocketClient.ReconnectionMetrics(
            5, 2, 3, 1, 10, 500L, 1000L, true
        );
        
        assertTrue(metrics.isReconnecting());
        assertEquals(5, metrics.getTotalAttempts());
    }
    
    // Note: URL conversion and other methods require instance creation which creates OkHttpClient
    // These are better tested through integration tests or by testing the actual behavior
    // in a real Jenkins environment
}

