/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AsgServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AsgDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AsgInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.AsgInfrastructureDetails;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import com.google.inject.Singleton;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AsgInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.ASG_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.ASG_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.ASG;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof AsgInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of " + AsgInstanceInfoDTO.class));
    }

    AsgInstanceInfoDTO asgInstanceInfoDTO = (AsgInstanceInfoDTO) instanceInfoDTO;
    return AsgInfrastructureDetails.builder().region(asgInstanceInfoDTO.getRegion()).build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof AsgServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + AsgServerInstanceInfo.class));
    }

    AsgServerInstanceInfo asgServerInstanceInfo = (AsgServerInstanceInfo) serverInstanceInfo;
    return AsgInstanceInfoDTO.builder()
        .region(asgServerInstanceInfo.getRegion())
        .infrastructureKey(asgServerInstanceInfo.getInfrastructureKey())
        .asgName(asgServerInstanceInfo.getAsgName())
        .asgNameWithoutSuffix(asgServerInstanceInfo.getAsgNameWithoutSuffix())
        .instanceId(asgServerInstanceInfo.getInstanceId())
        .executionStrategy(asgServerInstanceInfo.getExecutionStrategy())
        .production(asgServerInstanceInfo.getProduction())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof AsgInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of AsgInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof AsgServerInstanceInfo)) {
      throw new InvalidArgumentsException(Pair.of("serverInstanceInfo", "Must be instance of AsgServerInstanceInfo"));
    }

    AsgServerInstanceInfo asgServerInstanceInfo = (AsgServerInstanceInfo) serverInstanceInfoList.get(0);

    return AsgDeploymentInfoDTO.builder()
        .region(asgServerInstanceInfo.getRegion())
        .asgNameWithoutSuffix(asgServerInstanceInfo.getAsgNameWithoutSuffix())
        .infrastructureKey(asgServerInstanceInfo.getInfrastructureKey())
        .executionStrategy(asgServerInstanceInfo.getExecutionStrategy())
        .build();
  }
}
