/**
 * NexiScope Jenkins Plugin - Real-time observability integration for Jenkins.
 * 
 * <h1>Overview</h1>
 * <p>The NexiScope Jenkins Plugin provides comprehensive visibility into CI/CD pipelines
 * by capturing and streaming execution data to the NexiScope platform in real-time. The
 * plugin is designed to be non-intrusive, performant, and secure.</p>
 * 
 * <h2>Core Components</h2>
 * 
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@link io.nexiscope.jenkins.plugin.NexiScopeGlobalConfiguration} - Global plugin configuration</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.NexiScopeConfigurationAction} - Dedicated configuration page</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.config.ConfigurationValidator} - Configuration validation</li>
 * </ul>
 * 
 * <h3>Event System</h3>
 * <ul>
 *   <li>{@link io.nexiscope.jenkins.plugin.events.EventMapper} - Event transformation and mapping</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.events.EventQueue} - Event queuing for offline resilience</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.events.ScmInfoExtractor} - SCM information extraction</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.events.ExecutionGraph} - Pipeline execution graph tracking</li>
 * </ul>
 * 
 * <h3>Event Listeners</h3>
 * <ul>
 *   <li>{@link io.nexiscope.jenkins.plugin.listeners.PipelineEventListener} - Pipeline lifecycle events</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.listeners.StageEventListener} - Stage-level events</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.listeners.FlowNodeEventListener} - Flow node events</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.listeners.FreestyleBuildListener} - Freestyle build events</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.listeners.MatrixBuildListener} - Matrix build events</li>
 * </ul>
 * 
 * <h3>WebSocket Communication</h3>
 * <ul>
 *   <li>{@link io.nexiscope.jenkins.plugin.websocket.WebSocketClient} - WebSocket client with reconnection</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.websocket.EventBatcher} - Event batching for performance</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.websocket.ConnectionTester} - Connection testing utility</li>
 * </ul>
 * 
 * <h3>Security</h3>
 * <ul>
 *   <li>{@link io.nexiscope.jenkins.plugin.security.InputValidator} - Input validation utilities</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.security.RateLimiter} - Rate limiting implementation</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.security.AuditLogger} - Security audit logging</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.security.CertificatePinningConfig} - Certificate pinning</li>
 * </ul>
 * 
 * <h3>Performance</h3>
 * <ul>
 *   <li>{@link io.nexiscope.jenkins.plugin.performance.MemoryOptimizer} - Memory optimization utilities</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.metrics.PerformanceMetrics} - Performance tracking</li>
 * </ul>
 * 
 * <h3>Error Handling</h3>
 * <ul>
 *   <li>{@link io.nexiscope.jenkins.plugin.errors.ErrorHandler} - Centralized error handling</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.errors.ErrorMessages} - User-friendly error messages</li>
 *   <li>{@link io.nexiscope.jenkins.plugin.errors.CircuitBreaker} - Circuit breaker pattern</li>
 * </ul>
 * 
 * <h3>Pipeline Steps</h3>
 * <ul>
 *   <li>{@link io.nexiscope.jenkins.plugin.steps.SendCustomEventStep} - Send custom events from pipelines</li>
 * </ul>
 * 
 * <h2>Architecture</h2>
 * 
 * <h3>Event Flow</h3>
 * <pre>
 * Jenkins Pipeline
 *       ↓
 * Event Listeners (via @Extension)
 *       ↓
 * Event Mapper (transform to JSON)
 *       ↓
 * Event Filter (optional filtering)
 *       ↓
 * Event Queue (offline resilience)
 *       ↓
 * Event Batcher (performance optimization)
 *       ↓
 * WebSocket Client (send to platform)
 *       ↓
 * NexiScope Platform
 * </pre>
 * 
 * <h3>Key Design Patterns</h3>
 * <ul>
 *   <li><b>Singleton</b>: WebSocketClient, RateLimiter, AuditLogger</li>
 *   <li><b>Observer</b>: Event listeners using Jenkins @Extension mechanism</li>
 *   <li><b>Strategy</b>: Event filtering with configurable patterns</li>
 *   <li><b>Circuit Breaker</b>: Automatic failure detection and recovery</li>
 *   <li><b>Batch Processing</b>: Event batching for network efficiency</li>
 * </ul>
 * 
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>CPU Overhead</b>: &lt;1% under normal load</li>
 *   <li><b>Memory Usage</b>: ~50MB for plugin + event queue</li>
 *   <li><b>Network Efficiency</b>: 90% reduction via batching</li>
 *   <li><b>Throughput</b>: 1000+ events/second sustained</li>
 *   <li><b>Latency</b>: &lt;1 second average event delivery</li>
 * </ul>
 * 
 * <h2>Security Features</h2>
 * <ul>
 *   <li><b>Input Validation</b>: Comprehensive validation of all user inputs</li>
 *   <li><b>Rate Limiting</b>: Per-operation rate limits to prevent abuse</li>
 *   <li><b>Audit Logging</b>: Security event tracking for compliance</li>
 *   <li><b>Secrets Management</b>: Encrypted token storage via Jenkins Secret</li>
 *   <li><b>Certificate Pinning</b>: Optional certificate pinning for production</li>
 * </ul>
 * 
 * <h2>Configuration</h2>
 * <p>The plugin is configured via Jenkins UI at: <b>Manage Jenkins → NexiScope Integration</b></p>
 * 
 * <h3>Required Settings</h3>
 * <ul>
 *   <li><b>Platform URL</b>: NexiScope platform endpoint (HTTPS/WSS)</li>
 *   <li><b>Authentication Token</b>: API token from NexiScope platform</li>
 *   <li><b>Instance ID</b>: Unique identifier for this Jenkins instance</li>
 * </ul>
 * 
 * <h3>Optional Settings</h3>
 * <ul>
 *   <li><b>Event Batching</b>: Batch size and timeout configuration</li>
 *   <li><b>Event Filtering</b>: Filter events by job, branch, or type</li>
 *   <li><b>Log Streaming</b>: Stream build logs to platform</li>
 *   <li><b>Certificate Pinning</b>: Pin server certificates</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Sending Custom Events</h3>
 * <pre>
 * pipeline {
 *     agent any
 *     stages {
 *         stage('Deploy') {
 *             steps {
 *                 // Send custom event
 *                 nexiscopeSendEvent(
 *                     eventType: 'DEPLOYMENT_STARTED',
 *                     data: [
 *                         environment: 'production',
 *                         version: '1.2.3'
 *                     ]
 *                 )
 *                 
 *                 // Deploy...
 *                 
 *                 nexiscopeSendEvent(eventType: 'DEPLOYMENT_COMPLETED')
 *             }
 *         }
 *     }
 * }
 * </pre>
 * 
 * <h2>Troubleshooting</h2>
 * <p>For troubleshooting information, see:</p>
 * <ul>
 *   <li><a href="https://docs.nexiscope.com/jenkins-plugin/troubleshooting">Troubleshooting Guide</a></li>
 *   <li><a href="https://docs.nexiscope.com/jenkins-plugin/faq">FAQ</a></li>
 *   <li><a href="https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/issues">GitHub Issues</a></li>
 * </ul>
 * 
 * @author NexiScope Team
 * @version 1.0.0
 * @since 1.0.0
 */
package io.nexiscope.jenkins.plugin;

