/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
public class SettingIdentifiers {
  public static String DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER = "disable_harness_built_in_secret_manager";
  public static String ENABLE_FORCE_DELETE = "enable_force_delete";
  public static String SCIM_JWT_TOKEN_CONFIGURATION_GROUP_IDENTIFIER = "scim_jwt_token_configuration_g1";
  public static String SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER = "scim_jwt_token_key_field";
  public static String SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER = "scim_jwt_token_value_field";
  public static String SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER = "scim_jwt_token_jwks_keys_url";
  public static String SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER = "scim_jwt_token_service_account_id";
  public static String PIPELINE_TIMEOUT_IDENTIFIER = "pipeline_timeout";
  public static String STAGE_TIMEOUT_IDENTIFIER = "stage_timeout";
  public static String STEP_TIMEOUT_IDENTIFIER = "step_timeout";
  public static String CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS = "concurrent_active_pipeline_executions";
  public static String PERSPECTIVE_PREFERENCES_GROUP_IDENTIFIER = "perspective_preferences";
  public static String SHOW_ANOMALIES_IDENTIFIER = "show_anomalies";
  public static String SHOW_OTHERS_IDENTIFIER = "show_others";
  public static String SHOW_UNALLOCATED_CLUSTER_COST_IDENTIFIER = "show_unallocated_cluster_cost";
  public static String INCLUDE_AWS_DISCOUNTS_IDENTIFIER = "include_aws_discounts";
  public static String INCLUDE_AWS_CREDIT_IDENTIFIER = "include_aws_credit";
  public static String INCLUDE_AWS_REFUNDS_IDENTIFIER = "include_aws_refunds";
  public static String INCLUDE_AWS_TAXES_IDENTIFIER = "include_aws_taxes";
  public static String SHOW_AWS_COST_AS_IDENTIFIER = "show_aws_cost_as";
  public static String INCLUDE_GCP_DISCOUNTS_IDENTIFIER = "include_gcp_discounts";
  public static String INCLUDE_GCP_TAXES_IDENTIFIER = "include_gcp_taxes";
  public static String SERVICE_OVERRIDE_V2_IDENTIFIER = "service_override_v2";
  public static String EXPORT_SERVICE_VARS_AS_ENV_VARS = "export_service_variables_as_env_variables";
  public static String TRIGGER_FOR_ALL_ARTIFACTS_OR_MANIFESTS = "trigger_for_all_artifacts_or_manifests";
  public static String TICKETING_PREFERENCES_GROUP_IDENTIFIER = "ticketing_preferences";
  public static String TICKETING_TOOL_IDENTIFIER = "ticketing_tool";
  public static String TICKETING_TOOL_CONNECTOR_IDENTIFIER = "ticketing_tool_connector";
  public static String AIDA = "aida";
}
