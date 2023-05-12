/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.userprofile.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USER_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USER;

import static java.util.Objects.isNull;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.user.PasswordChangeDTO;
import io.harness.ng.core.user.PasswordChangeResponse;
import io.harness.ng.core.user.TwoFactorAuthMechanismInfo;
import io.harness.ng.core.user.TwoFactorAuthSettingsInfo;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserInfoUpdateDTO;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.userprofile.services.api.UserInfoService;
import io.harness.remote.client.CGRestUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.PL)
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoServiceImpl implements UserInfoService {
  @Inject private UserClient userClient;
  @Inject private NgUserService ngUserService;
  @Inject private AccessControlClient accessControlClient;

  @Override
  public UserInfo getCurrentUser() {
    Optional<String> userEmail = getUserEmail();
    if (userEmail.isPresent()) {
      Optional<UserInfo> userInfo = CGRestUtils.getResponse(userClient.getUserByEmailId(userEmail.get()));
      return userInfo.get();
    } else {
      throw new IllegalStateException("user login required");
    }
  }

  @Override
  public UserInfo update(UserInfo userInfo, String accountIdentifier) {
    if (PrincipalType.USER.equals(SourcePrincipalContextBuilder.getSourcePrincipal().getType())) {
      validateUserUpdateRequestOfUserPrincipalType(userInfo);
    } else if (PrincipalType.SERVICE_ACCOUNT.equals(SourcePrincipalContextBuilder.getSourcePrincipal().getType())) {
      if (isEmpty(userInfo.getEmail())) {
        throw new InvalidRequestException("Email ID is required to update user details");
      }
      Optional<UserInfo> optionalTargetUserInfo =
          CGRestUtils.getResponse(userClient.getUserByEmailId(userInfo.getEmail()));

      if (!optionalTargetUserInfo.isPresent()) {
        throw new InvalidRequestException(String.format("User with %s email ID doesn't exist", userInfo.getEmail()));
      }
      validateUserUpdateRequestOfServiceAccountPrincipalType(optionalTargetUserInfo.get(), accountIdentifier);
      accessControlClient.checkForAccessOrThrow(ResourceScope.builder().accountIdentifier(accountIdentifier).build(),
          Resource.of(USER, optionalTargetUserInfo.get().getUuid()), MANAGE_USER_PERMISSION);
    }

    Optional<UserInfo> updatedUserInfo = CGRestUtils.getResponse(userClient.updateUser(userInfo));
    return updatedUserInfo.get();
  }

  @Override
  public UserInfo update(UserInfoUpdateDTO userInfo, String userId, String accountIdentifier) {
    if (PrincipalType.USER.equals(SourcePrincipalContextBuilder.getSourcePrincipal().getType())) {
      validateUserUpdateRequestOfUserPrincipalTypeV2(userInfo);
    } else if (PrincipalType.SERVICE_ACCOUNT.equals(SourcePrincipalContextBuilder.getSourcePrincipal().getType())) {
      if (isEmpty(userInfo.getEmail())) {
        throw new InvalidRequestException("Email ID is required to update user details");
      }
      Optional<UserInfo> optionalTargetUserInfo =
          CGRestUtils.getResponse(userClient.getUserByEmailId(userInfo.getEmail()));

      if (!optionalTargetUserInfo.isPresent()) {
        throw new InvalidRequestException(String.format("User with %s email ID doesn't exist", userInfo.getEmail()));
      }
      validateUserUpdateRequestOfServiceAccountPrincipalType(optionalTargetUserInfo.get(), accountIdentifier);
      accessControlClient.checkForAccessOrThrow(ResourceScope.builder().accountIdentifier(accountIdentifier).build(),
          Resource.of(USER, optionalTargetUserInfo.get().getUuid()), MANAGE_USER_PERMISSION);
    }
    UserInfo userInfoInput = UserInfo.builder()
                                 .uuid(userId)
                                 .name(userInfo.getName())
                                 .email(userInfo.getEmail())
                                 .givenName(userInfo.getGivenName())
                                 .familyName(userInfo.getFamilyName())
                                 .build();
    Optional<UserInfo> updatedUserInfo = CGRestUtils.getResponse(userClient.updateUser(userInfoInput));
    return updatedUserInfo.get();
  }

  @Override
  public TwoFactorAuthSettingsInfo getTwoFactorAuthSettingsInfo(TwoFactorAuthMechanismInfo twoFactorAuthMechanismInfo) {
    Optional<String> userEmail = getUserEmail();
    if (userEmail.isPresent()) {
      Optional<TwoFactorAuthSettingsInfo> twoFactorAuthSettingsInfo =
          CGRestUtils.getResponse(userClient.getUserTwoFactorAuthSettings(twoFactorAuthMechanismInfo, userEmail.get()));
      return twoFactorAuthSettingsInfo.get();
    } else {
      throw new IllegalStateException("user login required");
    }
  }

  @Override
  public UserInfo updateTwoFactorAuthInfo(TwoFactorAuthSettingsInfo authSettingsInfo) {
    Optional<String> userEmail = getUserEmail();
    if (userEmail.isPresent()) {
      Optional<UserInfo> userInfo =
          CGRestUtils.getResponse(userClient.updateUserTwoFactorAuthInfo(userEmail.get(), authSettingsInfo));
      return userInfo.get();
    } else {
      throw new IllegalStateException("user login required");
    }
  }

  @Override
  public UserInfo disableTFA() {
    Optional<String> userEmail = getUserEmail();
    if (userEmail.isPresent()) {
      Optional<UserInfo> userInfo = CGRestUtils.getResponse(userClient.disableUserTwoFactorAuth(userEmail.get()));
      return userInfo.get();
    } else {
      throw new IllegalStateException("user login required");
    }
  }

  @Override
  public Boolean sendTwoFactorAuthenticationResetEmail(String userId, String accountId) {
    Boolean twoFactorResetEmailSent =
        CGRestUtils.getResponse(userClient.sendTwoFactorAuthenticationResetEmail(userId, accountId));
    if (isNull(twoFactorResetEmailSent)) {
      return false;
    }
    return twoFactorResetEmailSent;
  }

  @Override
  public PasswordChangeResponse changeUserPassword(PasswordChangeDTO passwordChangeDTO) {
    UserInfo user = getCurrentUser();
    return CGRestUtils.getResponse(userClient.changeUserPassword(user.getUuid(), passwordChangeDTO));
  }

  @Override
  public UserInfo unlockUser(String userId, String accountId) {
    Optional<UserMetadataDTO> userMetadataOpt = ngUserService.getUserMetadata(userId);
    if (userMetadataOpt.isPresent()) {
      String email = userMetadataOpt.get().getEmail();
      Optional<UserInfo> userInfo = CGRestUtils.getResponse(userClient.unlockUser(email, accountId));
      return userInfo.get();
    } else {
      throw new InvalidRequestException("userMetadata does not exist");
    }
  }

  private Optional<String> getUserEmail() {
    String userEmail;
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      userEmail = ((UserPrincipal) (SourcePrincipalContextBuilder.getSourcePrincipal())).getEmail();
    } else {
      throw new InvalidRequestException("Current user can be accessed only by 'USER' principal type");
    }
    return Optional.of(userEmail);
  }

  private void validateUserUpdateRequestOfUserPrincipalType(UserInfo userInfo) {
    String userEmail = ((UserPrincipal) (SourcePrincipalContextBuilder.getSourcePrincipal())).getEmail();
    String userInfoEmail = userInfo.getEmail();
    if (userInfoEmail == null) {
      userInfo.setEmail(userEmail);
    } else if (!userEmail.equals(userInfoEmail)) {
      throw new InvalidRequestException("Cannot modify details of different user");
    }
  }

  private void validateUserUpdateRequestOfUserPrincipalTypeV2(UserInfoUpdateDTO userInfo) {
    String userEmail = ((UserPrincipal) (SourcePrincipalContextBuilder.getSourcePrincipal())).getEmail();
    String userInfoEmail = userInfo.getEmail();
    if (userInfoEmail == null) {
      userInfo.setEmail(userEmail);
    } else if (!userEmail.equals(userInfoEmail)) {
      throw new InvalidRequestException("Cannot modify details of different user");
    }
  }

  private void validateUserUpdateRequestOfServiceAccountPrincipalType(
      UserInfo targetUserInfo, String accountIdentifier) {
    if (accountIdentifier == null) {
      throw new InvalidRequestException("Account Identifier is required to update user details");
    }
    if (!verifyUserPartOfAccount(targetUserInfo, accountIdentifier)) {
      throw new InvalidRequestException(String.format(
          "User with %s email ID is not a part of the account %s", targetUserInfo.getEmail(), accountIdentifier));
    }
  }

  private boolean verifyUserPartOfAccount(UserInfo userInfo, String accountIdentifier) {
    return ngUserService.isUserAtScope(
        userInfo.getUuid(), Scope.builder().accountIdentifier(accountIdentifier).build());
  }
}
