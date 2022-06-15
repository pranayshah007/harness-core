/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.scim.PatchOperation;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimMultiValuedObject;
import io.harness.scim.ScimUser;
import io.harness.scim.ScimUserValuedObject;
import io.harness.scim.service.ScimUserService;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class NGScimUserServiceImpl implements ScimUserService {
  private static final String ACCOUNT_VIEWER_ROLE = "_account_viewer";
  private static final String GIVEN_NAME = "givenName";
  private static final String FAMILY_NAME = "familyName";
  private static final String VALUE = "value";
  private static final String PRIMARY = "primary";
  private final NgUserService ngUserService;
  private final InviteService inviteService;
  private final UserGroupService userGroupService;

  @Override
  public Response createUser(ScimUser userQuery, String accountId) {
    log.info("NGSCIM: Creating user call for accountId {} with query {}", accountId, userQuery);
    String primaryEmail = getPrimaryEmail(userQuery);

    Optional<UserInfo> userInfoOptional = ngUserService.getUserInfoByEmailFromCG(primaryEmail);
    Optional<UserMetadataDTO> userOptional =
        Optional.ofNullable(userInfoOptional
                                .map(user
                                    -> UserMetadataDTO.builder()
                                           .uuid(user.getUuid())
                                           .name(user.getName())
                                           .email(user.getEmail())
                                           .locked(user.isLocked())
                                           .disabled(user.isDisabled())
                                           .externallyManaged(user.isExternallyManaged())
                                           .build())
                                .orElse(null));
    UserMetadataDTO user;
    if (checkUserPartOfAccount(accountId, userInfoOptional) && userOptional.isPresent()) {
      user = userOptional.get();
      userQuery.setId(user.getUuid());
      userQuery.setActive(true);
      if (shouldUpdateUser(userQuery, user)) {
        updateUser(user.getUuid(), accountId, userQuery);
        log.info("NGSCIM: Creating user call for accountId {} with updation {}", accountId, userQuery);
      } else {
        log.info("NGSCIM: Creating user call for accountId {} with conflict {}", accountId, userQuery);
      }
      ngUserService.addUserToScope(
          user.getUuid(), Scope.of(accountId, null, null), null, null, UserMembershipUpdateSource.SYSTEM);
      return Response.status(Response.Status.CREATED).entity(getUser(user.getUuid(), accountId)).build();
    } else {
      String userName = getName(userQuery);
      Invite invite = Invite.builder()
                          .accountIdentifier(accountId)
                          .approved(true)
                          .email(primaryEmail)
                          .name(userName)
                          .roleBindings(Collections.singletonList(
                              RoleBinding.builder().roleIdentifier(ACCOUNT_VIEWER_ROLE).build()))
                          .inviteType(InviteType.SCIM_INITIATED_INVITE)
                          .build();

      inviteService.create(invite, true);

      userOptional = ngUserService.getUserByEmail(primaryEmail, true);

      if (userOptional.isPresent()) {
        user = userOptional.get();
        userQuery.setId(user.getUuid());
        log.info("NGSCIM: Completed creating user call for accountId {} with query {}", accountId, userQuery);
        return Response.status(Response.Status.CREATED).entity(getUser(user.getUuid(), accountId)).build();
      } else {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
    }
  }

  private boolean checkUserPartOfAccount(String accountId, Optional<UserInfo> userInfoOptional) {
    if (userInfoOptional.isPresent() && userInfoOptional.get().getAccounts() != null) {
      return userInfoOptional.get().getAccounts().stream().anyMatch(account -> accountId.equals(account.getUuid()));
    }
    return false;
  }

  @Override
  public ScimUser getUser(String userId, String accountId) {
    Optional<UserInfo> userInfo = ngUserService.getUserById(userId);
    return userInfo.map(this::buildUserResponse).orElse(null);
  }

  @Override
  public ScimListResponse<ScimUser> searchUser(String accountId, String filter, Integer count, Integer startIndex) {
    log.info("NGSCIM: searching users accountId {}, search query {}", accountId, filter);
    ScimListResponse<ScimUser> result = ngUserService.searchScimUsersByEmailQuery(accountId, filter, count, startIndex);
    log.info("NGSCIM: completed search. accountId {}, search query {}, resultSize: {}", accountId, filter,
        result.getTotalResults());
    return result;
  }

  @Override
  public void deleteUser(String userId, String accountId) {
    log.info("NGSCIM: deleting for accountId {} the user {}", accountId, userId);
    ngUserService.removeUser(userId, accountId);
    log.info("NGSCIM: deleting the user completed for accountId {} the user {}", accountId, userId);
  }

  @Override
  public ScimUser updateUser(String accountId, String userId, PatchRequest patchRequest) {
    log.info("NGSCIM: Updating user : Patch - userId: {}, accountId: {}", userId, accountId);
    // Call CG to update the user as it is
    ngUserService.updateScimUser(accountId, userId, patchRequest);
    Optional<UserMetadataDTO> userMetadataDTOOptional = ngUserService.getUserMetadata(userId);
    if (!userMetadataDTOOptional.isPresent()) {
      log.error(
          "NGSCIM: User not found for updating in NG, updating nothing. userId: {}, accountId: {}", userId, accountId);
    } else {
      patchRequest.getOperations().forEach(patchOperation -> {
        try {
          applyUserUpdateOperation(accountId, userId, userMetadataDTOOptional.get(), patchOperation);
        } catch (Exception ex) {
          log.error("NGSCIM: Failed to update user: {}, patchOperation: {}", userId, patchOperation, ex);
        }
      });
    }
    return getUser(userId, accountId);
  }

  @Override
  public Response updateUser(String userId, String accountId, ScimUser scimUser) {
    log.info("NGSCIM: Updating user - userId: {}, accountId: {}", userId, accountId);
    Optional<UserInfo> userInfo = ngUserService.getUserById(userId);
    Optional<UserMetadataDTO> userMetadataDTOOptional = ngUserService.getUserMetadata(userId);
    if (!userInfo.isPresent() || !userMetadataDTOOptional.isPresent()) {
      log.error("NGSCIM: User is not found. userId: {}, accountId: {}", userId, accountId);
      return Response.status(Response.Status.NOT_FOUND).build();
    } else {
      boolean cgUpdateResult = ngUserService.updateScimUser(accountId, userId, scimUser);
      if (!cgUpdateResult) {
        log.error("NGSCIM: User is not in CG found. userId: {}, accountId: {}", userId, accountId);
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      String displayName = getName(scimUser);
      UserInfo existingUser = userInfo.get();
      UserMetadataDTO userMetadata = userMetadataDTOOptional.get();

      if (StringUtils.isNotEmpty(displayName) && !displayName.equals(existingUser.getName())) {
        userMetadata.setName(displayName);
        userMetadata.setExternallyManaged(true);
        ngUserService.updateUserMetadata(userMetadata);
      }

      if (scimUser.getActive() != null && scimUser.getActive() == existingUser.isDisabled()) {
        log.info("NGSCIM: Updated user's {}, active: {}", userId, scimUser.getActive());
        changeScimUserDisabled(accountId, userId, !scimUser.getActive());
      }
      log.info("NGSCIM: Updating user completed - userId: {}, accountId: {}", userId, accountId);

      // @Todo: Not handling GIVEN_NAME AND FAMILY_NAME. Add if we need to persist them
      return Response.status(Response.Status.OK).entity(getUser(userId, accountId)).build();
    }
  }

  @Override
  public boolean changeScimUserDisabled(String accountId, String userId, boolean disabled) {
    if (disabled) {
      // OKTA doesn't send an explicit delete user request but only makes active true/false.
      // We need to remove the user completely if active=false as we do not have any first
      // class support for a disabled user vs a deleted user
      ngUserService.removeUser(userId, accountId);
    } else {
      // This is to keep CG implementation working as it is.
      ngUserService.updateUserDisabled(accountId, userId, disabled);
    }
    return true;
  }

  private void applyUserUpdateOperation(String accountId, String userId, UserMetadataDTO userMetadataDTO,
      PatchOperation patchOperation) throws JsonProcessingException {
    // Not sure why this needs to be done for displayName as well as ScimMultiValuedObject
    // Relying on CG implementation as it has been around for a while
    if ("displayName".equals(patchOperation.getPath())) {
      userMetadataDTO.setName(patchOperation.getValue(String.class));
      userMetadataDTO.setExternallyManaged(true);
      ngUserService.updateUserMetadata(userMetadataDTO);
    }
    if (patchOperation.getValue(ScimMultiValuedObject.class) != null
        && patchOperation.getValue(ScimMultiValuedObject.class).getDisplayName() != null) {
      // @Todo: Check with Ujjawal why CG has patchOperation.getValue(String.class)
      userMetadataDTO.setName(patchOperation.getValue(ScimMultiValuedObject.class).getDisplayName());
      userMetadataDTO.setExternallyManaged(true);
      ngUserService.updateUserMetadata(userMetadataDTO);
    }

    if ("active".equals(patchOperation.getPath()) && patchOperation.getValue(Boolean.class) != null) {
      changeScimUserDisabled(accountId, userId, !patchOperation.getValue(Boolean.class));
    }

    if (patchOperation.getValue(ScimUserValuedObject.class) != null) {
      changeScimUserDisabled(accountId, userId, !(patchOperation.getValue(ScimUserValuedObject.class)).isActive());
    } else {
      // Not supporting any other updates as of now.
      log.error("NGSCIM: Unexpected patch operation received: accountId: {}, userId: {}, patchOperation: {}", accountId,
          userId, patchOperation);
    }
  }

  private void removeUserFromAllNGScimGroups(String accountId, String userId) {
    List<UserGroup> userGroups = userGroupService.getExternallyManagedGroups(accountId);
    userGroups.forEach(userGroup -> {
      Scope scope =
          Scope.of(userGroup.getAccountIdentifier(), userGroup.getOrgIdentifier(), userGroup.getProjectIdentifier());
      if (userGroupService.checkMember(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
              scope.getProjectIdentifier(), userGroup.getIdentifier(), userId)) {
        log.info("[NGSCIM] Removing user: {} from user group: {} in account: {}", userId, userGroup.getName(),
            userGroup.getAccountIdentifier());
        userGroupService.removeMember(scope, userGroup.getIdentifier(), userId);
      }
    });
  }

  private boolean shouldUpdateUser(ScimUser userQuery, UserMetadataDTO user) {
    return user.isDisabled() || !StringUtils.equals(user.getName(), userQuery.getDisplayName())
        || !StringUtils.equals(user.getEmail(), userQuery.getUserName());
  }

  private ScimUser buildUserResponse(UserInfo user) {
    ScimUser userResource = new ScimUser();
    userResource.setId(user.getUuid());

    userResource.setActive(!user.isDisabled());
    userResource.setUserName(user.getEmail());
    userResource.setDisplayName(user.getName());

    // @Todo - Check with Ujjawal on this if we need GIVEN_NAME & FAMILY_NAME
    Map<String, String> nameMap = new HashMap<String, String>() {
      {
        //        put(GIVEN_NAME, user.getGivenName() != null ? user.getGivenName() : user.getName());
        //        put(FAMILY_NAME, user.getFamilyName() != null ? user.getFamilyName() : user.getName());
        put("displayName", user.getName());
      }
    };

    Map<String, Object> emailMap = new HashMap<String, Object>() {
      {
        put(VALUE, user.getEmail());
        put(PRIMARY, true);
      }
    };

    userResource.setEmails(JsonUtils.asTree(Collections.singletonList(emailMap)));
    userResource.setName(JsonUtils.asTree(nameMap));
    return userResource;
  }

  private String getPrimaryEmail(ScimUser userQuery) {
    return userQuery.getUserName().toLowerCase();
  }

  private String getName(ScimUser user) {
    return StringUtils.isNotEmpty(user.getDisplayName()) ? user.getDisplayName() : getDisplayNameFromName(user);
  }

  private String getDisplayNameFromName(@NotNull ScimUser user) {
    if (user.getName() != null) {
      return user.getName().get(GIVEN_NAME).asText() + " " + user.getName().get(FAMILY_NAME).asText();
    }
    return null;
  }
}