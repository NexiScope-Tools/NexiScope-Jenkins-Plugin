package io.nexiscope.jenkins.plugin.listeners;

import hudson.model.Queue;
import hudson.model.Run;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import io.nexiscope.jenkins.plugin.events.EventMapper;
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FlowNodeEventListener.
 * 
 * @author NexiScope Team
 */
class FlowNodeEventListenerTest {
    
    private FlowNodeEventListener listener;
    private FlowNode mockNode;
    private FlowExecution mockExecution;
    private WorkflowRun mockRun;
    private NexiScopeGlobalConfiguration mockConfig;
    private WebSocketClient mockWebSocketClient;
    
    @BeforeEach
    void setUp() {
        listener = new FlowNodeEventListener();
        mockNode = mock(FlowNode.class);
        mockExecution = mock(FlowExecution.class);
        mockRun = mock(WorkflowRun.class);
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
        when(mockNode.getExecution()).thenReturn(mockExecution);
        
        FlowExecutionOwner mockOwner = mock(FlowExecutionOwner.class);
        when(mockExecution.getOwner()).thenReturn(mockOwner);
        // WorkflowRun implements Queue.Executable, so we can use it directly
        try {
            when(mockOwner.getExecutable()).thenReturn(mockRun);
        } catch (java.io.IOException e) {
            // Mock doesn't actually throw, but method signature requires handling
        }
    }
    
    @Test
    void testOnNewHeadWithSignificantNode() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class);
             MockedStatic<hudson.ExtensionList> extListMock = Mockito.mockStatic(hudson.ExtensionList.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            // Use a non-stage node name to test flow node event
            // The node should not match stage detection patterns (no "Stage" in name)
            when(mockNode.getDisplayName()).thenReturn("Step: Build");
            
            // Mock StageEventListener lookup to return empty (so it falls back to flow node event)
            hudson.ExtensionList<StageEventListener> mockExtList = mock(hudson.ExtensionList.class);
            when(mockExtList.isEmpty()).thenReturn(true);
            extListMock.when(() -> hudson.ExtensionList.lookup(StageEventListener.class)).thenReturn(mockExtList);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String eventJson = "{\"type\":\"FLOW_NODE\"}";
            mapperMock.when(() -> EventMapper.createFlowNodeEvent(mockRun, mockNode)).thenReturn(eventJson);
            
            listener.onNewHead(mockNode);
            
            verify(mockWebSocketClient, times(1)).sendEvent(eventJson);
        }
    }
    
    @Test
    void testOnNewHeadWithNonSignificantNode() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            when(mockNode.getDisplayName()).thenReturn(""); // Empty display name
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            listener.onNewHead(mockNode);
            
            verify(mockWebSocketClient, never()).sendEvent(anyString());
        }
    }
    
    @Test
    void testOnNewHeadWhenDisabled() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(false);
            
            when(mockNode.getDisplayName()).thenReturn("Stage: Build");
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            listener.onNewHead(mockNode);
            
            verify(mockWebSocketClient, never()).sendEvent(anyString());
        }
    }
    
    @Test
    void testOnNewHeadWithNullExecution() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            when(mockNode.getExecution()).thenReturn(null);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            listener.onNewHead(mockNode);
            
            verify(mockWebSocketClient, never()).sendEvent(anyString());
        }
    }
    
    @Test
    void testOnNewHeadWithNonWorkflowRun() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            // Use Queue.Executable instead of Run
            Queue.Executable nonWorkflowExecutable = mock(Queue.Executable.class);
            FlowExecutionOwner mockOwner = mock(FlowExecutionOwner.class);
            try {
                when(mockOwner.getExecutable()).thenReturn(nonWorkflowExecutable);
            } catch (java.io.IOException e) {
                // Mock doesn't actually throw, but method signature requires handling
            }
            when(mockExecution.getOwner()).thenReturn(mockOwner);
            when(mockNode.getDisplayName()).thenReturn("Stage: Build");
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            listener.onNewHead(mockNode);
            
            verify(mockWebSocketClient, never()).sendEvent(anyString());
        }
    }
    
    @Test
    void testOnNewHeadWithStageNode() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class);
             MockedStatic<EventMapper> mapperMock = Mockito.mockStatic(EventMapper.class);
             MockedStatic<hudson.ExtensionList> extListMock = Mockito.mockStatic(hudson.ExtensionList.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            // Stage node should trigger stage events, not flow node events
            when(mockNode.getDisplayName()).thenReturn("Stage: Deploy");
            
            // Mock StageEventListener
            StageEventListener mockStageListener = mock(StageEventListener.class);
            hudson.ExtensionList<StageEventListener> mockExtList = mock(hudson.ExtensionList.class);
            when(mockExtList.isEmpty()).thenReturn(false);
            when(mockExtList.get(0)).thenReturn(mockStageListener);
            extListMock.when(() -> hudson.ExtensionList.lookup(StageEventListener.class)).thenReturn(mockExtList);
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            String stageEventJson = "{\"type\":\"STAGE_STARTED\"}";
            mapperMock.when(() -> EventMapper.createStageStartedEvent(mockRun, mockNode)).thenReturn(stageEventJson);
            
            listener.onNewHead(mockNode);
            
            // Should call stage listener, not send flow node event
            verify(mockStageListener, atLeastOnce()).onStageStarted(eq(mockRun), eq(mockNode), isNull());
        }
    }
    
    @Test
    void testOnNewHeadHandlesException() {
        try (MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(NexiScopeGlobalConfiguration.class);
             MockedStatic<WebSocketClient> wsMock = Mockito.mockStatic(WebSocketClient.class)) {
            
            configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
            when(mockConfig.isEnabled()).thenReturn(true);
            when(mockConfig.isValid()).thenReturn(true);
            
            when(mockNode.getExecution()).thenThrow(new RuntimeException("Test exception"));
            
            wsMock.when(WebSocketClient::getInstance).thenReturn(mockWebSocketClient);
            
            // Should not throw exception
            assertDoesNotThrow(() -> listener.onNewHead(mockNode));
        }
    }
}

