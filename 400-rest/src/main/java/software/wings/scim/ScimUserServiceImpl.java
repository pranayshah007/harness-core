/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.scim.PatchOperation;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimMultiValuedObject;
import io.harness.scim.ScimUser;
import io.harness.scim.ScimUserValuedObject;
import io.harness.scim.service.ScimUserService;
import io.harness.serializer.JsonUtils;

import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.UserInvite;
import software.wings.beans.UserInvite.UserInviteBuilder;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.UserService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(PL)
@Slf4j
public class ScimUserServiceImpl implements ScimUserService {
  @Inject private UserService userService;
  @Inject private WingsPersistence wingsPersistence;

  private static final Integer MAX_RESULT_COUNT = 20;
  private static final String GIVEN_NAME = "givenName";
  private static final String FAMILY_NAME = "familyName";
  private static final String VALUE = "value";
  private static final String PRIMARY = "primary";

  @Override
  public Response createUser(ScimUser userQuery, String accountId) {
    log.info("SCIM: Creating user call for accountId {} with query {}", accountId, userQuery);
    String primaryEmail = getPrimaryEmail(userQuery);

    User user = userService.getUserByEmail(primaryEmail, accountId);

    if (user != null) {
      userQuery.setId(user.getUuid());
      userQuery.setActive(true);
      if (shouldUpdateUser(userQuery, user)) {
        updateUser(user.getUuid(), accountId, userQuery);
        log.info("SCIM: Creating user call for accountId {} with updation {}", accountId, userQuery);
      } else {
        log.info("SCIM: Creating user call for accountId {} with conflict {}", accountId, userQuery);
      }
      return Response.status(Status.CREATED).entity(getUser(user.getUuid(), accountId)).build();
    }

    String userName = getName(userQuery);
    UserInvite userInvite = UserInviteBuilder.anUserInvite()
                                .withAccountId(accountId)
                                .withEmail(primaryEmail)
                                .withName(userName != null ? userName : getDisplayNameFromName(userQuery))
                                .withGivenName(getGivenNameFromScimUser(userQuery))
                                .withFamilyName(getFamilyNameFromScimUser(userQuery))
                                .withUserGroups(Lists.newArrayList())
                                .withImportedByScim(true)
                                .build();

    userService.inviteUser(userInvite, false, true);

    user = userService.getUserByEmail(primaryEmail, accountId);
    if (user != null) {
      userQuery.setId(user.getUuid());
      log.info("SCIM: Completed creating user call for accountId {} with query {}", accountId, userQuery);
      return Response.status(Status.CREATED).entity(getUser(user.getUuid(), accountId)).build();
    } else {
      return Response.status(Status.NOT_FOUND).build();
    }
  }

