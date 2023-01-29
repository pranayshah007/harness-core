/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.functional.appService;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.AZURE_WEB_APP_BLUE_GREEN_ROLLBACK_TEST;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.AZURE_WEB_APP_BLUE_GREEN_TEST;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.AZURE_WEB_APP_CANARY_TEST;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.BlueGreenOrchestrationWorkflow.BlueGreenOrchestrationWorkflowBuilder.aBlueGreenOrchestrationWorkflow;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_WEBAPP_SLOT_DEPLOYMENT;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_WEBAPP_SLOT_ROLLBACK;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_WEBAPP_SLOT_SETUP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_WEBAPP_SLOT_SWAP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_WEBAPP_SLOT_TRAFFIC;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DEPLOY_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_PREFIX;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.VERIFY_SERVICE;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.WorkflowType;
import io.harness.category.element.CDFunctionalTests;
import io.harness.category.speed.SlowTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.sm.StateType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureAppServiceFunctionalTest extends AbstractFunctionalTest {
  public static final String BLUE_GREEN_DEPLOYMENT_SERVICE_NAME = "Azure_WebApp_Service_Blue_Green";
  public static final String ROLLBACK_BLUE_GREEN_DEPLOYMENT_SERVICE_NAME = "Azure_WebApp_Service_Rollback_Blue_Green";
  public static final String CANARY_DEPLOYMENT_SERVICE_NAME = "Azure_WebApp_Service_Canary";

  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;

  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  private Owners owners;
  private Application application;
  private Environment environment;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = getApplication();
    environment = getEnvironment();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category({CDFunctionalTests.class, SlowTests.class})
  public void testWebAppBlueGreenWorkflow() {
    Service service = getService(BLUE_GREEN_DEPLOYMENT_SERVICE_NAME);
    String accountId = service.getAccountId();

    InfrastructureDefinition infrastructureDefinition = getInfrastructureDefinition(AZURE_WEB_APP_BLUE_GREEN_TEST);
    resetCache(accountId);

    Workflow workflow = generateBlueGreenWorkflow(service, infrastructureDefinition, false);
    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, service.getAppId(), service.getArtifactStreamIds().get(0), 0);

    workflow = saveWorkflow(workflow);
    resetCache(accountId);

    WorkflowExecution workflowExecution = runWorkflow(
        bearerToken, application.getUuid(), environment.getUuid(), workflow.getUuid(), ImmutableList.of(artifact));
    getFailedWorkflowExecutionLogs(workflowExecution);
    verifyExecution(workflowExecution);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category({CDFunctionalTests.class, SlowTests.class})
  public void testWebAppBlueGreenWorkflowRollback() {
    Service service = getService(ROLLBACK_BLUE_GREEN_DEPLOYMENT_SERVICE_NAME);
    String accountId = service.getAccountId();

    InfrastructureDefinition infrastructureDefinition =
        getInfrastructureDefinition(AZURE_WEB_APP_BLUE_GREEN_ROLLBACK_TEST);
    resetCache(accountId);

    Workflow workflow = generateBlueGreenWorkflow(service, infrastructureDefinition, true);
    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, service.getAppId(), service.getArtifactStreamIds().get(0), 0);

    workflow = saveWorkflow(workflow);
    resetCache(accountId);

    WorkflowExecution workflowExecution = runWorkflow(
        bearerToken, application.getUuid(), environment.getUuid(), workflow.getUuid(), ImmutableList.of(artifact));
    getFailedWorkflowExecutionLogs(workflowExecution);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(workflowExecution.getRollbackDuration()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category({CDFunctionalTests.class, SlowTests.class})
  public void testWebAppCanaryWorkflow() {
    Service service = getService(CANARY_DEPLOYMENT_SERVICE_NAME);
    String accountId = service.getAccountId();

    InfrastructureDefinition infrastructureDefinition = getInfrastructureDefinition(AZURE_WEB_APP_CANARY_TEST);
    resetCache(accountId);

    Workflow workflow = generateCanaryWorkflow(service, infrastructureDefinition);
    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, service.getAppId(), service.getArtifactStreamIds().get(0), 0);

    workflow = saveWorkflow(workflow);
    resetCache(accountId);

    WorkflowExecution workflowExecution = runWorkflow(
        bearerToken, application.getUuid(), environment.getUuid(), workflow.getUuid(), ImmutableList.of(artifact));
    getFailedWorkflowExecutionLogs(workflowExecution);
    verifyExecution(workflowExecution);
  }

  private Application getApplication() {
    Application application =
        applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
    return application;
  }

  private Environment getEnvironment() {
    environment = environmentGenerator.ensurePredefined(seed, owners, GENERIC_TEST);
    assertThat(environment).isNotNull();
    return environment;
  }

  public String getWorkflowName(OrchestrationWorkflowType workflowType) {
    return "web-app-" + workflowType.name() + "-" + System.currentTimeMillis();
  }

  private Service getService(String serviceName) {
    Service service = serviceGenerator.ensureAzureWebAppService(seed, owners, serviceName);
    assertThat(service).isNotNull();
    resetCache(service.getAccountId());
    return service;
  }

  @NotNull
  private InfrastructureDefinition getInfrastructureDefinition(InfrastructureDefinitions infraType) {
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, infraType);
    assertThat(infrastructureDefinition).isNotNull();
    return infrastructureDefinition;
  }

  public Workflow saveWorkflow(Workflow workflow) {
    Workflow savedWorkflow =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(), workflow);
    assertThat(savedWorkflow).isNotNull();
    return savedWorkflow;
  }

  public void generateAzureWebAppSwapSlotStep(List<PhaseStep> phaseSteps) {
    ImmutableMap<String, Object> deployProperties = ImmutableMap.<String, Object>builder().build();

    addSwapSlotStep(phaseSteps, deployProperties);
  }

  public void addWrapUpStep(List<PhaseStep> phaseSteps) {
    phaseSteps.add(aPhaseStep(WRAP_UP, WRAP_UP.name()).build());
  }

  public void addVerifyServiceStep(List<PhaseStep> phaseSteps) {
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE).build());
  }

  private void addSetupStep(List<PhaseStep> phaseSteps, Map<String, Object> properties) {
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_SETUP, AZURE_WEBAPP_SLOT_SETUP)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_WEBAPP_SLOT_SETUP.name())
                                    .name(AZURE_WEBAPP_SLOT_DEPLOYMENT)
                                    .properties(properties)
                                    .build())
                       .build());
  }

  private void addTrafficShiftStep(List<PhaseStep> phaseSteps, Map<String, Object> properties) {
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT, AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_WEBAPP_SLOT_SHIFT_TRAFFIC.name())
                                    .name(AZURE_WEBAPP_SLOT_TRAFFIC)
                                    .properties(properties)
                                    .build())
                       .build());
  }

  private void addSwapSlotStep(List<PhaseStep> phaseSteps, ImmutableMap<String, Object> properties) {
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_SWAP, AZURE_WEBAPP_SLOT_SWAP)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_WEBAPP_SLOT_SWAP.name())
                                    .name(AZURE_WEBAPP_SLOT_SWAP)
                                    .properties(properties)
                                    .build())
                       .build());
  }

  public Workflow generateBlueGreenWorkflow(
      Service service, InfrastructureDefinition infrastructureDefinition, boolean withShellScript) {
    String workflowName = getWorkflowName(OrchestrationWorkflowType.BLUE_GREEN);

    WorkflowPhase workflowPhase1 = generateBlueGreenWorkflowPhase(service, infrastructureDefinition);
    WorkflowPhase rollbackPhase = generateRollbackPhase(workflowPhase1);

    if (withShellScript) {
      workflowPhase1.getPhaseSteps().get(0).getSteps().add(WorkflowUtils.getShellScriptStep("exit 1"));
    }

    Map<String, WorkflowPhase> rollbackMap = new HashMap<>();
    rollbackMap.put(workflowPhase1.getUuid(), rollbackPhase);

    return aWorkflow()
        .name(workflowName)
        .appId(service.getAppId())
        .serviceId(service.getUuid())
        .envId(infrastructureDefinition.getEnvId())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aBlueGreenOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withWorkflowPhases(Collections.singletonList(workflowPhase1))
                                   .withRollbackWorkflowPhaseIdMap(rollbackMap)
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public Workflow generateCanaryWorkflow(Service service, InfrastructureDefinition infrastructureDefinition) {
    String workflowName = getWorkflowName(OrchestrationWorkflowType.CANARY);

    WorkflowPhase workflowPhase1 = generateCanaryWorkflowPhase(service, infrastructureDefinition);
    WorkflowPhase rollbackPhase = generateRollbackPhase(workflowPhase1);

    Map<String, WorkflowPhase> rollbackMap = new HashMap<>();
    rollbackMap.put(workflowPhase1.getUuid(), rollbackPhase);

    return aWorkflow()
        .name(workflowName)
        .appId(service.getAppId())
        .serviceId(service.getUuid())
        .envId(infrastructureDefinition.getEnvId())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withWorkflowPhases(Collections.singletonList(workflowPhase1))
                                   .withRollbackWorkflowPhaseIdMap(rollbackMap)
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  @NotNull
  public WorkflowPhase generateBlueGreenWorkflowPhase(
      Service service, InfrastructureDefinition infrastructureDefinition) {
    List<PhaseStep> phaseSteps = new ArrayList<>();

    generateAzureSlotSetupStep(phaseSteps);
    generateAzureWebAppSwapSlotStep(phaseSteps);

    addVerifyServiceStep(phaseSteps);
    addWrapUpStep(phaseSteps);

    return aWorkflowPhase()
        .uuid(generateUuid())
        .serviceId(service.getUuid())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .phaseSteps(phaseSteps)
        .build();
  }

  @NotNull
  public WorkflowPhase generateCanaryWorkflowPhase(Service service, InfrastructureDefinition infrastructureDefinition) {
    List<PhaseStep> phaseSteps = new ArrayList<>();

    generateAzureSlotSetupStep(phaseSteps);
    generateShiftTrafficStep(phaseSteps);
    generateAzureWebAppSwapSlotStep(phaseSteps);

    addVerifyServiceStep(phaseSteps);
    addWrapUpStep(phaseSteps);

    return aWorkflowPhase()
        .uuid(generateUuid())
        .serviceId(service.getUuid())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .phaseSteps(phaseSteps)
        .build();
  }

  public void generateAzureSlotSetupStep(List<PhaseStep> phaseSteps) {
    Map<String, Object> setupProperties = new HashMap<>();
    setupProperties.put("appService", "functiona-test");
    setupProperties.put("deploymentSlot", "functiona-test-stage");
    setupProperties.put("targetSlot", "functiona-test");
    setupProperties.put("slotSteadyStateTimeout", "20");

    addSetupStep(phaseSteps, setupProperties);
  }

  public void generateShiftTrafficStep(List<PhaseStep> phaseSteps) {
    Map<String, Object> setupProperties = new HashMap<>();
    setupProperties.put("title", AZURE_WEBAPP_SLOT_TRAFFIC);
    setupProperties.put("trafficWeightExpr", "100");
    addTrafficShiftStep(phaseSteps, setupProperties);
  }

  private WorkflowPhase generateRollbackPhase(WorkflowPhase workflowPhase) {
    WorkflowPhaseBuilder rollbackPhaseBuilder = aWorkflowPhase()
                                                    .name(ROLLBACK_PREFIX + workflowPhase.getName())
                                                    .deploymentType(workflowPhase.getDeploymentType())
                                                    .rollback(true)
                                                    .phaseNameForRollback(workflowPhase.getName())
                                                    .serviceId(workflowPhase.getServiceId())
                                                    .computeProviderId(workflowPhase.getComputeProviderId())
                                                    .infraDefinitionId(workflowPhase.getInfraDefinitionId());

    return rollbackPhaseBuilder
        .phaseSteps(asList(aPhaseStep(PhaseStepType.AZURE_WEBAPP_SLOT_ROLLBACK, AZURE_WEBAPP_SLOT_ROLLBACK)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.AZURE_WEBAPP_SLOT_ROLLBACK.name())
                                            .name(AZURE_WEBAPP_SLOT_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(AZURE_WEBAPP_SLOT_ROLLBACK)
                               .withStatusForRollback(SUCCESS)
                               .withRollback(true)
                               .build(),
            aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE)
                .withPhaseStepNameForRollback(DEPLOY_CONTAINERS)
                .withStatusForRollback(SUCCESS)
                .withRollback(true)
                .build(),
            aPhaseStep(WRAP_UP, WRAP_UP.name()).withRollback(true).build()))
        .build();
  }

  private void verifyExecution(WorkflowExecution workflowExecution) {
    ExecutionStatus status = workflowExecution.getStatus();
    assertThat(status).isEqualTo(SUCCESS);
    String appId = workflowExecution.getAppId();
    String infraMappingId = workflowExecution.getInfraMappingIds().get(0);
    assertInstanceCount(status, appId, infraMappingId, workflowExecution.getInfraDefinitionIds().get(0));
  }
}
