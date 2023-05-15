/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static software.wings.security.PermissionAttribute.PermissionType;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.LogoutResponse;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.event.model.EventType;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.common.beans.Generation;
import io.harness.ng.core.common.beans.UserSource;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.ng.core.switchaccount.RestrictedSwitchAccountInfo;
import io.harness.ng.core.user.PasswordChangeDTO;
import io.harness.ng.core.user.PasswordChangeResponse;
import io.harness.signup.dto.SignupInviteDTO;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.Account;
import software.wings.beans.AccountJoinRequest;
import software.wings.beans.AccountRole;
import software.wings.beans.ApplicationRole;
import software.wings.beans.LicenseInfo;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.ZendeskSsoLoginResponse;
import software.wings.beans.loginSettings.PasswordSource;
import software.wings.beans.loginSettings.PasswordStrengthViolations;
import software.wings.beans.marketplace.MarketPlaceType;
import software.wings.beans.security.UserGroup;
import software.wings.resources.UserResource;
import software.wings.security.JWT_CATEGORY;
import software.wings.security.UserPermissionInfo;
import software.wings.security.authentication.TwoFactorAuthenticationSettings;
import software.wings.security.authentication.oauth.OauthUserInfo;
import software.wings.service.intfc.ownership.OwnedByAccount;

import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by anubhaw on 3/28/16.
 */
