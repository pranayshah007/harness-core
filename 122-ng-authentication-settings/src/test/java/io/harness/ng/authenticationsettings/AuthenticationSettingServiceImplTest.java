/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT;
import static io.harness.rule.OwnerRule.ADITYA;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.authenticationsettings.dtos.AuthenticationSettingsResponse;
import io.harness.ng.authenticationsettings.dtos.mechanisms.LDAPSettings;
import io.harness.ng.authenticationsettings.impl.AuthenticationSettingsServiceImpl;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.loginSettings.PasswordExpirationPolicy;
import software.wings.beans.loginSettings.PasswordStrengthPolicy;
import software.wings.beans.loginSettings.UserLockoutPolicy;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlSettings;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.SSOConfig;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import okhttp3.MultipartBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class AuthenticationSettingServiceImplTest extends CategoryTest {
  @Mock private AuthSettingsManagerClient managerClient;
  @Mock private UserGroupService userGroupService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject @InjectMocks AuthenticationSettingsServiceImpl authenticationSettingsServiceImpl;

  private SamlSettings samlSettings;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  @Before
  public void setup() {
    initMocks(this);
    samlSettings = SamlSettings.builder().accountId(ACCOUNT_ID).build();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    List<String> userGroups = new ArrayList<>();
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(ACCOUNT_ID, samlSettings.getUuid());
    SSOConfig ssoConfig = SSOConfig.builder().accountId(ACCOUNT_ID).build();
    Call<RestResponse<SSOConfig>> config = mock(Call.class);
    doReturn(config).when(managerClient).deleteSAMLMetadata(ACCOUNT_ID);
    RestResponse<SSOConfig> mockConfig = new RestResponse<>(ssoConfig);
    doReturn(Response.success(mockConfig)).when(config).execute();
    SSOConfig expectedConfig = authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
    assertThat(expectedConfig.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata_InvalidSSO_throwsException() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(null);
    doReturn(Response.success(mockResponse)).when(request).execute();
    try {
      authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
      fail("Deleting SAML metadata should fail.");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).isEqualTo("No Saml Metadata found for this account");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata_WithExistingUserGroupsLinked_throwsException() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    List<String> userGroups = Collections.singletonList("userGroup1");
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(ACCOUNT_ID, samlSettings.getUuid());
    try {
      authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
      fail("Deleting SAML metadata should fail.");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo("Deleting Saml provider with linked user groups is not allowed. Unlink the user groups first");
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetLdapSettings() throws IOException {
    final String displayName = "NG_LDAP";
    Call<RestResponse<software.wings.beans.sso.LdapSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getLdapSettings(ACCOUNT_ID);
    RestResponse<software.wings.beans.sso.LdapSettings> mockResponse =
        new RestResponse<>(software.wings.beans.sso.LdapSettings.builder()
                               .displayName(displayName)
                               .connectionSettings(new LdapConnectionSettings())
                               .build());
    doReturn(Response.success(mockResponse)).when(request).execute();
    LDAPSettings ngLdapSettings = authenticationSettingsServiceImpl.getLdapSettings(ACCOUNT_ID);
    assertNotNull(ngLdapSettings);
    assertThat(ngLdapSettings.getDisplayName()).isEqualTo(displayName);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCreateLdapSettings() throws IOException {
    final String displayName = "NG_LDAP";
    final String cronExpr = "0 0/30 * 1/1 * ? *";
    LDAPSettings settings = LDAPSettings.builder()
                                .displayName(displayName)
                                .connectionSettings(new LdapConnectionSettings())
                                .userSettingsList(new ArrayList<>())
                                .groupSettingsList(new ArrayList<>())
                                .cronExpression(cronExpr)
                                .build();
    Call<RestResponse<software.wings.beans.sso.LdapSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).createLdapSettings(anyString(), any());
    software.wings.beans.sso.LdapSettings builtSettings = software.wings.beans.sso.LdapSettings.builder()
                                                              .displayName(displayName)
                                                              .accountId(ACCOUNT_ID)
                                                              .connectionSettings(new LdapConnectionSettings())
                                                              .build();
    builtSettings.setCronExpression(cronExpr);
    RestResponse<software.wings.beans.sso.LdapSettings> mockResponse = new RestResponse<>(builtSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    LDAPSettings createdLDAPSettings = authenticationSettingsServiceImpl.createLdapSettings(ACCOUNT_ID, settings);
    assertNotNull(createdLDAPSettings);
    assertThat(createdLDAPSettings.getDisplayName()).isEqualTo(displayName);
    assertThat(createdLDAPSettings.getCronExpression()).isEqualTo(cronExpr);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateLdapSettings() throws IOException {
    final String displayName = "NG_LDAP_NEW";
    final String cronExpr = "0 0/30 * 1/1 * ? *";
    LDAPSettings settings = LDAPSettings.builder()
                                .displayName(displayName)
                                .connectionSettings(new LdapConnectionSettings())
                                .userSettingsList(new ArrayList<>())
                                .groupSettingsList(new ArrayList<>())
                                .cronExpression(cronExpr)
                                .build();
    Call<RestResponse<software.wings.beans.sso.LdapSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).updateLdapSettings(anyString(), any());
    software.wings.beans.sso.LdapSettings builtSettings = software.wings.beans.sso.LdapSettings.builder()
                                                              .displayName(displayName)
                                                              .accountId(ACCOUNT_ID)
                                                              .connectionSettings(new LdapConnectionSettings())
                                                              .build();
    builtSettings.setCronExpression(cronExpr);
    RestResponse<software.wings.beans.sso.LdapSettings> mockResponse = new RestResponse<>(builtSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    LDAPSettings createdLDAPSettings = authenticationSettingsServiceImpl.updateLdapSettings(ACCOUNT_ID, settings);
    assertNotNull(createdLDAPSettings);
    assertThat(createdLDAPSettings.getDisplayName()).isEqualTo(displayName);
    assertThat(createdLDAPSettings.getCronExpression()).isEqualTo(cronExpr);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testDeleteLdapSettings() throws IOException {
    Call<RestResponse<software.wings.beans.sso.LdapSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getLdapSettings(ACCOUNT_ID);
    RestResponse<software.wings.beans.sso.LdapSettings> mockResponse =
        new RestResponse<>(software.wings.beans.sso.LdapSettings.builder()
                               .displayName("displayName")
                               .connectionSettings(new LdapConnectionSettings())
                               .build());
    doReturn(Response.success(mockResponse)).when(request).execute();
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier("ORG_ID")
                        .projectIdentifier("PROJECT_ID")
                        .ssoGroupId("groupDn")
                        .users(Collections.singletonList("testUserEmail"))
                        .build();
    when(userGroupService.getUserGroupsBySsoId(anyString(), anyString())).thenReturn(Collections.singletonList(ug1));

    doReturn(request).when(managerClient).deleteLdapSettings(ACCOUNT_ID);
    doReturn(Response.success(mockResponse)).when(request).execute();
    authenticationSettingsServiceImpl.deleteLdapSettings(ACCOUNT_ID);
    verify(managerClient, times(1)).deleteLdapSettings(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetAuthenticationSettings_FF_PL_ENABLE_MULTIPLE_IDP_SUPPORT_Enabled() throws IOException {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(ACCOUNT_ID, PL_ENABLE_MULTIPLE_IDP_SUPPORT);
    assertThatThrownBy(() -> authenticationSettingsServiceImpl.getAuthenticationSettings(ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetAuthenticationSettingsV2() throws IOException {
    Call<RestResponse<Set<String>>> whitelistDomainsRequest = mock(Call.class);
    doReturn(whitelistDomainsRequest).when(managerClient).getWhitelistedDomains(anyString());
    Set<String> strs = new HashSet<>();
    strs.add("wl1");
    RestResponse<Set<String>> mockSetsResp = new RestResponse<>(strs);
    doReturn(Response.success(mockSetsResp)).when(whitelistDomainsRequest).execute();

    Call<RestResponse<SSOConfig>> ssoConfigRequest = mock(Call.class);
    doReturn(ssoConfigRequest).when(managerClient).getAccountAccessManagementSettingsV2(anyString());
    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(ACCOUNT_ID)
                                    .friendlySamlName("testSAMLFriendlyName")
                                    .ssoType(SSOType.SAML)
                                    .logoutUrl("http://dummy.logout.url")
                                    .build();
    List<SSOSettings> settings = new ArrayList<>();
    settings.add(samlSettings);
    SSOConfig config = SSOConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .authenticationMechanism(AuthenticationMechanism.SAML)
                           .ssoSettings(settings)
                           .build();
    RestResponse<SSOConfig> mockSSOConfigRes = new RestResponse<>(config);
    doReturn(Response.success(mockSSOConfigRes)).when(ssoConfigRequest).execute();

    Call<RestResponse<Boolean>> twoFARequest = mock(Call.class);
    Call<RestResponse<Integer>> sessionTimeoutRequest = mock(Call.class);
    doReturn(twoFARequest).when(managerClient).twoFactorEnabled(anyString());
    doReturn(sessionTimeoutRequest).when(managerClient).getSessionTimeoutAtAccountLevel(anyString());
    RestResponse<Boolean> twoFARep = new RestResponse<>(false);
    RestResponse<Integer> sessionTimeoutResp = new RestResponse<>(50);
    doReturn(Response.success(twoFARep)).when(twoFARequest).execute();
    doReturn(Response.success(sessionTimeoutResp)).when(sessionTimeoutRequest).execute();

    Call<RestResponse<LoginSettings>> loginSettingsRequest = mock(Call.class);
    doReturn(loginSettingsRequest).when(managerClient).getUserNamePasswordSettings(anyString());

    LoginSettings loginSettings = LoginSettings.builder()
                                      .passwordExpirationPolicy(PasswordExpirationPolicy.builder()
                                                                    .enabled(false)
                                                                    .daysBeforeUserNotifiedOfPasswordExpiration(2)
                                                                    .daysBeforePasswordExpires(1)
                                                                    .build())
                                      .userLockoutPolicy(UserLockoutPolicy.builder()
                                                             .enableLockoutPolicy(false)
                                                             .lockOutPeriod(24)
                                                             .notifyUser(false)
                                                             .numberOfFailedAttemptsBeforeLockout(3)
                                                             .build())
                                      .passwordStrengthPolicy(PasswordStrengthPolicy.builder().enabled(false).build())
                                      .accountId(ACCOUNT_ID)
                                      .build();

    RestResponse<LoginSettings> loginSettingsResp = new RestResponse<>(loginSettings);
    doReturn(Response.success(loginSettingsResp)).when(loginSettingsRequest).execute();

    AuthenticationSettingsResponse settingsV2Response =
        authenticationSettingsServiceImpl.getAuthenticationSettingsV2(ACCOUNT_ID);
    assertNotNull(settingsV2Response);
    assertNotNull(settingsV2Response.getNgAuthSettings());
    assertNotNull(settingsV2Response.getWhitelistedDomains());
    assertThat(settingsV2Response.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SAML);
    assertThat(settingsV2Response.isTwoFactorEnabled()).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateSamlMetadata_SSOId() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request)
        .when(managerClient)
        .updateSAMLMetadata(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any());

    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(ACCOUNT_ID)
                                    .friendlySamlName("testSAMLFriendlyName")
                                    .ssoType(SSOType.SAML)
                                    .logoutUrl("http://dummy.logout.url")
                                    .build();
    List<SSOSettings> settings = new ArrayList<>();
    settings.add(samlSettings);
    SSOConfig config = SSOConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .authenticationMechanism(AuthenticationMechanism.SAML)
                           .ssoSettings(settings)
                           .build();
    RestResponse<SSOConfig> mockSSOConfigRes = new RestResponse<>(config);
    doReturn(Response.success(mockSSOConfigRes)).when(request).execute();

    SSOConfig responseSSOConfig = authenticationSettingsServiceImpl.updateSAMLMetadata(anyString(), anyString(), any(),
        eq("hi"), anyString(), anyBoolean(), eq("http://dummy.logout.url"), anyString(), anyString(), anyString(),
        anyString(), eq("friendlyName"), anyBoolean(), anyString(), anyString());

    assertNotNull(responseSSOConfig);
    assertThat(responseSSOConfig.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SAML);
    assertNotNull(responseSSOConfig.getSsoSettings());
    assertThat(responseSSOConfig.getSsoSettings().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateSamlAuthenticationEnabled() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request)
        .when(managerClient)
        .updateAuthenticationEnabledForSAMLSetting(anyString(), anyString(), anyBoolean());
    doReturn(Response.success(new RestResponse<>(true))).when(request).execute();
    final String testSAMLId = "testSAMLSSOId";
    authenticationSettingsServiceImpl.updateAuthenticationForSAMLSetting(ACCOUNT_ID, testSAMLId, Boolean.TRUE);
    verify(managerClient, times(1)).updateAuthenticationEnabledForSAMLSetting(ACCOUNT_ID, testSAMLId, true);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata_SSOId() throws IOException {
    final String testSAMLId = "testSAMLSSOId";
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID, testSAMLId);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    List<String> userGroups = new ArrayList<>();
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(ACCOUNT_ID, testSAMLId);
    SSOConfig ssoConfig = SSOConfig.builder().accountId(ACCOUNT_ID).build();
    Call<RestResponse<SSOConfig>> config = mock(Call.class);
    doReturn(config).when(managerClient).deleteSAMLMetadata(ACCOUNT_ID, testSAMLId);
    RestResponse<SSOConfig> mockConfig = new RestResponse<>(ssoConfig);
    doReturn(Response.success(mockConfig)).when(config).execute();
    SSOConfig expectedConfig = authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID, testSAMLId);
    assertNotNull(expectedConfig);
    assertThat(expectedConfig.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void getSAMLLoginTest_SamlSSOId() throws IOException {
    final String testSAMLId = "testSAMLSSOId";
    LoginTypeResponse response =
        LoginTypeResponse.builder().authenticationMechanism(AuthenticationMechanism.SAML).isOauthEnabled(false).build();
    Call<RestResponse<LoginTypeResponse>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLLoginTestV2(ACCOUNT_ID, testSAMLId);
    RestResponse<LoginTypeResponse> mockResponse = new RestResponse<>(response);
    doReturn(Response.success(mockResponse)).when(request).execute();
    LoginTypeResponse loginTypeResponse = authenticationSettingsServiceImpl.getSAMLLoginTestV2(ACCOUNT_ID, testSAMLId);
    assertNotNull(loginTypeResponse);
    assertThat(loginTypeResponse.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SAML);
    assertThat(loginTypeResponse.isOauthEnabled()).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSAMLSettingAuthenticationCanNotBeDisabled() throws IOException {
    String uuid = "someRandomTestUUID";
    Call<RestResponse<SSOConfig>> ssoConfigRequest = mock(Call.class);
    doReturn(ssoConfigRequest).when(managerClient).getAccountAccessManagementSettingsV2(anyString());
    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(ACCOUNT_ID)
                                    .friendlySamlName("testSAMLFriendlyName")
                                    .ssoType(SSOType.SAML)
                                    .logoutUrl("http://dummy.logout.url")
                                    .build();
    samlSettings.setUuid(uuid);
    List<SSOSettings> settings = new ArrayList<>();
    settings.add(samlSettings);
    SSOConfig config = SSOConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .authenticationMechanism(AuthenticationMechanism.SAML)
                           .ssoSettings(settings)
                           .build();

    RestResponse<SSOConfig> mockSSOConfigRes = new RestResponse<>(config);
    doReturn(Response.success(mockSSOConfigRes)).when(ssoConfigRequest).execute();

    assertThatThrownBy(
        () -> authenticationSettingsServiceImpl.updateAuthenticationForSAMLSetting(ACCOUNT_ID, uuid, Boolean.FALSE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "SAML setting with SSO Id %s can not be disabled for authentication, as account's %s current authentication mechanism is SAML, "
                + "and this is the only SAML setting with authentication setting enabled. Please enable authentication on other configured SAML"
                + " setting(s) first or switch account authentication mechanism to other before disabling authentication for this SAML.",
            uuid, ACCOUNT_ID));
  }

  @Test
  @Owner(developers = ADITYA)
  @Category(UnitTests.class)
  public void testUploadSamlMetaDataConfigWInvalidName() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(ACCOUNT_ID, PL_ENABLE_MULTIPLE_IDP_SUPPORT);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    // Mock the response
    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(ACCOUNT_ID)
                                    .friendlySamlName("testSAMLFriendlyName")
                                    .ssoType(SSOType.SAML)
                                    .logoutUrl("http://dummy.logout.url")
                                    .build();
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    Response<RestResponse<SamlSettings>> response = Response.success(mockResponse);
    doReturn(response).when(request).execute();

    // Create the test data
    MultipartBody.Part inputStreamPart = mock(MultipartBody.Part.class);
    String displayName = "js.alert()";
    String groupMembershipAttr = "groupMembershipAttr";
    boolean authorizationEnabled = true;
    String logoutUrl = "http://dummy.logout.url";
    String entityIdentifier = "entityIdentifier";
    String samlProviderType = "samlProviderType";
    String clientId = "clientId";
    String clientSecret = "clientSecret";
    String friendlySamlName = "friendlySamlName";
    boolean jitEnabled = true;
    String jitValidationKey = "jitValidationKey";
    String jitValidationValue = "jitValidationValue";

    assertThatThrownBy(
        ()
            -> authenticationSettingsServiceImpl.uploadSAMLMetadata(ACCOUNT_ID, inputStreamPart, displayName,
                groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
                clientSecret, friendlySamlName, jitEnabled, jitValidationKey, jitValidationValue))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Name can be 128 characters long and can contain alphanumeric characters.,-_");
  }

  @Test
  @Owner(developers = ADITYA)
  @Category(UnitTests.class)
  public void testUploadSamlMetaDataConfigWInvalidFriendlyname() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(ACCOUNT_ID, PL_ENABLE_MULTIPLE_IDP_SUPPORT);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    // Mock the response
    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(ACCOUNT_ID)
                                    .friendlySamlName("testSAMLFriendlyName")
                                    .ssoType(SSOType.SAML)
                                    .logoutUrl("http://dummy.logout.url")
                                    .build();
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    Response<RestResponse<SamlSettings>> response = Response.success(mockResponse);
    doReturn(response).when(request).execute();

    // Create the test data
    MultipartBody.Part inputStreamPart = mock(MultipartBody.Part.class);
    String displayName = "Dummy";
    String groupMembershipAttr = "groupMembershipAttr";
    boolean authorizationEnabled = true;
    String logoutUrl = "http://dummy.logout.url";
    String entityIdentifier = "entityIdentifier";
    String samlProviderType = "samlProviderType";
    String clientId = "clientId";
    String clientSecret = "clientSecret";
    String friendlySamlName = "js.alert()";
    boolean jitEnabled = true;
    String jitValidationKey = "jitValidationKey";
    String jitValidationValue = "jitValidationValue";

    assertThatThrownBy(
        ()
            -> authenticationSettingsServiceImpl.uploadSAMLMetadata(ACCOUNT_ID, inputStreamPart, displayName,
                groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
                clientSecret, friendlySamlName, jitEnabled, jitValidationKey, jitValidationValue))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Display Name can be 128 characters long and can contain alphanumeric characters.,-_");
  }
  @Test
  @Owner(developers = ADITYA)
  @Category(UnitTests.class)
  public void testUploadSamlMetaDataConfigWInvalidlogoutUrl() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(ACCOUNT_ID, PL_ENABLE_MULTIPLE_IDP_SUPPORT);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    // Mock the response
    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(ACCOUNT_ID)
                                    .friendlySamlName("testSAMLFriendlyName")
                                    .ssoType(SSOType.SAML)
                                    .logoutUrl("http://dummy.logout.url")
                                    .build();
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    Response<RestResponse<SamlSettings>> response = Response.success(mockResponse);
    doReturn(response).when(request).execute();

    // Create the test data
    MultipartBody.Part inputStreamPart = mock(MultipartBody.Part.class);
    String displayName = "Dummy";
    String groupMembershipAttr = "groupMembershipAttr";
    boolean authorizationEnabled = true;
    String logoutUrl = "<+script.js()>";
    String entityIdentifier = "entityIdentifier";
    String samlProviderType = "samlProviderType";
    String clientId = "clientId";
    String clientSecret = "clientSecret";
    String friendlySamlName = "Friendly Name";
    boolean jitEnabled = true;
    String jitValidationKey = "jitValidationKey";
    String jitValidationValue = "jitValidationValue";

    assertThatThrownBy(
        ()
            -> authenticationSettingsServiceImpl.uploadSAMLMetadata(ACCOUNT_ID, inputStreamPart, displayName,
                groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
                clientSecret, friendlySamlName, jitEnabled, jitValidationKey, jitValidationValue))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid logoutUrl " + logoutUrl);
  }

  @Test
  @Owner(developers = ADITYA)
  @Category(UnitTests.class)
  public void testUpdateSAMLMetaDataWInvalidName() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request)
        .when(managerClient)
        .updateSAMLMetadata(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any());

    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(ACCOUNT_ID)
                                    .friendlySamlName("testSAMLFriendlyName")
                                    .ssoType(SSOType.SAML)
                                    .logoutUrl("http://dummy.logout.url")
                                    .build();
    List<SSOSettings> settings = new ArrayList<>();
    settings.add(samlSettings);
    SSOConfig config = SSOConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .authenticationMechanism(AuthenticationMechanism.SAML)
                           .ssoSettings(settings)
                           .build();
    RestResponse<SSOConfig> mockSSOConfigRes = new RestResponse<>(config);
    doReturn(Response.success(mockSSOConfigRes)).when(request).execute();

    String displayName = "js.alert()";
    String logoutUrl = "http://dummy.logout.url";
    assertThatThrownBy(()
                           -> authenticationSettingsServiceImpl.updateSAMLMetadata(anyString(), anyString(), any(),
                               displayName, anyString(), anyBoolean(), logoutUrl, anyString(), anyString(), anyString(),
                               anyString(), anyString(), anyBoolean(), anyString(), anyString()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Name can be 128 characters long and can contain alphanumeric characters.,-_");
  }

  @Test
  @Owner(developers = ADITYA)
  @Category(UnitTests.class)
  public void testUpdateSAMLMetaDataWInvalidLogoutUrl() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request)
        .when(managerClient)
        .updateSAMLMetadata(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any());

    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(ACCOUNT_ID)
                                    .friendlySamlName("testSAMLFriendlyName")
                                    .ssoType(SSOType.SAML)
                                    .logoutUrl("http://dummy.logout.url")
                                    .build();
    List<SSOSettings> settings = new ArrayList<>();
    settings.add(samlSettings);
    SSOConfig config = SSOConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .authenticationMechanism(AuthenticationMechanism.SAML)
                           .ssoSettings(settings)
                           .build();
    RestResponse<SSOConfig> mockSSOConfigRes = new RestResponse<>(config);
    doReturn(Response.success(mockSSOConfigRes)).when(request).execute();

    String logoutUrl = "hello";
    assertThatThrownBy(()
                           -> authenticationSettingsServiceImpl.updateSAMLMetadata(anyString(), anyString(), any(),
                               anyString(), anyString(), anyBoolean(), logoutUrl, anyString(), anyString(), anyString(),
                               anyString(), anyString(), anyBoolean(), anyString(), anyString()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid logoutUrl " + logoutUrl);
  }
}
