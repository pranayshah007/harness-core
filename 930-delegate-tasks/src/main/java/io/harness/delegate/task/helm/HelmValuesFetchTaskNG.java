/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.clienttools.ClientTool;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;

@Slf4j
@OwnedBy(CDP)
public class HelmValuesFetchTaskNG extends AbstractDelegateRunnableTask {
  @Inject private HelmTaskHelperBase helmTaskHelperBase;

  public HelmValuesFetchTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("This method is deprecated. Use run(TaskParameters) instead.");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) parameters;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    log.info(format("Running HelmValuesFetchTaskNG for account %s", helmValuesFetchRequest.getAccountId()));

    LogCallback logCallback = getLogCallback(commandUnitsProgress, helmValuesFetchRequest.isOpenNewLogStream());

    printHelmBinaryPathAndVersion(
        helmValuesFetchRequest.getHelmChartManifestDelegateConfig().getHelmVersion(), logCallback);

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        helmValuesFetchRequest.getHelmChartManifestDelegateConfig();
    try {
      helmTaskHelperBase.decryptEncryptedDetails(helmChartManifestDelegateConfig);
      List<HelmFetchFileConfig> helmFetchFileConfigList = helmValuesFetchRequest.getHelmFetchFileConfigList();
      Map<String, HelmFetchFileResult> helmChartValuesFileMapContents = helmTaskHelperBase.fetchValuesYamlFromChart(
          helmChartManifestDelegateConfig, helmValuesFetchRequest.getTimeout(), logCallback, helmFetchFileConfigList);

      logCallback.saveExecutionLog("\nFetching helm values completed successfully.", INFO);
      if (helmValuesFetchRequest.isCloseLogStream()) {
        logCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);
      }
      return HelmValuesFetchResponse.builder()
          .commandExecutionStatus(SUCCESS)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .helmChartValuesFileMapContent(helmChartValuesFileMapContents)
          .build();
    } catch (Exception e) {
      String exceptionMsg = e.getMessage() == null ? ExceptionUtils.getMessage(e) : e.getMessage();
      String msg = "HelmValuesFetchTaskNG execution failed with exception " + exceptionMsg;
      log.error(msg, e);
      logCallback.saveExecutionLog(msg, INFO, FAILURE);
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), e);
    }
  }

  public LogCallback getLogCallback(CommandUnitsProgress commandUnitsProgress, boolean openLogStream) {
    return new NGDelegateLogCallback(
        getLogStreamingTaskClient(), K8sCommandUnitConstants.FetchFiles, openLogStream, commandUnitsProgress);
  }

  public void printHelmBinaryPathAndVersion(HelmVersion helmVersion, LogCallback logCallback) {
    String helmPath;
    if (helmVersion == null) {
      helmVersion = HelmVersion.V2; // Default to V2
    }
    switch (helmVersion) {
      // catch blocks will be deleted when we end up with just one helm version in the immutable delegate
      case V3:
        try {
          helmPath = InstallUtils.getPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3);
        } catch (IllegalArgumentException e) {
          log.warn("Helm 3.1.2 not installed Version 3.12.0 will be used");
          helmPath = InstallUtils.getPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3_12);
        }
        break;
      case V380:
        try {
          helmPath = InstallUtils.getPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3_8);
        } catch (IllegalArgumentException e) {
          log.warn("Helm 3.8.0 not installed Version 3.12.0 will be used");
          helmPath = InstallUtils.getPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3_12);
        }
        break;
      default:
        helmPath = InstallUtils.getPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V2);
    }
    logCallback.saveExecutionLog("Path of helm binary picked up: " + helmPath);

    try {
      String version = new ProcessExecutor()
                           .directory(null)
                           .commandSplit(helmPath + " version")
                           .readOutput(true)
                           .execute()
                           .outputUTF8();
      logCallback.saveExecutionLog("Helm binary version is: " + version);
    } catch (Exception e) {
      log.warn("Exception occurred while getting helm version");
    }
  }
}
