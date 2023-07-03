/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.Collections.emptySet;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UsageRestrictionException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.EntityReference;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.User;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.restrictions.AppRestrictionsSummary;
import software.wings.beans.security.restrictions.RestrictionsSummary;
import software.wings.dl.WingsPersistence;
import software.wings.security.AppFilter;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.EnvFilter;
import software.wings.security.EnvFilter.EnvFilterBuilder;
import software.wings.security.EnvFilter.FilterType;
import software.wings.security.Filter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserRestrictionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.security.WorkflowFilter;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.RestrictionsAndAppEnvMap;
import software.wings.settings.RestrictionsAndAppEnvMap.RestrictionsAndAppEnvMapBuilder;
import software.wings.settings.SettingVariableTypes;
import software.wings.settings.UsageRestrictionsReferenceSummary;
import software.wings.settings.UsageRestrictionsReferenceSummary.IdNameReference;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 06/10/18
 */
@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
public class UsageRestrictionsServiceImpl implements UsageRestrictionsService {
  @Inject private AuthHandler authHandler;
  @Inject private UserGroupService userGroupService;
  @Inject private UserService userService;
  @Inject private AppService appService;
  @Inject private EnvironmentService environmentService;
  @Inject private SettingsService settingsService;
  @Inject private SettingServiceHelper settingServiceHelper;
  @Inject private SecretManager secretManager;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private FeatureFlagService featureFlagService;
  public static final String NON_NULL_USAGE_RESTRICTION_MSG_FMT =
      "Non null restrictions are not allowed when scoping entity to account for accountId %s";

  @Inject
  public UsageRestrictionsServiceImpl(AuthHandler authHandler, UserGroupService userGroupService, AppService appService,
      EnvironmentService environmentService, SettingsService settingsService, SecretManager secretManager,
      WingsPersistence wingsPersistence, UserService userService) {
    this.authHandler = authHandler;
    this.userGroupService = userGroupService;
    this.appService = appService;
    this.environmentService = environmentService;
    this.settingsService = settingsService;
    this.secretManager = secretManager;
    this.wingsPersistence = wingsPersistence;
    this.userService = userService;
  }

  @Override
  public boolean hasAccess(String accountId, boolean isAccountAdmin, String appIdFromRequest, String envIdFromRequest,
      boolean forUsageInNewApp, UsageRestrictions entityUsageRestrictions,
      UsageRestrictions restrictionsFromUserPermissions, Map<String, Set<String>> appEnvMapFromPermissions,
      Map<String, List<Base>> appIdEnvMap, boolean isScopedToAccount) {
    boolean hasNoRestrictions = hasNoRestrictions(entityUsageRestrictions);

    if (isNotEmpty(appIdFromRequest) && !appIdFromRequest.equals(GLOBAL_APP_ID)) {
      if (hasNoRestrictions || isScopedToAccount) {
        return false;
      }
      Map<String, Set<String>> appEnvMapFromEntityRestrictions =
          getAppEnvMap(entityUsageRestrictions.getAppEnvRestrictions(), appIdEnvMap);
      if (isNotEmpty(envIdFromRequest)) {
        // Restrict it to both app and env
        Set<String> envIds = appEnvMapFromEntityRestrictions.get(appIdFromRequest);
        if (isEmpty(envIds)) {
          return false;
        }
        return envIds.contains(envIdFromRequest);
      } else {
        // Restrict it to app
        return appEnvMapFromEntityRestrictions.containsKey(appIdFromRequest);
      }
    } else if (!GLOBAL_APP_ID.equals(appIdFromRequest) && forUsageInNewApp) {
      if (hasNoRestrictions || isScopedToAccount) {
        return false;
      }
      return hasUsageRestrictionsForNewApp(entityUsageRestrictions.getAppEnvRestrictions());
    } else {
      User user = UserThreadLocal.get();
      if (user == null) {
        return true;
      }
      if (isScopedToAccount) {
        return isAccountAdmin || hasAllEnvAccess(restrictionsFromUserPermissions);
      }
      if (hasNoRestrictions) {
        return isAccountAdmin;
      }
      Map<String, Set<String>> appEnvMapFromEntityRestrictions =
          getAppEnvMap(entityUsageRestrictions.getAppEnvRestrictions(), appIdEnvMap);
      return hasAccess(isAccountAdmin, appEnvMapFromEntityRestrictions, entityUsageRestrictions,
          appEnvMapFromPermissions, restrictionsFromUserPermissions);
    }
  }

  @Override
  public boolean hasNoRestrictions(UsageRestrictions usageRestrictions) {
    // Observed some entities having empty usage restrictions. Covering that case.
    // Could have been due to a ui bug at some point.
    return usageRestrictions == null || isEmpty(usageRestrictions.getAppEnvRestrictions());
  }

  private boolean hasAllEnvAccess(UsageRestrictions usageRestrictions) {
    return hasAllEnvAccessOfType(usageRestrictions, FilterType.PROD)
        && hasAllEnvAccessOfType(usageRestrictions, FilterType.NON_PROD);
  }

  private boolean hasAccess(boolean isAccountAdmin, Map<String, Set<String>> appEnvMapFromEntityRestrictions,
      UsageRestrictions entityUsageRestrictions, Map<String, Set<String>> appEnvMapFromPermissions,
      UsageRestrictions restrictionsFromUserPermissions) {
    if (hasAllEnvAccess(entityUsageRestrictions)) {
      return true;
    }

    if (hasNoRestrictions(entityUsageRestrictions)) {
      return isAccountAdmin;
    }

    if (isEmpty(appEnvMapFromPermissions) || restrictionsFromUserPermissions == null
        || isEmpty(restrictionsFromUserPermissions.getAppEnvRestrictions())) {
      return false;
    }

    UsageRestrictions entityUsageRestrictionsFinal = entityUsageRestrictions;
    // We want to first check if the restrictions from user permissions is not null
    if (isEmpty(appEnvMapFromEntityRestrictions)) {
      return hasAnyCommonEnv(entityUsageRestrictions, restrictionsFromUserPermissions);
    }

    return appEnvMapFromEntityRestrictions.entrySet().stream().anyMatch(
        (Entry<String, Set<String>> appEnvEntryOfEntity) -> {
          String appId = appEnvEntryOfEntity.getKey();

          if (!appEnvMapFromPermissions.containsKey(appId)) {
            return false;
          }

          Set<String> envIdsFromRestrictions = appEnvEntryOfEntity.getValue();
          if (isEmpty(envIdsFromRestrictions)) {
            return hasAnyCommonEnv(appId, entityUsageRestrictionsFinal, restrictionsFromUserPermissions);
          }

          Set<String> envIdsOfUser = appEnvMapFromPermissions.get(appId);
          if (isEmpty(envIdsOfUser)) {
            return false;
          }

          return envIdsFromRestrictions.stream().anyMatch(envIdsOfUser::contains);
        });
  }

  private boolean hasAnyCommonEnv(
      String appId, UsageRestrictions restrictionsFromEntity, UsageRestrictions restrictionsFromUserPermissions) {
    return (hasAllEnvAccessOfType(restrictionsFromEntity, appId, FilterType.PROD)
               && hasAllEnvAccessOfType(restrictionsFromUserPermissions, appId, FilterType.PROD))
        || (hasAllEnvAccessOfType(restrictionsFromEntity, appId, FilterType.NON_PROD)
            && hasAllEnvAccessOfType(restrictionsFromUserPermissions, appId, FilterType.NON_PROD));
  }

  private boolean hasAnyCommonEnv(
      UsageRestrictions restrictionsFromEntity, UsageRestrictions restrictionsFromUserPermissions) {
    return (hasAllEnvAccessOfType(restrictionsFromEntity, FilterType.PROD)
               && hasAllEnvAccessOfType(restrictionsFromUserPermissions, FilterType.PROD))
        || (hasAllEnvAccessOfType(restrictionsFromEntity, FilterType.NON_PROD)
            && hasAllEnvAccessOfType(restrictionsFromUserPermissions, FilterType.NON_PROD));
  }

  private boolean hasAllCommonEnv(
      String appId, UsageRestrictions restrictionsFromEntity, UsageRestrictions restrictionsFromUserPermissions) {
    boolean hasAllProdRestrictions = hasAllEnvAccessOfType(restrictionsFromEntity, appId, FilterType.PROD);
    boolean hasAllProdPermissions = hasAllEnvAccessOfType(restrictionsFromUserPermissions, appId, FilterType.PROD);
    boolean hasAllNonProdRestrictions = hasAllEnvAccessOfType(restrictionsFromEntity, appId, FilterType.NON_PROD);
    boolean hasAllNonProdPermissions =
        hasAllEnvAccessOfType(restrictionsFromUserPermissions, appId, FilterType.NON_PROD);

    return hasAllAccess(
        hasAllProdPermissions, hasAllProdRestrictions, hasAllNonProdPermissions, hasAllNonProdRestrictions);
  }

