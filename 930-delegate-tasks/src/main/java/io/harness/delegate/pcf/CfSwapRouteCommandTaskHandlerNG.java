/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.pcf;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.NON_VERSION_TO_NON_VERSION;
import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.NON_VERSION_TO_VERSION;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.PcfUtils.encodeColor;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

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
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.beans.pcf.CfSwapRouteCommandResult;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfSwapRoutesRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfSwapRouteCommandResponseNG;
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
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CfSwapRouteCommandTaskHandlerNG extends CfCommandTaskNGHandler {
  @Inject TasTaskHelperBase tasTaskHelperBase;
  @Inject TasNgConfigMapper tasNgConfigMapper;
  @Inject CfDeploymentManager cfDeploymentManager;
  @Inject protected CfCommandTaskHelperNG cfCommandTaskHelperNG;

  @Override
  public CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    if (!(cfCommandRequestNG instanceof CfSwapRoutesRequestNG)) {
      throw new InvalidArgumentsException(Pair.of("cfCommandRequest", "Must be instance of CfSwapRoutesRequestNG"));
    }
    CfInBuiltVariablesUpdateValues updateValues = null;
    LogCallback executionLogCallback = tasTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, CfCommandUnitConstants.SwapRoutesForNewApplication, true, commandUnitsProgress);
    CfSwapRouteCommandResult cfSwapRouteCommandResult = CfSwapRouteCommandResult.builder().build();
    CfSwapRouteCommandResponseNG cfSwapRouteCommandResponseNG = CfSwapRouteCommandResponseNG.builder().build();

    CfSwapRoutesRequestNG cfSwapRoutesRequestNG = (CfSwapRoutesRequestNG) cfCommandRequestNG;
    File workingDirectory = null;
    try {
      // This will be CF_HOME for any cli related operations
      workingDirectory = cfCommandTaskHelperNG.generateWorkingDirectoryForDeployment();
      TasInfraConfig tasInfraConfig = cfSwapRoutesRequestNG.getTasInfraConfig();
      CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(
          tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

      executionLogCallback.saveExecutionLog(color("--------- Starting PCF Route Update\n", White, Bold));
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

      CfRequestConfig cfRequestConfig =
          CfRequestConfig.builder()
              .userName(String.valueOf(cfConfig.getUserName()))
              .endpointUrl(cfConfig.getEndpointUrl())
              .password(String.valueOf(cfConfig.getPassword()))
              .orgName(tasInfraConfig.getOrganization())
              .spaceName(tasInfraConfig.getSpace())
              .timeOutIntervalInMins(cfSwapRoutesRequestNG.getTimeoutIntervalInMin())
              .cfHomeDirPath(workingDirectory.getAbsolutePath())
              .cfCliPath(cfCommandTaskHelperNG.getCfCliPathOnDelegate(true, cfSwapRoutesRequestNG.getCfCliVersion()))
              .cfCliVersion(cfSwapRoutesRequestNG.getCfCliVersion())
              .useCFCLI(true)
              .build();

      if (cfSwapRoutesRequestNG.getActiveApplicationDetails() != null) {
        List<String> existingApps = cfCommandTaskHelperNG.getAppNameBasedOnGuidForBlueGreenDeployment(cfRequestConfig,
            cfSwapRoutesRequestNG.getReleaseNamePrefix(),
            cfSwapRoutesRequestNG.getActiveApplicationDetails().getApplicationGuid());
        String existingAppName = isEmpty(existingApps)
            ? cfSwapRoutesRequestNG.getActiveApplicationDetails().getApplicationName()
            : existingApps.get(0);
        cfRequestConfig.setApplicationName(existingAppName);
        ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
        TasApplicationInfo activeApplicationInfo =
            TasApplicationInfo.builder()
                .applicationGuid(applicationDetail.getId())
                .applicationName(applicationDetail.getName())
                .oldName(cfSwapRoutesRequestNG.getActiveApplicationDetails().getOldName())
                .attachedRoutes(new ArrayList<>(applicationDetail.getUrls()))
                .runningCount(applicationDetail.getRunningInstances())
                .build();

        cfSwapRoutesRequestNG.setActiveApplicationDetails(activeApplicationInfo);
        cfSwapRoutesRequestNG.setExistingApplicationNames(Collections.singletonList(existingAppName));

        CfAppAutoscalarRequestData appAutoscalarRequestData = null;
        appAutoscalarRequestData =
            CfAppAutoscalarRequestData.builder()
                .applicationGuid(cfSwapRoutesRequestNG.getActiveApplicationDetails().getApplicationGuid())
                .applicationName(cfSwapRoutesRequestNG.getActiveApplicationDetails().getApplicationName())
                .cfRequestConfig(cfRequestConfig)
                .configPathVar(workingDirectory.getAbsolutePath())
                .timeoutInMins(cfSwapRoutesRequestNG.getTimeoutIntervalInMin())
                .build();
        boolean isAutoScalarEnabled =
            cfDeploymentManager.checkIfAppHasAutoscalarEnabled(appAutoscalarRequestData, executionLogCallback);
        cfSwapRoutesRequestNG.setUseAppAutoScalar(isAutoScalarEnabled);
      }

      if (cfSwapRoutesRequestNG.getNewApplicationDetails() != null) {
        List<String> newApps = cfCommandTaskHelperNG.getAppNameBasedOnGuidForBlueGreenDeployment(cfRequestConfig,
            cfSwapRoutesRequestNG.getReleaseNamePrefix(),
            cfSwapRoutesRequestNG.getNewApplicationDetails().getApplicationGuid());
        String newAppName = isEmpty(newApps) ? cfSwapRoutesRequestNG.getNewApplicationName() : newApps.get(0);
        cfRequestConfig.setApplicationName(newAppName);
        ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
        TasApplicationInfo newApplicationInfo =
            TasApplicationInfo.builder()
                .applicationGuid(applicationDetail.getId())
                .applicationName(applicationDetail.getName())
                .oldName(cfSwapRoutesRequestNG.getNewApplicationDetails().getOldName())
                .attachedRoutes(new ArrayList<>(applicationDetail.getUrls()))
                .runningCount(applicationDetail.getRunningInstances())
                .build();

        cfSwapRoutesRequestNG.setNewApplicationDetails(newApplicationInfo);
        cfSwapRoutesRequestNG.setNewApplicationName(newAppName);
      }

      TasApplicationInfo activeApplicationDetails = cfSwapRoutesRequestNG.getActiveApplicationDetails();
      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData =
          CfRouteUpdateRequestConfigData.builder()
              .isRollback(true)
              .existingApplicationDetails(activeApplicationDetails != null
                      ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                      : null)
              .cfAppNamePrefix(cfSwapRoutesRequestNG.getReleaseNamePrefix())
              .downsizeOldApplication(cfSwapRoutesRequestNG.isDownsizeOldApplication())
              .existingApplicationNames(activeApplicationDetails == null
                      ? Collections.emptyList()
                      : Collections.singletonList(activeApplicationDetails.getApplicationName()))
              .tempRoutes(cfSwapRoutesRequestNG.getTempRoutes())
              .skipRollback(false)
              .isStandardBlueGreen(true)
              .newApplicationDetails(cfSwapRoutesRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
              .versioningChanged(false)
              .nonVersioning(true)
              .newApplicationName(cfSwapRoutesRequestNG.getNewApplicationDetails().getApplicationName())
              .finalRoutes(cfSwapRoutesRequestNG.getFinalRoutes())
              .isMapRoutesOperation(false)
              .build();
      // Swap routes
      performRouteUpdateForStandardBlueGreen(cfRequestConfig, pcfRouteUpdateConfigData, iLogStreamingTaskClient,
          commandUnitsProgress, executionLogCallback);

      executionLogCallback = tasTaskHelperBase.getLogCallback(
          iLogStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);
      // if deploy and downsizeOld is true
      updateValues = downsizeOldAppDuringDeployAndRenameApps(executionLogCallback, cfSwapRoutesRequestNG,
          cfRequestConfig, pcfRouteUpdateConfigData, workingDirectory.getAbsolutePath(), iLogStreamingTaskClient,
          commandUnitsProgress);

      getInstancesForNewApplication(
          cfSwapRoutesRequestNG.getReleaseNamePrefix(), cfSwapRouteCommandResult, cfRequestConfig);

      cfSwapRouteCommandResult.setUpdatedValues(updateValues);
      cfSwapRouteCommandResponseNG.setErrorMessage(StringUtils.EMPTY);
      cfSwapRouteCommandResponseNG.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
      cfSwapRouteCommandResponseNG.setNewApplicationName(cfSwapRoutesRequestNG.getReleaseNamePrefix());
      cfSwapRouteCommandResponseNG.setCfSwapRouteCommandResult(cfSwapRouteCommandResult);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing PCF Route Update task", sanitizedException);
      executionLogCallback.saveExecutionLog("\n\n--------- Step failed to complete successfully");
      executionLogCallback.saveExecutionLog(
          "# Error: " + sanitizedException.getMessage(), ERROR, CommandExecutionStatus.FAILURE);
      cfSwapRouteCommandResponseNG.setErrorMessage(sanitizedException.getMessage());
      cfSwapRouteCommandResponseNG.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    } finally {
      LogCallback logCallback =
          tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Wrapup, true, commandUnitsProgress);
      try {
        if (workingDirectory != null) {
          logCallback.saveExecutionLog("Removing any temporary files created");
          FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
        }
      } catch (IOException e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        log.warn("Failed to delete temp directory created for CF CLI login", sanitizedException);
      } finally {
        logCallback.saveExecutionLog("Cleaning up temporary files completed", INFO, SUCCESS);
      }
    }
    return cfSwapRouteCommandResponseNG;
  }

  private void getInstancesForNewApplication(
      String newApplicationName, CfSwapRouteCommandResult cfSwapRouteCommandResult, CfRequestConfig cfRequestConfig)
      throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(newApplicationName);
    ApplicationDetail newAppDetails = cfDeploymentManager.getApplicationByName(cfRequestConfig);
    List<CfInternalInstanceElement> newAppInstances = new ArrayList<>();

    newAppDetails.getInstanceDetails().forEach(instance
        -> newAppInstances.add(CfInternalInstanceElement.builder()
                                   .applicationId(newAppDetails.getId())
                                   .displayName(newAppDetails.getName())
                                   .instanceIndex(instance.getIndex())
                                   .isUpsize(false)
                                   .build()));
    cfSwapRouteCommandResult.setNewAppInstances(newAppInstances);
  }

  CfInBuiltVariablesUpdateValues downsizeOldAppDuringDeployAndRenameApps(LogCallback executionLogCallback,
      CfSwapRoutesRequestNG cfSwapRoutesRequestNG, CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData, String configVarPath,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws PivotalClientApiException {
    if (pcfRouteUpdateConfigData.isDownsizeOldApplication()) {
      resizeOldApplications(
          cfSwapRoutesRequestNG, cfRequestConfig, pcfRouteUpdateConfigData, executionLogCallback, configVarPath);
    } else {
      executionLogCallback.saveExecutionLog(
          "Skipping as Downsize Old Application option is not set true", INFO, SUCCESS);
    }
    executionLogCallback = tasTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, CfCommandUnitConstants.Rename, true, commandUnitsProgress);
    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues =
        renameApps(pcfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);
    executionLogCallback.saveExecutionLog("Renaming of Apps Completed", INFO, CommandExecutionStatus.SUCCESS);
    return cfInBuiltVariablesUpdateValues;
  }

  private CfInBuiltVariablesUpdateValues renameApps(CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback) throws PivotalClientApiException {
    return performRenamingWhenExistingStrategyWasNonVersioning(
        pcfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);
  }

  private CfInBuiltVariablesUpdateValues performRenamingWhenExistingStrategyWasNonVersioning(
      CfRouteUpdateRequestConfigData cfRouteUpdateConfigData, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    executionLogCallback.saveExecutionLog(color("\n# Starting Renaming apps", White, Bold));
    boolean nonVersioning = cfRouteUpdateConfigData.isNonVersioning();
    NamingTransition transition = nonVersioning ? NON_VERSION_TO_NON_VERSION : NON_VERSION_TO_VERSION;
    return performAppRenaming(transition, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);
  }

  @VisibleForTesting
  void resizeOldApplications(CfSwapRoutesRequestNG cfSwapRoutesRequestNG, CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData, LogCallback executionLogCallback, String configVarPath)
      throws PivotalClientApiException {
    String appNameBeingDownsized = null;

    List<CfAppSetupTimeDetails> existingApplicationDetails = pcfRouteUpdateConfigData.getExistingApplicationDetails();
    if (isNotEmpty(existingApplicationDetails)) {
      try {
        CfAppSetupTimeDetails existingAppDetails = existingApplicationDetails.get(0);
        appNameBeingDownsized = existingAppDetails.getApplicationName();
        int count = 0;

        if (isNotEmpty(appNameBeingDownsized)) {
          String msg = "Resizing Old Apps to 0 count as configured";
          executionLogCallback.saveExecutionLog(msg);

          cfRequestConfig.setApplicationName(appNameBeingDownsized);
          cfRequestConfig.setDesiredCount(count);
          executionLogCallback.saveExecutionLog(new StringBuilder()
                                                    .append("Resizing Application: {")
                                                    .append(encodeColor(appNameBeingDownsized))
                                                    .append("} to Count: ")
                                                    .append(count)
                                                    .toString());

          performResizing(cfSwapRoutesRequestNG, cfRequestConfig, configVarPath, executionLogCallback);
          executionLogCallback.saveExecutionLog("Resizing Completed", INFO, SUCCESS);
        } else {
          executionLogCallback.saveExecutionLog("Nothing to Downsize", INFO, SUCCESS);
        }
      } catch (Exception e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        log.error("Failed to downsize PCF application: " + appNameBeingDownsized, sanitizedException);
        executionLogCallback.saveExecutionLog(
            "Failed while downsizing old application: " + encodeColor(appNameBeingDownsized), INFO, FAILURE);
        throw e;
      }
    } else {
      executionLogCallback.saveExecutionLog("Nothing to Downsize", INFO, SUCCESS);
    }
  }

  private CfAppAutoscalarRequestData performResizing(CfSwapRoutesRequestNG cfSwapRoutesRequestNG,
      CfRequestConfig cfRequestConfig, String configVarPath, LogCallback executionLogCallback)
      throws PivotalClientApiException {
    // If downsizing, disable auto-scalar
    CfAppAutoscalarRequestData appAutoscalarRequestData = null;
    if (cfSwapRoutesRequestNG.isUseAppAutoScalar()) {
      ApplicationDetail applicationDetail = cfDeploymentManager.getApplicationByName(cfRequestConfig);
      appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                     .applicationGuid(applicationDetail.getId())
                                     .applicationName(applicationDetail.getName())
                                     .cfRequestConfig(cfRequestConfig)
                                     .configPathVar(configVarPath)
                                     .timeoutInMins(cfSwapRoutesRequestNG.getTimeoutIntervalInMin())
                                     .build();

      // Before downsizing, disable autoscalar if its enabled.
      appAutoscalarRequestData.setExpectedEnabled(true);
      cfCommandTaskHelperNG.disableAutoscalar(appAutoscalarRequestData, executionLogCallback);
    }

    // resize app (upsize in swap rollback, downsize in swap state)
    cfDeploymentManager.resizeApplication(cfRequestConfig, executionLogCallback);

    return appAutoscalarRequestData;
  }

  private void performRouteUpdateForStandardBlueGreen(CfRequestConfig cfRequestConfig,
      CfRouteUpdateRequestConfigData data, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress, LogCallback executionLogCallback) throws PivotalClientApiException {
    CfAppSetupTimeDetails newApplicationDetails = data.getNewApplicationDetails();
    List<String> newApps = cfCommandTaskHelperNG.getAppNameBasedOnGuidForBlueGreenDeployment(
        cfRequestConfig, data.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());
    data.setNewApplicationName(isEmpty(newApps) ? data.getNewApplicationName() : newApps.get(0));

    updateRoutesForNewApplication(cfRequestConfig, executionLogCallback, data);
    executionLogCallback.saveExecutionLog("Swapping Routes For New Applications Completed", INFO, SUCCESS);

    executionLogCallback = tasTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, CfCommandUnitConstants.SwapRoutesForExistingApplication, true, commandUnitsProgress);
    updateRoutesForExistingApplication(cfRequestConfig, executionLogCallback, data);
  }

  private void updateRoutesForExistingApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      CfRouteUpdateRequestConfigData data) throws PivotalClientApiException {
    if (isNotEmpty(data.getExistingApplicationNames())) {
      List<String> mapRouteForExistingApp = data.getTempRoutes();
      List<String> unmapRouteForExistingApp = data.getFinalRoutes();
      for (String existingAppName : data.getExistingApplicationNames()) {
        cfCommandTaskHelperNG.mapRouteMaps(
            existingAppName, mapRouteForExistingApp, cfRequestConfig, executionLogCallback);
        cfCommandTaskHelperNG.unmapRouteMaps(
            existingAppName, unmapRouteForExistingApp, cfRequestConfig, executionLogCallback);
        updateEnvVariableForApplication(cfRequestConfig, executionLogCallback, existingAppName, false);
      }
      executionLogCallback.saveExecutionLog("Swapping Routes For Existing Applications Completed", INFO, SUCCESS);
    } else {
      executionLogCallback.saveExecutionLog("Skipping Swapping Routes For Existing Applications", INFO, SUCCESS);
    }
  }

  private void updateRoutesForNewApplication(CfRequestConfig cfRequestConfig, LogCallback executionLogCallback,
      CfRouteUpdateRequestConfigData data) throws PivotalClientApiException {
    List<String> mapRouteForNewApp = data.getFinalRoutes();
    List<String> unmapRouteForNewApp = data.getTempRoutes();
    cfCommandTaskHelperNG.mapRouteMaps(
        data.getNewApplicationName(), mapRouteForNewApp, cfRequestConfig, executionLogCallback);
    cfCommandTaskHelperNG.unmapRouteMaps(
        data.getNewApplicationName(), unmapRouteForNewApp, cfRequestConfig, executionLogCallback);
    // mark new app as ACTIVE if not rollback, STAGE if rollback
    updateEnvVariableForApplication(cfRequestConfig, executionLogCallback, data.getNewApplicationName(), true);
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
