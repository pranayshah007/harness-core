/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.infra.beans.GoogleFunctionsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.GoogleFunctionDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.GoogleFunctionInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.GoogleFunctionInfrastructureDetails;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class GoogleFunctionInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.GOOGLE_CLOUD_FUNCTION_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.GOOGLE_CLOUD_FUNCTIONS_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof GoogleFunctionInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of GoogleFunctionInstanceInfoDTO"));
    }
    GoogleFunctionInstanceInfoDTO googleFunctionInstanceInfoDTO = (GoogleFunctionInstanceInfoDTO) instanceInfoDTO;
    return GoogleFunctionInfrastructureDetails.builder()
        .project(googleFunctionInstanceInfoDTO.getProject())
        .region(googleFunctionInstanceInfoDTO.getRegion())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof GoogleFunctionsInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of GoogleFunctionsInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof GoogleFunctionServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of GoogleCloudFunctionsServerInstanceInfo"));
    }

    GoogleFunctionServerInstanceInfo googleFunctionServerInstanceInfo =
        (GoogleFunctionServerInstanceInfo) serverInstanceInfoList.get(0);

    return GoogleFunctionDeploymentInfoDTO.builder()
        .environmentType(googleFunctionServerInstanceInfo.getEnvironmentType())
        .functionName(googleFunctionServerInstanceInfo.getFunctionName())
        .project(googleFunctionServerInstanceInfo.getProject())
        .region(googleFunctionServerInstanceInfo.getRegion())
        .infraStructureKey(googleFunctionServerInstanceInfo.getInfraStructureKey())
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof GoogleFunctionServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of GoogleFunctionServerInstanceInfo"));
    }

    GoogleFunctionServerInstanceInfo googleFunctionServerInstanceInfo =
        (GoogleFunctionServerInstanceInfo) serverInstanceInfo;

    return GoogleFunctionInstanceInfoDTO.builder()
        .functionName(googleFunctionServerInstanceInfo.getFunctionName())
        .project(googleFunctionServerInstanceInfo.getProject())
        .region(googleFunctionServerInstanceInfo.getRegion())
        .revision(googleFunctionServerInstanceInfo.getRevision())
        .source(googleFunctionServerInstanceInfo.getSource())
        .updatedTime(googleFunctionServerInstanceInfo.getUpdatedTime())
        .memorySize(googleFunctionServerInstanceInfo.getMemorySize())
        .runTime(googleFunctionServerInstanceInfo.getRunTime())
        .infraStructureKey(googleFunctionServerInstanceInfo.getInfraStructureKey())
        .build();
  }
}
