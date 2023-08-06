/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask;
import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(DX)
public interface InstanceSyncPerpetualTaskService {
  String createPerpetualTask(InfrastructureMappingDTO infrastructureMappingDTO,
      AbstractInstanceSyncHandler abstractInstanceSyncHandler, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome);

  void resetPerpetualTask(String accountIdentifier, String perpetualTaskId,
      InfrastructureMappingDTO infrastructureMappingDTO, AbstractInstanceSyncHandler abstractInstanceSyncHandler,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome);

  void resetPerpetualTaskV2(String accountIdentifier, String perpetualTaskId,
      InfrastructureMappingDTO infrastructureMappingDTO, AbstractInstanceSyncHandler abstractInstanceSyncHandler,
      ConnectorInfoDTO connectorInfoDTO);

  void deletePerpetualTask(String accountIdentifier, String perpetualTaskId);

  String createPerpetualTaskV2(AbstractInstanceSyncHandler abstractInstanceSyncHandler,
      InfrastructureMappingDTO infrastructureMappingDTO, ConnectorInfoDTO connectorInfoDTO);
}
