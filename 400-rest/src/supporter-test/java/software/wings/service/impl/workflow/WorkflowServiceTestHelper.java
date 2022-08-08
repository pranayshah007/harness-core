/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.api.DeploymentType.AMI;
import static software.wings.api.DeploymentType.AWS_LAMBDA;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.api.DeploymentType.HELM;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.api.DeploymentType.WINRM;
import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.BlueGreenOrchestrationWorkflow.BlueGreenOrchestrationWorkflowBuilder.aBlueGreenOrchestrationWorkflow;
import static software.wings.beans.BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.CustomOrchestrationWorkflow.CustomOrchestrationWorkflowBuilder.aCustomOrchestrationWorkflow;
import static software.wings.beans.DirectKubernetesInfrastructureMapping.Builder.aDirectKubernetesInfrastructureMapping;
import static software.wings.beans.EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.Graph.Builder.aGraph;
import static software.wings.beans.GraphLink.Builder.aLink;
import static software.wings.beans.InfrastructureMappingType.AWS_AMI;
import static software.wings.beans.InfrastructureMappingType.AWS_ECS;
import static software.wings.beans.InfrastructureMappingType.GCP_KUBERNETES;
import static software.wings.beans.InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH;
import static software.wings.beans.MultiServiceOrchestrationWorkflow.MultiServiceOrchestrationWorkflowBuilder.aMultiServiceOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP;
import static software.wings.beans.PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP;
import static software.wings.beans.PhaseStepType.HELM_DEPLOY;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;
import static software.wings.beans.PipelineStage.PipelineStageElement.builder;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.common.TemplateConstants.LATEST_TAG;
import static software.wings.common.WorkflowConstants.PHASE_NAME_PREFIX;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DEPLOY_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DEPLOY_SERVICE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.RUNTIME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SPOTINST_ALB_SHIFT_LISTENER_UPDATE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SPOTINST_LISTENER_UPDATE;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.UPGRADE_CONTAINERS;
import static software.wings.settings.SettingVariableTypes.AWS;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_DEPLOY;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_ROLLBACK;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_SETUP;
import static software.wings.sm.StateType.AWS_NODE_SELECT;
import static software.wings.sm.StateType.CLOUD_FORMATION_CREATE_STACK;
import static software.wings.sm.StateType.ECS_SERVICE_DEPLOY;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.sm.StateType.HELM_ROLLBACK;
import static software.wings.sm.StateType.SHELL_SCRIPT;
import static software.wings.sm.StateType.TERRAFORM_PROVISION;
import static software.wings.sm.StateType.WAIT;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_COMMAND_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.TARGET_APP_ID;
import static software.wings.utils.WingsTestConstants.TARGET_SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.RepairActionCode;
import io.harness.beans.WorkflowType;

