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
import software.wings.beans.SumoConfig;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.SumoConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class SumoConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private SumoConfigYamlHandler yamlHandler;

  public static final String url = "https://api.us2.sumologic.com/api/v1/";

  private String invalidYamlContent = "url_controlller: https://api.us2.sumologic.com/api/v1/\n"
      + "accessId: safeharness:pgWCawZUSnuZYi0lvgfQQQ\n"
      + "accessKey: safeharness:kdT-tC2dTNCyY2pJJzSN9A\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: SUMO\n";

  private Class yamlClass = SumoConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String sumoLogicProviderName = "SumoLogic" + System.currentTimeMillis();

    // 1. Create sumoLogic verification record
    SettingAttribute settingAttributeSaved = createSumoVerificationProvider(sumoLogicProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(sumoLogicProviderName);

    testCRUD(generateSettingValueYamlConfig(sumoLogicProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String sumoLogicProviderName = "SumoLogic" + System.currentTimeMillis();

    // 1. Create sumoLogic verification provider record
    SettingAttribute settingAttributeSaved = createSumoVerificationProvider(sumoLogicProviderName);
    testFailureScenario(generateSettingValueYamlConfig(sumoLogicProviderName, settingAttributeSaved));
  }

  private SettingAttribute createSumoVerificationProvider(String sumoProviderName) {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    SumoConfig sumoConfig = new SumoConfig();
    sumoConfig.setAccessId(createSecretText(ACCOUNT_ID, "accessId", acessId).toCharArray());
    sumoConfig.setAccessKey(createSecretText(ACCOUNT_ID, "accesskey", accesskey).toCharArray());
    sumoConfig.setSumoUrl(url);
    sumoConfig.setAccountId(ACCOUNT_ID);

    return settingsService.save(aSettingAttribute()
                                    .withCategory(SettingCategory.CONNECTOR)
                                    .withName(sumoProviderName)
                                    .withAccountId(ACCOUNT_ID)
                                    .withValue(sumoConfig)
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
        .configclazz(SumoConfig.class)
        .updateMethodName("setSumoUrl")
        .currentFieldValue(url)
        .build();
  }
}
