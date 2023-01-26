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
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.beans.AffectedEntity;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeDashboardFilter;
import io.harness.cvng.downtime.beans.DowntimeHistoryView;
import io.harness.cvng.downtime.beans.DowntimeListView;
import io.harness.cvng.downtime.beans.DowntimeResponse;
import io.harness.cvng.downtime.beans.DowntimeSpecDTO;
import io.harness.cvng.downtime.beans.DowntimeStatus;
import io.harness.cvng.downtime.beans.DowntimeType;
import io.harness.cvng.downtime.beans.EntityDetails;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeType;
import io.harness.cvng.downtime.beans.RecurringDowntimeSpec;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.entities.Downtime.DowntimeDetails;
import io.harness.cvng.downtime.entities.Downtime.DowntimeKeys;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.downtime.transformer.DowntimeSpecDetailsTransformer;
import io.harness.cvng.downtime.utils.DowntimeUtils;
import io.harness.cvng.events.downtime.DowntimeCreateEvent;
import io.harness.cvng.events.downtime.DowntimeDeleteEvent;
import io.harness.cvng.events.downtime.DowntimeUpdateEvent;
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
  public DowntimeResponse update(ProjectParams projectParams, String identifier, DowntimeDTO downtimeDTO) {
    validateUpdate(projectParams, identifier, downtimeDTO);
    Optional<Downtime> downtimeOptional = getOptionalDowntime(projectParams, identifier);
    if (downtimeOptional.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Downtime with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    DowntimeDTO existingDowntimeDTO = getDowntimeDTOFromDowntime(downtimeOptional.get());
    validateNotAllowedFieldsChanges(existingDowntimeDTO, downtimeDTO);
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
    if ((!updatedDowntimeDTO.getSpec().equals(existingDowntimeDTO.getSpec()) || !existingDowntimeDTO.isEnabled())
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
    entityUnavailabilityStatusesService.deleteFutureDowntimeInstances(projectParams, identifier);
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
    List<Downtime> downtimeList = get(projectParams, downtimeIdentifiers);
    if (!isEmpty(filter.getSearchFilter())) {
      downtimeList = filterDowntimes(downtimeList, filter.getSearchFilter());
    }
    if (filter.getMonitoredServiceIdentifier() != null) {
      downtimeList = filterDowntimesOnMonitoredService(downtimeList, filter.getMonitoredServiceIdentifier());
    }

    Map<String, Downtime> identifierToDowntimeMap =
        downtimeList.stream().collect(Collectors.toMap(Downtime::getIdentifier, downtime -> downtime));

    Set<String> monitoredServicesIdentifiers = downtimeList.stream()
                                                   .map(downtime -> downtime.getEntityRefs())
                                                   .flatMap(List::stream)
                                                   .map(EntityDetails::getEntityRef)
                                                   .collect(Collectors.toSet());
    List<MonitoredServiceResponse> monitoredServiceResponses =
        monitoredServiceService.get(projectParams, monitoredServicesIdentifiers);
    Map<String, AffectedEntity> identifierAffectedEntityMap =
        monitoredServiceResponses.stream().collect(Collectors.toMap(monitoredServiceResponse
            -> monitoredServiceResponse.getMonitoredServiceDTO().getIdentifier(),
            monitoredServiceResponse
            -> AffectedEntity.builder()
                   .envRef(monitoredServiceResponse.getMonitoredServiceDTO().getEnvironmentRef())
                   .serviceRef(monitoredServiceResponse.getMonitoredServiceDTO().getServiceRef())
                   .build()));

    List<DowntimeHistoryView> downtimeHistoryViews = getDowntimeHistoryViewFromPastInstances(
        pastDowntimeInstances, identifierToDowntimeMap, identifierAffectedEntityMap);

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
                   .endTime(instance.getEndTime())
                   .duration(
                       DowntimeUtils.getDowntimeDurationFromSeconds(instance.getEndTime() - instance.getStartTime()))
                   .affectedEntities(
                       identifierToDowntimeMap.get(instance.getEntityId())
                           .getEntityRefs()
                           .stream()
                           .map(entityDetails
                               -> monitoredServiceIdentifierAffectedEntityMap.get(entityDetails.getEntityRef()))
                           .collect(Collectors.toList()))
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
    Set<String> monitoredServicesIdentifiers = downtimes.stream()
                                                   .map(downtime -> downtime.getEntityRefs())
                                                   .flatMap(List::stream)
                                                   .map(EntityDetails::getEntityRef)
                                                   .collect(Collectors.toSet());
    List<MonitoredServiceResponse> monitoredServiceResponses =
        monitoredServiceService.get(projectParams, monitoredServicesIdentifiers);
    Map<String, AffectedEntity> identifierAffectedEntityMap =
        monitoredServiceResponses.stream().collect(Collectors.toMap(monitoredServiceResponse
            -> monitoredServiceResponse.getMonitoredServiceDTO().getIdentifier(),
            monitoredServiceResponse
            -> AffectedEntity.builder()
                   .envRef(monitoredServiceResponse.getMonitoredServiceDTO().getEnvironmentRef())
                   .serviceRef(monitoredServiceResponse.getMonitoredServiceDTO().getServiceRef())
                   .build()));
    List<DowntimeListView> downtimeListViews =
        getDowntimeListViewFromDowntime(projectParams, downtimes, identifierAffectedEntityMap);
    return PageUtils.offsetAndLimit(downtimeListViews, offset, pageSize);
  }

  private List<Downtime> get(ProjectParams projectParams) {
    Query<Downtime> sloQuery = hPersistence.createQuery(Downtime.class)
                                   .disableValidation()
                                   .filter(DowntimeKeys.accountId, projectParams.getAccountIdentifier())
                                   .filter(DowntimeKeys.orgIdentifier, projectParams.getOrgIdentifier())
                                   .filter(DowntimeKeys.projectIdentifier, projectParams.getProjectIdentifier())
                                   .order(Sort.descending(DowntimeKeys.lastUpdatedAt));
    return sloQuery.asList();
  }

  private List<Downtime> get(ProjectParams projectParams, Set<String> downtimeIdentifiers) {
    Query<Downtime> sloQuery = hPersistence.createQuery(Downtime.class)
                                   .disableValidation()
                                   .filter(DowntimeKeys.accountId, projectParams.getAccountIdentifier())
                                   .filter(DowntimeKeys.orgIdentifier, projectParams.getOrgIdentifier())
                                   .filter(DowntimeKeys.projectIdentifier, projectParams.getProjectIdentifier())
                                   .field(DowntimeKeys.identifier)
                                   .in(downtimeIdentifiers)
                                   .order(Sort.descending(DowntimeKeys.lastUpdatedAt));
    return sloQuery.asList();
  }
  private List<Downtime> filterDowntimesOnMonitoredService(
      List<Downtime> downtimes, String monitoredServiceIdentifier) {
    return downtimes.stream()
        .filter(downtime
            -> downtime.getEntityRefs()
                   .stream()
                   .map(EntityDetails::getEntityRef)
                   .collect(Collectors.toList())
                   .contains(monitoredServiceIdentifier))
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
  private DowntimeSpecDTO getDowntimeSpecDTO(DowntimeType downtimeType, DowntimeDetails downtimeDetails) {
    return DowntimeSpecDTO.builder()
        .type(downtimeType)
        .spec(downtimeTransformerMap.get(downtimeType).getDowntimeSpec(downtimeDetails))
        .build();
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
        .entityRefs(downtime.getEntityRefs())
        .spec(getDowntimeSpecDTO(downtime.getType(), downtime.getDowntimeDetails()))
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
        .entityRefs(downtimeDTO.getEntityRefs())
        .downtimeDetails(getDowntimeDetails(downtimeDTO.getSpec()))
        .build();
  }

  private List<DowntimeListView> getDowntimeListViewFromDowntime(
      ProjectParams projectParams, List<Downtime> downtimes, Map<String, AffectedEntity> identifierAffectedEntityMap) {
    List<String> downtimeIdentifiers = downtimes.stream().map(Downtime::getIdentifier).collect(Collectors.toList());
    Set<String> activeDowntimes =
        entityUnavailabilityStatusesService.getActiveInstances(projectParams, downtimeIdentifiers);
    return downtimes.stream()
        .map(downtime
            -> DowntimeListView.builder()
                   .name(downtime.getName())
                   .category(downtime.getCategory())
                   .description(downtime.getDescription())
                   .enabled(downtime.isEnabled())
                   .identifier(downtime.getIdentifier())
                   .status(activeDowntimes.contains(downtime.getIdentifier()) ? DowntimeStatus.ACTIVE
                                                                              : DowntimeStatus.SCHEDULED)
                   .affectedEntities(
                       downtime.getEntityRefs()
                           .stream()
                           .map(entityDetails -> identifierAffectedEntityMap.get(entityDetails.getEntityRef()))
                           .collect(Collectors.toList()))
                   .lastModified(
                       DowntimeListView.LastModified.builder()
                           .lastModifiedAt(downtime.getLastUpdatedAt())
                           .lastModifiedBy(
                               downtime.getLastUpdatedBy() != null ? downtime.getLastUpdatedBy().getName() : "")
                           .build())
                   .duration(downtimeTransformerMap.get(downtime.getType())
                                 .getDowntimeDuration(downtime.getDowntimeDetails()))
                   .spec(DowntimeSpecDTO.builder()
                             .spec(downtimeTransformerMap.get(downtime.getType())
                                       .getDowntimeSpec(downtime.getDowntimeDetails()))
                             .type(downtime.getType())
                             .build())
                   .build())
        .collect(Collectors.toList());
  }

  private Downtime updateDowntimeEntity(
      ProjectParams projectParams, DowntimeDTO downtimeDTO, Downtime existingDowntime) {
    UpdateOperations<Downtime> updateOperations = hPersistence.createUpdateOperations(Downtime.class);
    if (downtimeDTO.getTags() != null) {
      updateOperations.set(DowntimeKeys.tags, downtimeDTO.getTags());
    }
    updateOperations.set(DowntimeKeys.entityRefs, downtimeDTO.getEntityRefs());
    updateOperations.set(DowntimeKeys.category, downtimeDTO.getCategory());
    updateOperations.set(DowntimeKeys.description, downtimeDTO.getDescription());
    updateOperations.set(DowntimeKeys.enabled, downtimeDTO.isEnabled());
    updateOperations.set(DowntimeKeys.name, downtimeDTO.getName());
    updateOperations.set(DowntimeKeys.downtimeDetails, getDowntimeDetails(downtimeDTO.getSpec()));
    updateOperations.set(DowntimeKeys.type, downtimeDTO.getSpec().getType());
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
    validateReferredMonitoredServices(projectParams, downtimeDTO.getEntityRefs());
  }

  private void validateUpdate(ProjectParams projectParams, String identifier, DowntimeDTO downtimeDTO) {
    Preconditions.checkArgument(identifier.equals(downtimeDTO.getIdentifier()),
        String.format("Identifier %s does not match with path identifier %s", downtimeDTO.getIdentifier(), identifier));
    validateEndTime(downtimeDTO.getSpec());
    validateReferredMonitoredServices(projectParams, downtimeDTO.getEntityRefs());
  }

  private void validateNotAllowedFieldsChanges(DowntimeDTO existingDowntimeDTO, DowntimeDTO newDowntimeDTO) {
    if (existingDowntimeDTO.getScope() != newDowntimeDTO.getScope()) {
      throw new InvalidRequestException("Scope of Downtime can't be changed");
    }
  }
  private void validateReferredMonitoredServices(ProjectParams projectParams, List<EntityDetails> entityDetails) {
    Set<String> entityRefs = entityDetails.stream().map(EntityDetails::getEntityRef).collect(Collectors.toSet());
    if (entityRefs.size() != entityDetails.size()) {
      throw new InvalidRequestException("Duplicate Monitored services added");
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
  private void validateEndTime(DowntimeSpecDTO downtimeSpecDTO) {
    Instant now = clock.instant();
    Instant endInstant = null;
    Duration allowedDuration = Duration.ofDays(MAX_DURATION_IN_DAYS);
    if (downtimeSpecDTO.getType().equals(DowntimeType.RECURRING)) {
      RecurringDowntimeSpec recurringDowntimeSpec = (RecurringDowntimeSpec) downtimeSpecDTO.getSpec();
      endInstant = Instant.ofEpochSecond(recurringDowntimeSpec.getRecurrenceEndTime());
    } else {
      OnetimeDowntimeSpec onetimeDowntimeSpec = (OnetimeDowntimeSpec) downtimeSpecDTO.getSpec();
      if (onetimeDowntimeSpec.getSpec().getType().equals(OnetimeDowntimeType.END_TIME)) {
        OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec onetimeEndTimeBasedSpec =
            (OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec) onetimeDowntimeSpec.getSpec();
        endInstant = Instant.ofEpochSecond(onetimeEndTimeBasedSpec.getEndTime());
      }
    }
    if (endInstant != null && now.plus(allowedDuration).isBefore(endInstant)) {
      throw new InvalidArgumentsException("EndTime can't be more than 3 years from now.");
    }
  }

  @Value
  @Builder
  private static class Filter {
    String monitoredServiceIdentifier;
    String searchFilter;
  }
}
