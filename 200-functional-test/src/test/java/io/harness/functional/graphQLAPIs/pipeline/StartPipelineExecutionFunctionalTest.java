/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.graphQLAPIs.pipeline;

import static io.harness.functional.WorkflowUtils.getTemplateExpressionsForEnv;
import static io.harness.functional.WorkflowUtils.getTemplateExpressionsForInfraDefinition;
import static io.harness.functional.WorkflowUtils.getTemplateExpressionsForService;
import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.rule.OwnerRule.POOJA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.FeatureName;
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
import software.wings.beans.InfrastructureType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StartPipelineExecutionFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowUtils workflowUtils;

  private Application application;
  private Service service;
  private Environment environment;
  private String workflowId;
  private Pipeline savedPipeline;
  private InfrastructureDefinition infrastructureDefinition;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;

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

    Workflow workflow =
        workflowUtils.getRollingK8sWorkflow("gcp-k8s-templatized-pooja-", service, infrastructureDefinition);
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();

    // Templatize env and infra of  the workflow
    savedWorkflow.setTemplateExpressions(Arrays.asList(getTemplateExpressionsForEnv(),
        getTemplateExpressionsForService(), getTemplateExpressionsForInfraDefinition("${InfraDefinition_Kubernetes}")));

    Workflow templatizedWorkflow =
        WorkflowRestUtils.updateWorkflow(bearerToken, application.getAccountId(), application.getUuid(), savedWorkflow);
    assertThat(templatizedWorkflow.isEnvTemplatized()).isTrue();
    assertThat(templatizedWorkflow.isTemplatized()).isTrue();
    workflowId = templatizedWorkflow.getUuid();

    String pipelineName = "GraphQLAPI Test - " + System.currentTimeMillis();

    Pipeline pipeline = new Pipeline();
    pipeline.setName(pipelineName);
    pipeline.setDescription("description");

    Pipeline createdPipeline =
        PipelineRestUtils.createPipeline(application.getAppId(), pipeline, getAccount().getUuid(), bearerToken);
    assertThat(createdPipeline).isNotNull();

    ImmutableMap<String, String> workflowVariables = ImmutableMap.<String, String>builder()
                                                         .put("Environment", "${env}")
                                                         .put("Service", "${service}")
                                                         .put("InfraDefinition_Kubernetes", "${infra}")
                                                         .build();

    List<PipelineStage> pipelineStages = new ArrayList<>();
    PipelineStage executionStage =
        PipelineUtils.prepareExecutionStage(environment.getUuid(), templatizedWorkflow.getUuid(), workflowVariables);
    pipelineStages.add(executionStage);
    createdPipeline.setPipelineStages(pipelineStages);

    savedPipeline = PipelineRestUtils.updatePipeline(application.getAppId(), createdPipeline, bearerToken);
    assertThat(createdPipeline).isNotNull();
  }

  @Test
  @Owner(developers = POOJA, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldTriggerTemplatisedPipeline() {
    Artifact artifact = getArtifact(service, service.getAppId());

    ImmutableMap<String, String> pipelineVariables = ImmutableMap.<String, String>builder()
                                                         .put("env", environment.getName())
                                                         .put("service", service.getName())
                                                         .put("infra", infrastructureDefinition.getName())
                                                         .build();

    String mutation = getGraphqlQueryForPipeline("123", application.getAppId(), savedPipeline.getUuid(),
        pipelineVariables, artifact.getUuid(), service.getName());
    Map<Object, Object> response =
        GraphQLRestUtils.executeGraphQLQuery(bearerToken, application.getAccountId(), mutation);

    assertThat(response).isNotEmpty();
    assertThat(response.get("startExecution")).isNotNull();
    Map<String, Object> executionData = (Map<String, Object>) response.get("startExecution");
    assertThat(executionData.get("clientMutationId")).isEqualTo("123");
    assertThat(executionData.get("execution")).isNotNull();
  }

  private String getGraphqlQueryForPipeline(String clientMutationId, String appId, String pipelineId,
      ImmutableMap<String, String> workflowVariables, String artifactId, String serviceName) {
    String variableInputsQuery = getVariableInputsQuery(workflowVariables);
    String serviceInputQuery =
        $GQL(/*[{
name: "%s"
artifactValueInput: {
valueType: ARTIFACT_ID
artifactId: {
artifactId: "%s"
}}}]*/ serviceName, artifactId);

    String mutationInputQuery = $GQL(/*
{
entityId: "%s",
applicationId: "%s",
executionType: PIPELINE,
variableInputs: %s,
serviceInputs: %s,
clientMutationId: "%s"
}*/ pipelineId, appId, variableInputsQuery, serviceInputQuery, clientMutationId);

    return $GQL(/*
mutation{
startExecution(input:%s) {
clientMutationId
execution {
 status
}
}
}*/ mutationInputQuery);
  }

  @NotNull
  private String getVariableInputsQuery(ImmutableMap<String, String> workflowVariables) {
    List<String> variableInputs = new ArrayList<>();
    for (Map.Entry<String, String> entry : workflowVariables.entrySet()) {
      String queryVariableInput = $GQL(/*{
      name: "%s"
      variableValue: {
          value: "%s"
          type: NAME
          }}*/ entry.getKey(), entry.getValue());
      variableInputs.add(queryVariableInput);
    }
    return "[" + String.join(",", variableInputs) + "]";
  }

  private Artifact getArtifact(Service service, String appId) {
    return ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);
  }

  @Test
  @Owner(developers = POOJA, intermittent = true)
  @Category(FunctionalTests.class)
  public void getExecutionInputsPipeline() {
    ImmutableMap<String, String> pipelineVariables = ImmutableMap.<String, String>builder()
                                                         .put("env", environment.getName())
                                                         .put("service", service.getName())
                                                         .put("infra", infrastructureDefinition.getName())
                                                         .build();

    String query =
        getGraphqlQueryForExecutionInputs(application.getAppId(), savedPipeline.getUuid(), pipelineVariables);
    Map<Object, Object> response = GraphQLRestUtils.executeGraphQLQuery(bearerToken, application.getAccountId(), query);

    assertThat(response).isNotEmpty();
    assertThat(response.get("executionInputs")).isNotNull();
    Map<String, Object> executionInputs = (Map<String, Object>) response.get("executionInputs");
    assertThat(executionInputs.get("serviceInputs")).isNotNull();
    List<Object> serviceInputs = (List<Object>) executionInputs.get("serviceInputs");

    assertThat(serviceInputs.size()).isEqualTo(1);
    Map<String, Object> serviceInput0 = (Map<String, Object>) serviceInputs.get(0);
    assertThat(serviceInput0.get("id")).isEqualTo(service.getUuid());
  }

  private String getGraphqlQueryForExecutionInputs(
      String appId, String pipelineId, ImmutableMap<String, String> pipelineVariables) {
    String variableInputsQuery = getVariableInputsQuery(pipelineVariables);

    return $GQL(/*
query{
executionInputs(entityId: "%s",
applicationId: "%s",
executionType: PIPELINE,
variableInputs: %s
){
    serviceInputs {
      id
      name
      artifactType
    }
  }
}*/ pipelineId, appId, variableInputsQuery);
  }

  @After
  public void destroy() {
    pipelineService.deletePipeline(application.getUuid(), savedPipeline.getUuid());
    workflowService.deleteWorkflow(application.getUuid(), workflowId);
  }
}
