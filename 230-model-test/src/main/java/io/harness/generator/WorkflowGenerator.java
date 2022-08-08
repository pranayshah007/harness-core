/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator;

import static io.harness.beans.SweepingOutputInstance.Scope.PHASE;
import static io.harness.beans.SweepingOutputInstance.Scope.PIPELINE;
import static io.harness.beans.SweepingOutputInstance.Scope.WORKFLOW;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.generator.ResourceConstraintGenerator.ResourceConstraints.GENERIC_ASAP_TEST;
import static io.harness.generator.SettingGenerator.Settings.HARNESS_JENKINS_CONNECTOR;
import static io.harness.govern.Switch.unhandled;

import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.DEPLOY_SERVICE;
import static software.wings.beans.PhaseStepType.DISABLE_SERVICE;
import static software.wings.beans.PhaseStepType.ENABLE_SERVICE;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PREPARE_STEPS;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.RollingOrchestrationWorkflow.RollingOrchestrationWorkflowBuilder.aRollingOrchestrationWorkflow;
import static software.wings.beans.TaskType.JENKINS;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.VariableType.ARTIFACT;
import static software.wings.beans.VariableType.TEXT;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.INFRASTRUCTURE_NODE_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SELECT_NODE_NAME;
import static software.wings.sm.StateType.DC_NODE_SELECT;
import static software.wings.sm.StateType.HTTP;
import static software.wings.sm.StateType.RESOURCE_CONSTRAINT;
import static software.wings.sm.StateType.TERRAFORM_DESTROY;
import static software.wings.sm.StateType.TERRAFORM_PROVISION;
import static software.wings.sm.StepType.COMMAND;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.ResourceConstraint;
import io.harness.beans.WorkflowType;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.InfrastructureProvisionerGenerator.InfrastructureProvisioners;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.generator.artifactstream.ArtifactStreamManager.ArtifactStreams;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.beans.Application;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.BasicOrchestrationWorkflow;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.template.Template;
import software.wings.beans.template.command.ShellScriptTemplate;
import software.wings.dl.WingsPersistence;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateType;
import software.wings.sm.states.HoldingScope;
import software.wings.sm.states.HttpState.HttpStateKeys;
import software.wings.sm.states.JenkinsState;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Singleton
public class WorkflowGenerator {
  @Inject private WorkflowService workflowService;
  @Inject private ServiceResourceService serviceResourceService;

  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private OrchestrationWorkflowGenerator orchestrationWorkflowGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private InfrastructureProvisionerGenerator infrastructureProvisionerGenerator;
  @Inject private ResourceConstraintGenerator resourceConstraintGenerator;
  @Inject private ArtifactStreamManager artifactStreamManager;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private ScmSecret scmSecret;
  @Inject private SecretGenerator secretGenerator;
  @Inject private SettingGenerator settingGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private TemplateGenerator templateGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;

  /**
   * The constant COLLECT_ARTIFACT.
   */
  private static final String COLLECT_ARTIFACT = "Collect Artifact";

  public enum Workflows {
    BASIC_SIMPLE,
    BASIC_10_NODES,
    ROLLING_10_NODES,
    TERRAFORM,
    PERMANENTLY_BLOCKED_RESOURCE_CONSTRAINT,
    BUILD_JENKINS,
    BUILD_SHELL_SCRIPT,
    BASIC_ECS,
    BASIC_SIMPLE_MULTI_ARTIFACT,
    K8S_ROLLING
  }

  public Workflow ensurePredefined(Randomizer.Seed seed, Owners owners, Workflows predefined) {
    switch (predefined) {
      case BASIC_SIMPLE:
        return ensureBasicSimple(seed, owners);
      case BASIC_10_NODES:
        return ensureBasic10Nodes(seed, owners);
      case ROLLING_10_NODES:
        return ensureRolling10Nodes(seed, owners);
      case TERRAFORM:
        return ensureTerraform(seed, owners);
      case PERMANENTLY_BLOCKED_RESOURCE_CONSTRAINT:
        return ensurePermanentlyBlockedResourceConstraint(seed, owners);
      case BUILD_JENKINS:
        return ensureBuildJenkins(seed, owners);
      case BUILD_SHELL_SCRIPT:
        return ensureBuildShellScript(seed, owners);
      case BASIC_SIMPLE_MULTI_ARTIFACT:
        return ensureBasicSimpleMultiArtifact(seed, owners);
      case K8S_ROLLING:
        return ensureK8sRolling(seed, owners);
      default:
        unhandled(predefined);
    }

    return null;
  }

