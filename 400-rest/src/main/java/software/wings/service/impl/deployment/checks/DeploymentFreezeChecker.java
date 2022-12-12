/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.deployment.checks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.DEPLOYMENT_GOVERNANCE_ERROR;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.data.validator.ConditionsValidator;
import io.harness.data.validator.ConditionsValidator.Condition;
import io.harness.eraro.Level;
import io.harness.exception.DeploymentFreezeException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomAppFilter;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.GovernanceFreezeConfig;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.WeeklyFreezeConfig;

import software.wings.beans.Environment;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.deployment.PreDeploymentChecker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
@ParametersAreNonnullByDefault
@TargetModule(HarnessModule._960_API_SERVICES)
public class DeploymentFreezeChecker implements PreDeploymentChecker {
  private GovernanceConfigService governanceConfigService;
  private DeploymentCtx deploymentCtx;
  private EnvironmentService environmentService;
  private FeatureFlagService featureFlagService;

  public DeploymentFreezeChecker(GovernanceConfigService governanceConfigService, DeploymentCtx deploymentCtx,
      EnvironmentService environmentService, FeatureFlagService featureFlagService) {
    this.governanceConfigService = governanceConfigService;
    this.deploymentCtx = deploymentCtx;
    this.environmentService = environmentService;
    this.featureFlagService = featureFlagService;
  }

