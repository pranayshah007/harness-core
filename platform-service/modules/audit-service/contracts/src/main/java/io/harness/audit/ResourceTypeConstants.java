/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class ResourceTypeConstants {
  public static final String ORGANIZATION = "ORGANIZATION";
  public static final String PROJECT = "PROJECT";
  public static final String USER_GROUP = "USER_GROUP";
  public static final String SECRET = "SECRET";
  public static final String RESOURCE_GROUP = "RESOURCE_GROUP";
  public static final String USER = "USER";
  public static final String ROLE = "ROLE";
  public static final String ROLE_ASSIGNMENT = "ROLE_ASSIGNMENT";
  public static final String PIPELINE = "PIPELINE";
  public static final String TRIGGER = "TRIGGER";
  public static final String TEMPLATE = "TEMPLATE";
  public static final String INPUT_SET = "INPUT_SET";
  public static final String DELEGATE_CONFIGURATION = "DELEGATE_CONFIGURATION";
  public static final String SERVICE = "SERVICE";
  public static final String ENVIRONMENT = "ENVIRONMENT";
  public static final String ENVIRONMENT_GROUP = "ENVIRONMENT_GROUP";
  public static final String DELEGATE = "DELEGATE";
  public static final String DELEGATE_GROUPS = "DELEGATE_GROUPS";
  public static final String SERVICE_ACCOUNT = "SERVICE_ACCOUNT";
  public static final String CONNECTOR = "CONNECTOR";
  public static final String API_KEY = "API_KEY";
  public static final String TOKEN = "TOKEN";
  public static final String DELEGATE_TOKEN = "DELEGATE_TOKEN";
  public static final String GOVERNANCE_POLICY = "GOVERNANCE_POLICY";
  public static final String GOVERNANCE_POLICY_SET = "GOVERNANCE_POLICY_SET";
  public static final String FILE = "FILE";
  public static final String VARIABLE = "VARIABLE";
  public static final String CHAOS_HUB = "CHAOS_HUB";
  public static final String MONITORED_SERVICE = "MONITORED_SERVICE";
  public static final String CHAOS_INFRASTRUCTURE = "CHAOS_INFRASTRUCTURE";
  public static final String CHAOS_EXPERIMENT = "CHAOS_EXPERIMENT";
  public static final String CHAOS_GAMEDAY = "CHAOS_GAMEDAY";
  public static final String SERVICE_LEVEL_OBJECTIVE = "SERVICE_LEVEL_OBJECTIVE";
  public static final String STO_TARGET = "STO_TARGET";
  public static final String STO_EXEMPTION = "STO_EXEMPTION";
  public static final String PERSPECTIVE = "PERSPECTIVE";
  public static final String PERSPECTIVE_BUDGET = "PERSPECTIVE_BUDGET";
  public static final String PERSPECTIVE_REPORT = "PERSPECTIVE_REPORT";
  public static final String COST_CATEGORY = "COST_CATEGORY";
  public static final String SMTP = "SMTP";
  public static final String PERSPECTIVE_FOLDER = "PERSPECTIVE_FOLDER";
  public static final String AUTOSTOPPING_RULE = "AUTOSTOPPING_RULE";
  public static final String AUTOSTOPPING_LB = "AUTOSTOPPING_LB";
  public static final String AUTOSTOPPING_STARTSTOP = "AUTOSTOPPING_STARTSTOP";
  public static final String SETTING = "SETTING";
  public static final String NG_LOGIN_SETTINGS = "NG_LOGIN_SETTINGS";
  public static final String DEPLOYMENT_FREEZE = "DEPLOYMENT_FREEZE";
  public static final String CLOUD_ASSET_GOVERNANCE_RULE = "CLOUD_ASSET_GOVERNANCE_RULE";
  public static final String CLOUD_ASSET_GOVERNANCE_RULE_SET = "CLOUD_ASSET_GOVERNANCE_RULE_SET";
  public static final String CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT = "CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT";
  public static final String FEATURE_FLAG = "FEATURE_FLAG";
  public static final String TARGET_GROUP = "TARGET_GROUP";
  public static final String DOWNTIME = "DOWNTIME";
  public static final String STREAMING_DESTINATION = "STREAMING_DESTINATION";
  public static final String NG_ACCOUNT_DETAILS = "NG_ACCOUNT_DETAILS";
  public static final String BUDGET_GROUP = "BUDGET_GROUP";
  public static final String IP_ALLOWLIST_CONFIG = "IP_ALLOWLIST_CONFIG";
  public static final String NETWORK_MAP = "NETWORK_MAP";
  public static final String CET_AGENT_TOKEN = "CET_AGENT_TOKEN";
  public static final String CET_CRITICAL_EVENT = "CET_CRITICAL_EVENT";
  public static final String CET_SAVED_FILTER = "CET_SAVED_FILTER";
  public static final String CHAOS_SECURITY_GOVERNANCE = "CHAOS_SECURITY_GOVERNANCE";
  public static final String EULA = "END_USER_LICENSE_AGREEMENT";
  public static final String WORKSPACE = "WORKSPACE";

  public static final String SEI_CONFIGURATION_SETTINGS = "SEI_CONFIGURATION_SETTINGS";
  public static final String SEI_COLLECTIONS = "SEI_COLLECTIONS";
  public static final String SEI_INSIGHTS = "SEI_INSIGHTS";
  public static final String IDP_APP_CONFIGS = "IDP_APP_CONFIGS";
  public static final String IDP_CONFIG_ENV_VARIABLES = "IDP_CONFIG_ENV_VARIABLES";
  public static final String IDP_PROXY_HOST = "IDP_PROXY_HOST";

  public static final String IDP_SCORECARDS = "IDP_SCORECARDS";
  public static final String IDP_CHECKS = "IDP_CHECKS";
  public static final String IDP_ALLOW_LIST = "IDP_ALLOW_LIST";

  public static final String IDP_OAUTH_CONFIG = "IDP_OAUTH_CONFIG";

  public static final String IDP_CATALOG_CONNECTOR = "IDP_CATALOG_CONNECTOR";
  public static final String GITOPS_REPOSITORY = "GITOPS_REPOSITORY";
  public static final String GITOPS_CLUSTER = "GITOPS_CLUSTER";
  public static final String GITOPS_CREDENTIAL_TEMPLATE = "GITOPS_CREDENTIAL_TEMPLATE";
  public static final String GITOPS_REPOSITORY_CERTIFICATE = "GITOPS_REPOSITORY_CERTIFICATE";
  public static final String GITOPS_GNUPG_KEY = "GITOPS_GNUPG_KEY";
  public static final String GITOPS_AGENT = "GITOPS_AGENT";
  public static final String GITOPS_PROJECT_MAPPING = "GITOPS_PROJECT_MAPPING";
  public static final String GITOPS_APPLICATION = "GITOPS_APPLICATION";

  public static final String CODE_REPOSITORY = "CODE_REPOSITORY";
}
