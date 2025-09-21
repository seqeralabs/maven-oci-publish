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

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing Maven group IDs to be compatible with OCI registry naming conventions.
 * 
 * <p>This class handles the conversion between Maven group ID naming conventions and OCI registry
 * repository naming requirements. OCI registries have stricter naming rules than Maven, requiring
 * lowercase names with limited special characters.</p>
 * 
 * <h3>Sanitization Rules</h3>
 * <p>The sanitization process applies the following transformations:</p>
 * <ol>
 *   <li><strong>Case normalization</strong>: Convert to lowercase</li>
 *   <li><strong>Dot replacement</strong>: Replace dots ({@code .}) with hyphens ({@code -}) for Docker compatibility</li>
 *   <li><strong>Character filtering</strong>: Remove invalid characters (only keep alphanumeric, dots, hyphens, underscores)</li>
 *   <li><strong>Separator consolidation</strong>: Replace consecutive separators with single hyphens</li>
 *   <li><strong>Trimming</strong>: Remove leading and trailing separators</li>
 *   <li><strong>Safety prefix</strong>: Add "g" prefix if result starts with hyphen or underscore</li>
 * </ol>
 * 
 * <h3>Examples</h3>
 * <pre>
 * Original              | Sanitized
 * ----------------------|------------------
 * com.example           | com-example
 * org.springframework   | org-springframework  
 * Com.EXAMPLE.Test      | com-example-test
 * com.example@version   | com-exampleversion
 * spaces in group       | spacesingroup
 * -invalid.start        | invalid-start
 * _private.group        | private-group
 * </pre>
 * 
 * <h3>Validation</h3>
 * <p>The class also provides validation methods to check if a string conforms to
 * OCI registry naming rules:</p>
 * <ul>
 *   <li>Must be lowercase</li>
 *   <li>Can contain alphanumeric characters, hyphens, underscores, periods</li>
 *   <li>Cannot start or end with separators</li>
 *   <li>Cannot be empty</li>
 * </ul>
 * 
 * <h3>Reverse Mapping</h3>
 * <p>The class provides a best-effort reverse mapping from sanitized names back to
 * Maven group IDs. Note that this is not always perfect due to information loss
 * during sanitization (e.g., case information, removed characters).</p>
 * 
 * <h3>Thread Safety</h3>
 * <p>All methods in this class are static and thread-safe.</p>
 * 
 * @see MavenOciResolver
 * @see MavenOciRepositoryFactory
 * @since 1.0
 */
public class MavenOciGroupSanitizer {
    
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9._-]");
    private static final Pattern CONSECUTIVE_SEPARATORS = Pattern.compile("[-._]+");
    
    /**
     * Sanitizes a Maven group ID to be compatible with OCI registry naming conventions.
     * 
     * <p>This method transforms a Maven group ID according to OCI registry naming rules,
     * ensuring the result can be used as part of an OCI repository name.</p>
     * 
     * @param groupId the Maven group ID to sanitize (e.g., "com.example", "org.springframework")
     * @return the sanitized group ID suitable for OCI registry use (e.g., "com-example", "org-springframework")
     * @throws IllegalArgumentException if the group ID is null, empty, or results in an empty string after sanitization
     * 
     * @see #isValid(String) to check if a string is already properly sanitized
     * @see #reverse(String) to convert back to Maven group ID format
     */
    public static String sanitize(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        
        String sanitized = groupId.trim().toLowerCase();
        
        // Replace dots with hyphens for Docker compatibility
        sanitized = sanitized.replace('.', '-');
        
        // Remove any invalid characters (keep only alphanumeric, dots, hyphens, underscores)
        sanitized = INVALID_CHARS.matcher(sanitized).replaceAll("");
        
        // Replace consecutive separators with single hyphen
        sanitized = CONSECUTIVE_SEPARATORS.matcher(sanitized).replaceAll("-");
        
        // Remove leading/trailing separators
        sanitized = sanitized.replaceAll("^[-._]+", "").replaceAll("[-._]+$", "");
        
        // Ensure it's not empty after sanitization
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Group ID results in empty string after sanitization: " + groupId);
        }
        
        // Docker registry names cannot start with a hyphen or underscore
        if (sanitized.startsWith("-") || sanitized.startsWith("_")) {
            sanitized = "g" + sanitized;
        }
        
        return sanitized;
    }
    
    public static String reverse(String sanitizedGroup) {
        if (sanitizedGroup == null || sanitizedGroup.trim().isEmpty()) {
            throw new IllegalArgumentException("Sanitized group cannot be null or empty");
        }
        
        // Simple reverse: replace hyphens with dots
        // Note: This is a best-effort reverse mapping and may not be perfect
        // for complex group IDs that had multiple types of separators
        return sanitizedGroup.replace('-', '.');
    }
    
    public static boolean isValid(String sanitizedGroup) {
        if (sanitizedGroup == null || sanitizedGroup.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = sanitizedGroup.trim();
        
        // Check Docker registry naming rules
        // - Must be lowercase
        // - Can contain alphanumeric, hyphens, underscores, periods
        // - Cannot start with hyphen or underscore
        // - Cannot be empty
        
        if (!trimmed.equals(trimmed.toLowerCase())) {
            return false;
        }
        
        if (trimmed.startsWith("-") || trimmed.startsWith("_")) {
            return false;
        }
        
        if (trimmed.endsWith("-") || trimmed.endsWith("_")) {
            return false;
        }
        
        return trimmed.matches("^[a-z0-9][a-z0-9._-]*[a-z0-9]$") || trimmed.matches("^[a-z0-9]$");
    }
}
