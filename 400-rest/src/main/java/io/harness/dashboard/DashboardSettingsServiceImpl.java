/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dashboard;

import static io.harness.beans.PageRequest.DEFAULT_PAGE_SIZE;
import static io.harness.beans.PageRequest.DEFAULT_UNLIMITED;
import static io.harness.beans.PageRequest.PageRequestBuilder;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.util.Arrays.asList;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.dashboard.DashboardSettings.keys;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.AccountEvent;
import software.wings.beans.Event.Type;
import software.wings.beans.account.AccountEventType;
import software.wings.dl.WingsPersistence;
import software.wings.features.CustomDashboardFeature;
import software.wings.features.api.AccountId;
import software.wings.features.api.RestrictedApi;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.security.auth.DashboardAuthHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DashboardSettingsServiceImpl implements DashboardSettingsService {
  @Inject private WingsPersistence persistence;
  @Inject private DashboardAuthHandler dashboardAuthHandler;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private EventPublishHelper eventPublishHelper;
  private static final String eventName = "Custom Dashboard Created";

  @Override
  @RestrictedApi(CustomDashboardFeature.class)
  public DashboardSettings get(@NotNull @AccountId String accountId, @NotNull String id) {
    DashboardSettings dashboardSettings = persistence.get(DashboardSettings.class, id);
    dashboardAuthHandler.setAccessFlags(asList(dashboardSettings));
    return dashboardSettings;
  }

  @Override
  @RestrictedApi(CustomDashboardFeature.class)
  public DashboardSettings createDashboardSettings(
      @NotNull @AccountId String accountId, @NotNull DashboardSettings dashboardSettings) {
    dashboardSettings.setAccountId(accountId);
    assertThatNameIsUnique(accountId, dashboardSettings.getName());
    DashboardSettings savedDashboardSettings = get(accountId, persistence.save(dashboardSettings));
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, savedDashboardSettings, Type.CREATE);
    Map<String, String> properties = new HashMap<>();
    properties.put("module", "Dashboards");
    properties.put("shared", isEmpty(savedDashboardSettings.getPermissions()) ? "false" : "true");
    properties.put("dashboardName", savedDashboardSettings.getName());
    properties.put("groupId", accountId);
    AccountEvent accountEvent = AccountEvent.builder()
                                    .accountEventType(AccountEventType.CUSTOM)
                                    .customMsg(eventName)
                                    .properties(properties)
                                    .build();
    eventPublishHelper.publishAccountEvent(accountId, accountEvent, false, false);
    log.info("Created dashboard for account {}", accountId);
    return savedDashboardSettings;
  }

  @Override
  @RestrictedApi(CustomDashboardFeature.class)
  public DashboardSettings updateDashboardSettings(
      @NotNull @AccountId String accountId, @NotNull DashboardSettings dashboardSettings) {
    String id = dashboardSettings.getUuid();
    if (id == null) {
      throw new InvalidRequestException("Invalid Dashboard update request", USER);
    }

    DashboardSettings dashboardSettingsBeforeUpdate = get(accountId, id);

    UpdateOperations<DashboardSettings> updateOperations = persistence.createUpdateOperations(DashboardSettings.class);
    updateOperations.set(DashboardSettings.keys.data, dashboardSettings.getData());
    updateOperations.set(DashboardSettings.keys.name, dashboardSettings.getName());
    updateOperations.set(DashboardSettings.keys.description, dashboardSettings.getDescription());
    if (isNotEmpty(dashboardSettings.getPermissions())) {
      List<DashboardAccessPermissions> flattenedList = flattenPermissions(dashboardSettings.getPermissions());
      updateOperations.set(keys.permissions, flattenedList);
    } else {
      updateOperations.unset(keys.permissions);
    }

    persistence.update(dashboardSettings, updateOperations);
    DashboardSettings updatedDashboardSettings = get(accountId, id);
    auditServiceHelper.reportForAuditingUsingAccountId(
        accountId, dashboardSettingsBeforeUpdate, updatedDashboardSettings, Type.UPDATE);
    log.info("Updated dashboard {}", id);
    return updatedDashboardSettings;
  }

  private Action compareAction(Action lhs, Action rhs) {
    if (lhs == null && rhs == null) {
      return null;
    }

    if (lhs == null) {
      return rhs;
    }

    if (rhs == null) {
      return lhs;
    }

    int comparison = rhs.compareTo(lhs);
    return comparison >= 0 ? rhs : lhs;
  }

  private List<Action> removeDuplicates(Set<Action> actions) {
    Action finalAction = null;
    for (Action action : actions) {
      finalAction = compareAction(action, finalAction);
    }
    return asList(finalAction);
  }

  public List<DashboardAccessPermissions> flattenPermissions(List<DashboardAccessPermissions> permissions) {
    if (isEmpty(permissions)) {
      return permissions;
    }

    List<DashboardAccessPermissions> finalPermissions = new ArrayList<>();
    Map<String, Set<Action>> map = getPermissionMap(permissions);

    map.forEach((userGroup, actions)
                    -> finalPermissions.add(DashboardAccessPermissions.builder()
                                                .userGroups(asList(userGroup))
                                                .allowedActions(removeDuplicates(actions))
                                                .build()));
    return finalPermissions;
  }

  private Map<String, Set<Action>> getPermissionMap(List<DashboardAccessPermissions> permissions) {
    Map<String, Set<Action>> map = new HashMap<>();
    if (isEmpty(permissions)) {
      return map;
    }
    permissions.forEach(permission -> {
      List<String> userGroups = permission.getUserGroups();
      if (isEmpty(userGroups)) {
        return;
      }

      userGroups.forEach(userGroup -> {
        if (isEmpty(permission.getAllowedActions())) {
          return;
        }

        Set<Action> currentActions = map.get(userGroup);
        if (isEmpty(currentActions)) {
          currentActions = new HashSet<>();
          map.put(userGroup, currentActions);
        }
        currentActions.addAll(permission.getAllowedActions());
      });
    });
    return map;
  }

  @Override
  public boolean doesPermissionsMatch(
      @NotNull DashboardSettings newDashboard, @NotNull DashboardSettings existingDashboard) {
    return getPermissionMap(newDashboard.getPermissions()).equals(getPermissionMap(existingDashboard.getPermissions()));
  }

  @Override
  @RestrictedApi(CustomDashboardFeature.class)
  public boolean deleteDashboardSettings(@NotNull @AccountId String accountId, @NotNull String dashboardSettingsId) {
    DashboardSettings dashboardSettings = get(accountId, dashboardSettingsId);
    if (dashboardSettings != null && dashboardSettings.getAccountId().equals(accountId)) {
      boolean deleted = persistence.delete(DashboardSettings.class, dashboardSettingsId);
      if (deleted) {
        auditServiceHelper.reportForAuditingUsingAccountId(accountId, dashboardSettings, null, Type.DELETE);
        log.info("Deleted dashboard {} for account {}", dashboardSettingsId, accountId);
      }
      return deleted;
    }
    return false;
  }

  @Override
  public PageResponse<DashboardSettings> getDashboardSettingSummary(
      @NotNull String accountId, @NotNull PageRequest pageRequest) {
    pageRequest = sanitizePageRequest(pageRequest);
    pageRequest.addFilter(DashboardSettings.keys.accountId, Operator.EQ, accountId);
    pageRequest.addFieldsExcluded(DashboardSettings.keys.data);
    PageResponse pageResponse = persistence.query(DashboardSettings.class, pageRequest);
    dashboardAuthHandler.setAccessFlags(pageResponse.getResponse());
    return pageResponse;
  }

  private PageRequest sanitizePageRequest(PageRequest pageRequest) {
    if (pageRequest == null) {
      pageRequest =
          PageRequestBuilder.aPageRequest().withLimit(Integer.toString(DEFAULT_PAGE_SIZE)).withOffset("0").build();
    }
    pageRequest.setOffset(pageRequest.getLimit() != null
            ? Integer.toString(Integer.max(Integer.parseInt(pageRequest.getOffset()), 0))
            : "0");
    pageRequest.setLimit(pageRequest.getLimit() != null
            ? Integer.toString(Integer.min(Integer.parseInt(pageRequest.getLimit()), DEFAULT_UNLIMITED))
            : Integer.toString(DEFAULT_PAGE_SIZE));
    return pageRequest;
  }

  private void assertThatNameIsUnique(String accountId, String name) {
    DashboardSettings dashboardSettings = persistence.createQuery(DashboardSettings.class)
                                              .filter(Cd1SetupFields.ACCOUNT_ID_KEY, accountId)
                                              .filter(keys.name, name)
                                              .get();
    if (dashboardSettings == null) {
      return;
    }
    throw new InvalidRequestException("Dashboard already exists with the name " + name);
  }
}
