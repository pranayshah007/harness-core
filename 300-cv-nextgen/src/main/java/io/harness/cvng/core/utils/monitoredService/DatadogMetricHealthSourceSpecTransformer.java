package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.DatadogMetricHealthDefinition;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.DatadogMetricHealthSourceSpec;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

public class DatadogMetricHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<DatadogMetricCVConfig, DatadogMetricHealthSourceSpec> {
  @Override
  public DatadogMetricHealthSourceSpec transformToHealthSourceConfig(List<DatadogMetricCVConfig> cvConfigs) {
    Preconditions.checkNotNull(cvConfigs);
    Preconditions.checkArgument(
        cvConfigs.stream().map(DatadogMetricCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for all the configs in the list.");
    List<DatadogMetricHealthDefinition> metricDefinitions = new ArrayList<>();
    cvConfigs.forEach(cvConfig -> cvConfig.getMetricInfoList().forEach(metricInfo -> {
      RiskProfile riskProfile = RiskProfile.builder()
                                    .category(cvConfig.getMetricPack().getCategory())
                                    .metricType(metricInfo.getMetricType())
                                    .thresholdTypes(cvConfig.getThresholdTypeOfMetric(metricInfo.getMetric(), cvConfig))
                                    .build();
      DatadogMetricHealthDefinition metricDefinition =
          DatadogMetricHealthDefinition.builder()
              .dashboardId(cvConfig.getDashboardId())
              .dashboardName(cvConfig.getDashboardName())
              .metricName(metricInfo.getMetricName())
              .identifier(metricInfo.getIdentifier())
              .metric(metricInfo.getMetric())
              .serviceInstanceIdentifierTag(metricInfo.getServiceInstanceIdentifierTag())
              .query(metricInfo.getQuery())
              .groupingQuery(metricInfo.getGroupingQuery())
              .aggregation(metricInfo.getAggregation())
              .metricTags(metricInfo.getMetricTags())
              .isManualQuery(metricInfo.isManualQuery())
              .riskProfile(riskProfile)
              .sli(transformSLIEntityToDTO(metricInfo.getSli()))
              .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder()
                            .liveMonitoring(transformLiveMonitoringEntityToDTO(metricInfo.getLiveMonitoring()))
                            .deploymentVerification(transformDevelopmentVerificationEntityToDTO(
                                metricInfo.getDeploymentVerification(), metricInfo.getServiceInstanceIdentifierTag()))
                            .riskProfile(riskProfile)
                            .build())
              .build();

      metricDefinitions.add(metricDefinition);
    }));
    return DatadogMetricHealthSourceSpec.builder()
        .feature(cvConfigs.get(0).getProductName())
        .metricDefinitions(metricDefinitions)
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .build();
  }

  public HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO transformDevelopmentVerificationEntityToDTO(
      AnalysisInfo.DeploymentVerification deploymentVerification, String serviceInstanceFieldName) {
    return HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
        .serviceInstanceFieldName(serviceInstanceFieldName)
        .enabled(deploymentVerification.isEnabled())
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
}
