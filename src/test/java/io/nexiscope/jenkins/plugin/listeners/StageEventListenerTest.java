package io.nexiscope.jenkins.plugin.listeners;

import hudson.model.TaskListener;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StageEventListener.
 * 
 * @author NexiScope Team
 */
class StageEventListenerTest {
    
    private StageEventListener listener;
    private WorkflowRun mockRun;
    private FlowNode mockNode;
    private TaskListener mockListener;
    private NexiScopeGlobalConfiguration mockConfig;
    private WebSocketClient mockWebSocketClient;
    
    @BeforeEach
    void setUp() {
        listener = new StageEventListener();
        mockRun = mock(WorkflowRun.class);
        mockNode = mock(FlowNode.class);
        mockListener = mock(TaskListener.class);
        mockConfig = mock(NexiScopeGlobalConfiguration.class);
        mockWebSocketClient = mock(WebSocketClient.class);
        
        // Setup common mocks
        WorkflowJob mockJob = mock(WorkflowJob.class);
        when(mockRun.getParent()).thenReturn(mockJob);
        when(mockJob.getFullName()).thenReturn("test-job");
        when(mockRun.getNumber()).thenReturn(1);
        when(mockRun.getId()).thenReturn("1");
        when(mockNode.getDisplayName()).thenReturn("Test Stage");
        when(mockNode.getId()).thenReturn("node-1");
    }
    
    @Test
    void testOnStageStartedWhenEnabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String eventJson = "{\"type\":\"STAGE_STARTED\"}";
            mapperMock.when(() -> EventMapper.createStageStartedEvent(mockRun, mockNode)).thenReturn(eventJson);
            
            listener.onStageStarted(mockRun, mockNode, mockListener);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testOnStageStartedWhenDisabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(false);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            listener.onStageStarted(mockRun, mockNode, mockListener);
            
            verify(mockWebSocketClient, never()).sendEvent(anyString());
        }
    }
    
    @Test
    void testOnStageCompletedWhenEnabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String eventJson = "{\"type\":\"STAGE_COMPLETED\"}";
            // The 3-parameter method calls the 4-parameter method with duration 0
            mapperMock.when(() -> EventMapper.createStageCompletedEvent(mockRun, mockNode, 0)).thenReturn(eventJson);
            
            listener.onStageCompleted(mockRun, mockNode, mockListener);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testOnStageFailedWhenEnabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            Throwable error = new RuntimeException("Test error");
            String eventJson = "{\"type\":\"STAGE_FAILED\"}";
            mapperMock.when(() -> EventMapper.createStageFailedEvent(mockRun, mockNode, error)).thenReturn(eventJson);
            
            listener.onStageFailed(mockRun, mockNode, mockListener, error);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testOnStageFailedWithNullError() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String eventJson = "{\"type\":\"STAGE_FAILED\"}";
            mapperMock.when(() -> EventMapper.createStageFailedEvent(mockRun, mockNode, null)).thenReturn(eventJson);
            
            listener.onStageFailed(mockRun, mockNode, mockListener, null);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testNotifyOfNewStep() {
        Step mockStep = mock(Step.class);
        StepContext mockContext = mock(StepContext.class);
        
        // Should not throw exception
        assertDoesNotThrow(() -> listener.notifyOfNewStep(mockStep, mockContext));
    }
    
    @Test
    void testOnStageStartedHandlesException() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            mapperMock.when(() -> EventMapper.createStageStartedEvent(mockRun, mockNode))
                .thenThrow(new RuntimeException("Test exception"));
            
            // Should not throw exception
            assertDoesNotThrow(() -> listener.onStageStarted(mockRun, mockNode, mockListener));
        }
    }
}

