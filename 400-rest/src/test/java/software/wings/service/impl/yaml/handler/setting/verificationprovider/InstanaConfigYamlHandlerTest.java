/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.setting.verificationprovider;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.InstanaConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.yaml.handler.connectors.configyamlhandlers.SettingValueYamlConfig;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class InstanaConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private InstanaConfigYamlHandler yamlHandler;

  public static final String url = "https://plum-io0hp4gn.instana.io/";

  private String invalidYamlContent = "apiKey_instana: amazonkms:C7cBDpxHQzG5rv30tvZDgw\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: NEW_RELIC";

  private Class yamlClass = InstanaConfig.Yaml.class;

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String instanaProviderName = "instana-" + System.currentTimeMillis();

    SettingAttribute settingAttributeSaved = createInstanaProviderNameVerificationProvider(instanaProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(instanaProviderName);

    testCRUD(generateSettingValueYamlConfig(instanaProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String instanaProviderName = "newRelic" + System.currentTimeMillis();

    SettingAttribute settingAttributeSaved = createInstanaProviderNameVerificationProvider(instanaProviderName);
    testFailureScenario(generateSettingValueYamlConfig(instanaProviderName, settingAttributeSaved));
  }

  private SettingAttribute createInstanaProviderNameVerificationProvider(String instanaProviderName) {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);
    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
            .withName(instanaProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(InstanaConfig.builder()
                           .instanaUrl("https://instana-example.com/")
                           .accountId(ACCOUNT_ID)
                           .apiToken(createSecretText(ACCOUNT_ID, "apiToken", generateUuid()).toCharArray())
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
        .configclazz(InstanaConfig.class)
        .updateMethodName(null)
        .currentFieldValue(null)
        .build();
  }
}
