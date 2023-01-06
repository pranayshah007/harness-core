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
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.SpotServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.SpotDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.SpotInstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.SpotInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class SpotInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.SPOT_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.SPOT_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.ELASTIGROUP;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof SpotInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of " + SpotInstanceInfoDTO.class));
    }

    SpotInstanceInfoDTO spotInstanceInfoDTO = (SpotInstanceInfoDTO) instanceInfoDTO;
    return SpotInfrastructureDetails.builder()
        .ec2InstanceId(spotInstanceInfoDTO.getEc2InstanceId())
        .elastigroupId(spotInstanceInfoDTO.getElastigroupId())
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof SpotServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + SpotServerInstanceInfo.class));
    }

    SpotServerInstanceInfo spotServerInstanceInfo = (SpotServerInstanceInfo) serverInstanceInfo;
    return SpotInstanceInfoDTO.builder()
        .infrastructureKey(spotServerInstanceInfo.getInfrastructureKey())
        .elastigroupId(spotServerInstanceInfo.getElastigroupId())
        .ec2InstanceId(spotServerInstanceInfo.getEc2InstanceId())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof ElastigroupInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of " + ElastigroupInfrastructureOutcome.class));
    }
    if (!(serverInstanceInfoList.get(0) instanceof SpotServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + SpotServerInstanceInfo.class));
    }

    List<String> ec2Instances = serverInstanceInfoList.stream()
                                    .map(s -> (SpotServerInstanceInfo) s)
                                    .map(SpotServerInstanceInfo::getEc2InstanceId)
                                    .collect(Collectors.toList());

    return SpotDeploymentInfoDTO.builder()
        .infrastructureKey(infrastructureOutcome.getInfrastructureKey())
        .elastigroupId(((SpotServerInstanceInfo) serverInstanceInfoList.get(0)).getElastigroupId())
        .ec2Instances(ec2Instances)
        .build();
  }
}
