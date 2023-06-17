/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.OrchestrationWorkflowType.BASIC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VIKAS_S;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.CloudProviderType.AWS;
import static software.wings.api.CloudProviderType.GCP;
import static software.wings.api.CloudProviderType.PHYSICAL_DATA_CENTER;
import static software.wings.api.DeploymentType.AMI;
import static software.wings.api.DeploymentType.CUSTOM;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.api.DeploymentType.HELM;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.PCF;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.AZURE_VMSS_DEPLOY;
import static software.wings.beans.PhaseStepType.AZURE_VMSS_SETUP;
import static software.wings.beans.PhaseStepType.AZURE_VMSS_SWITCH_ROUTES;
import static software.wings.beans.PhaseStepType.AZURE_WEBAPP_SLOT_SETUP;
import static software.wings.beans.PhaseStepType.AZURE_WEBAPP_SLOT_SWAP;
import static software.wings.beans.PhaseStepType.AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT;
import static software.wings.beans.PhaseStepType.CLUSTER_SETUP;
import static software.wings.beans.PhaseStepType.DEPLOY_SERVICE;
import static software.wings.beans.PhaseStepType.ECS_UPDATE_LISTENER_BG;
import static software.wings.beans.PhaseStepType.ENABLE_SERVICE;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PROVISION_INFRASTRUCTURE;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.PhaseStepType.WRAP_UP;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.beans.container.EcsServiceSpecification.ECS_REPLICA_SCHEDULING_STRATEGY;
import static software.wings.beans.workflow.StepSkipStrategy.Scope.ALL_STEPS;
import static software.wings.beans.workflow.StepSkipStrategy.Scope.SPECIFIC_STEPS;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.DEPLOY;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ECS_DAEMON_SCHEDULING_STRATEGY;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_SERVICE;
import static software.wings.service.impl.workflow.creation.abstractfactories.AbstractWorkflowFactory.Category.GENERAL;
import static software.wings.service.impl.workflow.creation.abstractfactories.AbstractWorkflowFactory.Category.K8S_V2;
import static software.wings.sm.StateType.AZURE_VMSS_ROLLBACK;
import static software.wings.sm.StateType.AZURE_VMSS_SWITCH_ROUTES_ROLLBACK;
import static software.wings.sm.StateType.CUSTOM_DEPLOYMENT_FETCH_INSTANCES;
import static software.wings.sm.StateType.DC_NODE_SELECT;
import static software.wings.sm.StateType.ECS_BG_SERVICE_SETUP;
import static software.wings.sm.StateType.ECS_DAEMON_SERVICE_SETUP;
import static software.wings.sm.StateType.ECS_LISTENER_UPDATE;
import static software.wings.sm.StateType.ECS_SERVICE_DEPLOY;
import static software.wings.sm.StateType.ECS_SERVICE_ROLLBACK;
import static software.wings.sm.StateType.ECS_SERVICE_SETUP;
import static software.wings.sm.StateType.ECS_SERVICE_SETUP_ROLLBACK;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.BasicOrchestrationWorkflow;
import software.wings.beans.BuildWorkflow;
import software.wings.beans.BuildWorkflow.BuildOrchestrationWorkflowBuilder;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.FailureStrategy;
import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.AzureVMSSInfra;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PcfInfraStructure;
import software.wings.infra.PhysicalInfra;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.sm.StateType;
import software.wings.sm.states.customdeployment.InstanceFetchState.InstanceFetchStateKeys;
import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class WorkflowServiceHelperTest extends WingsBaseTest {
  private static String envId = generateUuid();

  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private ArtifactStreamService artifactStreamService;
  @InjectMocks @Inject private WorkflowServiceHelper workflowServiceHelper;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetHPAYamlStringWithCustomMetric() throws Exception {
    WorkflowServiceHelper workflowServiceHelper = new WorkflowServiceHelper();
    String hpaString = workflowServiceHelper.getHPAYamlStringWithCustomMetric(2, 4, 80);

    String yamlForHPAWithCustomMetric = "apiVersion: autoscaling/v2beta1\n"
        + "kind: HorizontalPodAutoscaler\n"
        + "metadata:\n"
        + "  name: hpa-name\n"
        + "spec:\n"
        + "  scaleTargetRef:\n"
        + "    apiVersion: extensions/v1beta1\n"
        + "    kind: Deployment\n"
        + "    name: target-name\n"
        + "  minReplicas: 2\n"
        + "  maxReplicas: 4\n"
        + "  metrics:\n"
        + "  - type: Resource\n"
        + "    resource:\n"
        + "      name: cpu\n"
        + "      targetAverageUtilization: 80\n";

    assertThat(hpaString).isEqualTo(yamlForHPAWithCustomMetric);
  }
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldValidateK8ServiceType() throws Exception {
    String oldServiceId = SERVICE_ID + "_Old";

    Service service = Service.builder().name(SERVICE_NAME).isK8sV2(true).uuid(SERVICE_ID).build();
    Service oldService = Service.builder().name(SERVICE_NAME + "_Old").isK8sV2(false).uuid(oldServiceId).build();

    doReturn(service).when(serviceResourceService).get(APP_ID, SERVICE_ID, false);
    doReturn(oldService).when(serviceResourceService).get(APP_ID, oldServiceId, false);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> workflowServiceHelper.validateServiceCompatibility(APP_ID, SERVICE_ID, oldServiceId));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsDaemonSchedulingStrategy() throws Exception {
    EcsServiceSpecification serviceSpecification = EcsServiceSpecification.builder().build();
    serviceSpecification.setServiceSpecJson(null);
    serviceSpecification.setSchedulingStrategy(ECS_DAEMON_SCHEDULING_STRATEGY);
    doReturn(serviceSpecification).when(serviceResourceService).getEcsServiceSpecification(anyString(), anyString());

    boolean isDaemonSchedulingStrategy =
        workflowServiceHelper.isDaemonSchedulingStrategy(APP_ID, aWorkflowPhase().serviceId(SERVICE_ID).build(), BASIC);
    assertThat(isDaemonSchedulingStrategy).isTrue();

    isDaemonSchedulingStrategy = workflowServiceHelper.isDaemonSchedulingStrategy(
        APP_ID, aWorkflowPhase().serviceId(SERVICE_ID).build(), OrchestrationWorkflowType.CANARY);
    assertThat(isDaemonSchedulingStrategy).isFalse();
    isDaemonSchedulingStrategy = workflowServiceHelper.isDaemonSchedulingStrategy(
        APP_ID, aWorkflowPhase().serviceId(SERVICE_ID).build(), OrchestrationWorkflowType.ROLLING);
    assertThat(isDaemonSchedulingStrategy).isFalse();
    isDaemonSchedulingStrategy = workflowServiceHelper.isDaemonSchedulingStrategy(
        APP_ID, aWorkflowPhase().serviceId(SERVICE_ID).build(), OrchestrationWorkflowType.CUSTOM);
    assertThat(isDaemonSchedulingStrategy).isFalse();

    serviceSpecification.setSchedulingStrategy(ECS_REPLICA_SCHEDULING_STRATEGY);
    isDaemonSchedulingStrategy =
        workflowServiceHelper.isDaemonSchedulingStrategy(APP_ID, aWorkflowPhase().serviceId(SERVICE_ID).build(), BASIC);
    assertThat(isDaemonSchedulingStrategy).isFalse();

    serviceSpecification.resetToDefaultSpecification();
    isDaemonSchedulingStrategy =
        workflowServiceHelper.isDaemonSchedulingStrategy(APP_ID, aWorkflowPhase().serviceId(SERVICE_ID).build(), BASIC);
    assertThat(isDaemonSchedulingStrategy).isFalse();

    serviceSpecification.setServiceSpecJson(serviceSpecification.getServiceSpecJson().replace("REPLICA", "DAEMON"));
    isDaemonSchedulingStrategy =
        workflowServiceHelper.isDaemonSchedulingStrategy(APP_ID, aWorkflowPhase().serviceId(SERVICE_ID).build(), BASIC);
    assertThat(isDaemonSchedulingStrategy).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForSpotinstTrafficShiftAlb() {
    ServiceResourceService mockServiceResourceService = mock(ServiceResourceService.class);
    WorkflowServiceHelper helper = spy(WorkflowServiceHelper.class);
    on(helper).set("serviceResourceService", mockServiceResourceService);
    Service service = Service.builder().appId(APP_ID).build();
    doReturn(service).when(mockServiceResourceService).getWithDetails(anyString(), anyString());
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    helper.generateNewWorkflowPhaseStepsForSpotinstAlbTrafficShift(APP_ID, workflowPhase);
    verifyPhase(workflowPhase,
        asList(StateType.SPOTINST_ALB_SHIFT_SETUP.name(), StateType.SPOTINST_ALB_SHIFT_DEPLOY.name()), 5);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGenerateRollbackWorkflowPhaseForSpotinstAlbTrafficShift() {
    WorkflowServiceHelper helper = spy(WorkflowServiceHelper.class);
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    WorkflowPhase rollbackPhase = helper.generateRollbackWorkflowPhaseForSpotinstAlbTrafficShift(workflowPhase);
    verifyPhase(rollbackPhase, singletonList(StateType.SPOTINST_LISTENER_ALB_SHIFT_ROLLBACK.name()), 3);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForAwsAmiTrafficShiftAlb() {
    ServiceResourceService mockServiceResourceService = mock(ServiceResourceService.class);
    WorkflowServiceHelper helper = spy(WorkflowServiceHelper.class);
    on(helper).set("serviceResourceService", mockServiceResourceService);
    Service service = Service.builder().appId(APP_ID).build();
    doReturn(service).when(mockServiceResourceService).getWithDetails(anyString(), anyString());
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    helper.generateNewWorkflowPhaseStepsForAsgAmiAlbTrafficShiftBlueGreen(APP_ID, workflowPhase);
    verifyPhase(workflowPhase,
        asList(StateType.ASG_AMI_SERVICE_ALB_SHIFT_SETUP.name(), StateType.ASG_AMI_SERVICE_ALB_SHIFT_DEPLOY.name()), 5);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGenerateRollbackWorkflowPhaseForAwsAmiAlbTrafficShift() {
    WorkflowServiceHelper helper = spy(WorkflowServiceHelper.class);
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    WorkflowPhase rollbackPhase = helper.generateRollbackWorkflowPhaseForAsgAmiTrafficShiftBlueGreen(workflowPhase);
    verifyPhase(rollbackPhase, singletonList(StateType.ASG_AMI_ROLLBACK_ALB_SHIFT_SWITCH_ROUTES.name()), 3);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForECS_ReplicaStrategy() throws Exception {
    doReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(null).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    EcsServiceSpecification specification = EcsServiceSpecification.builder().build();
    specification.setServiceSpecJson(null);
    specification.setSchedulingStrategy(ECS_REPLICA_SCHEDULING_STRATEGY);
    doReturn(specification).when(serviceResourceService).getEcsServiceSpecification(anyString(), anyString());

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    workflowServiceHelper.generateNewWorkflowPhaseStepsForECS(APP_ID, workflowPhase, true, BASIC);
    verifyPhase(workflowPhase, asList(new String[] {ECS_SERVICE_SETUP.name(), ECS_SERVICE_DEPLOY.name()}), 4);

    specification.resetToDefaultSpecification();
    workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForECS(APP_ID, workflowPhase, true, BASIC);
    verifyPhase(workflowPhase, asList(new String[] {ECS_SERVICE_SETUP.name(), ECS_SERVICE_DEPLOY.name()}), 4);

    // Non basic workflows should not respect DEAMON scheduling strategy
    specification.setServiceSpecJson(null);
    specification.setSchedulingStrategy(ECS_DAEMON_SCHEDULING_STRATEGY);
    workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForECS(
        APP_ID, workflowPhase, true, OrchestrationWorkflowType.CANARY);
    verifyPhase(workflowPhase, asList(new String[] {ECS_SERVICE_SETUP.name(), ECS_SERVICE_DEPLOY.name()}), 4);

    specification.resetToDefaultSpecification();
    specification.setServiceSpecJson(specification.getServiceSpecJson().replace("REPLICA", "DAEMON"));
    workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForECS(
        APP_ID, workflowPhase, true, OrchestrationWorkflowType.CUSTOM);
    verifyPhase(workflowPhase, asList(new String[] {ECS_SERVICE_SETUP.name(), ECS_SERVICE_DEPLOY.name()}), 4);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForECS_BG() throws Exception {
    doReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(null).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    EcsServiceSpecification specification = EcsServiceSpecification.builder().build();
    specification.setServiceSpecJson(null);
    specification.setSchedulingStrategy(ECS_REPLICA_SCHEDULING_STRATEGY);
    doReturn(specification).when(serviceResourceService).getEcsServiceSpecification(anyString(), anyString());

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    workflowServiceHelper.generateNewWorkflowPhaseStepsForECSBlueGreen(APP_ID, workflowPhase, true, false);
    verifyPhase(workflowPhase,
        asList(new String[] {ECS_BG_SERVICE_SETUP.name(), ECS_SERVICE_DEPLOY.name(), null, ECS_LISTENER_UPDATE.name()}),
        5);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForECS_BG_addProvisionStep() {
    doReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(null).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    EcsServiceSpecification specification = EcsServiceSpecification.builder().build();
    specification.setServiceSpecJson(null);
    specification.setSchedulingStrategy(ECS_REPLICA_SCHEDULING_STRATEGY);
    doReturn(specification).when(serviceResourceService).getEcsServiceSpecification(anyString(), anyString());

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    workflowServiceHelper.generateNewWorkflowPhaseStepsForECSBlueGreen(APP_ID, workflowPhase, true, true);
    verifyPhase(workflowPhase,
        asList(new String[] {
            null, ECS_BG_SERVICE_SETUP.name(), ECS_SERVICE_DEPLOY.name(), null, ECS_LISTENER_UPDATE.name()}),
        6);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PROVISION_INFRASTRUCTURE, PhaseStepType.CONTAINER_SETUP, PhaseStepType.CONTAINER_DEPLOY,
            VERIFY_SERVICE, ECS_UPDATE_LISTENER_BG, WRAP_UP);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForECS_DaemonStrategy() throws Exception {
    doReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(null).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    EcsServiceSpecification serviceSpecification = EcsServiceSpecification.builder().build();
    serviceSpecification.setServiceSpecJson(null);
    serviceSpecification.setSchedulingStrategy(ECS_DAEMON_SCHEDULING_STRATEGY);
    doReturn(serviceSpecification).when(serviceResourceService).getEcsServiceSpecification(anyString(), anyString());

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    workflowServiceHelper.generateNewWorkflowPhaseStepsForECS(APP_ID, workflowPhase, true, BASIC);
    verifyPhase(workflowPhase, asList(new String[] {ECS_DAEMON_SERVICE_SETUP.name()}), 3);

    serviceSpecification.resetToDefaultSpecification();
    serviceSpecification.setServiceSpecJson(serviceSpecification.getServiceSpecJson().replace("REPLICA", "DAEMON"));
    workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForECS(APP_ID, workflowPhase, true, BASIC);
    verifyPhase(workflowPhase, asList(new String[] {ECS_DAEMON_SERVICE_SETUP.name()}), 3);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateRollbackWorkflowPhaseForEcs_ReplicaStrategy() throws Exception {
    doReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(null).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    EcsServiceSpecification serviceSpecification = EcsServiceSpecification.builder().build();
    serviceSpecification.setServiceSpecJson(null);
    serviceSpecification.setSchedulingStrategy(ECS_REPLICA_SCHEDULING_STRATEGY);
    doReturn(serviceSpecification).when(serviceResourceService).getEcsServiceSpecification(anyString(), anyString());

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    WorkflowPhase rollbackPhase =
        workflowServiceHelper.generateRollbackWorkflowPhaseForEcs(APP_ID, workflowPhase, BASIC);
    verifyPhase(rollbackPhase, asList(new String[] {ECS_SERVICE_ROLLBACK.name()}), 3);

    serviceSpecification.resetToDefaultSpecification();
    workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    workflowServiceHelper.generateRollbackWorkflowPhaseForEcs(APP_ID, workflowPhase, BASIC);
    verifyPhase(rollbackPhase, asList(new String[] {ECS_SERVICE_ROLLBACK.name()}), 3);

    // Non basic workflows should not respect DEAMON scheduling strategy
    serviceSpecification.setServiceSpecJson(null);
    serviceSpecification.setSchedulingStrategy(ECS_DAEMON_SCHEDULING_STRATEGY);
    workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    workflowServiceHelper.generateRollbackWorkflowPhaseForEcs(APP_ID, workflowPhase, OrchestrationWorkflowType.CANARY);
    verifyPhase(rollbackPhase, asList(new String[] {ECS_SERVICE_ROLLBACK.name()}), 3);

    serviceSpecification.resetToDefaultSpecification();
    serviceSpecification.setServiceSpecJson(serviceSpecification.getServiceSpecJson().replace("REPLICA", "DAEMON"));
    workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    workflowServiceHelper.generateRollbackWorkflowPhaseForEcs(APP_ID, workflowPhase, OrchestrationWorkflowType.CUSTOM);
    verifyPhase(rollbackPhase, asList(new String[] {ECS_SERVICE_ROLLBACK.name()}), 3);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateRollbackWorkflowPhaseForEcs_BG() {
    doReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(null).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    EcsServiceSpecification serviceSpecification = EcsServiceSpecification.builder().build();
    serviceSpecification.setServiceSpecJson(null);
    serviceSpecification.setSchedulingStrategy(ECS_REPLICA_SCHEDULING_STRATEGY);
    doReturn(serviceSpecification).when(serviceResourceService).getEcsServiceSpecification(anyString(), anyString());

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    WorkflowPhase rollbackPhase = workflowServiceHelper.generateRollbackWorkflowPhaseForEcsBlueGreen(workflowPhase);
    verifyPhase(rollbackPhase,
        asList(new String[] {StateType.ECS_LISTENER_UPDATE_ROLLBACK.name(), ECS_SERVICE_ROLLBACK.name()}), 4);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateRollbackWorkflowPhaseForEcs_DaemonStrategy() throws Exception {
    doReturn(Service.builder().uuid(SERVICE_ID).serviceCommands(null).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());

    EcsServiceSpecification ecsServiceSpecification = EcsServiceSpecification.builder().build();
    ecsServiceSpecification.setServiceSpecJson(null);
    ecsServiceSpecification.setSchedulingStrategy(ECS_DAEMON_SCHEDULING_STRATEGY);
    doReturn(ecsServiceSpecification).when(serviceResourceService).getEcsServiceSpecification(anyString(), anyString());

    WorkflowPhase phase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    WorkflowPhase rollbackPhase = workflowServiceHelper.generateRollbackWorkflowPhaseForEcs(APP_ID, phase, BASIC);
    verifyPhase(rollbackPhase, asList(new String[] {ECS_SERVICE_SETUP_ROLLBACK.name()}), 3);

    ecsServiceSpecification.resetToDefaultSpecification();
    ecsServiceSpecification.setServiceSpecJson(
        ecsServiceSpecification.getServiceSpecJson().replace("REPLICA", "DAEMON"));
    phase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    rollbackPhase = workflowServiceHelper.generateRollbackWorkflowPhaseForEcs(APP_ID, phase, BASIC);
    verifyPhase(rollbackPhase, asList(new String[] {ECS_SERVICE_SETUP_ROLLBACK.name()}), 3);
  }

  @Test
  @Owner(developers = {IVAN, ANIL})
  @Category(UnitTests.class)
  public void testGenerateRollbackWorkflowPhaseForAzureVMSS() {
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    WorkflowPhase rollbackPhase = workflowServiceHelper.generateRollbackWorkflowPhaseForAzureVMSS(workflowPhase, BASIC);
    verifyPhase(rollbackPhase, singletonList(AZURE_VMSS_ROLLBACK.name()), 3);

    WorkflowPhase rollbackCanary = workflowServiceHelper.generateRollbackWorkflowPhaseForAzureVMSS(
        workflowPhase, OrchestrationWorkflowType.CANARY);
    verifyPhase(rollbackCanary, singletonList(AZURE_VMSS_ROLLBACK.name()), 3);

    WorkflowPhase rollbackPhaseBlueGreen = workflowServiceHelper.generateRollbackWorkflowPhaseForAzureVMSS(
        workflowPhase, OrchestrationWorkflowType.BLUE_GREEN);
    verifyPhase(rollbackPhaseBlueGreen, singletonList(AZURE_VMSS_SWITCH_ROUTES_ROLLBACK.name()), 3);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGenerateRollbackWorkflowPhaseForAzureWebApp() {
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();
    WorkflowPhase rollbackPhase = workflowServiceHelper.generateRollbackWorkflowPhaseForAzureWebApp(workflowPhase);

    List<PhaseStepType> phaseStepTypes =
        rollbackPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes).containsExactly(PhaseStepType.AZURE_WEBAPP_SLOT_ROLLBACK, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckWorkflowVariablesOverrides() {
    String stageName = "stageName";
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder().name(stageName).build();

    // No required variables.
    assertThatCode(() -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(pipelineStageElement, null, null, null))
        .doesNotThrowAnyException();

    List<Variable> variables1 = singletonList(aVariable().name("v1").type(VariableType.TEXT).build());
    assertThatCode(
        () -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(pipelineStageElement, variables1, null, null))
        .doesNotThrowAnyException();

    List<Variable> variables2 =
        singletonList(aVariable().type(VariableType.TEXT).name("v1").mandatory(true).fixed(true).value("val").build());
    assertThatCode(
        () -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(pipelineStageElement, variables2, null, null))
        .doesNotThrowAnyException();

    // Required variables.
    List<Variable> variables3 =
        singletonList(aVariable().type(VariableType.TEXT).name("v1").mandatory(true).fixed(false).build());
    assertThatThrownBy(
        () -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(pipelineStageElement, variables3, null, null))
        .isInstanceOf(InvalidRequestException.class);

    // Correct set of variables and overrides.
    List<Variable> variables4 =
        asList(aVariable().type(VariableType.ENTITY).name("v1").mandatory(true).entityType(EntityType.SERVICE).build(),
            aVariable().type(VariableType.ENTITY).name("v2").mandatory(true).entityType(EntityType.ENVIRONMENT).build(),
            aVariable()
                .type(VariableType.ENTITY)
                .name("v3")
                .mandatory(true)
                .entityType(EntityType.INFRASTRUCTURE_MAPPING)
                .build(),
            aVariable()
                .type(VariableType.ENTITY)
                .name("v4")
                .mandatory(true)
                .entityType(EntityType.INFRASTRUCTURE_DEFINITION)
                .build(),
            aVariable()
                .type(VariableType.ENTITY)
                .name("v5")
                .mandatory(true)
                .entityType(EntityType.APPDYNAMICS_APPID)
                .build(),
            aVariable().type(VariableType.TEXT).name("v6").mandatory(true).fixed(false).build(),
            aVariable().type(VariableType.TEXT).name("v7").mandatory(true).fixed(true).value("val").build());
    Map<String, String> workflowStepVariables1 = ImmutableMap.<String, String>builder()
                                                     .put("v1", "val1")
                                                     .put("v2", "val2")
                                                     .put("v3", "${pv3}")
                                                     .put("v4", "val4")
                                                     .put("v5", "${pv5}")
                                                     .put("v6", "${pv6}")
                                                     .build();
    Map<String, String> pipelineVariables1 =
        ImmutableMap.of("v1", "${pv1}", "pv3", "val3", "pv5", "val5", "pv6", "val6");
    assertThatCode(()
                       -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(
                           pipelineStageElement, variables4, workflowStepVariables1, pipelineVariables1))
        .doesNotThrowAnyException();

    // Missing pipeline variable pv6.
    Map<String, String> pipelineVariables2 = ImmutableMap.of("pv3", "val3", "pv5", "val5");
    assertThatThrownBy(()
                           -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(
                               pipelineStageElement, variables4, workflowStepVariables1, pipelineVariables2))
        .isInstanceOf(InvalidRequestException.class);

    // Correct set of variables and overrides. v4 moves from pipeline stage element to pipeline variables.
    Map<String, String> workflowStepVariables2 = ImmutableMap.<String, String>builder()
                                                     .put("v1", "val1")
                                                     .put("v2", "val2")
                                                     .put("v3", "${pv3}")
                                                     .put("v5", "${pv5}")
                                                     .put("v6", "${pv6}")
                                                     .build();
    Map<String, String> pipelineVariables3 = ImmutableMap.of("pv3", "val3", "pv5", "val5", "pv6", "val6", "v4", "val4");
    assertThatCode(()
                       -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(
                           pipelineStageElement, variables4, workflowStepVariables2, pipelineVariables3))
        .doesNotThrowAnyException();

    // Missing variable v4 in both pipeline stage element and pipeline variables.
    assertThatThrownBy(()
                           -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(
                               pipelineStageElement, variables4, workflowStepVariables2, pipelineVariables1))
        .isInstanceOf(InvalidRequestException.class);

    // Ignore variable values with '.' for non-entity required variables.
    Map<String, String> workflowStepVariables3 = ImmutableMap.<String, String>builder()
                                                     .put("v1", "val1")
                                                     .put("v2", "val2")
                                                     .put("v3", "${pv3}")
                                                     .put("v4", "val4")
                                                     .put("v5", "${pv5}")
                                                     .put("v6", "${workflow.variables.v6}")
                                                     .build();
    Map<String, String> pipelineVariables4 = ImmutableMap.of("pv3", "val3", "pv5", "val5");
    assertThatCode(()
                       -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(
                           pipelineStageElement, variables4, workflowStepVariables3, pipelineVariables4))
        .doesNotThrowAnyException();

    // Ignore variable values with '.' for non-entity required variables.
    Map<String, String> workflowStepVariables4 = ImmutableMap.<String, String>builder()
                                                     .put("v1", "val1")
                                                     .put("v2", "val2")
                                                     .put("v3", "${workflow.variables.v3}")
                                                     .put("v4", "val4")
                                                     .put("v5", "${pv5}")
                                                     .put("v6", "val6")
                                                     .build();
    // Having "workflow.variables.v3" in pipeline variables should not have any effect. We should still throw an error
    // for entity variables if they have a dot in their value.
    Map<String, String> pipelineVariables5 = ImmutableMap.of("workflow.variables.v3", "val3", "pv5", "val5");
    assertThatThrownBy(()
                           -> WorkflowServiceHelper.checkWorkflowVariablesOverrides(
                               pipelineStageElement, variables4, workflowStepVariables4, pipelineVariables5))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestWorkflowOverrideVariables() {
    List<Variable> variables =
        asList(aVariable().name("Environment").build(), aVariable().name("OverrideVariable").build(),
            aVariable().name("MyOwnVariable").build(), aVariable().name("NewlyCreatedVariable").build());

    Map<String, String> pipelineVariables = new HashMap<>();
    pipelineVariables.put("Env", "EnvironmentValue");
    pipelineVariables.put("Service", "ServiceValue");
    pipelineVariables.put("OverrideVariable", "${app.name}");
    pipelineVariables.put("NewlyCreatedVariable", "NewlyCreatedVariableValue");

    Map<String, String> pipelineStepVariables = new HashMap<>();
    pipelineStepVariables.put("Environment", "${Env}");
    pipelineStepVariables.put("OverrideVariable", "myValue");
    pipelineStepVariables.put("MyOwnVariable", "MyOwnVariableValue");

    Map<String, String> overrideWorkflowVariables =
        WorkflowServiceHelper.overrideWorkflowVariables(variables, pipelineStepVariables, pipelineVariables);

    // Case 1: Override the workflow variable name
    assertThat(overrideWorkflowVariables).containsKeys("Environment", "OverrideVariable", "MyOwnVariable");
    assertThat(overrideWorkflowVariables).containsValues("EnvironmentValue", "myValue", "MyOwnVariableValue");
    assertThat(overrideWorkflowVariables).doesNotContainKey("Service");
    assertThat(overrideWorkflowVariables).containsKey("NewlyCreatedVariable");
    assertThat(overrideWorkflowVariables.get("NewlyCreatedVariable")).isEqualTo("NewlyCreatedVariableValue");

    // Case 2: No pipeline variables .. should return all the workflow variables
    overrideWorkflowVariables =
        WorkflowServiceHelper.overrideWorkflowVariables(variables, pipelineStepVariables, new HashMap<>());
    assertThat(overrideWorkflowVariables).containsKeys("Environment", "OverrideVariable", "MyOwnVariable");
    assertThat(overrideWorkflowVariables).containsValues("${Env}", "myValue", "MyOwnVariableValue");

    // Case 3: Pipeline Step has variable, however Workflow does not have that variable anymore
    variables = asList(aVariable().name("Environment").build(), aVariable().name("OverrideVariable").build());
    overrideWorkflowVariables =
        WorkflowServiceHelper.overrideWorkflowVariables(variables, pipelineStepVariables, pipelineVariables);
    assertThat(overrideWorkflowVariables).doesNotContainKey("MyOwnVariable");

    // Case 4: Pipeline step does not have variable, however pipeline variable has the value
    variables = asList(aVariable().name("Environment").build(), aVariable().name("OverrideVariable").build(),
        aVariable().name("MyOwnVariable").build());
    overrideWorkflowVariables =
        WorkflowServiceHelper.overrideWorkflowVariables(variables, pipelineStepVariables, pipelineVariables);
    assertThat(overrideWorkflowVariables).doesNotContainKey("Service");
    assertThat(overrideWorkflowVariables.get("Service")).isNullOrEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testWorkflowHasSshDeploymentPhase() {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow_ssh =
        aCanaryOrchestrationWorkflow()
            .withWorkflowPhases(Arrays.asList(aWorkflowPhase().deploymentType(SSH).build()))
            .build();
    assertThat(workflowServiceHelper.workflowHasSshDeploymentPhase(canaryOrchestrationWorkflow_ssh)).isTrue();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow_withoutssh =
        aCanaryOrchestrationWorkflow()
            .withWorkflowPhases(Arrays.asList(aWorkflowPhase().deploymentType(KUBERNETES).build()))
            .build();

    assertThat(workflowServiceHelper.workflowHasSshDeploymentPhase(canaryOrchestrationWorkflow_withoutssh)).isFalse();

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow_withoutPhase =
        aCanaryOrchestrationWorkflow().withWorkflowPhases(Collections.emptyList()).build();

    assertThat(workflowServiceHelper.workflowHasSshDeploymentPhase(canaryOrchestrationWorkflow_withoutPhase)).isFalse();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testObtainDeploymentTypes() {
    List<WorkflowPhase> workflowPhases = ImmutableList.<WorkflowPhase>of(aWorkflowPhase().deploymentType(SSH).build(),
        aWorkflowPhase().deploymentType(KUBERNETES).build(), aWorkflowPhase().deploymentType(ECS).build(),
        aWorkflowPhase().deploymentType(SSH).build(), aWorkflowPhase().deploymentType(ECS).build(),
        aWorkflowPhase().deploymentType(ECS).build(), aWorkflowPhase().build());
    final CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withWorkflowPhases(workflowPhases).build();
    assertThat(workflowServiceHelper.obtainDeploymentTypes(canaryOrchestrationWorkflow))
        .containsExactlyInAnyOrder(SSH, ECS, KUBERNETES);

    final BuildWorkflow buildWorkflow = BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow().build();
    assertThat(workflowServiceHelper.obtainDeploymentTypes(buildWorkflow)).isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testNeedArtifactCheckStep() {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = null;
    List<WorkflowPhase> workflowPhases = null;

    InfrastructureDefinition awsInfraDef = InfrastructureDefinition.builder()
                                               .appId(APP_ID)
                                               .uuid(INFRA_DEFINITION_ID)
                                               .deploymentType(SSH)
                                               .cloudProviderType(AWS)
                                               .infrastructure(AwsInstanceInfrastructure.builder().build())
                                               .build();

    workflowPhases = ImmutableList.<WorkflowPhase>of(aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).build());

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(APP_ID, singletonList(INFRA_DEFINITION_ID)))
        .thenReturn(singletonList(awsInfraDef));
    canaryOrchestrationWorkflow = aCanaryOrchestrationWorkflow().withWorkflowPhases(workflowPhases).build();
    assertThat(workflowServiceHelper.needArtifactCheckStep(awsInfraDef.getAppId(), canaryOrchestrationWorkflow))
        .isTrue();

    InfrastructureDefinition physicalInfraDef = InfrastructureDefinition.builder()
                                                    .appId(APP_ID)
                                                    .deploymentType(SSH)
                                                    .cloudProviderType(PHYSICAL_DATA_CENTER)
                                                    .infrastructure(PhysicalInfra.builder().build())
                                                    .build();

    workflowPhases = ImmutableList.<WorkflowPhase>of(aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).build());

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(APP_ID, singletonList(INFRA_DEFINITION_ID)))
        .thenReturn(singletonList(physicalInfraDef));
    canaryOrchestrationWorkflow = aCanaryOrchestrationWorkflow().withWorkflowPhases(workflowPhases).build();
    assertThat(workflowServiceHelper.needArtifactCheckStep(physicalInfraDef.getAppId(), canaryOrchestrationWorkflow))
        .isTrue();

    InfrastructureDefinition pcfInfraDef = InfrastructureDefinition.builder()
                                               .appId(APP_ID)
                                               .uuid(INFRA_DEFINITION_ID)
                                               .deploymentType(PCF)
                                               .cloudProviderType(CloudProviderType.PCF)
                                               .infrastructure(PcfInfraStructure.builder().build())
                                               .build();

    workflowPhases = ImmutableList.<WorkflowPhase>of(aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).build());

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(APP_ID, singletonList(INFRA_DEFINITION_ID)))
        .thenReturn(singletonList(pcfInfraDef));
    canaryOrchestrationWorkflow = aCanaryOrchestrationWorkflow().withWorkflowPhases(workflowPhases).build();
    assertThat(workflowServiceHelper.needArtifactCheckStep(pcfInfraDef.getAppId(), canaryOrchestrationWorkflow))
        .isTrue();

    InfrastructureDefinition gcpK8sInfraDef = InfrastructureDefinition.builder()
                                                  .appId(APP_ID)
                                                  .uuid(INFRA_DEFINITION_ID)
                                                  .deploymentType(KUBERNETES)
                                                  .cloudProviderType(GCP)
                                                  .infrastructure(GoogleKubernetesEngine.builder().build())
                                                  .build();

    workflowPhases =
        ImmutableList.<WorkflowPhase>of(aWorkflowPhase().infraDefinitionId(gcpK8sInfraDef.getUuid()).build());

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(APP_ID, singletonList(INFRA_DEFINITION_ID)))
        .thenReturn(singletonList(gcpK8sInfraDef));
    canaryOrchestrationWorkflow = aCanaryOrchestrationWorkflow().withWorkflowPhases(workflowPhases).build();
    assertThat(workflowServiceHelper.needArtifactCheckStep(gcpK8sInfraDef.getAppId(), canaryOrchestrationWorkflow))
        .isFalse();

    workflowPhases = Collections.emptyList();
    canaryOrchestrationWorkflow = aCanaryOrchestrationWorkflow().withWorkflowPhases(workflowPhases).build();
    assertThat(workflowServiceHelper.needArtifactCheckStep(APP_ID, canaryOrchestrationWorkflow)).isFalse();

    InfrastructureDefinition infrastructureDefinition = InfrastructureDefinition.builder()
                                                            .uuid("id1")
                                                            .appId(APP_ID)
                                                            .infrastructure(PcfInfraStructure.builder().build())
                                                            .build();
    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(anyString(), anyList()))
        .thenReturn(Arrays.asList(infrastructureDefinition));
    workflowPhases = ImmutableList.<WorkflowPhase>of(aWorkflowPhase().infraDefinitionId(INFRA_DEFINITION_ID).build());
    canaryOrchestrationWorkflow = aCanaryOrchestrationWorkflow().withWorkflowPhases(workflowPhases).build();
    assertThat(workflowServiceHelper.needArtifactCheckStep(APP_ID, canaryOrchestrationWorkflow)).isTrue();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureArtifactCheckInPreDeployment() {
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        aCanaryOrchestrationWorkflow().withPreDeploymentSteps(null).build();

    assertThat(workflowServiceHelper.ensureArtifactCheckInPreDeployment(canaryOrchestrationWorkflow)).isTrue();
    assertThat(workflowServiceHelper.ensureArtifactCheckInPreDeployment(canaryOrchestrationWorkflow)).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testObtainEnvIdWithoutOrchestration() {
    Workflow workflow = aWorkflow().envId(ENV_ID).build();
    assertThat(workflowServiceHelper.obtainEnvIdWithoutOrchestration(workflow, null)).isEqualTo(workflow.getEnvId());

    Workflow workflowTemplated =
        aWorkflow()
            .templateExpressions(singletonList(
                TemplateExpression.builder().fieldName(WorkflowKeys.envId).expression("${envVar}").build()))
            .build();
    assertThat(
        workflowServiceHelper.obtainEnvIdWithoutOrchestration(workflowTemplated, ImmutableMap.of("envVar", ENV_ID)))
        .isEqualTo(ENV_ID);
    assertThat(workflowServiceHelper.obtainEnvIdWithoutOrchestration(
                   workflowTemplated, ImmutableMap.of("envVarOther", ENV_ID)))
        .isNull();
    assertThat(workflowServiceHelper.obtainEnvIdWithoutOrchestration(workflowTemplated, null)).isNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testObtainTemplatedEnvironmentId() {
    Workflow workflow = aWorkflow().envId(ENV_ID).build();
    assertThat(workflowServiceHelper.obtainTemplatedEnvironmentId(workflow, null)).isEqualTo(workflow.getEnvId());
    assertThat(workflowServiceHelper.resolveEnvironmentId(workflow, null)).isEqualTo(workflow.getEnvId());

    Workflow workflow_templated =
        aWorkflow()
            .templateExpressions(Arrays.asList(TemplateExpression.builder().fieldName(WorkflowKeys.envId).build()))
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow()
                    .withUserVariables(ImmutableList.of(
                        aVariable().name(WorkflowKeys.envId).entityType(EntityType.ENVIRONMENT).build()))
                    .build())
            .build();
    Map<String, String> workflowVariables = ImmutableMap.of(WorkflowKeys.envId, ENV_ID);
    assertThat(workflowServiceHelper.obtainTemplatedEnvironmentId(workflow_templated, workflowVariables))
        .isEqualTo(ENV_ID);
    assertThat(workflowServiceHelper.obtainTemplatedEnvironmentId(workflow_templated, emptyMap())).isEqualTo(null);

    assertThat(workflowServiceHelper.resolveEnvironmentId(workflow_templated, workflowVariables)).isEqualTo(ENV_ID);
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> workflowServiceHelper.resolveEnvironmentId(workflow_templated, emptyMap()));

    Map<String, String> incorrectVariableMap = ImmutableMap.of(WorkflowKeys.appId, APP_ID);
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> workflowServiceHelper.resolveEnvironmentId(workflow_templated, incorrectVariableMap));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSetGetKeywords() {
    Workflow workflow = aWorkflow()
                            .appId(APP_ID)
                            .envId(ENV_ID)
                            .description("test")
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .name(WORKFLOW_NAME)
                            .notes("test")
                            .build();

    workflowServiceHelper.setKeywords(workflow);
    assertThat(workflow.getKeywords()).isNotEmpty();

    when(environmentService.get(workflow.getAppId(), workflow.getEnvId()))
        .thenReturn(Environment.Builder.anEnvironment().name(ENV_NAME).uuid(ENV_ID).build());

    assertThat(workflowServiceHelper.getKeywords(workflow)).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testValidateServiceAndInfraDefinition() {
    InfrastructureDefinition infraDef = InfrastructureDefinition.builder()
                                            .uuid(INFRA_DEFINITION_ID)
                                            .appId(APP_ID)
                                            .envId(ENV_ID)
                                            .cloudProviderType(AWS)
                                            .deploymentType(SSH)
                                            .build();
    Service sshService = Service.builder().deploymentType(SSH).uuid(SERVICE_ID).build();
    Service K8sService = Service.builder().deploymentType(KUBERNETES).uuid(SERVICE_ID).build();
    Service noDeploymentTypeService = Service.builder().uuid(SERVICE_ID).build();

    // mock stuff
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(null, noDeploymentTypeService, K8sService, noDeploymentTypeService, sshService);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infraDef);

    // service is null
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () -> workflowServiceHelper.validateServiceAndInfraDefinition(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID));

    // service is noDeploymentTypeService
    workflowServiceHelper.validateServiceAndInfraDefinition(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID);

    // service is K8sService
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () -> workflowServiceHelper.validateServiceAndInfraDefinition(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID));

    // service is sshService
    workflowServiceHelper.validateServiceAndInfraDefinition(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID);

    // service is sshService
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () -> workflowServiceHelper.validateServiceAndInfraDefinition(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID));

    // service is sshService
    infraDef.setScopedToServices(ImmutableList.of(SERVICE_ID + "-test"));
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infraDef);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () -> workflowServiceHelper.validateServiceAndInfraDefinition(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID));

    // validate null cases
    workflowServiceHelper.validateServiceAndInfraDefinition(APP_ID, SERVICE_ID, null);
    workflowServiceHelper.validateServiceAndInfraDefinition(APP_ID, null, INFRA_DEFINITION_ID);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForSpotinstandOtherAwsAmiWorkflow() {
    InfrastructureDefinition awsAmiInfraDef = InfrastructureDefinition.builder()
                                                  .uuid(INFRA_DEFINITION_ID)
                                                  .appId(APP_ID)
                                                  .envId(ENV_ID)
                                                  .cloudProviderType(AWS)
                                                  .deploymentType(AMI)
                                                  .infrastructure(AwsAmiInfrastructure.builder().build())
                                                  .build();
    Service amiService = Service.builder().deploymentType(AMI).uuid(SERVICE_ID).build();
    WorkflowPhase workflowPhase = aWorkflowPhase()
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .serviceId(SERVICE_ID)
                                      .build();

    // mock
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(awsAmiInfraDef);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(awsAmiInfraDef.getInfraMapping());
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(amiService);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    // feature flag off
    workflowServiceHelper.generateNewWorkflowPhaseStepsForSpotinst(APP_ID, workflowPhase, true);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.SPOTINST_SETUP, PhaseStepType.SPOTINST_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    // feature flag on
    workflowPhase.setPhaseSteps(new ArrayList<>());
    workflowServiceHelper.generateNewWorkflowPhaseStepsForSpotinst(APP_ID, workflowPhase, true);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.SPOTINST_SETUP, PhaseStepType.SPOTINST_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForSpotinst(APP_ID, workflowPhase, false);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes).containsExactly(PhaseStepType.SPOTINST_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    // SPOT INST BLUE GREEN

    // feature flag off
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForSpotInstBlueGreen(APP_ID, workflowPhase, true);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.SPOTINST_SETUP, PhaseStepType.SPOTINST_DEPLOY, VERIFY_SERVICE,
            PhaseStepType.SPOTINST_LISTENER_UPDATE, WRAP_UP);

    // feature flag on
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForSpotInstBlueGreen(APP_ID, workflowPhase, true);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.SPOTINST_SETUP, PhaseStepType.SPOTINST_DEPLOY, VERIFY_SERVICE,
            PhaseStepType.SPOTINST_LISTENER_UPDATE, WRAP_UP);

    // AWS AMI BLUE GREEN

    // feature flag off
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAWSAmiBlueGreen(APP_ID, workflowPhase, true, false);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP, PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP,
            VERIFY_SERVICE, PhaseStepType.AMI_SWITCH_AUTOSCALING_GROUP_ROUTES, WRAP_UP);

    // feature flag on
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAWSAmiBlueGreen(APP_ID, workflowPhase, true, false);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP, PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP,
            VERIFY_SERVICE, PhaseStepType.AMI_SWITCH_AUTOSCALING_GROUP_ROUTES, WRAP_UP);

    // dynamic infra
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAWSAmiBlueGreen(APP_ID, workflowPhase, true, true);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PROVISION_INFRASTRUCTURE, PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP,
            PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP, VERIFY_SERVICE,
            PhaseStepType.AMI_SWITCH_AUTOSCALING_GROUP_ROUTES, WRAP_UP);

    // AWS AMI BASIC

    // feature flag off
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAWSAmi(APP_ID, workflowPhase, true, false, BASIC);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP, PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP,
            VERIFY_SERVICE, WRAP_UP);

    // feature flag on
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAWSAmi(APP_ID, workflowPhase, true, false, BASIC);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP, PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP,
            VERIFY_SERVICE, WRAP_UP);

    // dynamic infra
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAWSAmi(APP_ID, workflowPhase, true, true, BASIC);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PROVISION_INFRASTRUCTURE, PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP,
            PhaseStepType.AMI_DEPLOY_AUTOSCALING_GROUP, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForAWSLambda() {
    Service lambdaService =
        Service.builder().deploymentType(DeploymentType.AWS_LAMBDA).appId(APP_ID).uuid(SERVICE_ID).build();

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    // mocks
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(lambdaService);

    workflowServiceHelper.generateNewWorkflowPhaseStepsForAWSLambda(APP_ID, workflowPhase);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.PREPARE_STEPS, PhaseStepType.DEPLOY_AWS_LAMBDA, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = {IVAN, ANIL})
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForAzureVMSS() {
    Service lambdaService =
        Service.builder().deploymentType(DeploymentType.AZURE_VMSS).appId(APP_ID).uuid(SERVICE_ID).build();
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().infrastructure(AzureVMSSInfra.builder().build()).build();
    InfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();
    WorkflowPhase workflowPhase = aWorkflowPhase()
                                      .serviceId(SERVICE_ID)
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .build();

    // mocks
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(lambdaService);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infrastructureDefinition);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infrastructureMapping);

    // basic deployment test
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureVMSS(APP_ID, ACCOUNT_ID, workflowPhase, BASIC, true);
    List<PhaseStepType> phaseStepTypesBasic =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypesBasic).containsExactly(AZURE_VMSS_SETUP, AZURE_VMSS_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    // canary deployment test
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureVMSS(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CANARY, true);
    List<PhaseStepType> phaseStepTypesCanary =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypesCanary).containsExactly(AZURE_VMSS_SETUP, AZURE_VMSS_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    // blue green deployment test
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureVMSS(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.BLUE_GREEN, true);
    List<PhaseStepType> phaseStepTypesBlueGreen =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypesBlueGreen)
        .containsExactly(AZURE_VMSS_SETUP, AZURE_VMSS_DEPLOY, VERIFY_SERVICE, AZURE_VMSS_SWITCH_ROUTES, WRAP_UP);

    // unsupported deployment type test
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureVMSS(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.BUILD, true))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureVMSS(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.MULTI_SERVICE, true))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureVMSS(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.ROLLING, true))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureVMSS(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CUSTOM, true))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForAzureWebApp() {
    Service service =
        Service.builder().deploymentType(DeploymentType.AZURE_WEBAPP).appId(APP_ID).uuid(SERVICE_ID).build();
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().infrastructure(AzureVMSSInfra.builder().build()).build();
    InfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();
    WorkflowPhase workflowPhase = aWorkflowPhase()
                                      .serviceId(SERVICE_ID)
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .build();

    // mocks
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infrastructureDefinition);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infrastructureMapping);
    when(mockFeatureFlagService.isEnabled(FeatureName.AZURE_WEBAPP, ACCOUNT_ID)).thenReturn(true);

    // canary deployment test
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CANARY, false, true);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(
            AZURE_WEBAPP_SLOT_SETUP, VERIFY_SERVICE, AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT, AZURE_WEBAPP_SLOT_SWAP, WRAP_UP);
    workflowPhase.getPhaseSteps().clear();

    // blue green deployment test
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.BLUE_GREEN, false, true);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(AZURE_WEBAPP_SLOT_SETUP, VERIFY_SERVICE, AZURE_WEBAPP_SLOT_SWAP, WRAP_UP);
    workflowPhase.getPhaseSteps().clear();

    // basic deployment test
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.BASIC, false, true);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes).containsExactly(AZURE_WEBAPP_SLOT_SETUP, VERIFY_SERVICE, WRAP_UP);
    workflowPhase.getPhaseSteps().clear();

    // dynamic provisioner test
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CANARY, true, true);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PROVISION_INFRASTRUCTURE, AZURE_WEBAPP_SLOT_SETUP, VERIFY_SERVICE,
            AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT, AZURE_WEBAPP_SLOT_SWAP, WRAP_UP);
    workflowPhase.getPhaseSteps().clear();

    // webapp non-container tests
    when(mockFeatureFlagService.isEnabled(FeatureName.AZURE_WEBAPP, ACCOUNT_ID)).thenReturn(true);

    Service warService = Service.builder().artifactType(ArtifactType.WAR).uuid(SERVICE_ID).build();
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(warService);

    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CANARY, true, true);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PROVISION_INFRASTRUCTURE, AZURE_WEBAPP_SLOT_SETUP, VERIFY_SERVICE,
            AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT, AZURE_WEBAPP_SLOT_SWAP, WRAP_UP);
    workflowPhase.getPhaseSteps().clear();

    // unsupported deployment type test
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.MULTI_SERVICE, true, false))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.BUILD, true, false))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.MULTI_SERVICE, true, false))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.ROLLING, true, false))
        .isInstanceOf(InvalidRequestException.class);

    // feature flag test
    when(mockFeatureFlagService.isEnabled(FeatureName.AZURE_WEBAPP, ACCOUNT_ID)).thenReturn(false);
    assertThatThrownBy(()
                           -> workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
                               APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CANARY, true, false))
        .isInstanceOf(InvalidRequestException.class);

    // webapp-non container FF
    when(mockFeatureFlagService.isEnabled(FeatureName.AZURE_WEBAPP, ACCOUNT_ID)).thenReturn(true);

    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(warService);
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CANARY, true, false);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PROVISION_INFRASTRUCTURE, AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT, AZURE_WEBAPP_SLOT_SWAP, WRAP_UP);
    workflowPhase.getPhaseSteps().clear();

    Service nugetService = Service.builder().artifactType(ArtifactType.NUGET).uuid(SERVICE_ID).build();
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(nugetService);
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CANARY, true, false);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PROVISION_INFRASTRUCTURE, AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT, AZURE_WEBAPP_SLOT_SWAP, WRAP_UP);
    workflowPhase.getPhaseSteps().clear();

    Service zipService = Service.builder().artifactType(ArtifactType.ZIP).uuid(SERVICE_ID).build();
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(zipService);
    workflowServiceHelper.generateNewWorkflowPhaseStepsForAzureWebApp(
        APP_ID, ACCOUNT_ID, workflowPhase, OrchestrationWorkflowType.CANARY, true, false);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PROVISION_INFRASTRUCTURE, AZURE_WEBAPP_SLOT_TRAFFIC_SHIFT, AZURE_WEBAPP_SLOT_SWAP, WRAP_UP);
    workflowPhase.getPhaseSteps().clear();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForAWSCodeDeploy() {
    Service codeDeployService =
        Service.builder().deploymentType(DeploymentType.AWS_CODEDEPLOY).uuid(SERVICE_ID).build();

    AmazonS3ArtifactStream artifactStream = AmazonS3ArtifactStream.builder().serviceId(SERVICE_ID).build();

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    // mocks
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(codeDeployService);
    when(artifactStreamService.fetchArtifactStreamsForService(APP_ID, SERVICE_ID))
        .thenReturn(ImmutableList.of(artifactStream));

    workflowServiceHelper.generateNewWorkflowPhaseStepsForAWSCodeDeploy(APP_ID, workflowPhase);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.PREPARE_STEPS, PhaseStepType.DEPLOY_AWSCODEDEPLOY, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForECSBlueGreenRoute53() {
    Service ecsService = Service.builder().uuid(SERVICE_ID).appId(APP_ID).deploymentType(ECS).build();
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    // mock
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(ecsService);

    workflowServiceHelper.generateNewWorkflowPhaseStepsForECSBlueGreenRoute53(APP_ID, workflowPhase, true, false);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.CONTAINER_SETUP, PhaseStepType.CONTAINER_DEPLOY, VERIFY_SERVICE,
            PhaseStepType.ECS_UPDATE_ROUTE_53_DNS_WEIGHT, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForPCFBlueGreen() {
    Service pcfService = Service.builder().uuid(SERVICE_ID).appId(APP_ID).deploymentType(DeploymentType.PCF).build();
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    // mock
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(pcfService);

    workflowPhase.setDaemonSet(true);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () -> workflowServiceHelper.generateNewWorkflowPhaseStepsForPCFBlueGreen(APP_ID, workflowPhase, true));
    workflowPhase.setDaemonSet(false);

    workflowPhase.setStatefulSet(true);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            () -> workflowServiceHelper.generateNewWorkflowPhaseStepsForPCFBlueGreen(APP_ID, workflowPhase, true));
    workflowPhase.setStatefulSet(false);

    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForPCFBlueGreen(APP_ID, workflowPhase, true);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(
            PhaseStepType.PCF_SETUP, PhaseStepType.PCF_RESIZE, VERIFY_SERVICE, PhaseStepType.PCF_SWICH_ROUTES, WRAP_UP);

    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForPCFBlueGreen(APP_ID, workflowPhase, false);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.PCF_RESIZE, VERIFY_SERVICE, PhaseStepType.PCF_SWICH_ROUTES, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForPCF() {
    Service pcfService = Service.builder().uuid(SERVICE_ID).appId(APP_ID).deploymentType(DeploymentType.PCF).build();
    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    // mock
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(pcfService);

    workflowServiceHelper.generateNewWorkflowPhaseStepsForPCF(APP_ID, workflowPhase, true);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.PCF_SETUP, PhaseStepType.PCF_RESIZE, VERIFY_SERVICE, WRAP_UP);

    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForPCF(APP_ID, workflowPhase, false);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes).containsExactly(PhaseStepType.PCF_RESIZE, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForHelm() {
    Service helmService = Service.builder().uuid(SERVICE_ID).appId(APP_ID).deploymentType(HELM).build();

    WorkflowPhase workflowPhase = aWorkflowPhase().serviceId(SERVICE_ID).build();

    // mock
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(helmService);

    workflowServiceHelper.generateNewWorkflowPhaseStepsForHelm(APP_ID, workflowPhase);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes).containsExactly(PhaseStepType.HELM_DEPLOY, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForKubernetes() {
    Service k8sService = Service.builder().appId(APP_ID).uuid(SERVICE_ID).deploymentType(KUBERNETES).build();

    InfrastructureDefinition gcpK8sInfra = InfrastructureDefinition.builder()
                                               .uuid(INFRA_DEFINITION_ID)
                                               .appId(APP_ID)
                                               .envId(ENV_ID)
                                               .cloudProviderType(GCP)
                                               .deploymentType(KUBERNETES)
                                               .infrastructure(GoogleKubernetesEngine.builder()
                                                                   .namespace("namespace")
                                                                   .clusterName("clusterName")
                                                                   .cloudProviderId(SETTING_ID)
                                                                   .build())
                                               .build();

    WorkflowPhase workflowPhase = aWorkflowPhase()
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .serviceId(SERVICE_ID)
                                      .build();

    // mock
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(k8sService);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(gcpK8sInfra);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(gcpK8sInfra.getInfraMapping());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    // feature flag off
    workflowServiceHelper.generateNewWorkflowPhaseStepsForKubernetes(
        APP_ID, workflowPhase, true, OrchestrationWorkflowType.CANARY);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.CONTAINER_SETUP, PhaseStepType.CONTAINER_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    // feature flag on
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForKubernetes(
        APP_ID, workflowPhase, true, OrchestrationWorkflowType.CANARY);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.CONTAINER_SETUP, PhaseStepType.CONTAINER_DEPLOY, VERIFY_SERVICE, WRAP_UP);

    // ClusterName = RUNTIME
    ((GoogleKubernetesEngine) gcpK8sInfra.getInfrastructure()).setClusterName(WorkflowServiceHelper.RUNTIME);
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForKubernetes(
        APP_ID, workflowPhase, true, OrchestrationWorkflowType.CANARY);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(
            CLUSTER_SETUP, PhaseStepType.CONTAINER_SETUP, PhaseStepType.CONTAINER_DEPLOY, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForSSH() {
    InfrastructureDefinition sshDefinition = InfrastructureDefinition.builder()
                                                 .uuid(INFRA_DEFINITION_ID)
                                                 .appId(APP_ID)
                                                 .envId(ENV_ID)
                                                 .cloudProviderType(PHYSICAL_DATA_CENTER)
                                                 .deploymentType(SSH)
                                                 .infrastructure(PhysicalInfra.builder().loadBalancerId("lb").build())
                                                 .build();

    Service sshService = Service.builder().appId(APP_ID).uuid(SERVICE_ID).deploymentType(SSH).build();

    WorkflowPhase workflowPhase = aWorkflowPhase()
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .serviceId(SERVICE_ID)
                                      .build();

    // mocks
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(sshService);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(sshDefinition);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(sshDefinition.getInfraMapping());
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    // feature flag off
    workflowServiceHelper.generateNewWorkflowPhaseStepsForSSH(APP_ID, workflowPhase, OrchestrationWorkflowType.CANARY);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.INFRASTRUCTURE_NODE, PhaseStepType.DISABLE_SERVICE, DEPLOY_SERVICE,
            ENABLE_SERVICE, VERIFY_SERVICE, WRAP_UP);

    // feature flag on
    workflowPhase.getPhaseSteps().clear();
    workflowServiceHelper.generateNewWorkflowPhaseStepsForSSH(APP_ID, workflowPhase, OrchestrationWorkflowType.CANARY);
    phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes)
        .containsExactly(PhaseStepType.INFRASTRUCTURE_NODE, PhaseStepType.DISABLE_SERVICE, DEPLOY_SERVICE,
            ENABLE_SERVICE, VERIFY_SERVICE, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewWorkflowPhaseStepsForArtifactCollection() {
    WorkflowPhase workflowPhase = aWorkflowPhase().build();

    workflowServiceHelper.generateNewWorkflowPhaseStepsForArtifactCollection(workflowPhase);
    List<PhaseStepType> phaseStepTypes =
        workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList());
    assertThat(phaseStepTypes).containsExactly(PhaseStepType.PREPARE_STEPS, PhaseStepType.COLLECT_ARTIFACT, WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testIsK8sV2Service() {
    Service k8sService = Service.builder().isK8sV2(true).build();

    // mock
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(k8sService);

    assertThat(workflowServiceHelper.isK8sV2Service(APP_ID, SERVICE_ID)).isTrue();
    k8sService.setK8sV2(false);

    assertThat(workflowServiceHelper.isK8sV2Service(APP_ID, SERVICE_ID)).isFalse();
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCleanupEmptyPhaseStepStrategies() {
    // null phase step.
    WorkflowServiceHelper.cleanupPhaseStepStrategies(null);
    // empty step skip strategies.
    WorkflowServiceHelper.cleanupPhaseStepStrategies(aPhaseStep(PRE_DEPLOYMENT).build());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCleanupFailureStrategies() {
    FailureStrategy failureStrategy1 = FailureStrategy.builder().build();
    PhaseStep phaseStep = aPhaseStep(PRE_DEPLOYMENT).withFailureStrategies(singletonList(failureStrategy1)).build();

    WorkflowServiceHelper.cleanupPhaseStepStrategies(phaseStep);
    assertThat(phaseStep.getFailureStrategies()).isNullOrEmpty();

    FailureStrategy failureStrategy2 =
        FailureStrategy.builder().failureTypes(singletonList(FailureType.APPLICATION_ERROR)).build();
    phaseStep.setFailureStrategies(asList(failureStrategy1, failureStrategy2));
    WorkflowServiceHelper.cleanupPhaseStepStrategies(phaseStep);
    assertThat(phaseStep.getFailureStrategies()).isNotEmpty();
    assertThat(phaseStep.getFailureStrategies().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testCleanupFailureStrategiesInCaseOfInvalidStepNames() {
    List<String> validSteps = Arrays.asList("validStep1", "validStep2");
    Set<String> validStepsSet = new HashSet<>(validSteps);
    List<String> invalidSteps = Arrays.asList("invalidStep1", "invalidStep2");
    List<String> bothValidAndInvalidSteps = new ArrayList<>();
    bothValidAndInvalidSteps.addAll(validSteps);
    bothValidAndInvalidSteps.addAll(invalidSteps);

    // FailureStrategy with no specific steps should not be modified.
    {
      FailureStrategy failureStrategy =
          FailureStrategy.builder().failureTypes(singletonList(FailureType.APPLICATION_ERROR)).build();
      List<FailureStrategy> before = Arrays.asList(failureStrategy);
      List<FailureStrategy> after = WorkflowServiceHelper.cleanupFailureStrategies(before, validStepsSet);
      assertThat(before).isEqualTo(after);
    }

    // For a FailureStrategy, if all specific steps are VALID, it should not be modified.
    {
      FailureStrategy failureStrategy = FailureStrategy.builder()
                                            .failureTypes(singletonList(FailureType.APPLICATION_ERROR))
                                            .specificSteps(validSteps)
                                            .build();
      List<FailureStrategy> before = Arrays.asList(failureStrategy);
      List<FailureStrategy> after = WorkflowServiceHelper.cleanupFailureStrategies(before, validStepsSet);
      assertThat(before).isEqualTo(after);
    }

    // For a FailureStrategy, if a subset of specific steps are INVALID, if should be cleaned up to contain only
    // valid steps.
    {
      FailureStrategy failureStrategy = FailureStrategy.builder()
                                            .failureTypes(singletonList(FailureType.APPLICATION_ERROR))
                                            .specificSteps(new ArrayList<>(bothValidAndInvalidSteps))
                                            .build();
      List<FailureStrategy> before = Arrays.asList(failureStrategy);
      List<FailureStrategy> after = WorkflowServiceHelper.cleanupFailureStrategies(before, validStepsSet);
      assertThat(before.size()).isEqualTo(after.size());
      assertThat(after.get(0).getSpecificSteps()).isEqualTo(validSteps);
    }
    // For a FailureStrategy, if all specific steps are INVALID, it should be removed.
    {
      FailureStrategy failureStrategy = FailureStrategy.builder()
                                            .failureTypes(singletonList(FailureType.APPLICATION_ERROR))
                                            .specificSteps(invalidSteps)
                                            .build();
      List<FailureStrategy> failureStrategies = Arrays.asList(failureStrategy);
      List<FailureStrategy> after = WorkflowServiceHelper.cleanupFailureStrategies(failureStrategies, validStepsSet);
      assertThat(after).isEqualTo(Collections.emptyList());
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCleanupStepSkipStrategies() {
    StepSkipStrategy strategy1 = new StepSkipStrategy(SPECIFIC_STEPS, singletonList("id1"), "true");
    StepSkipStrategy strategy2 = new StepSkipStrategy(SPECIFIC_STEPS, singletonList("id2"), "true");
    StepSkipStrategy strategy3 = new StepSkipStrategy(SPECIFIC_STEPS, asList("id1", "id2"), "true");
    PhaseStep phaseStep = aPhaseStep(PRE_DEPLOYMENT).withStepSkipStrategies(singletonList(strategy1)).build();

    WorkflowServiceHelper.cleanupPhaseStepStrategies(phaseStep);
    assertThat(phaseStep.getStepSkipStrategies()).isNullOrEmpty();

    StepSkipStrategy strategy4 = new StepSkipStrategy(ALL_STEPS, null, "true");
    phaseStep.setStepSkipStrategies(singletonList(strategy4));
    WorkflowServiceHelper.cleanupPhaseStepStrategies(phaseStep);
    assertThat(phaseStep.getStepSkipStrategies()).isNotEmpty();

    phaseStep.setSteps(asList(prepareGraphNode(2), prepareGraphNode(3)));
    phaseStep.setStepSkipStrategies(singletonList(strategy1));
    WorkflowServiceHelper.cleanupPhaseStepStrategies(phaseStep);
    assertThat(phaseStep.getStepSkipStrategies()).isNullOrEmpty();

    phaseStep.setStepSkipStrategies(singletonList(strategy2));
    WorkflowServiceHelper.cleanupPhaseStepStrategies(phaseStep);
    assertThat(phaseStep.getStepSkipStrategies()).isNotEmpty();

    phaseStep.setStepSkipStrategies(singletonList(strategy3));
    WorkflowServiceHelper.cleanupPhaseStepStrategies(phaseStep);
    assertThat(phaseStep.getStepSkipStrategies()).isNotEmpty();
    assertThat(phaseStep.getStepSkipStrategies().size()).isEqualTo(1);

    StepSkipStrategy finalStrategy = phaseStep.getStepSkipStrategies().get(0);
    assertThat(finalStrategy.getScope()).isEqualTo(SPECIFIC_STEPS);
    assertThat(finalStrategy.getStepIds()).containsExactly("id2");
  }

  private GraphNode prepareGraphNode(int idx) {
    return GraphNode.builder().id("id" + idx).build();
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCleanupEmptyPhaseStrategies() {
    // null phase.
    WorkflowServiceHelper.cleanupPhaseStrategies(null);
    // empty step skip strategies.
    WorkflowServiceHelper.cleanupPhaseStrategies(aWorkflowPhase().build());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCleanupPhaseStrategies() {
    PhaseStep phaseStep =
        aPhaseStep(PRE_DEPLOYMENT)
            .addStep(prepareGraphNode(2))
            .addStep(prepareGraphNode(3))
            .withStepSkipStrategies(singletonList(new StepSkipStrategy(SPECIFIC_STEPS, asList("id1", "id2"), "true")))
            .withFailureStrategies(asList(FailureStrategy.builder().build(),
                FailureStrategy.builder().failureTypes(singletonList(FailureType.APPLICATION_ERROR)).build()))
            .build();
    WorkflowPhase phase = aWorkflowPhase().phaseSteps(singletonList(phaseStep)).build();
    WorkflowServiceHelper.cleanupPhaseStrategies(phase);
    assertThat(phaseStep.getFailureStrategies()).isNotEmpty();
    assertThat(phaseStep.getFailureStrategies().size()).isEqualTo(1);
    assertThat(phaseStep.getStepSkipStrategies()).isNotEmpty();

    StepSkipStrategy finalStrategy = phaseStep.getStepSkipStrategies().get(0);
    assertThat(finalStrategy.getScope()).isEqualTo(SPECIFIC_STEPS);
    assertThat(finalStrategy.getStepIds()).containsExactly("id2");
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCleanupEmptyWorkflowStrategies() {
    // null phase.
    WorkflowServiceHelper.cleanupWorkflowStrategies(null);
    // empty step skip strategies.
    WorkflowServiceHelper.cleanupWorkflowStrategies(aCanaryOrchestrationWorkflow().build());
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCleanupWorkflowStrategies() {
    PhaseStep phaseStep =
        aPhaseStep(PRE_DEPLOYMENT)
            .addStep(prepareGraphNode(2))
            .addStep(prepareGraphNode(3))
            .withStepSkipStrategies(singletonList(new StepSkipStrategy(SPECIFIC_STEPS, asList("id1", "id2"), "true")))
            .withFailureStrategies(asList(FailureStrategy.builder().build(),
                FailureStrategy.builder().failureTypes(singletonList(FailureType.APPLICATION_ERROR)).build()))
            .build();
    WorkflowPhase phase = aWorkflowPhase().phaseSteps(singletonList(phaseStep)).build();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        aCanaryOrchestrationWorkflow()
            .addWorkflowPhase(phase)
            .withFailureStrategies(asList(FailureStrategy.builder().build(),
                FailureStrategy.builder().failureTypes(singletonList(FailureType.APPLICATION_ERROR)).build()))
            .build();
    WorkflowServiceHelper.cleanupWorkflowStrategies(orchestrationWorkflow);
    assertThat(orchestrationWorkflow.getFailureStrategies()).isNotEmpty();
    assertThat(orchestrationWorkflow.getFailureStrategies().size()).isEqualTo(1);
    assertThat(phaseStep.getFailureStrategies()).isNotEmpty();
    assertThat(phaseStep.getFailureStrategies().size()).isEqualTo(1);
    assertThat(phaseStep.getStepSkipStrategies()).isNotEmpty();

    StepSkipStrategy finalStrategy = phaseStep.getStepSkipStrategies().get(0);
    assertThat(finalStrategy.getScope()).isEqualTo(SPECIFIC_STEPS);
    assertThat(finalStrategy.getStepIds()).containsExactly("id2");
  }

  private void verifyPhase(WorkflowPhase workflowPhase, List<String> expectedNames, int stepCount) {
    List<PhaseStep> phaseStepList = workflowPhase.getPhaseSteps();
    assertThat(phaseStepList).isNotNull();
    assertThat(phaseStepList).hasSize(stepCount);

    for (int index = 0; index < expectedNames.size(); index++) {
      if (EmptyPredicate.isNotEmpty(expectedNames.get(index))) {
        PhaseStep phaseStep = phaseStepList.get(index);
        assertThat(phaseStep.getSteps()).isNotNull();
        assertThat(phaseStep.getSteps()).hasSize(1);
        assertThat(phaseStep.getSteps().get(0).getType()).isEqualTo(expectedNames.get(index));
      }
    }
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateNewPhaseStepsForCustomDeployment() {
    WorkflowPhase workflowPhase = aWorkflowPhase().deploymentType(CUSTOM).build();
    workflowServiceHelper.generateNewWorkflowPhaseSteps(APP_ID, workflowPhase, false, BASIC, null);

    assertThat(workflowPhase.getPhaseSteps()).hasSize(3);
    assertThat(workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toSet()))
        .containsExactly(PhaseStepType.CUSTOM_DEPLOYMENT_PHASE_STEP);
    assertThat(workflowPhase.getPhaseSteps().stream().map(PhaseStep::getName).collect(Collectors.toList()))
        .containsExactly(DEPLOY, WorkflowServiceHelper.VERIFY_SERVICE, WorkflowServiceHelper.WRAP_UP);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGenerateRollBackPhaseStepsForCustomDeployment() {
    final WorkflowPhase workflowPhase = workflowServiceHelper.generateRollbackWorkflowPhase(
        APP_ID, aWorkflowPhase().deploymentType(CUSTOM).build(), false, BASIC, null);

    assertThat(workflowPhase.getPhaseSteps()).hasSize(1);
    assertThat(workflowPhase.getPhaseSteps().stream().map(PhaseStep::getPhaseStepType).collect(Collectors.toList()))
        .containsExactly(PhaseStepType.CUSTOM_DEPLOYMENT_PHASE_STEP);
    assertThat(workflowPhase.getPhaseSteps().stream().map(PhaseStep::getName).collect(Collectors.toList()))
        .containsExactly(ROLLBACK_SERVICE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetCategory() {
    assertThat(workflowServiceHelper.getCategory(null, null)).isEqualTo(GENERAL);
    assertThat(workflowServiceHelper.getCategory(null, "")).isEqualTo(GENERAL);

    doReturn(Service.builder().isK8sV2(true).build()).when(serviceResourceService).get(APP_ID, SERVICE_ID, false);
    assertThat(workflowServiceHelper.getCategory(APP_ID, SERVICE_ID)).isEqualTo(K8S_V2);

    doReturn(Service.builder().build()).when(serviceResourceService).get(APP_ID, SERVICE_ID, false);
    assertThat(workflowServiceHelper.getCategory(APP_ID, SERVICE_ID)).isEqualTo(GENERAL);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCustomDeploymentWrapUpPhaseHasFetchInstanceScriptStep() {
    WorkflowPhase workflowPhase = aWorkflowPhase().deploymentType(CUSTOM).build();
    workflowServiceHelper.generateNewWorkflowPhaseSteps(APP_ID, workflowPhase, false, BASIC, null);

    assertThat(workflowPhase.getPhaseSteps().get(0).getSteps().size()).isNotEqualTo(0);
    assertThat(workflowPhase.getPhaseSteps().get(0).getSteps().get(0).getType())
        .isEqualTo(CUSTOM_DEPLOYMENT_FETCH_INSTANCES.name());
    assertThat(workflowPhase.getPhaseSteps().get(0).getSteps().get(0).getProperties().size()).isEqualTo(1);
    assertThat(workflowPhase.getPhaseSteps().get(0).getSteps().get(0).getProperties())
        .isEqualTo(ImmutableMap.of(InstanceFetchStateKeys.stateTimeoutInMinutes, 1));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testResetNodeSelection() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("specificHosts", Boolean.TRUE);
    properties.put("hostNames", "my-host");
    WorkflowPhase workflowPhase =
        aWorkflowPhase()
            .phaseSteps(
                asList(aPhaseStep(INFRASTRUCTURE_NODE)
                           .addStep(GraphNode.builder().type(DC_NODE_SELECT.name()).properties(properties).build())
                           .build()))
            .build();

    workflowServiceHelper.resetNodeSelection(workflowPhase);
    assertThat(workflowPhase.getPhaseSteps().get(0).getSteps().size()).isEqualTo(1);
    assertThat(workflowPhase.getPhaseSteps().get(0).getSteps().get(0).getType()).isEqualTo(DC_NODE_SELECT.name());
    assertThat(workflowPhase.getPhaseSteps().get(0).getSteps().get(0).getProperties()).isNotNull();
    Map<String, Object> expectedProperties = new HashMap<>();
    expectedProperties.put("specificHosts", Boolean.FALSE);
    expectedProperties.put("instanceCount", 1);
    expectedProperties.put("instanceUnitType", InstanceUnitType.COUNT);
    assertThat(workflowPhase.getPhaseSteps().get(0).getSteps().get(0).getProperties()).isEqualTo(expectedProperties);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testNegativeWaitIntervalWhenOrchestrationWorkflowIsNull() {
    Workflow workflow = WorkflowServiceTestHelper.constructBasicWorkflowWithPhaseSteps();

    workflow.setOrchestrationWorkflow(null);
    assertThat(workflow.getOrchestrationWorkflow()).isEqualTo(null);
    workflowServiceHelper.validateWaitInterval(workflow);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testNegativeWaitIntervalWhenOrchestrationWorkflowPhaseIdMapIsNull() {
    Workflow workflow = WorkflowServiceTestHelper.constructBasicWorkflowWithPhaseSteps();

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    orchestrationWorkflow.setWorkflowPhaseIdMap(null);
    assertThat(((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getWorkflowPhaseIdMap())
        .isEqualTo(null);
    workflowServiceHelper.validateWaitInterval(workflow);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testNegativeWaitIntervalWhenWaitIntervalIsNegative() {
    Workflow workflow = WorkflowServiceTestHelper.constructBasicWorkflowWithPhaseSteps();
    BasicOrchestrationWorkflow basicOrchestrationWorkflow =
        (BasicOrchestrationWorkflow) workflow.getOrchestrationWorkflow();

    Map<String, WorkflowPhase> workflowPhaseIdMap = basicOrchestrationWorkflow.getWorkflowPhaseIdMap();
    List<String> workflowPhaseIds = basicOrchestrationWorkflow.getWorkflowPhaseIds();
    assertThat(workflowPhaseIdMap).isNotEmpty().size().isGreaterThan(0);
    assertThat(workflowPhaseIds).isNotEmpty().size().isGreaterThan(0);
    WorkflowPhase workflowPhase = workflowPhaseIdMap.get(workflowPhaseIds.get(0));

    assertThat(workflowPhase.getPhaseSteps()).isNotEmpty().size().isGreaterThan(0);
    PhaseStep phaseStep = workflowPhase.getPhaseSteps().get(0);
    assertThat(phaseStep).isNotNull();

    phaseStep.setWaitInterval(-1);
    assertThatThrownBy(() -> workflowServiceHelper.validateWaitInterval(workflow))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Negative values for wait interval not allowed.");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testNegativeWaitIntervalWhenWaitIntervalIsGreaterThanAllowedValue() {
    Workflow workflow = WorkflowServiceTestHelper.constructBasicWorkflowWithPhaseSteps();
    BasicOrchestrationWorkflow basicOrchestrationWorkflow =
        (BasicOrchestrationWorkflow) workflow.getOrchestrationWorkflow();

    Map<String, WorkflowPhase> workflowPhaseIdMap = basicOrchestrationWorkflow.getWorkflowPhaseIdMap();
    List<String> workflowPhaseIds = basicOrchestrationWorkflow.getWorkflowPhaseIds();
    assertThat(workflowPhaseIdMap).isNotEmpty().size().isGreaterThan(0);
    assertThat(workflowPhaseIds).isNotEmpty().size().isGreaterThan(0);
    WorkflowPhase workflowPhase = workflowPhaseIdMap.get(workflowPhaseIds.get(0));

    assertThat(workflowPhase.getPhaseSteps()).isNotEmpty().size().isGreaterThan(0);
    PhaseStep phaseStep = workflowPhase.getPhaseSteps().get(0);
    assertThat(phaseStep).isNotNull();

    // Wait interval is 1 day 1 sec
    phaseStep.setWaitInterval(24 * 60 * 60 + 1);
    assertThatThrownBy(() -> workflowServiceHelper.validateWaitInterval(workflow))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Wait Interval cannot be more than one day.");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldUnsetInfraDefinitionsDetailsWhenEnvChanged() {
    Workflow workflow = WorkflowServiceTestHelper.constructCanaryWorkflowWithTwoPhases();
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflowServiceHelper.propagateWorkflowDataToPhases(
            workflow.getOrchestrationWorkflow(), Collections.emptyList(), APP_ID, null, null, true, false, false);

    assertThat(orchestrationWorkflow.getWorkflowPhases()).hasSize(2);
    orchestrationWorkflow.getWorkflowPhases().forEach(phase -> {
      assertThat(phase.getComputeProviderId()).isNull();
      assertThat(phase.getInfraDefinitionId()).isNull();
      assertThat(phase.getInfraDefinitionName()).isNull();
    });
  }
}
