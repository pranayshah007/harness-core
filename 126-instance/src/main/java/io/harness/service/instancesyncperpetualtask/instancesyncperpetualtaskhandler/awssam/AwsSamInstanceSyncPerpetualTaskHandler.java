/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.awssam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.awssam.AwsSamEntityHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.awssam.AwsSamDeploymentReleaseData;
import io.harness.delegate.task.awssam.AwsSamInfraConfig;
import io.harness.delegate.task.awssam.request.AwsSamInstanceSyncRequest;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.AwsSamDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.AwsSamDeploymentRelease;
import io.harness.perpetualtask.instancesync.AwsSamInstanceSyncPerpetualTaskParams;
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
public class AwsSamInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject AwsSamEntityHelper awsSamEntityHelper;

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<AwsSamDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packAwsSamInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }

  private List<AwsSamDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(AwsSamDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toAwsSamDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private AwsSamDeploymentReleaseData toAwsSamDeploymentReleaseData(InfrastructureMappingDTO infrastructureMappingDTO,
      AwsSamDeploymentInfoDTO deploymentInfoDTO, InfrastructureOutcome infrastructureOutcome) {
    AwsSamInfraConfig awsSamInfraConfig = getAwsSamInfraConfig(infrastructureMappingDTO, infrastructureOutcome);
    return AwsSamDeploymentReleaseData.builder()
        .awsSamInfraConfig(awsSamInfraConfig)
        .functions(deploymentInfoDTO.getFunctions())
        .region(deploymentInfoDTO.getRegion())
        .build();
  }

  private AwsSamInfraConfig getAwsSamInfraConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return awsSamEntityHelper.getAwsSamInfraConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private Any packAwsSamInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<AwsSamDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createAwsSamInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }

  private AwsSamInstanceSyncPerpetualTaskParams createAwsSamInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<AwsSamDeploymentReleaseData> deploymentReleaseData) {
    return AwsSamInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllAwsSamDeploymentReleaseList(toAwsSamDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<AwsSamDeploymentRelease> toAwsSamDeploymentReleaseList(
      List<AwsSamDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream().map(this::toAwsSamDeploymentRelease).collect(Collectors.toList());
  }

  private AwsSamDeploymentRelease toAwsSamDeploymentRelease(AwsSamDeploymentReleaseData releaseData) {
    return AwsSamDeploymentRelease.newBuilder()
        .addAllFunctions(releaseData.getFunctions())
        .setRegion(releaseData.getRegion())
        .setAwsSamInfraConfig(ByteString.copyFrom(kryoSerializer.asBytes(releaseData.getAwsSamInfraConfig())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(
      List<AwsSamDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<AwsSamDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    return toAwsSamInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);
  }

  private AwsSamInstanceSyncRequest toAwsSamInstanceSyncRequest(
      AwsSamDeploymentReleaseData awsSamDeploymentReleaseData) {
    return AwsSamInstanceSyncRequest.builder()
        .awsSamInfraConfig(awsSamDeploymentReleaseData.getAwsSamInfraConfig())
        .functions(awsSamDeploymentReleaseData.getFunctions())
        .build();
  }
}
