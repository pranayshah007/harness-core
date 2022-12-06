/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import io.harness.ng.core.template.TemplateEntityType;

import com.google.inject.Inject;

public class TemplateVariableCreatorFactory {
  @Inject PipelineTemplateVariablesCreatorService pipelineTemplateVariablesCreatorService;
  @Inject CustomDeploymentTemplateVariablesCreatorService customDeploymentTemplateVariablesCreatorService;
  @Inject GenericTemplateVariablesCreatorService genericTemplateVariablesCreatorService;
  @Inject NoOpVariablesCreatorService noOpVariablesCreatorService;

  public TemplateVariableCreatorService getVariablesService(TemplateEntityType templateEntityType) {
    switch (templateEntityType) {
      case STEP_TEMPLATE:
      case STAGE_TEMPLATE:
      case PIPELINE_TEMPLATE:
      case STEPGROUP_TEMPLATE:
        return pipelineTemplateVariablesCreatorService;
      case CUSTOM_DEPLOYMENT_TEMPLATE:
        return customDeploymentTemplateVariablesCreatorService;
      case ARTIFACT_SOURCE_TEMPLATE:
      case MONITORED_SERVICE_TEMPLATE:
      case SECRET_MANAGER_TEMPLATE:
        return genericTemplateVariablesCreatorService;
      default:
        return noOpVariablesCreatorService;
    }
  }
}
