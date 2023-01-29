/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.functional.vmss;

import static io.harness.azure.model.AzureConstants.BLUE_GREEN;
import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_MAX_INSTANCES;
import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_TIMEOUT_MIN;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.AZURE_VMSS_BASIC_TEST;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.AZURE_VMSS_BLUE_GREEN_TEST;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.BlueGreenOrchestrationWorkflow.BlueGreenOrchestrationWorkflowBuilder.aBlueGreenOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_VMSS_DEPLOY;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_VMSS_SETUP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_VMSS_SWITCH_ROUTES;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.AZURE_VMSS_SWITCH_ROUTES_ROLLBACK;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DEPLOY;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DEPLOY_CONTAINERS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_PREFIX;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SETUP;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.VERIFY_SERVICE;

import static com.google.common.collect.Maps.newHashMap;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.WorkflowType;
import io.harness.category.element.CDFunctionalTests;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.constants.InfraDefinitionGeneratorConstants;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.ArtifactRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GraphNode;
import software.wings.beans.InstanceUnitType;
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

public class AzureVMSSFunctionalTest extends AbstractFunctionalTest {
  public static final String BASIC_DEPLOYMENT_SERVICE_NAME = "Azure_VMSS_Service_Basic";
  public static final String BLUE_GREEN_DEPLOYMENT_SERVICE_NAME = "Azure_VMSS_Service_Blue_Green";
  public static final int CUSTOM_DESIRED_VM_INSTANCES = 1;
  public static final int CUSTOM_MIN_VM_INSTANCES = 1;

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

  @Test
  @Owner(developers = {ANIL, IVAN})
  @Category(CDFunctionalTests.class)
  public void testVMSSBasicWorkflow() {
    Service service = getService(BASIC_DEPLOYMENT_SERVICE_NAME);
    String accountId = service.getAccountId();

    InfrastructureDefinition infrastructureDefinition = getInfrastructureDefinition(AZURE_VMSS_BASIC_TEST);
    resetCache(accountId);

    Workflow workflow = generateBasicWorkflow(service, infrastructureDefinition);
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
  @Owner(developers = {ANIL, IVAN})
  @Category(CDFunctionalTests.class)
  public void testVMSSBlueGreenWorkflow() {
    Service service = getService(BLUE_GREEN_DEPLOYMENT_SERVICE_NAME);
    String accountId = service.getAccountId();

    InfrastructureDefinition infrastructureDefinition = getInfrastructureDefinition(AZURE_VMSS_BLUE_GREEN_TEST);
    resetCache(accountId);

    Workflow workflow = generateBlueGreenWorkflow(service, infrastructureDefinition);
    Artifact artifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, service.getAppId(), service.getArtifactStreamIds().get(0), 0);

    workflow = saveWorkflow(workflow);
    resetCache(accountId);

    WorkflowExecution workflowExecution = runWorkflow(
        bearerToken, application.getUuid(), environment.getUuid(), workflow.getUuid(), ImmutableList.of(artifact));
    getFailedWorkflowExecutionLogs(workflowExecution);
    verifyExecution(workflowExecution);
  }

  public String getWorkflowName(OrchestrationWorkflowType workflowType) {
    return "vmss-" + workflowType.name() + "-" + System.currentTimeMillis();
  }

  private Service getService(String serviceName) {
    Service service = serviceGenerator.ensureAzureVMSSService(seed, owners, serviceName);
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

  public Workflow generateBasicWorkflow(Service service, InfrastructureDefinition infrastructureDefinition) {
    String workflowName = getWorkflowName(OrchestrationWorkflowType.BASIC);

    WorkflowPhase workflowPhase1 = generateBasicWorkflowPhase(service, infrastructureDefinition);
    WorkflowPhase rollbackPhase = generateBasicRollbackPhase(workflowPhase1);

    Map<String, WorkflowPhase> rollbackMap = new HashMap<>();
    rollbackMap.put(workflowPhase1.getUuid(), rollbackPhase);

    return aWorkflow()
        .name(workflowName)
        .appId(service.getAppId())
        .serviceId(service.getUuid())
        .envId(infrastructureDefinition.getEnvId())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                   .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                   .withWorkflowPhases(Collections.singletonList(workflowPhase1))
                                   .withRollbackWorkflowPhaseIdMap(rollbackMap)
                                   .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                   .build())
        .build();
  }

