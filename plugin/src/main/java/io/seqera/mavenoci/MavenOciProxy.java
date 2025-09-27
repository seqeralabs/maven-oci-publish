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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * HTTP proxy server that bridges Maven repository requests to OCI registry resolution.
 * 
 * <p>This class implements the core architecture for lazy, on-demand resolution of Maven artifacts 
 * from OCI registries. Instead of pre-resolving all dependencies before Gradle's repository chain 
 * begins, this proxy allows OCI repositories to participate naturally in Gradle's standard 
 * repository resolution order.</p>
 * 
 * <h2>Architecture Overview</h2>
 * <p>The proxy works by:</p>
 * <ol>
 *   <li>Starting a local HTTP server that mimics Maven repository structure</li>
 *   <li>Configuring Gradle's Maven repository to point to this proxy server</li>
 *   <li>When Gradle makes HTTP requests for artifacts, intercepting and parsing them</li>
 *   <li>Converting Maven coordinates to OCI references on-demand</li>
 *   <li>Using ORAS Java SDK to fetch artifacts from OCI registry only when requested</li>
 *   <li>Streaming artifacts directly back to Gradle via HTTP response</li>
 * </ol>
 * 
 * <h2>Repository Order Restoration</h2>
 * <p>This approach solves the key problem with the previous pre-resolution architecture:</p>
 * <ul>
 *   <li><strong>Before</strong>: All dependencies attempted OCI resolution before any repository checking</li>
 *   <li><strong>After</strong>: OCI resolution occurs only when Gradle reaches the OCI repository in its normal order</li>
 * </ul>
 * 
 * <h2>Request Flow Example</h2>
 * <pre>{@code
 * User Configuration:
 *   repositories {
 *     mavenCentral()
 *     mavenOci { url = 'https://registry.com/maven' }
 *   }
 * 
 * Dependency Resolution for org.slf4j:slf4j-api:2.0.7:
 *   1. Gradle tries mavenCentral() → FOUND (returns artifact)
 *   2. Proxy never called (no unnecessary OCI network calls)
 * 
 * Dependency Resolution for com.example:my-lib:1.0.0:
 *   1. Gradle tries mavenCentral() → 404 Not Found
 *   2. Gradle tries proxy: GET http://localhost:8543/maven/com/example/my-lib/1.0.0/my-lib-1.0.0.jar
 *   3. Proxy converts to OCI: registry.com/maven/com-example/my-lib:1.0.0
 *   4. ORAS SDK fetches from OCI registry
 *   5. Proxy streams artifact back: HTTP 200 + JAR bytes
 * }</pre>
 * 
 * <h2>Performance Benefits</h2>
 * <ul>
 *   <li><strong>True Lazy Resolution</strong>: Only resolves artifacts when Gradle actually requests them</li>
 *   <li><strong>Repository Order Respect</strong>: No unnecessary OCI calls for Maven Central artifacts</li>
 *   <li><strong>Network Efficiency</strong>: Dramatic reduction in network calls for mixed dependency trees</li>
 *   <li><strong>Memory Efficiency</strong>: Optional session caching with automatic cleanup</li>
 * </ul>
 * 
 * <h2>Error Handling</h2>
 * <p>The proxy handles errors gracefully:</p>
 * <ul>
 *   <li>Network failures to OCI registry result in HTTP 404 responses</li>
 *   <li>Invalid Maven requests result in HTTP 400 responses</li>
 *   <li>Server errors result in HTTP 500 responses</li>
 *   <li>All errors are logged but allow Gradle to continue with other repositories</li>
 * </ul>
 * 
 * <h2>Lifecycle Management</h2>
 * <p>The proxy server lifecycle is managed automatically:</p>
 * <ul>
 *   <li>Server starts with dynamic port allocation to avoid conflicts</li>
 *   <li>Session cache is maintained during build execution</li>
 *   <li>Server stops and cache clears when Gradle build finishes</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe:</p>
 * <ul>
 *   <li>HTTP server uses thread pool to handle concurrent requests</li>
 *   <li>Session cache uses {@link ConcurrentHashMap} for thread-safe access</li>
 *   <li>Multiple Maven repositories can use separate proxy instances</li>
 * </ul>
 * 
 * @see MavenArtifactRequest for Maven coordinate parsing
 * @see MavenOciResolver for OCI artifact resolution
 * @see MavenOciRepositoryFactory for proxy lifecycle management
 * @since 1.0
 */
public class MavenOciProxy {
    private static final Logger logger = LoggerFactory.getLogger(MavenOciProxy.class);
    
    private final String ociRegistryUrl;
    private final boolean insecure;
    private final MavenOciResolver ociResolver;
    private final ConcurrentHashMap<String, byte[]> sessionCache;
    
