/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.workflowExecution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.WorkflowGenerator;
import io.harness.persistence.HIterator;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class WorkflowExecutionTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;

  public Workflow buildWorkflow(Seed seed, Owners owners) {
    // Test  creating a workflow
    Workflow workflow = workflowGenerator.ensureWorkflow(seed, owners,
        aWorkflow()
            .name("Workflow - " + generateUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                       .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                       .build())
            .build());

    return workflow;
  }

  @NotNull
  public WorkflowExecution executeWorkflow(Workflow workflow, Application application, Environment environment) {
    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getUuid(), environment.getUuid(),
        workflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution).isNotNull();
    return workflowExecution;
  }

  @Test
  @Owner(developers = GEORGE)
  @Category({FunctionalTests.class})
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void fetchExecutionsInRange() throws Exception {
    final Seed seed = new Seed(0);
    Owners owners = ownerManager.create();
    Workflow workflow = buildWorkflow(seed, owners);

    final Application application = owners.obtainApplication();
    final Environment environment = owners.obtainEnvironment();

    // Test running the workflow

    WorkflowExecution workflowExecution1 = executeWorkflow(workflow, application, environment);
    // Make sure the two workflows have at least one millisecond difference in the creation time.
    sleep(ofMillis(2));
    WorkflowExecution workflowExecution2 = executeWorkflow(workflow, application, environment);

    {
      try (HIterator<WorkflowExecution> executions = workflowExecutionService.executions(
               application.getUuid(), workflowExecution1.getStartTs(), workflowExecution2.getStartTs(), null)) {
        assertThat(executions.hasNext()).isTrue();
        assertThat(executions.next().getUuid()).isEqualTo(workflowExecution1.getUuid());
      }
    }
  }
}