@OwnedBy(PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public interface UserService extends OwnedByAccount {
  /**
   * Consider the following characters in email as illegal and prohibit trial signup with the following characters
   */
  List<Character> ILLEGAL_CHARACTERS = Collections.unmodifiableList(Arrays.asList(
      '$', '&', ',', '/', ':', ';', '=', '?', '<', '>', '#', '{', '}', '|', '^', '~', '(', ')', ']', '`', '\'', '\"'));

  /**
   * Register a new user with basic account information. Create the account if that
   * account did not exist.
   *
   * @param user the user
   * @return the user
   */
  @ValidationGroups(Create.class) User register(@Valid User user);

  /**
   * Start the trial registration with an user invite.
   *
   * @param userInvite user invite with registration info
   */
  boolean trialSignup(UserInvite userInvite);

  /**
   * Used for NG signup to create a new user and login from an NG user object
   */
  User createNewUserAndSignIn(User user, String accountId, Generation generation);

  /**
   * Used for NG signup to create a new oauth user and login from an NG user object
   */
  User createNewOAuthUser(User user, String accountId);

  /**
   * Used for NG signup to create a new user invite
   */
  UserInvite createNewSignupInvite(SignupInviteDTO user);

  /**
   * Used for NG signup to finish provisioning of account, user etc.
   */
  User completeNewSignupInvite(UserInvite userInvite);

  /**
   * Used for NG community edition to finish provisioning of account, user etc.
   */
  User completeCommunitySignup(UserInvite userInvite);

  UserInvite createUserInviteForMarketPlace();

  boolean hasPermission(String accountId, PermissionType permissionType);

  boolean userHasPermission(String accountId, User user, PermissionType permissionType);

  User getUserSummary(User user);

  String getUserInvitationId(String email);

  List<User> getUserSummary(List<User> userList);

  /**
   * Register a new user within an existing account.
   *
   * @param user the user
   * @param account the account the user should be registered to
   * @return the user
   */
  User registerNewUser(User user, Account account);

  /**
   * Match password.
   *
   * @param password the password
   * @param hash     the hash
   * @return true, if successful
   */
  boolean matchPassword(@NotEmpty char[] password, @NotEmpty String hash);

  /**
   * Adds the role.
   *
   * @param userId the user id
   * @param roleId the role id
   * @return the user
   */
  User addRole(@NotEmpty String userId, @NotEmpty String roleId);

  /**
   * Update.
   *
   * @param user the user
   * @return the user
   */
  @ValidationGroups(Update.class) User update(@Valid User user);

  /**
   * Update the user and the associated user groups.
   *
   * @param userId the user id
   * @param userGroups updated user groups
   * @param accountId the account id
   * @param sendNotification send notification flag
   * @return the user
   */
  @ValidationGroups(Update.class)
  User updateUserGroupsOfUser(String userId, List<UserGroup> userGroups, String accountId, boolean sendNotification);

  /**
   * List.
   *
   * @param pageRequest the page request
   * @param loadUserGroups if load user groups is needed
   * @return the page response
   */
  PageResponse<User> list(PageRequest<User> pageRequest, boolean loadUserGroups);

  /**
   * Delete.
   *
   * @param accountId the account id
   * @param userId    the user id
   */
  void delete(@NotEmpty String accountId, @NotEmpty String userId);

  /**
   *
   * @param accountId
   * @param userId
   * @param generation
   */
  boolean delete(@NotEmpty String accountId, @NotEmpty String userId, @NotNull Generation generation);

  /**
   * Deletes the user from both CG and NG.
   *
   * @param accountId the account id
   * @param userId    the user id
   */
  void forceDelete(@NotEmpty String accountId, @NotEmpty String userId);

  /**
   * overrideTwoFactorforAccount
   *
   * @param accountId the account id
   * @param adminOverrideTwoFactorEnabled boolean
   */
  boolean overrideTwoFactorforAccount(String accountId, boolean adminOverrideTwoFactorEnabled);

  boolean isTwoFactorEnabled(String accountId, String usedId);

  User updateUser(String userId, UpdateOperations<User> updateOperations);

  /**
   * Gets the.
   *
   * @param userId the user id
   * @return the user
   */
  User get(@NotEmpty String userId);

  List<User> getUsers(Set<String> userIds);

  /**
   * Gets the user and loads the user groups for the given account.
   *
   * @param accountId the account Id
   * @param userId the user id
   * @return the user
   */
  User get(@NotEmpty String accountId, @NotEmpty String userId);

  void loadSupportAccounts(User user);

  void loadSupportAccounts(User user, Set<String> fieldsToBeIncluded);

  /**
   * Gets user from cache or db.
   *
   * @param userId the user id
   * @return the user from cache or db
   */
  User getUserFromCacheOrDB(String userId);

  /**
   * Evict user from cache.
   *
   * @param userId the user id
   */
  void evictUserFromCache(String userId);

  /**
   * Revoke role.
   *
   * @param userId the user id
   * @param roleId the role id
   * @return the user
   */
  User revokeRole(@NotEmpty String userId, @NotEmpty String roleId);

  /**
   * Add account account.
   *
   * @param account the account
   * @param user    the user
   * @param addUser    whether to add the specified current as account admin to the specified account
   * @return the account
   */
  Account addAccount(Account account, User user, boolean addUser);

  /**
   * Retrieve an user by its email.
   *
   * @param email
   * @return
   */
  User getUserByEmail(String email);

  User getUserByUserId(String accountId, String userId);

  List<User> getUsersByEmail(List<String> emailIds, String accountId);

  User getUserByEmail(String email, String accountId);

  List<User> getUsersEmails(String accountId);

  User getUserWithAcceptedInviteByEmail(String email, String accountId);

  UserInvite getUserInviteByEmailAndAccount(String email, String accountId);

  /**
   * Verify registered or allowed.
   *
   * @param emailAddress the email address
   */
  void verifyRegisteredOrAllowed(String emailAddress);

  /**
   * Resend verification email boolean.
   *
   * @param email the email
   * @return the boolean
   */
  boolean resendVerificationEmail(String email);

  /**
   * Resend the invitation email.
   * @param accountId account id
   * @param email     email address
   * @return the boolean
   */
  boolean resendInvitationEmail(@NotBlank String accountId, @NotBlank String email);

  /**
   * Verify email string.
   *
   * @param token the token
   * @return the string
   */
  boolean verifyToken(String token);

  /**
   * Invite user user invite.
   *
   * @param userInvite the user invite
   * @return the user invite
   */
  List<InviteOperationResponse> inviteUsers(UserInvite userInvite);

  InviteOperationResponse inviteUser(UserInvite userInvite, boolean isInviteAcceptanceRequired, boolean emailVerified);

  void addUserToUserGroups(
      String accountId, User user, List<UserGroup> userGroups, boolean sendNotification, boolean toBeAudited);

  String getUserInviteUrl(UserInvite userInvite, Account account) throws URISyntaxException;

  String getUserInviteUrl(UserInvite userInvite) throws URISyntaxException;

  void sendVerificationEmail(UserInvite userInvite, String url, Map<String, String> params);

  /**
   * Send user invitation email
   *
   * @param userInvite  user invite
   * @param account     account
   */
  void sendNewInvitationMail(UserInvite userInvite, Account account, User user);

  /**
   * Send login invitation email
   *
   * @param user  user
   * @param account     account
   */
  void sendUserInvitationToOnlySsoAccountMail(Account account, User user);

  /**
   * Send added new role email
   *
   * @param user        user
   * @param account     account
   */
  void sendAddedGroupEmail(User user, Account account, List<UserGroup> userGroups);

  /**
   * List invites page response.
   *
   * @param pageRequest the page request
   * @return the page response
   */
  PageResponse<UserInvite> listInvites(PageRequest<UserInvite> pageRequest);

  /**
   * Gets invites from accountId.
   *
   * @param accountId the account id
   * @return the invites
   */
  Query<UserInvite> getInvitesQueryFromAccountId(String accountId);

  /**
   * Gets invite.
   *
   * @param accountId the account id
   * @param inviteId  the invite id
   * @return the invite
   */
  UserInvite getInvite(String accountId, String inviteId);

  /**
   * Complete invite user invite.
   *
   * @param userInvite the user invite
   * @return the user invite
   */
  InviteOperationResponse completeInvite(UserInvite userInvite);

  boolean checkIfUserLimitHasReached(String accountId, String email);

  void completeNGInviteWithAccountLevelData(UserInviteDTO userInvite, boolean shouldSendTwoFactorAuthResetEmail);

  /**
   * Complete NG invite and create user
   *
   * @param userInvite the user invite DTO
   * @param isScimInvite if the Invite is created for a SCIM user
   */
  void completeNGInvite(UserInviteDTO userInvite, boolean isScimInvite, boolean shouldSendTwoFactorAuthResetEmail);

  /**
   * Complete the user invite and login the user in one call.
   *
   * @param userInvite the user invite
   * @return the logged-in user
   */
  User completeInviteAndSignIn(UserInvite userInvite);

  /**
   * Complete the user invite NG and login the user in one call.
   *
   * @param userInvite the user invite
   * @return the logged-in user
   */
  User completeNGInviteAndSignIn(UserInviteDTO userInvite);

  /**
   * Complete the trial user signup. Both the trial account and the account admin user will be created
   * as part of this operation.
   *
   * @param user The user to be signed up for a free trial
   * @param userInvite the user invite.
   * @param licenseInfo
   * @return the completed user invite
   */
  UserInvite completeSignup(User user, UserInvite userInvite, LicenseInfo licenseInfo);

  UserInvite completeSignup(User user, UserInvite userInvite, LicenseInfo licenseInfo, boolean shouldCreateSampleApp);

  User completeMarketPlaceSignup(User user, UserInvite userInvite, MarketPlaceType marketPlaceType);

  /**
   * Complete the trial user signup using oauth. Both the trial account and the account admin user will be created
   * as part of this operation. Also a default SSO settings corresponding to the identity provider will be created
   * and will set up as the default login mechanism.
   *
   * @param userInfo The user to be signed up for a free trial
   * @param oauthProviderName The oauthClient being used for the signup process.
   * @return the new User
   */
  User signUpUserUsingOauth(OauthUserInfo userInfo, String oauthProviderName);

  /**
   * Complete the trial user signup and signin. Both the trial account and the account admin user will be created
   * as part of this operation.
   *
   * @param userInviteId the user invite id.
   * @return the completed user invite
   */
  User completeTrialSignupAndSignIn(String userInviteId);

  User completeTrialSignupAndSignIn(UserInvite userInvite, boolean shouldCreateSampleApp);

  User completePaidSignupAndSignIn(UserInvite userInvite);

  /**
   * Delete invite user invite.
   *
   * @param accountId the account id
   * @param inviteId  the invite id
   * @return the user invite
   */
  UserInvite deleteInvite(String accountId, String inviteId);

  /**
   * Delete existing user invites by email
   * @param accountId the account id
   * @param email     the user email
   * @return the boolean
   */
  boolean deleteInvites(@NotBlank String accountId, @NotBlank String email);

  /**
   * Given a JWT encoded token from the invite email, get the mongo id corresponding to it
   * @param jwtToken  the JWT token encoded in email
   * @return the String
   */
  String getInviteIdFromToken(String jwtToken);

  /**
   * Gets invites from accountId & userGroupId.
   *
   * @param accountId the account id
   * @param userGroupId the userGroup id
   * @return the invites list
   */
  List<UserInvite> getInvitesFromAccountIdAndUserGroupId(String accountId, String userGroupId);

  /**
   * Gets user account role.
   *
   * @param userId    the user id
   * @param accountId the account id
   * @return the user account role
   */
  AccountRole getUserAccountRole(String userId, String accountId);

  /**
   * Gets user application role.
   *
   * @param userId the user id
   * @param appId  the app id
   * @return the user application role
   */
  ApplicationRole getUserApplicationRole(String userId, String appId);

  /**
   * Reset password boolean.
   *
   *
   * @param resetPasswordRequest
   * @return the boolean
   */
  boolean resetPassword(UserResource.ResetPasswordRequest resetPasswordRequest);

  /**
   * Update password via reset-password link.
   *
   * @param resetPasswordToken the reset password token
   * @param password           the password
   * @return the boolean
   */
  boolean updatePassword(String resetPasswordToken, char[] password);

  /**
   * Change password from user profile page
   *
   * @param userId                 User ID for the user submitting the change request
   * @param passwordChangeDTO      DTO with the new password
   * @return the boolean
   */
  PasswordChangeResponse changePassword(String userId, PasswordChangeDTO passwordChangeDTO);

  LogoutResponse logout(String accountId, String userId);

  void logout(User user);

  /**
   * Generate zendesk sso jwt zendesk sso login response.
   *
   * @param returnToUrl the return to url
   * @return the zendesk sso login response
   */
  ZendeskSsoLoginResponse generateZendeskSsoJwt(String returnToUrl);

  /**
   *
   *
   * @param user
   * @param claims Map of claims
   * @param claims Persist accountId present in claims or not
   * @return
   */

  String generateJWTToken(
      User user, Map<String, String> claims, @NotNull JWT_CATEGORY category, boolean persistOldAccountId);

  /**
   *
   * @param jwtToken
   * @param category
   * @return
   */
  User verifyJWTToken(@NotEmpty String jwtToken, @NotNull JWT_CATEGORY category);

  boolean isAccountAdmin(String accountId);

  boolean isAccountAdmin(User user, String accountId);

  boolean isUserAccountAdmin(@NotNull UserPermissionInfo userPermissionInfo, @NotNull String accountId);

  boolean isUserAssignedToAccount(User user, String accountId);

  boolean isUserInvitedToAccount(User user, String accountId);

  boolean isUserVerified(User user);

  List<User> getUsersOfAccount(@NotEmpty String accountId);

  List<User> getUsersWithThisAsPrimaryAccount(@NotEmpty String accountId);

  AuthenticationMechanism getAuthenticationMechanism(User user);

  /**
   * Set the default account for a user. It means the user will land on the default account next time logging in.
   */
  boolean setDefaultAccount(User user, @NotEmpty String accountId);

  boolean updateLead(String email, String accountId);

  boolean deleteUsersByEmailAddress(String accountId, List<String> usersToRetain);

  /**
   * Disable or Enable an user administratively. Once disabled, the user can no longer login.
   */
  boolean enableUser(String accountId, String userId, boolean enabled);

  boolean passwordExpired(String email);

  void sendPasswordExpirationWarning(String email, Integer passExpirationDays);

  String createSignupSecretToken(String email, Integer passExpirationDays);

  void sendPasswordExpirationMail(String email);

  @ValidationGroups(Update.class) User updateUserProfile(@NotNull User user);

  @ValidationGroups(Update.class)
  User addEventToUserMarketoCampaigns(@NotNull String userId, @NotNull EventType eventType);

  @ValidationGroups(Update.class)
  User updateTwoFactorAuthenticationSettings(@NotNull User user, @NotNull TwoFactorAuthenticationSettings settings);

  /**
   *
   */
  boolean accountJoinRequest(AccountJoinRequest accountJoinRequest);

  //  boolean postCustomEvent(String accountId, String event, String additionalInfo);

  boolean postCustomEvent(String accountId, String event);

  PasswordStrengthViolations checkPasswordViolations(
      String resetPasswordToken, PasswordSource passwordSource, String password, String accountId);

  User applyUpdateOperations(User user, UpdateOperations<User> updateOperations);

  User unlockUser(String userId, String accountId);

  void sendAccountLockedNotificationMail(User user, int lockoutExpirationTime);

  Account getAccountByIdIfExistsElseGetDefaultAccount(User user, Optional<String> accountId);

  boolean canEnableOrDisable(User user);

  User createUserWithAccountLevelData(User user, String accountId, UserSource userSource, Generation generation);

  User createUser(User user, String accountId);

  String saveUserInvite(UserInvite userInvite);

  List<User> listUsers(PageRequest pageRequest, String accountId, String searchTerm, Integer offset, Integer pageSize,
      boolean loadUserGroups, boolean includeUsersPendingInviteAcceptance, boolean includeDisabled);

  long getTotalUserCount(String accountId, boolean includeUsersPendingInviteAcceptance);

  InviteOperationResponse checkInviteStatus(UserInvite userInvite, Generation gen);

  void loadUserGroupsForUsers(List<User> users, String accountId);
  boolean isUserPartOfAnyUserGroupInCG(String userId, String accountId);
  boolean isUserPresent(String userId);

  List<User> getUsers(List<String> userIds, String accountId);

  String sanitizeUserName(String name);

  List<UserGroup> getUserGroupsOfUserAudit(String accountId, String userId);

  void addUserToAccount(String userId, String accountId);

  void addUserToAccount(String userId, String accountId, UserSource userSource);

  void setUserEmailVerified(String userId);

  List<Account> getUserAccounts(String userId, int pageIndex, int pageSize, String searchTerm);

  boolean isUserPasswordPresent(String accountId, String emailId);

  URI getInviteAcceptRedirectURL(InviteOperationResponse inviteResponse, UserInvite userInvite, String jwtToken)
      throws URISyntaxException;

  RestrictedSwitchAccountInfo getSwitchAccountInfo(String accountId, String userId);

  io.harness.ng.beans.PageResponse<Account> getUserAccountsAndSupportAccounts(
      String userId, int pageIndex, int pageSize, String searchTerm);

  boolean ifUserHasAccessToSupportAccount(String userId, String accountId);

  void removeAllUserGroupsFromUser(User user, String accountId);

  void updateUserAccountLevelDataForThisGen(String accountId, User user, Generation generation, UserSource userSource);

  boolean updateExternallyManaged(String userId, Generation generation, boolean externallyManaged);
}
