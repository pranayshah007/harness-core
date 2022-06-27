package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTrafficShiftRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTrafficShiftResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class AzureWebAppTrafficShiftRequestHandler extends AzureWebAppRequestHandler<AzureWebAppTrafficShiftRequest> {
  @Override
  protected AzureWebAppRequestResponse execute(AzureWebAppTrafficShiftRequest taskRequest, AzureConfig azureConfig,
      AzureLogCallbackProvider logCallbackProvider) {
    AzureWebAppInfraDelegateConfig infrastructure = taskRequest.getInfrastructure();
    String webAppName = infrastructure.getAppName();
    String deploymentSlot = infrastructure.getDeploymentSlot();
    double trafficPercent = taskRequest.getTrafficPercentage();
    azureAppServiceResourceUtilities.validateSlotShiftTrafficParameters(webAppName, deploymentSlot, trafficPercent);

    AzureWebClientContext webClientContext = buildAzureWebClientContext(infrastructure, azureConfig);
    updateSlotTrafficWeight(deploymentSlot, webClientContext, trafficPercent, logCallbackProvider);

    return AzureWebAppTrafficShiftResponse.builder().deploymentProgressMarker(SLOT_TRAFFIC_PERCENTAGE).build();
  }

  @Override
  protected Class<AzureWebAppTrafficShiftRequest> getRequestType() {
    return AzureWebAppTrafficShiftRequest.class;
  }

  private void updateSlotTrafficWeight(String deploymentSlot, AzureWebClientContext webClientContext,
      double trafficPercent, AzureLogCallbackProvider logCallbackProvider) {
    azureAppServiceDeploymentService.rerouteProductionSlotTraffic(
        webClientContext, deploymentSlot, trafficPercent, logCallbackProvider);
  }
}
