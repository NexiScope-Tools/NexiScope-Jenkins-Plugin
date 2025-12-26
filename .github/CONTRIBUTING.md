# Contributing to NexiScope Jenkins Plugin

Thank you for your interest in contributing to the NexiScope Jenkins Plugin! We welcome contributions from the community.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing](#testing)
- [Documentation](#documentation)
- [Community](#community)

---

## Code of Conduct

This project adheres to a code of conduct. By participating, you are expected to uphold this code. Please be respectful and constructive in all interactions.

---

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally
3. **Set up the development environment** (see below)
4. **Create a branch** for your changes
5. **Make your changes** and test them
6. **Submit a pull request**

---

## Development Setup

### Prerequisites

- Java 21+
- Maven 3.9+
- Git

### Setup Steps

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/NexiScope-Jenkins-Plugin.git
cd NexiScope-Jenkins-Plugin/apps/jenkins-plugin

# Build the plugin
mvn clean package

# Run tests
mvn test

# Run plugin in development mode
mvn hpi:run
# Access at http://localhost:8080/jenkins
```

For detailed setup instructions, see [Developer Guide](docs/DEVELOPER_GUIDE.md).

---

## How to Contribute

### Reporting Bugs

- Use the [Bug Report template](.github/ISSUE_TEMPLATE/bug_report.md)
- Search existing issues first to avoid duplicates
- Include as much detail as possible
- Provide steps to reproduce
- Include logs and screenshots

### Suggesting Features

- Use the [Feature Request template](.github/ISSUE_TEMPLATE/feature_request.md)
- Explain the use case and benefits
- Consider implementation complexity
- Be open to discussion

### Improving Documentation

- Use the [Documentation Issue template](.github/ISSUE_TEMPLATE/documentation.md)
- Documentation is as important as code
- Fix typos, improve clarity, add examples
- Keep documentation up-to-date with code changes

### Asking Questions

- Use [GitHub Discussions](https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/discussions)
- Search existing discussions first
- Be clear and specific
- Help others when you can

---

## Pull Request Process

### Before Submitting

1. **Create an issue** first (unless it's a trivial change)
2. **Discuss your approach** in the issue
3. **Fork and branch** from `main`
4. **Follow coding standards** (see below)
5. **Write tests** for new functionality
6. **Update documentation** as needed
7. **Run all tests** and ensure they pass
8. **Keep commits clean** and well-described

### PR Guidelines

- Use the [Pull Request template](.github/PULL_REQUEST_TEMPLATE.md)
- Link to the related issue
- Provide a clear description of changes
- Include test results
- Add screenshots for UI changes
- Keep PRs focused and reasonably sized
- Respond to review feedback promptly

### PR Checklist

- [ ] Code follows project style guidelines
- [ ] Self-reviewed my own code
- [ ] Commented complex code sections
- [ ] Updated documentation
- [ ] Added/updated tests
- [ ] All tests pass locally
- [ ] No new warnings or errors
- [ ] Linked to related issue

---

## Coding Standards

### Java Code Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use meaningful variable and method names
- Add JavaDoc for public methods and classes

### Code Quality

- Write clean, readable code
- Keep methods small and focused
- Avoid code duplication
- Handle errors appropriately
- Use appropriate design patterns
- Follow SOLID principles

### Example

```java
/**
 * Validates the platform URL format.
 * 
 * @param url The URL to validate
 * @return FormValidation result
 */
public FormValidation doCheckPlatformUrl(@QueryParameter String url) {
    if (StringUtils.isBlank(url)) {
        return FormValidation.error("Platform URL is required");
    }
    
    if (!url.startsWith("https://") && !url.startsWith("wss://")) {
        return FormValidation.error("URL must start with https:// or wss://");
    }
    
    return FormValidation.ok();
}
```

---

## Testing

### Test Requirements

- Write tests for all new functionality
- Update tests for modified functionality
- Ensure all tests pass before submitting PR
- Aim for high code coverage (>80%)

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=YourTestClass

# Run with coverage
mvn test jacoco:report
# View report at target/site/jacoco/index.html
```

### Test Types

1. **Unit Tests**: Test individual components
2. **Integration Tests**: Test component interactions
3. **Jenkins Tests**: Use `JenkinsRule` for Jenkins-specific tests

### Example Test

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

---

## Documentation

### Documentation Standards

- Keep documentation up-to-date
- Use clear, concise language
- Provide examples
- Include screenshots for UI features
- Update all relevant docs when making changes

### Documentation Locations

- `README.md` - User-facing overview
- `docs/USER_GUIDE.md` - User documentation
- `docs/DEVELOPER_GUIDE.md` - Developer documentation
- `docs/ARCHITECTURE.md` - Technical architecture
- `docs/API_REFERENCE.md` - API documentation
- JavaDoc - Code documentation

---

## Community

### Communication Channels

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: Questions and general discussion
- **Pull Requests**: Code contributions and reviews

### Getting Help

- Read the [documentation](docs/)
- Search existing issues and discussions
- Ask in GitHub Discussions
- Be patient and respectful

### Helping Others

- Answer questions in discussions
- Review pull requests
- Improve documentation
- Share your experience

---

## Recognition

Contributors will be recognized in:
- Release notes
- CHANGELOG.md
- GitHub contributors page

---

## License and Contributions

By contributing to this project, you agree that:

1. **Ownership**: All contributions become the property of NexiScope
2. **License Grant**: You grant NexiScope a perpetual, worldwide, non-exclusive, royalty-free license to use, modify, and distribute your contributions
3. **Rights**: You represent that you have the right to grant such license
4. **Proprietary**: This is proprietary software, not open source
5. **Commercial Use**: NexiScope may use contributions in its commercial products

For questions about contributions and licensing, contact: legal@nexiscope.com

---

## Questions?

If you have questions about contributing, please:
1. Check the [Developer Guide](docs/DEVELOPER_GUIDE.md)
2. Search [GitHub Discussions](https://github.com/NexiScope-Tools/NexiScope-Jenkins-Plugin/discussions)
3. Open a new discussion if needed

Thank you for contributing! 🎉

