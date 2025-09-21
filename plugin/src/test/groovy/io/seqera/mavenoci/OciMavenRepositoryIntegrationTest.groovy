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

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Integration test for the new publishing.repositories.oci DSL syntax
 */
class OciMavenRepositoryIntegrationTest extends Specification {

    def "can use oci() method in publishing.repositories DSL"() {
        given: "A project with the Maven OCI plugin applied"
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        project.pluginManager.apply('io.seqera.maven-oci-publish')
        
        and: "Basic project configuration"
        project.group = 'com.example'
        project.version = '1.0.0'

        when: "We configure an OCI repository in publishing.repositories"
        project.extensions.configure(PublishingExtension) { publishing ->
            publishing.publications {
                maven(MavenPublication) {
                    from project.components.java
                }
            }
            
            publishing.repositories {
                maven {
                    name = 'central'
                    url = 'https://repo1.maven.org/maven2/'
                }
                
                // This should work with our new DSL
                oci('dockerRegistry') {
                    url = 'https://registry-1.docker.io'
                    namespace = 'maven'
                    credentials {
                        username = 'testuser'
                        password = 'testpass'
                    }
                }
            }
        }

        and: "Project is evaluated"
        project.evaluate()

        then: "The publishing extension should exist"
        project.extensions.getByType(PublishingExtension) != null
        
        and: "Both maven and oci repositories should be configured"
        def publishing = project.extensions.getByType(PublishingExtension)
        publishing.repositories.size() == 2
        publishing.repositories.findByName('central') != null
        publishing.repositories.findByName('dockerRegistry') != null
        
        and: "OCI repository should be of correct type"
        def ociRepo = publishing.repositories.findByName('dockerRegistry')
        ociRepo instanceof OciMavenRepository
        
        and: "OCI repository should have correct configuration"
        def ociMavenRepo = (OciMavenRepository) ociRepo
        ociMavenRepo.getUrl().toString() == 'https://registry-1.docker.io'
        ociMavenRepo.getNamespace().get() == 'maven'
        ociMavenRepo.hasCredentials()
        ociMavenRepo.credentials.username == 'testuser'
        ociMavenRepo.credentials.password == 'testpass'
    }

    def "generates correct OCI publishing tasks"() {
        given: "A project with Maven OCI plugin and OCI repository"
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        project.pluginManager.apply('io.seqera.maven-oci-publish')
        
        project.group = 'com.example'
        project.version = '2.0.0'

        when: "Configuring publishing with OCI repository"
        project.extensions.configure(PublishingExtension) { publishing ->
            publishing.publications {
                maven(MavenPublication) {
                    from project.components.java
                }
            }
            
            publishing.repositories {
                oci('testRegistry') {
                    url = 'https://test.registry.com'
                }
            }
        }
        
        project.evaluate()

        then: "Publishing tasks should be created"
        project.tasks.findByName('publishMavenPublicationToTestRegistryRepository') != null
        project.tasks.findByName('publishToOciRegistries') != null
        
        and: "Lifecycle task should depend on OCI publish task"
        def lifecycleTask = project.tasks.findByName('publishToOciRegistries')
        def publishTask = project.tasks.findByName('publishMavenPublicationToTestRegistryRepository')
        lifecycleTask.dependsOn.any { dep -> dep.toString().contains(publishTask.name) }
    }

    def "plugin applies without errors with new DSL"() {
        when: "Creating a project and applying the plugin"
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        project.pluginManager.apply('io.seqera.maven-oci-publish')
        
        project.group = 'com.example'
        project.version = '1.0.0'
        
        project.evaluate()

        then: "No exceptions are thrown"
        noExceptionThrown()
        
        and: "The plugin is applied"
        project.plugins.hasPlugin('io.seqera.maven-oci-publish')
        
        and: "Maven publish plugin is also applied"
        project.plugins.hasPlugin('maven-publish')
        
        and: "Publishing extension has OCI capabilities"
        def publishing = project.extensions.getByType(PublishingExtension)
        // OCI repositories are now supported via direct oci() method
        publishing.repositories != null
    }

    def "oci repository builds correct OCI references"() {
        given: "An OCI Maven repository"
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply('io.seqera.maven-oci-publish')
        def repo = project.objects.newInstance(OciMavenRepository, 'test')
        
        when: "Configuring with URL and namespace"
        repo.setUrl('https://registry.example.com')
        repo.getNamespace().set('maven')
        
        then: "Should build correct OCI reference"
        def reference = repo.buildOciReference('com.example', 'my-lib', '1.0.0')
        reference == 'registry.example.com/maven/com-example/my-lib:1.0.0'
    }

    def "supports insecure registries"() {
        given: "A project with insecure OCI repository"
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        project.pluginManager.apply('io.seqera.maven-oci-publish')
        
        when: "Configuring insecure OCI repository"
        project.extensions.configure(PublishingExtension) { publishing ->
            publishing.publications {
                maven(MavenPublication) {
                    from project.components.java
                }
            }
            
            publishing.repositories {
                oci('localRegistry') {
                    url = 'http://localhost:5000'
                    insecure = true
                }
            }
        }
        
        project.evaluate()

        then: "Repository should allow insecure protocol"
        def publishing = project.extensions.getByType(PublishingExtension)
        def ociRepo = (OciMavenRepository) publishing.repositories.findByName('localRegistry')
        ociRepo.isAllowInsecureProtocol() == true
        ociRepo.getInsecure().get() == true
    }
}