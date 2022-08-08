/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.servicevariables;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.rule.OwnerRule.NATARAJA;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.generator.artifactstream.ArtifactStreamManager.ArtifactStreams;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.framework.utils.WorkflowUtils;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.ServiceVariablesUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.ExecutionDataValue;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionCredential.ExecutionType;
import software.wings.beans.GraphNode;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.restassured.path.json.JsonPath;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
@OwnedBy(CDC)
@Slf4j
public class ServiceVariablesTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ArtifactStreamManager artifactStreamManager;

  final String NORMAL_TEXT = "normalText";
  final String OVERRIDABLE_TEXT = "overridableText";
  final String ENV_OVERRIDDEN_TEXT = "envOverridableText";

  private Application application;
  private Service service;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;
  private ArtifactStream artifactStream;
  final Seed seed = new Seed(0);
  Owners owners;

  @Before
  public void setUp() {
    owners = ownerManager.create();

    application = applicationGenerator.ensurePredefined(seed, owners, Applications.FUNCTIONAL_TEST);
    assertThat(application).isNotNull();

    service = serviceGenerator.ensurePredefined(seed, owners, Services.FUNCTIONAL_TEST);
    assertThat(service).isNotNull();

    environment = environmentGenerator.ensurePredefined(seed, owners, Environments.FUNCTIONAL_TEST);
    assertThat(environment).isNotNull();

    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitions.AWS_SSH_FUNCTIONAL_TEST);
    assertThat(infrastructureDefinition).isNotNull();

    artifactStream = artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.ARTIFACTORY_ECHO_WAR);
    assertThat(artifactStream).isNotNull();
  }

  @Test
  @Owner(developers = NATARAJA)
  @Category(FunctionalTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void variablesTest() {
    log.info("Starting the test");
    ServiceVariable normalServiceVariable = new ServiceVariable();
    normalServiceVariable.setAccountId(getAccount().getUuid());
    normalServiceVariable.setAppId(service.getAppId());
    normalServiceVariable.setValue(NORMAL_TEXT.toCharArray());
    normalServiceVariable.setName(NORMAL_TEXT);
    normalServiceVariable.setEntityType(EntityType.SERVICE);
    normalServiceVariable.setType(ServiceVariableType.TEXT);
    normalServiceVariable.setEntityId(service.getUuid());

    ServiceVariable overridableVariables = new ServiceVariable();
    overridableVariables.setAccountId(getAccount().getUuid());
    overridableVariables.setAppId(service.getAppId());
    overridableVariables.setValue(OVERRIDABLE_TEXT.toCharArray());
    overridableVariables.setName(OVERRIDABLE_TEXT);
    overridableVariables.setEntityType(EntityType.SERVICE);
    overridableVariables.setType(ServiceVariableType.TEXT);
    overridableVariables.setEntityId(service.getUuid());
    ServiceVariable addedNormalServiceVariable = null;
    ServiceVariable addedOverridableServiceVariable = null;
    ServiceVariable addedEnvOverriddenVariable = null;

    log.info("Adding service variable : " + normalServiceVariable.getName());
    addedNormalServiceVariable = ServiceVariablesUtils.addOrGetServiceVariable(bearerToken, normalServiceVariable);
    assertThat(addedNormalServiceVariable).isNotNull();
    log.info("Adding service variable : " + overridableVariables.getName());
    addedOverridableServiceVariable = ServiceVariablesUtils.addOrGetServiceVariable(bearerToken, overridableVariables);
    assertThat(addedOverridableServiceVariable).isNotNull();
    overridableVariables.setEntityType(EntityType.ENVIRONMENT);
    overridableVariables.setEntityId(environment.getUuid());
    overridableVariables.setAppId(environment.getAppId());
    overridableVariables.setValue(ENV_OVERRIDDEN_TEXT.toCharArray());
    log.info("Adding environment service variable : " + overridableVariables.getName());
    addedEnvOverriddenVariable = ServiceVariablesUtils.addOrGetServiceVariable(bearerToken, overridableVariables);
    assertThat(addedEnvOverriddenVariable).isNotNull();

    WorkflowPhase phase1 =
        aWorkflowPhase().serviceId(service.getUuid()).infraDefinitionId(infrastructureDefinition.getUuid()).build();
    final String variablesTestName = "Variables Test";

    log.info("Creating workflow with canary orchestration : " + variablesTestName);
    Workflow workflow =
        aWorkflow()
            .name("Variables Test")
            .description("Variables Test")
            .serviceId(service.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .envId(environment.getUuid())
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withWorkflowPhases(ImmutableList.of(phase1)).build())
            .build();

    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, AccountGenerator.ACCOUNT_ID, application.getUuid(), workflow);

    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getUuid()).isNotEmpty();
    assertThat(savedWorkflow.getWorkflowType()).isEqualTo(ORCHESTRATION);

    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, application.getUuid(), artifactStream.getUuid(), 0);

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(savedWorkflow.getWorkflowType());
    executionArgs.setExecutionCredential(
        SSHExecutionCredential.Builder.aSSHExecutionCredential().withExecutionType(ExecutionType.SSH).build());
    executionArgs.setOrchestrationId(savedWorkflow.getUuid());
    executionArgs.setArtifacts(Collections.singletonList(artifact));

    log.info("Modifying Workflow Phase to add HTTP command in Verify Step of Phase 1");

    WorkflowUtils.modifyPhases(bearerToken, savedWorkflow, application.getUuid());

    log.info("Workflow execution starts");

    WorkflowExecution workflowExecution =
        WorkflowRestUtils.startWorkflow(bearerToken, application.getUuid(), environment.getUuid(), executionArgs);
    assertThat(workflowExecution).isNotNull();

    assertThat(workflowExecution).isNotNull();

    log.info("Waiting for 2 mins until the workflow execution is complete");

    Awaitility.await()
        .atMost(120, TimeUnit.SECONDS)
        .pollInterval(5, TimeUnit.SECONDS)
        .until(()
                   -> Setup.portal()
                          .auth()
                          .oauth2(bearerToken)
                          .queryParam("appId", application.getUuid())
                          .get("/executions/" + workflowExecution.getUuid())
                          .jsonPath()
                          .<String>getJsonObject("resource.status")
                          .equals(ExecutionStatus.SUCCESS.name()));

    log.info("Workflow execution completed");

    WorkflowExecution completedWorkflowExecution =
        workflowExecutionService.getExecutionDetails(application.getUuid(), workflowExecution.getUuid(), true, false);

    log.info("Validation starts");

    assertThat(completedWorkflowExecution.getExecutionNode().getStatus()).isEqualTo("SUCCESS");
    assertThat(completedWorkflowExecution.getName().equals(variablesTestName)).isTrue();
    assertThat(completedWorkflowExecution.getExecutionNode().getNext().getName().equals("Phase 1")).isTrue();
    assertThat(completedWorkflowExecution.getExecutionNode().getNext().getStatus().equals("SUCCESS")).isTrue();
    assertThat(completedWorkflowExecution.getExecutionNode().getNext().getGroup().getElements().size() == 1).isTrue();

    GraphNode gNode = completedWorkflowExecution.getExecutionNode().getNext().getGroup().getElements().get(0);
    GraphNode verificationPhase = null;

    assertThat(gNode).isNotNull();

    while (gNode.getNext() != null) {
      if (gNode.getNext().getName().equals("Verify Service")) {
        verificationPhase = gNode.getNext();
        break;
      }
      gNode = gNode.getNext();
    }
    assertThat(verificationPhase).isNotNull();
    assertThat(verificationPhase.getStatus().equals("SUCCESS")).isTrue();
    assertThat(verificationPhase.getGroup().getElements().size() == 1).isTrue();

    assertThat(verificationPhase.getGroup().getElements().get(0).getStatus().equals("SUCCESS")).isTrue();
    assertThat(verificationPhase.getGroup().getElements().get(0).getName().equals("HTTP")).isTrue();

    Map<String, Object> executionResults =
        (LinkedHashMap) verificationPhase.getGroup().getElements().get(0).getExecutionDetails();
    assertThat(executionResults.containsKey("httpResponseCode")).isTrue();
    assertThat(executionResults.containsKey("httpResponseBody")).isTrue();

    ExecutionDataValue dataValue = (ExecutionDataValue) executionResults.get("httpResponseBody");
    JsonPath jPath = JsonPath.from(dataValue.getValue().toString());

    System.out.println(jPath.get("headers").toString());
    assertThat((Object) jPath.get("headers." + NORMAL_TEXT.toLowerCase())).isNotNull();
    String normalTextVal = jPath.get("headers." + NORMAL_TEXT.toLowerCase()).toString();
    assertThat(normalTextVal.equals("Test")).isTrue();

    assertThat((Object) jPath.get("headers." + ENV_OVERRIDDEN_TEXT.toLowerCase())).isNotNull();
    String envTextValue = jPath.get("headers." + ENV_OVERRIDDEN_TEXT.toLowerCase()).toString();
    assertThat(envTextValue.equals("Test")).isTrue();

    log.info("All validations ended successfully");
  }
}
