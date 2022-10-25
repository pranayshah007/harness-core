/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PRANJAL;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.api.DeploymentType.KUBERNETES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.metrics.MetricType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.sm.states.DatadogState.Metric;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DatadogStateTest extends WingsBaseTest {
  private static final SecureRandom random = new SecureRandom();

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void metrics() {
    Map<String, DatadogState.Metric> metrics =
        DatadogState.metrics(Optional.of(Lists.newArrayList("trace.servlet.request.duration")), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());
    assertThat(metrics.containsKey("trace.servlet.request.duration")).isTrue();
    assertThat(metrics.get("trace.servlet.request.duration").getDatadogMetricType()).isEqualTo("Servlet");
    assertThat(metrics.get("trace.servlet.request.duration").getTags()).isEqualTo(Sets.newHashSet("Servlet"));
    assertThat(metrics.get("trace.servlet.request.duration").getMetricName())
        .isEqualTo("trace.servlet.request.duration");
    assertThat(metrics.get("trace.servlet.request.duration").getDisplayName()).isEqualTo("Request Duration");
    assertThat(metrics.get("trace.servlet.request.duration").getMlMetricType()).isEqualTo(MetricType.RESP_TIME.name());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void metricDefinitions() {
    Map<String, DatadogState.Metric> metrics =
        DatadogState.metrics(Optional.of(Lists.newArrayList("trace.servlet.request.duration", "system.cpu.iowait")),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    List<DatadogState.Metric> metricList = new ArrayList<>();
    metricList.add(metrics.get("trace.servlet.request.duration"));
    metricList.add(metrics.get("system.cpu.iowait"));
    Map<String, TimeSeriesMetricDefinition> metricDefinitionMap = DatadogState.metricDefinitions(metricList);

    assertThat(metricDefinitionMap.containsKey("Request Duration")).isTrue();
    assertThat(metricDefinitionMap.containsKey("IO Wait")).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void metricEndpointsInfo() {
    Map<String, List<APMMetricInfo>> metricEndpointsInfo = DatadogState.metricEndpointsInfo(Optional.of("todolist"),
        Optional.of(Lists.newArrayList("system.cpu.iowait", "trace.servlet.request.duration")), Optional.empty(),
        Optional.empty(), Optional.of(KUBERNETES));
    assertThat(metricEndpointsInfo).hasSize(4);
    List<APMMetricInfo> apmMetricInfos = metricEndpointsInfo.values().iterator().next();
    for (String query : metricEndpointsInfo.keySet()) {
      if (query.contains("system.cpu.iowait")) {
        apmMetricInfos = metricEndpointsInfo.get(query);
      }
    }

    assertThat(
        "[{\"metricName\":\"IO Wait\",\"responseMappers\":{\"host\":{\"fieldName\":\"host\",\"jsonPath\":\"series[*].scope\",\"regexs\":[\"((?<=host:)([^,]*))\"]},\"value\":{\"fieldName\":\"value\",\"jsonPath\":\"series[*].pointlist[*].[1]\"},\"txnName\":{\"fieldName\":\"txnName\",\"jsonPath\":\"series[*].metric\"},\"timestamp\":{\"fieldName\":\"timestamp\",\"jsonPath\":\"series[*].pointlist[*].[0]\"}},\"metricType\":\"INFRA\",\"tag\":\"System\"}]")
        .isEqualTo(JsonUtils.asJson(apmMetricInfos));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void metricEndpointsInfoTransformation() {
    Map<String, List<APMMetricInfo>> metricEndpointsInfo = DatadogState.metricEndpointsInfo(Optional.empty(),
        Optional.of(Lists.newArrayList("kubernetes.cpu.usage.total")), Optional.empty(), Optional.empty(),
        Optional.of(KUBERNETES));
    assertThat(metricEndpointsInfo).hasSize(1);
    List<APMMetricInfo> apmMetricInfos = metricEndpointsInfo.values().iterator().next();
    String query =
        "query?api_key=${apiKey}&application_key=${applicationKey}&from=${start_time_seconds}&to=${end_time_seconds}&query=kubernetes.cpu.usage.total{pod_name:${host}}by{pod_name}.rollup(avg,60)/1000000000/kubernetes.cpu.capacity{*}.rollup(avg,60)*100";
    assertThat(metricEndpointsInfo.keySet().iterator().next()).isEqualTo(query);

    assertThat(
        "[{\"metricName\":\"K8 CPU Usage\",\"responseMappers\":{\"host\":{\"fieldName\":\"host\",\"jsonPath\":\"series[*].scope\",\"regexs\":[\"((?<=pod_name:)([^,]*))\"]},\"value\":{\"fieldName\":\"value\",\"jsonPath\":\"series[*].pointlist[*].[1]\"},\"txnName\":{\"fieldName\":\"txnName\",\"jsonPath\":\"series[*].metric\",\"regexs\":[\"((?<=[(]|^)[^(]([^ /|+|-|*]*))\"]},\"timestamp\":{\"fieldName\":\"timestamp\",\"jsonPath\":\"series[*].pointlist[*].[0]\"}},\"metricType\":\"INFRA\",\"tag\":\"Kubernetes\"}]")
        .isEqualTo(JsonUtils.asJson(apmMetricInfos));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void metricEndpointsInfoDocker() {
    Map<String, List<APMMetricInfo>> metricEndpointsInfo =
        DatadogState.metricEndpointsInfo(Optional.empty(), Optional.of(Lists.newArrayList("docker.cpu.usage")),
            Optional.empty(), Optional.empty(), Optional.of(KUBERNETES));
    assertThat(metricEndpointsInfo).hasSize(1);
    List<APMMetricInfo> apmMetricInfos = metricEndpointsInfo.values().iterator().next();
    String query =
        "query?api_key=${apiKey}&application_key=${applicationKey}&from=${start_time_seconds}&to=${end_time_seconds}&query=docker.cpu.usage{pod_name:${host}}by{pod_name}.rollup(avg,60)";
    assertThat(metricEndpointsInfo.keySet().iterator().next()).isEqualTo(query);
    assertThat(
        "[{\"metricName\":\"Docker CPU Usage\",\"responseMappers\":{\"host\":{\"fieldName\":\"host\",\"jsonPath\":\"series[*].scope\",\"regexs\":[\"((?<=pod_name:)([^,]*))\"]},\"value\":{\"fieldName\":\"value\",\"jsonPath\":\"series[*].pointlist[*].[1]\"},\"txnName\":{\"fieldName\":\"txnName\",\"jsonPath\":\"series[*].metric\",\"regexs\":[\"((?<=[(]|^)[^(]([^ /|+|-|*]*))\"]},\"timestamp\":{\"fieldName\":\"timestamp\",\"jsonPath\":\"series[*].pointlist[*].[0]\"}},\"metricType\":\"INFRA\",\"tag\":\"Docker\"}]")
        .isEqualTo(JsonUtils.asJson(apmMetricInfos));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void metricEndpointsInfoDocker24x7() {
    Map<String, List<APMMetricInfo>> metricEndpointsInfo =
        DatadogState.metricEndpointsInfo(Optional.of("todolist"), Optional.of(Lists.newArrayList("docker.cpu.usage")),
            Optional.of("cluster:harness-test"), Optional.empty(), Optional.empty());
    assertThat(metricEndpointsInfo).hasSize(4);
    List<APMMetricInfo> apmMetricInfos = metricEndpointsInfo.values().iterator().next();
    String query =
        "query?api_key=${apiKey}&application_key=${applicationKey}&from=${start_time_seconds}&to=${end_time_seconds}&query=docker.cpu.usage{cluster:harness-test}.rollup(avg,60)";
    assertThat(metricEndpointsInfo.keySet().iterator().next()).isEqualTo(query);
    assertThat(
        "[{\"metricName\":\"Docker CPU Usage\",\"responseMappers\":{\"host\":{\"fieldName\":\"host\",\"jsonPath\":\"series[*].scope\",\"regexs\":[\"((?<=pod_name:)([^,]*))\"]},\"value\":{\"fieldName\":\"value\",\"jsonPath\":\"series[*].pointlist[*].[1]\"},\"txnName\":{\"fieldName\":\"txnName\",\"jsonPath\":\"series[*].metric\",\"regexs\":[\"((?<=[(]|^)[^(]([^ /|+|-|*]*))\"]},\"timestamp\":{\"fieldName\":\"timestamp\",\"jsonPath\":\"series[*].pointlist[*].[0]\"}},\"metricType\":\"INFRA\",\"tag\":\"Docker\"}]")
        .isEqualTo(JsonUtils.asJson(apmMetricInfos));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testBadMetric() {
    Map<String, List<APMMetricInfo>> metricEndpointsInfo =
        DatadogState.metricEndpointsInfo(Optional.of("todolist"), Optional.of(Lists.newArrayList("dummyMetricName")),
            Optional.empty(), Optional.empty(), Optional.of(KUBERNETES));
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testServletSystemMetrics() {
    Map<String, List<APMMetricInfo>> metricEndpointsInfo = DatadogState.metricEndpointsInfo(Optional.empty(),
        Optional.of(Lists.newArrayList("trace.servlet.request.duration", "system.cpu.iowait")), Optional.empty(),
        Optional.empty(), Optional.of(KUBERNETES));
    assertThat(metricEndpointsInfo.size() == 2).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testServiceLevelMetrics() {
    Map<String, List<APMMetricInfo>> metricEndpointsInfo = DatadogState.metricEndpointsInfo(
        Optional.of("todolist"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(KUBERNETES));
    assertThat(metricEndpointsInfo).hasSize(3);
    Set<String> traceMetrics = new HashSet<>(Arrays.asList("Request Duration", "Errors", "Hits"));
    metricEndpointsInfo.forEach((k, v) -> {
      assertThat(v.size() == 1).isTrue();
      assertThat(traceMetrics.contains(v.get(0).getMetricName())).isTrue();
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCustomDatadogMetricsValidateTxnName() {
    Map<String, Set<Metric>> customMetricMap = new HashMap<>();
    Set<Metric> metrics = new HashSet<>();
    metrics.add(Metric.builder()
                    .txnName("transaction1")
                    .displayName("display")
                    .mlMetricType("RESP_TIME")
                    .metricName("test.metric.1")
                    .datadogMetricType("Custom")
                    .build());
    metrics.add(Metric.builder()
                    .txnName("transaction1")
                    .displayName("display2")
                    .mlMetricType("THROUGHPUT")
                    .metricName("test.metric.2")
                    .datadogMetricType("Custom")
                    .build());
    customMetricMap.put("service:todolist", metrics);
    Map<String, List<APMMetricInfo>> metricEndpointsInfo = DatadogState.metricEndpointsInfo(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(customMetricMap), Optional.empty());
    assertThat(metricEndpointsInfo).hasSize(2);
    Set<String> traceMetrics = new HashSet<>(Arrays.asList("Request Duration", "Errors", "Hits"));
    metricEndpointsInfo.forEach((k, v) -> {
      v.forEach(metricInfo -> {
        assertThat(metricInfo.getResponseMappers().containsKey("txnName")).isTrue();
        assertThat(metricInfo.getResponseMappers().get("txnName").getFieldValue().equals("transaction1")).isTrue();
      });
    });
  }

  private Set<Metric> getMetricsSet(List<String> metricType) {
    Set<Metric> metrics = new HashSet<>();
    int i = 0;
    metricType.forEach(type -> {
      metrics.add(Metric.builder()
                      .txnName("transaction1")
                      .displayName("display23" + random.nextInt())
                      .mlMetricType(type)
                      .metricName("test.metric3" + random.nextInt())
                      .datadogMetricType("Custom")
                      .build());
    });

    return metrics;
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateCustomFields2Throughput() {
    Map<String, Set<Metric>> customMetricMap = new HashMap<>();

    customMetricMap.put("service:todolist", getMetricsSet(Arrays.asList("THROUGHPUT", "THROUGHPUT")));

    Map<String, String> invalidFields = DatadogState.validateDatadogCustomMetrics(customMetricMap);
    assertThat(invalidFields).hasSize(2);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateCustomFieldsOnlyThroughput() {
    Map<String, Set<Metric>> customMetricMap = new HashMap<>();

    Set<Metric> metrics = getMetricsSet(Arrays.asList("THROUGHPUT"));

    customMetricMap.put("service:todolist", metrics);

    Map<String, String> invalidFields = DatadogState.validateDatadogCustomMetrics(customMetricMap);
    assertThat(invalidFields).hasSize(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateCustomFieldsMissingThroughput() {
    Map<String, Set<Metric>> customMetricMap = new HashMap<>();

    Set<Metric> metrics = getMetricsSet(Arrays.asList("RESP_TIME", "ERROR"));

    customMetricMap.put("service:todolist", metrics);

    Map<String, String> invalidFields = DatadogState.validateDatadogCustomMetrics(customMetricMap);
    assertThat(invalidFields).hasSize(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateCustomFieldsGoodCase() {
    Map<String, Set<Metric>> customMetricMap = new HashMap<>();

    Set<Metric> metrics = getMetricsSet(Arrays.asList("THROUGHPUT", "RESP_TIME", "ERROR"));

    customMetricMap.put("service:todolist", metrics);

    Map<String, String> invalidFields = DatadogState.validateDatadogCustomMetrics(customMetricMap);
    assertThat(invalidFields).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateTraceMetricInWorkflowHappyCase() {
    String metrics = "docker.mem.rss,kubernetes.cpu.usage.total";
    DatadogState state = new DatadogState("testName");
    state.setMetrics(metrics);
    Map<String, String> validateFields = state.validateFields();
    assertThat(validateFields).hasSize(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateTraceMetricInWorkflowTraceMetrics() {
    String metrics = "docker.mem.rss,kubernetes.cpu.usage.total,trace.servlet.request.errors";
    DatadogState state = new DatadogState("testName");
    state.setMetrics(metrics);
    Map<String, String> validateFields = state.validateFields();
    assertThat(validateFields).hasSize(1);
    assertThat(validateFields.containsKey("trace.servlet.request.errors")).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCustomMetricMetricType() {
    Map<String, Set<Metric>> customMetrics = new HashMap<>();
    customMetrics.put(generateUuid(),
        Sets.newHashSet(Metric.builder().mlMetricType(MetricType.INFRA.name()).displayName("metric1").build(),
            Metric.builder().mlMetricType(MetricType.ERROR.name()).displayName("metric2").build()));
    DatadogCVServiceConfiguration datadogCVServiceConfiguration =
        DatadogCVServiceConfiguration.builder().customMetrics(customMetrics).build();

    assertThat(MetricType.INFRA.name())
        .isEqualTo(DatadogState.getMetricTypeForMetric("metric1", datadogCVServiceConfiguration));
    assertThat(MetricType.ERROR.name())
        .isEqualTo(DatadogState.getMetricTypeForMetric("metric2", datadogCVServiceConfiguration));
  }
}
