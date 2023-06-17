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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.ElkConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.metrics.RiskLevel;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.ElkConnector;
import software.wings.service.impl.analysis.ElkValidationType;
import software.wings.service.impl.analysis.LogMLAnalysisSummary;
import software.wings.service.impl.elk.ElkDataCollectionInfoV2;
import software.wings.service.impl.elk.ElkQueryType;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.elk.ElkAnalysisService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;
import software.wings.sm.states.AbstractAnalysisState.CVInstanceApiResponse;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Created by rsingh on 10/9/17.
 */
public class ElkAnalysisStateTest extends APMStateVerificationTestBase {
  @Mock private ElkAnalysisService elkAnalysisService;
  private ElkAnalysisState elkAnalysisState;
  private String elkConfigId;
  @Mock private CVActivityLogger activityLogger;

  @Before
  public void setup() throws IllegalAccessException {
    setupCommon();
    MockitoAnnotations.initMocks(this);
    setupCommonMocks();

    AppService appService = mock(AppService.class);
    when(appService.getAccountIdByAppId(anyString())).thenReturn(generateUuid());
    when(appService.get(anyString()))
        .thenReturn(Application.Builder.anApplication().name(generateUuid()).accountId(accountId).build());

    AccountService accountService = mock(AccountService.class);
    when(accountService.getAccountType(anyString())).thenReturn(Optional.of(AccountType.PAID));

    elkAnalysisState = new ElkAnalysisState("ElkAnalysisState");
    elkAnalysisState.setQuery("exception");
    elkAnalysisState.setTimeDuration("15");
    elkAnalysisState.setMessageField(generateUuid());
    elkAnalysisState.setTimestampFormat(generateUuid());
    elkAnalysisState.setQueryType(ElkQueryType.MATCH.name());
    elkAnalysisState.setTimestampField(generateUuid());
    elkAnalysisState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    setupCommonFields(elkAnalysisState);
    FieldUtils.writeField(elkAnalysisState, "elkAnalysisService", elkAnalysisService, true);
    FieldUtils.writeField(elkAnalysisState, "accountService", accountService, true);
    FieldUtils.writeField(elkAnalysisState, "analysisService", analysisService, true);
    FieldUtils.writeField(
        elkAnalysisState, "workflowVerificationResultService", workflowVerificationResultService, true);
    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString())).thenReturn(activityLogger);
    when(executionContext.getAccountId()).thenReturn(accountId);

    ElkConfig elkConfig = ElkConfig.builder()
                              .accountId(accountId)
                              .elkConnector(ElkConnector.ELASTIC_SEARCH_SERVER)
                              .elkUrl(generateUuid())
                              .username(generateUuid())
                              .password(generateUuid().toCharArray())
                              .validationType(ElkValidationType.PASSWORD)
                              .kibanaVersion(String.valueOf(0))
                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withName("elk-config")
                                            .withValue(elkConfig)
                                            .build();
    elkConfigId = wingsPersistence.save(settingAttribute);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDefaultComparsionStrategy() {
    ElkAnalysisState elkAnalysisState = new ElkAnalysisState("ElkAnalysisState");
    assertThat(elkAnalysisState.getComparisonStrategy()).isEqualTo(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testExecute_skipVerificationTrue() {
    ElkAnalysisState spyState = spy(elkAnalysisState);
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    doReturn(CVInstanceApiResponse.builder()
                 .testNodes(Collections.emptySet())
                 .controlNodes(Collections.emptySet())
                 .skipVerification(true)
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());

    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(response.getErrorMessage()).isEqualTo("Could not find newly deployed instances. Skipping verification");

    LogMLAnalysisSummary analysisSummary = analysisService.getAnalysisSummary(stateExecutionId, appId, StateType.ELK);
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.NA);
    assertThat(analysisSummary.getQuery()).isEqualTo(elkAnalysisState.getQuery());
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(response.getErrorMessage());
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void noControlNodesCompareWithCurrent() {
    elkAnalysisState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    ElkAnalysisState spyState = spy(elkAnalysisState);
    doReturn(CVInstanceApiResponse.builder()
                 .testNodes(Collections.singleton("some-host"))
                 .controlNodes(Collections.emptySet())
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());

    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    String analysisResponseMsg =
        "As no previous version instances exist for comparison, analysis will be skipped. Check your setup if this is the first deployment or if the previous instances have been deleted or replaced.";
    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(response.getErrorMessage()).isEqualTo(analysisResponseMsg);
    verify(activityLogger, times(1)).info(eq(analysisResponseMsg));
    LogMLAnalysisSummary analysisSummary = analysisService.getAnalysisSummary(stateExecutionId, appId, StateType.ELK);
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.NA);
    assertThat(analysisSummary.getQuery()).isEqualTo(elkAnalysisState.getQuery());
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(response.getErrorMessage());
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void compareWithCurrentSameTestAndControlNodes() {
    elkAnalysisState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    ElkAnalysisState spyState = spy(elkAnalysisState);
    doReturn(workflowId).when(spyState).getWorkflowId(executionContext);
    doReturn(serviceId).when(spyState).getPhaseServiceId(executionContext);
    doReturn(CVInstanceApiResponse.builder()
                 .testNodes(Collections.singleton("some-host"))
                 .controlNodes(Collections.emptySet())
                 .newNodesTrafficShiftPercent(Optional.empty())
                 .build())
        .when(spyState)
        .getCVInstanceAPIResponse(any());

    ExecutionResponse response = spyState.execute(executionContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(response.getErrorMessage())
        .isEqualTo(
            "As no previous version instances exist for comparison, analysis will be skipped. Check your setup if this is the first deployment or if the previous instances have been deleted or replaced.");

    LogMLAnalysisSummary analysisSummary = analysisService.getAnalysisSummary(stateExecutionId, appId, StateType.ELK);
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.NA);
    assertThat(analysisSummary.getQuery()).isEqualTo(elkAnalysisState.getQuery());
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(response.getErrorMessage());
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withoutTemplatization() throws IllegalAccessException {
    elkAnalysisState.setAnalysisServerConfigId(generateUuid());
    String indices = generateUuid();
    elkAnalysisState.setIndices(indices);

    String messageField = generateUuid();
    elkAnalysisState.setMessageField(messageField);

    String timestampFieldFormat = generateUuid();
    elkAnalysisState.setTimestampFormat(timestampFieldFormat);

    elkAnalysisState.setQueryType(ElkQueryType.MATCH.name());
    elkAnalysisState.setTimestampField("@time");
    elkAnalysisState.setComparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT.name());
    FieldUtils.writeField(elkAnalysisState, "renderedQuery", "rendered query", true);
    ElkDataCollectionInfoV2 elkDataCollectionInfoV2 =
        (ElkDataCollectionInfoV2) elkAnalysisState.createDataCollectionInfo(
            executionContext, Sets.newHashSet("host1", "host2"));
    assertThat(DelegateStateType.ELK).isEqualTo(elkDataCollectionInfoV2.getStateType());
    assertThat(elkDataCollectionInfoV2.getElkConfig()).isNull();
    assertThat(elkDataCollectionInfoV2.getStateExecutionId()).isEqualTo(executionContext.getStateExecutionInstanceId());
    assertThat(elkDataCollectionInfoV2.getMessageField()).isEqualTo(elkAnalysisState.getMessageField());
    assertThat(elkDataCollectionInfoV2.getQuery()).isEqualTo(elkAnalysisState.getRenderedQuery());
    assertThat(elkDataCollectionInfoV2.getTimestampField()).isEqualTo(elkAnalysisState.getTimestampField());
    assertThat(elkDataCollectionInfoV2.getTimestampFieldFormat()).isEqualTo(elkAnalysisState.getTimestampFormat());
    assertThat(elkDataCollectionInfoV2.getIndices()).isEqualTo(elkAnalysisState.getIndices());
    assertThat(elkDataCollectionInfoV2.getQueryType()).isEqualTo(elkAnalysisState.getQueryType());
    assertThat(elkDataCollectionInfoV2.getConnectorId()).isEqualTo(elkAnalysisState.getAnalysisServerConfigId());
    assertThat(elkDataCollectionInfoV2.getHosts()).isEqualTo(Sets.newHashSet("host1", "host2"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withTemplatization() throws IllegalAccessException {
    FieldUtils.writeField(elkAnalysisState, "renderedQuery", "rendered query", true);
    FieldUtils.writeField(elkAnalysisState, "templateExpressionProcessor", templateExpressionProcessor, true);
    ElkAnalysisState spyState = spy(elkAnalysisState);
    doReturn(Arrays.asList(TemplateExpression.builder()
                               .fieldName("analysisServerConfigId")
                               .expression("${ELK_Server}")
                               .metadata(ImmutableMap.of("entityType", "ELK_CONFIGID"))
                               .build(),
                 TemplateExpression.builder()
                     .fieldName("indices")
                     .expression("${indices}")
                     .metadata(ImmutableMap.of("entityType", "ELK_INDICES"))
                     .build()))
        .when(spyState)
        .getTemplateExpressions();
    when(executionContext.renderExpression("${workflow.variables.ELK_Server}")).thenReturn(elkConfigId);
    when(executionContext.renderExpression("${workflow.variables.indices}")).thenReturn("rendered index");
    ElkDataCollectionInfoV2 elkDataCollectionInfoV2 = (ElkDataCollectionInfoV2) spyState.createDataCollectionInfo(
        executionContext, Sets.newHashSet("host1", "host2"));

    assertThat(DelegateStateType.ELK).isEqualTo(elkDataCollectionInfoV2.getStateType());
    assertThat(elkDataCollectionInfoV2.getElkConfig()).isNull();
    assertThat(elkDataCollectionInfoV2.getStateExecutionId()).isEqualTo(executionContext.getStateExecutionInstanceId());
    assertThat(elkDataCollectionInfoV2.getMessageField()).isEqualTo(elkAnalysisState.getMessageField());
    assertThat(elkDataCollectionInfoV2.getQuery()).isEqualTo(elkAnalysisState.getRenderedQuery());
    assertThat(elkDataCollectionInfoV2.getTimestampField()).isEqualTo(elkAnalysisState.getTimestampField());
    assertThat(elkDataCollectionInfoV2.getTimestampFieldFormat()).isEqualTo(elkAnalysisState.getTimestampFormat());
    assertThat(elkDataCollectionInfoV2.getIndices()).isEqualTo("rendered index");
    assertThat(elkDataCollectionInfoV2.getQueryType()).isEqualTo(elkAnalysisState.getQueryType());
    assertThat(elkDataCollectionInfoV2.getConnectorId()).isEqualTo(elkConfigId);
    assertThat(elkDataCollectionInfoV2.getHosts()).isEqualTo(Sets.newHashSet("host1", "host2"));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withExpression() throws IllegalAccessException {
    elkAnalysisState.setAnalysisServerConfigId("${connectorName}");
    elkAnalysisState.setIndices("${indices}");
    FieldUtils.writeField(elkAnalysisState, "renderedQuery", "rendered query", true);
    when(executionContext.renderExpression("${connectorName}")).thenReturn("elk-config");
    when(executionContext.renderExpression("${indices}")).thenReturn("rendered index");
    ElkDataCollectionInfoV2 elkDataCollectionInfoV2 =
        (ElkDataCollectionInfoV2) elkAnalysisState.createDataCollectionInfo(
            executionContext, Sets.newHashSet("host1", "host2"));

    assertThat(DelegateStateType.ELK).isEqualTo(elkDataCollectionInfoV2.getStateType());
    assertThat(elkDataCollectionInfoV2.getElkConfig()).isNull();
    assertThat(elkDataCollectionInfoV2.getStateExecutionId()).isEqualTo(executionContext.getStateExecutionInstanceId());
    assertThat(elkDataCollectionInfoV2.getMessageField()).isEqualTo(elkAnalysisState.getMessageField());
    assertThat(elkDataCollectionInfoV2.getQuery()).isEqualTo(elkAnalysisState.getRenderedQuery());
    assertThat(elkDataCollectionInfoV2.getTimestampField()).isEqualTo(elkAnalysisState.getTimestampField());
    assertThat(elkDataCollectionInfoV2.getTimestampFieldFormat()).isEqualTo(elkAnalysisState.getTimestampFormat());
    assertThat(elkDataCollectionInfoV2.getIndices()).isEqualTo("rendered index");
    assertThat(elkDataCollectionInfoV2.getQueryType()).isEqualTo(elkAnalysisState.getQueryType());
    assertThat(elkDataCollectionInfoV2.getConnectorId()).isEqualTo(elkConfigId);
    assertThat(elkDataCollectionInfoV2.getHosts()).isEqualTo(Sets.newHashSet("host1", "host2"));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCreateDataCollectionInfo_withInvalidIndexExpression() throws IllegalAccessException {
    elkAnalysisState.setAnalysisServerConfigId("${connectorName}");
    elkAnalysisState.setIndices("${indices}");
    FieldUtils.writeField(elkAnalysisState, "renderedQuery", "rendered query", true);
    when(executionContext.renderExpression("${connectorName}")).thenReturn("elk-config");
    when(executionContext.renderExpression("${indices}")).thenReturn("${indices}");
    assertThatThrownBy(
        () -> elkAnalysisState.createDataCollectionInfo(executionContext, Sets.newHashSet("host1", "host2")))
        .isInstanceOf(DataCollectionException.class)
        .hasMessage("Expression " + elkAnalysisState.getIndices() + " could not be resolved");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetDelaySeconds_withNonDefaultValue() {
    elkAnalysisState.setInitialAnalysisDelay("5m");
    assertThat(elkAnalysisState.initialAnalysisDelay).isEqualTo("5m");
    assertThat(elkAnalysisState.getDelaySeconds("3m")).isEqualTo(180);
    assertThat(elkAnalysisState.getDelaySeconds("600s")).isEqualTo(600);
    assertThatThrownBy(() -> elkAnalysisState.getDelaySeconds("601s"))
        .hasMessage("initialAnalysisDelay can only be between 1 to 10 minutes.");
    assertThatThrownBy(() -> elkAnalysisState.getDelaySeconds("600"))
        .hasMessage("Specify delay(initialAnalysisDelay) in seconds (1s) or minutes (1m)");
  }
}
