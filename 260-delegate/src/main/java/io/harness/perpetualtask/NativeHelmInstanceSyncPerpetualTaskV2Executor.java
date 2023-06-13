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
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.InstanceSyncV2Request;
import io.harness.perpetualtask.instancesync.NativeHelmInstanceSyncPerpetualTaskParamsV2;
import io.harness.perpetualtask.instancesync.helm.NativeHelmDeploymentReleaseDetails;
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
public class NativeHelmInstanceSyncPerpetualTaskV2Executor extends AbstractInstanceSyncV2TaskExecutor {
  private static final String NAMESPACE_RELEASE_NAME_KEY_PATTERN = "namespace:%s_releaseName:%s";
  private static final String DEFAULT_NAMESPACE = "default";
  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private K8sInstanceSyncV2Helper k8sInstanceSyncV2Helper;

  @Override
  protected InstanceSyncV2Request createRequest(String perpetualTaskId, PerpetualTaskExecutionParams params) {
    NativeHelmInstanceSyncPerpetualTaskParamsV2 taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), NativeHelmInstanceSyncPerpetualTaskParamsV2.class);
    ConnectorInfoDTO connectorInfoDTO =
        (ConnectorInfoDTO) kryoSerializer.asObject(taskParams.getConnectorInfoDto().toByteArray());
    decryptConnector(connectorInfoDTO,
        (List<EncryptedDataDetail>) kryoSerializer.asObject(taskParams.getEncryptedData().toByteArray()));
    return InstanceSyncV2Request.builder()
        .accountId(taskParams.getAccountId())
        .orgId(taskParams.getOrgId())
        .projectId(taskParams.getProjectId())
        .connector(connectorInfoDTO)
        .perpetualTaskId(perpetualTaskId)
        .build();
  }

  @Override
  protected List<ServerInstanceInfo> retrieveServiceInstances(
      InstanceSyncV2Request instanceSyncV2Request, DeploymentReleaseDetails details) {
    List<ServerInstanceInfo> serverInstanceInfos = new ArrayList<>();

    Set<NativeHelmDeploymentReleaseDetails> nativeHelmDeploymentReleaseDetailsSet =
        details.getDeploymentDetails()
            .stream()
            .map(NativeHelmDeploymentReleaseDetails.class ::cast)
            .map(this::setDefaultNamespaceIfNeeded)
            .collect(Collectors.toSet());

    if (nativeHelmDeploymentReleaseDetailsSet.isEmpty()) {
      log.warn(format("No K8sDeploymentReleaseDetails for Instance Sync perpetual task Id: [%s] and taskInfo Id: [%s]",
          instanceSyncV2Request.getPerpetualTaskId(), details.getTaskInfoId()));
      return emptyList();
    }
    for (NativeHelmDeploymentReleaseDetails nativeHelmDeploymentReleaseDetails :
        nativeHelmDeploymentReleaseDetailsSet) {
      Set<NativeHelmInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest> distinctPodDetailsRequestList =
          getDistinctPodDetailsRequestList(instanceSyncV2Request, nativeHelmDeploymentReleaseDetails);
      serverInstanceInfos.addAll(distinctPodDetailsRequestList.stream()
                                     .map(k8sInstanceSyncV2Helper::getServerInstanceInfoList)
                                     .flatMap(Collection::stream)
                                     .collect(Collectors.toList()));
    }
    return serverInstanceInfos;
  }

  private NativeHelmDeploymentReleaseDetails setDefaultNamespaceIfNeeded(
      NativeHelmDeploymentReleaseDetails releaseDetails) {
    if (isEmpty(releaseDetails.getNamespaces()) && isNotBlank(releaseDetails.getReleaseName())) {
      releaseDetails.getNamespaces().add(DEFAULT_NAMESPACE);
    }
    return releaseDetails;
  }

  private Set<NativeHelmInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest> getDistinctPodDetailsRequestList(
      InstanceSyncV2Request instanceSyncV2Request, NativeHelmDeploymentReleaseDetails releaseDetails) {
    Set<String> distinctNamespaceReleaseNameKeys = new HashSet<>();

    return populatePodDetailsRequest(instanceSyncV2Request.getConnector(), releaseDetails)
        .stream()
        .filter(requestData -> distinctNamespaceReleaseNameKeys.add(generateNamespaceReleaseNameKey(requestData)))
        .collect(Collectors.toSet());
  }

  private List<NativeHelmInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest> populatePodDetailsRequest(
      ConnectorInfoDTO connectorDTO, NativeHelmDeploymentReleaseDetails releaseDetails) {
    HashSet<String> namespaces = new HashSet<>(releaseDetails.getNamespaces());
    String releaseName = releaseDetails.getReleaseName();
    return namespaces.stream()
        .map(namespace
            -> PodDetailsRequest.builder()
                   .kubernetesConfig(k8sInstanceSyncV2Helper.getKubernetesConfig(
                       connectorDTO, releaseDetails.getK8sCloudClusterConfig(), namespace))
                   .namespace(namespace)
                   .releaseName(releaseName)
                   .helmChartInfo(releaseDetails.getHelmChartInfo())
                   .helmVersion(releaseDetails.getHelmVersion())
                   .build())
        .collect(Collectors.toList());
  }

  private String generateNamespaceReleaseNameKey(
      NativeHelmInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest requestData) {
    return format(NAMESPACE_RELEASE_NAME_KEY_PATTERN, requestData.namespace, requestData.releaseName);
  }

  @Data
  @Builder
  static class PodDetailsRequest {
    private KubernetesConfig kubernetesConfig;
    @NotNull private String namespace;
    @NotNull private String releaseName;
    private HelmChartInfo helmChartInfo;
    String helmVersion;
  }
}
