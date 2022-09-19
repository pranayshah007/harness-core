/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.pipeline;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.sm.StateType;
import software.wings.sm.states.EnvState.EnvStateKeys;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@TargetModule(HarnessModule._800_PIPELINE_SERVICE)
@OwnedBy(CDC)
public class PipelineServiceHelperTest extends WingsBaseTest {
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testUpdateLoopingInfoWhenLooped() {
    List<Variable> userVariables = new ArrayList<>();
    Variable infraVar1 = aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("infra1").build();
    userVariables.add(infraVar1);
    OrchestrationWorkflow orchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build();
    Workflow workflow = aWorkflow().orchestrationWorkflow(orchestrationWorkflow).build();
    Map<String, String> workflowVariable = ImmutableMap.of("infra1", "testVal1,testVal2");
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder().workflowVariables(workflowVariable).name("test step").type("ENV_STATE").build();

    PipelineStage pipelineStage = PipelineStage.builder().pipelineStageElements(asList(pipelineStageElement)).build();
    List<String> infraDefIds = new ArrayList<>();
    PipelineServiceHelper.updateLoopingInfo(pipelineStage, workflow, infraDefIds);
    assertThat(pipelineStage.isLooped()).isEqualTo(true);
    assertThat(pipelineStage.getLoopedVarName()).isEqualTo("infra1");
    assertThat(infraDefIds.size()).isEqualTo(2);
    assertThat(infraDefIds.contains("testVal1")).isTrue();
    assertThat(infraDefIds.contains("testVal2")).isTrue();
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testUpdateLoopingInfoWhenLoopedRuntimeInfra() {
    List<Variable> userVariables = new ArrayList<>();
    Variable infraVar1 = aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("infra1").build();
    userVariables.add(infraVar1);
    OrchestrationWorkflow orchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build();
    Workflow workflow = aWorkflow().orchestrationWorkflow(orchestrationWorkflow).build();
    Map<String, String> workflowVariable = ImmutableMap.of("infra1", "${infraVal}");
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder()
            .workflowVariables(workflowVariable)
            .runtimeInputsConfig(RuntimeInputsConfig.builder().runtimeInputVariables(asList("infra1")).build())
            .name("test step")
            .type("ENV_STATE")
            .build();

    PipelineStage pipelineStage = PipelineStage.builder().pipelineStageElements(asList(pipelineStageElement)).build();
    List<String> infraDefIds = new ArrayList<>();
    PipelineServiceHelper.updateLoopingInfo(pipelineStage, workflow, infraDefIds);
    assertThat(pipelineStage.isLooped()).isEqualTo(true);
    assertThat(pipelineStage.getLoopedVarName()).isEqualTo("infra1");
    assertThat(infraDefIds.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testUpdateLoopingInfoWhenNotLooped() {
    List<Variable> userVariables = new ArrayList<>();
    OrchestrationWorkflow orchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build();
    Workflow workflow = aWorkflow().orchestrationWorkflow(orchestrationWorkflow).build();
    Map<String, String> workflowVariable = ImmutableMap.of("infra1", "testVal1,testVal2");
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder().workflowVariables(workflowVariable).name("test step").type("ENV_STATE").build();

    PipelineStage pipelineStage = PipelineStage.builder().pipelineStageElements(asList(pipelineStageElement)).build();
    List<String> infraDefIds = new ArrayList<>();
    PipelineServiceHelper.updateLoopingInfo(pipelineStage, workflow, infraDefIds);
    assertThat(pipelineStage.isLooped()).isEqualTo(false);
    assertThat(pipelineStage.getLoopedVarName()).isEqualTo(null);
    assertThat(infraDefIds.size()).isEqualTo(0);

    Variable envVar1 = aVariable().entityType(ENVIRONMENT).name("env1").build();
    userVariables.add(envVar1);

    PipelineServiceHelper.updateLoopingInfo(pipelineStage, workflow, infraDefIds);
    assertThat(pipelineStage.isLooped()).isEqualTo(false);
    assertThat(pipelineStage.getLoopedVarName()).isEqualTo(null);
    assertThat(infraDefIds.size()).isEqualTo(0);

    Variable infraVar1 = aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("infra1").build();
    Variable infraVar2 = aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("infra2").build();

    userVariables.add(infraVar1);
    userVariables.add(infraVar2);

    PipelineServiceHelper.updateLoopingInfo(pipelineStage, workflow, infraDefIds);
    assertThat(pipelineStage.isLooped()).isEqualTo(false);
    assertThat(pipelineStage.getLoopedVarName()).isEqualTo(null);
    assertThat(infraDefIds.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testUpdateLoopingInfoWhenNotLoopedInvalid() {
    List<Variable> userVariables = new ArrayList<>();
    Variable infraVar1 = aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("infra1").build();
    userVariables.add(infraVar1);
    OrchestrationWorkflow orchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build();
    Workflow workflow = aWorkflow().orchestrationWorkflow(orchestrationWorkflow).build();
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder().workflowVariables(new HashMap<>()).name("test step").type("ENV_STATE").build();

    PipelineStage pipelineStage = PipelineStage.builder().pipelineStageElements(asList(pipelineStageElement)).build();
    List<String> infraDefIds = new ArrayList<>();

    assertThatThrownBy(() -> PipelineServiceHelper.updateLoopingInfo(pipelineStage, workflow, infraDefIds))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No value supplied in pipeline for infra variable: infra1");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithLoopedState() {
    Map<String, String> workflowVariable = ImmutableMap.of("infra1", "testVal1,testVal2");
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder()
                                                    .workflowVariables(workflowVariable)
                                                    .properties(new HashMap<>())
                                                    .name("test step")
                                                    .type("ENV_STATE")
                                                    .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    Pipeline pipeline = Pipeline.builder().name("Test pipeline").pipelineStages(asList(pipelineStage)).build();
    PipelineServiceHelper.updatePipelineWithLoopedState(pipeline);
    assertThat(pipeline.getPipelineStages().get(0).getPipelineStageElements().get(0).getType())
        .isEqualTo(StateType.ENV_LOOP_STATE.getType());
    Map<String, Object> properties =
        pipeline.getPipelineStages().get(0).getPipelineStageElements().get(0).getProperties();
    assertThat(properties.get("loopedValues")).isNotNull();
    List<String> values = (List<String>) properties.get("loopedValues");
    assertThat(values.size()).isEqualTo(2);
    assertThat(values.contains("testVal1")).isTrue();
    assertThat(values.contains("testVal2")).isTrue();
    assertThat(properties.get("loopedVarName")).isEqualTo("infra1");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithLoopedStateRuntimeVariableNoValue() {
    Map<String, String> workflowVariable = ImmutableMap.of("infra1", "testVal1");
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder()
            .workflowVariables(workflowVariable)
            .properties(new HashMap<>())
            .runtimeInputsConfig(RuntimeInputsConfig.builder().runtimeInputVariables(asList("infra1")).build())
            .name("test step")
            .type("ENV_STATE")
            .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    Pipeline pipeline = Pipeline.builder().name("Test pipeline").pipelineStages(asList(pipelineStage)).build();
    PipelineServiceHelper.updatePipelineWithLoopedState(pipeline);
    assertThat(pipeline.getPipelineStages().get(0).getPipelineStageElements().get(0).getType())
        .isEqualTo(StateType.ENV_LOOP_STATE.getType());
    Map<String, Object> properties =
        pipeline.getPipelineStages().get(0).getPipelineStageElements().get(0).getProperties();
    assertThat(properties.get("loopedValues")).isNotNull();
    List<String> values = (List<String>) properties.get("loopedValues");
    assertThat(values.size()).isEqualTo(1);
    assertThat(values.contains("testVal1")).isTrue();
    assertThat(properties.get("loopedVarName")).isEqualTo("infra1");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithLoopedStateRuntimeVariableDefaultValue() {
    Map<String, String> workflowVariable = ImmutableMap.of();
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder()
            .workflowVariables(workflowVariable)
            .properties(new HashMap<>())
            .runtimeInputsConfig(RuntimeInputsConfig.builder().runtimeInputVariables(asList("infra1")).build())
            .name("test step")
            .type("ENV_STATE")
            .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    Pipeline pipeline = Pipeline.builder().name("Test pipeline").pipelineStages(asList(pipelineStage)).build();
    PipelineServiceHelper.updatePipelineWithLoopedState(pipeline);
    assertThat(pipeline.getPipelineStages().get(0).getPipelineStageElements().get(0).getType())
        .isEqualTo(StateType.ENV_LOOP_STATE.getType());
    Map<String, Object> properties =
        pipeline.getPipelineStages().get(0).getPipelineStageElements().get(0).getProperties();
    assertThat(properties.get("loopedValues")).isNotNull();
    List<String> values = (List<String>) properties.get("loopedValues");
    assertThat(values.size()).isEqualTo(0);
    assertThat(properties.get("loopedVarName")).isEqualTo("infra1");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithLoopedStateInvalid() {
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder().properties(new HashMap<>()).name("test step").type("ENV_STATE").build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    Pipeline pipeline = Pipeline.builder().name("Test pipeline").pipelineStages(asList(pipelineStage)).build();
    assertThatThrownBy(() -> PipelineServiceHelper.updatePipelineWithLoopedState(pipeline))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline stage marked as loop, but doesnt have looping config");

    Map<String, String> workflowVariable = ImmutableMap.of("infra2", "testVal1,testVal2");
    pipelineStageElement.setWorkflowVariables(workflowVariable);

    assertThatThrownBy(() -> PipelineServiceHelper.updatePipelineWithLoopedState(pipeline))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline stage marked as loop, but doesnt have looping config");

    workflowVariable = ImmutableMap.of("infra1", "testVal1");
    pipelineStageElement.setWorkflowVariables(workflowVariable);

    assertThatThrownBy(() -> PipelineServiceHelper.updatePipelineWithLoopedState(pipeline))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline stage marked as loop, but doesnt have looping config");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetEnvIdsForSeriesStage1() {
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of(EnvStateKeys.envId, ENV_ID))
                                                    .name("test step")
                                                    .type("ENV_STATE")
                                                    .parallelIndex(1)
                                                    .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    Pipeline pipeline = Pipeline.builder().name("Test pipeline").pipelineStages(asList(pipelineStage)).build();
    List<String> envIds = PipelineServiceHelper.getEnvironmentIdsForParallelIndex(pipeline, 1);
    assertThat(envIds).hasSize(1).containsExactly(ENV_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetEnvIdsForSeriesStage2() {
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of(EnvStateKeys.envId, ENV_ID))
                                                    .name("test step")
                                                    .type("ENV_STATE")
                                                    .parallelIndex(1)
                                                    .build();
    PipelineStageElement pipelineStage2Element = PipelineStageElement.builder()
                                                     .properties(ImmutableMap.of(EnvStateKeys.envId, ENV_ID))
                                                     .name("test step")
                                                     .type("ENV_STATE")
                                                     .parallelIndex(2)
                                                     .build();
    PipelineStageElement pipelineStage2Element2 = PipelineStageElement.builder()
                                                      .properties(ImmutableMap.of(EnvStateKeys.envId, ENV_ID + 2))
                                                      .name("test step")
                                                      .type("ENV_STATE")
                                                      .parallelIndex(2)
                                                      .build();
    PipelineStageElement pipelineStage3Element = PipelineStageElement.builder()
                                                     .properties(ImmutableMap.of(EnvStateKeys.envId, ENV_ID + 3))
                                                     .name("test step")
                                                     .type("ENV_STATE")
                                                     .parallelIndex(3)
                                                     .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    PipelineStage pipelineStage2 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStage2Element))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    PipelineStage pipelineStage3 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStage2Element2))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    PipelineStage pipelineStage4 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStage3Element))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    Pipeline pipeline = Pipeline.builder()
                            .name("Test pipeline")
                            .pipelineStages(asList(pipelineStage, pipelineStage2, pipelineStage3, pipelineStage4))
                            .build();
    List<String> envIds = PipelineServiceHelper.getEnvironmentIdsForParallelIndex(pipeline, 2);
    assertThat(envIds).hasSize(2).containsExactly(ENV_ID, ENV_ID + 2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyListIfEnvIdsNotPresent() {
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of(EnvStateKeys.envId, ENV_ID))
                                                    .name("test step")
                                                    .type("ENV_STATE")
                                                    .parallelIndex(1)
                                                    .build();
    PipelineStageElement pipelineStage2Element = PipelineStageElement.builder()
                                                     .properties(new HashMap<>())
                                                     .name("test step")
                                                     .type("ENV_STATE")
                                                     .parallelIndex(2)
                                                     .build();
    PipelineStageElement pipelineStage2Element2 = PipelineStageElement.builder()
                                                      .properties(new HashMap<>())
                                                      .name("test step")
                                                      .type("ENV_STATE")
                                                      .parallelIndex(2)
                                                      .build();
    PipelineStageElement pipelineStage3Element = PipelineStageElement.builder()
                                                     .properties(ImmutableMap.of(EnvStateKeys.envId, ENV_ID + 3))
                                                     .name("test step")
                                                     .type("ENV_STATE")
                                                     .parallelIndex(3)
                                                     .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    PipelineStage pipelineStage2 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStage2Element))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    PipelineStage pipelineStage3 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStage2Element2))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    PipelineStage pipelineStage4 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStage3Element))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    Pipeline pipeline = Pipeline.builder()
                            .name("Test pipeline")
                            .pipelineStages(asList(pipelineStage, pipelineStage2, pipelineStage3, pipelineStage4))
                            .build();
    List<String> envIds = PipelineServiceHelper.getEnvironmentIdsForParallelIndex(pipeline, 2);
    assertThat(envIds).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveEnvIdsForParallelStage1() {
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of(EnvStateKeys.envId, "${env}"))
                                                    .name("test step")
                                                    .type("ENV_STATE")
                                                    .workflowVariables(ImmutableMap.of("envName", ENV_ID))
                                                    .parallelIndex(1)
                                                    .build();
    PipelineStageElement pipelineStageElement2 = PipelineStageElement.builder()
                                                     .properties(ImmutableMap.of(EnvStateKeys.envId, "${env}"))
                                                     .name("test step")
                                                     .type("ENV_STATE")
                                                     .workflowVariables(ImmutableMap.of("envName", ENV_ID))
                                                     .parallelIndex(1)
                                                     .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    PipelineStage pipelineStage2 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStageElement2))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    Pipeline pipeline =
        Pipeline.builder().name("Test pipeline").pipelineStages(asList(pipelineStage, pipelineStage2)).build();
    pipeline.getPipelineVariables().add(aVariable().name("env").value("envName").build());
    List<String> envIds = PipelineServiceHelper.getEnvironmentIdsForParallelIndex(pipeline, 1);
    assertThat(envIds).hasSize(2).containsExactly(ENV_ID, ENV_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnEmptyListIfEnvVariableAbsentInPipeline() {
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of(EnvStateKeys.envId, "${env}"))
                                                    .name("test step")
                                                    .type("ENV_STATE")
                                                    .workflowVariables(ImmutableMap.of("envName", ENV_ID))
                                                    .parallelIndex(1)
                                                    .build();
    PipelineStageElement pipelineStageElement2 = PipelineStageElement.builder()
                                                     .properties(ImmutableMap.of(EnvStateKeys.envId, "${env}"))
                                                     .name("test step")
                                                     .type("ENV_STATE")
                                                     .workflowVariables(ImmutableMap.of("envName", ENV_ID))
                                                     .parallelIndex(1)
                                                     .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    PipelineStage pipelineStage2 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStageElement2))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    Pipeline pipeline =
        Pipeline.builder().name("Test pipeline").pipelineStages(asList(pipelineStage, pipelineStage2)).build();
    List<String> envIds = PipelineServiceHelper.getEnvironmentIdsForParallelIndex(pipeline, 1);
    assertThat(envIds).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testUpdateLoopingInfoWhenInfraAsExpression() {
    List<Variable> userVariables = new ArrayList<>();
    Variable infraVar1 = aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("infra1").build();
    userVariables.add(infraVar1);
    OrchestrationWorkflow orchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build();
    Workflow workflow = aWorkflow().orchestrationWorkflow(orchestrationWorkflow).build();
    Map<String, String> workflowVariable = ImmutableMap.of("infra1", "${context.var1}");
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder().workflowVariables(workflowVariable).name("test step").type("ENV_STATE").build();

    PipelineStage pipelineStage = PipelineStage.builder().pipelineStageElements(asList(pipelineStageElement)).build();
    List<String> infraDefIds = new ArrayList<>();
    PipelineServiceHelper.updateLoopingInfo(pipelineStage, workflow, infraDefIds);
    assertThat(pipelineStage.isLooped()).isEqualTo(true);
    assertThat(pipelineStage.getLoopedVarName()).isEqualTo("infra1");
    assertThat(infraDefIds).isEmpty();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldResolveEnvIdsForParallelStage1UsingPipelineVariables() {
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of(EnvStateKeys.envId, "${env}"))
                                                    .name("test step")
                                                    .type("ENV_STATE")
                                                    .workflowVariables(ImmutableMap.of("envName", "anotherEnvId"))
                                                    .parallelIndex(1)
                                                    .build();
    PipelineStageElement pipelineStageElement2 = PipelineStageElement.builder()
                                                     .properties(ImmutableMap.of(EnvStateKeys.envId, "${env}"))
                                                     .name("test step")
                                                     .type("ENV_STATE")
                                                     .workflowVariables(ImmutableMap.of("envName", ENV_ID))
                                                     .parallelIndex(1)
                                                     .build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(asList(pipelineStageElement))
                                      .loopedVarName("infra1")
                                      .looped(true)
                                      .build();
    PipelineStage pipelineStage2 = PipelineStage.builder()
                                       .pipelineStageElements(asList(pipelineStageElement2))
                                       .loopedVarName("infra1")
                                       .looped(true)
                                       .build();
    Pipeline pipeline =
        Pipeline.builder().name("Test pipeline").pipelineStages(asList(pipelineStage, pipelineStage2)).build();
    pipeline.getPipelineVariables().add(aVariable().entityType(ENVIRONMENT).name("envName").value("envId").build());
    List<String> envIds = PipelineServiceHelper.getEnvironmentIdsForParallelIndex(pipeline, 1);
    assertThat(envIds).hasSize(2).containsExactly("anotherEnvId", ENV_ID);
  }
}
