/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.INDER;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.beans.approval.JiraApprovalParams;
import software.wings.beans.approval.ServiceNowApprovalParams;
import software.wings.beans.servicenow.ServiceNowTicketType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDC)
public class ApprovalStepYamlBuilderTest extends StepYamlBuilderTestBase {
  @InjectMocks private ApprovalStepYamlBuilder approvalStepYamlBuilder;

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testNameToIdForHarnessApproval_UserGroupNamesAsList() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForHarnessApproval();
    inputPropertiesForHarnessApproval.put(USER_GROUP_NAMES, asList(USER_GROUP_NAME + 0, USER_GROUP_NAME + 1));
    Map<String, Object> outputProperties = new HashMap<>();

    inputPropertiesForHarnessApproval.forEach((name, value)
                                                  -> approvalStepYamlBuilder.convertNameToIdForKnownTypes(
                                                      name, value, outputProperties, APP_ID, ACCOUNT_ID, null));
    assertThat(outputProperties).containsAllEntriesOf(getInputPropertiesForHarnessApproval());
    assertThat(outputProperties).containsEntry(USER_GROUPS, asList(USER_GROUP_ID + 0, USER_GROUP_ID + 1));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testIdToNameForHarnessApproval_UserGroupNamesAsList() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForHarnessApproval();
    inputPropertiesForHarnessApproval.put(USER_GROUPS, asList(USER_GROUP_ID + 0, USER_GROUP_ID + 1));
    Map<String, Object> outputProperties = new HashMap<>();

    inputPropertiesForHarnessApproval.forEach(
        (name, value)
            -> approvalStepYamlBuilder.convertIdToNameForKnownTypes(name, value, outputProperties, APP_ID, null));
    assertThat(outputProperties).containsAllEntriesOf(getInputPropertiesForHarnessApproval());
    assertThat(outputProperties).containsEntry(USER_GROUP_NAMES, asList(USER_GROUP_NAME + 0, USER_GROUP_NAME + 1));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testNameToIdForHarnessApproval_UserGroupNamesAsExpression() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForHarnessApproval();
    inputPropertiesForHarnessApproval.put(USER_GROUP_NAMES, "${user_groups}");
    inputPropertiesForHarnessApproval.put(
        TEMPLATE_EXPRESSIONS, getTemplateExpressions("${user_groups}", USER_GROUP_NAMES));
    Map<String, Object> outputProperties = new HashMap<>();

    inputPropertiesForHarnessApproval.forEach((name, value)
                                                  -> approvalStepYamlBuilder.convertNameToIdForKnownTypes(
                                                      name, value, outputProperties, APP_ID, ACCOUNT_ID, null));
    assertThat(outputProperties).containsAllEntriesOf(getInputPropertiesForHarnessApproval());
    assertThat(outputProperties).containsEntry(USER_GROUPS, "${user_groups}");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testIdToNameForHarnessApproval_UserGroupNamesAsExpression() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForHarnessApproval();
    inputPropertiesForHarnessApproval.put(USER_GROUPS, "${user_groups}");
    inputPropertiesForHarnessApproval.put(
        TEMPLATE_EXPRESSIONS, getTemplateExpressions("${user_groups}", USER_GROUP_NAMES));
    Map<String, Object> outputProperties = new HashMap<>();

    inputPropertiesForHarnessApproval.forEach(
        (name, value)
            -> approvalStepYamlBuilder.convertIdToNameForKnownTypes(name, value, outputProperties, APP_ID, null));
    assertThat(outputProperties).containsAllEntriesOf(getInputPropertiesForHarnessApproval());
    assertThat(outputProperties).containsEntry(USER_GROUP_NAMES, "${user_groups}");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testNameToIdForHarnessApproval_UserGroupNamesAsEmptyList() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForHarnessApproval();
    inputPropertiesForHarnessApproval.put(USER_GROUP_NAMES, new ArrayList<>());
    Map<String, Object> outputProperties = new HashMap<>();

