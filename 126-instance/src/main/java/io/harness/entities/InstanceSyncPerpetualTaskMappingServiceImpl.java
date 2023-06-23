/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceSyncPerpetualTaskMappingDTO;
import io.harness.mappers.InstanceSyncPerpetualTaskMappingMapper;
import io.harness.repositories.instanceSyncPerpetualTaskMapping.InstanceSyncPerpetualTaskMappingRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class InstanceSyncPerpetualTaskMappingServiceImpl implements InstanceSyncPerpetualTaskMappingService {
  @Inject InstanceSyncPerpetualTaskMappingRepository instanceSyncPerpetualTaskMappingRepository;

  @Override
  public InstanceSyncPerpetualTaskMappingDTO save(
      InstanceSyncPerpetualTaskMappingDTO instanceSyncPerpetualTaskMappingDTO) {
    InstanceSyncPerpetualTaskMapping instanceSyncPerpetualTaskMapping =
        InstanceSyncPerpetualTaskMappingMapper.toEntity(instanceSyncPerpetualTaskMappingDTO);
    instanceSyncPerpetualTaskMapping =
        instanceSyncPerpetualTaskMappingRepository.save(instanceSyncPerpetualTaskMapping);
    return InstanceSyncPerpetualTaskMappingMapper.toDTO(instanceSyncPerpetualTaskMapping);
  }

  @Override
  public boolean delete(String accountId, String perpetualTaskId) {
    return instanceSyncPerpetualTaskMappingRepository.deleteByAccountIdAndPerpetualTaskId(accountId, perpetualTaskId);
  }

  @Override
  public Optional<InstanceSyncPerpetualTaskMappingDTO> findByConnectorRefAndDeploymentType(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String connectorId, String deploymentType) {
    Optional<InstanceSyncPerpetualTaskMapping> instanceSyncPerpetualTaskMappingOptional =
        instanceSyncPerpetualTaskMappingRepository.findByConnectorRefAndDeploymentType(
            accountIdentifier, orgIdentifier, projectIdentifier, connectorId, deploymentType);

    return instanceSyncPerpetualTaskMappingOptional.map(InstanceSyncPerpetualTaskMappingMapper::toDTO);
  }
}
