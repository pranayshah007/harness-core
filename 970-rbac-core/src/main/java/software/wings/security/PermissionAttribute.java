/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT;
import static software.wings.security.PermissionAttribute.PermissionType.APP;
import static software.wings.security.PermissionAttribute.PermissionType.ENV;
import static software.wings.security.PermissionAttribute.PermissionType.NONE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.Data;

/**
 * Created by anubhaw on 3/10/16.
 */
@OwnedBy(PL)
@Data
public class PermissionAttribute {
  private static final Map<String, Action> methodActionMap =
      ImmutableMap.of("GET", Action.READ, "PUT", Action.UPDATE, "POST", Action.CREATE, "DELETE", Action.DELETE);
  private ResourceType resourceType;
  private Action action;
  private PermissionType permissionType;

  // If the query / path parameter has a different name other than the default name for the permission type.
  private String parameterName;
  private String dbFieldName;
  private String dbCollectionName;
  private boolean skipAuth;

  /**
   * This constructor is used by old rbac code
   * @param resourceType
   * @param action
   */
  public PermissionAttribute(ResourceType resourceType, Action action) {
    this.resourceType = resourceType;
    this.action = action;
    this.permissionType = resourceType.getActionPermissionScopeMap().get(action);
  }

  public PermissionAttribute(PermissionType permissionType) {
    this.permissionType = permissionType;
  }

  /**
   * This constructor is used by old rbac code
   * @param resourceType
   * @param permissionType
   * @param method
   */
  public PermissionAttribute(ResourceType resourceType, PermissionType permissionType, String method) {
    this.resourceType = resourceType;
    this.action = methodActionMap.get(method);
    this.permissionType = permissionType;
    if (permissionType == null || permissionType == NONE) {
      this.permissionType = resourceType.getActionPermissionScopeMap().get(action);
    }
  }

  public PermissionAttribute(PermissionType permissionType, Action action) {
    this(null, permissionType, action, null, null, null, null, false);
  }

  public PermissionAttribute(ResourceType resourceType, PermissionType permissionType, Action action) {
    this(resourceType, permissionType, action, null, null, null, null, false);
  }

  /**
   *
   * @param resourceType
   * @param permissionType
   * @param action
   */
  public PermissionAttribute(ResourceType resourceType, PermissionType permissionType, Action action, String method) {
    this(resourceType, permissionType, action, method, null, null, null, false);
  }

  /**
   *
   * @param resourceType
   * @param permissionType
   * @param action
   */
  public PermissionAttribute(ResourceType resourceType, PermissionType permissionType, Action action,
      String parameterName, String dbFieldName) {
    this(resourceType, permissionType, action, null, parameterName, dbFieldName, null, false);
  }

  /**
   *
   * @param resourceType
   * @param permissionType
   * @param action
   */
  public PermissionAttribute(ResourceType resourceType, PermissionType permissionType, Action action,
      String parameterName, String dbFieldName, String dbCollectionName) {
    this(resourceType, permissionType, action, null, parameterName, dbFieldName, dbCollectionName, false);
  }

  /**
   * Instantiates a new Permission attribute.
   *
   * @param resourceType the resource type
   * @param permissionType      the permissionType
   * @param method     the method
   */
  public PermissionAttribute(ResourceType resourceType, PermissionType permissionType, Action action, String method,
      String parameterName, String dbFieldName, String dbCollectionName, boolean skipAuth) {
    this.resourceType = resourceType;
    this.permissionType = permissionType;
    if (permissionType != null && permissionType != NONE) {
      if (action != null) {
        this.action = action;
      } else {
        if (method != null) {
          this.action = methodActionMap.get(method);
        } else {
          throw new WingsException("Either action or method has to be specified if permission type is specified");
        }
      }
    }

    this.parameterName = parameterName;
    this.dbFieldName = dbFieldName;
    this.dbCollectionName = dbCollectionName;
    this.skipAuth = skipAuth;
  }

