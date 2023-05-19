/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.setting.verificationprovider;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.yaml.ChangeContext.Builder.aChangeContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.APMVerificationConfig;
import software.wings.beans.APMVerificationConfig.KeyValues;
import software.wings.beans.APMVerificationConfig.Yaml;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.yaml.handler.connectors.configyamlhandlers.SettingValueYamlConfig;

import com.google.inject.Inject;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class APMConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @Inject APMConfigYamlHandler yamlHandler;

  String APP_ID = "APP_ID";
  String ACCOUNT_ID = "ACCOUNT_ID";
  String CONFIG_NAME = "APM Config";
  String CONFIG_URL = "URL";
  String VALIDATION_URL = "Validation url";
  String HEADER_KEY = "key";
  String HEADER_VALUE = "value";
  String HEADER_KEY2 = "key2";
  String HEADER_VALUE2 = "value2";

  String invalidYamlContent = "apiKey_datadog: amazonkms:C7cBDpxHQzG5rv30tvZDgw\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: NEW_RELIC";

  SettingAttribute createSettingAttribute() {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);
    String secret = createSecretText(ACCOUNT_ID, HEADER_VALUE, generateUuid());
    KeyValues keyValues =
        KeyValues.builder().key(HEADER_KEY).value(secret).encryptedValue(secret).encrypted(true).build();
    KeyValues keyValues2 = KeyValues.builder().key(HEADER_KEY2).value(HEADER_VALUE2).encrypted(false).build();
    APMVerificationConfig config = new APMVerificationConfig();
    config.setAccountId(ACCOUNT_ID);
    config.setUrl(CONFIG_URL);
    config.setValidationUrl(VALIDATION_URL);
    config.setLogVerification(true);
    config.setHeadersList(Collections.singletonList(keyValues));
    config.setOptionsList(Collections.singletonList(keyValues2));
    return settingsService.save(aSettingAttribute()
                                    .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                    .withName(CONFIG_NAME)
                                    .withAccountId(ACCOUNT_ID)
                                    .withValue(config)
                                    .build());
  }

  SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(Yaml.class)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(verificationProviderYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(APMVerificationConfig.class)
        .updateMethodName(null)
        .currentFieldValue(null)
        .build();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToYaml() {
    Yaml yaml = yamlHandler.toYaml(createSettingAttribute(), APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("APM_VERIFICATION");
    assertThat(yaml.getUrl()).isEqualTo(CONFIG_URL);
    assertThat(yaml.getHeadersList().size()).isEqualTo(1);
    assertThat(yaml.getHeadersList().get(0).getKey()).isEqualTo(HEADER_KEY);
    assertThat(yaml.getHeadersList().get(0).getValue()).isEqualTo(HEADER_VALUE);
    assertThat(yaml.getHeadersList().get(0).isEncrypted()).isEqualTo(true);
    assertThat(yaml.getOptionsList().size()).isEqualTo(1);
    assertThat(yaml.getOptionsList().get(0).getKey()).isEqualTo(HEADER_KEY2);
    assertThat(yaml.getOptionsList().get(0).getValue()).isEqualTo(HEADER_VALUE2);
    assertThat(yaml.getOptionsList().get(0).isEncrypted()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    testCRUD(generateSettingValueYamlConfig(CONFIG_NAME, createSettingAttribute()));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToBean_valid() {
    SettingAttribute settingAttribute = createSettingAttribute();
    Yaml yaml = yamlHandler.toYaml(settingAttribute, APP_ID);
    String newSecretName = "New secret";
    String newSecretId = createSecretText(ACCOUNT_ID, newSecretName, generateUuid());
    String newValue = "New value";
    yaml.getHeadersList().get(0).setValue(newSecretName);
    yaml.getOptionsList().get(0).setValue(newValue);

    Change change = Change.Builder.aFileChange()
                        .withAccountId(ACCOUNT_ID)
                        .withFilePath("Setup/Verification Provider/test-harness.yaml")
                        .build();
    SettingAttribute saved =
        yamlHandler.toBean(settingAttribute, aChangeContext().withChange(change).withYaml(yaml).build(), null);
    assertThat(saved).isNotNull();
    APMVerificationConfig config = (APMVerificationConfig) saved.getValue();
    assertThat(config).isNotNull();
    assertThat(config.getUrl()).isEqualTo(CONFIG_URL);
    assertThat(config.getValidationUrl()).isEqualTo(VALIDATION_URL);
    assertThat(config.getHeadersList().size()).isEqualTo(1);
    assertThat(config.getHeadersList().get(0).getValue()).isEqualTo(newSecretId);
    assertThat(config.getHeadersList().get(0).isEncrypted()).isEqualTo(true);
    assertThat(config.getHeadersList().get(0).getEncryptedValue()).isEqualTo(newSecretId);
    assertThat(config.getOptionsList().size()).isEqualTo(1);
    assertThat(config.getOptionsList().get(0).getValue()).isEqualTo(newValue);
    assertThat(config.getOptionsList().get(0).isEncrypted()).isEqualTo(false);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToBean_invalid() {
    SettingAttribute settingAttribute = createSettingAttribute();
    Yaml yaml = yamlHandler.toYaml(settingAttribute, APP_ID);
    String newSecretName = "New secret";
    String newValue = "New value";
    yaml.getHeadersList().get(0).setValue(newSecretName);
    yaml.getOptionsList().get(0).setValue(newValue);

    Change change = Change.Builder.aFileChange()
                        .withAccountId(ACCOUNT_ID)
                        .withFilePath("Setup/Verification Provider/test-harness.yaml")
                        .build();
    assertThatThrownBy(
        () -> yamlHandler.toBean(settingAttribute, aChangeContext().withChange(change).withYaml(yaml).build(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
