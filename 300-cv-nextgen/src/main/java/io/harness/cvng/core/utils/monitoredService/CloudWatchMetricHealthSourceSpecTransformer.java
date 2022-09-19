/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO.MetricThreshold;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CloudWatchMetricsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CloudWatchMetricsHealthSourceSpec.CloudWatchMetricDefinition;
import io.harness.cvng.core.constant.MonitoredServiceConstants;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CloudWatchMetricHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<CloudWatchMetricCVConfig, CloudWatchMetricsHealthSourceSpec> {
  @Override
  public CloudWatchMetricsHealthSourceSpec transformToHealthSourceConfig(List<CloudWatchMetricCVConfig> cvConfigs) {
    Preconditions.checkArgument(cvConfigs.stream().map(CloudWatchMetricCVConfig::getRegion).distinct().count() == 1,
        "Region should be same for List of all configs.");
    Preconditions.checkArgument(
        cvConfigs.stream().map(CloudWatchMetricCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    Preconditions.checkArgument(
        cvConfigs.stream().map(CloudWatchMetricCVConfig::getProductName).distinct().count() == 1,
        "Feature name should be same for List of all configs.");

    final List<CloudWatchMetricDefinition> cloudWatchMetricDefinitions = new ArrayList<>();

    cvConfigs.forEach(cv -> cv.getMetricInfos().forEach(metricInfo -> {
      RiskProfile riskProfile = RiskProfile.builder()
                                    .category(cv.getMetricPack().getCategory())
                                    .metricType(metricInfo.getMetricType())
                                    .thresholdTypes(cv.getThresholdTypeOfMetric(metricInfo.getMetricName(), cv))
                                    .build();
      CloudWatchMetricDefinition cloudWatchMetricDefinition =
          CloudWatchMetricDefinition.builder()
              .identifier(metricInfo.getIdentifier())
              .metricName(metricInfo.getMetricName())
              .groupName(cv.getGroupName())
              .expression(metricInfo.getExpression())
              .responseMapping(metricInfo.getResponseMapping())
              .riskProfile(riskProfile)
              .sli(HealthSourceMetricDefinition.SLIDTO.builder().enabled(metricInfo.getSli().isEnabled()).build())
              .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder()
                            .liveMonitoring(HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder()
                                                .enabled(metricInfo.getLiveMonitoring().isEnabled())
                                                .build())
                            .deploymentVerification(
                                HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                    .enabled(metricInfo.getDeploymentVerification().isEnabled())
                                    .serviceInstanceMetricPath(
                                        metricInfo.getDeploymentVerification().getServiceInstanceMetricPath())
                                    .build())
                            .riskProfile(riskProfile)
                            .build())

              .build();
      cloudWatchMetricDefinitions.add(cloudWatchMetricDefinition);
    }));

    Set<TimeSeriesMetricPackDTO> metricThresholds = new HashSet<>();
    List<MetricThreshold> allMetricThresholds = new ArrayList<>();
    cvConfigs.forEach(cloudWatchMetricCVConfig -> {
      String identifier = cloudWatchMetricCVConfig.getMetricPack().getIdentifier();
      List<MetricThreshold> metricThresholdDTOs = cloudWatchMetricCVConfig.getMetricThresholdDTOs();
      if (isNotEmpty(metricThresholdDTOs)) {
        metricThresholdDTOs.forEach(metricThresholdDTO -> metricThresholdDTO.setMetricType(identifier));
        allMetricThresholds.addAll(metricThresholdDTOs);
      }
    });
    if (allMetricThresholds.size() > 0) {
      metricThresholds.add(TimeSeriesMetricPackDTO.builder()
                               .metricThresholds(allMetricThresholds)
                               .identifier(MonitoredServiceConstants.CUSTOM_METRIC_PACK)
                               .build());
    }

    return CloudWatchMetricsHealthSourceSpec.builder()
        .region(cvConfigs.get(0).getRegion())
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .feature(cvConfigs.get(0).getProductName())
        .metricDefinitions(cloudWatchMetricDefinitions)
        .metricThresholds(metricThresholds)
        .build();
  }
}
