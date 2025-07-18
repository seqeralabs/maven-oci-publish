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

/*
 * Maven OCI Publish Plugin
 */
package io.seqera.mavenoci

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion

/**
 * A Gradle plugin for publishing Maven artifacts to OCI registries
 */
class MavenOciPublishPlugin implements Plugin<Project> {
    
    static final String EXTENSION_NAME = 'mavenOci'
    static final String PUBLISH_TASK_GROUP = 'publishing'
    
    void apply(Project project) {
        // Check minimum Gradle version
        if (GradleVersion.current() < GradleVersion.version('6.0')) {
            throw new IllegalStateException("This plugin requires Gradle 6.0 or later")
        }
        
        // Create the extension
        MavenOciPublishingExtension extension = project.extensions.create(
            EXTENSION_NAME, 
            MavenOciPublishingExtension,
            project.objects
        )
        
        // Apply the maven-publish plugin to leverage its components
        project.plugins.apply('maven-publish')
        
        // Create tasks after project evaluation
        project.afterEvaluate {
            createPublishingTasks(project, extension)
        }
        
        // Add a lifecycle task
        project.tasks.register('publishToOciRegistries') {
            description = 'Publishes all OCI publications to all OCI repositories'
            group = PUBLISH_TASK_GROUP
        }
    }
    
    private void createPublishingTasks(Project project, MavenOciPublishingExtension extension) {
        // Create a task for each publication-repository combination
        extension.publications.all { publication ->
            extension.repositories.all { repository ->
                createPublishTask(project, publication, repository)
            }
        }
    }
    
    private void createPublishTask(Project project, OciPublication publication, OciRepository repository) {
        String taskName = "publish${publication.name.capitalize()}PublicationTo${repository.name.capitalize()}Repository"
        
        TaskProvider<PublishToOciRepositoryTask> publishTask = project.tasks.register(taskName, PublishToOciRepositoryTask) {
            description = "Publishes OCI publication '${publication.name}' to repository '${repository.name}'"
            group = PUBLISH_TASK_GROUP
            
            // Configure task inputs
            it.publication.set(publication)
            it.ociRepository.set(repository)
            it.registryUrl.set(repository.url)
            it.insecure.set(repository.insecure)
            
            // Configure repository and tag
            if (publication.repository.isPresent()) {
                it.repository.set(publication.repository)
            }
            if (publication.tag.isPresent()) {
                it.tag.set(publication.tag)
            } else {
                // Default tag based on project version
                it.tag.set(project.version.toString())
            }
            
            // Configure credentials if available
            if (repository.credentials.isPresent()) {
                def creds = repository.credentials.get()
                if (creds.hasProperty('username') && creds.hasProperty('password')) {
                    it.username.set(creds.username)
                    it.password.set(creds.password)
                }
            }
            
            // Configure artifacts
            if (publication.component.isPresent()) {
                // Add artifacts from software component
                addArtifactsFromComponent(publication.component.get(), project, it)
            }
            
            // Add custom artifacts
            publication.artifacts.get().each { artifact ->
                it.artifacts.from(artifact.file)
            }
        }
        
        // Wire the task to the lifecycle task
        project.tasks.named('publishToOciRegistries').configure {
            dependsOn publishTask
        }
    }
    
    private void addArtifactsFromComponent(SoftwareComponent component, Project project, PublishToOciRepositoryTask task) {
        // Get the corresponding Maven publication to extract artifacts
        PublishingExtension publishingExtension = project.extensions.getByType(PublishingExtension)
        
        // Try to find a Maven publication with the same component
        publishingExtension.publications.withType(MavenPublication).all { mavenPub ->
            if (mavenPub.component == component) {
                // Add all artifacts from the Maven publication
                mavenPub.artifacts.all { artifact ->
                    task.artifacts.from(artifact.file)
                }
            }
        }
    }
}
