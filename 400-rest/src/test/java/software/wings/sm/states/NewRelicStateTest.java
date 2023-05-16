/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.DeploymentType;
import software.wings.beans.AccountType;
import software.wings.beans.Environment;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WorkflowExecution;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.metrics.MetricType;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicMetricValueDefinition;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.newrelic.NewRelicService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.NewRelicState.Metric;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * author: Praveen
 */

public class NewRelicStateTest extends APMStateVerificationTestBase {
  private NewRelicState nrState;

  private NewRelicState.Metric requestsPerMinuteMetric, averageResponseTimeMetric, errorMetric, apdexScoreMetric;
  private List<Metric> expectedMetrics;

  @Mock private MetricDataAnalysisService metricAnalysisService;
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private NewRelicService newRelicService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private DelegateService delegateService;
  @Mock private SecretManager secretManager;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  private String infraMappingId;
  private NewRelicState newRelicState;

  @Before
  public void setup() throws Exception {
    nrState = new NewRelicState("nrStateName");
    setupCommon();
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(nrState, "metricAnalysisService", metricAnalysisService, true);
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
    infraMappingId = generateUuid();
    when(executionContext.getAccountId()).thenReturn(accountId);
    when(executionContext.getContextElement(ContextElementType.PARAM, AbstractAnalysisStateTestBase.PHASE_PARAM))
        .thenReturn(phaseElement);
    when(executionContext.fetchInfraMappingId()).thenReturn(infraMappingId);
    when(executionContext.getAppId()).thenReturn(appId);
    when(infraMappingService.get(anyString(), anyString()))
        .thenReturn(anAwsInfrastructureMapping().withDeploymentType(DeploymentType.KUBERNETES.name()).build());

    newRelicState = new NewRelicState("NewRelicState");
    newRelicState.setApplicationId("30444");
    newRelicState.setTimeDuration("10");

    AccountService accountService = mock(AccountService.class);
    when(accountService.getAccountType(anyString())).thenReturn(Optional.of(AccountType.PAID));

    FieldUtils.writeField(newRelicState, "appService", appService, true);
    FieldUtils.writeField(newRelicState, "configuration", configuration, true);
    FieldUtils.writeField(newRelicState, "settingsService", settingsService, true);
    FieldUtils.writeField(newRelicState, "waitNotifyEngine", waitNotifyEngine, true);
    FieldUtils.writeField(newRelicState, "delegateService", delegateService, true);
    FieldUtils.writeField(newRelicState, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(newRelicState, "secretManager", secretManager, true);
    FieldUtils.writeField(newRelicState, "metricAnalysisService", metricAnalysisService, true);
    FieldUtils.writeField(newRelicState, "templateExpressionProcessor", templateExpressionProcessor, true);
    FieldUtils.writeField(newRelicState, "workflowExecutionService", workflowExecutionService, true);
    FieldUtils.writeField(newRelicState, "continuousVerificationService", continuousVerificationService, true);
    FieldUtils.writeField(newRelicState, "workflowExecutionBaselineService", workflowExecutionBaselineService, true);
    FieldUtils.writeField(newRelicState, "newRelicService", newRelicService, true);
    FieldUtils.writeField(newRelicState, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(newRelicState, "infraMappingService", infraMappingService, true);
    FieldUtils.writeField(newRelicState, "versionInfoManager", versionInfoManager, true);
    FieldUtils.writeField(newRelicState, "appService", appService, true);
    FieldUtils.writeField(newRelicState, "accountService", accountService, true);
    FieldUtils.writeField(newRelicState, "cvActivityLogService", cvActivityLogService, true);
    FieldUtils.writeField(newRelicState, "workflowVerificationResultService", workflowVerificationResultService, true);
    FieldUtils.writeField(
        newRelicState, "workflowStandardParamsExtensionService", workflowStandardParamsExtensionService, true);
    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString()))
        .thenReturn(mock(CVActivityLogger.class));

    setupCommonMocks();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAnalysisType() {
    nrState.setComparisonStrategy("COMPARE_WITH_CURRENT");
    assertThat(nrState.getAnalysisType()).isEqualTo(TimeSeriesMlAnalysisType.COMPARATIVE);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisTypePredictive() {
    nrState.setComparisonStrategy("PREDICTIVE");
    assertThat(nrState.getAnalysisType()).isEqualTo(TimeSeriesMlAnalysisType.PREDICTIVE);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateGroup() {
    // setup
    Map<String, String> hosts = new HashMap<>();
    hosts.put("dummy", DEFAULT_GROUP_NAME);
    Map<String, TimeSeriesMlAnalysisGroupInfo> metricGroups = new HashMap<>();
    TimeSeriesMlAnalysisGroupInfo analysisGroupInfo = TimeSeriesMlAnalysisGroupInfo.builder()
                                                          .groupName(DEFAULT_GROUP_NAME)
                                                          .mlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
                                                          .build();
    metricGroups.put(DEFAULT_GROUP_NAME, analysisGroupInfo);
    doNothing()
        .when(metricAnalysisService)
        .saveMetricGroups(appId, StateType.NEW_RELIC, stateExecutionId, metricGroups);

    // execute

    nrState.setComparisonStrategy("COMPARE_WITH_CURRENT");
    nrState.createAndSaveMetricGroups(executionContext, hosts);

    // verify
    verify(metricAnalysisService).saveMetricGroups(appId, StateType.NEW_RELIC, stateExecutionId, metricGroups);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetMetricType() {
    String errType = NewRelicState.getMetricTypeForMetric(NewRelicMetricValueDefinition.ERROR);
    assertThat(errType).isNotNull();
    assertThat(errType).isEqualTo(MetricType.ERROR.name());
    String throughput = NewRelicState.getMetricTypeForMetric(NewRelicMetricValueDefinition.REQUSET_PER_MINUTE);
    assertThat(throughput).isNotNull();
    assertThat(throughput).isEqualTo(MetricType.THROUGHPUT.name());
    String respTime = NewRelicState.getMetricTypeForMetric(NewRelicMetricValueDefinition.AVERAGE_RESPONSE_TIME);
    assertThat(respTime).isNotNull();
    assertThat(respTime).isEqualTo(MetricType.RESP_TIME.name());

    String dummy = NewRelicState.getMetricTypeForMetric("incorrectName");
    assertThat(dummy).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldTestTriggered() throws IllegalAccessException {
    NewRelicConfig newRelicConfig = NewRelicConfig.builder()
                                        .accountId(accountId)
                                        .newRelicUrl("newrelic-url")
                                        .apiKey(generateUuid().toCharArray())
                                        .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("relic-config")
                                            .withValue(newRelicConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    newRelicState.setAnalysisServerConfigId(settingAttribute.getUuid());
    wingsPersistence.save(WorkflowExecution.builder()
                              .appId(appId)
                              .uuid(workflowExecutionId)
                              .triggeredBy(EmbeddedUser.builder().name("Deployment Trigger workflow").build())
                              .build());

    final NewRelicService mockNewRelicService = mock(NewRelicService.class);
    FieldUtils.writeField(newRelicState, "newRelicService", mockNewRelicService, true);
    doThrow(new WingsException("Can not find application by id"))
        .when(mockNewRelicService)
        .resolveApplicationId(anyString(), anyString(), anyString(), anyString());

    doThrow(new WingsException("Can not find application by name"))
        .when(mockNewRelicService)
        .resolveApplicationName(anyString(), anyString(), anyString(), anyString());

    NewRelicState spyNewRelicState = spy(newRelicState);
    doReturn(false).when(spyNewRelicState).isEligibleForPerMinuteTask(accountId);
    doReturn(false).when(spyNewRelicState).isDemoPath(any(AnalysisContext.class));
    doReturn(
        AbstractAnalysisState.CVInstanceApiResponse.builder().newNodesTrafficShiftPercent(Optional.empty()).build())
        .when(spyNewRelicState)
        .getCVInstanceAPIResponse(any());

    doReturn(asList(TemplateExpression.builder()
                        .fieldName("analysisServerConfigId")
                        .expression("${NewRelic_Server}")
                        .metadata(ImmutableMap.of("entityType", "NEWRELIC_CONFIGID"))
                        .build(),
                 TemplateExpression.builder()
                     .fieldName("applicationId")
                     .expression("${NewRelic_App}")
                     .metadata(ImmutableMap.of("entityType", "NEWRELIC_APPID"))
                     .build()))
        .when(spyNewRelicState)
        .getTemplateExpressions();

    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("control"))
                 .testNodes(Collections.singleton("test"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyNewRelicState)
        .getCVInstanceAPIResponse(any());
    doReturn(workflowId).when(spyNewRelicState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyNewRelicState).getPhaseServiceId(executionContext);

    when(metricAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
             DelegateStateType.NEW_RELIC, appId, workflowId, serviceId, infraMappingId, environment.getUuid()))
        .thenReturn(workflowExecutionId);
    when(executionContext.renderExpression("${workflow.variables.NewRelic_Server}"))
        .thenReturn(settingAttribute.getUuid());
    when(executionContext.renderExpression("${workflow.variables.NewRelic_App}")).thenReturn("30444");
    doReturn(Environment.Builder.anEnvironment().uuid(UUID.randomUUID().toString()).build())
        .when(workflowStandardParamsExtensionService)
        .getEnv(workflowStandardParams);
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    doReturn(false).when(spyNewRelicState).isCVTaskEnqueuingEnabled(anyString());
    doReturn(AnalysisTolerance.LOW).when(spyNewRelicState).getAnalysisTolerance();
    ExecutionResponse executionResponse = spyNewRelicState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ERROR);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("Can not find application by name");

    doReturn(NewRelicApplication.builder().build())
        .when(mockNewRelicService)
        .resolveApplicationId(anyString(), anyString(), anyString(), anyString());
    executionResponse = spyNewRelicState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(RUNNING);

    doThrow(new RuntimeException("Can not find application by id"))
        .when(mockNewRelicService)
        .resolveApplicationId(anyString(), anyString(), anyString(), anyString());

    executionResponse = spyNewRelicState.execute(executionContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ERROR);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("RuntimeException: Can not find application by id");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsValid() {
    NewRelicState spyState = spy(newRelicState);

    VerificationStateAnalysisExecutionData executionData = VerificationStateAnalysisExecutionData.builder().build();
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");

    String configId = generateUuid();
    NewRelicConfig newRelicConfig = NewRelicConfig.builder()
                                        .accountId(accountId)
                                        .newRelicUrl("newrelic-url")
                                        .apiKey(generateUuid().toCharArray())
                                        .build();

    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("relic-config")
                                            .withValue(newRelicConfig)
                                            .withUuid(configId)
                                            .build();
    wingsPersistence.save(settingAttribute);

    doReturn(configId).when(spyState).getResolvedConnectorId(any(), any(), any());
    spyState.triggerAnalysisDataCollection(executionContext, AnalysisContext.builder().build(), executionData, hosts);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(delegateTaskArgumentCaptor.capture());

    DelegateTask task = delegateTaskArgumentCaptor.getValue();
    NewRelicDataCollectionInfo dataCollectionInfo = (NewRelicDataCollectionInfo) task.getData().getParameters()[0];

    assertThat(dataCollectionInfo.getNewRelicConfig()).isEqualTo(newRelicConfig);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_whenConnectorIdIsInValid() {
    NewRelicState spyState = spy(newRelicState);
    String configId = generateUuid();
    doReturn(configId).when(spyState).getResolvedConnectorId(any(), any(), any());

    VerificationStateAnalysisExecutionData executionData = VerificationStateAnalysisExecutionData.builder().build();
    Map<String, String> hosts = new HashMap<>();
    hosts.put("host1", "default");

    assertThatThrownBy(()
                           -> spyState.triggerAnalysisDataCollection(
                               executionContext, AnalysisContext.builder().build(), executionData, hosts))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("No connector found with id " + configId);
  }
}
