/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.MITISHA;
import static io.harness.rule.OwnerRule.POOJA;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.service.impl.WorkflowExecutionServiceHelper.calculateCdPageCandidate;
import static software.wings.sm.StateMachine.StateMachineBuilder.aStateMachine;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Pipeline;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.deployment.WorkflowVariablesMetadata;
import software.wings.service.impl.WorkflowExecutionServiceHelper;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateMachine;
import software.wings.utils.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class WorkflowExecutionServiceHelperTest extends WingsBaseTest {
  @Mock private WorkflowService workflowService;
  @Mock private PipelineService pipelineService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @InjectMocks @Inject private WorkflowExecutionServiceHelper workflowExecutionServiceHelper;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesSimple() {
    List<Variable> workflowVariables = asList(aVariable().build(), aVariable().build());
    Workflow workflow = prepareWorkflow(workflowVariables);
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    WorkflowVariablesMetadata workflowVariablesMetadata =
        workflowExecutionServiceHelper.fetchWorkflowVariables(APP_ID, prepareExecutionArgs(null, false), null);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isEqualTo(workflowVariables);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesSimpleInvalid() {
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(null);
    WorkflowVariablesMetadata workflowVariablesMetadata =
        workflowExecutionServiceHelper.fetchWorkflowVariables(APP_ID, prepareExecutionArgs(null, false), null);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isEmpty();

    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(aWorkflow().build());
    workflowVariablesMetadata =
        workflowExecutionServiceHelper.fetchWorkflowVariables(APP_ID, prepareExecutionArgs(null, false), null);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isEmpty();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesSimplePipeline() {
    List<Variable> workflowVariables = asList(aVariable().build(), aVariable().build());
    Pipeline pipeline = preparePipeline(workflowVariables);
    when(pipelineService.getPipelineVariables(APP_ID, PIPELINE_ID)).thenReturn(pipeline.getPipelineVariables());
    WorkflowVariablesMetadata workflowVariablesMetadata =
        workflowExecutionServiceHelper.fetchWorkflowVariables(APP_ID, prepareExecutionArgs(null, true), null);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isEqualTo(workflowVariables);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesSimplePipelineInvalid() {
    when(pipelineService.getPipelineVariables(APP_ID, PIPELINE_ID)).thenReturn(null);
    WorkflowVariablesMetadata workflowVariablesMetadata =
        workflowExecutionServiceHelper.fetchWorkflowVariables(APP_ID, prepareExecutionArgs(null, true), null);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isEmpty();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariables() {
    List<Variable> workflowVariables = asList(prepareVariable(1), prepareVariable(2));
    Map<String, String> workflowVariablesMap = prepareWorkflowVariablesMap(2);
    Workflow workflow = prepareWorkflow(workflowVariables);
    WorkflowExecution workflowExecution = prepareWorkflowExecution(workflowVariables, workflowVariablesMap, false);
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    WorkflowVariablesMetadata workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, false), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isNotNull();
    assertThat(newWorkflowVariables.size()).isEqualTo(2);
    assertThat(newWorkflowVariables.get(0).getName()).isEqualTo("var1");
    assertThat(newWorkflowVariables.get(0).getValue()).isEqualTo("val1");
    assertThat(newWorkflowVariables.get(1).getName()).isEqualTo("var2");
    assertThat(newWorkflowVariables.get(1).getValue()).isEqualTo("val2");
  }

  @Test
  @Owner(developers = MITISHA)
  @Category(UnitTests.class)
  public void shouldNotPopulateWorkflowVariablesForFalseIfCondition() {
    List<Variable> workflowVariables = asList(prepareVariable(1), prepareVariable(2));
    Map<String, String> workflowVariablesMap = prepareOldWorkflowVariablesMap(2);
    Workflow workflow = prepareWorkflow(workflowVariables);
    WorkflowExecution workflowExecution = prepareWorkflowExecution(workflowVariables, workflowVariablesMap, false);
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    WorkflowVariablesMetadata workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, false), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isNotNull();
    assertThat(newWorkflowVariables.size()).isEqualTo(2);
    assertThat(newWorkflowVariables.get(0).getName()).isEqualTo("var1");
    assertThat(newWorkflowVariables.get(0).getValue()).isEqualTo(null);
    assertThat(newWorkflowVariables.get(1).getName()).isEqualTo("var2");
    assertThat(newWorkflowVariables.get(1).getValue()).isEqualTo(null);
  }
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesInvalid() {
    List<Variable> workflowVariables = asList(prepareVariable(1), prepareVariable(2));
    Map<String, String> workflowVariablesMap = prepareWorkflowVariablesMap(2);

    Workflow workflow = prepareWorkflow(workflowVariables);
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(null);
    WorkflowVariablesMetadata workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, false), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    validateUnsetWorkflowVariables(newWorkflowVariables);

    WorkflowExecution workflowExecution = prepareWorkflowExecution(workflowVariables, workflowVariablesMap, true);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, false), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    validateUnsetWorkflowVariables(newWorkflowVariables);

    workflowExecution = prepareWorkflowExecution(workflowVariables, workflowVariablesMap, false);
    workflowExecution.setWorkflowId("random");
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, false), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    validateUnsetWorkflowVariables(newWorkflowVariables);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesPipeline() {
    List<Variable> workflowVariables = asList(prepareVariable(1), prepareVariable(2));
    Map<String, String> workflowVariablesMap = prepareWorkflowVariablesMap(2);
    Pipeline pipeline = preparePipeline(workflowVariables);
    WorkflowExecution workflowExecution = prepareWorkflowExecution(workflowVariables, workflowVariablesMap, true);
    when(pipelineService.getPipelineVariables(APP_ID, PIPELINE_ID)).thenReturn(pipeline.getPipelineVariables());
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    WorkflowVariablesMetadata workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, true), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isNotNull();
    assertThat(newWorkflowVariables.size()).isEqualTo(2);
    assertThat(newWorkflowVariables.get(0).getName()).isEqualTo("var1");
    assertThat(newWorkflowVariables.get(0).getValue()).isEqualTo("val1");
    assertThat(newWorkflowVariables.get(1).getName()).isEqualTo("var2");
    assertThat(newWorkflowVariables.get(1).getValue()).isEqualTo("val2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesPipelineInvalid() {
    List<Variable> workflowVariables = asList(aVariable().name("var1").type(VariableType.ENTITY).build(),
        aVariable().name("var2").type(VariableType.ENTITY).build());
    Map<String, String> workflowVariablesMap = prepareWorkflowVariablesMap(2);

    Pipeline pipeline = preparePipeline(workflowVariables);
    when(pipelineService.getPipelineVariables(APP_ID, PIPELINE_ID)).thenReturn(pipeline.getPipelineVariables());
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(null);
    WorkflowVariablesMetadata workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, true), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    validateUnsetWorkflowVariables(newWorkflowVariables);

    WorkflowExecution workflowExecution = prepareWorkflowExecution(workflowVariables, workflowVariablesMap, false);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, true), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    validateUnsetWorkflowVariables(newWorkflowVariables);

    workflowExecution = prepareWorkflowExecution(workflowVariables, workflowVariablesMap, true);
    workflowExecution.setPipelineExecution(null);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, true), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    validateUnsetWorkflowVariables(newWorkflowVariables);

    workflowExecution = prepareWorkflowExecution(workflowVariables, workflowVariablesMap, true);
    workflowExecution.getPipelineExecution().setPipelineId("random");
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, true), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    validateUnsetWorkflowVariables(newWorkflowVariables);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesExtra() {
    validateFetchWorkflowVariablesReset(prepareWorkflowVariablesMap(3));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesLess() {
    validateFetchWorkflowVariablesReset(prepareWorkflowVariablesMap(1));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesEmpty() {
    validateFetchWorkflowVariablesReset(prepareWorkflowVariablesMap(0));
  }

  private void validateFetchWorkflowVariablesReset(Map<String, String> workflowVariablesMap) {
    List<Variable> oldWorkflowVariables = asList(prepareVariable(1), prepareVariable(2), prepareVariable(3));
    List<Variable> workflowVariables = asList(prepareVariable(1), prepareVariable(2));
    Workflow workflow = prepareWorkflow(workflowVariables);
    WorkflowExecution workflowExecution = prepareWorkflowExecution(oldWorkflowVariables, workflowVariablesMap, false);
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    WorkflowVariablesMetadata workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, false), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isTrue();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    validateUnsetWorkflowVariables(newWorkflowVariables);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesChangedType() {
    Map<String, String> workflowVariablesMap = prepareWorkflowVariablesMap(4);
    List<Variable> oldWorkflowVariables = asList(prepareVariable(1), prepareVariable(2),
        prepareVariable(3, VariableType.TEXT), prepareVariable(4, VariableType.TEXT));
    List<Variable> workflowVariables = asList(prepareVariable(1), prepareVariable(2, VariableType.TEXT),
        prepareVariable(3, VariableType.TEXT), prepareVariable(4, VariableType.TEXT));
    Workflow workflow = prepareWorkflow(workflowVariables);
    WorkflowExecution workflowExecution = prepareWorkflowExecution(oldWorkflowVariables, workflowVariablesMap, false);
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    WorkflowVariablesMetadata workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, false), WORKFLOW_EXECUTION_ID);
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isNotNull();
    assertThat(newWorkflowVariables.size()).isEqualTo(4);
    assertThat(newWorkflowVariables.get(0).getName()).isEqualTo("var1");
    assertThat(newWorkflowVariables.get(1).getName()).isEqualTo("var2");
    assertThat(newWorkflowVariables.get(2).getName()).isEqualTo("var3");
    assertThat(newWorkflowVariables.get(2).getValue()).isEqualTo("val3");
    assertThat(newWorkflowVariables.get(3).getName()).isEqualTo("var4");
    assertThat(newWorkflowVariables.get(3).getValue()).isEqualTo("val4");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariablesChangedEntityType() {
    Map<String, String> workflowVariablesMap = prepareWorkflowVariablesMap(4);
    List<Variable> oldWorkflowVariables =
        asList(prepareVariable(1, VariableType.ENTITY, SERVICE), prepareVariable(2, VariableType.ENTITY, ENVIRONMENT),
            prepareVariable(3, VariableType.ENTITY, INFRASTRUCTURE_DEFINITION), prepareVariable(4, VariableType.TEXT));
    List<Variable> workflowVariables =
        asList(prepareVariable(1, VariableType.ENTITY, SERVICE), prepareVariable(2, VariableType.ENTITY, ENVIRONMENT),
            prepareVariable(3, VariableType.ENTITY, INFRASTRUCTURE_DEFINITION), prepareVariable(4, VariableType.TEXT));
    Workflow workflow = prepareWorkflow(workflowVariables);
    WorkflowExecution workflowExecution = prepareWorkflowExecution(oldWorkflowVariables, workflowVariablesMap, false);
    when(workflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(workflowExecution);
    WorkflowVariablesMetadata workflowVariablesMetadata = workflowExecutionServiceHelper.fetchWorkflowVariables(
        APP_ID, prepareExecutionArgs(null, false), WORKFLOW_EXECUTION_ID);
    assertThat(workflowVariablesMetadata.isChanged()).isFalse();
    List<Variable> newWorkflowVariables = workflowVariablesMetadata.getWorkflowVariables();
    assertThat(newWorkflowVariables).isNotNull();
    assertThat(newWorkflowVariables.size()).isEqualTo(4);
    assertThat(newWorkflowVariables.get(0).getName()).isEqualTo("var1");
    assertThat(newWorkflowVariables.get(1).getName()).isEqualTo("var2");
    assertThat(newWorkflowVariables.get(2).getName()).isEqualTo("var3");
    assertThat(newWorkflowVariables.get(3).getName()).isEqualTo("var4");
    assertThat(newWorkflowVariables.get(3).getValue()).isEqualTo("val4");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void calculateCdPageCandidateTest() {
    assertThat(calculateCdPageCandidate("Pipeline_execution_Id", null, false)).isFalse();
    assertThat(calculateCdPageCandidate(null, null, false)).isTrue();
    assertThat(calculateCdPageCandidate(null, "pipelineResumeId", false)).isFalse();
    assertThat(calculateCdPageCandidate(null, "pipelineResumeId", true)).isTrue();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testRunningPipelineWorkflowVariablesReturnsCorrectVariableList() {
    final String appID = "nCLN8c84SqWPr44sqg65JQ";
    final String pipelineStageElementId = "iibzVUjNTlWsv23lQIrkWw";
    final String pipelineExecutionId = "3v2FfeZUTvqSnz7djyGMqQ";

    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/runtime_pipeline_execution_stage2.json", WorkflowExecution.class);
    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/k8s_two_stage_runtime_pipeline.json", Pipeline.class);
    Workflow workflow = JsonUtils.readResourceFile("workflows/k8s_workflow.json", Workflow.class);

    when(workflowExecutionService.getWorkflowExecution(eq(appID), eq(pipelineExecutionId)))
        .thenReturn(workflowExecution);
    when(workflowService.readWorkflow(eq(appID), anyString())).thenReturn(workflow);
    when(pipelineService.readPipelineWithVariables(eq(appID), anyString())).thenReturn(pipeline);

    WorkflowVariablesMetadata actual = workflowExecutionServiceHelper.fetchWorkflowVariablesForRunningExecution(
        appID, pipelineExecutionId, pipelineStageElementId);
    List<Variable> expected =
        JsonUtils.readResourceFile("expected_workflow_vars_metada.json", new TypeReference<List<Variable>>() {});
    assertThat(actual.getWorkflowVariables()).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testVariablesWithEnvVariableNonRuntime() {
    // Single stage pipeline.
    // Env is variable but non runtime
    // Infra and Service are runtime. But left blank at the time of deployment
    ExecutionArgs executionArgs =
        ExecutionArgs.builder()
            .workflowVariables(ImmutableMap.<String, String>builder().put("env", "ENV1").build())
            .build();
    RuntimeInputsConfig config =
        RuntimeInputsConfig.builder()
            .runtimeInputVariables(Lists.newArrayList("ServiceId", "InfraDefinition_KUBERNETES"))
            .build();
    List<Variable> workflowVariables =
        Lists.newArrayList(varBuilder("Environment", false, "InfraDefinition_KUBERNETES", ENVIRONMENT),
            varBuilder("InfraDefinition_KUBERNETES", true, "ServiceId", INFRASTRUCTURE_DEFINITION),
            varBuilder("ServiceId", true, "InfraDefinition_KUBERNETES", SERVICE));
    List<Variable> pipelineVariables = Lists.newArrayList(varBuilder("env", null, "pipelineInfra", ENVIRONMENT),
        varBuilder("pipelineInfra", true, "pipelineService", INFRASTRUCTURE_DEFINITION),
        varBuilder("pipelineService", true, "pipelineInfra", SERVICE));

    Map<String, String> resolveVarValues = ImmutableMap.<String, String>builder()
                                               .put("Environment", "${env}")
                                               .put("InfraDefinition_KUBERNETES", "${pipelineInfra}")
                                               .put("ServiceId", "${pipelineService}")
                                               .build();

    List<Variable> actual = WorkflowExecutionServiceHelper.getRuntimeVariablesForStage(
        executionArgs, config, workflowVariables, pipelineVariables, resolveVarValues);

    Variable infraVariableExpected = varBuilder("pipelineInfra", true, "pipelineService", INFRASTRUCTURE_DEFINITION);
    infraVariableExpected.getMetadata().put("env", "ENV1");

    List<Variable> expected =
        Lists.newArrayList(infraVariableExpected, varBuilder("pipelineService", true, "pipelineInfra", SERVICE));
    assertThat(actual).isEqualTo(expected);
  }

  private Variable varBuilder(String name, Boolean isRuntime, String relatedField, EntityType entityType) {
    Variable var = new Variable();
    var.setName(name);
    var.setMandatory(true);
    var.setRuntimeInput(isRuntime);
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("relatedField", relatedField);
    metadata.put("entityType", entityType.name());
    var.setMetadata(metadata);
    return var;
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testVariablesWithInfraAsRuntime() {
    // Single stage pipeline.
    // Env is fixed. Service is variable but non runtime
    // Infra is runtime & left blank at the time of deployment.
    ExecutionArgs executionArgs =
        ExecutionArgs.builder()
            .workflowVariables(ImmutableMap.<String, String>builder().put("pipelineService", "SERVICE1").build())
            .build();
    RuntimeInputsConfig config =
        RuntimeInputsConfig.builder().runtimeInputVariables(Lists.newArrayList("InfraDefinition_KUBERNETES")).build();
    List<Variable> workflowVariables =
        Lists.newArrayList(varBuilder("Environment", false, "InfraDefinition_KUBERNETES", ENVIRONMENT),
            varBuilder("InfraDefinition_KUBERNETES", true, "ServiceId", INFRASTRUCTURE_DEFINITION),
            varBuilder("ServiceId", false, "InfraDefinition_KUBERNETES", SERVICE));
    Variable infraPipelineVariable = varBuilder("pipelineInfra", true, "pipelineService", INFRASTRUCTURE_DEFINITION);
    infraPipelineVariable.getMetadata().put("envId", "ENV1");
    List<Variable> pipelineVariables =
        Lists.newArrayList(infraPipelineVariable, varBuilder("pipelineService", false, "pipelineInfra", SERVICE));

    Map<String, String> resolveVarValues = ImmutableMap.<String, String>builder()
                                               .put("Environment", "ENV1")
                                               .put("InfraDefinition_KUBERNETES", "${pipelineInfra}")
                                               .put("ServiceId", "${pipelineService}")
                                               .build();

    List<Variable> actual = WorkflowExecutionServiceHelper.getRuntimeVariablesForStage(
        executionArgs, config, workflowVariables, pipelineVariables, resolveVarValues);

    Variable infraExpected = varBuilder("pipelineInfra", true, "pipelineService", INFRASTRUCTURE_DEFINITION);
    infraExpected.getMetadata().put("pipelineServiceId", "SERVICE1");
    List<Variable> expected = Lists.newArrayList(infraExpected);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testVariablesWithServiceRuntime() {
    // Single stage pipeline.
    // Env is fixed. Infra is variable but non runtime
    // Service is runtime & left blank at the time of deployment.
    ExecutionArgs executionArgs =
        ExecutionArgs.builder()
            .workflowVariables(ImmutableMap.<String, String>builder().put("pipelineInfra", "INFRA1").build())
            .build();
    RuntimeInputsConfig config =
        RuntimeInputsConfig.builder().runtimeInputVariables(Lists.newArrayList("ServiceId")).build();
    List<Variable> workflowVariables =
        Lists.newArrayList(varBuilder("Environment", false, "InfraDefinition_KUBERNETES", ENVIRONMENT),
            varBuilder("InfraDefinition_KUBERNETES", true, "ServiceId", INFRASTRUCTURE_DEFINITION),
            varBuilder("ServiceId", false, "InfraDefinition_KUBERNETES", SERVICE));
    Variable servicePipelineVar = varBuilder("pipelineService", true, "pipelineInfra", SERVICE);
    List<Variable> pipelineVariables = Lists.newArrayList(
        servicePipelineVar, varBuilder("pipelineInfra", false, "pipelineService", INFRASTRUCTURE_DEFINITION));

    Map<String, String> resolveVarValues = ImmutableMap.<String, String>builder()
                                               .put("Environment", "ENV1")
                                               .put("InfraDefinition_KUBERNETES", "${pipelineInfra}")
                                               .put("ServiceId", "${pipelineService}")
                                               .build();

    List<Variable> actual = WorkflowExecutionServiceHelper.getRuntimeVariablesForStage(
        executionArgs, config, workflowVariables, pipelineVariables, resolveVarValues);

    Variable serviceExpected = varBuilder("pipelineService", true, "pipelineInfra", SERVICE);
    serviceExpected.getMetadata().put("pipelineInfra", "INFRA1");
    List<Variable> expected = Lists.newArrayList(serviceExpected);
    assertThat(actual).isEqualTo(expected);
  }

  private void validateUnsetWorkflowVariables(List<Variable> workflowVariables) {
    assertThat(workflowVariables).isNotNull();
    assertThat(workflowVariables.size()).isEqualTo(2);
    assertThat(workflowVariables.get(0).getName()).isEqualTo("var1");
    assertThat(workflowVariables.get(0).getValue()).isNull();
    assertThat(workflowVariables.get(1).getName()).isEqualTo("var2");
    assertThat(workflowVariables.get(1).getValue()).isNull();
  }

  private WorkflowExecution prepareWorkflowExecution(
      List<Variable> workflowVariables, Map<String, String> workflowVariablesMap, boolean isPipeline) {
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().executionArgs(prepareExecutionArgs(workflowVariablesMap, isPipeline)).build();
    if (isPipeline) {
      Pipeline pipeline = Pipeline.builder().build();
      pipeline.setPipelineVariables(workflowVariables);
      workflowExecution.setWorkflowType(WorkflowType.PIPELINE);
      workflowExecution.setPipelineExecution(
          aPipelineExecution().withPipelineId(PIPELINE_ID).withPipeline(pipeline).build());
    } else {
      workflowExecution.setWorkflowType(WorkflowType.ORCHESTRATION);
      workflowExecution.setWorkflowId(WORKFLOW_ID);
      StateMachine stateMachine = aStateMachine().build();
      stateMachine.setOrchestrationWorkflow(
          aCanaryOrchestrationWorkflow().withUserVariables(workflowVariables).build());
      workflowExecution.setStateMachine(stateMachine);
      when(workflowService.readStateMachine(any(), any(), any())).thenReturn(stateMachine);
      when(workflowExecutionService.obtainStateMachine(any())).thenReturn(stateMachine);
    }
    return workflowExecution;
  }

  private Workflow prepareWorkflow(List<Variable> workflowVariables) {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withUserVariables(workflowVariables).build();
    return aWorkflow().orchestrationWorkflow(canaryOrchestrationWorkflow).build();
  }

  private Pipeline preparePipeline(List<Variable> workflowVariables) {
    Pipeline pipeline = Pipeline.builder().build();
    pipeline.setPipelineVariables(workflowVariables);
    return pipeline;
  }

  private ExecutionArgs prepareExecutionArgs(Map<String, String> workflowVariables, boolean isPipeline) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowVariables(workflowVariables);
    if (isPipeline) {
      executionArgs.setWorkflowType(WorkflowType.PIPELINE);
      executionArgs.setPipelineId(PIPELINE_ID);
    } else {
      executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
      executionArgs.setOrchestrationId(WORKFLOW_ID);
    }
    return executionArgs;
  }

  private Variable prepareVariable(int index, VariableType type, EntityType entityType) {
    Variable variable = aVariable()
                            .name("var" + index)
                            .allowedList(Collections.singletonList("val" + index))
                            .type(type)
                            .mandatory(true)
                            .build();
    if (VariableType.ENTITY == type) {
      variable.setMetadata(singletonMap(Variable.ENTITY_TYPE, entityType));
    }
    return variable;
  }

  private Variable prepareVariable(int index, VariableType type) {
    return prepareVariable(index, type, SERVICE);
  }

  private Variable prepareVariable(int index) {
    return prepareVariable(index, VariableType.ENTITY);
  }

  private Map<String, String> prepareWorkflowVariablesMap(int count) {
    Map<String, String> workflowVariablesMap = new HashMap<>();
    for (int i = 1; i <= count; i++) {
      workflowVariablesMap.put("var" + i, "val" + i);
    }
    return workflowVariablesMap;
  }

  private Map<String, String> prepareOldWorkflowVariablesMap(int count) {
    Map<String, String> workflowVariablesMap = new HashMap<>();
    for (int i = 1; i <= count; i++) {
      workflowVariablesMap.put("var" + i, "h" + i);
    }
    return workflowVariablesMap;
  }
}
