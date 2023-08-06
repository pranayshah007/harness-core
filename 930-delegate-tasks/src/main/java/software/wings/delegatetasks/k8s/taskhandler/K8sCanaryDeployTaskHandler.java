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
import static io.harness.govern.Switch.unhandled;
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

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileData;
import io.harness.delegate.k8s.K8sCanaryBaseHandler;
import io.harness.delegate.k8s.beans.K8sCanaryHandlerConfig;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.istio.IstioTaskHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sCanaryDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sCanaryDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class K8sCanaryDeployTaskHandler extends K8sTaskHandler {
  @Inject private transient ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private transient K8sTaskHelper k8sTaskHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private IstioTaskHelper istioTaskHelper;
  @Inject private transient K8sCanaryBaseHandler k8sCanaryBaseHandler;
  private K8sReleaseHandler releaseHandler;

  private final K8sCanaryHandlerConfig canaryHandlerConfig = new K8sCanaryHandlerConfig();
  private K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
  private boolean canaryWorkloadDeployed;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (!(k8sTaskParameters instanceof K8sCanaryDeployTaskParameters)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sTaskParameters", "Must be instance of K8sCanaryDeployTaskParameters"));
    }

    K8sCanaryDeployTaskParameters k8sCanaryDeployTaskParameters = (K8sCanaryDeployTaskParameters) k8sTaskParameters;

    canaryHandlerConfig.setReleaseName(k8sCanaryDeployTaskParameters.getReleaseName());
    canaryHandlerConfig.setManifestFilesDirectory(
        Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString());
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(k8sCanaryDeployTaskParameters.isUseDeclarativeRollback());
    final long timeoutInMillis = getTimeoutMillisFromMinutes(k8sTaskParameters.getTimeoutIntervalInMin());

    GitFetchFilesConfig gitFetchFilesConfig = null;
    boolean success;
    if (k8sCanaryDeployTaskParameters.isInheritManifests()) {
      success = k8sTaskHelper.restore(k8sCanaryDeployTaskParameters.getKubernetesResources(),
          k8sCanaryDeployTaskParameters.getK8sClusterConfig(), k8sDelegateTaskParams, canaryHandlerConfig,
          k8sRequestHandlerContext, k8sTaskHelper.getExecutionLogCallback(k8sCanaryDeployTaskParameters, Init));
      if (!success) {
        return getFailureResponse();
      }
    } else {
      ExecutionLogCallback executionLogCallback = getLogCallBack(k8sCanaryDeployTaskParameters, FetchFiles);
      executionLogCallback.saveExecutionLog(
          color("\nStarting Kubernetes Canary Deployment", LogColor.White, LogWeight.Bold));

      success = k8sTaskHelper.fetchManifestFilesAndWriteToDirectory(
          k8sCanaryDeployTaskParameters.getK8sDelegateManifestConfig(), canaryHandlerConfig.getManifestFilesDirectory(),
          executionLogCallback, timeoutInMillis);
      if (!success) {
        return getFailureResponse();
      }

      if (k8sCanaryDeployTaskParameters.getK8sDelegateManifestConfig().getGitFileConfig() != null
          && k8sCanaryDeployTaskParameters.getK8sDelegateManifestConfig().isShouldSaveManifest()) {
        gitFetchFilesConfig =
            GitFetchFilesConfig.builder()
                .gitFileConfig(k8sCanaryDeployTaskParameters.getK8sDelegateManifestConfig().getGitFileConfig())
                .build();
      }

      success = init(
          k8sCanaryDeployTaskParameters, k8sDelegateTaskParams, getLogCallBack(k8sCanaryDeployTaskParameters, Init));
      if (!success) {
        return getFailureResponse();
      }

      if (k8sCanaryDeployTaskParameters.isExportManifests()) {
        return K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .k8sTaskResponse(K8sCanaryDeployResponse.builder().resources(canaryHandlerConfig.getResources()).build())
            .build();
      }
    }

    success = prepareForCanary(
        k8sDelegateTaskParams, k8sCanaryDeployTaskParameters, getLogCallBack(k8sCanaryDeployTaskParameters, Prepare));
    if (!success) {
      return getFailureResponse();
    }

    canaryWorkloadDeployed = true;
    success = k8sTaskHelperBase.applyManifests(canaryHandlerConfig.getClient(), canaryHandlerConfig.getResources(),
        k8sDelegateTaskParams, getLogCallBack(k8sCanaryDeployTaskParameters, Apply), true, null);
    if (!success) {
      k8sCanaryBaseHandler.failAndSaveRelease(canaryHandlerConfig);
      return getFailureResponse();
    }

    ExecutionLogCallback executionLogCallback =
        k8sTaskHelper.getExecutionLogCallback(k8sCanaryDeployTaskParameters, WaitForSteadyState);
    KubernetesResource canaryWorkload = canaryHandlerConfig.getCanaryWorkload();
    success = k8sTaskHelperBase.doStatusCheck(
        canaryHandlerConfig.getClient(), canaryWorkload.getResourceId(), k8sDelegateTaskParams, executionLogCallback);

    if (!success) {
      k8sCanaryBaseHandler.failAndSaveRelease(canaryHandlerConfig);
      return getFailureResponse();
    }

    ExecutionLogCallback wrapUpLogCallback = getLogCallBack(k8sCanaryDeployTaskParameters, WrapUp);

    try {
      List<K8sPod> allPods = k8sCanaryBaseHandler.getAllPods(
          canaryHandlerConfig, k8sCanaryDeployTaskParameters.getReleaseName(), timeoutInMillis);
      HelmChartInfo helmChartInfo =
          k8sTaskHelper.getHelmChartDetails(k8sCanaryDeployTaskParameters.getK8sDelegateManifestConfig(),
              canaryHandlerConfig.getManifestFilesDirectory());

      k8sCanaryBaseHandler.wrapUp(canaryHandlerConfig.getClient(), k8sDelegateTaskParams, wrapUpLogCallback);

      k8sTaskHelperBase.saveRelease(k8sCanaryDeployTaskParameters.isUseDeclarativeRollback(), false,
          canaryHandlerConfig.getKubernetesConfig(), canaryHandlerConfig.getCurrentRelease(),
          canaryHandlerConfig.getReleaseHistory(), canaryHandlerConfig.getReleaseName());

      String canaryObjectsNames = canaryWorkload.getResourceId().namespaceKindNameRef();
      if (k8sCanaryDeployTaskParameters.isUseDeclarativeRollback()) {
        canaryObjectsNames = k8sCanaryBaseHandler.appendSecretAndConfigMapNamesToCanaryWorkloads(
            canaryObjectsNames, canaryHandlerConfig.getResources());
      }

      K8sCanaryDeployResponse k8sCanaryDeployResponse =
          K8sCanaryDeployResponse.builder()
              .releaseNumber(canaryHandlerConfig.getCurrentRelease().getReleaseNumber())
              .k8sPodList(allPods)
              .currentInstances(canaryHandlerConfig.getTargetInstances())
              .canaryWorkload(canaryObjectsNames)
              .gitFetchFilesConfig(gitFetchFilesConfig)
              .helmChartInfo(helmChartInfo)
              .build();
      wrapUpLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

      return K8sTaskExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .k8sTaskResponse(k8sCanaryDeployResponse)
          .build();
    } catch (Exception e) {
      wrapUpLogCallback.saveExecutionLog(e.getMessage(), ERROR, FAILURE);
      k8sCanaryBaseHandler.failAndSaveRelease(canaryHandlerConfig);
      throw e;
    }
  }

  private K8sTaskExecutionResponse getFailureResponse() {
    K8sCanaryDeployResponse k8sCanaryDeployResponse = K8sCanaryDeployResponse.builder().build();
    KubernetesResource canaryWorkload = canaryHandlerConfig.getCanaryWorkload();
    if (canaryWorkloadDeployed && canaryWorkload != null && canaryWorkload.getResourceId() != null) {
      String canaryObjectsNames = canaryWorkload.getResourceId().namespaceKindNameRef();
      if (canaryHandlerConfig.isUseDeclarativeRollback()) {
        canaryObjectsNames = k8sCanaryBaseHandler.appendSecretAndConfigMapNamesToCanaryWorkloads(
            canaryObjectsNames, canaryHandlerConfig.getResources());
      }

      k8sCanaryDeployResponse.setCanaryWorkload(canaryObjectsNames);
    }

    return K8sTaskExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .k8sTaskResponse(k8sCanaryDeployResponse)
        .build();
  }

  private ExecutionLogCallback getLogCallBack(K8sCanaryDeployTaskParameters request, String commandUnit) {
    return new ExecutionLogCallback(
        delegateLogService, request.getAccountId(), request.getAppId(), request.getActivityId(), commandUnit);
  }

  @VisibleForTesting
  boolean init(K8sCanaryDeployTaskParameters k8sCanaryDeployTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams,
      ExecutionLogCallback executionLogCallback) throws IOException {
    executionLogCallback.saveExecutionLog("Initializing..\n");

    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(
        k8sCanaryDeployTaskParameters.getK8sClusterConfig(), false);

    Kubectl client = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());

    canaryHandlerConfig.setKubernetesConfig(kubernetesConfig);
    canaryHandlerConfig.setClient(client);
    canaryHandlerConfig.setReleaseName(k8sCanaryDeployTaskParameters.getReleaseName());
    canaryHandlerConfig.setUseDeclarativeRollback(k8sCanaryDeployTaskParameters.isUseDeclarativeRollback());
    try {
      k8sTaskHelperBase.deleteSkippedManifestFiles(
          canaryHandlerConfig.getManifestFilesDirectory(), executionLogCallback);

      List<FileData> manifestFiles = k8sTaskHelper.renderTemplate(k8sDelegateTaskParams,
          k8sCanaryDeployTaskParameters.getK8sDelegateManifestConfig(), canaryHandlerConfig.getManifestFilesDirectory(),
          k8sCanaryDeployTaskParameters.getValuesYamlList(), canaryHandlerConfig.getReleaseName(),
          kubernetesConfig.getNamespace(), executionLogCallback, k8sCanaryDeployTaskParameters);

      List<KubernetesResource> resources = k8sTaskHelperBase.readManifests(manifestFiles, executionLogCallback);
      k8sRequestHandlerContext.setResources(resources);
      k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, kubernetesConfig.getNamespace());

      istioTaskHelper.updateDestinationRuleManifestFilesWithSubsets(resources,
          asList(HarnessLabelValues.trackCanary, HarnessLabelValues.trackStable), kubernetesConfig,
          executionLogCallback);
      istioTaskHelper.updateVirtualServiceManifestFilesWithRoutesForCanary(
          resources, kubernetesConfig, executionLogCallback);
      canaryHandlerConfig.setResources(resources);
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }

    executionLogCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));

    executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(canaryHandlerConfig.getResources()));

    if (k8sCanaryDeployTaskParameters.isSkipDryRun()) {
      executionLogCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    return k8sTaskHelperBase.dryRunManifests(
        client, canaryHandlerConfig.getResources(), k8sDelegateTaskParams, executionLogCallback);
  }

  @VisibleForTesting
  boolean prepareForCanary(K8sDelegateTaskParams k8sDelegateTaskParams,
      K8sCanaryDeployTaskParameters k8sCanaryDeployTaskParameters, ExecutionLogCallback executionLogCallback) {
    try {
      IK8sReleaseHistory releaseHistory = releaseHandler.getReleaseHistory(
          canaryHandlerConfig.getKubernetesConfig(), k8sCanaryDeployTaskParameters.getReleaseName());
      canaryHandlerConfig.setReleaseHistory(releaseHistory);

      int currentReleaseNumber = releaseHistory.getAndIncrementLastReleaseNumber();
      if (canaryHandlerConfig.isUseDeclarativeRollback() && isEmpty(releaseHistory)) {
        currentReleaseNumber = k8sTaskHelperBase.getNextReleaseNumberFromOldReleaseHistory(
            canaryHandlerConfig.getKubernetesConfig(), k8sCanaryDeployTaskParameters.getReleaseName());
      }
      canaryHandlerConfig.setCurrentReleaseNumber(currentReleaseNumber);

      boolean success =
          k8sCanaryBaseHandler.prepareForCanary(canaryHandlerConfig, k8sRequestHandlerContext, k8sDelegateTaskParams,
              k8sCanaryDeployTaskParameters.getSkipVersioningForAllK8sObjects(), executionLogCallback, false);
      if (!success) {
        return false;
      }

      Integer currentInstances =
          k8sCanaryBaseHandler.getCurrentInstances(canaryHandlerConfig, k8sDelegateTaskParams, executionLogCallback);
      Integer targetInstances = currentInstances;

      switch (k8sCanaryDeployTaskParameters.getInstanceUnitType()) {
        case COUNT:
          targetInstances = k8sCanaryDeployTaskParameters.getInstances();
          break;

        case PERCENTAGE:
          Integer maxInstances;
          if (k8sCanaryDeployTaskParameters.getMaxInstances().isPresent()) {
            maxInstances = k8sCanaryDeployTaskParameters.getMaxInstances().get();
          } else {
            maxInstances = currentInstances;
          }
          targetInstances = k8sTaskHelperBase.getTargetInstancesForCanary(
              k8sCanaryDeployTaskParameters.getInstances(), maxInstances, executionLogCallback);
          break;

        default:
          unhandled(k8sCanaryDeployTaskParameters.getInstanceUnitType());
      }

      k8sCanaryBaseHandler.updateTargetInstances(
          canaryHandlerConfig, k8sRequestHandlerContext, targetInstances, executionLogCallback);

      IK8sRelease currentRelease = releaseHandler.createRelease(
          canaryHandlerConfig.getReleaseName(), canaryHandlerConfig.getCurrentReleaseNumber());
      currentRelease.setReleaseData(canaryHandlerConfig.getResources(), false);

      canaryHandlerConfig.setCurrentRelease(currentRelease);
      if (!k8sCanaryDeployTaskParameters.isUseDeclarativeRollback()) {
        ((K8SLegacyReleaseHistory) canaryHandlerConfig.getReleaseHistory())
            .getReleaseHistory()
            .addReleaseToReleaseHistory((K8sLegacyRelease) currentRelease);
      }
    } catch (Exception e) {
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR, CommandExecutionStatus.FAILURE);
      log.error("Exception:", e);
      return false;
    }
    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return true;
  }

  @VisibleForTesting
  K8sCanaryHandlerConfig getCanaryHandlerConfig() {
    return canaryHandlerConfig;
  }

  @VisibleForTesting
  K8sRequestHandlerContext getK8sRequestHandlerContext() {
    return k8sRequestHandlerContext;
  }
}
