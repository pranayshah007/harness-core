/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtaskinfo;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Preconditions.checkArgument;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo.InstanceSyncPerpetualTaskInfoKeys;
import io.harness.mappers.DeploymentInfoDetailsMapper;
import io.harness.mappers.InstanceSyncPerpetualTaskInfoMapper;
import io.harness.repositories.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoInfoRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(DX)
@Singleton
public class InstanceSyncPerpetualTaskInfoServiceImpl implements InstanceSyncPerpetualTaskInfoService {
  @Inject InstanceSyncPerpetualTaskInfoInfoRepository instanceSyncPerpetualTaskInfoRepository;

  @Override
  public Optional<InstanceSyncPerpetualTaskInfoDTO> findByInfrastructureMappingId(String infrastructureMappingId) {
    Optional<InstanceSyncPerpetualTaskInfo> instanceSyncPerpetualTaskInfoOptional =
        instanceSyncPerpetualTaskInfoRepository.findByInfrastructureMappingId(infrastructureMappingId);
    return instanceSyncPerpetualTaskInfoOptional.map(InstanceSyncPerpetualTaskInfoMapper::toDTO);
  }

  @Override
  public Optional<InstanceSyncPerpetualTaskInfoDTO> findByPerpetualTaskId(
      String accountIdentifier, String perpetualTaskId) {
    Optional<InstanceSyncPerpetualTaskInfo> instanceSyncPerpetualTaskInfoOptional =
        instanceSyncPerpetualTaskInfoRepository.findByAccountIdentifierAndPerpetualTaskId(
            accountIdentifier, perpetualTaskId);
    return instanceSyncPerpetualTaskInfoOptional.map(InstanceSyncPerpetualTaskInfoMapper::toDTO);
  }

  @Override
  public InstanceSyncPerpetualTaskInfoDTO save(InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    InstanceSyncPerpetualTaskInfo instanceSyncPerpetualTaskInfo =
        InstanceSyncPerpetualTaskInfoMapper.toEntity(instanceSyncPerpetualTaskInfoDTO);
    instanceSyncPerpetualTaskInfo = instanceSyncPerpetualTaskInfoRepository.save(instanceSyncPerpetualTaskInfo);
    return InstanceSyncPerpetualTaskInfoMapper.toDTO(instanceSyncPerpetualTaskInfo);
  }

  @Override
  public void deleteById(String accountIdentifier, String instanceSyncPerpetualTaskInfoId) {
    instanceSyncPerpetualTaskInfoRepository.deleteByAccountIdentifierAndId(
        accountIdentifier, instanceSyncPerpetualTaskInfoId);
  }

  @Override
  public InstanceSyncPerpetualTaskInfoDTO updateDeploymentInfoDetailsList(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    Criteria criteria = Criteria.where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                            .is(instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier())
                            .and(InstanceSyncPerpetualTaskInfoKeys.id)
                            .is(instanceSyncPerpetualTaskInfoDTO.getId());
    Update update = new Update().set(InstanceSyncPerpetualTaskInfoKeys.deploymentInfoDetailsList,
        DeploymentInfoDetailsMapper.toDeploymentInfoDetailsEntityList(
            instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList()));
    return InstanceSyncPerpetualTaskInfoMapper.toDTO(instanceSyncPerpetualTaskInfoRepository.update(criteria, update));
  }

  @Override
  public InstanceSyncPerpetualTaskInfoDTO updateDeploymentInfoListAndConnectorId(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO, String connectorIdentifier) {
    Criteria criteria = Criteria.where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                            .is(instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier())
                            .and(InstanceSyncPerpetualTaskInfoKeys.id)
                            .is(instanceSyncPerpetualTaskInfoDTO.getId());
    Update update = new Update();
    update.set(InstanceSyncPerpetualTaskInfoKeys.deploymentInfoDetailsList,
        DeploymentInfoDetailsMapper.toDeploymentInfoDetailsEntityList(
            instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList()));
    update.set(InstanceSyncPerpetualTaskInfoKeys.connectorIdentifier, connectorIdentifier);
    return InstanceSyncPerpetualTaskInfoMapper.toDTO(instanceSyncPerpetualTaskInfoRepository.update(criteria, update));
  }

  @Override
  public boolean deleteAllInstanceSyncPTs(String accountIdentifier) {
    checkArgument(isNotEmpty(accountIdentifier), "accountId must be present");
    Criteria criteria = getCriteriaForDeletion(accountIdentifier);
    DeleteResult delete = instanceSyncPerpetualTaskInfoRepository.delete(criteria);
    return delete.wasAcknowledged();
  }

  private Criteria getCriteriaForDeletion(String accountIdentifier) {
    return where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier).is(accountIdentifier);
  }
}
