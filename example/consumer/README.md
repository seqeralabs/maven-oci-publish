# Consumer Example - Maven OCI Publish Plugin

This example demonstrates how to consume a Java library published to Docker Hub using the Maven OCI Publish Plugin.

## 🎯 What This Example Does

- **Consumes**: A shared library (`io.seqera:shared-library:1.0.0`) from Docker Hub
- **Registry**: Docker Hub (registry-1.docker.io)
- **Repository**: `pditommaso/maven`
- **Challenge**: Standard Maven/Gradle don't natively support OCI registries

## ✅ Current Capabilities

This example demonstrates the new direct `oci()` syntax for consuming libraries from OCI registries. The Maven OCI Publish Plugin now supports both **publishing** and **consuming** artifacts from OCI registries using standard Gradle dependency syntax.

## ⚠️ Important: Repository Must Exist

The example references `pditommaso/maven` repository which **must be created first** by publishing artifacts. You have two options:

### Option A: Create the Repository
Run the publisher example first to create the repository:

```bash
cd ../publisher
export DOCKERHUB_USERNAME=pditommaso
export DOCKERHUB_PASSWORD=your-password
../../gradlew publishMavenPublicationToDockerhubRepository
```

This will:
1. Create the `pditommaso/maven` repository on Docker Hub
2. Push the JAR, POM, sources, and javadoc artifacts
3. Make the repository available for consumption examples

### Option B: Use an Existing Repository
You can test consumption patterns with any existing OCI repository by updating the artifact references in the code.

## 🚀 Future Solutions

### Option 1: Maven Repository Proxy
A service that translates Maven repository requests to OCI pulls:

```gradle
repositories {
    maven {
        name = "OciMavenProxy"
        url = uri("http://localhost:8080/maven")
        // Proxy handles OCI resolution behind the scenes
    }
}

dependencies {
    implementation 'io.seqera:shared-library:1.0.0'
}
```

### Option 2: Custom Gradle Plugin
A Gradle plugin that can resolve dependencies from OCI registries:

```gradle
plugins {
    id 'java'
    id 'oci-resolver' // Hypothetical plugin
}

ociRepositories {
    dockerHub {
        url = 'https://registry-1.docker.io'
        credentials {
            username = project.findProperty('dockerHubUsername')
            password = project.findProperty('dockerHubPassword')
        }
    }
}

dependencies {
    implementation oci('io.seqera:shared-library:1.0.0')
}
```

### Option 3: Manual Artifact Extraction
Use ORAS or Docker to manually extract artifacts:

```bash
# Pull the OCI artifact
docker pull pditommaso/maven:1.0.0

# Extract JAR files
docker run --rm -v $(pwd)/libs:/output pditommaso/maven:1.0.0 cp /*.jar /output/

# Add to local Maven repository
mvn install:install-file -Dfile=libs/shared-library-1.0.0.jar -DgroupId=io.seqera -DartifactId=shared-library -Dversion=1.0.0 -Dpackaging=jar
```

## 📦 Running This Example

```bash
# Build the consumer app
./gradlew build

# Run the consumer app
./gradlew run

# Show consumption information
./gradlew showConsumptionInfo

# Run tests
./gradlew test
```

## 🔍 What This Example Shows

1. **Project Structure**: How a consumer project would be organized
2. **Dependency Declaration**: How dependencies would be declared (commented out for now)
3. **Usage Patterns**: How the consumed library would be used
4. **Logging**: Proper logging setup for the consumer application

## 🛠️ Implementation Notes

This example is currently a placeholder that demonstrates:
- Project structure for consuming OCI artifacts
- Expected usage patterns
- Logging and testing setup
- Future integration possibilities

Once proper OCI artifact resolution is implemented (via proxy, plugin, or native support), this example can be updated to actually consume the published library.

## 🎉 Complete Workflow

1. **Publish**: Use `../publisher` to publish the shared library to Docker Hub
2. **Consume**: Use this consumer example to demonstrate consumption patterns
3. **Verify**: Check that the complete publish-consume cycle works

## 📚 References

- [ORAS](https://oras.land/) - OCI Registry as Storage
- [Maven OCI Publish Plugin](../../README.md) - Publisher side
- [Docker Hub](https://hub.docker.com/) - OCI Registry
