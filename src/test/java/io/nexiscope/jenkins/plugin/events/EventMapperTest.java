package io.nexiscope.jenkins.plugin.events;

import hudson.model.Run;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;
import jenkins.model.Jenkins;
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
 * Unit tests for EventMapper.
 * 
 * @author NexiScope Team
 */
class EventMapperTest {
    
    private WorkflowRun mockRun;
    private FlowNode mockNode;
    private Jenkins mockJenkins;
    private WorkflowJob mockJob;
    
    @BeforeEach
    void setUp() {
        mockRun = mock(WorkflowRun.class);
        mockNode = mock(FlowNode.class);
        mockJenkins = mock(Jenkins.class);
        
        // Setup common mocks
        mockJob = mock(WorkflowJob.class);
        when(mockRun.getParent()).thenReturn(mockJob);
        when(mockJob.getFullName()).thenReturn("test-job");
        when(mockRun.getNumber()).thenReturn(42);
        when(mockRun.getId()).thenReturn("42");
        when(mockRun.getStartTimeInMillis()).thenReturn(1000L);
        when(mockRun.getDuration()).thenReturn(5000L);
        when(mockRun.getResult()).thenReturn(hudson.model.Result.SUCCESS);
        
        when(mockNode.getDisplayName()).thenReturn("Test Stage");
        when(mockNode.getId()).thenReturn("node-1");
        // Note: getClass() is final and cannot be mocked
    }
    
    @Test
    void testCreatePipelineStartedEvent() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createPipelineStartedEvent(mockRun);
            
