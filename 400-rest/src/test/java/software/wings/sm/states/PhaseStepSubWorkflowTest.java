/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.beans.PhaseStepType.AMI_AUTOSCALING_GROUP_SETUP;
import static software.wings.beans.PhaseStepType.CLUSTER_SETUP;
import static software.wings.beans.PhaseStepType.CONTAINER_SETUP;
import static software.wings.beans.PhaseStepType.DEPLOY_AWS_LAMBDA;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.AmiServiceSetupElement;
import software.wings.api.AmiServiceTrafficShiftAlbSetupElement;
import software.wings.api.AwsLambdaContextElement;
import software.wings.api.ClusterElement;
import software.wings.api.ContainerServiceElement;
import software.wings.api.DeploymentType;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceInstanceIdsParam;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.ElementNotifyResponseData;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.spotinst.SpotinstTrafficShiftAlbSetupElement;

import java.util.HashMap;
import java.util.Map;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PhaseStepSubWorkflowTest extends WingsBaseTest {
  private static final String INFRA_DEFINITION_ID = "INFRA_DEFINITION_ID";
  public static final String SERVICE_ID = "SERVICE_ID";
  public static final String APP_ID = "APP_ID";
  public static final String INFRA_MAPPING_ID = "INFRA_MAPPING_ID";
  private static final String PROVISIONER_ID = "PROVISIONER_ID";
  private static final String RANDOM = "RANDOM";

  @Mock private ExecutionContext executionContext;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;

  private PhaseStepSubWorkflow phaseStepSubWorkflow = spy(new PhaseStepSubWorkflow(RANDOM));

  @Before
  public void setUp() throws Exception {
    Reflect.on(phaseStepSubWorkflow).set("infrastructureDefinitionService", infrastructureDefinitionService);
    Reflect.on(phaseStepSubWorkflow).set("workflowExecutionService", workflowExecutionService);
    Reflect.on(phaseStepSubWorkflow).set("infrastructureMappingService", infrastructureMappingService);
    Reflect.on(phaseStepSubWorkflow).set("infrastructureProvisionerService", infrastructureProvisionerService);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldNotCallGetInfraMappingWhenAlreadyAvailable() {
    when(executionContext.fetchInfraMappingId()).thenReturn("INFRA_MAPPING_ID");

    phaseStepSubWorkflow.populateInfraMapping(executionContext);

    verify(infrastructureDefinitionService, never()).renderAndSaveInfraMapping(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldCreateInfraMappingWhenNonProvisioner() {
    doReturn(null).when(executionContext).fetchInfraMappingId();
    doReturn(APP_ID).when(executionContext).getAppId();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .build();
    doReturn(phaseElement).when(executionContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    InfrastructureDefinition infraDefinition = InfrastructureDefinition.builder().build();
    doReturn(infraDefinition).when(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);
    InfrastructureMapping infraMapping = GcpKubernetesInfrastructureMapping.builder().uuid(INFRA_MAPPING_ID).build();
    doReturn(infraMapping)
        .when(infrastructureDefinitionService)
        .renderAndSaveInfraMapping(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID, executionContext);
    doNothing()
        .when(phaseStepSubWorkflow)
        .updateInfraMappingDependencies(executionContext, phaseElement, APP_ID, infraMapping);

    phaseStepSubWorkflow.populateInfraMapping(executionContext);

    verify(infrastructureDefinitionService, times(1))
        .renderAndSaveInfraMapping(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID, executionContext);
    verify(phaseStepSubWorkflow, times(1)).updateInfraMappingDependencies(any(), any(), anyString(), any());

    // In case inframapping failed to be created.
    doReturn(null)
        .when(infrastructureDefinitionService)
        .renderAndSaveInfraMapping(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID, executionContext);
    phaseStepSubWorkflow.populateInfraMapping(executionContext);
    verify(infrastructureDefinitionService, times(2))
        .renderAndSaveInfraMapping(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID, executionContext);
    verify(phaseStepSubWorkflow, times(1)).updateInfraMappingDependencies(any(), any(), anyString(), any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldNotCreateInfraMappingWhenProvisionerOutputsNotAvailable() {
    doReturn(null).when(executionContext).fetchInfraMappingId();
    doReturn(APP_ID).when(executionContext).getAppId();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .build();
    doReturn(phaseElement).when(executionContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    InfrastructureDefinition infraDefinition = InfrastructureDefinition.builder().provisionerId(PROVISIONER_ID).build();
    doReturn(infraDefinition).when(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);
    InfrastructureProvisioner infraProvisioner = TerraformInfrastructureProvisioner.builder().build();
    doReturn(infraProvisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(null).when(executionContext).evaluateExpression(any());

    phaseStepSubWorkflow.populateInfraMapping(executionContext);

    verify(infrastructureDefinitionService, never())
        .renderAndSaveInfraMapping(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID, executionContext);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldCreateInfraMappingWhenProvisionerOutputsAvailable() {
    doReturn(null).when(executionContext).fetchInfraMappingId();
    doReturn(APP_ID).when(executionContext).getAppId();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .build();
    doReturn(phaseElement).when(executionContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    InfrastructureDefinition infraDefinition = InfrastructureDefinition.builder().provisionerId(PROVISIONER_ID).build();
    doReturn(infraDefinition).when(infrastructureDefinitionService).get(APP_ID, INFRA_DEFINITION_ID);
    InfrastructureProvisioner infraProvisioner = TerraformInfrastructureProvisioner.builder().build();
    doReturn(infraProvisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(RANDOM).when(executionContext).evaluateExpression(any());
    InfrastructureMapping infraMapping = GcpKubernetesInfrastructureMapping.builder().uuid(INFRA_MAPPING_ID).build();
    doReturn(infraMapping)
        .when(infrastructureDefinitionService)
        .renderAndSaveInfraMapping(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID, executionContext);
    doNothing()
        .when(phaseStepSubWorkflow)
        .updateInfraMappingDependencies(executionContext, phaseElement, APP_ID, infraMapping);

    phaseStepSubWorkflow.populateInfraMapping(executionContext);

    verify(infrastructureDefinitionService, times(1))
        .renderAndSaveInfraMapping(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID, executionContext);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldNotCreateInfraMappingForBuildWorkflow() {
    doReturn(null).when(executionContext).fetchInfraMappingId();
    doReturn(APP_ID).when(executionContext).getAppId();
    PhaseElement phaseElement = PhaseElement.builder().build();
    doReturn(phaseElement).when(executionContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);

    phaseStepSubWorkflow.populateInfraMapping(executionContext);

    verify(infrastructureDefinitionService, never())
        .renderAndSaveInfraMapping(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID, executionContext);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetSpotinstNotifiedContextElement() {
    PhaseStepSubWorkflow workflow = spy(new PhaseStepSubWorkflow("RANDOM_LOCAL"));
    ElementNotifyResponseData data =
        ElementNotifyResponseData.builder()
            .contextElements(singletonList(SpotinstTrafficShiftAlbSetupElement.builder().build()))
            .build();
    ContextElement element = workflow.getSpotinstNotifiedContextElement(data);
    assertThat(element instanceof SpotinstTrafficShiftAlbSetupElement).isTrue();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleElementNotifyResponseData() {
    when(workflowExecutionService.getPhaseStepExecutionSummary(anyString(), any(), anyString())).thenReturn(null);
    PhaseElement phaseElement = PhaseElement.builder()
                                    .infraDefinitionId(INFRA_DEFINITION_ID)
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .deploymentType(DeploymentType.KUBERNETES.name())
                                    .build();
    when(executionContext.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(phaseElement);
    when(executionContext.getStateExecutionData()).thenReturn(new PhaseStepExecutionData());

    Map<String, ResponseData> response = new HashMap<>();

    response.put("", getElementNotifyResponseData(null));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", K8S_PHASE_STEP);
    ExecutionResponse executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isEmpty();
    assertThat(executionResponse.getNotifyElements()).isEmpty();

    response.put("", getElementNotifyResponseData(InstanceElementListParam.builder().build()));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", K8S_PHASE_STEP);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isNotEmpty();
    assertThat(executionResponse.getNotifyElements()).isEmpty();

    response.put("", getElementNotifyResponseData(null));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", AMI_AUTOSCALING_GROUP_SETUP);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isEmpty();
    assertThat(executionResponse.getNotifyElements()).isEmpty();

    response.put("", getElementNotifyResponseData(AmiServiceSetupElement.builder().build()));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", AMI_AUTOSCALING_GROUP_SETUP);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isNotEmpty();
    assertThat(executionResponse.getContextElements().get(0)).isInstanceOf(AmiServiceSetupElement.class);
    assertThat(executionResponse.getNotifyElements().get(0)).isInstanceOf(AmiServiceSetupElement.class);

    response.put("", getElementNotifyResponseData(AmiServiceTrafficShiftAlbSetupElement.builder().build()));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", AMI_AUTOSCALING_GROUP_SETUP);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isNotEmpty();
    assertThat(executionResponse.getContextElements().get(0)).isInstanceOf(AmiServiceTrafficShiftAlbSetupElement.class);
    assertThat(executionResponse.getNotifyElements().get(0)).isInstanceOf(AmiServiceTrafficShiftAlbSetupElement.class);

    response.put("", getElementNotifyResponseData(null));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", CONTAINER_SETUP);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isEmpty();
    assertThat(executionResponse.getNotifyElements()).isEmpty();

    response.put("", getElementNotifyResponseData(ContainerServiceElement.builder().build()));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", CONTAINER_SETUP);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isNotEmpty();
    assertThat(executionResponse.getContextElements().get(0)).isInstanceOf(ContainerServiceElement.class);
    assertThat(executionResponse.getNotifyElements().get(0)).isInstanceOf(ContainerServiceElement.class);

    response.put("", getElementNotifyResponseData(null));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", CLUSTER_SETUP);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isEmpty();
    assertThat(executionResponse.getNotifyElements()).isEmpty();

    response.put("", getElementNotifyResponseData(ClusterElement.builder().build()));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", CLUSTER_SETUP);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isNotEmpty();
    assertThat(executionResponse.getContextElements().get(0)).isInstanceOf(ClusterElement.class);
    assertThat(executionResponse.getNotifyElements().get(0)).isInstanceOf(ClusterElement.class);

    phaseElement.setDeploymentType(DeploymentType.SSH.name());
    response.put("", getElementNotifyResponseData(null));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", INFRASTRUCTURE_NODE);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isEmpty();
    assertThat(executionResponse.getNotifyElements()).isEmpty();

    response.put("", getElementNotifyResponseData(new ServiceInstanceIdsParam()));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", INFRASTRUCTURE_NODE);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isNotEmpty();
    assertThat(executionResponse.getContextElements().get(0)).isInstanceOf(ServiceInstanceIdsParam.class);
    assertThat(executionResponse.getNotifyElements()).isEmpty();

    phaseElement.setDeploymentType(DeploymentType.AWS_LAMBDA.name());
    response.put("", getElementNotifyResponseData(null));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", DEPLOY_AWS_LAMBDA);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isEmpty();
    assertThat(executionResponse.getNotifyElements()).isEmpty();

    response.put("", getElementNotifyResponseData(AwsLambdaContextElement.builder().build()));
    Reflect.on(phaseStepSubWorkflow).set("phaseStepType", DEPLOY_AWS_LAMBDA);
    executionResponse = phaseStepSubWorkflow.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getContextElements()).isNotEmpty();
    assertThat(executionResponse.getContextElements().get(0)).isInstanceOf(AwsLambdaContextElement.class);
    assertThat(executionResponse.getNotifyElements()).isEmpty();
  }

  private ElementNotifyResponseData getElementNotifyResponseData(ContextElement contextElement) {
    return ElementNotifyResponseData.builder()
        .executionStatus(ExecutionStatus.SUCCESS)
        .contextElements(asList(contextElement))
        .build();
  }
}
