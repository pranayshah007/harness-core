/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessModule._970_RBAC_CORE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.HAS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.ng.core.common.beans.Generation.CG;
import static io.harness.ng.core.common.beans.Generation.NG;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.MOHIT;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.SHASHANK;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.UNKNOWN;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS;
import static io.harness.rule.OwnerRule.VOJIN;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.AccountType.COMMUNITY;
import static software.wings.beans.AccountType.ESSENTIALS;
import static software.wings.beans.AccountType.PAID;
import static software.wings.beans.AccountType.TRIAL;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.EmailVerificationToken.Builder.anEmailVerificationToken;
import static software.wings.beans.Role.Builder.aRole;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;
import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;
import static software.wings.security.PermissionAttribute.ResourceType.ARTIFACT;
import static software.wings.security.PermissionAttribute.ResourceType.DEPLOYMENT;
import static software.wings.security.PermissionAttribute.ResourceType.ENVIRONMENT;
import static software.wings.security.PermissionAttribute.ResourceType.SERVICE;
import static software.wings.security.PermissionAttribute.ResourceType.WORKFLOW;
import static software.wings.service.impl.UserServiceImpl.INVITE_EMAIL_TEMPLATE_NAME;
import static software.wings.service.impl.UserServiceImpl.SIGNUP_EMAIL_TEMPLATE_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.INVALID_USER_EMAIL;
import static software.wings.utils.WingsTestConstants.NEW_USER_EMAIL;
import static software.wings.utils.WingsTestConstants.NEW_USER_NAME;
import static software.wings.utils.WingsTestConstants.NOTE;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.PORTAL_URL;
import static software.wings.utils.WingsTestConstants.ROLE_ID;
import static software.wings.utils.WingsTestConstants.ROLE_NAME;
import static software.wings.utils.WingsTestConstants.SUPPORT_EMAIL;
import static software.wings.utils.WingsTestConstants.TEMPORARY_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_INVITE_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.USER_PASSWORD;
import static software.wings.utils.WingsTestConstants.VERIFICATION_PATH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.crypto.bcrypt.BCrypt.hashpw;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.LogoutResponse;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.cache.HarnessCacheManager;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.event.model.EventType;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SignupException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.UserAlreadyPresentException;
import io.harness.exception.UserRegistrationException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitCheckerFactory;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.common.beans.UserSource;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Account;
import software.wings.beans.AccountJoinRequest;
import software.wings.beans.AccountRole;
import software.wings.beans.AccountStatus;
import software.wings.beans.ApplicationRole;
import software.wings.beans.Base.BaseKeys;
import software.wings.beans.CannySsoLoginResponse;
import software.wings.beans.EmailVerificationToken;
import software.wings.beans.Event.Type;
import software.wings.beans.LicenseInfo;
import software.wings.beans.MarketPlace;
import software.wings.beans.Role;
import software.wings.beans.RoleType;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.UserInvite;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.beans.marketplace.MarketPlaceType;
import software.wings.beans.security.AccessRequest;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlSettings;
import software.wings.beans.utm.UtmInfo;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.persistence.mail.EmailData;
import software.wings.resources.UserResource;
import software.wings.security.JWT_CATEGORY;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.SecretManager;
import software.wings.security.UserThreadLocal;
import software.wings.security.authentication.AuthenticationManager;
import software.wings.security.authentication.AuthenticationUtils;
import software.wings.security.authentication.MarketPlaceConfig;
import software.wings.security.authentication.TOTPAuthHandler;
import software.wings.service.impl.AccessRequestServiceImpl;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.AwsMarketPlaceApiHandlerImpl;
import software.wings.service.impl.HarnessUserGroupServiceImpl;
import software.wings.service.impl.UserServiceHelper;
import software.wings.service.impl.UserServiceImpl;
import software.wings.service.impl.UserServiceLimitChecker;
import software.wings.service.intfc.AccessRequestService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.RoleService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.signup.BlackListedDomainChecker;
import software.wings.signup.SignupServiceImpl;
import software.wings.utils.WingsTestConstants;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.mapping.Mapper;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.mail.EmailException;
import org.apache.commons.validator.routines.UrlValidator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * Created by anubhaw on 3/9/16.
 */
@Slf4j
@OwnedBy(PL)
@TargetModule(_970_RBAC_CORE)
public class UserServiceTest extends WingsBaseTest {
  private static final String ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME = "add_group";
  private final User.Builder userBuilder = anUser().appId(APP_ID).email(USER_EMAIL).name(USER_NAME).password(PASSWORD);

  /**
   * The Update operations.
   */
  @Mock UpdateOperations<User> updateOperations;

  /**
   * The Query.
   */
  @Mock Query<User> query;

  /**
   * The End.
   */
  @Mock FieldEnd end;

  /**
   * The Verification query.
   */
  @Mock Query<EmailVerificationToken> verificationQuery;

  /**
   * The User invite query.
   */
  @Mock Query<UserInvite> userInviteQuery;

  /**
   * The User invite query end.
   */
  @Mock private EmailNotificationService emailDataNotificationService;
  @Mock private RoleService roleService;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private AccountService accountService;
  @Mock private AppService appService;
  @Mock private AuthService authService;
  @Mock private UserGroupService userGroupService;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private UserServiceLimitChecker userServiceLimitChecker;
  @Mock private LoginSettingsService loginSettingsService;
  @Mock private BlackListedDomainChecker blackListedDomainChecker;
  @Mock private EventPublishHelper eventPublishHelper;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private AuthenticationUtils authenticationUtils;
  @Mock private TOTPAuthHandler totpAuthHandler;
  @Mock private SSOSettingService ssoSettingService;

  @Mock private FeatureFlagService featureFlagService;

  @Spy @InjectMocks private SignupServiceImpl signupService;

  /**
   * The Cache.
   */
  @Mock Cache<String, User> cache;
  @Mock private HarnessCacheManager harnessCacheManager;
  @Mock private ConfigurationController configurationController;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private MainConfiguration configuration;
  @Inject @InjectMocks private UserService userService;
  @InjectMocks private UserService mockedUserService = mock(UserServiceImpl.class);
  @Captor private ArgumentCaptor<EmailData> emailDataArgumentCaptor;
  @Captor private ArgumentCaptor<User> userArgumentCaptor;
  @Captor private ArgumentCaptor<PageRequest<User>> pageRequestArgumentCaptor;
  @Inject @InjectMocks SecretManager secretManager;
  @Inject @InjectMocks private AwsMarketPlaceApiHandlerImpl marketPlaceService;
  @Inject WingsPersistence realWingsPersistence;
  @Mock PortalConfig portalConfig;

  @Inject @InjectMocks UserServiceHelper userServiceHelper;

  @InjectMocks private HarnessUserGroupService harnessUserGroupService = mock(HarnessUserGroupServiceImpl.class);
  @InjectMocks private AccessRequestService accessRequestService = mock(AccessRequestServiceImpl.class);
  private Query<User> userQuery;

