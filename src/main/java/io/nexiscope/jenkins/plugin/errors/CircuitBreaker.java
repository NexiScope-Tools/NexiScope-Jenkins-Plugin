package io.nexiscope.jenkins.plugin.errors;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Circuit breaker pattern implementation for connection failures.
 * 
 * Prevents cascading failures by temporarily stopping connection attempts
 * after repeated failures, allowing the system to recover.
 * 
 * States:
 * - CLOSED: Normal operation, connections allowed
 * - OPEN: Too many failures, connections blocked temporarily
 * - HALF_OPEN: Testing if system has recovered, allowing limited connections
 * 
 * @author NexiScope Team
 */
public class CircuitBreaker {
    
    private static final Logger LOGGER = Logger.getLogger(CircuitBreaker.class.getName());
    
    /**
     * Circuit breaker states.
     */
    public enum State {
        CLOSED("Closed", "Normal operation"),
        OPEN("Open", "Circuit is open, blocking connections"),
        HALF_OPEN("Half-Open", "Testing recovery, allowing limited connections");
        
        private final String displayName;
        private final String description;
        
        State(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Configuration
    private final int failureThreshold;
    private final long timeoutMs;
    private final int halfOpenMaxAttempts;
    
    // State tracking
    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenAttempts = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;
    private volatile long stateChangeTime = 0;
    
    // Metrics
    private final AtomicLong totalTrips = new AtomicLong(0);
    private final AtomicLong totalRecoveries = new AtomicLong(0);
    
    /**
     * Creates a circuit breaker with default configuration.
     */
    public CircuitBreaker() {
        this(5, 60000, 3); // 5 failures, 60s timeout, 3 half-open attempts
    }
    
    /**
     * Creates a circuit breaker with custom configuration.
     * 
     * @param failureThreshold Number of consecutive failures before opening circuit
     * @param timeoutMs Time in milliseconds before attempting to close circuit (half-open state)
     * @param halfOpenMaxAttempts Maximum attempts allowed in half-open state
     */
    public CircuitBreaker(int failureThreshold, long timeoutMs, int halfOpenMaxAttempts) {
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
        this.halfOpenMaxAttempts = halfOpenMaxAttempts;
    }
    
    /**
     * Checks if connection is allowed based on circuit breaker state.
     * 
     * @return true if connection is allowed, false if blocked
     */
    public boolean allowConnection() {
        updateState();
        
        switch (state) {
            case CLOSED:
                return true;
                
            case OPEN:
                // Check if timeout has passed, transition to half-open
                if (System.currentTimeMillis() - stateChangeTime >= timeoutMs) {
                    transitionToHalfOpen();
                    return true; // Allow one attempt in half-open
                }
                return false;
                
            case HALF_OPEN:
                // Allow limited attempts in half-open state
                int attempts = halfOpenAttempts.incrementAndGet();
                if (attempts <= halfOpenMaxAttempts) {
                    return true;
                } else {
                    // Too many attempts in half-open, go back to open
                    transitionToOpen("Too many attempts in half-open state");
                    return false;
                }
                
            default:
                return false;
        }
    }
    
    /**
     * Records a successful connection.
     */
    public void recordSuccess() {
        successCount.incrementAndGet();
        failureCount.set(0);
        halfOpenAttempts.set(0);
        
        if (state == State.HALF_OPEN) {
            // Success in half-open means system recovered, close circuit
            transitionToClosed();
            totalRecoveries.incrementAndGet();
            LOGGER.info("Circuit breaker recovered, transitioning to CLOSED");
        } else if (state == State.CLOSED) {
            // Reset failure count on success in closed state
            failureCount.set(0);
        }
    }
    
    /**
     * Records a connection failure.
     */
    public void recordFailure() {
        lastFailureTime = System.currentTimeMillis();
        int failures = failureCount.incrementAndGet();
        halfOpenAttempts.set(0);
        
        if (state == State.HALF_OPEN) {
            // Failure in half-open means system not recovered, open circuit
            transitionToOpen("Failure in half-open state");
        } else if (state == State.CLOSED && failures >= failureThreshold) {
            // Too many failures, open circuit
            transitionToOpen("Failure threshold reached: " + failures);
            totalTrips.incrementAndGet();
            LOGGER.warning("Circuit breaker opened due to " + failures + " consecutive failures");
        }
    }
    
    /**
     * Updates circuit breaker state based on time and conditions.
     */
    private void updateState() {
        if (state == State.OPEN) {
            // Check if timeout has passed
            if (System.currentTimeMillis() - stateChangeTime >= timeoutMs) {
                transitionToHalfOpen();
            }
        }
    }
    
    /**
     * Transitions to CLOSED state.
     */
    private void transitionToClosed() {
        state = State.CLOSED;
        stateChangeTime = System.currentTimeMillis();
        failureCount.set(0);
        successCount.set(0);
        halfOpenAttempts.set(0);
    }
    
    /**
     * Transitions to OPEN state.
     */
    private void transitionToOpen(String reason) {
        state = State.OPEN;
        stateChangeTime = System.currentTimeMillis();
        halfOpenAttempts.set(0);
        LOGGER.warning("Circuit breaker opened: " + reason);
    }
    
    /**
     * Transitions to HALF_OPEN state.
     */
    private void transitionToHalfOpen() {
        state = State.HALF_OPEN;
        stateChangeTime = System.currentTimeMillis();
        halfOpenAttempts.set(0);
        successCount.set(0);
        LOGGER.info("Circuit breaker transitioning to HALF_OPEN to test recovery");
    }
    
    /**
     * Gets current state.
     */
    public State getState() {
        updateState();
        return state;
    }
    
    /**
     * Gets current failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Gets current success count.
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Gets total number of times circuit has been tripped (opened).
     */
    public long getTotalTrips() {
        return totalTrips.get();
    }
    
    /**
     * Gets total number of times circuit has recovered (closed after being open).
     */
    public long getTotalRecoveries() {
        return totalRecoveries.get();
    }
    
    /**
     * Resets circuit breaker to initial state.
     */
    public void reset() {
        transitionToClosed();
        totalTrips.set(0);
        totalRecoveries.set(0);
    }
    
    /**
     * Gets time remaining before circuit breaker attempts recovery (if open).
     * 
     * @return Time in milliseconds, or 0 if not applicable
     */
    public long getTimeUntilRecoveryAttempt() {
        if (state != State.OPEN) {
            return 0;
        }
        
        long elapsed = System.currentTimeMillis() - stateChangeTime;
        long remaining = timeoutMs - elapsed;
        return remaining > 0 ? remaining : 0;
    }
}

