/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.k8s.K8sCommandUnitConstants.Apply;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Prepare;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.k8s.K8sCommandUnitConstants.WrapUp;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.k8s.K8sApplyBaseHandler;
import io.harness.delegate.k8s.beans.K8sApplyHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sApplyResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sApplyTaskHandler extends K8sTaskHandler {
  @Inject private K8sTaskHelper k8sTaskHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private K8sApplyBaseHandler k8sApplyBaseHandler;

  private final K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (!(k8sTaskParameters instanceof K8sApplyTaskParameters)) {
      throw new InvalidArgumentsException(Pair.of("k8sTaskParameters", "Must be instance of K8sApplyTaskParameters"));
    }

    K8sApplyTaskParameters k8sApplyTaskParameters = (K8sApplyTaskParameters) k8sTaskParameters;
    k8sApplyHandlerConfig.setReleaseName(k8sApplyTaskParameters.getReleaseName());
    k8sApplyHandlerConfig.setManifestFilesDirectory(
        Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString());
    final long timeoutInMillis = getTimeoutMillisFromMinutes(k8sTaskParameters.getTimeoutIntervalInMin());

    boolean success;
    if (k8sApplyTaskParameters.isInheritManifests()) {
      success = k8sTaskHelper.restore(k8sApplyTaskParameters.getKubernetesResources(),
          k8sApplyTaskParameters.getK8sClusterConfig(), k8sDelegateTaskParams, k8sApplyHandlerConfig, null,
          k8sTaskHelper.getExecutionLogCallback(k8sApplyTaskParameters, Init));
      if (!success) {
        return getFailureResponse();
      }
    } else {
      ExecutionLogCallback executionLogCallback =
          k8sTaskHelper.getExecutionLogCallback(k8sApplyTaskParameters, FetchFiles);
      executionLogCallback.saveExecutionLog(color("\nStarting Kubernetes Apply", LogColor.White, LogWeight.Bold));

      success =
          k8sTaskHelper.fetchManifestFilesAndWriteToDirectory(k8sApplyTaskParameters.getK8sDelegateManifestConfig(),
              k8sApplyHandlerConfig.getManifestFilesDirectory(), executionLogCallback, timeoutInMillis);
      if (!success) {
        return getFailureResponse();
      }

      success = init(k8sApplyTaskParameters, k8sDelegateTaskParams,
          k8sTaskHelper.getExecutionLogCallback(k8sApplyTaskParameters, Init));
      if (!success) {
        return getFailureResponse();
      }

      if (k8sApplyTaskParameters.isExportManifests()) {
        return K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .k8sTaskResponse(K8sApplyResponse.builder().resources(k8sApplyHandlerConfig.getResources()).build())
            .build();
      }
    }

    success = k8sApplyBaseHandler.prepare(k8sTaskHelper.getExecutionLogCallback(k8sApplyTaskParameters, Prepare),
        k8sApplyTaskParameters.isSkipSteadyStateCheck(), k8sApplyHandlerConfig);
    if (!success) {
      return getFailureResponse();
    }

    success = k8sTaskHelperBase.applyManifests(k8sApplyHandlerConfig.getClient(), k8sApplyHandlerConfig.getResources(),
        k8sDelegateTaskParams, k8sTaskHelper.getExecutionLogCallback(k8sApplyTaskParameters, Apply), true, null);
    if (!success) {
      return getFailureResponse();
    }

    success = k8sApplyBaseHandler.steadyStateCheck(k8sApplyTaskParameters.isSkipSteadyStateCheck(),
        k8sTaskParameters.getK8sClusterConfig().getNamespace(), k8sDelegateTaskParams, timeoutInMillis,
        k8sTaskHelper.getExecutionLogCallback(k8sApplyTaskParameters, WaitForSteadyState), k8sApplyHandlerConfig);
    if (!success) {
      return getFailureResponse();
    }

    k8sApplyBaseHandler.wrapUp(k8sDelegateTaskParams,
        k8sTaskHelper.getExecutionLogCallback(k8sApplyTaskParameters, WrapUp), k8sApplyHandlerConfig.getClient());

    return K8sTaskExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .k8sTaskResponse(K8sApplyResponse.builder().build())
        .build();
  }

  @VisibleForTesting
  boolean init(K8sApplyTaskParameters k8sApplyTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams,
      ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Initializing..\n");

    k8sApplyHandlerConfig.setKubernetesConfig(
        containerDeploymentDelegateHelper.getKubernetesConfig(k8sApplyTaskParameters.getK8sClusterConfig(), false));

    k8sApplyHandlerConfig.setClient(KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory()));

    try {
      List<String> applyFilePaths = Arrays.stream(k8sApplyTaskParameters.getFilePaths().split(","))
                                        .map(String::trim)
                                        .filter(StringUtils::isNotBlank)
                                        .collect(Collectors.toList());

      if (isEmpty(applyFilePaths)) {
        executionLogCallback.saveExecutionLog(color("\nNo file specified in the state", Yellow, Bold));
        executionLogCallback.saveExecutionLog("\nFailed.", INFO, CommandExecutionStatus.FAILURE);
        return false;
      }

      executionLogCallback.saveExecutionLog(color("Found following files to be applied in the state", White, Bold));
      StringBuilder sb = new StringBuilder(1024);
      applyFilePaths.forEach(each -> sb.append(color(format("- %s", each), Gray)).append(System.lineSeparator()));
      executionLogCallback.saveExecutionLog(sb.toString());

      k8sApplyHandlerConfig.setResources(k8sTaskHelper.getResourcesFromManifests(k8sDelegateTaskParams,
          k8sApplyTaskParameters.getK8sDelegateManifestConfig(), k8sApplyHandlerConfig.getManifestFilesDirectory(),
          applyFilePaths, k8sApplyTaskParameters.getValuesYamlList(), k8sApplyHandlerConfig.getReleaseName(),
          k8sApplyHandlerConfig.getKubernetesConfig().getNamespace(), executionLogCallback, k8sApplyTaskParameters,
          k8sApplyTaskParameters.isSkipRendering()));

      executionLogCallback.saveExecutionLog(
          color(String.format(
                    "%nManifests %s:%n", k8sApplyTaskParameters.isSkipRendering() ? "" : "[Post template rendering] "),
              White, Bold));
      executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(k8sApplyHandlerConfig.getResources()));

      if (k8sApplyTaskParameters.isSkipDryRun()) {
        executionLogCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
        executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        return true;
      }

      return k8sTaskHelperBase.dryRunManifests(k8sApplyHandlerConfig.getClient(), k8sApplyHandlerConfig.getResources(),
          k8sDelegateTaskParams, executionLogCallback);
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }
  }

  private K8sTaskExecutionResponse getFailureResponse() {
    K8sApplyResponse k8sApplyResponse = K8sApplyResponse.builder().build();
    return K8sTaskExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .k8sTaskResponse(k8sApplyResponse)
        .build();
  }
}
