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

import land.oras.ContainerRef;
import land.oras.Registry;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Resource accessor for downloading artifacts from OCI registries.
 * This class uses the ORAS Java SDK to pull artifacts from OCI registries.
 */
public class OciResourceAccessor {
    
    private static final Logger logger = Logging.getLogger(OciResourceAccessor.class);
    
    private final OciRepositorySpec spec;
    
    public OciResourceAccessor(OciRepositorySpec spec) {
        this.spec = spec;
        logger.info("Created OCI resource accessor for repository: {}", spec.getName());
    }
    
    /**
     * Downloads artifacts from an OCI registry for the given OCI reference.
     * 
     * @param ociReference The OCI reference to download (e.g., docker.io/maven/io-seqera/shared-library:1.0.0)
     * @return List of downloaded artifact files
     */
    public List<File> downloadArtifacts(String ociReference) {
        logger.info("Downloading artifacts from OCI reference: {}", ociReference);
        
        try {
            // Create registry client
            Registry registry = createRegistry();
            
            // Parse the OCI reference
            ContainerRef ref = ContainerRef.parse(ociReference);
            
            // Create temporary directory for downloads
            Path tempDir = Files.createTempDirectory("oci-artifacts-");
            logger.debug("Created temporary download directory: {}", tempDir);
            
            // Download artifacts using ORAS
            List<File> downloadedFiles = new ArrayList<>();
            
            // Pull the artifact from the registry
            // Note: This is a simplified implementation
            // In a real implementation, you would use ORAS to pull specific files
            registry.pullArtifact(ref, tempDir, false);
            
            // Collect all downloaded files
            Files.walk(tempDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        // Move file to a permanent location in Gradle's cache
                        File permanentFile = createPermanentFile(file, ociReference);
                        Files.move(file, permanentFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        downloadedFiles.add(permanentFile);
                        logger.debug("Downloaded artifact: {}", permanentFile.getName());
                    } catch (IOException e) {
                        logger.warn("Failed to move downloaded file: {}", file, e);
                    }
                });
            
            // Clean up temporary directory
            Files.deleteIfExists(tempDir);
            
            logger.info("Successfully downloaded {} artifacts from {}", downloadedFiles.size(), ociReference);
            return downloadedFiles;
            
        } catch (Exception e) {
            logger.error("Failed to download artifacts from OCI reference: {}", ociReference, e);
            throw new GradleException("Failed to download artifacts from OCI registry: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if an artifact exists in the OCI registry.
     * 
     * @param ociReference The OCI reference to check
     * @return true if the artifact exists, false otherwise
     */
    public boolean artifactExists(String ociReference) {
        logger.debug("Checking if artifact exists: {}", ociReference);
        
        try {
            // Create registry client
            Registry registry = createRegistry();
            
            // Parse the OCI reference
            ContainerRef ref = ContainerRef.parse(ociReference);
            
            // Try to get the manifest to check if artifact exists
            // This is a simplified check - in practice you might use a HEAD request
            registry.getManifest(ref);
            
            logger.debug("Artifact exists: {}", ociReference);
            return true;
            
        } catch (Exception e) {
            logger.debug("Artifact does not exist or is not accessible: {}", ociReference, e);
            return false;
        }
    }
    
    /**
     * Creates a registry client based on the repository specification.
     * 
     * @return The configured registry client
     */
    private Registry createRegistry() {
        Registry.Builder builder = Registry.builder();
        
        // Configure credentials and security
        if (spec.getInsecure().getOrElse(false)) {
            // Use insecure mode for development
            if (spec.hasCredentials()) {
                logger.debug("Using insecure mode with explicit credentials for {}", spec.getName());
                builder.insecure(spec.getUrl().get(), 
                               spec.getCredentials().get().getUsername(),
                               spec.getCredentials().get().getPassword());
            } else {
                logger.debug("Using insecure mode with anonymous access for {}", spec.getName());
                builder.insecure();
            }
        } else {
            // Use secure mode
            if (spec.hasCredentials()) {
                logger.debug("Using secure mode with explicit credentials for {}", spec.getName());
                builder.defaults(spec.getCredentials().get().getUsername(),
                               spec.getCredentials().get().getPassword());
            } else {
                logger.debug("Using secure mode with anonymous access for {}", spec.getName());
                builder.defaults();
            }
        }
        
        return builder.build();
    }
    
    /**
     * Creates a permanent file for a downloaded artifact in Gradle's cache.
     * 
     * @param tempFile The temporary file to make permanent
     * @param ociReference The OCI reference for cache key generation
     * @return The permanent file location
     */
    private File createPermanentFile(Path tempFile, String ociReference) throws IOException {
        // Create a cache directory based on the OCI reference
        String cacheKey = ociReference.replace(":", "_").replace("/", "_");
        
        // Use system temp directory for now - in production this would be Gradle's cache
        Path cacheDir = Files.createTempDirectory("gradle-oci-cache-" + cacheKey);
        cacheDir.toFile().deleteOnExit();
        
        File permanentFile = cacheDir.resolve(tempFile.getFileName()).toFile();
        permanentFile.deleteOnExit();
        
        return permanentFile;
    }
}