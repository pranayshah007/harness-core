/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils.execution;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.KUBERNETES_DIRECT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.container.execution.ContainerStepCleanupHelper;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.plugin.infrastructure.ContainerCleanupDetails;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.utils.PmsFeatureFlagService;

import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ContainerStepCleanupHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final List<String> CONTAINERS = List.of("container1", "container2");
  private static final String POD_NAME = "podName";
  private static final String NAMESPACE = "namespace";

  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private ConnectorUtils connectorUtils;

  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @InjectMocks private ContainerStepCleanupHelper containerStepCleanupHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ILogStreamingStepClient logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSendCleanupRequestWithContainerCleanupDetails() {
    ContainerStepInfra ContainerStepInfra = ContainerK8sInfra.builder()
                                                .type(KUBERNETES_DIRECT)
                                                .spec(ContainerInfraYamlSpec.builder()
                                                          .namespace(ParameterField.createValueField(NAMESPACE))
                                                          .connectorRef(ParameterField.createValueField("conRef"))
                                                          .build())
                                                .build();
    ContainerCleanupDetails containerCleanupDetails = ContainerCleanupDetails.builder()
                                                          .podName(POD_NAME)
                                                          .cleanUpContainerNames(CONTAINERS)
                                                          .infrastructure(ContainerStepInfra)
                                                          .build();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(containerCleanupDetails).build());
    when(pmsFeatureFlagService.isEnabled(anyString(), anyString())).thenReturn(true);
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(ConnectorDetails.builder().build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("taskId");

    containerStepCleanupHelper.sendCleanupRequest(getAmbiance());

    final ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));

    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    CIK8CleanupTaskParams taskParameters = (CIK8CleanupTaskParams) delegateTaskRequest.getTaskParameters();

    assertThat(taskParameters.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(taskParameters.getCleanupContainerNames()).isEqualTo(CONTAINERS);
    assertThat(taskParameters.getPodNameList()).isEqualTo(List.of(POD_NAME));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSendCleanupRequestWithPodCleanupDetails() {
    PodCleanupDetails podCleanupDetails =
        PodCleanupDetails.builder().podName(POD_NAME).cleanUpContainerNames(CONTAINERS).build();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(podCleanupDetails).build());
    containerStepCleanupHelper.sendCleanupRequest(getAmbiance());

    verify(delegateGrpcClientWrapper, times(0)).submitAsyncTaskV2(any(), any());
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
        .addLevels(Level.newBuilder().setIdentifier("Identifier").build())
        .build();
  }
}
