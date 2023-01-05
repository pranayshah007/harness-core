/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.dashboard.Action;
import io.harness.dashboard.DashboardAccessPermissions;
import io.harness.dashboard.DashboardSettings;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HIterator;

import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author rktummala on 07/01/19
 */
@Singleton
public class DashboardAuthHandler {
  @Inject private WingsPersistence wingsPersistence;
  private Set<Action> allActions = Sets.newHashSet(Action.READ, Action.UPDATE, Action.DELETE, Action.MANAGE);

  public Map<String, Set<Action>> getDashboardAccessPermissions(
      User user, String accountId, UserPermissionInfo userPermissionInfo, List<UserGroup> userGroups) {
    if (user == null) {
      return new HashMap<>();
    }

    boolean isDashboardAdmin = hasManageDashboardsPermissions(userPermissionInfo);

    Map<String, Set<Action>> dashboardActionMap = new HashMap<>();
    Query<DashboardSettings> query = wingsPersistence.createQuery(DashboardSettings.class);
    query.filter("accountId", accountId);
    if (!isDashboardAdmin) {
      if (isEmpty(userGroups)) {
        query.filter("createdBy", user.getUuid());
      } else {
        Set<String> userGroupIdSet = userGroups.stream().map(UserGroup::getUuid).collect(Collectors.toSet());
        query.or(query.criteria("createdBy.uuid").equal(user.getUuid()),
            query.criteria("permissions.userGroups").in(userGroupIdSet));
      }
    }

    try (HIterator<DashboardSettings> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        DashboardSettings dashboardSettings = iterator.next();
        if (dashboardSettings == null) {
          continue;
        }

        boolean owner = dashboardSettings.getCreatedBy().getUuid().equals(user.getUuid());
        if (isDashboardAdmin || owner) {
          Set<Action> actions = dashboardActionMap.putIfAbsent(dashboardSettings.getUuid(), new HashSet<>());
          if (actions == null) {
            actions = dashboardActionMap.get(dashboardSettings.getUuid());
          }
          actions.addAll(allActions);
          continue;
        }

        List<DashboardAccessPermissions> permissions = dashboardSettings.getPermissions();

        if (permissions == null) {
          continue;
        }
        permissions.forEach(permission -> {
          if (permission == null) {
            return;
          }
          List<String> userGroupIds = permission.getUserGroups();
          if (userGroupIds == null) {
            return;
          }

          Set<Action> actions = dashboardActionMap.putIfAbsent(dashboardSettings.getUuid(), new HashSet<>());
          if (actions == null) {
            actions = dashboardActionMap.get(dashboardSettings.getUuid());
          }

          actions.addAll(permission.getAllowedActions());
        });
      }
    }

