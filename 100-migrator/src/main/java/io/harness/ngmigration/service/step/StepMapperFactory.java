/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.ngmigration.service.step.arm.AzureCreateARMResourceStepMapperImpl;
import io.harness.ngmigration.service.step.arm.AzureRollbackARMResourceStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupDeployStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupListenerRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupSetupStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupSwapRouteStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sApplyStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sBlueGreenDeployStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sCanaryDeployStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sDeleteStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sRollingRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sRollingStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sScaleStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sSwapServiceSelectorsStepMapperImpl;
import io.harness.ngmigration.service.step.terraform.TerraformApplyStepMapperImpl;
import io.harness.ngmigration.service.step.terraform.TerraformDestroyStepMapperImpl;
import io.harness.ngmigration.service.step.terraform.TerraformProvisionStepMapperImpl;
import io.harness.ngmigration.service.step.terraform.TerraformRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.verification.ApmVerificationStepMapperImpl;
import io.harness.ngmigration.service.step.verification.AppDynamicsStepMapperImpl;
import io.harness.ngmigration.service.step.verification.BugsnagStepMapperImpl;
import io.harness.ngmigration.service.step.verification.CloudWatchStepMapperImpl;
import io.harness.ngmigration.service.step.verification.DataDogLogStepMapperImpl;
import io.harness.ngmigration.service.step.verification.DataDogStepMapperImpl;
import io.harness.ngmigration.service.step.verification.DynatraceStepMapperImpl;
import io.harness.ngmigration.service.step.verification.ElasticSearchStepMapperImpl;
import io.harness.ngmigration.service.step.verification.InstanaStepMapperImpl;
import io.harness.ngmigration.service.step.verification.LogVerificationStepMapperImpl;
import io.harness.ngmigration.service.step.verification.LogzStepMapperImpl;
import io.harness.ngmigration.service.step.verification.NewrelicStepMapperImpl;
import io.harness.ngmigration.service.step.verification.PrometheusStepMapperImpl;
import io.harness.ngmigration.service.step.verification.SplunkStepMapperImpl;
import io.harness.ngmigration.service.step.verification.SplunkV2StepMapperImpl;
import io.harness.ngmigration.service.step.verification.StackDriverLogStepMapperImpl;
import io.harness.ngmigration.service.step.verification.StackDriverStepMapperImpl;
import io.harness.ngmigration.service.step.verification.SumoStepMapperImpl;

