/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import static io.harness.azure.model.AzureConstants.AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.azure.model.AzureConstants.REPOSITORY_DIR_PATH;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.delegate.beans.azure.registry.AzureRegistry;
import io.harness.delegate.beans.azure.registry.AzureRegistryFactory;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureContainerRegistryConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotSetupParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.delegate.task.azure.common.AzureContainerRegistryService;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.azure.appservice.webapp.AbstractAzureWebAppTaskHandler;
import software.wings.delegatetasks.azure.common.AutoCloseableWorkingDirectory;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppSlotSetupTaskHandler extends AbstractAzureWebAppTaskHandler {
  @Inject private AzureContainerRegistryService azureContainerRegistryService;

  @Override
  protected AzureAppServiceTaskResponse executeTaskInternal(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient,
      ArtifactStreamAttributes artifactStreamAttributes) {
    return artifactStreamAttributes == null
        ? executeDockerTask(azureAppServiceTaskParameters, azureConfig, logStreamingTaskClient)
        : executePackageTask(
            azureAppServiceTaskParameters, azureConfig, logStreamingTaskClient, artifactStreamAttributes);
  }

  private AzureAppServiceTaskResponse executeDockerTask(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient) {
    AzureWebAppSlotSetupParameters azureWebAppSlotSetupParameters =
        (AzureWebAppSlotSetupParameters) azureAppServiceTaskParameters;

    AzureWebClientContext azureWebClientContext =
        buildAzureWebClientContext(azureWebAppSlotSetupParameters, azureConfig);
    AzureAppServiceDockerDeploymentContext dockerDeploymentContext = toAzureAppServiceDockerDeploymentContext(
        azureWebAppSlotSetupParameters, azureWebClientContext, azureConfig, logStreamingTaskClient);
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        azureAppServiceService
            .getDefaultPreDeploymentDataBuilder(
                azureWebAppSlotSetupParameters.getAppName(), azureWebAppSlotSetupParameters.getSlotName())
            .build();
    try {
      azureAppServicePreDeploymentData =
          azureAppServiceService.getDockerDeploymentPreDeploymentData(dockerDeploymentContext);

      azureAppServiceDeploymentService.deployDockerImage(dockerDeploymentContext, azureAppServicePreDeploymentData);

      List<AzureAppDeploymentData> azureAppDeploymentData = azureAppServiceService.fetchDeploymentData(
          azureWebClientContext, azureWebAppSlotSetupParameters.getSlotName());
      markDeploymentStatusAsSuccess(azureAppServiceTaskParameters, logStreamingTaskClient);
      return AzureWebAppSlotSetupResponse.builder()
          .azureAppDeploymentData(azureAppDeploymentData)
          .preDeploymentData(azureAppServicePreDeploymentData)
          .build();
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String message = AzureResourceUtility.getAzureCloudExceptionMessage(sanitizedException);
      logErrorMsg(azureAppServiceTaskParameters, logStreamingTaskClient, sanitizedException, message);
      return AzureWebAppSlotSetupResponse.builder()
          .errorMsg(message)
          .preDeploymentData(azureAppServicePreDeploymentData)
          .build();
    }
  }

  public AzureAppServiceTaskResponse executePackageTask(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient,
      ArtifactStreamAttributes streamAttributes) {
    AzureWebAppSlotSetupParameters azureWebAppSlotSetupParameters =
        (AzureWebAppSlotSetupParameters) azureAppServiceTaskParameters;
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        azureAppServiceService
            .getDefaultPreDeploymentDataBuilder(
                azureWebAppSlotSetupParameters.getAppName(), azureWebAppSlotSetupParameters.getSlotName())
            .build();

    try (AutoCloseableWorkingDirectory autoCloseableWorkingDirectory =
             new AutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH)) {
      File artifactFile = getArtifactFile(
          azureWebAppSlotSetupParameters, streamAttributes, autoCloseableWorkingDirectory, logStreamingTaskClient);

      AzureWebClientContext azureWebClientContext =
          buildAzureWebClientContext(azureWebAppSlotSetupParameters, azureConfig);
      AzureAppServicePackageDeploymentContext packageDeploymentContext =
          toAzureAppServicePackageDeploymentContext(azureWebAppSlotSetupParameters, azureWebClientContext, artifactFile,
              streamAttributes.getArtifactType(), logStreamingTaskClient);
      azureAppServicePreDeploymentData =
          azureAppServiceService.getPackageDeploymentPreDeploymentData(packageDeploymentContext);

      azureAppServiceDeploymentService.deployPackage(packageDeploymentContext, azureAppServicePreDeploymentData);
      List<AzureAppDeploymentData> azureAppDeploymentData = azureAppServiceService.fetchDeploymentData(
          azureWebClientContext, azureWebAppSlotSetupParameters.getSlotName());
      markDeploymentStatusAsSuccess(azureAppServiceTaskParameters, logStreamingTaskClient);
      return AzureWebAppSlotSetupResponse.builder()
          .azureAppDeploymentData(azureAppDeploymentData)
          .preDeploymentData(azureAppServicePreDeploymentData)
          .build();
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String message = AzureResourceUtility.getAzureCloudExceptionMessage(sanitizedException);
      logErrorMsg(azureAppServiceTaskParameters, logStreamingTaskClient, sanitizedException, message);
      return AzureWebAppSlotSetupResponse.builder()
          .errorMsg(message)
          .preDeploymentData(azureAppServicePreDeploymentData)
          .build();
    }
  }

  private AzureAppServiceDockerDeploymentContext toAzureAppServiceDockerDeploymentContext(
      AzureWebAppSlotSetupParameters slotSetupParameters, AzureWebClientContext azureWebClientContext,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient) {
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        getAppSettingsToAdd(slotSetupParameters.getApplicationSettings());
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        getConnSettingsToAdd(slotSetupParameters.getConnectionStrings());

    Map<String, AzureAppServiceApplicationSetting> dockerSettings = getDockerSettings(
        slotSetupParameters.getConnectorConfigDTO(), slotSetupParameters.getAzureRegistryType(), azureConfig);
    String imagePathAndTag = AzureResourceUtility.getDockerImageFullNameAndTag(
        slotSetupParameters.getImageName(), slotSetupParameters.getImageTag());

    return AzureAppServiceDockerDeploymentContext.builder()
        .logCallbackProvider(logCallbackProviderFactory.createCg(logStreamingTaskClient))
        .appSettingsToAdd(appSettingsToAdd)
        .connSettingsToAdd(connSettingsToAdd)
        .dockerSettings(dockerSettings)
        .imagePathAndTag(imagePathAndTag)
        .slotName(slotSetupParameters.getSlotName())
        .targetSlotName(slotSetupParameters.getTargetSlotName())
        .startupCommand(slotSetupParameters.getStartupCommand())
        .azureWebClientContext(azureWebClientContext)
        .steadyStateTimeoutInMin(slotSetupParameters.getTimeoutIntervalInMin())
        .build();
  }

  private Map<String, AzureAppServiceApplicationSetting> getAppSettingsToAdd(
      List<AzureAppServiceApplicationSetting> applicationSettings) {
    return applicationSettings.stream().collect(
        Collectors.toMap(AzureAppServiceApplicationSetting::getName, Function.identity()));
  }

  private Map<String, AzureAppServiceConnectionString> getConnSettingsToAdd(
      List<AzureAppServiceConnectionString> connectionStrings) {
    return connectionStrings.stream().collect(
        Collectors.toMap(AzureAppServiceConnectionString::getName, Function.identity()));
  }

  private Map<String, AzureAppServiceApplicationSetting> getDockerSettings(
      ConnectorConfigDTO connectorConfigDTO, AzureRegistryType azureRegistryType, AzureConfig azureConfig) {
    AzureRegistry azureRegistry = AzureRegistryFactory.getAzureRegistry(azureRegistryType);
    Map<String, AzureAppServiceApplicationSetting> dockerSettings =
        azureRegistry.getContainerSettings(connectorConfigDTO);

    if (AzureRegistryType.ACR == azureRegistryType) {
      azureContainerRegistryService.updateACRDockerSettingByCredentials(
          (AzureContainerRegistryConnectorDTO) connectorConfigDTO, azureConfig, dockerSettings);
    }

    return dockerSettings;
  }

  private AzureAppServicePackageDeploymentContext toAzureAppServicePackageDeploymentContext(
      AzureWebAppSlotSetupParameters slotSetupParameters, AzureWebClientContext azureWebClientContext,
      File artifactFile, ArtifactType artifactType, ILogStreamingTaskClient logStreamingTaskClient) {
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        getAppSettingsToAdd(slotSetupParameters.getApplicationSettings());
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        getConnSettingsToAdd(slotSetupParameters.getConnectionStrings());

    return AzureAppServicePackageDeploymentContext.builder()
        .logCallbackProvider(logCallbackProviderFactory.createCg(logStreamingTaskClient))
        .appSettingsToAdd(appSettingsToAdd)
        .connSettingsToAdd(connSettingsToAdd)
        .slotName(slotSetupParameters.getSlotName())
        .targetSlotName(slotSetupParameters.getTargetSlotName())
        .azureWebClientContext(azureWebClientContext)
        .startupCommand(slotSetupParameters.getStartupCommand())
        .artifactFile(artifactFile)
        .artifactType(artifactType)
        .steadyStateTimeoutInMin(slotSetupParameters.getTimeoutIntervalInMin())
        .build();
  }
}
