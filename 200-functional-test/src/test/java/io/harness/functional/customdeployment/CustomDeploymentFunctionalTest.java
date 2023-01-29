/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.customdeployment;

import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.CUSTOM_DEPLOYMENT_PHASE_STEP;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.VariableType.TEXT;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_PREFIX;
import static software.wings.sm.StateType.CUSTOM_DEPLOYMENT_FETCH_INSTANCES;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.SecretGenerator;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.TemplateGenerator;
import io.harness.rule.Owner;
import io.harness.scm.SecretName;
import io.harness.shell.ScriptType;
import io.harness.testframework.restutils.ArtifactStreamRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.ShellScriptTemplate;
import software.wings.beans.template.deploymenttype.CustomDeploymentTypeTemplate;
import software.wings.exception.TemplateException;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.template.TemplateService;
import software.wings.sm.StateType;
import software.wings.sm.states.customdeployment.InstanceFetchState.InstanceFetchStateKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private TemplateGenerator templateGenerator;
  @Inject private SecretGenerator secretGenerator;
  @Inject private TemplateService templateService;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;

  final String FETCH_INSTANCE_SCRIPT_CONSTANT = "Run FetchInstanceScript";
  final String WRAP_UP_CONSTANT = "Wrap Up";
  final String PRE_DEPLOYMENT_CONSTANT = "Pre-Deployment";
  final String POST_DEPLOYMENT_CONSTANT = "Post-Deployment";

  final String resourcePath = "200-functional-test/src/test/resources/io/harness/functional/customDeployment";
  final String fetchInstanceScript = "FetchInstanceScript";
  final String checkVarsScript = "CheckVariablesScript";

  private Application application;
  private Service service;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;
  private Template template;

  List<Variable> secretInfraVariables = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
    resetCache(owners.obtainAccount().getUuid());

    CustomDeploymentTypeTemplate customDeploymentTypeTemplate =
        CustomDeploymentTypeTemplate.builder()
            .hostAttributes(new HashMap<String, String>() {
              { put("hostname", "ip"); }
            })
            .fetchInstanceScript(readFileContent(fetchInstanceScript, resourcePath))
            .hostObjectArrayPath("instances")
            .build();

    template = templateGenerator.ensureCustomDeploymentTemplate(
        seed, owners, customDeploymentTypeTemplate, "Test Custom Deployment Workflow ", secretInfraVariables);

    service = serviceGenerator.ensurePredefinedCustomDeployment(
        seed, owners, template.getUuid(), "Custom Deployment Service");
    assertThat(service).isNotNull();
    environment = environmentGenerator.ensurePredefined(seed, owners, EnvironmentGenerator.Environments.GENERIC_TEST);
    assertThat(environment).isNotNull();
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefinedCustomDeployment(
        seed, owners, template.getUuid(), "CustomDeployment InfraStructureDefinition");
    assertThat(infrastructureDefinition).isNotNull();

    secretGenerator.ensureStored(owners, SecretName.builder().value("pcf_password").build());
    assertThat(application).isNotNull();
    secretInfraVariables.add(aVariable()
                                 .type(TEXT)
                                 .name("secretKey")
                                 .mandatory(true)
                                 .value("${secrets.getValue(\"pcf_password\")}")
                                 .description("aws secret key")
                                 .build());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testCustomDeploymentWorkflowSuccess() throws IOException {
    final String accountId = owners.obtainAccount().getUuid();
    resetCache(accountId);

    Workflow workflow = getBasicCustomDeploymentTypeWorkflow(template.getUuid(), "Basic Workflow");
    Workflow savedWorkflow = createAndAssertWorkflow(workflow);
    assertThat(savedWorkflow).isNotNull();

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getUuid(), environment.getUuid(),
        getExecutionArgs(savedWorkflow, environment.getUuid(), service.getUuid()));

    log.info("Custom Deployment's Execution status: " + workflowExecution.getStatus());
    logStateExecutionInstanceErrors(workflowExecution);
    assertInstanceCount(workflowExecution.getStatus(), application.getUuid(),
        workflowExecution.getInfraMappingIds().get(0), workflowExecution.getInfraDefinitionIds().get(0));

    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(workflowExecution.getServiceExecutionSummaries().get(0).getInstancesCount()).isEqualTo(2);
    assertThat(workflowExecution.getServiceExecutionSummaries()
                   .get(0)
                   .getInstanceStatusSummaries()
                   .get(0)
                   .getInstanceElement()
                   .getHostName())
        .isEqualTo("1.1");
    assertThat(workflowExecution.getServiceExecutionSummaries()
                   .get(0)
                   .getInstanceStatusSummaries()
                   .get(1)
                   .getInstanceElement()
                   .getHostName())
        .isEqualTo("2.2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testEmptyFetchInstanceSciptFail() throws IOException {
    resetCache(owners.obtainAccount().getUuid());
    CustomDeploymentTypeTemplate customDeploymentTypeTemplate = CustomDeploymentTypeTemplate.builder()
                                                                    .hostAttributes(new HashMap<String, String>() {
                                                                      { put("var", "varValue"); }
                                                                    })
                                                                    .fetchInstanceScript("")
                                                                    .hostObjectArrayPath("arraypath")
                                                                    .build();
    Template template = templateGenerator.ensureCustomDeploymentTemplate(
        seed, owners, customDeploymentTypeTemplate, "Test CD EmptyFetchInstanceScript", secretInfraVariables);

    service = serviceGenerator.ensurePredefinedCustomDeployment(
        seed, owners, template.getUuid(), "CustomDeployment Service Empty Script");
    assertThat(service).isNotNull();
    environment = environmentGenerator.ensurePredefined(seed, owners, EnvironmentGenerator.Environments.GENERIC_TEST);
    assertThat(environment).isNotNull();
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefinedCustomDeployment(
        seed, owners, template.getUuid(), "CustomDeployment InfraStructureDefinition Empty Script");
    infrastructureDefinition.setDeploymentTypeTemplateId(template.getUuid());
    assertThat(infrastructureDefinition).isNotNull();

    Workflow workflow =
        getBasicCustomDeploymentTypeWorkflow(template.getUuid(), "Workflow with Empty FetchInstanceScript");

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();

    List<GraphNode> steps =
        Arrays.asList(canaryOrchestrationWorkflow.getWorkflowPhases().get(0).getPhaseSteps().get(0).getSteps().get(0));

    Workflow workflowWithRollbackStep = addRollbackToCustomDeploymentWorkflow(workflow, steps);

    Workflow savedWorkflow = createAndAssertWorkflow(workflowWithRollbackStep);
    assertThat(savedWorkflow).isNotNull();

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getUuid(), environment.getUuid(),
        getExecutionArgs(savedWorkflow, environment.getUuid(), service.getUuid()));

    logStateExecutionInstanceErrors(workflowExecution);
    log.info("Custom Deployment's Execution status: " + workflowExecution.getStatus());

    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(workflowExecution.getRollbackDuration()).isNotNull();
    assertThat(workflowExecution.getServiceExecutionSummaries().get(0).getInstancesCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testTemplateLinkedWithInfraNotGetDeleted() throws IOException {
    assertThatThrownBy(() -> templateService.delete(owners.obtainAccount().getUuid(), template.getUuid()))
        .isInstanceOf(TemplateException.class);
    assertThatThrownBy(() -> templateService.delete(owners.obtainAccount().getUuid(), template.getUuid()))
        .hasMessage("Template : [Test Custom Deployment Workflow ] couldn't be deleted");
  }

  private Workflow createAndAssertWorkflow(Workflow workflow) {
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getUuid()).isNotEmpty();
    assertThat(savedWorkflow.getWorkflowType()).isEqualTo(ORCHESTRATION);
    return savedWorkflow;
  }

  public WorkflowPhase generateRollbackWorkflowPhaseForCustomDeployment(
      WorkflowPhase workflowPhase, List<GraphNode> stepsInRollbackPhase) {
    final List<PhaseStep> phaseSteps = new ArrayList<>();
    phaseSteps.add(aPhaseStep(CUSTOM_DEPLOYMENT_PHASE_STEP, WorkflowServiceHelper.ROLLBACK_SERVICE)
                       .addAllSteps(stepsInRollbackPhase)
                       .build());
    phaseSteps.forEach(step -> step.setRollback(true));

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

    return workflowPhaseBuilder.phaseSteps(phaseSteps).build();
  }

  public Workflow addRollbackToCustomDeploymentWorkflow(Workflow workflow, List<GraphNode> steps) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
    List<WorkflowPhase> workflowPhases = canaryOrchestrationWorkflow.getWorkflowPhases();

    workflowPhases.forEach(workflowPhase -> {
      WorkflowPhase rollbackWorkflowPhase = generateRollbackWorkflowPhaseForCustomDeployment(workflowPhase, steps);
      canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap().put(workflowPhase.getUuid(), rollbackWorkflowPhase);
    });

    return workflow;
  }

  public Template generateShellScriptforWorkflow(String resourcePathForScript, String filePathForScript)
      throws IOException {
    ShellScriptTemplate templateObject = ShellScriptTemplate.builder()
                                             .scriptType(ScriptType.BASH.name())
                                             .scriptString(readFileContent(filePathForScript, resourcePathForScript))
                                             .build();

    return templateGenerator.ensureTemplate(seed, owners,
        Template.builder()
            .type(TemplateType.SHELL_SCRIPT.name())
            .accountId(owners.obtainAccount().getUuid())
            .name("Check InfraVariable expression")
            .templateObject(templateObject)
            .appId(GLOBAL_APP_ID)
            .build());
  }

  private Workflow getBasicCustomDeploymentTypeWorkflow(String customDeploymentTypeTemplateId, String workflowName)
      throws IOException {
    Template shellScriptTemplate = generateShellScriptforWorkflow(resourcePath, checkVarsScript);
    ShellScriptTemplate templateObject = (ShellScriptTemplate) shellScriptTemplate.getTemplateObject();

    Map<String, Object> properties = new HashMap<>();
    properties.put("scriptType", templateObject.getScriptType());
    properties.put("scriptString", templateObject.getScriptString());
    properties.put("outputVars", templateObject.getOutputVars());
    properties.put("connectionType", "SSH");
    properties.put("executeOnDelegate", true);

    List<PhaseStep> phaseSteps = new ArrayList<>();
    phaseSteps.add(aPhaseStep(CUSTOM_DEPLOYMENT_PHASE_STEP, FETCH_INSTANCE_SCRIPT_CONSTANT)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.SHELL_SCRIPT.name())
                                    .name(shellScriptTemplate.getName())
                                    .properties(properties)
                                    .templateVariables(shellScriptTemplate.getVariables())
                                    .templateUuid(shellScriptTemplate.getUuid())
                                    .templateVersion("latest")
                                    .build())
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .name("Fetch Instance")
                                    .type(CUSTOM_DEPLOYMENT_FETCH_INSTANCES.name())
                                    .templateUuid(customDeploymentTypeTemplateId)
                                    .properties(ImmutableMap.<String, Object>builder()
                                                    .put(InstanceFetchStateKeys.stateTimeoutInMinutes, "1")
                                                    .build())
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(WRAP_UP, WRAP_UP_CONSTANT).build());

    return aWorkflow()
        .name(workflowName + System.currentTimeMillis())
        .workflowType(WorkflowType.ORCHESTRATION)
        .appId(application.getAppId())
        .envId(infrastructureDefinition.getEnvId())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .serviceId(service.getUuid())
        .orchestrationWorkflow(
            aBasicOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT, PRE_DEPLOYMENT_CONSTANT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT, POST_DEPLOYMENT_CONSTANT).build())
                .addWorkflowPhase(aWorkflowPhase()
                                      .serviceId(service.getUuid())
                                      .deploymentType(DeploymentType.CUSTOM)
                                      .daemonSet(false)
                                      .infraDefinitionId(infrastructureDefinition.getUuid())
                                      .infraDefinitionName(infrastructureDefinition.getName())
                                      .phaseSteps(phaseSteps)
                                      .build())
                .build())
        .build();
  }

  private ExecutionArgs getExecutionArgs(Workflow workflow, String envId, String serviceId) {
    String artifactId =
        ArtifactStreamRestUtils.getArtifactStreamId(bearerToken, application.getUuid(), envId, serviceId);
    Artifact artifact = new Artifact();
    artifact.setUuid(artifactId);
    ExecutionArgs executionArgs = new ExecutionArgs();

    executionArgs.setWorkflowType(workflow.getWorkflowType());
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setServiceId(serviceId);
    executionArgs.setCommandName("START");
    executionArgs.setArtifacts(Collections.singletonList(artifact));
    return executionArgs;
  }

  public static String readFileContent(String filePath, String resourcePath) throws IOException {
    File scriptFile = null;
    scriptFile = new File(resourcePath + PATH_DELIMITER + filePath);

    return FileUtils.readFileToString(scriptFile, "UTF-8");
  }
}
