/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.DynaTraceConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.DynaTraceConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

/**
 * Created by rsingh on 2/12/18.
 */
public class DynaTraceConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private DynaTraceConfigYamlHandler yamlHandler;

  public static final String url = "https://bdv73347.live.dynatrace.com";

  private String invalidYamlContent = "apiToken: amazonkms:C7cBDpxHQzG5rv30tvZDgw\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: DYNA_TRACE";

  private Class yamlClass = DynaTraceConfig.DynaTraceYaml.class;

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String dynatraceProviderName = "dynaTrace" + System.currentTimeMillis();

    // 1. Create dynatrace verification record
    SettingAttribute settingAttributeSaved = createDynaTraceProviderNameVerificationProvider(dynatraceProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(dynatraceProviderName);

    testCRUD(generateSettingValueYamlConfig(dynatraceProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String dynatraceProviderName = "dynaTrace" + System.currentTimeMillis();

    // 1. Create dynatrace verification provider record
    SettingAttribute settingAttributeSaved = createDynaTraceProviderNameVerificationProvider(dynatraceProviderName);
    testFailureScenario(generateSettingValueYamlConfig(dynatraceProviderName, settingAttributeSaved));
  }

  private SettingAttribute createDynaTraceProviderNameVerificationProvider(String dynaTraceProviderName) {
    // Generate dynatrace verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(dynaTraceProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(DynaTraceConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .dynaTraceUrl(url)
                           .apiToken(createSecretText(ACCOUNT_ID, "apiKey", apiKey).toCharArray())
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
        .configclazz(DynaTraceConfig.class)
        .updateMethodName(null)
        .currentFieldValue(null)
        .build();
  }
}
