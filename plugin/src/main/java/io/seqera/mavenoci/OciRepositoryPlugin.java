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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.GradleVersion;

/**
 * Plugin for consuming Maven artifacts from OCI registries.
 * This plugin enables the ociRepositories DSL block for configuring OCI registries
 * as Maven repositories in Gradle builds.
 */
public class OciRepositoryPlugin implements Plugin<Project> {
    
    private static final Logger logger = Logging.getLogger(OciRepositoryPlugin.class);
    
    @Override
    public void apply(Project project) {
        // Check minimum Gradle version
        if (GradleVersion.current().compareTo(GradleVersion.version("6.0")) < 0) {
            throw new IllegalStateException("OCI repository plugin requires Gradle 6.0 or later");
        }
        
        logger.info("Applying OCI repository plugin to project: {}", project.getName());
        
        // Create the OCI repository handler
        OciRepositoryHandler ociHandler = project.getObjects().newInstance(
            OciRepositoryHandler.class, 
            project.getObjects()
        );
        
        // Add as a project extension
        project.getExtensions().add("ociRepositories", ociHandler);
        
        logger.info("OCI repository plugin successfully applied. Use 'ociRepositories' to configure OCI registries.");
    }
}