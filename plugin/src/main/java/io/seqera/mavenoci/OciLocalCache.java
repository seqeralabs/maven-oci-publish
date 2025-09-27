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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Persistent local cache for OCI artifacts, similar to Maven's ~/.m2/repository.
 * 
 * <p>This cache provides:</p>
 * <ul>
 *   <li><strong>Persistent storage</strong>: Artifacts persist between builds</li>
 *   <li><strong>Thread safety</strong>: Safe for concurrent access across builds</li>
 *   <li><strong>Maven structure</strong>: Compatible with Maven repository layout</li>
 *   <li><strong>Registry isolation</strong>: Separate cache per OCI registry</li>
 * </ul>
 * 
 * <h2>Cache Structure</h2>
 * <pre>
 * ~/.gradle/caches/maven-oci-plugin/
 * └── repositories/
 *     └── {registry-hash}/
 *         └── {group-path}/
 *             └── {artifactId}/
 *                 └── {version}/
 *                     ├── {artifactId}-{version}.jar
 *                     ├── {artifactId}-{version}.pom
 *                     ├── {artifactId}-{version}.jar.sha1
 *                     └── {artifactId}-{version}.jar.md5
 * </pre>
 * 
 * <h2>Registry Hash</h2>
 * <p>Each OCI registry gets its own subdirectory based on a hash of the registry URL.
 * This prevents conflicts between different registries and allows for registry-specific
 * cache management.</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>The cache uses file-based locking to ensure safe concurrent access across
 * multiple Gradle builds and processes. Read operations can occur concurrently,
 * but write operations are exclusive.</p>
 * 
 * @see MavenOciProxy for HTTP proxy integration
 * @see MavenOciResolver for OCI artifact resolution
 * @since 1.0
 */
public class OciLocalCache {
    
    private static final Logger logger = Logging.getLogger(OciLocalCache.class);
    
    private static final String CACHE_DIR_NAME = "maven-oci-plugin";
    private static final String REPOSITORIES_DIR = "repositories";
    
    private final Path cacheRoot;
    private final String registryUrl;
    private final String registryHash;
    private final Path registryCacheDir;
    
    // Thread-safe locks for concurrent access
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> fileLocks = new ConcurrentHashMap<>();
    
    /**
     * Creates a new OCI local cache for the specified registry.
     * 
     * @param registryUrl the OCI registry URL
     */
    public OciLocalCache(String registryUrl) {
        this.registryUrl = registryUrl;
        this.registryHash = computeRegistryHash(registryUrl);
        
        // Use Gradle's standard cache directory
        String userHome = System.getProperty("user.home");
        this.cacheRoot = Paths.get(userHome, ".gradle", "caches", CACHE_DIR_NAME);
        this.registryCacheDir = cacheRoot.resolve(REPOSITORIES_DIR).resolve(registryHash);
        
        // Ensure cache directory exists
        try {
            Files.createDirectories(registryCacheDir);
            logger.debug("Initialized OCI cache directory: {}", registryCacheDir);
        } catch (IOException e) {
            logger.warn("Failed to create OCI cache directory: {}", registryCacheDir, e);
        }
    }
    
    /**
     * Checks if an artifact file exists in the cache.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param fileName the specific file name (e.g., "my-lib-1.0.0.jar")
     * @return true if the file exists in cache, false otherwise
     */
    public boolean hasArtifactFile(String groupId, String artifactId, String version, String fileName) {
        Path artifactFile = getArtifactFilePath(groupId, artifactId, version, fileName);
        boolean exists = Files.exists(artifactFile) && Files.isRegularFile(artifactFile);
        
        if (exists) {
            logger.debug("Cache hit for artifact file: {}", artifactFile);
        } else {
            logger.debug("Cache miss for artifact file: {}", artifactFile);
        }
        
        return exists;
    }
    
