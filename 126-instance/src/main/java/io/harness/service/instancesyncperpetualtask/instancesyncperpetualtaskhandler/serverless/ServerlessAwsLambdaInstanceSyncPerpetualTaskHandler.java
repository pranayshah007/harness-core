/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaDeploymentReleaseData;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.delegate.task.serverless.request.ServerlessInstanceSyncRequest;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.ServerlessAwsLambdaDeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.ServerlessAwsLambdaDeploymentRelease;
import io.harness.perpetualtask.instancesync.ServerlessAwsLambdaInstanceSyncPerpetualTaskParams;
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
public class ServerlessAwsLambdaInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private ServerlessEntityHelper serverlessEntityHelper;
  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<ServerlessAwsLambdaDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packServerlessAwsLambdaInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }

  private List<ServerlessAwsLambdaDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(ServerlessAwsLambdaDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toServerlessAwsLambdaDeploymentReleaseData(
                infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private ServerlessAwsLambdaDeploymentReleaseData toServerlessAwsLambdaDeploymentReleaseData(
      InfrastructureMappingDTO infrastructureMappingDTO, ServerlessAwsLambdaDeploymentInfoDTO deploymentInfoDTO,
      InfrastructureOutcome infrastructureOutcome) {
    ServerlessInfraConfig serverlessInfraConfig =
        getServerlessInfraConfig(infrastructureMappingDTO, infrastructureOutcome);
    return ServerlessAwsLambdaDeploymentReleaseData.builder()
        .serverlessInfraConfig(serverlessInfraConfig)
        .serviceName(deploymentInfoDTO.getServiceName())
        .region(deploymentInfoDTO.getRegion())
        .functions(deploymentInfoDTO.getFunctions())
        .build();
  }

  private ServerlessInfraConfig getServerlessInfraConfig(
      InfrastructureMappingDTO infrastructure, InfrastructureOutcome infrastructureOutcome) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructure);
    return serverlessEntityHelper.getServerlessInfraConfig(infrastructureOutcome, baseNGAccess);
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }

  private Any packServerlessAwsLambdaInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<ServerlessAwsLambdaDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createServerlessAwsLambdaInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }

  private ServerlessAwsLambdaInstanceSyncPerpetualTaskParams createServerlessAwsLambdaInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<ServerlessAwsLambdaDeploymentReleaseData> deploymentReleaseData) {
    return ServerlessAwsLambdaInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllServerlessAwsLambdaDeploymentReleaseList(
            toServerlessAwsLambdaDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<ServerlessAwsLambdaDeploymentRelease> toServerlessAwsLambdaDeploymentReleaseList(
      List<ServerlessAwsLambdaDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream()
        .map(this::toServerlessAwsLambdaDeploymentRelease)
        .collect(Collectors.toList());
  }

  private ServerlessAwsLambdaDeploymentRelease toServerlessAwsLambdaDeploymentRelease(
      ServerlessAwsLambdaDeploymentReleaseData releaseData) {
    return ServerlessAwsLambdaDeploymentRelease.newBuilder()
        .setServiceName(releaseData.getServiceName())
        .setRegion(releaseData.getRegion())
        .addAllFunctions(releaseData.getFunctions())
        .setServerlessInfraConfig(
            ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(releaseData.getServerlessInfraConfig())))
        .build();
  }

  private List<ExecutionCapability> getExecutionCapabilities(
      List<ServerlessAwsLambdaDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<ServerlessAwsLambdaDeploymentReleaseData> deploymentReleaseSample =
        deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    return toServerlessAwsLambdaInstanceSyncRequest(deploymentReleaseSample.get())
        .fetchRequiredExecutionCapabilities(null);
  }

  private ServerlessInstanceSyncRequest toServerlessAwsLambdaInstanceSyncRequest(
      ServerlessAwsLambdaDeploymentReleaseData serverlessAwsLambdaDeploymentReleaseData) {
    return ServerlessInstanceSyncRequest.builder()
        .serverlessInfraConfig(serverlessAwsLambdaDeploymentReleaseData.getServerlessInfraConfig())
        .functions(serverlessAwsLambdaDeploymentReleaseData.getFunctions())
        .build();
  }
}
