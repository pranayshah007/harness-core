/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.distribution.constraint.Consumer.State.FINISHED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.ResourceRestraintInstanceRepository;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintInstanceServiceImplTest extends OrchestrationStepsTestBase {
  private static final String RESOURCE_UNIT = generateUuid();

  @Inject private ResourceRestraintInstanceRepository restraintInstanceRepository;

  @Mock private PlanExecutionService planExecutionService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Inject @InjectMocks @Spy private ResourceRestraintInstanceService resourceRestraintInstanceService;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(ACTIVE)
                                             .build();

    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestActivateBlockedInstance() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .resourceUnit(generateUuid())
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(BLOCKED)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    ResourceRestraintInstance updatedInstance = resourceRestraintInstanceService.activateBlockedInstance(
        savedInstance.getUuid(), savedInstance.getResourceUnit());
    assertThat(updatedInstance).isNotNull();
    assertThat(updatedInstance.getState()).isEqualTo(ACTIVE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestActivateBlockedInstance_InvalidRequestException() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .resourceUnit(generateUuid())
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(BLOCKED)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    assertThatThrownBy(
        () -> resourceRestraintInstanceService.activateBlockedInstance(generateUuid(), savedInstance.getResourceUnit()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("Cannot find ResourceRestraintInstance with id");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestFinishActiveInstance() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .resourceUnit(generateUuid())
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(ACTIVE)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    ResourceRestraintInstance updatedInstance =
        resourceRestraintInstanceService.finishInstance(savedInstance.getUuid(), savedInstance.getResourceUnit());
    assertThat(updatedInstance).isNotNull();
    assertThat(updatedInstance.getState()).isEqualTo(FINISHED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestFinishActiveInstance_InvalidRequestException() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .resourceUnit(generateUuid())
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(ACTIVE)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    assertThatThrownBy(
        () -> resourceRestraintInstanceService.finishInstance(generateUuid(), savedInstance.getResourceUnit()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("Cannot find ResourceRestraintInstance with id");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForInstance_ForPlan() {
    ResourceRestraintInstance instance = saveInstance(BLOCKED, HoldingScope.PIPELINE);

    when(planExecutionService.get(any())).thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).build());

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isTrue();

    Optional<ResourceRestraintInstance> updatedInstance = restraintInstanceRepository.findById(instance.getUuid());
    assertThat(updatedInstance.isPresent()).isTrue();
    assertThat(updatedInstance.get().getState()).isEqualTo(FINISHED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForInstance_ForPlan_InvalidRequestException() {
    ResourceRestraintInstance instance = saveInstance(BLOCKED, HoldingScope.PIPELINE);

    when(planExecutionService.get(any())).thenThrow(new InvalidRequestException(""));

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isFalse();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForInstance_ForOther() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(generateUuid()).setSetupId(generateUuid()).build()))
                            .build();
    ResourceRestraintInstance instance = saveInstance(BLOCKED, HoldingScope.STAGE);

    when(nodeExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(
            NodeExecution.builder()
                .ambiance(ambiance)
                .planNode(
                    PlanNode.builder()
                        .uuid(generateUuid())
                        .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                        .name("dummy")
                        .identifier("dummy")
                        .build())
                .mode(ExecutionMode.SYNC)
                .status(Status.SUCCEEDED)
                .build());

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isTrue();

    Optional<ResourceRestraintInstance> updatedInstance = restraintInstanceRepository.findById(instance.getUuid());
    assertThat(updatedInstance.isPresent()).isTrue();
    assertThat(updatedInstance.get().getState()).isEqualTo(FINISHED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForInstance_ForOther_InvalidRequestException() {
    ResourceRestraintInstance instance = saveInstance(BLOCKED, HoldingScope.STAGE);

    when(nodeExecutionService.getByPlanNodeUuid(any(), any())).thenThrow(new InvalidRequestException(""));

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isFalse();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGetAllByRestraintIdAndResourceUnitAndStates() {
    ResourceRestraintInstance instance = saveInstance(ACTIVE, HoldingScope.PIPELINE);

    List<ResourceRestraintInstance> instances =
        resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
            instance.getResourceRestraintId(), instance.getResourceUnit(), Lists.newArrayList(ACTIVE));

    assertThat(instances).isNotEmpty();
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances).contains(instance);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGetMaxOrder() {
    String resourceRestraintId = generateUuid();
    ResourceRestraintInstance instance = saveInstance(resourceRestraintId, ACTIVE, HoldingScope.PIPELINE, 1);
    saveInstance(resourceRestraintId, ACTIVE, HoldingScope.PIPELINE, 2);

    int maxOrder = resourceRestraintInstanceService.getMaxOrder(instance.getResourceRestraintId());
    assertThat(maxOrder).isEqualTo(2);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGetAllCurrentlyAcquiredPermits() {
    String releaseEntityId = generateUuid();
    ResourceRestraintInstance instance = saveInstance(releaseEntityId, RESOURCE_UNIT);
    saveInstance(releaseEntityId, RESOURCE_UNIT);

    int maxOrder = resourceRestraintInstanceService.getAllCurrentlyAcquiredPermits(
        HoldingScope.valueOf(instance.getReleaseEntityType()), instance.getReleaseEntityId(),
        instance.getResourceUnit());
    assertThat(maxOrder).isEqualTo(2);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetAllCurrentlyAcquiredPermitsForDifferentKeys() {
    String releaseEntityId = generateUuid();
    saveInstance(releaseEntityId, "keyA");
    saveInstance(releaseEntityId, "keyA");
    saveInstance(releaseEntityId, "keyB");

    int permits =
        resourceRestraintInstanceService.getAllCurrentlyAcquiredPermits(HoldingScope.PIPELINE, releaseEntityId, "keyB");
    assertThat(permits).isEqualTo(1);
  }

  private ResourceRestraintInstance saveInstance(String releaseEntityId, String resourceUnit) {
    return saveInstance(generateUuid(), ACTIVE, HoldingScope.PIPELINE, releaseEntityId, 1, resourceUnit);
  }

  private ResourceRestraintInstance saveInstance(State state, HoldingScope scope) {
    return saveInstance(generateUuid(), state, scope,
        scope == HoldingScope.PIPELINE ? generateUuid() : generateUuid() + '|' + generateUuid(), 1, generateUuid());
  }

  private ResourceRestraintInstance saveInstance(
      String resourceRestraintId, State state, HoldingScope scope, int order) {
    return saveInstance(resourceRestraintId, state, scope,
        scope == HoldingScope.PIPELINE ? generateUuid() : generateUuid() + '|' + generateUuid(), order, generateUuid());
  }

  private ResourceRestraintInstance saveInstance(String resourceRestraintId, State state,
      HoldingScope releaseEntityType, String releaseEntityId, int order, String resourceUnit) {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(releaseEntityId)
                                             .releaseEntityType(releaseEntityType.name())
                                             .resourceUnit(resourceUnit)
                                             .order(order)
                                             .permits(1)
                                             .resourceRestraintId(resourceRestraintId)
                                             .state(state)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    return savedInstance;
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testProcessRestraintBlockedInstance() {
    ResourceRestraintInstance instance = getResourceRestraint(BLOCKED);
    resourceRestraintInstanceService.processRestraint(instance);
    verify(resourceRestraintInstanceService)
        .updateBlockedConstraints(Sets.newHashSet(instance.getResourceRestraintId()));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testProcessRestraintActiveInstance() {
    ResourceRestraintInstance instance = getResourceRestraint(ACTIVE);
    Set<String> constraintIds = Sets.newHashSet(instance.getResourceRestraintId());
    doNothing().when(resourceRestraintInstanceService).updateBlockedConstraints(constraintIds);
    doReturn(true).when(resourceRestraintInstanceService).updateActiveConstraintsForInstance(eq(instance));
    resourceRestraintInstanceService.processRestraint(instance);
    verify(resourceRestraintInstanceService).updateActiveConstraintsForInstance(eq(instance));
    verify(resourceRestraintInstanceService).updateBlockedConstraints(constraintIds);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testActiveInstance_WhenNoInstancesAreUpdated() {
    ResourceRestraintInstance instance = getResourceRestraint(ACTIVE);
    doReturn(false).when(resourceRestraintInstanceService).updateActiveConstraintsForInstance(eq(instance));
    resourceRestraintInstanceService.processRestraint(instance);
    verify(resourceRestraintInstanceService, never())
        .updateBlockedConstraints(Sets.newHashSet(instance.getResourceRestraintId()));
  }

  private ResourceRestraintInstance getResourceRestraint(State state) {
    return ResourceRestraintInstance.builder().resourceRestraintId(generateUuid()).state(state).build();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateAbstraction() {
    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid("UUID").capacity(1010).strategy(Constraint.Strategy.ASAP).build();
    Constraint constraint = resourceRestraintInstanceService.createAbstraction(resourceRestraint);
    assertThat(constraint).isNotNull();
    assertThat(constraint.getId()).isNotNull();
    assertThat(constraint.getId().getValue()).isEqualTo("UUID");
    assertThat(constraint.getSpec()).isNotNull();
    assertThat(constraint.getSpec().getLimits()).isEqualTo(1010);
    assertThat(constraint.getSpec().getStrategy()).isEqualTo(Constraint.Strategy.ASAP);
  }
}
