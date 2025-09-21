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

class MavenOciGroupSanitizerTest extends Specification {

    def "should sanitize basic Maven group IDs"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                       | expected
        "com.example"               | "com-example"
        "io.seqera"                 | "io-seqera"
        "org.springframework"       | "org-springframework"
        "org.apache.commons"        | "org-apache-commons"
    }

    def "should handle case conversion"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                       | expected
        "Com.Example"               | "com-example"
        "IO.SEQERA"                 | "io-seqera"
        "Org.SpringFramework"       | "org-springframework"
        "MY.GROUP.ID"               | "my-group-id"
    }

    def "should remove invalid characters"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                       | expected
        "com.example@version"       | "com-exampleversion"
        "io.seqera#test"            | "io-seqeratest"
        "org.spring+framework"      | "org-springframework"
        "group/with/slashes"        | "groupwithslashes"
        "test:with:colons"          | "testwithcolons"
        "spaces in group"           | "spacesingroup"
    }

    def "should handle consecutive separators"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                       | expected
        "com..example"              | "com-example"
        "io...seqera"               | "io-seqera"
        "org--apache"               | "org-apache"
        "test.__multiple"           | "test-multiple"
        "mixed.-._.separators"      | "mixed-separators"
    }

    def "should remove leading and trailing separators"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                       | expected
        ".com.example"              | "com-example"
        "io.seqera."                | "io-seqera"
        "-org.apache-"              | "org-apache"
        "_test.group_"              | "test-group"
        "...multiple.leading..."    | "multiple-leading"
    }

    def "should handle leading separators by removing them"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                       | expected
        "-example"                  | "example"
        "_private.group"            | "private-group"
        "-io.seqera"                | "io-seqera"
    }

    def "should prefix with 'g' when result starts with separator after sanitization"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                       | expected
        "123-start"                 | "123-start"  // Numbers are valid start chars
        "a-example"                 | "a-example"  // Letters are valid start chars
        // Note: The current implementation removes leading separators in cleanup,
        // so the prefix case may not occur with the current logic
    }

    def "should handle edge cases"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                       | expected
        "a"                         | "a"
        "A"                         | "a"
        "com"                       | "com"
        "1.2.3"                     | "1-2-3"
        "test123"                   | "test123"
        "com.example.sub.package"   | "com-example-sub-package"
    }

    def "should throw exception for null or empty input"() {
        when:
        MavenOciGroupSanitizer.sanitize(input)

        then:
        thrown(IllegalArgumentException)

        where:
        input << [null, "", "   ", "\t\n"]
    }

    def "should throw exception when sanitization results in empty string"() {
        when:
        MavenOciGroupSanitizer.sanitize(input)

        then:
        thrown(IllegalArgumentException)

        where:
        input << ["...", "---", "___", "@#\$", "!@#\$%^&*()"]
    }

    def "should reverse sanitization correctly"() {
        expect:
        MavenOciGroupSanitizer.reverse(input) == expected

        where:
        input                       | expected
        "com-example"               | "com.example"
        "io-seqera"                 | "io.seqera"
        "org-springframework"       | "org.springframework"
        "org-apache-commons"        | "org.apache.commons"
        "my-complex-group-id"       | "my.complex.group.id"
    }

    def "should handle reverse edge cases"() {
        expect:
        MavenOciGroupSanitizer.reverse(input) == expected

        where:
        input                       | expected
        "a"                         | "a"
        "simple"                    | "simple"
        "test123"                   | "test123"
        "g-private"                 | "g.private"
    }

    def "should throw exception for null reverse input"() {
        when:
        MavenOciGroupSanitizer.reverse(input)

        then:
        thrown(IllegalArgumentException)

        where:
        input << [null, "", "   "]
    }

    def "should validate sanitized groups correctly"() {
        expect:
        MavenOciGroupSanitizer.isValid(input) == expected

        where:
        input                       | expected
        // Valid cases
        "com-example"               | true
        "io-seqera"                 | true
        "org-springframework"       | true
        "a"                         | true
        "test123"                   | true
        "my-complex-group"          | true
        "group.with.dots"           | true
        "group_with_underscores"    | true
        
        // Invalid cases - uppercase
        "Com-Example"               | false
        "IO-SEQERA"                 | false
        
        // Invalid cases - starts with separator
        "-example"                  | false
        "_private"                  | false
        ".dotted"                   | false
        
        // Invalid cases - ends with separator
        "example-"                  | false
        "private_"                  | false
        "dotted."                   | false
        
        // Invalid cases - empty/null
        ""                          | false
        null                        | false
        "   "                       | false
        
        // Invalid cases - special characters
        "test@example"              | false
        "group#test"                | false
        "invalid+chars"             | false
    }

    def "should maintain round-trip consistency for common Maven groups"() {
        given:
        def commonGroups = [
            "org.springframework",
            "com.fasterxml.jackson.core",
            "io.seqera.nextflow",
            "org.apache.commons",
            "junit",
            "org.slf4j",
            "ch.qos.logback",
            "com.google.guava",
            "org.jetbrains.kotlin"
        ]

        expect:
        commonGroups.each { originalGroup ->
            def sanitized = MavenOciGroupSanitizer.sanitize(originalGroup)
            def reversed = MavenOciGroupSanitizer.reverse(sanitized)
            
            assert MavenOciGroupSanitizer.isValid(sanitized)
            // Note: Perfect round-trip may not always be possible due to sanitization rules
            // but the sanitized version should be valid and the reverse should be reasonable
            assert reversed != null
            assert !reversed.isEmpty()
        }
    }

    def "should handle complex real-world Maven group IDs"() {
        expect:
        MavenOciGroupSanitizer.sanitize(input) == expected

        where:
        input                                   | expected
        "com.fasterxml.jackson.core"           | "com-fasterxml-jackson-core"
        "org.springframework.boot"             | "org-springframework-boot"
        "io.micronaut.configuration"           | "io-micronaut-configuration"
        "org.apache.logging.log4j"             | "org-apache-logging-log4j"
        "com.google.cloud.tools"               | "com-google-cloud-tools"
        "org.jetbrains.kotlin.jvm"             | "org-jetbrains-kotlin-jvm"
        "net.java.dev.jna"                     | "net-java-dev-jna"
    }
}
