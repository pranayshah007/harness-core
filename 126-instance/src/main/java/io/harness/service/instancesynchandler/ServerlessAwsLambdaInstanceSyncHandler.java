/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.ServerlessAwsLambdaDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.ServerlessAwsLambdaInstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.ServerlessAwsLambdaInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class ServerlessAwsLambdaInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.SERVERLESS_AWS_LAMBDA_INSTANCE_SYNC;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.SERVERLESS_AWS_LAMBDA_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.SERVERLESS_AWS_LAMBDA;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof ServerlessAwsLambdaInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of ServerlessAwsLambdaInstanceInfoDTO"));
    }
    ServerlessAwsLambdaInstanceInfoDTO serverlessAwsLambdaInstanceInfoDTO =
        (ServerlessAwsLambdaInstanceInfoDTO) instanceInfoDTO;
    return ServerlessAwsLambdaInfrastructureDetails.builder()
        .region(serverlessAwsLambdaInstanceInfoDTO.getRegion())
        .stage(serverlessAwsLambdaInstanceInfoDTO.getStage())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof ServerlessAwsLambdaInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of ServerlessAwsLambdaInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof ServerlessAwsLambdaServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of ServerlessAwsLambdaServerInstanceInfo"));
    }

    ServerlessAwsLambdaServerInstanceInfo serverlessAwsLambdaServerInstanceInfo =
        (ServerlessAwsLambdaServerInstanceInfo) serverInstanceInfoList.get(0);

    List<String> functions =
        serverInstanceInfoList.stream()
            .map(serverInstanceInfo -> ((ServerlessAwsLambdaServerInstanceInfo) serverInstanceInfo).getFunctionName())
            .collect(Collectors.toList());

    return ServerlessAwsLambdaDeploymentInfoDTO.builder()
        .serviceName(serverlessAwsLambdaServerInstanceInfo.getServerlessServiceName())
        .region(serverlessAwsLambdaServerInstanceInfo.getRegion())
        .functions(functions)
        .infraStructureKey(serverlessAwsLambdaServerInstanceInfo.getInfraStructureKey())
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof ServerlessAwsLambdaServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of ServerlessAwsLambdaServerInstanceInfo"));
    }

    ServerlessAwsLambdaServerInstanceInfo serverlessAwsLambdaServerInstanceInfo =
        (ServerlessAwsLambdaServerInstanceInfo) serverInstanceInfo;

    return ServerlessAwsLambdaInstanceInfoDTO.builder()
        .functionName(serverlessAwsLambdaServerInstanceInfo.getFunctionName())
        .serviceName(serverlessAwsLambdaServerInstanceInfo.getServerlessServiceName())
        .stage(serverlessAwsLambdaServerInstanceInfo.getServerlessStage())
        .handler(serverlessAwsLambdaServerInstanceInfo.getHandler())
        .timeout(serverlessAwsLambdaServerInstanceInfo.getTimeout())
        .runTime(serverlessAwsLambdaServerInstanceInfo.getRunTime())
        .memorySize(serverlessAwsLambdaServerInstanceInfo.getMemorySize())
        .region(serverlessAwsLambdaServerInstanceInfo.getRegion())
        .infraStructureKey(serverlessAwsLambdaServerInstanceInfo.getInfraStructureKey())
        .build();
  }
}