  @Override
  public void check(String accountId) {
    GovernanceConfig governanceConfig = governanceConfigService.get(accountId);
    if (governanceConfig == null) {
      return;
    }

    if (governanceConfig.isDeploymentFreeze()) {
      throw new DeploymentFreezeException(DEPLOYMENT_GOVERNANCE_ERROR, Level.INFO, USER, accountId,
          Collections.emptyList(), Collections.emptyList(), "", true, false);
    }

    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)) {
      if (featureFlagService.isNotEnabled(FeatureName.SPG_NEW_DEPLOYMENT_FREEZE_EXCLUSIONS, accountId)) {
        for (TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig :
            governanceConfig.getTimeRangeBasedFreezeConfigs()) {
          // Set excludeAppSelections to an empty list if exclusions FF is not enabled.
          timeRangeBasedFreezeConfig.setExcludeAppSelections(new ArrayList<>());
        }
      }
      if (isEmpty(deploymentCtx.getEnvIds())) {
        checkIfAppFrozen(governanceConfig, accountId);
      }
      checkIfEnvFrozen(accountId, governanceConfig);
      checkIfServiceFrozen(accountId, governanceConfig);
      return;
    }

    if (matches(deploymentCtx, governanceConfig)) {
      log.info("Deployment Context matches governance config. accountId={} GovernanceConfig: {} Ctx: {}", accountId,
          governanceConfig, deploymentCtx);
      throw new WingsException(GENERAL_ERROR, USER)
          .addParam("message", "Deployment Freeze window is active. No deployments are allowed.");
    }
  }

  public void checkIfServiceFrozen(String accountId, GovernanceConfig governanceConfig) {
    List<TimeRangeBasedFreezeConfig> blockingWindows;
    if (featureFlagService.isEnabled(FeatureName.SPG_NEW_DEPLOYMENT_FREEZE_EXCLUSIONS, accountId)) {
      blockingWindows = governanceConfig.getTimeRangeBasedFreezeConfigs()
                            .stream()
                            .filter(TimeRangeBasedFreezeConfig::checkIfActive)
                            .filter(window -> isServiceFrozenWithExclusions(governanceConfig, window))
                            .collect(Collectors.toList());
    } else {
      blockingWindows = governanceConfig.getTimeRangeBasedFreezeConfigs()
                            .stream()
                            .filter(TimeRangeBasedFreezeConfig::checkIfActive)
                            .filter(window -> isServiceFrozen(governanceConfig, window))
                            .collect(Collectors.toList());
    }
    if (isNotEmpty(blockingWindows)) {
      throw new DeploymentFreezeException(DEPLOYMENT_GOVERNANCE_ERROR, Level.INFO, USER, accountId,
          blockingWindows.stream().map(GovernanceFreezeConfig::getUuid).collect(Collectors.toList()),
          blockingWindows.stream().map(GovernanceFreezeConfig::getName).collect(Collectors.toList()),
          blockingWindows.stream().map(GovernanceFreezeConfig::getName).collect(Collectors.joining(", ", "[", "]")),
          false, true);
    }
  }

  private boolean isServiceFrozen(GovernanceConfig governanceConfig, TimeRangeBasedFreezeConfig window) {
    // We will need to check here if some services are frozen.
    //
    boolean isFrozen = false;
    for (ApplicationFilter applicationFilter : window.getAppSelections()) {
      if (BlackoutWindowFilterType.CUSTOM.equals(applicationFilter.getFilterType())) {
        // custom app filters with more than 1 entry cannot freeze individual services so those cases would be handled
        // by isEnvFrozen logic
        List<String> appIds = ((CustomAppFilter) applicationFilter).getApps();
        if (appIds.size() == 1) {
          if (!Collections.disjoint(governanceConfigService.getEnvIdsFromAppSelection(appIds.get(0), applicationFilter),
                  deploymentCtx.getEnvIds())
              && !Collections.disjoint(
                  governanceConfigService.getServiceIdsFromAppSelection(appIds.get(0), applicationFilter),
                  deploymentCtx.getServiceIds())) {
            isFrozen = true;
          }
        }
      }
    }
    return isFrozen;
  }

  private boolean isServiceFrozenWithExclusions(GovernanceConfig governanceConfig, TimeRangeBasedFreezeConfig window) {
    // We will need to check here if some services are frozen.
    //
    boolean isFrozen = false;
    for (ApplicationFilter applicationFilter : window.getAppSelections()) {
      if (BlackoutWindowFilterType.CUSTOM.equals(applicationFilter.getFilterType())) {
        List<String> appIds = ((CustomAppFilter) applicationFilter).getApps();
        for (String appId : appIds) {
          if (!Collections.disjoint(governanceConfigService.getEnvIdsFromAppSelection(appId, applicationFilter),
                  deploymentCtx.getEnvIds())
              && !Collections.disjoint(governanceConfigService.getServiceIdsFromAppSelection(appId, applicationFilter),
                  deploymentCtx.getServiceIds())) {
            isFrozen = true;
            break;
          }
        }
      }
    }
    // Handle exclusion logic
    for (ApplicationFilter applicationFilter : window.getExcludeAppSelections()) {
      if (BlackoutWindowFilterType.CUSTOM.equals(applicationFilter.getFilterType())) {
        List<String> appIds = ((CustomAppFilter) applicationFilter).getApps();
        for (String appId : appIds) {
          if (!Collections.disjoint(governanceConfigService.getEnvIdsFromAppSelection(appId, applicationFilter),
                  deploymentCtx.getEnvIds())
              && !Collections.disjoint(governanceConfigService.getServiceIdsFromAppSelection(appId, applicationFilter),
                  deploymentCtx.getServiceIds())) {
            isFrozen = false;
            break;
          }
        }
      }
    }
    return isFrozen;
  }

  // Checks if the app is completely frozen then sends notification to all windows that freeze the app
  private void checkIfAppFrozen(GovernanceConfig governanceConfig, String accountId) {
    if (isEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      return;
    }
    List<GovernanceFreezeConfig> blockingWindows =
        governanceConfig.getTimeRangeBasedFreezeConfigs()
            .stream()
            .filter(freezeWindow -> freezeWindow.checkIfActive() && containsApplication(freezeWindow))
            .collect(Collectors.toList());
    if (isNotEmpty(blockingWindows)) {
      throw new DeploymentFreezeException(DEPLOYMENT_GOVERNANCE_ERROR, Level.INFO, USER, accountId,
          blockingWindows.stream().map(GovernanceFreezeConfig::getUuid).collect(Collectors.toList()),
          blockingWindows.stream().map(GovernanceFreezeConfig::getName).collect(Collectors.toList()),
          blockingWindows.stream().map(GovernanceFreezeConfig::getName).collect(Collectors.joining(", ", "[", "]")),
          false, false);
    }
  }

  // To check if a freeze window freezes that particular application completely
  private boolean containsApplication(TimeRangeBasedFreezeConfig freezeWindow) {
    if (isEmpty(freezeWindow.getAppSelections())) {
      return false;
    }
    boolean is_frozen =
        freezeWindow.getAppSelections()
            .stream()
            .filter(appSelection -> appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.ALL)
            .anyMatch(appSelection
                -> appSelection.getFilterType() == BlackoutWindowFilterType.ALL
                    || ((CustomAppFilter) appSelection).getApps().contains(deploymentCtx.getAppId()));

    boolean is_excluded_from_freeze =
        freezeWindow.getExcludeAppSelections()
            .stream()
            .filter(appSelection -> appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.ALL)
            .anyMatch(appSelection
                -> appSelection.getFilterType() == BlackoutWindowFilterType.ALL
                    || ((CustomAppFilter) appSelection).getApps().contains(deploymentCtx.getAppId()));

    return is_frozen && !is_excluded_from_freeze;
  }

  void checkIfEnvFrozen(String accountId, GovernanceConfig governanceConfig) {
    Map<String, Set<String>> frozenEnvsByWindow =
        governanceConfigService.getFrozenEnvIdsForApp(accountId, deploymentCtx.getAppId(), governanceConfig);
    if (isNotEmpty(deploymentCtx.getEnvIds()) && isNotEmpty(frozenEnvsByWindow)) {
      // In case of pipeline with multiple envIds, we just check for the first stage environment(s). If any of the
      // successive environments are frozen, pipeline is rejected at that stage
      Set<String> allBlockedEnvs =
          frozenEnvsByWindow.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
      if (!allBlockedEnvs.containsAll(deploymentCtx.getEnvIds())) {
        return;
      }

      // Windows which blocks any of the environment in check
      List<GovernanceFreezeConfig> blockingWindows =
          frozenEnvsByWindow.entrySet()
              .stream()
              .filter(entry -> deploymentCtx.getEnvIds().stream().anyMatch(envId -> entry.getValue().contains(envId)))
              .map(entry -> getFreezeWindow(entry.getKey(), governanceConfig))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (isNotEmpty(blockingWindows)) {
        throw new DeploymentFreezeException(DEPLOYMENT_GOVERNANCE_ERROR, Level.INFO, USER, accountId,
            blockingWindows.stream().map(GovernanceFreezeConfig::getUuid).collect(Collectors.toList()),
            blockingWindows.stream().map(GovernanceFreezeConfig::getName).collect(Collectors.toList()),
            blockingWindows.stream().map(GovernanceFreezeConfig::getName).collect(Collectors.joining(", ", "[", "]")),
            false, false);
      }
    }
  }

  private GovernanceFreezeConfig getFreezeWindow(String freezeId, GovernanceConfig governanceConfig) {
    if (isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      return governanceConfig.getTimeRangeBasedFreezeConfigs()
          .stream()
          .filter(freeze -> freezeId.equals(freeze.getUuid()))
          .findAny()
          .orElse(null);
    }
    return null;
  }

  public boolean matches(DeploymentCtx deploymentCtx, GovernanceConfig freezeConfig) {
    boolean timeBasedFreezeMatch = freezeConfig.getTimeRangeBasedFreezeConfigs().stream().anyMatch(
        (TimeRangeBasedFreezeConfig it) -> this.matches(deploymentCtx, it) && this.matchesTimeRange(it));
    log.info("DeploymentCtx: {}, governanceConfig(date range based): {}. Match: {}", deploymentCtx,
        freezeConfig.getUuid(), timeBasedFreezeMatch);
    boolean weeklyFreezeMatch = freezeConfig.getWeeklyFreezeConfigs().stream().anyMatch(
        (WeeklyFreezeConfig it) -> this.matches(deploymentCtx, it) && this.matchesWeeklyRange(it));
    log.info("DeploymentCtx: {}, governanceConfig(weekly window): {}. Match: {}", deploymentCtx, freezeConfig.getUuid(),
        weeklyFreezeMatch);
    return timeBasedFreezeMatch || weeklyFreezeMatch;
  }

  public boolean matches(final DeploymentCtx deploymentCtx, final GovernanceFreezeConfig freezeConfig) {
    ConditionsValidator conditions = new ConditionsValidator();

    // apps should match
    Condition condition = new Condition(
        "Freeze configuration apps match deployment context apps", () -> appsMatcher(freezeConfig, deploymentCtx));
    conditions.addCondition(condition);

    // environment Types should match
    condition = new Condition("Freeze configuration env types should match deployment env types",
        () -> envTypeMatcher(freezeConfig, deploymentCtx));
    conditions.addCondition(condition);

    return conditions.allConditionsSatisfied();
  }

  public boolean matchesTimeRange(final TimeRangeBasedFreezeConfig freezeConfig) {
    ConditionsValidator conditions = new ConditionsValidator();
    // time range should match
    Condition condition =
        new Condition("Freeze configuration contains current timestamp", () -> freezeConfig.getTimeRange().isInRange());
    conditions.addCondition(condition);

    return conditions.allConditionsSatisfied();
  }

  public boolean matchesWeeklyRange(final WeeklyFreezeConfig freezeConfig) {
    ConditionsValidator conditions = new ConditionsValidator();
    // time range should match
    Condition condition = new Condition(
        "Freeze configuration contains current timestamp", () -> freezeConfig.getWeeklyRange().isInRange());
    conditions.addCondition(condition);

    return conditions.allConditionsSatisfied();
  }

  private Boolean envTypeMatcher(final GovernanceFreezeConfig freezeConfig, final DeploymentCtx deploymentCtx) {
    boolean envTypesIsAll = false;

    if (!freezeConfig.getEnvironmentTypes().isEmpty()) {
      envTypesIsAll = freezeConfig.getEnvironmentTypes().get(0) == EnvironmentType.ALL;
    }

    if (envTypesIsAll) {
      return true;
    }

    List<EnvironmentType> envTypesInDeploymentCtx =
        environmentService.getEnvByApp(deploymentCtx.getAppId())
            .stream()
            .filter(env -> deploymentCtx.getEnvIds().contains(env.getUuid()))
            .map(Environment::getEnvironmentType)
            .collect(Collectors.toList());

    return !CollectionUtils.intersection(freezeConfig.getEnvironmentTypes(), envTypesInDeploymentCtx).isEmpty();
  }

  /**
   * @return true if deployment context satisfies the freeze config. That is the freeze should be applied.
   */
  private Boolean appsMatcher(final GovernanceFreezeConfig freezeConfig, final DeploymentCtx deploymentCtx) {
    boolean freezeEnabledForAllApps = freezeConfig.isFreezeForAllApps();

    if (freezeEnabledForAllApps) {
      return true;
    } else {
      return freezeConfig.getAppIds().contains(deploymentCtx.getAppId());
    }
  }
}
