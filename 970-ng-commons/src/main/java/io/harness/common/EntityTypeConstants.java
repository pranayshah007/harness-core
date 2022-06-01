/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.common;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public interface EntityTypeConstants {
  String PROJECTS = "Projects";
  String PIPELINES = "Pipelines";
  String PIPELINE_STEPS = "PipelineSteps";
  String CONNECTORS = "Connectors";
  String SECRETS = "Secrets";
  String FILES = "Files";
  String SERVICE = "Service";
  String ENVIRONMENT = "Environment";
  String INPUT_SETS = "InputSets";
  String CV_CONFIG = "CvConfig";
  String DELEGATES = "Delegates";
  String DELEGATE_CONFIGURATIONS = "DelegateConfigurations";
  String CV_VERIFICATION_JOB = "CvVerificationJob";
  String INTEGRATION_STAGE = "IntegrationStage";
  String INTEGRATION_STEPS = "IntegrationSteps";
  String CV_KUBERNETES_ACTIVITY_SOURCE = "CvKubernetesActivitySource";
  String DEPLOYMENT_STEPS = "DeploymentSteps";
  String DEPLOYMENT_STAGE = "DeploymentStage";
  String APPROVAL_STAGE = "ApprovalStage";
  String CUSTOM_STAGE = "CustomStage";
  String FEATURE_FLAG_STAGE = "FeatureFlagStage";
  String TRIGGERS = "Triggers";
  String MONITORED_SERVICE = "MonitoredService";
  String TEMPLATE = "Template";
  String GIT_REPOSITORIES = "GitRepositories";
  String FEATURE_FLAGS = "FeatureFlags";
  String HTTP = "Http";
  String JIRA_CREATE = "JiraCreate";
  String JIRA_UPDATE = "JiraUpdate";
  String SHELL_SCRIPT = "ShellScript";
  String K8S_CANARY_DEPLOY = "K8sCanaryDeploy";
  String K8S_APPLY = "K8sApply";
  String K8S_BLUE_GREEN_DEPLOY = "K8sBlueGreenDeploy";
  String K8S_ROLLING_DEPLOY = "K8sRollingDeploy";
  String K8S_ROLLING_ROLLBACK = "K8sRollingRollback";
  String K8S_SCALE = "K8sScale";
  String K8S_DELETE = "K8sDelete";
  String K8S_BG_SWAP_SERVICES = "K8sBGSwapServices";
  String K8S_CANARY_DELETE = "K8sCanaryDelete";
  String RUN_STEP = "Run";
  String RUN_TEST = "RunTests";
  String PLUGIN = "Plugin";
  String RESTORE_CACHE_GCS = "RestoreCacheGCS";
  String RESTORE_CACHE_S3 = "RestoreCacheS3";
  String SAVE_CACHE_GCS = "SaveCacheGCS";
  String SAVE_CACHE_S3 = "SaveCacheS3";
  String SECURITY = "Security";
  String SECURITY_STAGE = "SecurityStage";
  String SECURITY_STEPS = "SecuritySteps";
  String ARTIFACTORY_UPLOAD = "ArtifactoryUpload";
  String GCS_UPLOAD = "GCSUpload";
  String S3_UPLOAD = "S3Upload";

  String BUILD_AND_PUSH_GCR = "BuildAndPushGCR";
  String BUILD_AND_PUSH_ECR = "BuildAndPushECR";
  String BUILD_AND_PUSH_DOCKER_REGISTRY = "BuildAndPushDockerRegistry";
  String TERRAFORM_APPLY = "TerraformApply";
  String TERRAFORM_PLAN = "TerraformPlan";
  String TERRAFORM_DESTROY = "TerraformDestroy";
  String TERRAFORM_ROLLBACK = "TerraformRollback";
  String HELM_DEPLOY = "HelmDeploy";
  String HELM_ROLLBACK = "HelmRollback";
  String SERVICENOW_APPROVAL = "ServiceNowApproval";
  String JIRA_APPROVAL = "JiraApproval";
  String HARNESS_APPROVAL = "HarnessApproval";
  String BARRIER = "Barrier";
  String Verify = "Verify";
  String FlagConfiguration = "FlagConfiguration";
  String OPAPOLICIES = "GovernancePolicies";
  String POLICY_STEP = "Policy";
  String SERVICENOW_CREATE = "ServiceNowCreate";
  String SERVICENOW_UPDATE = "ServiceNowUpdate";
  String ENVIRONMENT_GROUP = "EnvironmentGroup";
  String NG_FILE = "NgFile";
  String CLOUDFORMATION_CREATE_STACK_STEP = "CreateStack";
  String CLOUDFORMATION_DELETE_STACK_STEP = "DeleteStack";
  String SERVERLESS_AWS_LAMBDA_DEPLOY = "ServerlessAwsLambdaDeploy";
  String SERVERLESS_AWS_LAMBDA_ROLLBACK = "ServerlessAwsLambdaRollback";
  String CLOUDFORMATION_ROLLBACK_STACK_STEP = "RollbackStack";
  String INFRASTRUCTURE = "Infrastructure";
}
