/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.SettingAttribute;
import software.wings.beans.approval.ApprovalStateParams.ApprovalStateParamsKeys;
import software.wings.beans.security.UserGroup;
import software.wings.beans.yaml.ChangeContext;
import software.wings.exception.IncompleteStateException;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.sm.states.ApprovalState.ApprovalStateKeys;
import software.wings.yaml.workflow.StepYaml;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(CDC)
@Slf4j
public class ApprovalStepYamlBuilder extends StepYamlBuilder {
  private static final String USER_GROUPS = "userGroups";
  private static final String USER_GROUP_NAMES = "userGroupNames";
  private static final String JIRA_CONNECTOR_ID = "jiraConnectorId";
  private static final String JIRA_CONNECTOR_NAME = "jiraConnectorName";
  private static final String SNOW_CONNECTOR_ID = "snowConnectorId";
  private static final String SNOW_CONNECTOR_NAME = "snowConnectorName";
  private static final String TEMPLATE_EXPRESSIONS = "templateExpressions";

  @Inject private UserGroupService userGroupService;
  @Inject private SettingsService settingsService;

  @Override
  public void validate(ChangeContext<StepYaml> changeContext) {
    List<HashMap<String, Object>> templateExpressions = null;
    try {
      templateExpressions = (List<HashMap<String, Object>>) getAndSanitizeTemplateExpressions(changeContext.getYaml());

    } catch (ClassCastException e) {
      throw new InvalidRequestException(
          String.format("Template expressions in step [%s]", changeContext.getYaml().getName()));
    }
    if (!isEmpty(templateExpressions)) {
      for (HashMap<String, Object> templateExpression : templateExpressions) {
        if (templateExpression.get("expression") == null) {
          throw new IncompleteStateException("Expression cannot be empty in templatized step");
        }
      }
    }
  }

  private Object getAndSanitizeTemplateExpressions(StepYaml yaml) {
    Object value = yaml.getProperties().get(TEMPLATE_EXPRESSIONS);
    if (value instanceof String) {
      if (isEmpty((String) value) || value.equals("''")) {
        yaml.getProperties().put(TEMPLATE_EXPRESSIONS, null);
        value = null;
      }
    }
    return value;
  }

