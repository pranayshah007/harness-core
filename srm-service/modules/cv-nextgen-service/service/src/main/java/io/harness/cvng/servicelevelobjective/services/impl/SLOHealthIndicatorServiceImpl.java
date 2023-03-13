/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator.SLOHealthIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class SLOHealthIndicatorServiceImpl implements SLOHealthIndicatorService {
  @Inject private HPersistence hPersistence;
  @Inject private GraphDataService graphDataService;
  @Inject private Clock clock;
  @Inject private SLOErrorBudgetResetService sloErrorBudgetResetService;

  @Override
  public List<SLOHealthIndicator> getByMonitoredServiceIdentifiers(
      ProjectParams projectParams, List<String> monitoredServiceIdentifiers) {
    return hPersistence.createQuery(SLOHealthIndicator.class)
        .filter(SLOHealthIndicatorKeys.accountId, projectParams.getAccountIdentifier())
        .filter(SLOHealthIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(SLOHealthIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(SLOHealthIndicatorKeys.monitoredServiceIdentifier)
        .in(monitoredServiceIdentifiers)
        .asList();
  }

  @Override
  public SLOHealthIndicator getBySLOIdentifier(ProjectParams projectParams, String serviceLevelObjectiveIdentifier) {
    return hPersistence.createQuery(SLOHealthIndicator.class)
        .filter(SLOHealthIndicatorKeys.accountId, projectParams.getAccountIdentifier())
        .filter(SLOHealthIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(SLOHealthIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(SLOHealthIndicatorKeys.serviceLevelObjectiveIdentifier, serviceLevelObjectiveIdentifier)
        .get();
  }

  @Override
  public SLOHealthIndicator getBySLOEntity(AbstractServiceLevelObjective serviceLevelObjective) {
    return hPersistence.createQuery(SLOHealthIndicator.class)
        .filter(SLOHealthIndicatorKeys.accountId, serviceLevelObjective.getAccountId())
        .filter(SLOHealthIndicatorKeys.orgIdentifier, serviceLevelObjective.getOrgIdentifier())
        .filter(SLOHealthIndicatorKeys.projectIdentifier, serviceLevelObjective.getProjectIdentifier())
        .filter(SLOHealthIndicatorKeys.serviceLevelObjectiveIdentifier, serviceLevelObjective.getIdentifier())
        .get();
  }

  @Override
  public List<SLOHealthIndicator> getBySLOIdentifiers(
      ProjectParams projectParams, List<String> serviceLevelObjectiveIdentifiers) {
    return hPersistence.createQuery(SLOHealthIndicator.class)
        .filter(SLOHealthIndicatorKeys.accountId, projectParams.getAccountIdentifier())
        .filter(SLOHealthIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(SLOHealthIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(SLOHealthIndicatorKeys.serviceLevelObjectiveIdentifier)
        .in(serviceLevelObjectiveIdentifiers)
        .asList();
  }

  @Override
  public List<SLOHealthIndicator> getBySLOIdentifiers(String accountId, List<String> serviceLevelObjectiveIdentifiers) {
    return hPersistence.createQuery(SLOHealthIndicator.class)
        .filter(SLOHealthIndicatorKeys.accountId, accountId)
        .field(SLOHealthIndicatorKeys.serviceLevelObjectiveIdentifier)
        .in(serviceLevelObjectiveIdentifiers)
        .asList();
  }

  @Override
  public void delete(ProjectParams projectParams, String serviceLevelObjectiveIdentifier) {
    hPersistence.delete(
        hPersistence.createQuery(SLOHealthIndicator.class)
            .filter(SLOHealthIndicatorKeys.accountId, projectParams.getAccountIdentifier())
            .filter(SLOHealthIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(SLOHealthIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(SLOHealthIndicatorKeys.serviceLevelObjectiveIdentifier, serviceLevelObjectiveIdentifier));
  }

  @Override
  public void upsert(AbstractServiceLevelObjective serviceLevelObjective) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(serviceLevelObjective.getAccountId())
                                      .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                      .build();
    upsert(projectParams, serviceLevelObjective);
  }

  private void upsert(ProjectParams projectParams, AbstractServiceLevelObjective serviceLevelObjective) {
    SLOHealthIndicator sloHealthIndicator = getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());
    SLOGraphData sloGraphData = getGraphData(projectParams, serviceLevelObjective);
    String monitoredServiceIdentifier = "";
    if (serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      monitoredServiceIdentifier = serviceLevelObjective.mayBeGetMonitoredServiceIdentifier().get();
    }
    if (Objects.isNull(sloHealthIndicator)) {
      SLOHealthIndicator newSloHealthIndicator =
          SLOHealthIndicator.builder()
              .accountId(serviceLevelObjective.getAccountId())
              .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
              .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
              .serviceLevelObjectiveIdentifier(serviceLevelObjective.getIdentifier())
              .monitoredServiceIdentifier(monitoredServiceIdentifier)
              .errorBudgetRemainingPercentage(sloGraphData.getErrorBudgetRemainingPercentage())
              .errorBudgetRemainingMinutes(sloGraphData.getErrorBudgetRemaining())
              .build();
      hPersistence.save(newSloHealthIndicator);
    } else {
      UpdateOperations<SLOHealthIndicator> updateOperations =
          hPersistence.createUpdateOperations(SLOHealthIndicator.class);
      updateOperations.set(SLOHealthIndicatorKeys.monitoredServiceIdentifier, monitoredServiceIdentifier);
      updateOperations.set(
          SLOHealthIndicatorKeys.errorBudgetRemainingPercentage, sloGraphData.getErrorBudgetRemainingPercentage());
      updateOperations.set(SLOHealthIndicatorKeys.errorBudgetRisk,
          ErrorBudgetRisk.getFromPercentage(sloGraphData.getErrorBudgetRemainingPercentage()));
      updateOperations.set(SLOHealthIndicatorKeys.errorBudgetRemainingMinutes, sloGraphData.getErrorBudgetRemaining());
      updateOperations.set(SLOHealthIndicatorKeys.errorBudgetBurnRate,
          sloGraphData.dailyBurnRate(serviceLevelObjective.getZoneOffset()));
      updateOperations.set(SLOHealthIndicatorKeys.lastComputedAt, Instant.now());
      hPersistence.update(sloHealthIndicator, updateOperations);
    }
  }

  @Override
  public SLOGraphData getGraphData(ProjectParams projectParams, AbstractServiceLevelObjective serviceLevelObjective) {
    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
    List<SLOErrorBudgetResetDTO> errorBudgetResetDTOS =
        sloErrorBudgetResetService.getErrorBudgetResets(projectParams, serviceLevelObjective.getIdentifier());
    int totalErrorBudgetMinutes =
        serviceLevelObjective.getActiveErrorBudgetMinutes(errorBudgetResetDTOS, currentLocalDate);
    TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
    Instant currentTimeMinute = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    return graphDataService.getGraphData(serviceLevelObjective,
        timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()), currentTimeMinute, totalErrorBudgetMinutes);
  }
}
