/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset.SLOErrorBudgetResetKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SLOErrorBudgetResetServiceImpl implements SLOErrorBudgetResetService {
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject private Clock clock;

  @Override
  public SLOErrorBudgetResetDTO resetErrorBudget(
      ProjectParams projectParams, SLOErrorBudgetResetDTO sloErrorBudgetResetDTO) {
    ServiceLevelObjective serviceLevelObjective = serviceLevelObjectiveService.getEntity(
        projectParams, sloErrorBudgetResetDTO.getServiceLevelObjectiveIdentifier());
    Preconditions.checkNotNull(serviceLevelObjective, "SLO with identifier:%s not found",
        sloErrorBudgetResetDTO.getServiceLevelObjectiveIdentifier());
    SLOErrorBudgetReset sloErrorBudgetReset = entityFromDTO(projectParams, sloErrorBudgetResetDTO,
        serviceLevelObjective
            .getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset()))
            .getEndTime()
            .toInstant(ZoneOffset.UTC));
    hPersistence.save(sloErrorBudgetReset);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    return dtoFromEntity(sloErrorBudgetReset);
  }

  @Override
  public List<SLOErrorBudgetResetDTO> getErrorBudgetResets(ProjectParams projectParams, String sloIdentifier) {
    return hPersistence.createQuery(SLOErrorBudgetReset.class)
        .filter(SLOErrorBudgetResetKeys.accountId, projectParams.getAccountIdentifier())
        .filter(SLOErrorBudgetResetKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(SLOErrorBudgetResetKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(SLOErrorBudgetResetKeys.serviceLevelObjectiveIdentifier, sloIdentifier)
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
  public void clearErrorBudgetResets(ProjectParams projectParams, String sloIdentifier) {
    hPersistence.delete(hPersistence.createQuery(SLOErrorBudgetReset.class)
                            .filter(SLOErrorBudgetResetKeys.accountId, projectParams.getAccountIdentifier())
                            .filter(SLOErrorBudgetResetKeys.orgIdentifier, projectParams.getOrgIdentifier())
                            .filter(SLOErrorBudgetResetKeys.projectIdentifier, projectParams.getProjectIdentifier())
                            .filter(SLOErrorBudgetResetKeys.serviceLevelObjectiveIdentifier, sloIdentifier));
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
}
