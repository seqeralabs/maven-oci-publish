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

package io.seqera.mavenoci

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.credentials.Credentials
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.authentication.Authentication

import javax.inject.Inject

/**
 * Configuration for an OCI registry repository.
 * Handles authentication and connection details for OCI-compliant registries.
 */
class OciRepository implements Named {
    
    private final String name
    private final Property<String> url
    private final Property<Boolean> insecure
    private final Property<Credentials> credentials
    private final ObjectFactory objectFactory
    
    @Inject
    OciRepository(String name, ObjectFactory objectFactory) {
        this.name = name
        this.objectFactory = objectFactory
        this.url = objectFactory.property(String)
        this.insecure = objectFactory.property(Boolean)
        this.insecure.set(false) // Default to secure
        this.credentials = objectFactory.property(Credentials)
    }
    
    @Override
    String getName() {
        return name
    }
    
    /**
     * Configures password credentials for this repository.
     * @param action the configuration action
     */
    void credentials(Action<? super PasswordCredentials> action) {
        def passwordCredentials = objectFactory.newInstance(PasswordCredentials)
        action.execute(passwordCredentials)
        this.credentials.set(passwordCredentials)
    }
    
    /**
     * Configures credentials for this repository.
     * @param credentialsType the type of credentials
     * @param action the configuration action
     */
    def <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) {
        def creds = objectFactory.newInstance(credentialsType)
        action.execute(creds)
        this.credentials.set(creds)
    }
    
    // Getters and setters
    
    Property<String> getUrl() { return url }
    void setUrl(String url) { this.url.set(url) }
    
    Property<Boolean> getInsecure() { return insecure }
    void setInsecure(Boolean insecure) { this.insecure.set(insecure) }
    
    Property<Credentials> getCredentials() { return credentials }
}
