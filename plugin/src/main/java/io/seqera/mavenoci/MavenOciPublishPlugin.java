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
import java.io.FileInputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.GradleVersion;

/**
 * A Gradle plugin for publishing and consuming Maven artifacts to/from OCI registries.
 * 
 * <p>This plugin extends Gradle's standard Maven publishing capabilities to support OCI (Open Container Initiative)
 * registries as publishing destinations. It uses the ORAS (OCI Registry as Storage) protocol to store Maven 
 * artifacts as OCI artifacts.</p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><strong>Standard Integration</strong>: Uses Gradle's standard {@code publishing.repositories} DSL</li>
 *   <li><strong>OCI Registry Support</strong>: Publish to any OCI-compliant registry</li>
 *   <li><strong>Coordinate Mapping</strong>: Intelligent mapping between Maven coordinates and OCI references</li>
 *   <li><strong>Authentication</strong>: Support for registry authentication</li>
 * </ul>
 * 
 * <h2>Publishing Configuration</h2>
 * <p>Configure OCI publishing using the standard publishing DSL:</p>
 * <pre>{@code
 * publishing {
 *     publications {
 *         maven(MavenPublication) {
 *             from components.java
 *         }
 *     }
 *     
 *     repositories {
 *         maven {
 *             name = 'central'
 *             url = 'https://repo1.maven.org/maven2/'
 *         }
 *         
 *         mavenOci {
 *             name = 'docker'
 *             url = 'https://registry-1.docker.io'
 *             namespace = 'maven'
 *             credentials {
 *                 username = "user"
 *                 password = "pass"
 *             }
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h2>Plugin Tasks</h2>
 * <p>The plugin creates the following tasks:</p>
 * <ul>
 *   <li>{@code publishToOciRegistries} - Publishes all publications to all OCI repositories</li>
 *   <li>{@code publish<Publication>To<Repository>Repository} - Publishes specific publication to specific repository</li>
 * </ul>
 * 
 * <h2>Requirements</h2>
 * <ul>
 *   <li>Gradle 6.0 or later</li>
 *   <li>Java 17 or later</li>
 *   <li>Network access to OCI registries</li>
 * </ul>
 * 
 * @see MavenOciArtifactRepository
 * @since 1.0
 */
public class MavenOciPublishPlugin implements Plugin<Project> {
    
    private static final Logger logger = Logging.getLogger(MavenOciPublishPlugin.class);
    
    public static final String PUBLISH_TASK_GROUP = "publishing";
    
    @Override
    public void apply(Project project) {
        // Check minimum Gradle version
        if (GradleVersion.current().compareTo(GradleVersion.version("6.0")) < 0) {
            throw new IllegalStateException("This plugin requires Gradle 6.0 or later");
        }
        
        logger.debug("Applying Maven OCI plugin to project: {}", project.getName());
        
        // Store OCI repositories for separate processing before maven-publish sees them
        java.util.List<MavenOciArtifactRepository> ociRepositories = new java.util.ArrayList<>();
        project.getExtensions().getExtraProperties().set("ociRepositoriesForProcessing", ociRepositories);
        
        // Apply the maven-publish plugin to leverage its components
        project.getPluginManager().apply("maven-publish");
        
        // CRITICAL: Apply ordering to ensure we run AFTER maven-publish configures its tasks
        project.getPluginManager().withPlugin("maven-publish", appliedPlugin -> {
            logger.debug("Maven-publish plugin detected, setting up OCI repository filtering");
            // We'll filter OCI repositories after maven-publish has done its initial setup
        });
        
        // Add OCI factory method immediately when plugin is applied
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        extendPublishingRepositories(publishing, project);
        
        // Also add OCI method to main project repositories for dependency resolution
        extendMainRepositories(project);
        
        // Intercept OCI repositories during creation to capture them for our processing
        interceptOciRepositories(publishing, project, ociRepositories);
        
        // Create OCI publishing tasks after project evaluation
        project.afterEvaluate(p -> createOciPublishingTasks(p, ociRepositories));
        
        // Add a lifecycle task for OCI publishing
        project.getTasks().register("publishToOciRegistries", task -> {
            task.setDescription("Publishes all publications to all OCI repositories");
            task.setGroup(PUBLISH_TASK_GROUP);
        });
    }
    
