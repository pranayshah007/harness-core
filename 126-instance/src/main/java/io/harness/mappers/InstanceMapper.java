/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.Instance;
import io.harness.mappers.instanceinfo.InstanceInfoMapper;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
public class InstanceMapper {
  public InstanceDTO toDTO(Instance instance) {
    return InstanceDTO.builder()
        .uuid(instance.getId())
        .accountIdentifier(instance.getAccountIdentifier())
        .envIdentifier(instance.getEnvIdentifier())
        .envName(instance.getEnvName())
        .envType(instance.getEnvType())
        .envGroupRef(instance.getEnvGroupRef())
        .infrastructureKind(instance.getInfrastructureKind())
        .infrastructureMappingId(instance.getInfrastructureMappingId())
        .instanceType(instance.getInstanceType())
        .lastDeployedAt(instance.getLastDeployedAt())
        .lastDeployedById(instance.getLastDeployedById())
        .lastDeployedByName(instance.getLastDeployedByName())
        .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
        .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
        .stageStatus(instance.getStageStatus())
        .stageNodeExecutionId(instance.getStageNodeExecutionId())
        .stageSetupId(instance.getStageSetupId())
        .rollbackStatus(instance.getRollbackStatus())
        .orgIdentifier(instance.getOrgIdentifier())
        .projectIdentifier(instance.getProjectIdentifier())
        .primaryArtifact(instance.getPrimaryArtifact())
        .serviceIdentifier(instance.getServiceIdentifier())
        .serviceName(instance.getServiceName())
        .createdAt(instance.getCreatedAt())
        .deletedAt(instance.getDeletedAt())
        .isDeleted(instance.isDeleted())
        .lastModifiedAt(instance.getLastModifiedAt())
        .connectorRef(instance.getConnectorRef())
        .instanceInfoDTO(InstanceInfoMapper.toDTO(instance.getInstanceInfo()))
        .instanceKey(instance.getInstanceKey())
        .infraIdentifier(instance.getInfraIdentifier())
        .infraName(instance.getInfraName())
        .podCreatedAt(instance.getPodCreatedAt())
        .instanceKey(instance.getInstanceKey())
        .infrastructureMappingId(instance.getInfrastructureMappingId())
        .build();
  }

  public List<InstanceDTO> toDTO(List<Instance> instances) {
    List<InstanceDTO> instanceDTOList = new ArrayList<>();
    instances.forEach(instance -> instanceDTOList.add(toDTO(instance)));
    return instanceDTOList;
  }

  public Instance toEntity(InstanceDTO instanceDTO) {
    return Instance.builder()
        .id(instanceDTO.getUuid())
        .accountIdentifier(instanceDTO.getAccountIdentifier())
        .envIdentifier(instanceDTO.getEnvIdentifier())
        .envName(instanceDTO.getEnvName())
        .envGroupRef(instanceDTO.getEnvGroupRef())
        .envType(instanceDTO.getEnvType())
        .infrastructureKind(instanceDTO.getInfrastructureKind())
        .infrastructureMappingId(instanceDTO.getInfrastructureMappingId())
        .instanceType(instanceDTO.getInstanceType())
        .lastDeployedAt(instanceDTO.getLastDeployedAt())
        .lastDeployedById(instanceDTO.getLastDeployedById())
        .lastDeployedByName(instanceDTO.getLastDeployedByName())
        .stageNodeExecutionId(instanceDTO.getStageNodeExecutionId())
        .stageSetupId(instanceDTO.getStageSetupId())
        .rollbackStatus(instanceDTO.getRollbackStatus())
        .stageStatus(instanceDTO.getStageStatus())
        .lastPipelineExecutionId(instanceDTO.getLastPipelineExecutionId())
        .lastPipelineExecutionName(instanceDTO.getLastPipelineExecutionName())
        .orgIdentifier(instanceDTO.getOrgIdentifier())
        .projectIdentifier(instanceDTO.getProjectIdentifier())
        .primaryArtifact(instanceDTO.getPrimaryArtifact())
        .serviceIdentifier(instanceDTO.getServiceIdentifier())
        .serviceName(instanceDTO.getServiceName())
        .createdAt(instanceDTO.getCreatedAt())
        .deletedAt(instanceDTO.getDeletedAt())
        .isDeleted(instanceDTO.isDeleted())
        .lastModifiedAt(instanceDTO.getLastModifiedAt())
        .connectorRef(instanceDTO.getConnectorRef())
        .instanceInfo(InstanceInfoMapper.toEntity(instanceDTO.getInstanceInfoDTO()))
        .instanceKey(instanceDTO.getInstanceKey())
        .infraIdentifier(instanceDTO.getInfraIdentifier())
        .infraName(instanceDTO.getInfraName())
        .podCreatedAt(instanceDTO.getPodCreatedAt())
        .build();
  }
}
