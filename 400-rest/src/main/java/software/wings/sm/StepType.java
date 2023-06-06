/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.OrchestrationWorkflowType.BASIC;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.beans.OrchestrationWorkflowType.CANARY;
import static io.harness.beans.OrchestrationWorkflowType.MULTI_SERVICE;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.InfrastructureMappingType.AZURE_INFRA;
import static software.wings.beans.InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH;
import static software.wings.beans.InfrastructureMappingType.PHYSICAL_DATA_CENTER_WINRM;
import static software.wings.beans.PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP;
import static software.wings.beans.PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP;
import static software.wings.beans.PhaseStepType.AMI_SWITCH_AUTOSCALING_GROUP_ROUTES;
import static software.wings.beans.PhaseStepType.AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT;
import static software.wings.beans.PhaseStepType.CLUSTER_SETUP;
import static software.wings.beans.PhaseStepType.COLLECT_ARTIFACT;
import static software.wings.beans.PhaseStepType.CONTAINER_DEPLOY;
import static software.wings.beans.PhaseStepType.CONTAINER_SETUP;
import static software.wings.beans.PhaseStepType.CUSTOM_DEPLOYMENT_PHASE_STEP;
import static software.wings.beans.PhaseStepType.DEPLOY_AWSCODEDEPLOY;
import static software.wings.beans.PhaseStepType.DEPLOY_AWS_LAMBDA;
import static software.wings.beans.PhaseStepType.DISABLE_SERVICE;
import static software.wings.beans.PhaseStepType.ECS_UPDATE_LISTENER_BG;
import static software.wings.beans.PhaseStepType.ECS_UPDATE_ROUTE_53_DNS_WEIGHT;
import static software.wings.beans.PhaseStepType.ENABLE_SERVICE;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PROVISION_INFRASTRUCTURE;
import static software.wings.beans.PhaseStepType.ROUTE_UPDATE;
import static software.wings.beans.PhaseStepType.SELECT_NODE;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_SETUP_COMMAND_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.APPDYNAMICS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.APPROVAL_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ARTIFACT_CHECK_STEP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ARTIFACT_COLLECTION_STEP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AWS_CODE_DEPLOY;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AWS_LAMBDA;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.CF_CREATE_STACK;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.CF_DELETE_STACK;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.CLOUDWATCH;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.COMMAND_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.CUSTOM_LOG_VERIFICATION;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.CUSTOM_METRICS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.CVNG_STATE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DATADOG_LOG;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DATADOG_METRICS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DYNATRACE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_BG_SERVICE_SETUP_ELB;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_BG_SERVICE_SETUP_ROUTE_53;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_ROLLBACK_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_ROUTE53_DNS_WEIGHTS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_STEADY_STATE_CHK;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_SWAP_TARGET_GROUPS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_SWAP_TARGET_GROUPS_ROLLBACK;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_UPGRADE_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ELB;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.GCP_CLUSTER_SETUP_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.JIRA;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.KUBERNETES_ROLLBACK_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.KUBERNETES_SERVICE_SETUP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.KUBERNETES_UPGRADE_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.PCF_MAP_ROUTE_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.PCF_UNMAP_ROUTE_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.PROVISION_SHELL_SCRIPT;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_AUTOSCALING_GROUP_ROUTE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_AWS_AMI_CLUSTER;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_AWS_CODE_DEPLOY;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_AWS_LAMBDA;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_CLOUD_FORMATION;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_ECS_ROUTE53_DNS_WEIGHTS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_ECS_SETUP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_KUBERNETES_SETUP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_TERRAFORM_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLING_SELECT_NODES;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SELECT_NODE_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SERVICENOW;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SPLUNK_V2;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SPOTINST_ALB_SHIFT_LISTENER_UPDATE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.STACKDRIVER;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.STACKDRIVER_LOG;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SUMO_LOGIC;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SWAP_SERVICE_SELECTORS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.UPGRADE_AUTOSCALING_GROUP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.UPGRADE_AUTOSCALING_GROUP_ROUTE;
import static software.wings.sm.states.k8s.K8sApplyState.K8S_APPLY_STATE;
import static software.wings.sm.states.k8s.K8sTrafficSplitState.K8S_TRAFFIC_SPLIT_STATE_NAME;
import static software.wings.stencils.WorkflowStepType.APM;
import static software.wings.stencils.WorkflowStepType.ARTIFACT;
import static software.wings.stencils.WorkflowStepType.AWS_SSH;
import static software.wings.stencils.WorkflowStepType.CI_SYSTEM;
import static software.wings.stencils.WorkflowStepType.HELM;
import static software.wings.stencils.WorkflowStepType.INFRASTRUCTURE_PROVISIONER;
import static software.wings.stencils.WorkflowStepType.ISSUE_TRACKING;
import static software.wings.stencils.WorkflowStepType.KUBERNETES;
import static software.wings.stencils.WorkflowStepType.LOG;
import static software.wings.stencils.WorkflowStepType.NOTIFICATION;
import static software.wings.stencils.WorkflowStepType.PCF;
import static software.wings.stencils.WorkflowStepType.UTILITY;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.OrchestrationWorkflowType;