  /**
   * The Enum Resource.
   */
  public enum ResourceType {
    /**
     * App resource.
     */
    APPLICATION(APP),
    /**
     * Service resource.
     */
    SERVICE(APP),
    /**
     * Config resource.
     */
    CONFIGURATION(APP),
    /**
     * Configuration Override resource.
     */
    CONFIGURATION_OVERRIDE(ENV),
    /**
     * Configuration Override resource.
     */
    WORKFLOW(ENV),
    /**
     * Env resource.
     */
    ENVIRONMENT(APP, ENV, ENV, APP),
    /**
     * Role resource.
     */
    ROLE(ACCOUNT),
    /**
     * Deployment resource.
     */
    DEPLOYMENT(ENV),
    /**
     * Artifacts resource.
     */
    ARTIFACT(APP),
    /**
     * User resource.
     */
    CLOUD(ACCOUNT),
    /**
     * User resource.
     */
    USER(ACCOUNT),
    /**
     * CD resource.
     */
    CD(APP),
    /**
     * Pipeline resource.
     */
    PIPELINE(APP),
    /**
     * Setting resource.
     */
    SETTING(ACCOUNT),

    /**
     * Configured Limit resource.
     */
    LIMIT(ACCOUNT),

    /**
     * App stack resource type.
     */
    APP_STACK(ACCOUNT),
    /**
     * Notificaion Group.
     */
    NOTIFICATION_GROUP(ACCOUNT),

    /**
     * Delegate resource type.
     */
    DELEGATE(PermissionType.DELEGATE),
    /**
     * Delegate Scope resource type.
     */
    DELEGATE_SCOPE(PermissionType.DELEGATE),

    WHITE_LIST(ACCOUNT),

    SSO(ACCOUNT),

    API_KEY(ACCOUNT),

    PROVISIONER(APP),

    PREFERENCE(ACCOUNT),

    TEMPLATE(ACCOUNT),

    CUSTOM_DASHBOARD(ACCOUNT),

    BUDGET(ACCOUNT),

    GCP_RESOURCE(ACCOUNT),

    CLUSTERRECORD(ACCOUNT),

    K8S_LABEL(ACCOUNT),

    K8S_EVENT_YAML_DIFF(ACCOUNT),

    K8S_RECOMMENDATION(ACCOUNT),

    CE_ANOMALIES(ACCOUNT),

    CE_CLUSTER(ACCOUNT),

    CE_CONNECTOR(ACCOUNT),

    CE_BATCH(ACCOUNT),

    LINKED_ACCOUNT(ACCOUNT);

    private ImmutableMap<Action, PermissionType> actionPermissionScopeMap;

    ResourceType(PermissionType permissionScope) {
      this(permissionScope, permissionScope);
    }

    ResourceType(PermissionType readPermissionScope, PermissionType writePermissionScope) {
      this(writePermissionScope, readPermissionScope, writePermissionScope, writePermissionScope);
    }

    ResourceType(PermissionType createPermissionScope, PermissionType readPermissionScope,
        PermissionType updatePermissionScope, PermissionType deletePermissionScope) {
      actionPermissionScopeMap = ImmutableMap.of(Action.CREATE, createPermissionScope, Action.READ, readPermissionScope,
          Action.UPDATE, updatePermissionScope, Action.DELETE, deletePermissionScope);
    }

    /**
     * Gets action permission permissionType map.
     *
     * @return the action permission permissionType map
     */
    public ImmutableMap<Action, PermissionType> getActionPermissionScopeMap() {
      return actionPermissionScopeMap;
    }
  }

  /**
   * The Enum Action.
   */
  public enum Action {
    /**
     * All action.
     */
    ALL,
    /**
     * Create action.
     */
    CREATE,
    /**
     * Read action.
     */
    READ,
    /**
     * Update action.
     */
    UPDATE,
    /**
     * Delete action.
     */
    DELETE,
    /**
     * Deprecated Execute action.
     */
    @Deprecated EXECUTE,
    /**
     * Execute Workflow action.
     */
    EXECUTE_WORKFLOW,
    /**
     * Execute Pipeline action.
     */
    EXECUTE_PIPELINE,
    /**
     * Execute Workflow Rollback
     */
    EXECUTE_WORKFLOW_ROLLBACK,
    /**
     * Abort Workflow
     */
    ABORT_WORKFLOW,
    /**
     * default action.
     */
    DEFAULT
  }

