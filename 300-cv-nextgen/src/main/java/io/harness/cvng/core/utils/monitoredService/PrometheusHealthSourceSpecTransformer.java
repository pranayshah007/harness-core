/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.PrometheusHealthSourceSpec;
import io.harness.cvng.core.constant.MonitoredServiceConstants;
import io.harness.cvng.core.entities.AnalysisInfo.DeploymentVerification;
import io.harness.cvng.core.entities.AnalysisInfo.LiveMonitoring;
import io.harness.cvng.core.entities.AnalysisInfo.SLI;
import io.harness.cvng.core.entities.PrometheusCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrometheusHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<PrometheusCVConfig, PrometheusHealthSourceSpec> {
  @Override
  public PrometheusHealthSourceSpec transformToHealthSourceConfig(List<PrometheusCVConfig> cvConfigs) {
    Preconditions.checkNotNull(cvConfigs);
    Preconditions.checkArgument(
        cvConfigs.stream().map(PrometheusCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for all the configs in the list.");
    List<PrometheusMetricDefinition> metricDefinitions = new ArrayList<>();
    Set<TimeSeriesMetricPackDTO> metricPacks = new HashSet<>();
    cvConfigs.forEach(prometheusCVConfig -> {
      prometheusCVConfig.getMetricInfoList().forEach(metricInfo -> {
        RiskProfile riskProfile = RiskProfile.builder()
                                      .category(prometheusCVConfig.getMetricPack().getCategory())
                                      .metricType(metricInfo.getMetricType())
                                      .thresholdTypes(prometheusCVConfig.getThresholdTypeOfMetric(
                                          metricInfo.getMetricName(), prometheusCVConfig))
                                      .build();
        PrometheusMetricDefinition metricDefinition =
            PrometheusMetricDefinition.builder()
                .groupName(prometheusCVConfig.getGroupName())
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
      });
    });

    cvConfigs.forEach(prometheusCVConfig -> {
      String identifier = MonitoredServiceConstants.CUSTOM_METRIC_PACK;
      List<TimeSeriesMetricPackDTO.MetricThreshold> metricThresholds = prometheusCVConfig.getMetricThresholdDTOs();
      if (isNotEmpty(metricThresholds)) {
        metricThresholds.forEach(metricThreshold -> metricThreshold.setMetricType(identifier));
      }
      metricPacks.add(
          TimeSeriesMetricPackDTO.builder().identifier(identifier).metricThresholds(metricThresholds).build());
    });

    return PrometheusHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .metricDefinitions(metricDefinitions)
        .metricPacks(metricPacks)
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
