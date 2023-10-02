/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.service.impl;

import static io.harness.NGConstants.ACCOUNT_ADMIN_ROLE;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.user.remote.dto.LastAdminCheckFilter.LastAdminCheckFilterType.USER_DELETION;

import static java.util.stream.Collectors.toList;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.configuration.DeployVariant;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.LastAdminCheckFilter;
import io.harness.ng.core.user.service.LastAdminCheckService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.remote.client.NGRestUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.validation.executable.ValidateOnExecution;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
@ValidateOnExecution
public class LastAdminCheckServiceImpl implements LastAdminCheckService {
  private final LicenseService licenseService;
  private final NgUserService ngUserService;
  private final UserGroupService userGroupService;
  private final AccessControlAdminClient accessControlAdminClient;

  @Inject
  public LastAdminCheckServiceImpl(LicenseService licenseService, NgUserService ngUserService,
      UserGroupService userGroupService, AccessControlAdminClient accessControlAdminClient) {
    this.licenseService = licenseService;
    this.ngUserService = ngUserService;
    this.userGroupService = userGroupService;
    this.accessControlAdminClient = accessControlAdminClient;
  }

  @Override
  public boolean doesAdminExistAfterRemoval(String accountIdentifier, LastAdminCheckFilter lastAdminCheckFilter) {
    if (DeployVariant.isCommunity(System.getenv().get(DEPLOY_VERSION))) {
      if (USER_DELETION.equals(lastAdminCheckFilter.getType())) {
        List<String> userIds = ngUserService.listUserIds(Scope.of(accountIdentifier, null, null));
        return userIds.stream().anyMatch(userId -> !userId.equals(lastAdminCheckFilter.getUserIdentifier()));
      }
      return true;
    }

    switch (lastAdminCheckFilter.getType()) {
      case USER_DELETION:
        return doesAdminExistAfterUserDeletion(accountIdentifier, lastAdminCheckFilter.getUserIdentifier());
      case USER_GROUP_DELETION:
        return doesAdminExistAfterUserGroupDeletion(accountIdentifier, lastAdminCheckFilter.getUserGroupIdentifier());
      case USER_FROM_USER_GROUP_REMOVAL:
        return doesAdminExistAfterUserRemovalFromUserGroup(
            accountIdentifier, lastAdminCheckFilter.getUserGroupIdentifier(), lastAdminCheckFilter.getUserIdentifier());
      default:
        throw new InvalidArgumentsException(
            String.format("Invalid last admin check filter type %s", lastAdminCheckFilter.getType()));
    }
  }

  private boolean doesAdminExistAfterUserDeletion(String accountIdentifier, String userIdentifier) {
    List<PrincipalDTO> principals = getAdminsFromAccessControl(accountIdentifier);
    boolean doesOtherAdminUsersExist = principals.stream().anyMatch(
        admin -> admin.getType().equals(USER) && !userIdentifier.equals(admin.getIdentifier()));
    if (doesOtherAdminUsersExist) {
      return true;
    }

    List<UserGroup> adminUserGroups = filterUserGroups(accountIdentifier, principals);
    return adminUserGroups.stream().anyMatch(
        userGroup -> userGroup.getUsers().stream().anyMatch(user -> !user.equals(userIdentifier)));
  }

  private boolean doesAdminExistAfterUserGroupDeletion(String accountIdentifier, String userGroupIdentifier) {
    List<PrincipalDTO> principals = getAdminsFromAccessControl(accountIdentifier);
    boolean adminUsersExist = principals.stream().anyMatch(admin -> admin.getType().equals(USER));
    if (adminUsersExist) {
      return true;
    }
    List<UserGroup> adminUserGroups = filterUserGroups(accountIdentifier, principals);
    return adminUserGroups.stream().anyMatch(userGroup -> {
      if (!userGroup.getIdentifier().equals(userGroupIdentifier)) {
        return isNotEmpty(userGroup.getUsers());
      }
      return false;
    });
  }

  private boolean doesAdminExistAfterUserRemovalFromUserGroup(
      String accountIdentifier, String userGroupIdentifier, String userIdentifier) {
    List<PrincipalDTO> principals = getAdminsFromAccessControl(accountIdentifier);
    boolean adminUsersExist = principals.stream().anyMatch(admin -> admin.getType().equals(USER));
    if (adminUsersExist) {
      return true;
    }
    List<UserGroup> adminUserGroups = filterUserGroups(accountIdentifier, principals);
    return adminUserGroups.stream().anyMatch(userGroup -> {
      if (userGroup.getIdentifier().equals(userGroupIdentifier)) {
        return userGroup.getUsers().stream().anyMatch(user -> !user.equals(userIdentifier));
      } else {
        return isNotEmpty(userGroup.getUsers());
      }
    });
  }

  private List<UserGroup> filterUserGroups(@NotEmpty String accountIdentifier, List<PrincipalDTO> principals) {
    List<String> adminUserGroupIds = principals.stream()
                                         .filter(admin -> admin.getType().equals(USER_GROUP))
                                         .map(PrincipalDTO::getIdentifier)
                                         .distinct()
                                         .collect(toList());
    if (isEmpty(adminUserGroupIds)) {
      return new ArrayList<>();
    }
    return userGroupService.list(UserGroupFilterDTO.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .identifierFilter(new HashSet<>(adminUserGroupIds))
                                     .build());
  }

  @VisibleForTesting
  protected List<PrincipalDTO> getAdminsFromAccessControl(String accountIdentifier) {
    PageResponse<RoleAssignmentResponseDTO> response =
        NGRestUtils.getResponse(accessControlAdminClient.getFilteredRoleAssignments(accountIdentifier, null, null, 0,
            10000, RoleAssignmentFilterDTO.builder().roleFilter(Collections.singleton(ACCOUNT_ADMIN_ROLE)).build()));
    return response.getContent()
        .stream()
        .map(dto -> dto.getRoleAssignment().getPrincipal())
        .distinct()
        .collect(toList());
  }
}
