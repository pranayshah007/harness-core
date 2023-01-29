/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.downloadArtifact;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AADITI;

import static software.wings.api.DeploymentType.WINRM;
import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.SELECT_NODE;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateType.COMMAND;
import static software.wings.sm.StateType.DC_NODE_SELECT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.service.impl.workflow.WorkflowServiceHelper;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WinRmDownloadArtifactTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private WorkflowExecutionServiceImpl workflowExecutionService;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowUtils workflowUtils;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;
  Application application;
  private Service service;
  private InfrastructureDefinition infrastructureDefinition;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application =
        applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.FUNCTIONAL_TEST);
    assertThat(application).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("enable this when we have the infra setup")
  public void shouldDownloadJenkinsArtifact() {
    testDownloadArtifact(ArtifactStreamManager.ArtifactStreams.JENKINS_METADATA_ONLY);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("enable this when we have the infra setup")
  public void shouldDownloadBambooArtifact() {
    testDownloadArtifact(ArtifactStreamManager.ArtifactStreams.BAMBOO_METADATA_ONLY);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("enable this when we have the infra setup")
  public void shouldDownloadNexus2MavenArtifact() {
    testDownloadArtifact(ArtifactStreamManager.ArtifactStreams.NEXUS2_MAVEN_METADATA_ONLY);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("enable this when we have the infra setup")
  public void shouldDownloadNexus3MavenArtifact() {
    testDownloadArtifact(ArtifactStreamManager.ArtifactStreams.NEXUS3_MAVEN_METADATA_ONLY);
  }

  private void testDownloadArtifact(ArtifactStreamManager.ArtifactStreams artifactStreams) {
    service = serviceGenerator.ensurePredefined(
        seed, owners, ServiceGenerator.Services.WINDOWS_TEST_DOWNLOAD, artifactStreams);
    resetCache(service.getAccountId());

    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.AWS_WINRM_DOWNLOAD);

    resetCache(service.getAccountId());
    Workflow workflow = createWinRMWorkflow("winrm-download-artifact", service, infrastructureDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    resetCache(workflow.getAccountId());
    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, service.getAppId(), service.getArtifactStreamIds().get(0), 0);
    WorkflowExecution workflowExecution = executeWorkflow(
        workflow, service, Collections.singletonList(artifact), ImmutableMap.<String, String>builder().build());
    workflowUtils.checkForWorkflowSuccess(workflowExecution);

    // Clean up workflow
    cleanUpWorkflow(application.getUuid(), workflow.getUuid());
  }

  public Workflow createWinRMWorkflow(String name, Service service, InfrastructureDefinition infrastructureDefinition) {
    name = Joiner.on(StringUtils.EMPTY).join(name, System.currentTimeMillis());
    List<PhaseStep> phaseSteps = new ArrayList<>();
    Map<String, Object> selectNodeProperties = new HashMap<>();
    selectNodeProperties.put("specificHosts", false);
    selectNodeProperties.put("instanceCount", 1);
    selectNodeProperties.put("excludeSelectedHostsFromFuturePhases", true);

    phaseSteps.add(aPhaseStep(SELECT_NODE, SELECT_NODE.name())
                       .withPhaseStepType(PhaseStepType.INFRASTRUCTURE_NODE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .name("Select Node")
                                    .type(DC_NODE_SELECT.name())
                                    .properties(selectNodeProperties)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.DISABLE_SERVICE, PhaseStepType.DISABLE_SERVICE.name()).build());
    phaseSteps.add(
        aPhaseStep(PhaseStepType.DEPLOY_SERVICE, PhaseStepType.DEPLOY_SERVICE.name())
            .addStep(GraphNode.builder()
                         .id(generateUuid())
                         .type(COMMAND.name())
                         .name("Install")
                         .properties(ImmutableMap.<String, Object>builder().put("commandName", "Install").build())
                         .rollback(false)
                         .build())
            .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.ENABLE_SERVICE, PhaseStepType.ENABLE_SERVICE.name()).build());
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, PhaseStepType.VERIFY_SERVICE.name()).build());
    phaseSteps.add(aPhaseStep(WRAP_UP, WorkflowServiceHelper.WRAP_UP).build());

    return aWorkflow()
        .name(name)
        .appId(service.getAppId())
        .serviceId(service.getUuid())
        .envId(infrastructureDefinition.getEnvId())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PhaseStepType.PRE_DEPLOYMENT).build())
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .name("Phase1")
                                                         .serviceId(service.getUuid())
                                                         .deploymentType(WINRM)
                                                         .infraDefinitionId(infrastructureDefinition.getUuid())
                                                         .phaseSteps(phaseSteps)
                                                         .build())
                                   .withPostDeploymentSteps(aPhaseStep(PhaseStepType.POST_DEPLOYMENT).build())
                                   .build())
        .build();
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

  private void cleanUpWorkflow(String appId, String workflowId) {
    assertThat(appId).isNotNull();
    assertThat(workflowId).isNotNull();
    // Clean up resources
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("appId", appId)
        .pathParam("workflowId", workflowId)
        .delete("/workflows/{workflowId}")
        .then()
        .statusCode(200);
  }
}
