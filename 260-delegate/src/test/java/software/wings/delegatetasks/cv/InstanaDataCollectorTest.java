/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.beans.InstanaConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.instana.InstanaAnalyzeMetrics;
import software.wings.service.impl.instana.InstanaDataCollectionInfo;
import software.wings.service.impl.instana.InstanaDataCollector;
import software.wings.service.impl.instana.InstanaInfraMetrics;
import software.wings.service.intfc.instana.InstanaDelegateService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class InstanaDataCollectorTest extends WingsBaseTest {
  private InstanaConfig config;
  private InstanaDataCollector instanaDataCollector;
  private String host = "test-host";
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Mock DataCollectionExecutionContext dataCollectionExecutionContext;
  @Mock InstanaDelegateService instanaDelegateService;
  private String accountId = generateUuid();

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    initMocks(this);
    config = InstanaConfig.builder()
                 .instanaUrl("https://instana-example.com/")
                 .accountId(accountId)
                 .apiToken(UUID.randomUUID().toString().toCharArray())
                 .build();
    instanaDataCollector = new InstanaDataCollector();
    FieldUtils.writeField(instanaDataCollector, "instanaDelegateService", instanaDelegateService, true);
    when(dataCollectionExecutionContext.getActivityLogger())
        .thenReturn(mock(DelegateCVActivityLogService.Logger.class));
    ThirdPartyApiCallLog thirdPartyApiCallLog = mock(ThirdPartyApiCallLog.class);
    when(dataCollectionExecutionContext.createApiCallLog()).thenReturn(thirdPartyApiCallLog);
    when(dataCollectionExecutionContext.executeRequest(any(), any())).then(invocation -> {
      String title = invocation.getArgument(0, String.class);
      Call<?> call = invocation.getArgument(1, Call.class);
      Response<?> response = call.execute();
      return response.body();
    });
    InstanaInfraMetrics instanaInfraMetrics = getInstanaInfraMetricsEmptyItems();
    InstanaAnalyzeMetrics instanaAnalyzeMetrics = getInstanaTraceMetricsEmptyItems();
    when(instanaDelegateService.getInfraMetrics(any(), any(), any(), any())).thenReturn(instanaInfraMetrics);
    when(instanaDelegateService.getInstanaTraceMetrics(any(), any(), any(), any())).thenReturn(instanaAnalyzeMetrics);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testInit_failIfInvalidQuery() {
    InstanaDataCollectionInfo instanaDataCollectionInfo = createDataCollectionInfo();
    instanaDataCollectionInfo.setQuery("InvalidQueryWithoutHostExpression");
    instanaDataCollector.init(dataCollectionExecutionContext, instanaDataCollectionInfo);
    assertThatThrownBy(() -> instanaDataCollector.fetchMetrics(Lists.newArrayList(host)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query should contain ${host}");
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHostBatchSize() {
    assertThat(instanaDataCollector.getHostBatchSize()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetric_withHostWithEmptyItems() {
    instanaDataCollector.init(dataCollectionExecutionContext, createDataCollectionInfo());
    InstanaInfraMetrics instanaInfraMetrics = getInstanaInfraMetricsEmptyItems();
    InstanaAnalyzeMetrics instanaAnalyzeMetrics = getInstanaTraceMetricsEmptyItems();
    when(instanaDelegateService.getInfraMetrics(any(), any(), any(), any())).thenReturn(instanaInfraMetrics);
    when(instanaDelegateService.getInstanaTraceMetrics(any(), any(), any(), any())).thenReturn(instanaAnalyzeMetrics);
    List<MetricElement> metricElements = instanaDataCollector.fetchMetrics(Lists.newArrayList(host));
    assertThat(metricElements).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetric_withHostWithEmptyData() {
    instanaDataCollector.init(dataCollectionExecutionContext, createDataCollectionInfo());
    InstanaInfraMetrics instanaInfraMetrics = getInstanaInfraMetricsWithNoData();
    when(instanaDelegateService.getInfraMetrics(any(), any(), any(), any())).thenReturn(instanaInfraMetrics);
    List<MetricElement> metricElements = instanaDataCollector.fetchMetrics(Lists.newArrayList(host));
    assertThat(metricElements).isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetric_withHostForInfraMetrics() {
    instanaDataCollector.init(dataCollectionExecutionContext, createDataCollectionInfo());
    InstanaInfraMetrics instanaInfraMetrics = getInstanaInfraMetrics();
    when(instanaDelegateService.getInfraMetrics(any(), any(), any(), any())).thenReturn(instanaInfraMetrics);
    List<MetricElement> metricElements = instanaDataCollector.fetchMetrics(Lists.newArrayList(host));
    assertThat(metricElements).hasSize(1);
    String expected = "[\n"
        + "  {\n"
        + "    \"name\": \"Infrastructure\",\n"
        + "    \"host\": \"test-host\",\n"
        + "    \"groupName\": \"default\",\n"
        + "    \"timestamp\": 1579508880000,\n"
        + "    \"values\": {\n"
        + "      \"Docker containers memory usage\": 246259780.2666,\n"
        + "      \"Docker container total CPU usage\": 0.1265\n"
        + "    }\n"
        + "  }\n"
        + "]";
    assertThat(metricElements).isEqualTo(JsonUtils.asObject(expected, List.class, MetricElement.class));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetric_withHostForTraceMetrics() {
    instanaDataCollector.init(dataCollectionExecutionContext, createDataCollectionInfo());
    InstanaAnalyzeMetrics instanaAnalyzeMetrics = getInstanaAnalysisMetrics();
    String host = "harness-example-deployment-canary-656c858465-42xdw";
    when(instanaDelegateService.getInstanaTraceMetrics(any(), any(), any(), any())).thenReturn(instanaAnalyzeMetrics);
    List<MetricElement> metricElements = instanaDataCollector.fetchMetrics(Lists.newArrayList(host));
    assertThat(metricElements).hasSize(2);
    String expected = "[\n"
        + "  {\n"
        + "    \"name\": \"GET /todolist/exception\",\n"
        + "    \"host\": \"harness-example-deployment-canary-656c858465-42xdw\",\n"
        + "    \"groupName\": \"default\",\n"
        + "    \"timestamp\": 1580724180000,\n"
        + "    \"values\": {\n"
        + "      \"Call latency\": 12.232558139534884,\n"
        + "      \"Error rate\": 0,\n"
        + "      \"Trace count\": 43\n"
        + "    }\n"
        + "  },\n"
        + "  {\n"
        + "    \"name\": \"GET /todolist/inside/addTask\",\n"
        + "    \"host\": \"harness-example-deployment-canary-656c858465-42xdw\",\n"
        + "    \"groupName\": \"default\",\n"
        + "    \"timestamp\": 1580724180000,\n"
        + "    \"values\": {\n"
        + "      \"Call latency\": 1.2307692307692308,\n"
        + "      \"Error rate\": 0,\n"
        + "      \"Trace count\": 13\n"
        + "    }\n"
        + "  }\n"
        + "]";
    assertThat(metricElements).isEqualTo(JsonUtils.asObject(expected, List.class, MetricElement.class));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetric_withoutHostForTraceMetrics() {
    InstanaDataCollectionInfo instanaDataCollectionInfo = createDataCollectionInfoWithoutHost();
    instanaDataCollector.init(dataCollectionExecutionContext, instanaDataCollectionInfo);
    InstanaAnalyzeMetrics instanaAnalyzeMetrics = getInstanaAnalysisMetricsMultiMinute();
    when(instanaDelegateService.getInstanaTraceMetrics(any(), any(), any(), any())).thenReturn(instanaAnalyzeMetrics);
    List<MetricElement> metricElements = instanaDataCollector.fetchMetrics();
    assertThat(metricElements).hasSize(4);
    String expected = "[\n"
        + "  {\n"
        + "    \"name\": \"GET /todolist/exception\",\n"
        + "    \"host\": \"dummy\",\n"
        + "    \"groupName\": \"default\",\n"
        + "    \"timestamp\": 1581530580000,\n"
        + "    \"values\": {\n"
        + "      \"Call latency\": 12.23076923076923,\n"
        + "      \"Error rate\": 0,\n"
        + "      \"Trace count\": 26\n"
        + "    }\n"
        + "  },\n"
        + "  {\n"
        + "    \"name\": \"GET /todolist/exception\",\n"
        + "    \"host\": \"dummy\",\n"
        + "    \"groupName\": \"default\",\n"
        + "    \"timestamp\": 1581530640000,\n"
        + "    \"values\": {\n"
        + "      \"Call latency\": 10.6,\n"
        + "      \"Error rate\": 0,\n"
        + "      \"Trace count\": 55\n"
        + "    }\n"
        + "  },\n"
        + "  {\n"
        + "    \"name\": \"GET /todolist/inside/addTask\",\n"
        + "    \"host\": \"dummy\",\n"
        + "    \"groupName\": \"default\",\n"
        + "    \"timestamp\": 1581530580000,\n"
        + "    \"values\": {\n"
        + "      \"Call latency\": 1.625,\n"
        + "      \"Error rate\": 0,\n"
        + "      \"Trace count\": 8\n"
        + "    }\n"
        + "  },\n"
        + "  {\n"
        + "    \"name\": \"GET /todolist/inside/addTask\",\n"
        + "    \"host\": \"dummy\",\n"
        + "    \"groupName\": \"default\",\n"
        + "    \"timestamp\": 1581530640000,\n"
        + "    \"values\": {\n"
        + "      \"Call latency\": 0.9047619047619048,\n"
        + "      \"Error rate\": 0,\n"
        + "      \"Trace count\": 21\n"
        + "    }\n"
        + "  }\n"
        + "]";
    assertThat(metricElements).isEqualTo(JsonUtils.asObject(expected, List.class, MetricElement.class));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetric_withoutHostWithEmptyItems() {
    instanaDataCollector.init(dataCollectionExecutionContext, createDataCollectionInfoWithoutHost());
    List<MetricElement> metricElements = instanaDataCollector.fetchMetrics();
    assertThat(metricElements).isEmpty();
  }

  private InstanaInfraMetrics getInstanaInfraMetricsWithNoData() {
    String json = "{\n"
        + "    \"items\": [\n"
        + "        {\n"
        + "            \"snapshotId\": \"-Ag3ICIk3z23l4HShQQCWt9KkYQ\",\n"
        + "            \"plugin\": \"docker\",\n"
        + "            \"from\": 1579157474000,\n"
        + "            \"to\": 1579161206000,\n"
        + "            \"tags\": [],\n"
        + "            \"label\": \"hs-harness-todolist-hs (default/cv-pr-kube-kube-v1-service-kube-env-1-9c7c76f7c-sz7n4)\",\n"
        + "            \"host\": \"GCE-3791764252540429558\",\n"
        + "            \"metrics\": {\n"
        + "                \"memory.usage\": [],\n"
        + "                \"cpu.total_usage\": []\n"
        + "            }\n"
        + "        }\n"
        + "    ]\n"
        + "}";
    return JsonUtils.asObject(json, InstanaInfraMetrics.class);
  }

  private InstanaInfraMetrics getInstanaInfraMetrics() {
    String jsonResponse = "{\n"
        + "  \"items\": [\n"
        + "    {\n"
        + "      \"from\": 1579508772000,\n"
        + "      \"to\": 0,\n"
        + "      \"label\": \"hs-harness-todolist-hs (default/cv-pr-kube-kube-v1-appdyn-kube-env-3-6c6c8c94f4-m9fq4)\",\n"
        + "      \"metrics\": {\n"
        + "        \"memory.usage\": [\n"
        + "          [\n"
        + "            1579508880000,\n"
        + "            246259780.2666\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"cpu.total_usage\": [\n"
        + "          [\n"
        + "            1579508880000,\n"
        + "            0.1265\n"
        + "          ]\n"
        + "        ]\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    return JsonUtils.asObject(jsonResponse, InstanaInfraMetrics.class);
  }

  private InstanaAnalyzeMetrics getInstanaAnalysisMetrics() {
    String jsonResponse = "{\n"
        + "  \"items\": [\n"
        + "    {\n"
        + "      \"metrics\": {\n"
        + "        \"latency.mean.60\": [\n"
        + "          [\n"
        + "            1580724180000,\n"
        + "            12.232558139534884\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"errors.mean.60\": [\n"
        + "          [\n"
        + "            1580724180000,\n"
        + "            0\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"traces.sum.60\": [\n"
        + "          [\n"
        + "            1580724180000,\n"
        + "            43\n"
        + "          ]\n"
        + "        ]\n"
        + "      },\n"
        + "      \"name\": \"GET /todolist/exception\",\n"
        + "      \"timestamp\": 1580724193662\n"
        + "    },\n"
        + "    {\n"
        + "      \"metrics\": {\n"
        + "        \"latency.mean.60\": [\n"
        + "          [\n"
        + "            1580724180000,\n"
        + "            1.2307692307692308\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"errors.mean.60\": [\n"
        + "          [\n"
        + "            1580724180000,\n"
        + "            0\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"traces.sum.60\": [\n"
        + "          [\n"
        + "            1580724180000,\n"
        + "            13\n"
        + "          ]\n"
        + "        ]\n"
        + "      },\n"
        + "      \"name\": \"GET /todolist/inside/addTask\",\n"
        + "      \"timestamp\": 1580724193754\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    return JsonUtils.asObject(jsonResponse, InstanaAnalyzeMetrics.class);
  }

  private InstanaAnalyzeMetrics getInstanaAnalysisMetricsMultiMinute() {
    String jsonResponse = "{\n"
        + "  \"items\": [\n"
        + "    {\n"
        + "      \"metrics\": {\n"
        + "        \"latency.mean.60\": [\n"
        + "          [\n"
        + "            1581530580000,\n"
        + "            12.23076923076923\n"
        + "          ],\n"
        + "          [\n"
        + "            1581530640000,\n"
        + "            10.6\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"errors.mean.60\": [\n"
        + "          [\n"
        + "            1581530580000,\n"
        + "            0\n"
        + "          ],\n"
        + "          [\n"
        + "            1581530640000,\n"
        + "            0\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"traces.sum.60\": [\n"
        + "          [\n"
        + "            1581530580000,\n"
        + "            26\n"
        + "          ],\n"
        + "          [\n"
        + "            1581530640000,\n"
        + "            55\n"
        + "          ]\n"
        + "        ]\n"
        + "      },\n"
        + "      \"name\": \"GET /todolist/exception\",\n"
        + "      \"timestamp\": 1581530612236\n"
        + "    },\n"
        + "    {\n"
        + "      \"metrics\": {\n"
        + "        \"latency.mean.60\": [\n"
        + "          [\n"
        + "            1581530580000,\n"
        + "            1.625\n"
        + "          ],\n"
        + "          [\n"
        + "            1581530640000,\n"
        + "            0.9047619047619048\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"errors.mean.60\": [\n"
        + "          [\n"
        + "            1581530580000,\n"
        + "            0\n"
        + "          ],\n"
        + "          [\n"
        + "            1581530640000,\n"
        + "            0\n"
        + "          ]\n"
        + "        ],\n"
        + "        \"traces.sum.60\": [\n"
        + "          [\n"
        + "            1581530580000,\n"
        + "            8\n"
        + "          ],\n"
        + "          [\n"
        + "            1581530640000,\n"
        + "            21\n"
        + "          ]\n"
        + "        ]\n"
        + "      },\n"
        + "      \"name\": \"GET /todolist/inside/addTask\",\n"
        + "      \"timestamp\": 1581530614680\n"
        + "    }"
        + "  ]\n"
        + "}";
    return JsonUtils.asObject(jsonResponse, InstanaAnalyzeMetrics.class);
  }

  private InstanaInfraMetrics getInstanaInfraMetricsEmptyItems() {
    String json = "{\n"
        + "    \"items\": [\n"
        + "    ]\n"
        + "}";
    return JsonUtils.asObject(json, InstanaInfraMetrics.class);
  }

  private InstanaAnalyzeMetrics getInstanaTraceMetricsEmptyItems() {
    String json = "{\n"
        + "    \"items\": [\n"
        + "    ]\n"
        + "}";
    return JsonUtils.asObject(json, InstanaAnalyzeMetrics.class);
  }

  private InstanaDataCollectionInfo createDataCollectionInfo() {
    InstanaDataCollectionInfo instanaDataCollectionInfo = createDataCollectionInfoWithoutHost();
    instanaDataCollectionInfo.setHostsToGroupNameMap(Collections.singletonMap(host, "default"));
    instanaDataCollectionInfo.setHosts(new HashSet<>(Arrays.asList(host)));
    return instanaDataCollectionInfo;
  }

  private InstanaDataCollectionInfo createDataCollectionInfoWithoutHost() {
    List<String> metrics = Lists.newArrayList("cpu.total_usage", "memory.usage");
    return InstanaDataCollectionInfo.builder()
        .query("entity.kubernetes.pod.name:${host}")
        .metrics(metrics)
        .startTime(Instant.now())
        .endTime(Instant.now().plus(1, ChronoUnit.MINUTES))
        .instanaConfig(config)
        .hostTagFilter("kubernetes.pod.name")
        .hostsToGroupNameMap(Collections.emptyMap())
        .hosts(Collections.emptySet())
        .build();
  }
}
