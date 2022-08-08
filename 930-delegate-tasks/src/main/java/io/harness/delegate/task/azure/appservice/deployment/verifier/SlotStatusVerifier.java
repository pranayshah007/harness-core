/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment.verifier;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_NAME;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureServiceCallBack;
import io.harness.azure.client.AzureMonitorClient;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.delegate.task.azure.appservice.deployment.context.SlotContainerDeploymentVerifierContext;
import io.harness.delegate.task.azure.appservice.deployment.context.SlotDeploymentVerifierContext;
import io.harness.delegate.task.azure.appservice.deployment.context.StatusVerifierContext;
import io.harness.delegate.task.azure.appservice.deployment.context.SwapSlotStatusVerifierContext;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;

import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.appservice.WebApp;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDP)
public abstract class SlotStatusVerifier {
  protected final String slotName;
  protected final AzureWebClient azureWebClient;
  protected final AzureServiceCallBack restCallBack;
  protected final LogCallback logCallback;
  protected final AzureWebClientContext azureWebClientContext;

  public enum SlotStatus { STOPPED, RUNNING }
  public enum SlotStatusVerifierType {
    STOP_VERIFIER,
    START_VERIFIER,
    SWAP_VERIFIER,
    SLOT_DEPLOYMENT_VERIFIER,
    SLOT_CONTAINER_DEPLOYMENT_VERIFIER
  }

  public SlotStatusVerifier(LogCallback logCallback, String slotName, AzureWebClient azureWebClient,
      AzureWebClientContext azureWebClientContext, AzureServiceCallBack restCallBack) {
    this.logCallback = logCallback;
    this.slotName = slotName;
    this.azureWebClient = azureWebClient;
    this.azureWebClientContext = azureWebClientContext;
    this.restCallBack = restCallBack;
  }

  public boolean hasReachedSteadyState() {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      WebApp webapp = getWebApp();
      String currentAppState = webapp.state();
      logCallback.saveExecutionLog(color(format("%nCurrent state for WebApp is - [%s]", currentAppState), White, Bold));
      return getSteadyState().equalsIgnoreCase(currentAppState);
    }
    DeploymentSlot slot = getDeploymentSlot();
    String currentSlotState = slot.state();
    logCallback.saveExecutionLog(
        color(format("%nCurrent state for deployment slot is - [%s]", currentSlotState), White, Bold));
    return getSteadyState().equalsIgnoreCase(currentSlotState);
  }

  public boolean operationFailed() {
    return restCallBack.callFailed();
  }

  public String getErrorMessage() {
    return restCallBack.getErrorMessage();
  }

  public void stopPolling() {
    logCallback.saveExecutionLog("All operations is paused");
  }

  public abstract String getSteadyState();

  protected WebApp getWebApp() {
    Optional<WebApp> webAppOptional = azureWebClient.getWebAppByName(azureWebClientContext);
    return webAppOptional.orElseThrow(
        () -> new InvalidRequestException(format("Unable to find WebApp with name: %s", slotName)));
  }
  protected DeploymentSlot getDeploymentSlot() {
    Optional<DeploymentSlot> deploymentSlotOptional =
        azureWebClient.getDeploymentSlotByName(azureWebClientContext, slotName);
    return deploymentSlotOptional.orElseThrow(
        () -> new InvalidRequestException(format("Unable to find deployment slot with name: %s", slotName)));
  }

  public static SlotStatusVerifier getStatusVerifier(String verifierType, LogCallback logCallback, String slotName,
      AzureWebClient azureWebClient, AzureMonitorClient azureMonitorClient, AzureWebClientContext azureWebClientContext,
      AzureServiceCallBack restCallBack) {
    switch (verifierType) {
      case "STOP_VERIFIER":
        return new StopSlotStatusVerifier(logCallback, slotName, azureWebClient, azureWebClientContext, restCallBack);
      case "START_VERIFIER":
        return new StartSlotStatusVerifier(logCallback, slotName, azureWebClient, azureWebClientContext, restCallBack);
      case "SWAP_VERIFIER":
        return new SwapSlotStatusVerifier(
            logCallback, slotName, azureWebClient, azureMonitorClient, azureWebClientContext, restCallBack);
      default:
        throw new InvalidRequestException(String.format("No slot status verifier defined for - [%s]", verifierType));
    }
  }

  public static SlotStatusVerifier getStatusVerifier(String verifierType, StatusVerifierContext context) {
    switch (verifierType) {
      case "STOP_VERIFIER":
        return new StopSlotStatusVerifier(context);
      case "START_VERIFIER":
        return new StartSlotStatusVerifier(context);
      case "SWAP_VERIFIER":
        return new SwapSlotStatusVerifier((SwapSlotStatusVerifierContext) context);
      case "SLOT_DEPLOYMENT_VERIFIER":
        return new SlotPackageDeploymentStatusVerifier((SlotDeploymentVerifierContext) context);
      case "SLOT_CONTAINER_DEPLOYMENT_VERIFIER":
        return new SlotContainerDeploymentStatusVerifier((SlotContainerDeploymentVerifierContext) context);

      default:
        throw new InvalidRequestException(String.format("No slot status verifier defined for - [%s]", verifierType));
    }
  }
}
