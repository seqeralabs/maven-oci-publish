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

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Factory for creating Maven repositories that can resolve artifacts from OCI registries.
 * 
 * <p>This factory creates an HTTP proxy approach where:</p>
 * <ul>
 *   <li>A local HTTP server mimics Maven repository structure</li>
 *   <li>Artifacts are resolved from OCI registries on-demand when Gradle requests them</li>
 *   <li>No pre-resolution or cache management needed</li>
 *   <li>Respects Gradle's natural repository ordering</li>
 * </ul>
 * 
 * <h2>How it works</h2>
 * <p>When a dependency resolution occurs:</p>
 * <ol>
 *   <li>Gradle checks repositories in declared order (mavenCentral, then mavenOci)</li>
 *   <li>When Gradle makes HTTP request to OCI repository, it hits the local proxy</li>
 *   <li>Proxy parses Maven request and converts to OCI reference</li>
 *   <li>ORAS Java SDK pulls the artifact from OCI registry on-demand</li>
 *   <li>Artifact is streamed directly back to Gradle</li>
 *   <li>Optional session caching avoids repeated OCI calls</li>
 * </ol>
 * 
 * <h2>Benefits Over Pre-Resolution Architecture</h2>
 * <ul>
 *   <li><strong>True lazy resolution</strong> - only resolves when Gradle actually requests artifacts</li>
 *   <li><strong>Repository order respect</strong> - no unnecessary OCI calls for Maven Central artifacts</li>
 *   <li><strong>Performance improvement</strong> - dramatically faster for mixed Maven/OCI dependency trees</li>
 *   <li><strong>Memory efficiency</strong> - no persistent cache directories to manage</li>
 *   <li><strong>Standard integration</strong> - works exactly like any Maven repository</li>
 * </ul>
 * 
 * <h2>Architecture Revolution</h2>
 * <p>This factory represents a fundamental shift from the previous pre-resolution approach:</p>
 * <table>
 *   <caption>Performance Comparison: Pre-Resolution vs HTTP Proxy</caption>
 *   <tr><th>Aspect</th><th>Old (Pre-Resolution)</th><th>New (HTTP Proxy)</th></tr>
 *   <tr><td>Network Calls</td><td>ALL dependencies try OCI first</td><td>Only OCI dependencies call OCI</td></tr>
 *   <tr><td>Repository Order</td><td>Bypassed via beforeResolve hook</td><td>Natural Gradle order respected</td></tr>
 *   <tr><td>Performance</td><td>Slow (resolves everything upfront)</td><td>Fast (lazy on-demand)</td></tr>
 * </table>
 * 
 * <h2>Error Handling</h2>
 * <p>The factory is designed to fail gracefully:</p>
 * <ul>
 *   <li>If an artifact doesn't exist in OCI, proxy returns HTTP 404 and Gradle continues</li>
 *   <li>Network failures return HTTP 500 but don't break the build</li>
 *   <li>Proxy lifecycle is managed automatically with build cleanup</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * repositories {
 *     mavenCentral()                    // Checked FIRST
 *     mavenOci { url = 'registry.com' } // Only if Maven Central fails
 * }
 * 
 * // For org.slf4j:slf4j-api:2.0.7:
 * // 1. Gradle tries Maven Central → FOUND → STOP (no OCI call)
 * 
 * // For com.example:my-lib:1.0.0:
 * // 1. Gradle tries Maven Central → 404
 * // 2. Gradle tries OCI proxy → converts to registry.com/com-example/my-lib:1.0.0
 * // 3. Proxy streams artifact back → HTTP 200
 * }</pre>
 * 
 * @see MavenOciProxy for the HTTP proxy server implementation
 * @see MavenArtifactRequest for Maven coordinate parsing
 * @see MavenOciResolver for OCI artifact resolution using ORAS
 * @see MavenOciGroupSanitizer for Maven to OCI coordinate mapping
 * @since 1.0
 */
public class MavenOciRepositoryFactory {
    
    private static final Logger logger = Logging.getLogger(MavenOciRepositoryFactory.class);
    
    // Keep track of proxy servers for cleanup
    private static final ConcurrentHashMap<String, MavenOciProxy> activeProxies = new ConcurrentHashMap<>();
    
    /**
     * Creates a Maven repository that resolves artifacts from an OCI registry using HTTP proxy.
     * 
     * @param spec OCI repository specification
     * @param repository Maven repository to configure
     * @param project Gradle project
     */
    public static void createOciMavenRepository(MavenOciRepositorySpec spec, MavenArtifactRepository repository, Project project) {
        try {
            logger.debug("Creating OCI-backed Maven repository with HTTP proxy: {}", spec.getName());
            
            String registryUrl = spec.getUrl().getOrElse("").toString();
            boolean insecure = spec.getInsecure().getOrElse(false);
            
            // Create and start HTTP proxy for this OCI registry
            MavenOciProxy proxy = new MavenOciProxy(registryUrl, insecure);
            proxy.start();
            
            // Store proxy for cleanup
            String proxyKey = project.getPath() + ":" + spec.getName();
            activeProxies.put(proxyKey, proxy);
            
            // Configure repository to point to HTTP proxy
            repository.setUrl(proxy.getProxyUrl());
            
            // Always allow insecure for localhost proxy
            repository.setAllowInsecureProtocol(true);
            
            // Set up cleanup when build finishes
            setupProxyCleanup(project, proxyKey);
            
            logger.info("Created OCI-backed repository '{}' with HTTP proxy: {} -> {}", 
                       spec.getName(), registryUrl, proxy.getProxyUrl());
            
        } catch (Exception e) {
            logger.error("Failed to create OCI-backed Maven repository with HTTP proxy", e);
            throw new RuntimeException("Failed to create OCI-backed Maven repository with HTTP proxy", e);
        }
    }
    
    /**
     * Sets up cleanup for the HTTP proxy when the build finishes.
     * 
     * @param project Gradle project
     * @param proxyKey Key for the proxy in activeProxies map
     */
    private static void setupProxyCleanup(Project project, String proxyKey) {
        project.getGradle().buildFinished(result -> {
            MavenOciProxy proxy = activeProxies.remove(proxyKey);
            if (proxy != null) {
                try {
                    proxy.stop();
                    logger.debug("Cleaned up HTTP proxy for: {}", proxyKey);
                } catch (Exception e) {
                    logger.debug("Failed to stop HTTP proxy for: {}", proxyKey, e);
                }
            }
        });
    }
    
    /**
     * Stops all active HTTP proxy servers.
     * This is typically called during plugin cleanup or testing.
     */
    public static void stopAllProxies() {
        activeProxies.values().forEach(proxy -> {
            try {
                proxy.stop();
            } catch (Exception e) {
                logger.debug("Failed to stop proxy during cleanup", e);
            }
        });
        activeProxies.clear();
        logger.debug("Stopped all HTTP proxy servers");
    }
    
    
}
