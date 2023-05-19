/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.setting.verificationprovider;

import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.yaml.ChangeContext.Builder.aChangeContext;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.HarnessException;
import io.harness.rule.Owner;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.JenkinsConfig.VerificationYaml;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.yaml.handler.connectors.configyamlhandlers.SettingValueYamlConfig;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class JenkinsConfigVerificationYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private JenkinsConfigVerificationYamlHandler yamlHandler;
  public static final String url = "https://jenkins.wings.software";

  private Class yamlClass = JenkinsConfig.VerificationYaml.class;

  protected static final String token = "token";

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String jenkinsProviderName = "Jenkins" + System.currentTimeMillis();

    // 1. Create jenkins verification record
    SettingAttribute settingAttributeSaved = createJenkinsVerificationProvider(jenkinsProviderName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(jenkinsProviderName);

    testCRUD(generateSettingValueYamlConfig(jenkinsProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String jenkinsProviderName = "Jenkins" + System.currentTimeMillis();

    // 1. Create jenkins verification provider record
    SettingAttribute settingAttributeSaved = createJenkinsVerificationProvider(jenkinsProviderName);
    testFailureScenario(generateSettingValueYamlConfig(jenkinsProviderName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testToBeanForNullValues() {
    ChangeContext<VerificationYaml> changeContext =
        aChangeContext()
            .withYaml(VerificationYaml.builder().build())
            .withChange(Change.Builder.aFileChange().withAccountId("ACCOUNT_ID").build())
            .build();
    try {
      yamlHandler.toBean(aSettingAttribute().build(), changeContext, Collections.EMPTY_LIST);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof HarnessException).isTrue();
    }

    changeContext = aChangeContext()
                        .withYaml(VerificationYaml.builder().authMechanism(JenkinsConfig.USERNAME_DEFAULT_TEXT).build())
                        .withChange(Change.Builder.aFileChange().withAccountId("ACCOUNT_ID").build())
                        .build();
    try {
      yamlHandler.toBean(aSettingAttribute().build(), changeContext, Collections.EMPTY_LIST);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof HarnessException).isTrue();
    }

    changeContext = aChangeContext()
                        .withYaml(VerificationYaml.builder().authMechanism(JenkinsUtils.TOKEN_FIELD).build())
                        .withChange(Change.Builder.aFileChange().withAccountId("ACCOUNT_ID").build())
                        .build();
    try {
      yamlHandler.toBean(aSettingAttribute().build(), changeContext, Collections.EMPTY_LIST);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof HarnessException).isTrue();
    }

    changeContext = aChangeContext()
                        .withYaml(VerificationYaml.builder().authMechanism("Fake").build())
                        .withChange(Change.Builder.aFileChange().withAccountId("ACCOUNT_ID").build())
                        .build();
    try {
      yamlHandler.toBean(aSettingAttribute().build(), changeContext, Collections.EMPTY_LIST);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof HarnessException).isTrue();
    }
  }

  private SettingAttribute createJenkinsVerificationProvider(String jenkinsProviderName) {
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
                           .password(createSecretText(ACCOUNT_ID, "password", password).toCharArray())
                           .token(createSecretText(ACCOUNT_ID, "token", token).toCharArray())
                           .authMechanism(JenkinsConfig.USERNAME_DEFAULT_TEXT)
                           .build())
            .build());
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
