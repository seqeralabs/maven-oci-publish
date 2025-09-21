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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import groovy.lang.Closure;

import javax.inject.Inject;

/**
 * Main extension for OCI publishing and repository configuration.
 * 
 * <p>This class provides the {@code oci} DSL block for configuring Maven artifact publishing
 * to OCI registries. It manages both publications (what to publish) and repositories (where to publish).</p>
 * 
 * <h3>DSL Structure</h3>
 * <p>The extension creates the following DSL structure in your build script:</p>
 * <pre>{@code
 * oci {
 *     publications {
 *         maven {
 *             from components.java
 *             repository = 'my-artifacts'
 *         }
 *     }
 *     
 *     repositories {
 *         dockerHub {
 *             url = 'https://registry-1.docker.io'
 *             namespace = 'maven'
 *             credentials {
 *                 username = 'user'
 *                 password = 'pass'
 *             }
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h3>Task Generation</h3>
 * <p>The extension automatically generates Gradle publishing tasks for each publication-repository 
 * combination. For example, a publication named "maven" and repository named "dockerHub" creates:</p>
 * <ul>
 *   <li>{@code publishMavenPublicationToDockerHubRepository}</li>
 *   <li>{@code publishToOciRegistries} (lifecycle task)</li>
 * </ul>
 * 
 * <h3>Integration</h3>
 * <p>This extension also provides OCI repository support for dependency resolution by registering
 * the {@code oci("name")} factory method in the {@code repositories} block.</p>
 * 
 * @see MavenOciPublication
 * @see MavenOciRepository
 * @see MavenOciPublishPlugin
 * @since 1.0
 */
public class MavenOciPublishingExtension {
    
    private static final Logger logger = Logging.getLogger(MavenOciPublishingExtension.class);
    
    private final NamedDomainObjectContainer<MavenOciPublication> publications;
    private final NamedDomainObjectContainer<MavenOciRepository> repositories;
    private final ObjectFactory objectFactory;
    private Project project;
    
    @Inject
    public MavenOciPublishingExtension(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        this.publications = objectFactory.domainObjectContainer(MavenOciPublication.class);
        this.repositories = objectFactory.domainObjectContainer(MavenOciRepository.class);
    }
    
    /**
     * Sets the project context for repository factory operations.
     * This is called by the plugin during setup.
     */
    public void setProject(Project project) {
        this.project = project;
    }
    
    /**
     * Gets the project context.
     */
    public Project getProject() {
        return project;
    }
    
    /**
     * Configures the publications for this extension.
     */
    public void publications(Action<? super NamedDomainObjectContainer<MavenOciPublication>> action) {
        action.execute(publications);
    }
    
    /**
     * Configures the repositories for this extension.
     */
    public void repositories(Action<? super NamedDomainObjectContainer<MavenOciRepository>> action) {
        action.execute(repositories);
    }
    
    /**
     * Returns the publications container.
     */
    public NamedDomainObjectContainer<MavenOciPublication> getPublications() {
        return publications;
    }
    
    /**
     * Returns the repositories container.
     */
    public NamedDomainObjectContainer<MavenOciRepository> getRepositories() {
        return repositories;
    }
    
    /**
     * Creates an OCI repository using a named approach similar to maven repositories.
     * This enables: oci("name") { url = "http://..." }
     */
    public ArtifactRepository call(String name, Closure<?> closure) {
        if (project == null) {
            throw new IllegalStateException("Project context not set. This method should be called after plugin application.");
        }
        
        logger.debug("Creating named OCI repository '{}' using closure syntax", name);
        
        // Create OCI repository specification with the provided name
        MavenOciRepositorySpec spec = objectFactory.newInstance(MavenOciRepositorySpec.class, name);
        
        // Configure using the spec as delegate (isolated from project context)
        closure.setDelegate(spec);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        
        logger.debug("Creating OCI repository '{}' with URL: {}", spec.getName(), spec.getUrl().getOrNull());
        
        // Create and register Maven repository that wraps OCI functionality
        return project.getRepositories().maven(mavenRepo -> {
            mavenRepo.setName(spec.getName());
            MavenOciRepositoryFactory.createOciMavenRepository(spec, mavenRepo, project);
        });
    }
    
}