  private boolean hasAllCommonEnv(
      UsageRestrictions restrictionsFromEntity, UsageRestrictions restrictionsFromUserPermissions) {
    boolean hasAllProdRestrictions = hasAllEnvAccessOfType(restrictionsFromEntity, FilterType.PROD);
    boolean hasAllProdPermissions = hasAllEnvAccessOfType(restrictionsFromUserPermissions, FilterType.PROD);
    boolean hasAllNonProdRestrictions = hasAllEnvAccessOfType(restrictionsFromEntity, FilterType.NON_PROD);
    boolean hasAllNonProdPermissions = hasAllEnvAccessOfType(restrictionsFromUserPermissions, FilterType.NON_PROD);

    return hasAllAccess(
        hasAllProdPermissions, hasAllProdRestrictions, hasAllNonProdPermissions, hasAllNonProdRestrictions);
  }

  private boolean hasAllAccess(boolean hasAllProdPermissions, boolean hasAllProdRestrictions,
      boolean hasAllNonProdPermissions, boolean hasAllNonProdRestrictions) {
    boolean hasAllAccess = false;
    if (hasAllProdRestrictions) {
      if (!hasAllProdPermissions) {
        log.info("The user doesn't has all prod permissions");
        return false;
      }
      hasAllAccess = true;
    }

    if (hasAllNonProdRestrictions) {
      if (!hasAllNonProdPermissions) {
        log.info("The user doesn't has all non prod permissions");
        return false;
      }
      hasAllAccess = true;
    }

    return hasAllAccess;
  }

  @Override
  public Map<String, Set<String>> getAppEnvMapFromUserPermissions(
      String accountId, UserPermissionInfo userPermissionInfo, Action action) {
    Map<String, Set<String>> appEnvMap = new HashMap<>();

    Map<String, AppPermissionSummary> appPermissionMap = userPermissionInfo.getAppPermissionMapInternal();

    if (isEmpty(appPermissionMap)) {
      return appEnvMap;
    }

    Set<Entry<String, AppPermissionSummary>> entrySet = appPermissionMap.entrySet();

    if (isEmpty(entrySet)) {
      return appEnvMap;
    }

    entrySet.forEach(appPermission -> {
      String appId = appPermission.getKey();

      // Multimap is deliberately not used since we want to be able to insert the key with null values.
      Set<String> envSet = appEnvMap.get(appId);
      if (envSet == null) {
        envSet = new HashSet<>();
        appEnvMap.put(appId, envSet);
      }

      Set<String> envSetFinal = envSet;
      AppPermissionSummary appPermissionSummary = appPermission.getValue();

      if (appPermissionSummary == null) {
        return;
      }

      Map<Action, Set<EnvInfo>> envPermissions = appPermissionSummary.getEnvPermissions();
      if (isEmpty(envPermissions)) {
        return;
      }

      Set<EnvInfo> envInfoSet = envPermissions.get(action);

      if (isEmpty(envInfoSet)) {
        return;
      }

      envInfoSet.forEach(envInfo -> envSetFinal.add(envInfo.getEnvId()));
    });

    return appEnvMap;
  }

  @Override
  public Map<String, Set<String>> getAppSvcMapFromUserPermissions(
      String accountId, UserPermissionInfo userPermissionInfo, Action action) {
    Map<String, Set<String>> appSvcMap = new HashMap<>();

    Map<String, AppPermissionSummary> appPermissionMap = userPermissionInfo.getAppPermissionMapInternal();

    if (isEmpty(appPermissionMap)) {
      return appSvcMap;
    }

    Set<Entry<String, AppPermissionSummary>> entrySet = appPermissionMap.entrySet();

    if (isEmpty(entrySet)) {
      return appSvcMap;
    }

    entrySet.forEach(appPermission -> {
      String appId = appPermission.getKey();

      // Multimap is deliberately not used since we want to be able to insert the key with null values.
      Set<String> svcSet = appSvcMap.get(appId);
      if (svcSet == null) {
        svcSet = new HashSet<>();
        appSvcMap.put(appId, svcSet);
      }

      Set<String> svcSetFinal = svcSet;
      AppPermissionSummary appPermissionSummary = appPermission.getValue();

      if (appPermissionSummary == null) {
        return;
      }

      Map<Action, Set<String>> svcPermissions = appPermissionSummary.getServicePermissions();
      if (isEmpty(svcPermissions)) {
        return;
      }

      Set<String> svcPermissionSet = svcPermissions.get(action);

      if (isEmpty(svcPermissionSet)) {
        return;
      }

      svcSetFinal.addAll(svcPermissionSet);
    });

    return appSvcMap;
  }

  private boolean hasUsageRestrictionsForNewApp(Set<AppEnvRestriction> appEnvRestrictions) {
    if (isEmpty(appEnvRestrictions)) {
      return false;
    }

    return appEnvRestrictions.stream().anyMatch(appEnvRestriction -> {
      GenericEntityFilter appFilter = appEnvRestriction.getAppFilter();
      return GenericEntityFilter.FilterType.ALL.equals(appFilter.getFilterType());
    });
  }

  @Override
  public Map<String, Set<String>> getAppEnvMap(
      Set<AppEnvRestriction> appEnvRestrictions, Map<String, List<Base>> appIdEnvMap) {
    Map<String, Set<String>> appEnvMap = new HashMap<>();

    if (isEmpty(appEnvRestrictions)) {
      return appEnvMap;
    }

    appEnvRestrictions.forEach(appEnvRestriction -> {
      GenericEntityFilter appFilter = appEnvRestriction.getAppFilter();
      Set<String> appSet = getAppIdsByFilter(appFilter, appIdEnvMap.keySet());

      if (isEmpty(appSet)) {
        return;
      }

      EnvFilter envFilter = appEnvRestriction.getEnvFilter();
      appSet.forEach(appId -> {
        Set<String> envIdsByFilter = getEnvIdsByFilter(envFilter, appId, appIdEnvMap);
        // Multimap is deliberately not used since we want to be able to insert the key with null values.
        Set<String> valueSet = appEnvMap.computeIfAbsent(appId, k -> new HashSet<>());

        if (!isEmpty(envIdsByFilter)) {
          valueSet.addAll(envIdsByFilter);
        }
      });
    });

    return appEnvMap;
  }

  private Set<String> getAppIdsByFilter(GenericEntityFilter appFilter, Set<String> allAppIdsInAccount) {
    Set<String> appSet;
    switch (appFilter.getFilterType()) {
      case GenericEntityFilter.FilterType.ALL:
        appSet = new HashSet<>(allAppIdsInAccount);
        break;
      case GenericEntityFilter.FilterType.SELECTED:
        appSet = appFilter.getIds();
        break;
      default:
        throw new WingsException("Unsupported app filter type" + appFilter.getFilterType());
    }

    return appSet;
  }

  private Set<String> getEnvIdsByFilter(EnvFilter envFilter, String appId, Map<String, List<Base>> appIdEnvMap) {
    Set<String> envSet = new HashSet<>();
    if (envFilter == null) {
      return envSet;
    }

    Set<String> filterTypes = envFilter.getFilterTypes();

    if (isEmpty(filterTypes)) {
      return envSet;
    }

    List<Base> envList = appIdEnvMap.get(appId);
    if (isEmpty(envList)) {
      return envSet;
    }

    if (filterTypes.contains(FilterType.PROD) && filterTypes.contains(FilterType.NON_PROD)) {
      return envList.stream().map(Base::getUuid).collect(Collectors.toSet());
    }

    filterTypes.forEach(filterType -> {
      switch (filterType) {
        case FilterType.PROD:
          Set<String> prodEnvSet = envList.stream()
                                       .filter(env -> ((Environment) env).getEnvironmentType() == EnvironmentType.PROD)
                                       .map(Base::getUuid)
                                       .collect(Collectors.toSet());
          envSet.addAll(prodEnvSet);
          break;
        case FilterType.NON_PROD:
          Set<String> nonProdEnvSet =
              envList.stream()
                  .filter(env -> ((Environment) env).getEnvironmentType() == EnvironmentType.NON_PROD)
                  .map(Base::getUuid)
                  .collect(Collectors.toSet());
          envSet.addAll(nonProdEnvSet);
          break;
        case FilterType.SELECTED:
          if (envFilter.getIds() != null) {
            Set<String> envFilterIds = envFilter.getIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
            envSet.addAll(envFilterIds);
          }
          break;
        default:
          throw new WingsException("Unsupported env filter type" + envFilter.getFilterTypes());
      }
    });

    return envSet;
  }

  private boolean hasUserContext() {
    User user = UserThreadLocal.get();
    return user != null && user.getUserRequestContext() != null;
  }

