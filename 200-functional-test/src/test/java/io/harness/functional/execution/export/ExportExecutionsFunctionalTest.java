/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.execution.export;

import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.execution.export.request.ExportExecutionsRequest.Status;
import io.harness.execution.export.request.ExportExecutionsRequestLimitChecks;
import io.harness.execution.export.request.ExportExecutionsRequestSummary;
import io.harness.execution.export.request.ExportExecutionsUserParams;
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
import io.harness.testframework.restutils.ExportExecutionsRestUtils;
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
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExportExecutionsFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowUtils workflowUtils;

  private Application application;
  private Service service;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;

  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  private OwnerManager.Owners owners;
  private Pipeline savedPipeline;
  private WorkflowExecution workflowExecution;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
    service = serviceGenerator.ensureK8sTest(seed, owners, "k8s-service");
    environment = environmentGenerator.ensurePredefined(seed, owners, GENERIC_TEST);
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureType.GCP_KUBERNETES_ENGINE, bearerToken);
    createPipeline();
  }

  private void createPipeline() {
    Workflow workflow = workflowUtils.getRollingK8sWorkflow(
        "gcp-k8s-templatized-export-executions-", service, infrastructureDefinition);
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();

    Pipeline pipeline = new Pipeline();
    pipeline.setName("Export Executions Test - " + System.currentTimeMillis());
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

    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, service.getAppId(), service.getArtifactStreamIds().get(0), 0);
    ExecutionArgs executionArgs = prepareExecutionArgs(savedPipeline, Collections.singletonList(artifact));
    workflowExecution = startPipeline(application.getAppId(), environment.getUuid(), executionArgs);
    assertThat(workflowExecution).isNotNull();
    waitForWorkflowExecutionCompletion();
  }

  @Test
  @Owner(developers = GARVIT, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldExportExecutions() {
    ExportExecutionsRequestLimitChecks limitChecks = ExportExecutionsRestUtils.getLimitChecks(
        bearerToken, application.getAccountId(), application.getUuid(), savedPipeline.getUuid());
    assertThat(limitChecks).isNotNull();
    assertThat(limitChecks.getExecutionCount().getValue()).isEqualTo(1);

    ExportExecutionsRequestSummary requestSummary =
        ExportExecutionsRestUtils.export(bearerToken, application.getAccountId(), application.getUuid(),
            savedPipeline.getUuid(), ExportExecutionsUserParams.builder().build());
    assertThat(requestSummary).isNotNull();
    assertThat(requestSummary.getRequestId()).isNotNull();

    String requestId = requestSummary.getRequestId();
    assertThat(waitForFinalExportStatus(requestId)).isEqualTo(Status.READY);

    byte[] bs = ExportExecutionsRestUtils.downloadFile(bearerToken, application.getAccountId(), requestId);
    assertThat(bs.length).isGreaterThan(0);
  }

  @Test
  @Owner(developers = GARVIT, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldExportExecutionsGraphQL() {
    ExportExecutionsRequestLimitChecks limitChecks = ExportExecutionsRestUtils.getLimitChecks(
        bearerToken, application.getAccountId(), application.getUuid(), savedPipeline.getUuid());
    assertThat(limitChecks).isNotNull();
    assertThat(limitChecks.getExecutionCount().getValue()).isEqualTo(1);

    String query = getGraphqlQueryForExportExecutions();
    Map<Object, Object> response = GraphQLRestUtils.executeGraphQLQuery(bearerToken, application.getAccountId(), query);

    assertThat(response).isNotEmpty();
    Map<String, Object> exportExecutions = (Map<String, Object>) response.get("exportExecutions");
    String requestId = (String) exportExecutions.get("requestId");
    assertThat(requestId).isNotNull();
    assertThat(waitForFinalExportStatus(requestId)).isEqualTo(Status.READY);

    byte[] bs = ExportExecutionsRestUtils.downloadFile(bearerToken, application.getAccountId(), requestId);
    assertThat(bs.length).isGreaterThan(0);
  }

  private WorkflowExecution startPipeline(String appId, String environmentId, ExecutionArgs executionArgs) {
    return workflowExecutionService.triggerEnvExecution(appId, environmentId, executionArgs, null);
  }

  private void waitForWorkflowExecutionCompletion() {
    Awaitility.await()
        .atMost(5, TimeUnit.MINUTES)
        .pollInterval(5, TimeUnit.SECONDS)
        .until(()
                   -> ExecutionStatus.isFinalStatus(
                       workflowExecutionService.getWorkflowExecution(application.getUuid(), workflowExecution.getUuid())
                           .getStatus()));
  }

  private Status waitForFinalExportStatus(String requestId) {
    Awaitility.await().atMost(5, TimeUnit.MINUTES).pollInterval(5, TimeUnit.SECONDS).until(() -> {
      ExportExecutionsRequestSummary requestSummary =
          ExportExecutionsRestUtils.getStatus(bearerToken, application.getAccountId(), requestId);
      Status status = requestSummary.getStatus();
      return status != Status.QUEUED;
    });

    return ExportExecutionsRestUtils.getStatus(bearerToken, application.getAccountId(), requestId).getStatus();
  }

  private ExecutionArgs prepareExecutionArgs(Pipeline pipeline, List<Artifact> artifacts) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.PIPELINE);
    executionArgs.setPipelineId(pipeline.getUuid());
    executionArgs.setArtifacts(artifacts);
    return executionArgs;
  }

  private String getGraphqlQueryForExportExecutions() {
    return $GQL(/*
mutation {
  exportExecutions(input: {
    filters: [{
      application: {operator: EQUALS, values: ["%s"]},
      pipeline: {operator: EQUALS, values: ["%s"]}
    }]
  }) {
    requestId
  }
}*/ application.getUuid(), savedPipeline.getUuid());
  }
}
