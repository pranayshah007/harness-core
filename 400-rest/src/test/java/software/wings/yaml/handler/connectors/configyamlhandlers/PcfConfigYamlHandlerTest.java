/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME_DECRYPTED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.scm.SecretName;

import software.wings.beans.PcfConfig;
import software.wings.beans.PcfConfig.Yaml;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.PcfConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(CDP)
public class PcfConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @Inject YamlHandlersSecretGeneratorHelper yamlHandlersSecretGeneratorHelper;
  @InjectMocks @Inject private PcfConfigYamlHandler yamlHandler;
  public static final String endpointUrl = "link.com";

  private String invalidYamlContent = "invalidClientId: dummyClientId\n"
      + "key: amazonkms:zsj_HWfkSF-3li3W-9acHA\n"
      + "tenantId: dummyTenantId\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: AZURE";

  private Class yamlClass = Yaml.class;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String pcfConfigName = "Pcf" + System.currentTimeMillis();

    SettingAttribute settingAttributeSaved = createPCFConfigProvider(pcfConfigName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(pcfConfigName);

    testCRUD(generateSettingValueYamlConfig(pcfConfigName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String pcfConfigName = "Pcf" + System.currentTimeMillis();

    SettingAttribute settingAttributeSaved = createPCFConfigProvider(pcfConfigName);
    testFailureScenario(generateSettingValueYamlConfig(pcfConfigName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToBeanBothUsernameAndUsernameSecretId() {
    Yaml yaml = Yaml.builder().username("username").usernameSecretId("usernameSecretId").build();

    Change change = Change.Builder.aFileChange()
                        .withAccountId("ABC")
                        .withFilePath("Setup/Cloud Providers/test-harness.yaml")
                        .build();
    ChangeContext<Yaml> changeContext = ChangeContext.Builder.aChangeContext()
                                            .withYamlType(YamlType.CLOUD_PROVIDER)
                                            .withYaml(yaml)
                                            .withChange(change)
                                            .build();

    assertThatThrownBy(() -> yamlHandler.toBean(null, changeContext, null))
        .hasMessageContaining("Cannot set both value and secret reference for username field");
  }

  private SettingAttribute createPCFConfigProvider(String pcfConfigName) {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);
    SecretName secretName = SecretName.builder().value("pcf_password").build();
    String secretKey = yamlHandlersSecretGeneratorHelper.generateSecret(ACCOUNT_ID, secretName);

    return settingsService.save(aSettingAttribute()
                                    .withCategory(SettingCategory.CLOUD_PROVIDER)
                                    .withName(pcfConfigName)
                                    .withAccountId(ACCOUNT_ID)
                                    .withValue(PcfConfig.builder()
                                                   .username(USER_NAME_DECRYPTED)
                                                   .endpointUrl(endpointUrl)
                                                   .password(secretKey.toCharArray())
                                                   .accountId(ACCOUNT_ID)
                                                   .skipValidation(true)
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
        .configclazz(PcfConfig.class)
        .updateMethodName("setEndpointUrl")
        .currentFieldValue(endpointUrl)
        .build();
  }
}
