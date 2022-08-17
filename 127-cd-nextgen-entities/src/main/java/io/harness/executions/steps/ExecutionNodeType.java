/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.executions.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.YamlTypes;

@OwnedBy(HarnessTeam.CDP)
public enum ExecutionNodeType {
  GITOPS_CREATE_PR("GITOPS_CREATE_PR", StepSpecTypeConstants.GITOPS_CREATE_PR),
  GITOPS_MERGE_PR("GITOPS_MERGE_PR", StepSpecTypeConstants.GITOPS_MERGE_PR),
  SERVICE("SERVICE", YamlTypes.SERVICE_ENTITY),
  SERVICE_V2("SERVICE_V2", YamlTypes.SERVICE_ENTITY),
  SERVICE_V3("SERVICE_V3", YamlTypes.SERVICE_ENTITY),
  ENVIRONMENT("ENVIRONMENT", YamlTypes.ENVIRONMENT_YAML),
  ENVIRONMENT_V2("ENVIRONMENT_V2", YamlTypes.ENVIRONMENT_YAML),
  SERVICE_CONFIG("SERVICE_CONFIG", YamlTypes.SERVICE_CONFIG),
  SERVICE_SECTION("SERVICE_SECTION", YamlTypes.SERVICE_SECTION),
  SERVICE_DEFINITION("SERVICE_DEFINITION", YamlTypes.SERVICE_DEFINITION),
  SERVICE_SPEC("SERVICE_SPEC", YamlTypes.SERVICE_SPEC),
  ARTIFACTS("ARTIFACTS", "artifacts"),
  ARTIFACTS_V2("ARTIFACTS_V2", "artifacts"),
  ARTIFACT("ARTIFACT", "artifact"),
  ARTIFACT_SYNC("ARTIFACT_SYNC", "artifact"),
  SIDECARS("SIDECARS", "sidecars"),
  MANIFESTS("MANIFESTS", "manifests"),
  MANIFEST_FETCH("MANIFEST_FETCH", YamlTypes.MANIFEST_LIST_CONFIG),
  MANIFEST("MANIFEST", YamlTypes.MANIFEST_CONFIG),
  PIPELINE_SETUP("PIPELINE_SETUP", "pipeline"),
  INFRASTRUCTURE_SECTION("INFRASTRUCTURE_SECTION", YamlTypes.PIPELINE_INFRASTRUCTURE),
  INFRASTRUCTURE("INFRASTRUCTURE", YamlTypes.INFRASTRUCTURE_DEF),
  INFRASTRUCTURE_V2("INFRASTRUCTURE_V2", YamlTypes.INFRASTRUCTURE_DEF),
  GITOPS_CLUSTERS("GITOPS CLUSTERS", YamlTypes.GITOPS_CLUSTERS),
  DEPLOYMENT_STAGE_STEP("DEPLOYMENT_STAGE_STEP", "deployment"),
  K8S_ROLLING("K8S_ROLLING", YamlTypes.K8S_ROLLING_DEPLOY),
  K8S_ROLLBACK_ROLLING("K8S_ROLLBACK_ROLLING", YamlTypes.K8S_ROLLING_ROLLBACK),
  K8S_BLUE_GREEN("K8S_BLUE_GREEN", YamlTypes.K8S_BLUE_GREEN_DEPLOY),
  K8S_APPLY("K8S_APPLY", YamlTypes.K8S_APPLY),
  K8S_SCALE("K8S_SCALE", YamlTypes.K8S_SCALE),
  K8S_CANARY("K8S_CANARY", YamlTypes.K8S_CANARY_DEPLOY),
  K8S_BG_SWAP_SERVICES("K8S_BG_SWAP_SERVICES", YamlTypes.K8S_BG_SWAP_SERVICES),
  K8S_DELETE("K8S_DELETE", YamlTypes.K8S_DELETE),
  K8S_CANARY_DELETE("K8S_CANARY_DELETE", YamlTypes.K8S_CANARY_DELETE),
  INFRASTRUCTURE_PROVISIONER_STEP("INFRASTRUCTURE_PROVISIONER_STEP", "infraProvisionerStep"),
  CD_EXECUTION_STEP("CD_EXECUTION_STEP", "cdExecutionStep"),
  CD_STEPS_STEP("CD_EXECUTION_STEP", "cdExecutionStep"),
  INFRASTRUCTURE_DEFINITION_STEP("INFRASTRUCTURE_DEFINITION_STEP", "infraDefStep"),
  EXECUTION_ROLLBACK_STEP("EXECUTION_ROLLBACK_STEP", "executionRollbackStep"),
  ROLLBACK_SECTION("ROLLBACK_SECTION", "rollbackSection"),
  GENERIC_SECTION("GENERIC_SECTION", "genericSection"),
  TERRAFORM_APPLY("TERRAFORM_APPLY", StepSpecTypeConstants.TERRAFORM_APPLY),
  TERRAFORM_PLAN("TERRAFORM_PLAN", StepSpecTypeConstants.TERRAFORM_PLAN),
  TERRAFORM_DESTROY("TERRAFORM_DESTROY", StepSpecTypeConstants.TERRAFORM_DESTROY),
  TERRAFORM_ROLLBACK("TERRAFORM_ROLLBACK", StepSpecTypeConstants.TERRAFORM_ROLLBACK),
  HELM_DEPLOY("HELM_DEPLOY", YamlTypes.HELM_DEPLOY),
  HELM_ROLLBACK("HELM_ROLLBACK", YamlTypes.HELM_ROLLBACK),
  CLOUDFORMATION_CREATE_STACK("CLOUDFORMATION_CREATE_STACK", StepSpecTypeConstants.CLOUDFORMATION_CREATE_STACK),
  CLOUDFORMATION_DELETE_STACK("CLOUDFORMATION_DELETE_STACK", StepSpecTypeConstants.CLOUDFORMATION_DELETE_STACK),
  CLOUDFORMATION_ROLLBACK_STACK("CLOUDFORMATION_ROLLBACK_STACK", StepSpecTypeConstants.CLOUDFORMATION_ROLLBACK_STACK),
  SERVERLESS_AWS_LAMBDA_DEPLOY("SERVERLESS_AWS_LAMBDA_DEPLOY", YamlTypes.SERVERLESS_AWS_LAMBDA_DEPLOY),
  SERVERLESS_AWS_LAMBDA_ROLLBACK("SERVERLESS_AWS_LAMBDA_ROLLBACK", YamlTypes.SERVERLESS_AWS_LAMBDA_ROLLBACK),
  CONFIG_FILE("CONFIG_FILE", YamlTypes.CONFIG_FILE),
  CONFIG_FILES("CONFIG_FILES", YamlTypes.CONFIG_FILES),
  COMMAND("COMMAND", YamlTypes.COMMAND),
  AZURE_SLOT_DEPLOYMENT("AZURE_SLOT_DEPLOYMENT", YamlTypes.AZURE_SLOT_DEPLOYMENT),
  AZURE_TRAFFIC_SHIFT("AZURE_TRAFFIC_SHIFT", YamlTypes.AZURE_TRAFFIC_SHIFT),
  AZURE_SWAP_SLOT("AZURE_SWAP_SLOT", YamlTypes.AZURE_SWAP_SLOT),
  AZURE_WEBAPP_ROLLBACK("AZURE_WEBAPP_ROLLBACK", YamlTypes.AZURE_WEBAPP_ROLLBACK),
  JENKINS_BUILD("JENKINS_BUILD", StepSpecTypeConstants.JENKINS_BUILD),
  STARTUP_COMMAND("STARTUP_COMMAND", YamlTypes.STARTUP_COMMAND),
  APPLICATION_SETTINGS("APPLICATION_SETTINGS", YamlTypes.APPLICATION_SETTINGS),
  CONNECTION_STRINGS("CONNECTION_STRINGS", YamlTypes.CONNECTION_STRINGS),
  AZURE_CREATE_ARM_RESOURCE("AZURE_CREATE_ARM_RESOURCE", StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE);

  private final String name;
  private final String yamlType;

  ExecutionNodeType(String name, String yamlType) {
    this.name = name;
    this.yamlType = yamlType;
  }

  public String getName() {
    return name;
  }

  public String getYamlType() {
    return yamlType;
  }
}
