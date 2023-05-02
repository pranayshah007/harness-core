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
import static io.harness.delegate.k8s.K8sRollingBaseHandler.HARNESS_TRACK_STABLE_SELECTOR;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.k8s.K8sCommandUnitConstants.Apply;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Prepare;
import static io.harness.k8s.K8sCommandUnitConstants.Prune;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.k8s.K8sCommandUnitConstants.WrapUp;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.k8s.manifest.ManifestHelper.getCustomResourceDefinitionWorkloads;
import static io.harness.k8s.manifest.ManifestHelper.getWorkloads;
import static io.harness.k8s.manifest.VersionUtils.markVersionedResources;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Cyan;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sRollingDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.delegate.task.utils.ServiceHookDTO;
import io.harness.delegate.utils.ServiceHookHandler;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sCliCommandType;
import io.harness.k8s.K8sCommandFlagsUtils;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesReleaseDetails;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.ServiceHookAction;
import io.harness.k8s.model.ServiceHookType;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sRelease.Status;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistoryCleanupDTO;
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
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@NoArgsConstructor
@Slf4j
public class K8sRollingRequestHandler extends K8sRequestHandler {
  @Inject private transient KubernetesContainerService kubernetesContainerService;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject K8sRollingBaseHandler k8sRollingBaseHandler;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  private KubernetesConfig kubernetesConfig;
  private Kubectl client;
  List<KubernetesResource> customWorkloads;
  List<KubernetesResource> managedWorkloads;
  List<KubernetesResource> resources;
  private String releaseName;
  private String manifestFilesDirectory;
  private boolean shouldSaveReleaseHistory;
  private boolean useDeclarativeRollback;
  private K8sReleaseHandler releaseHandler;
  private IK8sReleaseHistory releaseHistory;
  private IK8sRelease release;
  private int currentReleaseNumber;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sRollingDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sRollingDeployRequest"));
    }

    K8sRollingDeployRequest k8sRollingDeployRequest = (K8sRollingDeployRequest) k8sDeployRequest;

    releaseName = k8sRollingDeployRequest.getReleaseName();
    manifestFilesDirectory = Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString();
    useDeclarativeRollback = k8sRollingDeployRequest.isUseDeclarativeRollback();
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);
    long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(k8sDeployRequest.getTimeoutIntervalInMin());

    LogCallback logCallback = k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, FetchFiles,
        k8sRollingDeployRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);
    ServiceHookDTO serviceHookTaskParams = new ServiceHookDTO(k8sDelegateTaskParams);
    ServiceHookHandler serviceHookHandler = new ServiceHookHandler(
        k8sRollingDeployRequest.getServiceHooks(), serviceHookTaskParams, steadyStateTimeoutInMillis);
    serviceHookHandler.applyServiceHooks(ServiceHookType.PRE_HOOK, ServiceHookAction.FETCH_FILES,
        k8sDelegateTaskParams.getWorkingDirectory(), logCallback, manifestFilesDirectory);
    logCallback.saveExecutionLog(color("\nStarting Kubernetes Rolling Deployment", LogColor.White, LogWeight.Bold));
    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sRollingDeployRequest.getManifestDelegateConfig(),
        manifestFilesDirectory, logCallback, steadyStateTimeoutInMillis, k8sRollingDeployRequest.getAccountId(), false);

    serviceHookHandler.applyServiceHooks(ServiceHookType.POST_HOOK, ServiceHookAction.FETCH_FILES,
        k8sDelegateTaskParams.getWorkingDirectory(), logCallback, manifestFilesDirectory);
    logCallback.saveExecutionLog("Done.", INFO, SUCCESS);
    init(k8sRollingDeployRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress));

    LogCallback prepareLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prepare, true, commandUnitsProgress);
    prepareForRolling(k8sDelegateTaskParams, prepareLogCallback, k8sRollingDeployRequest.isInCanaryWorkflow(),
        k8sRollingDeployRequest.isSkipResourceVersioning(),
        k8sRollingDeployRequest.isSkipAddingTrackSelectorToDeployment(), k8sRollingDeployRequest.isPruningEnabled());

    List<KubernetesResource> allWorkloads = ListUtils.union(managedWorkloads, customWorkloads);
    List<K8sPod> existingPodList = k8sRollingBaseHandler.getExistingPods(
        steadyStateTimeoutInMillis, allWorkloads, kubernetesConfig, releaseName, prepareLogCallback);
    shouldSaveReleaseHistory = true;

    try {
      Map<String, String> k8sCommandFlag = k8sRollingDeployRequest.getK8sCommandFlags();
      String commandFlags = K8sCommandFlagsUtils.getK8sCommandFlags(K8sCliCommandType.Apply.name(), k8sCommandFlag);
      k8sTaskHelperBase.applyManifests(client, resources, k8sDelegateTaskParams,
          k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Apply, true, commandUnitsProgress), true, true,
          commandFlags);
    } finally {
      if (!useDeclarativeRollback && (isNotEmpty(managedWorkloads) || isNotEmpty(customWorkloads))) {
        k8sRollingBaseHandler.setManagedWorkloadsInRelease(
            k8sDelegateTaskParams, managedWorkloads, (K8sLegacyRelease) release, client);
        k8sRollingBaseHandler.setCustomWorkloadsInRelease(customWorkloads, (K8sLegacyRelease) release);
      }
    }

    if (isEmpty(managedWorkloads) && isEmpty(customWorkloads)) {
      k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress)
          .saveExecutionLog("Skipping Status Check since there is no Managed Workload.", INFO, SUCCESS);
    } else {
      k8sTaskHelperBase.saveRelease(
          useDeclarativeRollback, !customWorkloads.isEmpty(), kubernetesConfig, release, releaseHistory, releaseName);

      List<KubernetesResourceId> managedWorkloadKubernetesResourceIds =
          managedWorkloads.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList());
      LogCallback waitForeSteadyStateLogCallback =
          k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress);

      try {
        K8sSteadyStateDTO k8sSteadyStateDTO = k8sTaskHelperBase.createSteadyStateCheckRequest(k8sDeployRequest,
            managedWorkloadKubernetesResourceIds, waitForeSteadyStateLogCallback, k8sDelegateTaskParams,
            kubernetesConfig.getNamespace(), customWorkloads.isEmpty(), true);

        K8sClient k8sClient =
            k8sTaskHelperBase.getKubernetesClient(k8sRollingDeployRequest.isUseK8sApiForSteadyStateCheck());
        k8sClient.performSteadyStateCheck(k8sSteadyStateDTO);

        k8sTaskHelperBase.doStatusCheckForAllCustomResources(client, customWorkloads, k8sDelegateTaskParams,
            waitForeSteadyStateLogCallback, true, steadyStateTimeoutInMillis, true);
      } finally {
        // We have to update the DeploymentConfig revision again as the rollout history command sometimes gives the
        // older revision. There seems to be delay in handling of the DeploymentConfig where it still gives older
        // revision even after the apply command has successfully run
        if (!useDeclarativeRollback) {
          k8sRollingBaseHandler.updateManagedWorkloadsRevision(
              k8sDelegateTaskParams, (K8sLegacyRelease) release, client);
        }
      }
    }

    LogCallback executionLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WrapUp, true, commandUnitsProgress);
    k8sRollingBaseHandler.wrapUp(k8sDelegateTaskParams, executionLogCallback, client);

    String loadBalancer = k8sTaskHelperBase.getLoadBalancerEndpoint(kubernetesConfig, resources);
    K8sRollingDeployResponse rollingSetupResponse =
        K8sRollingDeployResponse.builder()
            .releaseNumber(currentReleaseNumber)
            .k8sPodList(k8sTaskHelperBase.tagNewPods(
                k8sRollingBaseHandler.getPods(steadyStateTimeoutInMillis, allWorkloads, kubernetesConfig, releaseName),
                existingPodList))
            .loadBalancer(loadBalancer)
            .build();

    saveRelease(Succeeded);
    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

    if (k8sRollingDeployRequest.isPruningEnabled()) {
      IK8sRelease lastSuccessfulRelease = k8sRollingBaseHandler.getLastSuccessfulRelease(
          useDeclarativeRollback, releaseHistory, currentReleaseNumber, kubernetesConfig, releaseName);
      LogCallback pruneResourcesLogCallback =
          k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prune, true, commandUnitsProgress);
      List<KubernetesResourceId> prunedResourceIds =
          prune(k8sDelegateTaskParams, lastSuccessfulRelease, pruneResourcesLogCallback);
      rollingSetupResponse.setPrunedResourceIds(prunedResourceIds);
    }

    return K8sDeployResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .k8sNGTaskResponse(rollingSetupResponse)
        .build();
  }

  public List<KubernetesResourceId> prune(K8sDelegateTaskParams k8sDelegateTaskParams,
      IK8sRelease previousSuccessfulRelease, LogCallback executionLogCallback) throws Exception {
    if (previousSuccessfulRelease == null || isEmpty(previousSuccessfulRelease.getResourcesWithSpecs())) {
      String logCallbackMessage = previousSuccessfulRelease == null
          ? "No previous successful deployment found, So no pruning required"
          : "Previous successful deployment executed with pruning disabled, Pruning can't be done";
      executionLogCallback.saveExecutionLog(logCallbackMessage, WARN, CommandExecutionStatus.SUCCESS);
      return emptyList();
    }

    // add namespace since new release implementation doesn't store namespace in spec
    List<KubernetesResource> previousResources = previousSuccessfulRelease.getResourcesWithSpecs();
    List<KubernetesResource> currentResources = release.getResourcesWithSpecs();
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(previousResources, kubernetesConfig.getNamespace());
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(currentResources, kubernetesConfig.getNamespace());

    List<KubernetesResourceId> resourceIdsToBePruned =
        k8sTaskHelperBase.getResourcesToBePrunedInOrder(previousResources, currentResources);
    if (isEmpty(resourceIdsToBePruned)) {
      executionLogCallback.saveExecutionLog(
          format("No resource is eligible to be pruned from last successful release: %s, So no pruning required",
              previousSuccessfulRelease.getReleaseNumber()),
          INFO, CommandExecutionStatus.SUCCESS);
      return emptyList();
    }

    List<KubernetesResourceId> prunedResources = k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
        client, k8sDelegateTaskParams, resourceIdsToBePruned, executionLogCallback, false);
    executionLogCallback.saveExecutionLog("Pruning step completed", INFO, SUCCESS);
    return prunedResources;
  }

  @Override
  protected void handleTaskFailure(K8sDeployRequest request, Exception exception) throws Exception {
    if (shouldSaveReleaseHistory) {
      saveRelease(Failed);
    }
  }

  private void saveRelease(Status status) throws Exception {
    release.updateReleaseStatus(status);
    k8sTaskHelperBase.saveRelease(
        useDeclarativeRollback, !customWorkloads.isEmpty(), kubernetesConfig, release, releaseHistory, releaseName);
  }

  @VisibleForTesting
  void init(K8sRollingDeployRequest request, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    executionLogCallback.saveExecutionLog(color(String.format("Release Name: [%s]", releaseName), Yellow, Bold));
    kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        request.getK8sInfraDelegateConfig(), k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);
    client = Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath());

    releaseHistory = releaseHandler.getReleaseHistory(kubernetesConfig, request.getReleaseName());
    currentReleaseNumber = releaseHistory.getNextReleaseNumber(request.isInCanaryWorkflow());

    if (useDeclarativeRollback && isEmpty(releaseHistory) && !request.isInCanaryWorkflow()) {
      currentReleaseNumber =
          k8sTaskHelperBase.getNextReleaseNumberFromOldReleaseHistory(kubernetesConfig, request.getReleaseName());
    }

    KubernetesReleaseDetails releaseDetails =
        KubernetesReleaseDetails.builder().releaseNumber(currentReleaseNumber).build();

    List<String> manifestOverrideFiles = getManifestOverrideFlies(request, releaseDetails.toContextMap());

    this.resources =
        k8sRollingBaseHandler.prepareResourcesAndRenderTemplate(request, k8sDelegateTaskParams, manifestOverrideFiles,
            this.kubernetesConfig, this.manifestFilesDirectory, this.releaseName, request.isLocalOverrideFeatureFlag(),
            isErrorFrameworkSupported(), request.isInCanaryWorkflow(), executionLogCallback);

    if (request.isSkipDryRun()) {
      executionLogCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    k8sTaskHelperBase.dryRunManifests(
        client, resources, k8sDelegateTaskParams, executionLogCallback, true, request.isUseNewKubectlVersion());
  }

  private void prepareForRolling(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      boolean inCanaryWorkflow, boolean skipResourceVersioning, boolean skipAddingTrackSelectorToDeployment,
      boolean pruningEnabled) throws Exception {
    managedWorkloads = getWorkloads(resources);
    if (isNotEmpty(managedWorkloads) && !skipResourceVersioning && !useDeclarativeRollback) {
      markVersionedResources(resources);
    }

    executionLogCallback.saveExecutionLog(
        "Manifests processed. Found following resources: \n" + k8sTaskHelperBase.getResourcesInTableFormat(resources));

    if (inCanaryWorkflow) {
      release = releaseHistory.getLatestRelease();
    }

    if (release == null) {
      // either not in canary workflow or release history is empty (null latest release)
      release = releaseHandler.createRelease(releaseName, currentReleaseNumber);
    }

    executionLogCallback.saveExecutionLog("\nCurrent release number is: " + currentReleaseNumber);

    K8sReleaseHistoryCleanupDTO releaseCleanupDTO = k8sTaskHelperBase.createReleaseHistoryCleanupRequest(releaseName,
        releaseHistory, client, kubernetesConfig, executionLogCallback, currentReleaseNumber, k8sDelegateTaskParams);
    releaseHandler.cleanReleaseHistory(releaseCleanupDTO);

    customWorkloads = getCustomResourceDefinitionWorkloads(resources);

    if (isEmpty(managedWorkloads) && isEmpty(customWorkloads)) {
      executionLogCallback.saveExecutionLog(color("\nNo Managed Workload found.", Yellow, Bold));
    } else {
      executionLogCallback.saveExecutionLog(color("\nFound following Managed Workloads: \n", Cyan, Bold)
          + k8sTaskHelperBase.getResourcesInTableFormat(ListUtils.union(managedWorkloads, customWorkloads)));

      if (!skipResourceVersioning && !useDeclarativeRollback) {
        executionLogCallback.saveExecutionLog("\nVersioning resources.");
        k8sTaskHelperBase.addRevisionNumber(resources, currentReleaseNumber);
      }

      final List<KubernetesResource> deploymentContainingTrackStableSelector = skipAddingTrackSelectorToDeployment
          ? k8sTaskHelperBase.getDeploymentContainingTrackStableSelector(
              kubernetesConfig, managedWorkloads, HARNESS_TRACK_STABLE_SELECTOR)
          : emptyList();

      k8sRollingBaseHandler.addLabelsInManagedWorkloadPodSpec(inCanaryWorkflow, skipAddingTrackSelectorToDeployment,
          managedWorkloads, deploymentContainingTrackStableSelector, releaseName);
      k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(inCanaryWorkflow,
          skipAddingTrackSelectorToDeployment, managedWorkloads, deploymentContainingTrackStableSelector);
    }

    release.setReleaseData(resources, pruningEnabled);

    if (!useDeclarativeRollback && !inCanaryWorkflow) {
      ((K8SLegacyReleaseHistory) releaseHistory)
          .getReleaseHistory()
          .addReleaseToReleaseHistory((K8sLegacyRelease) release);
    }
  }
}
