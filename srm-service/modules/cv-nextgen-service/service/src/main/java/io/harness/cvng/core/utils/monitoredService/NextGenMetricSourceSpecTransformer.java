/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.healthsource.HealthSourceParamsDTO;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.monitoredService.MetricThreshold;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.constant.MonitoredServiceConstants;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;
import io.harness.data.structure.EmptyPredicate;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class NextGenMetricSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<NextGenMetricCVConfig, NextGenHealthSourceSpec> {
  @Override
  public NextGenHealthSourceSpec transformToHealthSourceConfig(List<NextGenMetricCVConfig> cvConfigs) {
    Preconditions.checkNotNull(cvConfigs);
    Preconditions.checkArgument(
        cvConfigs.stream().map(NextGenMetricCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    List<QueryDefinition> queryDefinitions = new ArrayList<>();

    cvConfigs.forEach((NextGenMetricCVConfig nextGenMetricCVConfig)
                          -> nextGenMetricCVConfig.getMetricInfos().forEach((NextGenMetricInfo metricInfo) -> {
      RiskProfile riskProfile = RiskProfile.builder()
                                    .category(nextGenMetricCVConfig.getMetricPack().getCategory())
                                    .metricType(metricInfo.getMetricType())
                                    .thresholdTypes(nextGenMetricCVConfig.getThresholdTypeOfMetric(
                                        metricInfo.getMetricName(), nextGenMetricCVConfig))
                                    .build();
      QueryDefinition queryDefinition =
          QueryDefinition.builder()
              .groupName(nextGenMetricCVConfig.getGroupName())
              .query(metricInfo.getQuery())
              .identifier(metricInfo.getIdentifier())
              .name(metricInfo.getMetricName())
              .sliEnabled(metricInfo.getSli().isEnabled())
              .riskProfile(riskProfile)
              .continuousVerificationEnabled(metricInfo.getDeploymentVerification().isEnabled())
              .liveMonitoringEnabled(metricInfo.getLiveMonitoring().isEnabled())
              .queryParams(QueryParamsDTO.getQueryParamsDTO(metricInfo.getQueryParams()))
              .build();
      List<MetricThreshold> metricThresholds =
          Optional.ofNullable(nextGenMetricCVConfig.getMetricThresholdDTOs())
              .orElse(Collections.emptyList())
              .stream()
              .filter(metricThreshold -> metricThreshold.getMetricName().equals(queryDefinition.getName()))
              .collect(Collectors.toList());
      // We have already filtered out default thresholds so all the metric thresholds will always be of type custom
      // here.
      if (EmptyPredicate.isNotEmpty(metricThresholds)) {
        metricThresholds.forEach(
            metricThreshold -> metricThreshold.setMetricType(MonitoredServiceConstants.CUSTOM_METRIC_PACK));
      }
      queryDefinition.getMetricThresholds().addAll(metricThresholds);
      queryDefinitions.add(queryDefinition);
    }));
    return NextGenHealthSourceSpec.builder()
        .healthSourceParams(HealthSourceParamsDTO.getHealthSourceParamsDTO(cvConfigs.get(0).getHealthSourceParams()))
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .dataSourceType(cvConfigs.get(0).getType())
        .queryDefinitions(queryDefinitions)
        .build();
  }
}