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

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * Configuration for an OCI registry repository used for publishing Maven artifacts.
 * 
 * <p>This class handles authentication and connection details for OCI-compliant registries
 * such as Docker Hub, GitHub Container Registry, AWS ECR, and others. It defines where
 * artifacts should be published within the {@code oci} DSL block.</p>
 * 
 * <h3>Usage</h3>
 * <p>Repositories are configured within the {@code oci} DSL block:</p>
 * <pre>{@code
 * oci {
 *     repositories {
 *         dockerHub {
 *             url = 'https://registry-1.docker.io'
 *             namespace = 'maven'
 *             credentials {
 *                 username = 'myuser'
 *                 password = 'mypass'
 *             }
 *         }
 *         
 *         localDev {
 *             url = 'http://localhost:5000'
 *             insecure = true
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h3>Configuration Options</h3>
 * <ul>
 *   <li>{@code url} - OCI registry URL (required)</li>
 *   <li>{@code namespace} - Optional namespace/organization within the registry</li>
 *   <li>{@code insecure} - Allow HTTP connections (default: false)</li>
 *   <li>{@code credentials} - Username/password authentication</li>
 *   <li>{@code overwritePolicy} - Policy for handling existing packages (default: fail)</li>
 * </ul>
 * 
 * <h3>Namespace Behavior</h3>
 * <p>When a namespace is specified, the final OCI reference becomes:</p>
 * <pre>registry.com/namespace/sanitized-groupId/artifactId:version</pre>
 * 
 * @see MavenOciPublication
 * @since 1.0
 */
public class MavenOciRepository implements Named {
    
    private final String name;
    private final Property<String> url;
    private final Property<String> namespace;
    private final Property<Boolean> insecure;
    private final Property<Credentials> credentials;
    private final Property<OverwritePolicy> overwritePolicy;
    private final ObjectFactory objectFactory;
    
    @Inject
    public MavenOciRepository(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.objectFactory = objectFactory;
        this.url = objectFactory.property(String.class);
        this.namespace = objectFactory.property(String.class);
        this.insecure = objectFactory.property(Boolean.class);
        this.insecure.set(false); // Default to secure
        this.credentials = objectFactory.property(Credentials.class);
        this.overwritePolicy = objectFactory.property(OverwritePolicy.class);
        this.overwritePolicy.set(OverwritePolicy.FAIL); // Default to fail
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Configures password credentials for this repository.
     * @param action the configuration action
     */
    public void credentials(Action<? super PasswordCredentials> action) {
        PasswordCredentials passwordCredentials = objectFactory.newInstance(PasswordCredentials.class);
        action.execute(passwordCredentials);
        this.credentials.set(passwordCredentials);
    }
    
    /**
     * Configures credentials for this repository.
     * @param credentialsType the type of credentials
     * @param action the configuration action
     */
    public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) {
        T creds = objectFactory.newInstance(credentialsType);
        action.execute(creds);
        this.credentials.set(creds);
    }
    
    // Getters and setters
    
    public Property<String> getUrl() { 
        return url; 
    }
    
    public void setUrl(String url) { 
        this.url.set(url); 
    }
    
    public Property<String> getNamespace() { 
        return namespace; 
    }
    
    public void setNamespace(String namespace) { 
        this.namespace.set(namespace); 
    }
    
    public Property<Boolean> getInsecure() { 
        return insecure; 
    }
    
    public void setInsecure(Boolean insecure) { 
        this.insecure.set(insecure); 
    }
    
    public Property<Credentials> getCredentials() { 
        return credentials; 
    }
    
    public Property<OverwritePolicy> getOverwritePolicy() {
        return overwritePolicy;
    }
    
    public void setOverwritePolicy(String policy) {
        this.overwritePolicy.set(OverwritePolicy.fromString(policy));
    }
    
    public void setOverwritePolicy(OverwritePolicy policy) {
        this.overwritePolicy.set(policy);
    }
    
    /**
     * Checks if credentials are configured for this repository.
     * @return true if credentials are present, false for anonymous access
     */
    public boolean hasCredentials() {
        return credentials.isPresent() && credentials.get() != null;
    }
    
    /**
     * Parses the registry URI to extract host and namespace information.
     * @return OciRegistryInfo containing parsed registry details
     */
    public MavenOciRegistryUriParser.OciRegistryInfo parseRegistryInfo() {
        if (!url.isPresent()) {
            throw new IllegalStateException("Registry URL is not configured");
        }
        return MavenOciRegistryUriParser.parse(url.get());
    }
    
    /**
     * Builds an OCI reference for the given Maven coordinates.
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @return Complete OCI reference string
     */
    public String buildOciReference(String groupId, String artifactId, String version) {
        // Use the new namespace-aware approach
        if (namespace.isPresent()) {
            return buildOciReferenceWithNamespace(groupId, artifactId, version);
        } else {
            // Fallback to URL parsing approach
            return parseRegistryInfo().buildOciReference(groupId, artifactId, version);
        }
    }
    
    /**
     * Builds an OCI reference using the separate namespace property.
     * This is Harbor-compatible approach that avoids URL parsing issues.
     */
    private String buildOciReferenceWithNamespace(String groupId, String artifactId, String version) {
        StringBuilder ref = new StringBuilder();
        
        // Add registry host (without protocol)
        String registryUrl = url.get();
        String host = registryUrl.replaceFirst("^https?://", "");
        ref.append(host);
        
        // Add namespace
        if (namespace.isPresent()) {
            ref.append("/").append(namespace.get());
        }
        
        // Add sanitized group
        String sanitizedGroup = MavenOciGroupSanitizer.sanitize(groupId);
        ref.append("/").append(sanitizedGroup);
        
        // Add artifact and version
        ref.append("/").append(artifactId).append(":").append(version);
        
        return ref.toString();
    }
}
