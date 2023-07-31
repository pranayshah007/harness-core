/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.ROLLBACK_OPERATOR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.Downsize;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.PcfUtils.encodeColor;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfRollbackCommandResult;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfSwapRollbackCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollbackCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CfSwapRollbackCommandTaskHandlerNG extends CfCommandTaskNGHandler {
  @Inject TasTaskHelperBase tasTaskHelperBase;
  @Inject TasNgConfigMapper tasNgConfigMapper;
  @Inject CfDeploymentManager cfDeploymentManager;
  @Inject protected CfCommandTaskHelperNG cfCommandTaskHelperNG;

  @Override
  public CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    if (!(cfCommandRequestNG instanceof CfSwapRollbackCommandRequestNG)) {
      throw new InvalidArgumentsException(
          Pair.of("cfCommandRequest", "Must be instance of CfSwapRollbackCommandRequestNG"));
    }
    CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();
    LogCallback executionLogCallback = tasTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);
    CfRollbackCommandResult cfRollbackCommandResult = CfRollbackCommandResult.builder().build();
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG = CfRollbackCommandResponseNG.builder().build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG = (CfSwapRollbackCommandRequestNG) cfCommandRequestNG;
    File workingDirectory = null;
    List<CfServiceData> cfServiceDataUpdated = new ArrayList<>();

    try {
      // This will be CF_HOME for any cli related operations
      workingDirectory = cfCommandTaskHelperNG.generateWorkingDirectoryForDeployment();
      TasInfraConfig tasInfraConfig = cfRollbackCommandRequestNG.getTasInfraConfig();
      CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(
          tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

      executionLogCallback.saveExecutionLog(color("--------- Starting Swap Rollback Step\n", White, Bold));
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

      CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                                            .userName(String.valueOf(cfConfig.getUserName()))
                                            .endpointUrl(cfConfig.getEndpointUrl())
                                            .password(String.valueOf(cfConfig.getPassword()))
                                            .orgName(tasInfraConfig.getOrganization())
                                            .spaceName(tasInfraConfig.getSpace())
                                            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
                                            .cfHomeDirPath(workingDirectory.getAbsolutePath())
                                            .cfCliPath(cfCommandTaskHelperNG.getCfCliPathOnDelegate(
                                                true, cfRollbackCommandRequestNG.getCfCliVersion()))
                                            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
                                            .useCFCLI(true)
                                            .build();

      TasApplicationInfo activeApplicationDetails = cfRollbackCommandRequestNG.getActiveApplicationDetails();
      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData =
          CfRouteUpdateRequestConfigData.builder()
              .isRollback(true)
              .existingApplicationDetails(activeApplicationDetails != null
                      ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                      : null)
              .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
              .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
              .existingApplicationNames(getExistingApplicationNames(cfRollbackCommandRequestNG))
              .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                      ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                      : null)
              .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
              .skipRollback(false)
              .isStandardBlueGreen(true)
              .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
              .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
              .versioningChanged(false)
              .nonVersioning(true)
              .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
              .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
              .isMapRoutesOperation(false)
              .build();

      List<CfInternalInstanceElement> cfInstanceElements = new ArrayList<>();
      if (cfRollbackCommandRequestNG.isSwapRouteOccurred()) {
        // If rollback and active & in-active app was downsized or renamed, then restore it
        updateValues = restoreAppsDuringRollback(executionLogCallback, cfRollbackCommandRequestNG, cfRequestConfig,
            pcfRouteUpdateConfigData, workingDirectory.getAbsolutePath());
        // Swap routes
        performRouteUpdateForStandardBlueGreen(
            cfRequestConfig, pcfRouteUpdateConfigData, executionLogCallback, cfRollbackCommandResult);
      } else {
        updateValues = handleFailureHappenedBeforeSwapRoute(executionLogCallback, workingDirectory,
            cfRollbackCommandRequestNG, cfRequestConfig, pcfRouteUpdateConfigData, cfRollbackCommandResult);
      }

      // Will be used if app autoscalar is configured
      CfAppAutoscalarRequestData autoscalarRequestData =
          CfAppAutoscalarRequestData.builder()
              .cfRequestConfig(cfRequestConfig)
              .configPathVar(workingDirectory.getAbsolutePath())
              .timeoutInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin() != null
                      ? cfRollbackCommandRequestNG.getTimeoutIntervalInMin()
                      : 10)
              .build();

      executionLogCallback = tasTaskHelperBase.getLogCallback(
          iLogStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);
      // get Upsize Instance data
      List<CfServiceData> upsizeList = cfCommandTaskHelperNG.getUpsizeListForRollback(
          cfRollbackCommandRequestNG.getInstanceData(), cfRollbackCommandRequestNG.getNewApplicationDetails());

      // get Downsize Instance data
      List<CfServiceData> downSizeList =
          cfRollbackCommandRequestNG.getInstanceData()
              .stream()
              .filter(cfServiceData -> cfServiceData.getDesiredCount() < cfServiceData.getPreviousCount())
              .collect(toList());

      // During rollback, always upsize old ones
      cfCommandTaskHelperNG.upsizeListOfInstances(executionLogCallback, cfDeploymentManager, cfServiceDataUpdated,
          cfRequestConfig, upsizeList, cfInstanceElements);

      // Enable autoscalar for older app, if it was disabled during deploy
      if (!isNull(cfRollbackCommandRequestNG.getActiveApplicationDetails())
          && cfRollbackCommandRequestNG.getActiveApplicationDetails().isAutoScalarEnabled()) {
        cfCommandTaskHelperNG.enableAutoscalerIfNeeded(
            cfRollbackCommandRequestNG.getActiveApplicationDetails(), autoscalarRequestData, executionLogCallback);
      }
      if (!isNull(cfRollbackCommandRequestNG.getInActiveApplicationDetails())
          && cfRollbackCommandRequestNG.getInActiveApplicationDetails().isAutoScalarEnabled()) {
        cfCommandTaskHelperNG.enableAutoscalerIfNeeded(
            cfRollbackCommandRequestNG.getInActiveApplicationDetails(), autoscalarRequestData, executionLogCallback);
      }

      executionLogCallback.saveExecutionLog("#---------- Upsize Application Successfully Completed", INFO, SUCCESS);

      executionLogCallback =
          tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Downsize, true, commandUnitsProgress);
      // Downsizing
      cfCommandTaskHelperNG.downSizeListOfInstances(executionLogCallback, cfServiceDataUpdated, cfRequestConfig,
          updateNewAppName(cfRequestConfig, cfRollbackCommandRequestNG, downSizeList),
          cfRollbackCommandRequestNG.isUseAppAutoScalar(), autoscalarRequestData);

      // Deleting
      cfCommandTaskHelperNG.deleteNewApp(cfRequestConfig, cfRollbackCommandRequestNG.getCfAppNamePrefix(),
          cfRollbackCommandRequestNG.getNewApplicationDetails(), executionLogCallback);

      executionLogCallback.saveExecutionLog("#---------- Downsizing Successfully Completed", INFO, SUCCESS);

      cfRollbackCommandResponseNG.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
      cfRollbackCommandResult.setUpdatedValues(updateValues);
      cfRollbackCommandResult.setInstanceDataUpdated(cfServiceDataUpdated);
      if (cfRollbackCommandResult.getCfInstanceElements() != null) {
        cfRollbackCommandResult.getCfInstanceElements().addAll(cfInstanceElements);
      } else {
        cfRollbackCommandResult.setCfInstanceElements(cfInstanceElements);
      }
      List<CfInternalInstanceElement> newAppInstances = getCurrentProdAppInstance(cfRequestConfig,
          cfRollbackCommandRequestNG.getCfAppNamePrefix(), cfRollbackCommandRequestNG.isSwapRouteOccurred(),
          cfRollbackCommandRequestNG.getActiveApplicationDetails());
      cfRollbackCommandResult.setNewAppInstances(newAppInstances);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing PCF Route Update task", sanitizedException);
      executionLogCallback.saveExecutionLog("\n\n--------- PCF Route Update failed to complete successfully");
      executionLogCallback.saveExecutionLog("# Error: " + sanitizedException.getMessage(), INFO, FAILURE);
      cfRollbackCommandResponseNG.setErrorMessage(sanitizedException.getMessage());
      cfRollbackCommandResult.setInstanceDataUpdated(cfServiceDataUpdated);
      cfRollbackCommandResponseNG.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    } finally {
      executionLogCallback =
          tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Wrapup, true, commandUnitsProgress);
      executionLogCallback.saveExecutionLog("#------- Deleting Temporary Files");
      try {
        if (workingDirectory != null) {
          FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
          executionLogCallback.saveExecutionLog("Temporary Files Successfully deleted", INFO, SUCCESS);
        }
      } catch (IOException e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        log.warn("Failed to delete temp directory created for CF CLI login", sanitizedException);
        executionLogCallback.saveExecutionLog(
            "Failed to delete temp directory created for CF CLI login", INFO, FAILURE);
      }
    }
    cfRollbackCommandResponseNG.setCfRollbackCommandResult(cfRollbackCommandResult);
    return cfRollbackCommandResponseNG;
  }

  private List<CfInternalInstanceElement> getCurrentProdAppInstance(CfRequestConfig cfRequestConfig,
      String cfAppNamePrefix, boolean swapRouteOccurred, TasApplicationInfo activeAppInfo)
      throws PivotalClientApiException {
    if (!swapRouteOccurred || isNull(activeAppInfo)) {
      // in both cases we will not update anything
      return Collections.emptyList();
    }
    cfRequestConfig.setApplicationName(cfAppNamePrefix);
    List<CfInternalInstanceElement> instances = new ArrayList<>();
    ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
    applicationDetail.getInstanceDetails().forEach(instance
        -> instances.add(CfInternalInstanceElement.builder()
                             .applicationId(applicationDetail.getId())
                             .displayName(applicationDetail.getName())
                             .instanceIndex(instance.getIndex())
                             .isUpsize(false)
                             .build()));
    return instances;
  }

  private List<String> getExistingApplicationNames(CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG) {
    TasApplicationInfo activeApplicationDetails = cfRollbackCommandRequestNG.getActiveApplicationDetails();
    return activeApplicationDetails == null ? Collections.emptyList()
                                            : Collections.singletonList(activeApplicationDetails.getApplicationName());
  }

  private List<CfServiceData> updateNewAppName(CfRequestConfig cfRequestConfig,
      CfSwapRollbackCommandRequestNG commandRollbackRequest, List<CfServiceData> downSizeList)
      throws PivotalClientApiException {
    String cfAppNamePrefix = commandRollbackRequest.getCfAppNamePrefix();

    for (CfServiceData data : downSizeList) {
      List<String> apps = cfCommandTaskHelperNG.getAppNameBasedOnGuidForBlueGreenDeployment(
          cfRequestConfig, cfAppNamePrefix, data.getId());
      data.setName(isEmpty(apps) ? data.getName() : apps.get(0));
    }
    return downSizeList;
  }

  private CfInBuiltVariablesUpdateValues handleFailureHappenedBeforeSwapRoute(LogCallback executionLogCallback,
      File workingDirectory, CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG, CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData cfRouteUpdateRequestConfigData, CfRollbackCommandResult cfRollbackCommandResult)
      throws PivotalClientApiException {
    CfInBuiltVariablesUpdateValues updateValues =
        performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateRequestConfigData, cfRequestConfig, executionLogCallback);
    executionLogCallback.saveExecutionLog(color("# No Route Update Required for Active app", White, Bold));
    restoreInActiveAppForFailureBeforeSwapRouteStep(executionLogCallback, cfRollbackCommandRequestNG,
        cfRouteUpdateRequestConfigData, cfRequestConfig, workingDirectory.getAbsolutePath(), cfRollbackCommandResult);
    executionLogCallback.saveExecutionLog("#---------- Successfully Completed", INFO, SUCCESS);
    return updateValues;
  }

  private void restoreInActiveAppForFailureBeforeSwapRouteStep(LogCallback executionLogCallback,
      CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG, CfRouteUpdateRequestConfigData routeUpdateConfigData,
      CfRequestConfig cfRequestConfig, String configVarPath, CfRollbackCommandResult cfRollbackCommandResult)
      throws PivotalClientApiException {
    if (routeUpdateConfigData.isUpSizeInActiveApp()) {
      upSizeInActiveApp(
          cfRollbackCommandRequestNG, cfRequestConfig, routeUpdateConfigData, executionLogCallback, configVarPath);
      updateRoutesForInActiveApplication(
          cfRequestConfig, executionLogCallback, routeUpdateConfigData, cfRollbackCommandResult);
    }
    CfAppSetupTimeDetails newApplicationDetails = routeUpdateConfigData.getNewApplicationDetails();
    List<String> newApps = cfCommandTaskHelperNG.getAppNameBasedOnGuidForBlueGreenDeployment(
        cfRequestConfig, routeUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());
    routeUpdateConfigData.setNewApplicationName(
        isEmpty(newApps) ? routeUpdateConfigData.getNewApplicationName() : newApps.get(0));
    clearRoutesAndEnvVariablesForNewApplication(cfRequestConfig, executionLogCallback,
        routeUpdateConfigData.getNewApplicationName(), routeUpdateConfigData.getTempRoutes());
  }

  @VisibleForTesting
  CfInBuiltVariablesUpdateValues restoreAppsDuringRollback(LogCallback executionLogCallback,
      CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG, CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData, String configVarPath) throws PivotalClientApiException {
    CfInBuiltVariablesUpdateValues updateValues =
        performAppRenaming(ROLLBACK_OPERATOR, pcfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    if (pcfRouteUpdateConfigData.isDownsizeOldApplication()) {
      resizeOldApplications(
          cfRollbackCommandRequestNG, cfRequestConfig, pcfRouteUpdateConfigData, executionLogCallback, configVarPath);
    }
    if (pcfRouteUpdateConfigData.isUpSizeInActiveApp()) {
      upSizeInActiveApp(
          cfRollbackCommandRequestNG, cfRequestConfig, pcfRouteUpdateConfigData, executionLogCallback, configVarPath);
    }
    return updateValues;
  }

  private void upSizeInActiveApp(CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG,
      CfRequestConfig cfRequestConfig, CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData,
      LogCallback executionLogCallback, String configVarPath) {
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
      cfDeploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, executionLogCallback);
      enableAutoScalar(cfRollbackCommandRequestNG, cfRequestConfig, executionLogCallback, configVarPath);
    } catch (Exception exception) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(exception);
      log.error("Failed to up size PCF application: " + inActiveAppName, sanitizedException);
      executionLogCallback.saveExecutionLog(
          "Failed while up sizing In Active application: " + encodeColor(inActiveAppName));
    }
  }

  private void enableAutoScalar(CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, String configVarPath)
      throws PivotalClientApiException {
    if (cfRollbackCommandRequestNG.isUseAppAutoScalar()) {
      ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
      CfAppAutoscalarRequestData appAutoScalarRequestData =
          CfAppAutoscalarRequestData.builder()
              .applicationGuid(applicationDetail.getId())
              .applicationName(applicationDetail.getName())
              .cfRequestConfig(cfRequestConfig)
              .configPathVar(configVarPath)
              .timeoutInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
              .build();
      appAutoScalarRequestData.setExpectedEnabled(false);
      cfDeploymentManager.changeAutoscalarState(appAutoScalarRequestData, executionLogCallback, true);
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
        cfDeploymentManager.getPreviousReleases(cfRequestConfig, cfAppNamePrefix);
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
  void resizeOldApplications(CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG, CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData, LogCallback executionLogCallback, String configVarPath) {
    String msg = "\n# Restoring Old Apps to original count";
    executionLogCallback.saveExecutionLog(msg);
    String appNameBeingDownsized = null;

    List<CfAppSetupTimeDetails> existingApplicationDetails = pcfRouteUpdateConfigData.getExistingApplicationDetails();
    if (isNotEmpty(existingApplicationDetails)) {
      try {
        CfAppSetupTimeDetails existingAppDetails = existingApplicationDetails.get(0);
        appNameBeingDownsized = existingAppDetails.getApplicationName();
        int count = existingAppDetails.getInitialInstanceCount();

        cfRequestConfig.setApplicationName(appNameBeingDownsized);
        cfRequestConfig.setDesiredCount(count);
        executionLogCallback.saveExecutionLog(new StringBuilder()
                                                  .append("Resizing Application: {")
                                                  .append(encodeColor(appNameBeingDownsized))
                                                  .append("} to Count: ")
                                                  .append(count)
                                                  .toString());

        CfAppAutoscalarRequestData appAutoscalarRequestData = performResizing(
            cfRollbackCommandRequestNG.isUseAppAutoScalar(), cfRollbackCommandRequestNG.getTimeoutIntervalInMin(),
            cfRequestConfig, configVarPath, executionLogCallback);

        // After resize, enable autoscalar if it was attached.
        if (cfRollbackCommandRequestNG.isUseAppAutoScalar() && appAutoscalarRequestData != null) {
          appAutoscalarRequestData.setExpectedEnabled(false);
          cfDeploymentManager.changeAutoscalarState(appAutoscalarRequestData, executionLogCallback, true);
        }
      } catch (Exception e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        log.error("Failed to downsize PCF application: " + appNameBeingDownsized, sanitizedException);
        executionLogCallback.saveExecutionLog(
            "Failed while downsizing old application: " + encodeColor(appNameBeingDownsized));
      }
    }
  }

  private CfAppAutoscalarRequestData performResizing(boolean isUseAppAutoScalar, Integer timeoutIntervalInMin,
      CfRequestConfig cfRequestConfig, String configVarPath, LogCallback executionLogCallback)
      throws PivotalClientApiException {
    CfAppAutoscalarRequestData appAutoscalarRequestData = null;
    if (isUseAppAutoScalar) {
      ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
      appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                     .applicationGuid(applicationDetail.getId())
                                     .applicationName(applicationDetail.getName())
                                     .cfRequestConfig(cfRequestConfig)
                                     .configPathVar(configVarPath)
                                     .timeoutInMins(timeoutIntervalInMin)
                                     .build();
    }

    // resize app (upsize in swap rollback, downsize in swap state)
    cfDeploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, executionLogCallback);

    return appAutoscalarRequestData;
  }

  private void performRouteUpdateForStandardBlueGreen(CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData data, LogCallback executionLogCallback,
      CfRollbackCommandResult cfRollbackCommandResult) throws PivotalClientApiException {
    CfAppSetupTimeDetails newApplicationDetails = data.getNewApplicationDetails();
    List<String> newApps = cfCommandTaskHelperNG.getAppNameBasedOnGuidForBlueGreenDeployment(
        cfRequestConfig, data.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());
    data.setNewApplicationName(isEmpty(newApps) ? data.getNewApplicationName() : newApps.get(0));

    updateRoutesForExistingApplication(cfRequestConfig, executionLogCallback, data, cfRollbackCommandResult);
    if (data.isUpSizeInActiveApp()) {
      updateRoutesForInActiveApplication(cfRequestConfig, executionLogCallback, data, cfRollbackCommandResult);
    }
    clearRoutesAndEnvVariablesForNewApplication(
        cfRequestConfig, executionLogCallback, data.getNewApplicationName(), data.getFinalRoutes());
    executionLogCallback.saveExecutionLog("#---------- Successfully Completed", INFO, SUCCESS);
  }

  private void clearRoutesAndEnvVariablesForNewApplication(CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback, String appName, List<String> routeList) throws PivotalClientApiException {
    cfCommandTaskHelperNG.unmapRouteMaps(appName, routeList, cfRequestConfig, executionLogCallback);
    cfRequestConfig.setApplicationName(appName);
    cfDeploymentManager.unsetEnvironmentVariableForAppStatus(cfRequestConfig, executionLogCallback);
  }

  private void updateRoutesForInActiveApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      CfRouteUpdateRequestConfigData data, CfRollbackCommandResult cfRollbackCommandResult)
      throws PivotalClientApiException {
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
    cfRollbackCommandResult.setInActiveAppAttachedRoutes(inActiveApplicationDetails.getUrls());
    if (isNotEmpty(inActiveApplicationDetails.getUrls())) {
      executionLogCallback.saveExecutionLog(
          String.format("%nUpdating routes for In Active application - [%s]", encodeColor(inActiveAppName)));
      List<String> inActiveApplicationUrls = inActiveApplicationDetails.getUrls();
      cfCommandTaskHelperNG.mapRouteMaps(
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
      CfRouteUpdateRequestConfigData data, CfRollbackCommandResult cfRollbackCommandResult)
      throws PivotalClientApiException {
    if (isNotEmpty(data.getExistingApplicationNames())) {
      List<String> mapRouteForExistingApp = data.getFinalRoutes();
      List<String> unmapRouteForExistingApp = data.getTempRoutes();
      cfRollbackCommandResult.setActiveAppAttachedRoutes(mapRouteForExistingApp);
      for (String existingAppName : data.getExistingApplicationNames()) {
        if (isNotEmpty(existingAppName)) {
          cfCommandTaskHelperNG.mapRouteMaps(
              existingAppName, mapRouteForExistingApp, cfRequestConfig, executionLogCallback);
          cfCommandTaskHelperNG.unmapRouteMaps(
              existingAppName, unmapRouteForExistingApp, cfRequestConfig, executionLogCallback);
          updateEnvVariableForApplication(cfRequestConfig, executionLogCallback, existingAppName, true);
        }
      }
    }
  }

  private void updateEnvVariableForApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      String appName, boolean isActiveApplication) throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(appName);
    cfDeploymentManager.setEnvironmentVariableForAppStatusNG(
        cfRequestConfig, isActiveApplication, executionLogCallback);
  }

  private CfInBuiltVariablesUpdateValues performAppRenaming(NamingTransition transition,
      CfRouteUpdateRequestConfigData cfRouteUpdateConfigData, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    return cfCommandTaskHelperNG.performAppRenaming(
        transition, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);
  }
}
