/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.aws;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.asg.AsgEntityHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.aws.asg.AsgDeploymentReleaseData;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgInstanceSyncRequest;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.AsgDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.AsgDeploymentRelease;
import io.harness.perpetualtask.instancesync.AsgInstanceSyncPerpetualTaskParamsNg;
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
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AsgInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private AsgEntityHelper asgEntityHelper;

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<AsgDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packAsgInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }

  private List<AsgDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(AsgDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toAsgDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private AsgDeploymentReleaseData toAsgDeploymentReleaseData(InfrastructureMappingDTO infrastructureMappingDTO,
      AsgDeploymentInfoDTO deploymentInfoDTO, InfrastructureOutcome infrastructureOutcome) {
    AsgInfraConfig asgInfraConfig = getAsgInfraConfig(infrastructureMappingDTO, infrastructureOutcome);
    return AsgDeploymentReleaseData.builder()
        .asgInfraConfig(asgInfraConfig)
        .asgNameWithoutSuffix(deploymentInfoDTO.getAsgNameWithoutSuffix())
        .executionStrategy(deploymentInfoDTO.getExecutionStrategy())
        .build();
  }

  private AsgInfraConfig getAsgInfraConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return asgEntityHelper.getAsgInfraConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private Any packAsgInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<AsgDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createAsgInstanceSyncPerpetualTaskParamsNg(accountIdentifier, deploymentReleaseData));
  }

  private AsgInstanceSyncPerpetualTaskParamsNg createAsgInstanceSyncPerpetualTaskParamsNg(
      String accountIdentifier, List<AsgDeploymentReleaseData> deploymentReleaseData) {
    return AsgInstanceSyncPerpetualTaskParamsNg.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllAsgDeploymentReleaseList(toAsgDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<AsgDeploymentRelease> toAsgDeploymentReleaseList(List<AsgDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream().map(this::toAsgDeploymentRelease).collect(Collectors.toList());
  }

  private AsgDeploymentRelease toAsgDeploymentRelease(AsgDeploymentReleaseData releaseData) {
    return AsgDeploymentRelease.newBuilder()
        .setAsgNameWithoutSuffix(releaseData.getAsgNameWithoutSuffix())
        .setAsgInfraConfig(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(releaseData.getAsgInfraConfig())))
        .setExecutionStrategy(releaseData.getExecutionStrategy())
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(List<AsgDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<AsgDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    return toAsgInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);
  }

  private AsgInstanceSyncRequest toAsgInstanceSyncRequest(AsgDeploymentReleaseData asgDeploymentReleaseData) {
    return AsgInstanceSyncRequest.builder()
        .asgInfraConfig(asgDeploymentReleaseData.getAsgInfraConfig())
        .asgNameWithoutSuffix(asgDeploymentReleaseData.getAsgNameWithoutSuffix())
        .executionStrategy(asgDeploymentReleaseData.getExecutionStrategy())
        .build();
  }
}
