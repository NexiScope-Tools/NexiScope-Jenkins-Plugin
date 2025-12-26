package io.nexiscope.jenkins.plugin.events;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Extracts SCM information (branch, commit hash) from Jenkins pipeline runs.
 * 
 * Supports:
 * - Git (via GitSCM and Git plugin)
 * - SVN (via SubversionSCM)
 * - Multi-branch pipelines
 * - Generic SCM implementations
 * 
 * @author NexiScope Team
 */
public class ScmInfoExtractor {
    
    private static final Logger LOGGER = Logger.getLogger(ScmInfoExtractor.class.getName());
    
    /**
     * SCM information extracted from a pipeline run.
     */
    public static class ScmInfo {
        private final String branch;
        private final String commitHash;
        private final String scmType;
        
        public ScmInfo(String branch, String commitHash, String scmType) {
            this.branch = branch;
            this.commitHash = commitHash;
            this.scmType = scmType;
        }
        
        public String getBranch() {
            return branch;
        }
        
        public String getCommitHash() {
            return commitHash;
        }
        
        public String getScmType() {
            return scmType;
        }
        
        public boolean hasBranch() {
            return branch != null && !branch.isEmpty();
        }
        
        public boolean hasCommitHash() {
            return commitHash != null && !commitHash.isEmpty();
        }
    }
    
