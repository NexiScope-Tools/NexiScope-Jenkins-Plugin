package io.nexiscope.jenkins.plugin.events;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread-safe event queue for buffering events when WebSocket is disconnected.
 * 
 * Implements a bounded queue with configurable size. When the queue is full,
 * oldest events are dropped to make room for new events (FIFO drop policy).
 * 
 * @author NexiScope Team
 */
public class EventQueue {
    
    private static final Logger LOGGER = Logger.getLogger(EventQueue.class.getName());
    private static final int DEFAULT_MAX_SIZE = 1000;
    
    private final BlockingQueue<QueuedEvent> queue;
    private final int maxSize;
    
    // Metrics
    private final AtomicLong totalEventsQueued = new AtomicLong(0);
    private final AtomicLong totalEventsDropped = new AtomicLong(0);
    private final AtomicLong totalEventsFlushed = new AtomicLong(0);
    private final AtomicInteger currentSize = new AtomicInteger(0);
    private volatile long oldestEventTimestamp = 0;
    
    /**
     * Creates a new event queue with default maximum size.
     */
    public EventQueue() {
        this(DEFAULT_MAX_SIZE);
    }
    
    /**
     * Creates a new event queue with specified maximum size.
     * 
     * @param maxSize Maximum number of events to store (must be > 0)
     */
    public EventQueue(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("Max size must be greater than 0");
        }
        this.maxSize = maxSize;
        // Use LinkedBlockingQueue for FIFO behavior
        this.queue = new LinkedBlockingQueue<>(maxSize);
    }
    
    /**
     * Adds an event to the queue.
     * 
     * If the queue is full, the oldest event is dropped to make room.
     * 
     * @param eventJson The event JSON string to queue
     * @return true if event was queued, false if queue was disabled or event was rejected
     */
    public boolean enqueue(String eventJson) {
        if (eventJson == null || eventJson.trim().isEmpty()) {
            LOGGER.warning("Attempted to queue null or empty event");
            return false;
        }
        
        QueuedEvent queuedEvent = new QueuedEvent(eventJson, System.currentTimeMillis());
        
        // Try to add to queue
        boolean added = queue.offer(queuedEvent);
        
        if (added) {
            totalEventsQueued.incrementAndGet();
            int size = currentSize.incrementAndGet();
            
            // Update oldest event timestamp if this is the first event
            if (oldestEventTimestamp == 0) {
                oldestEventTimestamp = queuedEvent.getTimestamp();
            }
            
            LOGGER.fine("Event queued. Queue size: " + size + "/" + maxSize);
            return true;
        } else {
            // Queue is full, drop oldest and try again
            QueuedEvent dropped = queue.poll();
            if (dropped != null) {
                currentSize.decrementAndGet();
                totalEventsDropped.incrementAndGet();
                LOGGER.warning("Queue full, dropped oldest event to make room for new event");
                
                // Add new event
                added = queue.offer(queuedEvent);
                if (added) {
                    totalEventsQueued.incrementAndGet();
                    currentSize.incrementAndGet();
                    // Update oldest timestamp to the new oldest event
                    QueuedEvent oldest = queue.peek();
                    if (oldest != null) {
                        oldestEventTimestamp = oldest.getTimestamp();
                    }
                    return true;
                }
            }
            
            LOGGER.severe("Failed to queue event even after dropping oldest event");
            return false;
        }
    }
    
    /**
     * Removes and returns the next event from the queue.
     * 
     * @return The next event, or null if queue is empty
     */
    public QueuedEvent dequeue() {
        QueuedEvent event = queue.poll();
        if (event != null) {
            currentSize.decrementAndGet();
            
            // Update oldest timestamp if queue is now empty
            if (queue.isEmpty()) {
                oldestEventTimestamp = 0;
            } else {
                QueuedEvent oldest = queue.peek();
                if (oldest != null) {
                    oldestEventTimestamp = oldest.getTimestamp();
                }
            }
        }
        return event;
    }
    
    /**
     * Removes all events from the queue and returns them.
     * 
     * @return List of all queued events
     */
    public java.util.List<QueuedEvent> flush() {
        java.util.List<QueuedEvent> events = new java.util.ArrayList<>();
        QueuedEvent event;
        
        while ((event = queue.poll()) != null) {
            events.add(event);
            currentSize.decrementAndGet();
        }
        
        int flushedCount = events.size();
        if (flushedCount > 0) {
            totalEventsFlushed.addAndGet(flushedCount);
            LOGGER.info("Flushed " + flushedCount + " events from queue");
        }
        
        oldestEventTimestamp = 0;
        return events;
    }
    
    /**
     * Clears all events from the queue without processing them.
     */
    public void clear() {
        int cleared = queue.size();
        queue.clear();
        currentSize.set(0);
        oldestEventTimestamp = 0;
        
        if (cleared > 0) {
            LOGGER.info("Cleared " + cleared + " events from queue");
        }
    }
    
    /**
     * Gets the current number of events in the queue.
     * 
     * @return Current queue size
     */
    public int size() {
        return currentSize.get();
    }
    
    /**
     * Checks if the queue is empty.
     * 
     * @return true if queue is empty, false otherwise
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * Gets the maximum size of the queue.
     * 
     * @return Maximum queue size
     */
    public int getMaxSize() {
        return maxSize;
    }
    
    /**
     * Gets queue metrics.
     * 
     * @return QueueMetrics object with current metrics
     */
    public QueueMetrics getMetrics() {
        long oldestAge = 0;
        if (oldestEventTimestamp > 0) {
            oldestAge = System.currentTimeMillis() - oldestEventTimestamp;
        }
        
        return new QueueMetrics(
            currentSize.get(),
            maxSize,
            totalEventsQueued.get(),
            totalEventsFlushed.get(),
            totalEventsDropped.get(),
            oldestEventTimestamp,
            oldestAge
        );
    }
    
    /**
     * Represents a queued event with timestamp.
     */
    public static class QueuedEvent {
        private final String eventJson;
        private final long timestamp;
        
        public QueuedEvent(String eventJson, long timestamp) {
            this.eventJson = eventJson;
            this.timestamp = timestamp;
        }
        
        public String getEventJson() {
            return eventJson;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return "QueuedEvent{timestamp=" + timestamp + ", size=" + eventJson.length() + "}";
        }
    }
    
    /**
     * Queue metrics data class.
     */
    public static class QueueMetrics {
        private final int currentSize;
        private final int maxSize;
        private final long totalQueued;
        private final long totalFlushed;
        private final long totalDropped;
        private final long oldestEventTimestamp;
        private final long oldestEventAge;
        
        public QueueMetrics(int currentSize, int maxSize, long totalQueued, 
                          long totalFlushed, long totalDropped,
                          long oldestEventTimestamp, long oldestEventAge) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.totalQueued = totalQueued;
            this.totalFlushed = totalFlushed;
            this.totalDropped = totalDropped;
            this.oldestEventTimestamp = oldestEventTimestamp;
            this.oldestEventAge = oldestEventAge;
        }
        
        public int getCurrentSize() { return currentSize; }
        public int getMaxSize() { return maxSize; }
        public long getTotalQueued() { return totalQueued; }
        public long getTotalFlushed() { return totalFlushed; }
        public long getTotalDropped() { return totalDropped; }
        public long getOldestEventTimestamp() { return oldestEventTimestamp; }
        public long getOldestEventAge() { return oldestEventAge; }
        
        public double getUtilization() {
            return maxSize > 0 ? (double) currentSize / maxSize : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "QueueMetrics{size=%d/%d (%.1f%%), queued=%d, flushed=%d, dropped=%d, oldestAge=%dms}",
                currentSize, maxSize, getUtilization() * 100, 
                totalQueued, totalFlushed, totalDropped, oldestEventAge
            );
        }
    }
}

