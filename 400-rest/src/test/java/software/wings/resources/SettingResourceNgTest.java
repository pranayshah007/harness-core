/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;

import software.wings.WingsBaseTest;
import software.wings.beans.KmsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.Builder;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.impl.security.auth.SettingAuthHandler;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
public class SettingResourceNgTest extends WingsBaseTest {
  @Mock SecretManager secretManager;
  @Mock SettingsService settingsService;
  @Mock SecretManagerConfigService secretManagerConfigService;
  @Mock SettingAuthHandler settingAuthHandler;
  @Mock SettingServiceHelper settingServiceHelper;
  @InjectMocks SettingResourceNg settingResourceNg;

  private SettingAttribute settingAttributeWithEmptyPassword;
  private SettingAttribute getSettingAttributeWithNullPassword;
  private SettingAttribute attributeWithMetaCharPassword;

  @Before
  public void setup() {
    SmtpConfig smtpConfigWithEmptyPassword =
        SmtpConfig.builder().accountId(ACCOUNT_ID).host("sendgrid.net").password(new char[0]).port(465).build();
    settingAttributeWithEmptyPassword = Builder.aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withAppId(APP_ID)
                                            .withCategory(SettingCategory.CONNECTOR)
                                            .withValue(smtpConfigWithEmptyPassword)
                                            .build();

    SmtpConfig smtpConfigWithNullPassword =
        SmtpConfig.builder().accountId(ACCOUNT_ID).host("sendgrid.net").port(465).build();
    getSettingAttributeWithNullPassword = Builder.aSettingAttribute()
                                              .withAccountId(ACCOUNT_ID)
                                              .withAppId(APP_ID)
                                              .withCategory(SettingCategory.CONNECTOR)
                                              .withValue(smtpConfigWithNullPassword)
                                              .build();

    SmtpConfig smtpConfigMetaChar = SmtpConfig.builder()
                                        .accountId(ACCOUNT_ID)
                                        .host("sendgrid.net")
                                        .port(465)
                                        .fromAddress("sendgrid.net")
                                        .username("testSecret")
                                        .encryptedPassword("testSecret")
                                        .build();
    attributeWithMetaCharPassword = Builder.aSettingAttribute()
                                        .withName("smtp.test.secret")
                                        .withAccountId(ACCOUNT_ID)
                                        .withAppId(APP_ID)
                                        .withCategory(SettingCategory.CONNECTOR)
                                        .withValue(smtpConfigMetaChar)
                                        .build();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testSaveSmtpSetting_withEmptyPassword_throwsInvalidRequestWithSpecificMessage() {
    SecretManagerConfig secretManagerConfig = KmsConfig.builder().build();
    when(secretManagerConfigService.getDefaultSecretManager(ACCOUNT_ID)).thenReturn(secretManagerConfig);
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name()))
        .thenReturn(new ArrayList<>());
    doThrow(new InvalidRequestException("Cannot create empty secret"))
        .when(secretManager)
        .saveSecretText(any(String.class), any(SecretText.class), any(Boolean.class));
    ArgumentCaptor<SecretText> argumentCaptor = ArgumentCaptor.forClass(SecretText.class);
    try {
      settingResourceNg.save(APP_ID, ACCOUNT_ID, settingAttributeWithEmptyPassword);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).isEqualTo("Cannot create empty secret");
    }
    verify(secretManager).saveSecretText(any(String.class), argumentCaptor.capture(), any(Boolean.class));
    assertThat(argumentCaptor.getValue().getValue()).isEqualTo("");
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testSaveSmtpSetting_withNullPassword_throwsInvalidRequestWithSpecificMessage() {
    SecretManagerConfig secretManagerConfig = KmsConfig.builder().build();
    when(secretManagerConfigService.getDefaultSecretManager(ACCOUNT_ID)).thenReturn(secretManagerConfig);
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name()))
        .thenReturn(new ArrayList<>());
    doThrow(new InvalidRequestException("Cannot create empty secret"))
        .when(secretManager)
        .saveSecretText(any(String.class), any(SecretText.class), any(Boolean.class));
    ArgumentCaptor<SecretText> argumentCaptor = ArgumentCaptor.forClass(SecretText.class);
    try {
      settingResourceNg.save(APP_ID, ACCOUNT_ID, getSettingAttributeWithNullPassword);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).isEqualTo("Cannot create empty secret");
    }
    verify(secretManager).saveSecretText(any(String.class), argumentCaptor.capture(), any(Boolean.class));
    assertThat(argumentCaptor.getValue().getValue()).isEqualTo("");
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testSaveSmtpSetting_withMetaCharsSecret_notThrowSecretManagementException() {
    SecretManagerConfig secretManagerConfig = KmsConfig.builder().build();
    when(secretManagerConfigService.getDefaultSecretManager(ACCOUNT_ID)).thenReturn(secretManagerConfig);
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.SMTP.name()))
        .thenReturn(new ArrayList<>());
    when(secretManager.saveSecretText(any(String.class), any(SecretText.class), any(Boolean.class)))
        .thenReturn("testSecret");
    doNothing().when(settingAuthHandler).authorize(any(SettingAttribute.class), any(String.class));
    when(settingsService.saveWithPruning(any(SettingAttribute.class), any(String.class), any(String.class)))
        .thenReturn(attributeWithMetaCharPassword);
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(any(SettingAttribute.class), any(Boolean.class));
    ArgumentCaptor<SecretText> argumentCaptor = ArgumentCaptor.forClass(SecretText.class);
    try {
      settingResourceNg.save(APP_ID, ACCOUNT_ID, attributeWithMetaCharPassword);
    } catch (RuntimeException e) {
      assertThat(e).isEqualTo(SecretManagementException.class);
    }
    verify(secretManager).saveSecretText(any(String.class), argumentCaptor.capture(), any(Boolean.class));
    assertThat(argumentCaptor.getValue().getValue()).isEqualTo("");
  }
}
