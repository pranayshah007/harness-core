/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.clienttools.ClientTool;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmValuesFetchTaskParameters;
import software.wings.helpers.ext.helm.response.HelmValuesFetchTaskResponse;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.zeroturnaround.exec.ProcessExecutor;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmValuesFetchTask extends AbstractDelegateRunnableTask {
  @Inject private HelmTaskHelper helmTaskHelper;
  @Inject private DelegateLogService delegateLogService;

  public HelmValuesFetchTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public HelmValuesFetchTaskResponse run(TaskParameters parameters) {
    HelmValuesFetchTaskParameters taskParams = (HelmValuesFetchTaskParameters) parameters;
    log.info(
        format("Running HelmValuesFetchTask for account %s app %s", taskParams.getAccountId(), taskParams.getAppId()));

    ExecutionLogCallback executionLogCallback = getExecutionLogCallback(taskParams, FetchFiles);

    printHelmBinaryPathAndVersion(taskParams.getHelmChartConfigTaskParams().getHelmVersion(), executionLogCallback);

    executionLogCallback.saveExecutionLog(color("\nStarting fetching Helm values", LogColor.White, LogWeight.Bold));

    try {
      executionLogCallback.saveExecutionLog(color("\nFetching values.yaml from helm chart for Service", White, Bold));

      HelmChartConfigParams helmChartConfigParams = taskParams.getHelmChartConfigTaskParams();

      Map<String, List<String>> mapK8sValuesLocationToContent = helmTaskHelper.getValuesYamlFromChart(
          helmChartConfigParams, taskParams.getTimeoutInMillis(), taskParams.getHelmCommandFlag(),
          ((HelmValuesFetchTaskParameters) parameters).getMapK8sValuesLocationToFilePaths());

      helmTaskHelper.printHelmChartInfoInExecutionLogs(helmChartConfigParams, executionLogCallback);

      if (EmptyPredicate.isEmpty(mapK8sValuesLocationToContent)
          || mapK8sValuesLocationToContent.values().stream().allMatch(Objects::isNull)) {
        executionLogCallback.saveExecutionLog("No values.yaml found", WARN, SUCCESS);
      } else {
        executionLogCallback.saveExecutionLog("\nSuccessfully fetched values.yaml files", INFO, SUCCESS);
      }

      executionLogCallback.saveExecutionLog(
          color("\nFetching helm values completed successfully.", LogColor.White, LogWeight.Bold));

      return HelmValuesFetchTaskResponse.builder()
          .commandExecutionStatus(SUCCESS)
          .mapK8sValuesLocationToContent(mapK8sValuesLocationToContent)
          .build();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("HelmValuesFetchTask execution failed with exception ", sanitizedException);
      executionLogCallback.saveExecutionLog(sanitizedException.getMessage(), ERROR, CommandExecutionStatus.FAILURE);

      return HelmValuesFetchTaskResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage("Execution failed with Exception: " + sanitizedException.getMessage())
          .build();
    }
  }

  private ExecutionLogCallback getExecutionLogCallback(HelmValuesFetchTaskParameters taskParams, String commandUnit) {
    return new ExecutionLogCallback(
        delegateLogService, taskParams.getAccountId(), taskParams.getAppId(), taskParams.getActivityId(), commandUnit);
  }

  @Override
  public HelmValuesFetchTaskResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  public void printHelmBinaryPathAndVersion(HelmVersion helmVersion, ExecutionLogCallback logCallback) {
    String helmPath;
    if (helmVersion == null) {
      helmVersion = HelmVersion.V2; // Default to V2
    }
    switch (helmVersion) {
      case V3:
        try {
          helmPath = InstallUtils.getPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3);
        } catch (IllegalArgumentException e) {
          log.warn("Helm 3.1.2 not installed Version 3.8.0 will be used");
          helmPath = InstallUtils.getPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3_8);
        }
        break;
      case V380:
        helmPath = InstallUtils.getPath(ClientTool.HELM, io.harness.delegate.clienttools.HelmVersion.V3_8);
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
