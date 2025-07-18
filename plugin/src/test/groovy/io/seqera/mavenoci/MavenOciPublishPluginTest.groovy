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
 * A simple unit test for the Maven OCI Publish plugin.
 */
class MavenOciPublishPluginTest extends Specification {
    def "plugin registers extension and lifecycle task"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.plugins.apply("io.seqera.maven-oci-publish")

        then:
        project.extensions.findByName("mavenOci") != null
        project.tasks.findByName("publishToOciRegistries") != null
    }
}
