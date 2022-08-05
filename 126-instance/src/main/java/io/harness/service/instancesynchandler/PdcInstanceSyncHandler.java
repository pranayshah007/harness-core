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
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.PdcServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.PdcDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.PdcInstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.PdcInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.PerpetualTaskType;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class PdcInstanceSyncHandler extends AbstractInstanceSyncHandler {
  private static final Set<String> VALID_SERVICE_TYPES =
      Collections.unmodifiableSet(new HashSet(Arrays.asList(ServiceSpecType.SSH, ServiceSpecType.WINRM)));

  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.PDC_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.PHYSICAL_HOST_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.PDC;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof PdcInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of " + PdcInstanceInfoDTO.class));
    }

    PdcInstanceInfoDTO pdcInstanceInfoDTO = (PdcInstanceInfoDTO) instanceInfoDTO;
    return PdcInfrastructureDetails.builder().host(pdcInstanceInfoDTO.getHost()).build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof PdcServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + PdcServerInstanceInfo.class));
    }

    PdcServerInstanceInfo pdcServerInstanceInfo = (PdcServerInstanceInfo) serverInstanceInfo;
    return PdcInstanceInfoDTO.builder()
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
    if (!(infrastructureOutcome instanceof PdcInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of " + PdcInfrastructureOutcome.class));
    }
    if (!(serverInstanceInfoList.get(0) instanceof PdcServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of " + PdcServerInstanceInfo.class));
    }

    List<PdcServerInstanceInfo> pdcServerInstanceInfos = (List<PdcServerInstanceInfo>) (List<?>) serverInstanceInfoList;
    PdcServerInstanceInfo pdcServerInstanceInfo = pdcServerInstanceInfos.get(0);
    if (!VALID_SERVICE_TYPES.contains(pdcServerInstanceInfo.getServiceType())) {
      throw new InvalidArgumentsException(format("Invalid serviceType provided %s . Must be one of %s",
          pdcServerInstanceInfo.getServiceType(), VALID_SERVICE_TYPES));
    }

    return PdcDeploymentInfoDTO.builder()
        .serviceType(pdcServerInstanceInfo.getServiceType())
        .infrastructureKey(infrastructureOutcome.getInfrastructureKey())
        .host(pdcServerInstanceInfo.getHost())
        .build();
  }

  @Override
  public List<ServerInstanceInfo> refreshServerInstanceInfo(
      List<ServerInstanceInfo> serverInstanceInfoList, List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOList) {
    List<ServerInstanceInfo> result = new ArrayList<>();
    deploymentInfoDetailsDTOList.forEach(info -> {
      PdcDeploymentInfoDTO deploymentInfoDTO = (PdcDeploymentInfoDTO) info.getDeploymentInfoDTO();
      result.add(PdcServerInstanceInfo.builder()
                     .infrastructureKey(deploymentInfoDTO.getInfrastructureKey())
                     .host(deploymentInfoDTO.getHost())
                     .serviceType(deploymentInfoDTO.getServiceType())
                     .build());
    });
    return result;
  }
}
