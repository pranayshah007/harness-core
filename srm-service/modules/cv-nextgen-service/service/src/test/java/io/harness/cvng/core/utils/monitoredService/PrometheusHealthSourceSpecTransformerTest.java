/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.PrometheusHealthSourceSpec;
import io.harness.cvng.core.entities.AnalysisInfo.DeploymentVerification;
import io.harness.cvng.core.entities.AnalysisInfo.LiveMonitoring;
import io.harness.cvng.core.entities.AnalysisInfo.SLI;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig.MetricInfo;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class PrometheusHealthSourceSpecTransformerTest extends CvNextGenTestBase {
  String connectorIdentifier;
  String identifier;
  String monitoringSourceName;
  BuilderFactory builderFactory;
  @Inject PrometheusHealthSourceSpecTransformer prometheusHealthSourceSpecTransformer;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    connectorIdentifier = "connectorId";
    identifier = "healthSourceIdentifier";
    monitoringSourceName = "Prometheus";
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() {
    PrometheusHealthSourceSpec prometheusHealthSourceSpec =
        prometheusHealthSourceSpecTransformer.transform(Arrays.asList(createCVConfig()));

    assertThat(prometheusHealthSourceSpec).isNotNull();
    assertThat(prometheusHealthSourceSpec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(prometheusHealthSourceSpec.getMetricDefinitions().size()).isEqualTo(1);
    PrometheusMetricDefinition metricDefinition = prometheusHealthSourceSpec.getMetricDefinitions().get(0);
    assertThat(metricDefinition.getMetricName()).isEqualTo("myMetric");
    assertThat(metricDefinition.getPrometheusMetric()).isEqualTo("cpu_usage_total");
    assertThat(metricDefinition.getRiskProfile().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
    assertThat(metricDefinition.getRiskProfile().getMetricType()).isEqualTo(TimeSeriesMetricType.RESP_TIME);
    assertThat(metricDefinition.getServiceFilter())
        .contains(
            PrometheusMetricDefinition.PrometheusFilter.builder().labelName("app").labelValue("cv-demo-app").build());
    assertThat(metricDefinition.getEnvFilter())
        .contains(
            PrometheusMetricDefinition.PrometheusFilter.builder().labelName("namespace").labelValue("cv-demo").build());
    assertThat(metricDefinition.getAdditionalFilters())
        .hasSameElementsAs(Arrays.asList(
            PrometheusMetricDefinition.PrometheusFilter.builder().labelName("filter2").labelValue("cv-2").build(),
            PrometheusMetricDefinition.PrometheusFilter.builder().labelName("filter3").labelValue("cv-3").build()));
    assertThat(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getLiveMonitoring().getEnabled()).isTrue();
    assertThat(metricDefinition.getSli().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getRiskProfile().getMetricType())
        .isEqualTo(TimeSeriesMetricType.RESP_TIME);
  }

  private PrometheusCVConfig createCVConfig() {
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().build();
    cvConfig.setConnectorIdentifier(connectorIdentifier);
    cvConfig.setMetricPack(MetricPack.builder().category(CVMonitoringCategory.PERFORMANCE).build());
    MetricInfo metricInfo =
        MetricInfo.builder()
            .metricName("myMetric")
            .metricType(TimeSeriesMetricType.RESP_TIME)
            .prometheusMetricName("cpu_usage_total")
            .envFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                         .labelName("namespace")
                                         .labelValue("cv-demo")
                                         .build()))
            .serviceFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                             .labelName("app")
                                             .labelValue("cv-demo-app")
                                             .build()))
            .additionalFilters(Arrays.asList(
                PrometheusMetricDefinition.PrometheusFilter.builder().labelName("filter2").labelValue("cv-2").build(),
                PrometheusMetricDefinition.PrometheusFilter.builder().labelName("filter3").labelValue("cv-3").build()))
            .deploymentVerification(DeploymentVerification.builder().enabled(Boolean.TRUE).build())
            .liveMonitoring(LiveMonitoring.builder().enabled(Boolean.TRUE).build())
            .sli(SLI.builder().enabled(Boolean.TRUE).build())
            .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    return cvConfig;
  }
}
