/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.resume;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceKeys;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.api.HostElement;
import software.wings.api.WorkflowElement;
import software.wings.api.artifact.ServiceArtifactElement;
import software.wings.api.artifact.ServiceArtifactVariableElement;
import software.wings.expression.SweepingOutputData;
import software.wings.service.impl.SweepingOutputServiceImpl;
import software.wings.service.intfc.FeatureFlagService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ResumeStateUtilsTest extends WingsBaseTest {
  private static final String SWEEPING_OUTPUT_CONTENT = "SWEEPING_OUTPUT_CONTENT";

  @Inject @InjectMocks private ResumeStateUtils resumeStateUtils;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject KryoSerializer kryoSerializer;
  @Inject private HPersistence persistence;

  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService featureFlagService;

  private final String appId = generateUuid();
  private final String workflowExecutionUuid = generateUuid();
  private final String pipelineExecutionUuid = generateUuid();
  private final String phaseElementId = generateUuid();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldPrepareExecutionResponse() {
    String prevStateExecutionId = "id";
    String errMsg = "errMsg";
    LinkedList<ContextElement> contextElements = new LinkedList<>(asList(HostElement.builder().build(),
        ServiceArtifactElement.builder().build(), ServiceArtifactVariableElement.builder().build()));
    StateExecutionInstance stateExecutionInstance = mock(StateExecutionInstance.class);
    when(stateExecutionInstance.getStatus()).thenReturn(ExecutionStatus.SUCCESS);
    when(stateExecutionInstance.getContextElements()).thenReturn(contextElements);
    StateExecutionData stateExecutionData = aStateExecutionData().withErrorMsg(errMsg).build();
    when(stateExecutionInstance.fetchStateExecutionData()).thenReturn(stateExecutionData);
    when(stateExecutionService.getStateExecutionData(eq(APP_ID), eq(prevStateExecutionId)))
        .thenReturn(stateExecutionInstance);

    ExecutionContext context = mock(ExecutionContextImpl.class);
    when(context.getAppId()).thenReturn(APP_ID);
    ExecutionResponse executionResponse = resumeStateUtils.prepareExecutionResponse(context, prevStateExecutionId);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getErrorMessage()).isEqualTo(errMsg);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(stateExecutionData);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(stateExecutionData);
    assertThat(executionResponse.getContextElements()).isNotNull();
    assertThat(executionResponse.getContextElements().size()).isEqualTo(2);
    assertThat(executionResponse.getContextElements()
                   .stream()
                   .map(ContextElement::getElementType)
                   .collect(Collectors.toList()))
        .containsExactly(ContextElementType.ARTIFACT, ContextElementType.ARTIFACT_VARIABLE);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchPipelineExecutionId() {
    ExecutionContext context = mock(ExecutionContextImpl.class);
    when(context.getContextElement(eq(ContextElementType.STANDARD))).thenReturn(null);
    assertThat(resumeStateUtils.fetchPipelineExecutionId(context)).isNull();

    WorkflowStandardParams std = mock(WorkflowStandardParams.class);
    when(context.getContextElement(eq(ContextElementType.STANDARD))).thenReturn(std);
    when(std.getWorkflowElement()).thenReturn(null);
    assertThat(resumeStateUtils.fetchPipelineExecutionId(context)).isNull();

    String pipelineExecutionId = "id";
    when(std.getWorkflowElement())
        .thenReturn(WorkflowElement.builder().pipelineDeploymentUuid(pipelineExecutionId).build());
    assertThat(resumeStateUtils.fetchPipelineExecutionId(context)).isEqualTo(pipelineExecutionId);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCopyPipelineStageOutputs() {
    // Setup some existing sweeping outputs.
    String stateExecutionInstanceIdEnvState = generateUuid();
    String stateExecutionInstanceIdApprovalState = generateUuid();
    String phaseExecutionId = workflowExecutionUuid + phaseElementId + "Phase 1";

    // This should not be copied as it's at the workflow scope.
    sweepingOutputService.save(
        SweepingOutputServiceImpl
            .prepareSweepingOutputBuilder(appId, pipelineExecutionUuid, workflowExecutionUuid, phaseExecutionId,
                stateExecutionInstanceIdEnvState, SweepingOutputInstance.Scope.WORKFLOW)
            .name("wf1")
            .output(kryoSerializer.asBytes(SWEEPING_OUTPUT_CONTENT))
            .value(SweepingOutputData.builder().text(SWEEPING_OUTPUT_CONTENT).build())
            .build());

    // All the others should be copied.
    sweepingOutputService.save(
        SweepingOutputServiceImpl
            .prepareSweepingOutputBuilder(appId, pipelineExecutionUuid, workflowExecutionUuid, phaseExecutionId,
                stateExecutionInstanceIdEnvState, SweepingOutputInstance.Scope.PIPELINE)
            .name("pl1")
            .output(kryoSerializer.asBytes(SWEEPING_OUTPUT_CONTENT))
            .value(SweepingOutputData.builder().text(SWEEPING_OUTPUT_CONTENT).build())
            .build());
    sweepingOutputService.save(
        SweepingOutputServiceImpl
            .prepareSweepingOutputBuilder(appId, pipelineExecutionUuid, workflowExecutionUuid, phaseExecutionId,
                stateExecutionInstanceIdEnvState, SweepingOutputInstance.Scope.PIPELINE)
            .name("pl2")
            .output(kryoSerializer.asBytes(SWEEPING_OUTPUT_CONTENT))
            .value(SweepingOutputData.builder().text(SWEEPING_OUTPUT_CONTENT).build())
            .build());
    sweepingOutputService.save(SweepingOutputServiceImpl
                                   .prepareSweepingOutputBuilder(appId, pipelineExecutionUuid, null, null,
                                       stateExecutionInstanceIdApprovalState, SweepingOutputInstance.Scope.PIPELINE)
                                   .name("pl3")
                                   .output(kryoSerializer.asBytes(SWEEPING_OUTPUT_CONTENT))
                                   .value(SweepingOutputData.builder().text(SWEEPING_OUTPUT_CONTENT).build())
                                   .build());

    String newPipelineExecutionUuid = generateUuid();
    String newStateExecutionInstanceIdEnvState = generateUuid();
    String newStateExecutionInstanceIdApprovalState = generateUuid();

    // This should be a no-op as the pipeline execution ids are the same.
    resumeStateUtils.copyPipelineStageOutputs(appId, newPipelineExecutionUuid, stateExecutionInstanceIdEnvState,
        singletonList(workflowExecutionUuid), newPipelineExecutionUuid, newStateExecutionInstanceIdEnvState);
    List<SweepingOutputInstance> instances = listPipelineInstances(newPipelineExecutionUuid);
    assertThat(instances).isNullOrEmpty();

    // This should be a no-op as the old pipeline execution id has no outputs.
    resumeStateUtils.copyPipelineStageOutputs(appId, pipelineExecutionUuid + "random", stateExecutionInstanceIdEnvState,
        singletonList(workflowExecutionUuid), newPipelineExecutionUuid, newStateExecutionInstanceIdEnvState);
    instances = listPipelineInstances(newPipelineExecutionUuid);
    assertThat(instances).isNullOrEmpty();

    // Copy outputs.
    resumeStateUtils.copyPipelineStageOutputs(appId, pipelineExecutionUuid, stateExecutionInstanceIdEnvState,
        singletonList(workflowExecutionUuid), newPipelineExecutionUuid, newStateExecutionInstanceIdEnvState);
    resumeStateUtils.copyPipelineStageOutputs(appId, pipelineExecutionUuid, stateExecutionInstanceIdApprovalState, null,
        newPipelineExecutionUuid, newStateExecutionInstanceIdApprovalState);

    instances = listPipelineInstances(newPipelineExecutionUuid);
    assertThat(instances).isNotNull();
    assertThat(instances.size()).isEqualTo(3);

    SweepingOutputInstance instance = getInstanceByName(instances, "wf1");
    assertThat(instance).isNull();

    instance = getInstanceByName(instances, "pl1");
    assertThat(instance).isNotNull();
    assertThat(instance.getWorkflowExecutionIds()).containsExactly(newPipelineExecutionUuid);
    assertThat(instance.getStateExecutionId()).isEqualTo(newStateExecutionInstanceIdEnvState);
    verifyOutputAndValue(instance);

    instance = getInstanceByName(instances, "pl2");
    assertThat(instance).isNotNull();
    assertThat(instance.getWorkflowExecutionIds()).containsExactly(newPipelineExecutionUuid);
    assertThat(instance.getStateExecutionId()).isEqualTo(newStateExecutionInstanceIdEnvState);
    verifyOutputAndValue(instance);

    instance = getInstanceByName(instances, "pl3");
    assertThat(instance).isNotNull();
    assertThat(instance.getWorkflowExecutionIds()).containsExactly(newPipelineExecutionUuid);
    assertThat(instance.getStateExecutionId()).isEqualTo(newStateExecutionInstanceIdApprovalState);
    verifyOutputAndValue(instance);
  }

  private List<SweepingOutputInstance> listPipelineInstances(String pipelineExecutionUuid) {
    return persistence.createQuery(SweepingOutputInstance.class)
        .filter(SweepingOutputInstanceKeys.appId, appId)
        .filter(SweepingOutputInstanceKeys.pipelineExecutionId, pipelineExecutionUuid)
        .asList();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_mergeWorkflowElementVariables() {
    ExecutionContext context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams std1 = mock(WorkflowStandardParams.class);
    WorkflowStandardParams std2 = mock(WorkflowStandardParams.class);
    Map<String, Object> variableMap1 = new HashMap<>();
    variableMap1.put("var1", "value1");
    variableMap1.put("varX", "valueX");
    WorkflowElement element1 = WorkflowElement.builder().variables(variableMap1).build();
    Map<String, Object> variableMap2 = new HashMap<>();
    variableMap2.put("var1", "newValue1");
    variableMap2.put("var2", "newValue2");
    variableMap2.put("var3", "newValue3");
    variableMap2.put("var4", "newValue4");
    WorkflowElement element2 = WorkflowElement.builder().variables(variableMap2).build();
    when(std1.getWorkflowElement()).thenReturn(element1);
    when(std2.getWorkflowElement()).thenReturn(element2);
    when(context.getContextElement(eq(ContextElementType.STANDARD))).thenReturn(std1);
    resumeStateUtils.mergeWorkflowElementVariables(context, std2);
    assertThat(std1.getWorkflowElement().getVariables().size()).isEqualTo(5);
    assertThat(std1.getWorkflowElement().getVariables().get("var1")).isEqualTo("newValue1");
    assertThat(std1.getWorkflowElement().getVariables().get("var2")).isEqualTo("newValue2");
    assertThat(std1.getWorkflowElement().getVariables().get("var3")).isEqualTo("newValue3");
    assertThat(std1.getWorkflowElement().getVariables().get("var4")).isEqualTo("newValue4");
    assertThat(std1.getWorkflowElement().getVariables().get("varX")).isEqualTo("valueX");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_mergeWorkflowElementVariablesNull() {
    ExecutionContext context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams std1 = mock(WorkflowStandardParams.class);
    WorkflowStandardParams std2 = mock(WorkflowStandardParams.class);
    WorkflowElement element1 = WorkflowElement.builder().build();
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var1", "newValue1");
    variableMap.put("var2", "newValue2");
    variableMap.put("var3", "newValue3");
    variableMap.put("var4", "newValue4");
    WorkflowElement element2 = WorkflowElement.builder().variables(variableMap).build();
    when(std1.getWorkflowElement()).thenReturn(element1);
    when(std2.getWorkflowElement()).thenReturn(element2);
    when(context.getContextElement(eq(ContextElementType.STANDARD))).thenReturn(std1);
    resumeStateUtils.mergeWorkflowElementVariables(context, std2);
    assertThat(std1.getWorkflowElement().getVariables().size()).isEqualTo(4);
    assertThat(std1.getWorkflowElement().getVariables().get("var1")).isEqualTo("newValue1");
    assertThat(std1.getWorkflowElement().getVariables().get("var2")).isEqualTo("newValue2");
    assertThat(std1.getWorkflowElement().getVariables().get("var3")).isEqualTo("newValue3");
    assertThat(std1.getWorkflowElement().getVariables().get("var4")).isEqualTo("newValue4");
  }

  private SweepingOutputInstance getInstanceByName(List<SweepingOutputInstance> instances, String name) {
    return instances.stream().filter(instance -> instance.getName().equals(name)).findFirst().orElse(null);
  }

  private void verifyOutputAndValue(SweepingOutputInstance instance) {
    assertThat(instance.getOutput()).isEqualTo(kryoSerializer.asBytes(SWEEPING_OUTPUT_CONTENT));
    assertThat(((SweepingOutputData) instance.getValue()).getText()).isEqualTo(SWEEPING_OUTPUT_CONTENT);
  }
}
