#!/bin/bash

# Maven OCI Publish Plugin Example Setup
# This script creates publisher and consumer example projects to demonstrate the maven-oci-publish plugin

echo "Setting up Maven OCI Publish Plugin examples..."

# Get the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Creating examples in: $(pwd)"

# Clean existing examples
rm -rf publisher consumer

# Create publisher and consumer directories
mkdir -p publisher consumer

echo "Creating publisher app..."

# ===========================================
# PUBLISHER APP
# ===========================================

cd publisher

# Create settings.gradle for publisher
cat > settings.gradle << 'EOF'
// Include the plugin project as a composite build
includeBuild('../../')

rootProject.name = 'oci-publisher-example'
EOF

# Create build.gradle for publisher
cat > build.gradle << 'EOF'
plugins {
    id 'java'
    id 'maven-publish'
    id 'io.seqera.maven-oci-publish'
}

group = 'io.seqera'
version = '1.0.0'

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

// Standard Maven publishing configuration
publishing {
    publications {
        maven(MavenPublication) {
            from components.java
            artifactId = 'shared-library'
            
            pom {
                name = 'Shared Library'
                description = 'A shared library published via maven-oci-publish plugin'
                url = 'https://github.com/pditommaso/maven-oci-publish'
                
                licenses {
                    license {
                        name = 'Apache License 2.0'
                        url = 'https://www.apache.org/licenses/LICENSE-2.0'
                    }
                }
                
                developers {
                    developer {
                        id = 'pditommaso'
                        name = 'Paolo Di Tommaso'
                        email = 'paolo.ditommaso@gmail.com'
                    }
                }
            }
        }
    }
}

// OCI publishing configuration for Docker Hub
oci {
    publications {
        maven {
            from components.java
        }
    }
    
    repositories {
        // Docker Hub - real repository with credentials
        dockerhub {
            url = 'https://registry-1.docker.io'
            credentials {
                username = project.findProperty('dockerHubUsername') ?: System.getenv('DOCKERHUB_USERNAME')
                password = project.findProperty('dockerHubPassword') ?: System.getenv('DOCKERHUB_PASSWORD')
            }
        }
    }
}

// Custom task to show publishing information
tasks.register('showPublishInfo') {
    doLast {
        println "\n🚀 OCI Publishing Configuration:"
        println "   Registry: https://registry-1.docker.io"
        println "   Repository: pditommaso/maven"
        println "   Tag: ${project.version}"
        println "   Username: ${project.findProperty('dockerHubUsername') ?: System.getenv('DOCKERHUB_USERNAME') ?: 'NOT_SET'}"
        println "   Password: ${(project.findProperty('dockerHubPassword') ?: System.getenv('DOCKERHUB_PASSWORD')) ? 'SET' : 'NOT_SET'}"
        
        println "\n📋 Available publishing tasks:"
        println "   ./gradlew publishMavenPublicationToDockerhubRepository"
        println "   ./gradlew publishToOciRegistries"
        
        println "\n🔧 To set credentials:"
        println "   export DOCKERHUB_USERNAME=your-username"
        println "   export DOCKERHUB_PASSWORD=your-password"
        println "   # OR"
        println "   ./gradlew publishMavenPublicationToDockerhubRepository -PdockerHubUsername=your-username -PdockerHubPassword=your-password"
        
        println "\n📦 After publishing, check:"
        println "   https://hub.docker.com/r/pditommaso/maven"
    }
}

dependencies {
    implementation 'org.slf4j:slf4j-api:2.0.7'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.9.2'
}

test {
    useJUnitPlatform()
}
EOF

# Create gradle.properties for publisher
cat > gradle.properties << 'EOF'
# Docker Hub credentials
# Set these values or use environment variables DOCKERHUB_USERNAME and DOCKERHUB_PASSWORD
#dockerHubUsername=pditommaso
#dockerHubPassword=your-docker-hub-password

# Gradle configuration
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
EOF

# Create source directory structure for publisher
mkdir -p src/main/java/io/seqera/shared
mkdir -p src/test/java/io/seqera/shared

# Create a simple Java library
cat > src/main/java/io/seqera/shared/SharedLibrary.java << 'EOF'
package io.seqera.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A shared library to demonstrate OCI publishing and consumption.
 */
public class SharedLibrary {
    
    private static final Logger logger = LoggerFactory.getLogger(SharedLibrary.class);
    
    /**
     * Returns the version of this library.
     * @return the version string
     */
    public static String getVersion() {
        return "1.0.0";
    }
    
    /**
     * Returns a greeting message.
     * @param name the name to greet
     * @return a greeting message
     */
    public static String greet(String name) {
        String message = "Hello, " + name + "! This library was published via Maven OCI Publish Plugin.";
        logger.info("Generated greeting: {}", message);
        return message;
    }
    
