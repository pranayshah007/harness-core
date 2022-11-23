/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.permissions;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.QLEnum;

@OwnedBy(PL)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public enum QLAccountPermissionType implements QLEnum {
  CREATE_AND_DELETE_APPLICATION,
  READ_USERS_AND_GROUPS,
  MANAGE_USERS_AND_GROUPS,
  MANAGE_TEMPLATE_LIBRARY,
  ADMINISTER_OTHER_ACCOUNT_FUNCTIONS,
  VIEW_AUDITS,
  MANAGE_TAGS,
  MANAGE_ACCOUNT_DEFAULTS,
  ADMINISTER_CE,
  VIEW_CE,
  /**
   * Manage Cloud Providers
   */
  MANAGE_CLOUD_PROVIDERS,

  /**
   * Manage Connectors
   */
  MANAGE_CONNECTORS,

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
  MANAGE_USER_AND_USER_GROUPS_AND_API_KEYS,

  /**
   *  View User, User Groups and API keys
   */
  VIEW_USER_AND_USER_GROUPS_AND_API_KEYS,

  /**
   * Manage IP Whitelist
   */
  MANAGE_IP_WHITELIST,

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
  MANAGE_RESTRICTED_ACCESS,

  /**
   * Hide NextGen Button
   */
  HIDE_NEXTGEN_BUTTON;

  @Override
  public String getStringValue() {
    return this.name();
  }
}
