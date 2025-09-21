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

class OverwritePolicyTest extends Specification {

    def "should parse string values to enum correctly"() {
        when:
        def result = OverwritePolicy.fromString(input)

        then:
        result == expected

        where:
        input         | expected
        "fail"        | OverwritePolicy.FAIL
        "FAIL"        | OverwritePolicy.FAIL
        "error"       | OverwritePolicy.FAIL
        "ERROR"       | OverwritePolicy.FAIL
        "override"    | OverwritePolicy.OVERRIDE
        "OVERRIDE"    | OverwritePolicy.OVERRIDE
        "overwrite"   | OverwritePolicy.OVERRIDE
        "OVERWRITE"   | OverwritePolicy.OVERRIDE
        "replace"     | OverwritePolicy.OVERRIDE
        "REPLACE"     | OverwritePolicy.OVERRIDE
        "skip"        | OverwritePolicy.SKIP
        "SKIP"        | OverwritePolicy.SKIP
        "ignore"      | OverwritePolicy.SKIP
        "IGNORE"      | OverwritePolicy.SKIP
        null          | OverwritePolicy.FAIL
        ""            | OverwritePolicy.FAIL
        "  "          | OverwritePolicy.FAIL
    }

    def "should throw exception for invalid values"() {
        when:
        OverwritePolicy.fromString("invalid")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Invalid overwrite policy")
        ex.message.contains("invalid")
        ex.message.contains("fail, override, skip")
    }

    def "should provide human-readable descriptions"() {
        expect:
        OverwritePolicy.FAIL.description == "Fail if package already exists"
        OverwritePolicy.OVERRIDE.description == "Override existing package"
        OverwritePolicy.SKIP.description == "Skip if package already exists"
    }

    def "should have FAIL as default when parsing empty/null values"() {
        expect:
        OverwritePolicy.fromString(value) == OverwritePolicy.FAIL

        where:
        value << [null, "", "  ", "\t", "\n"]
    }
}