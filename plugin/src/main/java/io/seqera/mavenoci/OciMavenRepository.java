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
import java.util.Set;
import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ComponentMetadataVersionLister;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/**
 * OCI Maven repository implementation for publishing artifacts to OCI registries.
 * This integrates with Gradle's standard publishing.repositories DSL.
 */
public class OciMavenRepository implements MavenArtifactRepository {
    
    private final String name;
    private final Property<String> url;
    private final Property<String> namespace;
    private final Property<Boolean> insecure;
    private final PasswordCredentials credentials;
    private final AuthenticationContainer authenticationContainer;
    
    @Inject
    public OciMavenRepository(ObjectFactory objectFactory, String name) {
        this.name = name;
        this.url = objectFactory.property(String.class);
        this.namespace = objectFactory.property(String.class);
        this.insecure = objectFactory.property(Boolean.class);
        this.insecure.set(false); // Default to secure
        this.credentials = objectFactory.newInstance(PasswordCredentials.class);
        // Create a simple empty authentication container for interface compliance
        this.authenticationContainer = null; // We'll handle authentication through credentials
    }
    
    // Implement MavenArtifactRepository methods directly
    @Override public String getName() { return name; }
    @Override public void setName(String name) { /* Name is immutable */ }
    
    @Override public URI getUrl() { 
        String urlStr = url.getOrNull();
        return urlStr != null ? URI.create(urlStr) : null;
    }
    
    @Override public void setUrl(Object url) { 
        this.url.set(url.toString()); 
    }
    
    @Override public void setUrl(URI url) { 
        this.url.set(url.toString()); 
    }
    
    @Override public void setArtifactUrls(Iterable<?> artifactUrls) { 
        // OCI registries don't use separate artifact URLs
    }
    
    @Override public void setArtifactUrls(Set<URI> artifactUrls) { 
        // OCI registries don't use separate artifact URLs
    }
    
    @Override public void artifactUrls(Object... urls) { 
        // OCI registries don't use separate artifact URLs
    }
    
    @Override public Set<URI> getArtifactUrls() { 
        return java.util.Collections.emptySet(); 
    }
    
    @Override public void credentials(Action<? super PasswordCredentials> action) { 
        action.execute(credentials); 
    }
    
    @Override public void credentials(Class<? extends Credentials> credentialsType) { 
        // Simple implementation
    }
    
    @Override public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) { 
        if (credentialsType.isAssignableFrom(PasswordCredentials.class)) {
            action.execute(credentialsType.cast(credentials));
        }
    }
    
    @Override public <T extends Credentials> T getCredentials(Class<T> credentialsType) {
        if (credentialsType.isAssignableFrom(PasswordCredentials.class)) {
            return credentialsType.cast(credentials);
        }
        return null;
    }
    
    @Override public AuthenticationContainer getAuthentication() {
        // Return null - OCI repositories use credential-based authentication
        return authenticationContainer;
    }
    
    @Override public void authentication(Action<? super AuthenticationContainer> action) {
        // No-op - OCI repositories use credential-based authentication
        if (authenticationContainer != null) {
            action.execute(authenticationContainer);
        }
    }
    
    @Override public boolean isAllowInsecureProtocol() { 
        return insecure.getOrElse(false); 
    }
    
    @Override public void setAllowInsecureProtocol(boolean allowInsecureProtocol) { 
        this.insecure.set(allowInsecureProtocol); 
    }
    
    @Override public void content(org.gradle.api.Action<? super org.gradle.api.artifacts.repositories.RepositoryContentDescriptor> configureAction) { 
        // OCI repositories don't use content filtering
    }
    
    @Override public void mavenContent(org.gradle.api.Action<? super org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor> configureAction) {
        // OCI repositories don't use Maven content filtering
    }
    
    @Override public MavenArtifactRepository.MetadataSources getMetadataSources() {
        // Return empty metadata sources for OCI repositories  
        return null;
    }
    
    @Override public void metadataSources(org.gradle.api.Action<? super MavenArtifactRepository.MetadataSources> configureAction) {
        // OCI repositories don't use metadata sources configuration
    }
    
    @Override public void setComponentVersionsLister(Class<? extends ComponentMetadataVersionLister> lister, Action<? super ActionConfiguration> configureAction) {
        // OCI repositories don't use component version listers
    }
    
    @Override public void setComponentVersionsLister(Class<? extends ComponentMetadataVersionLister> lister) {
        // OCI repositories don't use component version listers
    }
    
    @Override public void setMetadataSupplier(Class<? extends ComponentMetadataSupplier> supplier, Action<? super ActionConfiguration> configureAction) {
        // OCI repositories don't use metadata suppliers
    }
    
    @Override public void setMetadataSupplier(Class<? extends ComponentMetadataSupplier> supplier) {
        // OCI repositories don't use metadata suppliers
    }
    
    // Custom OCI properties
    public Property<String> getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace.set(namespace); }
    public Property<Boolean> getInsecure() { return insecure; }
    public void setInsecure(Boolean insecure) { this.insecure.set(insecure); }
    
    public PasswordCredentials getCredentials() { 
        return credentials;
    }
    
    public boolean hasCredentials() {
        PasswordCredentials creds = getCredentials();
        return creds != null && creds.getUsername() != null && creds.getPassword() != null;
    }
    
    /**
     * Builds an OCI reference for the given Maven coordinates.
     */
    public String buildOciReference(String groupId, String artifactId, String version) {
        if (namespace.isPresent()) {
            return buildOciReferenceWithNamespace(groupId, artifactId, version);
        } else {
            // Fallback to URL parsing approach
            MavenOciRegistryUriParser.OciRegistryInfo registryInfo = MavenOciRegistryUriParser.parse(getUrl().toString());
            return registryInfo.buildOciReference(groupId, artifactId, version);
        }
    }
    
    /**
     * Builds an OCI reference using the separate namespace property.
     */
    private String buildOciReferenceWithNamespace(String groupId, String artifactId, String version) {
        StringBuilder ref = new StringBuilder();
        
        // Add registry host (without protocol)
        String registryUrl = getUrl().toString();
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
