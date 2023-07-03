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
import static io.harness.k8s.model.HarnessLabelValues.bgStageEnv;
import static io.harness.k8s.model.ServiceHookContext.MANIFEST_FILES_DIRECTORY;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_RELEASE_COLOR_KEY;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sBGDeployRequest;
import io.harness.delegate.task.k8s.K8sBGDeployResponse;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.delegate.task.utils.ServiceHookDTO;
import io.harness.delegate.utils.ServiceHookHandler;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.KubernetesYamlException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sCliCommandType;
import io.harness.k8s.K8sCommandFlagsUtils;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesReleaseDetails;
import io.harness.k8s.KubernetesReleaseDetails.KubernetesReleaseDetailsBuilder;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
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
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseSecretHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.models.V1Service;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@NoArgsConstructor
@Slf4j
public class K8sBGRequestHandler extends K8sRequestHandler {
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sBGBaseHandler k8sBGBaseHandler;
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private K8sManifestHashGenerator k8sManifestHashGenerator;
  private KubernetesConfig kubernetesConfig;
  private Kubectl client;
  private IK8sReleaseHistory releaseHistory;
  private IK8sRelease release;
  private KubernetesResource managedWorkload;
  private List<KubernetesResource> resources;
  private KubernetesResource primaryService;
  private KubernetesResource stageService;
  private String primaryColor;
  private String stageColor;
  private String releaseName;
  private String manifestFilesDirectory;
  private PrePruningInfo prePruningInfo;
  private boolean deploymentSkipped;

