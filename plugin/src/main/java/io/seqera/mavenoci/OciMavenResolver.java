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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolver that retrieves Maven artifacts from OCI registries using ORAS protocol.
 * This class handles the conversion from Maven coordinates to OCI references
 * and downloads artifacts using the ORAS Java SDK.
 */
public class OciMavenResolver {
    
    private static final Logger logger = Logging.getLogger(OciMavenResolver.class);
    
    private final String registryUrl;
    private final boolean insecure;
    private final String username;
    private final String password;
    
    public OciMavenResolver(String registryUrl, boolean insecure, String username, String password) {
        this.registryUrl = registryUrl;
        this.insecure = insecure;
        this.username = username;
        this.password = password;
    }
    
    /**
     * Resolves Maven artifacts from OCI registry to a local directory.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param targetDir Local directory to store resolved artifacts
     * @return true if artifacts were successfully resolved, false otherwise
     */
    public boolean resolveArtifacts(String groupId, String artifactId, String version, Path targetDir) {
        try {
            logger.info("Resolving Maven artifacts {}:{}:{} from OCI registry: {}", 
                       groupId, artifactId, version, registryUrl);
            
            // Create OCI reference from Maven coordinates
            String ociRef = buildOciReference(groupId, artifactId, version);
            logger.debug("Built OCI reference: {}", ociRef);
            
            // Create registry client
            Registry registry = createRegistry();
            
            // Parse container reference
            ContainerRef ref = ContainerRef.parse(ociRef);
            
            // Create target directory if it doesn't exist
            Files.createDirectories(targetDir);
            
            // Pull artifacts from OCI registry
            logger.info("Pulling artifacts from OCI registry: {}", ociRef);
            registry.pullArtifact(ref, targetDir, false);
            
            logger.info("Successfully pulled artifacts from OCI registry");
            
            // Verify that we got the expected Maven artifacts
            boolean hasJar = Files.exists(targetDir.resolve(artifactId + "-" + version + ".jar"));
            boolean hasPom = Files.exists(targetDir.resolve(artifactId + "-" + version + ".pom"));
            
            if (!hasJar && !hasPom) {
                logger.warn("No standard Maven artifacts found after OCI pull");
                // Try to find any files that were downloaded
                if (Files.exists(targetDir)) {
                    try {
                        Files.walk(targetDir, 1)
                            .filter(Files::isRegularFile)
                            .forEach(file -> logger.debug("Found file: {}", file.getFileName()));
                    } catch (IOException e) {
                        logger.debug("Error listing downloaded files", e);
                    }
                }
                return false;
            }
            
            logger.info("Successfully resolved Maven artifacts - JAR: {}, POM: {}", hasJar, hasPom);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to resolve Maven artifacts from OCI registry: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Builds OCI reference from Maven coordinates.
     * Uses the same coordinate mapping as the publishing side.
     */
    private String buildOciReference(String groupId, String artifactId, String version) {
        // Extract hostname from registry URL
        String registryHost = registryUrl.replaceFirst("^https?://", "");
        
        // Build reference: registry/group/artifact:version
        StringBuilder ref = new StringBuilder();
        ref.append(registryHost);
        
        // Add sanitized group
        String sanitizedGroup = MavenGroupSanitizer.sanitize(groupId);
        ref.append("/").append(sanitizedGroup);
        
        // Add artifact and version
        ref.append("/").append(artifactId).append(":").append(version);
        
        return ref.toString();
    }
    
    /**
     * Creates registry client with appropriate authentication.
     */
    private Registry createRegistry() {
        Registry.Builder builder = Registry.builder();
        
        if (insecure) {
            if (username != null && password != null) {
                logger.debug("Using insecure mode with credentials");
                String registryHost = registryUrl.replaceFirst("^https?://", "");
                builder.insecure(registryHost, username, password);
            } else {
                logger.debug("Using insecure mode with anonymous access");
                builder.insecure();
            }
        } else {
            if (username != null && password != null) {
                logger.debug("Using secure mode with credentials");
                builder.defaults(username, password);
            } else {
                logger.debug("Using secure mode with default credentials");
                builder.defaults();
            }
        }
        
        return builder.build();
    }
    
    /**
     * Checks if artifacts exist in the OCI registry without downloading them.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID  
     * @param version Maven version
     * @return true if artifacts exist, false otherwise
     */
    public boolean artifactsExist(String groupId, String artifactId, String version) {
        try {
            String ociRef = buildOciReference(groupId, artifactId, version);
            Registry registry = createRegistry();
            ContainerRef ref = ContainerRef.parse(ociRef);
            
            // Try to get manifest metadata without downloading
            logger.debug("Checking existence of OCI reference: {}", ociRef);
            
            // Create temporary directory for the check
            Path tempDir = Files.createTempDirectory("oci-check-");
            try {
                registry.pullArtifact(ref, tempDir, false);
                return true;
            } finally {
                // Clean up temporary directory
                try {
                    Files.walk(tempDir)
                        .map(Path::toFile)
                        .forEach(File::delete);
                    Files.deleteIfExists(tempDir);
                } catch (IOException e) {
                    logger.debug("Failed to clean up temporary directory", e);
                }
            }
            
        } catch (Exception e) {
            logger.debug("Artifacts do not exist or are not accessible: {}", e.getMessage());
            return false;
        }
    }
}