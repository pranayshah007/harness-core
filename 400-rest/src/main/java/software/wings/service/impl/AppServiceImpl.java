/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.PURGE_DANGLING_APP_ENV_REFS;
import static io.harness.beans.FeatureName.SPG_ALLOW_DISABLE_TRIGGERS;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.validation.PersistenceValidator.duplicateCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.Role.Builder.aRole;
import static software.wings.beans.RoleType.APPLICATION_ADMIN;
import static software.wings.beans.RoleType.NON_PROD_SUPPORT;
import static software.wings.beans.RoleType.PROD_SUPPORT;
import static software.wings.service.intfc.UsageRestrictionsService.UsageRestrictionsClient.ALL;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.data.validator.EntityNameValidator;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.limits.LimitEnforcementUtils;
import io.harness.limits.checker.StaticLimitCheckerWithDecrement;
import io.harness.queue.QueuePublisher;
import io.harness.scheduler.PersistentScheduler;
import io.harness.service.EventConfigService;
import io.harness.service.EventService;
import io.harness.validation.PersistenceValidator;

import software.wings.beans.AccountEvent;
import software.wings.beans.AccountEventType;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.Base;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Event.Type;
import software.wings.beans.InformationNotification;
import software.wings.beans.Role;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.common.NotificationMessageResolver.NotificationMessageType;
import software.wings.dl.GenericDbCache;
import software.wings.dl.WingsPersistence;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.RoleService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StatisticsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.ownership.OwnedByApplication;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.YamlGitConfigKeys;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Application Service Implementation class.
 *
 * @author Rishi
 */

@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
@ValidateOnExecution
@Singleton
@Slf4j
public class AppServiceImpl implements AppService {
  // key = appId, value = accountId
  private Cache<String, String> appIdToAccountIdCache = CacheBuilder.newBuilder().maximumSize(1000).build();

  @Inject private AlertService alertService;
  @Inject private ArtifactService artifactService;
  @Inject private AuthHandler authHandler;
  @Inject private EnvironmentService environmentService;
  @Inject private InstanceService instanceService;
  @Inject private NotificationService notificationService;
  @Inject private PipelineService pipelineService;
  @Inject private RoleService roleService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private SettingsService settingsService;
  @Inject private StatisticsService statisticsService;
  @Inject private TriggerService triggerService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WorkflowService workflowService;
  @Inject private YamlPushService yamlPushService;
  @Inject private GenericDbCache dbCache;
  @Inject private LimitCheckerFactory limitCheckerFactory;
  @Inject private YamlGitService yamlGitService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Inject private TemplateService templateService;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private ResourceLookupService resourceLookupService;
  @Inject private UserGroupService userGroupService;
  @Inject private AuthService authService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private EventConfigService eventConfigService;
  @Inject private EventService eventService;

  @Inject private QueuePublisher<PruneEvent> pruneQueue;
  @Inject @Named("ServiceJobScheduler") private PersistentScheduler serviceJobScheduler;

