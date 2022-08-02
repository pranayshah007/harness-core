/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.barriers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.barrier.Barrier.State.STANDING;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierResponseData;
import io.harness.steps.barriers.service.BarrierService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierStepTest extends OrchestrationStepsTestBase {
  @Mock BarrierService barrierService;
  @Inject @InjectMocks BarrierStep barrierStep;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestExecuteAsync() {
    String uuid = generateUuid();
    String barrierIdentifier = "barrierIdentifier";
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(Collections.singletonList(Level.newBuilder().setRuntimeId(uuid).build()))
                            .setPlanExecutionId(generateUuid())
                            .build();
    BarrierExecutionInstance barrier = BarrierExecutionInstance.builder()
                                           .uuid(uuid)
                                           .identifier(barrierIdentifier)
                                           .planExecutionId(ambiance.getPlanExecutionId())
                                           .barrierState(STANDING)
                                           .build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    BarrierSpecParameters stepParameters = BarrierSpecParameters.builder().barrierRef(barrierIdentifier).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    when(barrierService.findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId()))
        .thenReturn(barrier);

    AsyncExecutableResponse stepResponse =
        barrierStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getCallbackIdsList()).contains(uuid);
    assertThat(stepResponse.getLogKeysList()).isEmpty();
    assertThat(stepResponse.getUnitsList()).isEmpty();

    verify(barrierService).findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponse() {
    String uuid = generateUuid();
    String barrierIdentifier = "barrierIdentifier";
    BarrierExecutionInstance barrier =
        BarrierExecutionInstance.builder().uuid(uuid).identifier(barrierIdentifier).barrierState(STANDING).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(Collections.singletonList(Level.newBuilder().setRuntimeId(uuid).build()))
                            .setPlanExecutionId(generateUuid())
                            .build();
    BarrierSpecParameters specParameters = BarrierSpecParameters.builder().barrierRef(barrierIdentifier).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    when(barrierService.findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId()))
        .thenReturn(barrier);
    when(barrierService.update(barrier)).thenReturn(barrier);

    StepResponse stepResponse = barrierStep.handleAsyncResponse(
        ambiance, stepElementParameters, ImmutableMap.of(uuid, BarrierResponseData.builder().failed(false).build()));

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    verify(barrierService).findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId());
    verify(barrierService).update(barrier);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponse_Expired() {
    String uuid = generateUuid();
    String barrierIdentifier = "barrierIdentifier";
    BarrierExecutionInstance barrier =
        BarrierExecutionInstance.builder().uuid(uuid).identifier(barrierIdentifier).barrierState(STANDING).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(Collections.singletonList(Level.newBuilder().setRuntimeId(uuid).build()))
                            .setPlanExecutionId(generateUuid())
                            .build();
    BarrierSpecParameters specParameters = BarrierSpecParameters.builder().barrierRef(barrierIdentifier).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    when(barrierService.findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId()))
        .thenReturn(barrier);
    when(barrierService.update(barrier)).thenReturn(barrier);

    StepResponse stepResponse = barrierStep.handleAsyncResponse(ambiance, stepElementParameters,
        ImmutableMap.of(uuid,
            BarrierResponseData.builder()
                .failed(true)
                .barrierError(BarrierResponseData.BarrierError.builder().errorMessage("Error").timedOut(true).build())
                .build()));

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.EXPIRED);

    verify(barrierService).findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId());
    verify(barrierService).update(barrier);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponse_Failed() {
    String uuid = generateUuid();
    String barrierIdentifier = "barrierIdentifier";
    BarrierExecutionInstance barrier =
        BarrierExecutionInstance.builder().uuid(uuid).identifier(barrierIdentifier).barrierState(STANDING).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(Collections.singletonList(Level.newBuilder().setRuntimeId(uuid).build()))
                            .setPlanExecutionId(generateUuid())
                            .build();
    BarrierSpecParameters specParameters = BarrierSpecParameters.builder().barrierRef(barrierIdentifier).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    when(barrierService.findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId()))
        .thenReturn(barrier);
    when(barrierService.update(barrier)).thenReturn(barrier);

    StepResponse stepResponse = barrierStep.handleAsyncResponse(ambiance, stepElementParameters,
        ImmutableMap.of(uuid,
            BarrierResponseData.builder()
                .failed(true)
                .barrierError(BarrierResponseData.BarrierError.builder().errorMessage("Error").timedOut(false).build())
                .build()));

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);

    verify(barrierService).findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId());
    verify(barrierService).update(barrier);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldHandleAbort() {
    String uuid = generateUuid();
    String barrierIdentifier = "barrierIdentifier";
    BarrierExecutionInstance barrier =
        BarrierExecutionInstance.builder().uuid(uuid).identifier(barrierIdentifier).barrierState(STANDING).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(Collections.singletonList(Level.newBuilder().setRuntimeId(uuid).build()))
                            .setPlanExecutionId(generateUuid())
                            .build();
    BarrierSpecParameters specParameters = BarrierSpecParameters.builder().barrierRef(barrierIdentifier).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    when(barrierService.findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId()))
        .thenReturn(barrier);
    when(barrierService.update(barrier)).thenReturn(barrier);

    barrierStep.handleAbort(ambiance, stepElementParameters, null);

    verify(barrierService).findByIdentifierAndPlanExecutionId(barrierIdentifier, ambiance.getPlanExecutionId());
    verify(barrierService).update(barrier);
  }
}
