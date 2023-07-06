/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.HAS;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.ng.core.account.AuthenticationMechanism.USER_PASSWORD;
import static io.harness.ng.core.common.beans.Generation.CG;
import static io.harness.ng.core.common.beans.Generation.NG;
import static io.harness.ng.core.common.beans.UserSource.JIT;
import static io.harness.ng.core.common.beans.UserSource.LDAP;
import static io.harness.ng.core.common.beans.UserSource.MANUAL;
import static io.harness.ng.core.common.beans.UserSource.SCIM;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.FAIL;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_EXPIRED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_INVALID;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_ALREADY_ADDED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_ALREADY_INVITED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_INVITED_SUCCESSFULLY;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_INVITE_NOT_REQUIRED;
import static io.harness.persistence.AccountAccess.ACCOUNT_ID_KEY;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.app.ManagerCacheRegistrar.PRIMARY_CACHE_PREFIX;
import static software.wings.app.ManagerCacheRegistrar.USER_CACHE;
import static software.wings.beans.Account.AccountKeys;
import static software.wings.beans.AccountRole.AccountRoleBuilder.anAccountRole;
import static software.wings.beans.ApplicationRole.ApplicationRoleBuilder.anApplicationRole;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.UserInviteSource.SourceType.SSO;
import static software.wings.security.JWT_CATEGORY.INVITE_SECRET;
import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;
import static software.wings.security.PermissionAttribute.ResourceType.ARTIFACT;
import static software.wings.security.PermissionAttribute.ResourceType.DEPLOYMENT;
import static software.wings.security.PermissionAttribute.ResourceType.ENVIRONMENT;
import static software.wings.security.PermissionAttribute.ResourceType.SERVICE;
import static software.wings.security.PermissionAttribute.ResourceType.WORKFLOW;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Lists.newArrayList;
import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.String.format;
import static java.sql.Date.from;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.security.crypto.bcrypt.BCrypt.checkpw;
import static org.springframework.security.crypto.bcrypt.BCrypt.hashpw;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.LogoutResponse;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.cache.HarnessCacheManager;
import io.harness.cd.CDLicenseType;
import io.harness.data.encoding.EncodingUtils;
import io.harness.eraro.ErrorCode;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.event.handler.impl.segment.SegmentHandler.Keys;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.event.model.EventType;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.user.UserDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidCredentialsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SignupException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.UserAlreadyPresentException;
import io.harness.exception.UserRegistrationException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.invites.remote.InviteAcceptResponse;
import io.harness.invites.remote.NgInviteClient;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.CEModuleLicenseDTO;
import io.harness.licensing.beans.modules.CFModuleLicenseDTO;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SRMModuleLicenseDTO;
import io.harness.licensing.beans.modules.STOModuleLicenseDTO;
import io.harness.licensing.remote.admin.AdminLicenseHttpClient;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.limits.LimitEnforcementUtils;
import io.harness.limits.checker.StaticLimitCheckerWithDecrement;
import io.harness.marketplace.gcp.procurement.GcpProcurementService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.account.OauthProviderType;
import io.harness.ng.core.common.beans.Generation;
import io.harness.ng.core.common.beans.UserSource;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.invites.dto.InviteDTO;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.ng.core.switchaccount.LdapIdentificationInfo;
import io.harness.ng.core.switchaccount.OauthIdentificationInfo;
import io.harness.ng.core.switchaccount.RestrictedSwitchAccountInfo;
import io.harness.ng.core.switchaccount.SamlIdentificationInfo;
import io.harness.ng.core.user.NGRemoveUserFilter;
import io.harness.ng.core.user.PasswordChangeDTO;
import io.harness.ng.core.user.PasswordChangeResponse;
import io.harness.ng.core.user.UserAccountLevelData.UserAccountLevelDataKeys;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.EmailChannel.EmailChannelBuilder;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.UuidAware;
import io.harness.remote.client.NGRestUtils;
import io.harness.sanitizer.HtmlInputSanitizer;
import io.harness.security.dto.UserPrincipal;
import io.harness.serializer.KryoSerializer;
import io.harness.signup.dto.SignupInviteDTO;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;
import io.harness.usermembership.remote.UserMembershipClient;
import io.harness.utils.UserUtils;
import io.harness.version.VersionInfoManager;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.AccountJoinRequest;
import software.wings.beans.AccountRole;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.ApplicationRole;
import software.wings.beans.Base.BaseKeys;
import software.wings.beans.CannySsoLoginResponse;
import software.wings.beans.EmailVerificationToken;
import software.wings.beans.EmailVerificationToken.EmailVerificationTokenKeys;
import software.wings.beans.EntityType;
import software.wings.beans.Event;
import software.wings.beans.Event.Type;
import software.wings.beans.LicenseInfo;
import software.wings.beans.MarketPlace;
import software.wings.beans.Role;
import software.wings.beans.TrialSignupOptions;
import software.wings.beans.TrialSignupOptions.Products;
import software.wings.beans.User;
import software.wings.beans.User.Builder;
import software.wings.beans.User.UserKeys;
import software.wings.beans.UserInvite;
import software.wings.beans.UserInvite.UserInviteKeys;
import software.wings.beans.UserInviteSource;
import software.wings.beans.UserInviteSource.SourceType;
import software.wings.beans.ZendeskSsoLoginResponse;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.beans.loginSettings.PasswordSource;
import software.wings.beans.loginSettings.PasswordStrengthViolations;
import software.wings.beans.loginSettings.UserLockoutInfo;
import software.wings.beans.marketplace.MarketPlaceConstants;
import software.wings.beans.marketplace.MarketPlaceType;
import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.security.AccessRequest;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.HarnessUserGroup;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.beans.utm.UtmInfo;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.licensing.LicenseService;
import software.wings.persistence.mail.EmailData;
import software.wings.resources.UserResource;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.JWT_CATEGORY;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.SecretManager;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.security.authentication.AuthenticationManager;
import software.wings.security.authentication.AuthenticationUtils;
import software.wings.security.authentication.TOTPAuthHandler;
import software.wings.security.authentication.TwoFactorAuthenticationMechanism;
import software.wings.security.authentication.TwoFactorAuthenticationSettings;
import software.wings.security.authentication.oauth.OauthUserInfo;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccessRequestService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.AwsMarketPlaceApiHandler;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.RoleService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.signup.SignupSpamChecker;

import com.amazonaws.services.codedeploy.model.InvalidOperationException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Criteria;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * Created by anubhaw on 3/9/16.
 */
