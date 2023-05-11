/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTaskRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.exception.InvalidArgumentsException;

import software.wings.delegatetasks.azure.AzureSecretHelper;

import com.google.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
public abstract class AzureWebAppRequestHandler<T extends AzureWebAppTaskRequest> {
  @Inject private AzureConnectorMapper connectorMapper;
  @Inject protected AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @Inject protected AzureAppServiceService azureAppServiceService;
  @Inject protected AzureAppServiceResourceUtilities azureAppServiceResourceUtilities;
  @Inject protected AzureSecretHelper azureSecretHelper;

  public final AzureWebAppRequestResponse handleRequest(
      AzureWebAppTaskRequest azureWebAppTaskRequest, AzureLogCallbackProvider logCallbackProvider) {
    if (!getRequestType().isAssignableFrom(azureWebAppTaskRequest.getClass())) {
      throw new InvalidArgumentsException(Pair.of("azureWebAppTaskRequest",
          String.format("Unexpected type of task request [%s], expected: [%s]",
              azureWebAppTaskRequest.getClass().getSimpleName(), getRequestType().getSimpleName())));
    }

    AzureConnectorDTO connectorDTO = azureWebAppTaskRequest.getInfrastructure().getAzureConnectorDTO();
    return execute((T) azureWebAppTaskRequest, connectorMapper.toAzureConfig(connectorDTO), logCallbackProvider);
  }

  protected AzureWebClientContext buildAzureWebClientContext(
      AzureWebAppInfraDelegateConfig infrastructure, AzureConfig azureConfig, boolean useExtendedReadTimeout) {
    return AzureWebClientContext.builder()
        .azureConfig(azureConfig)
        .appName(infrastructure.getAppName())
        .subscriptionId(infrastructure.getSubscription())
        .resourceGroupName(infrastructure.getResourceGroup())
        .extendedReadTimeout(useExtendedReadTimeout)
        .build();
  }

  protected AzureWebClientContext buildAzureWebClientContext(
      AzureWebAppInfraDelegateConfig infrastructure, AzureConfig azureConfig) {
    return buildAzureWebClientContext(infrastructure, azureConfig, false);
  }

  protected abstract AzureWebAppRequestResponse execute(
      T taskRequest, AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider);

  protected abstract Class<T> getRequestType();
}
