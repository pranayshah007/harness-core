/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InputSetReference;
import io.harness.beans.NGTemplateReference;
import io.harness.beans.TriggerReference;
import io.harness.common.EntityReference;
import io.harness.common.EntityTypeConstants;
import io.harness.common.EntityYamlRootNames;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.DX)
// todo(abhinav): refactor/adapt this according to needs later depending on how service registration comes in
// one more enum might come in here for product types.
public enum EntityType {
  @JsonProperty(EntityTypeConstants.PROJECTS)
  PROJECTS(ModuleType.CORE, EntityTypeConstants.PROJECTS, IdentifierRef.class, EntityYamlRootNames.PROJECT),
  @JsonProperty(EntityTypeConstants.PIPELINES)
  PIPELINES(ModuleType.CD, EntityTypeConstants.PIPELINES, IdentifierRef.class, EntityYamlRootNames.PIPELINE),
  @JsonProperty(EntityTypeConstants.PIPELINE_STEPS)
  PIPELINE_STEPS(
      ModuleType.CD, EntityTypeConstants.PIPELINE_STEPS, IdentifierRef.class, EntityYamlRootNames.PIPELINE_STEP),
  @JsonProperty(EntityTypeConstants.HTTP)
  HTTP_STEP(ModuleType.PMS, EntityTypeConstants.HTTP, IdentifierRef.class, EntityYamlRootNames.HTTP),
  @JsonProperty(EntityTypeConstants.JIRA_CREATE)
  JIRA_CREATE_STEP(
      ModuleType.PMS, EntityTypeConstants.JIRA_CREATE, IdentifierRef.class, EntityYamlRootNames.JIRA_CREATE),
  @JsonProperty(EntityTypeConstants.JIRA_UPDATE)
  JIRA_UPDATE_STEP(
      ModuleType.PMS, EntityTypeConstants.JIRA_UPDATE, IdentifierRef.class, EntityYamlRootNames.JIRA_UPDATE),
  @JsonProperty(EntityTypeConstants.JIRA_APPROVAL)
  JIRA_APPROVAL_STEP(
      ModuleType.PMS, EntityTypeConstants.JIRA_APPROVAL, IdentifierRef.class, EntityYamlRootNames.JIRA_APPROVAL),
  @JsonProperty(EntityTypeConstants.HARNESS_APPROVAL)
  HARNESS_APPROVAL_STEP(
      ModuleType.PMS, EntityTypeConstants.HARNESS_APPROVAL, IdentifierRef.class, EntityYamlRootNames.HARNESS_APPROVAL),
  @JsonProperty(EntityTypeConstants.BARRIER)
  BARRIER_STEP(ModuleType.PMS, EntityTypeConstants.BARRIER, IdentifierRef.class, EntityYamlRootNames.BARRIER),
  @JsonProperty(EntityTypeConstants.FlagConfiguration)
  FLAG_CONFIGURATION(ModuleType.CF, EntityTypeConstants.FlagConfiguration, IdentifierRef.class,
      EntityYamlRootNames.FLAG_CONFIGURATION),
  @JsonProperty(EntityTypeConstants.SHELL_SCRIPT)
  SHELL_SCRIPT_STEP(
      ModuleType.PMS, EntityTypeConstants.SHELL_SCRIPT, IdentifierRef.class, EntityYamlRootNames.SHELL_SCRIPT),
  @JsonProperty(EntityTypeConstants.K8S_CANARY_DEPLOY)
  K8S_CANARY_DEPLOY_STEP(
      ModuleType.CD, EntityTypeConstants.K8S_CANARY_DEPLOY, IdentifierRef.class, EntityYamlRootNames.K8S_CANARY_DEPLOY),
  @JsonProperty(EntityTypeConstants.K8S_APPLY)
  K8S_APPLY_STEP(ModuleType.CD, EntityTypeConstants.K8S_APPLY, IdentifierRef.class, EntityYamlRootNames.K8S_APPLY),
  @JsonProperty(EntityTypeConstants.K8S_BLUE_GREEN_DEPLOY)
  K8S_BLUE_GREEN_DEPLOY_STEP(ModuleType.CD, EntityTypeConstants.K8S_BLUE_GREEN_DEPLOY, IdentifierRef.class,
      EntityYamlRootNames.K8S_BLUE_GREEN_DEPLOY),
  @JsonProperty(EntityTypeConstants.K8S_ROLLING_DEPLOY)
  K8S_ROLLING_DEPLOY_STEP(ModuleType.CD, EntityTypeConstants.K8S_ROLLING_DEPLOY, IdentifierRef.class,
      EntityYamlRootNames.K8S_ROLLING_DEPLOY),
  @JsonProperty(EntityTypeConstants.K8S_ROLLING_ROLLBACK)
  K8S_ROLLING_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.K8S_ROLLING_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.K8S_ROLLING_ROLLBACK),
  @JsonProperty(EntityTypeConstants.K8S_SCALE)
  K8S_SCALE_STEP(ModuleType.CD, EntityTypeConstants.K8S_SCALE, IdentifierRef.class, EntityYamlRootNames.K8S_SCALE),
  @JsonProperty(EntityTypeConstants.K8S_DELETE)
  K8S_DELETE_STEP(ModuleType.CD, EntityTypeConstants.K8S_DELETE, IdentifierRef.class, EntityYamlRootNames.K8S_DELETE),
  @JsonProperty(EntityTypeConstants.K8S_BG_SWAP_SERVICES)
  K8S_BG_SWAP_SERVICES_STEP(ModuleType.CD, EntityTypeConstants.K8S_BG_SWAP_SERVICES, IdentifierRef.class,
      EntityYamlRootNames.K8S_SWAP_SERVICES),
  @JsonProperty(EntityTypeConstants.K8S_CANARY_DELETE)
  K8S_CANARY_DELETE_STEP(
      ModuleType.CD, EntityTypeConstants.K8S_CANARY_DELETE, IdentifierRef.class, EntityYamlRootNames.K8S_CANARY_DELETE),
  @JsonProperty(EntityTypeConstants.TERRAFORM_APPLY)
  TERRAFORM_APPLY_STEP(
      ModuleType.CD, EntityTypeConstants.TERRAFORM_APPLY, IdentifierRef.class, EntityYamlRootNames.TERRAFORM_APPLY),
  @JsonProperty(EntityTypeConstants.TERRAFORM_PLAN)
  TERRAFORM_PLAN_STEP(
      ModuleType.CD, EntityTypeConstants.TERRAFORM_PLAN, IdentifierRef.class, EntityYamlRootNames.TERRAFORM_PLAN),
  @JsonProperty(EntityTypeConstants.TERRAFORM_DESTROY)
  TERRAFORM_DESTROY_STEP(
      ModuleType.CD, EntityTypeConstants.TERRAFORM_DESTROY, IdentifierRef.class, EntityYamlRootNames.TERRAFORM_DESTROY),
  @JsonProperty(EntityTypeConstants.TERRAFORM_ROLLBACK)
  TERRAFORM_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.TERRAFORM_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.TERRAFORM_ROLLBACK),
  @JsonProperty(EntityTypeConstants.HELM_DEPLOY)
  HELM_DEPLOY_STEP(
      ModuleType.CD, EntityTypeConstants.HELM_DEPLOY, IdentifierRef.class, EntityYamlRootNames.HELM_DEPLOY),
  @JsonProperty(EntityTypeConstants.HELM_ROLLBACK)
  HELM_ROLLBACK_STEP(
      ModuleType.CD, EntityTypeConstants.HELM_ROLLBACK, IdentifierRef.class, EntityYamlRootNames.HELM_ROLLBACK),
  @JsonProperty(EntityTypeConstants.CONNECTORS)
  CONNECTORS(ModuleType.CORE, EntityTypeConstants.CONNECTORS, IdentifierRef.class, EntityYamlRootNames.CONNECTOR),
  @JsonProperty(EntityTypeConstants.SECRETS)
  SECRETS(ModuleType.CORE, EntityTypeConstants.SECRETS, IdentifierRef.class, EntityYamlRootNames.SECRET),
  @JsonProperty(EntityTypeConstants.FILES)
  FILES(ModuleType.CORE, EntityTypeConstants.FILES, IdentifierRef.class, EntityYamlRootNames.FILE),
  @JsonProperty(EntityTypeConstants.SERVICE)
  SERVICE(ModuleType.CORE, EntityTypeConstants.SERVICE, IdentifierRef.class, EntityYamlRootNames.SERVICE),
  @JsonProperty(EntityTypeConstants.ENVIRONMENT)
  ENVIRONMENT(ModuleType.CORE, EntityTypeConstants.ENVIRONMENT, IdentifierRef.class, EntityYamlRootNames.ENVIRONMENT),
  @JsonProperty(EntityTypeConstants.ENVIRONMENT_GROUP)
  ENVIRONMENT_GROUP(ModuleType.CORE, EntityTypeConstants.ENVIRONMENT_GROUP, IdentifierRef.class,
      EntityYamlRootNames.ENVIRONMENT_GROUP),
  @JsonProperty(EntityTypeConstants.INPUT_SETS)
  INPUT_SETS(ModuleType.CORE, EntityTypeConstants.INPUT_SETS, InputSetReference.class, EntityYamlRootNames.INPUT_SET,
      EntityYamlRootNames.OVERLAY_INPUT_SET),
  @JsonProperty(EntityTypeConstants.CV_CONFIG)
  CV_CONFIG(ModuleType.CV, EntityTypeConstants.CV_CONFIG, IdentifierRef.class, EntityYamlRootNames.CV_CONFIG),
  @JsonProperty(EntityTypeConstants.Verify)
  VERIFY_STEP(ModuleType.CV, EntityTypeConstants.Verify, IdentifierRef.class, EntityYamlRootNames.VERIFY),
  @JsonProperty(EntityTypeConstants.DELEGATES)
  DELEGATES(ModuleType.CORE, EntityTypeConstants.DELEGATES, IdentifierRef.class, EntityYamlRootNames.DELEGATE),
  @JsonProperty(EntityTypeConstants.DELEGATE_CONFIGURATIONS)
  DELEGATE_CONFIGURATIONS(ModuleType.CORE, EntityTypeConstants.DELEGATE_CONFIGURATIONS, IdentifierRef.class,
      EntityYamlRootNames.DELEGATE_CONFIGURATION),
  @JsonProperty(EntityTypeConstants.CV_VERIFICATION_JOB)
  CV_VERIFICATION_JOB(ModuleType.CV, EntityTypeConstants.CV_VERIFICATION_JOB, IdentifierRef.class,
      EntityYamlRootNames.CV_VERIFICATION_JOB),
  @JsonProperty(EntityTypeConstants.INTEGRATION_STAGE)
  INTEGRATION_STAGE(
      ModuleType.CI, EntityTypeConstants.INTEGRATION_STAGE, IdentifierRef.class, EntityYamlRootNames.INTEGRATION_STAGE),
  @JsonProperty(EntityTypeConstants.INTEGRATION_STEPS)
  INTEGRATION_STEPS(
      ModuleType.CI, EntityTypeConstants.INTEGRATION_STEPS, IdentifierRef.class, EntityYamlRootNames.INTEGRATION_STEP),
  @JsonProperty(EntityTypeConstants.SECURITY_STAGE)
  SECURITY_STAGE(
      ModuleType.STO, EntityTypeConstants.SECURITY_STAGE, IdentifierRef.class, EntityYamlRootNames.SECURITY_STAGE),
  @JsonProperty(EntityTypeConstants.SECURITY_STEPS)
  SECURITY_STEPS(
      ModuleType.STO, EntityTypeConstants.SECURITY_STEPS, IdentifierRef.class, EntityYamlRootNames.SECURITY_STEP),
  @JsonProperty(EntityTypeConstants.CV_KUBERNETES_ACTIVITY_SOURCE)
  CV_KUBERNETES_ACTIVITY_SOURCE(ModuleType.CV, EntityTypeConstants.CV_KUBERNETES_ACTIVITY_SOURCE, IdentifierRef.class,
      EntityYamlRootNames.CV_KUBERNETES_ACTIVITY_SOURCE),

  @JsonProperty(EntityTypeConstants.DEPLOYMENT_STEPS)
  DEPLOYMENT_STEPS(
      ModuleType.CD, EntityTypeConstants.DEPLOYMENT_STEPS, IdentifierRef.class, EntityYamlRootNames.DEPLOYMENT_STEP),
  @JsonProperty(EntityTypeConstants.DEPLOYMENT_STAGE)
  DEPLOYMENT_STAGE(
      ModuleType.CD, EntityTypeConstants.DEPLOYMENT_STAGE, IdentifierRef.class, EntityYamlRootNames.DEPLOYMENT_STAGE),
  @JsonProperty(EntityTypeConstants.APPROVAL_STAGE)
  APPROVAL_STAGE(
      ModuleType.PMS, EntityTypeConstants.APPROVAL_STAGE, IdentifierRef.class, EntityYamlRootNames.APPROVAL_STAGE),
  @JsonProperty(EntityTypeConstants.FEATURE_FLAG_STAGE)
  FEATURE_FLAG_STAGE(ModuleType.CF, EntityTypeConstants.FEATURE_FLAG_STAGE, IdentifierRef.class,
      EntityYamlRootNames.FEATURE_FLAG_STAGE),
  @JsonProperty(EntityTypeConstants.TEMPLATE)
  TEMPLATE(ModuleType.TEMPLATESERVICE, EntityTypeConstants.TEMPLATE, NGTemplateReference.class,
      EntityYamlRootNames.TEMPLATE),
  @JsonProperty(EntityTypeConstants.TRIGGERS)
  TRIGGERS(ModuleType.CD, EntityTypeConstants.TRIGGERS, TriggerReference.class, EntityYamlRootNames.TRIGGERS),
  @JsonProperty(EntityTypeConstants.MONITORED_SERVICE)
  MONITORED_SERVICE(
      ModuleType.CV, EntityTypeConstants.MONITORED_SERVICE, IdentifierRef.class, EntityYamlRootNames.MONITORED_SERVICE),
  @JsonProperty(EntityTypeConstants.GIT_REPOSITORIES)
  GIT_REPOSITORIES(
      ModuleType.CORE, EntityTypeConstants.GIT_REPOSITORIES, IdentifierRef.class, EntityYamlRootNames.GIT_REPOSITORY),
  @JsonProperty(EntityTypeConstants.FEATURE_FLAGS)
  FEATURE_FLAGS(
      ModuleType.CF, EntityTypeConstants.FEATURE_FLAGS, IdentifierRef.class, EntityYamlRootNames.FEATURE_FLAGS),
  @JsonProperty(EntityTypeConstants.SERVICENOW_APPROVAL)
  SERVICENOW_APPROVAL_STEP(ModuleType.CD, EntityTypeConstants.SERVICENOW_APPROVAL, IdentifierRef.class,
      EntityYamlRootNames.SERVICENOW_APPROVAL),
  @JsonProperty(EntityTypeConstants.SERVICENOW_CREATE)
  SERVICENOW_CREATE_STEP(ModuleType.PMS, EntityTypeConstants.SERVICENOW_CREATE, IdentifierRef.class,
      EntityYamlRootNames.SERVICENOW_CREATE),
  @JsonProperty(EntityTypeConstants.SERVICENOW_UPDATE)
  SERVICENOW_UPDATE_STEP(ModuleType.PMS, EntityTypeConstants.SERVICENOW_UPDATE, IdentifierRef.class,
      EntityYamlRootNames.SERVICENOW_UPDATE),
  @JsonProperty(EntityTypeConstants.OPAPOLICIES)
  OPAPOLICIES(ModuleType.CORE, EntityTypeConstants.OPAPOLICIES, IdentifierRef.class, EntityYamlRootNames.OPAPOLICY),
  POLICY_STEP(ModuleType.PMS, EntityTypeConstants.POLICY_STEP, IdentifierRef.class, EntityYamlRootNames.POLICY_STEP),
  @JsonProperty(EntityTypeConstants.RUN_STEP)
  RUN_STEP(ModuleType.CI, EntityTypeConstants.RUN_STEP, IdentifierRef.class, EntityYamlRootNames.RUN_STEP),
  @JsonProperty(EntityTypeConstants.RUN_TEST)
  RUN_TEST(ModuleType.CI, EntityTypeConstants.RUN_TEST, IdentifierRef.class, EntityYamlRootNames.RUN_TEST),
  @JsonProperty(EntityTypeConstants.PLUGIN)
  PLUGIN(ModuleType.CI, EntityTypeConstants.PLUGIN, IdentifierRef.class, EntityYamlRootNames.PLUGIN),
  @JsonProperty(EntityTypeConstants.RESTORE_CACHE_GCS)
  RESTORE_CACHE_GCS(
      ModuleType.CI, EntityTypeConstants.RESTORE_CACHE_GCS, IdentifierRef.class, EntityYamlRootNames.RESTORE_CACHE_GCS),
  @JsonProperty(EntityTypeConstants.RESTORE_CACHE_S3)
  RESTORE_CACHE_S3(
      ModuleType.CI, EntityTypeConstants.RESTORE_CACHE_S3, IdentifierRef.class, EntityYamlRootNames.RESTORE_CACHE_S3),
  @JsonProperty(EntityTypeConstants.SAVE_CACHE_GCS)
  SAVE_CACHE_GCS(
      ModuleType.CI, EntityTypeConstants.SAVE_CACHE_GCS, IdentifierRef.class, EntityYamlRootNames.SAVE_CACHE_GCS),
  @JsonProperty(EntityTypeConstants.SAVE_CACHE_S3)
  SAVE_CACHE_S3(
      ModuleType.CI, EntityTypeConstants.SAVE_CACHE_S3, IdentifierRef.class, EntityYamlRootNames.SAVE_CACHE_S3),
  @JsonProperty(EntityTypeConstants.SECURITY)
  SECURITY(ModuleType.CI, EntityTypeConstants.SECURITY, IdentifierRef.class, EntityYamlRootNames.SECURITY),
  @JsonProperty(EntityTypeConstants.ARTIFACTORY_UPLOAD)
  ARTIFACTORY_UPLOAD(ModuleType.CI, EntityTypeConstants.ARTIFACTORY_UPLOAD, IdentifierRef.class,
      EntityYamlRootNames.ARTIFACTORY_UPLOAD),
  @JsonProperty(EntityTypeConstants.GCS_UPLOAD)
  GCS_UPLOAD(ModuleType.CI, EntityTypeConstants.GCS_UPLOAD, IdentifierRef.class, EntityYamlRootNames.GCS_UPLOAD),
  @JsonProperty(EntityTypeConstants.S3_UPLOAD)
  S3_UPLOAD(ModuleType.CI, EntityTypeConstants.S3_UPLOAD, IdentifierRef.class, EntityYamlRootNames.S3_UPLOAD),
  @JsonProperty(EntityTypeConstants.BUILD_AND_PUSH_GCR)
  BUILD_AND_PUSH_GCR(ModuleType.CI, EntityTypeConstants.BUILD_AND_PUSH_GCR, IdentifierRef.class,
      EntityYamlRootNames.BUILD_AND_PUSH_GCR),
  @JsonProperty(EntityTypeConstants.BUILD_AND_PUSH_ECR)
  BUILD_AND_PUSH_ECR(ModuleType.CI, EntityTypeConstants.BUILD_AND_PUSH_ECR, IdentifierRef.class,
      EntityYamlRootNames.BUILD_AND_PUSH_ECR),
  @JsonProperty(EntityTypeConstants.BUILD_AND_PUSH_DOCKER_REGISTRY)
  BUILD_AND_PUSH_DOCKER_REGISTRY(ModuleType.CI, EntityTypeConstants.BUILD_AND_PUSH_DOCKER_REGISTRY, IdentifierRef.class,
      EntityYamlRootNames.BUILD_AND_PUSH_DOCKER_REGISTRY),
  @JsonProperty(EntityTypeConstants.CLOUDFORMATION_CREATE_STACK_STEP)
  CLOUDFORMATION_CREATE_STACK_STEP(ModuleType.CD, EntityTypeConstants.CLOUDFORMATION_CREATE_STACK_STEP,
      IdentifierRef.class, EntityYamlRootNames.CLOUDFORMATION_CREATE_STACK_STEP),
  @JsonProperty(EntityTypeConstants.CLOUDFORMATION_DELETE_STACK_STEP)
  CLOUDFORMATION_DELETE_STACK_STEP(ModuleType.CD, EntityTypeConstants.CLOUDFORMATION_DELETE_STACK_STEP,
      IdentifierRef.class, EntityYamlRootNames.CLOUDFORMATION_DELETE_STACK_STEP),
  @JsonProperty(EntityTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY)
  SERVERLESS_AWS_LAMBDA_DEPLOY_STEP(ModuleType.CD, EntityTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY,
      IdentifierRef.class, EntityYamlRootNames.SERVERLESS_AWS_LAMBDA_DEPLOY),
  @JsonProperty(EntityTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK)
  SERVERLESS_AWS_LAMBDA_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK,
      IdentifierRef.class, EntityYamlRootNames.SERVERLESS_AWS_LAMBDA_ROLLBACK),
  @JsonProperty(EntityTypeConstants.CUSTOM_STAGE)
  CUSTOM_STAGE(ModuleType.PMS, EntityTypeConstants.CUSTOM_STAGE, IdentifierRef.class, EntityYamlRootNames.CUSTOM_STAGE),
  @JsonProperty(EntityTypeConstants.CLOUDFORMATION_ROLLBACK_STACK_STEP)
  CLOUDFORMATION_ROLLBACK_STACK_STEP(ModuleType.CD, EntityTypeConstants.CLOUDFORMATION_ROLLBACK_STACK_STEP,
      IdentifierRef.class, EntityYamlRootNames.CLOUDFORMATION_ROLLBACK_STACK_STEP),
  @JsonProperty(EntityTypeConstants.INFRASTRUCTURE)
  INFRASTRUCTURE(
      ModuleType.CORE, EntityTypeConstants.INFRASTRUCTURE, IdentifierRef.class, EntityYamlRootNames.INFRASTRUCTURE);

  private final ModuleType moduleType;
  String yamlName;
  List<String> yamlRootElementString;
  Class<? extends EntityReference> entityReferenceClass;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static EntityType fromString(@JsonProperty("type") String entityType) {
    for (EntityType entityTypeEnum : EntityType.values()) {
      if (entityTypeEnum.getYamlName().equalsIgnoreCase(entityType)) {
        return entityTypeEnum;
      }
      if (entityTypeEnum.name().equalsIgnoreCase(entityType)) {
        return entityTypeEnum;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + entityType);
  }

  public static List<EntityType> getEntityTypes(ModuleType moduleType) {
    return (moduleType == null)
        ? Collections.emptyList()
        : Arrays.stream(EntityType.values())
              .filter(entityType -> entityType.moduleType.name().equalsIgnoreCase(moduleType.name()))
              .collect(Collectors.toList());
  }

  public static EntityType getEntityFromYamlType(String yamlType) {
    return Arrays.stream(EntityType.values())
        .filter(value -> value.yamlName.equalsIgnoreCase(yamlType))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid value: " + yamlType));
  }

  public static EntityType getEntityTypeFromYamlRootName(String yamlRootString) {
    return Arrays.stream(EntityType.values())
        .filter(entityType -> entityType.yamlRootElementString.stream().anyMatch(yamlRootString::equalsIgnoreCase))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Invalid value: " + yamlRootString));
  }

  public ModuleType getEntityProduct() {
    return this.moduleType;
  }

  EntityType(ModuleType moduleType, String yamlName, Class<? extends EntityReference> entityReferenceClass,
      String... yamlRootStrings) {
    this.moduleType = moduleType;
    this.yamlName = yamlName;
    this.entityReferenceClass = entityReferenceClass;
    this.yamlRootElementString = Lists.newArrayList(yamlRootStrings);
  }

  public String getYamlName() {
    return yamlName;
  }

  @Override
  @JsonValue
  public String toString() {
    return this.yamlName;
  }
}
