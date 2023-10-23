/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.container.execution.ContainerRunStepHelper;
import io.harness.steps.container.execution.RunContainerStep;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RunContainerStepTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String DELEGATE_ID = "delegateId";
  @Mock private PmsFeatureFlagHelper mockFeatureFlagHelper;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CIDelegateTaskExecutor taskExecutor;
  @Mock private ContainerRunStepHelper containerRunStepHelper;
  @InjectMocks private RunContainerStep runContainerStep;

  private AutoCloseable mocks;
  private final Ambiance testAmbiance = testAmbiance();

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.SOURABH)
  @Category(UnitTests.class)
  public void testDelegateSelectorForLightEngineAndParkedTask() {
    ContainerStepInfo containerStepSpec = ContainerStepInfo.infoBuilder().build();
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml("foo");
    taskSelectorYaml.setOrigin("step");
    containerStepSpec.setDelegateSelectors(ParameterField.createValueField(List.of(taskSelectorYaml)));
    containerStepSpec.setInfrastructure(
        ContainerK8sInfra.builder()
            .spec(ContainerInfraYamlSpec.builder()
                      .os(ParameterField.<OSType>builder().value(OSType.Linux).build())
                      .connectorRef(ParameterField.<String>builder().value("ConnectorDisconnectHandler").build())
                      .build())
            .build());

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorConfig(
                KubernetesClusterConfigDTO.builder().delegateSelectors(new HashSet<>(List.of(DELEGATE_ID))).build())
            .build();

    doReturn(true).when(mockFeatureFlagHelper).isEnabled(any(), eq(FeatureName.CD_CONTAINER_STEP_DELEGATE_SELECTOR));
    doReturn("delegate-task-1").when(taskExecutor).queueParkedDelegateTask(any(), anyLong(), any(), anyList());
    doReturn(connectorDetails).when(connectorUtils).getConnectorDetails(any(), any());
    doReturn(TaskData.builder().build())
        .when(containerRunStepHelper)
        .getRunStepTask(any(), any(), any(), any(), anyLong(), any());
    doReturn("delegate-task-2").when(taskExecutor).queueTask(any(), any(), any(), anyList());

    runContainerStep.executeAsyncAfterRbac(testAmbiance,
        StepElementParameters.builder()
            .timeout(ParameterField.<String>builder().value("1d").expression(false).build())
            .spec(containerStepSpec)
            .build(),
        null);
    ArgumentCaptor<List<TaskSelector>> taskSelectorCaptor = ArgumentCaptor.forClass(List.class);
    verify(taskExecutor, times(1)).queueTask(any(), any(), any(), taskSelectorCaptor.capture());
    List<TaskSelector> taskSelectors = taskSelectorCaptor.getValue();
    assertThat(taskSelectors).hasSize(2);
    assertThat(taskSelectors.get(0).getSelector()).isEqualTo(DELEGATE_ID);
    assertThat(taskSelectors.get(1)).isEqualTo(TaskSelectorYaml.toTaskSelector(taskSelectorYaml));
  }

  private Ambiance testAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", ACCOUNT_ID);
    setupAbstractions.put("projectIdentifier", "projectId");
    setupAbstractions.put("orgIdentifier", "orgId");
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setRunSequence(1).setPipelineIdentifier("pipeline").build();
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(setupAbstractions)
        .setMetadata(executionMetadata)
        .build();
  }
}
