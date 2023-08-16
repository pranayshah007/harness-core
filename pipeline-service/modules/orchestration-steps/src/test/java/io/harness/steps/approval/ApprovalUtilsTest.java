/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.steps.shellscript.ShellType;

import com.mongodb.MongoException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ApprovalUtilsTest extends CategoryTest {
  private static final String INSTANCE_ID = "id";
  private static final String TASK_ID = "task_id";
  @Mock ApprovalInstanceService approvalInstanceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetDelegateTaskName() {
    assertThat(ApprovalUtils.getDelegateTaskName(buildApprovalInstance(ApprovalType.SERVICENOW_APPROVAL)))
        .isEqualTo("ServiceNow Task: Get ticket");
    assertThat(ApprovalUtils.getDelegateTaskName(buildApprovalInstance(ApprovalType.JIRA_APPROVAL)))
        .isEqualTo("Jira Task: Get Issue");
    CustomApprovalInstance customApprovalInstance =
        (CustomApprovalInstance) buildApprovalInstance(ApprovalType.CUSTOM_APPROVAL);
    customApprovalInstance.setShellType(ShellType.Bash);
    assertThat(ApprovalUtils.getDelegateTaskName(customApprovalInstance)).isEqualTo("Shell Script Task");
    customApprovalInstance.setShellType(ShellType.PowerShell);
    assertThat(ApprovalUtils.getDelegateTaskName(customApprovalInstance)).isEqualTo("Shell Script Task");
    assertThatThrownBy(() -> ApprovalUtils.getDelegateTaskName(buildApprovalInstance(ApprovalType.HARNESS_APPROVAL)))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(ApprovalUtils.getDelegateTaskName(null)).isNull();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateTaskId() {
    // update is not called if task id is empty or approval Service is null
    ApprovalUtils.updateTaskId(INSTANCE_ID, "  ", approvalInstanceService);
    ApprovalUtils.updateTaskId(INSTANCE_ID, TASK_ID, null);
    ApprovalUtils.updateTaskId(INSTANCE_ID, TASK_ID, approvalInstanceService);
    Mockito.verify(approvalInstanceService, times(1)).updateLatestDelegateTaskId(INSTANCE_ID, TASK_ID);

    // when update fails
    doThrow(new MongoException("example exception"))
        .when(approvalInstanceService)
        .updateLatestDelegateTaskId(INSTANCE_ID, TASK_ID);
    ApprovalUtils.updateTaskId(INSTANCE_ID, TASK_ID, approvalInstanceService);
    Mockito.verify(approvalInstanceService, times(2)).updateLatestDelegateTaskId(INSTANCE_ID, TASK_ID);
  }

  private ApprovalInstance buildApprovalInstance(ApprovalType approvalType) {
    switch (approvalType) {
      case JIRA_APPROVAL:
        JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder().build();
        jiraApprovalInstance.setId(INSTANCE_ID);
        jiraApprovalInstance.setType(ApprovalType.JIRA_APPROVAL);
        return jiraApprovalInstance;
      case SERVICENOW_APPROVAL:
        ServiceNowApprovalInstance serviceNowApprovalInstance = ServiceNowApprovalInstance.builder().build();
        serviceNowApprovalInstance.setId(INSTANCE_ID);
        serviceNowApprovalInstance.setType(ApprovalType.SERVICENOW_APPROVAL);
        return serviceNowApprovalInstance;
      case CUSTOM_APPROVAL:
        CustomApprovalInstance customApprovalInstance = CustomApprovalInstance.builder().build();
        customApprovalInstance.setId(INSTANCE_ID);
        customApprovalInstance.setType(ApprovalType.CUSTOM_APPROVAL);
        return customApprovalInstance;
      case HARNESS_APPROVAL:
        HarnessApprovalInstance harnessApprovalInstance = HarnessApprovalInstance.builder().build();
        harnessApprovalInstance.setId(INSTANCE_ID);
        harnessApprovalInstance.setType(ApprovalType.HARNESS_APPROVAL);
        return harnessApprovalInstance;
      default:
        return null;
    }
  }
}
