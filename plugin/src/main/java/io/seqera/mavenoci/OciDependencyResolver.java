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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Resolver for OCI-based Maven dependencies.
 * This class handles the resolution of Maven coordinates to OCI artifact references
 * and coordinates with the OCI resource accessor to download artifacts.
 */
public class OciDependencyResolver {
    
    private static final Logger logger = Logging.getLogger(OciDependencyResolver.class);
    
    private final OciRepositorySpec spec;
    private final OciResourceAccessor resourceAccessor;
    
    public OciDependencyResolver(OciRepositorySpec spec) {
        this.spec = spec;
        this.resourceAccessor = new OciResourceAccessor(spec);
        
        logger.info("Created OCI dependency resolver for repository: {}", spec.getName());
    }
    
    /**
     * Resolves a module component selector to a component identifier.
     * This maps Maven coordinates to OCI references using the enhanced coordinate system.
     * 
     * @param selector The module component selector (e.g., io.seqera:shared-library:1.0.0)
     * @return The resolved component identifier
     */
    public ModuleComponentIdentifier resolveComponent(ModuleComponentSelector selector) {
        logger.debug("Resolving component: {}:{}:{}", 
                    selector.getGroup(), selector.getModule(), selector.getVersion());
        
        // Build OCI reference using the enhanced coordinate system
        String ociReference = buildOciReference(selector);
        logger.info("Mapped Maven coordinate {}:{}:{} to OCI reference: {}", 
                   selector.getGroup(), selector.getModule(), selector.getVersion(), ociReference);
        
        // Create component identifier
        return DefaultModuleComponentIdentifier.newId(
            DefaultModuleIdentifier.newId(selector.getGroup(), selector.getModule()),
            selector.getVersion()
        );
    }
    
    /**
     * Resolves and downloads artifact files for a given component.
     * 
     * @param identifier The component identifier to resolve
     * @return List of resolved artifact files
     */
    public List<File> resolveArtifacts(ModuleComponentIdentifier identifier) {
        logger.debug("Resolving artifacts for component: {}", identifier);
        
        // Build OCI reference from component identifier
        String ociReference = buildOciReference(identifier);
        
        // Use resource accessor to download artifacts
        return resourceAccessor.downloadArtifacts(ociReference);
    }
    
    /**
     * Checks if an artifact exists in the OCI registry.
     * 
     * @param selector The module component selector to check
     * @return true if the artifact exists, false otherwise
     */
    public boolean artifactExists(ModuleComponentSelector selector) {
        try {
            String ociReference = buildOciReference(selector);
            return resourceAccessor.artifactExists(ociReference);
        } catch (Exception e) {
            logger.debug("Error checking artifact existence for {}:{}:{}", 
                        selector.getGroup(), selector.getModule(), selector.getVersion(), e);
            return false;
        }
    }
    
    /**
     * Builds an OCI reference from a module component selector.
     * Uses the enhanced coordinate system for mapping.
     * 
     * @param selector The module component selector
     * @return The OCI reference string
     */
    private String buildOciReference(ModuleComponentSelector selector) {
        return buildOciReference(selector.getGroup(), selector.getModule(), selector.getVersion());
    }
    
    /**
     * Builds an OCI reference from a module component identifier.
     * Uses the enhanced coordinate system for mapping.
     * 
     * @param identifier The module component identifier
     * @return The OCI reference string
     */
    private String buildOciReference(ModuleComponentIdentifier identifier) {
        return buildOciReference(identifier.getGroup(), identifier.getModule(), identifier.getVersion());
    }
    
    /**
     * Builds an OCI reference from Maven coordinates.
     * Uses the enhanced coordinate system for mapping.
     * 
     * @param group The Maven group ID
     * @param artifact The Maven artifact ID
     * @param version The Maven version
     * @return The OCI reference string
     */
    private String buildOciReference(String group, String artifact, String version) {
        // Parse registry information from the repository URL
        OciRegistryUriParser.OciRegistryInfo registryInfo = 
            OciRegistryUriParser.parse(spec.getUrl().getOrElse(""));
        
        // Use the enhanced coordinate system to build the OCI reference
        return registryInfo.buildOciReference(group, artifact, version);
    }
    
    /**
     * Gets the resource accessor for this resolver.
     * 
     * @return The OCI resource accessor
     */
    public OciResourceAccessor getResourceAccessor() {
        return resourceAccessor;
    }
}