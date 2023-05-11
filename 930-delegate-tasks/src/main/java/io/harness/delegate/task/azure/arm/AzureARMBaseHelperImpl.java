/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.AZURE_ARM_ROLLBACK_PATTERN;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_NAME_PATTERN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentManagementGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentSubscriptionContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentTenantContext;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.logging.CommandExecutionStatus;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@OwnedBy(CDP)
public class AzureARMBaseHelperImpl implements AzureResourceCreationBaseHelper {
  protected static final String EMPTY_JSON = "{}";
  private static final Random rand = new Random();

  @Override
  public DeploymentResourceGroupContext toDeploymentResourceGroupContext(
      AzureARMTaskNGParameters deploymentParameters, AzureConfig azureConfig, AzureLogCallbackProvider logCallback) {
    AzureClientContext azureClientContext = getAzureClientContext(deploymentParameters, azureConfig);
    return DeploymentResourceGroupContext.builder()
        .azureClientContext(azureClientContext)
        .deploymentName(getDeploymentName(deploymentParameters))
        .mode(deploymentParameters.getDeploymentMode())
        .templateJson(deploymentParameters.getTemplateBody().fetchFileContent())
        .parametersJson(deploymentParameters.getParametersBody() != null
                ? deploymentParameters.getParametersBody().fetchFileContent()
                : EMPTY_JSON)
        .logStreamingTaskClient(logCallback)
        .steadyStateTimeoutInMin((int) TimeUnit.MILLISECONDS.toMinutes(deploymentParameters.getTimeoutInMs()))
        .isRollback(deploymentParameters.isRollback())
        .build();
  }

  @Override
  public AzureClientContext getAzureClientContext(
      AzureARMTaskNGParameters azureARMTaskNGParameters, AzureConfig azureConfig) {
    return new AzureClientContext(azureConfig, azureARMTaskNGParameters.getSubscriptionId(),
        azureARMTaskNGParameters.getResourceGroupName(), false);
  }

  @Override
  public String getDeploymentName(AzureARMTaskNGParameters deploymentParameters) {
    if (!isEmpty(deploymentParameters.getDeploymentName())) {
      return deploymentParameters.getDeploymentName();
    }
    int randomNum = rand.nextInt(1000);
    return deploymentParameters.isRollback()
        ? String.format(DEPLOYMENT_NAME_PATTERN, AZURE_ARM_ROLLBACK_PATTERN + randomNum, System.currentTimeMillis())
        : String.format(DEPLOYMENT_NAME_PATTERN, randomNum, System.currentTimeMillis());
  }

  @Override
  public DeploymentManagementGroupContext toDeploymentManagementGroupContext(
      AzureARMTaskNGParameters deploymentParameters, AzureConfig azureConfig,
      AzureLogCallbackProvider logStreamingTaskClient) {
    return DeploymentManagementGroupContext.builder()
        .azureConfig(azureConfig)
        .deploymentName(getDeploymentName(deploymentParameters))
        .deploymentDataLocation(deploymentParameters.getDeploymentDataLocation())
        .managementGroupId(deploymentParameters.getManagementGroupId())
        .mode(deploymentParameters.getDeploymentMode())
        .templateJson(deploymentParameters.getTemplateBody().fetchFileContent())
        .parametersJson(deploymentParameters.getParametersBody() != null
                ? deploymentParameters.getParametersBody().fetchFileContent()
                : EMPTY_JSON)
        .logStreamingTaskClient(logStreamingTaskClient)
        .steadyStateTimeoutInMin((int) TimeUnit.MILLISECONDS.toMinutes(deploymentParameters.getTimeoutInMs()))
        .build();
  }

  /*
   * populateDeploymentResponse returns the response for successful deployments at Subscription, ManagementGroup
   * and Tenant. The AzureARMTaskResponse is used in the manager to determine the result of the execution and store
   *  the azureARMPreDeploymentData for future rollbacks. Because this 3 deployments type don't support rollback,
   *  the azureARMPreDeploymentData is empty.
   * @param  outputs Output received from Azure
   * @return AzureARMTaskNGResponse the response containing the status of the deployment operation, the output from
   *     azure
   * and the data for a rollback scenario if supported.
   */
  @Override
  public AzureARMTaskNGResponse populateDeploymentResponse(String outputs) {
    return AzureARMTaskNGResponse.builder()
        .outputs(outputs)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  @Override
  public DeploymentTenantContext toDeploymentTenantContext(AzureARMTaskNGParameters deploymentParameters,
      AzureConfig azureConfig, AzureLogCallbackProvider logStreamingTaskClient) {
    return DeploymentTenantContext.builder()
        .azureConfig(azureConfig)
        .deploymentName(getDeploymentName(deploymentParameters))
        .deploymentDataLocation(deploymentParameters.getDeploymentDataLocation())
        .mode(deploymentParameters.getDeploymentMode())
        .templateJson(deploymentParameters.getTemplateBody().fetchFileContent())
        .parametersJson(deploymentParameters.getParametersBody() != null
                ? deploymentParameters.getParametersBody().fetchFileContent()
                : EMPTY_JSON)
        .logStreamingTaskClient(logStreamingTaskClient)
        .steadyStateTimeoutInMin((int) TimeUnit.MILLISECONDS.toMinutes(deploymentParameters.getTimeoutInMs()))
        .build();
  }

  @Override
  public DeploymentSubscriptionContext toDeploymentSubscriptionContext(AzureARMTaskNGParameters deploymentParameters,
      AzureConfig azureConfig, AzureLogCallbackProvider logStreamingTaskClient) {
    return DeploymentSubscriptionContext.builder()
        .azureConfig(azureConfig)
        .deploymentName(getDeploymentName(deploymentParameters))
        .deploymentDataLocation(deploymentParameters.getDeploymentDataLocation())
        .subscriptionId(deploymentParameters.getSubscriptionId())
        .mode(deploymentParameters.getDeploymentMode())
        .templateJson(deploymentParameters.getTemplateBody().fetchFileContent())
        .parametersJson(deploymentParameters.getParametersBody() != null
                ? deploymentParameters.getParametersBody().fetchFileContent()
                : EMPTY_JSON)
        .logStreamingTaskClient(logStreamingTaskClient)
        .steadyStateTimeoutInMin((int) TimeUnit.MILLISECONDS.toMinutes(deploymentParameters.getTimeoutInMs()))
        .build();
  }
}
