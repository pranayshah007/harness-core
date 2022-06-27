/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.k8s.K8sCommandUnitConstants.Apply;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Prepare;
import static io.harness.k8s.K8sCommandUnitConstants.Prune;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.k8s.K8sCommandUnitConstants.WrapUp;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.k8s.manifest.ManifestHelper.getKubernetesResourceFromSpec;
import static io.harness.k8s.manifest.ManifestHelper.getManagedWorkload;
import static io.harness.k8s.manifest.ManifestHelper.getPrimaryService;
import static io.harness.k8s.manifest.ManifestHelper.getServices;
import static io.harness.k8s.manifest.ManifestHelper.getStageService;
import static io.harness.k8s.manifest.ManifestHelper.getWorkloadsForCanaryAndBG;
import static io.harness.k8s.manifest.VersionUtils.markVersionedResources;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sBGDeployRequest;
import io.harness.delegate.task.k8s.K8sBGDeployResponse;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.exception.KubernetesExceptionExplanation;
import io.harness.delegate.task.k8s.exception.KubernetesExceptionHints;
import io.harness.delegate.task.k8s.exception.KubernetesExceptionMessages;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.KubernetesYamlException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
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
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.models.V1Service;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@NoArgsConstructor
@Slf4j
public class K8sBGRequestHandler extends K8sRequestHandler {
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sBGBaseHandler k8sBGBaseHandler;
  @Inject private KubernetesContainerService kubernetesContainerService;

  private KubernetesConfig kubernetesConfig;
  private Kubectl client;
  private ReleaseHistory releaseHistory;
  private Release currentRelease;
  private KubernetesResource managedWorkload;
  private List<KubernetesResource> resources;
  private KubernetesResource primaryService;
  private KubernetesResource stageService;
  private String primaryColor;
  private String stageColor;
  private String releaseName;
  private String manifestFilesDirectory;
  private PrePruningInfo prePruningInfo;

