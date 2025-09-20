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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Component repository for OCI registries that handles Maven module resolution.
 * This class bridges Gradle's dependency resolution system with OCI registries.
 */
public class OciModuleComponentRepository {
    
    private static final Logger logger = Logging.getLogger(OciModuleComponentRepository.class);
    
    private final OciRepositorySpec spec;
    private final OciDependencyResolver resolver;
    
    public OciModuleComponentRepository(OciRepositorySpec spec) {
        this.spec = spec;
        this.resolver = new OciDependencyResolver(spec);
        
        logger.info("Created OCI module component repository for: {}", spec.getName());
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
     * Gets the dependency resolver for this repository.
     * 
     * @return The OCI dependency resolver
     */
    public OciDependencyResolver getResolver() {
        return resolver;
    }
    
    /**
     * Resolves a module component selector to an identifier.
     * This is where Maven coordinates are mapped to OCI references.
     * 
     * @param selector The module component selector (e.g., io.seqera:shared-library:1.0.0)
     * @return The resolved component identifier
     */
    public ModuleComponentIdentifier resolveComponentSelector(ModuleComponentSelector selector) {
        logger.debug("Resolving component selector: {}:{}:{}", 
                    selector.getGroup(), selector.getModule(), selector.getVersion());
        
        // Use the resolver to handle the actual resolution
        return resolver.resolveComponent(selector);
    }
    
    /**
     * Checks if this repository can resolve the given module component selector.
     * 
     * @param selector The module component selector to check
     * @return true if this repository can resolve the selector, false otherwise
     */
    public boolean canResolve(ModuleComponentSelector selector) {
        // For now, assume we can resolve any Maven coordinate
        // In a more sophisticated implementation, we might check if the artifact exists
        return true;
    }
}