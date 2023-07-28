/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pcf.CfCommandUnitConstants.Downsize;
import static io.harness.pcf.CfCommandUnitConstants.Upsize;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfDeployCommandResult;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.Misc;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.cloudfoundry.operations.applications.ApplicationDetail;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@NoArgsConstructor
@Singleton
@Slf4j
@OwnedBy(CDP)
public class CfDeployCommandTaskHandlerNG extends CfCommandTaskNGHandler {
  @Inject TasTaskHelperBase tasTaskHelperBase;
  @Inject TasNgConfigMapper tasNgConfigMapper;
  @Inject CfDeploymentManager cfDeploymentManager;
  @Inject protected CfCommandTaskHelperNG cfCommandTaskHelperNG;
  @Override
  protected CfCommandResponseNG executeTaskInternal(CfCommandRequestNG cfCommandRequestNG,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(cfCommandRequestNG instanceof CfDeployCommandRequestNG)) {
      throw new InvalidArgumentsException(
          Pair.of("cfCommandRequestNG", "Must be instance of CfDeployCommandRequestNG"));
    }
    CfDeployCommandRequestNG cfDeployCommandRequestNG = (CfDeployCommandRequestNG) cfCommandRequestNG;
    String commandUnitType =
        (TasResizeStrategyType.DOWNSCALE_OLD_FIRST == cfDeployCommandRequestNG.getResizeStrategy()) ? Downsize : Upsize;
    LogCallback executionLogCallback =
        tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, commandUnitType, true, commandUnitsProgress);
    List<CfServiceData> cfServiceDataUpdated = new ArrayList<>();
    CfDeployCommandResponseNG cfDeployCommandResponseNG = CfDeployCommandResponseNG.builder().build();
    CfDeployCommandResult cfDeployCommandResult = CfDeployCommandResult.builder().build();
    cfDeployCommandResult.setStandardBG(cfDeployCommandRequestNG.isStandardBlueGreen());

    File workingDirectory = null;
    boolean noExceptionOccured = true;
    Exception exception;
    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = CfAppAutoscalarRequestData.builder().build();
    try {
      executionLogCallback.saveExecutionLog(color("\n---------- Starting CF Resize Command\n", White, Bold));

      TasInfraConfig tasInfraConfig = cfCommandRequestNG.getTasInfraConfig();
      CloudFoundryConfig cfConfig = tasNgConfigMapper.mapTasConfigWithDecryption(
          tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());
      CfRequestConfig cfRequestConfig = getCfRequestConfig(cfDeployCommandRequestNG, cfConfig);
      // This will be CF_HOME for any cli related operations
      workingDirectory = cfCommandTaskHelperNG.generateWorkingDirectoryForDeployment();
      if (workingDirectory == null) {
        throw new PivotalClientApiException("Failed to create working CF directory");
      }
      cfRequestConfig.setCfHomeDirPath(workingDirectory.getAbsolutePath());

      cfAppAutoscalarRequestData.setCfRequestConfig(cfRequestConfig);
      cfAppAutoscalarRequestData.setConfigPathVar(workingDirectory.getAbsolutePath());
      cfAppAutoscalarRequestData.setTimeoutInMins(cfDeployCommandRequestNG.getTimeoutIntervalInMin());

      ApplicationDetail details = cfCommandTaskHelperNG.getNewlyCreatedApplication(
          cfRequestConfig, cfDeployCommandRequestNG, cfDeploymentManager);
      Integer stepDecrease = cfDeployCommandRequestNG.getDownSizeCount();

      // downsize previous apps with non zero instances by same count new app was upsized
      List<CfInternalInstanceElement> oldAppInstances = new ArrayList<>();
      List<CfInternalInstanceElement> newAppInstances = new ArrayList<>();
      if (TasResizeStrategyType.DOWNSCALE_OLD_FIRST == cfDeployCommandRequestNG.getResizeStrategy()) {
        cfCommandTaskHelperNG.downsizePreviousReleases(cfDeployCommandRequestNG, cfRequestConfig, executionLogCallback,
            cfServiceDataUpdated, stepDecrease, oldAppInstances, cfAppAutoscalarRequestData);
        executionLogCallback.saveExecutionLog("Downsize Application Successfully Completed", INFO, SUCCESS);
        executionLogCallback =
            tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Upsize, true, commandUnitsProgress);
        cfCommandTaskHelperNG.upsizeNewApplication(executionLogCallback, cfDeployCommandRequestNG, cfServiceDataUpdated,
            cfRequestConfig, details, newAppInstances, cfAppAutoscalarRequestData);
        executionLogCallback.saveExecutionLog("Upsize Application Successfully Completed", INFO, SUCCESS);
      } else {
        cfCommandTaskHelperNG.upsizeNewApplication(executionLogCallback, cfDeployCommandRequestNG, cfServiceDataUpdated,
            cfRequestConfig, details, newAppInstances, cfAppAutoscalarRequestData);
        executionLogCallback.saveExecutionLog("Upsize Application Successfully Completed", INFO, SUCCESS);
        executionLogCallback =
            tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Downsize, true, commandUnitsProgress);
        cfCommandTaskHelperNG.downsizePreviousReleases(cfDeployCommandRequestNG, cfRequestConfig, executionLogCallback,
            cfServiceDataUpdated, stepDecrease, oldAppInstances, cfAppAutoscalarRequestData);
        executionLogCallback.saveExecutionLog("Downsize Application Successfully Completed", INFO, SUCCESS);
      }

      // This data will be used by verification phase for analysis
      executionLogCallback =
          tasTaskHelperBase.getLogCallback(iLogStreamingTaskClient, Wrapup, true, commandUnitsProgress);

      // generate response to be sent back to Manager
      cfDeployCommandResponseNG.setCommandExecutionStatus(SUCCESS);
      cfDeployCommandResult.setOldAppInstances(oldAppInstances);
      cfDeployCommandResult.setNewAppInstances(newAppInstances);
      cfDeployCommandResult.setInstanceDataUpdated(cfServiceDataUpdated);

    } catch (Exception e) {
      noExceptionOccured = false;
      exception = ExceptionMessageSanitizer.sanitizeException(e);
      logException(executionLogCallback, cfDeployCommandRequestNG, exception);
      cfDeployCommandResponseNG.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      cfDeployCommandResponseNG.setErrorMessage(ExceptionUtils.getMessage(exception));
      cfDeployCommandResult.setInstanceDataUpdated(cfServiceDataUpdated);
    } finally {
      try {
        if (workingDirectory != null) {
          executionLogCallback.saveExecutionLog("#--------- Removing any temporary files created");
          FileIo.deleteDirectoryAndItsContentIfExists(workingDirectory.getAbsolutePath());
        }
      } catch (IOException e) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
        log.warn("Failed to delete Temp Directory created for CF CLI login", sanitizedException);
      }
    }
    if (noExceptionOccured) {
      executionLogCallback.saveExecutionLog("#------- CF Resize State Successfully Completed", INFO, SUCCESS);
    }
    cfDeployCommandResponseNG.setCfDeployCommandResult(cfDeployCommandResult);
    return cfDeployCommandResponseNG;
  }

  private CfRequestConfig getCfRequestConfig(
      CfDeployCommandRequestNG cfDeployCommandRequestNG, CloudFoundryConfig cfConfig) {
    return CfRequestConfig.builder()
        .userName(String.valueOf(cfConfig.getUserName()))
        .password(String.valueOf(cfConfig.getPassword()))
        .endpointUrl(cfConfig.getEndpointUrl())
        .orgName(cfDeployCommandRequestNG.getTasInfraConfig().getOrganization())
        .spaceName(cfDeployCommandRequestNG.getTasInfraConfig().getSpace())
        .timeOutIntervalInMins(cfDeployCommandRequestNG.getTimeoutIntervalInMin())
        .cfCliPath(cfCommandTaskHelperNG.getCfCliPathOnDelegate(true, cfDeployCommandRequestNG.getCfCliVersion()))
        .cfCliVersion(cfDeployCommandRequestNG.getCfCliVersion())
        .useCFCLI(cfDeployCommandRequestNG.isUseCfCLI())
        .build();
  }

  private void logException(
      LogCallback executionLogCallback, CfDeployCommandRequestNG cfDeployCommandRequestNG, Exception exception) {
    log.error(
        CLOUD_FOUNDRY_LOG_PREFIX + "Exception in processing CF Deploy task [{}]", cfDeployCommandRequestNG, exception);

    Misc.logAllMessages(exception, executionLogCallback);
    executionLogCallback.saveExecutionLog("\n\n--------- CF Resize failed to complete successfully", ERROR, FAILURE);
  }
}
