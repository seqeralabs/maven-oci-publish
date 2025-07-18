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

package io.seqera.mavenoci

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal

import land.oras.Registry
import land.oras.LocalPath
import land.oras.ContainerRef
import land.oras.Manifest
import land.oras.ArtifactType
import land.oras.Annotations

/**
 * Task for publishing Maven artifacts to OCI registries using ORAS.
 */
class PublishToOciRepositoryTask extends DefaultTask {
    
    private static final Logger logger = Logging.getLogger(PublishToOciRepositoryTask)
    
    @Input
    final Property<String> registryUrl = project.objects.property(String)
    
    @Input
    final Property<String> repository = project.objects.property(String)
    
    @Input
    final Property<String> tag = project.objects.property(String)
    
    @Input
    @Optional
    final Property<String> username = project.objects.property(String)
    
    @Input
    @Optional
    final Property<String> password = project.objects.property(String)
    
    @Input
    final Property<Boolean> insecure = project.objects.property(Boolean)
    
    @InputFiles
    final ConfigurableFileCollection artifacts = project.objects.fileCollection()
    
    @Internal
    final Property<OciPublication> publication = project.objects.property(OciPublication)
    
    @Internal
    final Property<OciRepository> ociRepository = project.objects.property(OciRepository)
    
    PublishToOciRepositoryTask() {
        description = 'Publishes Maven artifacts to an OCI registry'
        group = 'publishing'
        
        // Set defaults
        insecure.set(false)
    }
    
    @TaskAction
    void publishToOciRegistry() {
        logger.info("Publishing to OCI registry: {}", registryUrl.get())
        
        try {
            // Create registry client
            Registry registry = createRegistry()
            
            // Convert artifacts to LocalPath objects
            List<LocalPath> artifactPaths = artifacts.files.collect { file ->
                // Determine media type based on file extension
                String mediaType = determineMediaType(file.name)
                LocalPath.of(file.absolutePath, mediaType)
            }
            
            if (artifactPaths.isEmpty()) {
                logger.warn("No artifacts to publish")
                return
            }
            
            // Create container reference
            String containerRef = "${repository.get()}:${tag.get()}"
            ContainerRef ref = ContainerRef.parse(containerRef)
            
            logger.info("Publishing {} artifacts to {}", artifactPaths.size(), containerRef)
            
            // Push artifacts to registry using varargs
            Manifest manifest = registry.pushArtifact(
                ref,
                ArtifactType.from("application/vnd.oci.image.manifest.v1+json"),
                Annotations.empty(),
                null,
                artifactPaths.toArray(new LocalPath[0])
            )
            
            logger.info("Successfully published artifacts. Schema version: {}", manifest.getSchemaVersion())
            
        } catch (Exception e) {
            logger.error("Failed to publish to OCI registry", e)
            throw new GradleException("Failed to publish to OCI registry: ${e.message}", e)
        }
    }
    
    private String determineMediaType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
        switch (extension) {
            case 'jar':
                return 'application/java-archive'
            case 'pom':
                return 'application/xml'
            case 'xml':
                return 'application/xml'
            case 'json':
                return 'application/json'
            case 'tar':
            case 'tgz':
            case 'tar.gz':
                return 'application/gzip'
            default:
                return 'application/octet-stream'
        }
    }
    
    private Registry createRegistry() {
        Registry.Builder builder = Registry.builder()
        
        // Configure credentials and security
        if (insecure.get()) {
            // Use insecure mode for development
            if (username.isPresent() && password.isPresent()) {
                builder.insecure(registryUrl.get(), username.get(), password.get())
            } else {
                builder.insecure()
            }
        } else {
            // Use secure mode
            if (username.isPresent() && password.isPresent()) {
                builder.defaults(username.get(), password.get())
            } else {
                // Use default credentials (e.g., from ~/.docker/config.json)
                builder.defaults()
            }
        }
        
        return builder.build()
    }
}
