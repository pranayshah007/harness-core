/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.compliance;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.event.model.EventType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.AllUserGroupFilter;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomAppFilter;
import io.harness.governance.CustomEnvFilter;
import io.harness.governance.CustomUserGroupFilter;
import io.harness.governance.DeploymentFreezeInfo;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.GovernanceFreezeConfig;
import io.harness.governance.ServiceFilter.ServiceFilterType;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.validation.Validator;

import software.wings.beans.Event.Type;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.governance.GovernanceConfig.GovernanceConfigKeys;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.features.GovernanceFeature;
import software.wings.features.api.AccountId;
import software.wings.features.api.RestrictedApi;
import software.wings.resources.stats.model.TimeRange;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.deployment.checks.DeploymentFreezeUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.segment.analytics.messages.TrackMessage;
import com.segment.analytics.messages.TrackMessage.Builder;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

/**
 * @author rktummala on 02/04/19
 */
@Slf4j
@ValidateOnExecution
@Singleton
@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(HarnessTeam.CDC)
public class GovernanceConfigServiceImpl implements GovernanceConfigService {
  private static final long MIN_FREEZE_WINDOW_TIME = 1800000L;
  private static final long MAX_FREEZE_WINDOW_TIME = 31536000000L;
  public static final String GOVERNANCE_CONFIG = "GOVERNANCE_CONFIG";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private SegmentHelper segmentHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AppService appService;
  @Inject private EnvironmentService environmentService;
  @Inject private DeploymentFreezeActivationHandler freezeActivationHandler;
  @Inject private DeploymentFreezeDeactivationHandler freezeDeactivationHandler;
  @Inject private YamlPushService yamlPushService;
  @Inject private UserGroupService userGroupService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private DeploymentFreezeUtils deploymentFreezeUtils;
  @Inject private ExecutorService executorService;

