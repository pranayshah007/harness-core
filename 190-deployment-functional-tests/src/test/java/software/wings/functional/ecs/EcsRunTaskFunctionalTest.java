/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.functional.ecs;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.generator.SettingGenerator.Settings.AWS_DEPLOYMENT_FUNCTIONAL_TESTS_CLOUD_PROVIDER;
import static io.harness.generator.SettingGenerator.Settings.ECS_FUNCTIONAL_TEST_GIT_REPO;
import static io.harness.rule.OwnerRule.RAGHVENDRA;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.CONTAINER_SETUP;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateType.ECS_RUN_TASK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.CDFunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.SettingGenerator;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.testframework.restutils.ArtifactStreamRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.infra.InfrastructureDefinition;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ecs.AmazonECSAsync;
import com.amazonaws.services.ecs.AmazonECSAsyncClientBuilder;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class EcsRunTaskFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private SettingGenerator settingGenerator;
  @Inject ScmSecret scmSecret;

  final Seed seed = new Seed(0);
  Owners owners;

  // @TODO please remove this and use actual constants when constants class is refactored
  final String SETUP_CONTAINER_CONSTANT = "Setup Container";
  final String PRE_DEPLOYMENT_CONSTANT = "Pre-Deployment";
  final String ECS_DAEMON_SERVICE_SETUP_NAME = "ECS Daemon Service Setup";
  final String POST_DEPLOYMENT_CONSTANT = "Post-Deployment";
  final String WRAP_UP_CONSTANT = "Wrap Up";
  final String ECS_SERVICE_SETUP_CONSTANT = "ECS Service Setup";
  final String UPGRADE_CONTAINERS_CONSTANT = "Upgrade Containers";
  final String DEPLOY_CONTAINERS_CONSTANT = "Deploy Containers";
  final String serviceName = "Func_Test_Ecs_Git_Service";
  final String runTaskFamilyName = "RUN_TASK_FAMILY";
  final String addInlineTaskDefinition = "Inline";
  final String addRemoteTaskDefinition = "Remote";
  final String taskDefinitionJson = "{\n"
      + "  \"family\": \"RUN_TASK_FAMILY\",\n"
      + "  \"containerDefinitions\": [\n"
      + "    {\n"
      + "      \"name\": \"web\",\n"
      + "      \"image\": \"tongueroo/sinatra:latest\",\n"
      + "      \"cpu\": 128,\n"
      + "      \"memoryReservation\": 128,\n"
      + "      \"portMappings\": [\n"
      + "        {\n"
      + "          \"containerPort\": 4567,\n"
      + "          \"protocol\": \"tcp\"\n"
      + "        }\n"
      + "      ],\n"
      + "      \"command\": [\n"
      + "        \"ruby\", \"hi.rb\"\n"
      + "      ],\n"
      + "      \"essential\": true\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  private Application application;
  private Service service;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;
  private SettingAttribute awsSettingAttribute;
  private Artifact artifact;
  private ArtifactStream artifactStream;

  @Before
  public void setUp() {
    owners = ownerManager.create();

    application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(CDFunctionalTests.class)
  public void shouldCreateLocalEcsWorkflow() throws Exception {
    Workflow savedWorkflow = getWorkflow(StoreType.Local, false);
    assertExecutionEcsRunTask(savedWorkflow, application.getUuid(), environment.getUuid());
    cleanupRunTask();
  }

  @NotNull
  private Workflow getWorkflow(StoreType storeType, boolean accountConnector) {
    service = serviceGenerator.ensureEcsRemoteTest(seed, owners, serviceName, storeType, accountConnector,
        "ecsgitops/containerspec_templatized.json", "ecsgitops/servicespec.json");
    assertThat(service).isNotNull();

    environment = environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST);
    assertThat(environment).isNotNull();

    infrastructureDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitions.ECS_DEPLOYMENT_FUNCTIONAL_TEST);
    assertThat(infrastructureDefinition).isNotNull();

    awsSettingAttribute =
        settingGenerator.ensurePredefined(seed, owners, AWS_DEPLOYMENT_FUNCTIONAL_TESTS_CLOUD_PROVIDER);

    Workflow basicEcsRunTypeWorkflow = getEcsRunTaskWorkflow(storeType);
    Workflow savedWorkflow = WorkflowRestUtils.createWorkflow(
        bearerToken, application.getAccountId(), application.getUuid(), basicEcsRunTypeWorkflow);
    assertThat(savedWorkflow).isNotNull();
    savedWorkflow.setServiceId(service.getUuid());

    return savedWorkflow;
  }

  protected void assertExecutionEcsRunTask(Workflow savedWorkflow, String appId, String envId) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(savedWorkflow.getWorkflowType());

    String artifactId = ArtifactStreamRestUtils.getArtifactStreamId(
        bearerToken, appId, savedWorkflow.getEnvId(), savedWorkflow.getServiceId());
    Artifact artifact = new Artifact();
    artifact.setUuid(artifactId);

    executionArgs.setArtifacts(Arrays.asList(artifact));
    executionArgs.setOrchestrationId(savedWorkflow.getUuid());
    executionArgs.setExecutionCredential(SSHExecutionCredential.Builder.aSSHExecutionCredential()
                                             .withExecutionType(ExecutionCredential.ExecutionType.SSH)
                                             .build());

    log.info("Invoking workflow execution");

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, appId, envId, executionArgs);
    logStateExecutionInstanceErrors(workflowExecution);
    assertThat(workflowExecution).isNotNull();

    if (workflowExecution.getStatus() != ExecutionStatus.SUCCESS) {
      return;
    }

    log.info("ECs Execution status: " + workflowExecution.getStatus());
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private ServiceVariable createServiceVariable(String name, String value) {
    ServiceVariable variable = ServiceVariable.builder().build();
    variable.setAccountId(getAccount().getUuid());
    variable.setAppId(service.getAppId());
    variable.setValue(value.toCharArray());
    variable.setName(name);
    variable.setEntityType(EntityType.SERVICE);
    variable.setType(ServiceVariableType.TEXT);
    variable.setEntityId(service.getUuid());
    return variable;
  }

  private Workflow getEcsRunTaskWorkflow(StoreType storeType) {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    GraphNode graphNode = null;
    GraphNode localGraphNode = GraphNode.builder()
                                   .id(generateUuid())
                                   .type(ECS_RUN_TASK.name())
                                   .name(ECS_SERVICE_SETUP_CONSTANT)
                                   .properties(ImmutableMap.<String, Object>builder()
                                                   .put("runTaskFamilyName", runTaskFamilyName)
                                                   .put("addTaskDefinition", addInlineTaskDefinition)
                                                   .put("taskDefinitionJson", taskDefinitionJson)
                                                   .put("skipSteadyStateCheck", true)
                                                   .put("serviceSteadyStateTimeout", 1)
                                                   .build())
                                   .build();

    final SettingAttribute gitConnectorSetting =
        settingGenerator.ensurePredefined(seed, owners, ECS_FUNCTIONAL_TEST_GIT_REPO);

    GitFileConfig gitFileConfig =
        GitFileConfig.builder()
            .connectorId(gitConnectorSetting.getUuid())
            .branch("ecs-gitops-test")
            .useBranch(true)
            .filePathList(Collections.singletonList("ecsgitops/containerspec_templatized.json"))
            .build();

    GraphNode remoteGraphNode = GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ECS_RUN_TASK.name())
                                    .name(ECS_SERVICE_SETUP_CONSTANT)
                                    .properties(ImmutableMap.<String, Object>builder()
                                                    .put("runTaskFamilyName", runTaskFamilyName)
                                                    .put("addTaskDefinition", addRemoteTaskDefinition)
                                                    .put("gitFileConfig", gitFileConfig)
                                                    .put("skipSteadyStateCheck", true)
                                                    .put("serviceSteadyStateTimeout", 1)
                                                    .build())
                                    .build();

    if (storeType.equals(StoreType.Remote)) {
      graphNode = remoteGraphNode;
    } else {
      graphNode = localGraphNode;
    }

    phaseSteps.add(aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER_CONSTANT).addStep(graphNode).build());

    phaseSteps.add(aPhaseStep(WRAP_UP, WRAP_UP_CONSTANT).build());

    return aWorkflow()
        .name(storeType + " Basic ECS Run Task " + System.currentTimeMillis())
        .workflowType(WorkflowType.ORCHESTRATION)
        .appId(service.getAppId())
        .envId(infrastructureDefinition.getEnvId())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .serviceId(service.getUuid())
        .orchestrationWorkflow(
            aBasicOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT, PRE_DEPLOYMENT_CONSTANT).build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT, POST_DEPLOYMENT_CONSTANT).build())
                .addWorkflowPhase(aWorkflowPhase()
                                      .serviceId(service.getUuid())
                                      .deploymentType(DeploymentType.ECS)
                                      .daemonSet(false)
                                      .infraDefinitionId(infrastructureDefinition.getUuid())
                                      .infraDefinitionName(infrastructureDefinition.getName())
                                      .computeProviderId(awsSettingAttribute.getUuid())
                                      .phaseSteps(phaseSteps)
                                      .build())
                .build())
        .build();
  }

  private void cleanupRunTask() {
    AmazonECSAsync client =
        AmazonECSAsyncClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                new String(scmSecret.decryptToCharArray(new SecretName("aws_qa_setup_access_key"))),
                new String(scmSecret.decryptToCharArray(new SecretName("aws_qa_setup_secret_key"))))))
            .withRegion("us-east-1")
            .build();
    ListTasksRequest listTasksRequest = new ListTasksRequest();
    listTasksRequest.setCluster("qa-test-cluster");
    listTasksRequest.setFamily(runTaskFamilyName);
    ListTasksResult listTasksResult = client.listTasks(listTasksRequest);

    listTasksResult.getTaskArns().forEach(taskArn -> {
      StopTaskRequest stopTaskRequest = new StopTaskRequest();
      stopTaskRequest.setCluster("qa-test-cluster");
      stopTaskRequest.setTask(taskArn);
      client.stopTask(stopTaskRequest);
    });
  }
}
