/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SumologicMetricHealthSourceSpec;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.SumologicMetricCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

public class SumologicMetricSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<SumologicMetricCVConfig, SumologicMetricHealthSourceSpec> {
  @Override
  public SumologicMetricHealthSourceSpec transformToHealthSourceConfig(List<SumologicMetricCVConfig> cvConfigs) {
    Preconditions.checkNotNull(cvConfigs);
    Preconditions.checkArgument(
        cvConfigs.stream().map(SumologicMetricCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for all the configs in the list.");
    Preconditions.checkArgument(cvConfigs.stream().map(SumologicMetricCVConfig::getProductName).distinct().count() == 1,
        "Feature name should be same for List of all configs.");
    List<SumologicMetricHealthSourceSpec.SumologicMetricDefinition> metricDefinitions = new ArrayList<>();

    cvConfigs.forEach(SumologicMetricCVConfig -> {
      SumologicMetricCVConfig.getMetricInfos().forEach(metricInfo -> {
        RiskProfile riskProfile = RiskProfile.builder()
                                      .category(SumologicMetricCVConfig.getMetricPack().getCategory())
                                      .metricType(TimeSeriesMetricType.INFRA) // check how to get this.
                                      .thresholdTypes(SumologicMetricCVConfig.getThresholdTypeOfMetric(
                                          metricInfo.getMetricName(), SumologicMetricCVConfig))
                                      .build();
        SumologicMetricHealthSourceSpec.SumologicMetricDefinition metricDefinition =
            SumologicMetricHealthSourceSpec.SumologicMetricDefinition.builder()
                .groupName(SumologicMetricCVConfig.getGroupName())
                .query(metricInfo.getQuery())
                .identifier(metricInfo.getIdentifier())
                .metricName(metricInfo.getMetricName())
                .sli(transformSLIEntityToDTO(metricInfo.getSli()))
                .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder()
                              .liveMonitoring(transformLiveMonitoringEntityToDTO(metricInfo.getLiveMonitoring()))
                              .deploymentVerification(transformDevelopmentVerificationEntityToDTO(
                                  metricInfo.getDeploymentVerification(), null)) // Deployment is not supported yet.
                              .riskProfile(riskProfile)
                              .build())
                .build();
        metricDefinitions.add(metricDefinition);
      });
    });

    return SumologicMetricHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .metricDefinitions(metricDefinitions)
        .build();
  }

  private HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO transformLiveMonitoringEntityToDTO(
      AnalysisInfo.LiveMonitoring liveMonitoring) {
    return HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder()
        .enabled(liveMonitoring.isEnabled())
        .build();
  }
  public HealthSourceMetricDefinition.SLIDTO transformSLIEntityToDTO(AnalysisInfo.SLI sli) {
    return HealthSourceMetricDefinition.SLIDTO.builder().enabled(sli.isEnabled()).build();
  }

  public HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO transformDevelopmentVerificationEntityToDTO(
      AnalysisInfo.DeploymentVerification deploymentVerification, String serviceInstanceFieldName) {
    return HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
        .serviceInstanceFieldName(serviceInstanceFieldName)
        .enabled(deploymentVerification.isEnabled())
        .build();
  }
}