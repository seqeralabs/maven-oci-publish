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

package io.seqera.mavenoci;

/**
 * Policy for handling existing packages when publishing to OCI registries.
 * 
 * <p>This enum defines the behavior when attempting to publish an artifact
 * that already exists in the target OCI registry.</p>
 * 
 * <h2>Policy Options</h2>
 * <ul>
 *   <li>{@link #FAIL} - Fail the build if the package already exists (default)</li>
 *   <li>{@link #OVERRIDE} - Replace the existing package with the new one</li>
 *   <li>{@link #SKIP} - Skip publishing if the package already exists</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * oci('myRepo') {
 *     url = 'https://registry.com'
 *     overwritePolicy = 'skip'  // or 'override' or 'fail'
 * }
 * }</pre>
 * 
 * @since 1.0
 */
public enum OverwritePolicy {
    
    /**
     * Fail the build if the package already exists in the registry.
     * This is the default behavior to prevent accidental overwrites.
     */
    FAIL,
    
    /**
     * Override (replace) the existing package with the new one.
     * This will push the artifact even if it already exists.
     */
    OVERRIDE,
    
    /**
     * Skip publishing if the package already exists.
     * The task will complete successfully without pushing.
     */
    SKIP;
    
    /**
     * Parses a string value to an OverwritePolicy enum.
     * Case-insensitive parsing supports common variations.
     * 
     * @param value the string value to parse
     * @return the corresponding OverwritePolicy
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static OverwritePolicy fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return FAIL; // Default
        }
        
        String normalized = value.trim().toUpperCase();
        switch (normalized) {
            case "FAIL":
            case "ERROR":
                return FAIL;
            case "OVERRIDE":
            case "OVERWRITE":
            case "REPLACE":
                return OVERRIDE;
            case "SKIP":
            case "IGNORE":
                return SKIP;
            default:
                throw new IllegalArgumentException(
                    "Invalid overwrite policy: '" + value + "'. " +
                    "Valid values are: fail, override, skip"
                );
        }
    }
    
    /**
     * Returns a human-readable description of the policy.
     */
    public String getDescription() {
        switch (this) {
            case FAIL:
                return "Fail if package already exists";
            case OVERRIDE:
                return "Override existing package";
            case SKIP:
                return "Skip if package already exists";
            default:
                return "Unknown policy";
        }
    }
}