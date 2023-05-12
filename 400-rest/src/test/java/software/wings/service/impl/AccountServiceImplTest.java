/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.XIN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.AuthenticationInfo;
import io.harness.authenticationservice.beans.AuthenticationInfoV2;
import io.harness.authenticationservice.beans.SSORequest;
import io.harness.beans.FeatureName;
import io.harness.cache.HarnessCacheManager;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.service.DelegateVersionService;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;
import io.harness.persistence.HQuery;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.sso.OauthSettings;
import software.wings.dl.GenericDbCache;
import software.wings.dl.WingsPersistence;
import software.wings.licensing.LicenseService;
import software.wings.security.saml.SamlClientService;
import software.wings.service.intfc.AuthService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(_955_ACCOUNT_MGMT)
public class AccountServiceImplTest extends WingsBaseTest {
  @Mock WingsPersistence wingsPersistence;
  @Mock protected AuthService authService;
  @Mock protected HarnessCacheManager harnessCacheManager;
  @Mock private LicenseService licenseService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private GenericDbCache dbCache;
  @Mock private SSOSettingServiceImpl ssoSettingService;
  @Mock private SamlClientService samlClientService;
  @Mock private DelegateVersionService delegateVersionService;

  @Mock Query<Account> accountQuery1;
  @Mock Query<Account> accountQuery2;

  @InjectMocks AccountServiceImpl accountService;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  private static final String INDIVIDUAL_VERSION = "individualVersion";
  private static final String INDIVIDUAL_ACCOUNT = "individualAccount";
  private static final String GLOBAL_ACCOUNT = "__GLOBAL_ACCOUNT_ID__";
  private static final String GLOBAL_VERSION = "globalVersion";
  private final String ACCOUNT_ID = "accountId";

