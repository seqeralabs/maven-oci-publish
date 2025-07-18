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
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

import javax.inject.Inject

/**
 * Configuration for publishing Maven artifacts to OCI registries.
 * Similar to MavenPublication but adapted for OCI storage.
 */
@CompileStatic
class OciPublication implements Named {
    
    private final String name
    private final Property<String> groupId
    private final Property<String> artifactId
    private final Property<String> version
    private final Property<String> repository
    private final Property<String> tag
    private final SetProperty<PublishArtifact> artifacts
    private final Property<SoftwareComponent> component
    private final ObjectFactory objectFactory
    private Action<Object> pomAction
    
    @Inject
    OciPublication(String name, ObjectFactory objectFactory) {
        this.name = name
        this.objectFactory = objectFactory
        this.groupId = objectFactory.property(String)
        this.artifactId = objectFactory.property(String)
        this.version = objectFactory.property(String)
        this.repository = objectFactory.property(String)
        this.tag = objectFactory.property(String)
        this.artifacts = objectFactory.setProperty(PublishArtifact)
        this.component = objectFactory.property(SoftwareComponent)
    }
    
    @Override
    String getName() {
        return name
    }
    
    /**
     * Configures this publication to use the specified software component.
     * @param component the software component to publish
     */
    void from(SoftwareComponent component) {
        this.component.set(component)
    }
    
    /**
     * Adds an artifact to this publication.
     * @param artifact the artifact to add
     */
    void artifact(PublishArtifact artifact) {
        this.artifacts.add(artifact)
    }
    
    /**
     * Configures the Maven POM for this publication.
     * @param action the configuration action
     */
    void pom(Action<Object> action) {
        this.pomAction = action
    }
    
    // Getters and setters
    
    Property<String> getGroupId() { return groupId }
    void setGroupId(String groupId) { this.groupId.set(groupId) }
    
    Property<String> getArtifactId() { return artifactId }
    void setArtifactId(String artifactId) { this.artifactId.set(artifactId) }
    
    Property<String> getVersion() { return version }
    void setVersion(String version) { this.version.set(version) }
    
    Property<String> getRepository() { return repository }
    void setRepository(String repository) { this.repository.set(repository) }
    
    Property<String> getTag() { return tag }
    void setTag(String tag) { this.tag.set(tag) }
    
    SetProperty<PublishArtifact> getArtifacts() { return artifacts }
    
    Property<SoftwareComponent> getComponent() { return component }
    
    Action<Object> getPomAction() { return pomAction }
}
