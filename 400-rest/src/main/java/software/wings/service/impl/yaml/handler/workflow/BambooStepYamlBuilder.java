/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(CDC)
@Slf4j
public class BambooStepYamlBuilder extends StepYamlBuilder {
  private static final String BAMBOO_CONFIG_ID = "bambooConfigId";
  private static final String BAMBOO_CONFIG_NAME = "bambooConfigName";

  @Inject private SettingsService settingsService;

  @Override
  public void convertIdToNameForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, Map<String, Object> inputProperties) {
    if (BAMBOO_CONFIG_ID.equals(name)) {
      String bambooConfigId = (String) objectValue;
      SettingAttribute bambooConfig = settingsService.get(bambooConfigId);
      notNullCheck("Bamboo ConnectorDisconnectHandler does not exist.", bambooConfig);
      outputProperties.put(BAMBOO_CONFIG_NAME, bambooConfig.getName());
      return;
    }
    outputProperties.put(name, objectValue);
  }

  @Override
  public void convertNameToIdForKnownTypes(String name, Object objectValue, Map<String, Object> outputProperties,
      String appId, String accountId, Map<String, Object> inputProperties) {
    if (BAMBOO_CONFIG_NAME.equals(name)) {
      String bambooConfigName = (String) objectValue;
      SettingAttribute bambooConfig = settingsService.getSettingAttributeByName(accountId, bambooConfigName);
      notNullCheck(String.format("Bamboo ConnectorDisconnectHandler %s does not exist.", bambooConfigName), bambooConfig);
      outputProperties.put(BAMBOO_CONFIG_ID, bambooConfig.getUuid());
      return;
    }
    if (BAMBOO_CONFIG_ID.equals(name)) {
      log.info(YAML_ID_LOG, "BAMBOO", accountId);
    }
    outputProperties.put(name, objectValue);
  }
}
