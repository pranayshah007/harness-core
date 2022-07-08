/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.OrchestrationWorkflowType.BASIC;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.beans.OrchestrationWorkflowType.CANARY;
import static io.harness.beans.OrchestrationWorkflowType.MULTI_SERVICE;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;
import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.ExpressionEvaluator.getName;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.api.DeploymentType.AMI;
import static software.wings.api.DeploymentType.AWS_CODEDEPLOY;
import static software.wings.api.DeploymentType.AZURE_VMSS;
import static software.wings.api.DeploymentType.AZURE_WEBAPP;
import static software.wings.api.DeploymentType.CUSTOM;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.api.DeploymentType.HELM;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.PCF;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.INFRASTRUCTURE_MAPPING;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.InfrastructureMappingType.AWS_AWS_LAMBDA;
import static software.wings.beans.InfrastructureMappingType.AWS_SSH;
import static software.wings.beans.InfrastructureMappingType.PCF_PCF;
import static software.wings.beans.InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH;
import static software.wings.beans.PhaseStep.PhaseStepBuilder;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP;
import static software.wings.beans.PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP;
import static software.wings.beans.PhaseStepType.AMI_SWITCH_AUTOSCALING_GROUP_ROUTES;
import static software.wings.beans.PhaseStepType.CLUSTER_SETUP;
import static software.wings.beans.PhaseStepType.COLLECT_ARTIFACT;
import static software.wings.beans.PhaseStepType.CONTAINER_DEPLOY;
import static software.wings.beans.PhaseStepType.CONTAINER_SETUP;
import static software.wings.beans.PhaseStepType.CUSTOM_DEPLOYMENT_PHASE_STEP;
import static software.wings.beans.PhaseStepType.DEPLOY_AWSCODEDEPLOY;
import static software.wings.beans.PhaseStepType.DEPLOY_AWS_LAMBDA;
import static software.wings.beans.PhaseStepType.ECS_UPDATE_LISTENER_BG;
import static software.wings.beans.PhaseStepType.ECS_UPDATE_ROUTE_53_DNS_WEIGHT;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;
import static software.wings.beans.PhaseStepType.PCF_SWICH_ROUTES;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.settings.SettingVariableTypes.PHYSICAL_DATA_CENTER;
import static software.wings.sm.StateType.ARTIFACT_CHECK;
import static software.wings.sm.StateType.ARTIFACT_COLLECTION;
import static software.wings.sm.StateType.ASG_AMI_ALB_SHIFT_SWITCH_ROUTES;
import static software.wings.sm.StateType.ASG_AMI_ROLLBACK_ALB_SHIFT_SWITCH_ROUTES;
import static software.wings.sm.StateType.ASG_AMI_SERVICE_ALB_SHIFT_DEPLOY;
import static software.wings.sm.StateType.ASG_AMI_SERVICE_ALB_SHIFT_SETUP;
import static software.wings.sm.StateType.AWS_AMI_ROLLBACK_SWITCH_ROUTES;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_DEPLOY;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_ROLLBACK;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_SETUP;
import static software.wings.sm.StateType.AWS_AMI_SWITCH_ROUTES;
import static software.wings.sm.StateType.AWS_CODEDEPLOY_ROLLBACK;
import static software.wings.sm.StateType.AWS_CODEDEPLOY_STATE;
import static software.wings.sm.StateType.AWS_LAMBDA_ROLLBACK;
import static software.wings.sm.StateType.AWS_LAMBDA_STATE;
import static software.wings.sm.StateType.AWS_NODE_SELECT;
import static software.wings.sm.StateType.AZURE_NODE_SELECT;
import static software.wings.sm.StateType.COMMAND;
import static software.wings.sm.StateType.CUSTOM_DEPLOYMENT_FETCH_INSTANCES;
import static software.wings.sm.StateType.DC_NODE_SELECT;
import static software.wings.sm.StateType.ECS_BG_SERVICE_SETUP;
import static software.wings.sm.StateType.ECS_BG_SERVICE_SETUP_ROUTE53;
import static software.wings.sm.StateType.ECS_LISTENER_UPDATE;
import static software.wings.sm.StateType.ECS_ROUTE53_DNS_WEIGHT_UPDATE;
import static software.wings.sm.StateType.ECS_SERVICE_DEPLOY;
import static software.wings.sm.StateType.ECS_SERVICE_ROLLBACK;
import static software.wings.sm.StateType.ECS_SERVICE_SETUP_ROLLBACK;
import static software.wings.sm.StateType.ELASTIC_LOAD_BALANCER;
import static software.wings.sm.StateType.GCP_CLUSTER_SETUP;
import static software.wings.sm.StateType.KUBERNETES_DEPLOY;
import static software.wings.sm.StateType.KUBERNETES_DEPLOY_ROLLBACK;
import static software.wings.sm.StateType.KUBERNETES_SETUP;
import static software.wings.sm.StateType.KUBERNETES_SETUP_ROLLBACK;
import static software.wings.sm.StateType.KUBERNETES_SWAP_SERVICE_SELECTORS;
import static software.wings.sm.StateType.ROLLING_NODE_SELECT;
import static software.wings.sm.states.ElasticLoadBalancerState.Operation.Disable;
import static software.wings.sm.states.ElasticLoadBalancerState.Operation.Enable;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConstants;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;

