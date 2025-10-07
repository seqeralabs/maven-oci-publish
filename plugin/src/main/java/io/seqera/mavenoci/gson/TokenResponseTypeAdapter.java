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

package io.seqera.mavenoci.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Custom Gson TypeAdapter for ORAS TokenResponse record.
 *
 * <p>This adapter handles deserialization of the TokenResponse record from ORAS Java SDK,
 * which Gson cannot deserialize by default because records have final fields.</p>
 *
 * <p>The TokenResponse record has the following structure:</p>
 * <pre>
 * record TokenResponse(
 *     String token,
 *     String access_token,
 *     Integer expires_in,
 *     ZonedDateTime issued_at
 * )
 * </pre>
 *
 * @see land.oras.auth.HttpClient.TokenResponse
 */
public class TokenResponseTypeAdapter extends TypeAdapter<Object> {

    private static final Class<?> TOKEN_RESPONSE_CLASS;
    private static final Constructor<?> TOKEN_RESPONSE_CONSTRUCTOR;

    static {
        try {
            // Load the TokenResponse record class from ORAS SDK
            TOKEN_RESPONSE_CLASS = Class.forName("land.oras.auth.HttpClient$TokenResponse");

            // Get the canonical constructor for the record
            TOKEN_RESPONSE_CONSTRUCTOR = TOKEN_RESPONSE_CLASS.getDeclaredConstructor(
                String.class,    // token
                String.class,    // access_token
                Integer.class,   // expires_in
                ZonedDateTime.class  // issued_at
            );
            TOKEN_RESPONSE_CONSTRUCTOR.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException("Failed to initialize TokenResponseTypeAdapter", e);
        }
    }

    @Override
    public void write(JsonWriter out, Object value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        try {
            out.beginObject();

            // Serialize using record component accessors
            var tokenMethod = value.getClass().getMethod("token");
            var accessTokenMethod = value.getClass().getMethod("access_token");
            var expiresInMethod = value.getClass().getMethod("expires_in");
            var issuedAtMethod = value.getClass().getMethod("issued_at");

            String token = (String) tokenMethod.invoke(value);
            if (token != null) {
                out.name("token").value(token);
            }

            String accessToken = (String) accessTokenMethod.invoke(value);
            if (accessToken != null) {
                out.name("access_token").value(accessToken);
            }

            Integer expiresIn = (Integer) expiresInMethod.invoke(value);
            if (expiresIn != null) {
                out.name("expires_in").value(expiresIn);
            }

            ZonedDateTime issuedAt = (ZonedDateTime) issuedAtMethod.invoke(value);
            if (issuedAt != null) {
                out.name("issued_at").value(issuedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }

            out.endObject();
        } catch (Exception e) {
            throw new IOException("Failed to serialize TokenResponse", e);
        }
    }

    @Override
    public Object read(JsonReader in) throws IOException {
        String token = null;
        String accessToken = null;
        Integer expiresIn = null;
        ZonedDateTime issuedAt = null;

        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "token":
                    token = in.nextString();
                    break;
                case "access_token":
                    accessToken = in.nextString();
                    break;
                case "expires_in":
                    expiresIn = in.nextInt();
                    break;
                case "issued_at":
                    String dateStr = in.nextString();
                    issuedAt = ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    break;
                default:
                    in.skipValue();
                    break;
            }
        }
        in.endObject();

        try {
            // Instantiate the record using its canonical constructor
            return TOKEN_RESPONSE_CONSTRUCTOR.newInstance(token, accessToken, expiresIn, issuedAt);
        } catch (Exception e) {
            throw new IOException("Failed to create TokenResponse instance", e);
        }
    }
}
