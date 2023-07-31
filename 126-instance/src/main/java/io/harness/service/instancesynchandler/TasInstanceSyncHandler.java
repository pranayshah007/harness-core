/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.TasDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.TasInstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.TasInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
public class TasInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.TAS_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.TAS_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.TAS;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof TasInstanceInfoDTO)) {
      throw new InvalidArgumentsException(Pair.of("instanceInfoDTO", "Must be instance of TasInstanceInfoDTO"));
    }
    TasInstanceInfoDTO tasInstanceInfoDTO = (TasInstanceInfoDTO) instanceInfoDTO;
    return TasInfrastructureDetails.builder()
        .organization(tasInstanceInfoDTO.getOrganization())
        .tasApplicationName(tasInstanceInfoDTO.getTasApplicationName())
        .space(tasInstanceInfoDTO.getSpace())
        .build();
  }
  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof TasServerInstanceInfo)) {
      throw new InvalidArgumentsException(Pair.of("serverInstanceInfo", "Must be instance of TasServerInstanceInfo"));
    }

    TasServerInstanceInfo tasServerInstanceInfo = (TasServerInstanceInfo) serverInstanceInfo;

    return TasInstanceInfoDTO.builder()
        .instanceIndex(tasServerInstanceInfo.getInstanceIndex())
        .tasApplicationGuid(tasServerInstanceInfo.getTasApplicationGuid())
        .tasApplicationName(tasServerInstanceInfo.getTasApplicationName())
        .organization(tasServerInstanceInfo.getOrganization())
        .space(tasServerInstanceInfo.getSpace())
        .id(tasServerInstanceInfo.getId())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof TanzuApplicationServiceInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of TanzuApplicationServiceInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof TasServerInstanceInfo)) {
      throw new InvalidArgumentsException(Pair.of("serverInstanceInfo", "Must be instance of TasServerInstanceInfo"));
    }
    return TasDeploymentInfoDTO.builder()
        .applicationName(((TasServerInstanceInfo) serverInstanceInfoList.get(0)).getTasApplicationName())
        .applicationGuid(((TasServerInstanceInfo) serverInstanceInfoList.get(0)).getTasApplicationGuid())
        .build();
  }

  @Override
  public InstanceDTO updateInstance(InstanceDTO instanceDTO, InstanceInfoDTO instanceInfoFromServer) {
    instanceDTO.setInstanceInfoDTO(instanceInfoFromServer);
    return instanceDTO;
  }
}