  private GenericEntityFilter convertToGenericEntityFilter(String accountId, AppFilter appFilter) {
    if (appFilter == null) {
      throw new InvalidRequestException("App Filter cannot be null");
    }
    switch (appFilter.getFilterType()) {
      case AppFilter.FilterType.ALL:
        return GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build();
      case AppFilter.FilterType.SELECTED:
        return GenericEntityFilter.builder()
            .filterType(GenericEntityFilter.FilterType.SELECTED)
            .ids(appFilter.getIds())
            .build();
      case AppFilter.FilterType.EXCLUDE_SELECTED:
        Set<String> allAppIds = Sets.newHashSet(appService.getAppIdsByAccountId(accountId));
        return GenericEntityFilter.builder()
            .filterType(GenericEntityFilter.FilterType.SELECTED)
            .ids(new HashSet<>(Sets.difference(allAppIds, appFilter.getIds())))
            .build();
      default:
        throw new InvalidRequestException("Unknown app filter type: " + appFilter.getFilterType());
    }
  }

  @Override
  public UsageRestrictions getUsageRestrictionsFromUserPermissions(
      String accountId, Action action, List<UserGroup> userGroupList) {
    Set<AppEnvRestriction> appEnvRestrictions = Sets.newHashSet();

    userGroupList.forEach(userGroup -> {
      Set<AppPermission> appPermissions = userGroup.getAppPermissions();
      if (isEmpty(appPermissions)) {
        return;
      }

      appPermissions.forEach(appPermission -> {
        PermissionType permissionType = appPermission.getPermissionType();

        Set<Action> actions = appPermission.getActions();
        if (!actions.contains(action) || appPermission.getAppFilter() == null) {
          return;
        }

        GenericEntityFilter appFilter = convertToGenericEntityFilter(accountId, appPermission.getAppFilter());
        if (permissionType == PermissionType.ALL_APP_ENTITIES) {
          EnvFilter prodEntityFilter = EnvFilter.builder().filterTypes(Sets.newHashSet(FilterType.PROD)).build();
          AppEnvRestriction prodAppEnvRestriction =
              AppEnvRestriction.builder().appFilter(appFilter).envFilter(prodEntityFilter).build();
          appEnvRestrictions.add(prodAppEnvRestriction);

          EnvFilter nonProdEntityFilter = EnvFilter.builder().filterTypes(Sets.newHashSet(FilterType.NON_PROD)).build();
          AppEnvRestriction nonProdAppEnvRestriction =
              AppEnvRestriction.builder().appFilter(appFilter).envFilter(nonProdEntityFilter).build();
          appEnvRestrictions.add(nonProdAppEnvRestriction);

        } else {
          Filter entityFilter = appPermission.getEntityFilter();
          if (!(entityFilter instanceof EnvFilter)) {
            return;
          }

          if (entityFilter instanceof WorkflowFilter) {
            entityFilter = getEnvFilterFromWorkflowFilter((WorkflowFilter) entityFilter);
          }

          AppEnvRestriction appEnvRestriction =
              AppEnvRestriction.builder().appFilter(appFilter).envFilter((EnvFilter) entityFilter).build();
          appEnvRestrictions.add(appEnvRestriction);
        }
      });
    });

    if (isEmpty(appEnvRestrictions)) {
      return null;
    }

    return UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();
  }

  public Set<EnvFilter> getEnvFilterForApp(String accountId, String appId) {
    if (!hasUserContext()) {
      return null;
    }

    User user = UserThreadLocal.get();
    UserRequestContext userRequestContext = user.getUserRequestContext();

    Set<EnvFilter> envFilters = Sets.newHashSet();

    List<UserGroup> userGroupsByAccountId =
        userGroupService.listByAccountId(userRequestContext.getAccountId(), user, true);
    userGroupsByAccountId.forEach(userGroup -> {
      Set<AppPermission> appPermissions = userGroup.getAppPermissions();
      if (isEmpty(appPermissions)) {
        return;
      }

      appPermissions.forEach(appPermission -> {
        PermissionType permissionType = appPermission.getPermissionType();

        Set<Action> actions = appPermission.getActions();
        if (!actions.contains(Action.UPDATE)) {
          return;
        }

        AppFilter appFilter = appPermission.getAppFilter();
        Set<String> appIdsByFilter = authHandler.getAppIdsByFilter(accountId, appFilter);
        if (isEmpty(appIdsByFilter)) {
          return;
        }

        if (!appIdsByFilter.contains(appId)) {
          return;
        }

        Filter entityFilter;
        if (permissionType == PermissionType.ALL_APP_ENTITIES) {
          entityFilter = EnvFilter.builder().filterTypes(Sets.newHashSet(FilterType.PROD, FilterType.NON_PROD)).build();
        } else {
          entityFilter = appPermission.getEntityFilter();
          if (!(entityFilter instanceof EnvFilter)) {
            return;
          }
        }

        if (entityFilter instanceof WorkflowFilter) {
          entityFilter = getEnvFilterFromWorkflowFilter((WorkflowFilter) entityFilter);
        }

        envFilters.add((EnvFilter) entityFilter);
      });
    });

    return envFilters;
  }

  private EnvFilter getEnvFilterFromWorkflowFilter(WorkflowFilter workflowFilter) {
    EnvFilterBuilder envFilterBuilder = EnvFilter.builder();
    Set<String> envFilterTypes = Sets.newHashSet();

    if (isEmpty(workflowFilter.getFilterTypes())) {
      return envFilterBuilder.build();
    }

    workflowFilter.getFilterTypes().forEach(filterType -> {
      if (filterType.equals(WorkflowFilter.FilterType.TEMPLATES)) {
        return;
      }

      envFilterTypes.add(filterType);
      if (filterType.equals(EnvFilter.FilterType.SELECTED)) {
        envFilterBuilder.ids(workflowFilter.getIds());
      }
    });
    envFilterBuilder.filterTypes(envFilterTypes);
    return envFilterBuilder.build();
  }

  @Override
  public RestrictionsSummary listAppsWithEnvUpdatePermissions(String accountId) {
    RestrictionsAndAppEnvMap restrictionsAndAppEnvMap = getRestrictionsAndAppEnvMapFromCache(accountId, Action.UPDATE);
    UsageRestrictions usageRestrictionsFromUserPermissions = restrictionsAndAppEnvMap.getUsageRestrictions();

    Set<AppRestrictionsSummary> appRestrictionsSummarySet = Sets.newHashSet();
    if (usageRestrictionsFromUserPermissions == null) {
      return RestrictionsSummary.builder()
          .hasAllAppAccess(false)
          .hasAllNonProdEnvAccess(false)
          .hasAllProdEnvAccess(false)
          .applications(appRestrictionsSummarySet)
          .build();
    }

    PageResponse<Application> pageResponse = appService.list(PageRequestBuilder.aPageRequest()
                                                                 .addFilter("accountId", EQ, accountId)
                                                                 .addFieldsIncluded("_id", "name")
                                                                 .build(),
        false, false, null, false);

    Map<String, String> appMap =
        pageResponse.getResponse().stream().collect(Collectors.toMap(Base::getUuid, Application::getName));

    if (isEmpty(appMap)) {
      return RestrictionsSummary.builder()
          .hasAllAppAccess(true)
          .hasAllNonProdEnvAccess(true)
          .hasAllProdEnvAccess(true)
          .applications(emptySet())
          .build();
    }

    PageResponse<Environment> envPageResponse =
        environmentService.list(PageRequestBuilder.aPageRequest()
                                    .addFilter(EnvironmentKeys.accountId, EQ, accountId)
                                    .addFilter("appId", Operator.IN, appMap.keySet().toArray(new String[0]))
                                    .addFieldsIncluded("_id", "name", "environmentType")
                                    .build(),
            false, null, true);

    Map<String, String> envMap =
        envPageResponse.getResponse().stream().collect(Collectors.toMap(Base::getUuid, Environment::getName));

    Map<String, EnvironmentType> envTypeMap = envPageResponse.getResponse().stream().collect(
        Collectors.toMap(Base::getUuid, Environment::getEnvironmentType));

    Map<String, Set<String>> appEnvMapOfUser = restrictionsAndAppEnvMap.getAppEnvMap();

    boolean hasAllAppAccess = hasAllAppAccess(usageRestrictionsFromUserPermissions);
    boolean hasAllProdEnvAccessForAllApps =
        hasAllEnvAccessOfType(usageRestrictionsFromUserPermissions, FilterType.PROD);
    boolean hasAllNonProdEnvAccessForAllApps =
        hasAllEnvAccessOfType(usageRestrictionsFromUserPermissions, FilterType.NON_PROD);

    appEnvMapOfUser.forEach((key, value) -> {
      String appId = key;
      boolean hasAllProdEnvAccess = hasAllEnvAccessOfType(usageRestrictionsFromUserPermissions, appId, FilterType.PROD);
      boolean hasAllNonProdEnvAccess =
          hasAllEnvAccessOfType(usageRestrictionsFromUserPermissions, appId, FilterType.NON_PROD);
      Set<EntityReference> envSet = Sets.newHashSet();
      value.forEach(envId -> {
        if (envTypeMap.get(envId) != null) {
          envSet.add(EntityReference.builder()
                         .name(envMap.get(envId))
                         .id(envId)
                         .appId(appId)
                         .entityType(envTypeMap.get(envId).toString())
                         .build());
        } else {
          log.info("No Environment Type present for envId: {}, accountId: {}", envId, accountId);
          envSet.add(EntityReference.builder().name(envMap.get(envId)).id(envId).appId(appId).build());
        }
      });

      AppRestrictionsSummary appRestrictionsSummary = AppRestrictionsSummary.builder()
                                                          .application(EntityReference.builder()
                                                                           .id(appId)
                                                                           .name(appMap.get(appId))
                                                                           .entityType(EntityType.APPLICATION.name())
                                                                           .appId(appId)
                                                                           .build())
                                                          .environments(envSet)
                                                          .hasAllNonProdEnvAccess(hasAllNonProdEnvAccess)
                                                          .hasAllProdEnvAccess(hasAllProdEnvAccess)
                                                          .build();
      appRestrictionsSummarySet.add(appRestrictionsSummary);
    });
    return RestrictionsSummary.builder()
        .hasAllAppAccess(hasAllAppAccess)
        .hasAllNonProdEnvAccess(hasAllNonProdEnvAccessForAllApps)
        .hasAllProdEnvAccess(hasAllProdEnvAccessForAllApps)
        .applications(appRestrictionsSummarySet)
        .build();
  }

