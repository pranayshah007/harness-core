/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dtos.InstanceSyncPerpetualTaskMappingDTO;
import io.harness.entities.InstanceSyncPerpetualTaskMapping;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class InstanceSyncPerpetualTaskMappingMapper {
  public InstanceSyncPerpetualTaskMappingDTO toDTO(InstanceSyncPerpetualTaskMapping instanceSyncPerpetualTaskMapping) {
    return InstanceSyncPerpetualTaskMappingDTO.builder()
        .id(instanceSyncPerpetualTaskMapping.getId())
        .connectorIdentifier(instanceSyncPerpetualTaskMapping.getConnectorIdentifier())
        .perpetualTaskId(instanceSyncPerpetualTaskMapping.getPerpetualTaskId())
        .accountId(instanceSyncPerpetualTaskMapping.getAccountId())
        .orgId(instanceSyncPerpetualTaskMapping.getOrgId())
        .projectId(instanceSyncPerpetualTaskMapping.getProjectId())
        .deploymentType(instanceSyncPerpetualTaskMapping.getDeploymentType())
        .build();
  }

  public InstanceSyncPerpetualTaskMapping toEntity(
      InstanceSyncPerpetualTaskMappingDTO instanceSyncPerpetualTaskMappingDTO) {
    return InstanceSyncPerpetualTaskMapping.builder()
        .id(instanceSyncPerpetualTaskMappingDTO.getId())
        .connectorIdentifier(instanceSyncPerpetualTaskMappingDTO.getConnectorIdentifier())
        .perpetualTaskId(instanceSyncPerpetualTaskMappingDTO.getPerpetualTaskId())
        .accountId(instanceSyncPerpetualTaskMappingDTO.getAccountId())
        .orgId(instanceSyncPerpetualTaskMappingDTO.getOrgId())
        .projectId(instanceSyncPerpetualTaskMappingDTO.getProjectId())
        .deploymentType(instanceSyncPerpetualTaskMappingDTO.getDeploymentType())
        .build();
  }
}
