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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.DatadogConfig;
import software.wings.beans.DatadogYaml;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.yaml.handler.connectors.configyamlhandlers.SettingValueYamlConfig;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class DatadogConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @Inject DatadogConfigYamlHandler yamlHandler;

  String APP_ID = "APP_ID";
  String ACCOUNT_ID = "ACCOUNT_ID";
  String DATADOG_NAME = "datadog";
  String DATADOG_URL = "datadog_url";
  String API_KEY = "API_KEY";
  String API_KEY_NEW = "API_KEY_NEW";
  String APPLICATION_KEY = "APPLICATION_KEY";

  String invalidYamlContent = "apiKey_datadog: amazonkms:C7cBDpxHQzG5rv30tvZDgw\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: NEW_RELIC";

  SettingAttribute createDatadogSettingAttribute() {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);
    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
            .withName(DATADOG_NAME)
            .withAccountId(ACCOUNT_ID)
            .withValue(DatadogConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .url(DATADOG_URL)
                           .apiKey(createSecretText(ACCOUNT_ID, API_KEY, generateUuid()).toCharArray())
                           .applicationKey(createSecretText(ACCOUNT_ID, APPLICATION_KEY, generateUuid()).toCharArray())
                           .build())
            .build());
  }

  SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(DatadogYaml.class)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(verificationProviderYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(DatadogConfig.class)
        .updateMethodName(null)
        .currentFieldValue(null)
        .build();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToYaml() {
    DatadogYaml yaml = yamlHandler.toYaml(createDatadogSettingAttribute(), APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("DATA_DOG");
    assertThat(yaml.getUrl()).isEqualTo(DATADOG_URL);
    assertThat(yaml.getApiKey()).isEqualTo(API_KEY);
    assertThat(yaml.getApplicationKey()).isEqualTo(APPLICATION_KEY);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    testCRUD(generateSettingValueYamlConfig(DATADOG_NAME, createDatadogSettingAttribute()));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToBean_Valid() {
    SettingAttribute settingAttribute = createDatadogSettingAttribute();
    DatadogYaml yaml = yamlHandler.toYaml(settingAttribute, APP_ID);
    String newSecret = createSecretText(ACCOUNT_ID, API_KEY_NEW, generateUuid());
    yaml.setUrl(DATADOG_URL + "/new");
    yaml.setApiKey(API_KEY_NEW);

    Change change = Change.Builder.aFileChange()
                        .withAccountId(ACCOUNT_ID)
                        .withFilePath("Setup/Verification Provider/test-harness.yaml")
                        .build();
    SettingAttribute changedSettingAttribute =
        yamlHandler.toBean(settingAttribute, aChangeContext().withChange(change).withYaml(yaml).build(), null);
    assertThat(changedSettingAttribute).isNotNull();
    DatadogConfig datadogConfig = (DatadogConfig) changedSettingAttribute.getValue();
    assertThat(datadogConfig).isNotNull();
    assertThat(datadogConfig.getUrl()).isEqualTo(DATADOG_URL + "/new");
    assertThat(datadogConfig.getEncryptedApiKey()).isEqualTo(newSecret);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToBean_invalid() {
    SettingAttribute settingAttribute = createDatadogSettingAttribute();
    DatadogYaml yaml = yamlHandler.toYaml(settingAttribute, APP_ID);
    yaml.setUrl(DATADOG_URL + "/new");
    yaml.setApiKey(API_KEY_NEW);

    Change change = Change.Builder.aFileChange()
                        .withAccountId(ACCOUNT_ID)
                        .withFilePath("Setup/Verification Provider/test-harness.yaml")
                        .build();
    assertThatThrownBy(
        () -> yamlHandler.toBean(settingAttribute, aChangeContext().withChange(change).withYaml(yaml).build(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
