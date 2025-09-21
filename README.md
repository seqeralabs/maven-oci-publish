# Maven OCI Publish Plugin

A Gradle plugin that enables publishing Maven artifacts to OCI-compliant registries (like Docker registries) using the [ORAS (OCI Registry as Storage)](https://oras.land/) Java SDK.

## Features

- 📦 Publish Maven artifacts (JARs, POMs, etc.) to OCI registries
- 🔐 Support for authentication (username/password, default Docker credentials)
- 🛡️ Secure and insecure registry support
- 🏗️ Integration with Gradle's Software Components API
- 📋 Automatic task generation for publication-repository combinations
- 🔧 Follows Gradle maven-publish plugin conventions

## Requirements

- Gradle 6.0 or later
- Java 17 or later (required by ORAS Java SDK)

## Installation

Add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'java'
    id 'maven-publish'
    id 'io.seqera.maven-oci-publish' version 'x.x.x'
}
```

## Usage

### Basic Configuration

```gradle
// Configure standard Maven publication
publishing {
    publications {
        maven(MavenPublication) {
            from components.java
        }
    }
}

// Configure OCI publishing
oci {
    publications {
        maven(OciPublication) {
            from components.java
            repository = 'myregistry.io/maven/my-group/my-artifact'
            tag = '1.0.0'
        }
    }
    
    repositories {
        myRegistry(OciRepository) {
            url = 'https://myregistry.io'
            credentials {
                username = project.findProperty('registryUsername')
                password = project.findProperty('registryPassword')
            }
        }
    }
}
```

### Advanced Configuration

```gradle
oci {
    publications {
        // Publication with custom artifacts
        customMaven(OciPublication) {
            from components.java
            repository = 'ghcr.io/myorg/myapp'
            tag = project.version
            
            // Configure Maven POM
            pom {
                name = 'My Application'
                description = 'A sample application'
                url = 'https://github.com/myorg/myapp'
                
                licenses {
                    license {
                        name = 'MIT License'
                        url = 'https://opensource.org/licenses/MIT'
                    }
                }
            }
        }
    }
    
    repositories {
        // GitHub Container Registry
        github(OciRepository) {
            url = 'https://ghcr.io'
            credentials {
                username = System.getenv('GITHUB_ACTOR')
                password = System.getenv('GITHUB_TOKEN')
            }
        }
        
        // Private registry with insecure connection (for development)
        local(OciRepository) {
            url = 'http://localhost:5000'
            insecure = true
        }
    }
}
```

### Anonymous Access

For public registries that don't require authentication, simply omit the credentials configuration:

```gradle
oci {
    publications {
        maven(OciPublication) {
            from components.java
            repository = 'public-registry.io/maven/my-artifact'
        }
    }
    
    repositories {
        publicRegistry(OciRepository) {
            url = 'https://public-registry.io'
            // No credentials needed - will use anonymous access
        }
    }
}
```

### Using Default Docker Credentials

If you're already logged into a Docker registry, the plugin can use those credentials:

```gradle
oci {
    publications {
        maven(OciPublication) {
            from components.java
            repository = 'myregistry.io/maven/my-artifact'
        }
    }
    
    repositories {
        myRegistry(OciRepository) {
            url = 'https://myregistry.io'
            // No credentials needed - will use ~/.docker/config.json
        }
    }
}
```

## Tasks

The plugin automatically generates tasks following the pattern:
`publish{PublicationName}PublicationTo{RepositoryName}Repository`

Common tasks:
- `publishMavenPublicationToMyRegistryRepository` - Publish specific publication to specific repository
- `publishToOciRegistries` - Publish all publications to all repositories

## Examples

### Publishing to GitHub Container Registry

```gradle
oci {
    publications {
        maven(OciPublication) {
            from components.java
            repository = 'ghcr.io/myorg/myapp'
            tag = project.version
        }
    }
    
    repositories {
        github(OciRepository) {
            url = 'https://ghcr.io'
            credentials {
                username = System.getenv('GITHUB_ACTOR')
                password = System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

### Publishing to Public Registry (Anonymous)

```gradle
oci {
    publications {
        maven(OciPublication) {
            from components.java
            repository = 'public-registry.io/maven/myorg/myapp'
            tag = project.version
        }
    }
    
    repositories {
        publicRegistry(OciRepository) {
            url = 'https://public-registry.io'
            // No credentials - uses anonymous access
        }
    }
}
```

### Publishing to Multiple Registries

```gradle
oci {
    publications {
        maven(OciPublication) {
            from components.java
            repository = 'myorg/myapp'
            tag = project.version
        }
    }
    
    repositories {
        dockerHub(OciRepository) {
            url = 'https://registry-1.docker.io'
            credentials {
                username = project.findProperty('dockerHubUsername')
                password = project.findProperty('dockerHubPassword')
            }
        }
        
        github(OciRepository) {
            url = 'https://ghcr.io'
            credentials {
                username = System.getenv('GITHUB_ACTOR')
                password = System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

## Authentication

The plugin supports several authentication methods:

1. **Anonymous Access**: No credentials required for public registries
2. **Username/Password**: Explicitly configured credentials
3. **Default Docker Credentials**: Uses `~/.docker/config.json`
4. **Environment Variables**: Can be configured via environment variables

### Using Environment Variables

```gradle
oci {
    repositories {
        myRegistry(OciRepository) {
            url = 'https://myregistry.io'
            credentials {
                username = System.getenv('REGISTRY_USERNAME')
                password = System.getenv('REGISTRY_PASSWORD')
            }
        }
    }
}
```

## Accessing Published Libraries

Once you've published your Maven artifacts to an OCI registry, you can access them from other projects using the plugin's built-in consumption support.

### Using OCI Repository Configuration

The plugin provides seamless consumption of OCI-published libraries by configuring OCI repositories in the standard `repositories` block:

```gradle
// In your consuming project's build.gradle
plugins {
    id 'java'
    id 'io.seqera.maven-oci-publish' version 'x.x.x'
}

repositories {
    mavenCentral()
    
    // Configure OCI repositories for dependency resolution
    oci("myRegistry") {
        url = 'https://myregistry.io'
    }
    
    oci("localRegistry") {
        url = 'http://localhost:5000'
        insecure = true  // Allow HTTP connections
    }
}

dependencies {
    implementation 'com.example:my-library:1.0.0'
}
```

### With Authentication

For private registries, the plugin automatically uses Docker credentials from `~/.docker/config.json` if available, or you can configure authentication through environment variables or system properties that the underlying ORAS SDK can access.

```gradle
repositories {
    mavenCentral()
    
    // Configure authenticated OCI repository (uses Docker credentials if available)
    oci("privateRegistry") {
        url = 'https://private.registry.io'
    }
}
```

The plugin leverages the ORAS Java SDK's built-in authentication mechanisms, which support:
- Docker configuration files (`~/.docker/config.json`)
- Environment variables (`DOCKER_USERNAME`, `DOCKER_PASSWORD`)
- Registry-specific authentication flows

### Custom Repository Implementation

You can create a custom Gradle plugin that implements a repository resolver for OCI registries:

```gradle
// Custom OCI repository resolver
class OciRepositoryResolver implements RepositoryTransport {
    
    @Override
    void get(URI location, File destination) throws IOException {
        // Convert Maven coordinates to OCI reference
        String ociRef = convertMavenToOciRef(location)
        
        // Use ORAS Java SDK to pull artifact
        Registry registry = Registry.builder().defaults().build()
        ContainerRef ref = ContainerRef.parse(ociRef)
        registry.pullArtifact(ref, destination.getParent())
    }
    
    private String convertMavenToOciRef(URI location) {
        // Convert: /com/example/my-library/1.0.0/my-library-1.0.0.jar
        // To: myregistry.io/maven/com.example/my-library:1.0.0
        // Implementation details...
    }
}
```

### Integration with Existing Build Systems

#### Gradle Project

```gradle
plugins {
    id 'java'
    id 'maven-publish'
}

repositories {
    mavenCentral()
    
    // OCI registry as Maven repository
    maven {
        name = "CompanyOciRegistry"
        url = uri("oci+https://registry.company.com/maven")
        credentials {
            username = System.getenv('REGISTRY_USERNAME')
            password = System.getenv('REGISTRY_PASSWORD')
        }
    }
}

dependencies {
    implementation 'com.company:shared-library:2.1.0'
    implementation 'com.company:common-utils:1.5.0'
    testImplementation 'junit:junit:4.13.2'
}
```

#### Maven Project

```xml
<project>
    <repositories>
        <repository>
            <id>company-oci-registry</id>
            <name>Company OCI Registry</name>
            <url>oci+https://registry.company.com/maven</url>
        </repository>
    </repositories>
    
    <dependencies>
        <dependency>
            <groupId>com.company</groupId>
            <artifactId>shared-library</artifactId>
            <version>2.1.0</version>
        </dependency>
    </dependencies>
</project>
```

### Repository Proxy Service

For organizations, you can set up a proxy service that translates Maven repository requests to OCI pulls:

```yaml
# docker-compose.yml for OCI-to-Maven proxy
version: '3.8'
services:
  oci-maven-proxy:
    image: company/oci-maven-proxy:latest
    ports:
      - "8080:8080"
    environment:
      - REGISTRY_URL=https://registry.company.com
      - REGISTRY_USERNAME=${REGISTRY_USERNAME}
      - REGISTRY_PASSWORD=${REGISTRY_PASSWORD}
    volumes:
      - ./cache:/app/cache
```

Then use it as a regular Maven repository:

```gradle
repositories {
    maven {
        name = "OciMavenProxy"
        url = uri("http://localhost:8080/maven")
    }
}
```

### Automated Resolution

You can also implement automatic resolution that checks multiple sources:

```gradle
repositories {
    mavenCentral()
    
    // Fallback to OCI registry for internal dependencies
    maven {
        name = "InternalOciRegistry"
        url = uri("oci+https://internal-registry.company.com/maven")
        credentials {
            username = System.getenv('INTERNAL_REGISTRY_USERNAME')
            password = System.getenv('INTERNAL_REGISTRY_PASSWORD')
        }
        content {
            // Only look for company artifacts in OCI registry
            includeGroup "com.company"
            includeGroup "com.company.internal"
        }
    }
}
```

### Complete Lifecycle Example

Here's a complete example showing how to publish a library and consume it:

**1. Publishing Project (`publisher/build.gradle`)**
```gradle
plugins {
    id 'java'
    id 'maven-publish'
    id 'io.seqera.maven-oci-publish' version 'x.x.x'
}

group = 'com.example'
version = '1.0.0'

publishing {
    publications {
        maven(MavenPublication) {
            from components.java
        }
    }
}

mavenOci {
    publications {
        maven {
            from components.java
            repository = 'maven/com.example/my-library'
            tag = project.version
        }
    }
    
    repositories {
        myRegistry {
            url = 'https://registry.example.com'
            credentials {
                username = project.findProperty('registryUsername')
                password = project.findProperty('registryPassword')
            }
        }
    }
}
```

**2. Publish the library:**
```bash
./gradlew publishMavenPublicationToMyRegistryRepository
```

**3. Consuming Project (`consumer/build.gradle`)**
```gradle
plugins {
    id 'java'
}

repositories {
    mavenCentral()
    
    // Custom OCI repository resolver (implementation depends on your setup)
    maven {
        name = "OciMavenRegistry"
        url = uri("oci+https://registry.example.com/maven")
        credentials {
            username = project.findProperty('registryUsername')
            password = project.findProperty('registryPassword')
        }
    }
}

dependencies {
    implementation 'com.example:my-library:1.0.0'
}
```

**4. Use the library:**
```java
import com.example.MyLibrary;

public class MyApp {
    public static void main(String[] args) {
        MyLibrary.doSomething(); // Use the OCI-published library
    }
}
```

This approach allows you to treat OCI registries as Maven repositories, making them seamlessly integrated into your build process.

## Supported Registries

The plugin supports any OCI-compliant registry, including:

- GitHub Container Registry (ghcr.io)
- Docker Hub (registry-1.docker.io)
- Azure Container Registry (ACR)
- AWS Elastic Container Registry (ECR)
- Google Container Registry (GCR)
- Harbor
- Quay.io
- Private registries

## Integration with CI/CD

### GitHub Actions

```yaml
- name: Publish to GitHub Container Registry
  run: ./gradlew publishMavenPublicationToGithubRepository
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### GitLab CI

```yaml
publish:
  script:
    - ./gradlew publishToOciRegistries
  variables:
    REGISTRY_USERNAME: $CI_REGISTRY_USER
    REGISTRY_PASSWORD: $CI_REGISTRY_PASSWORD
```

## Troubleshooting

### Common Issues

1. **Java Version**: Ensure you're using Java 17 or later
2. **Authentication**: Verify your credentials are correct
3. **Registry URL**: Ensure the registry URL is correct and accessible
4. **Insecure Registries**: Use `insecure = true` for HTTP registries

### Debug Mode

Enable debug logging to see more details:

```gradle
logging.level = LogLevel.DEBUG
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Related Projects

- [ORAS](https://oras.land/) - OCI Registry as Storage
- [ORAS Java SDK](https://github.com/oras-project/oras-java) - Java SDK for ORAS
- [Maven ORAS Plugin](https://github.com/Tosan/oras-maven-plugin) - Similar plugin for Maven

## Support

For questions, issues, or contributions, please visit the [GitHub repository](https://github.com/pditommaso/maven-oci-publish).