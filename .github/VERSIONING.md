# Versioning Guide

This project follows [Semantic Versioning](https://semver.org/) (SemVer) for version management.

## Version Format

```
MAJOR.MINOR.PATCH[-SNAPSHOT]
```

- **MAJOR**: Incompatible API changes or major feature releases
- **MINOR**: New functionality in a backward-compatible manner
- **PATCH**: Backward-compatible bug fixes
- **SNAPSHOT**: Development version (not for release)

## Automated Version Management

We use GitHub Actions to automate version bumping, tagging, and releasing.

### 🚀 How to Release a New Version

1. **Go to GitHub Actions**
   - Navigate to: `Actions` → `Version Bump`
   - Click `Run workflow`

2. **Enter Version Information**
   - **Version to release**: e.g., `1.1.0` (no 'v' prefix, no -SNAPSHOT)
   - **Next development version**: e.g., `1.2.0-SNAPSHOT`

3. **The Workflow Will**:
   - ✅ Validate version formats
   - ✅ Update `pom.xml` to release version
   - ✅ Update `CHANGELOG.md` with release date
   - ✅ Commit and create git tag `v1.1.0`
   - ✅ Push tag (triggers release workflow)
   - ✅ Update `pom.xml` to next SNAPSHOT version
   - ✅ Prepare `CHANGELOG.md` for next release
   - ✅ Commit next development version

4. **Release Workflow Triggers Automatically**
   - Builds the plugin
   - Creates GitHub Release
   - Attaches `.hpi` and `.jar` artifacts

## Version Workflow Example

### Current State
```
pom.xml: 1.0.0-SNAPSHOT
CHANGELOG.md: [Unreleased] section with changes
```

### Run Version Bump
```
Version to release: 1.0.0
Next version: 1.1.0-SNAPSHOT
```

### After Version Bump
```
Commit 1: "chore: release version 1.0.0"
  - pom.xml: 1.0.0
  - CHANGELOG.md: [1.0.0] - 2025-12-26
  - Tag: v1.0.0

Commit 2: "chore: prepare for next development iteration 1.1.0-SNAPSHOT"
  - pom.xml: 1.1.0-SNAPSHOT
  - CHANGELOG.md: New [Unreleased] section
```

### GitHub Release Created
```
Release v1.0.0 with artifacts:
  - nexiscope-integration-1.0.0.hpi
  - nexiscope-integration-1.0.0.jar
  - Checksums (.sha256)
```

## Manual Version Bump (Not Recommended)

If you need to manually bump versions:

```bash
# Update pom.xml version
mvn versions:set -DnewVersion=1.1.0

# Update CHANGELOG.md manually

# Commit and tag
git add pom.xml CHANGELOG.md
git commit -m "chore: release version 1.1.0"
git tag -a v1.1.0 -m "Release v1.1.0"
git push origin main v1.1.0

# Prepare next development version
mvn versions:set -DnewVersion=1.2.0-SNAPSHOT
git add pom.xml
git commit -m "chore: prepare for next development iteration"
git push origin main
```

## CHANGELOG Management

### During Development

Add changes under the `[Unreleased]` section:

```markdown
## [Unreleased]

### Added
- New feature X

### Changed
- Improved Y

### Fixed
- Bug Z
```

### On Release

The version bump workflow automatically:
1. Converts `[Unreleased]` to `[1.1.0] - 2025-12-26`
2. Creates a new `[Unreleased]` section for future changes

## Version Decision Guide

### When to Bump MAJOR (X.0.0)
- Breaking API changes
- Removed deprecated features
- Major architectural changes
- Requires Jenkins version upgrade

### When to Bump MINOR (1.X.0)
- New features (backward-compatible)
- New configuration options
- New pipeline steps
- Deprecated features (not removed)

### When to Bump PATCH (1.1.X)
- Bug fixes
- Security patches
- Documentation updates
- Performance improvements (no API changes)

## Pre-release Versions

For beta/RC releases, use:
```
1.1.0-beta.1
1.1.0-rc.1
```

These can be created manually or by modifying the version bump workflow.

## Hotfix Process

For urgent fixes on a released version:

```bash
# Create hotfix branch from tag
git checkout -b hotfix/1.0.1 v1.0.0

# Make fixes
# Update version to 1.0.1
# Update CHANGELOG

# Commit, tag, and merge back
git commit -m "fix: critical bug"
git tag v1.0.1
git push origin hotfix/1.0.1 v1.0.1

# Merge to main
git checkout main
git merge hotfix/1.0.1
git push origin main
```

## Best Practices

1. **Always use the automated workflow** for releases
2. **Keep CHANGELOG.md updated** during development
3. **Follow conventional commits** for clear history
4. **Test thoroughly** before releasing
5. **Document breaking changes** clearly in CHANGELOG
6. **Use SNAPSHOT versions** for development
7. **Never modify released tags** (create new version instead)

## Troubleshooting

### Version Bump Failed

Check:
- Version format is correct (X.Y.Z)
- Next version ends with `-SNAPSHOT`
- No uncommitted changes
- GitHub token has write permissions

### Release Not Created

Check:
- Tag was pushed successfully
- Release workflow has proper permissions
- GitHub Actions are enabled

### Wrong Version Released

1. Delete the tag: `git tag -d vX.Y.Z && git push origin :refs/tags/vX.Y.Z`
2. Delete the GitHub Release
3. Fix the version
4. Re-run version bump workflow

## References

- [Semantic Versioning](https://semver.org/)
- [Keep a Changelog](https://keepachangelog.com/)
- [Conventional Commits](https://www.conventionalcommits.org/)

