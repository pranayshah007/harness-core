/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.helm;

import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.task.helm.HelmInstanceSyncRequest;
import io.harness.delegate.task.helm.NativeHelmDeploymentReleaseData;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.NativeHelmDeploymentInfoDTO;
import io.harness.k8s.model.HelmVersion;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.NativeHelmDeploymentRelease;
import io.harness.perpetualtask.instancesync.NativeHelmInstanceSyncPerpetualTaskParams;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class NativeHelmInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private K8sEntityHelper k8sEntityHelper;
  private static final String HELM_INSTANCE_SYNC_COMMAND_NAME = "Instance Sync";

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructure,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<NativeHelmDeploymentReleaseData> deploymentReleaseList =
        populateDeploymentReleaseList(infrastructure, deploymentInfoDTOList, infrastructureOutcome);

    HelmVersion helmVersion = ((NativeHelmDeploymentInfoDTO) deploymentInfoDTOList.get(0)).getHelmVersion();
    Any perpetualTaskPack = packNativeHelmInstanceSyncPerpetualTaskParams(
        infrastructure.getAccountIdentifier(), deploymentReleaseList, helmVersion);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseList, helmVersion);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier());
  }

  private List<NativeHelmDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(NativeHelmDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toNativeHelmDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private NativeHelmDeploymentReleaseData toNativeHelmDeploymentReleaseData(
      InfrastructureMappingDTO infrastructureMappingDTO, NativeHelmDeploymentInfoDTO deploymentInfoDTO,
      InfrastructureOutcome infrastructureOutcome) {
    K8sInfraDelegateConfig k8sInfraDelegateConfig =
        getK8sInfraDelegateConfig(infrastructureMappingDTO, infrastructureOutcome);
    return NativeHelmDeploymentReleaseData.builder()
        .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
        .namespaces(deploymentInfoDTO.getNamespaces())
        .releaseName(deploymentInfoDTO.getReleaseName())
        .helmChartInfo(deploymentInfoDTO.getHelmChartInfo())
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

  private Any packNativeHelmInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<NativeHelmDeploymentReleaseData> deploymentReleaseData, HelmVersion helmVersion) {
    return Any.pack(
        createNativeHelmInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData, helmVersion));
  }

  private NativeHelmInstanceSyncPerpetualTaskParams createNativeHelmInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<NativeHelmDeploymentReleaseData> deploymentReleaseData, HelmVersion helmVersion) {
    return NativeHelmInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .setHelmVersion(helmVersion.toString())
        .addAllDeploymentReleaseList(toNativeHelmDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<NativeHelmDeploymentRelease> toNativeHelmDeploymentReleaseList(
      List<NativeHelmDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream().map(this::toNativeHelmDeploymentRelease).collect(Collectors.toList());
  }

  private NativeHelmDeploymentRelease toNativeHelmDeploymentRelease(NativeHelmDeploymentReleaseData releaseData) {
    return NativeHelmDeploymentRelease.newBuilder()
        .setReleaseName(releaseData.getReleaseName())
        .addAllNamespaces(releaseData.getNamespaces())
        .setK8SInfraDelegateConfig(
            ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(releaseData.getK8sInfraDelegateConfig())))
        .setHelmChartInfo(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(releaseData.getHelmChartInfo())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(
      List<NativeHelmDeploymentReleaseData> deploymentReleaseDataList, HelmVersion helmVersion) {
    Optional<NativeHelmDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    List<ExecutionCapability> capabilities =
        toNativeHelmInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);

    capabilities.add(HelmInstallationCapability.builder()
                         .version(helmVersion)
                         .criteria(String.format("Helm %s Installed", helmVersion))
                         .build());

    return capabilities;
  }

  private HelmInstanceSyncRequest toNativeHelmInstanceSyncRequest(
      NativeHelmDeploymentReleaseData deploymentReleaseData) {
    return HelmInstanceSyncRequest.builder()
        .k8sInfraDelegateConfig(deploymentReleaseData.getK8sInfraDelegateConfig())
        .commandName(HELM_INSTANCE_SYNC_COMMAND_NAME)
        .build();
  }
}
