package io.nexiscope.jenkins.plugin.listeners;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PipelineEventListener.
 * 
 * @author NexiScope Team
 */
class PipelineEventListenerTest {
    
    private PipelineEventListener listener;
    private WorkflowRun mockRun;
    private TaskListener mockListener;
    private NexiScopeGlobalConfiguration mockConfig;
    private WebSocketClient mockWebSocketClient;
    
    @BeforeEach
    void setUp() {
        listener = new PipelineEventListener();
        mockRun = mock(WorkflowRun.class);
        mockListener = mock(TaskListener.class);
        mockConfig = mock(NexiScopeGlobalConfiguration.class);
        mockWebSocketClient = mock(WebSocketClient.class);
        
        // Setup common mocks
        WorkflowJob mockJob = mock(WorkflowJob.class);
        when(mockRun.getParent()).thenReturn(mockJob);
        when(mockJob.getFullName()).thenReturn("test-job");
        when(mockRun.getFullDisplayName()).thenReturn("test-job #1");
        when(mockRun.getNumber()).thenReturn(1);
        when(mockRun.getId()).thenReturn("1");
        when(mockRun.getStartTimeInMillis()).thenReturn(1000L);
        when(mockRun.getDuration()).thenReturn(5000L);
        when(mockRun.getResult()).thenReturn(hudson.model.Result.SUCCESS);
    }
    
    @Test
    void testOnStartedWhenEnabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String eventJson = "{\"type\":\"PIPELINE_STARTED\"}";
            mapperMock.when(() -> EventMapper.createPipelineStartedEvent(mockRun)).thenReturn(eventJson);
            
            listener.onStarted(mockRun, mockListener);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testOnStartedWhenDisabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(false);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            listener.onStarted(mockRun, mockListener);
            
            verify(mockWebSocketClient, never()).sendEvent(anyString());
        }
    }
    
    @Test
    void testOnStartedWhenConfigNull() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(null);
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            listener.onStarted(mockRun, mockListener);
            
            verify(mockWebSocketClient, never()).sendEvent(anyString());
        }
    }
    
    @Test
    void testOnCompletedWhenEnabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String eventJson = "{\"type\":\"PIPELINE_COMPLETED\"}";
            mapperMock.when(() -> EventMapper.createPipelineCompletedEvent(mockRun)).thenReturn(eventJson);
            
            listener.onCompleted(mockRun, mockListener);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testOnFinalizedWithAborted() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            when(mockRun.getResult()).thenReturn(hudson.model.Result.ABORTED);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String eventJson = "{\"type\":\"PIPELINE_ABORTED\"}";
            mapperMock.when(() -> EventMapper.createPipelineAbortedEvent(mockRun)).thenReturn(eventJson);
            
            listener.onFinalized(mockRun);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testOnFinalizedWithNonAborted() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            when(mockRun.getResult()).thenReturn(hudson.model.Result.SUCCESS);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            listener.onFinalized(mockRun);
            
            verify(mockWebSocketClient, never()).sendEvent(anyString());
        }
    }
    
    @Test
    void testOnDeletedWhenEnabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String eventJson = "{\"type\":\"PIPELINE_DELETED\"}";
            mapperMock.when(() -> EventMapper.createPipelineDeletedEvent(mockRun)).thenReturn(eventJson);
            
            listener.onDeleted(mockRun);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testOnStartedHandlesException() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            mapperMock.when(() -> EventMapper.createPipelineStartedEvent(mockRun))
                .thenThrow(new RuntimeException("Test exception"));
            
            // Should not throw exception
            assertDoesNotThrow(() -> listener.onStarted(mockRun, mockListener));
        }
    }
}

