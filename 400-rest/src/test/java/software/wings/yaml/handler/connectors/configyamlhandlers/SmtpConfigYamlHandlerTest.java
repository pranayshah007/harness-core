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
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.service.impl.yaml.handler.setting.collaborationprovider.SmtpConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class SmtpConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private SmtpConfigYamlHandler yamlHandler;

  public static final String url = "smtp.gmail.com";

  private String invalidYamlContent = "host_invalid: smtp.gmail.com\n"
      + "port: 465\n"
      + "fromAddress: support@harness.io\n"
      + "useSSL: true\n"
      + "username: support@harness.io\n"
      + "password: safeharness:DBAtpYCHSx2fPG8MIFQFmA\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: SMTP";

  private Class yamlClass = SmtpConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String name = "SMTP" + System.currentTimeMillis();

    // 1. Create SMTP record
    SettingAttribute settingAttributeSaved = createSMTPVerificationProvider(name);
    assertThat(settingAttributeSaved.getName()).isEqualTo(name);

    testCRUD(generateSettingValueYamlConfig(name, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String name = "SMTP" + System.currentTimeMillis();

    // 1. Create SMTP record
    SettingAttribute settingAttributeSaved = createSMTPVerificationProvider(name);
    testFailureScenario(generateSettingValueYamlConfig(name, settingAttributeSaved));
  }

  private SettingAttribute createSMTPVerificationProvider(String name) {
    // Generate SMTP connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(name)
            .withAccountId(ACCOUNT_ID)
            .withValue(SmtpConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .host(url)
                           .port(4403)
                           .username(userName)
                           .password(createSecretText(ACCOUNT_ID, "password", password).toCharArray())
                           .useSSL(true)
                           .fromAddress("support@harness.io")
                           .build())
            .build());
  }

  private SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(yamlClass)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(collaborationProviderYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(SmtpConfig.class)
        .updateMethodName("setHost")
        .currentFieldValue(url)
        .build();
  }
}
