package io.nexiscope.jenkins.plugin.security;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for InputValidator.
 */
public class InputValidatorTest {
    
    @Test
    public void testValidPlatformUrl() {
        InputValidator.ValidationResult result = InputValidator.validatePlatformUrl("https://api.nexiscope.com");
        assertTrue("Valid HTTPS URL should pass", result.isOk());
        assertFalse("Valid HTTPS URL should not be error", result.isError());
    }
    
    @Test
    public void testInvalidPlatformUrl() {
        // Null/empty
        InputValidator.ValidationResult result = InputValidator.validatePlatformUrl(null);
        assertTrue("Null URL should fail", result.isError());
        
        result = InputValidator.validatePlatformUrl("");
        assertTrue("Empty URL should fail", result.isError());
        
        // Invalid protocol
        result = InputValidator.validatePlatformUrl("ftp://api.nexiscope.com");
        assertTrue("FTP protocol should fail", result.isError());
        
        // Dangerous characters
        result = InputValidator.validatePlatformUrl("https://api.nexiscope.com<script>");
        assertTrue("URL with dangerous chars should fail", result.isError());
        
        // Too long
        String longUrl = "https://" + "a".repeat(3000);
        result = InputValidator.validatePlatformUrl(longUrl);
        assertTrue("Too long URL should fail", result.isError());
    }
    
    @Test
    public void testPlatformUrlWarnings() {
        // HTTP (unencrypted)
        InputValidator.ValidationResult result = InputValidator.validatePlatformUrl("http://api.nexiscope.com");
        assertTrue("HTTP should give warning", result.isWarning());
        
        // Localhost
        result = InputValidator.validatePlatformUrl("https://localhost:8080");
        assertTrue("Localhost should give warning", result.isWarning());
    }
    
    @Test
    public void testValidAuthToken() {
        // 64-char token should pass without warning
        InputValidator.ValidationResult result = InputValidator.validateAuthToken("a".repeat(64));
        assertTrue("Valid 64-char token should pass", result.isOk());
        assertFalse("Valid 64-char token should not be error", result.isError());
    }
    
    @Test
    public void testInvalidAuthToken() {
        // Null/empty
        InputValidator.ValidationResult result = InputValidator.validateAuthToken(null);
        assertTrue("Null token should fail", result.isError());
        
        result = InputValidator.validateAuthToken("");
        assertTrue("Empty token should fail", result.isError());
        
        // Too short
        result = InputValidator.validateAuthToken("short");
        assertTrue("Short token should fail", result.isError());
        
        // Invalid characters
        result = InputValidator.validateAuthToken("token with spaces!");
        assertTrue("Token with spaces should fail", result.isError());
        
        // Too long
        result = InputValidator.validateAuthToken("a".repeat(600));
        assertTrue("Too long token should fail", result.isError());
    }
    
    @Test
    public void testAuthTokenWarnings() {
        // Short but valid
        InputValidator.ValidationResult result = InputValidator.validateAuthToken("abcdef1234567890");
        assertTrue("16-char token should give warning", result.isWarning());
    }
    
    @Test
    public void testValidInstanceId() {
        InputValidator.ValidationResult result = InputValidator.validateInstanceId("jenkins-prod-01");
        assertTrue("Valid instance ID should pass", result.isOk());
        
        result = InputValidator.validateInstanceId("jenkins_test");
        assertTrue("Instance ID with underscore should pass", result.isOk());
    }
    
    @Test
    public void testInvalidInstanceId() {
        // Null/empty
        InputValidator.ValidationResult result = InputValidator.validateInstanceId(null);
        assertTrue("Null instance ID should fail", result.isError());
        
        result = InputValidator.validateInstanceId("");
        assertTrue("Empty instance ID should fail", result.isError());
        
        // Invalid characters
        result = InputValidator.validateInstanceId("jenkins prod!");
        assertTrue("Instance ID with spaces should fail", result.isError());
        
        // Too long
        result = InputValidator.validateInstanceId("a".repeat(200));
        assertTrue("Too long instance ID should fail", result.isError());
    }
    
    @Test
    public void testValidEventType() {
        InputValidator.ValidationResult result = InputValidator.validateEventType("PIPELINE_STARTED");
        assertTrue("Valid event type should pass", result.isOk());
        
        result = InputValidator.validateEventType("STAGE_COMPLETED");
        assertTrue("Valid event type should pass", result.isOk());
    }
    
    @Test
    public void testInvalidEventType() {
        // Lowercase
        InputValidator.ValidationResult result = InputValidator.validateEventType("pipeline_started");
        assertTrue("Lowercase event type should fail", result.isError());
        
        // With spaces
        result = InputValidator.validateEventType("PIPELINE STARTED");
        assertTrue("Event type with spaces should fail", result.isError());
        
        // Too long
        result = InputValidator.validateEventType("A".repeat(100));
        assertTrue("Too long event type should fail", result.isError());
    }
    
    @Test
    public void testValidPattern() {
        InputValidator.ValidationResult result = InputValidator.validatePattern(".*test.*");
        assertTrue("Valid regex should pass", result.isOk());
        
        result = InputValidator.validatePattern("^prod-.*");
        assertTrue("Valid regex should pass", result.isOk());
        
        // Empty is OK
        result = InputValidator.validatePattern("");
        assertTrue("Empty pattern should pass", result.isOk());
    }
    
    @Test
    public void testInvalidPattern() {
        // Invalid regex
        InputValidator.ValidationResult result = InputValidator.validatePattern("[invalid");
        assertTrue("Invalid regex should fail", result.isError());
        
        // Too long
        result = InputValidator.validatePattern("a".repeat(2000));
        assertTrue("Too long pattern should fail", result.isError());
    }
    
    @Test
    public void testSanitizeForDisplay() {
        String sanitized = InputValidator.sanitizeForDisplay("<script>alert('xss')</script>");
        assertFalse("Should not contain <", sanitized.contains("<"));
        assertFalse("Should not contain >", sanitized.contains(">"));
        assertTrue("Should contain escaped &lt;", sanitized.contains("&lt;"));
        assertTrue("Should contain escaped &gt;", sanitized.contains("&gt;"));
    }
}

