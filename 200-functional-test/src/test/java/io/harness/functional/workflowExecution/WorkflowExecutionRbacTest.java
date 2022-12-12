/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.workflowExecution;

import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.PipelineStage.PipelineStageElement;
import static software.wings.sm.StateType.ENV_STATE;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.rule.Owner;
import io.harness.testframework.framework.utils.TestUtils;
import io.harness.testframework.restutils.ExecutionRestUtils;

import software.wings.beans.Application;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WorkflowExecutionRbacTest extends AbstractFunctionalTest {
  private PipelineStage getPipelineStage(
      String envId, String workflowId, Map<String, String> workflowVariables, String entityName) {
    return PipelineStage.builder()
        .name(TestUtils.generateRandomUUID())
        .pipelineStageElements(asList(PipelineStageElement.builder()
                                          .uuid(TestUtils.generateRandomUUID())
                                          .name(entityName)
                                          .type(ENV_STATE.name())
                                          .properties(ImmutableMap.of("envId", envId, "workflowId", workflowId))
                                          .workflowVariables(workflowVariables)
                                          .build()))
        .build();
  }

  private ExecutionArgs setExecutionArgs(
      Pipeline pipeline, List<Artifact> artifacts, ImmutableMap<String, String> workflowFlowVariables) {
    ExecutionArgs executionArgValues = new ExecutionArgs();
    executionArgValues.setWorkflowType(PIPELINE);
    executionArgValues.setPipelineId(pipeline.getUuid());
    executionArgValues.setArtifacts(artifacts);
    executionArgValues.setNotifyTriggeredUserOnly(false);

    executionArgValues.setExecutionCredential(null);
    executionArgValues.setExcludeHostsWithSameArtifact(false);
    executionArgValues.setWorkflowVariables(workflowFlowVariables);
    return executionArgValues;
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(FunctionalTests.class)
  public void shouldExecutePipelineNegativePositive() {
    Workflow workflow = new Workflow();
    workflow.setUuid(TestUtils.generateRandomUUID());
    Workflow workflow1 = new Workflow();
    workflow1.setUuid(TestUtils.generateRandomUUID());

    Application application = new Application();

    application.setUuid(TestUtils.generateRandomUUID());
    application.setAppId(application.getUuid());

    Artifact artifact = new Artifact();

    String envId = TestUtils.generateRandomUUID();

    Map<String, String> workflowVariables = new HashMap<>();
    List<PipelineStage> pipelineStages =
        asList(getPipelineStage(envId, workflow.getUuid(), workflowVariables, "Dev stage1"),
            getPipelineStage(envId, workflow1.getUuid(), workflowVariables, "Dev stage2"));

    Pipeline pipeline = Pipeline.builder()
                            .uuid(TestUtils.generateRandomUUID())
                            .appId(application.getUuid())
                            .name("pipeline1" + System.currentTimeMillis())
                            .description("Sample Pipeline")
                            .pipelineStages(pipelineStages)
                            .accountId(application.getAccountId())
                            .build();

    ExecutionArgs executionArgs = setExecutionArgs(pipeline, asList(artifact), null);
    executionArgs.setOrchestrationId(pipeline.getUuid());

    Map<String, Object> pipelineExecution =
        ExecutionRestUtils.runPipeline(bearerToken, application.getAppId(), envId, pipeline.getUuid(), executionArgs);

    assertThat(pipelineExecution).isNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(FunctionalTests.class)
  public void shouldExecuteWorkflowNegativePositive() {
    Workflow workflow = new Workflow();
    workflow.setUuid(TestUtils.generateRandomUUID());
    Workflow workflow1 = new Workflow();
    workflow1.setUuid(TestUtils.generateRandomUUID());

    Application application = new Application();
    Artifact artifact = new Artifact();

    application.setUuid(TestUtils.generateRandomUUID());
    application.setAppId(application.getUuid());

    String envId = TestUtils.generateRandomUUID();

    Map<String, String> workflowVariables = new HashMap<>();
    List<PipelineStage> pipelineStages =
        asList(getPipelineStage(envId, workflow.getUuid(), workflowVariables, "Dev stage1"),
            getPipelineStage(envId, workflow1.getUuid(), workflowVariables, "Dev stage2"));

    Pipeline pipeline = Pipeline.builder()
                            .uuid(TestUtils.generateRandomUUID())
                            .appId(application.getUuid())
                            .name("pipeline1" + System.currentTimeMillis())
                            .description("Sample Pipeline")
                            .pipelineStages(pipelineStages)
                            .accountId(application.getAccountId())
                            .build();

    ExecutionArgs executionArgs = setExecutionArgs(pipeline, asList(artifact), null);
    executionArgs.setOrchestrationId(pipeline.getUuid());

    WorkflowExecution workflowExecution =
        ExecutionRestUtils.runWorkflow(bearerToken, application.getUuid(), envId, executionArgs);

    assertThat(workflowExecution).isNull();
  }
}
