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
import io.harness.jira.JiraIssueNG;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.ConditionDTO;
import io.harness.steps.approval.step.beans.CriteriaSpecType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.beans.JexlCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.steps.shellscript.ShellType;

import com.mongodb.MongoException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
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
  private static final String EMPTY_STRING = "";
  private static final Map<String, String> namesMap = new HashMap<>();
  @Mock ApprovalInstanceService approvalInstanceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    namesMap.put("name_1", "key_1");
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

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenParamsNull() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(null, jiraIssueNG)).isEqualTo(EMPTY_STRING);

    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(JiraApprovalInstance.builder().build(), null))
        .isEqualTo(EMPTY_STRING);

    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(JiraApprovalInstance.builder().build(), jiraIssueNG))
        .isEqualTo(EMPTY_STRING);
    jiraIssueNG.setFieldNameToKeys(new HashMap<>());
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(JiraApprovalInstance.builder().build(), jiraIssueNG))
        .isEqualTo(EMPTY_STRING);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalJEXLCriteriaOnly() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();
    jiraIssueNG.setFieldNameToKeys(namesMap);
    JiraApprovalInstance jiraApprovalInstance =
        JiraApprovalInstance.builder().approvalCriteria(buildJEXLCriteria()).build();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG)).isEqualTo(EMPTY_STRING);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalJEXLCriteriaAndRejectionJEXLCriteria() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();
    jiraIssueNG.setFieldNameToKeys(namesMap);
    JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder()
                                                    .approvalCriteria(buildJEXLCriteria())
                                                    .rejectionCriteria(buildJEXLCriteria())
                                                    .build();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG)).isEqualTo(EMPTY_STRING);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalKeyCriteriaAndRejectionJEXLCriteria() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();
    jiraIssueNG.setFieldNameToKeys(namesMap);
    HashMap<String, String> criteria = new HashMap<>();
    criteria.put("key", "value");
    JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder()
                                                    .approvalCriteria(buildKeyValueCriteria(criteria))
                                                    .rejectionCriteria(buildJEXLCriteria())
                                                    .build();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG)).isEqualTo(EMPTY_STRING);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalJEXLCriteriaAndRejectionKeyCriteria() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();
    jiraIssueNG.setFieldNameToKeys(namesMap);
    HashMap<String, String> criteria = new HashMap<>();
    criteria.put("key", "value");
    JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder()
                                                    .approvalCriteria(buildJEXLCriteria())
                                                    .rejectionCriteria(buildKeyValueCriteria(criteria))
                                                    .build();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG)).isEqualTo(EMPTY_STRING);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalOrRejectionCriteriaIsNullKeyValue() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();
    jiraIssueNG.setFieldNameToKeys(namesMap);
    JiraApprovalInstance jiraApprovalInstance =
        JiraApprovalInstance.builder()
            .approvalCriteria(CriteriaSpecWrapperDTO.builder().type(CriteriaSpecType.KEY_VALUES).build())
            .build();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG).split(",").length)
        .isEqualTo(3);
    // rejection also has null key criteria
    jiraApprovalInstance =
        JiraApprovalInstance.builder()
            .approvalCriteria(CriteriaSpecWrapperDTO.builder().type(CriteriaSpecType.KEY_VALUES).build())
            .rejectionCriteria(CriteriaSpecWrapperDTO.builder().type(CriteriaSpecType.KEY_VALUES).build())
            .build();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG).split(",").length)
        .isEqualTo(3);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalCriteriaIsKeyValueAndBlankKeys() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();
    jiraIssueNG.setFieldNameToKeys(namesMap);
    HashMap<String, String> criteria = new HashMap<>();
    criteria.put("   ", "value");
    JiraApprovalInstance jiraApprovalInstance =
        JiraApprovalInstance.builder().approvalCriteria(buildKeyValueCriteria(criteria)).build();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG).split(",").length)
        .isEqualTo(3);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalCriteriaIsKeyValueWhenKeyMissedInNames() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();

    HashMap<String, Object> fields = new HashMap<>();
    fields.put("key1", "value1 in jira");
    jiraIssueNG.setFieldNameToKeys(namesMap);
    jiraIssueNG.setFields(fields);

    HashMap<String, String> criteria = new HashMap<>();
    criteria.put("key1", "value");
    criteria.put("key2", "value");
    criteria.put("    ", "this key will be ignored");
    JiraApprovalInstance jiraApprovalInstance =
        JiraApprovalInstance.builder().approvalCriteria(buildKeyValueCriteria(criteria)).build();
    assertThat(ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG)).isEqualTo(EMPTY_STRING);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalCriteriaIsKeyValueWhenKeyInvalidInCriteria() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();

    HashMap<String, Object> fields = new HashMap<>();
    fields.put("key1", "value1 in jira");
    HashMap<String, String> names = new HashMap<>();
    names.put("key1", "key_1");

    jiraIssueNG.setFieldNameToKeys(names);
    jiraIssueNG.setFields(fields);

    HashMap<String, String> criteria = new HashMap<>();
    criteria.put("key1", "value");
    criteria.put("key2", "value");
    criteria.put("    ", "this key will be ignored");
    JiraApprovalInstance jiraApprovalInstance =
        JiraApprovalInstance.builder().approvalCriteria(buildKeyValueCriteria(criteria)).build();
    // key2 will be ignored
    String result = ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    assertThat(result.split(",").length).isEqualTo(4);
    assertThat(result).contains("key_1");
    assertThat(result).contains("project");
    assertThat(result).contains("issuetype");
    assertThat(result).contains("status");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalCriteriaIsKeyValueWhenKeyNameHasBlankKey() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();

    HashMap<String, Object> fields = new HashMap<>();
    fields.put("key1", "value1 in jira");
    fields.put("key2", "value2 in jira");
    HashMap<String, String> names = new HashMap<>();
    names.put("key1", "key_1");
    names.put("key2", "   "); // key2 name has blank value

    jiraIssueNG.setFieldNameToKeys(names);
    jiraIssueNG.setFields(fields);

    HashMap<String, String> criteria = new HashMap<>();
    criteria.put("key1", "value");
    criteria.put("key2", "value");
    criteria.put("    ", "this key will be ignored");
    JiraApprovalInstance jiraApprovalInstance =
        JiraApprovalInstance.builder().approvalCriteria(buildKeyValueCriteria(criteria)).build();
    // key2 will be ignored
    String result = ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    assertThat(result.split(",").length).isEqualTo(4);
    assertThat(result).contains("key_1");
    assertThat(result).contains("project");
    assertThat(result).contains("issuetype");
    assertThat(result).contains("status");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchKeysForApprovalInstanceWhenApprovalCriteriaAndRejectionBothIsKeyValue() {
    JiraIssueNG jiraIssueNG = new JiraIssueNG();

    HashMap<String, Object> fields = new HashMap<>();
    fields.put("key1", "value1 in jira");
    fields.put("key2", "value2 in jira");
    fields.put("key3", "value3 in jira");
    HashMap<String, String> names = new HashMap<>();
    names.put("key1", "key_1");
    names.put("key2", "key_2");
    names.put("key3", "key_3");

    jiraIssueNG.setFieldNameToKeys(names);
    jiraIssueNG.setFields(fields);

    HashMap<String, String> criteria = new HashMap<>();
    criteria.put("key1", "value1");
    criteria.put("key3", "value3");
    criteria.put("    ", "this key will be ignored");

    HashMap<String, String> rejectionCriteria = new HashMap<>();
    criteria.put("key2", "value2");
    criteria.put("key3", "value3");
    criteria.put("    ", "this key will be ignored");

    JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder()
                                                    .approvalCriteria(buildKeyValueCriteria(criteria))
                                                    .rejectionCriteria(buildKeyValueCriteria(rejectionCriteria))
                                                    .build();
    // key2 will be ignored
    String result = ApprovalUtils.fetchKeysForApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    assertThat(result.split(",").length).isEqualTo(6);
    assertThat(result).contains("key_1");
    assertThat(result).contains("key_2");
    assertThat(result).contains("key_3");
    assertThat(result).contains("project");
    assertThat(result).contains("issuetype");
    assertThat(result).contains("status");
  }

  private CriteriaSpecWrapperDTO buildJEXLCriteria() {
    return CriteriaSpecWrapperDTO.builder()
        .type(CriteriaSpecType.JEXL)
        .criteriaSpecDTO(JexlCriteriaSpecDTO.builder().expression("JEXL").build())
        .build();
  }

  private CriteriaSpecWrapperDTO buildKeyValueCriteria(HashMap<String, String> keyValues) {
    return CriteriaSpecWrapperDTO.builder()
        .type(CriteriaSpecType.KEY_VALUES)
        .criteriaSpecDTO(KeyValuesCriteriaSpecDTO.builder()
                             .conditions(keyValues.entrySet()
                                             .stream()
                                             .map(entry
                                                 -> ConditionDTO.builder()
                                                        .key(entry.getKey())
                                                        .value(entry.getValue())
                                                        .operator(Operator.EQ)
                                                        .build())
                                             .collect(Collectors.toList()))
                             .matchAnyCondition(true)
                             .build())
        .build();
  }
}
