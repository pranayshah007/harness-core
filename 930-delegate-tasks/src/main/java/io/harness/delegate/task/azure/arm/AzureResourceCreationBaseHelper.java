/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentManagementGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentSubscriptionContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentTenantContext;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

@OwnedBy(CDP)
public interface AzureResourceCreationBaseHelper {
  DeploymentResourceGroupContext toDeploymentResourceGroupContext(
      AzureARMTaskNGParameters azureARMTaskNGParameters, AzureConfig azureConfig, AzureLogCallbackProvider logCallback);
  AzureClientContext getAzureClientContext(AzureARMTaskNGParameters azureARMTaskNGParameters, AzureConfig azureConfig);
  String getDeploymentName(AzureARMTaskNGParameters deploymentParameters);
  DeploymentManagementGroupContext toDeploymentManagementGroupContext(AzureARMTaskNGParameters deploymentParameters,
      AzureConfig azureConfig, AzureLogCallbackProvider logStreamingTaskClient);
  AzureARMTaskNGResponse populateDeploymentResponse(String outputs);
  DeploymentTenantContext toDeploymentTenantContext(AzureARMTaskNGParameters deploymentParameters,
      AzureConfig azureConfig, AzureLogCallbackProvider logStreamingTaskClient);
  DeploymentSubscriptionContext toDeploymentSubscriptionContext(AzureARMTaskNGParameters deploymentParameters,
      AzureConfig azureConfig, AzureLogCallbackProvider logStreamingTaskClient);
}
