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
import static io.harness.k8s.manifest.VersionUtils.addRevisionNumber;
import static io.harness.k8s.manifest.VersionUtils.addSuffixToConfigmapsAndSecrets;
import static io.harness.k8s.manifest.VersionUtils.markVersionedResources;
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
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileData;
import io.harness.delegate.k8s.K8sBGBaseHandler;
import io.harness.delegate.k8s.beans.K8sBlueGreenHandlerConfig;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.KubernetesYamlException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseSecretHelper;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sBlueGreenDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sBlueGreenDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.models.V1Service;
import java.nio.file.Paths;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sBlueGreenDeployTaskHandler extends K8sTaskHandler {
  @Inject private transient KubernetesContainerService kubernetesContainerService;
  @Inject private transient ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private transient K8sTaskHelper k8sTaskHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sBGBaseHandler k8sBGBaseHandler;

  private K8sBlueGreenHandlerConfig k8sBlueGreenHandlerConfig = new K8sBlueGreenHandlerConfig();
  private K8sReleaseHandler releaseHandler;
  private int currentReleaseNumber;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (!(k8sTaskParameters instanceof K8sBlueGreenDeployTaskParameters)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sTaskParameters", "Must be instance of K8sBlueGreenDeployTaskParameters"));
    }

    K8sBlueGreenDeployTaskParameters k8sBlueGreenDeployTaskParameters =
        (K8sBlueGreenDeployTaskParameters) k8sTaskParameters;

    k8sBlueGreenHandlerConfig.setReleaseName(k8sBlueGreenDeployTaskParameters.getReleaseName());
    k8sBlueGreenHandlerConfig.setManifestFilesDirectory(
        Paths.get(k8sDelegateTaskParams.getWorkingDirectory(), MANIFEST_FILES_DIR).toString());
    boolean useDeclarativeRollback = k8sBlueGreenDeployTaskParameters.isUseDeclarativeRollback();
    k8sBlueGreenHandlerConfig.setUseDeclarativeRollback(useDeclarativeRollback);
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);
    final long timeoutInMillis = getTimeoutMillisFromMinutes(k8sTaskParameters.getTimeoutIntervalInMin());

    boolean success;
    if (k8sBlueGreenDeployTaskParameters.isInheritManifests()) {
      success = k8sTaskHelper.restore(k8sBlueGreenDeployTaskParameters.getKubernetesResources(),
          k8sBlueGreenDeployTaskParameters.getK8sClusterConfig(), k8sDelegateTaskParams, k8sBlueGreenHandlerConfig,
          k8sTaskHelper.getExecutionLogCallback(k8sBlueGreenDeployTaskParameters, Init));
      if (!success) {
        return getFailureResponse(null);
      }
    } else {
      ExecutionLogCallback executionLogCallback =
          k8sTaskHelper.getExecutionLogCallback(k8sBlueGreenDeployTaskParameters, FetchFiles);
      executionLogCallback.saveExecutionLog(
          color("\nStarting Kubernetes Blue-Greeen Deployment", LogColor.White, LogWeight.Bold));

      success = k8sTaskHelper.fetchManifestFilesAndWriteToDirectory(
          k8sBlueGreenDeployTaskParameters.getK8sDelegateManifestConfig(),
          k8sBlueGreenHandlerConfig.getManifestFilesDirectory(), executionLogCallback, timeoutInMillis);
      if (!success) {
        return getFailureResponse(null);
      }

      success = init(k8sBlueGreenDeployTaskParameters, k8sDelegateTaskParams,
          k8sTaskHelper.getExecutionLogCallback(k8sBlueGreenDeployTaskParameters, Init));
      if (!success) {
        return getFailureResponse(null);
      }

      if (k8sBlueGreenDeployTaskParameters.isExportManifests()) {
        return K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .k8sTaskResponse(
                K8sBlueGreenDeployResponse.builder().resources(k8sBlueGreenHandlerConfig.getResources()).build())
            .build();
      }
    }

    success = prepareForBlueGreen(k8sBlueGreenDeployTaskParameters, k8sDelegateTaskParams,
        k8sTaskHelper.getExecutionLogCallback(k8sBlueGreenDeployTaskParameters, Prepare));
    if (!success) {
      return getFailureResponse(null);
    }

    if (!useDeclarativeRollback) {
      ((K8sLegacyRelease) k8sBlueGreenHandlerConfig.getCurrentRelease())
          .setManagedWorkload(k8sBlueGreenHandlerConfig.getManagedWorkload().getResourceId().cloneInternal());
    }

    success = k8sTaskHelperBase.applyManifests(k8sBlueGreenHandlerConfig.getClient(),
        k8sBlueGreenHandlerConfig.getResources(), k8sDelegateTaskParams,
        k8sTaskHelper.getExecutionLogCallback(k8sBlueGreenDeployTaskParameters, Apply), true, null);
    if (!success) {
      saveRelease(Failed, useDeclarativeRollback);
      return getFailureResponse(null);
    }

    k8sTaskHelperBase.saveRelease(useDeclarativeRollback, false, k8sBlueGreenHandlerConfig.getKubernetesConfig(),
        k8sBlueGreenHandlerConfig.getCurrentRelease(), k8sBlueGreenHandlerConfig.getReleaseHistory(),
        k8sBlueGreenHandlerConfig.getReleaseName());

    if (!useDeclarativeRollback) {
      ((K8sLegacyRelease) k8sBlueGreenHandlerConfig.getCurrentRelease())
          .setManagedWorkloadRevision(k8sTaskHelperBase.getLatestRevision(k8sBlueGreenHandlerConfig.getClient(),
              k8sBlueGreenHandlerConfig.getManagedWorkload().getResourceId(), k8sDelegateTaskParams));
    }

    ExecutionLogCallback executionLogCallback =
        k8sTaskHelper.getExecutionLogCallback(k8sBlueGreenDeployTaskParameters, WaitForSteadyState);

    success = k8sTaskHelperBase.doStatusCheck(k8sBlueGreenHandlerConfig.getClient(),
        k8sBlueGreenHandlerConfig.getManagedWorkload().getResourceId(), k8sDelegateTaskParams, executionLogCallback);

    if (!success) {
      saveRelease(Failed, useDeclarativeRollback);
      return getFailureResponse(null);
    }

    ExecutionLogCallback wrapUpLogCallback =
        k8sTaskHelper.getExecutionLogCallback(k8sBlueGreenDeployTaskParameters, WrapUp);
    try {
      HelmChartInfo helmChartInfo =
          k8sTaskHelper.getHelmChartDetails(k8sBlueGreenDeployTaskParameters.getK8sDelegateManifestConfig(),
              k8sBlueGreenHandlerConfig.getManifestFilesDirectory());
      k8sBGBaseHandler.wrapUp(k8sDelegateTaskParams, wrapUpLogCallback, k8sBlueGreenHandlerConfig.getClient());
      final List<K8sPod> podList =
          k8sBGBaseHandler.getAllPods(timeoutInMillis, k8sBlueGreenHandlerConfig.getKubernetesConfig(),
              k8sBlueGreenHandlerConfig.getManagedWorkload(), k8sBlueGreenHandlerConfig.getPrimaryColor(),
              k8sBlueGreenHandlerConfig.getStageColor(), k8sBlueGreenHandlerConfig.getReleaseName());

      if (!useDeclarativeRollback) {
        ((K8sLegacyRelease) k8sBlueGreenHandlerConfig.getCurrentRelease())
            .setManagedWorkloadRevision(k8sTaskHelperBase.getLatestRevision(k8sBlueGreenHandlerConfig.getClient(),
                k8sBlueGreenHandlerConfig.getManagedWorkload().getResourceId(), k8sDelegateTaskParams));
      }

      saveRelease(Succeeded, useDeclarativeRollback);

      wrapUpLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

      if (k8sBlueGreenDeployTaskParameters.isPruningEnabled()) {
        ExecutionLogCallback pruneExecutionLogCallback =
            k8sTaskHelper.getExecutionLogCallback(k8sBlueGreenDeployTaskParameters, Prune);
        k8sBGBaseHandler.pruneForBg(k8sDelegateTaskParams, pruneExecutionLogCallback,
            k8sBlueGreenHandlerConfig.getPrimaryColor(), k8sBlueGreenHandlerConfig.getStageColor(),
            k8sBlueGreenHandlerConfig.getPrePruningInfo(), k8sBlueGreenHandlerConfig.getCurrentRelease(),
            k8sBlueGreenHandlerConfig.getClient());
      }

      return k8sTaskHelper.getK8sTaskExecutionResponse(
          K8sBlueGreenDeployResponse.builder()
              .releaseNumber(k8sBlueGreenHandlerConfig.getCurrentRelease().getReleaseNumber())
              .k8sPodList(podList)
              .primaryServiceName(k8sBlueGreenHandlerConfig.getPrimaryService().getResourceId().getName())
              .stageServiceName(k8sBlueGreenHandlerConfig.getStageService().getResourceId().getName())
              .stageColor(k8sBlueGreenHandlerConfig.getStageColor())
              .helmChartInfo(helmChartInfo)
              .build(),
          SUCCESS);
    } catch (Exception e) {
      wrapUpLogCallback.saveExecutionLog(e.getMessage(), ERROR, FAILURE);
      saveRelease(Failed, useDeclarativeRollback);
      throw e;
    }
  }

  private void saveRelease(IK8sRelease.Status status, boolean useDeclarativeRollback) throws Exception {
    k8sBlueGreenHandlerConfig.getCurrentRelease().updateReleaseStatus(status);
    k8sTaskHelperBase.saveRelease(useDeclarativeRollback, false, k8sBlueGreenHandlerConfig.getKubernetesConfig(),
        k8sBlueGreenHandlerConfig.getCurrentRelease(), k8sBlueGreenHandlerConfig.getReleaseHistory(),
        k8sBlueGreenHandlerConfig.getReleaseName());
  }

  boolean init(K8sBlueGreenDeployTaskParameters k8sBlueGreenDeployTaskParameters,
      K8sDelegateTaskParams k8sDelegateTaskParams, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("Initializing..\n");

    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(
        k8sBlueGreenDeployTaskParameters.getK8sClusterConfig(), false);
    k8sBlueGreenHandlerConfig.setKubernetesConfig(kubernetesConfig);

    k8sBlueGreenHandlerConfig.setClient(
        Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath()));

    try {
      k8sTaskHelperBase.deleteSkippedManifestFiles(
          k8sBlueGreenHandlerConfig.getManifestFilesDirectory(), executionLogCallback);

      List<FileData> manifestFiles = k8sTaskHelper.renderTemplate(k8sDelegateTaskParams,
          k8sBlueGreenDeployTaskParameters.getK8sDelegateManifestConfig(),
          k8sBlueGreenHandlerConfig.getManifestFilesDirectory(), k8sBlueGreenDeployTaskParameters.getValuesYamlList(),
          k8sBlueGreenHandlerConfig.getReleaseName(), kubernetesConfig.getNamespace(), executionLogCallback,
          k8sBlueGreenDeployTaskParameters);

      List<KubernetesResource> resources = k8sTaskHelperBase.readManifests(manifestFiles, executionLogCallback);
      k8sBlueGreenHandlerConfig.setResources(resources);
      k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, kubernetesConfig.getNamespace());
    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR);
      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    }

    executionLogCallback.saveExecutionLog(color("\nManifests [Post template rendering] :\n", White, Bold));

    executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(k8sBlueGreenHandlerConfig.getResources()));

    if (k8sBlueGreenDeployTaskParameters.isSkipDryRun()) {
      executionLogCallback.saveExecutionLog(color("\nSkipping Dry Run", Yellow, Bold), INFO);
      executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    return k8sTaskHelperBase.dryRunManifests(k8sBlueGreenHandlerConfig.getClient(),
        k8sBlueGreenHandlerConfig.getResources(), k8sDelegateTaskParams, executionLogCallback,
        k8sBlueGreenDeployTaskParameters.isUseNewKubectlVersion());
  }

  @VisibleForTesting
  boolean prepareForBlueGreen(K8sBlueGreenDeployTaskParameters k8sBlueGreenDeployTaskParameters,
      K8sDelegateTaskParams k8sDelegateTaskParams, ExecutionLogCallback executionLogCallback) {
    try {
      boolean useDeclarativeRollback = k8sBlueGreenDeployTaskParameters.isUseDeclarativeRollback();
      IK8sReleaseHistory releaseHistory = releaseHandler.getReleaseHistory(
          k8sBlueGreenHandlerConfig.getKubernetesConfig(), k8sBlueGreenHandlerConfig.getReleaseName());
      k8sBlueGreenHandlerConfig.setReleaseHistory(releaseHistory);
      currentReleaseNumber = releaseHistory.getAndIncrementLastReleaseNumber();
      if (useDeclarativeRollback && isEmpty(releaseHistory)) {
        currentReleaseNumber = k8sTaskHelperBase.getNextReleaseNumberFromOldReleaseHistory(
            k8sBlueGreenHandlerConfig.getKubernetesConfig(), k8sBlueGreenHandlerConfig.getReleaseName());
      }

      if (isNotTrue(k8sBlueGreenDeployTaskParameters.getSkipVersioningForAllK8sObjects()) && !useDeclarativeRollback) {
        markVersionedResources(k8sBlueGreenHandlerConfig.getResources());
      }

      executionLogCallback.saveExecutionLog("Manifests processed. Found following resources: \n"
          + k8sTaskHelperBase.getResourcesInTableFormat(k8sBlueGreenHandlerConfig.getResources()));

      List<KubernetesResource> workloads = getWorkloadsForCanaryAndBG(k8sBlueGreenHandlerConfig.getResources());

      if (workloads.size() != 1) {
        if (workloads.isEmpty()) {
          executionLogCallback.saveExecutionLog(
              "\nNo workload found in the Manifests. Can't do  Blue/Green Deployment. Only Deployment, DeploymentConfig (OpenShift) and StatefulSet workloads are supported in Blue/Green workflow type.",
              ERROR, FAILURE);
        } else {
          executionLogCallback.saveExecutionLog(
              "\nThere are multiple workloads in the Service Manifests you are deploying. Blue/Green Workflows support a single Deployment, DeploymentConfig (OpenShift) or StatefulSet workload only. To deploy additional workloads in Manifests, annotate them with "
                  + HarnessAnnotations.directApply + ": true",
              ERROR, FAILURE);
        }
        return false;
      }

      k8sBlueGreenHandlerConfig.setPrimaryService(getPrimaryService(k8sBlueGreenHandlerConfig.getResources()));
      k8sBlueGreenHandlerConfig.setStageService(getStageService(k8sBlueGreenHandlerConfig.getResources()));

      if (k8sBlueGreenHandlerConfig.getPrimaryService() == null) {
        List<KubernetesResource> services = getServices(k8sBlueGreenHandlerConfig.getResources());
        if (services.size() == 1) {
          k8sBlueGreenHandlerConfig.setPrimaryService(services.get(0));
          executionLogCallback.saveExecutionLog("Primary Service is "
              + color(k8sBlueGreenHandlerConfig.getPrimaryService().getResourceId().getName(), White, Bold));
        } else if (services.size() == 0) {
          throw new KubernetesYamlException(
              "No service is found in manifests. Service is required for BlueGreen deployments."
              + " Add at least one service manifest. Two services [i.e. primary and stage] can be specified with annotations "
              + HarnessAnnotations.primaryService + " and " + HarnessAnnotations.stageService);
        } else {
          throw new KubernetesYamlException(
              "Could not locate a Primary Service in Manifests. Primary and Stage services should be annotated with "
              + HarnessAnnotations.primaryService + " and " + HarnessAnnotations.stageService);
        }
      }

      if (k8sBlueGreenHandlerConfig.getStageService() == null) {
        // create a clone
        k8sBlueGreenHandlerConfig.setStageService(
            getKubernetesResourceFromSpec(k8sBlueGreenHandlerConfig.getPrimaryService().getSpec()));
        k8sBlueGreenHandlerConfig.getStageService().appendSuffixInName("-stage");
        k8sBlueGreenHandlerConfig.getResources().add(k8sBlueGreenHandlerConfig.getStageService());
        executionLogCallback.saveExecutionLog(format("Created Stage service [%s] using Spec from Primary Service [%s]",
            k8sBlueGreenHandlerConfig.getStageService().getResourceId().getName(),
            k8sBlueGreenHandlerConfig.getPrimaryService().getResourceId().getName()));
      }

      try {
        k8sBlueGreenHandlerConfig.setPrimaryColor(
            k8sBGBaseHandler.getPrimaryColor(k8sBlueGreenHandlerConfig.getPrimaryService(),
                k8sBlueGreenHandlerConfig.getKubernetesConfig(), executionLogCallback));
        V1Service stageServiceInCluster =
            kubernetesContainerService.getService(k8sBlueGreenHandlerConfig.getKubernetesConfig(),
                k8sBlueGreenHandlerConfig.getStageService().getResourceId().getName());
        if (stageServiceInCluster == null) {
          executionLogCallback.saveExecutionLog("Stage Service ["
              + k8sBlueGreenHandlerConfig.getStageService().getResourceId().getName() + "] not found in cluster.");
        }

        if (k8sBlueGreenHandlerConfig.getPrimaryColor() == null) {
          executionLogCallback.saveExecutionLog(
              format(
                  "Found conflicting service [%s] in the cluster. For blue/green deployment, the label [harness.io/color] is required in service selector. Delete this existing service to proceed",
                  k8sBlueGreenHandlerConfig.getPrimaryService().getResourceId().getName()),
              ERROR, FAILURE);
          return false;
        }

        k8sBlueGreenHandlerConfig.setStageColor(
            k8sBGBaseHandler.getInverseColor(k8sBlueGreenHandlerConfig.getPrimaryColor()));

      } catch (Exception e) {
        log.error("Exception:", e);
        executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR, FAILURE);
        return false;
      }

      IK8sRelease release =
          releaseHandler.createRelease(k8sBlueGreenHandlerConfig.getReleaseName(), currentReleaseNumber);

      k8sBlueGreenHandlerConfig.setPrePruningInfo(k8sBGBaseHandler.cleanupForBlueGreen(k8sDelegateTaskParams,
          k8sBlueGreenHandlerConfig.getReleaseHistory(), executionLogCallback,
          k8sBlueGreenHandlerConfig.getPrimaryColor(), k8sBlueGreenHandlerConfig.getStageColor(), currentReleaseNumber,
          k8sBlueGreenHandlerConfig.getClient(), k8sBlueGreenHandlerConfig.getKubernetesConfig(),
          k8sBlueGreenHandlerConfig.getReleaseName(), k8sBlueGreenHandlerConfig.isUseDeclarativeRollback()));

      executionLogCallback.saveExecutionLog("\nCurrent release number is: " + currentReleaseNumber);

      if (isNotTrue(k8sBlueGreenDeployTaskParameters.getSkipVersioningForAllK8sObjects()) && !useDeclarativeRollback) {
        executionLogCallback.saveExecutionLog("\nVersioning resources.");
        addRevisionNumber(k8sBlueGreenHandlerConfig.getResources(), currentReleaseNumber);
      }

      if (useDeclarativeRollback) {
        executionLogCallback.saveExecutionLog(
            format("Adding stage color [%s] as a suffix to Configmap and Secret names.",
                k8sBlueGreenHandlerConfig.getStageColor()));
        addSuffixToConfigmapsAndSecrets(
            k8sBlueGreenHandlerConfig.getResources(), k8sBlueGreenHandlerConfig.getStageColor(), executionLogCallback);
      }

      KubernetesResource managedWorkload = getManagedWorkload(k8sBlueGreenHandlerConfig.getResources());
      managedWorkload.appendSuffixInName('-' + k8sBlueGreenHandlerConfig.getStageColor());
      managedWorkload.addLabelsInPodSpec(ImmutableMap.of(HarnessLabels.releaseName,
          k8sBlueGreenHandlerConfig.getReleaseName(), HarnessLabels.color, k8sBlueGreenHandlerConfig.getStageColor()));
      managedWorkload.addLabelsInDeploymentSelector(
          ImmutableMap.of(HarnessLabels.color, k8sBlueGreenHandlerConfig.getStageColor()));
      k8sBlueGreenHandlerConfig.setManagedWorkload(managedWorkload);

      k8sBlueGreenHandlerConfig.getPrimaryService().addColorSelectorInService(
          k8sBlueGreenHandlerConfig.getPrimaryColor());
      k8sBlueGreenHandlerConfig.getStageService().addColorSelectorInService(k8sBlueGreenHandlerConfig.getStageColor());

      executionLogCallback.saveExecutionLog("\nWorkload to deploy is: "
          + color(managedWorkload.getResourceId().kindNameRef(),
              k8sBGBaseHandler.getLogColor(k8sBlueGreenHandlerConfig.getStageColor()), Bold));

      release.setReleaseData(
          k8sBlueGreenHandlerConfig.getResources(), k8sBlueGreenDeployTaskParameters.isPruningEnabled());
      if (useDeclarativeRollback) {
        // store color in new release secret's labels
        K8sReleaseSecretHelper.putLabelsItem(
            (K8sRelease) release, RELEASE_SECRET_RELEASE_COLOR_KEY, k8sBlueGreenHandlerConfig.getStageColor());
      }

      k8sBlueGreenHandlerConfig.setCurrentRelease(release);
      if (!useDeclarativeRollback) {
        ((K8SLegacyReleaseHistory) releaseHistory)
            .getReleaseHistory()
            .addReleaseToReleaseHistory((K8sLegacyRelease) release);
      }

    } catch (Exception e) {
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR, FAILURE);
      return false;
    }
    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return true;
  }

  private K8sTaskExecutionResponse getFailureResponse(String errorMessage) {
    K8sBlueGreenDeployResponse k8sBlueGreenDeployResponse = K8sBlueGreenDeployResponse.builder().build();
    return K8sTaskExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .k8sTaskResponse(k8sBlueGreenDeployResponse)
        .errorMessage(errorMessage)
        .build();
  }
}
