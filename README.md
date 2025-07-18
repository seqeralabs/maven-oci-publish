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
mavenOci {
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
mavenOci {
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

### Using Default Docker Credentials

If you're already logged into a Docker registry, the plugin can use those credentials:

```gradle
mavenOci {
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
mavenOci {
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

### Publishing to Multiple Registries

```gradle
mavenOci {
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

1. **Username/Password**: Explicitly configured credentials
2. **Default Docker Credentials**: Uses `~/.docker/config.json`
3. **Environment Variables**: Can be configured via environment variables

### Using Environment Variables

```gradle
mavenOci {
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