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

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.file.Path;

/**
 * Task for publishing Maven artifacts to OCI registries using ORAS.
 */
public abstract class PublishToOciRepositoryTask extends DefaultTask {
    
    private static final Logger logger = Logging.getLogger(PublishToOciRepositoryTask.class);
    
    @Input
    public abstract Property<String> getRegistryUrl();
    
    @Input
    public abstract Property<String> getRepository();
    
    @Input
    public abstract Property<String> getTag();
    
    @Input
    @Optional
    public abstract Property<String> getUsername();
    
    @Input
    @Optional
    public abstract Property<String> getPassword();
    
    @Input
    public abstract Property<Boolean> getInsecure();
    
    @InputFiles
    public abstract ConfigurableFileCollection getArtifacts();
    
    @Internal
    public abstract Property<OciPublication> getPublication();
    
    @Internal
    public abstract Property<OciRepository> getOciRepository();
    
    public PublishToOciRepositoryTask() {
        setDescription("Publishes Maven artifacts to an OCI registry");
        setGroup("publishing");
        
        // Set defaults
        getInsecure().convention(false);
    }
    
    @TaskAction
    public void publishToOciRegistry() {
        logger.info("Publishing to OCI registry: {}", getRegistryUrl().get());
        
        try {
            // Create registry client
            Registry registry = createRegistry();
            
            // Convert artifacts to LocalPath objects
            List<LocalPath> artifactPaths = getArtifacts().getFiles().stream()
                .map(file -> {
                    // Determine media type based on file extension
                    String mediaType = determineMediaType(file.getName());
                    return LocalPath.of(file.toPath(), mediaType);
                })
                .collect(Collectors.toList());
            
            if (artifactPaths.isEmpty()) {
                logger.warn("No artifacts to publish");
                return;
            }
            
            // Create container reference
            String containerRef = getRepository().get() + ":" + getTag().get();
            ContainerRef ref = ContainerRef.parse(containerRef);
            
            logger.info("Publishing {} artifacts to {}", artifactPaths.size(), containerRef);
            
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
        
        // Configure credentials and security
        if (getInsecure().get()) {
            // Use insecure mode for development
            if (getUsername().isPresent() && getPassword().isPresent()) {
                builder.insecure(getRegistryUrl().get(), getUsername().get(), getPassword().get());
            } else {
                builder.insecure();
            }
        } else {
            // Use secure mode
            if (getUsername().isPresent() && getPassword().isPresent()) {
                builder.defaults(getUsername().get(), getPassword().get());
            } else {
                // Use default credentials (e.g., from ~/.docker/config.json)
                builder.defaults();
            }
        }
        
        return builder.build();
    }
}