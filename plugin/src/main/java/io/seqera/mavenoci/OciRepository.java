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
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for an OCI registry repository.
 * Handles authentication and connection details for OCI-compliant registries.
 */
public class OciRepository implements Named {
    
    private final String name;
    private final Property<String> url;
    private final Property<Boolean> insecure;
    private final Property<Credentials> credentials;
    private final ObjectFactory objectFactory;
    
    @Inject
    public OciRepository(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.objectFactory = objectFactory;
        this.url = objectFactory.property(String.class);
        this.insecure = objectFactory.property(Boolean.class);
        this.insecure.set(false); // Default to secure
        this.credentials = objectFactory.property(Credentials.class);
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
    
    public Property<Boolean> getInsecure() { 
        return insecure; 
    }
    
    public void setInsecure(Boolean insecure) { 
        this.insecure.set(insecure); 
    }
    
    public Property<Credentials> getCredentials() { 
        return credentials; 
    }
}