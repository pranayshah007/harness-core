/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.aws;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.lambda.AwsLambdaEntityHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.aws.lambda.AwsLambdaDeploymentReleaseData;
import io.harness.delegate.task.aws.lambda.AwsLambdaInfraConfig;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaInstanceSyncRequest;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.AwsLambdaDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.AwsLambdaDeploymentRelease;
import io.harness.perpetualtask.instancesync.AwsLambdaInstanceSyncPerpetualTaskParamsNg;
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
            -> toAwsLambdaDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
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
        .version(deploymentInfoDTO.getVersion())
        .handler(deploymentInfoDTO.getHandler())
        .aliases(deploymentInfoDTO.getAliases())
        .artifactId(deploymentInfoDTO.getArtifactId())
        .description(deploymentInfoDTO.getDescription())
        .functionArn(deploymentInfoDTO.getFunctionArn())
        .memorySize(deploymentInfoDTO.getMemorySize())
        .runtime(deploymentInfoDTO.getRuntime())
        .source(deploymentInfoDTO.getSource())
        .tags(deploymentInfoDTO.getTags())
        .updatedTime(deploymentInfoDTO.getUpdatedTime())
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

  private AwsLambdaInstanceSyncPerpetualTaskParamsNg createAwsLambdaInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<AwsLambdaDeploymentReleaseData> deploymentReleaseData) {
    return AwsLambdaInstanceSyncPerpetualTaskParamsNg.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllAwsLambdaDeploymentReleaseList(toAwsLambdaDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<AwsLambdaDeploymentRelease> toAwsLambdaDeploymentReleaseList(
      List<AwsLambdaDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream().map(this::toAwsLambdaDeploymentRelease).collect(Collectors.toList());
  }

  private AwsLambdaDeploymentRelease toAwsLambdaDeploymentRelease(AwsLambdaDeploymentReleaseData releaseData) {
    return AwsLambdaDeploymentRelease.newBuilder()
        .setFunction(releaseData.getFunction())
        .setRegion(releaseData.getRegion())
        .setAwsLambdaInfraConfig(
            ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(releaseData.getAwsLambdaInfraConfig())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(
      List<AwsLambdaDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<AwsLambdaDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    return toAwsLambdaInstanceSyncRequest(deploymentReleaseSample.get()).fetchRequiredExecutionCapabilities(null);
  }

  private AwsLambdaInstanceSyncRequest toAwsLambdaInstanceSyncRequest(
      AwsLambdaDeploymentReleaseData awsLambdaDeploymentReleaseData) {
    return AwsLambdaInstanceSyncRequest.builder()
        .awsLambdaInfraConfig(awsLambdaDeploymentReleaseData.getAwsLambdaInfraConfig())
        .function(awsLambdaDeploymentReleaseData.getFunction())
        .build();
  }
}