  static boolean hasAllEnvAccessOfType(UsageRestrictions usageRestrictions, String appId, String envType) {
    if (usageRestrictions == null) {
      return false;
    }

    Set<AppEnvRestriction> appEnvRestrictions = usageRestrictions.getAppEnvRestrictions();
    if (isEmpty(appEnvRestrictions)) {
      return false;
    }

    return appEnvRestrictions.stream().anyMatch(appEnvRestriction -> {
      GenericEntityFilter appFilter = appEnvRestriction.getAppFilter();
      EnvFilter envFilter = appEnvRestriction.getEnvFilter();
      if (appFilter == null || appFilter.getFilterType() == null || envFilter == null
          || isEmpty(envFilter.getFilterTypes())) {
        return false;
      } else {
        return (appFilter.getFilterType().equals(GenericEntityFilter.FilterType.ALL)
                   || (appFilter.getIds() != null && appFilter.getIds().contains(appId)))
            && envFilter.getFilterTypes().contains(envType);
      }
    });
  }

  private boolean hasAllEnvAccessOfType(UsageRestrictions usageRestrictions, String envType) {
    return hasAllEnvAccessOfTypes(usageRestrictions, Sets.newHashSet(envType));
  }

  private boolean hasAllEnvAccessOfTypes(UsageRestrictions usageRestrictions, Set<String> envTypes) {
    if (usageRestrictions == null) {
      return false;
    }

    Set<AppEnvRestriction> appEnvRestrictions = usageRestrictions.getAppEnvRestrictions();
    if (isEmpty(appEnvRestrictions)) {
      return false;
    }

    return appEnvRestrictions.stream().anyMatch(appEnvRestriction -> {
      if (!appEnvRestriction.getAppFilter().getFilterType().equals(GenericEntityFilter.FilterType.ALL)) {
        return false;
      }
      return envTypes.stream().allMatch(envType -> appEnvRestriction.getEnvFilter().getFilterTypes().contains(envType));
    });
  }

  private boolean hasAllAppAccess(UsageRestrictions usageRestrictions) {
    if (usageRestrictions == null) {
      return false;
    }

    Set<AppEnvRestriction> appEnvRestrictions = usageRestrictions.getAppEnvRestrictions();
    if (isEmpty(appEnvRestrictions)) {
      return false;
    }

    return appEnvRestrictions.stream().anyMatch(appEnvRestriction
        -> appEnvRestriction.getAppFilter().getFilterType().equals(GenericEntityFilter.FilterType.ALL));
  }

  @Override
  public UsageRestrictions getUsageRestrictionsFromJson(String usageRestrictionsString) {
    // TODO use a bean param instead. It wasn't working for some reason.
    if (EmptyPredicate.isNotEmpty(usageRestrictionsString)) {
      try {
        return JsonUtils.asObject(usageRestrictionsString, UsageRestrictions.class);
      } catch (Exception ex) {
        throw new WingsException("Invalid usage restrictions", ex);
      }
    }
    return null;
  }

  @Override
  public UsageRestrictions getDefaultUsageRestrictions(String accountId, String appId, String envId) {
    Set<AppEnvRestriction> appEnvRestrictions = Sets.newHashSet();
    if (isNotEmpty(appId)) {
      GenericEntityFilter appFilter = GenericEntityFilter.builder()
                                          .filterType(GenericEntityFilter.FilterType.SELECTED)
                                          .ids(Sets.newHashSet(appId))
                                          .build();
      if (isNotEmpty(envId)) {
        AppEnvRestriction appEnvRestriction = AppEnvRestriction.builder()
                                                  .appFilter(appFilter)
                                                  .envFilter(EnvFilter.builder()
                                                                 .filterTypes(Sets.newHashSet(FilterType.SELECTED))
                                                                 .ids(Sets.newHashSet(envId))
                                                                 .build())
                                                  .build();
        appEnvRestrictions.add(appEnvRestriction);
      } else {
        Set<EnvFilter> envFilters = getEnvFilterForApp(accountId, appId);
        if (isEmpty(envFilters)) {
          return null;
        }
        envFilters.forEach(envFilter -> {
          AppEnvRestriction appEnvRestriction =
              AppEnvRestriction.builder().appFilter(appFilter).envFilter(envFilter).build();
          appEnvRestrictions.add(appEnvRestriction);
        });
      }

      return UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();
    } else {
      return getRestrictionsAndAppEnvMapFromCache(accountId, Action.UPDATE).getUsageRestrictions();
    }
  }

