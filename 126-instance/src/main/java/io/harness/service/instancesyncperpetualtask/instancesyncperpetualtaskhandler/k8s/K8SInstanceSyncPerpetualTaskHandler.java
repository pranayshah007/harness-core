/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.k8s;

import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AZURE;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_GCP;

import static java.lang.String.format;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.k8s.K8sDeploymentReleaseData;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInstanceSyncRequest;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.K8sDeploymentRelease;
import io.harness.perpetualtask.instancesync.K8sInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.K8sInstanceSyncPerpetualTaskParamsV2;
import io.harness.remote.client.CGRestUtils;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class K8SInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private AccountClient accountClient;

  private static final String K8S_INSTANCE_SYNC_COMMAND_NAME = "Instance Sync";
  private static final int DEFAULT_TIMEOUT_IN_MIN = 10;
  private static final String DEFAULT_NAMESPACE = "default";

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructure,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<K8sDeploymentReleaseData> deploymentReleaseList =
        populateDeploymentReleaseList(infrastructure, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack =
        packK8sInstanceSyncPerpetualTaskParams(infrastructure.getAccountIdentifier(), deploymentReleaseList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier(),
        infrastructure.getAccountIdentifier());
  }

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundleForV2(
      InfrastructureMappingDTO infrastructureMappingDTO, ConnectorInfoDTO connectorInfoDTO) {
    Any perpetualTaskPack = packK8sInstanceSyncPerpetualTaskV2Params(infrastructureMappingDTO, connectorInfoDTO);

    List<ExecutionCapability> executionCapabilities =
        getExecutionCapabilitiesV2(connectorInfoDTO, infrastructureMappingDTO);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier(),
        infrastructureMappingDTO.getAccountIdentifier());
  }

  private List<K8sDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(K8sDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toK8sDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private K8sDeploymentReleaseData toK8sDeploymentReleaseData(InfrastructureMappingDTO infrastructureMappingDTO,
      K8sDeploymentInfoDTO deploymentInfoDTO, InfrastructureOutcome infrastructureOutcome) {
    K8sInfraDelegateConfig k8sInfraDelegateConfig =
        getK8sInfraDelegateConfig(infrastructureMappingDTO, infrastructureOutcome);
    return K8sDeploymentReleaseData.builder()
        .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
        .namespaces(deploymentInfoDTO.getNamespaces())
        .releaseName(deploymentInfoDTO.getReleaseName())
        .build();
  }

  private K8sInfraDelegateConfig getK8sInfraDelegateConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return k8sEntityHelper.getK8sInfraDelegateConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private InfrastructureOutcome getInfrastructureOutcome(
      String releaseName, String infrastructureKind, String connectorRef) {
    switch (infrastructureKind) {
      case KUBERNETES_DIRECT:
        return K8sDirectInfrastructureOutcome.builder()
            .releaseName(releaseName)
            .connectorRef(connectorRef)
            .namespace(DEFAULT_NAMESPACE)
            .build();
      case KUBERNETES_GCP:
        return K8sGcpInfrastructureOutcome.builder()
            .releaseName(releaseName)
            .connectorRef(connectorRef)
            .namespace(DEFAULT_NAMESPACE)
            .build();
      case KUBERNETES_AZURE:
        return K8sAzureInfrastructureOutcome.builder()
            .releaseName(releaseName)
            .connectorRef(connectorRef)
            .namespace(DEFAULT_NAMESPACE)
            .build();
      default:
        throw new UnsupportedOperationException(
            format("Unsupported outcome for infrastructure kind: [%s]", infrastructureKind));
    }
  }

  private Any packK8sInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<K8sDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createK8sInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }
  private Any packK8sInstanceSyncPerpetualTaskV2Params(
      InfrastructureMappingDTO infrastructureMappingDTO, ConnectorInfoDTO connectorInfoDTO) {
    return Any.pack(createK8sInstanceSyncPerpetualTaskV2Params(infrastructureMappingDTO, connectorInfoDTO));
  }

  private K8sInstanceSyncPerpetualTaskParamsV2 createK8sInstanceSyncPerpetualTaskV2Params(
      InfrastructureMappingDTO infrastructureMappingDTO, ConnectorInfoDTO connectorInfoDTO) {
    return K8sInstanceSyncPerpetualTaskParamsV2.newBuilder()
        .setAccountId(infrastructureMappingDTO.getAccountIdentifier())
        .setOrgId(connectorInfoDTO.getOrgIdentifier())
        .setProjectId(connectorInfoDTO.getProjectIdentifier())
        .setConnectorInfoDto(ByteString.copyFrom(
            getKryoSerializer(infrastructureMappingDTO.getAccountIdentifier()).asBytes(connectorInfoDTO)))
        .build();
  }

  private K8sInstanceSyncPerpetualTaskParams createK8sInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<K8sDeploymentReleaseData> deploymentReleaseData) {
    return K8sInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllK8SDeploymentReleaseList(toK8sDeploymentReleaseList(deploymentReleaseData, accountIdentifier))
        .build();
  }

  private List<K8sDeploymentRelease> toK8sDeploymentReleaseList(
      List<K8sDeploymentReleaseData> deploymentReleaseData, String accountIdentifier) {
    return deploymentReleaseData.stream()
        .map(data -> toK8sDeploymentRelease(data, accountIdentifier))
        .collect(Collectors.toList());
  }

  private K8sDeploymentRelease toK8sDeploymentRelease(K8sDeploymentReleaseData releaseData, String accountIdentifier) {
    return K8sDeploymentRelease.newBuilder()
        .setReleaseName(releaseData.getReleaseName())
        .addAllNamespaces(releaseData.getNamespaces())
        .setK8SInfraDelegateConfig(
            ByteString.copyFrom(getKryoSerializer(accountIdentifier).asBytes(releaseData.getK8sInfraDelegateConfig())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(List<K8sDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<K8sDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }

    return toK8sInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);
  }

  private List<ExecutionCapability> getExecutionCapabilitiesV2(
      ConnectorInfoDTO connectorInfoDTO, InfrastructureMappingDTO infrastructureMappingDTO) {
    if (connectorInfoDTO == null) {
      return Collections.emptyList();
    }

    return K8sInstanceSyncUtils.fetchRequiredK8sExecutionCapabilities(infrastructureMappingDTO,
        connectorInfoDTO.getConnectorConfig(), null,
        CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(
            FeatureName.CDS_K8S_SOCKET_CAPABILITY_CHECK_NG.name(), infrastructureMappingDTO.getAccountIdentifier())));
  }

  private K8sInstanceSyncRequest toK8sInstanceSyncRequest(K8sDeploymentReleaseData k8sDeploymentReleaseData) {
    return K8sInstanceSyncRequest.builder()
        .taskType(K8sTaskType.INSTANCE_SYNC)
        .timeoutIntervalInMin(DEFAULT_TIMEOUT_IN_MIN)
        .releaseName(k8sDeploymentReleaseData.getReleaseName())
        .k8sInfraDelegateConfig(k8sDeploymentReleaseData.getK8sInfraDelegateConfig())
        .commandName(K8S_INSTANCE_SYNC_COMMAND_NAME)
        .namespace(findFirstNamespace(k8sDeploymentReleaseData))
        .build();
  }

  private String findFirstNamespace(K8sDeploymentReleaseData k8sDeploymentReleaseData) {
    return k8sDeploymentReleaseData.getNamespaces().stream().findFirst().orElseThrow(
        () -> new InvalidRequestException("Not found namespace for K8SInstanceSyncPerpetualTask capability check"));
  }
}