  @Override
  public void convertIdToNameForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, Map<String, Object> inputProperties) {
    if (USER_GROUPS.equals(name)) {
      if (objectValue instanceof List) {
        List<String> userGroupIds = (List<String>) objectValue;
        List<String> userGroupNames = new ArrayList<>();
        userGroupIds.forEach(userGroupId -> {
          UserGroup userGroup = userGroupService.get(userGroupId);
          notNullCheck(String.format("User group with id %s doesnot exist.", userGroupId), userGroup);
          userGroupNames.add(userGroup.getName());
        });
        outputProperties.put(USER_GROUP_NAMES, userGroupNames);
      } else {
        outputProperties.put(USER_GROUP_NAMES, objectValue);
      }
      return;
    }

    if (ApprovalStateKeys.approvalStateParams.equals(name)) {
      Map<String, Object> approvalStateParams = (Map<String, Object>) objectValue;
      if (approvalStateParams.containsKey(ApprovalStateParamsKeys.jiraApprovalParams)) {
        Map<String, Object> jiraApprovalParams =
            (Map<String, Object>) approvalStateParams.get(ApprovalStateParamsKeys.jiraApprovalParams);
        String jiraConnectorId = (String) jiraApprovalParams.get(JIRA_CONNECTOR_ID);
        SettingAttribute jiraConnectionAttribute = settingsService.get(jiraConnectorId);
        notNullCheck("Jira connector does not exist.", jiraConnectionAttribute);
        jiraApprovalParams.remove(JIRA_CONNECTOR_ID);
        jiraApprovalParams.put(JIRA_CONNECTOR_NAME, jiraConnectionAttribute.getName());
      }
      if (approvalStateParams.containsKey(ApprovalStateParamsKeys.serviceNowApprovalParams)) {
        Map<String, Object> snowApprovalParams =
            (Map<String, Object>) approvalStateParams.get(ApprovalStateParamsKeys.serviceNowApprovalParams);
        String snowConnectorId = (String) snowApprovalParams.get(SNOW_CONNECTOR_ID);
        SettingAttribute snowConnectionAttribute = settingsService.get(snowConnectorId);
        notNullCheck("ServiceNow connector does not exist.", snowConnectionAttribute);
        snowApprovalParams.remove(SNOW_CONNECTOR_ID);
        snowApprovalParams.put(SNOW_CONNECTOR_NAME, snowConnectionAttribute.getName());
      }
      outputProperties.put(name, approvalStateParams);
      return;
    }
    outputProperties.put(name, objectValue);
  }

  @Override
  public void convertNameToIdForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, String accountId, Map<String, Object> inputProperties) {
    if (USER_GROUP_NAMES.equals(name)) {
      if (objectValue instanceof List) {
        List<String> userGroupNames = (List<String>) objectValue;
        if (isEmpty(userGroupNames)) {
          throw new InvalidRequestException("User group names list cannot be empty.");
        }
        List<String> userGroupIds = new ArrayList<>();
        for (String userGroupName : userGroupNames) {
          UserGroup userGroup = userGroupService.fetchUserGroupByName(accountId, userGroupName);
          notNullCheck("User group " + userGroupName + " doesn't exist", userGroup);
          userGroupIds.add(userGroup.getUuid());
        }
        outputProperties.put(USER_GROUPS, userGroupIds);
      } else {
        outputProperties.put(USER_GROUPS, objectValue);
      }
      return;
    }

    if (ApprovalStateKeys.approvalStateParams.equals(name)) {
      Map<String, Object> approvalStateParams = (Map<String, Object>) objectValue;
      if (approvalStateParams.containsKey(ApprovalStateParamsKeys.jiraApprovalParams)) {
        Map<String, Object> jiraApprovalParams =
            (Map<String, Object>) approvalStateParams.get(ApprovalStateParamsKeys.jiraApprovalParams);
        if (jiraApprovalParams.containsKey(JIRA_CONNECTOR_ID)) {
          log.info(YAML_ID_LOG, "JIRA APPROVAL", accountId);
        }
        if (jiraApprovalParams.containsKey(JIRA_CONNECTOR_NAME)) {
          String jiraConnectorName = (String) jiraApprovalParams.get(JIRA_CONNECTOR_NAME);
          SettingAttribute jiraSettingAttribute =
              settingsService.getSettingAttributeByName(accountId, jiraConnectorName);
          notNullCheck(String.format("Jira connector %s does not exist.", jiraConnectorName), jiraSettingAttribute);
          jiraApprovalParams.remove(JIRA_CONNECTOR_NAME);
          jiraApprovalParams.put(JIRA_CONNECTOR_ID, jiraSettingAttribute.getUuid());
        }
      }
      if (approvalStateParams.containsKey(ApprovalStateParamsKeys.serviceNowApprovalParams)) {
        Map<String, Object> snowApprovalParams =
            (Map<String, Object>) approvalStateParams.get(ApprovalStateParamsKeys.serviceNowApprovalParams);
        if (snowApprovalParams.containsKey(SNOW_CONNECTOR_ID)) {
          log.info(YAML_ID_LOG, "SNOW APPROVAL", accountId);
        }
        if (snowApprovalParams.containsKey(SNOW_CONNECTOR_NAME)) {
          String snowConnectorName = (String) snowApprovalParams.get(SNOW_CONNECTOR_NAME);
          SettingAttribute snowSettingAttribute =
              settingsService.getSettingAttributeByName(accountId, snowConnectorName);
          notNullCheck(
              String.format("ServiceNow connector %s does not exist.", snowConnectorName), snowSettingAttribute);
          snowApprovalParams.remove(SNOW_CONNECTOR_NAME);
          snowApprovalParams.put(SNOW_CONNECTOR_ID, snowSettingAttribute.getUuid());
        }
      }
      outputProperties.put(name, approvalStateParams);
      return;
    }

    if (USER_GROUPS.equals(name)) {
      log.info(YAML_ID_LOG, "HARNESS APPROVAL", accountId);
    }
    outputProperties.put(name, objectValue);
  }
}
