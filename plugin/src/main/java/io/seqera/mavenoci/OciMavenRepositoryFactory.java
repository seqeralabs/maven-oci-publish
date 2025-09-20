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

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Factory for creating Maven repositories that can resolve artifacts from OCI registries.
 * This creates a hybrid approach where artifacts are pulled from OCI on-demand and
 * cached in a local Maven repository structure.
 */
public class OciMavenRepositoryFactory {
    
    private static final Logger logger = Logging.getLogger(OciMavenRepositoryFactory.class);
    
    /**
     * Creates a Maven repository that resolves artifacts from an OCI registry.
     * 
     * @param spec OCI repository specification
     * @param repository Maven repository to configure
     * @param project Gradle project
     */
    public static void createOciMavenRepository(OciRepositorySpec spec, MavenArtifactRepository repository, Project project) {
        try {
            logger.info("Creating OCI-backed Maven repository: {}", spec.getName());
            
            // Create a persistent cache directory for this OCI repository
            Path cacheDir = createCacheDirectory(spec, project);
            
            // Configure the Maven repository to use the cache directory
            repository.setName(spec.getName() + "_oci_cache");
            repository.setUrl(cacheDir.toUri());
            
            // Create OCI resolver for on-demand artifact retrieval
            String registryUrl = spec.getUrl().getOrElse("").toString();
            boolean insecure = spec.getInsecure().getOrElse(false);
            
            // Get credentials if available
            String username = null;
            String password = null;
            if (spec.getCredentials().isPresent()) {
                // For simplicity, assume string credentials (would need proper credential handling in real implementation)
                logger.debug("Credentials are configured for OCI repository");
            }
            
            OciMavenResolver resolver = new OciMavenResolver(registryUrl, insecure, username, password);
            
            // Store resolver reference for potential use by dependency resolution hooks
            project.getExtensions().getExtraProperties().set(spec.getName() + "_oci_resolver", resolver);
            project.getExtensions().getExtraProperties().set(spec.getName() + "_oci_cache", cacheDir);
            
            logger.info("OCI-backed Maven repository created: {} -> {}", spec.getName(), cacheDir);
            
        } catch (Exception e) {
            logger.error("Failed to create OCI-backed Maven repository", e);
            throw new RuntimeException("Failed to create OCI-backed Maven repository", e);
        }
    }
    
    /**
     * Creates a cache directory for the OCI repository.
     * 
     * @param spec OCI repository specification
     * @param project Gradle project
     * @return Path to cache directory
     */
    private static Path createCacheDirectory(OciRepositorySpec spec, Project project) throws IOException {
        // Create cache directory in the project's build directory
        Path buildDir = project.getBuildDir().toPath();
        Path ociCacheDir = buildDir.resolve("oci-cache").resolve(spec.getName());
        
        Files.createDirectories(ociCacheDir);
        
        logger.debug("Created OCI cache directory: {}", ociCacheDir);
        return ociCacheDir;
    }
    
