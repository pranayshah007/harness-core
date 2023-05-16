/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.datahandler.services;

import static io.harness.NGConstants.ACCOUNT_ADMIN_ROLE;
import static io.harness.NGConstants.ACCOUNT_ADMIN_ROLE_NAME;
import static io.harness.NGConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_NAME;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.invites.remote.NgInviteClient;
import io.harness.ng.core.common.beans.Generation;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.user.AddUsersDTO;
import io.harness.remote.client.NGRestUtils;

import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {
  private UserService userService;
  private NgInviteClient ngInviteClient;
  private WingsPersistence wingsPersistence;

  @Inject
  public AdminUserServiceImpl(
      UserService userService, @Named("PRIVILEGED") NgInviteClient ngInviteClient, WingsPersistence wingsPersistence) {
    this.userService = userService;
    this.ngInviteClient = ngInviteClient;
    this.wingsPersistence = wingsPersistence;
  }

  @Override
  public boolean enableOrDisableUser(String accountId, String userIdOrEmail, boolean enabled) {
    User user = userService.getUserByEmail(userIdOrEmail);
    if (isNull(user)) {
      user = userService.get(userIdOrEmail);
    }
    return userService.enableUser(accountId, user.getUuid(), enabled);
  }

  @Override
  public boolean assignAdminRoleToUserInNG(String accountId, String userIdOrEmail) {
    User user = userService.getUserByEmail(userIdOrEmail);
    if (isNull(user)) {
      user = userService.getUserByUserId(accountId, userIdOrEmail);
    }
    String email = userIdOrEmail;
    if (!isNull(user)) {
      email = user.getEmail();
    }
    AddUsersDTO addUsersDTO =
        AddUsersDTO.builder()
            .emails(singletonList(email))
            .roleBindings(singletonList(
                RoleBinding.builder()
                    .roleIdentifier(ACCOUNT_ADMIN_ROLE)
                    .roleName(ACCOUNT_ADMIN_ROLE_NAME)
                    .resourceGroupIdentifier(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER)
                    .resourceGroupName(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_NAME)
                    .managedRole(true)
                    .build()))
            .build();
    NGRestUtils.getResponse(ngInviteClient.addUsers(accountId, null, null, addUsersDTO));
    return true;
  }

  @Override
  public boolean updateExternallyManaged(String userId, Generation generation, boolean externallyManaged) {
    try {
      return userService.updateExternallyManaged(userId, generation, externallyManaged);
    } catch (Exception ex) {
      throw new InvalidRequestException(String.format(
          "Failed to updated externallyManaged to %s for the user- %s for %s", externallyManaged, userId, generation));
    }
  }
}
