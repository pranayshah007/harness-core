/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANKIT_TIWARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.AsyncSdkProgressCallback;
import io.harness.pms.sdk.core.execution.AsyncSdkResumeCallback;
import io.harness.pms.sdk.core.execution.ChainDetails;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.supporter.async.TestAsyncChainStep;
import io.harness.pms.sdk.core.supporter.async.TestStepParameters;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.waiter.StringNotifyResponseData;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class AsyncChainStrategyTest extends PmsSdkCoreTestBase {
  @Mock private SdkNodeExecutionService sdkNodeExecutionService;
  @Mock private AsyncWaitEngine asyncWaitEngine;

  @Inject @InjectMocks private AsyncChainStrategy asyncChainStrategy;
  @Inject private StepRegistry stepRegistry;

  @Inject private StrategyHelper strategyHelper;

  private TestAsyncChainStep step;
  @Before
  public void setup() {
    step = new TestAsyncChainStep("Initialized");
    stepRegistry.register(TestAsyncChainStep.ASYNC_CHAIN_STEP_TYPE, step);
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void shouldTestStart() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions())
                            .setPlanId(generateUuid())
                            .addLevels(Level.newBuilder()
                                           .setSetupId(generateUuid())
                                           .setRuntimeId(generateUuid())
                                           .setStepType(TestAsyncChainStep.ASYNC_CHAIN_STEP_TYPE)
                                           .setIdentifier(generateUuid())
                                           .build())
                            .build();
    InvokerPackage invokerPackage = InvokerPackage.builder()
                                        .ambiance(ambiance)
                                        .executionMode(ExecutionMode.ASYNC_CHAIN)
                                        .passThroughData(null)
                                        .stepParameters(TestStepParameters.builder().param("TEST_PARAM").build())
                                        .build();

    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<ExecutableResponse> responseArgumentCaptor = ArgumentCaptor.forClass(ExecutableResponse.class);

    asyncChainStrategy.start(invokerPackage);
    Mockito.verify(sdkNodeExecutionService, Mockito.times(1))
        .addExecutableResponse(ambianceCaptor.capture(), responseArgumentCaptor.capture());

    ArgumentCaptor<AsyncSdkResumeCallback> notifyCallbackArgumentCaptor =
        ArgumentCaptor.forClass(AsyncSdkResumeCallback.class);
    ArgumentCaptor<AsyncSdkProgressCallback> progressCallbackArgumentCaptor =
        ArgumentCaptor.forClass(AsyncSdkProgressCallback.class);
    ArgumentCaptor<List<String>> correlationIdsCaptor = ArgumentCaptor.forClass(List.class);

    Mockito.verify(asyncWaitEngine, Mockito.times(1))
        .waitForAllOn(notifyCallbackArgumentCaptor.capture(), progressCallbackArgumentCaptor.capture(),
            correlationIdsCaptor.capture(), eq(TestAsyncChainStep.timeout));
    // Wait Engine Mock verify interactions
    AsyncSdkResumeCallback resumeCallback = notifyCallbackArgumentCaptor.getValue();
    assertThat(resumeCallback.getAmbianceBytes()).isEqualTo(ambiance.toByteArray());

    AsyncSdkProgressCallback progressCallback = progressCallbackArgumentCaptor.getValue();
    assertThat(progressCallback.getAmbianceBytes()).isEqualTo(ambiance.toByteArray());
    assertThat(progressCallback.getStepParameters())
        .isEqualTo(
            ByteString.copyFromUtf8(RecastOrchestrationUtils.toJson(invokerPackage.getStepParameters())).toByteArray());

    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);

    ExecutableResponse executableResponse = responseArgumentCaptor.getValue();
    assertThat(executableResponse.getResponseCase()).isEqualTo(ExecutableResponse.ResponseCase.ASYNCCHAIN);

    AsyncChainExecutableResponse asyncChainExecutableResponse = executableResponse.getAsyncChain();
    String callbackId = asyncChainExecutableResponse.getCallbackId();
    String corrId = correlationIdsCaptor.getValue().get(0);
    assertThat(callbackId).isEqualTo(corrId);
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void shouldTestResume() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions())
                            .setPlanId(generateUuid())
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder()
                                           .setSetupId(generateUuid())
                                           .setRuntimeId(generateUuid())
                                           .setStepType(TestAsyncChainStep.ASYNC_CHAIN_STEP_TYPE)
                                           .setIdentifier(generateUuid())
                                           .build())
                            .build();
    ResumePackage resumePackage = ResumePackage.builder()
                                      .ambiance(ambiance)
                                      .stepParameters(TestStepParameters.builder().param("TEST_PARAM").build())
                                      .responseDataMap(ImmutableMap.of(generateUuid(),
                                          StringNotifyResponseData.builder().data("someString").build()))
                                      .chainDetails(ChainDetails.builder().shouldEnd(true).build())
                                      .build();

    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<StepResponseProto> stepResponseCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    asyncChainStrategy.resume(resumePackage);
    Mockito.verify(sdkNodeExecutionService, Mockito.times(1))
        .handleStepResponse(ambianceCaptor.capture(), stepResponseCaptor.capture());

    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);
  }

  private Map<String, String> setupAbstractions() {
    return ImmutableMap.<String, String>builder()
        .put(SetupAbstractionKeys.accountId, generateUuid())
        .put(SetupAbstractionKeys.orgIdentifier, generateUuid())
        .put(SetupAbstractionKeys.projectIdentifier, generateUuid())
        .build();
  }
}
