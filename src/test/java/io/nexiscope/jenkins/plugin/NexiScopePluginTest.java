package io.nexiscope.jenkins.plugin;

import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NexiScopePlugin.
 * 
 * @author NexiScope Team
 */
public class NexiScopePluginTest {
    
    private NexiScopePlugin plugin;
    
    @BeforeEach
    public void setUp() {
        plugin = new NexiScopePlugin();
    }
    
    @Test
    public void testPluginExists() {
        assertNotNull(NexiScopePlugin.class);
        assertNotNull(plugin);
    }
    
    @Test
    public void testStartInitializesWebSocketClient() throws Exception {
        try (MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            WebSocketClient mockClient = mock(WebSocketClient.class);
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockClient);
            
            plugin.start();
            
            verify(mockClient, times(1)).initialize();
        }
    }
    
    @Test
    public void testStartHandlesException() throws Exception {
        try (MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            WebSocketClient mockClient = mock(WebSocketClient.class);
            doThrow(new RuntimeException("Connection error")).when(mockClient).initialize();
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockClient);
            
            // Should not throw exception
            assertDoesNotThrow(() -> plugin.start());
        }
    }
    
    @Test
    public void testStopClosesWebSocketClient() throws Exception {
        try (MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            WebSocketClient mockClient = mock(WebSocketClient.class);
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockClient);
            
            plugin.stop();
            
            verify(mockClient, times(1)).close();
        }
    }
    
    @Test
    public void testStopHandlesException() throws Exception {
        try (MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            WebSocketClient mockClient = mock(WebSocketClient.class);
            doThrow(new RuntimeException("Close error")).when(mockClient).close();
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockClient);
            
            // Should not throw exception
            assertDoesNotThrow(() -> plugin.stop());
        }
    }
}

