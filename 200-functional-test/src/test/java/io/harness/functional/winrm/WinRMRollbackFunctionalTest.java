/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.winrm;

import static io.harness.rule.OwnerRule.PRASHANT;

import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.ff.FeatureFlagService;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.ExecutionArgs;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WinRMRollbackFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;

  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private FeatureFlagService featureFlagService;
  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  private OwnerManager.Owners owners;

  private Service service;
  private InfrastructureDefinition infrastructureDefinition;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.FUNCTIONAL_TEST));
    resetCache(owners.obtainAccount().getUuid());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunWinRmWorkflow() {
    service = serviceGenerator.ensurePredefined(seed, owners, Services.WINDOWS_TEST);
    resetCache(service.getAccountId());
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitions.AWS_WINRM_FUNCTIONAL_TEST);
    Workflow workflow = workflowUtils.createWinRMWorkflow("winrm-rollback-wf", service, infrastructureDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    resetCache(workflow.getAccountId());
    Artifact artifact = getArtifact(service, service.getAppId(), 0);
    WorkflowExecution workflowExecution = executeWorkflow(
        workflow, service, Collections.singletonList(artifact), ImmutableMap.<String, String>builder().build());
    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(FunctionalTests.class)
  @Ignore("Enable once feature flag is enabled")
  public void shouldCreateAndRunAndRollbackWinRmWorkflow() {
    service = serviceGenerator.ensurePredefined(seed, owners, ServiceGenerator.Services.WINDOWS_TEST);
    resetCache(service.getAccountId());
    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitions.AWS_WINRM_FUNCTIONAL_TEST);
    Workflow workflow = workflowUtils.createWinRMWorkflow("phy-winrm-", service, infrastructureDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    Artifact artifact_one = getArtifact(service, service.getAppId(), 0);
    WorkflowExecution firstExecution = executeWorkflow(
        workflow, service, Collections.singletonList(artifact_one), ImmutableMap.<String, String>builder().build());
    workflowUtils.checkForWorkflowSuccess(firstExecution);
    Artifact artifact_two = getArtifact(service, service.getAppId(), 1);
    WorkflowExecution secondExecution = executeWorkflow(
        workflow, service, Collections.singletonList(artifact_two), ImmutableMap.<String, String>builder().build());
    workflowUtils.checkForWorkflowSuccess(secondExecution);
    WorkflowExecution rollbackExecution =
        WorkflowRestUtils.rollbackExecution(bearerToken, secondExecution.getAppId(), secondExecution.getUuid());
    workflowUtils.checkForWorkflowSuccess(rollbackExecution);
  }

  private Artifact getArtifact(Service service, String appId, int idx) {
    return ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), idx);
  }

  private WorkflowExecution executeWorkflow(final Workflow workflow, final Service service,
      final List<Artifact> artifacts, ImmutableMap<String, String> workflowVariables) {
    final String appId = service.getAppId();
    final String envId = workflow.getEnvId();

    resetCache(this.service.getAccountId());
    ExecutionArgs executionArgs = prepareExecutionArgs(workflow, artifacts, workflowVariables);
    return WorkflowRestUtils.startWorkflow(bearerToken, appId, envId, executionArgs);
  }

  private ExecutionArgs prepareExecutionArgs(
      Workflow workflow, List<Artifact> artifacts, ImmutableMap<String, String> workflowFlowVariables) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setArtifacts(artifacts);
    executionArgs.setWorkflowVariables(workflowFlowVariables);
    return executionArgs;
  }
}
