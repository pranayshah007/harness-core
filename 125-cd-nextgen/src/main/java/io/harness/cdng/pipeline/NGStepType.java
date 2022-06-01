/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline;

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
      StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK);

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
