/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.Application;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.stackdriver.StackDriverDataCollectionInfo;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.stackdriver.StackDriverService;
import software.wings.verification.VerificationStateAnalysisExecutionData;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class StackDriverStateTest extends APMStateVerificationTestBase {
  @Mock private MetricDataAnalysisService metricAnalysisService;
  @Mock private DelegateService delegateService;
  @Mock private SettingsService settingsService;
  @Mock private StackDriverService stackDriverService;
  @Mock private SecretManager secretManager;
  @Mock private AppService appService;
  @Mock private WaitNotifyEngine waitNotifyEngine;

  private StackDriverState stackDriverState;
  private AnalysisContext analysisContext;
  private VerificationStateAnalysisExecutionData executionData;
  private Map<String, String> hosts;
  private String configId;
  private SettingAttribute settingAttribute;
  private GcpConfig gcpConfig;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    setupCommon();
    String paramsForStackDriver =
        Resources.toString(StackDriverStateTest.class.getResource("/apm/stackdriverpayload.json"), Charsets.UTF_8);
    MockitoAnnotations.initMocks(this);

    analysisContext = AnalysisContext.builder().build();
    executionData = VerificationStateAnalysisExecutionData.builder().build();
    hosts = new HashMap<>();
    configId = generateUuid();

    stackDriverState = Mockito.spy(new StackDriverState("stackDriverState"));
    stackDriverState.setTimeDuration("10");
    stackDriverState.setMetricDefinitions(Collections.singletonList(StackDriverMetricDefinition.builder()
                                                                        .metricName("CPU")
                                                                        .metricType("INFRA")
                                                                        .txnName("txn")
                                                                        .filterJson(paramsForStackDriver)
                                                                        .build()));
    gcpConfig = GcpConfig.builder().accountId(accountId).build();
    settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                           .withAccountId(accountId)
                           .withAppId(appId)
                           .withValue(gcpConfig)
                           .build();

    Application app = new Application();
    app.setName("name");

    FieldUtils.writeField(stackDriverState, "metricAnalysisService", metricAnalysisService, true);
    FieldUtils.writeField(stackDriverState, "delegateService", delegateService, true);
    FieldUtils.writeField(stackDriverState, "settingsService", settingsService, true);
    FieldUtils.writeField(stackDriverState, "stackDriverService", stackDriverService, true);
    FieldUtils.writeField(stackDriverState, "secretManager", secretManager, true);
    FieldUtils.writeField(stackDriverState, "appService", appService, true);
    FieldUtils.writeField(stackDriverState, "waitNotifyEngine", waitNotifyEngine, true);

    when(settingsService.get(configId)).thenReturn(settingAttribute);
    when(appService.get(any())).thenReturn(app);

    doReturn(configId).when(stackDriverState).getResolvedConnectorId(any(), any(), any());
    doReturn(workflowId).when(stackDriverState).getWorkflowId(any());
    doReturn(serviceId).when(stackDriverState).getPhaseServiceId(any());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsValid() {
    stackDriverState.triggerAnalysisDataCollection(executionContext, analysisContext, executionData, hosts);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(delegateTaskArgumentCaptor.capture());

    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    StackDriverDataCollectionInfo dataCollectionInfo =
        (StackDriverDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(dataCollectionInfo.getGcpConfig()).isEqualTo(gcpConfig);
    assertThat(dataCollectionInfo.getTimeSeriesToCollect().size()).isEqualTo(1);
    assertThat(dataCollectionInfo.getTimeSeriesToCollect().get(0).getMetricType()).isEqualTo("INFRA");
    assertThat(dataCollectionInfo.getTimeSeriesToCollect().get(0).getMetricName()).isEqualTo("CPU");
    assertThat(dataCollectionInfo.getTimeSeriesToCollect().get(0).getTxnName()).isEqualTo("txn");
    assertThat(dataCollectionInfo.getTimeSeriesToCollect().get(0).getFilter())
        .isEqualTo(
            "metric.type=\"kubernetes.io/container/restart_count\" resource.type=\"k8s_container\" resource.label.\"cluster_name\"=\"harness-test\"");
    assertThat(dataCollectionInfo.getTimeSeriesToCollect().get(0).getAggregation().getGroupByFields().size())
        .isEqualTo(1);
    assertThat(dataCollectionInfo.getTimeSeriesToCollect().get(0).getAggregation().getGroupByFields().get(0))
        .isEqualTo("resource.label.\"cluster_name\"");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsInValid() {
    when(settingsService.get(configId)).thenReturn(null);
    assertThatThrownBy(
        () -> stackDriverState.triggerAnalysisDataCollection(executionContext, analysisContext, executionData, hosts))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("No Gcp config setting with id: " + configId + " found");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testFetchMetricDefinitions_withExpression() {
    StackDriverMetricDefinition definition = stackDriverState.getMetricDefinitions().get(0);
    String oldFilter = definition.getFilterJson();
    String filter = oldFilter.replace("cluster_name", "${env.name}");
    definition.setFilterJson(filter);
    definition.extractJson();

    doReturn(oldFilter).when(stackDriverState).getResolvedFieldValue(executionContext, "", filter);

    List<StackDriverMetricDefinition> definitions = stackDriverState.fetchMetricDefinitions(executionContext);

    assertThat(definitions.size()).isEqualTo(1);
    assertThat(definitions.get(0).getFilterJson()).isEqualTo(oldFilter);
  }
}
