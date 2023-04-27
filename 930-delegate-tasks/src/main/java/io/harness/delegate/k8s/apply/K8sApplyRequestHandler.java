/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.k8s.K8sCommandUnitConstants.Apply;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Prepare;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.k8s.K8sCommandUnitConstants.WrapUp;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.beans.K8sApplyHandlerConfig;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.K8sCliCommandType;
import io.harness.k8s.K8sCommandFlagsUtils;
import io.harness.k8s.KubernetesReleaseDetails;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@NoArgsConstructor
@Slf4j
public class K8sApplyRequestHandler extends K8sRequestHandler {
  @Inject private K8sApplyBaseHandler k8sApplyBaseHandler;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  private final K8sApplyHandlerConfig k8sApplyHandlerConfig = new K8sApplyHandlerConfig();

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sApplyRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sRollingDeployRequest"));
    }

    K8sApplyRequest k8sApplyRequest = (K8sApplyRequest) k8sDeployRequest;
    k8sApplyHandlerConfig.setReleaseName(k8sApplyRequest.getReleaseName());
    k8sApplyHandlerConfig.setManifestFilesDirectory(
        Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString());
    long timeoutInMillis = getTimeoutMillisFromMinutes(k8sDeployRequest.getTimeoutIntervalInMin());

    LogCallback executionLogCallback = k8sTaskHelperBase.getLogCallback(
        logStreamingTaskClient, FetchFiles, k8sApplyRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);
    executionLogCallback.saveExecutionLog(color("\nStarting Kubernetes Apply", LogColor.White, LogWeight.Bold));

    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sApplyRequest.getManifestDelegateConfig(),
        k8sApplyHandlerConfig.getManifestFilesDirectory(), executionLogCallback, timeoutInMillis,
        k8sApplyRequest.getAccountId());

    init(k8sApplyRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress));

    k8sApplyBaseHandler.prepare(
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prepare, true, commandUnitsProgress),
        k8sApplyRequest.isSkipSteadyStateCheck(), k8sApplyHandlerConfig, isErrorFrameworkSupported());
    Map<String, String> k8sCommandFlag = k8sApplyRequest.getK8sCommandFlags();
    String commandFlags = K8sCommandFlagsUtils.getK8sCommandFlags(K8sCliCommandType.Apply.name(), k8sCommandFlag);
    k8sTaskHelperBase.applyManifests(k8sApplyHandlerConfig.getClient(), k8sApplyHandlerConfig.getResources(),
        k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Apply, true, commandUnitsProgress), true,
        isErrorFrameworkSupported(), commandFlags);
    final LogCallback waitForSteadyStateLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress);

    if (!k8sApplyRequest.isSkipSteadyStateCheck() && isNotEmpty(k8sApplyHandlerConfig.getWorkloads())) {
      List<KubernetesResourceId> kubernetesResourceIds = k8sApplyHandlerConfig.getWorkloads()
                                                             .stream()
                                                             .map(KubernetesResource::getResourceId)
                                                             .collect(Collectors.toList());

      K8sSteadyStateDTO k8sSteadyStateDTO =
          K8sSteadyStateDTO.builder()
              .request(k8sDeployRequest)
              .resourceIds(kubernetesResourceIds)
              .executionLogCallback(waitForSteadyStateLogCallback)
              .k8sDelegateTaskParams(k8sDelegateTaskParams)
              .namespace(k8sApplyRequest.getK8sInfraDelegateConfig().getNamespace())
              .denoteOverallSuccess(k8sApplyHandlerConfig.getCustomWorkloads().isEmpty())
              .isErrorFrameworkEnabled(true)
              .build();

      K8sClient k8sClient = k8sTaskHelperBase.getKubernetesClient(k8sApplyRequest.isUseK8sApiForSteadyStateCheck());
      k8sClient.performSteadyStateCheck(k8sSteadyStateDTO);
    }

    k8sApplyBaseHandler.steadyStateCheck(k8sApplyRequest.isSkipSteadyStateCheck(),
        k8sApplyRequest.getK8sInfraDelegateConfig().getNamespace(), k8sDelegateTaskParams, timeoutInMillis,
        waitForSteadyStateLogCallback, k8sApplyHandlerConfig, isErrorFrameworkSupported(), true);

    k8sApplyBaseHandler.wrapUp(k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WrapUp, true, commandUnitsProgress),
        k8sApplyHandlerConfig.getClient());

    return K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
  }

  @Override
  public boolean isErrorFrameworkSupported() {
    return true;
  }

  private void init(K8sApplyRequest request, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback)
      throws Exception {
    logCallback.saveExecutionLog("Initializing..\n");
    logCallback.saveExecutionLog(color(String.format("Release Name: [%s]", request.getReleaseName()), Yellow, Bold));

    k8sApplyHandlerConfig.setKubernetesConfig(containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        request.getK8sInfraDelegateConfig(), k8sDelegateTaskParams.getWorkingDirectory(), logCallback));

    k8sApplyHandlerConfig.setClient(
        Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath()));

    List<String> applyFilePaths =
        request.getFilePaths().stream().map(String::trim).filter(StringUtils::isNotBlank).collect(Collectors.toList());

    if (isEmpty(applyFilePaths)) {
      logCallback.saveExecutionLog(color("\nNo file specified in the state", Yellow, Bold));
      logCallback.saveExecutionLog("\nFailed.", INFO, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.APPLY_NO_FILEPATH_SPECIFIED,

          KubernetesExceptionExplanation.APPLY_NO_FILEPATH_SPECIFIED,
          new KubernetesTaskException(KubernetesExceptionMessages.APPLY_NO_FILEPATH_SPECIFIED));
    }

    logCallback.saveExecutionLog(color("Found following files to be applied in the state", White, Bold));
    StringBuilder sb = new StringBuilder(1024);
    applyFilePaths.forEach(each -> sb.append(color(format("- %s", each), Gray)).append(System.lineSeparator()));
    logCallback.saveExecutionLog(sb.toString());

    // To be defined a better logic to detect release number
    KubernetesReleaseDetails releaseDetails = KubernetesReleaseDetails.builder().releaseNumber(1).build();

    List<String> manifestOverrideFiles = getManifestOverrideFlies(request, releaseDetails.toContextMap());

    k8sApplyHandlerConfig.setResources(
        k8sTaskHelperBase.getResourcesFromManifests(k8sDelegateTaskParams, request.getManifestDelegateConfig(),
            k8sApplyHandlerConfig.getManifestFilesDirectory(), applyFilePaths, manifestOverrideFiles,
            k8sApplyHandlerConfig.getReleaseName(), k8sApplyHandlerConfig.getKubernetesConfig().getNamespace(),
            logCallback, request.getTimeoutIntervalInMin(), request.isSkipRendering()));

    logCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));
    logCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(k8sApplyHandlerConfig.getResources()));

    if (request.isSkipDryRun()) {
      logCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    k8sTaskHelperBase.dryRunManifests(k8sApplyHandlerConfig.getClient(), k8sApplyHandlerConfig.getResources(),
        k8sDelegateTaskParams, logCallback, isErrorFrameworkSupported(), request.isUseNewKubectlVersion());
  }

  @VisibleForTesting
  K8sApplyHandlerConfig getK8sApplyHandlerConfig() {
    return k8sApplyHandlerConfig;
  }
}