    /**
     * Resolves a specific Maven artifact from OCI registry to the cache.
     * This method is called on-demand when Gradle needs an artifact.
     * 
     * @param project Gradle project
     * @param repositoryName OCI repository name
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @return true if artifact was successfully resolved, false otherwise
     */
    public static boolean resolveArtifactOnDemand(Project project, String repositoryName, 
                                                 String groupId, String artifactId, String version) {
        try {
            // Get the resolver and cache directory from project extensions
            String resolverKey = repositoryName + "_oci_resolver";
            String cacheKey = repositoryName + "_oci_cache";
            
            OciMavenResolver resolver = (OciMavenResolver) project.getExtensions()
                .getExtraProperties().get(resolverKey);
            Path cacheDir = (Path) project.getExtensions()
                .getExtraProperties().get(cacheKey);
            
            if (resolver == null || cacheDir == null) {
                logger.warn("OCI resolver or cache not found for repository: {}", repositoryName);
                return false;
            }
            
            // Create Maven directory structure in cache
            String groupPath = groupId.replace(".", "/");
            Path artifactDir = cacheDir.resolve(groupPath).resolve(artifactId).resolve(version);
            
            // Check if artifacts already exist in cache
            Path jarFile = artifactDir.resolve(artifactId + "-" + version + ".jar");
            Path existingPomFile = artifactDir.resolve(artifactId + "-" + version + ".pom");
            
            if (Files.exists(jarFile) && Files.exists(existingPomFile)) {
                logger.debug("Artifacts already cached: {}:{}:{}", groupId, artifactId, version);
                return true;
            }
            
            // Resolve artifacts from OCI registry
            logger.info("Resolving {}:{}:{} from OCI registry", groupId, artifactId, version);
            
            // Create temporary directory for OCI pull
            Path tempDir = Files.createTempDirectory("oci-resolve-");
            try {
                boolean resolved = resolver.resolveArtifacts(groupId, artifactId, version, tempDir);
                
                if (resolved) {
                    // Move artifacts from temp directory to cache with proper Maven names
                    Files.createDirectories(artifactDir);
                    
                    // Move and rename files to Maven conventions
                    moveArtifactFiles(tempDir, artifactDir, artifactId, version);
                    
                    // Ensure POM file exists - create minimal POM if not downloaded
                    Path pomFile = artifactDir.resolve(artifactId + "-" + version + ".pom");
                    if (!Files.exists(pomFile)) {
                        logger.info("POM not found in OCI artifacts, generating minimal POM");
                        createMinimalPom(artifactDir, groupId, artifactId, version);
                    }
                    
                    logger.info("Successfully cached artifacts: {}:{}:{}", groupId, artifactId, version);
                    return true;
                } else {
                    logger.warn("Failed to resolve artifacts from OCI: {}:{}:{}", groupId, artifactId, version);
                    return false;
                }
                
            } finally {
                // Clean up temp directory
                try {
                    Files.walk(tempDir)
                        .map(Path::toFile)
                        .forEach(file -> file.delete());
                    Files.deleteIfExists(tempDir);
                } catch (IOException e) {
                    logger.debug("Failed to clean up temp directory", e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error resolving artifact on demand: {}:{}:{}", groupId, artifactId, version, e);
            return false;
        }
    }
    
    /**
     * Moves and renames artifact files from temp directory to cache directory.
     * 
     * @param tempDir Temporary directory containing downloaded artifacts
     * @param artifactDir Target artifact directory in cache
     * @param artifactId Maven artifact ID
     * @param version Maven version
     */
    private static void moveArtifactFiles(Path tempDir, Path artifactDir, String artifactId, String version) 
            throws IOException {
        
        // Walk through temp directory and move files
        Files.walk(tempDir, 1)
            .filter(Files::isRegularFile)
            .forEach(file -> {
                try {
                    String fileName = file.getFileName().toString();
                    Path targetFile;
                    
                    // Determine target file name based on file extension and content
                    if (fileName.endsWith(".jar")) {
                        targetFile = artifactDir.resolve(artifactId + "-" + version + ".jar");
                    } else if (fileName.endsWith(".pom") || fileName.endsWith(".xml")) {
                        targetFile = artifactDir.resolve(artifactId + "-" + version + ".pom");
                    } else if (fileName.contains("sources")) {
                        targetFile = artifactDir.resolve(artifactId + "-" + version + "-sources.jar");
                    } else if (fileName.contains("javadoc")) {
                        targetFile = artifactDir.resolve(artifactId + "-" + version + "-javadoc.jar");
                    } else {
                        // Keep original name for unknown file types
                        targetFile = artifactDir.resolve(fileName);
                    }
                    
                    Files.move(file, targetFile);
                    logger.debug("Moved artifact file: {} -> {}", fileName, targetFile.getFileName());
                    
                } catch (IOException e) {
                    logger.warn("Failed to move artifact file: {}", file, e);
                }
            });
    }
    
    /**
     * Creates a minimal POM file for an artifact that doesn't have one.
     * 
     * @param artifactDir Directory where the POM should be created
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     */
    private static void createMinimalPom(Path artifactDir, String groupId, String artifactId, String version) 
            throws IOException {
        
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                           "<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\" " +
                           "xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                           "  <modelVersion>4.0.0</modelVersion>\n" +
                           "  <groupId>" + groupId + "</groupId>\n" +
                           "  <artifactId>" + artifactId + "</artifactId>\n" +
                           "  <version>" + version + "</version>\n" +
                           "  <packaging>jar</packaging>\n" +
                           "  <description>Artifact resolved from OCI registry</description>\n" +
                           "  \n" +
                           "  <!-- Generated by Maven OCI Publish Plugin -->\n" +
                           "  <properties>\n" +
                           "    <maven.compiler.source>17</maven.compiler.source>\n" +
                           "    <maven.compiler.target>17</maven.compiler.target>\n" +
                           "  </properties>\n" +
                           "</project>\n";
        
        Path pomFile = artifactDir.resolve(artifactId + "-" + version + ".pom");
        Files.write(pomFile, pomContent.getBytes());
        
        logger.info("Created minimal POM file: {}", pomFile);
    }
}