    /**
     * Retrieves an artifact file from the cache.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param fileName the specific file name
     * @return the file content as bytes, or null if not found
     */
    public byte[] getArtifactFile(String groupId, String artifactId, String version, String fileName) {
        Path artifactFile = getArtifactFilePath(groupId, artifactId, version, fileName);
        String lockKey = artifactFile.toString();
        
        ReentrantReadWriteLock lock = fileLocks.computeIfAbsent(lockKey, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();
        
        try {
            if (Files.exists(artifactFile) && Files.isRegularFile(artifactFile)) {
                byte[] content = Files.readAllBytes(artifactFile);
                logger.debug("Retrieved artifact from cache: {} ({} bytes)", artifactFile, content.length);
                return content;
            }
        } catch (IOException e) {
            logger.warn("Failed to read cached artifact file: {}", artifactFile, e);
        } finally {
            lock.readLock().unlock();
        }
        
        return null;
    }
    
    /**
     * Stores an artifact file in the cache.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param fileName the specific file name
     * @param content the file content as bytes
     * @return true if successfully stored, false otherwise
     */
    public boolean storeArtifactFile(String groupId, String artifactId, String version, String fileName, byte[] content) {
        Path artifactFile = getArtifactFilePath(groupId, artifactId, version, fileName);
        String lockKey = artifactFile.toString();
        
        ReentrantReadWriteLock lock = fileLocks.computeIfAbsent(lockKey, k -> new ReentrantReadWriteLock());
        lock.writeLock().lock();
        
        try {
            // Create parent directories
            Files.createDirectories(artifactFile.getParent());
            
            // Write file atomically
            Path tempFile = artifactFile.resolveSibling(fileName + ".tmp");
            Files.write(tempFile, content);
            Files.move(tempFile, artifactFile, StandardCopyOption.REPLACE_EXISTING);
            
            logger.debug("Stored artifact in cache: {} ({} bytes)", artifactFile, content.length);
            return true;
            
        } catch (IOException e) {
            logger.warn("Failed to store artifact file in cache: {}", artifactFile, e);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets the cache directory for a specific Maven artifact.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @return the path to the artifact's cache directory
     */
    public Path getArtifactCacheDir(String groupId, String artifactId, String version) {
        String groupPath = groupId.replace('.', '/');
        return registryCacheDir.resolve(groupPath).resolve(artifactId).resolve(version);
    }
    
    /**
     * Gets the full path for a specific artifact file in the cache.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param fileName the specific file name
     * @return the full path to the cached file
     */
    public Path getArtifactFilePath(String groupId, String artifactId, String version, String fileName) {
        return getArtifactCacheDir(groupId, artifactId, version).resolve(fileName);
    }
    
    /**
     * Copies all files from a source directory to the artifact cache directory.
     * This is used when downloading complete artifact bundles from OCI registry.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param sourceDir directory containing downloaded OCI artifacts
     * @return true if successful, false otherwise
     */
    public boolean storeArtifactBundle(String groupId, String artifactId, String version, Path sourceDir) {
        Path targetDir = getArtifactCacheDir(groupId, artifactId, version);
        
        try {
            Files.createDirectories(targetDir);
            
            // Copy all files from source to cache with Maven naming conventions
            Files.list(sourceDir)
                .filter(Files::isRegularFile)
                .forEach(sourceFile -> {
                    try {
                        String normalizedName = normalizeMavenFileName(sourceFile.getFileName().toString(), artifactId, version);
                        Path targetFile = targetDir.resolve(normalizedName);
                        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        logger.debug("Cached artifact file: {} -> {}", sourceFile.getFileName(), normalizedName);
                    } catch (IOException e) {
                        logger.warn("Failed to cache artifact file: {}", sourceFile, e);
                    }
                });
            
            logger.info("Stored artifact bundle in cache: {}:{}:{} -> {}", groupId, artifactId, version, targetDir);
            return true;
            
        } catch (IOException e) {
            logger.warn("Failed to store artifact bundle in cache: {}:{}:{}", groupId, artifactId, version, e);
            return false;
        }
    }
    
    /**
     * Normalizes OCI artifact filenames to Maven naming conventions.
     * 
     * <p>This method converts OCI artifact names to the names that Gradle expects:</p>
     * <ul>
     *   <li>pom-default.xml → {artifactId}-{version}.pom</li>
     *   <li>{anything}-{version}.jar → {artifactId}-{version}.jar</li>
     *   <li>{anything}-{version}-sources.jar → {artifactId}-{version}-sources.jar</li>
     *   <li>{anything}-{version}-javadoc.jar → {artifactId}-{version}-javadoc.jar</li>
     *   <li>Preserves checksums with normalized base names</li>
     * </ul>
     * 
     * @param originalName the original filename from OCI
     * @param artifactId the Maven artifact ID
     * @param version the Maven version
     * @return the normalized filename following Maven conventions
     */
    private String normalizeMavenFileName(String originalName, String artifactId, String version) {
        String fileName = originalName;
        
        // Handle POM files
        if ("pom-default.xml".equals(fileName)) {
            return artifactId + "-" + version + ".pom";
        }
        
        // Handle POM checksums
        if ("pom-default.xml.sha1".equals(fileName)) {
            return artifactId + "-" + version + ".pom.sha1";
        }
        if ("pom-default.xml.md5".equals(fileName)) {
            return artifactId + "-" + version + ".pom.md5";
        }
        
        // Handle JAR files with classifiers
        if (fileName.endsWith("-sources.jar")) {
            return artifactId + "-" + version + "-sources.jar";
        }
        if (fileName.endsWith("-sources.jar.sha1")) {
            return artifactId + "-" + version + "-sources.jar.sha1";
        }
        if (fileName.endsWith("-sources.jar.md5")) {
            return artifactId + "-" + version + "-sources.jar.md5";
        }
        
        if (fileName.endsWith("-javadoc.jar")) {
            return artifactId + "-" + version + "-javadoc.jar";
        }
        if (fileName.endsWith("-javadoc.jar.sha1")) {
            return artifactId + "-" + version + "-javadoc.jar.sha1";
        }
        if (fileName.endsWith("-javadoc.jar.md5")) {
            return artifactId + "-" + version + "-javadoc.jar.md5";
        }
        
        // Handle main JAR files
        if (fileName.endsWith(".jar") && !fileName.contains("-sources") && !fileName.contains("-javadoc")) {
            return artifactId + "-" + version + ".jar";
        }
        if (fileName.endsWith(".jar.sha1") && !fileName.contains("-sources") && !fileName.contains("-javadoc")) {
            return artifactId + "-" + version + ".jar.sha1";
        }
        if (fileName.endsWith(".jar.md5") && !fileName.contains("-sources") && !fileName.contains("-javadoc")) {
            return artifactId + "-" + version + ".jar.md5";
        }
        
        // For any other files, return as-is (this handles edge cases)
        logger.debug("No normalization rule for file: {}, keeping original name", fileName);
        return fileName;
    }
    
    /**
     * Computes a hash of the registry URL to create a unique cache directory.
     * 
     * @param registryUrl the OCI registry URL
     * @return a hash string suitable for use as a directory name
     */
    private String computeRegistryHash(String registryUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(registryUrl.getBytes("UTF-8"));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            
            // Use first 8 characters for readability
            return sb.substring(0, 8);
            
        } catch (Exception e) {
            logger.debug("Failed to compute registry hash, using fallback", e);
            // Fallback: simple hash based on URL length and characters
            return String.format("%08x", registryUrl.hashCode());
        }
    }
    
    /**
     * Gets the registry URL this cache is associated with.
     * 
     * @return the OCI registry URL
     */
    public String getRegistryUrl() {
        return registryUrl;
    }
    
    /**
     * Gets the root cache directory for this registry.
     * 
     * @return the path to the registry's cache directory
     */
    public Path getCacheDirectory() {
        return registryCacheDir;
    }
    
    /**
     * Clears all cached artifacts for this registry.
     * This is useful for debugging or when registry content changes.
     */
    public void clearCache() {
        try {
            if (Files.exists(registryCacheDir)) {
                Files.walk(registryCacheDir)
                    .map(Path::toFile)
                    .forEach(File::delete);
                
                logger.info("Cleared OCI cache for registry: {}", registryUrl);
            }
        } catch (IOException e) {
            logger.warn("Failed to clear OCI cache for registry: {}", registryUrl, e);
        }
    }
}