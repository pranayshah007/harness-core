/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.workflow;

import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.approval.ApprovalStateParams;
import software.wings.beans.approval.ShellScriptApprovalParams;
import software.wings.persistence.artifact.Artifact;
import software.wings.sm.StateType;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ShellScriptApprovalFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private WorkflowUtils workflowUtils;

  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  private final Randomizer.Seed seed = new Randomizer.Seed(0);

  private OwnerManager.Owners owners;

  Application application;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
  }

  @Test
  @Owner(developers = PRABU, intermittent = true)
  @Category(FunctionalTests.class)
  public void shouldCreateShellScriptApprovalStepAndRun() throws Exception {
    Environment environment = environmentGenerator.ensurePredefined(seed, owners, GENERIC_TEST);
    assertThat(environment).isNotNull();

    ShellScriptApprovalParams shellparams = new ShellScriptApprovalParams();
    shellparams.setRetryInterval(30);
    shellparams.setScriptString("HARNESS_APPROVAL_STATUS=\"APPROVED\"");

    ApprovalStateParams params = new ApprovalStateParams();
    params.setShellScriptApprovalParams(shellparams);

    Workflow workflow = workflowUtils.buildCanaryWorkflowPostDeploymentStep(
        "Approval Workflow " + System.currentTimeMillis(), environment.getUuid(),
        GraphNode.builder()
            .name("SS-Approval-workflow-" + System.currentTimeMillis())
            .type(StateType.APPROVAL.toString())
            .properties(ImmutableMap.<String, Object>builder()
                            .put("approvalStateType", ApprovalStateType.SHELL_SCRIPT)
                            .put("approvalStateParams", params)
                            .build())
            .build());
    // Test  creating a workflow
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getUuid(), environment.getUuid(),
        savedWorkflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution).isNotNull();
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }
}