  public WorkflowPhase generateBasicWorkflowPhase(Service service, InfrastructureDefinition infrastructureDefinition) {
    List<PhaseStep> phaseSteps = new ArrayList<>();

    generateBasicVMSSSetupStep(phaseSteps);
    generateAzureVMSSUpgradeStep(phaseSteps);

    addVerifyServiceStep(phaseSteps);
    addWrapUpStep(phaseSteps);

    return aWorkflowPhase()
        .uuid(generateUuid())
        .serviceId(service.getUuid())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .phaseSteps(phaseSteps)
        .build();
  }

  public void generateBasicVMSSSetupStep(List<PhaseStep> phaseSteps) {
    Map<String, Object> setupProperties = new HashMap<>();
    setupProperties.put("minInstances", CUSTOM_MIN_VM_INSTANCES);
    setupProperties.put("maxInstances", DEFAULT_AZURE_VMSS_MAX_INSTANCES);
    setupProperties.put("desiredInstances", CUSTOM_DESIRED_VM_INSTANCES);
    setupProperties.put("autoScalingSteadyStateVMSSTimeout", DEFAULT_AZURE_VMSS_TIMEOUT_MIN);
    setupProperties.put("useCurrentRunningCount", false);
    setupProperties.put("resizeStrategy", ResizeStrategy.RESIZE_NEW_FIRST);
    setupProperties.put(BLUE_GREEN, Boolean.FALSE);

    addSetupStep(phaseSteps, setupProperties);
  }

  public void generateAzureVMSSUpgradeStep(List<PhaseStep> phaseSteps) {
    ImmutableMap<String, Object> deployProperties = ImmutableMap.<String, Object>builder()
                                                        .put("instanceCount", 100)
                                                        .put("instanceUnitType", InstanceUnitType.PERCENTAGE)
                                                        .build();

    addDeployStep(phaseSteps, deployProperties);
  }

  public void addWrapUpStep(List<PhaseStep> phaseSteps) {
    phaseSteps.add(aPhaseStep(WRAP_UP, WRAP_UP.name()).build());
  }

  public void addVerifyServiceStep(List<PhaseStep> phaseSteps) {
    phaseSteps.add(aPhaseStep(PhaseStepType.VERIFY_SERVICE, VERIFY_SERVICE).build());
  }

