/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.GEORGE;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.JiraConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.collaborationprovider.JiraConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class JiraConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private JiraConfigYamlHandler yamlHandler;

  public static final String url = "jira.com";

  private String invalidYamlContent = "host_invalid: jira.com\n"
      + "username: support@harness.io\n"
      + "password: safeharness:DBAtpYCHSx2fPG8MIFQFmA\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: JIRA";

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String name = "JIRA" + System.currentTimeMillis();

    // 1. Create JIRA record
    SettingAttribute settingAttributeSaved = createJIRAVerificationProvider(name);
    assertThat(settingAttributeSaved.getName()).isEqualTo(name);

    testCRUD(generateSettingValueYamlConfig(name, settingAttributeSaved));
  }

  private SettingAttribute createJIRAVerificationProvider(String name) {
    // Generate JIRA connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(name)
            .withAccountId(ACCOUNT_ID)
            .withValue(JiraConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .baseUrl(url)
                           .username(userName)
                           .password(createSecretText(ACCOUNT_ID, "password", password).toCharArray())
                           .build())
            .build());
  }

  private SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(JiraConfig.Yaml.class)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(collaborationProviderYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(JiraConfig.class)
        .updateMethodName("setBaseUrl")
        .currentFieldValue(url)
        .build();
  }
}