  private boolean shouldSaveReleaseHistory;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sBGDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sBGDeployRequest"));
    }

    K8sBGDeployRequest k8sBGDeployRequest = (K8sBGDeployRequest) k8sDeployRequest;

    releaseName = k8sBGDeployRequest.getReleaseName();
    manifestFilesDirectory = Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString();
    final long timeoutInMillis = getTimeoutMillisFromMinutes(k8sBGDeployRequest.getTimeoutIntervalInMin());

    LogCallback executionLogCallback = k8sTaskHelperBase.getLogCallback(
        logStreamingTaskClient, FetchFiles, k8sBGDeployRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);
    executionLogCallback.saveExecutionLog(
        color("\nStarting Kubernetes Blue-Greeen Deployment", LogColor.White, LogWeight.Bold));

    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sBGDeployRequest.getManifestDelegateConfig(),
        manifestFilesDirectory, executionLogCallback, timeoutInMillis, k8sBGDeployRequest.getAccountId());

    init(k8sBGDeployRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress));

    prepareForBlueGreen(k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prepare, true, commandUnitsProgress),
        k8sBGDeployRequest.isSkipResourceVersioning(), k8sBGDeployRequest.isPruningEnabled());

    currentRelease.setManagedWorkload(managedWorkload.getResourceId().cloneInternal());

    shouldSaveReleaseHistory = true;
    k8sTaskHelperBase.applyManifests(client, resources, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Apply, true, commandUnitsProgress), true, true);

    k8sTaskHelperBase.saveReleaseHistoryInConfigMap(
        kubernetesConfig, k8sBGDeployRequest.getReleaseName(), releaseHistory.getAsYaml());

    currentRelease.setManagedWorkloadRevision(
        k8sTaskHelperBase.getLatestRevision(client, managedWorkload.getResourceId(), k8sDelegateTaskParams));

    k8sTaskHelperBase.doStatusCheck(client, managedWorkload.getResourceId(), k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress), true);

    LogCallback wrapUpLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WrapUp, true, commandUnitsProgress);

    k8sBGBaseHandler.wrapUp(k8sDelegateTaskParams, wrapUpLogCallback, client);
    final List<K8sPod> podList = k8sBGBaseHandler.getAllPods(
        timeoutInMillis, kubernetesConfig, managedWorkload, primaryColor, stageColor, releaseName);

    currentRelease.setManagedWorkloadRevision(
        k8sTaskHelperBase.getLatestRevision(client, managedWorkload.getResourceId(), k8sDelegateTaskParams));
    releaseHistory.setReleaseStatus(Status.Succeeded);
    k8sTaskHelperBase.saveReleaseHistoryInConfigMap(
        kubernetesConfig, k8sBGDeployRequest.getReleaseName(), releaseHistory.getAsYaml());

    K8sBGDeployResponse k8sBGDeployResponse = K8sBGDeployResponse.builder()
                                                  .releaseNumber(currentRelease.getNumber())
                                                  .k8sPodList(podList)
                                                  .primaryServiceName(primaryService.getResourceId().getName())
                                                  .stageServiceName(stageService.getResourceId().getName())
                                                  .stageColor(stageColor)
                                                  .primaryColor(primaryColor)
                                                  .build();

    if (k8sBGDeployRequest.isPruningEnabled()) {
      LogCallback pruneExecutionLogCallback =
          k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prune, true, commandUnitsProgress);
      k8sBGBaseHandler.pruneForBg(k8sDelegateTaskParams, pruneExecutionLogCallback, primaryColor, stageColor,
          prePruningInfo, currentRelease, client);
    }

    wrapUpLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return K8sDeployResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .k8sNGTaskResponse(k8sBGDeployResponse)
        .build();
  }

  @Override
  public boolean isErrorFrameworkSupported() {
    return true;
  }

  @Override
  protected void handleTaskFailure(K8sDeployRequest request, Exception exception) throws Exception {
    if (shouldSaveReleaseHistory) {
      K8sBGDeployRequest k8sBGDeployRequest = (K8sBGDeployRequest) request;
      releaseHistory.setReleaseStatus(Status.Failed);
      k8sTaskHelperBase.saveReleaseHistoryInConfigMap(
          kubernetesConfig, k8sBGDeployRequest.getReleaseName(), releaseHistory.getAsYaml());
    }
  }

  @VisibleForTesting
  void init(K8sBGDeployRequest request, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback)
      throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    executionLogCallback.saveExecutionLog(color(String.format("Release Name: [%s]", releaseName), Yellow, Bold));

    kubernetesConfig =
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(request.getK8sInfraDelegateConfig());

    client = Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath());
    String releaseHistoryData =
        k8sTaskHelperBase.getReleaseHistoryDataFromConfigMap(kubernetesConfig, request.getReleaseName());
    releaseHistory = (StringUtils.isEmpty(releaseHistoryData)) ? ReleaseHistory.createNew()
                                                               : ReleaseHistory.createFromData(releaseHistoryData);

    k8sTaskHelperBase.deleteSkippedManifestFiles(manifestFilesDirectory, executionLogCallback);

    List<String> manifestOverrideFiles = getManifestOverrideFlies(request);

    List<FileData> manifestFiles = k8sTaskHelperBase.renderTemplate(k8sDelegateTaskParams,
        request.getManifestDelegateConfig(), manifestFilesDirectory, manifestOverrideFiles, releaseName,
        kubernetesConfig.getNamespace(), executionLogCallback, request.getTimeoutIntervalInMin());

    resources = k8sTaskHelperBase.readManifests(manifestFiles, executionLogCallback, isErrorFrameworkSupported());
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, kubernetesConfig.getNamespace());

    executionLogCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));

    executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(resources));

    if (request.isSkipDryRun()) {
      executionLogCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    k8sTaskHelperBase.dryRunManifests(
        client, resources, k8sDelegateTaskParams, executionLogCallback, true, request.isUseNewKubectlVersion());
  }

  @VisibleForTesting
  void prepareForBlueGreen(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      boolean skipResourceVersioning, boolean isPruningEnabled) throws Exception {
    if (!skipResourceVersioning) {
      markVersionedResources(resources);
    }

    executionLogCallback.saveExecutionLog(
        "Manifests processed. Found following resources: \n" + k8sTaskHelperBase.getResourcesInTableFormat(resources));

    List<KubernetesResource> workloads = getWorkloadsForCanaryAndBG(resources);

    if (workloads.size() != 1) {
      if (workloads.isEmpty()) {
        executionLogCallback.saveExecutionLog(
            "\nNo workload found in the Manifests. Can't do  Blue/Green Deployment. Only Deployment, DeploymentConfig (OpenShift) and StatefulSet workloads are supported in Blue/Green workflow type.",
            ERROR, FAILURE);

        throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.BG_NO_WORKLOADS_FOUND,
            KubernetesExceptionExplanation.BG_NO_WORKLOADS_FOUND,
            new KubernetesTaskException(KubernetesExceptionMessages.NO_WORKLOADS_FOUND));
      } else {
        executionLogCallback.saveExecutionLog(
            "\nThere are multiple workloads in the Service Manifests you are deploying. Blue/Green Workflows support a single Deployment, DeploymentConfig (OpenShift) or StatefulSet workload only. To deploy additional workloads in Manifests, annotate them with "
                + HarnessAnnotations.directApply + ": true",
            ERROR, FAILURE);
        String workloadsPrintableList = workloads.stream()
                                            .map(KubernetesResource::getResourceId)
                                            .map(KubernetesResourceId::kindNameRef)
                                            .collect(Collectors.joining(", "));

        throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.BG_MULTIPLE_WORKLOADS,
            format(KubernetesExceptionExplanation.BG_MULTIPLE_WORKLOADS, workloads.size(), workloadsPrintableList),
            new KubernetesTaskException(KubernetesExceptionMessages.MULTIPLE_WORKLOADS));
      }
    }

    primaryService = getPrimaryService(resources);
    stageService = getStageService(resources);

    if (primaryService == null) {
      List<KubernetesResource> services = getServices(resources);
      if (services.size() == 1) {
        primaryService = services.get(0);
        executionLogCallback.saveExecutionLog(
            "Primary Service is " + color(primaryService.getResourceId().getName(), White, Bold));
      } else if (services.size() == 0) {
        throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.BG_NO_SERVICE_FOUND,
            KubernetesExceptionExplanation.BG_NO_SERVICE_FOUND,
            new KubernetesYamlException(KubernetesExceptionMessages.NO_SERVICE_FOUND));
      } else {
        String servicePrintableList = services.stream()
                                          .map(KubernetesResource::getResourceId)
                                          .map(KubernetesResourceId::kindNameRef)
                                          .collect(Collectors.joining(", "));

        throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.BG_MULTIPLE_PRIMARY_SERVICE,
            format(KubernetesExceptionExplanation.BG_MULTIPLE_PRIMARY_SERVICE, services.size(), servicePrintableList),
            new KubernetesYamlException(KubernetesExceptionMessages.MULTIPLE_SERVICES));
      }
    }

    if (stageService == null) {
      // create a clone
      stageService = getKubernetesResourceFromSpec(primaryService.getSpec());
      stageService.appendSuffixInName("-stage");
      resources.add(stageService);
      executionLogCallback.saveExecutionLog(format("Created Stage service [%s] using Spec from Primary Service [%s]",
          stageService.getResourceId().getName(), primaryService.getResourceId().getName()));
    }

    primaryColor = k8sBGBaseHandler.getPrimaryColor(primaryService, kubernetesConfig, executionLogCallback);
    V1Service stageServiceInCluster =
        kubernetesContainerService.getService(kubernetesConfig, stageService.getResourceId().getName());
    if (stageServiceInCluster == null) {
      executionLogCallback.saveExecutionLog(
          "Stage Service [" + stageService.getResourceId().getName() + "] not found in cluster.");
    }

    if (primaryColor == null) {
      String serviceName = primaryService.getResourceId().getName();
      executionLogCallback.saveExecutionLog(
          format(
              "Found conflicting service [%s] in the cluster. For blue/green deployment, the label [harness.io/color] is required in service selector. Delete this existing service to proceed",
              serviceName),
          ERROR, FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(
          format(KubernetesExceptionHints.BG_CONFLICTING_SERVICE, serviceName),
          KubernetesExceptionExplanation.BG_CONFLICTING_SERVICE,
          new KubernetesTaskException(format(KubernetesExceptionMessages.BG_CONFLICTING_SERVICE, serviceName)));
    }

    stageColor = k8sBGBaseHandler.getInverseColor(primaryColor);

    if (isPruningEnabled) {
      currentRelease = releaseHistory.createNewReleaseWithResourceMap(
          resources.stream().filter(resource -> !resource.isSkipPruning()).collect(toList()));
    } else {
      currentRelease = releaseHistory.createNewRelease(
          resources.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList()));
    }

    prePruningInfo = k8sBGBaseHandler.cleanupForBlueGreen(
        k8sDelegateTaskParams, releaseHistory, executionLogCallback, primaryColor, stageColor, currentRelease, client);

    executionLogCallback.saveExecutionLog("\nCurrent release number is: " + currentRelease.getNumber());

    if (!skipResourceVersioning) {
      executionLogCallback.saveExecutionLog("\nVersioning resources.");
      k8sTaskHelperBase.addRevisionNumber(resources, currentRelease.getNumber());
    }
    managedWorkload = getManagedWorkload(resources);
    managedWorkload.appendSuffixInName('-' + stageColor);
    managedWorkload.addLabelsInPodSpec(
        ImmutableMap.of(HarnessLabels.releaseName, releaseName, HarnessLabels.color, stageColor));
    managedWorkload.addLabelsInDeploymentSelector(ImmutableMap.of(HarnessLabels.color, stageColor));

    primaryService.addColorSelectorInService(primaryColor);
    stageService.addColorSelectorInService(stageColor);

    executionLogCallback.saveExecutionLog("\nWorkload to deploy is: "
        + color(managedWorkload.getResourceId().kindNameRef(), k8sBGBaseHandler.getLogColor(stageColor), Bold));

    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }
}
