/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AwsSamDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AwsSamInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.AwsSamInfrastructureDetails;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class AwsSamInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.AWS_SAM_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.AWS_SAM_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.AWS_SAM;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof AwsSamInstanceInfoDTO)) {
      throw new InvalidArgumentsException(Pair.of("instanceInfoDTO", "Must be instance of AwsSamInstanceInfoDTO"));
    }
    AwsSamInstanceInfoDTO awsSamInstanceInfoDTO = (AwsSamInstanceInfoDTO) instanceInfoDTO;
    return AwsSamInfrastructureDetails.builder().region(awsSamInstanceInfoDTO.getRegion()).build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof AwsSamInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of AwsSamInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof AwsSamServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of AwsSamServerInstanceInfo"));
    }

    AwsSamServerInstanceInfo awsSamServerInstanceInfo = (AwsSamServerInstanceInfo) serverInstanceInfoList.get(0);

    List<String> functions =
        serverInstanceInfoList.stream()
            .map(serverInstanceInfo -> ((AwsSamServerInstanceInfo) serverInstanceInfo).getFunctionName())
            .collect(Collectors.toList());

    return AwsSamDeploymentInfoDTO.builder()
        .functions(functions)
        .region(awsSamServerInstanceInfo.getRegion())
        .infraStructureKey(awsSamServerInstanceInfo.getInfraStructureKey())
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof AwsSamServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of AwsSamServerInstanceInfo"));
    }

    AwsSamServerInstanceInfo awsSamServerInstanceInfo = (AwsSamServerInstanceInfo) serverInstanceInfo;

    return AwsSamInstanceInfoDTO.builder()
        .functionName(awsSamServerInstanceInfo.getFunctionName())
        .region(awsSamServerInstanceInfo.getRegion())
        .handler(awsSamServerInstanceInfo.getHandler())
        .runTime(awsSamServerInstanceInfo.getRunTime())
        .memorySize(awsSamServerInstanceInfo.getMemorySize())
        .infraStructureKey(awsSamServerInstanceInfo.getInfraStructureKey())
        .build();
  }
}
