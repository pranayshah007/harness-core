package io.harness.steps.resourcerestraint;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.distribution.constraint.Consumer;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.HoldingScope.HoldingScopeBuilder;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintStepTest extends OrchestrationStepsTestBase {
  private static final String RESOURCE_RESTRAINT_ID = generateUuid();
  private static final String RESOURCE_UNIT = generateUuid();
  private static final HoldingScope HOLDING_SCOPE = HoldingScopeBuilder.aPlan().build();

  @Mock private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Mock private ResourceRestraintService resourceRestraintService;
  @Inject @InjectMocks private ResourceRestraintStep resourceRestraintStep;

  @Before
  public void setUp() {
    ResourceRestraint resourceConstraint = ResourceRestraint.builder()
                                               .accountId(generateUuid())
                                               .capacity(1)
                                               .strategy(Constraint.Strategy.FIFO)
                                               .uuid(generateUuid())
                                               .build();
    ConstraintId constraintId = new ConstraintId(RESOURCE_RESTRAINT_ID);
    when(resourceRestraintService.getByNameAndAccountId(any(), any())).thenReturn(resourceConstraint);
    when(resourceRestraintService.get(any(), any())).thenReturn(resourceConstraint);
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
  public void shouldTestExecuteAsync() {
    String uuid = generateUuid();
    String planNodeId = generateUuid();
    String consumerId = generateUuid();
    HoldingScope holdingScope = HoldingScopeBuilder.aPlan().build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(uuid).setSetupId(planNodeId).build()))
                            .build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    ResourceRestraintSpecParameters specParameters = ResourceRestraintSpecParameters.builder()
                                                         .resourceUnit(RESOURCE_UNIT)
                                                         .acquireMode(AcquireMode.ACCUMULATE)
                                                         .holdingScope(holdingScope)
                                                         .permits(1)
                                                         .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    doReturn(Collections.singletonList(ResourceRestraintInstance.builder()
                                           .state(Consumer.State.ACTIVE)
                                           .permits(1)
                                           .releaseEntityType(holdingScope.getScope())
                                           .releaseEntityId(holdingScope.getNodeSetupId())
                                           .build()))
        .when(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(any(), any(), any());
    AsyncExecutableResponse asyncExecutableResponse =
        resourceRestraintStep.executeAsync(ambiance, stepElementParameters, stepInputPackage,
            ResourceRestraintPassThroughData.builder().consumerId(consumerId).build());

    assertThat(asyncExecutableResponse).isNotNull();
    assertThat(asyncExecutableResponse.getCallbackIdsCount()).isEqualTo(1);
    assertThat(asyncExecutableResponse.getCallbackIdsList().get(0)).isEqualTo(consumerId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestExecuteSync() {
    String uuid = generateUuid();
    String planNodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(uuid).setSetupId(planNodeId).build()))
                            .build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    ResourceRestraintSpecParameters specParameters = ResourceRestraintSpecParameters.builder()
                                                         .resourceUnit(RESOURCE_UNIT)
                                                         .acquireMode(AcquireMode.ACCUMULATE)
                                                         .holdingScope(HOLDING_SCOPE)
                                                         .permits(1)
                                                         .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    StepResponse stepResponse =
        resourceRestraintStep.executeSync(ambiance, stepElementParameters, stepInputPackage, null);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(SUCCEEDED);
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
                                                         .holdingScope(HOLDING_SCOPE)
                                                         .permits(1)
                                                         .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    doNothing().when(resourceRestraintInstanceService).updateBlockedConstraints(any());

    StepResponse stepResponse = resourceRestraintStep.handleAsyncResponse(ambiance, stepElementParameters, null);

    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(SUCCEEDED);

    verify(resourceRestraintService).getByNameAndAccountId(any(), any());
    verify(resourceRestraintInstanceService).updateBlockedConstraints(any());
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
                                                         .holdingScope(HOLDING_SCOPE)
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
                                                         .holdingScope(HOLDING_SCOPE)
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
