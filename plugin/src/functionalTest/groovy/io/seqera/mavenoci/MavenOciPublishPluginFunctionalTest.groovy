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
 * Maven OCI Publish Plugin Functional Test
 */
package io.seqera.mavenoci

import spock.lang.Specification
import spock.lang.TempDir
import org.gradle.testkit.runner.GradleRunner

/**
 * A simple functional test for the Maven OCI Publish plugin.
 */
class MavenOciPublishPluginFunctionalTest extends Specification {
    @TempDir
    private File projectDir

    private getBuildFile() {
        new File(projectDir, "build.gradle")
    }

    private getSettingsFile() {
        new File(projectDir, "settings.gradle")
    }

    def "can run lifecycle task"() {
        given:
        settingsFile << ""
        buildFile << """
plugins {
    id('io.seqera.maven-oci-publish')
}
"""

        when:
        def runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("publishToOciRegistries")
        runner.withProjectDir(projectDir)
        def result = runner.build()

        then:
        result.output.contains("BUILD SUCCESSFUL")
        result.task(":publishToOciRegistries") != null
    }
}
