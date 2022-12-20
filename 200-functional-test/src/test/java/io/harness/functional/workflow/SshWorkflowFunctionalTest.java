/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.workflow;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.FunctionalTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class SshWorkflowFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private WorkflowUtils workflowUtils;

  @Inject private ServiceGenerator serviceGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  private final Randomizer.Seed seed = new Randomizer.Seed(0);

  private Owners owners;
  private Service service;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    service = serviceGenerator.ensureGenericTest(seed, owners, "ssh-test-service");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunSshWorkflowWithOneNoNodePhase() {
    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(accountId);

    InfrastructureDefinition infraDef = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_SSH_TEST);
    Workflow workflow =
        workflowUtils.createMultiPhaseSshWorkflowWithNoNodePhase("ssh-pdc-rolling", service, infraDef, false);
    workflow = saveWorkflow(workflow, appId, accountId);
    resetCache(accountId);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, null, workflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution.getStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunCanarySshWorkflowWithRollback() {
    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(accountId);

    InfrastructureDefinition infraDef = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_SSH_TEST);
    Workflow workflow =
        workflowUtils.createMultiPhaseSshWorkflowWithNoNodePhase("ssh-canary-rollback", service, infraDef, true);

    workflow = saveWorkflow(workflow, appId, accountId);
    resetCache(accountId);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, null, workflow.getUuid(), Collections.<Artifact>emptyList());
    getFailedWorkflowExecutionLogs(workflowExecution);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(workflowExecution.getRollbackDuration()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunRollingSshWorkflowWithRollback() {
    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(accountId);

    InfrastructureDefinition infraDef = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_SSH_TEST);
    Workflow workflow =
        workflowUtils.createRollingSshWorkflowWithNoNodePhase("ssh-rolling-rollback", service, infraDef, true);

    workflow = saveWorkflow(workflow, appId, accountId);
    resetCache(accountId);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, null, workflow.getUuid(), Collections.<Artifact>emptyList());
    getFailedWorkflowExecutionLogs(workflowExecution);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(workflowExecution.getRollbackDuration()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunBasicSshWorkflowWithRollback() {
    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(accountId);

    InfrastructureDefinition infraDef = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_SSH_TEST);
    Workflow workflow =
        workflowUtils.createBasicSshWorkflowWithNoNodePhase("ssh-basic-rollback", service, infraDef, true);

    workflow = saveWorkflow(workflow, appId, accountId);
    resetCache(accountId);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, null, workflow.getUuid(), Collections.<Artifact>emptyList());
    getFailedWorkflowExecutionLogs(workflowExecution);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(workflowExecution.getRollbackDuration()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunCanarySshWorkflowAndPostProdRollback() {
    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(accountId);

    InfrastructureDefinition infraDef = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_SSH_TEST);
    Workflow workflow = workflowUtils.createMultiPhaseSshWorkflowWithNoNodePhase(
        "ssh-canary-postprod-rollback", service, infraDef, false);

    workflow = saveWorkflow(workflow, appId, accountId);
    resetCache(accountId);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, null, workflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution.getStatus()).isEqualTo(SUCCESS);

    WorkflowExecution rollbackExecution =
        WorkflowRestUtils.rollbackExecution(bearerToken, workflowExecution.getAppId(), workflowExecution.getUuid());
    workflowUtils.checkForWorkflowSuccess(rollbackExecution);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunRollingSshWorkflowAndPostProdRollback() {
    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(accountId);

    InfrastructureDefinition infraDef = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_SSH_TEST);
    Workflow workflow = workflowUtils.createRollingSshWorkflowWithNoNodePhase(
        "ssh-rolling-postprod-rollback", service, infraDef, false);

    workflow = saveWorkflow(workflow, appId, accountId);
    resetCache(accountId);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, null, workflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution.getStatus()).isEqualTo(SUCCESS);

    WorkflowExecution rollbackExecution =
        WorkflowRestUtils.rollbackExecution(bearerToken, workflowExecution.getAppId(), workflowExecution.getUuid());
    workflowUtils.checkForWorkflowSuccess(rollbackExecution);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void shouldRunBasicSshWorkflowAndPostProdRollback() {
    final String appId = service.getAppId();
    final String accountId = service.getAccountId();

    resetCache(accountId);

    InfrastructureDefinition infraDef = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.PHYSICAL_SSH_TEST);
    Workflow workflow =
        workflowUtils.createBasicSshWorkflowWithNoNodePhase("ssh-basic-postprod-rollback", service, infraDef, false);

    workflow = saveWorkflow(workflow, appId, accountId);
    resetCache(accountId);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, appId, null, workflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution.getStatus()).isEqualTo(SUCCESS);

    WorkflowExecution rollbackExecution =
        WorkflowRestUtils.rollbackExecution(bearerToken, workflowExecution.getAppId(), workflowExecution.getUuid());
    workflowUtils.checkForWorkflowSuccess(rollbackExecution);
  }

  public Workflow saveWorkflow(Workflow workflow, String appId, String accountId) {
    workflow.setName(workflow.getName() + "-" + UUIDGenerator.generateUuid());
    Workflow savedWorkflow = WorkflowRestUtils.createWorkflow(bearerToken, accountId, appId, workflow);
    assertThat(savedWorkflow).isNotNull();
    return savedWorkflow;
  }
}