    private HttpServer server;
    private int port;
    
    /**
     * Creates a new HTTP proxy for the specified OCI registry.
     * 
     * @param ociRegistryUrl the base URL of the OCI registry to proxy requests to
     * @param insecure if true, allows insecure HTTP connections to the OCI registry
     */
    public MavenOciProxy(String ociRegistryUrl, boolean insecure) {
        this.ociRegistryUrl = ociRegistryUrl;
        this.insecure = insecure;
        this.ociResolver = new MavenOciResolver(ociRegistryUrl, insecure, null, null);
        this.sessionCache = new ConcurrentHashMap<>();
        
        logger.info("Created MavenOciProxy for OCI registry: {} (insecure: {})", ociRegistryUrl, insecure);
    }
    
    /**
     * Starts the HTTP proxy server on a dynamically allocated localhost port.
     * 
     * <p>The server will:</p>
     * <ul>
     *   <li>Listen for HTTP GET requests matching Maven repository structure</li>
     *   <li>Use a cached thread pool to handle concurrent requests</li>
     *   <li>Route all requests to the {@link MavenRequestHandler}</li>
     * </ul>
     * 
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        // Create HTTP server on localhost with dynamic port
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        port = server.getAddress().getPort();
        
        // Handle Maven repository requests
        server.createContext("/maven/", new MavenRequestHandler());
        
        // Use a thread pool to handle requests
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        logger.info("Started Maven OCI proxy server on http://localhost:{}/maven/ for OCI registry: {}", port, ociRegistryUrl);
    }
    
    /**
     * Stops the HTTP proxy server and clears the session cache.
     * 
     * <p>This method is idempotent and can be called multiple times safely.
     * It should be called when the Gradle build finishes to clean up resources.</p>
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            sessionCache.clear();
            logger.debug("Stopped Maven OCI proxy server for OCI registry: {}", ociRegistryUrl);
        }
    }
    
    /**
     * Returns the port number the HTTP server is listening on.
     * 
     * @return the dynamically allocated port number, or 0 if server is not started
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Returns the complete URL that Gradle's Maven repository should be configured to use.
     * 
     * @return the proxy URL in format http://localhost:PORT/maven/
     */
    public String getProxyUrl() {
        return "http://localhost:" + port + "/maven/";
    }
    
    /**
     * HTTP request handler that processes Maven repository requests and converts them to OCI resolution.
     * 
     * <p>This handler:</p>
     * <ul>
     *   <li>Accepts HTTP GET and HEAD requests (Maven repositories use GET for retrieval, HEAD for existence checks)</li>
     *   <li>Parses Maven repository request paths using {@link MavenArtifactRequest}</li>
     *   <li>Delegates to {@link #resolveArtifactFromOci(MavenArtifactRequest)} for OCI resolution</li>
     *   <li>Returns appropriate HTTP status codes (200, 400, 404, 405, 500)</li>
     *   <li>Properly handles HEAD requests by returning only headers without response bodies</li>
     * </ul>
     */
    private class MavenRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();
            String requestPath = exchange.getRequestURI().getPath();
            
            logger.debug("Handling Maven request: {} {}", requestMethod, requestPath);
            