  @Override
  public GovernanceConfig get(String accountId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.debug("Getting Deployment Freeze window");
      GovernanceConfig governanceConfig =
          wingsPersistence.createQuery(GovernanceConfig.class).filter(GovernanceConfigKeys.accountId, accountId).get();
      if (governanceConfig == null) {
        return getDefaultGovernanceConfig(accountId);
      }
      toggleExpiredWindows(governanceConfig, accountId);
      populateWindowsStatus(governanceConfig, accountId);
      populateUserGroupInBackwardCompatibleManner(governanceConfig, accountId);
      return governanceConfig;
    }
  }

  private void populateUserGroupInBackwardCompatibleManner(GovernanceConfig governanceConfig, String accountId) {
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)
        && isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      governanceConfig.getTimeRangeBasedFreezeConfigs().forEach(timeRangeBasedFreezeConfig -> {
        if (timeRangeBasedFreezeConfig.getUserGroups() != null) {
          timeRangeBasedFreezeConfig.setUserGroupSelection(CustomUserGroupFilter.builder()
                                                               .userGroupFilterType(BlackoutWindowFilterType.CUSTOM)
                                                               .userGroups(timeRangeBasedFreezeConfig.getUserGroups())
                                                               .build());
        }
      });
    }
  }

  private void populateWindowsStatus(GovernanceConfig governanceConfig, String accountId) {
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)
        && isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      governanceConfig.getTimeRangeBasedFreezeConfigs().forEach(
          TimeRangeBasedFreezeConfig::recalculateFreezeWindowState);
    }
  }

  private void toggleExpiredWindows(GovernanceConfig governanceConfig, String accountId) {
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)
        && isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      governanceConfig.getTimeRangeBasedFreezeConfigs().forEach(TimeRangeBasedFreezeConfig::toggleExpiredWindowsOff);
    }
  }

  private GovernanceConfig getDefaultGovernanceConfig(String accountId) {
    return GovernanceConfig.builder().accountId(accountId).deploymentFreeze(false).build();
  }

  @Override
  @RestrictedApi(GovernanceFeature.class)
  public GovernanceConfig upsert(@AccountId String accountId, @Nonnull GovernanceConfig governanceConfig) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      boolean newDeploymentFreezeEnabled = featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId);

      log.info("Updating Deployment Freeze window");
      GovernanceConfig oldSetting = get(accountId);

      if (newDeploymentFreezeEnabled) {
        validateDeploymentFreezeInput(governanceConfig.getTimeRangeBasedFreezeConfigs(), accountId, oldSetting);
        resetReadOnlyProperties(governanceConfig.getTimeRangeBasedFreezeConfigs(), accountId, oldSetting);
        checkForWindowActivationAndSendNotification(
            governanceConfig.getTimeRangeBasedFreezeConfigs(), oldSetting.getTimeRangeBasedFreezeConfigs(), accountId);
        checkIfOnlyOneTypeOfUserGroupIsSet(governanceConfig.getTimeRangeBasedFreezeConfigs(), accountId);
      }

      Query<GovernanceConfig> query =
          wingsPersistence.createQuery(GovernanceConfig.class).filter(GovernanceConfigKeys.accountId, accountId);

      UpdateOperations<GovernanceConfig> updateOperations =
          wingsPersistence.createUpdateOperations(GovernanceConfig.class)
              .set(GovernanceConfigKeys.deploymentFreeze, governanceConfig.isDeploymentFreeze())
              .set(GovernanceConfigKeys.timeRangeBasedFreezeConfigs, governanceConfig.getTimeRangeBasedFreezeConfigs())
              .set(GovernanceConfigKeys.weeklyFreezeConfigs, governanceConfig.getWeeklyFreezeConfigs());

      User user = UserThreadLocal.get();
      if (null != user) {
        EmbeddedUser embeddedUser =
            new EmbeddedUser(user.getUuid(), user.getName(), user.getEmail(), user.getExternalUserId());
        updateOperations.set(GovernanceConfigKeys.lastUpdatedBy, embeddedUser);
      } else {
        log.error("ThreadLocal User is null when trying to update governance config. accountId={}", accountId);
      }

      if (newDeploymentFreezeEnabled) {
        governanceConfig.recalculateNextIterations(GovernanceConfigKeys.nextIterations, true, 0);
        governanceConfig.recalculateNextIterations(GovernanceConfigKeys.nextCloseIterations, true, 0);
        updateOperations.set(GovernanceConfigKeys.nextIterations, governanceConfig.getNextIterations());
        updateOperations.set(GovernanceConfigKeys.nextCloseIterations, governanceConfig.getNextCloseIterations());
      }

      GovernanceConfig updatedSetting =
          wingsPersistence.findAndModify(query, updateOperations, WingsPersistence.upsertReturnNewOptions);
      executorService.submit(() -> updateUserGroupReference(updatedSetting, oldSetting, accountId));

      // push service also adds audit trail, in case of no yaml we add the entry explicitly
      if (newDeploymentFreezeEnabled) {
        yamlPushService.pushYamlChangeSet(
            accountId, oldSetting, updatedSetting, Type.UPDATE, governanceConfig.isSyncFromGit(), false);
      } else {
        auditDeploymentFreeze(accountId, oldSetting, updatedSetting);
      }

      if (newDeploymentFreezeEnabled) {
        freezeDeactivationHandler.wakeup();
        freezeActivationHandler.wakeup();
      }

      if (!ListUtils.isEqualList(
              oldSetting.getTimeRangeBasedFreezeConfigs(), governanceConfig.getTimeRangeBasedFreezeConfigs())) {
        publishToSegment(accountId, user, EventType.BLACKOUT_WINDOW_UPDATED);
      }

      if (!ListUtils.isEqualList(oldSetting.getWeeklyFreezeConfigs(), governanceConfig.getWeeklyFreezeConfigs())) {
        publishToSegment(accountId, user, EventType.BLACKOUT_WINDOW_UPDATED);
      }
      toggleExpiredWindows(updatedSetting, accountId);
      populateWindowsStatus(updatedSetting, accountId);
      return updatedSetting;
    }
  }

  private void updateUserGroupReference(
      GovernanceConfig updatedSetting, GovernanceConfig oldSetting, String accountId) {
    try {
      List<TimeRangeBasedFreezeConfig> oldTimeRangeBasedFreezeConfigs = oldSetting.getTimeRangeBasedFreezeConfigs();
      List<TimeRangeBasedFreezeConfig> newTimeRangeBasedFreezeConfigs = updatedSetting.getTimeRangeBasedFreezeConfigs();
      Set<String> currentReferencedUserGroups = getReferencedUserGroupIds(oldTimeRangeBasedFreezeConfigs);
      Set<String> updatedReferencedUserGroups = getReferencedUserGroupIds(newTimeRangeBasedFreezeConfigs);
      updateFreezeWindowReferenceInUserGroup(currentReferencedUserGroups, updatedReferencedUserGroups, accountId,
          oldSetting.getAppId(), oldSetting.getUuid());
    } catch (Exception e) {
      log.error(
          "error while fetching the timeRangeBasedFreezeConfig in account with id {} in Governance Config with id {}",
          accountId, updatedSetting.getUuid());
    }
  }
  @Override
  public Set<String> getReferencedUserGroupIds(List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfig) {
    Set<String> referencedUserGroups = new HashSet<>();
    for (TimeRangeBasedFreezeConfig entry : timeRangeBasedFreezeConfig) {
      List<String> userGroups = new ArrayList<>();
      if (entry.getUserGroupSelection() == null) {
        userGroups = entry.getUserGroups();
      } else if (entry.getUserGroupSelection() instanceof CustomUserGroupFilter) {
        userGroups = ((CustomUserGroupFilter) entry.getUserGroupSelection()).getUserGroups();
      }
      for (String id : userGroups) {
        referencedUserGroups.add(id);
      }
    }
    return referencedUserGroups;
  }

  private void updateFreezeWindowReferenceInUserGroup(
      Set<String> previousUserGroups, Set<String> currentUserGroups, String accountId, String appId, String entityId) {
    Set<String> parentsToRemove = Sets.difference(previousUserGroups, currentUserGroups);
    Set<String> parentsToAdd = Sets.difference(currentUserGroups, previousUserGroups);

    for (String id : parentsToRemove) {
      userGroupService.removeParentsReference(id, accountId, appId, entityId, GOVERNANCE_CONFIG);
    }
    for (String id : parentsToAdd) {
      userGroupService.addParentsReference(id, accountId, appId, entityId, GOVERNANCE_CONFIG);
    }
  }

  private void checkIfOnlyOneTypeOfUserGroupIsSet(
      List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfigs, String accountId) {
    emptyIfNull(timeRangeBasedFreezeConfigs).forEach(timeRangeBasedFreezeConfig -> {
      if (isNotEmpty(timeRangeBasedFreezeConfig.getUserGroups())
          && timeRangeBasedFreezeConfig.getUserGroupSelection() != null) {
        if (timeRangeBasedFreezeConfig.getUserGroupSelection() instanceof AllUserGroupFilter) {
          throw new InvalidRequestException("Only one of user group list or UserGroupSelection can be set");
        } else if (!((CustomUserGroupFilter) timeRangeBasedFreezeConfig.getUserGroupSelection())
                        .getUserGroups()
                        .equals(timeRangeBasedFreezeConfig.getUserGroups())) {
          throw new InvalidRequestException("User group selection can't be different then user group");
        }
      }
    });
  }

  private void checkForWindowActivationAndSendNotification(
      List<TimeRangeBasedFreezeConfig> newWindows, List<TimeRangeBasedFreezeConfig> oldWindows, String accountId) {
    if (isEmpty(newWindows)) {
      return;
    }
    for (TimeRangeBasedFreezeConfig freezeWindow : newWindows) {
      Optional<TimeRangeBasedFreezeConfig> oldMatchingWindow =
          oldWindows.stream().filter(window -> window.getUuid().equals(freezeWindow.getUuid())).findFirst();

      if (!oldMatchingWindow.isPresent()) {
        if (freezeWindow.checkIfActive()) {
          executorService.submit(() -> deploymentFreezeUtils.handleActivationEvent(freezeWindow, accountId));
        }
      } else {
        if (freezeWindow.checkIfActive() && !oldMatchingWindow.get().checkIfActive()) {
          executorService.submit(() -> deploymentFreezeUtils.handleActivationEvent(freezeWindow, accountId));
        } else if (!freezeWindow.checkIfActive() && oldMatchingWindow.get().checkIfActive()) {
          executorService.submit(() -> deploymentFreezeUtils.handleDeActivationEvent(freezeWindow, accountId));
        }
      }
    }
  }

  @Override
  public Map<String, Set<String>> getFrozenEnvIdsForApp(
      String accountId, String appId, GovernanceConfig governanceConfig) {
    if (governanceConfig == null) {
      governanceConfig = get(accountId);
    }
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)) {
      if (isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
        Map<String, Set<String>> envIdsByWindow = new HashMap<>();
        for (TimeRangeBasedFreezeConfig freezeConfig : governanceConfig.getTimeRangeBasedFreezeConfigs()) {
          if (isNotEmpty(freezeConfig.getAppSelections()) && freezeConfig.checkIfActive()) {
            freezeConfig.getAppSelections().forEach(appSelection -> {
              if ((appSelection.getFilterType() == BlackoutWindowFilterType.ALL
                      || (appSelection.getFilterType() == BlackoutWindowFilterType.CUSTOM
                          && ((CustomAppFilter) appSelection).getApps().contains(appId)))
                  && areAllServicesFrozen(appSelection)) {
                envIdsByWindow.merge(freezeConfig.getUuid(),
                    new HashSet<>(getEnvIdsFromAppSelection(appId, appSelection)), (prevEnvSet, newEnvSet) -> {
                      prevEnvSet.addAll(newEnvSet);
                      return prevEnvSet;
                    });
              }
            });
          }
          if (isNotEmpty(freezeConfig.getExcludeAppSelections())) {
            freezeConfig.getExcludeAppSelections().forEach(appSelection -> {
              if (appSelection.getFilterType() == BlackoutWindowFilterType.ALL
                  || (appSelection.getFilterType() == BlackoutWindowFilterType.CUSTOM
                      && ((CustomAppFilter) appSelection).getApps().contains(appId))) {
                envIdsByWindow.merge(freezeConfig.getUuid(), new HashSet<>(), (prevEnvSet, newEnvSet) -> {
                  prevEnvSet.removeAll(new HashSet<>(getEnvIdsFromAppSelection(appId, appSelection)));
                  return prevEnvSet;
                });
              }
            });
          }
        }
        return envIdsByWindow;
      }
    }
    return Collections.emptyMap();
  }

  private boolean areAllServicesFrozen(ApplicationFilter appSelection) {
    return appSelection.getServiceSelection() == null
        || appSelection.getServiceSelection().getFilterType() == ServiceFilterType.ALL;
  }

  @Override
  public List<GovernanceFreezeConfig> getGovernanceFreezeConfigs(String accountId, List<String> deploymentFreezeIds) {
    GovernanceConfig governanceConfig = get(accountId);
    if (governanceConfig != null && EmptyPredicate.isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      return governanceConfig.getTimeRangeBasedFreezeConfigs()
          .stream()
          .filter(freeze -> deploymentFreezeIds.contains(freeze.getUuid()))
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  @Override
  public List<String> getEnvIdsFromAppSelection(String appId, ApplicationFilter appSelection) {
    switch (appSelection.getEnvSelection().getFilterType()) {
      case ALL:
        return environmentService.getEnvIdsByApp(appId);
      case ALL_NON_PROD:
        return environmentService.getEnvIdsByAppsAndType(
            Collections.singletonList(appId), EnvironmentType.NON_PROD.name());
      case ALL_PROD:
        return environmentService.getEnvIdsByAppsAndType(Collections.singletonList(appId), EnvironmentType.PROD.name());
      case CUSTOM:
        return ((CustomEnvFilter) appSelection.getEnvSelection()).getEnvironments();
      default:
    }
    return new ArrayList<>();
  }

  @Override
  public List<String> getServiceIdsFromAppSelection(String appId, ApplicationFilter appSelection) {
    switch (appSelection.getServiceSelection().getFilterType()) {
      case ALL:
        return serviceResourceService.findServicesByAppInternal(appId)
            .stream()
            .map(Service::getUuid)
            .collect(Collectors.toList());
      case CUSTOM:
        if (isEmpty(appSelection.getServiceSelection().getServices())) {
          return Collections.emptyList();
        } else {
          return appSelection.getServiceSelection().getServices();
        }
      default:
    }
    return Collections.emptyList();
  }

  @Override
  public DeploymentFreezeInfo getDeploymentFreezeInfo(String accountId) {
    GovernanceConfig governanceConfig = get(accountId);
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)) {
      Set<String> allEnvFrozenApps = new HashSet<>();
      Map<String, Set<String>> appEnvs = new HashMap<>();

      if (isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
        for (TimeRangeBasedFreezeConfig freezeConfig : governanceConfig.getTimeRangeBasedFreezeConfigs()) {
          if (isNotEmpty(freezeConfig.getAppSelections()) && isActive(freezeConfig)) {
            freezeConfig.getAppSelections().forEach(appSelection -> {
              if (appSelection.getServiceSelection() == null
                  || appSelection.getServiceSelection().getFilterType() != null) {
                Map<String, Set<String>> appEnvMap = getAppEnvMapForAppSelection(accountId, appSelection);
                if (isNotEmpty(appEnvMap)) {
                  appEnvMap.forEach((app, envSet) -> appEnvs.merge(app, envSet, (prevEnvSet, newEnvSet) -> {
                    prevEnvSet.addAll(newEnvSet);
                    return prevEnvSet;
                  }));
                }
                checkIfAllEnvAllServiceFrozenAndAdd(appSelection, allEnvFrozenApps, accountId);
              }
            });
          }
          if (featureFlagService.isEnabled(FeatureName.SPG_NEW_DEPLOYMENT_FREEZE_EXCLUSIONS, accountId)
              && isNotEmpty(freezeConfig.getExcludeAppSelections()) && isActive(freezeConfig)) {
            freezeConfig.getExcludeAppSelections().forEach(appSelection -> {
              Map<String, Set<String>> appEnvMap = getAppEnvMapForAppSelection(accountId, appSelection);
              if (isNotEmpty(appEnvMap)) {
                appEnvMap.forEach((app, envSet) -> appEnvs.merge(app, new HashSet<>(), (prevEnvSet, newEnvSet) -> {
                  prevEnvSet.removeAll(envSet);
                  return prevEnvSet;
                }));
                appEnvs.values().removeIf(Set::isEmpty);
              }
              checkIfAllEnvAllServiceExcludedFromFreezeAndRemove(appSelection, allEnvFrozenApps, accountId);
            });
          }
        }
      }
      return DeploymentFreezeInfo.builder()
          .freezeAll(governanceConfig.isDeploymentFreeze())
          .allEnvFrozenApps(allEnvFrozenApps)
          .appEnvs(appEnvs)
          .build();
    }
    return DeploymentFreezeInfo.builder()
        .freezeAll(false)
        .allEnvFrozenApps(Collections.emptySet())
        .appEnvs(Collections.emptyMap())
        .build();
  }

  private void checkIfAllEnvAllServiceFrozenAndAdd(
      ApplicationFilter appSelection, Set<String> allEnvFrozenApps, String accountId) {
    // Whole app is frozen when both Env Filters and Service filters are ALL frozen.
    // This also applies to previously created windows which did not have option to add service selection
    if (appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.ALL
        && (appSelection.getServiceSelection() == null
            || appSelection.getServiceSelection().getFilterType() == ServiceFilterType.ALL)) {
      if (appSelection.getFilterType() == BlackoutWindowFilterType.CUSTOM) {
        allEnvFrozenApps.addAll(((CustomAppFilter) appSelection).getApps());
      } else {
        allEnvFrozenApps.addAll(emptyIfNull(appService.getAppIdsByAccountId(accountId)));
      }
    }
  }

  private void checkIfAllEnvAllServiceExcludedFromFreezeAndRemove(
      ApplicationFilter appSelection, Set<String> allEnvFrozenApps, String accountId) {
    // Whole app is excluded from freeze when both Env Filters and Service filters are ALL selected.
    // This also applies to previously created windows which did not have option to add service selection
    if (appSelection.getFilterType() == BlackoutWindowFilterType.CUSTOM) {
      allEnvFrozenApps.removeAll(new HashSet<>(((CustomAppFilter) appSelection).getApps()));
    } else {
      allEnvFrozenApps.removeAll(new HashSet<>(emptyIfNull(appService.getAppIdsByAccountId(accountId))));
    }
  }

  // Given an app selection row in a freeze window, this returns a map of frozen environments in each application as
  // specified by it
  private Map<String, Set<String>> getAppEnvMapForAppSelection(String accountId, ApplicationFilter appSelection) {
    List<String> appIds = appSelection.getFilterType() == BlackoutWindowFilterType.ALL
        ? appService.getAppIdsByAccountId(accountId)
        : ((CustomAppFilter) appSelection).getApps();
    Map<String, Set<String>> appEnvMap = new HashMap<>();
    switch (appSelection.getEnvSelection().getFilterType()) {
      case ALL:
        if (featureFlagService.isEnabled(FeatureName.SPG_NEW_DEPLOYMENT_FREEZE_EXCLUSIONS, accountId)) {
          appEnvMap = environmentService.getAppIdEnvIdMap(new HashSet<>(appIds));
        }
        break;
      case ALL_NON_PROD:
        appEnvMap = environmentService.getAppIdEnvIdMapByType(new HashSet<>(appIds), EnvironmentType.NON_PROD);
        break;
      case ALL_PROD:
        appEnvMap = environmentService.getAppIdEnvIdMapByType(new HashSet<>(appIds), EnvironmentType.PROD);
        break;
      case CUSTOM:
        CustomEnvFilter customEnvFilter = (CustomEnvFilter) appSelection.getEnvSelection();
        appEnvMap.put(appIds.get(0), new HashSet<>(customEnvFilter.getEnvironments()));
        break;
      default:
        throw new InvalidRequestException("Invalid app selection");
    }
    if (EmptyPredicate.isEmpty(appEnvMap)) {
      log.info("No applications and environments matching the given app selection: {}, environment selection type: {}",
          appSelection.getFilterType(), appSelection.getEnvSelection().getFilterType());
    }
    return appEnvMap;
  }

  // Function to check if freeze window is turned on and is effective for the current time
  private boolean isActive(TimeRangeBasedFreezeConfig freezeConfig) {
    if (!freezeConfig.isApplicable()) {
      return false;
    }
    long currentTime = System.currentTimeMillis();
    log.info("Window id: {}, Current time: {}, from: {}, to: {}", freezeConfig.getUuid(), currentTime,
        freezeConfig.getTimeRange().getFrom(), freezeConfig.getTimeRange().getTo());
    return currentTime <= freezeConfig.getTimeRange().getTo() && currentTime >= freezeConfig.getTimeRange().getFrom();
  }

  private void validateDeploymentFreezeInput(List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfigs,
      String accountId, GovernanceConfig oldGovernanceConfig) {
    if (EmptyPredicate.isEmpty(timeRangeBasedFreezeConfigs)) {
      return;
    }

    Set<String> freezeNameSet = new HashSet<>();
    timeRangeBasedFreezeConfigs.stream().map(GovernanceFreezeConfig::getName).filter(Objects::nonNull).forEach(name -> {
      if (freezeNameSet.contains(name)) {
        throw new InvalidRequestException(format("Duplicate name %s", name), WingsException.USER);
      }
      freezeNameSet.add(name);
    });

    timeRangeBasedFreezeConfigs.stream()
        .filter(freeze -> EmptyPredicate.isNotEmpty(freeze.getAppSelections()))
        .forEach(deploymentFreeze -> {
          validateName(deploymentFreeze.getName());
          validateAppEnvFilter(deploymentFreeze);
          validateTimeRange(deploymentFreeze.getTimeRange(), deploymentFreeze.getName());
        });
  }

  private void validateUserGroups(List<String> userGroups, String accountId) {
    if (isEmpty(userGroups)) {
      throw new InvalidRequestException("User Groups cannot be empty");
    }
    for (String userGroupId : userGroups) {
      UserGroup userGroup = userGroupService.get(accountId, userGroupId);
      if (userGroup == null) {
        throw new InvalidRequestException(format("Invalid User Group Id: %s", userGroupId));
      }
    }
  }

  /**
   * We need to set uuid for individual windows
   * @param timeRangeBasedFreezeConfigs
   * @param oldGovernanceConfig
   */
  private void resetReadOnlyProperties(List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfigs, String accountId,
      GovernanceConfig oldGovernanceConfig) {
    List<TimeRangeBasedFreezeConfig> oldTimeRangeBasedFreezeConfigs =
        oldGovernanceConfig.getTimeRangeBasedFreezeConfigs();

    Map<String, TimeRangeBasedFreezeConfig> configMap = oldTimeRangeBasedFreezeConfigs.stream().collect(
        Collectors.toMap(TimeRangeBasedFreezeConfig::getName, Function.identity()));

    for (TimeRangeBasedFreezeConfig entry : timeRangeBasedFreezeConfigs) {
      if (configMap.get(entry.getName()) != null) {
        TimeRangeBasedFreezeConfig oldWindow = configMap.get(entry.getName());
        // update scenario, restore uuid and timezone
        entry.setUuid(oldWindow.getUuid());

        if (isEmpty(entry.getDescription())) {
          entry.setDescription(null);
        }

        if (isEmpty(oldWindow.getDescription())) {
          oldWindow.setDescription(null);
        }

        // if any updates to an active window
        if (!entry.equals(oldWindow)) {
          if (oldWindow.checkIfActive()) {
            throw new InvalidRequestException("Cannot update active freeze window");
          }
        } else if (entry.isApplicable() != oldWindow.isApplicable()) {
          if (entry.checkWindowExpired()) {
            throw new InvalidRequestException("Cannot update expired freeze window: " + entry.getName());
          }
        }
        if (entry.getUserGroupSelection() == null) {
          validateUserGroups(entry.getUserGroups(), accountId);
        } else if (entry.getUserGroupSelection() instanceof CustomUserGroupFilter) {
          validateUserGroups(((CustomUserGroupFilter) entry.getUserGroupSelection()).getUserGroups(), accountId);
        }
      }
    }
  }

  private void validateName(String name) {
    if (name == null) {
      throw new InvalidRequestException("Name cannot be empty for the freeze window");
    }
  }

  private void validateTimeRange(TimeRange timeRange, String name) {
    Validator.notNullCheck("Time zone cannot be empty", timeRange.getTimeZone());
    if (!ZoneId.getAvailableZoneIds().contains(timeRange.getTimeZone())) {
      throw new InvalidRequestException(
          "Please select a valid time zone. Eg. Asia/Calcutta for freeze window: " + name);
    }
    if (timeRange.getFrom() > timeRange.getTo()) {
      throw new InvalidRequestException("Window Start time is less than Window end Time");
    }
    if (timeRange.getTo() - timeRange.getFrom() < MIN_FREEZE_WINDOW_TIME) {
      throw new InvalidRequestException("Freeze window time should be at least 30 minutes");
    }
    if (timeRange.getTo() - timeRange.getFrom() > MAX_FREEZE_WINDOW_TIME) {
      throw new InvalidRequestException("Freeze window time should be less than 365 days");
    }
  }

  private void validateAppEnvFilter(TimeRangeBasedFreezeConfig deploymentFreeze) {
    validateAppEnvServiceFilterValues(deploymentFreeze.getAppSelections());
    validateAppEnvFilterTypes(deploymentFreeze.getAppSelections());
    validateAppEnvFilterOneAppWhenEnvFilterTypeIsCustom(deploymentFreeze.getAppSelections());
    validateAppEnvFilterOneAppWhenServiceFilterTypeIsCustom(deploymentFreeze.getAppSelections());
    validateAppEnvFilterTypes(deploymentFreeze.getExcludeAppSelections());
    validateAppEnvFilterOneAppWhenEnvFilterTypeIsCustom(deploymentFreeze.getExcludeAppSelections());
    validateAppEnvFilterOneAppWhenServiceFilterTypeIsCustom(deploymentFreeze.getExcludeAppSelections());
  }

  private void validateAppEnvServiceFilterValues(List<ApplicationFilter> appSelections) {
    for (ApplicationFilter selection : appSelections) {
      if (selection.getFilterType() == BlackoutWindowFilterType.CUSTOM) {
        if (((CustomAppFilter) selection).getApps().stream().anyMatch(EmptyPredicate::isEmpty)) {
          throw new InvalidRequestException("Application filter must contain valid app Ids");
        }
      }
      if (selection.getEnvSelection() != null
          && selection.getEnvSelection().getFilterType() == EnvironmentFilterType.CUSTOM) {
        if (((CustomEnvFilter) selection.getEnvSelection())
                .getEnvironments()
                .stream()
                .anyMatch(EmptyPredicate::isEmpty)) {
          throw new InvalidRequestException("Environment filter must contain valid env Ids");
        }
      }
      if (selection.getServiceSelection() != null
          && selection.getServiceSelection().getFilterType() == ServiceFilterType.CUSTOM) {
        if (selection.getServiceSelection().getServices().stream().anyMatch(EmptyPredicate::isEmpty)) {
          throw new InvalidRequestException("Service filter must contain valid service Ids");
        }
      }
    }
  }

  private void validateAppEnvFilterTypes(List<ApplicationFilter> appSelections) {
    if (appSelections.stream().anyMatch(appSelection
            -> appSelection.getFilterType() != BlackoutWindowFilterType.CUSTOM
                && appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.CUSTOM)) {
      throw new InvalidRequestException(
          "Environment filter type can be CUSTOM only when Application Filter type is CUSTOM");
    }
  }

  private void validateAppEnvFilterOneAppWhenEnvFilterTypeIsCustom(List<ApplicationFilter> appSelections) {
    if (appSelections.stream()
            .filter(selection -> selection.getFilterType() == BlackoutWindowFilterType.CUSTOM)
            .anyMatch(appSelection
                -> appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.CUSTOM
                    && ((CustomAppFilter) appSelection).getApps().size() != 1)) {
      throw new InvalidRequestException(
          "Application filter should have exactly one app when environment filter type is CUSTOM");
    }
  }

  private void validateAppEnvFilterOneAppWhenServiceFilterTypeIsCustom(List<ApplicationFilter> appSelections) {
    if (appSelections.stream()
            .filter(selection -> selection.getFilterType() == BlackoutWindowFilterType.CUSTOM)
            .anyMatch(appSelection
                -> appSelection.getServiceSelection().getFilterType() == ServiceFilterType.CUSTOM
                    && ((CustomAppFilter) appSelection).getApps().size() != 1)) {
      throw new InvalidRequestException(
          "Application filter should have exactly one app when service filter type is CUSTOM");
    }
  }

  private void auditDeploymentFreeze(String accountId, GovernanceConfig oldConfig, GovernanceConfig updatedConfig) {
    if (deploymentFreezeBeingEnabled(oldConfig, updatedConfig)) {
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, oldConfig, updatedConfig, Type.ENABLE);
    } else if (deploymentFreezeBeingDisabled(oldConfig, updatedConfig)) {
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, oldConfig, updatedConfig, Type.DISABLE);
    } else {
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, oldConfig, updatedConfig, Type.UPDATE);
    }
  }

  private boolean deploymentFreezeBeingEnabled(GovernanceConfig oldConfig, GovernanceConfig updatedConfig) {
    return updatedConfig.isDeploymentFreeze() && (oldConfig == null || !oldConfig.isDeploymentFreeze());
  }

  private boolean deploymentFreezeBeingDisabled(GovernanceConfig oldConfig, GovernanceConfig updatedConfig) {
    return !updatedConfig.isDeploymentFreeze() && (oldConfig == null || oldConfig.isDeploymentFreeze());
  }

  private void publishToSegment(String accountId, User user, EventType eventType) {
    if (null == user) {
      log.error("User is null when trying to publish to segment. Event will be skipped. Event Type: {}, accountId={}",
          eventType, accountId);
      return;
    }

    if (isEmpty(user.getUuid())) {
      log.error(
          "User id is empty or null when trying to publish to segment. Event will be skipped. Event Type: {}, accountId={}",
          eventType, accountId);
      return;
    }

    // repeating=false until repeating blackout windows are implemented
    Builder messageBuilder = TrackMessage.builder(eventType.toString())
                                 .userId(user.getUuid())
                                 .properties(new ImmutableMap.Builder<String, Object>()
                                                 .put("product", "Security")
                                                 .put("groupId", accountId)
                                                 .put("module", "Governance")
                                                 .put("repeating", false)
                                                 .build());

    segmentHelper.enqueue(messageBuilder);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting Deployment Freeze window(s)");
      Query<GovernanceConfig> query =
          wingsPersistence.createQuery(GovernanceConfig.class).filter(GovernanceConfigKeys.accountId, accountId);
      GovernanceConfig config = query.get();
      if (wingsPersistence.delete(query)) {
        auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, config);
      }
    }
  }
}
