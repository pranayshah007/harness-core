/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.rule.OwnerRule.PRATEEK;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.usermembership.remote.UserMembershipClient;

import software.wings.beans.Account;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapSettings;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import retrofit2.Response;

@OwnedBy(PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class LdapBasedAuthHandlerTest extends CategoryTest {
  @Mock AuthenticationUtils authenticationUtils;
  @Mock SSOSettingService ssoSettingService;
  @Spy private SecretManager secretManager;
  @Mock EncryptionService encryptionService;
  @Mock DomainWhitelistCheckerService domainWhitelistCheckerService;
  private UserMembershipClient userMembershipClient;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private FeatureFlagService featureFlagService;

  @Mock UserService userService;
  @Inject @InjectMocks LdapBasedAuthHandler ldapBasedAuthHandler;

  private LdapSettings ldapSettings;
  private static final String testAccountId = "testAccountId";
  private static final String userEmail = "test_ldap_01@mailinator.co";
  private static final String userPwd = "testPwd0123";

  @Before
  public void setUp() throws IOException {
    LdapConnectionSettings settings = new LdapConnectionSettings();
    settings.setBindDN("testBindDN");
    settings.setBindPassword("testBindPassword");
    ldapSettings = software.wings.beans.sso.LdapSettings.builder()
                       .connectionSettings(settings)
                       .displayName("someDisplayName")
                       .accountId(testAccountId)
                       .build();
    ldapSettings.setUuid("someUuid");
    userMembershipClient = mock(UserMembershipClient.class, RETURNS_DEEP_STUBS);
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCgLDAPBasedAuthenticationSuccess() throws IOException {
    Account account = getTestAccount(false);
    User user = getUserWithEmailAndAccount(account);

    LdapDelegateService ldapDelegateService = mockCommonFlow(user, account);

    String authSuccessMsg = "Authentication Successful";
    LdapResponse ldapResponse =
        LdapResponse.builder().status(LdapResponse.Status.SUCCESS).message(authSuccessMsg).build();

    when(ldapDelegateService.authenticate(any(), any(), anyString(), any())).thenReturn(ldapResponse);

    AuthenticationResponse response = ldapBasedAuthHandler.authenticate(userEmail, userPwd, account.getUuid());
    assertNotNull(response);
    assertThat(response.getUser().getEmail()).isEqualTo(userEmail);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCgLDAPBasedAuthenticationFailsInvalidCredentials() {
    Account account = getTestAccount(false);
    User user = getUserWithEmailAndAccount(account);

    LdapDelegateService ldapDelegateService = mockCommonFlow(user, account);

    String authSuccessMsg = "Authentication Failed";
    LdapResponse ldapResponse =
        LdapResponse.builder().status(LdapResponse.Status.FAILURE).message(authSuccessMsg).build();

    when(ldapDelegateService.authenticate(any(), any(), anyString(), any())).thenReturn(ldapResponse);

    assertThatThrownBy(() -> ldapBasedAuthHandler.authenticate(userEmail, userPwd)).isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testNgLDAPBasedAuthenticationSuccess() throws IOException {
    Account account = getTestAccount(true);
    User user = getUserWithEmailAndAccount(account);

    LdapDelegateService ldapDelegateService = mockCommonFlow(user, account);
    when(userMembershipClient.isUserInScope(anyString(), anyString(), any(), any()).execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(Boolean.TRUE)));
    when(authenticationUtils.getAccount(account.getUuid())).thenReturn(account);

    String authSuccessMsg = "Authentication Success";
    LdapResponse ldapResponse =
        LdapResponse.builder().status(LdapResponse.Status.SUCCESS).message(authSuccessMsg).build();

    when(ldapDelegateService.authenticate(any(), any(), anyString(), any()))
        .thenThrow(new NoAvailableDelegatesException())
        .thenReturn(ldapResponse);

    AuthenticationResponse response = ldapBasedAuthHandler.authenticate(userEmail, userPwd, account.getUuid());
    assertNotNull(response);
    assertThat(response.getUser().getEmail()).isEqualTo(userEmail);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCgLDAPBasedAuthenticationNoLdapSSO() {
    Account account = getTestAccount(true);
    User user = getUserWithEmailAndAccount(account);

    when(authenticationUtils.getUser(anyString())).thenReturn(user);
    when(userService.getUserByEmail(userEmail)).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    when(ssoSettingService.getLdapSettingsByAccountId(testAccountId)).thenReturn(null);

    assertThatThrownBy(() -> ldapBasedAuthHandler.authenticate(userEmail, userPwd, account.getUuid()))
        .isInstanceOf(WingsException.class)
        .hasMessage(INVALID_CREDENTIAL.name());
  }

  private Account getTestAccount(boolean isNGEnabled) {
    Account account = new Account();
    account.setUuid("testAccountId");
    account.setAuthenticationMechanism(AuthenticationMechanism.LDAP);
    if (isNGEnabled) {
      account.setNextGenEnabled(Boolean.TRUE);
    }

    return account;
  }

  private User getUserWithEmailAndAccount(Account account) {
    User user = new User();
    user.setEmail(userEmail);
    user.setUuid("testUserUuid");

    user.setAccounts(Collections.singletonList(account));
    return user;
  }

  private LdapDelegateService mockCommonFlow(User user, Account account) {
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    LdapSettings spyLdapSettings = spy(ldapSettings);

    when(userService.getUserByEmail(userEmail)).thenReturn(user);
    when(authenticationUtils.getUser(anyString())).thenReturn(user);
    when(authenticationUtils.getDefaultAccount(any(User.class))).thenReturn(account);
    LdapDelegateService ldapDelegateService = mock(LdapDelegateService.class);
    when(delegateProxyFactory.getV2(eq(LdapDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(ldapDelegateService);

    doReturn(encryptedDataDetail).when(spyLdapSettings).getEncryptedDataDetails(any());
    Optional<EncryptedDataDetail> encryptedDataDetailOptional =
        Optional.of(EncryptedDataDetail.builder().fieldName("password").build());
    doReturn(encryptedDataDetailOptional)
        .when(secretManager)
        .getEncryptedDataDetails(anyString(), anyString(), any(), any());

    EncryptedData encryptedSecret = mock(EncryptedData.class);
    doReturn(encryptedSecret).when(secretManager).encryptSecret(anyString(), any(), anyBoolean());

    when(domainWhitelistCheckerService.isDomainWhitelisted(any())).thenReturn(true);
    when(ssoSettingService.getLdapSettingsByAccountId(testAccountId)).thenReturn(spyLdapSettings);
    return ldapDelegateService;
  }
}
