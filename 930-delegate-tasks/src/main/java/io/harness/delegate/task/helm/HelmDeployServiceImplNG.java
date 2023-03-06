/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.CUSTOM_REMOTE;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GCS_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GIT;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.HARNESS;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.HTTP_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.OCI_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.S3_HELM;
import static io.harness.delegate.clienttools.ClientTool.OC;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HELM_CHART_ERROR_EXPLANATION;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HELM_CHART_EXCEPTION;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HELM_CHART_REGEX;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HELM_CUSTOM_EXCEPTION_HINT;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.getChartDirectory;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.helm.HelmCommandType.RELEASE_HISTORY;
import static io.harness.helm.HelmConstants.CHARTS_YAML_KEY;
import static io.harness.helm.HelmConstants.DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.delegatetasks.helm.HelmTaskHelper.copyManifestFilesToWorkingDir;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.concurrent.HTimeLimiter;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.CustomRemoteStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.exception.HelmNGException;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.localstore.ManifestFiles;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GitOperationException;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmClient;
import io.harness.helm.HelmClientImpl.HelmCliResponse;
import io.harness.helm.HelmCommandResponseMapper;
import io.harness.helm.HelmCommandType;
import io.harness.helm.HelmConstants;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.helpers.ext.helm.response.ReleaseInfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class HelmDeployServiceImplNG implements HelmDeployServiceNG {
  private static final Set<StoreDelegateConfigType> HELM_SUPPORTED_STORE_TYPES =
      ImmutableSet.of(GIT, HTTP_HELM, S3_HELM, GCS_HELM, OCI_HELM, CUSTOM_REMOTE, HARNESS);
  private static final String SUB_CHARTS_FOLDER = "charts";
  @Inject private HelmClient helmClient;
  @Inject private HelmTaskHelperBase helmTaskHelperBase;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  private KubernetesConfig kubernetesConfig;
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;
  @Inject private KubernetesContainerService kubernetesContainerService;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private NGGitService ngGitService;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Inject private SecretDecryptionService secretDecryptionService;
  private ILogStreamingTaskClient logStreamingTaskClient;
  @Inject private TimeLimiter timeLimiter;
  @Inject private CustomManifestFetchTaskHelper customManifestFetchTaskHelper;
  public static final String TIMED_OUT_IN_STEADY_STATE = "Timed out waiting for controller to reach in steady state";
  public static final String InstallUpgrade = "Install / Upgrade";
  public static final String Rollback = "Rollback";
  public static final String WaitForSteadyState = "Wait For Steady State";
  public static final String WrapUp = "Wrap Up";
  private static final String NON_DIGITS_REGEX = "\\D+";
  private static final int VERSION_LENGTH = 3;
  private static final int KUBERNETESS_116_VERSION = 116;
  @Override
  public void setLogStreamingClient(ILogStreamingTaskClient iLogStreamingTaskClient) {
    this.logStreamingTaskClient = iLogStreamingTaskClient;
  }

  @Override
  public HelmCommandResponseNG deploy(HelmInstallCommandRequestNG commandRequest) throws IOException {
    LogCallback logCallback = commandRequest.getLogCallback();
    HelmChartInfo helmChartInfo = null;
    int prevVersion = -1;
    boolean isInstallUpgrade = false;
    List<KubernetesResource> resources = Collections.emptyList();
    String initWorkingDir = commandRequest.getWorkingDir();
    ReleaseHistory releaseHistory = null;
    try {
      HelmInstallCmdResponseNG commandResponse;
      logCallback.saveExecutionLog(
          "List all existing deployed releases for release name: " + commandRequest.getReleaseName());

      if (HelmVersion.V380.equals(commandRequest.getHelmVersion())) {
        helmTaskHelperBase.revokeReadPermission(commandRequest.getKubeConfigLocation());
      }

      HelmCliResponse helmCliResponse =
          helmClient.releaseHistory(HelmCommandDataMapperNG.getHelmCmdDataNG(commandRequest), true);

      List<ReleaseInfo> releaseInfoList =
          helmTaskHelperBase.parseHelmReleaseCommandOutput(helmCliResponse.getOutput(), RELEASE_HISTORY);

      if (!isEmpty(releaseInfoList)) {
        helmTaskHelperBase.processHelmReleaseHistOutput(
            releaseInfoList.get(releaseInfoList.size() - 1), commandRequest.isIgnoreReleaseHistFailStatus());
      }

      logCallback.saveExecutionLog(helmCliResponse.getOutputWithErrorStream());

      prevVersion = getPrevReleaseVersion(helmCliResponse);

      kubernetesConfig =
          containerDeploymentDelegateBaseHelper.createKubernetesConfig(commandRequest.getK8sInfraDelegateConfig());

      prepareRepoAndCharts(commandRequest, commandRequest.getTimeoutInMillis(), logCallback);

      skipApplyDefaultValuesYaml(commandRequest);

      resources = printHelmChartKubernetesResources(commandRequest);

      List<KubernetesResourceId> workloads = readResources(resources);

      boolean useSteadyStateCheck = useSteadyStateCheck(commandRequest.isK8SteadyStateCheckEnabled(), logCallback);

      if (useSteadyStateCheck) {
        releaseHistory = createNewRelease(commandRequest, workloads, prevVersion);
      }

      helmChartInfo = getHelmChartDetails(commandRequest);

      logCallback = markDoneAndStartNew(commandRequest, logCallback, InstallUpgrade);

      // call listReleases method
      HelmListReleaseResponseNG helmListReleaseResponseNG = listReleases(commandRequest);
      logCallback.saveExecutionLog(helmListReleaseResponseNG.getOutput() + "\n");

      // if list release failed due to unknown exception:
      if (helmListReleaseResponseNG.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE) {
        throw new HelmNGException(prevVersion,
            new HelmClientRuntimeException(
                new HelmClientException(helmListReleaseResponseNG.getOutput(), USER, HelmCliCommandType.LIST_RELEASE)),
            false);
      }

      // list releases cmd passed
      isInstallUpgrade = true;
      if (checkNewHelmInstall(helmListReleaseResponseNG)) {
        // install
        logCallback.saveExecutionLog("No previous deployment found for release. Installing chart");
        commandResponse = HelmCommandResponseMapper.getHelmInstCmdRespNG(
            helmClient.install(HelmCommandDataMapperNG.getHelmCmdDataNG(commandRequest), true));
      }

      else {
        // upgrade
        logCallback.saveExecutionLog("Previous release exists for chart. Upgrading chart");
        commandResponse = HelmCommandResponseMapper.getHelmInstCmdRespNG(
            helmClient.upgrade(HelmCommandDataMapperNG.getHelmCmdDataNG(commandRequest), true));
      }

      logCallback.saveExecutionLog(commandResponse.getOutput());

      commandResponse.setHelmChartInfo(helmChartInfo);
      commandResponse.setPrevReleaseVersion(prevVersion);
      commandResponse.setReleaseName(commandRequest.getReleaseName());

      commandRequest.setPrevReleaseVersion(prevVersion);
      commandRequest.setNewReleaseVersion(prevVersion + 1);

      if (useSteadyStateCheck) {
        saveReleaseHistory(commandRequest, releaseHistory, CommandExecutionStatus.SUCCESS);
      }

      logCallback = markDoneAndStartNew(commandRequest, logCallback, WaitForSteadyState);

      List<ContainerInfo> containerInfos = getContainerInfos(
          commandRequest, workloads, useSteadyStateCheck, logCallback, commandRequest.getTimeoutInMillis());
      if (!useSteadyStateCheck) {
        setReleaseNameForContainers(containerInfos, commandRequest.getReleaseName(), commandRequest.getNamespace());
      }
      commandResponse.setContainerInfoList(containerInfos);
      commandResponse.setHelmVersion(commandRequest.getHelmVersion());

      logCallback = markDoneAndStartNew(commandRequest, logCallback, WrapUp);

      return commandResponse;

    } catch (UncheckedTimeoutException e) {
      logCallback.saveExecutionLog(TIMED_OUT_IN_STEADY_STATE, LogLevel.ERROR);
      if (isInstallUpgrade && useSteadyStateCheck(commandRequest.isK8SteadyStateCheckEnabled(), logCallback)
          && releaseHistory != null) {
        saveReleaseHistory(commandRequest, releaseHistory, CommandExecutionStatus.FAILURE);
      }
      throw new HelmNGException(prevVersion, ExceptionMessageSanitizer.sanitizeException(e), isInstallUpgrade);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      if (isInstallUpgrade && useSteadyStateCheck(commandRequest.isK8SteadyStateCheckEnabled(), logCallback)
          && releaseHistory != null) {
        saveReleaseHistory(commandRequest, releaseHistory, CommandExecutionStatus.FAILURE);
      }

      String exceptionMessage = ExceptionUtils.getMessage(sanitizedException);
      String msg = "Exception in deploying helm chart: " + exceptionMessage;
      logCallback.saveExecutionLog(msg, LogLevel.ERROR);
      throw new HelmNGException(prevVersion, sanitizedException, isInstallUpgrade);
    } finally {
      if (checkIfReleasePurgingNeeded(commandRequest)) {
        logCallback.saveExecutionLog("Deployment failed.");
        deleteAndPurgeHelmRelease(commandRequest, logCallback);
      }
      if (!helmTaskHelperBase.isHelmLocalRepoSet()) {
        cleanUpWorkingDirectory(initWorkingDir);
      }
      if (isNotEmpty(commandRequest.getGcpKeyPath())) {
        deleteDirectoryAndItsContentIfExists(Paths.get(commandRequest.getGcpKeyPath()).getParent().toString());
      }
    }
  }

  public void skipApplyDefaultValuesYaml(HelmInstallCommandRequestNG commandRequest) {
    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        (HelmChartManifestDelegateConfig) commandRequest.getManifestDelegateConfig();
    int index = helmTaskHelperBase.skipDefaultHelmValuesYaml(commandRequest.getWorkingDir(),
        commandRequest.getValuesYamlList(), helmChartManifestDelegateConfig.isSkipApplyHelmDefaultValues(),
        helmChartManifestDelegateConfig.getHelmVersion());
    if (index != -1) {
      List<String> valuesYamlList = commandRequest.getValuesYamlList();
      valuesYamlList.remove(index);
      commandRequest.setValuesYamlList(valuesYamlList);
    }
  }

  private void setReleaseNameForContainers(List<ContainerInfo> containerInfos, String releaseName, String namespace) {
    // if Fabric8() to get containerInfos then setReleaseName and Namespace
    for (ContainerInfo containerInfo : containerInfos) {
      if (containerInfo.getReleaseName() == null) {
        containerInfo.setReleaseName(releaseName);
      }
      if (containerInfo.getNamespace() == null) {
        containerInfo.setNamespace(namespace);
      }
    }
  }

  private int getPrevReleaseVersion(HelmCliResponse helmCliResponse) throws IOException {
    List<ReleaseInfo> releaseInfoList =
        helmTaskHelperBase.parseHelmReleaseCommandOutput(helmCliResponse.getOutput(), RELEASE_HISTORY);
    return isEmpty(releaseInfoList) ? 0
                                    : Integer.parseInt(releaseInfoList.get(releaseInfoList.size() - 1).getRevision());
  }

  private boolean checkNewHelmInstall(HelmListReleaseResponseNG helmListReleaseResponseNG) {
    return isEmpty(helmListReleaseResponseNG.getReleaseInfoList());
  }

  private void cleanUpWorkingDirectory(String workingDir) {
    try {
      if (!StringUtils.isEmpty(workingDir)) {
        deleteDirectoryAndItsContentIfExists(workingDir);
      }
    } catch (IOException e) {
      log.info("Unable to delete working directory: " + workingDir, e);
    }
  }

  void deleteAndPurgeHelmRelease(HelmInstallCommandRequestNG commandRequest, LogCallback logCallback) {
    try {
      String message = "Cleaning up. Deleting the release, freeing it up for later use";
      logCallback.saveExecutionLog(message);

      HelmCliResponse deleteResponse =
          helmClient.deleteHelmRelease(HelmCommandDataMapperNG.getHelmCmdDataNG(commandRequest), true);
      logCallback.saveExecutionLog(deleteResponse.getOutputWithErrorStream());
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      String exceptionMessage = ExceptionUtils.getMessage(sanitizedException);
      String msg = "Helm delete failed: " + exceptionMessage;
      log.error(msg, sanitizedException);
    }
  }

  private boolean checkIfReleasePurgingNeeded(HelmInstallCommandRequestNG commandRequest) {
    HelmListReleaseResponseNG commandResponse = listReleases(commandRequest);
    commandRequest.getLogCallback().saveExecutionLog(commandResponse.getOutput() + "\n");

    if (commandResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      if (isEmpty(commandResponse.getReleaseInfoList())) {
        return false;
      }
      return commandResponse.getReleaseInfoList().stream().anyMatch(releaseInfo
          -> releaseInfo.getRevision().equals("1") && releaseInfo.getStatus().equalsIgnoreCase("failed")
              && releaseInfo.getName().equals(commandRequest.getReleaseName()));
    }

    return false;
  }

  public List<ContainerInfo> getContainerInfos(HelmCommandRequestNG commandRequest,
      List<KubernetesResourceId> workloads, boolean useSteadyStateCheck, LogCallback logCallback, long timeoutInMillis)
      throws Exception {
    return useSteadyStateCheck ? getKubectlContainerInfos(commandRequest, workloads, logCallback, timeoutInMillis)
                               : getFabric8ContainerInfos(commandRequest, logCallback, timeoutInMillis);
  }

  private List<ContainerInfo> getFabric8ContainerInfos(
      HelmCommandRequestNG commandRequest, LogCallback logCallback, long timeoutInMillis) throws Exception {
    List<ContainerInfo> containerInfos = new ArrayList<>();
    LogCallback finalExecutionLogCallback = logCallback;
    HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMillis(timeoutInMillis),
        () -> containerInfos.addAll(fetchContainerInfo(commandRequest, finalExecutionLogCallback, new ArrayList<>())));
    return containerInfos;
  }

  private List<ContainerInfo> getKubectlContainerInfos(HelmCommandRequestNG commandRequest,
      List<KubernetesResourceId> workloads, LogCallback logCallback, long timeoutInMillis) throws Exception {
    Kubectl client = Kubectl.client(k8sGlobalConfigService.getKubectlPath(commandRequest.isUseLatestKubectlVersion()),
        commandRequest.getKubeConfigLocation());
    List<ContainerInfo> containerInfoList = new ArrayList<>();
    final Map<String, List<KubernetesResourceId>> namespacewiseResources =
        workloads.stream().collect(Collectors.groupingBy(KubernetesResourceId::getNamespace));
    boolean success = true;
    for (Map.Entry<String, List<KubernetesResourceId>> entry : namespacewiseResources.entrySet()) {
      if (success) {
        final String namespace = entry.getKey();
        Optional<String> ocPath = setupPathOfOcBinaries(entry.getValue());
        if (ocPath.isPresent()) {
          commandRequest.setOcPath(ocPath.get());
        }
        success = success
            && doStatusCheckAllResourcesForHelm(client, entry.getValue(), commandRequest.getOcPath(),
                commandRequest.getWorkingDir(), namespace, commandRequest.getKubeConfigLocation(), logCallback,
                commandRequest.getGcpKeyPath());
        logCallback.saveExecutionLog(
            format("Status check done with success [%s] for resources in namespace: [%s]", success, namespace));
        String releaseName = commandRequest.getReleaseName();
        List<ContainerInfo> containerInfos =
            k8sTaskHelperBase.getContainerInfos(kubernetesConfig, releaseName, namespace, timeoutInMillis);
        containerInfoList.addAll(containerInfos);
      }
    }
    logCallback.saveExecutionLog(format("Currently running Containers: [%s]", containerInfoList.size()));
    if (success) {
      return containerInfoList;
    } else {
      throw new InvalidRequestException("Steady state check failed", USER);
    }
  }

  private boolean doStatusCheckAllResourcesForHelm(Kubectl client, List<KubernetesResourceId> resourceIds,
      String ocPath, String workingDir, String namespace, String kubeconfigPath, LogCallback executionLogCallback,
      String gcpKeyFilePath) throws Exception {
    return k8sTaskHelperBase.doStatusCheckForAllResources(client, resourceIds,
        K8sDelegateTaskParams.builder()
            .ocPath(ocPath)
            .workingDirectory(workingDir)
            .kubeconfigPath(kubeconfigPath)
            .gcpKeyFilePath(gcpKeyFilePath)
            .build(),
        namespace, executionLogCallback, false, true);
  }

  private Collection<? extends ContainerInfo> fetchContainerInfo(
      HelmCommandRequestNG commandRequest, LogCallback logCallback, List<Pod> existingPods) {
    return containerDeploymentDelegateBaseHelper.getContainerInfosWhenReadyByLabels(
        kubernetesConfig, logCallback, ImmutableMap.of("release", commandRequest.getReleaseName()), existingPods);
  }

  private void saveReleaseHistory(HelmCommandRequestNG commandRequest, ReleaseHistory releaseHistory,
      CommandExecutionStatus commandExecutionStatus) throws IOException {
    K8sLegacyRelease.Status releaseStatus = CommandExecutionStatus.SUCCESS == commandExecutionStatus
        ? IK8sRelease.Status.Succeeded
        : IK8sRelease.Status.Failed;
    releaseHistory.setReleaseStatus(releaseStatus);
    k8sTaskHelperBase.saveReleaseHistory(
        kubernetesConfig, commandRequest.getReleaseName(), releaseHistory.getAsYaml(), true);
  }

  private ReleaseHistory createNewRelease(HelmCommandRequestNG commandRequest, List<KubernetesResourceId> workloads,
      Integer newReleaseVersion) throws IOException {
    ReleaseHistory releaseHistory = fetchReleaseHistory(commandRequest, kubernetesConfig);
    releaseHistory.cleanup();
    releaseHistory.createNewRelease(workloads);
    if (newReleaseVersion != null) {
      releaseHistory.setReleaseNumber(newReleaseVersion);
    }
    return releaseHistory;
  }

  private ReleaseHistory fetchReleaseHistory(HelmCommandRequestNG commandRequest, KubernetesConfig kubernetesConfig)
      throws IOException {
    String releaseHistoryData =
        k8sTaskHelperBase.getReleaseHistoryFromSecret(kubernetesConfig, commandRequest.getReleaseName());
    if (StringUtils.isEmpty(releaseHistoryData)) {
      return ReleaseHistory.createNew();
    }
    return ReleaseHistory.createFromData(releaseHistoryData);
  }

  private List<KubernetesResourceId> readResources(List<KubernetesResource> resources) {
    List<KubernetesResource> eligibleWorkloads = ManifestHelper.getEligibleWorkloads(resources);
    return eligibleWorkloads.stream()
        .filter(resource -> resource.getMetadataAnnotationValue(HelmConstants.HELM_HOOK_ANNOTATION) == null)
        .map(KubernetesResource::getResourceId)
        .collect(Collectors.toList());
  }

  private boolean useSteadyStateCheck(boolean isK8sSteadyStateCheckEnabled, LogCallback logCallback) {
    if (!isK8sSteadyStateCheckEnabled) {
      return false;
    }
    String versionAsString = kubernetesContainerService.getVersionAsString(kubernetesConfig);
    logCallback.saveExecutionLog(format("Kubernetes version [%s]", versionAsString));
    int versionMajorMin = Integer.parseInt(escapeNonDigitsAndTruncate(versionAsString));
    return KUBERNETESS_116_VERSION <= versionMajorMin;
  }

  private String escapeNonDigitsAndTruncate(String value) {
    String digits = value.replaceAll(NON_DIGITS_REGEX, EMPTY);
    return digits.length() > VERSION_LENGTH ? digits.substring(0, VERSION_LENGTH) : digits;
  }

  boolean isHelm3(String cliResponse) {
    return isNotEmpty(cliResponse) && cliResponse.toLowerCase().startsWith("v3.");
  }

  @Override
  public HelmCommandResponseNG rollback(HelmRollbackCommandRequestNG commandRequest) throws Exception {
    LogCallback logCallback = commandRequest.getLogCallback();
    kubernetesConfig =
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(commandRequest.getK8sInfraDelegateConfig());
    try {
      logCallback = markDoneAndStartNew(commandRequest, logCallback, Rollback);
      HelmInstallCmdResponseNG commandResponse = HelmCommandResponseMapper.getHelmInstCmdRespNG(
          helmClient.rollback(HelmCommandDataMapperNG.getHelmCmdDataNG(commandRequest), true));
      commandResponse.setPrevReleaseVersion(commandRequest.getPrevReleaseVersion());
      commandResponse.setReleaseName(commandRequest.getReleaseName());
      logCallback.saveExecutionLog(commandResponse.getOutput());
      if (commandResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
        return commandResponse;
      }

      List<KubernetesResourceId> rollbackWorkloads = new ArrayList<>();
      boolean useSteadyStateCheck = useSteadyStateCheck(commandRequest.isK8SteadyStateCheckEnabled(), logCallback);
      if (useSteadyStateCheck) {
        rollbackWorkloads = readResourcesForRollback(commandRequest, commandRequest.getPrevReleaseVersion());
        ReleaseHistory releaseHistory = createNewRelease(commandRequest, rollbackWorkloads, null);
        saveReleaseHistory(commandRequest, releaseHistory, CommandExecutionStatus.FAILURE);
      }
      logCallback = markDoneAndStartNew(commandRequest, logCallback, WaitForSteadyState);

      List<ContainerInfo> containerInfos = getContainerInfos(
          commandRequest, rollbackWorkloads, useSteadyStateCheck, logCallback, commandRequest.getTimeoutInMillis());
      if (!useSteadyStateCheck) {
        setReleaseNameForContainers(containerInfos, commandRequest.getReleaseName(), commandRequest.getNamespace());
      }
      commandResponse.setContainerInfoList(containerInfos);
      commandResponse.setHelmVersion(commandRequest.getHelmVersion());

      HelmChartInfo helmChartInfo = getHelmChartDetails(commandRequest);
      commandResponse.setHelmChartInfo(helmChartInfo);

      logCallback.saveExecutionLog("\nDone", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return commandResponse;
    } catch (UncheckedTimeoutException e) {
      log.error(TIMED_OUT_IN_STEADY_STATE, ExceptionMessageSanitizer.sanitizeException(e));
      logCallback.saveExecutionLog(TIMED_OUT_IN_STEADY_STATE, LogLevel.ERROR);
      throw ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      String msg = sanitizedException.getMessage() == null ? ExceptionUtils.getMessage(sanitizedException)
                                                           : sanitizedException.getMessage();
      log.error("Helm rollback failed: " + msg, sanitizedException);
      logCallback.saveExecutionLog(msg, LogLevel.ERROR);
      throw sanitizedException;

    } finally {
      if (!helmTaskHelperBase.isHelmLocalRepoSet()) {
        cleanUpWorkingDirectory(commandRequest.getWorkingDir());
      }
      if (isNotEmpty(commandRequest.getGcpKeyPath())) {
        deleteDirectoryAndItsContentIfExists(Paths.get(commandRequest.getGcpKeyPath()).getParent().toString());
      }
    }
  }

  private List<KubernetesResourceId> readResourcesForRollback(
      HelmCommandRequestNG commandRequest, Integer prevReleaseVersion) throws IOException {
    ReleaseHistory releaseHistory = fetchReleaseHistory(commandRequest, kubernetesConfig);
    K8sLegacyRelease rollbackRelease = releaseHistory.getRelease(prevReleaseVersion);
    notNullCheck("Unable to find release " + prevReleaseVersion, rollbackRelease);

    if (IK8sRelease.Status.Succeeded != rollbackRelease.getStatus()) {
      throw new InvalidRequestException("Invalid status for release with number " + prevReleaseVersion
          + ". Expected 'Succeeded' status, actual status is '" + rollbackRelease.getStatus() + "'");
    }
    return rollbackRelease.getResources();
  }

  @Override
  public HelmCommandResponseNG ensureHelmCliAndTillerInstalled(HelmCommandRequestNG helmCommandRequest) {
    try {
      return HTimeLimiter.callInterruptible21(
          timeLimiter, Duration.ofMillis(DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS), () -> {
            HelmCliResponse cliResponse = helmClient.getClientAndServerVersion(
                HelmCommandDataMapperNG.getHelmCmdDataNG(helmCommandRequest), true);
            if (cliResponse.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE) {
              throw new InvalidRequestException(cliResponse.getOutputWithErrorStream());
            }

            if (isHelm3(cliResponse.getOutput())) {
              throw NestedExceptionUtils.hintWithExplanationException("Change the version to V3 from V2",
                  "Trying to perform a helm V2 deployment with helm V3",
                  new InvalidRequestException("Location of helm binary used is: "
                      + helmClient.getHelmPath(helmCommandRequest.getHelmVersion())));
            }

            return new HelmCommandResponseNG(CommandExecutionStatus.SUCCESS, cliResponse.getOutputWithErrorStream());
          });
    } catch (UncheckedTimeoutException e) {
      String msg = "Timed out while finding helm client and server version";
      log.error(msg, ExceptionMessageSanitizer.sanitizeException(e));
      throw new InvalidRequestException(msg);
    } catch (Exception e) {
      throw new InvalidRequestException("Some error occurred while finding Helm client and server version",
          ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  @Override
  public HelmListReleaseResponseNG listReleases(HelmInstallCommandRequestNG helmCommandRequest) {
    try {
      HelmCliResponse helmCliResponse =
          helmClient.listReleases(HelmCommandDataMapperNG.getHelmCmdDataNG(helmCommandRequest), true);
      List<ReleaseInfo> releaseInfoList =
          helmTaskHelperBase.parseHelmReleaseCommandOutput(helmCliResponse.getOutput(), HelmCommandType.LIST_RELEASE);
      return HelmListReleaseResponseNG.builder()
          .commandExecutionStatus(helmCliResponse.getCommandExecutionStatus())
          .output(helmCliResponse.getOutputWithErrorStream())
          .releaseInfoList(releaseInfoList)
          .build();
    } catch (HelmClientRuntimeException e) {
      log.error("Helm list releases failed", e);
      throw e;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Helm list releases failed", sanitizedException);
      return HelmListReleaseResponseNG.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .output(ExceptionUtils.getMessage(sanitizedException))
          .build();
    }
  }

  @Override
  public HelmReleaseHistoryCmdResponseNG releaseHistory(HelmReleaseHistoryCommandRequestNG helmCommandRequest) {
    try {
      HelmCliResponse helmCliResponse =
          helmClient.releaseHistory(HelmCommandDataMapperNG.getHelmCmdDataNG(helmCommandRequest), true);
      List<ReleaseInfo> releaseInfoList = helmTaskHelperBase.parseHelmReleaseCommandOutput(
          helmCliResponse.getOutput(), helmCommandRequest.getHelmCommandType());
      return HelmReleaseHistoryCmdResponseNG.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .releaseInfoList(releaseInfoList)
          .build();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Helm list releases failed:", sanitizedException);
      return HelmReleaseHistoryCmdResponseNG.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .output(ExceptionUtils.getMessage(sanitizedException))
          .build();
    }
  }

  @Override
  public HelmCommandResponseNG ensureHelm3Installed(HelmCommandRequestNG commandRequest) {
    String helmPath = helmTaskHelperBase.getHelmPath(commandRequest.getHelmVersion());
    if (isNotBlank(helmPath)) {
      return new HelmCommandResponseNG(CommandExecutionStatus.SUCCESS, format("Helm3 is installed at [%s]", helmPath));
    }
    return new HelmCommandResponseNG(
        CommandExecutionStatus.FAILURE, "Helm3 not installed in the delegate client tools");
  }

  @Override
  public HelmCommandResponseNG ensureHelmInstalled(HelmCommandRequestNG commandRequest) {
    if (commandRequest.getHelmVersion() == null) {
      log.error("Did not expect null value of helmVersion, defaulting to V2");
    }
    return commandRequest.getHelmVersion() == HelmVersion.V2
        ? ensureHelmCliAndTillerInstalled(commandRequest)
        : new HelmCommandResponseNG(
            CommandExecutionStatus.SUCCESS, "Skipping this as we already have capability check for V3");
  }

  @VisibleForTesting
  void prepareRepoAndCharts(HelmCommandRequestNG commandRequest, long timeoutInMillis, LogCallback logCallback)
      throws Exception {
    ManifestDelegateConfig manifestDelegateConfig = commandRequest.getManifestDelegateConfig();
    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        (HelmChartManifestDelegateConfig) manifestDelegateConfig;

    switch (helmChartManifestDelegateConfig.getStoreDelegateConfig().getType()) {
      case HARNESS:
        fetchLocalChartRepo(commandRequest);
        break;
      case CUSTOM_REMOTE:
        fetchCustomSourceManifest(commandRequest, logCallback);
        break;
      case GIT:
        fetchSourceRepo(commandRequest);
        break;
      case HTTP_HELM:
      case S3_HELM:
      case GCS_HELM:
      case OCI_HELM:
        fetchChartRepo(commandRequest, timeoutInMillis);
        break;
      default:
        throw new InvalidRequestException(
            "Unsupported store type: " + helmChartManifestDelegateConfig.getStoreDelegateConfig().getType(), USER);
    }
  }

  void fetchCustomSourceManifest(HelmCommandRequestNG commandRequest, LogCallback logCallback) throws IOException {
    CustomRemoteStoreDelegateConfig storeDelegateConfig =
        (CustomRemoteStoreDelegateConfig) commandRequest.getManifestDelegateConfig().getStoreDelegateConfig();

    String workingDirectory = Paths.get(commandRequest.getWorkingDir()).toString();
    customManifestFetchTaskHelper.downloadAndUnzipCustomSourceManifestFiles(workingDirectory,
        storeDelegateConfig.getCustomManifestSource().getZippedManifestFileId(), commandRequest.getAccountId());

    File file = new File(workingDirectory);
    if (isEmpty(file.list())) {
      throw new InvalidRequestException("No manifest files found under working directory", USER);
    }
    File manifestDirectory = file.listFiles(pathname -> !file.isHidden())[0];

    try {
      copyManifestFilesToWorkingDir(manifestDirectory, new File(workingDirectory));
    } catch (IOException e) {
      String exceptionMessage = ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e));
      String msg = HELM_CHART_EXCEPTION + exceptionMessage;
      log.error(msg, e);
      if (e.getMessage().matches(HELM_CHART_REGEX)) {
        throw NestedExceptionUtils.hintWithExplanationException(HELM_CUSTOM_EXCEPTION_HINT,
            HELM_CHART_ERROR_EXPLANATION, new InvalidRequestException(HELM_CHART_EXCEPTION, e, WingsException.USER));
      } else {
        throw new InvalidRequestException(HELM_CHART_EXCEPTION, e, WingsException.USER);
      }
    }
    commandRequest.setWorkingDir(workingDirectory);
    commandRequest.getLogCallback().saveExecutionLog("Custom source manifest downloaded locally");
  }

  private void fetchSourceRepo(HelmCommandRequestNG commandRequest) {
    StoreDelegateConfig storeDelegateConfig = commandRequest.getManifestDelegateConfig().getStoreDelegateConfig();
    if (!(storeDelegateConfig instanceof GitStoreDelegateConfig)) {
      throw new InvalidArgumentsException(Pair.of("storeDelegateConfig", "Must be instance of GitStoreDelegateConfig"));
    }

    GitStoreDelegateConfig gitStoreDelegateConfig = (GitStoreDelegateConfig) storeDelegateConfig;

    // ToDo What to set here now as we have a list now?
    //    if (isBlank(gitStoreDelegateConfig.getPaths().getFilePath())) {
    //      delegateManifestConfig.getGitFileConfig().setFilePath(StringUtils.EMPTY);
    //    }
    try {
      String manifestFilesDirectory = Paths.get(commandRequest.getWorkingDir(), MANIFEST_FILES_DIR).toString();

      printGitConfigInExecutionLogs(gitStoreDelegateConfig, commandRequest.getLogCallback());

      if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
        commandRequest.getLogCallback().saveExecutionLog("Using optimized file fetch");
        secretDecryptionService.decrypt(
            GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
            gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
            GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
            gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
        scmFetchFilesHelper.downloadFilesUsingScm(
            manifestFilesDirectory, gitStoreDelegateConfig, commandRequest.getLogCallback());
      } else {
        GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
        gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
            gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
        SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
            gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
        ngGitService.downloadFiles(gitStoreDelegateConfig, manifestFilesDirectory, commandRequest.getAccountId(),
            sshSessionConfig, gitConfigDTO);
      }

      commandRequest.getLogCallback().saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      commandRequest.getLogCallback().saveExecutionLog(getManifestFileNamesInLogFormat(manifestFilesDirectory));

      HelmChartManifestDelegateConfig helmChartManifest =
          (HelmChartManifestDelegateConfig) commandRequest.getManifestDelegateConfig();
      if (isNotEmpty(helmChartManifest.getSubChartName())) {
        manifestFilesDirectory =
            Paths.get(manifestFilesDirectory, SUB_CHARTS_FOLDER, helmChartManifest.getSubChartName()).toString();
      }
      commandRequest.setWorkingDir(manifestFilesDirectory);

    } catch (Exception e) {
      String errorMsg = "Failed to download manifest files from git. ";
      throw new GitOperationException(errorMsg, ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  private void fetchChartRepo(HelmCommandRequestNG commandRequestNG, long timeoutInMillis) throws Exception {
    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        (HelmChartManifestDelegateConfig) commandRequestNG.getManifestDelegateConfig();
    String chartName = helmChartManifestDelegateConfig.getChartName();
    String workingDir = Paths.get(commandRequestNG.getWorkingDir()).toString();
    if (helmTaskHelperBase.isHelmLocalRepoSet()) {
      createDirectoryIfDoesNotExist(workingDir);
      waitForDirectoryToBeAccessibleOutOfProcess(workingDir, 10);
      helmTaskHelperBase.populateChartToLocalHelmRepo(
          helmChartManifestDelegateConfig, timeoutInMillis, commandRequestNG.getLogCallback(), workingDir);
    } else {
      downloadFilesFromChartRepo(
          commandRequestNG.getManifestDelegateConfig(), workingDir, commandRequestNG.getLogCallback(), timeoutInMillis);
    }
    if (isNotEmpty(helmChartManifestDelegateConfig.getSubChartName())) {
      String finalWorkingDir = Paths
                                   .get(getChartDirectory(workingDir, chartName), SUB_CHARTS_FOLDER,
                                       helmChartManifestDelegateConfig.getSubChartName())
                                   .toString();
      commandRequestNG.setWorkingDir(finalWorkingDir);
    } else {
      commandRequestNG.setWorkingDir(getChartDirectory(workingDir, chartName));
    }
    commandRequestNG.getLogCallback().saveExecutionLog("Helm Chart Repo checked-out locally");
  }

  private void fetchLocalChartRepo(HelmCommandRequestNG commandRequestNG) {
    String workingDir = Paths.get(commandRequestNG.getWorkingDir()).toString();
    LocalFileStoreDelegateConfig localFileStoreDelegateConfig =
        (LocalFileStoreDelegateConfig) commandRequestNG.getManifestDelegateConfig().getStoreDelegateConfig();
    List<ManifestFiles> ManifestFileList = localFileStoreDelegateConfig.getManifestFiles();
    if (isNotEmpty(localFileStoreDelegateConfig.getManifestType())
        && isNotEmpty(localFileStoreDelegateConfig.getManifestIdentifier())) {
      commandRequestNG.getLogCallback().saveExecutionLog("\n"
          + color(format("Fetching %s files with identifier: %s", localFileStoreDelegateConfig.getManifestType(),
                      localFileStoreDelegateConfig.getManifestIdentifier()),
              White, Bold));
      commandRequestNG.getLogCallback().saveExecutionLog(color("Fetching manifest files at path: ", LogColor.White));
    } else {
      commandRequestNG.getLogCallback().saveExecutionLog("\n" + color("Fetching manifest files", White, Bold));
    }
    List<String> scopedFilePathList = localFileStoreDelegateConfig.getFilePaths();
    printFilesFetchedFromHarnessStore(scopedFilePathList, commandRequestNG.getLogCallback());
    commandRequestNG.getLogCallback().saveExecutionLog(
        color(format("%nSuccessfully fetched following files: "), LogColor.White, LogWeight.Bold));
    downloadFilesFromLocalChartRepo(ManifestFileList, workingDir, commandRequestNG.getLogCallback());

    commandRequestNG.setWorkingDir(workingDir);
    commandRequestNG.getLogCallback().saveExecutionLog("Helm Chart Repo checked-out locally");
  }

  private void downloadFilesFromChartRepo(ManifestDelegateConfig manifestDelegateConfig, String destinationDirectory,
      LogCallback logCallback, long timeoutInMillis) {
    if (!(manifestDelegateConfig instanceof HelmChartManifestDelegateConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("manifestDelegateConfig", "Must be instance of HelmChartManifestDelegateConfig"));
    }

    try {
      HelmChartManifestDelegateConfig helmChartManifestConfig =
          (HelmChartManifestDelegateConfig) manifestDelegateConfig;
      logCallback.saveExecutionLog(color(format("%nFetching files from helm chart repo"), White, Bold));
      helmTaskHelperBase.printHelmChartInfoInExecutionLogs(helmChartManifestConfig, logCallback);

      helmTaskHelperBase.initHelm(destinationDirectory, helmChartManifestConfig.getHelmVersion(), timeoutInMillis);

      if (HTTP_HELM == manifestDelegateConfig.getStoreDelegateConfig().getType()) {
        helmTaskHelperBase.downloadChartFilesFromHttpRepo(
            helmChartManifestConfig, destinationDirectory, timeoutInMillis);
      } else if (OCI_HELM == manifestDelegateConfig.getStoreDelegateConfig().getType()) {
        helmTaskHelperBase.downloadChartFilesFromOciRepo(
            helmChartManifestConfig, destinationDirectory, timeoutInMillis);
      } else {
        helmTaskHelperBase.downloadChartFilesUsingChartMuseum(
            helmChartManifestConfig, destinationDirectory, timeoutInMillis);
      }

      logCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      logCallback.saveExecutionLog(getManifestFileNamesInLogFormat(destinationDirectory));
    } catch (HelmClientException e) {
      throw new HelmClientRuntimeException((HelmClientException) ExceptionMessageSanitizer.sanitizeException(e));
    } catch (Exception e) {
      String errorMsg = format("Failed to download manifest files from %s repo. ",
          manifestDelegateConfig.getStoreDelegateConfig().getType());
      throw new HelmClientRuntimeException(
          new HelmClientException(errorMsg, ExceptionMessageSanitizer.sanitizeException(e), HelmCliCommandType.FETCH));
    }
  }

  private void downloadFilesFromLocalChartRepo(
      List<ManifestFiles> ManifestFileList, String destinationDirectory, LogCallback executionLogCallback) {
    String directoryPath = Paths.get(destinationDirectory).toString();

    try {
      for (int i = 0; i < ManifestFileList.size(); i++) {
        ManifestFiles manifestFile = ManifestFileList.get(i);
        if (StringUtils.equals(values_filename, manifestFile.getFileName())) {
          continue;
        }

        Path filePath = Paths.get(directoryPath, manifestFile.getFileName());
        Path parent = filePath.getParent();
        if (parent == null) {
          throw new InvalidRequestException("Failed to create file at path " + filePath.toString());
        }

        createDirectoryIfDoesNotExist(parent.toString());
        FileIo.writeUtf8StringToFile(filePath.toString(), manifestFile.getFileContent());
        executionLogCallback.saveExecutionLog(color(format("%n- %s", manifestFile.getFilePath()), LogColor.White));
      }

    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)),
          ERROR, CommandExecutionStatus.FAILURE);
    }
  }

  public String createDirectory(String directoryBase) throws IOException {
    String workingDirectory = Paths.get(directoryBase).normalize().toAbsolutePath().toString();

    createDirectoryIfDoesNotExist(workingDirectory);
    waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

    return workingDirectory;
  }

  private void printGitConfigInExecutionLogs(
      GitStoreDelegateConfig gitStoreDelegateConfig, LogCallback executionLogCallback) {
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
    if (isNotEmpty(gitStoreDelegateConfig.getManifestType()) && isNotEmpty(gitStoreDelegateConfig.getManifestId())) {
      executionLogCallback.saveExecutionLog("\n"
          + color(format("Fetching %s files with identifier: %s", gitStoreDelegateConfig.getManifestType(),
                      gitStoreDelegateConfig.getManifestId()),
              White, Bold));
    } else {
      executionLogCallback.saveExecutionLog("\n" + color("Fetching manifest files", White, Bold));
    }
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitConfigDTO.getUrl());

    if (FetchType.BRANCH == gitStoreDelegateConfig.getFetchType()) {
      executionLogCallback.saveExecutionLog("Branch: " + gitStoreDelegateConfig.getBranch());
    } else {
      executionLogCallback.saveExecutionLog("CommitId: " + gitStoreDelegateConfig.getCommitId());
    }

    StringBuilder sb = new StringBuilder(1024);
    sb.append("\nFetching manifest files at path: \n");
    gitStoreDelegateConfig.getPaths().forEach(
        filePath -> sb.append(color(format("- %s", filePath), Gray)).append(System.lineSeparator()));
    executionLogCallback.saveExecutionLog(sb.toString());
  }

  public String getManifestFileNamesInLogFormat(String manifestFilesDirectory) throws IOException {
    Path basePath = Paths.get(manifestFilesDirectory);
    try (Stream<Path> paths = Files.walk(basePath)) {
      return generateTruncatedFileListForLogging(basePath, paths);
    }
  }

  public String generateTruncatedFileListForLogging(Path basePath, Stream<Path> paths) {
    StringBuilder sb = new StringBuilder(1024);
    AtomicInteger filesTraversed = new AtomicInteger(0);
    paths.filter(Files::isRegularFile).forEach(each -> {
      if (filesTraversed.getAndIncrement() <= K8sConstants.FETCH_FILES_DISPLAY_LIMIT) {
        sb.append(color(format("- %s", getRelativePath(each.toString(), basePath.toString())), Gray))
            .append(System.lineSeparator());
      }
    });
    if (filesTraversed.get() > K8sConstants.FETCH_FILES_DISPLAY_LIMIT) {
      sb.append(color(format("- ..%d more", filesTraversed.get() - K8sConstants.FETCH_FILES_DISPLAY_LIMIT), Gray))
          .append(System.lineSeparator());
    }

    return sb.toString();
  }

  public static String getRelativePath(String filePath, String prefixPath) {
    Path fileAbsolutePath = Paths.get(filePath).toAbsolutePath();
    Path prefixAbsolutePath = Paths.get(prefixPath).toAbsolutePath();
    return prefixAbsolutePath.relativize(fileAbsolutePath).toString();
  }

  @VisibleForTesting
  public List<KubernetesResource> printHelmChartKubernetesResources(HelmInstallCommandRequestNG commandRequest)
      throws Exception {
    ManifestDelegateConfig manifestDelegateConfig = commandRequest.getManifestDelegateConfig();

    Optional<StoreDelegateConfigType> storeTypeOpt =
        Optional.ofNullable(manifestDelegateConfig.getStoreDelegateConfig())
            .map(StoreDelegateConfig::getType)
            .filter(Objects::nonNull)
            .filter(HELM_SUPPORTED_STORE_TYPES::contains);

    if (!storeTypeOpt.isPresent()) {
      log.warn("Unsupported store type, storeType: {}",
          manifestDelegateConfig.getStoreDelegateConfig() != null
              ? manifestDelegateConfig.getStoreDelegateConfig().getType()
              : null);
      return Collections.emptyList();
    }

    final String namespace = commandRequest.getNamespace();
    final String workingDir = commandRequest.getWorkingDir();
    final List<String> valueOverrides = commandRequest.getValuesYamlList();
    LogCallback executionLogCallback = commandRequest.getLogCallback();

    log.debug("Printing Helm chart K8S resources, storeType: {}, namespace: {}, workingDir: {}",
        manifestDelegateConfig.getStoreDelegateConfig().getType(), namespace, workingDir);
    List<KubernetesResource> helmKubernetesResources = Collections.emptyList();
    try {
      helmKubernetesResources =
          getKubernetesResourcesFromHelmChart(commandRequest, namespace, workingDir, valueOverrides);
      executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(helmKubernetesResources));

    } catch (InterruptedException e) {
      log.error("Failed to get k8s resources from Helm chart", ExceptionMessageSanitizer.sanitizeException(e));
      Thread.currentThread().interrupt();
      throw new HelmClientRuntimeException(
          new HelmClientException(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e)), USER,
              HelmCliCommandType.RENDER_CHART));
    }
    return helmKubernetesResources;
  }

  private List<KubernetesResource> getKubernetesResourcesFromHelmChart(HelmInstallCommandRequestNG commandRequest,
      String namespace, String workingDir, List<String> valueOverrides) throws Exception {
    log.debug("Getting K8S resources from Helm chart, namespace: {}, chartLocation: {}", namespace, workingDir);

    HelmCommandResponseNG commandResponse = renderHelmChart(commandRequest, namespace, workingDir, valueOverrides);
    List<KubernetesResource> resources = ManifestHelper.processYaml(
        delegateLocalConfigService.replacePlaceholdersWithLocalConfig(commandResponse.getOutput()));
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, namespace);
    return resources;
  }

  @Override
  public HelmCommandResponseNG renderHelmChart(HelmCommandRequestNG commandRequest, String namespace,
      String chartLocation, List<String> valueOverrides) throws Exception {
    LogCallback executionLogCallback = commandRequest.getLogCallback();

    log.debug("Rendering Helm chart, namespace: {}, chartLocation: {}", namespace, chartLocation);

    executionLogCallback.saveExecutionLog("Rendering Helm chart", LogLevel.INFO, CommandExecutionStatus.RUNNING);

    HelmCliResponse cliResponse = helmClient.renderChart(
        HelmCommandDataMapperNG.getHelmCmdDataNG(commandRequest), chartLocation, namespace, valueOverrides, true);

    HelmChartManifestDelegateConfig helmManifest =
        (HelmChartManifestDelegateConfig) commandRequest.getManifestDelegateConfig();

    int index = helmTaskHelperBase.checkForDependencyUpdateFlag(
        helmManifest.getHelmCommandFlag().getValueMap(), cliResponse.getOutput());

    return new HelmCommandResponseNG(cliResponse.getCommandExecutionStatus(),
        index == -1 ? cliResponse.getOutput() : cliResponse.getOutput().substring(index));
  }

  private LogCallback markDoneAndStartNew(
      HelmCommandRequestNG helmCommandRequestNG, LogCallback logCallback, String newName) {
    logCallback.saveExecutionLog("\nDone", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    logCallback = k8sTaskHelperBase.getLogCallback(
        logStreamingTaskClient, newName, true, helmCommandRequestNG.getCommandUnitsProgress());
    helmCommandRequestNG.setLogCallback(logCallback);
    return logCallback;
  }

  private HelmChartInfo getHelmChartDetails(HelmCommandRequestNG commandRequest) throws Exception {
    ManifestDelegateConfig manifestDelegateConfig = commandRequest.getManifestDelegateConfig();

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        (HelmChartManifestDelegateConfig) manifestDelegateConfig;

    HelmChartInfo helmChartInfo = null;

    if (commandRequest instanceof HelmRollbackCommandRequestNG) {
      HelmCliResponse helmHistResponse =
          helmClient.releaseHistory(HelmCommandDataMapperNG.getHelmCmdDataNG(commandRequest), true);
      List<ReleaseInfo> releaseInfoList =
          helmTaskHelperBase.parseHelmReleaseCommandOutput(helmHistResponse.getOutput(), RELEASE_HISTORY);
      String chartName = releaseInfoList.get(releaseInfoList.size() - 1).getChart();
      int index = chartName.lastIndexOf('-');
      String version = chartName.substring(index + 1);
      helmChartInfo = HelmChartInfo.builder().name(chartName).version(version).build();
    }

    else {
      helmChartInfo = helmTaskHelperBase.getHelmChartInfoFromChartsYamlFile(
          Paths.get(commandRequest.getWorkingDir(), CHARTS_YAML_KEY).toString());
    }

    try {
      switch (manifestDelegateConfig.getStoreDelegateConfig().getType()) {
        case GIT:
          GitStoreDelegateConfig gitStoreDelegateConfig =
              (GitStoreDelegateConfig) helmChartManifestDelegateConfig.getStoreDelegateConfig();
          helmChartInfo.setRepoUrl(gitStoreDelegateConfig.getGitConfigDTO().getUrl());
          break;
        case HTTP_HELM:
        case GCS_HELM:
        case S3_HELM:
        case OCI_HELM:
          helmChartInfo.setRepoUrl(getRepoUrlForHelmRepoConfig(helmChartManifestDelegateConfig));
          break;
        case CUSTOM_REMOTE:
        case HARNESS:
          log.debug(
              format("No repo url exists for %s store", manifestDelegateConfig.getStoreDelegateConfig().getType()));
          break;
        default:
          log.warn("Unsupported store type: " + manifestDelegateConfig.getStoreDelegateConfig().getType());
      }

    } catch (Exception e) {
      log.error("Incorrect/Unsupported store type.", ExceptionMessageSanitizer.sanitizeException(e));
    }

    return helmChartInfo;
  }

  public String getRepoUrlForHelmRepoConfig(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig) {
    StoreDelegateConfig helmStoreDelegatConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();

    if (helmStoreDelegatConfig instanceof HttpHelmStoreDelegateConfig) {
      return ((HttpHelmStoreDelegateConfig) helmStoreDelegatConfig).getHttpHelmConnector().getHelmRepoUrl();
    }

    else if (helmStoreDelegatConfig instanceof S3HelmStoreDelegateConfig) {
      S3HelmStoreDelegateConfig amazonS3HelmConfig = (S3HelmStoreDelegateConfig) helmStoreDelegatConfig;
      return new StringBuilder("s3://")
          .append(amazonS3HelmConfig.getBucketName())
          .append("/")
          .append(amazonS3HelmConfig.getFolderPath())
          .toString();
    }

    else if (helmStoreDelegatConfig instanceof GcsHelmStoreDelegateConfig) {
      GcsHelmStoreDelegateConfig gcsHelmConfig = (GcsHelmStoreDelegateConfig) helmStoreDelegatConfig;

      return new StringBuilder("gs://")
          .append(gcsHelmConfig.getBucketName())
          .append("/")
          .append(gcsHelmConfig.getFolderPath())
          .toString();
    } else {
      return null;
    }
  }

  public void printFilesFetchedFromHarnessStore(List<String> scopedFilePathList, LogCallback logCallback) {
    for (String scopedFilePath : scopedFilePathList) {
      logCallback.saveExecutionLog(color(format("- %s", scopedFilePath), LogColor.White));
    }
  }

  private Optional<String> setupPathOfOcBinaries(List<KubernetesResourceId> resourceIds) {
    if (isEmpty(resourceIds)) {
      return Optional.empty();
    }

    for (KubernetesResourceId kubernetesResourceId : resourceIds) {
      if (Kind.DeploymentConfig.name().equals(kubernetesResourceId.getKind())) {
        String ocPath = null;
        try {
          ocPath = InstallUtils.getLatestVersionPath(OC);
        } catch (Exception ex) {
          log.warn(
              "Unable to fetch OC binary path from delegate. Kindly ensure it is configured as env variable." + ex);
        }
        return Optional.ofNullable(ocPath);
      }
    }
    return Optional.empty();
  }
}
