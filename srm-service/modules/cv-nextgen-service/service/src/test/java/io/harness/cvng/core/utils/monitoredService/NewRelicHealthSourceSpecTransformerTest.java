/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NewRelicHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NewRelicHealthSourceSpec.NewRelicMetricDefinition;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.NewRelicCVConfig;
import io.harness.cvng.core.entities.NewRelicCVConfig.NewRelicMetricInfo;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NewRelicHealthSourceSpecTransformerTest extends CvNextGenTestBase {
  MetricPack metricPack;
  String applicationName;
  String connectorIdentifier;
  String productName;
  String identifier;
  String monitoringSourceName;
  String applicationId;
  BuilderFactory builderFactory;

  @Inject NewRelicHealthSourceSpecTransformer newRelicHealthSourceSpecTransformer;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    metricPack = MetricPack.builder().identifier("Performance").category(CVMonitoringCategory.PERFORMANCE).build();
    applicationName = "appName";
    connectorIdentifier = "connectorId";
    productName = "apm";
    identifier = "healthSourceIdentifier";
    monitoringSourceName = "AppDynamics";
    applicationId = "1234";
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    NewRelicCVConfig newRelicCVConfig = createCVConfig();
    NewRelicHealthSourceSpec newRelicHealthSourceSpec =
        newRelicHealthSourceSpecTransformer.transform(Arrays.asList(newRelicCVConfig));

    assertThat(newRelicHealthSourceSpec.getApplicationName()).isEqualTo(applicationName);
    assertThat(newRelicHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(newRelicHealthSourceSpec.getApplicationId()).isEqualTo(applicationId);
    assertThat(newRelicHealthSourceSpec.getFeature()).isEqualTo(productName);
    assertThat(newRelicHealthSourceSpec.getMetricPacks().size()).isEqualTo(1);
  }

  private NewRelicCVConfig createCVConfig() {
    return (NewRelicCVConfig) builderFactory.newRelicCVConfigBuilder()
        .applicationId(Long.valueOf(applicationId))
        .applicationName(applicationName)
        .metricPack(metricPack)
        .connectorIdentifier(connectorIdentifier)
        .productName(productName)
        .identifier(identifier)
        .monitoringSourceName(monitoringSourceName)
        .build();
  }

  private NewRelicCVConfig createCVConfigWithCustomMetric() {
    NewRelicCVConfig cvConfig = (NewRelicCVConfig) builderFactory.newRelicCVConfigBuilder()
                                    .groupName("groupName")
                                    .connectorIdentifier(connectorIdentifier)
                                    .productName(productName)
                                    .identifier(identifier)
                                    .monitoringSourceName(monitoringSourceName)
                                    .build();
    cvConfig.setMetricPack(MetricPack.builder()
                               .identifier(CVMonitoringCategory.PERFORMANCE.getDisplayName())
                               .category(CVMonitoringCategory.PERFORMANCE)
                               .build());
    cvConfig.setMetricInfos(Arrays.asList(
        NewRelicMetricInfo.builder()
            .metricName("metric1")
            .identifier("metricId1")
            .nrql("Select * from transactions")
            .metricType(TimeSeriesMetricType.RESP_TIME)
            .responseMapping(MetricResponseMapping.builder()
                                 .metricValueJsonPath("$.metricValue")
                                 .timestampJsonPath("$.timestamp")
                                 .build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(Boolean.TRUE).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(Boolean.TRUE).build())
            .sli(AnalysisInfo.SLI.builder().enabled(Boolean.TRUE).build())
            .build()));
    return cvConfig;
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig_withCustomMetrics() {
    NewRelicCVConfig newRelicCVConfig = createCVConfigWithCustomMetric();
    NewRelicHealthSourceSpec newRelicHealthSourceSpec =
        newRelicHealthSourceSpecTransformer.transform(Arrays.asList(newRelicCVConfig));

    assertThat(newRelicHealthSourceSpec.getNewRelicMetricDefinitions()).isNotEmpty();
    assertThat(newRelicHealthSourceSpec.getNewRelicMetricDefinitions().size()).isEqualTo(1);
    assertThat(newRelicHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(newRelicHealthSourceSpec.getFeature()).isEqualTo(productName);
    assertThat(newRelicHealthSourceSpec.getMetricPacks().size()).isEqualTo(1);
    NewRelicMetricDefinition metricDefinition = newRelicHealthSourceSpec.getNewRelicMetricDefinitions().get(0);

    assertThat(metricDefinition.getGroupName()).isEqualTo(newRelicCVConfig.getGroupName());
    assertThat(metricDefinition.getNrql()).isEqualTo(newRelicCVConfig.getMetricInfos().get(0).getNrql());
    assertThat(metricDefinition.getMetricName()).isEqualTo(newRelicCVConfig.getMetricInfos().get(0).getMetricName());
    assertThat(metricDefinition.getIdentifier()).isEqualTo(newRelicCVConfig.getMetricInfos().get(0).getIdentifier());
    assertThat(metricDefinition.getResponseMapping())
        .isEqualTo(newRelicCVConfig.getMetricInfos().get(0).getResponseMapping());
    assertThat(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getLiveMonitoring().getEnabled()).isTrue();
    assertThat(metricDefinition.getSli().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getRiskProfile().getMetricType())
        .isEqualTo(TimeSeriesMetricType.RESP_TIME);
  }
}
