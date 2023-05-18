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
import software.wings.beans.config.LogzConfig;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.LogzConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class LogzConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private LogzConfigYamlHandler yamlHandler;

  public static final String url = "https://wingsnfr.saas.appdynamics.com:443/controller";

  private String invalidYamlContent = "url_controller: http://localhost\n"
      + "token : safeharness:kdT-tC2dTNCyY2pJJzSN9A\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: Logz";

  private Class yamlClass = LogzConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String logzProviderName = "Logz" + System.currentTimeMillis();

    // 1. Create Logz verification record
    SettingAttribute settingAttributeSaved = createLogzVerificationProvider(logzProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(logzProviderName);

    testCRUD(generateSettingValueYamlConfig(logzProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String logzProviderName = "Logz" + System.currentTimeMillis();

    // 1. Create Logz verification provider record
    SettingAttribute settingAttributeSaved = createLogzVerificationProvider(logzProviderName);
    testFailureScenario(generateSettingValueYamlConfig(logzProviderName, settingAttributeSaved));
  }

  private SettingAttribute createLogzVerificationProvider(String logzProviderName) {
    // Generate Logz verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    LogzConfig logzConfig = new LogzConfig();
    logzConfig.setAccountId(ACCOUNT_ID);
    logzConfig.setLogzUrl(url);
    logzConfig.setToken(createSecretText(ACCOUNT_ID, "token", token).toCharArray());

    return settingsService.save(aSettingAttribute()
                                    .withCategory(SettingCategory.CONNECTOR)
                                    .withName(logzProviderName)
                                    .withAccountId(ACCOUNT_ID)
                                    .withValue(logzConfig)
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
        .configclazz(LogzConfig.class)
        .updateMethodName("setLogzUrl")
        .currentFieldValue(url)
        .build();
  }
}
