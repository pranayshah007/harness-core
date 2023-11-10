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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.instancesync.K8sInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.K8sPodToServiceInstanceInfoMapper;
import io.harness.delegate.k8s.utils.K8sTaskCleaner;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeploymentReleaseData;
import io.harness.delegate.task.k8s.K8sDeploymentReleaseData.K8sDeploymentReleaseDataBuilder;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskCleanupDTO;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.K8sDeploymentRelease;
import io.harness.perpetualtask.instancesync.K8sInstanceSyncPerpetualTaskParams;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sInstanceSyncPerpetualTaskExecutor implements PerpetualTaskExecutor {
  private static final int DEFAULT_GET_K8S_POD_DETAILS_STEADY_STATE_TIMEOUT = 5;
  private static final String SUCCESS_RESPONSE_MSG = "success";
  private static final String NAMESPACE_RELEASE_NAME_KEY_PATTERN = "namespace:%s_releaseName:%s";
  private static final String DEFAULT_NAMESPACE = "default";

  @Inject private KryoSerializer kryoSerializer;
  @Inject private ContainerDeploymentDelegateBaseHelper containerBaseHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private K8sTaskCleaner k8sTaskCleaner;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the K8s InstanceSync perpetual task executor for task id: {}", taskId);
    K8sInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), K8sInstanceSyncPerpetualTaskParams.class);
    return executeK8sInstanceSyncTask(taskId, taskParams);
  }

  private PerpetualTaskResponse executeK8sInstanceSyncTask(
      PerpetualTaskId taskId, K8sInstanceSyncPerpetualTaskParams taskParams) {
    List<K8sDeploymentReleaseData> deploymentReleaseDataList =
        fixK8sDeploymentReleaseData(getK8sDeploymentReleaseData(taskParams));
    List<PodDetailsRequest> distinctPodDetailsRequestList = getDistinctPodDetailsRequestList(deploymentReleaseDataList);
    List<ServerInstanceInfo> serverInstanceInfos = distinctPodDetailsRequestList.stream()
                                                       .map(this::getServerInstanceInfoList)
                                                       .flatMap(Collection::stream)
                                                       .collect(Collectors.toList());

    log.info("K8s Instance sync nInstances: {}, task id: {}",
        isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<K8sDeploymentReleaseData> getK8sDeploymentReleaseData(K8sInstanceSyncPerpetualTaskParams taskParams) {
    return taskParams.getK8SDeploymentReleaseListList()
        .stream()
        .map(this::toK8sDeploymentReleaseData)
        .collect(Collectors.toList());
  }

  private K8sDeploymentReleaseData toK8sDeploymentReleaseData(K8sDeploymentRelease k8SDeploymentRelease) {
    K8sDeploymentReleaseDataBuilder k8sDeploymentReleaseDataBuilder =
        K8sDeploymentReleaseData.builder()
            .releaseName(k8SDeploymentRelease.getReleaseName())
            .namespaces(new LinkedHashSet<>(k8SDeploymentRelease.getNamespacesList()))
            .k8sInfraDelegateConfig((K8sInfraDelegateConfig) kryoSerializer.asObject(
                k8SDeploymentRelease.getK8SInfraDelegateConfig().toByteArray()));
    if (k8SDeploymentRelease.getHelmChartInfo().toByteArray().length != 0) {
      k8sDeploymentReleaseDataBuilder.helmChartInfo(
          (HelmChartInfo) kryoSerializer.asObject(k8SDeploymentRelease.getHelmChartInfo().toByteArray()));
    }
    return k8sDeploymentReleaseDataBuilder.build();
  }

  private List<K8sDeploymentReleaseData> fixK8sDeploymentReleaseData(
      List<K8sDeploymentReleaseData> k8sDeploymentReleaseData) {
    return k8sDeploymentReleaseData.stream().map(this::setDefaultNamespaceIfNeeded).collect(Collectors.toList());
  }

  private K8sDeploymentReleaseData setDefaultNamespaceIfNeeded(K8sDeploymentReleaseData deploymentReleaseData) {
    if (isEmpty(deploymentReleaseData.getNamespaces()) && isNotBlank(deploymentReleaseData.getReleaseName())) {
      deploymentReleaseData.getNamespaces().add(DEFAULT_NAMESPACE);
    }
    return deploymentReleaseData;
  }

  private List<PodDetailsRequest> getDistinctPodDetailsRequestList(List<K8sDeploymentReleaseData> releaseDataList) {
    Set<String> distinctNamespaceReleaseNameKeys = new HashSet<>();
    return releaseDataList.stream()
        .map(this::populatePodDetailsRequest)
        .flatMap(Collection::stream)
        .filter(requestData -> distinctNamespaceReleaseNameKeys.add(generateNamespaceReleaseNameKey(requestData)))
        .collect(Collectors.toList());
  }

  private List<PodDetailsRequest> populatePodDetailsRequest(K8sDeploymentReleaseData releaseData) {
    containerBaseHelper.decryptK8sInfraDelegateConfig(releaseData.getK8sInfraDelegateConfig());
    KubernetesConfig kubernetesConfig =
        containerBaseHelper.createKubernetesConfig(releaseData.getK8sInfraDelegateConfig(), null);
    LinkedHashSet<String> namespaces = releaseData.getNamespaces();
    String releaseName = releaseData.getReleaseName();
    HelmChartInfo helmChartInfo = releaseData.getHelmChartInfo();
    return namespaces.stream()
        .map(namespace
            -> PodDetailsRequest.builder()
                   .kubernetesConfig(kubernetesConfig)
                   .namespace(namespace)
                   .releaseName(releaseName)
                   .helmChartInfo(helmChartInfo)
                   .cleanupDTO(K8sTaskCleanupDTO.builder()
                                   .infraDelegateConfig(releaseData.getK8sInfraDelegateConfig())
                                   .generatedKubeConfig(kubernetesConfig)
                                   .build())
                   .build())
        .collect(Collectors.toList());
  }

  private String generateNamespaceReleaseNameKey(PodDetailsRequest requestData) {
    return format(NAMESPACE_RELEASE_NAME_KEY_PATTERN, requestData.namespace, requestData.releaseName);
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(PodDetailsRequest requestData) {
    long timeoutMillis =
        K8sTaskHelperBase.getTimeoutMillisFromMinutes(DEFAULT_GET_K8S_POD_DETAILS_STEADY_STATE_TIMEOUT);
    try {
      List<K8sPod> k8sPodList = k8sTaskHelperBase.getPodDetails(
          requestData.getKubernetesConfig(), requestData.getNamespace(), requestData.getReleaseName(), timeoutMillis);
      return K8sPodToServiceInstanceInfoMapper.toServerInstanceInfoList(k8sPodList, requestData.getHelmChartInfo());
    } catch (Exception ex) {
      log.warn("Unable to get list of server instances, namespace: {}, releaseName: {}", requestData.getNamespace(),
          requestData.getReleaseName(), ex);
      return Collections.emptyList();
    } finally {
      k8sTaskCleaner.cleanup(requestData.getCleanupDTO());
    }
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    K8sInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        K8sInstanceSyncPerpetualTaskResponse.builder()
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
  static class PodDetailsRequest {
    private KubernetesConfig kubernetesConfig;
    @NotNull private String namespace;
    @NotNull private String releaseName;
    private HelmChartInfo helmChartInfo;
    private K8sTaskCleanupDTO cleanupDTO;
  }
}
