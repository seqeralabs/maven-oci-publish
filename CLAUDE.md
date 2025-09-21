# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Gradle plugin that enables **bidirectional** Maven artifact management with OCI-compliant registries using the ORAS (OCI Registry as Storage) Java SDK. The plugin provides both:

1. **Publishing**: A DSL similar to Gradle's `maven-publish` plugin for publishing Maven artifacts to OCI registries
2. **Dependency Resolution**: Transparent consumption of Maven artifacts from OCI registries using standard Gradle dependency syntax

The plugin seamlessly integrates with Gradle's existing repository and dependency system, allowing OCI registries to be used alongside traditional Maven repositories.

Project inspired to https://github.com/Tosan/oras-maven-plugin.

## Build and Test Commands

```bash
# Build the plugin
./gradlew build

# Run unit tests
./gradlew test

# Run all tests
./gradlew check

# Run specific tests
./gradlew test --tests "MavenOciPublishPluginTest"
./gradlew test --tests "*MavenOciGroupSanitizerTest"
./gradlew test --tests "*MavenOciRepositoryFactoryTest"

# Test examples
cd example/publisher && ./gradlew publishToOciRegistries --dry-run
cd example/consumer && ./gradlew run
```

## Architecture

### Core Components

The plugin follows a standard Gradle plugin architecture with these key components:

#### Publishing Components
- **MavenOciPublishPlugin** - Main plugin class that applies the plugin, creates DSL extension, and generates publishing tasks
- **MavenOciPublishingExtension** - Provides the `oci` DSL block for configuration and repository registration
- **MavenOciPublication** - Domain object representing what to publish (similar to MavenPublication)
- **MavenOciRepository** - Domain object representing where to publish (OCI registry configuration for publishing)
- **PublishToOciRepositoryTask** - Task implementation that performs the actual publishing using ORAS Java SDK

#### Dependency Resolution Components
- **MavenOciRepositoryFactory** - Creates Maven repositories backed by OCI registries with transparent caching
- **MavenOciResolver** - Core resolver that handles OCI artifact downloading using ORAS protocol
- **MavenOciGroupSanitizer** - Utility for mapping Maven group IDs to OCI-compliant repository names
- **MavenOciRepositorySpec** - Domain object for OCI repository specifications used in dependency resolution
- **MavenOciRegistryUriParser** - Utility for parsing and extracting information from OCI registry URLs

### Plugin Flow

#### Publishing Flow
1. Plugin applies and creates direct `oci()` method in `publishing.repositories`
2. Users configure repositories using `publishing { repositories { oci('name') { } } }` syntax
3. After project evaluation, plugin creates publishing tasks for each publication-repository combination
4. Tasks use ORAS Java SDK to push Maven artifacts to OCI registries with proper media types

#### Dependency Resolution Flow
1. Users configure OCI repositories using `repositories { oci("name") { url = "..."; insecure = true } }`
2. Plugin installs hooks into Gradle's dependency resolution system
3. Before dependency resolution, hooks check each dependency against OCI registries
4. Maven coordinates are mapped to OCI references (e.g., `com.example:lib:1.0` → `registry.com/com-example/lib:1.0`)
5. ORAS Java SDK attempts to pull artifacts from OCI registry
6. Downloaded artifacts are cached locally in Maven repository structure
7. Gradle continues normal resolution using cached files

### Key Dependencies

- **ORAS Java SDK** (`land.oras:oras-java-sdk:0.2.15`) - Core OCI registry operations
- **Testcontainers** (`org.testcontainers:testcontainers:1.20.4`) - Container-based integration testing
- **Spock Framework** (`org.spockframework:spock-core:2.2-groovy-3.0`) - Testing framework
- Requires Java 17+ and Gradle 6.0+

## Test Structure

### Unit Tests (`plugin/src/test/groovy/`)
- **MavenOciGroupSanitizerTest** - Tests for Maven group ID sanitization to OCI-compliant names
- **MavenOciRepositoryFactoryTest** - Tests for OCI-backed Maven repository creation
- **MavenOciResolverTest** - Tests for OCI artifact resolution logic
- **MavenOciPublishPluginTest** - Basic plugin registration and extension creation
- Uses Spock framework with Gradle's `ProjectBuilder`

### Integration Tests (`plugin/src/test/groovy/`)
- **MavenOciLifecycleIntegrationTest** - End-to-end publish-consume lifecycle tests with real OCI registry
- **MavenOciLifecycleWithAuthIntegrationTest** - Docker Distribution authentication analysis and Bearer token testing
- **MavenOciPublishPluginContainerIntegrationTest** - Comprehensive container-based integration tests
- **MavenOciPublishPluginIntegrationTest** - Basic plugin integration tests with Gradle TestKit
- Uses Gradle TestKit and Testcontainers for real OCI registry testing

