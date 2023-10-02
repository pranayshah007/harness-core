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
import static io.harness.azure.model.AzureConstants.DEFAULT_JAR_ARTIFACT_NAME;
import static io.harness.azure.model.AzureConstants.DELETE_APPLICATION_SETTINGS;
import static io.harness.azure.model.AzureConstants.DELETE_CONNECTION_STRINGS;
import static io.harness.azure.model.AzureConstants.DELETE_CONTAINER_SETTINGS;
import static io.harness.azure.model.AzureConstants.DELETE_IMAGE_SETTINGS;
import static io.harness.azure.model.AzureConstants.DEPLOY_DETAILS_LOG;
import static io.harness.azure.model.AzureConstants.DEPLOY_LOG;
import static io.harness.azure.model.AzureConstants.DEPLOY_OPTIONS_LOG;
import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
import static io.harness.azure.model.AzureConstants.EMPTY_DOCKER_SETTINGS;
import static io.harness.azure.model.AzureConstants.FAIL_DEPLOYMENT;
import static io.harness.azure.model.AzureConstants.FAIL_START_SLOT;
import static io.harness.azure.model.AzureConstants.FAIL_STOP_SLOT;
import static io.harness.azure.model.AzureConstants.FAIL_UPDATE_CONTAINER_SETTINGS;
import static io.harness.azure.model.AzureConstants.FAIL_UPDATE_SLOT_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.FILE_RENAME_FAILURE;
import static io.harness.azure.model.AzureConstants.JAR_EXTENSION;
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
import static io.harness.azure.model.AzureConstants.SWAP_SLOT_FAILURE;
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
import static io.harness.azure.model.AzureConstants.ZIP_EXTENSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.SLOT_CONTAINER_DEPLOYMENT_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.SLOT_DEPLOYMENT_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.START_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.STOP_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.SWAP_VERIFIER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
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
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.azure.AzureAppServicesDeployArtifactFileException;
import io.harness.logging.LogCallback;

import software.wings.utils.ArtifactType;

import com.azure.core.http.rest.Response;
import com.azure.resourcemanager.appservice.models.DeployOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import reactor.core.publisher.Mono;

@Singleton
@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AZURE_WEBAPP})
public class AzureAppServiceDeploymentService {
  @Inject private AzureWebClient azureWebClient;
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

      containerDeploymentSteadyStateCheck(deploymentContext.getSlotName(),
          deploymentContext.getSteadyStateTimeoutInMin(), deploymentContext.getAzureWebClientContext(),
          deployLogCallback, slotLogStreamer);

