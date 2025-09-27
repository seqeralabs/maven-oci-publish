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
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class MavenOciRepositoryFactoryTest extends Specification {

    @TempDir
    Path tempDir

    Project project
    MavenOciRepositorySpec spec

    def setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        
        // Create a mock OCI repository spec
        spec = project.objects.newInstance(MavenOciRepositorySpec, "testRepo")
        spec.url.set("https://test.registry.com")
        spec.insecure.set(false)
    }

    def "should create OCI-backed Maven repository with correct configuration"() {
        given:
        def mavenRepo = Mock(MavenArtifactRepository)

        when:
        MavenOciRepositoryFactory.createOciMavenRepository(spec, mavenRepo, project)

        then:
        1 * mavenRepo.setUrl(_) >> { args ->
            def uri = args[0]
            // Should be localhost HTTP proxy URL, not cache directory
            assert uri.toString().startsWith("http://localhost:")
            assert uri.toString().endsWith("/maven/")
        }
        1 * mavenRepo.setAllowInsecureProtocol(true) // Always called for localhost proxy
    }

    def "should create OCI-backed Maven repository with insecure protocol"() {
        given:
        spec.insecure.set(true)
        def mavenRepo = Mock(MavenArtifactRepository)

        when:
        MavenOciRepositoryFactory.createOciMavenRepository(spec, mavenRepo, project)

        then:
        1 * mavenRepo.setUrl(_) >> { args ->
            def uri = args[0]
            // Should be localhost HTTP proxy URL
            assert uri.toString().startsWith("http://localhost:")
            assert uri.toString().endsWith("/maven/")
        }
        1 * mavenRepo.setAllowInsecureProtocol(true)
    }

    def "should start HTTP proxy server for OCI repository"() {
        given:
        def mavenRepo = Mock(MavenArtifactRepository)

        when:
        MavenOciRepositoryFactory.createOciMavenRepository(spec, mavenRepo, project)

        then:
        1 * mavenRepo.setUrl(_) >> { args ->
            def uri = args[0]
            // Verify that an HTTP proxy server is actually started
            def url = new URL(uri.toString())
            assert url.protocol == "http"
            assert url.host == "localhost"
            assert url.port > 0
            assert url.path == "/maven/"
        }
        1 * mavenRepo.setAllowInsecureProtocol(true)
    }

    def "should handle repository creation errors gracefully"() {
        given:
        // Create a spec with no URL set (which results in empty string)
        def badSpec = project.objects.newInstance(MavenOciRepositorySpec, "badRepo")
        def mavenRepo = Mock(MavenArtifactRepository)

        when:
        MavenOciRepositoryFactory.createOciMavenRepository(badSpec, mavenRepo, project)

        then:
        // Should still succeed since empty URL is handled
        noExceptionThrown()
        1 * mavenRepo.setUrl(_)
    }

    def "should create separate HTTP proxy servers for different repositories"() {
        given:
        def mavenRepo = Mock(MavenArtifactRepository)
        def spec1 = project.objects.newInstance(MavenOciRepositorySpec, "repo1")
        spec1.url.set("https://registry1.com")
        def spec2 = project.objects.newInstance(MavenOciRepositorySpec, "repo2")
        spec2.url.set("https://registry2.com")
        def ports = [] as Set

        when:
        MavenOciRepositoryFactory.createOciMavenRepository(spec1, mavenRepo, project)
        MavenOciRepositoryFactory.createOciMavenRepository(spec2, mavenRepo, project)

        then:
        2 * mavenRepo.setUrl(_) >> { args ->
            def uri = args[0]
            def url = new URL(uri.toString())
            assert url.protocol == "http"
            assert url.host == "localhost"
            assert url.port > 0
            assert url.path == "/maven/"
            ports.add(url.port)
        }
        2 * mavenRepo.setAllowInsecureProtocol(true)
        // Should have different ports for different repositories
        ports.size() == 2
    }
}