    /**
     * Intercept OCI repositories during creation to capture them for our processing
     * NOTE: Since we're not adding OCI repositories to publishing.repositories anymore,
     * this method is no longer needed - repositories are added directly to our list in mavenOci methods
     */
    private void interceptOciRepositories(PublishingExtension publishing, Project project, java.util.List<MavenOciArtifactRepository> ociRepositories) {
        // No-op since OCI repositories are now managed separately from maven-publish
        logger.debug("OCI repository interception disabled - repositories managed separately from maven-publish");
    }
    
    
    /**
     * Option E: Add mavenOci method via programmatic factory approach
     */
    private void extendPublishingRepositories(PublishingExtension publishing, Project project) {
        logger.debug("Adding mavenOci factory method to publishing.repositories for project: {}", project.getName());
        
        // Create a simple oci factory using Groovy's extensibility 
        addOciRepositoryFactory(publishing.getRepositories(), project);
        logger.info("Successfully added mavenOci method support to publishing.repositories");
    }
    
    /**
     * Extend main project repositories with mavenOci method for dependency resolution
     */
    private void extendMainRepositories(Project project) {
        logger.debug("Adding mavenOci factory method to main repositories for project: {}", project.getName());
        
        // Get the main project repositories
        org.gradle.api.artifacts.dsl.RepositoryHandler repositories = project.getRepositories();
        addOciRepositoryFactory(repositories, project);
        logger.info("Successfully added mavenOci method support to main repositories");
    }
    
    /**
     * Add mavenOci method using Extension objects (Configuration Cache compatible)
     */
    private void addOciRepositoryFactory(org.gradle.api.artifacts.dsl.RepositoryHandler repositories, Project project) {
        try {
            // Use Extension object approach for Configuration Cache compatibility
            addExtensionBasedOciMethod(repositories, project);
            
            logger.info("Successfully added OCI repository extension method");
            
        } catch (Exception e) {
            logger.error("Failed to add OCI repository extension method: " + e.getMessage(), e);
            // Fallback to factory approach
            project.getExtensions().getExtraProperties().set("ociRepositoryFactory", new OciRepositoryFactory(repositories, project));
        }
    }
    
