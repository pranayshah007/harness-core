/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instance;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceKeys;
import io.harness.mappers.InstanceMapper;
import io.harness.models.ActiveServiceInstanceInfo;
import io.harness.models.CountByServiceIdAndEnvType;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstancesByBuildId;
import io.harness.repositories.instance.InstanceRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@OwnedBy(HarnessTeam.DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InstanceServiceImpl implements InstanceService {
  private final InstanceRepository instanceRepository;

  @Override
  public InstanceDTO save(InstanceDTO instanceDTO) {
    Instance instance = InstanceMapper.toEntity(instanceDTO);
    instance = instanceRepository.save(instance);
    return InstanceMapper.toDTO(instance);
  }

  @Override
  public List<InstanceDTO> saveAll(List<InstanceDTO> instanceDTOList) {
    List<Instance> instances = (List<Instance>) instanceRepository.saveAll(
        instanceDTOList.stream().map(InstanceMapper::toEntity).collect(Collectors.toList()));
    return instances.stream().map(InstanceMapper::toDTO).collect(Collectors.toList());
  }

  /**
   * Create instance record if not present already
   * @param instanceDTO
   * @return  Optional.empty() in case duplicate key issue occurs as record is already present
   *          Instance entity in case record is created successfully
   */
  @Override
  public Optional<InstanceDTO> saveOrReturnEmptyIfAlreadyExists(InstanceDTO instanceDTO) {
    Instance instance = InstanceMapper.toEntity(instanceDTO);
    try {
      instance = instanceRepository.save(instance);
    } catch (DuplicateKeyException duplicateKeyException) {
      // If instance exists in deleted state, undelete it
      if (undeleteInstance(instance) != null) {
        log.info("Undeleted instance : {}", instanceDTO);
      } else {
        log.error("Duplicate key error while inserting instance : {}", instanceDTO);
      }
      return Optional.empty();
    }
    return Optional.of(InstanceMapper.toDTO(instance));
  }

  @Override
  public void deleteById(String id) {
    instanceRepository.deleteById(id);
  }

  @Override
  public void deleteAll(List<InstanceDTO> instanceDTOList) {
    instanceDTOList.forEach(instanceDTO -> instanceRepository.deleteByInstanceKey(instanceDTO.getInstanceKey()));
  }

  @Override
  public Optional<InstanceDTO> delete(String instanceKey, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String infrastructureMappingId) {
    checkArgument(isNotEmpty(instanceKey), "instanceKey must be present");
    checkArgument(isNotEmpty(accountIdentifier), "accountIdentifier must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgIdentifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "projectIdentifier must be present");

    Criteria criteria = Criteria.where(InstanceKeys.instanceKey)
                            .is(instanceKey)
                            .and(InstanceKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(InstanceKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(InstanceKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(InstanceKeys.infrastructureMappingId)
                            .is(infrastructureMappingId);
    Update update =
        new Update().set(InstanceKeys.isDeleted, true).set(InstanceKeys.deletedAt, System.currentTimeMillis());
    Instance instance = instanceRepository.findAndModify(criteria, update);
    if (instance == null) {
      return Optional.empty();
    }
    return Optional.of(InstanceMapper.toDTO(instance));
  }

  /**
   * Returns null if no document found to replace
   * Returns updated record if document is successfully replaced
   */
  @Override
  public Optional<InstanceDTO> findAndReplace(InstanceDTO instanceDTO) {
    Criteria criteria = Criteria.where(InstanceKeys.instanceKey)
                            .is(instanceDTO.getInstanceKey())
                            .and(InstanceKeys.infrastructureMappingId)
                            .is(instanceDTO.getInfrastructureMappingId());
    Instance instanceOptional = instanceRepository.findAndReplace(criteria, InstanceMapper.toEntity(instanceDTO));
    if (instanceOptional == null) {
      return Optional.empty();
    }
    return Optional.of(InstanceMapper.toDTO(instanceOptional));
  }

  @Override
  public List<InstanceDTO> getActiveInstancesByAccount(String accountIdentifier, long timestamp) {
    return InstanceMapper.toDTO(instanceRepository.getActiveInstancesByAccount(accountIdentifier, timestamp));
  }

  @Override
  public List<InstanceDTO> getInstancesDeployedInInterval(
      String accountIdentifier, long startTimestamp, long endTimeStamp) {
    return InstanceMapper.toDTO(
        instanceRepository.getInstancesDeployedInInterval(accountIdentifier, startTimestamp, endTimeStamp));
  }

  @Override
  public List<InstanceDTO> getInstancesDeployedInInterval(
      String accountIdentifier, String organizationId, String projectId, long startTimestamp, long endTimeStamp) {
    return InstanceMapper.toDTO(instanceRepository.getInstancesDeployedInInterval(
        accountIdentifier, organizationId, projectId, startTimestamp, endTimeStamp));
  }

  @Override
  public List<InstanceDTO> getInstances(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String infrastructureMappingId) {
    return InstanceMapper.toDTO(
        instanceRepository.getInstances(accountIdentifier, orgIdentifier, projectIdentifier, infrastructureMappingId));
  }

  /*
    Returns list of active instances for given account+org+project at given timestamp
  */
  @Override
  public List<InstanceDTO> getActiveInstances(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs) {
    return InstanceMapper.toDTO(
        instanceRepository.getActiveInstances(accountIdentifier, orgIdentifier, projectIdentifier, timestampInMs));
  }

  /*
    Returns list of active instances for given account+org+project+service at given timestamp
  */
  @Override
  public List<InstanceDTO> getActiveInstancesByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    return InstanceMapper.toDTO(instanceRepository.getActiveInstancesByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs));
  }

  @Override
  public List<InstanceDTO> getActiveInstancesByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    return InstanceMapper.toDTO(instanceRepository.getActiveInstancesByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId));
  }

  @Override
  public List<InstanceDTO> getActiveInstancesByInfrastructureMappingId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String infrastructureMappingId) {
    return InstanceMapper.toDTO(instanceRepository.getActiveInstancesByInfrastructureMappingId(
        accountIdentifier, orgIdentifier, projectIdentifier, infrastructureMappingId));
  }

  @Override
  public List<InstanceDTO> getActiveInstancesByInstanceInfo(
      String accountIdentifier, String instanceInfoNamespace, String instanceInfoPodName) {
    return InstanceMapper.toDTO(instanceRepository.getActiveInstancesByInstanceInfo(
        accountIdentifier, instanceInfoNamespace, instanceInfoPodName));
  }

  /*
    Returns aggregated result containing unique environment and build ids with instance count
  */
  @Override
  public AggregationResults<EnvBuildInstanceCount> getEnvBuildInstanceCountByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    return instanceRepository.getEnvBuildInstanceCountByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs);
  }

  @Override
  public AggregationResults<ActiveServiceInstanceInfo> getActiveServiceInstanceInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    return instanceRepository.getActiveServiceInstanceInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
  }

  /*
    Returns aggregated result containing total {limit} instances for given buildIds
   */
  @Override
  public AggregationResults<InstancesByBuildId> getActiveInstancesByServiceIdEnvIdAndBuildIds(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String envId, List<String> buildIds,
      long timestampInMs, int limit) {
    return instanceRepository.getActiveInstancesByServiceIdEnvIdAndBuildIds(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, envId, buildIds, timestampInMs, limit);
  }

  /*
    Returns breakup of active instances by envType at a given timestamp for specified accountIdentifier,
    projectIdentifier, orgIdentifier and serviceIds
  */
  @Override
  public AggregationResults<CountByServiceIdAndEnvType> getActiveServiceInstanceCountBreakdown(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<String> serviceId, long timestampInMs) {
    return instanceRepository.getActiveServiceInstanceCountBreakdown(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs);
  }

  @Override
  public void updateInfrastructureMapping(List<String> instanceIds, String infrastructureMappingId) {
    UpdateResult updateResult = instanceRepository.updateInfrastructureMapping(instanceIds, infrastructureMappingId);
    log.info("Updated infrastructure mapping for {} instances", updateResult.getModifiedCount());
  }

  // ----------------------------------- PRIVATE METHODS -------------------------------------

  private Instance undeleteInstance(Instance instance) {
    Criteria criteria = Criteria.where(InstanceKeys.instanceKey)
                            .is(instance.getInstanceKey())
                            .and(InstanceKeys.infrastructureMappingId)
                            .is(instance.getInfrastructureMappingId())
                            .and(InstanceKeys.isDeleted)
                            .is(true);
    instance.setDeleted(false);
    instance.setDeletedAt(0);
    return instanceRepository.findAndReplace(criteria, instance);
  }
}
