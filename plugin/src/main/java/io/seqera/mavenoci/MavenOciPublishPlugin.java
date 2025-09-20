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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.GradleVersion;

import java.util.stream.Collectors;

/**
 * A Gradle plugin for publishing and consuming Maven artifacts to/from OCI registries
 */
public class MavenOciPublishPlugin implements Plugin<Project> {
    
    private static final Logger logger = Logging.getLogger(MavenOciPublishPlugin.class);
    
    public static final String EXTENSION_NAME = "mavenOci";
    public static final String PUBLISH_TASK_GROUP = "publishing";
    
    @Override
    public void apply(Project project) {
        // Check minimum Gradle version
        if (GradleVersion.current().compareTo(GradleVersion.version("6.0")) < 0) {
            throw new IllegalStateException("This plugin requires Gradle 6.0 or later");
        }
        
        logger.info("Applying Maven OCI plugin to project: {}", project.getName());
        
        // Create the publishing extension
        MavenOciPublishingExtension extension = project.getExtensions().create(
            EXTENSION_NAME, 
            MavenOciPublishingExtension.class,
            project.getObjects()
        );
        
        // Apply the maven-publish plugin to leverage its components
        project.getPluginManager().apply("maven-publish");
        
        // Add consumer functionality (OCI repositories)
        setupConsumerSupport(project);
        
        // Install dependency resolution interceptor
        OciDependencyResolutionInterceptor.install(project);
        
        // Create tasks after project evaluation
        project.afterEvaluate(p -> createPublishingTasks(p, extension));
        
        // Add a lifecycle task
        project.getTasks().register("publishToOciRegistries", task -> {
            task.setDescription("Publishes all OCI publications to all OCI repositories");
            task.setGroup(PUBLISH_TASK_GROUP);
        });
    }
    
    private void createPublishingTasks(Project project, MavenOciPublishingExtension extension) {
        // Create a task for each publication-repository combination
        extension.getPublications().all(publication -> 
            extension.getRepositories().all(repository -> 
                createPublishTask(project, publication, repository)
            )
        );
    }
    
    private void createPublishTask(Project project, OciPublication publication, OciRepository repository) {
        String taskName = "publish" + capitalize(publication.getName()) + "PublicationTo" + capitalize(repository.getName()) + "Repository";
        
        TaskProvider<PublishToOciRepositoryTask> publishTask = project.getTasks().register(taskName, PublishToOciRepositoryTask.class, task -> {
            task.setDescription("Publishes OCI publication '" + publication.getName() + "' to repository '" + repository.getName() + "'");
            task.setGroup(PUBLISH_TASK_GROUP);
            
            // Configure task inputs (configuration cache compatible)
            task.getPublicationName().set(publication.getName());
            task.getRepositoryName().set(repository.getName());
            task.getRegistryUrl().set(repository.getUrl());
            task.getInsecure().set(repository.getInsecure());
            task.getExecutionId().set(System.currentTimeMillis() + "-" + publication.getName() + "-" + repository.getName());
            
            // Configure namespace if provided
            if (repository.getNamespace().isPresent()) {
                task.getNamespace().set(repository.getNamespace());
            }
            
            // Configure Maven coordinates for new coordinate mapping
            task.getGroupId().set(project.getGroup().toString());
            task.getVersion().set(project.getVersion().toString());
            
            // Try to get artifactId from Maven publication, fallback to project name
            PublishingExtension publishingExt = project.getExtensions().findByType(PublishingExtension.class);
            if (publishingExt != null) {
                publishingExt.getPublications().withType(MavenPublication.class).all(mavenPub -> {
                    if (mavenPub.getArtifactId() != null) {
                        task.getArtifactId().set(mavenPub.getArtifactId());
                    } else {
                        task.getArtifactId().set(project.getName());
                    }
                });
            } else {
                task.getArtifactId().set(project.getName());
            }
            
            // Configure repository and tag (for backward compatibility)
            if (publication.getRepository().isPresent()) {
                task.getRepository().set(publication.getRepository());
            }
            if (publication.getTag().isPresent()) {
                task.getTag().set(publication.getTag());
            } else {
                // Default tag based on project version
                task.getTag().set(project.getVersion().toString());
            }
            
            // Configure credentials if available
            if (repository.getCredentials().isPresent()) {
                Object creds = repository.getCredentials().get();
                if (creds instanceof PasswordCredentials) {
                    PasswordCredentials passwordCreds = (PasswordCredentials) creds;
                    task.getUsername().set(passwordCreds.getUsername());
                    task.getPassword().set(passwordCreds.getPassword());
                }
            }
            
            // Configure artifacts - use lazy providers to ensure Maven publication artifacts are available
            if (publication.getComponent().isPresent()) {
                // Add artifacts from software component using lazy evaluation
                configureArtifactsFromComponent(publication.getComponent().get(), project, task);
            }
            
            // Add custom artifacts
            publication.getArtifacts().get().forEach(artifact -> 
                task.getArtifacts().from(artifact.getFile())
            );
            
            // Ensure the task depends on the build tasks that create the artifacts
            try {
                task.dependsOn(project.getTasks().named("jar"));
            } catch (Exception e) {
                // jar task may not exist, that's ok
            }
            try {
                task.dependsOn(project.getTasks().named("build"));
            } catch (Exception e) {
                // build task may not exist, that's ok
            }
        });
        
        // Wire the task to the lifecycle task
        project.getTasks().named("publishToOciRegistries").configure(lifecycleTask -> 
            lifecycleTask.dependsOn(publishTask)
        );
    }
    
    private void configureArtifactsFromComponent(SoftwareComponent component, Project project, PublishToOciRepositoryTask task) {
        // Use lazy configuration to add artifacts from Maven publications
        PublishingExtension publishingExtension = project.getExtensions().getByType(PublishingExtension.class);
        
        // Configure artifacts lazily by depending on the jar task and using lazy providers
        task.getArtifacts().from(project.provider(() -> {
            return publishingExtension.getPublications().withType(MavenPublication.class)
                .stream()
                .flatMap(mavenPublication -> mavenPublication.getArtifacts().stream())
                .filter(artifact -> artifact.getFile() != null)
                .map(artifact -> artifact.getFile())
                .collect(Collectors.toList());
        }));
    }
    
    /**
     * Sets up consumer support for OCI repositories.
     * This enables the ociRepositories DSL block for consuming Maven artifacts from OCI registries.
     * 
     * @param project The project to configure
     */
    private void setupConsumerSupport(Project project) {
        logger.info("Setting up OCI repository consumer support for project: {}", project.getName());
        
        // Create the OCI repository handler
        OciRepositoryHandler ociHandler = project.getObjects().newInstance(
            OciRepositoryHandler.class, 
            project.getObjects(),
            project
        );
        
        // Add as a project extension
        project.getExtensions().add("ociRepositories", ociHandler);
        
        logger.info("OCI repository consumer support enabled. Use 'ociRepositories { ... }' to configure OCI registries.");
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}