/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.instancesync.NativeHelmInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.K8sContainerToHelmServiceInstanceInfoMapper;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.helm.NativeHelmDeploymentReleaseData;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.NativeHelmDeploymentRelease;
import io.harness.perpetualtask.instancesync.NativeHelmInstanceSyncPerpetualTaskParams;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class NativeHelmInstanceSyncPerpetualTaskExcecutor implements PerpetualTaskExecutor {
  private static final int DEFAULT_STEADY_STATE_TIMEOUT = 5;
  private static final String SUCCESS_RESPONSE_MSG = "success";

  @Inject private KryoSerializer kryoSerializer;
  @Inject private ContainerDeploymentDelegateBaseHelper containerBaseHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    NativeHelmInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), NativeHelmInstanceSyncPerpetualTaskParams.class);

    return executeNativeHelmInstanceSyncTask(taskId, taskParams);
  }

  private PerpetualTaskResponse executeNativeHelmInstanceSyncTask(
      PerpetualTaskId taskId, NativeHelmInstanceSyncPerpetualTaskParams taskParams) {
    List<NativeHelmDeploymentReleaseData> deploymentReleaseDataList =
        fixNativeHelmDeploymentReleaseData(getNativeHelmDeploymentReleaseData(taskParams));

    HelmVersion helmVersion = HelmVersion.fromString(taskParams.getHelmVersion());

    Set<ContainerDetailsRequest> distinctContainerDetailsRequestList =
        getDistinctContainerDetailsRequestList(deploymentReleaseDataList);

    List<ServerInstanceInfo> serverInstanceInfos =
        distinctContainerDetailsRequestList.stream()
            .map(requestData -> getServerInstanceInfoList(requestData, helmVersion))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    log.info("Native Helm Instance sync nInstances: {}, task id: {}",
        isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<NativeHelmDeploymentReleaseData> getNativeHelmDeploymentReleaseData(
      NativeHelmInstanceSyncPerpetualTaskParams taskParams) {
    return taskParams.getDeploymentReleaseListList()
        .stream()
        .map(this::toNativeHelmDeploymentReleaseData)
        .collect(Collectors.toList());
  }

  private NativeHelmDeploymentReleaseData toNativeHelmDeploymentReleaseData(
      NativeHelmDeploymentRelease nativeHelmDeploymentRelease) {
    return NativeHelmDeploymentReleaseData.builder()
        .releaseName(nativeHelmDeploymentRelease.getReleaseName())
        .namespaces(new LinkedHashSet<>(nativeHelmDeploymentRelease.getNamespacesList()))
        .k8sInfraDelegateConfig((K8sInfraDelegateConfig) kryoSerializer.asObject(
            nativeHelmDeploymentRelease.getK8SInfraDelegateConfig().toByteArray()))
        .helmChartInfo(
            (HelmChartInfo) kryoSerializer.asObject(nativeHelmDeploymentRelease.getHelmChartInfo().toByteArray()))
        .build();
  }

  private List<NativeHelmDeploymentReleaseData> fixNativeHelmDeploymentReleaseData(
      List<NativeHelmDeploymentReleaseData> nativeHelmDeploymentReleaseData) {
    return nativeHelmDeploymentReleaseData.stream().map(this::setDefaultNamespaceIfNeeded).collect(Collectors.toList());
  }

  private NativeHelmDeploymentReleaseData setDefaultNamespaceIfNeeded(
      NativeHelmDeploymentReleaseData deploymentReleaseData) {
    if (isEmpty(deploymentReleaseData.getNamespaces()) && isNotBlank(deploymentReleaseData.getReleaseName())) {
      deploymentReleaseData.getNamespaces().add("default");
    }
    return deploymentReleaseData;
  }

  private Set<ContainerDetailsRequest> getDistinctContainerDetailsRequestList(
      List<NativeHelmDeploymentReleaseData> releaseDataList) {
    return releaseDataList.stream()
        .map(this::populateContainerDetailsRequest)
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }

  private List<ContainerDetailsRequest> populateContainerDetailsRequest(NativeHelmDeploymentReleaseData releaseData) {
    containerBaseHelper.decryptK8sInfraDelegateConfig(releaseData.getK8sInfraDelegateConfig());
    KubernetesConfig kubernetesConfig =
        containerBaseHelper.createKubernetesConfig(releaseData.getK8sInfraDelegateConfig(), null);
    LinkedHashSet<String> namespaces = releaseData.getNamespaces();
    String releaseName = releaseData.getReleaseName();
    return namespaces.stream()
        .map(namespace
            -> ContainerDetailsRequest.builder()
                   .kubernetesConfig(kubernetesConfig)
                   .namespace(namespace)
                   .releaseName(releaseName)
                   .helmChartInfo(releaseData.getHelmChartInfo())
                   .build())
        .collect(Collectors.toList());
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(
      ContainerDetailsRequest requestData, HelmVersion helmVersion) {
    long timeoutMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(DEFAULT_STEADY_STATE_TIMEOUT);
    try {
      List<ContainerInfo> containerInfoList = k8sTaskHelperBase.getContainerInfos(
          requestData.getKubernetesConfig(), requestData.getReleaseName(), requestData.getNamespace(), timeoutMillis);
      return K8sContainerToHelmServiceInstanceInfoMapper.toServerInstanceInfoList(
          containerInfoList, requestData.getHelmChartInfo(), helmVersion);
    } catch (Exception ex) {
      log.warn("Unable to get list of server instances, namespace: {}, releaseName: {}", requestData.getNamespace(),
          requestData.getReleaseName(), ex);
      return Collections.emptyList();
    }
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    NativeHelmInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        NativeHelmInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg = format(
          "Failed to publish K8s instance sync result PerpetualTaskId [%s], accountId [%s]", taskId.getId(), accountId);
      log.error(errorMsg + ", serverInstanceInfos: {}", serverInstanceInfos, e);
      return errorMsg;
    }
    return SUCCESS_RESPONSE_MSG;
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }

  @Data
  @Builder
  @EqualsAndHashCode(exclude = {"kubernetesConfig", "helmChartInfo"})
  static class ContainerDetailsRequest {
    private KubernetesConfig kubernetesConfig;
    @NotNull private String namespace;
    @NotNull private String releaseName;
    private HelmChartInfo helmChartInfo;
  }
}
