/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.TaskType.SHELL_SCRIPT_TASK_NG;
import static software.wings.beans.TaskType.WIN_RM_SHELL_SCRIPT_TASK_NG;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.jira.JiraActionNG;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.ApprovalProgressData;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.shellscript.ShellType;
import io.harness.waiter.WaitNotifyEngine;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ApprovalUtils {
  public static final String JIRA_DELEGATE_TASK_NAME = format("Jira Task: %s", JiraActionNG.GET_ISSUE.getDisplayName());
  public static final String SERVICENOW_DELEGATE_TASK_NAME =
      format("ServiceNow Task: %s", ServiceNowActionNG.GET_TICKET.getDisplayName());

  public static void sendTaskIdProgressUpdate(
      String taskId, String taskName, String instanceId, WaitNotifyEngine waitNotifyEngine) {
    if (isNotBlank(taskId)) {
      try {
        // Sends approval progress update to update task id to latest delegate task id
        waitNotifyEngine.progressOn(
            instanceId, ApprovalProgressData.builder().latestDelegateTaskId(taskId).taskName(taskName).build());
      } catch (Exception ex) {
        // log and ignore the error occurred while progress update
        log.warn("Error sending progress update for taskId {} while polling", taskId, ex);
      }
    }
  }

  public static void updateTaskId(String instanceId, String taskId, ApprovalInstanceService approvalInstanceService) {
    if (isNotBlank(taskId) && !isNull(approvalInstanceService)) {
      try {
        // update task id in approval instance to latest delegate task id
        approvalInstanceService.updateLatestDelegateTaskId(instanceId, taskId);
      } catch (Exception ex) {
        // log and ignore the error occurred while taskId update
        log.warn("Error updating latest taskId {} while polling for instance {}", taskId, instanceId, ex);
      }
    }
  }

  public String getCustomApprovalTaskName(CustomApprovalInstance instance) {
    if (ShellType.Bash.equals(instance.getShellType())) {
      return SHELL_SCRIPT_TASK_NG.getDisplayName();
    } else if (ShellType.PowerShell.equals(instance.getShellType())) {
      return WIN_RM_SHELL_SCRIPT_TASK_NG.getDisplayName();
    } else {
      throw new InvalidRequestException(format("Shell %s is not supported", instance.getShellType()));
    }
  }

  public String getDelegateTaskName(ApprovalInstance instance) {
    if (isNull(instance)) {
      return null;
    }
    switch (instance.getType()) {
      case JIRA_APPROVAL:
        return JIRA_DELEGATE_TASK_NAME;
      case SERVICENOW_APPROVAL:
        return SERVICENOW_DELEGATE_TASK_NAME;
      case CUSTOM_APPROVAL:
        return getCustomApprovalTaskName((CustomApprovalInstance) instance);
      default:
        throw new InvalidRequestException(
            format("%s Approval doesn't have delegate task associated", instance.getType()));
    }
  }
}
