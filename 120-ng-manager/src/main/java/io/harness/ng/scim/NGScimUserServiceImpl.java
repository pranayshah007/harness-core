/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.NGConstants.CREATED;
import static io.harness.NGConstants.FAMILY_NAME;
import static io.harness.NGConstants.FORMATTED_NAME;
import static io.harness.NGConstants.GIVEN_NAME;
import static io.harness.NGConstants.LAST_MODIFIED;
import static io.harness.NGConstants.LOCATION;
import static io.harness.NGConstants.PRIMARY;
import static io.harness.NGConstants.RESOURCE_TYPE;
import static io.harness.NGConstants.VALUE;
import static io.harness.NGConstants.VERSION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_NEW_SCIM_STANDARDS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.api.InviteService;
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
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class NGScimUserServiceImpl implements ScimUserService {
  private final NgUserService ngUserService;
  private final InviteService inviteService;
  private final UserGroupService userGroupService;
  private final AccountClient accountClient;

  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  @Override
  public Response createUser(ScimUser userQuery, String accountId) {
    log.info("NGSCIM: Creating user call for accountId {} with scim user query {}", accountId, userQuery);
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
                                           .twoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
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
      return Response.status(Response.Status.CREATED).entity(getUserInternal(user.getUuid(), accountId)).build();
    } else {
      String userName = getName(userQuery);
      Invite invite = Invite.builder()
                          .accountIdentifier(accountId)
                          .approved(true)
                          .email(primaryEmail)
                          .name(userName)
                          .givenName(getGivenNameFromScimUser(userQuery))
                          .familyName(getFamilyNameFromScimUser(userQuery))
                          .externalId(getExternalIdFromScimUser(userQuery))
                          .inviteType(InviteType.SCIM_INITIATED_INVITE)
                          .build();

      invite.setRoleBindings(emptyList());
      inviteService.create(invite, true, false);

      userOptional = ngUserService.getUserByEmail(primaryEmail, true);

      if (userOptional.isPresent()) {
        user = userOptional.get();
        userQuery.setId(user.getUuid());
        log.info("NGSCIM: Completed creating user call for accountId {} with query {}", accountId, userQuery);
        return Response.status(Response.Status.CREATED).entity(getUserInternal(user.getUuid(), accountId)).build();
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

  private ScimUser getUserInternal(String userId, String accountId) {
    Optional<UserInfo> userInfo = ngUserService.getUserById(userId);
    return userInfo.map(user -> buildUserResponse(user, accountId)).orElse(null);
  }

  @Override
  public ScimUser getUser(String userId, String accountId) {
    Optional<UserInfo> userInfo = ngUserService.getUserById(userId);
    if (userInfo.isPresent()) {
      Optional<UserMetadataDTO> userOptional = ngUserService.getUserByEmail(userInfo.get().getEmail(), false);
      if (userOptional.isPresent()
          && ngUserService.isUserAtScope(
              userOptional.get().getUuid(), Scope.builder().accountIdentifier(accountId).build())) {
        return userInfo.map(user -> buildUserResponse(user, accountId)).get();
      } else {
        throw new InvalidRequestException("User does not exist in NG");
      }
    } else {
      throw new InvalidRequestException("User does not exist in Harness");
    }
  }

  @Override
  public ScimListResponse<ScimUser> searchUser(String accountId, String filter, Integer count, Integer startIndex) {
    log.info("NGSCIM: searching users accountId {}, search query {}", accountId, filter);
    ScimListResponse<ScimUser> result = ngUserService.searchScimUsersByEmailQuery(accountId, filter, count, startIndex);
    if (result.getTotalResults() > 0) {
      result = removeUsersNotinNG(result, accountId);
    }
    // now add the groups for these users
    if (ngFeatureFlagHelperService.isEnabled(accountId, PL_NEW_SCIM_STANDARDS)) {
      for (ScimUser scimUser : result.getResources()) {
        log.info("NGSCIM: adding user groups data for each of the user {}, in account {} for search user",
            scimUser.getId(), accountId);
        scimUser.setGroups(JsonUtils.asTree(getUserGroupNodesForAGivenUser(scimUser.getId(), accountId)));
      }
    }
    log.info("NGSCIM: completed search. accountId {}, search query {}, resultSize: {}", accountId, filter,
        result.getTotalResults());
    return result;
  }

  private ScimListResponse<ScimUser> removeUsersNotinNG(ScimListResponse<ScimUser> result, String accountId) {
    List<ScimUser> usersNotinNG = new ArrayList<>();
    for (ScimUser scimUser : result.getResources()) {
      Optional<UserMetadataDTO> userOptional = ngUserService.getUserByEmail(scimUser.getUserName(), false);
      if (!userOptional.isPresent()) {
        usersNotinNG.add(scimUser);
      } else if (!ngUserService.isUserAtScope(
                     userOptional.get().getUuid(), Scope.builder().accountIdentifier(accountId).build())) {
        usersNotinNG.add(scimUser);
      }
    }
    if (!usersNotinNG.isEmpty()) {
      log.warn(
          "NGSCIM: Removing the following users from the search result, as they don't exist in NG {}, for accountID {}",
          usersNotinNG, accountId);
      List<ScimUser> updatedResources = result.getResources();
      updatedResources.removeAll(usersNotinNG);
      result.setResources(updatedResources);
      result.setTotalResults(updatedResources.size());
    }
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
    String operation = isNotEmpty(patchRequest.getOperations()) ? patchRequest.getOperations().toString() : null;
    String schemas = isNotEmpty(patchRequest.getSchemas()) ? patchRequest.getSchemas().toString() : null;
    log.info(
        "NGSCIM: Updating user: Patch Request Logging\nOperations {}\n, Schemas {}\n,External Id {}\n, Meta {}, for userId: {}, accountId {}",
        operation, schemas, patchRequest.getExternalId(), patchRequest.getMeta(), userId, accountId);

    // Call CG to update only the user details
    if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_USER_DELETION_V2)) {
      ngUserService.updateUserDetails(accountId, userId, patchRequest);
    } else {
      ngUserService.updateScimUser(accountId, userId, patchRequest);
    }

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
    return getUserInternal(userId, accountId);
  }

  @Override
  public ScimUser updateUserDetails(String accountId, String userId, PatchRequest patchRequest) {
    throw new NotImplementedException("NGSCIM: Update method can be invoked only on CG SCIM");
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
      // Passing the changed details on NG to CG, provided the user is active in NG.
      if (!ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_USER_DELETION_V2)) {
        boolean cgUpdateResult = ngUserService.updateScimUser(accountId, userId, scimUser);
        if (!cgUpdateResult) {
          log.error("NGSCIM: User is not found in CG. userId: {}, accountId: {}", userId, accountId);
          return Response.status(Response.Status.NOT_FOUND).build();
        }
      } else if (scimUser.getActive()
          && ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_USER_DELETION_V2)) {
        boolean cgUpdateResult = ngUserService.updateScimUser(accountId, userId, scimUser);
        if (!cgUpdateResult) {
          log.error("NGSCIM: User is not found in CG. userId: {}, accountId: {}", userId, accountId);
          return Response.status(Response.Status.NOT_FOUND).build();
        }
      }
      String displayName = getName(scimUser);
      UserInfo existingUser = userInfo.get();
      UserMetadataDTO userMetadata = userMetadataDTOOptional.get();

      if (StringUtils.isNotEmpty(displayName) && !displayName.equals(existingUser.getName())) {
        userMetadata.setName(displayName);
        userMetadata.setExternallyManaged(true);
        log.info("NGSCIM: Updating name for user {} ; Updated name: {}", userId, displayName);
      }

      String updatedEmail = getPrimaryEmail(scimUser);
      if (existingUser.getEmail() != null && !existingUser.getEmail().equals(updatedEmail)) {
        userMetadata.setEmail(updatedEmail);
        userMetadata.setExternallyManaged(true);
        log.info("NGSCIM: Updating email for user {} ; Updated email: {}", userId, updatedEmail);
      }

      ngUserService.updateUserMetadata(userMetadata);
      log.info("NGSCIM: Updated metadata for user: {}", userId);

      if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_USER_DELETION_V2)) {
        if (scimUser.getActive() != null && !scimUser.getActive()) {
          log.info("NGSCIM: Removing user {}, from account: {}", userId, accountId);
          deleteUser(userId, accountId);
        }
      } else {
        if (scimUser.getActive() != null && scimUser.getActive() == existingUser.isDisabled()) {
          log.info("NGSCIM: Updating disabled state for user: {}, to: {}", userId, !scimUser.getActive());
          changeScimUserDisabled(accountId, userId, !scimUser.getActive());
        }
      }

      log.info("NGSCIM: Updating user completed - userId: {}, accountId: {}", userId, accountId);

      // @Todo: Not handling GIVEN_NAME AND FAMILY_NAME. Add if we need to persist them
      return Response.status(Response.Status.OK).entity(getUserInternal(userId, accountId)).build();
    }
  }

  @Override
  public boolean changeScimUserDisabled(String accountId, String userId, boolean disabled) {
    if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_USER_DELETION_V2)) {
      log.info("NGSCIM: Removing user {}, from account: {}", userId, accountId);
      deleteUser(userId, accountId);
    } else {
      if (disabled) {
        // OKTA doesn't send an explicit delete user request but only makes active true/false.
        // We need to remove the user completely if active=false as we do not have any first
        // class support for a disabled user vs a deleted user
        ngUserService.removeUser(userId, accountId);
      } else {
        // This is to keep CG implementation working as it is.
        ngUserService.updateUserDisabled(accountId, userId, disabled);
      }
    }
    return true;
  }

  private void applyUserUpdateOperation(String accountId, String userId, UserMetadataDTO userMetadataDTO,
      PatchOperation patchOperation) throws JsonProcessingException {
    // Not sure why this needs to be done for displayName as well as ScimMultiValuedObject
    // Relying on CG implementation as it has been around for a while
    if ("displayName".equals(patchOperation.getPath())
        && !userMetadataDTO.getName().equals(patchOperation.getValue(String.class))) {
      userMetadataDTO.setName(patchOperation.getValue(String.class));
      userMetadataDTO.setExternallyManaged(true);
      ngUserService.updateUserMetadata(userMetadataDTO);
    }

    if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_USER_DELETION_V2)) {
      if ("active".equals(patchOperation.getPath()) && patchOperation.getValue(Boolean.class) != null
          && !patchOperation.getValue(Boolean.class)) {
        log.info("NGSCIM: Removing user {}, from account: {}", userId, accountId);
        deleteUser(userId, accountId);
      }
    } else {
      if ("active".equals(patchOperation.getPath()) && patchOperation.getValue(Boolean.class) != null) {
        log.info("NGSCIM: Updating disabled state for user: {} in account: {} with value: {} for patch operation case",
            userId, accountId, !patchOperation.getValue(Boolean.class));
        changeScimUserDisabled(accountId, userId, !patchOperation.getValue(Boolean.class));
      }
    }

    if ("userName".equals(patchOperation.getPath()) && patchOperation.getValue(String.class) != null
        && !userMetadataDTO.getEmail().equalsIgnoreCase(patchOperation.getValue(String.class))) {
      String updatedEmail = patchOperation.getValue(String.class).toLowerCase();
      userMetadataDTO.setEmail(updatedEmail);
      userMetadataDTO.setExternallyManaged(true);
      ngUserService.updateUserMetadata(userMetadataDTO);
      log.info("SCIM: Updated user's {}, email to id: {}", userId, updatedEmail);
    }

    if (patchOperation.getValue(ScimMultiValuedObject.class) != null
        && patchOperation.getValue(ScimMultiValuedObject.class).getDisplayName() != null) {
      userMetadataDTO.setName(patchOperation.getValue(ScimMultiValuedObject.class).getDisplayName());
      userMetadataDTO.setExternallyManaged(true);
      ngUserService.updateUserMetadata(userMetadataDTO);
    }

    if (!ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_USER_DELETION_V2)
        && patchOperation.getValue(ScimUserValuedObject.class) != null) {
      log.info(
          "NGSCIM: Updating disabled state for user: {} in account: {} with value: {} for scim user value object case",
          userId, accountId, !patchOperation.getValue(Boolean.class));
      changeScimUserDisabled(accountId, userId, !(patchOperation.getValue(ScimUserValuedObject.class)).isActive());
    } else if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_USER_DELETION_V2)
        && patchOperation.getValue(ScimUserValuedObject.class) != null
        && !(patchOperation.getValue(ScimUserValuedObject.class)).isActive()) {
      log.info("NGSCIM: Removing user {}, from account: {}", userId, accountId);
      deleteUser(userId, accountId);
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
        || !StringUtils.equalsIgnoreCase(user.getEmail(), userQuery.getUserName());
  }

  private ScimUser buildUserResponse(UserInfo user, String accountId) {
    ScimUser userResource = new ScimUser();
    userResource.setId(user.getUuid());

    userResource.setActive(!user.isDisabled());
    userResource.setUserName(user.getEmail());
    userResource.setDisplayName(user.getName());
    userResource.setExternalId(user.getExternalId());

    boolean isJpmcFfOn = ngFeatureFlagHelperService.isEnabled(accountId, PL_NEW_SCIM_STANDARDS);

    // @Todo - Check with Ujjawal on this if we need GIVEN_NAME & FAMILY_NAME
    Map<String, String> nameMap = new HashMap<String, String>() {
      {
        put("displayName", user.getName());
        if (isJpmcFfOn) {
          final String givenNm = user.getGivenName() == null ? user.getName() : user.getGivenName();
          final String familyNm = user.getFamilyName() == null ? user.getName() : user.getFamilyName();

          put(FAMILY_NAME, familyNm);
          put(GIVEN_NAME, givenNm);

          if (user.getGivenName() == null && user.getFamilyName() == null) {
            put(FORMATTED_NAME, user.getName());
          } else {
            put(FORMATTED_NAME, givenNm + ", " + familyNm);
          }
        }
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

    if (isJpmcFfOn) {
      Map<String, String> metaMap = new HashMap<String, String>() {
        {
          put(RESOURCE_TYPE, "User");
          put(CREATED, simpleDateFormat.format(new Date(user.getCreatedAt())));
          put(LAST_MODIFIED, simpleDateFormat.format(new Date(user.getLastUpdatedAt())));
          put(VERSION, "");
          put(LOCATION, "");
        }
      };
      userResource.setMeta(JsonUtils.asTree(metaMap));

      // get UserGroups of this user
      log.info("NGSCIM: adding user groups data for the user {}, in account {}", user.getUuid(), accountId);
      List<JsonNode> groupsNode = getUserGroupNodesForAGivenUser(user.getUuid(), accountId);
      userResource.setGroups(JsonUtils.asTree(groupsNode));
    }
    return userResource;
  }

  private String getPrimaryEmail(ScimUser userQuery) {
    return isEmpty(userQuery.getUserName()) ? null : userQuery.getUserName().toLowerCase();
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

  private String getGivenNameFromScimUser(@NotNull ScimUser userQuery) {
    return userQuery.getName() != null && userQuery.getName().get(GIVEN_NAME) != null
        ? userQuery.getName().get(GIVEN_NAME).textValue()
        : userQuery.getDisplayName();
  }

  private String getFamilyNameFromScimUser(@NotNull ScimUser userQuery) {
    return userQuery.getName() != null && userQuery.getName().get(GIVEN_NAME) != null
        ? userQuery.getName().get(FAMILY_NAME).textValue()
        : userQuery.getDisplayName();
  }

  private String getExternalIdFromScimUser(@NotNull ScimUser userQuery) {
    return isEmpty(userQuery.getExternalId()) ? null : userQuery.getExternalId();
  }

  private List<JsonNode> getUserGroupNodesForAGivenUser(String userId, String accountId) {
    List<JsonNode> groupsNode = new ArrayList<>();
    List<UserGroup> userGroups = userGroupService.getUserGroupsForUser(accountId, userId);
    for (UserGroup userGroup : userGroups) {
      Map<String, String> userGroupMap = new HashMap<>() {
        {
          put("value", userGroup.getIdentifier());
          put("ref", "");
          put("display", userGroup.getName());
        }
      };
      groupsNode.add(JsonUtils.asTree(userGroupMap));
    }
    return groupsNode;
  }
}