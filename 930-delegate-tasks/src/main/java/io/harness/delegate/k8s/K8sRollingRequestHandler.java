/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.k8s.K8sRollingBaseHandler.HARNESS_TRACK_STABLE_SELECTOR;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_HARNESS_SECRET_LABELS;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_HARNESS_SECRET_TYPE;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_KEY;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_VALUE;
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
import static io.harness.k8s.manifest.ManifestHelper.getNonManagedResources;
import static io.harness.k8s.manifest.ManifestHelper.getWorkloads;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Cyan;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.releasehistory.K8sReleaseHistoryService;
import io.harness.delegate.k8s.releasehistory.K8sReleaseService;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sRollingDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.Release.Status;
import io.harness.k8s.model.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.models.V1Secret;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  @Inject private K8sReleaseHistoryService releaseHistoryService;
  @Inject private K8sReleaseService releaseService;

  private KubernetesConfig kubernetesConfig;
  private Kubectl client;
  private ReleaseHistory releaseHistory;
  Release release;
  List<KubernetesResource> customWorkloads;
  List<KubernetesResource> managedWorkloads;
  List<KubernetesResource> nonManagedResources;
  List<KubernetesResource> resources;
  List<V1Secret> releaseList;
  private String releaseName;
  private String manifestFilesDirectory;
  private boolean shouldSaveReleaseHistory;
  private V1Secret currentRelease;
  int currentReleaseNumber;

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
    long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(k8sDeployRequest.getTimeoutIntervalInMin());

    LogCallback logCallback = k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, FetchFiles,
        k8sRollingDeployRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);

    logCallback.saveExecutionLog(color("\nStarting Kubernetes Rolling Deployment", LogColor.White, LogWeight.Bold));
    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sRollingDeployRequest.getManifestDelegateConfig(),
        manifestFilesDirectory, logCallback, steadyStateTimeoutInMillis, k8sRollingDeployRequest.getAccountId());

    init(k8sRollingDeployRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress));

    LogCallback prepareLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prepare, true, commandUnitsProgress);
    prepareForRolling(prepareLogCallback, k8sRollingDeployRequest.isInCanaryWorkflow(),
        k8sRollingDeployRequest.isSkipAddingTrackSelectorToDeployment());

    List<KubernetesResource> allWorkloads = ListUtils.union(managedWorkloads, customWorkloads);
    List<K8sPod> existingPodList = k8sRollingBaseHandler.getExistingPods(
        steadyStateTimeoutInMillis, allWorkloads, kubernetesConfig, releaseName, prepareLogCallback);

    k8sTaskHelperBase.applyManifests(client, resources, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Apply, true, commandUnitsProgress), true, true);
    shouldSaveReleaseHistory = true;

    if (isEmpty(managedWorkloads) && isEmpty(customWorkloads)) {
      k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress)
          .saveExecutionLog("Skipping Status Check since there is no Managed Workload.", INFO, SUCCESS);
    } else {
      releaseHistoryService.markStatusAndSaveRelease(currentRelease, Status.Succeeded.name(), kubernetesConfig);
      List<KubernetesResourceId> managedWorkloadKubernetesResourceIds =
          managedWorkloads.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList());
      LogCallback waitForeSteadyStateLogCallback =
          k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress);

      K8sSteadyStateDTO k8sSteadyStateDTO = K8sSteadyStateDTO.builder()
                                                .request(k8sDeployRequest)
                                                .resourceIds(managedWorkloadKubernetesResourceIds)
                                                .executionLogCallback(waitForeSteadyStateLogCallback)
                                                .k8sDelegateTaskParams(k8sDelegateTaskParams)
                                                .namespace(kubernetesConfig.getNamespace())
                                                .denoteOverallSuccess(customWorkloads.isEmpty())
                                                .isErrorFrameworkEnabled(true)
                                                .build();

      K8sClient k8sClient =
          k8sTaskHelperBase.getKubernetesClient(k8sRollingDeployRequest.isUseK8sApiForSteadyStateCheck());
      k8sClient.performSteadyStateCheck(k8sSteadyStateDTO);

      k8sTaskHelperBase.doStatusCheckForAllCustomResources(client, customWorkloads, k8sDelegateTaskParams,
          waitForeSteadyStateLogCallback, true, steadyStateTimeoutInMillis, true);
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

    releaseHistoryService.markStatusAndSaveRelease(currentRelease, Status.Succeeded.name(), kubernetesConfig);
    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

    if (k8sRollingDeployRequest.isPruningEnabled()) {
      V1Secret lastSuccessfulRelease = releaseService.getLastSuccessfulRelease(releaseList, currentReleaseNumber);
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
      V1Secret previousSuccessfulRelease, LogCallback executionLogCallback) throws Exception {
    if (previousSuccessfulRelease == null) {
      String logCallbackMessage = "No previous successful deployment found, So no pruning required";
      executionLogCallback.saveExecutionLog(logCallbackMessage, WARN, CommandExecutionStatus.SUCCESS);
      return emptyList();
    }

    List<KubernetesResource> resourcesFromPreviousSuccessfulRelease =
        releaseService.getResourcesFromRelease(previousSuccessfulRelease);

    if (isEmpty(resourcesFromPreviousSuccessfulRelease)) {
      String logCallbackMessage =
          "Previous successful deployment executed with pruning disabled, Pruning can't be done";
      executionLogCallback.saveExecutionLog(logCallbackMessage, WARN, CommandExecutionStatus.SUCCESS);
      return emptyList();
    }

    List<KubernetesResourceId> resourcesToPrune =
        k8sTaskHelperBase.getResourcesToBePrunedInOrder(resourcesFromPreviousSuccessfulRelease, resources);
    if (isEmpty(resourcesToPrune)) {
      executionLogCallback.saveExecutionLog(
          format("No resource is eligible to be pruned from last successful release %s, So no pruning required",
              releaseService.getReleaseLabelValue(previousSuccessfulRelease, RELEASE_NUMBER_LABEL_KEY)),
          INFO, CommandExecutionStatus.SUCCESS);
      return emptyList();
    }

    List<KubernetesResourceId> prunedResources = k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
        client, k8sDelegateTaskParams, resourcesToPrune, executionLogCallback, false);
    executionLogCallback.saveExecutionLog("Pruning step completed", INFO, SUCCESS);
    return prunedResources;
  }

  @Override
  protected void handleTaskFailure(K8sDeployRequest request, Exception exception) throws Exception {
    if (shouldSaveReleaseHistory) {
      releaseHistoryService.markStatusAndSaveRelease(currentRelease, Status.Failed.name(), kubernetesConfig);
    }
  }

  @VisibleForTesting
  void init(K8sRollingDeployRequest request, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    executionLogCallback.saveExecutionLog(color(String.format("Release Name: [%s]", releaseName), Yellow, Bold));
    kubernetesConfig =
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(request.getK8sInfraDelegateConfig());
    client = Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath());

    Map<String, String> labels = new HashMap<>(RELEASE_HARNESS_SECRET_LABELS);
    labels.put(RELEASE_KEY, request.getReleaseName());

    releaseList = releaseHistoryService.getReleaseHistory(kubernetesConfig, labels, RELEASE_HARNESS_SECRET_TYPE);
    currentReleaseNumber = releaseService.getCurrentReleaseNumber(releaseList);

    k8sTaskHelperBase.deleteSkippedManifestFiles(manifestFilesDirectory, executionLogCallback);

    List<String> manifestOverrideFiles = getManifestOverrideFlies(request);

    List<FileData> manifestFiles = k8sTaskHelperBase.renderTemplate(k8sDelegateTaskParams,
        request.getManifestDelegateConfig(), manifestFilesDirectory, manifestOverrideFiles, releaseName,
        kubernetesConfig.getNamespace(), executionLogCallback, request.getTimeoutIntervalInMin());

    resources = k8sTaskHelperBase.readManifestAndOverrideLocalSecrets(
        manifestFiles, executionLogCallback, request.isLocalOverrideFeatureFlag(), isErrorFrameworkSupported());
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, kubernetesConfig.getNamespace());

    if (request.isInCanaryWorkflow()) {
      k8sRollingBaseHandler.updateDestinationRuleWithSubsets(executionLogCallback, resources, kubernetesConfig);
      k8sRollingBaseHandler.updateVirtualServiceWithRoutes(executionLogCallback, resources, kubernetesConfig);
    }

    executionLogCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));

    executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(resources));

    currentRelease = releaseHistoryService.createRelease(releaseName, currentReleaseNumber, Status.InProgress.name());

    if (request.isSkipDryRun()) {
      executionLogCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    k8sTaskHelperBase.dryRunManifests(
        client, resources, k8sDelegateTaskParams, executionLogCallback, true, request.isUseNewKubectlVersion());
  }

  private void prepareForRolling(LogCallback executionLogCallback, boolean inCanaryWorkflow,
      boolean skipAddingTrackSelectorToDeployment) throws Exception {
    managedWorkloads = getWorkloads(resources);

    executionLogCallback.saveExecutionLog(
        "Manifests processed. Found following resources: \n" + k8sTaskHelperBase.getResourcesInTableFormat(resources));

    executionLogCallback.saveExecutionLog("\nCurrent release number is: " + currentReleaseNumber);

    releaseHistoryService.cleanReleaseHistory(kubernetesConfig, releaseName, currentReleaseNumber, releaseList);

    customWorkloads = getCustomResourceDefinitionWorkloads(resources);
    nonManagedResources = getNonManagedResources(resources, managedWorkloads, customWorkloads);

    if (isEmpty(managedWorkloads) && isEmpty(customWorkloads)) {
      executionLogCallback.saveExecutionLog(color("\nNo Managed Workload found.", Yellow, Bold));
    } else {
      executionLogCallback.saveExecutionLog(color("\nFound following Managed Workloads: \n", Cyan, Bold)
          + k8sTaskHelperBase.getResourcesInTableFormat(ListUtils.union(managedWorkloads, customWorkloads)));

      final List<KubernetesResource> deploymentContainingTrackStableSelector = skipAddingTrackSelectorToDeployment
          ? k8sTaskHelperBase.getDeploymentContainingTrackStableSelector(
              kubernetesConfig, managedWorkloads, HARNESS_TRACK_STABLE_SELECTOR)
          : emptyList();

      k8sRollingBaseHandler.addLabelsInManagedWorkloadPodSpec(inCanaryWorkflow, skipAddingTrackSelectorToDeployment,
          managedWorkloads, deploymentContainingTrackStableSelector, releaseName);
      k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(inCanaryWorkflow,
          skipAddingTrackSelectorToDeployment, managedWorkloads, deploymentContainingTrackStableSelector);
    }

    resources = Stream.of(managedWorkloads, customWorkloads, nonManagedResources)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
    releaseService.setResourcesInRelease(currentRelease, resources);
  }
}