  private void validateAppName(Application app) {
    if (app != null) {
      if (isEmpty(app.getName().trim())) {
        throw new InvalidRequestException("App Name can not be empty", USER);
      }
      if (!EntityNameValidator.isValid(app.getName().trim())) {
        throw new InvalidRequestException("App Name can only have characters -, _, a-z, A-Z, 0-9 and space", USER);
      }
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.AppService#save(software.wings.beans.Application)
   */
  @Override
  public Application save(Application app) {
    String accountId = app.getAccountId();
    notNullCheck("accountId", accountId);

    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new io.harness.limits.Action(app.getAccountId(), ActionType.CREATE_APPLICATION));

    return LimitEnforcementUtils.withLimitCheck(checker, () -> {
      validateAppName(app);
      app.setKeywords(trimmedLowercaseSet(app.generateKeywords()));

      Application application =
          duplicateCheck(() -> wingsPersistence.saveAndGet(Application.class, app), "name", app.getName());
      createDefaultRoles(app);
      settingsService.createDefaultApplicationSettings(
          application.getUuid(), application.getAccountId(), app.isSyncFromGit());
      sendNotification(application, NotificationMessageType.ENTITY_CREATE_NOTIFICATION);

      // Save the Git Configuration for application if not null
      YamlGitConfig yamlGitConfig = app.getYamlGitConfig();
      if (yamlGitConfig != null && hasGitSyncPermission(accountId)) {
        setAppYamlGitConfigDefaults(application.getAccountId(), application.getUuid(), yamlGitConfig);

        // We are disabling git fullsync when the app is created on git side. The reason we have to to do this is that
        // the fullsync for an app deletes the original app folder and creates new one. If the app is created on git
        // side, then we dont need fullsync again.
        yamlGitService.save(yamlGitConfig, !app.isSyncFromGit());
      }

      yamlPushService.pushYamlChangeSet(app.getAccountId(), null, app, Type.CREATE, app.isSyncFromGit(), false);
      if (!app.isSample()) {
        eventPublishHelper.publishAccountEvent(
            accountId, AccountEvent.builder().accountEventType(AccountEventType.APP_CREATED).build(), true, true);
      }
      return get(application.getUuid());
    });
  }

  private void sendNotification(Application application, NotificationMessageType entityCreateNotification) {
    notificationService.sendNotificationAsync(InformationNotification.builder()
                                                  .appId(application.getUuid())
                                                  .accountId(application.getAccountId())
                                                  .notificationTemplateId(entityCreateNotification.name())
                                                  .notificationTemplateVariables(ImmutableMap.of("ENTITY_TYPE",
                                                      "Application", "ENTITY_NAME", application.getName()))
                                                  .build());
  }

  List<Role> createDefaultRoles(Application app) {
    return Lists.newArrayList(roleService.save(aRole()
                                                   .withAppId(GLOBAL_APP_ID)
                                                   .withAccountId(app.getAccountId())
                                                   .withName(APPLICATION_ADMIN.getDisplayName())
                                                   .withRoleType(APPLICATION_ADMIN)
                                                   .withAllApps(false)
                                                   .withAppId(app.getUuid())
                                                   .withAppName(app.getName())
                                                   .build()),
        roleService.save(aRole()
                             .withAppId(GLOBAL_APP_ID)
                             .withAccountId(app.getAccountId())
                             .withName(PROD_SUPPORT.getDisplayName())
                             .withRoleType(PROD_SUPPORT)
                             .withAllApps(false)
                             .withAppId(app.getUuid())
                             .withAppName(app.getName())
                             .build()),
        roleService.save(aRole()
                             .withAppId(GLOBAL_APP_ID)
                             .withAccountId(app.getAccountId())
                             .withName(NON_PROD_SUPPORT.getDisplayName())
                             .withRoleType(NON_PROD_SUPPORT)
                             .withAllApps(false)
                             .withAppId(app.getUuid())
                             .withAppName(app.getName())
                             .build()));
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.AppService#list(software.wings.dl.PageRequest)
   */
  @Override
  public PageResponse<Application> list(
      PageRequest<Application> req, boolean details, boolean withTags, String tagFilter, boolean hitSecondary) {
    PageResponse<Application> response =
        resourceLookupService.listWithTagFilters(req, tagFilter, EntityType.APPLICATION, withTags, hitSecondary);

    List<Application> applicationList = response.getResponse();
    if (isEmpty(applicationList)) {
      return response;
    }
    List<String> appIdList = applicationList.stream().map(Base::getUuid).collect(toList());

    PermissionAttribute svcPermissionAttribute = new PermissionAttribute(PermissionType.SERVICE, Action.READ);
    authHandler.setEntityIdFilterIfUserAction(asList(svcPermissionAttribute), appIdList);

    PermissionAttribute envPermissionAttribute = new PermissionAttribute(PermissionType.ENV, Action.READ);
    authHandler.setEntityIdFilterIfUserAction(asList(envPermissionAttribute), appIdList);

    String accountId;
    String[] appIdArray = appIdList.toArray(new String[0]);
    if (isNotEmpty(applicationList)) {
      accountId = applicationList.get(0).getAccountId();

      List<YamlGitConfig> yamlGitConfigList = wingsPersistence.createQuery(YamlGitConfig.class)
                                                  .filter(YamlGitConfigKeys.accountId, accountId)
                                                  .field(YamlGitConfigKeys.entityId)
                                                  .in(appIdList)
                                                  .filter(YamlGitConfigKeys.entityType, EntityType.APPLICATION)
                                                  .asList();
      Map<String, YamlGitConfig> yamlGitConfigMap =
          yamlGitConfigList.stream().collect(Collectors.toMap(YamlGitConfig::getEntityId, identity()));

      Map<String, List<Environment>> appIdEnvMap;
      Map<String, List<Service>> appIdServiceMap;

      if (details) {
        PageRequest<Environment> envPageRequest =
            PageRequestBuilder.aPageRequest().addFilter("appId", Operator.IN, appIdArray).build();
        List<Environment> envList = wingsPersistence.getAllEntities(
            envPageRequest, () -> environmentService.list(envPageRequest, false, null, false));
        appIdEnvMap = envList.stream().collect(groupingBy(Environment::getAppId));

        PageRequest<Service> servicePageRequest =
            PageRequestBuilder.aPageRequest().addFilter("appId", Operator.IN, appIdArray).build();
        List<Service> serviceList = wingsPersistence.getAllEntities(
            servicePageRequest, () -> serviceResourceService.list(servicePageRequest, false, false, false, null));
        appIdServiceMap = serviceList.stream().collect(groupingBy(Service::getAppId));

        applicationList.forEach(app -> {
          app.setYamlGitConfig(yamlGitConfigMap.get(app.getUuid()));
          app.setEnvironments(appIdEnvMap.getOrDefault(app.getUuid(), emptyList()));
          app.setServices(appIdServiceMap.getOrDefault(app.getUuid(), emptyList()));
        });

      } else {
        applicationList.forEach(app -> { app.setYamlGitConfig(yamlGitConfigMap.get(app.getUuid())); });
      }
    }

    return response;
  }

  @Override
  public PageResponse<Application> list(PageRequest<Application> req) {
    return list(req, false, false, null, false);
  }

  @Override
  public boolean exist(String appId) {
    return wingsPersistence.createQuery(Application.class, excludeAuthority).filter(ID_KEY, appId).getKey() != null;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.AppService#get(java.lang.String)
   */
  @Override
  public Application get(String uuid) {
    Application application = wingsPersistence.get(Application.class, uuid);
    if (application == null) {
      throw new WingsException(INVALID_ARGUMENT).addParam("args", "Application " + uuid + " doesn't exist");
    }

    application.setYamlGitConfig(
        yamlGitService.get(application.getAccountId(), application.getUuid(), EntityType.APPLICATION));

    return application;
  }

  @Override
  public Application getApplicationWithDefaults(String uuid) {
    Application application = get(uuid);
    application.setDefaults(settingsService.listAppDefaults(application.getAccountId(), uuid));

    return application;
  }

  @Override
  public Application getAppByName(String accountId, String appName) {
    return wingsPersistence.createQuery(Application.class)
        .filter(ApplicationKeys.accountId, accountId)
        .filter(ApplicationKeys.name, appName)
        .get();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.AppService#update(software.wings.beans.Application)
   */
  @Override
  public Application update(Application app) {
    validateAppName(app);
    Application savedApp = get(app.getUuid());
    Query<Application> query = wingsPersistence.createQuery(Application.class).filter(ID_KEY, app.getUuid());
    Set<String> keywords = trimmedLowercaseSet(app.generateKeywords());

    UpdateOperations<Application> operations =
        wingsPersistence.createUpdateOperations(Application.class).set("name", app.getName()).set("keywords", keywords);

    if (featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, savedApp.getAccountId())
        && app.getIsManualTriggerAuthorized() != null) {
      operations.set(ApplicationKeys.isManualTriggerAuthorized, app.getIsManualTriggerAuthorized());
    }

    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, savedApp.getAccountId())
        && app.getAreWebHookSecretsMandated() != null) {
      operations.set(ApplicationKeys.areWebHookSecretsMandated, app.getAreWebHookSecretsMandated());
    }

    if (featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, savedApp.getAccountId())
        && app.getDisableTriggers() != null) {
      operations.set(ApplicationKeys.disableTriggers, app.getDisableTriggers());
    }

    setUnset(operations, "description", app.getDescription());

    PersistenceValidator.duplicateCheck(
        () -> wingsPersistence.update(query, operations), ApplicationKeys.name, app.getName());

    dbCache.invalidate(Application.class, app.getUuid());

    updateAppYamlGitConfig(savedApp, app, !app.isSyncFromGit());

    Application updatedApp = get(app.getUuid());

    boolean isRename = !savedApp.getName().equals(app.getName());
    yamlPushService.pushYamlChangeSet(
        app.getAccountId(), savedApp, updatedApp, Type.UPDATE, app.isSyncFromGit(), isRename);

    return updatedApp;
  }

