/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.SAMLProviderType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataAndPasswordDetail;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapTestResponse.Status;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlSettings;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.security.authentication.SSOConfig;
import software.wings.security.saml.SamlClientService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.security.SecretManager;

import com.coveo.saml.SamlClient;
import com.coveo.saml.SamlException;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.ldaptive.ResultCode;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class SSOServiceTest extends WingsBaseTest {
  @Mock private AccountService ACCOUNT_SERVICE;
  @Mock private SSOSettingService SSO_SETTING_SERVICE;
  @Mock private SamlClientService SAML_CLIENT_SERVICE;
  @Mock private DelegateProxyFactory DELEGATE_PROXY_FACTORY;
  @Mock private LdapDelegateService LDAP_DELEGATE_SERVICE;
  @Mock private SecretManager SECRET_MANAGER;
  @Mock private FeatureFlagService FEATURE_FLAG_SERVICE;
  ;

  private LdapSettings ldapSettings;
  @Inject @InjectMocks private SSOService ssoService;

  public static final String logoutUrl = "logout_url";

  @Before
  public void setup() {
    EncryptedRecordData encryptedData = mock(EncryptedRecordData.class);
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    when(SECRET_MANAGER.encryptedDataDetails(eq(ACCOUNT_ID), eq(LdapConstants.BIND_PASSWORD_KEY), any(), eq(null)))
        .thenReturn(Optional.of(encryptedDataDetail));
    when(SECRET_MANAGER.encryptedDataDetails(eq(ACCOUNT_ID), eq(LdapConstants.USER_PASSWORD_KEY), any(), eq(null)))
        .thenReturn(Optional.of(encryptedDataDetail));
    when(SECRET_MANAGER.deleteSecret(anyString(), anyString(), eq(new HashMap<>()), eq(false))).thenReturn(true);
    when(encryptedDataDetail.getEncryptedData()).thenReturn(encryptedData);
    when(encryptedData.getUuid()).thenReturn("UUID");
    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setBindPassword("testBindPassword");
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    List<LdapUserSettings> userSettingsList = new ArrayList<>();
    userSettingsList.add(userSettings);
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");
    ldapSettings = new LdapSettings(
        "testSettings", ACCOUNT_ID, connectionSettings, userSettingsList, Arrays.asList(groupSettings));
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void uploadSamlConfiguration() throws IOException, SamlException {
    Account account = new Account();
    account.setAccountName("account_name");
    account.setCompanyName("company_name");
    account.setUuid("accountId");
    final String friendlySamlName = "testFriendlySamlName";

    account.setAuthenticationMechanism(AuthenticationMechanism.SAML);
    String xml = IOUtils.toString(getClass().getResourceAsStream("/okta-IDP-metadata.xml"), Charset.defaultCharset());
    when(ACCOUNT_SERVICE.get(anyString())).thenReturn(account);
    when(ACCOUNT_SERVICE.update(account)).thenReturn(account);
    when(SAML_CLIENT_SERVICE.getSamlClient(anyString(), anyString())).thenCallRealMethod();

    SamlSettings mockSamlSettings = SamlSettings.builder().build();
    mockSamlSettings.setAccountId(account.getUuid());
    mockSamlSettings.setUrl(
        "https://dev-274703.oktapreview.com/app/harnessiodev274703_testapp_1/exkefa5xlgHhrU1Mc0h7/sso/saml");
    mockSamlSettings.setMetaDataFile(xml);
    mockSamlSettings.setDisplayName("Okta");
    mockSamlSettings.setFriendlySamlName(friendlySamlName);
    when(SSO_SETTING_SERVICE.getSamlSettingsByAccountId(anyString())).thenReturn(mockSamlSettings);
    when(FEATURE_FLAG_SERVICE.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid())).thenReturn(true);

    SSOConfig settings = ssoService.uploadSamlConfiguration(account.getUuid(),
        getClass().getResourceAsStream("/okta-IDP-metadata.xml"), "Okta", "group", true, logoutUrl, "app.harness.io",
        SAMLProviderType.OKTA.name(), anyString(), any(), friendlySamlName, false, false, null, null);
    String idpRedirectUrl = ((SamlSettings) settings.getSsoSettings().get(0)).getUrl();
    assertThat(idpRedirectUrl)
        .isEqualTo("https://dev-274703.oktapreview.com/app/harnessiodev274703_testapp_1/exkefa5xlgHhrU1Mc0h7/sso/saml");
    assertThat(settings.getSsoSettings().get(0).getDisplayName()).isEqualTo("Okta");
    assertThat(((SamlSettings) settings.getSsoSettings().get(0)).getFriendlySamlName()).isEqualTo(friendlySamlName);

    try {
      ssoService.uploadSamlConfiguration(account.getUuid(), getClass().getResourceAsStream("/SamlResponse.txt"), "Okta",
          "group", true, logoutUrl, "app.harness.io", SAMLProviderType.OKTA.name(), anyString(), any(),
          friendlySamlName, false, false, null, null);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_SAML_CONFIGURATION.name());
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateLogoutUrlSamlSettings() {
    Account account = getAccount(AccountType.PAID);
    account.setAuthenticationMechanism(AuthenticationMechanism.SAML);

    String logoutUrl = "logout_url";
    String updatedLogoutUrl = "updated_logout_url";

    SamlSettings samlSettings =
        SamlSettings.builder().accountId(account.getUuid()).ssoType(SSOType.SAML).logoutUrl(logoutUrl).build();
    samlSettings.setAuthenticationEnabled(true);

    when(SSO_SETTING_SERVICE.getSamlSettingsByAccountId(account.getUuid())).thenReturn(samlSettings);
    when(FEATURE_FLAG_SERVICE.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid())).thenReturn(true);
    when(ACCOUNT_SERVICE.get(account.getUuid())).thenReturn(account);
    SSOConfig ssoConfig = ssoService.updateLogoutUrlSamlSettings(account.getUuid(), updatedLogoutUrl);

    assertThat(SSO_SETTING_SERVICE.getSamlSettingsByAccountId(account.getUuid()).getLogoutUrl()).isNotNull();
    assertThat(SSO_SETTING_SERVICE.getSamlSettingsByAccountId(account.getUuid()).getLogoutUrl())
        .isEqualTo(updatedLogoutUrl);
    assertThat(ssoConfig.getSsoSettings().get(0).getType()).isEqualTo(SSOType.SAML);
    assertThat(((SamlSettings) ssoConfig.getSsoSettings().get(0)).isAuthenticationEnabled()).isEqualTo(true);

    assertThat(((SamlSettings) ssoConfig.getSsoSettings().get(0)).getLogoutUrl()).isNotNull();
    assertThat(((SamlSettings) ssoConfig.getSsoSettings().get(0)).getLogoutUrl()).isEqualTo(updatedLogoutUrl);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void setAuthenticationMechanism() {
    Account account = new Account();
    when(ACCOUNT_SERVICE.get(anyString())).thenReturn(account);
    when(ACCOUNT_SERVICE.update(account)).thenReturn(account);
    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(account.getUuid())
                                    .ssoType(SSOType.SAML)
                                    .displayName("originalDisplayNameSAML")
                                    .logoutUrl(logoutUrl)
                                    .build();
    samlSettings.setUuid("testSAMLUuid");

    when(SSO_SETTING_SERVICE.getSamlSettingsByAccountIdNotConfiguredFromNG("testAccount")).thenReturn(samlSettings);
    SSOConfig settings = ssoService.setAuthenticationMechanism("testAccount", AuthenticationMechanism.SAML, false);
    assertThat(settings.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SAML);

    settings = ssoService.setAuthenticationMechanism("testAccount", AuthenticationMechanism.USER_PASSWORD, false);
    assertThat(settings.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.USER_PASSWORD);

    settings = ssoService.setAuthenticationMechanism("testAccount", AuthenticationMechanism.LDAP, false);
    assertThat(settings.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.LDAP);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void validateLdapConnectionSettings() {
    when(DELEGATE_PROXY_FACTORY.getV2(any(), any())).thenReturn(LDAP_DELEGATE_SERVICE);
    when(LDAP_DELEGATE_SERVICE.validateLdapConnectionSettings(any(), any()))
        .thenReturn(LdapTestResponse.builder().status(Status.SUCCESS).build());
    LdapTestResponse response = ssoService.validateLdapConnectionSettings(ldapSettings, "testAccount");
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    verify(SECRET_MANAGER, times(1)).deleteSecret(anyString(), any(), any(), anyBoolean());
  }
  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void validateLdapConnectionSettingsWithSecret() {
    when(DELEGATE_PROXY_FACTORY.getV2(any(), any())).thenReturn(LDAP_DELEGATE_SERVICE);
    when(LDAP_DELEGATE_SERVICE.validateLdapConnectionSettings(any(), any()))
        .thenReturn(LdapTestResponse.builder().status(Status.SUCCESS).build());
    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setEncryptedBindSecret("secretuuID");
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    List<LdapUserSettings> userSettingsList = new ArrayList<>();
    userSettingsList.add(userSettings);
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");
    LdapSettings ldapSettings2 = new LdapSettings(
        "testSettings", ACCOUNT_ID, connectionSettings, userSettingsList, Arrays.asList(groupSettings));
    LdapTestResponse response = ssoService.validateLdapConnectionSettings(ldapSettings2, "testAccount");
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    verify(SECRET_MANAGER, times(0)).deleteSecret(anyString(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void validateLdapUserSettings() {
    when(DELEGATE_PROXY_FACTORY.getV2(any(), any())).thenReturn(LDAP_DELEGATE_SERVICE);
    when(LDAP_DELEGATE_SERVICE.validateLdapUserSettings(any(), any()))
        .thenReturn(LdapTestResponse.builder().status(Status.SUCCESS).build());
    LdapTestResponse response = ssoService.validateLdapUserSettings(ldapSettings, "testAccount");
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void validateLdapGroupSettings() {
    when(DELEGATE_PROXY_FACTORY.getV2(any(), any())).thenReturn(LDAP_DELEGATE_SERVICE);
    when(LDAP_DELEGATE_SERVICE.validateLdapGroupSettings(any(), any()))
        .thenReturn(LdapTestResponse.builder().status(Status.SUCCESS).build());
    LdapTestResponse response = ssoService.validateLdapGroupSettings(ldapSettings, "testAccount");
    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void validateLdapAuthentication() {
    when(DELEGATE_PROXY_FACTORY.getV2(any(), any())).thenReturn(LDAP_DELEGATE_SERVICE);
    when(LDAP_DELEGATE_SERVICE.authenticate(any(), any(), any(), any()))
        .thenReturn(LdapResponse.builder().status(LdapResponse.Status.SUCCESS).build());
    LdapResponse response = ssoService.validateLdapAuthentication(ldapSettings, "username", "password");
    assertThat(response.getStatus()).isEqualTo(LdapResponse.Status.SUCCESS);
    when(LDAP_DELEGATE_SERVICE.authenticate(any(), any(), any(), any()))
        .thenReturn(LdapResponse.builder()
                        .status(LdapResponse.Status.FAILURE)
                        .message(ResultCode.INAPPROPRIATE_MATCHING.toString())
                        .build());
    response = ssoService.validateLdapAuthentication(ldapSettings, "username", "password");
    assertThat(response.getStatus()).isEqualTo(LdapResponse.Status.FAILURE);
    assertThat(response.getMessage()).contains(ResultCode.INAPPROPRIATE_MATCHING.toString());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void searchGroupsByName() {
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    LdapSettings spyLdapSettings = spy(ldapSettings);
    when(DELEGATE_PROXY_FACTORY.getV2(any(), any())).thenReturn(LDAP_DELEGATE_SERVICE);
    when(LDAP_DELEGATE_SERVICE.searchGroupsByName(any(), any(), any()))
        .thenReturn(Collections.singletonList(LdapGroupResponse.builder().name("testGroup").build()));
    when(SSO_SETTING_SERVICE.getLdapSettingsByUuid(any())).thenReturn(spyLdapSettings);
    doReturn(encryptedDataDetail).when(spyLdapSettings).getEncryptedDataDetails(any());

    Collection<LdapGroupResponse> responses = ssoService.searchGroupsByName("testLdapId", "testQuery");
    assertThat(responses.size()).isEqualTo(1);
    for (LdapGroupResponse response : responses) {
      assertThat(response.getName()).isEqualTo("testGroup");
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetLocallyEncryptedDataDetailForLdapAuth() {
    String testLdapRecordName = "testLdapRecord";
    String password = "password";
    final EncryptedRecordData encryptedRecord = EncryptedRecordData.builder()
                                                    .name(testLdapRecordName)
                                                    .encryptedValue("encryptedTestPassword".toCharArray())
                                                    .kmsId(ACCOUNT_ID)
                                                    .build();
    EncryptedDataDetail encryptedPwdDetail =
        EncryptedDataDetail.builder().fieldName(password).encryptedData(encryptedRecord).build();

    when(SECRET_MANAGER.saveSecretText(anyString(), any(), anyBoolean())).thenReturn("testEncryptedPwd");
    when(SECRET_MANAGER.encryptedDataDetails(anyString(), anyString(), any(), any()))
        .thenReturn(Optional.of(encryptedPwdDetail));
    when(SECRET_MANAGER.deleteSecret(anyString(), anyString(), any(), anyBoolean())).thenReturn(true);
    when(SSO_SETTING_SERVICE.getLdapSettingsByAccountId(anyString())).thenReturn(ldapSettings);

    LdapSettingsWithEncryptedDataAndPasswordDetail withEncryptedDataAndPasswordDetail =
        ssoService.getLdapSettingsWithEncryptedDataAndPasswordDetail(ACCOUNT_ID, testLdapRecordName);
    assertThat(withEncryptedDataAndPasswordDetail.getEncryptedPwdDataDetail().getEncryptedData().getKmsId())
        .isEqualTo(ACCOUNT_ID);
    assertThat(withEncryptedDataAndPasswordDetail.getEncryptedPwdDataDetail().getEncryptedData().getName())
        .isEqualTo(testLdapRecordName);
    assertThat(withEncryptedDataAndPasswordDetail.getEncryptedPwdDataDetail().getFieldName()).isEqualTo(password);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateSamlSettingsConfiguration() throws IOException, SamlException {
    Account account = getAccount(AccountType.PAID);
    account.setAuthenticationMechanism(AuthenticationMechanism.SAML);
    final String uuidStr = UUID.randomUUID().toString();
    final String testDisplayName = "testDisplayNameSAML";
    final String testFriendlySamlName = "testFriendlyNameSAML";

    SamlClient mockSamlClient = mock(SamlClient.class);

    SamlSettings samlSettings =
        SamlSettings.builder()
            .accountId(account.getUuid())
            .ssoType(SSOType.SAML)
            .displayName("originalDisplayNameSAML")
            .origin("login.microsoftonline.com")
            .metaDataFile(
                IOUtils.toString(getClass().getResourceAsStream("/Azure-1-metadata.xml"), Charset.defaultCharset()))
            .logoutUrl(logoutUrl)
            .build();
    samlSettings.setAuthenticationEnabled(true);

    when(SSO_SETTING_SERVICE.getSamlSettingsByAccountIdAndUuid(anyString(), anyString())).thenReturn(samlSettings);
    when(ACCOUNT_SERVICE.get(anyString())).thenReturn(account);
    when(SAML_CLIENT_SERVICE.getSamlClient(anyString(), anyString())).thenReturn(mockSamlClient);
    when(mockSamlClient.getIdentityProviderUrl()).thenReturn("testIdentityUrl");

    SSOConfig ssoConfig = ssoService.updateSamlConfiguration(ACCOUNT_ID, uuidStr, null, testDisplayName,
        "testGrpMembershipAttr", false, "test_logout_url", "testEntityId", "OKTA", "testClientId",
        "testClientSecret".toCharArray(), testFriendlySamlName, false, false, null, null);

    assertThat(ssoConfig).isNotNull();
    assertThat(ssoConfig.getSsoSettings()).isNotNull();
    assertThat(ssoConfig.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SAML);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testDeleteSamlConfigurationSSOId() throws IOException, SamlException {
    Account account = getAccount(AccountType.PAID);
    account.setAuthenticationMechanism(AuthenticationMechanism.SAML);
    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(account.getUuid())
                                    .ssoType(SSOType.SAML)
                                    .displayName("originalDisplayNameSAML")
                                    .logoutUrl(logoutUrl)
                                    .build();
    samlSettings.setAuthenticationEnabled(true);
    List<SamlSettings> samlSettingsList = new ArrayList<>();
    samlSettingsList.add(samlSettings);

    when(ACCOUNT_SERVICE.get(anyString())).thenReturn(account);
    when(SSO_SETTING_SERVICE.getSamlSettingsByAccountIdAndUuid(anyString(), anyString())).thenReturn(samlSettings);
    when(SSO_SETTING_SERVICE.deleteSamlSettingsWithAudits(any(SamlSettings.class))).thenReturn(true);
    when(SSO_SETTING_SERVICE.getSamlSettingsListByAccountId(anyString())).thenReturn(samlSettingsList);
    when(FEATURE_FLAG_SERVICE.isEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, ACCOUNT_ID)).thenReturn(true);
    SSOConfig ssoConfig = ssoService.deleteSamlConfiguration(ACCOUNT_ID, "testSAMLSSOId");
    assertThat(ssoConfig).isNotNull();
    assertThat(ssoConfig.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SAML);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void setAuthenticationMechanism_FF_PL_ENABLE_MULTIPLE_IDP_SUPPORT_Enabled() {
    Account account = new Account();
    account.setUuid("testAccountId");
    account.setAuthenticationMechanism(AuthenticationMechanism.LDAP);

    Account updatedAccount = new Account();
    updatedAccount.setUuid("testAccountId");
    when(ACCOUNT_SERVICE.get(anyString())).thenReturn(account);
    when(ACCOUNT_SERVICE.update(any())).thenReturn(updatedAccount);

    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(account.getUuid())
                                    .ssoType(SSOType.SAML)
                                    .displayName("originalDisplayNameSAML")
                                    .logoutUrl(logoutUrl)
                                    .build();
    samlSettings.setUuid("testSAMLUuid");
    List<SamlSettings> samlSettingsList = new ArrayList<>();
    samlSettingsList.add(samlSettings);

    when(FEATURE_FLAG_SERVICE.isEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, ACCOUNT_ID)).thenReturn(true);
    when(SSO_SETTING_SERVICE.getSamlSettingsByAccountIdNotConfiguredFromNG("testAccountId")).thenReturn(samlSettings);
    when(SSO_SETTING_SERVICE.getSamlSettingsListByAccountId("testAccountId")).thenReturn(samlSettingsList);
    doNothing()
        .when(SSO_SETTING_SERVICE)
        .updateAuthenticationEnabledForSAMLSetting(anyString(), anyString(), anyBoolean());
    SSOConfig settings = ssoService.setAuthenticationMechanism("testAccountId", AuthenticationMechanism.SAML, false);
    assertThat(settings.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SAML);
    verify(SSO_SETTING_SERVICE, times(1))
        .updateAuthenticationEnabledForSAMLSetting(anyString(), anyString(), anyBoolean());
  }
}
