/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.stepsdependency.constants;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class OutcomeExpressionConstants {
  public final String CREATE_PR_OUTCOME = "createPROutcome";
  public final String SERVICE = "service";
  public final String ARTIFACTS = "artifacts";
  public final String MANIFESTS = "manifests";
  public final String INFRASTRUCTURE_OUTCOME = "stage.spec.infrastructure.output";
  public final String INFRASTRUCTURE_GROUP = "infrastructureGroup";
  public final String K8S_ROLL_OUT = "rollingOutcome";
  public final String K8S_BLUE_GREEN_OUTCOME = "k8sBlueGreenOutcome";
  public final String K8S_APPLY_OUTCOME = "k8sApplyOutcome";
  public final String K8S_CANARY_OUTCOME = "k8sCanaryOutcome";
  public final String K8S_CANARY_DELETE_OUTCOME = "k8sCanaryDeleteOutcome";
  public final String K8S_BG_SWAP_SERVICES_OUTCOME = "k8sBGSwapServicesOutcome";
  public final String OUTPUT = "output";
  public final String TERRAFORM_CONFIG = "terraformConfig";
  public final String DEPLOYMENT_INFO_OUTCOME = "deploymentInfoOutcome";
  public final String HELM_DEPLOY_OUTCOME = "helmDeployOutcome";
  public final String HELM_ROLLBACK_OUTCOME = "helmRollbackOutcome";
  public final String SERVERLESS_AWS_LAMBDA_ROLLBACK_DATA_OUTCOME = "serverlessAwsLambdaRollbackDataOutcome";
  public final String SERVERLESS_GIT_FETCH_OUTCOME = "serverlessGitFetchOutcome";
  public final String SERVERLESS_AWS_LAMBDA_ROLLBACK_OUTCOME = "serverlessAwsLambdaRollbackOutcome";
  public final String CONFIG_FILES = "configFiles";
  public final String STARTUP_SCRIPT = "startupScript";
  public final String APPLICATION_SETTINGS = "applicationSettings";
  public final String CONNECTION_STRINGS = "connectionStrings";
}
