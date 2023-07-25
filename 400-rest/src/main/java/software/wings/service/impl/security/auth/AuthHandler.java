/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;
import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.noop;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.security.UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.DEFAULT_PROD_SUPPORT_USER_GROUP_NAME;
import static software.wings.security.EnvFilter.FilterType.NON_PROD;
import static software.wings.security.EnvFilter.FilterType.PROD;
import static software.wings.security.GenericEntityFilter.FilterType.SELECTED;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.ALL_APP_ENTITIES;
import static software.wings.security.PermissionAttribute.PermissionType.APP_TEMPLATE;
import static software.wings.security.PermissionAttribute.PermissionType.AUDIT_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CE_ADMIN;
import static software.wings.security.PermissionAttribute.PermissionType.CE_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CREATE_CUSTOM_DASHBOARDS;
import static software.wings.security.PermissionAttribute.PermissionType.DEPLOYMENT;
import static software.wings.security.PermissionAttribute.PermissionType.ENV;
import static software.wings.security.PermissionAttribute.PermissionType.HIDE_NEXTGEN_BUTTON;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ACCOUNT_DEFAULTS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ALERT_NOTIFICATION_RULES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_API_KEYS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATION_STACKS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_AUTHENTICATION_SETTINGS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CLOUD_PROVIDERS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONFIG_AS_CODE;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONNECTORS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CUSTOM_DASHBOARDS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATE_PROFILES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DEPLOYMENT_FREEZES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_IP_WHITELIST;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_PIPELINE_GOVERNANCE_STANDARDS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_RESTRICTED_ACCESS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRETS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRET_MANAGERS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SSH_AND_WINRM;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_TAGS;
import static software.wings.security.PermissionAttribute.PermissionType.PIPELINE;
import static software.wings.security.PermissionAttribute.PermissionType.PROVISIONER;
import static software.wings.security.PermissionAttribute.PermissionType.SERVICE;
import static software.wings.security.PermissionAttribute.PermissionType.TEMPLATE_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;
import static software.wings.security.PermissionAttribute.PermissionType.WORKFLOW;
import static software.wings.security.UserRequestContext.EntityInfo;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Account;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.Base;
import software.wings.beans.Environment;
import software.wings.beans.HttpMethod;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupBuilder;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.dl.WingsPersistence;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.AccountPermissionSummary.AccountPermissionSummaryBuilder;
import software.wings.security.AppFilter;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.AppPermissionSummaryForUI;
import software.wings.security.AppPermissionSummaryForUI.AppPermissionSummaryForUIBuilder;
import software.wings.security.EnvFilter;
import software.wings.security.ExecutableElementsFilter;
import software.wings.security.Filter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.GenericEntityFilter.FilterType;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserPermissionInfo.UserPermissionInfoBuilder;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.security.WorkflowFilter;
import software.wings.service.impl.UserGroupServiceImpl;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessApiKeyService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.sm.StateType;
import software.wings.sm.states.EnvState.EnvStateKeys;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author rktummala on 3/7/18
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
@Singleton
@Slf4j
public class AuthHandler {
  /**
   * The constant DEFAULT_PROD_SUPPORT_USER_GROUP_DESCRIPTION.
   */
  private static final String DEFAULT_PROD_SUPPORT_USER_GROUP_DESCRIPTION =
      "Production Support members have access to override configuration, "
      + "setup infrastructure and setup/execute deployment workflows within PROD environments";
  /**
   * The constant DEFAULT_NON_PROD_SUPPORT_USER_GROUP_DESCRIPTION.
   */
  private static final String DEFAULT_NON_PROD_SUPPORT_USER_GROUP_DESCRIPTION =
      "Non-production Support members have access to override configuration, "
      + "setup infrastructure and setup/execute deployment workflows within NON_PROD environments";

  private static final String USER_NOT_AUTHORIZED = "User not authorized";

  @Inject private PipelineService pipelineService;
  @Inject private AppService appService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private EnvironmentService environmentService;
  @Inject private WorkflowService workflowService;
  @Inject private UserGroupService userGroupService;
  @Inject private AuthService authService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private DashboardAuthHandler dashboardAuthHandler;
  @Inject private ApiKeyService apiKeyService;
  @Inject private HarnessApiKeyService harnessApiKeyService;
  @Inject private WorkflowServiceHelper workflowServiceHelper;
  @Inject private TemplateService templateService;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private FeatureFlagService featureFlagService;

  public UserPermissionInfo evaluateUserPermissionInfo(String accountId, List<UserGroup> userGroups, User user) {
    double accountPermissionsTime, getAppIdsTime, collectAppIdsTime, fetchRequiredEntitiesTime,
        populateAppPermissionsTime, dashboardAccessPermissionsTime;
    long startTime = System.currentTimeMillis();

    UserPermissionInfoBuilder userPermissionInfoBuilder = UserPermissionInfo.builder().accountId(accountId);

    Set<PermissionType> accountPermissionSet = new HashSet<>();
    AccountPermissionSummaryBuilder accountPermissionSummaryBuilder =
        AccountPermissionSummary.builder().permissions(accountPermissionSet);

    populateRequiredAccountPermissions(userGroups, accountPermissionSet);
    accountPermissionsTime = System.currentTimeMillis() - startTime;
    startTime = System.currentTimeMillis();

    // Get all app ids
    HashSet<String> allAppIds = new HashSet<>(appService.getAppIdsByAccountId(accountId));
    getAppIdsTime = System.currentTimeMillis() - startTime;
    startTime = System.currentTimeMillis();

    // Cache all the entities by app id first
    Map<PermissionType, Set<String>> permissionTypeAppIdSetMap = collectRequiredAppIds(userGroups, allAppIds);
    collectAppIdsTime = System.currentTimeMillis() - startTime;
    startTime = System.currentTimeMillis();

    // Fetch all entities by appIds
    Map<PermissionType, Map<String, List<Base>>> permissionTypeAppIdEntityMap =
        fetchRequiredEntities(accountId, permissionTypeAppIdSetMap);

    fetchRequiredEntitiesTime = System.currentTimeMillis() - startTime;
    startTime = System.currentTimeMillis();

    // Filter and assign permissions
    Map<String, AppPermissionSummary> appPermissionMap =
        populateAppPermissions(userGroups, permissionTypeAppIdEntityMap, allAppIds);

    userPermissionInfoBuilder.appPermissionMapInternal(appPermissionMap)
        .accountPermissionSummary(accountPermissionSummaryBuilder.build());

    userPermissionInfoBuilder.hasAllAppAccess(allAppIds.size() <= appPermissionMap.keySet().size());

    UserPermissionInfo userPermissionInfo = userPermissionInfoBuilder.build();
    setAppPermissionMap(userPermissionInfo);

    populateAppPermissionsTime = System.currentTimeMillis() - startTime;
    startTime = System.currentTimeMillis();

    Map<String, Set<io.harness.dashboard.Action>> dashboardPermissions =
        dashboardAuthHandler.getDashboardAccessPermissions(user, accountId, userPermissionInfo, userGroups);
    userPermissionInfo.setDashboardPermissions(dashboardPermissions);

    dashboardAccessPermissionsTime = System.currentTimeMillis() - startTime;
    log.info(
        "evaluateUserPermissionInfo benchmarking - accountPermissionsTime:{}, getAppIdsTime:{}, collectAppIdsTime:{}, fetchRequiredEntitiesTime:{}, populateAppPermissionsTime:{}, dashboardAccessPermissionsTime:{}",
        accountPermissionsTime, getAppIdsTime, collectAppIdsTime, fetchRequiredEntitiesTime, populateAppPermissionsTime,
        dashboardAccessPermissionsTime);
    return userPermissionInfo;
  }

  private Map<String, AppPermissionSummary> populateAppPermissions(List<UserGroup> userGroups,
      Map<PermissionType, Map<String, List<Base>>> permissionTypeAppIdEntityMap, HashSet<String> allAppIds) {
    Map<String, AppPermissionSummary> appPermissionMap = new HashMap<>();
    Multimap<String, Action> envActionMapForPipeline = HashMultimap.create();
    Multimap<String, Action> envActionMapForDeployment = HashMultimap.create();

    userGroups.forEach(userGroup -> {
      Set<AppPermission> appPermissions = userGroup.getAppPermissions();
      if (isEmpty(appPermissions)) {
        return;
      }

      appPermissions.forEach(appPermission -> {
        if (isEmpty(appPermission.getActions())) {
          log.error("Actions empty for apps: {}", appPermission.getAppFilter());
          return;
        }

        Set<String> appIds = getAppIdsByFilter(allAppIds, appPermission.getAppFilter());
        PermissionType permissionType = appPermission.getPermissionType();

        if (permissionType == ALL_APP_ENTITIES) {
          asList(SERVICE, PROVISIONER, ENV, WORKFLOW, DEPLOYMENT, APP_TEMPLATE).forEach(permissionType1 -> {
            // ignoring entity filter in case of ALL_APP_ENTITIES
            attachPermission(appPermissionMap, permissionTypeAppIdEntityMap, appIds, permissionType1, null,
                appPermission.getActions());
          });

          attachPipelinePermission(envActionMapForPipeline, appPermissionMap, permissionTypeAppIdEntityMap, appIds,
              PIPELINE, null, appPermission.getActions());

          attachPipelinePermission(envActionMapForDeployment, appPermissionMap, permissionTypeAppIdEntityMap, appIds,
              DEPLOYMENT, null, appPermission.getActions());

        } else {
          if (permissionType == PIPELINE) {
            attachPipelinePermission(envActionMapForPipeline, appPermissionMap, permissionTypeAppIdEntityMap, appIds,
                permissionType, appPermission.getEntityFilter(), appPermission.getActions());
          } else {
            attachPermission(appPermissionMap, permissionTypeAppIdEntityMap, appIds, permissionType,
                appPermission.getEntityFilter(), appPermission.getActions());

            if (permissionType == DEPLOYMENT) {
              attachPipelinePermission(envActionMapForDeployment, appPermissionMap, permissionTypeAppIdEntityMap,
                  appIds, permissionType, appPermission.getEntityFilter(), appPermission.getActions());
            }
          }
        }
      });
    });

    return appPermissionMap;
  }

  private Map<String, Set<Action>> setActionsForEntity(
      Map<String, Set<Action>> permissionMap, String entityId, Collection<Action> actionCollection) {
    if (permissionMap == null) {
      permissionMap = new HashMap<>();
    }

    Set<Action> actions = permissionMap.get(entityId);

    if (isEmpty(actions)) {
      actions = new HashSet<>();
      permissionMap.put(entityId, actions);
    }

    actions.addAll(actionCollection);
    return permissionMap;
  }

  private Map<Action, Set<String>> buildActionEntityMap(
      Map<Action, Set<String>> permissionMap, Set<String> entityIdSet, Set<Action> actionSet) {
    if (permissionMap == null) {
      permissionMap = new HashMap<>();
    }

    Map<Action, Set<String>> finalPermissionMap = permissionMap;
    actionSet.forEach(action -> {
      Set<String> existingEntityIdSet = finalPermissionMap.get(action);
      if (isEmpty(existingEntityIdSet)) {
        existingEntityIdSet = new HashSet<>();
        finalPermissionMap.put(action, existingEntityIdSet);
      }
      existingEntityIdSet.addAll(entityIdSet);
    });
    return permissionMap;
  }