  private Workflow ensureBasicSimple(Randomizer.Seed seed, Owners owners) {
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AWS_SSH_TEST);

    return ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Basic - simple")
            .workflowType(WorkflowType.ORCHESTRATION)
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                       .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                       .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                       .build())
            .build());
  }

  private Workflow ensureBasic10Nodes(Randomizer.Seed seed, Owners owners) {
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AWS_SSH_TEST);

    Workflow workflow = ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Basic - 10 nodes")
            .workflowType(WorkflowType.ORCHESTRATION)
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                       .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                       .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                       .build())
            .build());

    workflow = postProcess(workflow, PostProcessInfo.builder().selectNodeCount(10).build());
    return workflow;
  }

  private Workflow ensureRolling10Nodes(Randomizer.Seed seed, Owners owners) {
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AWS_SSH_TEST);

    Workflow workflow = ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Rolling - 10 nodes")
            .workflowType(WorkflowType.ORCHESTRATION)
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .orchestrationWorkflow(aRollingOrchestrationWorkflow()
                                       .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                       .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                       .build())
            .build());

    workflow = postProcess(workflow, PostProcessInfo.builder().selectNodeCount(2).build());
    return workflow;
  }

  private Workflow ensureTerraform(Randomizer.Seed seed, Owners owners) {
    owners.obtainEnvironment(() -> environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST));

    final InfrastructureProvisioner infrastructureProvisioner =
        infrastructureProvisionerGenerator.ensurePredefined(seed, owners, InfrastructureProvisioners.TERRAFORM_TEST);

    owners.obtainService(()
                             -> serviceResourceService.getWithDetails(infrastructureProvisioner.getAppId(),
                                 infrastructureProvisioner.getMappingBlueprints().iterator().next().getServiceId()));

    final SecretName awsPlaygroundAccessKeyName = SecretName.builder().value("aws_playground_access_key").build();
    final String awsPlaygroundAccessKey = scmSecret.decryptToString(awsPlaygroundAccessKeyName);
    final SecretName awsPlaygroundSecretKeyName = SecretName.builder().value("aws_playground_secret_key").build();
    final String awsPlaygroundSecretKeyId = secretGenerator.ensureStored(owners, awsPlaygroundSecretKeyName);

    // TODO: this is temporary adding second key, to workaround bug in the UI
    final SecretName terraformPasswordName = SecretName.builder().value("terraform_password").build();
    secretGenerator.ensureStored(owners, terraformPasswordName);

    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AWS_SSH_TEST);

    return workflowGenerator.ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Terraform provision")
            .workflowType(WorkflowType.ORCHESTRATION)
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow()
                    .withPreDeploymentSteps(
                        aPhaseStep(PRE_DEPLOYMENT)
                            .addStep(GraphNode.builder()
                                         .type(TERRAFORM_PROVISION.name())
                                         .name("Provision infra")
                                         .properties(ImmutableMap.<String, Object>builder()
                                                         .put("provisionerId", infrastructureProvisioner.getUuid())
                                                         .put("variables",
                                                             asList(ImmutableMap.of("name", "access_key", "value",
                                                                        awsPlaygroundAccessKey, "valueType",
                                                                        ServiceVariableType.TEXT.name()),
                                                                 ImmutableMap.of("name", "secret_key", "value",
                                                                     awsPlaygroundSecretKeyId, "valueType",
                                                                     ServiceVariableType.ENCRYPTED_TEXT.name())))
                                                         .build())
                                         .build())
                            .build())
                    .withPostDeploymentSteps(
                        aPhaseStep(POST_DEPLOYMENT)
                            .addStep(GraphNode.builder()
                                         .type(TERRAFORM_DESTROY.name())
                                         .name("Deprovision infra")
                                         .properties(ImmutableMap.<String, Object>builder()
                                                         .put("provisionerId", infrastructureProvisioner.getUuid())
                                                         .build())
                                         .build())
                            .build())
                    .build())
            .build());
  }

  private Workflow ensurePermanentlyBlockedResourceConstraint(Randomizer.Seed seed, Owners owners) {
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AWS_SSH_TEST);

    final ResourceConstraint asapResourceConstraint =
        resourceConstraintGenerator.ensurePredefined(seed, owners, GENERIC_ASAP_TEST);

    return ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Resource constraint")
            .workflowType(WorkflowType.ORCHESTRATION)
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .orchestrationWorkflow(
                aBasicOrchestrationWorkflow()
                    .withPreDeploymentSteps(
                        aPhaseStep(PRE_DEPLOYMENT)
                            .addStep(GraphNode.builder()
                                         .type(RESOURCE_CONSTRAINT.name())
                                         .name(asapResourceConstraint.getName() + " 1")
                                         .properties(ImmutableMap.<String, Object>builder()
                                                         .put("resourceConstraintId", asapResourceConstraint.getUuid())
                                                         .put("permits", 6)
                                                         .put("holdingScope", HoldingScope.WORKFLOW.name())
                                                         .build())
                                         .build())
                            .addStep(GraphNode.builder()
                                         .type(RESOURCE_CONSTRAINT.name())
                                         .name(asapResourceConstraint.getName() + " 2")
                                         .properties(ImmutableMap.<String, Object>builder()
                                                         .put("resourceConstraintId", asapResourceConstraint.getUuid())
                                                         .put("permits", 6)
                                                         .put("holdingScope", HoldingScope.WORKFLOW.name())
                                                         .build())
                                         .build())
                            .build())
                    .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                    .build())
            .build());
  }

  private Workflow ensureBuildJenkins(Seed seed, Owners owners) {
    // Ensure artifact stream

    // TODO: Change it to Docker ArtifactStream
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_ECHO_WAR);

    SettingAttribute jenkinsConfig = settingGenerator.ensurePredefined(seed, owners, HARNESS_JENKINS_CONNECTOR);

    return ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Build Jenkins")
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(
                aBuildOrchestrationWorkflow()
                    .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                    .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT)
                                                 .addStep(getHTTPNode("pipeline"))
                                                 .addStep(getHTTPNode("workflow"))
                                                 .addStep(getHTTPNode("phase"))
                                                 .build())
                    .addWorkflowPhase(
                        aWorkflowPhase()
                            .phaseSteps(asList(aPhaseStep(PREPARE_STEPS, WorkflowServiceHelper.PREPARE_STEPS).build(),
                                aPhaseStep(PhaseStepType.COLLECT_ARTIFACT, COLLECT_ARTIFACT)
                                    .addStep(
                                        GraphNode.builder()
                                            .id(generateUuid())
                                            .type(JENKINS.name())
                                            .name("Jenkins - pipeline")
                                            .properties(
                                                ImmutableMap.<String, Object>builder()
                                                    .put(JenkinsState.JENKINS_CONFIG_ID_KEY, jenkinsConfig.getUuid())
                                                    .put(JenkinsState.JOB_NAME_KEY, "build-description-setter")
                                                    .put(JenkinsState.SWEEPING_OUTPUT_NAME_KEY, "pipeline")
                                                    .put(JenkinsState.SWEEPING_OUTPUT_SCOPE_KEY, PIPELINE)
                                                    .build())
                                            .build())
                                    .addStep(
                                        GraphNode.builder()
                                            .id(generateUuid())
                                            .type(JENKINS.name())
                                            .name("Jenkins - workflow")
                                            .properties(
                                                ImmutableMap.<String, Object>builder()
                                                    .put(JenkinsState.JENKINS_CONFIG_ID_KEY, jenkinsConfig.getUuid())
                                                    .put(JenkinsState.JOB_NAME_KEY, "build-description-setter")
                                                    .put(JenkinsState.SWEEPING_OUTPUT_NAME_KEY, "workflow")
                                                    .put(JenkinsState.SWEEPING_OUTPUT_SCOPE_KEY, WORKFLOW)
                                                    .build())
                                            .build())
                                    .addStep(
                                        GraphNode.builder()
                                            .id(generateUuid())
                                            .type(JENKINS.name())
                                            .name("Jenkins - phase")
                                            .properties(
                                                ImmutableMap.<String, Object>builder()
                                                    .put(JenkinsState.JENKINS_CONFIG_ID_KEY, jenkinsConfig.getUuid())
                                                    .put(JenkinsState.JOB_NAME_KEY, "build-description-setter")
                                                    .put(JenkinsState.SWEEPING_OUTPUT_NAME_KEY, "phase")
                                                    .put(JenkinsState.SWEEPING_OUTPUT_SCOPE_KEY, PHASE)
                                                    .build())
                                            .build())
                                    .build(),
                                aPhaseStep(WRAP_UP, WorkflowServiceHelper.WRAP_UP)
                                    .addStep(getHTTPNode("pipeline"))
                                    .addStep(getHTTPNode("workflow"))
                                    .addStep(getHTTPNode("phase"))
                                    .build()))
                            .build())
                    .build())
            .build());
  }

  private GraphNode getHTTPNode(String scope) {
    return GraphNode.builder()
        .id(generateUuid())
        .type(HTTP.name())
        .name("HTTP - " + scope)
        .properties(
            ImmutableMap.<String, Object>builder()
                .put(HttpStateKeys.url, format("http://www.google.com?h=${context.output(\"%s\").buildNumber}", scope))
                .put(HttpStateKeys.method, "GET")
                .build())
        .build();
  }

  private Workflow ensureBuildShellScript(Seed seed, Owners owners) {
    Template shellScriptTemplate =
        templateGenerator.ensurePredefined(seed, owners, TemplateGenerator.Templates.SHELL_SCRIPT);
    ShellScriptTemplate templateObject = (ShellScriptTemplate) shellScriptTemplate.getTemplateObject();
    Map<String, Object> properties = new HashMap<>();
    properties.put("scriptType", templateObject.getScriptType());
    properties.put("scriptString", templateObject.getScriptString());
    properties.put("outputVars", templateObject.getOutputVars());
    properties.put("connectionType", "SSH");
    properties.put("executeOnDelegate", true);

    return ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Shell Script Build Workflow")
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(
                aBuildOrchestrationWorkflow()
                    .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                    .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                    .addWorkflowPhase(
                        aWorkflowPhase()
                            .phaseSteps(asList(aPhaseStep(PREPARE_STEPS, WorkflowServiceHelper.PREPARE_STEPS).build(),
                                aPhaseStep(PhaseStepType.COLLECT_ARTIFACT, COLLECT_ARTIFACT)
                                    .addStep(GraphNode.builder()
                                                 .id(generateUuid())
                                                 .type(StateType.SHELL_SCRIPT.name())
                                                 .name(shellScriptTemplate.getName())
                                                 .properties(properties)
                                                 .templateVariables(shellScriptTemplate.getVariables())
                                                 .templateUuid(shellScriptTemplate.getUuid())
                                                 .templateVersion("latest")
                                                 .build())
                                    .build(),
                                aPhaseStep(WRAP_UP, WorkflowServiceHelper.WRAP_UP).build()))
                            .build())
                    .build())
            .build());
  }

  private Workflow ensureBasicSimpleMultiArtifact(Randomizer.Seed seed, Owners owners) {
    templateGenerator.ensureServiceCommandTemplate(seed, owners, TemplateGenerator.Templates.SERVICE_COMMAND_1);
    templateGenerator.ensureServiceCommandTemplate(seed, owners, TemplateGenerator.Templates.SERVICE_COMMAND_2);

    Template multiArtifactCommandTemplate =
        templateGenerator.ensurePredefined(seed, owners, TemplateGenerator.Templates.MULTI_ARTIFACT_COMMAND_TEMPLATE);

    Service savedService = serviceGenerator.ensurePredefined(seed, owners, Services.MULTI_ARTIFACT_FUNCTIONAL_TEST);
    Environment savedEnvironment = environmentGenerator.ensurePredefined(seed, owners, Environments.FUNCTIONAL_TEST);
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitions.MULTI_ARTIFACT_AWS_SSH_FUNCTIONAL_TEST);

    Map<String, Object> selectNodeProperties = new HashMap<>();
    selectNodeProperties.put("specificHosts", false);
    selectNodeProperties.put("instanceCount", 1);
    selectNodeProperties.put("excludeSelectedHostsFromFuturePhases", false);
    List<PhaseStep> phaseSteps = new ArrayList<>();

    phaseSteps.add(aPhaseStep(INFRASTRUCTURE_NODE, WorkflowServiceHelper.INFRASTRUCTURE_NODE_NAME)
                       .withPhaseStepType(INFRASTRUCTURE_NODE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .name(SELECT_NODE_NAME)
                                    .type(DC_NODE_SELECT.name())
                                    .properties(selectNodeProperties)
                                    .build())
                       .build());
    phaseSteps.add(
        aPhaseStep(DISABLE_SERVICE, WorkflowServiceHelper.DISABLE_SERVICE).withPhaseStepType(DISABLE_SERVICE).build());

    Map<String, Variable> variableMap1 = new HashMap<>();
    variableMap1.put("t_artifact1", aVariable().name("m_artifact1").type(ARTIFACT).build());
    variableMap1.put("var1", aVariable().name("var1").type(TEXT).build());

    Map<String, Variable> variableMap2 = new HashMap<>();
    variableMap2.put("t_artifact1", aVariable().name("m_artifact2").type(ARTIFACT).build());
    variableMap2.put("var2", aVariable().name("var2").type(TEXT).build());

    Map<String, Object> commandProperties = new HashMap<>();
    commandProperties.put("commandName", "MyCommand");
    commandProperties.put("commandType", "OTHER");
    commandProperties.put("executeOnDelegate", "true");
    phaseSteps.add(
        aPhaseStep(DEPLOY_SERVICE, WorkflowServiceHelper.DEPLOY_SERVICE)
            .withPhaseStepType(DEPLOY_SERVICE)
            .addStep(GraphNode.builder()
                         .id(generateUuid())
                         .name("MyCommand")
                         .type(COMMAND.name())
                         .properties(commandProperties)
                         .templateUuid(multiArtifactCommandTemplate.getUuid())
                         .templateVersion(String.valueOf(multiArtifactCommandTemplate.getVersion()))
                         .templateVariables(asList(
                             aVariable().type(ARTIFACT).name("m_artifact1").value("${artifacts.artifact1}").build(),
                             aVariable().type(TEXT).name("var1").value("Another Jane Doe").build(),
                             aVariable().type(ARTIFACT).name("m_artifact2").value("${artifacts.artifact2}").build(),
                             aVariable().type(TEXT).name("var2").value("Another John Doe").build()))
                         .build())
            .build());
    phaseSteps.add(
        aPhaseStep(ENABLE_SERVICE, WorkflowServiceHelper.ENABLE_SERVICE).withPhaseStepType(ENABLE_SERVICE).build());
    phaseSteps.add(
        aPhaseStep(VERIFY_SERVICE, WorkflowServiceHelper.VERIFY_SERVICE).withPhaseStepType(VERIFY_SERVICE).build());
    phaseSteps.add(aPhaseStep(WRAP_UP, WorkflowServiceHelper.WRAP_UP).withPhaseStepType(PhaseStepType.WRAP_UP).build());

    return ensureWorkflow(seed, owners,
        aWorkflow()
            .name("MultiArtifact - Basic - simple")
            .serviceId(savedService.getUuid())
            .envId(savedEnvironment.getUuid())
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                       .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT)
                                                                   .addStep(GraphNode.builder()
                                                                                .id(generateUuid())
                                                                                .name("Artifact Check")
                                                                                .type(StateType.ARTIFACT_CHECK.name())
                                                                                .build())
                                                                   .build())
                                       .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                       .addWorkflowPhase(aWorkflowPhase()
                                                             .name("Phase1")
                                                             .serviceId(savedService.getUuid())
                                                             .deploymentType(SSH)
                                                             .infraDefinitionId(infrastructureDefinition.getUuid())
                                                             .phaseSteps(phaseSteps)
                                                             .build())
                                       .build())
            .build());
  }

  private Workflow ensureK8sRolling(Randomizer.Seed seed, Owners owners) {
    Service service = owners.obtainService(() -> serviceGenerator.ensurePredefined(seed, owners, Services.K8S_V2_TEST));
    Environment environment =
        owners.obtainEnvironment(() -> environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST));
    InfrastructureDefinition infra =
        owners.obtainInfrastructureDefinition(()
                                                  -> infrastructureDefinitionGenerator.ensurePredefined(
                                                      seed, owners, InfrastructureDefinitions.K8S_ROLLING_TEST));
    Workflow workflow =
        aWorkflow()
            .name(format("Rolling %10s-%10s-%10s", service.getName(), environment.getName(), infra.getName()))
            .appId(service.getAppId())
            .envId(environment.getUuid())
            .serviceId(service.getUuid())
            .envId(environment.getUuid())
            .infraDefinitionId(infra.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow()
                    .withOrchestrationWorkflowType(OrchestrationWorkflowType.ROLLING)
                    .withUserVariables(
                        asList(aVariable().name("workloadName").value("default-workload-name").type(TEXT).build(),
                            aVariable().name("valueOverride").type(TEXT).build(),
                            aVariable().name("value1Override").type(TEXT).build(),
                            aVariable().name("value2Override").type(TEXT).build(),
                            aVariable().name("value3Override").type(TEXT).build(),
                            aVariable().name("value4Override").type(TEXT).build()))
                    .build())
            .build();
    return ensureWorkflow(seed, owners, workflow);
  }

  public Workflow exists(Workflow workflow) {
    return workflowService.readWorkflowByName(workflow.getAppId(), workflow.getName());
  }

  public Workflow ensureWorkflow(Randomizer.Seed seed, OwnerManager.Owners owners, Workflow workflow) {
    WorkflowBuilder builder = aWorkflow();

    if (workflow != null && workflow.getAppId() != null) {
      builder.appId(workflow.getAppId());
    } else {
      final Application application = owners.obtainApplication(
          () -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
      builder.appId(application.getUuid());
    }

    if (workflow != null && workflow.getName() != null) {
      builder.name(workflow.getName());
    } else {
      throw new UnsupportedOperationException();
    }

    Workflow existing = exists(builder.build());
    if (existing != null) {
      return existing;
    }

    if (workflow.getEnvId() != null) {
      builder.envId(workflow.getEnvId());
    } else {
      OrchestrationWorkflowType orchestrationWorkflowType =
          workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType();
      if (OrchestrationWorkflowType.BUILD != orchestrationWorkflowType) {
        Environment environment = owners.obtainEnvironment(
            () -> environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST));
        builder.envId(environment.getUuid());
      }
    }

    if (workflow.getServiceId() != null) {
      builder.serviceId(workflow.getServiceId());
    } else {
      OrchestrationWorkflowType orchestrationWorkflowType =
          workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType();
      if (OrchestrationWorkflowType.BUILD != orchestrationWorkflowType) {
        Service service =
            owners.obtainService(() -> serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST));
        builder.serviceId(service.getUuid());
      }
    }

    if (workflow.getWorkflowType() != null) {
      builder.workflowType(workflow.getWorkflowType());
    } else {
      throw new UnsupportedOperationException();
    }

    if (workflow.getOrchestrationWorkflow() != null) {
      builder.orchestrationWorkflow(workflow.getOrchestrationWorkflow());
    } else {
      throw new UnsupportedOperationException();
    }

    if (workflow.getInfraMappingId() != null) {
      builder.infraMappingId(workflow.getInfraMappingId());
    }

    if (isNotEmpty(workflow.getInfraDefinitionId())) {
      builder.infraDefinitionId(workflow.getInfraDefinitionId());
    }
    if (workflow.getCreatedBy() != null) {
      builder.createdBy(workflow.getCreatedBy());
    } else {
      builder.createdBy(owners.obtainUser());
    }

    if (workflow.getTemplateExpressions() != null) {
      builder.templateExpressions(workflow.getTemplateExpressions());
    }

    builder.templatized(workflow.isTemplatized());

    final Workflow finalWorkflow = builder.build();

    return GeneratorUtils.suppressDuplicateException(
        () -> workflowService.createWorkflow(finalWorkflow), () -> exists(finalWorkflow));
  }

  @Value
  @Builder
  public static class PostProcessInfo {
    private Integer selectNodeCount;
  }

  public Workflow postProcess(Workflow workflow, PostProcessInfo params) {
    if (params.getSelectNodeCount() != null) {
      if (workflow.getOrchestrationWorkflow() instanceof BasicOrchestrationWorkflow) {
        final GraphNode selectNodes =
            ((BasicOrchestrationWorkflow) workflow.getOrchestrationWorkflow())
                .getGraph()
                .getSubworkflows()
                .entrySet()
                .stream()
                .filter(entry -> INFRASTRUCTURE_NODE_NAME.equals(entry.getValue().getGraphName()))
                .findFirst()
                .orElseGet(null)
                .getValue()
                .getNodes()
                .stream()
                .filter(entry -> SELECT_NODE_NAME.equals(entry.getName()))
                .findFirst()
                .orElseGet(null);

        selectNodes.getProperties().put("instanceCount", params.getSelectNodeCount());
      }
    }

    return workflowService.updateWorkflow(workflow, false);
  }

  public List<ArtifactVariable> getArtifactVariablesFromDeploymentMetadataForWorkflow(
      String appId, String orchestrationId) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setOrchestrationId(orchestrationId);
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    DeploymentMetadata deploymentMetadata = workflowExecutionService.fetchDeploymentMetadata(appId, executionArgs);
    return deploymentMetadata.getArtifactVariables();
  }
}
