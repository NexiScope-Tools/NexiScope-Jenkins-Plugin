package io.nexiscope.jenkins.plugin.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventQueue.
 * 
 * @author NexiScope Team
 */
class EventQueueTest {
    
    private EventQueue queue;
    
    @BeforeEach
    void setUp() {
        queue = new EventQueue(10); // Small queue for testing
    }
    
    @Test
    void testEnqueueAndDequeue() {
        // Test basic enqueue/dequeue
        assertTrue(queue.enqueue("event1"));
        assertTrue(queue.enqueue("event2"));
        assertEquals(2, queue.size());
        
        EventQueue.QueuedEvent event1 = queue.dequeue();
        assertNotNull(event1);
        assertEquals("event1", event1.getEventJson());
        assertEquals(1, queue.size());
        
        EventQueue.QueuedEvent event2 = queue.dequeue();
        assertNotNull(event2);
        assertEquals("event2", event2.getEventJson());
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }
    
    @Test
    void testEnqueueNullOrEmpty() {
        // Test null event
        assertFalse(queue.enqueue(null));
        assertEquals(0, queue.size());
        
        // Test empty event
        assertFalse(queue.enqueue(""));
        assertFalse(queue.enqueue("   "));
        assertEquals(0, queue.size());
    }
    
    @Test
    void testQueueOverflow() {
        // Fill queue to capacity
        for (int i = 0; i < 10; i++) {
            assertTrue(queue.enqueue("event" + i));
        }
        assertEquals(10, queue.size());
        
        // Add one more - should drop oldest
        assertTrue(queue.enqueue("event10"));
        assertEquals(10, queue.size()); // Still at max
        
        // Verify oldest was dropped
        EventQueue.QueuedEvent first = queue.dequeue();
        assertEquals("event1", first.getEventJson()); // event0 was dropped
    }
    
    @Test
    void testFlush() {
        // Add multiple events
        queue.enqueue("event1");
        queue.enqueue("event2");
        queue.enqueue("event3");
        assertEquals(3, queue.size());
        
        // Flush all events
        List<EventQueue.QueuedEvent> events = queue.flush();
        assertEquals(3, events.size());
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
        
        // Verify event order (FIFO)
        assertEquals("event1", events.get(0).getEventJson());
        assertEquals("event2", events.get(1).getEventJson());
        assertEquals("event3", events.get(2).getEventJson());
    }
    
    @Test
    void testFlushEmptyQueue() {
        List<EventQueue.QueuedEvent> events = queue.flush();
        assertNotNull(events);
        assertEquals(0, events.size());
        assertTrue(queue.isEmpty());
    }
    
    @Test
    void testClear() {
        queue.enqueue("event1");
        queue.enqueue("event2");
        assertEquals(2, queue.size());
        
        queue.clear();
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }
    
    @Test
    void testIsEmpty() {
        assertTrue(queue.isEmpty());
        queue.enqueue("event1");
        assertFalse(queue.isEmpty());
        queue.dequeue();
        assertTrue(queue.isEmpty());
    }
    
    @Test
    void testGetMaxSize() {
        EventQueue smallQueue = new EventQueue(5);
        assertEquals(5, smallQueue.getMaxSize());
        
        EventQueue largeQueue = new EventQueue(1000);
        assertEquals(1000, largeQueue.getMaxSize());
    }
    
    @Test
    void testInvalidMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            new EventQueue(0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new EventQueue(-1);
        });
    }
    
    @Test
    void testDefaultConstructor() {
        EventQueue defaultQueue = new EventQueue();
        assertEquals(1000, defaultQueue.getMaxSize());
    }
    
    @Test
    void testQueuedEventTimestamp() {
        long before = System.currentTimeMillis();
        queue.enqueue("event1");
        long after = System.currentTimeMillis();
        
        EventQueue.QueuedEvent event = queue.dequeue();
        assertNotNull(event);
        assertTrue(event.getTimestamp() >= before);
        assertTrue(event.getTimestamp() <= after);
    }
    
    @Test
    void testMetrics() {
        queue.enqueue("event1");
        queue.enqueue("event2");
        
        EventQueue.QueueMetrics metrics = queue.getMetrics();
        assertNotNull(metrics);
        assertEquals(2, metrics.getCurrentSize());
        assertEquals(10, metrics.getMaxSize());
        assertEquals(2, metrics.getTotalQueued());
        assertEquals(0, metrics.getTotalFlushed());
        assertEquals(0, metrics.getTotalDropped());
        assertTrue(metrics.getOldestEventAge() >= 0);
        
        // Test utilization
        double utilization = metrics.getUtilization();
        assertEquals(0.2, utilization, 0.01); // 2/10 = 0.2
    }
    
    @Test
    void testMetricsAfterFlush() {
        queue.enqueue("event1");
        queue.enqueue("event2");
        queue.flush();
        
        EventQueue.QueueMetrics metrics = queue.getMetrics();
        assertEquals(0, metrics.getCurrentSize());
        assertEquals(2, metrics.getTotalQueued());
        assertEquals(2, metrics.getTotalFlushed());
        assertEquals(0.0, metrics.getUtilization(), 0.01);
    }
    
    @Test
    void testMetricsAfterOverflow() {
        // Fill queue
        for (int i = 0; i < 10; i++) {
            queue.enqueue("event" + i);
        }
        
        // Cause overflow
        queue.enqueue("event10");
        
        EventQueue.QueueMetrics metrics = queue.getMetrics();
        assertEquals(1, metrics.getTotalDropped());
        assertEquals(11, metrics.getTotalQueued()); // 10 + 1 new
    }
    
    @Test
    void testOldestEventTimestamp() {
        queue.enqueue("event1");
        long firstTimestamp = System.currentTimeMillis();
        
        try {
            Thread.sleep(10); // Small delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        queue.enqueue("event2");
        
        EventQueue.QueueMetrics metrics = queue.getMetrics();
        assertTrue(metrics.getOldestEventTimestamp() > 0);
        assertTrue(metrics.getOldestEventAge() >= 0);
    }
    
    @Test
    void testConcurrentEnqueue() throws InterruptedException {
        int threadCount = 5;
        int eventsPerThread = 10;
        Thread[] threads = new Thread[threadCount];
        
        // Start multiple threads enqueueing events
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    queue.enqueue("event-" + threadId + "-" + j);
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all events were queued (some may have been dropped due to overflow)
        EventQueue.QueueMetrics metrics = queue.getMetrics();
        assertTrue(metrics.getTotalQueued() >= 10); // At least max size
        assertTrue(metrics.getCurrentSize() <= 10); // Not more than max
    }
}