  private void addSetupStep(List<PhaseStep> phaseSteps, Map<String, Object> properties) {
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_VMSS_SETUP, SETUP)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_VMSS_SETUP.name())
                                    .name(AZURE_VMSS_SETUP)
                                    .properties(properties)
                                    .build())
                       .build());
  }

  private void addDeployStep(List<PhaseStep> phaseSteps, ImmutableMap<String, Object> properties) {
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_VMSS_DEPLOY, DEPLOY)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(StateType.AZURE_VMSS_DEPLOY.name())
                                    .name(AZURE_VMSS_DEPLOY)
                                    .properties(properties)
                                    .build())
                       .build());
  }

  private WorkflowPhase generateBasicRollbackPhase(WorkflowPhase workflowPhase) {
    WorkflowPhaseBuilder rollbackPhaseBuilder = aWorkflowPhase()
                                                    .name(ROLLBACK_PREFIX + workflowPhase.getName())
                                                    .deploymentType(workflowPhase.getDeploymentType())
                                                    .rollback(true)
                                                    .phaseNameForRollback(workflowPhase.getName())
                                                    .serviceId(workflowPhase.getServiceId())
                                                    .computeProviderId(workflowPhase.getComputeProviderId())
                                                    .infraDefinitionId(workflowPhase.getInfraDefinitionId());

    return rollbackPhaseBuilder
        .phaseSteps(asList(aPhaseStep(PhaseStepType.AZURE_VMSS_ROLLBACK, AZURE_VMSS_SWITCH_ROUTES_ROLLBACK)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.AZURE_VMSS_ROLLBACK.name())
                                            .name(AZURE_VMSS_SWITCH_ROUTES_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY)
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

  public Workflow generateBlueGreenWorkflow(Service service, InfrastructureDefinition infrastructureDefinition) {
    String workflowName = getWorkflowName(OrchestrationWorkflowType.BLUE_GREEN);

    WorkflowPhase workflowPhase1 = generateBlueGreenWorkflowPhase(service, infrastructureDefinition);
    WorkflowPhase rollbackPhase = generateBlueGreenRollbackPhase(workflowPhase1);

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

  @NotNull
  public WorkflowPhase generateBlueGreenWorkflowPhase(
      Service service, InfrastructureDefinition infrastructureDefinition) {
    List<PhaseStep> phaseSteps = new ArrayList<>();

    generateBlueGreenAzureVMSSSetupStep(phaseSteps);
    generateAzureVMSSUpgradeStep(phaseSteps);
    generateSwitchRoutesStep(phaseSteps);

    addVerifyServiceStep(phaseSteps);
    addWrapUpStep(phaseSteps);

    return aWorkflowPhase()
        .uuid(generateUuid())
        .serviceId(service.getUuid())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .phaseSteps(phaseSteps)
        .build();
  }

  public void generateBlueGreenAzureVMSSSetupStep(List<PhaseStep> phaseSteps) {
    Map<String, Object> setupProperties = new HashMap<>();
    setupProperties.put("minInstances", CUSTOM_MIN_VM_INSTANCES);
    setupProperties.put("maxInstances", DEFAULT_AZURE_VMSS_MAX_INSTANCES);
    setupProperties.put("desiredInstances", CUSTOM_DESIRED_VM_INSTANCES);
    setupProperties.put("autoScalingSteadyStateVMSSTimeout", DEFAULT_AZURE_VMSS_TIMEOUT_MIN);
    setupProperties.put("useCurrentRunningCount", false);
    setupProperties.put("resizeStrategy", ResizeStrategy.RESIZE_NEW_FIRST);
    setupProperties.put(BLUE_GREEN, Boolean.TRUE);
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetailForBGDeployment =
        AzureLoadBalancerDetailForBGDeployment.builder()
            .loadBalancerName(InfraDefinitionGeneratorConstants.AZURE_VMSS_BLUE_GREEN_BALANCER_NAME)
            .prodBackendPool(InfraDefinitionGeneratorConstants.AZURE_VMSS_BLUE_GREEN_PROD_BP_NAME)
            .stageBackendPool(InfraDefinitionGeneratorConstants.AZURE_VMSS_BLUE_GREEN_STAGE_BP_NAME)
            .build();
    setupProperties.put("azureLoadBalancerDetail", azureLoadBalancerDetailForBGDeployment);

    addSetupStep(phaseSteps, setupProperties);
  }

  public void generateSwitchRoutesStep(List<PhaseStep> phaseSteps) {
    Map<String, Object> switchRoutesProperties = newHashMap();
    switchRoutesProperties.put("downsizeOldVMSS", true);
    switchRoutesProperties.put("autoScalingSteadyStateVMSSTimeout", DEFAULT_AZURE_VMSS_TIMEOUT_MIN);

    addSwitchRoutesStep(phaseSteps, switchRoutesProperties);
  }

  private void addSwitchRoutesStep(List<PhaseStep> phaseSteps, Map<String, Object> defaultDataSwitchRoutes) {
    phaseSteps.add(aPhaseStep(PhaseStepType.AZURE_VMSS_SWITCH_ROUTES, AZURE_VMSS_SWITCH_ROUTES)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(PhaseStepType.AZURE_VMSS_SWITCH_ROUTES.name())
                                    .name(AZURE_VMSS_SWITCH_ROUTES)
                                    .properties(defaultDataSwitchRoutes)
                                    .build())
                       .build());
  }

  private WorkflowPhase generateBlueGreenRollbackPhase(WorkflowPhase workflowPhase) {
    WorkflowPhaseBuilder rollbackPhaseBuilder = aWorkflowPhase()
                                                    .name(ROLLBACK_PREFIX + workflowPhase.getName())
                                                    .deploymentType(workflowPhase.getDeploymentType())
                                                    .rollback(true)
                                                    .phaseNameForRollback(workflowPhase.getName())
                                                    .serviceId(workflowPhase.getServiceId())
                                                    .computeProviderId(workflowPhase.getComputeProviderId())
                                                    .infraDefinitionId(workflowPhase.getInfraDefinitionId());

    return rollbackPhaseBuilder
        .phaseSteps(asList(aPhaseStep(PhaseStepType.AZURE_VMSS_SWITCH_ROLLBACK, AZURE_VMSS_SWITCH_ROUTES_ROLLBACK)
                               .addStep(GraphNode.builder()
                                            .id(generateUuid())
                                            .type(StateType.AZURE_VMSS_SWITCH_ROUTES_ROLLBACK.name())
                                            .name(AZURE_VMSS_SWITCH_ROUTES_ROLLBACK)
                                            .rollback(true)
                                            .build())
                               .withPhaseStepNameForRollback(DEPLOY)
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
    String appId = workflowExecution.getAppId();
    String infraMappingId = workflowExecution.getInfraMappingIds().get(0);
    assertInstanceCount(status, appId, infraMappingId, workflowExecution.getInfraDefinitionIds().get(0));
  }
}