    /**
     * Extracts SCM information from a WorkflowRun.
     * 
     * @param run The workflow run to extract information from
     * @return ScmInfo object with branch and commit hash, or null if extraction fails
     */
    public static ScmInfo extract(WorkflowRun run) {
        if (run == null) {
            return null;
        }
        
        try {
            String branch = extractBranch(run);
            String commitHash = extractCommitHash(run);
            String scmType = detectScmType(run);
            
            return new ScmInfo(branch, commitHash, scmType);
        } catch (Exception e) {
            LOGGER.warning("Failed to extract SCM info from run " + run.getFullDisplayName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts SCM information from a FreeStyleBuild.
     * 
     * @param build The freestyle build to extract information from
     * @return ScmInfo object with branch and commit hash, or null if extraction fails
     */
    public static ScmInfo extract(FreeStyleBuild build) {
        if (build == null) {
            return null;
        }
        
        try {
            String branch = extractBranchFromFreestyle(build);
            String commitHash = extractCommitHashFromFreestyle(build);
            String scmType = detectScmTypeFromFreestyle(build);
            
            return new ScmInfo(branch, commitHash, scmType);
        } catch (Exception e) {
            LOGGER.warning("Failed to extract SCM info from freestyle build " + build.getFullDisplayName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Extracts branch name from a FreeStyleBuild.
     */
    private static String extractBranchFromFreestyle(FreeStyleBuild build) {
        try {
            // Method 1: Try to get from environment variables
            EnvVars env = build.getEnvironment(null);
            if (env != null) {
                // Common environment variables for branch
                String[] branchVars = {"GIT_BRANCH", "BRANCH_NAME", "SVN_BRANCH", "BRANCH"};
                for (String var : branchVars) {
                    String value = env.get(var);
                    if (value != null && !value.isEmpty()) {
                        // Clean up branch name (remove origin/, remotes/, etc.)
                        return cleanBranchName(value);
                    }
                }
            }
            
            // Method 2: Try to get from SCM revision action
            hudson.scm.SCMRevisionState revisionState = build.getAction(hudson.scm.SCMRevisionState.class);
            if (revisionState != null) {
                // Some SCM plugins store branch info in revision state
                // This is SCM-specific and may not always work
            }
            
            // Method 3: Try to get from change log
            ChangeLogSet<?> changeSet = build.getChangeSet();
            if (changeSet != null && !changeSet.isEmptySet()) {
                // Some SCM plugins include branch info in change log
                Iterator<? extends ChangeLogSet.Entry> entries = changeSet.iterator();
                if (entries.hasNext()) {
                    ChangeLogSet.Entry entry = entries.next();
                    // Try to get branch from entry (SCM-specific)
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Failed to extract branch from freestyle build: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extracts commit hash from a FreeStyleBuild.
     */
    private static String extractCommitHashFromFreestyle(FreeStyleBuild build) {
        try {
            // Method 1: Try to get from environment variables
            EnvVars env = build.getEnvironment(null);
            if (env != null) {
                // Common environment variables for commit hash
                String[] commitVars = {"GIT_COMMIT", "SVN_REVISION", "COMMIT", "BUILD_VCS_NUMBER"};
                for (String var : commitVars) {
                    String value = env.get(var);
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
            
            // Method 2: Try to get from change log
            ChangeLogSet<?> changeSet = build.getChangeSet();
            if (changeSet != null && !changeSet.isEmptySet()) {
                Iterator<? extends ChangeLogSet.Entry> entries = changeSet.iterator();
                if (entries.hasNext()) {
                    ChangeLogSet.Entry entry = entries.next();
                    // Get commit ID from entry
                    String commitId = entry.getCommitId();
                    if (commitId != null && !commitId.isEmpty()) {
                        return commitId;
                    }
                }
            }
            
            // Method 3: Try to get from SCM revision action
            hudson.scm.SCMRevisionState revisionState = build.getAction(hudson.scm.SCMRevisionState.class);
            if (revisionState != null) {
                // Some SCM plugins store commit hash in revision state
                String revision = revisionState.toString();
                if (revision != null && !revision.isEmpty() && revision.length() >= 7) {
                    return revision;
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Failed to extract commit hash from freestyle build: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Detects SCM type from a FreeStyleBuild.
     */
    private static String detectScmTypeFromFreestyle(FreeStyleBuild build) {
        try {
            // Check environment variables for SCM type hints
            EnvVars env = build.getEnvironment(null);
            if (env != null) {
                if (env.containsKey("GIT_COMMIT") || env.containsKey("GIT_BRANCH")) {
                    return "git";
                }
                if (env.containsKey("SVN_REVISION") || env.containsKey("SVN_BRANCH")) {
                    return "svn";
                }
            }
            
            // Check change log set type
            ChangeLogSet<?> changeSet = build.getChangeSet();
            if (changeSet != null) {
                String className = changeSet.getClass().getName();
                if (className.contains("GitChangeSet") || className.contains("git")) {
                    return "git";
                }
                if (className.contains("SubversionChangeSet") || className.contains("svn")) {
                    return "svn";
                }
            }
            
            // Check project SCM configuration
            if (build.getProject() != null && build.getProject().getScm() != null) {
                String scmClass = build.getProject().getScm().getClass().getName();
                if (scmClass.contains("GitSCM") || scmClass.contains("git")) {
                    return "git";
                }
                if (scmClass.contains("SubversionSCM") || scmClass.contains("svn")) {
                    return "svn";
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Failed to detect SCM type from freestyle build: " + e.getMessage());
        }
        
        return "unknown";
    }
    
    /**
     * Cleans branch name by removing common prefixes.
     */
    private static String cleanBranchName(String branch) {
        if (branch == null) {
            return null;
        }
        
        // Remove common prefixes
        branch = branch.replaceFirst("^origin/", "");
        branch = branch.replaceFirst("^remotes/", "");
        branch = branch.replaceFirst("^refs/heads/", "");
        branch = branch.replaceFirst("^refs/remotes/", "");
        
        return branch;
    }
    
    /**
     * Extracts branch name from a WorkflowRun.
     * 
     * @param run The workflow run
     * @return Branch name, or null if not found
     */
    private static String extractBranch(WorkflowRun run) {
        try {
            // Method 1: Try to get branch from multi-branch project
            if (run.getParent().getParent() instanceof WorkflowMultiBranchProject) {
                WorkflowMultiBranchProject mbp = (WorkflowMultiBranchProject) run.getParent().getParent();
                // For multi-branch projects, the branch name is in the job name
                // The structure is: MultiBranchProject -> BranchJob -> Run
                String jobName = run.getParent().getName();
                if (jobName != null && !jobName.isEmpty()) {
                    return jobName;
                }
            }
            
            // Method 2: Try to get from environment variables (set by Git plugin)
            try {
                String branchName = run.getEnvironment(null).get("GIT_BRANCH");
                if (branchName != null && !branchName.isEmpty()) {
                    // GIT_BRANCH might be in format "origin/branch-name", extract just the branch
                    if (branchName.contains("/")) {
                        branchName = branchName.substring(branchName.lastIndexOf("/") + 1);
                    }
                    return branchName;
                }
            } catch (Exception e) {
                // Environment might not be available, continue to next method
            }
            
            // Method 3: Try to get from build variables via environment
            // Note: getBuildVariables() doesn't exist, we use environment instead
            try {
                String branchName = run.getEnvironment(null).get("BRANCH_NAME");
                if (branchName != null && !branchName.isEmpty()) {
                    return branchName;
                }
            } catch (Exception e) {
                // Build variables might not be available
            }
            
            // Method 4: Try to get from job name (for single-branch jobs, this might be the branch)
            // Note: This is a weak fallback and may not always be accurate
            // We skip this method as it's unreliable
            
        } catch (Exception e) {
            LOGGER.fine("Error extracting branch: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extracts commit hash from a WorkflowRun.
     * 
     * @param run The workflow run
     * @return Commit hash, or null if not found
     */
    private static String extractCommitHash(WorkflowRun run) {
        try {
            // Method 1: Try to get from environment variables (set by Git plugin)
            try {
                String commitHash = run.getEnvironment(null).get("GIT_COMMIT");
                if (commitHash != null && !commitHash.isEmpty()) {
                    return commitHash;
                }
            } catch (Exception e) {
                // Environment might not be available, continue to next method
            }
            
            // Method 2: Try to get from build variables via environment
            // Note: getBuildVariables() doesn't exist, we use environment instead
            try {
                String commitHash = run.getEnvironment(null).get("GIT_COMMIT");
                if (commitHash != null && !commitHash.isEmpty()) {
                    return commitHash;
                }
            } catch (Exception e) {
                // Build variables might not be available
            }
            
            // Method 3: Try to extract from changelog
            // Note: getChangeSet() doesn't exist, use getChangeSets() which returns a list
            try {
                for (ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : run.getChangeSets()) {
                    if (changeSet != null && !changeSet.isEmptySet()) {
                        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
                        if (iterator.hasNext()) {
                            ChangeLogSet.Entry entry = iterator.next();
                            // Try to get commit ID from the entry
                            try {
                                // For Git, the commit ID is typically available
                                String commitId = entry.getCommitId();
                                if (commitId != null && !commitId.isEmpty()) {
                                    return commitId;
                                }
                            } catch (Exception e) {
                                // commitId might not be available for all SCM types
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Changelog might not be available
            }
            
        } catch (Exception e) {
            LOGGER.fine("Error extracting commit hash: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Detects the SCM type used in the pipeline.
     * 
     * @param run The workflow run
     * @return SCM type (e.g., "git", "svn", "unknown"), or null if detection fails
     */
    private static String detectScmType(WorkflowRun run) {
        try {
            // Method 1: Check environment variables
            try {
                String scmUrl = run.getEnvironment(null).get("GIT_URL");
                if (scmUrl != null && !scmUrl.isEmpty()) {
                    return "git";
                }
                
                String svnUrl = run.getEnvironment(null).get("SVN_URL");
                if (svnUrl != null && !svnUrl.isEmpty()) {
                    return "svn";
                }
            } catch (Exception e) {
                // Environment might not be available
            }
            
            // Method 2: Check changelog
            // Note: getChangeSet() doesn't exist, use getChangeSets() which returns a list
            try {
                for (ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : run.getChangeSets()) {
                    if (changeSet != null) {
                        String kind = changeSet.getKind();
                        if (kind != null) {
                            // Kind is typically "git", "svn", etc.
                            return kind.toLowerCase();
                        }
                    }
                }
            } catch (Exception e) {
                // Changelog might not be available
            }
            
            // Method 3: Check if Git commit hash format (40 char hex)
            String commitHash = extractCommitHash(run);
            if (commitHash != null && commitHash.matches("^[a-f0-9]{40}$")) {
                return "git";
            }
            
        } catch (Exception e) {
            LOGGER.fine("Error detecting SCM type: " + e.getMessage());
        }
        
        return "unknown";
    }
}

