/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.entities.NextGenMetricInfo;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

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
                                    .metricType(TimeSeriesMetricType.INFRA) // check how to get this.
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
              .queryParams(metricInfo.getQueryParams())
              .build();
      queryDefinitions.add(queryDefinition);
    }));

    return NextGenHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .dataSourceType(cvConfigs.get(0).getType())
        .queryDefinitions(queryDefinitions)
        .build();
  }
}