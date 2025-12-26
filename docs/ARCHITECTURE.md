# 🏗️ NexiScope Jenkins Plugin - Architecture Guide

## Table of Contents

- [Overview](#overview)
- [System Architecture](#system-architecture)
- [Core Components](#core-components)
- [Event Flow](#event-flow)
- [WebSocket Communication](#websocket-communication)
- [Performance Optimizations](#performance-optimizations)
- [Security Architecture](#security-architecture)
- [Extension Points](#extension-points)

---

## Overview

The NexiScope Jenkins Plugin is designed as a lightweight, event-driven system that captures Jenkins pipeline events and streams them in real-time to the NexiScope platform.

### Design Principles

1. **Non-Intrusive**: Zero impact on pipeline execution
2. **Asynchronous**: All operations are non-blocking
3. **Resilient**: Handles connection failures gracefully
4. **Efficient**: Batches events and optimizes memory usage
5. **Secure**: Validates all inputs and encrypts sensitive data

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Jenkins Core                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  Pipeline    │  │  Freestyle   │  │  Multi-branch│          │
│  │  Jobs        │  │  Jobs        │  │  Pipelines   │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                  │                  │                  │
│         └──────────────────┴──────────────────┘                  │
│                            │                                     │
└────────────────────────────┼─────────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  Event Listeners │
                    │  (@Extension)    │
                    └────────┬─────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
┌───────▼────────┐  ┌────────▼────────┐  ┌───────▼────────┐
│ Pipeline       │  │ Stage           │  │ FlowNode       │
│ EventListener  │  │ EventListener   │  │ EventListener  │
└───────┬────────┘  └────────┬────────┘  └───────┬────────┘
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  Event Mapper   │
                    │  (Transform)    │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  Event Queue    │
                    │  (Buffer)       │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  Event Batcher  │
                    │  (Optimize)     │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ WebSocket Client│
                    │  (Transport)    │
                    └────────┬────────┘
                             │
                             │ WSS
                             ▼
                    ┌─────────────────┐
                    │   NexiScope     │
                    │   Platform      │
                    └─────────────────┘
```

---

## Core Components

### 1. Plugin Initialization

**Class**: `NexiScopePlugin`

- Entry point for the plugin
- Initializes WebSocket connection on startup
- Manages plugin lifecycle

```java
@Extension
public class NexiScopePlugin extends Plugin {
    @Override
    public void start() {
        // Initialize WebSocket connection
        // Set up listeners
    }
}
```

### 2. Global Configuration

**Class**: `NexiScopeGlobalConfiguration`

- Stores plugin settings (URL, token, instance ID)
- Provides configuration UI via Jelly
- Validates configuration inputs
- Handles connection testing

**Storage**: `$JENKINS_HOME/io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration.xml`

### 3. Event Listeners

#### PipelineEventListener

- Captures pipeline-level events (start, complete, abort)
- Implements `RunListener<WorkflowRun>`
- Extracts build metadata (duration, result, parameters)

#### StageEventListener

- Captures stage transitions
- Implements `GraphListener`
- Tracks stage duration and status

#### FlowNodeEventListener

- Captures individual step execution
- Implements `FlowExecutionListener`
- Provides fine-grained execution details

### 4. Event Processing Pipeline

#### EventMapper

Transforms Jenkins events into NexiScope format:

```java
public class EventMapper {
    public static Map<String, Object> mapPipelineStartEvent(WorkflowRun run) {
        return Map.of(
            "type", "pipeline.started",
            "timestamp", System.currentTimeMillis(),
            "buildId", run.getId(),
            "jobName", run.getParent().getFullName(),
            // ... more fields
        );
    }
}
```

#### EventQueue

- In-memory bounded queue (default: 10,000 events)
- FIFO with overflow handling (drop oldest)
- Thread-safe using `ConcurrentLinkedQueue`
- Metrics tracking (size, age, drops)

#### EventBatcher

- Batches events for efficient transmission
- Configurable batch size (default: 50 events)
- Time-based flushing (default: 5 seconds)
- Asynchronous sending using `CompletableFuture`

### 5. WebSocket Communication

**Class**: `WebSocketClient`

- Manages persistent WebSocket connection
- Handles authentication
- Implements reconnection with exponential backoff
- Sends batched events

**Connection Lifecycle**:

```
┌─────────┐
│ CLOSED  │
└────┬────┘
     │ connect()
     ▼
┌─────────┐
│CONNECTING│
└────┬────┘
     │ onOpen()
     ▼
┌─────────┐      authenticate()      ┌──────────┐
│  OPEN   │─────────────────────────▶│  AUTH    │
└────┬────┘                          └────┬─────┘
     │                                    │
     │◀───────────────────────────────────┘
     │         AUTH_SUCCESS
     ▼
┌─────────┐
│CONNECTED│
└────┬────┘
     │ onError() / onClose()
     ▼
┌─────────┐
│RECONNECT│
└────┬────┘
     │ exponential backoff
     │
     └────▶ (retry)
```

### 6. Log Streaming

**Class**: `LogStreamingFilter`

- Implements `ConsoleLogFilter`
- Captures log lines in real-time
- Filters by log level
- Enforces per-build limits
- Buffers and batches log lines

### 7. Security Components

#### InputValidator

- Validates all configuration inputs
- Sanitizes user input (XSS prevention)
- Checks URL formats, token lengths, etc.

#### RateLimiter

- Sliding window algorithm
- Per-operation limits (connection tests, event sending)
- Prevents abuse and DoS attacks

#### AuditLogger

- Tracks security-relevant events
- Circular buffer (last 1000 events)
- Includes user, timestamp, operation, outcome

---

## Event Flow

### Pipeline Start Event Flow

```
1. Jenkins starts pipeline
   │
   ▼
2. PipelineEventListener.onStarted(run)
   │
   ▼
3. EventMapper.mapPipelineStartEvent(run)
   │ - Extract job name, build ID, parameters
   │ - Add timestamp, instance ID
   │ - Extract SCM info (branch, commit)
   │
   ▼
4. EventQueue.enqueue(event)
   │ - Check queue size
   │ - Drop oldest if full
   │
   ▼
5. EventBatcher.add(event)
   │ - Add to current batch
   │ - Check batch size (50 events)
   │ - Check time since last flush (5s)
   │
   ▼
6. EventBatcher.flush() [if triggered]
   │ - Create batch message
   │ - Send asynchronously
   │
   ▼
7. WebSocketClient.sendBatch(events)
   │ - Check connection state
   │ - Serialize to JSON
   │ - Send via WebSocket
   │
   ▼
8. NexiScope Platform receives event
```

### Error Handling Flow

```
1. Error occurs (connection failure, etc.)
   │
   ▼
2. ErrorHandler.handleError(exception)
   │ - Categorize error
   │ - Log details
   │
   ▼
3. ErrorMessages.getErrorMessage(category)
   │ - Get user-friendly message
   │ - Add resolution suggestions
   │
   ▼
4. Return to user / Log
```

---

## WebSocket Communication

### Protocol

1. **Connection**: Client initiates WSS connection to platform
2. **Authentication**: Client sends auth message with token
3. **Event Streaming**: Client sends batched events
4. **Heartbeat**: Periodic ping/pong to keep connection alive
5. **Reconnection**: Automatic reconnection on failure

### Message Format

#### Authentication Message

```json
{
  "type": "auth",
  "token": "nxs_live_...",
  "instanceId": "jenkins-prod-01"
}
```

#### Event Batch Message

```json
{
  "type": "batch",
  "events": [
    {
      "type": "pipeline.started",
      "timestamp": 1703606400000,
      "buildId": "123",
      "jobName": "my-pipeline",
      "metadata": { ... }
    },
    // ... more events
  ]
}
```

### Reconnection Strategy

- **Initial delay**: 1 second
- **Max delay**: 60 seconds
- **Backoff multiplier**: 2x
- **Max attempts**: 10 (then stops)

```
Attempt 1: Wait 1s
Attempt 2: Wait 2s
Attempt 3: Wait 4s
Attempt 4: Wait 8s
Attempt 5: Wait 16s
Attempt 6: Wait 32s
Attempt 7+: Wait 60s
```

---

## Performance Optimizations

### 1. Event Batching

- Reduces WebSocket messages by 50x
- Configurable batch size and timeout
- Metrics: `batchesSent`, `eventsPerBatch`

### 2. Asynchronous Processing

- All I/O operations are non-blocking
- Uses `CompletableFuture` for async tasks
- Prevents blocking Jenkins executor threads

### 3. Memory Optimization

#### String Interning

```java
MemoryOptimizer.internString("pipeline.started")
```

- Reduces memory for repeated strings
- Uses weak reference cache

#### Object Pooling

- Reuses event objects where possible
- Reduces GC pressure

#### Memory Monitoring

```java
MemoryOptimizer.checkMemory()
```

- Warns when memory usage > 80%
- Triggers cleanup if needed

### 4. Queue Management

- Bounded queue prevents memory exhaustion
- Drop oldest strategy maintains recent events
- Configurable queue size

---

## Security Architecture

### 1. Input Validation

All user inputs are validated:

- **URLs**: Must be valid HTTPS/WSS
- **Tokens**: Must match format `nxs_(live|test)_[a-zA-Z0-9]{32,}`
- **Instance IDs**: Alphanumeric, dash, underscore only

### 2. Secrets Management

- Tokens stored using Jenkins `Secret` class
- Encrypted at rest
- Never logged or exposed in UI

### 3. Rate Limiting

- **Connection tests**: 5 per minute per user
- **Event sending**: 1000 per minute per instance
- **Config saves**: 10 per minute per user

### 4. Audit Logging

Tracks:
- Configuration changes
- Connection tests
- Authentication attempts
- Rate limit violations

---

## Extension Points

### Custom Event Types

Developers can send custom events from pipelines:

```groovy
nexiscopeEvent(
    eventType: 'deployment',
    message: 'Deployed to production',
    metadata: [
        environment: 'prod',
        version: '1.2.3'
    ]
)
```

### Custom Filters

Extend `LogStreamingFilter` to add custom filtering logic:

```java
public class MyCustomFilter extends LogStreamingFilter {
    @Override
    protected boolean shouldIncludeLine(String line) {
        // Custom logic
        return super.shouldIncludeLine(line) && !line.contains("SECRET");
    }
}
```

### Custom Listeners

Add new event listeners:

```java
@Extension
public class MyCustomListener extends RunListener<Run<?, ?>> {
    @Override
    public void onCompleted(Run<?, ?> run, TaskListener listener) {
        // Send custom event
    }
}
```

---

## Performance Metrics

The plugin tracks various metrics:

| Metric | Description | Location |
|--------|-------------|----------|
| `eventsQueued` | Total events queued | EventQueue |
| `eventsDropped` | Events dropped due to overflow | EventQueue |
| `batchesSent` | Total batches sent | EventBatcher |
| `eventsPerBatch` | Average events per batch | EventBatcher |
| `connectionAttempts` | Total connection attempts | WebSocketClient |
| `reconnectionCount` | Total reconnections | WebSocketClient |
| `messagesSent` | Total messages sent | WebSocketClient |

Access metrics via: `WebSocketClient.getMetrics()`

---

## Configuration Files

### Plugin Configuration

**Location**: `$JENKINS_HOME/io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration.xml`

```xml
<?xml version='1.1' encoding='UTF-8'?>
<io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration>
  <platformUrl>https://app.nexiscope.com</platformUrl>
  <authToken>{encrypted-token}</authToken>
  <instanceId>jenkins-prod-01</instanceId>
  <enableLogStreaming>true</enableLogStreaming>
  <maxLogLinesPerBuild>10000</maxLogLinesPerBuild>
  <enableEventFiltering>true</enableEventFiltering>
  <captureQueueEvents>true</captureQueueEvents>
</io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration>
```

---

## Troubleshooting

### Enable Debug Logging

Add to `$JENKINS_HOME/logging.properties`:

```properties
io.nexiscope.jenkins.plugin.level=FINE
io.nexiscope.jenkins.plugin.websocket.level=FINEST
```

Or via Jenkins UI:
1. `Manage Jenkins` → `System Log`
2. Add new log recorder: "NexiScope Plugin"
3. Add logger: `io.nexiscope.jenkins.plugin`
4. Set level: `FINE` or `FINEST`

### Common Issues

**High Memory Usage**
- Reduce queue size
- Reduce batch size
- Disable log streaming

**Connection Failures**
- Check firewall/proxy settings
- Verify platform URL
- Check token validity

**Missing Events**
- Check event filtering settings
- Verify listeners are registered
- Check Jenkins logs for errors

---

## Future Enhancements

Potential areas for expansion:

1. **Persistent Queue**: File-based or database-backed queue for durability
2. **Compression**: Compress event batches before sending
3. **Metrics Dashboard**: Built-in Jenkins page for plugin metrics
4. **Event Replay**: Ability to replay failed events
5. **Multi-Platform Support**: Support for multiple NexiScope instances

---

## References

- [Jenkins Plugin Development](https://www.jenkins.io/doc/developer/plugin-development/)
- [WebSocket Protocol](https://tools.ietf.org/html/rfc6455)
- [Java Concurrency](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [NexiScope API Documentation](https://docs.nexiscope.com/api)

