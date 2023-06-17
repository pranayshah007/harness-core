/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_NAME;
import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
import static io.harness.azure.model.AzureConstants.NO_TRAFFIC_SHIFT_REQUIRED;
import static io.harness.azure.model.AzureConstants.REPOSITORY_DIR_PATH;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.START_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.STOP_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.UPDATE_DEPLOYMENT_SLOT_CONTAINER_SETTINGS;
import static io.harness.azure.model.AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress.SAVE_CONFIGURATION;
import static io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress.STOP_SLOT;
import static io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress.UPDATE_SLOT_CONFIGURATIONS_SETTINGS;
import static io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress.UPDATE_SLOT_CONTAINER_SETTINGS;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.delegate.beans.azure.mapper.AzureAppServiceConfigurationDTOMapper;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.exception.AzureWebAppRollbackExceptionData;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppRollbackRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppNGRollbackResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.artifact.ArtifactDownloadContext;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadResponse;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadService;
import io.harness.delegate.task.azure.artifact.AzurePackageArtifactConfig;
import io.harness.delegate.task.azure.common.AutoCloseableWorkingDirectory;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class AzureWebAppRollbackRequestHandler extends AzureWebAppRequestHandler<AzureWebAppRollbackRequest> {
  @Inject private AzureArtifactDownloadService artifactDownloaderService;

  @Override
  protected AzureWebAppRequestResponse execute(
      AzureWebAppRollbackRequest taskRequest, AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider) {
    azureSecretHelper.decryptAzureWebAppRollbackParameters(taskRequest.getPreDeploymentData());
    switch (taskRequest.getAzureArtifactType()) {
      case CONTAINER:
        return executeContainer(taskRequest, azureConfig, logCallbackProvider);
      case PACKAGE:
        return executePackage(taskRequest, azureConfig, logCallbackProvider);
      default:
        throw new UnsupportedOperationException(
            format("Artifact type [%s] is not supported yet", taskRequest.getAzureArtifactType()));
    }
  }

  @Override
  protected Class<AzureWebAppRollbackRequest> getRequestType() {
    return AzureWebAppRollbackRequest.class;
  }

  private AzureWebAppRequestResponse executeContainer(
      AzureWebAppRollbackRequest taskRequest, AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider) {
    log.info("Rollback using container artifact");
    azureSecretHelper.decryptAzureWebAppRollbackParameters(taskRequest.getPreDeploymentData());
    AzureWebClientContext azureWebClientContext =
        buildAzureWebClientContext(taskRequest.getInfrastructure(), azureConfig, true);
    AzureAppServiceDockerDeploymentContext dockerDeploymentContext =
        toAzureAppServiceDockerDeploymentContext(taskRequest, azureWebClientContext, logCallbackProvider);

    try {
      performRollback(logCallbackProvider, taskRequest, azureWebClientContext, dockerDeploymentContext, azureConfig);
      List<AzureAppDeploymentData> azureAppDeploymentData =
          getAppServiceDeploymentData(taskRequest, azureWebClientContext);

      markDeploymentStatusAsSuccess(taskRequest, logCallbackProvider);

      return AzureWebAppNGRollbackResponse.builder()
          .azureAppDeploymentData(azureAppDeploymentData)
          .preDeploymentData(taskRequest.getPreDeploymentData())
          .deploymentProgressMarker(taskRequest.getPreDeploymentData().getDeploymentProgressMarker())
          .build();
    } catch (Exception e) {
      throw new AzureWebAppRollbackExceptionData(taskRequest.getPreDeploymentData().getDeploymentProgressMarker(), e);
    }
  }

  private AzureWebAppNGRollbackResponse executePackage(
      AzureWebAppRollbackRequest taskRequest, AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider) {
    log.info("Rollback using package artifact");
    AzureWebClientContext azureWebClientContext =
        buildAzureWebClientContext(taskRequest.getInfrastructure(), azureConfig, true);
    try (AutoCloseableWorkingDirectory autoCloseableWorkingDirectory =
             new AutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH)) {
      AzureAppServicePackageDeploymentContext deploymentContext = toAzureAppServicePackageDeploymentContext(
          taskRequest, azureWebClientContext, autoCloseableWorkingDirectory, logCallbackProvider);

      performRollback(logCallbackProvider, taskRequest, azureWebClientContext, deploymentContext, azureConfig);
    }

    List<AzureAppDeploymentData> azureAppDeploymentData =
        getAppServiceDeploymentData(taskRequest, azureWebClientContext);

    markDeploymentStatusAsSuccess(taskRequest, logCallbackProvider);
    return AzureWebAppNGRollbackResponse.builder()
        .azureAppDeploymentData(azureAppDeploymentData)
        .preDeploymentData(taskRequest.getPreDeploymentData())
        .build();
  }

  private AzureAppServicePackageDeploymentContext toAzureAppServicePackageDeploymentContext(
      AzureWebAppRollbackRequest taskRequest, AzureWebClientContext azureWebClientContext,
      AutoCloseableWorkingDirectory autoCloseableWorkingDirectory, AzureLogCallbackProvider logCallbackProvider) {
    AzureArtifactDownloadResponse artifactResponse;
    AzurePackageArtifactConfig artifactConfig = (AzurePackageArtifactConfig) taskRequest.getArtifact();
    artifactResponse = null;
    if (artifactConfig != null) {
      ArtifactDownloadContext downloadContext = azureAppServiceResourceUtilities.toArtifactNgDownloadContext(
          artifactConfig, autoCloseableWorkingDirectory, logCallbackProvider);
      artifactResponse = artifactDownloaderService.download(downloadContext);
    }

    AzureAppServicePreDeploymentData preDeploymentData = taskRequest.getPreDeploymentData();
    return AzureAppServicePackageDeploymentContext.builder()
        .logCallbackProvider(logCallbackProvider)
        .appSettingsToAdd(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(
            preDeploymentData.getAppSettingsToAdd()))
        .appSettingsToRemove(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(
            preDeploymentData.getAppSettingsToRemove()))
        .connSettingsToAdd(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStrings(
            preDeploymentData.getConnStringsToAdd()))
        .connSettingsToRemove(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStrings(
            preDeploymentData.getConnStringsToRemove()))
        .slotName(preDeploymentData.getSlotName())
        .artifactFile(artifactResponse != null ? artifactResponse.getArtifactFile() : null)
        .artifactType(artifactResponse != null ? artifactResponse.getArtifactType() : ArtifactType.ZIP)
        .azureWebClientContext(azureWebClientContext)
        .startupCommand(preDeploymentData.getStartupCommand())
        .steadyStateTimeoutInMin(
            azureAppServiceResourceUtilities.getTimeoutIntervalInMin(taskRequest.getTimeoutIntervalInMin()))
        .isBasicDeployment(DEPLOYMENT_SLOT_PRODUCTION_NAME.equalsIgnoreCase(preDeploymentData.getSlotName()))
        .build();
  }

  private void performRollback(AzureLogCallbackProvider logCallbackProvider, AzureWebAppRollbackRequest taskRequest,
      AzureWebClientContext azureWebClientContext, AzureAppServiceDeploymentContext deploymentContext,
      AzureConfig azureConfig) {
    AppServiceDeploymentProgress progressMarker = getProgressMarker(taskRequest);
    log.info(String.format("Starting rollback from previous marker - [%s]", progressMarker.getStepName()));

    switch (progressMarker) {
      case SAVE_CONFIGURATION:
        rollbackFromSaveConfigurationState(logCallbackProvider);
        break;
      case STOP_SLOT:
        rollbackFromStopSlotState(logCallbackProvider, taskRequest, deploymentContext);
        break;
      case UPDATE_SLOT_CONFIGURATIONS_SETTINGS:
        rollbackFromUpdateConfigurationState(logCallbackProvider, taskRequest, deploymentContext);
        break;
      case UPDATE_SLOT_CONTAINER_SETTINGS:
      case DEPLOY_TO_SLOT:
        rollbackSetupSlot(taskRequest, deploymentContext);
        rollbackTrafficShift(logCallbackProvider, taskRequest, azureWebClientContext, deploymentContext);
        break;
      case SWAP_SLOT:
        swapSlots(azureConfig, logCallbackProvider, taskRequest);
        rollbackSetupSlot(taskRequest, deploymentContext);
        rollbackTrafficShift(logCallbackProvider, taskRequest, azureWebClientContext, deploymentContext);
        break;
      case DEPLOYMENT_COMPLETE:
        noRollback(logCallbackProvider);
        break;

      default:
        break;
    }
  }

  private void swapSlots(
      AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider, AzureWebAppRollbackRequest taskRequest) {
    AzureWebClientContext webClientContext =
        buildAzureWebClientContext(taskRequest.getInfrastructure(), azureConfig, true);
    azureAppServiceResourceUtilities.swapSlots(webClientContext, logCallbackProvider,
        taskRequest.getInfrastructure().getDeploymentSlot(), taskRequest.getTargetSlot(),
        taskRequest.getTimeoutIntervalInMin());
  }

  private AzureAppServiceDockerDeploymentContext toAzureAppServiceDockerDeploymentContext(
      AzureWebAppRollbackRequest taskRequest, AzureWebClientContext azureWebClientContext,
      AzureLogCallbackProvider logCallbackProvider) {
    AzureAppServicePreDeploymentData preDeploymentData = taskRequest.getPreDeploymentData();

    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(preDeploymentData.getAppSettingsToAdd());
    Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove =
        AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(preDeploymentData.getAppSettingsToRemove());
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStrings(preDeploymentData.getConnStringsToAdd());
    Map<String, AzureAppServiceConnectionString> connSettingsToRemove =
        AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStrings(preDeploymentData.getConnStringsToRemove());
    Map<String, AzureAppServiceApplicationSetting> dockerSettings =
        AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(preDeploymentData.getDockerSettingsToAdd());

    return AzureAppServiceDockerDeploymentContext.builder()
        .logCallbackProvider(logCallbackProvider)
        .appSettingsToAdd(appSettingsToAdd)
        .appSettingsToRemove(appSettingsToRemove)
        .connSettingsToAdd(connSettingsToAdd)
        .connSettingsToRemove(connSettingsToRemove)
        .dockerSettings(dockerSettings)
        .imagePathAndTag(preDeploymentData.getImageNameAndTag())
        .slotName(preDeploymentData.getSlotName())
        .azureWebClientContext(azureWebClientContext)
        .startupCommand(preDeploymentData.getStartupCommand())
        .steadyStateTimeoutInMin(
            azureAppServiceResourceUtilities.getTimeoutIntervalInMin(taskRequest.getTimeoutIntervalInMin()))
        .isBasicDeployment(DEPLOYMENT_SLOT_PRODUCTION_NAME.equalsIgnoreCase(preDeploymentData.getSlotName()))
        .build();
  }

  private void noRollback(AzureLogCallbackProvider logCallbackProvider) {
    String message = "The previous deployment was complete. Hence nothing to revert during rollback";
    markCommandUnitAsDone(logCallbackProvider, STOP_DEPLOYMENT_SLOT, message);
    markCommandUnitAsDone(logCallbackProvider, UPDATE_SLOT_CONFIGURATION_SETTINGS, message);
    markCommandUnitAsDone(logCallbackProvider, UPDATE_DEPLOYMENT_SLOT_CONTAINER_SETTINGS, message);
    markCommandUnitAsDone(logCallbackProvider, START_DEPLOYMENT_SLOT, message);
    markCommandUnitAsDone(logCallbackProvider, SLOT_TRAFFIC_PERCENTAGE, message);
  }

  private void rollbackTrafficShift(AzureLogCallbackProvider logCallbackProvider,
      AzureWebAppRollbackRequest taskRequest, AzureWebClientContext azureWebClientContext,
      AzureAppServiceDeploymentContext deploymentContext) {
    AzureAppServicePreDeploymentData preDeploymentData = taskRequest.getPreDeploymentData();
    if (!deploymentContext.isBasicDeployment()
        && isTrafficWeightDifferent(azureWebClientContext, deploymentContext, preDeploymentData)) {
      rollbackUpdateSlotTrafficWeight(preDeploymentData, azureWebClientContext, logCallbackProvider);
    }
    LogCallback rerouteTrafficLogCallback = logCallbackProvider.obtainLogCallback(SLOT_TRAFFIC_PERCENTAGE);
    rerouteTrafficLogCallback.saveExecutionLog(NO_TRAFFIC_SHIFT_REQUIRED, INFO, SUCCESS);
  }

  private boolean isTrafficWeightDifferent(AzureWebClientContext azureWebClientContext,
      AzureAppServiceDeploymentContext deploymentContext, AzureAppServicePreDeploymentData preDeploymentData) {
    double slotTrafficWeight =
        azureAppServiceService.getSlotTrafficWeight(azureWebClientContext, deploymentContext.getSlotName());
    return slotTrafficWeight != preDeploymentData.getTrafficWeight();
  }

  private void rollbackUpdateSlotTrafficWeight(AzureAppServicePreDeploymentData preDeploymentData,
      AzureWebClientContext azureWebClientContext, AzureLogCallbackProvider logCallbackProvider) {
    double trafficWeight = preDeploymentData.getTrafficWeight();
    String slotName = preDeploymentData.getSlotName();
    azureAppServiceDeploymentService.rerouteProductionSlotTraffic(
        azureWebClientContext, slotName, trafficWeight, logCallbackProvider);
  }

  private void rollbackSetupSlot(
      AzureWebAppRollbackRequest taskRequest, AzureAppServiceDeploymentContext deploymentContext) {
    if ((deploymentContext instanceof AzureAppServicePackageDeploymentContext)
        && ((AzureAppServicePackageDeploymentContext) deploymentContext).getArtifactFile() == null) {
      LogCallback updateSlotLogCallback =
          deploymentContext.getLogCallbackProvider().obtainLogCallback(UPDATE_SLOT_CONFIGURATION_SETTINGS);
      updateSlotLogCallback.saveExecutionLog(
          "Skip Update Slot Configuration Settings as no previous successful deployment found", INFO, SUCCESS);
      LogCallback deploySlotLogCallback = deploymentContext.getLogCallbackProvider().obtainLogCallback(DEPLOY_TO_SLOT);
      deploySlotLogCallback.saveExecutionLog(
          "Skip Deploying to Slot as no previous successful deployment found", INFO, SUCCESS);
      return;
    }
    deploymentContext.deploy(azureAppServiceDeploymentService, taskRequest.getPreDeploymentData());
  }

  private void rollbackFromUpdateConfigurationState(AzureLogCallbackProvider logCallbackProvider,
      AzureWebAppRollbackRequest taskRequest, AzureAppServiceDeploymentContext deploymentContext) {
    LogCallback logCallback = logCallbackProvider.obtainLogCallback(UPDATE_SLOT_CONFIGURATION_SETTINGS);
    azureAppServiceDeploymentService.updateDeploymentSlotConfigurationSettings(
        deploymentContext, taskRequest.getPreDeploymentData(), logCallback);

    markCommandUnitAsDone(
        logCallbackProvider, UPDATE_SLOT_CONFIGURATION_SETTINGS, "Reverted the slot configuration settings");

    String message = "No artifact/image was deployed during deployment. Hence skipping this step";
    markCommandUnitAsDone(logCallbackProvider, DEPLOY_TO_SLOT, message);

    message = "Slot traffic was not changed. Hence skipping this step";
    markCommandUnitAsDone(logCallbackProvider, SLOT_TRAFFIC_PERCENTAGE, message);
  }

  private void rollbackFromStopSlotState(AzureLogCallbackProvider logCallbackProvider,
      AzureWebAppRollbackRequest taskRequest, AzureAppServiceDeploymentContext deploymentContext) {
    String message = "Slot configuration was not changed. Hence skipping this step";
    markCommandUnitAsDone(logCallbackProvider, UPDATE_SLOT_CONFIGURATION_SETTINGS, message);

    LogCallback deployLogCallback = logCallbackProvider.obtainLogCallback(DEPLOY_TO_SLOT);
    azureAppServiceDeploymentService.startSlotAsyncWithSteadyCheck(
        deploymentContext, taskRequest.getPreDeploymentData(), deployLogCallback);
    markCommandUnitAsDone(logCallbackProvider, DEPLOY_TO_SLOT, "Rollback completed");
    message = "Slot traffic was not changed. Hence skipping this step";
    markCommandUnitAsDone(logCallbackProvider, SLOT_TRAFFIC_PERCENTAGE, message);
  }

  protected void markDeploymentStatusAsSuccess(
      AzureWebAppRollbackRequest taskRequest, AzureLogCallbackProvider logCallbackProvider) {
    LogCallback logCallback = logCallbackProvider.obtainLogCallback(AzureConstants.DEPLOYMENT_STATUS);
    logCallback.saveExecutionLog(
        String.format("The following task - [%s] completed successfully", taskRequest.getRequestType().name()),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  private List<AzureAppDeploymentData> getAppServiceDeploymentData(
      AzureWebAppRollbackRequest taskRequest, AzureWebClientContext azureWebClientContext) {
    if (slotDeploymentDidNotHappen(taskRequest)) {
      return Collections.emptyList();
    }
    return azureAppServiceService.fetchDeploymentData(
        azureWebClientContext, taskRequest.getPreDeploymentData().getSlotName());
  }

  private boolean slotDeploymentDidNotHappen(AzureWebAppRollbackRequest taskRequest) {
    AppServiceDeploymentProgress progressMarker = getProgressMarker(taskRequest);
    return (progressMarker == SAVE_CONFIGURATION) || (progressMarker == STOP_SLOT)
        || (progressMarker == UPDATE_SLOT_CONFIGURATIONS_SETTINGS)
        || (progressMarker == UPDATE_SLOT_CONTAINER_SETTINGS);
  }

  private AppServiceDeploymentProgress getProgressMarker(AzureWebAppRollbackRequest taskRequest) {
    AzureAppServicePreDeploymentData preDeploymentData = taskRequest.getPreDeploymentData();
    String deploymentProgressMarker = preDeploymentData.getDeploymentProgressMarker();
    return AppServiceDeploymentProgress.valueOf(deploymentProgressMarker);
  }

  private void rollbackFromSaveConfigurationState(AzureLogCallbackProvider logCallbackProvider) {
    String message = "The previous deployment did not start. Hence nothing to revert during rollback";
    markCommandUnitAsDone(logCallbackProvider, UPDATE_SLOT_CONFIGURATION_SETTINGS, message);
    markCommandUnitAsDone(logCallbackProvider, DEPLOY_TO_SLOT, message);
    markCommandUnitAsDone(logCallbackProvider, SLOT_TRAFFIC_PERCENTAGE, message);
  }

  private void markCommandUnitAsDone(AzureLogCallbackProvider logCallbackProvider, String commandUnit, String message) {
    LogCallback logCallback = logCallbackProvider.obtainLogCallback(commandUnit);
    logCallback.saveExecutionLog(
        String.format("Message - [%s]", message), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }
}
