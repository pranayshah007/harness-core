/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.executions.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class CDCreatorUtils {
  public Set<String> getSupportedSteps() {
    return Collections.emptySet();
  }
  public Set<String> getSupportedStepsV2() {
    return Sets.newHashSet(StepSpecTypeConstants.GITOPS_MERGE_PR, StepSpecTypeConstants.K8S_CANARY_DEPLOY,
        StepSpecTypeConstants.K8S_APPLY, StepSpecTypeConstants.K8S_BLUE_GREEN_DEPLOY,
        StepSpecTypeConstants.K8S_ROLLING_DEPLOY, StepSpecTypeConstants.K8S_ROLLING_ROLLBACK,
        StepSpecTypeConstants.K8S_SCALE, StepSpecTypeConstants.K8S_DELETE, StepSpecTypeConstants.K8S_BG_SWAP_SERVICES,
        StepSpecTypeConstants.K8S_CANARY_DELETE, StepSpecTypeConstants.TERRAFORM_APPLY,
        StepSpecTypeConstants.TERRAFORM_PLAN, StepSpecTypeConstants.TERRAFORM_DESTROY,
        StepSpecTypeConstants.TERRAFORM_ROLLBACK, StepSpecTypeConstants.HELM_DEPLOY,
        StepSpecTypeConstants.HELM_ROLLBACK, StepSpecTypeConstants.CLOUDFORMATION_CREATE_STACK,
        StepSpecTypeConstants.CLOUDFORMATION_DELETE_STACK, StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY,
        StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK, StepSpecTypeConstants.CLOUDFORMATION_ROLLBACK_STACK,
        StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE, StepSpecTypeConstants.AZURE_CREATE_BP_RESOURCE,
        StepSpecTypeConstants.AZURE_ROLLBACK_ARM_RESOURCE, StepSpecTypeConstants.AZURE_SLOT_DEPLOYMENT,
        StepSpecTypeConstants.AZURE_TRAFFIC_SHIFT, StepSpecTypeConstants.AZURE_SWAP_SLOT,
        StepSpecTypeConstants.AZURE_WEBAPP_ROLLBACK, StepSpecTypeConstants.JENKINS_BUILD,
        StepSpecTypeConstants.ECS_ROLLING_DEPLOY, StepSpecTypeConstants.ECS_ROLLING_ROLLBACK,
        StepSpecTypeConstants.ECS_CANARY_DEPLOY, StepSpecTypeConstants.ECS_CANARY_DELETE,
        StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE, StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS,
        StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK, StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT,
        StepSpecTypeConstants.SHELL_SCRIPT_PROVISION, StepSpecTypeConstants.GITOPS_UPDATE_RELEASE_REPO,
        StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS, StepSpecTypeConstants.ECS_RUN_TASK,
        StepSpecTypeConstants.CHAOS_STEP, StepSpecTypeConstants.ELASTIGROUP_DEPLOY,
        StepSpecTypeConstants.ELASTIGROUP_ROLLBACK, StepSpecTypeConstants.ELASTIGROUP_SETUP,
        StepSpecTypeConstants.TERRAGRUNT_PLAN, StepSpecTypeConstants.TERRAGRUNT_APPLY,
        StepSpecTypeConstants.TERRAGRUNT_DESTROY, StepSpecTypeConstants.TERRAGRUNT_ROLLBACK,
        StepSpecTypeConstants.ASG_CANARY_DEPLOY, StepSpecTypeConstants.ASG_CANARY_DELETE,
        StepSpecTypeConstants.TAS_CANARY_APP_SETUP, StepSpecTypeConstants.TAS_BG_APP_SETUP,
        StepSpecTypeConstants.TAS_BASIC_APP_SETUP, StepSpecTypeConstants.TANZU_COMMAND,
        StepSpecTypeConstants.TAS_APP_RESIZE, StepSpecTypeConstants.TAS_SWAP_ROUTES, StepSpecTypeConstants.TAS_ROLLBACK,
        StepSpecTypeConstants.SWAP_ROLLBACK, StepSpecTypeConstants.ELASTIGROUP_BG_STAGE_SETUP,
        StepSpecTypeConstants.ELASTIGROUP_SWAP_ROUTE, StepSpecTypeConstants.ASG_ROLLING_DEPLOY,
        StepSpecTypeConstants.ASG_ROLLING_ROLLBACK, StepSpecTypeConstants.TAS_ROLLING_DEPLOY,
        StepSpecTypeConstants.TAS_ROLLING_ROLLBACK, StepSpecTypeConstants.K8S_DRY_RUN_MANIFEST,
        StepSpecTypeConstants.ASG_BLUE_GREEN_SWAP_SERVICE, StepSpecTypeConstants.ASG_BLUE_GREEN_DEPLOY,
        StepSpecTypeConstants.ASG_BLUE_GREEN_ROLLBACK, StepSpecTypeConstants.TERRAFORM_CLOUD_RUN,
        StepSpecTypeConstants.TERRAFORM_CLOUD_ROLLBACK, StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY,
        StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC,
        StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT,
        StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK, StepSpecTypeConstants.AWS_LAMBDA_DEPLOY,
        StepSpecTypeConstants.AWS_SAM_DEPLOY, StepSpecTypeConstants.DOWNLOAD_MANIFESTS,
        StepSpecTypeConstants.AWS_SAM_BUILD, StepSpecTypeConstants.AWS_SAM_ROLLBACK, StepSpecTypeConstants.BAMBOO_BUILD,
        StepSpecTypeConstants.AWS_LAMBDA_ROLLBACK, StepSpecTypeConstants.AWS_LAMBDA_ROLLBACK,
        StepSpecTypeConstants.GITOPS_SYNC, StepSpecTypeConstants.TAS_ROUTE_MAPPING,
        StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_DEPLOY,
        StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_GEN_ONE_ROLLBACK,
        StepSpecTypeConstants.K8S_BLUE_GREEN_STAGE_SCALE_DOWN,
        StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2,
        StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_V2, StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY_V2,
        StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_PACKAGE_V2, StepSpecTypeConstants.GITOPS_REVERT_PR,
        StepSpecTypeConstants.AWS_CDK_BOOTSTRAP);
  }
}
