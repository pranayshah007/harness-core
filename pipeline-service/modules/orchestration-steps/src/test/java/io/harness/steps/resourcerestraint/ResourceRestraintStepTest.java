/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintResponseData;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String RESOURCE_RESTRAINT_ID = generateUuid();
  private static final ParameterField<String> RESOURCE_UNIT =
      ParameterField.<String>builder().value(generateUuid()).build();

  @Mock private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Mock private ResourceRestraintService resourceRestraintService;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ILogStreamingStepClient iLogStreamingStepClient;
  @Inject @InjectMocks private ResourceRestraintStep resourceRestraintStep;

  @Before
  public void setUp() {
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(iLogStreamingStepClient);
    ResourceRestraint resourceConstraint = ResourceRestraint.builder()
                                               .accountId(generateUuid())
                                               .capacity(1)
                                               .strategy(Constraint.Strategy.FIFO)
                                               .uuid(generateUuid())
                                               .build();
    ConstraintId constraintId = new ConstraintId(RESOURCE_RESTRAINT_ID);
    when(resourceRestraintService.getByNameAndAccountId(any(), any())).thenReturn(resourceConstraint);
    when(resourceRestraintService.get(any())).thenReturn(resourceConstraint);
    doReturn(Constraint.builder()
                 .id(constraintId)
                 .spec(Constraint.Spec.builder().limits(1).strategy(Constraint.Strategy.FIFO).build())
                 .build())
        .when(resourceRestraintInstanceService)
        .createAbstraction(any());
    doReturn(ResourceRestraintInstance.builder().build()).when(resourceRestraintInstanceService).save(any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestHandleAsyncResponse() {
    String uuid = generateUuid();
    String planNodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(uuid).setSetupId(planNodeId).build()))
                            .build();
    ResourceRestraintSpecParameters specParameters = ResourceRestraintSpecParameters.builder()
                                                         .resourceUnit(RESOURCE_UNIT)
                                                         .acquireMode(AcquireMode.ACCUMULATE)
                                                         .holdingScope(HoldingScope.PIPELINE)
                                                         .permits(1)
                                                         .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    doNothing().when(resourceRestraintInstanceService).updateBlockedConstraints(any());

    ResourceRestraintResponseData responseData = ResourceRestraintResponseData.builder()
                                                     .resourceRestraintId(generateUuid())
                                                     .resourceUnit(generateUuid())
                                                     .build();

    StepResponse stepResponse = resourceRestraintStep.handleAsyncResponse(
        ambiance, stepElementParameters, ImmutableMap.of(generateUuid(), responseData));

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(SUCCEEDED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldHandleAbort() {
    String uuid = generateUuid();
    String planNodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(uuid).setSetupId(planNodeId).build()))
                            .build();
    ResourceRestraintSpecParameters specParameters = ResourceRestraintSpecParameters.builder()
                                                         .resourceUnit(RESOURCE_UNIT)
                                                         .acquireMode(AcquireMode.ACCUMULATE)
                                                         .holdingScope(HoldingScope.PIPELINE)
                                                         .permits(1)
                                                         .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    when(resourceRestraintInstanceService.finishInstance(any(), any()))
        .thenReturn(ResourceRestraintInstance.builder().build());

    resourceRestraintStep.handleAbort(
        ambiance, stepElementParameters, AsyncExecutableResponse.newBuilder().addCallbackIds(generateUuid()).build());

    verify(resourceRestraintInstanceService).finishInstance(any(), any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldHandleAbort_ThrowException() {
    String uuid = generateUuid();
    String planNodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(uuid).setSetupId(planNodeId).build()))
                            .build();
    ResourceRestraintSpecParameters specParameters = ResourceRestraintSpecParameters.builder()
                                                         .resourceUnit(RESOURCE_UNIT)
                                                         .acquireMode(AcquireMode.ACCUMULATE)
                                                         .holdingScope(HoldingScope.PIPELINE)
                                                         .permits(1)
                                                         .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    when(resourceRestraintInstanceService.finishInstance(any(), any()))
        .thenThrow(new InvalidRequestException("Exception"));

    assertThatThrownBy(()
                           -> resourceRestraintStep.handleAbort(ambiance, stepElementParameters,
                               AsyncExecutableResponse.newBuilder().addCallbackIds("").build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("Exception");

    verify(resourceRestraintInstanceService, only()).finishInstance(any(), any());
  }
}