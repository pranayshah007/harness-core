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
import software.wings.beans.config.NexusConfig;
import software.wings.service.impl.yaml.handler.setting.artifactserver.NexusConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class NexusConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private NexusConfigYamlHandler yamlHandler;

  public static final String URL = "https://nexus.wings.software/";

  private String invalidYamlContent = "url_Nexus: https://nexus.wings.software\n"
      + "username: admin\n"
      + "password: safeharness:Q1ESI1KVTrCaBARuR38kqA\n"
      + "harnessApiVersion: '1.0'\n"
      + "version: 3.x\n"
      + "delegateSelectors:\n"
      + "- nexus\n"
      + "- harness\n"
      + "type: NEXUS";

  private Class yamlClass = NexusConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String nexusProviderName = "Nexus" + System.currentTimeMillis();

    // 1. Create Nexus verification record
    SettingAttribute settingAttributeSaved = createNexusVerificationProvider(nexusProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(nexusProviderName);

    testCRUD(generateSettingValueYamlConfig(nexusProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String nexusProviderName = "Nexus" + System.currentTimeMillis();

    // 1. Create Nexus verification provider record
    SettingAttribute settingAttributeSaved = createNexusVerificationProvider(nexusProviderName);
    testFailureScenario(generateSettingValueYamlConfig(nexusProviderName, settingAttributeSaved));
  }

  private SettingAttribute createNexusVerificationProvider(String nexusProviderName) {
    // Generate Nexus verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(nexusProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(NexusConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .nexusUrl(URL)
                           .username(userName)
                           .password(createSecretText(ACCOUNT_ID, "password", password).toCharArray())
                           .delegateSelectors(Lists.newArrayList("nexus", "harness"))
                           .version("3.x")
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
        .configclazz(NexusConfig.class)
        .updateMethodName("setNexusUrl")
        .currentFieldValue(URL)
        .build();
  }
}
