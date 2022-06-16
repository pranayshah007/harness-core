/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.pcf.ResizeStrategy.DOWNSIZE_OLD_FIRST;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.Downsize;
import static io.harness.pcf.CfCommandUnitConstants.Upsize;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.PcfUtils.encodeColor;
import static io.harness.pcf.model.PcfConstants.PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;

@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfDeployCommandTaskHandler extends PcfCommandTaskHandler {
  /**
   * This method is responsible for upsizing new application instances and downsizing previous application instances.
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
    if (!(cfCommandRequest instanceof CfCommandDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("CfCommandRequest", "Must be instance of CfCommandDeployRequest"));
    }
    LogCallback executionLogCallback = logStreamingTaskClient.obtainLogCallback(cfCommandRequest.getCommandName());
    CfCommandDeployRequest cfCommandDeployRequest = (CfCommandDeployRequest) cfCommandRequest;
    List<CfServiceData> cfServiceDataUpdated = new ArrayList<>();
    CfDeployCommandResponse cfDeployCommandResponse =
        CfDeployCommandResponse.builder().pcfInstanceElements(new ArrayList<>()).build();

    File workingDirectory = null;
    boolean exceptionOccured = false;
    Exception exception = null;
    CfAppAutoscalarRequestData pcfAppAutoscalarRequestData = CfAppAutoscalarRequestData.builder().build();
    try {
      boolean downSize = DOWNSIZE_OLD_FIRST == cfCommandDeployRequest.getResizeStrategy();
      String commandUnitType = downSize ? Downsize : Upsize;
      executionLogCallback = logStreamingTaskClient.obtainLogCallback(commandUnitType);
      executionLogCallback.saveExecutionLog(color("\n---------- Starting PCF Resize Command\n", White, Bold));

      CfInternalConfig pcfConfig = cfCommandRequest.getPcfConfig();
      secretDecryptionService.decrypt(pcfConfig, encryptedDataDetails, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfConfig, encryptedDataDetails);

      CfRequestConfig cfRequestConfig = getCfRequestConfig(cfCommandDeployRequest, pcfConfig);

      // This will be CF_HOME for any cli related operations
      workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();
      if (workingDirectory == null) {
        throw new PivotalClientApiException("Failed to create working CF directory");
      }
      cfRequestConfig.setCfHomeDirPath(workingDirectory.getAbsolutePath());

      // Init AppAutoscalarRequestData If Needed
      if (cfCommandDeployRequest.isUseAppAutoscalar()) {
        pcfAppAutoscalarRequestData.setCfRequestConfig(cfRequestConfig);
        pcfAppAutoscalarRequestData.setConfigPathVar(workingDirectory.getAbsolutePath());
        pcfAppAutoscalarRequestData.setTimeoutInMins(cfCommandDeployRequest.getTimeoutIntervalInMin());
      }

      ApplicationDetail details = pcfCommandTaskBaseHelper.getNewlyCreatedApplication(
          cfRequestConfig, cfCommandDeployRequest, pcfDeploymentManager);
      // No of instances to be added to newly created application in this deploy stage
      Integer stepIncrease = cfCommandDeployRequest.getUpdateCount() - details.getInstances();
      Integer stepDecrease = cfCommandDeployRequest.getDownSizeCount();

      // downsize previous apps with non zero instances by same count new app was upsized
      List<CfInternalInstanceElement> pcfInstanceElementsForVerification = new ArrayList<>();
      if (DOWNSIZE_OLD_FIRST == cfCommandDeployRequest.getResizeStrategy()) {
        pcfCommandTaskBaseHelper.downsizePreviousReleases(cfCommandDeployRequest, cfRequestConfig, executionLogCallback,
            cfServiceDataUpdated, stepDecrease, pcfInstanceElementsForVerification, pcfAppAutoscalarRequestData);
        unmapRoutesIfAppDownsizedToZero(cfCommandDeployRequest, cfRequestConfig, executionLogCallback);
        executionLogCallback.saveExecutionLog("Downsize Application Successfully Completed", INFO, SUCCESS);

        executionLogCallback = logStreamingTaskClient.obtainLogCallback(Upsize);
        performUpsize(executionLogCallback, cfCommandDeployRequest, cfServiceDataUpdated, cfRequestConfig, details,
            pcfInstanceElementsForVerification, pcfAppAutoscalarRequestData);
        executionLogCallback.saveExecutionLog("Upsize Application Successfully Completed", INFO, SUCCESS);
      } else {
        performUpsize(executionLogCallback, cfCommandDeployRequest, cfServiceDataUpdated, cfRequestConfig, details,
            pcfInstanceElementsForVerification, pcfAppAutoscalarRequestData);
        executionLogCallback.saveExecutionLog("Upsize Application Successfully Completed", INFO, SUCCESS);

        executionLogCallback = logStreamingTaskClient.obtainLogCallback(Downsize);
        pcfCommandTaskBaseHelper.downsizePreviousReleases(cfCommandDeployRequest, cfRequestConfig, executionLogCallback,
            cfServiceDataUpdated, stepDecrease, pcfInstanceElementsForVerification, pcfAppAutoscalarRequestData);
        unmapRoutesIfAppDownsizedToZero(cfCommandDeployRequest, cfRequestConfig, executionLogCallback);
        executionLogCallback.saveExecutionLog("Downsize Application Successfully Completed", INFO, SUCCESS);
      }

      // This data will be used by verification phase for analysis
      executionLogCallback = logStreamingTaskClient.obtainLogCallback(Wrapup);
      generatePcfInstancesElementsForExistingApp(
          pcfInstanceElementsForVerification, cfRequestConfig, cfCommandDeployRequest, executionLogCallback);

      // generate response to be sent back to Manager
      cfDeployCommandResponse.setCommandExecutionStatus(SUCCESS);
      cfDeployCommandResponse.setOutput(StringUtils.EMPTY);
      cfDeployCommandResponse.setInstanceDataUpdated(cfServiceDataUpdated);
      cfDeployCommandResponse.getPcfInstanceElements().addAll(pcfInstanceElementsForVerification);

    } catch (Exception e) {
      exceptionOccured = true;
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      logException(executionLogCallback, cfCommandDeployRequest, sanitizedException);
    } finally {
      try {
        if (workingDirectory != null) {
          executionLogCallback.saveExecutionLog("#--------- Removing any temporary files created");
          FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
        }
      } catch (IOException e) {
        log.warn("Failed to delete Temp Directory created for CF CLI login", e);
      }
    }

    if (exceptionOccured) {
      cfDeployCommandResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      cfDeployCommandResponse.setOutput(ExceptionUtils.getMessage(exception));
      cfDeployCommandResponse.setInstanceDataUpdated(cfServiceDataUpdated);
    } else {
      executionLogCallback.saveExecutionLog("#------- PCF Resize State Successfully Completed", INFO, SUCCESS);
    }

    return CfCommandExecutionResponse.builder()
        .commandExecutionStatus(cfDeployCommandResponse.getCommandExecutionStatus())
        .errorMessage(cfDeployCommandResponse.getOutput())
        .pcfCommandResponse(cfDeployCommandResponse)
        .build();
  }

  private CfRequestConfig getCfRequestConfig(
      CfCommandDeployRequest cfCommandDeployRequest, CfInternalConfig pcfConfig) {
    return CfRequestConfig.builder()
        .userName(String.valueOf(pcfConfig.getUsername()))
        .password(String.valueOf(pcfConfig.getPassword()))
        .endpointUrl(pcfConfig.getEndpointUrl())
        .orgName(cfCommandDeployRequest.getOrganization())
        .spaceName(cfCommandDeployRequest.getSpace())
        .timeOutIntervalInMins(cfCommandDeployRequest.getTimeoutIntervalInMin())
        .useCFCLI(cfCommandDeployRequest.isUseCfCLI())
        .cfCliPath(pcfCommandTaskBaseHelper.getCfCliPathOnDelegate(
            cfCommandDeployRequest.isUseCfCLI(), cfCommandDeployRequest.getCfCliVersion()))
        .cfCliVersion(cfCommandDeployRequest.getCfCliVersion())
        .limitPcfThreads(cfCommandDeployRequest.isLimitPcfThreads())
        .ignorePcfConnectionContextCache(cfCommandDeployRequest.isIgnorePcfConnectionContextCache())
        .build();
  }

  private void logException(
      LogCallback executionLogCallback, CfCommandDeployRequest cfCommandDeployRequest, Exception exception) {
    log.error(PIVOTAL_CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing PCF Deploy task [{}]", cfCommandDeployRequest,
        exception);

    executionLogCallback.saveExecutionLog("\n\n--------- PCF Resize failed to complete successfully", ERROR, FAILURE);
    Misc.logAllMessages(exception, executionLogCallback);
  }

  @VisibleForTesting
  void generatePcfInstancesElementsForExistingApp(List<CfInternalInstanceElement> pcfInstanceElementsForVerification,
      CfRequestConfig cfRequestConfig, CfCommandDeployRequest cfCommandDeployRequest,
      LogCallback executionLogCallback) {
    CfAppSetupTimeDetails downsizeAppDetail = cfCommandDeployRequest.getDownsizeAppDetail();
    if (downsizeAppDetail == null || isBlank(downsizeAppDetail.getApplicationName())) {
      return;
    }

    try {
      cfRequestConfig.setApplicationName(downsizeAppDetail.getApplicationName());
      ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
      applicationDetail.getInstanceDetails().forEach(instanceDetail
          -> pcfInstanceElementsForVerification.add(CfInternalInstanceElement.builder()
                                                        .applicationId(applicationDetail.getId())
                                                        .displayName(applicationDetail.getName())
                                                        .instanceIndex(instanceDetail.getIndex())
                                                        .isUpsize(false)
                                                        .build()));
    } catch (Exception e) {
      executionLogCallback.saveExecutionLog(
          new StringBuilder(128)
              .append("# Failed to fetch InstanceDetails for existing Application: ")
              .append(encodeColor(downsizeAppDetail.getApplicationName()))
              .append(", Verification may be able to use older instances to compare data")
              .toString());
    }
  }

  private void performUpsize(LogCallback executionLogCallback, CfCommandDeployRequest cfCommandDeployRequest,
      List<CfServiceData> cfServiceDataUpdated, CfRequestConfig cfRequestConfig, ApplicationDetail details,
      List<CfInternalInstanceElement> pcfInstanceElementsForVerification,
      CfAppAutoscalarRequestData appAutoscalarRequestData) throws PivotalClientApiException, IOException {
    pcfCommandTaskBaseHelper.upsizeNewApplication(executionLogCallback, cfCommandDeployRequest, cfServiceDataUpdated,
        cfRequestConfig, details, pcfInstanceElementsForVerification);
    configureAutoscalarIfNeeded(cfCommandDeployRequest, details, appAutoscalarRequestData, executionLogCallback);
  }

  @VisibleForTesting
  void configureAutoscalarIfNeeded(CfCommandDeployRequest cfCommandDeployRequest, ApplicationDetail applicationDetail,
      CfAppAutoscalarRequestData appAutoscalarRequestData, LogCallback executionLogCallback)
      throws PivotalClientApiException, IOException {
    if (cfCommandDeployRequest.isUseAppAutoscalar() && cfCommandDeployRequest.getPcfManifestsPackage() != null
        && isNotEmpty(cfCommandDeployRequest.getPcfManifestsPackage().getAutoscalarManifestYml())
        && cfCommandDeployRequest.getMaxCount() <= cfCommandDeployRequest.getUpdateCount()) {
      // This is autoscalar file inside workingDirectory
      String filePath =
          appAutoscalarRequestData.getConfigPathVar() + "/autoscalar_" + System.currentTimeMillis() + ".yml";
      pcfCommandTaskBaseHelper.createYamlFileLocally(
          filePath, cfCommandDeployRequest.getPcfManifestsPackage().getAutoscalarManifestYml());

      // upload autoscalar config
      appAutoscalarRequestData.setApplicationName(applicationDetail.getName());
      appAutoscalarRequestData.setApplicationGuid(applicationDetail.getId());
      appAutoscalarRequestData.setTimeoutInMins(cfCommandDeployRequest.getTimeoutIntervalInMin());
      appAutoscalarRequestData.setAutoscalarFilePath(filePath);
      pcfDeploymentManager.performConfigureAutoscalar(appAutoscalarRequestData, executionLogCallback);
    }
  }

  @VisibleForTesting
  void unmapRoutesIfAppDownsizedToZero(CfCommandDeployRequest cfCommandDeployRequest, CfRequestConfig cfRequestConfig,
      LogCallback executionLogCallback) throws PivotalClientApiException {
    if (cfCommandDeployRequest.isStandardBlueGreen() || cfCommandDeployRequest.getDownsizeAppDetail() == null
        || isBlank(cfCommandDeployRequest.getDownsizeAppDetail().getApplicationName())) {
      return;
    }

    cfRequestConfig.setApplicationName(cfCommandDeployRequest.getDownsizeAppDetail().getApplicationName());
    ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);

    if (applicationDetail.getInstances() == 0) {
      pcfCommandTaskBaseHelper.unmapExistingRouteMaps(applicationDetail, cfRequestConfig, executionLogCallback);
    }
  }
}
