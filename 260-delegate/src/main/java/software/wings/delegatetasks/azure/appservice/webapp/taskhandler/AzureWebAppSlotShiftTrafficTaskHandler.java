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
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotShiftTrafficParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotShiftTrafficResponse;

import software.wings.delegatetasks.azure.appservice.webapp.AbstractAzureWebAppTaskHandler;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppSlotShiftTrafficTaskHandler extends AbstractAzureWebAppTaskHandler {
  @Override
  protected AzureAppServiceTaskResponse executeTaskInternal(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient) {
    AzureWebAppSlotShiftTrafficParameters slotShiftTrafficParameters =
        (AzureWebAppSlotShiftTrafficParameters) azureAppServiceTaskParameters;

    String webAppName = slotShiftTrafficParameters.getAppName();
    String deploymentSlot = slotShiftTrafficParameters.getDeploymentSlot();
    double trafficPercent = slotShiftTrafficParameters.getTrafficWeightInPercentage();
    azureAppServiceResourceUtilities.validateSlotShiftTrafficParameters(webAppName, deploymentSlot, trafficPercent);

    AzureWebClientContext webClientContext = buildAzureWebClientContext(slotShiftTrafficParameters, azureConfig);

    updateSlotTrafficWeight(slotShiftTrafficParameters, webClientContext, logStreamingTaskClient);

    markDeploymentStatusAsSuccess(azureAppServiceTaskParameters, logStreamingTaskClient);
    return AzureWebAppSlotShiftTrafficResponse.builder()
        .preDeploymentData(slotShiftTrafficParameters.getPreDeploymentData())
        .build();
  }

  private void updateSlotTrafficWeight(AzureWebAppSlotShiftTrafficParameters slotShiftTrafficParameters,
      AzureWebClientContext webClientContext, ILogStreamingTaskClient logStreamingTaskClient) {
    String shiftTrafficSlotName = slotShiftTrafficParameters.getDeploymentSlot();
    double trafficWeightInPercentage = slotShiftTrafficParameters.getTrafficWeightInPercentage();
    slotShiftTrafficParameters.getPreDeploymentData().setDeploymentProgressMarker(
        AppServiceDeploymentProgress.UPDATE_TRAFFIC_PERCENT.name());
    azureAppServiceDeploymentService.rerouteProductionSlotTraffic(webClientContext, shiftTrafficSlotName,
        trafficWeightInPercentage, logCallbackProviderFactory.createCg(logStreamingTaskClient));
  }
}