### Example Project (`example/`)
- Demonstrates plugin usage patterns
- Shows integration with standard Maven publishing
- Includes examples for GitHub Container Registry and local registry

## Test Naming Convention

All test classes follow the `ClassNameTest` convention:

- **Unit Tests**: For testing a specific class `Foo`, create test class `FooTest`
  - Example: `MavenOciGroupSanitizer` → `MavenOciGroupSanitizerTest`
  - Example: `MavenOciResolver` → `MavenOciResolverTest`

- **Integration Tests**: For end-to-end or integration tests, use descriptive names ending with `IntegrationTest`
  - Example: `MavenOciLifecycleIntegrationTest` - Tests complete publish-consume lifecycle
  - Example: `MavenOciPublishPluginContainerIntegrationTest` - Tests with containerized registry
  - Example: `MavenOciPublishPluginIntegrationTest` - Basic plugin integration tests

This convention ensures:
- Clear distinction between unit and integration tests
- Easy identification of what functionality each test covers
- Consistent naming across the entire test suite

## Repository Configuration

### For Consuming Artifacts from OCI Registries

To consume artifacts from OCI registries, configure OCI repositories using the named factory method within the `repositories` block. The plugin supports multiple namespace levels embedded directly in the URL:

```gradle
repositories {
    mavenCentral()
    
    // Configure OCI repositories for dependency resolution
    oci("seqeraPublic") {
        url = 'https://seqera.io/oci-registry'
    }
    
    // Multi-level namespace support
    oci("nestedNamespace") {
        url = 'https://registry.com/org/team/maven'
    }
    
    oci("localRegistry") {
        url = 'http://localhost:5000/maven'
        insecure = true
    }
}

dependencies {
    implementation 'com.example:my-library:1.0.0'
}
```

### For Publishing Artifacts to OCI Registries

Publishing uses the standard `publishing` DSL with direct `oci()` method. Supports both URL-embedded namespaces and separate namespace configuration:

```gradle
publishing {
    publications {
        maven(MavenPublication) {
            from components.java
        }
    }
    
    repositories {
        // URL-embedded namespace approach
        oci('seqeraPublic') {
            url = 'https://seqera.io/oci-registry/org/maven'
            credentials {
                username = project.findProperty('oci.username')
                password = project.findProperty('oci.password')
            }
        }
        
        // Separate namespace configuration approach  
        oci('dockerRegistry') {
            url = 'https://registry.com'
            namespace = 'org/team/maven'
            credentials {
                username = project.findProperty('docker.username')
                password = project.findProperty('docker.password')
            }
        }
    }
}
```

## Development Notes

- The plugin auto-applies the `maven-publish` plugin and builds upon it
- Task naming follows Gradle conventions: `publish{Publication}To{Repository}Repository`
- Uses Gradle's Provider API for lazy evaluation and configuration
- Integrates with Gradle's software component system (`components.java`)
- Supports various authentication methods including Docker credentials and environment variables

## Authentication Support

### Docker Distribution Compliance
The plugin follows the Docker Distribution authentication and authorization specification:
- **Bearer Token Authentication**: Uses proper JWT Bearer tokens per Docker Distribution spec
- **Challenge-Response Flow**: Implements standard auth challenge handling
- **ORAS SDK Integration**: Leverages ORAS Java SDK's built-in Bearer token support

### Authentication Analysis Results
Through comprehensive testing, we discovered and resolved authentication compatibility:
- **Root Cause**: Previous Basic authentication (`Basic realm="test"`) was incompatible with Docker Distribution spec
- **Solution**: ORAS Java SDK correctly implements Bearer token authentication (`Bearer realm="<token-server>" service="<service>" scope="<scope>"`)
- **Testing**: Uses Testcontainers with `RegistryAuthLocator` and `AuthConfig` for Bearer token testing

### Key Authentication Components
- **AuthConfig** (`com.github.dockerjava.api.model.AuthConfig`) - Docker authentication configuration
- **RegistryAuthLocator** (`org.testcontainers.utility.RegistryAuthLocator`) - Authentication resolution
- **Bearer Token Support**: Full implementation of Docker Distribution token authentication flow

## Recent Achievements

### Test Coverage - 13/13 Tests Passing (100%)
- ✅ **Complete End-to-End Lifecycle**: Real publish-consume workflow with OCI registries
- ✅ **Authentication Analysis**: Comprehensive Bearer token authentication testing
- ✅ **Container Integration**: Real Docker registry integration with Testcontainers
- ✅ **ORAS SDK Validation**: Confirmed SDK works correctly with proper authentication format

### Major Issues Resolved
- **Authentication Compatibility**: Resolved "Invalid WWW-Authenticate header value" by implementing proper Bearer token flow
- **Real Implementation**: Replaced conceptual/fake tests with actual ORAS SDK integration
- **Dependency Resolution**: Added complete OCI artifact resolution and consumption support
