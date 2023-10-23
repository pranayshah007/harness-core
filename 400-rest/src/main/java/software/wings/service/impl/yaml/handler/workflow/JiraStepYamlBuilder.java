/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
public class JiraStepYamlBuilder extends StepYamlBuilder {
  private static final String JIRA_CONNECTOR_ID = "jiraConnectorId";
  private static final String JIRA_CONNECTOR_NAME = "jiraConnectorName";

  @Inject private SettingsService settingsService;

  @Override
  public void convertIdToNameForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, Map<String, Object> inputProperties) {
    if (JIRA_CONNECTOR_ID.equals(name)) {
      String jiraConnectorId = (String) objectValue;
      SettingAttribute jiraConnectionAttribute = settingsService.get(jiraConnectorId);
      notNullCheck("Jira ConnectorDisconnectHandler does not exist.", jiraConnectionAttribute);
      outputProperties.put(JIRA_CONNECTOR_NAME, jiraConnectionAttribute.getName());
      return;
    }
    outputProperties.put(name, objectValue);
  }

  @Override
  public void convertNameToIdForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, String accountId, Map<String, Object> inputProperties) {
    if (JIRA_CONNECTOR_NAME.equals(name)) {
      String jiraConnectorName = (String) objectValue;
      SettingAttribute jiraConnectionAttribute =
          settingsService.getSettingAttributeByName(accountId, jiraConnectorName);
      notNullCheck(String.format("Jira ConnectorDisconnectHandler %s does not exist.", jiraConnectorName), jiraConnectionAttribute);
      outputProperties.put(JIRA_CONNECTOR_ID, jiraConnectionAttribute.getUuid());
      return;
    }
    if (JIRA_CONNECTOR_ID.equals(name)) {
      log.info(YAML_ID_LOG, "JIRA", accountId);
    }
    outputProperties.put(name, objectValue);
  }
}