  private Map<Action, Set<String>> buildActionPipelineMap(
      Map<Action, Set<String>> permissionMap, Multimap<String, Action> pipelineIdActionMap) {
    if (permissionMap == null) {
      permissionMap = new HashMap<>();
    }

    Map<Action, Set<String>> finalPermissionMap = permissionMap;
    pipelineIdActionMap.forEach((pipelineId, action) -> {
      Set<String> existingPipelineIdSet = finalPermissionMap.get(action);
      if (isEmpty(existingPipelineIdSet)) {
        existingPipelineIdSet = new HashSet<>();
        finalPermissionMap.put(action, existingPipelineIdSet);
      }
      existingPipelineIdSet.add(pipelineId);
    });
    return permissionMap;
  }

  private Map<Action, Set<EnvInfo>> buildActionEnvMap(
      Map<Action, Set<EnvInfo>> permissionMap, Set<EnvInfo> envInfoSet, Set<Action> actionSet) {
    if (permissionMap == null) {
      permissionMap = new HashMap<>();
    }

    Map<Action, Set<EnvInfo>> finalPermissionMap = permissionMap;
    actionSet.forEach(action -> {
      Set<EnvInfo> existingEnvIdSet = finalPermissionMap.get(action);
      if (isEmpty(existingEnvIdSet)) {
        existingEnvIdSet = new HashSet<>();
        finalPermissionMap.put(action, existingEnvIdSet);
      }
      existingEnvIdSet.addAll(envInfoSet);
    });
    return permissionMap;
  }

  private void attachPermission(Map<String, AppPermissionSummary> appPermissionMap,
      Map<PermissionType, Map<String, List<Base>>> permissionTypeAppIdEntityMap, Set<String> appIds,
      PermissionType permissionType, Filter entityFilter, Set<Action> actions) {
    final HashSet<Action> fixedEntityActions = Sets.newHashSet(Action.READ, Action.UPDATE, Action.DELETE,
        Action.EXECUTE_PIPELINE, Action.EXECUTE_WORKFLOW, Action.EXECUTE_WORKFLOW_ROLLBACK, Action.ABORT_WORKFLOW);
    appIds.forEach(appId -> {
      AppPermissionSummary appPermissionSummary = appPermissionMap.get(appId);
      if (appPermissionSummary == null) {
        appPermissionSummary = new AppPermissionSummary();
        appPermissionMap.put(appId, appPermissionSummary);
      }

      SetView<Action> intersection = Sets.intersection(fixedEntityActions, actions);
      Set<Action> entityActions = new HashSet<>(intersection);
      final AppPermissionSummary finalAppPermissionSummary = appPermissionSummary;
      switch (permissionType) {
        case SERVICE: {
          if (actions.contains(Action.CREATE)) {
            appPermissionSummary.setCanCreateService(true);
          }

          if (isEmpty(entityActions)) {
            break;
          }

          Set<String> entityIds = getServiceIdsByFilter(
              permissionTypeAppIdEntityMap.get(permissionType).get(appId), (GenericEntityFilter) entityFilter);
          if (isEmpty(entityIds)) {
            break;
          }
          Map<Action, Set<String>> actionEntityIdMap =
              buildActionEntityMap(finalAppPermissionSummary.getServicePermissions(), entityIds, entityActions);
          finalAppPermissionSummary.setServicePermissions(actionEntityIdMap);
          break;
        }
        case PROVISIONER: {
          if (actions.contains(Action.CREATE)) {
            appPermissionSummary.setCanCreateProvisioner(true);
          }

          if (isEmpty(entityActions)) {
            break;
          }
          Set<String> entityIds = getProvisionerIdsByFilter(
              permissionTypeAppIdEntityMap.get(permissionType).get(appId), (GenericEntityFilter) entityFilter);
          if (isEmpty(entityIds)) {
            break;
          }
          Map<Action, Set<String>> actionEntityIdMap =
              buildActionEntityMap(finalAppPermissionSummary.getProvisionerPermissions(), entityIds, entityActions);
          finalAppPermissionSummary.setProvisionerPermissions(actionEntityIdMap);
          break;
        }
        case ENV: {
          if (actions.contains(Action.CREATE)) {
            appPermissionSummary.setCanCreateEnvironment(true);
            Set<EnvironmentType> environmentTypeSet = addEnvTypesByFilter(
                finalAppPermissionSummary.getEnvCreatePermissionsForEnvTypes(), (EnvFilter) entityFilter);
            finalAppPermissionSummary.setEnvCreatePermissionsForEnvTypes(environmentTypeSet);
          }

          if (isEmpty(entityActions)) {
            break;
          }
          Set<EnvInfo> envInfoSet = getEnvsInfoByFilter(
              permissionTypeAppIdEntityMap.get(permissionType).get(appId), (EnvFilter) entityFilter);
          if (isEmpty(envInfoSet)) {
            break;
          }

          Map<Action, Set<EnvInfo>> actionEnvInfoMap =
              buildActionEnvMap(finalAppPermissionSummary.getEnvPermissions(), envInfoSet, entityActions);
          finalAppPermissionSummary.setEnvPermissions(actionEnvInfoMap);
          break;
        }
        case WORKFLOW: {
          if (entityFilter instanceof GenericEntityFilter) {
            // Workflow Permissions given per workflow basis
            long startTime = System.nanoTime();
            Set<String> workflowIdSet = null;
            if (actions.contains(Action.CREATE)) {
              appPermissionSummary.setCanCreateWorkflow(true);
              workflowIdSet = getWorkflowIdsByEntityFilter(
                  permissionTypeAppIdEntityMap.get(permissionType).get(appId), (GenericEntityFilter) entityFilter);
            }

            if (isEmpty(entityActions)) {
              break;
            }

            if (entityActions.contains(Action.UPDATE)) {
              if (workflowIdSet == null) {
                workflowIdSet = getWorkflowIdsByEntityFilter(
                    permissionTypeAppIdEntityMap.get(permissionType).get(appId), (GenericEntityFilter) entityFilter);
              }

              Set<String> updatedWorkflowIdSet = addToExistingEntityIdSet(
                  finalAppPermissionSummary.getWorkflowUpdatePermissionsForEnvs(), workflowIdSet);
              // Maintain list of workflows with access given directly so that RBAC check on env basis is not done
              finalAppPermissionSummary.setWorkflowUpdatePermissionsByEntity(updatedWorkflowIdSet);
            }

            Set<String> entityIds = getWorkflowIdsByEntityFilter(
                permissionTypeAppIdEntityMap.get(permissionType).get(appId), (GenericEntityFilter) entityFilter);

            if (isEmpty(entityIds)) {
              break;
            }
            Map<Action, Set<String>> actionEntityIdMap =
                buildActionEntityMap(finalAppPermissionSummary.getWorkflowPermissions(), entityIds, entityActions);
            finalAppPermissionSummary.setWorkflowPermissions(actionEntityIdMap);
            long endTime = System.nanoTime();
            log.info("{}: Elapsed time(ns) for attaching workflow permission: {}",
                FeatureName.WORKFLOW_PIPELINE_PERMISSION_BY_ENTITY.name(), endTime - startTime);
            break;
          } else {
            // Workflow Permissions given via Environment
            Set<String> envIdSet = null;
            if (actions.contains(Action.CREATE)) {
              appPermissionSummary.setCanCreateWorkflow(true);
              envIdSet = getEnvIdsByFilter(permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter);
              Set<String> updatedEnvIdSet =
                  addToExistingEntityIdSet(finalAppPermissionSummary.getWorkflowCreatePermissionsForEnvs(), envIdSet);
              finalAppPermissionSummary.setWorkflowCreatePermissionsForEnvs(updatedEnvIdSet);

              if (!finalAppPermissionSummary.isCanCreateTemplatizedWorkflow()) {
                WorkflowFilter workflowFilter = getDefaultWorkflowFilterIfNull((WorkflowFilter) entityFilter);
                Set<String> filterTypes = workflowFilter.getFilterTypes();
                if (isNotEmpty(filterTypes)) {
                  boolean hasTemplateFilterType = filterTypes.contains(WorkflowFilter.FilterType.TEMPLATES);
                  finalAppPermissionSummary.setCanCreateTemplatizedWorkflow(hasTemplateFilterType);
                }
              }
            }

            if (isEmpty(entityActions)) {
              break;
            }

            if (entityActions.contains(Action.UPDATE)) {
              if (envIdSet == null) {
                envIdSet =
                    getEnvIdsByFilter(permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter);
              }

              Set<String> updatedEnvIdSet =
                  addToExistingEntityIdSet(finalAppPermissionSummary.getWorkflowUpdatePermissionsForEnvs(), envIdSet);
              finalAppPermissionSummary.setWorkflowUpdatePermissionsForEnvs(updatedEnvIdSet);
            }

            Set<String> entityIds = getWorkflowIdsByFilter(permissionTypeAppIdEntityMap.get(permissionType).get(appId),
                permissionTypeAppIdEntityMap.get(ENV).get(appId), (WorkflowFilter) entityFilter);

            if (isEmpty(entityIds)) {
              break;
            }

            Map<Action, Set<String>> actionEntityIdMap =
                buildActionEntityMap(finalAppPermissionSummary.getWorkflowPermissions(), entityIds, entityActions);
            finalAppPermissionSummary.setWorkflowPermissions(actionEntityIdMap);
            break;
          }
        }
        case DEPLOYMENT: {
          if (isEmpty(entityActions)) {
            break;
          }
          Set<String> entityIds = getDeploymentIdsByFilter(permissionTypeAppIdEntityMap.get(WORKFLOW).get(appId),
              permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter, appId);

          if (isEmpty(entityIds)) {
            break;
          }

          Map<Action, Set<String>> actionEntityIdMap =
              buildActionEntityMap(finalAppPermissionSummary.getDeploymentPermissions(), entityIds, entityActions);
          finalAppPermissionSummary.setDeploymentPermissions(actionEntityIdMap);

          Set<String> envIdSet =
              getEnvIdsByFilter(permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter);

          if (entityActions.contains(Action.EXECUTE_WORKFLOW)) {
            Set<String> updatedEnvIdSet =
                addToExistingEntityIdSet(finalAppPermissionSummary.getWorkflowExecutePermissionsForEnvs(), envIdSet);
            finalAppPermissionSummary.setWorkflowExecutePermissionsForEnvs(updatedEnvIdSet);
          }
          if (entityActions.contains(Action.EXECUTE_PIPELINE)) {
            Set<String> updatedEnvIdSet =
                addToExistingEntityIdSet(finalAppPermissionSummary.getPipelineExecutePermissionsForEnvs(), envIdSet);
            finalAppPermissionSummary.setPipelineExecutePermissionsForEnvs(updatedEnvIdSet);
          }
          if (entityActions.contains(Action.EXECUTE_WORKFLOW_ROLLBACK)) {
            Set<String> updatedEnvIdSet = addToExistingEntityIdSet(
                finalAppPermissionSummary.getRollbackWorkflowExecutePermissionsForEnvs(), envIdSet);
            finalAppPermissionSummary.setRollbackWorkflowExecutePermissionsForEnvs(updatedEnvIdSet);
          }
          if (entityActions.contains(Action.ABORT_WORKFLOW)) {
            Set<String> updatedEnvIdSet = addToExistingEntityIdSet(
                finalAppPermissionSummary.getAbortWorkflowExecutePermissionsForEnvs(), envIdSet);
            finalAppPermissionSummary.setAbortWorkflowExecutePermissionsForEnvs(updatedEnvIdSet);
          }
          break;
        }
        case APP_TEMPLATE: {
          if (actions.contains(Action.CREATE)) {
            appPermissionSummary.setCanCreateTemplate(true);
          }

          if (isEmpty(entityActions)) {
            break;
          }

          Set<String> entityIds = getTemplateIdsByFilter(
              permissionTypeAppIdEntityMap.get(permissionType).get(appId), (GenericEntityFilter) entityFilter);
          if (isEmpty(entityIds)) {
            break;
          }
          Map<Action, Set<String>> actionEntityIdMap =
              buildActionEntityMap(finalAppPermissionSummary.getTemplatePermissions(), entityIds, entityActions);
          finalAppPermissionSummary.setTemplatePermissions(actionEntityIdMap);
          break;
        }
        default:
          noop();
      }
    });
  }

