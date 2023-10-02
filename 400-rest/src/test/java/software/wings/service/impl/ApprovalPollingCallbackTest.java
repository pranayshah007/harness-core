/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.service.impl.yaml.handler.infraprovisioner.TestConstants.ACCOUNT_ID;
import static software.wings.service.impl.yaml.handler.infraprovisioner.TestConstants.APP_ID;
import static software.wings.sm.states.ApprovalState.ApprovalStateType.JIRA;
import static software.wings.utils.WingsTestConstants.JIRA_CONNECTOR_ID;
import static software.wings.utils.WingsTestConstants.JIRA_ISSUE_ID;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.service.ApprovalUtils;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ApprovalPollingCallbackTest {
  private static final String APRROVAL_ID = "approvalId";
  private static final String APPROVAL_FIELD = "approvalField";
  private static final String APPROVAL_VALUE = "in progress";
  private static final String REJECTION_FIELD = "rejectionField";
  private static final String REJECTION_VALUE = "todo";

  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock StateExecutionService stateExecutionService;
  @Mock WaitNotifyEngine waitNotifyEngine;

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void notifyTest() {
    String callbackId = generateUuid();

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().executionStatus(ExecutionStatus.PAUSED).currentStatus("To Do").build();

    ApprovalPollingJobEntity entity = ApprovalPollingJobEntity.builder()
                                          .approvalId(APRROVAL_ID)
                                          .accountId(ACCOUNT_ID)
                                          .appId(APP_ID)
                                          .stateExecutionInstanceId(STATE_EXECUTION_ID)
                                          .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                          .connectorId(JIRA_CONNECTOR_ID)
                                          .approvalField(APPROVAL_FIELD)
                                          .approvalValue(APPROVAL_VALUE)
                                          .rejectionField(REJECTION_FIELD)
                                          .rejectionValue(REJECTION_VALUE)
                                          .issueId(JIRA_ISSUE_ID)
                                          .approvalType(JIRA)
                                          .build();

    ApprovalPollingCallback callback = ApprovalPollingCallback.builder()
                                           .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                           .workflowExecutionService(workflowExecutionService)
                                           .stateExecutionService(stateExecutionService)
                                           .stateExecutionInstanceId(STATE_EXECUTION_ID)
                                           .waitNotifyEngine(waitNotifyEngine)
                                           .entity(entity)
                                           .build();

    try (MockedStatic<ApprovalUtils> approvalMock = Mockito.mockStatic(ApprovalUtils.class)) {
      approvalMock
          .when(()
                    -> ApprovalUtils.checkApproval(any(), any(), eq(WORKFLOW_EXECUTION_ID), eq(STATE_EXECUTION_ID),
                        eq(null), eq(ExecutionStatus.PAUSED), any()))
          .thenAnswer((Answer<Void>) invocation -> null);

      callback.notify(Map.of(callbackId, jiraExecutionData));

      approvalMock.verify(()
                              -> ApprovalUtils.checkApproval(any(), any(), eq(WORKFLOW_EXECUTION_ID),
                                  eq(STATE_EXECUTION_ID), eq(null), eq(ExecutionStatus.PAUSED), any()));
    }
  }
}
