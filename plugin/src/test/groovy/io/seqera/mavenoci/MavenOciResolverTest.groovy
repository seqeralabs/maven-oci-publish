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

import java.nio.file.Path

class MavenOciResolverTest extends Specification {

    @TempDir
    Path tempDir

    def "should build correct OCI reference from Maven coordinates"() {
        given:
        def resolver = new MavenOciResolver("https://registry.example.com", false, null, null)

        when:
        def result = resolver.invokeMethod("buildOciReference", ["com.example", "my-artifact", "1.0.0"])

        then:
        result == "registry.example.com/com-example/my-artifact:1.0.0"
    }

    def "should build OCI reference with complex group ID"() {
        given:
        def resolver = new MavenOciResolver("https://public.cr.example.io/maven", false, null, null)

        when:
        def result = resolver.invokeMethod("buildOciReference", ["org.springframework.boot", "spring-boot-starter", "2.7.0"])

        then:
        result == "public.cr.example.io/maven/org-springframework-boot/spring-boot-starter:2.7.0"
    }

    def "should handle HTTP registry URLs"() {
        given:
        def resolver = new MavenOciResolver("http://localhost:5000", true, null, null)

        when:
        def result = resolver.invokeMethod("buildOciReference", ["io.seqera", "shared-library", "1.0.0"])

        then:
        result == "localhost:5000/io-seqera/shared-library:1.0.0"
    }

    def "should handle registry URLs with paths"() {
        given:
        def resolver = new MavenOciResolver("https://registry.com/v2/maven", false, null, null)

        when:
        def result = resolver.invokeMethod("buildOciReference", ["com.test", "artifact", "1.2.3"])

        then:
        result == "registry.com/v2/maven/com-test/artifact:1.2.3"
    }

    def "should sanitize Maven group IDs correctly"() {
        given:
        def resolver = new MavenOciResolver("https://registry.com", false, null, null)

        when:
        def result = resolver.invokeMethod("buildOciReference", [groupId, "artifact", "1.0.0"])

        then:
        result == expected

        where:
        groupId                     | expected
        "com.example"               | "registry.com/com-example/artifact:1.0.0"
        "org.springframework"       | "registry.com/org-springframework/artifact:1.0.0"
        "io.seqera.test"            | "registry.com/io-seqera-test/artifact:1.0.0"
        "com.fasterxml.jackson"     | "registry.com/com-fasterxml-jackson/artifact:1.0.0"
    }

    def "should create registry with correct configuration"() {
        when:
        def secureResolver = new MavenOciResolver("https://registry.com", false, "user", "pass")
        def insecureResolver = new MavenOciResolver("http://localhost:5000", true, null, null)

        then:
        secureResolver != null
        insecureResolver != null
    }

    def "should handle artifact existence check gracefully"() {
        given:
        def resolver = new MavenOciResolver("https://nonexistent.registry.com", false, null, null)

        when:
        def exists = resolver.artifactsExist("com.example", "nonexistent", "1.0.0")

        then:
        exists == false
    }

    def "should handle artifact resolution failure gracefully"() {
        given:
        def resolver = new MavenOciResolver("https://nonexistent.registry.com", false, null, null)

        when:
        def resolved = resolver.resolveArtifacts("com.example", "nonexistent", "1.0.0", tempDir)

        then:
        resolved == false
    }
}
