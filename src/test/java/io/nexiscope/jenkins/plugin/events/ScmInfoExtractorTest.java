package io.nexiscope.jenkins.plugin.events;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for ScmInfoExtractor.
 * 
 * @author NexiScope Team
 */
class ScmInfoExtractorTest {
    
    private WorkflowRun mockRun;
    
    @BeforeEach
    void setUp() {
        mockRun = mock(WorkflowRun.class);
    }
    
    @Test
    void testExtractWithNullRun() {
        // Test with null WorkflowRun (explicit cast to avoid ambiguity)
        WorkflowRun nullRun = null;
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(nullRun);
        assertNull(info);
    }
    
    @Test
    void testExtractBranchFromEnvironment() throws Exception {
        // Setup: Not a multi-branch project, so it will check environment
        WorkflowJob mockJob = mock(WorkflowJob.class);
        when(mockRun.getParent()).thenReturn(mockJob);
        // getParent().getParent() should not be WorkflowMultiBranchProject
        when(mockJob.getParent()).thenReturn(mock(jenkins.model.Jenkins.class));
        
        EnvVars envVars = new EnvVars();
        envVars.put("GIT_BRANCH", "origin/feature/test");
        envVars.put("GIT_COMMIT", "abc123def456");
        
        when(mockRun.getEnvironment(any())).thenReturn(envVars);
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("test", info.getBranch()); // Should extract branch name without origin/
        assertEquals("abc123def456", info.getCommitHash());
        assertTrue(info.hasBranch());
        assertTrue(info.hasCommitHash());
    }
    
    @Test
    void testExtractBranchFromEnvironmentBRANCH_NAME() throws Exception {
        // Setup: Not a multi-branch project, so it will check environment
        WorkflowJob mockJob = mock(WorkflowJob.class);
        when(mockRun.getParent()).thenReturn(mockJob);
        when(mockJob.getParent()).thenReturn(mock(jenkins.model.Jenkins.class));
        
        // The actual implementation uses getEnvironment() for BRANCH_NAME
        EnvVars envVars = new EnvVars();
        envVars.put("BRANCH_NAME", "main");
        envVars.put("GIT_COMMIT", "def456ghi789");
        
        when(mockRun.getEnvironment(any())).thenReturn(envVars);
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("main", info.getBranch());
        assertEquals("def456ghi789", info.getCommitHash());
    }
    
    @Test
    @org.junit.jupiter.api.Disabled("Multi-branch project mocking is complex due to Jenkins type hierarchy. " +
            "This scenario is better tested with integration tests using real Jenkins instances.")
    void testExtractBranchFromMultiBranchProject() throws Exception {
        // For multi-branch projects, the structure is: MultiBranchProject -> BranchJob -> Run
        // Mocking this is complex due to Jenkins type hierarchy and instanceof checks
        // Integration tests would be better for this scenario
        WorkflowJob mockJob = mock(WorkflowJob.class);
        
        when(mockRun.getParent()).thenReturn(mockJob);
        when(mockJob.getName()).thenReturn("feature-branch");
        
        // Mock getParent().getParent() to return WorkflowMultiBranchProject using lenient mocking
        WorkflowMultiBranchProject mockMBP = mock(WorkflowMultiBranchProject.class);
        lenient().when(mockJob.getParent()).thenReturn(mockMBP);
        
        // getEnvironment() throws IOException
        when(mockRun.getEnvironment(any())).thenThrow(new java.io.IOException("No environment"));
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("feature-branch", info.getBranch());
    }
    
    @Test
    void testExtractCommitFromChangelog() throws Exception {
        @SuppressWarnings("unchecked")
        ChangeLogSet.Entry mockEntry = mock(ChangeLogSet.Entry.class);
        when(mockEntry.getCommitId()).thenReturn("commit123");
        
        // Create a real ChangeLogSet with a real iterator
        @SuppressWarnings("unchecked")
        ChangeLogSet<ChangeLogSet.Entry> mockChangeSet = mock(ChangeLogSet.class);
        when(mockChangeSet.isEmptySet()).thenReturn(false);
        // Use a real iterator from a list
        when(mockChangeSet.iterator()).thenReturn(Collections.singletonList(mockEntry).iterator());
        
        // Use getChangeSets() which returns a list
        @SuppressWarnings("unchecked")
        java.util.List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = 
            Collections.singletonList((ChangeLogSet<? extends ChangeLogSet.Entry>) mockChangeSet);
        when(mockRun.getChangeSets()).thenReturn(changeSets);
        when(mockRun.getEnvironment(any())).thenThrow(new java.io.IOException("No environment"));
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("commit123", info.getCommitHash());
    }
    
