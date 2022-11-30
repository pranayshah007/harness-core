/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.User;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.features.api.AbstractUsageLimitedFeature;
import software.wings.features.api.ComplianceByLimitingUsage;
import software.wings.features.api.FeatureRestrictions;
import software.wings.security.AppFilter;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(PL)
@Singleton
public class RbacFeature extends AbstractUsageLimitedFeature implements ComplianceByLimitingUsage {
  @Inject private AuthHandler authHandler;

  public static final String FEATURE_NAME = "RBAC";

  private final UserService userService;
  private final UserGroupService userGroupService;

  @Inject
  public RbacFeature(AccountService accountService, FeatureRestrictions featureRestrictions, UserService userService,
      UserGroupService userGroupService) {
    super(accountService, featureRestrictions);
    this.userService = userService;
    this.userGroupService = userGroupService;
  }

  @Override
  public int getMaxUsageAllowed(String accountType) {
    return (int) getRestrictions(accountType).getOrDefault("maxUserGroupsAllowed", Integer.MAX_VALUE);
  }

  @Override
  public int getUsage(String accountId) {
    return (int) userGroupService.getCountOfUserGroups(accountId);
  }

  @Override
  public String getFeatureName() {
    return FEATURE_NAME;
  }

  @Override
  public boolean limitUsageForCompliance(
      String accountId, String targetAccountType, Map<String, Object> requiredInfoToLimitUsage) {
    if (isUsageCompliantWithRestrictions(accountId, targetAccountType)) {
      return true;
    }

    if (getMaxUsageAllowed(targetAccountType) == 1) {
      userGroupService.deleteNonAdminUserGroups(accountId);

      UserGroup adminUserGroup = userGroupService.getAdminUserGroup(accountId);

      assignAllPermissionsToUserGroup(adminUserGroup);
      assignAllUsersMembershipToUserGroup(adminUserGroup);
    } else {
      @SuppressWarnings("unchecked")
      List<String> userGroupsToRetain = (List<String>) requiredInfoToLimitUsage.get("userGroupsToRetain");
      if (!isEmpty(userGroupsToRetain)) {
        userGroupService.deleteUserGroupsByName(accountId, userGroupsToRetain);
      }
    }

    return isUsageCompliantWithRestrictions(accountId, targetAccountType);
  }

  private void assignAllUsersMembershipToUserGroup(UserGroup adminUserGroup) {
    String accountId = adminUserGroup.getAccountId();

    List<User> usersOfAccount = userService.getUsersOfAccount(accountId);
    usersOfAccount.forEach(user
        -> userService.updateUserGroupsOfUser(
            user.getUuid(), Collections.singletonList(adminUserGroup), accountId, false));
  }

  private void assignAllPermissionsToUserGroup(UserGroup userGroup) {
    AccountPermissions accountPermissions =
        AccountPermissions.builder().permissions(authHandler.getDefaultEnabledAccountPermissions()).build();

    Set<AppPermission> appPermissions = Sets.newHashSet();
    AppPermission appPermission =
        AppPermission.builder()
            .actions(Sets.newHashSet(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE, Action.EXECUTE_PIPELINE,
                Action.EXECUTE_WORKFLOW, Action.EXECUTE_WORKFLOW_ROLLBACK, Action.ABORT_WORKFLOW))
            .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
            .permissionType(PermissionType.ALL_APP_ENTITIES)
            .build();
    appPermissions.add(appPermission);

    userGroup.setAccountPermissions(accountPermissions);
    userGroup.setAppPermissions(appPermissions);

    userGroupService.updatePermissions(userGroup);
  }
}
