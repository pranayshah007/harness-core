/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsSshWinrmServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AwsSshWinrmDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AwsSshWinrmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.SshWinrmInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.PerpetualTaskType;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AwsSshWinrmInstanceSyncHandler extends AbstractInstanceSyncHandler {
  private static final Set<String> VALID_SERVICE_TYPES = ImmutableSet.of(ServiceSpecType.SSH, ServiceSpecType.WINRM);

  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.AWS_SSH_WINRM_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.AWS_SSH_WINRM_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.SSH_WINRM_AWS;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof AwsSshWinrmInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of " + AwsSshWinrmInstanceInfoDTO.class));
    }

    AwsSshWinrmInstanceInfoDTO awsSshWinrmInstanceInfoDTO = (AwsSshWinrmInstanceInfoDTO) instanceInfoDTO;
    return SshWinrmInfrastructureDetails.builder().host(awsSshWinrmInstanceInfoDTO.getHost()).build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof AwsSshWinrmServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + AwsSshWinrmServerInstanceInfo.class));
    }

    AwsSshWinrmServerInstanceInfo awsSshWinrmServerInstanceInfo = (AwsSshWinrmServerInstanceInfo) serverInstanceInfo;
    return AwsSshWinrmInstanceInfoDTO.builder()
        .serviceType(awsSshWinrmServerInstanceInfo.getServiceType())
        .host(awsSshWinrmServerInstanceInfo.getHost())
        .infrastructureKey(awsSshWinrmServerInstanceInfo.getInfrastructureKey())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof SshWinRmAwsInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of " + SshWinRmAwsInfrastructureOutcome.class));
    }
    if (!(serverInstanceInfoList.get(0) instanceof AwsSshWinrmServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + AwsSshWinrmServerInstanceInfo.class));
    }

    List<AwsSshWinrmServerInstanceInfo> awsSshWinrmServerInstanceInfos =
        (List<AwsSshWinrmServerInstanceInfo>) (List<?>) serverInstanceInfoList;
    AwsSshWinrmServerInstanceInfo awsSshWinrmServerInstanceInfo = awsSshWinrmServerInstanceInfos.get(0);
    if (!VALID_SERVICE_TYPES.contains(awsSshWinrmServerInstanceInfo.getServiceType())) {
      throw new InvalidArgumentsException(format("Invalid serviceType provided %s . Must be one of %s",
          awsSshWinrmServerInstanceInfo.getServiceType(), VALID_SERVICE_TYPES));
    }

    return AwsSshWinrmDeploymentInfoDTO.builder()
        .serviceType(awsSshWinrmServerInstanceInfo.getServiceType())
        .infrastructureKey(infrastructureOutcome.getInfrastructureKey())
        .host(awsSshWinrmServerInstanceInfo.getHost())
        .build();
  }
}
