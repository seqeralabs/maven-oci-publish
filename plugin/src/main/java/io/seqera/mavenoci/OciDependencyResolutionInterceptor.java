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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Intercepts dependency resolution to trigger OCI artifact resolution when needed.
 * This class hooks into Gradle's dependency resolution lifecycle to ensure
 * OCI artifacts are pulled and cached before standard Maven resolution occurs.
 */
public class OciDependencyResolutionInterceptor {
    
    private static final Logger logger = Logging.getLogger(OciDependencyResolutionInterceptor.class);
    
    /**
     * Installs the dependency resolution interceptor on a project.
     * This hooks into the beforeResolve event for all configurations.
     * 
     * @param project The Gradle project
     */
    public static void install(Project project) {
        logger.debug("Installing OCI dependency resolution interceptor for project: {}", project.getName());
        
        // Hook into all configurations to intercept dependency resolution
        project.getConfigurations().all(configuration -> {
            configuration.getIncoming().beforeResolve(resolvableDeps -> {
                interceptDependencyResolution(project, configuration, resolvableDeps);
            });
        });
        
        logger.debug("OCI dependency resolution interceptor installed");
    }
    
    /**
     * Intercepts dependency resolution for a specific configuration.
     * Attempts to resolve any missing artifacts from OCI registries.
     * 
     * @param project The Gradle project
     * @param configuration The configuration being resolved
     * @param resolvableDeps The resolvable dependencies
     */
    private static void interceptDependencyResolution(Project project, Configuration configuration, 
                                                     ResolvableDependencies resolvableDeps) {
        try {
            logger.debug("Intercepting dependency resolution for configuration: {}", configuration.getName());
            
            // Get all dependencies in this configuration
            configuration.getAllDependencies().forEach(dependency -> {
                String group = dependency.getGroup();
                String name = dependency.getName();
                String version = dependency.getVersion();
                
                if (group != null && name != null && version != null) {
                    logger.debug("Checking dependency: {}:{}:{}", group, name, version);
                    
                    // Try to resolve from each configured OCI repository
                    tryResolveFromOciRepositories(project, group, name, version);
                } else {
                    logger.debug("Skipping dependency with null coordinates: {} {} {}", group, name, version);
                }
            });
            
        } catch (Exception e) {
            logger.debug("Error during OCI dependency resolution interception", e);
            // Don't fail the build - just log the error and let standard resolution proceed
        }
    }
    
    /**
     * Attempts to resolve an artifact from configured OCI repositories.
     * 
     * @param project The Gradle project
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     */
    private static void tryResolveFromOciRepositories(Project project, String groupId, String artifactId, String version) {
        try {
            // Look for OCI repository configurations in project extensions
            project.getExtensions().getExtraProperties().getProperties().entrySet().stream()
                .filter(entry -> entry.getKey().toString().endsWith("_oci_resolver"))
                .forEach(entry -> {
                    String repositoryName = entry.getKey().toString().replace("_oci_resolver", "");
                    logger.debug("Trying to resolve {}:{}:{} from OCI repository: {}", 
                               groupId, artifactId, version, repositoryName);
                    
                    boolean resolved = OciMavenRepositoryFactory.resolveArtifactOnDemand(
                        project, repositoryName, groupId, artifactId, version);
                    
                    if (resolved) {
                        logger.info("Successfully resolved {}:{}:{} from OCI repository: {}", 
                                   groupId, artifactId, version, repositoryName);
                    } else {
                        logger.debug("Failed to resolve {}:{}:{} from OCI repository: {}", 
                                    groupId, artifactId, version, repositoryName);
                    }
                });
                
        } catch (Exception e) {
            logger.debug("Error trying to resolve {}:{}:{} from OCI repositories", 
                        groupId, artifactId, version, e);
        }
    }
}