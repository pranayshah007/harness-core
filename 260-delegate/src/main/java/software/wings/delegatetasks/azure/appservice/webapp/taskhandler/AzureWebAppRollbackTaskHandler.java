/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import static io.harness.azure.model.AzureConstants.AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
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

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.azure.mapper.AzureAppServiceConfigurationDTOMapper;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppRollbackParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.azure.appservice.webapp.AbstractAzureWebAppTaskHandler;
import software.wings.delegatetasks.azure.common.AutoCloseableWorkingDirectory;

import com.google.inject.Singleton;
import java.io.File;
import java.util.Collections;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppRollbackTaskHandler extends AbstractAzureWebAppTaskHandler {
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
    AzureWebAppRollbackParameters rollbackParameters = (AzureWebAppRollbackParameters) azureAppServiceTaskParameters;
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext(rollbackParameters, azureConfig);
    AzureAppServiceDockerDeploymentContext deploymentContext =
        toAzureAppServiceDockerDeploymentContext(rollbackParameters, azureWebClientContext, logStreamingTaskClient);

    performRollback(logStreamingTaskClient, rollbackParameters, azureWebClientContext, deploymentContext);

    List<AzureAppDeploymentData> azureAppDeploymentData =
        getAppServiceDeploymentData(rollbackParameters, azureWebClientContext);

    markDeploymentStatusAsSuccess(azureAppServiceTaskParameters, logStreamingTaskClient);
    return AzureWebAppSlotSetupResponse.builder()
        .azureAppDeploymentData(azureAppDeploymentData)
        .preDeploymentData(rollbackParameters.getPreDeploymentData())
        .build();
  }

  private AzureAppServiceTaskResponse executePackageTask(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient,
      ArtifactStreamAttributes artifactStreamAttributes) {
    AzureWebAppRollbackParameters rollbackParameters = (AzureWebAppRollbackParameters) azureAppServiceTaskParameters;

    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext(rollbackParameters, azureConfig);
    AzureAppServicePackageDeploymentContext deploymentContext = toAzureAppServicePackageDeploymentContext(
        rollbackParameters, azureWebClientContext, logStreamingTaskClient, artifactStreamAttributes);

    performRollback(logStreamingTaskClient, rollbackParameters, azureWebClientContext, deploymentContext);

    List<AzureAppDeploymentData> azureAppDeploymentData =
        getAppServiceDeploymentData(rollbackParameters, azureWebClientContext);

    markDeploymentStatusAsSuccess(azureAppServiceTaskParameters, logStreamingTaskClient);
    return AzureWebAppSlotSetupResponse.builder()
        .azureAppDeploymentData(azureAppDeploymentData)
        .preDeploymentData(rollbackParameters.getPreDeploymentData())
        .build();
  }

  private List<AzureAppDeploymentData> getAppServiceDeploymentData(
      AzureWebAppRollbackParameters rollbackParameters, AzureWebClientContext azureWebClientContext) {
    if (slotDeploymentDidNotHappen(rollbackParameters)) {
      return Collections.emptyList();
    }
    return azureAppServiceService.fetchDeploymentData(
        azureWebClientContext, rollbackParameters.getPreDeploymentData().getSlotName());
  }

  private boolean slotDeploymentDidNotHappen(AzureWebAppRollbackParameters rollbackParameters) {
    AppServiceDeploymentProgress progressMarker = getProgressMarker(rollbackParameters);
    return (progressMarker == SAVE_CONFIGURATION) || (progressMarker == STOP_SLOT)
        || (progressMarker == UPDATE_SLOT_CONFIGURATIONS_SETTINGS)
        || (progressMarker == UPDATE_SLOT_CONTAINER_SETTINGS);
  }

  private void performRollback(ILogStreamingTaskClient logStreamingTaskClient,
      AzureWebAppRollbackParameters rollbackParameters, AzureWebClientContext azureWebClientContext,
      AzureAppServiceDeploymentContext deploymentContext) {
    AppServiceDeploymentProgress progressMarker = getProgressMarker(rollbackParameters);
    log.info(String.format("Starting rollback from previous marker - [%s]", progressMarker.getStepName()));

    switch (progressMarker) {
      case SAVE_CONFIGURATION:
        rollbackFromSaveConfigurationState(logStreamingTaskClient);
        break;

      case STOP_SLOT:
        rollbackFromStopSlotState(logStreamingTaskClient, rollbackParameters, deploymentContext);
        break;

      case UPDATE_SLOT_CONFIGURATIONS_SETTINGS:
        rollbackFromUpdateConfigurationState(logStreamingTaskClient, rollbackParameters, deploymentContext);
        break;

      case UPDATE_SLOT_CONTAINER_SETTINGS:
      case DEPLOY_TO_SLOT:
      case UPDATE_TRAFFIC_PERCENT:
      case SWAP_SLOT:
        rollbackDeploymentAndTrafficShift(
            logStreamingTaskClient, rollbackParameters, azureWebClientContext, deploymentContext);
        break;

      case DEPLOYMENT_COMPLETE:
        noRollback(logStreamingTaskClient);
        break;

      default:
        break;
    }
  }

  private AppServiceDeploymentProgress getProgressMarker(AzureWebAppRollbackParameters rollbackParameters) {
    AzureAppServicePreDeploymentData preDeploymentData = rollbackParameters.getPreDeploymentData();
    String deploymentProgressMarker = preDeploymentData.getDeploymentProgressMarker();
    return AppServiceDeploymentProgress.valueOf(deploymentProgressMarker);
  }

  private void rollbackFromSaveConfigurationState(ILogStreamingTaskClient logStreamingTaskClient) {
    String message = "The previous deployment did not start. Hence nothing to revert during rollback";
    markCommandUnitAsDone(logStreamingTaskClient, UPDATE_SLOT_CONFIGURATION_SETTINGS, message);
    markCommandUnitAsDone(logStreamingTaskClient, DEPLOY_TO_SLOT, message);
    markCommandUnitAsDone(logStreamingTaskClient, SLOT_TRAFFIC_PERCENTAGE, message);
  }

  private void rollbackFromStopSlotState(ILogStreamingTaskClient logStreamingTaskClient,
      AzureWebAppRollbackParameters rollbackParameters, AzureAppServiceDeploymentContext deploymentContext) {
    String message = "Slot configuration was not changed. Hence skipping this step";
    markCommandUnitAsDone(logStreamingTaskClient, UPDATE_SLOT_CONFIGURATION_SETTINGS, message);

    LogCallback deployLogCallback = logStreamingTaskClient.obtainLogCallback(DEPLOY_TO_SLOT);
    azureAppServiceDeploymentService.startSlotAsyncWithSteadyCheck(
        deploymentContext, rollbackParameters.getPreDeploymentData(), deployLogCallback);
    markCommandUnitAsDone(logStreamingTaskClient, DEPLOY_TO_SLOT, "Rollback completed");

    message = "Slot traffic was not changed. Hence skipping this step";
    markCommandUnitAsDone(logStreamingTaskClient, SLOT_TRAFFIC_PERCENTAGE, message);
  }

  private void rollbackFromUpdateConfigurationState(ILogStreamingTaskClient logStreamingTaskClient,
      AzureWebAppRollbackParameters rollbackParameters, AzureAppServiceDeploymentContext deploymentContext) {
    LogCallback logCallback = logStreamingTaskClient.obtainLogCallback(UPDATE_SLOT_CONFIGURATION_SETTINGS);
    azureAppServiceDeploymentService.updateDeploymentSlotConfigurationSettings(
        deploymentContext, rollbackParameters.getPreDeploymentData(), logCallback);
    markCommandUnitAsDone(
        logStreamingTaskClient, UPDATE_SLOT_CONFIGURATION_SETTINGS, "Reverted the slot configuration settings");

    String message = "No artifact/image was deployed during deployment. Hence skipping this step";
    markCommandUnitAsDone(logStreamingTaskClient, DEPLOY_TO_SLOT, message);

    message = "Slot traffic was not changed. Hence skipping this step";
    markCommandUnitAsDone(logStreamingTaskClient, SLOT_TRAFFIC_PERCENTAGE, message);
  }

  private void rollbackDeploymentAndTrafficShift(ILogStreamingTaskClient logStreamingTaskClient,
      AzureWebAppRollbackParameters rollbackParameters, AzureWebClientContext azureWebClientContext,
      AzureAppServiceDeploymentContext deploymentContext) {
    AzureAppServicePreDeploymentData preDeploymentData = rollbackParameters.getPreDeploymentData();
    rollbackSetupSlot(rollbackParameters, deploymentContext);
    if (!rollbackParameters.isBlueGreen()) {
      rollbackUpdateSlotTrafficWeight(preDeploymentData, azureWebClientContext, logStreamingTaskClient);
    }
  }

  private void noRollback(ILogStreamingTaskClient logStreamingTaskClient) {
    String message;
    message = "The previous deployment was complete. Hence nothing to revert during rollback";
    markCommandUnitAsDone(logStreamingTaskClient, STOP_DEPLOYMENT_SLOT, message);
    markCommandUnitAsDone(logStreamingTaskClient, UPDATE_SLOT_CONFIGURATION_SETTINGS, message);
    markCommandUnitAsDone(logStreamingTaskClient, UPDATE_DEPLOYMENT_SLOT_CONTAINER_SETTINGS, message);
    markCommandUnitAsDone(logStreamingTaskClient, START_DEPLOYMENT_SLOT, message);
    markCommandUnitAsDone(logStreamingTaskClient, SLOT_TRAFFIC_PERCENTAGE, message);
  }

  private void rollbackSetupSlot(
      AzureWebAppRollbackParameters rollbackParameters, AzureAppServiceDeploymentContext deploymentContext) {
    deploymentContext.deploy(azureAppServiceDeploymentService, rollbackParameters.getPreDeploymentData());
  }

  private AzureAppServiceDockerDeploymentContext toAzureAppServiceDockerDeploymentContext(
      AzureWebAppRollbackParameters rollbackParameters, AzureWebClientContext azureWebClientContext,
      ILogStreamingTaskClient logStreamingTaskClient) {
    AzureAppServicePreDeploymentData preDeploymentData = rollbackParameters.getPreDeploymentData();
    return AzureAppServiceDockerDeploymentContext.builder()
        .logCallbackProvider(logCallbackProviderFactory.createCg(logStreamingTaskClient))
        .appSettingsToAdd(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(
            preDeploymentData.getAppSettingsToAdd()))
        .appSettingsToRemove(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(
            preDeploymentData.getAppSettingsToRemove()))
        .connSettingsToAdd(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStrings(
            preDeploymentData.getConnStringsToAdd()))
        .connSettingsToRemove(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStrings(
            preDeploymentData.getConnStringsToRemove()))
        .dockerSettings(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(
            preDeploymentData.getDockerSettingsToAdd()))
        .imagePathAndTag(preDeploymentData.getImageNameAndTag())
        .slotName(preDeploymentData.getSlotName())
        .azureWebClientContext(azureWebClientContext)
        .startupCommand(preDeploymentData.getStartupCommand())
        .steadyStateTimeoutInMin(rollbackParameters.getTimeoutIntervalInMin())
        .build();
  }

  private AzureAppServicePackageDeploymentContext toAzureAppServicePackageDeploymentContext(
      AzureWebAppRollbackParameters rollbackParameters, AzureWebClientContext azureWebClientContext,
      ILogStreamingTaskClient logStreamingTaskClient, ArtifactStreamAttributes streamAttributes) {
    AutoCloseableWorkingDirectory autoCloseableWorkingDirectory =
        new AutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH);
    File artifactFile =
        getArtifactFile(rollbackParameters, streamAttributes, autoCloseableWorkingDirectory, logStreamingTaskClient);

    AzureAppServicePreDeploymentData preDeploymentData = rollbackParameters.getPreDeploymentData();
    return AzureAppServicePackageDeploymentContext.builder()
        .logCallbackProvider(logCallbackProviderFactory.createCg(logStreamingTaskClient))
        .appSettingsToAdd(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(
            preDeploymentData.getAppSettingsToAdd()))
        .appSettingsToRemove(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettings(
            preDeploymentData.getAppSettingsToRemove()))
        .connSettingsToAdd(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStrings(
            preDeploymentData.getConnStringsToAdd()))
        .connSettingsToRemove(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStrings(
            preDeploymentData.getConnStringsToRemove()))
        .slotName(preDeploymentData.getSlotName())
        .artifactType(streamAttributes.getArtifactType())
        .artifactFile(artifactFile)
        .azureWebClientContext(azureWebClientContext)
        .startupCommand(preDeploymentData.getStartupCommand())
        .steadyStateTimeoutInMin(rollbackParameters.getTimeoutIntervalInMin())
        .build();
  }

  private void rollbackUpdateSlotTrafficWeight(AzureAppServicePreDeploymentData preDeploymentData,
      AzureWebClientContext azureWebClientContext, ILogStreamingTaskClient logStreamingTaskClient) {
    double trafficWeight = preDeploymentData.getTrafficWeight();
    String slotName = preDeploymentData.getSlotName();
    azureAppServiceDeploymentService.rerouteProductionSlotTraffic(
        azureWebClientContext, slotName, trafficWeight, logCallbackProviderFactory.createCg(logStreamingTaskClient));
  }

  private void markCommandUnitAsDone(
      ILogStreamingTaskClient logStreamingTaskClient, String commandUnit, String message) {
    LogCallback logCallback = logStreamingTaskClient.obtainLogCallback(commandUnit);
    logCallback.saveExecutionLog(
        String.format("Message - [%s]", message), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }
}
