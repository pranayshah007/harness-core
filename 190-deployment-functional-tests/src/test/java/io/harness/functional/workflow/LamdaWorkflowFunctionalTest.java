/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAKHAR;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.DEPLOY_AWS_LAMBDA;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.AWS_LAMBDA_ROLLBACK;
import static software.wings.sm.StateType.AWS_LAMBDA_STATE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.CDFunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.dl.WingsPersistence;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
@Slf4j
public class LamdaWorkflowFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private InfrastructureMappingService infrastructureMappingService;

  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private WingsPersistence wingsPersistence;

  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  private OwnerManager.Owners owners;

  private Service service;
  private Application application;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;
  private Artifact artifact;

  final String WRAP_UP_CONSTANT = "Wrap Up";
  final String APPROVAL_CONSTANT = "Approval";
  final String DEPLOY_SERVICE = "Deploy Service";
  final String AWS_LAMBDA = "AWS Lambda";

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
    environment = environmentGenerator.ensurePredefined(seed, owners, EnvironmentGenerator.Environments.GENERIC_TEST);
    assertThat(environment).isNotNull();
    service = serviceGenerator.ensureAwsLambdaGenericTest(seed, owners, "aws-lambda");
    assertThat(service).isNotNull();
    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AWS_LAMBDA_TEST);
    assertThat(infrastructureDefinition).isNotNull();
    artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, application.getAppId(), service.getArtifactStreamIds().get(0), 0);
    assertThat(artifact).isNotNull();
  }

  @Test
  @Owner(developers = PRAKHAR)
  @Category(CDFunctionalTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldRunAwsLambdaRollbackWorkflow() {
    Workflow workflow = getBasicWorkflowWithApprovalStep("aws-lambda-", service, infrastructureDefinition);
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    resetCache(service.getAccountId());
    assertThat(savedWorkflow).isNotNull();
    // Test running the workflow
    WorkflowExecution workflowExecution = assertExecutionWithStatus(savedWorkflow, ExecutionStatus.FAILED);
    workflowUtils.assertRollbackInWorkflowExecution(workflowExecution);
  }

  private Workflow getBasicWorkflowWithApprovalStep(
      String name, Service service, InfrastructureDefinition infrastructureDefinition) {
    List<String> userGroups = Collections.singletonList("uK63L5CVSAa1-BkC4rXoRg");
    List<PhaseStep> phaseSteps = new ArrayList<>();

    phaseSteps.add(
        aPhaseStep(DEPLOY_AWS_LAMBDA, DEPLOY_SERVICE)
            .addStep(GraphNode.builder().id(generateUuid()).type(AWS_LAMBDA_STATE.name()).name(AWS_LAMBDA).build())
            .build());

    phaseSteps.add(aPhaseStep(WRAP_UP, WRAP_UP_CONSTANT)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(APPROVAL.name())
                                    .name(APPROVAL_CONSTANT)
                                    .properties(ImmutableMap.<String, Object>builder()
                                                    .put("timeoutMillis", 60000)
                                                    .put("approvalStateType", "USER_GROUP")
                                                    .put("userGroups", userGroups)
                                                    .build())
                                    .build())
                       .build());

    WorkflowPhase workflowPhase = aWorkflowPhase()
                                      .name("Phase 1")
                                      .serviceId(service.getUuid())
                                      .deploymentType(DeploymentType.AWS_LAMBDA)
                                      .daemonSet(false)
                                      .infraDefinitionId(infrastructureDefinition.getUuid())
                                      .infraDefinitionName(infrastructureDefinition.getName())
                                      .phaseSteps(phaseSteps)
                                      .build();

    List<PhaseStep> rollbackPhaseStep = new ArrayList<>();
    rollbackPhaseStep.add(aPhaseStep(DEPLOY_AWS_LAMBDA, DEPLOY_SERVICE)
                              .withStatusForRollback(ExecutionStatus.SUCCESS)
                              .withRollback(true)
                              .withPhaseStepNameForRollback(DEPLOY_SERVICE)
                              .addStep(GraphNode.builder()
                                           .id(generateUuid())
                                           .name("Rollback AWS Lambda")
                                           .type(AWS_LAMBDA_ROLLBACK.name())
                                           .rollback(true)
                                           .origin(true)
                                           .properties(ImmutableMap.<String, Object>builder().build())
                                           .build())
                              .build());
    rollbackPhaseStep.add(aPhaseStep(WRAP_UP, WRAP_UP_CONSTANT).withRollback(true).build());

    Map<String, WorkflowPhase> workflowPhaseIdMap = new HashMap<>();
    workflowPhaseIdMap.put(workflowPhase.getUuid(),
        aWorkflowPhase()
            .rollback(true)
            .phaseSteps(rollbackPhaseStep)
            .serviceId(service.getUuid())
            .deploymentType(DeploymentType.AWS_LAMBDA)
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .build());

    Workflow workflow = aWorkflow()
                            .name(name + System.currentTimeMillis())
                            .appId(service.getAppId())
                            .envId(infrastructureDefinition.getEnvId())
                            .infraDefinitionId(infrastructureDefinition.getUuid())
                            .serviceId(service.getUuid())
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                                       .addWorkflowPhase(workflowPhase)
                                                       .withRollbackWorkflowPhaseIdMap(workflowPhaseIdMap)
                                                       .build())
                            .build();
    return workflow;
  }

  private void assertExecution(Workflow savedWorkflow) {
    assertExecutionWithStatus(savedWorkflow, ExecutionStatus.SUCCESS);
  }

  private WorkflowExecution assertExecutionWithStatus(Workflow savedWorkflow, ExecutionStatus executionStatus) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(savedWorkflow.getWorkflowType());
    executionArgs.setArtifacts(Arrays.asList(artifact));
    executionArgs.setOrchestrationId(savedWorkflow.getUuid());
    executionArgs.setExecutionCredential(SSHExecutionCredential.Builder.aSSHExecutionCredential()
                                             .withExecutionType(ExecutionCredential.ExecutionType.SSH)
                                             .build());

    log.info("Invoking workflow execution");
    //    WorkflowExecution workflowExecution = workflowExecutionService.triggerEnvExecution(service.getApplicationId(),
    //        infrastructureMapping.getEnvId(), executionArgs, Trigger.builder().name("adwait").uuid("uuId").build());

    WorkflowExecution workflowExecution =
        WorkflowRestUtils.startWorkflow(bearerToken, application.getUuid(), environment.getUuid(), executionArgs);
    assertThat(workflowExecution).isNotNull();
    log.info("Waiting for execution to finish");

    Awaitility.await()
        .atMost(600, TimeUnit.SECONDS)
        .pollInterval(15, TimeUnit.SECONDS)
        .until(()
                   -> Setup.portal()
                          .auth()
                          .oauth2(bearerToken)
                          .queryParam("appId", application.getUuid())
                          .get("/executions/" + workflowExecution.getUuid())
                          .jsonPath()
                          .<String>getJsonObject("resource.status")
                          .equals(executionStatus.name()));

    WorkflowExecution completedWorkflowExecution =
        workflowExecutionService.getExecutionDetails(application.getUuid(), workflowExecution.getUuid(), true, false);
    log.info("ECs Execution status: " + completedWorkflowExecution.getStatus());
    assertThat(executionStatus == completedWorkflowExecution.getStatus());

    return completedWorkflowExecution;
  }
}