  private boolean shouldUpdateUser(ScimUser userQuery, User user) {
    return user.isDisabled() || !StringUtils.equals(user.getName(), userQuery.getDisplayName())
        || !StringUtils.equals(user.getEmail(), userQuery.getUserName())
        || (userQuery.getName() != null
            && (!StringUtils.equals(userQuery.getName().get(GIVEN_NAME).asText(), user.getGivenName())
                || !StringUtils.equals(userQuery.getName().get(FAMILY_NAME).asText(), user.getFamilyName())));
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

  private String getPrimaryEmail(ScimUser userQuery) {
    // Another alternate way is to get it from the list of emails.work.
    return userQuery.getUserName().toLowerCase();
  }

  private String getName(ScimUser userQuery) {
    return userQuery.getDisplayName();
  }

  @Override
  public ScimUser getUser(String userId, String accountId) {
    User user = userService.get(accountId, userId);
    return buildUserResponse(user);
  }

  private ScimUser buildUserResponse(User user) {
    ScimUser userResource = new ScimUser();
    if (user == null) {
      return null;
    }
    userResource.setId(user.getUuid());

    userResource.setActive(!user.isDisabled());
    userResource.setUserName(user.getEmail());
    userResource.setDisplayName(user.getName());

    Map<String, String> nameMap = new HashMap<String, String>() {
      {
        put(GIVEN_NAME, user.getGivenName() != null ? user.getGivenName() : user.getName());
        put(FAMILY_NAME, user.getFamilyName() != null ? user.getFamilyName() : user.getName());
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

  @Override
  public ScimListResponse<ScimUser> searchUser(String accountId, String filter, Integer count, Integer startIndex) {
    startIndex = startIndex == null ? 0 : startIndex;
    count = count == null ? MAX_RESULT_COUNT : count;

    log.info("SCIM: Searching users in account {} with filter: {}", accountId, filter);

    ScimListResponse<ScimUser> userResponse = new ScimListResponse<>();
    String searchQuery = null;
    if (isNotEmpty(filter)) {
      try {
        filter = URLDecoder.decode(filter, "UTF-8");
        String[] split = filter.split(" eq ");
        String operand = split[1];
        searchQuery = operand.substring(1, operand.length() - 1);
      } catch (Exception ex) {
        log.error("SCIM: Failed to process filter query: {} for account: {}", filter, accountId, ex);
      }
    }

    List<ScimUser> scimUsers = new ArrayList<>();
    try {
      scimUsers = searchUserByUserName(accountId, searchQuery, count, startIndex);
      log.info("SCIM: Scim users in account {} found from query {}", accountId, scimUsers);
      scimUsers.forEach(userResponse::resource);
    } catch (WingsException ex) {
      log.info("SCIM: Search user by name failed. account: {} ,searchQuery: {}", accountId, searchQuery, ex);
    }

    userResponse.startIndex(startIndex);
    userResponse.itemsPerPage(count);
    userResponse.totalResults(scimUsers.size());
    return userResponse;
  }

  private List<ScimUser> searchUserByUserName(String accountId, String searchQuery, Integer count, Integer startIndex) {
    Query<User> userQuery = wingsPersistence.createQuery(User.class)
                                .field(UserKeys.accounts)
                                .hasThisOne(accountId)
                                .field(UserKeys.imported)
                                .equal(true);

    if (StringUtils.isNotEmpty(searchQuery)) {
      userQuery.field(UserKeys.email).equal(searchQuery);
    }
    List<User> userList = userQuery.asList(new FindOptions().skip(startIndex).limit(count));
    return userList.stream().map(this::buildUserResponse).collect(Collectors.toList());
  }

  @Override
  public void deleteUser(String userId, String accountId) {
    log.info("SCIM: deleting for accountId {} the user {}", accountId, userId);
    userService.delete(accountId, userId);
    log.info("SCIM: deleting the user completed for accountId {} the user {}", accountId, userId);
  }

  @Override
  public ScimUser updateUser(String accountId, String userId, PatchRequest patchRequest) {
    log.info("SCIM: Updating user : Patch - userId: {}, accountId: {}", userId, accountId);
    patchRequest.getOperations().forEach(patchOperation -> {
      try {
        applyUserUpdateOperation(accountId, userId, patchOperation);
      } catch (Exception ex) {
        log.error("SCIM: Failed to update user: {}, patchOperation: {}", userId, patchOperation, ex);
      }
    });
    return getUser(userId, accountId);
  }

  private void applyUserUpdateOperation(String accountId, String userId, PatchOperation patchOperation)
      throws JsonProcessingException {
    User user = userService.get(accountId, userId);
    if (user == null) {
      throw new WingsException(ErrorCode.USER_DOES_NOT_EXIST);
    }
    if ("displayName".equals(patchOperation.getPath())) {
      UpdateOperations<User> updateOperation = wingsPersistence.createUpdateOperations(User.class);
      updateOperation.set(UserKeys.name, patchOperation.getValue(String.class));
      userService.updateUser(user.getUuid(), updateOperation);
    }
    if (patchOperation.getValue(ScimMultiValuedObject.class) != null
        && patchOperation.getValue(ScimMultiValuedObject.class).getDisplayName() != null) {
      UpdateOperations<User> updateOperation = wingsPersistence.createUpdateOperations(User.class);
      updateOperation.set(UserKeys.name, patchOperation.getValue(String.class));
      userService.updateUser(user.getUuid(), updateOperation);
    }
    if ("active".equals(patchOperation.getPath()) && patchOperation.getValue(Boolean.class) != null) {
      changeScimUserDisabled(accountId, user.getUuid(), !(patchOperation.getValue(Boolean.class)));
    }

    if (patchOperation.getValue(ScimUserValuedObject.class) != null) {
      changeScimUserDisabled(
          accountId, user.getUuid(), !(patchOperation.getValue(ScimUserValuedObject.class)).isActive());
    } else {
      // Not supporting any other updates as of now.
      log.error("SCIM: Unexpected patch operation received: accountId: {}, userId: {}, patchOperation: {}", accountId,
          userId, patchOperation);
    }
  }

  private void removeUserFromAllScimGroups(String accountId, String userId) {
    try {
      List<UserGroup> scimUserGroups = wingsPersistence.createQuery(UserGroup.class)
                                           .field(UserGroupKeys.memberIds)
                                           .contains(userId)
                                           .filter(UserGroupKeys.accountId, accountId)
                                           .filter(UserGroupKeys.importedByScim, true)
                                           .asList();

      if (isNotEmpty(scimUserGroups)) {
        scimUserGroups.forEach(userGroup -> {
          userGroup.getMemberIds().remove(userId);
          wingsPersistence.save(userGroup);
        });
      }
    } catch (Exception ex) {
      log.error("SCIM: Error while removing User from SCIM User groups, with accountId {} and userId {}", accountId,
          userId, ex);
    }
  }

  private String getDisplayNameFromName(@NotNull ScimUser userResource) {
    if (userResource.getName() != null) {
      return userResource.getName().get(GIVEN_NAME).asText() + " " + userResource.getName().get(FAMILY_NAME).asText();
    }
    return null;
  }

  @Override
  public Response updateUser(String userId, String accountId, ScimUser userResource) {
    log.info("SCIM: Updating user resource: {}", userResource);
    User user = userService.get(accountId, userId);

    if (user == null) {
      log.error("SCIM: User is not found. userId: {}, accountId: {}", userId, accountId);
      return Response.status(Status.NOT_FOUND).build();
    } else {
      String displayName =
          userResource.getDisplayName() != null ? userResource.getDisplayName() : getDisplayNameFromName(userResource);

      UpdateOperations<User> updateOperations = wingsPersistence.createUpdateOperations(User.class);
      boolean userUpdate = false;
      if (StringUtils.isNotEmpty(displayName) && !displayName.equals(user.getName())) {
        userUpdate = true;
        updateOperations.set(UserKeys.name, displayName);
        log.info("SCIM: Updated user's {} name: {}", userId, displayName);
      }

      if (userResource.getName() != null) {
        if (userResource.getName().get(GIVEN_NAME) != null
            && !StringUtils.equals(userResource.getName().get(GIVEN_NAME).asText(), user.getGivenName())) {
          userUpdate = true;
          updateOperations.set(UserKeys.givenName, userResource.getName().get(GIVEN_NAME).asText());
          log.info("SCIM: Updated user's {} given name: {}", userId, userResource.getName().get(GIVEN_NAME).asText());
        }
        if (userResource.getName().get(FAMILY_NAME) != null
            && !StringUtils.equals(userResource.getName().get(FAMILY_NAME).asText(), user.getFamilyName())) {
          userUpdate = true;
          updateOperations.set(UserKeys.familyName, userResource.getName().get(FAMILY_NAME).asText());
          log.info("SCIM: Updated user's {} family name: {}", userId, userResource.getName().get(FAMILY_NAME).asText());
        }
      }

      if (userResource.getActive() != null && userResource.getActive() == user.isDisabled()) {
        userUpdate = true;
        log.info("SCIM: Updated user's {}, enabled: {}", userId, userResource.getActive());
        updateOperations.set(UserKeys.disabled, !userResource.getActive());
      }
      if (userUpdate) {
        updateOperations.set(UserKeys.imported, true);
        userService.updateUser(user.getUuid(), updateOperations);
      }
      return Response.status(Status.OK).entity(getUser(user.getUuid(), accountId)).build();
    }
  }

  @Override
  public boolean changeScimUserDisabled(String accountId, String userId, boolean disabled) {
    UpdateOperations<User> updateOperation = wingsPersistence.createUpdateOperations(User.class);
    updateOperation.set(UserKeys.disabled, disabled);
    userService.updateUser(userId, updateOperation);
    if (disabled) {
      removeUserFromAllScimGroups(accountId, userId);
    }
    return true;
  }
}