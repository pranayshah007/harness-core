/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtaskinfo;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo.InstanceSyncPerpetualTaskInfoKeys;
import io.harness.mappers.DeploymentInfoDetailsMapper;
import io.harness.mappers.InstanceSyncPerpetualTaskInfoMapper;
import io.harness.repositories.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoInfoRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  public InstanceSyncPerpetualTaskInfoDTO updatePerpetualTaskIdV1OrV2(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    Criteria criteria = Criteria.where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                            .is(instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier())
                            .and(InstanceSyncPerpetualTaskInfoKeys.id)
                            .is(instanceSyncPerpetualTaskInfoDTO.getId());
    Update update = new Update();

    update.set(
        InstanceSyncPerpetualTaskInfoKeys.perpetualTaskId, instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskId());
    update.set(
        InstanceSyncPerpetualTaskInfoKeys.perpetualTaskIdV2, instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskIdV2());
    return InstanceSyncPerpetualTaskInfoMapper.toDTO(instanceSyncPerpetualTaskInfoRepository.update(criteria, update));
  }

  @Override
  public InstanceSyncPerpetualTaskInfoDTO updateLastSuccessfulRun(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    Criteria criteria = Criteria.where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                            .is(instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier())
                            .and(InstanceSyncPerpetualTaskInfoKeys.id)
                            .is(instanceSyncPerpetualTaskInfoDTO.getId());
    Update update = new Update();

    update.set(
        InstanceSyncPerpetualTaskInfoKeys.lastSuccessfulRun, instanceSyncPerpetualTaskInfoDTO.getLastSuccessfulRun());
    return InstanceSyncPerpetualTaskInfoMapper.toDTO(instanceSyncPerpetualTaskInfoRepository.update(criteria, update));
  }

  @Override
  public List<InstanceSyncPerpetualTaskInfoDTO> findAll(String accountId, String perpetualTaskId) {
    Criteria criteria = Criteria.where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                            .is(accountId)
                            .and(InstanceSyncPerpetualTaskInfoKeys.perpetualTaskIdV2)
                            .is(perpetualTaskId);

    return instanceSyncPerpetualTaskInfoRepository.findAll(criteria)
        .stream()
        .map(InstanceSyncPerpetualTaskInfoMapper::toDTO)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public Page<InstanceSyncPerpetualTaskInfoDTO> findAllInPages(
      Pageable pageRequest, String accountId, String perpetualTaskId) {
    Criteria criteria = Criteria.where(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                            .is(accountId)
                            .and(InstanceSyncPerpetualTaskInfoKeys.perpetualTaskIdV2)
                            .is(perpetualTaskId);

    return instanceSyncPerpetualTaskInfoRepository.findAllInPages(criteria, pageRequest)
        .map(InstanceSyncPerpetualTaskInfoMapper::toDTO);
  }
}
