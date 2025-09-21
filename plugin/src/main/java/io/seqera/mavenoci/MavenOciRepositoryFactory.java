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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Factory for creating Maven repositories that can resolve artifacts from OCI registries.
 * 
 * <p>This factory creates a hybrid approach where:</p>
 * <ul>
 *   <li>Artifacts are pulled from OCI registries on-demand using the ORAS protocol</li>
 *   <li>Downloaded artifacts are cached locally in standard Maven repository structure</li>
 *   <li>The cache persists across builds for performance</li>
 *   <li>Integration is transparent to Gradle's dependency resolution system</li>
 * </ul>
 * 
 * <h3>How it works</h3>
 * <p>When a dependency resolution occurs:</p>
 * <ol>
 *   <li>Gradle hooks trigger before dependency resolution</li>
 *   <li>Each dependency is checked against the local cache</li>
 *   <li>If missing, the factory attempts to resolve from the OCI registry</li>
 *   <li>Maven coordinates are mapped to OCI references (e.g., {@code com.example:artifact:1.0} → {@code registry.com/com-example/artifact:1.0})</li>
 *   <li>ORAS Java SDK pulls the artifact from the OCI registry</li>
 *   <li>Files are organized in proper Maven directory structure in the cache</li>
 *   <li>Gradle continues normal resolution using the cached files</li>
 * </ol>
 * 
 * <h3>Cache Location</h3>
 * <p>Artifacts are cached in {@code PROJECT_ROOT/.gradle/oci-cache/REPOSITORY_NAME/} which:</p>
 * <ul>
 *   <li>Survives {@code gradle clean} operations</li>
 *   <li>Follows standard Maven repository layout: {@code groupId/artifactId/version/}</li>
 *   <li>Contains JAR, POM, sources, and javadoc files as appropriate</li>
 * </ul>
 * 
 * <h3>Error Handling</h3>
 * <p>The factory is designed to fail gracefully:</p>
 * <ul>
 *   <li>If an artifact doesn't exist in OCI, resolution continues with other repositories</li>
 *   <li>Network failures are logged but don't break the build</li>
 *   <li>Invalid OCI registries fall back to standard Maven resolution</li>
 * </ul>
 * 
 * @see MavenOciResolver
 * @see MavenOciGroupSanitizer
 * @since 1.0
 */
public class MavenOciRepositoryFactory {
    
    private static final Logger logger = Logging.getLogger(MavenOciRepositoryFactory.class);
    
