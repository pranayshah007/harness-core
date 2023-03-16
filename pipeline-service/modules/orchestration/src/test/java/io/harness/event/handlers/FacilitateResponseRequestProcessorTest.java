/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.FacilitatorResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class FacilitateResponseRequestProcessorTest extends CategoryTest {
  @Mock private io.harness.engine.OrchestrationEngine orchestrationEngine;
  @InjectMocks private FacilitateResponseRequestProcessor facilitateResponseRequestHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleAdviseEvent() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    // Facilitation success.
    FacilitatorResponseRequest request = FacilitatorResponseRequest.newBuilder()
                                             .setFacilitatorResponse(FacilitatorResponseProto.newBuilder()
                                                                         .setIsSuccessful(true)
                                                                         .setExecutionMode(ExecutionMode.TASK)
                                                                         .build())
                                             .build();
    SdkResponseEventProto sdkResponseEventInternal =
        SdkResponseEventProto.newBuilder().setAmbiance(ambiance).setFacilitatorResponseRequest(request).build();
    facilitateResponseRequestHandler.handleEvent(sdkResponseEventInternal);
    // facilitation response is successful. So engine.processFacilitatorResponse will be invoked.
    verify(orchestrationEngine, times(1))
        .processFacilitatorResponse(sdkResponseEventInternal.getAmbiance(), request.getFacilitatorResponse());

    // Facilitation failed.
    request = FacilitatorResponseRequest.newBuilder()
                  .setFacilitatorResponse(FacilitatorResponseProto.newBuilder()
                                              .setIsSuccessful(false)
                                              .setPassThroughData("Error during the facilitation")
                                              .setExecutionMode(ExecutionMode.TASK)
                                              .build())
                  .build();
    sdkResponseEventInternal =
        SdkResponseEventProto.newBuilder().setAmbiance(ambiance).setFacilitatorResponseRequest(request).build();
    facilitateResponseRequestHandler.handleEvent(sdkResponseEventInternal);
    ArgumentCaptor<StepResponseProto> argumentCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    // facilitation response is not successful. So engine.processStepResponse will be invoked with status=FAILED.
    verify(orchestrationEngine, times(1))
        .processStepResponse(eq(sdkResponseEventInternal.getAmbiance()), argumentCaptor.capture());
    StepResponseProto stepResponseProto = argumentCaptor.getValue();

    assertThat(stepResponseProto.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder()
                       .addFailureData(FailureData.newBuilder()
                                           .setMessage(request.getFacilitatorResponse().getPassThroughData())
                                           .setCode(ErrorCode.GENERAL_ERROR.name())
                                           .setLevel(io.harness.eraro.Level.ERROR.name())
                                           .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                           .build())
                       .setErrorMessage(request.getFacilitatorResponse().getPassThroughData())
                       .build());
    assertThat(stepResponseProto.getStatus()).isEqualTo(Status.FAILED);
  }
}
