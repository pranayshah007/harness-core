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
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureSshWinrmServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AzureSshWinrmDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AzureSshWinrmInstanceInfoDTO;
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
public class AzureSshWinrmInstanceSyncHandler extends AbstractInstanceSyncHandler {
  private static final Set<String> VALID_SERVICE_TYPES = ImmutableSet.of(ServiceSpecType.SSH, ServiceSpecType.WINRM);

  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.AZURE_SSH_WINRM_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.AZURE_SSH_WINRM_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.SSH_WINRM_AZURE;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof AzureSshWinrmInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of " + AzureSshWinrmInstanceInfoDTO.class));
    }

    AzureSshWinrmInstanceInfoDTO pdcInstanceInfoDTO = (AzureSshWinrmInstanceInfoDTO) instanceInfoDTO;
    return SshWinrmInfrastructureDetails.builder().host(pdcInstanceInfoDTO.getHost()).build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof AzureSshWinrmServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + AzureSshWinrmServerInstanceInfo.class));
    }

    AzureSshWinrmServerInstanceInfo pdcServerInstanceInfo = (AzureSshWinrmServerInstanceInfo) serverInstanceInfo;
    return AzureSshWinrmInstanceInfoDTO.builder()
        .serviceType(pdcServerInstanceInfo.getServiceType())
        .host(pdcServerInstanceInfo.getHost())
        .infrastructureKey(pdcServerInstanceInfo.getInfrastructureKey())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof SshWinRmAzureInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of " + SshWinRmAzureInfrastructureOutcome.class));
    }
    if (!(serverInstanceInfoList.get(0) instanceof AzureSshWinrmServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + AzureSshWinrmServerInstanceInfo.class));
    }

    List<AzureSshWinrmServerInstanceInfo> serverInstanceInfos =
        (List<AzureSshWinrmServerInstanceInfo>) (List<?>) serverInstanceInfoList;
    AzureSshWinrmServerInstanceInfo serverInstanceInfo = serverInstanceInfos.get(0);
    if (!VALID_SERVICE_TYPES.contains(serverInstanceInfo.getServiceType())) {
      throw new InvalidArgumentsException(format("Invalid serviceType provided %s . Must be one of %s",
          serverInstanceInfo.getServiceType(), VALID_SERVICE_TYPES));
    }

    return AzureSshWinrmDeploymentInfoDTO.builder()
        .serviceType(serverInstanceInfo.getServiceType())
        .infrastructureKey(infrastructureOutcome.getInfrastructureKey())
        .host(serverInstanceInfo.getHost())
        .build();
  }
}
