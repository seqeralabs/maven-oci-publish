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
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

/**
 * Main extension for OCI publishing configuration.
 * Provides the 'mavenOci' DSL block for configuring OCI publication.
 */
public class MavenOciPublishingExtension {
    
    private final NamedDomainObjectContainer<OciPublication> publications;
    private final NamedDomainObjectContainer<OciRepository> repositories;
    private final ObjectFactory objectFactory;
    
    @Inject
    public MavenOciPublishingExtension(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        this.publications = objectFactory.domainObjectContainer(OciPublication.class);
        this.repositories = objectFactory.domainObjectContainer(OciRepository.class);
    }
    
    /**
     * Configures the publications for this extension.
     */
    public void publications(Action<? super NamedDomainObjectContainer<OciPublication>> action) {
        action.execute(publications);
    }
    
    /**
     * Configures the repositories for this extension.
     */
    public void repositories(Action<? super NamedDomainObjectContainer<OciRepository>> action) {
        action.execute(repositories);
    }
    
    /**
     * Returns the publications container.
     */
    public NamedDomainObjectContainer<OciPublication> getPublications() {
        return publications;
    }
    
    /**
     * Returns the repositories container.
     */
    public NamedDomainObjectContainer<OciRepository> getRepositories() {
        return repositories;
    }
}