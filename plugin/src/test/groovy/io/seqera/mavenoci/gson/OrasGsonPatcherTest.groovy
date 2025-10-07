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

package io.seqera.mavenoci.gson

import com.google.gson.Gson
import spock.lang.Specification

import java.lang.reflect.Field
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Test suite for OrasGsonPatcher and TokenResponseTypeAdapter.
 *
 * Validates that the Gson patcher correctly handles ORAS TokenResponse record
 * deserialization, which fails with standard Gson due to final field restrictions.
 */
class OrasGsonPatcherTest extends Specification {

    def "should fail to deserialize TokenResponse without custom TypeAdapter - demonstrating the problem"() {
        given: "a JSON string representing a TokenResponse"
        def json = '''
        {
            "token": "test-token-123",
            "access_token": "access-456",
            "expires_in": 3600
        }
        '''

        and: "a vanilla Gson instance WITHOUT the custom TypeAdapter"
        Gson vanillaGson = new Gson()

        and: "the TokenResponse class"
        Class<?> tokenResponseClass = Class.forName("land.oras.auth.HttpClient\$TokenResponse")

        when: "deserializing the JSON with vanilla Gson (no TypeAdapter)"
        vanillaGson.fromJson(json, tokenResponseClass)

        then: "an exception is thrown because Gson cannot deserialize records"
        def ex = thrown(Exception)

        and: "the root cause indicates module access issues or final field restrictions"
        def rootCause = getRootCause(ex)
        // In Java 16+, the error is typically InaccessibleObjectException when trying to access ZonedDateTime
        // OR IllegalAccessException when trying to set final fields in the record
        (rootCause instanceof IllegalAccessException ||
         rootCause.class.name.contains("InaccessibleObjectException"))

        and: "the error message indicates the underlying problem"
        rootCause.message != null
    }

    def "should patch ORAS JsonUtils with custom Gson instance"() {
        when: "patching ORAS JsonUtils"
        OrasGsonPatcher.patchOrasGson()

        then: "no exception is thrown"
        noExceptionThrown()

        and: "JsonUtils.gson field is accessible"
        Class<?> jsonUtilsClass = Class.forName("land.oras.utils.JsonUtils")
        Field gsonField = jsonUtilsClass.getDeclaredField("gson")
        gsonField.setAccessible(true)
        Gson patchedGson = (Gson) gsonField.get(null)

        and: "patched Gson is not null"
        patchedGson != null
    }

