package io.nexiscope.jenkins.plugin.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;

/**
 * Event filter engine for filtering events before sending to NexiScope.
 * 
 * Supports:
 * - Job name pattern matching (include/exclude)
 * - Branch pattern matching (include/exclude)
 * - Event type filtering
 * - Custom filter expressions (future)
 * 
 * @author NexiScope Team
 */
public class EventFilter {
    
    private static final Logger LOGGER = Logger.getLogger(EventFilter.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private final List<Pattern> jobIncludePatterns = new ArrayList<>();
    private final List<Pattern> jobExcludePatterns = new ArrayList<>();
    private final List<Pattern> branchIncludePatterns = new ArrayList<>();
    private final List<Pattern> branchExcludePatterns = new ArrayList<>();
    private final List<String> allowedEventTypes = new ArrayList<>();
    private final List<String> blockedEventTypes = new ArrayList<>();
    
    private boolean enabled = false;
    
    /**
     * Creates an EventFilter from configuration.
     */
    public static EventFilter fromConfig(NexiScopeGlobalConfiguration config) {
        EventFilter filter = new EventFilter();
        
        if (config == null || !config.isEventFilteringEnabled()) {
            return filter; // Return disabled filter
        }
        
        filter.enabled = true;
        
        // Parse job patterns
        String jobInclude = config.getJobIncludePatterns();
        if (jobInclude != null && !jobInclude.trim().isEmpty()) {
            filter.addPatterns(jobInclude, filter.jobIncludePatterns, "job include");
        }
        
        String jobExclude = config.getJobExcludePatterns();
        if (jobExclude != null && !jobExclude.trim().isEmpty()) {
            filter.addPatterns(jobExclude, filter.jobExcludePatterns, "job exclude");
        }
        
        // Parse branch patterns
        String branchInclude = config.getBranchIncludePatterns();
        if (branchInclude != null && !branchInclude.trim().isEmpty()) {
            filter.addPatterns(branchInclude, filter.branchIncludePatterns, "branch include");
        }
        
        String branchExclude = config.getBranchExcludePatterns();
        if (branchExclude != null && !branchExclude.trim().isEmpty()) {
            filter.addPatterns(branchExclude, filter.branchExcludePatterns, "branch exclude");
        }
        
        // Parse event types
        String allowedTypes = config.getAllowedEventTypes();
        if (allowedTypes != null && !allowedTypes.trim().isEmpty()) {
            filter.parseEventTypes(allowedTypes, filter.allowedEventTypes);
        }
        
        String blockedTypes = config.getBlockedEventTypes();
        if (blockedTypes != null && !blockedTypes.trim().isEmpty()) {
            filter.parseEventTypes(blockedTypes, filter.blockedEventTypes);
        }
        
        return filter;
    }
    
    /**
     * Checks if an event should be sent (passes all filters).
     */
    public boolean shouldSendEvent(String eventJson) {
        if (!enabled) {
            return true; // No filtering when disabled
        }
        
        try {
            JsonNode event = mapper.readTree(eventJson);
            
            // Extract event metadata
            String eventType = extractEventType(event);
            String jobName = extractJobName(event);
            String branch = extractBranch(event);
            
            // Check event type filters
            if (!passesEventTypeFilter(eventType)) {
                LOGGER.fine("Event filtered by event type: " + eventType);
                return false;
            }
            
            // Check job name filters
            if (!passesJobNameFilter(jobName)) {
                LOGGER.fine("Event filtered by job name: " + jobName);
                return false;
            }
            
            // Check branch filters
            if (!passesBranchFilter(branch)) {
                LOGGER.fine("Event filtered by branch: " + branch);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.warning("Error filtering event, allowing it through: " + e.getMessage());
            return true; // On error, allow event through (fail open)
        }
    }
    
    /**
     * Extracts event type from event JSON.
     */
    private String extractEventType(JsonNode event) {
        JsonNode typeNode = event.get("type");
        return typeNode != null && typeNode.isTextual() ? typeNode.asText() : null;
    }
    
    /**
     * Extracts job name from event JSON.
     */
    private String extractJobName(JsonNode event) {
        JsonNode pipeline = event.get("pipeline");
        if (pipeline != null) {
            JsonNode jobNameNode = pipeline.get("jobName");
            if (jobNameNode != null && jobNameNode.isTextual()) {
                return jobNameNode.asText();
            }
        }
        return null;
    }
    
    /**
     * Extracts branch name from event JSON.
     */
    private String extractBranch(JsonNode event) {
        JsonNode pipeline = event.get("pipeline");
        if (pipeline != null) {
            JsonNode branchNode = pipeline.get("branch");
            if (branchNode != null && branchNode.isTextual()) {
                return branchNode.asText();
            }
        }
        return null;
    }
    
    /**
     * Checks if event type passes the filter.
     */
    private boolean passesEventTypeFilter(String eventType) {
        if (eventType == null) {
            return true; // Allow events without type
        }
        
        // Check blocked types first
        if (!blockedEventTypes.isEmpty()) {
            for (String blocked : blockedEventTypes) {
                if (eventType.equalsIgnoreCase(blocked.trim())) {
                    return false;
                }
            }
        }
        
        // Check allowed types
        if (!allowedEventTypes.isEmpty()) {
            for (String allowed : allowedEventTypes) {
                if (eventType.equalsIgnoreCase(allowed.trim())) {
                    return true;
                }
            }
            return false; // Not in allowed list
        }
        
        return true; // No restrictions
    }
    
    /**
     * Checks if job name passes the filter.
     */
    private boolean passesJobNameFilter(String jobName) {
        if (jobName == null) {
            return true; // Allow events without job name
        }
        
        // Check exclude patterns first
        for (Pattern pattern : jobExcludePatterns) {
            if (pattern.matcher(jobName).find()) {
                return false;
            }
        }
        
        // Check include patterns
        if (!jobIncludePatterns.isEmpty()) {
            for (Pattern pattern : jobIncludePatterns) {
                if (pattern.matcher(jobName).find()) {
                    return true;
                }
            }
            return false; // Not matched by any include pattern
        }
        
        return true; // No restrictions
    }
    
    /**
     * Checks if branch passes the filter.
     */
    private boolean passesBranchFilter(String branch) {
        if (branch == null) {
            return true; // Allow events without branch
        }
        
        // Check exclude patterns first
        for (Pattern pattern : branchExcludePatterns) {
            if (pattern.matcher(branch).find()) {
                return false;
            }
        }
        
        // Check include patterns
        if (!branchIncludePatterns.isEmpty()) {
            for (Pattern pattern : branchIncludePatterns) {
                if (pattern.matcher(branch).find()) {
                    return true;
                }
            }
            return false; // Not matched by any include pattern
        }
        
        return true; // No restrictions
    }
    
    /**
     * Adds patterns from a comma-separated or newline-separated string.
     */
    private void addPatterns(String patternsStr, List<Pattern> patternList, String patternType) {
        String[] patterns = patternsStr.split("[,\n]");
        for (String patternStr : patterns) {
            patternStr = patternStr.trim();
            if (patternStr.isEmpty()) {
                continue;
            }
            
            try {
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                patternList.add(pattern);
                LOGGER.fine("Added " + patternType + " pattern: " + patternStr);
            } catch (PatternSyntaxException e) {
                LOGGER.warning("Invalid " + patternType + " pattern: " + patternStr + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * Parses event types from a comma-separated or newline-separated string.
     */
    private void parseEventTypes(String typesStr, List<String> typeList) {
        String[] types = typesStr.split("[,\n]");
        for (String type : types) {
            type = type.trim();
            if (!type.isEmpty()) {
                typeList.add(type);
            }
        }
    }
    
    /**
     * Checks if filtering is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}

