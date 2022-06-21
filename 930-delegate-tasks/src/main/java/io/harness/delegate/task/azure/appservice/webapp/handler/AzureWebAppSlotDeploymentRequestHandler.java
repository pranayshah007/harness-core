/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.exception.AzureWebAppSlotDeploymentExceptionData;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSlotDeploymentRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppSlotDeploymentResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class AzureWebAppSlotDeploymentRequestHandler
    extends AzureWebAppRequestHandler<AzureWebAppSlotDeploymentRequest> {
  @Inject private AzureAppServiceResourceUtilities azureAppServiceResourceUtilities;

  @Override
  protected AzureWebAppRequestResponse execute(AzureWebAppSlotDeploymentRequest taskRequest, AzureConfig azureConfig,
      AzureLogCallbackProvider logCallbackProvider) {
    AzureArtifactConfig artifactConfig = taskRequest.getArtifact();
    switch (artifactConfig.getArtifactType()) {
      case CONTAINER:
        return executeContainer(taskRequest, azureConfig, logCallbackProvider);
      case PACKAGE:
      default:
        throw new UnsupportedOperationException(
            format("Artifact type [%s] is not supported yet", artifactConfig.getArtifactType()));
    }
  }

  @Override
  protected Class<AzureWebAppSlotDeploymentRequest> getRequestType() {
    return AzureWebAppSlotDeploymentRequest.class;
  }

  private AzureWebAppRequestResponse executeContainer(AzureWebAppSlotDeploymentRequest taskRequest,
      AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider) {
    AzureWebClientContext azureWebClientContext =
        buildAzureWebClientContext(taskRequest.getInfrastructure(), azureConfig);
    AzureWebAppInfraDelegateConfig infrastructure = taskRequest.getInfrastructure();
    AzureAppServiceDockerDeploymentContext dockerDeploymentContext =
        toAzureAppServiceDockerDeploymentContext(taskRequest, azureConfig, azureWebClientContext, logCallbackProvider);
    AzureAppServicePreDeploymentData preDeploymentData = taskRequest.getPreDeploymentData();

    try {
      azureAppServiceDeploymentService.deployDockerImage(dockerDeploymentContext, preDeploymentData);
      List<AzureAppDeploymentData> azureAppDeploymentData =
          azureAppServiceService.fetchDeploymentData(azureWebClientContext, infrastructure.getDeploymentSlot());

      return AzureWebAppSlotDeploymentResponse.builder()
          .azureAppDeploymentData(azureAppDeploymentData)
          .deploymentProgressMarker(preDeploymentData.getDeploymentProgressMarker())
          .build();
    } catch (Exception e) {
      throw new AzureWebAppSlotDeploymentExceptionData(preDeploymentData.getDeploymentProgressMarker(), e);
    }
  }

  private AzureAppServiceDockerDeploymentContext toAzureAppServiceDockerDeploymentContext(
      AzureWebAppSlotDeploymentRequest taskRequest, AzureConfig azureConfig, AzureWebClientContext clientContext,
      AzureLogCallbackProvider logCallbackProvider) {
    AzureContainerArtifactConfig artifactConfig = (AzureContainerArtifactConfig) taskRequest.getArtifact();
    AzureWebAppInfraDelegateConfig infrastructure = taskRequest.getInfrastructure();
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        azureAppServiceResourceUtilities.getAppSettingsToAdd(taskRequest.getApplicationSettings());
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        azureAppServiceResourceUtilities.getConnectionSettingsToAdd(taskRequest.getConnectionStrings());
    Map<String, AzureAppServiceApplicationSetting> dockerSettings = azureAppServiceResourceUtilities.getDockerSettings(
        artifactConfig.getConnectorConfig(), artifactConfig.getRegistryType(), azureConfig);

    String imagePathAndTag =
        AzureResourceUtility.getDockerImageFullNameAndTag(artifactConfig.getImage(), artifactConfig.getTag());

    return AzureAppServiceDockerDeploymentContext.builder()
        .logCallbackProvider(logCallbackProvider)
        .startupCommand(taskRequest.getStartupCommand())
        .slotName(infrastructure.getDeploymentSlot())
        .azureWebClientContext(clientContext)
        .appSettingsToAdd(appSettingsToAdd)
        .connSettingsToAdd(connSettingsToAdd)
        .dockerSettings(dockerSettings)
        .imagePathAndTag(imagePathAndTag)
        .steadyStateTimeoutInMin(
            azureAppServiceResourceUtilities.getTimeoutIntervalInMin(taskRequest.getTimeoutIntervalInMin()))
        .skipTargetSlotValidation(true)
        .build();
  }
}
