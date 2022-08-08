/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.SHASHANK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertType;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.SSOSettings.SSOSettingsKeys;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.scheduler.LdapSyncJobConfig;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author Deepak Patankar
 * 21/Oct/2019
 */
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class SSOSettingServiceImplTest extends WingsBaseTest {
  @Mock private AlertService alertService;
  @Mock private SecretManager secretManager;
  @Inject @InjectMocks private SSOSettingService ssoSettingService;
  @Inject @InjectMocks SSOServiceHelper ssoServiceHelper;
  @Mock private EncryptionService encryptionService;
  @Mock private FeatureFlagService featureFlagService;
  private String accountId = "accountId";
  private String ssoId = "ssoID";
  private String message = "errorMessage";
  private LdapSettings ldapSettings;
  @Mock LdapSyncJobConfig ldapSyncJobConfig;

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testRaiseSyncFailureAlert() {
    doAnswer(i -> CompletableFuture.completedFuture("0")).when(alertService).openAlert(any(), any(), any(), any());

    // case when a alert exists and is created within 24 hours, we should not create a alert
    Alert newAlert =
        Alert.builder().uuid("alertId").appId("applicationId").lastUpdatedAt(System.currentTimeMillis()).build();
    when(alertService.findExistingAlert(
             any(String.class), any(String.class), any(AlertType.class), any(AlertData.class)))
        .thenReturn(Optional.of(newAlert));
    ssoSettingService.raiseSyncFailureAlert(accountId, ssoId, message);
    verify(alertService, times(0)).openAlert(any(), any(), any(), any());

    // case when a alert exists and before 24 hours, we need to create a alert and send mail
    long oldTime = System.currentTimeMillis() - 86400001;
    Alert oldAlert = Alert.builder().uuid("alertId").appId("applicationId").lastUpdatedAt(oldTime).build();
    when(alertService.findExistingAlert(
             any(String.class), any(String.class), any(AlertType.class), any(AlertData.class)))
        .thenReturn(Optional.of(oldAlert));
    ssoSettingService.raiseSyncFailureAlert(accountId, ssoId, message);
    verify(alertService, times(1)).openAlert(any(), any(), any(), any());

    // Case when no such alert exist, so we will create a new alert
    when(alertService.findExistingAlert(
             any(String.class), any(String.class), any(AlertType.class), any(AlertData.class)))
        .thenReturn(Optional.empty());
    ssoSettingService.raiseSyncFailureAlert(accountId, ssoId, message);
    verify(alertService, times(2)).openAlert(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldSaveLdapSettingWithCronExpression() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings = ssoSettingService.createLdapSettings(ldapSettings);
    LdapSettings savedLdapSettings = ssoSettingService.getLdapSettingsByAccountId(ldapSettings.getAccountId());

    assertThat(savedLdapSettings.getUuid()).isNotEmpty();
    assertThat(savedLdapSettings.getCronExpression()).isNotEmpty().isEqualTo("0 0/15 * 1/1 * ? *");
    assertThat(savedLdapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldSaveLdapSettingWithDefaultCronExpressionFromConfig() {
    when(ldapSyncJobConfig.getDefaultCronExpression()).thenReturn("0 0/15 * 1/1 * ? *");
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings = ssoSettingService.createLdapSettings(ldapSettings);
    LdapSettings savedLdapSettings = ssoSettingService.getLdapSettingsByAccountId(ldapSettings.getAccountId());

    assertThat(savedLdapSettings.getUuid()).isNotEmpty();
    assertThat(savedLdapSettings.getCronExpression()).isNotEmpty().isEqualTo("0 0/15 * 1/1 * ? *");
    assertThat(savedLdapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  // Min Interval = 900 seconds
  public void shouldNotSaveCronExpressionLessThanMinInterval() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/14 * 1/1 * ? *");
    ssoSettingService.createLdapSettings(ldapSettings);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldHaveNextIterations() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations().size()).isGreaterThan(1);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldHaveNextIterationsWithOnlyDefaultCronSet() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setDefaultCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations().size()).isGreaterThan(1);
  }

  @Test(expected = NullPointerException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldFailNextIterationsWithNoCronOrDefaultCron() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldFailNextIterationsForWrongCron() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/A * 1/1 * ? *");
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldNotSaveForWrongCron() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/A * 1/1 * ? *");
    ssoSettingService.createLdapSettings(ldapSettings);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldNotReCalculateNextIterationsForExistingListOfMoreThanTwo() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations().size()).isEqualTo(10);
    ldapSettings.getNextIterations().remove(0);
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations().size()).isLessThan(10);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldReCalculateNextIterationsForExistingListOfLessThanTwo() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations().size()).isEqualTo(10);
    ldapSettings.setNextIterations(ldapSettings.getNextIterations().subList(0, 1));

    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations().size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void createLDAPSettingWithSecret() {
    LdapSettings ldapSettings = createLDAOSSOProviderWithSecret();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.getConnectionSettings().setPasswordType(LdapConnectionSettings.SECRET);
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setBindSecret("randomuuid".toCharArray());
    ldapSettings.getConnectionSettings().setEncryptedBindSecret("randomuuid");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyListOf(EncryptedDataDetail.class), eq(false)))
        .thenReturn(null);
    when(featureFlagService.isEnabled(FeatureName.LDAP_SECRET_AUTH, ldapSettings.getAccountId())).thenReturn(true);
    LdapSettings createdLdapSetting = ssoSettingService.createLdapSettings(ldapSettings);
    assertThat(createdLdapSetting.getConnectionSettings().getPasswordType()).isEqualTo(LdapConnectionSettings.SECRET);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void createLDAPSettingWithInlinePassword() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setEncryptedBindPassword("EncryptedBindPassword");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyListOf(EncryptedDataDetail.class), eq(false)))
        .thenReturn(null);
    when(featureFlagService.isEnabled(FeatureName.LDAP_SECRET_AUTH, ldapSettings.getAccountId())).thenReturn(true);
    LdapSettings createdLdapSetting = ssoSettingService.createLdapSettings(ldapSettings);
    assertThat(createdLdapSetting.getConnectionSettings().getPasswordType())
        .isEqualTo(LdapConnectionSettings.INLINE_SECRET);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void updateLDAPSettingWithInlinePasswordToSecret() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setEncryptedBindPassword("EncryptedBindPassword");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyListOf(EncryptedDataDetail.class), eq(false)))
        .thenReturn(null);
    when(featureFlagService.isEnabled(FeatureName.LDAP_SECRET_AUTH, ldapSettings.getAccountId())).thenReturn(true);
    LdapSettings createdLdapSetting = ssoSettingService.createLdapSettings(ldapSettings);
    assertThat(createdLdapSetting.getConnectionSettings().getBindPassword()).isEqualTo(LdapConstants.MASKED_STRING);
    ldapSettings.getConnectionSettings().setBindSecret("randomuuid".toCharArray());
    ldapSettings.getConnectionSettings().setEncryptedBindSecret("randomuuid");
    LdapSettings updateLdapSetting = ssoSettingService.updateLdapSettings(ldapSettings);
    assertThat(updateLdapSetting.getConnectionSettings().getPasswordType()).isEqualTo(LdapConnectionSettings.SECRET);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void updateLDAPSettingWithBothInlinePasswordAndSecret() {
    LdapSettings ldapSettings = createLDAOSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setEncryptedBindPassword("EncryptedBindPassword");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyListOf(EncryptedDataDetail.class), eq(false)))
        .thenReturn(null);
    when(featureFlagService.isEnabled(FeatureName.LDAP_SECRET_AUTH, ldapSettings.getAccountId())).thenReturn(true);
    ssoSettingService.createLdapSettings(ldapSettings);
    ldapSettings.getConnectionSettings().setBindPassword("randomPassword");
    ldapSettings.getConnectionSettings().setBindSecret("randomuuid".toCharArray());
    ldapSettings.getConnectionSettings().setEncryptedBindSecret("randomuuid");
    ssoSettingService.updateLdapSettings(ldapSettings);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void updateLDAPSettingWithSecretToInlinePassword() {
    LdapSettings ldapSettings = createLDAOSSOProviderWithSecret();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.getConnectionSettings().setPasswordType(LdapConnectionSettings.SECRET);
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setBindSecret("randomuuid".toCharArray());
    ldapSettings.getConnectionSettings().setEncryptedBindSecret("randomuuid");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyListOf(EncryptedDataDetail.class), eq(false)))
        .thenReturn(null);
    when(featureFlagService.isEnabled(FeatureName.LDAP_SECRET_AUTH, ldapSettings.getAccountId())).thenReturn(true);
    ssoSettingService.createLdapSettings(ldapSettings);
    ldapSettings.getConnectionSettings().setBindPassword("bindPassword");
    LdapSettings updatedLdapSetting = ssoSettingService.updateLdapSettings(ldapSettings);
    assertThat(updatedLdapSetting.getConnectionSettings().getPasswordType())
        .isEqualTo(LdapConnectionSettings.INLINE_SECRET);
  }

  public LdapSettings createLDAOSSOProvider() {
    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setBindPassword("testBindPassword");
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    List<LdapUserSettings> userSettingsList = new ArrayList<>();
    userSettingsList.add(userSettings);
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");
    ldapSettings =
        new LdapSettings("testSettings", accountId, connectionSettings, userSettingsList, Arrays.asList(groupSettings));
    return ldapSettings;
  }

  public LdapSettings createLDAOSSOProviderWithSecret() {
    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setBindSecret("testBindSecret".toCharArray());
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    List<LdapUserSettings> userSettingsList = new ArrayList<>();
    userSettingsList.add(userSettings);
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");
    ldapSettings =
        new LdapSettings("testSettings", accountId, connectionSettings, userSettingsList, Arrays.asList(groupSettings));
    return ldapSettings;
  }
}
