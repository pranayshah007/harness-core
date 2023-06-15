/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

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
import static io.harness.k8s.model.ServiceHookContext.MANIFEST_FILES_DIRECTORY;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
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
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.delegate.task.k8s.data.K8sCanaryDataException;
import io.harness.delegate.task.k8s.data.K8sCanaryDataException.K8sCanaryDataExceptionBuilder;
import io.harness.delegate.task.utils.ServiceHookDTO;
import io.harness.delegate.utils.ServiceHookHandler;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sCliCommandType;
import io.harness.k8s.K8sCommandFlagsUtils;
import io.harness.k8s.KubernetesReleaseDetails;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.ServiceHookAction;
import io.harness.k8s.model.ServiceHookType;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public class K8sCanaryRequestHandler extends K8sRequestHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sCanaryBaseHandler k8sCanaryBaseHandler;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  private final K8sCanaryHandlerConfig k8sCanaryHandlerConfig = new K8sCanaryHandlerConfig();
  private K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
  private boolean canaryWorkloadDeployed;
  private boolean saveReleaseHistory;
  private K8sReleaseHandler releaseHandler;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sCanaryDeployRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sDeployRequest", "Must be instance of K8sCanaryDeployRequestK8sCanaryDeployRequest"));
    }

    K8sCanaryDeployRequest k8sCanaryDeployRequest = (K8sCanaryDeployRequest) k8sDeployRequest;
    k8sRequestHandlerContext.setEnabledSupportHPAAndPDB(k8sCanaryDeployRequest.isEnabledSupportHPAAndPDB());
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(k8sCanaryDeployRequest.isUseDeclarativeRollback());
    k8sCanaryHandlerConfig.setReleaseName(k8sCanaryDeployRequest.getReleaseName());
    k8sCanaryHandlerConfig.setManifestFilesDirectory(
        Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString());
    final long timeoutInMillis = getTimeoutMillisFromMinutes(k8sCanaryDeployRequest.getTimeoutIntervalInMin());

    LogCallback executionLogCallback = k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, FetchFiles,
        k8sCanaryDeployRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);
    executionLogCallback.saveExecutionLog(
        color("\nStarting Kubernetes Canary Deployment", LogColor.White, LogWeight.Bold));
    ServiceHookDTO serviceHookTaskParams = new ServiceHookDTO(k8sDelegateTaskParams);
    ServiceHookHandler serviceHookHandler =
        new ServiceHookHandler(k8sCanaryDeployRequest.getServiceHooks(), serviceHookTaskParams, timeoutInMillis);
    serviceHookHandler.addToContext(
        MANIFEST_FILES_DIRECTORY.getContextName(), k8sCanaryHandlerConfig.getManifestFilesDirectory());
    serviceHookHandler.execute(ServiceHookType.PRE_HOOK, ServiceHookAction.FETCH_FILES,
        k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);
    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sCanaryDeployRequest.getManifestDelegateConfig(),
        k8sCanaryHandlerConfig.getManifestFilesDirectory(), executionLogCallback, timeoutInMillis,
        k8sCanaryDeployRequest.getAccountId(), false);
    serviceHookHandler.execute(ServiceHookType.POST_HOOK, ServiceHookAction.FETCH_FILES,
        k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);
    executionLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
    init(k8sCanaryDeployRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress), serviceHookHandler);

    prepareForCanary(k8sCanaryDeployRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prepare, true, commandUnitsProgress));
    // Apply Command Flag
    Map<String, String> k8sCommandFlag = k8sCanaryDeployRequest.getK8sCommandFlags();
    String commandFlags = K8sCommandFlagsUtils.getK8sCommandFlags(K8sCliCommandType.Apply.name(), k8sCommandFlag);
    k8sTaskHelperBase.applyManifests(k8sCanaryHandlerConfig.getClient(), k8sCanaryHandlerConfig.getResources(),
        k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Apply, true, commandUnitsProgress), true, true,
        commandFlags);

    // At this point we're sure that manifest has been applied successfully and canary workload is deployed
    this.canaryWorkloadDeployed = true;
    this.saveReleaseHistory = true;

    LogCallback steadyStateLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress);
    KubernetesResource canaryWorkload = k8sCanaryHandlerConfig.getCanaryWorkload();
    serviceHookHandler.addWorkloadContextForHooks(Collections.singletonList(canaryWorkload), Collections.emptyList());
    serviceHookHandler.execute(ServiceHookType.PRE_HOOK, ServiceHookAction.STEADY_STATE_CHECK,
        k8sDelegateTaskParams.getWorkingDirectory(), steadyStateLogCallback);
    K8sSteadyStateDTO k8sSteadyStateDTO = K8sSteadyStateDTO.builder()
                                              .request(k8sDeployRequest)
                                              .resourceIds(Collections.singletonList(canaryWorkload.getResourceId()))
                                              .executionLogCallback(steadyStateLogCallback)
                                              .k8sDelegateTaskParams(k8sDelegateTaskParams)
                                              .namespace(canaryWorkload.getResourceId().getNamespace())
                                              .denoteOverallSuccess(false)
                                              .isErrorFrameworkEnabled(true)
                                              .kubernetesConfig(k8sCanaryHandlerConfig.getKubernetesConfig())
                                              .build();

    K8sClient k8sClient =
        k8sTaskHelperBase.getKubernetesClient(k8sCanaryDeployRequest.isUseK8sApiForSteadyStateCheck());
    k8sClient.performSteadyStateCheck(k8sSteadyStateDTO);

    serviceHookHandler.execute(ServiceHookType.POST_HOOK, ServiceHookAction.STEADY_STATE_CHECK,
        k8sDelegateTaskParams.getWorkingDirectory(), steadyStateLogCallback);
    steadyStateLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
    LogCallback wrapUpLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WrapUp, true, commandUnitsProgress);

    List<K8sPod> allPods = k8sCanaryBaseHandler.getAllPods(
        k8sCanaryHandlerConfig, k8sCanaryDeployRequest.getReleaseName(), timeoutInMillis);
    k8sCanaryBaseHandler.wrapUp(k8sCanaryHandlerConfig.getClient(), k8sDelegateTaskParams, wrapUpLogCallback);

    k8sTaskHelperBase.saveRelease(k8sCanaryDeployRequest.isUseDeclarativeRollback(), false,
        k8sCanaryHandlerConfig.getKubernetesConfig(), k8sCanaryHandlerConfig.getCurrentRelease(),
        k8sCanaryHandlerConfig.getReleaseHistory(), k8sCanaryHandlerConfig.getReleaseName());

    wrapUpLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

    String canaryObjectsNames = canaryWorkload.getResourceId().namespaceKindNameRef();

    if (k8sRequestHandlerContext.isEnabledSupportHPAAndPDB()) {
      canaryObjectsNames = k8sCanaryBaseHandler.appendHPAAndPDBNamesToCanaryWorkloads(
          canaryObjectsNames, k8sRequestHandlerContext.getResourcesForNameUpdate());
    }

    if (k8sCanaryDeployRequest.isUseDeclarativeRollback()) {
      canaryObjectsNames = k8sCanaryBaseHandler.appendSecretAndConfigMapNamesToCanaryWorkloads(
          canaryObjectsNames, k8sCanaryHandlerConfig.getResources());
    }
    return K8sDeployResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .k8sNGTaskResponse(K8sCanaryDeployResponse.builder()
                               .canaryWorkload(canaryObjectsNames)
                               .k8sPodList(allPods)
                               .releaseNumber(k8sCanaryHandlerConfig.getCurrentRelease().getReleaseNumber())
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
      k8sCanaryBaseHandler.failAndSaveRelease(k8sCanaryHandlerConfig);
    }

    K8sCanaryDataExceptionBuilder k8sCanaryDataBuilder =
        K8sCanaryDataException.dataBuilder().canaryWorkloadDeployed(canaryWorkloadDeployed).cause(exception);
    KubernetesResource canaryWorkload = k8sCanaryHandlerConfig.getCanaryWorkload();
    if (canaryWorkload != null && canaryWorkload.getResourceId() != null) {
      String canaryObjectsNames = canaryWorkload.getResourceId().namespaceKindNameRef();
      if (((K8sCanaryDeployRequest) request).isUseDeclarativeRollback()) {
        canaryObjectsNames = k8sCanaryBaseHandler.appendSecretAndConfigMapNamesToCanaryWorkloads(
            canaryObjectsNames, k8sCanaryHandlerConfig.getResources());
      }

      if (k8sRequestHandlerContext.isEnabledSupportHPAAndPDB()) {
        canaryObjectsNames = k8sCanaryBaseHandler.appendHPAAndPDBNamesToCanaryWorkloads(
            canaryObjectsNames, k8sRequestHandlerContext.getResourcesForNameUpdate());
      }

      k8sCanaryDataBuilder.canaryWorkload(canaryObjectsNames);
    }

    throw k8sCanaryDataBuilder.build();
  }

  @VisibleForTesting
  void init(K8sCanaryDeployRequest request, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback,
      ServiceHookHandler serviceHookHandler) throws Exception {
    logCallback.saveExecutionLog("Initializing..\n");
    logCallback.saveExecutionLog(color(String.format("Release Name: [%s]", request.getReleaseName()), Yellow, Bold));
    k8sCanaryHandlerConfig.setKubernetesConfig(containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        request.getK8sInfraDelegateConfig(), k8sDelegateTaskParams.getWorkingDirectory(), logCallback));
    k8sCanaryHandlerConfig.setClient(KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory()));

    IK8sReleaseHistory releaseHistory =
        releaseHandler.getReleaseHistory(k8sCanaryHandlerConfig.getKubernetesConfig(), request.getReleaseName());
    int currentReleaseNumber = releaseHistory.getAndIncrementLastReleaseNumber();
    if (request.isUseDeclarativeRollback() && isEmpty(releaseHistory)) {
      currentReleaseNumber = k8sTaskHelperBase.getNextReleaseNumberFromOldReleaseHistory(
          k8sCanaryHandlerConfig.getKubernetesConfig(), request.getReleaseName());
    }

    k8sCanaryHandlerConfig.setCurrentReleaseNumber(currentReleaseNumber);
    k8sCanaryHandlerConfig.setReleaseHistory(releaseHistory);
    k8sCanaryHandlerConfig.setUseDeclarativeRollback(request.isUseDeclarativeRollback());
    k8sCanaryHandlerConfig.setReleaseName(request.getReleaseName());

    k8sTaskHelperBase.deleteSkippedManifestFiles(k8sCanaryHandlerConfig.getManifestFilesDirectory(), logCallback);

    KubernetesReleaseDetails releaseDetails =
        KubernetesReleaseDetails.builder().releaseNumber(currentReleaseNumber).build();

    List<String> manifestOverrideFiles = getManifestOverrideFlies(request, releaseDetails.toContextMap());

    List<FileData> manifestFiles = k8sTaskHelperBase.renderTemplate(k8sDelegateTaskParams,
        request.getManifestDelegateConfig(), k8sCanaryHandlerConfig.getManifestFilesDirectory(), manifestOverrideFiles,
        request.getReleaseName(), k8sCanaryHandlerConfig.getKubernetesConfig().getNamespace(), logCallback,
        request.getTimeoutIntervalInMin());
    serviceHookHandler.execute(ServiceHookType.PRE_HOOK, ServiceHookAction.TEMPLATE_MANIFEST,
        k8sDelegateTaskParams.getWorkingDirectory(), logCallback);
    List<KubernetesResource> resources =
        k8sTaskHelperBase.readManifests(manifestFiles, logCallback, isErrorFrameworkSupported());
    k8sRequestHandlerContext.setResources(resources);
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(
        resources, k8sCanaryHandlerConfig.getKubernetesConfig().getNamespace());

    k8sCanaryBaseHandler.updateDestinationRuleManifestFilesWithSubsets(
        resources, k8sCanaryHandlerConfig.getKubernetesConfig(), logCallback);
    k8sCanaryBaseHandler.updateVirtualServiceManifestFilesWithRoutes(
        resources, k8sCanaryHandlerConfig.getKubernetesConfig(), logCallback);
    k8sCanaryHandlerConfig.setResources(resources);

    logCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));
    logCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(k8sCanaryHandlerConfig.getResources()));

    serviceHookHandler.execute(ServiceHookType.POST_HOOK, ServiceHookAction.TEMPLATE_MANIFEST,
        k8sDelegateTaskParams.getWorkingDirectory(), logCallback);

    if (request.isSkipDryRun()) {
      logCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    k8sTaskHelperBase.dryRunManifests(k8sCanaryHandlerConfig.getClient(), k8sCanaryHandlerConfig.getResources(),
        k8sDelegateTaskParams, logCallback, true);
  }

  @VisibleForTesting
  void prepareForCanary(K8sCanaryDeployRequest k8sCanaryDeployRequest, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback logCallback) throws Exception {
    k8sCanaryBaseHandler.prepareForCanary(k8sCanaryHandlerConfig, k8sRequestHandlerContext, k8sDelegateTaskParams,
        k8sCanaryDeployRequest.isSkipResourceVersioning(), logCallback, true);
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

    k8sCanaryBaseHandler.updateTargetInstances(
        k8sCanaryHandlerConfig, k8sRequestHandlerContext, targetInstances, logCallback);

    IK8sRelease currentRelease = releaseHandler.createRelease(
        k8sCanaryHandlerConfig.getReleaseName(), k8sCanaryHandlerConfig.getCurrentReleaseNumber());
    currentRelease.setReleaseData(k8sCanaryHandlerConfig.getResources(), false);

    k8sCanaryHandlerConfig.setCurrentRelease(currentRelease);
    if (!k8sCanaryDeployRequest.isUseDeclarativeRollback()) {
      ((K8SLegacyReleaseHistory) k8sCanaryHandlerConfig.getReleaseHistory())
          .getReleaseHistory()
          .addReleaseToReleaseHistory((K8sLegacyRelease) currentRelease);
    }

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }

  @VisibleForTesting
  K8sCanaryHandlerConfig getK8sCanaryHandlerConfig() {
    return k8sCanaryHandlerConfig;
  }

  @VisibleForTesting
  K8sRequestHandlerContext getK8sRequestHandlerContext() {
    return k8sRequestHandlerContext;
  }
}
