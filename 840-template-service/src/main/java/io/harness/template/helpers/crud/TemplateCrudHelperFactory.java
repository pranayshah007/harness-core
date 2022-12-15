/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers.crud;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.template.TemplateEntityType;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CDC)
public class TemplateCrudHelperFactory {
  @Inject NoOpTemplateCrudHelper noOpTemplateCrudHelper;
  @Inject PipelineTemplateCrudHelper pipelineTemplateCrudHelper;
  @Inject CustomDeploymentCrudHelper customDeploymentCrudHelper;
  @Inject ArtifactSourceTemplateCrudHelper artifactSourceTemplateCrudHelper;

  public TemplateCrudHelper getCrudHelperForTemplateType(TemplateEntityType templateEntityType) {
    switch (templateEntityType) {
      case STEP_TEMPLATE:
      case STEPGROUP_TEMPLATE:
      case STAGE_TEMPLATE:
      case PIPELINE_TEMPLATE:
        return pipelineTemplateCrudHelper;
      case CUSTOM_DEPLOYMENT_TEMPLATE:
        return customDeploymentCrudHelper;
      case ARTIFACT_SOURCE_TEMPLATE:
        return artifactSourceTemplateCrudHelper;
      default:
        return noOpTemplateCrudHelper;
    }
  }
}
