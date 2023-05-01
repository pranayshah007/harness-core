/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.entities.DeploymentSummary;
import io.harness.mappers.deploymentinfomapper.DeploymentInfoMapper;

import java.util.ArrayList;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
public class DeploymentSummaryMapper {
  public DeploymentSummaryDTO toDTO(DeploymentSummary deploymentSummary) {
    return DeploymentSummaryDTO.builder()
        .accountIdentifier(deploymentSummary.getAccountIdentifier())
        .orgIdentifier(deploymentSummary.getOrgIdentifier())
        .projectIdentifier(deploymentSummary.getProjectIdentifier())
        .pipelineExecutionId(deploymentSummary.getPipelineExecutionId())
        .pipelineExecutionName(deploymentSummary.getPipelineExecutionName())
        .stageNodeExecutionId(deploymentSummary.getStageNodeExecutionId())
        .stageStatus(deploymentSummary.getStageStatus())
        .stageSetupId(deploymentSummary.getStageSetupId())
        .rollbackStatus(deploymentSummary.getRollbackStatus())
        .artifactDetails(deploymentSummary.getArtifactDetails())
        .createdAt(deploymentSummary.getCreatedAt())
        .deployedAt(deploymentSummary.getDeployedAt())
        .deployedById(deploymentSummary.getDeployedById())
        .deployedByName(deploymentSummary.getDeployedByName())
        .deploymentInfoDTO(DeploymentInfoMapper.toDTO(deploymentSummary.getDeploymentInfo()))
        .infrastructureMappingId(deploymentSummary.getInfrastructureMappingId())
        .infrastructureIdentifier(deploymentSummary.getInfrastructureIdentifier())
        .envGroupRef(deploymentSummary.getEnvGroupRef())
        .infrastructureName(deploymentSummary.getInfrastructureName())
        .lastModifiedAt(deploymentSummary.getLastModifiedAt())
        .id(deploymentSummary.getId())
        .serverInstanceInfoList(new ArrayList<>())
        .isRollbackDeployment(deploymentSummary.isRollbackDeployment())
        .instanceSyncKey(deploymentSummary.getInstanceSyncKey())
        .build();
  }

  public DeploymentSummary toEntity(DeploymentSummaryDTO deploymentSummaryDTO) {
    return DeploymentSummary.builder()
        .accountIdentifier(deploymentSummaryDTO.getAccountIdentifier())
        .projectIdentifier(deploymentSummaryDTO.getProjectIdentifier())
        .orgIdentifier(deploymentSummaryDTO.getOrgIdentifier())
        .deployedById(deploymentSummaryDTO.getDeployedById())
        .deployedAt(deploymentSummaryDTO.getDeployedAt())
        .deployedByName(deploymentSummaryDTO.getDeployedByName())
        .deploymentInfo(DeploymentInfoMapper.toEntity(deploymentSummaryDTO.getDeploymentInfoDTO()))
        .infrastructureMappingId(deploymentSummaryDTO.getInfrastructureMappingId())
        .infrastructureIdentifier(deploymentSummaryDTO.getInfrastructureIdentifier())
        .infrastructureName(deploymentSummaryDTO.getInfrastructureName())
        .pipelineExecutionId(deploymentSummaryDTO.getPipelineExecutionId())
        .stageNodeExecutionId(deploymentSummaryDTO.getStageNodeExecutionId())
        .stageStatus(deploymentSummaryDTO.getStageStatus())
        .stageSetupId(deploymentSummaryDTO.getStageSetupId())
        .rollbackStatus(deploymentSummaryDTO.getRollbackStatus())
        .pipelineExecutionName(deploymentSummaryDTO.getPipelineExecutionName())
        .artifactDetails(deploymentSummaryDTO.getArtifactDetails())
        .isRollbackDeployment(deploymentSummaryDTO.isRollbackDeployment())
        .instanceSyncKey(deploymentSummaryDTO.getInstanceSyncKey())
        .envGroupRef(deploymentSummaryDTO.getEnvGroupRef())
        .build();
  }
}