import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.BasicOrchestrationWorkflow;
import software.wings.beans.BuildWorkflow;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.CustomOrchestrationWorkflow;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.EntityType;
import software.wings.beans.ExecutionScope;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.beans.stats.CloneMetadata;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AwsLambdaInfrastructure;
import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.sm.StateType;
import software.wings.sm.StepType;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowServiceTestHelper {
  public static Workflow constructCustomWorkflow() {
    Graph graph = aGraph()
                      .addNodes(GraphNode.builder().id("n1").name("stop").type(ENV_STATE.name()).origin(true).build(),
                          GraphNode.builder()
                              .id("n2")
                              .name("wait")
                              .type(WAIT.name())
                              .properties(ImmutableMap.<String, Object>builder().put("duration", 1l).build())
                              .build(),
                          GraphNode.builder().id("n3").name("start").type(ENV_STATE.name()).build())
                      .addLinks(aLink().withId("l1").withFrom("n1").withTo("n2").withType("success").build())
                      .addLinks(aLink().withId("l2").withFrom("n2").withTo("n3").withType("success").build())
                      .build();

    CustomOrchestrationWorkflow orchestrationWorkflow = aCustomOrchestrationWorkflow().withGraph(graph).build();
    return aWorkflow()
        .appId(APP_ID)
        .name("workflow1")
        .description("Sample Workflow")
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(orchestrationWorkflow)
        .build();
  }

  public static Workflow constructCanaryWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .envId(ENV_ID)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructCanaryWorkflowWithPhase() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .deploymentType(SSH)
                                                         .build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructCanaryWorkflowWithTwoPhases() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraMappingId(INFRA_MAPPING_ID)
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraMappingId(INFRA_MAPPING_ID)
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .build())
                                   .build())
        .build();
  }

  public static Workflow constructCanaryWithHttpStep() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).addStep(constructHttpStep()).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .build())
        .build();
  }

  public static Workflow constructCanaryWithApprovalStep() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT)
                                            .addStep(constructApprovalStep(asList(USER_GROUP_ID, USER_GROUP_ID + 2)))
                                            .build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .build())
        .build();
  }

  public static Workflow constructCanaryWithApprovalPhaseStep() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .addWorkflowPhase(
                    aWorkflowPhase()
                        .infraMappingId(INFRA_MAPPING_ID)
                        .infraDefinitionId(INFRA_DEFINITION_ID)
                        .serviceId(SERVICE_ID)
                        .deploymentType(SSH)
                        .phaseSteps(asList(aPhaseStep(VERIFY_SERVICE, WorkflowServiceHelper.ENABLE_SERVICE)
                                               .addStep(constructApprovalStep(asList(USER_GROUP_ID, USER_GROUP_ID + 2)))
                                               .build()))
                        .build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .build())
        .build();
  }

  public static Workflow constructCanaryWithHttpPhaseStep() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .addWorkflowPhase(
                    aWorkflowPhase()
                        .infraMappingId(INFRA_MAPPING_ID)
                        .infraDefinitionId(INFRA_DEFINITION_ID)
                        .serviceId(SERVICE_ID)
                        .deploymentType(SSH)
                        .phaseSteps(asList(aPhaseStep(VERIFY_SERVICE, WorkflowServiceHelper.ENABLE_SERVICE)
                                               .addStep(constructHttpStep())
                                               .build()))
                        .build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .build())
        .build();
  }

  public static Workflow constructCanaryHttpAsPostDeploymentStep() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).addStep(constructHttpStep()).build())
                .build())
        .build();
  }

  private static GraphNode constructHttpStep() {
    return GraphNode.builder()
        .type("HTTP")
        .name("http")
        .properties(ImmutableMap.<String, Object>builder().put("url", "http://www.google.com").build())
        .build();
  }

  public static GraphNode constructApprovalStep(List<String> userGroups) {
    return GraphNode.builder()
        .type(StepType.APPROVAL.name())
        .name("Approval")
        .properties(ImmutableMap.<String, Object>builder()
                        .put("userGroups", userGroups)
                        .put("approvalStateType", ApprovalStateType.USER_GROUP.name())
                        .build())
        .build();
  }

  public static Workflow constructBasicWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .envId(ENV_ID)
        .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructBlueGreenWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .envId(ENV_ID)
        .orchestrationWorkflow(aBlueGreenOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructBasicWorkflowWithPhase() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .envId(ENV_ID)
        .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .deploymentType(SSH)
                                                         .build())
                                   .build())
        .build();
  }

  public static Workflow constructBasicWorkflowWithPhaseSteps() {
    WorkflowPhase workflowPhase =
        aWorkflowPhase()
            .phaseSteps(asList(aPhaseStep(PhaseStepType.CONTAINER_SETUP, "Set up Container").build()))
            .build();

    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .uuid(WORKFLOW_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .envId(ENV_ID)
        .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .withWorkflowPhaseIdMap(ImmutableMap.of("WORKFLOW_PHASE_ID", workflowPhase))
                                   .withWorkflowPhaseIds(asList("WORKFLOW_PHASE_ID"))
                                   .build())
        .build();
  }

  public static Workflow constructBuildWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .orchestrationWorkflow(aBuildOrchestrationWorkflow().build())
        .build();
  }

  public static Workflow constructBuildWorkflowWithPhase() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aBuildOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .addWorkflowPhase(
                    aWorkflowPhase()
                        .phaseSteps(asList(aPhaseStep(PhaseStepType.COLLECT_ARTIFACT, "Collect Artifact").build()))
                        .build())
                .build())
        .build();
  }

  public static Workflow constructBasicWorkflowWithInfraNodeDeployServicePhaseStep() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aBuildOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .addWorkflowPhase(
                    aWorkflowPhase()
                        .serviceId(SERVICE_ID)
                        .deploymentType(SSH)
                        .infraDefinitionId(INFRA_DEFINITION_ID)
                        .phaseSteps(
                            asList(aPhaseStep(PhaseStepType.INFRASTRUCTURE_NODE, INFRASTRUCTURE_NODE.name()).build(),
                                aPhaseStep(PhaseStepType.DEPLOY_SERVICE, "Deploy Service").build()))
                        .build())
                .build())
        .build();
  }

  public static Workflow constructBasicWorkflowWithInfraNodeDeployServicePhaseStepAndWinRmDeployment() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aBuildOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .addWorkflowPhase(
                    aWorkflowPhase()
                        .serviceId(SERVICE_ID)
                        .deploymentType(WINRM)
                        .infraDefinitionId(INFRA_DEFINITION_ID)
                        .phaseSteps(
                            asList(aPhaseStep(PhaseStepType.INFRASTRUCTURE_NODE, INFRASTRUCTURE_NODE.name()).build(),
                                aPhaseStep(PhaseStepType.DEPLOY_SERVICE, "Deploy Service").build()))
                        .build())
                .build())
        .build();
  }

  public static Workflow constructBasicWorkflowWithInfraNodeDeployServicePhaseStepWithInfraDefinitionId() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aBuildOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .addWorkflowPhase(
                    aWorkflowPhase()
                        .serviceId(SERVICE_ID)
                        .deploymentType(SSH)
                        .infraDefinitionId(INFRA_DEFINITION_ID)
                        .phaseSteps(
                            asList(aPhaseStep(PhaseStepType.INFRASTRUCTURE_NODE, INFRASTRUCTURE_NODE.name()).build(),
                                aPhaseStep(PhaseStepType.DEPLOY_SERVICE, "Deploy Service").build()))
                        .build())
                .build())
        .build();
  }

  public static Workflow constructMultiServiceWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .envId(ENV_ID)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aMultiServiceOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructMultiServiceWorkflowWithPhase() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .envId(ENV_ID)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aMultiServiceOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .deploymentType(SSH)
                                                         .build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructMulitServiceTemplateWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aMultiServiceOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .deploymentType(SSH)
                                                         .build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .deploymentType(SSH)
                                                         .build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructTemplatizedCanaryWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .deploymentType(SSH)
                                                         .build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .deploymentType(SSH)
                                                         .build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static ServiceCommand constructServiceCommand() {
    return aServiceCommand()
        .withServiceId(SERVICE_ID)
        .withUuid(SERVICE_COMMAND_ID)
        .withAppId(APP_ID)
        .withName("START")
        .withCommand(
            aCommand()
                .withArtifactNeeded(true)
                .withName("START")
                .addCommandUnits(
                    anExecCommandUnit().withCommandPath("/home/xxx/tomcat").withCommandString("bin/startup.sh").build())
                .build())
        .build();
  }

  public static void assertClonedWorkflowAcrossApps(Workflow workflow2, Workflow clonedWorkflow) {
    assertThat(clonedWorkflow).isNotNull();
    assertThat(clonedWorkflow.getUuid()).isNotEqualTo(workflow2.getUuid());
    assertThat(clonedWorkflow.getAppId()).isEqualTo(TARGET_APP_ID);
    assertThat(clonedWorkflow.getEnvId()).isNullOrEmpty();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow2.getOrchestrationWorkflow();
    CanaryOrchestrationWorkflow clonedOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) clonedWorkflow.getOrchestrationWorkflow();
    assertThat(clonedOrchestrationWorkflow).isNotNull();
    assertThat(clonedOrchestrationWorkflow.getOrchestrationWorkflowType())
        .isEqualTo(orchestrationWorkflow.getOrchestrationWorkflowType());
    assertThat(clonedOrchestrationWorkflow.isValid()).isFalse();
    assertThat(clonedOrchestrationWorkflow.getValidationMessage()).startsWith("Environment");
    List<WorkflowPhase> workflowPhases = clonedOrchestrationWorkflow.getWorkflowPhases();
    assertThat(workflowPhases).extracting(WorkflowPhase::getServiceId).containsNull();
    assertThat(workflowPhases).isNotNull().hasSize(1).extracting(WorkflowPhase::getInfraMappingId).containsNull();
    assertThat(workflowPhases).isNotNull().hasSize(1).extracting(WorkflowPhase::getInfraMappingName).containsNull();
    assertThat(workflowPhases).isNotNull().hasSize(1).extracting(WorkflowPhase::getComputeProviderId).containsNull();
  }

  public static void assertOrchestrationWorkflow(CanaryOrchestrationWorkflow orchestrationWorkflow) {
    assertThat(orchestrationWorkflow)
        .isNotNull()
        .hasFieldOrProperty("preDeploymentSteps")
        .hasFieldOrProperty("postDeploymentSteps")
        .hasFieldOrProperty("graph");
    assertThat(orchestrationWorkflow.getGraph()).isNotNull();
    assertThat(orchestrationWorkflow.getGraph().getNodes())
        .extracting("id")
        .contains(orchestrationWorkflow.getPostDeploymentSteps().getUuid(),
            orchestrationWorkflow.getPostDeploymentSteps().getUuid());

    assertThat(orchestrationWorkflow.getGraph().getSubworkflows())
        .containsKeys(orchestrationWorkflow.getPostDeploymentSteps().getUuid(),
            orchestrationWorkflow.getPostDeploymentSteps().getUuid());

    assertThat(orchestrationWorkflow.getNotificationRules()).isNotNull();
    assertThat(orchestrationWorkflow.getNotificationRules().get(0)).isNotNull();
    assertThat(orchestrationWorkflow.getNotificationRules().get(0).getConditions()).isNotNull();
    assertThat(orchestrationWorkflow.getNotificationRules().get(0).getExecutionScope())
        .isEqualTo(ExecutionScope.WORKFLOW);

    if (!(orchestrationWorkflow instanceof BuildWorkflow)) {
      assertThat(orchestrationWorkflow.getFailureStrategies()).isNotNull();
      assertThat(orchestrationWorkflow.getFailureStrategies().get(0)).isNotNull();
      assertThat(orchestrationWorkflow.getFailureStrategies().get(0).getRepairActionCode())
          .isEqualTo(RepairActionCode.ROLLBACK_WORKFLOW);
    }
  }

  public static Workflow constructWorkflowWithParam(PhaseStep phaseStep) {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraMappingId(INFRA_MAPPING_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .phaseSteps(asList(phaseStep))
                                                         .deploymentType(SSH)
                                                         .build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructEcsWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .addWorkflowPhase(aWorkflowPhase()
                                      .serviceId(SERVICE_ID)
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .deploymentType(ECS)
                                      .phaseSteps(asList(aPhaseStep(PhaseStepType.CONTAINER_DEPLOY, DEPLOY_CONTAINERS)
                                                             .addStep(GraphNode.builder()
                                                                          .id(generateUuid())
                                                                          .type(ECS_SERVICE_DEPLOY.name())
                                                                          .name(UPGRADE_CONTAINERS)
                                                                          .build())
                                                             .build()))
                                      .build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .build())
        .build();
  }

  public static Workflow constructHelmWorkflowWithProperties(Map<String, Object> properties) {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(getHelmDeployPhase(properties))
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructBlueGreenHelmWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .orchestrationWorkflow(aBlueGreenOrchestrationWorkflow()
                                   .withWorkflowPhases(asList(aWorkflowPhase()
                                                                  .serviceId(SERVICE_ID)
                                                                  .infraDefinitionId(INFRA_DEFINITION_ID)
                                                                  .deploymentType(HELM)
                                                                  .phaseSteps(asList(aPhaseStep(HELM_DEPLOY).build()))
                                                                  .build()))
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructHelmRollbackWorkflowWithProperties(Map<String, Object> properties) {
    WorkflowPhase helmDeployPhase = getHelmDeployPhase(properties);

    WorkflowPhase rollbackPhase = getHelmRollbackyPhase(helmDeployPhase, properties);
    Map<String, WorkflowPhase> rollbackMap = new HashMap<>();
    rollbackMap.put(helmDeployPhase.getUuid(), rollbackPhase);

    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(helmDeployPhase)
                                   .withRollbackWorkflowPhaseIdMap(rollbackMap)
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  private static WorkflowPhase getHelmRollbackyPhase(WorkflowPhase phase, Map<String, Object> properties) {
    return aWorkflowPhase()
        .uuid(generateUuid())
        .serviceId(phase.getServiceId())
        .infraMappingId(phase.getInfraMappingId())
        .deploymentType(DeploymentType.HELM)
        .name("Rollback" + phase.getName())
        .deploymentType(phase.getDeploymentType())
        .rollback(true)
        .phaseSteps(asList(aPhaseStep(HELM_DEPLOY, DEPLOY_CONTAINERS)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(HELM_ROLLBACK.name())
                                            .name(UPGRADE_CONTAINERS)
                                            .properties(properties)
                                            .build())
                               .build()))
        .build();
  }

  private static WorkflowPhase getHelmDeployPhase(Map<String, Object> properties) {
    return aWorkflowPhase()
        .uuid(generateUuid())
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .deploymentType(DeploymentType.HELM)
        .phaseSteps(asList(aPhaseStep(HELM_DEPLOY, DEPLOY_CONTAINERS)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(HELM_DEPLOY.name())
                                            .name(UPGRADE_CONTAINERS)
                                            .properties(properties)
                                            .build())
                               .build()))
        .build();
  }

  public static Workflow constructK8SWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .serviceId(SERVICE_ID)
                                                         .infraMappingId(INFRA_MAPPING_ID)
                                                         .deploymentType(DeploymentType.KUBERNETES)
                                                         .phaseSteps(asList(aPhaseStep(K8S_PHASE_STEP, "Deploy")
                                                                                .addStep(GraphNode.builder()
                                                                                             .id(generateUuid())
                                                                                             .type(SHELL_SCRIPT.name())
                                                                                             .name("Shell Script")
                                                                                             .build())
                                                                                .build()))
                                                         .build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public static Workflow constructK8SBlueGreenWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .orchestrationWorkflow(aBlueGreenOrchestrationWorkflow()
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .deploymentType(DeploymentType.KUBERNETES)
                                                         .phaseSteps(asList(aPhaseStep(K8S_PHASE_STEP, "Deploy")
                                                                                .addStep(GraphNode.builder()
                                                                                             .id(generateUuid())
                                                                                             .type(SHELL_SCRIPT.name())
                                                                                             .name("Shell Script")
                                                                                             .build())
                                                                                .build()))
                                                         .build())
                                   .build())
        .build();
  }

  public static Workflow constructAmiWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .orchestrationWorkflow(
            aBasicOrchestrationWorkflow()
                .withWorkflowPhases(asList(
                    aWorkflowPhase()
                        .serviceId(SERVICE_ID)
                        .infraDefinitionId(INFRA_DEFINITION_ID)
                        .deploymentType(AMI)
                        .phaseSteps(asList(
                            aPhaseStep(AMI_DEPLOY_AUTOSCALING_GROUP, AMI_DEPLOY_AUTOSCALING_GROUP.name()).build()))
                        .build()))
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .build())
        .build();
  }

  public static Workflow constructAmiBGWorkflow() {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .orchestrationWorkflow(
            aBlueGreenOrchestrationWorkflow()
                .withWorkflowPhases(asList(
                    aWorkflowPhase()
                        .serviceId(SERVICE_ID)
                        .infraDefinitionId(INFRA_DEFINITION_ID)
                        .deploymentType(AMI)
                        .phaseSteps(asList(
                            aPhaseStep(AMI_DEPLOY_AUTOSCALING_GROUP, AMI_DEPLOY_AUTOSCALING_GROUP.name()).build()))
                        .build()))
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .build())
        .build();
  }

  public static GcpKubernetesInfrastructureMapping constructGKInfraMapping() {
    return aGcpKubernetesInfrastructureMapping()
        .withUuid(INFRA_MAPPING_ID)
        .withServiceId(SERVICE_ID)
        .withDeploymentType(DeploymentType.KUBERNETES.name())
        .withInfraMappingType(GCP_KUBERNETES.name())
        .withComputeProviderType(GCP.name())
        .withClusterName(RUNTIME)
        .build();
  }

  public static InfrastructureDefinition constructGKInfraDef() {
    return InfrastructureDefinition.builder()
        .deploymentType(KUBERNETES)
        .uuid(INFRA_DEFINITION_ID)
        .cloudProviderType(CloudProviderType.GCP)
        .infrastructure(GoogleKubernetesEngine.builder().clusterName(RUNTIME).build())
        .build();
  }

  public static PhysicalInfrastructureMapping constructPhysicalInfraMapping() {
    return aPhysicalInfrastructureMapping()
        .withUuid(INFRA_MAPPING_ID)
        .withServiceId(SERVICE_ID)
        .withDeploymentType(DeploymentType.SSH.name())
        .withInfraMappingType(InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH.name())
        .withComputeProviderType(PHYSICAL_DATA_CENTER_SSH.name())
        .build();
  }

  public static DirectKubernetesInfrastructureMapping constructDirectKubernetesInfra() {
    return aDirectKubernetesInfrastructureMapping()
        .withUuid(INFRA_MAPPING_ID)
        .withServiceId(SERVICE_ID)
        .withDeploymentType(DeploymentType.KUBERNETES.name())
        .withComputeProviderType(GCP.name())
        .build();
  }

  public static InfrastructureDefinition constructDirectKubernetesInfraDef() {
    return InfrastructureDefinition.builder()
        .uuid(INFRA_DEFINITION_ID)
        .deploymentType(KUBERNETES)
        .cloudProviderType(CloudProviderType.GCP)
        .infrastructure(DirectKubernetesInfrastructure.builder().build())
        .build();
  }

  public static DirectKubernetesInfrastructureMapping constructHELMInfra() {
    return aDirectKubernetesInfrastructureMapping()
        .withAppId(APP_ID)
        .withUuid(INFRA_MAPPING_ID)
        .withServiceId(SERVICE_ID)
        .withServiceTemplateId(SERVICE_TEMPLATE_ID)
        .withDeploymentType(DeploymentType.HELM.name())
        .withComputeProviderType(GCP.name())
        .build();
  }

  public static InfrastructureDefinition constructHELMInfraDef() {
    return InfrastructureDefinition.builder()
        .appId(APP_ID)
        .uuid(INFRA_DEFINITION_ID)
        .envId(ENV_ID)
        .deploymentType(HELM)
        .cloudProviderType(CloudProviderType.KUBERNETES_CLUSTER)
        .infrastructure(GoogleKubernetesEngine.builder().build())
        .build();
  }

  public static EcsInfrastructureMapping constructEcsnfraMapping() {
    return anEcsInfrastructureMapping()
        .withUuid(INFRA_MAPPING_ID)
        .withServiceId(SERVICE_ID)
        .withDeploymentType(ECS.name())
        .withInfraMappingType(AWS_ECS.name())
        .withComputeProviderType(GCP.name())
        .withClusterName(RUNTIME)
        .build();
  }

  public static InfrastructureDefinition constructEcsInfraDef() {
    return InfrastructureDefinition.builder()
        .uuid(INFRA_DEFINITION_ID)
        .deploymentType(ECS)
        .cloudProviderType(CloudProviderType.AWS)
        .appId(APP_ID)
        .infrastructure(AwsEcsInfrastructure.builder().clusterName(RUNTIME).build())
        .build();
  }

  public static EcsInfrastructureMapping constructAmiInfraMapping() {
    return anEcsInfrastructureMapping()
        .withUuid(INFRA_MAPPING_ID)
        .withServiceId(SERVICE_ID)
        .withDeploymentType(DeploymentType.AMI.name())
        .withInfraMappingType(AWS_AMI.name())
        .withComputeProviderType(AWS.name())
        .build();
  }

  public static InfrastructureDefinition constructAmiInfraDef() {
    return InfrastructureDefinition.builder()
        .uuid(INFRA_DEFINITION_ID)
        .cloudProviderType(CloudProviderType.AWS)
        .deploymentType(AMI)
        .infrastructure(AwsAmiInfrastructure.builder().build())
        .build();
  }

  public static AwsLambdaInfraStructureMapping constructAwsLambdaInfraMapping() {
    AwsLambdaInfraStructureMapping awsLambdaInfraStructureMapping = new AwsLambdaInfraStructureMapping();
    awsLambdaInfraStructureMapping.setUuid(INFRA_MAPPING_ID);
    awsLambdaInfraStructureMapping.setServiceId(SERVICE_ID);
    awsLambdaInfraStructureMapping.setDeploymentType(DeploymentType.AWS_LAMBDA.name());
    awsLambdaInfraStructureMapping.setComputeProviderType(AWS.name());

    return awsLambdaInfraStructureMapping;
  }

  public static InfrastructureDefinition constructAwsLambdaInfraDef() {
    return InfrastructureDefinition.builder()
        .deploymentType(AWS_LAMBDA)
        .cloudProviderType(CloudProviderType.AWS)
        .uuid(INFRA_DEFINITION_ID)
        .infrastructure(AwsLambdaInfrastructure.builder().build())
        .build();
  }

  public static CloneMetadata constructCloneMetadata(Workflow workflow2) {
    return CloneMetadata.builder()
        .workflow(workflow2)
        .serviceMapping(ImmutableMap.of(SERVICE_ID, TARGET_SERVICE_ID))
        .targetAppId(TARGET_APP_ID)
        .build();
  }

  public static Pipeline constructPipeline(String workflowId) {
    return Pipeline.builder()
        .name("PIPELINE_NAME")
        .pipelineStages(
            asList(PipelineStage.builder()
                       .pipelineStageElements(asList(builder()
                                                         .name("STAGE")
                                                         .type(ENV_STATE.name())
                                                         .properties(ImmutableMap.of("workflowId", workflowId))
                                                         .build()))
                       .build()))
        .build();
  }

  public static Workflow constructBasicDeploymentTemplateWorkflow() {
    return aWorkflow()
        .envId(ENV_ID)
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .serviceId(SERVICE_ID)
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .orchestrationWorkflow(
            aBasicOrchestrationWorkflow()
                .withWorkflowPhases(asList(aWorkflowPhase()
                                               .serviceId(SERVICE_ID)
                                               .infraDefinitionId(INFRA_DEFINITION_ID)
                                               .templateExpressions(asList(getEnvTemplateExpression(),
                                                   getServiceTemplateExpression(), prepareInfraDefTemplateExpression()))
                                               .build()))
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .build())
        .templateExpressions(
            asList(getEnvTemplateExpression(), getServiceTemplateExpression(), prepareInfraDefTemplateExpression()))
        .build();
  }

  public static Workflow constructLinkedTemplate(GraphNode step) {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .uuid(WORKFLOW_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).addStep(step).build())
                .addWorkflowPhase(
                    aWorkflowPhase()
                        .infraMappingId(INFRA_MAPPING_ID)
                        .infraDefinitionId(INFRA_DEFINITION_ID)
                        .serviceId(SERVICE_ID)
                        .deploymentType(SSH)
                        .phaseSteps(asList(
                            aPhaseStep(VERIFY_SERVICE, WorkflowServiceHelper.VERIFY_SERVICE).addStep(step).build()))
                        .build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).addStep(step).build())
                .build())
        .build();
  }

  public static void assertTemplatizedWorkflow(Workflow workflow3) {
    assertThat(workflow3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("envId");
    OrchestrationWorkflow orchestrationWorkflow = workflow3.getOrchestrationWorkflow();
    List<WorkflowPhase> workflowPhases = ((BasicOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases();
    assertThat(orchestrationWorkflow).extracting("userVariables").isNotNull();
    assertThat(
        orchestrationWorkflow.getUserVariables().stream().anyMatch(variable -> variable.getName().equals("Service")))
        .isTrue();

    assertThat(workflowPhases).isNotNull().hasSize(1);

    WorkflowPhase workflowPhase = workflowPhases.get(0);
    assertThat(workflowPhase).isNotNull().hasFieldOrPropertyWithValue("name", PHASE_NAME_PREFIX + 1);
    assertThat(workflowPhase.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(orchestrationWorkflow.getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(ENVIRONMENT, SERVICE, EntityType.INFRASTRUCTURE_DEFINITION);
    assertThat(workflowPhase.fetchServiceTemplateExpression().getMetadata()).containsKey(Variable.ARTIFACT_TYPE);
  }

  public static List<TemplateExpression> constructAppdTemplateExpressions() {
    return asList(TemplateExpression.builder()
                      .fieldName("analysisServerConfigId")
                      .expression("${AppDynamics_Server}")
                      .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_CONFIGID"))
                      .build(),
        TemplateExpression.builder()
            .fieldName("applicationId")
            .expression("${AppDynamics_App}")
            .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_APPID"))
            .build(),
        TemplateExpression.builder()
            .fieldName("tierId")
            .expression("${AppDynamics_Tier}")
            .metadata(ImmutableMap.of("entityType", "APPDYNAMICS_TIERID"))
            .build());
  }

  public static List<TemplateExpression> constructElkTemplateExpressions() {
    return asList(TemplateExpression.builder()
                      .fieldName("analysisServerConfigId")
                      .expression("${ELK_Server}")
                      .metadata(ImmutableMap.of("entityType", "ELK_CONFIGID"))
                      .build(),
        TemplateExpression.builder()
            .fieldName("indices")
            .expression("${ELK_Indices}")
            .metadata(ImmutableMap.of("entityType", "ELK_INDICES"))
            .build());
  }

  public static PhaseStep constructAppDVerifyStep(WorkflowPhase workflowPhase) {
    PhaseStep verifyPhaseStep = workflowPhase.getPhaseSteps()
                                    .stream()
                                    .filter(ps -> ps.getPhaseStepType() == PhaseStepType.VERIFY_SERVICE)
                                    .collect(toList())
                                    .get(0);

    verifyPhaseStep.getSteps().add(GraphNode.builder()
                                       .type("APP_DYNAMICS")
                                       .name("APP_DYNAMICS")
                                       .properties(ImmutableMap.<String, Object>builder()
                                                       .put("analysisServerConfigId", "analysisServerConfigId")
                                                       .put("applicationId", "applicationId")
                                                       .put("tierId", "tierId")
                                                       .build())
                                       .build());

    verifyPhaseStep.getSteps().add(GraphNode.builder()
                                       .type("ELK")
                                       .name("ELK")
                                       .properties(ImmutableMap.<String, Object>builder()
                                                       .put("analysisServerConfigId", "analysisServerConfigId")
                                                       .put("indices", "indices")
                                                       .build())
                                       .build());
    return verifyPhaseStep;
  }

  public static TemplateExpression getEnvTemplateExpression() {
    return TemplateExpression.builder()
        .fieldName("envId")
        .expression("${Environment}")
        .metadata(ImmutableMap.of("entityType", "ENVIRONMENT"))
        .build();
  }

  public static TemplateExpression getServiceTemplateExpression() {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(Variable.ENTITY_TYPE, SERVICE.name());
    return TemplateExpression.builder().fieldName("serviceId").expression("${Service}").metadata(metadata).build();
  }

  public static TemplateExpression getInfraTemplateExpression() {
    return TemplateExpression.builder()
        .fieldName("infraMappingId")
        .expression("${ServiceInfra_SSH}")
        .metadata(ImmutableMap.of("entityType", "INFRASTRUCTURE_MAPPING"))
        .build();
  }

  public static TemplateExpression prepareInfraDefTemplateExpression() {
    return TemplateExpression.builder()
        .fieldName("infraDefinitionId")
        .expression("${InfraDef_SSH}")
        .metadata(ImmutableMap.of("entityType", "INFRASTRUCTURE_DEFINITION"))
        .build();
  }

  public static GraphNode constructHttpTemplateStep() {
    return GraphNode.builder()
        .type(StateType.HTTP.name())
        .templateUuid(TEMPLATE_ID)
        .templateVersion(LATEST_TAG)
        .properties(constructHttpProperties())
        .templateVariables(
            asList(aVariable().name("url").value("https://harness.io?q=${artifact.name}").build(), aVariable().build()))
        .build();
  }

  public static GraphNode constructShellScriptTemplateStep() {
    return GraphNode.builder()
        .type(StateType.SHELL_SCRIPT.name())
        .templateUuid(TEMPLATE_ID)
        .properties(constructShellScriptProperties())
        .templateVersion(LATEST_TAG)
        .templateVariables(asList(aVariable().name("name").value("${artifact.name}").build(), aVariable().build()))
        .build();
  }

  public static GraphNode constructCommandTemplateStep() {
    return GraphNode.builder()
        .type(StateType.COMMAND.name())
        .templateUuid(TEMPLATE_ID)
        .properties(constructCommandTemplateProperties())
        .templateVersion(LATEST_TAG)
        .templateVariables(asList(aVariable().name("name").value("${artifact.name}").build(), aVariable().build()))
        .build();
  }

  private static Map<String, Object> constructHttpProperties() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("url", "${url}");
    properties.put("method", "GET");
    properties.put("assertion", "${assertion}");
    return properties;
  }

  private static Map<String, Object> constructShellScriptProperties() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("scriptType", "BASH");
    properties.put("scriptString", "echo \"Executing\" ${name}\n export A=\"aaa\"");
    properties.put("outputVars", "A");
    return properties;
  }

  private static Map<String, Object> constructCommandTemplateProperties() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("commandName", "MyInstall");
    properties.put("host", "localhost");
    properties.put("sshKeyRef", "dummykey");
    properties.put("executeOnDelegate", "false");
    return properties;
  }

  public static void assertLinkedPhaseStep(
      PhaseStep phaseStep, GraphNode preDeploymentStep, PhaseStep updatedPhaseStep) {
    assertThat(updatedPhaseStep).isNotNull();
    GraphNode updatedPreStep = phaseStep.getSteps().stream().findFirst().orElse(null);
    assertLinkedStep(updatedPreStep);
    assertThat(preDeploymentStep.getProperties()).isNotEmpty().containsKeys("url", "method");
    assertThat(preDeploymentStep.getProperties()).isNotEmpty().doesNotContainValue("200 OK");
  }

  public static void assertLinkedStep(GraphNode updatedPreStep) {
    assertThat(updatedPreStep).isNotNull();
    assertThat(updatedPreStep.getTemplateUuid()).isNotEmpty().isEqualTo(TEMPLATE_ID);
    assertThat(updatedPreStep.getTemplateVersion()).isNotEmpty().isEqualTo("1");

    assertThat(updatedPreStep.getTemplateVariables()).isNotEmpty();

    assertThat(updatedPreStep.getTemplateVariables()).isNotEmpty().extracting(Variable::getName).contains("url");
    assertThat(updatedPreStep.getTemplateVariables())
        .isNotEmpty()
        .extracting(Variable::getValue)
        .contains("https://google.com");
    assertThat(updatedPreStep.getTemplateVariables())
        .isNotEmpty()
        .extracting(Variable::getValue)
        .doesNotContain("200 OK");
  }

  public static void assertTemplatizedOrchestrationWorkflow(OrchestrationWorkflow orchestrationWorkflow) {
    assertThat(orchestrationWorkflow.getTemplatizedServiceIds()).isNotNull().doesNotContain(SERVICE_ID);
    assertThat(orchestrationWorkflow.getTemplatizedInfraDefinitionIds()).isNotNull().contains(INFRA_DEFINITION_ID);
    assertThat(orchestrationWorkflow).extracting("userVariables").isNotNull();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("InfraDefinition_SSH")))
        .isTrue();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("InfraDefinition_SSH2")))
        .isTrue();
  }

  public static void assertTemplateWorkflowPhase(
      OrchestrationWorkflow orchestrationWorkflow, List<WorkflowPhase> workflowPhases) {
    assertThat(orchestrationWorkflow.getTemplatizedServiceIds()).isNullOrEmpty();
    assertThat(orchestrationWorkflow.getTemplatizedInfraDefinitionIds()).isNotNull().contains(INFRA_DEFINITION_ID);
    assertThat(orchestrationWorkflow).extracting("userVariables").isNotNull();
    assertThat(
        orchestrationWorkflow.getUserVariables().stream().anyMatch(variable -> variable.getName().equals("Service")))
        .isFalse();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("InfraDef_SSH")))
        .isTrue();
    assertThat(orchestrationWorkflow.getUserVariables().stream().anyMatch(
                   variable -> variable.getName().equals("Environment")))
        .isTrue();

    assertThat(workflowPhases).isNotNull().hasSize(1);
  }

  public static WorkflowPhase assertWorkflowPhaseTemplateExpressions(Workflow workflow, WorkflowPhase workflowPhase) {
    List<WorkflowPhase> workflowPhases3 =
        ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhases();
    WorkflowPhase workflowPhase3 = workflowPhases3.get(workflowPhases3.size() - 1);
    assertThat(workflowPhase3).isEqualToComparingOnlyGivenFields(workflowPhase, "uuid", "name");

    assertThat(workflowPhase3.getInfraDefinitionId()).isNotNull();
    assertThat(workflowPhase3.getTemplateExpressions())
        .isNotEmpty()
        .extracting(TemplateExpression::getFieldName)
        .contains("infraDefinitionId");
    assertThat(workflow.getOrchestrationWorkflow().getUserVariables())
        .extracting(Variable::obtainEntityType)
        .containsSequence(SERVICE, EntityType.INFRASTRUCTURE_DEFINITION);
    return workflowPhase3;
  }

  public static void assertTemplateStep(GraphNode preDeploymentStep) {
    assertThat(preDeploymentStep).isNotNull();
    assertThat(preDeploymentStep.getTemplateUuid()).isNotEmpty().isEqualTo(TEMPLATE_ID);
    assertThat(preDeploymentStep.getTemplateVersion()).isNotEmpty().isEqualTo(LATEST_TAG);

    List<Variable> templateVariables = preDeploymentStep.getTemplateVariables();
    assertThat(templateVariables).isNotEmpty();

    preDeploymentStep.setTemplateVersion("1");
    preDeploymentStep.setTemplateVariables(asList(aVariable().name("url").value("https://google.com").build()));
  }

  public static void assertPhaseNode(GraphNode updatedPhaseNode) {
    assertThat(updatedPhaseNode).isNotNull();
    assertThat(updatedPhaseNode.getTemplateUuid()).isNotEmpty().isEqualTo(TEMPLATE_ID);
    assertThat(updatedPhaseNode.getTemplateVersion()).isNotEmpty().isEqualTo("1");

    assertThat(updatedPhaseNode.getTemplateVariables()).isNotEmpty();

    assertThat(updatedPhaseNode.getTemplateVariables()).isNotEmpty().extracting(Variable::getName).contains("url");
    assertThat(updatedPhaseNode.getTemplateVariables())
        .isNotEmpty()
        .extracting(Variable::getValue)
        .contains("https://google.com");
    assertThat(updatedPhaseNode.getTemplateVariables())
        .isNotEmpty()
        .extracting(Variable::getValue)
        .doesNotContain("200 OK");
  }

  public static void assertWorkflowPhaseTemplateStep(GraphNode phaseNode) {
    assertThat(phaseNode).isNotNull();
    assertThat(phaseNode.getTemplateUuid()).isNotEmpty().isEqualTo(TEMPLATE_ID);
  }

  public static void assertPostDeployTemplateStep(GraphNode postDeploymentStep) {
    assertThat(postDeploymentStep).isNotNull();
    assertThat(postDeploymentStep.getTemplateUuid()).isNotEmpty().isEqualTo(TEMPLATE_ID);
    assertThat(postDeploymentStep.getTemplateVersion()).isNotEmpty().isEqualTo(LATEST_TAG);
  }

  public static void assertPreDeployTemplateStep(GraphNode preDeploymentStep) {
    assertThat(preDeploymentStep).isNotNull();
    assertThat(preDeploymentStep.getTemplateVariables()).isNotEmpty().extracting(Variable::getName).contains("url");
    assertThat(preDeploymentStep.getTemplateVariables())
        .isNotEmpty()
        .extracting(Variable::getValue)
        .contains("https://harness.io?q=${artifact.name}");
    assertThat(preDeploymentStep.getProperties()).isNotEmpty().containsKeys("url", "method", "assertion");
  }

  public static Workflow constructWfWithTrafficShiftSteps() {
    return aWorkflow()
        .uuid(generateUuid())
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .accountId(ACCOUNT_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .addWorkflowPhase(aWorkflowPhase()
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .serviceId(SERVICE_ID)
                                      .deploymentType(AMI)
                                      .phaseSteps(singletonList(
                                          aPhaseStep(PhaseStepType.SPOTINST_LISTENER_UPDATE, SPOTINST_LISTENER_UPDATE)
                                              .addStep(GraphNode.builder()
                                                           .type(StateType.SPOTINST_LISTENER_ALB_SHIFT.name())
                                                           .name(SPOTINST_ALB_SHIFT_LISTENER_UPDATE)
                                                           .build())
                                              .build()))
                                      .build())
                .withRollbackWorkflowPhaseIdMap(emptyMap())
                .build())
        .build();
  }

  public static Workflow constructCanaryWorkflowWithConcurrencyStrategy() {
    return aWorkflow()
        .uuid(generateUuid())
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .envId(ENV_ID)
        .accountId(ACCOUNT_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT)
                                            .addStep(GraphNode.builder()
                                                         .type(TERRAFORM_PROVISION.name())
                                                         .name("Terraform Provision")
                                                         .properties(ImmutableMap.<String, Object>builder().build())
                                                         .build())
                                            .build())
                .addWorkflowPhase(aWorkflowPhase()
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .serviceId(SERVICE_ID)
                                      .deploymentType(SSH)
                                      .phaseSteps(getSSHPhaseSteps())
                                      .build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                .withConcurrencyStrategy(ConcurrencyStrategy.builder().build())
                .build())
        .build();
  }

  public static Workflow constructBasicWorkflowWithThrottling(boolean withProvisionerStep) {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .serviceId(SERVICE_ID)
        .accountId(ACCOUNT_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .envId(ENV_ID)
        .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .infraDefinitionId(INFRA_DEFINITION_ID)
                                                         .serviceId(SERVICE_ID)
                                                         .deploymentType(SSH)
                                                         .phaseSteps(getAmiPhaseSteps(withProvisionerStep))
                                                         .build())
                                   .withConcurrencyStrategy(ConcurrencyStrategy.builder().build())
                                   .build())
        .build();
  }

  public static Workflow constructBasicWorkflowWithRollbackForAMI() {
    WorkflowPhase phase = getWorkflowPhaseForAMI();
    return aWorkflow()
        .uuid(WORKFLOW_ID)
        .defaultVersion(1)
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .serviceId(SERVICE_ID)
        .accountId(ACCOUNT_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .envId(ENV_ID)
        .orchestrationWorkflow(getBasicOrchestrationWorkflowForAMI(phase))
        .build();
  }

  private static WorkflowPhase getWorkflowPhaseForAMI() {
    return aWorkflowPhase()
        .infraDefinitionId(INFRA_DEFINITION_ID)
        .serviceId(SERVICE_ID)
        .deploymentType(AMI)
        .phaseSteps(getAmiPhaseSteps(false))
        .build();
  }

  private static BasicOrchestrationWorkflow getBasicOrchestrationWorkflowForAMI(WorkflowPhase phase) {
    WorkflowPhase rollbackPhase = rollbackWorkflowPhase(phase);
    Map<String, WorkflowPhase> rollbackMap = new HashMap<>();
    rollbackMap.put(phase.getUuid(), rollbackPhase);
    BasicOrchestrationWorkflow basicOrchestrationWorkflow =
        aBasicOrchestrationWorkflow()
            .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
            .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
            .addWorkflowPhase(phase)
            .withRollbackWorkflowPhaseIdMap(rollbackMap)
            .withConcurrencyStrategy(ConcurrencyStrategy.builder().build())
            .build();
    basicOrchestrationWorkflow.onSave();
    return basicOrchestrationWorkflow;
  }

  private static WorkflowPhase rollbackWorkflowPhase(WorkflowPhase workflowPhase) {
    return aWorkflowPhase()
        .name("Rollback" + workflowPhase.getName())
        .deploymentType(workflowPhase.getDeploymentType())
        .rollback(true)
        .phaseNameForRollback(workflowPhase.getName())
        .serviceId(workflowPhase.getServiceId())
        .computeProviderId(workflowPhase.getComputeProviderId())
        .infraMappingId(workflowPhase.getInfraMappingId())
        .infraMappingName(workflowPhase.getInfraMappingName())
        .infraDefinitionId(workflowPhase.getInfraDefinitionId())
        .phaseSteps(asList(aPhaseStep(AMI_DEPLOY_AUTOSCALING_GROUP, "Rollback Service")
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(AWS_AMI_SERVICE_ROLLBACK.name())
                                            .name("Rollback AutoScaling Group")
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, "Verify Service")
                .withRollback(true)
                .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(PhaseStepType.WRAP_UP, "Wrap Up").withRollback(true).build()))
        .build();
  }

  private static List<PhaseStep> getAmiPhaseSteps(boolean withProvisionerStep) {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    if (withProvisionerStep) {
      phaseSteps.add(aPhaseStep(PhaseStepType.PROVISION_INFRASTRUCTURE, "Provision InfraStructure")
                         .addStep(GraphNode.builder()
                                      .type(CLOUD_FORMATION_CREATE_STACK.name())
                                      .name("CloudFormation Create Stack")
                                      .properties(ImmutableMap.<String, Object>builder().build())
                                      .build())
                         .build());
    }
    phaseSteps.add(aPhaseStep(AMI_AUTOSCALING_GROUP_SETUP, "Setup AutoScaling Group")
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(AWS_AMI_SERVICE_SETUP.name())
                                    .name("AWS AutoScaling Group Setup")
                                    .properties(new HashMap<>())
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(AMI_DEPLOY_AUTOSCALING_GROUP, DEPLOY_SERVICE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(AWS_AMI_SERVICE_DEPLOY.name())
                                    .name("Upgrade AutoScaling Group")
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, "Verify Service").build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, "Wrap Up").build());
    return phaseSteps;
  }

  private static List<PhaseStep> getSSHPhaseSteps() {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    phaseSteps.add(aPhaseStep(INFRASTRUCTURE_NODE, "Prepare Infra")
                       .addStep(GraphNode.builder()
                                    .type(AWS_NODE_SELECT.name())
                                    .name("Select Nodes")
                                    .properties(ImmutableMap.<String, Object>builder()
                                                    .put("specificHosts", false)
                                                    .put("instanceCount", 1)
                                                    .put("excludeSelectedHostsFromFuturePhases", true)
                                                    .build())
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.DISABLE_SERVICE, "Disable Service").build());
    phaseSteps.add(aPhaseStep(PhaseStepType.DEPLOY_SERVICE, DEPLOY_SERVICE).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.ENABLE_SERVICE, "Enable Service").build());
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE.name()).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.WRAP_UP, "Wrap UP").build());
    return phaseSteps;
  }
}
