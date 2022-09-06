/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.userGroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.graphql.schema.type.permissions.QLAccountPermissionType.ADMINISTER_CE;
import static software.wings.graphql.schema.type.permissions.QLAccountPermissionType.ADMINISTER_OTHER_ACCOUNT_FUNCTIONS;
import static software.wings.graphql.schema.type.permissions.QLAccountPermissionType.CREATE_AND_DELETE_APPLICATION;
import static software.wings.graphql.schema.type.permissions.QLAccountPermissionType.MANAGE_TEMPLATE_LIBRARY;
import static software.wings.graphql.schema.type.permissions.QLAccountPermissionType.MANAGE_USERS_AND_GROUPS;
import static software.wings.graphql.schema.type.permissions.QLAccountPermissionType.READ_USERS_AND_GROUPS;
import static software.wings.graphql.schema.type.permissions.QLAccountPermissionType.VIEW_AUDITS;
import static software.wings.graphql.schema.type.permissions.QLAccountPermissionType.VIEW_CE;
import static software.wings.security.PermissionAttribute.Action.ABORT_WORKFLOW;
import static software.wings.security.PermissionAttribute.Action.CREATE;
import static software.wings.security.PermissionAttribute.Action.DELETE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_PIPELINE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_WORKFLOW;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_WORKFLOW_ROLLBACK;
import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.Action.UPDATE;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.ALLOW_DEPLOYMENTS_DURING_FREEZE;
import static software.wings.security.PermissionAttribute.PermissionType.ALL_APP_ENTITIES;
import static software.wings.security.PermissionAttribute.PermissionType.APP_TEMPLATE;
import static software.wings.security.PermissionAttribute.PermissionType.AUDIT_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CE_ADMIN;
import static software.wings.security.PermissionAttribute.PermissionType.CE_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CREATE_CUSTOM_DASHBOARDS;
import static software.wings.security.PermissionAttribute.PermissionType.DEPLOYMENT;
import static software.wings.security.PermissionAttribute.PermissionType.ENV;
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
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS;
import static software.wings.security.PermissionAttribute.PermissionType.PIPELINE;
import static software.wings.security.PermissionAttribute.PermissionType.PROVISIONER;
import static software.wings.security.PermissionAttribute.PermissionType.SERVICE;
import static software.wings.security.PermissionAttribute.PermissionType.TEMPLATE_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;
import static software.wings.security.PermissionAttribute.PermissionType.VIEW_USER_AND_USER_GROUPS_AND_API_KEYS;
import static software.wings.security.PermissionAttribute.PermissionType.WORKFLOW;

