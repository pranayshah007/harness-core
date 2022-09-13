/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.request;

import static io.harness.delegate.task.azure.arm.AzureARMTaskType.ARM_DEPLOYMENT;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;
import io.harness.expression.Expression;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AzureARMDeploymentParameters extends AzureARMTaskParameters {
  private ARMScopeType deploymentScope;
  private AzureDeploymentMode deploymentMode;
  private String deploymentName;
  private String managementGroupId;
  private String subscriptionId;
  private String resourceGroupName;
  private String deploymentDataLocation;
  @Expression(ALLOW_SECRETS) private String templateJson;
  @Expression(ALLOW_SECRETS) private String parametersJson;
  private boolean rollback;

  @Builder
  public AzureARMDeploymentParameters(String appId, String accountId, String activityId, ARMScopeType deploymentScope,
      AzureDeploymentMode deploymentMode, String deploymentName, String managementGroupId, String subscriptionId,
      String resourceGroupName, String deploymentDataLocation, String templateJson, String parametersJson,
      String commandName, Integer timeoutIntervalInMin, boolean rollback) {
    super(appId, accountId, activityId, subscriptionId, commandName, timeoutIntervalInMin, ARM_DEPLOYMENT);
    this.deploymentScope = deploymentScope;
    this.deploymentMode = deploymentMode;
    this.deploymentName = deploymentName;
    this.managementGroupId = managementGroupId;
    this.subscriptionId = subscriptionId;
    this.resourceGroupName = resourceGroupName;
    this.deploymentDataLocation = deploymentDataLocation;
    this.templateJson = templateJson;
    this.parametersJson = parametersJson;
    this.rollback = rollback;
  }
}
