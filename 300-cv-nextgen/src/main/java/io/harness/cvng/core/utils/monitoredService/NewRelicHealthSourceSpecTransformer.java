package io.harness.cvng.core.utils.monitoredService;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.MetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NewRelicHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NewRelicHealthSourceSpec.NewRelicMetricDefinition;
import io.harness.cvng.core.entities.NewRelicCVConfig;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class NewRelicHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<NewRelicCVConfig, NewRelicHealthSourceSpec> {
  @Override
  public NewRelicHealthSourceSpec transformToHealthSourceConfig(List<NewRelicCVConfig> cvConfigs) {
    Preconditions.checkArgument(cvConfigs.stream().map(NewRelicCVConfig::getApplicationName).distinct().count() == 1,
        "Application Name should be same for List of all configs.");
    Preconditions.checkArgument(
        cvConfigs.stream().map(NewRelicCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    Preconditions.checkArgument(cvConfigs.stream().map(NewRelicCVConfig::getProductName).distinct().count() == 1,
        "Application feature name should be same for List of all configs.");

    List<NewRelicCVConfig> configsWithoutCustom =
        cvConfigs.stream().filter(cvConfig -> cvConfig.getMetricInfos() == null).collect(Collectors.toList());
    if (isNotEmpty(configsWithoutCustom)) {
      Preconditions.checkArgument(
          configsWithoutCustom.stream().map(NewRelicCVConfig::getApplicationId).distinct().count() == 1,
          "ApplicationId should be same for List of all configs.");
    }
    Long appId = isEmpty(configsWithoutCustom) ? null : configsWithoutCustom.get(0).getApplicationId();
    String appName = isEmpty(configsWithoutCustom) ? null : configsWithoutCustom.get(0).getApplicationName();

    List<NewRelicMetricDefinition> newRelicMetricDefinitions =
        cvConfigs.stream()
            .flatMap(cv -> CollectionUtils.emptyIfNull(cv.getMetricInfos()).stream().map(metricInfo -> {
              RiskProfile riskProfile = RiskProfile.builder()
                                            .category(cv.getMetricPack().getCategory())
                                            .metricType(metricInfo.getMetricType())
                                            .thresholdTypes(cv.getThresholdTypeOfMetric(metricInfo.getMetricName(), cv))
                                            .build();
              return NewRelicMetricDefinition.builder()
                  .nrql(metricInfo.getNrql())
                  .responseMapping(metricInfo.getResponseMapping())
                  .metricName(metricInfo.getMetricName())
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
                  .queryType(cv.getQueryType())
                  .build();
            }))
            .collect(Collectors.toList());

    return NewRelicHealthSourceSpec.builder()
        .applicationName(appName)
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .applicationId(appId != null ? String.valueOf(appId) : null)
        .feature(cvConfigs.get(0).getProductName())
        .newRelicMetricDefinitions(newRelicMetricDefinitions)
        .metricPacks(configsWithoutCustom.stream()
                         .map(cv -> MetricPackDTO.toMetricPackDTO(cv.getMetricPack()))
                         .collect(Collectors.toSet()))
        .build();
  }
}
