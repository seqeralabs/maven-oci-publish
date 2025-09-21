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
 * Maven OCI Publish Plugin Test
 */
package io.seqera.mavenoci

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import spock.lang.Specification

/**
 * Unit tests for the Maven OCI Publish plugin using Gradle's ProjectBuilder.
 * These tests validate the basic plugin functionality without running actual builds.
 * 
 * Test Coverage:
 * - Plugin registration and extension creation
 * - Task creation and lifecycle integration
 * - DSL extension availability
 */
class MavenOciPublishPluginTest extends Specification {
    
    def "plugin registers extension and lifecycle task"() {
        given: "A fresh Gradle project created using ProjectBuilder"
        // ProjectBuilder creates a minimal Gradle project for testing
        // without the overhead of a full build environment
        def project = ProjectBuilder.builder().build()

        when: "The Maven OCI Publish plugin is applied to the project"
        // This simulates applying the plugin via build.gradle:
        // plugins { id 'io.seqera.maven-oci-publish' }
        project.plugins.apply("io.seqera.maven-oci-publish")

        then: "The plugin should create the 'oci' DSL extension"
        // Validates that the plugin registers its DSL extension properly
        // This extension allows users to configure: oci { publications { ... } repositories { ... } }
        project.extensions.findByName("oci") != null
        
        and: "The plugin should create the lifecycle task 'publishToOciRegistries'"
        // Validates that the plugin creates the main lifecycle task
        // This task serves as an umbrella task that depends on all individual publishing tasks
        // Users can run: ./gradlew publishToOciRegistries
        project.tasks.findByName("publishToOciRegistries") != null
    }
}
