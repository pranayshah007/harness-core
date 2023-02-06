/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.aws;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.lambda.AwsLambdaEntityHelper;
import io.harness.cdng.googlefunctions.GoogleFunctionsEntityHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.aws.lambda.AwsLambdaDeploymentReleaseData;
import io.harness.delegate.task.aws.lambda.AwsLambdaInfraConfig;
import io.harness.delegate.task.googlefunction.GoogleFunctionDeploymentReleaseData;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionInstanceSyncRequest;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.AwsLambdaDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.GoogleFunctionDeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.GoogleFunctionDeploymentRelease;
import io.harness.perpetualtask.instancesync.GoogleFunctionInstanceSyncPerpetualTaskParams;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;
import lombok.AllArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private AwsLambdaEntityHelper awsLambdaEntityHelper;
  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<AwsLambdaDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packAwsLambdaInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }

  private List<AwsLambdaDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(AwsLambdaDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toAwsLambdaDeploymentReleaseData(
                infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private AwsLambdaDeploymentReleaseData toAwsLambdaDeploymentReleaseData(
      InfrastructureMappingDTO infrastructureMappingDTO, AwsLambdaDeploymentInfoDTO deploymentInfoDTO,
      InfrastructureOutcome infrastructureOutcome) {
    AwsLambdaInfraConfig awsLambdaInfraConfig =
            getAwsLambdaInfraConfig(infrastructureMappingDTO, infrastructureOutcome);
    return AwsLambdaDeploymentReleaseData.builder()
        .awsLambdaInfraConfig(awsLambdaInfraConfig)
        .function(deploymentInfoDTO.getFunctionName())
        .region(deploymentInfoDTO.getRegion())
        .build();
  }

  private AwsLambdaInfraConfig getAwsLambdaInfraConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return awsLambdaEntityHelper.getInfraConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private Any packAwsLambdaInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<AwsLambdaDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createAwsLambdaInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }

  private GoogleFunctionInstanceSyncPerpetualTaskParams createAwsLambdaInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<GoogleFunctionDeploymentReleaseData> deploymentReleaseData) {
    return GoogleFunctionInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllGoogleFunctionsDeploymentReleaseList(toGoogleFunctionsDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<GoogleFunctionDeploymentRelease> toGoogleFunctionsDeploymentReleaseList(
      List<GoogleFunctionDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream().map(this::toGoogleFunctionDeploymentRelease).collect(Collectors.toList());
  }

  private GoogleFunctionDeploymentRelease toGoogleFunctionDeploymentRelease(
      GoogleFunctionDeploymentReleaseData releaseData) {
    return GoogleFunctionDeploymentRelease.newBuilder()
        .setFunction(releaseData.getFunction())
        .setRegion(releaseData.getRegion())
        .setGoogleFunctionsInfraConfig(
            ByteString.copyFrom(kryoSerializer.asBytes(releaseData.getGoogleFunctionInfraConfig())))
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
