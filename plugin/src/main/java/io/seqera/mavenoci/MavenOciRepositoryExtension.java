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
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.util.internal.ConfigureUtil;

import java.util.List;

/**
 * Extension class that provides the mavenOci method to RepositoryHandler.
 * This replaces the ExpandoMetaClass approach for Configuration Cache compatibility.
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * repositories {
 *     mavenOci {
 *         url = 'https://registry.com'
 *         namespace = 'maven'
 *         credentials {
 *             username = 'user'
 *             password = 'pass'
 *         }
 *     }
 * }
 * }</pre>
 */
public class MavenOciRepositoryExtension {
    
    private static final Logger logger = Logging.getLogger(MavenOciRepositoryExtension.class);
    
    private final RepositoryHandler repositories;
    private final Project project;
    private final boolean isPublishingRepo;
    
    public MavenOciRepositoryExtension(RepositoryHandler repositories, Project project) {
        this.repositories = repositories;
        this.project = project;
        this.isPublishingRepo = detectIsPublishingRepo(project, repositories);
    }
    
    /**
     * Creates an OCI repository with configuration closure
     */
    public Object mavenOci(groovy.lang.Closure<?> configureAction) {
        return mavenOci(null, configureAction);
    }
    
    /**
     * Creates an OCI repository with explicit name and configuration closure
     */
    public Object mavenOci(String explicitName, groovy.lang.Closure<?> configureAction) {
        // Extract configuration to determine repository name
        ConfigExtractionResult config = extractConfigFromClosure(configureAction);
        String repositoryName = determineRepositoryName(explicitName, config, isPublishingRepo);
        
        logger.info("Creating OCI repository '{}' via extension (type: {})", repositoryName, 
                   isPublishingRepo ? "publishing" : "dependency");
        
        if (isPublishingRepo) {
            return createPublishingRepository(repositoryName, configureAction);
        } else {
            return createDependencyRepository(repositoryName, configureAction);
        }
    }
    
    /**
     * Create OCI repository for publishing
     */
    private MavenOciArtifactRepository createPublishingRepository(String repositoryName, groovy.lang.Closure<?> configureAction) {
        MavenOciArtifactRepository ociRepo = project.getObjects().newInstance(MavenOciArtifactRepository.class, repositoryName);
        
        // Configure the repository
        if (configureAction != null) {
            ConfigureUtil.configure(configureAction, ociRepo);
        }
        
        // Store in separate list for publishing task generation
        @SuppressWarnings("unchecked")
        List<MavenOciArtifactRepository> ociRepositories =
            (List<MavenOciArtifactRepository>) project.getExtensions().getExtraProperties().get("ociRepositoriesForProcessing");
        if (ociRepositories != null && !ociRepositories.contains(ociRepo)) {
            ociRepositories.add(ociRepo);
            logger.debug("Added OCI repository '{}' to publishing list", repositoryName);
        }
        
        // Add to repositories after evaluation for test visibility
        if (project.getState().getExecuted()) {
            addRepositorySafely(ociRepo, repositoryName);
        } else {
            project.afterEvaluate(p -> addRepositorySafely(ociRepo, repositoryName));
        }
        
        return ociRepo;
    }
    
    /**
     * Create OCI-backed Maven repository for dependency resolution
     */
    private MavenArtifactRepository createDependencyRepository(String repositoryName, groovy.lang.Closure<?> configureAction) {
        // Create OCI spec
        MavenOciRepositorySpec spec = project.getObjects().newInstance(MavenOciRepositorySpec.class, repositoryName);
        if (configureAction != null) {
            ConfigureUtil.configure(configureAction, spec);
        }
        
        // Generate display name
        String registryUrl = spec.getUrl().getOrNull();
        String displayName = registryUrl != null && !registryUrl.trim().isEmpty() 
            ? repositoryName + " (OCI: " + registryUrl + ")"
            : repositoryName;
        
        // Create Maven repository backed by OCI
        MavenArtifactRepository mavenRepo = repositories.maven(mavenRepoAction -> {
            mavenRepoAction.setName(displayName);
        });
        
        // Use factory to create OCI-backed Maven repository
        MavenOciRepositoryFactory.createOciMavenRepository(spec, mavenRepo, project);
        
        logger.debug("Created OCI-backed Maven repository for dependency resolution: {}", repositoryName);
        return mavenRepo;
    }
    
    /**
     * Safely add repository with error handling
     */
    private void addRepositorySafely(MavenOciArtifactRepository ociRepo, String repositoryName) {
        try {
            repositories.add(ociRepo);
            logger.debug("Added OCI repository '{}' to repositories for visibility", repositoryName);
        } catch (Exception e) {
            logger.debug("Could not add OCI repository '{}' for visibility: {}", repositoryName, e.getMessage());
        }
    }
    
