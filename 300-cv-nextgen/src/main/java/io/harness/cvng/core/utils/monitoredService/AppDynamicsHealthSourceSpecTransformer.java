/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AppDynamicsHealthSourceSpec.AppDMetricDefinitions;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;

import com.google.common.base.Preconditions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class AppDynamicsHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<AppDynamicsCVConfig, AppDynamicsHealthSourceSpec> {
  @Override
  public AppDynamicsHealthSourceSpec transformToHealthSourceConfig(List<AppDynamicsCVConfig> cvConfigs) {
    Preconditions.checkArgument(cvConfigs.stream().map(AppDynamicsCVConfig::getApplicationName).distinct().count() == 1,
        "Application Name should be same for List of all configs.");
    Preconditions.checkArgument(
        cvConfigs.stream().map(AppDynamicsCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    Preconditions.checkArgument(cvConfigs.stream().map(AppDynamicsCVConfig::getTierName).distinct().count() == 1,
        "Application tier name should be same for List of all configs.");
    Preconditions.checkArgument(cvConfigs.stream().map(AppDynamicsCVConfig::getProductName).distinct().count() == 1,
        "Application feature name should be same for List of all configs.");

    List<AppDMetricDefinitions> metricDefinitions =
        cvConfigs.stream()
            .flatMap(cv -> CollectionUtils.emptyIfNull(cv.getMetricInfos()).stream().map(metricInfo -> {
              RiskProfile riskProfile = RiskProfile.builder()
                                            .category(cv.getMetricPack().getCategory())
                                            .metricType(metricInfo.getMetricType())
                                            .thresholdTypes(cv.getThresholdTypeOfMetric(metricInfo.getMetricName(), cv))
                                            .build();
              return AppDMetricDefinitions.builder()
                  .baseFolder(metricInfo.getBaseFolder())
                  .metricPath(metricInfo.getMetricPath())
                  .completeServiceInstanceMetricPath(metricInfo.getCompleteServiceInstanceMetricPath())
                  .completeMetricPath(metricInfo.getCompleteMetricPath())
                  .identifier(metricInfo.getIdentifier())
                  .metricName(metricInfo.getMetricName())
                  .riskProfile(riskProfile)
                  .sli(SLIDTO.builder().enabled(metricInfo.getSli().isEnabled()).build())
                  .analysis(
                      AnalysisDTO.builder()
                          .liveMonitoring(
                              LiveMonitoringDTO.builder().enabled(metricInfo.getLiveMonitoring().isEnabled()).build())
                          .deploymentVerification(
                              DeploymentVerificationDTO.builder()
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
    Set<TimeSeriesMetricPackDTO> metricPacks = new HashSet<>();
    cvConfigs.forEach(appDynamicsCVConfig -> {
      String identifier = appDynamicsCVConfig.getMetricPack().getIdentifier();
      List<TimeSeriesMetricPackDTO.MetricThreshold> metricThresholds = appDynamicsCVConfig.getMetricThresholdDTOs();
      if (isNotEmpty(metricThresholds)) {
        metricThresholds.forEach(metricThreshold -> metricThreshold.setMetricType(identifier));
      }
      if (!("Custom".equals(identifier) && isEmpty(metricThresholds))) {
        metricPacks.add(
            TimeSeriesMetricPackDTO.builder().identifier(identifier).metricThresholds(metricThresholds).build());
      }
    });

    return AppDynamicsHealthSourceSpec.builder()
        .applicationName(cvConfigs.get(0).getApplicationName())
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .tierName(cvConfigs.get(0).getTierName())
        .feature(cvConfigs.get(0).getProductName())
        .metricPacks(metricPacks)
        .metricDefinitions(metricDefinitions)
        .build();
  }
}
