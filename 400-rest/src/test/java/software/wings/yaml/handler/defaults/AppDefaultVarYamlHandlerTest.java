/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.defaults;

import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.beans.StringValue;
import software.wings.beans.defaults.Defaults.Yaml;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.rules.SetupScheduler;
import software.wings.service.impl.yaml.handler.defaults.DefaultVariablesYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingValue;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import javax.validation.ConstraintViolationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 1/19/18
 */
@SetupScheduler
public class AppDefaultVarYamlHandlerTest extends YamlHandlerTestBase {
  @Mock YamlHelper yamlHelper;
  @Mock AppService appService;
  @InjectMocks @Inject private SettingsService settingsService;
  @InjectMocks @Inject private DefaultVariablesYamlHandler yamlHandler;

  private final String APP_NAME = "app1";
  private SettingAttribute v1_settingAttribute1;
  private SettingAttribute v1_settingAttribute2;
  private SettingAttribute v1_settingAttribute3;
  private List<SettingAttribute> v1_settingAttributeList;

  private SettingAttribute v2_settingAttribute1;
  private SettingAttribute v2_settingAttribute2;
  private SettingAttribute v2_settingAttribute3;
  private List<SettingAttribute> v2_settingAttributeList;

  private String v1_validYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_DEFAULTS\n"
      + "defaults:\n"
      + "- name: var1\n"
      + "  value: value1\n"
      + "- name: var2\n"
      + "  value: value2\n"
      + "- name: var3\n"
      + "  value: value3";
  private String v2_validYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_DEFAULTS\n"
      + "defaults:\n"
      + "- name: var1\n"
      + "  value: modified\n"
      + "- name: var4\n"
      + "  value: add\n"
      + "- name: var3\n"
      + "  value: value3";
  private String validYamlFilePath = "Setup/Applications/" + APP_NAME + "/Defaults.yaml";
  private String invalidYamlContent = "defaults:\n"
      + "  - name1: STAGING_PATH\n"
      + "    value: $HOME/${app.name}/${service.name}/${env.name}/staging/${timestampId}\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_DEFAULTS";

  @Before
  public void setUp() throws IOException {
    v1_settingAttribute1 = createNewSettingAttribute("var1", "value1");
    v1_settingAttribute2 = createNewSettingAttribute("var2", "value2");
    v1_settingAttribute3 = createNewSettingAttribute("var3", "value3");
    v1_settingAttributeList = asList(v1_settingAttribute1, v1_settingAttribute2, v1_settingAttribute3);

    v2_settingAttribute1 = createNewSettingAttribute("var1", "modified");
    v2_settingAttribute2 = createNewSettingAttribute("var4", "add");
    v2_settingAttribute3 = createNewSettingAttribute("var3", "value3");
    v2_settingAttributeList = asList(v2_settingAttribute1, v2_settingAttribute2, v2_settingAttribute3);
  }

  private SettingAttribute createNewSettingAttribute(String name, String value) {
    SettingValue settingValue = StringValue.Builder.aStringValue().withValue(value).build();
    return SettingAttribute.Builder.aSettingAttribute()
        .withAppId(APP_ID)
        .withName(name)
        .withValue(settingValue)
        .withAccountId(ACCOUNT_ID)
        .withEnvId(GLOBAL_ENV_ID)
        .build();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(APP_ID);
    when(yamlHelper.getAppName(anyString())).thenReturn(APP_NAME);
    Application app = new Application();
    app.setUuid(APP_ID);
    when(appService.getAppByName(ACCOUNT_ID, APP_NAME)).thenReturn(app).thenReturn(app);
    when(yamlHelper.getApp(anyString(), anyString())).thenReturn(app);

    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(v1_validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.APPLICATION_DEFAULTS);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(v1_validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    List<SettingAttribute> createdSettingAttributes = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    resetPresetFields(createdSettingAttributes);
    compareSettingAttributes(v1_settingAttributeList, createdSettingAttributes);

    Yaml yaml = yamlHandler.toYaml(this.v1_settingAttributeList, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isNotNull();

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(v1_validYamlContent);

    List<SettingAttribute> settingAttributesFromGet = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    resetPresetFields(settingAttributesFromGet);
    compareSettingAttributes(v1_settingAttributeList, settingAttributesFromGet);

    gitFileChange.setFileContent(v2_validYamlContent);

    yamlObject = (Yaml) getYaml(v2_validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    List<SettingAttribute> v2_createdSettingAttributes =
        yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    resetPresetFields(v2_createdSettingAttributes);
    compareSettingAttributes(v2_settingAttributeList, v2_createdSettingAttributes);

    yamlHandler.delete(changeContext);
    List<SettingAttribute> settingAttributeList = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(settingAttributeList.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(APP_ID);

    // Invalid yaml path
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(invalidYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<Yaml> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.APPLICATION_DEFAULTS);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(v1_validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    // Invalid yaml content
    yamlObject = (Yaml) getYaml(invalidYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);
    thrown.expect(ConstraintViolationException.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }

  private void compareSettingAttributes(List<SettingAttribute> lhs, List<SettingAttribute> rhs) {
    assertThat(rhs).hasSize(lhs.size());
    assertThat(lhs).containsAll(rhs);
  }

  private void resetPresetFields(List<SettingAttribute> createdSettingAttributes) {
    createdSettingAttributes.forEach(settingAttribute -> {
      settingAttribute.setUuid(null);
      settingAttribute.setCreatedBy(null);
      settingAttribute.setCreatedAt(0);
      settingAttribute.setLastUpdatedAt(0);
    });
  }
}