import software.wings.api.DeploymentType;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PhaseStepType;
import software.wings.common.ProvisionerConstants;
import software.wings.common.WorkflowConstants;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.impl.yaml.handler.workflow.APMVerificationStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.AppDynamicsStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.ApprovalStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.BambooStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.BarrierStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.CloudFormationProvisionStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.CommandStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.DynatraceStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.EmailStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.GcbStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.JenkinsStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.JiraStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.NewRelicStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.PrometheusStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.ResourceConstraintStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.ServiceNowStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.ShellScriptProvisionStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.ShellScriptStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.StepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.TerraformProvisionStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.TerragruntProvisionStepYamlBuilder;
import software.wings.sm.states.APMVerificationState;
import software.wings.sm.states.AppDynamicsState;
import software.wings.sm.states.ApprovalState;
import software.wings.sm.states.ArtifactCheckState;
import software.wings.sm.states.ArtifactCollectionState;
import software.wings.sm.states.AwsAmiRollbackSwitchRoutesState;
import software.wings.sm.states.AwsAmiRollbackTrafficShiftAlbSwitchRoutesState;
import software.wings.sm.states.AwsAmiServiceDeployState;
import software.wings.sm.states.AwsAmiServiceRollback;
import software.wings.sm.states.AwsAmiServiceSetup;
import software.wings.sm.states.AwsAmiServiceTrafficShiftAlbDeployState;
import software.wings.sm.states.AwsAmiServiceTrafficShiftAlbSetup;
import software.wings.sm.states.AwsAmiSwitchRoutesState;
import software.wings.sm.states.AwsAmiTrafficShiftAlbSwitchRoutesState;
import software.wings.sm.states.AwsCodeDeployRollback;
import software.wings.sm.states.AwsCodeDeployState;
import software.wings.sm.states.AwsLambdaRollback;
import software.wings.sm.states.AwsLambdaState;
import software.wings.sm.states.AwsNodeSelectState;
import software.wings.sm.states.AzureNodeSelectState;
import software.wings.sm.states.BambooState;
import software.wings.sm.states.BarrierState;
import software.wings.sm.states.BugsnagState;
import software.wings.sm.states.CVNGState;
import software.wings.sm.states.CloudWatchState;
import software.wings.sm.states.CommandState;
import software.wings.sm.states.CustomLogVerificationState;
import software.wings.sm.states.DatadogLogState;
import software.wings.sm.states.DatadogState;
import software.wings.sm.states.DcNodeSelectState;
import software.wings.sm.states.DynatraceState;
import software.wings.sm.states.EcsBGRollbackRoute53DNSWeightState;
import software.wings.sm.states.EcsBGUpdateListnerRollbackState;
import software.wings.sm.states.EcsBGUpdateListnerState;
import software.wings.sm.states.EcsBGUpdateRoute53DNSWeightState;
import software.wings.sm.states.EcsBlueGreenServiceSetup;
import software.wings.sm.states.EcsBlueGreenServiceSetupRoute53DNS;
import software.wings.sm.states.EcsDaemonServiceSetup;
import software.wings.sm.states.EcsRunTaskDeploy;
import software.wings.sm.states.EcsServiceDeploy;
import software.wings.sm.states.EcsServiceRollback;
import software.wings.sm.states.EcsServiceSetup;
import software.wings.sm.states.EcsSetupRollback;
import software.wings.sm.states.EcsSteadyStateCheck;
import software.wings.sm.states.ElasticLoadBalancerState;
import software.wings.sm.states.ElkAnalysisState;
import software.wings.sm.states.EmailState;
import software.wings.sm.states.GcbState;
import software.wings.sm.states.GcpClusterSetup;
import software.wings.sm.states.HelmDeployState;
import software.wings.sm.states.HelmRollbackState;
import software.wings.sm.states.HttpState;
import software.wings.sm.states.InstanaState;
import software.wings.sm.states.JenkinsState;
import software.wings.sm.states.KubernetesDeploy;
import software.wings.sm.states.KubernetesDeployRollback;
import software.wings.sm.states.KubernetesSetup;
import software.wings.sm.states.KubernetesSetupRollback;
import software.wings.sm.states.KubernetesSteadyStateCheck;
import software.wings.sm.states.KubernetesSwapServiceSelectors;
import software.wings.sm.states.LogzAnalysisState;
import software.wings.sm.states.NewRelicDeploymentMarkerState;
import software.wings.sm.states.NewRelicState;
import software.wings.sm.states.PrometheusState;
import software.wings.sm.states.ResourceConstraintState;
import software.wings.sm.states.RollingNodeSelectState;
import software.wings.sm.states.ShellScriptState;
import software.wings.sm.states.SplunkV2State;
import software.wings.sm.states.StackDriverLogState;
import software.wings.sm.states.StackDriverState;
import software.wings.sm.states.SumoLogicAnalysisState;
import software.wings.sm.states.TemplatizedSecretManagerState;
import software.wings.sm.states.azure.AzureVMSSDeployState;
import software.wings.sm.states.azure.AzureVMSSRollbackState;
import software.wings.sm.states.azure.AzureVMSSSetupState;
import software.wings.sm.states.azure.AzureVMSSSwitchRoutesRollbackState;
import software.wings.sm.states.azure.AzureVMSSSwitchRoutesState;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotRollback;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotSetup;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotShiftTraffic;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotSwap;
import software.wings.sm.states.collaboration.JiraCreateUpdate;
import software.wings.sm.states.collaboration.ServiceNowCreateUpdateState;
import software.wings.sm.states.customdeployment.InstanceFetchState;
import software.wings.sm.states.k8s.K8sApplyState;
import software.wings.sm.states.k8s.K8sBlueGreenDeploy;
import software.wings.sm.states.k8s.K8sCanaryDeploy;
import software.wings.sm.states.k8s.K8sDelete;
import software.wings.sm.states.k8s.K8sRollingDeploy;
import software.wings.sm.states.k8s.K8sRollingDeployRollback;
import software.wings.sm.states.k8s.K8sScale;
import software.wings.sm.states.k8s.K8sTrafficSplitState;
import software.wings.sm.states.pcf.MapRouteState;
import software.wings.sm.states.pcf.PcfDeployState;
import software.wings.sm.states.pcf.PcfPluginState;
import software.wings.sm.states.pcf.PcfRollbackState;
import software.wings.sm.states.pcf.PcfSetupState;
import software.wings.sm.states.pcf.PcfSwitchBlueGreenRoutes;
import software.wings.sm.states.pcf.UnmapRouteState;
import software.wings.sm.states.provision.ARMProvisionState;
import software.wings.sm.states.provision.ARMRollbackState;
import software.wings.sm.states.provision.ApplyTerraformProvisionState;
import software.wings.sm.states.provision.ApplyTerraformState;
import software.wings.sm.states.provision.CloudFormationCreateStackState;
import software.wings.sm.states.provision.CloudFormationDeleteStackState;
import software.wings.sm.states.provision.CloudFormationRollbackStackState;
import software.wings.sm.states.provision.DestroyTerraformProvisionState;
import software.wings.sm.states.provision.ShellScriptProvisionState;
import software.wings.sm.states.provision.TerraformRollbackState;
import software.wings.sm.states.provision.TerragruntApplyState;
import software.wings.sm.states.provision.TerragruntDestroyState;
import software.wings.sm.states.provision.TerragruntRollbackState;
import software.wings.sm.states.rancher.RancherK8sBlueGreenDeploy;
import software.wings.sm.states.rancher.RancherK8sCanaryDeploy;
import software.wings.sm.states.rancher.RancherK8sDelete;
import software.wings.sm.states.rancher.RancherK8sRollingDeploy;
import software.wings.sm.states.rancher.RancherK8sRollingDeployRollback;
import software.wings.sm.states.rancher.RancherKubernetesSwapServiceSelectors;
import software.wings.sm.states.rancher.RancherResolveState;
import software.wings.sm.states.spotinst.SpotInstDeployState;
import software.wings.sm.states.spotinst.SpotInstListenerUpdateRollbackState;
import software.wings.sm.states.spotinst.SpotInstListenerUpdateState;
import software.wings.sm.states.spotinst.SpotInstRollbackState;
import software.wings.sm.states.spotinst.SpotInstServiceSetup;
import software.wings.sm.states.spotinst.SpotinstTrafficShiftAlbDeployState;
import software.wings.sm.states.spotinst.SpotinstTrafficShiftAlbRollbackSwitchRoutesState;
import software.wings.sm.states.spotinst.SpotinstTrafficShiftAlbSetupState;
import software.wings.sm.states.spotinst.SpotinstTrafficShiftAlbSwitchRoutesState;
import software.wings.stencils.WorkflowStepType;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(CDC)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public enum StepType {
  // Important: Do not change the order of StepTypes in the enum.
  // The order of StepTypes dictates the order in which Step Types are shown under each Category.
  // Also, the Step Types are listed based on the order of Categories appearing in WorkflowStepType.

  // Each StepType takes in StateClass, the display name, the category to which the command belongs to, list of
  // PhaseStepTypes where this command is relevant, the deployment types where the command is relevant and a list
  // of PhaseTypes to indicate if the command is relevant only in rollback/ non-rollback section.
  // For eg. the AppDynamics State belongs to APM Category and its relevant only on Verify Service and K8S phase Step.
  // Another example is the AWS_NODE_SELECT command which belongs to AWS_SSH category, should show up only if
  // PhaseStepType is INFRASTRUCTURE_NODE or SELECT_NODE. Deployment type is SSH in this case since it should be only
  // shown for SSH based deployments

  // First the StepTypes are filtered based on the PhaseStepType -
  // if List<PhaseStepType> contains sectionId(i.e. phase step type), then we filter using the rollbackSection flag
  // if rollbackSection == true check if List<PhaseType> contains ROLLBACK -> include it in list of filtered
  // if rollbackSection == false check if List<PhaseType> contains NON_ROLLBACK -> include it in list of filtered
  // step types.
  // Next, the filtering happens based on deployment type - if List<DeploymentType> contains the specified
  // deployment type, it will be included in the list of filtered step types.
  // Next we group the StepTypes based on Categories(i.e WorkflowStepType) to display in UI

  // Artifact
  ARTIFACT_COLLECTION(ArtifactCollectionState.class, ARTIFACT_COLLECTION_STEP, asList(ARTIFACT),
      asList(COLLECT_ARTIFACT), asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK), true),
  ARTIFACT_CHECK(ArtifactCheckState.class, ARTIFACT_CHECK_STEP, asList(ARTIFACT), asList(PRE_DEPLOYMENT),
      asList(DeploymentType.SSH), asList(PhaseType.NON_ROLLBACK)),

  // SSH
  AWS_NODE_SELECT(AwsNodeSelectState.class, SELECT_NODE_NAME, asList(AWS_SSH), asList(INFRASTRUCTURE_NODE, SELECT_NODE),
      asList(DeploymentType.SSH, DeploymentType.WINRM), asList(PhaseType.NON_ROLLBACK)),
  ELASTIC_LOAD_BALANCER(ElasticLoadBalancerState.class, ELB, asList(AWS_SSH), asList(ENABLE_SERVICE, DISABLE_SERVICE),
      asList(DeploymentType.SSH), asList(PhaseType.NON_ROLLBACK)),

  // DC SSH
  DC_NODE_SELECT(DcNodeSelectState.class, SELECT_NODE_NAME, asList(WorkflowStepType.DC_SSH),
      asList(INFRASTRUCTURE_NODE, SELECT_NODE), asList(DeploymentType.SSH), asList(PhaseType.NON_ROLLBACK)),

  // AZURE ssh
  AZURE_NODE_SELECT(AzureNodeSelectState.class, SELECT_NODE_NAME, asList(WorkflowStepType.AZURE),
      asList(INFRASTRUCTURE_NODE, SELECT_NODE), Lists.newArrayList(DeploymentType.SSH, DeploymentType.WINRM),
      asList(PhaseType.NON_ROLLBACK)),

  // AZURE Virtual Machine Scale Set
  AZURE_VMSS_SETUP(AzureVMSSSetupState.class, WorkflowServiceHelper.AZURE_VMSS_SETUP,
      asList(WorkflowStepType.AZURE_VMSS), asList(PhaseStepType.AZURE_VMSS_SETUP),
      Lists.newArrayList(DeploymentType.AZURE_VMSS), asList(PhaseType.NON_ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),
  AZURE_VMSS_DEPLOY(AzureVMSSDeployState.class, WorkflowServiceHelper.AZURE_VMSS_DEPLOY,
      asList(WorkflowStepType.AZURE_VMSS), asList(PhaseStepType.AZURE_VMSS_DEPLOY),
      Lists.newArrayList(DeploymentType.AZURE_VMSS), asList(PhaseType.NON_ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),
  AZURE_VMSS_ROLLBACK(AzureVMSSRollbackState.class, WorkflowServiceHelper.AZURE_VMSS_ROLLBACK,
      asList(WorkflowStepType.AZURE_VMSS), asList(PhaseStepType.AZURE_VMSS_ROLLBACK),
      Lists.newArrayList(DeploymentType.AZURE_VMSS), asList(PhaseType.ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),
  AZURE_VMSS_SWITCH_ROUTES(AzureVMSSSwitchRoutesState.class, WorkflowServiceHelper.AZURE_VMSS_SWITCH_ROUTES,
      singletonList(WorkflowStepType.AZURE_VMSS), singletonList(PhaseStepType.AZURE_VMSS_SWITCH_ROUTES),
      Lists.newArrayList(DeploymentType.AZURE_VMSS), singletonList(PhaseType.NON_ROLLBACK), singletonList(BLUE_GREEN)),
  AZURE_VMSS_SWITCH_ROUTES_ROLLBACK(AzureVMSSSwitchRoutesRollbackState.class,
      WorkflowServiceHelper.AZURE_VMSS_SWITCH_ROUTES_ROLLBACK, singletonList(WorkflowStepType.AZURE_VMSS),
      singletonList(PhaseStepType.AZURE_VMSS_SWITCH_ROLLBACK), Lists.newArrayList(DeploymentType.AZURE_VMSS),
      singletonList(PhaseType.ROLLBACK), singletonList(BLUE_GREEN)),

  // Azure Web App
  AZURE_WEBAPP_SLOT_SETUP(AzureWebAppSlotSetup.class, WorkflowServiceHelper.AZURE_WEBAPP_SLOT_SETUP,
      singletonList(WorkflowStepType.AZURE_WEBAPP), singletonList(PhaseStepType.AZURE_WEBAPP_SLOT_SETUP),
      Lists.newArrayList(DeploymentType.AZURE_WEBAPP), singletonList(PhaseType.NON_ROLLBACK),
      asList(BASIC, CANARY, BLUE_GREEN)),
  AZURE_WEBAPP_SLOT_SWAP(AzureWebAppSlotSwap.class, WorkflowServiceHelper.AZURE_WEBAPP_SLOT_SWAP,
      singletonList(WorkflowStepType.AZURE_WEBAPP), singletonList(PhaseStepType.AZURE_WEBAPP_SLOT_SWAP),
      Lists.newArrayList(DeploymentType.AZURE_WEBAPP), singletonList(PhaseType.NON_ROLLBACK),
      asList(CANARY, BLUE_GREEN)),
  AZURE_WEBAPP_SLOT_SHIFT_TRAFFIC(AzureWebAppSlotShiftTraffic.class, WorkflowServiceHelper.AZURE_WEBAPP_SLOT_TRAFFIC,
      singletonList(WorkflowStepType.AZURE_WEBAPP), singletonList(AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT),
      Lists.newArrayList(DeploymentType.AZURE_WEBAPP), singletonList(PhaseType.NON_ROLLBACK), singletonList(CANARY)),
  AZURE_WEBAPP_SLOT_ROLLBACK(AzureWebAppSlotRollback.class, WorkflowServiceHelper.AZURE_WEBAPP_SLOT_ROLLBACK,
      singletonList(WorkflowStepType.AZURE_WEBAPP), singletonList(PhaseStepType.AZURE_WEBAPP_SLOT_ROLLBACK),
      Lists.newArrayList(DeploymentType.AZURE_WEBAPP), singletonList(PhaseType.ROLLBACK),
      asList(BASIC, CANARY, BLUE_GREEN)),

  // AWS CodeDeploy
  AWS_CODEDEPLOY_STATE(AwsCodeDeployState.class, AWS_CODE_DEPLOY, asList(WorkflowStepType.AWS_CODE_DEPLOY),
      asList(DEPLOY_AWSCODEDEPLOY), Lists.newArrayList(DeploymentType.AWS_CODEDEPLOY), asList(PhaseType.NON_ROLLBACK)),
  AWS_CODEDEPLOY_ROLLBACK(AwsCodeDeployRollback.class, ROLLBACK_AWS_CODE_DEPLOY,
      asList(WorkflowStepType.AWS_CODE_DEPLOY), asList(DEPLOY_AWSCODEDEPLOY),
      Lists.newArrayList(DeploymentType.AWS_CODEDEPLOY), asList(PhaseType.ROLLBACK)),

  // AWS lambda
  AWS_LAMBDA_STATE(AwsLambdaState.class, AWS_LAMBDA, asList(WorkflowStepType.AWS_LAMBDA), asList(DEPLOY_AWS_LAMBDA),
      Lists.newArrayList(DeploymentType.AWS_LAMBDA), asList(PhaseType.NON_ROLLBACK)),
  AWS_LAMBDA_ROLLBACK(AwsLambdaRollback.class, ROLLBACK_AWS_LAMBDA, asList(WorkflowStepType.AWS_LAMBDA),
      asList(DEPLOY_AWS_LAMBDA), Lists.newArrayList(DeploymentType.AWS_LAMBDA), asList(PhaseType.ROLLBACK)),

  // AMI
  AWS_AMI_SERVICE_SETUP(AwsAmiServiceSetup.class, AMI_SETUP_COMMAND_NAME, asList(WorkflowStepType.AWS_AMI),
      asList(AMI_AUTOSCALING_GROUP_SETUP), Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK)),
  ASG_AMI_SERVICE_ALB_SHIFT_SETUP(AwsAmiServiceTrafficShiftAlbSetup.class,
      WorkflowServiceHelper.ASG_AMI_ALB_SHIFT_SETUP, asList(WorkflowStepType.AWS_AMI),
      asList(AMI_AUTOSCALING_GROUP_SETUP), Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK)),
  AWS_AMI_SERVICE_DEPLOY(AwsAmiServiceDeployState.class, UPGRADE_AUTOSCALING_GROUP, asList(WorkflowStepType.AWS_AMI),
      asList(AMI_DEPLOY_AUTOSCALING_GROUP), Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK)),
  ASG_AMI_SERVICE_ALB_SHIFT_DEPLOY(AwsAmiServiceTrafficShiftAlbDeployState.class,
      WorkflowServiceHelper.ASG_AMI_ALB_SHIFT_DEPLOY, asList(WorkflowStepType.AWS_AMI),
      asList(AMI_DEPLOY_AUTOSCALING_GROUP), Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK)),
  AWS_AMI_SWITCH_ROUTES(AwsAmiSwitchRoutesState.class, UPGRADE_AUTOSCALING_GROUP_ROUTE,
      asList(WorkflowStepType.AWS_AMI), singletonList(AMI_SWITCH_AUTOSCALING_GROUP_ROUTES),
      Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK)),
  ASG_AMI_ALB_SHIFT_SWITCH_ROUTES(AwsAmiTrafficShiftAlbSwitchRoutesState.class, SPOTINST_ALB_SHIFT_LISTENER_UPDATE,
      asList(WorkflowStepType.AWS_AMI), singletonList(AMI_SWITCH_AUTOSCALING_GROUP_ROUTES),
      Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK)),
  AWS_AMI_SERVICE_ROLLBACK(AwsAmiServiceRollback.class, ROLLBACK_AWS_AMI_CLUSTER, asList(WorkflowStepType.AWS_AMI),
      asList(AMI_DEPLOY_AUTOSCALING_GROUP), Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.ROLLBACK)),
  AWS_AMI_ROLLBACK_SWITCH_ROUTES(AwsAmiRollbackSwitchRoutesState.class, ROLLBACK_AUTOSCALING_GROUP_ROUTE,
      asList(WorkflowStepType.AWS_AMI), singletonList(AMI_SWITCH_AUTOSCALING_GROUP_ROUTES),
      Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.ROLLBACK)),
  ASG_AMI_ROLLBACK_ALB_SHIFT_SWITCH_ROUTES(AwsAmiRollbackTrafficShiftAlbSwitchRoutesState.class,
      ROLLBACK_AUTOSCALING_GROUP_ROUTE, asList(WorkflowStepType.AWS_AMI),
      singletonList(AMI_SWITCH_AUTOSCALING_GROUP_ROUTES), Lists.newArrayList(DeploymentType.AMI),
      asList(PhaseType.ROLLBACK)),

  // ECS
  ECS_SERVICE_SETUP(EcsServiceSetup.class, WorkflowServiceHelper.ECS_SERVICE_SETUP, asList(WorkflowStepType.ECS),
      asList(CONTAINER_SETUP), Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.NON_ROLLBACK),
      asList(BASIC, CANARY), true),
  ECS_RUN_TASK(EcsRunTaskDeploy.class, WorkflowServiceHelper.ECS_RUN_TASK, asList(WorkflowStepType.ECS),
      asList(CONTAINER_SETUP, CONTAINER_DEPLOY, ECS_UPDATE_LISTENER_BG, ECS_UPDATE_ROUTE_53_DNS_WEIGHT, VERIFY_SERVICE,
          WRAP_UP),
      Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.NON_ROLLBACK),
      asList(BASIC, CANARY, BLUE_GREEN, MULTI_SERVICE), true),
  ECS_DAEMON_SERVICE_SETUP(EcsDaemonServiceSetup.class, WorkflowServiceHelper.ECS_DAEMON_SERVICE_SETUP,
      asList(WorkflowStepType.ECS), asList(CONTAINER_SETUP), Lists.newArrayList(DeploymentType.ECS),
      asList(PhaseType.NON_ROLLBACK), asList(BASIC), true),
  ECS_BG_SERVICE_SETUP(EcsBlueGreenServiceSetup.class, ECS_BG_SERVICE_SETUP_ELB, asList(WorkflowStepType.ECS),
      asList(CONTAINER_SETUP), Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.NON_ROLLBACK),
      asList(BLUE_GREEN), true),
  ECS_BG_SERVICE_SETUP_ROUTE53(EcsBlueGreenServiceSetupRoute53DNS.class, ECS_BG_SERVICE_SETUP_ROUTE_53,
      asList(WorkflowStepType.ECS), asList(CONTAINER_SETUP), Lists.newArrayList(DeploymentType.ECS),
      asList(PhaseType.NON_ROLLBACK), asList(BLUE_GREEN), true),
  ECS_SERVICE_DEPLOY(EcsServiceDeploy.class, ECS_UPGRADE_CONTAINERS, asList(WorkflowStepType.ECS),
      asList(CONTAINER_DEPLOY), Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.NON_ROLLBACK),
      asList(BASIC, CANARY, BLUE_GREEN), true),
  ECS_STEADY_STATE_CHECK(EcsSteadyStateCheck.class, ECS_STEADY_STATE_CHK, asList(WorkflowStepType.ECS),
      asList(CONTAINER_DEPLOY), Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.NON_ROLLBACK), true),
  ECS_LISTENER_UPDATE(EcsBGUpdateListnerState.class, ECS_SWAP_TARGET_GROUPS, asList(WorkflowStepType.ECS),
      singletonList(ECS_UPDATE_LISTENER_BG), Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.NON_ROLLBACK),
      asList(BLUE_GREEN), true),
  ECS_ROUTE53_DNS_WEIGHT_UPDATE(EcsBGUpdateRoute53DNSWeightState.class, ECS_ROUTE53_DNS_WEIGHTS,
      asList(WorkflowStepType.ECS), singletonList(ECS_UPDATE_ROUTE_53_DNS_WEIGHT),
      Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.NON_ROLLBACK), asList(BLUE_GREEN), true),

  ECS_SERVICE_SETUP_ROLLBACK(EcsSetupRollback.class, ROLLBACK_ECS_SETUP, asList(WorkflowStepType.ECS),
      asList(CONTAINER_SETUP), Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.ROLLBACK), asList(BASIC), true),
  ECS_SERVICE_ROLLBACK(EcsServiceRollback.class, ECS_ROLLBACK_CONTAINERS, asList(WorkflowStepType.ECS),
      asList(CONTAINER_DEPLOY), Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.ROLLBACK),
      asList(BASIC, CANARY, BLUE_GREEN), true),
  ECS_ROUTE53_DNS_WEIGHT_UPDATE_ROLLBACK(EcsBGRollbackRoute53DNSWeightState.class, ROLLBACK_ECS_ROUTE53_DNS_WEIGHTS,
      asList(WorkflowStepType.ECS), singletonList(ECS_UPDATE_ROUTE_53_DNS_WEIGHT),
      Lists.newArrayList(DeploymentType.ECS), asList(PhaseType.ROLLBACK), asList(BLUE_GREEN), true),
  ECS_LISTENER_UPDATE_ROLLBACK(EcsBGUpdateListnerRollbackState.class, ECS_SWAP_TARGET_GROUPS_ROLLBACK,
      asList(WorkflowStepType.ECS), singletonList(ECS_UPDATE_LISTENER_BG), Lists.newArrayList(DeploymentType.ECS),
      asList(PhaseType.ROLLBACK), asList(BLUE_GREEN), true),

  // Spot Instance
  SPOTINST_SETUP(SpotInstServiceSetup.class, WorkflowServiceHelper.SPOTINST_SETUP, asList(WorkflowStepType.SPOTINST),
      asList(PhaseStepType.SPOTINST_SETUP), Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK),
      asList(BASIC, CANARY, BLUE_GREEN)),
  SPOTINST_ALB_SHIFT_SETUP(SpotinstTrafficShiftAlbSetupState.class, WorkflowServiceHelper.SPOTINST_ALB_SHIFT_SETUP,
      asList(WorkflowStepType.SPOTINST), asList(PhaseStepType.SPOTINST_SETUP), Lists.newArrayList(DeploymentType.AMI),
      asList(PhaseType.NON_ROLLBACK), asList(BLUE_GREEN)),
  SPOTINST_DEPLOY(SpotInstDeployState.class, WorkflowServiceHelper.SPOTINST_DEPLOY, asList(WorkflowStepType.SPOTINST),
      asList(PhaseStepType.SPOTINST_DEPLOY), Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK),
      asList(BASIC, CANARY, BLUE_GREEN)),
  SPOTINST_ALB_SHIFT_DEPLOY(SpotinstTrafficShiftAlbDeployState.class, WorkflowServiceHelper.SPOTINST_ALB_SHIFT_DEPLOY,
      asList(WorkflowStepType.SPOTINST), asList(PhaseStepType.SPOTINST_DEPLOY), Lists.newArrayList(DeploymentType.AMI),
      asList(PhaseType.NON_ROLLBACK), asList(BLUE_GREEN)),
  SPOTINST_LISTENER_UPDATE(SpotInstListenerUpdateState.class, WorkflowServiceHelper.SPOTINST_LISTENER_UPDATE,
      asList(WorkflowStepType.SPOTINST), asList(PhaseStepType.SPOTINST_LISTENER_UPDATE),
      Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK), asList(BLUE_GREEN)),
  SPOTINST_LISTENER_ALB_SHIFT(SpotinstTrafficShiftAlbSwitchRoutesState.class, SPOTINST_ALB_SHIFT_LISTENER_UPDATE,
      asList(WorkflowStepType.SPOTINST), asList(PhaseStepType.SPOTINST_LISTENER_UPDATE),
      Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.NON_ROLLBACK), asList(BLUE_GREEN)),
  SPOTINST_ROLLBACK(SpotInstRollbackState.class, WorkflowServiceHelper.SPOTINST_ROLLBACK,
      asList(WorkflowStepType.SPOTINST), asList(PhaseStepType.SPOTINST_ROLLBACK),
      Lists.newArrayList(DeploymentType.AMI), asList(PhaseType.ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),
  SPOTINST_LISTENER_UPDATE_ROLLBACK(SpotInstListenerUpdateRollbackState.class,
      WorkflowServiceHelper.SPOTINST_LISTENER_UPDATE_ROLLBACK, asList(WorkflowStepType.SPOTINST),
      asList(PhaseStepType.SPOTINST_LISTENER_UPDATE_ROLLBACK), Lists.newArrayList(DeploymentType.AMI),
      asList(PhaseType.ROLLBACK), asList(BLUE_GREEN)),
  SPOTINST_LISTENER_ALB_SHIFT_ROLLBACK(SpotinstTrafficShiftAlbRollbackSwitchRoutesState.class,
      WorkflowServiceHelper.SPOTINST_ALB_SHIFT_LISTENER_UPDATE_ROLLBACK, asList(WorkflowStepType.SPOTINST),
      asList(PhaseStepType.SPOTINST_LISTENER_UPDATE_ROLLBACK), Lists.newArrayList(DeploymentType.AMI),
      asList(PhaseType.ROLLBACK), asList(BLUE_GREEN)),

  // K8S
  KUBERNETES_SETUP(KubernetesSetup.class, KUBERNETES_SERVICE_SETUP, asList(WorkflowStepType.KUBERNETES),
      asList(CONTAINER_SETUP), Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK)),
  KUBERNETES_DEPLOY(KubernetesDeploy.class, KUBERNETES_UPGRADE_CONTAINERS, asList(WorkflowStepType.KUBERNETES),
      asList(CONTAINER_DEPLOY, WRAP_UP), Lists.newArrayList(DeploymentType.KUBERNETES),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN, MULTI_SERVICE)),
  KUBERNETES_STEADY_STATE_CHECK(KubernetesSteadyStateCheck.class, WorkflowServiceHelper.KUBERNETES_STEADY_STATE_CHECK,
      asList(WorkflowStepType.KUBERNETES), asList(CONTAINER_DEPLOY, CONTAINER_SETUP),
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  GCP_CLUSTER_SETUP(GcpClusterSetup.class, GCP_CLUSTER_SETUP_NAME, asList(WorkflowStepType.KUBERNETES),
      asList(CLUSTER_SETUP), Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK)),
  K8S_CANARY_DEPLOY(K8sCanaryDeploy.class, WorkflowConstants.K8S_CANARY_DEPLOY, asList(KUBERNETES),
      asList(K8S_PHASE_STEP), Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK), asList(CANARY), true),
  K8S_BLUE_GREEN_DEPLOY(K8sBlueGreenDeploy.class, WorkflowConstants.K8S_BLUE_GREEN_DEPLOY, asList(KUBERNETES),
      asList(K8S_PHASE_STEP), Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK), asList(BLUE_GREEN), true),
  K8S_DEPLOYMENT_ROLLING(K8sRollingDeploy.class, WorkflowConstants.K8S_DEPLOYMENT_ROLLING, asList(KUBERNETES),
      asList(K8S_PHASE_STEP), Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK), asList(ROLLING, CANARY, MULTI_SERVICE), true),
  K8S_DEPLOYMENT_ROLLING_ROLLBACK(K8sRollingDeployRollback.class, WorkflowConstants.K8S_DEPLOYMENT_ROLLING_ROLLBACK,
      asList(KUBERNETES), asList(K8S_PHASE_STEP, WRAP_UP),
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM), asList(PhaseType.ROLLBACK), true),
  KUBERNETES_SWAP_SERVICE_SELECTORS(KubernetesSwapServiceSelectors.class, SWAP_SERVICE_SELECTORS, asList(KUBERNETES),
      asList(CONTAINER_SETUP, CONTAINER_DEPLOY, ROUTE_UPDATE, WRAP_UP, K8S_PHASE_STEP),
      Lists.newArrayList(DeploymentType.KUBERNETES), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK),
      asList(BASIC, ROLLING, CANARY, BLUE_GREEN, MULTI_SERVICE)),
  K8S_TRAFFIC_SPLIT(K8sTrafficSplitState.class, K8S_TRAFFIC_SPLIT_STATE_NAME, asList(KUBERNETES),
      asList(K8S_PHASE_STEP, WRAP_UP), Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), asList(ROLLING, CANARY, BLUE_GREEN, MULTI_SERVICE)),
  K8S_SCALE(K8sScale.class, WorkflowConstants.K8S_SCALE, asList(KUBERNETES), asList(K8S_PHASE_STEP, WRAP_UP),
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), asList(ROLLING, CANARY, BLUE_GREEN, MULTI_SERVICE), true),
  K8S_DELETE(K8sDelete.class, WorkflowConstants.K8S_DELETE, asList(KUBERNETES), asList(K8S_PHASE_STEP, WRAP_UP),
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), asList(ROLLING, CANARY, BLUE_GREEN, MULTI_SERVICE), true),
  K8S_APPLY(K8sApplyState.class, K8S_APPLY_STATE, asList(KUBERNETES), asList(K8S_PHASE_STEP, WRAP_UP),
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), asList(ROLLING, CANARY, BLUE_GREEN, MULTI_SERVICE), true),

  RANCHER_RESOLVE(RancherResolveState.class, WorkflowConstants.RANCHER_RESOLVE_CLUSTERS,
      asList(WorkflowStepType.KUBERNETES), asList(K8S_PHASE_STEP), Lists.newArrayList(DeploymentType.KUBERNETES),
      asList(PhaseType.NON_ROLLBACK), asList(ROLLING, CANARY, BLUE_GREEN)),
  RANCHER_K8S_DEPLOYMENT_ROLLING(RancherK8sRollingDeploy.class, WorkflowConstants.RANCHER_K8S_DEPLOYMENT_ROLLING,
      asList(KUBERNETES), asList(K8S_PHASE_STEP), Lists.newArrayList(DeploymentType.KUBERNETES),
      asList(PhaseType.NON_ROLLBACK), asList(ROLLING, CANARY)),
  RANCHER_K8S_CANARY_DEPLOY(RancherK8sCanaryDeploy.class, WorkflowConstants.RANCHER_K8S_CANARY_DEPLOY,
      asList(KUBERNETES), asList(K8S_PHASE_STEP), Lists.newArrayList(DeploymentType.KUBERNETES),
      asList(PhaseType.NON_ROLLBACK), asList(CANARY)),
  RANCHER_K8S_BLUE_GREEN_DEPLOY(RancherK8sBlueGreenDeploy.class, WorkflowConstants.RANCHER_K8S_BLUE_GREEN_DEPLOY,
      asList(KUBERNETES), asList(K8S_PHASE_STEP), Lists.newArrayList(DeploymentType.KUBERNETES),
      asList(PhaseType.NON_ROLLBACK), asList(BLUE_GREEN)),
  RANCHER_KUBERNETES_SWAP_SERVICE_SELECTORS(RancherKubernetesSwapServiceSelectors.class,
      WorkflowConstants.RANCHER_KUBERNETES_SWAP_SERVICE_SELECTORS, asList(KUBERNETES),
      asList(CONTAINER_SETUP, CONTAINER_DEPLOY, ROUTE_UPDATE, WRAP_UP, K8S_PHASE_STEP),
      Lists.newArrayList(DeploymentType.KUBERNETES), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK),
      asList(ROLLING, CANARY, BLUE_GREEN)),
  RANCHER_K8S_DELETE(RancherK8sDelete.class, WorkflowConstants.RANCHER_K8S_DELETE, asList(KUBERNETES),
      asList(K8S_PHASE_STEP, WRAP_UP), Lists.newArrayList(DeploymentType.KUBERNETES),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), asList(ROLLING, CANARY, BLUE_GREEN)),
  RANCHER_K8S_DEPLOYMENT_ROLLING_ROLLBACK(RancherK8sRollingDeployRollback.class,
      WorkflowConstants.RANCHER_K8S_DEPLOYMENT_ROLLING_ROLLBACK, asList(KUBERNETES), asList(K8S_PHASE_STEP, WRAP_UP),
      Lists.newArrayList(DeploymentType.KUBERNETES), asList(PhaseType.ROLLBACK)),

  ROLLING_NODE_SELECT(RollingNodeSelectState.class, ROLLING_SELECT_NODES, asList(WorkflowStepType.KUBERNETES),
      asList(SELECT_NODE), asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK)),

  KUBERNETES_SETUP_ROLLBACK(KubernetesSetupRollback.class, ROLLBACK_KUBERNETES_SETUP,
      asList(WorkflowStepType.KUBERNETES), asList(CONTAINER_SETUP),
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM), asList(PhaseType.ROLLBACK)),
  KUBERNETES_DEPLOY_ROLLBACK(KubernetesDeployRollback.class, KUBERNETES_ROLLBACK_CONTAINERS,
      asList(WorkflowStepType.KUBERNETES), asList(CONTAINER_DEPLOY),
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM), asList(PhaseType.ROLLBACK)),

  // Helm
  HELM_DEPLOY(HelmDeployState.class, WorkflowServiceHelper.HELM_DEPLOY, asList(HELM), asList(PhaseStepType.HELM_DEPLOY),
      Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM), asList(PhaseType.NON_ROLLBACK)),
  HELM_ROLLBACK(HelmRollbackState.class, WorkflowServiceHelper.HELM_ROLLBACK, asList(HELM),
      asList(PhaseStepType.HELM_DEPLOY), Lists.newArrayList(DeploymentType.KUBERNETES, DeploymentType.HELM),
      asList(PhaseType.ROLLBACK)),

  // PCF
  PCF_SETUP(PcfSetupState.class, WorkflowServiceHelper.PCF_SETUP, asList(PCF), asList(PhaseStepType.PCF_SETUP),
      Lists.newArrayList(DeploymentType.PCF), asList(PhaseType.NON_ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),
  PCF_RESIZE(PcfDeployState.class, WorkflowServiceHelper.PCF_RESIZE, asList(PCF), asList(PhaseStepType.PCF_RESIZE),
      Lists.newArrayList(DeploymentType.PCF), asList(PhaseType.NON_ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),
  PCF_MAP_ROUTE(MapRouteState.class, PCF_MAP_ROUTE_NAME, asList(PCF), asList(PhaseStepType.PCF_RESIZE),
      Lists.newArrayList(DeploymentType.PCF), asList(PhaseType.NON_ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),
  PCF_UNMAP_ROUTE(UnmapRouteState.class, PCF_UNMAP_ROUTE_NAME, asList(PCF), asList(PhaseStepType.PCF_RESIZE),
      Lists.newArrayList(DeploymentType.PCF), asList(PhaseType.NON_ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),
  PCF_BG_MAP_ROUTE(PcfSwitchBlueGreenRoutes.class, WorkflowServiceHelper.PCF_BG_SWAP_ROUTE, asList(PCF),
      asList(PhaseStepType.PCF_SWICH_ROUTES), Lists.newArrayList(DeploymentType.PCF),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK), asList(BLUE_GREEN)),
  PCF_ROLLBACK(PcfRollbackState.class, WorkflowServiceHelper.PCF_ROLLBACK, asList(PCF),
      asList(PhaseStepType.PCF_RESIZE), Lists.newArrayList(DeploymentType.PCF), asList(PhaseType.ROLLBACK),
      asList(BASIC, CANARY, BLUE_GREEN)),
  // todo @rk: verify
  PCF_PLUGIN(PcfPluginState.class, WorkflowServiceHelper.PCF_PLUGIN, asList(PCF),
      asList(PhaseStepType.PCF_SETUP, PhaseStepType.PCF_RESIZE), Lists.newArrayList(DeploymentType.PCF),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), asList(BASIC, CANARY, BLUE_GREEN)),

  // Infra Provisioners
  CLOUD_FORMATION_CREATE_STACK(CloudFormationCreateStackState.class, CF_CREATE_STACK,
      asList(INFRASTRUCTURE_PROVISIONER), asList(PhaseStepType.values()),
      Lists.newArrayList(DeploymentType.SSH, DeploymentType.AMI, DeploymentType.ECS, DeploymentType.AWS_LAMBDA,
          DeploymentType.AWS_CODEDEPLOY, DeploymentType.WINRM, DeploymentType.CUSTOM),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK), CloudFormationProvisionStepYamlBuilder.class),
  CLOUD_FORMATION_DELETE_STACK(CloudFormationDeleteStackState.class, CF_DELETE_STACK,
      asList(INFRASTRUCTURE_PROVISIONER), asList(PhaseStepType.values()),
      Lists.newArrayList(DeploymentType.SSH, DeploymentType.AMI, DeploymentType.ECS, DeploymentType.AWS_LAMBDA,
          DeploymentType.AWS_CODEDEPLOY, DeploymentType.WINRM, DeploymentType.CUSTOM),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), CloudFormationProvisionStepYamlBuilder.class),
  CLOUD_FORMATION_ROLLBACK_STACK(CloudFormationRollbackStackState.class, ROLLBACK_CLOUD_FORMATION,
      asList(INFRASTRUCTURE_PROVISIONER), singletonList(PRE_DEPLOYMENT),
      Lists.newArrayList(
          DeploymentType.SSH, DeploymentType.AMI, DeploymentType.ECS, DeploymentType.AWS_LAMBDA, DeploymentType.CUSTOM),
      asList(PhaseType.ROLLBACK), CloudFormationProvisionStepYamlBuilder.class),
  TERRAFORM_PROVISION(ApplyTerraformProvisionState.class, WorkflowServiceHelper.TERRAFORM_PROVISION,
      asList(INFRASTRUCTURE_PROVISIONER), asList(PRE_DEPLOYMENT, PROVISION_INFRASTRUCTURE),
      Lists.newArrayList(DeploymentType.SSH, DeploymentType.AMI, DeploymentType.ECS, DeploymentType.AWS_LAMBDA,
          DeploymentType.CUSTOM, DeploymentType.AZURE_WEBAPP),
      asList(PhaseType.NON_ROLLBACK), TerraformProvisionStepYamlBuilder.class),
  TERRAFORM_APPLY(ApplyTerraformState.class, WorkflowServiceHelper.TERRAFORM_APPLY, asList(INFRASTRUCTURE_PROVISIONER),
      asList(PhaseStepType.values()), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK), TerraformProvisionStepYamlBuilder.class),
  TERRAFORM_DESTROY(DestroyTerraformProvisionState.class, WorkflowServiceHelper.TERRAFORM_DESTROY,
      asList(INFRASTRUCTURE_PROVISIONER),
      asList(POST_DEPLOYMENT, WRAP_UP, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP),
      Lists.newArrayList(DeploymentType.SSH, DeploymentType.AMI, DeploymentType.ECS, DeploymentType.AWS_LAMBDA,
          DeploymentType.KUBERNETES, DeploymentType.CUSTOM),
      asList(PhaseType.NON_ROLLBACK), TerraformProvisionStepYamlBuilder.class),
  TERRAFORM_ROLLBACK(TerraformRollbackState.class, ROLLBACK_TERRAFORM_NAME, asList(INFRASTRUCTURE_PROVISIONER),
      singletonList(PRE_DEPLOYMENT),
      Lists.newArrayList(
          DeploymentType.SSH, DeploymentType.AMI, DeploymentType.ECS, DeploymentType.AWS_LAMBDA, DeploymentType.CUSTOM),
      asList(PhaseType.ROLLBACK), TerraformProvisionStepYamlBuilder.class),
  TERRAGRUNT_PROVISION(TerragruntApplyState.class, WorkflowServiceHelper.TERRAGRUNT_PROVISION,
      asList(INFRASTRUCTURE_PROVISIONER), asList(PhaseStepType.values()), asList(DeploymentType.values()),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), TerragruntProvisionStepYamlBuilder.class),
  TERRAGRUNT_DESTROY(TerragruntDestroyState.class, WorkflowServiceHelper.TERRAGRUNT_DESTROY,
      asList(INFRASTRUCTURE_PROVISIONER),
      asList(POST_DEPLOYMENT, WRAP_UP, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP),
      Lists.newArrayList(DeploymentType.SSH, DeploymentType.AMI, DeploymentType.ECS, DeploymentType.AWS_LAMBDA,
          DeploymentType.KUBERNETES, DeploymentType.CUSTOM),
      asList(PhaseType.NON_ROLLBACK), TerragruntProvisionStepYamlBuilder.class),
  TERRAGRUNT_ROLLBACK(TerragruntRollbackState.class, WorkflowServiceHelper.TERRAGRUNT_ROLLBACK,
      asList(INFRASTRUCTURE_PROVISIONER), singletonList(PRE_DEPLOYMENT),
      Lists.newArrayList(
          DeploymentType.SSH, DeploymentType.AMI, DeploymentType.ECS, DeploymentType.AWS_LAMBDA, DeploymentType.CUSTOM),
      asList(PhaseType.ROLLBACK), TerragruntProvisionStepYamlBuilder.class),
  SHELL_SCRIPT_PROVISION(ShellScriptProvisionState.class, PROVISION_SHELL_SCRIPT, asList(INFRASTRUCTURE_PROVISIONER),
      asList(PRE_DEPLOYMENT, PROVISION_INFRASTRUCTURE, CUSTOM_DEPLOYMENT_PHASE_STEP, INFRASTRUCTURE_NODE),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK), ShellScriptProvisionStepYamlBuilder.class),
  ARM_CREATE_RESOURCE(ARMProvisionState.class, WorkflowServiceHelper.ARM_CREATE_RESOURCE,
      Collections.singletonList(INFRASTRUCTURE_PROVISIONER),
      asList(PRE_DEPLOYMENT, PROVISION_INFRASTRUCTURE, POST_DEPLOYMENT, WRAP_UP),
      Lists.newArrayList(
          DeploymentType.SSH, DeploymentType.CUSTOM, DeploymentType.AZURE_WEBAPP, DeploymentType.AZURE_VMSS),
      Collections.singletonList(PhaseType.NON_ROLLBACK)),
  ARM_ROLLBACK(ARMRollbackState.class, ProvisionerConstants.ARM_ROLLBACK, asList(INFRASTRUCTURE_PROVISIONER),
      singletonList(PRE_DEPLOYMENT),
      Lists.newArrayList(
          DeploymentType.SSH, DeploymentType.CUSTOM, DeploymentType.AZURE_WEBAPP, DeploymentType.AZURE_VMSS),
      asList(PhaseType.ROLLBACK)),

  // APM
  APP_DYNAMICS(AppDynamicsState.class, APPDYNAMICS, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, PhaseStepType.SPOTINST_LISTENER_UPDATE, CUSTOM_DEPLOYMENT_PHASE_STEP),
      asList(DeploymentType.values()), asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK),
      AppDynamicsStepYamlBuilder.class),
  NEW_RELIC(NewRelicState.class, WorkflowServiceHelper.NEW_RELIC, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK), NewRelicStepYamlBuilder.class),
  INSTANA(InstanaState.class, WorkflowServiceHelper.INSTANA, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),

  DYNA_TRACE(DynatraceState.class, DYNATRACE, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK), DynatraceStepYamlBuilder.class),
  PROMETHEUS(PrometheusState.class, WorkflowServiceHelper.PROMETHEUS, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK), PrometheusStepYamlBuilder.class),
  DATA_DOG(DatadogState.class, DATADOG_METRICS, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  STACK_DRIVER(StackDriverState.class, STACKDRIVER, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  CLOUD_WATCH(CloudWatchState.class, CLOUDWATCH, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),

  APM_VERIFICATION(APMVerificationState.class, CUSTOM_METRICS, asList(APM),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK), APMVerificationStepYamlBuilder.class),

  // Logs
  DATA_DOG_LOG(DatadogLogState.class, DATADOG_LOG, asList(LOG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  BUG_SNAG(BugsnagState.class, WorkflowServiceHelper.BUG_SNAG, asList(LOG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)), //
  ELK(ElkAnalysisState.class, WorkflowServiceHelper.ELK, asList(LOG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, PhaseStepType.SPOTINST_LISTENER_UPDATE, CUSTOM_DEPLOYMENT_PHASE_STEP),
      asList(DeploymentType.values()), asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  SPLUNKV2(SplunkV2State.class, SPLUNK_V2, asList(LOG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  STACK_DRIVER_LOG(StackDriverLogState.class, STACKDRIVER_LOG, asList(LOG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  SUMO(SumoLogicAnalysisState.class, SUMO_LOGIC, asList(LOG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  LOGZ(LogzAnalysisState.class, WorkflowServiceHelper.LOGZ, asList(LOG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  LOG_VERIFICATION(CustomLogVerificationState.class, CUSTOM_LOG_VERIFICATION, asList(LOG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),
  // cvng
  CVNG(CVNGState.class, CVNG_STATE, asList(WorkflowStepType.CVNG),
      asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.values()),
      asList(PhaseType.ROLLBACK, PhaseType.NON_ROLLBACK)),

  // Issue Tracking
  JIRA_CREATE_UPDATE(JiraCreateUpdate.class, JIRA, asList(ISSUE_TRACKING), asList(PhaseStepType.values()),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), JiraStepYamlBuilder.class),
  SERVICENOW_CREATE_UPDATE(ServiceNowCreateUpdateState.class, SERVICENOW, asList(ISSUE_TRACKING),
      asList(PhaseStepType.values()), asList(DeploymentType.values()),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), ServiceNowStepYamlBuilder.class),

  // Notifications
  EMAIL(EmailState.class, WorkflowServiceHelper.EMAIL, asList(NOTIFICATION), asList(PhaseStepType.values()),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), EmailStepYamlBuilder.class),

  // Flow Control
  BARRIER(BarrierState.class, WorkflowServiceHelper.BARRIER, asList(WorkflowStepType.FLOW_CONTROL),
      asList(PhaseStepType.values()), asList(DeploymentType.values()),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), BarrierStepYamlBuilder.class),
  RESOURCE_CONSTRAINT(ResourceConstraintState.class, WorkflowServiceHelper.RESOURCE_CONSTRAINT,
      asList(WorkflowStepType.FLOW_CONTROL), asList(PhaseStepType.values()), asList(DeploymentType.values()),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), ResourceConstraintStepYamlBuilder.class),
  APPROVAL(ApprovalState.class, APPROVAL_NAME, asList(WorkflowStepType.FLOW_CONTROL), asList(PhaseStepType.values()),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK),
      ApprovalStepYamlBuilder.class),

  // CI System
  JENKINS(JenkinsState.class, WorkflowServiceHelper.JENKINS, asList(CI_SYSTEM), asList(PhaseStepType.values()),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK),
      JenkinsStepYamlBuilder.class),
  GCB(GcbState.class, WorkflowServiceHelper.GCB, singletonList(CI_SYSTEM), asList(PhaseStepType.values()),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), GcbStepYamlBuilder.class),
  BAMBOO(BambooState.class, WorkflowServiceHelper.BAMBOO, asList(CI_SYSTEM), asList(PhaseStepType.values()),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), BambooStepYamlBuilder.class),

  // Utility
  SHELL_SCRIPT(ShellScriptState.class, WorkflowServiceHelper.SHELL_SCRIPT, asList(UTILITY),
      asList(PhaseStepType.values()), asList(DeploymentType.values()),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), true, ShellScriptStepYamlBuilder.class),
  HTTP(HttpState.class, WorkflowServiceHelper.HTTP, asList(UTILITY), asList(PhaseStepType.values()),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK), true),
  NEW_RELIC_DEPLOYMENT_MARKER(NewRelicDeploymentMarkerState.class, WorkflowServiceHelper.NEW_RELIC_DEPLOYMENT_MARKER,
      asList(UTILITY), asList(VERIFY_SERVICE, K8S_PHASE_STEP, CUSTOM_DEPLOYMENT_PHASE_STEP),
      asList(DeploymentType.values()), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK)),

  TEMPLATIZED_SECRET_MANAGER(TemplatizedSecretManagerState.class, WorkflowServiceHelper.TEMPLATIZED_SECRET_MANAGER,
      asList(UTILITY), asList(PhaseStepType.values()), asList(DeploymentType.values()),
      asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK)),

  CUSTOM_DEPLOYMENT_FETCH_INSTANCES(InstanceFetchState.class, WorkflowServiceHelper.FETCH_INSTANCES, asList(UTILITY),
      asList(PhaseStepType.CUSTOM_DEPLOYMENT_PHASE_STEP), asList(DeploymentType.CUSTOM), asList(PhaseType.values())),

  // Command
  COMMAND(CommandState.class, COMMAND_NAME, asList(WorkflowStepType.SERVICE_COMMAND), asList(PhaseStepType.values()),
      Lists.newArrayList(DeploymentType.SSH, DeploymentType.WINRM), asList(PhaseType.NON_ROLLBACK, PhaseType.ROLLBACK),
      CommandStepYamlBuilder.class);

  private final Class<? extends State> stateClass;
  private List<String> phaseStepTypes = new ArrayList<>();
  private String displayName = UPPER_UNDERSCORE.to(UPPER_CAMEL, name());
  private List<DeploymentType> deploymentTypes = emptyList();
  private List<OrchestrationWorkflowType> orchestrationWorkflowTypes = emptyList();
  private List<WorkflowStepType> workflowStepTypes = emptyList();
  private List<PhaseType> phaseTypes = emptyList();
  private Class<? extends StepYamlBuilder> yamlValidatorClass;
  private boolean supportsTimeoutFailure;

  StepType(Class<? extends State> stateClass, String displayName, List<WorkflowStepType> workflowStepTypes,
      List<PhaseStepType> phaseStepTypes, List<DeploymentType> deploymentTypes, List<PhaseType> phaseTypes) {
    this.stateClass = stateClass;
    this.displayName = displayName;
    this.deploymentTypes = deploymentTypes;
    this.phaseStepTypes = phaseStepTypes.stream().map(Enum::name).collect(toList());
    this.workflowStepTypes = workflowStepTypes;
    this.phaseTypes = phaseTypes;
  }

  StepType(Class<? extends State> stateClass, String displayName, List<WorkflowStepType> workflowStepTypes,
      List<PhaseStepType> phaseStepTypes, List<DeploymentType> deploymentTypes, List<PhaseType> phaseTypes,
      boolean supportsTimeoutFailure) {
    this.stateClass = stateClass;
    this.displayName = displayName;
    this.deploymentTypes = deploymentTypes;
    this.phaseStepTypes = phaseStepTypes.stream().map(Enum::name).collect(toList());
    this.workflowStepTypes = workflowStepTypes;
    this.phaseTypes = phaseTypes;
    this.supportsTimeoutFailure = supportsTimeoutFailure;
  }

  StepType(Class<? extends State> stateClass, String displayName, List<WorkflowStepType> workflowStepTypes,
      List<PhaseStepType> phaseStepTypes, List<DeploymentType> deploymentTypes, List<PhaseType> phaseTypes,
      Class<? extends StepYamlBuilder> yamlValidatorClass) {
    this.stateClass = stateClass;
    this.displayName = displayName;
    this.deploymentTypes = deploymentTypes;
    this.phaseStepTypes = phaseStepTypes.stream().map(Enum::name).collect(toList());
    this.workflowStepTypes = workflowStepTypes;
    this.phaseTypes = phaseTypes;
    this.yamlValidatorClass = yamlValidatorClass;
  }

  StepType(Class<? extends State> stateClass, String displayName, List<WorkflowStepType> workflowStepTypes,
      List<PhaseStepType> phaseStepTypes, List<DeploymentType> deploymentTypes, List<PhaseType> phaseTypes,
      boolean supportsTimeoutFailure, Class<? extends StepYamlBuilder> yamlValidatorClass) {
    this.stateClass = stateClass;
    this.displayName = displayName;
    this.deploymentTypes = deploymentTypes;
    this.phaseStepTypes = phaseStepTypes.stream().map(Enum::name).collect(toList());
    this.workflowStepTypes = workflowStepTypes;
    this.phaseTypes = phaseTypes;
    this.supportsTimeoutFailure = supportsTimeoutFailure;
    this.yamlValidatorClass = yamlValidatorClass;
  }

  StepType(Class<? extends State> stateClass, String displayName, List<WorkflowStepType> workflowStepTypes,
      List<PhaseStepType> phaseStepTypes, List<DeploymentType> deploymentTypes, List<PhaseType> phaseTypes,
      List<OrchestrationWorkflowType> orchestrationWorkflowTypes, boolean supportsTimeoutFailure) {
    this.stateClass = stateClass;
    this.displayName = displayName;
    this.deploymentTypes = deploymentTypes;
    this.phaseStepTypes = phaseStepTypes.stream().map(Enum::name).collect(toList());
    this.workflowStepTypes = workflowStepTypes;
    this.phaseTypes = phaseTypes;
    this.orchestrationWorkflowTypes = orchestrationWorkflowTypes;
    this.supportsTimeoutFailure = supportsTimeoutFailure;
  }

  StepType(Class<? extends State> stateClass, String displayName, List<WorkflowStepType> workflowStepTypes,
      List<PhaseStepType> phaseStepTypes, List<DeploymentType> deploymentTypes, List<PhaseType> phaseTypes,
      List<OrchestrationWorkflowType> orchestrationWorkflowTypes) {
    this.stateClass = stateClass;
    this.displayName = displayName;
    this.deploymentTypes = deploymentTypes;
    this.phaseStepTypes = phaseStepTypes.stream().map(Enum::name).collect(toList());
    this.workflowStepTypes = workflowStepTypes;
    this.phaseTypes = phaseTypes;
    this.orchestrationWorkflowTypes = orchestrationWorkflowTypes;
  }

  public List<WorkflowStepType> getWorkflowStepTypes() {
    return workflowStepTypes;
  }

  public String getName() {
    return displayName;
  }

  @JsonValue
  public String getType() {
    return name();
  }

  public List<String> getPhaseStepTypes() {
    return phaseStepTypes;
  }

  public List<PhaseType> getPhaseTypes() {
    return phaseTypes;
  }

  public Class<? extends StepYamlBuilder> getYamlValidatorClass() {
    return yamlValidatorClass;
  }

  public boolean supportsTimeoutFailure() {
    return supportsTimeoutFailure;
  }

  public boolean matchesDeploymentType(DeploymentType deploymentType) {
    // Deployment type == null in case of build workflow for pre-deployment section
    return deploymentTypes.contains(deploymentType) || deploymentType == null;
  }

  public boolean matches(DeploymentType deploymentType, OrchestrationWorkflowType orchestrationWorkflowType) {
    // Deployment type == null in case of build workflow for pre-deployment section
    return matchesDeploymentType(deploymentType) && matchesWithOrchestrationWorkflowType(orchestrationWorkflowType);
  }

  private boolean matchesWithOrchestrationWorkflowType(OrchestrationWorkflowType orchestrationWorkflowType) {
    return isEmpty(orchestrationWorkflowTypes) || orchestrationWorkflowTypes.contains(orchestrationWorkflowType);
  }

  public static List<StepType> filterByPhaseStepType(String phaseStepType, boolean rollbackSection) {
    List<StepType> stepTypes = new ArrayList<>();
    // go over all step types
    // if List<PhaseStepType> contains sectionId(i.e. phase step type), then we filter using the rollbackSection flag
    // if rollbackSection == true check if List<PhaseType> contains ROLLBACK -> include it in list of filtered
    // if rollbackSection == false check if List<PhaseType> contains NON_ROLLBACK -> include it in list of filtered
    // step types
    for (StepType stepType : StepType.values()) {
      if (stepType.getPhaseStepTypes().contains(phaseStepType)) {
        if (rollbackSection) {
          if (stepType.getPhaseTypes().contains(PhaseType.ROLLBACK)) {
            stepTypes.add(stepType);
          }
        } else {
          if (stepType.getPhaseTypes().contains(PhaseType.NON_ROLLBACK)) {
            stepTypes.add(stepType);
          }
        }
      }
    }
    return stepTypes;
  }

  // static map that contains Categories and the corresponding step types in a specific order
  // order of values in each category is governed by the order of StepType enum values.
  // Changing order of that enum will affect the order of steps within each category.
  // Step types in turn are listed in order of Categories they belong to. Category order is as defined in
  // WorkflowStepType enum.
  public static final Map<WorkflowStepType, List<StepType>> workflowStepTypeListMap = new LinkedHashMap<>();
  public static final Map<InfrastructureMappingType, StepType> infrastructureMappingTypeToStepTypeMap =
      new LinkedHashMap<>();
  public static final Set<StepType> k8sSteps = new HashSet<>();

  static {
    for (StepType st : StepType.values()) {
      st.getWorkflowStepTypes().forEach(wst -> {
        List<StepType> listStateType = workflowStepTypeListMap.computeIfAbsent(wst, ignore -> new ArrayList<>());
        listStateType.add(st);
      });
    }

    infrastructureMappingTypeToStepTypeMap.put(PHYSICAL_DATA_CENTER_SSH, DC_NODE_SELECT);
    infrastructureMappingTypeToStepTypeMap.put(InfrastructureMappingType.AWS_SSH, AWS_NODE_SELECT);
    infrastructureMappingTypeToStepTypeMap.put(AZURE_INFRA, AZURE_NODE_SELECT);
    infrastructureMappingTypeToStepTypeMap.put(PHYSICAL_DATA_CENTER_WINRM, AZURE_NODE_SELECT);

    k8sSteps.add(KUBERNETES_SWAP_SERVICE_SELECTORS);
    k8sSteps.add(K8S_SCALE);
    k8sSteps.add(K8S_DELETE);
    k8sSteps.add(K8S_APPLY);
    k8sSteps.add(K8S_BLUE_GREEN_DEPLOY);
    k8sSteps.add(K8S_DEPLOYMENT_ROLLING);
    k8sSteps.add(K8S_CANARY_DEPLOY);
    k8sSteps.add(K8S_DEPLOYMENT_ROLLING_ROLLBACK);
    k8sSteps.add(K8S_TRAFFIC_SPLIT);
  }
}
