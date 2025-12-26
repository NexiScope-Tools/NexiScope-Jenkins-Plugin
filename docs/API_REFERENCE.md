# 📚 NexiScope Jenkins Plugin - API Reference

## Table of Contents

- [Pipeline DSL](#pipeline-dsl)
- [Event Types](#event-types)
- [Configuration API](#configuration-api)
- [Java API](#java-api)
- [REST Endpoints](#rest-endpoints)

---

## Pipeline DSL

### nexiscopeEvent

Send custom events from your Jenkins pipeline.

#### Syntax

```groovy
nexiscopeEvent(
    eventType: String,
    message: String,
    metadata: Map<String, Object> (optional)
)
```

#### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `eventType` | String | Yes | Type of the event (e.g., 'deployment', 'test', 'notification') |
| `message` | String | Yes | Human-readable message describing the event |
| `metadata` | Map | No | Additional key-value pairs with event details |

#### Examples

**Basic Event**

```groovy
pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                nexiscopeEvent(
                    eventType: 'deployment',
                    message: 'Deploying application to production'
                )
                // Your deployment steps
            }
        }
    }
}
```

**Event with Metadata**

```groovy
nexiscopeEvent(
    eventType: 'deployment',
    message: 'Deployed version 1.2.3 to production',
    metadata: [
        environment: 'production',
        version: '1.2.3',
        deployedBy: env.BUILD_USER,
        region: 'us-east-1',
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
    ]
)
```

**Test Results Event**

```groovy
stage('Test') {
    steps {
        sh 'npm test'
        
        nexiscopeEvent(
            eventType: 'test.completed',
            message: 'Unit tests completed successfully',
            metadata: [
                totalTests: 150,
                passed: 148,
                failed: 2,
                duration: '45s'
            ]
        )
    }
}
```

**Error Notification**

```groovy
post {
    failure {
        nexiscopeEvent(
            eventType: 'build.failed',
            message: "Build failed: ${currentBuild.result}",
            metadata: [
                stage: env.STAGE_NAME,
                error: currentBuild.description,
                buildUrl: env.BUILD_URL
            ]
        )
    }
}
```

#### Scripted Pipeline

```groovy
node {
    stage('Build') {
        nexiscopeEvent(
            eventType: 'build.started',
            message: 'Starting build process'
        )
        
        // Build steps
        
        nexiscopeEvent(
            eventType: 'build.completed',
            message: 'Build completed successfully'
        )
    }
}
```

---

## Event Types

### Automatic Events

These events are automatically captured by the plugin:

#### Pipeline Events

| Event Type | Description | Triggered When |
|------------|-------------|----------------|
| `pipeline.started` | Pipeline execution started | Pipeline begins |
| `pipeline.completed` | Pipeline execution completed | Pipeline finishes (success/failure) |
| `pipeline.aborted` | Pipeline execution aborted | Pipeline is manually stopped |
| `pipeline.queued` | Pipeline added to queue | Build is queued |
| `pipeline.dequeued` | Pipeline removed from queue | Build starts executing |

**Event Payload Example:**

```json
{
  "type": "pipeline.started",
  "timestamp": 1703606400000,
  "instanceId": "jenkins-prod-01",
  "buildId": "123",
  "buildNumber": 42,
  "jobName": "my-pipeline",
  "jobUrl": "https://jenkins.example.com/job/my-pipeline/",
  "buildUrl": "https://jenkins.example.com/job/my-pipeline/42/",
  "parameters": {
    "BRANCH": "main",
    "DEPLOY_ENV": "production"
  },
  "scmInfo": {
    "branch": "main",
    "commit": "abc123def456",
    "repository": "https://github.com/example/repo.git"
  }
}
```

#### Stage Events

| Event Type | Description | Triggered When |
|------------|-------------|----------------|
| `stage.started` | Stage execution started | Stage begins |
| `stage.completed` | Stage execution completed | Stage finishes |
| `stage.failed` | Stage execution failed | Stage fails |

**Event Payload Example:**

```json
{
  "type": "stage.started",
  "timestamp": 1703606400000,
  "instanceId": "jenkins-prod-01",
  "buildId": "123",
  "stageName": "Build",
  "stageId": "stage-1"
}
```

#### Step Events

| Event Type | Description | Triggered When |
|------------|-------------|----------------|
| `step.started` | Step execution started | Individual step begins |
| `step.completed` | Step execution completed | Step finishes |
| `step.failed` | Step execution failed | Step fails |

**Event Payload Example:**

```json
{
  "type": "step.started",
  "timestamp": 1703606400000,
  "instanceId": "jenkins-prod-01",
  "buildId": "123",
  "stageName": "Build",
  "stepName": "sh",
  "stepId": "step-1"
}
```

#### Log Events

| Event Type | Description | Triggered When |
|------------|-------------|----------------|
| `log.line` | Log line captured | Log line is written |
| `log.batch` | Batch of log lines | Multiple logs batched together |

**Event Payload Example:**

```json
{
  "type": "log.batch",
  "timestamp": 1703606400000,
  "instanceId": "jenkins-prod-01",
  "buildId": "123",
  "lines": [
    {
      "timestamp": 1703606400000,
      "level": "INFO",
      "message": "Building application..."
    },
    {
      "timestamp": 1703606401000,
      "level": "INFO",
      "message": "Build successful"
    }
  ]
}
```

### Custom Events

Custom events sent via `nexiscopeEvent` step:

```json
{
  "type": "custom",
  "eventType": "deployment",
  "timestamp": 1703606400000,
  "instanceId": "jenkins-prod-01",
  "buildId": "123",
  "message": "Deployed to production",
  "metadata": {
    "environment": "production",
    "version": "1.2.3"
  }
}
```

---

## Configuration API

### Global Configuration

Access and modify plugin configuration programmatically.

#### Get Configuration

```java
import io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration;

NexiScopeGlobalConfiguration config = 
    NexiScopeGlobalConfiguration.get();

String platformUrl = config.getPlatformUrl();
Secret authToken = config.getAuthToken();
String instanceId = config.getInstanceId();
```

#### Set Configuration

```java
import hudson.util.Secret;

NexiScopeGlobalConfiguration config = 
    NexiScopeGlobalConfiguration.get();

config.setPlatformUrl("https://app.nexiscope.com");
config.setAuthToken(Secret.fromString("nxs_live_..."));
config.setInstanceId("jenkins-prod-01");

config.save();
```

#### Test Connection

```java
import io.nexiscope.jenkins.plugin.websocket.ConnectionTester;

ConnectionTester.TestResult result = 
    ConnectionTester.testConnection(
        "https://app.nexiscope.com",
        "nxs_live_...",
        "jenkins-prod-01"
    );

if (result.isSuccess()) {
    System.out.println("Connection successful!");
} else {
    System.out.println("Error: " + result.getMessage());
}
```

---

## Java API

### Sending Events Programmatically

#### Using WebSocketClient

```java
import io.nexiscope.jenkins.plugin.websocket.WebSocketClient;
import java.util.Map;

WebSocketClient client = WebSocketClient.getInstance();

Map<String, Object> event = Map.of(
    "type", "custom",
    "eventType", "deployment",
    "message", "Deployed to production",
    "timestamp", System.currentTimeMillis(),
    "metadata", Map.of(
        "environment", "production",
        "version", "1.2.3"
    )
);

client.sendEvent(event);
```

#### Using EventMapper

```java
import io.nexiscope.jenkins.plugin.events.EventMapper;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

WorkflowRun run = ...; // Your pipeline run

Map<String, Object> event = 
    EventMapper.mapPipelineStartEvent(run);

// Event is automatically queued and sent
```

### Event Queue Management

#### Check Queue Status

```java
import io.nexiscope.jenkins.plugin.events.EventQueue;

EventQueue queue = EventQueue.getInstance();

int size = queue.size();
int capacity = queue.getCapacity();
long droppedEvents = queue.getDroppedEventCount();

System.out.println("Queue: " + size + "/" + capacity);
System.out.println("Dropped: " + droppedEvents);
```

#### Clear Queue

```java
EventQueue queue = EventQueue.getInstance();
queue.clear();
```

### WebSocket Connection Management

#### Get Connection Status

```java
WebSocketClient client = WebSocketClient.getInstance();

boolean isConnected = client.isConnected();
String status = client.getConnectionStatus();

System.out.println("Connected: " + isConnected);
System.out.println("Status: " + status);
```

#### Reconnect

```java
WebSocketClient client = WebSocketClient.getInstance();
client.reconnect();
```

#### Get Metrics

```java
WebSocketClient client = WebSocketClient.getInstance();
Map<String, Object> metrics = client.getMetrics();

System.out.println("Messages sent: " + metrics.get("messagesSent"));
System.out.println("Reconnections: " + metrics.get("reconnectionCount"));
```

---

## REST Endpoints

The plugin exposes REST endpoints for external integrations.

### Base URL

```
http://jenkins.example.com/nexiscope-config
```

### Endpoints

#### GET /status

Get plugin status and metrics.

**Request:**

```bash
curl -X GET http://jenkins.example.com/nexiscope-config/status \
  -u username:token
```

**Response:**

```json
{
  "status": "connected",
  "platformUrl": "https://app.nexiscope.com",
  "instanceId": "jenkins-prod-01",
  "queueSize": 42,
  "queueCapacity": 10000,
  "eventsDropped": 0,
  "messagesSent": 1523,
  "reconnectionCount": 2,
  "lastConnectionTime": 1703606400000
}
```

#### POST /test-connection

Test connection to NexiScope platform.

**Request:**

```bash
curl -X POST http://jenkins.example.com/nexiscope-config/test-connection \
  -u username:token \
  -H "Content-Type: application/json" \
  -d '{
    "platformUrl": "https://app.nexiscope.com",
    "authToken": "nxs_live_...",
    "instanceId": "jenkins-prod-01"
  }'
```

**Response (Success):**

```json
{
  "success": true,
  "message": "Connection successful"
}
```

**Response (Failure):**

```json
{
  "success": false,
  "message": "Connection failed: Invalid authentication token"
}
```

#### POST /send-event

Send a custom event (for external integrations).

**Request:**

```bash
curl -X POST http://jenkins.example.com/nexiscope-config/send-event \
  -u username:token \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "external.deployment",
    "message": "Deployed via external tool",
    "metadata": {
      "tool": "ArgoCD",
      "environment": "production"
    }
  }'
```

**Response:**

```json
{
  "success": true,
  "message": "Event queued successfully"
}
```

---

## Error Handling

### Error Response Format

All API errors follow this format:

```json
{
  "success": false,
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Authentication token is invalid",
    "details": "Token must start with 'nxs_live_' or 'nxs_test_'",
    "suggestion": "Generate a new token from your NexiScope dashboard"
  }
}
```

### Common Error Codes

| Code | Description | Resolution |
|------|-------------|------------|
| `INVALID_URL` | Platform URL is invalid | Check URL format (must be HTTPS/WSS) |
| `INVALID_TOKEN` | Authentication token is invalid | Verify token format and validity |
| `CONNECTION_FAILED` | Cannot connect to platform | Check network, firewall, proxy settings |
| `AUTH_FAILED` | Authentication failed | Verify token is correct and not expired |
| `RATE_LIMITED` | Too many requests | Wait and retry |
| `QUEUE_FULL` | Event queue is full | Events are being dropped, check connection |

---

## Validation

### Input Validation

All inputs are validated according to these rules:

#### Platform URL

- Must start with `https://` or `wss://`
- Must be a valid URL format
- No localhost or private IPs in production

#### Authentication Token

- Format: `nxs_(live|test)_[a-zA-Z0-9]{32,}`
- Minimum length: 40 characters
- Must not be empty or null

#### Instance ID

- Alphanumeric characters, dashes, and underscores only
- Length: 1-64 characters
- Must be unique across your Jenkins instances

#### Event Type

- Alphanumeric characters, dots, and underscores only
- Length: 1-100 characters
- Examples: `deployment`, `test.completed`, `custom_event`

---

## Rate Limits

### Default Limits

| Operation | Limit | Window |
|-----------|-------|--------|
| Connection tests | 5 requests | Per minute per user |
| Event sending | 1000 events | Per minute per instance |
| Configuration saves | 10 requests | Per minute per user |
| API calls | 100 requests | Per minute per user |

### Rate Limit Headers

API responses include rate limit information:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1703606460
```

---

## Examples

### Complete Pipeline Example

```groovy
@Library('shared-library') _

pipeline {
    agent any
    
    parameters {
        choice(name: 'ENVIRONMENT', choices: ['dev', 'staging', 'prod'])
        string(name: 'VERSION', defaultValue: '1.0.0')
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                nexiscopeEvent(
                    eventType: 'checkout.completed',
                    message: "Checked out branch ${env.GIT_BRANCH}",
                    metadata: [
                        branch: env.GIT_BRANCH,
                        commit: env.GIT_COMMIT
                    ]
                )
            }
        }
        
        stage('Build') {
            steps {
                nexiscopeEvent(
                    eventType: 'build.started',
                    message: 'Starting build process'
                )
                
                sh 'mvn clean package'
                
                nexiscopeEvent(
                    eventType: 'build.completed',
                    message: 'Build completed successfully',
                    metadata: [
                        version: params.VERSION,
                        artifact: 'target/app.jar'
                    ]
                )
            }
        }
        
        stage('Test') {
            steps {
                sh 'mvn test'
                
                script {
                    def testResults = junit 'target/surefire-reports/*.xml'
                    
                    nexiscopeEvent(
                        eventType: 'test.completed',
                        message: "Tests completed: ${testResults.totalCount} total",
                        metadata: [
                            total: testResults.totalCount,
                            passed: testResults.passCount,
                            failed: testResults.failCount,
                            skipped: testResults.skipCount
                        ]
                    )
                }
            }
        }
        
        stage('Deploy') {
            when {
                expression { params.ENVIRONMENT == 'prod' }
            }
            steps {
                nexiscopeEvent(
                    eventType: 'deployment.started',
                    message: "Deploying ${params.VERSION} to ${params.ENVIRONMENT}",
                    metadata: [
                        environment: params.ENVIRONMENT,
                        version: params.VERSION,
                        deployedBy: env.BUILD_USER
                    ]
                )
                
                // Deployment steps
                sh "./deploy.sh ${params.ENVIRONMENT}"
                
                nexiscopeEvent(
                    eventType: 'deployment.completed',
                    message: "Successfully deployed to ${params.ENVIRONMENT}",
                    metadata: [
                        environment: params.ENVIRONMENT,
                        version: params.VERSION,
                        duration: currentBuild.durationString
                    ]
                )
            }
        }
    }
    
    post {
        success {
            nexiscopeEvent(
                eventType: 'pipeline.success',
                message: 'Pipeline completed successfully'
            )
        }
        failure {
            nexiscopeEvent(
                eventType: 'pipeline.failure',
                message: "Pipeline failed at stage: ${env.STAGE_NAME}",
                metadata: [
                    failedStage: env.STAGE_NAME,
                    buildUrl: env.BUILD_URL
                ]
            )
        }
    }
}
```

---

## Support

For API questions or issues:

- 📧 **Email**: api-support@nexiscope.com
- 💬 **Documentation**: https://docs.nexiscope.com/jenkins-plugin
- 🐛 **Issues**: https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/issues

