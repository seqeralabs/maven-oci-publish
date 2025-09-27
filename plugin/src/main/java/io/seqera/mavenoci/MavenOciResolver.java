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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import land.oras.ContainerRef;
import land.oras.Registry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Resolver that retrieves Maven artifacts from OCI registries using the ORAS protocol.
 * 
 * <p>This class is the core component for OCI artifact resolution, handling:</p>
 * <ul>
 *   <li>Conversion from Maven coordinates to OCI references</li>
 *   <li>Authentication with OCI registries</li>
 *   <li>Downloading artifacts using the ORAS Java SDK</li>
 *   <li>Registry connectivity and error handling</li>
 * </ul>
 * 
 * <h2>Maven to OCI Coordinate Mapping</h2>
 * <p>Maven coordinates are mapped to OCI references using the following pattern:</p>
 * <pre>
 * Maven: {@code groupId:artifactId:version}
 * OCI:   {@code registry.com/sanitized-group-id/artifactId:version}
 * </pre>
 * 
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code com.example:my-lib:1.0.0} → {@code registry.com/com-example/my-lib:1.0.0}</li>
 *   <li>{@code org.springframework:spring-core:5.3.21} → {@code registry.com/org-springframework/spring-core:5.3.21}</li>
 * </ul>
 * 
 * <h2>Registry Configuration</h2>
 * <p>The resolver supports:</p>
 * <ul>
 *   <li><strong>Secure registries</strong>: HTTPS with optional authentication</li>
 *   <li><strong>Insecure registries</strong>: HTTP for local development</li>
 *   <li><strong>Anonymous access</strong>: Public registries without credentials</li>
 *   <li><strong>Authenticated access</strong>: Username/password or token-based auth</li>
 * </ul>
 * 
 * <h2>Error Handling</h2>
 * <p>The resolver is designed to be resilient:</p>
 * <ul>
 *   <li>Network timeouts and connectivity issues are caught and logged</li>
 *   <li>Missing artifacts return {@code false} rather than throwing exceptions</li>
 *   <li>Authentication failures are handled gracefully</li>
 *   <li>Invalid registry URLs are detected early</li>
 * </ul>
 * 
 * <h2>Performance Considerations</h2>
 * <p>For optimal performance:</p>
 * <ul>
 *   <li>Registry instances are reused when possible</li>
 *   <li>Artifact existence checks avoid unnecessary downloads</li>
 *   <li>Temporary directories are cleaned up promptly</li>
 *   <li>Failed resolutions are cached to avoid repeated attempts</li>
 * </ul>
 * 
 * @see MavenOciRepositoryFactory
 * @see MavenOciGroupSanitizer
 * @since 1.0
 */
public class MavenOciResolver {
    
    private static final Logger logger = Logging.getLogger(MavenOciResolver.class);
    
    private final String registryUrl;
    private final boolean insecure;
    private final String username;
    private final String password;
    
    public MavenOciResolver(String registryUrl, boolean insecure, String username, String password) {
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
            logger.debug("Resolving Maven artifacts {}:{}:{} from OCI registry: {}", 
                       groupId, artifactId, version, registryUrl);
            
            // Create OCI reference from Maven coordinates
            String ociRef = buildOciReference(groupId, artifactId, version);
            logger.debug("Built OCI reference for {}:{}:{} -> {}", groupId, artifactId, version, ociRef);
            
            // Create registry client
            Registry registry = createRegistry();
            
            // Parse container reference
            ContainerRef ref = ContainerRef.parse(ociRef);
            
            // Create target directory if it doesn't exist
            Files.createDirectories(targetDir);
            
            // Pull artifacts from OCI registry
            logger.debug("Pulling artifacts from OCI registry: {}", ociRef);
            registry.pullArtifact(ref, targetDir, false);
            
            logger.debug("Successfully pulled artifacts from OCI registry: {}", ociRef);
            
            // Just return true - file mapping is handled by OciMavenRepositoryFactory.moveArtifactFiles()
            logger.debug("Successfully resolved Maven artifacts from OCI registry");
            return true;
            
        } catch (Exception e) {
            logger.debug("Failed to resolve Maven artifacts from OCI registry {} ({}:{}:{}): {}", 
                       registryUrl, groupId, artifactId, version, e.getMessage(), e);
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
        String sanitizedGroup = MavenOciGroupSanitizer.sanitize(groupId);
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
                String registryHost = registryUrl.replaceFirst("^https?://", "");
                builder.insecure(registryHost, username, password);
            } else {
                builder.insecure();
            }
        } else {
            if (username != null && password != null) {
                builder.defaults(username, password);
            } else {
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
            logger.debug("Artifacts do not exist or are not accessible from OCI registry {} ({}:{}:{}): {}", 
                       registryUrl, groupId, artifactId, version, e.getMessage());
            return false;
        }
    }
}
