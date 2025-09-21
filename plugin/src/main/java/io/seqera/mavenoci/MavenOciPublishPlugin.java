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
 *         oci('docker') {
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
     * this method is no longer needed - repositories are added directly to our list in oci() methods
     */
    private void interceptOciRepositories(PublishingExtension publishing, Project project, java.util.List<MavenOciArtifactRepository> ociRepositories) {
        // No-op since OCI repositories are now managed separately from maven-publish
        logger.debug("OCI repository interception disabled - repositories managed separately from maven-publish");
    }
    
    
    /**
     * Option E: Add oci() method via programmatic factory approach
     */
    private void extendPublishingRepositories(PublishingExtension publishing, Project project) {
        logger.debug("Adding oci() factory method to publishing.repositories for project: {}", project.getName());
        
        // Create a simple oci factory using Groovy's extensibility 
        addOciRepositoryFactory(publishing.getRepositories(), project);
        logger.info("Successfully added oci() method support to publishing.repositories");
    }
    
    /**
     * Extend main project repositories with oci() method for dependency resolution
     */
    private void extendMainRepositories(Project project) {
        logger.debug("Adding oci() factory method to main repositories for project: {}", project.getName());
        
        // Get the main project repositories
        org.gradle.api.artifacts.dsl.RepositoryHandler repositories = project.getRepositories();
        addOciRepositoryFactory(repositories, project);
        logger.info("Successfully added oci() method support to main repositories");
    }
    
    /**
     * Add oci() method directly to repositories using advanced Groovy approach
     */
    private void addOciRepositoryFactory(org.gradle.api.artifacts.dsl.RepositoryHandler repositories, Project project) {
        try {
            // Method 1: Create factory for backward compatibility
            project.getExtensions().getExtraProperties().set("ociRepositoryFactory", new OciRepositoryFactory(repositories, project));
            
            // Method 2: Try to add oci() method directly to repositories using extension properties
            addDirectOciMethod(repositories, project);
            
            logger.info("Successfully added OCI repository factory and direct method");
            
        } catch (Exception e) {
            logger.error("Failed to add OCI repository methods: " + e.getMessage(), e);
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
     * Add oci() method directly to RepositoryHandler using ExpandoMetaClass approach
     */
    private void addDirectOciMethod(org.gradle.api.artifacts.dsl.RepositoryHandler repositories, Project project) {
        try {
            logger.info("Attempting to add oci() method to repositories of type: {}", repositories.getClass().getName());
            
            // Use reflection to access and manipulate the MetaClass
            java.lang.reflect.Method getMetaClass = repositories.getClass().getMethod("getMetaClass");
            Object metaClassObj = getMetaClass.invoke(repositories);
            
            // Get the MetaClass for the repositories object
            groovy.lang.ExpandoMetaClass emc = new groovy.lang.ExpandoMetaClass(repositories.getClass(), false);
            
            // Detect whether this is for publishing or dependency resolution
            // Check if this is the publishing repositories container
            final boolean isPublishingRepo = detectIsPublishingRepo(project, repositories);
            
            // Create the oci method implementation
            groovy.lang.Closure<?> ociMethod = new groovy.lang.Closure<Object>(this) {
                public Object doCall(Object[] args) {
                    if (args.length == 0) {
                        throw new IllegalArgumentException("oci() method requires at least a name parameter");
                    }
                    
                    String name = args[0].toString();
                    groovy.lang.Closure<?> configureAction = args.length > 1 && args[1] instanceof groovy.lang.Closure ? 
                        (groovy.lang.Closure<?>) args[1] : null;
                    
                    logger.info("Creating OCI repository '{}' via direct oci() method (type: {})", name, 
                               isPublishingRepo ? "publishing" : "dependency");
                    
                    if (isPublishingRepo) {
                        // For publishing - create OciMavenRepository for publishing tasks
                        MavenOciArtifactRepository ociRepo = project.getObjects().newInstance(MavenOciArtifactRepository.class, name);
                        
                        // Configure the repository if configuration block provided
                        if (configureAction != null) {
                            org.gradle.util.internal.ConfigureUtil.configure(configureAction, ociRepo);
                        }
                        
                        // Store in separate list for publishing task generation
                        @SuppressWarnings("unchecked")
                        java.util.List<MavenOciArtifactRepository> ociRepositories =
                            (java.util.List<MavenOciArtifactRepository>) project.getExtensions().getExtraProperties().get("ociRepositoriesForProcessing");
                        if (ociRepositories != null && !ociRepositories.contains(ociRepo)) {
                            ociRepositories.add(ociRepo);
                            logger.debug("Added OCI repository '{}' to publishing list", name);
                        }
                        
                        // IMPORTANT: Do NOT add to publishing.repositories to avoid ClassCastException from maven-publish
                        // Only add for test visibility after project evaluation
                        if (project.getState().getExecuted()) {
                            // Test scenario - add immediately for visibility
                            try {
                                repositories.add(ociRepo);
                                logger.debug("Added OCI repository '{}' to publishing.repositories for test visibility", name);
                            } catch (Exception e) {
                                logger.debug("Could not add OCI repository for test visibility: {}", e.getMessage());
                            }
                        } else {
                            // Normal build - add after evaluation to avoid maven-publish conflicts  
                            project.afterEvaluate(p -> {
                                try {
                                    repositories.add(ociRepo);
                                    logger.debug("Added OCI repository '{}' to publishing.repositories for test visibility", name);
                                } catch (Exception e) {
                                    logger.debug("Could not add OCI repository for test visibility: {}", e.getMessage());
                                }
                            });
                        }
                        
                        return ociRepo;
                        
                    } else {
                        // For dependency resolution - create Maven repository backed by OCI
                        org.gradle.api.artifacts.repositories.MavenArtifactRepository mavenRepo = repositories.maven(mavenRepoAction -> {
                            // Set the correct name initially so factory doesn't need to change it
                            mavenRepoAction.setName(name);
                        });
                        
                        // Create OCI spec from configuration
                        MavenOciRepositorySpec spec = project.getObjects().newInstance(MavenOciRepositorySpec.class, name);
                        if (configureAction != null) {
                            org.gradle.util.internal.ConfigureUtil.configure(configureAction, spec);
                        }
                        
                        // Use factory to create OCI-backed Maven repository
                        MavenOciRepositoryFactory.createOciMavenRepository(spec, mavenRepo, project);
                        
                        logger.debug("Created OCI-backed Maven repository for dependency resolution: {}", name);
                        return mavenRepo;
                    }
                }
            };
            
            // Add the method to the ExpandoMetaClass
            emc.registerInstanceMethod("oci", ociMethod);
            
            // Initialize the ExpandoMetaClass
            emc.initialize();
            
            // Apply the ExpandoMetaClass to the specific repositories instance using reflection
            java.lang.reflect.Method setMetaClass = repositories.getClass().getMethod("setMetaClass", groovy.lang.MetaClass.class);
            setMetaClass.invoke(repositories, emc);
            
            logger.info("Successfully added direct oci() method to repositories via ExpandoMetaClass");
        } catch (Exception e) {
            logger.error("Failed to add direct oci method via ExpandoMetaClass: " + e.getMessage(), e);
            e.printStackTrace();
            
            // Fallback: Try simpler approach with method missing
            addMethodMissingHandler(repositories, project);
        }
    }
    
    /**
     * Fallback approach: Add methodMissing handler to intercept oci() calls
     */
    private void addMethodMissingHandler(org.gradle.api.artifacts.dsl.RepositoryHandler repositories, Project project) {
        try {
            logger.info("Attempting to add methodMissing handler for oci() method");
            
            // Get the MetaClass and add a methodMissing handler using reflection
            java.lang.reflect.Method getMetaClass = repositories.getClass().getMethod("getMetaClass");
            groovy.lang.MetaClass mc = (groovy.lang.MetaClass) getMetaClass.invoke(repositories);
            
            // Create a methodMissing closure
            groovy.lang.Closure<?> methodMissing = new groovy.lang.Closure<Object>(this) {
                public Object doCall(String name, Object args) {
                    if ("oci".equals(name)) {
                        // Handle oci method call
                        Object[] argArray = (Object[]) args;
                        if (argArray.length == 0) {
                            throw new IllegalArgumentException("oci() method requires at least a name parameter");
                        }
                        
                        String repoName = argArray[0].toString();
                        groovy.lang.Closure<?> configureAction = argArray.length > 1 && argArray[1] instanceof groovy.lang.Closure ? 
                            (groovy.lang.Closure<?>) argArray[1] : null;
                        
                        logger.info("Creating OCI repository '{}' via methodMissing handler", repoName);
                        
                        // Create OCI Maven repository (no proxy needed since not added to maven-publish)
                        MavenOciArtifactRepository ociRepo = project.getObjects().newInstance(MavenOciArtifactRepository.class, repoName);
                        
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
                            logger.debug("Added OCI repository '{}' to separate processing list", repoName);
                        }
                        
                        // Add to repositories AFTER maven-publish has finished processing
                        if (project.getState().getExecuted()) {
                            // Project already evaluated - add immediately (test scenario)
                            try {
                                repositories.add(ociRepo);
                                logger.debug("Added OCI repository '{}' immediately for test visibility", repoName);
                            } catch (Exception e) {
                                logger.debug("Could not add OCI repository '{}' to repositories: {}", repoName, e.getMessage());
                            }
                        } else {
                            // Project still being evaluated - add after evaluation
                            project.afterEvaluate(p -> {
                                try {
                                    repositories.add(ociRepo);
                                    logger.debug("Added OCI repository '{}' back to repositories for test visibility", repoName);
                                } catch (Exception e) {
                                    logger.debug("Could not add OCI repository '{}' back to repositories: {}", repoName, e.getMessage());
                                }
                            });
                        }
                        
                        return ociRepo;
                    }
                    
                    // For other methods, throw the standard exception
                    throw new groovy.lang.MissingMethodException(name, repositories.getClass(), (Object[]) args);
                }
            };
            
            // Set the methodMissing handler
            mc.setProperty(repositories, "methodMissing", methodMissing);
            
            logger.info("Successfully added methodMissing handler for oci() method");
        } catch (Exception e) {
            logger.error("Failed to add methodMissing handler: " + e.getMessage(), e);
            e.printStackTrace();
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
     * Closure that acts as the oci() method for repositories
     */
    public static class OciMethodClosure extends groovy.lang.Closure<MavenOciArtifactRepository> {
        private final org.gradle.api.artifacts.dsl.RepositoryHandler repositories;
        private final Project project;
        
        public OciMethodClosure(org.gradle.api.artifacts.dsl.RepositoryHandler repositories, Project project) {
            super(null);
            this.repositories = repositories;
            this.project = project;
        }
        
        public MavenOciArtifactRepository doCall(String name) {
            return doCall(name, null);
        }
        
        public MavenOciArtifactRepository doCall(String name, groovy.lang.Closure<?> configureAction) {
            logger.info("Creating OCI repository '{}' via direct method", name);
            
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
            
            // Add to repositories AFTER maven-publish has finished processing
            if (project.getState().getExecuted()) {
                // Project already evaluated - add immediately (test scenario)
                try {
                    repositories.add(ociRepo);
                    logger.debug("Added OCI repository '{}' immediately for test visibility", name);
                } catch (Exception e) {
                    logger.debug("Could not add OCI repository '{}' to repositories: {}", name, e.getMessage());
                }
            } else {
                // Project still being evaluated - add after evaluation
                project.afterEvaluate(p -> {
                    try {
                        repositories.add(ociRepo);
                        logger.debug("Added OCI repository '{}' back to repositories for test visibility", name);
                    } catch (Exception e) {
                        logger.debug("Could not add OCI repository '{}' back to repositories: {}", name, e.getMessage());
                    }
                });
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
            return publication.getArtifacts()
                .stream()
                .filter(artifact -> artifact.getFile() != null)
                .map(artifact -> artifact.getFile())
                .collect(Collectors.toList());
        }));
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
