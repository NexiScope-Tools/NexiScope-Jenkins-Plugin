# 📖 NexiScope Jenkins Plugin - User Guide

## Table of Contents

- [Introduction](#introduction)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Features](#features)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)

---

## Introduction

The NexiScope Jenkins Plugin provides real-time observability for your Jenkins pipelines. It automatically captures and streams pipeline events, logs, and metrics to the NexiScope platform, giving you instant visibility into your CI/CD workflows.

### What Does It Do?

- **Real-time Pipeline Monitoring**: Track pipeline execution as it happens
- **Automatic Event Capture**: No code changes needed - just install and configure
- **Log Streaming**: Stream build logs in real-time with intelligent filtering
- **Performance Metrics**: Track build duration, queue time, and resource usage
- **Error Detection**: Automatically capture and categorize build failures

---

## Prerequisites

Before installing the plugin, ensure you have:

1. **Jenkins Version**: 2.528.3 or higher
2. **Java Version**: Java 21 or higher
3. **NexiScope Account**: Sign up at [nexiscope.com](https://nexiscope.com)
4. **Authentication Token**: Generate from your NexiScope dashboard

---

## Installation

### Method 1: Jenkins Update Center (Recommended)

1. Navigate to **Manage Jenkins** → **Manage Plugins**
2. Go to the **Available** tab
3. Search for "NexiScope Integration"
4. Check the box and click **Install without restart**

### Method 2: Manual Installation

1. Download the latest `.hpi` file from [GitHub Releases](https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases)
2. Navigate to **Manage Jenkins** → **Manage Plugins**
3. Go to the **Advanced** tab
4. Under "Upload Plugin", choose the downloaded `.hpi` file
5. Click **Upload**
6. Restart Jenkins when prompted

---

## Configuration

### Initial Setup

1. Navigate to **Manage Jenkins** → **Configure System**
2. Scroll to the **NexiScope Configuration** section
3. Configure the following settings:

#### Connection Settings

| Setting | Description | Example |
|---------|-------------|---------|
| **Platform URL** | Your NexiScope platform URL | `https://app.nexiscope.com` |
| **Authentication Token** | API token from NexiScope dashboard | `nxs_live_abc123...` |
| **Instance ID** | Unique identifier for this Jenkins instance | `jenkins-prod-01` |

#### Advanced Settings

**Log Streaming**
- Enable/disable real-time log streaming
- Configure log levels (INFO, WARNING, ERROR)
- Set maximum lines per build (default: 10,000)

**Event Filtering**
- Choose which events to capture:
  - Pipeline start/end
  - Stage transitions
  - Step execution
  - Build failures
  - Queue events

**Performance**
- Connection timeout (default: 30 seconds)
- Reconnection attempts (default: 5)
- Event batch size (default: 50)

### Testing Your Configuration

1. After entering your settings, click **Test Connection**
2. Wait for the validation result:
   - ✅ **Success**: Connection established
   - ❌ **Error**: Check your URL and token

### Saving Configuration

Click **Save** at the bottom of the page to apply your settings.

---

## Features

### 1. Real-time Pipeline Monitoring

Once configured, the plugin automatically captures:

- **Pipeline Events**: Start, completion, and status changes
- **Stage Events**: Entry, exit, and duration for each stage
- **Step Events**: Individual step execution and results
- **Queue Events**: Time spent in queue before execution

### 2. Log Streaming

Stream build logs in real-time to NexiScope:

- **Intelligent Filtering**: Only send relevant log levels
- **Rate Limiting**: Prevent overwhelming the platform
- **Per-Build Limits**: Control maximum logs per build
- **Buffering**: Batch logs for efficient transmission

### 3. Custom Events (Pipeline DSL)

Send custom events from your pipeline scripts:

```groovy
pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                // Send a custom deployment event
                nexiscopeEvent(
                    eventType: 'deployment',
                    message: 'Deploying to production',
                    metadata: [
                        environment: 'production',
                        version: '1.2.3'
                    ]
                )
            }
        }
    }
}
```

### 4. Error Detection

Automatically captures and categorizes errors:

- Build failures with stack traces
- Test failures with details
- Timeout errors
- SCM checkout failures

### 5. Performance Metrics

Track key performance indicators:

- Build duration
- Queue time
- Stage durations
- Resource usage

---

## Troubleshooting

### Connection Issues

**Problem**: "Connection failed" error when testing connection

**Solutions**:
1. Verify your Platform URL is correct (should start with `https://` or `wss://`)
2. Check that your authentication token is valid
3. Ensure Jenkins can reach the NexiScope platform (check firewall/proxy settings)
4. Verify the token has not expired

### Events Not Appearing

**Problem**: Pipeline runs but events don't appear in NexiScope

**Solutions**:
1. Check that event filtering is not too restrictive
2. Verify the WebSocket connection is established (check Jenkins logs)
3. Ensure your Instance ID is correct
4. Check Jenkins system logs for errors: `Manage Jenkins` → `System Log`

### Log Streaming Issues

**Problem**: Logs are not streaming or incomplete

**Solutions**:
1. Verify log streaming is enabled in configuration
2. Check log level filters (you may be filtering out too much)
3. Verify you haven't hit the per-build log limit
4. Check for rate limiting messages in Jenkins logs

### Performance Impact

**Problem**: Jenkins seems slower after installing the plugin

**Solutions**:
1. Reduce event batch size in configuration
2. Disable log streaming if not needed
3. Increase connection timeout
4. Enable more aggressive event filtering

---

## FAQ

### Q: Does this plugin work with Pipeline jobs?
**A:** Yes! The plugin fully supports both Declarative and Scripted Pipeline syntax.

### Q: Does it work with Freestyle jobs?
**A:** Yes, the plugin captures events from all job types.

### Q: What happens if the connection to NexiScope is lost?
**A:** Events are queued in memory and automatically sent when the connection is restored. The queue has a configurable size limit.

### Q: Can I use this with Jenkins behind a corporate proxy?
**A:** Yes, the plugin respects Jenkins proxy configuration. Configure your proxy in `Manage Jenkins` → `Manage Plugins` → `Advanced`.

### Q: How much overhead does this plugin add?
**A:** Minimal. Events are batched and sent asynchronously. Typical overhead is less than 1% of build time.

### Q: Can I filter which pipelines send events?
**A:** Currently, all pipelines send events. You can control which event types are sent via event filtering in the configuration.

### Q: Is my data secure?
**A:** Yes. All communication uses secure WebSocket (WSS) connections. Authentication tokens are stored encrypted using Jenkins' built-in credential storage.

### Q: What Jenkins versions are supported?
**A:** Jenkins 2.528.3 and higher with Java 21+.

### Q: Can I use multiple Jenkins instances with one NexiScope account?
**A:** Yes! Use different Instance IDs for each Jenkins instance.

### Q: How do I upgrade the plugin?
**A:** Use Jenkins' built-in plugin manager: `Manage Jenkins` → `Manage Plugins` → `Updates` tab.

---

## Support

Need help? We're here for you:

- 📧 **Email**: support@nexiscope.com
- 💬 **Documentation**: https://docs.nexiscope.com
- 🐛 **Bug Reports**: https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/issues
- 💡 **Feature Requests**: https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/discussions

---

## Next Steps

- [Developer Guide](DEVELOPER_GUIDE.md) - For plugin development
- [Architecture Guide](ARCHITECTURE.md) - Technical architecture details
- [API Reference](API_REFERENCE.md) - Pipeline DSL and API documentation
- [Security Guide](../SECURITY.md) - Security features and best practices

