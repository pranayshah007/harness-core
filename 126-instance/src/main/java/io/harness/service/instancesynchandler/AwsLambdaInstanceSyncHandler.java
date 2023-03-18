/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.infra.beans.AwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsLambdaServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AwsLambdaDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AwsLambdaInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.AwsLambdaInfrastructureDetails;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class AwsLambdaInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.AWS_LAMBDA_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.AWS_LAMBDA_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.AWS_LAMBDA;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof AwsLambdaInstanceInfoDTO)) {
      throw new InvalidArgumentsException(Pair.of("instanceInfoDTO", "Must be instance of AwsLambdaInstanceInfoDTO"));
    }
    AwsLambdaInstanceInfoDTO awsLambdaInstanceInfoDTO = (AwsLambdaInstanceInfoDTO) instanceInfoDTO;
    return AwsLambdaInfrastructureDetails.builder().region(awsLambdaInstanceInfoDTO.getRegion()).build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof AwsLambdaInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of AwsLambdaInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof AwsLambdaServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of AwsLambdaServerInstanceInfo"));
    }

    AwsLambdaServerInstanceInfo awsLambdaServerInstanceInfo =
        (AwsLambdaServerInstanceInfo) serverInstanceInfoList.get(0);

    return AwsLambdaDeploymentInfoDTO.builder()
        .functionName(awsLambdaServerInstanceInfo.getFunctionName())
        .region(awsLambdaServerInstanceInfo.getRegion())
        .infraStructureKey(awsLambdaServerInstanceInfo.getInfrastructureKey())
        .version(awsLambdaServerInstanceInfo.getVersion())
        .tags(awsLambdaServerInstanceInfo.getTags())
        .handler(awsLambdaServerInstanceInfo.getHandler())
        .runtime(awsLambdaServerInstanceInfo.getRuntime())
        .functionArn(awsLambdaServerInstanceInfo.getFunctionArn())
        .description(awsLambdaServerInstanceInfo.getDescription())
        .aliases(awsLambdaServerInstanceInfo.getAliases())
        .artifactId(awsLambdaServerInstanceInfo.getArtifactId())
        .source(awsLambdaServerInstanceInfo.getSource())
        .updatedTime(awsLambdaServerInstanceInfo.getUpdatedTime())
        .memorySize(awsLambdaServerInstanceInfo.getMemorySize())
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof AwsLambdaServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of AwsLambdaServerInstanceInfo"));
    }

    AwsLambdaServerInstanceInfo awsLambdaServerInstanceInfo = (AwsLambdaServerInstanceInfo) serverInstanceInfo;

    return AwsLambdaInstanceInfoDTO.builder()
        .functionName(awsLambdaServerInstanceInfo.getFunctionName())
        .region(awsLambdaServerInstanceInfo.getRegion())
        .version(awsLambdaServerInstanceInfo.getVersion())
        .source(awsLambdaServerInstanceInfo.getSource())
        .updatedTime(awsLambdaServerInstanceInfo.getUpdatedTime())
        .memorySize(awsLambdaServerInstanceInfo.getMemorySize())
        .runTime(awsLambdaServerInstanceInfo.getRuntime())
        .infraStructureKey(awsLambdaServerInstanceInfo.getInfrastructureKey())
        .handler(awsLambdaServerInstanceInfo.getHandler())
        .artifactId(awsLambdaServerInstanceInfo.getArtifactId())
        .functionArn(awsLambdaServerInstanceInfo.getFunctionArn())
        .description(awsLambdaServerInstanceInfo.getDescription())
        .aliases(awsLambdaServerInstanceInfo.getAliases())
        .tags(awsLambdaServerInstanceInfo.getTags())
        .build();
  }
}
