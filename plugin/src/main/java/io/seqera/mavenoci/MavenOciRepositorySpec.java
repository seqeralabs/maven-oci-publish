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

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.Optional;

/**
 * Specification for an OCI repository that can be used as a Maven dependency repository.
 * 
 * <p>This class defines the configuration for accessing OCI registries as artifact repositories
 * for dependency resolution. It's used when configuring OCI repositories in the {@code repositories}
 * block for consuming Maven artifacts from OCI registries.</p>
 * 
 * <h3>Usage</h3>
 * <p>OCI repositories are configured using the {@code oci("name")} factory method:</p>
 * <pre>{@code
 * repositories {
 *     mavenCentral()
 *     
 *     oci("myRegistry") {
 *         url = 'https://registry.example.com/maven'
 *         insecure = false
 *         credentials {
 *             username = 'user'
 *             password = 'pass'
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h3>URL Structure</h3>
 * <p>The URL can include a namespace path component which will be used to locate artifacts:</p>
 * <ul>
 *   <li>{@code https://registry.com} - Base registry</li>
 *   <li>{@code https://registry.com/maven} - Registry with "maven" namespace</li>
 *   <li>{@code https://registry.com/org/maven} - Registry with "org/maven" nested namespace</li>
 * </ul>
 * 
 * <h3>Dependency Resolution</h3>
 * <p>When Gradle resolves a dependency like {@code com.example:my-lib:1.0.0}, this repository
 * will attempt to find the artifact at the OCI reference:</p>
 * <pre>registry.com/[namespace/]com-example/my-lib:1.0.0</pre>
 * 
 * @see MavenOciRepositoryFactory
 * @see MavenOciResolver
 * @see MavenOciGroupSanitizer
 * @since 1.0
 */
public abstract class MavenOciRepositorySpec implements Named {
    
    private final String name;
    private PasswordCredentials credentials;
    
    @Inject
    public MavenOciRepositorySpec(String name) {
        this.name = name;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * The URL of the OCI registry (e.g., "https://docker.io/maven").
     * The namespace will be automatically inferred from the URL path.
     */
    public abstract Property<String> getUrl();
    
    /**
     * Whether to use insecure (HTTP) connections to the registry.
     * Defaults to false (secure HTTPS connections).
     */
    public abstract Property<Boolean> getInsecure();
    
    /**
     * Configures credentials for accessing the OCI registry.
     * If not provided, anonymous access will be used.
     * 
     * @param action Configuration action for credentials
     */
    public void credentials(Action<? super PasswordCredentials> action) {
        if (credentials == null) {
            credentials = new PasswordCredentials() {
                private String username;
                private String password;
                
                @Override
                public String getUsername() {
                    return username;
                }
                
                @Override
                public void setUsername(String username) {
                    this.username = username;
                }
                
                @Override
                public String getPassword() {
                    return password;
                }
                
                @Override
                public void setPassword(String password) {
                    this.password = password;
                }
            };
        }
        action.execute(credentials);
    }
    
    /**
     * Gets the configured credentials, if any.
     * 
     * @return Optional containing credentials, or empty if none configured
     */
    public Optional<PasswordCredentials> getCredentials() {
        return Optional.ofNullable(credentials);
    }
    
    /**
     * Checks if this repository has credentials configured.
     * 
     * @return true if credentials are configured, false for anonymous access
     */
    public boolean hasCredentials() {
        return credentials != null && 
               credentials.getUsername() != null && 
               credentials.getPassword() != null;
    }
}
