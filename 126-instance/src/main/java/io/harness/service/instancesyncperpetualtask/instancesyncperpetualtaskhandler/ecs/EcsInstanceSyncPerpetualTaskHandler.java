/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsEntityHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.ecs.EcsDeploymentReleaseData;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.request.EcsInstanceSyncRequest;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.EcsDeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.EcsDeploymentRelease;
import io.harness.perpetualtask.instancesync.EcsInstanceSyncPerpetualTaskParams;
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
public class EcsInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private EcsEntityHelper ecsEntityHelper;
  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<EcsDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packEcsInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
        infrastructureMappingDTO.getAccountIdentifier());
  }

  private List<EcsDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(EcsDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toEcsDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private EcsDeploymentReleaseData toEcsDeploymentReleaseData(InfrastructureMappingDTO infrastructureMappingDTO,
      EcsDeploymentInfoDTO deploymentInfoDTO, InfrastructureOutcome infrastructureOutcome) {
    EcsInfraConfig ecsInfraConfig = getEcsInfraConfig(infrastructureMappingDTO, infrastructureOutcome);
    return EcsDeploymentReleaseData.builder()
        .ecsInfraConfig(ecsInfraConfig)
        .serviceName(deploymentInfoDTO.getServiceName())
        .build();
  }

  private EcsInfraConfig getEcsInfraConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return ecsEntityHelper.getEcsInfraConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private Any packEcsInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<EcsDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createEcsInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }

  private EcsInstanceSyncPerpetualTaskParams createEcsInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<EcsDeploymentReleaseData> deploymentReleaseData) {
    return EcsInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllEcsDeploymentReleaseList(toEcsDeploymentReleaseList(deploymentReleaseData, accountIdentifier))
        .build();
  }

  private List<EcsDeploymentRelease> toEcsDeploymentReleaseList(
      List<EcsDeploymentReleaseData> deploymentReleaseData, String accountIdentifier) {
    return deploymentReleaseData.stream()
        .map(data -> toEcsDeploymentRelease(data, accountIdentifier))
        .collect(Collectors.toList());
  }

  private EcsDeploymentRelease toEcsDeploymentRelease(EcsDeploymentReleaseData releaseData, String accountIdentifier) {
    return EcsDeploymentRelease.newBuilder()
        .setServiceName(releaseData.getServiceName())
        .setEcsInfraConfig(
            ByteString.copyFrom(getKryoSerializer(accountIdentifier).asBytes(releaseData.getEcsInfraConfig())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(List<EcsDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<EcsDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    return toEcsInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);
  }

  private EcsInstanceSyncRequest toEcsInstanceSyncRequest(EcsDeploymentReleaseData ecsDeploymentReleaseData) {
    return EcsInstanceSyncRequest.builder()
        .ecsInfraConfig(ecsDeploymentReleaseData.getEcsInfraConfig())
        .serviceName(ecsDeploymentReleaseData.getServiceName())
        .build();
  }
}
