/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.GoogleFunctionsEntityHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.googlefunction.GoogleFunctionDeploymentReleaseData;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionInstanceSyncRequest;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.GoogleFunctionDeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.GoogleFunctionDeploymentRelease;
import io.harness.perpetualtask.instancesync.GoogleFunctionInstanceSyncPerpetualTaskParams;
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
public class GoogleFunctionInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private GoogleFunctionsEntityHelper googleFunctionsEntityHelper;
  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<GoogleFunctionDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packGoogleFunctionsInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
        infrastructureMappingDTO.getAccountIdentifier());
  }

  private List<GoogleFunctionDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(GoogleFunctionDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toGoogleFunctionsDeploymentReleaseData(
                infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private GoogleFunctionDeploymentReleaseData toGoogleFunctionsDeploymentReleaseData(
      InfrastructureMappingDTO infrastructureMappingDTO, GoogleFunctionDeploymentInfoDTO deploymentInfoDTO,
      InfrastructureOutcome infrastructureOutcome) {
    GoogleFunctionInfraConfig googleFunctionInfraConfig =
        getGoogleFunctionInfraConfig(infrastructureMappingDTO, infrastructureOutcome);
    return GoogleFunctionDeploymentReleaseData.builder()
        .googleFunctionInfraConfig(googleFunctionInfraConfig)
        .function(deploymentInfoDTO.getFunctionName())
        .region(deploymentInfoDTO.getRegion())
        .build();
  }

  private GoogleFunctionInfraConfig getGoogleFunctionInfraConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return googleFunctionsEntityHelper.getInfraConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private Any packGoogleFunctionsInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<GoogleFunctionDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createGoogleFunctionsInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }

  private GoogleFunctionInstanceSyncPerpetualTaskParams createGoogleFunctionsInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<GoogleFunctionDeploymentReleaseData> deploymentReleaseData) {
    return GoogleFunctionInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllGoogleFunctionsDeploymentReleaseList(
            toGoogleFunctionsDeploymentReleaseList(deploymentReleaseData, accountIdentifier))
        .build();
  }

  private List<GoogleFunctionDeploymentRelease> toGoogleFunctionsDeploymentReleaseList(
      List<GoogleFunctionDeploymentReleaseData> deploymentReleaseData, String accountIdentifier) {
    return deploymentReleaseData.stream()
        .map(data -> toGoogleFunctionDeploymentRelease(data, accountIdentifier))
        .collect(Collectors.toList());
  }

  private GoogleFunctionDeploymentRelease toGoogleFunctionDeploymentRelease(
      GoogleFunctionDeploymentReleaseData releaseData, String accountIdentifier) {
    return GoogleFunctionDeploymentRelease.newBuilder()
        .setFunction(releaseData.getFunction())
        .setRegion(releaseData.getRegion())
        .setGoogleFunctionsInfraConfig(ByteString.copyFrom(
            getKryoSerializer(accountIdentifier).asBytes(releaseData.getGoogleFunctionInfraConfig())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(
      List<GoogleFunctionDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<GoogleFunctionDeploymentReleaseData> deploymentReleaseSample =
        deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    return toGoogleFunctionsInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);
  }

  private GoogleFunctionInstanceSyncRequest toGoogleFunctionsInstanceSyncRequest(
      GoogleFunctionDeploymentReleaseData googleFunctionsDeploymentReleaseData) {
    return GoogleFunctionInstanceSyncRequest.builder()
        .googleFunctionInfraConfig(googleFunctionsDeploymentReleaseData.getGoogleFunctionInfraConfig())
        .function(googleFunctionsDeploymentReleaseData.getFunction())
        .build();
  }
}
