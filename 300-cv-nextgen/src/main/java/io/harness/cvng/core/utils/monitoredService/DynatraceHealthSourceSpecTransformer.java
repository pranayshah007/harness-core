/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.DynatraceHealthSourceSpec;
import io.harness.cvng.core.entities.DynatraceCVConfig;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class DynatraceHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<DynatraceCVConfig, DynatraceHealthSourceSpec> {
  @Override
  public DynatraceHealthSourceSpec transformToHealthSourceConfig(List<DynatraceCVConfig> cvConfigs) {
    Preconditions.checkArgument(
        cvConfigs.stream().map(DynatraceCVConfig::getDynatraceServiceName).distinct().count() == 1,
        "Dynatrace serviceName should be same for list of all configs.");
    Preconditions.checkArgument(
        cvConfigs.stream().map(DynatraceCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for list of all configs.");
    Preconditions.checkArgument(
        cvConfigs.stream().map(DynatraceCVConfig::getDynatraceServiceId).distinct().count() == 1,
        "Dynatrace serviceEntityId should be same for list of all configs.");
    Preconditions.checkArgument(cvConfigs.stream().map(DynatraceCVConfig::getProductName).distinct().count() == 1,
        "Application feature name should be same for list of all configs.");

    List<DynatraceHealthSourceSpec.DynatraceMetricDefinition> metricDefinitions =
        cvConfigs.stream()
            .flatMap(cv -> CollectionUtils.emptyIfNull(cv.getMetricInfos()).stream().map(metricInfo -> {
              RiskProfile riskProfile = RiskProfile.builder()
                                            .category(cv.getMetricPack().getCategory())
                                            .metricType(metricInfo.getMetricType())
                                            .thresholdTypes(cv.getThresholdTypeOfMetric(metricInfo.getMetricName(), cv))
                                            .build();

              return DynatraceHealthSourceSpec.DynatraceMetricDefinition.builder()
                  .identifier(metricInfo.getIdentifier())
                  .metricName(metricInfo.getMetricName())
                  .metricSelector(metricInfo.getMetricSelector())
                  .isManualQuery(metricInfo.isManualQuery())
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
    return DynatraceHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .serviceId(cvConfigs.get(0).getDynatraceServiceId())
        .serviceMethodIds(cvConfigs.get(0).getServiceMethodIds())
        .feature(cvConfigs.get(0).getProductName())
        .serviceName(cvConfigs.get(0).getDynatraceServiceName())
        .metricPacks(cvConfigs.stream()
                         .filter(cv -> CollectionUtils.isEmpty(cv.getMetricInfos()))
                         .map(cv -> TimeSeriesMetricPackDTO.toMetricPackDTO(cv.getMetricPack()))
                         .collect(Collectors.toSet()))
        .metricDefinitions(metricDefinitions)
        .build();
  }
}