    /**
     * Performs a simple calculation.
     * @param a first number
     * @param b second number
     * @return the sum of a and b
     */
    public static int add(int a, int b) {
        int result = a + b;
        logger.debug("Adding {} + {} = {}", a, b, result);
        return result;
    }
    
    /**
     * Returns information about the publishing mechanism.
     * @return publishing info
     */
    public static String getPublishingInfo() {
        return "Published to Docker Hub (registry-1.docker.io) as OCI artifact";
    }
}
EOF

# Create a test for the shared library
cat > src/test/java/io/seqera/shared/SharedLibraryTest.java << 'EOF'
package io.seqera.shared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SharedLibrary.
 */
public class SharedLibraryTest {
    
    @Test
    public void testGetVersion() {
        String version = SharedLibrary.getVersion();
        assertNotNull(version);
        assertEquals("1.0.0", version);
    }
    
    @Test
    public void testGreet() {
        String greeting = SharedLibrary.greet("World");
        assertNotNull(greeting);
        assertTrue(greeting.contains("Hello, World!"));
        assertTrue(greeting.contains("Maven OCI Publish Plugin"));
    }
    
    @Test
    public void testAdd() {
        assertEquals(5, SharedLibrary.add(2, 3));
        assertEquals(0, SharedLibrary.add(-1, 1));
        assertEquals(-5, SharedLibrary.add(-3, -2));
    }
    
    @Test
    public void testGetPublishingInfo() {
        String info = SharedLibrary.getPublishingInfo();
        assertNotNull(info);
        assertTrue(info.contains("Docker Hub"));
        assertTrue(info.contains("OCI artifact"));
    }
}
EOF

# Create README for publisher
cat > README.md << 'EOF'
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
EOF

echo "✅ Publisher app created successfully!"

# ===========================================
# CONSUMER APP  
# ===========================================

cd ../consumer

echo "Creating consumer app..."

# Create settings.gradle for consumer
cat > settings.gradle << 'EOF'
rootProject.name = 'oci-consumer-example'
EOF

# Create build.gradle for consumer
cat > build.gradle << 'EOF'
plugins {
    id 'java'
    id 'application'
}

group = 'io.seqera'
version = '1.0.0'

repositories {
    mavenCentral()
    
    // TODO: This is a placeholder for OCI registry consumption
    // In a real scenario, you would need a Maven repository proxy
    // that can resolve artifacts from OCI registries
    // maven {
    //     name = "OciMavenRegistry"
    //     url = uri("oci+https://registry-1.docker.io/maven")
    //     credentials {
    //         username = project.findProperty('dockerHubUsername') ?: System.getenv('DOCKERHUB_USERNAME')
    //         password = project.findProperty('dockerHubPassword') ?: System.getenv('DOCKERHUB_PASSWORD')
    //     }
    // }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'io.seqera.consumer.ConsumerApp'
}

dependencies {
    // TODO: In a real scenario, this would resolve from the OCI registry
    // For now, we'll use a local file dependency or comment it out
    // implementation 'io.seqera:shared-library:1.0.0'
    
    implementation 'org.slf4j:slf4j-api:2.0.7'
    implementation 'ch.qos.logback:logback-classic:1.4.7'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.9.2'
}

test {
    useJUnitPlatform()
}

// Custom task to show how to consume OCI artifacts
tasks.register('showConsumptionInfo') {
    doLast {
        println "\n📦 OCI Artifact Consumption:"
        println "   Registry: https://registry-1.docker.io"
        println "   Artifact: pditommaso/maven:1.0.0"
        
        println "\n🔧 To consume OCI artifacts, you need:"
        println "   1. A Maven repository proxy that can resolve OCI artifacts"
        println "   2. Or use ORAS/Docker to pull and extract artifacts manually"
        
        println "\n📋 Manual artifact extraction:"
        println "   # Pull the OCI artifact"
        println "   docker pull pditommaso/maven:1.0.0"
        println "   # Extract the JAR files"
        println "   docker run --rm -v \$(pwd):/output pditommaso/maven:1.0.0 cp /*.jar /output/"
        
        println "\n🔮 Future: Maven repository proxy for OCI"
        println "   Maven repository that translates requests to OCI pulls"
        println "   This would enable seamless consumption via standard Gradle/Maven"
    }
}

// Task to download artifacts manually using ORAS
tasks.register('downloadOciArtifacts') {
    doLast {
        println "📥 Downloading OCI artifacts manually..."
        println "This would use ORAS or Docker to pull and extract artifacts"
        println "Implementation depends on your specific OCI registry setup"
    }
}
EOF

# Create gradle.properties for consumer
cat > gradle.properties << 'EOF'
# Docker Hub credentials (for private registries)
# Set these values or use environment variables DOCKERHUB_USERNAME and DOCKERHUB_PASSWORD
#dockerHubUsername=pditommaso
#dockerHubPassword=your-docker-hub-password

# Gradle configuration
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
EOF

