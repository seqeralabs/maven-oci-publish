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

import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Project evaluation listener that helps set up OCI artifact resolution.
 */
public class OciResolutionProjectEvaluationListener implements ProjectEvaluationListener {
    
    private static final Logger logger = Logging.getLogger(OciResolutionProjectEvaluationListener.class);
    
    private final Project project;
    private final String repositoryName;
    
    public OciResolutionProjectEvaluationListener(Project project, String repositoryName) {
        this.project = project;
        this.repositoryName = repositoryName;
    }
    
    @Override
    public void beforeEvaluate(Project project) {
        // Nothing to do before evaluation
    }
    
    @Override
    public void afterEvaluate(Project project, ProjectState state) {
        if (project.equals(this.project) && state.getFailure() == null) {
            logger.debug("Project evaluated successfully, OCI resolution ready for: {}", repositoryName);
        }
    }
}