  /**
   * The enum Permission type.
   */
  public enum PermissionType {
    /**
     * Account permission type.
     */
    ACCOUNT,
    /**
     * Logged In permission type.
     */
    LOGGED_IN,
    /**
     * Delegate In permission type.
     */
    DELEGATE,
    /**
     * None permission permissionType.
     */
    NONE,
    /**
     * App permission type.
     */
    APP,
    /**
     * All App permission types.
     */
    ALL_APP_ENTITIES,
    /**
     * Env permission type.
     */
    ENV,
    /**
     * Service permission permissionType
     */
    SERVICE,
    /**
     * Workflow permission permissionType
     */
    WORKFLOW,
    /**
     * Pipeline permission permissionType
     */
    PIPELINE,
    /**
     * Deployment permission permissionType
     */
    DEPLOYMENT,
    /**
     * Manage Applications
     */
    @Deprecated APPLICATION_CREATE_DELETE,
    /**
     * Account permission type.
     */
    USER_PERMISSION_MANAGEMENT,
    /**
     * Account permission type.
     */
    ACCOUNT_MANAGEMENT,
    /**
     * Provisioner permission permissionType
     */
    PROVISIONER,
    /**
     * Manage Account templates
     */
    TEMPLATE_MANAGEMENT,
    /**
     * Manage Application templates
     */
    APP_TEMPLATE,
    /**
     * User Permission Read.
     */
    USER_PERMISSION_READ,

    /**
     * View Audit Page
     */
    AUDIT_VIEWER,

    /**
     * Tag Management Deprecated
     */

    @Deprecated TAG_MANAGEMENT,

    CE_ADMIN,

    CE_VIEWER,

    /**
     * Manage Cloud Providers
     */
    MANAGE_CLOUD_PROVIDERS,

    /**
     * Manage Connectors
     */
    MANAGE_CONNECTORS,

    /**
     * Manage Applications
     */
    MANAGE_APPLICATIONS,

    /**
     * Manage Application Stacks
     */
    MANAGE_APPLICATION_STACKS,

    /**
     * Manage Delegates
     */
    MANAGE_DELEGATES,

    /**
     * Manage Alert Notification Rules
     */
    MANAGE_ALERT_NOTIFICATION_RULES,

    /**
     * Manage Delegate profiles
     */
    MANAGE_DELEGATE_PROFILES,

    /**
     * Manage Config-as-code
     */
    MANAGE_CONFIG_AS_CODE,

    /**
     * Manage Secrets
     */
    MANAGE_SECRETS,

    /**
     * Manage Secret Managers
     */
    MANAGE_SECRET_MANAGERS,

    /**
     * Manage SSH and WinRM Connection
     */
    MANAGE_SSH_AND_WINRM,

    /**
     * Manage Authentication Settings
     */
    MANAGE_AUTHENTICATION_SETTINGS,

    /**
     * Manage User, User Groups and API keys
     */
    @Deprecated MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS,

    /**
     *  View User, User Groups and API keys
     */
    @Deprecated VIEW_USER_AND_USER_GROUPS_AND_API_KEYS,

    /**
     * Manage IP Whitelist
     */
    MANAGE_IP_WHITELIST,

    @Deprecated MANAGE_IP_WHITELISTING,

    /**
     * Manage Deployment Freezes
     */
    MANAGE_DEPLOYMENT_FREEZES,

    /**
     * Deploy during Deployment Freezes
     */
    ALLOW_DEPLOYMENTS_DURING_FREEZE,

    /**
     * Manage Pipeline Governance Standards
     */
    MANAGE_PIPELINE_GOVERNANCE_STANDARDS,

    /**
     * Manage API Keys
     */
    MANAGE_API_KEYS,

    /**
     * Manage Tags
     */
    MANAGE_TAGS,

    /**
     * Manage Account Defaults
     */
    MANAGE_ACCOUNT_DEFAULTS,

    /**
     * Manage custom dashboard
     */
    MANAGE_CUSTOM_DASHBOARDS,

    /**
     * Create custom dashboard
     */
    CREATE_CUSTOM_DASHBOARDS,

    /**
     * Manage Restricted Access
     */
    MANAGE_RESTRICTED_ACCESS;
  }
}