  private boolean shouldSaveReleaseHistory;
  private boolean useDeclarativeRollback;
  private boolean skipUnchangedManifest;
  private boolean storeReleaseHash;
  private int currentReleaseNumber;
  private long timeoutInMillis;
  private String currentManifestHash;
  private K8sReleaseHandler releaseHandler;
  private K8sRequestHandlerContext k8sRequestHandlerContext = new K8sRequestHandlerContext();

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sBGDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sBGDeployRequest"));
    }

    K8sBGDeployRequest k8sBGDeployRequest = (K8sBGDeployRequest) k8sDeployRequest;
    deploymentSkipped = false;
    k8sRequestHandlerContext.setEnabledSupportHPAAndPDB(k8sBGDeployRequest.isEnabledSupportHPAAndPDB());
    releaseName = k8sBGDeployRequest.getReleaseName();
    useDeclarativeRollback = k8sBGDeployRequest.isUseDeclarativeRollback();
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);
    manifestFilesDirectory = Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString();
    timeoutInMillis = getTimeoutMillisFromMinutes(k8sBGDeployRequest.getTimeoutIntervalInMin());
    skipUnchangedManifest = k8sBGDeployRequest.isSkipUnchangedManifest();
    storeReleaseHash = k8sBGDeployRequest.isStoreReleaseHash();
    LogCallback executionLogCallback = k8sTaskHelperBase.getLogCallback(
        logStreamingTaskClient, FetchFiles, k8sBGDeployRequest.isShouldOpenFetchFilesLogStream(), commandUnitsProgress);
    ServiceHookDTO serviceHookTaskParams = new ServiceHookDTO(k8sDelegateTaskParams);
    ServiceHookHandler serviceHookHandler =
        new ServiceHookHandler(k8sBGDeployRequest.getServiceHooks(), serviceHookTaskParams, timeoutInMillis);
    executionLogCallback.saveExecutionLog(
        color("\nStarting Kubernetes Blue-Green Deployment", LogColor.White, LogWeight.Bold));
    serviceHookHandler.addToContext(MANIFEST_FILES_DIRECTORY.getContextName(), manifestFilesDirectory);
    serviceHookHandler.execute(ServiceHookType.PRE_HOOK, ServiceHookAction.FETCH_FILES,
        k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);
    k8sTaskHelperBase.fetchManifestFilesAndWriteToDirectory(k8sBGDeployRequest.getManifestDelegateConfig(),
        manifestFilesDirectory, executionLogCallback, timeoutInMillis, k8sBGDeployRequest.getAccountId(), false);
    serviceHookHandler.execute(ServiceHookType.POST_HOOK, ServiceHookAction.FETCH_FILES,
        k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);
    executionLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
    init(k8sBGDeployRequest, k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress), serviceHookHandler);

    prepareForBlueGreen(k8sDelegateTaskParams,
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prepare, true, commandUnitsProgress),
        k8sBGDeployRequest.isSkipResourceVersioning(), k8sBGDeployRequest.isPruningEnabled());

    if (deploymentSkipped) {
      K8sBGDeployResponse k8sBGDeployResponse = K8sBGDeployResponse.builder().stageDeploymentSkipped(true).build();
      return K8sDeployResponse.builder().commandExecutionStatus(SUCCESS).k8sNGTaskResponse(k8sBGDeployResponse).build();
    }

    if (!useDeclarativeRollback) {
      ((K8sLegacyRelease) release).setManagedWorkload(managedWorkload.getResourceId().cloneInternal());
    }

    shouldSaveReleaseHistory = true;
    // Apply Command Flag
    Map<String, String> k8sCommandFlag = k8sBGDeployRequest.getK8sCommandFlags();

    String commandFlags = K8sCommandFlagsUtils.getK8sCommandFlags(K8sCliCommandType.Apply.name(), k8sCommandFlag);

    LogCallback applyManifestsLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Apply, true, commandUnitsProgress);

    if (!useDeclarativeRollback) {
      k8sTaskHelperBase.warnIfReleaseNameConflictsWithSecretOrConfigMap(
          resources, releaseName, applyManifestsLogCallback);
    }

    k8sTaskHelperBase.applyManifests(
        client, resources, k8sDelegateTaskParams, applyManifestsLogCallback, true, true, commandFlags);

    k8sTaskHelperBase.saveRelease(
        useDeclarativeRollback, false, kubernetesConfig, release, releaseHistory, releaseName);

    if (!useDeclarativeRollback) {
      ((K8sLegacyRelease) release)
          .setManagedWorkloadRevision(
              k8sTaskHelperBase.getLatestRevision(client, managedWorkload.getResourceId(), k8sDelegateTaskParams));
    }

    LogCallback steadyStateLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress);

    serviceHookHandler.addWorkloadContextForHooks(Collections.singletonList(managedWorkload), Collections.emptyList());
    serviceHookHandler.execute(ServiceHookType.PRE_HOOK, ServiceHookAction.STEADY_STATE_CHECK,
        k8sDelegateTaskParams.getWorkingDirectory(), steadyStateLogCallback);

    K8sSteadyStateDTO k8sSteadyStateDTO = K8sSteadyStateDTO.builder()
                                              .request(k8sDeployRequest)
                                              .resourceIds(Collections.singletonList(managedWorkload.getResourceId()))
                                              .executionLogCallback(steadyStateLogCallback)
                                              .k8sDelegateTaskParams(k8sDelegateTaskParams)
                                              .namespace(managedWorkload.getResourceId().getNamespace())
                                              .denoteOverallSuccess(false)
                                              .isErrorFrameworkEnabled(true)
                                              .kubernetesConfig(kubernetesConfig)
                                              .build();

    K8sClient k8sClient = k8sTaskHelperBase.getKubernetesClient(k8sBGDeployRequest.isUseK8sApiForSteadyStateCheck());
    k8sClient.performSteadyStateCheck(k8sSteadyStateDTO);

    serviceHookHandler.execute(ServiceHookType.POST_HOOK, ServiceHookAction.STEADY_STATE_CHECK,
        k8sDelegateTaskParams.getWorkingDirectory(), steadyStateLogCallback);
    steadyStateLogCallback.saveExecutionLog("Done.", INFO, SUCCESS);
    LogCallback wrapUpLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WrapUp, true, commandUnitsProgress);

    k8sBGBaseHandler.wrapUp(k8sDelegateTaskParams, wrapUpLogCallback, client);
    final List<K8sPod> podList = k8sBGBaseHandler.getAllPods(
        timeoutInMillis, kubernetesConfig, managedWorkload, primaryColor, stageColor, releaseName);

    if (!useDeclarativeRollback) {
      ((K8sLegacyRelease) release)
          .setManagedWorkloadRevision(
              k8sTaskHelperBase.getLatestRevision(client, managedWorkload.getResourceId(), k8sDelegateTaskParams));
    }
    saveRelease(Succeeded);

    K8sBGDeployResponse k8sBGDeployResponse = K8sBGDeployResponse.builder()
                                                  .releaseNumber(release.getReleaseNumber())
                                                  .k8sPodList(podList)
                                                  .primaryServiceName(primaryService.getResourceId().getName())
                                                  .stageServiceName(stageService.getResourceId().getName())
                                                  .stageColor(stageColor)
                                                  .primaryColor(primaryColor)
                                                  .build();
    wrapUpLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

    if (k8sBGDeployRequest.isPruningEnabled()) {
      LogCallback pruneExecutionLogCallback =
          k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Prune, true, commandUnitsProgress);
      k8sBGBaseHandler.pruneForBg(
          k8sDelegateTaskParams, pruneExecutionLogCallback, primaryColor, stageColor, prePruningInfo, release, client);
    }
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
      saveRelease(Failed);
    }
  }

  private void saveRelease(Status status) throws Exception {
    release.updateReleaseStatus(status);
    k8sTaskHelperBase.saveRelease(
        useDeclarativeRollback, false, kubernetesConfig, release, releaseHistory, releaseName);
  }

  @VisibleForTesting
  void init(K8sBGDeployRequest request, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      ServiceHookHandler serviceHookHandler) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    executionLogCallback.saveExecutionLog(color(String.format("Release Name: [%s]", releaseName), Yellow, Bold));

    kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        request.getK8sInfraDelegateConfig(), k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);

    client = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());
    releaseHistory = releaseHandler.getReleaseHistory(kubernetesConfig, request.getReleaseName());
    currentReleaseNumber = releaseHistory.getAndIncrementLastReleaseNumber();
    if (useDeclarativeRollback && isEmpty(releaseHistory)) {
      currentReleaseNumber =
          k8sTaskHelperBase.getNextReleaseNumberFromOldReleaseHistory(kubernetesConfig, request.getReleaseName());
    }

    k8sTaskHelperBase.deleteSkippedManifestFiles(manifestFilesDirectory, executionLogCallback);

    KubernetesReleaseDetailsBuilder releaseBuilder =
        KubernetesReleaseDetails.builder().releaseNumber(currentReleaseNumber);

    if (useDeclarativeRollback) {
      IK8sRelease latestRelease = releaseHistory.getLatestRelease();
      if (latestRelease == null) {
        // Since HarnessLabelValues.colorDefault is for primary service during first time deployment
        // for stage color we should reverse of it
        releaseBuilder.color(k8sBGBaseHandler.getInverseColor(HarnessLabelValues.colorDefault));
      } else {
        releaseBuilder.color(k8sBGBaseHandler.getInverseColor(latestRelease.getReleaseColor()));
      }
    }

    List<String> manifestOverrideFiles = getManifestOverrideFlies(request, releaseBuilder.build().toContextMap());
    serviceHookHandler.execute(ServiceHookType.PRE_HOOK, ServiceHookAction.TEMPLATE_MANIFEST,
        k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);

    List<FileData> manifestFiles = k8sTaskHelperBase.renderTemplate(k8sDelegateTaskParams,
        request.getManifestDelegateConfig(), manifestFilesDirectory, manifestOverrideFiles, releaseName,
        kubernetesConfig.getNamespace(), executionLogCallback, request.getTimeoutIntervalInMin());

    resources = k8sTaskHelperBase.readManifests(manifestFiles, executionLogCallback, isErrorFrameworkSupported());
    k8sRequestHandlerContext.setResources(resources);
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, kubernetesConfig.getNamespace());

    executionLogCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));

    executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(resources));

    serviceHookHandler.execute(ServiceHookType.POST_HOOK, ServiceHookAction.TEMPLATE_MANIFEST,
        k8sDelegateTaskParams.getWorkingDirectory(), executionLogCallback);

    if (storeReleaseHash) {
      currentManifestHash = k8sManifestHashGenerator.manifestHash(
          resources, k8sDelegateTaskParams, executionLogCallback, timeoutInMillis, client);
    }

    if (request.isSkipDryRun()) {
      executionLogCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    k8sTaskHelperBase.dryRunManifests(client, resources, k8sDelegateTaskParams, executionLogCallback, true);
  }

  @VisibleForTesting
  void prepareForBlueGreen(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback,
      boolean skipResourceVersioning, boolean isPruningEnabled) throws Exception {
    if (!skipResourceVersioning && !useDeclarativeRollback) {
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
      stageService.appendSuffixInName("-stage", k8sRequestHandlerContext);
      resources.add(stageService);
      executionLogCallback.saveExecutionLog(format("Created Stage service [%s] using Spec from Primary Service [%s]",
          stageService.getResourceId().getName(), primaryService.getResourceId().getName()));
    }

    primaryColor = k8sBGBaseHandler.getPrimaryColor(primaryService, kubernetesConfig, executionLogCallback);

    if (skipUnchangedManifest) {
      IK8sRelease primaryRelease = releaseHistory.getLatestSuccessfulReleaseMatchingColor(primaryColor);
      if (primaryRelease != null) {
        String primaryReleaseHash = primaryRelease.getManifestHash();
        if (currentManifestHash.equals(primaryReleaseHash)) {
          executionLogCallback.saveExecutionLog(
              color("\nSkipping stage deployment because given manifest matches existing deployed manifest", Yellow,
                  Bold),
              INFO);
          executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
          deploymentSkipped = true;
          return;
        }
      }
    }
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

    release = releaseHandler.createRelease(releaseName, currentReleaseNumber);

    if (storeReleaseHash) {
      release.setManifestHash(currentManifestHash);
    }
    prePruningInfo = k8sBGBaseHandler.cleanupForBlueGreen(k8sDelegateTaskParams, releaseHistory, executionLogCallback,
        primaryColor, stageColor, currentReleaseNumber, client, kubernetesConfig, releaseName, useDeclarativeRollback);

    executionLogCallback.saveExecutionLog("\nCurrent release number is: " + currentReleaseNumber);

    if (!skipResourceVersioning && !useDeclarativeRollback) {
      executionLogCallback.saveExecutionLog("\nVersioning resources.");
      k8sTaskHelperBase.addRevisionNumber(k8sRequestHandlerContext, currentReleaseNumber);
    }

    if (useDeclarativeRollback) {
      executionLogCallback.saveExecutionLog(
          format("Adding stage color [%s] as a suffix to Configmap and Secret names.", stageColor));
      k8sTaskHelperBase.addSuffixToConfigmapsAndSecrets(k8sRequestHandlerContext, stageColor, executionLogCallback);
    }

    managedWorkload = getManagedWorkload(resources);

    managedWorkload.appendSuffixInName('-' + stageColor, k8sRequestHandlerContext);
    managedWorkload.addLabelsInPodSpec(
        ImmutableMap.of(HarnessLabels.releaseName, releaseName, HarnessLabels.color, stageColor));
    managedWorkload.addLabelsInResourceSelector(
        ImmutableMap.of(HarnessLabels.color, stageColor), k8sRequestHandlerContext);

    // do the name update for all the resources (HPA and PDB)
    if (k8sRequestHandlerContext.isEnabledSupportHPAAndPDB()
        && EmptyPredicate.isNotEmpty(k8sRequestHandlerContext.getResourcesForNameUpdate())) {
      String suffix = '-' + stageColor;
      k8sRequestHandlerContext.getResourcesForNameUpdate().forEach(
          resource -> { resource.appendSuffixInName(suffix, k8sRequestHandlerContext); });
    }

    primaryService.addColorSelector(primaryColor, k8sRequestHandlerContext);
    stageService.addColorSelector(stageColor, k8sRequestHandlerContext);

    executionLogCallback.saveExecutionLog("\nWorkload to deploy is: "
        + color(managedWorkload.getResourceId().kindNameRef(), k8sBGBaseHandler.getLogColor(stageColor), Bold));

    release.setReleaseData(resources, isPruningEnabled);
    release.setBgEnvironment(bgStageEnv);
    if (useDeclarativeRollback) {
      // store color in new release secret's labels
      K8sReleaseSecretHelper.putLabelsItem((K8sRelease) release, RELEASE_SECRET_RELEASE_COLOR_KEY, stageColor);
    }

    if (!useDeclarativeRollback) {
      ((K8SLegacyReleaseHistory) releaseHistory)
          .getReleaseHistory()
          .addReleaseToReleaseHistory((K8sLegacyRelease) release);
    }

    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }
}
