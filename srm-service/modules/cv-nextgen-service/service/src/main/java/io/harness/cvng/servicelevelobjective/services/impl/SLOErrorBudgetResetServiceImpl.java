/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveErrorBudgetResetEvent;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetInstanceDetails;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventDetailsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset.SLOErrorBudgetResetKeys;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.SecondaryEventDetailsService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SLOErrorBudgetResetServiceImpl implements SLOErrorBudgetResetService, SecondaryEventDetailsService {
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject private Clock clock;
  @Inject private OutboxService outboxService;

  @Override
  public SLOErrorBudgetResetDTO resetErrorBudget(
      ProjectParams projectParams, SLOErrorBudgetResetDTO sloErrorBudgetResetDTO) {
    AbstractServiceLevelObjective serviceLevelObjective = serviceLevelObjectiveV2Service.getEntity(
        projectParams, sloErrorBudgetResetDTO.getServiceLevelObjectiveIdentifier());
    Preconditions.checkNotNull(serviceLevelObjective, "SLO with identifier:%s not found",
        sloErrorBudgetResetDTO.getServiceLevelObjectiveIdentifier());
    Preconditions.checkArgument(serviceLevelObjective.getSliEvaluationType() == SLIEvaluationType.WINDOW,
        "ServiceLevelObjective Should be of type Window.");
    SLOErrorBudgetReset sloErrorBudgetReset = entityFromDTO(projectParams, sloErrorBudgetResetDTO,
        serviceLevelObjective
            .getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset()))
            .getEndTime()
            .toInstant(ZoneOffset.UTC));
    hPersistence.save(sloErrorBudgetReset);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    outboxService.save(ServiceLevelObjectiveErrorBudgetResetEvent.builder()
                           .resourceName(serviceLevelObjective.getName())
                           .accountIdentifier(projectParams.getAccountIdentifier())
                           .serviceLevelObjectiveIdentifier(serviceLevelObjective.getIdentifier())
                           .orgIdentifier(projectParams.getOrgIdentifier())
                           .projectIdentifier(projectParams.getProjectIdentifier())
                           .build());
    return dtoFromEntity(sloErrorBudgetReset);
  }

  @Override
  public SLOErrorBudgetReset getErrorBudgetResetByUuid(String uuid) {
    return hPersistence.get(SLOErrorBudgetReset.class, uuid);
  }

  @Override
  public List<SLOErrorBudgetReset> getErrorBudgetResetEntities(
      ProjectParams projectParams, String sloIdentifier, long startTime, long endTime) {
    return getErrorBudgetResetQuery(projectParams, sloIdentifier)
        .field(SLOErrorBudgetResetKeys.createdAt)
        .greaterThanOrEq(startTime)
        .field(SLOErrorBudgetResetKeys.createdAt)
        .lessThanOrEq(endTime)
        .asList();
  }

  @Override
  public List<SLOErrorBudgetResetDTO> getErrorBudgetResets(ProjectParams projectParams, String sloIdentifier) {
    return getErrorBudgetResetQuery(projectParams, sloIdentifier)
        .asList()
        .stream()
        .map(this::dtoFromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, List<SLOErrorBudgetResetDTO>> getErrorBudgetResets(
      ProjectParams projectParams, Set<String> sloIdentifiers) {
    return hPersistence.createQuery(SLOErrorBudgetReset.class)
        .filter(SLOErrorBudgetResetKeys.accountId, projectParams.getAccountIdentifier())
        .filter(SLOErrorBudgetResetKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(SLOErrorBudgetResetKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(SLOErrorBudgetResetKeys.serviceLevelObjectiveIdentifier)
        .in(sloIdentifiers)
        .asList()
        .stream()
        .map(this::dtoFromEntity)
        .collect(Collectors.groupingBy(dto -> dto.getServiceLevelObjectiveIdentifier()));
  }

  @Override
  public SecondaryEventDetailsResponse getInstanceByUuids(List<String> uuids, SecondaryEventsType eventType) {
    SLOErrorBudgetReset sloErrorBudgetReset = getErrorBudgetResetByUuid(uuids.get(0));
    return SecondaryEventDetailsResponse.builder()
        .type(SecondaryEventsType.ERROR_BUDGET_RESET)
        .startTime(TimeUnit.MILLISECONDS.toSeconds(sloErrorBudgetReset.getCreatedAt()))
        .details(SLOErrorBudgetResetInstanceDetails.builder()
                     .errorBudgetIncrementMinutes(sloErrorBudgetReset.getErrorBudgetIncrementMinutes())
                     .build())
        .build();
  }

  @Override
  public void clearErrorBudgetResets(ProjectParams projectParams, String sloIdentifier) {
    hPersistence.delete(getErrorBudgetResetQuery(projectParams, sloIdentifier));
  }

  private SLOErrorBudgetResetDTO dtoFromEntity(SLOErrorBudgetReset sloErrorBudgetReset) {
    return SLOErrorBudgetResetDTO.builder()
        .serviceLevelObjectiveIdentifier(sloErrorBudgetReset.getServiceLevelObjectiveIdentifier())
        .errorBudgetIncrementPercentage(sloErrorBudgetReset.getErrorBudgetIncrementPercentage())
        .errorBudgetIncrementMinutes(sloErrorBudgetReset.getErrorBudgetIncrementMinutes())
        .remainingErrorBudgetAtReset(sloErrorBudgetReset.getRemainingErrorBudgetAtReset())
        .errorBudgetAtReset(sloErrorBudgetReset.getErrorBudgetAtReset())
        .reason(sloErrorBudgetReset.getReason())
        .createdAt(sloErrorBudgetReset.getCreatedAt())
        .validUntil(sloErrorBudgetReset.getValidUntil().getTime())
        .build();
  }

  private SLOErrorBudgetReset entityFromDTO(
      ProjectParams projectParams, SLOErrorBudgetResetDTO sloErrorBudgetResetDTO, Instant validTill) {
    return SLOErrorBudgetReset.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .serviceLevelObjectiveIdentifier(sloErrorBudgetResetDTO.getServiceLevelObjectiveIdentifier())
        .errorBudgetIncrementPercentage(sloErrorBudgetResetDTO.getErrorBudgetIncrementPercentage())
        .errorBudgetIncrementMinutes(sloErrorBudgetResetDTO.getErrorBudgetIncrementMinutes())
        .remainingErrorBudgetAtReset(sloErrorBudgetResetDTO.getRemainingErrorBudgetAtReset())
        .errorBudgetAtReset(sloErrorBudgetResetDTO.getErrorBudgetAtReset())
        .reason(sloErrorBudgetResetDTO.getReason())
        .validUntil(Date.from(validTill))
        .build();
  }

  private Query<SLOErrorBudgetReset> getErrorBudgetResetQuery(ProjectParams projectParams, String sloIdentifier) {
    return hPersistence.createQuery(SLOErrorBudgetReset.class)
        .filter(SLOErrorBudgetResetKeys.accountId, projectParams.getAccountIdentifier())
        .filter(SLOErrorBudgetResetKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(SLOErrorBudgetResetKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(SLOErrorBudgetResetKeys.serviceLevelObjectiveIdentifier, sloIdentifier);
  }

  @VisibleForTesting
  List<SLOErrorBudgetReset> getSLOErrorBudgetResetEntities(ProjectParams projectParams, String sloIdentifier) {
    return getErrorBudgetResetQuery(projectParams, sloIdentifier).asList();
  }
}
