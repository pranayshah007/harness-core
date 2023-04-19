/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANJAN;
import static io.harness.rule.OwnerRule.KAMAL;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.PrometheusConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.metrics.MetricType;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.TimeSeries;
import software.wings.service.impl.apm.APMDataCollectionInfo;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.prometheus.PrometheusAnalysisService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PrometheusStateTest extends APMStateVerificationTestBase {
  @InjectMocks private PrometheusState prometheusState;
  @Inject protected SecretManager secretManager;
  @Inject private PrometheusAnalysisService prometheusAnalysisService;
  @Mock SettingsService settingsService;
  @Mock MetricDataAnalysisService metricAnalysisService;
  @Mock AppService appService;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock DelegateService delegateService;
  @Mock AccountService accountService;
  @Mock FeatureFlagService featureFlagService;

  @Before
  public void setup() throws Exception {
    setupCommon();
    MockitoAnnotations.initMocks(this);
    setupCommonMocks();
    FieldUtils.writeField(prometheusState, "prometheusAnalysisService", prometheusAnalysisService, true);
    FieldUtils.writeField(prometheusState, "secretManager", secretManager, true);
    FieldUtils.writeField(prometheusState, "featureFlagService", featureFlagService, true);
    when(featureFlagService.isEnabled(FeatureName.CV_AWS_PROMETHEUS, accountId)).thenReturn(false);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testDefaultComparisionStrategy() {
    assertThat(prometheusState.getComparisonStrategy()).isEqualTo(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testRenderURLExpression() throws IllegalAccessException {
    AnalysisContext analysisContext = mock(AnalysisContext.class);
    VerificationStateAnalysisExecutionData executionData = mock(VerificationStateAnalysisExecutionData.class);
    Map<String, String> hosts = new HashMap<>();
    hosts.put("prometheus.host", "default");

    when(settingsService.get(any()))
        .thenReturn(aSettingAttribute().withValue(PrometheusConfig.builder().build()).build());
    when(appService.get(anyString())).thenReturn(application);

    String renderedUrl =
        "/api/v1/query_range?start=${start_time_seconds}&end=${end_time_seconds}&step=60s&query=jvm_memory_max_bytes{pod_name=\"${host}\"}";
    String testUrl = "jvm_memory_max_bytes{pod_name=\"$hostName\"}";
    List<TimeSeries> timeSeriesToAnalyze = new ArrayList<>();
    TimeSeries timeSeries =
        TimeSeries.builder().metricName("testMetric").url(testUrl).metricType(MetricType.INFRA.name()).build();
    timeSeriesToAnalyze.add(timeSeries);
    FieldUtils.writeField(prometheusState, "timeSeriesToAnalyze", timeSeriesToAnalyze, true);

    when(executionContext.renderExpression(testUrl)).thenReturn(renderedUrl);

    prometheusState.triggerAnalysisDataCollection(executionContext, analysisContext, executionData, hosts);
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(argument.capture());
    TaskData taskData = argument.getValue().getData();
    Object parameters[] = taskData.getParameters();
    assertThat(1).isEqualTo(parameters.length);
    assertThat(TaskType.APM_METRIC_DATA_COLLECTION_TASK.name()).isEqualTo(taskData.getTaskType());
    APMDataCollectionInfo prometheusDataCollectionInfo = (APMDataCollectionInfo) parameters[0];
    assertThat(prometheusDataCollectionInfo.getMetricEndpoints()).containsKey(renderedUrl);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_renderConnectorId() throws IllegalAccessException {
    AnalysisContext analysisContext = mock(AnalysisContext.class);
    VerificationStateAnalysisExecutionData executionData = mock(VerificationStateAnalysisExecutionData.class);
    Map<String, String> hosts = new HashMap<>();
    hosts.put("prometheus.host", "default");
    String resolvedAnalysisServerConfigId = generateUuid();

    PrometheusConfig prometheusConfig = PrometheusConfig.builder().url(generateUuid()).build();
    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withUuid(resolvedAnalysisServerConfigId)
                                            .withValue(prometheusConfig)
                                            .withName("prometheus")
                                            .build();
    wingsPersistence.save(settingAttribute);

    when(settingsService.get(eq(resolvedAnalysisServerConfigId))).thenReturn(settingAttribute);
    when(appService.get(anyString())).thenReturn(application);
    String analysisServerConfigId = "${workflow.variables.connectorName}";
    prometheusState.setAnalysisServerConfigId(analysisServerConfigId);
    List<TimeSeries> timeSeriesToAnalyze = new ArrayList<>();
    String testUrl = "jvm_memory_max_bytes{pod_name=\"$hostName\"}";
    TimeSeries timeSeries =
        TimeSeries.builder().metricName("testMetric").url(testUrl).metricType(MetricType.INFRA.name()).build();
    timeSeriesToAnalyze.add(timeSeries);
    FieldUtils.writeField(prometheusState, "timeSeriesToAnalyze", timeSeriesToAnalyze, true);

    PrometheusState spyState = spy(prometheusState);
    when(spyState.getResolvedConnectorId(any(), eq("analysisServerConfigId"), eq(analysisServerConfigId)))
        .thenReturn(resolvedAnalysisServerConfigId);

    spyState.triggerAnalysisDataCollection(executionContext, analysisContext, executionData, hosts);
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(argument.capture());
    TaskData taskData = argument.getValue().getData();
    Object parameters[] = taskData.getParameters();
    assertThat(1).isEqualTo(parameters.length);
    assertThat(TaskType.APM_METRIC_DATA_COLLECTION_TASK.name()).isEqualTo(taskData.getTaskType());
    APMDataCollectionInfo prometheusDataCollectionInfo = (APMDataCollectionInfo) parameters[0];
    assertThat(prometheusDataCollectionInfo.getBaseUrl()).isEqualTo(prometheusConfig.getUrl());
    assertThat(prometheusDataCollectionInfo.isBase64EncodingRequired()).isFalse();
    assertThat(prometheusDataCollectionInfo.getHeaders()).isEqualTo(Collections.emptyMap());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_forBaseEncodedHeader() throws IllegalAccessException {
    AnalysisContext analysisContext = mock(AnalysisContext.class);
    VerificationStateAnalysisExecutionData executionData = mock(VerificationStateAnalysisExecutionData.class);
    Map<String, String> hosts = new HashMap<>();
    hosts.put("prometheus.host", "default");
    String resolvedAnalysisServerConfigId = generateUuid();

    PrometheusConfig prometheusConfig = PrometheusConfig.builder()
                                            .accountId(accountId)
                                            .username("user")
                                            .password(new char[] {'p', 'a', 's', 's'})
                                            .url(generateUuid())
                                            .build();

    prometheusConfig.setUsername("user");

    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withUuid(resolvedAnalysisServerConfigId)
                                            .withValue(prometheusConfig)
                                            .withName("prometheus")
                                            .build();
    wingsPersistence.save(settingAttribute);

    when(settingsService.get(eq(resolvedAnalysisServerConfigId))).thenReturn(settingAttribute);
    when(appService.get(anyString())).thenReturn(application);
    String analysisServerConfigId = "${workflow.variables.connectorName}";
    prometheusState.setAnalysisServerConfigId(analysisServerConfigId);
    List<TimeSeries> timeSeriesToAnalyze = new ArrayList<>();
    String testUrl = "jvm_memory_max_bytes{pod_name=\"$hostName\"}";
    TimeSeries timeSeries =
        TimeSeries.builder().metricName("testMetric").url(testUrl).metricType(MetricType.INFRA.name()).build();
    timeSeriesToAnalyze.add(timeSeries);
    FieldUtils.writeField(prometheusState, "timeSeriesToAnalyze", timeSeriesToAnalyze, true);

    PrometheusState spyState = spy(prometheusState);
    when(spyState.getResolvedConnectorId(any(), eq("analysisServerConfigId"), eq(analysisServerConfigId)))
        .thenReturn(resolvedAnalysisServerConfigId);

    spyState.triggerAnalysisDataCollection(executionContext, analysisContext, executionData, hosts);
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(argument.capture());
    TaskData taskData = argument.getValue().getData();
    Object parameters[] = taskData.getParameters();
    assertThat(1).isEqualTo(parameters.length);
    assertThat(TaskType.APM_METRIC_DATA_COLLECTION_TASK.name()).isEqualTo(taskData.getTaskType());
    APMDataCollectionInfo prometheusDataCollectionInfo = (APMDataCollectionInfo) parameters[0];
    assertThat(prometheusDataCollectionInfo.getBaseUrl()).isEqualTo(prometheusConfig.getUrl());

    HashMap<String, String> headersMap = new HashMap<>();
    headersMap.put("Authorization", "Basic encodeWithBase64(user:${password})");
    assertThat(prometheusDataCollectionInfo.isBase64EncodingRequired()).isTrue();
    assertThat(prometheusDataCollectionInfo.getHeaders()).isEqualTo(headersMap);
  }
}
