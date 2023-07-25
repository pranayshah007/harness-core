/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public final class EventsFrameworkMetadataConstants {
  public static final String ENTITY_TYPE = "entityType";

  public static final String ACTION = "action";
  public static final String CREATE_ACTION = "create";
  public static final String RESTORE_ACTION = "restore";
  public static final String UPDATE_ACTION = "update";
  public static final String SYNC_ACTION = "syncNgWithCg";
  public static final String UPSERT_ACTION = "upsert";
  public static final String DELETE_ACTION = "delete";
  public static final String NG_USER_CLEANUP_ACTION = "ngUserCleanUp";
  public static final String DISABLE_IP_ALLOWLIST = "disableIPAllowlist";
  public static final String FLUSH_CREATE_ACTION = "flushCreate";

  public static final String PROJECT_ENTITY = "project";
  public static final String ORGANIZATION_ENTITY = "organization";
  public static final String CONNECTOR_ENTITY = "connector";
  public static final String SERVICEACCOUNT_ENTITY = "serviceaccount";
  public static final String TERRAFORM_CONFIG_ENTITY = "terraformconfig";
  public static final String TERRAGRUNT_CONFIG_ENTITY = "terragruntconfig";
  public static final String CLOUDFORMATION_CONFIG_ENTITY = "cloudformationconfig";
  public static final String AZURE_ARM_CONFIG_ENTITY = "azurearmconfig";
  public static final String SECRET_ENTITY = "secret";
  public static final String VARIABLE_ENTITY = "variable";
  public static final String USER_ENTITY = "user";
  public static final String PIPELINE_ENTITY = "pipeline";
  public static final String DELEGATE_ENTITY = "delegate";
  public static final String DELEGATE_CONFIGURATION_ENTITY = "delegateconfiguration";
  public static final String FILE_ENTITY = "file";
  public static final String USER_SCOPE_RECONCILIATION = "userScopeReconciliation";
  public static final String CHAOS_EXPERIMENT = "chaosexperiment";
  public static final String CHAOS_HUB = "chaoshub";
  public static final String CHAOS_INFRASTRUCTURE = "chaosinfrastructure";
  public static final String CHAOS_GAMEDAY = "chaosgameday";

  public static final String SERVICE_ENTITY = "service";
  public static final String DEPLOYMENT_FREEZE_ENTITY = "deploymentfreeze";
  public static final String ENVIRONMENT_ENTITY = "environment";
  public static final String ENVIRONMENT_GROUP_ENTITY = "environmentGroup";

  public static final String RESOURCE_GROUP = "resourcegroup";

  public static final String USER_GROUP = "usergroup";
  public static final String FILTER = "filter";
  public static final String FREEZE_CONFIG = "freezeConfig";

  public static final String GIT_COMMIT = "gitCommit";
  public static final String CD_TELEMETRY = "cdTelemetry";

  public static final String LICENSE_MODULES = "licenseModule";
  public static final String SIGNUP_TOKEN = "signupToken";
  public static final String POLLING_DOCUMENT = "pollingDocument";
  public static final String GIT_TO_HARNESS_PROGRESS = "gitToHarnessProgress";
  public static final String INVITE = "invite";

  public static final String SETTINGS = "settings";
  public static final String SETTINGS_GROUP_IDENTIFIER = "settingGroupIdentifier";
  public static final String SETTINGS_CATEGORY = "settingCategory";

  public static final String SCM = "sourceCodeManager";
  public static final String STAGE_EXEC_INFO = "stageExecutionInfo";
  public static final String YAML_CHANGE_SET = "yamlChangeSet";
  public static final String GIT_PROCESS_REQUEST = "gitProcessReq";
  // deprecated, use setupusage and entityActivity channel.
  public static final String SETUP_USAGE_ENTITY = "setupUsage";
  public static final String ACCOUNT_ENTITY = "account";

  public static final String REFERRED_ENTITY_TYPE = "referredEntityType";
  public static final String CONNECTOR_ENTITY_TYPE = "connectorType";
  public static final String SERVICE_ACCOUNT_ENTITY = "serviceaccount";
  public static final String API_KEY_ENTITY = "apiKey";
  public static final String TOKEN_ENTITY = "token";
  public static final String TEMPLATE_ENTITY = "template";

  // Metric Constants
  public static final String ACCOUNT_IDENTIFIER_METRICS_KEY = "accountId";
  public static final String STREAM_NAME_METRICS_KEY = "streamName";

  public static final String GITOPS_AGENT_ENTITY = "agent";
  public static final String GITOPS_APPLICATION_ENTITY = "application";
  public static final String GITOPS_REPOSITORY_ENTITY = "repository";
  public static final String GITOPS_CLUSTER_ENTITY = "cluster";

  public static final String STREAMING_DESTINATION = "streamingDestination";

  // CCM
  public static final String CCM_FOLDER = "ccmFolder";
  public static final String CCM_RULE = "ccmRule";

  // IDP
  public static final String ASYNC_CATALOG_IMPORT_ENTITY = "asyncCatalogImport";
}
