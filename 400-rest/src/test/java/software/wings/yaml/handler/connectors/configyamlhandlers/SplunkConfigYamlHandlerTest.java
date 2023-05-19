/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.SplunkConfig;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.SplunkConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

/**/

public class SplunkConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private SplunkConfigYamlHandler yamlHandler;

  public static final String url = "https://ec2-52-54-103-49.compute-1.amazonaws.com:8089";

  private String invalidYamlContent = "splunk_controllerUrl: https://ec2-52-54-103-49.compute-1.amazonaws.com:8089\n"
      + "username: username\n"
      + "password: amazonkms:#\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: SPLUNK";

  private Class yamlClass = SplunkConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String splunkProviderName = "Splunk" + System.currentTimeMillis();

    // 1. Create newRelic verification record
    SettingAttribute settingAttributeSaved = createSplunkVerificationProvider(splunkProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(splunkProviderName);

    testCRUD(generateSettingValueYamlConfig(splunkProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String splunkProviderName = "Splunk" + System.currentTimeMillis();

    // 1. Create newRelic verification provider record
    SettingAttribute settingAttributeSaved = createSplunkVerificationProvider(splunkProviderName);
    testFailureScenario(generateSettingValueYamlConfig(splunkProviderName, settingAttributeSaved));
  }

  private SettingAttribute createSplunkVerificationProvider(String splunkProviderName) {
    // Generate Splunk verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(splunkProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(SplunkConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .splunkUrl(url)
                           .username(userName)
                           .password(createSecretText(ACCOUNT_ID, "password", password).toCharArray())
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
        .configclazz(SplunkConfig.class)
        .updateMethodName("setSplunkUrl")
        .currentFieldValue(url)
        .build();
  }
}
