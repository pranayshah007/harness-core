/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SHIVAM;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE2_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.AllAppFilter;
import io.harness.governance.AllEnvFilter;
import io.harness.governance.AllProdEnvFilter;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomAppFilter;
import io.harness.governance.CustomEnvFilter;
import io.harness.governance.DeploymentFreezeInfo;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.GovernanceFreezeConfig;
import io.harness.governance.ServiceFilter;
import io.harness.governance.ServiceFilter.ServiceFilterType;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.security.UserGroup;
import software.wings.resources.stats.model.TimeRange;
import software.wings.service.impl.deployment.checks.DeploymentFreezeUtils;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(HarnessTeam.CDC)
public class GovernanceConfigServiceImplTest extends WingsBaseTest {
  public static final int DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
  @Mock FeatureFlagService featureFlagService;
  @Mock EnvironmentService environmentService;
  @Mock AppService appService;
  @Mock UserGroupService userGroupService;
  @Mock DeploymentFreezeUtils deploymentFreezeUtils;
  @Inject @InjectMocks private GovernanceConfigService governanceConfigService;

  @Before
  public void setUp() {
    when(featureFlagService.isEnabled(eq(FeatureName.NEW_DEPLOYMENT_FREEZE), anyString())).thenReturn(true);
    UserGroup userGroup = UserGroup.builder().name("testUserGroup").build();
    when(userGroupService.get(any(), any())).thenReturn(userGroup);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSaveAndGetDeploymentFreeze() {
    GovernanceConfig governanceConfig =
        JsonUtils.readResourceFile("governance/governance_config.json", GovernanceConfig.class);

    GovernanceConfig savedGovernanceConfig =
        governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    savedGovernanceConfig.setUuid("GOVERNANCE_CONFIG_ID");
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).setApplicable(true);
    JsonNode actual = JsonUtils.toJsonNode(savedGovernanceConfig);
    JsonNode expected = JsonUtils.readResourceFile("governance/governance_config_expected.json", JsonNode.class);
    assertThat(actual.equals(expected)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDeleteDeploymentFreeze() {
    GovernanceConfig governanceConfig =
        JsonUtils.readResourceFile("governance/governance_config.json", GovernanceConfig.class);
    GovernanceConfig savedGovernanceConfig =
        governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().remove(0);
    savedGovernanceConfig = governanceConfigService.upsert(governanceConfig.getAccountId(), savedGovernanceConfig);
    assertThat(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs()).isEmpty();
    assertThat(savedGovernanceConfig.isDeploymentFreeze()).isTrue();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSwitchMasterFreezeOnAndOffWithNoFreezeWindows() {
    GovernanceConfig governanceConfig = GovernanceConfig.builder().accountId(ACCOUNT_ID).deploymentFreeze(true).build();

    GovernanceConfig savedGovernanceConfig =
        governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    savedGovernanceConfig.setDeploymentFreeze(false);
    savedGovernanceConfig = governanceConfigService.upsert(governanceConfig.getAccountId(), savedGovernanceConfig);
    assertThat(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs()).isEmpty();
    assertThat(savedGovernanceConfig.isDeploymentFreeze()).isFalse();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForCustomEnvWithNonCustomApp() {
    GovernanceConfig governanceConfig =
        JsonUtils.readResourceFile("governance/governance_config.json", GovernanceConfig.class);
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).setFilterType(
        BlackoutWindowFilterType.ALL);

    assertThatThrownBy(() -> governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Environment filter type can be CUSTOM only when Application Filter type is CUSTOM");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForCustomServiceWithMultipleApps() {
    GovernanceConfig governanceConfig =
        JsonUtils.readResourceFile("governance/governance_config.json", GovernanceConfig.class);
    CustomAppFilter appConfig =
        (CustomAppFilter) governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0);
    appConfig.getApps().add("app2");

    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).getEnvSelection().setFilterType(
        EnvironmentFilterType.ALL);

    assertThatThrownBy(() -> governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Application filter should have exactly one app when service filter type is CUSTOM");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForCustomEnvWithMultipleApps() {
    GovernanceConfig governanceConfig =
        JsonUtils.readResourceFile("governance/governance_config.json", GovernanceConfig.class);
    CustomAppFilter appConfig =
        (CustomAppFilter) governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0);
    appConfig.getApps().add("app2");

    assertThatThrownBy(() -> governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Application filter should have exactly one app when environment filter type is CUSTOM");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetFrozenAppEnvs() {
    TimeRange range = new TimeRange(System.currentTimeMillis(), System.currentTimeMillis() + 100_000_000,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection2 = CustomAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
                                          .apps(asList(APP_ID, APP_ID + 2))
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID + 3))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldGetFrozenAppEnvs", null, true, asList(appSelection1, appSelection2, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);
    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .accountId(ACCOUNT_ID)
            .timeRangeBasedFreezeConfigs(Collections.singletonList(timeRangeBasedFreezeConfig))
            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    Map<String, Set<String>> envAppMap = ImmutableMap.<String, Set<String>>builder()
                                             .put(APP_ID, new HashSet<>(asList(ENV_ID)))
                                             .put(APP_ID + 2, new HashSet<>(asList(ENV_ID, ENV_ID + 2, ENV_ID + 3)))
                                             .build();
    when(environmentService.getAppIdEnvIdMapByType(anySet(), eq(EnvironmentType.PROD))).thenReturn(envAppMap);
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    DeploymentFreezeInfo deploymentFreezeInfo = governanceConfigService.getDeploymentFreezeInfo(ACCOUNT_ID);
    assertThat(deploymentFreezeInfo).isNotNull();
    assertThat(deploymentFreezeInfo.isFreezeAll()).isFalse();
    assertThat(deploymentFreezeInfo.getAppEnvs()).hasSize(3);
    assertThat(deploymentFreezeInfo.getAllEnvFrozenApps()).containsExactlyInAnyOrder(APP_ID, APP_ID + 2);
    assertThat(deploymentFreezeInfo.getAppEnvs().get(APP_ID + 2))
        .containsExactlyInAnyOrder(ENV_ID + 3, ENV_ID, ENV_ID + 2);
    assertThat(deploymentFreezeInfo.getAppEnvs().get(APP_ID + 3)).containsExactlyInAnyOrder(ENV_ID, ENV_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetFrozenAllAppEnvs() {
    TimeRange range = new TimeRange(System.currentTimeMillis(), System.currentTimeMillis() + 100_000_000,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "shouldGetFrozenAllAppEnvs",
        null, true, asList(appSelection1, appSelection3), Collections.singletonList("testUserGroup"), "uuid", null);
    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .accountId(ACCOUNT_ID)
            .timeRangeBasedFreezeConfigs(Collections.singletonList(timeRangeBasedFreezeConfig))
            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    DeploymentFreezeInfo deploymentFreezeInfo = governanceConfigService.getDeploymentFreezeInfo(ACCOUNT_ID);
    assertThat(deploymentFreezeInfo).isNotNull();
    assertThat(deploymentFreezeInfo.isFreezeAll()).isFalse();
    assertThat(deploymentFreezeInfo.getAppEnvs()).hasSize(1);
    assertThat(deploymentFreezeInfo.getAllEnvFrozenApps()).containsExactlyInAnyOrder(APP_ID, APP_ID + 2, APP_ID + 3);
    assertThat(deploymentFreezeInfo.getAppEnvs().get(APP_ID)).containsExactlyInAnyOrder(ENV_ID, ENV_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetOnlyApplicableAppEnvs() {
    TimeRange range = new TimeRange(System.currentTimeMillis(), System.currentTimeMillis() + 2_000_000, "Asia/Calcutta",
        false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, asList(SERVICE_ID, SERVICE2_ID)))
            .build();
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldGetOnlyApplicableAppEnvs", null, false, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig2 =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldGetOnlyApplicableAppEnvs2", null, true, asList(appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);
    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .accountId(ACCOUNT_ID)
            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig, timeRangeBasedFreezeConfig2))
            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    DeploymentFreezeInfo deploymentFreezeInfo = governanceConfigService.getDeploymentFreezeInfo(ACCOUNT_ID);
    assertThat(deploymentFreezeInfo).isNotNull();
    assertThat(deploymentFreezeInfo.isFreezeAll()).isFalse();
    assertThat(deploymentFreezeInfo.getAppEnvs()).hasSize(1);
    assertThat(deploymentFreezeInfo.getAppEnvs().get(APP_ID)).containsExactlyInAnyOrder(ENV_ID, ENV_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyMapForNoAppSelections() {
    TimeRange range = new TimeRange(System.currentTimeMillis() + 18_00_000, System.currentTimeMillis() + 54_00_000,
        "Asia/Calcutta", false, null, null, null, false);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldReturnEmptyMapForNoAppSelections", null, false, Collections.emptyList(), null, "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));

    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    DeploymentFreezeInfo deploymentFreezeInfo = governanceConfigService.getDeploymentFreezeInfo(ACCOUNT_ID);
    assertThat(deploymentFreezeInfo).isNotNull();
    assertThat(deploymentFreezeInfo.isFreezeAll()).isFalse();
    assertThat(deploymentFreezeInfo.getAppEnvs()).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyMapForNoWindows() {
    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .deploymentFreeze(true)
                                            .timeRangeBasedFreezeConfigs(Collections.emptyList())
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));

    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    DeploymentFreezeInfo deploymentFreezeInfo = governanceConfigService.getDeploymentFreezeInfo(ACCOUNT_ID);
    assertThat(deploymentFreezeInfo).isNotNull();
    assertThat(deploymentFreezeInfo.isFreezeAll()).isTrue();
    assertThat(deploymentFreezeInfo.getAppEnvs()).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyMapForOutofTimeRange() {
    TimeRange range = new TimeRange(100, 18_00_100, "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldReturnEmptyMapForNoWindows", null, false, asList(appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));

    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    DeploymentFreezeInfo deploymentFreezeInfo = governanceConfigService.getDeploymentFreezeInfo(ACCOUNT_ID);
    assertThat(deploymentFreezeInfo).isNotNull();
    assertThat(deploymentFreezeInfo.isFreezeAll()).isFalse();
    assertThat(deploymentFreezeInfo.getAppEnvs()).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetFrozenEnvsForApp() {
    TimeRange range = new TimeRange(System.currentTimeMillis() + 1, System.currentTimeMillis() + 54_00_000,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection2 = CustomAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
                                          .apps(asList(APP_ID, APP_ID + 2))
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID + 3))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "shouldGetFrozenEnvsForApp",
        null, true, asList(appSelection1), Collections.singletonList("testUserGroup"), "uuid", null);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig1 = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "shouldGetFrozenEnvsForApp2",
        null, true, asList(appSelection2, appSelection3), Collections.singletonList("testUserGroup"), "uuid2", null);
    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .accountId(ACCOUNT_ID)
            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig, timeRangeBasedFreezeConfig1))
            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    when(environmentService.getEnvIdsByAppsAndType(anyList(), eq(EnvironmentType.PROD.name())))
        .thenReturn(asList(ENV_ID, ENV_ID + 3));
    when(environmentService.getEnvIdsByApp(APP_ID + 2)).thenReturn(asList(ENV_ID, ENV_ID + 2, ENV_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID + 2, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo).isNotNull();
    assertThat(deploymentFreezeInfo.get(timeRangeBasedFreezeConfig1.getUuid()))
        .containsExactlyInAnyOrder(ENV_ID, ENV_ID + 2, ENV_ID + 3);
    assertThat(deploymentFreezeInfo.get(timeRangeBasedFreezeConfig.getUuid()))
        .containsExactlyInAnyOrder(ENV_ID, ENV_ID + 3);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnEmptyMapWhenEnvIsFrozenForParticularService() {
    TimeRange range = new TimeRange(System.currentTimeMillis() + 1, System.currentTimeMillis() + 54_00_000,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(Collections.singletonList(APP_ID))
            .envSelection(new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD))
            .serviceSelection(new ServiceFilter(ServiceFilterType.CUSTOM, Collections.singletonList(SERVICE_ID)))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "shouldGetFrozenEnvsForApp",
        null, true, asList(appSelection), Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .accountId(ACCOUNT_ID)
            .timeRangeBasedFreezeConfigs(Collections.singletonList(timeRangeBasedFreezeConfig))
            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    when(environmentService.getEnvIdsByAppsAndType(anyList(), eq(EnvironmentType.PROD.name())))
        .thenReturn(asList(ENV_ID, ENV_ID + 2));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo).isEmpty();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnOnlyCompletelyFrozenEnv() {
    TimeRange range = new TimeRange(System.currentTimeMillis() + 1, System.currentTimeMillis() + 54_00_000,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter allProdForParticularService =
        AllAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
            .envSelection(new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD))
            .serviceSelection(new ServiceFilter(ServiceFilterType.CUSTOM, Collections.singletonList(SERVICE_ID)))
            .build();

    ApplicationFilter allNonProd = AllAppFilter.builder()
                                       .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                       .envSelection(new AllProdEnvFilter(EnvironmentFilterType.ALL_NON_PROD))
                                       .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                       .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.emptyList(), range, "shouldGetFrozenEnvsForApp", null, true,
        asList(allProdForParticularService, allNonProd), Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .accountId(ACCOUNT_ID)
            .timeRangeBasedFreezeConfigs(Collections.singletonList(timeRangeBasedFreezeConfig))
            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    when(environmentService.getEnvIdsByAppsAndType(anyList(), eq(EnvironmentType.PROD.name())))
        .thenReturn(asList(ENV_ID, ENV_ID + 2));
    when(environmentService.getEnvIdsByAppsAndType(anyList(), eq(EnvironmentType.NON_PROD.name())))
        .thenReturn(Collections.singletonList(ENV_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID + 2, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo.get(timeRangeBasedFreezeConfig.getUuid())).containsOnly(ENV_ID + 3);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetFrozenEnvsForOneBlockingWindow() {
    TimeRange range = new TimeRange(System.currentTimeMillis(), System.currentTimeMillis() + 18_000_000,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection2 = CustomAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
                                          .apps(asList(APP_ID, APP_ID + 2))
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig1 =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldGetFrozenEnvsForOneBlockingWindow", null, true, asList(appSelection2, appSelection1),
            Collections.singletonList("testUserGroup"), "uuid", null);
    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig1))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    ArrayList<String> envList = new ArrayList<>();
    envList.add(ENV_ID);
    envList.add(ENV_ID + 3);
    when(environmentService.getEnvIdsByAppsAndType(anyList(), eq(EnvironmentType.PROD.name()))).thenReturn(envList);
    ArrayList<String> envList2 = new ArrayList<>(envList);
    envList2.add(ENV_ID + 2);
    when(environmentService.getEnvIdsByApp(APP_ID + 2)).thenReturn(envList2);
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID + 2, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo).isNotNull();
    assertThat(deploymentFreezeInfo.get(timeRangeBasedFreezeConfig1.getUuid()))
        .contains(ENV_ID, ENV_ID + 2, ENV_ID + 3);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetOnlyApplicableEnvs() throws InterruptedException {
    TimeRange range = new TimeRange(System.currentTimeMillis() + 5000, System.currentTimeMillis() + 54_00_000,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "shouldGetOnlyApplicableEnvs",
        null, false, asList(appSelection1, appSelection3), Collections.singletonList("testUserGroup"), "uuid", null);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig2 = new TimeRangeBasedFreezeConfig(true,
        Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD), range, "shouldGetOnlyApplicableEnvs2",
        null, true, asList(appSelection3), Collections.singletonList("testUserGroup"), "uuid2", null);
    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .accountId(ACCOUNT_ID)
            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig, timeRangeBasedFreezeConfig2))
            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    when(environmentService.getEnvIdsByApp(APP_ID)).thenReturn(asList(ENV_ID, ENV_ID + 2, ENV_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    Thread.sleep(5000);
    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo.get(timeRangeBasedFreezeConfig2.getUuid()))
        .containsExactlyInAnyOrder(ENV_ID, ENV_ID + 2);
    assertThat(deploymentFreezeInfo.get(timeRangeBasedFreezeConfig.getUuid())).isNull();
    verify(environmentService, never()).getEnvIdsByApp(APP_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyEnvForNoAppSelections() {
    TimeRange range = new TimeRange(System.currentTimeMillis() + 18_00_000, System.currentTimeMillis() + 54_00_000,
        "Asia/Calcutta", false, null, null, null, false);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldReturnEmptyEnvForNoAppSelections", null, false, Collections.emptyList(), null, "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));

    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID + 2, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo).isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyMapForUnFrozenApp() {
    TimeRange range = new TimeRange(System.currentTimeMillis() + 18_00_000, System.currentTimeMillis() + 54_00_000,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldReturnEmptyMapForUnFrozenApp", null, false, asList(appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID + 2, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo).isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyEnvForNoWindows() {
    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .deploymentFreeze(true)
                                            .timeRangeBasedFreezeConfigs(Collections.emptyList())
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));

    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID + 2, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo).isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyEnvForOutofTimeRange() {
    TimeRange range = new TimeRange(100, 18_00_100, "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldReturnEmptyEnvForOutofTimeRange", null, false, asList(appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));

    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    Map<String, Set<String>> deploymentFreezeInfo =
        governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID + 2, governanceConfigService.get(ACCOUNT_ID));
    assertThat(deploymentFreezeInfo).isNotNull().isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetFreezeWindowsWithId() {
    TimeRange range = new TimeRange(System.currentTimeMillis() + 18_00_000, System.currentTimeMillis() + 54_00_000,
        "Asia/Calcutta", false, null, null, null, false);

    GovernanceConfig governanceConfig =
        JsonUtils.readResourceFile("governance/governance_config.json", GovernanceConfig.class);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "FREEZE1", null, false, Collections.emptyList(), null, "uuid", null);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig2 =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "FREEZE2", null, false, Collections.emptyList(), null, "uuid2", null);

    governanceConfig.getTimeRangeBasedFreezeConfigs().add(timeRangeBasedFreezeConfig);
    governanceConfig.getTimeRangeBasedFreezeConfigs().add(timeRangeBasedFreezeConfig2);

    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    List<GovernanceFreezeConfig> governanceFreezeConfigs = governanceConfigService.getGovernanceFreezeConfigs(
        governanceConfig.getAccountId(), asList("FREEZE_ID", timeRangeBasedFreezeConfig2.getUuid()));
    assertThat(governanceFreezeConfigs).hasSize(2);
    assertThat(governanceFreezeConfigs.stream().map(GovernanceFreezeConfig::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("freezer", "FREEZE2");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForDuplicateNames() {
    GovernanceConfig governanceConfig =
        JsonUtils.readResourceFile("governance/governance_config.json", GovernanceConfig.class);
    TimeRangeBasedFreezeConfig freeze1 = governanceConfig.getTimeRangeBasedFreezeConfigs().get(0);
    governanceConfig.getTimeRangeBasedFreezeConfigs().add(freeze1);

    assertThatThrownBy(() -> governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Duplicate name freezer");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldRecalculateNextIterations() {
    long currentTimeMillis = System.currentTimeMillis();
    TimeRange range = new TimeRange(currentTimeMillis + DAY_IN_MILLIS, currentTimeMillis + 2 * DAY_IN_MILLIS,
        "Asia/Calcutta", false, null, null, null, false);
    TimeRange range2 = new TimeRange(currentTimeMillis - DAY_IN_MILLIS, currentTimeMillis + DAY_IN_MILLIS,
        "Asia/Calcutta", false, null, null, null, false);
    TimeRange range3 = new TimeRange(currentTimeMillis + 2 * DAY_IN_MILLIS, currentTimeMillis + 3 * DAY_IN_MILLIS,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldRecalculateNextIterations", null, true, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig2 =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range2, "shouldRecalculateNextIterations2", null, true, asList(appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig3 =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range3, "shouldRecalculateNextIterations3", null, false, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);
    GovernanceConfig governanceConfig =
        GovernanceConfig.builder()
            .accountId(ACCOUNT_ID)
            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig, timeRangeBasedFreezeConfig2))
            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    assertThat(governanceConfig.getNextCloseIterations())
        .hasSize(2)
        .containsExactly(currentTimeMillis + DAY_IN_MILLIS, currentTimeMillis + 2 * DAY_IN_MILLIS);
    assertThat(governanceConfig.getNextIterations()).hasSize(1).containsExactly(currentTimeMillis + DAY_IN_MILLIS);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendNotificationForNewActiveWindow() {
    long currentTimeMillis = System.currentTimeMillis();
    TimeRange range = new TimeRange(currentTimeMillis - DAY_IN_MILLIS, currentTimeMillis + DAY_IN_MILLIS,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldRecalculateNextIterations", null, true, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    verify(deploymentFreezeUtils).handleActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotSendNotificationForNewInActiveWindow() {
    long currentTimeMillis = System.currentTimeMillis();
    TimeRange range = new TimeRange(currentTimeMillis + DAY_IN_MILLIS, currentTimeMillis + 2 * DAY_IN_MILLIS,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldRecalculateNextIterations", null, true, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    verify(deploymentFreezeUtils, never()).handleActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
    verify(deploymentFreezeUtils, never()).handleDeActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendNotificationWhenWindowMadeActive() {
    long currentTimeMillis = System.currentTimeMillis();
    TimeRange range = new TimeRange(currentTimeMillis - DAY_IN_MILLIS, currentTimeMillis + DAY_IN_MILLIS,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldRecalculateNextIterations", null, false, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    verify(deploymentFreezeUtils, never()).handleActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);

    timeRangeBasedFreezeConfig.setApplicable(true);
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig2 =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldRecalculateNextIteration2s", null, true, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid2", null);
    governanceConfig = GovernanceConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig, timeRangeBasedFreezeConfig2))
                           .build();
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    verify(deploymentFreezeUtils).handleActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
    verify(deploymentFreezeUtils).handleActivationEvent(timeRangeBasedFreezeConfig2, ACCOUNT_ID);
    verify(deploymentFreezeUtils, never()).handleDeActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendNotificationWhenWindowMadeInactive() {
    long currentTimeMillis = System.currentTimeMillis();
    TimeRange range = new TimeRange(currentTimeMillis - 2 * DAY_IN_MILLIS, currentTimeMillis - DAY_IN_MILLIS,
        "Asia/Calcutta", false, null, null, null, false);

    ApplicationFilter appSelection1 = AllAppFilter.builder()
                                          .blackoutWindowFilterType(BlackoutWindowFilterType.ALL)
                                          .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                          .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
                                          .build();

    ApplicationFilter appSelection3 =
        CustomAppFilter.builder()
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .apps(asList(APP_ID))
            .envSelection(new CustomEnvFilter(EnvironmentFilterType.CUSTOM, asList(ENV_ID, ENV_ID + 2)))
            .serviceSelection(new ServiceFilter(ServiceFilterType.ALL, null))
            .build();

    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldRecalculateNextIterations", null, true, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid", null);

    GovernanceConfig governanceConfig = GovernanceConfig.builder()
                                            .accountId(ACCOUNT_ID)
                                            .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig))
                                            .build();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(asList(APP_ID, APP_ID + 2, APP_ID + 3));
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);

    timeRangeBasedFreezeConfig.setApplicable(false);
    TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig2 =
        new TimeRangeBasedFreezeConfig(true, Collections.emptyList(), Collections.singletonList(EnvironmentType.PROD),
            range, "shouldRecalculateNextIteration2s", null, false, asList(appSelection1, appSelection3),
            Collections.singletonList("testUserGroup"), "uuid2", null);
    governanceConfig = GovernanceConfig.builder()
                           .accountId(ACCOUNT_ID)
                           .timeRangeBasedFreezeConfigs(asList(timeRangeBasedFreezeConfig, timeRangeBasedFreezeConfig2))
                           .build();
    governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    //    verify(deploymentFreezeUtils).handleActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
    //    verify(deploymentFreezeUtils, never()).handleActivationEvent(timeRangeBasedFreezeConfig2, ACCOUNT_ID);
    //    verify(deploymentFreezeUtils).handleDeActivationEvent(timeRangeBasedFreezeConfig, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  @Ignore("TODO: to be fixed later")
  public void shouldThrowErrorOnEnablingTheExpiredWindow() {
    GovernanceConfig governanceConfig =
        JsonUtils.readResourceFile("governance/governance_config.json", GovernanceConfig.class);
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).setApplicable(true);

    GovernanceConfig savedGovernanceConfig =
        governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig);
    savedGovernanceConfig.setUuid("GOVERNANCE_CONFIG_ID");
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).setApplicable(false);
    assertThatThrownBy(() -> governanceConfigService.upsert(governanceConfig.getAccountId(), governanceConfig))
        .isInstanceOf(InvalidRequestException.class);
  }
}
