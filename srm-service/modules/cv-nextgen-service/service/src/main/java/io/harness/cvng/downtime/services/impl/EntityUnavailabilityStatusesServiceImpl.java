/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.downtime.beans.DataCollectionFailureInstanceDetails;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeInstanceDetails;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses.EntityUnavailabilityStatusesKeys;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.downtime.transformer.EntityUnavailabilityStatusesEntityAndDTOTransformer;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventDetails;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventDetailsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType;
import io.harness.cvng.servicelevelobjective.services.api.SecondaryEventDetailsService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class EntityUnavailabilityStatusesServiceImpl
    implements EntityUnavailabilityStatusesService, SecondaryEventDetailsService {
  @Inject private HPersistence hPersistence;

  @Inject private Clock clock;

  @Inject private EntityUnavailabilityStatusesEntityAndDTOTransformer statusesEntityAndDTOTransformer;

  @Override
  public void create(
      ProjectParams projectParams, List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS) {
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        entityUnavailabilityStatusesDTOS.stream()
            .map(statusesDTO -> statusesEntityAndDTOTransformer.getEntity(projectParams, statusesDTO))
            .collect(Collectors.toList());
    hPersistence.save(entityUnavailabilityStatuses);
  }

  @Override
  public void update(ProjectParams projectParams, String entityId,
      List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS) {
    deleteFutureDowntimeInstances(projectParams, entityId);
    List<EntityUnavailabilityStatuses> runningEntityUnavailabilityStatuses =
        updateRunningInstance(projectParams, entityId);
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        entityUnavailabilityStatusesDTOS.stream()
            .map(statusesDTO -> statusesEntityAndDTOTransformer.getEntity(projectParams, statusesDTO))
            .collect(Collectors.toList());
    entityUnavailabilityStatuses.addAll(runningEntityUnavailabilityStatuses);
    hPersistence.save(entityUnavailabilityStatuses);
  }

  @Override
  public void updateAndSaveRunningInstance(ProjectParams projectParams, String entityId) {
    List<EntityUnavailabilityStatuses> runningEntityUnavailabilityStatuses =
        updateRunningInstance(projectParams, entityId);
    hPersistence.save(runningEntityUnavailabilityStatuses);
  }
  @Override
  public List<EntityUnavailabilityStatusesDTO> getEntityUnavaialabilityStatusesDTOs(
      ProjectParams projectParams, DowntimeDTO downtimeDTO, List<Pair<Long, Long>> futureInstances) {
    List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS = new ArrayList<>();
    for (Pair<Long, Long> startAndEndTime : futureInstances) {
      entityUnavailabilityStatusesDTOS.add(EntityUnavailabilityStatusesDTO.builder()
                                               .entityId(downtimeDTO.getIdentifier())
                                               .orgIdentifier(projectParams.getOrgIdentifier())
                                               .projectIdentifier(projectParams.getProjectIdentifier())
                                               .status(EntityUnavailabilityStatus.MAINTENANCE_WINDOW)
                                               .entityType(EntityType.MAINTENANCE_WINDOW)
                                               .startTime(startAndEndTime.getLeft())
                                               .endTime(startAndEndTime.getRight())
                                               .entitiesRule(downtimeDTO.getEntitiesRule())
                                               .build());
    }
    return entityUnavailabilityStatusesDTOS;
  }
  @Override
  public List<EntityUnavailabilityStatusesDTO> getPastInstances(ProjectParams projectParams) {
    List<EntityUnavailabilityStatuses> pastInstances = getPastInstancesQuery(projectParams).asList();
    return pastInstances.stream()
        .map(status -> statusesEntityAndDTOTransformer.getDto(status))
        .collect(Collectors.toList());
  }

  @Override
  public List<EntityUnavailabilityStatusesDTO> getPastAndActiveDowntimeInstances(
      ProjectParams projectParams, List<String> entityIds) {
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        getPastOrActiveDowntimeInstancesQuery(projectParams, entityIds).asList();
    return entityUnavailabilityStatuses.stream()
        .map(status -> statusesEntityAndDTOTransformer.getDto(status))
        .collect(Collectors.toList());
  }

  @Override
  public EntityUnavailabilityStatuses getInstanceByUuid(String uuid) {
    return hPersistence.get(EntityUnavailabilityStatuses.class, uuid);
  }

  @Override
  public List<EntityUnavailabilityStatusesDTO> getAllInstances(ProjectParams projectParams) {
    List<EntityUnavailabilityStatuses> allInstances = getAllInstancesQuery(projectParams).asList();
    return allInstances.stream()
        .map(status -> statusesEntityAndDTOTransformer.getDto(status))
        .collect(Collectors.toList());
  }

  @Override
  public List<EntityUnavailabilityStatusesDTO> getAllInstances(
      ProjectParams projectParams, EntityType entityType, String entityIdentifier) {
    List<EntityUnavailabilityStatuses> allInstances =
        getAllInstancesQuery(projectParams, entityType, entityIdentifier).asList();
    return allInstances.stream()
        .map(status -> statusesEntityAndDTOTransformer.getDto(status))
        .collect(Collectors.toList());
  }

  @Override
  public List<EntityUnavailabilityStatusesDTO> getAllInstances(
      ProjectParams projectParams, long startTime, long endTime) {
    List<EntityUnavailabilityStatuses> allInstances =
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .disableValidation()
            .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
            .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .field(EntityUnavailabilityStatusesKeys.startTime)
            .lessThanOrEq(endTime)
            .field(EntityUnavailabilityStatusesKeys.endTime)
            .greaterThanOrEq(startTime)
            .order(Sort.ascending(EntityUnavailabilityStatusesKeys.startTime))
            .asList();
    return allInstances.stream()
        .map(status -> statusesEntityAndDTOTransformer.getDto(status))
        .collect(Collectors.toList());
  }

  @Override
  public List<EntityUnavailabilityStatuses> getAllUnavailabilityInstances(
      ProjectParams projectParams, long startTime, long endTime) {
    return hPersistence.createQuery(EntityUnavailabilityStatuses.class)
        .disableValidation()
        .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(EntityUnavailabilityStatusesKeys.startTime)
        .lessThanOrEq(endTime)
        .field(EntityUnavailabilityStatusesKeys.endTime)
        .greaterThanOrEq(startTime)
        .order(Sort.ascending(EntityUnavailabilityStatusesKeys.startTime))
        .asList();
  }

  @Override
  public List<EntityUnavailabilityStatusesDTO> getActiveOrFirstUpcomingInstance(
      ProjectParams projectParams, List<String> entityIds) {
    Query<EntityUnavailabilityStatuses> query =
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
            .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .field(EntityUnavailabilityStatusesKeys.entityIdentifier)
            .in(entityIds);
    query.or(query.criteria(EntityUnavailabilityStatusesKeys.startTime).greaterThanOrEq(clock.millis() / 1000),
        query.and(query.criteria(EntityUnavailabilityStatusesKeys.startTime).lessThanOrEq(clock.millis() / 1000),
            query.criteria(EntityUnavailabilityStatusesKeys.endTime).greaterThanOrEq(clock.millis() / 1000)));
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        query.order(Sort.ascending(EntityUnavailabilityStatusesKeys.startTime)).asList();
    Map<String, EntityUnavailabilityStatuses> firstUnavailabilityInstances = new HashMap<>();
    for (EntityUnavailabilityStatuses downtime : entityUnavailabilityStatuses) {
      firstUnavailabilityInstances.putIfAbsent(downtime.getEntityIdentifier(), downtime);
    }
    return firstUnavailabilityInstances.values()
        .stream()
        .map(status -> statusesEntityAndDTOTransformer.getDto(status))
        .collect(Collectors.toList());
  }

  @Override
  public SecondaryEventDetailsResponse getInstanceByUuids(List<String> uuids, SecondaryEventsType eventType) {
    EntityUnavailabilityStatuses instance = getInstanceByUuid(uuids.get(0));
    SecondaryEventDetails details = null;
    if (eventType == SecondaryEventsType.DOWNTIME) {
      details = DowntimeInstanceDetails.builder().build();
    } else if (eventType == SecondaryEventsType.DATA_COLLECTION_FAILURE) {
      details = DataCollectionFailureInstanceDetails.builder().build();
    }
    return SecondaryEventDetailsResponse.builder()
        .type(eventType)
        .startTime(instance.getStartTime())
        .endTime(instance.getEndTime())
        .details(details)
        .build();
  }

  @Override
  public boolean deleteFutureDowntimeInstances(ProjectParams projectParams, String entityId) {
    return hPersistence.delete(getFutureInstances(projectParams, entityId));
  }

  @Override
  public boolean deleteAllInstances(ProjectParams projectParams, String entityId) {
    return hPersistence.delete(
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
            .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(EntityUnavailabilityStatusesKeys.entityIdentifier, entityId));
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<EntityUnavailabilityStatuses> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .filter(EntityUnavailabilityStatusesKeys.accountId, accountId)
            .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, orgIdentifier)
            .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectIdentifier)
            .asList();
    entityUnavailabilityStatuses.forEach(entityUnavailabilityStatus
        -> deleteAllInstances(ProjectParams.builder()
                                  .accountIdentifier(entityUnavailabilityStatus.getAccountId())
                                  .orgIdentifier(entityUnavailabilityStatus.getOrgIdentifier())
                                  .projectIdentifier(entityUnavailabilityStatus.getProjectIdentifier())
                                  .build(),
            entityUnavailabilityStatus.getEntityIdentifier()));
  }

  @Override
  public void deleteByOrgIdentifier(Class<EntityUnavailabilityStatuses> clazz, String accountId, String orgIdentifier) {
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .filter(EntityUnavailabilityStatusesKeys.accountId, accountId)
            .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, orgIdentifier)
            .asList();
    entityUnavailabilityStatuses.forEach(entityUnavailabilityStatus
        -> deleteAllInstances(ProjectParams.builder()
                                  .accountIdentifier(entityUnavailabilityStatus.getAccountId())
                                  .orgIdentifier(entityUnavailabilityStatus.getOrgIdentifier())
                                  .projectIdentifier(entityUnavailabilityStatus.getProjectIdentifier())
                                  .build(),
            entityUnavailabilityStatus.getEntityIdentifier()));
  }

  @Override
  public void deleteByAccountIdentifier(Class<EntityUnavailabilityStatuses> clazz, String accountId) {
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .filter(EntityUnavailabilityStatusesKeys.accountId, accountId)
            .asList();
    entityUnavailabilityStatuses.forEach(entityUnavailabilityStatus
        -> deleteAllInstances(ProjectParams.builder()
                                  .accountIdentifier(entityUnavailabilityStatus.getAccountId())
                                  .orgIdentifier(entityUnavailabilityStatus.getOrgIdentifier())
                                  .projectIdentifier(entityUnavailabilityStatus.getProjectIdentifier())
                                  .build(),
            entityUnavailabilityStatus.getEntityIdentifier()));
  }

  private List<EntityUnavailabilityStatuses> updateRunningInstance(ProjectParams projectParams, String entityId) {
    List<EntityUnavailabilityStatuses> runningEntityUnavailabilityStatuses =
        getCurrentRunningInstance(projectParams, entityId).asList();
    runningEntityUnavailabilityStatuses.forEach(instance -> instance.setEndTime(clock.millis() / 1000));
    return runningEntityUnavailabilityStatuses;
  }

  private Query<EntityUnavailabilityStatuses> getAllInstancesQuery(ProjectParams projectParams) {
    return hPersistence.createQuery(EntityUnavailabilityStatuses.class)
        .disableValidation()
        .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .order(Sort.descending(EntityUnavailabilityStatusesKeys.endTime));
  }
  private Query<EntityUnavailabilityStatuses> getPastInstancesQuery(ProjectParams projectParams) {
    return hPersistence.createQuery(EntityUnavailabilityStatuses.class)
        .disableValidation()
        .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(EntityUnavailabilityStatusesKeys.endTime)
        .lessThanOrEq(clock.millis() / 1000)
        .order(Sort.descending(EntityUnavailabilityStatusesKeys.endTime));
  }

  private Query<EntityUnavailabilityStatuses> getPastOrActiveDowntimeInstancesQuery(
      ProjectParams projectParams, List<String> entityIds) {
    return hPersistence.createQuery(EntityUnavailabilityStatuses.class)
        .disableValidation()
        .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.entityType, EntityType.MAINTENANCE_WINDOW)
        .field(EntityUnavailabilityStatusesKeys.entityIdentifier)
        .in(entityIds)
        .field(EntityUnavailabilityStatusesKeys.startTime)
        .lessThanOrEq(clock.millis() / 1000)
        .order(Sort.descending(EntityUnavailabilityStatusesKeys.endTime));
  }

  private Query<EntityUnavailabilityStatuses> getAllInstancesQuery(
      ProjectParams projectParams, EntityType entityType, String entityId) {
    return hPersistence.createQuery(EntityUnavailabilityStatuses.class)
        .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.entityType, entityType)
        .filter(EntityUnavailabilityStatusesKeys.entityIdentifier, entityId);
  }

  private Query<EntityUnavailabilityStatuses> getFutureInstances(ProjectParams projectParams, String entityId) {
    return hPersistence.createQuery(EntityUnavailabilityStatuses.class)
        .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.entityIdentifier, entityId)
        .field(EntityUnavailabilityStatusesKeys.startTime)
        .greaterThanOrEq(clock.millis() / 1000);
  }

  private Query<EntityUnavailabilityStatuses> getCurrentRunningInstance(ProjectParams projectParams, String entityId) {
    return hPersistence.createQuery(EntityUnavailabilityStatuses.class)
        .filter(EntityUnavailabilityStatusesKeys.accountId, projectParams.getAccountIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(EntityUnavailabilityStatusesKeys.entityIdentifier, entityId)
        .field(EntityUnavailabilityStatusesKeys.startTime)
        .lessThanOrEq(clock.millis() / 1000)
        .field(EntityUnavailabilityStatusesKeys.endTime)
        .greaterThanOrEq(clock.millis() / 1000);
  }
}