  @Override
  public void delete(String appId, boolean syncFromGit) {
    Application application = wingsPersistence.get(Application.class, appId);
    if (application == null) {
      return;
    }

    ensureSafeToDelete(appId, application.getName());
    // added as response of https://harness.atlassian.net/browse/CDS-35910
    log.info("Deleting app {} by user {}", appId, UserThreadLocal.get());
    String accountId = this.getAccountIdByAppId(appId);
    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new io.harness.limits.Action(accountId, ActionType.CREATE_APPLICATION));

    LimitEnforcementUtils.withCounterDecrement(checker, () -> {
      dbCache.invalidate(Application.class, appId);

      yamlPushService.pushYamlChangeSet(application.getAccountId(), application, null, Type.DELETE, syncFromGit, false);

      pruneQueue.send(new PruneEvent(Application.class, appId, appId));

      // Do not add too much between these too calls (on top and bottom). We need to persist the job
      // before we delete the object to avoid leaving the objects unpruned in case of crash. Waiting
      // too much though will result in start the job before the object is deleted, this possibility is
      // handled, but this is still not good.

      // HAR-6245: Need to first remove all the references to this app ID in existing usage restrictions.
      usageRestrictionsService.removeAppEnvReferences(application.getAccountId(), appId, null);

      // Now we are ready to delete the object.
      if (wingsPersistence.delete(Application.class, appId)) {
        sendNotification(application, NotificationMessageType.ENTITY_DELETE_NOTIFICATION);
      } else {
        throw new InvalidRequestException(
            String.format("Application %s does not exist or might already be deleted.", application.getName()));
      }

      // Note that if we failed to delete the object we left without the yaml. Likely the users
      // will not reconsider and start using the object as they never intended to delete it, but
      // probably they will retry. This is why there is no reason for us to regenerate it at this
      // point. We should have the necessary APIs elsewhere, if we find the users want it.
    });
  }

  private void ensureSafeToDelete(String appId, String appName) {
    List<String> runningExecutionNames = workflowExecutionService.runningExecutionsForApplication(appId);
    if (isNotEmpty(runningExecutionNames)) {
      throw new InvalidRequestException(
          format("Application:[%s] couldn't be deleted. [%d] Running executions present: [%s]", appName,
              runningExecutionNames.size(), String.join(", ", runningExecutionNames)),
          USER);
    }
  }

  @Override
  public void delete(String appId, String entityId) {
    delete(entityId, false);
  }

  @Override
  public void delete(String appId) {
    delete(appId, false);
    String accountIdByAppId = getAccountIdByAppId(appId);
    if (featureFlagService.isEnabled(PURGE_DANGLING_APP_ENV_REFS, accountIdByAppId)) {
      usageRestrictionsService.purgeDanglingAppEnvReferences(accountIdByAppId, ALL);
    }
  }

  @Override
  public void pruneDescendingEntities(@NotEmpty String appId) {
    List<OwnedByApplication> services =
        ServiceClassLocator.descendingServices(this, AppServiceImpl.class, OwnedByApplication.class);
    PruneEntityListener.pruneDescendingEntities(services, descending -> descending.pruneByApplication(appId));
  }

  @Override
  public List<Application> getAppsByAccountId(String accountId) {
    return wingsPersistence.createQuery(Application.class).filter(ApplicationKeys.accountId, accountId).asList();
  }

  @Override
  public List<String> getAppIdsByAccountId(String accountId) {
    return wingsPersistence.createQuery(Application.class)
        .filter(ApplicationKeys.accountId, accountId)
        .asKeyList()
        .stream()
        .map(applicationKey -> applicationKey.getId().toString())
        .collect(toList());
  }

  @Override
  public Set<String> getAppIdsAsSetByAccountId(String accountId) {
    return wingsPersistence.createQuery(Application.class)
        .filter(ApplicationKeys.accountId, accountId)
        .asKeyList()
        .stream()
        .map(applicationKey -> applicationKey.getId().toString())
        .collect(Collectors.toSet());
  }

  @Override
  public List<String> getAppNamesByAccountId(String accountId) {
    return wingsPersistence.createQuery(Application.class)
        .project(ApplicationKeys.name, true)
        .filter(ApplicationKeys.accountId, accountId)
        .asList()
        .stream()
        .map(Application::getName)
        .collect(toList());
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.createQuery(Application.class)
        .filter(Application.ACCOUNT_ID_KEY2, accountId)
        .asKeyList()
        .forEach(key -> delete(key.getId().toString()));
  }

  @Override
  public Application get(String appId, boolean details) {
    Application application = get(appId);

    if (details) {
      List<String> appIdAsList = asList(appId);
      PermissionAttribute svcPermissionAttribute = new PermissionAttribute(PermissionType.SERVICE, Action.READ);
      authHandler.setEntityIdFilterIfUserAction(asList(svcPermissionAttribute), appIdAsList);

      PermissionAttribute envPermissionAttribute = new PermissionAttribute(PermissionType.ENV, Action.READ);
      authHandler.setEntityIdFilterIfUserAction(asList(envPermissionAttribute), appIdAsList);

      application.setEnvironments(environmentService.getEnvByApp(application.getUuid()));
      application.setServices(serviceResourceService.findServicesByApp(application.getUuid()));
    }
    return application;
  }

  @Override
  public String getAccountIdByAppId(String appId) {
    if (isEmpty(appId)) {
      return null;
    }

    String accountId = appIdToAccountIdCache.getIfPresent(appId);
    if (null != accountId) {
      return accountId;
    }

    Application app = getNullable(appId);
    if (app == null) {
      return null;
    }
    accountId = app.getAccountId();
    appIdToAccountIdCache.put(appId, accountId);
    return accountId;
  }
  @Override
  public Application getNullable(String appId) {
    Application application = wingsPersistence.get(Application.class, appId);
    if (application == null) {
      return null;
    }

    application.setYamlGitConfig(
        yamlGitService.get(application.getAccountId(), application.getUuid(), EntityType.APPLICATION));

    return application;
  }

  private void updateAppYamlGitConfig(Application savedApp, Application app, boolean performFullSync) {
    if (!hasGitSyncPermission(savedApp.getAccountId())) {
      return;
    }

    YamlGitConfig savedYamlGitConfig = savedApp.getYamlGitConfig();

    YamlGitConfig yamlGitConfig = app.getYamlGitConfig();

    if (savedYamlGitConfig != null) {
      if (yamlGitConfig != null) {
        // Forcing the yamlGitConfig to have correct values set.
        setAppYamlGitConfigDefaults(savedApp.getAccountId(), savedApp.getUuid(), yamlGitConfig);
        // Its an update operation, but we don't want it to do full sync. That's we are directly calling the save with
        // fullsync disabled
        yamlGitService.save(yamlGitConfig, performFullSync);
      } else {
        yamlGitService.delete(savedApp.getAccountId(), savedApp.getUuid(), EntityType.APPLICATION);
      }
    } else {
      if (yamlGitConfig != null) {
        // Forcing the yamlGitConfig to have correct values set.
        setAppYamlGitConfigDefaults(savedApp.getAccountId(), savedApp.getUuid(), yamlGitConfig);
        yamlGitService.save(yamlGitConfig, performFullSync);
      }
    }
  }

  private boolean hasGitSyncPermission(String accountId) {
    User user = UserThreadLocal.get();
    if (null == user) {
      return true;
    }

    final UserPermissionInfo userPermissionInfo = authService.getUserPermissionInfo(accountId, user, false);
    return Optional.ofNullable(userPermissionInfo)
        .map(UserPermissionInfo::getAccountPermissionSummary)
        .map(AccountPermissionSummary::getPermissions)
        .map(permission -> permission.contains(PermissionType.MANAGE_CONFIG_AS_CODE))
        .orElse(false);
  }

  private void setAppYamlGitConfigDefaults(String accountId, String appId, YamlGitConfig yamlGitConfig) {
    yamlGitConfig.setAccountId(accountId);
    yamlGitConfig.setEntityId(appId);
    yamlGitConfig.setAppId(appId);
    yamlGitConfig.setEntityType(EntityType.APPLICATION);
  }

  @Override
  public List<Application> getAppsByIds(Set<String> appIds) {
    return wingsPersistence.createQuery(Application.class).field(ApplicationKeys.appId).hasAnyOf(appIds).asList();
  }

  @Override
  public Boolean getDisableTriggersByAppId(String appId) {
    return get(appId).getDisableTriggers();
  }
}
