/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.deployment.checks;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.VINICIUS;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.DeploymentFreezeException;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.AllEnvFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomAppFilter;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.ServiceFilter;
import io.harness.governance.ServiceFilter.ServiceFilterType;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.TimeRangeOccurrence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.resources.stats.model.TimeRange;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.compliance.GovernanceConfigService;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(HarnessTeam.CDC)
public class DeploymentFreezeCheckerTest extends WingsBaseTest {
  public static final String FREEZE_ID = "FREEZE_ID";
  @Mock EnvironmentService environmentService;
  @Mock FeatureFlagService featureFlagService;
  @Mock GovernanceConfigService governanceConfigService;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void checkEnvFrozenByMultipleWindows() {
    GovernanceConfig governanceConfig = generateGovernanceConfig();

    Map<String, Set<String>> frozenEnvs = new HashMap<>();
    frozenEnvs.put(FREEZE_ID, ImmutableSet.of(ENV_ID, ENV_ID + 2));
    frozenEnvs.put(FREEZE_ID + 2, ImmutableSet.of(ENV_ID + 3, ENV_ID + 2));
    frozenEnvs.put(FREEZE_ID + 3, ImmutableSet.of(ENV_ID + 4));
    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, asList(ENV_ID + 2), Collections.emptyList()), environmentService, featureFlagService);
    when(governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfig)).thenReturn(frozenEnvs);
    assertThatThrownBy(() -> deploymentFreezeChecker.checkIfEnvFrozen(ACCOUNT_ID, governanceConfig))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Windows [FREEZE2, FREEZE1] are active for the environment. No deployments are allowed to proceed.")
        .extracting("deploymentFreezeIds", InstanceOfAssertFactories.ITERABLE)
        .containsExactlyInAnyOrder(FREEZE_ID, FREEZE_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void checkEnvFrozenBySingleWindow() {
    GovernanceConfig governanceConfig = generateGovernanceConfig();
    Map<String, Set<String>> frozenEnvs = new HashMap<>();
    frozenEnvs.put(FREEZE_ID, ImmutableSet.of(ENV_ID, ENV_ID + 2));
    frozenEnvs.put(FREEZE_ID + 2, ImmutableSet.of(ENV_ID + 3, ENV_ID + 2));
    frozenEnvs.put(FREEZE_ID + 3, ImmutableSet.of(ENV_ID + 4));

    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, asList(ENV_ID + 3), Collections.emptyList()), environmentService, featureFlagService);
    when(governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfig)).thenReturn(frozenEnvs);
    assertThatThrownBy(() -> deploymentFreezeChecker.checkIfEnvFrozen(ACCOUNT_ID, governanceConfig))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Window [FREEZE2] is active for the environment. No deployments are allowed to proceed.")
        .extracting("accountId")
        .isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void checkEnvNotFrozenByWindow() {
    GovernanceConfig governanceConfig = generateGovernanceConfig();
    Map<String, Set<String>> frozenEnvs = new HashMap<>();
    frozenEnvs.put(FREEZE_ID, ImmutableSet.of(ENV_ID, ENV_ID + 2));
    frozenEnvs.put(FREEZE_ID + 2, ImmutableSet.of(ENV_ID + 3, ENV_ID + 2));
    frozenEnvs.put(FREEZE_ID + 3, ImmutableSet.of(ENV_ID + 4));

    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, asList(ENV_ID + 5), Collections.emptyList()), environmentService, featureFlagService);
    when(governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfigService.get(ACCOUNT_ID)))
        .thenReturn(frozenEnvs);
    deploymentFreezeChecker.checkIfEnvFrozen(ACCOUNT_ID, governanceConfig);
    verify(governanceConfigService, times(1)).getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfig);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void checkEnvNotFrozenIfNoWindowsActive() {
    GovernanceConfig governanceConfig = generateGovernanceConfig();

    Map<String, Set<String>> frozenEnvs = new HashMap<>();
    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, asList(ENV_ID + 5), Collections.emptyList()), environmentService, featureFlagService);
    when(governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfigService.get(ACCOUNT_ID)))
        .thenReturn(frozenEnvs);
    deploymentFreezeChecker.checkIfEnvFrozen(ACCOUNT_ID, governanceConfig);
    verify(governanceConfigService, times(1)).getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfig);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void checkAppFrozenIfEnvIsEmpty() {
    GovernanceConfig governanceConfig = generateGovernanceConfig();

    Map<String, Set<String>> frozenEnvs = new HashMap<>();
    frozenEnvs.put(FREEZE_ID, ImmutableSet.of(ENV_ID, ENV_ID + 2));
    frozenEnvs.put(FREEZE_ID + 2, ImmutableSet.of(ENV_ID + 3, ENV_ID + 2));
    frozenEnvs.put(FREEZE_ID + 3, ImmutableSet.of(ENV_ID + 4));
    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, Collections.emptyList(), Collections.emptyList()), environmentService,
        featureFlagService);
    when(governanceConfigService.get(ACCOUNT_ID)).thenReturn(governanceConfig);
    when(featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, ACCOUNT_ID)).thenReturn(true);
    when(governanceConfigService.getFrozenEnvIdsForApp(ACCOUNT_ID, APP_ID, governanceConfig)).thenReturn(frozenEnvs);
    assertThatThrownBy(() -> deploymentFreezeChecker.check(ACCOUNT_ID))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Window [FREEZE1] is active for the environment. No deployments are allowed to proceed.")
        .extracting("deploymentFreezeIds", InstanceOfAssertFactories.ITERABLE)
        .containsExactlyInAnyOrder(FREEZE_ID);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkDeploymentFreezeWhenOnlyEnvFrozen() {
    GovernanceConfig governanceConfig = generateGovernanceConfig();
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).setServiceSelection(
        new ServiceFilter(ServiceFilterType.CUSTOM, Collections.singletonList(SERVICE_ID + 1)));

    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, Collections.singletonList(ENV_ID + 2), Collections.singletonList(SERVICE_ID)),
        environmentService, featureFlagService);
    when(governanceConfigService.getEnvIdsFromAppSelection(any(), any()))
        .thenReturn(Arrays.asList(ENV_ID, ENV_ID + 1, ENV_ID + 2));
    when(governanceConfigService.getServiceIdsFromAppSelection(any(), any())).thenReturn(Arrays.asList(SERVICE_ID + 1));
    deploymentFreezeChecker.checkIfServiceFrozen(ACCOUNT_ID, governanceConfig);
    verify(governanceConfigService, times(1))
        .getServiceIdsFromAppSelection(
            APP_ID, governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkDeploymentFreezeWhenOnlyServiceFrozen() {
    GovernanceConfig governanceConfig = generateGovernanceConfig();
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).setServiceSelection(
        new ServiceFilter(ServiceFilterType.CUSTOM, Collections.singletonList(SERVICE_ID + 1)));

    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, Collections.singletonList(ENV_ID + 2), Collections.singletonList(SERVICE_ID)),
        environmentService, featureFlagService);
    when(governanceConfigService.getEnvIdsFromAppSelection(any(), any())).thenReturn(Arrays.asList(ENV_ID, ENV_ID + 1));
    when(governanceConfigService.getServiceIdsFromAppSelection(any(), any()))
        .thenReturn(Arrays.asList(SERVICE_ID, SERVICE_ID + 1));
    deploymentFreezeChecker.checkIfServiceFrozen(ACCOUNT_ID, governanceConfig);
    verify(governanceConfigService, times(1))
        .getEnvIdsFromAppSelection(
            APP_ID, governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkDeploymentFreezeWhenBothEnvServiceFrozen() {
    GovernanceConfig governanceConfig = generateGovernanceConfig();
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).setServiceSelection(
        new ServiceFilter(ServiceFilterType.CUSTOM, Arrays.asList(SERVICE_ID, SERVICE_ID + 1)));

    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, Collections.singletonList(ENV_ID + 2), Collections.singletonList(SERVICE_ID)),
        environmentService, featureFlagService);
    when(governanceConfigService.getEnvIdsFromAppSelection(any(), any()))
        .thenReturn(Arrays.asList(ENV_ID, ENV_ID + 1, ENV_ID + 2));
    when(governanceConfigService.getServiceIdsFromAppSelection(any(), any()))
        .thenReturn(Arrays.asList(SERVICE_ID, SERVICE_ID + 1));
    assertThatThrownBy(() -> deploymentFreezeChecker.checkIfServiceFrozen(ACCOUNT_ID, governanceConfig))
        .isInstanceOf(DeploymentFreezeException.class)
        .hasMessage(
            "Deployment Freeze Window [FREEZE1] is active for the service. No deployments are allowed to proceed.")
        .extracting("accountId")
        .isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void checkDeploymentFreezeExclusionWhenBothEnvServiceFrozenAndExcluded() {
    when(featureFlagService.isEnabled(FeatureName.SPG_NEW_DEPLOYMENT_FREEZE_EXCLUSIONS, ACCOUNT_ID)).thenReturn(true);
    GovernanceConfig governanceConfig = generateGovernanceConfig();
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).setServiceSelection(
        new ServiceFilter(ServiceFilterType.CUSTOM, Arrays.asList(SERVICE_ID, SERVICE_ID + 1)));
    governanceConfig.getTimeRangeBasedFreezeConfigs().get(0).setExcludeAppSelections(asList(
        CustomAppFilter.builder()
            .apps(asList(APP_ID))
            .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
            .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
            .serviceSelection(new ServiceFilter(ServiceFilterType.CUSTOM, Arrays.asList(SERVICE_ID, SERVICE_ID + 1)))
            .build()));

    DeploymentFreezeChecker deploymentFreezeChecker = new DeploymentFreezeChecker(governanceConfigService,
        new DeploymentCtx(APP_ID, Collections.singletonList(ENV_ID + 2), Collections.singletonList(SERVICE_ID)),
        environmentService, featureFlagService);
    when(governanceConfigService.getEnvIdsFromAppSelection(any(), any()))
        .thenReturn(Arrays.asList(ENV_ID, ENV_ID + 1, ENV_ID + 2));
    when(governanceConfigService.getServiceIdsFromAppSelection(any(), any()))
        .thenReturn(Arrays.asList(SERVICE_ID, SERVICE_ID + 1));
    Assertions.assertThatCode(() -> deploymentFreezeChecker.checkIfServiceFrozen(ACCOUNT_ID, governanceConfig))
        .doesNotThrowAnyException();
  }

  private GovernanceConfig generateGovernanceConfig() {
    TimeRange timeRange = new TimeRange(
        System.currentTimeMillis(), System.currentTimeMillis() + 100_000, "", false, null, null, null, false);
    TimeRangeBasedFreezeConfig freezeConfig =
        TimeRangeBasedFreezeConfig.builder()
            .applicable(true)
            .appSelections(asList(CustomAppFilter.builder()
                                      .apps(asList(APP_ID))
                                      .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
                                      .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                      .build()))
            .name("FREEZE1")
            .timeRange(timeRange)
            .build();
    freezeConfig.setUuid(FREEZE_ID);
    TimeRangeBasedFreezeConfig freezeConfig2 =
        TimeRangeBasedFreezeConfig.builder().name("FREEZE2").timeRange(timeRange).build();
    freezeConfig2.setUuid(FREEZE_ID + 2);
    TimeRangeBasedFreezeConfig freezeConfig3 =
        TimeRangeBasedFreezeConfig.builder().name("FREEZE3").timeRange(timeRange).build();
    freezeConfig3.setUuid(FREEZE_ID + 3);
    return GovernanceConfig.builder()
        .deploymentFreeze(false)
        .timeRangeBasedFreezeConfigs(asList(freezeConfig, freezeConfig2, freezeConfig3))
        .build();
  }

  private GovernanceConfig generateGovernanceConfigTimeRangeTest() {
    // expired window with end time less than current time
    TimeRange timeRangeExpired = new TimeRange(System.currentTimeMillis(), System.currentTimeMillis() + 100_000,
        "Asia/Calcutta", false, null, System.currentTimeMillis() - 100_000, TimeRangeOccurrence.DAILY, false);

    // active window with future expiry
    TimeRange timeRangeActive = new TimeRange(System.currentTimeMillis(), System.currentTimeMillis() + 100_000,
        "Asia/Calcutta", false, null, System.currentTimeMillis() + 100_000_000, TimeRangeOccurrence.DAILY, false);

    // inactive window with future expiry
    TimeRange timeRangeInactive =
        new TimeRange(System.currentTimeMillis() + 100_000, System.currentTimeMillis() + 200_000, "Asia/Calcutta",
            false, null, System.currentTimeMillis() + 100_000_000, TimeRangeOccurrence.DAILY, false);

    // non recurring expired window
    TimeRange timeRangeExpiredNonRecurring = new TimeRange(System.currentTimeMillis() - 200_000,
        System.currentTimeMillis() - 100_000, "Asia/Calcutta", false, null, null, null, false);

    // non recurring inactive window schedules in future
    TimeRange timeRangeNonRecurringInActive = new TimeRange(System.currentTimeMillis() + 200_000,
        System.currentTimeMillis() + 300_000, "Asia/Calcutta", false, null, null, null, false);

    // non recurring active window
    TimeRange timeRangeNonRecurringActive = new TimeRange(System.currentTimeMillis(),
        System.currentTimeMillis() + 300_000, "Asia/Calcutta", false, null, null, null, false);

    TimeRangeBasedFreezeConfig freezeConfig =
        TimeRangeBasedFreezeConfig.builder()
            .applicable(true)
            .appSelections(asList(CustomAppFilter.builder()
                                      .apps(asList(APP_ID))
                                      .blackoutWindowFilterType(BlackoutWindowFilterType.CUSTOM)
                                      .envSelection(new AllEnvFilter(EnvironmentFilterType.ALL))
                                      .build()))
            .name("FREEZE1")
            .timeRange(timeRangeExpired)
            .build();
    TimeRangeBasedFreezeConfig freezeConfig2 =
        TimeRangeBasedFreezeConfig.builder().name("FREEZE2").applicable(true).timeRange(timeRangeActive).build();
    TimeRangeBasedFreezeConfig freezeConfig3 =
        TimeRangeBasedFreezeConfig.builder().name("FREEZE3").applicable(true).timeRange(timeRangeInactive).build();

    TimeRangeBasedFreezeConfig freezeConfig4 = TimeRangeBasedFreezeConfig.builder()
                                                   .name("FREEZE4")
                                                   .applicable(true)
                                                   .timeRange(timeRangeExpiredNonRecurring)
                                                   .build();
    TimeRangeBasedFreezeConfig freezeConfig5 = TimeRangeBasedFreezeConfig.builder()
                                                   .name("FREEZE5")
                                                   .applicable(true)
                                                   .timeRange(timeRangeNonRecurringInActive)
                                                   .build();

    TimeRangeBasedFreezeConfig freezeConfig6 = TimeRangeBasedFreezeConfig.builder()
                                                   .name("FREEZE6")
                                                   .applicable(true)
                                                   .timeRange(timeRangeNonRecurringActive)
                                                   .build();

    return GovernanceConfig.builder()
        .deploymentFreeze(false)
        .timeRangeBasedFreezeConfigs(
            asList(freezeConfig, freezeConfig2, freezeConfig3, freezeConfig4, freezeConfig5, freezeConfig6))
        .build();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkActiveStatus() {
    GovernanceConfig governanceConfig = generateGovernanceConfigTimeRangeTest();

    governanceConfig.getTimeRangeBasedFreezeConfigs().forEach(TimeRangeBasedFreezeConfig::toggleExpiredWindowsOff);
    governanceConfig.getTimeRangeBasedFreezeConfigs().forEach(TimeRangeBasedFreezeConfig::recalculateFreezeWindowState);

    // toggle the expired window as off
    TimeRangeBasedFreezeConfig freezeConfigExpired = governanceConfig.getTimeRangeBasedFreezeConfigs().get(0);
    assertThat(freezeConfigExpired.getFreezeWindowState())
        .isEqualTo(TimeRangeBasedFreezeConfig.FreezeWindowStateType.INACTIVE);
    assertThat(freezeConfigExpired.isApplicable()).isEqualTo(false);

    TimeRangeBasedFreezeConfig freezeConfigActive = governanceConfig.getTimeRangeBasedFreezeConfigs().get(1);
    assertThat(freezeConfigActive.getFreezeWindowState())
        .isEqualTo(TimeRangeBasedFreezeConfig.FreezeWindowStateType.ACTIVE);
    assertThat(freezeConfigActive.isApplicable()).isEqualTo(true);

    TimeRangeBasedFreezeConfig freezeConfigInactive = governanceConfig.getTimeRangeBasedFreezeConfigs().get(2);
    assertThat(freezeConfigInactive.getFreezeWindowState())
        .isEqualTo(TimeRangeBasedFreezeConfig.FreezeWindowStateType.INACTIVE);
    assertThat(freezeConfigInactive.isApplicable()).isEqualTo(true);

    // toggle the expired window as off
    TimeRangeBasedFreezeConfig freezeConfigNonRecurringExpired =
        governanceConfig.getTimeRangeBasedFreezeConfigs().get(3);
    assertThat(freezeConfigNonRecurringExpired.getFreezeWindowState())
        .isEqualTo(TimeRangeBasedFreezeConfig.FreezeWindowStateType.INACTIVE);
    assertThat(freezeConfigNonRecurringExpired.isApplicable()).isEqualTo(false);

    TimeRangeBasedFreezeConfig freezeConfigNonRecurringInactive =
        governanceConfig.getTimeRangeBasedFreezeConfigs().get(4);
    assertThat(freezeConfigNonRecurringInactive.getFreezeWindowState())
        .isEqualTo(TimeRangeBasedFreezeConfig.FreezeWindowStateType.INACTIVE);
    assertThat(freezeConfigNonRecurringInactive.isApplicable()).isEqualTo(true);

    TimeRangeBasedFreezeConfig freezeConfigNonRecurringActive =
        governanceConfig.getTimeRangeBasedFreezeConfigs().get(5);
    assertThat(freezeConfigNonRecurringActive.getFreezeWindowState())
        .isEqualTo(TimeRangeBasedFreezeConfig.FreezeWindowStateType.ACTIVE);
    assertThat(freezeConfigNonRecurringActive.isApplicable()).isEqualTo(true);
  }
}
