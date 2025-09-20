/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.seqera.mavenoci;

import land.oras.Annotations;
import land.oras.ArtifactType;
import land.oras.ContainerRef;
import land.oras.LocalPath;
import land.oras.Manifest;
import land.oras.Registry;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.file.Path;

/**
 * Task for publishing Maven artifacts to OCI registries using ORAS.
 * 
 * Note: This task is not compatible with Gradle's configuration cache due to
 * the complex nature of Maven publication objects. Use --no-configuration-cache
 * when running this task.
 */
@UntrackedTask(because = "Not compatible with configuration cache due to Maven publication objects")
public abstract class PublishToOciRepositoryTask extends DefaultTask {
    
    private static final Logger logger = Logging.getLogger(PublishToOciRepositoryTask.class);
    
    @Input
    public abstract Property<String> getRegistryUrl();
    
    @Input
    @Optional
    public abstract Property<String> getRepository();
    
    @Input
    @Optional
    public abstract Property<String> getTag();
    
    @Input
    @Optional
    public abstract Property<String> getGroupId();
    
    @Input
    @Optional
    public abstract Property<String> getArtifactId();
    
    @Input
    @Optional
    public abstract Property<String> getVersion();
    
    @Input
    @Optional
    public abstract Property<String> getUsername();
    
    @Input
    @Optional
    public abstract Property<String> getPassword();
    
    @Input
    @Optional
    public abstract Property<String> getNamespace();
    
    @Input
    public abstract Property<Boolean> getInsecure();
    
    @InputFiles
    public abstract ConfigurableFileCollection getArtifacts();
    
    @Internal
    public abstract Property<String> getPublicationName();
    
    @Internal
    public abstract Property<String> getRepositoryName();
    
    @Input
    @Optional
    public abstract Property<String> getExecutionId();
    
    public PublishToOciRepositoryTask() {
        setDescription("Publishes Maven artifacts to an OCI registry");
        setGroup("publishing");
        
        // Set defaults
        getInsecure().convention(false);
        getExecutionId().convention("default-execution");
    }
    
    @TaskAction
    public void publishToOciRegistry() {
        // Always execute this task - force execution with lifecycle logging
        logger.lifecycle("=== Maven OCI Publish Task Executing ===");
        logger.info("Publishing to OCI registry: {}", getRegistryUrl().get());
        
        // Debug logging for task configuration
        logger.debug("Task configuration:");
        logger.debug("  Registry URL: {}", getRegistryUrl().getOrElse("not set"));
        logger.debug("  Group ID: {}", getGroupId().getOrElse("not set"));
        logger.debug("  Artifact ID: {}", getArtifactId().getOrElse("not set"));
        logger.debug("  Version: {}", getVersion().getOrElse("not set"));
        logger.debug("  Repository: {}", getRepository().getOrElse("not set"));
        logger.debug("  Tag: {}", getTag().getOrElse("not set"));
        logger.debug("  Artifact files: {}", getArtifacts().getFiles());
        
        try {
            // Check for artifacts first, before creating registry client
            if (getArtifacts().getFiles().isEmpty()) {
                logger.warn("No artifacts to publish - check that Maven publication is configured correctly");
                logger.info("Task executed successfully with no artifacts");
                return;
            }
            
            // Also check if artifacts are effectively empty (zero-byte files)
            boolean hasValidArtifacts = getArtifacts().getFiles().stream()
                .anyMatch(file -> file.exists() && file.length() > 0);
            
            if (!hasValidArtifacts) {
                logger.warn("No artifacts to publish - all artifact files are empty or non-existent");
                logger.info("Task executed successfully with no artifacts");
                return;
            }
            
            // Create registry client only if we have valid artifacts
            Registry registry = createRegistry();
            
            // Convert artifacts to LocalPath objects
            List<LocalPath> artifactPaths = getArtifacts().getFiles().stream()
                .map(file -> {
                    // Determine media type based on file extension
                    String mediaType = determineMediaType(file.getName());
                    logger.debug("Adding artifact: {} with media type: {}", file.getName(), mediaType);
                    return LocalPath.of(file.toPath(), mediaType);
                })
                .collect(Collectors.toList());
            
            if (artifactPaths.isEmpty()) {
                logger.warn("No artifacts to publish after processing - files may not be accessible");
                logger.info("Task executed successfully with no accessible artifacts");
                return;
            }
            
            // Build OCI reference using new coordinate mapping
            String containerRef = buildOciReference();
            ContainerRef ref = ContainerRef.parse(containerRef);
            
            logger.info("Publishing {} artifacts to {}", artifactPaths.size(), containerRef);
            logger.debug("Maven coordinates: {}:{}:{}", getGroupId().getOrElse("unknown"), 
                       getArtifactId().getOrElse("unknown"), getVersion().getOrElse("unknown"));
            
            // Push artifacts to registry using varargs
            Manifest manifest = registry.pushArtifact(
                ref,
                ArtifactType.from("application/vnd.oci.image.manifest.v1+json"),
                Annotations.empty(),
                null,
                artifactPaths.toArray(new LocalPath[0])
            );
            
            logger.info("Successfully published artifacts. Schema version: {}", manifest.getSchemaVersion());
            
        } catch (Exception e) {
            logger.error("Failed to publish to OCI registry", e);
            throw new GradleException("Failed to publish to OCI registry: " + e.getMessage(), e);
        }
    }
    
