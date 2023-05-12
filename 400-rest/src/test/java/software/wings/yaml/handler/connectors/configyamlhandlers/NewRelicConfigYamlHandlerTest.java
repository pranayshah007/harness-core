/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.NewRelicConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.NewRelicConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class NewRelicConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private NewRelicConfigYamlHandler yamlHandler;

  public static final String url = "https://api.newrelic.com";

  private String invalidYamlContent = "apiKey_newrelic: amazonkms:C7cBDpxHQzG5rv30tvZDgw\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: NEW_RELIC";

  private Class yamlClass = NewRelicConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String newRelicProviderName = "newRelic" + System.currentTimeMillis();

    // 1. Create newRelic verification record
    SettingAttribute settingAttributeSaved = createNewRelicProviderNameVerificationProvider(newRelicProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(newRelicProviderName);

    testCRUD(generateSettingValueYamlConfig(newRelicProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String newRelicProviderName = "newRelic" + System.currentTimeMillis();

    // 1. Create newRelic verification provider record
    SettingAttribute settingAttributeSaved = createNewRelicProviderNameVerificationProvider(newRelicProviderName);
    testFailureScenario(generateSettingValueYamlConfig(newRelicProviderName, settingAttributeSaved));
  }

  private SettingAttribute createNewRelicProviderNameVerificationProvider(String newRelicProviderName) {
    // Generate newRelic verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(newRelicProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(NewRelicConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .newRelicUrl(url)
                           .apiKey(createSecretText(ACCOUNT_ID, generateUuid(), apiKey).toCharArray())
                           .build())
            .build());
  }

  private SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(yamlClass)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(verificationProviderYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(NewRelicConfig.class)
        .updateMethodName(null)
        .currentFieldValue(null)
        .build();
  }
}
