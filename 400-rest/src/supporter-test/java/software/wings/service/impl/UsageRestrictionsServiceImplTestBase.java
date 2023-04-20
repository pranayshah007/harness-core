/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static software.wings.security.EnvFilter.FilterType.SELECTED;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static com.google.common.collect.Sets.newHashSet;

import io.harness.CategoryTest;

import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.dl.WingsPersistence;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.AppPermissionSummaryForUI;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.GenericEntityFilter.FilterType;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction;
import software.wings.security.UserPermissionInfo;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.security.SecretManager;

import dev.morphia.query.Query;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author rktummala on 06/08/18
 */
@RunWith(MockitoJUnitRunner.class)
public class UsageRestrictionsServiceImplTestBase extends CategoryTest {
  @Mock private WingsPersistence mockWingsPersistence;
  @Mock private AppService appService;
  @Mock private AuthHandler authHandler;
  @Mock private Application application;
  @Mock private UserGroupService userGroupService;
  @Mock private UserService userService;
  @Mock private EnvironmentService envService;
  @Mock private SettingsService settingsService;
  @Mock private SettingServiceHelper settingServiceHelper;
  @Mock private SecretManager secretManager;
  @Mock private Query<SettingAttribute> query;

  private static Set<Action> readAction = newHashSet(Action.READ);
  private static Set<Action> updateAndReadAction = newHashSet(Action.UPDATE, Action.READ);
  private static Set<Action> allActions = newHashSet(
      Action.CREATE, Action.UPDATE, Action.READ, Action.DELETE, Action.EXECUTE_WORKFLOW, Action.EXECUTE_PIPELINE);

  private String SETTING_ATTRIBUTE_ID = "SETTING_ATTRIBUTE_ID";

  private String ENV_ID_1 = "ENV_ID_1";
  private String ENV_ID_2 = "ENV_ID_2";
  private String ENV_ID_3 = "ENV_ID_3";

  private String APP_ID_1 = "APP_ID_1";
  private String APP_ID_2 = "APP_ID_2";
  private String APP_ID_3 = "APP_ID_3";

  public static UsageRestrictions getUsageRestrictionsForAppIdAndEnvId(String appId, String envId) {
    GenericEntityFilter appFilter =
        GenericEntityFilter.builder().filterType(FilterType.SELECTED).ids(newHashSet(appId)).build();
    HashSet<String> envFilters = newHashSet(SELECTED);
    EnvFilter envFilter = EnvFilter.builder().filterTypes(envFilters).ids(newHashSet(envId)).build();
    AppEnvRestriction appEnvRestriction = AppEnvRestriction.builder().appFilter(appFilter).envFilter(envFilter).build();
    return UsageRestrictions.builder().appEnvRestrictions(newHashSet(appEnvRestriction)).build();
  }

  public static UserPermissionInfo getUserPermissionInfo(
      List<String> appIds, List<String> envIds, Set<Action> actions) {
    Map<Action, Set<EnvInfo>> envPermissionsInternal = new HashMap<>();

    Set<EnvInfo> envInfoSet = envIds.stream()
                                  .map(envId -> EnvInfo.builder().envType("PROD").envId(envId).build())
                                  .collect(Collectors.toSet());
    actions.forEach(action -> envPermissionsInternal.put(action, envInfoSet));

    Map<String, AppPermissionSummary> appPermissionsMapInternal = new HashMap<>();
    AppPermissionSummary appPermissionSummaryInternal =
        AppPermissionSummary.builder().envPermissions(envPermissionsInternal).build();

    appIds.forEach(appId -> appPermissionsMapInternal.put(appId, appPermissionSummaryInternal));

    Map<String, AppPermissionSummaryForUI> appPermissionsMap = new HashMap<>();

    Map<String, Set<Action>> envPermissionMap = new HashMap<>();
    envIds.forEach(envId -> envPermissionMap.put(envId, actions));

    AppPermissionSummaryForUI appPermissionSummaryForUI =
        AppPermissionSummaryForUI.builder().envPermissions(envPermissionMap).build();

    appIds.forEach(appId -> appPermissionsMap.put(appId, appPermissionSummaryForUI));

    return UserPermissionInfo.builder()
        .accountId(ACCOUNT_ID)
        .appPermissionMap(appPermissionsMap)
        .appPermissionMapInternal(appPermissionsMapInternal)
        .build();
  }
}
