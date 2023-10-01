/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.beans.Variable.ENV_ID;
import static software.wings.service.impl.yaml.handler.infraprovisioner.TestConstants.ACCOUNT_ID;
import static software.wings.service.impl.yaml.handler.infraprovisioner.TestConstants.APP_ID;
import static software.wings.sm.states.ApprovalState.ApprovalStateType.JIRA;
import static software.wings.utils.WingsTestConstants.JIRA_CONNECTOR_ID;
import static software.wings.utils.WingsTestConstants.JIRA_ISSUE_ID;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.waiter.OrchestrationNotifyEventListener;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.JiraConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JiraHelperServiceTest {
  private static final String APRROVAL_ID = "approvalId";
  private static final String APPROVAL_FIELD = "approvalField";
  private static final String APPROVAL_VALUE = "in progress";
  private static final String REJECTION_FIELD = "rejectionField";
  private static final String REJECTION_VALUE = "todo";
  @InjectMocks JiraHelperService jiraHelperService;
  @Mock SettingsService settingService;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock DelegateServiceImpl delegateService;
  @Mock SecretManager secretManager;

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void checkApprovalAsyncTest() {
    JiraConfig jiraConfig = JiraConfig.builder()
                                .accountId(ACCOUNT_ID)
                                .password("aoeu".toCharArray())
                                .username("username")
                                .baseUrl("http://jira.dev.harness")
                                .encryptedPassword("aoeu")
                                .delegateSelectors(null)
                                .build();
    SettingAttribute settingAttr = SettingAttribute.Builder.aSettingAttribute()
                                       .withAccountId(ACCOUNT_ID)
                                       .withAppId(APP_ID)
                                       .withEnvId(ENV_ID)
                                       .withName("Jira onprem")
                                       .withValue(jiraConfig)
                                       .build();
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

    String taskId = "TASK_ID";
    when(settingService.getByAccountAndId(ACCOUNT_ID, JIRA_CONNECTOR_ID)).thenReturn(settingAttr);
    when(delegateService.queueTaskV2(any())).thenReturn(taskId);

    jiraHelperService.checkApprovalAsync(entity);

    verify(waitNotifyEngine, times(1))
        .waitForAllOn(eq(OrchestrationNotifyEventListener.ORCHESTRATION), any(), eq(taskId));
  }
}
