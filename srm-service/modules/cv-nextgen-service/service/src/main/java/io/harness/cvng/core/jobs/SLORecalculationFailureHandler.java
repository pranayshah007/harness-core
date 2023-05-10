/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.beans.SLOMetricContext;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.impl.SLOHealthIndicatorServiceImpl;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import lombok.SneakyThrows;

public class SLORecalculationFailureHandler implements MongoPersistenceIterator.Handler<AbstractServiceLevelObjective> {
  @Inject private SLOHealthIndicatorServiceImpl sloHealthIndicatorService;
  @Inject MetricService metricService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  Long recalculationDelayThreshold = 30 * 60 * 1000L;
  @SneakyThrows
  @Override
  public void handle(AbstractServiceLevelObjective serviceLevelObjective) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(serviceLevelObjective.getAccountId())
                                      .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                      .build();
    SLODashboardWidget.SLOGraphData sloGraphData =
        sloHealthIndicatorService.getGraphData(projectParams, serviceLevelObjective, null);
    if (serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.COMPOSITE)) {
      try (SLOMetricContext sloMetricContext =
               new SLOMetricContext((CompositeServiceLevelObjective) serviceLevelObjective)) {
        addMetrics(sloGraphData, serviceLevelObjective);
      }
    } else {
      ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
          projectParams, ((SimpleServiceLevelObjective) serviceLevelObjective).getServiceLevelIndicators().get(0));
      try (SLOMetricContext sloMetricContext = new SLOMetricContext(serviceLevelIndicator)) {
        addMetrics(sloGraphData, serviceLevelObjective);
      }
    }
  }
  private void addMetrics(
      SLODashboardWidget.SLOGraphData sloGraphData, AbstractServiceLevelObjective serviceLevelObjective) {
    if (sloGraphData.isRecalculatingSLI()
        && serviceLevelObjective.getLastUpdatedAt() > System.currentTimeMillis() - recalculationDelayThreshold) {
      metricService.incCounter(CVNGMetricsUtils.RECALCULATION_FAILURE);
    }
    if (sloGraphData.isCalculatingSLI()) {
      metricService.incCounter(CVNGMetricsUtils.CALCULATION_FAILURE);
    }
  }
}