  /**
   * Sets mocks.
   */
  @Before
  public void setupMocks() {
    when(featureFlagService.isEnabled(FeatureName.PL_USER_DELETION_V2, ACCOUNT_ID)).thenReturn(true);
    when(configuration.getSupportEmail()).thenReturn(SUPPORT_EMAIL);
    doNothing().when(userServiceLimitChecker).limitCheck(Mockito.anyString(), anyList(), anySet());

    when(wingsPersistence.createQuery(User.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    when(query.order(any(Sort.class))).thenReturn(query);

    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    when(updateOperations.addToSet(any(), any())).thenReturn(updateOperations);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    when(updateOperations.addToSet(any(), any())).thenReturn(updateOperations);

    when(wingsPersistence.createQuery(EmailVerificationToken.class)).thenReturn(verificationQuery);
    when(verificationQuery.filter(any(), any())).thenReturn(verificationQuery);

    when(wingsPersistence.createQuery(UserInvite.class)).thenReturn(userInviteQuery);
    when(userInviteQuery.filter(any(), any())).thenReturn(userInviteQuery);
    when(userInviteQuery.order(any(Sort.class))).thenReturn(userInviteQuery);
    when(userInviteQuery.order(anyString())).thenReturn(userInviteQuery);
    when(limitCheckerFactory.getInstance(Mockito.any(io.harness.limits.Action.class)))
        .thenReturn(WingsTestConstants.mockChecker());

    when(configuration.isBlacklistedEmailDomainsAllowed()).thenReturn(true);
    when(totpAuthHandler.generateOtpUrl(any(), any(), any())).thenReturn(StringUtils.EMPTY);

    userQuery = realWingsPersistence.createQuery(User.class);
    when(configurationController.isPrimary()).thenReturn(true);
    when(harnessCacheManager.getCache(anyString(), eq(String.class), eq(User.class), any())).thenReturn(cache);
    when(harnessUserGroupService.isHarnessSupportUser(any())).thenReturn(false);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testLogout() {
    String token = "token";
    User user = anUser()
                    .appId(generateUuid())
                    .defaultAccountId(generateUuid())
                    .token(token)
                    .email("email")
                    .name("user_name")
                    .build();

    userService.logout(user);
    verify(cache).remove(user.getUuid());
    verify(authService, times(1)).invalidateToken(user.getToken());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_fetchUserSupportAccountWithNoAccessRequest() {
    String accountId1 = generateUuid();
    String accountId2 = generateUuid();
    Account account1 = Account.Builder.anAccount().withUuid(accountId1).build();
    Account account2 = Account.Builder.anAccount().withUuid(accountId2).build();

    User user = userBuilder.uuid(USER_ID).accounts(Arrays.asList(account1)).build();
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(user);
    when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
    List<Account> accountList = Arrays.asList(account2);
    when(harnessUserGroupService.listAllowedSupportAccounts(any(), any())).thenReturn(accountList);

    userService.loadSupportAccounts(user);

    assertThat(user.getAccounts().size()).isEqualTo(1);
    assertThat(user.getAccounts().get(0).getUuid()).isEqualTo(account1.getUuid());
    assertThat(user.getSupportAccounts().size()).isEqualTo(1);
    assertThat(user.getSupportAccounts().get(0).getUuid()).isEqualTo(account2.getUuid());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_fetchUserSupportAccountWithAccessRequest() {
    String accountId1 = generateUuid();
    String accountId2 = generateUuid();
    String accountId3 = generateUuid();
    Account account1 = Account.Builder.anAccount().withUuid(accountId1).build();
    account1.setHarnessSupportAccessAllowed(false);
    Account account2 = Account.Builder.anAccount().withUuid(accountId2).build();
    Account account3 = Account.Builder.anAccount().withUuid(accountId3).build();
    account3.setHarnessSupportAccessAllowed(false);
    wingsPersistence.save(Arrays.asList(account1, account2, account3));

    User user = userBuilder.uuid(USER_ID).accounts(Arrays.asList(account1)).build();
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(user);
    when(harnessUserGroupService.isHarnessSupportUser(any())).thenReturn(true);
    List<Account> accountList = Arrays.asList(account2);
    when(harnessUserGroupService.listAllowedSupportAccounts(any(), any())).thenReturn(accountList);
    when(accountService.getAccountsWithDisabledHarnessUserGroupAccess())
        .thenReturn(Sets.newHashSet(accountId3, accountId1));
    when(accountService.get(accountId3)).thenReturn(account3);

    AccessRequest accessRequest1 = AccessRequest.builder()
                                       .accountId(accountId3)
                                       .memberIds(Sets.newHashSet(USER_ID))
                                       .accessStartAt(Instant.now().toEpochMilli())
                                       .accessEndAt(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli())
                                       .accessActive(true)
                                       .accessType(AccessRequest.AccessType.MEMBER_ACCESS)
                                       .build();
    String harnessUserGroupId = generateUuid();
    HarnessUserGroup harnessUserGroup = HarnessUserGroup.builder()
                                            .uuid(harnessUserGroupId)
                                            .memberIds(Sets.newHashSet(USER_ID))
                                            .groupType(HarnessUserGroup.GroupType.RESTRICTED)
                                            .accountIds(Sets.newHashSet(accountId3))
                                            .build();
    AccessRequest accessRequest2 = AccessRequest.builder()
                                       .accountId(accountId3)
                                       .harnessUserGroupId(harnessUserGroupId)
                                       .accessStartAt(Instant.now().toEpochMilli())
                                       .accessEndAt(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli())
                                       .accessActive(true)
                                       .accessType(AccessRequest.AccessType.GROUP_ACCESS)
                                       .build();

    when(accessRequestService.getActiveAccessRequestForAccount(accountId3))
        .thenReturn(Arrays.asList(accessRequest1, accessRequest2));
    when(harnessUserGroupService.get(harnessUserGroupId)).thenReturn(harnessUserGroup);
    userService.loadSupportAccounts(user);
    user.setAccounts(Arrays.asList(account1));

    assertThat(user.getAccounts().get(0).getUuid()).isEqualTo(account1.getUuid());
    assertThat(user.getSupportAccounts().size()).isEqualTo(2);
    assertThat(user.getSupportAccounts().get(0).getUuid()).isEqualTo(account2.getUuid());
    assertThat(user.getSupportAccounts().get(1).getUuid()).isEqualTo(account3.getUuid());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_fetchUserSupportAccountWithIncorrectAccessRequests() {
    String accountId1 = generateUuid();
    String accountId2 = generateUuid();
    String accountId3 = generateUuid();
    Account account1 = Account.Builder.anAccount().withUuid(accountId1).build();
    account1.setHarnessSupportAccessAllowed(false);
    Account account2 = Account.Builder.anAccount().withUuid(accountId2).build();
    Account account3 = Account.Builder.anAccount().withUuid(accountId3).build();
    account3.setHarnessSupportAccessAllowed(false);
    wingsPersistence.save(Arrays.asList(account1, account2, account3));

    User user = userBuilder.uuid(USER_ID).accounts(Arrays.asList(account1)).build();
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(user);
    when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
    List<Account> accountList = Arrays.asList(account2);
    when(harnessUserGroupService.listAllowedSupportAccounts(any(), any())).thenReturn(accountList);
    when(accountService.getAccountsWithDisabledHarnessUserGroupAccess())
        .thenReturn(Sets.newHashSet(accountId3, accountId1));
    when(accountService.get(accountId3)).thenReturn(account3);

    // Access Request that doesn't have member Ids.
    AccessRequest accessRequest1 = AccessRequest.builder()
                                       .accountId(accountId3)
                                       .memberIds(Sets.newHashSet())
                                       .accessStartAt(Instant.now().toEpochMilli())
                                       .accessEndAt(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli())
                                       .accessActive(true)
                                       .accessType(AccessRequest.AccessType.MEMBER_ACCESS)
                                       .build();

    // Harness user group with no members.
    String harnessUserGroupId1 = generateUuid();
    HarnessUserGroup harnessUserGroup1 = HarnessUserGroup.builder()
                                             .uuid(harnessUserGroupId1)
                                             .groupType(HarnessUserGroup.GroupType.RESTRICTED)
                                             .accountIds(Sets.newHashSet(accountId3))
                                             .build();

    AccessRequest accessRequest2 = AccessRequest.builder()
                                       .accountId(accountId3)
                                       .harnessUserGroupId(harnessUserGroupId1)
                                       .accessStartAt(Instant.now().toEpochMilli())
                                       .accessEndAt(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli())
                                       .accessActive(true)
                                       .accessType(AccessRequest.AccessType.GROUP_ACCESS)
                                       .build();

    // harness user group to return null
    String harnessUserGroupId2 = generateUuid();
    AccessRequest accessRequest3 = AccessRequest.builder()
                                       .accountId(accountId3)
                                       .harnessUserGroupId(harnessUserGroupId2)
                                       .accessStartAt(Instant.now().toEpochMilli())
                                       .accessEndAt(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli())
                                       .accessActive(true)
                                       .accessType(AccessRequest.AccessType.GROUP_ACCESS)
                                       .build();

    when(accessRequestService.getActiveAccessRequestForAccount(accountId3))
        .thenReturn(Arrays.asList(accessRequest1, accessRequest2, accessRequest3));
    when(harnessUserGroupService.get(harnessUserGroupId1)).thenReturn(harnessUserGroup1);
    userService.loadSupportAccounts(user);
    user.setAccounts(Arrays.asList(account1));

    assertThat(user.getAccounts().get(0).getUuid()).isEqualTo(account1.getUuid());
    assertThat(user.getSupportAccounts().size()).isEqualTo(1);
    assertThat(user.getSupportAccounts().get(0).getUuid()).isEqualTo(account2.getUuid());
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  @Ignore("Ignoring it while finding the actual fix")
  public void testMarketPlaceSignUp() {
    when(configuration.getPortal().getJwtMarketPlaceSecret()).thenReturn("TESTSECRET");
    when(configuration.getMarketPlaceConfig())
        .thenReturn(MarketPlaceConfig.builder().awsMarketPlaceProductCode("CD").build());

    UserInvite testInvite = anUserInvite().withUuid(USER_INVITE_ID).withEmail(USER_EMAIL).build();
    testInvite.setPassword("TestPassword".toCharArray());
    MarketPlace marketPlace = MarketPlace.builder()
                                  .uuid("TESTUUID")
                                  .type(MarketPlaceType.AWS)
                                  .orderQuantity(10)
                                  .expirationDate(new Date())
                                  .productCode("CD")
                                  .build();
    String token = marketPlaceService.getMarketPlaceToken(marketPlace, testInvite);

    User savedUser = userBuilder.uuid(USER_ID)
                         .email(USER_EMAIL)
                         .emailVerified(false)
                         .companyName(COMPANY_NAME)
                         .accountName(ACCOUNT_NAME)
                         .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                         .build();

    try {
      userService.completeMarketPlaceSignup(savedUser, testInvite, MarketPlaceType.AWS);
      fail("");
    } catch (Exception e) {
      log.info("Expected error " + e.getMessage());
      assertThat(e).isInstanceOf(UnauthorizedException.class);
    }
    when(wingsPersistence.get(UserInvite.class, USER_INVITE_ID)).thenReturn(testInvite);
    when(userService.getUserByEmail(USER_EMAIL)).thenReturn(savedUser);
    try {
      userService.completeMarketPlaceSignup(savedUser, testInvite, MarketPlaceType.AWS);
      fail("");
    } catch (WingsException e) {
      log.info("Expected error " + e.getCode());
      assertThat(e).isInstanceOf(UserRegistrationException.class);
    }

    when(wingsPersistence.createQuery(User.class).filter(any(), any()).get()).thenReturn(null);
    try {
      userService.completeMarketPlaceSignup(savedUser, testInvite, MarketPlaceType.AWS);
      fail("");
    } catch (WingsException e) {
      log.info("Expected error " + e.getCode());
      assertThat(e).isInstanceOf(GeneralException.class);
    }

    testInvite.setMarketPlaceToken("fakeToken");

    try {
      userService.completeMarketPlaceSignup(savedUser, testInvite, MarketPlaceType.AWS);
      fail("");
    } catch (WingsException e) {
      log.info("Expected error " + e.getCode());
      assertThat(e.getCode()).isEqualTo(INVALID_CREDENTIAL);
    }

    testInvite.setMarketPlaceToken(token);

    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .build();

    when(accountService.save(any(Account.class), eq(false), eq(true))).thenReturn(account);
    when(wingsPersistence.saveAndGet(any(Class.class), any(User.class))).thenReturn(savedUser);
    when(wingsPersistence.get(MarketPlace.class, "TESTUUID")).thenReturn(marketPlace);
    when(userGroupService.list(anyString(), any(PageRequest.class), anyBoolean(), null, null))
        .thenReturn(aPageResponse().build());
    when(authenticationManager.defaultLogin(USER_EMAIL, "TestPassword")).thenReturn(savedUser);
    User user = userService.completeMarketPlaceSignup(savedUser, testInvite, MarketPlaceType.AWS);
    assertThat(user).isEqualTo(savedUser);

    // AWS marketplace signUp for CE
    when(configuration.getMarketPlaceConfig())
        .thenReturn(MarketPlaceConfig.builder().awsMarketPlaceProductCode("").build());
    when(configuration.getMarketPlaceConfig())
        .thenReturn(MarketPlaceConfig.builder().awsMarketPlaceCeProductCode("CE").build());
    marketPlace.setProductCode("CE");
    user = userService.completeMarketPlaceSignup(savedUser, testInvite, MarketPlaceType.AWS);
    assertThat(user).isEqualTo(savedUser);

    // AWS marketplace signUp for FF
    String ffProductCode = "FF";
    when(configuration.getMarketPlaceConfig())
        .thenReturn(MarketPlaceConfig.builder().awsMarketPlaceProductCode("").build());
    when(configuration.getMarketPlaceConfig())
        .thenReturn(MarketPlaceConfig.builder().awsMarketPlaceFfProductCode(ffProductCode).build());
    marketPlace.setProductCode(ffProductCode);
    user = userService.completeMarketPlaceSignup(savedUser, testInvite, MarketPlaceType.AWS);
    assertThat(user).isEqualTo(savedUser);

    // AWS marketplace signUp for CI
    String ciProductCode = "CI";
    when(configuration.getMarketPlaceConfig())
        .thenReturn(MarketPlaceConfig.builder().awsMarketPlaceProductCode("").build());
    when(configuration.getMarketPlaceConfig())
        .thenReturn(MarketPlaceConfig.builder().awsMarketPlaceFfProductCode(ciProductCode).build());
    marketPlace.setProductCode(ciProductCode);
    user = userService.completeMarketPlaceSignup(savedUser, testInvite, MarketPlaceType.AWS);
    assertThat(user).isEqualTo(savedUser);
  }

  /**
   * Test register.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldRegisterNewUser() {
    User savedUser = userBuilder.uuid(USER_ID)
                         .emailVerified(false)
                         .companyName(COMPANY_NAME)
                         .accountName(ACCOUNT_NAME)
                         .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                         .build();
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .build();

    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(configuration.getPortal().getVerificationUrl()).thenReturn(VERIFICATION_PATH);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(savedUser);
    when(wingsPersistence.saveAndGet(eq(EmailVerificationToken.class), any(EmailVerificationToken.class)))
        .thenReturn(new EmailVerificationToken(USER_ID));

    when(accountService.save(any(Account.class), eq(false), eq(false))).thenReturn(account);
    when(wingsPersistence.query(eq(User.class), any(PageRequest.class))).thenReturn(aPageResponse().build());
    when(userGroupService.list(anyString(), any(PageRequest.class), anyBoolean(), any(), any()))
        .thenReturn(aPageResponse().build());
    when(subdomainUrlHelper.getPortalBaseUrl(ACCOUNT_ID)).thenReturn(PORTAL_URL + "/");
    userService.register(userBuilder.build());

    verify(wingsPersistence).saveAndGet(eq(User.class), userArgumentCaptor.capture());
    assertThat(BCrypt.checkpw(new String(PASSWORD), userArgumentCaptor.getValue().getPasswordHash())).isTrue();
    assertThat(userArgumentCaptor.getValue().isEmailVerified()).isFalse();
    assertThat(userArgumentCaptor.getValue().getCompanyName()).isEqualTo(COMPANY_NAME);
    verify(emailDataNotificationService).send(emailDataArgumentCaptor.capture());
    assertThat(emailDataArgumentCaptor.getValue().getTo().get(0)).isEqualTo(USER_EMAIL);
    assertThat(emailDataArgumentCaptor.getValue().getTemplateName()).isEqualTo(SIGNUP_EMAIL_TEMPLATE_NAME);
    assertThat(((Map) emailDataArgumentCaptor.getValue().getTemplateModel()).get("name")).isEqualTo(USER_NAME);
    assertThat(((Map<String, String>) emailDataArgumentCaptor.getValue().getTemplateModel()).get("url"))
        .startsWith(PORTAL_URL + "/#" + VERIFICATION_PATH);
    verify(cache).remove(USER_ID);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void shouldIncludeEnvPathInTrialSignupEmailUrl() {
    when(configuration.isTrialRegistrationAllowed()).thenReturn(true);
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("https://qa.harness.io/");
    doNothing().when(signupService).validatePassword(any());
    when(authenticationUtils.buildAbsoluteUrl(any(), (String) any(), any())).thenCallRealMethod();

    String inviteId = UUIDGenerator.generateUuid();
    when(wingsPersistence.save(any(UserInvite.class))).thenReturn(inviteId);

    UserInvite userInvite = getUserInvite();

    userService.trialSignup(userInvite);

    verify(emailDataNotificationService).send(emailDataArgumentCaptor.capture());

    String templateUrl = ((Map<String, String>) emailDataArgumentCaptor.getValue().getTemplateModel()).get("url");
    assertThat(templateUrl).isNotNull();
    assertThat(UrlValidator.getInstance().isValid(templateUrl)).isTrue();
    assertThat(templateUrl.startsWith("https://qa.harness.io/")).isTrue();
    assertThat(templateUrl.contains("userInviteId=" + inviteId)).isTrue();
    assertThat(templateUrl.contains("email="
                   + "user%40wings.software"))
        .isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testBlockBlacklistedEmailRegistration() {
    when(configuration.isBlacklistedEmailDomainsAllowed()).thenReturn(false);
    when(configuration.isTrialRegistrationAllowed()).thenReturn(true);
    doThrow(new SignupException("Invalid domain")).when(blackListedDomainChecker).check(Mockito.anyString());
    try {
      UserInvite userInvite = getUserInvite();
      userInvite.setEmail(TEMPORARY_EMAIL);
      userService.trialSignup(userInvite);
      fail("Temporary is not allowed for trial signup");
    } catch (SignupException e) {
      // Exception is expected as temporary emails is not allowed.
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testNewUserSignup() throws URISyntaxException {
    when(configuration.isTrialRegistrationAllowed()).thenReturn(true);
    when(configuration.getPortal().getUrl()).thenReturn("https://qa.harness.io");
    doNothing().when(signupService).validatePassword(any());

    String inviteId = UUIDGenerator.generateUuid();
    when(wingsPersistence.save(any(UserInvite.class))).thenReturn(inviteId);

    String email = "testuser@account10.com";
    String accountName = "ACCOUNT10";
    String userName = "testuser";
    UserInvite userInvite = anUserInvite()
                                .withAccountName(accountName)
                                .withCompanyName("COMPANY10")
                                .withEmail(email)
                                .withName(userName)
                                .build();
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL);
    userInvite.setPassword("password".toCharArray());
    UtmInfo utmInfo = UtmInfo.builder().utmCampaign("campaign").utmContent("content").utmSource("source").build();
    userInvite.setUtmInfo(utmInfo);
    when(authenticationUtils.getDefaultAccount(any())).thenReturn(anAccount().withUuid(ACCOUNT_ID).build());
    when(authenticationUtils.buildAbsoluteUrl(anyString(), anyString(), any())).thenReturn(new URI(StringUtils.EMPTY));
    userService.trialSignup(userInvite);
    verify(eventPublishHelper).publishTrialUserSignupEvent(inviteId, email, userInvite);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testValidateTrialSignupEmailWithIllegalChars() {
    when(configuration.isBlacklistedEmailDomainsAllowed()).thenReturn(false);
    when(configuration.isTrialRegistrationAllowed()).thenReturn(true);

    for (Character illegalChar : userService.ILLEGAL_CHARACTERS) {
      try {
        String email = "test" + illegalChar + "User@abc.com";
        UserInvite userInvite = getUserInvite();
        userInvite.setEmail(email);
        userService.trialSignup(userInvite);
        fail("Email with illegal character is not allowed for trial signup");
      } catch (WingsException e) {
        // Exception is expected as temporary emails is not allowed.
      }
    }
  }

  /**
   * Test register for existing user.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldRegisterExistingUser() {
    User existingUser = userBuilder.uuid(generateUuid()).build();
    User savedUser = userBuilder.uuid(USER_ID)
                         .emailVerified(false)
                         .companyName(COMPANY_NAME)
                         .accountName(ACCOUNT_NAME)
                         .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                         .build();
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .build();

    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(configuration.getPortal().getVerificationUrl()).thenReturn(VERIFICATION_PATH);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(savedUser);
    when(accountService.save(any(Account.class), eq(false), eq(false))).thenReturn(account);
    when(wingsPersistence.query(eq(User.class), any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(Lists.newArrayList(existingUser)).build());
    when(wingsPersistence.saveAndGet(eq(EmailVerificationToken.class), any(EmailVerificationToken.class)))
        .thenReturn(anEmailVerificationToken().withToken("token123").build());
    when(userGroupService.list(anyString(), any(PageRequest.class), anyBoolean(), any(), any()))
        .thenReturn(aPageResponse().build());
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL);

    userService.register(userBuilder.build());

    verify(wingsPersistence).saveAndGet(eq(User.class), userArgumentCaptor.capture());
    assertThat(BCrypt.checkpw(new String(PASSWORD), userArgumentCaptor.getValue().getPasswordHash())).isTrue();
    assertThat(userArgumentCaptor.getValue().isEmailVerified()).isFalse();
    assertThat(userArgumentCaptor.getValue().getCompanyName()).isEqualTo(COMPANY_NAME);

    verify(emailDataNotificationService).send(emailDataArgumentCaptor.capture());
    assertThat(emailDataArgumentCaptor.getValue().getTo().get(0)).isEqualTo(USER_EMAIL);
    assertThat(emailDataArgumentCaptor.getValue().getTemplateName()).isEqualTo("signup");
    assertThat(((Map) emailDataArgumentCaptor.getValue().getTemplateModel()).get("name")).isEqualTo(USER_NAME);
    assertThat(((Map<String, String>) emailDataArgumentCaptor.getValue().getTemplateModel()).get("url"))
        .contains(VERIFICATION_PATH + "/token123");
    verify(cache).remove(USER_ID);
  }

  /**
   * Should match password.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldMatchPassword() {
    String hashpw = hashpw(new String(PASSWORD), BCrypt.gensalt());
    assertThat(userService.matchPassword(PASSWORD, hashpw)).isTrue();
  }

  /**
   * Should update user.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldUpdateUser() {
    List<Role> roles = Lists.newArrayList(
        aRole().withUuid(generateUuid()).withRoleType(RoleType.APPLICATION_ADMIN).withAppId(generateUuid()).build());
    User user = anUser().appId(APP_ID).uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).roles(roles).build();
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.set("name", USER_NAME);
    updateOperations.set("roles", roles);

    userService.update(user);
    Query<User> userQuery = wingsPersistence.createQuery(User.class).filter(BaseKeys.uuid, user.getUuid());
    verify(wingsPersistence).findAndModify(userQuery, updateOperations, HPersistence.returnOldOptions);
    verify(cache).remove(USER_ID);
  }

  /**
   * Should update user profile.
   */
  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void shouldUpdateUserProfile() {
    User user = anUser().appId(APP_ID).uuid(USER_ID).appId(APP_ID).name(USER_NAME).build();
    userService.updateUserProfile(user);
    verify(updateOperations).set(UserKeys.name, USER_NAME);
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.set(UserKeys.name, USER_NAME);
    Query<User> userQuery = wingsPersistence.createQuery(User.class).filter(BaseKeys.uuid, user.getUuid());
    verify(wingsPersistence).findAndModify(userQuery, updateOperations, HPersistence.returnOldOptions);
    verify(cache).remove(USER_ID);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldAuditUpdateUserProfile() {
    User user = anUser().appId(APP_ID).uuid(USER_ID).appId(APP_ID).name(USER_NAME).build();
    userService.updateUserProfile(user);
    verify(updateOperations).set(UserKeys.name, USER_NAME);
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    verify(auditServiceHelper, times(user.getAccounts().size()))
        .reportForAuditingUsingAccountId(anyString(), eq(null), eq(user), eq(Type.UPDATE));
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void shouldUpdateUserProfileWithoutName() {
    User user = anUser().appId(APP_ID).uuid(USER_ID).appId(APP_ID).build();
    userService.updateUserProfile(user);
    verify(updateOperations).unset(UserKeys.name);
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.unset(UserKeys.name);
    Query<User> query = wingsPersistence.createQuery(User.class).filter(BaseKeys.uuid, user.getUuid());
    verify(wingsPersistence).findAndModify(query, updateOperations, HPersistence.returnOldOptions);
    verify(cache).remove(USER_ID);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void addEventToUserMarketoCampaigns() {
    EventType eventType = EventType.USER_INVITED_FROM_EXISTING_ACCOUNT;
    User user = anUser().appId(APP_ID).uuid(USER_ID).appId(APP_ID).build();
    doReturn(user).when(cache).get(any(String.class));
    userService.addEventToUserMarketoCampaigns(user.getUuid(), eventType);
    verify(updateOperations).set(UserKeys.reportedMarketoCampaigns, Sets.newHashSet(eventType.name()));

    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.set(UserKeys.reportedMarketoCampaigns, Sets.newHashSet(eventType.name()));
    verify(wingsPersistence).update(user, updateOperations);
    verify(wingsPersistence).getWithAppId(User.class, APP_ID, USER_ID);
    verify(cache).remove(USER_ID);
  }

  /**
   * Should list users.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListUsers() {
    PageRequest<User> request = new PageRequest<>();
    request.addFilter("appId", EQ, GLOBAL_APP_ID);
    userService.list(request, false);
    verify(wingsPersistence).query(eq(User.class), pageRequestArgumentCaptor.capture());
    SearchFilter filter = (SearchFilter) pageRequestArgumentCaptor.getValue().getFilters().get(0);
    assertThat(filter.getFieldName()).isEqualTo("appId");
    assertThat(filter.getFieldValues()).containsExactly(GLOBAL_APP_ID);
    assertThat(filter.getOp()).isEqualTo(EQ);
  }

  /**
   * Should delete user.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteUser() {
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(userBuilder.uuid(USER_ID).build());
    when(wingsPersistence.delete(User.class, USER_ID)).thenReturn(true);
    when(wingsPersistence.findAndDelete(any(), any())).thenReturn(userBuilder.uuid(USER_ID).build());
    when(userGroupService.list(ACCOUNT_ID,
             aPageRequest().withLimit("0").addFilter(UserGroupKeys.memberIds, HAS, USER_ID).build(), true, null, null))
        .thenReturn(aPageResponse().withResponse(Collections.emptyList()).withTotal(0).withLimit("0").build());
    userService.delete(ACCOUNT_ID, USER_ID);
    verify(wingsPersistence).findAndDelete(any(), any());
    verify(cache).remove(USER_ID);
    verify(auditServiceHelper, times(1)).reportDeleteForAuditingUsingAccountId(eq(ACCOUNT_ID), any(User.class));
  }

  /**
   * Should delete user flow V2.
   */
  @Test
  @Owner(developers = {BOOPESH, SHASHANK})
  @Category(UnitTests.class)
  public void shouldDeleteUserV2WithAccountLevelDataFFOff() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .build();
    User user = userBuilder.uuid(USER_ID).accounts(Arrays.asList(account)).build();
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, CG, UserSource.MANUAL);
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(user);
    when(wingsPersistence.delete(User.class, USER_ID)).thenReturn(true);
    when(wingsPersistence.findAndDelete(any(), any())).thenReturn(user);
    when(userGroupService.list(anyString(), any(PageRequest.class), anyBoolean(), any(), any()))
        .thenReturn(aPageResponse().build());
    userService.delete(ACCOUNT_ID, USER_ID);
    verify(wingsPersistence).findAndDelete(any(), any());
    verify(cache).remove(USER_ID);
    verify(auditServiceHelper, times(1)).reportDeleteForAuditingUsingAccountId(eq(ACCOUNT_ID), any(User.class));
    mockRestStatic.close();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void shouldDeleteUserV2WithAccountLevelDataFFOn() {
    when(accountService.isNextGenEnabled(ACCOUNT_ID)).thenReturn(true);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(false);
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .build();
    User user = userBuilder.uuid(USER_ID).accounts(Arrays.asList(account)).build();
    when(featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, ACCOUNT_ID)).thenReturn(true);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, CG, UserSource.MANUAL);
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(user);
    when(wingsPersistence.delete(User.class, USER_ID)).thenReturn(true);
    when(wingsPersistence.findAndDelete(any(), any())).thenReturn(user);
    when(userGroupService.list(anyString(), any(PageRequest.class), anyBoolean(), any(), any()))
        .thenReturn(aPageResponse().build());
    userService.delete(ACCOUNT_ID, USER_ID);
    verify(wingsPersistence).findAndDelete(any(), any());
    verify(cache).remove(USER_ID);
    verify(auditServiceHelper, times(1)).reportDeleteForAuditingUsingAccountId(eq(ACCOUNT_ID), any(User.class));

    user.getUserAccountLevelDataMap().remove(ACCOUNT_ID);
    userServiceHelper.populateAccountToUserMapping(user, ACCOUNT_ID, NG, UserSource.MANUAL);
    userService.delete(ACCOUNT_ID, USER_ID);
    verify(wingsPersistence, times(1)).findAndDelete(any(), any());
    verify(cache, times(2)).remove(USER_ID);
    verify(auditServiceHelper, times(1)).reportDeleteForAuditingUsingAccountId(eq(ACCOUNT_ID), any(User.class));
    mockRestStatic.close();
  }
  /**
   * Should fetch user.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldFetchUser() {
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(userBuilder.uuid(USER_ID).build());
    User user = userService.get(USER_ID);
    verify(wingsPersistence).get(User.class, USER_ID);
    assertThat(user).isEqualTo(userBuilder.uuid(USER_ID).build());
  }

  /**
   * Should throw exception if user does not exist.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfUserDoesNotExist() {
    assertThatThrownBy(() -> userService.get("INVALID_USER_ID"))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessage("User does not exist");
  }

  /**
   * Should verify email.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldVerifyEmail() {
    when(verificationQuery.get())
        .thenReturn(anEmailVerificationToken().withUuid("TOKEN_ID").withUserId(USER_ID).withToken("TOKEN").build());

    userService.verifyToken("TOKEN");

    verify(verificationQuery).filter("appId", GLOBAL_APP_ID);
    verify(verificationQuery).filter("token", "TOKEN");
    verify(wingsPersistence).updateFields(User.class, USER_ID, ImmutableMap.of("emailVerified", true));
    verify(wingsPersistence).delete(EmailVerificationToken.class, "TOKEN_ID");
  }

  /**
   * Should send email.
   *
   * @throws EmailException    the email exception
   * @throws TemplateException the template exception
   * @throws IOException       the io exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSendEmail() throws EmailException, TemplateException, IOException {
    emailDataNotificationService.send(
        EmailData.builder().to(asList("anubhaw@gmail.com")).subject("wings-test").body("hi").build());
  }

  /**
   * Test assign role to user.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldAddRole() {
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(userBuilder.uuid(USER_ID).build());
    when(roleService.get(ROLE_ID)).thenReturn(aRole().withUuid(ROLE_ID).withName(ROLE_NAME).build());

    userService.addRole(USER_ID, ROLE_ID);
    verify(wingsPersistence, times(2)).get(User.class, USER_ID);
    verify(wingsPersistence).update(any(Query.class), any());
    verify(query).filter(Mapper.ID_KEY, USER_ID);
    verify(updateOperations).addToSet("roles", aRole().withUuid(ROLE_ID).withName(ROLE_NAME).build());
    verify(cache).remove(USER_ID);
  }

  /**
   * Test revoke role to user.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldRevokeRole() {
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(userBuilder.uuid(USER_ID).build());
    when(roleService.get(ROLE_ID)).thenReturn(aRole().withUuid(ROLE_ID).withName(ROLE_NAME).build());

    userService.revokeRole(USER_ID, ROLE_ID);
    verify(wingsPersistence, times(2)).get(User.class, USER_ID);
    verify(wingsPersistence).update(any(Query.class), any());
    verify(query).filter(Mapper.ID_KEY, USER_ID);
    verify(updateOperations).removeAll("roles", aRole().withUuid(ROLE_ID).withName(ROLE_NAME).build());
    verify(cache).remove(USER_ID);
  }

  /**
   * Should invite new user.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldInviteNewUser() {
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmails(asList(USER_EMAIL))
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .build();
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                          .build();

    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL);
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(userBuilder.uuid(USER_ID).build());
    when(authenticationUtils.getDefaultAccount(any())).thenReturn(account);
    when(wingsPersistence.getWithAppId(UserInvite.class, GLOBAL_APP_ID, USER_INVITE_ID)).thenReturn(userInvite);
    userService.inviteUsers(userInvite);
    verify(wingsPersistence).save(any(UserInvite.class));
    verify(wingsPersistence).saveAndGet(eq(User.class), any(User.class));
    verify(auditServiceHelper, times(userInvite.getEmails().size()))
        .reportForAuditingUsingAccountId(eq(ACCOUNT_ID), eq(null), any(User.class), eq(Type.CREATE));

    verify(emailDataNotificationService).send(emailDataArgumentCaptor.capture());
    assertThat(emailDataArgumentCaptor.getValue().getTemplateName()).isEqualTo(INVITE_EMAIL_TEMPLATE_NAME);
  }

  /**
   * Should invite new user - mixed case email.
   */
  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldInviteNewUserMixedCaseEmail() throws EmailException, TemplateException, IOException {
    String mixedEmail = "UseR@wings.software ";
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmails(asList(mixedEmail))
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .build();

    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    Account account = anAccount()
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                          .build();
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(userBuilder.uuid(USER_ID).build());
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL);
    when(authenticationUtils.getDefaultAccount(any())).thenReturn(account);
    when(wingsPersistence.getWithAppId(UserInvite.class, GLOBAL_APP_ID, USER_INVITE_ID)).thenReturn(userInvite);
    userService.inviteUsers(userInvite);

    verify(wingsPersistence).saveAndGet(eq(User.class), userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue()).hasFieldOrPropertyWithValue("email", mixedEmail.trim().toLowerCase());
    verify(auditServiceHelper, times(userInvite.getEmails().size()))
        .reportForAuditingUsingAccountId(eq(ACCOUNT_ID), eq(null), any(User.class), eq(Type.CREATE));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void checkInviteUser() {
    boolean val = userService.checkIfUserLimitHasReached(ACCOUNT_ID, "admin@harness.io");
    assertThat(val).isFalse();
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void testInviteNewUser_invalidEmail_shouldFail() {
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmails(asList(INVALID_USER_EMAIL))
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .build();

    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount()
                        .withCompanyName(COMPANY_NAME)
                        .withUuid(ACCOUNT_ID)
                        .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                        .build());
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(userBuilder.uuid(USER_ID).build());

    try {
      userService.inviteUsers(userInvite);
      verify(auditServiceHelper, atLeastOnce())
          .reportForAuditingUsingAccountId(
              eq(userInvite.getAccountId()), eq(null), any(UserInvite.class), any(Type.class));
      fail("Exception is expected when inviting with invalid user email");
    } catch (WingsException e) {
      // Ignore, exception expected here.
    }
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testInviteNewUser_emptyEmail_shouldFail() {
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmails(asList())
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .build();

    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount()
                        .withCompanyName(COMPANY_NAME)
                        .withUuid(ACCOUNT_ID)
                        .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                        .build());
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(userBuilder.uuid(USER_ID).build());

    try {
      userService.inviteUsers(userInvite);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo("No email provided. Please provide vaild email info");
    }
  }

  /**
   * Should invite existing user.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldInviteExistingUser() {
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmails(asList(USER_EMAIL))
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .build();

    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withCompanyName(COMPANY_NAME).withUuid(ACCOUNT_ID).build());
    when(query.get()).thenReturn(userBuilder.uuid(USER_ID).build());
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);

    userService.inviteUsers(userInvite);

    verify(query, times(2)).field("email");
    verify(end, times(2)).equal(USER_EMAIL);
    verify(accountService).get(ACCOUNT_ID);
    verify(wingsPersistence).save(userInvite);
    verify(wingsPersistence).getWithAppId(UserInvite.class, GLOBAL_APP_ID, USER_INVITE_ID);
  }

  /**
   * Should Override 2FA on user
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldOverrideTwoFactorForUser() {
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(userBuilder.uuid(USER_ID).build());
    when(wingsPersistence.get(Account.class, ACCOUNT_ID))
        .thenReturn(Account.Builder.anAccount().withUuid(ACCOUNT_ID).build());
    // userService.overrideTwoFactorforAccount(ACCOUNT_ID, true);
  }

  /**
   * Should complete invite.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCompleteInvite() {
    Account temp = Account.Builder.anAccount()
                       .withAccountName("harness")
                       .withCompanyName("harness")
                       .withAppId(GLOBAL_APP_ID)
                       .withUuid("uuid")
                       .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                       .build();

    User user = User.Builder.anUser().uuid(USER_ID).name("UserName").email(USER_EMAIL).build();
    user.getAccounts().add(temp);

    when(accountService.get(anyString())).thenReturn(temp);
    when(query.get()).thenReturn(user);
    when(accountService.get(ACCOUNT_ID)).thenReturn(temp);
    when(userInviteQuery.get())
        .thenReturn(anUserInvite().withUuid(USER_INVITE_ID).withAccountId(ACCOUNT_ID).withEmail(USER_EMAIL).build());
    when(loginSettingsService.verifyPasswordStrength(Mockito.any(Account.class), Mockito.any(char[].class)))
        .thenReturn(true);
    when(wingsPersistence.findAndModify(any(), any(), any())).thenReturn(user);

    UserInvite userInvite =
        anUserInvite().withAccountId(ACCOUNT_ID).withEmail(USER_EMAIL).withUuid(USER_INVITE_ID).build();
    userInvite.setName(USER_NAME);
    userInvite.setPassword(USER_PASSWORD);
    userService.completeInvite(userInvite);

    verify(wingsPersistence, times(1)).updateFields(any(Class.class), anyString(), any(HashMap.class));
    verify(wingsPersistence, times(2)).findAndModify(any(), any(), any());
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void testResendInvitationEmail() {
    when(accountService.get(any())).thenReturn(anAccount().withUuid(UUIDGenerator.generateUuid()).build());
    when(userInviteQuery.get()).thenReturn(anUserInvite().withEmail(USER_EMAIL).build());
    when(query.get()).thenReturn(anUser().build());
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL + "/");
    when(wingsPersistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    assertThat(userService.resendInvitationEmail(ACCOUNT_ID, "testinviteuser2@harness.io")).isTrue();
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldResendEmailIfUserGroupsChange() {
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .withUserGroups(asList(UserGroup.builder().uuid(UUIDGenerator.generateUuid()).build()))
                                .build();
    User user =
        userBuilder.uuid(USER_ID).pendingAccounts(Arrays.asList(anAccount().withUuid(ACCOUNT_ID).build())).build();
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withCompanyName(COMPANY_NAME).withUuid(ACCOUNT_ID).build());
    when(userInviteQuery.get())
        .thenReturn(anUserInvite()
                        .withUserGroups(Arrays.asList(UserGroup.builder().uuid(UUIDGenerator.generateUuid()).build()))
                        .withEmail(TEMPORARY_EMAIL)
                        .build());
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL + "/");
    when(query.get()).thenReturn(user);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    doNothing().when(signupService).checkIfEmailIsValid(any());
    userService.inviteUser(userInvite, true, false);
    verify(signupService, times(1)).sendEmail(any(), anyString(), any());
  }

  /**
   * Should get account role.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldGetAccountRole() {
    List<Role> roles =
        asList(aRole().withUuid(generateUuid()).withRoleType(RoleType.ACCOUNT_ADMIN).withAccountId(ACCOUNT_ID).build());
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(userBuilder.uuid(USER_ID).roles(roles).build());
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build());

    AccountRole userAccountRole = userService.getUserAccountRole(USER_ID, ACCOUNT_ID);
    assertThat(userAccountRole)
        .isNotNull()
        .extracting("accountId", "accountName", "allApps")
        .containsExactly(ACCOUNT_ID, ACCOUNT_NAME, true);
    assertThat(userAccountRole.getResourceAccess()).isNotNull();
    for (ResourceType resourceType : ResourceType.values()) {
      for (Action action : Action.values()) {
        assertThat(userAccountRole.getResourceAccess()).contains(ImmutablePair.of(resourceType, action));
      }
    }
  }

  /**
   * Should get account for all aps admin role.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldGetAccountForAllApsAdminRole() {
    List<Role> roles = asList(aRole()
                                  .withUuid(generateUuid())
                                  .withRoleType(RoleType.APPLICATION_ADMIN)
                                  .withAccountId(ACCOUNT_ID)
                                  .withAllApps(true)
                                  .build());
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(userBuilder.uuid(USER_ID).roles(roles).build());
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build());

    AccountRole userAccountRole = userService.getUserAccountRole(USER_ID, ACCOUNT_ID);
    assertThat(userAccountRole)
        .isNotNull()
        .extracting("accountId", "accountName", "allApps")
        .containsExactly(ACCOUNT_ID, ACCOUNT_NAME, true);
    assertThat(userAccountRole.getResourceAccess()).isNotNull();
    for (ResourceType resourceType : asList(APPLICATION, SERVICE, ARTIFACT, DEPLOYMENT, WORKFLOW, ENVIRONMENT)) {
      for (Action action : Action.values()) {
        assertThat(userAccountRole.getResourceAccess()).contains(ImmutablePair.of(resourceType, action));
      }
    }
  }

  /**
   * Should get application role.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetApplicationRole() {
    List<Role> roles =
        asList(aRole().withUuid(generateUuid()).withRoleType(RoleType.ACCOUNT_ADMIN).withAccountId(ACCOUNT_ID).build());
    when(wingsPersistence.get(User.class, USER_ID)).thenReturn(userBuilder.uuid(USER_ID).roles(roles).build());
    when(appService.get(APP_ID)).thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build());

    ApplicationRole applicationRole = userService.getUserApplicationRole(USER_ID, APP_ID);
    assertThat(applicationRole)
        .isNotNull()
        .extracting("appId", "appName", "allEnvironments")
        .containsExactly(APP_ID, APP_NAME, true);
    assertThat(applicationRole.getResourceAccess()).isNotNull();
    for (ResourceType resourceType : asList(APPLICATION, SERVICE, ARTIFACT, DEPLOYMENT, WORKFLOW, ENVIRONMENT)) {
      for (Action action : Action.values()) {
        assertThat(applicationRole.getResourceAccess()).contains(ImmutablePair.of(resourceType, action));
      }
    }
  }

  /**
   * Should send reset password email.
   *
   * @throws EmailException    the email exception
   * @throws TemplateException the template exception
   * @throws IOException       the io exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSendResetPasswordEmail() throws EmailException, TemplateException, IOException {
    ArrayList<Account> accounts = new ArrayList<>();
    accounts.add(new Account());
    when(query.get()).thenReturn(userBuilder.uuid(USER_ID).accounts(accounts).build());
    when(configuration.getPortal().getJwtPasswordSecret()).thenReturn("SECRET");
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL + "/");
    UserResource.ResetPasswordRequest resetPasswordRequest = new UserResource.ResetPasswordRequest();
    resetPasswordRequest.setEmail(USER_EMAIL);
    resetPasswordRequest.setIsNG(false);
    userService.resetPassword(resetPasswordRequest);

    verify(emailDataNotificationService).send(emailDataArgumentCaptor.capture());
    assertThat(emailDataArgumentCaptor.getValue().getTo().get(0)).isEqualTo(USER_EMAIL);
    assertThat(emailDataArgumentCaptor.getValue().getTemplateName()).isEqualTo("reset_password");
    assertThat(((Map) emailDataArgumentCaptor.getValue().getTemplateModel()).get("name")).isEqualTo(USER_NAME);
    assertThat(((Map<String, String>) emailDataArgumentCaptor.getValue().getTemplateModel()).get("url"))
        .startsWith(PORTAL_URL + "/#/reset-password/");
    assertThat(((Map<String, String>) emailDataArgumentCaptor.getValue().getTemplateModel()).get("url").length())
        .isGreaterThan((PORTAL_URL + "/#/reset-password/").length());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldJoinAccount() {
    ArrayList<Account> accounts = new ArrayList<>();
    accounts.add(new Account());
    when(query.get()).thenReturn(userBuilder.uuid(USER_ID).email(NEW_USER_EMAIL).accounts(accounts).build());
    when(configuration.getPortal().getJwtPasswordSecret()).thenReturn("SECRET");
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(configuration.isTrialRegistrationAllowed()).thenReturn(true);
    when(configuration.getSupportEmail()).thenReturn(SUPPORT_EMAIL);
    doNothing().when(blackListedDomainChecker).check(Mockito.anyString());

    AccountJoinRequest accountJoinRequest = AccountJoinRequest.builder()
                                                .email(NEW_USER_EMAIL)
                                                .name(NEW_USER_NAME)
                                                .note(NOTE)
                                                .companyName(COMPANY_NAME)
                                                .build();
    userService.accountJoinRequest(accountJoinRequest);
    verify(emailDataNotificationService).send(emailDataArgumentCaptor.capture());
    assertThat(emailDataArgumentCaptor.getValue().getTo().get(0)).isEqualTo(SUPPORT_EMAIL);
    assertThat(emailDataArgumentCaptor.getValue().getCc()).isEmpty();
    assertThat(((Map) emailDataArgumentCaptor.getValue().getTemplateModel()).get("msg"))
        .asString()
        .startsWith("Recipient is not a Harness user in production cluster - ");
  }

  /**
   * Should update password.
   *
   * @throws UnsupportedEncodingException the unsupported encoding exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldUpdatePassword() throws UnsupportedEncodingException {
    when(query.get()).thenReturn(userBuilder.uuid(USER_ID).build());
    when(configuration.getPortal().getJwtPasswordSecret()).thenReturn("SECRET");
    Algorithm algorithm = Algorithm.HMAC256("SECRET");
    String token = JWT.create()
                       .withIssuer("Harness Inc")
                       .withIssuedAt(new Date())
                       .withExpiresAt(new Date(System.currentTimeMillis() + 4 * 60 * 60 * 1000)) // 4 hrs
                       .withClaim("email", USER_EMAIL)
                       .sign(algorithm);

    userService.updatePassword(token, USER_PASSWORD);

    verify(query, times(1)).filter("email", USER_EMAIL);
    verify(emailDataNotificationService).send(any());
    verify(authService).invalidateAllTokensForUser(USER_ID);
    verify(wingsPersistence).update(eq(userBuilder.uuid(USER_ID).build()), any(UpdateOperations.class));
    verify(updateOperations).set(eq("passwordHash"), anyString());
    verify(updateOperations).set(eq("passwordChangedAt"), anyLong());
  }

  /**
   * Should update password and clear lockout info
   *
   */
  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void shouldUpdatePasswordAndClearLockoutInfo() throws UnsupportedEncodingException {
    when(query.get()).thenReturn(userBuilder.uuid(USER_ID).build());
    when(configuration.getPortal().getJwtPasswordSecret()).thenReturn("SECRET");
    Algorithm algorithm = Algorithm.HMAC256("SECRET");
    String token = JWT.create()
                       .withIssuer("Harness Inc")
                       .withIssuedAt(new Date())
                       .withExpiresAt(new Date(System.currentTimeMillis() + 4 * 60 * 60 * 1000)) // 4 hrs
                       .withClaim("email", USER_EMAIL)
                       .sign(algorithm);

    userService.updatePassword(token, USER_PASSWORD);
    when(loginSettingsService.verifyPasswordStrength(Mockito.any(Account.class), Mockito.any(char[].class)))
        .thenReturn(true);

    verify(query, times(1)).filter("email", USER_EMAIL);
    verify(emailDataNotificationService).send(any());
    verify(authService).invalidateAllTokensForUser(USER_ID);
    verify(wingsPersistence).update(eq(userBuilder.uuid(USER_ID).build()), any(UpdateOperations.class));
    verify(updateOperations).set(eq("passwordHash"), anyString());
    verify(updateOperations).set(eq("passwordExpired"), anyBoolean());
    verify(updateOperations).set(eq("passwordChangedAt"), anyLong());
    verify(loginSettingsService, times(1)).updateUserLockoutInfo(any(), any(), anyInt());
  }

  /**
   * Should add Account.
   *
   * @throws UnsupportedEncodingException the unsupported encoding exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldAddAccount() throws UnsupportedEncodingException {
    Account account =
        anAccount().withUuid(ACCOUNT_ID).withCompanyName(COMPANY_NAME).withAccountName(ACCOUNT_NAME).build();
    when(accountService.save(any(Account.class), false)).thenReturn(account);
    when(roleService.getAccountAdminRole(ACCOUNT_ID)).thenReturn(aRole().build());
    when(updateOperations.addToSet(any(), any())).thenReturn(updateOperations);
    User user = anUser().uuid(USER_ID).email(USER_EMAIL).build();
    Account created = userService.addAccount(account, user, true);
    assertThat(created).isEqualTo(account);
    verify(accountService).exists(eq(ACCOUNT_NAME));
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateNameThrowsInvalidArgumentsException() {
    final String blankName = "  ";
    assertThatThrownBy(() -> userService.validateName(blankName)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> userService.validateName(null)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> userService.validateName("<a href='http://authorization.site'>Click ME</a>"))
        .isInstanceOf(InvalidRequestException.class);
    final String nameWithBraces = "firstName lastName (firstName.lastName)";
    assertDoesNotThrow(() -> userService.validateName(nameWithBraces));
  }
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testJWTToken() {
    when(portalConfig.getJwtMultiAuthSecret())
        .thenReturn("5E1YekVGldTSS5Kt0GHlyWrJ6fJHmee9nXSBssefAWSOgdMwAvvbvJalnYENZ0H0EealN0CxHh34gUCN");
    HashMap<String, String> claimMap = new HashMap<>();
    claimMap.put("email", "testUser@harness.io");
    assertThat(userService.verifyJWTToken(userService.generateJWTToken(User.Builder.anUser().build(), claimMap,
                                              JWT_CATEGORY.MULTIFACTOR_AUTH, false),
                   JWT_CATEGORY.MULTIFACTOR_AUTH))
        .isEqualTo(null);

    try {
      userService.verifyJWTToken(
          userService.generateJWTToken(User.Builder.anUser().build(), claimMap, JWT_CATEGORY.MULTIFACTOR_AUTH, false)
              + "fakeData",
          JWT_CATEGORY.MULTIFACTOR_AUTH);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      // Ignore
    }

    try {
      userService.verifyJWTToken("fakeData", JWT_CATEGORY.MULTIFACTOR_AUTH);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      // Ignore
    }
  }

  /**
   * Should resend the invitation email
   */
  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void resendInvitationEmail() {
    User user = userBuilder.uuid(USER_ID)
                    .emailVerified(false)
                    .companyName(COMPANY_NAME)
                    .accountName(ACCOUNT_NAME)
                    .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                    .build();
    UserInvite userInvite =
        anUserInvite().withAppId(GLOBAL_APP_ID).withAccountId(ACCOUNT_ID).withEmail(USER_EMAIL).build();
    Account account = anAccount()
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                          .build();
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    when(wingsPersistence.delete(any(Query.class))).thenReturn(true);
    when(mockedUserService.getUserByEmail(USER_EMAIL)).thenReturn(user);
    when(mockedUserService.deleteInvites(ACCOUNT_ID, USER_EMAIL)).thenCallRealMethod();
    doCallRealMethod().when(mockedUserService).sendNewInvitationMail(userInvite, account, user);
    when(authenticationUtils.getDefaultAccount(user)).thenReturn(account);
    when(userInviteQuery.get()).thenReturn(userInvite);
    when(query.get()).thenReturn(user);
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL);

    userService.resendInvitationEmail(ACCOUNT_ID, USER_EMAIL);

    verify(accountService, times(1)).get(ACCOUNT_ID);
    verify(emailDataNotificationService).send(emailDataArgumentCaptor.capture());
    assertThat(emailDataArgumentCaptor.getValue().getTemplateName()).isEqualTo(INVITE_EMAIL_TEMPLATE_NAME);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testUserVerified() {
    Account account = anAccount()
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                          .build();
    User user = userBuilder.uuid(USER_ID)
                    .emailVerified(false)
                    .companyName(COMPANY_NAME)
                    .accountName(ACCOUNT_NAME)
                    .passwordHash(hashpw(new String(PASSWORD), BCrypt.gensalt()))
                    .accounts(Arrays.asList(account))
                    .build();

    assertThat(userService.isUserVerified(user)).isFalse();
    user.setEmailVerified(true);
    assertThat(userService.isUserVerified(user)).isTrue();

    account.setAuthenticationMechanism(AuthenticationMechanism.LDAP);
    user.setEmailVerified(false);
    assertThat(userService.isUserVerified(user)).isTrue();

    account.setAuthenticationMechanism(AuthenticationMechanism.SAML);
    assertThat(userService.isUserVerified(user)).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testLogout() {
    String token = "token";
    String logoutUrl = "logout_url";
    String userName = "user_name";

    Account account = getAccount(PAID);
    account.setUuid("accountId");

    User user = anUser()
                    .name(userName)
                    .appId(generateUuid())
                    .defaultAccountId(account.getUuid())
                    .token(token)
                    .accounts(Arrays.asList(account))
                    .email("emailId")
                    .emailVerified(true)
                    .uuid("userId")
                    .build();

    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(account.getUuid())
                                    .logoutUrl(logoutUrl)
                                    .ssoType(SSOType.SAML)
                                    .displayName("display_name")
                                    .build();
    wingsPersistence.save(user);

    UserGroup userGroup = new UserGroup();
    userGroup.setAccountId(account.getUuid());
    userGroup.setMemberIds(Arrays.asList(user.getUuid()));

    PageResponse<UserGroup> val = new PageResponse<>();

    when(ssoSettingService.getSamlSettingsByAccountId(account.getUuid())).thenReturn(samlSettings);
    when(wingsPersistence.get(User.class, user.getUuid())).thenReturn(user);
    when(userGroupService.list(anyString(), any(), anyBoolean(), any(), any())).thenReturn(val);

    LogoutResponse logoutResponse = userService.logout(account.getUuid(), user.getUuid());
    assertThat(logoutResponse.getLogoutUrl()).isNotNull();
    assertThat(logoutResponse.getLogoutUrl()).isEqualTo(logoutUrl);
    verify(cache).remove(user.getUuid());
    verify(authService, times(1)).invalidateToken(user.getToken());

    wingsPersistence.delete(user);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSendAccountLockedNotificationMail() {
    String token = "token";
    String userName = "user_name";

    Account account = getAccount(PAID);
    account.setUuid("accountId");

    User user =
        anUser()
            .name("Evil<img src=https://poc.shellcode.se/spongebob-ninja.jpg><h1>Hacker</h1><svg/onload=alert()>")
            .appId(generateUuid())
            .defaultAccountId(account.getUuid())
            .token(token)
            .accounts(Arrays.asList(account))
            .email("emailId")
            .emailVerified(true)
            .uuid("userId")
            .build();

    userService.sendAccountLockedNotificationMail(user, 2);
    verify(emailDataNotificationService, times(1)).send(any(EmailData.class));
  }

  @Test(expected = UserAlreadyPresentException.class)
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldNotAllowAExistingUsersToBeAddedAgain() {
    Account account = anAccount().build();
    String savedAccount = realWingsPersistence.save(account);
    UserInvite userInvite =
        anUserInvite().withEmails(Collections.singletonList(USER_EMAIL)).withAccountId(savedAccount).build();
    User user = anUser()
                    .email(USER_EMAIL)
                    .pendingAccounts(Collections.singletonList(realWingsPersistence.get(Account.class, savedAccount)))
                    .build();
    realWingsPersistence.save(user);
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    when(accountService.get(anyString())).thenReturn(account);
    when(subdomainUrlHelper.getPortalBaseUrl(account.getUuid())).thenReturn(PORTAL_URL);
    userService.inviteUsers(userInvite);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void setNewDefaultAccountIdTest() {
    String defaultAccountCandidate;
    Account account1 = getAccount(AccountStatus.ACTIVE, PAID, "111");
    Account account2 = getAccount(AccountStatus.ACTIVE, ESSENTIALS, "222");
    User user1 = anUser().accounts(Arrays.asList(account2, account1)).build();
    defaultAccountCandidate = user1.getDefaultAccountCandidate();

    assertThat(defaultAccountCandidate).isEqualTo("111");

    Account account3 = getAccount(AccountStatus.EXPIRED, PAID, "111");
    Account account4 = getAccount(AccountStatus.ACTIVE, TRIAL, "222");
    User user2 = anUser().accounts(Arrays.asList(account3, account4)).build();
    defaultAccountCandidate = user2.getDefaultAccountCandidate();

    assertThat(defaultAccountCandidate).isEqualTo("222");

    Account account5 = getAccount(AccountStatus.INACTIVE, PAID, "111");
    Account account6 = getAccount(AccountStatus.DELETED, ESSENTIALS, "222");
    Account account7 = getAccount(AccountStatus.EXPIRED, TRIAL, "333");
    User user3 = anUser().accounts(Arrays.asList(account5, account6, account7)).build();
    defaultAccountCandidate = user3.getDefaultAccountCandidate();

    assertThat(defaultAccountCandidate).isEqualTo("333");

    Account account8 = getAccount(AccountStatus.DELETED, PAID, "111");
    Account account9 = getAccount(AccountStatus.DELETED, ESSENTIALS, "222");
    Account account10 = getAccount(AccountStatus.DELETED, TRIAL, "333");
    User user4 = anUser().accounts(Arrays.asList(account8, account9, account10)).build();
    defaultAccountCandidate = user4.getDefaultAccountCandidate();

    assertThat(defaultAccountCandidate).isEqualTo("111");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getUserAccountsAndSupportAccountsTest() {
    User user = initAccountsAndSupportAcccountsForUser();
    when(wingsPersistence.get(User.class, user.getUuid())).thenReturn(user);
    io.harness.ng.beans.PageResponse<Account> accountList =
        userService.getUserAccountsAndSupportAccounts(user.getUuid(), 0, 20, "");
    assertThat(accountList.getContent().size() == 6);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getUserAccountsPageSizeTest() {
    User user = initAccountsAndSupportAcccountsForUser();
    when(wingsPersistence.get(User.class, user.getUuid())).thenReturn(user);
    io.harness.ng.beans.PageResponse<Account> accountList =
        userService.getUserAccountsAndSupportAccounts(user.getUuid(), 0, 3, "");
    assertThat(accountList.getContent().size() == 3);
    assertThat(accountList.getContent().containsAll(getAccounts()));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getUserAccountsPageIndexTest() {
    User user = initAccountsAndSupportAcccountsForUser();
    when(wingsPersistence.get(User.class, user.getUuid())).thenReturn(user);
    io.harness.ng.beans.PageResponse<Account> accountList =
        userService.getUserAccountsAndSupportAccounts(user.getUuid(), 3, 2, "");
    assertThat(accountList.getContent().size() == 2);
    assertThat(accountList.getContent().containsAll(getSupportAccounts()));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void getUserAccountsSearchTermTest() {
    User user = initAccountsAndSupportAcccountsForUser();
    List<Account> accountTarget = Arrays.asList(user.getAccounts().get(0));
    when(accountService.getAccounts(any(PageRequest.class)))
        .thenReturn(aPageResponse().withResponse(accountTarget).build());
    when(wingsPersistence.get(User.class, user.getUuid())).thenReturn(user);
    io.harness.ng.beans.PageResponse<Account> accountListAfterFilter =
        userService.getUserAccountsAndSupportAccounts(user.getUuid(), 0, 10, "Test");
    assertThat(accountListAfterFilter.getContent().size() == 1);
    assertThat(accountListAfterFilter.getContent().get(0).getAccountName().equals("Test-Account"));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void shouldInviteNewUser_withSsoEnabled_withAutoInviteAcceptanceEnabled() {
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmails(asList(USER_EMAIL))
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .build();
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAuthenticationMechanism(AuthenticationMechanism.SAML)
                          .build();
    SamlSettings samlSettings = SamlSettings.builder()
                                    .accountId(account.getUuid())
                                    .logoutUrl("logoutUrl")
                                    .url("url")
                                    .ssoType(SSOType.SAML)
                                    .displayName("display_name")
                                    .build();

    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL);
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(accountService.isAutoInviteAcceptanceEnabled(ACCOUNT_ID)).thenReturn(true);
    when(accountService.isSSOEnabled(any())).thenReturn(true);
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(userBuilder.uuid(USER_ID).build());
    when(authenticationUtils.getDefaultAccount(any())).thenReturn(account);
    when(wingsPersistence.getWithAppId(UserInvite.class, GLOBAL_APP_ID, USER_INVITE_ID)).thenReturn(userInvite);
    when(ssoSettingService.getSamlSettingsByAccountId(ACCOUNT_ID)).thenReturn(samlSettings);

    List<InviteOperationResponse> inviteOperationResponses = userService.inviteUsers(userInvite);

    assertThat(inviteOperationResponses.get(0)).isEqualTo(InviteOperationResponse.USER_INVITED_SUCCESSFULLY);
    verify(emailDataNotificationService).send(emailDataArgumentCaptor.capture());
    assertThat(emailDataArgumentCaptor.getValue().getTemplateName())
        .isEqualTo(ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void shouldInviteNewUser_withSsoEnabled_withPLNoEmailForSamlAccountInvitesEnabled() {
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmails(asList(USER_EMAIL))
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .build();
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAuthenticationMechanism(AuthenticationMechanism.SAML)
                          .build();

    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL);
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(userBuilder.uuid(USER_ID).build());
    when(authenticationUtils.getDefaultAccount(any())).thenReturn(account);
    when(wingsPersistence.getWithAppId(UserInvite.class, GLOBAL_APP_ID, USER_INVITE_ID)).thenReturn(userInvite);
    when(accountService.isPLNoEmailForSamlAccountInvitesEnabled(anyString())).thenReturn(true);

    List<InviteOperationResponse> inviteOperationResponses = userService.inviteUsers(userInvite);

    assertThat(inviteOperationResponses.get(0)).isEqualTo(InviteOperationResponse.USER_INVITE_NOT_REQUIRED);
    verify(emailDataNotificationService, times(0)).send(any());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void shouldInviteNewUser_withScimEnabledAndSsoDisabled() {
    UserInvite userInvite = anUserInvite()
                                .withAppId(GLOBAL_APP_ID)
                                .withAccountId(ACCOUNT_ID)
                                .withEmails(asList(USER_EMAIL))
                                .withEmail(USER_EMAIL)
                                .withRoles(asList(aRole().withUuid(ROLE_ID).build()))
                                .build();
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAuthenticationMechanism(AuthenticationMechanism.SAML)
                          .build();

    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn(PORTAL_URL);
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(accountService.isSSOEnabled(any())).thenReturn(false);
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    when(wingsPersistence.save(userInvite)).thenReturn(USER_INVITE_ID);
    when(wingsPersistence.saveAndGet(eq(User.class), any(User.class))).thenReturn(userBuilder.uuid(USER_ID).build());
    when(authenticationUtils.getDefaultAccount(any())).thenReturn(account);
    when(wingsPersistence.getWithAppId(UserInvite.class, GLOBAL_APP_ID, USER_INVITE_ID)).thenReturn(userInvite);
    doNothing().when(signupService).checkIfEmailIsValid(any());

    InviteOperationResponse inviteOperationResponse = userService.inviteUser(userInvite, false, true);

    assertThat(inviteOperationResponse).isEqualTo(InviteOperationResponse.USER_INVITED_SUCCESSFULLY);
    verify(signupService, times(1)).sendEmail(any(), anyString(), any());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGenerateCannySsoJwt() {
    String returnToUrl = "https://harness-io.canny.io/admin/settings/sso-redirect?works=true";
    String cannyBaseUrl = "https://canny.io/api/redirects/sso";
    String companyId = "123456789";
    when(configuration.getPortal().getCannyBaseUrl()).thenReturn(cannyBaseUrl);
    when(configuration.getPortal().getJwtCannySecret()).thenReturn("yv7TV4NsP4fps8pLdGuVdV8CHgJT3wCaFgTEg7dKcanzKN7C");

    User user = userBuilder.build();
    UserThreadLocal.set(user);

    CannySsoLoginResponse cannySsoLoginResponse = userService.generateCannySsoJwt(returnToUrl, companyId);

    assertThat(cannySsoLoginResponse.getUserId()).isEqualTo(user.getUuid());

    String redirectUrl = cannySsoLoginResponse.getRedirectUrl();
    String cannyBaseUrlResult = redirectUrl.split("\\?")[0];
    assertThat(cannyBaseUrlResult).isEqualTo(cannyBaseUrl);

    String queryParams = redirectUrl.split("\\?")[1];
    String companyIdResult = queryParams.split("&")[0];
    assertThat(companyIdResult).isEqualTo("companyID=" + companyId);

    String returnToUrlResult = redirectUrl.split("&")[2];
    assertThat(returnToUrlResult).isEqualTo("redirect=" + returnToUrl);
  }

  private void assertDoesNotThrow(Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception ex) {
      log.error("ERROR: ", ex);
      Assert.fail();
    }
  }

  private List<Account> getAccounts() {
    Account account1 = getAccount(AccountStatus.ACTIVE, PAID, "111");
    account1.setAccountName("Test-Account");
    Account account2 = getAccount(AccountStatus.ACTIVE, ESSENTIALS, "222");
    Account account3 = getAccount(AccountStatus.ACTIVE, COMMUNITY, "333");
    return Arrays.asList(account1, account2, account3);
  }

  private List<Account> getSupportAccounts() {
    Account supportAccount1 = getAccount(AccountStatus.ACTIVE, PAID, "444");
    Account supportAccount2 = getAccount(AccountStatus.ACTIVE, PAID, "555");
    return Arrays.asList(supportAccount1, supportAccount2);
  }

  private User initAccountsAndSupportAcccountsForUser() {
    User user = anUser().uuid("userId").accounts(getAccounts()).supportAccounts(getSupportAccounts()).build();
    return user;
  }

  private Account getAccount(String accountStatus, String accountType, String uuid) {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(accountStatus);
    licenseInfo.setAccountType(accountType);
    return anAccount().withLicenseInfo(licenseInfo).withUuid(uuid).build();
  }

  private UserInvite getUserInvite() {
    UserInvite userInvite = anUserInvite()
                                .withAccountName(ACCOUNT_NAME)
                                .withCompanyName(COMPANY_NAME)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .build();
    userInvite.setPassword("password".toCharArray());
    return userInvite;
  }
}
