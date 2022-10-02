/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppInstancesParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppInstancesResponse;

import software.wings.delegatetasks.azure.appservice.webapp.AbstractAzureWebAppTaskHandler;

import javax.inject.Singleton;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppListWebAppInstancesTaskHandler extends AbstractAzureWebAppTaskHandler {
  @Override
  public AzureAppServiceTaskResponse executeTaskInternal(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient) {
    String subscriptionId = azureAppServiceTaskParameters.getSubscriptionId();
    String resourceGroupName =
        ((AzureWebAppListWebAppInstancesParameters) azureAppServiceTaskParameters).getResourceGroupName();
    String webAppName = ((AzureWebAppListWebAppInstancesParameters) azureAppServiceTaskParameters).getAppName();
    String slotName = ((AzureWebAppListWebAppInstancesParameters) azureAppServiceTaskParameters).getSlotName();

    AzureWebClientContext azureWebClientContext = AzureWebClientContext.builder()
                                                      .azureConfig(azureConfig)
                                                      .subscriptionId(subscriptionId)
                                                      .resourceGroupName(resourceGroupName)
                                                      .appName(webAppName)
                                                      .build();

    List<AzureAppDeploymentData> deploymentData =
        azureAppServiceService.fetchDeploymentData(azureWebClientContext, slotName);

    return AzureWebAppListWebAppInstancesResponse.builder().deploymentData(deploymentData).build();
  }
}
