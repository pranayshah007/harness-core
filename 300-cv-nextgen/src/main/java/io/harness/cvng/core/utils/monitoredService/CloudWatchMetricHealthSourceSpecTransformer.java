/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CloudWatchMetricsHealthSourceSpec;
import io.harness.cvng.core.entities.CloudWatchMetricCVConfig;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

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
        "Application feature name should be same for List of all configs.");

    List<CloudWatchMetricCVConfig> configsWithoutCustom =
        cvConfigs.stream().filter(cvConfig -> cvConfig.getMetricInfos().isEmpty()).collect(Collectors.toList());
    if (CollectionUtils.isNotEmpty(configsWithoutCustom)) {
      Preconditions.checkArgument(
          configsWithoutCustom.stream().map(CloudWatchMetricCVConfig::getRegion).distinct().count() == 1,
          "Region should be same for List of all configs.");
    }

    // TODO: Test for cloudwatch data-collection.
    String region = "";
    List<CloudWatchMetricsHealthSourceSpec.CloudWatchMetricDefinition> cloudWatchMetricDefinitions =
        cvConfigs.stream()
            .flatMap(cv -> CollectionUtils.emptyIfNull(cv.getMetricInfos()).stream().map(metricInfo -> {
              RiskProfile riskProfile = RiskProfile.builder()
                                            .category(cv.getMetricPack().getCategory())
                                            .metricType(metricInfo.getMetricType())
                                            .thresholdTypes(cv.getThresholdTypeOfMetric(metricInfo.getMetricName(), cv))
                                            .build();
              return CloudWatchMetricsHealthSourceSpec.CloudWatchMetricDefinition.builder()
                  .expression(metricInfo.getExpression())
                  .responseMapping(metricInfo.getResponseMapping())
                  .metricName(metricInfo.getMetricName())
                  .identifier(metricInfo.getIdentifier())
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
                  .groupName(cv.getGroupName())
                  .build();
            }))
            .collect(Collectors.toList());

    return CloudWatchMetricsHealthSourceSpec.builder()
        .region(region)
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .feature(cvConfigs.get(0).getProductName())
        .metricDefinitions(cloudWatchMetricDefinitions)
        .metricThresholds(configsWithoutCustom.stream()
                              .map(cv -> TimeSeriesMetricPackDTO.toMetricPackDTO(cv.getMetricPack()))
                              .collect(Collectors.toSet()))
        .build();
  }
}
