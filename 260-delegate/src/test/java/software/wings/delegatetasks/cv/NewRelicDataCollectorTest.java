/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateCVActivityLogService.Logger;
import software.wings.helpers.ext.newrelic.NewRelicRestClient;
import software.wings.service.impl.analysis.MetricElement;
import software.wings.service.impl.newrelic.NewRelicApplicationInstance;
import software.wings.service.impl.newrelic.NewRelicApplicationInstancesResponse;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfoV2;
import software.wings.service.impl.newrelic.NewRelicMetric;
import software.wings.service.impl.newrelic.NewRelicMetricData;
import software.wings.service.impl.newrelic.NewRelicMetricDataResponse;
import software.wings.service.impl.newrelic.NewRelicMetricResponse;

import com.google.inject.Inject;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import okhttp3.Request;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Lists;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;
import retrofit2.Call;
import retrofit2.Response;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class NewRelicDataCollectorTest extends WingsBaseTest {
  private static final SecureRandom random = new SecureRandom();

  private NewRelicConfig config;
  private NewRelicDataCollector newRelicDataCollector;
  private String host = "test-host";
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Mock DataCollectionExecutionContext dataCollectionExecutionContext;
  @Mock NewRelicRestClient newRelicRestClient;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    initMocks(this);
    config = NewRelicConfig.builder()
                 .accountId("123")
                 .newRelicUrl("https://newrelic-test-host.com:8089")
                 .newRelicAccountId(UUID.randomUUID().toString())
                 .apiKey("api-key".toCharArray())
                 .build();
    newRelicDataCollector = spy(new NewRelicDataCollector());
    FieldUtils.writeField(newRelicDataCollector, "dataCollectionService", dataCollectionService, true);
    when(dataCollectionExecutionContext.getActivityLogger()).thenReturn(mock(Logger.class));
    ThirdPartyApiCallLog thirdPartyApiCallLog = mock(ThirdPartyApiCallLog.class);
    when(dataCollectionExecutionContext.createApiCallLog()).thenReturn(thirdPartyApiCallLog);
    when(dataCollectionExecutionContext.executeRequest(any(), any())).then(invocation -> {
      String title = invocation.getArgument(0, String.class);
      Call<?> call = invocation.getArgument(1, Call.class);
      Response<?> response = call.execute();
      return response.body();
    });
    mockListMetricNames("test-metric-name");
    mockListAppInstances();
  }

  private void mockListMetricNames(String metricName) throws IOException {
    Call<NewRelicMetricResponse> requestCall = mock(Call.class);
    Request request = new Request.Builder().url(config.getNewRelicUrl()).build();
    when(requestCall.request()).thenReturn(request);
    NewRelicMetricResponse newRelicMetricResponse =
        NewRelicMetricResponse.builder()
            .metrics(Arrays.asList(NewRelicMetric.builder().name(metricName).build()))
            .build();
    when(requestCall.execute()).thenReturn(Response.success(newRelicMetricResponse));
    when(newRelicRestClient.listMetricNames(anyString(), anyLong(), any())).thenReturn(requestCall);
  }

  private void mockListAppInstances() throws IOException {
    Call<NewRelicApplicationInstancesResponse> requestCall = getMockNewRelicApplicationInstancesResponseCall(
        Arrays.asList(NewRelicApplicationInstance.builder().host(host).port(80).id(847475).build()));
    when(newRelicRestClient.listAppInstances(anyString(), anyLong(), anyInt()))
        .thenReturn(requestCall)
        .thenReturn(getMockNewRelicApplicationInstancesResponseCall(Lists.emptyList()));
  }

  private void mockGetInstanceMetricData() throws IOException {
    Call<NewRelicMetricDataResponse> requestCall = mock(Call.class);
    Request request = new Request.Builder().url(config.getNewRelicUrl()).build();
    when(requestCall.request()).thenReturn(request);
    NewRelicMetricDataResponse newRelicMetricDataResponse =
        NewRelicMetricDataResponse.builder().metric_data(getNewRelicMetricData()).build();
    when(requestCall.execute()).thenReturn(Response.success(newRelicMetricDataResponse));
    when(newRelicRestClient.getInstanceMetricData(any(), anyLong(), anyLong(), any(), any(), any()))
        .thenReturn(requestCall);
  }

  private void mockGetApplicationMetricData() throws IOException {
    Call<NewRelicMetricDataResponse> requestCall = mock(Call.class);
    Request request = new Request.Builder().url(config.getNewRelicUrl()).build();
    when(requestCall.request()).thenReturn(request);
    NewRelicMetricDataResponse newRelicMetricDataResponse =
        NewRelicMetricDataResponse.builder().metric_data(getNewRelicMetricData()).build();
    when(requestCall.execute()).thenReturn(Response.success(newRelicMetricDataResponse));
    when(newRelicRestClient.getApplicationMetricData(
             anyString(), anyLong(), anyBoolean(), anyString(), anyString(), any()))
        .thenReturn(requestCall);
  }

  @NotNull
  private Call<NewRelicApplicationInstancesResponse> getMockNewRelicApplicationInstancesResponseCall(
      List<NewRelicApplicationInstance> applicationInstanceList) throws IOException {
    Call<NewRelicApplicationInstancesResponse> requestCall = mock(Call.class);
    Request request = new Request.Builder().url(config.getNewRelicUrl()).build();
    when(requestCall.request()).thenReturn(request);
    NewRelicApplicationInstancesResponse newRelicApplicationInstancesResponse =
        NewRelicApplicationInstancesResponse.builder().application_instances(applicationInstanceList).build();
    when(requestCall.execute()).thenReturn(Response.success(newRelicApplicationInstancesResponse));
    return requestCall;
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testInitNewRelicServiceWhenNoTxsToCollect() {
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfo = createDataCollectionInfo();
    doReturn(newRelicRestClient).when(newRelicDataCollector).getNewRelicRestClient();
    newRelicDataCollector.init(dataCollectionExecutionContext, newRelicDataCollectionInfo);
    assertThat(newRelicDataCollector.instances.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testInitNewRelicServiceWhenWebTxsArePresent() throws IOException {
    String txs = "WebTransactionTotalTime/JSP/index.jsp";
    mockListMetricNames(txs);
    mockGetInstanceMetricData();
    mockGetApplicationMetricData();
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfo = createDataCollectionInfo();
    doReturn(newRelicRestClient).when(newRelicDataCollector).getNewRelicRestClient();
    newRelicDataCollector.init(dataCollectionExecutionContext, newRelicDataCollectionInfo);
    assertThat(newRelicDataCollector.instances.size()).isEqualTo(1);
    assertThat(newRelicDataCollector.transactionsToCollect)
        .isEqualTo(Sets.newSet(NewRelicMetric.builder().name(txs).build()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetricNewRelicServiceWhenWebTxsArePresent() throws IOException {
    mockListMetricNames("WebTransactionTotalTime/JSP/index.jsp");
    mockGetInstanceMetricData();
    mockGetApplicationMetricData();
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfo = createDataCollectionInfo();
    doReturn(newRelicRestClient).when(newRelicDataCollector).getNewRelicRestClient();
    newRelicDataCollector.init(dataCollectionExecutionContext, newRelicDataCollectionInfo);
    List<MetricElement> metricElements = newRelicDataCollector.fetchMetrics(Lists.list(host));
    assertThat(metricElements).isEqualTo(expectedMetricElementsWithHost());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetricWithHostFilterWhenSpecialCharInMetricName() throws IOException {
    mockListMetricNames("WebTransaction/JSP/login{}.jsp/");
    mockGetInstanceMetricData();
    mockGetApplicationMetricData();
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfo = createDataCollectionInfo();
    doReturn(newRelicRestClient).when(newRelicDataCollector).getNewRelicRestClient();
    newRelicDataCollector.init(dataCollectionExecutionContext, newRelicDataCollectionInfo);
    doThrow(new DataCollectionException("Exception from test"))
        .when(dataCollectionExecutionContext)
        .executeRequest(any(), any());
    List<MetricElement> metricElements = newRelicDataCollector.fetchMetrics(Lists.list(host));
    assertThat(metricElements).isEqualTo(Lists.emptyList());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetricWithoutHostWhenSpecialCharInMetricName() throws IOException {
    mockListMetricNames("WebTransaction/JSP/login{}.jsp/");
    mockGetInstanceMetricData();
    mockGetApplicationMetricData();
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfo = createDataCollectionInfo();
    doReturn(newRelicRestClient).when(newRelicDataCollector).getNewRelicRestClient();
    newRelicDataCollector.init(dataCollectionExecutionContext, newRelicDataCollectionInfo);
    doThrow(new DataCollectionException("Exception from test"))
        .when(dataCollectionExecutionContext)
        .executeRequest(any(), any());
    List<MetricElement> metricElements = newRelicDataCollector.fetchMetrics();
    assertThat(metricElements).isEqualTo(Lists.emptyList());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetricNewRelicServiceWhenHostMetricAreNotAvailable() throws IOException {
    mockListMetricNames("WebTransaction/JSP/login.jsp/");
    mockGetInstanceMetricData();
    mockGetApplicationMetricData();
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfo = createDataCollectionInfo();
    doReturn(newRelicRestClient).when(newRelicDataCollector).getNewRelicRestClient();
    newRelicDataCollector.init(dataCollectionExecutionContext, newRelicDataCollectionInfo);
    List<MetricElement> metricElements = newRelicDataCollector.fetchMetrics(Lists.list("host-with-no-metrics"));
    assertThat(metricElements).isEqualTo(Lists.emptyList());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFetchMetricNewRelicServiceWithoutHostFilters() throws IOException {
    mockListMetricNames("WebTransactionTotalTime/JSP/index.jsp");
    mockGetApplicationMetricData();
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfo = createDataCollectionInfo();
    doReturn(newRelicRestClient).when(newRelicDataCollector).getNewRelicRestClient();
    newRelicDataCollector.init(dataCollectionExecutionContext, newRelicDataCollectionInfo);
    List<MetricElement> metricElements = newRelicDataCollector.fetchMetrics();
    assertThat(metricElements).isEqualTo(expectedMetricElementsWithoutHost());
  }
  private NewRelicMetricData getNewRelicMetricData() {
    return JsonUtils.asObject(getNewRelicMetricDataJson(), NewRelicMetricData.class);
  }

  private List<MetricElement> expectedMetricElementsWithHost() {
    @Language("JSON")
    String json = "[{\n"
        + "  \"name\" : \"WebTransactionTotalTime/JSP/index.jsp\",\n"
        + "  \"host\" : \"test-host\",\n"
        + "  \"groupName\" : \"default\",\n"
        + "  \"timestamp\" : 1568715720000,\n"
        + "  \"values\" : {\n"
        + "    \"averageResponseTime\" : 5,\n"
        + "    \"callCount\" : 5,\n"
        + "    \"requestsPerMinute\" : 10,\n"
        + "    \"error\" : 0\n"
        + "  }\n"
        + "},\n"
        + " {\n"
        + "   \"name\" : \"WebTransactionTotalTime/JSP/jsp/jsp2/el/basic_002dcomparisons.jsp\",\n"
        + "   \"host\" : \"test-host\",\n"
        + "   \"groupName\" : \"default\",\n"
        + "   \"timestamp\" : 1568715720000,\n"
        + "   \"values\" : {\n"
        + "     \"averageResponseTime\" : 0,\n"
        + "     \"callCount\" : 3,\n"
        + "     \"requestsPerMinute\" : 0,\n"
        + "     \"error\" : 0\n"
        + "   }\n"
        + " },\n"
        + " {\n"
        + "   \"name\" : \"WebTransactionTotalTime/JSP/jsp/jsptoserv/jsptoservlet.jsp\",\n"
        + "   \"host\" : \"test-host\",\n"
        + "   \"groupName\" : \"default\",\n"
        + "   \"timestamp\" : 1568715720000,\n"
        + "   \"values\" : {\n"
        + "     \"averageResponseTime\" : 0,\n"
        + "     \"callCount\" : 2,\n"
        + "     \"requestsPerMinute\" : 0,\n"
        + "     \"error\" : 0\n"
        + "   }\n"
        + " }]";
    return JsonUtils.asObject(json, List.class, MetricElement.class);
  }

  private List<MetricElement> expectedMetricElementsWithoutHost() {
    @Language("JSON")
    String json = "[{\n"
        + "  \"name\" : \"WebTransactionTotalTime/JSP/index.jsp\",\n"
        + "  \"host\" : \"default\",\n"
        + "  \"groupName\" : \"default\",\n"
        + "  \"timestamp\" : 1568715720000,\n"
        + "  \"values\" : {\n"
        + "    \"averageResponseTime\" : 5,\n"
        + "    \"callCount\" : 5,\n"
        + "    \"requestsPerMinute\" : 10,\n"
        + "    \"error\" : 0\n"
        + "  }\n"
        + "},\n"
        + " {\n"
        + "   \"name\" : \"WebTransactionTotalTime/JSP/jsp/jsp2/el/basic_002dcomparisons.jsp\",\n"
        + "   \"host\" : \"default\",\n"
        + "   \"groupName\" : \"default\",\n"
        + "   \"timestamp\" : 1568715720000,\n"
        + "   \"values\" : {\n"
        + "     \"averageResponseTime\" : 0,\n"
        + "     \"callCount\" : 3,\n"
        + "     \"requestsPerMinute\" : 0,\n"
        + "     \"error\" : 0\n"
        + "   }\n"
        + " },\n"
        + " {\n"
        + "   \"name\" : \"WebTransactionTotalTime/JSP/jsp/jsptoserv/jsptoservlet.jsp\",\n"
        + "   \"host\" : \"default\",\n"
        + "   \"groupName\" : \"default\",\n"
        + "   \"timestamp\" : 1568715720000,\n"
        + "   \"values\" : {\n"
        + "     \"averageResponseTime\" : 0,\n"
        + "     \"callCount\" : 2,\n"
        + "     \"requestsPerMinute\" : 0,\n"
        + "     \"error\" : 0\n"
        + "   }\n"
        + " }]";
    return JsonUtils.asObject(json, List.class, MetricElement.class);
  }

  private String getNewRelicMetricDataJson() {
    @Language("JSON")
    String metricDataRes = "{\n"
        + "  \"from\": \"2019-09-17T10:24:00+00:00\",\n"
        + "  \"to\": \"2019-09-17T10:25:00+00:00\",\n"
        + "  \"metrics_not_found\": [],\n"
        + "  \"metrics_found\": [\n"
        + "    \"WebTransactionTotalTime/JSP/jsp/jsp2/el/basic_002dcomparisons.jsp\",\n"
        + "    \"WebTransactionTotalTime/JSP/jsp/jsptoserv/jsptoservlet.jsp\",\n"
        + "    \"WebTransactionTotalTime/JSP/index.jsp\",\n"
        + "    \"WebTransaction/JSP/jsp/forward/forward.jsp\",\n"
        + "    \"WebTransactionTotalTime/JSP/inside/display.jsp\"\n"
        + "  ],\n"
        + "  \"metrics\": [\n"
        + "    {\n"
        + "      \"name\": \"WebTransactionTotalTime/JSP/index.jsp\",\n"
        + "      \"timeslices\": [\n"
        + "        {\n"
        + "          \"from\": \"2019-09-17T10:22:00+00:00\",\n"
        + "          \"to\": \"2019-09-17T10:23:00+00:00\",\n"
        + "          \"values\": {\n"
        + "            \"average_response_time\": 5,\n"
        + "            \"calls_per_minute\": 0,\n"
        + "            \"call_count\": 5,\n"
        + "            \"min_response_time\": 1,\n"
        + "            \"max_response_time\": 10,\n"
        + "            \"average_exclusive_time\": 0,\n"
        + "            \"average_value\": 0,\n"
        + "            \"total_call_time_per_minute\": 10,\n"
        + "            \"requests_per_minute\": 10,\n"
        + "            \"standard_deviation\": 0\n"
        + "          }\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    {\n"
        + "      \"name\": \"WebTransactionTotalTime/JSP/jsp/jsptoserv/jsptoservlet.jsp\",\n"
        + "      \"timeslices\": [\n"
        + "        {\n"
        + "          \"from\": \"2019-09-17T10:22:00+00:00\",\n"
        + "          \"to\": \"2019-09-17T10:23:00+00:00\",\n"
        + "          \"values\": {\n"
        + "            \"average_response_time\": 0,\n"
        + "            \"calls_per_minute\": 0,\n"
        + "            \"call_count\": 2,\n"
        + "            \"min_response_time\": 0,\n"
        + "            \"max_response_time\": 0,\n"
        + "            \"average_exclusive_time\": 0,\n"
        + "            \"average_value\": 0,\n"
        + "            \"total_call_time_per_minute\": 0,\n"
        + "            \"requests_per_minute\": 0,\n"
        + "            \"standard_deviation\": 0\n"
        + "          }\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    {\n"
        + "      \"name\": \"WebTransactionTotalTime/JSP/jsp/jsp2/el/basic_002dcomparisons.jsp\",\n"
        + "      \"timeslices\": [\n"
        + "        {\n"
        + "          \"from\": \"2019-09-17T10:22:00+00:00\",\n"
        + "          \"to\": \"2019-09-17T10:23:00+00:00\",\n"
        + "          \"values\": {\n"
        + "            \"average_response_time\": 0,\n"
        + "            \"calls_per_minute\": 0,\n"
        + "            \"call_count\": 3,\n"
        + "            \"min_response_time\": 0,\n"
        + "            \"max_response_time\": 0,\n"
        + "            \"average_exclusive_time\": 0,\n"
        + "            \"average_value\": 0,\n"
        + "            \"total_call_time_per_minute\": 0,\n"
        + "            \"requests_per_minute\": 0,\n"
        + "            \"standard_deviation\": 0\n"
        + "          }\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    {\n"
        + "      \"name\": \"WebTransaction/JSP/jsp/forward/forward.jsp\",\n"
        + "      \"timeslices\": [\n"
        + "        {\n"
        + "          \"from\": \"2019-09-17T10:22:00+00:00\",\n"
        + "          \"to\": \"2019-09-17T10:23:00+00:00\",\n"
        + "          \"values\": {\n"
        + "            \"average_call_time\": 0,\n"
        + "            \"average_response_time\": 0,\n"
        + "            \"requests_per_minute\": 0,\n"
        + "            \"call_count\": 0,\n"
        + "            \"min_call_time\": 0,\n"
        + "            \"max_call_time\": 0,\n"
        + "            \"total_call_time\": 0,\n"
        + "            \"throughput\": 0,\n"
        + "            \"standard_deviation\": 0\n"
        + "          }\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    {\n"
        + "      \"name\": \"WebTransaction/JSP/inside/display.jsp\",\n"
        + "      \"timeslices\": [\n"
        + "        {\n"
        + "          \"from\": \"2019-09-17T10:22:00+00:00\",\n"
        + "          \"to\": \"2019-09-17T10:23:00+00:00\",\n"
        + "          \"values\": {\n"
        + "            \"average_call_time\": 0,\n"
        + "            \"average_response_time\": 0,\n"
        + "            \"requests_per_minute\": 0,\n"
        + "            \"call_count\": 0,\n"
        + "            \"min_call_time\": 0,\n"
        + "            \"max_call_time\": 0,\n"
        + "            \"total_call_time\": 0,\n"
        + "            \"throughput\": 0,\n"
        + "            \"standard_deviation\": 0\n"
        + "          }\n"
        + "        }\n"
        + "      ]\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    return metricDataRes;
  }

  private NewRelicDataCollectionInfoV2 createDataCollectionInfo() {
    return NewRelicDataCollectionInfoV2.builder()
        .startTime(Instant.now())
        .endTime(Instant.now())
        .newRelicConfig(config)
        .hostsToGroupNameMap(Collections.singletonMap(host, "default"))
        .hosts(new HashSet<>(Arrays.asList(host)))
        .newRelicAppId(random.nextLong())
        .build();
  }
}
