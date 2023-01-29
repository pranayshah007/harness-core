/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.governance;

import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.sm.StateType.HTTP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.GovernanceUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.states.HttpState.HttpStateKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author rktummala on 02/18/19
 */
public class GovernanceFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;

  Application application;
  final Seed seed = new Seed(0);
  Owners owners;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(FunctionalTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldBlockDeploymentsWhenFreezeIsEnabled() throws Exception {
    Environment environment = environmentGenerator.ensurePredefined(seed, owners, GENERIC_TEST);
    assertThat(environment).isNotNull();
    Workflow workflow = aWorkflow()
                            .name("HTTP Workflow")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .envId(environment.getUuid())
                            .orchestrationWorkflow(
                                aCanaryOrchestrationWorkflow()
                                    .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                    .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).addStep(getHTTPNode()).build())
                                    .build())
                            .build();

    // Test creating a workflow
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getUuid()).isNotEmpty();
    assertThat(savedWorkflow.getWorkflowType()).isEqualTo(ORCHESTRATION);

    // Test running the workflow

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getUuid(), environment.getUuid(),
        savedWorkflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution).isNotNull();
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    String executionUuid = workflowExecution.getUuid();

    GovernanceUtils.setDeploymentFreeze(application.getAccountId(), bearerToken, true);

    workflowExecution = runWorkflow(bearerToken, application.getUuid(), environment.getUuid(), savedWorkflow.getUuid(),
        Collections.<Artifact>emptyList());
    assertThat(workflowExecution).isNull();

    GovernanceUtils.setDeploymentFreeze(application.getAccountId(), bearerToken, false);

    workflowExecution = runWorkflow(bearerToken, application.getUuid(), environment.getUuid(), savedWorkflow.getUuid(),
        Collections.<Artifact>emptyList());
    assertThat(workflowExecution).isNotNull();
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private GraphNode getHTTPNode() {
    return GraphNode.builder()
        .id(generateUuid())
        .type(HTTP.name())
        .name("HTTP")
        .properties(ImmutableMap.<String, Object>builder()
                        .put(HttpStateKeys.url, "http://www.google.com")
                        .put(HttpStateKeys.method, "GET")
                        .build())
        .build();
  }
}
