# Publisher Example - Maven OCI Publish Plugin

This example demonstrates how to publish a Java library to Docker Hub using the Maven OCI Publish Plugin.

## 🎯 What This Example Does

- **Publishes**: A shared library (`io.seqera:shared-library:1.0.0`) to Docker Hub
- **Registry**: Docker Hub (registry-1.docker.io)
- **Repository**: `pditommaso/maven`
- **Artifacts**: JAR, sources JAR, Javadoc JAR, and POM

## 🔧 Setup

### 1. Set Docker Hub Credentials

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

### 2. Docker Hub Setup

Create a Docker Hub account and optionally create a repository named `shared-library`.

## 🚀 Publishing Commands

```bash
# 1. Build the library
./gradlew build

# 2. Show publishing configuration
./gradlew showPublishInfo

# 3. Run tests
./gradlew test

# 4. Publish to Docker Hub
./gradlew publishMavenPublicationToDockerhubRepository

# 5. Publish to all configured repositories
./gradlew publishToOciRegistries
```

## 📦 Verification

After publishing, you can verify the artifacts:

### 1. Check Docker Hub
Visit: https://hub.docker.com/r/pditommaso/maven

### 2. Use Docker/ORAS to pull
```bash
# Pull with Docker
docker pull pditommaso/maven:1.0.0

# Or with ORAS
oras pull pditommaso/maven:1.0.0
```

### 3. Use in another project
See the `../consumer` example for how to consume this library.

## 🔍 What Gets Published

The plugin publishes:
- `shared-library-1.0.0.jar` (compiled classes)
- `shared-library-1.0.0-sources.jar` (source code)
- `shared-library-1.0.0-javadoc.jar` (javadoc)
- `shared-library-1.0.0.pom` (Maven metadata)

All artifacts are stored as OCI artifacts in Docker Hub.

## 🔧 Configuration Syntax

This example uses the new `mavenOci` syntax for configuring repositories:

```gradle
publishing {
    repositories {
        // OCI registry using the new syntax
        mavenOci {
            name = 'seqeraPublic'
            url = 'https://public.cr.stage-seqera.io'
            namespace = 'maven'
            credentials {
                username = getRequiredProperty('ociRegistryUsername')
                password = getRequiredProperty('ociRegistryPassword')
            }
        }
    }
}
```

This provides:
- ✅ **Standard Gradle syntax** - no custom DSL required
- ✅ **Seamless integration** - works alongside traditional `maven {}` repositories
- ✅ **Full feature support** - credentials, namespaces, insecure connections

## 🛠️ Troubleshooting

1. **Authentication Issues**
   - Ensure your Docker Hub credentials are correct
   - Check that username/password are properly set

2. **Permission Denied**
   - Make sure you have push access to the repository
   - Verify the repository namespace matches your username

3. **Connection Issues**
   - Check your internet connection
   - Verify Docker Hub is accessible

## 🎉 Success Indicators

Look for these in the build output:
- `Using secure mode with explicit credentials`
- `Publishing to OCI registry: https://registry-1.docker.io`
- `Successfully published artifacts`
- `BUILD SUCCESSFUL`
