/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.deploymentTags;

import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AADITI;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.sm.StateType.ENV_STATE;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.ff.FeatureFlagService;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.PipelineGenerator;
import io.harness.generator.Randomizer;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.restutils.PipelineRestUtils;

import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineBuilder;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.WorkflowExecutionServiceImpl;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DeploymentTagsFunctionalTest extends AbstractFunctionalTest {
  private static final String WORKFLOW_VARIABLE_NAME = "var_workflow";

  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private WorkflowExecutionServiceImpl workflowExecutionService;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PipelineGenerator pipelineGenerator;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    owners.obtainApplication(
        () -> applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST));

    resetCache(owners.obtainAccount().getUuid());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("enable this when pr-portal-functional-test job starts working")
  public void runWorkflowAndValidateTags() {
    // create build workflow with variable
    Workflow workflow1 = buildWorkflow(true);
    Workflow workflow2 = buildWorkflow(true);
    final Application application = owners.obtainApplication();
    final Environment environment = owners.obtainEnvironment();

    // attach tag(key:value) to workflow1 referring the above workflow variable
    attachTagToEntity(application.getUuid(), EntityType.WORKFLOW, workflow1.getUuid(), "myworkflow",
        format("${workflow.variables.%s}", WORKFLOW_VARIABLE_NAME));
    // attach tag(label) to workflow2 referring the above workflow variable
    attachTagToEntity(application.getUuid(), EntityType.WORKFLOW, workflow2.getUuid(),
        format("${workflow.variables.%s}", WORKFLOW_VARIABLE_NAME), "");

    // Run the workflows
    Map<String, String> workflowVariables1 = ImmutableMap.of(WORKFLOW_VARIABLE_NAME, workflow1.getName());
    WorkflowExecution workflowExecution1 =
        executeWorkflow(workflow1.getUuid(), application.getUuid(), environment.getUuid(), workflowVariables1);
    WorkflowExecution workflowExecution2 =
        executeWorkflow(workflow1.getUuid(), application.getUuid(), environment.getUuid(), workflowVariables1);

    Map<String, String> workflowVariables2 = ImmutableMap.of(WORKFLOW_VARIABLE_NAME, workflow2.getName());
    WorkflowExecution workflowExecution3 =
        executeWorkflow(workflow2.getUuid(), application.getUuid(), environment.getUuid(), workflowVariables2);
    WorkflowExecution workflowExecution4 =
        executeWorkflow(workflow2.getUuid(), application.getUuid(), environment.getUuid(), workflowVariables2);

    // fetch deployments with tags matching key:value
    List<String> executions = fetchExecutionsWithTags(
        "{\"harnessTagFilter\":{\"matchAll\":false,\"conditions\":[{\"name\":\"myworkflow\",\"operator\":\"IN\",\"values\":[\""
        + workflow1.getName() + "\"]}]}}");
    assertThat(executions).isNotEmpty();
    assertThat(executions.size()).isEqualTo(2);
    assertThat(executions).containsAll(asList(workflowExecution1.getUuid(), workflowExecution2.getUuid()));

    // fetch deployments with tags matching label
    executions = fetchExecutionsWithTags("{\"harnessTagFilter\":{\"matchAll\":false,\"conditions\":[{\"name\":\""
        + workflow2.getName() + "\",\"operator\":\"EXISTS\"}]}}");
    assertThat(executions).isNotEmpty();
    assertThat(executions.size()).isEqualTo(2);
    assertThat(executions).containsAll(asList(workflowExecution3.getUuid(), workflowExecution4.getUuid()));

    // cleanup workflows
    cleanUpWorkflow(application.getUuid(), workflow1.getUuid());
    cleanUpWorkflow(application.getUuid(), workflow2.getUuid());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("enable this when pr-portal-functional-test job starts working")
  public void runPipelineAndValidateTags() {
    Workflow workflow = buildWorkflow(false);
    PipelineBuilder builder =
        Pipeline.builder()
            .name("Pipeline - " + generateUuid())
            .pipelineStages(asList(
                PipelineStage.builder()
                    .pipelineStageElements(asList(
                        PipelineStageElement.builder()
                            .name("Build")
                            .type(ENV_STATE.name())
                            .properties(ImmutableMap.of("workflowId", workflow.getUuid(), "envId", workflow.getEnvId()))
                            .build()))
                    .build()));
    final Pipeline buildPipeline = pipelineGenerator.ensurePipeline(seed, owners, builder.build());
    assertThat(buildPipeline).isNotNull();
    final Application application = owners.obtainApplication();
    final Environment environment = owners.obtainEnvironment();

    // attach tag to Pipeline
    attachTagToEntity(
        application.getUuid(), EntityType.PIPELINE, buildPipeline.getUuid(), "mypipeline", buildPipeline.getName());

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(PIPELINE);
    executionArgs.setPipelineId(buildPipeline.getUuid());

    resetCache(application.getUuid());
    resetCache(getAccount().getUuid());

    WorkflowExecution workflowExecution =
        runPipeline(bearerToken, application.getAppId(), environment.getUuid(), executionArgs);

    // fetch deployments with tags matching key:value
    List<String> executions = fetchExecutionsWithTags(
        "{\"harnessTagFilter\":{\"matchAll\":false,\"conditions\":[{\"name\":\"mypipeline\",\"operator\":\"IN\",\"values\":[\""
        + buildPipeline.getName() + "\"]}]}}");
    assertThat(executions).isNotEmpty();
    assertThat(executions.size()).isEqualTo(1);
    assertThat(executions).containsAll(asList(workflowExecution.getUuid()));
    PipelineRestUtils.deletePipeline(application.getUuid(), buildPipeline.getUuid(), bearerToken);
  }

  private Workflow buildWorkflow(boolean withVariable) {
    CanaryOrchestrationWorkflowBuilder builder = aCanaryOrchestrationWorkflow()
                                                     .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                                     .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build());
    if (withVariable) {
      builder.withUserVariables(
          singletonList(aVariable().name(WORKFLOW_VARIABLE_NAME).type(VariableType.TEXT).mandatory(true).build()));
    }
    // create a workflow
    return workflowGenerator.ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Workflow - " + generateUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(builder.build())
            .build());
  }

  @NotNull
  public WorkflowExecution executeWorkflow(
      String workflowId, String appId, String envId, Map<String, String> workflowVariables) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setExecutionCredential(SSHExecutionCredential.Builder.aSSHExecutionCredential()
                                             .withExecutionType(ExecutionCredential.ExecutionType.SSH)
                                             .build());
    executionArgs.setOrchestrationId(workflowId);
    executionArgs.setArtifacts(Collections.<Artifact>emptyList());
    executionArgs.setWorkflowVariables(workflowVariables);
    return getWorkflowExecution(bearerToken, appId, envId, executionArgs);
  }

  private List<String> fetchExecutionsWithTags(String tagFilter) {
    List<String> workflowExecutionIds = new ArrayList<>();
    JsonPath jsonPath = Setup.portal()
                            .auth()
                            .oauth2(bearerToken)
                            .queryParam("accountId", getAccount().getUuid())
                            .queryParam("withTags", true)
                            .queryParam("tagFilter", tagFilter)
                            .contentType(ContentType.JSON)
                            .get("/executions")
                            .getBody()
                            .jsonPath();

    ArrayList<HashMap<String, String>> hashMaps =
        (ArrayList<HashMap<String, String>>) jsonPath.getMap("resource").get("response");
    for (HashMap<String, String> data : hashMaps) {
      workflowExecutionIds.add(data.get("uuid"));
    }
    return workflowExecutionIds;
  }

  private void attachTagToEntity(String appId, EntityType entityType, String entityId, String key, String value) {
    HarnessTagLink tagLink =
        HarnessTagLink.builder().entityId(entityId).entityType(entityType).key(key).value(value).build();

    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("accountId", getAccount().getUuid())
        .queryParam("appId", appId)
        .contentType(ContentType.JSON)
        .body(tagLink)
        .post("/tags/attach");
  }

  private void cleanUpWorkflow(String appId, String workflowId) {
    assertThat(appId).isNotNull();
    assertThat(workflowId).isNotNull();
    // Clean up resources
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("appId", appId)
        .pathParam("workflowId", workflowId)
        .delete("/workflows/{workflowId}")
        .then()
        .statusCode(200);
  }
}
