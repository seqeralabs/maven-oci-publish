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

package io.seqera.mavenoci

import spock.lang.Specification
import spock.lang.TempDir
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

/**
 * Integration test for the Maven OCI Publish plugin with a more realistic scenario.
 */
class MavenOciPublishPluginIntegrationTest extends Specification {
    @TempDir
    private File projectDir

    private getBuildFile() {
        new File(projectDir, "build.gradle")
    }

    private getSettingsFile() {
        new File(projectDir, "settings.gradle")
    }

    def "plugin creates tasks for publication-repository combinations"() {
        given:
        settingsFile << """
            rootProject.name = 'test-project'
        """
        
        buildFile << """
            plugins {
                id 'java'
                id 'io.seqera.maven-oci-registry'
            }
            
            group = 'com.example'
            version = '1.0.0'
            
            repositories {
                mavenCentral()
            }
            
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
                
                repositories {
                    mavenOci {
                        name = 'registry1'
                        url = 'https://registry1.example.com/maven'
                        insecure = false
                    }
                    
                    mavenOci {
                        name = 'registry2'
                        url = 'https://registry2.example.com/maven'
                        insecure = false
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("tasks", "--group=publishing")
            .withProjectDir(projectDir)
            .build()

        then:
        result.output.contains("publishToOciRegistries")
        // Repository names use explicit names
        result.output.contains("publishMavenPublicationToRegistry1Repository")
        result.output.contains("publishMavenPublicationToRegistry2Repository")
    }
    
    def "plugin integrates with maven-publish plugin"() {
        given:
        settingsFile << """
            rootProject.name = 'test-project'
        """
        
        buildFile << """
            plugins {
                id 'java'
                id 'io.seqera.maven-oci-registry'
            }
            
            group = 'com.example'
            version = '1.0.0'
            
            repositories {
                mavenCentral()
            }
            
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
                
                repositories {
                    mavenOci {
                        name = 'example'
                        url = 'https://example.com/maven'
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("help")
            .withProjectDir(projectDir)
            .build()

        then:
        result.task(":help").outcome == TaskOutcome.SUCCESS
    }
    
    def "plugin handles empty configuration gracefully"() {
        given:
        settingsFile << """
            rootProject.name = 'test-project'
        """
        
        buildFile << """
            plugins {
                id 'java'
                id 'io.seqera.maven-oci-registry'
            }
            
            // Plugin now integrates with publishing.repositories
            // No separate oci block needed
        """

        when:
        def result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("publishToOciRegistries")
            .withProjectDir(projectDir)
            .build()

        then:
        result.task(":publishToOciRegistries").outcome == TaskOutcome.UP_TO_DATE
    }
}
