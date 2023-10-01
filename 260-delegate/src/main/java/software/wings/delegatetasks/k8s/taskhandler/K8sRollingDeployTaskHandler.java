/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.k8s.K8sRollingBaseHandler.HARNESS_TRACK_STABLE_SELECTOR;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.exception.ExceptionUtils.getMessage;
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
import static io.harness.k8s.manifest.VersionUtils.addRevisionNumber;
import static io.harness.k8s.manifest.VersionUtils.markVersionedResources;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Cyan;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileData;
import io.harness.delegate.k8s.K8sRollingBaseHandler;
import io.harness.delegate.k8s.beans.K8sRollingHandlerConfig;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
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
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sRollingDeployTaskHandler extends K8sTaskHandler {
  @Inject private transient KubernetesContainerService kubernetesContainerService;
  @Inject private transient ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private transient K8sTaskHelper k8sTaskHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject K8sRollingBaseHandler k8sRollingBaseHandler;

  private K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
  private K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();
  private K8sReleaseHandler releaseHandler;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (!(k8sTaskParameters instanceof K8sRollingDeployTaskParameters)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sTaskParameters", "Must be instance of K8sRollingDeployTaskParameters"));
    }

    K8sRollingDeployTaskParameters k8sRollingDeployTaskParameters = (K8sRollingDeployTaskParameters) k8sTaskParameters;

    boolean useDeclarativeRollback = k8sRollingDeployTaskParameters.isUseDeclarativeRollback();
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);

    k8sRollingHandlerConfig.setReleaseName(k8sRollingDeployTaskParameters.getReleaseName());
    k8sRollingHandlerConfig.setUseDeclarativeRollback(useDeclarativeRollback);
    k8sRollingHandlerConfig.setManifestFilesDirectory(
        Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString());
    long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(k8sTaskParameters.getTimeoutIntervalInMin());

    boolean success;
    if (k8sRollingDeployTaskParameters.isInheritManifests()) {
      success = k8sTaskHelper.restore(k8sRollingDeployTaskParameters.getKubernetesResources(),
          k8sRollingDeployTaskParameters.getK8sClusterConfig(), k8sDelegateTaskParams, k8sRollingHandlerConfig,
          k8sRequestHandlerContext, k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, Init));
      if (!success) {
        return getFailureResponse();
      }
    } else {
      ExecutionLogCallback executionLogCallback =
          k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, FetchFiles);
      executionLogCallback.saveExecutionLog(
          color("\nStarting Kubernetes Rolling Deployment", LogColor.White, LogWeight.Bold));

      success = k8sTaskHelper.fetchManifestFilesAndWriteToDirectory(
          k8sRollingDeployTaskParameters.getK8sDelegateManifestConfig(),
          k8sRollingHandlerConfig.getManifestFilesDirectory(), executionLogCallback, steadyStateTimeoutInMillis);

      if (!success) {
        return getFailureResponse();
      }

      success = init(k8sRollingDeployTaskParameters, k8sDelegateTaskParams,
          k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, Init));

      if (!success) {
        return getFailureResponse();
      }

      if (k8sRollingDeployTaskParameters.isExportManifests()) {
        return K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .k8sTaskResponse(
                K8sRollingDeployResponse.builder().resources(k8sRollingHandlerConfig.getResources()).build())
            .build();
      }
    }

    ExecutionLogCallback prepareLogCallback =
        k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, Prepare);
    success = prepareForRolling(k8sDelegateTaskParams, prepareLogCallback,
        k8sRollingDeployTaskParameters.isInCanaryWorkflow(),
        k8sRollingDeployTaskParameters.getSkipVersioningForAllK8sObjects(),
        k8sRollingDeployTaskParameters.isPruningEnabled(),
        k8sRollingDeployTaskParameters.isSkipAddingTrackSelectorToDeployment());
    if (!success) {
      return getFailureResponse();
    }

    List<KubernetesResource> allWorkloads =
        ListUtils.union(k8sRollingHandlerConfig.getManagedWorkloads(), k8sRollingHandlerConfig.getCustomWorkloads());
    List<K8sPod> existingPodList = k8sRollingBaseHandler.getExistingPods(steadyStateTimeoutInMillis, allWorkloads,
        k8sRollingHandlerConfig.getKubernetesConfig(), k8sRollingHandlerConfig.getReleaseName(), prepareLogCallback);

    success = k8sTaskHelperBase.applyManifests(k8sRollingHandlerConfig.getClient(),
        k8sRollingHandlerConfig.getResources(), k8sDelegateTaskParams,
        k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, Apply), true, null);
    if (!success) {
      if (!useDeclarativeRollback) {
        k8sRollingBaseHandler.setManagedWorkloadsInRelease(k8sDelegateTaskParams,
            k8sRollingHandlerConfig.getManagedWorkloads(), (K8sLegacyRelease) k8sRollingHandlerConfig.getRelease(),
            k8sRollingHandlerConfig.getClient());
        k8sRollingBaseHandler.setCustomWorkloadsInRelease(
            k8sRollingHandlerConfig.getCustomWorkloads(), (K8sLegacyRelease) k8sRollingHandlerConfig.getRelease());
      }
      saveRelease(IK8sRelease.Status.Failed);
      return getFailureResponse();
    }

    if (isEmpty(k8sRollingHandlerConfig.getManagedWorkloads())
        && isEmpty(k8sRollingHandlerConfig.getCustomWorkloads())) {
      k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, WaitForSteadyState)
          .saveExecutionLog("Skipping Status Check since there is no Managed Workload.", INFO, SUCCESS);
    } else {
      if (!useDeclarativeRollback) {
        k8sRollingBaseHandler.setManagedWorkloadsInRelease(k8sDelegateTaskParams,
            k8sRollingHandlerConfig.getManagedWorkloads(), (K8sLegacyRelease) k8sRollingHandlerConfig.getRelease(),
            k8sRollingHandlerConfig.getClient());
        k8sRollingBaseHandler.setCustomWorkloadsInRelease(
            k8sRollingHandlerConfig.getCustomWorkloads(), (K8sLegacyRelease) k8sRollingHandlerConfig.getRelease());
      }

      k8sTaskHelperBase.saveRelease(useDeclarativeRollback, !k8sRollingHandlerConfig.getCustomWorkloads().isEmpty(),
          k8sRollingHandlerConfig.getKubernetesConfig(), k8sRollingHandlerConfig.getRelease(),
          k8sRollingHandlerConfig.getReleaseHistory(), k8sRollingHandlerConfig.getReleaseName());

      List<KubernetesResourceId> managedWorkloadKubernetesResourceIds = k8sRollingHandlerConfig.getManagedWorkloads()
                                                                            .stream()
                                                                            .map(KubernetesResource::getResourceId)
                                                                            .collect(toList());
      ExecutionLogCallback executionLogCallback =
          k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, WaitForSteadyState);

      success = k8sTaskHelperBase.doStatusCheckForAllResources(k8sRollingHandlerConfig.getClient(),
          managedWorkloadKubernetesResourceIds, k8sDelegateTaskParams,
          k8sRollingHandlerConfig.getKubernetesConfig().getNamespace(), executionLogCallback,
          k8sRollingHandlerConfig.getCustomWorkloads().isEmpty());

      boolean customWorkloadsStatusSuccess = k8sTaskHelperBase.doStatusCheckForAllCustomResources(
          k8sRollingHandlerConfig.getClient(), k8sRollingHandlerConfig.getCustomWorkloads(), k8sDelegateTaskParams,
          executionLogCallback, true, steadyStateTimeoutInMillis);

      if (!useDeclarativeRollback) {
        // We have to update the workload revision again as the rollout history command sometimes gives the older
        // revision (known issue at least for Deployment and DeploymentConfig). There seems to be delay in handling of
        // the workloads where it still gives older revision even after the apply command has successfully run
        k8sRollingBaseHandler.updateManagedWorkloadsRevision(k8sDelegateTaskParams,
            (K8sLegacyRelease) k8sRollingHandlerConfig.getRelease(), k8sRollingHandlerConfig.getClient());
      }

      if (!success || !customWorkloadsStatusSuccess) {
        saveRelease(IK8sRelease.Status.Failed);
        return getFailureResponse();
      }
    }

    HelmChartInfo helmChartInfo =
        k8sTaskHelper.getHelmChartDetails(k8sRollingDeployTaskParameters.getK8sDelegateManifestConfig(),
            k8sRollingHandlerConfig.getManifestFilesDirectory());

    ExecutionLogCallback executionLogCallback =
        k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, WrapUp);

    k8sRollingBaseHandler.wrapUp(k8sDelegateTaskParams, executionLogCallback, k8sRollingHandlerConfig.getClient());

    try {
      String loadBalancer = k8sTaskHelperBase.getLoadBalancerEndpoint(
          k8sRollingHandlerConfig.getKubernetesConfig(), k8sRollingHandlerConfig.getResources());
      K8sRollingDeployResponse rollingSetupResponse =
          K8sRollingDeployResponse.builder()
              .releaseNumber(k8sRollingHandlerConfig.getRelease().getReleaseNumber())
              .k8sPodList(k8sTaskHelperBase.tagNewPods(
                  k8sRollingBaseHandler.getPods(steadyStateTimeoutInMillis, allWorkloads,
                      k8sRollingHandlerConfig.getKubernetesConfig(), k8sRollingHandlerConfig.getReleaseName()),
                  existingPodList))
              .loadBalancer(loadBalancer)
              .helmChartInfo(helmChartInfo)
              .build();

      saveRelease(IK8sRelease.Status.Succeeded);
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

      if (k8sRollingDeployTaskParameters.isPruningEnabled()) {
        IK8sRelease previousSuccessfulRelease = k8sRollingBaseHandler.getLastSuccessfulRelease(useDeclarativeRollback,
            k8sRollingHandlerConfig.getReleaseHistory(), k8sRollingHandlerConfig.getCurrentReleaseNumber(),
            k8sRollingHandlerConfig.getKubernetesConfig(), k8sRollingHandlerConfig.getReleaseName());
        List<KubernetesResourceId> prunedResourcesIds =
            prune(k8sRollingDeployTaskParameters, k8sDelegateTaskParams, previousSuccessfulRelease);
        rollingSetupResponse.setPrunedResourcesIds(prunedResourcesIds);
      }

      return K8sTaskExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .k8sTaskResponse(rollingSetupResponse)
          .build();
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(getMessage(ex), ERROR, FAILURE);
      saveRelease(IK8sRelease.Status.Failed);
      throw ex;
    }
  }

  public List<KubernetesResourceId> prune(K8sRollingDeployTaskParameters k8sRollingDeployTaskParameters,
      K8sDelegateTaskParams k8sDelegateTaskParams, IK8sRelease previousSuccessfulRelease) {
    ExecutionLogCallback executionLogCallback =
        k8sTaskHelper.getExecutionLogCallback(k8sRollingDeployTaskParameters, Prune);
    try {
      if (previousSuccessfulRelease == null || isEmpty(previousSuccessfulRelease.getResourcesWithSpecs())) {
        String logCallbackMessage = previousSuccessfulRelease == null
            ? "No previous successful deployment found, So no pruning required"
            : "Previous successful deployment executed with pruning disabled, Pruning can't be done";
        executionLogCallback.saveExecutionLog(logCallbackMessage, INFO, CommandExecutionStatus.SUCCESS);
        return emptyList();
      }

      List<KubernetesResource> previousResources = previousSuccessfulRelease.getResourcesWithSpecs();
      List<KubernetesResource> currentResources = k8sRollingHandlerConfig.getResources();
      k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(
          previousResources, k8sRollingHandlerConfig.getKubernetesConfig().getNamespace());
      k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(
          currentResources, k8sRollingHandlerConfig.getKubernetesConfig().getNamespace());

      List<KubernetesResourceId> resourceIdsToBePruned =
          k8sTaskHelperBase.getResourcesToBePrunedInOrder(previousResources, currentResources);
      if (isEmpty(resourceIdsToBePruned)) {
        executionLogCallback.saveExecutionLog(
            format("No resource is eligible to be pruned from last successful release: %s, So no pruning required",
                previousSuccessfulRelease.getReleaseNumber()),
            INFO, CommandExecutionStatus.SUCCESS);
        return emptyList();
      }
      List<KubernetesResourceId> prunedResources =
          k8sTaskHelperBase.executeDeleteHandlingPartialExecution(k8sRollingHandlerConfig.getClient(),
              k8sDelegateTaskParams, resourceIdsToBePruned, executionLogCallback, false);
      executionLogCallback.saveExecutionLog("Pruning step completed", INFO, SUCCESS);
      return prunedResources;
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog("Failed to delete resources while pruning", WARN, RUNNING);
      executionLogCallback.saveExecutionLog(getMessage(ex), WARN, SUCCESS);
      return emptyList();
    }
  }

  private void saveRelease(Status status) throws Exception {
    k8sRollingHandlerConfig.getRelease().updateReleaseStatus(status);
    k8sTaskHelperBase.saveRelease(k8sRollingHandlerConfig.isUseDeclarativeRollback(),
        !k8sRollingHandlerConfig.getCustomWorkloads().isEmpty(), k8sRollingHandlerConfig.getKubernetesConfig(),
        k8sRollingHandlerConfig.getRelease(), k8sRollingHandlerConfig.getReleaseHistory(),
        k8sRollingHandlerConfig.getReleaseName());
  }

  private K8sTaskExecutionResponse getFailureResponse() {
    K8sRollingDeployResponse rollingSetupResponse = K8sRollingDeployResponse.builder().build();
    return K8sTaskExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .k8sTaskResponse(rollingSetupResponse)
        .build();
  }

  @VisibleForTesting
  boolean init(K8sRollingDeployTaskParameters request, K8sDelegateTaskParams k8sDelegateTaskParams,
      ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    KubernetesConfig kubernetesConfig =
        containerDeploymentDelegateHelper.getKubernetesConfig(request.getK8sClusterConfig(), false);
    k8sRollingHandlerConfig.setKubernetesConfig(kubernetesConfig);
    Kubectl client = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());
    k8sRollingHandlerConfig.setClient(client);
    try {
      k8sTaskHelperBase.deleteSkippedManifestFiles(
          k8sRollingHandlerConfig.getManifestFilesDirectory(), executionLogCallback);

      List<FileData> manifestFiles = k8sTaskHelper.renderTemplate(k8sDelegateTaskParams,
          request.getK8sDelegateManifestConfig(), k8sRollingHandlerConfig.getManifestFilesDirectory(),
          request.getValuesYamlList(), k8sRollingHandlerConfig.getReleaseName(),
          k8sRollingHandlerConfig.getKubernetesConfig().getNamespace(), executionLogCallback, request);

      List<KubernetesResource> resources = k8sTaskHelperBase.readManifestAndOverrideLocalSecrets(
          manifestFiles, executionLogCallback, request.isLocalOverrideFeatureFlag());
      k8sRollingHandlerConfig.setResources(resources);
      k8sRequestHandlerContext.setResources(resources);
      k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, kubernetesConfig.getNamespace());

      if (request.isInCanaryWorkflow()) {
        k8sRollingBaseHandler.updateDestinationRuleWithSubsets(executionLogCallback, resources, kubernetesConfig);
        k8sRollingBaseHandler.updateVirtualServiceWithRoutes(executionLogCallback, resources, kubernetesConfig);
      }

      executionLogCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));

      executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(resources));

      if (request.isSkipDryRun()) {
        executionLogCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
        executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        return true;
      }

      return k8sTaskHelperBase.dryRunManifests(client, resources, k8sDelegateTaskParams, executionLogCallback);
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }
  }

  private boolean prepareForRolling(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      boolean inCanaryWorkflow, Boolean skipVersioningForAllK8sObjects, boolean isPruningEnabled,
      boolean skipAddingTrackSelectorToDeployment) {
    try {
      IK8sReleaseHistory releaseHistory = releaseHandler.getReleaseHistory(
          k8sRollingHandlerConfig.getKubernetesConfig(), k8sRollingHandlerConfig.getReleaseName());
      k8sRollingHandlerConfig.setReleaseHistory(releaseHistory);

      List<KubernetesResource> managedWorkloads = getWorkloads(k8sRollingHandlerConfig.getResources());
      k8sRollingHandlerConfig.setManagedWorkloads(managedWorkloads);
      boolean noManagedWorkloads = isEmpty(managedWorkloads);
      boolean useDeclarativeRollback = k8sRollingHandlerConfig.isUseDeclarativeRollback();

      if (!noManagedWorkloads && isNotTrue(skipVersioningForAllK8sObjects) && !useDeclarativeRollback) {
        markVersionedResources(k8sRollingHandlerConfig.getResources());
      } else if (noManagedWorkloads) {
        executionLogCallback.saveExecutionLog(
            color("No managed workloads, skipping resource versioning \n", Yellow, Bold));
      }

      executionLogCallback.saveExecutionLog("Manifests processed. Found following resources: \n"
          + k8sTaskHelperBase.getResourcesInTableFormat(k8sRollingHandlerConfig.getResources()));

      int currentReleaseNumber = releaseHistory.getNextReleaseNumber(inCanaryWorkflow);
      if (useDeclarativeRollback && isEmpty(releaseHistory) && !inCanaryWorkflow) {
        currentReleaseNumber = k8sTaskHelperBase.getNextReleaseNumberFromOldReleaseHistory(
            k8sRollingHandlerConfig.getKubernetesConfig(), k8sRollingHandlerConfig.getReleaseName());
      }
      k8sRollingHandlerConfig.setCurrentReleaseNumber(currentReleaseNumber);

      IK8sRelease currentRelease = null;
      if (inCanaryWorkflow) {
        currentRelease = releaseHistory.getLatestRelease();
      }

      if (currentRelease == null) {
        // either not in canary workflow or release history is empty (null latest release)
        currentRelease = releaseHandler.createRelease(k8sRollingHandlerConfig.getReleaseName(), currentReleaseNumber);
      }

      executionLogCallback.saveExecutionLog("\nCurrent release number is: " + currentReleaseNumber);

      K8sReleaseHistoryCleanupDTO releaseCleanupDTO =
          k8sTaskHelperBase.createReleaseHistoryCleanupRequest(k8sRollingHandlerConfig.getReleaseName(), releaseHistory,
              k8sRollingHandlerConfig.getClient(), k8sRollingHandlerConfig.getKubernetesConfig(), executionLogCallback,
              currentReleaseNumber, k8sDelegateTaskParams);
      releaseHandler.cleanReleaseHistory(releaseCleanupDTO);

      List<KubernetesResource> customWorkloads =
          getCustomResourceDefinitionWorkloads(k8sRollingHandlerConfig.getResources());
      k8sRollingHandlerConfig.setCustomWorkloads(customWorkloads);

      if (isEmpty(managedWorkloads) && isEmpty(customWorkloads)) {
        executionLogCallback.saveExecutionLog(color("\nNo Managed Workload found.", Yellow, Bold));
      } else {
        executionLogCallback.saveExecutionLog(color("\nFound following Managed Workloads: \n", Cyan, Bold)
            + k8sTaskHelperBase.getResourcesInTableFormat(ListUtils.union(managedWorkloads, customWorkloads)));

        k8sTaskHelperBase.checkSteadyStateCondition(customWorkloads);

        executionLogCallback.saveExecutionLog("\nVersioning resources.");
        if (isNotTrue(skipVersioningForAllK8sObjects) && !useDeclarativeRollback) {
          addRevisionNumber(k8sRequestHandlerContext, currentReleaseNumber);
        }

        final List<KubernetesResource> deploymentContainingTrackStableSelector = skipAddingTrackSelectorToDeployment
            ? k8sTaskHelperBase.getDeploymentContainingTrackStableSelector(
                k8sRollingHandlerConfig.getKubernetesConfig(), managedWorkloads, HARNESS_TRACK_STABLE_SELECTOR)
            : emptyList();

        k8sRollingBaseHandler.addLabelsInDeploymentSelectorForCanary(inCanaryWorkflow,
            skipAddingTrackSelectorToDeployment, managedWorkloads, deploymentContainingTrackStableSelector,
            k8sRequestHandlerContext);
        k8sRollingBaseHandler.addLabelsInManagedWorkloadPodSpec(inCanaryWorkflow, skipAddingTrackSelectorToDeployment,
            managedWorkloads, deploymentContainingTrackStableSelector, k8sRollingHandlerConfig.getReleaseName());
      }

      currentRelease.setReleaseData(k8sRollingHandlerConfig.getResources(), isPruningEnabled);
      k8sRollingHandlerConfig.setRelease(currentRelease);

      if (!useDeclarativeRollback && !inCanaryWorkflow) {
        ((K8SLegacyReleaseHistory) releaseHistory)
            .getReleaseHistory()
            .addReleaseToReleaseHistory((K8sLegacyRelease) currentRelease);
      }
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(getMessage(e), ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }

    return true;
  }

  @VisibleForTesting
  K8sRequestHandlerContext getK8sRequestHandlerContext() {
    return k8sRequestHandlerContext;
  }
}
