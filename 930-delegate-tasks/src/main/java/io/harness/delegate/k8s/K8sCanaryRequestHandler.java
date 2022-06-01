/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.K8sCommandUnitConstants.Apply;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Prepare;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.k8s.K8sCommandUnitConstants.WrapUp;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.beans.K8sCanaryHandlerConfig;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sCanaryDeployRequest;
import io.harness.delegate.task.k8s.K8sCanaryDeployResponse;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.data.K8sCanaryDataException;
import io.harness.delegate.task.k8s.data.K8sCanaryDataException.K8sCanaryDataExceptionBuilder;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public class K8sCanaryRequestHandler extends K8sRequestHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sCanaryBaseHandler k8sCanaryBaseHandler;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  private final K8sCanaryHandlerConfig k8sCanaryHandlerConfig = new K8sCanaryHandlerConfig();
  private boolean canaryWorkloadDeployed;
  private boolean saveReleaseHistory;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sCanaryDeployRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sDeployRequest", "Must be instance of K8sCanaryDeployRequestK8sCanaryDeployRequest"));
    }

    K8sCanaryDeployRequest k8sCanaryDeployRequest = (K8sCanaryDeployRequest) k8sDeployRequest;
    k8sCanaryHandlerConfig.setReleaseName(k8sCanaryDeployRequest.getReleaseName());
    k8sCanaryHandlerConfig.setManifestFilesDirectory(
        Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString());
    final long timeoutInMillis = getTimeoutMillisFromMinutes(k8sCanaryDeployRequest.getTimeoutIntervalInMin());

    LogCallback executionLogCallback = k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, FetchFiles,
        k8sCanaryDeployRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);
    executionLogCallback.saveExecutionLog(
        color("\nStarting Kubernetes Canary Deployment", LogColor.White, LogWeight.Bold));
    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sCanaryDeployRequest.getManifestDelegateConfig(),
        k8sCanaryHandlerConfig.getManifestFilesDirectory(), executionLogCallback, timeoutInMillis,
        k8sCanaryDeployRequest.getAccountId());

    init(k8sCanaryDeployRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress));

    prepareForCanary(k8sCanaryDeployRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prepare, true, commandUnitsProgress));

    k8sTaskHelperBase.applyManifests(k8sCanaryHandlerConfig.getClient(), k8sCanaryHandlerConfig.getResources(),
        k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Apply, true, commandUnitsProgress), true, true);

    // At this point we're sure that manifest has been applied successfully and canary workload is deployed
    this.canaryWorkloadDeployed = true;
    this.saveReleaseHistory = true;

    LogCallback steadyStateLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress);
    KubernetesResource canaryWorkload = k8sCanaryHandlerConfig.getCanaryWorkload();
    k8sTaskHelperBase.doStatusCheck(k8sCanaryHandlerConfig.getClient(), canaryWorkload.getResourceId(),
        k8sDelegateTaskParams, steadyStateLogCallback, true);

    LogCallback wrapUpLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WrapUp, true, commandUnitsProgress);

    List<K8sPod> allPods = k8sCanaryBaseHandler.getAllPods(
        k8sCanaryHandlerConfig, k8sCanaryDeployRequest.getReleaseName(), timeoutInMillis);
    k8sCanaryBaseHandler.wrapUp(k8sCanaryHandlerConfig.getClient(), k8sDelegateTaskParams, wrapUpLogCallback);

    ReleaseHistory releaseHistory = k8sCanaryHandlerConfig.getReleaseHistory();
    k8sTaskHelperBase.saveReleaseHistoryInConfigMap(k8sCanaryHandlerConfig.getKubernetesConfig(),
        k8sCanaryDeployRequest.getReleaseName(), releaseHistory.getAsYaml());
    wrapUpLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

    return K8sDeployResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .k8sNGTaskResponse(K8sCanaryDeployResponse.builder()
                               .canaryWorkload(canaryWorkload.getResourceId().namespaceKindNameRef())
                               .k8sPodList(allPods)
                               .releaseNumber(k8sCanaryHandlerConfig.getCurrentRelease().getNumber())
                               .currentInstances(k8sCanaryHandlerConfig.getTargetInstances())
                               .canaryWorkloadDeployed(this.canaryWorkloadDeployed)
                               .build())
        .build();
  }

  @Override
  public boolean isErrorFrameworkSupported() {
    return true;
  }

  @Override
  protected void handleTaskFailure(K8sDeployRequest request, Exception exception) throws Exception {
    if (saveReleaseHistory) {
      K8sCanaryDeployRequest k8sCanaryDeployRequest = (K8sCanaryDeployRequest) request;
      k8sCanaryBaseHandler.failAndSaveKubernetesRelease(
          k8sCanaryHandlerConfig, k8sCanaryDeployRequest.getReleaseName());
    }

    K8sCanaryDataExceptionBuilder k8sCanaryDataBuilder =
        K8sCanaryDataException.dataBuilder().canaryWorkloadDeployed(canaryWorkloadDeployed).cause(exception);
    KubernetesResource canaryWorkload = k8sCanaryHandlerConfig.getCanaryWorkload();
    if (canaryWorkload != null && canaryWorkload.getResourceId() != null) {
      k8sCanaryDataBuilder.canaryWorkload(canaryWorkload.getResourceId().namespaceKindNameRef());
    }

    throw k8sCanaryDataBuilder.build();
  }

  @VisibleForTesting
  void init(K8sCanaryDeployRequest request, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback)
      throws Exception {
    logCallback.saveExecutionLog("Initializing..\n");
    logCallback.saveExecutionLog(color(String.format("Release Name: [%s]", request.getReleaseName()), Yellow, Bold));
    k8sCanaryHandlerConfig.setKubernetesConfig(
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(request.getK8sInfraDelegateConfig()));
    k8sCanaryHandlerConfig.setClient(
        Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath()));

    String releaseHistoryData = k8sTaskHelperBase.getReleaseHistoryDataFromConfigMap(
        k8sCanaryHandlerConfig.getKubernetesConfig(), request.getReleaseName());

    ReleaseHistory releaseHistory = (StringUtils.isEmpty(releaseHistoryData))
        ? ReleaseHistory.createNew()
        : ReleaseHistory.createFromData(releaseHistoryData);
    k8sCanaryHandlerConfig.setReleaseHistory(releaseHistory);

    k8sTaskHelperBase.deleteSkippedManifestFiles(k8sCanaryHandlerConfig.getManifestFilesDirectory(), logCallback);

    List<String> manifestOverrideFiles = getManifestOverrideFlies(request);

    List<FileData> manifestFiles = k8sTaskHelperBase.renderTemplate(k8sDelegateTaskParams,
        request.getManifestDelegateConfig(), k8sCanaryHandlerConfig.getManifestFilesDirectory(), manifestOverrideFiles,
        request.getReleaseName(), k8sCanaryHandlerConfig.getKubernetesConfig().getNamespace(), logCallback,
        request.getTimeoutIntervalInMin());

    List<KubernetesResource> resources =
        k8sTaskHelperBase.readManifests(manifestFiles, logCallback, isErrorFrameworkSupported());
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(
        resources, k8sCanaryHandlerConfig.getKubernetesConfig().getNamespace());

    k8sCanaryBaseHandler.updateDestinationRuleManifestFilesWithSubsets(
        resources, k8sCanaryHandlerConfig.getKubernetesConfig(), logCallback);
    k8sCanaryBaseHandler.updateVirtualServiceManifestFilesWithRoutes(
        resources, k8sCanaryHandlerConfig.getKubernetesConfig(), logCallback);
    k8sCanaryHandlerConfig.setResources(resources);

    logCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));
    logCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(k8sCanaryHandlerConfig.getResources()));

    if (request.isSkipDryRun()) {
      logCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    k8sTaskHelperBase.dryRunManifests(k8sCanaryHandlerConfig.getClient(), k8sCanaryHandlerConfig.getResources(),
        k8sDelegateTaskParams, logCallback, true, request.isUseNewKubectlVersion());
  }

  @VisibleForTesting
  void prepareForCanary(K8sCanaryDeployRequest k8sCanaryDeployRequest, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback logCallback) throws Exception {
    k8sCanaryBaseHandler.prepareForCanary(k8sCanaryHandlerConfig, k8sDelegateTaskParams,
        k8sCanaryDeployRequest.isSkipResourceVersioning(), logCallback, true,
        k8sCanaryDeployRequest.isCleanUpIncompleteCanaryDeployRelease());
    Integer currentInstances =
        k8sCanaryBaseHandler.getCurrentInstances(k8sCanaryHandlerConfig, k8sDelegateTaskParams, logCallback);
    Integer targetInstances = currentInstances;

    switch (k8sCanaryDeployRequest.getInstanceUnitType()) {
      case COUNT:
        targetInstances = k8sCanaryDeployRequest.getInstances();
        break;

      case PERCENTAGE:
        Integer maxInstances;
        if (k8sCanaryDeployRequest.getMaxInstances() != null) {
          maxInstances = k8sCanaryDeployRequest.getMaxInstances();
        } else {
          maxInstances = currentInstances;
        }
        targetInstances = k8sTaskHelperBase.getTargetInstancesForCanary(
            k8sCanaryDeployRequest.getInstances(), maxInstances, logCallback);
        break;

      default:
        unhandled(k8sCanaryDeployRequest.getInstanceUnitType());
    }

    k8sCanaryBaseHandler.updateTargetInstances(k8sCanaryHandlerConfig, targetInstances, logCallback);

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }

  @VisibleForTesting
  K8sCanaryHandlerConfig getK8sCanaryHandlerConfig() {
    return k8sCanaryHandlerConfig;
  }
}
