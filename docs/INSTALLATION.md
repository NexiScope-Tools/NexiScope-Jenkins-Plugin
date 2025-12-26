# 📦 NexiScope Jenkins Plugin - Installation Guide

This guide covers all methods for installing the NexiScope Jenkins Plugin.

---

## 📋 Prerequisites

Before installing, ensure you have:

- **Jenkins**: Version 2.528.3 or later
- **Java**: Version 21 or later
- **NexiScope Platform Account**: Sign up at [nexiscope.com](https://nexiscope.com)
- **Admin Access**: Jenkins administrator privileges required

---

## 🚀 Installation Methods

### Method 1: Via Jenkins UI (Recommended)

This is the easiest method for most users.

#### Step 1: Download the Plugin

1. Go to [GitHub Releases](https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases)
2. Download the latest `nexiscope-integration-X.Y.Z.hpi` file
3. Optionally, download the `.sha256` file to verify integrity

#### Step 2: Verify Download (Optional but Recommended)

```bash
# Verify the checksum
sha256sum -c nexiscope-integration-X.Y.Z.hpi.sha256

# Or manually compare
sha256sum nexiscope-integration-X.Y.Z.hpi
```

#### Step 3: Upload to Jenkins

1. Open Jenkins in your browser
2. Navigate to: **Manage Jenkins** → **Manage Plugins**
3. Click the **Advanced** tab
4. Scroll to **Upload Plugin** section
5. Click **Choose File** and select the downloaded `.hpi` file
6. Click **Upload**

#### Step 4: Restart Jenkins

1. Jenkins will prompt you to restart
2. Click **Restart Jenkins when installation is complete and no jobs are running**
3. Wait for Jenkins to restart

#### Step 5: Verify Installation

1. Go to: **Manage Jenkins** → **Manage Plugins** → **Installed**
2. Search for "NexiScope"
3. Confirm the plugin is listed and enabled

---

### Method 2: Via Jenkins CLI

For automation or remote installation.

#### Step 1: Download the Plugin

```bash
# Download latest release
wget https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases/download/vX.Y.Z/nexiscope-integration-X.Y.Z.hpi

# Verify checksum
wget https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases/download/vX.Y.Z/nexiscope-integration-X.Y.Z.hpi.sha256
sha256sum -c nexiscope-integration-X.Y.Z.hpi.sha256
```

#### Step 2: Install via CLI

```bash
# Install the plugin
java -jar jenkins-cli.jar -s http://your-jenkins-url/ install-plugin /path/to/nexiscope-integration-X.Y.Z.hpi

# Restart Jenkins
java -jar jenkins-cli.jar -s http://your-jenkins-url/ safe-restart
```

#### Step 3: Verify Installation

```bash
# List installed plugins
java -jar jenkins-cli.jar -s http://your-jenkins-url/ list-plugins | grep nexiscope
```

---

### Method 3: Manual File System Installation

For advanced users or containerized environments.

#### Step 1: Download the Plugin

```bash
wget https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases/download/vX.Y.Z/nexiscope-integration-X.Y.Z.hpi
```

#### Step 2: Copy to Jenkins Plugins Directory

```bash
# Default Jenkins home directory
JENKINS_HOME=/var/lib/jenkins

# Copy plugin
sudo cp nexiscope-integration-X.Y.Z.hpi $JENKINS_HOME/plugins/nexiscope-integration.hpi

# Set correct ownership
sudo chown jenkins:jenkins $JENKINS_HOME/plugins/nexiscope-integration.hpi
```

#### Step 3: Restart Jenkins

```bash
# Using systemd
sudo systemctl restart jenkins

# Or using service
sudo service jenkins restart
```

---

### Method 4: Docker Installation

For Jenkins running in Docker containers.

#### Option A: Volume Mount

```bash
# Download plugin
wget https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases/download/vX.Y.Z/nexiscope-integration-X.Y.Z.hpi

# Copy to Jenkins volume
docker cp nexiscope-integration-X.Y.Z.hpi jenkins:/var/jenkins_home/plugins/nexiscope-integration.hpi

# Restart container
docker restart jenkins
```

#### Option B: Dockerfile

```dockerfile
FROM jenkins/jenkins:lts

# Install NexiScope plugin
RUN jenkins-plugin-cli --plugins \
    https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases/download/vX.Y.Z/nexiscope-integration-X.Y.Z.hpi
```

#### Option C: Docker Compose

```yaml
version: '3.8'
services:
  jenkins:
    image: jenkins/jenkins:lts
    ports:
      - "8080:8080"
      - "50000:50000"
    volumes:
      - jenkins_home:/var/jenkins_home
      - ./nexiscope-integration-X.Y.Z.hpi:/var/jenkins_home/plugins/nexiscope-integration.hpi
    environment:
      - JAVA_OPTS=-Djenkins.install.runSetupWizard=false

volumes:
  jenkins_home:
```

---

### Method 5: Kubernetes Installation

For Jenkins running on Kubernetes.

#### Using Helm Chart

```yaml
# values.yaml
controller:
  installPlugins:
    - https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases/download/vX.Y.Z/nexiscope-integration-X.Y.Z.hpi

# Install/upgrade
helm upgrade --install jenkins jenkins/jenkins -f values.yaml
```

#### Using Init Container

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jenkins
spec:
  template:
    spec:
      initContainers:
      - name: install-nexiscope-plugin
        image: curlimages/curl:latest
        command:
        - sh
        - -c
        - |
          curl -L -o /plugins/nexiscope-integration.hpi \
            https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases/download/vX.Y.Z/nexiscope-integration-X.Y.Z.hpi
        volumeMounts:
        - name: plugins
          mountPath: /plugins
      containers:
      - name: jenkins
        image: jenkins/jenkins:lts
        volumeMounts:
        - name: plugins
          mountPath: /var/jenkins_home/plugins
      volumes:
      - name: plugins
        emptyDir: {}
```

---

## ⚙️ Post-Installation Configuration

After installing the plugin, you need to configure it:

### Step 1: Access Configuration

1. Go to: **Manage Jenkins** → **NexiScope Integration**
2. Or: **Manage Jenkins** → **Configure System** → Scroll to "NexiScope Integration"

### Step 2: Required Settings

Configure the following required fields:

| Setting | Description | Example |
|---------|-------------|---------|
| **Platform URL** | Your NexiScope platform endpoint | `https://api.nexiscope.com` or `wss://api.nexiscope.com` |
| **Authentication Token** | API token from NexiScope platform | `nx_abc123...` |
| **Instance ID** | Unique identifier for this Jenkins | `jenkins-prod-01` |

### Step 3: Optional Settings

Configure optional features:

- **Event Batching**: Batch size and timeout (default: 100 events, 5 seconds)
- **Event Filtering**: Filter by job name, branch, or event type
- **Log Streaming**: Enable/disable build log streaming
- **Connection Settings**: Timeout and retry configuration

### Step 4: Test Connection

1. Click **Test Connection** button
2. Verify you see: ✅ "Connection successful"
3. If errors occur, check:
   - Platform URL is correct and accessible
   - Authentication token is valid
   - Firewall allows outbound connections
   - Jenkins can resolve DNS

### Step 5: Save Configuration

1. Click **Save** at the bottom of the page
2. Configuration is now active

---

## 🔄 Updating the Plugin

### Via Jenkins UI

1. Download the new version `.hpi` file
2. Follow the same upload process as installation
3. Jenkins will replace the old version
4. Restart Jenkins

### Via CLI

```bash
# Download new version
wget https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/releases/download/vX.Y.Z/nexiscope-integration-X.Y.Z.hpi

# Install (will update existing)
java -jar jenkins-cli.jar -s http://your-jenkins-url/ install-plugin /path/to/nexiscope-integration-X.Y.Z.hpi

# Restart
java -jar jenkins-cli.jar -s http://your-jenkins-url/ safe-restart
```

### Automated Updates

Currently, automatic updates via Jenkins Update Center are not supported. You must manually update the plugin when new versions are released.

**Stay Informed:**
- Watch the [GitHub repository](https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin) for releases
- Subscribe to release notifications
- Check the [CHANGELOG](../CHANGELOG.md) for updates

---

## 🗑️ Uninstalling the Plugin

### Via Jenkins UI

1. Go to: **Manage Jenkins** → **Manage Plugins** → **Installed**
2. Find "NexiScope Integration"
3. Click the **Uninstall** button
4. Restart Jenkins

### Via File System

```bash
# Remove plugin file
sudo rm $JENKINS_HOME/plugins/nexiscope-integration.hpi
sudo rm -rf $JENKINS_HOME/plugins/nexiscope-integration/

# Restart Jenkins
sudo systemctl restart jenkins
```

---

## 🐛 Troubleshooting

### Plugin Not Appearing After Installation

**Possible causes:**
- Jenkins didn't restart properly
- Plugin file is corrupted
- Incompatible Jenkins version

**Solutions:**
```bash
# Check plugin file exists
ls -lh $JENKINS_HOME/plugins/nexiscope-integration.*

# Check Jenkins logs
tail -f $JENKINS_HOME/logs/jenkins.log

# Force restart
sudo systemctl restart jenkins
```

### Installation Fails with "Dependency Error"

**Cause:** Missing required plugins or incompatible versions

**Solution:**
```bash
# Check Jenkins version
java -jar jenkins-cli.jar -s http://your-jenkins-url/ version

# Ensure Jenkins >= 2.528.3 and Java >= 21
```

### Permission Denied Errors

**Cause:** Incorrect file permissions

**Solution:**
```bash
# Fix permissions
sudo chown -R jenkins:jenkins $JENKINS_HOME/plugins/
sudo chmod 644 $JENKINS_HOME/plugins/*.hpi
```

### Plugin Fails to Load

**Check Jenkins logs:**
```bash
# System log
tail -f /var/log/jenkins/jenkins.log

# Or in Jenkins UI
# Manage Jenkins → System Log → All Jenkins Logs
```

---

## 📚 Next Steps

After installation:

1. **Configure the Plugin**: See [Configuration](#️-post-installation-configuration) above
2. **Read User Guide**: [docs/USER_GUIDE.md](USER_GUIDE.md)
3. **Test with a Pipeline**: Run a simple pipeline to verify events are captured
4. **Check NexiScope Platform**: Verify events appear in your NexiScope dashboard

---

## 🆘 Support

Need help with installation?

- 💬 **Discussions**: https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/discussions
- 🐛 **Issues**: https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/issues
- 📧 **Email**: support@nexiscope.com
- 📚 **Documentation**: https://docs.nexiscope.com/jenkins-plugin

---

## 📋 Version Compatibility

| Plugin Version | Jenkins Version | Java Version |
|----------------|-----------------|--------------|
| 1.x.x          | 2.528.3+        | 21+          |

Always use the latest plugin version for the best experience and security updates.

