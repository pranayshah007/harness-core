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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.DockerConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.artifactserver.DockerRegistryConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class DockerRegistryConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private DockerRegistryConfigYamlHandler yamlHandler;

  public static final String url = "https://registry.hub.docker.com/v2/";

  private String invalidYamlContent = "url_Docker: https://registry.hub.docker.com/v2/\n"
      + "username: wingsplugins\n"
      + "password: safeharness:pUnshzJMSJuOlNIusxanfw\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: DOCKER";

  private Class yamlClass = DockerConfig.Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String dockerProviderName = "Docker" + System.currentTimeMillis();

    // 1. Create Docker verification record
    SettingAttribute settingAttributeSaved = createDockerVerificationProvider(dockerProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(dockerProviderName);

    testCRUD(generateSettingValueYamlConfig(dockerProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String dockerProviderName = "Docker" + System.currentTimeMillis();

    // 1. Create Docker verification provider record
    SettingAttribute settingAttributeSaved = createDockerVerificationProvider(dockerProviderName);
    testFailureScenario(generateSettingValueYamlConfig(dockerProviderName, settingAttributeSaved));
  }

  private SettingAttribute createDockerVerificationProvider(String docketRegistryName) {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(docketRegistryName)
            .withAccountId(ACCOUNT_ID)
            .withValue(DockerConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .dockerRegistryUrl(url)
                           .username(userName)
                           .delegateSelectors(Collections.singletonList("K8s"))
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
        .configclazz(DockerConfig.class)
        .updateMethodName("setDockerRegistryUrl")
        .currentFieldValue(url)
        .build();
  }
}
