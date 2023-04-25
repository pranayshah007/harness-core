/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.setting;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.JenkinsConfig.VerificationYaml;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.GenericEntityFilter.FilterType;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction;
import software.wings.service.impl.yaml.handler.setting.verificationprovider.JenkinsConfigVerificationYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.service.impl.yaml.handler.usagerestrictions.UsageRestrictionsYamlHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.yaml.handler.connectors.configyamlhandlers.SettingValueYamlConfig;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class UsageRestrictionsYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @InjectMocks @Inject private UsageRestrictionsYamlHandler usageRestrictionsYamlHandler;
  @InjectMocks @Inject private JenkinsConfigVerificationYamlHandler yamlHandler;
  public static final String url = "https://jenkins.wings.software";

  private Class yamlClass = VerificationYaml.class;

  protected static final String token = "token";

  @Before
  public void setUp() throws Exception {
    Application app = new Application();
    app.setName(APP_NAME);
    app.setUuid(APP_ID);
    when(appService.getAppByName(ACCOUNT_ID, APP_NAME)).thenReturn(app);
    when(appService.get(APP_ID)).thenReturn(app);

    Environment env = new Environment();
    env.setName(ENV_NAME);
    env.setUuid(ENV_ID);
    env.setAppId(APP_ID);

    when(environmentService.getEnvironmentByName(APP_ID, ENV_NAME, false)).thenReturn(env);
    when(environmentService.get(APP_ID, ENV_ID)).thenReturn(env);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String jenkinsProviderName = "Jenkins" + System.currentTimeMillis();

    // 1. Create jenkins verification record
    SettingAttribute settingAttributeSaved =
        createJenkinsProviderWithUsageRestrictions(jenkinsProviderName, createUsageRestrictions());
    assertThat(settingAttributeSaved.getName()).isEqualTo(jenkinsProviderName);

    testCRUD(generateSettingValueYamlConfig(jenkinsProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCRUDAndGetNullRestrictions() throws Exception {
    String jenkinsProviderName = "Jenkins" + System.currentTimeMillis();

    // 1. Create jenkins verification record
    SettingAttribute settingAttributeSaved = createJenkinsProviderWithUsageRestrictions(jenkinsProviderName, null);
    assertThat(settingAttributeSaved.getName()).isEqualTo(jenkinsProviderName);

    testCRUD(generateSettingValueYamlConfig(jenkinsProviderName, settingAttributeSaved));

    settingAttributeSaved =
        createJenkinsProviderWithUsageRestrictions(jenkinsProviderName, UsageRestrictions.builder().build());
    assertThat(settingAttributeSaved.getName()).isEqualTo(jenkinsProviderName);

    testCRUD(generateSettingValueYamlConfig(jenkinsProviderName, settingAttributeSaved));
  }

  private SettingAttribute createJenkinsProviderWithUsageRestrictions(
      String jenkinsProviderName, UsageRestrictions usageRestrictions) {
    // Generate Jenkins verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName(jenkinsProviderName)
            .withAccountId(ACCOUNT_ID)
            .withValue(JenkinsConfig.builder()
                           .jenkinsUrl(url)
                           .username(userName)
                           .accountId(ACCOUNT_ID)
                           .password(createSecretText(ACCOUNT_ID, generateUuid(), password).toCharArray())
                           .token(createSecretText(ACCOUNT_ID, generateUuid(), token).toCharArray())
                           .authMechanism(JenkinsConfig.USERNAME_DEFAULT_TEXT)
                           .build())
            .withUsageRestrictions(usageRestrictions)
            .build());
  }

  private UsageRestrictions createUsageRestrictions() {
    AppEnvRestriction appEnvRestriction1 =
        AppEnvRestriction.builder()
            .appFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build())
            .envFilter(EnvFilter.builder()
                           .filterTypes(Sets.newHashSet(EnvFilter.FilterType.PROD, EnvFilter.FilterType.NON_PROD))
                           .build())
            .build();
    AppEnvRestriction appEnvRestriction2 =
        AppEnvRestriction.builder()
            .appFilter(
                GenericEntityFilter.builder().filterType(FilterType.SELECTED).ids(Sets.newHashSet(APP_ID)).build())
            .envFilter(EnvFilter.builder()
                           .filterTypes(Sets.newHashSet(EnvFilter.FilterType.PROD, EnvFilter.FilterType.NON_PROD))
                           .build())
            .build();
    AppEnvRestriction appEnvRestriction3 =
        AppEnvRestriction.builder()
            .appFilter(
                GenericEntityFilter.builder().filterType(FilterType.SELECTED).ids(Sets.newHashSet(APP_ID)).build())
            .envFilter(EnvFilter.builder()
                           .filterTypes(Sets.newHashSet(EnvFilter.FilterType.SELECTED))
                           .ids(Sets.newHashSet(ENV_ID))
                           .build())
            .build();
    return UsageRestrictions.builder()
        .appEnvRestrictions(Sets.newHashSet(appEnvRestriction1, appEnvRestriction2, appEnvRestriction3))
        .build();
  }

  private SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    String invalidYamlContent = "url_jenkins: https://jenkins.example.com\n"
        + "username: username\n"
        + "password: amazonkms:C7cBDpxHQzG5rv30tvZDgw\n"
        + "token: amazonkms:C7cBDpxHQzG5rv30tvZDgw\n"
        + "type: JENKINS";
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(yamlClass)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(verificationProviderYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(JenkinsConfig.class)
        .updateMethodName("setJenkinsUrl")
        .currentFieldValue(url)
        .build();
  }
}
