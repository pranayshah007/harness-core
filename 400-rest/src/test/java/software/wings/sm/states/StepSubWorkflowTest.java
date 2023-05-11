/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.PHASE_STEP;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceElement;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceInstanceIdsParam;
import software.wings.api.ServiceInstanceIdsParam.ServiceInstanceIdsParamBuilder;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.FailureStrategy;
import software.wings.beans.InfraMappingSweepingOutput;
import software.wings.beans.PhaseStepType;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ElementNotifyResponseData;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionStatusData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

/**
 * Created by rishi on 2/25/17.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class StepSubWorkflowTest extends WingsBaseTest {
  private static final String STATE_NAME = "state";
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Inject private KryoSerializer kryoSerializer;

  private List<ElementExecutionSummary> elementExecutionSummaries = new ArrayList<>();

  private WorkflowStandardParams workflowStandardParams = aWorkflowStandardParams().withAppId(APP_ID).build();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldExecutePreDeployStep() {
    PhaseElement phaseElement =
        PhaseElement.builder()
            .uuid(generateUuid())
            .serviceElement(ServiceElement.builder().uuid(generateUuid()).name("service1").build())
            .build();
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .displayName(STATE_NAME)
                                                        .addContextElement(workflowStandardParams)
                                                        .addContextElement(phaseElement)
                                                        .addStateExecutionData(new PhaseStepExecutionData())
                                                        .build();
    ExecutionContextImpl context = spy(new ExecutionContextImpl(stateExecutionInstance));
    Reflect.on(context).set("sweepingOutputService", sweepingOutputService);
    doReturn(SweepingOutputInstance.builder()
                 .value(InfraMappingSweepingOutput.builder().infraMappingId(INFRA_MAPPING_ID).build())
                 .build())
        .when(sweepingOutputService)
        .find(any(SweepingOutputInquiry.class));
    PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
    phaseStepSubWorkflow.setPhaseStepType(PhaseStepType.PRE_DEPLOYMENT);
    List<FailureStrategy> failureStrategies = new ArrayList<>();
    phaseStepSubWorkflow.setFailureStrategies(failureStrategies);
    phaseStepSubWorkflow.setStepsInParallel(true);
    phaseStepSubWorkflow.setDefaultFailureStrategy(true);
    Reflect.on(phaseStepSubWorkflow).set("featureFlagService", featureFlagService);
    Reflect.on(phaseStepSubWorkflow).set("kryoSerializer", kryoSerializer);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    doReturn(ACCOUNT_ID).when(context).getAccountId();

    ExecutionResponse response = phaseStepSubWorkflow.execute(context);

    assertThat(response).isNotNull().hasFieldOrProperty("stateExecutionData");
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .hasFieldOrPropertyWithValue("failureStrategies", failureStrategies)
        .hasFieldOrPropertyWithValue("defaultFailureStrategy", true)
        .hasFieldOrPropertyWithValue("stepsInParallel", true);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldValidateContainerDeploy() {
    ServiceElement serviceElement = ServiceElement.builder().uuid(generateUuid()).name("service1").build();
    PhaseElement phaseElement = PhaseElement.builder().uuid(generateUuid()).serviceElement(serviceElement).build();
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .displayName(STATE_NAME)
                                                        .addContextElement(workflowStandardParams)
                                                        .addContextElement(phaseElement)
                                                        .addContextElement(ContainerServiceElement.builder()
                                                                               .uuid(serviceElement.getUuid())
                                                                               .resizeStrategy(RESIZE_NEW_FIRST)
                                                                               .build())
                                                        .addStateExecutionData(new PhaseStepExecutionData())
                                                        .build();
    ExecutionContextImpl context = spy(new ExecutionContextImpl(stateExecutionInstance));
    Reflect.on(context).set("sweepingOutputService", sweepingOutputService);
    doReturn(SweepingOutputInstance.builder()
                 .value(InfraMappingSweepingOutput.builder().infraMappingId(INFRA_MAPPING_ID).build())
                 .build())
        .when(sweepingOutputService)
        .find(any(SweepingOutputInquiry.class));
    PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
    phaseStepSubWorkflow.setPhaseStepType(PhaseStepType.CONTAINER_DEPLOY);
    Reflect.on(phaseStepSubWorkflow).set("featureFlagService", featureFlagService);
    Reflect.on(phaseStepSubWorkflow).set("kryoSerializer", kryoSerializer);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    doReturn(ACCOUNT_ID).when(context).getAccountId();

    ExecutionResponse response = phaseStepSubWorkflow.execute(context);

    assertThat(response).isNotNull().hasFieldOrProperty("stateExecutionData");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowNullPhaseType() {
    try {
      ExecutionContextImpl context = new ExecutionContextImpl(null);
      PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
      ExecutionResponse response = phaseStepSubWorkflow.execute(context);
      assertThat(response).isNotNull();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage("null phaseStepType");
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldHandleAsyncPreDeploy() {
    ExecutionContextImpl context = new ExecutionContextImpl(aStateExecutionInstance().uuid(generateUuid()).build());
    PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
    phaseStepSubWorkflow.setPhaseStepType(PhaseStepType.PRE_DEPLOYMENT);
    Map<String, ResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put("", ExecutionStatusData.builder().executionStatus(ExecutionStatus.SUCCESS).build());
    ExecutionResponse response = phaseStepSubWorkflow.handleAsyncResponse(context, notifyResponse);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldHandleAsyncProvisionNode() {
    when(workflowExecutionService.getElementsSummary(anyString(), anyString(), anyString()))
        .thenReturn(elementExecutionSummaries);

    List<String> instanceIds = Lists.newArrayList(generateUuid(), generateUuid());
    String serviceId = generateUuid();
    ServiceInstanceIdsParam serviceInstanceIdsParam = ServiceInstanceIdsParamBuilder.aServiceInstanceIdsParam()
                                                          .withInstanceIds(instanceIds)
                                                          .withServiceId(serviceId)
                                                          .build();

    ServiceElement serviceElement = ServiceElement.builder().uuid(serviceId).name("service1").build();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .uuid(generateUuid())
                                    .serviceElement(serviceElement)
                                    .deploymentType(DeploymentType.SSH.name())
                                    .build();
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .displayName(STATE_NAME)
                                                        .addContextElement(workflowStandardParams)
                                                        .addContextElement(phaseElement)
                                                        .addStateExecutionData(new PhaseStepExecutionData())
                                                        .build();
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance);
    PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
    phaseStepSubWorkflow.setPhaseStepType(PhaseStepType.INFRASTRUCTURE_NODE);
    Map<String, ResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put(
        "key", ElementNotifyResponseData.builder().contextElements(asList(serviceInstanceIdsParam)).build());
    Reflect.on(phaseStepSubWorkflow).set("workflowExecutionService", workflowExecutionService);

    ExecutionResponse response = phaseStepSubWorkflow.handleAsyncResponse(context, notifyResponse);
    assertThat(response).isNotNull().hasFieldOrProperty("stateExecutionData");
    assertThat(response.getStateExecutionData()).isNotNull().isExactlyInstanceOf(PhaseStepExecutionData.class);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldHandleAsyncEcsSetup() {
    when(workflowExecutionService.getElementsSummary(anyString(), anyString(), anyString()))
        .thenReturn(elementExecutionSummaries);

    String serviceId = generateUuid();
    ServiceElement serviceElement = ServiceElement.builder().uuid(serviceId).name("service1").build();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .uuid(generateUuid())
                                    .serviceElement(serviceElement)
                                    .deploymentType(DeploymentType.KUBERNETES.name())
                                    .build();

    ContainerServiceElement containerServiceElement =
        ContainerServiceElement.builder().uuid(serviceElement.getUuid()).resizeStrategy(RESIZE_NEW_FIRST).build();
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .displayName(STATE_NAME)
                                                        .addContextElement(workflowStandardParams)
                                                        .addContextElement(phaseElement)
                                                        .addStateExecutionData(new PhaseStepExecutionData())
                                                        .build();
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance);
    PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
    phaseStepSubWorkflow.setPhaseStepType(PhaseStepType.CONTAINER_SETUP);
    Map<String, ResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put(
        "key", ElementNotifyResponseData.builder().contextElements(asList(containerServiceElement)).build());
    Reflect.on(phaseStepSubWorkflow).set("workflowExecutionService", workflowExecutionService);

    ExecutionResponse response = phaseStepSubWorkflow.handleAsyncResponse(context, notifyResponse);
    assertThat(response).isNotNull().hasFieldOrProperty("stateExecutionData");
    assertThat(response.getContextElements()).isNotNull().hasSize(1);
    assertThat(response.getContextElements().get(0)).isNotNull().isEqualTo(containerServiceElement);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowInvalidEcsSetup() {
    when(workflowExecutionService.getElementsSummary(anyString(), anyString(), anyString()))
        .thenReturn(elementExecutionSummaries);

    String serviceId = generateUuid();
    ServiceElement serviceElement = ServiceElement.builder().uuid(serviceId).name("service1").build();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .uuid(generateUuid())
                                    .serviceElement(serviceElement)
                                    .deploymentType(DeploymentType.ECS.name())
                                    .build();

    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .displayName(STATE_NAME)
                                                        .addContextElement(workflowStandardParams)
                                                        .addContextElement(phaseElement)
                                                        .addStateExecutionData(new PhaseStepExecutionData())
                                                        .build();
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance);
    PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
    phaseStepSubWorkflow.setPhaseStepType(PhaseStepType.CONTAINER_SETUP);
    Map<String, ResponseData> notifyResponse = new HashMap<>();
    try {
      phaseStepSubWorkflow.handleAsyncResponse(context, notifyResponse);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage("Missing response");
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldHandleAsyncKubernetesSetup() {
    when(workflowExecutionService.getElementsSummary(anyString(), anyString(), anyString()))
        .thenReturn(elementExecutionSummaries);

    String serviceId = generateUuid();
    ServiceElement serviceElement = ServiceElement.builder().uuid(serviceId).name("service1").build();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .uuid(generateUuid())
                                    .serviceElement(serviceElement)
                                    .deploymentType(DeploymentType.KUBERNETES.name())
                                    .build();

    ContainerServiceElement containerServiceElement =
        ContainerServiceElement.builder().uuid(serviceElement.getUuid()).resizeStrategy(RESIZE_NEW_FIRST).build();
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .displayName(STATE_NAME)
                                                        .addContextElement(workflowStandardParams)
                                                        .addContextElement(phaseElement)
                                                        .addStateExecutionData(new PhaseStepExecutionData())
                                                        .build();
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance);
    PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
    phaseStepSubWorkflow.setPhaseStepType(PhaseStepType.CONTAINER_SETUP);
    Map<String, ResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put(
        "key", ElementNotifyResponseData.builder().contextElements(asList(containerServiceElement)).build());
    Reflect.on(phaseStepSubWorkflow).set("workflowExecutionService", workflowExecutionService);

    ExecutionResponse response = phaseStepSubWorkflow.handleAsyncResponse(context, notifyResponse);
    assertThat(response).isNotNull().hasFieldOrProperty("stateExecutionData");
    assertThat(response.getContextElements()).isNotNull().hasSize(1);
    assertThat(response.getContextElements().get(0)).isNotNull().isEqualTo(containerServiceElement);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowInvalidKubernetesSetup() {
    when(workflowExecutionService.getElementsSummary(anyString(), anyString(), anyString()))
        .thenReturn(elementExecutionSummaries);

    String serviceId = generateUuid();
    ServiceElement serviceElement = ServiceElement.builder().uuid(serviceId).name("service1").build();
    PhaseElement phaseElement = PhaseElement.builder()
                                    .uuid(generateUuid())
                                    .serviceElement(serviceElement)
                                    .deploymentType(DeploymentType.KUBERNETES.name())
                                    .build();

    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .displayName(STATE_NAME)
                                                        .addContextElement(workflowStandardParams)
                                                        .addContextElement(phaseElement)
                                                        .addStateExecutionData(new PhaseStepExecutionData())
                                                        .build();
    ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance);
    PhaseStepSubWorkflow phaseStepSubWorkflow = new PhaseStepSubWorkflow(PHASE_STEP);
    phaseStepSubWorkflow.setPhaseStepType(PhaseStepType.CONTAINER_SETUP);
    Map<String, ResponseData> notifyResponse = new HashMap<>();
    try {
      phaseStepSubWorkflow.handleAsyncResponse(context, notifyResponse);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage("Missing response");
    }
  }
}
