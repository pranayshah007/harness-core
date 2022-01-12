/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.annotations.dev;

import static io.harness.annotations.dev.HarnessTeam.DX;

@OwnedBy(DX)
public enum HarnessModule {
  UNDEFINED,
  _001_MICROSERVICE_INTERFACE_TOOL,
  _270_VERIFICATION,
  _280_BATCH_PROCESSING,
  _310_CI_MANAGER,
  _320_CI_EXECUTION,
  _330_CI_BEANS,
  _360_CG_MANAGER,
  _375_CE_GRAPHQL,
  _380_CG_GRAPHQL,
  _390_DB_MIGRATION,
  _410_CG_REST,
  _420_DELEGATE_AGENT,
  _420_DELEGATE_SERVICE,
  _430_CV_NEXTGEN_COMMONS,
  _440_CONNECTOR_NEXTGEN,
  _440_SECRET_MANAGEMENT_SERVICE,
  _441_CG_INSTANCE_SYNC,
  _445_CG_CONNECTORS,
  _450_CE_VIEWS,
  _460_CAPABILITY,
  _470_ALERT,
  _490_CE_COMMONS,
  _800_PIPELINE_SERVICE,
  _815_CG_TRIGGERS,
  _810_NG_TRIGGERS,
  _820_PLATFORM_SERVICE,
  _830_NOTIFICATION_SERVICE,
  _840_NG_TRIGGERS,
  _850_EXECUTION_PLAN,
  _850_NG_PIPELINE_COMMONS,
  _860_ORCHESTRATION_STEPS,
  _860_ORCHESTRATION_VISUALIZATION,
  _865_CG_EVENTS,
  _870_CG_ORCHESTRATION,
  _870_ORCHESTRATION,
  _876_ORCHESTRATION_BEANS,
  _878_PIPELINE_SERVICE_UTILITIES,
  _879_PMS_SDK,
  _882_PMS_SDK_CORE,
  _884_PMS_COMMONS,
  _888_PMS_CLIENT,
  _889_YAML_COMMONS,
  _890_ORCHESTRATION_PERSISTENCE,
  _890_PMS_CONTRACTS,
  _890_SM_CORE,
  _900_YAML_SDK,
  _910_DELEGATE_SERVICE_DRIVER,
  _910_DELEGATE_TASK_GRPC_SERVICE,
  _920_DELEGATE_AGENT_BEANS,
  _920_DELEGATE_SERVICE_BEANS,
  _930_DELEGATE_TASKS,
  _930_NG_CORE_CLIENTS,
  _930_NOTIFICATION_SERVICE,
  _930_NG_LICENSE_MANAGER,
  _940_NOTIFICATION_CLIENT,
  _940_SECRET_MANAGER_CLIENT,
  _940_CG_AUDIT_SERVICE,
  _940_MARKETPLACE_INTEGRATIONS,
  _945_ACCOUNT_MGMT,
  _950_COMMAND_LIBRARY_COMMON,
  _950_DELEGATE_TASKS_BEANS,
  _950_EVENTS_FRAMEWORK,
  _940_FEATURE_FLAG,
  _950_NG_CORE,
  _950_NG_PROJECT_N_ORGS,
  _950_TIMEOUT_ENGINE,
  _950_WAIT_ENGINE,
  _950_WALKTREE_VISITOR,
  _950_NG_AUTHENTICATION_SERVICE,
  _950_NG_SIGNUP,
  _951_CG_GIT_SYNC,
  _953_EVENTS_API,
  _953_GIT_SYNC_COMMONS,
  _955_ACCOUNT_MGMT,
  _955_ALERT_BEANS,
  _955_CG_YAML,
  _955_DELEGATE_BEANS,
  _957_CG_BEANS,
  _959_COMMON_ENTITIES,
  _959_PSQL_DATABASE_MODELS,
  _960_API_SERVICES,
  _960_EXPRESSION_SERVICE,
  _960_NG_CORE_BEANS,
  _960_NOTIFICATION_BEANS,
  _960_PERSISTENCE,
  _970_API_SERVICES_BEANS,
  _970_GRPC,
  _970_NG_COMMONS,
  _970_RBAC_CORE,
  _980_COMMONS,
  _990_COMMONS_TEST;
}
