/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.resourceconstraint.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint.Strategy;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.resourceconstraints.response.ResourceConstraintDetailDTO;
import io.harness.pms.resourceconstraints.response.ResourceConstraintExecutionInfoDTO;
import io.harness.pms.resourceconstraints.service.PMSResourceConstraintServiceImpl;
import io.harness.pms.utils.PmsConstants;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSResourceConstraintServiceTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = generateUuid();
  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String STAGE_EXECUTION_ID = generateUuid();
  private static final String RESOURCE_UNIT = generateUuid();

  @Mock private ResourceRestraintService resourceRestraintService;
  @Mock private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PMSPipelineService pipelineService;
  @Mock private NodeExecutionService nodeExecutionService;
  private PMSResourceConstraintServiceImpl pmsResourceConstraintService;

  @Before
  public void setUp() {
    pmsResourceConstraintService = new PMSResourceConstraintServiceImpl(resourceRestraintService,
        resourceRestraintInstanceService, planExecutionService, pipelineService, nodeExecutionService);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenResourceConstraintNotFound() {
    when(resourceRestraintService.getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID)).thenReturn(null);

    assertThatThrownBy(() -> pmsResourceConstraintService.getResourceConstraintExecutionInfo(ACCOUNT_ID, RESOURCE_UNIT))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format(PMSResourceConstraintServiceImpl.NOT_FOUND_WITH_ARGUMENTS, ACCOUNT_ID));

    verify(resourceRestraintService).getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGetResourceConstraintExecutionInfoList() {
    ResourceRestraint resourceConstraint = getResourceConstraint();
    List<ResourceRestraintInstance> restraintInstanceList =
        Lists.newArrayList(ResourceRestraintInstance.builder()
                               .state(State.BLOCKED)
                               .uuid(generateUuid())
                               .resourceRestraintId(resourceConstraint.getUuid())
                               .resourceUnit(RESOURCE_UNIT)
                               .releaseEntityType(PmsConstants.RELEASE_ENTITY_TYPE_PLAN)
                               .releaseEntityId(PLAN_EXECUTION_ID)
                               .order(2)
                               .build(),
            ResourceRestraintInstance.builder()
                .state(State.ACTIVE)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(PmsConstants.RELEASE_ENTITY_TYPE_PLAN)
                .releaseEntityId(PLAN_EXECUTION_ID + "1")
                .order(1)
                .build(),
            ResourceRestraintInstance.builder()
                .state(State.BLOCKED)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(PmsConstants.RELEASE_ENTITY_TYPE_PLAN)
                .releaseEntityId(PLAN_EXECUTION_ID + "2")
                .order(3)
                .build());
    Map<String, String> setupAbstractions = new HashMap<>();
    List<PlanExecution> planExecutionList =
        Lists.newArrayList(PlanExecution.builder()
                               .uuid(PLAN_EXECUTION_ID)
                               .setupAbstractions(setupAbstractions)
                               .startTs(20L)
                               .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("k8s").build())
                               .build(),
            PlanExecution.builder()
                .uuid(PLAN_EXECUTION_ID + "1")
                .setupAbstractions(setupAbstractions)
                .startTs(10L)
                .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("rc-pipeline").build())
                .build(),
            PlanExecution.builder()
                .uuid(PLAN_EXECUTION_ID + "2")
                .setupAbstractions(setupAbstractions)
                .startTs(30L)
                .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("barriers-pipeline").build())
                .build());

    when(resourceRestraintService.getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID))
        .thenReturn(resourceConstraint);
    when(resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
             resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED)))
        .thenReturn(restraintInstanceList);
    when(planExecutionService.findAllByPlanExecutionIdIn(any())).thenReturn(planExecutionList);

    ResourceConstraintExecutionInfoDTO response =
        pmsResourceConstraintService.getResourceConstraintExecutionInfo(ACCOUNT_ID, RESOURCE_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getCapacity()).isEqualTo(resourceConstraint.getCapacity());
    assertThat(response.getName()).isEqualTo(resourceConstraint.getName());
    assertThat(response.getResourceConstraints()).isNotEmpty();
    assertThat(response.getResourceConstraints().size()).isEqualTo(3);
    assertThat(response.getResourceConstraints())
        .containsExactly(ResourceConstraintDetailDTO.builder()
                             .pipelineIdentifier("rc-pipeline")
                             .planExecutionId(PLAN_EXECUTION_ID + "1")
                             .state(ACTIVE)
                             .startTs(10L)
                             .build(),
            ResourceConstraintDetailDTO.builder()
                .pipelineIdentifier("k8s")
                .planExecutionId(PLAN_EXECUTION_ID)
                .state(BLOCKED)
                .startTs(20L)
                .build(),
            ResourceConstraintDetailDTO.builder()
                .pipelineIdentifier("barriers-pipeline")
                .planExecutionId(PLAN_EXECUTION_ID + "2")
                .state(BLOCKED)
                .startTs(30L)
                .build());

    verify(resourceRestraintService).getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID);
    verify(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(
            resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED));
    verify(planExecutionService).findAllByPlanExecutionIdIn(any());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetResourceConstraintExecutionInfoListWhenStageScope() {
    ResourceRestraint resourceConstraint = getResourceConstraint();
    List<ResourceRestraintInstance> restraintInstanceList =
        Lists.newArrayList(ResourceRestraintInstance.builder()
                               .state(State.BLOCKED)
                               .uuid(generateUuid())
                               .resourceRestraintId(resourceConstraint.getUuid())
                               .resourceUnit(RESOURCE_UNIT)
                               .releaseEntityType(HoldingScope.STAGE.name())
                               .releaseEntityId(STAGE_EXECUTION_ID)
                               .order(2)
                               .build(),
            ResourceRestraintInstance.builder()
                .state(State.ACTIVE)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(HoldingScope.STAGE.name())
                .releaseEntityId(STAGE_EXECUTION_ID + "1")
                .order(1)
                .build(),
            ResourceRestraintInstance.builder()
                .state(State.BLOCKED)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(HoldingScope.STAGE.name())
                .releaseEntityId(STAGE_EXECUTION_ID + "2")
                .order(3)
                .build());
    Map<String, String> setupAbstractions = createSetupAbstractions();
    List<PlanExecution> planExecutionList =
        Lists.newArrayList(PlanExecution.builder()
                               .uuid(PLAN_EXECUTION_ID)
                               .setupAbstractions(setupAbstractions)
                               .startTs(20L)
                               .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("k8s").build())
                               .build(),
            PlanExecution.builder()
                .uuid(PLAN_EXECUTION_ID + "1")
                .setupAbstractions(setupAbstractions)
                .startTs(10L)
                .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("rc-pipeline").build())
                .build(),
            PlanExecution.builder()
                .uuid(PLAN_EXECUTION_ID + "2")
                .setupAbstractions(setupAbstractions)
                .startTs(30L)
                .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("barriers-pipeline").build())
                .build());

    when(resourceRestraintService.getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID))
        .thenReturn(resourceConstraint);
    when(resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
             resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED)))
        .thenReturn(restraintInstanceList);
    when(planExecutionService.findAllByPlanExecutionIdIn(any())).thenReturn(planExecutionList);
    when(nodeExecutionService.get(STAGE_EXECUTION_ID))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build())
                        .build());
    when(nodeExecutionService.get(STAGE_EXECUTION_ID + "1"))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID + "1").build())
                        .build());
    when(nodeExecutionService.get(STAGE_EXECUTION_ID + "2"))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID + "2").build())
                        .build());

    ResourceConstraintExecutionInfoDTO response =
        pmsResourceConstraintService.getResourceConstraintExecutionInfo(ACCOUNT_ID, RESOURCE_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getCapacity()).isEqualTo(resourceConstraint.getCapacity());
    assertThat(response.getName()).isEqualTo(resourceConstraint.getName());
    assertThat(response.getResourceConstraints()).isNotEmpty();
    assertThat(response.getResourceConstraints().size()).isEqualTo(3);
    assertThat(response.getResourceConstraints())
        .containsExactly(ResourceConstraintDetailDTO.builder()
                             .pipelineIdentifier("rc-pipeline")
                             .planExecutionId(PLAN_EXECUTION_ID + "1")
                             .state(ACTIVE)
                             .startTs(10L)
                             .build(),
            ResourceConstraintDetailDTO.builder()
                .pipelineIdentifier("k8s")
                .planExecutionId(PLAN_EXECUTION_ID)
                .state(BLOCKED)
                .startTs(20L)
                .build(),
            ResourceConstraintDetailDTO.builder()
                .pipelineIdentifier("barriers-pipeline")
                .planExecutionId(PLAN_EXECUTION_ID + "2")
                .state(BLOCKED)
                .startTs(30L)
                .build());

    verify(resourceRestraintService).getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID);
    verify(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(
            resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED));
    verify(planExecutionService).findAllByPlanExecutionIdIn(any());
  }

  private Map<String, String> createSetupAbstractions() {
    return new HashMap<>();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetPipelineNameCallRemoteServiceOnlyOnce() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "A");
    setupAbstractions.put("orgIdentifier", "B");
    setupAbstractions.put("projectIdentifier", "C");
    PlanExecution planExecution =
        PlanExecution.builder()
            .uuid(PLAN_EXECUTION_ID)
            .setupAbstractions(setupAbstractions)
            .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipeline-id").build())
            .build();

    when(pipelineService.getPipeline("A", "B", "C", "pipeline-id", false, true))
        .thenReturn(Optional.of(PipelineEntity.builder().name("pipeline-name").build()));

    Map<String, PipelineEntity> cache = new HashMap<>();
    assertThat(pmsResourceConstraintService.getPipelineName(cache, planExecution)).isEqualTo("pipeline-name");
    assertThat(pmsResourceConstraintService.getPipelineName(cache, planExecution)).isEqualTo("pipeline-name");
    assertThat(pmsResourceConstraintService.getPipelineName(cache, planExecution)).isEqualTo("pipeline-name");

    verify(pipelineService, Mockito.times(1)).getPipeline("A", "B", "C", "pipeline-id", false, true);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetResourceConstraintExecutionInfoListWhenParallelExecution() {
    ResourceRestraint resourceConstraint = getResourceConstraint();
    List<ResourceRestraintInstance> restraintInstanceList =
        Lists.newArrayList(ResourceRestraintInstance.builder()
                               .state(State.BLOCKED)
                               .uuid(generateUuid())
                               .resourceRestraintId(resourceConstraint.getUuid())
                               .resourceUnit(RESOURCE_UNIT)
                               .releaseEntityType(HoldingScope.PIPELINE.name())
                               .releaseEntityId(PLAN_EXECUTION_ID)
                               .order(2)
                               .build(),
            ResourceRestraintInstance.builder()
                .state(State.ACTIVE)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(HoldingScope.PIPELINE.name())
                .releaseEntityId(PLAN_EXECUTION_ID + "1")
                .order(1)
                .build(),
            ResourceRestraintInstance.builder()
                .state(State.BLOCKED)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(HoldingScope.PIPELINE.name())
                .releaseEntityId(PLAN_EXECUTION_ID)
                .order(3)
                .build());
    Map<String, String> setupAbstractions = new HashMap<>();
    List<PlanExecution> planExecutionList =
        Lists.newArrayList(PlanExecution.builder()
                               .uuid(PLAN_EXECUTION_ID)
                               .setupAbstractions(setupAbstractions)
                               .startTs(20L)
                               .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("k8s").build())
                               .build(),
            PlanExecution.builder()
                .uuid(PLAN_EXECUTION_ID + "1")
                .setupAbstractions(setupAbstractions)
                .startTs(10L)
                .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("rc-pipeline").build())
                .build());

    when(resourceRestraintService.getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID))
        .thenReturn(resourceConstraint);
    when(resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
             resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED)))
        .thenReturn(restraintInstanceList);
    when(planExecutionService.findAllByPlanExecutionIdIn(any())).thenReturn(planExecutionList);

    ResourceConstraintExecutionInfoDTO response =
        pmsResourceConstraintService.getResourceConstraintExecutionInfo(ACCOUNT_ID, RESOURCE_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getCapacity()).isEqualTo(resourceConstraint.getCapacity());
    assertThat(response.getName()).isEqualTo(resourceConstraint.getName());
    assertThat(response.getResourceConstraints()).isNotEmpty();
    assertThat(response.getResourceConstraints().size()).isEqualTo(3);
    assertThat(response.getResourceConstraints())
        .containsExactly(ResourceConstraintDetailDTO.builder()
                             .pipelineIdentifier("rc-pipeline")
                             .planExecutionId(PLAN_EXECUTION_ID + "1")
                             .state(ACTIVE)
                             .startTs(10L)
                             .build(),
            ResourceConstraintDetailDTO.builder()
                .pipelineIdentifier("k8s")
                .planExecutionId(PLAN_EXECUTION_ID)
                .state(BLOCKED)
                .startTs(20L)
                .build(),
            ResourceConstraintDetailDTO.builder()
                .pipelineIdentifier("k8s")
                .planExecutionId(PLAN_EXECUTION_ID)
                .state(BLOCKED)
                .startTs(20L)
                .build());

    verify(resourceRestraintService).getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID);
    verify(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(
            resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED));
    verify(planExecutionService).findAllByPlanExecutionIdIn(any());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetPipelineNameHandleExceptionFromPipelineServiceGetOperation() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "A");
    setupAbstractions.put("orgIdentifier", "B");
    setupAbstractions.put("projectIdentifier", "C");
    PlanExecution planExecution =
        PlanExecution.builder()
            .uuid(PLAN_EXECUTION_ID)
            .setupAbstractions(setupAbstractions)
            .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipeline-id").build())
            .build();

    when(pipelineService.getAndValidatePipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenThrow(new EntityNotFoundException("anyContent"));

    Map<String, PipelineEntity> cache = new HashMap<>();
    assertThat(pmsResourceConstraintService.getPipelineName(cache, planExecution)).isNull();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetResourceConstraintExecutionInfoListWhenStageScopeAndNodeExecutionNotFound() {
    ResourceRestraint resourceConstraint = getResourceConstraint();
    List<ResourceRestraintInstance> restraintInstanceList =
        Lists.newArrayList(ResourceRestraintInstance.builder()
                               .state(State.BLOCKED)
                               .uuid(generateUuid())
                               .resourceRestraintId(resourceConstraint.getUuid())
                               .resourceUnit(RESOURCE_UNIT)
                               .releaseEntityType(HoldingScope.STAGE.name())
                               .releaseEntityId(STAGE_EXECUTION_ID)
                               .order(2)
                               .build(),
            ResourceRestraintInstance.builder()
                .state(State.ACTIVE)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(HoldingScope.STAGE.name())
                .releaseEntityId(STAGE_EXECUTION_ID + "1")
                .order(1)
                .build(),
            ResourceRestraintInstance.builder()
                .state(State.BLOCKED)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(HoldingScope.STAGE.name())
                .releaseEntityId(STAGE_EXECUTION_ID + "2")
                .order(3)
                .build());
    Map<String, String> setupAbstractions = createSetupAbstractions();
    List<PlanExecution> planExecutionList =
        Lists.newArrayList(PlanExecution.builder()
                               .uuid(PLAN_EXECUTION_ID)
                               .setupAbstractions(setupAbstractions)
                               .startTs(20L)
                               .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("k8s").build())
                               .build(),
            PlanExecution.builder()
                .uuid(PLAN_EXECUTION_ID + "1")
                .setupAbstractions(setupAbstractions)
                .startTs(10L)
                .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("rc-pipeline").build())
                .build(),
            PlanExecution.builder()
                .uuid(PLAN_EXECUTION_ID + "2")
                .setupAbstractions(setupAbstractions)
                .startTs(30L)
                .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("barriers-pipeline").build())
                .build());

    when(resourceRestraintService.getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID))
        .thenReturn(resourceConstraint);
    when(resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
             resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED)))
        .thenReturn(restraintInstanceList);
    when(planExecutionService.findAllByPlanExecutionIdIn(any())).thenReturn(planExecutionList);
    when(nodeExecutionService.get(STAGE_EXECUTION_ID))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build())
                        .build());
    when(nodeExecutionService.get(STAGE_EXECUTION_ID + "1"))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID + "1").build())
                        .build());
    when(nodeExecutionService.get(STAGE_EXECUTION_ID + "2")).thenThrow(new InvalidRequestException(""));

    ResourceConstraintExecutionInfoDTO response =
        pmsResourceConstraintService.getResourceConstraintExecutionInfo(ACCOUNT_ID, RESOURCE_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getCapacity()).isEqualTo(resourceConstraint.getCapacity());
    assertThat(response.getName()).isEqualTo(resourceConstraint.getName());
    assertThat(response.getResourceConstraints()).isNotEmpty();
    assertThat(response.getResourceConstraints().size()).isEqualTo(2);
    assertThat(response.getResourceConstraints())
        .containsExactly(ResourceConstraintDetailDTO.builder()
                             .pipelineIdentifier("rc-pipeline")
                             .planExecutionId(PLAN_EXECUTION_ID + "1")
                             .state(ACTIVE)
                             .startTs(10L)
                             .build(),
            ResourceConstraintDetailDTO.builder()
                .pipelineIdentifier("k8s")
                .planExecutionId(PLAN_EXECUTION_ID)
                .state(BLOCKED)
                .startTs(20L)
                .build());

    verify(resourceRestraintService).getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID);
    verify(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(
            resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED));
    verify(planExecutionService).findAllByPlanExecutionIdIn(any());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetResourceConstraintExecutionInfoListWhenResourceRestraintInstanceIsEmpty() {
    ResourceRestraint resourceConstraint = getResourceConstraint();

    when(resourceRestraintService.getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID))
        .thenReturn(resourceConstraint);
    when(resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
             resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED)))
        .thenReturn(Collections.emptyList());
    when(planExecutionService.findAllByPlanExecutionIdIn(any())).thenReturn(Collections.emptyList());

    ResourceConstraintExecutionInfoDTO response =
        pmsResourceConstraintService.getResourceConstraintExecutionInfo(ACCOUNT_ID, RESOURCE_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getCapacity()).isEqualTo(resourceConstraint.getCapacity());
    assertThat(response.getName()).isEqualTo(resourceConstraint.getName());
    assertThat(response.getResourceConstraints()).isEmpty();

    verify(resourceRestraintService).getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID);
    verify(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(
            resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED));
    verify(planExecutionService).findAllByPlanExecutionIdIn(any());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetResourceConstraintExecutionInfoListWhenPlanExecutionListIsEmpty() {
    ResourceRestraint resourceConstraint = getResourceConstraint();
    List<ResourceRestraintInstance> restraintInstanceList =
        Lists.newArrayList(ResourceRestraintInstance.builder()
                               .state(State.BLOCKED)
                               .uuid(generateUuid())
                               .resourceRestraintId(resourceConstraint.getUuid())
                               .resourceUnit(RESOURCE_UNIT)
                               .releaseEntityType(HoldingScope.STAGE.name())
                               .releaseEntityId(STAGE_EXECUTION_ID)
                               .order(2)
                               .build(),
            ResourceRestraintInstance.builder()
                .state(State.ACTIVE)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(HoldingScope.STAGE.name())
                .releaseEntityId(STAGE_EXECUTION_ID + "1")
                .order(1)
                .build(),
            ResourceRestraintInstance.builder()
                .state(State.BLOCKED)
                .uuid(generateUuid())
                .resourceRestraintId(resourceConstraint.getUuid())
                .resourceUnit(RESOURCE_UNIT)
                .releaseEntityType(HoldingScope.STAGE.name())
                .releaseEntityId(STAGE_EXECUTION_ID + "2")
                .order(3)
                .build());
    List<PlanExecution> planExecutionList = Collections.emptyList();

    when(resourceRestraintService.getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID))
        .thenReturn(resourceConstraint);
    when(resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
             resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED)))
        .thenReturn(restraintInstanceList);
    when(planExecutionService.findAllByPlanExecutionIdIn(any())).thenReturn(planExecutionList);
    when(nodeExecutionService.get(STAGE_EXECUTION_ID))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID).build())
                        .build());
    when(nodeExecutionService.get(STAGE_EXECUTION_ID + "1"))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId(PLAN_EXECUTION_ID + "1").build())
                        .build());
    when(nodeExecutionService.get(STAGE_EXECUTION_ID + "2")).thenThrow(new InvalidRequestException(""));

    ResourceConstraintExecutionInfoDTO response =
        pmsResourceConstraintService.getResourceConstraintExecutionInfo(ACCOUNT_ID, RESOURCE_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getCapacity()).isEqualTo(resourceConstraint.getCapacity());
    assertThat(response.getName()).isEqualTo(resourceConstraint.getName());
    assertThat(response.getResourceConstraints()).isEmpty();

    verify(resourceRestraintService).getByNameAndAccountId(PmsConstants.QUEUING_RC_NAME, ACCOUNT_ID);
    verify(resourceRestraintInstanceService)
        .getAllByRestraintIdAndResourceUnitAndStates(
            resourceConstraint.getUuid(), RESOURCE_UNIT, Arrays.asList(ACTIVE, BLOCKED));
    verify(planExecutionService).findAllByPlanExecutionIdIn(any());
  }

  private ResourceRestraint getResourceConstraint() {
    return ResourceRestraint.builder()
        .accountId(ACCOUNT_ID)
        .capacity(1)
        .createdAt(System.currentTimeMillis())
        .createdBy(EmbeddedUser.builder().name(ALEXEI).build())
        .harnessOwned(true)
        .name(PmsConstants.QUEUING_RC_NAME)
        .strategy(Strategy.FIFO)
        .uuid(generateUuid())
        .build();
  }
}
