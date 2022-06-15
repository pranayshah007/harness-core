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
  public static final String UPSERT_ACTION = "upsert";
  public static final String DELETE_ACTION = "delete";
  public static final String FLUSH_CREATE_ACTION = "flushCreate";

  public static final String PROJECT_ENTITY = "project";
  public static final String ORGANIZATION_ENTITY = "organization";
  public static final String CONNECTOR_ENTITY = "connector";
  public static final String SERVICEACCOUNT_ENTITY = "serviceaccount";
  public static final String SECRET_ENTITY = "secret";
  public static final String VARIABLE_ENTITY = "variable";
  public static final String USER_ENTITY = "user";
  public static final String PIPELINE_ENTITY = "pipeline";
  public static final String DELEGATE_ENTITY = "delegate";
  public static final String DELEGATE_CONFIGURATION_ENTITY = "delegateconfiguration";
  public static final String FILE_ENTITY = "file";
  public static final String USER_SCOPE_RECONCILIATION = "userScopeReconciliation";
  public static final String CHAOS_HUB = "chaoshub";
  public static final String CHAOS_AGENT = "chaosagent";
  public static final String CHAOS_WORKFLOW = "chaosworkflow";

  public static final String SERVICE_ENTITY = "service";
  public static final String ENVIRONMENT_ENTITY = "environment";
  public static final String ENVIRONMENT_GROUP_ENTITY = "environmentGroup";

  public static final String RESOURCE_GROUP = "resourcegroup";
  public static final String USER_GROUP = "usergroup";
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
}
