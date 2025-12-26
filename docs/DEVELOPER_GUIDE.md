# 🛠️ NexiScope Jenkins Plugin - Developer Guide

## Table of Contents

- [Getting Started](#getting-started)
- [Development Environment](#development-environment)
- [Project Structure](#project-structure)
- [Building the Plugin](#building-the-plugin)
- [Testing](#testing)
- [Debugging](#debugging)
- [Contributing](#contributing)
- [Release Process](#release-process)

---

## Getting Started

This guide is for developers who want to contribute to or modify the NexiScope Jenkins Plugin.

### Prerequisites

- **Java 21+** (JDK)
- **Maven 3.9+**
- **Git**
- **IDE** (IntelliJ IDEA recommended)

---

## Development Environment

### Clone the Repository

```bash
git clone https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin.git
cd NexiScope-Jenkins-Plugin/apps/jenkins-plugin
```

### IDE Setup

#### IntelliJ IDEA

1. Open the project: `File` → `Open` → Select `pom.xml`
2. Enable annotation processing: `Settings` → `Build, Execution, Deployment` → `Compiler` → `Annotation Processors`
3. Install recommended plugins:
   - Maven Helper
   - Jenkins Control Plugin

#### VS Code

1. Install extensions:
   - Extension Pack for Java
   - Maven for Java
2. Open the folder in VS Code
3. Maven will auto-detect the project

### Local Jenkins Instance

Use Maven's built-in HPI plugin to run Jenkins locally:

```bash
mvn hpi:run
```

Access Jenkins at: http://localhost:8080/jenkins

The plugin will be automatically loaded and available for testing. Jenkins will run with a temporary workspace that's cleaned up when you stop the server.

---

## Project Structure

```
apps/jenkins-plugin/
├── src/
│   ├── main/
│   │   ├── java/io/nexiscope/jenkins/plugin/
│   │   │   ├── NexiScopePlugin.java              # Main plugin class
│   │   │   ├── NexiScopeGlobalConfiguration.java # Global config
│   │   │   ├── events/                           # Event handling
│   │   │   │   ├── EventMapper.java
│   │   │   │   ├── EventQueue.java
│   │   │   │   └── ScmInfoExtractor.java
│   │   │   ├── listeners/                        # Jenkins event listeners
│   │   │   │   ├── PipelineEventListener.java
│   │   │   │   ├── StageEventListener.java
│   │   │   │   └── FlowNodeEventListener.java
│   │   │   ├── websocket/                        # WebSocket client
│   │   │   │   ├── WebSocketClient.java
│   │   │   │   ├── ConnectionTester.java
│   │   │   │   └── EventBatcher.java
│   │   │   ├── security/                         # Security utilities
│   │   │   │   ├── InputValidator.java
│   │   │   │   ├── RateLimiter.java
│   │   │   │   └── AuditLogger.java
│   │   │   ├── performance/                      # Performance optimization
│   │   │   │   └── MemoryOptimizer.java
│   │   │   ├── filters/                          # Log filtering
│   │   │   │   └── LogStreamingFilter.java
│   │   │   ├── steps/                            # Pipeline steps
│   │   │   │   └── SendCustomEventStep.java
│   │   │   └── errors/                           # Error handling
│   │   │       ├── ErrorHandler.java
│   │   │       └── ErrorMessages.java
│   │   └── resources/
│   │       ├── index.jelly                       # Plugin description
│   │       └── io/nexiscope/jenkins/plugin/
│   │           ├── NexiScopeGlobalConfiguration/
│   │           │   └── config.jelly              # Config UI
│   │           └── NexiScopeConfigurationAction/
│   │               └── index.jelly               # Dedicated config page
│   └── test/
│       └── java/io/nexiscope/jenkins/plugin/     # Unit & integration tests
├── docs/                                          # Documentation
├── pom.xml                                        # Maven configuration
├── README.md                                      # User documentation
├── CHANGELOG.md                                   # Version history
└── SECURITY.md                                    # Security documentation
```

---

## Building the Plugin

### Full Build

```bash
mvn clean package
```

This will:
1. Compile Java sources
2. Run all tests
3. Generate JavaDoc
4. Create the `.hpi` file in `target/`

### Quick Build (Skip Tests)

```bash
mvn clean package -DskipTests
```

### Build Output

The plugin package will be created at:
```
target/nexiscope-integration.hpi
```

---

## Testing

### Run All Tests

```bash
mvn test
```

### Run Specific Test

```bash
mvn test -Dtest=WebSocketClientTest
```

### Test Coverage

Generate coverage report:

```bash
mvn clean test jacoco:report
```

View report at: `target/site/jacoco/index.html`

### Test Categories

1. **Unit Tests**: Test individual components in isolation
   - `EventMapperTest.java`
   - `InputValidatorTest.java`
   - `RateLimiterTest.java`

2. **Integration Tests**: Test component interactions
   - `WebSocketClientTest.java`
   - `EventBatcherTest.java`
   - `ConnectionTesterTest.java`

3. **Jenkins Tests**: Test with Jenkins test harness
   - `NexiScopeGlobalConfigurationTest.java`
   - `PipelineEventListenerTest.java`

### Writing Tests

Example unit test:

```java
@Test
public void testEventMapping() {
    WorkflowRun run = mock(WorkflowRun.class);
    when(run.getId()).thenReturn("123");
    
    Map<String, Object> event = EventMapper.mapPipelineStartEvent(run);
    
    assertEquals("pipeline.started", event.get("type"));
    assertEquals("123", event.get("buildId"));
}
```

Example Jenkins test:

```java
@Rule
public JenkinsRule jenkins = new JenkinsRule();

@Test
public void testConfiguration() throws Exception {
    NexiScopeGlobalConfiguration config = 
        NexiScopeGlobalConfiguration.get();
    
    config.setPlatformUrl("https://app.nexiscope.com");
    config.setAuthToken(Secret.fromString("test-token"));
    
    assertEquals("https://app.nexiscope.com", 
                 config.getPlatformUrl());
}
```

---

## Debugging

### Debug in IntelliJ IDEA

1. Create a new Maven run configuration:
   - Command: `hpi:run`
   - VM Options: `-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005`

2. Start the configuration

3. Create a Remote Debug configuration:
   - Host: `localhost`
   - Port: `5005`

4. Attach the debugger

### Debug with Maven HPI Plugin

The `hpi:run` command automatically enables debugging:

```bash
mvn hpi:run -Djetty.port=8080
```

By default, it listens on port 5005 for debugger connections. Attach your IDE's debugger to `localhost:5005`.

### Logging

Add debug logging:

```java
import java.util.logging.Logger;

private static final Logger LOGGER = 
    Logger.getLogger(YourClass.class.getName());

LOGGER.info("Info message");
LOGGER.warning("Warning message");
LOGGER.severe("Error message");
```

View logs in Jenkins: `Manage Jenkins` → `System Log`

---

## Contributing

### Code Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Add JavaDoc for all public methods

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add support for custom event metadata
fix: resolve WebSocket reconnection issue
docs: update installation instructions
test: add tests for EventBatcher
refactor: simplify error handling logic
```

### Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass: `mvn test`
6. Update documentation if needed
7. Commit your changes
8. Push to your fork
9. Create a Pull Request

### PR Checklist

- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] All tests passing
- [ ] Code follows style guide
- [ ] No new compiler warnings

---

## Release Process

### Version Numbering

We follow [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking changes (e.g., 2.0.0)
- **MINOR**: New features, backward compatible (e.g., 1.1.0)
- **PATCH**: Bug fixes, backward compatible (e.g., 1.0.1)

For detailed versioning guidelines, see [`.github/VERSIONING.md`](../.github/VERSIONING.md).

### Creating a Release (Automated)

We use an automated GitHub Actions workflow for releases. This ensures consistency and eliminates manual errors.

#### 🚀 Quick Release Process

1. **Ensure your changes are committed and pushed to `main`**

2. **Update CHANGELOG.md** with your changes under the `[Unreleased]` section:
```markdown
## [Unreleased]

### Added
- New feature X

### Changed
- Improved Y

### Fixed
- Bug Z
```

3. **Trigger the Version Bump workflow**:
   - Go to: **Actions** → **Version Bump** → **Run workflow**
   - Enter:
     - **Version to release**: `1.1.0` (no 'v' prefix, no -SNAPSHOT)
     - **Next development version**: `1.2.0-SNAPSHOT`
   - Click **Run workflow**

4. **The workflow automatically**:
   - ✅ Updates `pom.xml` to release version (`1.1.0`)
   - ✅ Updates `CHANGELOG.md` with release date
   - ✅ Creates commit: `chore: release version 1.1.0`
   - ✅ Creates and pushes git tag `v1.1.0`
   - ✅ Updates `pom.xml` to next SNAPSHOT (`1.2.0-SNAPSHOT`)
   - ✅ Prepares `CHANGELOG.md` for next release
   - ✅ Creates commit: `chore: prepare for next development iteration`

5. **Release workflow triggers automatically**:
   - Builds the plugin
   - Creates GitHub Release with tag `v1.1.0`
   - Attaches artifacts:
     - `nexiscope-integration-1.1.0.hpi`
     - `nexiscope-integration-1.1.0.jar`
     - SHA256 checksums

6. **Done!** 🎉 Your release is published.

#### 📋 Version Decision Guide

**When to bump MAJOR (X.0.0)**:
- Breaking API changes
- Removed deprecated features
- Major architectural changes
- Requires Jenkins version upgrade

**When to bump MINOR (1.X.0)**:
- New features (backward-compatible)
- New configuration options
- New pipeline steps
- Deprecated features (not removed)

**When to bump PATCH (1.1.X)**:
- Bug fixes
- Security patches
- Documentation updates
- Performance improvements (no API changes)

#### 🔧 Manual Release (Not Recommended)

If you need to create a release manually (e.g., for hotfixes):

```bash
# Update version
mvn versions:set -DnewVersion=1.0.1

# Update CHANGELOG.md manually

# Commit and tag
git add pom.xml CHANGELOG.md
git commit -m "chore: release version 1.0.1"
git tag -a v1.0.1 -m "Release v1.0.1"
git push origin main v1.0.1

# Prepare next version
mvn versions:set -DnewVersion=1.1.0-SNAPSHOT
git add pom.xml
git commit -m "chore: prepare for next development iteration"
git push origin main
```

### Jenkins Plugin Repository

To publish to the Jenkins Update Center:

1. Follow the [Jenkins plugin hosting guide](https://www.jenkins.io/doc/developer/publishing/)
2. Set up your credentials in `~/.m2/settings.xml`
3. Ensure you have commit access to the [Jenkins plugin repository](https://github.com/jenkinsci)
4. The release workflow can be extended to automatically publish to Jenkins Update Center

---

## Architecture

For detailed architecture information, see:
- [Architecture Guide](ARCHITECTURE.md)
- [API Reference](API_REFERENCE.md)

---

## Useful Commands

| Command | Description |
|---------|-------------|
| `mvn clean` | Clean build artifacts |
| `mvn compile` | Compile sources |
| `mvn test` | Run tests |
| `mvn package` | Build plugin package |
| `mvn hpi:run` | Run plugin in local Jenkins |
| `mvn javadoc:javadoc` | Generate JavaDoc |
| `mvn versions:display-dependency-updates` | Check for dependency updates |

---

## Resources

- [Jenkins Plugin Tutorial](https://www.jenkins.io/doc/developer/tutorial/)
- [Jenkins Plugin Development Guide](https://www.jenkins.io/doc/developer/plugin-development/)
- [Stapler Web Framework](https://stapler.kohsuke.org/)
- [Jelly Tag Reference](https://reports.jenkins.io/core-taglib/jelly-taglib-ref.html)

---

## Support

- 💬 **Discussions**: https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/discussions
- 🐛 **Issues**: https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/issues
- 📧 **Email**: developers@nexiscope.com