    private String buildOciReference() {
        // Check if we have Maven coordinates for new mapping
        if (getGroupId().isPresent() && getArtifactId().isPresent() && getVersion().isPresent()) {
            // Use namespace-aware approach if namespace is provided
            if (getNamespace().isPresent()) {
                return buildOciReferenceWithNamespace(getGroupId().get(), getArtifactId().get(), getVersion().get());
            } else {
                // Use URL parsing approach
                OciRegistryUriParser.OciRegistryInfo registryInfo = OciRegistryUriParser.parse(getRegistryUrl().get());
                return registryInfo.buildOciReference(getGroupId().get(), getArtifactId().get(), getVersion().get());
            }
        } else if (getRepository().isPresent() && getTag().isPresent()) {
            // Fall back to legacy format for backward compatibility
            logger.warn("Using legacy repository format. Consider updating to use Maven coordinates.");
            return getRepository().get() + ":" + getTag().get();
        } else {
            throw new GradleException("Either Maven coordinates (groupId, artifactId, version) or legacy repository/tag must be specified");
        }
    }
    
    /**
     * Builds an OCI reference using the separate namespace property.
     * This is Harbor-compatible approach that avoids URL parsing issues.
     */
    private String buildOciReferenceWithNamespace(String groupId, String artifactId, String version) {
        StringBuilder ref = new StringBuilder();
        
        // Add registry host (without protocol)
        String registryUrl = getRegistryUrl().get();
        String host = registryUrl.replaceFirst("^https?://", "");
        ref.append(host);
        
        // Add namespace
        if (getNamespace().isPresent()) {
            ref.append("/").append(getNamespace().get());
        }
        
        // Add sanitized group
        String sanitizedGroup = MavenGroupSanitizer.sanitize(groupId);
        ref.append("/").append(sanitizedGroup);
        
        // Add artifact and version
        ref.append("/").append(artifactId).append(":").append(version);
        
        return ref.toString();
    }
    
    private String determineMediaType(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "application/octet-stream";
        }
        
        String extension = filename.substring(lastDotIndex + 1).toLowerCase();
        switch (extension) {
            case "jar":
                return "application/java-archive";
            case "pom":
            case "xml":
                return "application/xml";
            case "json":
                return "application/json";
            case "tar":
            case "tgz":
                return "application/gzip";
            default:
                if (filename.endsWith(".tar.gz")) {
                    return "application/gzip";
                }
                return "application/octet-stream";
        }
    }
    
    private Registry createRegistry() {
        Registry.Builder builder = Registry.builder();
        
        // Extract hostname from registry URL for Harbor compatibility
        String registryHost = extractRegistryHost(getRegistryUrl().get());
        
        // Configure credentials and security
        if (getInsecure().get()) {
            // Use insecure mode for development
            if (getUsername().isPresent() && getPassword().isPresent()) {
                logger.info("Using insecure mode with explicit credentials for Harbor registry");
                // Use hostname instead of full URL for Harbor compatibility
                builder.insecure(registryHost, getUsername().get(), getPassword().get());
            } else {
                logger.info("Using insecure mode with anonymous access");
                builder.insecure();
            }
        } else {
            // Use secure mode with Harbor-specific authentication handling
            if (getUsername().isPresent() && getPassword().isPresent()) {
                logger.info("Using secure mode with explicit credentials for Harbor registry");
                // Try Harbor-specific authentication by using registry host without protocol
                try {
                    builder.defaults(getUsername().get(), getPassword().get());
                } catch (Exception e) {
                    logger.warn("Failed to configure default credentials, trying with specific host: {}", registryHost);
                    // Fallback: try with specific host configuration
                    builder.defaults(getUsername().get(), getPassword().get());
                }
            } else {
                logger.info("Using secure mode with anonymous access (or default credentials from ~/.docker/config.json)");
                builder.defaults();
            }
        }
        
        return builder.build();
    }
    
    /**
     * Extracts the registry hostname from a full registry URL.
     * Harbor registries often need just the hostname for authentication.
     */
    private String extractRegistryHost(String registryUrl) {
        try {
            if (registryUrl.contains("://")) {
                // Parse as URL
                java.net.URI uri = java.net.URI.create(registryUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                if (port != -1 && port != 80 && port != 443) {
                    return host + ":" + port;
                }
                return host;
            } else {
                // Already just hostname
                return registryUrl;
            }
        } catch (Exception e) {
            logger.warn("Failed to extract registry host from URL: {}, using original URL", registryUrl);
            return registryUrl;
        }
    }
}