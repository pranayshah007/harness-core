/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.SHASHANK;

import static software.wings.beans.loginSettings.LoginSettingsConstants.LDAP_SSO_CREATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.LDAP_SSO_DELETED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.LDAP_SSO_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.OAUTH_PROVIDER_CREATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.OAUTH_PROVIDER_DELETED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.OAUTH_PROVIDER_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.SAML_SSO_CREATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.SAML_SSO_DELETED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.SAML_SSO_UPDATED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.OauthProviderType;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.usergroups.UserGroupClient;

import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.Account;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertType;
import software.wings.beans.loginSettings.events.LoginSettingsLDAPCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsLDAPDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsLDAPUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLUpdateEvent;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SSOSettings.SSOSettingsKeys;
import software.wings.beans.sso.SamlSettings;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.scheduler.LdapSyncJobConfig;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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
  @Inject private OutboxService outboxService;
  @Mock private AccountService accountService;
  private UserGroupClient userGroupClient;

  @Before
  public void setup() {
    userGroupClient = mock(UserGroupClient.class, RETURNS_DEEP_STUBS);
  }

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
    LdapSettings ldapSettings = createLDAPSSOProvider();
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
    LdapSettings ldapSettings = createLDAPSSOProvider();
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
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/14 * 1/1 * ? *");
    ssoSettingService.createLdapSettings(ldapSettings);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldHaveNextIterations() {
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations().size()).isGreaterThan(1);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldHaveNextIterationsWithOnlyDefaultCronSet() {
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setDefaultCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    assertThat(ldapSettings.getNextIterations()).isNotEmpty();
    assertThat(ldapSettings.getNextIterations().size()).isGreaterThan(1);
  }

  @Test(expected = NullPointerException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldFailNextIterationsWithNoCronOrDefaultCron() {
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldFailNextIterationsForWrongCron() {
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/A * 1/1 * ? *");
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldNotSaveForWrongCron() {
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/A * 1/1 * ? *");
    ssoSettingService.createLdapSettings(ldapSettings);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldNotReCalculateNextIterationsForExistingListOfMoreThanTwo() {
    LdapSettings ldapSettings = createLDAPSSOProvider();
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
    LdapSettings ldapSettings = createLDAPSSOProvider();
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
    LdapSettings ldapSettings = createLDAPSSOProviderWithSecret();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.getConnectionSettings().setPasswordType(LdapConnectionSettings.SECRET);
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setBindSecret("randomuuid".toCharArray());
    ldapSettings.getConnectionSettings().setEncryptedBindSecret("randomuuid");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyList(), eq(false))).thenReturn(null);
    LdapSettings createdLdapSetting = ssoSettingService.createLdapSettings(ldapSettings);
    assertThat(createdLdapSetting.getConnectionSettings().getPasswordType()).isEqualTo(LdapConnectionSettings.SECRET);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void createLDAPSettingWithInlinePassword() {
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setEncryptedBindPassword("EncryptedBindPassword");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyList(), eq(false))).thenReturn(null);
    LdapSettings createdLdapSetting = ssoSettingService.createLdapSettings(ldapSettings);
    assertThat(createdLdapSetting.getConnectionSettings().getPasswordType())
        .isEqualTo(LdapConnectionSettings.INLINE_SECRET);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void updateLDAPSettingWithInlinePasswordToSecret() {
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setEncryptedBindPassword("EncryptedBindPassword");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyList(), eq(false))).thenReturn(null);
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
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setEncryptedBindPassword("EncryptedBindPassword");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyList(), eq(false))).thenReturn(null);
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
    LdapSettings ldapSettings = createLDAPSSOProviderWithSecret();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings.getConnectionSettings().setPasswordType(LdapConnectionSettings.SECRET);
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    List<EncryptedDataDetail> encryptedDataDetails = Arrays.asList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    ldapSettings.getConnectionSettings().setBindSecret("randomuuid".toCharArray());
    ldapSettings.getConnectionSettings().setEncryptedBindSecret("randomuuid");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyList(), eq(false))).thenReturn(null);
    ssoSettingService.createLdapSettings(ldapSettings);
    ldapSettings.getConnectionSettings().setBindPassword("bindPassword");
    LdapSettings updatedLdapSetting = ssoSettingService.updateLdapSettings(ldapSettings);
    assertThat(updatedLdapSetting.getConnectionSettings().getPasswordType())
        .isEqualTo(LdapConnectionSettings.INLINE_SECRET);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveSamlSettingsWithoutCGLicenseCheck_ForSAMLCreateUpdateDeleteNGAudits() throws IOException {
    SamlSettings samlSettings = SamlSettings.builder()
                                    .metaDataFile("TestMetaDataFile")
                                    .url("oktaIdpUrl")
                                    .accountId(accountId)
                                    .displayName("Okta")
                                    .origin("dev-274703.oktapreview.com")
                                    .build();
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(new ArrayList<>());
    SamlSettings savedSamlSettings = ssoSettingService.saveSamlSettingsWithoutCGLicenseCheck(samlSettings);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(SAML_SSO_CREATED);
    LoginSettingsSAMLCreateEvent loginSettingsSAMLCreateEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsSAMLCreateEvent.class);

    assertThat(loginSettingsSAMLCreateEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsSAMLCreateEvent.getOldSamlSettingsYamlDTO()).isEqualTo(null);
    assertThat(loginSettingsSAMLCreateEvent.getNewSamlSettingsYamlDTO().getSamlSettings()).isEqualTo(savedSamlSettings);

    SamlSettings newSamlSettings = SamlSettings.builder()
                                       .metaDataFile("TestMetaDataFile")
                                       .url("microsoftOnlineUrl")
                                       .accountId(accountId)
                                       .displayName("Azure 1")
                                       .origin("login.microsoftonline.com")
                                       .build();
    SamlSettings newSavedSamlSettings = ssoSettingService.saveSamlSettingsWithoutCGLicenseCheck(newSamlSettings);
    outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(SAML_SSO_UPDATED);
    LoginSettingsSAMLUpdateEvent loginSettingsSAMLUpdateEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsSAMLUpdateEvent.class);

    assertThat(loginSettingsSAMLUpdateEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsSAMLUpdateEvent.getOldSamlSettingsYamlDTO().getSamlSettings()).isEqualTo(savedSamlSettings);
    assertThat(loginSettingsSAMLUpdateEvent.getNewSamlSettingsYamlDTO().getSamlSettings())
        .isEqualTo(newSavedSamlSettings);

    boolean isSamlSettingsDeleted = ssoSettingService.deleteSamlSettings(accountId);
    assertThat(isSamlSettingsDeleted).isTrue();
    outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(SAML_SSO_DELETED);
    LoginSettingsSAMLDeleteEvent loginSettingsSAMLDeleteEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsSAMLDeleteEvent.class);

    assertThat(loginSettingsSAMLDeleteEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsSAMLDeleteEvent.getOldSamlSettingsYamlDTO().getSamlSettings())
        .isEqualTo(newSavedSamlSettings);
    assertThat(loginSettingsSAMLDeleteEvent.getNewSamlSettingsYamlDTO()).isEqualTo(null);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSaveOauthSettings_ForOAuthCreateUpdateDeleteNGAudits() throws IOException {
    Account account = new Account();
    account.setUuid(accountId);
    when(accountService.get(accountId)).thenReturn(account);

    OauthSettings oauthSettings = OauthSettings.builder()
                                      .accountId(accountId)
                                      .allowedProviders(Sets.newHashSet(OauthProviderType.GITHUB))
                                      .displayName("DisplayName")
                                      .build();
    OauthSettings savedOauthSettings = ssoSettingService.saveOauthSettings(oauthSettings);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(OAUTH_PROVIDER_CREATED);
    LoginSettingsOAuthCreateEvent loginSettingsOAuthCreateEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsOAuthCreateEvent.class);

    assertThat(loginSettingsOAuthCreateEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsOAuthCreateEvent.getOldOAuthSettingsYamlDTO()).isEqualTo(null);
    assertThat(loginSettingsOAuthCreateEvent.getNewOAuthSettingsYamlDTO().getOauthSettings())
        .isEqualTo(savedOauthSettings);

    OauthSettings newOauthSettings =
        OauthSettings.builder()
            .accountId(accountId)
            .allowedProviders(Sets.newHashSet(OauthProviderType.GITHUB, OauthProviderType.AZURE))
            .displayName("NewDisplayName")
            .build();
    OauthSettings newSaveOauthSettings = ssoSettingService.saveOauthSettings(newOauthSettings);
    outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(OAUTH_PROVIDER_UPDATED);
    LoginSettingsOAuthUpdateEvent loginSettingsOAuthUpdateEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsOAuthUpdateEvent.class);

    assertThat(loginSettingsOAuthUpdateEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsOAuthUpdateEvent.getOldOAuthSettingsYamlDTO().getOauthSettings())
        .isEqualTo(savedOauthSettings);
    assertThat(loginSettingsOAuthUpdateEvent.getNewOAuthSettingsYamlDTO().getOauthSettings())
        .isEqualTo(newSaveOauthSettings);

    boolean isOauthSettingsDeleted = ssoSettingService.deleteOauthSettings(accountId);
    assertThat(isOauthSettingsDeleted).isTrue();
    outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(OAUTH_PROVIDER_DELETED);
    LoginSettingsOAuthDeleteEvent loginSettingsOAuthDeleteEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsOAuthDeleteEvent.class);

    assertThat(loginSettingsOAuthDeleteEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsOAuthDeleteEvent.getOldOAuthSettingsYamlDTO().getOauthSettings())
        .isEqualTo(newSaveOauthSettings);
    assertThat(loginSettingsOAuthDeleteEvent.getNewOAuthSettingsYamlDTO()).isEqualTo(null);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreateLdapSettings_ForLDAPCreateUpdateDeleteNGAudits() throws IOException {
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(new ArrayList<>());
    LdapSettings savedLdapSettings = ssoSettingService.createLdapSettings(ldapSettings);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(LDAP_SSO_CREATED);
    LoginSettingsLDAPCreateEvent loginSettingsLDAPCreateEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsLDAPCreateEvent.class);

    assertThat(loginSettingsLDAPCreateEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsLDAPCreateEvent.getOldLdapSettingsYamlDTO()).isEqualTo(null);
    assertThat(loginSettingsLDAPCreateEvent.getNewLdapSettingsYamlDTO().getLdapSettings()).isEqualTo(savedLdapSettings);

    LdapSettings newLdapSettings = LdapSettings.builder()
                                       .accountId(accountId)
                                       .displayName("newLdapSettings")
                                       .connectionSettings(new LdapConnectionSettings())
                                       .userSettingsList(Arrays.asList(new LdapUserSettings()))
                                       .groupSettingsList(Arrays.asList(new LdapGroupSettings()))
                                       .build();
    newLdapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    LdapSettings newSavedLdapSettings = ssoSettingService.updateLdapSettings(newLdapSettings);
    outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(LDAP_SSO_UPDATED);
    LoginSettingsLDAPUpdateEvent loginSettingsLDAPUpdateEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsLDAPUpdateEvent.class);

    assertThat(loginSettingsLDAPUpdateEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsLDAPUpdateEvent.getOldLdapSettingsYamlDTO().getLdapSettings()).isEqualTo(savedLdapSettings);
    assertThat(loginSettingsLDAPUpdateEvent.getNewLdapSettingsYamlDTO().getLdapSettings())
        .isEqualTo(newSavedLdapSettings);

    ssoSettingService.deleteLdapSettings(accountId);
    outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(LDAP_SSO_DELETED);
    LoginSettingsLDAPDeleteEvent loginSettingsLDAPDeleteEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsLDAPDeleteEvent.class);

    assertThat(loginSettingsLDAPDeleteEvent.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(loginSettingsLDAPDeleteEvent.getOldLdapSettingsYamlDTO().getLdapSettings())
        .isEqualTo(newSavedLdapSettings);
    assertThat(loginSettingsLDAPDeleteEvent.getNewLdapSettingsYamlDTO()).isEqualTo(null);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void deleteLdapSettingsNGNotEnabled() {
    // Arrange
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings = ssoSettingService.createLdapSettings(ldapSettings);
    when(accountService.isNextGenEnabled(ldapSettings.getAccountId())).thenReturn(false);

    // Act
    LdapSettings deletedLdapSettings = ssoSettingService.deleteLdapSettings(ldapSettings.getAccountId());

    // Assert
    assertThat(deletedLdapSettings.getUuid()).isNotEmpty();
    assertThat(deletedLdapSettings.getCronExpression()).isNotEmpty().isEqualTo("0 0/15 * 1/1 * ? *");
    assertThat(deletedLdapSettings.getNextIterations()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void deleteLdapSettingsNoLinkedSsoNG() {
    // Arrange
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ldapSettings = ssoSettingService.createLdapSettings(ldapSettings);
    when(accountService.isNextGenEnabled(ldapSettings.getAccountId())).thenReturn(true);

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(new ArrayList<>());

    // Act
    LdapSettings deletedLdapSettings = ssoSettingService.deleteLdapSettings(ldapSettings.getAccountId());

    // Assert
    assertThat(deletedLdapSettings.getUuid()).isNotEmpty();
    assertThat(deletedLdapSettings.getCronExpression()).isNotEmpty().isEqualTo("0 0/15 * 1/1 * ? *");
    assertThat(deletedLdapSettings.getNextIterations()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void deleteLdapSettingsWithLinkedSsoNG() {
    // Arrange
    LdapSettings ldapSettings = createLDAPSSOProvider();
    ldapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    final LdapSettings createdLdapSettings = ssoSettingService.createLdapSettings(ldapSettings);
    when(accountService.isNextGenEnabled(ldapSettings.getAccountId())).thenReturn(true);

    UserGroupDTO testUGDto = UserGroupDTO.builder()
                                 .accountIdentifier(ldapSettings.getAccountId())
                                 .identifier("UG_Test_Identifier_01")
                                 .build();
    List<UserGroupDTO> ugDtoList = new ArrayList<>();
    ugDtoList.add(testUGDto);

    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(ugDtoList);

    // Act & Assert
    assertThatThrownBy(() -> ssoSettingService.deleteLdapSettings(createdLdapSettings.getAccountId()))
        .isInstanceOf(InvalidRequestException.class);
  }

  public LdapSettings createLDAPSSOProvider() {
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

  public LdapSettings createLDAPSSOProviderWithSecret() {
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
