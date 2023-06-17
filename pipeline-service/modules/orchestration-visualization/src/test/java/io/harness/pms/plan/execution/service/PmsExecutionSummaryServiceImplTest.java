/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.category.element.UnitTests;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.utils.OrchestrationVisualisationTestHelper;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsExecutionSummaryServiceImplTest extends OrchestrationVisualizationTestBase {
  @Mock PmsExecutionSummaryRepository pmsExecutionSummaryRepositoryMock;
  @Inject PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsGraphStepDetailsService pmsGraphStepDetailsService;
  @InjectMocks PmsExecutionSummaryServiceImpl pmsExecutionSummaryService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummary() {
    String projectId = "projectId";
    String planExecutionId = "planExecutionId";
    String accountId = "accountId";
    String orgId = "orgId";
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .planExecutionId(planExecutionId)
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgId)
                                                                        .projectIdentifier(projectId)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionSummaryRepositoryMock)
        .getPipelineExecutionSummaryWithProjections(any(),
            eq(Sets.newHashSet(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.planExecutionId,
                PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.projectIdentifier)));
    assertEquals(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(planExecutionId,
                     Sets.newHashSet(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.planExecutionId,
                         PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.projectIdentifier)),
        pipelineExecutionSummaryEntity);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateStrategyNode() {
    Update update = new Update();
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder().concurrentChildInstance(ConcurrentChildInstance.builder().build()).build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .executableResponse(ExecutableResponse.newBuilder().build())
            .ambiance(Ambiance.newBuilder()
                          .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                          .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .build();
    pmsExecutionSummaryService.updateStrategyPlanNode("planExecution", nodeExecution, update);
    verify(pmsGraphStepDetailsService, times(0)).fetchConcurrentChildInstance(nodeExecution.getUuid());
    nodeExecution =
        NodeExecution.builder()
            .ambiance(
                Ambiance.newBuilder()
                    .addLevels(Level.newBuilder().setGroup("STAGES").setNodeType(NodeType.PLAN_NODE.name()).build())
                    .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                    .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
            .planNode(PlanNode.builder().build())
            .build();
    pmsExecutionSummaryService.updateStrategyPlanNode("planExecution", nodeExecution, update);
    verify(pmsGraphStepDetailsService, times(1)).fetchConcurrentChildInstance(nodeExecution.getUuid());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchPlanExecutionIdsFromAnalytics() {
    String projectId = "projectId";
    String pipelineId = "pipelineId";
    String accountId = "accountId";
    String orgId = "orgId";

    doReturn(OrchestrationVisualisationTestHelper.createCloseableIterator(Collections.emptyListIterator()))
        .when(pmsExecutionSummaryRepositoryMock)
        .fetchExecutionSummaryEntityFromAnalytics(any());
    List<PipelineExecutionSummaryEntity> executionSummaryEntities = new LinkedList<>();
    pmsExecutionSummaryService.fetchPlanExecutionIdsFromAnalytics(accountId, orgId, projectId, pipelineId);
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionSummaryKeys.orgIdentifier)
                            .is(orgId)
                            .and(PlanExecutionSummaryKeys.projectIdentifier)
                            .is(projectId)
                            .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                            .is(pipelineId);
    Query query = new Query(criteria);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    verify(pmsExecutionSummaryRepositoryMock, times(1)).fetchExecutionSummaryEntityFromAnalytics(query);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateStageOfIdentityType() {
    String stageSetupIdForStrategy = "stageSetupId";
    String strategyNodeExecutionId = "strategyNodeExecutionId";
    String runtimeId = "runtimeId";
    String planExecutionId = "planExecutionId";
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder()
                                           .setNodeType(NodeType.PLAN_NODE.name())
                                           .setRuntimeId(runtimeId)
                                           .setSetupId("setupId")
                                           .build())
                            .build();

    List<NodeExecution> nodeExecutions = new ArrayList<>();

    nodeExecutions.add(NodeExecution.builder()
                           .uuid("nodeExecutionId1")
                           .endTs(1000L)
                           .planNode(PlanNode.builder().build())
                           .status(Status.SUCCEEDED)
                           .nodeId("stageNodeIdWithoutStrategy")
                           .parentId("parentNodeExecutionId")
                           .ambiance(ambiance)
                           .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build());

    nodeExecutions.add(NodeExecution.builder()
                           .uuid("nodeExecutionId2")
                           .endTs(1000L)
                           .planNode(PlanNode.builder().build())
                           .status(Status.SUCCEEDED)
                           .nodeId(stageSetupIdForStrategy)
                           .parentId(strategyNodeExecutionId)
                           .ambiance(ambiance)
                           .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build());

    nodeExecutions.add(
        NodeExecution.builder()
            .uuid("nodeExecutionId3")
            .parentId(strategyNodeExecutionId)
            .endTs(1000L)
            .nodeId(stageSetupIdForStrategy)
            .status(Status.SUCCEEDED)
            .ambiance(
                ambiance.toBuilder()
                    .addLevels(Level.newBuilder()
                                   .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                   .setRuntimeId(runtimeId)
                                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                   .build())
                    .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
            .build());

    nodeExecutions.add(
        NodeExecution.builder()
            .uuid("stepStrategyNodeExecutionId")
            .nodeId("stepStrategyNodeId")
            .parentId("stepsNodeExecutionId")
            .endTs(1000L)
            .status(Status.SUCCEEDED)
            .nodeId("stepNodeId")
            .ambiance(
                ambiance.toBuilder()
                    .addLevels(Level.newBuilder()
                                   .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                                   .setRuntimeId(runtimeId)
                                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                   .build())
                    .build())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
            .build());

    Ambiance ambianceForStageStrategy =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).setSetupId("setupId").setGroup("STAGES").build())
            .addLevels(Level.newBuilder()
                           .setNodeType(NodeType.IDENTITY_PLAN_NODE.name())
                           .setRuntimeId(runtimeId)
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                           .build())
            .build();

    nodeExecutions.add(NodeExecution.builder()
                           .uuid(strategyNodeExecutionId)
                           .nodeId("strategyNodeId")
                           .parentId("stageNodeExecutionId")
                           .endTs(1000L)
                           .status(Status.SUCCEEDED)
                           .ambiance(ambianceForStageStrategy)
                           .executableResponses(Collections.singleton(
                               ExecutableResponse.newBuilder()
                                   .setChildren(ChildrenExecutableResponse.newBuilder().setMaxConcurrency(3).build())
                                   .build()))
                           .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                           .build());

    doReturn(ConcurrentChildInstance.builder().childrenNodeExecutionIds(Collections.singletonList("randomId")).build())
        .when(pmsGraphStepDetailsService)
        .fetchConcurrentChildInstance(strategyNodeExecutionId);
    doReturn(nodeExecutions)
        .when(nodeExecutionService)
        .fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId);

    doReturn(Optional.of(
                 PipelineExecutionSummaryEntity.builder()
                     .layoutNodeMap(Map.of("strategyNodeId",
                         GraphLayoutNodeDTO.builder()
                             .nodeType(StrategyType.PARALLELISM.name())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build(),
                         stageSetupIdForStrategy,
                         GraphLayoutNodeDTO.builder()
                             .nodeType("STAGE")
                             .nodeGroup("stage")
                             .module("pms")
                             .edgeLayoutList(EdgeLayoutListDTO.builder().build())
                             .skipInfo(SkipInfo.newBuilder().build())
                             .nodeRunInfo(NodeRunInfo.newBuilder().build())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build()

                             ))
                     .build()))
        .when(pmsExecutionSummaryRepositoryMock)
        .findByPlanExecutionId(any());

    Update update = new Update();
    pmsExecutionSummaryService.updateIdentityStageOrStrategyNodes(planExecutionId, update);
    // Since returned pipelineExecutionSummary.layoutNode had the strategy node with type parallelism. So maxConcurrency
    // will not be present in update.
    assertEquals(update.toString(),
        "{ \"$set\" : { \"layoutNodeMap.setupId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.setupId.endTs\" : 1000, \"layoutNodeMap.stageSetupId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.stageSetupId.moduleInfo.stepParameters\" : null, \"layoutNodeMap.stageSetupId.startTs\" : null, \"layoutNodeMap.stageSetupId.endTs\" : 1000, \"layoutNodeMap.nodeExecutionId3.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.nodeExecutionId3.endTs\" : 1000, \"layoutNodeMap.strategyNodeId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.strategyNodeId.moduleInfo.stepParameters\" : null, \"layoutNodeMap.strategyNodeId.startTs\" : null, \"layoutNodeMap.strategyNodeId.endTs\" : 1000, \"layoutNodeMap.nodeExecutionId2.nodeType\" : \"STAGE\", \"layoutNodeMap.nodeExecutionId2.nodeGroup\" : \"stage\", \"layoutNodeMap.nodeExecutionId2.edgeLayoutList\" : { \"$java\" : EdgeLayoutListDTO(currentNodeChildren=[], nextIds=null) }, \"layoutNodeMap.nodeExecutionId2.skipInfo\" : { \"$java\" :  }, \"layoutNodeMap.nodeExecutionId2.nodeUuid\" : \"stageSetupId\", \"layoutNodeMap.nodeExecutionId2.executionInputConfigured\" : null, \"layoutNodeMap.nodeExecutionId3.nodeType\" : \"STAGE\", \"layoutNodeMap.nodeExecutionId3.nodeGroup\" : \"stage\", \"layoutNodeMap.nodeExecutionId3.edgeLayoutList\" : { \"$java\" : EdgeLayoutListDTO(currentNodeChildren=[], nextIds=null) }, \"layoutNodeMap.nodeExecutionId3.skipInfo\" : { \"$java\" :  }, \"layoutNodeMap.nodeExecutionId3.nodeUuid\" : \"stageSetupId\", \"layoutNodeMap.nodeExecutionId3.executionInputConfigured\" : null }, \"$addToSet\" : { \"layoutNodeMap.strategyNodeId.edgeLayoutList.currentNodeChildren\" : { \"$java\" : { \"$each\" : [ \"nodeExecutionId2\", \"nodeExecutionId3\" ] } } } }");

    doReturn(Optional.of(
                 PipelineExecutionSummaryEntity.builder()
                     .layoutNodeMap(Map.of("strategyNodeId",
                         GraphLayoutNodeDTO.builder()
                             .nodeType(StrategyType.MATRIX.name())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build(),
                         stageSetupIdForStrategy,
                         GraphLayoutNodeDTO.builder()
                             .nodeType("STAGE")
                             .nodeGroup("stage")
                             .module("pms")
                             .edgeLayoutList(EdgeLayoutListDTO.builder().build())
                             .skipInfo(SkipInfo.newBuilder().build())
                             .nodeRunInfo(NodeRunInfo.newBuilder().build())
                             .edgeLayoutList(EdgeLayoutListDTO.builder().currentNodeChildren(new ArrayList<>()).build())
                             .build()

                             ))
                     .build()))
        .when(pmsExecutionSummaryRepositoryMock)
        .findByPlanExecutionId(any());

    update = new Update();
    pmsExecutionSummaryService.updateIdentityStageOrStrategyNodes(planExecutionId, update);
    // Since returned pipelineExecutionSummary.layoutNode had the strategy node with type Matrix. So maxConcurrency will
    // be present in update.
    assertEquals(update.toString(),
        "{ \"$set\" : { \"layoutNodeMap.setupId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.setupId.endTs\" : 1000, \"layoutNodeMap.stageSetupId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.stageSetupId.moduleInfo.stepParameters\" : null, \"layoutNodeMap.stageSetupId.startTs\" : null, \"layoutNodeMap.stageSetupId.endTs\" : 1000, \"layoutNodeMap.nodeExecutionId3.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.nodeExecutionId3.endTs\" : 1000, \"layoutNodeMap.strategyNodeId.moduleInfo.maxConcurrency.value\" : 3, \"layoutNodeMap.strategyNodeId.status\" : { \"$java\" : SUCCESS }, \"layoutNodeMap.strategyNodeId.moduleInfo.stepParameters\" : null, \"layoutNodeMap.strategyNodeId.startTs\" : null, \"layoutNodeMap.strategyNodeId.endTs\" : 1000, \"layoutNodeMap.nodeExecutionId2.nodeType\" : \"STAGE\", \"layoutNodeMap.nodeExecutionId2.nodeGroup\" : \"stage\", \"layoutNodeMap.nodeExecutionId2.edgeLayoutList\" : { \"$java\" : EdgeLayoutListDTO(currentNodeChildren=[], nextIds=null) }, \"layoutNodeMap.nodeExecutionId2.skipInfo\" : { \"$java\" :  }, \"layoutNodeMap.nodeExecutionId2.nodeUuid\" : \"stageSetupId\", \"layoutNodeMap.nodeExecutionId2.executionInputConfigured\" : null, \"layoutNodeMap.nodeExecutionId3.nodeType\" : \"STAGE\", \"layoutNodeMap.nodeExecutionId3.nodeGroup\" : \"stage\", \"layoutNodeMap.nodeExecutionId3.edgeLayoutList\" : { \"$java\" : EdgeLayoutListDTO(currentNodeChildren=[], nextIds=null) }, \"layoutNodeMap.nodeExecutionId3.skipInfo\" : { \"$java\" :  }, \"layoutNodeMap.nodeExecutionId3.nodeUuid\" : \"stageSetupId\", \"layoutNodeMap.nodeExecutionId3.executionInputConfigured\" : null }, \"$addToSet\" : { \"layoutNodeMap.strategyNodeId.edgeLayoutList.currentNodeChildren\" : { \"$java\" : { \"$each\" : [ \"nodeExecutionId2\", \"nodeExecutionId3\" ] } } } }");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteAllSummaryForGivenPlanExecutionIds() {
    String projectId = "projectId";
    String planExecutionId = "planExecutionId";
    String accountId = "accountId";
    String orgId = "orgId";
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .planExecutionId(planExecutionId)
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgId)
                                                                        .projectIdentifier(projectId)
                                                                        .build();

    pmsExecutionSummaryRepository.save(pipelineExecutionSummaryEntity);
    on(pmsExecutionSummaryService).set("pmsExecutionSummaryRepository", pmsExecutionSummaryRepository);

    pmsExecutionSummaryService.deleteAllSummaryForGivenPlanExecutionIds(Sets.newHashSet(planExecutionId));

    PipelineExecutionSummaryEntity pipelineExecutionSummaryWithProjections =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
            planExecutionId, Sets.newHashSet(PlanExecutionSummaryKeys.accountId));

    assertThat(pipelineExecutionSummaryWithProjections).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testHandleNodeExecutionUpdateFromGraphUpdateForPRBStage() {
    Ambiance basicAmbiance =
        Ambiance.newBuilder().addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build()).build();
    StepType prbStepType =
        StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("PIPELINE_ROLLBACK_STAGE").build();
    String prevStageId = "prevStageId";
    String prevStageNodeId = "prevStageNodeId";
    String nodeId = "nodeId";
    NodeExecution currentNodeExecution = NodeExecution.builder()
                                             .ambiance(basicAmbiance)
                                             .stepType(prbStepType)
                                             .previousId(prevStageId)
                                             .nodeId(nodeId)
                                             .status(RUNNING)
                                             .build();
    NodeExecution prevNodeExecution = NodeExecution.builder().nodeId(prevStageNodeId).build();
    doReturn(prevNodeExecution).when(nodeExecutionService).get(prevStageId);
    Update update = new Update();
    pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(null, currentNodeExecution, update);
    Document updateObject = update.getUpdateObject();
    assertThat(updateObject).hasSize(1);
    Document setObjects = (Document) updateObject.get("$set");
    String expectedKey = "layoutNodeMap.prevStageNodeId.edgeLayoutList.nextIds";
    assertThat(setObjects).containsKey(expectedKey);
    assertThat(setObjects.get(expectedKey)).isEqualTo(Collections.singletonList("nodeId"));
  }
}
