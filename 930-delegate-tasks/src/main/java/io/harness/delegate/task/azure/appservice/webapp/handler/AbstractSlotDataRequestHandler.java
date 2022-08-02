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
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConfiguration;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AbstractSlotDataRequest;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadResponse;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureRegistrySettingsAdapter;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public abstract class AbstractSlotDataRequestHandler<T extends AbstractSlotDataRequest>
    extends AzureWebAppRequestHandler<T> {
  @Inject private AzureRegistrySettingsAdapter azureRegistrySettingsAdapter;
  @Inject protected AzureAppServiceResourceUtilities azureResourceUtilities;

  protected AzureAppServiceDockerDeploymentContext toAzureAppServiceDockerDeploymentContext(
      T taskRequest, AzureWebClientContext clientContext, AzureLogCallbackProvider logCallbackProvider) {
    AzureContainerArtifactConfig artifactConfig = (AzureContainerArtifactConfig) taskRequest.getArtifact();
    AzureWebAppInfraDelegateConfig infrastructure = taskRequest.getInfrastructure();
    AzureAppServiceConfiguration appServiceConfiguration =
        AzureAppServiceConfiguration.builder()
            .connStringsJSON(taskRequest.getConnectionStrings() != null
                    ? taskRequest.getConnectionStrings().fetchFileContent()
                    : null)
            .appSettingsJSON(taskRequest.getApplicationSettings() != null
                    ? taskRequest.getApplicationSettings().fetchFileContent()
                    : null)
            .build();
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        azureResourceUtilities.getAppSettingsToAdd(appServiceConfiguration.getAppSettings());
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        azureResourceUtilities.getConnectionSettingsToAdd(appServiceConfiguration.getConnStrings());
    Map<String, AzureAppServiceApplicationSetting> dockerSettings =
        azureRegistrySettingsAdapter.getContainerSettings(artifactConfig);

    String imagePathAndTag =
        AzureResourceUtility.getDockerImageFullNameAndTag(artifactConfig.getImage(), artifactConfig.getTag());

    return AzureAppServiceDockerDeploymentContext.builder()
        .logCallbackProvider(logCallbackProvider)
        .startupCommand(
            taskRequest.getStartupCommand() != null ? taskRequest.getStartupCommand().fetchFileContent() : null)
        .slotName(infrastructure.getDeploymentSlot())
        .azureWebClientContext(clientContext)
        .appSettingsToAdd(appSettingsToAdd)
        .connSettingsToAdd(connSettingsToAdd)
        .dockerSettings(dockerSettings)
        .imagePathAndTag(imagePathAndTag)
        .steadyStateTimeoutInMin(azureResourceUtilities.getTimeoutIntervalInMin(taskRequest.getTimeoutIntervalInMin()))
        .skipTargetSlotValidation(true)
        .build();
  }

  protected AzureAppServicePackageDeploymentContext toAzureAppServicePackageDeploymentContext(T taskRequest,
      AzureWebClientContext clientContext, AzureArtifactDownloadResponse artifactResponse,
      AzureLogCallbackProvider logCallbackProvider) {
    AzureAppServiceConfiguration appServiceConfiguration =
        AzureAppServiceConfiguration.builder()
            .connStringsJSON(taskRequest.getConnectionStrings() != null
                    ? taskRequest.getConnectionStrings().fetchFileContent()
                    : null)
            .appSettingsJSON(taskRequest.getApplicationSettings() != null
                    ? taskRequest.getApplicationSettings().fetchFileContent()
                    : null)
            .build();

    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        azureResourceUtilities.getAppSettingsToAdd(appServiceConfiguration.getAppSettings());
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        azureResourceUtilities.getConnectionSettingsToAdd(appServiceConfiguration.getConnStrings());

    return AzureAppServicePackageDeploymentContext.builder()
        .logCallbackProvider(logCallbackProvider)
        .appSettingsToAdd(appSettingsToAdd)
        .connSettingsToAdd(connSettingsToAdd)
        .slotName(taskRequest.getInfrastructure().getDeploymentSlot())
        .azureWebClientContext(clientContext)
        .startupCommand(
            taskRequest.getStartupCommand() != null ? taskRequest.getStartupCommand().fetchFileContent() : null)
        .artifactFile(artifactResponse != null ? artifactResponse.getArtifactFile() : null)
        .artifactType(artifactResponse != null ? artifactResponse.getArtifactType() : ArtifactType.ZIP)
        .steadyStateTimeoutInMin(azureResourceUtilities.getTimeoutIntervalInMin(taskRequest.getTimeoutIntervalInMin()))
        .skipTargetSlotValidation(true)
        .build();
  }
}