import software.wings.api.DeploymentType;
import software.wings.beans.AmiDeploymentType;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.FailureStrategy;
import software.wings.beans.GraphNode;
import software.wings.beans.GraphNode.GraphNodeBuilder;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.PhysicalInfrastructureMappingBase;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowCreationFlags;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AzureVMSSInfra;
import software.wings.infra.CloudProviderInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PhysicalInfra;
import software.wings.service.impl.aws.model.AwsConstants;
import software.wings.service.impl.workflow.creation.abstractfactories.AbstractWorkflowFactory;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.sm.StateType;
import software.wings.sm.states.AwsCodeDeployState;
import software.wings.sm.states.customdeployment.InstanceFetchState.InstanceFetchStateKeys;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowServiceHelper {
  public static final String RUNTIME = "RUNTIME";
  public static final String DISABLE_SERVICE = "Disable Service";
  public static final String ENABLE_SERVICE = "Enable Service";
  public static final String DEPLOY_SERVICE = "Deploy Service";
  public static final String ROLLBACK_SERVICE = "Rollback Service";
  public static final String STOP_SERVICE = "Stop Service";
  public static final String VERIFY_SERVICE = "Verify Service";
  public static final String VERIFY_STAGING = "Verify Staging";
  public static final String DEPLOY_CONTAINERS = "Deploy Containers";
  public static final String SETUP_CONTAINER = "Set up Container";
  public static final String SETUP_CLUSTER = "Setup Cluster";
  public static final String ECS_SERVICE_SETUP = "ECS Service Setup";
  public static final String ECS_RUN_TASK = "ECS Run Task";
  public static final String ECS_DAEMON_SERVICE_SETUP = "ECS Daemon Service Setup";
  public static final String ECS_BG_SERVICE_SETUP_ELB = "Setup Load Balancer";
  public static final String ECS_BG_SERVICE_SETUP_ROUTE_53 = "Setup Route 53";
  public static final String ECS_DAEMON_SCHEDULING_STRATEGY = "DAEMON";
  public static final String CHANGE_ROUTE53_DNS_WEIGHTS = "Change Route 53 Weights";
  public static final String ECS_ROUTE53_DNS_WEIGHTS = "Swap Route 53 DNS";
  public static final String ECS_SWAP_TARGET_GROUPS = "Swap Target Groups";
  public static final String ECS_SWAP_TARGET_GROUPS_ROLLBACK = "Rollback Swap Target Groups";
  public static final String PRIMARY_SERVICE_NAME_EXPRESSION = "${PRIMARY_SERVICE_NAME}";
  public static final String ROLLBACK_AUTOSCALING_GROUP_ROUTE = "Rollback AutoScaling Group Route";
  public static final String ROLLBACK_ECS_ROUTE53_DNS_WEIGHTS = "Rollback Route 53 Weights";
  public static final String STAGE_SERVICE_NAME_EXPRESSION = "${STAGE_SERVICE_NAME}";
  public static final String SWAP_AUTOSCALING_GROUP_ROUTE = "Swap Routes";
  public static final String UPGRADE_AUTOSCALING_GROUP_ROUTE = "Switch AutoScaling Group Route";
  public static final String AWS_CODE_DEPLOY = "AWS CodeDeploy";
  public static final String UPGRADE_AUTOSCALING_GROUP = "Upgrade AutoScaling Group";
  public static final String UPGRADE_TRAFFIC_SHIFT_AUTOSCALING_GROUP = "Upgrade Traffic Shift AutoScaling Group";
  public static final String WRAP_UP = "Wrap Up";
  public static final String PREPARE_STEPS = "Prepare Steps";
  public static final String UPGRADE_CONTAINERS = "Upgrade Containers";
  public static final String AWS_LAMBDA = "AWS Lambda";
  public static final String ROLLBACK_PREFIX = "Rollback ";
  public static final String PCF_BG_MAP_ROUTE = "Update Route";
  public static final String PCF_BG_SWAP_ROUTE = "Swap Routes";

  public static final String DEPLOY = "Deploy";
  public static final String PCF_ROLLBACK = "App Rollback";
  public static final String HELM_ROLLBACK = "Helm Rollback";
  public static final String ROLLBACK_AWS_AMI_CLUSTER = "Rollback AutoScaling Group";
  public static final String PCF_RESIZE = "App Resize";
  public static final String HELM_DEPLOY = "Helm Deploy";
  public static final String KUBERNETES_SERVICE_SETUP = "Kubernetes Service Setup";
  public static final String INFRA_TEMP_ROUTE_PCF = "infra.pcf.tempRoute";
  public static final String SETUP = "Setup";
  public static final String PCF_SETUP = "App Setup";
  public static final String PCF_PLUGIN = "CF Command";
  public static final String AUTO_SCALING_GROUP_NAME = "Auto Scaling Group Name";
  public static final String LOAD_BALANCER_DETAILS = "Load Balancer Details";
  public static final String ASG_AMI_ALB_SHIFT_SETUP = "ASG AMI ALB Shift Setup";
  public static final String ASG_AMI_ALB_SHIFT_DEPLOY = "ASG AMI ALB Shift Deploy";
  public static final String SPOTINST_SETUP = "Elastigroup Setup";
  public static final String SPOTINST_ALB_SHIFT_SETUP = "Elastigroup ALB Shift Setup";
  public static final String SPOTINST_DEPLOY = "Elastigroup Deploy";
  public static final String SPOTINST_ALB_SHIFT_DEPLOY = "Elastigroup ALB Shift Deploy";
  public static final String SPOTINST_ROLLBACK = "Elastigroup Rollback";
  public static final String SPOTINST_LISTENER_UPDATE_ROLLBACK = "Route Update Rollback";
  public static final String SPOTINST_ALB_SHIFT_LISTENER_UPDATE_ROLLBACK = "Shift Traffic Weight Rollback";
  public static final String SPOTINST_SWAP_ROLLBACK = "Swap Production with Stage";
  public static final String SPOTINST_LISTENER_UPDATE = "Route Update";
  public static final String SPOTINST_ALB_SHIFT_LISTENER_UPDATE = "Shift Traffic Weight";
  public static final String SPOTINST_SWAP = "Swap Production with Stage";
  public static final String AZURE_VMSS_SETUP = "Azure Virtual Machine Scale Set Setup";
  public static final String AZURE_VMSS_DEPLOY = "Upgrade Virtual Machine Scale Set";
  public static final String AZURE_VMSS_ROLLBACK = "Azure Virtual Machine Scale Set Rollback";
  public static final String AZURE_VMSS_SWITCH_ROUTES = "Swap Virtual Machine Scale Set Route";
  public static final String AZURE_VMSS_SWITCH_ROUTES_ROLLBACK = "Rollback Virtual Machine Scale Set Route";
  public static final String AZURE_WEBAPP_SLOT_SETUP = "Slot Setup";
  public static final String AZURE_WEBAPP_SLOT_DEPLOYMENT = "Slot Deployment";
  public static final String AZURE_WEBAPP_SLOT_SWAP = "Swap Slot";
  public static final String AZURE_WEBAPP_SLOT_ROLLBACK = "Slot Rollback";
  public static final String AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT = "Shift Traffic to Slot";
  public static final String AZURE_WEBAPP_SLOT_TRAFFIC = "Traffic %";
  public static final String AZURE_WEBAPP_SLOT_ROUTE = "Swap Deployment Slots";
  public static final String KUBERNETES_SERVICE_SETUP_BLUEGREEN = "Blue/Green Service Setup";
  public static final String INFRA_ROUTE_PCF = "infra.pcf.route";
  public static final String VERIFY_STAGE_SERVICE = "Verify Stage Service";
  public static final String ROUTE_UPDATE = "Route Update";
  public static final String KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE = "Swap Primary with Stage";
  public static final String RANCHER_KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE = "Rancher Swap Primary with Stage";
  public static final String INFRASTRUCTURE_NODE_NAME = "Prepare Infra";
  public static final String SELECT_NODE_NAME = "Select Nodes";
  public static final String ROLLBACK_AWS_LAMBDA = "Rollback AWS Lambda";
  public static final String ROLLBACK_CONTAINERS = "Rollback Containers";
  public static final String ROLLBACK_AWS_CODE_DEPLOY = "Rollback AWS CodeDeploy";
  public static final String ROLLBACK_KUBERNETES_SETUP = "Rollback Kubernetes Setup";
  public static final String JIRA = "Jira";
  public static final String ARTIFACT_COLLECTION_STEP = "Artifact Collection";
  public static final String ARTIFACT_CHECK_STEP = "Artifact Check";
  public static final String APPDYNAMICS = "AppDynamics";
  public static final String NEW_RELIC = "New Relic";
  public static final String INSTANA = "Instana";
  public static final String DYNATRACE = "Dynatrace";
  public static final String PROMETHEUS = "Prometheus";
  public static final String DATADOG_METRICS = "Datadog Metrics";
  public static final String STACKDRIVER = "Stackdriver";
  public static final String CLOUDWATCH = "CloudWatch";
  public static final String SCALYR = "Scalyr";
  public static final String BUG_SNAG = "Bugsnag";
  public static final String CUSTOM_METRICS = "Custom Metrics";
  public static final String DATADOG_LOG = "Datadog Log";
  public static final String ELK = "ELK";
  public static final String SPLUNK_V2 = "Splunkv2";
  public static final String STACKDRIVER_LOG = "Stackdriver Log";
  public static final String SUMO_LOGIC = "Sumo Logic";
  public static final String LOGZ = "LOGZ";
  public static final String CUSTOM_LOG_VERIFICATION = "Custom Log Verification";
  public static final String CVNG_STATE = "CVNG verification";
  public static final String AWS_SELECT_NODES = "AWS Select Nodes";
  public static final String ELB = "Elastic Load Balancer";
  public static final String AZURE_SELECT_NODES = "Azure Select Nodes";
  public static final String ROLLBACK_ECS_SETUP = "Rollback ECS Setup";
  public static final String PCF_MAP_ROUTE_NAME = "Map Route";
  public static final String PCF_UNMAP_ROUTE_NAME = "Unmap Route";
  public static final String PROVISION_SHELL_SCRIPT = "Shell Script Provision";
  public static final String ROLLBACK_CLOUD_FORMATION = "CloudFormation Rollback Stack";
  public static final String ROLLBACK_TERRAFORM_NAME = "Terraform Rollback";
  public static final String ECS_STEADY_STATE_CHK = "ECS Steady State Check";
  public static final String KUBERNETES_STEADY_STATE_CHECK = "Steady State Check";
  public static final String ECS_UPGRADE_CONTAINERS = "ECS Upgrade Containers";
  public static final String ECS_ROLLBACK_CONTAINERS = "ECS Rollback Containers";
  public static final String KUBERNETES_UPGRADE_CONTAINERS = "Kubernetes Upgrade Containers";
  public static final String KUBERNETES_ROLLBACK_CONTAINERS = "Kubernetes Rollback Containers";
  public static final String GCP_CLUSTER_SETUP_NAME = "GCP Cluster Setup";
  public static final String SWAP_SERVICE_SELECTORS = "Swap Service Selectors";
  public static final String ROLLING_SELECT_NODES = "Rolling Select Nodes";
  public static final String CF_CREATE_STACK = "CloudFormation Create Stack";
  public static final String CF_DELETE_STACK = "CloudFormation Delete Stack";
  public static final String TERRAFORM_APPLY = "Terraform Apply";
  public static final String TERRAFORM_PROVISION = "Terraform Provision";
  public static final String ARM_CREATE_RESOURCE = "ARM/Blueprint Create Resource";
  public static final String TERRAFORM_DESTROY = "Terraform Destroy";
  public static final String TERRAGRUNT_PROVISION = "Terragrunt Provision";
  public static final String TERRAGRUNT_DESTROY = "Terragrunt Destroy";
  public static final String TERRAGRUNT_ROLLBACK = "Terragrunt Rollback";

  public static final String SERVICENOW = "ServiceNow";
  public static final String EMAIL = "Email";
  public static final String BARRIER = "Barrier";
  public static final String RESOURCE_CONSTRAINT = "Resource Constraint";
  public static final String APPROVAL_NAME = "Approval";
  public static final String JENKINS = "Jenkins";
  public static final String GCB = "Google Cloud Build";
  public static final String BAMBOO = "Bamboo";
  public static final String SHELL_SCRIPT = "Shell Script";
  public static final String HTTP = "HTTP";
  public static final String TEMPLATIZED_SECRET_MANAGER = "Templatized Secret Manager";
  public static final String NEW_RELIC_DEPLOYMENT_MARKER = "New Relic Deployment Marker";
  public static final String COMMAND_NAME = "Command";
  public static final String FETCH_INSTANCES = "Fetch Instances";

  private static final String COLLECT_ARTIFACT_PHASE_STEP_NAME = "Collect Artifact";
  private static final String SETUP_AUTOSCALING_GROUP = "Setup AutoScaling Group";
  private static final String PROVISION_INFRASTRUCTURE = "Provision Infrastructure";
  private static final String MIN_REPLICAS = "\\$\\{MIN_REPLICAS}";
  private static final String MAX_REPLICAS = "\\$\\{MAX_REPLICAS}";
  private static final String UTILIZATION = "\\$\\{UTILIZATION}";
  // yaml template for custom metric HPA for cpu utilization threshold
  private static final String yamlForHPAWithCustomMetric = "apiVersion: autoscaling/v2beta1\n"
      + "kind: HorizontalPodAutoscaler\n"
      + "metadata:\n"
      + "  name: hpa-name\n"
      + "spec:\n"
      + "  scaleTargetRef:\n"
      + "    apiVersion: extensions/v1beta1\n"
      + "    kind: Deployment\n"
      + "    name: target-name\n"
      + "  minReplicas: ${MIN_REPLICAS}\n"
      + "  maxReplicas: ${MAX_REPLICAS}\n"
      + "  metrics:\n"
      + "  - type: Resource\n"
      + "    resource:\n"
      + "      name: cpu\n"
      + "      targetAverageUtilization: ${UTILIZATION}\n";

  @Inject private EnvironmentService environmentService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ExpressionEvaluator expressionEvaluator;
  @Inject private AppService appService;
  @Inject private FeatureFlagService featureFlagService;

  public String getHPAYamlStringWithCustomMetric(
      Integer minAutoscaleInstances, Integer maxAutoscaleInstances, Integer targetCpuUtilizationPercentage) {
    try {
      String hpaYaml =
          yamlForHPAWithCustomMetric.replaceAll(MIN_REPLICAS, String.valueOf(minAutoscaleInstances.intValue()))
              .replaceAll(MAX_REPLICAS, String.valueOf(maxAutoscaleInstances.intValue()))
              .replaceAll(UTILIZATION, String.valueOf(targetCpuUtilizationPercentage.intValue()));
      if (KubernetesHelper.loadYaml(hpaYaml, HorizontalPodAutoscaler.class) == null) {
        log.error("HPA couldn't be parsed: {}", hpaYaml);
      }
      return hpaYaml;
    } catch (IOException e) {
      throw new InvalidRequestException("Unable to generate Yaml String for Horizontal pod autoscalar", USER);
    }
  }

  public boolean workflowHasSshDeploymentPhase(CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    List<WorkflowPhase> workflowPhases = canaryOrchestrationWorkflow.getWorkflowPhases();
    if (isNotEmpty(workflowPhases)) {
      return workflowPhases.stream().anyMatch(workflowPhase -> DeploymentType.SSH == workflowPhase.getDeploymentType());
    }
    return false;
  }

  public List<DeploymentType> obtainDeploymentTypes(OrchestrationWorkflow orchestrationWorkflow) {
    if (orchestrationWorkflow instanceof CanaryOrchestrationWorkflow) {
      List<WorkflowPhase> workflowPhases = ((CanaryOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();
      if (isNotEmpty(workflowPhases)) {
        return workflowPhases.stream()
            .map(WorkflowPhase::getDeploymentType)
            .filter(Objects::nonNull)
            .distinct()
            .collect(toList());
      }
    }
    return new ArrayList<>();
  }

  public boolean needArtifactCheckStep(String appId, CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    List<WorkflowPhase> workflowPhases = canaryOrchestrationWorkflow.getWorkflowPhases();
    if (isNotEmpty(canaryOrchestrationWorkflow.getWorkflowPhases())) {
      List<String> infraDefinitionIds = workflowPhases.stream()
                                            .filter(workflowPhase -> workflowPhase.getInfraDefinitionId() != null)
                                            .map(WorkflowPhase::getInfraDefinitionId)
                                            .collect(toList());

      return infrastructureDefinitionService.getInfraStructureDefinitionByUuids(appId, infraDefinitionIds)
          .stream()
          .filter(infrastructureDefinition -> infrastructureDefinition.getInfrastructure() != null)
          .anyMatch((InfrastructureDefinition infra)
                        -> AWS_SSH.name().equals(infra.getInfrastructure().getInfrastructureType())
                  || PHYSICAL_DATA_CENTER_SSH.name().equals(infra.getInfrastructure().getInfrastructureType())
                  || PCF_PCF.name().equals(infra.getInfrastructure().getInfrastructureType())
                  || AWS_AWS_LAMBDA.name().equals(infra.getInfrastructure().getInfrastructureType())
                  || InfrastructureMappingType.AZURE_WEBAPP.name().equals(
                      infra.getInfrastructure().getInfrastructureType()));
    }
    return false;
  }

  public boolean ensureArtifactCheckInPreDeployment(CanaryOrchestrationWorkflow canaryOrchestrationWorkflow) {
    PhaseStep preDeploymentSteps = canaryOrchestrationWorkflow.getPreDeploymentSteps();
    if (preDeploymentSteps == null) {
      preDeploymentSteps = new PhaseStep();
      canaryOrchestrationWorkflow.setPreDeploymentSteps(preDeploymentSteps);
    }
    if (preDeploymentSteps.getSteps() == null) {
      preDeploymentSteps.setSteps(new ArrayList<>());
    }
    boolean artifactCheckFound =
        preDeploymentSteps.getSteps().stream().anyMatch(graphNode -> ARTIFACT_CHECK.name().equals(graphNode.getType()));
    if (artifactCheckFound) {
      return false;
    } else {
      preDeploymentSteps.getSteps().add(GraphNode.builder().type(ARTIFACT_CHECK.name()).name("Artifact Check").build());
      return true;
    }
  }

  public String obtainEnvIdWithoutOrchestration(Workflow workflow, Map<String, String> workflowVariables) {
    final String envTemplatizedName = workflow.fetchEnvTemplatizedName();
    if (isEmpty(envTemplatizedName)) {
      return workflow.getEnvId();
    }
    if (isEmpty(workflowVariables)) {
      return null;
    }
    return workflowVariables.get(envTemplatizedName);
  }

  public String obtainTemplatedEnvironmentId(Workflow workflow, Map<String, String> workflowVariables) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (!workflow.checkEnvironmentTemplatized()) {
      return workflow.getEnvId();
    } else {
      if (isNotEmpty(workflowVariables)) {
        String envName =
            WorkflowServiceTemplateHelper.getTemplatizedEnvVariableName(orchestrationWorkflow.getUserVariables());
        if (envName != null) {
          if (workflowVariables.get(envName) != null) {
            return workflowVariables.get(envName);
          }
        }
      }
    }
    return null;
  }

  public String resolveEnvironmentId(Workflow workflow, Map<String, String> workflowVariables) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (!workflow.checkEnvironmentTemplatized()) {
      return workflow.getEnvId();
    } else {
      if (isNotEmpty(workflowVariables)) {
        String envName =
            WorkflowServiceTemplateHelper.getTemplatizedEnvVariableName(orchestrationWorkflow.getUserVariables());
        if (envName != null) {
          if (workflowVariables.get(envName) != null) {
            return workflowVariables.get(envName);
          }
        }
      }
    }
    throw new InvalidRequestException(
        "Workflow [" + workflow.getName() + "] environment parameterized. However, the value not supplied", USER);
  }

  public Set<String> getKeywords(Workflow workflow) {
    Set<String> keywords = workflow.generateKeywords();
    if (workflow.getEnvId() != null) {
      Environment environment = environmentService.get(workflow.getAppId(), workflow.getEnvId());
      if (environment != null) {
        keywords.add(environment.getName());
      }
    }
    return trimmedLowercaseSet(keywords);
  }

  public void setKeywords(Workflow workflow) {
    workflow.setDefaultVersion(1);
    List<String> keywords = new ArrayList<>(asList(workflow.getName(), workflow.getDescription(), workflow.getNotes()));
    if (workflow.getWorkflowType() != null) {
      keywords.add(workflow.getWorkflowType().name());
    }
    workflow.setKeywords(trimmedLowercaseSet(keywords));
  }

  /**
   * Validates whether service id and mapped service are of same type
   */
  public void validateServiceMapping(String appId, String targetAppId, Map<String, String> serviceMapping) {
    if (serviceMapping == null) {
      throw new InvalidRequestException("At least one service mapping required to clone across applications", USER);
    }
    for (Map.Entry<String, String> service : serviceMapping.entrySet()) {
      if (service.getKey() == null || service.getValue() == null) {
        continue;
      }
      Service oldService = serviceResourceService.get(appId, service.getKey(), false);
      notNullCheck("Source service does not exist", oldService, USER);
      Service newService = serviceResourceService.get(targetAppId, service.getValue(), false);
      notNullCheck("Target service does not exist", newService, USER);
      if (oldService.getArtifactType() != null && oldService.getArtifactType() != newService.getArtifactType()) {
        throw new InvalidRequestException("Target service  [" + oldService.getName()
                + " ] is not compatible with service [" + newService.getName() + "]",
            USER);
      }
    }
  }

  public void validateServiceAndInfraMapping(String appId, String serviceId, String infraMappingId) {
    // Validate if service Id is valid or not
    if (serviceId == null || infraMappingId == null) {
      return;
    }
    Service service = serviceResourceService.get(appId, serviceId, false);
    if (service == null) {
      throw new InvalidRequestException("Service [" + serviceId + "] does not exist", USER);
    }
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (infrastructureMapping == null) {
      throw new InvalidRequestException("Service Infrastructure [" + infraMappingId + "] does not exist", USER);
    }
    if (!service.getUuid().equals(infrastructureMapping.getServiceId())) {
      throw new InvalidRequestException("Service Infrastructure [" + infrastructureMapping.getName()
              + "] not mapped to Service [" + service.getName() + "]",
          USER);
    }
  }

  public void validateServiceAndInfraDefinition(String appId, String serviceId, String infraDefinitionId) {
    if (serviceId == null || infraDefinitionId == null) {
      return;
    }
    Service service = serviceResourceService.get(appId, serviceId, false);
    if (service == null) {
      throw new InvalidRequestException("Service [" + serviceId + "] does not exist", USER);
    }
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefinitionId);
    if (infrastructureDefinition == null) {
      throw new InvalidRequestException("Service Infrastructure [" + infraDefinitionId + "] does not exist", USER);
    }

    // Ignoring validation for old Services where no deployment type is present
    if (service.getDeploymentType() != null) {
      if (service.getDeploymentType() != infrastructureDefinition.getDeploymentType()) {
        throw new InvalidRequestException(
            "Service [" + serviceId + "] Infrastructure Definition[" + infraDefinitionId + "] are not compatible",
            USER);
      }
      List<String> scopedServices = infrastructureDefinition.getScopedToServices();
      if (EmptyPredicate.isNotEmpty(scopedServices)) {
        if (!scopedServices.contains(serviceId)) {
          throw new InvalidRequestException(
              "Service [" + serviceId + "] Infrastructure Definition[" + infraDefinitionId + "] are not compatible",
              USER);
        }
      }
    }
  }

  public void setCloudProvider(String appId, WorkflowPhase workflowPhase) {
    if (workflowPhase.checkInfraTemplatized()) {
      return;
    }
    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(appId, workflowPhase.getInfraMappingId());
    if (infrastructureMapping == null) {
      log.warn(
          "Service Infrastructure with id {}  for appId {} does not exist", workflowPhase.getInfraMappingId(), appId);
      throw new InvalidRequestException("ServiceInfrastructure does not exist", USER);
    }
    workflowPhase.setComputeProviderId(infrastructureMapping.getComputeProviderSettingId());
    workflowPhase.setInfraMappingName(infrastructureMapping.getName());

    DeploymentType deploymentType =
        serviceResourceService.getDeploymentType(infrastructureMapping, null, infrastructureMapping.getServiceId());
    workflowPhase.setDeploymentType(deploymentType);
  }

  public void setCloudProviderInfraRefactor(String appId, WorkflowPhase workflowPhase) {
    if (workflowPhase.checkInfraDefinitionTemplatized()) {
      return;
    }

    if (workflowPhase.getInfraDefinitionId() != null) {
      InfrastructureDefinition infrastructureDefinition =
          infrastructureDefinitionService.get(appId, workflowPhase.getInfraDefinitionId());
      workflowPhase.setComputeProviderId(infrastructureDefinition.getInfrastructure().getCloudProviderId());
      workflowPhase.setDeploymentType(infrastructureDefinition.getDeploymentType());
    } else {
      setCloudProvider(appId, workflowPhase);
    }
  }

  public void generateNewWorkflowPhaseStepsForSpotinst(
      String appId, WorkflowPhase workflowPhase, boolean serviceSetupRequired) {
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();
    if (serviceSetupRequired) {
      boolean spotInstInfra;
      InfrastructureDefinition infrastructureDefinition =
          infrastructureDefinitionService.get(appId, workflowPhase.getInfraDefinitionId());
      spotInstInfra = infrastructureDefinition.getInfrastructure() instanceof AwsAmiInfrastructure;
      if (spotInstInfra) {
        Map<String, Object> defaultData = new HashMap();
        defaultData.put("blueGreen", false);
        phaseSteps.add(aPhaseStep(PhaseStepType.SPOTINST_SETUP, SPOTINST_SETUP)
                           .addStep(GraphNode.builder()
                                        .id(generateUuid())
                                        .type(StateType.SPOTINST_SETUP.name())
                                        .name(SPOTINST_SETUP)
                                        .properties(defaultData)
                                        .build())
                           .build());
      }
    }
    phaseSteps.add(aPhaseStep(PhaseStepType.SPOTINST_DEPLOY, SPOTINST_DEPLOY)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.SPOTINST_DEPLOY.name())
                                    .name(SPOTINST_DEPLOY)
                                    .build())
                       .build());

    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_STAGING)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  @VisibleForTesting
  void generateNewWorkflowPhaseStepsForSpotinstAlbTrafficShift(String appId, WorkflowPhase workflowPhase) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();
    phaseSteps.add(aPhaseStep(PhaseStepType.SPOTINST_SETUP, SPOTINST_SETUP)
                       .addStep(GraphNode.builder()
                                    .type(StateType.SPOTINST_ALB_SHIFT_SETUP.name())
                                    .name(SPOTINST_ALB_SHIFT_SETUP)
                                    .build())
                       .build());
    Map<String, Object> deployStateMap = new HashMap<>();
    deployStateMap.put("instanceUnitType", "PERCENTAGE");
    deployStateMap.put("instanceCount", 100);
    phaseSteps.add(aPhaseStep(PhaseStepType.SPOTINST_DEPLOY, SPOTINST_DEPLOY)
                       .addStep(GraphNode.builder()
                                    .type(StateType.SPOTINST_ALB_SHIFT_DEPLOY.name())
                                    .properties(deployStateMap)
                                    .name(SPOTINST_ALB_SHIFT_DEPLOY)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_STAGING)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());
    Map<String, Object> shiftStateMap = new HashMap<>();
    shiftStateMap.put("downsizeOldElastigroup", true);
    phaseSteps.add(aPhaseStep(PhaseStepType.SPOTINST_LISTENER_UPDATE, SPOTINST_LISTENER_UPDATE)
                       .addStep(GraphNode.builder()
                                    .type(StateType.SPOTINST_LISTENER_ALB_SHIFT.name())
                                    .name(SPOTINST_ALB_SHIFT_LISTENER_UPDATE)
                                    .properties(shiftStateMap)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForSpotInstBlueGreen(
      String appId, WorkflowPhase workflowPhase, boolean serviceSetupRequired) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();
    if (serviceSetupRequired) {
      boolean spotInstInfra;
      InfrastructureDefinition infrastructureDefinition =
          infrastructureDefinitionService.get(appId, workflowPhase.getInfraDefinitionId());
      spotInstInfra = infrastructureDefinition.getInfrastructure() instanceof AwsAmiInfrastructure;
      if (spotInstInfra) {
        Map<String, Object> defaultData = newHashMap();
        defaultData.put("blueGreen", true);
        phaseSteps.add(aPhaseStep(PhaseStepType.SPOTINST_SETUP, SPOTINST_SETUP)
                           .addStep(GraphNode.builder()
                                        .id(generateUuid())
                                        .type(StateType.SPOTINST_SETUP.name())
                                        .name(SPOTINST_SETUP)
                                        .properties(defaultData)
                                        .build())
                           .build());
      }
    }
    Map<String, Object> deployStateMap = newHashMap();
    deployStateMap.put("instanceUnitType", "PERCENTAGE");
    deployStateMap.put("instanceCount", 100);
    phaseSteps.add(aPhaseStep(PhaseStepType.SPOTINST_DEPLOY, SPOTINST_DEPLOY)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.SPOTINST_DEPLOY.name())
                                    .name(SPOTINST_DEPLOY)
                                    .properties(deployStateMap)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_STAGING)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());
    Map<String, Object> defaultDataSwitchRoutes = newHashMap();
    defaultDataSwitchRoutes.put("downsizeOldElastiGroup", true);
    phaseSteps.add(aPhaseStep(PhaseStepType.SPOTINST_LISTENER_UPDATE, SPOTINST_LISTENER_UPDATE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.SPOTINST_LISTENER_UPDATE.name())
                                    .name(SPOTINST_SWAP)
                                    .properties(defaultDataSwitchRoutes)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForAWSAmiBlueGreen(
      String appId, WorkflowPhase workflowPhase, boolean serviceSetupRequired, boolean isDynamicInfrastructure) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    if (isDynamicInfrastructure) {
      phaseSteps.add(aPhaseStep(PhaseStepType.PROVISION_INFRASTRUCTURE, PROVISION_INFRASTRUCTURE).build());
    }

    if (serviceSetupRequired) {
      boolean awsAmiInfra;
      InfrastructureDefinition infrastructureDefinition =
          infrastructureDefinitionService.get(appId, workflowPhase.getInfraDefinitionId());
      awsAmiInfra = infrastructureDefinition.getInfrastructure() instanceof AwsAmiInfrastructure;
      if (awsAmiInfra) {
        Map<String, Object> defaultData = newHashMap();
        defaultData.put("maxInstances", 10);
        defaultData.put("autoScalingSteadyStateTimeout", 10);
        defaultData.put("blueGreen", true);
        phaseSteps.add(aPhaseStep(AMI_AUTOSCALING_GROUP_SETUP, SETUP_AUTOSCALING_GROUP)
                           .addStep(GraphNode.builder()
                                        .id(generateUuid())
                                        .type(AWS_AMI_SERVICE_SETUP.name())
                                        .name("AWS AutoScaling Group Setup")
                                        .properties(defaultData)
                                        .build())
                           .build());
      }
    }
    phaseSteps.add(aPhaseStep(AMI_DEPLOY_AUTOSCALING_GROUP, DEPLOY_SERVICE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(AWS_AMI_SERVICE_DEPLOY.name())
                                    .name(UPGRADE_AUTOSCALING_GROUP)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_STAGING)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    Map<String, Object> defaultDataSwitchRoutes = newHashMap();
    defaultDataSwitchRoutes.put("downsizeOldAsg", true);
    phaseSteps.add(aPhaseStep(AMI_SWITCH_AUTOSCALING_GROUP_ROUTES, SWAP_AUTOSCALING_GROUP_ROUTE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(AWS_AMI_SWITCH_ROUTES.name())
                                    .name(UPGRADE_AUTOSCALING_GROUP_ROUTE)
                                    .properties(defaultDataSwitchRoutes)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  @VisibleForTesting
  void generateNewWorkflowPhaseStepsForAsgAmiAlbTrafficShiftBlueGreen(String appId, WorkflowPhase workflowPhase) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    Map<String, Object> defaultData = new HashMap<>();
    defaultData.put(AwsConstants.MIN_INSTANCES, AwsConstants.DEFAULT_AMI_ASG_MIN_INSTANCES);
    defaultData.put(AwsConstants.MAX_INSTANCES, AwsConstants.DEFAULT_AMI_ASG_MAX_INSTANCES);
    defaultData.put(AwsConstants.DESIRED_INSTANCES, AwsConstants.DEFAULT_AMI_ASG_DESIRED_INSTANCES);
    defaultData.put(AwsConstants.AUTO_SCALING_TIMEOUT, AwsConstants.DEFAULT_AMI_ASG_TIMEOUT_MIN);
    phaseSteps.add(aPhaseStep(AMI_AUTOSCALING_GROUP_SETUP, SETUP_AUTOSCALING_GROUP)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ASG_AMI_SERVICE_ALB_SHIFT_SETUP.name())
                                    .name(WorkflowServiceHelper.ASG_AMI_ALB_SHIFT_SETUP)
                                    .properties(defaultData)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(AMI_DEPLOY_AUTOSCALING_GROUP, DEPLOY_SERVICE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ASG_AMI_SERVICE_ALB_SHIFT_DEPLOY.name())
                                    .name(UPGRADE_TRAFFIC_SHIFT_AUTOSCALING_GROUP)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_STAGING)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    Map<String, Object> defaultDataSwitchRoutes = new HashMap<>();
    defaultDataSwitchRoutes.put("downsizeOldAsg", Boolean.TRUE);
    phaseSteps.add(aPhaseStep(AMI_SWITCH_AUTOSCALING_GROUP_ROUTES, SWAP_AUTOSCALING_GROUP_ROUTE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ASG_AMI_ALB_SHIFT_SWITCH_ROUTES.name())
                                    .name(SPOTINST_ALB_SHIFT_LISTENER_UPDATE)
                                    .properties(defaultDataSwitchRoutes)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForAWSAmi(String appId, WorkflowPhase workflowPhase,
      boolean serviceSetupRequired, boolean isDynamicInfrastructure,
      OrchestrationWorkflowType orchestrationWorkflowType) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    if (isDynamicInfrastructure && BASIC == orchestrationWorkflowType) {
      phaseSteps.add(aPhaseStep(PhaseStepType.PROVISION_INFRASTRUCTURE, PROVISION_INFRASTRUCTURE).build());
    }

    if (serviceSetupRequired) {
      boolean isAwsAmiInfrastructure;
      InfrastructureDefinition infraDefinition =
          infrastructureDefinitionService.get(appId, workflowPhase.getInfraDefinitionId());
      isAwsAmiInfrastructure = infraDefinition.getInfrastructure() instanceof AwsAmiInfrastructure;
      if (isAwsAmiInfrastructure) {
        Map<String, Object> defaultData = new HashMap<>();
        defaultData.put("maxInstances", 10);
        defaultData.put("autoScalingSteadyStateTimeout", 10);
        defaultData.put("blueGreen", false);
        phaseSteps.add(aPhaseStep(AMI_AUTOSCALING_GROUP_SETUP, SETUP_AUTOSCALING_GROUP)
                           .addStep(GraphNode.builder()
                                        .id(generateUuid())
                                        .type(AWS_AMI_SERVICE_SETUP.name())
                                        .name("AWS AutoScaling Group Setup")
                                        .properties(defaultData)
                                        .build())
                           .build());
      }
    }
    phaseSteps.add(aPhaseStep(AMI_DEPLOY_AUTOSCALING_GROUP, DEPLOY_SERVICE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(AWS_AMI_SERVICE_DEPLOY.name())
                                    .name(UPGRADE_AUTOSCALING_GROUP)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForAzureVMSS(String appId, String accountId, WorkflowPhase workflowPhase,
      OrchestrationWorkflowType orchestrationWorkflowType, boolean serviceSetupRequired) {
    validateVMSSWorkflowCreation(accountId, orchestrationWorkflowType);
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    if (serviceSetupRequired) {
      boolean azureVMSSInfra;
      InfrastructureDefinition infrastructureDefinition =
          infrastructureDefinitionService.get(appId, workflowPhase.getInfraDefinitionId());
      azureVMSSInfra = infrastructureDefinition.getInfrastructure() instanceof AzureVMSSInfra;
      if (azureVMSSInfra) {
        Map<String, Object> defaultData = new HashMap<>();
        defaultData.put(AzureConstants.MIN_INSTANCES, AzureConstants.DEFAULT_AZURE_VMSS_MIN_INSTANCES);
        defaultData.put(AzureConstants.MAX_INSTANCES, AzureConstants.DEFAULT_AZURE_VMSS_MAX_INSTANCES);
        defaultData.put(AzureConstants.DESIRED_INSTANCES, AzureConstants.DEFAULT_AZURE_VMSS_DESIRED_INSTANCES);
        defaultData.put(AzureConstants.AUTO_SCALING_VMSS_TIMEOUT, AzureConstants.DEFAULT_AZURE_VMSS_TIMEOUT_MIN);
        defaultData.put(AzureConstants.BLUE_GREEN, Boolean.FALSE);
        phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_VMSS_SETUP, AZURE_VMSS_SETUP)
                           .addStep(GraphNode.builder()
                                        .id(generateUuid())
                                        .type(StateType.AZURE_VMSS_SETUP.name())
                                        .name(AZURE_VMSS_SETUP)
                                        .properties(defaultData)
                                        .build())
                           .build());
      }
    }

    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_VMSS_DEPLOY, AZURE_VMSS_DEPLOY)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_VMSS_DEPLOY.name())
                                    .name(AZURE_VMSS_DEPLOY)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    if (orchestrationWorkflowType == BLUE_GREEN) {
      Map<String, Object> defaultDataSwitchRoutes = newHashMap();
      defaultDataSwitchRoutes.put("downsizeOldVMSS", true);
      phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_VMSS_SWITCH_ROUTES, AZURE_VMSS_SWITCH_ROUTES)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(PhaseStepType.AZURE_VMSS_SWITCH_ROUTES.name())
                                      .name(AZURE_VMSS_SWITCH_ROUTES)
                                      .properties(defaultDataSwitchRoutes)
                                      .build())
                         .build());
    }
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  private void validateVMSSWorkflowCreation(String accountId, OrchestrationWorkflowType orchestrationWorkflowType) {
    if (!featureFlagService.isEnabled(FeatureName.AZURE_VMSS, accountId)) {
      throw new InvalidRequestException(
          format("Azure VMSS is disabled by feature flag for account id : %s", accountId), USER);
    }
    if (!isAzureVMSSSupportedWorkflowType(orchestrationWorkflowType)) {
      throw new InvalidRequestException(format("Unsupported Azure VMSS deployment type, orchestrationWorkflowType: %s",
                                            orchestrationWorkflowType != null ? orchestrationWorkflowType.name() : ""),
          USER);
    }
  }

  private boolean isAzureVMSSSupportedWorkflowType(OrchestrationWorkflowType workflowType) {
    return (BASIC == workflowType) || (CANARY == workflowType) || (BLUE_GREEN == workflowType);
  }

  public void generateNewWorkflowPhaseStepsForAzureWebApp(String appId, String accountId, WorkflowPhase workflowPhase,
      OrchestrationWorkflowType orchestrationWorkflowType, boolean isDynamicInfrastructure, boolean isFirstPhase) {
    validateWebAppWorkflowCreation(appId, accountId, orchestrationWorkflowType, workflowPhase);
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    if (isDynamicInfrastructure) {
      phaseSteps.add(aPhaseStep(PhaseStepType.PROVISION_INFRASTRUCTURE, PROVISION_INFRASTRUCTURE).build());
    }
    switch (orchestrationWorkflowType) {
      case BASIC:
        generateAppServiceBasicPhaseSteps(phaseSteps, commandMap);
        break;
      case CANARY:
        generateAppServiceCanaryPhaseSteps(isFirstPhase, phaseSteps, commandMap);
        break;
      case BLUE_GREEN:
        generateAppServiceBlueGreenPhaseSteps(phaseSteps, commandMap);
        break;
      default:
        return;
    }
  }

  private void generateAppServiceBasicPhaseSteps(
      List<PhaseStep> phaseSteps, Map<CommandType, List<Command>> commandMap) {
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_SETUP, AZURE_WEBAPP_SLOT_SETUP)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_WEBAPP_SLOT_SETUP.name())
                                    .name(AZURE_WEBAPP_SLOT_DEPLOYMENT)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  private void generateAppServiceCanaryPhaseSteps(
      boolean isFirstPhase, List<PhaseStep> phaseSteps, Map<CommandType, List<Command>> commandMap) {
    if (isFirstPhase) {
      phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_SETUP, AZURE_WEBAPP_SLOT_SETUP)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(StateType.AZURE_WEBAPP_SLOT_SETUP.name())
                                      .name(AZURE_WEBAPP_SLOT_DEPLOYMENT)
                                      .build())
                         .build());

      phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                         .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                         .build());
    }
    Map<String, Object> defaultData = new HashMap<>();
    defaultData.put(AzureConstants.TRAFFIC_WEIGHT_EXPR, 0);
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT, AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_WEBAPP_SLOT_SHIFT_TRAFFIC.name())
                                    .name(AZURE_WEBAPP_SLOT_TRAFFIC)
                                    .properties(defaultData)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_SWAP, AZURE_WEBAPP_SLOT_ROUTE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_WEBAPP_SLOT_SWAP.name())
                                    .name(AZURE_WEBAPP_SLOT_SWAP)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  private void generateAppServiceBlueGreenPhaseSteps(
      List<PhaseStep> phaseSteps, Map<CommandType, List<Command>> commandMap) {
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_SETUP, AZURE_WEBAPP_SLOT_SETUP)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_WEBAPP_SLOT_SETUP.name())
                                    .name(AZURE_WEBAPP_SLOT_DEPLOYMENT)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_SWAP, AZURE_WEBAPP_SLOT_ROUTE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_WEBAPP_SLOT_SWAP.name())
                                    .name(AZURE_WEBAPP_SLOT_SWAP)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  private void validateWebAppWorkflowCreation(String appId, String accountId,
      OrchestrationWorkflowType orchestrationWorkflowType, WorkflowPhase workflowPhase) {
    if (!featureFlagService.isEnabled(FeatureName.AZURE_WEBAPP, accountId)) {
      throw new InvalidRequestException(
          format("Azure WebApp deployment is disabled by feature flag for account id : %s", accountId), USER);
    }
    if (!isAzureWebAppSupportedWorkflowType(orchestrationWorkflowType)) {
      throw new InvalidRequestException(
          format(
              "Unsupported workflow type [%s] for Azure Web App deployment. Basic, Canary & Blue/Green deployment are supported",
              orchestrationWorkflowType != null ? orchestrationWorkflowType.name() : ""),
          USER);
    }
  }

  private boolean isAzureWebAppSupportedWorkflowType(OrchestrationWorkflowType workflowType) {
    return (BASIC == workflowType) || (CANARY == workflowType) || (BLUE_GREEN == workflowType);
  }

  public void generateNewWorkflowPhaseStepsForAWSLambda(String appId, WorkflowPhase workflowPhase) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    phaseSteps.add(aPhaseStep(PhaseStepType.PREPARE_STEPS, PREPARE_STEPS).build());

    phaseSteps.add(
        aPhaseStep(DEPLOY_AWS_LAMBDA, DEPLOY_SERVICE)
            .addStep(GraphNode.builder().id(generateUuid()).type(AWS_LAMBDA_STATE.name()).name(AWS_LAMBDA).build())
            .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForAWSCodeDeploy(String appId, WorkflowPhase workflowPhase) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    phaseSteps.add(aPhaseStep(PhaseStepType.PREPARE_STEPS, PREPARE_STEPS).build());

    Map<String, String> stateDefaults = getStateDefaults(appId, service.getUuid(), AWS_CODEDEPLOY_STATE);
    GraphNodeBuilder node =
        GraphNode.builder().id(generateUuid()).type(AWS_CODEDEPLOY_STATE.name()).name(AWS_CODE_DEPLOY);

    if (isNotEmpty(stateDefaults)) {
      Map<String, Object> properties = new HashMap<>();
      if (isNotBlank(stateDefaults.get("bucket"))) {
        properties.put("bucket", stateDefaults.get("bucket"));
      }
      if (isNotBlank(stateDefaults.get("key"))) {
        properties.put("key", stateDefaults.get("key"));
      }
      if (isNotBlank(stateDefaults.get("bundleType"))) {
        properties.put("bundleType", stateDefaults.get("bundleType"));
      }
      node.properties(properties);
    }
    phaseSteps.add(aPhaseStep(DEPLOY_AWSCODEDEPLOY, DEPLOY_SERVICE).addStep(node.build()).build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public Map<String, String> getStateDefaults(String appId, String serviceId, StateType stateType) {
    if (AWS_CODEDEPLOY_STATE == stateType) {
      List<ArtifactStream> artifactStreams = artifactStreamService.fetchArtifactStreamsForService(appId, serviceId);
      if (artifactStreams.stream().anyMatch(
              artifactStream -> ArtifactStreamType.AMAZON_S3.name().equals(artifactStream.getArtifactStreamType()))) {
        return AwsCodeDeployState.loadDefaults();
      }
    }
    return Collections.emptyMap();
  }

  public void generateNewWorkflowPhaseStepsForECS(String appId, WorkflowPhase workflowPhase,
      boolean serviceSetupRequired, OrchestrationWorkflowType orchestrationWorkflowType) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    boolean isDaemonEcsWorkflow = isDaemonSchedulingStrategy(appId, workflowPhase, orchestrationWorkflowType);
    if (serviceSetupRequired) {
      if (isDaemonEcsWorkflow) {
        phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                           .addStep(GraphNode.builder()
                                        .id(generateUuid())
                                        .type(StateType.ECS_DAEMON_SERVICE_SETUP.name())
                                        .name(ECS_DAEMON_SERVICE_SETUP)
                                        .build())
                           .build());
      } else {
        phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                           .addStep(GraphNode.builder()
                                        .id(generateUuid())
                                        .type(StateType.ECS_SERVICE_SETUP.name())
                                        .name(ECS_SERVICE_SETUP)
                                        .build())
                           .build());
      }
    }

    if (!isDaemonEcsWorkflow) {
      phaseSteps.add(aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(ECS_SERVICE_DEPLOY.name())
                                      .name(UPGRADE_CONTAINERS)
                                      .build())
                         .build());
    }

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  private Map<String, Object> getDefaultDeployPropertyMapForEcsBG() {
    Map<String, Object> deployProperties = newHashMap();
    deployProperties.put("instanceUnitType", "PERCENTAGE");
    deployProperties.put("instanceCount", 100);
    deployProperties.put("downsizeInstanceUnitType", "PERCENTAGE");
    deployProperties.put("downsizeInstanceCount", 100);
    return deployProperties;
  }

  private void addDeployAndVerifyPhaseStepForEcsBG(
      List<PhaseStep> phaseSteps, Map<CommandType, List<Command>> commandMap) {
    phaseSteps.add(aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ECS_SERVICE_DEPLOY.name())
                                    .name(UPGRADE_CONTAINERS)
                                    .properties(getDefaultDeployPropertyMapForEcsBG())
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());
  }

  public void generateNewWorkflowPhaseStepsForECSBlueGreenRoute53(
      String appId, WorkflowPhase workflowPhase, boolean serviceSetupRequired, boolean isDynamicInfrastructure) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();
    if (isDynamicInfrastructure) {
      phaseSteps.add(aPhaseStep(PhaseStepType.PROVISION_INFRASTRUCTURE, PROVISION_INFRASTRUCTURE).build());
    }
    if (serviceSetupRequired) {
      Map<String, Object> setupProperties = newHashMap();
      setupProperties.put("resizeStrategy", "RESIZE_NEW_FIRST");
      phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(ECS_BG_SERVICE_SETUP_ROUTE53.name())
                                      .name(ECS_BG_SERVICE_SETUP_ROUTE_53)
                                      .properties(setupProperties)
                                      .build())
                         .build());
    }

    addDeployAndVerifyPhaseStepForEcsBG(phaseSteps, commandMap);

    Map<String, Object> defaultDataSwitchRoutes = newHashMap();
    defaultDataSwitchRoutes.put("downsizeOldService", true);
    defaultDataSwitchRoutes.put("oldServiceDNSWeight", 0);
    defaultDataSwitchRoutes.put("newServiceDNSWeight", 100);
    defaultDataSwitchRoutes.put("recordTTL", 60);
    phaseSteps.add(aPhaseStep(ECS_UPDATE_ROUTE_53_DNS_WEIGHT, ECS_ROUTE53_DNS_WEIGHTS)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ECS_ROUTE53_DNS_WEIGHT_UPDATE.name())
                                    .name(CHANGE_ROUTE53_DNS_WEIGHTS)
                                    .properties(defaultDataSwitchRoutes)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForECSBlueGreen(
      String appId, WorkflowPhase workflowPhase, boolean serviceSetupRequired, boolean isDynamicInfrastructure) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    if (isDynamicInfrastructure) {
      phaseSteps.add(aPhaseStep(PhaseStepType.PROVISION_INFRASTRUCTURE, PROVISION_INFRASTRUCTURE).build());
    }

    if (serviceSetupRequired) {
      Map<String, Object> defaultSetupProperties = newHashMap();
      defaultSetupProperties.put("resizeStrategy", "RESIZE_NEW_FIRST");
      defaultSetupProperties.put("useLoadBalancer", true);

      phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(ECS_BG_SERVICE_SETUP.name())
                                      .name(ECS_BG_SERVICE_SETUP_ELB)
                                      .properties(defaultSetupProperties)
                                      .build())
                         .build());
    }

    addDeployAndVerifyPhaseStepForEcsBG(phaseSteps, commandMap);

    Map<String, Object> defaultDataSwitchRoutes = newHashMap();
    defaultDataSwitchRoutes.put("downsizeOldService", true);
    phaseSteps.add(aPhaseStep(ECS_UPDATE_LISTENER_BG, ECS_SWAP_TARGET_GROUPS)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ECS_LISTENER_UPDATE.name())
                                    .name(ECS_SWAP_TARGET_GROUPS)
                                    .properties(defaultDataSwitchRoutes)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  boolean isDaemonSchedulingStrategy(
      String appId, WorkflowPhase workflowPhase, OrchestrationWorkflowType orchestrationWorkflowType) {
    boolean isDaemonSchedulingStrategy = false;

    // DaemonSchedulingStrategy is only allowed for Basic workflow
    if (OrchestrationWorkflowType.BASIC == orchestrationWorkflowType) {
      String serviceId = workflowPhase.getServiceId();
      EcsServiceSpecification serviceSpecification =
          serviceResourceService.getEcsServiceSpecification(appId, serviceId);

      if (serviceSpecification != null) {
        if (isEmpty(serviceSpecification.getServiceSpecJson())) {
          isDaemonSchedulingStrategy =
              ECS_DAEMON_SCHEDULING_STRATEGY.equals(serviceSpecification.getSchedulingStrategy());
        } else {
          Pattern pattern = Pattern.compile("\"schedulingStrategy\":\\s*\"DAEMON\"\\s*", Pattern.CASE_INSENSITIVE);
          Matcher matcher = pattern.matcher(serviceSpecification.getServiceSpecJson());
          isDaemonSchedulingStrategy = matcher.find();
        }
      }
    }

    return isDaemonSchedulingStrategy;
  }

  private WorkflowPhaseBuilder rollbackWorkflow(WorkflowPhase workflowPhase) {
    WorkflowPhaseBuilder workflowPhaseBuilder = aWorkflowPhase()
                                                    .name(ROLLBACK_PREFIX + workflowPhase.getName())
                                                    .deploymentType(workflowPhase.getDeploymentType())
                                                    .rollback(true)
                                                    .phaseNameForRollback(workflowPhase.getName())
                                                    .serviceId(workflowPhase.getServiceId())
                                                    .computeProviderId(workflowPhase.getComputeProviderId())
                                                    .infraMappingId(workflowPhase.getInfraMappingId())
                                                    .infraMappingName(workflowPhase.getInfraMappingName());
    if (isNotBlank(workflowPhase.getInfraDefinitionId())) {
      workflowPhaseBuilder.infraDefinitionId(workflowPhase.getInfraDefinitionId());
    }
    return workflowPhaseBuilder;
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForPCFBlueGreen(WorkflowPhase workflowPhase) {
    if (workflowPhase.isDaemonSet() || workflowPhase.isStatefulSet()) {
      throw new InvalidRequestException("DaemonSet and StatefulSet are not supported with Blue/Green Deployment", USER);
    }

    Map<String, Object> defaultRouteUpdateProperties = new HashMap<>();
    defaultRouteUpdateProperties.put("service1", PRIMARY_SERVICE_NAME_EXPRESSION);
    defaultRouteUpdateProperties.put("service2", STAGE_SERVICE_NAME_EXPRESSION);

    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(PCF_SWICH_ROUTES, PCF_BG_MAP_ROUTE)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.PCF_BG_MAP_ROUTE.name())
                                            .name(PCF_BG_SWAP_ROUTE)
                                            .properties(defaultRouteUpdateProperties)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(PCF_BG_MAP_ROUTE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),

            // When we rolling back the verification steps
            // the same criteria to run if deployment is needed should be used
            aPhaseStep(PhaseStepType.PCF_RESIZE, DEPLOY)
                .addStep(GraphNode.builder()
                             .id(generateUuid())
                             .type(StateType.PCF_ROLLBACK.name())
                             .name(PCF_ROLLBACK)
                             .rollback(true)
                             .build())
                .withPhaseStepNameForRollback(DEPLOY)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(PCF_RESIZE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public void generateNewWorkflowPhaseStepsForPCFBlueGreen(
      String appId, WorkflowPhase workflowPhase, boolean serviceSetupRequired) {
    if (workflowPhase.isDaemonSet() || workflowPhase.isStatefulSet()) {
      throw new InvalidRequestException("DaemonSet and StatefulSet are not supported with Blue/Green Deployment", USER);
    }

    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    // SETUP
    if (serviceSetupRequired) {
      Map<String, Object> defaultSetupProperties = new HashMap<>();
      defaultSetupProperties.put("blueGreen", true);
      defaultSetupProperties.put("isWorkflowV2", true);
      defaultSetupProperties.put("resizeStrategy", "RESIZE_NEW_FIRST");

      phaseSteps.add(aPhaseStep(PhaseStepType.PCF_SETUP, SETUP)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(StateType.PCF_SETUP.name())
                                      .name(PCF_SETUP)
                                      .properties(defaultSetupProperties)
                                      .build())
                         .build());
    }

    // RESIZE
    Map<String, Object> defaultUpgradeStageContainerProperties = new HashMap<>();
    defaultUpgradeStageContainerProperties.put("instanceUnitType", "PERCENTAGE");
    defaultUpgradeStageContainerProperties.put("instanceCount", 100);
    defaultUpgradeStageContainerProperties.put("downsizeInstanceUnitType", "PERCENTAGE");
    defaultUpgradeStageContainerProperties.put("downsizeInstanceCount", 100);

    phaseSteps.add(aPhaseStep(PhaseStepType.PCF_RESIZE, DEPLOY)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.PCF_RESIZE.name())
                                    .name(PCF_RESIZE)
                                    .properties(defaultUpgradeStageContainerProperties)
                                    .build())
                       .build());

    // Verify
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_STAGING)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    // Swap Routes
    Map<String, Object> defaultRouteUpdateProperties = new HashMap<>();
    defaultRouteUpdateProperties.put("downsizeOldApps", false);
    phaseSteps.add(aPhaseStep(PCF_SWICH_ROUTES, PCF_BG_MAP_ROUTE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.PCF_BG_MAP_ROUTE.name())
                                    .name(PCF_BG_SWAP_ROUTE)
                                    .properties(defaultRouteUpdateProperties)
                                    .build())
                       .build());

    // Wrap up
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForPCF(
      String appId, WorkflowPhase workflowPhase, boolean serviceSetupRequired) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    if (serviceSetupRequired) {
      Map<String, Object> defaultProperties = new HashMap<>();
      defaultProperties.put("blueGreen", false);
      defaultProperties.put("isWorkflowV2", true);
      defaultProperties.put("resizeStrategy", "DOWNSIZE_OLD_FIRST");

      phaseSteps.add(aPhaseStep(PhaseStepType.PCF_SETUP, SETUP)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(StateType.PCF_SETUP.name())
                                      .name(PCF_SETUP)
                                      .properties(defaultProperties)
                                      .build())
                         .build());
    }

    phaseSteps.add(
        aPhaseStep(PhaseStepType.PCF_RESIZE, DEPLOY)
            .addStep(GraphNode.builder().id(generateUuid()).type(StateType.PCF_RESIZE.name()).name(PCF_RESIZE).build())
            .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForHelm(String appId, WorkflowPhase workflowPhase) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    phaseSteps.add(
        aPhaseStep(PhaseStepType.HELM_DEPLOY, DEPLOY_CONTAINERS)
            .addStep(
                GraphNode.builder().id(generateUuid()).type(StateType.HELM_DEPLOY.name()).name(HELM_DEPLOY).build())
            .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForKubernetes(String appId, WorkflowPhase workflowPhase,
      boolean serviceSetupRequired, OrchestrationWorkflowType orchestrationWorkflowType) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    if (serviceSetupRequired) {
      // TODO => (Prashant) check if the RUNTIME is for provisioner
      boolean isGcpInfra;
      InfrastructureDefinition infrastructureDefinition =
          infrastructureDefinitionService.get(appId, workflowPhase.getInfraDefinitionId());
      isGcpInfra = infrastructureDefinition.getInfrastructure() instanceof GoogleKubernetesEngine
          && RUNTIME.equals(((GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure()).getClusterName());
      if (isGcpInfra) {
        phaseSteps.add(aPhaseStep(CLUSTER_SETUP, SETUP_CLUSTER)
                           .addStep(GraphNode.builder()
                                        .id(generateUuid())
                                        .type(GCP_CLUSTER_SETUP.name())
                                        .name("GCP Cluster Setup")
                                        .build())
                           .build());
      }

      Map<String, Object> defaultSetupProperties = new HashMap<>();
      defaultSetupProperties.put("replicationControllerName", "${app.name}-${service.name}-${env.name}");
      defaultSetupProperties.put("resizeStrategy", "RESIZE_NEW_FIRST");
      phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(KUBERNETES_SETUP.name())
                                      .name(KUBERNETES_SERVICE_SETUP)
                                      .properties(defaultSetupProperties)
                                      .build())
                         .build());
    }

    if (!workflowPhase.isDaemonSet() && !workflowPhase.isStatefulSet()) {
      Map<String, Object> properties = new HashMap<>();
      if (BASIC == orchestrationWorkflowType) {
        // Setting instance count always 100 percent
        properties.put("instanceCount", "100");
      }
      phaseSteps.add(aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(KUBERNETES_DEPLOY.name())
                                      .name(UPGRADE_CONTAINERS)
                                      .properties(properties)
                                      .build())
                         .build());
    }
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForKubernetesBlueGreen(
      String appId, WorkflowPhase workflowPhase, boolean serviceSetupRequired) {
    if (workflowPhase.isDaemonSet() || workflowPhase.isStatefulSet()) {
      throw new InvalidRequestException("DaemonSet and StatefulSet are not supported with Blue/Green Deployment", USER);
    }

    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    if (serviceSetupRequired) {
      Map<String, Object> defaultServiceSpec = new HashMap<>();
      defaultServiceSpec.put("serviceType", "ClusterIP");
      defaultServiceSpec.put("port", 80);
      defaultServiceSpec.put("targetPort", 8080);
      defaultServiceSpec.put("protocol", "TCP");

      Map<String, Object> defaultBlueGreenConfig = new HashMap<>();
      defaultBlueGreenConfig.put("primaryService", defaultServiceSpec);
      defaultBlueGreenConfig.put("stageService", defaultServiceSpec);

      Map<String, Object> defaultSetupProperties = new HashMap<>();
      defaultSetupProperties.put("replicationControllerName", "${app.name}-${service.name}-${env.name}");
      defaultSetupProperties.put("blueGreen", true);
      defaultSetupProperties.put("blueGreenConfig", defaultBlueGreenConfig);
      defaultSetupProperties.put("resizeStrategy", "RESIZE_NEW_FIRST");

      phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(KUBERNETES_SETUP.name())
                                      .name(KUBERNETES_SERVICE_SETUP_BLUEGREEN)
                                      .properties(defaultSetupProperties)
                                      .build())
                         .build());
    }

    Map<String, Object> defaultUpgradeStageContainerProperties = new HashMap<>();
    defaultUpgradeStageContainerProperties.put("instanceUnitType", "PERCENTAGE");
    defaultUpgradeStageContainerProperties.put("instanceCount", 100);
    defaultUpgradeStageContainerProperties.put("downsizeInstanceUnitType", "PERCENTAGE");
    defaultUpgradeStageContainerProperties.put("downsizeInstanceCount", 100);

    phaseSteps.add(aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(KUBERNETES_DEPLOY.name())
                                    .name(UPGRADE_CONTAINERS)
                                    .properties(defaultUpgradeStageContainerProperties)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_STAGE_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    Map<String, Object> defaultRouteUpdateProperties = new HashMap<>();
    defaultRouteUpdateProperties.put("service1", PRIMARY_SERVICE_NAME_EXPRESSION);
    defaultRouteUpdateProperties.put("service2", STAGE_SERVICE_NAME_EXPRESSION);
    phaseSteps.add(aPhaseStep(PhaseStepType.ROUTE_UPDATE, ROUTE_UPDATE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(KUBERNETES_SWAP_SERVICE_SELECTORS.name())
                                    .name(KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE)
                                    .properties(defaultRouteUpdateProperties)
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForSSH(
      String appId, WorkflowPhase workflowPhase, OrchestrationWorkflowType orchestrationWorkflowType) {
    // For DC only - for other types it has to be customized
    String computeProviderType;
    boolean attachElbSteps;
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionService.get(appId, workflowPhase.getInfraDefinitionId());
    computeProviderType = infrastructureDefinition.getCloudProviderType().name();
    attachElbSteps = attachElbSteps(infrastructureDefinition.getInfrastructure());

    StateType stateType;
    if (orchestrationWorkflowType == ROLLING) {
      stateType = ROLLING_NODE_SELECT;
    } else {
      stateType = computeProviderType.equals(PHYSICAL_DATA_CENTER.name()) ? DC_NODE_SELECT : AWS_NODE_SELECT;
    }

    if (!asList(ROLLING_NODE_SELECT, DC_NODE_SELECT, AWS_NODE_SELECT).contains(stateType)) {
      throw new InvalidRequestException("Unsupported state type: " + stateType, USER);
    }

    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    final PhaseStepBuilder infrastructurePhaseStepBuilder = aPhaseStep(INFRASTRUCTURE_NODE, INFRASTRUCTURE_NODE_NAME);

    infrastructurePhaseStepBuilder.addStep(GraphNode.builder()
                                               .type(stateType.name())
                                               .name(SELECT_NODE_NAME)
                                               .properties(ImmutableMap.<String, Object>builder()
                                                               .put("specificHosts", false)
                                                               .put("instanceCount", 1)
                                                               .put("excludeSelectedHostsFromFuturePhases", true)
                                                               .build())
                                               .build());

    phaseSteps.add(infrastructurePhaseStepBuilder.build());

    List<GraphNode> disableServiceSteps = commandNodes(commandMap, CommandType.DISABLE);
    List<GraphNode> enableServiceSteps = commandNodes(commandMap, CommandType.ENABLE);

    if (attachElbSteps) {
      disableServiceSteps.add(GraphNode.builder()
                                  .type(ELASTIC_LOAD_BALANCER.name())
                                  .name("Elastic Load Balancer")
                                  .properties(ImmutableMap.<String, Object>builder().put("operation", Disable).build())
                                  .build());
      enableServiceSteps.add(GraphNode.builder()
                                 .type(ELASTIC_LOAD_BALANCER.name())
                                 .name("Elastic Load Balancer")
                                 .properties(ImmutableMap.<String, Object>builder().put("operation", Enable).build())
                                 .build());
    }

    phaseSteps.add(aPhaseStep(PhaseStepType.DISABLE_SERVICE, DISABLE_SERVICE).addAllSteps(disableServiceSteps).build());

    phaseSteps.add(aPhaseStep(PhaseStepType.DEPLOY_SERVICE, DEPLOY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.INSTALL))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.ENABLE_SERVICE, ENABLE_SERVICE).addAllSteps(enableServiceSteps).build());

    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .addAllSteps(commandNodes(commandMap, CommandType.VERIFY))
                       .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).build());
  }

  private boolean attachElbSteps(InfrastructureMapping infrastructureMapping) {
    return (infrastructureMapping instanceof PhysicalInfrastructureMappingBase
               && isNotBlank(((PhysicalInfrastructureMappingBase) infrastructureMapping).getLoadBalancerId()))
        || (infrastructureMapping instanceof AwsInfrastructureMapping
            && isNotBlank(((AwsInfrastructureMapping) infrastructureMapping).getLoadBalancerId()));
  }

  private boolean attachElbSteps(CloudProviderInfrastructure infrastructure) {
    return (infrastructure instanceof PhysicalInfra && isNotBlank(((PhysicalInfra) infrastructure).getLoadBalancerId()))
        || (infrastructure instanceof AwsInstanceInfrastructure
            && isNotBlank(((AwsInstanceInfrastructure) infrastructure).getLoadBalancerId()));
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForPCF(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(PhaseStepType.PCF_RESIZE, DEPLOY)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.PCF_ROLLBACK.name())
                                            .name(PCF_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            // When we rolling back the verification steps
            // the same criteria to run if deployment is needed should be used
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForHelm(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(PhaseStepType.HELM_DEPLOY, DEPLOY_CONTAINERS)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.HELM_ROLLBACK.name())
                                            .name(HELM_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            // When we rolling back the verification steps the same criterie to run if deployment is needed should be
            // used
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForAwsAmi(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(AMI_DEPLOY_AUTOSCALING_GROUP, ROLLBACK_SERVICE)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(AWS_AMI_SERVICE_ROLLBACK.name())
                                            .name(ROLLBACK_AWS_AMI_CLUSTER)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withRollback(true)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  @VisibleForTesting
  WorkflowPhase generateRollbackWorkflowPhaseForSpotinstAlbTrafficShift(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(
            asList(aPhaseStep(PhaseStepType.SPOTINST_LISTENER_UPDATE_ROLLBACK, SPOTINST_LISTENER_UPDATE_ROLLBACK)
                       .addStep(GraphNode.builder()
                                    .type(StateType.SPOTINST_LISTENER_ALB_SHIFT_ROLLBACK.name())
                                    .name(SPOTINST_ALB_SHIFT_LISTENER_UPDATE_ROLLBACK)
                                    .rollback(true)
                                    .build())
                       .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                       .withStatusForRollback(SUCCESS)
                       .withRollback(true)
                       .build(),
                aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                    .withRollback(true)
                    .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                    .withStatusForRollback(SUCCESS)
                    .withRollback(true)
                    .build(),
                aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForSpotinst(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(PhaseStepType.SPOTINST_ROLLBACK, SPOTINST_ROLLBACK)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.SPOTINST_ROLLBACK.name())
                                            .name(SPOTINST_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withRollback(true)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForSpotInstBlueGreen(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(
            asList(aPhaseStep(PhaseStepType.SPOTINST_LISTENER_UPDATE_ROLLBACK, SPOTINST_LISTENER_UPDATE_ROLLBACK)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.SPOTINST_LISTENER_UPDATE_ROLLBACK.name())
                                    .name(SPOTINST_SWAP_ROLLBACK)
                                    .rollback(true)
                                    .build())
                       .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                       .withStatusForRollback(SUCCESS)
                       .withRollback(true)
                       .build(),
                aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                    .withRollback(true)
                    .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                    .withStatusForRollback(SUCCESS)
                    .withRollback(true)
                    .build(),
                aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForAwsAmiBlueGreen(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(AMI_SWITCH_AUTOSCALING_GROUP_ROUTES, ROLLBACK_SERVICE)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(AWS_AMI_ROLLBACK_SWITCH_ROUTES.name())
                                            .name(ROLLBACK_AUTOSCALING_GROUP_ROUTE)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withRollback(true)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForAsgAmiTrafficShiftBlueGreen(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(AMI_SWITCH_AUTOSCALING_GROUP_ROUTES, ROLLBACK_SERVICE)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(ASG_AMI_ROLLBACK_ALB_SHIFT_SWITCH_ROUTES.name())
                                            .name(SPOTINST_ALB_SHIFT_LISTENER_UPDATE_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withRollback(true)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForAwsLambda(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(DEPLOY_AWS_LAMBDA, DEPLOY_SERVICE)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(AWS_LAMBDA_ROLLBACK.name())
                                            .name(ROLLBACK_AWS_LAMBDA)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            // Verificanion is not exactly rollbacking operation. It should be executed if deployment is needed
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForEcs(
      String appId, WorkflowPhase workflowPhase, OrchestrationWorkflowType orchestrationWorkflowType) {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    if (isDaemonSchedulingStrategy(appId, workflowPhase, orchestrationWorkflowType)) {
      // For Daemon ECS workflow, need to add Setup rollback state
      phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(ECS_SERVICE_SETUP_ROLLBACK.name())
                                      .name(ROLLBACK_CONTAINERS)
                                      .rollback(true)
                                      .build())
                         .withPhaseStepNameForRollback(SETUP_CONTAINER)
                         .withStatusForRollback(SUCCESS)
                         .withRollback(true)
                         .build());
    } else {
      phaseSteps.add(aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(ECS_SERVICE_ROLLBACK.name())
                                      .name(ROLLBACK_CONTAINERS)
                                      .rollback(true)
                                      .build())
                         .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                         .withStatusForRollback(SUCCESS)
                         .withRollback(true)
                         .build());
    }

    // Verification
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                       .withStatusForRollback(SUCCESS)
                       .withRollback(true)
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build());

    return rollbackWorkflow(workflowPhase).phaseSteps(phaseSteps).build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForEcsBlueGreenRoute53(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(ECS_UPDATE_ROUTE_53_DNS_WEIGHT, ECS_ROUTE53_DNS_WEIGHTS)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.ECS_ROUTE53_DNS_WEIGHT_UPDATE_ROLLBACK.name())
                                            .name(ROLLBACK_ECS_ROUTE53_DNS_WEIGHTS)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(VERIFY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForEcsBlueGreen(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(ECS_UPDATE_LISTENER_BG, ECS_SWAP_TARGET_GROUPS)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.ECS_LISTENER_UPDATE_ROLLBACK.name())
                                            .name(ECS_SWAP_TARGET_GROUPS_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),

            aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                .addStep(GraphNode.builder()
                             .id(generateUuid())
                             .type(ECS_SERVICE_ROLLBACK.name())
                             .name(ROLLBACK_CONTAINERS)
                             .rollback(true)
                             .build())
                .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            // Verification
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForAwsCodeDeploy(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(DEPLOY_AWSCODEDEPLOY, DEPLOY_SERVICE)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(AWS_CODEDEPLOY_ROLLBACK.name())
                                            .name(ROLLBACK_AWS_CODE_DEPLOY)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            // When we rolling back the verification steps the same criterie to run if deployment is needed should be
            // used
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForSSH(String appId, WorkflowPhase workflowPhase) {
    Service service = serviceResourceService.getWithDetails(appId, workflowPhase.getServiceId());
    Map<CommandType, List<Command>> commandMap = getCommandTypeListMap(service);

    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(appId, workflowPhase.getInfraMappingId());

    List<GraphNode> disableServiceSteps = commandNodes(commandMap, CommandType.DISABLE, true);
    List<GraphNode> enableServiceSteps = commandNodes(commandMap, CommandType.ENABLE, true);

    if (attachElbSteps(infrastructureMapping)) {
      disableServiceSteps.add(GraphNode.builder()
                                  .type(ELASTIC_LOAD_BALANCER.name())
                                  .name("Elastic Load Balancer")
                                  .properties(ImmutableMap.<String, Object>builder().put("operation", Disable).build())
                                  .rollback(true)
                                  .build());
      enableServiceSteps.add(GraphNode.builder()
                                 .type(ELASTIC_LOAD_BALANCER.name())
                                 .name("Elastic Load Balancer")
                                 .properties(ImmutableMap.<String, Object>builder().put("operation", Enable).build())
                                 .rollback(true)
                                 .build());
    }

    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(PhaseStepType.DISABLE_SERVICE, DISABLE_SERVICE)
                               .addAllSteps(disableServiceSteps)
                               .withPhaseStepNameForRollback(ENABLE_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.STOP_SERVICE, STOP_SERVICE)
                .addAllSteps(commandNodes(commandMap, CommandType.STOP, true))
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.DEPLOY_SERVICE, DEPLOY_SERVICE)
                .addAllSteps(commandNodes(commandMap, CommandType.INSTALL, true))
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.ENABLE_SERVICE, ENABLE_SERVICE)
                .addAllSteps(enableServiceSteps)
                .withPhaseStepNameForRollback(DISABLE_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            // When we rolling back the verification steps
            // the same criteria to run if deployment is needed should be used
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .addAllSteps(commandNodes(commandMap, CommandType.VERIFY, true))
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForKubernetes(
      WorkflowPhase workflowPhase, boolean serviceSetupRequired) {
    if (workflowPhase.isDaemonSet() || workflowPhase.isStatefulSet()) {
      return generateRollbackSetupWorkflowPhase(workflowPhase);
    }

    List<PhaseStep> phaseSteps = new ArrayList<>();

    phaseSteps.add(aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(KUBERNETES_DEPLOY_ROLLBACK.name())
                                    .name(ROLLBACK_CONTAINERS)
                                    .rollback(true)
                                    .build())
                       .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                       .withStatusForRollback(SUCCESS)
                       .withRollback(true)
                       .build());
    if (serviceSetupRequired) {
      phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                         .addStep(GraphNode.builder()
                                      .id(generateUuid())
                                      .type(KUBERNETES_SETUP_ROLLBACK.name())
                                      .name(ROLLBACK_KUBERNETES_SETUP)
                                      .rollback(true)
                                      .build())
                         .withPhaseStepNameForRollback(SETUP_CONTAINER)
                         .withStatusForRollback(SUCCESS)
                         .withRollback(true)
                         .build());
    }

    // When we rolling back the verification steps the same criterie to run if deployment is needed should be used
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                       .withStatusForRollback(SUCCESS)
                       .withRollback(true)
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build());

    return rollbackWorkflow(workflowPhase).phaseSteps(phaseSteps).build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForKubernetesBlueGreen(
      WorkflowPhase workflowPhase, boolean serviceSetupRequired) {
    if (workflowPhase.isDaemonSet() || workflowPhase.isStatefulSet()) {
      throw new InvalidRequestException("DaemonSet and StatefulSet are not supported with Blue/Green Deployment", USER);
    }

    Map<String, Object> defaultRouteUpdateProperties = new HashMap<>();
    defaultRouteUpdateProperties.put("service1", PRIMARY_SERVICE_NAME_EXPRESSION);
    defaultRouteUpdateProperties.put("service2", STAGE_SERVICE_NAME_EXPRESSION);

    List<PhaseStep> phaseSteps = new ArrayList<>();

    phaseSteps.add(aPhaseStep(PhaseStepType.ROUTE_UPDATE, ROUTE_UPDATE)
                       .withPhaseStepNameForRollback(ROUTE_UPDATE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(KUBERNETES_SWAP_SERVICE_SELECTORS.name())
                                    .name(KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE)
                                    .properties(defaultRouteUpdateProperties)
                                    .rollback(true)
                                    .build())
                       .withRollback(true)
                       .build());
    phaseSteps.add(aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                       .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                       .withStatusForRollback(SUCCESS)
                       .withRollback(true)
                       .build());
    if (serviceSetupRequired) {
      phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                         .withPhaseStepNameForRollback(SETUP_CONTAINER)
                         .withStatusForRollback(SUCCESS)
                         .withRollback(true)
                         .build());
    }

    // When we rolling back the verification steps the same criterie to run if deployment is needed should be used
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                       .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                       .withStatusForRollback(SUCCESS)
                       .withRollback(true)
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build());
    return rollbackWorkflow(workflowPhase).phaseSteps(phaseSteps).build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForAzureVMSS(
      WorkflowPhase workflowPhase, OrchestrationWorkflowType orchestrationWorkflowType) {
    PhaseStepType azureVMSSPhaseStepRollback = (BLUE_GREEN == orchestrationWorkflowType)
        ? PhaseStepType.AZURE_VMSS_SWITCH_ROLLBACK
        : PhaseStepType.AZURE_VMSS_ROLLBACK;

    String azureVMSSStateType = (BLUE_GREEN == orchestrationWorkflowType)
        ? StateType.AZURE_VMSS_SWITCH_ROUTES_ROLLBACK.name()
        : StateType.AZURE_VMSS_ROLLBACK.name();

    String rollbackName =
        (BLUE_GREEN == orchestrationWorkflowType) ? AZURE_VMSS_SWITCH_ROUTES_ROLLBACK : AZURE_VMSS_ROLLBACK;

    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(azureVMSSPhaseStepRollback, rollbackName)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(azureVMSSStateType)
                                            .name(rollbackName)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withRollback(true)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForAzureWebApp(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .phaseSteps(asList(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_ROLLBACK, AZURE_WEBAPP_SLOT_ROLLBACK)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.AZURE_WEBAPP_SLOT_ROLLBACK.name())
                                            .name(AZURE_WEBAPP_SLOT_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withRollback(true)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  public WorkflowPhase generateRollbackSetupWorkflowPhase(WorkflowPhase workflowPhase) {
    return rollbackWorkflow(workflowPhase)
        .daemonSet(workflowPhase.isDaemonSet())
        .statefulSet(workflowPhase.isStatefulSet())
        .phaseSteps(asList(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(KUBERNETES_SETUP_ROLLBACK.name())
                                            .name(ROLLBACK_KUBERNETES_SETUP)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(SETUP_CONTAINER)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            // When we rolling back the verification steps the same criterie to run if deployment is needed should be
            // used
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(SETUP_CONTAINER)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, WRAP_UP).withRollback(true).build()))
        .build();
  }

  private Map<CommandType, List<Command>> getCommandTypeListMap(Service service) {
    Map<CommandType, List<Command>> commandMap = new HashMap<>();
    List<ServiceCommand> serviceCommands = service.getServiceCommands();
    if (serviceCommands == null) {
      return commandMap;
    }
    for (ServiceCommand sc : serviceCommands) {
      if (sc.getCommand() == null || sc.getCommand().getCommandType() == null) {
        continue;
      }
      commandMap.computeIfAbsent(sc.getCommand().getCommandType(), k -> new ArrayList<>()).add(sc.getCommand());
    }
    return commandMap;
  }

  private List<GraphNode> commandNodes(Map<CommandType, List<Command>> commandMap, CommandType commandType) {
    return commandNodes(commandMap, commandType, false);
  }

  private List<GraphNode> commandNodes(
      Map<CommandType, List<Command>> commandMap, CommandType commandType, boolean rollback) {
    List<GraphNode> nodes = new ArrayList<>();

    List<Command> commands = commandMap.get(commandType);
    if (commands == null) {
      return nodes;
    }

    for (Command command : commands) {
      nodes.add(GraphNode.builder()
                    .id(generateUuid())
                    .type(COMMAND.name())
                    .name(command.getName())
                    .properties(ImmutableMap.<String, Object>builder().put("commandName", command.getName()).build())
                    .rollback(rollback)
                    .build());
    }
    return nodes;
  }

  /***
   * Populates the workflow level data to Phase. It Validates the service and inframapping for Basics and Multi
   * Service deployment. Resets Node selection if environment or inframapping changed.
   * @param orchestrationWorkflow
   * @param templateExpressions
   * @param appId
   * @param serviceId
   * @param infraId
   * @param envChanged
   * @param infraChanged
   * @param migration
   * @return OrchestrationWorkflow
   */
  public OrchestrationWorkflow propagateWorkflowDataToPhases(OrchestrationWorkflow orchestrationWorkflow,
      List<TemplateExpression> templateExpressions, String appId, String serviceId, String infraId, boolean envChanged,
      boolean infraChanged, boolean migration) {
    if (orchestrationWorkflow != null) {
      OrchestrationWorkflowType orchestrationWorkflowType = orchestrationWorkflow.getOrchestrationWorkflowType();
      if (orchestrationWorkflowType == BASIC || orchestrationWorkflowType == ROLLING
          || orchestrationWorkflowType == BLUE_GREEN) {
        handleBasicWorkflow((CanaryOrchestrationWorkflow) orchestrationWorkflow, templateExpressions, appId, serviceId,
            infraId, envChanged, infraChanged);
      } else if (orchestrationWorkflowType == MULTI_SERVICE || orchestrationWorkflowType == CANARY) {
        handleCanaryOrMultiServiceWorkflow(
            orchestrationWorkflow, templateExpressions, appId, envChanged, infraChanged, migration);
      }
    }
    return orchestrationWorkflow;
  }

  public void handleBasicWorkflow(CanaryOrchestrationWorkflow canaryOrchestrationWorkflow,
      List<TemplateExpression> templateExpressions, String appId, String serviceId, String infraId, boolean envChanged,
      boolean infraChanged) {
    TemplateExpression envExpression =
        WorkflowServiceTemplateHelper.getTemplateExpression(templateExpressions, "envId");
    if (envExpression != null) {
      canaryOrchestrationWorkflow.addToUserVariables(asList(envExpression));
    }
    if (canaryOrchestrationWorkflow.getWorkflowPhases() != null) {
      for (WorkflowPhase phase : canaryOrchestrationWorkflow.getWorkflowPhases()) {
        WorkflowServiceTemplateHelper.setTemplateExpresssionsToPhase(templateExpressions, phase);
        if (!phase.checkServiceTemplatized()) {
          validateServiceCompatibility(appId, serviceId, phase.getServiceId());
        }
        if (serviceId != null) {
          phase.setServiceId(serviceId);
        }
        setInfraDefinitionDetails(appId, infraId, phase, envChanged, infraChanged);
        if (infraChanged || envChanged) {
          resetNodeSelection(phase);
        }
      }
    }
    Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap = canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap();
    if (rollbackWorkflowPhaseIdMap != null) {
      rollbackWorkflowPhaseIdMap.values().forEach(phase -> {
        if (serviceId != null) {
          phase.setServiceId(serviceId);
        }
        setInfraDefinitionDetails(appId, infraId, phase, envChanged, infraChanged);
      });
    }
  }

  private void setInfraDefinitionDetails(
      String appId, String infraDefinitionId, WorkflowPhase phase, boolean envChanged, boolean infraChanged) {
    if (infraDefinitionId != null) {
      if (!infraDefinitionId.equals(phase.getInfraDefinitionId())) {
        phase.setInfraDefinitionId(infraDefinitionId);
        InfrastructureDefinition infrastructureDefinition =
            infrastructureDefinitionService.get(appId, phase.getInfraDefinitionId());
        notNullCheck("Infra Structure Does Not exist", infrastructureDefinition, USER);
        phase.setComputeProviderId(infrastructureDefinition.getInfrastructure().getCloudProviderId());
        phase.setDeploymentType(infrastructureDefinition.getDeploymentType());
        resetNodeSelection(phase);
      }
    } else if (envChanged && !infraChanged) {
      unsetInfraDefinitionsDetails(phase);
    }
  }

  private void handleCanaryOrMultiServiceWorkflow(OrchestrationWorkflow orchestrationWorkflow,
      List<TemplateExpression> templateExpressions, String appId, boolean envChanged, boolean infraChanged,
      boolean migration) {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
    canaryOrchestrationWorkflow.addToUserVariables(templateExpressions);
    // If envId changed nullify the infraMapping Ids
    if (canaryOrchestrationWorkflow.getWorkflowPhases() != null) {
      for (WorkflowPhase phase : canaryOrchestrationWorkflow.getWorkflowPhases()) {
        if (infraChanged) {
          resetNodeSelection(phase);
        }
        // If environment templatized, then templatize infra automatically
        List<TemplateExpression> phaseTemplateExpressions = phase.getTemplateExpressions();
        if (phaseTemplateExpressions == null) {
          phaseTemplateExpressions = new ArrayList<>();
        }

        boolean envTemplatized = WorkflowServiceTemplateHelper.isEnvironmentTemplatized(templateExpressions);

        if (envTemplatized && !migration) {
          if (!WorkflowServiceTemplateHelper.isInfraDefinitionTemplatized(phaseTemplateExpressions)) {
            Service service = serviceResourceService.get(appId, phase.getServiceId(), false);
            notNullCheck("Service", service, USER);
            WorkflowServiceTemplateHelper.templatizeInfraDefinition(
                orchestrationWorkflow, phase, phaseTemplateExpressions, service);
          }
        }

        phase.setTemplateExpressions(phaseTemplateExpressions);

        // CDS-36932
        // When the environment change, we clear the infrastructure definitions to force the user
        // to review every phase and manually fix it. That behavior is a feature and not a bug.
        if (envChanged) {
          unsetInfraMappingDetails(phase);
          unsetInfraDefinitionsDetails(phase);
          resetNodeSelection(phase);
        }
      }
    }
    Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap = canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap();
    if (rollbackWorkflowPhaseIdMap != null) {
      rollbackWorkflowPhaseIdMap.values().forEach(phase -> {
        if (envChanged) {
          unsetInfraMappingDetails(phase);
          unsetInfraDefinitionsDetails(phase);
          resetNodeSelection(phase);
        }
        if (infraChanged) {
          resetNodeSelection(phase);
        }
      });
    }
  }

  /**
   * Validates service compatibility
   *
   * @param appId
   * @param serviceId
   * @param oldServiceId
   */
  public void validateServiceCompatibility(String appId, String serviceId, String oldServiceId) {
    if (serviceId == null || oldServiceId == null || serviceId.equals(oldServiceId)) {
      return;
    }
    Service oldService = null;
    if (EmptyPredicate.isNotEmpty(oldServiceId)) {
      oldService = serviceResourceService.get(appId, oldServiceId, false);
    }
    if (oldService == null) {
      // As service has been deleted, compatibility check does not make sense here
      return;
    }

    Service newService = serviceResourceService.get(appId, serviceId, false);
    notNullCheck("service", newService, USER);
    if (oldService.getArtifactType() != null && oldService.getArtifactType() != newService.getArtifactType()) {
      throw new InvalidRequestException(
          "Service [" + newService.getName() + "] is not compatible with the service [" + oldService.getName() + "]",
          USER);
    }

    if (oldService.isK8sV2() != newService.isK8sV2()) {
      throw new InvalidRequestException("Service [" + newService.getName() + "] is not compatible with the service ["
              + oldService.getName() + "] due to different kubernetes version",
          USER);
    }
  }

  /**
   * Resets node selection if environment of infra changed
   *
   * @param phase
   */
  public void resetNodeSelection(WorkflowPhase phase) {
    // Update the node selection
    if (phase.getPhaseSteps() == null) {
      return;
    }
    phase.getPhaseSteps()
        .stream()
        .filter(phaseStep -> phaseStep.getPhaseStepType() == INFRASTRUCTURE_NODE)
        .map(PhaseStep::getSteps)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(step
            -> step.getType().equals(DC_NODE_SELECT.name()) || step.getType().equals(AWS_NODE_SELECT.name())
                || step.getType().equals(AZURE_NODE_SELECT.name()) || step.getType().equals(ROLLING_NODE_SELECT.name()))
        .map(GraphNode::getProperties)
        .filter(properties -> (Boolean) properties.get("specificHosts"))
        .forEach(properties -> {
          properties.put("specificHosts", Boolean.FALSE);
          properties.remove("hostNames");
          properties.put("instanceCount", 1);
          properties.put("instanceUnitType", InstanceUnitType.COUNT);
        });
  }

  public List<Service> getResolvedServices(Workflow workflow, Map<String, String> workflowVariables) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow.isServiceTemplatized()) {
      List<Variable> userVariables = orchestrationWorkflow.getUserVariables();
      List<String> serviceNames = new ArrayList<>();
      if (isNotEmpty(userVariables)) {
        serviceNames = getEntityNames(userVariables, SERVICE);
      }
      List<String> serviceIds = getTemplatizedIds(workflowVariables, serviceNames);
      List<String> templatizedServiceIds = orchestrationWorkflow.getTemplatizedServiceIds();
      List<String> workflowServiceIds = orchestrationWorkflow.getServiceIds();
      if (workflowServiceIds != null) {
        workflowServiceIds.stream()
            .filter(serviceId -> !templatizedServiceIds.contains(serviceId))
            .forEach(serviceIds::add);
      }
      return serviceResourceService.fetchServicesByUuids(workflow.getAppId(), serviceIds);
    } else {
      return workflow.getServices();
    }
  }

  public List<String> getResolvedServiceIds(Workflow workflow, Map<String, String> workflowVariables) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow.isServiceTemplatized()) {
      List<Variable> userVariables = orchestrationWorkflow.getUserVariables();
      List<String> serviceNames = new ArrayList<>();
      if (isNotEmpty(userVariables)) {
        serviceNames = getEntityNames(userVariables, SERVICE);
      }
      List<String> serviceIds = getTemplatizedIds(workflowVariables, serviceNames);
      List<String> templatizedServiceIds = orchestrationWorkflow.getTemplatizedServiceIds();
      List<String> workflowServiceIds = orchestrationWorkflow.getServiceIds();
      if (workflowServiceIds != null) {
        workflowServiceIds.stream()
            .filter(serviceId -> !templatizedServiceIds.contains(serviceId))
            .forEach(serviceIds::add);
      }
      return serviceIds;
    } else {
      return workflow.getServices().stream().map(Service::getUuid).collect(toList());
    }
  }

  /**
   * Retrieve resolved service id from specific Workflow phase
   *
   * @param workflowPhase  specific workflow phase
   * @param workflowVariables  workflow variables
   *
   * @return serviceId
   */
  public String getResolvedServiceIdFromPhase(WorkflowPhase workflowPhase, Map<String, String> workflowVariables) {
    List<TemplateExpression> templateExpressions = workflowPhase.getTemplateExpressions();
    if (workflowPhase.isSrvTemplatised()) {
      if (templateExpressions != null && workflowVariables != null) {
        List<String> phaseExpressions =
            templateExpressions.stream()
                .filter(templateExpression -> templateExpression.getFieldName().equals("serviceId"))
                .map(TemplateExpression::getExpression)
                .collect(toList());

        return phaseExpressions.stream()
            .map(phaseExpression -> getWorkflowVariableValue(workflowVariables, phaseExpression))
            .findFirst()
            .orElse(null);
      } else {
        return null;
      }
    } else {
      return workflowPhase.getServiceId();
    }
  }

  /**
   * Retrieve resolved infra definition id from specific Workflow phase
   *
   * @param workflowPhase  specific workflow phase
   * @param workflowVariables  workflow variables
   *
   * @return infraDefinitionId
   */
  public String getResolvedInfraDefinitionIdFromPhase(
      WorkflowPhase workflowPhase, Map<String, String> workflowVariables) {
    List<TemplateExpression> templateExpressions = workflowPhase.getTemplateExpressions();
    if (workflowPhase.isInfraTemplatised()) {
      if (templateExpressions != null && workflowVariables != null) {
        List<String> phaseExpressions =
            templateExpressions.stream()
                .filter(templateExpression -> templateExpression.getFieldName().equals("infraDefinitionId"))
                .map(TemplateExpression::getExpression)
                .collect(toList());

        return phaseExpressions.stream()
            .map(phaseExpression -> getWorkflowVariableValue(workflowVariables, phaseExpression))
            .findFirst()
            .orElse(null);
      } else {
        return null;
      }
    } else {
      return workflowPhase.getInfraDefinitionId();
    }
  }

  /**
   * Retrieve Workflow execution variable which key match with workflow template expression
   *
   * @param workflowVariables  workflow variables
   * @param templateExpression  template expression
   *
   * @return workflowVariableValue
   */
  public String getWorkflowVariableValue(Map<String, String> workflowVariables, String templateExpression) {
    String expression = getName(templateExpression);
    for (Entry<String, String> entry : workflowVariables.entrySet()) {
      String variableName = entry.getKey();
      if (expression.equals(variableName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private List<String> getTemplatizedIds(Map<String, String> workflowVariables, List<String> entityNames) {
    List<String> entityIds = new ArrayList<>();
    if (workflowVariables != null) {
      for (Entry<String, String> entry : workflowVariables.entrySet()) {
        String variableName = entry.getKey();
        if (entityNames.contains(variableName)) {
          entityIds.add(workflowVariables.get(variableName));
        }
      }
    }
    return entityIds.stream().distinct().collect(toList());
  }

  public List<InfrastructureMapping> getResolvedInfraMappings(
      Workflow workflow, Map<String, String> workflowVariables) {
    if (workflow == null || workflow.getOrchestrationWorkflow() == null) {
      return new ArrayList<>();
    }
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow.isInfraMappingTemplatized()) {
      return resolvedTemplateInfraMappings(workflow, workflowVariables);
    }
    return infrastructureMappingService.getInfraStructureMappingsByUuids(
        workflow.getAppId(), orchestrationWorkflow.getInfraMappingIds());
  }

  public List<InfrastructureDefinition> getResolvedInfraDefinitions(
      Workflow workflow, Map<String, String> workflowVariables, String envId) {
    if (workflow == null || workflow.getOrchestrationWorkflow() == null) {
      return new ArrayList<>();
    }
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow.isInfraDefinitionTemplatized()) {
      return resolvedTemplateInfraDefinitions(workflow, workflowVariables, envId);
    }
    return infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
        workflow.getAppId(), orchestrationWorkflow.getInfraDefinitionIds());
  }

  public List<String> getResolvedInfraMappingIds(Workflow workflow, Map<String, String> workflowVariables) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow.isInfraMappingTemplatized()) {
      return resolveInfraMappingIds(workflow, workflowVariables);
    } else {
      return orchestrationWorkflow.getInfraMappingIds();
    }
  }

  public List<String> getResolvedInfraDefinitionIds(
      Workflow workflow, Map<String, String> workflowVariables, String resolveEnvId) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow.isInfraDefinitionTemplatized()) {
      return resolveInfraDefinitionIds(workflow, workflowVariables, resolveEnvId);
    } else {
      return orchestrationWorkflow.getInfraDefinitionIds();
    }
  }

  private List<InfrastructureMapping> resolvedTemplateInfraMappings(
      Workflow workflow, Map<String, String> workflowVariables) {
    List<String> infraMappingIds = resolveInfraMappingIds(workflow, workflowVariables);
    return infrastructureMappingService.getInfraStructureMappingsByUuids(workflow.getAppId(), infraMappingIds);
  }

  private List<InfrastructureDefinition> resolvedTemplateInfraDefinitions(
      Workflow workflow, Map<String, String> workflowVariables, String envId) {
    List<String> infrDefinitionIds = resolveInfraDefinitionIds(workflow, workflowVariables, envId);
    return infrastructureDefinitionService.getInfraStructureDefinitionByUuids(workflow.getAppId(), infrDefinitionIds);
  }

  private List<String> resolveInfraMappingIds(Workflow workflow, Map<String, String> workflowVariables) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    List<Variable> userVariables = orchestrationWorkflow.getUserVariables();
    List<String> infraMappingNames = new ArrayList<>();
    if (userVariables != null) {
      infraMappingNames = getEntityNames(userVariables, INFRASTRUCTURE_MAPPING);
    }
    List<String> infraMappingIds = getTemplatizedIds(workflowVariables, infraMappingNames);
    List<String> templatizedInfraMappingIds = orchestrationWorkflow.getTemplatizedInfraMappingIds();
    List<String> workflowInfraMappingIds = orchestrationWorkflow.getInfraMappingIds();
    if (workflowInfraMappingIds != null) {
      workflowInfraMappingIds.stream()
          .filter(infraMappingId -> !templatizedInfraMappingIds.contains(infraMappingId))
          .forEach(infraMappingIds::add);
    }
    return infraMappingIds;
  }

  private List<String> resolveInfraDefinitionIds(
      Workflow workflow, Map<String, String> workflowVariables, String resolveEnvId) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    List<Variable> userVariables = orchestrationWorkflow.getUserVariables();
    List<String> infraDefinitionNames = new ArrayList<>();
    if (userVariables != null) {
      infraDefinitionNames = getEntityNames(userVariables, INFRASTRUCTURE_DEFINITION);
    }
    List<String> infraDefinitionIdsOrNames = getTemplatizedIds(workflowVariables, infraDefinitionNames);
    List<String> templatizedInfraDefinitionIds = orchestrationWorkflow.getTemplatizedInfraDefinitionIds();
    List<String> workflowDefinitionIds = orchestrationWorkflow.getInfraDefinitionIds();
    if (workflowDefinitionIds != null) {
      workflowDefinitionIds.stream()
          .filter(infraDefinitionId -> !templatizedInfraDefinitionIds.contains(infraDefinitionId))
          .forEach(infraDefinitionIdsOrNames::add);
    }
    List<String> infraDefinitionIds = new ArrayList<>();
    infraDefinitionIdsOrNames.forEach(infraIdOrName -> {
      if (ExpressionEvaluator.containsVariablePattern(infraIdOrName)) {
        return;
      }
      InfrastructureDefinition infraDef =
          infrastructureDefinitionService.getInfraDefById(workflow.getAccountId(), infraIdOrName);
      if (infraDef == null) {
        infraDef = infrastructureDefinitionService.getInfraByName(workflow.getAccountId(), infraIdOrName, resolveEnvId);
      }
      notNullCheck("Infra definition " + infraIdOrName + " is invalid", infraDef);
      infraDefinitionIds.add(infraDef.getUuid());
    });
    return infraDefinitionIds;
  }

  private List<String> getEntityNames(List<Variable> userVariables, EntityType entityType) {
    return userVariables.stream()
        .filter(variable -> entityType == variable.obtainEntityType())
        .map(Variable::getName)
        .collect(toList());
  }

  public void unsetInfraMappingDetails(WorkflowPhase phase) {
    phase.setComputeProviderId(null);
    phase.setInfraMappingId(null);
    phase.setInfraMappingName(null);
  }

  public void unsetInfraDefinitionsDetails(WorkflowPhase phase) {
    phase.setComputeProviderId(null);
    phase.setInfraDefinitionId(null);
    phase.setInfraDefinitionName(null);
  }

  public boolean isExecutionForK8sV2Service(WorkflowExecution workflowExecution) {
    if (isNotEmpty(workflowExecution.getServiceIds())) {
      return isK8sV2Service(workflowExecution.getAppId(), workflowExecution.getServiceIds().get(0));
    }
    return false;
  }

  public boolean isOrchestrationWorkflowForK8sV2Service(
      String appId, CanaryOrchestrationWorkflow orchestrationWorkflow) {
    if (isNotEmpty(orchestrationWorkflow.getWorkflowPhases())) {
      WorkflowPhase firstPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
      if (isNotEmpty(firstPhase.getPhaseSteps())) {
        PhaseStep firstPhaseStep = firstPhase.getPhaseSteps().get(0);
        return firstPhaseStep.getPhaseStepType() == K8S_PHASE_STEP;
      }
    }

    if (isNotEmpty(orchestrationWorkflow.getServiceIds())) {
      return isK8sV2Service(appId, orchestrationWorkflow.getServiceIds().get(0));
    }
    return false;
  }

  public boolean isK8sV2Service(String appId, String serviceId) {
    Service service = serviceResourceService.get(appId, serviceId, false);
    return service != null && service.isK8sV2();
  }

  public static void checkWorkflowVariablesOverrides(PipelineStageElement stageElement, List<Variable> variables,
      Map<String, String> workflowStepVariables, Map<String, String> pipelineVariables) {
    if (isEmpty(variables) || stageElement.checkDisableAssertion()) {
      return;
    }

    List<Variable> requiredVariables =
        variables.stream().filter(variable -> variable.isMandatory() && !variable.isFixed()).collect(toList());
    if (isEmpty(requiredVariables)) {
      return;
    }

    List<String> runtimeVars = stageElement.getRuntimeInputsConfig() != null
        ? stageElement.getRuntimeInputsConfig().getRuntimeInputVariables()
        : new ArrayList<>();
    for (Variable variable : requiredVariables) {
      boolean isEntity = variable.obtainEntityType() != null;
      String workflowVariableValue = extractMapValue(workflowStepVariables, variable.getName());
      String finalValue;
      boolean isRuntimeVar = isNotEmpty(runtimeVars) && runtimeVars.contains(variable.getName());
      if (isEmpty(workflowVariableValue)) {
        finalValue = extractMapValue(pipelineVariables, variable.getName());
      } else {
        // Non entity variables can contain expressions like `${workflow.variables.var1}`. If entity variables contain
        // such a pattern, we throw an error.
        if (ExpressionEvaluator.matchesVariablePattern(workflowVariableValue) && !workflowVariableValue.contains(".")) {
          String pipelineVariableName = getName(workflowVariableValue);
          finalValue = extractMapValue(pipelineVariables, pipelineVariableName);
        } else {
          finalValue = workflowVariableValue;
        }
      }

      String prefix = isEntity ? "Templatized" : "Required";
      if (isEmpty(finalValue) && !isRuntimeVar) {
        throw new InvalidRequestException(
            format("%s variable %s is not set for stage %s", prefix, variable.getName(), stageElement.getName()));
      } else if (ExpressionEvaluator.matchesVariablePattern(finalValue) && (isEntity || !finalValue.contains("."))) {
        if (!(INFRASTRUCTURE_DEFINITION.equals(variable.obtainEntityType()) && finalValue.contains("."))) {
          throw new InvalidRequestException(format("%s variable %s for stage %s cannot be left as an expression",
              prefix, variable.getName(), stageElement.getName()));
        }
      }
    }
  }

  private static String extractMapValue(Map<String, String> map, String key) {
    return map == null ? null : map.getOrDefault(key, null);
  }

  public static Map<String, String> overrideWorkflowVariables(
      List<Variable> variables, Map<String, String> workflowStepVariables, Map<String, String> pipelineVariables) {
    Map<String, String> resolvedWorkflowVariables = new LinkedHashMap<>();
    if (isEmpty(variables)) {
      return new HashMap<>();
    }

    for (Variable variable : variables) {
      String workflowVariableValue = extractMapValue(workflowStepVariables, variable.getName());
      String finalValue;
      if (isEmpty(workflowVariableValue)) {
        finalValue = extractMapValue(pipelineVariables, variable.getName());
      } else {
        // Non entity variables can contain expressions like `${workflow.variables.var1}`. If entity variables contain
        // such a pattern, we throw an error.
        if (ExpressionEvaluator.matchesVariablePattern(workflowVariableValue) && !workflowVariableValue.contains(".")) {
          String pipelineVariableName = getName(workflowVariableValue);
          finalValue = extractMapValue(pipelineVariables, pipelineVariableName);
        } else {
          finalValue = workflowVariableValue;
        }
      }
      if (finalValue == null) {
        finalValue = workflowVariableValue;
      }
      resolvedWorkflowVariables.put(variable.getName(), finalValue);
    }
    return resolvedWorkflowVariables;
  }

  public void generateNewWorkflowPhaseSteps(String appId, WorkflowPhase workflowPhase, boolean serviceRepeat,
      OrchestrationWorkflowType orchestrationWorkflowType, WorkflowCreationFlags creationFlags) {
    DeploymentType deploymentType = workflowPhase.getDeploymentType();
    String accountId = appService.getAccountIdByAppId(appId);
    boolean isDynamicInfrastructure;
    isDynamicInfrastructure =
        infrastructureDefinitionService.isDynamicInfrastructure(appId, workflowPhase.getInfraDefinitionId());

    if (deploymentType == ECS) {
      if (orchestrationWorkflowType == OrchestrationWorkflowType.BLUE_GREEN) {
        if (creationFlags != null && creationFlags.isEcsBgDnsType()) {
          generateNewWorkflowPhaseStepsForECSBlueGreenRoute53(
              appId, workflowPhase, !serviceRepeat, isDynamicInfrastructure);
        } else {
          generateNewWorkflowPhaseStepsForECSBlueGreen(appId, workflowPhase, !serviceRepeat, isDynamicInfrastructure);
        }
      } else {
        generateNewWorkflowPhaseStepsForECS(appId, workflowPhase, !serviceRepeat, orchestrationWorkflowType);
      }
    } else if (deploymentType == KUBERNETES) {
      if (orchestrationWorkflowType == OrchestrationWorkflowType.BLUE_GREEN) {
        generateNewWorkflowPhaseStepsForKubernetesBlueGreen(appId, workflowPhase, !serviceRepeat);
      } else {
        generateNewWorkflowPhaseStepsForKubernetes(appId, workflowPhase, !serviceRepeat, orchestrationWorkflowType);
      }
    } else if (deploymentType == HELM) {
      generateNewWorkflowPhaseStepsForHelm(appId, workflowPhase);
    } else if (deploymentType == AWS_CODEDEPLOY) {
      generateNewWorkflowPhaseStepsForAWSCodeDeploy(appId, workflowPhase);
    } else if (deploymentType == DeploymentType.AWS_LAMBDA) {
      generateNewWorkflowPhaseStepsForAWSLambda(appId, workflowPhase);
    } else if (deploymentType == AMI) {
      if (isSpotInstTypeInfra(workflowPhase.getInfraDefinitionId(), appId)) {
        if (BLUE_GREEN == orchestrationWorkflowType) {
          generateNewWfPhaseStepsForSpotinstBg(creationFlags, appId, workflowPhase, serviceRepeat);
        } else {
          generateNewWorkflowPhaseStepsForSpotinst(appId, workflowPhase, !serviceRepeat);
        }
      } else {
        if (BLUE_GREEN == orchestrationWorkflowType) {
          generateNewWfPhaseStepsForAwsAmiBlueGreen(
              creationFlags, appId, workflowPhase, serviceRepeat, isDynamicInfrastructure);
        } else {
          generateNewWorkflowPhaseStepsForAWSAmi(
              appId, workflowPhase, !serviceRepeat, isDynamicInfrastructure, orchestrationWorkflowType);
        }
      }
    } else if (deploymentType == PCF) {
      if (orchestrationWorkflowType == OrchestrationWorkflowType.BLUE_GREEN) {
        generateNewWorkflowPhaseStepsForPCFBlueGreen(appId, workflowPhase, !serviceRepeat);
      } else {
        generateNewWorkflowPhaseStepsForPCF(appId, workflowPhase, !serviceRepeat);
      }
    } else if (deploymentType == CUSTOM) {
      generateNewWorkflowPhaseStepsForCustomDeploymentType(workflowPhase);
    } else if (deploymentType == AZURE_VMSS) {
      generateNewWorkflowPhaseStepsForAzureVMSS(
          appId, accountId, workflowPhase, orchestrationWorkflowType, !serviceRepeat);
    } else if (deploymentType == AZURE_WEBAPP) {
      generateNewWorkflowPhaseStepsForAzureWebApp(
          appId, accountId, workflowPhase, orchestrationWorkflowType, isDynamicInfrastructure, !serviceRepeat);
    } else {
      generateNewWorkflowPhaseStepsForSSH(appId, workflowPhase, orchestrationWorkflowType);
    }
  }

  private void generateNewWfPhaseStepsForSpotinstBg(
      WorkflowCreationFlags creationFlags, String appId, WorkflowPhase workflowPhase, boolean serviceRepeat) {
    if (isAlbTrafficShiftType(creationFlags)) {
      generateNewWorkflowPhaseStepsForSpotinstAlbTrafficShift(appId, workflowPhase);
    } else {
      generateNewWorkflowPhaseStepsForSpotInstBlueGreen(appId, workflowPhase, !serviceRepeat);
    }
  }

  private void generateNewWfPhaseStepsForAwsAmiBlueGreen(WorkflowCreationFlags creationFlags, String appId,
      WorkflowPhase workflowPhase, boolean serviceRepeat, boolean isDynamicInfrastructure) {
    if (isAsgAmiAlbTrafficShiftType(creationFlags)) {
      generateNewWorkflowPhaseStepsForAsgAmiAlbTrafficShiftBlueGreen(appId, workflowPhase);
    } else {
      generateNewWorkflowPhaseStepsForAWSAmiBlueGreen(appId, workflowPhase, !serviceRepeat, isDynamicInfrastructure);
    }
  }

  private boolean isAsgAmiAlbTrafficShiftType(WorkflowCreationFlags flags) {
    return flags != null && flags.isAwsTrafficShiftAlbType();
  }

  private boolean isAlbTrafficShiftType(WorkflowCreationFlags flags) {
    return flags != null && flags.isAwsTrafficShiftAlbType();
  }

  public WorkflowPhase generateRollbackWorkflowPhase(String appId, WorkflowPhase workflowPhase,
      boolean serviceSetupRequired, OrchestrationWorkflowType orchestrationWorkflowType,
      WorkflowCreationFlags creationFlags) {
    DeploymentType deploymentType = workflowPhase.getDeploymentType();
    if (deploymentType == ECS) {
      if (orchestrationWorkflowType == OrchestrationWorkflowType.BLUE_GREEN) {
        if (creationFlags != null && creationFlags.isEcsBgDnsType()) {
          return generateRollbackWorkflowPhaseForEcsBlueGreenRoute53(workflowPhase);
        } else {
          return generateRollbackWorkflowPhaseForEcsBlueGreen(workflowPhase);
        }
      } else {
        return generateRollbackWorkflowPhaseForEcs(appId, workflowPhase, orchestrationWorkflowType);
      }
    } else if (deploymentType == KUBERNETES) {
      if (orchestrationWorkflowType == OrchestrationWorkflowType.BLUE_GREEN) {
        return generateRollbackWorkflowPhaseForKubernetesBlueGreen(workflowPhase, serviceSetupRequired);
      } else {
        return generateRollbackWorkflowPhaseForKubernetes(workflowPhase, serviceSetupRequired);
      }
    } else if (deploymentType == AWS_CODEDEPLOY) {
      return generateRollbackWorkflowPhaseForAwsCodeDeploy(workflowPhase);
    } else if (deploymentType == DeploymentType.AWS_LAMBDA) {
      return generateRollbackWorkflowPhaseForAwsLambda(workflowPhase);
    } else if (deploymentType == AMI) {
      if (isSpotInstTypeInfra(workflowPhase.getInfraDefinitionId(), appId)) {
        if (BLUE_GREEN == orchestrationWorkflowType) {
          return generateRollbackBgPhaseForSpotinstBg(workflowPhase, creationFlags);
        } else {
          return generateRollbackWorkflowPhaseForSpotinst(workflowPhase);
        }
      } else {
        if (BLUE_GREEN == orchestrationWorkflowType) {
          return generateRollbackWfPhaseForAwsAmiBlueGreen(workflowPhase, creationFlags);
        } else {
          return generateRollbackWorkflowPhaseForAwsAmi(workflowPhase);
        }
      }
    } else if (deploymentType == HELM) {
      return generateRollbackWorkflowPhaseForHelm(workflowPhase);
    } else if (deploymentType == PCF) {
      if (orchestrationWorkflowType == OrchestrationWorkflowType.BLUE_GREEN) {
        return generateRollbackWorkflowPhaseForPCFBlueGreen(workflowPhase);
      } else {
        return generateRollbackWorkflowPhaseForPCF(workflowPhase);
      }
    } else if (deploymentType == AZURE_VMSS) {
      return generateRollbackWorkflowPhaseForAzureVMSS(workflowPhase, orchestrationWorkflowType);
    } else if (deploymentType == AZURE_WEBAPP) {
      return generateRollbackWorkflowPhaseForAzureWebApp(workflowPhase);
    } else if (deploymentType == CUSTOM) {
      return generateRollbackWorkflowPhaseForCustomDeployment(workflowPhase);
    } else {
      return generateRollbackWorkflowPhaseForSSH(appId, workflowPhase);
    }
  }

  private WorkflowPhase generateRollbackWorkflowPhaseForCustomDeployment(WorkflowPhase workflowPhase) {
    final List<PhaseStep> phaseSteps = new ArrayList<>();
    phaseSteps.add(aPhaseStep(CUSTOM_DEPLOYMENT_PHASE_STEP, WorkflowServiceHelper.ROLLBACK_SERVICE).build());
    phaseSteps.forEach(step -> step.setRollback(true));
    return rollbackWorkflow(workflowPhase).phaseSteps(phaseSteps).build();
  }

  private WorkflowPhase generateRollbackBgPhaseForSpotinstBg(
      WorkflowPhase workflowPhase, WorkflowCreationFlags creationFlags) {
    if (isAlbTrafficShiftType(creationFlags)) {
      return generateRollbackWorkflowPhaseForSpotinstAlbTrafficShift(workflowPhase);
    } else {
      return generateRollbackWorkflowPhaseForSpotInstBlueGreen(workflowPhase);
    }
  }

  private WorkflowPhase generateRollbackWfPhaseForAwsAmiBlueGreen(
      WorkflowPhase workflowPhase, WorkflowCreationFlags creationFlags) {
    if (isAsgAmiAlbTrafficShiftType(creationFlags)) {
      return generateRollbackWorkflowPhaseForAsgAmiTrafficShiftBlueGreen(workflowPhase);
    } else {
      return generateRollbackWorkflowPhaseForAwsAmiBlueGreen(workflowPhase);
    }
  }

  private void generateNewWorkflowPhaseStepsForCustomDeploymentType(WorkflowPhase workflowPhase) {
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    phaseSteps.add(aPhaseStep(CUSTOM_DEPLOYMENT_PHASE_STEP, WorkflowServiceHelper.DEPLOY)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(CUSTOM_DEPLOYMENT_FETCH_INSTANCES.name())
                                    .name(FETCH_INSTANCES)
                                    .properties(ImmutableMap.of(InstanceFetchStateKeys.stateTimeoutInMinutes, 1))
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(CUSTOM_DEPLOYMENT_PHASE_STEP, WorkflowServiceHelper.VERIFY_SERVICE).build());
    phaseSteps.add(aPhaseStep(CUSTOM_DEPLOYMENT_PHASE_STEP, WorkflowServiceHelper.WRAP_UP).build());
  }

  public void generateNewWorkflowPhaseStepsForArtifactCollection(WorkflowPhase workflowPhase) {
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();

    phaseSteps.add(aPhaseStep(PhaseStepType.PREPARE_STEPS, WorkflowServiceHelper.PREPARE_STEPS).build());

    phaseSteps.add(aPhaseStep(COLLECT_ARTIFACT, COLLECT_ARTIFACT_PHASE_STEP_NAME)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ARTIFACT_COLLECTION.name())
                                    .name(ARTIFACT_COLLECTION_STEP)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, WorkflowServiceHelper.WRAP_UP).build());
  }

  private boolean isSpotInstTypeInfra(String infraDefinitionId, String appId) {
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefinitionId);
    InfraMappingInfrastructureProvider infrastructure = infrastructureDefinition.getInfrastructure();
    return infrastructure instanceof AwsAmiInfrastructure
        && AmiDeploymentType.SPOTINST == ((AwsAmiInfrastructure) infrastructure).getAmiDeploymentType();
  }

  public WorkflowPhase getWorkflowPhase(Workflow workflow, String phaseId) {
    if (isBlank(phaseId)) {
      throw new InvalidRequestException("Phase Id not provided");
    }
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    return orchestrationWorkflow.getWorkflowPhases()
        .stream()
        .filter(workflowPhase -> workflowPhase.getUuid().equals(phaseId))
        .findFirst()
        .orElse(null);
  }

  public String getServiceIdFromPhase(WorkflowPhase workflowPhase) {
    if (workflowPhase != null) {
      //  in the future once service commands moved to template library, return serviceId only if service is not
      //  templatized in workflow phase
      return workflowPhase.getServiceId();
    }
    return null;
  }

  /**
   * Get service commands - exclude service that has internal commands
   *
   * @param workflowPhase
   * @param appId
   * @param svcId
   * @return
   */
  public List<String> getServiceCommands(WorkflowPhase workflowPhase, String appId, String svcId) {
    if (svcId != null && workflowPhase != null) {
      // Read Service commands only for SSH Deployment Type PDC, AWS and WINRM
      DeploymentType deploymentType = workflowPhase.getDeploymentType();
      if ((deploymentType == null || DeploymentType.SSH == deploymentType || DeploymentType.WINRM == deploymentType)
          && serviceResourceService.exist(appId, svcId)) {
        return serviceResourceService.getServiceCommands(appId, svcId)
            .stream()
            .map(serviceCommand -> serviceCommand.getCommand().getName())
            .distinct()
            .collect(Collectors.toList());
      }
    }
    return new ArrayList<>();
  }

  public static List<FailureStrategy> cleanupFailureStrategies(
      List<FailureStrategy> failureStrategies, Set<String> validStepNames) {
    if (isEmpty(failureStrategies)) {
      return Collections.emptyList();
    }

    List<FailureStrategy> strategies = cleanupFailureStrategies(failureStrategies);
    if (isEmpty(strategies)) {
      return Collections.emptyList();
    }
    return strategies.stream()
        .map(failureStrategy -> {
          if (!isEmpty(failureStrategy.getSpecificSteps())) {
            // Validate step names only if this failureStrategy applies to specificSteps.

            List<String> cleanedStepNames = new ArrayList<>(failureStrategy.getSpecificSteps());
            cleanedStepNames.removeIf(stepName -> !validStepNames.contains(stepName));

            if (isEmpty(cleanedStepNames)) {
              // If getSpecificSteps list becomes empty. Return false so that this strategy itself is deleted.
              // Otherwise this FailureStrategy will start applying to all steps.
              return null;
            } else if (cleanedStepNames.size() != failureStrategy.getSpecificSteps().size()) {
              // Some invalid steps were found.
              return failureStrategy.toBuilder().specificSteps(cleanedStepNames).build();
            }
          }
          return failureStrategy;
        })
        .filter(Objects::nonNull)
        .collect(toList());
  }

  public static List<FailureStrategy> cleanupFailureStrategies(List<FailureStrategy> failureStrategies) {
    if (isEmpty(failureStrategies)) {
      return Collections.emptyList();
    }

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    return failureStrategies.stream()
        .filter(failureStrategy -> isEmpty(validator.validate(failureStrategy)))
        .collect(toList());
  }

  public static void cleanupPhaseStepStrategies(PhaseStep phaseStep) {
    if (phaseStep == null) {
      return;
    }
    Set<String> validStepNames = phaseStep.getSteps() == null
        ? Collections.emptySet()
        : phaseStep.getSteps().stream().map(GraphNode::getName).collect(Collectors.toSet());
    Set<String> stepIds = phaseStep.getSteps() == null
        ? Collections.emptySet()
        : phaseStep.getSteps().stream().map(GraphNode::getId).collect(Collectors.toSet());
    if (isNotEmpty(phaseStep.getStepSkipStrategies())) {
      phaseStep.setStepSkipStrategies(
          phaseStep.getStepSkipStrategies()
              .stream()
              .filter(stepSkipStrategy -> {
                if (stepSkipStrategy.getScope() == StepSkipStrategy.Scope.ALL_STEPS) {
                  return true;
                }

                if (isNotEmpty(stepSkipStrategy.getStepIds())) {
                  stepSkipStrategy.setStepIds(
                      stepSkipStrategy.getStepIds().stream().filter(stepIds::contains).collect(toList()));
                }

                return isNotEmpty(stepSkipStrategy.getStepIds());
              })
              .collect(toList()));
    }

    if (isNotEmpty(phaseStep.getFailureStrategies())) {
      phaseStep.setFailureStrategies(cleanupFailureStrategies(phaseStep.getFailureStrategies(), validStepNames));
    }
  }

  public static void cleanupPhaseStrategies(WorkflowPhase workflowPhase) {
    if (workflowPhase == null || isEmpty(workflowPhase.getPhaseSteps())) {
      return;
    }

    for (PhaseStep phaseStep : workflowPhase.getPhaseSteps()) {
      cleanupPhaseStepStrategies(phaseStep);
    }
  }

  public static void cleanupWorkflowStrategies(OrchestrationWorkflow orchestrationWorkflow) {
    if (!(orchestrationWorkflow instanceof CanaryOrchestrationWorkflow)) {
      return;
    }

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
    if (isNotEmpty(canaryOrchestrationWorkflow.getFailureStrategies())) {
      canaryOrchestrationWorkflow.setFailureStrategies(
          cleanupFailureStrategies(canaryOrchestrationWorkflow.getFailureStrategies()));
    }

    cleanupPhaseStepStrategies(canaryOrchestrationWorkflow.getPreDeploymentSteps());
    cleanupPhaseStepStrategies(canaryOrchestrationWorkflow.getPostDeploymentSteps());
    if (isEmpty(canaryOrchestrationWorkflow.getWorkflowPhases())) {
      return;
    }

    for (WorkflowPhase workflowPhase : canaryOrchestrationWorkflow.getWorkflowPhases()) {
      cleanupPhaseStrategies(workflowPhase);
    }
  }

  public void validateService(WorkflowPhase phase, String serviceName, Boolean isBuild) {
    if (isBuild) {
      return;
    }
    if (!phase.checkServiceTemplatized() && phase.getServiceId() == null) {
      throw new InvalidRequestException("Service Cannot be Empty for name :" + serviceName);
    }
  }

  public AbstractWorkflowFactory.Category getCategory(String appId, String serviceId) {
    if (isNotBlank(serviceId) && isK8sV2Service(appId, serviceId)) {
      return AbstractWorkflowFactory.Category.K8S_V2;
    }
    return AbstractWorkflowFactory.Category.GENERAL;
  }

  public void validateWaitInterval(Workflow workflow) {
    if (workflow == null || workflow.getOrchestrationWorkflow() == null
        || workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() == null
        || OrchestrationWorkflowType.CUSTOM.equals(
            workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType())) {
      return;
    }
    CanaryOrchestrationWorkflow orchestrationWorkflow = null;
    if (workflow.getOrchestrationWorkflow() != null) {
      orchestrationWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
      if (orchestrationWorkflow != null) {
        validateWaitInterval(orchestrationWorkflow);
      }
    }
  }

  private void validateWaitInterval(CanaryOrchestrationWorkflow orchestrationWorkflow) {
    validateWaitInterval(orchestrationWorkflow.getPostDeploymentSteps());
    validateWaitInterval(orchestrationWorkflow.getPreDeploymentSteps());
    validateWaitInterval(orchestrationWorkflow.getRollbackProvisioners());
    validateWaitInterval(orchestrationWorkflow.getWorkflowPhaseIdMap());
    validateWaitInterval(orchestrationWorkflow.getRollbackWorkflowPhaseIdMap());
  }

  private void validateWaitIntervalValue(Integer value) {
    if (value != null) {
      if (value < 0) {
        throw new InvalidRequestException("Negative values for wait interval not allowed.");
      }
      // We use DelayEventHelper. Queueable.java has a FdTtlIndex limited to a day.
      // Refer to CDC-16519
      if (value > 24 * 60 * 60) {
        throw new InvalidRequestException("Wait Interval cannot be more than one day.");
      }
    }
  }

  private void validateWaitInterval(WorkflowPhase workflowPhase) {
    if (workflowPhase != null && workflowPhase.getPhaseSteps() != null) {
      workflowPhase.getPhaseSteps().forEach(phaseStep -> validateWaitIntervalValue(phaseStep.getWaitInterval()));
    }
  }

  public void validateWaitInterval(PhaseStep phaseStep) {
    if (phaseStep != null) {
      validateWaitIntervalValue(phaseStep.getWaitInterval());
    }
  }

  private void validateWaitInterval(Map<String, WorkflowPhase> workflowPhaseMap) {
    if (workflowPhaseMap != null) {
      workflowPhaseMap.forEach((workflowPhaseId, workflowPhase) -> validateWaitInterval(workflowPhase));
    }
  }
}
