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

import javax.inject.Inject;

/**
 * Handler for OCI repositories that provides the ociRepositories DSL block.
 * This enables consumers to configure OCI registries as Maven repositories.
 */
public class OciRepositoryHandler {
    
    private static final Logger logger = Logging.getLogger(OciRepositoryHandler.class);
    
    private final ObjectFactory objectFactory;
    private final Project project;
    private final NamedDomainObjectContainer<OciRepositorySpec> repositories;
    
    @Inject
    public OciRepositoryHandler(ObjectFactory objectFactory, Project project) {
        this.objectFactory = objectFactory;
        this.project = project;
        this.repositories = objectFactory.domainObjectContainer(OciRepositorySpec.class, 
            name -> objectFactory.newInstance(OciRepositorySpec.class, name));
    }
    
    /**
     * Configures OCI repositories using the provided action.
     * This is the method that will be called when users use the ociRepositories block.
     * 
     * @param action Configuration action for OCI repositories
     */
    public void call(Action<? super NamedDomainObjectContainer<OciRepositorySpec>> action) {
        action.execute(repositories);
        
        // Register each configured OCI repository with Gradle's repository system
        repositories.all(spec -> {
            logger.info("Registering OCI repository: {} with URL: {}", spec.getName(), spec.getUrl().getOrElse(""));
            
            // Create a Maven repository and configure it for OCI resolution
            project.getRepositories().maven(repo -> {
                // Use real OCI resolver to pull artifacts on-demand
                OciMavenRepositoryFactory.createOciMavenRepository(spec, repo, project);
                logger.info("Successfully registered OCI repository: {}", spec.getName());
            });
        });
    }
    
    /**
     * Creates a new OCI repository specification.
     * This method supports the DSL syntax: seqeraPublic { url = "..." }
     */
    public OciRepositorySpec seqeraPublic(Action<? super OciRepositorySpec> action) {
        logger.info("Creating seqeraPublic OCI repository specification");
        OciRepositorySpec spec = repositories.create("seqeraPublic");
        action.execute(spec);
        
        // Immediately register the repository when it's configured
        logger.info("Registering OCI repository immediately: {} with URL: {}", spec.getName(), spec.getUrl().getOrElse(""));
        project.getRepositories().maven(repo -> {
            OciMavenRepositoryFactory.createOciMavenRepository(spec, repo, project);
            logger.info("Successfully registered OCI repository: {}", spec.getName());
        });
        
        return spec;
    }
    
    /**
     * Generic method to create any named OCI repository.
     * This enables dynamic repository names.
     */
    public OciRepositorySpec create(String name, Action<? super OciRepositorySpec> action) {
        OciRepositorySpec spec = repositories.create(name);
        action.execute(spec);
        return spec;
    }
    
    /**
     * Creates a test registry repository.
     * This enables DSL syntax: testRegistry { url = "..." }
     */
    public OciRepositorySpec testRegistry(Action<? super OciRepositorySpec> action) {
        logger.info("Creating testRegistry OCI repository specification");
        OciRepositorySpec spec = repositories.create("testRegistry");
        action.execute(spec);
        
        // Immediately register the repository when it's configured
        logger.info("Registering OCI repository immediately: {} with URL: {}", spec.getName(), spec.getUrl().getOrElse(""));
        project.getRepositories().maven(repo -> {
            OciMavenRepositoryFactory.createOciMavenRepository(spec, repo, project);
            logger.info("Successfully registered OCI repository: {}", spec.getName());
        });
        
        return spec;
    }
    
    /**
     * Configures OCI repositories using the provided action.
     * Alternative method name for DSL compatibility.
     * 
     * @param action Configuration action for OCI repositories
     */
    public void configure(Action<? super NamedDomainObjectContainer<OciRepositorySpec>> action) {
        call(action);
    }
    
    /**
     * Gets the container of configured OCI repositories.
     * 
     * @return Container of OCI repository specifications
     */
    public NamedDomainObjectContainer<OciRepositorySpec> getRepositories() {
        return repositories;
    }
}