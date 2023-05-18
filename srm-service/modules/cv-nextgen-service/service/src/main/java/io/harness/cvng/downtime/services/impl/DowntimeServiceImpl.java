/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.beans.AffectedEntity;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeDashboardFilter;
import io.harness.cvng.downtime.beans.DowntimeHistoryView;
import io.harness.cvng.downtime.beans.DowntimeListView;
import io.harness.cvng.downtime.beans.DowntimeResponse;
import io.harness.cvng.downtime.beans.DowntimeSpec;
import io.harness.cvng.downtime.beans.DowntimeSpecDTO;
import io.harness.cvng.downtime.beans.DowntimeStatusDetails;
import io.harness.cvng.downtime.beans.DowntimeType;
import io.harness.cvng.downtime.beans.EntitiesRule;
import io.harness.cvng.downtime.beans.EntityDetails;
import io.harness.cvng.downtime.beans.EntityIdentifiersRule;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeType;
import io.harness.cvng.downtime.beans.RecurringDowntimeSpec;
import io.harness.cvng.downtime.beans.RuleType;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.entities.Downtime.DowntimeDetails;
import io.harness.cvng.downtime.entities.Downtime.DowntimeKeys;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.downtime.transformer.DowntimeSpecDetailsTransformer;
import io.harness.cvng.downtime.utils.DateTimeUtils;
import io.harness.cvng.downtime.utils.DowntimeUtils;
import io.harness.cvng.events.downtime.DowntimeCreateEvent;
import io.harness.cvng.events.downtime.DowntimeDeleteEvent;
import io.harness.cvng.events.downtime.DowntimeUpdateEvent;
import io.harness.cvng.servicelevelobjective.beans.MSDropdownResponse;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.utils.PageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class DowntimeServiceImpl implements DowntimeService {
  @Inject private HPersistence hPersistence;
  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;
  @Inject private Map<DowntimeType, DowntimeSpecDetailsTransformer> downtimeTransformerMap;

  @Inject private OutboxService outboxService;

  @Inject private MonitoredServiceService monitoredServiceService;

  @Inject private Clock clock;

  private static final int MAX_DURATION_IN_DAYS = 3 * 365;
  @Override
  public DowntimeResponse create(ProjectParams projectParams, DowntimeDTO downtimeDTO) {
    validateCreate(projectParams, downtimeDTO);
    Downtime downtime = getDowntimeFromDowntimeDTO(projectParams, downtimeDTO);
    hPersistence.save(downtime);
    if (downtime.isEnabled()) {
      List<Pair<Long, Long>> futureInstances =
          downtimeTransformerMap.get(downtimeDTO.getSpec().getType())
              .getStartAndEndTimesForFutureInstances(downtimeDTO.getSpec().getSpec());
      List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
          entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
              projectParams, downtimeDTO, futureInstances);
      entityUnavailabilityStatusesService.create(projectParams, entityUnavailabilityStatusesDTOS);
    }
    outboxService.save(DowntimeCreateEvent.builder()
                           .accountIdentifier(projectParams.getAccountIdentifier())
                           .orgIdentifier(projectParams.getOrgIdentifier())
                           .projectIdentifier(projectParams.getProjectIdentifier())
                           .downtimeIdentifier(downtime.getIdentifier())
                           .resourceName(downtime.getName())
                           .downtimeDTO(downtimeDTO)
                           .build());
    log.info("Saved downtime with identifier {} for account {}, org {}, and project {}", downtimeDTO.getIdentifier(),
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    return DowntimeResponse.builder()
        .downtimeDTO(downtimeDTO)
        .createdAt(downtime.getCreatedAt())
        .lastModifiedAt(downtime.getLastUpdatedAt())
        .build();
  }

  @Override
  public DowntimeResponse get(ProjectParams projectParams, String identifier) {
    Downtime downtime = getEntity(projectParams, identifier);
    DowntimeDTO downtimeDTO = getDowntimeDTOFromDowntime(downtime);
    return DowntimeResponse.builder()
        .downtimeDTO(downtimeDTO)
        .createdAt(downtime.getCreatedAt())
        .lastModifiedAt(downtime.getLastUpdatedAt())
        .build();
  }

  @Override
  public List<MonitoredServiceDetail> getAssociatedMonitoredServices(ProjectParams projectParams, String identifier) {
    Downtime downtime = getEntity(projectParams, identifier);
    if (downtime.getEntitiesRule().getType().equals(RuleType.IDENTFIERS)) {
      Set<String> monitoredServiceIdentifiers = ((EntityIdentifiersRule) downtime.getEntitiesRule())
                                                    .getEntityIdentifiers()
                                                    .stream()
                                                    .map(EntityDetails::getEntityRef)
                                                    .collect(Collectors.toSet());
      return monitoredServiceService.getMonitoredServiceDetails(projectParams, monitoredServiceIdentifiers);
    } else {
      return monitoredServiceService.getAllMonitoredServiceDetails(projectParams);
    }
  }

  @Override
  public PageResponse<MSDropdownResponse> getDowntimeAssociatedMonitoredServices(
      ProjectParams projectParams, PageParams pageParams) {
    List<Downtime> downtimes = get(projectParams);
    List<MonitoredServiceDetail> monitoredServiceDetails;
    List<Downtime> allMonitoredServicesAssociatedDowntime =
        downtimes.stream()
            .filter(downtime -> downtime.getEntitiesRule().getType().equals(RuleType.ALL))
            .collect(Collectors.toList());
    if (!allMonitoredServicesAssociatedDowntime.isEmpty()) {
      monitoredServiceDetails = monitoredServiceService.getAllMonitoredServiceDetails(projectParams);
    } else {
      Set<String> monitoredServiceIdentifiers = new HashSet<>();
      downtimes.forEach(downtime
          -> monitoredServiceIdentifiers.addAll(((EntityIdentifiersRule) downtime.getEntitiesRule())
                                                    .getEntityIdentifiers()
                                                    .stream()
                                                    .map(EntityDetails::getEntityRef)
                                                    .collect(Collectors.toSet())));
      monitoredServiceDetails =
          monitoredServiceService.getMonitoredServiceDetails(projectParams, monitoredServiceIdentifiers);
    }
    List<MSDropdownResponse> msDropdownResponseList =
        monitoredServiceDetails.stream().map(this::getMSDropdownResponse).collect(Collectors.toList());

    return PageUtils.offsetAndLimit(msDropdownResponseList, pageParams.getPage(), pageParams.getSize());
  }

  @Override
  public Map<String, EntityUnavailabilityStatusesDTO> getMonitoredServicesAssociatedUnavailabilityInstanceMap(
      ProjectParams projectParams, Set<String> msIdentifiers) {
    List<Downtime> downtimes = get(projectParams);
    List<Downtime> filteredDowntimes = filterDowntimesOnMonitoredServices(downtimes, msIdentifiers);
    List<String> downtimeIdentifiers =
        filteredDowntimes.stream().map(Downtime::getIdentifier).collect(Collectors.toList());
    List<EntityUnavailabilityStatusesDTO> activeOrFirstUpcomingInstances =
        entityUnavailabilityStatusesService.getActiveOrFirstUpcomingInstance(projectParams, downtimeIdentifiers);

    Map<String, EntityUnavailabilityStatusesDTO> monitoredServiceIdentifierToUnavailabilityStatusesDTOMap =
        new HashMap<>();
    activeOrFirstUpcomingInstances.forEach(instance -> {
      EntitiesRule entitiesRule = instance.getEntitiesRule();
      if (entitiesRule.getType().equals(RuleType.ALL)) {
        msIdentifiers.forEach(identifier
            -> addToMonitoredServiceIdentifierToUnavailabilityStatusesDTOMap(
                identifier, monitoredServiceIdentifierToUnavailabilityStatusesDTOMap, instance));
      } else {
        List<EntityDetails> entityDetails = ((EntityIdentifiersRule) entitiesRule).getEntityIdentifiers();
        entityDetails.forEach(detail
            -> addToMonitoredServiceIdentifierToUnavailabilityStatusesDTOMap(
                detail.getEntityRef(), monitoredServiceIdentifierToUnavailabilityStatusesDTOMap, instance));
      }
    });
    return monitoredServiceIdentifierToUnavailabilityStatusesDTOMap;
  }

  @Override
  public DowntimeResponse update(ProjectParams projectParams, String identifier, DowntimeDTO downtimeDTO) {
    validateUpdate(projectParams, identifier, downtimeDTO);
    Optional<Downtime> downtimeOptional = getOptionalDowntime(projectParams, identifier);
    if (downtimeOptional.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    return update(projectParams, identifier, downtimeOptional.get(), downtimeDTO);
  }

  @Override
  public DowntimeResponse enableOrDisable(ProjectParams projectParams, String identifier, boolean enable) {
    Optional<Downtime> downtimeOptional = getOptionalDowntime(projectParams, identifier);
    if (downtimeOptional.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    DowntimeDTO existingDowntimeDTO = getDowntimeDTOFromDowntime(downtimeOptional.get());
    if (existingDowntimeDTO.isEnabled() == enable) {
      log.info(String.format("Downtime with identifier %s was already in %s state", identifier, enable));
      return DowntimeResponse.builder()
          .createdAt(downtimeOptional.get().getCreatedAt())
          .lastModifiedAt(downtimeOptional.get().getLastUpdatedAt())
          .downtimeDTO(existingDowntimeDTO)
          .build();
    }
    DowntimeDTO updatedDowntimeDTO = existingDowntimeDTO;
    updatedDowntimeDTO.setEnabled(enable);
    return update(projectParams, identifier, downtimeOptional.get(), updatedDowntimeDTO);
  }

  private DowntimeResponse update(
      ProjectParams projectParams, String identifier, Downtime existingDowntime, DowntimeDTO updatedDowntimeDTO) {
    DowntimeDTO existingDowntimeDTO = getDowntimeDTOFromDowntime(existingDowntime);
    validateNotAllowedFieldsChanges(existingDowntimeDTO, updatedDowntimeDTO);
    Downtime updatedDowntime = updateDowntimeEntity(projectParams, updatedDowntimeDTO, existingDowntime);
    updatedDowntimeDTO = getDowntimeDTOFromDowntime(updatedDowntime);
    if ((!updatedDowntimeDTO.getSpec().equals(existingDowntimeDTO.getSpec()) || !existingDowntimeDTO.isEnabled()
            || !existingDowntimeDTO.getEntitiesRule().equals(updatedDowntimeDTO.getEntitiesRule()))
        && updatedDowntimeDTO.isEnabled()) {
      List<Pair<Long, Long>> futureInstances =
          downtimeTransformerMap.get(updatedDowntimeDTO.getSpec().getType())
              .getStartAndEndTimesForFutureInstances(updatedDowntimeDTO.getSpec().getSpec());
      List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS =
          entityUnavailabilityStatusesService.getEntityUnavaialabilityStatusesDTOs(
              projectParams, updatedDowntimeDTO, futureInstances);
      entityUnavailabilityStatusesService.update(
          projectParams, updatedDowntimeDTO.getIdentifier(), entityUnavailabilityStatusesDTOS);
    } else if (!updatedDowntimeDTO.isEnabled()) {
      entityUnavailabilityStatusesService.deleteFutureDowntimeInstances(projectParams, identifier);
      entityUnavailabilityStatusesService.updateAndSaveRunningInstance(projectParams, identifier);
    }
    outboxService.save(DowntimeUpdateEvent.builder()
                           .accountIdentifier(projectParams.getAccountIdentifier())
                           .orgIdentifier(projectParams.getOrgIdentifier())
                           .projectIdentifier(projectParams.getProjectIdentifier())
                           .downtimeIdentifier(updatedDowntimeDTO.getIdentifier())
                           .resourceName(updatedDowntimeDTO.getName())
                           .newDowntimeDTO(updatedDowntimeDTO)
                           .oldDowntimeDTO(existingDowntimeDTO)
                           .build());
    return DowntimeResponse.builder()
        .createdAt(updatedDowntime.getCreatedAt())
        .lastModifiedAt(updatedDowntime.getLastUpdatedAt())
        .downtimeDTO(updatedDowntimeDTO)
        .build();
  }

  @Override
  public boolean delete(ProjectParams projectParams, String identifier) {
    Optional<Downtime> downtimeOptional = getOptionalDowntime(projectParams, identifier);
    if (downtimeOptional.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    Downtime downtime = downtimeOptional.get();
    DowntimeDTO downtimeDTO = getDowntimeDTOFromDowntime(downtime);
    List<EntityUnavailabilityStatusesDTO> pastOrActiveInstances =
        entityUnavailabilityStatusesService.getPastAndActiveDowntimeInstances(
            projectParams, Collections.singletonList(identifier));
    if (!pastOrActiveInstances.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s can't be deleted, as it has a a past/current instance of downtime, where deleting it can impact SLO adversely.",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    entityUnavailabilityStatusesService.deleteFutureDowntimeInstances(projectParams, identifier);
    entityUnavailabilityStatusesService.updateAndSaveRunningInstance(projectParams, identifier);
    outboxService.save(DowntimeDeleteEvent.builder()
                           .resourceName(downtime.getName())
                           .downtimeDTO(downtimeDTO)
                           .accountIdentifier(projectParams.getAccountIdentifier())
                           .orgIdentifier(projectParams.getOrgIdentifier())
                           .projectIdentifier(projectParams.getProjectIdentifier())
                           .downtimeIdentifier(downtime.getIdentifier())
                           .build());
    return hPersistence.delete(downtime);
  }

  @Override
  public PageResponse<DowntimeListView> list(
      ProjectParams projectParams, PageParams pageParams, DowntimeDashboardFilter filter) {
    return getDowntimeListViewResponse(projectParams, pageParams.getPage(), pageParams.getSize(),
        Filter.builder()
            .monitoredServiceIdentifier(filter.getMonitoredServiceIdentifier())
            .searchFilter(filter.getSearchFilter())
            .build());
  }

  @Override
  public PageResponse<DowntimeHistoryView> history(
      ProjectParams projectParams, PageParams pageParams, DowntimeDashboardFilter filter) {
    return getDowntimeHistoryViewResponse(projectParams, pageParams.getPage(), pageParams.getSize(),
        Filter.builder()
            .monitoredServiceIdentifier(filter.getMonitoredServiceIdentifier())
            .searchFilter(filter.getSearchFilter())
            .build());
  }

  @Override
  public List<EntityUnavailabilityStatusesDTO> filterDowntimeInstancesOnMonitoredService(ProjectParams projectParams,
      List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS, String monitoredServiceIdentifier) {
    return filterDowntimeInstancesOnMonitoredServices(
        projectParams, entityUnavailabilityStatusesDTOS, Collections.singleton(monitoredServiceIdentifier));
  }

  @Override
  public List<EntityUnavailabilityStatusesDTO> filterDowntimeInstancesOnMonitoredServices(ProjectParams projectParams,
      List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS, Set<String> monitoredServiceIdentifiers) {
    return entityUnavailabilityStatusesDTOS.stream()
        .filter(entityUnavailabilityStatusesDTO
            -> monitoredServiceIdentifiers.stream().anyMatch(monitoredServiceIdentifier
                -> entityUnavailabilityStatusesDTO.getEntitiesRule().isPresent(
                    Collections.singletonMap(MonitoredServiceKeys.identifier, monitoredServiceIdentifier))))
        .collect(Collectors.toList());
  }

  @Override
  public List<EntityUnavailabilityStatuses> filterDowntimeInstancesOnMSs(ProjectParams projectParams,
      List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses, Set<String> monitoredServiceIdentifiers) {
    return entityUnavailabilityStatuses.stream()
        .filter(instance
            -> monitoredServiceIdentifiers.stream().anyMatch(monitoredServiceIdentifier
                -> instance.getEntitiesRule().isPresent(
                    Collections.singletonMap(MonitoredServiceKeys.identifier, monitoredServiceIdentifier))))
        .collect(Collectors.toList());
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<Downtime> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    List<Downtime> downtimes = hPersistence.createQuery(Downtime.class)
                                   .filter(DowntimeKeys.accountId, accountId)
                                   .filter(DowntimeKeys.orgIdentifier, orgIdentifier)
                                   .filter(DowntimeKeys.projectIdentifier, projectIdentifier)
                                   .asList();
    downtimes.forEach(downtime
        -> delete(ProjectParams.builder()
                      .accountIdentifier(downtime.getAccountId())
                      .orgIdentifier(downtime.getOrgIdentifier())
                      .projectIdentifier(downtime.getProjectIdentifier())
                      .build(),
            downtime.getIdentifier()));
    downtimes.forEach(downtime
        -> entityUnavailabilityStatusesService.deleteAllInstances(
            ProjectParams.builder()
                .accountIdentifier(downtime.getAccountId())
                .orgIdentifier(downtime.getOrgIdentifier())
                .projectIdentifier(downtime.getProjectIdentifier())
                .build(),
            downtime.getIdentifier()));
  }

  @Override
  public void deleteByOrgIdentifier(Class<Downtime> clazz, String accountId, String orgIdentifier) {
    List<Downtime> downtimes = hPersistence.createQuery(Downtime.class)
                                   .filter(DowntimeKeys.accountId, accountId)
                                   .filter(DowntimeKeys.orgIdentifier, orgIdentifier)
                                   .asList();
    downtimes.forEach(downtime
        -> delete(ProjectParams.builder()
                      .accountIdentifier(downtime.getAccountId())
                      .orgIdentifier(downtime.getOrgIdentifier())
                      .projectIdentifier(downtime.getProjectIdentifier())
                      .build(),
            downtime.getIdentifier()));
    downtimes.forEach(downtime
        -> entityUnavailabilityStatusesService.deleteAllInstances(
            ProjectParams.builder()
                .accountIdentifier(downtime.getAccountId())
                .orgIdentifier(downtime.getOrgIdentifier())
                .projectIdentifier(downtime.getProjectIdentifier())
                .build(),
            downtime.getIdentifier()));
  }

  @Override
  public void deleteByAccountIdentifier(Class<Downtime> clazz, String accountId) {
    List<Downtime> downtimes =
        hPersistence.createQuery(Downtime.class).filter(DowntimeKeys.accountId, accountId).asList();
    downtimes.forEach(downtime
        -> delete(ProjectParams.builder()
                      .accountIdentifier(downtime.getAccountId())
                      .orgIdentifier(downtime.getOrgIdentifier())
                      .projectIdentifier(downtime.getProjectIdentifier())
                      .build(),
            downtime.getIdentifier()));
    downtimes.forEach(downtime
        -> entityUnavailabilityStatusesService.deleteAllInstances(
            ProjectParams.builder()
                .accountIdentifier(downtime.getAccountId())
                .orgIdentifier(downtime.getOrgIdentifier())
                .projectIdentifier(downtime.getProjectIdentifier())
                .build(),
            downtime.getIdentifier()));
  }

  @Override
  public Downtime getEntity(ProjectParams projectParams, String identifier) {
    return hPersistence.createQuery(Downtime.class)
        .filter(DowntimeKeys.accountId, projectParams.getAccountIdentifier())
        .filter(DowntimeKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(DowntimeKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(DowntimeKeys.identifier, identifier)
        .get();
  }

  private PageResponse<DowntimeHistoryView> getDowntimeHistoryViewResponse(
      ProjectParams projectParams, Integer offset, Integer pageSize, Filter filter) {
    List<EntityUnavailabilityStatusesDTO> pastDowntimeInstances =
        entityUnavailabilityStatusesService.getPastInstances(projectParams)
            .stream()
            .filter(statusesDTO -> statusesDTO.getEntityType().equals(EntityType.MAINTENANCE_WINDOW))
            .collect(Collectors.toList());
    Set<String> downtimeIdentifiers =
        pastDowntimeInstances.stream().map(EntityUnavailabilityStatusesDTO::getEntityId).collect(Collectors.toSet());
    List<Downtime> downtimes = get(projectParams, downtimeIdentifiers);
    if (!isEmpty(filter.getSearchFilter())) {
      downtimes = filterDowntimes(downtimes, filter.getSearchFilter());
    }
    if (filter.getMonitoredServiceIdentifier() != null) {
      downtimes = filterDowntimesOnMonitoredService(downtimes, filter.getMonitoredServiceIdentifier());
    }

    Map<String, Downtime> identifierToDowntimeMap =
        downtimes.stream().collect(Collectors.toMap(Downtime::getIdentifier, downtime -> downtime));
    Set<String> monitoredServicesIdentifiers =
        pastDowntimeInstances.stream()
            .filter(instance -> instance.getEntitiesRule().getType().equals(RuleType.IDENTFIERS))
            .map(instance -> ((EntityIdentifiersRule) instance.getEntitiesRule()).getEntityIdentifiers())
            .flatMap(List::stream)
            .map(EntityDetails::getEntityRef)
            .collect(Collectors.toSet());
    Map<String, AffectedEntity> identifierAffectedEntityMap =
        getIdentifierAffectedEntityMap(projectParams, monitoredServicesIdentifiers);

    List<DowntimeHistoryView> downtimeHistoryViews = getDowntimeHistoryViewFromPastInstances(
        pastDowntimeInstances, identifierToDowntimeMap, identifierAffectedEntityMap);
    downtimeHistoryViews = downtimeHistoryViews.stream()
                               .filter(downtimeHistoryView -> downtimeHistoryView.getDuration().getDurationValue() != 0)
                               .collect(Collectors.toList());

    return PageUtils.offsetAndLimit(downtimeHistoryViews, offset, pageSize);
  }

  private List<DowntimeHistoryView> getDowntimeHistoryViewFromPastInstances(
      List<EntityUnavailabilityStatusesDTO> pastInstances, Map<String, Downtime> identifierToDowntimeMap,
      Map<String, AffectedEntity> monitoredServiceIdentifierAffectedEntityMap) {
    return pastInstances.stream()
        .filter(instance -> identifierToDowntimeMap.containsKey(instance.getEntityId()))
        .map(instance
            -> DowntimeHistoryView.builder()
                   .name(identifierToDowntimeMap.get(instance.getEntityId()).getName())
                   .category(identifierToDowntimeMap.get(instance.getEntityId()).getCategory())
                   .identifier(instance.getEntityId())
                   .startTime(instance.getStartTime())
                   .startDateTime(DateTimeUtils.getDateStringFromEpoch(
                       instance.getStartTime(), identifierToDowntimeMap.get(instance.getEntityId()).getTimezone()))
                   .endTime(instance.getEndTime())
                   .endDateTime(DateTimeUtils.getDateStringFromEpoch(
                       instance.getEndTime(), identifierToDowntimeMap.get(instance.getEntityId()).getTimezone()))
                   .spec(getDowntimeSpecDTO(identifierToDowntimeMap.get(instance.getEntityId()).getType(),
                       identifierToDowntimeMap.get(instance.getEntityId()).getDowntimeDetails(),
                       identifierToDowntimeMap.get(instance.getEntityId()).getTimezone()))
                   .downtimeDetails(getDowntimeSpecDTO(identifierToDowntimeMap.get(instance.getEntityId()).getType(),
                       identifierToDowntimeMap.get(instance.getEntityId()).getDowntimeDetails(),
                       identifierToDowntimeMap.get(instance.getEntityId()).getTimezone()))
                   .duration(
                       DowntimeUtils.getDowntimeDurationFromSeconds(instance.getEndTime() - instance.getStartTime()))
                   .affectedEntities(instance.getEntitiesRule().getType().equals(RuleType.IDENTFIERS)
                           ? ((EntityIdentifiersRule) instance.getEntitiesRule())
                                 .getEntityIdentifiers()
                                 .stream()
                                 .filter(entityDetails
                                     -> monitoredServiceIdentifierAffectedEntityMap.containsKey(
                                         entityDetails.getEntityRef()))
                                 .map(entityDetails
                                     -> monitoredServiceIdentifierAffectedEntityMap.get(entityDetails.getEntityRef()))
                                 .collect(Collectors.toList())
                           : Collections.singletonList(instance.getEntitiesRule().getAffectedEntity().get()))
                   .build())
        .collect(Collectors.toList());
  }
  private Optional<Downtime> getOptionalDowntime(ProjectParams projectParams, String identifier) {
    Downtime downtime = getEntity(projectParams, identifier);
    return Optional.ofNullable(downtime);
  }

  private PageResponse<DowntimeListView> getDowntimeListViewResponse(
      ProjectParams projectParams, Integer offset, Integer pageSize, Filter filter) {
    List<Downtime> downtimes = get(projectParams);
    downtimes = removePastDowntimes(downtimes);
    if (!isEmpty(filter.getSearchFilter())) {
      downtimes = filterDowntimes(downtimes, filter.getSearchFilter());
    }
    if (filter.getMonitoredServiceIdentifier() != null) {
      downtimes = filterDowntimesOnMonitoredService(downtimes, filter.getMonitoredServiceIdentifier());
    }
    Set<String> monitoredServicesIdentifiers =
        downtimes.stream()
            .filter(downtime -> downtime.getEntitiesRule().getType().equals(RuleType.IDENTFIERS))
            .map(downtime -> ((EntityIdentifiersRule) downtime.getEntitiesRule()).getEntityIdentifiers())
            .flatMap(List::stream)
            .map(EntityDetails::getEntityRef)
            .collect(Collectors.toSet());
    Map<String, AffectedEntity> identifierAffectedEntityMap =
        getIdentifierAffectedEntityMap(projectParams, monitoredServicesIdentifiers);
    List<DowntimeListView> downtimeListViews =
        getDowntimeListViewFromDowntime(projectParams, downtimes, identifierAffectedEntityMap);
    return PageUtils.offsetAndLimit(downtimeListViews, offset, pageSize);
  }

  private Map<String, AffectedEntity> getIdentifierAffectedEntityMap(
      ProjectParams projectParams, Set<String> monitoredServicesIdentifiers) {
    List<MonitoredServiceDetail> monitoredServiceDetails =
        monitoredServiceService.getMonitoredServiceDetails(projectParams, monitoredServicesIdentifiers);
    return monitoredServiceDetails.stream().collect(Collectors.toMap(monitoredServiceDetail
        -> monitoredServiceDetail.getMonitoredServiceIdentifier(),
        monitoredServiceDetail
        -> AffectedEntity.builder()
               .monitoredServiceIdentifier(monitoredServiceDetail.getMonitoredServiceIdentifier())
               .envName(monitoredServiceDetail.getEnvironmentName())
               .serviceName(monitoredServiceDetail.getServiceName())
               .build()));
  }

  private List<Downtime> get(ProjectParams projectParams) {
    Query<Downtime> downtimeQuery = hPersistence.createQuery(Downtime.class)
                                        .disableValidation()
                                        .filter(DowntimeKeys.accountId, projectParams.getAccountIdentifier())
                                        .filter(DowntimeKeys.orgIdentifier, projectParams.getOrgIdentifier())
                                        .filter(DowntimeKeys.projectIdentifier, projectParams.getProjectIdentifier())
                                        .order(Sort.descending(DowntimeKeys.lastUpdatedAt));
    return downtimeQuery.asList();
  }

  private List<Downtime> get(ProjectParams projectParams, Set<String> downtimeIdentifiers) {
    Query<Downtime> downtimeQuery = hPersistence.createQuery(Downtime.class)
                                        .disableValidation()
                                        .filter(DowntimeKeys.accountId, projectParams.getAccountIdentifier())
                                        .filter(DowntimeKeys.orgIdentifier, projectParams.getOrgIdentifier())
                                        .filter(DowntimeKeys.projectIdentifier, projectParams.getProjectIdentifier())
                                        .field(DowntimeKeys.identifier)
                                        .in(downtimeIdentifiers)
                                        .order(Sort.descending(DowntimeKeys.lastUpdatedAt));
    return downtimeQuery.asList();
  }

  private List<Downtime> filterDowntimesOnMonitoredService(
      List<Downtime> downtimes, String monitoredServiceIdentifier) {
    return filterDowntimesOnMonitoredServices(downtimes, Collections.singleton(monitoredServiceIdentifier));
  }

  private List<Downtime> filterDowntimesOnMonitoredServices(
      List<Downtime> downtimes, Set<String> monitoredServiceIdentifiers) {
    return downtimes.stream()
        .filter(downtime
            -> monitoredServiceIdentifiers.stream().anyMatch(monitoredServiceIdentifier
                -> downtime.getEntitiesRule().isPresent(
                    Collections.singletonMap(MonitoredServiceKeys.identifier, monitoredServiceIdentifier))))
        .collect(Collectors.toList());
  }

  private List<Downtime> filterDowntimes(List<Downtime> downtimes, String searchFilter) {
    return downtimes.stream()
        .filter(downtime -> downtime.getName().toLowerCase().contains(searchFilter.trim().toLowerCase()))
        .collect(Collectors.toList());
  }

  private List<Downtime> removePastDowntimes(List<Downtime> downtimes) {
    return downtimes.stream()
        .filter(
            downtime -> !downtimeTransformerMap.get(downtime.getType()).isPastDowntime(downtime.getDowntimeDetails()))
        .collect(Collectors.toList());
  }
  private DowntimeSpecDTO getDowntimeSpecDTO(
      DowntimeType downtimeType, DowntimeDetails downtimeDetails, String timeZone) {
    DowntimeSpec downtimeSpec = downtimeTransformerMap.get(downtimeType).getDowntimeSpec(downtimeDetails, timeZone);
    downtimeSpec.setStartDateTime(DateTimeUtils.getDateStringFromEpoch(downtimeDetails.getStartTime(), timeZone));
    downtimeSpec.setTimezone(timeZone);
    return DowntimeSpecDTO.builder().type(downtimeType).spec(downtimeSpec).build();
  }

  private DowntimeDetails getDowntimeDetails(DowntimeSpecDTO downtimeSpecDTO) {
    return downtimeTransformerMap.get(downtimeSpecDTO.getType()).getDowntimeDetails(downtimeSpecDTO.getSpec());
  }
  private DowntimeDTO getDowntimeDTOFromDowntime(Downtime downtime) {
    return DowntimeDTO.builder()
        .orgIdentifier(downtime.getOrgIdentifier())
        .projectIdentifier(downtime.getProjectIdentifier())
        .identifier(downtime.getIdentifier())
        .name(downtime.getName())
        .category(downtime.getCategory())
        .scope(downtime.getScope())
        .description(downtime.getDescription())
        .tags(TagMapper.convertToMap(downtime.getTags()))
        .enabled(downtime.isEnabled())
        .entitiesRule(downtime.getEntitiesRule())
        .spec(getDowntimeSpecDTO(downtime.getType(), downtime.getDowntimeDetails(), downtime.getTimezone()))
        .build();
  }

  @VisibleForTesting
  Downtime getDowntimeFromDowntimeDTO(ProjectParams projectParams, DowntimeDTO downtimeDTO) {
    return Downtime.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(downtimeDTO.getIdentifier())
        .name(downtimeDTO.getName())
        .category(downtimeDTO.getCategory())
        .scope(downtimeDTO.getScope())
        .description(downtimeDTO.getDescription())
        .tags(TagMapper.convertToList(downtimeDTO.getTags()))
        .type(downtimeDTO.getSpec().getType())
        .timezone(downtimeDTO.getSpec().getSpec().getTimezone())
        .enabled(downtimeDTO.isEnabled())
        .entitiesRule(downtimeDTO.getEntitiesRule())
        .downtimeDetails(getDowntimeDetails(downtimeDTO.getSpec()))
        .build();
  }

  private List<DowntimeListView> getDowntimeListViewFromDowntime(
      ProjectParams projectParams, List<Downtime> downtimes, Map<String, AffectedEntity> identifierAffectedEntityMap) {
    List<String> downtimeIdentifiers = downtimes.stream().map(Downtime::getIdentifier).collect(Collectors.toList());
    List<EntityUnavailabilityStatusesDTO> pastOrActiveInstances =
        entityUnavailabilityStatusesService.getPastAndActiveDowntimeInstances(projectParams, downtimeIdentifiers);
    List<EntityUnavailabilityStatusesDTO> activeOrFirstUpcomingInstance =
        entityUnavailabilityStatusesService.getActiveOrFirstUpcomingInstance(projectParams, downtimeIdentifiers);
    Map<String, Integer> downtimeIdentifierToPastAndActiveInstancesCountMap = new HashMap<>();
    for (EntityUnavailabilityStatusesDTO dto : pastOrActiveInstances) {
      downtimeIdentifierToPastAndActiveInstancesCountMap.merge(dto.getEntityId(), 1, Integer::sum);
    }

    Map<String, EntityUnavailabilityStatusesDTO> downtimeIdentifierToInstancesDTOMap =
        activeOrFirstUpcomingInstance.stream().collect(
            Collectors.toMap(EntityUnavailabilityStatusesDTO::getEntityId, statusesDTO -> statusesDTO));
    return downtimes.stream()
        .map(downtime
            -> DowntimeListView.builder()
                   .name(downtime.getName())
                   .category(downtime.getCategory())
                   .description(downtime.getDescription())
                   .enabled(downtime.isEnabled())
                   .identifier(downtime.getIdentifier())
                   .downtimeStatusDetails(downtimeIdentifierToInstancesDTOMap.containsKey(downtime.getIdentifier())
                           ? DowntimeStatusDetails
                                 .getDowntimeStatusDetailsInstanceBuilder(
                                     downtimeIdentifierToInstancesDTOMap.get(downtime.getIdentifier()).getStartTime(),
                                     downtimeIdentifierToInstancesDTOMap.get(downtime.getIdentifier()).getEndTime(),
                                     clock)
                                 .endDateTime(DateTimeUtils.getDateStringFromEpoch(
                                     downtimeIdentifierToInstancesDTOMap.get(downtime.getIdentifier()).getEndTime(),
                                     downtime.getTimezone()))
                                 .build()
                           : null)
                   .affectedEntities(downtime.getEntitiesRule().getType().equals(RuleType.IDENTFIERS)
                           ? ((EntityIdentifiersRule) downtime.getEntitiesRule())
                                 .getEntityIdentifiers()
                                 .stream()
                                 .filter(entityDetails
                                     -> identifierAffectedEntityMap.containsKey(entityDetails.getEntityRef()))
                                 .map(entityDetails -> identifierAffectedEntityMap.get(entityDetails.getEntityRef()))
                                 .collect(Collectors.toList())
                           : Collections.singletonList(downtime.getEntitiesRule().getAffectedEntity().get()))
                   .lastModified(
                       DowntimeListView.LastModified.builder()
                           .lastModifiedAt(downtime.getLastUpdatedAt())
                           .lastModifiedBy(
                               downtime.getLastUpdatedBy() != null ? downtime.getLastUpdatedBy().getEmail() : "")
                           .build())
                   .duration(downtimeTransformerMap.get(downtime.getType())
                                 .getDowntimeDuration(downtime.getDowntimeDetails()))
                   .spec(getDowntimeSpecDTO(downtime.getType(), downtime.getDowntimeDetails(), downtime.getTimezone()))
                   .pastOrActiveInstancesCount(
                       downtimeIdentifierToPastAndActiveInstancesCountMap.getOrDefault(downtime.getIdentifier(), 0))
                   .build())
        .collect(Collectors.toList());
  }

  private Downtime updateDowntimeEntity(
      ProjectParams projectParams, DowntimeDTO downtimeDTO, Downtime existingDowntime) {
    UpdateOperations<Downtime> updateOperations = hPersistence.createUpdateOperations(Downtime.class);
    if (downtimeDTO.getTags() != null) {
      updateOperations.set(DowntimeKeys.tags, downtimeDTO.getTags());
    }
    updateOperations.set(DowntimeKeys.entitiesRule, downtimeDTO.getEntitiesRule());
    updateOperations.set(DowntimeKeys.category, downtimeDTO.getCategory());
    if (downtimeDTO.getDescription() != null) {
      updateOperations.set(DowntimeKeys.description, downtimeDTO.getDescription());
    }
    updateOperations.set(DowntimeKeys.enabled, downtimeDTO.isEnabled());
    updateOperations.set(DowntimeKeys.name, downtimeDTO.getName());
    updateOperations.set(DowntimeKeys.downtimeDetails, getDowntimeDetails(downtimeDTO.getSpec()));
    updateOperations.set(DowntimeKeys.type, downtimeDTO.getSpec().getType());
    updateOperations.set(DowntimeKeys.timezone, downtimeDTO.getSpec().getSpec().getTimezone());
    hPersistence.update(existingDowntime, updateOperations);
    return getEntity(projectParams, downtimeDTO.getIdentifier());
  }
  private void validateCreate(ProjectParams projectParams, DowntimeDTO downtimeDTO) {
    Downtime downtime = getEntity(projectParams, downtimeDTO.getIdentifier());
    if (downtime != null) {
      throw new DuplicateFieldException(String.format(
          "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s  is already present.",
          downtimeDTO.getIdentifier(), projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    validateEndTime(downtimeDTO.getSpec());
    validateReferredMonitoredServices(projectParams, downtimeDTO.getEntitiesRule());
  }

  private void validateUpdate(ProjectParams projectParams, String identifier, DowntimeDTO downtimeDTO) {
    Preconditions.checkArgument(identifier.equals(downtimeDTO.getIdentifier()),
        String.format("Identifier %s does not match with path identifier %s", downtimeDTO.getIdentifier(), identifier));
    validateEndTime(downtimeDTO.getSpec());
    validateReferredMonitoredServices(projectParams, downtimeDTO.getEntitiesRule());
  }

  private void validateNotAllowedFieldsChanges(DowntimeDTO existingDowntimeDTO, DowntimeDTO newDowntimeDTO) {
    if (existingDowntimeDTO.getScope() != newDowntimeDTO.getScope()) {
      throw new InvalidRequestException("Scope of Downtime can't be changed");
    }
  }
  private void validateReferredMonitoredServices(ProjectParams projectParams, EntitiesRule entitiesRule) {
    if (entitiesRule.getType().equals(RuleType.IDENTFIERS)) {
      List<EntityDetails> entityDetails = ((EntityIdentifiersRule) entitiesRule).getEntityIdentifiers();
      Set<String> entityRefs = entityDetails.stream().map(EntityDetails::getEntityRef).collect(Collectors.toSet());
      if (entityRefs.size() != entityDetails.size()) {
        throw new InvalidRequestException("Duplicate Monitored services added");
      }
      if (entityRefs.isEmpty()) {
        throw new InvalidRequestException("No Monitored services added");
      }
      List<MonitoredServiceResponse> monitoredServices = monitoredServiceService.get(projectParams, entityRefs);
      if (monitoredServices.size() != entityRefs.size()) {
        List<String> entityRefFromMonitoredServices =
            monitoredServices.stream()
                .map(monitoredServiceResponse -> monitoredServiceResponse.getMonitoredServiceDTO().getIdentifier())
                .collect(Collectors.toList());
        List<String> incorrectEntityRefs = new ArrayList<>();
        for (String entityRef : entityRefs) {
          if (!entityRefFromMonitoredServices.contains(entityRef)) {
            incorrectEntityRefs.add(entityRef);
          }
        }
        throw new InvalidRequestException(
            String.format("Monitored Service%s %s for account %s, org %s, and project %s are not present.",
                incorrectEntityRefs.size() > 1 ? "s" : "", String.join(", ", incorrectEntityRefs),
                projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                projectParams.getProjectIdentifier()));
      }
    }
  }
  private void validateEndTime(DowntimeSpecDTO downtimeSpecDTO) {
    Instant now = clock.instant();
    Instant endInstant = null;
    Duration allowedDuration = Duration.ofDays(MAX_DURATION_IN_DAYS);
    if (downtimeSpecDTO.getType().equals(DowntimeType.RECURRING)) {
      RecurringDowntimeSpec recurringDowntimeSpec = (RecurringDowntimeSpec) downtimeSpecDTO.getSpec();
      Optional<String> recurrenceEndDateTime = Optional.ofNullable(recurringDowntimeSpec.getRecurrenceEndDateTime());
      endInstant = recurrenceEndDateTime.isPresent()
          ? Instant.ofEpochSecond(DateTimeUtils.getEpochValueFromDateString(
              recurrenceEndDateTime.get(), recurringDowntimeSpec.getTimezone()))
          : Instant.ofEpochSecond(recurringDowntimeSpec.getRecurrenceEndTime());
    } else {
      OnetimeDowntimeSpec onetimeDowntimeSpec = (OnetimeDowntimeSpec) downtimeSpecDTO.getSpec();
      if (onetimeDowntimeSpec.getSpec().getType().equals(OnetimeDowntimeType.END_TIME)) {
        OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec onetimeEndTimeBasedSpec =
            (OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec) onetimeDowntimeSpec.getSpec();
        Optional<String> endDateTime = Optional.ofNullable(onetimeEndTimeBasedSpec.getEndDateTime());
        endInstant = endDateTime.isPresent() ? Instant.ofEpochSecond(DateTimeUtils.getEpochValueFromDateString(
                         endDateTime.get(), onetimeDowntimeSpec.getTimezone()))
                                             : Instant.ofEpochSecond(onetimeEndTimeBasedSpec.getEndTime());
      } else if (onetimeDowntimeSpec.getSpec().getType().equals(OnetimeDowntimeType.DURATION)) {
        OnetimeDowntimeSpec.OnetimeDurationBasedSpec onetimeDurationBasedSpec =
            (OnetimeDowntimeSpec.OnetimeDurationBasedSpec) onetimeDowntimeSpec.getSpec();
        Optional<String> startDateTime = Optional.ofNullable(onetimeDowntimeSpec.getStartDateTime());
        endInstant = startDateTime.isPresent()
            ? Instant.ofEpochSecond(downtimeTransformerMap.get(DowntimeType.ONE_TIME)
                                        .getEndTime(DateTimeUtils.getEpochValueFromDateString(
                                                        startDateTime.get(), onetimeDowntimeSpec.getTimezone()),
                                            onetimeDurationBasedSpec.getDowntimeDuration()))
            : Instant.ofEpochSecond(
                downtimeTransformerMap.get(DowntimeType.ONE_TIME)
                    .getEndTime(onetimeDowntimeSpec.getStartTime(), onetimeDurationBasedSpec.getDowntimeDuration()));
      }
    }
    if (endInstant != null && now.plus(allowedDuration).isBefore(endInstant)) {
      throw new InvalidArgumentsException("EndTime can't be more than 3 years from now.");
    }
  }

  private MSDropdownResponse getMSDropdownResponse(MonitoredServiceDetail monitoredServiceDetail) {
    return MSDropdownResponse.builder()
        .identifier(monitoredServiceDetail.getMonitoredServiceIdentifier())
        .name(monitoredServiceDetail.getMonitoredServiceName())
        .serviceRef(monitoredServiceDetail.getServiceIdentifier())
        .environmentRef(monitoredServiceDetail.getEnvironmentIdentifier())
        .build();
  }

  private void addToMonitoredServiceIdentifierToUnavailabilityStatusesDTOMap(String msIdentifier,
      Map<String, EntityUnavailabilityStatusesDTO> monitoredServiceIdentifierToUnavailabilityStatusesDTOMap,
      EntityUnavailabilityStatusesDTO instance) {
    if (monitoredServiceIdentifierToUnavailabilityStatusesDTOMap.containsKey(msIdentifier)) {
      EntityUnavailabilityStatusesDTO status =
          monitoredServiceIdentifierToUnavailabilityStatusesDTOMap.get(msIdentifier);
      if (status.getStartTime() > instance.getStartTime()) {
        monitoredServiceIdentifierToUnavailabilityStatusesDTOMap.put(msIdentifier, instance);
      }
    } else {
      monitoredServiceIdentifierToUnavailabilityStatusesDTOMap.put(msIdentifier, instance);
    }
  }

  @Value
  @Builder
  private static class Filter {
    String monitoredServiceIdentifier;
    String searchFilter;
  }
}
