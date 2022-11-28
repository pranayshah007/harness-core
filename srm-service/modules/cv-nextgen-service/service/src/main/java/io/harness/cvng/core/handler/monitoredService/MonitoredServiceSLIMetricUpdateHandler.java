/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.handler.monitoredService;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricHealthSourceSpec;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class MonitoredServiceSLIMetricUpdateHandler extends BaseMonitoredServiceHandler {
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Override
  public void beforeUpdate(
      ProjectParams projectParams, MonitoredServiceDTO existingObject, MonitoredServiceDTO updatingObject) {
    if (existingObject.getSources() == null) {
      return;
    }
    Map<String, HealthSource> newHealthSourceMap = getHealthSourceMap(updatingObject);
    List<String> deletingMetricsSLIs =
        existingObject.getSources()
            .getHealthSources()
            .stream()
            .flatMap(healthSource -> {
              if (!newHealthSourceMap.containsKey(healthSource.getIdentifier())) {
                return serviceLevelIndicatorService
                    .getSLIsWithMetrics(projectParams, existingObject.getIdentifier(), healthSource.getIdentifier(),
                        getMetricIdentifiers(healthSource))
                    .stream();
              } else {
                List<String> existingMetric = getMetricIdentifiers(healthSource);
                List<String> newMetrics = getMetricIdentifiers(newHealthSourceMap.get(healthSource.getIdentifier()));
                existingMetric.removeAll(newMetrics);
                List<String> deletedMetrics = existingMetric;
                return serviceLevelIndicatorService
                    .getSLIsWithMetrics(
                        projectParams, existingObject.getIdentifier(), healthSource.getIdentifier(), deletedMetrics)
                    .stream();
              }
            })
            .collect(Collectors.toList());
    Preconditions.checkArgument(CollectionUtils.isEmpty(deletingMetricsSLIs),
        "Deleting metrics are used in SLIs, "
            + "Please delete the SLIs before deleting metrics. SLIs : " + String.join(", ", deletingMetricsSLIs));
  }

  @Override
  public void beforeDelete(ProjectParams projectParams, MonitoredServiceDTO deletingObject) {
    if (deletingObject.getSources() == null) {
      return;
    }
    List<String> deletingMetricsSLIs =
        deletingObject.getSources()
            .getHealthSources()
            .stream()
            .flatMap(healthSource
                -> serviceLevelIndicatorService
                       .getSLIsWithMetrics(projectParams, deletingObject.getIdentifier(), healthSource.getIdentifier(),
                           getMetricIdentifiers(healthSource))
                       .stream())
            .collect(Collectors.toList());

    Preconditions.checkArgument(CollectionUtils.isEmpty(deletingMetricsSLIs),
        "Deleting metrics are used in SLIs, "
            + "Please delete the SLIs before deleting metrics. SLIs : " + String.join(", ", deletingMetricsSLIs));
  }

  private Map<String, HealthSource> getHealthSourceMap(MonitoredServiceDTO monitoredServiceDTO) {
    if (monitoredServiceDTO.getSources() == null
        || CollectionUtils.isEmpty(monitoredServiceDTO.getSources().getHealthSources())) {
      Collections.emptyMap();
    }
    return monitoredServiceDTO.getSources().getHealthSources().stream().collect(
        Collectors.toMap(healthSource -> healthSource.getIdentifier(), healthSource -> healthSource));
  }

  private List<String> getMetricIdentifiers(HealthSource healthSource) {
    if (healthSource.getSpec() instanceof MetricHealthSourceSpec) {
      return ((MetricHealthSourceSpec) healthSource.getSpec())
          .getMetricDefinitions()
          .stream()
          .map(HealthSourceMetricDefinition::getIdentifier)
          .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }
}
