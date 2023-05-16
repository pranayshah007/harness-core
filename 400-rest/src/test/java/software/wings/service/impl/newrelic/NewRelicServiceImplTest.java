/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.SettingAttribute;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.metrics.MetricType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;
import software.wings.service.intfc.newrelic.NewRelicService;
import software.wings.sm.states.NewRelicState;
import software.wings.sm.states.NewRelicState.Metric;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NewRelicServiceImplTest extends WingsBaseTest {
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private NewRelicDelegateService newRelicDelegateService;
  @Mock private SettingsService settingsService;

  @Inject private NewRelicService newRelicService;

  private SettingAttribute settingAttribute;
  private String settingId;
  private Long applicationId;
  private Metric requestsPerMinuteMetric;
  private Metric averageResponseTimeMetric;
  private Metric errorMetric;
  private Metric apdexScoreMetric;
  private List<Metric> expectedMetrics;

  private String connectorName = "new_relic";
  private String applicationName = "app_name";

  @Before
  public void setUp() throws IllegalAccessException {
    settingId = generateUuid();
    applicationId = 8L;

    requestsPerMinuteMetric = NewRelicState.Metric.builder()
                                  .metricName(NewRelicMetricValueDefinition.REQUSET_PER_MINUTE)
                                  .mlMetricType(MetricType.THROUGHPUT)
                                  .displayName("Requests per Minute")
                                  .build();
    averageResponseTimeMetric = NewRelicState.Metric.builder()
                                    .metricName(NewRelicMetricValueDefinition.AVERAGE_RESPONSE_TIME)
                                    .mlMetricType(MetricType.RESP_TIME)
                                    .displayName("Response Time")
                                    .build();
    errorMetric = NewRelicState.Metric.builder()
                      .metricName(NewRelicMetricValueDefinition.ERROR)
                      .mlMetricType(MetricType.ERROR)
                      .displayName("ERROR")
                      .build();
    apdexScoreMetric = NewRelicState.Metric.builder()
                           .metricName(NewRelicMetricValueDefinition.APDEX_SCORE)
                           .mlMetricType(MetricType.APDEX)
                           .displayName("Apdex Score")
                           .build();

    expectedMetrics = Arrays.asList(requestsPerMinuteMetric, averageResponseTimeMetric, errorMetric, apdexScoreMetric);

    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(newRelicService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(newRelicService, "settingsService", settingsService, true);

    NewRelicConfig newRelicConfig = NewRelicConfig.builder().newRelicUrl("url").build();

    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withName(connectorName)
                                            .withUuid(settingId)
                                            .withValue(newRelicConfig)
                                            .build();

    when(settingsService.get(settingId)).thenReturn(settingAttribute);
    when(delegateProxyFactory.getV2(any(), any())).thenReturn(newRelicDelegateService);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testResolveApplicationName_correctAppName() throws IOException, CloneNotSupportedException {
    when(newRelicDelegateService.resolveNewRelicApplicationName(any(), any(), any(), any()))
        .thenReturn(NewRelicApplication.builder().id(applicationId).name(applicationName).build());
    NewRelicApplication newRelicApplication = newRelicService.resolveApplicationName(settingId, applicationName);
    assertThat(newRelicApplication).isNotNull();
    assertThat(newRelicApplication.getId()).isEqualTo(applicationId);
    assertThat(newRelicApplication.getName()).isEqualTo(applicationName);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testResolveApplicationName_delegateThrowsException() throws IOException, CloneNotSupportedException {
    when(newRelicDelegateService.resolveNewRelicApplicationName(any(), any(), any(), any()))
        .thenThrow(new DataCollectionException("Application name not found"));
    assertThatThrownBy(() -> newRelicService.resolveApplicationName(settingId, applicationName))
        .isInstanceOf(DataCollectionException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testResolveApplicationId_correctArguments() throws IOException, CloneNotSupportedException {
    when(newRelicDelegateService.resolveNewRelicApplicationId(any(), any(), any(), any()))
        .thenReturn(NewRelicApplication.builder().id(applicationId).name(applicationName).build());
    NewRelicApplication newRelicApplication = newRelicService.resolveApplicationId(settingId, applicationId.toString());
    assertThat(newRelicApplication).isNotNull();
    assertThat(newRelicApplication.getId()).isEqualTo(applicationId);
    assertThat(newRelicApplication.getName()).isEqualTo(applicationName);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testResolveApplicationId_delegateThrowsException() throws IOException, CloneNotSupportedException {
    when(newRelicDelegateService.resolveNewRelicApplicationId(any(), any(), any(), any()))
        .thenThrow(new DataCollectionException("Application ID not found"));
    assertThatThrownBy(() -> newRelicService.resolveApplicationId(settingId, applicationId.toString()))
        .isInstanceOf(DataCollectionException.class);
  }

  private TimeSeriesMetricDefinition buildTimeSeriesMetricDefinition(Metric metric) {
    return TimeSeriesMetricDefinition.builder()
        .metricName(metric.getMetricName())
        .metricType(metric.getMlMetricType())
        .tags(metric.getTags())
        .build();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsCorrespondingToMetricNames() {
    Map<String, TimeSeriesMetricDefinition> expectedMetricDefinitions = new HashMap<>();
    expectedMetricDefinitions.put(
        requestsPerMinuteMetric.getMetricName(), buildTimeSeriesMetricDefinition(requestsPerMinuteMetric));
    expectedMetricDefinitions.put(
        averageResponseTimeMetric.getMetricName(), buildTimeSeriesMetricDefinition(averageResponseTimeMetric));
    expectedMetricDefinitions.put(errorMetric.getMetricName(), buildTimeSeriesMetricDefinition(errorMetric));
    expectedMetricDefinitions.put(apdexScoreMetric.getMetricName(), buildTimeSeriesMetricDefinition(apdexScoreMetric));

    List<String> metricNames = Arrays.asList("requestsPerMinute", "averageResponseTime", "error", "apdexScore");
    Map<String, NewRelicState.Metric> metrics = newRelicService.getMetricsCorrespondingToMetricNames(metricNames);
    Map<String, TimeSeriesMetricDefinition> actualMetricDefinitions =
        newRelicService.metricDefinitions(metrics.values());

    assertThat(actualMetricDefinitions.get("requestsPerMinute"))
        .isEqualTo(expectedMetricDefinitions.get("requestsPerMinute"));
    assertThat(actualMetricDefinitions.get("averageResponseTime"))
        .isEqualTo(expectedMetricDefinitions.get("averageResponseTime"));
    assertThat(actualMetricDefinitions.get("error")).isEqualTo(expectedMetricDefinitions.get("error"));
    assertThat(actualMetricDefinitions.get("apdexScore")).isEqualTo(expectedMetricDefinitions.get("apdexScore"));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetListOfMetrics() {
    List<NewRelicState.Metric> actualMetrics = newRelicService.getListOfMetrics();
    assertThat(actualMetrics).isEqualTo(expectedMetrics);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsCorrespondingToMetricNames_emptyList() {
    List<String> metricNames = new ArrayList<>();
    Map<String, Metric> metrics = newRelicService.getMetricsCorrespondingToMetricNames(metricNames);
    assertThat(metrics.containsKey("requestsPerMinute")).isTrue();
    assertThat(metrics.containsKey("averageResponseTime")).isTrue();
    assertThat(metrics.containsKey("error")).isTrue();
    assertThat(metrics.containsKey("apdexScore")).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsCorrespondingToMetricNames_null() {
    List<String> metricNames = null;
    Map<String, Metric> metrics = newRelicService.getMetricsCorrespondingToMetricNames(metricNames);
    assertThat(metrics.containsKey("requestsPerMinute")).isTrue();
    assertThat(metrics.containsKey("averageResponseTime")).isTrue();
    assertThat(metrics.containsKey("error")).isTrue();
    assertThat(metrics.containsKey("apdexScore")).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsCorrespondingToMetricNames_nonEmptyList() {
    List<String> metricNames = Collections.singletonList("apdexScore");
    Map<String, Metric> metrics = newRelicService.getMetricsCorrespondingToMetricNames(metricNames);
    assertThat(metrics.containsKey("apdexScore")).isTrue();
    assertThat(metrics).hasSize(1);
    assertThat(metrics.get("apdexScore").getTags().size() >= 1).isTrue();
    assertThat(metrics.get("apdexScore").getTags()).isEqualTo(Sets.newHashSet("WebTransactions"));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsCorrespondingToMetricNames_incorrectMetricsInList() {
    List<String> metricNames = Arrays.asList("apdexScore", "averageResponseTime", "requestsPerMinute");
    Map<String, Metric> metrics = newRelicService.getMetricsCorrespondingToMetricNames(metricNames);
    assertThat(metrics.containsKey("apdexScore")).isTrue();
    assertThat(metrics.containsKey("averageResponseTime")).isTrue();
    assertThat(metrics.containsKey("requestsPerMinute")).isTrue();
    assertThat(metrics).hasSize(3);
  }
}