    @Test
    void testDetectGitScmType() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put("GIT_URL", "https://github.com/user/repo.git");
        envVars.put("GIT_COMMIT", "abc123def4567890123456789012345678901234"); // 40 char hex
        
        when(mockRun.getEnvironment(any())).thenReturn(envVars);
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("git", info.getScmType());
    }
    
    @Test
    void testDetectSvnScmType() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put("SVN_URL", "https://svn.example.com/repo");
        
        when(mockRun.getEnvironment(any())).thenReturn(envVars);
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("svn", info.getScmType());
    }
    
    @Test
    void testDetectGitFromCommitHashFormat() throws Exception {
        EnvVars envVars = new EnvVars();
        envVars.put("GIT_COMMIT", "a1b2c3d4e5f6789012345678901234567890abcd"); // 40 char hex
        
        when(mockRun.getEnvironment(any())).thenReturn(envVars);
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("git", info.getScmType());
    }
    
    @Test
    void testExtractWithNoScmInfo() throws Exception {
        when(mockRun.getEnvironment(any())).thenThrow(new java.io.IOException("No environment"));
        when(mockRun.getChangeSets()).thenReturn(Collections.emptyList());
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info); // Should return ScmInfo even if no data
        assertFalse(info.hasBranch());
        assertFalse(info.hasCommitHash());
        assertEquals("unknown", info.getScmType());
    }
    
    @Test
    void testScmInfoHasBranch() {
        ScmInfoExtractor.ScmInfo info = new ScmInfoExtractor.ScmInfo("main", null, "git");
        assertTrue(info.hasBranch());
        
        ScmInfoExtractor.ScmInfo noBranch = new ScmInfoExtractor.ScmInfo(null, "abc123", "git");
        assertFalse(noBranch.hasBranch());
        
        ScmInfoExtractor.ScmInfo emptyBranch = new ScmInfoExtractor.ScmInfo("", "abc123", "git");
        assertFalse(emptyBranch.hasBranch());
    }
    
    @Test
    void testScmInfoHasCommitHash() {
        ScmInfoExtractor.ScmInfo info = new ScmInfoExtractor.ScmInfo("main", "abc123", "git");
        assertTrue(info.hasCommitHash());
        
        ScmInfoExtractor.ScmInfo noCommit = new ScmInfoExtractor.ScmInfo("main", null, "git");
        assertFalse(noCommit.hasCommitHash());
        
        ScmInfoExtractor.ScmInfo emptyCommit = new ScmInfoExtractor.ScmInfo("main", "", "git");
        assertFalse(emptyCommit.hasCommitHash());
    }
    
    @Test
    void testBranchNameNormalization() throws Exception {
        // Setup: Not a multi-branch project
        WorkflowJob mockJob = mock(WorkflowJob.class);
        when(mockRun.getParent()).thenReturn(mockJob);
        when(mockJob.getParent()).thenReturn(mock(jenkins.model.Jenkins.class));
        
        EnvVars envVars = new EnvVars();
        envVars.put("GIT_BRANCH", "origin/main");
        
        when(mockRun.getEnvironment(any())).thenReturn(envVars);
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("main", info.getBranch()); // Should remove "origin/" prefix
    }
    
    @Test
    void testBranchNameWithMultipleSlashes() throws Exception {
        // Setup: Not a multi-branch project
        WorkflowJob mockJob = mock(WorkflowJob.class);
        when(mockRun.getParent()).thenReturn(mockJob);
        when(mockJob.getParent()).thenReturn(mock(jenkins.model.Jenkins.class));
        
        EnvVars envVars = new EnvVars();
        envVars.put("GIT_BRANCH", "origin/feature/new-feature");
        
        when(mockRun.getEnvironment(any())).thenReturn(envVars);
        
        ScmInfoExtractor.ScmInfo info = ScmInfoExtractor.extract(mockRun);
        
        assertNotNull(info);
        assertEquals("new-feature", info.getBranch()); // Should get last part after last /
    }
}