    /**
     * Creates a Maven repository that resolves artifacts from an OCI registry.
     * 
     * @param spec OCI repository specification
     * @param repository Maven repository to configure
     * @param project Gradle project
     */
    public static void createOciMavenRepository(MavenOciRepositorySpec spec, MavenArtifactRepository repository, Project project) {
        try {
            logger.debug("Creating OCI-backed Maven repository: {}", spec.getName());
            
            String registryUrl = spec.getUrl().getOrElse("").toString();
            boolean insecure = spec.getInsecure().getOrElse(false);
            
            // Create cache directory for this OCI repository
            Path cacheDir = createCacheDirectory(spec, project);
            
            // Configure repository to use cache directory as its URL  
            // Name is already set by caller, don't change it here
            repository.setUrl(cacheDir.toUri());
            
            if (insecure) {
                repository.setAllowInsecureProtocol(true);
            }
            
            // Install a simple resolution hook that silently handles missing artifacts
            installSilentOciResolutionHook(project, spec, cacheDir);
            
            logger.debug("Configured OCI-backed repository: {} -> cache at {}", spec.getName(), cacheDir);
            
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
    private static Path createCacheDirectory(MavenOciRepositorySpec spec, Project project) throws IOException {
        // Create cache directory in the project's .gradle directory (not affected by clean task)
        Path projectDir = project.getRootDir().toPath();
        Path ociCacheDir = projectDir.resolve(".gradle").resolve("oci-cache").resolve(spec.getName());
        
        Files.createDirectories(ociCacheDir);
        
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
            
            MavenOciResolver resolver = (MavenOciResolver) project.getExtensions()
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
            logger.debug("Resolving {}:{}:{} from OCI registry", groupId, artifactId, version);
            
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
                        logger.debug("POM not found in OCI artifacts, generating minimal POM");
                        createMinimalPom(artifactDir, groupId, artifactId, version);
                    }
                    
                    logger.debug("Successfully cached artifacts: {}:{}:{}", groupId, artifactId, version);
                    return true;
                } else {
                    logger.debug("Failed to resolve artifacts from OCI: {}:{}:{}", groupId, artifactId, version);
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
                    
                    // Determine target file name based on file content and extension
                    // Check for specific JAR types first before general .jar check
                    if (fileName.contains("sources") && fileName.endsWith(".jar")) {
                        targetFile = artifactDir.resolve(artifactId + "-" + version + "-sources.jar");
                    } else if (fileName.contains("javadoc") && fileName.endsWith(".jar")) {
                        targetFile = artifactDir.resolve(artifactId + "-" + version + "-javadoc.jar");
                    } else if (fileName.endsWith(".jar")) {
                        targetFile = artifactDir.resolve(artifactId + "-" + version + ".jar");
                    } else if (fileName.endsWith(".pom") || fileName.endsWith(".xml")) {
                        targetFile = artifactDir.resolve(artifactId + "-" + version + ".pom");
                    } else {
                        // Keep original name for unknown file types
                        targetFile = artifactDir.resolve(fileName);
                    }
                    
                    // Move file, replacing if exists
                    Files.move(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
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
        
        logger.debug("Created minimal POM file: {}", pomFile);
    }
    
    /**
     * Installs an artifact interceptor that attempts to resolve missing artifacts from OCI.
     * This hooks into the dependency resolution chain directly.
     */
    private static void installSilentOciResolutionHook(Project project, MavenOciRepositorySpec spec, Path cacheDir) {
        logger.debug("Installing OCI resolution interceptor for repository: {}", spec.getName());
        
        String registryUrl = spec.getUrl().getOrElse("").toString();
        boolean insecure = spec.getInsecure().getOrElse(false);
        
        MavenOciResolver resolver = new MavenOciResolver(registryUrl, insecure, null, null);
        
        // Hook into all configurations to intercept dependency resolution
        project.getConfigurations().configureEach(configuration -> {
            if (configuration.isCanBeResolved()) {
                configuration.getIncoming().beforeResolve(resolvableConfiguration -> {
                    logger.debug("OCI resolution hook triggered for configuration: {}", configuration.getName());
                    
                    // Try to pre-resolve OCI artifacts before normal resolution
                    configuration.getAllDependencies().forEach(dependency -> {
                        String group = dependency.getGroup();
                        String name = dependency.getName();
                        String version = dependency.getVersion();
                        
                        if (group != null && name != null && version != null) {
                            // Try to resolve from OCI and cache it
                            ensureArtifactInCache(resolver, cacheDir, group, name, version, spec.getName());
                        }
                    });
                });
            }
        });
    }
    
    
    /**
     * Ensures an artifact is available in the cache, downloading from OCI if needed.
     */
    private static void ensureArtifactInCache(MavenOciResolver resolver, Path cacheDir,
                                              String groupId, String artifactId, String version,
                                              String repositoryName) {
        try {
            // Check if artifacts already exist in cache
            String groupPath = groupId.replace(".", "/");
            Path artifactDir = cacheDir.resolve(groupPath).resolve(artifactId).resolve(version);
            Path jarFile = artifactDir.resolve(artifactId + "-" + version + ".jar");
            Path pomFile = artifactDir.resolve(artifactId + "-" + version + ".pom");
            
            if (Files.exists(jarFile) && Files.exists(pomFile)) {
                logger.debug("Artifacts already cached: {}:{}:{}", groupId, artifactId, version);
                return;
            }
            
            logger.debug("Attempting to resolve {}:{}:{} from OCI registry: {}", groupId, artifactId, version, repositoryName);
            
            try {
                // Create temp directory for OCI pull
                Path tempDir = Files.createTempDirectory("oci-resolve-");
                try {
                    boolean resolved = resolver.resolveArtifacts(groupId, artifactId, version, tempDir);
                    
                    if (resolved) {
                        // Move artifacts from temp directory to cache with proper Maven names
                        Files.createDirectories(artifactDir);
                        moveArtifactFiles(tempDir, artifactDir, artifactId, version);
                        
                        // Ensure POM file exists
                        if (!Files.exists(pomFile)) {
                            logger.debug("POM not found in OCI artifacts, generating minimal POM");
                            createMinimalPom(artifactDir, groupId, artifactId, version);
                        }
                        
                        logger.info("Successfully resolved {}:{}:{} from OCI registry to cache", groupId, artifactId, version);
                    } else {
                        logger.debug("Artifact not found in OCI registry: {}:{}:{} (will try other repositories)", groupId, artifactId, version);
                    }
                } finally {
                    // Clean up temp directory
                    try {
                        Files.walk(tempDir)
                            .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    logger.debug("Failed to delete temp file: {}", path, e);
                                }
                            });
                    } catch (IOException e) {
                        logger.debug("Failed to clean up temp directory", e);
                    }
                }
                
            } catch (Exception e) {
                // Silently ignore all OCI resolution errors - this allows the normal repository chain to continue
                logger.debug("OCI resolution failed for {}:{}:{} from repository '{}' (continuing with normal resolution): {}", 
                           groupId, artifactId, version, repositoryName, e.getMessage());
            }
            
        } catch (Exception e) {
            logger.debug("Error ensuring artifact in cache: {}:{}:{} from repository '{}': {}", 
                       groupId, artifactId, version, repositoryName, e.getMessage(), e);
        }
    }
    
}
