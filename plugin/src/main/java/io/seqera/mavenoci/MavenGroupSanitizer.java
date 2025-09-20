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

public class MavenGroupSanitizer {
    
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9._-]");
    private static final Pattern CONSECUTIVE_SEPARATORS = Pattern.compile("[-._]+");
    
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