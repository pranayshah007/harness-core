/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ReportTarget.REST_API;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.ng.core.common.beans.Generation.CG;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_AUTHENTICATION_SETTINGS;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;
import static software.wings.signup.BugsnagConstants.CLUSTER_TYPE;
import static software.wings.signup.BugsnagConstants.FREEMIUM;
import static software.wings.signup.BugsnagConstants.ONBOARDING;
import static software.wings.utils.Utils.urlDecode;

import static com.google.common.collect.ImmutableMap.of;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.recaptcha.ReCaptchaVerifier;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.data.parser.Parser;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.common.beans.Generation;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.ng.core.switchaccount.SwitchAccountResponse;
import io.harness.ng.core.user.TwoFactorAdminOverrideSettings;
import io.harness.rest.RestResponse;
import io.harness.rest.RestResponse.Builder;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.PublicApi;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.AccountJoinRequest;
import software.wings.beans.AccountRole;
import software.wings.beans.ApplicationRole;
import software.wings.beans.BugsnagTab;
import software.wings.beans.ErrorData;
import software.wings.beans.LoginRequest;
import software.wings.beans.LoginTypeRequest;
import software.wings.beans.PublicUser;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.UserInvite.UserInviteBuilder;
import software.wings.beans.ZendeskSsoLoginResponse;
import software.wings.beans.loginSettings.PasswordSource;
import software.wings.beans.marketplace.MarketPlaceType;
import software.wings.beans.security.UserGroup;
import software.wings.scheduler.AccountPasswordExpirationJob;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.IdentityServiceAuth;
import software.wings.security.annotations.Scope;
import software.wings.security.authentication.AuthenticationManager;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.LoginTypeResponseV2;
import software.wings.security.authentication.SsoRedirectRequest;
import software.wings.security.authentication.TwoFactorAuthenticationManager;
import software.wings.security.authentication.TwoFactorAuthenticationMechanism;
import software.wings.security.authentication.TwoFactorAuthenticationSettings;
import software.wings.service.impl.MarketplaceTypeLogContext;
import software.wings.service.impl.UserServiceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.signup.BugsnagErrorReporter;
import software.wings.utils.AccountPermissionUtils;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.typesafe.config.Optional;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.cache.Cache;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

/**
 * Users Resource class.
 *
 * @author Rishi
 */
