<div align="center">
  <img src="assets/nexiscope.png" alt="NexiScope Logo" width="200"/>
  
# NexiScope Jenkins Plugin

  **Real-time CI/CD Observability for Jenkins**
  
  <p>
    <a href="https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases">
      <img src="https://img.shields.io/badge/version-1.0.0-blue?style=flat-square&logo=github" alt="Version">
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/license-Proprietary-yellow?style=flat-square&logo=keycdn&logoColor=white" alt="License">
    </a>
    <a href="https://jenkins.io">
      <img src="https://img.shields.io/badge/Jenkins-2.528.3+-D24939?style=flat-square&logo=jenkins&logoColor=white" alt="Jenkins">
    </a>
    <a href="https://openjdk.org/">
      <img src="https://img.shields.io/badge/Java-21+-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java">
    </a>
    <a href="https://maven.apache.org/">
      <img src="https://img.shields.io/badge/Maven-3.9+-C71A36?style=flat-square&logo=apache-maven&logoColor=white" alt="Maven">
    </a>
  </p>
  
  <p>
    <a href="https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/stargazers">
      <img src="https://img.shields.io/github/stars/NexiScope-Tools/NexiScope-Jenkins-Plugin?style=social" alt="Stars">
    </a>
    <a href="https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/issues">
      <img src="https://img.shields.io/github/issues/NexiScope-Tools/NexiScope-Jenkins-Plugin?style=flat-square&logo=github" alt="Issues">
    </a>
    <a href="https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/pulls">
      <img src="https://img.shields.io/github/issues-pr/NexiScope-Tools/NexiScope-Jenkins-Plugin?style=flat-square&logo=github" alt="Pull Requests">
    </a>
  </p>
  
  [Features](#-what-does-it-do) • [Installation](#-quick-start) • [Configuration](#-configuration) • [Support](#-support)
</div>

---

## 🎯 What is NexiScope?

**NexiScope** transforms your Jenkins pipelines into a goldmine of insights. This plugin automatically captures every build, every stage, and every metric from your CI/CD pipelines and streams them to the NexiScope platform where AI-powered analytics help you:

- 🔍 **Identify bottlenecks** in your build process
- 📊 **Track trends** across thousands of builds
- 🚨 **Detect failures** before they become problems
- ⚡ **Optimize performance** with data-driven decisions
- 📈 **Visualize progress** with beautiful dashboards

### How It Works

```
┌─────────────┐         ┌──────────────┐         ┌─────────────────┐
│   Jenkins   │ ──────> │   NexiScope  │ ──────> │   AI-Powered    │
│  Pipelines  │  Stream │    Plugin    │  Sends  │    Analytics    │
└─────────────┘         └──────────────┘         └─────────────────┘
                              │                            │
                              │                            │
                        Real-time Events            Insights & Alerts
```

**The plugin is invisible** - it monitors your pipelines without changing them, adding overhead, or requiring code changes.

---

## ✨ What Does It Do?

### Automatic Event Capture

The plugin automatically tracks:

- ✅ **Pipeline Lifecycle**: When builds start, complete, or fail
- ✅ **Stage Execution**: Duration and status of each pipeline stage
- ✅ **Build Metrics**: Queue time, execution time, resource usage
- ✅ **SCM Information**: Branch names, commit hashes, repositories
- ✅ **Custom Events**: Send your own events from pipeline scripts

### Real-time Streaming

- 🚀 **WebSocket Connection**: Low-latency event delivery
- 🔄 **Auto-Reconnection**: Handles network issues gracefully
- 📦 **Event Batching**: Efficient network usage (90% reduction)
- 💾 **Offline Queue**: Events are queued when disconnected

### Enterprise-Ready

- 🔒 **Secure**: Token-based authentication, encrypted connections
- ⚡ **Fast**: <1% CPU overhead, minimal memory footprint
- 🛡️ **Reliable**: Automatic error handling and recovery
- 📝 **Auditable**: Security event logging for compliance

---

## 🚀 Quick Start

### 1. Install the Plugin

**Option A: Jenkins Plugin Manager** (Recommended)
1. Go to **Manage Jenkins** → **Manage Plugins** → **Available**
2. Search for "**NexiScope Integration**"
3. Click **Install** and restart Jenkins

**Option B: Manual Installation**
1. Download the [latest release](https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases)
2. Go to **Manage Jenkins** → **Manage Plugins** → **Advanced**
3. Upload the `.hpi` file and restart Jenkins

### 2. Get Your Credentials

1. Log in to [NexiScope Platform](https://platform.nexiscope.com)
2. Navigate to **Settings** → **Integrations** → **Jenkins**
3. Copy your **Platform URL** and **Authentication Token**

### 3. Configure the Plugin

1. In Jenkins, go to **Manage Jenkins** → **NexiScope Integration**
2. Enter your credentials:
   - **Platform URL**: `https://api.nexiscope.com`
   - **Authentication Token**: `your-token-here`
   - **Instance ID**: `jenkins-prod` (or any unique name)
3. Click **Test Connection** to verify
4. Click **Save Configuration**

🎉 **Done!** Your pipelines are now being monitored.

---

## ⚙️ Configuration

### Basic Settings

| Setting | Description | Example |
|---------|-------------|---------|
| **Platform URL** | NexiScope API endpoint | `https://api.nexiscope.com` |
| **Authentication Token** | Your API token | `nxs_abc123...` |
| **Instance ID** | Unique name for this Jenkins | `jenkins-prod-01` |
| **Enable Plugin** | Turn monitoring on/off | ✅ Enabled |

### Optional Features

<details>
<summary><b>Event Filtering</b> - Choose which events to send</summary>

Filter events by:
- **Job Names**: Include/exclude specific jobs using regex
- **Branch Names**: Filter by Git branch patterns
- **Event Types**: Select which event types to capture

Example: Only monitor production deployments
```
Job Include Pattern: prod-.*
Branch Include Pattern: main|release/.*
```
</details>

<details>
<summary><b>Log Streaming</b> - Stream build logs to NexiScope</summary>

Enable log streaming to capture console output:
- **Log Level Filter**: INFO, WARNING, SEVERE
- **Max Log Lines**: Limit per build (default: 1000)

⚠️ Note: This increases data transfer. Use filters to reduce volume.
</details>

<details>
<summary><b>Performance Tuning</b> - Optimize for your environment</summary>

Adjust batching settings:
- **Batch Size**: Events per batch (default: 50)
- **Batch Timeout**: Max wait time in ms (default: 1000)
- **Queue Size**: Max events to queue when offline (default: 1000)

Recommended for high-volume Jenkins:
```
Batch Size: 100
Batch Timeout: 500ms
Queue Size: 10000
```
</details>

---

## 📊 Usage

### Viewing Your Data

Once configured, all pipeline data automatically flows to NexiScope. Access your insights at:

🌐 **[platform.nexiscope.com](https://platform.nexiscope.com)**

### Sending Custom Events

Add custom tracking to your pipelines:

```groovy
pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                // Your deployment steps
                sh './deploy.sh'
                
                // Send custom event
                nexiscopeSendEvent(
                    eventType: 'DEPLOYMENT_COMPLETED',
                    data: [
                        environment: 'production',
                        version: '1.2.3',
                        duration: 120
                    ]
                )
            }
        }
    }
}
```

### Monitoring Plugin Status

Check if the plugin is working:

1. **Jenkins Logs**: Look for `NexiScope` entries
   ```
   [INFO] NexiScope: WebSocket connection established
   [INFO] NexiScope: Authentication successful
   ```

2. **Test Connection**: Use the "Test Connection" button in configuration

3. **NexiScope Dashboard**: Verify events are appearing in real-time

---

## 🔒 Security & Privacy

### What Data Is Collected?

The plugin sends:
- ✅ Build metadata (job name, number, status, duration)
- ✅ Stage information (name, status, timing)
- ✅ SCM data (branch, commit hash)
- ✅ Performance metrics (queue time, execution time)

The plugin **does NOT** send:
- ❌ Source code
- ❌ Secrets or credentials
- ❌ Build artifacts
- ❌ Environment variables (unless explicitly included in custom events)

### Security Features

- 🔐 **Encrypted Transport**: All data sent over HTTPS/WSS
- 🔑 **Token Authentication**: Secure API token (stored encrypted)
- 🛡️ **Rate Limiting**: Prevents abuse (10,000 events/minute)
- 📝 **Audit Logging**: Tracks all security-relevant events
- 🎯 **Input Validation**: All inputs sanitized and validated

---

## ⚡ Performance

### Impact on Jenkins

The plugin is designed to be invisible:

| Metric | Impact |
|--------|--------|
| **CPU Usage** | <1% overhead |
| **Memory** | ~50MB |
| **Network** | ~1KB per event (batched) |
| **Build Time** | No measurable impact |

### Optimization

Event batching reduces network traffic by **90%**:
- Individual events: ~100 messages/second
- Batched events: ~1000+ events/second

---

## 🔧 Troubleshooting

### Connection Issues

**Problem**: "Failed to connect to NexiScope platform"

**Solutions**:
1. Verify the Platform URL is correct
2. Check firewall allows outbound HTTPS (port 443)
3. Test from Jenkins server: `curl -I https://api.nexiscope.com`
4. Verify network connectivity

### Authentication Failures

**Problem**: "Authentication failed" or "Invalid token"

**Solutions**:
1. Verify token is copied correctly (no extra spaces)
2. Check token hasn't expired
3. Ensure token has `jenkins-integration` permission
4. Generate a new token if needed

### Events Not Appearing

**Problem**: No events showing in NexiScope dashboard

**Solutions**:
1. Verify plugin is **enabled** in configuration
2. Check **Test Connection** passes
3. Look for errors in Jenkins logs: **Manage Jenkins** → **System Log**
4. Verify at least one pipeline has run since configuration

### Need More Help?

- 📖 **Documentation**: [docs.nexiscope.com/jenkins-plugin](https://docs.nexiscope.com/jenkins-plugin)
- 💬 **Community**: [community.nexiscope.com](https://community.nexiscope.com)
- 📧 **Support**: support@nexiscope.com

---

## 🛠️ For Developers

<details>
<summary><b>Development Setup</b></summary>

### Prerequisites
- Java 21+
- Maven 3.9+

### Build from Source

```bash
# Clone repository
git clone https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin.git
cd jenkins-plugin/apps/jenkins-plugin

# Build plugin
mvn clean package

# Output: target/nexiscope-integration.hpi
```

### Local Development

```bash
# Run plugin in development mode
mvn hpi:run

# Access Jenkins at http://localhost:8080/jenkins
# The plugin will be automatically loaded

# For custom Jenkins setup, see Developer Guide
```

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Documentation

- **User Guide**: [docs/USER_GUIDE.md](docs/USER_GUIDE.md)
- **Developer Guide**: [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)
- **Architecture**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- **API Reference**: [docs/API_REFERENCE.md](docs/API_REFERENCE.md)
- **API Docs**: Run `mvn javadoc:javadoc`

</details>

---

## 📚 Additional Resources

### Documentation
- 📖 [User Guide](docs/USER_GUIDE.md) - Installation, configuration, and usage
- 🏗️ [Architecture Guide](docs/ARCHITECTURE.md) - Technical architecture and design
- 📚 [API Reference](docs/API_REFERENCE.md) - Pipeline DSL and Java API
- 🔒 [Security Guide](SECURITY.md) - Security features and best practices

### Project
- 📝 [Changelog](CHANGELOG.md) - Version history and release notes
- 🐛 [Issue Tracker](https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/issues)
- 💡 [Feature Requests](https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/discussions)

---

## 🤝 Support

### Getting Help

- **Documentation**: https://docs.nexiscope.com/jenkins-plugin
- **Community Forum**: https://community.nexiscope.com
- **Email Support**: support@nexiscope.com
- **GitHub Issues**: https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/issues

### Reporting Issues

When reporting issues, please include:
1. Jenkins version
2. Plugin version
3. Error message from logs
4. Steps to reproduce

### Contributing

We welcome contributions! See our [Developer Guide](docs/DEVELOPER_GUIDE.md) for:
- Development setup
- Code style guidelines
- Testing requirements
- Pull request process

---

## 📄 License

This plugin is proprietary software owned by NexiScope. It is designed exclusively for use with the NexiScope Platform. See the [LICENSE](LICENSE) file for complete terms and conditions.

**Key Points:**
- ✅ Free to use with NexiScope Platform
- ✅ Modify and customize for your needs
- ✅ Part of the NexiScope ecosystem
- 📧 Questions? Contact: legal@nexiscope.com

---

## 🙏 Acknowledgments

Built with ❤️ by the NexiScope team using:
- [Jenkins](https://jenkins.io) - The leading open source automation server
- [OkHttp](https://square.github.io/okhttp/) - Efficient HTTP & WebSocket client
- [JUnit](https://junit.org/) - Testing framework

---

<div align="center">
  
  **Ready to gain insights into your CI/CD pipelines?**
  
  [Get Started](https://platform.nexiscope.com/signup) • [View Demo](https://demo.nexiscope.com) • [Contact Sales](mailto:sales@nexiscope.com)
  
  ---
  
  Made with ❤️ by [NexiScope](https://nexiscope.com)
  
</div>
