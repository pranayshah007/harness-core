/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.deploymentsummary;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.entities.DeploymentSummary;
import io.harness.mappers.DeploymentSummaryMapper;
import io.harness.repositories.deploymentsummary.DeploymentSummaryRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.DX)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DeploymentSummaryServiceImpl implements DeploymentSummaryService {
  private DeploymentSummaryRepository deploymentSummaryRepository;

  @Override
  public DeploymentSummaryDTO save(DeploymentSummaryDTO deploymentSummaryDTO) {
    DeploymentSummary deploymentSummary =
        deploymentSummaryRepository.save(DeploymentSummaryMapper.toEntity(deploymentSummaryDTO));
    return DeploymentSummaryMapper.toDTO(deploymentSummary);
  }

  @Override
  public Optional<DeploymentSummaryDTO> getByDeploymentSummaryId(String deploymentSummaryId) {
    Optional<DeploymentSummary> deploymentSummaryOptional = deploymentSummaryRepository.findById(deploymentSummaryId);
    return deploymentSummaryOptional.map(DeploymentSummaryMapper::toDTO);
  }

  @Override
  public Optional<DeploymentSummaryDTO> getNthDeploymentSummaryFromNow(
      int N, String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO) {
    Optional<DeploymentSummary> deploymentSummaryOptional =
        deploymentSummaryRepository.fetchNthRecordFromNow(N, instanceSyncKey, infrastructureMappingDTO, null);
    return deploymentSummaryOptional.map(DeploymentSummaryMapper::toDTO);
  }

  @Override
  public Optional<DeploymentSummaryDTO> getLatestByInstanceKeyAndPipelineExecutionIdNot(
      String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO, String pipelineExecutionId) {
    Optional<DeploymentSummary> deploymentSummaryOptional =
        deploymentSummaryRepository.fetchLatestByInstanceKeyAndPipelineExecutionIdNot(
            instanceSyncKey, infrastructureMappingDTO, pipelineExecutionId);
    return deploymentSummaryOptional.map(DeploymentSummaryMapper::toDTO);
  }

  @Override
  public Optional<DeploymentSummaryDTO> getLatestByInstanceKey(
      String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO) {
    return getNthDeploymentSummaryFromNow(1, instanceSyncKey, infrastructureMappingDTO);
  }

  @Override
  public Optional<DeploymentSummaryDTO> getNthDeploymentSummaryFromNow(
      int N, String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO, Boolean isRollbackDeployment) {
    Optional<DeploymentSummary> deploymentSummaryOptional = deploymentSummaryRepository.fetchNthRecordFromNow(
        N, instanceSyncKey, infrastructureMappingDTO, isRollbackDeployment);
    return deploymentSummaryOptional.map(DeploymentSummaryMapper::toDTO);
  }
}