  private Set<String> addToExistingEntityIdSet(Set<String> existingEntityIdSet, Set<String> entityIdSet) {
    if (isEmpty(entityIdSet)) {
      return existingEntityIdSet;
    }

    if (existingEntityIdSet == null) {
      existingEntityIdSet = new HashSet<>();
    }

    existingEntityIdSet.addAll(entityIdSet);
    return existingEntityIdSet;
  }

  private Map<String, Workflow> prepareWorkflowCacheFromEntityMap(
      String appId, Map<PermissionType, Map<String, List<Base>>> permissionTypeAppIdEntityMap) {
    // Try to create a workflow cache using permissionTypeAppIdEntityMap.
    Map<String, Workflow> workflowCache = new HashMap<>();
    if (isEmpty(permissionTypeAppIdEntityMap)) {
      return workflowCache;
    }

    Map<String, List<Base>> appIdWorkflowsMap = permissionTypeAppIdEntityMap.get(WORKFLOW);
    if (isNotEmpty(appIdWorkflowsMap)) {
      List<Base> workflowList = appIdWorkflowsMap.get(appId);
      if (isNotEmpty(workflowList)) {
        for (Base entity : workflowList) {
          if (!(entity instanceof Workflow)) {
            continue;
          }

          Workflow workflow = (Workflow) entity;
          workflowCache.put(workflow.getUuid(), workflow);
        }
      }
    }

    return workflowCache;
  }

  private void attachPipelinePermission(Multimap<String, Action> envActionMap,
      Map<String, AppPermissionSummary> appPermissionMap,
      Map<PermissionType, Map<String, List<Base>>> permissionTypeAppIdEntityMap, Set<String> appIds,
      PermissionType permissionType, Filter entityFilter, Set<Action> actions) {
    final HashSet<Action> fixedEntityActions = Sets.newHashSet(Action.READ, Action.UPDATE, Action.DELETE,
        Action.EXECUTE_PIPELINE, Action.EXECUTE_WORKFLOW, Action.EXECUTE_WORKFLOW_ROLLBACK, Action.ABORT_WORKFLOW);
    appIds.forEach(appId -> {
      AppPermissionSummary appPermissionSummary = appPermissionMap.get(appId);
      if (appPermissionSummary == null) {
        appPermissionSummary = new AppPermissionSummary();
        appPermissionMap.put(appId, appPermissionSummary);
      }

      Map<String, Workflow> workflowCache = prepareWorkflowCacheFromEntityMap(appId, permissionTypeAppIdEntityMap);
      SetView<Action> intersection = Sets.intersection(fixedEntityActions, actions);
      Set<Action> entityActions = new HashSet<>(intersection);
      AppPermissionSummary finalAppPermissionSummary = appPermissionSummary;
      Multimap<String, Action> pipelineIdActionMap;
      switch (permissionType) {
        case PIPELINE:
          if (entityFilter instanceof GenericEntityFilter) {
            long startTime = System.nanoTime();
            Set<String> pipelineIdSet = null;
            if (actions.contains(Action.CREATE)) {
              appPermissionSummary.setCanCreatePipeline(true);
              pipelineIdSet = getPipelineIdsByEntityFilter(
                  permissionTypeAppIdEntityMap.get(PIPELINE).get(appId), (GenericEntityFilter) entityFilter);
            }

            if (isEmpty(entityActions)) {
              break;
            }

            if (entityActions.contains(Action.UPDATE)) {
              if (pipelineIdSet == null) {
                pipelineIdSet = getPipelineIdsByEntityFilter(
                    permissionTypeAppIdEntityMap.get(PIPELINE).get(appId), (GenericEntityFilter) entityFilter);
              }
              Set<String> updatedPipelineIdSet = addToExistingEntityIdSet(
                  finalAppPermissionSummary.getPipelineUpdatePermissionsByEntity(), pipelineIdSet);
              finalAppPermissionSummary.setPipelineUpdatePermissionsByEntity(updatedPipelineIdSet);
            }

            pipelineIdActionMap =
                getPipelineActionMapByEntityFilter(permissionTypeAppIdEntityMap.get(PIPELINE).get(appId),
                    (GenericEntityFilter) entityFilter, entityActions);

            Map<Action, Set<String>> actionEntityIdMap =
                buildActionPipelineMap(finalAppPermissionSummary.getPipelinePermissions(), pipelineIdActionMap);
            finalAppPermissionSummary.setPipelinePermissions(actionEntityIdMap);
            long endTime = System.nanoTime();
            log.info("{}: Elapsed time(ns) for attaching pipeline permission: {}",
                FeatureName.WORKFLOW_PIPELINE_PERMISSION_BY_ENTITY.name(), endTime - startTime);
            break;
          } else {
            Set<String> envIdSet = null;
            if (actions.contains(Action.CREATE)) {
              appPermissionSummary.setCanCreatePipeline(true);
              envIdSet = getEnvIdsByFilter(permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter);
              Set<String> updatedEnvIdSet =
                  addToExistingEntityIdSet(finalAppPermissionSummary.getPipelineCreatePermissionsForEnvs(), envIdSet);
              finalAppPermissionSummary.setPipelineCreatePermissionsForEnvs(updatedEnvIdSet);
            }

            if (isEmpty(entityActions)) {
              break;
            }

            if (entityActions.contains(Action.UPDATE)) {
              if (envIdSet == null) {
                envIdSet =
                    getEnvIdsByFilter(permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter);
              }
              Set<String> updatedEnvIdSet =
                  addToExistingEntityIdSet(finalAppPermissionSummary.getPipelineUpdatePermissionsForEnvs(), envIdSet);
              finalAppPermissionSummary.setPipelineUpdatePermissionsForEnvs(updatedEnvIdSet);
            }

            pipelineIdActionMap = getPipelineIdsByFilter(permissionTypeAppIdEntityMap.get(PIPELINE).get(appId),
                permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter, envActionMap, entityActions,
                workflowCache);

            Map<Action, Set<String>> actionEntityIdMap =
                buildActionPipelineMap(finalAppPermissionSummary.getPipelinePermissions(), pipelineIdActionMap);
            finalAppPermissionSummary.setPipelinePermissions(actionEntityIdMap);
            break;
          }

        case DEPLOYMENT:
          if (isEmpty(entityActions)) {
            break;
          }

          pipelineIdActionMap = getPipelineIdsByFilter(permissionTypeAppIdEntityMap.get(PIPELINE).get(appId),
              permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter, envActionMap, entityActions,
              workflowCache);
          Map<Action, Set<String>> actionEntityIdMap =
              buildActionPipelineMap(finalAppPermissionSummary.getDeploymentPermissions(), pipelineIdActionMap);
          finalAppPermissionSummary.setDeploymentPermissions(actionEntityIdMap);

          Map<AppPermissionSummary.ExecutableElementInfo, Set<String>> envDeploymentPermissionMap =
              appPermissionSummary.getEnvExecutableElementDeployPermissions();
          if (isEmpty(envDeploymentPermissionMap)) {
            envDeploymentPermissionMap = new HashMap<>();
          }
          buildPipelineEnvMap(permissionTypeAppIdEntityMap.get(PIPELINE).get(appId),
              permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter, envDeploymentPermissionMap,
              entityActions);
          buildWorkflowEnvMap(permissionTypeAppIdEntityMap.get(WORKFLOW).get(appId),
              permissionTypeAppIdEntityMap.get(ENV).get(appId), (EnvFilter) entityFilter, envDeploymentPermissionMap,
              entityActions);
          finalAppPermissionSummary.setEnvExecutableElementDeployPermissions(envDeploymentPermissionMap);
          break;

        default:
          noop();
      }
    });
  }