  @Override
  public boolean userHasPermissionsToChangeEntity(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, UsageRestrictions restrictionsFromUserPermissions,
      boolean scopedToAccount) {
    if (!hasUserContext()) {
      return true;
    }

    Set<String> appIdsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMap = environmentService.getAppIdEnvMap(appIdsByAccountId, accountId);
    if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
      log.info("[GetAppIdEnvMap] UsageRestrictionsServiceImpl:userHasPermissionsToChangeEntity - debug log");
    }
    return userHasPermissions(accountId, permissionType, entityUsageRestrictions, restrictionsFromUserPermissions,
        appIdEnvMap, scopedToAccount);
  }

  @Override
  public boolean userHasPermissionsToChangeEntity(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, UsageRestrictions restrictionsFromUserPermissions,
      Map<String, List<Base>> appIdEnvMap, boolean scopedToAccount) {
    if (!hasUserContext()) {
      return true;
    }

    return userHasPermissions(accountId, permissionType, entityUsageRestrictions, restrictionsFromUserPermissions,
        appIdEnvMap, scopedToAccount);
  }

  @Override
  public boolean userHasPermissionsToChangeEntity(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, boolean scopedToAccount) {
    Set<String> appIdsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMap = environmentService.getAppIdEnvMap(appIdsByAccountId, accountId);
    if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
      log.info("[GetAppIdEnvMap] UsageRestrictionsServiceImpl:userHasPermissionsToChangeEntity - debug log");
    }
    return userHasPermissionsToChangeEntity(accountId, permissionType, entityUsageRestrictions,
        getRestrictionsAndAppEnvMapFromCache(accountId, Action.UPDATE).getUsageRestrictions(), appIdEnvMap,
        scopedToAccount);
  }

  @Override
  public boolean userHasPermissionsToChangeEntity(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, Map<String, List<Base>> appIdEnvMap, boolean scopedToAccount) {
    return userHasPermissionsToChangeEntity(accountId, permissionType, entityUsageRestrictions,
        getRestrictionsAndAppEnvMapFromCache(accountId, Action.UPDATE).getUsageRestrictions(), appIdEnvMap,
        scopedToAccount);
  }

  private boolean userHasPermissions(String accountId, PermissionType permissionType,
      UsageRestrictions entityUsageRestrictions, UsageRestrictions restrictionsFromUserPermissions,
      Map<String, List<Base>> appIdEnvMap, boolean scopedToAccount) {
    if (!hasUserContext()) {
      return true;
    }

    if (scopedToAccount) {
      return isAdminOrHasAllEnvAccess(accountId, permissionType, restrictionsFromUserPermissions);
    }

    // If someone is updating a secret with no usage restrictions then no permission check is required.
    // This will allow env to be deleted if a secret only refers to the env being deleted
    if (hasNoRestrictions(entityUsageRestrictions)) {
      return true;
    }

    if (hasNoRestrictions(restrictionsFromUserPermissions)) {
      log.info("The user has no restrictions {}", restrictionsFromUserPermissions);
      return false;
    }

    Map<String, Set<String>> appEnvMapFromUserPermissions =
        getRestrictionsAndAppEnvMapFromCache(accountId, Action.UPDATE).getAppEnvMap();

    Map<String, Set<String>> appEnvMapFromEntityRestrictions =
        getAppEnvMap(entityUsageRestrictions.getAppEnvRestrictions(), appIdEnvMap);

    return isUsageRestrictionsSubsetInternal(entityUsageRestrictions, appEnvMapFromEntityRestrictions,
        restrictionsFromUserPermissions, appEnvMapFromUserPermissions);
  }

  @Override
  public boolean isUsageRestrictionsSubset(
      String accountId, UsageRestrictions usageRestrictions, UsageRestrictions parentRestrictions) {
    if (usageRestrictions == null || isEmpty(usageRestrictions.getAppEnvRestrictions())) {
      return true;
    }
    if (parentRestrictions == null) {
      return false;
    }
    Set<String> appIdsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMap = environmentService.getAppIdEnvMap(appIdsByAccountId, accountId);
    if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
      log.info("[GetAppIdEnvMap] UsageRestrictionsServiceImpl:isUsageRestrictionsSubset - debug log");
    }
    Map<String, Set<String>> appEnvMap = getAppEnvMap(usageRestrictions.getAppEnvRestrictions(), appIdEnvMap);
    Map<String, Set<String>> parentAppEnvMap = getAppEnvMap(parentRestrictions.getAppEnvRestrictions(), appIdEnvMap);
    return isUsageRestrictionsSubsetInternal(usageRestrictions, appEnvMap, parentRestrictions, parentAppEnvMap);
  }

  private boolean isUsageRestrictionsSubsetInternal(UsageRestrictions usageRestrictions,
      Map<String, Set<String>> appEnvMap, UsageRestrictions parentRestrictions,
      Map<String, Set<String>> parentAppEnvMap) {
    if (isEmpty(appEnvMap)) {
      log.info("The appEnvMap {} is empty", appEnvMap);
      return hasAllCommonEnv(usageRestrictions, parentRestrictions);
    }
    UsageRestrictions entityUsageRestrictionsFinal = usageRestrictions;
    return appEnvMap.entrySet().stream().allMatch((Entry<String, Set<String>> appEnvEntryOfEntity) -> {
      String appId = appEnvEntryOfEntity.getKey();
      if (!parentAppEnvMap.containsKey(appId)) {
        log.info("The parentAppEnvMap {} doesn't contains the appId {}", parentAppEnvMap, appId);
        return false;
      }
      Set<String> envIdsFromRestrictions = appEnvEntryOfEntity.getValue();
      if (isEmpty(envIdsFromRestrictions)) {
        return hasAllCommonEnv(appId, entityUsageRestrictionsFinal, parentRestrictions);
      }
      Set<String> envIdsFromUserPermissions = parentAppEnvMap.get(appId);
      if (isEmpty(envIdsFromUserPermissions)) {
        log.info("The envIdsFromUserPermissions {} is empty", envIdsFromUserPermissions);
        return false;
      }
      return envIdsFromUserPermissions.containsAll(envIdsFromRestrictions);
    });
  }

  /**
   *
   * @param accountId
   * @param usageRestrictions
   * @param scopedToAccount
   */
  @Override
  public void validateUsageRestrictionsOnEntitySave(
      String accountId, PermissionType permissionType, UsageRestrictions usageRestrictions, boolean scopedToAccount) {
    if (!hasUserContext()) {
      return;
    }
    checkForNonNullRestrictionWhenScopedToAccount(accountId, scopedToAccount, usageRestrictions);

    checkIfValidUsageRestrictions(usageRestrictions);

    UsageRestrictions restrictionsFromUserPermissions =
        getRestrictionsAndAppEnvMapFromCache(accountId, Action.UPDATE).getUsageRestrictions();

    /**
     * Condition for which scopedToAccount will be false and also usage restrictions is null or empty
     */
    if (!scopedToAccount && hasNoRestrictions(usageRestrictions)) {
      // Read only user should not be able to create with null restrictions
      if (hasNoRestrictions(restrictionsFromUserPermissions)) {
        throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS, USER);
      }
      return;
    }

    validatedIfEntityScopingAllowedForUser(accountId, permissionType, restrictionsFromUserPermissions, scopedToAccount);

    Set<String> appIdsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMap = environmentService.getAppIdEnvMap(appIdsByAccountId, accountId);
    if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
      log.info("[GetAppIdEnvMap] UsageRestrictionsServiceImpl:validateUsageRestrictionsOnEntitySave - debug log");
    }
    boolean canUpdateEntity = userHasPermissionsToChangeEntity(
        accountId, permissionType, usageRestrictions, restrictionsFromUserPermissions, appIdEnvMap, scopedToAccount);

    if (!canUpdateEntity) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS, USER);
    }
  }

  @VisibleForTesting
  boolean isAdminOrHasAllEnvAccess(
      String accountId, PermissionType permissionType, UsageRestrictions restrictionsFromUserPermissions) {
    return userService.hasPermission(accountId, permissionType) || hasAllEnvAccess(restrictionsFromUserPermissions);
  }

  private void validatedIfEntityScopingAllowedForUser(String accountId, PermissionType permissionType,
      UsageRestrictions restrictionsFromUserPermissions, boolean scopedToAccount) {
    if (scopedToAccount) {
      boolean allowed = isAdminOrHasAllEnvAccess(accountId, permissionType, restrictionsFromUserPermissions);
      if (!allowed) {
        throw new WingsException(ErrorCode.NOT_ACCOUNT_MGR_NOR_HAS_ALL_APP_ACCESS, USER);
      }
    }
  }

  @VisibleForTesting
  void checkForNonNullRestrictionWhenScopedToAccount(
      String accountId, boolean scopedToAccount, UsageRestrictions usageRestrictions) {
    /**
     * We should not allows scopedToAccount to be true and usageRestrictions to be non-null
     */
    if (scopedToAccount && !hasNoRestrictions(usageRestrictions)) {
      String errorMsg = String.format(NON_NULL_USAGE_RESTRICTION_MSG_FMT, accountId);
      throw new UsageRestrictionException(errorMsg, USER, null);
    }
  }

  @Override
  public void validateUsageRestrictionsOnEntityUpdate(String accountId, PermissionType permissionType,
      UsageRestrictions oldUsageRestrictions, UsageRestrictions newUsageRestrictions, boolean scopedToAccount) {
    if (!hasUserContext()) {
      return;
    }

    checkIfValidUsageRestrictions(newUsageRestrictions);

    checkForNonNullRestrictionWhenScopedToAccount(accountId, scopedToAccount, newUsageRestrictions);

    UsageRestrictions restrictionsFromUserPermissions =
        getRestrictionsAndAppEnvMapFromCache(accountId, Action.UPDATE).getUsageRestrictions();

    /**
     * Condition for which scopedToAccount will be null and also no restrictions
     */
    if (!scopedToAccount && hasNoRestrictions(newUsageRestrictions)) {
      // Read only user should not be able to create with null restrictions
      if (hasNoRestrictions(restrictionsFromUserPermissions)) {
        throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS, USER);
      }
      return;
    }

    validatedIfEntityScopingAllowedForUser(accountId, permissionType, restrictionsFromUserPermissions, scopedToAccount);

    boolean canUpdateOldRestrictions = userHasPermissionsToChangeEntity(
        accountId, permissionType, oldUsageRestrictions, restrictionsFromUserPermissions, scopedToAccount);

    if (!canUpdateOldRestrictions) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS, USER);
    }

    Set<String> appIdsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, List<Base>> appIdEnvMap = environmentService.getAppIdEnvMap(appIdsByAccountId, accountId);
    if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
      log.info("[GetAppIdEnvMap] UsageRestrictionsServiceImpl:validateUsageRestrictionsOnEntityUpdate - debug log");
    }
    boolean canAddNewRestrictions = userHasPermissionsToChangeEntity(
        accountId, permissionType, newUsageRestrictions, restrictionsFromUserPermissions, appIdEnvMap, scopedToAccount);

    if (!canAddNewRestrictions) {
      throw new WingsException(ErrorCode.USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS, USER);
    }
  }

  @Override
  public void validateSetupUsagesOnUsageRestrictionsUpdate(
      String accountId, Map<String, Set<String>> setupUsages, UsageRestrictions newUsageRestrictions) {
    Set<String> appsByAccountId = appService.getAppIdsAsSetByAccountId(accountId);
    Map<String, Set<String>> newAppEnvMap = newUsageRestrictions == null
        ? new HashMap<>()
        : getAppEnvMap(newUsageRestrictions.getAppEnvRestrictions(),
            environmentService.getAppIdEnvMap(appsByAccountId, accountId));

    if (newUsageRestrictions != null) {
      if (featureFlagService.isGlobalEnabled(FeatureName.SPG_ENVIRONMENT_QUERY_LOGS)) {
        log.info(
            "[GetAppIdEnvMap] UsageRestrictionsServiceImpl:validateSetupUsagesOnUsageRestrictionsUpdate - debug log");
      }
    }

    for (Entry<String, Set<String>> setupUsage : setupUsages.entrySet()) {
      String appId = setupUsage.getKey();

      if (!newAppEnvMap.containsKey(appId)) {
        Application referredApplication = appService.get(appId);
        String appName = referredApplication == null ? "" : referredApplication.getName();
        String errorMessage =
            "Can't update usage scope, application '" + appName + "' is still referencing this secret.";
        throw new WingsException(errorMessage, USER);
      }

      Set<String> setupEnvIds = setupUsage.getValue();
      if (isEmpty(setupEnvIds)) {
        continue;
      }

      Set<String> envIdsFromUsageRestrictions = newAppEnvMap.get(appId);
      setupEnvIds.removeAll(envIdsFromUsageRestrictions);

      if (!setupEnvIds.isEmpty()) {
        Environment environment = environmentService.get(appId, setupEnvIds.iterator().next());
        String envName = environment == null ? "" : environment.getName();
        String errorMessage =
            "Can't update usage scope, environment '" + envName + "' is still referencing this secret.";
        throw new WingsException(errorMessage, USER);
      }
    }
  }

  @Override
  public RestrictionsAndAppEnvMap getRestrictionsAndAppEnvMapFromCache(String accountId, Action action) {
    RestrictionsAndAppEnvMapBuilder builder = RestrictionsAndAppEnvMap.builder();

    if (action == null) {
      return builder.build();
    }

    User user = UserThreadLocal.get();

    if (user == null) {
      return builder.build();
    }

    UserRequestContext userRequestContext = user.getUserRequestContext();

    if (userRequestContext == null) {
      return builder.build();
    }

    UserRestrictionInfo userRestrictionInfo = userRequestContext.getUserRestrictionInfo();

    if (userRestrictionInfo == null) {
      return builder.build();
    }

    switch (action) {
      case READ:
        builder.appEnvMap(userRestrictionInfo.getAppEnvMapForReadAction());
        builder.usageRestrictions(userRestrictionInfo.getUsageRestrictionsForReadAction());
        break;
      case UPDATE:
        builder.appEnvMap(userRestrictionInfo.getAppEnvMapForUpdateAction());
        builder.usageRestrictions(userRestrictionInfo.getUsageRestrictionsForUpdateAction());
        break;
      default:
        log.error("Invalid action {} for restrictions", action);
        break;
    }

    return builder.build();
  }

  @Override
  public boolean isEditable(String accountId, String entityId, String entityType) {
    if (SettingVariableTypes.SECRET_TEXT.name().equals(entityType)
        || SettingVariableTypes.CONFIG_FILE.name().equals(entityType)) {
      EncryptedData encryptedData = secretManager.getSecretById(accountId, entityId);

      if (encryptedData == null) {
        log.info("The entity {} of type {} is not editable as the encrypted data is null", entityId, entityType);
        return false;
      }
      return userHasPermissionsToChangeEntity(accountId, PermissionType.MANAGE_SECRETS,
          encryptedData.getUsageRestrictions(), encryptedData.isScopedToAccount());

    } else if (EntityType.SECRETS_MANAGER.name().equals(entityType)) {
      SecretManagerConfig secretManagerConfig = secretManager.getSecretManager(accountId, entityId);
      if (secretManagerConfig == null) {
        log.info("The secret manager config with id {} is not editable as it is null", entityId);
        return false;
      }
      return userHasPermissionsToChangeEntity(accountId, PermissionType.MANAGE_SECRET_MANAGERS,
          secretManagerConfig.getUsageRestrictions(), secretManagerConfig.isScopedToAccount());
    } else {
      SettingAttribute settingAttribute = settingsService.get(entityId);
      if (settingAttribute == null || !accountId.equals(settingAttribute.getAccountId())) {
        log.info("The setting Attribute with id {} is not editable as it is null", entityId);
        return false;
      }
      PermissionAttribute.PermissionType permissionType = settingServiceHelper.getPermissionType(settingAttribute);
      boolean isAccountAdmin = userService.hasPermission(accountId, permissionType);

      return settingServiceHelper.userHasPermissionsToChangeEntity(
          settingAttribute, accountId, settingAttribute.getUsageRestrictions(), isAccountAdmin);
    }
  }

  @Override
  public UsageRestrictionsReferenceSummary getReferenceSummaryForApp(String accountId, String appId) {
    Set<IdNameReference> settings = new LinkedHashSet<>();
    Set<IdNameReference> secrets = new LinkedHashSet<>();

    try (HIterator<SettingAttribute> iterator = getSettingAttributesWithUsageRestrictionsIterator(accountId)) {
      for (SettingAttribute settingAttribute : iterator) {
        UsageRestrictions usageRestrictions = settingServiceHelper.getUsageRestrictions(settingAttribute);
        if (usageRestrictions == null) {
          continue;
        }
        for (AppEnvRestriction appEnvRestriction : usageRestrictions.getAppEnvRestrictions()) {
          GenericEntityFilter appFilter = appEnvRestriction.getAppFilter();
          if (FilterType.SELECTED.equals(appFilter.getFilterType()) && appFilter.getIds().contains(appId)) {
            settings.add(
                IdNameReference.builder().id(settingAttribute.getUuid()).name(settingAttribute.getName()).build());
          }
        }
      }
    }

    try (HIterator<EncryptedData> iterator = getEncryptedDataWithUsageRestrictionsIterator(accountId)) {
      for (EncryptedData encryptedData : iterator) {
        UsageRestrictions usageRestrictions = encryptedData.getUsageRestrictions();
        if (usageRestrictions == null) {
          continue;
        }
        for (AppEnvRestriction appEnvRestriction : usageRestrictions.getAppEnvRestrictions()) {
          GenericEntityFilter appFilter = appEnvRestriction.getAppFilter();
          if (FilterType.SELECTED.equals(appFilter.getFilterType()) && appFilter.getIds().contains(appId)) {
            secrets.add(IdNameReference.builder().id(encryptedData.getUuid()).name(encryptedData.getName()).build());
          }
        }
      }
    }

    int numOfSettings = settings.size();
    int numOfSecrets = secrets.size();
    return UsageRestrictionsReferenceSummary.builder()
        .total(numOfSettings + numOfSecrets)
        .numOfSettings(numOfSettings)
        .numOfSecrets(numOfSecrets)
        .settings(settings)
        .secrets(secrets)
        .build();
  }

  @Override
  public UsageRestrictionsReferenceSummary getReferenceSummaryForEnv(String accountId, String envId) {
    Set<IdNameReference> settings = new LinkedHashSet<>();
    Set<IdNameReference> secrets = new LinkedHashSet<>();

    try (HIterator<SettingAttribute> iterator = getSettingAttributesWithUsageRestrictionsIterator(accountId)) {
      for (SettingAttribute settingAttribute : iterator) {
        UsageRestrictions usageRestrictions = settingServiceHelper.getUsageRestrictions(settingAttribute);
        if (usageRestrictions == null) {
          continue;
        }
        for (AppEnvRestriction appEnvRestriction : usageRestrictions.getAppEnvRestrictions()) {
          EnvFilter envFilter = appEnvRestriction.getEnvFilter();
          if (envFilter.getFilterTypes().contains(FilterType.SELECTED) && envFilter.getIds().contains(envId)) {
            settings.add(
                IdNameReference.builder().id(settingAttribute.getUuid()).name(settingAttribute.getName()).build());
          }
        }
      }
    }

    try (HIterator<EncryptedData> iterator = getEncryptedDataWithUsageRestrictionsIterator(accountId)) {
      for (EncryptedData encryptedData : iterator) {
        UsageRestrictions usageRestrictions = encryptedData.getUsageRestrictions();
        if (usageRestrictions == null) {
          continue;
        }
        for (AppEnvRestriction appEnvRestriction : usageRestrictions.getAppEnvRestrictions()) {
          EnvFilter envFilter = appEnvRestriction.getEnvFilter();
          if (envFilter.getFilterTypes().contains(FilterType.SELECTED) && envFilter.getIds().contains(envId)) {
            secrets.add(IdNameReference.builder().id(encryptedData.getUuid()).name(encryptedData.getName()).build());
          }
        }
      }
    }

    int numOfSettings = settings.size();
    int numOfSecrets = secrets.size();
    return UsageRestrictionsReferenceSummary.builder()
        .total(numOfSettings + numOfSecrets)
        .numOfSettings(numOfSettings)
        .numOfSecrets(numOfSecrets)
        .settings(settings)
        .secrets(secrets)
        .build();
  }

  @Override
  public int purgeDanglingAppEnvReferences(String accountId, UsageRestrictionsClient client) {
    int count = 0;

    Set<String> existingAppIds = appService.getAppIdsAsSetByAccountId(accountId);
    Set<String> existingEnvIds = new HashSet<>();
    for (String appId : existingAppIds) {
      existingEnvIds.addAll(environmentService.getEnvIdsByApp(appId));
    }

    if (UsageRestrictionsClient.ALL.equals(client) || UsageRestrictionsClient.CONNECTORS.equals(client)) {
      try (HIterator<SettingAttribute> iterator = getSettingAttributesWithUsageRestrictionsIterator(accountId)) {
        for (SettingAttribute settingAttribute : iterator) {
          UsageRestrictions usageRestrictions = settingAttribute.getUsageRestrictions();
          int settingAttributeCount =
              purgeDanglingAppEnvReferenceInternal(usageRestrictions, existingAppIds, existingEnvIds);
          count += settingAttributeCount;
          // Dangling reference to App/Env has been cleared in the usage restrictions. Then update with the updated
          // usage restrictions.
          if (settingAttributeCount > 0) {
            log.info("Updating usage restrictions of setting attribute with id {}", settingAttribute.getUuid());
            settingsService.updateUsageRestrictionsInternal(settingAttribute.getUuid(), usageRestrictions);
          }
        }
      }
    }

    if (UsageRestrictionsClient.ALL.equals(client) || UsageRestrictionsClient.SECRETS_MANAGEMENT.equals(client)) {
      try (HIterator<EncryptedData> iterator = getEncryptedDataWithUsageRestrictionsIterator(accountId)) {
        for (EncryptedData encryptedData : iterator) {
          if (!encryptedData.isInheritScopesFromSM() && !encryptedData.isScopedToAccount()) {
            UsageRestrictions usageRestrictions = encryptedData.getUsageRestrictions();
            int encryptedDataCount =
                purgeDanglingAppEnvReferenceInternal(usageRestrictions, existingAppIds, existingEnvIds);
            count += encryptedDataCount;
            // Dangling reference to App/Env has been cleared in the usage restrictions. Then update with the updated
            // usage restrictions.
            if (encryptedDataCount > 0) {
              log.info("Updating usage restrictions of encrypted data with id {}", encryptedData.getUuid());
              secretManager.updateUsageRestrictionsForSecretOrFile(
                  accountId, encryptedData.getUuid(), usageRestrictions, false, false);
            }
          }
        }
      }

      try (HIterator<SecretManagerConfig> iterator = getSecretManagerConfigWithUsageRestrictionsIterator(accountId)) {
        for (SecretManagerConfig secretManagerConfig : iterator) {
          UsageRestrictions usageRestrictions = secretManagerConfig.getUsageRestrictions();
          int secretManagerConfigCount =
              purgeDanglingAppEnvReferenceInternal(usageRestrictions, existingAppIds, existingEnvIds);
          count += secretManagerConfigCount;
          if (secretManagerConfigCount > 0) {
            log.info("Updating usage restrictions of secret manager with id {}", secretManagerConfig.getUuid());
            secretManager.updateUsageRestrictionsForSecretManagerConfig(
                accountId, secretManagerConfig.getUuid(), usageRestrictions);
          }
        }
      }
    }

    return count;
  }

  @Override
  public int removeAppEnvReferences(String accountId, String appId, String envId) {
    int count = 0;

    try (HIterator<SettingAttribute> iterator = getSettingAttributesWithUsageRestrictionsIterator(accountId)) {
      for (SettingAttribute settingAttribute : iterator) {
        UsageRestrictions usageRestrictions = settingAttribute.getUsageRestrictions();
        int settingAttributeCount = removeAppEnvReferencesInternal(usageRestrictions, appId, envId);
        count += settingAttributeCount;

        // Dangling reference to App/Env has been cleared in the usage restrictions. Then update with the updated usage
        // restrictions.
        if (settingAttributeCount > 0) {
          settingsService.updateUsageRestrictionsInternal(settingAttribute.getUuid(), usageRestrictions);
          log.info("Reference to application {} has been removed in setting attribute {} with id {} in account {}",
              appId, settingAttribute.getName(), settingAttribute.getUuid(), accountId);
        }
      }
    }

    try (HIterator<EncryptedData> iterator = getEncryptedDataWithUsageRestrictionsIterator(accountId)) {
      for (EncryptedData encryptedData : iterator) {
        UsageRestrictions usageRestrictions = encryptedData.getUsageRestrictions();
        int encryptedDataCount = removeAppEnvReferencesInternal(usageRestrictions, appId, envId);
        count += encryptedDataCount;

        // Dangling reference to App/Env has been cleared in the usage restrictions. Then update with the updated usage
        // restrictions.
        if (encryptedDataCount > 0) {
          secretManager.updateUsageRestrictionsForSecretOrFile(
              accountId, encryptedData.getUuid(), usageRestrictions, false, false);
          log.info("Reference to application {} has been removed in encrypted text/file {} with id {} in account {}",
              appId, encryptedData.getName(), encryptedData.getUuid(), accountId);
        }
      }
    }

    try (HIterator<SecretManagerConfig> iterator = getSecretManagerConfigWithUsageRestrictionsIterator(accountId)) {
      for (SecretManagerConfig secretManagerConfig : iterator) {
        UsageRestrictions usageRestrictions = secretManagerConfig.getUsageRestrictions();
        int secretManagerConfigCount = removeAppEnvReferencesInternal(usageRestrictions, appId, envId);
        count += secretManagerConfigCount;
        if (secretManagerConfigCount > 0) {
          secretManager.updateUsageRestrictionsForSecretManagerConfig(
              accountId, secretManagerConfig.getUuid(), usageRestrictions);
          log.info("Reference to application {} has been removed in secrets manager {} with id {} in account {}", appId,
              secretManagerConfig.getName(), secretManagerConfig.getUuid(), accountId);
        }
      }
    }

    return count;
  }

  @Override
  public UsageRestrictions getMaximumAllowedUsageRestrictionsForUser(
      String accountId, UsageRestrictions usageRestrictions) {
    if (!hasUserContext() || usageRestrictions == null || isEmpty(usageRestrictions.getAppEnvRestrictions())) {
      return usageRestrictions;
    }
    RestrictionsAndAppEnvMap restrictionsAndAppEnvMap = getRestrictionsAndAppEnvMapFromCache(accountId, Action.UPDATE);
    UsageRestrictions userUsageRestrictions = restrictionsAndAppEnvMap.getUsageRestrictions();
    if (userUsageRestrictions == null || isEmpty(usageRestrictions.getAppEnvRestrictions())) {
      return UsageRestrictions.builder().appEnvRestrictions(new HashSet<>()).build();
    }
    return getCommonRestrictions(usageRestrictions, userUsageRestrictions);
  }

  private HIterator<SettingAttribute> getSettingAttributesWithUsageRestrictionsIterator(String accountId) {
    return new HIterator<>(
        wingsPersistence.createQuery(SettingAttribute.class).filter(SettingAttributeKeys.accountId, accountId).fetch());
  }

  private HIterator<EncryptedData> getEncryptedDataWithUsageRestrictionsIterator(String accountId) {
    return new HIterator<>(wingsPersistence.createQuery(EncryptedData.class)
                               .filter(EncryptedDataKeys.accountId, accountId)
                               .field(EncryptedDataKeys.ngMetadata)
                               .equal(null)
                               .fetch());
  }

  private HIterator<SecretManagerConfig> getSecretManagerConfigWithUsageRestrictionsIterator(String accountId) {
    return new HIterator<>(wingsPersistence.createQuery(SecretManagerConfig.class)
                               .filter(SecretManagerConfigKeys.accountId, accountId)
                               .field(SecretManagerConfigKeys.ngMetadata)
                               .equal(null)
                               .fetch());
  }

  private int removeAppEnvReferencesInternal(UsageRestrictions usageRestrictions, String appId, String envId) {
    int count = 0;

    Set<AppEnvRestriction> appEnvRestrictions =
        usageRestrictions == null ? Collections.emptySet() : usageRestrictions.getAppEnvRestrictions();
    if (isEmpty(appEnvRestrictions)) {
      return count;
    }

    Set<AppEnvRestriction> filteredAppEnvRestrictions = new LinkedHashSet<>();
    for (AppEnvRestriction appEnvRestriction : appEnvRestrictions) {
      // When envId is present, don't rely on appId match to remove appEnvRestriction within the usage restrictions.
      if (envId == null) {
        GenericEntityFilter appFilter = appEnvRestriction.getAppFilter();
        Set<String> appIds = appFilter.getIds();

        if (GenericEntityFilter.FilterType.SELECTED.equals(appFilter.getFilterType()) && isNotEmpty(appIds)
            && appIds.contains(appId)) {
          appIds.remove(appId);
          count++;

          if (isEmpty(appFilter.getIds())) {
            continue;
          }
        }
      } else {
        EnvFilter envFilter = appEnvRestriction.getEnvFilter();
        Set<String> envIds = envFilter.getIds();
        Set<String> filterTypes = envFilter.getFilterTypes();

        if (filterTypes.contains(EnvFilter.FilterType.SELECTED) && isNotEmpty(envIds) && envIds.contains(envId)) {
          envIds.remove(envId);
          count++;

          if (isEmpty(envFilter.getIds())) {
            continue;
          }
        }
      }

      filteredAppEnvRestrictions.add(appEnvRestriction);
    }

    if (count > 0) {
      usageRestrictions.setAppEnvRestrictions(filteredAppEnvRestrictions);
    }

    return count;
  }

  private int purgeDanglingAppEnvReferenceInternal(
      UsageRestrictions usageRestrictions, Set<String> existingAppIds, Set<String> existingEnvIds) {
    int count = 0;

    Set<AppEnvRestriction> filteredAppEnvRestrictions = new LinkedHashSet<>();
    Set<AppEnvRestriction> appEnvRestrictions =
        usageRestrictions == null || usageRestrictions.getAppEnvRestrictions() == null
        ? new HashSet<>()
        : usageRestrictions.getAppEnvRestrictions();
    for (AppEnvRestriction appEnvRestriction : appEnvRestrictions) {
      GenericEntityFilter appFilter = appEnvRestriction.getAppFilter();
      EnvFilter envFilter = appEnvRestriction.getEnvFilter();

      if (appFilter == null || envFilter == null) {
        continue;
      }

      String appId = null;
      if (GenericEntityFilter.FilterType.SELECTED.equals(appFilter.getFilterType())) {
        if (isNotEmpty(appFilter.getIds())) {
          appId = appFilter.getIds().iterator().next();
          if (!existingAppIds.contains(appId)) {
            appFilter.getIds().remove(appId);
            count++;
          }
        }
        if (isEmpty(appFilter.getIds())) {
          count++;
          continue;
        }
      }

      if (envFilter.getFilterTypes() != null && envFilter.getFilterTypes().contains(FilterType.SELECTED)) {
        if (isNotEmpty(envFilter.getIds())) {
          String envId = envFilter.getIds().iterator().next();
          if (appId != null && !existingEnvIds.contains(envId)) {
            envFilter.getIds().remove(envId);
            count++;
          }
        }
        if (isEmpty(envFilter.getIds())) {
          count++;
          continue;
        }
      }

      filteredAppEnvRestrictions.add(appEnvRestriction);
    }

    if (count > 0) {
      usageRestrictions.setAppEnvRestrictions(filteredAppEnvRestrictions);
    }
    return count;
  }

  private void checkIfValidUsageRestrictions(UsageRestrictions usageRestrictions) {
    if (!hasNoRestrictions(usageRestrictions)) {
      usageRestrictions.getAppEnvRestrictions().forEach(appEnvRestriction -> {
        GenericEntityFilter appFilter = appEnvRestriction.getAppFilter();
        if (appFilter == null || appFilter.getFilterType() == null) {
          throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
        }

        String filterType = appFilter.getFilterType();
        if (!GenericEntityFilter.FilterType.isValidFilterType(filterType)) {
          throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
        }

        if (appFilter.getFilterType().equals(GenericEntityFilter.FilterType.SELECTED)) {
          if (isEmpty(appFilter.getIds())) {
            throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
          } else if (appFilter.getIds().stream().anyMatch(EmptyPredicate::isEmpty)) {
            throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
          }
        }

        EnvFilter envFilter = appEnvRestriction.getEnvFilter();
        if (envFilter == null) {
          throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
        }

        Set<String> envFilterTypes = envFilter.getFilterTypes();
        if (envFilter == null || isEmpty(envFilterTypes)) {
          throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
        }

        for (String envFilterType : envFilterTypes) {
          if (!EnvFilter.FilterType.isValidFilterType(envFilterType)) {
            throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
          }
        }

        if (envFilterTypes.contains(FilterType.SELECTED)) {
          if (envFilterTypes.size() != 1) {
            throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
          } else {
            if (isEmpty(envFilter.getIds())) {
              throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
            } else if (envFilter.getIds().stream().anyMatch(EmptyPredicate::isEmpty)) {
              throw new WingsException(ErrorCode.INVALID_USAGE_RESTRICTION, USER);
            }
          }
        }
      });
      // todo(abhinav): add checks here.
    }
  }

  @Override
  public UsageRestrictions getCommonRestrictions(
      UsageRestrictions usageRestrictions1, UsageRestrictions usageRestrictions2) {
    Set<AppEnvRestriction> commonRestrictions = new HashSet<>();

    for (AppEnvRestriction restriction1 : usageRestrictions1.getAppEnvRestrictions()) {
      for (AppEnvRestriction restriction2 : usageRestrictions2.getAppEnvRestrictions()) {
        AppEnvRestriction commonRestriction = getCommonAppEnvRestriction(restriction1, restriction2);
        if (commonRestriction != null) {
          commonRestrictions.add(commonRestriction);
        }
      }
    }
    return UsageRestrictions.builder().appEnvRestrictions(commonRestrictions).build();
  }

  private AppEnvRestriction getCommonAppEnvRestriction(AppEnvRestriction restriction1, AppEnvRestriction restriction2) {
    GenericEntityFilter commonAppRestriction =
        getCommonAppRestriction(restriction1.getAppFilter(), restriction2.getAppFilter());
    EnvFilter commonEnvRestriction = getCommonEnvRestriction(restriction1.getEnvFilter(), restriction2.getEnvFilter());

    if (commonAppRestriction == null || commonEnvRestriction == null) {
      return null;
    } else {
      return AppEnvRestriction.builder().appFilter(commonAppRestriction).envFilter(commonEnvRestriction).build();
    }
  }

  private GenericEntityFilter getCommonAppRestriction(GenericEntityFilter appFilter1, GenericEntityFilter appFilter2) {
    if (appFilter1.getFilterType().equals(GenericEntityFilter.FilterType.ALL)) {
      return appFilter2;
    }

    if (appFilter2.getFilterType().equals(GenericEntityFilter.FilterType.ALL)) {
      return appFilter1;
    }

    Set<String> appIds = new HashSet<>(appFilter1.getIds());
    appIds.retainAll(appFilter2.getIds());

    if (appIds.isEmpty()) {
      return null;
    }

    return GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.SELECTED).ids(appIds).build();
  }

  private EnvFilter getCommonEnvRestriction(EnvFilter envFilter1, EnvFilter envFilter2) {
    Set<String> commonFilterTypes = new HashSet<>();
    Set<String> commonIds = new HashSet<>();

    if (isNotEmpty(envFilter1.getIds())) {
      commonIds.addAll(getCommonEnvIds(envFilter1.getIds(), envFilter2));
    } else if (isNotEmpty(envFilter2.getIds())) {
      commonIds.addAll(getCommonEnvIds(envFilter2.getIds(), envFilter1));
    } else {
      commonFilterTypes.addAll(envFilter1.getFilterTypes());
      commonFilterTypes.retainAll(envFilter2.getFilterTypes());
    }

    EnvFilter commonEnvRestriction = new EnvFilter();

    if (!commonIds.isEmpty()) {
      commonFilterTypes.add(FilterType.SELECTED);
      commonEnvRestriction.setIds(commonIds);
    }

    if (commonFilterTypes.isEmpty()) {
      return null;
    }

    commonEnvRestriction.setFilterTypes(commonFilterTypes);
    return commonEnvRestriction;
  }

  private Set<String> getCommonEnvIds(Set<String> ids, EnvFilter envFilter) {
    Set<String> commonIds = new HashSet<>();
    if (isNotEmpty(envFilter.getIds())) {
      commonIds.addAll(ids);
      commonIds.retainAll(envFilter.getIds());
    } else {
      envFilter.getFilterTypes().forEach(envType -> {
        Set<String> envIds = new HashSet<>(ids);
        envIds.removeIf(envId -> !isEnvironmentOfType(envId, EnvironmentType.valueOf(envType)));
        commonIds.addAll(envIds);
      });
    }
    return commonIds;
  }

  public boolean isEnvironmentOfType(String envId, EnvironmentType type) {
    Environment env = wingsPersistence.get(Environment.class, envId);
    if (env == null) {
      return false;
    }
    EnvironmentType envType = env.getEnvironmentType();
    return envType == type || envType == EnvironmentType.ALL;
  }
}
