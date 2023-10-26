/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.executions.steps.v1;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ECS, HarnessModuleComponent.CDS_GITOPS,
        HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(CDP)
public interface StepSpecTypeConstantsV1 {
  String DEPLOYMENT_STAGE = "deployment";

  String GITOPS_MERGE_PR = "merge-pr";
  String GITOPS_REVERT_PR = "revert-pr";
  String GITOPS_UPDATE_RELEASE_REPO = "gitops-update-release-repo";
  String GITOPS_SYNC = "gitops-sync";
  String UPDATE_GITOPS_APP = "update-gitops-app";
  String GITOPS_FETCH_LINKED_APPS = "gitops-fetch-linked-apps";

  String CLOUDFORMATION_CREATE_STACK = "create-stack";
  String CLOUDFORMATION_DELETE_STACK = "delete-stack";
  String CLOUDFORMATION_ROLLBACK_STACK = "rollback-stack";

  String K8S_ROLLING_DEPLOY = "k8s-rolling-deploy";
  String K8S_ROLLING_ROLLBACK = "k8s-rolling-rollback";
  String K8S_BLUE_GREEN_DEPLOY = "k8s-blue-green-deploy";
  String K8S_APPLY = "k8s-apply";
  String K8S_SCALE = "k8s-scale";
  String K8S_BG_SWAP_SERVICES = "k8s-bg-swap-services";
  String K8S_CANARY_DELETE = "k8s-canary-delete";
  String K8S_CANARY_DEPLOY = "k8s-canary-deploy";
  String K8S_DELETE = "k8s-delete";
  String K8S_BLUE_GREEN_STAGE_SCALE_DOWN = "k8s-blue-green-stage-scale-down";
  String K8S_DRY_RUN_MANIFEST = "k8s-dry-run";

  String TERRAFORM_APPLY = "terraform-apply";
  String TERRAFORM_PLAN = "terraform-plan";
  String TERRAFORM_DESTROY = "terraform-destroy";
  String TERRAFORM_ROLLBACK = "terraform-rollback";

  String TERRAFORM_CLOUD_RUN = "terraform-cloud-run";
  String TERRAFORM_CLOUD_ROLLBACK = "terraform-cloud-rollback";

  String TERRAGRUNT_PLAN = "terragrunt-plan";
  String TERRAGRUNT_APPLY = "terragrunt-apply";
  String TERRAGRUNT_DESTROY = "terragrunt-destroy";
  String TERRAGRUNT_ROLLBACK = "terragrunt-rollback";

  String AZURE_CREATE_ARM_RESOURCE = "azure-create-arm-resource";
  String AZURE_CREATE_BP_RESOURCE = "azure-create-bp-resource";
  String AZURE_ROLLBACK_ARM_RESOURCE = "azure-arm-rollback";

  String AZURE_SLOT_DEPLOYMENT = "azure-slot-deployment";
  String AZURE_TRAFFIC_SHIFT = "azure-traffic-shift";
  String AZURE_SWAP_SLOT = "azure-swap-slot";
  String AZURE_WEBAPP_ROLLBACK = "azure-webapp-rollback";

  String PLACEHOLDER = "placeholder";

  String HELM_DEPLOY = "helm-deploy";
  String HELM_ROLLBACK = "helm-rollback";

  String SERVERLESS_AWS_LAMBDA_DEPLOY = "serverless-aws-lambda-deploy";
  String SERVERLESS_AWS_LAMBDA_ROLLBACK = "serverless-aws-lambda-rollback";

  String SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2 = "serverless-aws-lambda-prepare-rollback-v2";
  String SERVERLESS_AWS_LAMBDA_ROLLBACK_V2 = "serverless-aws-lambda-rollback-v2";
  String SERVERLESS_AWS_LAMBDA_DEPLOY_V2 = "serverless-aws-lambda-deploy-v2";
  String SERVERLESS_AWS_LAMBDA_PACKAGE_V2 = "serverless-aws-lambda-package-v2";

  String COMMAND = "command";

  String ELASTIGROUP_DEPLOY = "elastigroup-deploy";
  String ELASTIGROUP_ROLLBACK = "elastigroup-rollback";
  String ELASTIGROUP_SETUP = "elastigroup-setup";
  String ELASTIGROUP_BG_STAGE_SETUP = "elastigroup-bg-stage-setup";
  String ELASTIGROUP_SWAP_ROUTE = "elastigroup-swap-route";

