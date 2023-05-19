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

import software.wings.beans.BambooConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.artifactserver.BambooConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class BambooConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private BambooConfigYamlHandler yamlHandler;

  public static final String url = "http://ec2-34-205-16-35.compute-1.amazonaws.com:8085/";

  private String invalidYamlContent = "url_invalid: http://ec2-34-205-16-35.compute-1.amazonaws.com:8085/\n"
      + "username: username\n"
      + "password: safeharness:2VX9g3DkTFa63TuO6rI8rQ\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: BAMBOO";

  private Class yamlClass = BambooConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String bambooProviderName = "Bamboo" + System.currentTimeMillis();

    // 1. Create Bamboo verification record
    SettingAttribute settingAttributeSaved = createBambooVerificationProvider(bambooProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(bambooProviderName);

    testCRUD(generateSettingValueYamlConfig(bambooProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String bambooProviderName = "Bamboo" + System.currentTimeMillis();

    // 1. Create Bamboo verification provider record
    SettingAttribute settingAttributeSaved = createBambooVerificationProvider(bambooProviderName);
    testFailureScenario(generateSettingValueYamlConfig(bambooProviderName, settingAttributeSaved));
  }

  private SettingAttribute createBambooVerificationProvider(String bambooProviderName) {
    // Generate Bamboo verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(bambooProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(BambooConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .bambooUrl(url)
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
        .yamlDirPath(artifactServerYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(BambooConfig.class)
        .updateMethodName("setBambooUrl")
        .currentFieldValue(url)
        .build();
  }
}