      deployLogCallback.saveExecutionLog(
          String.format(SUCCESS_SLOT_DEPLOYMENT, deploymentContext.getSlotName()), INFO, SUCCESS);
    } catch (Exception ex) {
      deployLogCallback.saveExecutionLog(String.format(FAIL_DEPLOYMENT, ex.getMessage()), ERROR, FAILURE);
      throw ex;
    }
  }

  private void containerDeploymentSteadyStateCheck(String slot, int timeOut, AzureWebClientContext deploymentContext,
      LogCallback deployLogCallback, SlotContainerLogStreamer slotLogStreamer) {
    SlotContainerDeploymentVerifierContext verifierContext = SlotContainerDeploymentVerifierContext.builder()
                                                                 .logCallback(deployLogCallback)
                                                                 .slotName(slot)
                                                                 .azureWebClient(azureWebClient)
                                                                 .azureWebClientContext(deploymentContext)
                                                                 .logStreamer(slotLogStreamer)
                                                                 .build();

    SlotStatusVerifier statusVerifier =
        SlotStatusVerifier.getStatusVerifier(SLOT_CONTAINER_DEPLOYMENT_VERIFIER.name(), verifierContext);
    slotSteadyStateChecker.waitUntilCompleteWithTimeout(
        timeOut, SLOT_STARTING_STATUS_CHECK_INTERVAL, deployLogCallback, DEPLOY_TO_SLOT, statusVerifier);
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
    markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.DEPLOY_TO_SLOT);
    AzureLogCallbackProvider logCallbackProvider = deploymentContext.getLogCallbackProvider();
    LogCallback deployLog = logCallbackProvider.obtainLogCallback(DEPLOY_TO_SLOT);
    deployLog.saveExecutionLog(String.format(START_SLOT_DEPLOYMENT, deploymentContext.getSlotName()));
    Optional<String> dockerImageAndTag = azureWebClient.getSlotDockerImageNameAndTag(
        deploymentContext.getAzureWebClientContext(), deploymentContext.getSlotName());

    if (dockerImageAndTag.isPresent() && isNotEmpty(dockerImageAndTag.get())) {
      SlotContainerLogStreamer slotLogStreamer = new SlotContainerLogStreamer(
          deploymentContext.getAzureWebClientContext(), azureWebClient, deploymentContext.getSlotName(), deployLog);
      deployArtifactFile(deploymentContext, preDeploymentData, deployLog, false);
      startSlotAsyncWithSteadyCheck(deploymentContext, preDeploymentData, deployLog);
      containerDeploymentSteadyStateCheck(deploymentContext.getSlotName(),
          deploymentContext.getSteadyStateTimeoutInMin(), deploymentContext.getAzureWebClientContext(), deployLog,
          slotLogStreamer);
    } else {
      DateTime startSlotTime = new DateTime(DateTimeZone.UTC).minusMinutes(1);
      StreamPackageDeploymentLogsTask logStreamer =
          new StreamPackageDeploymentLogsTask(deploymentContext.getAzureWebClientContext(), azureWebClient,
              deploymentContext.getSlotName(), deployLog, startSlotTime);
      try {
        logStreamer.run();
        deployArtifactFile(deploymentContext, preDeploymentData, deployLog, true);
        startSlotAsyncWithSteadyCheck(deploymentContext, preDeploymentData, deployLog);
        deploySlotSteadyStateCheck(deploymentContext, logStreamer, deployLog);
      } catch (Exception e) {
        logStreamer.unsubscribe();
        throw e;
      }
    }
    deployLog.saveExecutionLog(String.format(SUCCESS_SLOT_DEPLOYMENT, deploymentContext.getSlotName()), INFO, SUCCESS);
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
      Mono<Response<Void>> response =
          azureWebClient.startDeploymentSlotAsync(deploymentContext.getAzureWebClientContext(), slotName);

      SlotStatusVerifierContext statusVerifierContext =
          SlotStatusVerifierContext.builder()
              .logCallback(deployLogCallback)
              .slotName(slotName)
              .azureWebClient(azureWebClient)
              .azureWebClientContext(deploymentContext.getAzureWebClientContext())
              .responseMono(response)
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
      Mono<Response<Void>> responseMono =
          azureWebClient.stopDeploymentSlotAsync(deploymentContext.getAzureWebClientContext(), slotName);

      SlotStatusVerifierContext statusVerifierContext =
          SlotStatusVerifierContext.builder()
              .logCallback(updateSlotLog)
              .slotName(slotName)
              .azureWebClient(azureWebClient)
              .azureWebClientContext(deploymentContext.getAzureWebClientContext())
              .responseMono(responseMono)
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

    SwapSlotStatusVerifierContext context =
        SwapSlotStatusVerifierContext.builder()
            .logCallback(slotSwapLogCallback)
            .slotName(sourceSlotName)
            .azureWebClient(azureWebClient)
            .azureMonitorClient(azureMonitorClient)
            .azureWebClientContext(azureAppServiceDeploymentContext.getAzureWebClientContext())
            .build();

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(SWAP_VERIFIER.name(), context);

    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.submit(
        new SwapSlotTask(sourceSlotName, targetSlotName, azureWebClient, webClientContext, slotSwapLogCallback));
    executorService.shutdown();

    try {
      slotSteadyStateChecker.waitUntilCompleteWithTimeout(steadyStateTimeoutInMinutes,
          SLOT_STOPPING_STATUS_CHECK_INTERVAL, slotSwapLogCallback, SLOT_SWAP, statusVerifier);
    } catch (Exception e) {
      String message = ExceptionUtils.getMessage(e);
      slotSwapLogCallback.saveExecutionLog(format(SWAP_SLOT_FAILURE, targetSlotName, message), ERROR, FAILURE);
      throw e;
    }

    slotSwapLogCallback.saveExecutionLog(SWAP_SLOT_SUCCESS, INFO, SUCCESS);
  }

  private void deployArtifactFile(AzureAppServicePackageDeploymentContext context,
      AzureAppServicePreDeploymentData preDeploymentData, LogCallback deployLog, boolean isWindowsServicePlan) {
    String slotName = context.getSlotName();
    markDeploymentProgress(preDeploymentData, AppServiceDeploymentProgress.DEPLOY_TO_SLOT);
    try {
      deployLog.saveExecutionLog(START_ARTIFACT_DEPLOY);
      uploadStartupScript(context.getAzureWebClientContext(), slotName, context.getStartupCommand(), deployLog);
      Mono deployment;
      if (context.isUseNewDeployApi()) {
        deployment = deployPackage(context, slotName, deployLog);
      } else {
        deployment = deployPackage(context.getAzureWebClientContext(), slotName, context.getArtifactFile(),
            context.getArtifactType(), deployLog, isWindowsServicePlan);
      }

      deployment.block(Duration.ofMinutes(context.getSteadyStateTimeoutInMin()));
    } catch (Exception exception) {
      deployLog.saveExecutionLog(String.format(FAIL_DEPLOYMENT, exception.getMessage()), ERROR, FAILURE);
      throw new AzureAppServicesDeployArtifactFileException(
          context.getArtifactFile().toPath(), context.getArtifactType().name(), exception);
    }
  }

  private void uploadStartupScript(
      AzureWebClientContext context, final String slotName, final String startupCommand, LogCallback logCallback) {
    logCallback.saveExecutionLog(format(UPDATE_STARTUP_COMMAND, context.getAppName(), slotName));
    azureWebClient.updateSlotConfigurationWithAppCommandLineScript(context, slotName, startupCommand);
    logCallback.saveExecutionLog(SUCCESS_UPDATE_STARTUP_COMMAND);
  }

  private Mono<Void> deployPackage(
      AzureAppServicePackageDeploymentContext context, String slotName, LogCallback logCallback) {
    AzureWebClientContext clientContext = context.getAzureWebClientContext();
    File artifactFile = context.getArtifactFile();
    DeployOptions options = context.toDeployOptions();
    logCallback.saveExecutionLog(format(DEPLOY_LOG, context.getArtifactType()));
    logCallback.saveExecutionLog(
        format(DEPLOY_DETAILS_LOG, artifactFile.getAbsolutePath(), clientContext.getAppName(), slotName));
    logCallback.saveExecutionLog(format(DEPLOY_OPTIONS_LOG, context.getDeployType(), options.cleanDeployment()));
    Mono<Void> deployment = azureWebClient.deployAsync(
        context.getAzureWebClientContext(), context.getDeployType(), slotName, context.getArtifactFile(), options);
    logCallback.saveExecutionLog(ARTIFACT_DEPLOY_STARTED);
    return deployment;
  }

  private Mono deployPackage(AzureWebClientContext azureWebClientContext, final String slotName,
      final File artifactFile, ArtifactType artifactType, LogCallback logCallback, boolean isWindowsServicePlan) {
    switch (artifactType) {
      case ZIP:
      case NUGET:
        return deployZipToSlotAndLog(azureWebClientContext, slotName, artifactFile, logCallback);
      case WAR:
        return deployWarToSlotAndLog(azureWebClientContext, slotName, artifactFile, logCallback);
      case JAR:
        return deployJarToSlot(azureWebClientContext, slotName, artifactFile, logCallback, isWindowsServicePlan);
      default:
        throw new InvalidRequestException(format(UNSUPPORTED_ARTIFACT, artifactType, slotName));
    }
  }

  private Mono deployJarToSlot(AzureWebClientContext azureWebClientContext, final String slotName,
      final File artifactFile, LogCallback logCallback, boolean isWindowsServicePlan) {
    try {
      File zipFile = convertJarToZip(artifactFile, logCallback, isWindowsServicePlan);
      return deployZipToSlotAndLog(azureWebClientContext, slotName, zipFile, logCallback);
    } catch (IOException e) {
      logCallback.saveExecutionLog(String.format(FAIL_DEPLOYMENT, e.getMessage()), ERROR, FAILURE);
      throw new InvalidRequestException("Fail to zip the jar file", e);
    }
  }

  private File convertJarToZip(File artifactFile, LogCallback logCallback, boolean isWindowsServicePlan)
      throws IOException {
    artifactFile = renameJarFile(artifactFile, logCallback, isWindowsServicePlan);
    String artifactPath = artifactFile.getAbsolutePath();
    String zipPath = artifactPath + ZIP_EXTENSION;

    logCallback.saveExecutionLog("Generating zip file ... ");
    try (FileOutputStream fos = new FileOutputStream(zipPath); ZipOutputStream zipOut = new ZipOutputStream(fos);
         FileInputStream fis = new FileInputStream(artifactFile)) {
      ZipEntry zipEntry = new ZipEntry(artifactFile.getName());
      zipOut.putNextEntry(zipEntry);
      byte[] bytes = new byte[1024];
      int length;
      while ((length = fis.read(bytes)) >= 0) {
        zipOut.write(bytes, 0, length);
      }
    }
    return new File(zipPath);
  }

  // artifact file is downloaded to local system with suffix "_34578"
  // e.g.
  // "/private/var/tmp/_bazel_anilchowdhury/repository/azureappsvcartifacts/p2wEjFNSSA60rkrM1NDvjg/spring-boot-hello-2.0.jar_1307164"))
  // we must remove this suffix to work
  private File renameJarFile(File artifactFile, LogCallback logCallback, boolean isWindowsServicePlan) {
    String absolutePath = artifactFile.getAbsolutePath();
    if (absolutePath.endsWith(JAR_EXTENSION) && !isWindowsServicePlan) {
      return artifactFile;
    }
    String newName;
    if (isWindowsServicePlan) {
      newName = DEFAULT_JAR_ARTIFACT_NAME;
      logCallback.saveExecutionLog(String.format("Renaming %s to %s", artifactFile.getName(), newName));
    } else {
      int suffixSeparator = determineSuffixSeparator(absolutePath);
      if (suffixSeparator > -1) {
        int lastIndexOf = absolutePath.lastIndexOf((char) suffixSeparator);
        newName = absolutePath.substring(0, lastIndexOf);
      } else {
        newName = absolutePath;
      }
    }
    var oldFile = new File(absolutePath);
    var newFile = new File(newName);
    if (!oldFile.renameTo(newFile)) {
      throw new InvalidRequestException(String.format(FILE_RENAME_FAILURE, absolutePath, newName));
    }
    return newFile;
  }

  @VisibleForTesting
  protected int determineSuffixSeparator(String absolutePath) {
    if (absolutePath.endsWith("/")) {
      absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
    }
    int lastIndexOf = absolutePath.lastIndexOf('/');
    var fileName = absolutePath.substring(lastIndexOf + 1);
    if (fileName.matches(".*_[0-9]*")) {
      return '_';
    }
    return -1;
  }

  private Mono deployZipToSlotAndLog(
      AzureWebClientContext azureWebClientContext, String slotName, File artifactFile, LogCallback logCallback) {
    logCallback.saveExecutionLog(
        format(ZIP_DEPLOY, artifactFile.getAbsolutePath(), azureWebClientContext.getAppName(), slotName));
    Mono deployment = azureWebClient.deployZipToSlotAsync(azureWebClientContext, slotName, artifactFile);
    logCallback.saveExecutionLog(ARTIFACT_DEPLOY_STARTED);

    return deployment;
  }

  private Mono deployWarToSlotAndLog(
      AzureWebClientContext azureWebClientContext, String slotName, File artifactFile, LogCallback logCallback) {
    logCallback.saveExecutionLog(
        format(WAR_DEPLOY, artifactFile.getAbsolutePath(), azureWebClientContext.getAppName(), slotName));
    Mono deployment = azureWebClient.deployWarToSlotAsync(azureWebClientContext, slotName, artifactFile);
    logCallback.saveExecutionLog(ARTIFACT_DEPLOY_STARTED);

    return deployment;
  }

  private void markDeploymentProgress(
      AzureAppServicePreDeploymentData preDeploymentData, AppServiceDeploymentProgress progress) {
    preDeploymentData.setDeploymentProgressMarker(progress.name());
  }
}