  String JENKINS_BUILD = "jenkins-build";
  String JENKINS_BUILD_V2 = "jenkins-build-v2";
  String BAMBOO_BUILD = "bamboo-build";

  String DEPLOYMENT_TYPE_CUSTOM_DEPLOYMENT = "custom-deployment";
  String CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT = "fetch-instance-script";

  String TAS_CANARY_APP_SETUP = "canary-app-setup";
  String TAS_BG_APP_SETUP = "bg-app-setup";
  String TAS_APP_RESIZE = "app-resize";
  String TAS_ROLLBACK = "app-rollback";
  String TAS_SWAP_ROUTES = "swap-routes";
  String TAS_BASIC_APP_SETUP = "basic-app-setup";
  String SWAP_ROLLBACK = "swap-rollback";
  String TANZU_COMMAND = "tanzu-command";
  String TAS_ROLLING_DEPLOY = "tas-rolling-deploy";
  String TAS_ROLLING_ROLLBACK = "tas-rolling-rollback";
  String TAS_ROUTE_MAPPING = "route-mapping";

  String ECS_ROLLING_DEPLOY = "ecs-rolling-deploy";
  String ECS_ROLLING_ROLLBACK = "ecs-rolling-rollback";
  String ECS_CANARY_DEPLOY = "ecs-canary-deploy";
  String ECS_CANARY_DELETE = "ecs-canary-delete";
  String ECS_RUN_TASK = "ecs-run-task";
  String ECS_BLUE_GREEN_CREATE_SERVICE = "ecs-blue-green-create-service";
  String ECS_BLUE_GREEN_SWAP_TARGET_GROUPS = "ecs-blue-green-swap-target-groups";
  String ECS_BLUE_GREEN_ROLLBACK = "ecs-blue-green-rollback";
  String ECS_SERVICE_SETUP = "ecs-service-setup";
  String ECS_UPGRADE_CONTAINER = "ecs-upgrade-container";
  String ECS_BASIC_ROLLBACK = "ecs-basic-rollback";

  String GOOGLE_CLOUD_FUNCTIONS_DEPLOY = "deploy-cloud-function";
  String GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC = "deploy-cloud-function-with-no-traffic";
  String GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT = "cloud-function-traffic-shift";
  String GOOGLE_CLOUD_FUNCTIONS_ROLLBACK = "cloud-function-rollback";
  String GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY = "deploy-cloud-function-gen-one";
  String GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_ROLLBACK = "rollback-cloud-function-gen-one";

  String AWS_LAMBDA_DEPLOY = "aws-lambda-deploy";
  String AWS_LAMBDA = "aws-lambda";
  String AWS_LAMBDA_ROLLBACK = "aws-lambda-rollback";

  String SHELL_SCRIPT_PROVISION = "shell-script-provision";

  String CHAOS_STEP = "chaos";

  String ASG_CANARY_DEPLOY = "asg-canary-deploy";
  String ASG_CANARY_DELETE = "asg-canary-delete";
  String ASG_ROLLING_DEPLOY = "asg-rolling-deploy";
  String ASG_ROLLING_ROLLBACK = "asg-rolling-rollback";
  String ASG_BLUE_GREEN_DEPLOY = "asg-blue-green-deploy";
  String ASG_BLUE_GREEN_ROLLBACK = "asg-blue-green-rollback";
  String ASG_BLUE_GREEN_SWAP_SERVICE = "asg-blue-green-swap-service";

  // AWS SAM
  String AWS_SAM_DEPLOY = "aws-sam-deploy";
  String AWS_SAM_BUILD = "aws-sam-build";
  String AWS_SAM_ROLLBACK = "aws-sam-rollback";

  String DOWNLOAD_MANIFESTS = "download-manifests";
  String DOWNLOAD_SERVERLESS_MANIFESTS = "download-serverless-manifests";

  String AWS_CDK_BOOTSTRAP = "aws-cdk-bootstrap";
  String AWS_CDK_SYNTH = "aws-cdk-synth";
  String AWS_CDK_DIFF = "aws-cdk-diff";
  String AWS_CDK_DEPLOY = "aws-cdk-deploy";
  String AWS_CDK_DESTROY = "aws-cdk-destroy";
  String AWS_CDK_ROLLBACK = "aws-cdk-rollback";
}