# Create source directory structure for consumer
mkdir -p src/main/java/io/seqera/consumer
mkdir -p src/test/java/io/seqera/consumer

# Create consumer application
cat > src/main/java/io/seqera/consumer/ConsumerApp.java << 'EOF'
package io.seqera.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example application that consumes the shared library published via OCI.
 */
public class ConsumerApp {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsumerApp.class);
    
    public static void main(String[] args) {
        logger.info("Starting OCI Consumer Example App");
        
        // TODO: Once OCI artifact consumption is implemented, uncomment this:
        // String greeting = SharedLibrary.greet("OCI Consumer");
        // logger.info("Received greeting: {}", greeting);
        // 
        // int result = SharedLibrary.add(10, 20);
        // logger.info("Calculation result: {}", result);
        // 
        // String info = SharedLibrary.getPublishingInfo();
        // logger.info("Publishing info: {}", info);
        
        logger.info("This example demonstrates the consumer side of OCI artifact consumption");
        logger.info("To actually consume the shared library, you would need:");
        logger.info("1. A Maven repository proxy that can resolve OCI artifacts");
        logger.info("2. Or manual artifact extraction from the OCI registry");
        
        logger.info("For now, this serves as a placeholder for the complete workflow");
    }
    
    /**
     * Example method showing how the shared library would be used.
     */
    public void demonstrateUsage() {
        logger.info("Example usage of shared library:");
        logger.info("- SharedLibrary.greet(\"World\") -> greeting message");
        logger.info("- SharedLibrary.add(5, 3) -> 8");
        logger.info("- SharedLibrary.getVersion() -> \"1.0.0\"");
    }
}
EOF

# Create test for consumer
cat > src/test/java/io/seqera/consumer/ConsumerAppTest.java << 'EOF'
package io.seqera.consumer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConsumerApp.
 */
public class ConsumerAppTest {
    
    @Test
    public void testConsumerAppCreation() {
        ConsumerApp app = new ConsumerApp();
        assertNotNull(app);
    }
    
    @Test
    public void testDemonstrateUsage() {
        ConsumerApp app = new ConsumerApp();
        // This should not throw an exception
        assertDoesNotThrow(() -> app.demonstrateUsage());
    }
}
EOF

# Create README for consumer
cat > README.md << 'EOF'
# Consumer Example - Maven OCI Publish Plugin

This example demonstrates how to consume a Java library published to Docker Hub using the Maven OCI Publish Plugin.

## 🎯 What This Example Does

- **Consumes**: A shared library (`io.seqera:shared-library:1.0.0`) from Docker Hub
- **Registry**: Docker Hub (registry-1.docker.io)
- **Repository**: `pditommaso/maven`
- **Challenge**: Standard Maven/Gradle don't natively support OCI registries

## 🔧 Current Limitations

Currently, standard Maven and Gradle repositories don't natively support OCI registries. This example shows the structure and demonstrates what consumption would look like once proper tooling is available.

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
EOF

echo "✅ Consumer app created successfully!"

# ===========================================
# MAIN README
# ===========================================

cd ..

# Create main README for examples
cat > README.md << 'EOF'
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

### 2. Publish Library

```bash
cd publisher

# Set your Docker Hub credentials
export DOCKERHUB_USERNAME=your-username
export DOCKERHUB_PASSWORD=your-password

# Build and publish
./gradlew build publishMavenPublicationToDockerhubRepository
```

### 3. Consume Library

```bash
cd consumer

# Currently a placeholder showing future consumption patterns
./gradlew run
```

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

## 🛠️ Troubleshooting

### Common Issues

1. **Authentication Errors**
   - Check Docker Hub credentials
   - Verify repository permissions

2. **Build Failures**
   - Ensure Java 17+ is installed
   - Check internet connectivity

3. **Publishing Errors**
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
EOF

echo ""
echo "✅ Maven OCI Publish Plugin examples created successfully!"
echo ""
echo "📁 Structure:"
echo "   $(pwd)/publisher/    - Library publisher example"
echo "   $(pwd)/consumer/     - Library consumer example"
echo ""
echo "🚀 Next steps:"
echo ""
echo "🧪 Testing without publishing:"
echo "   cd publisher && ../../gradlew build test"
echo "   cd consumer && ../../gradlew build run"
echo ""
echo "📦 Full workflow (requires Docker Hub credentials):"
echo "1. Set Docker Hub credentials:"
echo "   export DOCKERHUB_USERNAME=your-username"
echo "   export DOCKERHUB_PASSWORD=your-password"
echo ""
echo "2. Publish the library (creates repository):"
echo "   cd publisher && ../../gradlew build publishMavenPublicationToDockerhubRepository"
echo ""
echo "3. Test consumption patterns:"
echo "   cd consumer && ../../gradlew run showConsumptionInfo"
echo ""
echo "📚 See README.md files in each directory for detailed instructions"