    /**
     * Helper method to get the root cause of an exception chain
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable
        while (cause.cause != null && cause.cause != cause) {
            cause = cause.cause
        }
        return cause
    }

    def "should be idempotent - multiple calls don't cause errors"() {
        when: "patching multiple times"
        OrasGsonPatcher.patchOrasGson()
        OrasGsonPatcher.patchOrasGson()
        OrasGsonPatcher.patchOrasGson()

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "should deserialize TokenResponse JSON with all fields"() {
        given: "a JSON string representing a TokenResponse"
        def json = '''
        {
            "token": "test-token-123",
            "access_token": "access-456",
            "expires_in": 3600,
            "issued_at": "2025-10-07T10:30:00+02:00"
        }
        '''

        and: "ORAS is patched"
        OrasGsonPatcher.patchOrasGson()

        and: "we get the patched Gson instance"
        Class<?> jsonUtilsClass = Class.forName("land.oras.utils.JsonUtils")
        Field gsonField = jsonUtilsClass.getDeclaredField("gson")
        gsonField.setAccessible(true)
        Gson gson = (Gson) gsonField.get(null)

        and: "the TokenResponse class"
        Class<?> tokenResponseClass = Class.forName("land.oras.auth.HttpClient\$TokenResponse")

        when: "deserializing the JSON"
        def tokenResponse = gson.fromJson(json, tokenResponseClass)

        then: "the TokenResponse is created successfully"
        tokenResponse != null

        and: "all fields are correctly populated"
        def tokenMethod = tokenResponseClass.getMethod("token")
        def accessTokenMethod = tokenResponseClass.getMethod("access_token")
        def expiresInMethod = tokenResponseClass.getMethod("expires_in")
        def issuedAtMethod = tokenResponseClass.getMethod("issued_at")

        tokenMethod.invoke(tokenResponse) == "test-token-123"
        accessTokenMethod.invoke(tokenResponse) == "access-456"
        expiresInMethod.invoke(tokenResponse) == 3600
        issuedAtMethod.invoke(tokenResponse) != null
    }

    def "should deserialize TokenResponse JSON with minimal fields"() {
        given: "a JSON string with only token field"
        def json = '{"token": "minimal-token"}'

        and: "ORAS is patched"
        OrasGsonPatcher.patchOrasGson()

        and: "we get the patched Gson instance"
        Class<?> jsonUtilsClass = Class.forName("land.oras.utils.JsonUtils")
        Field gsonField = jsonUtilsClass.getDeclaredField("gson")
        gsonField.setAccessible(true)
        Gson gson = (Gson) gsonField.get(null)

        and: "the TokenResponse class"
        Class<?> tokenResponseClass = Class.forName("land.oras.auth.HttpClient\$TokenResponse")

        when: "deserializing the JSON"
        def tokenResponse = gson.fromJson(json, tokenResponseClass)

        then: "the TokenResponse is created successfully"
        tokenResponse != null

        and: "token field is populated"
        def tokenMethod = tokenResponseClass.getMethod("token")
        tokenMethod.invoke(tokenResponse) == "minimal-token"

        and: "optional fields are null"
        def accessTokenMethod = tokenResponseClass.getMethod("access_token")
        def expiresInMethod = tokenResponseClass.getMethod("expires_in")
        def issuedAtMethod = tokenResponseClass.getMethod("issued_at")

        accessTokenMethod.invoke(tokenResponse) == null
        expiresInMethod.invoke(tokenResponse) == null
        issuedAtMethod.invoke(tokenResponse) == null
    }

    def "should serialize TokenResponse back to JSON"() {
        given: "ORAS is patched"
        OrasGsonPatcher.patchOrasGson()

        and: "we get the patched Gson instance"
        Class<?> jsonUtilsClass = Class.forName("land.oras.utils.JsonUtils")
        Field gsonField = jsonUtilsClass.getDeclaredField("gson")
        gsonField.setAccessible(true)
        Gson gson = (Gson) gsonField.get(null)

        and: "a TokenResponse instance"
        Class<?> tokenResponseClass = Class.forName("land.oras.auth.HttpClient\$TokenResponse")
        def constructor = tokenResponseClass.getDeclaredConstructor(
            String.class,
            String.class,
            Integer.class,
            ZonedDateTime.class
        )
        constructor.setAccessible(true)
        def issuedAt = ZonedDateTime.parse("2025-10-07T10:30:00+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        def tokenResponse = constructor.newInstance("test-token", "access-token", 7200, issuedAt)

        when: "serializing to JSON"
        def json = gson.toJson(tokenResponse)

        then: "JSON contains all fields"
        json.contains('"token":"test-token"')
        json.contains('"access_token":"access-token"')
        json.contains('"expires_in":7200')
        json.contains('"issued_at":"2025-10-07T10:30:00+02:00"')
    }

    def "should handle round-trip serialization/deserialization"() {
        given: "ORAS is patched"
        OrasGsonPatcher.patchOrasGson()

        and: "we get the patched Gson instance"
        Class<?> jsonUtilsClass = Class.forName("land.oras.utils.JsonUtils")
        Field gsonField = jsonUtilsClass.getDeclaredField("gson")
        gsonField.setAccessible(true)
        Gson gson = (Gson) gsonField.get(null)

        and: "a TokenResponse instance"
        Class<?> tokenResponseClass = Class.forName("land.oras.auth.HttpClient\$TokenResponse")
        def constructor = tokenResponseClass.getDeclaredConstructor(
            String.class,
            String.class,
            Integer.class,
            ZonedDateTime.class
        )
        constructor.setAccessible(true)
        def originalIssuedAt = ZonedDateTime.parse("2025-10-07T12:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        def original = constructor.newInstance("original-token", "original-access", 1800, originalIssuedAt)

        when: "round-tripping through JSON"
        def json = gson.toJson(original)
        def deserialized = gson.fromJson(json, tokenResponseClass)

        then: "deserialized object has same field values"
        def tokenMethod = tokenResponseClass.getMethod("token")
        def accessTokenMethod = tokenResponseClass.getMethod("access_token")
        def expiresInMethod = tokenResponseClass.getMethod("expires_in")
        def issuedAtMethod = tokenResponseClass.getMethod("issued_at")

        tokenMethod.invoke(deserialized) == "original-token"
        accessTokenMethod.invoke(deserialized) == "original-access"
        expiresInMethod.invoke(deserialized) == 1800

        // Compare ZonedDateTime values (they should be equal)
        ZonedDateTime deserializedIssuedAt = issuedAtMethod.invoke(deserialized) as ZonedDateTime
        deserializedIssuedAt.isEqual(originalIssuedAt)
    }

    def "should handle ZonedDateTime deserialization correctly"() {
        given: "a JSON with ISO-formatted datetime"
        def json = '''
        {
            "token": "test",
            "issued_at": "2025-10-07T15:30:45.123+02:00"
        }
        '''

        and: "ORAS is patched"
        OrasGsonPatcher.patchOrasGson()

        and: "we get the patched Gson instance"
        Class<?> jsonUtilsClass = Class.forName("land.oras.utils.JsonUtils")
        Field gsonField = jsonUtilsClass.getDeclaredField("gson")
        gsonField.setAccessible(true)
        Gson gson = (Gson) gsonField.get(null)

        and: "the TokenResponse class"
        Class<?> tokenResponseClass = Class.forName("land.oras.auth.HttpClient\$TokenResponse")

        when: "deserializing the JSON"
        def tokenResponse = gson.fromJson(json, tokenResponseClass)

        then: "issued_at is properly deserialized"
        def issuedAtMethod = tokenResponseClass.getMethod("issued_at")
        ZonedDateTime issuedAt = issuedAtMethod.invoke(tokenResponse) as ZonedDateTime

        issuedAt != null
        issuedAt.year == 2025
        issuedAt.monthValue == 10
        issuedAt.dayOfMonth == 7
        issuedAt.hour == 15
        issuedAt.minute == 30
    }

    def "should ignore unknown JSON fields gracefully"() {
        given: "a JSON with extra unknown fields"
        def json = '''
        {
            "token": "test-token",
            "unknown_field": "should-be-ignored",
            "another_unknown": 12345
        }
        '''

        and: "ORAS is patched"
        OrasGsonPatcher.patchOrasGson()

        and: "we get the patched Gson instance"
        Class<?> jsonUtilsClass = Class.forName("land.oras.utils.JsonUtils")
        Field gsonField = jsonUtilsClass.getDeclaredField("gson")
        gsonField.setAccessible(true)
        Gson gson = (Gson) gsonField.get(null)

        and: "the TokenResponse class"
        Class<?> tokenResponseClass = Class.forName("land.oras.auth.HttpClient\$TokenResponse")

        when: "deserializing the JSON"
        def tokenResponse = gson.fromJson(json, tokenResponseClass)

        then: "TokenResponse is created successfully despite unknown fields"
        tokenResponse != null

        and: "known field is populated"
        def tokenMethod = tokenResponseClass.getMethod("token")
        tokenMethod.invoke(tokenResponse) == "test-token"
    }
}
