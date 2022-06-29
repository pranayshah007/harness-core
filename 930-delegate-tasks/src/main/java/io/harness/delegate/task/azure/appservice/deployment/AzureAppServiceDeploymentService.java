/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.ADD_APPLICATION_SETTINGS;
import static io.harness.azure.model.AzureConstants.ADD_CONNECTION_STRINGS;
import static io.harness.azure.model.AzureConstants.ARTIFACT_DEPLOY_STARTED;
import static io.harness.azure.model.AzureConstants.DELETE_APPLICATION_SETTINGS;
import static io.harness.azure.model.AzureConstants.DELETE_CONNECTION_STRINGS;
import static io.harness.azure.model.AzureConstants.DELETE_CONTAINER_SETTINGS;
import static io.harness.azure.model.AzureConstants.DELETE_IMAGE_SETTINGS;
import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
import static io.harness.azure.model.AzureConstants.EMPTY_DOCKER_SETTINGS;
import static io.harness.azure.model.AzureConstants.FAIL_DEPLOYMENT;
import static io.harness.azure.model.AzureConstants.FAIL_LOG_STREAMING;
import static io.harness.azure.model.AzureConstants.FAIL_START_SLOT;
import static io.harness.azure.model.AzureConstants.FAIL_STOP_SLOT;
import static io.harness.azure.model.AzureConstants.FAIL_UPDATE_CONTAINER_SETTINGS;
import static io.harness.azure.model.AzureConstants.FAIL_UPDATE_SLOT_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.REQUEST_START_SLOT;
import static io.harness.azure.model.AzureConstants.REQUEST_STOP_SLOT;
import static io.harness.azure.model.AzureConstants.REQUEST_TRAFFIC_SHIFT;
import static io.harness.azure.model.AzureConstants.SLOT_STARTING_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.SLOT_STOPPING_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.START_ARTIFACT_DEPLOY;
import static io.harness.azure.model.AzureConstants.START_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.START_SLOT_DEPLOYMENT;
import static io.harness.azure.model.AzureConstants.STOP_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.SUCCESS_ADD_APPLICATION_SETTINGS;
import static io.harness.azure.model.AzureConstants.SUCCESS_ADD_CONNECTION_STRINGS;
import static io.harness.azure.model.AzureConstants.SUCCESS_DELETE_APPLICATIONS_SETTINGS;
import static io.harness.azure.model.AzureConstants.SUCCESS_DELETE_CONNECTION_STRINGS;
import static io.harness.azure.model.AzureConstants.SUCCESS_DELETE_CONTAINER_SETTINGS;
import static io.harness.azure.model.AzureConstants.SUCCESS_DELETE_IMAGE_SETTINGS;
import static io.harness.azure.model.AzureConstants.SUCCESS_SLOT_DEPLOYMENT;
import static io.harness.azure.model.AzureConstants.SUCCESS_START_SLOT;
import static io.harness.azure.model.AzureConstants.SUCCESS_STOP_SLOT;
import static io.harness.azure.model.AzureConstants.SUCCESS_TRAFFIC_SHIFT;
import static io.harness.azure.model.AzureConstants.SUCCESS_UPDATE_CONTAINER_SETTINGS;
import static io.harness.azure.model.AzureConstants.SUCCESS_UPDATE_IMAGE_SETTINGS;
import static io.harness.azure.model.AzureConstants.SUCCESS_UPDATE_STARTUP_COMMAND;
import static io.harness.azure.model.AzureConstants.SWAP_SLOT_SUCCESS;
import static io.harness.azure.model.AzureConstants.UNSUPPORTED_ARTIFACT;
import static io.harness.azure.model.AzureConstants.UPDATE_APPLICATION_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.UPDATE_CONTAINER_SETTINGS;
import static io.harness.azure.model.AzureConstants.UPDATE_IMAGE_SETTINGS;
import static io.harness.azure.model.AzureConstants.UPDATE_SLOT_CONFIGURATIONS_SUCCESS;
import static io.harness.azure.model.AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.azure.model.AzureConstants.UPDATE_STARTUP_COMMAND;
import static io.harness.azure.model.AzureConstants.UPDATING_SLOT_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.WAR_DEPLOY;
import static io.harness.azure.model.AzureConstants.ZIP_DEPLOY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.SLOT_CONTAINER_DEPLOYMENT_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.SLOT_DEPLOYMENT_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.START_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.STOP_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.SWAP_VERIFIER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureServiceCallBack;
import io.harness.azure.client.AzureMonitorClient;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.deployment.SlotContainerLogStreamer;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.WebAppHostingOS;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.SlotContainerDeploymentVerifierContext;
import io.harness.delegate.task.azure.appservice.deployment.context.SlotDeploymentVerifierContext;
import io.harness.delegate.task.azure.appservice.deployment.context.SlotStatusVerifierContext;
import io.harness.delegate.task.azure.appservice.deployment.context.SwapSlotStatusVerifierContext;
import io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.validator.Validators;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;

