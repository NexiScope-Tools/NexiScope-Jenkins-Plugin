package io.nexiscope.jenkins.plugin.security;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for RateLimiter.
 */
public class RateLimiterTest {
    
    @After
    public void cleanup() {
        // Clear rate limits after each test
        RateLimiter.clearAll();
    }
    
    @Test
    public void testAllowedRequests() {
        // First request should be allowed
        assertTrue("First request should be allowed", 
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1"));
        
        // Second request should be allowed
        assertTrue("Second request should be allowed", 
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1"));
    }
    
    @Test
    public void testRateLimitExceeded() {
        // Make 10 requests (limit for TEST_CONNECTION)
        for (int i = 0; i < 10; i++) {
            assertTrue("Request " + (i + 1) + " should be allowed", 
                RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1"));
        }
        
        // 11th request should be blocked
        assertFalse("11th request should be blocked", 
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1"));
    }
    
    @Test
    public void testDifferentUsers() {
        // User1 makes 10 requests
        for (int i = 0; i < 10; i++) {
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1");
        }
        
        // User1 should be blocked
        assertFalse("User1 should be blocked", 
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1"));
        
        // User2 should still be allowed
        assertTrue("User2 should be allowed", 
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user2"));
    }
    
    @Test
    public void testDifferentOperations() {
        // Make 10 TEST_CONNECTION requests
        for (int i = 0; i < 10; i++) {
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1");
        }
        
        // TEST_CONNECTION should be blocked
        assertFalse("TEST_CONNECTION should be blocked", 
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1"));
        
        // EVENT_SEND should still be allowed (different operation)
        assertTrue("EVENT_SEND should be allowed", 
            RateLimiter.isAllowed(RateLimiter.Operation.EVENT_SEND, "user1"));
    }
    
    @Test
    public void testGetCurrentCount() {
        // Make 5 requests
        for (int i = 0; i < 5; i++) {
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1");
        }
        
        int count = RateLimiter.getCurrentCount(RateLimiter.Operation.TEST_CONNECTION, "user1");
        assertEquals("Should have 5 requests", 5, count);
    }
    
    @Test
    public void testGetRemainingRequests() {
        // Make 3 requests
        for (int i = 0; i < 3; i++) {
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1");
        }
        
        int remaining = RateLimiter.getRemainingRequests(RateLimiter.Operation.TEST_CONNECTION, "user1");
        assertEquals("Should have 7 remaining (10 - 3)", 7, remaining);
    }
    
    @Test
    public void testReset() {
        // Make 10 requests
        for (int i = 0; i < 10; i++) {
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1");
        }
        
        // Should be blocked
        assertFalse("Should be blocked", 
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1"));
        
        // Reset
        RateLimiter.reset(RateLimiter.Operation.TEST_CONNECTION, "user1");
        
        // Should be allowed again
        assertTrue("Should be allowed after reset", 
            RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1"));
    }
    
    @Test
    public void testClearAll() {
        // Make requests for multiple users and operations
        RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1");
        RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user2");
        RateLimiter.isAllowed(RateLimiter.Operation.EVENT_SEND, "user1");
        
        // Clear all
        RateLimiter.clearAll();
        
        // All should have 0 count
        assertEquals("User1 TEST_CONNECTION should be 0", 0, 
            RateLimiter.getCurrentCount(RateLimiter.Operation.TEST_CONNECTION, "user1"));
        assertEquals("User2 TEST_CONNECTION should be 0", 0, 
            RateLimiter.getCurrentCount(RateLimiter.Operation.TEST_CONNECTION, "user2"));
        assertEquals("User1 EVENT_SEND should be 0", 0, 
            RateLimiter.getCurrentCount(RateLimiter.Operation.EVENT_SEND, "user1"));
    }
    
    @Test
    public void testGetStats() {
        // Make some requests
        RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user1");
        RateLimiter.isAllowed(RateLimiter.Operation.TEST_CONNECTION, "user2");
        RateLimiter.isAllowed(RateLimiter.Operation.EVENT_SEND, "user1");
        
        RateLimiter.RateLimitStats stats = RateLimiter.getStats();
        
        assertTrue("Should have trackers", stats.totalTrackers > 0);
        assertTrue("Should have active trackers", stats.activeTrackers > 0);
        assertTrue("Should have requests", stats.totalRequests > 0);
    }
    
    @Test
    public void testHighVolumeEventSend() {
        // EVENT_SEND has high limit (10,000)
        // Make 100 requests
        for (int i = 0; i < 100; i++) {
            assertTrue("Request " + (i + 1) + " should be allowed", 
                RateLimiter.isAllowed(RateLimiter.Operation.EVENT_SEND, "user1"));
        }
        
        // Should still be allowed
        assertTrue("Should still be allowed", 
            RateLimiter.isAllowed(RateLimiter.Operation.EVENT_SEND, "user1"));
    }
}

