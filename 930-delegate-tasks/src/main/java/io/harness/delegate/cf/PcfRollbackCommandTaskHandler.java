/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.constructActiveAppName;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.constructInActiveAppName;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.getVersionChangeMessage;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.pcf.CfCommandUnitConstants.Downsize;
import static io.harness.pcf.CfCommandUnitConstants.Upsize;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandRollbackRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.Misc;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfRollbackCommandTaskHandler extends PcfCommandTaskHandler {
  @Override
  public CfCommandExecutionResponse executeTaskInternal(CfCommandRequest cfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ILogStreamingTaskClient logStreamingTaskClient,
      boolean isInstanceSync) {
    if (!(cfCommandRequest instanceof CfCommandRollbackRequest)) {
      throw new InvalidArgumentsException(Pair.of("CfCommandRequest", "Must be instance of CfCommandRollbackRequest"));
    }
    CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();
    LogCallback executionLogCallback = logStreamingTaskClient.obtainLogCallback(Upsize);
    executionLogCallback.saveExecutionLog(color("--------- Starting Rollback deployment", White, Bold));
    List<CfServiceData> cfServiceDataUpdated = new ArrayList<>();
    CfDeployCommandResponse cfDeployCommandResponse =
        CfDeployCommandResponse.builder().pcfInstanceElements(new ArrayList<>()).build();

    CfCommandRollbackRequest commandRollbackRequest = (CfCommandRollbackRequest) cfCommandRequest;

    File workingDirectory = null;
    Exception exception = null;
    try {
      // This will be CF_HOME for any cli related operations
      workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();

      CfInternalConfig pcfConfig = cfCommandRequest.getPcfConfig();
      secretDecryptionService.decrypt(pcfConfig, encryptedDataDetails, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfConfig, encryptedDataDetails);
      if (CollectionUtils.isEmpty(commandRollbackRequest.getInstanceData())) {
        commandRollbackRequest.setInstanceData(new ArrayList<>());
      }

      CfRequestConfig cfRequestConfig =
          buildCfRequestConfig(cfCommandRequest, commandRollbackRequest, workingDirectory, pcfConfig);

      // Will be used if app autoscalar is configured
      CfAppAutoscalarRequestData autoscalarRequestData =
          CfAppAutoscalarRequestData.builder()
              .cfRequestConfig(cfRequestConfig)
              .configPathVar(workingDirectory.getAbsolutePath())
              .timeoutInMins(commandRollbackRequest.getTimeoutIntervalInMin() != null
                      ? commandRollbackRequest.getTimeoutIntervalInMin()
                      : 10)
              .build();

      // get Upsize Instance data
      List<CfServiceData> upsizeList =
          commandRollbackRequest.getInstanceData()
              .stream()
              .filter(cfServiceData -> cfServiceData.getDesiredCount() > cfServiceData.getPreviousCount())
              .collect(toList());

      // get Downsize Instance data
      List<CfServiceData> downSizeList =
          commandRollbackRequest.getInstanceData()
              .stream()
              .filter(pcfServiceData -> pcfServiceData.getDesiredCount() < pcfServiceData.getPreviousCount())
              .collect(toList());

      List<CfInternalInstanceElement> pcfInstanceElements = new ArrayList<>();
      // During rollback, always upsize old ones
      pcfCommandTaskBaseHelper.upsizeListOfInstances(executionLogCallback, pcfDeploymentManager, cfServiceDataUpdated,
          cfRequestConfig, upsizeList, pcfInstanceElements);
      restoreRoutesForOldApplication(commandRollbackRequest, cfRequestConfig, executionLogCallback);
      // Enable autoscalar for older app, if it was disabled during deploy
      enableAutoscalarIfNeeded(upsizeList, autoscalarRequestData, executionLogCallback);
      executionLogCallback.saveExecutionLog("#---------- Upsize Application Successfully Completed", INFO, SUCCESS);

      executionLogCallback = logStreamingTaskClient.obtainLogCallback(Downsize);
      pcfCommandTaskBaseHelper.downSizeListOfInstances(executionLogCallback, cfServiceDataUpdated, cfRequestConfig,
          updateNewAppName(cfRequestConfig, commandRollbackRequest, downSizeList), commandRollbackRequest,
          autoscalarRequestData);
      unmapRoutesFromNewAppAfterDownsize(executionLogCallback, commandRollbackRequest, cfRequestConfig);

      cfDeployCommandResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
      cfDeployCommandResponse.setOutput(StringUtils.EMPTY);
      cfDeployCommandResponse.setInstanceDataUpdated(cfServiceDataUpdated);
      cfDeployCommandResponse.getPcfInstanceElements().addAll(pcfInstanceElements);

      if (commandRollbackRequest.isStandardBlueGreenWorkflow()) {
        deleteNewApp(cfRequestConfig, commandRollbackRequest, executionLogCallback);
      } else {
        // for basic & canary
        if (isRollbackCompleted(commandRollbackRequest, cfRequestConfig)) {
          deleteNewApp(cfRequestConfig, commandRollbackRequest, executionLogCallback);
          updateValues = renameApps(cfRequestConfig, commandRollbackRequest, executionLogCallback);
        }
      }
      cfDeployCommandResponse.setUpdatedValues(updateValues);
      executionLogCallback.saveExecutionLog("\n\n--------- PCF Rollback completed successfully", INFO, SUCCESS);

    } catch (Exception e) {
      exception = e;
      logExceptionMessage(executionLogCallback, commandRollbackRequest, exception);
    } finally {
      executionLogCallback = logStreamingTaskClient.obtainLogCallback(Wrapup);
      executionLogCallback.saveExecutionLog("#------- Deleting Temporary Files");
      if (workingDirectory != null) {
        try {
          FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
          executionLogCallback.saveExecutionLog("Temporary Files Successfully deleted", INFO, SUCCESS);
        } catch (IOException e) {
          log.warn("Failed to delete temp cf home folder", e);
        }
      }
    }

    if (exception != null) {
      cfDeployCommandResponse.setCommandExecutionStatus(FAILURE);
      cfDeployCommandResponse.setInstanceDataUpdated(cfServiceDataUpdated);
      cfDeployCommandResponse.setOutput(ExceptionUtils.getMessage(exception));
    }

    return CfCommandExecutionResponse.builder()
        .commandExecutionStatus(cfDeployCommandResponse.getCommandExecutionStatus())
        .errorMessage(cfDeployCommandResponse.getOutput())
        .pcfCommandResponse(cfDeployCommandResponse)
        .build();
  }

  private CfRequestConfig buildCfRequestConfig(CfCommandRequest cfCommandRequest,
      CfCommandRollbackRequest commandRollbackRequest, File workingDirectory, CfInternalConfig pcfConfig) {
    return CfRequestConfig.builder()
        .userName(String.valueOf(pcfConfig.getUsername()))
        .password(String.valueOf(pcfConfig.getPassword()))
        .endpointUrl(pcfConfig.getEndpointUrl())
        .orgName(commandRollbackRequest.getOrganization())
        .spaceName(commandRollbackRequest.getSpace())
        .timeOutIntervalInMins(commandRollbackRequest.getTimeoutIntervalInMin() == null
                ? 10
                : commandRollbackRequest.getTimeoutIntervalInMin())
        .cfHomeDirPath(workingDirectory.getAbsolutePath())
        .useCFCLI(commandRollbackRequest.isUseCfCLI())
        .cfCliPath(pcfCommandTaskBaseHelper.getCfCliPathOnDelegate(
            cfCommandRequest.isUseCfCLI(), cfCommandRequest.getCfCliVersion()))
        .cfCliVersion(cfCommandRequest.getCfCliVersion())
        .limitPcfThreads(commandRollbackRequest.isLimitPcfThreads())
        .ignorePcfConnectionContextCache(commandRollbackRequest.isIgnorePcfConnectionContextCache())
        .build();
  }

  private void deleteNewApp(CfRequestConfig cfRequestConfig, CfCommandRollbackRequest commandRollbackRequest,
      LogCallback logCallback) throws PivotalClientApiException {
    // app downsized - to be deleted
    String cfAppNamePrefix = commandRollbackRequest.getCfAppNamePrefix();
    CfAppSetupTimeDetails newApp = commandRollbackRequest.getNewApplicationDetails();
    String newAppGuid = newApp.getApplicationGuid();
    String newAppName = newApp.getApplicationName();
    List<String> newApps = pcfCommandTaskBaseHelper.getAppNameBasedOnGuid(cfRequestConfig, cfAppNamePrefix, newAppGuid);

    if (newApps.isEmpty()) {
      logCallback.saveExecutionLog(
          String.format("No new app found to delete with id - [%s] and name - [%s]", newAppGuid, newAppName));
    } else if (newApps.size() == 1) {
      String newAppToDelete = newApps.get(0);
      cfRequestConfig.setApplicationName(newAppToDelete);
      logCallback.saveExecutionLog("Deleting application " + encodeColor(newAppToDelete));
      pcfDeploymentManager.deleteApplication(cfRequestConfig);
    } else {
      String newAppToDelete = newApps.get(0);
      String message = String.format(
          "Found [%d] applications with with id - [%s] and name - [%s]. Skipping new app deletion. Kindly delete the invalid app manually",
          newApps.size(), newAppGuid, newAppToDelete);
      logCallback.saveExecutionLog(message, WARN);
    }
  }

  private List<CfServiceData> updateNewAppName(CfRequestConfig cfRequestConfig,
      CfCommandRollbackRequest commandRollbackRequest, List<CfServiceData> downSizeList)
      throws PivotalClientApiException {
    String cfAppNamePrefix = commandRollbackRequest.getCfAppNamePrefix();

    for (CfServiceData data : downSizeList) {
      List<String> apps =
          pcfCommandTaskBaseHelper.getAppNameBasedOnGuid(cfRequestConfig, cfAppNamePrefix, data.getId());
      data.setName(isEmpty(apps) ? data.getName() : apps.get(0));
    }
    return downSizeList;
  }

  private boolean isRollbackCompleted(CfCommandRollbackRequest commandRollbackRequest, CfRequestConfig cfRequestConfig)
      throws PivotalClientApiException {
    // app downsized - to be deleted
    CfAppSetupTimeDetails newApp = commandRollbackRequest.getNewApplicationDetails();
    boolean rollbackCompleted =
        instanceCountMatches(newApp.getApplicationName(), newApp.getInitialInstanceCount(), cfRequestConfig);
    // app upsized - to be renamed
    List<CfAppSetupTimeDetails> prevActiveApps = commandRollbackRequest.getAppsToBeDownSized();
    if (!EmptyPredicate.isEmpty(prevActiveApps)) {
      CfAppSetupTimeDetails prevActiveApp = prevActiveApps.get(0);
      rollbackCompleted = rollbackCompleted
          && instanceCountMatches(
              prevActiveApp.getApplicationName(), prevActiveApp.getInitialInstanceCount(), cfRequestConfig);
    }

    return rollbackCompleted;
  }

  private boolean instanceCountMatches(String applicationName, Integer expectedInstanceCount,
      CfRequestConfig cfRequestConfig) throws PivotalClientApiException {
    cfRequestConfig.setApplicationName(applicationName);
    ApplicationDetail application = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
    return null != application && application.getInstances().equals(expectedInstanceCount);
  }

  private CfInBuiltVariablesUpdateValues renameApps(CfRequestConfig cfRequestConfig,
      CfCommandRollbackRequest commandRollbackRequest, LogCallback logCallback) throws PivotalClientApiException {
    CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();

    if (commandRollbackRequest.isNonVersioning()) {
      logCallback.saveExecutionLog("\n# Reverting app names");
      // app upsized - to be renamed
      List<CfAppSetupTimeDetails> prevActiveApps = commandRollbackRequest.getAppsToBeDownSized();
      // previous inactive app - to be marked inactive again
      CfAppSetupTimeDetails prevInactive = commandRollbackRequest.getExistingInActiveApplicationDetails();

      if (!EmptyPredicate.isEmpty(prevActiveApps)) {
        CfAppSetupTimeDetails prevActiveApp = prevActiveApps.get(0);
        String newName = constructActiveAppName(commandRollbackRequest.getCfAppNamePrefix(), -1, true);
        pcfDeploymentManager.renameApplication(new CfRenameRequest(cfRequestConfig, prevActiveApp.getApplicationGuid(),
                                                   prevActiveApp.getApplicationName(), newName),
            logCallback);
        updateValues.setOldAppGuid(prevActiveApp.getApplicationGuid());
        updateValues.setOldAppName(newName);
        updateValues.setActiveAppName(newName);
      }

      if (null != prevInactive && isNotEmpty(prevInactive.getApplicationName())) {
        String newName = constructInActiveAppName(commandRollbackRequest.getCfAppNamePrefix(), -1, true);
        pcfDeploymentManager.renameApplication(new CfRenameRequest(cfRequestConfig, prevInactive.getApplicationGuid(),
                                                   prevInactive.getApplicationName(), newName),
            logCallback);
        updateValues.setInActiveAppName(newName);
      }

      logCallback.saveExecutionLog("# App names reverted successfully");
    }

    if (commandRollbackRequest.isVersioningChanged()) {
      logCallback.saveExecutionLog(getVersionChangeMessage(!commandRollbackRequest.isNonVersioning()));

      List<ApplicationSummary> releases =
          pcfDeploymentManager.getPreviousReleases(cfRequestConfig, commandRollbackRequest.getCfAppNamePrefix());
      ApplicationSummary activeApplication = pcfCommandTaskBaseHelper.findActiveApplication(
          logCallback, commandRollbackRequest.isStandardBlueGreenWorkflow(), cfRequestConfig, releases);
      ApplicationSummary inactiveApplication = pcfCommandTaskBaseHelper.getMostRecentInactiveApplication(logCallback,
          commandRollbackRequest.isStandardBlueGreenWorkflow(), activeApplication, releases, cfRequestConfig);
      pcfCommandTaskBaseHelper.resetState(releases, activeApplication, inactiveApplication,
          commandRollbackRequest.getCfAppNamePrefix(), cfRequestConfig, !commandRollbackRequest.isNonVersioning(), null,
          commandRollbackRequest.getActiveAppRevision(), logCallback, updateValues);

      logCallback.saveExecutionLog(getVersionChangeMessage(!commandRollbackRequest.isNonVersioning()) + " completed");
    }
    return updateValues;
  }

  private void logExceptionMessage(
      LogCallback executionLogCallback, CfCommandRollbackRequest commandRollbackRequest, Exception exception) {
    log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing PCF Rollback task [{}]",
        commandRollbackRequest, exception);
    executionLogCallback.saveExecutionLog("\n\n--------- PCF Rollback failed to complete successfully", ERROR, FAILURE);
    Misc.logAllMessages(exception, executionLogCallback);
  }

  @VisibleForTesting
  void enableAutoscalarIfNeeded(List<CfServiceData> upsizeList, CfAppAutoscalarRequestData autoscalarRequestData,
      LogCallback logCallback) throws PivotalClientApiException {
    for (CfServiceData cfServiceData : upsizeList) {
      if (!cfServiceData.isDisableAutoscalarPerformed()) {
        continue;
      }

      autoscalarRequestData.setApplicationName(cfServiceData.getName());
      autoscalarRequestData.setApplicationGuid(cfServiceData.getId());
      autoscalarRequestData.setExpectedEnabled(false);
      pcfDeploymentManager.changeAutoscalarState(autoscalarRequestData, logCallback, true);
    }
  }

  /**
   * This is for non BG deployment.
   * Older app will be mapped to routes it was originally mapped to.
   * In deploy state, once older app is downsized to 0, we remove routeMaps,
   * this step will restore them.
   */
  @VisibleForTesting
  void restoreRoutesForOldApplication(CfCommandRollbackRequest commandRollbackRequest, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    if (commandRollbackRequest.isStandardBlueGreenWorkflow()
        || EmptyPredicate.isEmpty(commandRollbackRequest.getAppsToBeDownSized())) {
      return;
    }

    CfAppSetupTimeDetails cfAppSetupTimeDetails = commandRollbackRequest.getAppsToBeDownSized().get(0);

    if (cfAppSetupTimeDetails != null) {
      cfRequestConfig.setApplicationName(cfAppSetupTimeDetails.getApplicationName());
      ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);

      if (EmptyPredicate.isEmpty(cfAppSetupTimeDetails.getUrls())) {
        return;
      }

      if (EmptyPredicate.isEmpty(applicationDetail.getUrls())
          || !cfAppSetupTimeDetails.getUrls().containsAll(applicationDetail.getUrls())) {
        pcfCommandTaskBaseHelper.mapRouteMaps(cfAppSetupTimeDetails.getApplicationName(),
            cfAppSetupTimeDetails.getUrls(), cfRequestConfig, executionLogCallback);
      }
    }
  }

  @VisibleForTesting
  void unmapRoutesFromNewAppAfterDownsize(LogCallback executionLogCallback,
      CfCommandRollbackRequest commandRollbackRequest, CfRequestConfig cfRequestConfig)
      throws PivotalClientApiException {
    if (commandRollbackRequest.isStandardBlueGreenWorkflow()
        || commandRollbackRequest.getNewApplicationDetails() == null
        || isBlank(commandRollbackRequest.getNewApplicationDetails().getApplicationName())) {
      return;
    }

    cfRequestConfig.setApplicationName(commandRollbackRequest.getNewApplicationDetails().getApplicationName());
    ApplicationDetail appDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);

    if (appDetail.getInstances() == 0) {
      pcfCommandTaskBaseHelper.unmapExistingRouteMaps(appDetail, cfRequestConfig, executionLogCallback);
    }
  }
}
