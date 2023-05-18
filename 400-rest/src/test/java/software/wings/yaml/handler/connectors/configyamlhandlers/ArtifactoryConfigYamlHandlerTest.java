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
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.service.impl.yaml.handler.setting.artifactserver.ArtifactoryConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class ArtifactoryConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private ArtifactoryConfigYamlHandler yamlHandler;

  public static final String URL = "https://harness.jfrog.io/harness";

  private String invalidYamlContent = "url_invalid: https://harness.jfrog.io/harness\n"
      + "username: admin\n"
      + "password: safeharness:JAhmPyeCQYaVVRO4YULw6A\n"
      + "harnessApiVersion: '1.0'\n"
      + "delegateSelectors:\n"
      + "- primary\n"
      + "type: ARTIFACTORY";

  private Class yamlClass = ArtifactoryConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String artifactoryProviderName = "Artifactory" + System.currentTimeMillis();

    // 1. Create Artifactory verification record
    SettingAttribute settingAttributeSaved = createArtifactoryVerificationProvider(artifactoryProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(artifactoryProviderName);

    testCRUD(generateSettingValueYamlConfig(artifactoryProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String artifactoryProviderName = "Artifactory" + System.currentTimeMillis();

    // 1. Create Artifactory verification provider record
    SettingAttribute settingAttributeSaved = createArtifactoryVerificationProvider(artifactoryProviderName);
    testFailureScenario(generateSettingValueYamlConfig(artifactoryProviderName, settingAttributeSaved));
  }

  private SettingAttribute createArtifactoryVerificationProvider(String ArtifactoryProviderName) {
    // Generate Artifactory verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(ArtifactoryProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(ArtifactoryConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .artifactoryUrl(URL)
                           .username(userName)
                           .password(createSecretText(ACCOUNT_ID, "password", password).toCharArray())
                           .delegateSelectors(Lists.newArrayList("primary"))
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
        .configclazz(ArtifactoryConfig.class)
        .updateMethodName("setArtifactoryUrl")
        .currentFieldValue(URL)
        .build();
  }
}
