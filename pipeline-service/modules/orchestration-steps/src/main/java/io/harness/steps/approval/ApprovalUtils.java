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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.jira.JiraActionNG;
import io.harness.jira.JiraConstantsNG;
import io.harness.jira.JiraIssueNG;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.CriteriaSpecType;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.shellscript.ShellType;

import com.google.common.base.Joiner;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ApprovalUtils {
  public static final String JIRA_DELEGATE_TASK_NAME = format("Jira Task: %s", JiraActionNG.GET_ISSUE.getDisplayName());
  public static final String SERVICENOW_DELEGATE_TASK_NAME =
      format("ServiceNow Task: %s", ServiceNowActionNG.GET_TICKET.getDisplayName());
  private static final String EMPTY_STRING = "";
  public static final List<String> JIRA_APPROVAL_STATIC_FIELDS =
      Arrays.asList(JiraConstantsNG.PROJECT_KEY, JiraConstantsNG.ISSUE_TYPE_KEY, JiraConstantsNG.STATUS_KEY);

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

  /**
   * fetches keys list as String from KeyValue criteria for Jira Approval.
   * converts display value of fields names to actual value using fieldNameToKeys map in Jira issue provided.
   * key list is appended with Jira project, issue type, status fields as these required in project, issue type
   * validation and current status update <p> If any other criteria apart from Key Value criteria is present in
   * instance, then empty string is returned. In all negative scenarios, empty string is guaranteed to be returned.
   * *
   */
  public String fetchKeysForApprovalInstance(JiraApprovalInstance jiraApprovalInstance, JiraIssueNG jiraIssueNG) {
    String keyListInKeyValueCriteria = fetchKeysForApprovalInstanceInternal(jiraApprovalInstance, jiraIssueNG);

    if (isNull(keyListInKeyValueCriteria)) {
      log.warn("Unexpectedly null value obtained while fetching keys list in key value criteria");
      keyListInKeyValueCriteria = EMPTY_STRING;
    }
    log.info("Key list value calculated for approval instance {} : {}",
        isNull(jiraApprovalInstance) ? null : jiraApprovalInstance.getId(), keyListInKeyValueCriteria);
    return keyListInKeyValueCriteria;
  }

  private String fetchKeysForApprovalInstanceInternal(
      JiraApprovalInstance jiraApprovalInstance, JiraIssueNG jiraIssueNG) {
    try {
      if (isNull(jiraApprovalInstance) || isNull(jiraIssueNG)
          || EmptyPredicate.isEmpty(jiraIssueNG.getFieldNameToKeys())) {
        log.warn(
            "Approval instance, jira issue or names map absent in Jira issue, skipping the Jira Approval optimization");
        return EMPTY_STRING;
      }
      Set<String> keysInKeyValueCriteria = new HashSet<>();

      // if any criteria is not key value criteria, then we can't determine the fields, all fields will be fetched
      // only fetch fields when approval criteria is valid key value criteria
      if (CriteriaSpecType.KEY_VALUES.equals(jiraApprovalInstance.getApprovalCriteria().getType())) {
        if (!isNull(jiraApprovalInstance.getApprovalCriteria().getCriteriaSpecDTO())) {
          keysInKeyValueCriteria.addAll(
              ((KeyValuesCriteriaSpecDTO) jiraApprovalInstance.getApprovalCriteria().getCriteriaSpecDTO())
                  .fetchKeySetFromKeyValueCriteriaDTO());
        }
      } else {
        return EMPTY_STRING;
      }

      // only fetch fields when rejection criteria is present and is valid key value criteria
      if (!isNull(jiraApprovalInstance.getRejectionCriteria())) {
        if (CriteriaSpecType.KEY_VALUES.equals(jiraApprovalInstance.getRejectionCriteria().getType())) {
          if (!isNull(jiraApprovalInstance.getRejectionCriteria().getCriteriaSpecDTO())) {
            keysInKeyValueCriteria.addAll(
                ((KeyValuesCriteriaSpecDTO) jiraApprovalInstance.getRejectionCriteria().getCriteriaSpecDTO())
                    .fetchKeySetFromKeyValueCriteriaDTO());
          }

        } else {
          return EMPTY_STRING;
        }
      }

      keysInKeyValueCriteria =
          keysInKeyValueCriteria.stream()
              .map(name -> mapFieldNameToKey(name, jiraIssueNG.getFieldNameToKeys(), jiraIssueNG.getFields()))
              .filter(StringUtils::isNotBlank)
              .collect(Collectors.toSet());

      // now keysInKeyValueCriteria can be empty only in negative cases like all key names were empty or
      // keys were empty or all keys weren't present in Jira issue. Hence, proceed by only fetching static fields

      // added these static fields as they are required in project, issue type validation, status update, etc.
      keysInKeyValueCriteria.addAll(JIRA_APPROVAL_STATIC_FIELDS);

      return Joiner.on(",").join(keysInKeyValueCriteria);
    } catch (Exception ex) {
      // log and ignore the error occurred while fetching keys list and return null so that all fields will be fetched.
      log.warn("Error fetching keys list while polling for jira approval", ex);
      return EMPTY_STRING;
    }
  }

  private static String mapFieldNameToKey(
      String name, Map<String, String> fieldNameToKeys, Map<String, Object> fields) {
    if (!fieldNameToKeys.containsKey(name) && fields.containsKey(name)) {
      // CASE: the field was missed from fieldNameToKeys mapping on delegate. We need to monitor this and skip
      // optimization.
      throw new IllegalArgumentException(String.format("Field present in issue but not in names map: %s", name));
    }

    if (!fieldNameToKeys.containsKey(name) && !fields.containsKey(name)) {
      // CASE: user has given a field which dne in the issue, we can skip the field and continue optimization
      log.warn("Ignoring the field {} as it is not present in jira issue", name);
      return null;
    }
    return fieldNameToKeys.get(name);
  }
}
