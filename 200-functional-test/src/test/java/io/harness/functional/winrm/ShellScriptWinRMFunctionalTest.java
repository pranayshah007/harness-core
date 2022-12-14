/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.winrm;

import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.PREPARE_STEPS;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.EntityType;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PhysicalInfraWinrm;
import software.wings.persistence.artifact.Artifact;
import software.wings.sm.StateType;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.constraints.NotEmpty;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ShellScriptWinRMFunctionalTest extends AbstractFunctionalTest {
  @Inject private WorkflowUtils workflowUtils;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private WorkflowGenerator workflowGenerator;

  private Service service;
  private InfrastructureDefinition infrastructureDefinition;
  private OwnerManager.Owners owners;
  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  static final String WRAP_UP_CONSTANT = "Wrap Up";

  @Before
  public void setUp() {
    owners = ownerManager.create();
    owners.obtainApplication(
        () -> applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.FUNCTIONAL_TEST));
    resetCache(owners.obtainAccount().getUuid());
  }

  @Test
  @Owner(developers = PRABU, intermittent = true)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunTemplatizedWinRmWorkflow() {
    service = serviceGenerator.ensurePredefined(seed, owners, ServiceGenerator.Services.GENERIC_TEST);
    resetCache(service.getAccountId());
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.AWS_WINRM_FUNCTIONAL_TEST);
    String script = "Write-Host \"hello-world\"";
    Workflow workflow =
        createTemplatizedWorkflowWithShellScriptCommand("shell-script-", service.getAppId(), "POWERSHELL", script);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    resetCache(workflow.getAccountId());
    PhysicalInfraWinrm winrmHost =
        (PhysicalInfraWinrm) infrastructureDefinitionGenerator
            .ensurePredefined(
                seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_WINRM_TEST)
            .getInfrastructure();
    assertThat(winrmHost).isNotNull();
    WorkflowExecution workflowExecution = executeWorkflow(workflow, service, Collections.emptyList(),
        ImmutableMap.of("WinRM_ConnectionAttribute", winrmHost.getWinRmConnectionAttributes()));
    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  @Test
  @Owner(developers = PRABU, intermittent = true)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunWinRmWorkflow() {
    service = serviceGenerator.ensurePredefined(seed, owners, ServiceGenerator.Services.GENERIC_TEST);
    resetCache(service.getAccountId());
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.AWS_WINRM_FUNCTIONAL_TEST);
    String script = "Write-Host \"hello-world\"";
    Workflow workflow = createWorkflowWithShellScriptCommand("shell-script-", service.getAppId(), "POWERSHELL", script);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    resetCache(workflow.getAccountId());
    WorkflowExecution workflowExecution =
        executeWorkflow(workflow, service, Collections.emptyList(), ImmutableMap.<String, String>builder().build());
    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  public Workflow createWorkflowWithShellScriptCommand(
      @NotEmpty String name, String appId, @NotEmpty String scriptType, @NotEmpty String script) {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    List<GraphNode> steps = new ArrayList<>();

    PhysicalInfraWinrm winrmHost =
        (PhysicalInfraWinrm) infrastructureDefinitionGenerator
            .ensurePredefined(
                seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_WINRM_TEST)
            .getInfrastructure();

    assertThat(winrmHost).isNotNull();

    steps.add(GraphNode.builder()
                  .name("shell-script-winrm-" + System.currentTimeMillis())
                  .type(StateType.SHELL_SCRIPT.toString())
                  .properties(ImmutableMap.<String, Object>builder()
                                  .put("scriptType", scriptType)
                                  .put("scriptString", script)
                                  .put("executeOnDelegate", "false")
                                  .put("timeout", 120000)
                                  .put("connectionType", "WINRM")
                                  .put("host", "harness-ws-1.westus.cloudapp.azure.com")
                                  .put("commandPath", "/")
                                  .put("connectionAttributes", winrmHost.getWinRmConnectionAttributes())
                                  .build())
                  .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.PREPARE_STEPS, PREPARE_STEPS.toString()).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.COLLECT_ARTIFACT, PhaseStepType.COLLECT_ARTIFACT.toString())
                       .addAllSteps(steps)
                       .build());
    phaseSteps.add(aPhaseStep(WRAP_UP, WRAP_UP_CONSTANT).build());

    Workflow buildWorkflow =
        aWorkflow()
            .name(name + System.currentTimeMillis())
            .appId(appId)
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(
                aBuildOrchestrationWorkflow()
                    .withWorkflowPhases(Arrays.asList(aWorkflowPhase().phaseSteps(phaseSteps).build()))
                    .build())
            .templatized(false)
            .build();
    return buildWorkflow;
  }

  public Workflow createTemplatizedWorkflowWithShellScriptCommand(
      @NotEmpty String name, String appId, @NotEmpty String scriptType, @NotEmpty String script) {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    List<GraphNode> steps = new ArrayList<>();

    steps.add(GraphNode.builder()
                  .name("shell-script-winrm-" + System.currentTimeMillis())
                  .type(StateType.SHELL_SCRIPT.toString())
                  .properties(ImmutableMap.<String, Object>builder()
                                  .put("scriptType", scriptType)
                                  .put("scriptString", script)
                                  .put("executeOnDelegate", "false")
                                  .put("timeout", 120000)
                                  .put("connectionType", "WINRM")
                                  .put("host", "harness-ws-1.westus.cloudapp.azure.com")
                                  .put("commandPath", "/")
                                  .put("templateExpressions", Arrays.asList(getTemplateExpressionsforSshConnection()))
                                  .build())
                  .build());

    phaseSteps.add(aPhaseStep(PhaseStepType.PREPARE_STEPS, PREPARE_STEPS.toString()).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.COLLECT_ARTIFACT, PhaseStepType.COLLECT_ARTIFACT.toString())
                       .addAllSteps(steps)
                       .build());
    phaseSteps.add(aPhaseStep(WRAP_UP, WRAP_UP_CONSTANT).build());

    Workflow buildWorkflow =
        aWorkflow()
            .name(name + System.currentTimeMillis())
            .appId(appId)
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(
                aBuildOrchestrationWorkflow()
                    .withWorkflowPhases(Arrays.asList(aWorkflowPhase().phaseSteps(phaseSteps).build()))
                    .build())
            .templatized(true)
            .build();
    return buildWorkflow;
  }

  private Map<String, Object> getTemplateExpressionsforSshConnection() {
    Map<String, Object> metaData = ImmutableMap.<String, Object>builder()
                                       .put("entityType", EntityType.SS_WINRM_CONNECTION_ATTRIBUTE.name())
                                       .build();

    Map<String, Object> templateExpression = new HashMap<>();
    templateExpression.put("fieldName", "connectionAttributes");
    templateExpression.put("expression", "${WinRM_ConnectionAttribute}");
    templateExpression.put("mandatory", true);
    templateExpression.put("metadata", metaData);

    return templateExpression;
  }

  private WorkflowExecution executeWorkflow(final Workflow workflow, final Service service,
      final List<Artifact> artifacts, ImmutableMap<String, String> workflowVariables) {
    final String appId = service.getAppId();
    final String envId = workflow.getEnvId();

    resetCache(this.service.getAccountId());
    ExecutionArgs executionArgs = prepareExecutionArgs(workflow, artifacts, workflowVariables);
    return WorkflowRestUtils.startWorkflow(bearerToken, appId, envId, executionArgs);
  }
  private ExecutionArgs prepareExecutionArgs(
      Workflow workflow, List<Artifact> artifacts, ImmutableMap<String, String> workflowFlowVariables) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setArtifacts(artifacts);
    executionArgs.setWorkflowVariables(workflowFlowVariables);
    return executionArgs;
  }
}
