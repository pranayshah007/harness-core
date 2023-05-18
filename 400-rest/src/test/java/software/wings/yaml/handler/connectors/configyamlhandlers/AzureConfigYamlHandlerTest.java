/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.PUNEET;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.ccm.config.CCMSettingService;
import io.harness.rule.Owner;
import io.harness.scm.SecretName;

import software.wings.beans.AzureConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.AzureConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @Mock AccountService accountService;
  @Mock CCMSettingService ccmSettingService;
  @Inject YamlHandlersSecretGeneratorHelper yamlHandlersSecretGeneratorHelper;
  @InjectMocks @Inject private AzureConfigYamlHandler yamlHandler;
  public static final String clientId = "dummyClientId";
  public static final String tenantId = "dummyTenantId";

  private String invalidYamlContent = "invalidClientId: dummyClientId\n"
      + "key: amazonkms:zsj_HWfkSF-3li3W-9acHA\n"
      + "tenantId: dummyTenantId\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: AZURE";

  private Class yamlClass = AzureConfig.Yaml.class;

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String azureConfigName = "Azure" + System.currentTimeMillis();

    SettingAttribute settingAttributeSaved = createAzureConfigProvider(azureConfigName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(azureConfigName);

    testCRUD(generateSettingValueYamlConfig(azureConfigName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String azureConfigName = "Azure" + System.currentTimeMillis();

    SettingAttribute settingAttributeSaved = createAzureConfigProvider(azureConfigName);
    testFailureScenario(generateSettingValueYamlConfig(azureConfigName, settingAttributeSaved));
  }

  private SettingAttribute createAzureConfigProvider(String azureConfigName) {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);
    SecretName secretName = SecretName.builder().value("pcf_password").build();
    String secretKey = yamlHandlersSecretGeneratorHelper.generateSecret(ACCOUNT_ID, secretName);

    return settingsService.save(aSettingAttribute()
                                    .withCategory(SettingCategory.CLOUD_PROVIDER)
                                    .withName(azureConfigName)
                                    .withAccountId(ACCOUNT_ID)
                                    .withValue(AzureConfig.builder()
                                                   .clientId(clientId)
                                                   .tenantId(tenantId)
                                                   .key(secretKey.toCharArray())
                                                   .accountId(ACCOUNT_ID)
                                                   .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                                   .build())
                                    .build());
  }

  private SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(yamlClass)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(cloudProviderYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(AzureConfig.class)
        .updateMethodName("setClientId")
        .currentFieldValue(clientId)
        .build();
  }
}
