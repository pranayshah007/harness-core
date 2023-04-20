/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.ExecutionMode.CONSTRAINT;
import static io.harness.pms.contracts.execution.ExecutionMode.SYNC;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.distribution.constraint.Consumer;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.facilitator.DefaultFacilitatorParams;
import io.harness.pms.sdk.core.execution.events.node.facilitate.FacilitatorResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintRegistry;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintFacilitatorTest extends OrchestrationStepsTestBase {
  private static final String RESOURCE_RESTRAINT_ID = generateUuid();
  private static final ParameterField<String> RESOURCE_UNIT =
      ParameterField.<String>builder().value(generateUuid()).build();

  @Inject private KryoSerializer kryoSerializer;
  @Mock private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Mock private ResourceRestraintService resourceRestraintService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject @InjectMocks private ResourceRestraintRegistry resourceRestraintRegistry;
  @Inject @InjectMocks private ResourceRestraintFacilitator resourceRestraintFacilitator;

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
    when(resourceRestraintService.get(any())).thenReturn(resourceConstraint);
    doReturn(Constraint.builder()
                 .id(constraintId)
                 .spec(Constraint.Spec.builder().limits(1).strategy(Constraint.Strategy.FIFO).build())
                 .build())
        .when(resourceRestraintInstanceService)
        .createAbstraction(any());
    when(pmsEngineExpressionService.renderExpression(any(), any())).thenReturn(RESOURCE_UNIT.getValue());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldReturnAsyncMode() {
    String uuid = generateUuid();
    String planNodeId = generateUuid();
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(uuid).setSetupId(planNodeId).build()))
                            .build();
    byte[] parameters = kryoSerializer.asBytes(DefaultFacilitatorParams.builder().build());
    ResourceRestraintSpecParameters specParameters = ResourceRestraintSpecParameters.builder()
                                                         .resourceUnit(RESOURCE_UNIT)
                                                         .acquireMode(AcquireMode.ACCUMULATE)
                                                         .holdingScope(HoldingScope.PIPELINE)
                                                         .permits(1)
                                                         .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    doReturn(Collections.singletonList(ResourceRestraintInstance.builder()
                                           .state(Consumer.State.ACTIVE)
                                           .permits(1)
                                           .releaseEntityType(HoldingScope.PIPELINE.name())
                                           .releaseEntityId(planExecutionId)
                                           .build()))
        .when(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(any(), any(), any());

    FacilitatorResponse response =
        resourceRestraintFacilitator.facilitate(ambiance, stepElementParameters, parameters, null);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionMode()).isEqualTo(CONSTRAINT);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldReturnSyncMode() {
    String uuid = generateUuid();
    String planNodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(uuid).setSetupId(planNodeId).build()))
                            .build();
    byte[] parameters = kryoSerializer.asBytes(DefaultFacilitatorParams.builder().build());
    ResourceRestraintSpecParameters specParameters = ResourceRestraintSpecParameters.builder()
                                                         .resourceUnit(RESOURCE_UNIT)
                                                         .acquireMode(AcquireMode.ENSURE)
                                                         .holdingScope(HoldingScope.PIPELINE)
                                                         .permits(1)
                                                         .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(specParameters).build();

    doReturn(Collections.emptyList())
        .when(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(any(), any(), any());
    doReturn(0).when(resourceRestraintInstanceService).getAllCurrentlyAcquiredPermits(any(), any(), any());
    FacilitatorResponse response =
        resourceRestraintFacilitator.facilitate(ambiance, stepElementParameters, parameters, null);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionMode()).isEqualTo(SYNC);
  }
}
