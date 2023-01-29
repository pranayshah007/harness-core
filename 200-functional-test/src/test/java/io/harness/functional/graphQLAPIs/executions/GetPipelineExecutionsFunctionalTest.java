/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.graphQLAPIs.executions;

import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.ff.FeatureFlagService;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.framework.utils.PipelineUtils;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.GraphQLRestUtils;
import io.harness.testframework.restutils.PipelineRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.InfrastructureType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GetPipelineExecutionsFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private FeatureFlagService featureFlagService;

  private Application application;
  private Service service;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;

  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  private OwnerManager.Owners owners;
  private Pipeline savedPipeline;
  private String workflowExecutionId;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    assertThat(application).isNotNull();

    if (!featureFlagService.isEnabled(FeatureName.GRAPHQL_DEV, application.getAccountId())) {
      featureFlagService.enableAccount(FeatureName.GRAPHQL_DEV, application.getAccountId());
    }

    service = serviceGenerator.ensureK8sTest(seed, owners, "k8s-service");
    environment = environmentGenerator.ensurePredefined(seed, owners, GENERIC_TEST);
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureType.GCP_KUBERNETES_ENGINE, bearerToken);

    createPipeline();
  }

  private void createPipeline() {
    Workflow workflow =
        workflowUtils.getRollingK8sWorkflow("gcp-k8s-templatized-pooja-", service, infrastructureDefinition);
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();
    workflowExecutionId = savedWorkflow.getUuid();

    String pipelineName = "GraphQLAPI Test - " + System.currentTimeMillis();

    Pipeline pipeline = new Pipeline();
    pipeline.setName(pipelineName);
    pipeline.setDescription("description");

    Pipeline createdPipeline =
        PipelineRestUtils.createPipeline(application.getAppId(), pipeline, getAccount().getUuid(), bearerToken);
    assertThat(createdPipeline).isNotNull();

    List<PipelineStage> pipelineStages = new ArrayList<>();
    PipelineStage executionStage =
        PipelineUtils.prepareExecutionStage(environment.getUuid(), savedWorkflow.getUuid(), new HashMap<>());
    pipelineStages.add(executionStage);
    createdPipeline.setPipelineStages(pipelineStages);

    savedPipeline = PipelineRestUtils.updatePipeline(application.getAppId(), createdPipeline, bearerToken);
  }

  @Test
  @Owner(developers = POOJA, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldGetPipelineExecutionWithPipeline() {
    Artifact artifact = getArtifact(service, service.getAppId());
    ExecutionArgs executionArgs = prepareExecutionArgs(savedPipeline, Collections.singletonList(artifact));
    WorkflowExecution workflowExecution = startPipeline(application.getAppId(), environment.getUuid(), executionArgs);
    assertThat(workflowExecution).isNotNull();

    String query = getGraphqlQueryForExecution(workflowExecution.getUuid());
    Map<Object, Object> response = GraphQLRestUtils.executeGraphQLQuery(bearerToken, application.getAccountId(), query);

    assertThat(response).isNotEmpty();
    assertThat(response.get("execution")).isNotNull();
    Map<String, Object> executionData = (Map<String, Object>) response.get("execution");
    assertThat(executionData.get("id")).isEqualTo(workflowExecution.getUuid());
    Map<String, Object> workflowFromExecution = (Map<String, Object>) executionData.get("pipeline");
    assertThat(workflowFromExecution.get("id")).isEqualTo(savedPipeline.getUuid());
  }

  @Test
  @Owner(developers = PRABU, intermittent = true)
  @Category(FunctionalTests.class)
  @Ignore("enable this when we have the infra setup")
  public void shouldGetMemberWorkflowExecutionWithPipeline() {
    Artifact artifact = getArtifact(service, service.getAppId());
    ExecutionArgs executionArgs = prepareExecutionArgs(savedPipeline, Collections.singletonList(artifact));
    WorkflowExecution workflowExecution = startPipeline(application.getAppId(), environment.getUuid(), executionArgs);
    final String executionId = workflowExecution.getUuid();
    Awaitility.await().atMost(120, TimeUnit.SECONDS).pollInterval(10, TimeUnit.SECONDS).until(() -> {
      final WorkflowExecution pipelineExecution =
          workflowExecutionService.getWorkflowExecution(savedPipeline.getAppId(), executionId);
      return pipelineExecution != null && pipelineExecution.getStatus() != ExecutionStatus.NEW;
    });
    workflowExecution =
        workflowExecutionService.getWorkflowExecution(savedPipeline.getAppId(), workflowExecution.getUuid());
    assertThat(workflowExecution).isNotNull();

    String query = getGraphqlQueryForExecutions(workflowExecution.getUuid());
    Map<Object, Object> response = GraphQLRestUtils.executeGraphQLQuery(bearerToken, application.getAccountId(), query);

    assertThat(response).isNotEmpty();
    assertThat(response.get("executions")).isNotNull();
    Map<String, Object> executionsData = (Map<String, Object>) response.get("executions");
    assertThat(executionsData.get("nodes")).isNotNull();
    Map<String, Object> workflowNode = ((List<Map<String, Object>>) executionsData.get("nodes")).get(0);
    assertThat(workflowNode.get("workflow")).isNotNull();
    Map<String, Object> workflowData = (Map<String, Object>) workflowNode.get("workflow");
    assertThat(workflowData.get("id")).isEqualTo(workflowExecutionId);
    assertThat(workflowNode.get("application")).isNotNull();
    Map<String, Object> applicationData = (Map<String, Object>) workflowNode.get("application");
    assertThat(applicationData.get("id")).isEqualTo(savedPipeline.getAppId());
  }

  private WorkflowExecution startPipeline(String appId, String environmentId, ExecutionArgs executionArgs) {
    return workflowExecutionService.triggerEnvExecution(appId, environmentId, executionArgs, null);
  }

  private ExecutionArgs prepareExecutionArgs(Pipeline pipeline, List<Artifact> artifacts) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);
    executionArgs.setPipelineId(pipeline.getUuid());
    executionArgs.setArtifacts(artifacts);
    return executionArgs;
  }

  private Artifact getArtifact(Service service, String appId) {
    return ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);
  }

  private String getGraphqlQueryForExecution(String executionId) {
    return $GQL(/*
query{
execution(executionId: "%s"){
status
id
... on PipelineExecution {
  pipeline {
     id
     name
  }

}
}
}*/ executionId);
  }

  private String getGraphqlQueryForExecutions(String executionId) {
    return $GQL(/*
query
{
  executions(filters: [
    {status: {operator: NOT_IN, values: ["SUCCESS", "PAUSED"]}},
    {pipelineExecutionId: {operator: EQUALS, values: ["%s"]}}
  ], limit: 10) {
    nodes {
      application {
        name
        id
      }
      status
      id
      ...on WorkflowExecution{
          workflow{
                                                name
                                                id
        }
      }
    }
  }
}*/ executionId);
  }
}
