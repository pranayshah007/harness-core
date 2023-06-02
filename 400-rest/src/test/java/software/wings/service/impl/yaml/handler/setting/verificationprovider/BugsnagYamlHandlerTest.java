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

import software.wings.beans.BugsnagConfig;
import software.wings.beans.BugsnagConfig.Yaml;
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
public class BugsnagYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @Inject BugsnagConfigYamlHandler yamlHandler;

  String APP_ID = "APP_ID";
  String ACCOUNT_ID = "ACCOUNT_ID";
  String BUGSNAG_NAME = "bugsnag";
  String BUGSNAG_URL = "URL";
  String KEY = "KEY";
  String NEW_KEY = "NEW_KEY";

  String invalidYamlContent = "apiKey_datadog: amazonkms:C7cBDpxHQzG5rv30tvZDgw\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: NEW_RELIC";

  SettingAttribute createSettingAttribute() {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);
    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
            .withName(BUGSNAG_NAME)
            .withAccountId(ACCOUNT_ID)
            .withValue(BugsnagConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .url(BUGSNAG_URL)
                           .authToken(createSecretText(ACCOUNT_ID, KEY, generateUuid()).toCharArray())
                           .build())
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
        .configclazz(BugsnagConfig.class)
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
    assertThat(yaml.getType()).isEqualTo("BUG_SNAG");
    assertThat(yaml.getUrl()).isEqualTo(BUGSNAG_URL);
    assertThat(yaml.getAuthToken()).isEqualTo(KEY);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    testCRUD(generateSettingValueYamlConfig(BUGSNAG_NAME, createSettingAttribute()));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToBean_Valid() {
    SettingAttribute settingAttribute = createSettingAttribute();
    Yaml yaml = yamlHandler.toYaml(settingAttribute, APP_ID);
    String newSecret = createSecretText(ACCOUNT_ID, NEW_KEY, generateUuid());
    yaml.setAuthToken(NEW_KEY);

    Change change = Change.Builder.aFileChange()
                        .withAccountId(ACCOUNT_ID)
                        .withFilePath("Setup/Verification Provider/test-harness.yaml")
                        .build();
    SettingAttribute changedSettingAttribute =
        yamlHandler.toBean(settingAttribute, aChangeContext().withChange(change).withYaml(yaml).build(), null);
    assertThat(changedSettingAttribute).isNotNull();
    BugsnagConfig bugsnagConfig = (BugsnagConfig) changedSettingAttribute.getValue();
    assertThat(bugsnagConfig).isNotNull();
    assertThat(bugsnagConfig.getUrl()).isEqualTo(BUGSNAG_URL);
    assertThat(bugsnagConfig.getEncryptedAuthToken()).isEqualTo(newSecret);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToBean_Invalid() {
    SettingAttribute settingAttribute = createSettingAttribute();
    Yaml yaml = yamlHandler.toYaml(settingAttribute, APP_ID);
    yaml.setAuthToken(NEW_KEY);

    Change change = Change.Builder.aFileChange()
                        .withAccountId(ACCOUNT_ID)
                        .withFilePath("Setup/Verification Provider/test-harness.yaml")
                        .build();
    assertThatThrownBy(
        () -> yamlHandler.toBean(settingAttribute, aChangeContext().withChange(change).withYaml(yaml).build(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