import software.wings.beans.GraphNode;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StepMapperFactory {
  @Inject ShellScriptStepMapperImpl shellScriptStepMapper;
  @Inject K8sRollingStepMapperImpl k8sRollingStepMapper;
  @Inject HttpStepMapperImpl httpStepMapper;
  @Inject ApprovalStepMapperImpl approvalStepMapper;
  @Inject BarrierStepMapperImpl barrierStepMapper;
  @Inject K8sApplyStepMapperImpl k8sApplyStepMapper;
  @Inject K8sDeleteStepMapperImpl k8sDeleteStepMapper;
  @Inject EmailStepMapperImpl emailStepMapper;
  @Inject K8sRollingRollbackStepMapperImpl k8sRollingRollbackStepMapper;
  @Inject K8sCanaryDeployStepMapperImpl k8sCanaryDeployStepMapper;
  @Inject EmptyStepMapperImpl emptyStepMapper;
  @Inject K8sScaleStepMapperImpl k8sScaleStepMapper;
  @Inject JenkinsStepMapperImpl jenkinsStepMapper;
  @Inject K8sSwapServiceSelectorsStepMapperImpl k8sSwapServiceSelectorsStepMapper;
  @Inject K8sBlueGreenDeployStepMapperImpl k8sBlueGreenDeployStepMapper;
  @Inject JiraCreateUpdateStepMapperImpl jiraCreateUpdateStepMapper;
  @Inject CommandStepMapperImpl commandStepMapper;
  @Inject TerraformApplyStepMapperImpl terraformApplyStepMapper;
  @Inject TerraformProvisionStepMapperImpl terraformProvisionStepMapper;
  @Inject TerraformDestroyStepMapperImpl terraformDestroyStepMapper;
  @Inject TerraformRollbackStepMapperImpl terraformRollbackStepMapper;
  @Inject ElastigroupSetupStepMapperImpl elastigroupSetupStepMapper;
  @Inject ElastigroupDeployStepMapperImpl elastigroupDeployStepMapper;
  @Inject ElastigroupListenerRollbackStepMapperImpl elastigroupListenerRollbackStepMapper;
  @Inject ElastigroupRollbackStepMapperImpl elastigroupRollbackStepMapper;

  @Inject ApmVerificationStepMapperImpl apmVerificationStepMapper;
  @Inject AppDynamicsStepMapperImpl appDynamicsStepMapper;
  @Inject DataDogStepMapperImpl dataDogStepMapper;
  @Inject DynatraceStepMapperImpl dynatraceStepMapper;
  @Inject ElasticSearchStepMapperImpl elasticSearchStepMapper;
  @Inject LogVerificationStepMapperImpl logVerificationStepMapper;
  @Inject NewrelicStepMapperImpl newrelicStepMapper;
  @Inject SplunkStepMapperImpl splunkStepMapper;
  @Inject SplunkV2StepMapperImpl splunkV2StepMapper;
  @Inject SumoStepMapperImpl sumoStepMapper;
  @Inject PrometheusStepMapperImpl prometheusStepMapper;
  @Inject DataDogLogStepMapperImpl dataDogLogStepMapper;
  @Inject LogzStepMapperImpl logzStepMapper;
  @Inject BugsnagStepMapperImpl bugsnagStepMapper;
  @Inject StackDriverStepMapperImpl stackDriverStepMapper;
  @Inject StackDriverLogStepMapperImpl stackDriverLogStepMapper;
  @Inject CloudWatchStepMapperImpl cloudWatchStepMapper;
  @Inject InstanaStepMapperImpl instanaStepMapper;
  @Inject ResourceConstraintStepMapperImpl resourceConstraintStepMapper;
  @Inject ElastigroupSwapRouteStepMapperImpl elastigroupSwapRouteStepMapper;
  @Inject AzureCreateARMResourceStepMapperImpl azureCreateARMResourceStepMapper;
  @Inject AzureRollbackARMResourceStepMapperImpl azureRollbackARMResourceStepMapper;
  @Inject UnsupportedStepMapperImpl unsupportedStepMapper;

  public StepMapper getStepMapper(String stepType) {
    switch (stepType) {
      case "SHELL_SCRIPT":
        return shellScriptStepMapper;
      case "K8S_DEPLOYMENT_ROLLING":
        return k8sRollingStepMapper;
      case "HTTP":
        return httpStepMapper;
      case "APPROVAL":
        return approvalStepMapper;
      case "BARRIER":
        return barrierStepMapper;
      case "K8S_DELETE":
        return k8sDeleteStepMapper;
      case "K8S_APPLY":
        return k8sApplyStepMapper;
      case "K8S_SCALE":
        return k8sScaleStepMapper;
      case "EMAIL":
        return emailStepMapper;
      case "K8S_DEPLOYMENT_ROLLING_ROLLBACK":
        return k8sRollingRollbackStepMapper;
      case "K8S_CANARY_DEPLOY":
        return k8sCanaryDeployStepMapper;
      case "JENKINS":
        return jenkinsStepMapper;
      case "KUBERNETES_SWAP_SERVICE_SELECTORS":
        return k8sSwapServiceSelectorsStepMapper;
      case "K8S_BLUE_GREEN_DEPLOY":
        return k8sBlueGreenDeployStepMapper;
      case "JIRA_CREATE_UPDATE":
        return jiraCreateUpdateStepMapper;
      case "COMMAND":
        return commandStepMapper;
      case "TERRAFORM_PROVISION":
        return terraformProvisionStepMapper;
      case "TERRAFORM_APPLY":
        return terraformApplyStepMapper;
      case "TERRAFORM_DESTROY":
        return terraformDestroyStepMapper;
      case "TERRAFORM_ROLLBACK":
        return terraformRollbackStepMapper;
      case "APP_DYNAMICS":
        return appDynamicsStepMapper;
      case "NEW_RELIC":
        return newrelicStepMapper;
      case "DYNA_TRACE":
        return dynatraceStepMapper;
      case "SUMO":
        return sumoStepMapper;
      case "DATA_DOG":
        return dataDogStepMapper;
      case "APM_VERIFICATION":
        return apmVerificationStepMapper;
      case "LOG_VERIFICATION":
        return logVerificationStepMapper;
      case "SPLUNKV2":
        return splunkV2StepMapper;
      case "ELK":
        return elasticSearchStepMapper;
      case "PROMETHEUS":
        return prometheusStepMapper;
      case "DATA_DOG_LOG":
        return dataDogLogStepMapper;
      case "LOGZ":
        return logzStepMapper;
      case "BUG_SNAG":
        return bugsnagStepMapper;
      case "CLOUD_WATCH":
        return cloudWatchStepMapper;
      case "STACK_DRIVER":
        return stackDriverStepMapper;
      case "STACK_DRIVER_LOG":
        return stackDriverLogStepMapper;
      case "INSTANA":
        return instanaStepMapper;
      case "RESOURCE_CONSTRAINT":
        return resourceConstraintStepMapper;
      case "ROLLING_NODE_SELECT":
      case "AWS_NODE_SELECT":
      case "AZURE_NODE_SELECT":
      case "DC_NODE_SELECT":
      case "ARTIFACT_COLLECTION":
      case "ARTIFACT_CHECK":
        return emptyStepMapper;
      case "SPOTINST_SETUP":
        return elastigroupSetupStepMapper;
      case "SPOTINST_DEPLOY":
        return elastigroupDeployStepMapper;
      case "SPOTINST_LISTENER_UPDATE":
        return elastigroupSwapRouteStepMapper;
      case "SPOTINST_LISTENER_UPDATE_ROLLBACK":
        return elastigroupListenerRollbackStepMapper;
      case "SPOTINST_ROLLBACK":
        return elastigroupRollbackStepMapper;
      case "ARM_CREATE_RESOURCE":
        return azureCreateARMResourceStepMapper;
      case "ARM_ROLLBACK":
        return azureRollbackARMResourceStepMapper;
      default:
        return unsupportedStepMapper;
    }
  }

  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    if (!stepYaml1.getType().equals(stepYaml2.getType())) {
      return false;
    }
    try {
      return getStepMapper(stepYaml1.getType()).areSimilar(stepYaml1, stepYaml2);
    } catch (Exception e) {
      log.error("There was an error with finding similar steps", e);
      return false;
    }
  }
}
