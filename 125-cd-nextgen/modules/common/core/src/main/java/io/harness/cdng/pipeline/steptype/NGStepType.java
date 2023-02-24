/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steptype;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.executions.steps.StepSpecTypeConstants.CLOUDFORMATION_CREATE_STACK;
import static io.harness.executions.steps.StepSpecTypeConstants.CLOUDFORMATION_DELETE_STACK;
import static io.harness.executions.steps.StepSpecTypeConstants.CLOUDFORMATION_ROLLBACK_STACK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.executions.steps.StepSpecTypeConstants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;

/*
   Todo: Change StepSpecTypeConstants.PLACEHOLDER to their respective type once the StepInfo for those is implemented.
 */
@OwnedBy(CDP)
public enum NGStepType {
  // gitops steps
  @JsonProperty(StepSpecTypeConstants.GITOPS_CREATE_PR)
  GITOPS_CREATE_PR("Create PR", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.GITOPS_CREATE_PR),

  @JsonProperty(StepSpecTypeConstants.GITOPS_MERGE_PR)
  GITOPS_MERGE_PR(
      "Merge PR", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes", StepSpecTypeConstants.GITOPS_MERGE_PR),

  // k8s steps
  @JsonProperty("APPLY")
  APPLY("Apply", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes", StepSpecTypeConstants.PLACEHOLDER),
  @JsonProperty("SCALE")
  SCALE("Scale", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes", StepSpecTypeConstants.PLACEHOLDER),
  @JsonProperty("STAGE_DEPLOYMENT")
  STAGE_DEPLOYMENT("Stage Deployment", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.PLACEHOLDER),
  @JsonProperty(StepSpecTypeConstants.K8S_ROLLING_DEPLOY)
  K8S_ROLLING("K8s Rolling", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.K8S_ROLLING_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.K8S_ROLLING_ROLLBACK)
  K8S_ROLLING_ROLLBACK("K8s Rolling Rollback", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.K8S_ROLLING_ROLLBACK),

  // helm steps
  @JsonProperty(StepSpecTypeConstants.HELM_DEPLOY)
  HELM_DEPLOY("Helm Deploy", Arrays.asList(ServiceDefinitionType.NATIVE_HELM), "Native Helm",
      StepSpecTypeConstants.HELM_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.HELM_ROLLBACK)
  HELM_ROLLBACK("Helm Rollback", Arrays.asList(ServiceDefinitionType.NATIVE_HELM), "Native Helm",
      StepSpecTypeConstants.HELM_ROLLBACK),

  @JsonProperty(StepSpecTypeConstants.K8S_BG_SWAP_SERVICES)
  SWAP_SELECTORS("Swap Selectors", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.K8S_BG_SWAP_SERVICES),
  @JsonProperty(StepSpecTypeConstants.K8S_DELETE)
  DELETE("Delete", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes", StepSpecTypeConstants.K8S_DELETE),
  @JsonProperty(StepSpecTypeConstants.K8S_CANARY_DELETE)
  K8S_CANARY_DELETE("Canary Delete", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.K8S_CANARY_DELETE),
  @JsonProperty(StepSpecTypeConstants.K8S_CANARY_DEPLOY)
  CANARY_DEPLOYMENT("Deployment", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.K8S_CANARY_DEPLOY),

  // Infrastructure Provisioners
  @JsonProperty(StepSpecTypeConstants.TERRAFORM_APPLY)
  TERRAFORM_APPLY("Terraform Apply", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terraform", StepSpecTypeConstants.TERRAFORM_APPLY),
  @JsonProperty(StepSpecTypeConstants.TERRAFORM_PLAN)
  TERRAFORM_PLAN("Terraform Plan", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terraform", StepSpecTypeConstants.TERRAFORM_PLAN),
  @JsonProperty(StepSpecTypeConstants.TERRAFORM_DESTROY)
  TERRAFORM_DESTROY("Terraform Destroy", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terraform", StepSpecTypeConstants.TERRAFORM_DESTROY),
  @JsonProperty(StepSpecTypeConstants.TERRAFORM_ROLLBACK)
  TERRAFORM_ROLLBACK("Terraform Rollback", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terraform", StepSpecTypeConstants.TERRAFORM_ROLLBACK),
  @JsonProperty(CLOUDFORMATION_CREATE_STACK)
  CF_CREATE_STACK("Create Stack", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Cloudformation", CLOUDFORMATION_CREATE_STACK),
  @JsonProperty(CLOUDFORMATION_DELETE_STACK)
  CF_DELETE_STACK("Delete Stack", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Cloudformation", CLOUDFORMATION_DELETE_STACK),
  @JsonProperty(CLOUDFORMATION_ROLLBACK_STACK)
  CF_ROLLBACK_STACK("Rollback Stack", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Cloudformation", CLOUDFORMATION_ROLLBACK_STACK),
  @JsonProperty("SHELL_SCRIPT_PROVISIONER")
  SHELL_SCRIPT_PROVISIONER("Shell Script Provisioner", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Shell Script Provisioner", StepSpecTypeConstants.PLACEHOLDER),
  @JsonProperty(StepSpecTypeConstants.TERRAFORM_CLOUD_RUN)
  TERRAFORM_CLOUD_RUN("Terraform Cloud Run", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terraform Cloud", StepSpecTypeConstants.TERRAFORM_CLOUD_RUN),
  @JsonProperty(StepSpecTypeConstants.TERRAFORM_CLOUD_ROLLBACK)
  TERRAFORM_CLOUD_ROLLBACK("Terraform Cloud Rollback", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terraform Cloud", StepSpecTypeConstants.TERRAFORM_CLOUD_ROLLBACK),
  // Issue Tracking
  @JsonProperty("JIRA")
  JIRA("Jira", Arrays.asList(ServiceDefinitionType.values()), "Issue Tracking", StepSpecTypeConstants.PLACEHOLDER),
  @JsonProperty("SERVICENOW")
  SERVICENOW(
      "ServiceNow", Arrays.asList(ServiceDefinitionType.values()), "Issue Tracking", StepSpecTypeConstants.PLACEHOLDER),
  // Notifications
  @JsonProperty("EMAIL")
  EMAIL("Email", Arrays.asList(ServiceDefinitionType.values()), "Notification", StepSpecTypeConstants.PLACEHOLDER),
  // Flow Control
  @JsonProperty("BARRIERS")
  BARRIERS(
      "Barriers", Arrays.asList(ServiceDefinitionType.values()), "Flow Control", StepSpecTypeConstants.PLACEHOLDER),
  // Utilities
  @JsonProperty("NEW_RELIC_DEPLOYMENT_MAKER")
  NEW_RELIC_DEPLOYMENT_MAKER("New Relic Deployment Maker", Arrays.asList(ServiceDefinitionType.values()),
      "Utilites/Non-Scripted/", StepSpecTypeConstants.PLACEHOLDER),
  @JsonProperty("TEMPLATIZED_SECRET_MANAGER")
  TEMPLATIZED_SECRET_MANAGER("Templatized Secret Manager", Arrays.asList(ServiceDefinitionType.values()),
      "Utilites/Non-Scripted/", StepSpecTypeConstants.PLACEHOLDER),

  // serverless steps
  @JsonProperty(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY)
  SERVERLESS_AWS_LAMBDA_DEPLOY("Serverless Aws Lambda Deploy",
      Arrays.asList(ServiceDefinitionType.SERVERLESS_AWS_LAMBDA), "Serverless Aws Lambda",
      StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK)
  SERVERLESS_AWS_LAMBDA_ROLLBACK("Serverless Aws Lambda Rollback",
      Arrays.asList(ServiceDefinitionType.SERVERLESS_AWS_LAMBDA), "Serverless Aws Lambda",
      StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK),
  // ecs steps
  @JsonProperty(StepSpecTypeConstants.ECS_ROLLING_DEPLOY)
  ECS_ROLLING_DEPLOY(
      "ECS Rolling Deploy", Arrays.asList(ServiceDefinitionType.ECS), "ECS", StepSpecTypeConstants.ECS_ROLLING_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.ECS_ROLLING_ROLLBACK)
  ECS_ROLLING_ROLLBACK("ECS Rolling Rollback", Arrays.asList(ServiceDefinitionType.ECS), "ECS",
      StepSpecTypeConstants.ECS_ROLLING_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.ECS_CANARY_DEPLOY)
  ECS_CANARY_DEPLOY(
      "ECS Canary Deploy", Arrays.asList(ServiceDefinitionType.ECS), "ECS", StepSpecTypeConstants.ECS_CANARY_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.ECS_CANARY_DELETE)
  ECS_CANARY_DELETE(
      "ECS Canary Delete", Arrays.asList(ServiceDefinitionType.ECS), "ECS", StepSpecTypeConstants.ECS_CANARY_DELETE),
  @JsonProperty(StepSpecTypeConstants.ECS_RUN_TASK)
  ECS_RUN_TASK("ECS Run Task", Arrays.asList(ServiceDefinitionType.ECS), "ECS", StepSpecTypeConstants.ECS_RUN_TASK),
  // ssh steps
  @JsonProperty(StepSpecTypeConstants.COMMAND)
  COMMAND("Command", Arrays.asList(ServiceDefinitionType.SSH, ServiceDefinitionType.WINRM), "Command",
      StepSpecTypeConstants.COMMAND),
  // Jenkns Build
  @JsonProperty(StepSpecTypeConstants.JENKINS_BUILD)
  JENKINS_BUILD(
      "Jenkins Build", Arrays.asList(ServiceDefinitionType.values()), "Builds", StepSpecTypeConstants.JENKINS_BUILD),
  // Azure ARM/BP
  @JsonProperty(StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE)
  AZURE_CREATE_ARM_RESOURCE("Azure Create ARM", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/ARM", StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE),
  @JsonProperty(StepSpecTypeConstants.AZURE_CREATE_BP_RESOURCE)
  AZURE_CREATE_BP_RESOURCE("Azure Create Blueprint Resource", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Azure Blueprint", StepSpecTypeConstants.AZURE_CREATE_BP_RESOURCE),
  @JsonProperty(StepSpecTypeConstants.AZURE_ROLLBACK_ARM_RESOURCE)
  AZURE_ROLLBACK_ARM_RESOURCE("Azure ARM Rollback", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Azure ARM", StepSpecTypeConstants.AZURE_ROLLBACK_ARM_RESOURCE),
  @JsonProperty(StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE)
  ECS_BLUE_GREEN_CREATE_SERVICE("ECS Blue Green Create Service", Arrays.asList(ServiceDefinitionType.ECS), "ECS",
      StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE),
  @JsonProperty(StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS)
  ECS_BLUE_GREEN_SWAP_TARGET_GROUPS("ECS Blue Green Swap Target Group", Arrays.asList(ServiceDefinitionType.ECS), "ECS",
      StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS),
  @JsonProperty(StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK)
  ECS_BLUE_GREEN_ROLLBACK("ECS Blue Green Rollback", Arrays.asList(ServiceDefinitionType.ECS), "ECS",
      StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.GITOPS_UPDATE_RELEASE_REPO)
  GITOPS_UPDATE_RELEASE_REPO("Update Release Repo", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.GITOPS_UPDATE_RELEASE_REPO),
  @JsonProperty(StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS)
  GITOPS_FETCH_LINKED_APPS("Fetch Linked Apps", Arrays.asList(ServiceDefinitionType.KUBERNETES), "Kubernetes",
      StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS),
  @JsonProperty(StepSpecTypeConstants.ELASTIGROUP_DEPLOY)
  ELASTIGROUP_DEPLOY("Elastigroup Deploy", Arrays.asList(ServiceDefinitionType.ELASTIGROUP), "Elastigroup",
      StepSpecTypeConstants.ELASTIGROUP_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.ELASTIGROUP_ROLLBACK)
  ELASTIGROUP_ROLLBACK("Elastigroup Rollback", Arrays.asList(ServiceDefinitionType.ELASTIGROUP), "Elastigroup",
      StepSpecTypeConstants.ELASTIGROUP_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.ELASTIGROUP_SETUP)
  ELASTIGROUP_SETUP("Elastigroup Setup", Arrays.asList(ServiceDefinitionType.ELASTIGROUP), "Elastigroup",
      StepSpecTypeConstants.ELASTIGROUP_SETUP),
  @JsonProperty(StepSpecTypeConstants.TERRAGRUNT_PLAN)
  TERRAGRUNT_PLAN("Terragrunt Plan", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terragrunt", StepSpecTypeConstants.TERRAGRUNT_PLAN),
  @JsonProperty(StepSpecTypeConstants.TERRAGRUNT_APPLY)
  TERRAGRUNT_APPLY("Terragrunt Apply", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terragrunt", StepSpecTypeConstants.TERRAGRUNT_APPLY),
  @JsonProperty(StepSpecTypeConstants.TERRAGRUNT_DESTROY)
  TERRAGRUNT_DESTROY("Terragrunt Destroy", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terragrunt", StepSpecTypeConstants.TERRAGRUNT_DESTROY),
  @JsonProperty(StepSpecTypeConstants.TERRAGRUNT_ROLLBACK)
  TERRAGRUNT_ROLLBACK("Terragrunt Rollback", Arrays.asList(ServiceDefinitionType.values()),
      "Infrastructure Provisioners/Terragrunt", StepSpecTypeConstants.TERRAGRUNT_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.ASG_CANARY_DEPLOY)
  ASG_CANARY_DEPLOY(
      "Asg Canary Deploy", Arrays.asList(ServiceDefinitionType.ASG), "ASG", StepSpecTypeConstants.ASG_CANARY_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.ASG_CANARY_DELETE)
  ASG_CANARY_DELETE(
      "Asg Canary Delete", Arrays.asList(ServiceDefinitionType.ASG), "ASG", StepSpecTypeConstants.ASG_CANARY_DELETE),
  @JsonProperty(StepSpecTypeConstants.TAS_BASIC_APP_SETUP)
  TAS_BASIC_APP_SETUP(
      "Basic App Setup", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TAS_BASIC_APP_SETUP),
  @JsonProperty(StepSpecTypeConstants.TAS_BG_APP_SETUP)
  TAS_BG_APP_SETUP(
      "BG App Setup", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TAS_BG_APP_SETUP),
  @JsonProperty(StepSpecTypeConstants.TAS_CANARY_APP_SETUP)
  TAS_CANARY_APP_SETUP(
      "Canary App Setup", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TAS_CANARY_APP_SETUP),
  @JsonProperty(StepSpecTypeConstants.TANZU_COMMAND)
  TANZU_COMMAND("Tanzu Command", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TANZU_COMMAND),
  @JsonProperty(StepSpecTypeConstants.SWAP_ROLLBACK)
  SWAP_ROLLBACK("Swap Rollback", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.SWAP_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.TAS_SWAP_ROUTES)
  TAS_SWAP_ROUTES(
      "Swap Routes", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TAS_SWAP_ROUTES),
  @JsonProperty(StepSpecTypeConstants.TAS_APP_RESIZE)
  TAS_APP_RESIZE("App Resize", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TAS_APP_RESIZE),
  @JsonProperty(StepSpecTypeConstants.TAS_ROLLBACK)
  TAS_ROLLBACK("App Rollback", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TAS_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.ELASTIGROUP_BG_STAGE_SETUP)
  ELASTIGROUP_BG_STAGE_SETUP("Elastigroup BG Stage Setup", Arrays.asList(ServiceDefinitionType.ELASTIGROUP),
      "Elastigroup", StepSpecTypeConstants.ELASTIGROUP_BG_STAGE_SETUP),

  @JsonProperty(StepSpecTypeConstants.ELASTIGROUP_SWAP_ROUTE)
  ELASTIGROUP_SWAP_ROUTE("Elastigroup Swap Route", Arrays.asList(ServiceDefinitionType.ELASTIGROUP), "Elastigroup",
      StepSpecTypeConstants.ELASTIGROUP_SWAP_ROUTE),
  @JsonProperty(StepSpecTypeConstants.ASG_ROLLING_DEPLOY)
  ASG_ROLLING_DEPLOY(
      "Asg Rolling Deploy", Arrays.asList(ServiceDefinitionType.ASG), "ASG", StepSpecTypeConstants.ASG_ROLLING_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.ASG_ROLLING_ROLLBACK)
  ASG_ROLLING_ROLLBACK("Asg Rolling Rollback", Arrays.asList(ServiceDefinitionType.ASG), "ASG",
      StepSpecTypeConstants.ASG_ROLLING_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.TAS_ROLLING_DEPLOY)
  TAS_ROLLING_DEPLOY(
      "Rolling Deploy", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TAS_ROLLING_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.TAS_ROLLING_ROLLBACK)
  TAS_ROLLING_ROLLBACK(
      "Rolling Rollback", Arrays.asList(ServiceDefinitionType.TAS), "TAS", StepSpecTypeConstants.TAS_ROLLING_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.ASG_BLUE_GREEN_SWAP_SERVICE)
  ASG_BLUE_GREEN_SWAP_SERVICE("Asg Blue Green Swap Service", Arrays.asList(ServiceDefinitionType.ASG), "ASG",
      StepSpecTypeConstants.ASG_BLUE_GREEN_SWAP_SERVICE),
  @JsonProperty(StepSpecTypeConstants.ASG_BLUE_GREEN_DEPLOY)
  ASG_BLUE_GREEN_DEPLOY("Asg Blue Green Deploy", Arrays.asList(ServiceDefinitionType.ASG), "ASG",
      StepSpecTypeConstants.ASG_BLUE_GREEN_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.ASG_BLUE_GREEN_ROLLBACK)
  ASG_BLUE_GREEN_ROLLBACK("Asg Blue Green Rollback", Arrays.asList(ServiceDefinitionType.ASG), "ASG",
      StepSpecTypeConstants.ASG_BLUE_GREEN_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY)
  GOOGLE_CLOUD_FUNCTIONS_DEPLOY("Google Cloud Functions Deploy",
      Arrays.asList(ServiceDefinitionType.GOOGLE_CLOUD_FUNCTIONS), "Google Functions",
      StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC)
  GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC("Google Cloud Functions Deploy Without Traffic",
      Arrays.asList(ServiceDefinitionType.GOOGLE_CLOUD_FUNCTIONS), "Google Functions",
      StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC),
  @JsonProperty(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT)
  GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT("Google Cloud Functions Deploy Traffic Shift",
      Arrays.asList(ServiceDefinitionType.GOOGLE_CLOUD_FUNCTIONS), "Google Functions",
      StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT),
  @JsonProperty(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK)
  GOOGLE_CLOUD_FUNCTIONS_ROLLBACK("Google Cloud Functions Rollback",
      Arrays.asList(ServiceDefinitionType.GOOGLE_CLOUD_FUNCTIONS), "Google Functions",
      StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.AWS_LAMBDA_DEPLOY)
  AWS_LAMBDA_DEPLOY("AWS Lambda Deploy", Arrays.asList(ServiceDefinitionType.AWS_LAMBDA), "AWS Lambda",
      StepSpecTypeConstants.AWS_LAMBDA_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.AWS_SAM_DEPLOY)
  AWS_SAM_DEPLOY(
      "AWS SAM Deploy", Arrays.asList(ServiceDefinitionType.AWS_SAM), "AWS SAM", StepSpecTypeConstants.AWS_SAM_DEPLOY),
  @JsonProperty(StepSpecTypeConstants.AWS_SAM_ROLLBACK)
  AWS_SAM_ROLLBACK("AWS SAM Rollback", Arrays.asList(ServiceDefinitionType.AWS_SAM), "AWS SAM",
      StepSpecTypeConstants.AWS_SAM_ROLLBACK),
  @JsonProperty(StepSpecTypeConstants.AWS_LAMBDA_ROLLBACK)
  AWS_LAMBDA_ROLLBACK("AWS Lambda Rollback", Arrays.asList(ServiceDefinitionType.AWS_LAMBDA), "AWS Lambda",
      StepSpecTypeConstants.AWS_LAMBDA_ROLLBACK);

  private String displayName;
  private List<ServiceDefinitionType> serviceDefinitionTypes;
  private String category;
  private String yamlName;

  NGStepType(String displayName, List<ServiceDefinitionType> serviceDefinitionTypes, String category, String yamlName) {
    this.displayName = displayName;
    this.serviceDefinitionTypes = serviceDefinitionTypes;
    this.category = category;
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static NGStepType getNGStepType(@JsonProperty("type") String yamlName) {
    for (NGStepType ngStepType : NGStepType.values()) {
      if (ngStepType.yamlName.equalsIgnoreCase(yamlName)) {
        return ngStepType;
      }
    }
    throw new IllegalArgumentException(
        String.format("Invalid value:%s, the expected values are: %s", yamlName, Arrays.toString(NGStepType.values())));
  }

  public static List<ServiceDefinitionType> getServiceDefinitionTypes(NGStepType ngStepType) {
    return ngStepType.serviceDefinitionTypes;
  }

  public static String getDisplayName(NGStepType ngStepType) {
    return ngStepType.displayName;
  }
  public static String getCategory(NGStepType ngStepType) {
    return ngStepType.category;
  }

  public String getYamlName(NGStepType ngStepType) {
    return ngStepType.yamlName;
  }
}
