/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.resume;

import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.contracts.resume.ChainDetails;
import io.harness.pms.contracts.resume.NodeResumeEvent;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.DummyExecutionStrategy;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.EngineObtainmentHelper;
import io.harness.pms.sdk.core.execution.ExecutableProcessor;
import io.harness.pms.sdk.core.execution.ExecutableProcessorFactory;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeResumeEventHandlerTest extends PmsSdkCoreTestBase {
  @Mock ExecutableProcessorFactory executableProcessorFactory;
  @Mock EngineObtainmentHelper engineObtainmentHelper;
  @InjectMocks NodeResumeEventHandler nodeResumeEventHandler;
  @Mock SdkNodeExecutionService sdkNodeExecutionService;
  @Mock KryoSerializer mockedKryoSerializer;
  @Inject KryoSerializer kryoSerializer1;

  private NodeResumeEvent nodeResumeEvent;
  private Ambiance ambiance;

  @Before
  public void setup() {
    when(executableProcessorFactory.obtainProcessor(ExecutionMode.APPROVAL))
        .thenReturn(new ExecutableProcessor(new DummyExecutionStrategy()));

    ambiance = AmbianceTestUtils.buildAmbiance();
    Map<String, ByteString> responseDataMap = new HashMap<>();
    StepResponseNotifyData stepResponseNotifyData = StepResponseNotifyData.builder().build();
    ByteString byteString =
        ByteString.copyFrom(kryoSerializer1.asDeflatedBytes(StepResponseNotifyData.builder().build()));

    responseDataMap.put("response", byteString);
    when(mockedKryoSerializer.asInflatedObject(byteString.toByteArray())).thenReturn(stepResponseNotifyData);

    nodeResumeEvent = NodeResumeEvent.newBuilder()
                          .setExecutionMode(ExecutionMode.APPROVAL)
                          .setAmbiance(ambiance)
                          .setAsyncError(false)
                          .putAllResponse(responseDataMap)
                          .build();
  }

  public void verifyInteractions() {
    Mockito.verifyNoMoreInteractions(executableProcessorFactory);
    Mockito.verifyNoMoreInteractions(sdkNodeExecutionService);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMetricPrefix() {
    assertThat(nodeResumeEventHandler.getMetricPrefix(nodeResumeEvent)).isEqualTo("resume_event");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractLogProperties() {
    Map<String, String> metricsMap = nodeResumeEventHandler.extraLogProperties(nodeResumeEvent);
    assertThat(metricsMap.isEmpty()).isFalse();
    assertThat(metricsMap.size()).isEqualTo(1);
    assertThat(metricsMap.get("eventType")).isEqualTo(NodeExecutionEventType.RESUME.name());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractAmbiance() {
    assertThat(nodeResumeEventHandler.extractAmbiance(nodeResumeEvent)).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithContext() {
    nodeResumeEventHandler.handleEventWithContext(nodeResumeEvent);
    Mockito.verify(executableProcessorFactory).obtainProcessor(ExecutionMode.APPROVAL);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCalculateIsEndOnApproval() {
    nodeResumeEvent =
        nodeResumeEvent.toBuilder().setChainDetails(ChainDetails.newBuilder().setIsEnd(true).build()).build();
    assertThat(nodeResumeEventHandler.calculateIsEnd(nodeResumeEvent, new HashMap<>())).isTrue();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCalculateIsEndOnChildCHain() {
    nodeResumeEvent = nodeResumeEvent.toBuilder()
                          .setExecutionMode(ExecutionMode.CHILD_CHAIN)
                          .setChainDetails(ChainDetails.newBuilder().setIsEnd(false).build())
                          .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", StepResponseNotifyData.builder().build());
    assertThat(nodeResumeEventHandler.calculateIsEnd(nodeResumeEvent, responseDataMap)).isFalse();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithContextWithAsyncError() {
    Map<String, ByteString> responseDataMap = new HashMap<>();
    DummyErrorResponseData errorResponseData = DummyErrorResponseData.builder().build();
    ByteString byteString = ByteString.copyFrom(kryoSerializer1.asDeflatedBytes(errorResponseData));

    responseDataMap.put("response", byteString);
    when(mockedKryoSerializer.asInflatedObject(byteString.toByteArray())).thenReturn(errorResponseData);

    nodeResumeEvent = nodeResumeEvent.toBuilder().setAsyncError(true).putAllResponse(responseDataMap).build();
    nodeResumeEventHandler.handleEventWithContext(nodeResumeEvent);
    Mockito.verify(executableProcessorFactory).obtainProcessor(ExecutionMode.APPROVAL);
    Mockito.verify(sdkNodeExecutionService).handleStepResponse(eq(ambiance), any(StepResponseProto.class));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testHandleEventWithContextForChildChain() {
    StepResponseNotifyData notifyData = StepResponseNotifyData.builder().status(Status.RUNNING).build();
    Map<String, ByteString> responseDataMap = new HashMap<>();
    ByteString byteString = ByteString.copyFrom(kryoSerializer1.asDeflatedBytes(notifyData));
    responseDataMap.put("response", byteString);
    ByteString passThroughData =
        ByteString.copyFrom(RecastOrchestrationUtils.toBytes(TestPassThroughData.builder().data("SOME_DATA").build()));

    ByteString stepParameters =
        ByteString.copyFrom(RecastOrchestrationUtils.toBytes(TestStepParameters.builder().data("SOME_DATA").build()));
    NodeResumeEvent childChainResumeEvent =
        NodeResumeEvent.newBuilder()
            .setExecutionMode(ExecutionMode.CHILD_CHAIN)
            .setAmbiance(ambiance)
            .setAsyncError(false)
            .putAllResponse(responseDataMap)
            .setStepParameters(stepParameters)
            .setChainDetails(ChainDetails.newBuilder().setIsEnd(true).setPassThroughData(passThroughData).build())
            .build();

    when(mockedKryoSerializer.asInflatedObject(byteString.toByteArray())).thenReturn(notifyData);
    ExecutableProcessor executableProcessor = mock(ExecutableProcessor.class);
    when(executableProcessorFactory.obtainProcessor(ExecutionMode.CHILD_CHAIN)).thenReturn(executableProcessor);
    nodeResumeEventHandler.handleEventWithContext(childChainResumeEvent);
    ArgumentCaptor<ResumePackage> resumePackageArgumentCaptor = ArgumentCaptor.forClass(ResumePackage.class);
    verify(executableProcessor).handleResume(resumePackageArgumentCaptor.capture());

    ResumePackage resumePackage = resumePackageArgumentCaptor.getValue();

    assertThat(resumePackage.getChainDetails()).isNotNull();
    io.harness.pms.sdk.core.execution.ChainDetails chainDetails = resumePackage.getChainDetails();
    assertThat(chainDetails.isShouldEnd()).isTrue();
    assertThat(chainDetails.getPassThroughData()).isNull();
    assertThat(chainDetails.getPassThroughBytes()).isNotNull();
    assertThat(chainDetails.getPassThroughBytes()).isEqualTo(passThroughData);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testHandleEventWithContextForChildChainEmptyPassThrough() {
    StepResponseNotifyData notifyData = StepResponseNotifyData.builder().status(Status.RUNNING).build();
    Map<String, ByteString> responseDataMap = new HashMap<>();
    ByteString byteString = ByteString.copyFrom(kryoSerializer1.asDeflatedBytes(notifyData));
    responseDataMap.put("response", byteString);

    ByteString stepParameters =
        ByteString.copyFrom(RecastOrchestrationUtils.toBytes(TestStepParameters.builder().data("SOME_DATA").build()));
    NodeResumeEvent childChainResumeEvent = NodeResumeEvent.newBuilder()
                                                .setExecutionMode(ExecutionMode.CHILD_CHAIN)
                                                .setAmbiance(ambiance)
                                                .setAsyncError(false)
                                                .putAllResponse(responseDataMap)
                                                .setStepParameters(stepParameters)
                                                .setChainDetails(ChainDetails.newBuilder().setIsEnd(true).build())
                                                .build();

    when(mockedKryoSerializer.asInflatedObject(byteString.toByteArray())).thenReturn(notifyData);
    ExecutableProcessor executableProcessor = mock(ExecutableProcessor.class);
    when(executableProcessorFactory.obtainProcessor(ExecutionMode.CHILD_CHAIN)).thenReturn(executableProcessor);
    nodeResumeEventHandler.handleEventWithContext(childChainResumeEvent);
    ArgumentCaptor<ResumePackage> resumePackageArgumentCaptor = ArgumentCaptor.forClass(ResumePackage.class);
    verify(executableProcessor).handleResume(resumePackageArgumentCaptor.capture());

    ResumePackage resumePackage = resumePackageArgumentCaptor.getValue();

    assertThat(resumePackage.getChainDetails()).isNotNull();
    io.harness.pms.sdk.core.execution.ChainDetails chainDetails = resumePackage.getChainDetails();
    assertThat(chainDetails.isShouldEnd()).isTrue();
    assertThat(chainDetails.getPassThroughData()).isNull();
    assertThat(chainDetails.getPassThroughBytes()).isNull();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testHandleEventWithContextForTaskChain() {
    StepResponseNotifyData notifyData = StepResponseNotifyData.builder().status(Status.RUNNING).build();
    Map<String, ByteString> responseDataMap = new HashMap<>();
    ByteString byteString = ByteString.copyFrom(kryoSerializer1.asDeflatedBytes(notifyData));
    responseDataMap.put("response", byteString);
    ByteString passThroughData =
        ByteString.copyFrom(RecastOrchestrationUtils.toBytes(TestPassThroughData.builder().data("SOME_DATA").build()));

    ByteString stepParameters =
        ByteString.copyFrom(RecastOrchestrationUtils.toBytes(TestStepParameters.builder().data("SOME_DATA").build()));
    NodeResumeEvent childChainResumeEvent =
        NodeResumeEvent.newBuilder()
            .setExecutionMode(ExecutionMode.TASK_CHAIN)
            .setAmbiance(ambiance)
            .setAsyncError(false)
            .putAllResponse(responseDataMap)
            .setStepParameters(stepParameters)
            .setChainDetails(ChainDetails.newBuilder().setIsEnd(true).setPassThroughData(passThroughData).build())
            .build();

    when(mockedKryoSerializer.asInflatedObject(byteString.toByteArray())).thenReturn(notifyData);
    ExecutableProcessor executableProcessor = mock(ExecutableProcessor.class);
    when(executableProcessorFactory.obtainProcessor(ExecutionMode.TASK_CHAIN)).thenReturn(executableProcessor);
    nodeResumeEventHandler.handleEventWithContext(childChainResumeEvent);
    ArgumentCaptor<ResumePackage> resumePackageArgumentCaptor = ArgumentCaptor.forClass(ResumePackage.class);
    verify(executableProcessor).handleResume(resumePackageArgumentCaptor.capture());

    ResumePackage resumePackage = resumePackageArgumentCaptor.getValue();

    assertThat(resumePackage.getChainDetails()).isNotNull();
    io.harness.pms.sdk.core.execution.ChainDetails chainDetails = resumePackage.getChainDetails();
    assertThat(chainDetails.isShouldEnd()).isTrue();
    assertThat(chainDetails.getPassThroughData()).isNotNull();
    assertThat(chainDetails.getPassThroughData()).isInstanceOf(TestPassThroughData.class);
    assertThat(((TestPassThroughData) chainDetails.getPassThroughData()).getData()).isEqualTo("SOME_DATA");
    assertThat(chainDetails.getPassThroughBytes()).isNull();
  }

  @Value
  @Builder
  private static class TestPassThroughData implements PassThroughData {
    String data;
  }

  @Value
  @Builder
  @RecasterAlias("io.harness.pms.sdk.core.execution.events.node.resume.TestStepParameters")
  private static class TestStepParameters implements StepParameters {
    String data;
  }
}
