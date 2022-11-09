/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.deployment.validator;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.model.AzureConstants;
import io.harness.delegate.task.azure.common.validator.Validator;
import io.harness.exception.runtime.azure.AzureBPDeploymentException;

import software.wings.delegatetasks.azure.arm.deployment.context.DeploymentBlueprintContext;

public class DeploymentBlueprintContextValidator implements Validator<DeploymentBlueprintContext> {
  @Override
  public void validate(DeploymentBlueprintContext context) {
    if (context.getAzureConfig() == null) {
      throw new AzureBPDeploymentException(AzureConstants.AZURE_CONFIG_BLANK_VALIDATION_MSG);
    }
    if (isBlank(context.getDefinitionResourceScope())) {
      throw new AzureBPDeploymentException(AzureConstants.RESOURCE_SCOPE_BLANK_VALIDATION_MSG);
    }
    if (isBlank(context.getBlueprintName())) {
      throw new AzureBPDeploymentException(AzureConstants.BLUEPRINT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(context.getBlueprintJSON())) {
      throw new AzureBPDeploymentException(AzureConstants.BLUEPRINT_JSON_BLANK_VALIDATION_MSG);
    }
    if (isBlank(context.getVersionId())) {
      throw new AzureBPDeploymentException(AzureConstants.VERSION_ID_BLANK_VALIDATION_MSG);
    }
    if (isBlank(context.getAssignmentJSON())) {
      throw new AzureBPDeploymentException(AzureConstants.ASSIGNMENT_JSON_BLANK_VALIDATION_MSG);
    }
    if (isBlank(context.getRoleAssignmentName())) {
      throw new AzureBPDeploymentException(AzureConstants.ROLE_ASSIGNMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(context.getAssignmentSubscriptionId())) {
      throw new AzureBPDeploymentException(AzureConstants.ASSIGNMENT_SUBSCRIPTION_ID_BLANK_VALIDATION_MSG);
    }
    if (context.getAssignment() == null) {
      throw new AzureBPDeploymentException(AzureConstants.ASSIGNMENT_BLANK_VALIDATION_MSG);
    }
  }
}
