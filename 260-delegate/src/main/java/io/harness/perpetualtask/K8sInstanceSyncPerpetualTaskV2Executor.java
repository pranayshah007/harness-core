/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.K8sDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.K8sInstanceSyncPerpetualTaskParamsV2;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sInstanceSyncPerpetualTaskV2Executor extends AbstractInstanceSyncV2TaskExecutor {
  private static final String NAMESPACE_RELEASE_NAME_KEY_PATTERN = "namespace:%s_releaseName:%s";
  private static final String DEFAULT_NAMESPACE = "default";

  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private K8sInstanceSyncV2Helper k8sInstanceSyncV2Helper;

  @Override
  protected String getAccountId(PerpetualTaskExecutionParams params) {
    return AnyUtils.unpack(params.getCustomizedParams(), K8sInstanceSyncPerpetualTaskParamsV2.class).getAccountId();
  }

  @Override
  protected String getDeploymentType(ServerInstanceInfo serverInstanceInfos) {
    if (serverInstanceInfos instanceof K8sServerInstanceInfo) {
      return ServiceSpecType.KUBERNETES;
    }
    throw new UnsupportedOperationException(
        format("Unsupported serverInstanceInfos of type : [%s]", serverInstanceInfos.getClass()));
  }

  @Override
  protected List<ServerInstanceInfo> retrieveServiceInstances(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, DeploymentReleaseDetails details) {
    K8sInstanceSyncPerpetualTaskParamsV2 taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), K8sInstanceSyncPerpetualTaskParamsV2.class);
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();
    ConnectorInfoDTO connectorInfoDTO =
        (ConnectorInfoDTO) kryoSerializer.asObject(taskParams.getConnectorInfoDto().toByteArray());

    Set<K8sDeploymentReleaseDetails> k8sDeploymentReleaseDetailsList =
        details.getDeploymentDetailsList()
            .stream()
            .map(deploymentDetails -> AnyUtils.unpack(deploymentDetails, K8sDeploymentReleaseDetails.class))
            .map(this::setDefaultNamespaceIfNeeded)
            .collect(Collectors.toSet());

    if (k8sDeploymentReleaseDetailsList.isEmpty()) {
      log.warn(format("No K8sDeploymentReleaseDetails for Instance Sync perpetual task Id: [%s] and taskInfo Id: [%s]",
          taskId.getId(), details.getTaskInfoId()));
      return emptyList();
    }
    for (K8sDeploymentReleaseDetails k8sDeploymentReleaseDetails : k8sDeploymentReleaseDetailsList) {
      Set<PodDetailsRequest> distinctPodDetailsRequestList =
          getDistinctPodDetailsRequestList(connectorInfoDTO, k8sDeploymentReleaseDetails, taskParams);
      serverInstanceInfos.addAll(distinctPodDetailsRequestList.stream()
                                     .map(k8sInstanceSyncV2Helper::getServerInstanceInfoList)
                                     .flatMap(Collection::stream)
                                     .collect(Collectors.toList()));
    }
    return serverInstanceInfos;
  }

  private K8sDeploymentReleaseDetails setDefaultNamespaceIfNeeded(K8sDeploymentReleaseDetails releaseDetails) {
    if (isEmpty(releaseDetails.getNamespacesList()) && isNotBlank(releaseDetails.getReleaseName())) {
      releaseDetails.getNamespacesList().add(DEFAULT_NAMESPACE);
    }
    return releaseDetails;
  }

  private Set<PodDetailsRequest> getDistinctPodDetailsRequestList(ConnectorInfoDTO connectorDTO,
      K8sDeploymentReleaseDetails releaseDetails, K8sInstanceSyncPerpetualTaskParamsV2 taskParams) {
    Set<String> distinctNamespaceReleaseNameKeys = new HashSet<>();
    List<EncryptedDataDetail> encryptedDataDetails =
        (List<EncryptedDataDetail>) kryoSerializer.asObject(taskParams.getEncryptedData().toByteArray());
    return populatePodDetailsRequest(connectorDTO, releaseDetails, encryptedDataDetails)
        .stream()
        .filter(requestData -> distinctNamespaceReleaseNameKeys.add(generateNamespaceReleaseNameKey(requestData)))
        .collect(Collectors.toSet());
  }

  private List<PodDetailsRequest> populatePodDetailsRequest(ConnectorInfoDTO connectorDTO,
      K8sDeploymentReleaseDetails releaseDetails, List<EncryptedDataDetail> encryptedDataDetails) {
    HashSet<String> namespaces = new HashSet<>(releaseDetails.getNamespacesList());
    String releaseName = releaseDetails.getReleaseName();
    return namespaces.stream()
        .map(namespace
            -> PodDetailsRequest.builder()
                   .kubernetesConfig(k8sInstanceSyncV2Helper.getKubernetesConfig(
                       connectorDTO, releaseDetails, namespace, encryptedDataDetails))
                   .namespace(namespace)
                   .releaseName(releaseName)
                   .build())
        .collect(Collectors.toList());
  }

  private String generateNamespaceReleaseNameKey(PodDetailsRequest requestData) {
    return format(NAMESPACE_RELEASE_NAME_KEY_PATTERN, requestData.namespace, requestData.releaseName);
  }

  @Data
  @Builder
  static class PodDetailsRequest {
    private KubernetesConfig kubernetesConfig;
    @NotNull private String namespace;
    @NotNull private String releaseName;
  }
}
