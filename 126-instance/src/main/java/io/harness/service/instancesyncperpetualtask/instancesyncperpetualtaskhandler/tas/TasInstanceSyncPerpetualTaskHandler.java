/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.tas;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.tas.TasEntityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.pcf.TasDeploymentReleaseData;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequestNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.TasDeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.TasDeploymentRelease;
import io.harness.perpetualtask.instancesync.TasInstanceSyncPerpetualTaskParams;
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
public class TasInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private TasEntityHelper tasEntityHelper;
  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<TasDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packTasInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }

  private List<TasDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(TasDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toTasDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private TasDeploymentReleaseData toTasDeploymentReleaseData(InfrastructureMappingDTO infrastructureMappingDTO,
      TasDeploymentInfoDTO deploymentInfoDTO, InfrastructureOutcome infrastructureOutcome) {
    TasInfraConfig tasInfraConfig = getTasInfraConfig(infrastructureMappingDTO, infrastructureOutcome);
    return TasDeploymentReleaseData.builder()
        .tasInfraConfig(tasInfraConfig)
        .applicationName(deploymentInfoDTO.getApplicationName())
        .build();
  }

  private TasInfraConfig getTasInfraConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return tasEntityHelper.getTasInfraConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private Any packTasInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<TasDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createTasInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }

  private TasInstanceSyncPerpetualTaskParams createTasInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<TasDeploymentReleaseData> deploymentReleaseData) {
    return TasInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllTasDeploymentReleaseList(toTasDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<TasDeploymentRelease> toTasDeploymentReleaseList(List<TasDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream().map(this::toTasDeploymentRelease).collect(Collectors.toList());
  }

  private TasDeploymentRelease toTasDeploymentRelease(TasDeploymentReleaseData releaseData) {
    return TasDeploymentRelease.newBuilder()
        .setApplicationName(releaseData.getApplicationName())
        .setTasInfraConfig(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(releaseData.getTasInfraConfig())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(List<TasDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<TasDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (deploymentReleaseSample.isEmpty()) {
      return Collections.emptyList();
    }
    return toTasInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);
  }

  private CfInstanceSyncRequestNG toTasInstanceSyncRequest(TasDeploymentReleaseData tasDeploymentReleaseData) {
    return CfInstanceSyncRequestNG.builder()
        .tasInfraConfig(tasDeploymentReleaseData.getTasInfraConfig())
        .applicationName(tasDeploymentReleaseData.getApplicationName())
        .build();
  }
}
