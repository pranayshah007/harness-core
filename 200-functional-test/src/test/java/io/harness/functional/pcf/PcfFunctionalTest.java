/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.pcf;

import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.WorkflowGenerator;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.ExecutionArgs;
import software.wings.beans.InfrastructureType;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class PcfFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private ApplicationGenerator applicationGenerator;

  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowUtils workflowUtils;

  private final Seed seed = new Seed(0);
  private Owners owners;

  private Service service;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    resetCache(owners.obtainAccount().getUuid());
  }

  @Test
  @Owner(developers = OwnerRule.PRASHANT)
  @Category(FunctionalTests.class)
  @Ignore("Need to figure out the time outs")
  public void shouldCreateAndRunPcfBasicWorkflow() {
    WorkflowExecution workflowExecution = createAndExecuteWorkflow();
    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  @Test
  @Owner(developers = OwnerRule.PRASHANT)
  @Category(FunctionalTests.class)
  @Ignore("Need to figure out the time outs")
  public void shouldCreateAndRunPcfBasicWorkflowAndRollback() {
    WorkflowExecution firstExecution = createAndExecuteWorkflow();
    workflowUtils.checkForWorkflowSuccess(firstExecution);
    WorkflowExecution secondExecution = createAndExecuteWorkflow();
    workflowUtils.checkForWorkflowSuccess(secondExecution);
    resetCache(this.service.getAccountId());
    WorkflowExecution rollbackExecution =
        WorkflowRestUtils.rollbackExecution(bearerToken, secondExecution.getAppId(), secondExecution.getUuid());
    workflowUtils.checkForWorkflowSuccess(rollbackExecution);
  }

  // todo @rk : enable it after jenkins image has cf cli installed
  @Test
  @Owner(developers = OwnerRule.ROHIT_KUMAR)
  @Category(FunctionalTests.class)
  @Ignore("enable it after the jenkins image with CF cli has been released")
  public void testPCFCommandRemoteManifest() {
    WorkflowExecution workflowExecution = createAndExecuteWorkflowPCFCommand();
    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  // todo @rk : enable it after jenkins image has cf cli installed
  @Test
  @Owner(developers = OwnerRule.AADITI)
  @Category(FunctionalTests.class)
  @Ignore("enable it after the jenkins image with CF cli has been released")
  public void shouldCreateAndRunPcfBasicWorkflowWithLinkedPcfCommand() {
    WorkflowExecution workflowExecution = createAndExecuteWorkflowWithLinkedPcfCommand();
    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  private WorkflowExecution createAndExecuteWorkflowPCFCommand() {
    Service commandService = serviceGenerator.ensurePredefined(seed, owners, Services.PCF_V2_REMOTE_TEST);
    Artifact artifact = getArtifact(commandService, commandService.getAppId());
    resetCache(commandService.getAccountId());
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureType.PCF_INFRASTRUCTURE, bearerToken);
    resetCache(commandService.getAccountId());
    Workflow workflow =
        workflowUtils.createPcfCommandWorkflow("pcf-command-wf", commandService, infrastructureDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    return executeWorkflow(
        workflow, commandService, Arrays.asList(artifact), ImmutableMap.<String, String>builder().build());
  }

  private WorkflowExecution createAndExecuteWorkflow() {
    service = serviceGenerator.ensurePredefined(seed, owners, Services.PCF_V2_TEST);
    resetCache(service.getAccountId());
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureType.PCF_INFRASTRUCTURE, bearerToken);
    resetCache(service.getAccountId());
    Workflow workflow = workflowUtils.createPcfWorkflow("pcf-wf", service, infrastructureDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    Artifact artifact = getArtifact(service, service.getAppId());
    return executeWorkflow(workflow, service, Arrays.asList(artifact), ImmutableMap.<String, String>builder().build());
  }

  private WorkflowExecution createAndExecuteWorkflowWithLinkedPcfCommand() {
    service = serviceGenerator.ensurePredefined(seed, owners, Services.PCF_V2_REMOTE_TEST);
    resetCache(service.getAccountId());
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureType.PCF_INFRASTRUCTURE, bearerToken);
    resetCache(service.getAccountId());
    Workflow workflow = workflowUtils.createLinkedPcfCommandWorkflow(
        seed, owners, "pcf-wf-with-linked-command-" + System.currentTimeMillis(), service, infrastructureDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    Artifact artifact = getArtifact(service, service.getAppId());
    return executeWorkflow(workflow, service, Arrays.asList(artifact), ImmutableMap.<String, String>builder().build());
  }

  private WorkflowExecution executeWorkflow(final Workflow workflow, final Service service,
      final List<Artifact> artifacts, ImmutableMap<String, String> workflowVariables) {
    final String appId = service.getAppId();
    final String envId = workflow.getEnvId();

    resetCache(service.getAccountId());
    ExecutionArgs executionArgs = prepareExecutionArgs(workflow, artifacts, workflowVariables);
    return WorkflowRestUtils.startWorkflow(bearerToken, appId, envId, executionArgs);
  }

  private Artifact getArtifact(Service service, String appId) {
    return ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, appId, service.getArtifactStreamIds().get(0), 0);
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

  @After
  public void cleanUp() {}
}
