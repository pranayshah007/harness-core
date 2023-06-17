/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SRIRAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.beans.TemplateExpression;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.metrics.RiskLevel;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.LogMLAnalysisSummary;
import software.wings.service.impl.splunk.SplunkDataCollectionInfoV2;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Created by rsingh on 10/9/17.
 */
public class SplunkV2StateTest extends APMStateVerificationTestBase {
  private SplunkV2State splunkState;
  @Mock private CVActivityLogger activityLogger;

  @Before
  public void setup() throws IllegalAccessException {
    setupCommon();
    MockitoAnnotations.initMocks(this);
    setupCommonMocks();
    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString())).thenReturn(activityLogger);
    AppService appService = mock(AppService.class);
    when(appService.getAccountIdByAppId(anyString())).thenReturn(generateUuid());
    when(appService.get(anyString()))
        .thenReturn(Application.Builder.anApplication().name(generateUuid()).accountId(accountId).build());

    AccountService accountService = mock(AccountService.class);
    when(accountService.getAccountType(anyString())).thenReturn(Optional.of(AccountType.PAID));

    splunkState = new SplunkV2State("SplunkState");
    splunkState.setQuery("exception");
    splunkState.setTimeDuration("15");
    setupCommonFields(splunkState);
    FieldUtils.writeField(splunkState, "appService", appService, true);
    FieldUtils.writeField(splunkState, "accountService", accountService, true);
    FieldUtils.writeField(splunkState, "analysisService", analysisService, true);
    FieldUtils.writeField(splunkState, "templateExpressionProcessor", templateExpressionProcessor, true);
    FieldUtils.writeField(splunkState, "workflowVerificationResultService", workflowVerificationResultService, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDefaultComparsionStrategy() {
    SplunkV2State splunkState = new SplunkV2State("SplunkState");
    assertThat(splunkState.getComparisonStrategy()).isEqualTo(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testExecute_skipVerificationTrue() {
    SplunkV2State spyState = spy(splunkState);
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .testNodes(Collections.emptySet())
                 .controlNodes(Collections.emptySet())
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .skipVerification(true)
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());

    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(response.getErrorMessage()).isEqualTo("Could not find newly deployed instances. Skipping verification");

    LogMLAnalysisSummary analysisSummary =
        analysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.NA);
    assertThat(analysisSummary.getQuery()).isEqualTo(splunkState.getQuery());
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(response.getErrorMessage());
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void noControlNodesCompareWithCurrent() {
    splunkState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    SplunkV2State spyState = spy(splunkState);
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .controlNodes(Collections.emptySet())
                 .testNodes(Collections.singleton("some-host"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());

    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    String analysisResponseMsg =
        "As no previous version instances exist for comparison, analysis will be skipped. Check your setup if this is the first deployment or if the previous instances have been deleted or replaced.";
    assertThat(response.getErrorMessage()).isEqualTo(analysisResponseMsg);
    verify(activityLogger, times(1)).info(eq(analysisResponseMsg));
    LogMLAnalysisSummary analysisSummary =
        analysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.NA);
    assertThat(analysisSummary.getQuery()).isEqualTo(splunkState.getQuery());
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(response.getErrorMessage());
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void compareWithCurrentSameTestAndControlNodes() {
    splunkState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    SplunkV2State spyState = spy(splunkState);
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    CVActivityLogger activityLogger = mock(CVActivityLogger.class);
    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString())).thenReturn(activityLogger);
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .testNodes(Collections.singleton("some-host"))
                 .controlNodes(Collections.emptySet())
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());

    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    String analysisResponseMsg =
        "As no previous version instances exist for comparison, analysis will be skipped. Check your setup if this is the first deployment or if the previous instances have been deleted or replaced.";
    assertThat(response.getErrorMessage()).isEqualTo(analysisResponseMsg);
    verify(activityLogger, times(1)).info(eq(analysisResponseMsg));
    LogMLAnalysisSummary analysisSummary =
        analysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.NA);
    assertThat(analysisSummary.getQuery()).isEqualTo(splunkState.getQuery());
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(response.getErrorMessage());
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void comparePreviousNodeNameNotResolved() {
    splunkState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS.name());
    splunkState.setHostnameTemplate("${some-expression}");
    SplunkV2State spyState = spy(splunkState);
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    CVActivityLogger activityLogger = mock(CVActivityLogger.class);
    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString())).thenReturn(activityLogger);
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("${some-expression}"))
                 .testNodes(Collections.singleton("some-host"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());

    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("The expression ${some-expression} could not be resolved for hosts");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void compareCurrentNodeNameNotResolved() {
    splunkState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    splunkState.setHostnameTemplate("${some-expression}");
    SplunkV2State spyState = spy(splunkState);
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("${some-expression}"))
                 .testNodes(Collections.singleton("some-host"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());

    CVActivityLogger activityLogger = mock(CVActivityLogger.class);
    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString())).thenReturn(activityLogger);
    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("The expression ${some-expression} could not be resolved for hosts");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void handleAsyncSummaryFail() {
    VerificationStateAnalysisExecutionData logAnalysisExecutionData =
        VerificationStateAnalysisExecutionData.builder()
            .correlationId(UUID.randomUUID().toString())
            .stateExecutionInstanceId(stateExecutionId)
            .serverConfigId(UUID.randomUUID().toString())
            .query(splunkState.getQuery())
            .canaryNewHostNames(Sets.newHashSet("test1", "test2"))
            .lastExecutionNodes(Sets.newHashSet("control1", "control2", "control3"))
            .build();

    logAnalysisExecutionData.setErrorMsg(UUID.randomUUID().toString());

    VerificationDataAnalysisResponse response =
        VerificationDataAnalysisResponse.builder().stateExecutionData(logAnalysisExecutionData).build();
    response.setExecutionStatus(ExecutionStatus.ERROR);

    Map<String, ResponseData> responseMap = new HashMap<>();
    responseMap.put("somekey", response);

    ExecutionResponse executionResponse = splunkState.handleAsyncResponse(executionContext, responseMap);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.ERROR);
    assertThat(executionResponse.getErrorMessage()).isEqualTo(logAnalysisExecutionData.getErrorMsg());
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(logAnalysisExecutionData);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void handleAsyncSummaryPassNoData() {
    doReturn("exception").when(executionContext).renderExpression(anyString());
    CVActivityLogger activityLogger = mock(CVActivityLogger.class);
    when(cvActivityLogService.getLoggerByStateExecutionId(any(), any())).thenReturn(activityLogger);
    VerificationStateAnalysisExecutionData logAnalysisExecutionData =
        VerificationStateAnalysisExecutionData.builder()
            .correlationId(UUID.randomUUID().toString())
            .stateExecutionInstanceId(stateExecutionId)
            .serverConfigId(UUID.randomUUID().toString())
            .query(splunkState.getQuery())
            .canaryNewHostNames(Sets.newHashSet("test1", "test2"))
            .lastExecutionNodes(Sets.newHashSet("control1", "control2", "control3"))
            .build();

    logAnalysisExecutionData.setErrorMsg(UUID.randomUUID().toString());

    VerificationDataAnalysisResponse response =
        VerificationDataAnalysisResponse.builder().stateExecutionData(logAnalysisExecutionData).build();
    response.setExecutionStatus(ExecutionStatus.SUCCESS);

    Map<String, ResponseData> responseMap = new HashMap<>();
    responseMap.put("somekey", response);

    SplunkV2State spyState = spy(splunkState);
    doReturn(AbstractAnalysisState.CVInstanceApiResponse.builder()
                 .controlNodes(Collections.singleton("control"))
                 .testNodes(Collections.singleton("test"))
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);

    wingsPersistence.save(AnalysisContext.builder()
                              .stateExecutionId(executionContext.getStateExecutionInstanceId())
                              .appId(appId)
                              .query(splunkState.getQuery())
                              .build());
    ExecutionResponse executionResponse = spyState.handleAsyncResponse(executionContext, responseMap);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("No data found with given queries. Skipped Analysis");

    LogMLAnalysisSummary analysisSummary =
        analysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    verify(activityLogger).info("No data found with given queries. Skipped Analysis");
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.NA);
    assertThat(analysisSummary.getQuery()).isEqualTo(splunkState.getQuery());
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(executionResponse.getErrorMessage());
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void testTimestampFormat() {
    SimpleDateFormat sdf = new SimpleDateFormat(ElkAnalysisState.DEFAULT_TIME_FORMAT);
    assertThat(sdf.parse("2013-10-07T12:13:27.001Z", new ParsePosition(0))).isNotNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withoutTemplatization() throws IllegalAccessException {
    splunkState.setAnalysisServerConfigId(generateUuid());
    splunkState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    FieldUtils.writeField(splunkState, "renderedQuery", "rendered query", true);
    SplunkDataCollectionInfoV2 splunkDataCollectionInfoV2 =
        (software.wings.service.impl.splunk.SplunkDataCollectionInfoV2) splunkState.createDataCollectionInfo(
            executionContext, Sets.newHashSet("host1", "host2"));
    assertThat(DelegateStateType.SPLUNKV2).isEqualTo(splunkDataCollectionInfoV2.getStateType());
    assertThat(splunkDataCollectionInfoV2.getSplunkConfig()).isNull();
    assertThat(splunkDataCollectionInfoV2.getStateExecutionId())
        .isEqualTo(executionContext.getStateExecutionInstanceId());
    assertThat(splunkDataCollectionInfoV2.getQuery()).isEqualTo(splunkState.getRenderedQuery());
    assertThat(splunkDataCollectionInfoV2.getConnectorId()).isEqualTo(splunkState.getAnalysisServerConfigId());
    assertThat(splunkDataCollectionInfoV2.getHosts()).isEqualTo(Sets.newHashSet("host1", "host2"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withTemplatization() throws IllegalAccessException {
    SplunkConfig splunkConfig = SplunkConfig.builder()
                                    .accountId(accountId)
                                    .splunkUrl(UUID.randomUUID().toString())
                                    .username(UUID.randomUUID().toString())
                                    .password(UUID.randomUUID().toString().toCharArray())
                                    .build();
    splunkState.setAnalysisServerConfigId(generateUuid());
    splunkState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    FieldUtils.writeField(splunkState, "renderedQuery", "rendered query", true);
    FieldUtils.writeField(splunkState, "templateExpressionProcessor", templateExpressionProcessor, true);
    SplunkV2State spyState = spy(splunkState);
    doReturn(Arrays.asList(TemplateExpression.builder()
                               .fieldName("analysisServerConfigId")
                               .expression("${SPLUNK_Server}")
                               .metadata(ImmutableMap.of("entityType", "SPLUNK_CONFIGID"))
                               .build()))
        .when(spyState)
        .getTemplateExpressions();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("splunk-config")
                                            .withValue(splunkConfig)
                                            .build();
    wingsPersistence.save(settingAttribute);
    when(executionContext.renderExpression("${workflow.variables.SPLUNK_Server}"))
        .thenReturn(settingAttribute.getUuid());
    SplunkDataCollectionInfoV2 splunkDataCollectionInfoV2 =
        (software.wings.service.impl.splunk.SplunkDataCollectionInfoV2) splunkState.createDataCollectionInfo(
            executionContext, Sets.newHashSet("host1", "host2"));
    assertThat(DelegateStateType.SPLUNKV2).isEqualTo(splunkDataCollectionInfoV2.getStateType());
    assertThat(splunkDataCollectionInfoV2.getSplunkConfig()).isNull();
    assertThat(splunkDataCollectionInfoV2.getStateExecutionId())
        .isEqualTo(executionContext.getStateExecutionInstanceId());
    assertThat(splunkDataCollectionInfoV2.getQuery()).isEqualTo(splunkState.getRenderedQuery());
    assertThat(splunkDataCollectionInfoV2.getConnectorId()).isEqualTo(splunkState.getAnalysisServerConfigId());
    assertThat(splunkDataCollectionInfoV2.getHosts()).isEqualTo(Sets.newHashSet("host1", "host2"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withExpressionFields() throws IllegalAccessException {
    SplunkConfig splunkConfig = SplunkConfig.builder()
                                    .accountId(accountId)
                                    .splunkUrl(UUID.randomUUID().toString())
                                    .username(UUID.randomUUID().toString())
                                    .password(UUID.randomUUID().toString().toCharArray())
                                    .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("splunk-config")
                                            .withValue(splunkConfig)
                                            .withUuid(generateUuid())
                                            .build();
    wingsPersistence.save(settingAttribute);
    splunkState.setAnalysisServerConfigId(settingAttribute.getUuid());
    splunkState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    FieldUtils.writeField(splunkState, "renderedQuery", "rendered query", true);
    FieldUtils.writeField(splunkState, "templateExpressionProcessor", templateExpressionProcessor, true);
    SplunkV2State spyState = spy(splunkState);

    SplunkDataCollectionInfoV2 splunkDataCollectionInfoV2 =
        (SplunkDataCollectionInfoV2) spyState.createDataCollectionInfo(
            executionContext, Sets.newHashSet("host1", "host2"));
    assertThat(DelegateStateType.SPLUNKV2).isEqualTo(splunkDataCollectionInfoV2.getStateType());
    assertThat(splunkDataCollectionInfoV2.getSplunkConfig()).isNull();
    assertThat(splunkDataCollectionInfoV2.getStateExecutionId())
        .isEqualTo(executionContext.getStateExecutionInstanceId());
    assertThat(splunkDataCollectionInfoV2.getQuery()).isEqualTo(splunkState.getRenderedQuery());
    assertThat(splunkDataCollectionInfoV2.getConnectorId()).isEqualTo(splunkState.getAnalysisServerConfigId());
    assertThat(splunkDataCollectionInfoV2.getHosts()).isEqualTo(Sets.newHashSet("host1", "host2"));
  }
}
