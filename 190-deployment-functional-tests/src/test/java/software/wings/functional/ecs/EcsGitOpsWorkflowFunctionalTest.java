/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.functional.ecs;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.generator.SettingGenerator.Settings.AWS_DEPLOYMENT_FUNCTIONAL_TESTS_CLOUD_PROVIDER;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.RAGHVENDRA;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.CONTAINER_DEPLOY;
import static software.wings.beans.PhaseStepType.CONTAINER_SETUP;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateType.ECS_SERVICE_DEPLOY;
import static software.wings.sm.StateType.ECS_SERVICE_SETUP;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.WorkflowType;
import io.harness.category.element.CDFunctionalTests;
import io.harness.delegate.beans.pcf.ResizeStrategy;
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
import io.harness.testframework.restutils.ServiceVariablesUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.AwsElbConfig;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.infra.InfrastructureDefinition;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class EcsGitOpsWorkflowFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private SettingGenerator settingGenerator;

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
  @Owner(developers = ARVIND)
  @Category(CDFunctionalTests.class)
  public void shouldCreateLocalEcsWorkflow() throws Exception {
    Workflow savedWorkflow = getWorkflow(StoreType.Local, false, false);
    assertExecution(savedWorkflow, application.getUuid(), environment.getUuid());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(CDFunctionalTests.class)
  public void shouldCreateRemoteEcsWorkflow() throws Exception {
    Workflow savedWorkflow = getWorkflow(StoreType.Remote, false, false);

    ServiceVariablesUtils.addOrGetServiceVariable(bearerToken, createServiceVariable("containerPort", "80"));
    ServiceVariablesUtils.addOrGetServiceVariable(bearerToken, createServiceVariable("hostPort", "80"));

    assertExecution(savedWorkflow, application.getUuid(), environment.getUuid());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(CDFunctionalTests.class)
  public void shouldCreateRemoteEcsWorkflowMultiLbDeployment() throws Exception {
    Workflow savedWorkflow = getWorkflow(StoreType.Remote, false, true);

    ServiceVariablesUtils.addOrGetServiceVariable(bearerToken, createServiceVariable("containerPort", "80"));
    ServiceVariablesUtils.addOrGetServiceVariable(bearerToken, createServiceVariable("hostPort", "8080"));

    assertExecution(savedWorkflow, application.getUuid(), environment.getUuid());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(CDFunctionalTests.class)
  public void shouldCreateRemoteAccountConnectorEcsWorkflow() throws Exception {
    Workflow savedWorkflow = getWorkflow(StoreType.Remote, true, false);

    ServiceVariablesUtils.addServiceVariable(bearerToken, createServiceVariable("containerPort", "80"));
    ServiceVariablesUtils.addServiceVariable(bearerToken, createServiceVariable("hostPort", "80"));

    assertExecution(savedWorkflow, application.getUuid(), environment.getUuid());
  }

  @NotNull
  private Workflow getWorkflow(StoreType storeType, boolean accountConnector, boolean isMultiLb) {
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

    Workflow basicEcsEc2TypeWorkflow = getEcsEc2TypeWorkflow(storeType, isMultiLb);
    Workflow savedWorkflow = WorkflowRestUtils.createWorkflow(
        bearerToken, application.getAccountId(), application.getUuid(), basicEcsEc2TypeWorkflow);
    assertThat(savedWorkflow).isNotNull();
    savedWorkflow.setServiceId(service.getUuid());

    return savedWorkflow;
  }

  public ServiceVariable createServiceVariable(String name, String value) {
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

  private PhaseStep getEcsEc2MultiLbTypePhaseStep() {
    return aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER_CONSTANT)
        .addStep(
            GraphNode.builder()
                .id(generateUuid())
                .type(ECS_SERVICE_SETUP.name())
                .name(ECS_SERVICE_SETUP_CONSTANT)
                .properties(
                    ImmutableMap.<String, Object>builder()
                        .put("fixedInstances", "1")
                        .put("ecsServiceName", "${app.name}__${service.name}__BASIC")
                        .put("desiredInstanceCount", "fixedInstances")
                        .put("resizeStrategy", ResizeStrategy.DOWNSIZE_OLD_FIRST)
                        .put("serviceSteadyStateTimeout", 10)
                        .put("useLoadBalancer", true)
                        .put("targetPort", "")
                        .put("targetGroupArn",
                            "arn:aws:elasticloadbalancing:us-east-1:479370281431:targetgroup/a-group/2b773ec2b7f385f0")
                        .put("targetContainerName", "")
                        .put("loadBalancerName", "QA-Verification-LB")
                        .put("awsElbConfigs",
                            asList(
                                AwsElbConfig.builder()
                                    .loadBalancerName("QA-Verification-LB-2")
                                    .targetGroupArn(
                                        "arn:aws:elasticloadbalancing:us-east-1:479370281431:targetgroup/cdp-tg-01/3af160ca962ddd62")
                                    .targetContainerName("")
                                    .targetPort("")
                                    .build()))
                        .build())
                .build())
        .build();
  }

  private PhaseStep getEcsEc2TypePhaseStep() {
    return aPhaseStep(CONTAINER_SETUP, SETUP_CONTAINER_CONSTANT)
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(ECS_SERVICE_SETUP.name())
                     .name(ECS_SERVICE_SETUP_CONSTANT)
                     .properties(ImmutableMap.<String, Object>builder()
                                     .put("fixedInstances", "1")
                                     .put("useLoadBalancer", false)
                                     .put("ecsServiceName", "${app.name}__${service.name}__BASIC")
                                     .put("desiredInstanceCount", "fixedInstances")
                                     .put("resizeStrategy", ResizeStrategy.DOWNSIZE_OLD_FIRST)
                                     .put("serviceSteadyStateTimeout", 10)
                                     .build())
                     .build())
        .build();
  }

  private Workflow getEcsEc2TypeWorkflow(StoreType storeType, boolean isMultiLb) {
    List<PhaseStep> phaseSteps = new ArrayList<>();

    if (isMultiLb) {
      phaseSteps.add(getEcsEc2MultiLbTypePhaseStep());
    } else {
      phaseSteps.add(getEcsEc2TypePhaseStep());
    }

    phaseSteps.add(aPhaseStep(CONTAINER_DEPLOY, DEPLOY_CONTAINERS_CONSTANT)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(ECS_SERVICE_DEPLOY.name())
                                    .name(UPGRADE_CONTAINERS_CONSTANT)
                                    .properties(ImmutableMap.<String, Object>builder()
                                                    .put("instanceUnitType", "PERCENTAGE")
                                                    .put("instanceCount", 100)
                                                    .put("downsizeInstanceUnitType", "PERCENTAGE")
                                                    .put("downsizeInstanceCount", 0)
                                                    .build())
                                    .build())
                       .build());

    phaseSteps.add(aPhaseStep(WRAP_UP, WRAP_UP_CONSTANT).build());

    return aWorkflow()
        .name(storeType + " Basic ECS " + System.currentTimeMillis())
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
}
