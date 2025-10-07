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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Patches ORAS Java SDK's JsonUtils to use a custom Gson instance with TokenResponse support.
 *
 * <h2>Problem</h2>
 * <p>ORAS Java SDK 0.2.15 uses Java records for {@code TokenResponse}, which Gson cannot
 * deserialize because records have final fields. Gson tries to use reflection to set these
 * final fields, which fails with {@code IllegalAccessException}.</p>
 *
 * <h2>Solution</h2>
 * <p>This patcher injects a custom Gson instance into ORAS's {@code JsonUtils} that includes
 * a TypeAdapter for TokenResponse, allowing proper deserialization via the record's constructor.</p>
 *
 * <h2>Implementation</h2>
 * <p>Uses {@code sun.misc.Unsafe} to modify the static final field. While Unsafe is an internal API,
 * it is NOT marked as {@code @Deprecated} and remains the only reliable way to modify static final
 * fields in Java 12+. Alternative approaches like VarHandle don't support modifying final fields.</p>
 *
 * <h2>Why Unsafe?</h2>
 * <ul>
 *   <li>VarHandle: Cannot modify final fields (throws UnsupportedOperationException)</li>
 *   <li>Field.setAccessible(): Doesn't work for final fields in Java 12+</li>
 *   <li>Unsafe: Internal but functional - the only working solution</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * // Call once before using ORAS Registry
 * OrasGsonPatcher.patchOrasGson();
 * </pre>
 *
 * @see TokenResponseTypeAdapter
 * @see land.oras.utils.JsonUtils
 */
public class OrasGsonPatcher {

    private static boolean patched = false;

    /**
     * Patches ORAS JsonUtils with a custom Gson instance that can handle TokenResponse records.
     *
     * <p>This method is idempotent - calling it multiple times has no effect after the first call.</p>
     *
     * @throws RuntimeException if patching fails
     */
    public static synchronized void patchOrasGson() {
        if (patched) {
            return;
        }

        try {
            // Create custom Gson with ZonedDateTime and TokenResponse support
            Class<?> tokenResponseClass = Class.forName("land.oras.auth.HttpClient$TokenResponse");

            Gson customGson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeTypeAdapter())
                .registerTypeAdapter(tokenResponseClass, new TokenResponseTypeAdapter())
                .disableHtmlEscaping()
                .create();

            // Use reflection to replace the static Gson instance in JsonUtils
            Class<?> jsonUtilsClass = Class.forName("land.oras.utils.JsonUtils");
            Field gsonField = jsonUtilsClass.getDeclaredField("gson");

            // Use Unsafe to bypass final modifier restrictions in Java 12+
            // Note: Unsafe is internal but NOT deprecated - it's the only way to modify static final fields
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

            // Get the base offset for the static field and set the value
            Object staticFieldBase = unsafe.staticFieldBase(gsonField);
            long staticFieldOffset = unsafe.staticFieldOffset(gsonField);
            unsafe.putObject(staticFieldBase, staticFieldOffset, customGson);

            patched = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to patch ORAS JsonUtils with custom Gson", e);
        }
    }

    /**
     * Custom TypeAdapter for ZonedDateTime (copied from ORAS SDK for compatibility).
     */
    private static class ZonedDateTimeTypeAdapter extends TypeAdapter<ZonedDateTime> {
        @Override
        public void write(JsonWriter out, ZonedDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
        }

        @Override
        public ZonedDateTime read(JsonReader in) throws IOException {
            String dateStr = in.nextString();
            return ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }
}
