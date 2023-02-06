/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import io.harness.cdng.infra.beans.AwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.GoogleFunctionsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AwsLambdaDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.GoogleFunctionDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AwsLambdaInstanceInfoDTO;
import io.harness.dtos.instanceinfo.GoogleFunctionInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.AwsLambdaInfrastructureDetails;
import io.harness.models.infrastructuredetails.GoogleFunctionInfrastructureDetails;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

public class AwsLambdaInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.AWS_LAMBDA_INSTANCE_SYNC;
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
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of AwsLambdaInstanceInfoDTO"));
    }
    AwsLambdaInstanceInfoDTO awsLambdaInstanceInfoDTO = (AwsLambdaInstanceInfoDTO) instanceInfoDTO;
    return AwsLambdaInfrastructureDetails.builder()
        .project(awsLambdaInstanceInfoDTO.getProject())
        .region(awsLambdaInstanceInfoDTO.getRegion())
        .build();
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
        .revision(awsLambdaServerInstanceInfo.getRevision())
        .functionName(awsLambdaServerInstanceInfo.getFunctionName())
        .project(awsLambdaServerInstanceInfo.getProject())
        .region(awsLambdaServerInstanceInfo.getRegion())
        .infraStructureKey(awsLambdaServerInstanceInfo.getInfraStructureKey())
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof AwsLambdaServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of AwsLambdaServerInstanceInfo"));
    }

    AwsLambdaServerInstanceInfo awsLambdaServerInstanceInfo =
        (AwsLambdaServerInstanceInfo) serverInstanceInfo;

    return AwsLambdaInstanceInfoDTO.builder()
        .functionName(awsLambdaServerInstanceInfo.getFunctionName())
        .project(awsLambdaServerInstanceInfo.getProject())
        .region(awsLambdaServerInstanceInfo.getRegion())
        .revision(awsLambdaServerInstanceInfo.getRevision())
        .source(awsLambdaServerInstanceInfo.getSource())
        .updatedTime(awsLambdaServerInstanceInfo.getUpdatedTime())
        .memorySize(awsLambdaServerInstanceInfo.getMemorySize())
        .runTime(awsLambdaServerInstanceInfo.getRunTime())
        .infraStructureKey(awsLambdaServerInstanceInfo.getInfraStructureKey())
        .build();
  }
}
