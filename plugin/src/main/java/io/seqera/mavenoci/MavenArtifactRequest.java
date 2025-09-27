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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser and container for Maven repository HTTP request information.
 * 
 * <p>This class parses HTTP requests that Gradle makes to Maven repositories and extracts
 * the Maven coordinates, file type, and classifier information. It's designed to handle
 * the standard Maven repository URL structure used by Gradle's dependency resolution.</p>
 * 
 * <h2>Maven Repository URL Structure</h2>
 * <p>Maven repositories use a standardized URL structure:</p>
 * <pre>
 * /maven/{groupId}/{artifactId}/{version}/{artifactId}-{version}[-{classifier}].{extension}
 * </pre>
 * 
 * <h2>Supported Request Patterns</h2>
 * <p>This parser handles all standard Maven artifact types:</p>
 * <ul>
 *   <li><strong>Primary JAR</strong>: {@code /maven/com/example/my-lib/1.0.0/my-lib-1.0.0.jar}</li>
 *   <li><strong>POM files</strong>: {@code /maven/com/example/my-lib/1.0.0/my-lib-1.0.0.pom}</li>
 *   <li><strong>Sources JAR</strong>: {@code /maven/com/example/my-lib/1.0.0/my-lib-1.0.0-sources.jar}</li>
 *   <li><strong>Javadoc JAR</strong>: {@code /maven/com/example/my-lib/1.0.0/my-lib-1.0.0-javadoc.jar}</li>
 *   <li><strong>Checksum files</strong>: {@code /maven/com/example/my-lib/1.0.0/my-lib-1.0.0.jar.sha1}</li>
 * </ul>
 * 
 * <h2>Group ID Path Mapping</h2>
 * <p>Maven group IDs are converted to directory paths:</p>
 * <ul>
 *   <li>{@code com.example} → {@code com/example}</li>
 *   <li>{@code org.springframework.boot} → {@code org/springframework/boot}</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * String requestPath = "/maven/com/example/my-lib/1.0.0/my-lib-1.0.0-sources.jar";
 * MavenArtifactRequest request = MavenArtifactRequest.parse(requestPath);
 * 
 * if (request != null) {
 *     System.out.println("Group ID: " + request.getGroupId());        // com.example
 *     System.out.println("Artifact ID: " + request.getArtifactId());  // my-lib
 *     System.out.println("Version: " + request.getVersion());         // 1.0.0
 *     System.out.println("Classifier: " + request.getClassifier());   // sources
 *     System.out.println("File Type: " + request.getFileType());      // jar
 *     System.out.println("Is Sources: " + request.isSourcesArtifact()); // true
 * }
 * }</pre>
 * 
 * <h2>Error Handling</h2>
 * <p>The parser is designed to be robust:</p>
 * <ul>
 *   <li>Invalid request paths return {@code null} from {@link #parse(String)}</li>
 *   <li>Malformed filenames fall back to simple extension-based parsing</li>
 *   <li>Unknown file extensions are supported with generic content types</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <p>This class is immutable and thread-safe once created. The static {@link #parse(String)} 
 * method can be called safely from multiple threads.</p>
 * 
 * @see MavenOciProxy for usage in HTTP request handling
 * @since 1.0
 */
public class MavenArtifactRequest {
    
    /**
     * Regular expression pattern for parsing Maven repository artifact request paths.
     * 
     * <p>Matches paths in the format:</p>
     * <pre>/maven/{groupPath}/{artifactId}/{version}/{filename}</pre>
     * 
     * <p>Capture groups:</p>
     * <ol>
     *   <li>Group path (e.g., "com/example")</li>
     *   <li>Artifact ID (e.g., "my-lib")</li>
     *   <li>Version (e.g., "1.0.0")</li>
     *   <li>Filename (e.g., "my-lib-1.0.0.jar")</li>
     * </ol>
     */
    private static final Pattern MAVEN_ARTIFACT_PATTERN = Pattern.compile(
        "^/maven/(.+)/([^/]+)/([^/]+)/([^/]+)$"
    );
    
    /**
     * Regular expression pattern for parsing Maven artifact filenames.
     * 
     * <p>Matches filenames in the format:</p>
     * <pre>{artifactId}-{version}[-{classifier}].{extension}</pre>
     * 
     * <p>Capture groups:</p>
     * <ol>
     *   <li>Artifact ID (e.g., "my-lib")</li>
     *   <li>Version (e.g., "1.0.0")</li>
     *   <li>Classifier (optional, e.g., "sources")</li>
     *   <li>File extension (e.g., "jar")</li>
     * </ol>
     */
    private static final Pattern ARTIFACT_FILENAME_PATTERN = Pattern.compile(
        "^([^-]+(?:-[^-]+)*?)-([^-]+(?:\\.[^-]+)*?)(?:-([^.]+))?\\.([^.]+)$"
    );
    
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier; // sources, javadoc, etc.
    private final String fileType; // jar, pom, etc.
    private final String fileName;
    
    /**
     * Constructs a new Maven artifact request with the specified parameters.
     * 
     * @param groupId the Maven group ID (e.g., "com.example")
     * @param artifactId the Maven artifact ID (e.g., "my-lib")
     * @param version the artifact version (e.g., "1.0.0")
     * @param classifier the optional classifier (e.g., "sources"), may be null
     * @param fileType the file extension (e.g., "jar", "pom")
     * @param fileName the original filename from the request
     */
    public MavenArtifactRequest(String groupId, String artifactId, String version, String classifier, String fileType, String fileName) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.fileType = fileType;
        this.fileName = fileName;
    }
    
    /**
     * Parses a Maven repository HTTP request path into a structured request object.
     * 
     * <p>This method handles the complete parsing pipeline:</p>
     * <ol>
     *   <li>Validates the request path format</li>
     *   <li>Extracts group ID, artifact ID, version, and filename</li>
     *   <li>Parses the filename to extract classifier and file type</li>
     *   <li>Validates coordinate consistency</li>
     * </ol>
     * 
     * @param requestPath the HTTP request path (e.g., "/maven/com/example/my-lib/1.0.0/my-lib-1.0.0.jar")
     * @return a parsed request object, or null if the path is invalid
     */
    public static MavenArtifactRequest parse(String requestPath) {
        if (requestPath == null) {
            return null;
        }
        
        Matcher pathMatcher = MAVEN_ARTIFACT_PATTERN.matcher(requestPath);
        if (!pathMatcher.matches()) {
            return null;
        }
        
        String groupPath = pathMatcher.group(1);
        String artifactId = pathMatcher.group(2);
        String version = pathMatcher.group(3);
        String fileName = pathMatcher.group(4);
        
        // Convert group path back to group ID (com/example -> com.example)
        String groupId = groupPath.replace('/', '.');
        
        // Parse the filename to extract classifier and file type
        Matcher filenameMatcher = ARTIFACT_FILENAME_PATTERN.matcher(fileName);
        if (!filenameMatcher.matches()) {
            // Fallback: simple parsing for basic cases
            String fileType = getFileExtension(fileName);
            if (fileType == null) {
                return null;
            }
            return new MavenArtifactRequest(groupId, artifactId, version, null, fileType, fileName);
        }
        
        String fileArtifactId = filenameMatcher.group(1);
        String fileVersion = filenameMatcher.group(2);
        String classifier = filenameMatcher.group(3);
        String fileType = filenameMatcher.group(4);
        
        // Validate that artifact ID and version match
        if (!artifactId.equals(fileArtifactId) || !version.equals(fileVersion)) {
            // Handle special cases where filename might not exactly match
            if (!fileName.startsWith(artifactId + "-" + version)) {
                return null;
            }
        }
        
        return new MavenArtifactRequest(groupId, artifactId, version, classifier, fileType, fileName);
    }
    
    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDot + 1);
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public String getArtifactId() {
        return artifactId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getClassifier() {
        return classifier;
    }
    
    public String getFileType() {
        return fileType;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    /**
     * Returns a human-readable Maven coordinate string for this request.
     * 
     * <p>Format: {@code groupId:artifactId:version[:classifier] (fileType)}</p>
     * 
     * @return coordinate string (e.g., "com.example:my-lib:1.0.0:sources (jar)")
     */
    public String getCoordinate() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(":").append(artifactId).append(":").append(version);
        if (classifier != null && !classifier.isEmpty()) {
            sb.append(":").append(classifier);
        }
        sb.append(" (").append(fileType).append(")");
        return sb.toString();
    }
    
    /**
     * Returns a cache key string for this artifact request.
     * 
     * <p>Used by {@link MavenOciProxy} for session caching to avoid repeated
     * OCI resolution calls for the same artifact during a build.</p>
     * 
     * @return cache key (e.g., "com.example:my-lib:1.0.0:sources:jar")
     */
    public String getCacheKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(":").append(artifactId).append(":").append(version);
        if (classifier != null && !classifier.isEmpty()) {
            sb.append(":").append(classifier);
        }
        sb.append(":").append(fileType);
        return sb.toString();
    }
    
    /**
     * Returns the appropriate HTTP Content-Type header value for this artifact.
     * 
     * <p>Used by {@link MavenOciProxy} when serving artifacts to Gradle to ensure
     * proper content type headers are set in HTTP responses.</p>
     * 
     * @return the MIME content type (e.g., "application/java-archive" for JAR files)
     */
    public String getContentType() {
        switch (fileType) {
            case "jar":
                return "application/java-archive";
            case "pom":
                return "application/xml";
            case "xml":
                return "application/xml";
            case "txt":
                return "text/plain";
            case "md5":
                return "text/plain";
            case "sha1":
                return "text/plain";
            case "sha256":
                return "text/plain";
            default:
                return "application/octet-stream";
        }
    }
    
    public boolean isPrimaryArtifact() {
        return classifier == null || classifier.isEmpty();
    }
    
    public boolean isSourcesArtifact() {
        return "sources".equals(classifier);
    }
    
    public boolean isJavadocArtifact() {
        return "javadoc".equals(classifier);
    }
    
    public boolean isPomFile() {
        return "pom".equals(fileType);
    }
    
    public boolean isJarFile() {
        return "jar".equals(fileType);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MavenArtifactRequest{");
        sb.append("coordinate='").append(getCoordinate()).append("'");
        sb.append(", fileName='").append(fileName).append("'");
        sb.append("}");
        return sb.toString();
    }
}