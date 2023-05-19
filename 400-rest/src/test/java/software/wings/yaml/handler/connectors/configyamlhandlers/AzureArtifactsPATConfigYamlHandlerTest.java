/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.scm.SecretName;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.service.impl.yaml.handler.setting.artifactserver.AzureArtifactsPATConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class AzureArtifactsPATConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @Inject YamlHandlersSecretGeneratorHelper yamlHandlersSecretGeneratorHelper;
  @InjectMocks @Inject private AzureArtifactsPATConfigYamlHandler yamlHandler;

  private Class yamlClass = AzureArtifactsPATConfig.Yaml.class;
  private static final String azureDevopsUrl = "http://dev.azure.com/garvit-test";
  private static final String AZURE_ARTIFACTS_SETTING_NAME = "azure-artifacts";

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String azureArtifactsSettingName = AZURE_ARTIFACTS_SETTING_NAME + System.currentTimeMillis();
    SettingAttribute settingAttributeSaved = createAzureArtifactsConnector(azureArtifactsSettingName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(azureArtifactsSettingName);
    testCRUD(generateSettingValueYamlConfig(azureArtifactsSettingName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String azureArtifactsSettingName = AZURE_ARTIFACTS_SETTING_NAME + System.currentTimeMillis();
    SettingAttribute settingAttributeSaved = createAzureArtifactsConnector(azureArtifactsSettingName);
    testFailureScenario(generateSettingValueYamlConfig(azureArtifactsSettingName, settingAttributeSaved));
  }

  private SettingAttribute createAzureArtifactsConnector(String settingName) {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    SecretName secretName = SecretName.builder().value("pcf_password").build();
    String patKey = yamlHandlersSecretGeneratorHelper.generateSecret(ACCOUNT_ID, secretName);

    return settingsService.save(aSettingAttribute()
                                    .withCategory(SettingCategory.AZURE_ARTIFACTS)
                                    .withName(settingName)
                                    .withAccountId(ACCOUNT_ID)
                                    .withValue(AzureArtifactsPATConfig.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .azureDevopsUrl(azureDevopsUrl)
                                                   .pat(patKey.toCharArray())
                                                   .build())
                                    .build());
  }

  private SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    String invalidYamlContent = "harnessApiVersion: '1.0'\n"
        + "type: AZURE_ARTIFACTS_PAT\n"
        + "azureUrl: https://random.azure/org\n"
        + "pat: afeharness:kdT-tC2dTNCyY2pJJzSN9A";

    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(yamlClass)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(artifactServerYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(AzureArtifactsPATConfig.class)
        .updateMethodName("setAzureDevopsUrl")
        .currentFieldValue(azureDevopsUrl)
        .build();
  }
}
