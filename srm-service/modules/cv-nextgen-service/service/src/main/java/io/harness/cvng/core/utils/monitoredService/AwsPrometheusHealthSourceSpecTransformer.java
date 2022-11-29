/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.AwsPrometheusHealthSourceSpec;
import io.harness.cvng.core.entities.AnalysisInfo.DeploymentVerification;
import io.harness.cvng.core.entities.AnalysisInfo.LiveMonitoring;
import io.harness.cvng.core.entities.AnalysisInfo.SLI;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

public class AwsPrometheusHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<AwsPrometheusCVConfig, AwsPrometheusHealthSourceSpec> {
  @Override
  public AwsPrometheusHealthSourceSpec transformToHealthSourceConfig(List<AwsPrometheusCVConfig> cvConfigs) {
    Preconditions.checkNotNull(cvConfigs);
    Preconditions.checkArgument(
        cvConfigs.stream().map(AwsPrometheusCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for all the configs in the list.");
    Preconditions.checkArgument(cvConfigs.stream().map(AwsPrometheusCVConfig::getRegion).distinct().count() == 1,
        "region should be same for all the configs in the list.");
    Preconditions.checkArgument(cvConfigs.stream().map(AwsPrometheusCVConfig::getWorkspaceId).distinct().count() == 1,
        "workspaceId should be same for all the configs in the list.");
    List<PrometheusMetricDefinition> metricDefinitions = new ArrayList<>();
    cvConfigs.forEach(awsPrometheusCVConfig -> awsPrometheusCVConfig.getMetricInfoList().forEach(metricInfo -> {
      RiskProfile riskProfile = RiskProfile.builder()
                                    .category(awsPrometheusCVConfig.getMetricPack().getCategory())
                                    .metricType(metricInfo.getMetricType())
                                    .thresholdTypes(awsPrometheusCVConfig.getThresholdTypeOfMetric(
                                        metricInfo.getMetricName(), awsPrometheusCVConfig))
                                    .build();
      PrometheusMetricDefinition metricDefinition =
          PrometheusMetricDefinition.builder()
              .groupName(awsPrometheusCVConfig.getGroupName())
              .serviceFilter(metricInfo.getServiceFilter())
              .envFilter(metricInfo.getEnvFilter())
              .additionalFilters(metricInfo.getAdditionalFilters())
              .isManualQuery(metricInfo.isManualQuery())
              .query(metricInfo.getQuery())
              .identifier(metricInfo.getIdentifier())
              .metricName(metricInfo.getMetricName())
              .prometheusMetric(metricInfo.getPrometheusMetricName())
              .aggregation(metricInfo.getAggregation())
              .serviceInstanceFieldName(metricInfo.getServiceInstanceFieldName())
              .riskProfile(riskProfile)
              .sli(transformSLIEntityToDTO(metricInfo.getSli()))
              .analysis(AnalysisDTO.builder()
                            .liveMonitoring(transformLiveMonitoringEntityToDTO(metricInfo.getLiveMonitoring()))
                            .deploymentVerification(transformDevelopmentVerificationEntityToDTO(
                                metricInfo.getDeploymentVerification(), metricInfo.getServiceInstanceFieldName()))
                            .riskProfile(riskProfile)
                            .build())
              .build();
      metricDefinitions.add(metricDefinition);
    }));

    return AwsPrometheusHealthSourceSpec.builder()
        .region(cvConfigs.get(0).getRegion())
        .workspaceId(cvConfigs.get(0).getWorkspaceId())
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .metricDefinitions(metricDefinitions)
        .metricPacks(addCustomMetricPacks(cvConfigs))
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
