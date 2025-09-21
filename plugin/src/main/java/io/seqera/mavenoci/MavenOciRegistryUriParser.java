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

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility class for parsing OCI registry URIs and extracting registry host and namespace information.
 * 
 * <p>This parser handles the decomposition of OCI registry URLs into their constituent parts,
 * separating the registry host from the namespace path. This is crucial for proper OCI reference
 * construction when publishing or resolving Maven artifacts.</p>
 * 
 * <h2>URI Structure</h2>
 * <p>OCI registry URIs follow this general structure:</p>
 * <pre>
 * [scheme://]host[:port][/namespace[/sub-namespace[...]]]
 * </pre>
 * 
 * <h2>Parsing Examples</h2>
 * <ul>
 *   <li>{@code https://registry-1.docker.io} → host: {@code registry-1.docker.io}, namespace: {@code ""}</li>
 *   <li>{@code https://registry.com:5000/maven} → host: {@code registry.com:5000}, namespace: {@code "maven"}</li>
 *   <li>{@code http://localhost:5000/org/maven} → host: {@code localhost:5000}, namespace: {@code "org/maven"}</li>
 *   <li>{@code ghcr.io/myorg/maven} → host: {@code ghcr.io}, namespace: {@code "myorg/maven"}</li>
 * </ul>
 * 
 * <h2>Usage in OCI References</h2>
 * <p>The parsed components are used to construct OCI references for Maven artifacts:</p>
 * <pre>
 * Registry Host: registry.com:5000
 * Namespace: maven/snapshots
 * Maven Artifact: com.example:my-lib:1.0.0
 * Final OCI Ref: registry.com:5000/maven/snapshots/com-example/my-lib:1.0.0
 * </pre>
 * 
 * <h2>Error Handling</h2>
 * <p>The parser validates URI format and throws {@link IllegalArgumentException} for:</p>
 * <ul>
 *   <li>Null or empty URIs</li>
 *   <li>Malformed URIs that cannot be parsed</li>
 *   <li>URIs without a valid host component</li>
 * </ul>
 * 
 * @see MavenOciResolver
 * @see MavenOciRepositoryFactory
 * @since 1.0
 */
public class MavenOciRegistryUriParser {
    
    public static OciRegistryInfo parse(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("Registry URI cannot be null or empty");
        }
        
        try {
            URI parsed = new URI(uri);
            String host = parsed.getHost();
            int port = parsed.getPort();
            String path = parsed.getPath();
            
            if (host == null) {
                throw new IllegalArgumentException("Invalid registry URI: missing host");
            }
            
            // Build registry host (with port if specified)
            String registryHost = host;
            if (port != -1) {
                registryHost = host + ":" + port;
            }
            
            // Extract namespace from path
            String namespace = "";
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                // Remove leading slash and use as namespace
                namespace = path.startsWith("/") ? path.substring(1) : path;
                // Remove trailing slash if present
                if (namespace.endsWith("/")) {
                    namespace = namespace.substring(0, namespace.length() - 1);
                }
            }
            
            return new OciRegistryInfo(registryHost, namespace);
            
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid registry URI: " + uri, e);
        }
    }
    
    public static class OciRegistryInfo {
        private final String host;
        private final String namespace;
        
        public OciRegistryInfo(String host, String namespace) {
            this.host = host;
            this.namespace = namespace != null ? namespace : "";
        }
        
        public String getHost() {
            return host;
        }
        
        public String getNamespace() {
            return namespace;
        }
        
        public boolean hasNamespace() {
            return !namespace.isEmpty();
        }
        
        public String buildOciReference(String groupId, String artifactId, String version) {
            StringBuilder ref = new StringBuilder();
            ref.append(host);
            
            if (hasNamespace()) {
                ref.append("/").append(namespace);
            }
            
            // Add sanitized group
            String sanitizedGroup = MavenOciGroupSanitizer.sanitize(groupId);
            ref.append("/").append(sanitizedGroup);
            
            // Add artifact and version
            ref.append("/").append(artifactId).append(":").append(version);
            
            return ref.toString();
        }
        
        @Override
        public String toString() {
            return "OciRegistryInfo{" +
                   "host='" + host + '\'' +
                   ", namespace='" + namespace + '\'' +
                   '}';
        }
    }
}