            try {
                if (!"GET".equals(requestMethod) && !"HEAD".equals(requestMethod)) {
                    // Support both GET and HEAD requests for Maven artifacts
                    sendResponse(exchange, 405, "Method Not Allowed".getBytes());
                    return;
                }
                
                // Parse Maven artifact request
                MavenArtifactRequest request = MavenArtifactRequest.parse(requestPath);
                if (request == null) {
                    logger.debug("Invalid Maven request path: {}", requestPath);
                    sendResponse(exchange, 400, null);  // No body for error responses
                    return;
                }
                
                logger.debug("Parsed Maven request: {}", request);
                
                // Try to resolve artifact from OCI registry
                byte[] artifactContent = resolveArtifactFromOci(request);
                
                if (artifactContent != null) {
                    // Set appropriate content type
                    exchange.getResponseHeaders().set("Content-Type", request.getContentType());
                    
                    // Send successful response with artifact content (or just headers for HEAD)
                    if ("HEAD".equals(requestMethod)) {
                        sendResponse(exchange, 200, null);  // HEAD requests don't include body
                    } else {
                        sendResponse(exchange, 200, artifactContent);
                    }
                    
                    logger.info("Successfully served artifact from OCI: {} (size: {} bytes)", request.getCoordinate(), artifactContent.length);
                } else {
                    // Artifact not found in OCI registry
                    logger.debug("Artifact not found in OCI registry: {}", request.getCoordinate());
                    logger.warn("Could not find {} in OCI registry: {}", request.getCoordinate(), ociRegistryUrl);
                    
                    // Add custom header with original OCI URL for better error reporting
                    exchange.getResponseHeaders().set("X-OCI-Registry-URL", ociRegistryUrl);
                    sendResponse(exchange, 404, null);  // No body for error responses
                }
                
            } catch (Exception e) {
                logger.error("Error handling Maven request: {} {}", requestMethod, requestPath, e);
                sendResponse(exchange, 500, null);  // No body for error responses
            }
        }
        
        /**
         * Sends an HTTP response with the specified status code and body.
         * 
         * <p>This method properly handles HEAD requests by not sending response bodies,
         * following HTTP protocol requirements. For HEAD requests, only headers are sent.</p>
         * 
         * @param exchange the HTTP exchange to respond to
         * @param statusCode the HTTP status code (200, 400, 404, 405, 500)
         * @param responseBody the response body bytes (null for HEAD requests or errors)
         * @throws IOException if the response cannot be sent
         */
        private void sendResponse(HttpExchange exchange, int statusCode, byte[] responseBody) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("HEAD".equals(method) || responseBody == null) {
                // HEAD requests and error responses should not have bodies
                exchange.sendResponseHeaders(statusCode, -1);  // -1 means no content length
            } else {
                // GET requests with content
                exchange.sendResponseHeaders(statusCode, responseBody.length);
                exchange.getResponseBody().write(responseBody);
            }
            exchange.getResponseBody().close();
        }
    }
    
    /**
     * Resolves a Maven artifact from the OCI registry on-demand.
     * 
     * <p>This method:</p>
     * <ol>
     *   <li>Checks the session cache for previously resolved artifacts</li>
     *   <li>Uses {@link MavenOciResolver} to download artifacts from OCI registry</li>
     *   <li>Searches downloaded files for the requested artifact type</li>
     *   <li>Caches successful resolutions for the build session</li>
     * </ol>
     * 
     * @param request the parsed Maven artifact request
     * @return the artifact bytes if found, null if not found or resolution failed
     */
    private byte[] resolveArtifactFromOci(MavenArtifactRequest request) {
        String cacheKey = request.getCacheKey();
        
        // Check session cache first
        if (sessionCache.containsKey(cacheKey)) {
            logger.debug("Found artifact in session cache: {}", request.getCoordinate());
            return sessionCache.get(cacheKey);
        }
        
        try {
            logger.info("Attempting to resolve artifact from OCI registry: {} (registry: {})", request.getCoordinate(), ociRegistryUrl);
            
            // Create temporary directory for OCI resolution
            Path tempDir = Files.createTempDirectory("oci-proxy-");
            try {
                // Use OCI resolver to fetch artifacts
                boolean resolved = ociResolver.resolveArtifacts(
                    request.getGroupId(), 
                    request.getArtifactId(), 
                    request.getVersion(), 
                    tempDir
                );
                
                if (!resolved) {
                    logger.warn("Artifact not found in OCI registry: {} (registry: {})", request.getCoordinate(), ociRegistryUrl);
                    return null;
                } else {
                    logger.info("Successfully resolved artifact from OCI registry: {}", request.getCoordinate());
                }
                
                // Find the requested artifact file in the temp directory
                byte[] artifactContent = findArtifactFile(tempDir, request);
                
                if (artifactContent != null) {
                    // Cache for build session
                    sessionCache.put(cacheKey, artifactContent);
                    logger.debug("Cached artifact for session: {}", request.getCoordinate());
                }
                
                return artifactContent;
                
            } finally {
                // Clean up temporary directory
                cleanupTempDirectory(tempDir);
            }
            
        } catch (Exception e) {
            logger.debug("Failed to resolve artifact from OCI registry: {} - {}", request.getCoordinate(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Searches a temporary directory for the specific artifact file requested.
     * 
     * <p>This method handles the mapping between OCI artifact files and Maven naming conventions:</p>
     * <ul>
     *   <li>Looks for exact filename match first</li>
     *   <li>For POM files, tries variations without version suffix</li>
     *   <li>For JAR files, tries common naming patterns</li>
     * </ul>
     * 
     * @param tempDir the temporary directory containing downloaded OCI artifacts
     * @param request the Maven artifact request specifying the desired file
     * @return the artifact file bytes if found, null if not found
     * @throws IOException if file reading fails
     */
    private byte[] findArtifactFile(Path tempDir, MavenArtifactRequest request) throws IOException {
        // Look for the specific artifact file in the temp directory
        String expectedFileName = request.getFileName();
        
        // Debug: List all files in temp directory
        logger.info("Looking for artifact file '{}' in temp directory: {}", expectedFileName, tempDir);
        try {
            Files.list(tempDir).forEach(file -> {
                logger.info("  Found file: {}", file.getFileName());
            });
        } catch (IOException e) {
            logger.warn("Could not list files in temp directory: {}", e.getMessage());
        }
        
        // Try to find file with exact name first
        Path exactFile = tempDir.resolve(expectedFileName);
        if (Files.exists(exactFile)) {
            logger.info("Found exact artifact file: {}", exactFile);
            return Files.readAllBytes(exactFile);
        }
        
        // For POM files, try various naming patterns used in OCI artifacts
        if (request.getFileType().equals("pom")) {
            // Try various POM filename patterns
            String[] pomVariations = {
                "pom-default.xml",  // Gradle's default generated POM name
                request.getArtifactId() + ".pom",
                request.getArtifactId() + "-" + request.getVersion() + ".pom"
            };
            
            for (String variation : pomVariations) {
                Path pomFile = tempDir.resolve(variation);
                if (Files.exists(pomFile)) {
                    logger.info("Found POM file: {}", pomFile);
                    return Files.readAllBytes(pomFile);
                }
            }
            
            // If no POM file found, this is now an error since we should be publishing POMs
            logger.error("POM file not found for artifact: {} in OCI registry. " +
                        "This indicates the artifact was published without proper Maven metadata. " +
                        "Please ensure your publication includes POM files.", request.getCoordinate());
            return null; // Return null to indicate POM not found rather than generating a minimal one
        }
        
        // For checksum files (.sha1, .md5), look for them with various naming patterns
        if (request.getFileType().equals("sha1") || request.getFileType().equals("md5")) {
            // The request is for something like "lib-1.0.0.jar.sha1"
            // But the actual artifact in OCI might be named differently
            String baseFileName = request.getFileName();
            
            try {
                // Find any file that ends with the desired checksum extension
                String checksumExtension = "." + request.getFileType();
                
                Path checksumFile = Files.list(tempDir)
                    .filter(path -> path.getFileName().toString().endsWith(checksumExtension))
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        // Match files that contain the artifact type we're looking for
                        if (baseFileName.contains(".jar.")) {
                            return fileName.contains(".jar" + checksumExtension);
                        } else if (baseFileName.contains(".pom.")) {
                            return fileName.contains(".xml" + checksumExtension) || fileName.contains(".pom" + checksumExtension);
                        }
                        return false;
                    })
                    .findFirst()
                    .orElse(null);
                    
                if (checksumFile != null && Files.exists(checksumFile)) {
                    logger.info("Found checksum file: {}", checksumFile);
                    return Files.readAllBytes(checksumFile);
                }
            } catch (IOException e) {
                logger.warn("Error searching for checksum files: {}", e.getMessage());
            }
        }
        
        // For JAR files, try to match any JAR with appropriate classifier
        if (request.getFileType().equals("jar")) {
            try {
                // Find the appropriate JAR file based on classifier
                for (Path file : Files.list(tempDir).collect(java.util.stream.Collectors.toList())) {
                    String fileName = file.getFileName().toString();
                    if (!fileName.endsWith(".jar")) continue;
                    
                    if (request.getClassifier() == null) {
                        // Looking for main JAR (no classifier)
                        if (!fileName.contains("-sources") && !fileName.contains("-javadoc")) {
                            logger.info("Found main JAR file: {}", file);
                            return Files.readAllBytes(file);
                        }
                    } else if ("sources".equals(request.getClassifier())) {
                        if (fileName.contains("-sources")) {
                            logger.info("Found sources JAR file: {}", file);
                            return Files.readAllBytes(file);
                        }
                    } else if ("javadoc".equals(request.getClassifier())) {
                        if (fileName.contains("-javadoc")) {
                            logger.info("Found javadoc JAR file: {}", file);
                            return Files.readAllBytes(file);
                        }
                    }
                }
            } catch (IOException e) {
                logger.warn("Error searching for JAR files: {}", e.getMessage());
            }
        }
        
        logger.warn("Artifact file not found in temp directory: {} (looking for: {})", tempDir, expectedFileName);
        return null;
    }
    
    /**
     * Recursively deletes a temporary directory and all its contents.
     * 
     * <p>This method is safe and will not throw exceptions if cleanup fails,
     * but will log debug messages for any failures.</p>
     * 
     * @param tempDir the temporary directory to clean up
     */
    private void cleanupTempDirectory(Path tempDir) {
        try {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.debug("Failed to delete temp file: {}", path);
                    }
                });
        } catch (IOException e) {
            logger.debug("Failed to clean up temp directory: {}", tempDir);
        }
    }
}