    /**
     * Detect if the given repositories container is for publishing or dependency resolution
     */
    private boolean detectIsPublishingRepo(Project project, RepositoryHandler repositories) {
        try {
            PublishingExtension publishing = project.getExtensions().findByType(PublishingExtension.class);
            return publishing != null && repositories == publishing.getRepositories();
        } catch (Exception e) {
            logger.debug("Could not determine repository type, assuming dependency resolution: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract configuration from closure without execution risks
     */
    private ConfigExtractionResult extractConfigFromClosure(groovy.lang.Closure<?> configClosure) {
        if (configClosure == null) {
            return new ConfigExtractionResult("", "");
        }
        
        try {
            // Create a config capture object
            ConfigCapture capture = new ConfigCapture();
            
            // Create a copy of the closure with our config capture as delegate
            groovy.lang.Closure<?> extractorClosure = (groovy.lang.Closure<?>) configClosure.clone();
            extractorClosure.setDelegate(capture);
            extractorClosure.setResolveStrategy(groovy.lang.Closure.DELEGATE_FIRST);
            
            // Execute the closure to capture configuration
            try {
                extractorClosure.call();
            } catch (Exception e) {
                logger.debug("Could not extract config from configuration closure: {}", e.getMessage());
            }
            
            return new ConfigExtractionResult(capture.getUrl(), capture.getName());
        } catch (Exception e) {
            logger.debug("Failed to extract config from closure: {}", e.getMessage());
            return new ConfigExtractionResult("", "");
        }
    }
    
    /**
     * Determine the repository name based on explicit name, configuration, and repository type
     */
    private String determineRepositoryName(String explicitName, ConfigExtractionResult config, boolean isPublishingRepo) {
        // Use explicit name if provided
        if (explicitName != null && !explicitName.trim().isEmpty()) {
            return explicitName.trim();
        }
        
        // For publishing repositories, prefer explicit name from config
        if (isPublishingRepo && !config.name.isEmpty()) {
            logger.debug("Using explicit name '{}' for publishing repository", config.name);
            return config.name;
        }
        
        // Use URL-based naming
        if (!config.url.isEmpty()) {
            String urlBasedName = generateRepositoryNameFromUrl(config.url);
            logger.debug("Generated URL-based name '{}' from URL '{}'", urlBasedName, config.url);
            return urlBasedName;
        }
        
        // Fallback to default name
        return "mavenOci";
    }
    
    /**
     * Generate repository name from URL
     */
    private String generateRepositoryNameFromUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            StringBuilder nameBuilder = new StringBuilder();
            
            // Add hostname
            String host = uri.getHost();
            if (host != null) {
                nameBuilder.append(host.replaceAll("[^a-zA-Z0-9]", "_"));
            }
            
            // Add port if not default
            int port = uri.getPort();
            if (port != -1 && port != 80 && port != 443) {
                nameBuilder.append("_").append(port);
            }
            
            // Add path components
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                String cleanPath = path.replaceFirst("^/", "").replaceAll("[^a-zA-Z0-9]", "_");
                if (!cleanPath.isEmpty()) {
                    nameBuilder.append("_").append(cleanPath);
                }
            }
            
            String result = nameBuilder.toString();
            if (result.isEmpty()) {
                result = "mavenOci";
            }
            
            // Remove trailing underscores
            result = result.replaceAll("_+$", "");
            
            logger.debug("Generated repository name '{}' from URL '{}'", result, url);
            return result;
            
        } catch (Exception e) {
            logger.warn("Could not parse URL '{}' for name generation, using default: {}", url, e.getMessage());
            return "mavenOci";
        }
    }
    
    /**
     * Simple configuration capture object
     */
    private static class ConfigCapture {
        private String url = "";
        private String name = "";
        
        public void setUrl(String url) {
            if (url != null) this.url = url;
        }
        
        public void url(String url) {
            if (url != null) this.url = url;
        }
        
        public void setName(String name) {
            if (name != null) this.name = name;
        }
        
        public void name(String name) {
            if (name != null) this.name = name;
        }
        
        // Handle credentials block (ignore for name/url extraction)
        public void credentials(groovy.lang.Closure<?> credentialsConfig) {
            // No-op for extraction
        }
        
        // Handle namespace (ignore for name/url extraction)
        public void namespace(String namespace) {
            // No-op for extraction
        }
        
        public void setNamespace(String namespace) {
            // No-op for extraction
        }
        
        // Handle insecure flag (ignore for name/url extraction)
        public void insecure(boolean insecure) {
            // No-op for extraction
        }
        
        public void setInsecure(boolean insecure) {
            // No-op for extraction
        }
        
        public String getUrl() { return url; }
        public String getName() { return name; }
    }
    
    /**
     * Configuration extraction result
     */
    private static class ConfigExtractionResult {
        final String url;
        final String name;
        
        ConfigExtractionResult(String url, String name) {
            this.url = url != null ? url : "";
            this.name = name != null ? name : "";
        }
    }
}