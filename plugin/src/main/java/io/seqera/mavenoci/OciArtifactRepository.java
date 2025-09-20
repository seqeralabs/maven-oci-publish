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

import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.inject.Inject;
import java.net.URI;

/**
 * Implementation of Gradle's ArtifactRepository interface for OCI registries.
 * This allows OCI registries to be used as Maven repositories in Gradle builds.
 */
public class OciArtifactRepository implements ArtifactRepository {
    
    private static final Logger logger = Logging.getLogger(OciArtifactRepository.class);
    
    private final OciRepositorySpec spec;
    private final OciModuleComponentRepository componentRepository;
    
    @Inject
    public OciArtifactRepository(OciRepositorySpec spec) {
        this.spec = spec;
        this.componentRepository = new OciModuleComponentRepository(spec);
        
        logger.info("Created OCI artifact repository: {} -> {}", spec.getName(), spec.getUrl().getOrElse(""));
    }
    
    @Override
    public String getName() {
        return spec.getName();
    }
    
    public void setName(String name) {
        // Name is immutable for OCI repositories
        throw new UnsupportedOperationException("Cannot change name of OCI repository");
    }
    
    public String getDisplayName() {
        return "OCI repository '" + getName() + "' (" + spec.getUrl().getOrElse("") + ")";
    }
    
    public void content(org.gradle.api.Action<? super RepositoryContentDescriptor> configureAction) {
        // OCI repositories support all content types
        // This method is used for content filtering but we'll allow everything for now
    }
    
    /**
     * Gets the underlying component repository that handles dependency resolution.
     * 
     * @return The OCI module component repository
     */
    public OciModuleComponentRepository getComponentRepository() {
        return componentRepository;
    }
    
    /**
     * Gets the repository specification.
     * 
     * @return The OCI repository specification
     */
    public OciRepositorySpec getSpec() {
        return spec;
    }
    
    /**
     * Gets the registry URL.
     * 
     * @return The registry URL as a URI
     */
    public URI getUrl() {
        String url = spec.getUrl().getOrElse("");
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid URL for OCI repository {}: {}", getName(), url);
            return URI.create("https://docker.io");
        }
    }
}