  private void buildPipelineEnvMap(List<Base> pipelines, List<Base> environments, EnvFilter filter,
      Map<AppPermissionSummary.ExecutableElementInfo, Set<String>> permission, Set<Action> entityActions) {
    if (isEmpty(pipelines) || isEmpty(entityActions) || !entityActions.contains(Action.EXECUTE_PIPELINE)) {
      return;
    }

    ExecutableElementsFilter executableElementsFilter;
    if (filter == null) {
      executableElementsFilter = new ExecutableElementsFilter();
      executableElementsFilter.setFilterTypes(Sets.newHashSet(PROD, NON_PROD));
      executableElementsFilter.setExecutableElementFilterType(ExecutableElementsFilter.FilterType.PIPELINE);
      executableElementsFilter.setFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build());
    } else {
      if (filter instanceof ExecutableElementsFilter) {
        executableElementsFilter = (ExecutableElementsFilter) filter;
      } else {
        executableElementsFilter = new ExecutableElementsFilter();
        executableElementsFilter.setFilterTypes(filter.getFilterTypes());
        executableElementsFilter.setIds(filter.getIds());
        executableElementsFilter.setExecutableElementFilterType(ExecutableElementsFilter.FilterType.PIPELINE);
        executableElementsFilter.setFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build());
      }
    }

    final Set<String> envIds = getEnvIdsByFilter(environments, filter);
    final String executableElementFilterType = executableElementsFilter.getExecutableElementFilterType();
    final GenericEntityFilter executableElementFilter = executableElementsFilter.getFilter();
    final Set<String> pipelineIdsByEntityFilter = getPipelineIdsByEntityFilter(pipelines, executableElementFilter);
    pipelineIdsByEntityFilter.forEach(pipelineId -> {
      final AppPermissionSummary.ExecutableElementInfo executableElementInfo =
          AppPermissionSummary.ExecutableElementInfo.builder()
              .entityId(pipelineId)
              .entityType(executableElementFilterType)
              .build();
      if (permission.containsKey(executableElementInfo)) {
        permission.get(executableElementInfo).addAll(ObjectUtils.clone(envIds));
      } else {
        permission.put(executableElementInfo, ObjectUtils.clone(envIds));
      }
    });
  }

  private void buildWorkflowEnvMap(List<Base> workflows, List<Base> environments, EnvFilter filter,
      Map<AppPermissionSummary.ExecutableElementInfo, Set<String>> permission, Set<Action> entityActions) {
    if (isEmpty(workflows) || isEmpty(entityActions) || !entityActions.contains(Action.EXECUTE_WORKFLOW)) {
      return;
    }

    ExecutableElementsFilter executableElementsFilter;
    if (filter == null) {
      executableElementsFilter = new ExecutableElementsFilter();
      executableElementsFilter.setFilterTypes(Sets.newHashSet(PROD, NON_PROD));
      executableElementsFilter.setExecutableElementFilterType(ExecutableElementsFilter.FilterType.WORKFLOW);
      executableElementsFilter.setFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build());
    } else {
      if (filter instanceof ExecutableElementsFilter) {
        executableElementsFilter = (ExecutableElementsFilter) filter;
      } else {
        executableElementsFilter = new ExecutableElementsFilter();
        executableElementsFilter.setFilterTypes(filter.getFilterTypes());
        executableElementsFilter.setIds(filter.getIds());
        executableElementsFilter.setExecutableElementFilterType(ExecutableElementsFilter.FilterType.WORKFLOW);
        executableElementsFilter.setFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build());
      }
    }
    final String executableElementFilterType = executableElementsFilter.getExecutableElementFilterType();
    final GenericEntityFilter executableElementFilter = executableElementsFilter.getFilter();
    final Set<String> workflowIdsByEntityFilter = getWorkflowIdsByEntityFilter(workflows, executableElementFilter);
    final Set<String> envIds = getEnvIdsByFilter(environments, filter);
    workflowIdsByEntityFilter.forEach(workflowId -> {
      final AppPermissionSummary.ExecutableElementInfo executableElementInfo =
          AppPermissionSummary.ExecutableElementInfo.builder()
              .entityId(workflowId)
              .entityType(executableElementFilterType)
              .build();
      if (permission.containsKey(executableElementInfo)) {
        permission.get(executableElementInfo).addAll(ObjectUtils.clone(envIds));
      } else {
        permission.put(executableElementInfo, ObjectUtils.clone(envIds));
      }
    });
  }

  private Map<PermissionType, Map<String, List<Base>>> fetchRequiredEntities(
      String accountId, Map<PermissionType, Set<String>> permissionTypeAppIdSetMap) {
    Map<PermissionType, Map<String, List<Base>>> permissionTypeAppIdEntityMap = new HashMap<>();
    if ((permissionTypeAppIdSetMap.containsKey(PIPELINE) || permissionTypeAppIdSetMap.containsKey(DEPLOYMENT))
        && !permissionTypeAppIdSetMap.containsKey(WORKFLOW)) {
      // Read workflows in case the WORKFLOW permission type is not present but the PIPELINE or DEPLOYMENT permission is
      // present.
      permissionTypeAppIdEntityMap.put(WORKFLOW, getAppIdWorkflowMap(accountId));
    }

    permissionTypeAppIdSetMap.keySet().forEach(permissionType -> {
      switch (permissionType) {
        case SERVICE: {
          permissionTypeAppIdEntityMap.put(permissionType, getAppIdServiceMap(accountId));
          break;
        }
        case PROVISIONER: {
          permissionTypeAppIdEntityMap.put(permissionType, getAppIdProvisionerMap(accountId));
          break;
        }
        case ENV: {
          permissionTypeAppIdEntityMap.put(permissionType, getAppIdEnvMap(accountId));
          break;
        }
        case WORKFLOW: {
          permissionTypeAppIdEntityMap.put(permissionType, getAppIdWorkflowMap(accountId));
          break;
        }
        case PIPELINE: {
          permissionTypeAppIdEntityMap.put(permissionType, getAppIdPipelineMap(accountId));
          break;
        }
        case APP_TEMPLATE: {
          permissionTypeAppIdEntityMap.put(permissionType, getAppIdTemplateMap(accountId));
          break;
        }
        default: {
          noop();
        }
      }
    });
    return permissionTypeAppIdEntityMap;
  }

  private Map<String, List<Base>> getAppIdServiceMap(String accountId) {
    List<Service> list = serviceResourceService.list(accountId, Arrays.asList("_id", "appId"));
    return list.stream().collect(Collectors.groupingBy(Base::getAppId));
  }

  private Map<String, List<Base>> getAppIdProvisionerMap(String accountId) {
    PageRequest<InfrastructureProvisioner> pageRequest =
        aPageRequest().addFilter("accountId", Operator.EQ, accountId).addFieldsIncluded("_id", "appId").build();

    List<InfrastructureProvisioner> list =
        getAllEntities(pageRequest, () -> infrastructureProvisionerService.list(pageRequest));
    return list.stream().collect(Collectors.groupingBy(Base::getAppId));
  }

  private Map<String, List<Base>> getAppIdEnvMap(String accountId) {
    PageRequest<Environment> pageRequest = aPageRequest()
                                               .addFilter("accountId", Operator.EQ, accountId)
                                               .addFieldsIncluded("_id", "appId", "environmentType")
                                               .build();

    List<Environment> list =
        getAllEntities(pageRequest, () -> environmentService.list(pageRequest, false, null, false));

    return list.stream().collect(Collectors.groupingBy(Base::getAppId));
  }

  private Map<String, List<Base>> getAppIdWorkflowMap(String accountId) {
    List<Workflow> list = workflowService.list(
        accountId, Arrays.asList("_id", "appId", "envId", "templatized", "templateExpressions"), Workflow.RBAC_INDEX_2);
    return list.stream().collect(Collectors.groupingBy(Base::getAppId));
  }

  private Map<String, List<Base>> getAppIdPipelineMap(String accountId) {
    if (featureFlagService.isEnabled(FeatureName.SPG_OPTIMIZE_PIPELINE_QUERY_ON_AUTH, accountId)) {
      List<Pipeline> pipelines = wingsPersistence.createQuery(Pipeline.class)
                                     .filter(PipelineKeys.accountId, accountId)
                                     .project(PipelineKeys.uuid, true)
                                     .project(PipelineKeys.appId, true)
                                     .project(PipelineKeys.accountId, true)
                                     .asList();
      return pipelines.stream().collect(Collectors.groupingBy(Base::getAppId));
    } else {
      PageRequest<Pipeline> pageRequest = aPageRequest().addFilter("accountId", Operator.EQ, accountId).build();
      List<Pipeline> list = getAllEntities(pageRequest, () -> pipelineService.listPipelines(pageRequest));
      return list.stream().collect(Collectors.groupingBy(Base::getAppId));
    }
  }

  private Map<String, List<Base>> getAppIdTemplateMap(String accountId) {
    PageRequest<Template> pageRequest = aPageRequest()
                                            .addFilter(TemplateKeys.accountId, Operator.EQ, accountId)
                                            .addFilter(TemplateKeys.appId, Operator.NOT_EQ, GLOBAL_APP_ID)
                                            .build();
    List<Template> list = getAllEntities(pageRequest,
        ()
            -> templateService.list(pageRequest,
                Collections.singletonList(templateGalleryService.getAccountGalleryKey().name()), accountId, false));
    return list.stream().collect(Collectors.groupingBy(Base::getAppId));
  }

  private void populateRequiredAccountPermissions(
      List<UserGroup> userGroups, Set<PermissionType> accountPermissionSet) {
    userGroups.forEach(userGroup -> {
      AccountPermissions accountPermissions = userGroup.getAccountPermissions();
      if (accountPermissions != null) {
        Set<PermissionType> permissions = accountPermissions.getPermissions();
        if (CollectionUtils.isNotEmpty(permissions)) {
          accountPermissionSet.addAll(permissions);
        }
      }
    });
  }

  private Map<PermissionType, Set<String>> collectRequiredAppIds(
      List<UserGroup> userGroups, HashSet<String> allAppIds) {
    Map<PermissionType, Set<String>> permissionTypeAppIdSetMap = new HashMap<>();
    // initialize
    asList(SERVICE, PROVISIONER, ENV, WORKFLOW, PIPELINE, DEPLOYMENT, APP_TEMPLATE)
        .forEach(permissionType -> permissionTypeAppIdSetMap.put(permissionType, new HashSet<>()));

    userGroups.forEach(userGroup -> {
      Set<AppPermission> appPermissions = userGroup.getAppPermissions();
      if (isEmpty(appPermissions)) {
        return;
      }

      appPermissions.forEach(appPermission -> {
        Set<String> appIdSet = getAppIdsByFilter(allAppIds, appPermission.getAppFilter());
        if (isEmpty(appIdSet)) {
          return;
        }
        PermissionType permissionType = appPermission.getPermissionType();
        if (permissionType == PermissionType.ALL_APP_ENTITIES) {
          asList(SERVICE, PROVISIONER, ENV, WORKFLOW, PIPELINE, DEPLOYMENT, APP_TEMPLATE).forEach(permissionType1 -> {
            permissionTypeAppIdSetMap.get(permissionType1).addAll(appIdSet);
          });
        } else {
          permissionTypeAppIdSetMap.get(permissionType).addAll(appIdSet);
        }
      });
    });

    Set<String> appIdSetForWorkflowPermission = permissionTypeAppIdSetMap.get(WORKFLOW);
    if (isEmpty(appIdSetForWorkflowPermission)) {
      appIdSetForWorkflowPermission = new HashSet<>();
      permissionTypeAppIdSetMap.put(WORKFLOW, appIdSetForWorkflowPermission);
    }

    // pipeline will need workflow
    appIdSetForWorkflowPermission.addAll(permissionTypeAppIdSetMap.get(PIPELINE));

    Set<String> appIdSetForEnvPermission = permissionTypeAppIdSetMap.get(ENV);
    if (isEmpty(appIdSetForEnvPermission)) {
      appIdSetForEnvPermission = new HashSet<>();
      permissionTypeAppIdSetMap.put(ENV, appIdSetForEnvPermission);
    }

    // workflow will need env
    appIdSetForEnvPermission.addAll(appIdSetForWorkflowPermission);

    // DEPLOYMENT will need env
    appIdSetForEnvPermission.addAll(permissionTypeAppIdSetMap.get(DEPLOYMENT));

    return permissionTypeAppIdSetMap;
  }

  private Set<String> getAppIdsByFilter(Set<String> allAppIds, AppFilter appFilter) {
    if (appFilter == null) {
      return new HashSet<>(allAppIds);
    }

    switch (appFilter.getFilterType()) {
      case AppFilter.FilterType.ALL:
        return new HashSet<>(allAppIds);
      case AppFilter.FilterType.SELECTED:
        return appFilter.getIds() == null ? new HashSet<>()
                                          : new HashSet<>(Sets.intersection(allAppIds, appFilter.getIds()));
      case AppFilter.FilterType.EXCLUDE_SELECTED:
        return appFilter.getIds() == null ? new HashSet<>(allAppIds)
                                          : new HashSet<>(Sets.difference(allAppIds, appFilter.getIds()));
      default:
        throw new InvalidRequestException("Unknown app filter type: " + appFilter.getFilterType());
    }
  }

  public Set<String> getAppIdsByFilter(String accountId, AppFilter appFilter) {
    List<String> appIdsByAccountId = appService.getAppIdsByAccountId(accountId);
    return getAppIdsByFilter(Sets.newHashSet(appIdsByAccountId), appFilter);
  }

  public Set<String> getEnvIdsByFilter(String appId, EnvFilter envFilter) {
    PageRequest<Environment> pageRequest =
        aPageRequest().addFilter("appId", Operator.EQ, appId).addFieldsIncluded("_id", "environmentType").build();
    List<Environment> envList =
        getAllEntities(pageRequest, () -> environmentService.list(pageRequest, false, null, false));

    return getEnvIdsByFilter(envList, envFilter);
  }

  public <T extends Base> List<T> getAllEntities(PageRequest<T> pageRequest, Callable<PageResponse<T>> callable) {
    return wingsPersistence.getAllEntities(pageRequest, callable);
  }

  public void setEntityIdFilterIfUserAction(
      List<PermissionAttribute> requiredPermissionAttributes, List<String> appIds) {
    User user = UserThreadLocal.get();
    if (user != null && user.getUserRequestContext() != null) {
      setEntityIdFilter(requiredPermissionAttributes, user.getUserRequestContext(), appIds);
    }
  }

  public boolean authorize(
      List<PermissionAttribute> requiredPermissionAttributes, List<String> appIds, String entityId) {
    User user = UserThreadLocal.get();
    if (user != null) {
      UserRequestContext userRequestContext = user.getUserRequestContext();
      // UserRequestContext is null if rbac enabled is false
      if (userRequestContext != null) {
        authService.authorize(userRequestContext.getAccountId(), appIds, entityId, user, requiredPermissionAttributes);
      }
    }
    return true;
  }

  private void setEntityIdFilter(List<PermissionAttribute> requiredPermissionAttributes,
      UserRequestContext userRequestContext, List<String> appIds) {
    String entityFieldName = getEntityFieldName(requiredPermissionAttributes);

    userRequestContext.setEntityIdFilterRequired(true);

    Set<String> entityIds =
        getEntityIds(requiredPermissionAttributes, userRequestContext.getUserPermissionInfo(), appIds);
    EntityInfo entityInfo = EntityInfo.builder().entityFieldName(entityFieldName).entityIds(entityIds).build();
    String entityClassName = getEntityClassName(requiredPermissionAttributes);
    userRequestContext.getEntityInfoMap().put(entityClassName, entityInfo);
  }

  public void setEntityIdFilterIfGet(String httpMethod, boolean skipAuth,
      List<PermissionAttribute> requiredPermissionAttributes, UserRequestContext userRequestContext,
      boolean appIdFilterRequired, Set<String> allowedAppIds, List<String> appIdsFromRequest) {
    if (!skipAuth && HttpMethod.GET.name().equals(httpMethod)) {
      setEntityIdFilter(requiredPermissionAttributes, userRequestContext,
          appIdFilterRequired ? ImmutableList.copyOf(allowedAppIds) : appIdsFromRequest);
    }
  }

  private Set<String> getEntityIds(
      List<PermissionAttribute> permissionAttributes, UserPermissionInfo userPermissionInfo, List<String> appIds) {
    final Set<String> entityIds = new HashSet<>();

    if (appIds == null) {
      return entityIds;
    }

    Map<String, AppPermissionSummary> appPermissionMap = userPermissionInfo.getAppPermissionMapInternal();
    if (MapUtils.isEmpty(appPermissionMap)) {
      return entityIds;
    }

    for (String appId : appIds) {
      AppPermissionSummary appPermissionSummary = appPermissionMap.get(appId);
      if (appPermissionSummary == null) {
        continue;
      }

      for (PermissionAttribute permissionAttribute : permissionAttributes) {
        PermissionType permissionType = permissionAttribute.getPermissionType();
        Action action = permissionAttribute.getAction();

        Map<Action, Set<String>> entityPermissions = null;
        if (permissionType == SERVICE) {
          entityPermissions = appPermissionSummary.getServicePermissions();
        } else if (permissionType == PROVISIONER) {
          entityPermissions = appPermissionSummary.getProvisionerPermissions();
        } else if (permissionType == ENV) {
          Map<Action, Set<EnvInfo>> envEntityPermissions = appPermissionSummary.getEnvPermissions();
          if (isNotEmpty(envEntityPermissions)) {
            Set<EnvInfo> envInfoSet = envEntityPermissions.get(action);
            if (isNotEmpty(envInfoSet)) {
              envInfoSet.forEach(envInfo -> entityIds.add(envInfo.getEnvId()));
            }
          }
          continue;
        } else if (permissionType == WORKFLOW) {
          entityPermissions = appPermissionSummary.getWorkflowPermissions();
        } else if (permissionType == PIPELINE) {
          entityPermissions = appPermissionSummary.getPipelinePermissions();
        } else if (permissionType == DEPLOYMENT) {
          entityPermissions = appPermissionSummary.getDeploymentPermissions();
        } else if (permissionType == APP_TEMPLATE) {
          entityPermissions = appPermissionSummary.getTemplatePermissions();
        }

        if (isEmpty(entityPermissions)) {
          continue;
        }

        Set<String> entityIdCollection = entityPermissions.get(action);
        if (isNotEmpty(entityIdCollection)) {
          entityIds.addAll(entityIdCollection);
        }
      }
    }
    return entityIds;
  }

  private String getEntityFieldName(List<PermissionAttribute> permissionAttributes) {
    Optional<String> entityFieldNameOptional = permissionAttributes.stream()
                                                   .map(permissionAttribute -> {
                                                     if (StringUtils.isNotBlank(permissionAttribute.getDbFieldName())) {
                                                       return permissionAttribute.getDbFieldName();
                                                     }

                                                     return "_id";
                                                   })
                                                   .findFirst();

    if (entityFieldNameOptional.isPresent()) {
      return entityFieldNameOptional.get();
    }

    return null;
  }

  private String getEntityClassName(List<PermissionAttribute> permissionAttributes) {
    Optional<String> entityFieldNameOptional =
        permissionAttributes.stream()
            .map(permissionAttribute -> {
              if (StringUtils.isNotBlank(permissionAttribute.getDbCollectionName())) {
                return permissionAttribute.getDbCollectionName();
              }

              PermissionType permissionType = permissionAttribute.getPermissionType();

              String className;
              if (permissionType == SERVICE) {
                className = Service.class.getName();
              } else if (permissionType == PROVISIONER) {
                className = InfrastructureProvisioner.class.getName();
              } else if (permissionType == ENV) {
                className = Environment.class.getName();
              } else if (permissionType == WORKFLOW) {
                className = Workflow.class.getName();
              } else if (permissionType == PIPELINE) {
                className = Pipeline.class.getName();
              } else if (permissionType == DEPLOYMENT) {
                className = WorkflowExecution.class.getName();
              } else if (permissionType == APP_TEMPLATE) {
                className = Template.class.getName();
              } else {
                throw new InvalidRequestException("Invalid permission type: " + permissionType);
              }

              return className;
            })
            .findFirst();

    if (entityFieldNameOptional.isPresent()) {
      return entityFieldNameOptional.get();
    }

    return null;
  }

  private Set<String> getServiceIdsByFilter(List<Base> services, GenericEntityFilter serviceFilter) {
    if (isEmpty(services)) {
      return new HashSet<>();
    }
    if (serviceFilter == null) {
      serviceFilter = GenericEntityFilter.builder().filterType(FilterType.ALL).build();
    }

    if (FilterType.ALL.equals(serviceFilter.getFilterType())) {
      return services.stream().map(Base::getUuid).collect(Collectors.toSet());
    } else if (SELECTED.equals(serviceFilter.getFilterType())) {
      GenericEntityFilter finalServiceFilter = serviceFilter;
      return services.stream()
          .filter(service -> finalServiceFilter.getIds().contains(service.getUuid()))
          .map(Base::getUuid)
          .collect(Collectors.toSet());
    } else {
      String msg = "Unknown service filter type: " + serviceFilter.getFilterType();
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private Set<String> getTemplateIdsByFilter(List<Base> templates, GenericEntityFilter templateFilter) {
    if (isEmpty(templates)) {
      return new HashSet<>();
    }
    if (templateFilter == null) {
      templateFilter = GenericEntityFilter.builder().filterType(FilterType.ALL).build();
    }

    if (FilterType.ALL.equals(templateFilter.getFilterType())) {
      return templates.stream().map(Base::getUuid).collect(Collectors.toSet());
    } else if (SELECTED.equals(templateFilter.getFilterType())) {
      GenericEntityFilter finalTemplateFilter = templateFilter;
      return templates.stream()
          .filter(service -> finalTemplateFilter.getIds().contains(service.getUuid()))
          .map(Base::getUuid)
          .collect(Collectors.toSet());
    } else {
      String msg = "Unknown template filter type: " + templateFilter.getFilterType();
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private Set<String> getProvisionerIdsByFilter(List<Base> provisioners, GenericEntityFilter provisionerFilter) {
    if (isEmpty(provisioners)) {
      return new HashSet<>();
    }
    if (provisionerFilter == null) {
      provisionerFilter = GenericEntityFilter.builder().filterType(FilterType.ALL).build();
    }

    if (FilterType.ALL.equals(provisionerFilter.getFilterType())) {
      return provisioners.stream().map(Base::getUuid).collect(Collectors.toSet());
    } else if (SELECTED.equals(provisionerFilter.getFilterType())) {
      GenericEntityFilter finalServiceFilter = provisionerFilter;
      return provisioners.stream()
          .filter(service -> finalServiceFilter.getIds().contains(service.getUuid()))
          .map(Base::getUuid)
          .collect(Collectors.toSet());
    } else {
      String msg = "Unknown service filter type: " + provisionerFilter.getFilterType();
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private EnvFilter getDefaultEnvFilterIfNull(EnvFilter envFilter) {
    if (envFilter == null || isEmpty(envFilter.getFilterTypes())) {
      envFilter = new EnvFilter();
      envFilter.setFilterTypes(Sets.newHashSet(PROD, NON_PROD));
    }
    return envFilter;
  }

  private <T extends Base> Set<String> getEnvIdsByFilter(List<T> environments, EnvFilter envFilter) {
    if (environments == null) {
      return new HashSet<>();
    }

    envFilter = getDefaultEnvFilterIfNull(envFilter);

    Set<String> filterTypes = envFilter.getFilterTypes();

    boolean selected = hasEnvSelectedType(envFilter);
    if (selected) {
      EnvFilter finalEnvFilter = envFilter;
      return environments.stream()
          .filter(environment -> finalEnvFilter.getIds().contains(environment.getUuid()))
          .map(Base::getUuid)
          .collect(Collectors.toSet());
    } else {
      return environments.stream()
          .filter(environment -> filterTypes.contains(((Environment) environment).getEnvironmentType().name()))
          .map(Base::getUuid)
          .collect(Collectors.toSet());
    }
  }

  private Set<EnvInfo> getEnvsInfoByFilter(List<Base> environments, EnvFilter envFilter) {
    if (environments == null) {
      return new HashSet<>();
    }

    envFilter = getDefaultEnvFilterIfNull(envFilter);

    Set<String> filterTypes = envFilter.getFilterTypes();

    boolean selected = hasEnvSelectedType(envFilter);
    if (selected) {
      EnvFilter finalEnvFilter = envFilter;
      return environments.stream()
          .filter(environment -> finalEnvFilter.getIds().contains(environment.getUuid()))
          .map(environment
              -> EnvInfo.builder()
                     .envId(environment.getUuid())
                     .envType(((Environment) environment).getEnvironmentType().name())
                     .build())
          .collect(Collectors.toSet());
    } else {
      return environments.stream()
          .filter(environment -> filterTypes.contains(((Environment) environment).getEnvironmentType().name()))
          .map(environment
              -> EnvInfo.builder()
                     .envId(environment.getUuid())
                     .envType(((Environment) environment).getEnvironmentType().name())
                     .build())
          .collect(Collectors.toSet());
    }
  }

  private Set<EnvironmentType> addEnvTypesByFilter(Set<EnvironmentType> existingEnvTypes, EnvFilter envFilter) {
    envFilter = getDefaultEnvFilterIfNull(envFilter);

    if (existingEnvTypes == null) {
      existingEnvTypes = new HashSet<>();
    }

    Set<EnvironmentType> environmentTypeSet = envFilter.getFilterTypes()
                                                  .stream()
                                                  .filter(filter -> !filter.equals(EnvFilter.FilterType.SELECTED))
                                                  .map(EnvironmentType::valueOf)
                                                  .collect(Collectors.toSet());
    existingEnvTypes.addAll(environmentTypeSet);

    return existingEnvTypes;
  }

  private WorkflowFilter getDefaultWorkflowFilterIfNull(WorkflowFilter workflowFilter) {
    if (workflowFilter == null || isEmpty(workflowFilter.getFilterTypes())) {
      workflowFilter = new WorkflowFilter();
      workflowFilter.setFilterTypes(Sets.newHashSet(PROD, NON_PROD, WorkflowFilter.FilterType.TEMPLATES));
    }
    return workflowFilter;
  }

  private Set<String> getWorkflowIdsByFilter(
      List<Base> workflows, List<Base> environments, WorkflowFilter workflowFilter) {
    if (workflows == null) {
      return new HashSet<>();
    }

    workflowFilter = getDefaultWorkflowFilterIfNull(workflowFilter);

    Set<String> filterEnvIds = workflowFilter.getIds();
    if (filterEnvIds == null) {
      filterEnvIds = new HashSet<>();
    }

    boolean hasTemplateFilterType = workflowFilter.getFilterTypes().contains(WorkflowFilter.FilterType.TEMPLATES);

    Set<String> finalFilterEnvIds = filterEnvIds;
    WorkflowFilter finalWorkflowFilter = workflowFilter;

    final Set<String> envIds;
    if (environments != null) {
      envIds = environments.stream()
                   .filter(environment
                       -> finalFilterEnvIds.contains(environment.getUuid())
                           || finalWorkflowFilter.getFilterTypes().contains(
                               ((Environment) environment).getEnvironmentType().name()))
                   .map(Base::getUuid)
                   .collect(Collectors.toSet());
    } else {
      envIds = Collections.emptySet();
    }

    return workflows.stream()
        .filter(workflow -> {
          Workflow workflowObj = (Workflow) workflow;
          if (isEnvTemplatized(workflowObj)) {
            return hasTemplateFilterType;
          }

          if (workflowObj.getEnvId() == null) {
            return true;
          }

          return envIds.contains(workflowObj.getEnvId());
        })
        .map(Base::getUuid)
        .collect(Collectors.toSet());
  }

  private Set<String> getWorkflowIdsByEntityFilter(List<Base> workflows, GenericEntityFilter workflowFilter) {
    if (isEmpty(workflows)) {
      return new HashSet<>();
    }
    if (workflowFilter == null) {
      workflowFilter = GenericEntityFilter.builder().filterType(FilterType.ALL).build();
    }

    if (FilterType.ALL.equals(workflowFilter.getFilterType())) {
      return workflows.stream().map(Base::getUuid).collect(Collectors.toSet());
    } else if (SELECTED.equals(workflowFilter.getFilterType())) {
      GenericEntityFilter finalServiceFilter = workflowFilter;
      return workflows.stream()
          .filter(workflow -> finalServiceFilter.getIds().contains(workflow.getUuid()))
          .map(Base::getUuid)
          .collect(Collectors.toSet());
    } else {
      String msg = "Unknown workflow filter type: " + workflowFilter.getFilterType();
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private Set<String> getPipelineIdsByEntityFilter(List<Base> pipelines, GenericEntityFilter pipelineFilter) {
    if (isEmpty(pipelines)) {
      return new HashSet<>();
    }

    // why do this for other filters
    if (pipelineFilter == null) {
      pipelineFilter = GenericEntityFilter.builder().filterType(FilterType.ALL).build();
    }

    if (FilterType.ALL.equals(pipelineFilter.getFilterType())) {
      return pipelines.stream().map(Base::getUuid).collect(Collectors.toSet());
    } else if (SELECTED.equals(pipelineFilter.getFilterType())) {
      GenericEntityFilter finalPipelineFilter = pipelineFilter;
      return pipelines.stream()
          .filter(pipeline -> finalPipelineFilter.getIds().contains(pipeline.getUuid()))
          .map(Base::getUuid)
          .collect(Collectors.toSet());
    } else {
      String msg = "Unknown pipeline filter type: " + pipelineFilter.getFilterType();
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private Set<String> getDeploymentIdsByFilter(
      List<Base> workflows, List<Base> environments, EnvFilter envFilter, String appId) {
    WorkflowFilter workflowFilter = getWorkflowFilterFromEnvFilter(envFilter);

    if (environments != null) {
      Set<String> envIds = getEnvIdsByFilter(environments, envFilter);
      if (CollectionUtils.isEmpty(envIds)) {
        log.info("No environments matched the filter for app {}. Returning empty set of deployments", appId);
        return new HashSet<>();
      }
    }

    return getWorkflowIdsByFilter(workflows, environments, workflowFilter);
  }

  private WorkflowFilter getWorkflowFilterFromEnvFilter(EnvFilter envFilter) {
    envFilter = getDefaultEnvFilterIfNull(envFilter);

    // Construct workflow filter since we also want to include templates to deployments
    WorkflowFilter workflowFilter = new WorkflowFilter();

    Set<String> workflowFilterTypes = Sets.newHashSet();

    final EnvFilter envFilterFinal = envFilter;

    envFilter.getFilterTypes().forEach(filterType -> {
      workflowFilterTypes.add(filterType);
      if (filterType.equals(EnvFilter.FilterType.SELECTED)) {
        workflowFilter.setIds(envFilterFinal.getIds());
      }
    });

    workflowFilterTypes.add(WorkflowFilter.FilterType.TEMPLATES);
    workflowFilter.setFilterTypes(workflowFilterTypes);

    return workflowFilter;
  }

  private Map<String, Workflow> fillWorkflowCache(Map<String, Workflow> workflowCache, List<Base> pipelines) {
    Set<String> newWorkflowIds = new HashSet<>();
    // Find all new workflow ids that are not present in the cache but present in the list of pipelines.
    pipelines.forEach(p -> {
      Pipeline pipeline = (Pipeline) p;
      if (pipeline.getPipelineStages() == null) {
        return;
      }

      pipeline.getPipelineStages().forEach(pipelineStage -> {
        if (pipelineStage == null || pipelineStage.getPipelineStageElements() == null) {
          return;
        }

        pipelineStage.getPipelineStageElements().forEach(pipelineStageElement -> {
          final Map<String, Object> pipelineStageElementProperties = pipelineStageElement.getProperties();
          if (pipelineStageElementProperties == null
              || pipelineStageElementProperties.get(EnvStateKeys.workflowId) == null) {
            return;
          }

          String workflowId = (String) pipelineStageElement.getProperties().get(EnvStateKeys.workflowId);
          if (workflowCache == null || !workflowCache.containsKey(workflowId)) {
            newWorkflowIds.add(workflowId);
          }
        });
      });
    });

    // Return if no new workflow ids found. Cache has all the needed workflows.
    Map<String, Workflow> finalWorkflowCache = (workflowCache == null) ? new HashMap<>() : workflowCache;
    if (isEmpty(newWorkflowIds)) {
      return finalWorkflowCache;
    }

    // Fetch all the workflows in batch.
    List<Workflow> workflows = workflowService.listWorkflowsWithoutOrchestration(newWorkflowIds);
    if (isEmpty(workflows)) {
      return finalWorkflowCache;
    }

    // Update the workflowCache and return.
    for (Workflow workflow : workflows) {
      finalWorkflowCache.put(workflow.getUuid(), workflow);
    }
    return finalWorkflowCache;
  }

  private Multimap<String, Action> getPipelineIdsByFilter(List<Base> pipelines, List<Base> environments,
      EnvFilter envFilter, Multimap<String, Action> envActionMap, Set<Action> entityActionsFromCurrentPermission,
      Map<String, Workflow> workflowCache) {
    Multimap<String, Action> pipelineActionMap = HashMultimap.create();
    if (isEmpty(pipelines)) {
      return pipelineActionMap;
    }

    Set<String> envIds;
    if (isNotEmpty(environments)) {
      envIds = getEnvIdsByFilter(environments, envFilter);
      envIds.forEach(envId -> envActionMap.putAll(envId, entityActionsFromCurrentPermission));
    } else {
      envIds = Collections.emptySet();
    }

    final Map<String, Workflow> finalWorkflowCache = fillWorkflowCache(workflowCache, pipelines);
    Set<String> envIdsFromOtherPermissions = envActionMap.keySet();
    pipelines.forEach(p -> {
      Set<Action> entityActions = new HashSet<>(entityActionsFromCurrentPermission);
      boolean match;
      Pipeline pipeline = (Pipeline) p;
      if (pipeline.getPipelineStages() == null) {
        match = true;
      } else {
        match = pipeline.getPipelineStages().stream().allMatch(pipelineStage
            -> pipelineStage != null && pipelineStage.getPipelineStageElements() != null
                && pipelineStage.getPipelineStageElements().stream().allMatch(pipelineStageElement -> {
                     Pair<String, Boolean> pair = resolveEnvIdForPipelineStageElement(
                         pipeline.getAppId(), pipelineStageElement, finalWorkflowCache);
                     String envId = pair.getLeft();
                     if (isBlank(envId)) {
                       return pair.getRight();
                     }

                     if (envIds.contains(envId)) {
                       return true;
                     } else if (envIdsFromOtherPermissions.contains(envId)) {
                       entityActions.retainAll(envActionMap.get(envId));
                       return true;
                     }

                     return false;
                   }));
      }

      if (match) {
        pipelineActionMap.putAll(pipeline.getUuid(), entityActions);
      }
    });
    return pipelineActionMap;
  }

  private Multimap<String, Action> getPipelineActionMapByEntityFilter(
      List<Base> pipelines, GenericEntityFilter pipelineFilter, Set<Action> entityActions) {
    Multimap<String, Action> pipelineActionMap = HashMultimap.create();
    if (isEmpty(pipelines)) {
      return pipelineActionMap;
    }

    if (FilterType.ALL.equals(pipelineFilter.getFilterType())) {
      pipelines.forEach(p -> { pipelineActionMap.putAll(p.getUuid(), entityActions); });
    } else if (SELECTED.equals(pipelineFilter.getFilterType())) {
      pipelines.forEach(p -> {
        if (pipelineFilter.getIds().contains(p.getUuid())) {
          pipelineActionMap.putAll(p.getUuid(), entityActions);
        }
      });
    } else {
      String msg = "Unknown pipeline filter type: " + pipelineFilter.getFilterType();
      log.error(msg);
      throw new InvalidRequestException(msg);
    }

    return pipelineActionMap;
  }

  public boolean checkIfPipelineHasOnlyGivenEnvs(Pipeline pipeline, Set<String> allowedEnvIds) {
    if (isEmpty(pipeline.getPipelineStages())) {
      return true;
    }

    Map<String, Workflow> workflowCache = fillWorkflowCache(new HashMap<>(), Collections.singletonList(pipeline));
    return pipeline.getPipelineStages().stream().allMatch(pipelineStage
        -> pipelineStage != null && pipelineStage.getPipelineStageElements() != null
            && pipelineStage.getPipelineStageElements().stream().allMatch(pipelineStageElement -> {
                 Pair<String, Boolean> pair =
                     resolveEnvIdForPipelineStageElement(pipeline.getAppId(), pipelineStageElement, workflowCache);
                 String envId = pair.getLeft();
                 if (isBlank(envId)) {
                   return pair.getRight();
                 }

                 return isNotEmpty(allowedEnvIds) && allowedEnvIds.contains(envId);
               }));
  }

  private Pair<String, Boolean> resolveEnvIdForPipelineStageElement(
      String appId, PipelineStageElement pipelineStageElement, Map<String, Workflow> workflowCache) {
    if (pipelineStageElement.getType().equals(StateType.APPROVAL.name())) {
      return ImmutablePair.of(null, Boolean.TRUE);
    }

    final Map<String, Object> pipelineStageElementProperties = pipelineStageElement.getProperties();
    if (pipelineStageElementProperties == null || pipelineStageElementProperties.get(EnvStateKeys.workflowId) == null) {
      return ImmutablePair.of(null, Boolean.FALSE);
    }

    String envId = resolveEnvId(appId, pipelineStageElement, workflowCache);
    if (envId == null || (pipelineStageElement.checkDisableAssertion() && isEmpty(envId))
        || ManagerExpressionEvaluator.matchesVariablePattern(envId)) {
      return ImmutablePair.of(null, Boolean.TRUE);
    }

    return ImmutablePair.of(envId, Boolean.FALSE);
  }

  private String resolveEnvId(
      String appId, PipelineStageElement pipelineStageElement, Map<String, Workflow> workflowCache) {
    String workflowId = (String) pipelineStageElement.getProperties().get(EnvStateKeys.workflowId);
    Workflow workflow;
    if (workflowCache.containsKey(workflowId)) {
      workflow = workflowCache.get(workflowId);
    } else {
      log.info("Workflow not found in cache: {}", workflowId);
      workflow = workflowService.readWorkflowWithoutOrchestration(appId, workflowId);
      if (workflow == null) {
        return null;
      }
      workflowCache.put(workflowId, workflow);
    }
    return workflowServiceHelper.obtainEnvIdWithoutOrchestration(workflow, pipelineStageElement.getWorkflowVariables());
  }

  public boolean isEnvTemplatized(Workflow workflow) {
    List<TemplateExpression> templateExpressions = workflow.getTemplateExpressions();
    if (CollectionUtils.isNotEmpty(templateExpressions)) {
      return templateExpressions.stream()
          .filter(templateExpression -> templateExpression.getFieldName().equals("envId"))
          .findFirst()
          .isPresent();
    }
    return false;
  }

  private boolean hasEnvSelectedType(EnvFilter envFilter) {
    Set<String> filterTypes = envFilter.getFilterTypes();
    if (isEmpty(filterTypes)) {
      return false;
    }

    return filterTypes.stream()
        .filter(filterType -> filterType.equals(EnvFilter.FilterType.SELECTED))
        .findFirst()
        .isPresent();
  }

  private void setAppPermissionMap(UserPermissionInfo userPermissionInfo) {
    Map<String, AppPermissionSummary> fromAppPermissionSummaryMap = userPermissionInfo.getAppPermissionMapInternal();
    Map<String, AppPermissionSummaryForUI> toAppPermissionSummaryMap = new HashMap<>();
    if (MapUtils.isEmpty(fromAppPermissionSummaryMap)) {
      userPermissionInfo.setAppPermissionMap(toAppPermissionSummaryMap);
    }

    fromAppPermissionSummaryMap.forEach((key, summary) -> {
      AppPermissionSummaryForUI toAppPermissionSummary = convertAppSummaryToAppSummaryForUI(summary);
      toAppPermissionSummaryMap.put(key, toAppPermissionSummary);
    });

    userPermissionInfo.setAppPermissionMap(toAppPermissionSummaryMap);
  }

  private AppPermissionSummaryForUI convertAppSummaryToAppSummaryForUI(AppPermissionSummary fromSummary) {
    AppPermissionSummaryForUIBuilder toAppPermissionSummaryBuilder =
        AppPermissionSummaryForUI.builder()
            .canCreateService(fromSummary.isCanCreateService())
            .canCreateProvisioner(fromSummary.isCanCreateProvisioner())
            .canCreateEnvironment(fromSummary.isCanCreateEnvironment())
            .canCreateWorkflow(fromSummary.isCanCreateWorkflow())
            .canCreatePipeline(fromSummary.isCanCreatePipeline())
            .canCreateTemplate(fromSummary.isCanCreateTemplate())
            .servicePermissions(convertActionEntityIdMapToEntityActionMap(fromSummary.getServicePermissions()))
            .provisionerPermissions(convertActionEntityIdMapToEntityActionMap(fromSummary.getProvisionerPermissions()))
            .envPermissions(convertActionEnvMapToEnvActionMap(fromSummary.getEnvPermissions()))
            .workflowPermissions(convertActionEntityIdMapToEntityActionMap(fromSummary.getWorkflowPermissions()))
            .pipelinePermissions(convertActionEntityIdMapToEntityActionMap(fromSummary.getPipelinePermissions()))
            .deploymentPermissions(convertActionEntityIdMapToEntityActionMap(fromSummary.getDeploymentPermissions()))
            .templatePermissions(convertActionEntityIdMapToEntityActionMap(fromSummary.getTemplatePermissions()));
    // todo(abhinav): think of pipeline env filter here.
    return toAppPermissionSummaryBuilder.build();
  }

  /**
   * Transforms the Map -> (Action, Set of EntityIds) to Map -> (EntityId, Set of Actions)
   * The second format is organized in the way optimized for UI consumption.
   * @param fromMap
   * @return
   */
  private Map<String, Set<Action>> convertActionEntityIdMapToEntityActionMap(Map<Action, Set<String>> fromMap) {
    Map<String, Set<Action>> toMap = new HashMap<>();
    if (isEmpty(fromMap)) {
      return null;
    }

    fromMap.forEach((action, entitySet) -> {
      if (CollectionUtils.isNotEmpty(entitySet)) {
        entitySet.forEach(entityId -> {
          Set<Action> actionSet = toMap.get(entityId);
          if (actionSet == null) {
            actionSet = new HashSet<>();
          }
          actionSet.add(action);
          toMap.put(entityId, actionSet);
        });
      }
    });

    return toMap;
  }

  private Map<String, Set<Action>> convertActionEnvMapToEnvActionMap(Map<Action, Set<EnvInfo>> fromMap) {
    Map<String, Set<Action>> toMap = new HashMap<>();
    if (isEmpty(fromMap)) {
      return toMap;
    }

    fromMap.forEach((action, envInfoSet) -> {
      if (CollectionUtils.isNotEmpty(envInfoSet)) {
        envInfoSet.forEach(envInfo -> {
          Set<Action> actionSet = toMap.get(envInfo.getEnvId());
          if (actionSet == null) {
            actionSet = new HashSet<>();
          }
          actionSet.add(action);
          toMap.put(envInfo.getEnvId(), actionSet);
        });
      }
    });

    return toMap;
  }

  public UserGroup buildDefaultAdminUserGroup(String accountId, User user) {
    AccountPermissions accountPermissions =
        AccountPermissions.builder().permissions(getDefaultEnabledAccountPermissions()).build();

    Set<AppPermission> appPermissions = Sets.newHashSet();
    AppPermission appPermission = AppPermission.builder()
                                      .actions(getAllActions())
                                      .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
                                      .permissionType(PermissionType.ALL_APP_ENTITIES)
                                      .build();
    appPermissions.add(appPermission);

    NotificationSettings notificationSettings =
        new NotificationSettings(true, true, Collections.emptyList(), SlackNotificationSetting.emptyConfig(), "", "");

    UserGroupBuilder userGroupBuilder = UserGroup.builder()
                                            .accountId(accountId)
                                            .name(DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME)
                                            .isDefault(true)
                                            .accountPermissions(accountPermissions)
                                            .appPermissions(appPermissions)
                                            .notificationSettings(notificationSettings)
                                            .description(UserGroupServiceImpl.DEFAULT_USER_GROUP_DESCRIPTION);

    if (user != null) {
      userGroupBuilder.memberIds(asList(user.getUuid()));
    }

    return userGroupBuilder.build();
  }

  public UserGroup buildReadOnlyUserGroup(String accountId, User user, String userGroupName) {
    Set<AppPermission> appPermissions = Sets.newHashSet();
    AppPermission appPermission = AppPermission.builder()
                                      .actions(Sets.newHashSet(Action.READ))
                                      .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
                                      .permissionType(PermissionType.ALL_APP_ENTITIES)
                                      .build();
    appPermissions.add(appPermission);
    AccountPermissions accountPermissions =
        AccountPermissions.builder().permissions(Sets.newHashSet(CE_VIEWER)).build();

    UserGroupBuilder userGroupBuilder = UserGroup.builder()
                                            .accountId(accountId)
                                            .name(userGroupName)
                                            .isDefault(true)
                                            .appPermissions(appPermissions)
                                            .accountPermissions(accountPermissions)
                                            .description("Default account admin user group");
    if (user != null) {
      userGroupBuilder.memberIds(asList(user.getUuid()));
    }

    return userGroupBuilder.build();
  }

  private UserGroup buildSupportUserGroup(
      String accountId, String envFilterType, String userGroupName, String description, boolean isDefault) {
    // For Account level permissions, only add AUDIT_VIEW permission
    AccountPermissions accountPermissions =
        AccountPermissions.builder().permissions(Sets.newHashSet(AUDIT_VIEWER, CE_VIEWER)).build();

    Set<Action> actions = getAllNonDeploymentActions();
    Set<AppPermission> appPermissions = Sets.newHashSet();
    AppPermission svcPermission = AppPermission.builder()
                                      .actions(actions)
                                      .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
                                      .entityFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build())
                                      .permissionType(PermissionType.SERVICE)
                                      .build();
    appPermissions.add(svcPermission);

    AppPermission provisionerPermission =
        AppPermission.builder()
            .actions(actions)
            .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
            .entityFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build())
            .permissionType(PermissionType.PROVISIONER)
            .build();
    appPermissions.add(provisionerPermission);

    AppPermission envPermission = AppPermission.builder()
                                      .actions(actions)
                                      .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
                                      .entityFilter(new EnvFilter(null, Sets.newHashSet(envFilterType)))
                                      .permissionType(PermissionType.ENV)
                                      .build();
    appPermissions.add(envPermission);

    AppPermission workflowPermission =
        AppPermission.builder()
            .actions(actions)
            .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
            .entityFilter(new WorkflowFilter(null, Sets.newHashSet(envFilterType, WorkflowFilter.FilterType.TEMPLATES)))
            .permissionType(PermissionType.WORKFLOW)
            .build();
    appPermissions.add(workflowPermission);

    AppPermission deploymentPermission =
        AppPermission.builder()
            .actions(Sets.newHashSet(Action.READ, Action.EXECUTE_WORKFLOW, Action.EXECUTE_PIPELINE,
                Action.EXECUTE_WORKFLOW_ROLLBACK, Action.ABORT_WORKFLOW))
            .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
            .entityFilter(new EnvFilter(null, Sets.newHashSet(envFilterType)))
            .permissionType(PermissionType.DEPLOYMENT)
            .build();
    appPermissions.add(deploymentPermission);

    AppPermission pipelinePermission = AppPermission.builder()
                                           .actions(actions)
                                           .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
                                           .entityFilter(new EnvFilter(null, Sets.newHashSet(envFilterType)))
                                           .permissionType(PermissionType.PIPELINE)
                                           .build();
    appPermissions.add(pipelinePermission);

    AppPermission templatePermission =
        AppPermission.builder()
            .actions(actions)
            .appFilter(AppFilter.builder().filterType(AppFilter.FilterType.ALL).build())
            .entityFilter(GenericEntityFilter.builder().filterType(FilterType.ALL).build())
            .permissionType(APP_TEMPLATE)
            .build();
    appPermissions.add(templatePermission);

    UserGroupBuilder userGroupBuilder = UserGroup.builder()
                                            .accountId(accountId)
                                            .name(userGroupName)
                                            .isDefault(isDefault)
                                            .appPermissions(appPermissions)
                                            .accountPermissions(accountPermissions)
                                            .description(description);

    return userGroupBuilder.build();
  }

  public UserGroup buildProdSupportUserGroup(String accountId) {
    return buildSupportUserGroup(
        accountId, PROD, DEFAULT_PROD_SUPPORT_USER_GROUP_NAME, DEFAULT_PROD_SUPPORT_USER_GROUP_DESCRIPTION, true);
  }

  public UserGroup buildNonProdSupportUserGroup(String accountId) {
    return buildSupportUserGroup(accountId, NON_PROD, DEFAULT_NON_PROD_SUPPORT_USER_GROUP_NAME,
        DEFAULT_NON_PROD_SUPPORT_USER_GROUP_DESCRIPTION, true);
  }

  public Set<PermissionType> getAllAccountPermissions() {
    return Sets.newHashSet(USER_PERMISSION_MANAGEMENT, ACCOUNT_MANAGEMENT, MANAGE_APPLICATIONS, TEMPLATE_MANAGEMENT,
        USER_PERMISSION_READ, AUDIT_VIEWER, MANAGE_TAGS, MANAGE_ACCOUNT_DEFAULTS, CE_ADMIN, CE_VIEWER,
        MANAGE_CLOUD_PROVIDERS, MANAGE_CONNECTORS, MANAGE_APPLICATION_STACKS, MANAGE_DELEGATES,
        MANAGE_ALERT_NOTIFICATION_RULES, MANAGE_DELEGATE_PROFILES, MANAGE_CONFIG_AS_CODE, MANAGE_SECRETS,
        MANAGE_SECRET_MANAGERS, MANAGE_AUTHENTICATION_SETTINGS, MANAGE_IP_WHITELIST, MANAGE_DEPLOYMENT_FREEZES,
        MANAGE_PIPELINE_GOVERNANCE_STANDARDS, MANAGE_API_KEYS, MANAGE_CUSTOM_DASHBOARDS, CREATE_CUSTOM_DASHBOARDS,
        MANAGE_SSH_AND_WINRM, MANAGE_RESTRICTED_ACCESS, HIDE_NEXTGEN_BUTTON);
  }

  public Set<PermissionType> getDefaultEnabledAccountPermissions() {
    Set<PermissionType> allAccountPermissions = getAllAccountPermissions();
    Set<PermissionType> disabledPermissions = Sets.newHashSet(HIDE_NEXTGEN_BUTTON);
    return allAccountPermissions.stream()
        .filter(permission -> !disabledPermissions.contains(permission))
        .collect(Collectors.toSet());
  }

  private Set<Action> getAllActions() {
    return Sets.newHashSet(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE, Action.EXECUTE_WORKFLOW,
        Action.EXECUTE_WORKFLOW_ROLLBACK, Action.ABORT_WORKFLOW, Action.EXECUTE_PIPELINE);
  }

  private Set<Action> getAllNonDeploymentActions() {
    return Sets.newHashSet(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE);
  }

  public void addUserToDefaultAccountAdminUserGroup(User user, Account account, boolean sendNotification) {
    if (account == null) {
      log.info("account is null, continuing....");
      return;
    }

    String accountId = account.getUuid();

    PageRequest<UserGroup> pageRequest = aPageRequest()
                                             .addFilter("accountId", EQ, accountId)
                                             .addFilter("name", EQ, DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME)
                                             .build();
    PageResponse<UserGroup> userGroups = userGroupService.list(accountId, pageRequest, true, null, null);
    UserGroup userGroup = null;
    if (CollectionUtils.isNotEmpty(userGroups)) {
      userGroup = userGroups.get(0);
    }

    if (userGroup == null) {
      log.info("UserGroup doesn't exist in account {}", accountId);
      userGroup = buildDefaultAdminUserGroup(accountId, user);

      UserGroup savedUserGroup = userGroupService.save(userGroup);
      log.info("Created default user group {} for account {}", savedUserGroup.getUuid(), accountId);
    } else {
      log.info("UserGroup already exists in account {}", accountId);
      log.info(
          "Checking if user {} exists in user group {} in account {}", user.getName(), userGroup.getUuid(), accountId);

      List<String> memberIds = userGroup.getMemberIds();
      boolean userMemberOfGroup;
      if (isEmpty(memberIds)) {
        userMemberOfGroup = false;
      } else {
        userMemberOfGroup = memberIds.contains(user.getUuid());
      }

      if (!userMemberOfGroup) {
        log.info("User {} is not part of the user group in account {}, adding now ", user.getName(), accountId);
        List<String> members = userGroup.getMemberIds();
        if (members == null) {
          members = new ArrayList<>();
        }
        members.add(user.getUuid());
        userGroup.setMemberIds(members);

        userGroupService.updateMembers(userGroup, sendNotification, true);

        log.info("User {} is added to the user group in account {}", user.getName(), accountId);
      }
    }
  }

  public void createDefaultUserGroups(Account account) {
    UserGroup defaultAdminUserGroup = buildDefaultAdminUserGroup(account.getUuid(), null);
    userGroupService.save(defaultAdminUserGroup);

    // We don't create prodSupportUserGroup and nonProdSupportUserGroup for
    // Community account
    if (!account.isCommunity()) {
      // By default, we don't associate any users to the support groups
      UserGroup prodSupportUserGroup = buildProdSupportUserGroup(account.getUuid());
      userGroupService.save(prodSupportUserGroup);
      UserGroup nonProdSupportUserGroup = buildNonProdSupportUserGroup(account.getUuid());
      userGroupService.save(nonProdSupportUserGroup);
    }
  }

  public void authorizeAccountPermission(List<PermissionAttribute> requiredPermissionAttributes) {
    User user = UserThreadLocal.get();
    if (user != null) {
      UserRequestContext userRequestContext = user.getUserRequestContext();
      // UserRequestContext is null if rbac enabled is false
      if (userRequestContext != null) {
        authorizeAccountPermission(userRequestContext, requiredPermissionAttributes);
      }
    }
  }

  public void authorizeAccountPermission(
      UserRequestContext userRequestContext, List<PermissionAttribute> requiredPermissionAttributes) {
    UserPermissionInfo userPermissionInfo = userRequestContext.getUserPermissionInfo();
    if (userPermissionInfo == null) {
      throw new InvalidRequestException(USER_NOT_AUTHORIZED, USER);
    }

    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    if (accountPermissionSummary == null) {
      throw new InvalidRequestException(USER_NOT_AUTHORIZED, USER);
    }

    Set<PermissionType> accountPermissions = accountPermissionSummary.getPermissions();
    if (accountPermissions == null) {
      throw new InvalidRequestException(USER_NOT_AUTHORIZED, USER);
    }

    if (!isAuthorized(requiredPermissionAttributes, accountPermissions)) {
      log.info("{} : required => {} | permissions => {}", USER_NOT_AUTHORIZED,
          requiredPermissionAttributes.stream()
              .map(PermissionAttribute::getPermissionType)
              .collect(Collectors.toList()),
          accountPermissions);
      throw new InvalidRequestException(USER_NOT_AUTHORIZED, USER);
    }
  }

  private boolean isAuthorized(List<PermissionAttribute> permissionAttributes, Set<PermissionType> accountPermissions) {
    return permissionAttributes.stream().anyMatch(
        permissionAttribute -> accountPermissions.contains(permissionAttribute.getPermissionType()));
  }

  public void authorizeScimApi(ContainerRequestContext containerRequestContext) {
    String apiKey = getToken(containerRequestContext.getHeaderString("Authorization"), "Bearer");
    String accountId = getRequestParamFromContext("accountId", containerRequestContext.getUriInfo().getPathParameters(),
        containerRequestContext.getUriInfo().getQueryParameters());

    ApiKeyEntry apiKeyEntry = apiKeyService.getByKey(apiKey, accountId);
    if (apiKeyEntry == null) {
      throw new InvalidRequestException(USER_NOT_AUTHORIZED, USER);
    }

    UserPermissionInfo userPermissionInfo = apiKeyService.getApiKeyPermissions(apiKeyEntry, accountId);

    log.info("SCIM: Permissions {}", userPermissionInfo.getAccountPermissionSummary().getPermissions());
    if (!userPermissionInfo.getAccountPermissionSummary().getPermissions().contains(USER_PERMISSION_MANAGEMENT)) {
      throw new InvalidRequestException(USER_NOT_AUTHORIZED, USER);
    }
  }

  private String getToken(String authorizationHeader, String prefix) {
    if (!authorizationHeader.contains(prefix)) {
      throw new InvalidRequestException(USER_NOT_AUTHORIZED, USER);
    }
    return authorizationHeader.substring(prefix.length()).trim();
  }

  private String getRequestParamFromContext(
      String key, MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.getFirst(key) != null ? queryParameters.getFirst(key) : pathParameters.getFirst(key);
  }
}