import static org.elasticsearch.common.util.set.Sets.newHashSet;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.application.AppFilterController;
import software.wings.graphql.schema.type.QLAppFilter;
import software.wings.graphql.schema.type.QLEnvFilterType;
import software.wings.graphql.schema.type.permissions.QLAccountPermissionType;
import software.wings.graphql.schema.type.permissions.QLAccountPermissions;
import software.wings.graphql.schema.type.permissions.QLActions;
import software.wings.graphql.schema.type.permissions.QLAppPermission;
import software.wings.graphql.schema.type.permissions.QLAppPermission.QLAppPermissionBuilder;
import software.wings.graphql.schema.type.permissions.QLDeploymentFilterType;
import software.wings.graphql.schema.type.permissions.QLDeploymentPermissions;
import software.wings.graphql.schema.type.permissions.QLEnvPermissions;
import software.wings.graphql.schema.type.permissions.QLExecutableElementFilterInput;
import software.wings.graphql.schema.type.permissions.QLExecutableElementType;
import software.wings.graphql.schema.type.permissions.QLGroupPermissions;
import software.wings.graphql.schema.type.permissions.QLPermissionType;
import software.wings.graphql.schema.type.permissions.QLPermissionsFilterType;
import software.wings.graphql.schema.type.permissions.QLPipelineFilterType;
import software.wings.graphql.schema.type.permissions.QLPipelinePermissions;
import software.wings.graphql.schema.type.permissions.QLProivionerPermissions;
import software.wings.graphql.schema.type.permissions.QLServicePermissions;
import software.wings.graphql.schema.type.permissions.QLTemplatePermissions;
import software.wings.graphql.schema.type.permissions.QLUserGroupPermissions;
import software.wings.graphql.schema.type.permissions.QLWorkflowFilterType;
import software.wings.graphql.schema.type.permissions.QLWorkflowPermissions;
import software.wings.security.AppFilter;
import software.wings.security.EnvFilter;
import software.wings.security.ExecutableElementsFilter;
import software.wings.security.Filter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.WorkflowFilter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UserGroupPermissionsController {
  @Inject AppFilterController appFilterController;

  private static final String SELECTED = "SELECTED";
  private static final String NON_PROD = "NON_PROD";
  private static final String PROD = "PROD";
  private static final String ALL = "ALL";
  private static final String TEMPLATES = "TEMPLATES";

  UserGroup addAppPermissionToUserGroupObject(UserGroup userGroup, QLAppPermission appPermissions) {
    AppPermission appPermissionEntity = convertToAppPermissionEntity(appPermissions);
    // Adding this app Permission to the set
    Set<AppPermission> appPermissionSet =
        userGroup.getAppPermissions() != null ? userGroup.getAppPermissions() : newHashSet();
    if (appPermissionSet.contains(appPermissionEntity)) {
      throw new DuplicateFieldException("The application permission already exists in the user group");
    }
    appPermissionSet.add(appPermissionEntity);
    userGroup.setAppPermissions(appPermissionSet);
    return userGroup;
  }

  UserGroup addAccountPermissionToUserGroupObject(UserGroup userGroup, QLAccountPermissionType accountPermissionType) {
    // Adding the account Permissions
    PermissionType newAccountPermission = mapAccountPermissions(accountPermissionType);
    Set<PermissionType> permissions =
        userGroup.getAccountPermissions() != null ? userGroup.getAccountPermissions().getPermissions() : newHashSet();
    // Adding new permission to this set
    if (permissions.contains(newAccountPermission)) {
      throw new DuplicateFieldException("The account permission already exists in the user group");
    }
    permissions.add(newAccountPermission);
    userGroup.setAccountPermissions(AccountPermissions.builder().permissions(permissions).build());
    return userGroup;
  }

  /*
   *   Utility functions to convert GraphQL InputTypes to the userGroup Types of portal
   */
  // user Given Account Permissions to the portal permissionType
  private PermissionType mapAccountPermissions(QLAccountPermissionType permissionType) {
    switch (permissionType) {
      case CREATE_AND_DELETE_APPLICATION:
        return MANAGE_APPLICATIONS;
      case READ_USERS_AND_GROUPS:
        return USER_PERMISSION_READ;
      case MANAGE_USERS_AND_GROUPS:
        return USER_PERMISSION_MANAGEMENT;
      case MANAGE_TEMPLATE_LIBRARY:
        return TEMPLATE_MANAGEMENT;
      case ADMINISTER_OTHER_ACCOUNT_FUNCTIONS:
        return ACCOUNT_MANAGEMENT;
      case VIEW_AUDITS:
        return AUDIT_VIEWER;
      case MANAGE_TAGS:
        return PermissionType.MANAGE_TAGS;
      case MANAGE_ACCOUNT_DEFAULTS:
        return PermissionType.MANAGE_ACCOUNT_DEFAULTS;
      case ADMINISTER_CE:
        return CE_ADMIN;
      case VIEW_CE:
        return CE_VIEWER;
      case MANAGE_CLOUD_PROVIDERS:
        return MANAGE_CLOUD_PROVIDERS;
      case MANAGE_CONNECTORS:
        return MANAGE_CONNECTORS;
      case MANAGE_APPLICATION_STACKS:
        return MANAGE_APPLICATION_STACKS;
      case MANAGE_DELEGATES:
        return MANAGE_DELEGATES;
      case MANAGE_ALERT_NOTIFICATION_RULES:
        return MANAGE_ALERT_NOTIFICATION_RULES;
      case MANAGE_DELEGATE_PROFILES:
        return MANAGE_DELEGATE_PROFILES;
      case MANAGE_CONFIG_AS_CODE:
        return MANAGE_CONFIG_AS_CODE;
      case MANAGE_SECRETS:
        return MANAGE_SECRETS;
      case MANAGE_SSH_AND_WINRM:
        return MANAGE_SSH_AND_WINRM;
      case MANAGE_SECRET_MANAGERS:
        return MANAGE_SECRET_MANAGERS;
      case MANAGE_AUTHENTICATION_SETTINGS:
        return MANAGE_AUTHENTICATION_SETTINGS;
      case MANAGE_IP_WHITELIST:
        return MANAGE_IP_WHITELIST;
      case MANAGE_DEPLOYMENT_FREEZES:
        return MANAGE_DEPLOYMENT_FREEZES;
      case ALLOW_DEPLOYMENTS_DURING_FREEZE:
        return ALLOW_DEPLOYMENTS_DURING_FREEZE;
      case MANAGE_PIPELINE_GOVERNANCE_STANDARDS:
        return MANAGE_PIPELINE_GOVERNANCE_STANDARDS;
      case VIEW_USER_AND_USER_GROUPS_AND_API_KEYS:
        return VIEW_USER_AND_USER_GROUPS_AND_API_KEYS;
      case MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS:
        return MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS;
      case MANAGE_API_KEYS:
        return MANAGE_API_KEYS;
      case CREATE_CUSTOM_DASHBOARDS:
        return CREATE_CUSTOM_DASHBOARDS;
      case MANAGE_CUSTOM_DASHBOARDS:
        return MANAGE_CUSTOM_DASHBOARDS;
      case MANAGE_RESTRICTED_ACCESS:
        return MANAGE_RESTRICTED_ACCESS;

      default:
        log.error("Invalid Account Permission Type {} given by the user", permissionType.toString());
    }
    throw new InvalidRequestException("Invalid Account Permission Type Given by the user");
  }

  // Map the userGivenActions to the portal Actions
  private Action mapAppActions(QLActions action) {
    switch (action) {
      case CREATE:
        return CREATE; // Change this later on
      case READ:
        return READ;
      case UPDATE:
        return UPDATE;
      case DELETE:
        return DELETE;
      case EXECUTE:
        return EXECUTE;
      case EXECUTE_WORKFLOW:
        return EXECUTE_WORKFLOW;
      case EXECUTE_PIPELINE:
        return EXECUTE_PIPELINE;
      case ROLLBACK_WORKFLOW:
        return EXECUTE_WORKFLOW_ROLLBACK;
      case ABORT_WORKFLOW:
        return ABORT_WORKFLOW;
      default:
        log.error("Invalid Action {} given by the user", action.toString());
    }
    throw new InvalidRequestException("Invalid action given by the user");
  }

  // Map the permissions given by the users to the portal permissions
  private PermissionType mapToApplicationPermission(QLPermissionType permissionType) {
    switch (permissionType) {
      case ALL:
        return ALL_APP_ENTITIES;
      case SERVICE:
        return SERVICE;
      case ENV:
        return ENV;
      case WORKFLOW:
        return WORKFLOW;
      case PIPELINE:
        return PIPELINE;
      case DEPLOYMENT:
        return DEPLOYMENT;
      case PROVISIONER:
        return PROVISIONER;
      case TEMPLATE:
        return APP_TEMPLATE;
      default:
        log.error("Invalid Permission Type {} given by the user", permissionType.toString());
    }
    throw new InvalidRequestException("Invalid Permission Type given by the user");
  }

  private EnvFilter createEnvFilter(QLEnvPermissions envPermissions) {
    Set<String> filterTypes = new HashSet<>();
    if (isNotEmpty(envPermissions.getEnvIds()) && isNotEmpty(envPermissions.getFilterTypes())) {
      throw new InvalidRequestException("Cannot set both envIds and filterTypes in environment filter");
    }
    if (isNotEmpty(envPermissions.getEnvIds())) {
      filterTypes.add(SELECTED);
    } else {
      if (envPermissions.getFilterTypes() != null) {
        if (envPermissions.getFilterTypes().contains(QLEnvFilterType.PRODUCTION_ENVIRONMENTS)) {
          filterTypes.add(PROD);
        }
        if (envPermissions.getFilterTypes().contains(QLEnvFilterType.NON_PRODUCTION_ENVIRONMENTS)) {
          filterTypes.add(NON_PROD);
        }
      }
    }

    return EnvFilter.builder().ids(envPermissions.getEnvIds()).filterTypes(filterTypes).build();
  }

  private Filter createWorkflowFilter(QLWorkflowPermissions workflowPermissions) {
    if (isNotEmpty(workflowPermissions.getEnvIds()) && isNotEmpty(workflowPermissions.getFilterTypes())) {
      throw new InvalidRequestException("Cannot set both envIds and filterTypes in workflow filter");
    }
    if (isNotEmpty(workflowPermissions.getWorkflowIds()) && isNotEmpty(workflowPermissions.getFilterTypes())) {
      throw new InvalidRequestException("Cannot set both workflowIds and filterTypes in workflow filter");
    }
    if (isNotEmpty(workflowPermissions.getWorkflowIds()) && isNotEmpty(workflowPermissions.getEnvIds())) {
      throw new InvalidRequestException("Cannot set both workflowIds and envIds in workflow filter");
    }
    Set<String> filterTypes = new HashSet<>();
    if (isNotEmpty(workflowPermissions.getEnvIds()) || isNotEmpty(workflowPermissions.getWorkflowIds())) {
      filterTypes.add(SELECTED);
    } else {
      if (workflowPermissions.getFilterTypes() != null) {
        if (workflowPermissions.getFilterTypes().contains(QLWorkflowFilterType.PRODUCTION_WORKFLOWS)) {
          filterTypes.add(PROD);
        }
        if (workflowPermissions.getFilterTypes().contains(QLWorkflowFilterType.NON_PRODUCTION_WORKFLOWS)) {
          filterTypes.add(NON_PROD);
        }
        if (workflowPermissions.getFilterTypes().contains(QLWorkflowFilterType.WORKFLOW_TEMPLATES)) {
          filterTypes.add(TEMPLATES);
        }
        if (workflowPermissions.getFilterTypes().contains(QLWorkflowFilterType.ALL_WORKFLOWS)) {
          filterTypes.add(ALL);
        }
      }
    }
    // If permission given via workflow ids or All Workflows, use Entity filter else Env Filter
    if (isNotEmpty(workflowPermissions.getWorkflowIds())) {
      return GenericEntityFilter.builder().filterType(SELECTED).ids(workflowPermissions.getWorkflowIds()).build();
    } else if (filterTypes.contains(ALL)) {
      return GenericEntityFilter.builder().filterType(ALL).build();
    }
    return new WorkflowFilter(workflowPermissions.getEnvIds(), filterTypes);
  }

  private Filter createDeploymentFilter(QLDeploymentPermissions deploymentPermissions) {
    if (isNotEmpty(deploymentPermissions.getEnvIds()) && isNotEmpty(deploymentPermissions.getFilterTypes())) {
      throw new InvalidRequestException("Cannot set both envIds and filterTypes in deployment filter");
    }
    Set<String> filterTypes = new HashSet<>();
    if (isNotEmpty(deploymentPermissions.getEnvIds())) {
      filterTypes.add(SELECTED);
    } else {
      if (deploymentPermissions.getFilterTypes() != null) {
        if (deploymentPermissions.getFilterTypes().contains(QLDeploymentFilterType.PRODUCTION_ENVIRONMENTS)) {
          filterTypes.add(PROD);
        }
        if (deploymentPermissions.getFilterTypes().contains(QLDeploymentFilterType.NON_PRODUCTION_ENVIRONMENTS)) {
          filterTypes.add(NON_PROD);
        }
      }
    }
    if (deploymentPermissions.getExecutableElementFilter() == null) {
      return EnvFilter.builder().ids(deploymentPermissions.getEnvIds()).filterTypes(filterTypes).build();
    } else {
      return createDeploymentFilterWithExecutableElement(filterTypes, deploymentPermissions);
    }
  }

  private Filter createDeploymentFilterWithExecutableElement(
      Set<String> filterTypes, QLDeploymentPermissions deploymentPermissions) {
    final QLExecutableElementFilterInput executableElementFilter = deploymentPermissions.getExecutableElementFilter();
    final QLExecutableElementType executableElementType = executableElementFilter.getExecutableElementType();
    String elementType = executableElementType == QLExecutableElementType.WORKFLOW
        ? ExecutableElementsFilter.FilterType.WORKFLOW
        : ExecutableElementsFilter.FilterType.PIPELINE;
    final QLPermissionsFilterType executableElementFilterType =
        executableElementFilter.getExecutableElementFilterType();
    if (executableElementFilterType != null && isNotEmpty(executableElementFilter.getIds())) {
      throw new InvalidRequestException("Cannot set both Ids and ALL in executable element filter");
    }
    String executableElementFilterTypeValue = executableElementFilterType == null
        ? GenericEntityFilter.FilterType.SELECTED
        : GenericEntityFilter.FilterType.ALL;
    return ExecutableElementsFilter.executableBuilder()
        .executableElementType(elementType)
        .filter(GenericEntityFilter.builder()
                    .filterType(executableElementFilterTypeValue)
                    .ids(executableElementFilter.getIds())
                    .build())
        .envIds(deploymentPermissions.getEnvIds())
        .envTypes(filterTypes)
        .build();
  }

  private Filter createPipelineFilter(QLPipelinePermissions pipelinePermissions) {
    if (isNotEmpty(pipelinePermissions.getEnvIds()) && isNotEmpty(pipelinePermissions.getFilterTypes())) {
      throw new InvalidRequestException("Cannot set both envIds and filterTypes in environment filter");
    }
    if (isNotEmpty(pipelinePermissions.getPipelineIds()) && isNotEmpty(pipelinePermissions.getFilterTypes())) {
      throw new InvalidRequestException("Cannot set both pipelineIds and filterTypes in Pipeline filter");
    }
    if (isNotEmpty(pipelinePermissions.getEnvIds()) && isNotEmpty(pipelinePermissions.getPipelineIds())) {
      throw new InvalidRequestException("Cannot set both envIds and pipelineIds in Pipeline filter");
    }
    Set<String> filterTypes = new HashSet<>();
    if (isNotEmpty(pipelinePermissions.getEnvIds()) || isNotEmpty(pipelinePermissions.getPipelineIds())) {
      filterTypes.add(SELECTED);
    } else {
      if (pipelinePermissions.getFilterTypes() != null) {
        if (pipelinePermissions.getFilterTypes().contains(QLPipelineFilterType.PRODUCTION_PIPELINES)) {
          filterTypes.add(PROD);
        }
        if (pipelinePermissions.getFilterTypes().contains(QLPipelineFilterType.NON_PRODUCTION_PIPELINES)) {
          filterTypes.add(NON_PROD);
        }
        if (pipelinePermissions.getFilterTypes().contains(QLPipelineFilterType.ALL_PIPELINES)) {
          filterTypes.add(ALL);
        }
      }
    }
    // If permission given via pipeline ids or All Pipelines, use Entity filter else Env based Filter
    if (isNotEmpty(pipelinePermissions.getPipelineIds())) {
      return GenericEntityFilter.builder().filterType(SELECTED).ids(pipelinePermissions.getPipelineIds()).build();
    } else if (filterTypes.contains(ALL)) {
      return GenericEntityFilter.builder().filterType(ALL).build();
    }
    return WorkflowFilter.builder().ids(pipelinePermissions.getEnvIds()).filterTypes(filterTypes).build();
  }

  private void addDeploymentPermissions(Set<QLActions> actionsList) {
    if (actionsList.contains(QLActions.EXECUTE)) {
      actionsList.add(QLActions.EXECUTE_WORKFLOW);
      actionsList.add(QLActions.EXECUTE_PIPELINE);
      actionsList.add(QLActions.ROLLBACK_WORKFLOW);
      actionsList.add(QLActions.ABORT_WORKFLOW);
    }
  }

  private AppPermission convertToAppPermissionEntity(QLAppPermission permission) {
    // Converting the GraphQL actions to portal actions
    Set<QLActions> actionsList = permission.getActions();
    addDeploymentPermissions(actionsList);
    Set<Action> actions = actionsList.stream().map(this::mapAppActions).collect(Collectors.toSet());
    // Change the graphQL permissionType to the portal permissionType enum
    QLPermissionType permissionType = permission.getPermissionType();
    PermissionType appPermissionType = mapToApplicationPermission(permissionType);
    // Create the applicationFilter for the permissions
    AppFilter appFilter = appFilterController.createAppFilter(permission.getApplications());
    Filter entityFilter;
    String filterType;
    switch (permissionType) {
      case ALL:
        entityFilter = null;
        break;
      case SERVICE:
        Set<String> serviceIds = permission.getServices().getServiceIds();
        if (serviceIds != null) {
          filterType = isNotEmpty(serviceIds) ? SELECTED : ALL;
        } else {
          filterType = ALL;
        }
        entityFilter = GenericEntityFilter.builder().filterType(filterType).ids(serviceIds).build();
        break;
      case ENV:
        entityFilter = createEnvFilter(permission.getEnvironments());
        break;
      case WORKFLOW:
        entityFilter = createWorkflowFilter(permission.getWorkflows());
        break;
      case DEPLOYMENT:
        entityFilter = createDeploymentFilter(permission.getDeployments());
        break;
      case PIPELINE:
        entityFilter = createPipelineFilter(permission.getPipelines());
        break;
      case TEMPLATE:
        Set<String> templateIds = permission.getTemplates().getTemplateIds();
        if (templateIds != null) {
          filterType = isNotEmpty(templateIds) ? SELECTED : ALL;
        } else {
          filterType = ALL;
        }
        entityFilter = GenericEntityFilter.builder().filterType(filterType).ids(templateIds).build();
        break;
      case PROVISIONER:
        Set<String> provisionerIds = permission.getProvisioners().getProvisionerIds();
        if (provisionerIds != null) {
          filterType = isNotEmpty(provisionerIds) ? SELECTED : ALL;
        } else {
          filterType = ALL;
        }
        entityFilter = GenericEntityFilter.builder().filterType(filterType).ids(provisionerIds).build();
        break;
      default:
        log.error("Invalid Application Permission Type {} given by the user", permissionType.toString());
        throw new InvalidRequestException("Invalid Application Permission type given by the user");
    }

    return AppPermission.builder()
        .permissionType(appPermissionType)
        .appFilter(appFilter)
        .entityFilter(entityFilter)
        .actions(actions)
        .build();
  }

  // Populate the AppPermission entity
  Set<AppPermission> populateUserGroupAppPermissionEntity(QLUserGroupPermissions permissions) {
    if (permissions == null) {
      return Collections.emptySet();
    }
    List<QLAppPermission> appPermissions = permissions.getAppPermissions();
    Set<AppPermission> userGroupAppPermissions = null;
    if (appPermissions != null) {
      userGroupAppPermissions =
          appPermissions.stream().map(this::convertToAppPermissionEntity).collect(Collectors.toSet());
    }
    return userGroupAppPermissions;
  }

  // Populate the AccountPermission entity
  AccountPermissions populateUserGroupAccountPermissionEntity(QLUserGroupPermissions permissions) {
    if (permissions == null || permissions.getAccountPermissions() == null) {
      return null;
    }

    Set<QLAccountPermissionType> accountPermissionsInput =
        permissions.getAccountPermissions().getAccountPermissionTypes();
    if (accountPermissionsInput == null) {
      return null;
    }
    if (accountPermissionsInput.contains(MANAGE_USERS_AND_GROUPS)
        && !accountPermissionsInput.contains(READ_USERS_AND_GROUPS)) {
      throw new InvalidRequestException(
          "The permission MANAGE_USERS_AND_GROUPS cannot be set without setting READ_USERS_AND_GROUPS");
    }
    Set<PermissionType> accountPermissions =
        accountPermissionsInput.stream().map(this::mapAccountPermissions).collect(Collectors.toSet());
    return AccountPermissions.builder().permissions(accountPermissions).build();
  }

  /*
   *   Utility functions to convert UserGroup Types to Graphql output
   */
  // user portal permissionType to Account Permissions output
  private QLAccountPermissionType mapAccountPermissionsToOutput(PermissionType permissionType) {
    switch (permissionType) {
      case MANAGE_APPLICATIONS:
      case APPLICATION_CREATE_DELETE:
        return CREATE_AND_DELETE_APPLICATION;
      case USER_PERMISSION_READ:
        return READ_USERS_AND_GROUPS;
      case USER_PERMISSION_MANAGEMENT:
        return MANAGE_USERS_AND_GROUPS;
      case TEMPLATE_MANAGEMENT:
        return MANAGE_TEMPLATE_LIBRARY;
      case ACCOUNT_MANAGEMENT:
        return ADMINISTER_OTHER_ACCOUNT_FUNCTIONS;
      case AUDIT_VIEWER:
        return VIEW_AUDITS;
      case TAG_MANAGEMENT:
      case MANAGE_TAGS:
        return QLAccountPermissionType.MANAGE_TAGS;
      case MANAGE_ACCOUNT_DEFAULTS:
        return QLAccountPermissionType.MANAGE_ACCOUNT_DEFAULTS;
      case CE_ADMIN:
        return ADMINISTER_CE;
      case CE_VIEWER:
        return VIEW_CE;
      case MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS:
        return QLAccountPermissionType.MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS;
      case VIEW_USER_AND_USER_GROUPS_AND_API_KEYS:
        return QLAccountPermissionType.VIEW_USER_AND_USER_GROUPS_AND_API_KEYS;
      case MANAGE_PIPELINE_GOVERNANCE_STANDARDS:
        return QLAccountPermissionType.MANAGE_PIPELINE_GOVERNANCE_STANDARDS;
      case MANAGE_DEPLOYMENT_FREEZES:
        return QLAccountPermissionType.MANAGE_DEPLOYMENT_FREEZES;
      case ALLOW_DEPLOYMENTS_DURING_FREEZE:
        return QLAccountPermissionType.ALLOW_DEPLOYMENTS_DURING_FREEZE;
      case MANAGE_IP_WHITELIST:
        return QLAccountPermissionType.MANAGE_IP_WHITELIST;
      case MANAGE_AUTHENTICATION_SETTINGS:
        return QLAccountPermissionType.MANAGE_AUTHENTICATION_SETTINGS;
      case MANAGE_SECRET_MANAGERS:
        return QLAccountPermissionType.MANAGE_SECRET_MANAGERS;
      case MANAGE_CONFIG_AS_CODE:
        return QLAccountPermissionType.MANAGE_CONFIG_AS_CODE;
      case MANAGE_DELEGATE_PROFILES:
        return QLAccountPermissionType.MANAGE_DELEGATE_PROFILES;
      case MANAGE_ALERT_NOTIFICATION_RULES:
        return QLAccountPermissionType.MANAGE_ALERT_NOTIFICATION_RULES;
      case MANAGE_DELEGATES:
        return QLAccountPermissionType.MANAGE_DELEGATES;
      case MANAGE_APPLICATION_STACKS:
        return QLAccountPermissionType.MANAGE_APPLICATION_STACKS;
      case MANAGE_CONNECTORS:
        return QLAccountPermissionType.MANAGE_CONNECTORS;
      case MANAGE_CLOUD_PROVIDERS:
        return QLAccountPermissionType.MANAGE_CLOUD_PROVIDERS;
      case MANAGE_SECRETS:
        return QLAccountPermissionType.MANAGE_SECRETS;
      case MANAGE_SSH_AND_WINRM:
        return QLAccountPermissionType.MANAGE_SSH_AND_WINRM;
      case MANAGE_API_KEYS:
        return QLAccountPermissionType.MANAGE_API_KEYS;
      case MANAGE_CUSTOM_DASHBOARDS:
        return QLAccountPermissionType.MANAGE_CUSTOM_DASHBOARDS;
      case CREATE_CUSTOM_DASHBOARDS:
        return QLAccountPermissionType.CREATE_CUSTOM_DASHBOARDS;
      case MANAGE_RESTRICTED_ACCESS:
        return QLAccountPermissionType.MANAGE_RESTRICTED_ACCESS;
      default:
        log.error("Invalid Account Permission Type {} given by the user", permissionType.toString());
    }
    throw new InvalidRequestException("Invalid Account Permission Type Given by the user");
  }

  // Map the portal Actions to GraphQL action Type
  private QLActions mapAppActionsToOutput(Action action) {
    switch (action) {
      case CREATE: // Change this later on
        return QLActions.CREATE;
      case READ:
        return QLActions.READ;
      case UPDATE:
        return QLActions.UPDATE;
      case DELETE:
        return QLActions.DELETE;
      case EXECUTE:
        return QLActions.EXECUTE;
      case EXECUTE_PIPELINE:
        return QLActions.EXECUTE_PIPELINE;
      case EXECUTE_WORKFLOW:
        return QLActions.EXECUTE_WORKFLOW;
      case EXECUTE_WORKFLOW_ROLLBACK:
        return QLActions.ROLLBACK_WORKFLOW;
      case ABORT_WORKFLOW:
        return QLActions.ABORT_WORKFLOW;
      default:
        log.error("Invalid Action {} given by the user", action.toString());
    }
    throw new InvalidRequestException("Invalid action given by the user");
  }

  // Convert app entity to Output App Entity
  private QLPermissionType mapToApplicationPermissionOutput(PermissionType permissionType) {
    switch (permissionType) {
      case ALL_APP_ENTITIES:
        return QLPermissionType.ALL;
      case SERVICE:
        return QLPermissionType.SERVICE;
      case ENV:
        return QLPermissionType.ENV;
      case WORKFLOW:
        return QLPermissionType.WORKFLOW;
      case PIPELINE:
        return QLPermissionType.PIPELINE;
      case DEPLOYMENT:
        return QLPermissionType.DEPLOYMENT;
      case PROVISIONER:
        return QLPermissionType.PROVISIONER;
      case APP_TEMPLATE:
        return QLPermissionType.TEMPLATE;
      default:
        log.error("Invalid Permission Type {} given by the user", permissionType.toString());
    }
    throw new InvalidRequestException("Invalid Permission Type given by the user");
  }

  private QLWorkflowPermissions createWorkflowFilterOutput(WorkflowFilter workflowPermissions) {
    if (isEmpty(workflowPermissions.getIds())) {
      EnumSet<QLWorkflowFilterType> filterTypes = EnumSet.noneOf(QLWorkflowFilterType.class);
      if (isEmpty(workflowPermissions.getFilterTypes())) {
        filterTypes.add(QLWorkflowFilterType.PRODUCTION_WORKFLOWS);
        filterTypes.add(QLWorkflowFilterType.NON_PRODUCTION_WORKFLOWS);
        filterTypes.add(QLWorkflowFilterType.WORKFLOW_TEMPLATES);
      } else {
        if (workflowPermissions.getFilterTypes().contains(PROD)) {
          filterTypes.add(QLWorkflowFilterType.PRODUCTION_WORKFLOWS);
        }
        if (workflowPermissions.getFilterTypes().contains(NON_PROD)) {
          filterTypes.add(QLWorkflowFilterType.NON_PRODUCTION_WORKFLOWS);
        }
        if (workflowPermissions.getFilterTypes().contains(TEMPLATES)) {
          filterTypes.add(QLWorkflowFilterType.WORKFLOW_TEMPLATES);
        }
      }
      return QLWorkflowPermissions.builder().filterTypes(filterTypes).build();
    }
    return QLWorkflowPermissions.builder().envIds(workflowPermissions.getIds()).build();
  }

  private QLWorkflowPermissions createWorkflowFilterOutputFromEntityFilter(GenericEntityFilter workflowPermissions) {
    if (isEmpty(workflowPermissions.getIds())) {
      EnumSet<QLWorkflowFilterType> filterTypes = EnumSet.noneOf(QLWorkflowFilterType.class);
      if (isEmpty(workflowPermissions.getFilterType())) {
        filterTypes.add(QLWorkflowFilterType.ALL_WORKFLOWS);
      } else {
        if (workflowPermissions.getFilterType().contains(ALL)) {
          filterTypes.add(QLWorkflowFilterType.ALL_WORKFLOWS);
        }
      }
      return QLWorkflowPermissions.builder().filterTypes(filterTypes).build();
    }
    return QLWorkflowPermissions.builder().workflowIds(workflowPermissions.getIds()).build();
  }

  private QLEnvPermissions createEnvFilterOutput(EnvFilter envPermissions) {
    if (isEmpty(envPermissions.getIds())) {
      EnumSet<QLEnvFilterType> filterTypes = EnumSet.noneOf(QLEnvFilterType.class);
      if (isEmpty(envPermissions.getFilterTypes())) {
        filterTypes.add(QLEnvFilterType.PRODUCTION_ENVIRONMENTS);
        filterTypes.add(QLEnvFilterType.NON_PRODUCTION_ENVIRONMENTS);
      } else {
        if (envPermissions.getFilterTypes().contains(PROD)) {
          filterTypes.add(QLEnvFilterType.PRODUCTION_ENVIRONMENTS);
        }
        if (envPermissions.getFilterTypes().contains(NON_PROD)) {
          filterTypes.add(QLEnvFilterType.NON_PRODUCTION_ENVIRONMENTS);
        }
      }
      return QLEnvPermissions.builder().envIds(envPermissions.getIds()).filterTypes(filterTypes).build();
    }
    return QLEnvPermissions.builder().envIds(envPermissions.getIds()).build();
  }

  private QLDeploymentPermissions createDeploymentFilterOutput(EnvFilter envPermissions) {
    QLExecutableElementFilterInput executableElementsFilter = null;
    if (envPermissions instanceof ExecutableElementsFilter) {
      final GenericEntityFilter filter = ((ExecutableElementsFilter) envPermissions).getFilter();
      final String executableElementFilterType =
          ((ExecutableElementsFilter) envPermissions).getExecutableElementFilterType();
      final QLExecutableElementType executableElementFilterTypeValue =
          executableElementFilterType.equals(ExecutableElementsFilter.FilterType.WORKFLOW)
          ? QLExecutableElementType.WORKFLOW
          : QLExecutableElementType.PIPELINE;
      QLPermissionsFilterType filterType =
          filter.getFilterType().equals(GenericEntityFilter.FilterType.ALL) ? QLPermissionsFilterType.ALL : null;
      executableElementsFilter = QLExecutableElementFilterInput.builder()
                                     .executableElementFilterType(filterType)
                                     .executableElementType(executableElementFilterTypeValue)
                                     .ids(filter.getIds())
                                     .build();
    }
    if (isEmpty(envPermissions.getIds())) {
      EnumSet<QLDeploymentFilterType> filterTypes = EnumSet.noneOf(QLDeploymentFilterType.class);
      if (isEmpty(envPermissions.getFilterTypes())) {
        filterTypes.add(QLDeploymentFilterType.PRODUCTION_ENVIRONMENTS);
        filterTypes.add(QLDeploymentFilterType.NON_PRODUCTION_ENVIRONMENTS);
      } else {
        if (envPermissions.getFilterTypes().contains(PROD)) {
          filterTypes.add(QLDeploymentFilterType.PRODUCTION_ENVIRONMENTS);
        }
        if (envPermissions.getFilterTypes().contains(NON_PROD)) {
          filterTypes.add(QLDeploymentFilterType.NON_PRODUCTION_ENVIRONMENTS);
        }
      }

      return QLDeploymentPermissions.builder()
          .executableElementFilter(executableElementsFilter)
          .envIds(envPermissions.getIds())
          .filterTypes(filterTypes)
          .build();
    }
    return QLDeploymentPermissions.builder()
        .executableElementFilter(executableElementsFilter)
        .envIds(envPermissions.getIds())
        .build();
  }

  private QLPipelinePermissions createPipelineFilterOutput(EnvFilter envPermissions) {
    if (isEmpty(envPermissions.getIds())) {
      EnumSet<QLPipelineFilterType> filterTypes = EnumSet.noneOf(QLPipelineFilterType.class);
      if (isEmpty(envPermissions.getFilterTypes())) {
        filterTypes.add(QLPipelineFilterType.PRODUCTION_PIPELINES);
        filterTypes.add(QLPipelineFilterType.NON_PRODUCTION_PIPELINES);

      } else {
        if (envPermissions.getFilterTypes().contains(PROD)) {
          filterTypes.add(QLPipelineFilterType.PRODUCTION_PIPELINES);
        }
        if (envPermissions.getFilterTypes().contains(NON_PROD)) {
          filterTypes.add(QLPipelineFilterType.NON_PRODUCTION_PIPELINES);
        }
      }
      return QLPipelinePermissions.builder().envIds(envPermissions.getIds()).filterTypes(filterTypes).build();
    }
    return QLPipelinePermissions.builder().envIds(envPermissions.getIds()).build();
  }

  private QLPipelinePermissions createPipelineFilterOutputFromEntityFilter(GenericEntityFilter pipelinePermissions) {
    if (isEmpty(pipelinePermissions.getIds())) {
      EnumSet<QLPipelineFilterType> filterTypes = EnumSet.noneOf(QLPipelineFilterType.class);
      if (isEmpty(pipelinePermissions.getFilterType())) {
        filterTypes.add(QLPipelineFilterType.ALL_PIPELINES);
      } else {
        if (pipelinePermissions.getFilterType().contains(ALL)) {
          filterTypes.add(QLPipelineFilterType.ALL_PIPELINES);
        }
      }
      return QLPipelinePermissions.builder().filterTypes(filterTypes).build();
    }
    return QLPipelinePermissions.builder().pipelineIds(pipelinePermissions.getIds()).build();
  }

  private List<QLAppPermission> populateUserGroupAppPermissionOutput(Set<AppPermission> appPermissions) {
    List<QLAppPermission> userGroupAppPermissions = null;
    if (appPermissions != null) {
      userGroupAppPermissions =
          appPermissions.stream().map(this::convertToAppPermissionOutput).collect(Collectors.toList());
    }
    return userGroupAppPermissions;
  }

  private QLAppPermission convertToAppPermissionOutput(AppPermission permission) {
    // Convert portal actions to graphQL output
    Set<Action> actionsList = permission.getActions();
    Set<QLActions> actions = actionsList.stream().map(this::mapAppActionsToOutput).collect(Collectors.toSet());
    // Convert portal permissionType to graphQL output PermissionType
    PermissionType permissionType = permission.getPermissionType();
    QLPermissionType appPermissionType = mapToApplicationPermissionOutput(permissionType);
    // Convert the appFilter to the graphQLOutputType
    QLAppFilter appFilter = appFilterController.createAppFilterOutput(permission.getAppFilter());
    QLAppPermissionBuilder builder = QLAppPermission.builder().applications(appFilter).actions(actions);
    switch (permissionType) {
      case ALL_APP_ENTITIES:
        return builder.permissionType(appPermissionType).build();
      case SERVICE:
        QLServicePermissions servicePermissions;
        Set<String> serviceIds = permission.getEntityFilter().getIds();
        if (isEmpty(serviceIds)) {
          servicePermissions =
              QLServicePermissions.builder().filterType(QLPermissionsFilterType.ALL).serviceIds(serviceIds).build();
        } else {
          servicePermissions = QLServicePermissions.builder().serviceIds(serviceIds).build();
        }
        return builder.permissionType(appPermissionType).services(servicePermissions).build();
      case APP_TEMPLATE:
        QLTemplatePermissions templatePermissions;
        Set<String> templateIds = permission.getEntityFilter().getIds();
        if (isEmpty(templateIds)) {
          templatePermissions =
              QLTemplatePermissions.builder().filterType(QLPermissionsFilterType.ALL).templateIds(templateIds).build();
        } else {
          templatePermissions = QLTemplatePermissions.builder().templateIds(templateIds).build();
        }
        return builder.permissionType(appPermissionType).templates(templatePermissions).build();
      case ENV:
        QLEnvPermissions envFilter = createEnvFilterOutput((EnvFilter) permission.getEntityFilter());
        return builder.permissionType(appPermissionType).environments(envFilter).build();
      case WORKFLOW:
        QLWorkflowPermissions workflowFilter = getQlWorkflowPermissions(permission);
        return builder.permissionType(appPermissionType).workflows(workflowFilter).build();
      case DEPLOYMENT:
        QLDeploymentPermissions deploymentFilter =
            createDeploymentFilterOutput((EnvFilter) permission.getEntityFilter());
        return builder.permissionType(appPermissionType).deployments(deploymentFilter).build();
      case PIPELINE:
        QLPipelinePermissions pipelineFilter = getQlPipelinePermissions(permission);
        return builder.permissionType(appPermissionType).pipelines(pipelineFilter).build();
      case PROVISIONER:
        QLProivionerPermissions provisionerPermissions;
        Set<String> provisionersId = permission.getEntityFilter().getIds();
        if (isEmpty(provisionersId)) {
          provisionerPermissions = QLProivionerPermissions.builder()
                                       .filterType(QLPermissionsFilterType.ALL)
                                       .provisionerIds(provisionersId)
                                       .build();
        } else {
          provisionerPermissions = QLProivionerPermissions.builder().provisionerIds(provisionersId).build();
        }
        return builder.permissionType(appPermissionType).provisioners(provisionerPermissions).build();
      default:
        log.error("Invalid Application Permission Type {} given by the user", permissionType.toString());
        throw new InvalidRequestException("Invalid Application Permission type given by the user");
    }
  }

  private QLPipelinePermissions getQlPipelinePermissions(AppPermission permission) {
    QLPipelinePermissions pipelineFilter;
    if (permission.getEntityFilter() instanceof GenericEntityFilter) {
      pipelineFilter = createPipelineFilterOutputFromEntityFilter((GenericEntityFilter) permission.getEntityFilter());
    } else {
      pipelineFilter = createPipelineFilterOutput((EnvFilter) permission.getEntityFilter());
    }
    return pipelineFilter;
  }

  private QLWorkflowPermissions getQlWorkflowPermissions(AppPermission permission) {
    QLWorkflowPermissions workflowFilter;
    if (permission.getEntityFilter() instanceof GenericEntityFilter) {
      workflowFilter = createWorkflowFilterOutputFromEntityFilter((GenericEntityFilter) permission.getEntityFilter());

    } else {
      workflowFilter = createWorkflowFilterOutput((WorkflowFilter) permission.getEntityFilter());
    }
    return workflowFilter;
  }

  private QLAccountPermissions populateUserGroupAccountPermission(AccountPermissions permissions) {
    Set<QLAccountPermissionType> outputPermissions = null;
    if (permissions == null) {
      return null;
    }
    Set<PermissionType> accountPermissions = permissions.getPermissions();
    if (accountPermissions != null) {
      outputPermissions =
          accountPermissions.stream().map(this::mapAccountPermissionsToOutput).collect(Collectors.toSet());
    }
    return QLAccountPermissions.builder().accountPermissionTypes(outputPermissions).build();
  }

  QLGroupPermissions populateUserGroupPermissions(UserGroup userGroup) {
    QLAccountPermissions accountPermissions = populateUserGroupAccountPermission(userGroup.getAccountPermissions());
    List<QLAppPermission> appPermissions = populateUserGroupAppPermissionOutput(userGroup.getAppPermissions());
    return QLGroupPermissions.builder().appPermissions(appPermissions).accountPermissions(accountPermissions).build();
  }
}
