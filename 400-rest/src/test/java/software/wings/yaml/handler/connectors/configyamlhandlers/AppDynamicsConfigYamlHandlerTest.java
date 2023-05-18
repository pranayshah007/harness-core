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

import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.AppDynamicsConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class AppDynamicsConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private AppDynamicsConfigYamlHandler yamlHandler;

  public static final String url = "https://wingsnfr.saas.appdynamics.com:443/controller";

  private String invalidYamlContent = "username_appd: username\n"
      + "password: amazonkms:zsj_HWfkSF-3li3W-9acHA\n"
      + "accountName: accountName\n"
      + "controllerUrl: https://wingsnfr.saas.appdynamics.com:443/controller\n"
      + "type: APP_DYNAMICS";

  private Class yamlClass = AppDynamicsConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String appdProviderName = "Appdynamics" + System.currentTimeMillis();

    // 1. Create Appdynamics verification record
    SettingAttribute settingAttributeSaved = createAppdynamicsVerificationProvider(appdProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(appdProviderName);

    testCRUD(generateSettingValueYamlConfig(appdProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String appdProviderName = "Appdynamics" + System.currentTimeMillis();

    // 1. Create appdynamics verification provider record
    SettingAttribute settingAttributeSaved = createAppdynamicsVerificationProvider(appdProviderName);
    testFailureScenario(generateSettingValueYamlConfig(appdProviderName, settingAttributeSaved));
  }

  private SettingAttribute createAppdynamicsVerificationProvider(String appdProviderName) {
    // Generate appdynamics verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(appdProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(AppDynamicsConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .controllerUrl(url)
                           .username(userName)
                           .password(createSecretText(ACCOUNT_ID, "password", password).toCharArray())
                           .accountname(accountName)
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
        .configclazz(AppDynamicsConfig.class)
        .updateMethodName("setControllerUrl")
        .currentFieldValue(url)
        .build();
  }
}
