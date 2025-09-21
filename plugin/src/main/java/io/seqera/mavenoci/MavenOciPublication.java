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
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * Configuration for publishing Maven artifacts to OCI registries.
 * 
 * <p>This class is similar to Gradle's {@code MavenPublication} but adapted for OCI storage.
 * It defines what artifacts should be published to OCI registries and how they should be organized.</p>
 * 
 * <h3>Usage</h3>
 * <p>Publications are configured within the {@code oci} DSL block:</p>
 * <pre>{@code
 * oci {
 *     publications {
 *         maven {
 *             from components.java
 *             repository = 'my-artifacts'
 *             tag = project.version
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h3>Coordinate Mapping</h3>
 * <p>Maven coordinates are automatically mapped to OCI references:</p>
 * <ul>
 *   <li>{@code groupId} → sanitized group ID (e.g., {@code com.example} → {@code com-example})</li>
 *   <li>{@code artifactId} → repository name</li>
 *   <li>{@code version} → OCI tag</li>
 * </ul>
 * 
 * <h3>Software Components</h3>
 * <p>The publication can include artifacts from Gradle's software components (typically {@code components.java})
 * which automatically includes the main JAR, sources, javadoc, and POM files.</p>
 * 
 * @see MavenOciRepository
 * @since 1.0
 */
public class MavenOciPublication implements Named {
    
    private final String name;
    private final Property<String> groupId;
    private final Property<String> artifactId;
    private final Property<String> version;
    private final Property<String> repository;
    private final Property<String> tag;
    private final SetProperty<PublishArtifact> artifacts;
    private final Property<SoftwareComponent> component;
    private final ObjectFactory objectFactory;
    private Action<Object> pomAction;
    
    @Inject
    public MavenOciPublication(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.objectFactory = objectFactory;
        this.groupId = objectFactory.property(String.class);
        this.artifactId = objectFactory.property(String.class);
        this.version = objectFactory.property(String.class);
        this.repository = objectFactory.property(String.class);
        this.tag = objectFactory.property(String.class);
        this.artifacts = objectFactory.setProperty(PublishArtifact.class);
        this.component = objectFactory.property(SoftwareComponent.class);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Configures this publication to use the specified software component.
     * @param component the software component to publish
     */
    public void from(SoftwareComponent component) {
        this.component.set(component);
    }
    
    /**
     * Adds an artifact to this publication.
     * @param artifact the artifact to add
     */
    public void artifact(PublishArtifact artifact) {
        this.artifacts.add(artifact);
    }
    
    /**
     * Configures the Maven POM for this publication.
     * @param action the configuration action
     */
    public void pom(Action<Object> action) {
        this.pomAction = action;
    }
    
    // Getters and setters
    
    public Property<String> getGroupId() { 
        return groupId; 
    }
    
    public void setGroupId(String groupId) { 
        this.groupId.set(groupId); 
    }
    
    public Property<String> getArtifactId() { 
        return artifactId; 
    }
    
    public void setArtifactId(String artifactId) { 
        this.artifactId.set(artifactId); 
    }
    
    public Property<String> getVersion() { 
        return version; 
    }
    
    public void setVersion(String version) { 
        this.version.set(version); 
    }
    
    public Property<String> getRepository() { 
        return repository; 
    }
    
    public void setRepository(String repository) { 
        this.repository.set(repository); 
    }
    
    public Property<String> getTag() { 
        return tag; 
    }
    
    public void setTag(String tag) { 
        this.tag.set(tag); 
    }
    
    public SetProperty<PublishArtifact> getArtifacts() { 
        return artifacts; 
    }
    
    public Property<SoftwareComponent> getComponent() { 
        return component; 
    }
    
    public Action<Object> getPomAction() { 
        return pomAction; 
    }
}
