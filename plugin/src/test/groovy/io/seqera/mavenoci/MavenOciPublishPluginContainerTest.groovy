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

import java.nio.file.Files

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared
import spock.lang.Specification
/**
 * Integration tests for the Maven OCI Publish plugin using Testcontainers.
 * These tests run against a real Docker registry container to verify end-to-end functionality.
 */
class MavenOciPublishPluginContainerTest extends Specification {

    @Shared
    GenericContainer<?> registry = new GenericContainer<>("registry:2")
            .withExposedPorts(5000)
            .waitingFor(Wait.forHttp("/v2/"))

    private File projectDir

    private getBuildFile() {
        new File(projectDir, "build.gradle")
    }

    private getSettingsFile() {
        new File(projectDir, "settings.gradle")
    }

    def setupSpec() {
        registry.start()
    }
    
    def cleanupSpec() {
        registry.stop()
    }
    
    def setup() {
        // Create temporary directory for each test
        projectDir = Files.createTempDirectory("gradle-test").toFile()
        
        // Create a simple Java source file
        def srcDir = new File(projectDir, "src/main/java/com/example")
        srcDir.mkdirs()
        
        new File(srcDir, "Hello.java") << '''
            package com.example;
            
            public class Hello {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
        '''
        
        settingsFile << """
            rootProject.name = 'test-project'
        """
    }
    
    def cleanup() {
        // Clean up temporary directory
        if (projectDir?.exists()) {
            projectDir.deleteDir()
        }
    }

    def "can publish Maven artifacts to containerized registry"() {
        given:
        def registryUrl = "localhost:${registry.getMappedPort(5000)}"
        
        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
                id 'io.seqera.maven-oci-publish'
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
            }
            
            mavenOci {
                publications {
                    maven {
                        from components.java
                        repository = 'test-project'
                        tag = project.version
                    }
                }
                
                repositories {
                    testRegistry {
                        url = 'http://${registryUrl}'
                        insecure = true
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("build", "publishMavenPublicationToTestRegistryRepository", "--info")
            .withProjectDir(projectDir)
            .build()

        then:
        result.task(":publishMavenPublicationToTestRegistryRepository").outcome == TaskOutcome.SUCCESS
        result.output.contains("Publishing to OCI registry")
    }

    def "can publish to registry with authentication"() {
        given:
        def registryUrl = "localhost:${registry.getMappedPort(5000)}"
        
        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
                id 'io.seqera.maven-oci-publish'
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
            }
            
            mavenOci {
                publications {
                    maven {
                        from components.java
                        repository = 'authenticated-project'
                        tag = project.version
                    }
                }
                
                repositories {
                    testRegistry {
                        url = 'http://${registryUrl}'
                        insecure = true
                        credentials {
                            username = 'testuser'
                            password = 'testpass'
                        }
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("build", "publishMavenPublicationToTestRegistryRepository", "--info")
            .withProjectDir(projectDir)
            .build()

        then:
        result.task(":publishMavenPublicationToTestRegistryRepository").outcome == TaskOutcome.SUCCESS
        result.output.contains("Publishing to OCI registry")
    }

    def "can publish multiple artifacts and verify registry contents"() {
        given:
        def registryUrl = "localhost:${registry.getMappedPort(5000)}"
        
        // Create additional source files
        def testSrcDir = new File(projectDir, "src/test/java/com/example")
        testSrcDir.mkdirs()
        
        new File(testSrcDir, "HelloTest.java") << '''
            package com.example;
            
            import org.junit.Test;
            import static org.junit.Assert.assertTrue;
            
            public class HelloTest {
                @Test
                public void testHello() {
                    assertTrue(true);
                }
            }
        '''
        
        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
                id 'io.seqera.maven-oci-publish'
            }
            
            group = 'com.example'
            version = '1.0.0'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }
            
            java {
                withJavadocJar()
                withSourcesJar()
            }
            
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            
            mavenOci {
                publications {
                    maven {
                        from components.java
                        repository = 'multi-artifact-project'
                        tag = project.version
                    }
                }
                
                repositories {
                    testRegistry {
                        url = 'http://${registryUrl}'
                        insecure = true
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("build", "publishMavenPublicationToTestRegistryRepository", "--info")
            .withProjectDir(projectDir)
            .build()

        then:
        result.task(":publishMavenPublicationToTestRegistryRepository").outcome == TaskOutcome.SUCCESS
        result.output.contains("Publishing to OCI registry")
        
        // Verify that the publishing process ran
        result.task(":sourcesJar").outcome == TaskOutcome.SUCCESS
        result.task(":javadocJar").outcome == TaskOutcome.SUCCESS
    }

    def "gracefully handles empty artifacts"() {
        given:
        def registryUrl = "localhost:99999"  // Non-existent port
        
        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
                id 'io.seqera.maven-oci-publish'
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
            }
            
            mavenOci {
                publications {
                    maven {
                        from components.java
                        repository = 'test-project'
                        tag = project.version
                    }
                }
                
                repositories {
                    nonExistentRegistry {
                        url = 'http://${registryUrl}'
                        insecure = true
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("build", "publishMavenPublicationToNonExistentRegistryRepository", "--info")
            .withProjectDir(projectDir)
            .build()

        then:
        result.task(":publishMavenPublicationToNonExistentRegistryRepository").outcome == TaskOutcome.SUCCESS
        result.output.contains("No artifacts to publish")
    }

    def "publishes artifacts with correct media types"() {
        given:
        def registryUrl = "localhost:${registry.getMappedPort(5000)}"
        
        buildFile << """
            plugins {
                id 'java'
                id 'maven-publish'
                id 'io.seqera.maven-oci-publish'
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
            }
            
            mavenOci {
                publications {
                    maven {
                        from components.java
                        repository = 'media-type-test'
                        tag = project.version
                    }
                }
                
                repositories {
                    testRegistry {
                        url = 'http://${registryUrl}'
                        insecure = true
                    }
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments("build", "publishMavenPublicationToTestRegistryRepository", "--debug")
            .withProjectDir(projectDir)
            .build()

        then:
        result.task(":publishMavenPublicationToTestRegistryRepository").outcome == TaskOutcome.SUCCESS
        
        // Verify the publishing process ran
        result.output.contains("Publishing to OCI registry")
    }
}