    /**
     * Detect if the given repositories container is for publishing or dependency resolution
     */
    private boolean detectIsPublishingRepo(Project project, org.gradle.api.artifacts.dsl.RepositoryHandler repositories) {
        try {
            PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
            return (repositories == publishing.getRepositories());
        } catch (Exception e) {
            // If we can't determine, assume it's dependency resolution
            logger.debug("Could not determine repository type, assuming dependency resolution: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Add mavenOci method using Extension objects for Configuration Cache compatibility
     */
    private void addExtensionBasedOciMethod(org.gradle.api.artifacts.dsl.RepositoryHandler repositories, Project project) {
        try {
            logger.info("Adding mavenOci extension to repositories of type: {}", repositories.getClass().getName());
            
            // Create extension instance
            MavenOciRepositoryExtension extension = new MavenOciRepositoryExtension(repositories, project);
            
            // Store extension in project's extensions for lifecycle management
            String extensionName = "mavenOciExtension_" + System.identityHashCode(repositories);
            project.getExtensions().add(extensionName, extension);
            
            // Add mavenOci method using metaprogramming
            addMavenOciMethodToRepositories(repositories, extension);
            
            logger.info("Successfully added mavenOci extension to repositories");
        } catch (Exception e) {
            logger.error("Failed to add extension-based oci method: " + e.getMessage(), e);
            e.printStackTrace();
            
            // Fallback to factory approach
            project.getExtensions().getExtraProperties().set("ociRepositoryFactory", new OciRepositoryFactory(repositories, project));
        }
    }
    
    /**
     * Add mavenOci method to RepositoryHandler using ExpandoMetaClass (Configuration Cache compatible)
     */
    private void addMavenOciMethodToRepositories(org.gradle.api.artifacts.dsl.RepositoryHandler repositories, MavenOciRepositoryExtension extension) {
        try {
            // Use ExpandoMetaClass to add the method dynamically
            groovy.lang.ExpandoMetaClass emc = new groovy.lang.ExpandoMetaClass(repositories.getClass(), false);
            
            // Create closure that delegates to extension
            groovy.lang.Closure<?> mavenOciClosure = new groovy.lang.Closure<Object>(this) {
                public Object doCall(Object[] args) {
                    if (args.length == 0) {
                        return extension.mavenOci((groovy.lang.Closure<?>) null);
                    } else if (args.length == 1 && args[0] instanceof groovy.lang.Closure) {
                        return extension.mavenOci((groovy.lang.Closure<?>) args[0]);
                    } else if (args.length == 2 && args[0] instanceof String && args[1] instanceof groovy.lang.Closure) {
                        return extension.mavenOci((String) args[0], (groovy.lang.Closure<?>) args[1]);
                    } else {
                        throw new IllegalArgumentException("Invalid arguments for mavenOci method");
                    }
                }
            };
            
            // Register the method in ExpandoMetaClass
            emc.registerInstanceMethod("mavenOci", mavenOciClosure);
            
            // Initialize the ExpandoMetaClass
            emc.initialize();
            
            // Apply the ExpandoMetaClass to the repositories instance using reflection
            java.lang.reflect.Method setMetaClass = repositories.getClass().getMethod("setMetaClass", groovy.lang.MetaClass.class);
            setMetaClass.invoke(repositories, emc);
            
            logger.debug("Successfully added mavenOci method to repositories via ExpandoMetaClass");
        } catch (Exception e) {
            logger.warn("Could not add mavenOci method via ExpandoMetaClass: {}", e.getMessage());
            e.printStackTrace();
            // Method will still be available through extension, just not as direct method call
        }
    }
    
    
    
    /**
     * Simple factory class for OCI repositories
     */
    public static class OciRepositoryFactory {
        private final org.gradle.api.artifacts.dsl.RepositoryHandler repositories;
        private final Project project;
        
        public OciRepositoryFactory(org.gradle.api.artifacts.dsl.RepositoryHandler repositories, Project project) {
            this.repositories = repositories;
            this.project = project;
        }
        
        public MavenOciArtifactRepository call(String name) {
            return call(name, null);
        }
        
        public MavenOciArtifactRepository call(String name, groovy.lang.Closure<?> configureAction) {
            logger.info("Creating OCI repository '{}' via factory", name);
            
            // Create OCI Maven repository (no proxy needed since not added to maven-publish)
            MavenOciArtifactRepository ociRepo = project.getObjects().newInstance(MavenOciArtifactRepository.class, name);
            
            // Configure the repository if configuration block provided
            if (configureAction != null) {
                org.gradle.util.internal.ConfigureUtil.configure(configureAction, ociRepo);
            }
            
            // Store in separate list to avoid maven-publish conflicts, but add back for test visibility
            @SuppressWarnings("unchecked")
            java.util.List<MavenOciArtifactRepository> ociRepositories =
                (java.util.List<MavenOciArtifactRepository>) project.getExtensions().getExtraProperties().get("ociRepositoriesForProcessing");
            if (ociRepositories != null && !ociRepositories.contains(ociRepo)) {
                ociRepositories.add(ociRepo);
                logger.debug("Added OCI repository '{}' to separate processing list", name);
            }
            
            // IMPORTANT: Always add to repositories for test visibility
            // For the nested factory, repositories should be the correct target container
            try {
                repositories.add(ociRepo);
                logger.debug("Added OCI repository '{}' to target repositories for visibility", name);
            } catch (Exception e) {
                logger.debug("Could not add OCI repository '{}' to target repositories: {}", name, e.getMessage());
            }
            
            logger.info("Successfully created and added OCI repository '{}' with URL: {}", 
                       name, ociRepo.getUrl() != null ? ociRepo.getUrl().toString() : "not set");
            
            return ociRepo;
        }
    }
    
    
    /**
     * Create publishing tasks for OCI repositories
     */
    private void createOciPublishingTasks(Project project, java.util.List<MavenOciArtifactRepository> ociRepositories) {
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        
        if (ociRepositories != null && !ociRepositories.isEmpty()) {
            logger.debug("Creating OCI publishing tasks for {} repositories", ociRepositories.size());
            
            // Create tasks for each OCI repository and publication combination
            for (MavenOciArtifactRepository ociRepository : ociRepositories) {
                publishing.getPublications().withType(MavenPublication.class).all(publication -> {
                    createOciPublishTask(project, publication, ociRepository);
                });
            }
        } else {
            logger.debug("No OCI repositories found for task creation");
        }
    }
    
    /**
     * Create a publishing task for a specific publication-repository combination
     */
    private void createOciPublishTask(Project project, MavenPublication publication, MavenOciArtifactRepository repository) {
        String taskName = "publish" + capitalize(publication.getName()) + "PublicationTo" + capitalize(repository.getName()) + "Repository";
        
        // Check if task already exists (prevent duplicate registration in tests)
        if (project.getTasks().findByName(taskName) != null) {
            logger.debug("Task '{}' already exists, skipping registration", taskName);
            return;
        }
        
        TaskProvider<PublishToOciRepositoryTask> publishTask = project.getTasks().register(taskName, PublishToOciRepositoryTask.class, task -> {
            task.setDescription("Publishes Maven publication '" + publication.getName() + "' to OCI repository '" + repository.getName() + "'");
            task.setGroup(PUBLISH_TASK_GROUP);
            
            // Configure task inputs (configuration cache compatible)
            task.getPublicationName().set(publication.getName());
            task.getRepositoryName().set(repository.getName());
            task.getRegistryUrl().set(repository.getUrl().toString());
            task.getInsecure().set(repository.getInsecure());
            task.getExecutionId().set(System.currentTimeMillis() + "-" + publication.getName() + "-" + repository.getName());
            
            // Configure namespace if provided
            if (repository.getNamespace().isPresent()) {
                task.getNamespace().set(repository.getNamespace());
            }
            
            // Configure overwrite policy
            task.getOverwritePolicy().set(repository.getOverwritePolicy());
            
            // Configure Maven coordinates
            task.getGroupId().set(publication.getGroupId());
            task.getArtifactId().set(publication.getArtifactId());
            task.getVersion().set(publication.getVersion());
            task.getTag().set(publication.getVersion()); // Use version as tag
            
            // Configure credentials if available
            if (repository.hasCredentials()) {
                PasswordCredentials creds = repository.getCredentials();
                task.getUsername().set(creds.getUsername());
                task.getPassword().set(creds.getPassword());
            }
            
            // Configure artifacts from the Maven publication
            configureArtifactsFromPublication(publication, project, task);
            
            // Ensure the task depends on the build tasks that create the artifacts
            try {
                task.dependsOn(project.getTasks().named("jar"));
            } catch (Exception e) {
                // jar task may not exist, that's ok
            }
            
            // Also depend on sources and javadoc jar tasks if they exist
            try {
                task.dependsOn(project.getTasks().named("sourcesJar"));
            } catch (Exception e) {
                // sourcesJar task may not exist, that's ok
            }
            
            try {
                task.dependsOn(project.getTasks().named("javadocJar"));
            } catch (Exception e) {
                // javadocJar task may not exist, that's ok
            }
            
            // Depend on POM generation task to ensure POM file exists
            String pomTaskName = "generatePomFileFor" + capitalize(publication.getName()) + "Publication";
            try {
                task.dependsOn(project.getTasks().named(pomTaskName));
                logger.debug("Added dependency on POM generation task: {}", pomTaskName);
            } catch (Exception e) {
                logger.debug("POM generation task '{}' may not exist yet, that's ok", pomTaskName);
            }
        });
        
        // Wire the task to the lifecycle task
        project.getTasks().named("publishToOciRegistries").configure(lifecycleTask -> 
            lifecycleTask.dependsOn(publishTask)
        );
    }
    
    /**
     * Configure artifacts from a Maven publication
     */
    private void configureArtifactsFromPublication(MavenPublication publication, Project project, PublishToOciRepositoryTask task) {
        // Configure artifacts lazily using providers  
        task.getArtifacts().from(project.provider(() -> {
            List<File> allArtifacts = new ArrayList<>();
            
            // Add all publication artifacts (JAR, sources, javadoc, etc.)
            publication.getArtifacts()
                .stream()
                .filter(artifact -> artifact.getFile() != null)
                .map(artifact -> artifact.getFile())
                .forEach(allArtifacts::add);
                
            // Add the generated POM file - it follows a predictable naming pattern
            String pomFileName = publication.getArtifactId() + "-" + publication.getVersion() + ".pom";
            
            // Try common locations for the generated POM file
            File[] possiblePomLocations = {
                // Standard Gradle publishing location
                new File(project.getBuildDir(), "publications/" + publication.getName() + "/pom-default.xml"),
                // Alternative Maven-style naming in build outputs  
                new File(project.getBuildDir(), "libs/" + pomFileName),
                // Generated in build/publications/maven directory
                new File(project.getBuildDir(), "publications/maven/pom-default.xml")
            };
            
            File pomFile = null;
            for (File candidate : possiblePomLocations) {
                if (candidate.exists()) {
                    pomFile = candidate;
                    break;
                }
            }
            
            if (pomFile != null) {
                allArtifacts.add(pomFile);
                project.getLogger().info("Added POM file to OCI artifacts: {} ({})", pomFile.getName(), pomFile.getPath());
            } else {
                project.getLogger().warn("Could not find generated POM file for publication '{}'. Tried locations:", publication.getName());
                for (File candidate : possiblePomLocations) {
                    project.getLogger().warn("  - {}", candidate.getPath());
                }
            }
            
            // Generate checksums for all artifacts (including POM) that exist
            List<File> allArtifactsWithChecksums = new ArrayList<>(allArtifacts);
            for (File artifact : new ArrayList<>(allArtifacts)) {
                // Only generate checksums for files that actually exist
                if (artifact.exists() && artifact.isFile() && artifact.length() > 0) {
                    try {
                        // Generate SHA1 checksum
                        File sha1File = generateChecksum(artifact, "SHA-1", ".sha1", project);
                        if (sha1File != null) {
                            allArtifactsWithChecksums.add(sha1File);
                            project.getLogger().debug("Generated SHA1 checksum: {}", sha1File.getName());
                        }
                        
                        // Generate MD5 checksum  
                        File md5File = generateChecksum(artifact, "MD5", ".md5", project);
                        if (md5File != null) {
                            allArtifactsWithChecksums.add(md5File);
                            project.getLogger().debug("Generated MD5 checksum: {}", md5File.getName());
                        }
                    } catch (Exception e) {
                        project.getLogger().warn("Failed to generate checksums for {}: {}", artifact.getName(), e.getMessage());
                    }
                } else {
                    project.getLogger().debug("Skipping checksum generation for non-existent or empty artifact: {}", artifact.getName());
                }
            }
            
            return allArtifactsWithChecksums;
        }));
    }
    
    /**
     * Generate checksum file for an artifact
     */
    private File generateChecksum(File artifact, String algorithm, String extension, Project project) {
        if (!artifact.exists() || artifact.length() == 0) {
            return null;
        }
        
        try {
            // Calculate checksum
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (FileInputStream fis = new FileInputStream(artifact)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest.digest()) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // Create checksum file
            File checksumFile = new File(artifact.getParent(), artifact.getName() + extension);
            Files.write(checksumFile.toPath(), hexString.toString().getBytes());
            
            return checksumFile;
            
        } catch (Exception e) {
            project.getLogger().warn("Failed to generate {} checksum for {}: {}", algorithm, artifact.getName(), e.getMessage());
            return null;
        }
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
