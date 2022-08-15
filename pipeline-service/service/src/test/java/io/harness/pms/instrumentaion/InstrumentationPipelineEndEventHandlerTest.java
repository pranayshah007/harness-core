/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dto.FailureInfoDTO;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.FailureType;
import io.harness.execution.NodeExecution;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.notification.PipelineEventType;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.notification.NotificationInstrumentationHelper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.sdk.SdkStepHelper;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InstrumentationPipelineEndEventHandlerTest extends CategoryTest {
  @Mock TelemetryReporter telemetryReporter;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock NotificationInstrumentationHelper notificationInstrumentationHelper;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock SdkStepHelper sdkStepHelper;
  @Mock AccountService accountService;
  @InjectMocks InstrumentationPipelineEndEventHandler instrumentationPipelineEndEventHandler;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnEnd() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .layoutNodeMap(
                Collections.singletonMap("stage1", GraphLayoutNodeDTO.builder().status(ExecutionStatus.FAILED).build()))
            .executionTriggerInfo(
                ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).setIsRerun(false).build())
            .failureInfo(
                FailureInfoDTO.builder()
                    .responseMessages(Collections.singletonList(ResponseMessage.builder().message("message").build()))
                    .failureTypeList(EnumSet.of(FailureType.AUTHENTICATION))
                    .build())
            .startTs(1000L)
            .endTs(2000L)
            .status(ExecutionStatus.FAILED)
            .build();
    List<NotificationRules> notificationRulesList = Collections.singletonList(
        NotificationRules.builder()
            .pipelineEvents(
                Collections.singletonList(PipelineEvent.builder().type(PipelineEventType.PIPELINE_END).build()))
            .build());
    Set<String> notificationMethods = Collections.singleton("slack");
    String nodeExecutionId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .identifier("http")
                            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("Http").build())
                            .serviceName("CD")
                            .build();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId("planExecutionId")
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projectId")
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                 .setTriggeredBy(TriggeredBy.newBuilder()
                                                                     .setIdentifier("admin")
                                                                     .putExtraInfo("email", "admin@harness.io")
                                                                     .build())
                                                 .build())
                             .build())
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
            .build();
    List<NodeExecution> nodeExecutionList =
        Arrays.asList(NodeExecution.builder().ambiance(ambiance).planNode(planNode).build());
    doReturn(nodeExecutionList).when(nodeExecutionService).fetchNodeExecutions(any());
    doReturn(new HashSet() {
      { add("Http"); }
    })
        .when(sdkStepHelper)
        .getAllStepVisibleInUI();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity("accountId", "orgId", "projectId", "planExecutionId", false);
    doReturn(notificationRulesList)
        .when(notificationInstrumentationHelper)
        .getNotificationRules("planExecutionId", ambiance);
    doReturn(notificationMethods)
        .when(notificationInstrumentationHelper)
        .getNotificationMethodTypes(notificationRulesList);

    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    instrumentationPipelineEndEventHandler.onEnd(ambiance);

    ArgumentCaptor<HashMap> argumentCaptor = ArgumentCaptor.forClass(HashMap.class);
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eq(PipelineInstrumentationConstants.PIPELINE_EXECUTION), eq("admin@harness.io"),
            eq("accountId"), argumentCaptor.capture(), any(), any(), any());
    HashMap<String, Object> propertiesMap = argumentCaptor.getValue();
    EnumSet<io.harness.pms.contracts.execution.failure.FailureType> returnedFailureTypes =
        (EnumSet<io.harness.pms.contracts.execution.failure.FailureType>) propertiesMap.get(
            PipelineInstrumentationConstants.FAILURE_TYPES);
    Set<String> returnedErrorMessages =
        (Set<String>) propertiesMap.get(PipelineInstrumentationConstants.ERROR_MESSAGES);
    Set<String> returnedNotificationMethods =
        (Set<String>) propertiesMap.get(PipelineInstrumentationConstants.NOTIFICATION_METHODS);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.LEVEL), StepCategory.PIPELINE);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.STATUS), ExecutionStatus.FAILED);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.EXECUTION_TIME), 1L);
    assertEquals(((HashSet<String>) propertiesMap.get(PipelineInstrumentationConstants.STEP_TYPES)).size(), 1);
    assertTrue(((HashSet<String>) propertiesMap.get(PipelineInstrumentationConstants.STEP_TYPES)).contains("Http"));
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.ACCOUNT_NAME), "TestAccountName");
    assertEquals(returnedNotificationMethods, notificationMethods);
    assertTrue(returnedFailureTypes.contains(FailureType.AUTHENTICATION));
    assertTrue(returnedErrorMessages.contains("message"));

    verify(telemetryReporter, times(1))
        .sendTrackEvent(eq(PipelineInstrumentationConstants.PIPELINE_NOTIFICATION), eq("admin@harness.io"),
            eq("accountId"), argumentCaptor.capture(), any(), any(), any());
    propertiesMap = argumentCaptor.getValue();
    Set<String> eventTypes = (Set<String>) propertiesMap.get(PipelineInstrumentationConstants.EVENT_TYPES);
    assertEquals(propertiesMap.get(PipelineInstrumentationConstants.ACCOUNT_NAME), "TestAccountName");

    assertEquals(eventTypes.size(), 1);
    assertTrue(eventTypes.contains(PipelineEventType.PIPELINE_END));
  }
}
