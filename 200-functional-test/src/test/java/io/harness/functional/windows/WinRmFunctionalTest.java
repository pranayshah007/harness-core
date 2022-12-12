/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.windows;

import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.api.DeploymentType.WINRM;
import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.DEPLOY_SERVICE;
import static software.wings.beans.PhaseStepType.DISABLE_SERVICE;
import static software.wings.beans.PhaseStepType.ENABLE_SERVICE;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SELECT_NODE_NAME;
import static software.wings.sm.StateType.COMMAND;
import static software.wings.sm.StateType.DC_NODE_SELECT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionCredential.ExecutionType;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.service.impl.workflow.WorkflowServiceHelper;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.GenericType;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WinRmFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private WorkflowExecutionServiceImpl workflowExecutionService;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;

  final Randomizer.Seed seed = new Randomizer.Seed(0);
  OwnerManager.Owners owners;
  Application application;
  Workflow workflow;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(FunctionalTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldDeployIISAppWithPhysicalInfra() throws Exception {
    Service savedService = serviceGenerator.ensurePredefined(seed, owners, Services.WINDOWS_TEST);
    Environment savedEnvironment = environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST);

    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AZURE_WINRM_TEST);

    workflow = saveAndGetWorkflow(application.getUuid(), savedEnvironment.getUuid(), savedService.getUuid(),
        infrastructureDefinition.getUuid(), TestConstants.INSTALL_IIS_APPLICATION, false);

    assertThat(workflow).isNotNull();
    assertThat(workflow.getUuid()).isNotEmpty();
    assertThat(workflow.getWorkflowType()).isEqualTo(ORCHESTRATION);

    Artifact artifact = collectArtifact(application.getUuid(), savedEnvironment.getUuid(), savedService.getUuid());

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    executionArgs.setExecutionCredential(
        SSHExecutionCredential.Builder.aSSHExecutionCredential().withExecutionType(ExecutionType.SSH).build());
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setServiceId(savedService.getUuid());
    executionArgs.setCommandName("START");
    executionArgs.setArtifacts(Collections.singletonList(artifact));

    // Deploy the workflow
    WorkflowExecution workflowExecution =
        WorkflowRestUtils.startWorkflow(bearerToken, application.getUuid(), savedEnvironment.getUuid(), executionArgs);
    assertThat(workflowExecution).isNotNull();

    Awaitility.await()
        .atMost(120, TimeUnit.SECONDS)
        .pollInterval(5, TimeUnit.SECONDS)
        .until(()
                   -> workflowExecutionService.getWorkflowExecution(application.getUuid(), workflowExecution.getUuid())
                          .getStatus()
                == ExecutionStatus.SUCCESS);

    // Clean up workflow
    cleanUpWorkflow(application.getUuid(), workflow.getUuid());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(FunctionalTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldDeployIISAppWithAzureCloudProvider() throws Exception {
    Service savedService = serviceGenerator.ensurePredefined(seed, owners, Services.WINDOWS_TEST);
    Environment savedEnvironment = environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST);
    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.PHYSICAL_WINRM_TEST);

    Workflow savedWorkflow = saveAndGetWorkflow(application.getUuid(), savedEnvironment.getUuid(),
        savedService.getUuid(), infrastructureDefinition.getUuid(), TestConstants.INSTALL_IIS_APPLICATION, false);

    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getUuid()).isNotEmpty();
    assertThat(savedWorkflow.getWorkflowType()).isEqualTo(ORCHESTRATION);

    Artifact artifact = collectArtifact(application.getUuid(), savedEnvironment.getUuid(), savedService.getUuid());

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(savedWorkflow.getWorkflowType());
    executionArgs.setExecutionCredential(
        SSHExecutionCredential.Builder.aSSHExecutionCredential().withExecutionType(ExecutionType.SSH).build());
    executionArgs.setOrchestrationId(savedWorkflow.getUuid());
    executionArgs.setServiceId(savedService.getUuid());
    executionArgs.setCommandName("START");
    executionArgs.setArtifacts(Collections.singletonList(artifact));

    // Deploy the workflow
    WorkflowExecution workflowExecution =
        WorkflowRestUtils.startWorkflow(bearerToken, application.getUuid(), savedEnvironment.getUuid(), executionArgs);
    assertThat(workflowExecution).isNotNull();

    Awaitility.await()
        .atMost(120, TimeUnit.SECONDS)
        .pollInterval(5, TimeUnit.SECONDS)
        .until(()
                   -> workflowExecutionService.getWorkflowExecution(application.getUuid(), workflowExecution.getUuid())
                          .getStatus()
                == ExecutionStatus.SUCCESS);
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

  private Artifact collectArtifact(String appId, String envId, String serviceId) {
    GenericType<RestResponse<PageResponse<Artifact>>> workflowType =
        new GenericType<RestResponse<PageResponse<Artifact>>>() {};
    RestResponse<PageResponse<Artifact>> savedArtifactResponse = Setup.portal()
                                                                     .auth()
                                                                     .oauth2(bearerToken)
                                                                     .queryParam("appId", appId)
                                                                     .queryParam("envId", envId)
                                                                     .queryParam("serviceId", serviceId)
                                                                     .queryParam("search[0][field]", "status")
                                                                     .queryParam("search[0][op]", "IN")
                                                                     .queryParam("search[0][value]", "READY")
                                                                     .queryParam("search[0][value]", "APPROVED")
                                                                     .contentType(ContentType.JSON)
                                                                     .get("/artifacts")
                                                                     .as(workflowType.getType());

    return (savedArtifactResponse != null && savedArtifactResponse.getResource() != null
               && savedArtifactResponse.getResource().getResponse() != null
               && savedArtifactResponse.getResource().getResponse().size() > 0)
        ? savedArtifactResponse.getResource().getResponse().get(0)
        : null;
  }

  private Workflow saveAndGetWorkflow(String appId, String envId, String serviceId, String infraDefinitionId,
      String commandName, boolean specificHosts) throws Exception {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    Map<String, Object> selectNodeProperties = new HashMap<>();
    selectNodeProperties.put("specificHosts", specificHosts);
    selectNodeProperties.put("instanceCount", 1);
    selectNodeProperties.put("excludeSelectedHostsFromFuturePhases", true);
    if (specificHosts) {
      selectNodeProperties.put("hostNames", Lists.newArrayList(TestConstants.WINDOWS_DEPLOY_HOST));
      selectNodeProperties.put("excludeSelectedHostsFromFuturePhases", false);
    }

    phaseSteps.add(aPhaseStep(INFRASTRUCTURE_NODE, WorkflowServiceHelper.INFRASTRUCTURE_NODE_NAME)
                       .withPhaseStepType(PhaseStepType.INFRASTRUCTURE_NODE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .name(SELECT_NODE_NAME)
                                    .type(DC_NODE_SELECT.name())
                                    .properties(selectNodeProperties)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(DISABLE_SERVICE, WorkflowServiceHelper.DISABLE_SERVICE)
                       .withPhaseStepType(PhaseStepType.DISABLE_SERVICE)
                       .build());

    Map<String, Object> installCommandProperties = new HashMap<>();
    installCommandProperties.put("commandName", commandName);
    phaseSteps.add(aPhaseStep(DEPLOY_SERVICE, WorkflowServiceHelper.DEPLOY_SERVICE)
                       .withPhaseStepType(PhaseStepType.DEPLOY_SERVICE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .name(commandName)
                                    .type(COMMAND.name())
                                    .properties(installCommandProperties)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(ENABLE_SERVICE, WorkflowServiceHelper.ENABLE_SERVICE)
                       .withPhaseStepType(PhaseStepType.ENABLE_SERVICE)
                       .build());
    phaseSteps.add(aPhaseStep(VERIFY_SERVICE, WorkflowServiceHelper.VERIFY_SERVICE)
                       .withPhaseStepType(PhaseStepType.VERIFY_SERVICE)
                       .build());
    phaseSteps.add(aPhaseStep(WRAP_UP, WorkflowServiceHelper.WRAP_UP).withPhaseStepType(PhaseStepType.WRAP_UP).build());

    Workflow iisAppWorkflow =
        aWorkflow()
            .name(commandName + "Test Workflow")
            .description("To Test " + commandName)
            .serviceId(serviceId)
            .workflowType(WorkflowType.ORCHESTRATION)
            .infraDefinitionId(infraDefinitionId)
            .envId(envId)
            .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                       .withPreDeploymentSteps(aPhaseStep(PhaseStepType.PRE_DEPLOYMENT).build())
                                       .addWorkflowPhase(aWorkflowPhase()
                                                             .name("Phase1")
                                                             .serviceId(serviceId)
                                                             .deploymentType(WINRM)
                                                             .infraDefinitionId(infraDefinitionId)
                                                             .phaseSteps(phaseSteps)
                                                             .build())
                                       .withPostDeploymentSteps(aPhaseStep(PhaseStepType.POST_DEPLOYMENT).build())
                                       .build())
            .build();

    return WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), appId, iisAppWorkflow);
  }

  @After
  public void tearDown() {
    if (workflow != null && application != null && workflow.getUuid() != null && application != null) {
      cleanUpWorkflow(application.getUuid(), workflow.getUuid());
    }
  }
}
