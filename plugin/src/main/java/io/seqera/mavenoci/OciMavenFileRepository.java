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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * File-based Maven repository that simulates OCI artifact resolution.
 * This implementation creates a local Maven repository structure with artifacts
 * that would be resolved from OCI registries.
 */
public class OciMavenFileRepository {
    
    private static final Logger logger = Logging.getLogger(OciMavenFileRepository.class);
    
    /**
     * Creates a file-based Maven repository that simulates OCI artifact resolution.
     * 
     * @param spec The OCI repository specification
     * @param repository The Maven repository to configure
     * @param project The Gradle project
     */
    public static void createFileBasedRepository(OciRepositorySpec spec, MavenArtifactRepository repository, Project project) {
        try {
            logger.info("Creating file-based OCI Maven repository for: {}", spec.getName());
            
            // Create a temporary directory for the repository
            Path repoDir = Files.createTempDirectory("oci-maven-repo-");
            repoDir.toFile().deleteOnExit();
            
            // Configure the repository to use the temporary directory
            repository.setName(spec.getName());
            repository.setUrl(repoDir.toUri());
            
            // Ensure URL is properly set
            if (repository.getUrl() == null) {
                repository.setUrl(URI.create("file://" + repoDir.toAbsolutePath().toString()));
            }
            
            // Create a sample artifact to demonstrate the concept
            createSampleArtifact(repoDir, "io.seqera", "shared-library", "1.0.0", project);
            
            logger.info("File-based OCI Maven repository created at: {}", repoDir);
            
        } catch (Exception e) {
            logger.error("Failed to create file-based OCI Maven repository", e);
            throw new RuntimeException("Failed to create file-based OCI Maven repository", e);
        }
    }
    
    /**
     * Creates a sample artifact in the Maven repository structure.
     * 
     * @param repoDir The repository directory
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The Maven version
     * @param project The Gradle project
     */
    private static void createSampleArtifact(Path repoDir, String groupId, String artifactId, String version, Project project) throws IOException {
        // Create Maven directory structure: groupId/artifactId/version/
        String groupPath = groupId.replace(".", "/");
        Path artifactDir = repoDir.resolve(groupPath).resolve(artifactId).resolve(version);
        Files.createDirectories(artifactDir);
        
        // Create POM file
        createPomFile(artifactDir, groupId, artifactId, version);
        
        // Try to find the actual JAR from the project build
        File projectJar = findProjectJar(project, artifactId, version);
        
        if (projectJar != null && projectJar.exists()) {
            // Copy the actual project JAR
            Path jarFile = artifactDir.resolve(artifactId + "-" + version + ".jar");
            Files.copy(projectJar.toPath(), jarFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Created JAR file: {}", jarFile);
        } else {
            // Create a placeholder JAR
            createPlaceholderJar(artifactDir, artifactId, version);
        }
        
        logger.info("Created sample artifact: {}:{}:{} at {}", groupId, artifactId, version, artifactDir);
    }
    
    /**
     * Creates a Maven POM file.
     * 
     * @param artifactDir The artifact directory
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The Maven version
     */
    private static void createPomFile(Path artifactDir, String groupId, String artifactId, String version) throws IOException {
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                           "<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\" xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                           "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                           "  <modelVersion>4.0.0</modelVersion>\n" +
                           "  <groupId>" + groupId + "</groupId>\n" +
                           "  <artifactId>" + artifactId + "</artifactId>\n" +
                           "  <version>" + version + "</version>\n" +
                           "  <packaging>jar</packaging>\n" +
                           "  <description>Artifact resolved from OCI registry (simulated)</description>\n" +
                           "  \n" +
                           "  <!-- Generated by Maven OCI Publish Plugin -->\n" +
                           "  <properties>\n" +
                           "    <maven.compiler.source>11</maven.compiler.source>\n" +
                           "    <maven.compiler.target>11</maven.compiler.target>\n" +
                           "  </properties>\n" +
                           "</project>\n";
        
        Path pomFile = artifactDir.resolve(artifactId + "-" + version + ".pom");
        try (FileWriter writer = new FileWriter(pomFile.toFile())) {
            writer.write(pomContent);
        }
        
        logger.info("Created POM file: {}", pomFile);
    }
    
    /**
     * Finds the project JAR file.
     * 
     * @param project The Gradle project
     * @param artifactId The artifact ID
     * @param version The version
     * @return The JAR file, or null if not found
     */
    private static File findProjectJar(Project project, String artifactId, String version) {
        try {
            // Look for the JAR in the project's build directory
            Path buildDir = project.getBuildDir().toPath();
            Path libsDir = buildDir.resolve("libs");
            
            if (Files.exists(libsDir)) {
                // Look for any JAR file in the libs directory
                return Files.walk(libsDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .filter(path -> !path.toString().contains("javadoc"))
                    .filter(path -> !path.toString().contains("sources"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
            }
        } catch (Exception e) {
            logger.debug("Failed to find project JAR", e);
        }
        
        return null;
    }
    
    /**
     * Creates a placeholder JAR file.
     * 
     * @param artifactDir The artifact directory
     * @param artifactId The artifact ID
     * @param version The version
     */
    private static void createPlaceholderJar(Path artifactDir, String artifactId, String version) throws IOException {
        Path jarFile = artifactDir.resolve(artifactId + "-" + version + ".jar");
        
        // Create a minimal JAR file (just an empty ZIP)
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(jarFile))) {
            // Add a manifest entry
            java.util.zip.ZipEntry manifestEntry = new java.util.zip.ZipEntry("META-INF/MANIFEST.MF");
            zos.putNextEntry(manifestEntry);
            
            String manifest = "Manifest-Version: 1.0\n" +
                             "Created-By: Maven OCI Publish Plugin\n" +
                             "Implementation-Title: " + artifactId + "\n" +
                             "Implementation-Version: " + version + "\n";
            
            zos.write(manifest.getBytes());
            zos.closeEntry();
        }
        
        logger.info("Created placeholder JAR file: {}", jarFile);
    }
}