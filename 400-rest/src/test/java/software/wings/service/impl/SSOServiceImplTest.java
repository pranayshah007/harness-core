/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.ng.core.account.AuthenticationMechanism.LDAP;
import static io.harness.ng.core.account.AuthenticationMechanism.OAUTH;
import static io.harness.ng.core.account.AuthenticationMechanism.SAML;
import static io.harness.ng.core.account.AuthenticationMechanism.USER_PASSWORD;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.loginSettings.LoginSettingsConstants.AUTHENTICATION_MECHANISM_UPDATED;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.OauthProviderType;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.Account;
import software.wings.beans.Event;
import software.wings.beans.loginSettings.events.LoginSettingsAuthMechanismUpdateEvent;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SAMLProviderType;
import software.wings.security.authentication.SSOConfig;
import software.wings.security.saml.SamlClientService;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.coveo.saml.SamlClient;
import com.coveo.saml.SamlException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Vaibhav Tulsyan
 * 14/Jun/2019
 */
@OwnedBy(HarnessTeam.PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class SSOServiceImplTest extends WingsBaseTest {
  private static final String APP_ID = "app_id";

  @Mock private AuthHandler authHandler;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private SamlClientService samlClientService;
  @Mock private SecretManager secretManager;
  @Mock private EncryptionService encryptionService;
  @Mock private FeatureFlagService featureFlagService;
  @InjectMocks @Inject private SSOService ssoService;
  @InjectMocks @Inject private AccountService accountService;
  @InjectMocks @Inject private SSOSettingService ssoSettingService;
  @InjectMocks @Inject private SSOServiceHelper ssoServiceHelper;
  @Inject private OutboxService outboxService;

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void test_OauthAndUserPasswordCoexistence() {
    Account account = Account.Builder.anAccount()
                          .withUuid("Account 1")
                          .withOauthEnabled(false)
                          .withAccountName("Account 1")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 2")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();
    accountService.save(account, false);

    // Testcases to check logic for co-existence of USERNAME_PASSWORD and OAUTH
    // UP = USER_PASSWORD, OA = OAUTH

    // UP -> UP + OA - enable oauth
    ssoService.uploadOauthConfiguration(account.getUuid(), "", Sets.newHashSet(OauthProviderType.values()));
    ssoService.setAuthenticationMechanism(account.getUuid(), OAUTH);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(USER_PASSWORD);
    assertThat(account.isOauthEnabled()).isTrue();

    // UP + OA -> OA - disable user password
    ssoService.setAuthenticationMechanism(account.getUuid(), OAUTH);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(OAUTH);
    assertThat(account.isOauthEnabled()).isTrue();

    // OA -> UP + OA - enable user password
    ssoService.setAuthenticationMechanism(account.getUuid(), USER_PASSWORD);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(USER_PASSWORD);
    assertThat(account.isOauthEnabled()).isTrue();

    // UP + OA -> UP - disable oauth
    ssoService.setAuthenticationMechanism(account.getUuid(), USER_PASSWORD);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(USER_PASSWORD);
    assertThat(account.isOauthEnabled()).isFalse();

    accountService.delete(account.getUuid());
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void test_deleteSamlConfiguration() throws SamlException {
    String accountId = "test";
    Account account = Account.Builder.anAccount()
                          .withUuid(accountId)
                          .withOauthEnabled(true)
                          .withAccountName("Account 2")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 2")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .withNextGenEnabled(true)
                          .build();
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(new ArrayList<>());

    accountService.save(account, false);
    ssoService.uploadOauthConfiguration(
        accountId, "", ImmutableSet.of(OauthProviderType.GOOGLE, OauthProviderType.BITBUCKET));
    SamlClient samlClient = mock(SamlClient.class);
    doReturn(samlClient).when(samlClientService).getSamlClient(anyString(), anyString());
    doReturn("https://harness.onelogin.com").when(samlClient).getIdentityProviderUrl();

    // Upload SAML config and enable
    ssoService.uploadSamlConfiguration(accountId, new ByteArrayInputStream("test data".getBytes()), "test", "", false,
        "", "", SAMLProviderType.ONELOGIN.name(), anyString(), any(), "testOtherSamlName", false);
    ssoService.setAuthenticationMechanism(accountId, SAML);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(SAML);
    assertThat(account.isOauthEnabled()).isFalse();

    ssoService.deleteSamlConfiguration(accountId);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(USER_PASSWORD);
    assertThat(account.isOauthEnabled()).isTrue();

    accountService.delete(account.getUuid());
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void test_deleteSamlConfigurationWithJustUsernamePassword() throws SamlException {
    String accountId = "test";
    Account account = Account.Builder.anAccount()
                          .withUuid(accountId)
                          .withOauthEnabled(false)
                          .withAccountName("Account 2")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 2")
                          .withNextGenEnabled(false)
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();

    accountService.save(account, false);
    SamlClient samlClient = mock(SamlClient.class);
    doReturn(samlClient).when(samlClientService).getSamlClient(anyString(), anyString());
    doReturn("https://harness.onelogin.com").when(samlClient).getIdentityProviderUrl();

    // Upload SAML config and enable
    ssoService.uploadSamlConfiguration(accountId, new ByteArrayInputStream("test data".getBytes()), "test", "", false,
        "", "", SAMLProviderType.ONELOGIN.name(), anyString(), any(), "testOtherSamlName", false);
    ssoService.setAuthenticationMechanism(accountId, SAML);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(SAML);
    assertThat(account.isOauthEnabled()).isFalse();

    ssoService.deleteSamlConfiguration(accountId);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(USER_PASSWORD);
    assertThat(account.isOauthEnabled()).isFalse();

    accountService.delete(account.getUuid());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void test_UserPasswordToSAMLAuthMechanismUpdate() {
    Account account = Account.Builder.anAccount()
                          .withUuid("Account 2")
                          .withOauthEnabled(false)
                          .withAccountName("Account 2")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 2")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();
    accountService.save(account, false);
    ssoService.setAuthenticationMechanism(account.getUuid(), SAML);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(SAML);
    assertThat(account.isOauthEnabled()).isFalse();
    accountService.delete(account.getUuid());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void test_UserPasswordToLDAPAuthMechanismUpdate() {
    Account account = Account.Builder.anAccount()
                          .withUuid("Account 3")
                          .withOauthEnabled(false)
                          .withAccountName("Account 3")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 3")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();
    accountService.save(account, false);
    ssoService.setAuthenticationMechanism(account.getUuid(), LDAP);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(LDAP);
    assertThat(account.isOauthEnabled()).isFalse();
    accountService.delete(account.getUuid());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAccessManagement() {
    Account account = Account.Builder.anAccount()
                          .withUuid("Account 3")
                          .withOauthEnabled(false)
                          .withAccountName("Account 3")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 3")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();
    accountService.save(account, false);
    doNothing().when(authHandler).authorizeAccountPermission(anyList());
    SSOConfig accountAccessManagementSettings = ssoService.getAccountAccessManagementSettings(account.getUuid());
    assertThat(accountAccessManagementSettings).isNotNull();

    doThrow(new InvalidRequestException("INVALID")).when(authHandler).authorizeAccountPermission(anyList());
    try {
      ssoService.getAccountAccessManagementSettings(account.getUuid());
    } catch (InvalidRequestException ex) {
      assertThat(ex.getCode()).isEqualTo(ErrorCode.USER_NOT_AUTHORIZED);
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void test_uploadOauthSetting_thenSetAuthMechanismAsOauth() {
    Account account = Account.Builder.anAccount()
                          .withUuid("Account 4")
                          .withOauthEnabled(false)
                          .withAccountName("Account 4")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 4")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();
    accountService.save(account, false);
    OauthSettings response =
        ssoSettingService.saveOauthSettings(OauthSettings.builder()
                                                .accountId(account.getUuid())
                                                .allowedProviders(Sets.newHashSet(OauthProviderType.GITHUB))
                                                .displayName("Some display name")
                                                .build());
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(USER_PASSWORD);
    assertThat(account.isOauthEnabled()).isFalse();

    ssoService.setAuthenticationMechanism(account.getUuid(), OAUTH);
    account = accountService.get(account.getUuid());
    assertThat(account.getAuthenticationMechanism()).isEqualTo(USER_PASSWORD);
    assertThat(account.isOauthEnabled()).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUploadOauthSettingAuditing() {
    Account account = Account.Builder.anAccount()
                          .withUuid("Account 4")
                          .withOauthEnabled(false)
                          .withAccountName("Account 4")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 4")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();
    accountService.save(account, false);
    OauthSettings response =
        ssoSettingService.saveOauthSettings(OauthSettings.builder()
                                                .accountId(account.getUuid())
                                                .allowedProviders(Sets.newHashSet(OauthProviderType.GITHUB))
                                                .displayName("Some display name")
                                                .build());

    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(account.getUuid()), eq(null), any(OauthSettings.class), eq(Event.Type.CREATE));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void test_GetLdapSettingWithEncryptedDataDetail_Password() {
    final String testAccountId = "testAccountId";
    final String displayName = "testSettings";
    final String bindPassword = "bindPassword";

    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setBindPassword("testBindPassword");
    connectionSettings.setPasswordType(LdapConnectionSettings.INLINE_SECRET);
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    List<LdapUserSettings> userSettingsList = new ArrayList<>();
    userSettingsList.add(userSettings);
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");

    LdapSettings ldapSettings = new LdapSettings(
        displayName, testAccountId, connectionSettings, userSettingsList, Arrays.asList(groupSettings));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().fieldName(bindPassword).build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    when(secretManager.encryptedDataDetails(any(), any(), any(), any())).thenReturn(Optional.of(encryptedDataDetail));
    ldapSettings.getConnectionSettings().setEncryptedBindPassword("EncryptedBindPassword");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyList(), eq(false))).thenReturn(null);
    ssoSettingService.createLdapSettings(ldapSettings);

    LdapSettingsWithEncryptedDataDetail resultDetails =
        ssoService.getLdapSettingWithEncryptedDataDetail(testAccountId, null);
    assertThat(resultDetails.getLdapSettings().getAccountId()).isEqualTo(testAccountId);
    assertThat(resultDetails.getLdapSettings().getDisplayName()).isEqualTo(displayName);
    assertNotNull(resultDetails.getEncryptedDataDetail());
    assertThat(resultDetails.getEncryptedDataDetail().getFieldName()).isEqualTo(bindPassword);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void test_GetLdapSettingWithEncryptedDataDetail_Secret() {
    final String testAccountId = "testAccountId";
    final String displayName = "testSettings";
    final String bindSecret = "bindSecret";

    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setBindSecret("testBindSecret".toCharArray());
    connectionSettings.setPasswordType(LdapConnectionSettings.SECRET);
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    List<LdapUserSettings> userSettingsList = new ArrayList<>();
    userSettingsList.add(userSettings);
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");

    LdapSettings ldapSettings = new LdapSettings(
        displayName, testAccountId, connectionSettings, userSettingsList, Collections.singletonList(groupSettings));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().fieldName(bindSecret).build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(encryptedDataDetail);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);
    when(secretManager.encryptedDataDetails(any(), any(), any(), any())).thenReturn(Optional.of(encryptedDataDetail));
    ldapSettings.getConnectionSettings().setEncryptedBindSecret("EncryptedBindSecret");
    when(encryptionService.decrypt(any(EncryptableSetting.class), anyList(), eq(false))).thenReturn(null);
    ssoSettingService.createLdapSettings(ldapSettings);

    LdapSettingsWithEncryptedDataDetail resultDetails =
        ssoService.getLdapSettingWithEncryptedDataDetail(testAccountId, null);
    assertThat(resultDetails.getLdapSettings().getAccountId()).isEqualTo(testAccountId);
    assertThat(resultDetails.getLdapSettings().getDisplayName()).isEqualTo(displayName);
    assertNotNull(resultDetails.getEncryptedDataDetail());
    assertThat(resultDetails.getEncryptedDataDetail().getFieldName()).isEqualTo(bindSecret);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSetAuthenticationMechanism_forNGAudits() throws JsonProcessingException {
    Account account = Account.Builder.anAccount()
                          .withUuid("Account 4")
                          .withOauthEnabled(false)
                          .withAccountName("Account 4")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 4")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();
    accountService.save(account, false);

    ssoService.setAuthenticationMechanism(account.getUuid(), SAML);
    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(AUTHENTICATION_MECHANISM_UPDATED);
    LoginSettingsAuthMechanismUpdateEvent loginSettingsAuthMechanismUpdateEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
            outboxEvent.getEventData(), LoginSettingsAuthMechanismUpdateEvent.class);

    assertThat(loginSettingsAuthMechanismUpdateEvent.getAccountIdentifier()).isEqualTo(account.getUuid());
    assertThat(loginSettingsAuthMechanismUpdateEvent.getOldAuthMechanismYamlDTO().getAuthenticationMechanism())
        .isEqualTo(USER_PASSWORD);
    assertThat(loginSettingsAuthMechanismUpdateEvent.getNewAuthMechanismYamlDTO().getAuthenticationMechanism())
        .isEqualTo(SAML);

    ssoService.setAuthenticationMechanism(account.getUuid(), LDAP);
    outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo(AUTHENTICATION_MECHANISM_UPDATED);
    loginSettingsAuthMechanismUpdateEvent = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(
        outboxEvent.getEventData(), LoginSettingsAuthMechanismUpdateEvent.class);

    assertThat(loginSettingsAuthMechanismUpdateEvent.getAccountIdentifier()).isEqualTo(account.getUuid());
    assertThat(loginSettingsAuthMechanismUpdateEvent.getOldAuthMechanismYamlDTO().getAuthenticationMechanism())
        .isEqualTo(SAML);
    assertThat(loginSettingsAuthMechanismUpdateEvent.getNewAuthMechanismYamlDTO().getAuthenticationMechanism())
        .isEqualTo(LDAP);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testAccessManagementSettingsV2() {
    Account account = Account.Builder.anAccount()
                          .withUuid("Account 1")
                          .withOauthEnabled(false)
                          .withAccountName("Account 1")
                          .withLicenseInfo(getLicenseInfo())
                          .withAppId(APP_ID)
                          .withCompanyName("Account 1")
                          .withAuthenticationMechanism(USER_PASSWORD)
                          .build();
    accountService.save(account, false);
    doNothing().when(authHandler).authorizeAccountPermission(anyList());
    SSOConfig accountAccessManagementSettings = ssoService.getAccountAccessManagementSettingsV2(account.getUuid());
    assertThat(accountAccessManagementSettings).isNotNull();
  }
}
