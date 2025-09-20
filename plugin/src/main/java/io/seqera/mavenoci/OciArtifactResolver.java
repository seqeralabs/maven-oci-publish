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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolver that converts OCI artifacts to Maven artifacts.
 * This class handles the conversion between OCI registry artifacts and Maven repository artifacts.
 */
public class OciArtifactResolver {
    
    private static final Logger logger = Logging.getLogger(OciArtifactResolver.class);
    
    private final OciRepositorySpec spec;
    private final Map<String, File> artifactCache = new ConcurrentHashMap<>();
    private final Map<String, File> pomCache = new ConcurrentHashMap<>();
    
    public OciArtifactResolver(OciRepositorySpec spec) {
        this.spec = spec;
        logger.info("Created OCI artifact resolver for repository: {}", spec.getName());
    }
    
    /**
     * Resolves a Maven artifact from an OCI registry.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The Maven version
     * @param extension The artifact extension (jar, pom, etc.)
     * @return The resolved artifact file, or null if not found
     */
    public File resolveArtifact(String groupId, String artifactId, String version, String extension) {
        String cacheKey = buildCacheKey(groupId, artifactId, version, extension);
        
        // Check cache first
        if ("pom".equals(extension)) {
            File cachedPom = pomCache.get(cacheKey);
            if (cachedPom != null && cachedPom.exists()) {
                logger.debug("Using cached POM: {}", cacheKey);
                return cachedPom;
            }
        } else {
            File cachedArtifact = artifactCache.get(cacheKey);
            if (cachedArtifact != null && cachedArtifact.exists()) {
                logger.debug("Using cached artifact: {}", cacheKey);
                return cachedArtifact;
            }
        }
        
        try {
            // Build OCI reference
            String ociReference = buildOciReference(groupId, artifactId, version);
            
            if ("pom".equals(extension)) {
                // Generate POM for this artifact
                return generatePomFile(groupId, artifactId, version, ociReference);
            } else {
                // Download OCI artifact and extract requested file
                return downloadAndExtractArtifact(ociReference, extension, cacheKey);
            }
            
        } catch (Exception e) {
            logger.error("Failed to resolve artifact: {}:{}:{}:{}", groupId, artifactId, version, extension, e);
            return null;
        }
    }
    
    /**
     * Checks if an artifact exists in the OCI registry.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The Maven version
     * @return true if the artifact exists, false otherwise
     */
    public boolean artifactExists(String groupId, String artifactId, String version) {
        try {
            String ociReference = buildOciReference(groupId, artifactId, version);
            return checkOciArtifactExists(ociReference);
        } catch (Exception e) {
            logger.debug("Error checking artifact existence: {}:{}:{}", groupId, artifactId, version, e);
            return false;
        }
    }
    
    /**
     * Downloads an OCI artifact and extracts the requested file.
     * 
     * @param ociReference The OCI reference
     * @param extension The requested file extension
     * @param cacheKey The cache key for storing the result
     * @return The extracted file
     */
    private File downloadAndExtractArtifact(String ociReference, String extension, String cacheKey) throws Exception {
        logger.info("Downloading OCI artifact: {}", ociReference);
        
        // Create registry client
        Registry registry = createRegistry();
        
        // Parse the OCI reference
        ContainerRef ref = ContainerRef.parse(ociReference);
        
        // Create temporary directory for downloads
        Path tempDir = Files.createTempDirectory("oci-artifact-");
        
        try {
            // Download the artifact
            registry.pullArtifact(ref, tempDir, false);
            
            // Find the requested file
            File requestedFile = findFileWithExtension(tempDir, extension);
            
            if (requestedFile == null) {
                throw new IOException("File with extension '" + extension + "' not found in OCI artifact");
            }
            
            // Create permanent cache file
            File cacheFile = createCacheFile(cacheKey);
            Files.copy(requestedFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            // Cache the result
            artifactCache.put(cacheKey, cacheFile);
            
            logger.info("Successfully resolved artifact: {} -> {}", cacheKey, cacheFile.getAbsolutePath());
            return cacheFile;
            
        } finally {
            // Clean up temporary directory
            deleteRecursively(tempDir);
        }
    }
    
    /**
     * Generates a Maven POM file for an OCI artifact.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The Maven version
     * @param ociReference The OCI reference
     * @return The generated POM file
     */
    private File generatePomFile(String groupId, String artifactId, String version, String ociReference) throws Exception {
        String cacheKey = buildCacheKey(groupId, artifactId, version, "pom");
        
        // Check if we can access the OCI artifact to validate it exists
        if (!checkOciArtifactExists(ociReference)) {
            logger.debug("OCI artifact does not exist: {}", ociReference);
            return null;
        }
        
        // Create minimal POM content
        String pomContent = generatePomContent(groupId, artifactId, version);
        
        // Create POM file
        File pomFile = createCacheFile(cacheKey);
        try (FileWriter writer = new FileWriter(pomFile)) {
            writer.write(pomContent);
        }
        
        // Cache the POM
        pomCache.put(cacheKey, pomFile);
        
        logger.info("Generated POM for OCI artifact: {} -> {}", ociReference, pomFile.getAbsolutePath());
        return pomFile;
    }
    
    /**
     * Generates minimal POM content for an OCI artifact.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The Maven version
     * @return The POM content as XML string
     */
    private String generatePomContent(String groupId, String artifactId, String version) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\" xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
               "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
               "  <modelVersion>4.0.0</modelVersion>\n" +
               "  <groupId>" + groupId + "</groupId>\n" +
               "  <artifactId>" + artifactId + "</artifactId>\n" +
               "  <version>" + version + "</version>\n" +
               "  <packaging>jar</packaging>\n" +
               "  <description>Artifact resolved from OCI registry</description>\n" +
               "  \n" +
               "  <!-- Generated by Maven OCI Publish Plugin -->\n" +
               "  <properties>\n" +
               "    <maven.compiler.source>11</maven.compiler.source>\n" +
               "    <maven.compiler.target>11</maven.compiler.target>\n" +
               "  </properties>\n" +
               "</project>\n";
    }
    
