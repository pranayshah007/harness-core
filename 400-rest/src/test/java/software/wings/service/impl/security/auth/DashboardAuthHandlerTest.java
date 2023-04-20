/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.DASHBOARD1_ID;
import static software.wings.utils.WingsTestConstants.USER1_ID;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.dashboard.Action;
import io.harness.dashboard.DashboardAccessPermissions;
import io.harness.dashboard.DashboardSettings;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.events.TestUtils;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.PermissionAttribute;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DashboardAuthHandlerTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private UserGroupService userGroupService;
  @Mock private UserService userService;
  @Inject private TestUtils testUtils;
  @Mock private FeatureFlagService featureFlagService;
  @Inject private HPersistence persistence;

  private User user;
  private Account account;

  @InjectMocks @Inject private DashboardAuthHandler authHandler;

  //  private List<PermissionType> accountPermissionTypes =
  //      asList(ACCOUNT_MANAGEMENT, MANAGE_APPLICATIONS, USER_PERMISSION_MANAGEMENT, TEMPLATE_MANAGEMENT);
  //  private List<Action> allActions = asList(Action.CREATE, Action.UPDATE, Action.READ, Action.DELETE,
  //  Action.EXECUTE);
  private List<Action> allActions = asList(Action.MANAGE, Action.UPDATE, Action.READ, Action.DELETE);

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldWorkForManagePermission() {
    List<DashboardAccessPermissions> permissions = new ArrayList<>();
    List<io.harness.dashboard.Action> actions = new ArrayList<>();
    actions.add(Action.MANAGE);
    List<String> userGroups = new ArrayList<>();
    userGroups.add(USER_GROUP_ID);
    permissions.add(DashboardAccessPermissions.builder().userGroups(userGroups).allowedActions(actions).build());
    DashboardSettings dashboard1 = DashboardSettings.builder()
                                       .accountId(ACCOUNT_ID)
                                       .name(ACCOUNT_NAME)
                                       .permissions(permissions)
                                       .createdBy(EmbeddedUser.builder().uuid(USER1_ID).build())
                                       .uuid(DASHBOARD1_ID)
                                       .build();
    persistence.save(dashboard1);

    User user = User.Builder.anUser().uuid(USER_ID).build();
    UserPermissionInfo userPermissionInfo = UserPermissionInfo.builder().build();
    List<String> memberIds = new ArrayList<>();
    memberIds.add(USER_ID);
    List<UserGroup> userGroupList = new ArrayList<>();
    UserGroup userGroup = UserGroup.builder().uuid(USER_GROUP_ID).memberIds(memberIds).build();
    userGroupList.add(userGroup);
    Map<String, Set<Action>> dashboardAccessPermissions =
        authHandler.getDashboardAccessPermissions(user, ACCOUNT_ID, userPermissionInfo, userGroupList);
    assertThat(dashboardAccessPermissions.size()).isEqualTo(1);
    assertThat(dashboardAccessPermissions.get(DASHBOARD1_ID).size()).isEqualTo(1);
    assertThat(dashboardAccessPermissions.get(DASHBOARD1_ID).contains(Action.MANAGE)).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldWorkForOwner() {
    List<DashboardAccessPermissions> permissions = new ArrayList<>();
    List<io.harness.dashboard.Action> actions = new ArrayList<>();
    actions.add(Action.READ);
    List<String> userGroups = new ArrayList<>();
    userGroups.add(USER_GROUP_ID);
    permissions.add(DashboardAccessPermissions.builder().userGroups(userGroups).allowedActions(actions).build());
    DashboardSettings dashboard1 = DashboardSettings.builder()
                                       .accountId(ACCOUNT_ID)
                                       .name(ACCOUNT_NAME)
                                       .permissions(permissions)
                                       .createdBy(EmbeddedUser.builder().uuid(USER_ID).build())
                                       .uuid(DASHBOARD1_ID)
                                       .build();
    persistence.save(dashboard1);

    User user = User.Builder.anUser().uuid(USER_ID).build();
    UserPermissionInfo userPermissionInfo = UserPermissionInfo.builder().build();
    List<String> memberIds = new ArrayList<>();
    memberIds.add(USER_ID);
    List<UserGroup> userGroupList = new ArrayList<>();
    UserGroup userGroup = UserGroup.builder().uuid(USER_GROUP_ID).memberIds(memberIds).build();
    userGroupList.add(userGroup);
    Map<String, Set<Action>> dashboardAccessPermissions =
        authHandler.getDashboardAccessPermissions(user, ACCOUNT_ID, userPermissionInfo, userGroupList);
    assertThat(dashboardAccessPermissions.size()).isEqualTo(1);
    assertThat(dashboardAccessPermissions.get(DASHBOARD1_ID).size()).isEqualTo(4);
    assertThat(dashboardAccessPermissions.get(DASHBOARD1_ID).containsAll(allActions)).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldWorkForReadPermission() {
    List<DashboardAccessPermissions> permissions = new ArrayList<>();
    List<io.harness.dashboard.Action> actions = new ArrayList<>();
    actions.add(Action.READ);
    List<String> userGroups = new ArrayList<>();
    userGroups.add(USER_GROUP_ID);
    permissions.add(DashboardAccessPermissions.builder().userGroups(userGroups).allowedActions(actions).build());
    DashboardSettings dashboard1 = DashboardSettings.builder()
                                       .accountId(ACCOUNT_ID)
                                       .name(ACCOUNT_NAME)
                                       .permissions(permissions)
                                       .createdBy(EmbeddedUser.builder().uuid(USER1_ID).build())
                                       .uuid(DASHBOARD1_ID)
                                       .build();
    persistence.save(dashboard1);

    User user = User.Builder.anUser().uuid(USER_ID).build();
    UserPermissionInfo userPermissionInfo = UserPermissionInfo.builder().build();
    List<String> memberIds = new ArrayList<>();
    memberIds.add(USER_ID);
    List<UserGroup> userGroupList = new ArrayList<>();
    UserGroup userGroup = UserGroup.builder().uuid(USER_GROUP_ID).memberIds(memberIds).build();
    userGroupList.add(userGroup);
    Map<String, Set<Action>> dashboardAccessPermissions =
        authHandler.getDashboardAccessPermissions(user, ACCOUNT_ID, userPermissionInfo, userGroupList);
    assertThat(dashboardAccessPermissions.size()).isEqualTo(1);
    assertThat(dashboardAccessPermissions.get(DASHBOARD1_ID).size()).isEqualTo(1);
    assertThat(dashboardAccessPermissions.get(DASHBOARD1_ID).contains(Action.READ)).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldNotGetAnyDashboardForNoPermission() {
    List<DashboardAccessPermissions> permissions = new ArrayList<>();
    List<io.harness.dashboard.Action> actions = new ArrayList<>();
    actions.add(Action.READ);
    List<String> userGroups = new ArrayList<>();
    userGroups.add(USER_GROUP_ID);
    permissions.add(DashboardAccessPermissions.builder().userGroups(userGroups).allowedActions(actions).build());
    DashboardSettings dashboard1 = DashboardSettings.builder()
                                       .accountId(ACCOUNT_ID)
                                       .name(ACCOUNT_NAME)
                                       .permissions(permissions)
                                       .createdBy(EmbeddedUser.builder().uuid(USER1_ID).build())
                                       .uuid(DASHBOARD1_ID)
                                       .build();
    persistence.save(dashboard1);

    User user = User.Builder.anUser().uuid(USER_ID).build();
    UserPermissionInfo userPermissionInfo = UserPermissionInfo.builder().build();
    List<UserGroup> userGroupList = new ArrayList<>();
    Map<String, Set<Action>> dashboardAccessPermissions =
        authHandler.getDashboardAccessPermissions(user, ACCOUNT_ID, userPermissionInfo, userGroupList);
    assertThat(dashboardAccessPermissions.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldCreateCustomDashboardWithBothPermissions() {
    setupUser();
    addUserAccountPermission(Arrays.asList(PermissionAttribute.PermissionType.MANAGE_CUSTOM_DASHBOARDS));
    UserThreadLocal.set(user);
    try {
      assertThatCode(()
                         -> authHandler.authorizeDashboardCreation(
                             DashboardSettings.builder().accountId(ACCOUNT_ID).name("abc").build(), ACCOUNT_ID))
          .doesNotThrowAnyException();

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldCreateCustomDashboardWithCreatePermissions() {
    setupUser();
    addUserAccountPermission(Arrays.asList(PermissionAttribute.PermissionType.CREATE_CUSTOM_DASHBOARDS));
    UserThreadLocal.set(user);
    try {
      assertThatCode(()
                         -> authHandler.authorizeDashboardCreation(
                             DashboardSettings.builder().accountId(ACCOUNT_ID).name("abc").build(), ACCOUNT_ID))
          .doesNotThrowAnyException();

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldCreateCustomDashboardWithManagePermissions() {
    setupUser();
    addUserAccountPermission(Arrays.asList(PermissionAttribute.PermissionType.MANAGE_CUSTOM_DASHBOARDS));
    UserThreadLocal.set(user);
    try {
      assertThatCode(()
                         -> authHandler.authorizeDashboardCreation(
                             DashboardSettings.builder().accountId(ACCOUNT_ID).name("abc").build(), ACCOUNT_ID))
          .doesNotThrowAnyException();

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldNotCreateCustomDashboardWithoutDashboardPermissions() {
    setupUser();
    addUserAccountPermission(Collections.emptyList());
    UserThreadLocal.set(user);
    try {
      assertThatThrownBy(()
                             -> authHandler.authorizeDashboardCreation(
                                 DashboardSettings.builder().accountId(ACCOUNT_ID).name("abc").build(), ACCOUNT_ID));

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void adminShouldBeAbleToViewAllDashboards() {
    setupUser();
    DashboardSettings dashboard1 = DashboardSettings.builder()
                                       .accountId(ACCOUNT_ID)
                                       .name(ACCOUNT_NAME)
                                       .uuid(DASHBOARD1_ID)
                                       .createdBy(EmbeddedUser.builder().uuid(user.getUuid()).build())
                                       .build();
    persistence.save(dashboard1);
    DashboardSettings dashboard = DashboardSettings.builder()
                                      .accountId(ACCOUNT_ID)
                                      .name(ACCOUNT_NAME)
                                      .uuid("uuid2")
                                      .createdBy(EmbeddedUser.builder().uuid(user.getUuid()).build())
                                      .build();
    persistence.save(dashboard);

    addUserAccountPermission(Arrays.asList(PermissionAttribute.PermissionType.MANAGE_CUSTOM_DASHBOARDS));
    UserThreadLocal.set(user);
    try {
      final Map<String, Set<Action>> dashboardAccessPermissions = authHandler.getDashboardAccessPermissions(
          user, ACCOUNT_ID, user.getUserRequestContext().getUserPermissionInfo(), null);
      assertThat(dashboardAccessPermissions.size()).isEqualTo(2);
    } finally {
      UserThreadLocal.unset();
    }
  }

  private void addUserAccountPermission(List<PermissionAttribute.PermissionType> permissionTypeList) {
    user.setUserRequestContext(
        UserRequestContext.builder()
            .userPermissionInfo(
                UserPermissionInfo.builder()
                    .accountPermissionSummary(
                        AccountPermissionSummary.builder().permissions(new HashSet<>(permissionTypeList)).build())
                    .build())
            .build());
  }

  private void setupUser() {
    account = testUtils.createAccount();
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(accountService.getFromCache(ACCOUNT_ID)).thenReturn(account);
    when(accountService.save(any(), eq(false))).thenReturn(account);
    user = testUtils.createUser(account);
  }
}
