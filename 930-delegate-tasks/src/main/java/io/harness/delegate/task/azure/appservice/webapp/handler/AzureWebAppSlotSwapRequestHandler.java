/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSwapSlotsRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppSwapSlotsResponseNG;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class AzureWebAppSlotSwapRequestHandler extends AzureWebAppRequestHandler<AzureWebAppSwapSlotsRequest> {
  @Override
  protected AzureWebAppRequestResponse execute(
      AzureWebAppSwapSlotsRequest taskRequest, AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider) {
    AzureWebAppInfraDelegateConfig infrastructure = taskRequest.getInfrastructure();
    Integer timeoutIntervalInMin = taskRequest.getTimeoutIntervalInMin();
    String webAppName = infrastructure.getAppName();
    String sourceSlot = infrastructure.getDeploymentSlot();
    String targetSlot = taskRequest.getTargetSlot();
    azureAppServiceResourceUtilities.validateSlotSwapParameters(webAppName, sourceSlot, targetSlot);

    AzureWebClientContext webClientContext = buildAzureWebClientContext(infrastructure, azureConfig, true);
    azureAppServiceResourceUtilities.swapSlots(
        webClientContext, logCallbackProvider, infrastructure.getDeploymentSlot(), targetSlot, timeoutIntervalInMin);

    return AzureWebAppSwapSlotsResponseNG.builder().deploymentProgressMarker(SLOT_SWAP).build();
  }

  @Override
  protected Class<AzureWebAppSwapSlotsRequest> getRequestType() {
    return AzureWebAppSwapSlotsRequest.class;
  }
}
