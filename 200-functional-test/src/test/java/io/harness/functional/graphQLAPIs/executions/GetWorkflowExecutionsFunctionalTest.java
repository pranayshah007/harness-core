/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.graphQLAPIs.executions;

import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.rule.OwnerRule.POOJA;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.GraphQLRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.InfrastructureType;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GetWorkflowExecutionsFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private FeatureFlagService featureFlagService;

  private Application application;
  private Service service;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;

  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  private OwnerManager.Owners owners;

  @Before
  public void setUp() {
    owners = ownerManager.create();

    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    if (!featureFlagService.isEnabled(FeatureName.GRAPHQL_DEV, application.getAccountId())) {
      featureFlagService.enableAccount(FeatureName.GRAPHQL_DEV, application.getAccountId());
    }

    assertThat(application).isNotNull();
    service = serviceGenerator.ensureK8sTest(seed, owners, "k8s-service");
    environment = environmentGenerator.ensurePredefined(seed, owners, GENERIC_TEST);
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureType.GCP_KUBERNETES_ENGINE, bearerToken);
  }

  @Test
  @Owner(developers = POOJA, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldGetWorkflowExecutionwithArtifactsAndWorkflow() {
    Workflow workflow = workflowUtils.getRollingK8sWorkflow("GraphQLAPI-test-", service, infrastructureDefinition);
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();

    Artifact artifact = getArtifact(service, service.getAppId());

    ExecutionArgs executionArgs = prepareExecutionArgs(savedWorkflow, Collections.singletonList(artifact));
    WorkflowExecution workflowExecution =
        WorkflowRestUtils.startWorkflow(bearerToken, workflow.getAppId(), workflow.getEnvId(), executionArgs);
    assertThat(workflowExecution).isNotNull();

    String mutation = getGraphqlQueryForExecution(workflowExecution.getUuid());
    Map<Object, Object> response =
        GraphQLRestUtils.executeGraphQLQuery(bearerToken, application.getAccountId(), mutation);

    assertThat(response).isNotEmpty();
    assertThat(response.get("execution")).isNotNull();
    Map<String, Object> executionData = (Map<String, Object>) response.get("execution");
    assertThat(executionData.get("id")).isEqualTo(workflowExecution.getUuid());
    assertThat(executionData.get("artifacts")).isNotNull();
    List<Map<String, Object>> artifacts = (List<Map<String, Object>>) executionData.get("artifacts");
    assertThat(artifacts.get(0).get("id")).isEqualTo(artifact.getUuid());
    assertThat(artifacts.get(0).get("buildNo")).isEqualTo(artifact.getBuildNo());
    Map<String, Object> workflowFromExecution = (Map<String, Object>) executionData.get("workflow");
    assertThat(workflowFromExecution.get("id")).isEqualTo(savedWorkflow.getUuid());
  }

  private ExecutionArgs prepareExecutionArgs(Workflow workflow, List<Artifact> artifacts) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setOrchestrationId(workflow.getUuid());
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
... on WorkflowExecution {
  artifacts {
    id
    buildNo
  }
  workflow {
     id
     name
  }

}
}
}*/ executionId);
  }
}
