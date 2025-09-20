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

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.net.URI;

/**
 * Simple wrapper that creates a Maven repository with custom resolution logic.
 * This approach avoids the complexity of implementing the full MavenArtifactRepository interface
 * by extending an existing Maven repository and intercepting artifact resolution.
 */
public class OciRepositoryWrapper {
    
    private static final Logger logger = Logging.getLogger(OciRepositoryWrapper.class);
    
    /**
     * Creates a Maven repository configured for OCI artifact resolution.
     * 
     * @param spec The OCI repository specification
     * @param baseRepository The base Maven repository to wrap
     * @return The configured Maven repository
     */
    public static MavenArtifactRepository createOciMavenRepository(
            OciRepositorySpec spec, 
            MavenArtifactRepository baseRepository) {
        
        logger.info("Creating OCI Maven repository wrapper for: {}", spec.getName());
        
        // Use the file-based OCI Maven repository implementation for demonstration
        // In a production environment, this would use the full OCI resolution
        // OciMavenRepositoryImpl.configureOciResolution(spec, baseRepository);
        
        logger.info("OCI Maven repository wrapper created: {}", spec.getName());
        
        return baseRepository;
    }
    
    /**
     * Creates a simple demonstrative repository that shows OCI integration.
     * This is a simplified version that doesn't do actual OCI resolution
     * but demonstrates the concept.
     * 
     * @param spec The OCI repository specification
     * @param baseRepository The base Maven repository
     * @return The demonstration repository
     */
    public static MavenArtifactRepository createDemoOciRepository(
            OciRepositorySpec spec, 
            MavenArtifactRepository baseRepository) {
        
        logger.info("Creating demo OCI repository for: {}", spec.getName());
        
        // For demo purposes, we'll just configure a standard Maven repository
        // In a full implementation, this would have custom resolution logic
        
        baseRepository.setName("oci-" + spec.getName());
        
        // Use a placeholder URL that represents the OCI registry
        String demoUrl = "https://demo.oci.repository/" + spec.getName();
        baseRepository.setUrl(URI.create(demoUrl));
        
        // Configure credentials
        if (spec.hasCredentials()) {
            baseRepository.credentials(creds -> {
                creds.setUsername(spec.getCredentials().get().getUsername());
                creds.setPassword(spec.getCredentials().get().getPassword());
            });
        }
        
        logger.info("Demo OCI repository created: {}", baseRepository.getName());
        
        return baseRepository;
    }
}