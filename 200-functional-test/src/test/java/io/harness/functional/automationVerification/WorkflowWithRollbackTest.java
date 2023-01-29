/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.automationVerification;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.POOJA;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateType.HTTP;

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
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionCredential.ExecutionType;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.states.HttpState.HttpStateKeys;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class WorkflowWithRollbackTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ArtifactStreamManager artifactStreamManager;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;

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

    environment = environmentGenerator.ensurePredefined(seed, owners, Environments.FUNCTIONAL_TEST);
    assertThat(environment).isNotNull();

    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitions.AWS_SSH_FUNCTIONAL_TEST);
    assertThat(infrastructureDefinition).isNotNull();

    service = serviceGenerator.ensurePredefined(seed, owners, Services.FUNCTIONAL_TEST);
    assertThat(service).isNotNull();

    artifactStream = artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.ARTIFACTORY_ECHO_WAR);
    assertThat(artifactStream).isNotNull();
  }

  @Test
  @Owner(developers = POOJA)
  @Category(FunctionalTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void testWFWithRollback() throws Exception {
    Workflow savedWorkflow = addWorkflow();
    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getUuid()).isNotNull();

    WorkflowPhase updatedPhase2 = addVerificationPhase2(savedWorkflow);
    assertThat(updatedPhase2).isNotNull();

    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, application.getUuid(), artifactStream.getUuid(), 0);

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(savedWorkflow.getWorkflowType());
    executionArgs.setExecutionCredential(
        SSHExecutionCredential.Builder.aSSHExecutionCredential().withExecutionType(ExecutionType.SSH).build());
    executionArgs.setOrchestrationId(savedWorkflow.getUuid());
    executionArgs.setArtifacts(Collections.singletonList(artifact));

    WorkflowExecution workflowExecution =
        WorkflowRestUtils.startWorkflow(bearerToken, application.getUuid(), environment.getUuid(), executionArgs);
    assertThat(workflowExecution).isNotNull();

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
                          .equals(ExecutionStatus.FAILED.name()));

    WorkflowExecution completedWorkflowExecution =
        workflowExecutionService.getExecutionDetails(application.getUuid(), workflowExecution.getUuid(), true, false);
    System.out.println("test");
    assertThat(completedWorkflowExecution.getExecutionNode().getStatus()).isEqualTo("SUCCESS");
    assertThat(completedWorkflowExecution.getExecutionNode().getNext().getStatus()).isEqualTo("SUCCESS");
    assertThat(completedWorkflowExecution.getExecutionNode().getNext().getNext().getStatus()).isEqualTo("FAILED");

    assertThat(WorkflowRestUtils.deleteWorkflow(bearerToken, savedWorkflow.getUuid(), application.getUuid())).isNull();
  }

  private WorkflowPhase addVerificationPhase2(Workflow savedWorkflow) {
    PhaseStep verifyPhaseStep = aPhaseStep(PhaseStepType.VERIFY_SERVICE, "verifyHttp").addStep(getHTTPNode()).build();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) savedWorkflow.getOrchestrationWorkflow();
    for (WorkflowPhase workflowPhase : orchestrationWorkflow.getWorkflowPhases()) {
      if (workflowPhase.getName().equalsIgnoreCase("Phase 2")) {
        for (PhaseStep phaseStep : workflowPhase.getPhaseSteps()) {
          if (phaseStep.getPhaseStepType() == PhaseStepType.VERIFY_SERVICE) {
            phaseStep.setSteps(Collections.singletonList(getHTTPNode()));
            break;
          }
        }
        return WorkflowRestUtils.saveWorkflowPhase(
            bearerToken, application.getUuid(), savedWorkflow.getUuid(), workflowPhase.getUuid(), workflowPhase);
      }
    }
    return null;
  }

  private Workflow addWorkflow() throws Exception {
    WorkflowPhase phase1 =
        aWorkflowPhase().serviceId(service.getUuid()).infraDefinitionId(infrastructureDefinition.getUuid()).build();
    WorkflowPhase phase2 =
        aWorkflowPhase().serviceId(service.getUuid()).infraDefinitionId(infrastructureDefinition.getUuid()).build();

    Workflow variableTestWorkflow =
        aWorkflow()
            .name("Echo deployment with rollback")
            .description("Echo deployment with Rollback")
            .serviceId(service.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .envId(environment.getUuid())
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow().withWorkflowPhases(ImmutableList.of(phase1, phase2)).build())
            .build();

    return WorkflowRestUtils.createWorkflow(
        bearerToken, AccountGenerator.ACCOUNT_ID, application.getUuid(), variableTestWorkflow);
  }

  private GraphNode getHTTPNode() {
    return GraphNode.builder()
        .id(generateUuid())
        .type(HTTP.name())
        .name("HTTP")
        .properties(ImmutableMap.<String, Object>builder()
                        .put(HttpStateKeys.url, "failed")
                        .put(HttpStateKeys.method, "GET")
                        .build())
        .build();
  }
}
