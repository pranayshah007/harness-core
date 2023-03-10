/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.security.dto.PrincipalType.USER;

import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.mappers.AccountMapper;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.user.NGRemoveUserFilter;
import io.harness.ng.core.user.PasswordChangeDTO;
import io.harness.ng.core.user.PasswordChangeResponse;
import io.harness.ng.core.user.TwoFactorAdminOverrideSettings;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserRequestDTO;
import io.harness.ng.core.user.UtmInfo;
import io.harness.rest.RestResponse;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimUser;
import io.harness.scim.service.ScimUserService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.dto.UserPrincipal;
import io.harness.signup.dto.SignupInviteDTO;
import io.harness.user.remote.UserFilterNG;

import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.security.annotations.AuthRule;
import software.wings.security.authentication.TwoFactorAuthenticationManager;
import software.wings.security.authentication.TwoFactorAuthenticationMechanism;
import software.wings.security.authentication.TwoFactorAuthenticationSettings;
import software.wings.service.impl.UserServiceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Api(value = "/ng/user", hidden = true)
@Path("/ng/user")
@Produces("application/json")
@Consumes("application/json")
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class UserResourceNG {
  private final UserService userService;
  private final SignupService signupService;
  private final UserServiceHelper userServiceHelper;
  private final TwoFactorAuthenticationManager twoFactorAuthenticationManager;
  private final AccountService accountService;
  private final ScimUserService scimUserService;
  private static final String COMMUNITY_ACCOUNT_EXISTS = "A community account already exists";
  private static final String ACCOUNT_ADMINISTRATOR_USER_GROUP = "Account Administrator";

  @Inject private FeatureFlagService featureFlagService;

  @POST
  public RestResponse<UserInfo> createNewUserAndSignIn(UserRequestDTO userRequest) {
    User user = convertUserRequesttoUser(userRequest);
    String accountId = user.getDefaultAccountId();

    User createdUser = userService.createNewUserAndSignIn(user, accountId);

    return new RestResponse<>(convertUserToNgUser(createdUser));
  }

  @POST
  @Path("/signup-invite")
  public RestResponse<SignupInviteDTO> createSignupInvite(SignupInviteDTO request) {
    UserInvite userInvite = userService.createNewSignupInvite(request);
    return new RestResponse<>(convertUserInviteToSignupInviteDTO(userInvite));
  }

  @PUT
  @Path("/signup-invite")
  public RestResponse<UserInfo> completeSignupInvite(@QueryParam("email") String email) {
    UserInvite userInviteInDB = signupService.getUserInviteByEmail(email);
    if (userInviteInDB == null || !userInviteInDB.isCreatedFromNG()) {
      throw new InvalidRequestException("Can't complete signup, due to invalid user invite");
    }

    User createdUser = userService.completeNewSignupInvite(userInviteInDB);
    UserInfo userInfo = convertUserToNgUser(createdUser);
    userInfo.setIntent(userInviteInDB.getIntent());

    if (isNotEmpty(userInviteInDB.getSignupAction())) {
      userInfo.setSignupAction(userInviteInDB.getSignupAction());
    }

    if (isNotEmpty(userInviteInDB.getEdition())) {
      userInfo.setEdition(userInviteInDB.getEdition());
    }

    if (isNotEmpty(userInviteInDB.getBillingFrequency())) {
      userInfo.setBillingFrequency(userInviteInDB.getBillingFrequency());
    }

    return new RestResponse<>(userInfo);
  }

  @POST
  @Path("/signup-invite/community")
  public RestResponse<UserInfo> createCommunityUserAndCompleteSignup(SignupInviteDTO request) {
    try {
      signupService.validateEmail(request.getEmail());
    } catch (WingsException exception) {
      throw new InvalidRequestException(
          String.format("%s is an invalid email.Please add a valid email and try again.", request.getEmail()),
          exception);
    }
    if (!accountService.listAllAccountsWithoutTheGlobalAccount().isEmpty()) {
      throw new InvalidRequestException(COMMUNITY_ACCOUNT_EXISTS);
    }

    userService.createNewSignupInvite(request);

    UserInvite userInviteInDB = signupService.getUserInviteByEmail(request.getEmail());

    User createdUser = userService.completeCommunitySignup(userInviteInDB);
    UserInfo userInfo = convertUserToNgUser(createdUser);
    userInfo.setIntent(userInviteInDB.getIntent());

    if (isNotEmpty(userInviteInDB.getSignupAction())) {
      userInfo.setSignupAction(userInviteInDB.getSignupAction());
    }

    if (isNotEmpty(userInviteInDB.getEdition())) {
      userInfo.setEdition(userInviteInDB.getEdition());
    }

    if (isNotEmpty(userInviteInDB.getBillingFrequency())) {
      userInfo.setBillingFrequency(userInviteInDB.getBillingFrequency());
    }

    return new RestResponse<>(userInfo);
  }

  @GET
  @Path("/signup-invite")
  public RestResponse<SignupInviteDTO> getSignupInvite(@QueryParam("email") String email) {
    UserInvite userInvite = signupService.getUserInviteByEmail(email);
    return new RestResponse<>(convertUserInviteToSignupInviteDTO(userInvite));
  }

  @DELETE
  public RestResponse<Boolean> deleteUser(
      @QueryParam("accountId") String accountId, @QueryParam("userId") String userId) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_DELETION_V2, accountId)) {
      userServiceHelper.deleteUserFromNG(userId, accountId, NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK);
      if (!userService.isUserPartOfAnyUserGroupInCG(userId, accountId)) {
        log.warn("User {}, is being deleted from CG, since he is not part of any user-groups in CG",
            userService.get(userId).getEmail());
        userService.delete(accountId, userId);
      }
    } else {
      userService.delete(accountId, userId);
    }
    return new RestResponse<>(true);
  }

  @GET
  @Path("/scim/search")
  public RestResponse<ScimListResponse<ScimUser>> searchScimUsers(@QueryParam("accountId") String accountId,
      @QueryParam("searchQuery") String searchQuery, @QueryParam("count") Integer count,
      @QueryParam("startIndex") Integer startIndex) {
    return new RestResponse<>(scimUserService.searchUser(accountId, searchQuery, count, startIndex));
  }

  @PUT
  @Path("/scim/disabled")
  public RestResponse<Boolean> disableScimUser(@QueryParam("accountId") String accountId,
      @QueryParam("userId") String userId, @QueryParam("disabled") boolean disabled) {
    if (featureFlagService.isEnabled(FeatureName.PL_USER_DELETION_V2, accountId)) {
      disabled = false;
    }
    return new RestResponse<>(scimUserService.changeScimUserDisabled(accountId, userId, disabled));
  }

  @PUT
  @Path("/scim/patch")
  public RestResponse<ScimUser> patchUpdateScimUser(
      @QueryParam("accountId") String accountId, @QueryParam("userId") String userId, @Body PatchRequest patchRequest) {
    return new RestResponse<>(scimUserService.updateUser(accountId, userId, patchRequest));
  }

  @PUT
  @Path("/scim/patch/details")
  public RestResponse<ScimUser> patchUpdateScimUserDetails(
      @QueryParam("accountId") String accountId, @QueryParam("userId") String userId, @Body PatchRequest patchRequest) {
    return new RestResponse<>(scimUserService.updateUserDetails(accountId, userId, patchRequest));
  }

  @PUT
  @Path("/scim")
  public RestResponse<Boolean> updateScimUser(
      @QueryParam("accountId") String accountId, @QueryParam("userId") String userId, @Body ScimUser scimUser) {
    scimUserService.updateUser(userId, accountId, scimUser);
    return new RestResponse<>(true);
  }

  @POST
  @Path("/oauth")
  public RestResponse<UserInfo> createNewOAuthUserAndSignIn(UserRequestDTO userRequest) {
    User user = convertUserRequesttoUser(userRequest);
    String accountId = user.getDefaultAccountId();

    User createdUser = userService.createNewOAuthUser(user, accountId);

    return new RestResponse<>(convertUserToNgUser(createdUser));
  }

  @GET
  @Path("/search")
  public RestResponse<PageResponse<UserInfo>> list(@BeanParam PageRequest<User> pageRequest,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("searchTerm") String searchTerm,
      @QueryParam("requireAdminStatus") @DefaultValue("false") boolean requireAdminStatus) {
    Integer offset = Integer.valueOf(pageRequest.getOffset());
    Integer pageSize = pageRequest.getPageSize();

    List<User> userList =
        userService.listUsers(pageRequest, accountId, searchTerm, offset, pageSize, requireAdminStatus, false, false);

    PageResponse<UserInfo> pageResponse = aPageResponse()
                                              .withOffset(offset.toString())
                                              .withLimit(pageSize.toString())
                                              .withResponse(convertUserToNgUser(userList, requireAdminStatus))
                                              .withTotal(userService.getTotalUserCount(accountId, true))
                                              .build();

    return new RestResponse<>(pageResponse);
  }

  @GET
  @Path("/{userId}")
  public RestResponse<Optional<UserInfo>> getUser(@PathParam("userId") String userId,
      @QueryParam("includeSupportAccounts") @DefaultValue("false") Boolean includeSupportAccounts) {
    try {
      User user = userService.get(userId, includeSupportAccounts);
      return new RestResponse<>(Optional.ofNullable(convertUserToNgUser(user)));
    } catch (UnauthorizedException ex) {
      log.warn("User is not found in database {}", userId);
      return new RestResponse<>(Optional.empty());
    }
  }

  @GET
  @Path("email/{emailId}")
  public RestResponse<Optional<UserInfo>> getUserByEmailId(@PathParam("emailId") String emailId) {
    User user = userService.getUserByEmail(emailId, false);
    return new RestResponse<>(Optional.ofNullable(convertUserToNgUser(user)));
  }

  @GET
  @Path("user-password-present")
  public RestResponse<Boolean> getUserByEmailId(
      @QueryParam("accountId") String accountId, @QueryParam("emailId") String emailId) {
    return new RestResponse<>(userService.isUserPasswordPresent(accountId, emailId));
  }

  @PUT
  @Path("invites/create-user")
  public RestResponse<Boolean> createUserForInvite(@Body @NotNull UserInviteDTO userInvite,
      @QueryParam("isScimInvite") boolean isScimInvite,
      @QueryParam("shouldSendTwoFactorAuthResetEmail") boolean shouldSendTwoFactorAuthResetEmail) {
    userService.completeNGInvite(userInvite, isScimInvite, shouldSendTwoFactorAuthResetEmail);
    return new RestResponse<>(true);
  }

  @GET
  @Path("limit-check")
  public RestResponse<Boolean> getUserLimitCheckForAccount(
      @QueryParam("accountId") String accountId, @QueryParam("email") String email) {
    return new RestResponse<>(userService.checkIfUserLimitHasReached(accountId, email));
  }

  @PUT
  @Path("password")
  @ApiOperation(value = "Change user password", nickname = "changeUserPassword")
  @Operation(operationId = "changeUserPassword", summary = "Change User Password",
      description = "Changes user password.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully changed password of the user")
      })
  public RestResponse<PasswordChangeResponse>
  changeUserPassword(@QueryParam("userId") String userId, PasswordChangeDTO passwordChangeDTO) {
    return new RestResponse<>(userService.changePassword(userId, passwordChangeDTO));
  }

  @POST
  @Path("/batch")
  public RestResponse<List<UserInfo>> listUsers(@QueryParam("accountId") String accountId, UserFilterNG userFilterNG) {
    Set<User> userSet = new HashSet<>();

    if (!isEmpty(userFilterNG.getUserIds())) {
      userSet.addAll(userService.getUsers(userFilterNG.getUserIds(), accountId));
    }
    if (!isEmpty(userFilterNG.getEmailIds())) {
      userSet.addAll(userService.getUsersByEmail(userFilterNG.getEmailIds(), accountId));
    }
    return new RestResponse<>(convertUserToNgUser(new ArrayList<>(userSet), false));
  }

  @POST
  @Path("/batch-emails")
  public RestResponse<List<UserInfo>> listUsersEmails(@QueryParam("accountId") String accountId) {
    List<User> emails = userService.getUsersEmails(accountId);

    return new RestResponse<>(convertUserToNgUser(new ArrayList<>(emails), false));
  }

  @PUT
  @Path("/user")
  @ApiOperation(value = "Update user information", nickname = "updateUserInfo")
  @Operation(operationId = "updateUserInfo", summary = "Update user info",
      description = "Updates user information for the given user ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully updated user information")
      })
  public RestResponse<Optional<UserInfo>>
  updateUser(@Body UserInfo userInfo) {
    User user = convertNgUserToUserWithNameUpdated(userInfo);
    user = userService.update(user);
    return new RestResponse<>(Optional.ofNullable(convertUserToNgUser(user)));
  }

  @GET
  @Path("/user-account")
  public RestResponse<Boolean> isUserInAccount(
      @NotNull @QueryParam("accountId") String accountId, @QueryParam("userId") String userId) {
    try {
      User user = userService.getUserFromCacheOrDB(userId);
      boolean isUserInAccount = false;
      if (user != null && user.getAccounts() != null) {
        isUserInAccount = user.getAccounts().stream().anyMatch(account -> account.getUuid().equals(accountId));
      }
      if (!isUserInAccount && user != null) {
        isUserInAccount = userService.ifUserHasAccessToSupportAccount(userId, accountId);
      }
      if (!isUserInAccount) {
        log.error(String.format("User %s does not belong to account %s", userId, accountId));
      }
      return new RestResponse<>(isUserInAccount);
    } catch (Exception ex) {
      log.error(String.format("User %s does not belong to account %s", userId, accountId), ex);
      return new RestResponse<>(false);
    }
  }

  @POST
  @Path("/user-account")
  public RestResponse<Boolean> addUserToAccount(
      @QueryParam("userId") String userId, @QueryParam("accountId") String accountId) {
    userService.addUserToAccount(userId, accountId);
    return new RestResponse<>(true);
  }

  @GET
  @Path("/two-factor-auth/{auth-mechanism}")
  @ApiOperation(value = "Create two factor authorization setting", nickname = "createTwoFactorAuth")
  @Operation(operationId = "createTwoFactorAuth", summary = "Create two factor authorization setting",
      description = "Creates Two-Factor authorization settings.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully created two factor authorization setting for an account")
      })
  public RestResponse<Optional<TwoFactorAuthenticationSettings>>
  getTwoFactorAuthSettings(@PathParam("auth-mechanism") TwoFactorAuthenticationMechanism authMechanism,
      @QueryParam("emailId") String emailId) {
    return new RestResponse<>(Optional.ofNullable(twoFactorAuthenticationManager.createTwoFactorAuthenticationSettings(
        userService.getUserByEmail(emailId), authMechanism)));
  }

  @PUT
  @Path("/enable-two-factor-auth")
  @ApiOperation(value = "Enable two factor authorization", nickname = "enableTwoFactorAuth")
  @Operation(operationId = "enableTwoFactorAuth", summary = "Enables Two-Factor authorization",
      description = "Enables Two-Factor authorization.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Successfully enabled two factor authorization for an account")
      })
  public RestResponse<Optional<UserInfo>>
  enableTwoFactorAuth(@QueryParam("emailId") String emailId, @Body TwoFactorAuthenticationSettings settings) {
    return new RestResponse<>(
        Optional.ofNullable(convertUserToNgUser(twoFactorAuthenticationManager.enableTwoFactorAuthenticationSettings(
            userService.getUserByEmail(emailId), settings))));
  }

  @PUT
  @Path("/disable-two-factor-auth")
  @ApiOperation(value = "Disable two factor authorization", nickname = "disableTwoFactorAuth")
  @Operation(operationId = "disableTwoFactorAuth", summary = "Disable two factor authorization",
      description = "Disables Two-Factor authorization.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Successfully disabled two factor authorization for an account")
      })
  public RestResponse<Optional<UserInfo>>
  disableTwoFactorAuth(@QueryParam("emailId") String emailId) {
    return new RestResponse<>(Optional.ofNullable(convertUserToNgUser(
        twoFactorAuthenticationManager.disableTwoFactorAuthentication(userService.getUserByEmail(emailId)))));
  }

  @GET
  @Path("reset-two-factor-auth/{userId}")
  @ApiOperation(value = "Resend email for two factor authorization", nickname = "resetTwoFactorAuth")
  @AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
  public RestResponse<Boolean> reset2fa(
      @PathParam("userId") @NotEmpty String userId, @QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(twoFactorAuthenticationManager.sendTwoFactorAuthenticationResetEmail(userId));
  }

  @PUT
  @Path("/{userId}/verified")
  public RestResponse<Boolean> verifyToken(@PathParam("userId") String userId) {
    userService.setUserEmailVerified(userId);
    return new RestResponse<>(true);
  }

  private List<UserInfo> convertUserToNgUser(List<User> userList, boolean requireAdminStatus) {
    return userList.stream()
        .map(user
            -> UserInfo.builder()
                   .email(user.getEmail())
                   .name(user.getName())
                   .uuid(user.getUuid())
                   .locked(user.isUserLocked())
                   .admin(requireAdminStatus
                       && Optional.ofNullable(user.getUserGroups())
                              .map(x
                                  -> x.stream().anyMatch(
                                      y -> ACCOUNT_ADMINISTRATOR_USER_GROUP.equals(y.getName()) && y.isDefault()))
                              .orElse(false))
                   .twoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
                   .emailVerified(user.isEmailVerified())
                   .disabled(user.isDisabled())
                   .externallyManaged(user.isImported())
                   .build())
        .collect(Collectors.toList());
  }

  private UserInfo convertUserToNgUser(User user) {
    if (user == null) {
      return null;
    }
    return UserInfo.builder()
        .email(user.getEmail())
        .name(user.getName())
        .familyName(user.getFamilyName())
        .givenName(user.getGivenName())
        .uuid(user.getUuid())
        .locked(user.isUserLocked())
        .disabled(user.isDisabled())
        .externallyManaged(user.isImported())
        .defaultAccountId(user.getDefaultAccountId())
        .twoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
        .createdAt(user.getCreatedAt())
        .lastUpdatedAt(user.getLastUpdatedAt())
        .emailVerified(user.isEmailVerified())
        .accounts(user.getAccounts()
                      .stream()
                      .map(account -> AccountMapper.toGatewayAccountRequest(account))
                      .collect(Collectors.toList()))
        .token(user.getToken())
        .admin(
            Optional.ofNullable(user.getUserGroups())
                .map(x
                    -> x.stream().anyMatch(y -> ACCOUNT_ADMINISTRATOR_USER_GROUP.equals(y.getName()) && y.isDefault()))
                .orElse(false))
        .utmInfo(user.getUtmInfo() != null ? UtmInfo.builder()
                                                 .utmCampaign(user.getUtmInfo().getUtmCampaign())
                                                 .utmContent(user.getUtmInfo().getUtmContent())
                                                 .utmMedium(user.getUtmInfo().getUtmMedium())
                                                 .utmSource(user.getUtmInfo().getUtmSource())
                                                 .utmTerm(user.getUtmInfo().getUtmTerm())
                                                 .build()
                                           : UtmInfo.builder().build())
        .build();
  }

  private User convertUserRequesttoUser(UserRequestDTO userRequest) {
    if (userRequest == null) {
      return null;
    }

    return User.Builder.anUser()
        .email(userRequest.getEmail())
        .name(userRequest.getName())
        .twoFactorAuthenticationEnabled(userRequest.isTwoFactorAuthenticationEnabled())
        .passwordHash(userRequest.getPasswordHash())
        .accountName(userRequest.getAccountName())
        .companyName(userRequest.getCompanyName())
        .accounts(userRequest.getAccounts()
                      .stream()
                      .map(account -> AccountMapper.fromAccountDTO(account))
                      .collect(Collectors.toList()))
        .emailVerified(userRequest.isEmailVerified())
        .defaultAccountId(userRequest.getDefaultAccountId())
        .build();
  }

  private User convertNgUserToUserWithNameUpdated(UserInfo userInfo) {
    if (userInfo == null) {
      return null;
    }
    User user = userService.getUserByEmail(userInfo.getEmail());
    user.setName(userInfo.getName());
    return user;
  }

  @PUT
  @Path("two-factor-admin-override-settings")
  public RestResponse<Boolean> setTwoFactorAuthAtAccountLevel(
      @QueryParam("accountId") @NotEmpty String accountId, @NotNull TwoFactorAdminOverrideSettings settings) {
    // Trying Override = true
    if (settings.isAdminOverrideTwoFactorEnabled()) {
      if (SourcePrincipalContextBuilder.getSourcePrincipal() == null
          || !USER.equals(SourcePrincipalContextBuilder.getSourcePrincipal().getType())) {
        throw new InvalidRequestException("Unable to fetch current user");
      }

      UserPrincipal userPrincipal = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
      User user = userService.getUserByEmail(userPrincipal.getEmail());

      if (twoFactorAuthenticationManager.isTwoFactorEnabled(accountId, user)) {
        return new RestResponse(twoFactorAuthenticationManager.overrideTwoFactorAuthentication(accountId, settings));
      } else {
        throw new InvalidRequestException("Admin has 2FA disabled. Please enable to enforce 2FA on users.");
      }
    }
    // Trying Override = false
    else {
      return new RestResponse(twoFactorAuthenticationManager.overrideTwoFactorAuthentication(accountId, settings));
    }
  }

  @GET
  @Path("feature-flags/{accountId}")
  public RestResponse<Collection<FeatureFlag>> getFeatureFlags(@PathParam("accountId") String accountId) {
    return new RestResponse<>(accountService.getFeatureFlags(accountId));
  }

  @PUT
  @Path("/unlock-user")
  @ApiOperation(value = "Unlock user", nickname = "unlockUser")
  @Operation(operationId = "unlockUser", summary = "Unlocks a locked user",
      description = "Unlocks a locked user of an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully unlocked locked user of an account")
      })
  public RestResponse<Optional<UserInfo>>
  unlockUser(@NotEmpty @QueryParam("email") String email, @NotEmpty @QueryParam("accountId") String accountId) {
    return new RestResponse<>(Optional.ofNullable(convertUserToNgUser(userService.unlockUser(email, accountId))));
  }

  private SignupInviteDTO convertUserInviteToSignupInviteDTO(UserInvite userInvite) {
    if (userInvite == null) {
      return null;
    }
    return SignupInviteDTO.builder()
        .email(userInvite.getEmail())
        .completed(userInvite.isCompleted())
        .createdFromNG(userInvite.isCreatedFromNG())
        .build();
  }
}
