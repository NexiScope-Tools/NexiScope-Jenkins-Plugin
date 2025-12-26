package io.nexiscope.jenkins.plugin.websocket;

import org.junit.After;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/**
 * Tests for EventBatcher.
 */
public class EventBatcherTest {
    
    private EventBatcher batcher;
    private TestBatchSender sender;
    
    @After
    public void cleanup() {
        if (batcher != null) {
            batcher.shutdown();
        }
    }
    
    @Test
    public void testBatchSizeLimit() throws Exception {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 5, 10000); // 5 events, 10s timeout
        
        // Add 5 events
        for (int i = 0; i < 5; i++) {
            batcher.addEvent("{\"event\": " + i + "}");
        }
        
        // Wait for batch to be sent
        assertTrue("Batch should be sent", sender.waitForBatch(1000));
        assertEquals("Should have sent 1 batch", 1, sender.getBatchCount());
        assertEquals("Batch should have 5 events", 5, sender.getLastBatchSize());
    }
    
    @Test
    public void testBatchTimeout() throws Exception {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 100, 500); // 100 events, 500ms timeout
        
        // Add 3 events (less than batch size)
        for (int i = 0; i < 3; i++) {
            batcher.addEvent("{\"event\": " + i + "}");
        }
        
        // Wait for timeout
        assertTrue("Batch should be sent after timeout", sender.waitForBatch(1000));
        assertEquals("Should have sent 1 batch", 1, sender.getBatchCount());
        assertEquals("Batch should have 3 events", 3, sender.getLastBatchSize());
    }
    
    @Test
    public void testMultipleBatches() throws Exception {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 5, 10000);
        
        // Add 12 events (should create 2 full batches + 1 partial)
        for (int i = 0; i < 12; i++) {
            batcher.addEvent("{\"event\": " + i + "}");
        }
        
        // Wait for batches
        Thread.sleep(100);
        
        assertTrue("Should have sent at least 2 batches", sender.getBatchCount() >= 2);
    }
    
    @Test
    public void testNullEvent() {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 5, 10000);
        
        // Add null event
        batcher.addEvent(null);
        
        assertEquals("Should not add null event", 0, batcher.getCurrentBatchSize());
    }
    
    @Test
    public void testEmptyEvent() {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 5, 10000);
        
        // Add empty event
        batcher.addEvent("");
        batcher.addEvent("   ");
        
        assertEquals("Should not add empty events", 0, batcher.getCurrentBatchSize());
    }
    
    @Test
    public void testFlush() throws Exception {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 100, 10000); // Large batch size and timeout
        
        // Add 3 events
        for (int i = 0; i < 3; i++) {
            batcher.addEvent("{\"event\": " + i + "}");
        }
        
        // Manual flush
        batcher.flush();
        
        // Wait for batch
        assertTrue("Batch should be sent after flush", sender.waitForBatch(1000));
        assertEquals("Should have sent 1 batch", 1, sender.getBatchCount());
        assertEquals("Batch should have 3 events", 3, sender.getLastBatchSize());
    }
    
    @Test
    public void testShutdown() throws Exception {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 100, 10000);
        
        // Add events
        for (int i = 0; i < 3; i++) {
            batcher.addEvent("{\"event\": " + i + "}");
        }
        
        // Shutdown (should flush)
        batcher.shutdown();
        
        // Wait for batch
        assertTrue("Batch should be sent on shutdown", sender.waitForBatch(1000));
        assertEquals("Should have sent 1 batch", 1, sender.getBatchCount());
    }
    
    @Test
    public void testGetMetrics() throws Exception {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 5, 10000);
        
        // Add 7 events (1 full batch + 2 pending)
        for (int i = 0; i < 7; i++) {
            batcher.addEvent("{\"event\": " + i + "}");
        }
        
        // Wait for first batch
        sender.waitForBatch(1000);
        
        EventBatcher.BatchMetrics metrics = batcher.getMetrics();
        
        assertEquals("Should have received 7 events", 7, metrics.totalEventsReceived);
        assertTrue("Should have sent at least 1 batch", metrics.totalBatchesSent >= 1);
        assertTrue("Should have sent at least 5 events", metrics.totalEventsSent >= 5);
        assertEquals("Should have 2 events in current batch", 2, metrics.currentBatchSize);
    }
    
    @Test
    public void testAverageBatchSize() throws Exception {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 5, 10000);
        
        // Add 10 events (2 full batches)
        for (int i = 0; i < 10; i++) {
            batcher.addEvent("{\"event\": " + i + "}");
        }
        
        // Wait for batches
        Thread.sleep(200);
        
        EventBatcher.BatchMetrics metrics = batcher.getMetrics();
        
        double avgBatchSize = metrics.getAverageBatchSize();
        assertTrue("Average batch size should be around 5", avgBatchSize >= 4.0 && avgBatchSize <= 6.0);
    }
    
    @Test
    public void testConcurrentAdding() throws Exception {
        sender = new TestBatchSender();
        batcher = new EventBatcher(sender, 50, 10000);
        
        // Add events from multiple threads
        int threadCount = 5;
        int eventsPerThread = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                for (int i = 0; i < eventsPerThread; i++) {
                    batcher.addEvent("{\"thread\": " + threadId + ", \"event\": " + i + "}");
                }
                latch.countDown();
            }).start();
        }
        
        // Wait for all threads
        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS));
        
        // Flush to get all events
        batcher.flush();
        Thread.sleep(200);
        
        EventBatcher.BatchMetrics metrics = batcher.getMetrics();
        
        assertEquals("Should have received all events", 
            threadCount * eventsPerThread, metrics.totalEventsReceived);
    }
    
    /**
     * Test batch sender that tracks sent batches.
     */
    private static class TestBatchSender implements EventBatcher.BatchSender {
        private final List<List<String>> batches = new ArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);
        
        @Override
        public void sendBatch(List<String> events) {
            synchronized (batches) {
                batches.add(new ArrayList<>(events));
                latch.countDown();
            }
        }
        
        public int getBatchCount() {
            synchronized (batches) {
                return batches.size();
            }
        }
        
        public int getLastBatchSize() {
            synchronized (batches) {
                return batches.isEmpty() ? 0 : batches.get(batches.size() - 1).size();
            }
        }
        
        public boolean waitForBatch(long timeoutMs) throws InterruptedException {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }
}

