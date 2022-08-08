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

import io.harness.annotations.dev.OwnedBy;
import io.harness.invites.remote.NgInviteClient;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.user.AddUsersDTO;
import io.harness.remote.client.NGRestUtils;

import software.wings.beans.User;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Objects;

@OwnedBy(PL)
public class AdminUserServiceImpl implements AdminUserService {
  private UserService userService;
  private NgInviteClient ngInviteClient;

  @Inject
  public AdminUserServiceImpl(UserService userService, @Named("PRIVILEGED") NgInviteClient ngInviteClient) {
    this.userService = userService;
    this.ngInviteClient = ngInviteClient;
  }

  @Override
  public boolean enableOrDisableUser(String accountId, String userIdOrEmail, boolean enabled) {
    User user = userService.getUserByEmail(userIdOrEmail);
    if (Objects.isNull(user)) {
      user = userService.get(userIdOrEmail);
    }
    return userService.enableUser(accountId, user.getUuid(), enabled);
  }

  @Override
  public boolean assignAdminRoleToUserInNG(String accountId, String userIdOrEmail) {
    User user = userService.getUserByEmail(userIdOrEmail);
    if (Objects.isNull(user)) {
      user = userService.getUserByUserId(accountId, userIdOrEmail);
    }
    String email = userIdOrEmail;
    if (!Objects.isNull(user)) {
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
}