  @Before
  public void setup() throws Exception {
    initMocks(this);
    when(delegateVersionService.getDelegateJarVersions(anyString())).thenReturn(Collections.emptyList());
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void getDelegateConfigurationFromGlobalAccount() {
    when(licenseService.isAccountDeleted(any())).thenReturn(false);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    Account account = new Account();
    account.setUuid(INDIVIDUAL_ACCOUNT);

    Account globalAccount = new Account();
    globalAccount.setUuid(GLOBAL_ACCOUNT);

    String globalVersion = GLOBAL_VERSION;
    List<String> globalDelegateVersions = new ArrayList<>();
    globalDelegateVersions.add(globalVersion);
    DelegateConfiguration globalDelegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(globalDelegateVersions).build();

    globalAccount.setDelegateConfiguration(globalDelegateConfiguration);

    when(wingsPersistence.createQuery(Account.class, EnumSet.of(HQuery.QueryChecks.VALIDATE)))
        .thenReturn(accountQuery1)
        .thenReturn(accountQuery2);
    when(accountQuery1.filter(AccountKeys.uuid, INDIVIDUAL_ACCOUNT)).thenReturn(accountQuery1);
    when(accountQuery1.project("delegateConfiguration", true)).thenReturn(accountQuery1);
    when(accountQuery1.get()).thenReturn(account);

    when(accountQuery2.filter(AccountKeys.uuid, GLOBAL_ACCOUNT)).thenReturn(accountQuery2);
    when(accountQuery2.project("delegateConfiguration", true)).thenReturn(accountQuery2);
    when(accountQuery2.get()).thenReturn(globalAccount);

    DelegateConfiguration resultConfiguration = accountService.getDelegateConfiguration(INDIVIDUAL_ACCOUNT);
    assertEquals(resultConfiguration, globalDelegateConfiguration);
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void getDelegateConfigurationFromIndividualAccount() {
    when(licenseService.isAccountDeleted(any())).thenReturn(false);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    Account account = new Account();
    account.setUuid(INDIVIDUAL_ACCOUNT);

    String version = INDIVIDUAL_VERSION;
    List<String> delegateVersions = new ArrayList<>();
    delegateVersions.add(version);
    DelegateConfiguration individualConfiguration =
        DelegateConfiguration.builder().delegateVersions(delegateVersions).build();
    account.setDelegateConfiguration(individualConfiguration);

    Account globalAccount = new Account();
    globalAccount.setUuid(GLOBAL_ACCOUNT);

    String globalVersion = GLOBAL_VERSION;
    List<String> globalDelegateVersions = new ArrayList<>();
    globalDelegateVersions.add(globalVersion);
    DelegateConfiguration globalConfiguration =
        DelegateConfiguration.builder().delegateVersions(globalDelegateVersions).build();
    globalAccount.setDelegateConfiguration(globalConfiguration);

    when(wingsPersistence.createQuery(Account.class, EnumSet.of(HQuery.QueryChecks.VALIDATE)))
        .thenReturn(accountQuery1)
        .thenReturn(accountQuery2);
    when(accountQuery1.filter(AccountKeys.uuid, INDIVIDUAL_ACCOUNT)).thenReturn(accountQuery1);
    when(accountQuery1.project("delegateConfiguration", true)).thenReturn(accountQuery1);
    when(accountQuery1.get()).thenReturn(account);

    when(accountQuery2.filter(AccountKeys.uuid, GLOBAL_ACCOUNT)).thenReturn(accountQuery2);
    when(accountQuery2.project("delegateConfiguration", true)).thenReturn(accountQuery2);
    when(accountQuery2.get()).thenReturn(globalAccount);

    DelegateConfiguration resultConfiguration = accountService.getDelegateConfiguration(INDIVIDUAL_ACCOUNT);

    assertEquals(resultConfiguration, individualConfiguration);
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void getAuthenticationInfo_UserPassword() {
    buildAccountWithAuthMechanism(AuthenticationMechanism.USER_PASSWORD, false);
    assertEquals(AuthenticationInfo.builder()
                     .accountId(ACCOUNT_ID)
                     .authenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                     .oauthEnabled(false)
                     .build(),
        accountService.getAuthenticationInfo(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void getAuthenticationInfo_UserPasswordAndOauth() {
    buildAccountWithAuthMechanism(AuthenticationMechanism.USER_PASSWORD, true);
    when(ssoSettingService.getOauthSettingsByAccountId(ACCOUNT_ID))
        .thenReturn(OauthSettings.builder()
                        .accountId(ACCOUNT_ID)
                        .allowedProviders(ImmutableSet.of(OauthProviderType.GOOGLE))
                        .displayName("oauth")
                        .build());
    assertEquals(AuthenticationInfo.builder()
                     .accountId(ACCOUNT_ID)
                     .authenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                     .oauthEnabled(true)
                     .oauthProviders(ImmutableList.of(OauthProviderType.GOOGLE))
                     .build(),
        accountService.getAuthenticationInfo(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void getAuthenticationInfo_OauthOnly() {
    buildAccountWithAuthMechanism(AuthenticationMechanism.OAUTH, true);
    when(ssoSettingService.getOauthSettingsByAccountId(ACCOUNT_ID))
        .thenReturn(OauthSettings.builder()
                        .accountId(ACCOUNT_ID)
                        .allowedProviders(ImmutableSet.of(OauthProviderType.GOOGLE))
                        .displayName("oauth")
                        .build());
    assertEquals(AuthenticationInfo.builder()
                     .accountId(ACCOUNT_ID)
                     .authenticationMechanism(AuthenticationMechanism.OAUTH)
                     .oauthEnabled(true)
                     .oauthProviders(ImmutableList.of(OauthProviderType.GOOGLE))
                     .build(),
        accountService.getAuthenticationInfo(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void getAuthenticationInfo_LDAP() {
    buildAccountWithAuthMechanism(AuthenticationMechanism.LDAP, false);
    assertEquals(AuthenticationInfo.builder()
                     .accountId(ACCOUNT_ID)
                     .authenticationMechanism(AuthenticationMechanism.LDAP)
                     .oauthEnabled(false)
                     .build(),
        accountService.getAuthenticationInfo(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void getAuthenticationInfo_SAML() {
    Account account = buildAccountWithAuthMechanism(AuthenticationMechanism.SAML, false);
    when(samlClientService.generateSamlRequestFromAccount(account, false))
        .thenReturn(SSORequest.builder().idpRedirectUrl("testredirecturl").build());
    assertEquals(AuthenticationInfo.builder()
                     .accountId(ACCOUNT_ID)
                     .authenticationMechanism(AuthenticationMechanism.SAML)
                     .samlRedirectUrl("testredirecturl")
                     .build(),
        accountService.getAuthenticationInfo(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetFeatureFlagEnabledAccountIds() {
    FeatureName featureName = FeatureName.values()[0];
    Set<String> accountIds = new HashSet<>(Collections.singleton(randomAlphabetic(10)));
    accountIds.addAll(new ArrayList<>());
    when(featureFlagService.getAccountIds(featureName)).thenReturn(accountIds);
    Set<String> featureFlagEnabledAccounts = accountService.getFeatureFlagEnabledAccountIds(featureName.name());
    verify(featureFlagService, times(1)).getAccountIds(featureName);
    assertEquals(featureFlagEnabledAccounts, accountIds);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetFeatureFlagEnabledAccountIds_InvalidFeatureName() {
    String featureFlagName = randomAlphabetic(10);
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(String.format("Invalid feature flag name received: %s", featureFlagName));
    accountService.getFeatureFlagEnabledAccountIds(featureFlagName);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testIsAutoInviteAcceptanceEnabled() {
    buildAccountWithAuthMechanism(AuthenticationMechanism.SAML, false);
    when(featureFlagService.isEnabled(any(FeatureName.class), anyString())).thenReturn(true);
    assertTrue(accountService.isAutoInviteAcceptanceEnabled(ACCOUNT_ID));

    buildAccountWithAuthMechanism(AuthenticationMechanism.LDAP, false);
    assertTrue(accountService.isAutoInviteAcceptanceEnabled(ACCOUNT_ID));

    buildAccountWithAuthMechanism(AuthenticationMechanism.OAUTH, false);
    assertTrue(accountService.isAutoInviteAcceptanceEnabled(ACCOUNT_ID));

    buildAccountWithAuthMechanism(AuthenticationMechanism.USER_PASSWORD, false);
    assertFalse(accountService.isAutoInviteAcceptanceEnabled(ACCOUNT_ID));

    buildAccountWithAuthMechanism(AuthenticationMechanism.SAML, false);
    when(featureFlagService.isEnabled(any(FeatureName.class), anyString())).thenReturn(false);
    assertFalse(accountService.isAutoInviteAcceptanceEnabled(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testIsPLNoEmailForSamlAccountInvitesEnabled() {
    buildAccountWithAuthMechanism(AuthenticationMechanism.SAML, false);
    when(featureFlagService.isEnabled(any(FeatureName.class), anyString())).thenReturn(true);
    assertTrue(accountService.isPLNoEmailForSamlAccountInvitesEnabled(ACCOUNT_ID));

    buildAccountWithAuthMechanism(AuthenticationMechanism.LDAP, false);
    assertTrue(accountService.isPLNoEmailForSamlAccountInvitesEnabled(ACCOUNT_ID));

    buildAccountWithAuthMechanism(AuthenticationMechanism.OAUTH, false);
    assertTrue(accountService.isPLNoEmailForSamlAccountInvitesEnabled(ACCOUNT_ID));

    buildAccountWithAuthMechanism(AuthenticationMechanism.USER_PASSWORD, false);
    assertFalse(accountService.isPLNoEmailForSamlAccountInvitesEnabled(ACCOUNT_ID));

    buildAccountWithAuthMechanism(AuthenticationMechanism.SAML, false);
    when(featureFlagService.isEnabled(any(FeatureName.class), anyString())).thenReturn(false);
    assertFalse(accountService.isPLNoEmailForSamlAccountInvitesEnabled(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void getAuthenticationInfoV2_SAMLList() {
    Account account = buildAccountWithAuthMechanism(AuthenticationMechanism.SAML, false);
    when(samlClientService.generateSamlRequestFromAccount(account, false))
        .thenReturn(SSORequest.builder().idpRedirectUrl("testredirecturl").build());

    AuthenticationInfoV2 authInfoV2 = accountService.getAuthenticationInfoV2(ACCOUNT_ID);
    assertThat(authInfoV2).isNotNull();
    assertThat(authInfoV2.getAuthenticationMechanism()).isEqualTo(AuthenticationMechanism.SAML);
  }

  private Account buildAccountWithAuthMechanism(
      AuthenticationMechanism authenticationMechanism, boolean isOauthEnabled) {
    Account account = new Account();
    account.setUuid(ACCOUNT_ID);
    account.setAuthenticationMechanism(authenticationMechanism);
    account.setOauthEnabled(isOauthEnabled);
    account.setCompanyName("test");
    account.setAccountName("testaccount");
    account.setAppId("testappid");
    wingsPersistence.save(account);
    when(dbCache.get(Account.class, ACCOUNT_ID)).thenReturn(account);
    when(wingsPersistence.get(any(), any())).thenReturn(account);
    return account;
  }
}
