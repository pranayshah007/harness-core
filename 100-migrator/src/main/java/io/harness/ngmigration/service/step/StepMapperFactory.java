/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.ngmigration.service.step.arm.AzureCreateARMResourceStepMapperImpl;
import io.harness.ngmigration.service.step.arm.AzureRollbackARMResourceStepMapperImpl;
import io.harness.ngmigration.service.step.asg.AsgBlueGreenRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.asg.AsgBlueGreenSwapStepMapperImpl;
import io.harness.ngmigration.service.step.asg.AsgRollingDeployStepMapperImpl;
import io.harness.ngmigration.service.step.asg.AsgRollingRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.azure.webapp.AzureSlotRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.azure.webapp.AzureSlotSetupMapperImpl;
import io.harness.ngmigration.service.step.azure.webapp.AzureSlotShiftTrafficMapperImpl;
import io.harness.ngmigration.service.step.azure.webapp.AzureSlotSwapMapperImpl;
import io.harness.ngmigration.service.step.cloudformation.CloudformationCreateStepMapperImpl;
import io.harness.ngmigration.service.step.cloudformation.CloudformationDeleteStepMapperImpl;
import io.harness.ngmigration.service.step.cloudformation.CloudformationRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.cv.NewRelicDeploymentMarkerStepMapperImpl;
import io.harness.ngmigration.service.step.ecs.EcsBGServiceSetupStepMapperImpl;
import io.harness.ngmigration.service.step.ecs.EcsDaemonServiceSetupStepMapperImpl;
import io.harness.ngmigration.service.step.ecs.EcsListenerUpdateRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.ecs.EcsListenerUpdateStepMapperImpl;
import io.harness.ngmigration.service.step.ecs.EcsRunTaskStepMapperImpl;
import io.harness.ngmigration.service.step.ecs.EcsServiceRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.ecs.EcsServiceSetupStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupDeployStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupListenerRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupSetupStepMapperImpl;
import io.harness.ngmigration.service.step.elastigroup.ElastigroupSwapRouteStepMapperImpl;
import io.harness.ngmigration.service.step.helm.HelmDeployStepMapperImpl;
import io.harness.ngmigration.service.step.helm.HelmRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sApplyStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sBlueGreenDeployStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sCanaryDeployStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sDeleteStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sRollingRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sRollingStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sScaleStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sSwapServiceSelectorsStepMapperImpl;
import io.harness.ngmigration.service.step.k8s.K8sTrafficSplitStepMapperImpl;
import io.harness.ngmigration.service.step.lambda.LambdaRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.lambda.LambdaStepMapperImpl;
import io.harness.ngmigration.service.step.pcf.PcfBGMapRouteStepMapperImpl;
import io.harness.ngmigration.service.step.pcf.PcfDeployStepMapperImpl;
import io.harness.ngmigration.service.step.pcf.PcfPluginStepMapperImpl;
import io.harness.ngmigration.service.step.pcf.PcfRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.pcf.PcfSetupStepMapperImpl;
import io.harness.ngmigration.service.step.pcf.PcfSwapRoutesStepMapperImpl;
import io.harness.ngmigration.service.step.shellscriptprovisioner.ShellScriptProvisionerStepMapperImpl;
import io.harness.ngmigration.service.step.terraform.TerraformApplyStepMapperImpl;
import io.harness.ngmigration.service.step.terraform.TerraformDestroyStepMapperImpl;
import io.harness.ngmigration.service.step.terraform.TerraformProvisionStepMapperImpl;
import io.harness.ngmigration.service.step.terraform.TerraformRollbackStepMapperImpl;
import io.harness.ngmigration.service.step.terragrunt.TerragruntDestroyStepMapperImpl;
import io.harness.ngmigration.service.step.terragrunt.TerragruntProvisionStepMapperImpl;
import io.harness.ngmigration.service.step.terragrunt.TerragruntRollbackStepMapperImpl;
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
  @Inject EcsServiceSetupStepMapperImpl ecsServiceSetupStepMapper;
  @Inject EcsServiceRollbackStepMapperImpl ecsServiceRollbackStepMapper;
  @Inject EcsBGServiceSetupStepMapperImpl ecsBGServiceSetupStepMapper;
  @Inject EcsDaemonServiceSetupStepMapperImpl ecsDaemonServiceSetupStepMapper;
  @Inject EcsListenerUpdateStepMapperImpl ecsListenerUpdateStepMapper;
  @Inject EcsListenerUpdateRollbackStepMapperImpl ecsListenerUpdateRollbackStepMapper;
  @Inject EcsRunTaskStepMapperImpl ecsRunTaskStepMapper;
  @Inject HelmDeployStepMapperImpl helmDeployStepMapper;
  @Inject HelmRollbackStepMapperImpl helmRollbackStepMapper;
  @Inject CustomFetchInstancesStepMapperImpl customFetchInstancesStepMapper;
  @Inject ShellScriptStepMapperImpl shellScriptStepMapper;
  @Inject K8sRollingStepMapperImpl k8sRollingStepMapper;
  @Inject HttpStepMapperImpl httpStepMapper;
  @Inject ApprovalStepMapperImpl approvalStepMapper;
  @Inject BarrierStepMapperImpl barrierStepMapper;
  @Inject K8sApplyStepMapperImpl k8sApplyStepMapper;
  @Inject K8sDeleteStepMapperImpl k8sDeleteStepMapper;
  @Inject K8sTrafficSplitStepMapperImpl k8sTrafficSplitStepMapper;
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
  @Inject TerragruntProvisionStepMapperImpl terragruntProvisionStepMapper;
  @Inject TerragruntDestroyStepMapperImpl terragruntDestroyStepMapper;
  @Inject PcfSetupStepMapperImpl pcfSetupStepMapper;
  @Inject PcfSwapRoutesStepMapperImpl pcfSwapRoutesStepMapper;
  @Inject PcfPluginStepMapperImpl pcfPluginStepMapper;
  @Inject PcfDeployStepMapperImpl pcfDeployStepMapper;
  @Inject PcfBGMapRouteStepMapperImpl pcfBGMapRouteStepMapper;
  @Inject PcfRollbackStepMapperImpl pcfRollbackStepMapper;
  @Inject TerragruntRollbackStepMapperImpl terragruntRollbackStepMapper;
  @Inject ElastigroupSetupStepMapperImpl elastigroupSetupStepMapper;
  @Inject ElastigroupDeployStepMapperImpl elastigroupDeployStepMapper;
  @Inject ElastigroupListenerRollbackStepMapperImpl elastigroupListenerRollbackStepMapper;
  @Inject ElastigroupRollbackStepMapperImpl elastigroupRollbackStepMapper;
  @Inject ServiceNowStepMapperImpl serviceNowStepMapper;
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
  @Inject CloudformationCreateStepMapperImpl cloudformationCreateStepMapper;
  @Inject CloudformationDeleteStepMapperImpl cloudformationDeleteStepMapper;
  @Inject CloudformationRollbackStepMapperImpl cloudformationRollbackStepMapper;
  @Inject AzureSlotRollbackStepMapperImpl azureSlotRollbackStepMapper;
  @Inject AzureSlotSetupMapperImpl azureSlotSetupMapper;
  @Inject AzureSlotShiftTrafficMapperImpl azureSlotShiftTrafficMapper;
  @Inject AzureSlotSwapMapperImpl azureSlotSwapMapper;
  @Inject NewRelicDeploymentMarkerStepMapperImpl newRelicDeploymentMarkerStepMapper;
  @Inject AsgRollingDeployStepMapperImpl asgRollingDeployStepMapper;
  @Inject AsgRollingRollbackStepMapperImpl asgRollingRollbackStepMapper;
  @Inject AsgBlueGreenSwapStepMapperImpl asgBlueGreenSwapStepMapper;
  @Inject AsgBlueGreenRollbackStepMapperImpl asgBlueGreenRollbackStepMapper;
  @Inject ShellScriptProvisionerStepMapperImpl shellScriptProvisionerStepMapper;
  @Inject LambdaStepMapperImpl lambdaStepMapper;
  @Inject LambdaRollbackStepMapperImpl lambdaRollbackStepMapper;
  @Inject UnsupportedStepMapperImpl unsupportedStepMapper;

  public StepMapper getStepMapper(String stepType) {
    switch (stepType) {
      case "HELM_DEPLOY":
        return helmDeployStepMapper;
      case "HELM_ROLLBACK":
        return helmRollbackStepMapper;
      case "ECS_SERVICE_SETUP":
        return ecsServiceSetupStepMapper;
      case "ECS_SERVICE_ROLLBACK":
      case "ECS_SERVICE_SETUP_ROLLBACK":
        return ecsServiceRollbackStepMapper;
      case "ECS_BG_SERVICE_SETUP":
        return ecsBGServiceSetupStepMapper;
      case "ECS_DAEMON_SERVICE_SETUP":
        return ecsDaemonServiceSetupStepMapper;
      case "ECS_LISTENER_UPDATE":
        return ecsListenerUpdateStepMapper;
      case "ECS_LISTENER_UPDATE_ROLLBACK":
        return ecsListenerUpdateRollbackStepMapper;
      case "ECS_RUN_TASK":
        return ecsRunTaskStepMapper;
      case "ECS_ROUTE53_DNS_WEIGHT_UPDATE":
      case "ECS_ROUTE53_DNS_WEIGHT_UPDATE_ROLLBACK":
      case "ECS_BG_SERVICE_SETUP_ROUTE53":
        return unsupportedStepMapper;
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
      case "K8S_TRAFFIC_SPLIT":
        return k8sTrafficSplitStepMapper;
      case "JENKINS":
        return jenkinsStepMapper;
      case "KUBERNETES_SWAP_SERVICE_SELECTORS":
        return k8sSwapServiceSelectorsStepMapper;
      case "K8S_BLUE_GREEN_DEPLOY":
        return k8sBlueGreenDeployStepMapper;
      case "JIRA_CREATE_UPDATE":
        return jiraCreateUpdateStepMapper;
      case "SERVICENOW_CREATE_UPDATE":
        return serviceNowStepMapper;
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
      case "ECS_STEADY_STATE_CHECK":
      case "ECS_SERVICE_DEPLOY":
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
      case "CUSTOM_DEPLOYMENT_FETCH_INSTANCES":
        return customFetchInstancesStepMapper;
      case "TERRAGRUNT_PROVISION":
        return terragruntProvisionStepMapper;
      case "TERRAGRUNT_DESTROY":
        return terragruntDestroyStepMapper;
      case "TERRAGRUNT_ROLLBACK":
        return terragruntRollbackStepMapper;
      case "PCF_SETUP":
        return pcfSetupStepMapper;
      case "PCF_RESIZE":
        return pcfDeployStepMapper;
      case "PCF_BG_MAP_ROUTE":
        return pcfSwapRoutesStepMapper;
      case "PCF_PLUGIN":
        return pcfPluginStepMapper;
      case "PCF_ROLLBACK":
        return pcfRollbackStepMapper;
      case "PCF_MAP_ROUTE":
      case "PCF_UNMAP_ROUTE":
        return unsupportedStepMapper;
      case "CLOUD_FORMATION_CREATE_STACK":
        return cloudformationCreateStepMapper;
      case "CLOUD_FORMATION_DELETE_STACK":
        return cloudformationDeleteStepMapper;
      case "CLOUD_FORMATION_ROLLBACK_STACK":
        return cloudformationRollbackStepMapper;
      case "AZURE_WEBAPP_SLOT_SETUP":
        return azureSlotSetupMapper;
      case "AZURE_WEBAPP_SLOT_SHIFT_TRAFFIC":
        return azureSlotShiftTrafficMapper;
      case "AZURE_WEBAPP_SLOT_SWAP":
        return azureSlotSwapMapper;
      case "AZURE_WEBAPP_SLOT_ROLLBACK":
        return azureSlotRollbackStepMapper;
      case "NEW_RELIC_DEPLOYMENT_MARKER":
        return newRelicDeploymentMarkerStepMapper;
      case "AWS_AMI_SERVICE_SETUP":
        return asgRollingDeployStepMapper;
      case "AWS_AMI_SERVICE_ROLLBACK":
        return asgRollingRollbackStepMapper;
      case "AWS_AMI_SWITCH_ROUTES":
        return asgBlueGreenSwapStepMapper;
      case "AWS_AMI_ROLLBACK_SWITCH_ROUTES":
        return asgBlueGreenRollbackStepMapper;
      case "AWS_AMI_SERVICE_DEPLOY":
        return emptyStepMapper;
      case "ASG_AMI_ALB_SHIFT_SWITCH_ROUTES":
      case "ASG_AMI_SERVICE_ALB_SHIFT_DEPLOY":
      case "ASG_AMI_SERVICE_ALB_SHIFT_SETUP":
      case "ASG_AMI_ROLLBACK_ALB_SHIFT_SWITCH_ROUTES":
        return unsupportedStepMapper;
      case "SHELL_SCRIPT_PROVISION":
        return shellScriptProvisionerStepMapper;
      case "AWS_LAMBDA_STATE":
        return lambdaStepMapper;
      case "AWS_LAMBDA_ROLLBACK":
        return lambdaRollbackStepMapper;
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