    /**
     * Builds an OCI reference from Maven coordinates.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The Maven version
     * @return The OCI reference
     */
    private String buildOciReference(String groupId, String artifactId, String version) {
        OciRegistryUriParser.OciRegistryInfo registryInfo = 
            OciRegistryUriParser.parse(spec.getUrl().getOrElse(""));
        
        return registryInfo.buildOciReference(groupId, artifactId, version);
    }
    
    /**
     * Checks if an OCI artifact exists in the registry.
     * 
     * @param ociReference The OCI reference
     * @return true if the artifact exists, false otherwise
     */
    private boolean checkOciArtifactExists(String ociReference) {
        try {
            Registry registry = createRegistry();
            ContainerRef ref = ContainerRef.parse(ociReference);
            
            // Try to get the manifest to check if artifact exists
            registry.getManifest(ref);
            return true;
            
        } catch (Exception e) {
            logger.debug("OCI artifact does not exist: {}", ociReference, e);
            return false;
        }
    }
    
    /**
     * Creates a registry client.
     * 
     * @return The registry client
     */
    private Registry createRegistry() {
        Registry.Builder builder = Registry.builder();
        
        if (spec.getInsecure().getOrElse(false)) {
            if (spec.hasCredentials()) {
                builder.insecure(spec.getUrl().get(), 
                               spec.getCredentials().get().getUsername(),
                               spec.getCredentials().get().getPassword());
            } else {
                builder.insecure();
            }
        } else {
            if (spec.hasCredentials()) {
                builder.defaults(spec.getCredentials().get().getUsername(),
                               spec.getCredentials().get().getPassword());
            } else {
                builder.defaults();
            }
        }
        
        return builder.build();
    }
    
    /**
     * Finds a file with the specified extension in a directory.
     * 
     * @param directory The directory to search
     * @param extension The file extension
     * @return The found file, or null
     */
    private File findFileWithExtension(Path directory, String extension) throws IOException {
        return Files.walk(directory)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith("." + extension))
            .map(Path::toFile)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Builds a cache key for an artifact.
     * 
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The Maven version
     * @param extension The file extension
     * @return The cache key
     */
    private String buildCacheKey(String groupId, String artifactId, String version, String extension) {
        return groupId + ":" + artifactId + ":" + version + ":" + extension;
    }
    
    /**
     * Creates a cache file for an artifact.
     * 
     * @param cacheKey The cache key
     * @return The cache file
     */
    private File createCacheFile(String cacheKey) throws IOException {
        // Create cache directory in system temp
        Path cacheDir = Files.createTempDirectory("gradle-oci-cache-");
        cacheDir.toFile().deleteOnExit();
        
        String fileName = cacheKey.replace(":", "_").replace("/", "_");
        File cacheFile = cacheDir.resolve(fileName).toFile();
        cacheFile.deleteOnExit();
        
        return cacheFile;
    }
    
    /**
     * Deletes a directory recursively.
     * 
     * @param path The directory to delete
     */
    private void deleteRecursively(Path path) {
        try {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))  // Delete files before directories
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        logger.warn("Failed to delete temporary file: {}", p, e);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to clean up temporary directory: {}", path, e);
        }
    }
}