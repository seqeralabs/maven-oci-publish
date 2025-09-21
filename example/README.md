# Maven OCI Publish Plugin Examples

This directory contains complete examples demonstrating how to use the Maven OCI Publish Plugin to publish and consume Java libraries via OCI registries.

## 📁 Structure

```
example/
├── publisher/          # Library publisher example
│   ├── build.gradle    # Publishing configuration
│   └── src/           # Shared library source code
└── consumer/          # Library consumer example
    ├── build.gradle    # Consumption configuration (placeholder)
    └── src/           # Consumer application
```

## 🚀 Quick Start

### 1. Setup Examples

```bash
# Run the setup script to create both examples
./test-setup.sh
```

### 2. Publish Library (Creates Repository)

```bash
cd publisher

# Set your Docker Hub credentials  
export DOCKERHUB_USERNAME=your-username
export DOCKERHUB_PASSWORD=your-password

# Build and publish (this creates the repository)
../../gradlew build publishMavenPublicationToDockerhubRepository
```

**Note**: This step creates the `pditommaso/maven` repository on Docker Hub and pushes all artifacts.

### 3. Consume Library (Requires Repository)

```bash
cd consumer

# Run the consumer app (shows consumption patterns)
../../gradlew run

# Show consumption information
../../gradlew showConsumptionInfo
```

**Note**: The consumer examples show future consumption patterns. The repository must exist (created in step 2) for manual artifact extraction to work.

## 📦 What Gets Demonstrated

### Publisher Example
- ✅ Complete Java library with tests
- ✅ Maven publishing configuration
- ✅ OCI publishing to Docker Hub
- ✅ Multiple artifact types (JAR, sources, javadoc)
- ✅ Proper POM metadata

### Consumer Example
- 🚧 Project structure for consuming OCI artifacts
- 🚧 Expected usage patterns (placeholder)
- 🚧 Future integration possibilities
- 🚧 Manual artifact extraction methods

## 🔧 Configuration

### Docker Hub Credentials

**Option A: Environment Variables**
```bash
export DOCKERHUB_USERNAME=your-username
export DOCKERHUB_PASSWORD=your-password
```

**Option B: Gradle Properties**
```bash
# Add to gradle.properties
dockerHubUsername=your-username
dockerHubPassword=your-password
```

**Option C: Command Line**
```bash
./gradlew publishMavenPublicationToDockerhubRepository \
  -PdockerHubUsername=your-username \
  -PdockerHubPassword=your-password
```

## 🎯 Registry Configuration

By default, these examples use Docker Hub:
- **Registry**: https://registry-1.docker.io
- **Repository**: pditommaso/maven
- **Artifacts**: JAR, sources, javadoc, POM

To use a different registry, update the `oci` configuration in `publisher/build.gradle`.

## 🔍 Verification

After publishing, verify your artifacts:

1. **Docker Hub**: Visit https://hub.docker.com/r/pditommaso/maven
2. **Docker Pull**: `docker pull pditommaso/maven:1.0.0`
3. **ORAS Pull**: `oras pull pditommaso/maven:1.0.0`

## 🧪 Testing Without Publishing

If you don't have Docker Hub credentials or don't want to create a repository, you can still test the examples:

### Publisher Testing
```bash
cd publisher
../../gradlew build test  # Build and test the library
../../gradlew showPublishInfo  # Show configuration (won't publish)
```

### Consumer Testing  
```bash
cd consumer
../../gradlew build test  # Build and test the consumer
../../gradlew run  # Run the consumer app
../../gradlew showConsumptionInfo  # Show consumption info
```

## 🛠️ Troubleshooting

### Common Issues

1. **Repository Not Found**
   - Run the publisher first to create the repository
   - Or use an existing repository for testing

2. **Authentication Errors**
   - Check Docker Hub credentials
   - Verify repository permissions

3. **Build Failures**
   - Ensure Java 17+ is installed
   - Check internet connectivity

4. **Publishing Errors**
   - Verify registry URL is correct
   - Check repository namespace permissions

### Success Indicators

Look for these in the build output:
- `Using secure mode with explicit credentials`
- `Publishing to OCI registry: https://registry-1.docker.io`
- `Successfully published artifacts`
- `BUILD SUCCESSFUL`

## 📚 Further Reading

- [Main Plugin README](../README.md)
- [Publisher Example](publisher/README.md)
- [Consumer Example](consumer/README.md)
- [ORAS Documentation](https://oras.land/)