@OwnedBy(PL)
@ValidateOnExecution
@Singleton
@Slf4j
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class UserServiceImpl implements UserService {
  private static final Long TEST_FF_NUMBER_OF_CLIENT_MAUS = 1000000L;
  private static final int TEST_FF_NUMBER_OF_USERS = 50;
  static final String ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME = "add_group";
  static final String USER_PASSWORD_CHANGED_EMAIL_TEMPLATE_NAME = "password_changed";
  private static final String ADD_ACCOUNT_EMAIL_TEMPLATE_NAME = "add_account";
  public static final String SIGNUP_EMAIL_TEMPLATE_NAME = "signup";
  public static final String INVITE_EMAIL_TEMPLATE_NAME = "invite";
  private static final String JOIN_EXISTING_TEAM_TEMPLATE_NAME = "join_existing_team";
  public static final int REGISTRATION_SPAM_THRESHOLD = 3;
  private static final String EXC_MSG_RESET_PASS_LINK_NOT_GEN = "Reset password link could not be generated";
  private static final String EXC_MSG_USER_DOESNT_EXIST = "User does not exist";
  private static final String EXC_MSG_USER_INVITE_INVALID =
      "User was not invited to access account or the invitation is obsolete";
  private static final String EXC_USER_ALREADY_REGISTERED = "User is already registered";
  private static final String INCORRECT_PORTAL_SETUP = "Incorrect portal setup";
  private static final String RESET_ERROR = "Reset password email couldn't be sent";
  private static final String NG_AUTH_UI_INVITE_PATH_FORMAT = "api/users/invites/verify";
  private static final String INVITE_URL_FORMAT = "/invite?email=%s&inviteId=%s";
  private static final String LOGIN_URL_FORMAT = "/login?company=%s&account=%s&email=%s";
  private static final String HARNESS_ISSUER = "Harness Inc";
  private static final int MINIMAL_ORDER_QUANTITY = 1;
  private static final String SYSTEM = "system";
  private static final String SETUP_ACCOUNT_FROM_MARKETPLACE = "Account Setup from Marketplace";
  private static final String NG_AUTH_UI_PATH_PREFIX = "auth/";
  private static final String USER_INVITE = "user_invite";
  private static final String CD = "CD";
  private static final String CI = "CI";
  private static final String FF = "FF";
  private static final String CCM = "CCM";
  private static final String STO = "STO";
  private static final String SRM = "SRM";

  /**
   * The Executor service.
   */
  @Inject ExecutorService executorService;
  @Inject private UserServiceLimitChecker userServiceLimitChecker;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private EmailNotificationService emailNotificationService;
  @Inject private MainConfiguration configuration;
  @Inject private RoleService roleService;
  @Inject private AccountService accountService;
  @Inject private AuthService authService;
  @Inject private UserGroupService userGroupService;
  @Inject private HarnessUserGroupService harnessUserGroupService;
  @Inject private AppService appService;
  @Inject private AuthHandler authHandler;
  @Inject private SecretManager secretManager;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private LimitCheckerFactory limitCheckerFactory;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private AuthenticationManager authenticationManager;
  @Inject private AuthenticationUtils authenticationUtils;
  @Inject private SSOService ssoService;
  @Inject private LoginSettingsService loginSettingsService;
  @Inject private GcpProcurementService gcpProcurementService;
  @Inject private SignupService signupService;
  @Inject private SignupSpamChecker spamChecker;
  @Inject private software.wings.service.impl.AuditServiceHelper auditServiceHelper;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Inject private TOTPAuthHandler totpAuthHandler;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private LicenseService licenseService;
  @Inject private HarnessCacheManager harnessCacheManager;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private ConfigurationController configurationController;
  @Inject private HtmlInputSanitizer userNameSanitizer;
  @Inject @Named("PRIVILEGED") private NgInviteClient ngInviteClient;
  @Inject @Named(EventsFrameworkConstants.ENTITY_CRUD) private Producer eventProducer;
  @Inject private AccessRequestService accessRequestService;
  @Inject private SegmentHelper segmentHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject @Named("PRIVILEGED") private UserMembershipClient userMembershipClient;
  @Inject private TelemetryReporter telemetryReporter;

  @Inject private UserServiceHelper userServiceHelper;

  @Inject private AdminLicenseHttpClient adminLicenseHttpClient;

  @Inject private AwsMarketPlaceApiHandler awsMarketPlaceApiHandler;

  @Inject private NotificationClient notificationClient;

  private final ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(1,
      new ThreadFactoryBuilder().setNameFormat("invite-executor-thread-%d").setPriority(Thread.NORM_PRIORITY).build());

  private Cache<String, User> getUserCache() {
    if (configurationController.isPrimary()) {
      return harnessCacheManager.getCache(PRIMARY_CACHE_PREFIX + USER_CACHE, String.class, User.class,
          AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES));
    }
    return harnessCacheManager.getCache(USER_CACHE, String.class, User.class,
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo());
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#register(software.wings.beans.User)
   */
  @Override
  public User register(User user) {
    if (isNotBlank(user.getEmail())) {
      user.setEmail(user.getEmail().trim().toLowerCase());
    }

    if (isNotBlank(user.getAccountName())) {
      user.setAccountName(user.getAccountName().trim());
    }

    if (isNotBlank(user.getName())) {
      user.setName(user.getName().trim());
    }

    if (isNotBlank(user.getCompanyName())) {
      user.setCompanyName(user.getCompanyName().trim());
    }

    verifyRegisteredOrAllowed(user.getEmail());

    Account account = setupTrialAccount(user.getAccountName(), user.getCompanyName());

    User savedUser = registerNewUser(user, account);
    executorService.execute(() -> sendVerificationEmail(savedUser));
    eventPublishHelper.publishUserInviteFromAccountEvent(account.getUuid(), savedUser.getEmail());
    return savedUser;
  }

  private User createNewUser(User user, String accountId, Generation generation) {
    user.setAppId(GLOBAL_APP_ID);

    List<UserGroup> accountAdminGroups = getAccountAdminGroup(accountId);

    User savedUser = null;
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)) {
      savedUser = createUserWithAccountLevelData(user, accountId, MANUAL, generation);
    } else {
      savedUser = createUser(user, accountId);
    }
    addUserToUserGroups(accountId, user, accountAdminGroups, false, false);

    return savedUser;
  }

  @Override
  public User createNewUserAndSignIn(User user, String accountId, Generation generation) {
    User savedUser = createNewUser(user, accountId, generation);

    return authenticationManager.defaultLoginUsingPasswordHash(savedUser.getEmail(), savedUser.getPasswordHash());
  }

  @Override
  public User createNewOAuthUser(User user, String accountId) {
    User savedUser = createNewUser(user, accountId, NG);

    createSSOSettingsAndMarkAsDefaultAuthMechanism(accountId);

    return savedUser;
  }

  public io.harness.ng.beans.PageResponse<Account> getUserAccountsAndSupportAccounts(
      String userId, int pageIndex, int pageSize, String searchTerm) {
    User user = get(userId);
    loadSupportAccounts(user);
    Account defaultAccount = null;
    List<Account> userAccounts = user.getAccounts();
    for (Account account : userAccounts) {
      if (user.getDefaultAccountId().equals(account.getUuid())) {
        defaultAccount = account;
        break;
      }
    }
    if (defaultAccount != null) {
      userAccounts.remove(defaultAccount);
      userAccounts.add(0, defaultAccount);
    }
    userAccounts.addAll(user.getSupportAccounts());
    if (isNotEmpty(searchTerm)) {
      PageRequest<Account> accountPageRequest = aPageRequest()
                                                    .addFilter(SearchFilter.builder()
                                                                   .fieldName(AccountKeys.accountName)
                                                                   .op(SearchFilter.Operator.CONTAINS)
                                                                   .fieldValues(new String[] {searchTerm})
                                                                   .build())
                                                    .build();
      final List<String> accountIds =
          accountService.getAccounts(accountPageRequest).stream().map(UuidAware::getUuid).collect(toList());
      if (accountIds.size() > 0) {
        userAccounts = userAccounts.stream().filter(p -> accountIds.contains(p.getUuid())).collect(Collectors.toList());
      } else {
        userAccounts.clear();
      }
    }
    List<Account> finalAccounts = userAccounts.subList(
        Math.min(userAccounts.size(), pageIndex), Math.min(userAccounts.size(), pageIndex + pageSize));

    return io.harness.ng.beans.PageResponse.<Account>builder()
        .content(finalAccounts)
        .pageItemCount(finalAccounts.size())
        .pageSize(pageSize)
        .pageIndex(pageIndex)
        .totalItems(userAccounts.size())
        .totalPages((userAccounts.size() + pageSize - 1) / pageSize)
        .build();
  }

  @Override
  public UserInvite createNewSignupInvite(SignupInviteDTO signupInvite) {
    final String emailAddress = signupInvite.getEmail().toLowerCase();
    UserInvite userInviteInDB = signupService.getUserInviteByEmail(emailAddress);

    if (userInviteInDB == null) {
      io.harness.ng.core.user.UtmInfo signupInviteUtmInfo = signupInvite.getUtmInfo();

      UserInvite userInvite = new UserInvite();
      userInvite.setEmail(emailAddress);
      userInvite.setPasswordHash(signupInvite.getPasswordHash());
      userInvite.setIntent(signupInvite.getIntent());
      userInvite.setCreatedFromNG(true);
      userInvite.setSource(UserInviteSource.builder().type(SourceType.TRIAL).build());
      userInvite.setCompleted(false);

      if (signupInviteUtmInfo != null) {
        userInvite.setUtmInfo(UtmInfo.builder()
                                  .utmSource(signupInviteUtmInfo.getUtmSource())
                                  .utmTerm(signupInviteUtmInfo.getUtmTerm())
                                  .utmCampaign(signupInviteUtmInfo.getUtmCampaign())
                                  .utmContent(signupInviteUtmInfo.getUtmContent())
                                  .utmMedium(signupInviteUtmInfo.getUtmMedium())
                                  .build());
      }

      if (signupInvite.getEdition() != null) {
        userInvite.setEdition(signupInvite.getEdition().name());
      }

      if (signupInvite.getBillingFrequency() != null) {
        userInvite.setBillingFrequency(signupInvite.getBillingFrequency().name());
      }

      if (signupInvite.getSignupAction() != null) {
        userInvite.setSignupAction(signupInvite.getSignupAction().name());
      }

      String inviteId = wingsPersistence.save(userInvite);
      userInvite.setUuid(inviteId);

      log.info("Created a new ng signup user invite for {}", emailAddress);

      return userInvite;
    } else if (userInviteInDB.isCompleted()) {
      if (spamChecker.isSpam(userInviteInDB)) {
        throw new InvalidRequestException("User already finished registration.");
      }
    }

    if (spamChecker.isSpam(userInviteInDB)) {
      throw new InvalidRequestException("Spam signup request");
    }

    throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
  }

  @Override
  public List<Account> getUserAccounts(String userId, int pageIndex, int pageSize, String searchTerm) {
    Query<Account> query = getUserAccountsQuery(userId, searchTerm);
    return query.asList(new FindOptions().limit(pageSize).skip(pageIndex));
  }

  private Query<Account> getUserAccountsQuery(String userId, String searchTerm) {
    Query<Account> query = wingsPersistence.createQuery(Account.class);
    List<String> accountIds = getUserAccountIds(userId);
    if (harnessUserGroupService.isHarnessSupportUser(userId)) {
      accountIds.addAll(accessRequestService.getAccountsHavingActiveAccessRequestForUser(userId));
      query.or(query.criteria(AccountKeys.isHarnessSupportAccessAllowed).equal(true),
          query.criteria(AccountKeys.uuid).in(accountIds));

    } else {
      query.field(AccountKeys.uuid).in(accountIds);
    }
    query.and(getSearchCriterion(query, AccountKeys.accountName, searchTerm));
    query.order(Sort.ascending(AccountKeys.accountName));
    return query;
  }

  public List<String> getUserAccountIds(String userId) {
    User user = wingsPersistence.createQuery(User.class).filter("uuid", userId).project(UserKeys.accounts, true).get();
    return user.getAccounts().stream().map(Account::getUuid).collect(toList());
  }

  @Override
  public User completeNewSignupInvite(UserInvite userInvite) {
    User existingUser = getUserByEmail(userInvite.getEmail());
    if (existingUser != null) {
      throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
    }

    // create account
    String username = userInvite.getEmail().split("@")[0];
    Account account = Account.Builder.anAccount()
                          .withAccountName(username)
                          .withCompanyName(username)
                          .withDefaultExperience(DefaultExperience.NG)
                          .withCreatedFromNG(true)
                          .withIsProductLed(true)
                          .withAppId(GLOBAL_APP_ID)
                          .build();
    account.setLicenseInfo(LicenseInfo.builder()
                               .accountType(AccountType.TRIAL)
                               .accountStatus(AccountStatus.ACTIVE)
                               .licenseUnits(50)
                               .build());

    Account createdAccount = accountService.save(account, false);

    // create user
    User user = User.Builder.anUser()
                    .email(userInvite.getEmail())
                    .name(createdAccount.getAccountName())
                    .passwordHash(userInvite.getPasswordHash())
                    .accountName(createdAccount.getAccountName())
                    .companyName(createdAccount.getCompanyName())
                    .accounts(Lists.newArrayList(createdAccount))
                    .emailVerified(true)
                    .defaultAccountId(createdAccount.getUuid())
                    .utmInfo(userInvite.getUtmInfo())
                    .build();
    completeUserInviteForSignup(userInvite, createdAccount.getUuid());
    return createNewUserAndSignIn(user, createdAccount.getUuid(), NG);
  }

  @Override
  public User completeCommunitySignup(UserInvite userInvite) {
    User existingUser = getUserByEmail(userInvite.getEmail());
    if (existingUser != null) {
      throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
    }

    // create account
    String username = userInvite.getEmail().split("@")[0];
    Account account = Account.Builder.anAccount()
                          .withAccountName(username)
                          .withCompanyName(username)
                          .withDefaultExperience(DefaultExperience.NG)
                          .withCreatedFromNG(true)
                          .withAppId(GLOBAL_APP_ID)
                          .build();
    account.setLicenseInfo(LicenseInfo.builder()
                               .accountType(AccountType.COMMUNITY)
                               .accountStatus(AccountStatus.ACTIVE)
                               .licenseUnits(50)
                               .build());

    Account createdAccount = accountService.save(account, false, false);

    // create user
    User user = User.Builder.anUser()
                    .email(userInvite.getEmail())
                    .name(createdAccount.getAccountName())
                    .passwordHash(userInvite.getPasswordHash())
                    .accountName(createdAccount.getAccountName())
                    .companyName(createdAccount.getCompanyName())
                    .accounts(Lists.newArrayList(createdAccount))
                    .emailVerified(true)
                    .defaultAccountId(createdAccount.getUuid())
                    .utmInfo(userInvite.getUtmInfo())
                    .build();
    completeUserInviteForSignup(userInvite, createdAccount.getUuid());
    return createNewUserAndSignIn(user, createdAccount.getUuid(), NG);
  }

  @Override
  public UserInvite createUserInviteForMarketPlace() {
    UserInvite userInvite = new UserInvite();
    userInvite.setSource(UserInviteSource.builder().type(SourceType.MARKETPLACE).build());
    userInvite.setCompleted(false);

    String inviteId = wingsPersistence.save(userInvite);
    userInvite.setUuid(inviteId);

    log.info("Created a new user invite {} for a signup request from market place", inviteId);

    return userInvite;
  }

  /*
   * This function validates the account Name give in the userInvite is correct
   *
   */
  @VisibleForTesting
  void validateAccountName(String companyName, String accountName) {
    Account account = Account.Builder.anAccount().withCompanyName(companyName).withAccountName(accountName).build();
    accountService.validateAccount(account);
  }

  @Override
  public boolean hasPermission(String accountId, PermissionType permissionType) {
    User user = UserThreadLocal.get();
    if (user == null) {
      return true;
    }
    return userHasPermission(accountId, user, permissionType);
  }

  @Override
  public boolean userHasPermission(String accountId, User user, PermissionType permissionType) {
    UserRequestContext userRequestContext = user.getUserRequestContext();
    if (userRequestContext == null) {
      return true;
    }
    if (!accountId.equals(userRequestContext.getAccountId())) {
      return false;
    }

    UserPermissionInfo userPermissionInfo = userRequestContext.getUserPermissionInfo();
    return userPermissionInfo.hasAccountPermission(permissionType);
  }

  /**
   * Trial/Freemium user invitation won't create account. The freemium account will be created only at time of
   * invitation completion.
   */
  @Override
  public boolean trialSignup(UserInvite userInvite) {
    final String emailAddress = userInvite.getEmail().toLowerCase();
    validateTrialSignup(emailAddress);
    validateName(userInvite.getName());
    signupService.validatePassword(userInvite.getPassword());

    UserInvite userInviteInDB = signupService.getUserInviteByEmail(emailAddress);

    Map<String, String> params = new HashMap<>();
    params.put("email", userInvite.getEmail());
    String url = "/activation.html";

    if (userInviteInDB == null) {
      userInvite.setSource(UserInviteSource.builder().type(SourceType.TRIAL).build());
      userInvite.setCompleted(false);
      String hashed = hashpw(new String(userInvite.getPassword()), BCrypt.gensalt());
      userInvite.setPasswordHash(hashed);

      // validate that the account name or the company name is correct
      validateAccountName(userInvite.getCompanyName(), userInvite.getCompanyName());

      String inviteId = wingsPersistence.save(userInvite);
      userInvite.setUuid(inviteId);
      params.put("userInviteId", inviteId);

      log.info("Created a new user invite {} for company {}", inviteId, userInvite.getCompanyName());

      // Send an email invitation for the trial user to finish up the sign-up with additional information
      // such as password, account/company name information.
      sendVerificationEmail(userInvite, url, params);
      eventPublishHelper.publishTrialUserSignupEvent(inviteId, emailAddress, userInvite);
    } else if (userInviteInDB.isCompleted()) {
      if (spamChecker.isSpam(userInviteInDB)) {
        return false;
      }
      // HAR-7590: If user invite has completed. Send an email saying so and ask the user to login directly.
      signupService.sendTrialSignupCompletedEmail(userInviteInDB);
    } else {
      if (spamChecker.isSpam(userInviteInDB)) {
        return false;
      }

      params.put("userInviteId", userInviteInDB.getUuid());
      // HAR-7250: If the user invite was not completed. Resend the verification/invitation email.
      sendVerificationEmail(userInviteInDB, url, params);
    }
    return true;
  }

  @Override
  public boolean accountJoinRequest(AccountJoinRequest accountJoinRequest) {
    final String emailAddress = accountJoinRequest.getEmail().toLowerCase();
    validateName(accountJoinRequest.getName());
    signupService.validateEmail(emailAddress);
    Map<String, String> params = new HashMap<>();
    params.put("email", emailAddress);
    params.put("name", sanitizeUserName(accountJoinRequest.getName()));
    params.put("url", "https://app.harness.io/#/login");
    params.put("companyName", accountJoinRequest.getCompanyName());
    params.put("note", accountJoinRequest.getNote());
    String accountAdminEmail = accountJoinRequest.getAccountAdminEmail();
    boolean hasAccountAdminEmail = isNotEmpty(accountAdminEmail);
    User user = hasAccountAdminEmail ? getUserByEmail(accountAdminEmail) : null;
    String supportEmail = configuration.getSupportEmail();
    String to = supportEmail;
    String msg = "";
    if (user != null) {
      boolean isValidUser = hasAccountAdminEmail && isUserVerified(user);
      boolean isAdmin = isValidUser && isUserAdminOfAnyAccount(user);

      if (isAdmin) {
        to = accountAdminEmail;
      } else if (isValidUser) {
        to = supportEmail;
        msg = "Recipient is not a Harness admin in production cluster - ";
      } else {
        to = supportEmail;
        msg = "Recipient is not a Harness user in production cluster - ";
      }
    } else {
      msg = "Recipient is not a Harness user in production cluster - ";
    }

    params.put("msg", msg);
    boolean emailSent = sendEmail(to, JOIN_EXISTING_TEAM_TEMPLATE_NAME, params);
    eventPublishHelper.publishJoinAccountEvent(
        emailAddress, accountJoinRequest.getName(), accountJoinRequest.getCompanyName());
    return emailSent;
  }

  @Override
  public boolean postCustomEvent(String accountId, String event) {
    eventPublishHelper.publishCustomEvent(accountId, event);
    return true;
  }

  private void validateTrialSignup(String email) {
    signupService.validateCluster();
    signupService.validateEmail(email);
  }

  @Override
  public Account addAccount(Account account, User user, boolean addUser) {
    LicenseInfo licenseInfo = account.getLicenseInfo();
    if (!configuration.isTrialRegistrationAllowed()) {
      if (licenseInfo != null && (AccountType.TRIAL.equals(licenseInfo.getAccountType()))) {
        throw new InvalidRequestException("Cannot create a trial account in this cluster.");
      }
    }
    if (isNotBlank(account.getAccountName())) {
      account.setAccountName(account.getAccountName().trim());
    }

    if (isNotBlank(account.getCompanyName())) {
      account.setCompanyName(account.getCompanyName().trim());
    }

    if (isNotBlank(account.getRingName())) {
      account.setRingName(account.getRingName().trim());
    }

    if (licenseInfo != null && AccountType.TRIAL.equals(licenseInfo.getAccountType())
        && account.getTrialSignupOptions() == null) {
      account.setTrialSignupOptions(TrialSignupOptions.getDefaultTrialSignupOptions());
    }

    account = setupAccount(account);
    if (addUser) {
      addAccountAdminRole(user, account);
      authHandler.addUserToDefaultAccountAdminUserGroup(user, account, true);
      sendSuccessfullyAddedToNewAccountEmail(user, account);
      evictUserFromCache(user.getUuid());
    }
    return account;
  }

  @Override
  public String getUserInvitationId(String email) {
    if (isNotEmpty(email)) {
      List<UserInvite> userInviteList = wingsPersistence.createQuery(UserInvite.class)
                                            .filter(UserInviteKeys.email, email)
                                            .order(Sort.descending("createdAt"))
                                            .limit(1)
                                            .asList();
      if (isNotEmpty(userInviteList)) {
        return userInviteList.get(0).getUuid();
      }
    }
    return "";
  }

  @Override
  public User getUserSummary(User user) {
    if (user == null) {
      return null;
    }
    User userSummary = new User();
    userSummary.setName(user.getName());
    userSummary.setUuid(user.getUuid());
    userSummary.setEmail(user.getEmail());
    userSummary.setEmailVerified(user.isEmailVerified());
    userSummary.setTwoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled());
    userSummary.setUserLocked(user.isUserLocked());
    userSummary.setPasswordExpired(user.isPasswordExpired());
    userSummary.setImported(user.isImported());
    userSummary.setDisabled(user.isDisabled());
    userSummary.setExternalUserId(user.getExternalUserId());
    return userSummary;
  }

  @Override
  public List<User> getUserSummary(List<User> userList) {
    if (isEmpty(userList)) {
      return Collections.emptyList();
    }
    return userList.stream().map(this::getUserSummary).collect(toList());
  }

  private void sendSuccessfullyAddedToNewAccountEmail(User user, Account account) {
    try {
      String loginUrl = buildAbsoluteUrl(
          format(LOGIN_URL_FORMAT, account.getCompanyName(), account.getAccountName(), user.getEmail()),
          account.getUuid());

      Map<String, String> templateModel = getTemplateModel(user.getName(), loginUrl);
      templateModel.put("company", account.getCompanyName());
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName(ADD_ACCOUNT_EMAIL_TEMPLATE_NAME)
                                .templateModel(templateModel)
                                .accountId(account.getUuid())
                                .system(false)
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);
      emailNotificationService.send(emailData);
    } catch (URISyntaxException use) {
      log.error("Add account email couldn't be sent for accountId={}", account.getUuid(), use);
    }
  }

  public String sanitizeUserName(String name) {
    return userNameSanitizer.sanitizeInput(name);
  }

  @Override
  public void addUserToAccount(String userId, String accountId) {
    UserSource userSource = MANUAL;
    User user = get(userId);
    if (user != null && userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, accountId, NG)) {
      userSource = user.getUserAccountLevelDataMap().get(accountId).getSourceOfProvisioning().get(NG);
    }
    addUserToAccount(userId, accountId, userSource);
  }
  @Override
  public void addUserToAccount(String userId, String accountId, UserSource userSource) {
    Account account = accountService.get(accountId);
    if (account == null) {
      throw new InvalidRequestException("No account exists with id " + accountId);
    }
    User user = get(userId);
    if (user == null) {
      throw new InvalidRequestException("No user exists with id " + userId);
    }
    boolean shouldUpdateUserAccountLevelData = false;
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    if (user.getAccountIds().contains(account.getUuid())) {
      if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)
          && userServiceHelper.validationForUserAccountLevelDataFlow(user, accountId)) {
        shouldUpdateUserAccountLevelData = true;
      } else {
        return;
      }
    }

    List<Account> newAccounts = user.getAccounts();
    if (!shouldUpdateUserAccountLevelData) {
      newAccounts.add(account);
      updateOperations.set(UserKeys.accounts, newAccounts);
    }

    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)) {
      userServiceHelper.populateAccountToUserMapping(user, accountId, NG, userSource);
      updateOperations.set(UserKeys.userAccountLevelDataMap, user.getUserAccountLevelDataMap());
    }

    updateUser(user.getUuid(), updateOperations);
  }

  @Override
  public void setUserEmailVerified(String userId) {
    wingsPersistence.updateFields(User.class, userId, ImmutableMap.of("emailVerified", true));
  }

  @Override
  public boolean isUserPasswordPresent(String accountId, String emailId) {
    User user = getUserByEmail(emailId);
    return isNotEmpty(user.getPasswordHash());
  }

  @Override
  public URI getInviteAcceptRedirectURL(InviteOperationResponse inviteResponse, UserInvite userInvite, String jwtToken)
      throws URISyntaxException {
    String accountId = userInvite.getAccountId();
    log.info("Getting redirect url for {} - {}", accountId, inviteResponse.getType());
    if (ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD.equals(inviteResponse)) {
      return getUserInfoSubmitUrl(accountId, userInvite.getEmail(), jwtToken);
    } else if (FAIL.equals(inviteResponse)) {
      return getLoginPageUrl(accountId, "");
    } else if (INVITE_EXPIRED.equals(inviteResponse) || INVITE_INVALID.equals(inviteResponse)) {
      return getLoginPageUrl(accountId, inviteResponse.getType());
    } else {
      return getCGDashboardUrl(accountId);
    }
  }

  @Override
  public RestrictedSwitchAccountInfo getSwitchAccountInfo(String accountId, String userId) {
    Account account = accountService.get(accountId);
    RestrictedSwitchAccountInfo.Builder builder =
        RestrictedSwitchAccountInfo.builder()
            .skipReAuthentication(
                featureFlagService.isEnabled(FeatureName.SKIP_SWITCH_ACCOUNT_REAUTHENTICATION, accountId))
            .isHarnessSupportGroupUser(harnessUserGroupService.isHarnessSupportUser(userId))
            .whitelistedDomains(accountService.getWhitelistedDomains(accountId))
            .authenticationMechanism(account.getAuthenticationMechanism())
            .isTwoFactorAuthEnabledForAccount(accountService.getTwoFactorEnforceInfo(accountId));

    LdapSettings ldapSettings = ssoService.getLdapSettings(accountId);
    if (ldapSettings != null) {
      builder.ldapIdentificationInfo(LdapIdentificationInfo.builder()
                                         .host(ldapSettings.getConnectionSettings().getHost())
                                         .port(ldapSettings.getConnectionSettings().getPort())
                                         .build());
    }

    SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
    if (samlSettings != null) {
      builder.samlIdentificationInfo(SamlIdentificationInfo.builder()
                                         .origin(samlSettings.getOrigin())
                                         .metaDataFile(samlSettings.getMetaDataFile())
                                         .build());
    }

    OauthSettings oauthSettings = ssoSettingService.getOauthSettingsByAccountId(accountId);
    if (oauthSettings != null) {
      builder.oauthIdentificationInfo(
          OauthIdentificationInfo.builder().providers(oauthSettings.getAllowedProviders()).build());
    }

    return builder.build();
  }

  private URI getUserInfoSubmitUrl(String accountId, String email, String jwtToken) throws URISyntaxException {
    String encodedEmail = email;
    try {
      encodedEmail = URLEncoder.encode(email, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      log.error("Unable to encode email for userInfoSubmitUrl, using the invite one instead", e);
    }
    String accountCreationFragment =
        String.format("accountIdentifier=%s&email=%s&token=%s&generation=CG", accountId, encodedEmail, jwtToken);
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
    uriBuilder.setFragment("/accept-invite?" + accountCreationFragment);
    return uriBuilder.build();
  }

  private URI getLoginPageUrl(String accountId, String errorCode) throws URISyntaxException {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
    if (isNotEmpty(errorCode)) {
      uriBuilder.setFragment("/signin?errorCode=" + errorCode);
    }
    return uriBuilder.build();
  }

  private URI getCGDashboardUrl(String accountId) throws URISyntaxException {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setFragment(String.format("/account/%s/dashboard", accountId));
    return uriBuilder.build();
  }

  @Override
  public User registerNewUser(User user, Account account) {
    String accountId = account.getUuid();

    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new io.harness.limits.Action(accountId, ActionType.CREATE_USER));

    User existingUser = getUserByEmail(user.getEmail());
    if (existingUser == null) {
      return LimitEnforcementUtils.withLimitCheck(checker, () -> {
        user.setAppId(GLOBAL_APP_ID);
        user.getAccounts().add(account);
        user.setEmailVerified(false);
        String hashed = hashpw(new String(user.getPassword()), BCrypt.gensalt());
        user.setPasswordHash(hashed);
        user.setPasswordChangedAt(System.currentTimeMillis());
        user.setRoles(newArrayList(roleService.getAccountAdminRole(account.getUuid())));
        if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)) {
          return createUserWithAccountLevelData(user, accountId, MANUAL, CG);
        } else {
          return createUser(user, accountId);
        }
      });

    } else {
      UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
      if (isNotEmpty(user.getName())) {
        validateName(user.getName());
        updateOperations.set(UserKeys.name, user.getName());
      }
      updateOperations.set(UserKeys.passwordHash, hashpw(new String(user.getPassword()), BCrypt.gensalt()));
      if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)
          && userServiceHelper.validationForUserAccountLevelDataFlow(user, accountId)) {
        userServiceHelper.populateAccountToUserMapping(user, accountId, NG, MANUAL);
        updateOperations.set(UserKeys.userAccountLevelDataMap, user.getUserAccountLevelDataMap());
      }
      updateUser(existingUser.getUuid(), updateOperations);
      return existingUser;
    }
  }

  @Override
  public User getUserByEmail(String email) {
    User user = null;
    if (isNotEmpty(email)) {
      user = wingsPersistence.createQuery(User.class).filter(UserKeys.email, email.trim().toLowerCase()).get();
      if (user != null && isEmpty(user.getAccounts())) {
        user.setAccounts(newArrayList());
      }
      if (user != null && isEmpty(user.getPendingAccounts())) {
        user.setPendingAccounts(newArrayList());
      }
    }

    return user;
  }

  @Override
  public User getUserByUserId(String accountId, String userId) {
    User user = null;
    if (isNotEmpty(userId) && isNotEmpty(accountId)) {
      user = wingsPersistence.createQuery(User.class)
                 .filter(UserKeys.externalUserId, userId)
                 .field(UserKeys.accounts)
                 .hasThisOne(accountId)
                 .get();
      if (user != null && isEmpty(user.getAccounts())) {
        user.setAccounts(newArrayList());
      }
      if (user != null && isEmpty(user.getPendingAccounts())) {
        user.setPendingAccounts(newArrayList());
      }
    }
    return user;
  }

  @Override
  public List<User> getUsersByEmail(List<String> emailIds, String accountId) {
    Query<User> query = wingsPersistence.createQuery(User.class).field(UserKeys.email).in(emailIds);
    query.or(query.criteria(UserKeys.accounts).hasThisOne(accountId),
        query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    return query.asList();
  }

  @Override
  public User getUserByEmail(String email, String accountId) {
    User user = null;
    if (isNotEmpty(email)) {
      Query<User> query = wingsPersistence.createQuery(User.class).filter(UserKeys.email, email.trim().toLowerCase());
      query.or(query.criteria(UserKeys.accounts).hasThisOne(accountId),
          query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
      user = query.get();
    }
    return user;
  }

  @Override
  public List<User> getUsersEmails(String accountId) {
    Query<User> query = wingsPersistence.createQuery(User.class);
    query.project(UserKeys.email, true).limit(NO_LIMIT).criteria(UserKeys.accounts).hasThisOne(accountId);

    return query.asList();
  }

  @Override
  public User getUserWithAcceptedInviteByEmail(String email, String accountId) {
    User user = null;
    if (isNotEmpty(email)) {
      Query<User> query = wingsPersistence.createQuery(User.class).filter(UserKeys.email, email.trim().toLowerCase());
      query.criteria(UserKeys.accounts).hasThisOne(accountId);
      user = query.get();
    }
    return user;
  }

  @Override
  public UserInvite getUserInviteByEmailAndAccount(String email, String accountId) {
    return getUserInviteByEmailAndAccount(email, accountId, true);
  }

  private UserInvite getUserInviteByEmailAndAccount(String email, String accountId, boolean fetchPendingInvitesOnly) {
    UserInvite userInvite = null;
    if (isNotEmpty(email)) {
      Query<UserInvite> query = wingsPersistence.createQuery(UserInvite.class)
                                    .filter(UserInviteKeys.email, email)
                                    .filter(UserInviteKeys.accountId, accountId);

      if (!fetchPendingInvitesOnly) {
        query.filter(UserInviteKeys.completed, Boolean.FALSE);
      }

      userInvite = query.get();
    }
    return userInvite;
  }

  private void loadUserGroups(String accountId, User user) {
    List<UserGroup> userGroupList = getUserGroupsOfUser(accountId, user.getUuid(), false);
    user.setUserGroups(userGroupList);
  }

  private Map<String, String> getUserInvitationToSsoTemplateModel(Account account, User user)
      throws URISyntaxException {
    String loginUrl = configuration.isNgAuthUIEnabled()
        ? getLoginPageUrl(account.getUuid(), "").toString()
        : buildAbsoluteUrl(
            format(LOGIN_URL_FORMAT, account.getCompanyName(), account.getAccountName(), user.getEmail()),
            account.getUuid());
    Map<String, String> model = getTemplateModel(user.getName(), loginUrl);
    model.put("company", account.getCompanyName());
    model.put("subject", "You have been added to the account " + account.getAccountName() + " on Harness");
    model.put("email", user.getEmail());
    model.put("name", user.getEmail());
    model.put("authenticationMechanism", account.getAuthenticationMechanism().getType());
    model.put("message", "You have been added to the account " + account.getAccountName() + " on Harness Platform.");

    boolean shouldMailContainTwoFactorInfo = user.isTwoFactorAuthenticationEnabled();
    model.put("shouldMailContainTwoFactorInfo", Boolean.toString(shouldMailContainTwoFactorInfo));

    log.info("The shouldMailContainTwoFactorInfo for userId {} and accountId {} is {}", user.getUuid(),
        account.getUuid(), shouldMailContainTwoFactorInfo);

    model.put("totpSecret", user.getTotpSecretKey());
    String otpUrl = totpAuthHandler.generateOtpUrl(account.getCompanyName(), user.getEmail(), user.getTotpSecretKey());
    model.put("totpUrl", otpUrl);
    log.info("The totpUrl for userId {} and accountId {} is {}", user.getUuid(), account.getUuid(), otpUrl);
    return model;
  }

  private SSOSettings getSSOSettings(Account account) {
    AuthenticationMechanism authenticationMechanism = account.getAuthenticationMechanism();
    switch (authenticationMechanism) {
      case OAUTH: {
        return ssoSettingService.getOauthSettingsByAccountId(account.getUuid());
      }
      case SAML: {
        return ssoSettingService.getSamlSettingsByAccountId(account.getUuid());
      }
      case LDAP: {
        return ssoSettingService.getLdapSettingsByAccountId(account.getUuid());
      }
      default: {
        log.error("New authentication mechanism detected. Needs to handle the added role email template flow.");
        throw new GeneralException(
            "New authentication mechanism detected while getting SSOSettings for account=" + account.getUuid());
      }
    }
  }

  private Map<String, String> getTemplateModel(String userName, String url) {
    Map<String, String> templateModel = new HashMap<>();
    templateModel.put("name", sanitizeUserName(userName));
    templateModel.put("url", url);
    return templateModel;
  }

  private void sendVerificationEmail(User user) {
    EmailVerificationToken emailVerificationToken =
        wingsPersistence.saveAndGet(EmailVerificationToken.class, new EmailVerificationToken(user.getUuid()));
    try {
      String verificationUrl =
          buildAbsoluteUrl(configuration.getPortal().getVerificationUrl() + "/" + emailVerificationToken.getToken(),
              user.getDefaultAccountId());
      Map<String, String> templateModel = getTemplateModel(user.getName(), verificationUrl);
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName(SIGNUP_EMAIL_TEMPLATE_NAME)
                                .templateModel(templateModel)
                                .accountId(getPrimaryAccount(user).getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error("Verification email couldn't be sent", e);
    }
  }

  private Account getPrimaryAccount(User user) {
    return user.getAccounts().get(0);
  }

  private String buildAbsoluteUrl(String fragment, String accountId) throws URISyntaxException {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setFragment(fragment);
    return uriBuilder.toString();
  }

  private String buildAbsoluteUrl(String path, String fragment, String accountId) throws URISyntaxException {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(path);
    uriBuilder.setFragment(fragment);
    return uriBuilder.toString();
  }

  private String buildAbsoluteUrl(String path, List<NameValuePair> params, String accountId) throws URISyntaxException {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(path);
    uriBuilder.setParameters(params);
    return uriBuilder.toString();
  }

  @Override
  public void verifyRegisteredOrAllowed(String email) {
    signupService.checkIfEmailIsValid(email);

    final String emailAddress = email.trim();
    User existingUser = getUserByEmail(emailAddress);
    if (existingUser != null && existingUser.isEmailVerified()) {
      throw new UserRegistrationException("User Already Registered", ErrorCode.USER_ALREADY_REGISTERED, USER);
    }
  }

  @Override
  public boolean resendVerificationEmail(String email) {
    User existingUser = getUserByEmail(email);
    if (existingUser == null) {
      throw new UserRegistrationException(EXC_MSG_USER_DOESNT_EXIST, ErrorCode.USER_DOES_NOT_EXIST, USER);
    }

    sendVerificationEmail(existingUser);
    return true;
  }

  @Override
  public boolean resendInvitationEmail(String accountId, String email) {
    log.info("Initiating resending invitation email for user invite for: {}", email);
    UserInvite savedUserInvite = getUserInviteByEmailAndAccount(email, accountId, true);
    if (savedUserInvite == null) {
      log.info("Resending invitation email failed. User invite for: {} does not exist.", email);
      throw new InvalidOperationException("UserInvite not found.");
    }
    User user = getUserByEmail(email);
    if (user == null) {
      throw new InvalidRequestException("user does not exist");
    }
    Account account = accountService.get(accountId);
    user = checkIfTwoFactorAuthenticationIsEnabledForAccount(user, account);
    sendNewInvitationMail(savedUserInvite, account, user);
    log.info("Resent invitation email for user: {}", email);
    return true;
  }

  @Override
  public boolean verifyToken(String emailToken) {
    EmailVerificationToken verificationToken = wingsPersistence.createQuery(EmailVerificationToken.class)
                                                   .filter("appId", GLOBAL_APP_ID)
                                                   .filter(EmailVerificationTokenKeys.token, emailToken)
                                                   .get();

    if (verificationToken == null) {
      throw new GeneralException("Email verification token is not found");
    }
    wingsPersistence.updateFields(User.class, verificationToken.getUserId(), ImmutableMap.of("emailVerified", true));
    wingsPersistence.delete(EmailVerificationToken.class, verificationToken.getUuid());
    return true;
  }

  @Override
  public List<InviteOperationResponse> inviteUsers(UserInvite userInvite) {
    String accountId = userInvite.getAccountId();

    List<InviteOperationResponse> inviteOperationResponses = new ArrayList<>();
    if (userInvite.getEmails().isEmpty()) {
      String message = "No email provided. Please provide vaild email info";
      throw new InvalidArgumentsException(message);
    }
    inviteUserToAccount(accountId, userInvite, inviteOperationResponses);

    List<String> alreadyAddedUsers = new ArrayList<>();
    List<String> alreadyInvitedUsers = new ArrayList<>();
    IntStream.range(0, inviteOperationResponses.size()).forEach(i -> {
      InviteOperationResponse response = inviteOperationResponses.get(i);
      if (response == USER_ALREADY_ADDED) {
        alreadyAddedUsers.add(userInvite.getEmails().get(i));
      }
      if (response == USER_ALREADY_INVITED) {
        alreadyInvitedUsers.add(userInvite.getEmails().get(i));
      }
    });

    String message = "";
    if (isNotEmpty(alreadyAddedUsers)) {
      message += "User(s) with email: " + String.join(", ", alreadyAddedUsers)
          + " are already part of the account. Please change their user groups individually on user's page."
          + "\n";
    }
    if (isNotEmpty(alreadyInvitedUsers)) {
      message += "User(s) with email: " + String.join(", ", alreadyInvitedUsers)
          + " are already invited. Their user groups have been updated.";
    }
    if (!message.equals("")) {
      throw new UserAlreadyPresentException(message);
    }

    return inviteOperationResponses;
  }

  private void inviteUserToAccount(
      String accountId, UserInvite userInvite, List<InviteOperationResponse> inviteOperationResponses) {
    boolean autoInviteAcceptanceEnabled = accountService.isAutoInviteAcceptanceEnabled(accountId);

    for (String email : userInvite.getEmails()) {
      UserInvite userInviteClone = kryoSerializer.clone(userInvite);
      userInviteClone.setEmail(email.trim().toLowerCase());
      inviteOperationResponses.add(
          inviteUser(userInviteClone, !autoInviteAcceptanceEnabled, autoInviteAcceptanceEnabled));
    }
  }

  @Override
  public boolean checkIfUserLimitHasReached(String accountId, String email) {
    try {
      limitCheck(accountId, email);
      return false;
    } catch (WingsException e) {
      log.error("Exception while checking user limit for account {}", accountId, e);
      return true;
    }
  }

  private void limitCheck(String accountId, String email) {
    try {
      Account account = accountService.get(accountId);
      if (null == account) {
        log.error("No account found for accountId={}", accountId);
        return;
      }
      Query<User> query = getListUserQuery(accountId, true, false);
      List<User> existingUsersAndInvites = query.asList();
      userServiceLimitChecker.limitCheck(accountId, existingUsersAndInvites, new HashSet<>(Arrays.asList(email)));
    } catch (WingsException e) {
      log.error("The user limit has been reached for account {} and email {}", accountId, email);
      throw e;
    } catch (Exception e) {
      // catching this because we don't want to stop user invites due to failure in limit check
      log.error("Error while checking limits. accountId={}", accountId, e);
    }
  }

  private void updateEmailOfUser(User user, String newEmail) {
    if (user != null && isNotEmpty(user.getUuid())) {
      UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
      setUnset(updateOperations, UserKeys.email, newEmail);
      Query<User> query = wingsPersistence.createQuery(User.class).filter("_id", user.getUuid());
      wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
    }
  }

  @Override
  public InviteOperationResponse inviteUser(
      UserInvite userInvite, boolean isInviteAcceptanceRequired, boolean markEmailVerified) {
    log.info("Inviting user {} with isInviteAcceptanceRequired {} and markEmailVerified {}", userInvite.getEmail(),
        isInviteAcceptanceRequired, markEmailVerified);

    signupService.checkIfEmailIsValid(userInvite.getEmail());

    log.info("LDAPIterator: email {} of account {} is valid", userInvite.getEmail(), userInvite.getAccountId());
    String accountId = userInvite.getAccountId();
    limitCheck(accountId, userInvite.getEmail());

    Account account = accountService.get(accountId);

    User user = getUserByEmail(userInvite.getEmail());
    if (user == null && featureFlagService.isEnabled(FeatureName.LDAP_SYNC_WITH_USERID, accountId)) {
      user = getUserByUserId(account.getUuid(), userInvite.getExternalUserId());
    }

    boolean createNewUser = user == null;
    if (createNewUser) {
      user = anUser().build();
    }

    String incomingEmail = userInvite.getEmail();
    if (featureFlagService.isEnabled(FeatureName.LDAP_USER_ID_SYNC, accountId) && isNotEmpty(incomingEmail)
        && isNotEmpty(user.getEmail()) && !incomingEmail.equals(user.getEmail())) {
      String incomingEmailInLower = incomingEmail.trim().toLowerCase();
      log.info("Updating email Id for user {} with current mail {} and new email {}", user.getUuid(), user.getEmail(),
          incomingEmailInLower);
      updateEmailOfUser(user, incomingEmailInLower);
      user.setEmail(incomingEmailInLower);
    }

    List<UserGroup> userGroups = userGroupService.getUserGroupsFromUserInvite(userInvite);
    boolean isPLNoEmailForSamlAccountInvitesEnabled = accountService.isPLNoEmailForSamlAccountInvitesEnabled(accountId);
    if (isUserAssignedToAccountInGeneration(user, accountId, CG)) {
      updateUserGroupsOfUser(user.getUuid(), userGroups, accountId, true);
      return USER_ALREADY_ADDED;
    } else if (isUserInvitedToAccount(user, accountId)) {
      if (isInviteAcceptanceRequired) {
        updateUserInvite(userInvite);
        return USER_ALREADY_INVITED;
      }
      user.getPendingAccounts().remove(account);
      user.getAccounts().add(account);
    } else {
      userInvite.setUuid(wingsPersistence.save(userInvite));
      if (isUserAssignedToAccount(user, accountId)) {
        isInviteAcceptanceRequired = false;
      }
      if (isInviteAcceptanceRequired && !isPLNoEmailForSamlAccountInvitesEnabled) {
        user.getPendingAccounts().add(account);
      } else {
        user.getAccounts().add(account);
      }
    }

    user.setEmail(userInvite.getEmail().trim().toLowerCase());
    user.setName(userInvite.getName().trim());
    user.setGivenName(userInvite.getGivenName());
    user.setFamilyName(userInvite.getFamilyName());
    user.setRoles(Collections.emptyList());

    if (!user.isEmailVerified()) {
      if (isPLNoEmailForSamlAccountInvitesEnabled) {
        user.setEmailVerified(true);
      } else {
        user.setEmailVerified(markEmailVerified);
      }
    }

    user.setAppId(GLOBAL_APP_ID);
    user.setImported(userInvite.getImportedByScim());
    user.setExternalUserId(userInvite.getExternalUserId());

    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)) {
      UserSource userSource = getUserSource(userInvite.getImportedByScim(), SSO == userInvite.getSource().getType());
      user = createUserWithAccountLevelData(user, accountId, userSource, CG);
    } else {
      user = createUser(user, accountId);
    }
    user = checkIfTwoFactorAuthenticationIsEnabledForAccount(user, account);

    if (!isInviteAcceptanceRequired || isPLNoEmailForSamlAccountInvitesEnabled) {
      addUserToUserGroups(accountId, user, userGroups, false, true);
      userGroups = userGroupService.getUserGroupsFromUserInvite(userInvite);
    }
    boolean isAutoInviteAcceptanceEnabled = !isInviteAcceptanceRequired && accountService.isSSOEnabled(account);

    if (!(isPLNoEmailForSamlAccountInvitesEnabled && !user.isTwoFactorAuthenticationEnabled())) {
      if (isAutoInviteAcceptanceEnabled
          || (isPLNoEmailForSamlAccountInvitesEnabled && user.isTwoFactorAuthenticationEnabled())) {
        sendUserInvitationToOnlySsoAccountMail(account, user);
      } else {
        sendNewInvitationMail(userInvite, account, user);
      }
    }

    auditServiceHelper.reportForAuditingUsingAccountId(
        accountId, null, user, createNewUser ? Type.CREATE : Type.UPDATE);
    userGroups.forEach(userGroupAdded
        -> auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, userGroupAdded, Type.ADD));

    if (isPLNoEmailForSamlAccountInvitesEnabled && !user.isTwoFactorAuthenticationEnabled()) {
      return USER_INVITE_NOT_REQUIRED;
    } else {
      eventPublishHelper.publishUserInviteFromAccountEvent(accountId, userInvite.getEmail());
    }

    return USER_INVITED_SUCCESSFULLY;
  }

  private void updateUserInvite(UserInvite updatedUserInvite) {
    String userEmail = updatedUserInvite.getEmail();
    String accountId = updatedUserInvite.getAccountId();

    Account account = accountService.get(accountId);
    User user = getUserByEmail(userEmail);

    UserInvite existingInvite = getUserInviteByEmailAndAccount(userEmail, accountId);
    if (existingInvite != null && !areUserGroupsIdenticalForInvites(updatedUserInvite, existingInvite)) {
      wingsPersistence.updateField(
          UserInvite.class, existingInvite.getUuid(), "userGroups", updatedUserInvite.getUserGroups());
      sendNewInvitationMail(existingInvite, account, user);
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, updatedUserInvite, Type.UPDATE);
    }
  }

  private boolean areUserGroupsIdenticalForInvites(UserInvite userInvite1, UserInvite userInvite2) {
    if (userInvite1.getUserGroups().size() != userInvite2.getUserGroups().size()) {
      return false;
    }
    Set<String> ids1 = userInvite1.getUserGroups().stream().map(UserGroup::getUuid).collect(Collectors.toSet());
    Set<String> ids2 = userInvite2.getUserGroups().stream().map(UserGroup::getUuid).collect(Collectors.toSet());

    return ids1.equals(ids2);
  }

  @Override
  public void addUserToUserGroups(
      String accountId, User user, List<UserGroup> userGroups, boolean sendNotification, boolean toBeAudited) {
    if (isEmpty(userGroups)) {
      return;
    }

    Set<String> newUserGroups = Sets.newHashSet();
    for (UserGroup userGroup : userGroups) {
      if (!userGroup.hasMember(user)) {
        if (userGroup.getMemberIds() == null) {
          userGroup.setMemberIds(new ArrayList<>());
        }
        userGroup.getMemberIds().add(user.getUuid());
        userGroupService.updateMembers(userGroup, false, toBeAudited);
        NotificationSettings notificationSettings = userGroup.getNotificationSettings();
        if (notificationSettings == null) {
          log.error("Notification settings not found for user group id: [{}]", userGroup.getUuid());
        } else if (notificationSettings.isSendMailToNewMembers()) {
          newUserGroups.add(userGroup.getUuid());
        }
      }
    }

    if (sendNotification && isNotEmpty(newUserGroups)) {
      sendAddedGroupEmail(user, accountService.get(accountId), userGroups);
    }
  }

  private List<UserGroup> addUserToUserGroups(
      String accountId, User user, SetView<String> userGroupIds, boolean sendNotification) {
    if (isNotEmpty(userGroupIds)) {
      List<UserGroup> userGroups = getUserGroups(accountId, userGroupIds);
      addUserToUserGroups(accountId, user, userGroups, sendNotification, false);
      return userGroups;
    }
    return new ArrayList<>();
  }

  private List<UserGroup> removeUserFromUserGroups(
      String accountId, User user, SetView<String> userGroupIds, boolean sendNotification) {
    if (isNotEmpty(userGroupIds)) {
      List<UserGroup> userGroups = getUserGroups(accountId, userGroupIds);
      removeUserFromUserGroups(user, userGroups, sendNotification);
      return userGroups;
    }
    return new ArrayList<>();
  }

  public void removeUserFromUserGroups(User user, List<UserGroup> userGroups, boolean sendNotification) {
    if (isNotEmpty(userGroups)) {
      final User userFinal = user;
      userGroups.forEach(userGroup -> {
        List<String> userGroupMembers = userGroup.getMemberIds();
        if (userGroupMembers != null) {
          userGroupMembers.remove(userFinal.getUuid());
          userGroupService.updateMembers(userGroup, sendNotification, false);
        }
      });
    }
  }

  private void removeRelatedUserInvite(String accountId, String email) {
    UserInvite userInvite = wingsPersistence.createQuery(UserInvite.class)
                                .filter(UserInviteKeys.email, email)
                                .filter(UserInviteKeys.accountId, accountId)
                                .get();
    if (userInvite != null) {
      wingsPersistence.delete(userInvite);
    }
  }

  private List<UserGroup> getUserGroupsOfUser(String accountId, String userId, boolean loadUsers) {
    PageRequest<UserGroup> pageRequest = aPageRequest()
                                             .withLimit(Long.toString(userGroupService.getCountOfUserGroups(accountId)))
                                             .addFilter(UserGroupKeys.accountId, EQ, accountId)
                                             .addFilter(UserGroupKeys.memberIds, EQ, userId)
                                             .build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, loadUsers, null, null);
    return pageResponse.getResponse();
  }

  @Override
  public List<UserGroup> getUserGroupsOfUserAudit(String accountId, String userId) {
    return wingsPersistence.createQuery(UserGroup.class)
        .filter(UserGroupKeys.accountId, accountId)
        .filter(UserGroupKeys.memberIds, userId)
        .asList();
  }

  private List<UserGroup> getUserGroups(String accountId, SetView<String> userGroupIds) {
    PageRequest<UserGroup> pageRequest = aPageRequest()
                                             .withLimit(Long.toString(userGroupService.getCountOfUserGroups(accountId)))
                                             .addFilter("_id", IN, userGroupIds.toArray())
                                             .addFilter(UserGroupKeys.accountId, EQ, accountId)
                                             .build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, true, null, null);
    return pageResponse.getResponse();
  }

  private Map<String, String> getNewInvitationTemplateModel(UserInvite userInvite, Account account, User user)
      throws URISyntaxException {
    String inviteUrl = getUserInviteUrl(userInvite, account);
    Map<String, String> model = getTemplateModel(userInvite.getEmail(), inviteUrl);
    model.put("company", account.getCompanyName());
    model.put("accountname", account.getAccountName());
    boolean shouldMailContainTwoFactorInfo = user.isTwoFactorAuthenticationEnabled();
    model.put("shouldMailContainTwoFactorInfo", Boolean.toString(shouldMailContainTwoFactorInfo));
    model.put("totpSecret", user.getTotpSecretKey());
    String otpUrl = totpAuthHandler.generateOtpUrl(account.getCompanyName(), user.getEmail(), user.getTotpSecretKey());
    model.put("totpUrl", otpUrl);
    return model;
  }

  @Override
  public String getUserInviteUrl(UserInvite userInvite, Account account) throws URISyntaxException {
    if (userInvite == null) {
      return null;
    }

    String accountId = account.getUuid();
    String encodedEmail = userInvite.getEmail();
    try {
      encodedEmail = URLEncoder.encode(userInvite.getEmail(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      log.error("Unable to encode email for invite url, using the invite one instead", e);
    }
    if (configuration.isNgAuthUIEnabled()) {
      String jwtToken = secretManager.generateJWTToken(
          Collections.singletonMap(UserInvite.UUID_KEY, userInvite.getUuid()), INVITE_SECRET);
      return buildAbsoluteUrl(configuration.getPortal().getGatewayPathPrefix() + NG_AUTH_UI_INVITE_PATH_FORMAT,
          Arrays.asList(new BasicNameValuePair("accountId", accountId), new BasicNameValuePair("email", encodedEmail),
              new BasicNameValuePair("token", jwtToken)),
          accountId);
    } else {
      return buildAbsoluteUrl(
          format("/invite?accountId=%s&account=%s&company=%s&email=%s&inviteId=%s", accountId, account.getAccountName(),
              account.getCompanyName(), userInvite.getEmail(), userInvite.getUuid()),
          accountId);
    }
  }

  @Override
  public String getUserInviteUrl(UserInvite userInvite) throws URISyntaxException {
    if (userInvite == null) {
      return null;
    }
    return buildAbsoluteUrl(
        format(INVITE_URL_FORMAT, userInvite.getEmail(), userInvite.getUuid()), userInvite.getAccountId());
  }

  private Map<String, String> getEmailVerificationTemplateModel(
      String name, String url, Map<String, String> params, String accountId) {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    return getTemplateModel(name, authenticationUtils.buildAbsoluteUrl(baseUrl, url, params).toString());
  }

  @Override
  public void sendNewInvitationMail(UserInvite userInvite, Account account, User user) {
    try {
      Map<String, String> templateModel = getNewInvitationTemplateModel(userInvite, account, user);
      signupService.sendEmail(userInvite, INVITE_EMAIL_TEMPLATE_NAME, templateModel);
    } catch (URISyntaxException e) {
      log.error("Invitation email couldn't be sent for userInviteId={}, userId={} & accountId={}", userInvite.getUuid(),
          user.getUuid(), account.getUuid(), e);
    }
  }

  @Override
  public void sendVerificationEmail(UserInvite userInvite, String url, Map<String, String> params) {
    Map<String, String> templateModel =
        getEmailVerificationTemplateModel(userInvite.getName(), url, params, userInvite.getAccountId());
    signupService.sendTrialSignupVerificationEmail(userInvite, templateModel);
  }

  private boolean sendEmail(String toEmail, String templateName, Map<String, String> templateModel) {
    List<String> toList = new ArrayList<>();
    toList.add(toEmail);
    EmailData emailData =
        EmailData.builder().to(toList).templateName(templateName).templateModel(templateModel).build();
    emailData.setRetries(2);
    return emailNotificationService.send(emailData);
  }

  private Map<String, Object> getAddedUserGroupTemplateModel(User user, Account account, List<UserGroup> userGroups)
      throws URISyntaxException {
    List<String> userGroupNamesList = new ArrayList<>();
    userGroups.forEach(userGroup -> userGroupNamesList.add(userGroup.getName()));
    String loginUrl =
        buildAbsoluteUrl(format(LOGIN_URL_FORMAT, account.getCompanyName(), account.getAccountName(), user.getEmail()),
            account.getUuid());

    Map<String, Object> model = new HashMap<>();
    model.put("name", sanitizeUserName(user.getName()));
    model.put("url", loginUrl);
    model.put("company", account.getCompanyName());
    model.put("subject",
        "You have been assigned new user groups in " + account.getCompanyName().toUpperCase() + " account."
            + " Click below to Sign-in");
    model.put("email", user.getEmail());
    model.put("authenticationMechanism", account.getAuthenticationMechanism().getType());
    model.put("message", "You have been added to following user group(s): " + String.join(",", userGroupNamesList));
    model.put("shouldMailContainTwoFactorInfo", Boolean.toString(false));

    // In case of username-password authentication mechanism, we don't need to add the SSO details in the email.
    if (account.getAuthenticationMechanism() == USER_PASSWORD) {
      return model;
    }

    SSOSettings ssoSettings;
    if (account.getAuthenticationMechanism() == AuthenticationMechanism.SAML) {
      ssoSettings = ssoSettingService.getSamlSettingsByAccountId(account.getUuid());
    } else if (account.getAuthenticationMechanism() == AuthenticationMechanism.LDAP) {
      ssoSettings = ssoSettingService.getLdapSettingsByAccountId(account.getUuid());
    } else if (account.getAuthenticationMechanism() == AuthenticationMechanism.OAUTH) {
      ssoSettings = ssoSettingService.getOauthSettingsByAccountId(account.getUuid());
    } else {
      log.error("New authentication mechanism detected. Needs to handle the added role email template flow.");
      throw new GeneralException("New authentication mechanism detected.");
    }
    model.put("ssoUrl", checkGetDomainName(account, ssoSettings.getUrl()));
    return model;
  }

  private String checkGetDomainName(Account account, String url) {
    try {
      String domainName = getDomainName(url);
      return domainName != null ? domainName : account.getAuthenticationMechanism().name();
    } catch (URISyntaxException use) {
      return account.getAuthenticationMechanism().name();
    }
  }

  private static String getDomainName(String url) throws URISyntaxException {
    URI uri = new URIBuilder(url).build();
    return uri.getHost();
  }

  @Override
  public void sendAddedGroupEmail(User user, Account account, List<UserGroup> userGroups) {
    try {
      Map<String, Object> templateModel = getAddedUserGroupTemplateModel(user, account, userGroups);
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName(ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME)
                                .templateModel(templateModel)
                                .accountId(account.getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error("Add to User Groups email couldn't be sent for userId={} in accountId={}", user.getUuid(),
          account.getUuid(), e);
    }
  }

  @Override
  public PageResponse<UserInvite> listInvites(PageRequest<UserInvite> pageRequest) {
    return wingsPersistence.query(UserInvite.class, pageRequest);
  }

  @Override
  public UserInvite getInvite(String accountId, String inviteId) {
    return wingsPersistence.createQuery(UserInvite.class)
        .filter(UserInviteKeys.accountId, accountId)
        .filter(UserInvite.UUID_KEY, inviteId)
        .get();
  }

  private UserInvite getInvite(String inviteId) {
    return wingsPersistence.createQuery(UserInvite.class).filter(UserInvite.UUID_KEY, inviteId).get();
  }

  private UserInvite getInviteFromEmail(String accountId, String email) {
    return wingsPersistence.createQuery(UserInvite.class)
        .filter(UserInviteKeys.accountId, accountId)
        .filter(UserInviteKeys.email, email)
        .get();
  }

  @Override
  public Query<UserInvite> getInvitesQueryFromAccountId(String accountId) {
    return wingsPersistence.createQuery(UserInvite.class).filter(UserInvite.ACCOUNT_ID_KEY2, accountId);
  }

  @Override
  public InviteOperationResponse completeInvite(UserInvite userInvite) {
    log.info("Completing invite for inviteId: {}", userInvite.getUuid());
    UserInvite existingInvite = getInvite(userInvite.getUuid());
    if (existingInvite == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    }
    if (existingInvite.isCompleted()) {
      return FAIL;
    }
    validateName(userInvite.getName());
    if (userInvite.getPassword() == null) {
      throw new InvalidRequestException("User name/password is not provided", USER);
    }
    loginSettingsService.verifyPasswordStrength(
        accountService.get(userInvite.getAccountId()), userInvite.getPassword());

    User existingUser = getUserByEmail(existingInvite.getEmail());
    Account account = getAccountFromInviteId(existingInvite.getUuid());
    if (existingUser == null || account == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    }

    AuthenticationMechanism authenticationMechanism =
        account.getAuthenticationMechanism() == null ? USER_PASSWORD : account.getAuthenticationMechanism();
    Preconditions.checkState(authenticationMechanism == USER_PASSWORD,
        "Invalid request. Complete invite should only be called if Auth Mechanism is UsePass");

    io.harness.data.structure.CollectionUtils.isPresent(
        existingUser.getPendingAccounts(), acc -> existingUser.getPendingAccounts().contains(account));

    List<Account> pendingAccounts = existingUser.getPendingAccounts();
    List<Account> accounts = existingUser.getAccounts();
    if ((isEmpty(pendingAccounts) || !pendingAccounts.contains(account))
        && (isEmpty(accounts) || !accounts.contains(account))) {
      log.error("Processing of InviteId: {} failed. Account missing in both pendingAccounts and accounts",
          userInvite.getUuid());
      throw new InvalidRequestException("Invite processing failed", USER);
    }

    moveAccountFromPendingToConfirmed(
        existingUser, account, userGroupService.getUserGroupsFromUserInvite(existingInvite), true);

    completeUserInfo(userInvite, existingUser);
    markUserInviteComplete(userInvite);
    eventPublishHelper.publishUserRegistrationCompletionEvent(userInvite.getAccountId(), existingUser);
    auditServiceHelper.reportForAuditingUsingAccountId(
        userInvite.getAccountId(), null, userInvite, Type.ACCEPTED_INVITE);
    sendInviteAcceptTelemetryEvents(existingUser, userInvite.getAccountId(), account.getAccountName());
    log.info(
        "Auditing accepted invite for userInvite={} in account={}", userInvite.getName(), userInvite.getAccountName());
    return ACCOUNT_INVITE_ACCEPTED;
  }

  private void sendInviteAcceptTelemetryEvents(User user, String accountId, String accountName) {
    String userEmail = user.getEmail();

    HashMap<String, Object> properties = new HashMap<>();
    properties.put("email", userEmail);
    properties.put("name", sanitizeUserName(user.getName()));
    properties.put("id", user.getUuid());
    properties.put("startTime", String.valueOf(Instant.now().toEpochMilli()));
    properties.put("accountId", accountId);
    properties.put("accountName", accountName);
    properties.put("source", USER_INVITE);

    // identify event to register new user
    telemetryReporter.sendIdentifyEvent(
        userEmail, properties, ImmutableMap.<Destination, Boolean>builder().put(Destination.MARKETO, true).build());

    HashMap<String, Object> groupProperties = new HashMap<>();
    groupProperties.put("group_id", accountId);
    groupProperties.put("group_type", "Account");
    groupProperties.put("group_name", accountName);

    // group event to register new signed-up user with new account
    telemetryReporter.sendGroupEvent(accountId, userEmail, groupProperties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.MARKETO, true).build());

    // flush all events so that event queue is empty
    telemetryReporter.flush();

    properties.put("platform", "CG");
    // Wait 20 seconds, to ensure identify is sent before track
    scheduledExecutor.schedule(
        ()
            -> telemetryReporter.sendTrackEvent("Invite Accepted", userEmail, accountId, properties,
                ImmutableMap.<Destination, Boolean>builder().put(Destination.MARKETO, true).build(), null),
        20, TimeUnit.SECONDS);
    log.info("User Invite telemetry sent");
  }

  @Override
  public User completeInviteAndSignIn(UserInvite userInvite) {
    completeInvite(userInvite);
    return authenticationManager.defaultLogin(userInvite.getEmail(), String.valueOf(userInvite.getPassword()));
  }

  @Override
  public User completeNGInviteAndSignIn(UserInviteDTO userInvite) {
    validateName(userInvite.getName());
    if (!validateNgInvite(userInvite)) {
      throw new InvalidRequestException("User invite token invalid");
    }
    completeNGInvite(userInvite, false, true);
    return authenticationManager.defaultLogin(userInvite.getEmail(), userInvite.getPassword());
  }

  @Override
  public User completeTrialSignupAndSignIn(String userInviteId) {
    UserInvite userInvite = getInvite(userInviteId);
    if (userInvite == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    }
    return completeTrialSignupAndSignIn(userInvite, false);
  }

  @Override
  public User completePaidSignupAndSignIn(UserInvite userInvite) {
    String accountName = accountService.suggestAccountName(userInvite.getAccountName());
    String companyName = userInvite.getCompanyName();

    User user = Builder.anUser()
                    .email(userInvite.getEmail())
                    .name(userInvite.getName())
                    .passwordHash(userInvite.getPasswordHash())
                    .accountName(accountName)
                    .companyName(companyName != null ? companyName : accountName)
                    .build();

    completeSignup(user, userInvite, getDefaultPaidLicense());
    return authenticationManager.defaultLoginUsingPasswordHash(userInvite.getEmail(), userInvite.getPasswordHash());
  }

  @Override
  public User completeTrialSignupAndSignIn(UserInvite userInvite, boolean shouldCreateSampleApp) {
    String accountName = accountService.suggestAccountName(userInvite.getAccountName());
    String companyName = userInvite.getCompanyName();

    User user = Builder.anUser()
                    .email(userInvite.getEmail())
                    .name(userInvite.getName())
                    .passwordHash(userInvite.getPasswordHash())
                    .accountName(accountName)
                    .companyName(companyName != null ? companyName : accountName)
                    .utmInfo(userInvite.getUtmInfo())
                    .build();

    completeSignup(user, userInvite, getTrialLicense(), shouldCreateSampleApp);

    return authenticationManager.defaultLoginUsingPasswordHash(userInvite.getEmail(), userInvite.getPasswordHash());
  }

  @Override
  public User completeMarketPlaceSignup(User user, UserInvite userInvite, MarketPlaceType marketPlaceType) {
    if (userInvite.getPassword() == null) {
      throw new InvalidArgumentsException(Pair.of("args", "Password needs to be specified to login"));
    }

    marketPlaceSignup(user, userInvite, marketPlaceType);
    return authenticationManager.defaultLogin(userInvite.getEmail(), String.valueOf(userInvite.getPassword()));
  }

  @Override
  public void completeNGInviteWithAccountLevelData(
      UserInviteDTO userInvite, boolean shouldSendTwoFactorAuthResetEmail) {
    completeNGInvite(userInvite, SCIM == userInvite.getUserSource(), shouldSendTwoFactorAuthResetEmail);
  }
  @Override
  public void completeNGInvite(
      UserInviteDTO userInvite, boolean isScimInvite, boolean shouldSendTwoFactorAuthResetEmail) {
    String accountId = userInvite.getAccountId();
    limitCheck(accountId, userInvite.getEmail());
    Account account = accountService.get(accountId);
    User user = getUserByEmail(userInvite.getEmail());
    if (user == null) {
      user = anUser().build();
      user.setEmail(userInvite.getEmail().trim().toLowerCase());
      user.setName(userInvite.getName().trim());
      user.setGivenName(userInvite.getGivenName());
      user.setFamilyName(userInvite.getFamilyName());
      user.setExternalUserId(userInvite.getExternalId());
      user.setRoles(new ArrayList<>());
      user.setEmailVerified(true);
      user.setAppId(GLOBAL_APP_ID);
      user.setAccounts(new ArrayList<>(Collections.singletonList(account)));
    }

    if (isScimInvite) {
      user.setImported(true);
    }

    String name = userInvite.getName().trim();
    user.setName(name);
    if (userInvite.getPassword() != null) {
      AuthenticationMechanism authenticationMechanism =
          account.getAuthenticationMechanism() == null ? USER_PASSWORD : account.getAuthenticationMechanism();
      Preconditions.checkState(authenticationMechanism == USER_PASSWORD,
          "Invalid request. Complete invite should only be called if Auth Mechanism is UserPassword");
      loginSettingsService.verifyPasswordStrength(
          accountService.get(userInvite.getAccountId()), userInvite.getPassword().toCharArray());

      user.setPasswordHash(hashpw(userInvite.getPassword(), BCrypt.gensalt()));
    }

    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)) {
      UserSource userSource = null == userInvite.getUserSource() ? MANUAL : userInvite.getUserSource();
      createUserWithAccountLevelData(user, accountId, userSource, NG);
    } else {
      user = createUser(user, accountId);
    }

    if (shouldSendTwoFactorAuthResetEmail) {
      user = checkIfTwoFactorAuthenticationIsEnabledForAccount(user, account);
      if (user.isTwoFactorAuthenticationEnabled()) {
        totpAuthHandler.sendTwoFactorAuthenticationResetEmail(user);
      }
    }
    // Empty user group list because this user invite is from NG and the method adds user to CG user groups
    moveAccountFromPendingToConfirmed(user, account, Collections.emptyList(), true);
    eventPublishHelper.publishUserRegistrationCompletionEvent(userInvite.getAccountId(), user);
    NGRestUtils.getResponse(ngInviteClient.completeInvite(userInvite.getToken()));
  }

  @Override
  public User completeUserCreationOrAdditionViaJitAndSignIn(String email, String accountId) {
    User user = getUserByEmail(email);
    if (user == null) {
      user = anUser().build();
      user.setEmail(email.trim().toLowerCase());
      user.setName(email.trim().toLowerCase());
      user.setRoles(new ArrayList<>());
      user.setEmailVerified(true);
      user.setAppId(GLOBAL_APP_ID);
      user.setAccounts(new ArrayList<>());
    }
    user = createUser(user, accountId);
    try {
      NGRestUtils.getResponse(ngInviteClient.completeUserCreationForJIT(email, accountId));
      addUserToAccount(user.getUuid(), accountId, JIT);
    } catch (Exception ex) {
      log.info("JIT Error: User creation call in Ng failed while provisioning user.", ex);
      throw new WingsException("Something went wrong. Please re-try login or contact Harness Support");
    }
    return user;
  }

  private UserSource getUserSource(boolean isScimInvite, boolean isLDAPInvite) {
    UserSource userSource = MANUAL;
    if (isScimInvite) {
      userSource = SCIM;
    } else if (isLDAPInvite) {
      userSource = LDAP;
    }
    return userSource;
  }

  private boolean validateNgInvite(UserInviteDTO userInvite) {
    InviteDTO inviteDTO = NGRestUtils.getResponse(ngInviteClient.getInviteWithToken(userInvite.getToken()));
    if (inviteDTO == null || !inviteDTO.getEmail().equals(userInvite.getEmail())
        || !inviteDTO.getAccountIdentifier().equals(userInvite.getAccountId())) {
      return false;
    }
    return true;
  }

  private void marketPlaceSignup(User user, final UserInvite userInvite, MarketPlaceType marketPlaceType) {
    validateUser(user);
    log.info("Info for user: {}", user);

    UserInvite existingInvite = wingsPersistence.get(UserInvite.class, userInvite.getUuid());
    if (existingInvite == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    }
    if (existingInvite.isCompleted()) {
      log.error("Unexpected state: Existing invite is already completed. ID = {}", userInvite.getUuid());
      return;
    }

    String email = user.getEmail();
    User existingUser = getUserByEmail(email);
    if (existingUser != null) {
      throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
    }

    if (userInvite.getMarketPlaceToken() == null) {
      throw new GeneralException(
          String.format("Marketplace token not found for User Invite Id : [{%s}]", userInvite.getUuid()));
    }

    Map<String, Claim> claims =
        secretManager.verifyJWTToken(userInvite.getMarketPlaceToken(), JWT_CATEGORY.MARKETPLACE_SIGNUP);
    String userInviteID = claims.get(MarketPlaceConstants.USERINVITE_ID_CLAIM_KEY).asString();
    if (!userInviteID.equals(userInvite.getUuid())) {
      throw new GeneralException(
          String.format("User Invite Id in claim: [{%s}] does not match the User Invite Id : [{%s}]", userInviteID,
              userInvite.getUuid()));
    }

    LicenseInfo licenseInfo;
    MarketPlace marketPlace;
    if (marketPlaceType == MarketPlaceType.GCP) {
      String gcpAccountId = JWT.decode(claims.get(MarketPlaceConstants.GCP_MARKETPLACE_TOKEN).asString()).getSubject();
      licenseInfo = LicenseInfo.builder()
                        .accountType(AccountType.PAID)
                        .licenseUnits(50)
                        .expiryTime(software.wings.service.impl.LicenseUtils.getDefaultPaidExpiryTime())
                        .accountStatus(AccountStatus.INACTIVE)
                        .build();
      wingsPersistence.save(GCPMarketplaceCustomer.builder()
                                .gcpAccountId(gcpAccountId)
                                .harnessAccountId(setupAccountForUser(user, userInvite, licenseInfo, true))
                                .build());
      gcpProcurementService.approveAccount(gcpAccountId);
    } else {
      String marketPlaceID = claims.get(MarketPlaceConstants.MARKETPLACE_ID_CLAIM_KEY).asString();
      marketPlace = wingsPersistence.get(MarketPlace.class, marketPlaceID);
      if (marketPlace == null) {
        throw new GeneralException(String.format("No MarketPlace found with marketPlaceID=[{%s}]", marketPlaceID));
      }
      String accountId = setupAccountBasedOnProduct(user, userInvite, marketPlace);

      marketPlace.setAccountId(accountId);
      wingsPersistence.save(marketPlace);
    }
  }

  private String setupAccountForUser(User user, UserInvite userInvite, LicenseInfo licenseInfo, boolean isNG) {
    Account account = Account.Builder.anAccount()
                          .withAccountName(user.getAccountName())
                          .withCompanyName(user.getCompanyName())
                          .withLicenseInfo(licenseInfo)
                          .withAppId(GLOBAL_APP_ID)
                          .build();
    log.info("Info for account: {}", account);

    if (isNG) {
      account = setupNGAccount(account);
    } else {
      account = setupAccount(account);
    }
    String accountId = account.getUuid();
    List<UserGroup> accountAdminGroups = getAccountAdminGroup(accountId);
    saveUserAndUserGroups(user, account, accountAdminGroups);

    completeUserInviteForSignup(userInvite, accountId);
    return accountId;
  }

  private void saveUserAndUserGroups(User user, Account account, List<UserGroup> accountAdminGroups) {
    user.setAppId(GLOBAL_APP_ID);
    user.setEmail(user.getEmail().trim().toLowerCase());
    if (isEmpty(user.getPasswordHash())) {
      user.setPasswordHash(hashpw(new String(user.getPassword()), BCrypt.gensalt()));
    }
    user.setEmailVerified(true);
    user.getAccounts().add(account);
    user.setUserGroups(accountAdminGroups);
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, account.getUuid())) {
      createUserWithAccountLevelData(user, account.getUuid(), MANUAL, CG);
    } else {
      createUser(user, account.getUuid());
    }

    addUserToUserGroups(account.getUuid(), user, accountAdminGroups, false, false);
  }

  private void validateUser(User user) {
    if (user.getAccountName() == null || user.getCompanyName() == null) {
      throw new InvalidRequestException("Account/company name is not provided", USER);
    }
    if (isEmpty(user.getName()) || isEmpty(user.getPassword())) {
      throw new InvalidRequestException("User's name/password is not provided", USER);
    }
  }

  @Override
  public UserInvite completeSignup(User user, UserInvite userInvite, LicenseInfo licenseInfo) {
    return completeSignup(user, userInvite, licenseInfo, true);
  }

  @Override
  public UserInvite completeSignup(
      User user, UserInvite userInvite, LicenseInfo licenseInfo, boolean shouldCreateSampleApp) {
    if (user.getAccountName() == null || user.getCompanyName() == null) {
      throw new InvalidRequestException("Account/company name is not provided", USER);
    }
    if (isEmpty(user.getName()) || (isEmpty(user.getPassword()) && isEmpty(user.getPasswordHash()))) {
      throw new InvalidRequestException("User's name/password is not provided", USER);
    }

    UserInvite existingInvite = wingsPersistence.get(UserInvite.class, userInvite.getUuid());
    if (existingInvite == null) {
      throw new UnauthorizedException(EXC_MSG_USER_INVITE_INVALID, USER);
    } else if (existingInvite.isCompleted()) {
      return existingInvite;
    }

    String email = existingInvite.getEmail();
    User existingUser = getUserByEmail(email);
    if (existingUser != null) {
      throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
    }

    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    Account account = Account.Builder.anAccount()
                          .withAccountName(user.getAccountName())
                          .withCompanyName(user.getCompanyName())
                          .withAppId(GLOBAL_APP_ID)
                          .withLicenseInfo(licenseInfo)
                          .build();

    TrialSignupOptions trialSignupOptions =
        new TrialSignupOptions(Products.getProductsFromFullNames(existingInvite.getFreemiumProducts()),
            existingInvite.getFreemiumAssistedOption());
    account.setTrialSignupOptions(trialSignupOptions);

    // Create an trial account which license expires in 15 days.
    account = setupAccount(account, shouldCreateSampleApp);
    String accountId = account.getUuid();

    // For trial user just signed up, it will be assigned to the account admin role.
    List<UserGroup> accountAdminGroups = getAccountAdminGroup(accountId);

    completeUserInviteForSignup(userInvite, accountId);

    saveUserAndUserGroups(user, account, accountAdminGroups);

    eventPublishHelper.publishUserRegistrationCompletionEvent(accountId, user);
    if (userInvite.getAccountId() != null) {
      auditServiceHelper.reportForAuditingUsingAccountId(
          userInvite.getAccountId(), null, userInvite, Type.ACCEPTED_INVITE);
      log.info(
          "Auditing accepted invite for userInvite={} in account={}", userInvite.getName(), account.getAccountName());
    }
    return userInvite;
  }

  private LicenseInfo getTrialLicense() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountType(AccountType.TRIAL);
    return licenseInfo;
  }

  private LicenseInfo getDefaultPaidLicense() {
    final Instant defaultExpiry = Instant.now().plus(365, ChronoUnit.DAYS);
    return LicenseInfo.builder()
        .accountType(AccountType.PAID)
        .licenseUnits(50)
        .expiryTime(from(defaultExpiry).getTime())
        .accountStatus(AccountStatus.ACTIVE)
        .build();
  }

  private void completeUserInviteForSignup(UserInvite userInvite, String accountId) {
    userInvite.setAccountId(accountId);
    userInvite.setCompleted(true);
    userInvite.setAgreement(true);

    // Update existing invite with the associated account ID and set the status to be completed.
    wingsPersistence.save(userInvite);
  }

  @Override
  public User signUpUserUsingOauth(OauthUserInfo userInfo, String oauthProviderName) {
    log.info("User not found in db. Creating an account for: [{}]", userInfo.getEmail());
    checkForFreemiumCluster();
    User user = createUser(userInfo, oauthProviderName);
    notNullOrEmptyCheck(user.getAccountName(), "Account/Company name");
    notNullOrEmptyCheck(user.getName(), "User's name");

    throwExceptionIfUserIsAlreadyRegistered(user.getEmail());

    TrialSignupOptions trialSignupOptions =
        new TrialSignupOptions(userInfo.getFreemiumProducts(), userInfo.getFreemiumAssistedOption());

    // Create a trial account whose license expires in 15 days.
    Account account = createAccountWithTrialLicense(user, trialSignupOptions);

    // For trial user just signed up, it will be assigned to the account admin role.
    user = assignUserToAccountAdminGroup(user, account);

    String accountId = account.getUuid();
    createSSOSettingsAndMarkAsDefaultAuthMechanism(accountId);

    // PL-2698: UI lead-update call will be called only if it's first login. Will need to
    // make sure the isFirstLogin is always derived from lastLogin value.
    boolean isFirstLogin = user.getLastLogin() == 0L;
    user.setFirstLogin(isFirstLogin);

    return user;
  }

  private void checkForFreemiumCluster() {
    // A safe check to ensure that no signup is allowed in the paid cluster.
    if (!configuration.isTrialRegistrationAllowed()) {
      throw new InvalidRequestException("Signup is not allowed in paid cluster");
    }
  }

  private User createUser(OauthUserInfo userInfo, String oauthProvider) {
    String email = userInfo.getEmail();
    final String companyName = accountService.suggestAccountName(userInfo.getName());
    return Builder.anUser()
        .email(email)
        .name(userInfo.getName())
        .accountName(companyName)
        .companyName(companyName)
        .emailVerified(true)
        .oauthProvider(oauthProvider)
        .utmInfo(userInfo.getUtmInfo())
        .build();
  }

  private void notNullOrEmptyCheck(String name, String errorSubject) {
    if (Strings.isNullOrEmpty(name)) {
      throw new InvalidRequestException(errorSubject + " is empty", USER);
    }
  }

  private User assignUserToAccountAdminGroup(User user, Account account) {
    List<UserGroup> accountAdminGroups = getAccountAdminGroup(account.getUuid());

    user.setAppId(GLOBAL_APP_ID);
    user.getAccounts().add(account);
    user.setUserGroups(accountAdminGroups);
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, account.getUuid())) {
      createUserWithAccountLevelData(user, account.getUuid(), MANUAL, CG);
    } else {
      user = createUser(user, account.getUuid());
    }

    addUserToUserGroups(account.getUuid(), user, accountAdminGroups, false, false);

    return user;
  }

  private void throwExceptionIfUserIsAlreadyRegistered(final String email) {
    User existingUser = getUserByEmail(email);
    if (existingUser != null) {
      throw new UserRegistrationException(EXC_USER_ALREADY_REGISTERED, ErrorCode.USER_ALREADY_REGISTERED, USER);
    }
  }

  private Account createAccountWithTrialLicense(User user, TrialSignupOptions trialSignupOptions) {
    LicenseInfo licenseInfo = getTrialLicense();
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    Account account = Account.Builder.anAccount()
                          .withAccountName(user.getAccountName())
                          .withCompanyName(user.getCompanyName())
                          .withAppId(GLOBAL_APP_ID)
                          .withLicenseInfo(licenseInfo)
                          .build();

    account.setTrialSignupOptions(trialSignupOptions);

    account = setupAccount(account, false);
    return account;
  }

  private void createSSOSettingsAndMarkAsDefaultAuthMechanism(String accountId) {
    OauthSettings oauthSettings =
        OauthSettings.builder()
            .accountId(accountId)
            .displayName(
                Arrays.stream(OauthProviderType.values()).map(OauthProviderType::name).collect(Collectors.joining(",")))
            .allowedProviders(Arrays.stream(OauthProviderType.values()).collect(Collectors.toSet()))
            .build();
    ssoSettingService.saveOauthSettings(oauthSettings);
    log.info("Setting authentication mechanism as oauth for account id: {}", accountId);
    ssoService.setAuthenticationMechanism(accountId, AuthenticationMechanism.OAUTH, false);
  }

  @Override
  public UserInvite deleteInvite(String accountId, String inviteId) {
    UserInvite userInvite = wingsPersistence.createQuery(UserInvite.class)
                                .filter(ID_KEY, inviteId)
                                .filter(UserInviteKeys.accountId, accountId)
                                .get();
    if (userInvite != null) {
      wingsPersistence.delete(userInvite);
    }
    return userInvite;
  }

  @Override
  public boolean deleteInvites(String accountId, String email) {
    Query userInvitesQuery = wingsPersistence.createQuery(UserInvite.class)
                                 .filter(UserInviteKeys.accountId, accountId)
                                 .filter(UserInviteKeys.email, email);
    return wingsPersistence.delete(userInvitesQuery);
  }

  @Override
  public String getInviteIdFromToken(String jwtToken) {
    String jwtPasswordSecret = secretManager.getJWTSecret(INVITE_SECRET);
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(HARNESS_ISSUER).build();
      verifier.verify(jwtToken);
      return JWT.decode(jwtToken).getClaim(UserInvite.UUID_KEY).asString();
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException("JWTToken validation failed");
    } catch (JWTDecodeException | SignatureVerificationException e) {
      throw new InvalidCredentialsException("Invalid JWTToken received, failed to decode the token", USER);
    }
  }

  @Override
  public List<UserInvite> getInvitesFromAccountIdAndUserGroupId(String accountId, String userGroupId) {
    Query<UserInvite> userInviteQuery = wingsPersistence.createQuery(UserInvite.class)
                                            .filter(ACCOUNT_ID_KEY, accountId)
                                            .field(UserInviteKeys.userGroups)
                                            .hasThisOne(userGroupId);
    return userInviteQuery.asList();
  }

  @Override
  public boolean resetPassword(UserResource.ResetPasswordRequest resetPasswordRequest) {
    String email = resetPasswordRequest.getEmail();
    User user = getUserByEmail(email);
    if (user == null) {
      return true;
    }

    String jwtPasswordSecret = configuration.getPortal().getJwtPasswordSecret();
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      String token = JWT.create()
                         .withIssuer(HARNESS_ISSUER)
                         .withIssuedAt(new Date())
                         .withExpiresAt(new Date(System.currentTimeMillis() + 4 * 60 * 60 * 1000)) // 4 hrs
                         .withClaim("email", email)
                         .sign(algorithm);
      sendResetPasswordEmail(user, token, resetPasswordRequest.getIsNG());
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException(EXC_MSG_RESET_PASS_LINK_NOT_GEN);
    }
    return true;
  }

  private void sendPasswordChangeEmail(User user) {
    Map<String, Object> templateModel = new HashMap<>();
    templateModel.put("name", sanitizeUserName(user.getName()));
    templateModel.put("email", user.getEmail());
    List<String> toList = new ArrayList<>();
    toList.add(user.getEmail());
    EmailData emailData = EmailData.builder()
                              .to(toList)
                              .templateName(USER_PASSWORD_CHANGED_EMAIL_TEMPLATE_NAME)
                              .templateModel(templateModel)
                              .build();
    emailData.setCc(Collections.emptyList());
    emailData.setRetries(2);
    emailNotificationService.send(emailData);
  }

  @Override
  public boolean updatePassword(String resetPasswordToken, char[] password) {
    String jwtPasswordSecret = configuration.getPortal().getJwtPasswordSecret();
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(HARNESS_ISSUER).build();
      verifier.verify(resetPasswordToken);
      JWT decode = JWT.decode(resetPasswordToken);
      String email = decode.getClaim("email").asString();
      User user = validateTokenAndGetUser(email, decode.getIssuedAt().getTime());
      updatePasswordAndPostSteps(user, password);
    } catch (UnsupportedEncodingException exception) {
      throw new GeneralException("Invalid reset password link");
    } catch (JWTVerificationException exception) {
      throw new UnauthorizedException("Token has expired", USER);
    }
    return true;
  }

  @Override
  public PasswordChangeResponse changePassword(String userId, PasswordChangeDTO passwordChangeDTO) {
    User user = get(userId);
    boolean correctUserPassword = checkpw(passwordChangeDTO.getCurrentPassword(), user.getPasswordHash());
    if (!correctUserPassword) {
      return PasswordChangeResponse.INCORRECT_CURRENT_PASSWORD;
    }
    try {
      updatePasswordAndPostSteps(user, passwordChangeDTO.getNewPassword().toCharArray());
    } catch (WingsException e) {
      if (e.getMessage().matches("(.*)Password violates strength policy(.*)")) {
        return PasswordChangeResponse.PASSWORD_STRENGTH_VIOLATED;
      } else {
        throw e;
      }
    }
    return PasswordChangeResponse.PASSWORD_CHANGED;
  }

  private User validateTokenAndGetUser(String email, long tokenIssuedAt) {
    User user = getUserByEmail(email);
    if (user == null) {
      throw new InvalidRequestException("Email doesn't exist");
    } else if (user.getPasswordChangedAt() > tokenIssuedAt) {
      throw new UnauthorizedException("Token has expired", USER);
    }
    return user;
  }

  @Override
  public void logout(User user) {
    authService.invalidateToken(user.getToken());
    evictUserFromCache(user.getUuid());
  }

  @Override
  public LogoutResponse logout(String accountId, String userId) {
    LogoutResponse logoutResponse = new LogoutResponse();
    log.info("Sending logout response from manager for user {} in account {}", userId, accountId);
    SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
    log.info("Samlsettings from accountId is {}", samlSettings);
    if (samlSettings != null && samlSettings.getLogoutUrl() != null) {
      logoutResponse.setLogoutUrl(samlSettings.getLogoutUrl());
      log.info("Logout URL from accountId is {}", samlSettings.getLogoutUrl());
    }
    try {
      User user = get(accountId, userId);
      log.info("Invalidating token for {}", user);
      if (user != null) {
        logout(user);
      }
    } catch (Exception e) {
      log.error("Invalidation of token and cache clear wasn't done", e);
    }
    log.info("Logout Response from manager {}", logoutResponse);
    return logoutResponse;
  }

  private void updatePasswordAndPostSteps(User existingUser, char[] password) {
    User user = resetUserPassword(existingUser, password);
    log.info("UPDATE_USER_LOCKOUTINFO: Clearing and re-setting lockout info for user {} post password reset",
        user.getUuid());
    loginSettingsService.updateUserLockoutInfo(user, accountService.get(user.getDefaultAccountId()), 0);
    sendPasswordChangeEmail(user);
    user.getAccounts().forEach(account
        -> auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, user, Type.RESET_PASSWORD));
  }

  private User resetUserPassword(User user, char[] password) {
    loginSettingsService.verifyPasswordStrength(accountService.get(user.getDefaultAccountId()), password);
    String hashed = hashpw(new String(password), BCrypt.gensalt());
    wingsPersistence.update(user,
        wingsPersistence.createUpdateOperations(User.class)
            .set("passwordHash", hashed)
            .set("passwordExpired", false)
            .set("passwordChangedAt", System.currentTimeMillis()));
    executorService.submit(() -> authService.invalidateAllTokensForUser(user.getUuid()));
    return user;
  }

  @Override
  public boolean isTwoFactorEnabled(String accountId, String userId) {
    // Check if admin has 2FA enabled
    User user = wingsPersistence.get(User.class, userId);
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }
    return user.isTwoFactorAuthenticationEnabled();
  }

  @Override
  public boolean overrideTwoFactorforAccount(String accountId, boolean adminOverrideTwoFactorEnabled) {
    try {
      Query<User> updateQuery = wingsPersistence.createQuery(User.class);
      updateQuery.filter(UserKeys.accounts, accountId);
      if (updateQuery.count() > 0) {
        for (User user : updateQuery) {
          Account defaultAccount = authenticationUtils.getDefaultAccount(user);
          log.info("User {} default account Id is {}", user.getEmail(), defaultAccount.getUuid());
          if (defaultAccount.getUuid().equals(accountId) && !user.isTwoFactorAuthenticationEnabled()) {
            User updatedUser = enableTwoFactorAuthenticationForUser(user, defaultAccount);
            log.info("Sending 2FA reset email to user {}", updatedUser.getEmail());
            totpAuthHandler.sendTwoFactorAuthenticationResetEmail(updatedUser);
            publishUserEvent(user, updatedUser);
          }
        }
      }
    } catch (Exception ex) {
      throw new GeneralException("Exception occurred while enforcing Two factor authentication for users");
    }

    return true;
  }

  private User enableTwoFactorAuthenticationForUser(User user, Account account) {
    log.info("Enabling 2FA for user {}", user.getEmail());
    TwoFactorAuthenticationSettings twoFactorAuthenticationSettings =
        totpAuthHandler.createTwoFactorAuthenticationSettings(user, account);
    User updatedUser = updateTwoFactorAuthenticationSettings(user, twoFactorAuthenticationSettings);
    publishUserEvent(user, updatedUser);
    return updatedUser;
  }

  /**
   * Checks if the user's default account has 2FA enabled account wide. If yes, then setup 2FA for the user if not
   * already set
   *
   * @param user
   * @param account
   */
  private User checkIfTwoFactorAuthenticationIsEnabledForAccount(User user, Account account) {
    String defaultAccountId = null;
    if (isNotEmpty(user.getAccounts())) {
      defaultAccountId = authenticationUtils.getDefaultAccount(user).getUuid();
    } else {
      defaultAccountId = account.getUuid();
    }
    log.info("User {} default account Id is {}", user.getEmail(), defaultAccountId);
    log.info("2FA enabled is {} account wide for account {}", account.isTwoFactorAdminEnforced(), account.getUuid());
    if (defaultAccountId.equals(account.getUuid()) && account.isTwoFactorAdminEnforced()
        && !user.isTwoFactorAuthenticationEnabled()) {
      user = enableTwoFactorAuthenticationForUser(user, account);
      user.setTwoFactorAuthenticationEnabled(true);
    }
    return user;
  }

  private void sendResetPasswordEmail(User user, String token, boolean isNGRequest) {
    try {
      String resetPasswordUrl = getResetPasswordUrl(token, user, isNGRequest);
      Map<String, String> templateModel = getTemplateModel(user.getName(), resetPasswordUrl);
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      String templateName = isNGRequest ? "ng_reset_password" : "reset_password";
      if (isNGRequest) {
        EmailChannelBuilder emailChannel = EmailChannel.builder()
                                               .recipients(toList)
                                               .accountId(getPrimaryAccount(user).getUuid())
                                               .templateId(templateName)
                                               .templateData(templateModel)
                                               .team(Team.OTHER)
                                               .userGroups(Collections.emptyList());
        log.info("sending reset password email through ng: {} ", emailChannel.toString());
        notificationClient.sendNotificationAsync(emailChannel.build());
      } else {
        EmailData emailData = EmailData.builder()
                                  .to(toList)
                                  .templateName(templateName)
                                  .templateModel(templateModel)
                                  .accountId(getPrimaryAccount(user).getUuid())
                                  .build();
        emailData.setCc(Collections.emptyList());
        emailData.setRetries(2);
        emailNotificationService.send(emailData);
      }
    } catch (URISyntaxException e) {
      log.error(RESET_ERROR, e);
    }
  }

  @Override
  public void sendUserInvitationToOnlySsoAccountMail(Account account, User user) {
    try {
      Map<String, String> templateModel = getUserInvitationToSsoTemplateModel(account, user);
      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName(ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME)
                                .templateModel(templateModel)
                                .accountId(account.getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException use) {
      log.error("User Invitation email to SSO couldn't be sent for userId={} in accountId={}", user.getUuid(),
          account.getUuid(), use);
    }
  }

  private String getResetPasswordUrl(String token, User user, boolean isNGRequest) throws URISyntaxException {
    String accountIdParam = "?accountId=" + user.getDefaultAccountId();
    if (isNGRequest) {
      return buildAbsoluteUrl("auth/", "/reset-password/" + token + accountIdParam, user.getDefaultAccountId());
    } else {
      return buildAbsoluteUrl("/reset-password/" + token + accountIdParam, user.getDefaultAccountId());
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#matchPassword(java.lang.String, java.lang.String)
   */
  @Override
  public boolean matchPassword(char[] password, String hash) {
    return checkpw(new String(password), hash);
  }

  @Override
  public User createUserWithAccountLevelData(
      User user, String accountId, UserSource userSource, Generation generation) {
    userServiceHelper.populateAccountToUserMapping(user, accountId, generation, userSource);
    return createUser(user, accountId);
  }

  @Override
  public User createUser(User user, String accountId) {
    boolean isExistingUser = user.getUuid() != null;
    if (null != user.getName()) {
      validateName(user.getName());
    }
    user = wingsPersistence.saveAndGet(User.class, user);
    if (isExistingUser) {
      evictUserFromCache(user.getUuid());
    }
    eventPublishHelper.publishSetupRbacEvent(accountId, user.getUuid(), EntityType.USER);
    publishUserEvent(null, user);
    return user;
  }

  @Override
  public User updateUserProfile(@NotNull User user) {
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    if (user.getName() != null) {
      validateName(user.getName());
      updateOperations.set(UserKeys.name, user.getName());
    } else {
      updateOperations.unset(UserKeys.name);
    }
    if (isNotEmpty(user.getAccounts())) {
      user.getAccounts().forEach(account -> {
        auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, user, Event.Type.UPDATE);
        log.info(
            "Auditing updation of User Profile for user={} in account={}", user.getUuid(), account.getAccountName());
      });
    }
    return updateUser(user.getUuid(), updateOperations);
  }

  @Override
  public User addEventToUserMarketoCampaigns(String userId, EventType eventType) {
    User user = this.getUserFromCacheOrDB(userId);
    Set<String> consolidatedReportedMarketoCampaigns = Sets.newHashSet();
    Set<String> reportedMarketoCampaigns = user.getReportedMarketoCampaigns();
    if (isNotEmpty(reportedMarketoCampaigns)) {
      consolidatedReportedMarketoCampaigns.addAll(reportedMarketoCampaigns);
    }

    consolidatedReportedMarketoCampaigns.add(eventType.name());
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.set(UserKeys.reportedMarketoCampaigns, consolidatedReportedMarketoCampaigns);

    return applyUpdateOperations(user, updateOperations);
  }

  @Override
  public User updateTwoFactorAuthenticationSettings(User user, TwoFactorAuthenticationSettings settings) {
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.set(UserKeys.twoFactorAuthenticationEnabled, settings.isTwoFactorAuthenticationEnabled());
    addTwoFactorAuthenticationOperation(settings.getMechanism(), updateOperations);
    addTotpSecretKeyOperation(settings.getTotpSecretKey(), updateOperations);
    User updatedUser = this.applyUpdateOperations(user, updateOperations);
    publishUserEvent(user, updatedUser);
    return updatedUser;
  }

  private void addTwoFactorAuthenticationOperation(
      @NotNull TwoFactorAuthenticationMechanism twoFactorAuthenticationMechanism,
      @NotNull UpdateOperations<User> updateOperations) {
    if (twoFactorAuthenticationMechanism != null) {
      updateOperations.set(UserKeys.twoFactorAuthenticationMechanism, twoFactorAuthenticationMechanism);
    } else {
      updateOperations.unset(UserKeys.twoFactorAuthenticationMechanism);
    }
  }

  private void addTotpSecretKeyOperation(
      @NotNull String totpSecretKey, @NotNull UpdateOperations<User> updateOperations) {
    if (totpSecretKey != null) {
      updateOperations.set(UserKeys.totpSecretKey, totpSecretKey);
    } else {
      updateOperations.unset(UserKeys.totpSecretKey);
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#update(software.wings.beans.User)
   */
  @Override
  public User update(User user) {
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);

    if (user.getPassword() != null && user.getPassword().length > 0) {
      updateOperations.set(UserKeys.passwordHash, hashpw(new String(user.getPassword()), BCrypt.gensalt()));
      updateOperations.set(UserKeys.passwordChangedAt, System.currentTimeMillis());
    }
    if (isNotEmpty(user.getRoles())) {
      updateOperations.set(UserKeys.roles, user.getRoles());
    }

    if (user.getName() != null) {
      // validateName(user.getName());
      updateOperations.set(UserKeys.name, user.getName());
    } else {
      updateOperations.unset(UserKeys.name);
    }

    updateOperations.set(UserKeys.twoFactorAuthenticationEnabled, user.isTwoFactorAuthenticationEnabled());
    addTwoFactorAuthenticationOperation(user.getTwoFactorAuthenticationMechanism(), updateOperations);
    addTotpSecretKeyOperation(user.getTotpSecretKey(), updateOperations);

    if (user.getMarketoLeadId() > 0) {
      updateOperations.set(UserKeys.marketoLeadId, user.getMarketoLeadId());
    }

    if (isNotEmpty(user.getReportedMarketoCampaigns())) {
      updateOperations.set(UserKeys.reportedMarketoCampaigns, user.getReportedMarketoCampaigns());
    }

    if (isNotEmpty(user.getSegmentIdentity())) {
      updateOperations.set(UserKeys.segmentIdentity, user.getSegmentIdentity());
    }

    if (isNotEmpty(user.getReportedSegmentTracks())) {
      updateOperations.set(UserKeys.reportedSegmentTracks, user.getReportedSegmentTracks());
    }

    if (user.getLastLogin() > 0L) {
      updateOperations.set(UserKeys.lastLogin, user.getLastLogin());
    }

    if (isNotEmpty(user.getExternalUserId())) {
      updateOperations.set(UserKeys.externalUserId, user.getExternalUserId());
    }

    return updateUser(user.getUuid(), updateOperations);
  }

  @Override
  public User unlockUser(String email, String accountId) {
    User user = getUserByEmail(email);
    Preconditions.checkNotNull(user, "User cannot be null");
    Preconditions.checkNotNull(user.getDefaultAccountId(), "Default account can't be null for any user");
    if (!user.getDefaultAccountId().equals(accountId)) {
      throw new UnauthorizedException("User not authorized", USER);
    }
    UpdateOperations<User> operations = wingsPersistence.createUpdateOperations(User.class);
    setUnset(operations, UserKeys.userLocked, false);
    setUnset(operations, UserKeys.userLockoutInfo, new UserLockoutInfo());
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, user, Type.UNLOCK);
    log.info("Auditing unlocking of user={} in account={}", user.getName(), accountId);
    return updateUser(user.getUuid(), operations);
  }

  @Override
  public User applyUpdateOperations(User user, UpdateOperations<User> updateOperations) {
    wingsPersistence.update(user, updateOperations);
    evictUserFromCache(user.getUuid());
    return wingsPersistence.getWithAppId(User.class, user.getAppId(), user.getUuid());
  }

  private void auditUserAdditionAndRemovalFromUserGroups(
      String accountId, User user, List<UserGroup> userGroupMemberAdditions, List<UserGroup> userGroupMemberDeletions) {
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, user, Type.UPDATE);
    userGroupMemberAdditions.forEach(userGroupAdded
        -> auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, userGroupAdded, Type.ADD));
    userGroupMemberDeletions.forEach(userGroupDeleted
        -> auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, userGroupDeleted, Type.REMOVE));
  }

  @Override
  public User updateUserGroupsOfUser(
      String userId, List<UserGroup> userGroups, String accountId, boolean sendNotification) {
    User userFromDB = get(accountId, userId);
    List<UserGroup> oldUserGroups = userFromDB.getUserGroups();

    Set<String> oldUserGroupIds = oldUserGroups.stream().map(UserGroup::getUuid).collect(Collectors.toSet());
    Set<String> newUserGroupIds = userGroups.stream().map(UserGroup::getUuid).collect(Collectors.toSet());

    SetView<String> userGroupMemberDeletions = Sets.difference(oldUserGroupIds, newUserGroupIds);
    SetView<String> userGroupMemberAdditions = Sets.difference(newUserGroupIds, oldUserGroupIds);

    List<UserGroup> userGroupsAdded =
        addUserToUserGroups(accountId, userFromDB, userGroupMemberAdditions, sendNotification);
    List<UserGroup> userGroupsDeleted =
        removeUserFromUserGroups(accountId, userFromDB, userGroupMemberDeletions, false);

    if (!userGroupsAdded.isEmpty() || !userGroupsDeleted.isEmpty()) {
      auditUserAdditionAndRemovalFromUserGroups(accountId, userFromDB, userGroupsAdded, userGroupsDeleted);
    }

    authService.evictUserPermissionAndRestrictionCacheForAccount(accountId, Collections.singletonList(userId));
    return get(accountId, userId);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#list(software.wings.dl.PageRequest)
   */
  @Override
  public PageResponse<User> list(PageRequest<User> pageRequest, boolean loadUserGroups) {
    String accountId = null;
    SearchFilter searchFilter = pageRequest.getFilters()
                                    .stream()
                                    .filter(filter -> filter.getFieldName().equals(UserKeys.accounts))
                                    .findFirst()
                                    .orElse(null);

    if (searchFilter != null) {
      Account account = (Account) searchFilter.getFieldValues()[0];
      accountId = account.getUuid();
    }

    PageResponse<User> pageResponse = wingsPersistence.query(User.class, pageRequest);
    if (loadUserGroups) {
      loadUserGroupsForUsers(pageResponse.getResponse(), accountId);
    }
    if (isNotEmpty(pageResponse)) {
      userServiceHelper.processForSCIMUsers(accountId, pageResponse.getResponse(), CG);
    }
    return pageResponse;
  }
  private List<UserGroup> getUserGroupsOfAccount(String accountId) {
    PageRequest<UserGroup> req = aPageRequest()
                                     .withLimit(Long.toString(userGroupService.getCountOfUserGroups(accountId)))
                                     .addFilter(UserGroupKeys.accountId, EQ, accountId)
                                     .build();
    PageResponse<UserGroup> res = userGroupService.list(accountId, req, false, null, null);
    return res.getResponse();
  }

  private HashMultimap createUsergroupsOfUserMap(List<UserGroup> userGroupList) {
    Multimap<String, UserGroup> userUserGroupMap = HashMultimap.create();

    userGroupList.forEach(userGroup -> {
      List<String> memberIds = userGroup.getMemberIds();
      if (isEmpty(memberIds)) {
        return;
      }
      memberIds.forEach(userId -> userUserGroupMap.put(userId, userGroup));
    });
    return (HashMultimap) userUserGroupMap;
  }

  @Override
  public void loadUserGroupsForUsers(List<User> users, String accountId) {
    List<UserGroup> allUserGroupList = getUserGroupsOfAccount(accountId);
    if (isEmpty(allUserGroupList)) {
      return;
    }

    Multimap<String, UserGroup> userUserGroupMap = createUsergroupsOfUserMap(allUserGroupList);

    users.forEach(user -> {
      if (isUserInvitedToAccount(user, accountId)) {
        UserInvite userInvite = getInviteFromEmail(accountId, user.getEmail());
        if (userInvite == null) {
          user.setUserGroups(new ArrayList<>());
        } else {
          user.setUserGroups(userGroupService.getUserGroupSummary(userInvite.getUserGroups()));
        }
      } else {
        Collection<UserGroup> userGroups = userUserGroupMap.get(user.getUuid());
        if (isEmpty(userGroups)) {
          user.setUserGroups(new ArrayList<>());
        } else {
          user.setUserGroups(new ArrayList<>(userGroups));
        }
      }
    });
  }

  @Override
  public boolean isUserPartOfAnyUserGroupInCG(String userId, String accountId) {
    User user;
    try {
      user = get(userId);
    } catch (UnauthorizedException exception) {
      log.warn("User {}, is not found in Harness account {}", userId, accountId);
      return false;
    }

    List<UserGroup> allUserGroupList = getUserGroupsOfAccount(accountId);
    if (isEmpty(allUserGroupList)) {
      return false;
    }

    Multimap<String, UserGroup> userUserGroupMap = createUsergroupsOfUserMap(allUserGroupList);
    if (isUserInvitedToAccount(user, accountId)) {
      UserInvite userInvite = getInviteFromEmail(accountId, user.getEmail());
      if (userInvite == null) {
        return false;
      }
      return true;
    }
    Collection<UserGroup> userGroups = userUserGroupMap.get(user.getUuid());
    return !isEmpty(userGroups);
  }

  @Override
  public boolean delete(String accountId, String userId, Generation generation) {
    User user = get(userId);
    if (userServiceHelper.validationForUserAccountLevelDataFlow(user, accountId)) {
      if (CG.equals(generation)) {
        removeAllUserGroupsFromUser(user, accountId);
        removeUserFromThisGenInAccount(accountId, userId, generation, user);
        if (!userServiceHelper.isUserProvisionedInThisAccount(user, accountId)) {
          delete(accountId, userId);
        }
      } else if (NG.equals(generation) && isUserPresent(userId)) {
        removeUserFromThisGenInAccount(accountId, userId, generation, user);
        if (!userServiceHelper.isUserProvisionedInThisAccount(user, accountId)) {
          delete(accountId, userId);
        }
      }
      return true;
    } else {
      log.warn("FF PL_USER_ACCOUNT_LEVEL_DATA_FLOW is not enabled or Data is still not populated. No update done.");
      return false;
    }
  }

  private void removeUserFromThisGenInAccount(String accountId, String userId, Generation generation, User user) {
    UpdateOperations<User> updateOp = wingsPersistence.createUpdateOperations(User.class);
    userServiceHelper.removeUserProvisioningFromGenerationInAccount(accountId, user, updateOp, generation);
    updateUser(userId, updateOp);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#delete(java.lang.String)
   */
  @Override
  public void delete(String accountId, String userId) {
    if (featureFlagService.isNotEnabled(FeatureName.PL_USER_DELETION_V2, accountId)) {
      deleteInternal(accountId, userId, true, NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK);
    } else {
      User user = get(userId);
      if (!userServiceHelper.isUserActiveInNG(user, accountId)) {
        deleteInternal(accountId, userId, true, NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK);
      } else {
        log.warn("User is removed from all user groups in CG");
        removeAllUserGroupsFromUser(user, accountId);
        if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)) {
          removeUserFromThisGenInAccount(accountId, userId, CG, user);
        }
        log.error(
            "User {} cannot be deleted in CG, since it is active on NG in account {}", user.getEmail(), accountId);
      }
    }
  }

  @Override
  public void forceDelete(String accountId, String userId) {
    deleteInternal(accountId, userId, true, NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK);
  }

  private void deleteInternal(
      String accountId, String userId, boolean updateUsergroup, NGRemoveUserFilter removeUserFilter) {
    User user = get(userId);
    if (user.getAccounts() == null && user.getPendingAccounts() == null) {
      return;
    }
    // HAR-7189: If user removed, the corresponding user invite using the same email address should be removed.
    removeRelatedUserInvite(accountId, user.getEmail());

    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new io.harness.limits.Action(accountId, ActionType.CREATE_USER));

    AtomicBoolean isUserPartOfAccountInNG = new AtomicBoolean(false);
    isUserPartOfAccountInNG.set(userServiceHelper.isUserActiveInNG(user, accountId));

    LimitEnforcementUtils.withCounterDecrement(checker, () -> {
      List<Account> updatedActiveAccounts = userServiceHelper.updatedActiveAccounts(user, accountId);
      List<Account> updatedPendingAccounts = userServiceHelper.updatedPendingAccount(user, accountId);

      if (isUserPartOfAccountInNG.get()) {
        userServiceHelper.deleteUserFromNG(userId, accountId, removeUserFilter);
      }

      if (updateUsergroup) {
        removeAllUserGroupsFromUser(user, accountId);
      }

      if (updatedActiveAccounts.isEmpty() && updatedPendingAccounts.isEmpty()) {
        deleteUser(user);
        return;
      }

      String defaultAccountId = user.getDefaultAccountId();
      if (defaultAccountId != null && defaultAccountId.equals(accountId)) {
        defaultAccountId = user.getDefaultAccountCandidate();
      }

      Query<Role> query = roleService.getAccountRolesQuery(accountId);
      query.limit(NO_LIMIT);
      List<Role> updatedRolesForUser = new ArrayList<>(user.getRoles());
      try (HIterator<Role> accountRoles = new HIterator<>(query.fetch())) {
        for (Role role : accountRoles) {
          updatedRolesForUser.remove(role);
        }
      }

      UpdateOperations<User> updateOp = wingsPersistence.createUpdateOperations(User.class)
                                            .set(UserKeys.roles, updatedRolesForUser)
                                            .set(UserKeys.accounts, updatedActiveAccounts)
                                            .set(UserKeys.pendingAccounts, updatedPendingAccounts);
      if (defaultAccountId != null) {
        updateOp.set(UserKeys.defaultAccountId, defaultAccountId);
      }
      userServiceHelper.removeUserAccountLevelDataForThisAccount(accountId, user, updateOp);

      updateUser(user.getUuid(), updateOp);
    });
    auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, user);
    log.info("Auditing deletion of user={} in account={}", user.getName(), accountId);
  }

  @Override
  public User updateUser(String userId, UpdateOperations<User> updateOperations) {
    User oldUser =
        wingsPersistence.findAndModify(wingsPersistence.createQuery(User.class).filter(BaseKeys.uuid, userId),
            updateOperations, HPersistence.returnOldOptions);
    User updatedUser = wingsPersistence.createQuery(User.class).filter(BaseKeys.uuid, userId).get();
    evictUserFromCache(userId);
    publishUserEvent(oldUser, updatedUser);
    return updatedUser;
  }

  @Override
  public void validateName(String name) {
    UserUtils.validateUserName(name);
  }

  private void deleteUser(User user) {
    User oldUser = wingsPersistence.findAndDelete(
        wingsPersistence.createQuery(User.class).filter(BaseKeys.uuid, user.getUuid()), HPersistence.returnOldOptions);
    if (oldUser != null) {
      evictUserFromCache(user.getUuid());
      publishUserEvent(oldUser, null);
    }
  }

  private void publishUserEvent(User oldUser, User updatedUser) {
    if (oldUser == null && updatedUser == null) {
      return;
    }
    UserDTO userDTO = null;
    boolean isUserCreated = oldUser == null;
    boolean isUserDeleted = updatedUser == null;
    String action;
    if (isUserCreated) {
      action = EventsFrameworkMetadataConstants.CREATE_ACTION;
    } else if (isUserDeleted) {
      action = EventsFrameworkMetadataConstants.DELETE_ACTION;
    } else {
      action = EventsFrameworkMetadataConstants.UPDATE_ACTION;
      /**
       * Dont send unnecessary events. Right now we only send events when username has changed or user is
       * created/deleted or user locked status has changed.
       */
      if (updatedUser.getName().equals(oldUser.getName()) && updatedUser.isUserLocked() == oldUser.isUserLocked()
          && updatedUser.isTwoFactorAuthenticationEnabled() == oldUser.isTwoFactorAuthenticationEnabled()) {
        return;
      }
    }

    UserDTO.Builder userDTOBuilder = UserDTO.newBuilder();
    userDTOBuilder.setUserId(updatedUser != null ? updatedUser.getUuid() : oldUser.getUuid());
    userDTOBuilder.setName(updatedUser != null ? updatedUser.getName() : oldUser.getName());
    userDTOBuilder.setEmail(updatedUser != null ? updatedUser.getEmail() : oldUser.getEmail());
    userDTOBuilder.setLocked(updatedUser != null ? updatedUser.isUserLocked() : oldUser.isUserLocked());
    userDTOBuilder.setIsTwoFactorAuthenticationEnabled(updatedUser != null
            ? updatedUser.isTwoFactorAuthenticationEnabled()
            : oldUser.isTwoFactorAuthenticationEnabled());
    userDTO = userDTOBuilder.build();

    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of(EventsFrameworkMetadataConstants.ENTITY_TYPE,
                  EventsFrameworkMetadataConstants.USER_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(userDTO.toByteString())
              .build());
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework for user [userId: {}", userDTO.getUserId(), e);
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#get(java.lang.String)
   */
  @Override
  public User get(String userId) {
    User user = wingsPersistence.get(User.class, userId);
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }

    List<Account> accounts = user.getAccounts();
    if (isNotEmpty(accounts)) {
      accounts.forEach(account -> software.wings.service.impl.LicenseUtils.decryptLicenseInfo(account, false));
    }

    return user;
  }

  @Override
  public boolean isUserPresent(String userId) {
    User user = wingsPersistence.get(User.class, userId);
    return user != null;
  }

  @Override
  public List<User> getUsers(List<String> userIds, String accountId) {
    Query<User> query = wingsPersistence.createQuery(User.class).field("uuid").in(userIds);
    query.or(query.criteria(UserKeys.accounts).hasThisOne(accountId),
        query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    return query.asList();
  }

  @Override
  public List<User> getUsers(Set<String> userIds) {
    Query<User> query = wingsPersistence.createQuery(User.class, excludeAuthority).field("uuid").in(userIds);
    return query.asList();
  }

  @Override
  public void loadSupportAccounts(User user) {
    loadSupportAccounts(user, Collections.EMPTY_SET);
  }

  @Override
  public void loadSupportAccounts(User user, Set<String> fieldsToBeIncluded) {
    if (user == null) {
      return;
    }

    if (harnessUserGroupService.isHarnessSupportUser(user.getUuid())) {
      Set<String> excludeAccounts = user.getAccounts().stream().map(Account::getUuid).collect(Collectors.toSet());
      List<Account> accountList =
          harnessUserGroupService.listAllowedSupportAccounts(excludeAccounts, fieldsToBeIncluded);

      Set<String> restrictedAccountsIds = accountService.getAccountsWithDisabledHarnessUserGroupAccess();
      restrictedAccountsIds.removeAll(excludeAccounts);

      List<Account> supportAccountList = new ArrayList<>();
      supportAccountList.addAll(accountList);
      if (isNotEmpty(restrictedAccountsIds)) {
        Set<Account> restrictedAccountsWithActiveAccessRequest =
            getRestrictedAccountsWithActiveAccessRequest(restrictedAccountsIds, user.getUuid());
        if (isNotEmpty(restrictedAccountsWithActiveAccessRequest)) {
          restrictedAccountsWithActiveAccessRequest.forEach(account -> supportAccountList.add(account));
        }
      }
      user.setSupportAccounts(supportAccountList);
    }
  }

  public boolean ifUserHasAccessToSupportAccount(String userId, String accountId) {
    if (isNotEmpty(userId) && isNotEmpty(accountId) && harnessUserGroupService.isHarnessSupportUser(userId)
        && !accountService.isHarnessSupportAccessDisabled(accountId)) {
      return true;
    } else if (isNotEmpty(getRestrictedAccountsWithActiveAccessRequest(Set.of(accountId), userId))) {
      return true;
    }
    return false;
  }

  private Set<Account> getRestrictedAccountsWithActiveAccessRequest(Set<String> restrictedAccountIds, String userId) {
    Set<Account> accountSet = new HashSet<>();
    restrictedAccountIds.forEach(restrictedAccountId -> {
      List<AccessRequest> accessRequestList =
          accessRequestService.getActiveAccessRequestForAccount(restrictedAccountId);
      if (isNotEmpty(accessRequestList)) {
        accessRequestList.forEach(accessRequest -> {
          if (AccessRequest.AccessType.MEMBER_ACCESS.equals(accessRequest.getAccessType())) {
            if (isNotEmpty(accessRequest.getMemberIds()) && accessRequest.getMemberIds().contains(userId)) {
              accountSet.add(accountService.get(restrictedAccountId));
            }
          } else {
            HarnessUserGroup harnessUserGroup = harnessUserGroupService.get(accessRequest.getHarnessUserGroupId());
            if (harnessUserGroup != null && isNotEmpty(harnessUserGroup.getMemberIds())
                && harnessUserGroup.getMemberIds().contains(userId)) {
              accountSet.add(accountService.get(restrictedAccountId));
            }
          }
        });
      }
    });

    return accountSet;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#get(java.lang.String, java.lang.String)
   */
  @Override
  public User get(String accountId, String userId) {
    User user = wingsPersistence.get(User.class, userId);
    boolean userBelongsToAccount = true;
    if (user != null) {
      userBelongsToAccount = user.getAccounts().stream().anyMatch(acc -> acc.getUuid().equals(accountId));
    }
    if (user == null || !userBelongsToAccount) {
      throw new InvalidRequestException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }
    loadUserGroups(accountId, user);
    return user;
  }

  @Override
  public User getUserFromCacheOrDB(String userId) {
    Cache<String, User> userCache = getUserCache();
    User user = null;
    try {
      user = userCache.get(userId);
    } catch (Exception ex) {
      log.error("Exception occurred while loading User from cache", ex);
    }
    if (user == null) {
      log.info("User [{}] not found in Cache. Load it from DB", userId);
      user = get(userId);
      try {
        userCache.put(user.getUuid(), user);
      } catch (Exception ex) {
        log.error("Exception occurred while putting User from DB to cache", ex);
      }
    }

    return user;
  }

  @Override
  public void evictUserFromCache(String userId) {
    getUserCache().remove(userId);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#addRole(java.lang.String, java.lang.String)
   */
  @Override
  public User addRole(String userId, String roleId) {
    ensureUserExists(userId);
    Role role = ensureRolePresent(roleId);

    UpdateOperations<User> updateOp =
        wingsPersistence.createUpdateOperations(User.class).addToSet(UserKeys.roles, role);
    Query<User> updateQuery = wingsPersistence.createQuery(User.class).filter(ID_KEY, userId);
    wingsPersistence.update(updateQuery, updateOp);
    evictUserFromCache(userId);
    return wingsPersistence.get(User.class, userId);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.UserService#revokeRole(java.lang.String, java.lang.String)
   */
  @Override
  public User revokeRole(String userId, String roleId) {
    ensureUserExists(userId);
    Role role = ensureRolePresent(roleId);

    UpdateOperations<User> updateOp =
        wingsPersistence.createUpdateOperations(User.class).removeAll(UserKeys.roles, role);
    Query<User> updateQuery = wingsPersistence.createQuery(User.class).filter(ID_KEY, userId);
    wingsPersistence.update(updateQuery, updateOp);
    evictUserFromCache(userId);
    return wingsPersistence.get(User.class, userId);
  }

  @Override
  public AccountRole getUserAccountRole(String userId, String accountId) {
    Account account = accountService.get(accountId);
    if (account == null) {
      String message = "Account [" + accountId + "] does not exist";
      log.warn(message);
      throw new GeneralException(message);
    }
    User user = get(userId);

    if (user.isAccountAdmin(accountId)) {
      ImmutableList.Builder<ImmutablePair<ResourceType, Action>> builder = ImmutableList.builder();
      for (ResourceType resourceType : ResourceType.values()) {
        for (Action action : Action.values()) {
          builder.add(ImmutablePair.of(resourceType, action));
        }
      }
      return anAccountRole()
          .withAccountId(accountId)
          .withAccountName(account.getAccountName())
          .withAllApps(true)
          .withResourceAccess(builder.build())
          .build();

    } else if (user.isAllAppAdmin(accountId)) {
      ImmutableList.Builder<ImmutablePair<ResourceType, Action>> builder = ImmutableList.builder();
      for (ResourceType resourceType : asList(APPLICATION, SERVICE, ARTIFACT, DEPLOYMENT, WORKFLOW, ENVIRONMENT)) {
        for (Action action : Action.values()) {
          builder.add(ImmutablePair.of(resourceType, action));
        }
      }
      return anAccountRole()
          .withAccountId(accountId)
          .withAccountName(account.getAccountName())
          .withAllApps(true)
          .withResourceAccess(builder.build())
          .build();
    }
    return null;
  }

  @Override
  public ApplicationRole getUserApplicationRole(String userId, String appId) {
    Application application = appService.get(appId);
    User user = get(userId);
    if (user.isAccountAdmin(application.getAccountId()) || user.isAppAdmin(application.getAccountId(), appId)) {
      ImmutableList.Builder<ImmutablePair<ResourceType, Action>> builder = ImmutableList.builder();
      for (ResourceType resourceType : asList(APPLICATION, SERVICE, ARTIFACT, DEPLOYMENT, WORKFLOW, ENVIRONMENT)) {
        for (Action action : Action.values()) {
          builder.add(ImmutablePair.of(resourceType, action));
        }
      }
      return anApplicationRole()
          .withAppId(appId)
          .withAppName(application.getName())
          .withAllEnvironments(true)
          .withResourceAccess(builder.build())
          .build();
    }
    // TODO - for Prod support and non prod support
    return null;
  }

  @Override
  public ZendeskSsoLoginResponse generateZendeskSsoJwt(String returnToUrl) {
    String jwtZendeskSecret = configuration.getPortal().getJwtZendeskSecret();
    if (jwtZendeskSecret == null) {
      throw new InvalidRequestException("Request can not be completed. No Zendesk SSO secret found.");
    }

    User user = UserThreadLocal.get();

    // Given a user instance
    JWTClaimsSet jwtClaims = new JWTClaimsSet.Builder()
                                 .issueTime(new Date())
                                 .jwtID(UUID.randomUUID().toString())
                                 .claim(UserKeys.name, user.getName())
                                 .claim(UserKeys.email, user.getEmail())
                                 .build();

    // Create JWS header with HS256 algorithm
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).contentType("text/plain").build();

    // Create JWS object
    JWSObject jwsObject = new JWSObject(header, new Payload(jwtClaims.toJSONObject()));

    try {
      // Create HMAC signer
      JWSSigner signer = new MACSigner(jwtZendeskSecret.getBytes(UTF_8));
      jwsObject.sign(signer);
    } catch (com.nimbusds.jose.JOSEException e) {
      throw new InvalidRequestException("Error signing JWT: " + ExceptionUtils.getMessage(e));
    }

    // Serialise to JWT compact form
    String jwtString = jwsObject.serialize();

    String redirectUrl =
        String.format("%s/access/jwt?jwt=%s", configuration.getPortal().getZendeskBaseUrl(), jwtString);

    if (returnToUrl != null) {
      redirectUrl += "&return_to=" + returnToUrl;
    }
    return ZendeskSsoLoginResponse.builder().redirectUrl(redirectUrl).userId(user.getUuid()).build();
  }

  @Override
  public CannySsoLoginResponse generateCannySsoJwt(String returnToUrl, String companyID) {
    User user = UserThreadLocal.get();
    String jwtToken = createCannyToken(user);

    String redirectUrl =
        String.format("%s?companyID=%s&ssoToken=%s", configuration.getPortal().getCannyBaseUrl(), companyID, jwtToken);

    if (StringUtils.isNotEmpty(returnToUrl)) {
      redirectUrl += "&redirect=" + returnToUrl;
    }
    log.info("Canny login: successfully created jwt token and redirect URL for user {}", user.getUuid());
    return CannySsoLoginResponse.builder().redirectUrl(redirectUrl).userId(user.getUuid()).build();
  }

  private String createCannyToken(User user) {
    String jwtCannySecret = configuration.getPortal().getJwtCannySecret();

    if (StringUtils.isEmpty(jwtCannySecret)) {
      String errorMessage = "Canny secret is either null or empty.";
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    HashMap<String, Object> userData = new HashMap<>();
    userData.put(UserKeys.email, user.getEmail());
    userData.put("id", user.getUuid());
    userData.put(UserKeys.name, user.getName());
    userData.put(UserKeys.companyName, user.getCompanyName());

    byte[] jwtCannySecretBytes;
    try {
      jwtCannySecretBytes = jwtCannySecret.getBytes("UTF-8");
    } catch (UnsupportedEncodingException ex) {
      String errorMessage = "Error while encoding the canny secret to bytes";
      log.error(errorMessage, ex);
      throw new InvalidRequestException(errorMessage, ex);
    }

    return Jwts.builder()
        .setIssuedAt(new Date())
        .setClaims(userData)
        .signWith(SignatureAlgorithm.HS256, jwtCannySecretBytes)
        .compact();
  }

  private Role ensureRolePresent(String roleId) {
    Role role = roleService.get(roleId);
    if (role == null) {
      throw new GeneralException("Roles does not exist");
    }
    return role;
  }

  private void ensureUserExists(String userId) {
    User user = wingsPersistence.get(User.class, userId);
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    }
  }

  private void addAccountAdminRole(User existingUser, Account account) {
    addAccountRoles(existingUser, account, newArrayList(roleService.getAccountAdminRole(account.getUuid())));
  }

  private void addAccountRoles(User existingUser, Account account, List<Role> roles) {
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    if (isNotEmpty(roles)) {
      updateOperations.addToSet("roles", roles);
    }
    if (account != null) {
      updateOperations.addToSet(UserKeys.accounts, account.getUuid());
    }
    updateUser(existingUser.getUuid(), updateOperations);
  }

  private Account setupTrialAccount(String accountName, String companyName) {
    Account.Builder accountBuilder = Account.Builder.anAccount()
                                         .withAccountName(accountName)
                                         .withCompanyName(companyName)
                                         .withLicenseInfo(LicenseInfo.builder().accountType(AccountType.TRIAL).build());
    return setupAccount(accountBuilder.build(), false);
  }

  private Account setupAccount(Account account) {
    return setupAccount(account, true);
  }

  private Account setupNGAccount(Account account) {
    account.setAppId(GLOBAL_APP_ID);
    account.setDefaultExperience(DefaultExperience.NG);
    account.setCreatedFromNG(true);
    account.setProductLed(true);

    Account savedAccount = accountService.save(account, false, false);
    log.info("New account created with accountId {} and licenseType {}", account.getUuid(),
        account.getLicenseInfo().getAccountType());
    return savedAccount;
  }

  private Account setupAccount(Account account, boolean shouldCreateSampleApp) {
    // HAR-8645: Always set default appId for account creation to pass validation
    account.setAppId(GLOBAL_APP_ID);
    account.setDefaultExperience(DefaultExperience.CG);
    Account savedAccount = accountService.save(account, false, shouldCreateSampleApp);
    log.info("New account created with accountId {} and licenseType {}", account.getUuid(),
        account.getLicenseInfo().getAccountType());
    return savedAccount;
  }

  private List<UserGroup> getAccountAdminGroup(String accountId) {
    PageRequest<UserGroup> pageRequest =
        aPageRequest()
            .addFilter(UserGroup.ACCOUNT_ID_KEY, EQ, accountId)
            .addFilter(UserGroupKeys.name, EQ, UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME)
            .build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, true, null, null);
    return pageResponse.getResponse();
  }

  public Map<String, String> populateUserPrincipalClaim(
      User user, Map<String, String> claims, boolean persistOldAccountId) {
    String oldAccountId = claims.get(ACCOUNT_ID_KEY);
    // User Principal needed in token for environments without gateway as this token will be sent back to different
    // microservices
    addUserPrincipal(claims, user);
    if (persistOldAccountId && isNotEmpty(oldAccountId)) {
      claims.put(ACCOUNT_ID_KEY, oldAccountId);
    }
    return claims;
  }

  @Override
  public String generateJWTToken(
      User user, Map<String, String> claims, JWT_CATEGORY category, boolean persistOldAccountId) {
    String jwtPasswordSecret = secretManager.getJWTSecret(category);
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTCreator.Builder jwtBuilder =
          JWT.create()
              .withIssuer(HARNESS_ISSUER)
              .withIssuedAt(new Date())
              .withExpiresAt(new Date(System.currentTimeMillis() + category.getValidityDuration()));
      claims = populateUserPrincipalClaim(user, claims, persistOldAccountId);
      if (claims != null && claims.size() > 0) {
        claims.forEach(jwtBuilder::withClaim);
      }
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException("JWTToken could not be generated");
    }
  }

  private void addUserPrincipal(Map<String, String> claims, User user) {
    UserPrincipal userPrincipal =
        new UserPrincipal(user.getUuid(), user.getEmail(), user.getName(), user.getDefaultAccountId());
    Map<String, String> userClaims = userPrincipal.getJWTClaims();
    if (userClaims != null) {
      claims.putAll(userClaims);
    }
  }

  @Override
  public User verifyJWTToken(String jwtToken, JWT_CATEGORY category) {
    String jwtPasswordSecret = secretManager.getJWTSecret(category);
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }

    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtPasswordSecret);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(HARNESS_ISSUER).build();
      verifier.verify(jwtToken);
      return getUserByEmail(JWT.decode(jwtToken).getClaim("email").asString());
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new GeneralException("JWTToken validation failed");
    } catch (JWTDecodeException | SignatureVerificationException e) {
      throw new InvalidCredentialsException("Invalid JWTToken received, failed to decode the token", USER);
    }
  }

  private boolean isUserAdminOfAnyAccount(User user) {
    return user.getAccounts().stream().anyMatch(account -> {
      List<UserGroup> userGroupList = userGroupService.listByAccountId(account.getUuid(), user, true);
      if (isNotEmpty(userGroupList)) {
        return userGroupList.stream().anyMatch(userGroup -> {
          AccountPermissions accountPermissions = userGroup.getAccountPermissions();
          if (accountPermissions != null) {
            Set<PermissionType> permissions = accountPermissions.getPermissions();
            if (isNotEmpty(permissions)) {
              return permissions.contains(PermissionType.USER_PERMISSION_MANAGEMENT);
            }
          }
          return false;
        });
      }
      return false;
    });
  }

  @Override
  public boolean isAccountAdmin(String accountId) {
    User user = UserThreadLocal.get();
    if (user == null) {
      return true;
    }
    return isAccountAdmin(user, accountId);
  }

  @Override
  public boolean isAccountAdmin(User user, String accountId) {
    UserRequestContext userRequestContext = user.getUserRequestContext();
    if (userRequestContext == null) {
      return true;
    }

    if (!accountId.equals(userRequestContext.getAccountId())) {
      return false;
    }

    UserPermissionInfo userPermissionInfo = userRequestContext.getUserPermissionInfo();
    return isUserAccountAdmin(userPermissionInfo, accountId);
  }

  @Override
  public boolean isUserAccountAdmin(UserPermissionInfo userPermissionInfo, String accountId) {
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    if (accountPermissionSummary == null) {
      return false;
    }
    Set<PermissionType> permissions = accountPermissionSummary.getPermissions();
    if (isEmpty(permissions)) {
      return false;
    }
    return permissions.contains(PermissionType.ACCOUNT_MANAGEMENT);
  }

  @Override
  public boolean isUserAssignedToAccountInGeneration(User user, String accountId, Generation generation) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)
        && userServiceHelper.validationForUserAccountLevelDataFlow(user, accountId)) {
      if (user.getUserAccountLevelDataMap().get(accountId).getUserProvisionedTo().contains(generation)) {
        return true;
      } else {
        return false;
      }
    }
    return isUserAssignedToAccount(user, accountId);
  }
  @Override
  public boolean isUserAssignedToAccount(User user, String accountId) {
    return user != null && isNotEmpty(user.getAccounts())
        && user.getAccounts().stream().anyMatch(account -> account.getUuid().equals(accountId));
  }

  @Override
  public boolean isUserInvitedToAccount(User user, String accountId) {
    return user != null && isNotEmpty(user.getPendingAccounts())
        && user.getPendingAccounts().stream().anyMatch(account -> account.getUuid().equals(accountId));
  }

  @Override
  public boolean isUserVerified(User user) {
    if (isNotEmpty(user.getAccounts())) {
      AuthenticationMechanism authenticationMechanism = getPrimaryAccount(user).getAuthenticationMechanism();
      return (authenticationMechanism != null && authenticationMechanism.getType().equals("SSO"))
          || user.isEmailVerified();
    }
    log.warn("User {} has no accounts associated", user.getEmail());
    return false;
  }

  @Override
  public List<User> getUsersOfAccount(String accountId) {
    Account account = accountService.get(accountId);
    PageRequest<User> pageRequest = aPageRequest().addFilter(UserKeys.accounts, HAS, account).build();
    PageResponse<User> pageResponse = wingsPersistence.query(User.class, pageRequest);
    return pageResponse.getResponse();
  }

  @Override
  public List<User> getUsersWithThisAsPrimaryAccount(String accountId) {
    return getUsersOfAccount(accountId)
        .stream()
        .filter(u -> u.getAccounts().get(0).getUuid().equals(accountId))
        .collect(Collectors.toList());
  }

  @Override
  public AuthenticationMechanism getAuthenticationMechanism(User user) {
    return getPrimaryAccount(user).getAuthenticationMechanism();
  }

  @Override
  public boolean setDefaultAccount(User user, String accountId) {
    // First we have to make sure the user is assigned to the account
    boolean userAssignedToAccount = isUserAssignedToAccount(user, accountId);
    if (userAssignedToAccount) {
      user.setDefaultAccountId(accountId);
      wingsPersistence.save(user);
      return true;
    } else {
      throw new InvalidRequestException("Can't set default account if the user is not associated with the account");
    }
  }

  @Override
  public void deleteByAccountId(String accountId) {
    Query<User> query = wingsPersistence.createQuery(User.class);
    query.or(query.criteria(UserKeys.accounts).hasThisOne(accountId),
        query.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    List<User> users = query.asList();
    for (User user : users) {
      deleteInternal(accountId, user.getUuid(), false, NGRemoveUserFilter.STRICTLY_FORCE_REMOVE_USER);
    }
  }

  @Override
  public boolean updateLead(String email, String accountId) {
    User user = getUserByEmail(email);
    executorService.submit(() -> {
      try {
        Thread.sleep(30000);
      } catch (InterruptedException e) {
      }
      eventPublishHelper.publishUserRegistrationCompletionEvent(accountId, user);
    });
    return true;
  }

  @Override
  public boolean deleteUsersByEmailAddress(String accountId, List<String> usersToRetain) {
    if (CollectionUtils.isEmpty(usersToRetain)) {
      throw new IllegalArgumentException("'usersToRetain' is empty");
    }

    Set<String> usersToDelete = getUsersOfAccount(accountId)
                                    .stream()
                                    .filter(user -> !usersToRetain.contains(user.getEmail()))
                                    .map(UuidAware::getUuid)
                                    .collect(toSet());

    for (String userToDelete : usersToDelete) {
      if (featureFlagService.isEnabled(FeatureName.PL_USER_DELETION_V2, accountId)) {
        forceDelete(accountId, userToDelete);
      } else {
        delete(accountId, userToDelete);
      }
      delete(accountId, userToDelete);
    }

    return true;
  }

  @Override
  public boolean enableUser(String accountId, String userId, boolean enabled) {
    User user = wingsPersistence.get(User.class, userId);
    if (user == null) {
      throw new UnauthorizedException(EXC_MSG_USER_DOESNT_EXIST, USER);
    } else if (!isUserAssignedToAccount(user, accountId)) {
      throw new InvalidRequestException("User is not assigned to account", USER);
    }
    if (user.isDisabled() == enabled) {
      user.setDisabled(!enabled);
      wingsPersistence.save(user);
      evictUserFromCache(user.getUuid());
      log.info("User {} is enabled: {}", user.getEmail(), enabled);
    }
    return true;
  }

  @Override
  public void sendPasswordExpirationWarning(String email, Integer passExpirationDays) {
    User user = getUserByEmail(email);

    if (user == null) {
      throw new InvalidRequestException(
          String.format("Email [%s] exist while sending mail for password expiration.", email));
    }

    String jwtPasswordSecret = getJwtSecret();

    try {
      String token = signupService.createSignupTokenFromSecret(jwtPasswordSecret, email, 1);
      sendPasswordExpirationWarningMail(user, token, passExpirationDays);
    } catch (JWTCreationException | UnsupportedEncodingException exception) {
      throw new GeneralException(EXC_MSG_RESET_PASS_LINK_NOT_GEN);
    }
  }

  @Override
  public String createSignupSecretToken(String email, Integer passExpirationDays) {
    String jwtPasswordSecret = getJwtSecret();
    try {
      return signupService.createSignupTokenFromSecret(jwtPasswordSecret, email, passExpirationDays);
    } catch (JWTCreationException | UnsupportedEncodingException exception) {
      throw new SignupException("Signup secret token can't be generated");
    }
  }

  private void sendPasswordExpirationWarningMail(User user, String token, Integer passExpirationDays) {
    try {
      // @Todo(Raj): Fix isNgRequest url
      String resetPasswordUrl = getResetPasswordUrl(token, user, false);

      Map<String, String> templateModel = getTemplateModel(user.getName(), resetPasswordUrl);
      templateModel.put("passExpirationDays", passExpirationDays.toString());

      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName("password_expiration_warning")
                                .templateModel(templateModel)
                                .accountId(getPrimaryAccount(user).getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error(RESET_ERROR, e);
    }
  }

  @Override
  public void sendPasswordExpirationMail(String email) {
    User user = getUserByEmail(email);

    if (user == null) {
      throw new InvalidRequestException(
          String.format("Email [%s] exist while sending mail for password expiration.", email));
    }

    String jwtPasswordSecret = getJwtSecret();

    try {
      String token = signupService.createSignupTokenFromSecret(jwtPasswordSecret, email, 1);
      sendPasswordExpirationMail(user, token);
    } catch (JWTCreationException | UnsupportedEncodingException exception) {
      throw new GeneralException(EXC_MSG_RESET_PASS_LINK_NOT_GEN);
    }
  }

  private void sendPasswordExpirationMail(User user, String token) {
    try {
      // @Todo (Raj): Fix isNGrequest
      String resetPasswordUrl = getResetPasswordUrl(token, user, false);

      Map<String, String> templateModel = getTemplateModel(user.getName(), resetPasswordUrl);

      List<String> toList = new ArrayList<>();
      toList.add(user.getEmail());
      EmailData emailData = EmailData.builder()
                                .to(toList)
                                .templateName("password_expiration")
                                .templateModel(templateModel)
                                .accountId(getPrimaryAccount(user).getUuid())
                                .build();
      emailData.setCc(Collections.emptyList());
      emailData.setRetries(2);

      emailNotificationService.send(emailData);
    } catch (URISyntaxException e) {
      log.error(RESET_ERROR, e);
    }
  }

  private String getJwtSecret() {
    String jwtPasswordSecret = configuration.getPortal().getJwtPasswordSecret();
    if (jwtPasswordSecret == null) {
      throw new InvalidRequestException(INCORRECT_PORTAL_SETUP);
    }
    return jwtPasswordSecret;
  }

  @Override
  public boolean passwordExpired(String email) {
    User user = getUserByEmail(email);
    Preconditions.checkNotNull(user, "User is null");
    return user.isPasswordExpired();
  }

  @Override
  public PasswordStrengthViolations checkPasswordViolations(
      String token, PasswordSource passwordSource, String password, String accountId) {
    Account account = null;
    try {
      if (!isBlank(accountId)) {
        account = accountService.get(accountId);
      } else if (PasswordSource.PASSWORD_RESET_FLOW == passwordSource) {
        account = getAccountFromResetPasswordToken(token);
      } else if (PasswordSource.SIGN_UP_FLOW == passwordSource) {
        account = getAccountFromInviteId(token);
      } else {
        throw new InvalidRequestException("Incorrect password source provided.", USER);
      }
      return loginSettingsService.getPasswordStrengthCheckViolations(
          account, EncodingUtils.decodeBase64ToString(password).toCharArray());
    } catch (Exception ex) {
      log.warn("Password violation polling failed for token: [{}]", token, ex);
      throw new InvalidRequestException("Password violation polling failed", USER);
    }
  }

  private Account getAccountFromInviteId(String inviteId) {
    UserInvite userInvite = wingsPersistence.createQuery(UserInvite.class).filter("_id", inviteId).get();
    return accountService.get(userInvite.getAccountId());
  }

  private Account getAccountFromResetPasswordToken(String resetPasswordToken) {
    User user = verifyJWTToken(resetPasswordToken, JWT_CATEGORY.PASSWORD_SECRET);
    // Adding this check for infer checks, this condition won't happen, as if the user is null
    // verifyJWTToken will throw a exception
    if (user == null) {
      return null;
    }
    return accountService.get(user.getDefaultAccountId());
  }

  @Override
  public void sendAccountLockedNotificationMail(User user, int lockoutExpirationTime) {
    Map<String, String> templateModel = new HashMap<>();
    templateModel.put("name", sanitizeUserName(user.getName()));
    templateModel.put("lockoutExpirationTime", Integer.toString(lockoutExpirationTime));

    List<String> toList = new ArrayList<>();
    toList.add(user.getEmail());

    EmailData emailData = EmailData.builder()
                              .to(toList)
                              .templateName("user_account_locked_notification")
                              .templateModel(templateModel)
                              .accountId(getPrimaryAccount(user).getUuid())
                              .build();

    emailData.setCc(Collections.emptyList());
    emailData.setRetries(2);

    emailNotificationService.send(emailData);
  }

  @Override
  public Account getAccountByIdIfExistsElseGetDefaultAccount(User user, Optional<String> accountId) {
    if (accountId.isPresent()) {
      // First check if the user is associated with the account.
      if (!isUserAssignedToAccount(user, accountId.get())) {
        throw new InvalidRequestException("User is not assigned to account", USER);
      }
      return accountService.get(accountId.get());
    } else {
      Account defaultAccount = accountService.get(user.getDefaultAccountId());
      if (null == defaultAccount) {
        return getPrimaryAccount(user);
      } else {
        return defaultAccount;
      }
    }
  }

  /**
   * User can NOT be disabled/enabled in account status change only when:
   * 1. User belongs to multiple accounts
   * 2. User belongs to Harness user group
   */
  @Override
  public boolean canEnableOrDisable(User user) {
    String email = user.getEmail();
    boolean associatedWithMultipleAccounts = user.getAccounts().size() > 1;
    log.info("User {} is associated with {} accounts", email, user.getAccounts().size());
    boolean isHarnessUser = harnessUserGroupService.isHarnessSupportUser(user.getUuid());
    log.info("User {} is in harness user group: {}", email, isHarnessUser);
    boolean result = !(associatedWithMultipleAccounts || isHarnessUser);
    log.info("User {} can be set to new disabled status: {}", email, result);
    return result;
  }

  @Override
  public String saveUserInvite(UserInvite userInvite) {
    return wingsPersistence.save(userInvite);
  }

  public List<User> listUsers(PageRequest pageRequest, String accountId, String searchTerm, Integer offset,
      Integer pageSize, boolean loadUserGroups, boolean includeUsersPendingInviteAcceptance, boolean includeDisabled,
      boolean filterForGeneration) {
    Query<User> query;
    if (isNotEmpty(searchTerm)) {
      query = getSearchUserQuery(accountId, searchTerm, includeUsersPendingInviteAcceptance, includeDisabled);
    } else {
      query = getListUserQuery(accountId, includeUsersPendingInviteAcceptance, includeDisabled);
    }

    applySortFilter(pageRequest, query);
    FindOptions findOptions = new FindOptions().skip(offset).limit(pageSize);
    List<User> userList = query.asList(findOptions);
    if (filterForGeneration) {
      filterListForGeneration(accountId, userList, CG);
      userServiceHelper.processForSCIMUsers(accountId, userList, CG);
    }
    if (loadUserGroups) {
      loadUserGroupsForUsers(userList, accountId);
    }

    return userList;
  }

  private void applySortFilter(PageRequest pageRequest, Query<User> query) {
    UriInfo uriInfo = pageRequest.getUriInfo();
    if (null != uriInfo && isNotEmpty(uriInfo.getQueryParameters(true))) {
      List<String> fieldToSort = uriInfo.getQueryParameters(true).get("sort[0][field]");
      if (fieldToSort == null) {
        return;
      }
      if (uriInfo.getQueryParameters(true).get("sort[0][direction]").get(0).equals("ASC")) {
        query.order(Sort.ascending(fieldToSort.get(0)));
      } else {
        query.order(Sort.descending(fieldToSort.get(0)));
      }
    }
  }

  public long getTotalUserCount(String accountId, boolean includeUsersPendingInviteAcceptance, boolean excludeDisabled,
      boolean filterForGeneration) {
    Query<User> query = getListUserQuery(accountId, includeUsersPendingInviteAcceptance, !excludeDisabled);
    if (filterForGeneration) {
      queryFilterOnlyCGUsers(accountId, query);
    }
    return query.count();
  }

  private Query<User> getListUserQuery(
      String accountId, boolean includeUsersPendingInviteAcceptance, boolean includeDisabled) {
    Query<User> listUserQuery = wingsPersistence.createQuery(User.class, excludeAuthority);

    if (includeUsersPendingInviteAcceptance) {
      listUserQuery.or(listUserQuery.criteria(UserKeys.accounts).hasThisOne(accountId),
          listUserQuery.criteria(UserKeys.pendingAccounts).hasThisOne(accountId));
    } else {
      listUserQuery.criteria(UserKeys.accounts).hasThisOne(accountId);
    }
    listUserQuery.order(Sort.descending("lastUpdatedAt"));

    if (!includeDisabled) {
      listUserQuery.criteria(UserKeys.disabled).notEqual(true);
    }
    return listUserQuery;
  }

  @VisibleForTesting
  Query<User> getSearchUserQuery(
      String accountId, String searchTerm, boolean includeUsersPendingInviteAcceptance, boolean includeDisabled) {
    Query<User> query = wingsPersistence.createQuery(User.class, excludeAuthority);

    if (includeUsersPendingInviteAcceptance) {
      query.or(buildAccountsCriterion(query, accountId, searchTerm, includeDisabled),
          buildPendingAccountsCriterion(query, accountId, searchTerm, includeDisabled));
    } else {
      buildAccountsCriterion(query, accountId, searchTerm, includeDisabled);
    }
    return query;
  }

  private Criteria buildPendingAccountsCriterion(
      Query<User> query, String accountId, String searchTerm, boolean includeDisabled) {
    if (includeDisabled) {
      return query.and(query.criteria(UserKeys.pendingAccounts).equal(accountId),
          query.criteria(UserKeys.email).containsIgnoreCase(searchTerm));
    } else {
      return query.and(query.criteria(UserKeys.pendingAccounts).equal(accountId),
          query.criteria(UserKeys.email).containsIgnoreCase(searchTerm),
          query.criteria(UserKeys.disabled).notEqual(true));
    }
  }

  private Criteria buildAccountsCriterion(
      Query<User> query, String accountId, String searchTerm, boolean includeDisabled) {
    if (includeDisabled) {
      return query.and(query.criteria(UserKeys.accounts).equal(accountId),
          query.or(query.criteria(UserKeys.name).containsIgnoreCase(searchTerm),
              query.criteria(UserKeys.email).containsIgnoreCase(searchTerm)));
    } else {
      return query.and(query.criteria(UserKeys.accounts).equal(accountId),
          query.or(query.criteria(UserKeys.name).containsIgnoreCase(searchTerm),
              query.criteria(UserKeys.email).containsIgnoreCase(searchTerm)),
          query.criteria(UserKeys.disabled).notEqual(true));
    }
  }

  private CriteriaContainer getSearchCriterion(Query<?> query, String fieldName, String searchTerm) {
    return query.criteria(fieldName).startsWithIgnoreCase(searchTerm);
  }

  @Override
  public InviteOperationResponse checkInviteStatus(UserInvite userInvite, Generation gen) {
    if (gen != null && gen.equals(NG)) {
      return checkNgInviteStatus(userInvite);
    }

    UserInvite existingInvite = getInvite(userInvite.getUuid());
    if (existingInvite == null) {
      return INVITE_INVALID;
    }
    User user = getUserByEmail(existingInvite.getEmail());
    Account account = accountService.get(userInvite.getAccountId());

    if (account == null || user == null) {
      log.error("User or account does not exist - " + userInvite.getAccountId());
      return INVITE_INVALID;
    }

    if (existingInvite.isCompleted()) {
      return USER_ALREADY_ADDED;
    }

    eventPublishHelper.publishUserInviteVerifiedFromAccountEvent(account.getUuid(), user.getEmail());

    AuthenticationMechanism authMechanism = account.getAuthenticationMechanism();
    boolean isPasswordRequired =
        (authMechanism == null || authMechanism == USER_PASSWORD) && isEmpty(user.getPasswordHash());

    if (isPasswordRequired) {
      log.info("Redirecting invite id: {} to password signup page", existingInvite.getUuid());
      return ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD;
    } else {
      // Marking the user invite complete.
      moveAccountFromPendingToConfirmed(
          user, account, userGroupService.getUserGroupsFromUserInvite(existingInvite), true);
      markUserInviteComplete(existingInvite);
      return ACCOUNT_INVITE_ACCEPTED;
    }
  }

  private InviteOperationResponse checkNgInviteStatus(UserInvite userInvite) {
    InviteAcceptResponse inviteAcceptResponse = NGRestUtils.getResponse(ngInviteClient.accept(userInvite.getUuid()));
    if (inviteAcceptResponse.getResponse().equals(FAIL)) {
      return FAIL;
    }

    Account account = accountService.get(inviteAcceptResponse.getAccountIdentifier());
    AuthenticationMechanism authMechanism = account.getAuthenticationMechanism();
    boolean isPasswordRequired = authMechanism == null || authMechanism == USER_PASSWORD;

    UserInfo userInfo = inviteAcceptResponse.getUserInfo();
    if (userInfo == null) {
      if (isPasswordRequired) {
        return ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD;
      } else {
        String email = inviteAcceptResponse.getEmail();
        UserInviteDTO userInviteDTO = UserInviteDTO.builder()
                                          .accountId(userInvite.getAccountId())
                                          .email(email)
                                          .name(email.trim())
                                          .token(userInvite.getUuid())
                                          .build();
        completeNGInvite(userInviteDTO, false, true);
        return ACCOUNT_INVITE_ACCEPTED;
      }
    } else {
      User user = getUserByEmail(userInfo.getEmail());
      if (isPasswordRequired && isEmpty(user.getPasswordHash())) {
        return ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD;
      } else {
        NGRestUtils.getResponse(ngInviteClient.completeInvite(userInvite.getUuid()));
        return ACCOUNT_INVITE_ACCEPTED;
      }
    }
  }

  private void moveAccountFromPendingToConfirmed(
      User existingUser, Account invitationAccount, List<UserGroup> userGroups, boolean markEmailVerified) {
    addUserToUserGroups(invitationAccount.getUuid(), existingUser, userGroups, false, false);
    String invitationAccountId = invitationAccount.getUuid();
    List<Account> newAccountsList = new ArrayList<>(existingUser.getAccounts());
    if (newAccountsList.stream().map(Account::getUuid).noneMatch(invitationAccountId::equals)) {
      newAccountsList.add(invitationAccount);
    }

    List<Account> newPendingAccountsList = new ArrayList<>();
    if (existingUser.getPendingAccounts() != null) {
      for (Account pendingAccount : existingUser.getPendingAccounts()) {
        if (!pendingAccount.getUuid().equals(invitationAccount.getUuid())) {
          newPendingAccountsList.add(pendingAccount);
        }
      }
    }
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    updateOperations.set(UserKeys.accounts, newAccountsList);
    updateOperations.set(UserKeys.pendingAccounts, newPendingAccountsList);
    updateOperations.set(UserKeys.emailVerified, markEmailVerified);
    updateUser(existingUser.getUuid(), updateOperations);
  }

  private void markUserInviteComplete(UserInvite userInvite) {
    Map<String, Object> map = new HashMap<>();
    map.put(UserInviteKeys.completed, Boolean.TRUE);
    map.put(UserInviteKeys.agreement, userInvite.isAgreement());
    wingsPersistence.updateFields(UserInvite.class, userInvite.getUuid(), map);
  }

  private void completeUserInfo(UserInvite userInvite, User existingUser) {
    UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
    String userName = userInvite.getName().trim();
    validateName(userName);
    updateOperations.set(UserKeys.name, userName);
    updateOperations.set(UserKeys.passwordHash, hashpw(new String(userInvite.getPassword()), BCrypt.gensalt()));
    updateUser(existingUser.getUuid(), updateOperations);
  }

  public String setupAccountBasedOnProduct(User user, UserInvite userInvite, MarketPlace marketPlace) {
    String accountId;
    LicenseInfo licenseInfo = LicenseInfo.builder()
                                  .accountType(AccountType.PAID)
                                  .licenseUnits(marketPlace.getOrderQuantity())
                                  .expiryTime(marketPlace.getExpirationDate().getTime())
                                  .accountStatus(AccountStatus.ACTIVE)
                                  .build();

    String dimension = marketPlace.getDimension();
    Edition plan = licenseService.getDimensionPlan(dimension);
    boolean premiumSupport = licenseService.hasPremierSupport(dimension);
    LicenseType licenseType = licenseService.getModuleLicenseType(plan);
    Integer orderQuantity = awsMarketPlaceApiHandler.getDimensionQuantity(dimension);
    // dimensionModule is an internal way for us to test dimensions that belong not to the same listing
    // because when published, prices are high
    String dimensionModule = "";
    if (awsMarketPlaceApiHandler.isDimensionV2Provisionable(dimension, orderQuantity)) {
      dimensionModule = dimension.split("_")[0];
    }
    log.info("dimension:{}, dimensionModule, plan:{}, premiumSupport:{}, licenseType:{}, orderQuantity:{}", dimension,
        dimensionModule, plan, premiumSupport, licenseType, orderQuantity);

    if (marketPlace.getProductCode().equals(configuration.getMarketPlaceConfig().getAwsMarketPlaceCdProductCode())
        || CD.equals(dimensionModule)) {
      accountId = setupAccountForUser(user, userInvite, licenseInfo, true);
      ModuleLicenseDTO response = NGRestUtils.getResponse(adminLicenseHttpClient.createAccountLicense(accountId,
          CDModuleLicenseDTO.builder()
              .workloads(orderQuantity)
              .cdLicenseType(CDLicenseType.SERVICES)
              .accountIdentifier(accountId)
              .moduleType(ModuleType.CD)
              .edition(plan)
              .licenseType(licenseType)
              .premiumSupport(premiumSupport)
              .status(LicenseStatus.ACTIVE)
              .startTime(DateTime.now().getMillis())
              .expiryTime(marketPlace.getExpirationDate().getTime())
              .build()));
      log.info("CDModuleLicense {} created. CDModuleLicense {} response.", response.getId(), response);
    } else if (marketPlace.getProductCode().equals(
                   configuration.getMarketPlaceConfig().getAwsMarketPlaceCcmProductCode())
        || CCM.equals(dimensionModule)) {
      Long spendLimit = Long.valueOf(orderQuantity);
      log.info("spendLimit:{}", spendLimit);

      accountId = setupAccountForUser(user, userInvite, licenseInfo, true);

      ModuleLicenseDTO response = NGRestUtils.getResponse(adminLicenseHttpClient.createAccountLicense(accountId,
          CEModuleLicenseDTO.builder()
              .spendLimit(spendLimit)
              .accountIdentifier(accountId)
              .moduleType(ModuleType.CE)
              .edition(plan)
              .licenseType(licenseType)
              .premiumSupport(premiumSupport)
              .status(LicenseStatus.ACTIVE)
              .startTime(DateTime.now().getMillis())
              .expiryTime(marketPlace.getExpirationDate().getTime())
              .build()));
      log.info("CEModuleLicense {} created. CEModuleLicense {} response.", response.getId(), response);

    } else if (marketPlace.getProductCode().equals(
                   configuration.getMarketPlaceConfig().getAwsMarketPlaceFfProductCode())
        || FF.equals(dimensionModule)) {
      if (null != marketPlace.getLicenseType() && marketPlace.getLicenseType().equals(AccountType.TRIAL)) {
        licenseInfo.setAccountType(AccountType.TRIAL);
      }
      Long numberOfClientMAUs = licenseService.getNumberOfClientMAUs(plan);
      log.info("numberOfClientMAUs:{}", numberOfClientMAUs);

      accountId = setupAccountForUser(user, userInvite, licenseInfo, true);
      ModuleLicenseDTO response = NGRestUtils.getResponse(adminLicenseHttpClient.createAccountLicense(accountId,
          CFModuleLicenseDTO.builder()
              .numberOfClientMAUs(numberOfClientMAUs)
              .numberOfUsers(orderQuantity)
              .accountIdentifier(accountId)
              .moduleType(ModuleType.CF)
              .edition(plan)
              .licenseType(licenseType)
              .premiumSupport(premiumSupport)
              .status(LicenseStatus.ACTIVE)
              .startTime(DateTime.now().getMillis())
              .expiryTime(marketPlace.getExpirationDate().getTime())
              .build()));

      log.info("CFModuleLicense {} created. CFModuleLicense {} response.", response.getId(), response);
    } else if (marketPlace.getProductCode().equals(
                   configuration.getMarketPlaceConfig().getAwsMarketPlaceCiProductCode())
        || CI.equals(dimensionModule)) {
      if (null != marketPlace.getLicenseType() && marketPlace.getLicenseType().equals(AccountType.TRIAL)) {
        licenseInfo.setAccountType(AccountType.TRIAL);
      }
      accountId = setupAccountForUser(user, userInvite, licenseInfo, true);
      ModuleLicenseDTO response = NGRestUtils.getResponse(adminLicenseHttpClient.createAccountLicense(accountId,
          CIModuleLicenseDTO.builder()
              .numberOfCommitters(orderQuantity)
              .accountIdentifier(accountId)
              .moduleType(ModuleType.CI)
              .edition(plan)
              .licenseType(licenseType)
              .premiumSupport(premiumSupport)
              .status(LicenseStatus.ACTIVE)
              .startTime(DateTime.now().getMillis())
              .expiryTime(marketPlace.getExpirationDate().getTime())
              .build()));

      log.info("CIModuleLicense {} created. CIModuleLicense {} response.", response.getId(), response);
    } else if (marketPlace.getProductCode().equals(
                   configuration.getMarketPlaceConfig().getAwsMarketPlaceSrmProductCode())
        || SRM.equals(dimensionModule)) {
      if (null != marketPlace.getLicenseType() && marketPlace.getLicenseType().equals(AccountType.TRIAL)) {
        licenseInfo.setAccountType(AccountType.TRIAL);
      }
      accountId = setupAccountForUser(user, userInvite, licenseInfo, true);
      ModuleLicenseDTO response = NGRestUtils.getResponse(adminLicenseHttpClient.createAccountLicense(accountId,
          SRMModuleLicenseDTO.builder()
              .numberOfServices(orderQuantity)
              .accountIdentifier(accountId)
              .moduleType(ModuleType.SRM)
              .edition(plan)
              .licenseType(licenseType)
              .premiumSupport(premiumSupport)
              .status(LicenseStatus.ACTIVE)
              .startTime(DateTime.now().getMillis())
              .expiryTime(marketPlace.getExpirationDate().getTime())
              .build()));

      log.info("SRMModuleLicense {} created. SRMModuleLicense {} response.", response.getId(), response);

    } else if (marketPlace.getProductCode().equals(
                   configuration.getMarketPlaceConfig().getAwsMarketPlaceStoProductCode())
        || STO.equals(dimensionModule)) {
      if (null != marketPlace.getLicenseType() && marketPlace.getLicenseType().equals(AccountType.TRIAL)) {
        licenseInfo.setAccountType(AccountType.TRIAL);
      }
      accountId = setupAccountForUser(user, userInvite, licenseInfo, true);
      ModuleLicenseDTO response = NGRestUtils.getResponse(adminLicenseHttpClient.createAccountLicense(accountId,
          STOModuleLicenseDTO.builder()
              .numberOfDevelopers(orderQuantity)
              .accountIdentifier(accountId)
              .moduleType(ModuleType.STO)
              .edition(plan)
              .licenseType(licenseType)
              .premiumSupport(premiumSupport)
              .status(LicenseStatus.ACTIVE)
              .startTime(DateTime.now().getMillis())
              .expiryTime(marketPlace.getExpirationDate().getTime())
              .build()));

      log.info("CEModuleLicense {} created. CEModuleLicense {} response.", response.getId(), response);
    } else {
      throw new InvalidRequestException("Cannot resolve AWS marketplace order");
    }

    Map<String, String> properties = new HashMap<String, String>() {
      {
        put("marketPlaceId", marketPlace.getUuid());
        put("licenseType", marketPlace.getLicenseType());
        put("productCode", marketPlace.getProductCode());
      }
    };
    Map<String, Boolean> integrations = new HashMap<String, Boolean>() {
      { put(Keys.SALESFORCE, Boolean.TRUE); }
    };

    log.info("Setting up account {} for Product: {} and licenseType: {}", accountId, marketPlace.getProductCode(),
        marketPlace.getLicenseType());
    segmentHelper.reportTrackEvent(SYSTEM, SETUP_ACCOUNT_FROM_MARKETPLACE, properties, integrations);

    return accountId;
  }

  @Override
  public void removeAllUserGroupsFromUser(User user, String accountId) {
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId,
        aPageRequest()
            .withLimit(Long.toString(userGroupService.getCountOfUserGroups(accountId)))
            .addFilter(UserGroupKeys.memberIds, HAS, user.getUuid())
            .build(),
        true, null, null);
    List<UserGroup> userGroupList = pageResponse.getResponse();
    removeUserFromUserGroups(user, userGroupList, false);
  }

  @Override
  public void updateUserAccountLevelDataForThisGen(
      String accountId, User user, Generation generation, UserSource userSource) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)
        && userServiceHelper.validationForUserAccountLevelDataFlow(user, accountId)) {
      userServiceHelper.populateAccountToUserMapping(user, accountId, generation, userSource);
      UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
      updateOperations.set(UserKeys.userAccountLevelDataMap, user.getUserAccountLevelDataMap());
      updateUser(user.getUuid(), updateOperations);
    }
  }

  @Override
  public boolean updateExternallyManaged(String userId, Generation generation, boolean externallyManaged) {
    boolean updated = true;
    if (Generation.NG.equals(generation)) {
      try {
        UserMetadataDTO userMetadata =
            UserMetadataDTO.builder().uuid(userId).externallyManaged(externallyManaged).build();
        UserMetadataDTO updatedUserMetadata =
            NGRestUtils.getResponse(ngInviteClient.updateUserMetadata(userId, userMetadata));
      } catch (Exception ex) {
        log.error("Exception occurred while trying to update externallyManaged status for user- {}", userId, ex);
        updated = false;
      }
    } else {
      try {
        UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
        updateOperations.set(UserKeys.imported, externallyManaged);
        updateUser(userId, updateOperations);
      } catch (Exception ex) {
        log.error("Exception occurred while trying to update imported status for user- {}", userId, ex);
        updated = false;
      }
    }
    return updated;
  }

  private void queryFilterOnlyCGUsers(String accountId, Query<User> query) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)) {
      query.and(query
                    .criteria(UserKeys.userAccountLevelDataMap + "." + accountId + "."
                        + UserAccountLevelDataKeys.userProvisionedTo)
                    .equal(CG.name()));
    }
  }
  private void filterListForGeneration(String accountId, List<User> userList, Generation generation) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)) {
      Iterator<User> i = userList.iterator();
      while (i.hasNext()) {
        User user = i.next();
        if (userServiceHelper.validationForUserAccountLevelDataFlow(user, accountId)
            && !userServiceHelper.isUserProvisionedInThisGenerationInThisAccount(user, accountId, generation)) {
          i.remove();
        }
      }
    }
  }
}