@Api("users")
@Path("/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Scope(ResourceType.USER)
@AuthRule(permissionType = LOGGED_IN)
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class UserResource {
  private UserService userService;
  private AuthService authService;
  private AccountService accountService;
  private AuthenticationManager authenticationManager;
  private TwoFactorAuthenticationManager twoFactorAuthenticationManager;
  private Map<String, Cache<?, ?>> caches;
  private HarnessUserGroupService harnessUserGroupService;
  private UserGroupService userGroupService;
  private AccountPermissionUtils accountPermissionUtils;
  private MainConfiguration mainConfiguration;
  private AccountPasswordExpirationJob accountPasswordExpirationJob;
  private ReCaptchaVerifier reCaptchaVerifier;
  private FeatureFlagService featureFlagService;
  private UserServiceHelper userServiceHelper;

  private static final String BASIC = "Basic";
  private static final String LARGE_PAGE_SIZE_LIMIT = "3000";
  private static final List<BugsnagTab> tab =
      Collections.singletonList(BugsnagTab.builder().tabName(CLUSTER_TYPE).key(FREEMIUM).value(ONBOARDING).build());

  @Inject private BugsnagErrorReporter bugsnagErrorReporter;
  @Inject
  public UserResource(UserService userService, AuthService authService, AccountService accountService,
      AccountPermissionUtils accountPermissionUtils, AuthenticationManager authenticationManager,
      TwoFactorAuthenticationManager twoFactorAuthenticationManager, Map<String, Cache<?, ?>> caches,
      HarnessUserGroupService harnessUserGroupService, UserGroupService userGroupService,
      MainConfiguration mainConfiguration, AccountPasswordExpirationJob accountPasswordExpirationJob,
      ReCaptchaVerifier reCaptchaVerifier, FeatureFlagService featureFlagService, UserServiceHelper userServiceHelper) {
    this.userService = userService;
    this.authService = authService;
    this.accountService = accountService;
    this.accountPermissionUtils = accountPermissionUtils;
    this.authenticationManager = authenticationManager;
    this.twoFactorAuthenticationManager = twoFactorAuthenticationManager;
    this.caches = caches;
    this.harnessUserGroupService = harnessUserGroupService;
    this.userGroupService = userGroupService;
    this.mainConfiguration = mainConfiguration;
    this.accountPasswordExpirationJob = accountPasswordExpirationJob;
    this.reCaptchaVerifier = reCaptchaVerifier;
    this.featureFlagService = featureFlagService;
    this.userServiceHelper = userServiceHelper;
  }

  /**
   * List.
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */

  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_READ)
  public RestResponse<PageResponse<PublicUser>> list(@BeanParam PageRequest<User> pageRequest,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("searchTerm") String searchTerm,
      @QueryParam("details") @DefaultValue("true") boolean loadUserGroups,
      @QueryParam("showDisabled") @DefaultValue("false") boolean showDisabledUsers) {
    Integer offset = Integer.valueOf(pageRequest.getOffset());
    if (featureFlagService.isEnabled(FeatureName.EXTRA_LARGE_PAGE_SIZE, accountId)) {
      String baseLimit = LARGE_PAGE_SIZE_LIMIT;
      String limit = PageRequest.UNLIMITED.equals(pageRequest.getLimit())
          ? baseLimit
          : Integer.toString(Parser.asInt(pageRequest.getLimit(), Integer.parseInt(baseLimit)));
      pageRequest.setLimit(limit);
    }
    Integer pageSize = pageRequest.getPageSize();

    List<User> userList = userService.listUsers(
        pageRequest, accountId, searchTerm, offset, pageSize, true, true, showDisabledUsers, true);

    PageResponse<PublicUser> pageResponse = aPageResponse()
                                                .withOffset(offset.toString())
                                                .withLimit(pageSize.toString())
                                                .withResponse(getPublicUsers(userList, accountId))
                                                .withTotal(userService.getTotalUserCount(accountId, true, true, true))
                                                .build();

    return new RestResponse<>(pageResponse);
  }

  /**
   * Register a user with account/company name. The user and account will be created as part of this operation
   *
   * @param user the user
   * @return the rest response
   */
  @POST
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<User> register(User user) {
    user.setAppId(GLOBAL_APP_ID);
    return new RestResponse<>(userService.register(user));
  }

  /**
   *  Start the trial registration with email and user info.
   *  A verification email will be sent to the specified email address.
   *  On successful verification, it creates the account and registers the user.
   *
   * @param userInvite user invite with email and user info
   */
  @PublicApi
  @POST
  @Path("new-trial")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> trialSignup(UserInvite userInvite) {
    try {
      return new RestResponse<>(userService.trialSignup(userInvite));
    } catch (Exception exception) {
      bugsnagErrorReporter.report(
          ErrorData.builder().exception(exception).email(userInvite.getEmail()).tabs(tab).build());
      throw exception;
    }
  }

  /**
   *  Start the trial registration with email and user info.
   *  A verification email will be sent to the specified email address.
   *  On successful verification, it creates the account and registers the user.
   *
   * @param accountJoinRequest user invite with email and user info
   */
  @PublicApi
  @POST
  @Path("join-account")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> accountJoinRequest(AccountJoinRequest accountJoinRequest) {
    try {
      return new RestResponse<>(userService.accountJoinRequest(accountJoinRequest));
    } catch (Exception exception) {
      bugsnagErrorReporter.report(
          ErrorData.builder().exception(exception).email(accountJoinRequest.getEmail()).tabs(tab).build());
      throw exception;
    }
  }

  @POST
  @Path("account")
  @Timed
  @ExceptionMetered
  public RestResponse<Account> addAccount(
      Account account, @QueryParam("addUser") @DefaultValue("true") boolean addUser) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(userService.addAccount(account, existingUser, addUser));
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder().message("User not allowed to add account").build()))
          .build();
    }
  }

  /**
   * Update User group
   *
   * @param userId the user id
   * @param user   the user
   * @return the rest response
   */
  @PUT
  @Path("user/{userId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<User> updateUserGroupsOfUser(
      @QueryParam("accountId") @NotEmpty String accountId, @PathParam("userId") String userId, User user) {
    return getPublicUser(userService.updateUserGroupsOfUser(userId, user.getUserGroups(), accountId, true));
  }

  @GET
  @Hidden
  @InternalApi
  @AuthRule(permissionType = LOGGED_IN)
  @Path("validate-support-user/{userId}")
  @ApiOperation(value = "This is to check if user is part of harness support group", hidden = true)
  public RestResponse<Boolean> isHarnessSupportUser(@PathParam("userId") String userId) {
    boolean response = harnessUserGroupService.isHarnessSupportUser(userId);
    return new RestResponse<>(response);
  }

  /**
   * Update User profile
   *
   * @param userId the user id
   * @param user   the user
   * @return the rest response
   */
  @PUT
  @Path("profile/{userId}")
  @Timed
  @ExceptionMetered
  public RestResponse<User> updateUserProfile(
      @QueryParam("accountId") @NotEmpty String accountId, @PathParam("userId") String userId, @NotNull User user) {
    User authUser = UserThreadLocal.get();
    if (!authUser.getUuid().equals(userId)) {
      throw new WingsException(ErrorCode.ACCESS_DENIED, USER);
    }
    user.setUuid(userId);
    if (isEmpty(user.getAppId())) {
      user.setAppId(GLOBAL_APP_ID);
    }
    return new RestResponse<>(userService.updateUserProfile(user));
  }

  /**
   * Reset all caches.
   *
   * @return the rest response
   */
  @PUT
  @Path("reset-cache")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse resetCache() {
    User authUser = UserThreadLocal.get();
    if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      caches.values().forEach(Cache::clear);
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder().message("User not allowed to perform the reset-cache operation").build()))
          .build();
    }

    return Builder.aRestResponse()
        .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message("Cache reset successful").build()))
        .build();
  }

  /**
   * Delete.
   *
   * @param accountId the account id
   * @param userId    the user id
   * @return the rest response
   */
  @DELETE
  @Path("{userId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse delete(@QueryParam("accountId") @NotEmpty String accountId, @PathParam("userId") String userId) {
    User user = userService.get(userId);
    // If user doesn't exists, userService.get throws exception, so we don't have to handle it.
    InvalidRequestException exception = new InvalidRequestException("Can not delete user added via SCIM", USER);
    if (featureFlagService.isEnabled(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW, accountId)
        && userServiceHelper.validationForUserAccountLevelDataFlow(user, accountId)) {
      if (userServiceHelper.isSCIMManagedUser(accountId, user, CG)) {
        throw exception;
      }
    } else if (user.isImported()) {
      throw exception;
    }
    userService.delete(accountId, userId);
    if (featureFlagService.isEnabled(FeatureName.PL_USER_DELETION_V2, accountId) && userService.isUserPresent(userId)
        && !userService.isUserPartOfAnyUserGroupInCG(userId, accountId)) {
      throw new InvalidRequestException(
          "User is part of NG, hence the userGroups are removed for the user. Please delete the user from NG to remove the user from Harness.");
    }
    return new RestResponse();
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<Boolean> delete(
      @QueryParam("accountId") @NotEmpty String accountId, @NotEmpty List<String> usersToRetain) {
    if (CollectionUtils.isEmpty(usersToRetain)) {
      throw new InvalidRequestException("All users in the account can not be deleted");
    }
    return new RestResponse<>(userService.deleteUsersByEmailAddress(accountId, usersToRetain));
  }

  /**
   * Reset password rest response.
   *
   * @param passwordRequest the password request
   * @return the rest response
   */
  @PublicApi
  @POST
  @Path("reset-password")
  @Timed
  @ExceptionMetered
  public RestResponse resetPassword(@NotNull ResetPasswordRequest passwordRequest) {
    try {
      return new RestResponse<>(userService.resetPassword(passwordRequest));
    } catch (Exception exception) {
      bugsnagErrorReporter.report(
          ErrorData.builder().exception(exception).email(passwordRequest.getEmail()).tabs(tab).build());
      throw exception;
    }
  }

  /**
   * Resend invitation email
   *
   * @param invitationEmailRequest the invitation email request
   * @return the rest response
   */
  @POST
  @Path("resend-invitation-email")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse resendInvitationEmail(@QueryParam("accountId") @NotBlank String accountId,
      @Valid @NotNull ResendInvitationEmailRequest invitationEmailRequest) {
    return new RestResponse<>(userService.resendInvitationEmail(accountId, invitationEmailRequest.getEmail()));
  }

  /**
   * Update password rest response.
   *
   * @param resetPasswordToken    the reset password token
   * @param updatePasswordRequest the update password request
   * @return the rest response
   */
  @PublicApi
  @POST
  @Path("reset-password/{token}")
  @Timed
  @ExceptionMetered
  public RestResponse updatePassword(
      @NotEmpty @PathParam("token") String resetPasswordToken, UpdatePasswordRequest updatePasswordRequest) {
    try {
      return new RestResponse(
          userService.updatePassword(resetPasswordToken, updatePasswordRequest.getPassword().toCharArray()));
    } catch (Exception exception) {
      bugsnagErrorReporter.report(ErrorData.builder().exception(exception).tabs(tab).build());
      throw exception;
    }
  }

  @PublicApi
  @GET
  @Path("check-password-violations")
  @Timed
  @ExceptionMetered
  public RestResponse checkPasswordViolations(@NotEmpty @QueryParam("token") String token,
      @QueryParam("pollType") PasswordSource passwordSource, @HeaderParam(HttpHeaders.AUTHORIZATION) String password,
      @QueryParam("accountId") String accountId) {
    return new RestResponse(userService.checkPasswordViolations(token, passwordSource, password, accountId));
  }

  /**
   * Get rest response.
   *
   * @param accountName the account name
   * @return the rest response
   */
  @GET
  @Path("account-name/{accountName}")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<String> suggestAccountName(@PathParam("accountName") String accountName) {
    return new RestResponse<>(accountService.suggestAccountName(accountName));
  }

  /**
   * Get rest response.
   *
   * @return the rest response
   */
  @GET
  @Path("user")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<User> get() {
    return new RestResponse<>(UserThreadLocal.get().getPublicUser(false));
  }

  /**
   * Get User Account details response.
   *
   * @return the rest response
   */
  @GET
  @Path("userAccounts")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<io.harness.ng.beans.PageResponse<Account>> getUserAccounts(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int pageIndex,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("20") int pageSize,
      @Optional @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    List<Account> accounts =
        userService.getUserAccounts(UserThreadLocal.get().getUuid(), pageIndex, pageSize, searchTerm);
    return new RestResponse<>(io.harness.ng.beans.PageResponse.<Account>builder()
                                  .content(accounts)
                                  .pageItemCount(accounts.size())
                                  .pageSize(pageSize)
                                  .pageIndex(pageIndex)
                                  .build());
  }

  /**
   * Look up the user object using email and login the user. Intended for internal use only.
   * E.g. The Identity Service authenticated the user through OAuth provider and get the user email, then
   * login this user through this API directly.
   *
   * @return the rest response
   */
  @GET
  @Path("user/login")
  @Timed
  @ExceptionMetered
  @IdentityServiceAuth
  public RestResponse<User> loginUser(@QueryParam("email") String email) {
    return new RestResponse<>(authenticationManager.loginUserForIdentityService(urlDecode(email)));
  }

  /**
   * Get rest response.
   *
   * @param accountId the account id
   * @return the rest response
   */
  @GET
  @Path("account-roles/{accountId}")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  public RestResponse<AccountRole> getAccountRole(@PathParam("accountId") String accountId) {
    return new RestResponse<>(
        userService.getUserAccountRole(UserThreadLocal.get().getPublicUser(false).getUuid(), accountId));
  }

  @GET
  @Path("user-permissions/{accountId}")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  public RestResponse<UserPermissionInfo> getUserPermissionInfo(@PathParam("accountId") String accountId) {
    return new RestResponse<>(
        authService.getUserPermissionInfo(accountId, UserThreadLocal.get().getPublicUser(false), false));
  }

  /**
   * Get list of features flags and their statuses
   *
   * @param accountId account id
   * @return the rest response
   */
  @GET
  @Path("feature-flags/{accountId}")
  @Scope(value = ResourceType.USER)
  @AuthRule(permissionType = LOGGED_IN)
  @Timed
  @ExceptionMetered
  public RestResponse<Collection<FeatureFlag>> getFeatureFlags(@PathParam("accountId") String accountId) {
    return new RestResponse<>(accountService.getFeatureFlags(accountId));
  }

  /**
   * Get rest response.
   *
   * @param appId the app id
   * @return the rest response
   */
  @GET
  @Path("application-roles/{appId}")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  public RestResponse<ApplicationRole> getApplicationRole(@PathParam("appId") String appId) {
    return new RestResponse<>(
        userService.getUserApplicationRole(UserThreadLocal.get().getPublicUser(false).getUuid(), appId));
  }

  /**
   * Explicitly switch account for a logged in user. A new JWT bearer token with the new account ID will be generated
   * and returned as part of the response.
   */
  @GET
  @Path("switch-account")
  @Timed
  @ExceptionMetered
  public RestResponse<User> switchAccount(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(
        authenticationManager.switchAccount(authenticationManager.extractToken(authorization, "Bearer"), accountId));
  }

  @Data
  public static class SwitchAccountRequest {
    @NotBlank private String accountId;
  }

  @POST
  @Path("switch-account")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> newSwitchAccount(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @NotNull SwitchAccountRequest switchAccountRequest) {
    return new RestResponse<>(
        authenticationManager.switchAccount(
            authenticationManager.extractToken(authorization, "Bearer"), switchAccountRequest.getAccountId())
        != null);
  }

  @POST
  @Path("restricted-switch-account")
  @Timed
  @ExceptionMetered
  public RestResponse<SwitchAccountResponse> restrictedSwitchAccount(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization, @QueryParam("routingId") String accountId,
      @Valid @NotNull SwitchAccountRequest switchAccountRequest) {
    // Adding this endpoint for UI swagger generation
    return new RestResponse<>(SwitchAccountResponse.builder().requiresReAuthentication(false).build());
  }

  /**
   * Explicitly set default account for a logged in user. This means the user will be landed in the default account
   * after logged in next time.
   */
  @PUT
  @Path("set-default-account/{accountId}")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> setDefaultAccountForCurrentUser(@PathParam("accountId") @NotBlank String accountId) {
    User currentUser = UserThreadLocal.get();
    if (currentUser == null) {
      throw new InvalidRequestException("Invalid User");
    }
    return new RestResponse<>(userService.setDefaultAccount(currentUser, accountId));
  }

  /**
   * Login a user through basic auth.
   *
   * If accountId is specified, it will authenticate using the specified account's auth mechanism. Otherwise
   * it will authenticate using the user's default/primary account's auth mechanism.
   *
   * @return the rest response
   */
  @POST
  @Path("login")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<User> login(LoginRequest loginBody, @QueryParam("accountId") String accountId,
      @QueryParam("captcha") @Nullable String captchaToken) {
    String basicAuthToken = authenticationManager.extractToken(loginBody.getAuthorization(), BASIC);

    validateCaptchaToken(captchaToken);

    // accountId field is optional, it could be null.
    return new RestResponse<>(authenticationManager.defaultLoginAccount(basicAuthToken, accountId));
  }

  /**
   * Harness local login.
   * To be used in case of lockout scenarios, if the default login mechanism is 3rd party provider.
   * Only users having account management permissions will be able to login using this.
   *
   * @return the rest response
   */

  @POST
  @Path("harness-local-login")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<User> forceLoginUsingHarnessPassword(
      @QueryParam("accountId") String accountId, LoginRequest loginBody) {
    if (featureFlagService.isEnabled(FeatureName.DISABLE_LOCAL_LOGIN, accountId)) {
      throw new InvalidRequestException(String.format("Local Login is not enabled for account {}", accountId));
    }
    return new RestResponse<>(authenticationManager.loginUsingHarnessPassword(
        authenticationManager.extractToken(loginBody.getAuthorization(), BASIC), accountId));
  }

  @POST
  @Path("two-factor-login")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<User> twoFactorLogin(@NotNull LoginRequest loginRequest) {
    return new RestResponse<>(twoFactorAuthenticationManager.authenticate(
        authenticationManager.extractToken(loginRequest.getAuthorization(), "JWT")));
  }

  /**
   * Return the specified user's login types (including auth mechanism and redirect request if SSO).
   *
   * If accountId is specified, it will return using the specified account's login type. Otherwise
   * it will authenticate using the user's default/primary account's auth mechanism.
   *
   * @return the rest response
   */
  @POST
  @Path("logintype")
  @PublicApi
  public RestResponse<LoginTypeResponse> getLoginType(
      @NotNull LoginTypeRequest loginTypeRequest, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(authenticationManager.getLoginTypeResponse(loginTypeRequest.getUserName(), accountId));
  }

  @POST
  @Path("v2/logintype")
  @PublicApi
  public RestResponse<LoginTypeResponseV2> getLoginTypeV2(
      @NotNull LoginTypeRequest loginTypeRequest, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(authenticationManager.getLoginTypeResponseV2(loginTypeRequest.getUserName(), accountId));
  }

  @GET
  @Path("onprem-logintype")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<LoginTypeResponse> getLoginTypeForOnPremSetup() {
    return new RestResponse<>(authenticationManager.getLoginTypeResponseForOnPrem());
  }

  @GET
  @Path("oauth2Redirect")
  @PublicApi
  public Response oauth2Redirect(@QueryParam("provider") @NotNull String provider) {
    return authenticationManager.oauth2Redirect(provider.toLowerCase());
  }

  @GET
  @Path("oauth2/{provider}")
  @PublicApi
  public Response oauth2CallbackUrl(@QueryParam("code") String code, @QueryParam("state") String state,
      @PathParam("provider") @NotNull String provider) {
    try {
      return authenticationManager.oauth2CallbackUrl(code, state, provider.toLowerCase());
    } catch (URISyntaxException e) {
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
    }
  }

  @POST
  @Path("sso-redirect-login")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<User> redirectlogin(SsoRedirectRequest request) {
    return new RestResponse<>(authenticationManager.ssoRedirectLogin(request.getJwtToken()));
  }

  @GET
  @Path("two-factor-auth/{auth-mechanism}")
  @Timed
  @ExceptionMetered
  public RestResponse<TwoFactorAuthenticationSettings> getTwoFactorAuthSettings(
      @PathParam("auth-mechanism") TwoFactorAuthenticationMechanism authMechanism) {
    return new RestResponse(
        twoFactorAuthenticationManager.createTwoFactorAuthenticationSettings(UserThreadLocal.get(), authMechanism));
  }

  @PUT
  @Path("override-two-factor-auth/{accountId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_AUTHENTICATION_SETTINGS)
  public RestResponse<TwoFactorAdminOverrideSettings> setTwoFactorAuthAtAccountLevel(
      @PathParam("accountId") @NotEmpty String accountId, @NotNull TwoFactorAdminOverrideSettings settings) {
    // Trying Override = true
    if (settings.isAdminOverrideTwoFactorEnabled()) {
      if (twoFactorAuthenticationManager.isTwoFactorEnabled(accountId, UserThreadLocal.get())) {
        return new RestResponse(twoFactorAuthenticationManager.overrideTwoFactorAuthentication(accountId, settings));
      } else {
        return Builder.aRestResponse()
            .withResponseMessages(
                Lists.newArrayList(ResponseMessage.builder()
                                       .message("Admin has 2FA disabled. Please enable to enforce 2FA on users.")
                                       .build()))
            .build();
      }
    }
    // Trying Override = false
    else {
      return new RestResponse(twoFactorAuthenticationManager.overrideTwoFactorAuthentication(accountId, settings));
    }
  }

  @PUT
  @Path("disable-two-factor-auth/{accountId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = MANAGE_AUTHENTICATION_SETTINGS)
  public RestResponse<Boolean> disableTwoFactorAuth(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(twoFactorAuthenticationManager.disableTwoFactorAuthentication(accountId));
  }

  @GET
  @Path("two-factor-auth-info/{accountId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> getTwoFactorAuthAdminEnforceInfo(@PathParam("accountId") @NotEmpty String accountId) {
    return new RestResponse(twoFactorAuthenticationManager.getTwoFactorAuthAdminEnforceInfo(accountId));
  }

  @PUT
  @Path("disable-two-factor-auth")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<User> disableTwoFactorAuth() {
    return new RestResponse(twoFactorAuthenticationManager.disableTwoFactorAuthentication(UserThreadLocal.get()));
  }

  @GET
  @Path("invitation-id")
  @Timed
  @ExceptionMetered
  public RestResponse<String> getUserInvitationId(@QueryParam("email") @NotEmpty String email) {
    User user = UserThreadLocal.get();
    if (user == null) {
      throw new InvalidRequestException("Invalid User");
    }
    if (isEmpty(user.getEmail()) || !user.getEmail().endsWith("@harness.io")) {
      throw new UnauthorizedException("User not authorized.", USER);
    }
    return new RestResponse<>(userService.getUserInvitationId(email));
  }

  @PUT
  @Path("disable/{userId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> disableUser(
      @PathParam("userId") @NotEmpty String userId, @QueryParam("accountId") @NotEmpty String accountId) {
    // Only if the user the account administrator or in the Harness user group can perform the export operation.
    if (!userService.hasPermission(accountId, USER_PERMISSION_MANAGEMENT)) {
      String errorMessage = "User is not account administrator and can't perform the export operation.";
      RestResponse<Boolean> restResponse = accountPermissionUtils.checkIfHarnessUser(errorMessage);
      if (restResponse != null) {
        throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED, USER);
      }
    }
    return new RestResponse<>(userService.enableUser(accountId, userId, false));
  }

  @PUT
  @Path("enable/{userId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> enableUser(
      @PathParam("userId") @NotEmpty String userId, @QueryParam("accountId") @NotEmpty String accountId) {
    // Only if the user the account administrator or in the Harness user group can perform the export operation.
    if (!userService.hasPermission(accountId, USER_PERMISSION_MANAGEMENT)) {
      String errorMessage = "User is not account administrator and can't perform the export operation.";
      RestResponse<Boolean> restResponse = accountPermissionUtils.checkIfHarnessUser(errorMessage);
      if (restResponse != null) {
        throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED, USER);
      }
    }
    return new RestResponse<>(userService.enableUser(accountId, userId, true));
  }

  @PUT
  @Hidden
  @Path("enable-user-internal/{userId}")
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<Boolean> enableUserInternal(
      @PathParam("userId") @NotEmpty String userId, @QueryParam("accountId") @NotEmpty String accountId) {
    // If the current user can Manage User(s) & is part of the Harness user group can perform the enable operation
    User existingUser = UserThreadLocal.get();
    if (existingUser == null || !harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED, USER);
    }
    log.info("ENABLE_USER_INTERNAL: Enabling disabled user {} for account {}", existingUser.getUuid(), accountId);
    return new RestResponse<>(userService.enableUser(accountId, userId, true));
  }

  @PUT
  @Path("enable-two-factor-auth")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<User> enableTwoFactorAuth(TwoFactorAuthenticationSettings settings) {
    return new RestResponse(
        twoFactorAuthenticationManager.enableTwoFactorAuthenticationSettings(UserThreadLocal.get(), settings));
  }

  @POST
  @Path("saml-login")
  @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @PublicApi
  @Timed
  @ExceptionMetered
  public javax.ws.rs.core.Response samlLogin(@FormParam(value = "SAMLResponse") String samlResponse,
      @FormParam(value = "RelayState") String relayState, @Context HttpServletRequest request,
      @Context HttpServletResponse response, @QueryParam("accountId") String accountId) {
    try {
      return authenticationManager.samlLogin(
          request.getHeader(com.google.common.net.HttpHeaders.REFERER), samlResponse, accountId, relayState);
    } catch (WingsException e) {
      throw e;
    } catch (URISyntaxException e) {
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
    }
  }

  /**
   * Logout.
   *
   * @param userId the user id
   * @return the rest response
   */
  @POST
  @Path("{userId}/logout")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN, skipAuth = true)
  public RestResponse logout(@PathParam("userId") String userId) {
    User user = UserThreadLocal.get();
    userService.logout(user);
    return new RestResponse();
  }

  /**
   * Verify token rest response.
   *
   * @param token the token
   * @return the rest response
   * @throws URISyntaxException the uri syntax exception
   */
  @GET
  @Path("verify/{token}")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, Object>> verifyToken(@PathParam("token") String token) throws URISyntaxException {
    return new RestResponse<>(of("success", userService.verifyToken(token)));
  }

  /**
   * Verify email rest response.
   *
   * @param email the token
   * @return the rest response
   * @throws URISyntaxException the uri syntax exception
   */
  @GET
  @Path("verify-email")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> verifyEmail(@QueryParam("email") String email) throws URISyntaxException {
    try {
      userService.verifyRegisteredOrAllowed(email);
    } catch (WingsException exception) {
      // TODO: this seems wrong, just letting the exception to be thrown should do the same
      return RestResponse.Builder.aRestResponse()
          .withResource(false)
          .withResponseMessages(ExceptionLogger.getResponseMessageList(exception, REST_API))
          .build();
    }
    return new RestResponse<>(true);
  }

  /**
   * Resend verification email.
   *
   * @param email the token
   * @return the rest response
   * @throws URISyntaxException the uri syntax exception
   */
  @GET
  @Path("resend-verification-email/{email}")
  @PublicApi
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> resendVerificationEmail(@PathParam("email") String email) throws URISyntaxException {
    try {
      return new RestResponse<>(userService.resendVerificationEmail(email));
    } catch (Exception exception) {
      bugsnagErrorReporter.report(ErrorData.builder().exception(exception).email(email).tabs(tab).build());
      throw exception;
    }
  }

  /**
   * Assign role.
   *
   * @param userId the user id
   * @param roleId the role id
   * @return the rest response
   */
  @PUT
  @Path("{userId}/role/{roleId}")
  @Timed
  @ExceptionMetered
  public RestResponse<User> assignRole(@PathParam("userId") String userId, @PathParam("roleId") String roleId) {
    return getPublicUser(userService.addRole(userId, roleId));
  }

  /**
   * Revoke role.
   *
   * @param userId the user id
   * @param roleId the role id
   * @return the rest response
   */
  @DELETE
  @Path("{userId}/role/{roleId}")
  @Timed
  @ExceptionMetered
  public RestResponse<User> revokeRole(@PathParam("userId") String userId, @PathParam("roleId") String roleId) {
    return getPublicUser(userService.revokeRole(userId, roleId));
  }

  /**
   * List invites rest response.
   *
   * @param accountId   the account id
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  @Path("invites")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<PageResponse<UserInvite>> listInvites(
      @QueryParam("accountId") @NotEmpty String accountId, @BeanParam PageRequest<UserInvite> pageRequest) {
    return getPublicUserInvites(userService.listInvites(pageRequest));
  }

  /**
   * List invites rest response.
   */
  @POST
  @Path("trigger-account-password-expiration-job")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<String> triggerAccountPasswordExpirationCheckJob() {
    accountPermissionUtils.checkIfHarnessUser("User not authorized");
    accountPasswordExpirationJob.checkAccountPasswordExpiration();
    return new RestResponse<>("Running password expiration cron job");
  }

  /**
   * Gets invite.
   *
   * @param accountId the account id
   * @param inviteId  the invite id
   * @return the invite
   */
  @GET
  @Path("invites/{inviteId}")
  @Timed
  @ExceptionMetered
  public RestResponse<UserInvite> getInvite(
      @QueryParam("accountId") @NotEmpty String accountId, @PathParam("inviteId") @NotEmpty String inviteId) {
    return getPublicUserInvite(userService.getInvite(accountId, inviteId));
  }

  /**
   * Invite users rest response.
   *
   * @param accountId  the account id
   * @param userInvite the user invite
   * @return the rest response
   */
  @POST
  @Path("invites")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<List<InviteOperationResponse>> inviteUsers(
      @QueryParam("accountId") @NotEmpty String accountId, @NotNull UserInvite userInvite) {
    userInvite.setAccountId(accountId);
    userInvite.setAppId(GLOBAL_APP_ID);
    return new RestResponse<>(userService.inviteUsers(userInvite));
  }

  /**
   * Checks the status of the invite and either redirects on the login page with corresponding message or to the
   * password sign-up page.
   * @param accountId  the account id
   * @param inviteId   the invite id
   * @return the rest response
   */
  @PublicApi
  @GET
  @Path("invites/{inviteId}/status")
  @Timed
  @ExceptionMetered
  public RestResponse<InviteOperationResponse> checkInvite(@QueryParam("accountId") @NotEmpty String accountId,
      @PathParam("inviteId") @NotEmpty String inviteId, @QueryParam("generation") Generation gen) {
    UserInvite userInvite = new UserInvite();
    userInvite.setAccountId(accountId);
    userInvite.setUuid(inviteId);
    try {
      return new RestResponse<>(userService.checkInviteStatus(userInvite, gen));
    } catch (Exception e) {
      log.error("error checking invite", e);
      return new RestResponse<>(InviteOperationResponse.FAIL);
    }
  }

  @POST
  @Path("custom-event")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> postCustomEvent(
      @QueryParam("accountId") @NotEmpty String accountId, @NotEmpty String event) {
    return new RestResponse<>(userService.postCustomEvent(accountId, event));
  }

  @GET
  @Path("refresh-token")
  @Timed
  @ExceptionMetered
  public RestResponse<String> refreshToken(@HeaderParam(HttpHeaders.AUTHORIZATION) String oldToken) {
    return new RestResponse<>(authenticationManager.refreshToken(oldToken));
  }

  /**
   * Complete invite rest response.
   *
   * @param accountId  the account id
   * @param inviteId   the invite id
   * @param userInvite the user invite
   * @return the rest response
   */
  @PublicApi
  @PUT
  @Path("invites/{inviteId}")
  @Timed
  @ExceptionMetered
  public RestResponse<InviteOperationResponse> completeInvite(@QueryParam("accountId") @NotEmpty String accountId,
      @PathParam("inviteId") @NotEmpty String inviteId, @NotNull UserInvite userInvite) {
    userInvite.setAccountId(accountId);
    userInvite.setUuid(inviteId);
    return new RestResponse<>(userService.completeInvite(userInvite));
  }

  /**
   * Complete invite rest response.
   *
   * @param accountName the account name
   * @param inviteId   the invite id
   * @param userInvite the user invite
   * @return the rest response
   */
  @PublicApi
  @PUT
  @Path("invites/mktplace/{inviteId}/signin")
  @Timed
  @ExceptionMetered
  public RestResponse<User> completeMarketPlaceInvite(@QueryParam("account") @NotEmpty String accountName,
      @QueryParam("company") @NotEmpty String companyName,
      @QueryParam("marketPlaceType") MarketPlaceType marketPlaceType, @PathParam("inviteId") @NotEmpty String inviteId,
      @NotNull UserInvite userInvite) {
    // Only AWS / GCP marketplaces are supported
    try (AutoLogContext ignore1 = new MarketplaceTypeLogContext(marketPlaceType, OVERRIDE_ERROR)) {
      log.info("Marketplace sign-up. MarketPlaceType= {}", marketPlaceType);

      try {
        companyName = URLDecoder.decode(companyName, UTF_8.displayName());
        accountName = URLDecoder.decode(accountName, UTF_8.displayName());
      } catch (UnsupportedEncodingException e) {
        log.info("Account Name and Company Name must be UTF-8 compliant. accountName: {} companyName: {}", accountName,
            companyName);
        throw new InvalidRequestException("Account Name and Company Name must be UTF-8 compliant.", e);
      }

      userInvite.setUuid(inviteId);
      User user = User.Builder.anUser()
                      .email(userInvite.getEmail())
                      .name(userInvite.getName())
                      .password(userInvite.getPassword())
                      .accountName(accountName)
                      .companyName(companyName)
                      .build();

      User savedUser = userService.completeMarketPlaceSignup(user, userInvite, marketPlaceType);
      return new RestResponse<>(savedUser);
    }
  }

  @PublicApi
  @PUT
  @Path("invites/{inviteId}/signin")
  @Timed
  @ExceptionMetered
  public RestResponse<User> completeInviteAndSignIn(@QueryParam("accountId") @NotEmpty String accountId,
      @PathParam("inviteId") @NotEmpty String inviteId, @QueryParam("generation") Generation gen,
      @NotNull UserInvite userInvite) {
    if (gen != null && gen.equals(Generation.NG)) {
      UserInviteDTO inviteDTO = UserInviteDTO.builder()
                                    .accountId(accountId)
                                    .email(userInvite.getEmail())
                                    .password(String.valueOf(userInvite.getPassword()))
                                    .name(userInvite.getName())
                                    .token(inviteId)
                                    .build();
      return new RestResponse<>(userService.completeNGInviteAndSignIn(inviteDTO));
    } else {
      userInvite.setAccountId(accountId);
      userInvite.setUuid(inviteId);
      return new RestResponse<>(userService.completeInviteAndSignIn(userInvite));
    }
  }

  @PublicApi
  @PUT
  @Path("invites/ngsignin")
  @Timed
  @ExceptionMetered
  public RestResponse<User> completeInviteAndSignIn(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("generation") Generation gen, @NotNull UserInviteDTO userInviteDTO) {
    if (gen != null && gen.equals(CG)) {
      Account account = accountService.get(accountId);
      String inviteId = userService.getInviteIdFromToken(userInviteDTO.getToken());
      UserInvite userInvite = UserInviteBuilder.anUserInvite()
                                  .withAccountId(accountId)
                                  .withEmail(userInviteDTO.getEmail())
                                  .withName(userInviteDTO.getName())
                                  .withAccountName(account.getAccountName())
                                  .withCompanyName(account.getCompanyName())
                                  .withUuid(inviteId)
                                  .build();
      userInvite.setAccountId(accountId);
      userInvite.setUuid(inviteId);
      userInvite.setPassword(userInviteDTO.getPassword().toCharArray());
      return new RestResponse<>(userService.completeInviteAndSignIn(userInvite));
    } else {
      return new RestResponse<>(userService.completeNGInviteAndSignIn(userInviteDTO));
    }
  }

  /**
   * The backend URL for invite which will be added in
   *
   * @param accountId the account ID
   * @param jwtToken JWT token corresponding to the invite
   * @param email Email id for the user
   * @return the rest response
   */
  @PublicApi
  @GET
  @Path("invites/verify")
  @Timed
  @ExceptionMetered
  public Response acceptInviteAndRedirect(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("token") @NotNull String jwtToken, @QueryParam("email") @NotNull String email) {
    UserInvite userInvite = new UserInvite();
    String decodedEmail = email;
    try {
      decodedEmail = URLDecoder.decode(email, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      log.error("Unsupported encoding exception for " + accountId, e);
      throw new InvalidRequestException("Malformed email received");
    }

    String inviteId = userService.getInviteIdFromToken(jwtToken);
    userInvite.setAccountId(accountId);
    userInvite.setEmail(decodedEmail);
    userInvite.setUuid(inviteId);
    InviteOperationResponse inviteResponse = userService.checkInviteStatus(userInvite, CG);
    URI redirectURL = null;
    try {
      redirectURL = userService.getInviteAcceptRedirectURL(inviteResponse, userInvite, jwtToken);
      return Response.seeOther(redirectURL).build();
    } catch (URISyntaxException e) {
      log.error("Unable to create redirect url for invite", e);
      throw new InvalidRequestException("URI syntax error");
    }
  }

  @PublicApi
  @PUT
  @Path("invites/trial/{inviteId}/new-signin")
  @Timed
  @ExceptionMetered
  public RestResponse<User> completeTrialSignupAndSignIn(@PathParam("inviteId") @NotEmpty String inviteId) {
    return new RestResponse<>(userService.completeTrialSignupAndSignIn(inviteId));
  }

  /**
   * Delete invite rest response.
   *
   * @param inviteId  the invite id
   * @param accountId the account id
   * @return the rest response
   */
  @DELETE
  @Path("invites/{inviteId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<UserInvite> deleteInvite(
      @PathParam("inviteId") @NotEmpty String inviteId, @QueryParam("accountId") @NotEmpty String accountId) {
    return getPublicUserInvite(userService.deleteInvite(accountId, inviteId));
  }

  @POST
  @Path("sso/zendesk")
  public RestResponse<ZendeskSsoLoginResponse> zendDesk(@QueryParam("returnTo") @NotEmpty String returnTo) {
    return new RestResponse(userService.generateZendeskSsoJwt(returnTo));
  }

  /**
   * Delete invite rest response.
   *
   * @param userId  the user id
   * @param accountId the account id
   * @return the rest response
   */
  @GET
  @Path("reset-two-factor-auth/{userId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<Boolean> reset2fa(
      @PathParam("userId") @NotEmpty String userId, @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(twoFactorAuthenticationManager.sendTwoFactorAuthenticationResetEmail(userId));
  }

  @PUT
  @Path("lead-update")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> updateMarketoForUser(
      @NotEmpty @QueryParam("email") String email, @NotEmpty @QueryParam("accountId") String accountId) {
    return new RestResponse<>(userService.updateLead(email, accountId));
  }

  @PUT
  @Path("unlock-user")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<User> unlockUser(
      @NotEmpty @QueryParam("email") String email, @NotEmpty @QueryParam("accountId") String accountId) {
    return new RestResponse<>(userService.unlockUser(email, accountId));
  }

  @PUT
  @Path("update-externally-managed/{userId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> updateScimStatusNG(@PathParam("userId") String userId,
      @QueryParam("generation") Generation generation, @Body Boolean externallyManaged) {
    User existingUser = UserThreadLocal.get();
    if (existingUser == null) {
      throw new InvalidRequestException("Invalid User");
    }

    if (harnessUserGroupService.isHarnessSupportUser(existingUser.getUuid())) {
      return new RestResponse<>(userService.updateExternallyManaged(userId, generation, externallyManaged));
    } else {
      return RestResponse.Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder().message("User not allowed to update account product-led status").build()))
          .build();
    }
  }

  private RestResponse<UserInvite> getPublicUserInvite(UserInvite userInvite) {
    if (userInvite == null) {
      return new RestResponse<>();
    }
    setUserGroupSummary(userInvite);
    return new RestResponse<>(userInvite);
  }

  private RestResponse<PageResponse<UserInvite>> getPublicUserInvites(PageResponse<UserInvite> pageResponse) {
    if (pageResponse == null) {
      return new RestResponse<>();
    }

    List<UserInvite> userInvites = pageResponse.getResponse();
    if (isEmpty(userInvites)) {
      return new RestResponse<>(pageResponse);
    }
    userInvites.forEach(this::setUserGroupSummary);
    return new RestResponse<>(pageResponse);
  }

  private void setUserGroupSummary(UserInvite userInvite) {
    List<UserGroup> userGroupSummaryList = userGroupService.getUserGroupSummary(userInvite.getUserGroups());
    userInvite.setUserGroups(userGroupSummaryList);
  }

  private RestResponse<User> getPublicUser(User user) {
    if (user == null) {
      return new RestResponse<>();
    }

    List<UserGroup> userGroups = user.getUserGroups();
    user = userService.getUserSummary(user);
    setUserGroupSummary(user, userGroups);
    return new RestResponse<>(user);
  }

  private List<PublicUser> getPublicUsers(List<User> users, String accountId) {
    if (isEmpty(users)) {
      return Collections.emptyList();
    }
    List<PublicUser> publicUserList = new ArrayList<>();
    AtomicInteger index = new AtomicInteger(0);
    users.forEach(user -> {
      List<UserGroup> userGroups = user.getUserGroups();
      boolean inviteAccepted = checkIfInvitationIsAccepted(user, accountId);
      if (!inviteAccepted) {
        maskUserNameWithPendingInvitation(user);
      }
      user = userService.getUserSummary(user);
      setUserGroupSummary(user, userGroups);
      users.set(index.getAndIncrement(), user);
      publicUserList.add(PublicUser.builder().user(user).inviteAccepted(inviteAccepted).build());
    });
    return publicUserList;
  }

  private void maskUserNameWithPendingInvitation(User user) {
    user.setName("User with pending invitation");
  }

  private boolean checkIfInvitationIsAccepted(User user, String accountId) {
    List<Account> accounts = user.getAccounts();
    if (!(isNotEmpty(accounts) && accounts.contains(accountService.get(accountId)))) {
      return false;
    }
    return true;
  }

  private void setUserGroupSummary(User user, List<UserGroup> userGroups) {
    if (user == null) {
      return;
    }
    List<UserGroup> userGroupSummaryList = userGroupService.getUserGroupSummary(userGroups);
    user.setUserGroups(userGroupSummaryList);
  }

  /**
   * The type Reset password request.
   */
  @NoArgsConstructor
  public static class ResetPasswordRequest {
    private String email;
    @Getter @Setter private Boolean isNG = false;

    /**
     * Gets email.
     *
     * @return the email
     */
    public String getEmail() {
      return email;
    }

    /**
     * Sets email.
     *
     * @param email the email
     */
    public void setEmail(String email) {
      this.email = email;
    }
  }

  /**
   * The type Update password request.
   */
  public static class UpdatePasswordRequest {
    private String password;

    /**
     * Gets password.
     *
     * @return the password
     */
    public String getPassword() {
      return password;
    }

    /**
     * Sets password.
     *
     * @param password the password
     */
    public void setPassword(String password) {
      this.password = password;
    }
  }

  @Data
  public static class ResendInvitationEmailRequest {
    @NotBlank private String email;
  }

  private void validateCaptchaToken(String captchaToken) {
    if (StringUtils.isEmpty(captchaToken)) {
      return;
    }

    reCaptchaVerifier.verify(captchaToken);
  }
}