    assertThatThrownBy(()
                           -> inputPropertiesForHarnessApproval.forEach(
                               (name, value)
                                   -> approvalStepYamlBuilder.convertNameToIdForKnownTypes(
                                       name, value, outputProperties, APP_ID, ACCOUNT_ID, null)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User group names list cannot be empty.");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testNameToIdForJiraApproval() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForJiraApproval(true);
    Map<String, Object> outputProperties = new HashMap<>();

    inputPropertiesForHarnessApproval.forEach((name, value)
                                                  -> approvalStepYamlBuilder.convertNameToIdForKnownTypes(
                                                      name, value, outputProperties, APP_ID, ACCOUNT_ID, null));
    assertThat(outputProperties).containsExactlyInAnyOrderEntriesOf(getInputPropertiesForJiraApproval(false));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testIdToNameForJiraApproval() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForJiraApproval(false);

    Map<String, Object> outputProperties = new HashMap<>();
    inputPropertiesForHarnessApproval.forEach(
        (name, value)
            -> approvalStepYamlBuilder.convertIdToNameForKnownTypes(name, value, outputProperties, APP_ID, null));
    assertThat(outputProperties).containsExactlyInAnyOrderEntriesOf(getInputPropertiesForJiraApproval(true));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testNameToIdForSnowApproval() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForSnowApproval(true);
    Map<String, Object> outputProperties = new HashMap<>();

    inputPropertiesForHarnessApproval.forEach((name, value)
                                                  -> approvalStepYamlBuilder.convertNameToIdForKnownTypes(
                                                      name, value, outputProperties, APP_ID, ACCOUNT_ID, null));
    assertThat(outputProperties).containsExactlyInAnyOrderEntriesOf(getInputPropertiesForSnowApproval(false));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testIdToNameForSnowApproval() {
    Map<String, Object> inputPropertiesForHarnessApproval = getInputPropertiesForSnowApproval(false);
    Map<String, Object> outputProperties = new HashMap<>();

    inputPropertiesForHarnessApproval.forEach(
        (name, value)
            -> approvalStepYamlBuilder.convertIdToNameForKnownTypes(name, value, outputProperties, APP_ID, null));
    assertThat(outputProperties).containsExactlyInAnyOrderEntriesOf(getInputPropertiesForSnowApproval(true));
  }

  private Map<String, Object> getInputPropertiesForHarnessApproval() {
    HashMap<String, Object> inputProperties = new HashMap<>();
    inputProperties.put("approvalStateType", "USER_GROUP");
    inputProperties.put("timeoutMillis", 864000);
    return inputProperties;
  }

  private Map<String, Object> getInputPropertiesForJiraApproval(boolean withName) {
    HashMap<String, Object> inputProperties = new HashMap<>();
    inputProperties.put("approvalStateType", "JIRA");
    inputProperties.put("timeoutMillis", 864000);
    Map<String, Object> jiraApprovalParams = getJiraApprovalParamsMap();
    if (withName) {
      jiraApprovalParams.put(JIRA_CONNECTOR_NAME, JIRA_CONNECTOR_NAME);
      jiraApprovalParams.remove(JIRA_CONNECTOR_ID);
    }
    Map<String, Object> approvalStateParams = new HashMap<>();
    approvalStateParams.put(JIRA_APPROVAL_PARAMS, jiraApprovalParams);
    inputProperties.put(APPROVAL_STATE_PARAMS, approvalStateParams);
    return inputProperties;
  }

  private Map<String, Object> getJiraApprovalParamsMap() {
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setJiraConnectorId(JIRA_CONNECTOR_ID);
    jiraApprovalParams.setApprovalField("status");
    jiraApprovalParams.setApprovalValue("Done");
    jiraApprovalParams.setIssueId("1234");
    jiraApprovalParams.setProject("project");
    return JsonUtils.asMap(JsonUtils.asJson(jiraApprovalParams));
  }

  private Map<String, Object> getInputPropertiesForSnowApproval(boolean withName) {
    HashMap<String, Object> inputProperties = new HashMap<>();
    inputProperties.put("approvalStateType", "JIRA");
    inputProperties.put("timeoutMillis", 864000);
    Map<String, Object> snowApprovalParams = getSnowApprovalParamsMap();
    if (withName) {
      snowApprovalParams.put(SNOW_CONNECTOR_NAME, SNOW_CONNECTOR_NAME);
      snowApprovalParams.remove(SNOW_CONNECTOR_ID);
    }
    Map<String, Object> approvalStateParams = new HashMap<>();
    approvalStateParams.put(SNOW_APPROVAL_PARAMS, snowApprovalParams);
    inputProperties.put(APPROVAL_STATE_PARAMS, approvalStateParams);
    return inputProperties;
  }

  private Map<String, Object> getSnowApprovalParamsMap() {
    ServiceNowApprovalParams snowApprovalParams = new ServiceNowApprovalParams();
    snowApprovalParams.setSnowConnectorId(SNOW_CONNECTOR_ID);
    snowApprovalParams.setTicketType(ServiceNowTicketType.INCIDENT);
    snowApprovalParams.setChangeWindowEndField("false");
    snowApprovalParams.setIssueNumber("1234");
    return JsonUtils.asMap(JsonUtils.asJson(snowApprovalParams));
  }
}
