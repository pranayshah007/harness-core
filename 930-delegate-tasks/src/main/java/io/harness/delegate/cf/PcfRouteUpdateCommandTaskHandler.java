/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.NON_VERSION_TO_NON_VERSION;
import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.NON_VERSION_TO_VERSION;
import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.ROLLBACK_OPERATOR;
import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.VERSION_TO_NON_VERSION;
import static io.harness.pcf.PcfUtils.encodeColor;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.cf.apprenaming.AppNamingStrategy;
import io.harness.delegate.cf.apprenaming.AppRenamingOperator;
import io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandRouteUpdateRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfRouteUpdateCommandResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfRouteUpdateCommandTaskHandler extends PcfCommandTaskHandler {
  /**
   * Performs RouteSwapping for Blue-Green deployment
   *
   * @param cfCommandRequest
   * @param encryptedDataDetails
   * @param logStreamingTaskClient
   * @param isInstanceSync
   * @return
   */
  @Override
  public CfCommandExecutionResponse executeTaskInternal(CfCommandRequest cfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ILogStreamingTaskClient logStreamingTaskClient,
      boolean isInstanceSync) {
    if (!(cfCommandRequest instanceof CfCommandRouteUpdateRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("cfCommandRequest", "Must be instance of CfCommandRouteUpdateRequest"));
    }
    CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();
    LogCallback executionLogCallback = logStreamingTaskClient.obtainLogCallback(cfCommandRequest.getCommandName());
    CfRouteUpdateCommandResponse cfCommandResponse =
        CfRouteUpdateCommandResponse.builder().updateValues(updateValues).build();
    CfCommandExecutionResponse cfCommandExecutionResponse =
        CfCommandExecutionResponse.builder().pcfCommandResponse(cfCommandResponse).build();

    File workingDirectory = null;
    try {
      // This will be CF_HOME for any cli related operations
      workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();

      executionLogCallback.saveExecutionLog(color("--------- Starting PCF Route Update\n", White, Bold));
      CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest = (CfCommandRouteUpdateRequest) cfCommandRequest;
      CfInternalConfig pcfConfig = cfCommandRouteUpdateRequest.getPcfConfig();
      secretDecryptionService.decrypt(pcfConfig, encryptedDataDetails, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfConfig, encryptedDataDetails);

      CfRequestConfig cfRequestConfig =
          CfRequestConfig.builder()
              .userName(String.valueOf(pcfConfig.getUsername()))
              .endpointUrl(pcfConfig.getEndpointUrl())
              .password(String.valueOf(pcfConfig.getPassword()))
              .orgName(cfCommandRouteUpdateRequest.getOrganization())
              .spaceName(cfCommandRouteUpdateRequest.getSpace())
              .timeOutIntervalInMins(cfCommandRouteUpdateRequest.getTimeoutIntervalInMin())
              .cfHomeDirPath(workingDirectory.getAbsolutePath())
              .useCFCLI(cfCommandRouteUpdateRequest.isUseCfCLI())
              .cfCliPath(pcfCommandTaskBaseHelper.getCfCliPathOnDelegate(
                  cfCommandRequest.isUseCfCLI(), cfCommandRequest.getCfCliVersion()))
              .cfCliVersion(cfCommandRequest.getCfCliVersion())
              .limitPcfThreads(cfCommandRequest.isLimitPcfThreads())
              .build();

      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData =
          cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData();
      if (pcfRouteUpdateConfigData.isStandardBlueGreen()) {
        if (swapRouteExecutionNeeded(pcfRouteUpdateConfigData)) {
          // If rollback and active & in-active app was downsized or renamed, then restore it
          updateValues = restoreAppsDuringRollback(executionLogCallback, cfCommandRouteUpdateRequest, cfRequestConfig,
              pcfRouteUpdateConfigData, workingDirectory.getAbsolutePath());
          // Swap routes
          performRouteUpdateForStandardBlueGreen(cfCommandRouteUpdateRequest, cfRequestConfig, executionLogCallback);
          // if deploy and downsizeOld is true
          if (!pcfRouteUpdateConfigData.isRollback()) {
            updateValues = downsizeOldAppDuringDeployAndRenameApps(executionLogCallback, cfCommandRouteUpdateRequest,
                cfRequestConfig, pcfRouteUpdateConfigData, workingDirectory.getAbsolutePath());
          }
        } else {
          updateValues = handleFailureHappenedBeforeSwapRoute(
              executionLogCallback, workingDirectory, cfCommandRouteUpdateRequest, cfRequestConfig);
        }
      } else {
        performRouteUpdateForSimulatedBlueGreen(cfCommandRouteUpdateRequest, cfRequestConfig, executionLogCallback);
      }

      cfCommandResponse.setUpdatedValues(updateValues);
      executionLogCallback.saveExecutionLog(
          "\n--------- PCF Route Update completed successfully", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      cfCommandResponse.setOutput(StringUtils.EMPTY);
      cfCommandResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing PCF Route Update task", sanitizedException);
      executionLogCallback.saveExecutionLog("\n\n--------- PCF Route Update failed to complete successfully");
      executionLogCallback.saveExecutionLog("# Error: " + sanitizedException.getMessage());
      cfCommandResponse.setOutput(sanitizedException.getMessage());
      cfCommandResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    } finally {
      try {
        if (workingDirectory != null) {
          FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
        }
      } catch (IOException e) {
        log.warn("Failed to delete temp directory created for CF CLI login", e);
      }
    }

    cfCommandExecutionResponse.setCommandExecutionStatus(cfCommandResponse.getCommandExecutionStatus());
    cfCommandExecutionResponse.setErrorMessage(cfCommandResponse.getOutput());
    return cfCommandExecutionResponse;
  }

  private CfInBuiltVariablesUpdateValues handleFailureHappenedBeforeSwapRoute(LogCallback executionLogCallback,
      File workingDirectory, CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest, CfRequestConfig cfRequestConfig)
      throws PivotalClientApiException {
    CfInBuiltVariablesUpdateValues updateValues = performAppRenaming(ROLLBACK_OPERATOR,
        cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData(), cfRequestConfig, executionLogCallback);
    executionLogCallback.saveExecutionLog(color("# No Route Update Required for Active app", White, Bold));
    restoreInActiveAppForFailureBeforeSwapRouteStep(
        executionLogCallback, cfCommandRouteUpdateRequest, cfRequestConfig, workingDirectory.getAbsolutePath());
    return updateValues;
  }

  private void restoreInActiveAppForFailureBeforeSwapRouteStep(LogCallback executionLogCallback,
      CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest, CfRequestConfig cfRequestConfig, String configVarPath)
      throws PivotalClientApiException {
    CfRouteUpdateRequestConfigData routeUpdateConfigData = cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData();
    if (routeUpdateConfigData.isUpSizeInActiveApp()) {
      upSizeInActiveApp(cfCommandRouteUpdateRequest, cfRequestConfig, executionLogCallback, configVarPath);
      updateRoutesForInActiveApplication(cfRequestConfig, executionLogCallback, routeUpdateConfigData);
    }
    CfRouteUpdateRequestConfigData routeConfigData = cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData();
    CfAppSetupTimeDetails newApplicationDetails = routeConfigData.getNewApplicationDetails();
    List<String> newApps = pcfCommandTaskBaseHelper.getAppNameBasedOnGuid(
        cfRequestConfig, routeConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());
    routeConfigData.setNewApplicationName(isEmpty(newApps) ? routeConfigData.getNewApplicationName() : newApps.get(0));
    clearRoutesAndEnvVariablesForNewApplication(cfRequestConfig, executionLogCallback,
        routeUpdateConfigData.getNewApplicationName(), routeUpdateConfigData.getTempRoutes());
  }

  // This tells if routeUpdate needs to happen in Rollback.
  // If its rollback, and routeUpdate was not executed, no need to do anything
  @VisibleForTesting
  boolean swapRouteExecutionNeeded(CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData) {
    boolean executionNeeded;
    if (pcfRouteUpdateConfigData == null) {
      executionNeeded = false;
    } else if (!pcfRouteUpdateConfigData.isRollback()) {
      executionNeeded = true;
    } else {
      executionNeeded = !pcfRouteUpdateConfigData.isSkipRollback();
    }

    return executionNeeded;
  }

  @VisibleForTesting
  CfInBuiltVariablesUpdateValues restoreAppsDuringRollback(LogCallback executionLogCallback,
      CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest, CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData, String configVarPath) throws PivotalClientApiException {
    if (!pcfRouteUpdateConfigData.isRollback()) {
      return CfInBuiltVariablesUpdateValues.builder().build();
    }
    CfInBuiltVariablesUpdateValues updateValues = performAppRenaming(ROLLBACK_OPERATOR,
        cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData(), cfRequestConfig, executionLogCallback);

    if (pcfRouteUpdateConfigData.isDownsizeOldApplication()) {
      resizeOldApplications(cfCommandRouteUpdateRequest, cfRequestConfig, executionLogCallback, true, configVarPath);
    }
    if (pcfRouteUpdateConfigData.isUpSizeInActiveApp()) {
      upSizeInActiveApp(cfCommandRouteUpdateRequest, cfRequestConfig, executionLogCallback, configVarPath);
    }
    return updateValues;
  }

  private void upSizeInActiveApp(CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, String configVarPath) {
    CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData = cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData();
    CfAppSetupTimeDetails existingInActiveApplicationDetails =
        pcfRouteUpdateConfigData.getExistingInActiveApplicationDetails();
    if (existingInActiveApplicationDetails == null
        || isEmpty(existingInActiveApplicationDetails.getApplicationGuid())) {
      executionLogCallback.saveExecutionLog(
          color("\nNo in-active application found for up sizing. Hence skipping", White, Bold));
      return;
    }

    executionLogCallback.saveExecutionLog(color("\n# Restoring In Active App to original count", White, Bold));
    String inActiveAppName = existingInActiveApplicationDetails.getApplicationName();
    try {
      Integer instanceCount = existingInActiveApplicationDetails.getInitialInstanceCount();
      if (instanceCount == null || instanceCount <= 0) {
        executionLogCallback.saveExecutionLog(
            "No up size required for In Active application as original instance count was 0\n");
        return;
      }

      inActiveAppName = getAppNameBasedOnGuid(
          existingInActiveApplicationDetails, pcfRouteUpdateConfigData.getCfAppNamePrefix(), cfRequestConfig);
      if (isEmpty(inActiveAppName)) {
        executionLogCallback.saveExecutionLog(
            "Could not find in active application. Hence skipping up size for In Active application");
        return;
      }

      cfRequestConfig.setApplicationName(inActiveAppName);
      cfRequestConfig.setDesiredCount(instanceCount);
      executionLogCallback.saveExecutionLog(
          "Resizing Application: {" + encodeColor(inActiveAppName) + "} to Count: " + instanceCount);
      pcfDeploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, executionLogCallback);
      enableAutoScalar(cfCommandRouteUpdateRequest, cfRequestConfig, executionLogCallback, configVarPath);
    } catch (Exception exception) {
      log.error("Failed to up size PCF application: " + inActiveAppName, exception);
      executionLogCallback.saveExecutionLog(
          "Failed while up sizing In Active application: " + encodeColor(inActiveAppName));
    }
  }

  private void enableAutoScalar(CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, String configVarPath)
      throws PivotalClientApiException {
    if (cfCommandRouteUpdateRequest.isUseAppAutoscalar()) {
      ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
      CfAppAutoscalarRequestData appAutoScalarRequestData =
          CfAppAutoscalarRequestData.builder()
              .applicationGuid(applicationDetail.getId())
              .applicationName(applicationDetail.getName())
              .cfRequestConfig(cfRequestConfig)
              .configPathVar(configVarPath)
              .timeoutInMins(cfCommandRouteUpdateRequest.getTimeoutIntervalInMin())
              .build();
      appAutoScalarRequestData.setExpectedEnabled(false);
      pcfDeploymentManager.changeAutoscalarState(appAutoScalarRequestData, executionLogCallback, true);
    }
  }

  private String getAppNameBasedOnGuid(CfAppSetupTimeDetails existingInActiveApplicationDetails, String cfAppNamePrefix,
      CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    if (existingInActiveApplicationDetails == null) {
      return "";
    }
    if (isEmpty(existingInActiveApplicationDetails.getApplicationGuid())) {
      return existingInActiveApplicationDetails.getApplicationName();
    }
    String applicationGuid = existingInActiveApplicationDetails.getApplicationGuid();
    List<ApplicationSummary> previousReleases =
        pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfAppNamePrefix);
    List<String> appNames = previousReleases.stream()
                                .filter(app -> app.getId().equalsIgnoreCase(applicationGuid))
                                .map(ApplicationSummary::getName)
                                .collect(Collectors.toList());
    if (appNames.size() == 1) {
      return appNames.get(0);
    }
    return existingInActiveApplicationDetails.getApplicationName();
  }

  @VisibleForTesting
  void resizeOldApplications(CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback, boolean isRollback, String configVarPath) {
    CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData = cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData();

    String msg =
        isRollback ? "\n# Restoring Old Apps to original count" : "\n# Resizing Old Apps to 0 count as configured";
    executionLogCallback.saveExecutionLog(msg);
    String appNameBeingDownsized = null;

    List<CfAppSetupTimeDetails> existingApplicationDetails = pcfRouteUpdateConfigData.getExistingApplicationDetails();
    if (isNotEmpty(existingApplicationDetails)) {
      try {
        CfAppSetupTimeDetails existingAppDetails = existingApplicationDetails.get(0);
        appNameBeingDownsized = existingAppDetails.getApplicationName();
        int count = isRollback ? existingAppDetails.getInitialInstanceCount() : 0;

        cfRequestConfig.setApplicationName(appNameBeingDownsized);
        cfRequestConfig.setDesiredCount(count);
        executionLogCallback.saveExecutionLog(new StringBuilder()
                                                  .append("Resizing Application: {")
                                                  .append(encodeColor(appNameBeingDownsized))
                                                  .append("} to Count: ")
                                                  .append(count)
                                                  .toString());

        // If downsizing, disable auto-scalar
        CfAppAutoscalarRequestData appAutoscalarRequestData = null;
        if (cfCommandRouteUpdateRequest.isUseAppAutoscalar()) {
          ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
          appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                         .applicationGuid(applicationDetail.getId())
                                         .applicationName(applicationDetail.getName())
                                         .cfRequestConfig(cfRequestConfig)
                                         .configPathVar(configVarPath)
                                         .timeoutInMins(cfCommandRouteUpdateRequest.getTimeoutIntervalInMin())
                                         .build();

          // Before downsizing, disable autoscalar if its enabled.
          if (!isRollback) {
            appAutoscalarRequestData.setExpectedEnabled(true);
            pcfCommandTaskBaseHelper.disableAutoscalar(appAutoscalarRequestData, executionLogCallback);
          }
        }

        // resize app (upsize in swap rollback, downsize in swap state)
        if (!isRollback) {
          pcfDeploymentManager.resizeApplication(cfRequestConfig, executionLogCallback);
        } else {
          pcfDeploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, executionLogCallback);
        }

        // After resize, enable autoscalar if it was attached.
        if (isRollback && cfCommandRouteUpdateRequest.isUseAppAutoscalar()) {
          appAutoscalarRequestData.setExpectedEnabled(false);
          pcfDeploymentManager.changeAutoscalarState(appAutoscalarRequestData, executionLogCallback, true);
        }
      } catch (Exception e) {
        log.error("Failed to downsize PCF application: " + appNameBeingDownsized, e);
        executionLogCallback.saveExecutionLog(
            "Failed while downsizing old application: " + encodeColor(appNameBeingDownsized));
      }
    }
  }

  @VisibleForTesting
  CfInBuiltVariablesUpdateValues downsizeOldAppDuringDeployAndRenameApps(LogCallback executionLogCallback,
      CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest, CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData, String configVarPath) throws PivotalClientApiException {
    if (pcfRouteUpdateConfigData.isDownsizeOldApplication()) {
      resizeOldApplications(cfCommandRouteUpdateRequest, cfRequestConfig, executionLogCallback, false, configVarPath);
    }
    return renameApps(cfCommandRouteUpdateRequest, cfRequestConfig, executionLogCallback);
  }

  private void performRouteUpdateForSimulatedBlueGreen(CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    CfRouteUpdateRequestConfigData data = cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData();

    for (String appName : data.getExistingApplicationNames()) {
      if (data.isMapRoutesOperation()) {
        pcfCommandTaskBaseHelper.mapRouteMaps(appName, data.getFinalRoutes(), cfRequestConfig, executionLogCallback);
      } else {
        pcfCommandTaskBaseHelper.unmapRouteMaps(appName, data.getFinalRoutes(), cfRequestConfig, executionLogCallback);
      }
    }
  }

  private void performRouteUpdateForStandardBlueGreen(CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    CfRouteUpdateRequestConfigData data = cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData();

    if (!data.isRollback()) {
      updateRoutesForNewApplication(cfRequestConfig, executionLogCallback, data);
      updateRoutesForExistingApplication(cfRequestConfig, executionLogCallback, data);
    } else {
      CfAppSetupTimeDetails newApplicationDetails = data.getNewApplicationDetails();
      List<String> newApps = pcfCommandTaskBaseHelper.getAppNameBasedOnGuid(
          cfRequestConfig, data.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());
      data.setNewApplicationName(isEmpty(newApps) ? data.getNewApplicationName() : newApps.get(0));

      updateRoutesForExistingApplication(cfRequestConfig, executionLogCallback, data);
      if (data.isUpSizeInActiveApp()) {
        updateRoutesForInActiveApplication(cfRequestConfig, executionLogCallback, data);
      }
      clearRoutesAndEnvVariablesForNewApplication(
          cfRequestConfig, executionLogCallback, data.getNewApplicationName(), data.getFinalRoutes());
    }
  }

  private void clearRoutesAndEnvVariablesForNewApplication(CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback, String appName, List<String> routeList) throws PivotalClientApiException {
    pcfCommandTaskBaseHelper.unmapRouteMaps(appName, routeList, cfRequestConfig, executionLogCallback);
    cfRequestConfig.setApplicationName(appName);
    pcfDeploymentManager.unsetEnvironmentVariableForAppStatus(cfRequestConfig, executionLogCallback);
  }

  private void updateRoutesForInActiveApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      CfRouteUpdateRequestConfigData data) throws PivotalClientApiException {
    CfAppSetupTimeDetails inActiveApplicationDetails = data.getExistingInActiveApplicationDetails();
    if (inActiveApplicationDetails == null || isEmpty(inActiveApplicationDetails.getApplicationGuid())) {
      executionLogCallback.saveExecutionLog(
          color("No in-active application found for updating routes. Hence skipping\n", White, Bold));
      return;
    }
    String inActiveAppName =
        getAppNameBasedOnGuid(inActiveApplicationDetails, data.getCfAppNamePrefix(), cfRequestConfig);
    if (isEmpty(inActiveAppName)) {
      executionLogCallback.saveExecutionLog(
          color("Could not find in active application. Hence skipping update route for In Active Application\n", White,
              Bold));
      return;
    }

    if (isNotEmpty(inActiveApplicationDetails.getUrls())) {
      executionLogCallback.saveExecutionLog(
          String.format("%nUpdating routes for In Active application - [%s]", encodeColor(inActiveAppName)));
      List<String> inActiveApplicationUrls = inActiveApplicationDetails.getUrls();
      pcfCommandTaskBaseHelper.mapRouteMaps(
          inActiveAppName, inActiveApplicationUrls, cfRequestConfig, executionLogCallback);
    } else {
      executionLogCallback.saveExecutionLog(
          color(String.format("No previous route defined for in active application - [%s]. Hence skipping",
                    encodeColor(inActiveAppName)),
              White, Bold));
    }
    updateEnvVariableForApplication(cfRequestConfig, executionLogCallback, inActiveAppName, false);
  }

  private void updateRoutesForExistingApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      CfRouteUpdateRequestConfigData data) throws PivotalClientApiException {
    if (isNotEmpty(data.getExistingApplicationNames())) {
      List<String> mapRouteForExistingApp = data.isRollback() ? data.getFinalRoutes() : data.getTempRoutes();
      List<String> unmapRouteForExistingApp = data.isRollback() ? data.getTempRoutes() : data.getFinalRoutes();
      for (String existingAppName : data.getExistingApplicationNames()) {
        pcfCommandTaskBaseHelper.mapRouteMaps(
            existingAppName, mapRouteForExistingApp, cfRequestConfig, executionLogCallback);
        pcfCommandTaskBaseHelper.unmapRouteMaps(
            existingAppName, unmapRouteForExistingApp, cfRequestConfig, executionLogCallback);
        updateEnvVariableForApplication(cfRequestConfig, executionLogCallback, existingAppName, data.isRollback());
      }
    }
  }

  private void updateEnvVariableForApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      String appName, boolean isActiveApplication) throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(appName);
    pcfDeploymentManager.setEnvironmentVariableForAppStatus(cfRequestConfig, isActiveApplication, executionLogCallback);
  }

  private void updateRoutesForNewApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      CfRouteUpdateRequestConfigData data) throws PivotalClientApiException {
    List<String> mapRouteForNewApp = data.isRollback() ? data.getTempRoutes() : data.getFinalRoutes();
    List<String> unmapRouteForNewApp = data.isRollback() ? data.getFinalRoutes() : data.getTempRoutes();
    pcfCommandTaskBaseHelper.mapRouteMaps(
        data.getNewApplicationName(), mapRouteForNewApp, cfRequestConfig, executionLogCallback);
    pcfCommandTaskBaseHelper.unmapRouteMaps(
        data.getNewApplicationName(), unmapRouteForNewApp, cfRequestConfig, executionLogCallback);
    // mark new app as ACTIVE if not rollback, STAGE if rollback
    updateEnvVariableForApplication(
        cfRequestConfig, executionLogCallback, data.getNewApplicationName(), !data.isRollback());
  }

  private CfInBuiltVariablesUpdateValues renameApps(CfCommandRouteUpdateRequest cfCommandRouteUpdateRequest,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData = cfCommandRouteUpdateRequest.getPcfRouteUpdateConfigData();
    AppNamingStrategy existingStrategy = AppNamingStrategy.get(pcfRouteUpdateConfigData.getExistingAppNamingStrategy());
    if (AppNamingStrategy.VERSIONING == existingStrategy) {
      return performRenamingWhenExistingStrategyWasVersioning(
          pcfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);
    } else if (AppNamingStrategy.APP_NAME_WITH_VERSIONING == existingStrategy) {
      return performRenamingWhenExistingStrategyWasNonVersioning(
          pcfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);
    }
    return CfInBuiltVariablesUpdateValues.builder().build();
  }

  private CfInBuiltVariablesUpdateValues performRenamingWhenExistingStrategyWasVersioning(
      CfRouteUpdateRequestConfigData cfRouteUpdateConfigData, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    if (!cfRouteUpdateConfigData.isNonVersioning()) {
      // this indicates versioning to versioning deployment, hence no renaming is required
      return CfInBuiltVariablesUpdateValues.builder().build();
    }
    executionLogCallback.saveExecutionLog(color("\n# Starting Renaming apps", White, Bold));
    return performAppRenaming(VERSION_TO_NON_VERSION, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);
  }

  private CfInBuiltVariablesUpdateValues performRenamingWhenExistingStrategyWasNonVersioning(
      CfRouteUpdateRequestConfigData cfRouteUpdateConfigData, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog(color("\n# Starting Renaming apps", White, Bold));
    boolean nonVersioning = cfRouteUpdateConfigData.isNonVersioning();
    NamingTransition transition = nonVersioning ? NON_VERSION_TO_NON_VERSION : NON_VERSION_TO_VERSION;
    return performAppRenaming(transition, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);
  }

  private CfInBuiltVariablesUpdateValues performAppRenaming(NamingTransition transition,
      CfRouteUpdateRequestConfigData cfRouteUpdateConfigData, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    AppRenamingOperator renamingOperator = AppRenamingOperator.of(transition);
    return renamingOperator.renameApp(
        cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback, pcfDeploymentManager, pcfCommandTaskBaseHelper);
  }
}
