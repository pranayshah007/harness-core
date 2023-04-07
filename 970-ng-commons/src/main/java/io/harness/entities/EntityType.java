/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InputSetReference;
import io.harness.beans.NGTemplateReference;
import io.harness.beans.TriggerReference;
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
  @JsonProperty(EntityTypeConstants.GITOPS_CREATE_PR)
  GITOPS_CREATE_PR(
      ModuleType.CD, EntityTypeConstants.GITOPS_CREATE_PR, IdentifierRef.class, EntityYamlRootNames.GITOPS_CREATE_PR),
  GITOPS_MERGE_PR(
      ModuleType.CD, EntityTypeConstants.GITOPS_MERGE_PR, IdentifierRef.class, EntityYamlRootNames.GITOPS_MERGE_PR),
  @JsonProperty(EntityTypeConstants.PROJECTS)
  PROJECTS(ModuleType.CORE, EntityTypeConstants.PROJECTS, IdentifierRef.class, EntityYamlRootNames.PROJECT),
  @JsonProperty(EntityTypeConstants.PIPELINES)
  PIPELINES(ModuleType.CD, EntityTypeConstants.PIPELINES, IdentifierRef.class, EntityYamlRootNames.PIPELINE),
  @JsonProperty(EntityTypeConstants.PIPELINE_STEPS)
  PIPELINE_STEPS(
      ModuleType.CD, EntityTypeConstants.PIPELINE_STEPS, IdentifierRef.class, EntityYamlRootNames.PIPELINE_STEP),
  @JsonProperty(EntityTypeConstants.HTTP)
  HTTP_STEP(ModuleType.PMS, EntityTypeConstants.HTTP, IdentifierRef.class, EntityYamlRootNames.HTTP),
  @JsonProperty(EntityTypeConstants.EMAIL)
  EMAIL_STEP(ModuleType.PMS, EntityTypeConstants.EMAIL, IdentifierRef.class, EntityYamlRootNames.EMAIL),
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
  @JsonProperty(EntityTypeConstants.CUSTOM_APPROVAL)
  CUSTOM_APPROVAL_STEP(
      ModuleType.PMS, EntityTypeConstants.CUSTOM_APPROVAL, IdentifierRef.class, EntityYamlRootNames.CUSTOM_APPROVAL),
  @JsonProperty(EntityTypeConstants.BARRIER)
  BARRIER_STEP(ModuleType.PMS, EntityTypeConstants.BARRIER, IdentifierRef.class, EntityYamlRootNames.BARRIER),
  @JsonProperty(EntityTypeConstants.QUEUE)
  QUEUE_STEP(ModuleType.PMS, EntityTypeConstants.QUEUE, IdentifierRef.class, EntityYamlRootNames.QUEUE),
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
  @JsonProperty(EntityTypeConstants.PIPELINE_STAGE)
  PIPELINE_STAGE(
      ModuleType.PMS, EntityTypeConstants.PIPELINE_STAGE, IdentifierRef.class, EntityYamlRootNames.PIPELINE_STAGE),
  @JsonProperty(EntityTypeConstants.FEATURE_FLAG_STAGE)
  FEATURE_FLAG_STAGE(ModuleType.CF, EntityTypeConstants.FEATURE_FLAG_STAGE, IdentifierRef.class,
      EntityYamlRootNames.FEATURE_FLAG_STAGE),
  @JsonProperty(EntityTypeConstants.TEMPLATE)
  TEMPLATE(ModuleType.TEMPLATESERVICE, EntityTypeConstants.TEMPLATE, NGTemplateReference.class,
      EntityYamlRootNames.TEMPLATE),
  @JsonProperty(EntityTypeConstants.TEMPLATE_STAGE)
  TEMPLATE_STAGE(ModuleType.TEMPLATESERVICE, EntityTypeConstants.TEMPLATE_STAGE, NGTemplateReference.class,
      EntityYamlRootNames.TEMPLATE),
  @JsonProperty(EntityTypeConstants.TEMPLATE_CUSTOM_DEPLOYMENT)
  TEMPLATE_CUSTOM_DEPLOYMENT(ModuleType.TEMPLATESERVICE, EntityTypeConstants.TEMPLATE_CUSTOM_DEPLOYMENT,
      NGTemplateReference.class, EntityYamlRootNames.TEMPLATE),
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
  SERVICENOW_APPROVAL_STEP(ModuleType.PMS, EntityTypeConstants.SERVICENOW_APPROVAL, IdentifierRef.class,
      EntityYamlRootNames.SERVICENOW_APPROVAL),
  @JsonProperty(EntityTypeConstants.SERVICENOW_CREATE)
  SERVICENOW_CREATE_STEP(ModuleType.PMS, EntityTypeConstants.SERVICENOW_CREATE, IdentifierRef.class,
      EntityYamlRootNames.SERVICENOW_CREATE),
  @JsonProperty(EntityTypeConstants.SERVICENOW_UPDATE)
  SERVICENOW_UPDATE_STEP(ModuleType.PMS, EntityTypeConstants.SERVICENOW_UPDATE, IdentifierRef.class,
      EntityYamlRootNames.SERVICENOW_UPDATE),
  @JsonProperty(EntityTypeConstants.SERVICENOW_IMPORT_SET)
  SERVICENOW_IMPORT_SET_STEP(ModuleType.PMS, EntityTypeConstants.SERVICENOW_IMPORT_SET, IdentifierRef.class,
      EntityYamlRootNames.SERVICENOW_IMPORT_SET),
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

  @JsonProperty(EntityTypeConstants.AQUA_TRIVY)
  AQUA_TRIVY(ModuleType.STO, EntityTypeConstants.AQUA_TRIVY, IdentifierRef.class, EntityYamlRootNames.AQUA_TRIVY),
  @JsonProperty(EntityTypeConstants.AWS_ECR)
  AWS_ECR(ModuleType.STO, EntityTypeConstants.AWS_ECR, IdentifierRef.class, EntityYamlRootNames.AWS_ECR),

  @JsonProperty(EntityTypeConstants.BANDIT)
  BANDIT(ModuleType.STO, EntityTypeConstants.BANDIT, IdentifierRef.class, EntityYamlRootNames.BANDIT),
  @JsonProperty(EntityTypeConstants.BLACKDUCK)
  BLACKDUCK(ModuleType.STO, EntityTypeConstants.BLACKDUCK, IdentifierRef.class, EntityYamlRootNames.BLACKDUCK),
  @JsonProperty(EntityTypeConstants.BRAKEMAN)
  BRAKEMAN(ModuleType.STO, EntityTypeConstants.BRAKEMAN, IdentifierRef.class, EntityYamlRootNames.BRAKEMAN),
  @JsonProperty(EntityTypeConstants.BURP)
  BURP(ModuleType.STO, EntityTypeConstants.BURP, IdentifierRef.class, EntityYamlRootNames.BURP),
  @JsonProperty(EntityTypeConstants.CHECKMARX)
  CHECKMARX(ModuleType.STO, EntityTypeConstants.CHECKMARX, IdentifierRef.class, EntityYamlRootNames.CHECKMARX),
  @JsonProperty(EntityTypeConstants.CLAIR)
  CLAIR(ModuleType.STO, EntityTypeConstants.CLAIR, IdentifierRef.class, EntityYamlRootNames.CLAIR),
  @JsonProperty(EntityTypeConstants.DATA_THEOREM)
  DATA_THEOREM(ModuleType.STO, EntityTypeConstants.DATA_THEOREM, IdentifierRef.class, EntityYamlRootNames.DATA_THEOREM),
  @JsonProperty(EntityTypeConstants.DOCKER_CONTENT_TRUST)
  DOCKER_CONTENT_TRUST(ModuleType.STO, EntityTypeConstants.DOCKER_CONTENT_TRUST, IdentifierRef.class,
      EntityYamlRootNames.DOCKER_CONTENT_TRUST),
  @JsonProperty(EntityTypeConstants.EXTERNAL)
  EXTERNAL(ModuleType.STO, EntityTypeConstants.EXTERNAL, IdentifierRef.class, EntityYamlRootNames.EXTERNAL),
  @JsonProperty(EntityTypeConstants.FORTIFY_ON_DEMAND)
  FORTIFY_ON_DEMAND(ModuleType.STO, EntityTypeConstants.FORTIFY_ON_DEMAND, IdentifierRef.class,
      EntityYamlRootNames.FORTIFY_ON_DEMAND),
  @JsonProperty(EntityTypeConstants.GRYPE)
  GRYPE(ModuleType.STO, EntityTypeConstants.GRYPE, IdentifierRef.class, EntityYamlRootNames.GRYPE),
  @JsonProperty(EntityTypeConstants.JFROG_XRAY)
  JFROG_XRAY(ModuleType.STO, EntityTypeConstants.JFROG_XRAY, IdentifierRef.class, EntityYamlRootNames.JFROG_XRAY),
  @JsonProperty(EntityTypeConstants.MEND)
  MEND(ModuleType.STO, EntityTypeConstants.MEND, IdentifierRef.class, EntityYamlRootNames.MEND),
  @JsonProperty(EntityTypeConstants.METASPLOIT)
  METASPLOIT(ModuleType.STO, EntityTypeConstants.METASPLOIT, IdentifierRef.class, EntityYamlRootNames.METASPLOIT),
  @JsonProperty(EntityTypeConstants.NESSUS)
  NESSUS(ModuleType.STO, EntityTypeConstants.NESSUS, IdentifierRef.class, EntityYamlRootNames.NESSUS),
  @JsonProperty(EntityTypeConstants.NEXUS_IQ)
  NEXUS_IQ(ModuleType.STO, EntityTypeConstants.NEXUS_IQ, IdentifierRef.class, EntityYamlRootNames.NEXUS_IQ),
  @JsonProperty(EntityTypeConstants.NIKTO)
  NIKTO(ModuleType.STO, EntityTypeConstants.NIKTO, IdentifierRef.class, EntityYamlRootNames.NIKTO),
  @JsonProperty(EntityTypeConstants.NMAP)
  NMAP(ModuleType.STO, EntityTypeConstants.NMAP, IdentifierRef.class, EntityYamlRootNames.NMAP),
  @JsonProperty(EntityTypeConstants.OPENVAS)
  OPENVAS(ModuleType.STO, EntityTypeConstants.OPENVAS, IdentifierRef.class, EntityYamlRootNames.OPENVAS),
  @JsonProperty(EntityTypeConstants.OWASP)
  OWASP(ModuleType.STO, EntityTypeConstants.OWASP, IdentifierRef.class, EntityYamlRootNames.OWASP),
  @JsonProperty(EntityTypeConstants.PRISMA_CLOUD)
  PRISMA_CLOUD(ModuleType.STO, EntityTypeConstants.PRISMA_CLOUD, IdentifierRef.class, EntityYamlRootNames.PRISMA_CLOUD),
  @JsonProperty(EntityTypeConstants.PROWLER)
  PROWLER(ModuleType.STO, EntityTypeConstants.PROWLER, IdentifierRef.class, EntityYamlRootNames.PROWLER),
  @JsonProperty(EntityTypeConstants.QUALYS)
  QUALYS(ModuleType.STO, EntityTypeConstants.QUALYS, IdentifierRef.class, EntityYamlRootNames.QUALYS),
  @JsonProperty(EntityTypeConstants.REAPSAW)
  REAPSAW(ModuleType.STO, EntityTypeConstants.REAPSAW, IdentifierRef.class, EntityYamlRootNames.REAPSAW),
  @JsonProperty(EntityTypeConstants.SHIFT_LEFT)
  SHIFT_LEFT(ModuleType.STO, EntityTypeConstants.SHIFT_LEFT, IdentifierRef.class, EntityYamlRootNames.SHIFT_LEFT),
  @JsonProperty(EntityTypeConstants.SNIPER)
  SNIPER(ModuleType.STO, EntityTypeConstants.SNIPER, IdentifierRef.class, EntityYamlRootNames.SNIPER),
  @JsonProperty(EntityTypeConstants.SNYK)
  SNYK(ModuleType.STO, EntityTypeConstants.SNYK, IdentifierRef.class, EntityYamlRootNames.SNYK),
  @JsonProperty(EntityTypeConstants.SONARQUBE)
  SONARQUBE(ModuleType.STO, EntityTypeConstants.SONARQUBE, IdentifierRef.class, EntityYamlRootNames.SONARQUBE),
  @JsonProperty(EntityTypeConstants.SYSDIG)
  SYSDIG(ModuleType.STO, EntityTypeConstants.SYSDIG, IdentifierRef.class, EntityYamlRootNames.SYSDIG),
  @JsonProperty(EntityTypeConstants.TENABLE)
  TENABLE(ModuleType.STO, EntityTypeConstants.TENABLE, IdentifierRef.class, EntityYamlRootNames.TENABLE),
  @JsonProperty(EntityTypeConstants.VERACODE)
  VERACODE(ModuleType.STO, EntityTypeConstants.VERACODE, IdentifierRef.class, EntityYamlRootNames.VERACODE),
  @JsonProperty(EntityTypeConstants.ZAP)
  ZAP(ModuleType.STO, EntityTypeConstants.ZAP, IdentifierRef.class, EntityYamlRootNames.ZAP),

  @JsonProperty(EntityTypeConstants.GIT_CLONE)
  GIT_CLONE(ModuleType.CI, EntityTypeConstants.GIT_CLONE, IdentifierRef.class, EntityYamlRootNames.GIT_CLONE),
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
      ModuleType.CORE, EntityTypeConstants.INFRASTRUCTURE, IdentifierRef.class, EntityYamlRootNames.INFRASTRUCTURE),
  @JsonProperty(EntityTypeConstants.COMMAND)
  COMMAND_STEP(ModuleType.CD, EntityTypeConstants.COMMAND, IdentifierRef.class, EntityYamlRootNames.COMMAND),
  @JsonProperty(EntityTypeConstants.STRATEGY_NODE)
  STRATEGY_NODE(
      ModuleType.PMS, EntityTypeConstants.STRATEGY_NODE, IdentifierRef.class, EntityYamlRootNames.STRATEGY_NODE),
  AZURE_SLOT_DEPLOYMENT_STEP(ModuleType.CD, EntityTypeConstants.AZURE_SLOT_DEPLOYMENT, IdentifierRef.class,
      EntityYamlRootNames.AZURE_SLOT_DEPLOYMENT_STEP),
  @JsonProperty(EntityTypeConstants.AZURE_TRAFFIC_SHIFT)
  AZURE_TRAFFIC_SHIFT_STEP(ModuleType.CD, EntityTypeConstants.AZURE_TRAFFIC_SHIFT, IdentifierRef.class,
      EntityYamlRootNames.AZURE_TRAFFIC_SHIFT_STEP),
  @JsonProperty(EntityTypeConstants.FETCH_INSTANCE_SCRIPT)
  FETCH_INSTANCE_SCRIPT_STEP(ModuleType.CD, EntityTypeConstants.FETCH_INSTANCE_SCRIPT, IdentifierRef.class,
      EntityYamlRootNames.FETCH_INSTANCE_SCRIPT),
  @JsonProperty(EntityTypeConstants.AZURE_SWAP_SLOT)
  AZURE_SWAP_SLOT_STEP(ModuleType.CD, EntityTypeConstants.AZURE_SWAP_SLOT, IdentifierRef.class,
      EntityYamlRootNames.AZURE_SWAP_SLOT_STEP),
  @JsonProperty(EntityTypeConstants.AZURE_WEBAPP_ROLLBACK)
  AZURE_WEBAPP_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.AZURE_WEBAPP_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.AZURE_WEBAPP_ROLLBACK_STEP),
  @JsonProperty(EntityTypeConstants.JENKINS_BUILD)
  JENKINS_BUILD(
      ModuleType.CD, EntityTypeConstants.JENKINS_BUILD, IdentifierRef.class, EntityYamlRootNames.JENKINS_BUILD),
  @JsonProperty(EntityTypeConstants.ECS_ROLLING_DEPLOY)
  ECS_ROLLING_DEPLOY_STEP(ModuleType.CD, EntityTypeConstants.ECS_ROLLING_DEPLOY, IdentifierRef.class,
      EntityYamlRootNames.ECS_ROLLING_DEPLOY),
  @JsonProperty(EntityTypeConstants.ECS_ROLLING_ROLLBACK)
  ECS_ROLLING_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.ECS_ROLLING_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.ECS_ROLLING_ROLLBACK),
  @JsonProperty(EntityTypeConstants.ECS_CANARY_DEPLOY)
  ECS_CANARY_DEPLOY_STEP(
      ModuleType.CD, EntityTypeConstants.ECS_CANARY_DEPLOY, IdentifierRef.class, EntityYamlRootNames.ECS_CANARY_DEPLOY),
  @JsonProperty(EntityTypeConstants.ECS_CANARY_DELETE)
  ECS_CANARY_DELETE_STEP(
      ModuleType.CD, EntityTypeConstants.ECS_CANARY_DELETE, IdentifierRef.class, EntityYamlRootNames.ECS_CANARY_DELETE),
  @JsonProperty(EntityTypeConstants.AZURE_CREATE_ARM_RESOURCE_STEP)
  AZURE_CREATE_ARM_RESOURCE_STEP(ModuleType.CD, EntityTypeConstants.AZURE_CREATE_ARM_RESOURCE_STEP, IdentifierRef.class,
      EntityYamlRootNames.AZURE_CREATE_ARM_RESOURCE_STEP),
  @JsonProperty(EntityTypeConstants.BUILD_AND_PUSH_ACR)
  BUILD_AND_PUSH_ACR(ModuleType.CI, EntityTypeConstants.BUILD_AND_PUSH_ACR, IdentifierRef.class,
      EntityYamlRootNames.BUILD_AND_PUSH_ACR),
  @JsonProperty(EntityTypeConstants.AZURE_CREATE_BP_RESOURCE_STEP)
  AZURE_CREATE_BP_RESOURCE_STEP(ModuleType.CD, EntityTypeConstants.AZURE_CREATE_BP_RESOURCE_STEP, IdentifierRef.class,
      EntityYamlRootNames.AZURE_CREATE_BP_RESOURCE_STEP),
  @JsonProperty(EntityTypeConstants.AZURE_ROLLBACK_ARM_RESOURCE_STEP)
  AZURE_ROLLBACK_ARM_RESOURCE_STEP(ModuleType.CD, EntityTypeConstants.AZURE_ROLLBACK_ARM_RESOURCE_STEP,
      IdentifierRef.class, EntityYamlRootNames.AZURE_ROLLBACK_ARM_RESOURCE_STEP),
  @JsonProperty(EntityTypeConstants.BACKGROUND_STEP)
  BACKGROUND_STEP(
      ModuleType.CI, EntityTypeConstants.BACKGROUND_STEP, IdentifierRef.class, EntityYamlRootNames.BACKGROUND_STEP),
  @JsonProperty(EntityTypeConstants.WAIT_STEP)
  WAIT_STEP(ModuleType.PMS, EntityTypeConstants.WAIT_STEP, IdentifierRef.class, EntityYamlRootNames.WAIT_STEP),
  @JsonProperty(EntityTypeConstants.ARTIFACT_SOURCE_TEMPLATE)
  ARTIFACT_SOURCE_TEMPLATE(ModuleType.TEMPLATESERVICE, EntityTypeConstants.ARTIFACT_SOURCE_TEMPLATE,
      NGTemplateReference.class, EntityYamlRootNames.TEMPLATE),
  @JsonProperty(EntityTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE)
  ECS_BLUE_GREEN_CREATE_SERVICE_STEP(ModuleType.CD, EntityTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE,
      IdentifierRef.class, EntityYamlRootNames.ECS_BLUE_GREEN_CREATE_SERVICE),
  @JsonProperty(EntityTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS)
  ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_STEP(ModuleType.CD, EntityTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS,
      IdentifierRef.class, EntityYamlRootNames.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS),
  @JsonProperty(EntityTypeConstants.ECS_BLUE_GREEN_ROLLBACK)
  ECS_BLUE_GREEN_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.ECS_BLUE_GREEN_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.ECS_BLUE_GREEN_ROLLBACK),
  @JsonProperty(EntityTypeConstants.SHELL_SCRIPT_PROVISION_STEP)
  SHELL_SCRIPT_PROVISION_STEP(ModuleType.CD, EntityTypeConstants.SHELL_SCRIPT_PROVISION_STEP, IdentifierRef.class,
      EntityYamlRootNames.SHELL_SCRIPT_PROVISION_STEP),
  @JsonProperty(EntityTypeConstants.FREEZE)
  FREEZE(ModuleType.CD, EntityTypeConstants.FREEZE, IdentifierRef.class, EntityYamlRootNames.FREEZE),
  @JsonProperty(EntityTypeConstants.GITOPS_UPDATE_RELEASE_REPO)
  GITOPS_UPDATE_RELEASE_REPO(ModuleType.CD, EntityTypeConstants.GITOPS_UPDATE_RELEASE_REPO, IdentifierRef.class,
      EntityYamlRootNames.GITOPS_UPDATE_RELEASE_REPO),
  @JsonProperty(EntityTypeConstants.GITOPS_FETCH_LINKED_APPS)
  GITOPS_FETCH_LINKED_APPS(ModuleType.CD, EntityTypeConstants.GITOPS_FETCH_LINKED_APPS, IdentifierRef.class,
      EntityYamlRootNames.GITOPS_FETCH_LINKED_APPS),
  @JsonProperty(EntityTypeConstants.ECS_RUN_TASK)
  ECS_RUN_TASK_STEP(
      ModuleType.CD, EntityTypeConstants.ECS_RUN_TASK, IdentifierRef.class, EntityYamlRootNames.ECS_RUN_TASK),
  @JsonProperty(EntityTypeConstants.CHAOS_STEP)
  CHAOS_STEP(ModuleType.CHAOS, EntityTypeConstants.CHAOS_STEP, IdentifierRef.class, EntityYamlRootNames.CHAOS_STEP),
  @JsonProperty(EntityTypeConstants.ELASTIGROUP_DEPLOY_STEP)
  ELASTIGROUP_DEPLOY_STEP(ModuleType.CD, EntityTypeConstants.ELASTIGROUP_DEPLOY_STEP, IdentifierRef.class,
      EntityYamlRootNames.ELASTIGROUP_DEPLOY_STEP),
  @JsonProperty(EntityTypeConstants.ELASTIGROUP_ROLLBACK_STEP)
  ELASTIGROUP_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.ELASTIGROUP_ROLLBACK_STEP, IdentifierRef.class,
      EntityYamlRootNames.ELASTIGROUP_ROLLBACK_STEP),
  @JsonProperty(EntityTypeConstants.ACTION_STEP)
  ACTION_STEP(ModuleType.CI, EntityTypeConstants.ACTION_STEP, IdentifierRef.class, EntityYamlRootNames.ACTION_STEP),
  @JsonProperty(EntityTypeConstants.ELASTIGROUP_SETUP)
  ELASTIGROUP_SETUP_STEP(
      ModuleType.CD, EntityTypeConstants.ELASTIGROUP_SETUP, IdentifierRef.class, EntityYamlRootNames.ELASTIGROUP_SETUP),
  @JsonProperty(EntityTypeConstants.BITRISE_STEP)
  BITRISE_STEP(ModuleType.CI, EntityTypeConstants.BITRISE_STEP, IdentifierRef.class, EntityYamlRootNames.BITRISE_STEP),
  @JsonProperty(EntityTypeConstants.TERRAFORM_PLAN)
  TERRAGRUNT_PLAN_STEP(
      ModuleType.CD, EntityTypeConstants.TERRAGRUNT_PLAN, IdentifierRef.class, EntityYamlRootNames.TERRAGRUNT_PLAN),
  @JsonProperty(EntityTypeConstants.TERRAFORM_APPLY)
  TERRAGRUNT_APPLY_STEP(
      ModuleType.CD, EntityTypeConstants.TERRAGRUNT_APPLY, IdentifierRef.class, EntityYamlRootNames.TERRAGRUNT_APPLY),
  @JsonProperty(EntityTypeConstants.TERRAFORM_DESTROY)
  TERRAGRUNT_DESTROY_STEP(ModuleType.CD, EntityTypeConstants.TERRAGRUNT_DESTROY, IdentifierRef.class,
      EntityYamlRootNames.TERRAGRUNT_DESTROY),
  @JsonProperty(EntityTypeConstants.TERRAFORM_ROLLBACK)
  TERRAGRUNT_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.TERRAGRUNT_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.TERRAGRUNT_ROLLBACK),
  @JsonProperty(EntityTypeConstants.IACM_STAGE)
  IACM_STAGE(ModuleType.IACM, EntityTypeConstants.IACM_STAGE, IdentifierRef.class, EntityYamlRootNames.IACM_STAGE),
  @JsonProperty(EntityTypeConstants.IACM_STEPS)
  IACM_STEPS(ModuleType.IACM, EntityTypeConstants.IACM_STEPS, IdentifierRef.class, EntityYamlRootNames.IACM_STEP),
  @JsonProperty(EntityTypeConstants.IACM)
  IACM(ModuleType.IACM, EntityTypeConstants.IACM, IdentifierRef.class, EntityYamlRootNames.IACM),
  @JsonProperty(EntityTypeConstants.CONTAINER_STEP)
  CONTAINER_STEP(
      ModuleType.PMS, EntityTypeConstants.CONTAINER_STEP, IdentifierRef.class, EntityYamlRootNames.CONTAINER_STEP),
  @JsonProperty(EntityTypeConstants.IACM)
  IACM_TERRAFORM_PLAN(ModuleType.IACM, EntityTypeConstants.IACM_TERRAFORM_PLAN, IdentifierRef.class,
      EntityYamlRootNames.IACM_TERRAFORM_PLAN),
  @JsonProperty(EntityTypeConstants.IACM)
  IACM_TEMPLATE(
      ModuleType.IACM, EntityTypeConstants.IACM_TEMPLATE, IdentifierRef.class, EntityYamlRootNames.IACM_TEMPLATE),
  @JsonProperty(EntityTypeConstants.ELASTIGROUP_BG_STAGE_SETUP)
  ELASTIGROUP_BG_STAGE_SETUP_STEP(ModuleType.CD, EntityTypeConstants.ELASTIGROUP_BG_STAGE_SETUP, IdentifierRef.class,
      EntityYamlRootNames.ELASTIGROUP_BG_STAGE_SETUP),
  @JsonProperty(EntityTypeConstants.ELASTIGROUP_SWAP_ROUTE)
  ELASTIGROUP_SWAP_ROUTE_STEP(ModuleType.CD, EntityTypeConstants.ELASTIGROUP_SWAP_ROUTE, IdentifierRef.class,
      EntityYamlRootNames.ELASTIGROUP_SWAP_ROUTE),
  @JsonProperty(EntityTypeConstants.ASG_CANARY_DEPLOY)
  ASG_CANARY_DEPLOY_STEP(
      ModuleType.CD, EntityTypeConstants.ASG_CANARY_DEPLOY, IdentifierRef.class, EntityYamlRootNames.ASG_CANARY_DEPLOY),
  @JsonProperty(EntityTypeConstants.ASG_CANARY_DELETE)
  ASG_CANARY_DELETE_STEP(
      ModuleType.CD, EntityTypeConstants.ASG_CANARY_DELETE, IdentifierRef.class, EntityYamlRootNames.ASG_CANARY_DELETE),
  @JsonProperty(EntityTypeConstants.TAS_SWAP_ROUTES_STEP)
  TAS_SWAP_ROUTES_STEP(ModuleType.CD, EntityTypeConstants.TAS_SWAP_ROUTES_STEP, IdentifierRef.class,
      EntityYamlRootNames.TAS_SWAP_ROUTES_STEP),
  @JsonProperty(EntityTypeConstants.TAS_SWAP_ROLLBACK_STEP)
  TAS_SWAP_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.TAS_SWAP_ROLLBACK_STEP, IdentifierRef.class,
      EntityYamlRootNames.TAS_SWAP_ROLLBACK_STEP),
  @JsonProperty(EntityTypeConstants.TAS_APP_RESIZE_STEP)
  TAS_APP_RESIZE_STEP(ModuleType.CD, EntityTypeConstants.TAS_APP_RESIZE_STEP, IdentifierRef.class,
      EntityYamlRootNames.TAS_APP_RESIZE_STEP),
  @JsonProperty(EntityTypeConstants.TAS_ROLLBACK_STEP)
  TAS_ROLLBACK_STEP(
      ModuleType.CD, EntityTypeConstants.TAS_ROLLBACK_STEP, IdentifierRef.class, EntityYamlRootNames.TAS_ROLLBACK_STEP),
  @JsonProperty(EntityTypeConstants.TAS_CANARY_APP_SETUP_STEP)
  TAS_CANARY_APP_SETUP_STEP(ModuleType.CD, EntityTypeConstants.TAS_CANARY_APP_SETUP_STEP, IdentifierRef.class,
      EntityYamlRootNames.TAS_CANARY_APP_SETUP_STEP),
  @JsonProperty(EntityTypeConstants.TAS_BG_APP_SETUP_STEP)
  TAS_BG_APP_SETUP_STEP(ModuleType.CD, EntityTypeConstants.TAS_BG_APP_SETUP_STEP, IdentifierRef.class,
      EntityYamlRootNames.TAS_BG_APP_SETUP_STEP),
  @JsonProperty(EntityTypeConstants.TAS_BASIC_APP_SETUP_STEP)
  TAS_BASIC_APP_SETUP_STEP(ModuleType.CD, EntityTypeConstants.TAS_BASIC_APP_SETUP_STEP, IdentifierRef.class,
      EntityYamlRootNames.TAS_BASIC_APP_SETUP_STEP),
  @JsonProperty(EntityTypeConstants.TANZU_COMMAND_STEP)
  TANZU_COMMAND_STEP(ModuleType.CD, EntityTypeConstants.TANZU_COMMAND_STEP, IdentifierRef.class,
      EntityYamlRootNames.TANZU_COMMAND_STEP),
  @JsonProperty(EntityTypeConstants.ASG_ROLLING_DEPLOY)
  ASG_ROLLING_DEPLOY_STEP(ModuleType.CD, EntityTypeConstants.ASG_ROLLING_DEPLOY, IdentifierRef.class,
      EntityYamlRootNames.ASG_ROLLING_DEPLOY),
  @JsonProperty(EntityTypeConstants.ASG_ROLLING_ROLLBACK)
  ASG_ROLLING_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.ASG_ROLLING_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.ASG_ROLLING_ROLLBACK),
  @JsonProperty(EntityTypeConstants.CCM_GOVERNANCE_RULE_AWS)
  CCM_GOVERNANCE_RULE_AWS(ModuleType.CE, EntityTypeConstants.CCM_GOVERNANCE_RULE_AWS, IdentifierRef.class,
      EntityYamlRootNames.CCM_GOVERNANCE_RULE),
  @JsonProperty(EntityTypeConstants.TAS_ROLLING_DEPLOY)
  TAS_ROLLING_DEPLOY(ModuleType.CD, EntityTypeConstants.TAS_ROLLING_DEPLOY, IdentifierRef.class,
      EntityYamlRootNames.TAS_ROLLING_DEPLOY),
  @JsonProperty(EntityTypeConstants.TAS_ROLLING_ROLLBACK)
  TAS_ROLLING_ROLLBACK(ModuleType.CD, EntityTypeConstants.TAS_ROLLING_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.TAS_ROLLING_ROLLBACK),
  @JsonProperty(EntityTypeConstants.K8S_DRY_RUN_MANIFEST)
  K8S_DRY_RUN_MANIFEST_STEP(ModuleType.CD, EntityTypeConstants.K8S_DRY_RUN_MANIFEST, IdentifierRef.class,
      EntityYamlRootNames.K8S_DRY_RUN_MANIFEST),
  @JsonProperty(EntityTypeConstants.ASG_BLUE_GREEN_SWAP_SERVICE_STEP)
  ASG_BLUE_GREEN_SWAP_SERVICE_STEP(ModuleType.CD, EntityTypeConstants.ASG_BLUE_GREEN_SWAP_SERVICE_STEP,
      IdentifierRef.class, EntityYamlRootNames.ASG_BLUE_GREEN_SWAP_SERVICE_STEP),
  @JsonProperty(EntityTypeConstants.ASG_BLUE_GREEN_DEPLOY)
  ASG_BLUE_GREEN_DEPLOY_STEP(ModuleType.CD, EntityTypeConstants.ASG_BLUE_GREEN_DEPLOY, IdentifierRef.class,
      EntityYamlRootNames.ASG_BLUE_GREEN_DEPLOY),
  @JsonProperty(EntityTypeConstants.ASG_BLUE_GREEN_ROLLBACK)
  ASG_BLUE_GREEN_ROLLBACK_STEP(ModuleType.CD, EntityTypeConstants.ASG_BLUE_GREEN_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.ASG_BLUE_GREEN_ROLLBACK),
  @JsonProperty(EntityTypeConstants.TERRAFORM_CLOUD_RUN)
  TERRAFORM_CLOUD_RUN(ModuleType.CD, EntityTypeConstants.TERRAFORM_CLOUD_RUN, IdentifierRef.class,
      EntityYamlRootNames.TERRAFORM_CLOUD_RUN),
  @JsonProperty(EntityTypeConstants.TERRAFORM_CLOUD_ROLLBACK)
  TERRAFORM_CLOUD_ROLLBACK(ModuleType.CD, EntityTypeConstants.TERRAFORM_CLOUD_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.TERRAFORM_CLOUD_ROLLBACK),
  @JsonProperty(EntityTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY)
  GOOGLE_CLOUD_FUNCTIONS_DEPLOY(ModuleType.CD, EntityTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY, IdentifierRef.class,
      EntityYamlRootNames.GOOGLE_CLOUD_FUNCTIONS_DEPLOY),
  @JsonProperty(EntityTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC)
  GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC(ModuleType.CD,
      EntityTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC, IdentifierRef.class,
      EntityYamlRootNames.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC),
  @JsonProperty(EntityTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT)
  GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT(ModuleType.CD, EntityTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT,
      IdentifierRef.class, EntityYamlRootNames.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT),
  @JsonProperty(EntityTypeConstants.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK)
  GOOGLE_CLOUD_FUNCTIONS_ROLLBACK(ModuleType.CD, EntityTypeConstants.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK,
      IdentifierRef.class, EntityYamlRootNames.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK),
  @JsonProperty(EntityTypeConstants.AWS_LAMBDA_DEPLOY)
  AWS_LAMBDA_DEPLOY(
      ModuleType.CD, EntityTypeConstants.AWS_LAMBDA_DEPLOY, IdentifierRef.class, EntityYamlRootNames.AWS_LAMBDA_DEPLOY),
  @JsonProperty(EntityTypeConstants.AWS_SAM_DEPLOY)
  AWS_SAM_DEPLOY(
      ModuleType.CD, EntityTypeConstants.AWS_SAM_DEPLOY, IdentifierRef.class, EntityYamlRootNames.AWS_SAM_DEPLOY),
  @JsonProperty(EntityTypeConstants.AWS_SAM_ROLLBACK)
  AWS_SAM_ROLLBACK(
      ModuleType.CD, EntityTypeConstants.AWS_SAM_ROLLBACK, IdentifierRef.class, EntityYamlRootNames.AWS_SAM_ROLLBACK),
  @JsonProperty(EntityTypeConstants.SSCA_ORCHESTRATION)
  SSCA_ORCHESTRATION(ModuleType.CI, EntityTypeConstants.SSCA_ORCHESTRATION, IdentifierRef.class),
  @JsonProperty(EntityTypeConstants.AWS_LAMBDA_ROLLBACK)
  AWS_LAMBDA_ROLLBACK(ModuleType.CD, EntityTypeConstants.AWS_LAMBDA_ROLLBACK, IdentifierRef.class,
      EntityYamlRootNames.AWS_LAMBDA_ROLLBACK),
  GITOPS_SYNC(ModuleType.CD, EntityTypeConstants.GITOPS_SYNC, IdentifierRef.class, EntityYamlRootNames.GITOPS_SYNC),
  @JsonProperty(EntityTypeConstants.BAMBOO_BUILD)
  BAMBOO_BUILD(ModuleType.CD, EntityTypeConstants.BAMBOO_BUILD, IdentifierRef.class, EntityYamlRootNames.BAMBOO_BUILD),
  @JsonProperty(EntityTypeConstants.CD_SSCA_ORCHESTRATION)
  CD_SSCA_ORCHESTRATION(ModuleType.CD, EntityTypeConstants.CD_SSCA_ORCHESTRATION, IdentifierRef.class),
  @JsonProperty(EntityTypeConstants.TAS_ROUTE_MAPPING)
  TAS_ROUTE_MAPPING(
      ModuleType.CD, EntityTypeConstants.TAS_ROUTE_MAPPING, IdentifierRef.class, EntityYamlRootNames.TAS_ROUTE_MAPPING),

  @JsonProperty(EntityTypeConstants.AWS_SECURITY_HUB)
  AWS_SECURITY_HUB(
      ModuleType.STO, EntityTypeConstants.AWS_SECURITY_HUB, IdentifierRef.class, EntityYamlRootNames.AWS_SECURITY_HUB),
  @JsonProperty(EntityTypeConstants.CUSTOM_INGEST)
  CUSTOM_INGEST(
      ModuleType.STO, EntityTypeConstants.CUSTOM_INGEST, IdentifierRef.class, EntityYamlRootNames.CUSTOM_INGEST),
  @JsonProperty(EntityTypeConstants.BACKSTAGE_ENVIRONMENT_VARIABLE)
  BACKSTAGE_ENVIRONMENT_VARIABLE(
      ModuleType.IDP, EntityTypeConstants.BACKSTAGE_ENVIRONMENT_VARIABLE, IdentifierRef.class),
  @JsonProperty(EntityTypeConstants.FOSSA)
  FOSSA(ModuleType.STO, EntityTypeConstants.FOSSA, IdentifierRef.class, EntityYamlRootNames.FOSSA),
  @JsonProperty(EntityTypeConstants.CODEQL)
  CODEQL(ModuleType.STO, EntityTypeConstants.CODEQL, IdentifierRef.class, EntityYamlRootNames.CODEQL),
  @JsonProperty(EntityTypeConstants.GIT_LEAKS)
  GIT_LEAKS(ModuleType.STO, EntityTypeConstants.GIT_LEAKS, IdentifierRef.class, EntityYamlRootNames.GIT_LEAKS);

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
