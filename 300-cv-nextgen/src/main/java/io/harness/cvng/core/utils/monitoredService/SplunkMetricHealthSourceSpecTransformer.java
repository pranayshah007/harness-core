/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SplunkMetricHealthSourceSpec;
import io.harness.cvng.core.entities.AnalysisInfo.DeploymentVerification;
import io.harness.cvng.core.entities.AnalysisInfo.LiveMonitoring;
import io.harness.cvng.core.entities.AnalysisInfo.SLI;
import io.harness.cvng.core.entities.SplunkMetricCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

public class SplunkMetricHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<SplunkMetricCVConfig, SplunkMetricHealthSourceSpec> {
  @Override
  public SplunkMetricHealthSourceSpec transformToHealthSourceConfig(List<SplunkMetricCVConfig> cvConfigs) {
    Preconditions.checkNotNull(cvConfigs);
    Preconditions.checkArgument(
        cvConfigs.stream().map(SplunkMetricCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for all the configs in the list.");
    List<SplunkMetricHealthSourceSpec.SplunkMetricDefinition> metricDefinitions = new ArrayList<>();

    cvConfigs.forEach(splunkMetricCVConfig -> {
      splunkMetricCVConfig.getMetricInfos().forEach(metricInfo -> {
        RiskProfile riskProfile = RiskProfile.builder()
                                      .category(splunkMetricCVConfig.getMetricPack().getCategory())
                                      .metricType(TimeSeriesMetricType.INFRA) // check how to get this.
                                      .thresholdTypes(splunkMetricCVConfig.getThresholdTypeOfMetric(
                                          metricInfo.getMetricName(), splunkMetricCVConfig))
                                      .build();
        SplunkMetricHealthSourceSpec.SplunkMetricDefinition metricDefinition =
            SplunkMetricHealthSourceSpec.SplunkMetricDefinition.builder()
                .groupName(splunkMetricCVConfig.getGroupName())
                .query(metricInfo.getQuery())
                .identifier(metricInfo.getIdentifier())
                .metricName(metricInfo.getMetricName())
                .sli(transformSLIEntityToDTO(metricInfo.getSli()))
                .analysis(AnalysisDTO.builder()
                              .liveMonitoring(transformLiveMonitoringEntityToDTO(metricInfo.getLiveMonitoring()))
                              .deploymentVerification(transformDevelopmentVerificationEntityToDTO(
                                  metricInfo.getDeploymentVerification(), null)) // Deployment is not supported yet.
                              .riskProfile(riskProfile)
                              .build())
                .build();
        metricDefinitions.add(metricDefinition);
      });
    });

    return SplunkMetricHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .metricDefinitions(metricDefinitions)
        .build();
  }

  public DeploymentVerificationDTO transformDevelopmentVerificationEntityToDTO(
      DeploymentVerification deploymentVerification, String serviceInstanceFieldName) {
    return DeploymentVerificationDTO.builder()
        .serviceInstanceFieldName(serviceInstanceFieldName)
        .enabled(deploymentVerification.isEnabled())
        .build();
  }

  private LiveMonitoringDTO transformLiveMonitoringEntityToDTO(LiveMonitoring liveMonitoring) {
    return LiveMonitoringDTO.builder().enabled(liveMonitoring.isEnabled()).build();
  }

  public SLIDTO transformSLIEntityToDTO(SLI sli) {
    return SLIDTO.builder().enabled(sli.isEnabled()).build();
  }
}