    return dashboardActionMap;
  }

  public void setAccessFlags(List<DashboardSettings> dashboardSettingsList) {
    User user = UserThreadLocal.get();

    if (user == null) {
      return;
    }

    String userId = user.getUuid();

    UserPermissionInfo userPermissionInfo = user.getUserRequestContext().getUserPermissionInfo();
    Map<String, Set<Action>> dashboardPermissions = userPermissionInfo.getDashboardPermissions();

    dashboardSettingsList.forEach(dashboardSettings -> {
      if (dashboardSettings.getCreatedBy().getUuid().equals(userId)) {
        dashboardSettings.setOwner(true);
      }

      if (hasManageDashboardsPermissions(userPermissionInfo)) {
        dashboardSettings.setCanUpdate(true);
        dashboardSettings.setCanDelete(true);
      }

      Set<Action> actions = dashboardPermissions.get(dashboardSettings.getUuid());
      if (isNotEmpty(actions)) {
        // If user canUpdate/canDelete from account admin privilege. It should not be taken away because of
        // shared dashboard settings.
        dashboardSettings.setCanUpdate(dashboardSettings.isCanManage() || actions.contains(Action.MANAGE)
            || dashboardSettings.isCanUpdate() || actions.contains(Action.UPDATE));
        dashboardSettings.setCanDelete(dashboardSettings.isCanManage() || actions.contains(Action.MANAGE));
        dashboardSettings.setCanManage(dashboardSettings.isCanManage() || actions.contains(Action.MANAGE));
        // PL-3325: Should single out explicitly shared custom dashboards, it's shared if not owner when
        // it got permissions from shared user groups.
        dashboardSettings.setShared(!dashboardSettings.isOwner());
      }
    });
  }

  private boolean hasManageDashboardsPermissions(UserPermissionInfo userPermissionInfo) {
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    if (accountPermissionSummary == null) {
      return false;
    }

    Set<PermissionType> permissions = accountPermissionSummary.getPermissions();
    if (isEmpty(permissions)) {
      return false;
    }

    return permissions.contains(PermissionType.MANAGE_CUSTOM_DASHBOARDS);
  }

  public void authorizeDashboardCreation(DashboardSettings dashboardSettings, String accountId) {
    if (dashboardSettings == null) {
      return;
    }

    if (!dashboardSettings.getAccountId().equals(accountId)) {
      throw new InvalidRequestException("User not authorized", ErrorCode.USER_NOT_AUTHORIZED, USER);
    }

    User user = UserThreadLocal.get();

    if (user == null) {
      return;
    }

    UserPermissionInfo userPermissionInfo = user.getUserRequestContext().getUserPermissionInfo();

    if (userPermissionInfo == null || userPermissionInfo.getAccountPermissionSummary() == null) {
      throw new InvalidRequestException("User not authorized", ErrorCode.USER_NOT_AUTHORIZED, USER);
    }

    final Set<PermissionType> permissions = userPermissionInfo.getAccountPermissionSummary().getPermissions();

    if (isEmpty(permissions)) {
      throw new InvalidRequestException("User not authorized", ErrorCode.USER_NOT_AUTHORIZED, USER);
    }
    if (!(permissions.contains(PermissionType.MANAGE_CUSTOM_DASHBOARDS)
            || permissions.contains(PermissionType.CREATE_CUSTOM_DASHBOARDS))) {
      throw new InvalidRequestException("User not authorized", ErrorCode.USER_NOT_AUTHORIZED, USER);
    }
  }

  public void authorize(DashboardSettings dashboardSettings, String accountId, Action action) {
    if (dashboardSettings == null) {
      return;
    }

    if (!dashboardSettings.getAccountId().equals(accountId)) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED, USER);
    }

    User user = UserThreadLocal.get();

    if (user == null) {
      return;
    }

    UserPermissionInfo userPermissionInfo = user.getUserRequestContext().getUserPermissionInfo();
    Map<String, Set<Action>> dashboardPermissions = userPermissionInfo.getDashboardPermissions();

    if (isEmpty(dashboardPermissions)) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED, USER);
    }

    Set<Action> actions = dashboardPermissions.get(dashboardSettings.getUuid());
    if (isEmpty(actions)) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED, USER);
    }

    if (!isActionAllowed(actions, action)) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED, USER);
    }
  }

  private boolean isActionAllowed(Set<Action> allowedActions, Action action) {
    if (allowedActions.contains(action)) {
      return true;
    }

    switch (action) {
      case READ:
        return allowedActions.contains(Action.UPDATE) || allowedActions.contains(Action.MANAGE);
      case UPDATE:
      case DELETE:
      case MANAGE:
        return allowedActions.contains(Action.MANAGE);
      default:
        return false;
    }
  }

  public Set<String> getAllowedDashboardSettingIds() {
    User user = UserThreadLocal.get();

    if (user == null) {
      return null;
    }

    UserPermissionInfo userPermissionInfo = user.getUserRequestContext().getUserPermissionInfo();
    Map<String, Set<Action>> dashboardPermissions = userPermissionInfo.getDashboardPermissions();

    if (isEmpty(dashboardPermissions)) {
      return null;
    }

    return dashboardPermissions.keySet();
  }
}