            assertNotNull(event);
            assertTrue(event.contains("PIPELINE_STARTED"));
            assertTrue(event.contains("test-job"));
            assertTrue(event.contains("\"runNumber\":42"));
            assertTrue(event.contains("\"startTime\":1000"));
            assertTrue(event.contains("eventId"));
            assertTrue(event.contains("timestamp"));
        }
    }
    
    @Test
    void testCreatePipelineCompletedEvent() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createPipelineCompletedEvent(mockRun);
            
            assertNotNull(event);
            assertTrue(event.contains("PIPELINE_COMPLETED"));
            assertTrue(event.contains("SUCCESS"));
            assertTrue(event.contains("\"duration\":5000"));
            assertTrue(event.contains("\"buildNumber\":42"));
        }
    }
    
    @Test
    void testCreatePipelineAbortedEvent() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createPipelineAbortedEvent(mockRun);
            
            assertNotNull(event);
            assertTrue(event.contains("PIPELINE_ABORTED"));
            assertTrue(event.contains("ABORTED"));
        }
    }
    
    @Test
    void testCreatePipelineDeletedEvent() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createPipelineDeletedEvent(mockRun);
            
            assertNotNull(event);
            assertTrue(event.contains("PIPELINE_DELETED"));
            assertTrue(event.contains("\"buildNumber\":42"));
        }
    }
    
    @Test
    void testCreateStageStartedEvent() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createStageStartedEvent(mockRun, mockNode);
            
            assertNotNull(event);
            assertTrue(event.contains("STAGE_STARTED"));
            assertTrue(event.contains("Test Stage"));
            assertTrue(event.contains("node-1"));
        }
    }
    
    @Test
    void testCreateStageCompletedEvent() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createStageCompletedEvent(mockRun, mockNode);
            
            assertNotNull(event);
            assertTrue(event.contains("STAGE_COMPLETED"));
            assertTrue(event.contains("Test Stage"));
        }
    }
    
    @Test
    void testCreateStageFailedEvent() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            Throwable error = new RuntimeException("Test error");
            String event = EventMapper.createStageFailedEvent(mockRun, mockNode, error);
            
            assertNotNull(event);
            assertTrue(event.contains("STAGE_FAILED"));
            assertTrue(event.contains("Test error"));
            assertTrue(event.contains("RuntimeException"));
        }
    }
    
    @Test
    void testCreateStageFailedEventWithNullError() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createStageFailedEvent(mockRun, mockNode, null);
            
            assertNotNull(event);
            assertTrue(event.contains("STAGE_FAILED"));
        }
    }
    
    @Test
    void testCreateFlowNodeEvent() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createFlowNodeEvent(mockRun, mockNode);
            
            assertNotNull(event);
            assertTrue(event.contains("FLOW_NODE"));
            assertTrue(event.contains("Test Stage"));
            assertTrue(event.contains("node-1"));
        }
    }
    
    @Test
    void testEventContainsRequiredFields() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createPipelineStartedEvent(mockRun);
            
            // Verify all required base fields are present
            assertTrue(event.contains("eventId"));
            assertTrue(event.contains("type"));
            assertTrue(event.contains("timestamp"));
            assertTrue(event.contains("jenkins"));
            assertTrue(event.contains("pipeline"));
            assertTrue(event.contains("data"));
        }
    }
    
    @Test
    void testEventJsonIsValid() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            
            String event = EventMapper.createPipelineStartedEvent(mockRun);
            
            // Basic JSON structure validation
            assertTrue(event.startsWith("{"));
            assertTrue(event.endsWith("}"));
            assertTrue(event.contains("\""));
        }
    }
    
    @Test
    void testCreatePipelineCompletedEventWithNullResult() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            when(mockRun.getResult()).thenReturn(null);
            
            String event = EventMapper.createPipelineCompletedEvent(mockRun);
            
            assertNotNull(event);
            assertTrue(event.contains("UNKNOWN")); // Should default to UNKNOWN
        }
    }
    
    @Test
    void testCreatePipelineCompletedEventWithZeroDuration() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            when(mockRun.getDuration()).thenReturn(0L);
            
            String event = EventMapper.createPipelineCompletedEvent(mockRun);
            
            assertNotNull(event);
            assertTrue(event.contains("\"duration\":0"));
        }
    }
    
    @Test
    void testCreateStageFailedEventWithNullMessage() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            Throwable error = new RuntimeException();
            when(mockNode.getDisplayName()).thenReturn("Test Stage");
            
            String event = EventMapper.createStageFailedEvent(mockRun, mockNode, error);
            
            assertNotNull(event);
            assertTrue(event.contains("STAGE_FAILED"));
        }
    }
    
    @Test
    void testCreateFlowNodeEventWithNullDisplayName() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            when(mockNode.getDisplayName()).thenReturn(null);
            
            String event = EventMapper.createFlowNodeEvent(mockRun, mockNode);
            
            assertNotNull(event);
            assertTrue(event.contains("FLOW_NODE"));
        }
    }
    
    @Test
    void testEventSerializationWithSpecialCharacters() {
        try (MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class);
             MockedStatic<NexiScopeGlobalConfiguration> configMock = Mockito.mockStatic(
                 NexiScopeGlobalConfiguration.class)) {
            
            setupMocks(jenkinsMock, configMock);
            when(mockJob.getFullName()).thenReturn("test-job-with-special-chars-@#$");
            
            String event = EventMapper.createPipelineStartedEvent(mockRun);
            
            assertNotNull(event);
            // Should handle special characters in JSON
            assertTrue(event.contains("test-job"));
        }
    }
    
    private void setupMocks(MockedStatic<Jenkins> jenkinsMock, 
                           MockedStatic<NexiScopeGlobalConfiguration> configMock) {
        jenkinsMock.when(Jenkins::get).thenReturn(mockJenkins);
        when(mockJenkins.getRootUrl()).thenReturn("http://localhost:8080");
        when(mockJenkins.getVersion()).thenReturn(new hudson.util.VersionNumber("2.426.3"));
        
        NexiScopeGlobalConfiguration mockConfig = mock(NexiScopeGlobalConfiguration.class);
        configMock.when(NexiScopeGlobalConfiguration::get).thenReturn(mockConfig);
        when(mockConfig.getInstanceId()).thenReturn("test-instance");
    }
}

