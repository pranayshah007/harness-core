/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.RiskCategory;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.ELKHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDeserializer;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceVersion;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.PrometheusHealthSourceSpec;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HealthSourceDeserializerTest extends CvNextGenTestBase {
  @Inject HealthSourceDeserializer healthSourceDeserializer;

  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void deserializev1HealthSourceSpecToCorrectHealthSourceType() throws IOException {
    String logJsonAsString =
        Resources.toString(Objects.requireNonNull(HealthSourceDeserializerTest.class.getResource(
                               "/monitoredservice/healthsources/health-source-elasticsearch-v1.json")),
            Charsets.UTF_8);
    String metricJsonAsString =
        Resources.toString(Objects.requireNonNull(HealthSourceDeserializerTest.class.getResource(
                               "/monitoredservice/healthsources/health-source-prometheus-v1.json")),
            Charsets.UTF_8);

    HealthSource logHealthSourceDeserialized = JsonUtils.asObject(logJsonAsString, HealthSource.class);
    HealthSource metricHealthSourceDeserialized = JsonUtils.asObject(metricJsonAsString, HealthSource.class);
    HealthSource elasticSearchHealthSource =
        HealthSource.builder()
            .type(MonitoredServiceDataSourceType.ELASTICSEARCH)
            .identifier("elk_test")
            .name("elk test")
            .version(null)
            .spec(ELKHealthSourceSpec.builder()
                      .queries(List.of(ELKHealthSourceSpec.ELKHealthSourceQueryDTO.builder()
                                           .query("*")
                                           .index("integration-test-1")
                                           .name("ElasticSearch Logs Query")
                                           .messageIdentifier("['_source'].['message']")
                                           .serviceInstanceIdentifier("['_source'].['hostname']")
                                           .timeStampFormat("yyyy MMM dd HH:mm:ss.SSS zzz")
                                           .timeStampIdentifier("['_source'].['@timestamp']")
                                           .build()))
                      .connectorRef("account.ELK_Connector")
                      .feature("ElasticSearch Logs")
                      .build())
            .build();
    HealthSource prometheusHealthSource =
        HealthSource.builder()
            .type(MonitoredServiceDataSourceType.PROMETHEUS)
            .identifier("prometheus")
            .name("prometheus")
            .version(null)
            .spec(
                PrometheusHealthSourceSpec.builder()
                    .metricDefinitions(List.of(
                        PrometheusMetricDefinition.builder()
                            .identifier("prometheus_metric")
                            .metricName("Prometheus Metric")
                            .query(
                                "count(apiserver_request_total{version=\"v1\",component=\"apiserver\",job=\"kubernetes-apiservers\"})")
                            .groupName("g1")
                            .prometheusMetric("apiserver_request_total")
                            .aggregation("count")
                            .isManualQuery(false)
                            .sli(HealthSourceMetricDefinition.SLIDTO.builder().enabled(true).build())
                            .analysis(
                                HealthSourceMetricDefinition.AnalysisDTO.builder()
                                    .liveMonitoring(HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder()
                                                        .enabled(true)
                                                        .build())
                                    .deploymentVerification(
                                        HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                            .enabled(false)
                                            .build())
                                    .riskProfile(RiskProfile.builder()
                                                     .riskCategory(RiskCategory.PERFORMANCE_RESPONSE_TIME)
                                                     .thresholdTypes(List.of(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                                                     .build())
                                    .build())
                            .serviceFilter(List.of(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                       .labelName("version")
                                                       .labelValue("v1")
                                                       .build()))
                            .envFilter(List.of(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                   .labelValue("apiserver")
                                                   .labelName("component")
                                                   .build()))
                            .additionalFilters(List.of(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                           .labelName("job")
                                                           .labelValue("kubernetes-apiservers")
                                                           .build()))

                            .build()))
                    .metricPacks(Set.of(TimeSeriesMetricPackDTO.builder().build()))
                    .connectorRef("account.prometheus_do_not_delete")
                    .build())
            .build();
    assertThat(elasticSearchHealthSource).isEqualTo(logHealthSourceDeserialized);
    assertThat(prometheusHealthSource).isEqualTo(metricHealthSourceDeserialized);
    // Add another log one
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void deserializerv2HealthSourceSpecToCorrectHealthSourceType() throws IOException {
    String logJsonAsText =
        Resources.toString(Objects.requireNonNull(HealthSourceDeserializerTest.class.getResource(
                               "/monitoredservice/healthsources/health-source-sumologic-log-v2.json")),
            Charsets.UTF_8);
    String metricJsonAsText =
        Resources.toString(Objects.requireNonNull(HealthSourceDeserializerTest.class.getResource(
                               "/monitoredservice/healthsources/health-source-sumologic-metrics-v2.json")),
            Charsets.UTF_8);

    HealthSource logHealthSourceDeserialized = JsonUtils.asObject(logJsonAsText, HealthSource.class);
    HealthSource metricHealthSourceDeserialized = JsonUtils.asObject(metricJsonAsText, HealthSource.class);
    HealthSource sumologicLogHealthSource =
        HealthSource.builder()
            .type(MonitoredServiceDataSourceType.SUMOLOGIC_LOG)
            .identifier("Sumologic_Log_Local")
            .name("Sumologic Log Local")
            .version(HealthSourceVersion.V2)
            .spec(builderFactory.createNextGenHealthSourceSpecLogs("sample_identifier", DataSourceType.SUMOLOGIC_LOG)
                      .build())
            .build();

    HealthSource sumologicMetricsHealthSource =
        HealthSource.builder()
            .type(MonitoredServiceDataSourceType.SUMOLOGIC_METRICS)
            .identifier("sumologic_metric_sample")
            .name("sumologic metric sample")
            .version(HealthSourceVersion.V2)
            .spec(builderFactory
                      .createNextGenHealthSourceSpecMetric("sample_identifier", DataSourceType.SUMOLOGIC_METRICS)
                      .build())
            .build();
    assertThat(sumologicLogHealthSource).isEqualTo(logHealthSourceDeserialized);
    assertThat(sumologicMetricsHealthSource).isEqualTo(metricHealthSourceDeserialized);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void cannotDeserializev1HealthSourceSpecToCorrectHealthSourceType() {
    String healthSourceJsonAsString = "{\n"
        + "  \"name\": \"prometheus\",\n"
        + "  \"identifier\": \"prometheus\",\n"
        + "  \"type\": \"UnimplementedHealthSourceType\",\n"
        + "  \"spec\": {}\n"
        + "  }";
    assertThatThrownBy(() -> JsonUtils.asObject(healthSourceJsonAsString, HealthSource.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("InvalidFormatException: Cannot deserialize value of type");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void cannotDeserializev2HealthSourceSpecToCorrectHealthSourceType() {
    String healthSourceJsonAsString = "{\n"
        + "  \"name\": \"sumologic_logs\",\n"
        + "  \"identifier\": \"Sumologic_Logs_id\",\n"
        + "  \"type\": \"SumologicLogs\",\n"
        + "  \"spec\": {}\n"
        + "  }";
    assertThatThrownBy(() -> JsonUtils.asObject(healthSourceJsonAsString, HealthSource.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("javax.ws.rs.BadRequestException: Spec is not serializable, it doesn't match any schema.");
  }
}