import software.wings.delegatetasks.azure.AzureTimeLimiter;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import rx.Completable;

@Singleton
@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class AzureAppServiceDeploymentService {
  @Inject private AzureWebClient azureWebClient;
  @Inject private AzureTimeLimiter azureTimeLimiter;
  @Inject private SlotSteadyStateChecker slotSteadyStateChecker;
  @Inject private AzureMonitorClient azureMonitorClient;

  public void deployDockerImage(
      AzureAppServiceDockerDeploymentContext deploymentContext, AzureAppServicePreDeploymentData preDeploymentData) {
    Validators.validateJsr380FailFast(deploymentContext, InvalidRequestException::new);
    Validators.validateJsr380FailFast(deploymentContext.getAzureWebClientContext(), InvalidRequestException::new);
    log.info("Start deploying docker image: {} on slot: {}", deploymentContext.getImagePathAndTag(),
        deploymentContext.getSlotName());

    updateDockerDeploymentSlotConfigurations(deploymentContext, preDeploymentData);
    deployDockerToSlot(deploymentContext, preDeploymentData);
  }

  private void updateDockerDeploymentSlotConfigurations(
      AzureAppServiceDockerDeploymentContext deploymentContext, AzureAppServicePreDeploymentData preDeploymentData) {
    AzureLogCallbackProvider logCallbackProvider = deploymentContext.getLogCallbackProvider();
    LogCallback updateSlotLog = logCallbackProvider.obtainLogCallback(UPDATE_SLOT_CONFIGURATION_SETTINGS);
    updateSlotLog.saveExecutionLog(format(UPDATING_SLOT_CONFIGURATIONS, deploymentContext.getSlotName()));

    stopSlotAsyncWithSteadyCheck(deploymentContext, preDeploymentData, updateSlotLog);
    updateDeploymentSlotConfigurationSettings(deploymentContext, preDeploymentData, updateSlotLog);
    updateDeploymentSlotContainerSettings(deploymentContext, preDeploymentData, updateSlotLog);

    updateSlotLog.saveExecutionLog(
        format(UPDATE_SLOT_CONFIGURATIONS_SUCCESS, deploymentContext.getSlotName()), INFO, SUCCESS);
  }

  private void deployDockerToSlot(
      AzureAppServiceDockerDeploymentContext deploymentContext, AzureAppServicePreDeploymentData preDeploymentData) {
    AzureLogCallbackProvider logCallbackProvider = deploymentContext.getLogCallbackProvider();
    LogCallback deployLogCallback = logCallbackProvider.obtainLogCallback(DEPLOY_TO_SLOT);
    try {
      markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.DEPLOY_TO_SLOT);
      deployLogCallback.saveExecutionLog(String.format(START_SLOT_DEPLOYMENT, deploymentContext.getSlotName()));

      SlotContainerLogStreamer slotLogStreamer =
          new SlotContainerLogStreamer(deploymentContext.getAzureWebClientContext(), azureWebClient,
              deploymentContext.getSlotName(), deployLogCallback);
      uploadStartupScript(deploymentContext.getAzureWebClientContext(), deploymentContext.getSlotName(),
          deploymentContext.getStartupCommand(), deployLogCallback);
      startSlotAsyncWithSteadyCheck(deploymentContext, preDeploymentData, deployLogCallback);

      containerDeploymentSteadyStateCheck(deploymentContext, deployLogCallback, slotLogStreamer);

      deployLogCallback.saveExecutionLog(
          String.format(SUCCESS_SLOT_DEPLOYMENT, deploymentContext.getSlotName()), INFO, SUCCESS);
    } catch (Exception ex) {
      deployLogCallback.saveExecutionLog(String.format(FAIL_DEPLOYMENT, ex.getMessage()), ERROR, FAILURE);
      throw ex;
    }
  }

  private void containerDeploymentSteadyStateCheck(AzureAppServiceDockerDeploymentContext deploymentContext,
      LogCallback deployLogCallback, SlotContainerLogStreamer slotLogStreamer) {
    SlotContainerDeploymentVerifierContext verifierContext =
        SlotContainerDeploymentVerifierContext.builder()
            .logCallback(deployLogCallback)
            .slotName(deploymentContext.getSlotName())
            .azureWebClient(azureWebClient)
            .azureWebClientContext(deploymentContext.getAzureWebClientContext())
            .logStreamer(slotLogStreamer)
            .build();

    SlotStatusVerifier statusVerifier =
        SlotStatusVerifier.getStatusVerifier(SLOT_CONTAINER_DEPLOYMENT_VERIFIER.name(), verifierContext);
    slotSteadyStateChecker.waitUntilCompleteWithTimeout(deploymentContext.getSteadyStateTimeoutInMin(),
        SLOT_STARTING_STATUS_CHECK_INTERVAL, deployLogCallback, DEPLOY_TO_SLOT, statusVerifier);
  }

  private void deploySlotSteadyStateCheck(AzureAppServiceDeploymentContext deploymentContext,
      StreamPackageDeploymentLogsTask streamPackageDeploymentLogsTask, LogCallback deployLogCallback) {
    try {
      SlotDeploymentVerifierContext verifierContext =
          SlotDeploymentVerifierContext.builder()
              .logCallback(deployLogCallback)
              .slotName(deploymentContext.getSlotName())
              .azureWebClient(azureWebClient)
              .azureWebClientContext(deploymentContext.getAzureWebClientContext())
              .logStreamer(streamPackageDeploymentLogsTask)
              .build();

      SlotStatusVerifier statusVerifier =
          SlotStatusVerifier.getStatusVerifier(SLOT_DEPLOYMENT_VERIFIER.name(), verifierContext);

      slotSteadyStateChecker.waitUntilDeploymentCompleteWithTimeout(deploymentContext.getSteadyStateTimeoutInMin(),
          SLOT_STARTING_STATUS_CHECK_INTERVAL, deployLogCallback, DEPLOY_TO_SLOT, statusVerifier);
    } catch (Exception exception) {
      deployLogCallback.saveExecutionLog(String.format(FAIL_DEPLOYMENT, exception.getMessage()), ERROR, FAILURE);
      throw exception;
    }
  }

  public void deployPackage(
      AzureAppServicePackageDeploymentContext deploymentContext, AzureAppServicePreDeploymentData preDeploymentData) {
    Validators.validateJsr380FailFast(deploymentContext, InvalidRequestException::new);
    Validators.validateJsr380FailFast(deploymentContext.getAzureWebClientContext(), InvalidRequestException::new);
    log.info("Start deploying artifact file on slot, slotName: {}, artifactFileAbsolutePath: {}",
        deploymentContext.getSlotName(), deploymentContext.getArtifactFile().getAbsolutePath());

    updatePackageDeploymentSlotConfigurations(deploymentContext, preDeploymentData);
    deployPackageToSlot(deploymentContext, preDeploymentData);
  }

  private void deployPackageToSlot(
      AzureAppServicePackageDeploymentContext deploymentContext, AzureAppServicePreDeploymentData preDeploymentData) {
    Optional<StreamPackageDeploymentLogsTask> logStreamer = Optional.empty();

    try {
      DateTime startSlotTime = new DateTime(DateTimeZone.UTC).minusMinutes(1);
      markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.DEPLOY_TO_SLOT);
      AzureLogCallbackProvider logCallbackProvider = deploymentContext.getLogCallbackProvider();
      LogCallback deployLog = logCallbackProvider.obtainLogCallback(DEPLOY_TO_SLOT);
      deployLog.saveExecutionLog(String.format(START_SLOT_DEPLOYMENT, deploymentContext.getSlotName()));

      logStreamer = streamPackageDeploymentLogs(
          deployLog, deploymentContext.getAzureWebClientContext(), deploymentContext.getSlotName(), startSlotTime);
      deployArtifactFile(deploymentContext, preDeploymentData, deployLog);
      startSlotAsyncWithSteadyCheck(deploymentContext, preDeploymentData, deployLog);
      logStreamer.ifPresent(streamPackageDeploymentLogsTask
          -> deploySlotSteadyStateCheck(deploymentContext, streamPackageDeploymentLogsTask, deployLog));

      deployLog.saveExecutionLog(
          String.format(SUCCESS_SLOT_DEPLOYMENT, deploymentContext.getSlotName()), INFO, SUCCESS);
    } catch (Exception ex) {
      logStreamer.ifPresent(StreamPackageDeploymentLogsTask::unsubscribe);
      throw ex;
    }
  }

  private void updatePackageDeploymentSlotConfigurations(
      AzureAppServicePackageDeploymentContext deploymentContext, AzureAppServicePreDeploymentData preDeploymentData) {
    AzureLogCallbackProvider logCallbackProvider = deploymentContext.getLogCallbackProvider();
    LogCallback updateSlotLog = logCallbackProvider.obtainLogCallback(UPDATE_SLOT_CONFIGURATION_SETTINGS);
    updateSlotLog.saveExecutionLog(format(UPDATING_SLOT_CONFIGURATIONS, deploymentContext.getSlotName()));

    stopSlotAsyncWithSteadyCheck(deploymentContext, preDeploymentData, updateSlotLog);
    updateDeploymentSlotConfigurationSettings(deploymentContext, preDeploymentData, updateSlotLog);

    updateSlotLog.saveExecutionLog(
        format(UPDATE_SLOT_CONFIGURATIONS_SUCCESS, deploymentContext.getSlotName()), INFO, SUCCESS);
  }

  public void updateDeploymentSlotConfigurationSettings(AzureAppServiceDeploymentContext deploymentContext,
      AzureAppServicePreDeploymentData preDeploymentData, LogCallback updateSlotLog) {
    AzureWebClientContext azureWebClientContext = deploymentContext.getAzureWebClientContext();
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd = deploymentContext.getAppSettingsToAdd();
    Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove = deploymentContext.getAppSettingsToRemove();
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd = deploymentContext.getConnSettingsToAdd();
    Map<String, AzureAppServiceConnectionString> connSettingsToRemove = deploymentContext.getConnSettingsToRemove();
    String slotName = deploymentContext.getSlotName();

    try {
      markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.UPDATE_SLOT_CONFIGURATIONS_SETTINGS);
      updateSlotLog.saveExecutionLog(format(UPDATE_APPLICATION_CONFIGURATIONS, slotName));

      deleteDeploymentSlotAppSettings(azureWebClientContext, slotName, appSettingsToRemove, updateSlotLog);
      updateDeploymentSlotAppSettings(azureWebClientContext, slotName, appSettingsToAdd, updateSlotLog);
      deleteDeploymentSlotConnectionSettings(azureWebClientContext, slotName, connSettingsToRemove, updateSlotLog);
      updateDeploymentSlotConnectionSettings(azureWebClientContext, slotName, connSettingsToAdd, updateSlotLog);
    } catch (Exception ex) {
      String message = String.format(FAIL_UPDATE_SLOT_CONFIGURATIONS, ex.getMessage());
      updateSlotLog.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
  }

  private void deleteDeploymentSlotAppSettings(AzureWebClientContext azureWebClientContext, String slotName,
      Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove, LogCallback configurationLogCallback) {
    if (isEmpty(appSettingsToRemove)) {
      return;
    }
    String appSettingKeysStr = Arrays.toString(appSettingsToRemove.keySet().toArray(new String[0]));
    configurationLogCallback.saveExecutionLog(format(DELETE_APPLICATION_SETTINGS, appSettingKeysStr));
    azureWebClient.deleteDeploymentSlotAppSettings(azureWebClientContext, slotName, appSettingsToRemove);
    configurationLogCallback.saveExecutionLog(SUCCESS_DELETE_APPLICATIONS_SETTINGS);
  }

  private void updateDeploymentSlotAppSettings(AzureWebClientContext azureWebClientContext, String slotName,
      Map<String, AzureAppServiceApplicationSetting> appSettings, LogCallback configurationLogCallback) {
    if (isEmpty(appSettings)) {
      return;
    }

    String appSettingKeysStr = Arrays.toString(appSettings.keySet().toArray(new String[0]));
    configurationLogCallback.saveExecutionLog(format(ADD_APPLICATION_SETTINGS, appSettingKeysStr));
    azureWebClient.updateDeploymentSlotAppSettings(azureWebClientContext, slotName, appSettings);
    configurationLogCallback.saveExecutionLog(SUCCESS_ADD_APPLICATION_SETTINGS);
  }

  private void deleteDeploymentSlotConnectionSettings(AzureWebClientContext azureWebClientContext, String slotName,
      Map<String, AzureAppServiceConnectionString> connSettingsToRemove, LogCallback configurationLogCallback) {
    if (isEmpty(connSettingsToRemove)) {
      return;
    }

    String connSettingKeysStr = Arrays.toString(connSettingsToRemove.keySet().toArray(new String[0]));
    configurationLogCallback.saveExecutionLog(format(DELETE_CONNECTION_STRINGS, connSettingKeysStr));
    azureWebClient.deleteDeploymentSlotConnectionStrings(azureWebClientContext, slotName, connSettingsToRemove);
    configurationLogCallback.saveExecutionLog(SUCCESS_DELETE_CONNECTION_STRINGS);
  }

  private void updateDeploymentSlotConnectionSettings(AzureWebClientContext azureWebClientContext, String slotName,
      Map<String, AzureAppServiceConnectionString> connSettings, LogCallback configurationLogCallback) {
    if (isEmpty(connSettings)) {
      return;
    }
    String connSettingKeysStr = Arrays.toString(connSettings.keySet().toArray(new String[0]));
    configurationLogCallback.saveExecutionLog(format(ADD_CONNECTION_STRINGS, connSettingKeysStr));
    azureWebClient.updateDeploymentSlotConnectionStrings(azureWebClientContext, slotName, connSettings);
    configurationLogCallback.saveExecutionLog(SUCCESS_ADD_CONNECTION_STRINGS);
  }

  private void updateDeploymentSlotContainerSettings(AzureAppServiceDockerDeploymentContext deploymentContext,
      AzureAppServicePreDeploymentData preDeploymentData, LogCallback updateSlotLog) {
    AzureWebClientContext azureWebClientContext = deploymentContext.getAzureWebClientContext();
    Map<String, AzureAppServiceApplicationSetting> dockerSettings = deploymentContext.getDockerSettings();
    String imageAndTag = deploymentContext.getImagePathAndTag();
    String slotName = deploymentContext.getSlotName();

    try {
      markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.UPDATE_SLOT_CONTAINER_SETTINGS);
      updateSlotLog.saveExecutionLog(format(UPDATE_CONTAINER_SETTINGS, slotName));

      deleteDeploymentSlotContainerSettings(azureWebClientContext, slotName, updateSlotLog);
      deleteDeploymentSlotDockerImageNameAndTagSettings(azureWebClientContext, slotName, updateSlotLog);
      updateDeploymentSlotContainerSettings(azureWebClientContext, slotName, dockerSettings, updateSlotLog);
      updateDeploymentSlotDockerImageNameAndTagSettings(azureWebClientContext, slotName, imageAndTag, updateSlotLog);

      updateSlotLog.saveExecutionLog(SUCCESS_UPDATE_CONTAINER_SETTINGS, INFO);
    } catch (Exception ex) {
      updateSlotLog.saveExecutionLog(String.format(FAIL_UPDATE_CONTAINER_SETTINGS, ex.getMessage()), ERROR, FAILURE);
      throw ex;
    }
  }

  private void deleteDeploymentSlotContainerSettings(
      AzureWebClientContext azureWebClientContext, String slotName, LogCallback containerLogCallback) {
    containerLogCallback.saveExecutionLog(DELETE_CONTAINER_SETTINGS);
    azureWebClient.deleteDeploymentSlotDockerSettings(azureWebClientContext, slotName);
    containerLogCallback.saveExecutionLog(SUCCESS_DELETE_CONTAINER_SETTINGS);
  }

  private void deleteDeploymentSlotDockerImageNameAndTagSettings(
      AzureWebClientContext azureWebClientContext, String slotName, LogCallback containerLogCallback) {
    containerLogCallback.saveExecutionLog(DELETE_IMAGE_SETTINGS);
    azureWebClient.deleteDeploymentSlotDockerImageNameAndTagSettings(azureWebClientContext, slotName);
    containerLogCallback.saveExecutionLog(SUCCESS_DELETE_IMAGE_SETTINGS);
  }

  private void updateDeploymentSlotContainerSettings(AzureWebClientContext azureWebClientContext, String slotName,
      Map<String, AzureAppServiceApplicationSetting> dockerSettings, LogCallback containerLogCallback) {
    Set<String> containerSettingKeys = dockerSettings.keySet();
    if (containerSettingKeys.isEmpty()) {
      containerLogCallback.saveExecutionLog(format(EMPTY_DOCKER_SETTINGS, slotName));
      return;
    }

    String containerSettingKeysStr = Arrays.toString(containerSettingKeys.toArray(new String[0]));
    containerLogCallback.saveExecutionLog(format("Updating Container settings: %n[%s]", containerSettingKeysStr));
    azureWebClient.updateDeploymentSlotDockerSettings(azureWebClientContext, slotName, dockerSettings);
    containerLogCallback.saveExecutionLog("Container settings updated successfully");
  }

  private void updateDeploymentSlotDockerImageNameAndTagSettings(AzureWebClientContext azureWebClientContext,
      String slotName, String newImageAndTag, LogCallback containerLogCallback) {
    WebAppHostingOS webAppHostingOS = azureWebClient.getWebAppHostingOS(azureWebClientContext);
    containerLogCallback.saveExecutionLog(format(UPDATE_IMAGE_SETTINGS, newImageAndTag, webAppHostingOS));
    azureWebClient.updateDeploymentSlotDockerImageNameAndTagSettings(
        azureWebClientContext, slotName, newImageAndTag, webAppHostingOS);
    containerLogCallback.saveExecutionLog(format(SUCCESS_UPDATE_IMAGE_SETTINGS, slotName));
  }

  public void startSlotAsyncWithSteadyCheck(AzureAppServiceDeploymentContext deploymentContext,
      AzureAppServicePreDeploymentData preDeploymentData, LogCallback deployLogCallback) {
    markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.DEPLOY_TO_SLOT);
    String slotName = deploymentContext.getSlotName();
    deployLogCallback.saveExecutionLog(format(REQUEST_START_SLOT, slotName));
    try {
      AzureServiceCallBack restCallBack = new AzureServiceCallBack(deployLogCallback, START_DEPLOYMENT_SLOT);
      azureWebClient.startDeploymentSlotAsync(deploymentContext.getAzureWebClientContext(), slotName, restCallBack);

      SlotStatusVerifierContext statusVerifierContext =
          SlotStatusVerifierContext.builder()
              .logCallback(deployLogCallback)
              .slotName(slotName)
              .azureWebClient(azureWebClient)
              .azureWebClientContext(deploymentContext.getAzureWebClientContext())
              .restCallBack(restCallBack)
              .build();

      SlotStatusVerifier statusVerifier =
          SlotStatusVerifier.getStatusVerifier(START_VERIFIER.name(), statusVerifierContext);
      slotSteadyStateChecker.waitUntilCompleteWithTimeout(deploymentContext.getSteadyStateTimeoutInMin(),
          SLOT_STARTING_STATUS_CHECK_INTERVAL, deployLogCallback, START_DEPLOYMENT_SLOT, statusVerifier);
      deployLogCallback.saveExecutionLog(SUCCESS_START_SLOT, INFO);
    } catch (Exception exception) {
      deployLogCallback.saveExecutionLog(String.format(FAIL_START_SLOT, exception.getMessage()), ERROR, FAILURE);
      throw exception;
    }
  }

  public void stopSlotAsyncWithSteadyCheck(AzureAppServiceDeploymentContext deploymentContext,
      AzureAppServicePreDeploymentData preDeploymentData, LogCallback updateSlotLog) {
    markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.STOP_SLOT);
    String slotName = deploymentContext.getSlotName();
    updateSlotLog.saveExecutionLog(format(REQUEST_STOP_SLOT, slotName));

    try {
      AzureServiceCallBack restCallBack = new AzureServiceCallBack(updateSlotLog, STOP_DEPLOYMENT_SLOT);
      azureWebClient.stopDeploymentSlotAsync(deploymentContext.getAzureWebClientContext(), slotName, restCallBack);

      SlotStatusVerifierContext statusVerifierContext =
          SlotStatusVerifierContext.builder()
              .logCallback(updateSlotLog)
              .slotName(slotName)
              .azureWebClient(azureWebClient)
              .azureWebClientContext(deploymentContext.getAzureWebClientContext())
              .restCallBack(restCallBack)
              .build();

      SlotStatusVerifier statusVerifier =
          SlotStatusVerifier.getStatusVerifier(STOP_VERIFIER.name(), statusVerifierContext);

      slotSteadyStateChecker.waitUntilCompleteWithTimeout(deploymentContext.getSteadyStateTimeoutInMin(),
          SLOT_STOPPING_STATUS_CHECK_INTERVAL, updateSlotLog, STOP_DEPLOYMENT_SLOT, statusVerifier);
      updateSlotLog.saveExecutionLog(SUCCESS_STOP_SLOT, INFO);
    } catch (Exception exception) {
      updateSlotLog.saveExecutionLog(String.format(FAIL_STOP_SLOT, exception.getMessage()), ERROR, FAILURE);
      throw exception;
    }
  }

  public void rerouteProductionSlotTraffic(AzureWebClientContext webClientContext, String shiftTrafficSlotName,
      double trafficWeightInPercentage, AzureLogCallbackProvider logCallbackProvider) {
    LogCallback rerouteTrafficLogCallback = logCallbackProvider.obtainLogCallback(SLOT_TRAFFIC_PERCENTAGE);

    rerouteTrafficLogCallback.saveExecutionLog(
        format(REQUEST_TRAFFIC_SHIFT, trafficWeightInPercentage, shiftTrafficSlotName));
    azureWebClient.rerouteProductionSlotTraffic(webClientContext, shiftTrafficSlotName, trafficWeightInPercentage);
    rerouteTrafficLogCallback.saveExecutionLog(SUCCESS_TRAFFIC_SHIFT, INFO, SUCCESS);
  }

  public void swapSlotsUsingCallback(AzureAppServiceDeploymentContext azureAppServiceDeploymentContext,
      String targetSlotName, AzureLogCallbackProvider logCallbackProvider) {
    String sourceSlotName = azureAppServiceDeploymentContext.getSlotName();
    int steadyStateTimeoutInMinutes = azureAppServiceDeploymentContext.getSteadyStateTimeoutInMin();
    AzureWebClientContext webClientContext = azureAppServiceDeploymentContext.getAzureWebClientContext();
    LogCallback slotSwapLogCallback = logCallbackProvider.obtainLogCallback(SLOT_SWAP);
    AzureServiceCallBack restCallBack = new AzureServiceCallBack(slotSwapLogCallback, SLOT_SWAP);

    SwapSlotStatusVerifierContext context =
        SwapSlotStatusVerifierContext.builder()
            .logCallback(slotSwapLogCallback)
            .slotName(sourceSlotName)
            .azureWebClient(azureWebClient)
            .azureMonitorClient(azureMonitorClient)
            .azureWebClientContext(azureAppServiceDeploymentContext.getAzureWebClientContext())
            .restCallBack(restCallBack)
            .build();

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(SWAP_VERIFIER.name(), context);

    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.submit(new SwapSlotTask(
        sourceSlotName, targetSlotName, azureWebClient, webClientContext, restCallBack, slotSwapLogCallback));
    executorService.shutdown();

    slotSteadyStateChecker.waitUntilCompleteWithTimeout(steadyStateTimeoutInMinutes,
        SLOT_STOPPING_STATUS_CHECK_INTERVAL, slotSwapLogCallback, SLOT_SWAP, statusVerifier);
    slotSwapLogCallback.saveExecutionLog(SWAP_SLOT_SUCCESS, INFO, SUCCESS);
  }

  private Optional<StreamPackageDeploymentLogsTask> streamPackageDeploymentLogs(
      LogCallback logCallback, AzureWebClientContext azureWebClientContext, String slotName, DateTime startSlotTime) {
    StreamPackageDeploymentLogsTask logStreamer = null;
    try {
      ExecutorService executorService = Executors.newFixedThreadPool(1);
      logStreamer = new StreamPackageDeploymentLogsTask(
          azureWebClientContext, azureWebClient, slotName, logCallback, startSlotTime);
      executorService.submit(logStreamer);
      executorService.shutdown();
    } catch (Exception ex) {
      String errorMessage = ex.getCause() != null ? ex.getCause().getMessage() : ex.toString();
      logCallback.saveExecutionLog(
          color(String.format(FAIL_LOG_STREAMING, slotName, isEmpty(errorMessage) ? "" : errorMessage), White, Bold),
          INFO, SUCCESS);
    }
    return Optional.ofNullable(logStreamer);
  }

  private void deployArtifactFile(AzureAppServicePackageDeploymentContext context,
      AzureAppServicePreDeploymentData preDeploymentData, LogCallback deployLog) {
    String slotName = context.getSlotName();
    markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.DEPLOY_TO_SLOT);
    try {
      deployLog.saveExecutionLog(START_ARTIFACT_DEPLOY);
      uploadStartupScript(context.getAzureWebClientContext(), slotName, context.getStartupCommand(), deployLog);
      Completable deployment = deployPackage(context.getAzureWebClientContext(), slotName, context.getArtifactFile(),
          context.getArtifactType(), deployLog);
      deployment.await(context.getSteadyStateTimeoutInMin(), TimeUnit.MINUTES);
    } catch (Exception exception) {
      deployLog.saveExecutionLog(String.format(FAIL_DEPLOYMENT, exception.getMessage()), ERROR, FAILURE);
      throw exception;
    }
  }

  private void uploadStartupScript(
      AzureWebClientContext context, final String slotName, final String startupCommand, LogCallback logCallback) {
    logCallback.saveExecutionLog(format(UPDATE_STARTUP_COMMAND, context.getAppName(), slotName));
    azureWebClient.updateSlotConfigurationWithAppCommandLineScript(context, slotName, startupCommand);
    logCallback.saveExecutionLog(SUCCESS_UPDATE_STARTUP_COMMAND);
  }

  private Completable deployPackage(AzureWebClientContext azureWebClientContext, final String slotName,
      final File artifactFile, ArtifactType artifactType, LogCallback logCallback) {
    if (ArtifactType.ZIP == artifactType || ArtifactType.NUGET == artifactType) {
      return deployZipToSlotAndLog(azureWebClientContext, slotName, artifactFile, logCallback);
    } else if (ArtifactType.WAR == artifactType) {
      return deployWarToSlotAndLog(azureWebClientContext, slotName, artifactFile, logCallback);
    } else {
      throw new InvalidRequestException(format(UNSUPPORTED_ARTIFACT, artifactType, slotName));
    }
  }

  private Completable deployZipToSlotAndLog(
      AzureWebClientContext azureWebClientContext, String slotName, File artifactFile, LogCallback logCallback) {
    logCallback.saveExecutionLog(
        format(ZIP_DEPLOY, artifactFile.getAbsolutePath(), azureWebClientContext.getAppName(), slotName));
    Completable deployment = azureWebClient.deployZipToSlotAsync(azureWebClientContext, slotName, artifactFile);
    logCallback.saveExecutionLog(ARTIFACT_DEPLOY_STARTED);

    return deployment;
  }

  private Completable deployWarToSlotAndLog(
      AzureWebClientContext azureWebClientContext, String slotName, File artifactFile, LogCallback logCallback) {
    logCallback.saveExecutionLog(
        format(WAR_DEPLOY, artifactFile.getAbsolutePath(), azureWebClientContext.getAppName(), slotName));
    Completable deployment = azureWebClient.deployWarToSlotAsync(azureWebClientContext, slotName, artifactFile);
    logCallback.saveExecutionLog(ARTIFACT_DEPLOY_STARTED);

    return deployment;
  }

  private void markDeploymentProgress(
      AzureAppServicePreDeploymentData preDeploymentData, AppServiceDeploymentProgress progress) {
    preDeploymentData.setDeploymentProgressMarker(progress.name());
  }
}
