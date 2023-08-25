/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class PausedStepStatusUpdateTest extends OrchestrationTestBase {
  @Inject PausedStepStatusUpdate stepStatusUpdate;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject MongoTemplate mongoTemplate;
  @Inject Injector injector;

  @Before
  public void setUp() throws Exception {
    NodeExecutionServiceImpl nodeExecutionService =
        (NodeExecutionServiceImpl) injector.getInstance(NodeExecutionService.class);
    nodeExecutionService.getNodeStatusUpdateSubject().register(injector.getInstance(PlanExecutionService.class));
  }

  /**
   * Setup:
   *
   * PlanExecution : RUNNING
   *
   * pipeline (RUNNING)
   *  - stage (RUNNING)
   *    -fork (RUNNING)
   *      -child1 (PAUSED) ----> This gets paused
   *      -child2 (SUCCEEDED)
   *      -child3 (SUCCEEDED)
   *
   * PausedStepStatusUpdate should mark the PlanExecution as PAUSED along with all the parents
   */

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStatusUpdateIntervention() {
    String planExecutionId = generateUuid();
    String pipelineNodeId = generateUuid() + "plNode";
    String stageNodeId = generateUuid() + "stageNode";
    String forkNodeId = generateUuid() + "forkNode";
    String child1Id = generateUuid() + "child1Node";
    String child2Id = generateUuid() + "child2Node";
    String child3Id = generateUuid() + "child3Node";
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    planExecutionMetadataService.save(planExecutionMetadata);

    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder().setPlanExecutionId(planExecutionId);

    PlanNode pipelinePlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("PIPELINE")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.PIPELINE).build())
            .serviceName("CD")
            .build();
    NodeExecution pipelineNode =
        NodeExecution.builder()
            .uuid(pipelineNodeId)
            .status(Status.RUNNING)
            .ambiance(
                ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode)).build())
            .nodeId(pipelinePlanNode.getUuid())
            .name(pipelinePlanNode.getName())
            .stepType(pipelinePlanNode.getStepType())
            .identifier(pipelinePlanNode.getIdentifier())
            .module(pipelinePlanNode.getServiceName())
            .skipGraphType(pipelinePlanNode.getSkipGraphType())
            .build();

    PlanNode stagePlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("STAGE")
            .stepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
            .serviceName("CD")
            .build();
    NodeExecution stageNode =
        NodeExecution.builder()
            .uuid(stageNodeId)
            .status(Status.RUNNING)
            .parentId(pipelineNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .build())
            .nodeId(stagePlanNode.getUuid())
            .name(stagePlanNode.getName())
            .stepType(stagePlanNode.getStepType())
            .identifier(stagePlanNode.getIdentifier())
            .module(stagePlanNode.getServiceName())
            .skipGraphType(stagePlanNode.getSkipGraphType())
            .build();

    PlanNode forkPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("FORK")
            .stepType(StepType.newBuilder().setType("FORK").setStepCategory(StepCategory.FORK).build())
            .serviceName("CD")
            .build();
    NodeExecution forkNode =
        NodeExecution.builder()
            .uuid(forkNodeId)
            .status(Status.RUNNING)
            .parentId(stageNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(forkNodeId, forkPlanNode))
                          .build())
            .nodeId(forkPlanNode.getUuid())
            .name(forkPlanNode.getName())
            .stepType(forkPlanNode.getStepType())
            .identifier(forkPlanNode.getIdentifier())
            .module(forkPlanNode.getServiceName())
            .skipGraphType(forkPlanNode.getSkipGraphType())
            .build();
    // This node is getting transitioned to SUCCEEDED from MANUAL_INTERVENTION
    PlanNode child1Node =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("CHILD1")
            .stepType(StepType.newBuilder().setType("Step").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .build();
    NodeExecution child1 =
        NodeExecution.builder()
            .uuid(child1Id)
            .status(Status.PAUSED)
            .parentId(forkNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(forkNodeId, forkPlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(child1Id, child1Node))
                          .build())
            .nodeId(child1Node.getUuid())
            .name(child1Node.getName())
            .stepType(child1Node.getStepType())
            .identifier(child1Node.getIdentifier())
            .module(child1Node.getServiceName())
            .skipGraphType(child1Node.getSkipGraphType())
            .build();

    PlanNode child2Node =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("CHILD2")
            .stepType(StepType.newBuilder().setType("Step").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .build();
    NodeExecution child2 =
        NodeExecution.builder()
            .uuid(child2Id)
            .status(Status.SUCCEEDED)
            .parentId(forkNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(forkNodeId, forkPlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(child2Id, child2Node))
                          .build())
            .nodeId(child2Node.getUuid())
            .name(child2Node.getName())
            .stepType(child2Node.getStepType())
            .identifier(child2Node.getIdentifier())
            .module(child2Node.getServiceName())
            .skipGraphType(child2Node.getSkipGraphType())
            .build();

    PlanNode child3Node =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("CHILD3")
            .stepType(StepType.newBuilder().setType("Step").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .build();
    NodeExecution child3 =
        NodeExecution.builder()
            .uuid(child3Id)
            .status(Status.SUCCEEDED)
            .parentId(forkNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(forkNodeId, forkPlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(child3Id, child3Node))
                          .build())
            .nodeId(child3Node.getUuid())
            .name(child3Node.getName())
            .stepType(child3Node.getStepType())
            .identifier(child3Node.getIdentifier())
            .module(child3Node.getServiceName())
            .skipGraphType(child3Node.getSkipGraphType())
            .build();

    mongoTemplate.save(planExecution);
    mongoTemplate.save(pipelineNode);
    mongoTemplate.save(stageNode);
    mongoTemplate.save(forkNode);
    mongoTemplate.save(child1);
    mongoTemplate.save(child2);
    mongoTemplate.save(child3);

    stepStatusUpdate.handleNodeStatusUpdate(NodeUpdateInfo.builder().nodeExecution(child1).build());

    PlanExecution updated =
        mongoTemplate.findOne(query(where(PlanExecutionKeys.uuid).is(planExecutionId)), PlanExecution.class);
    assertThat(updated).isNotNull();
    assertThat(updated.getStatus()).isEqualTo(Status.PAUSED);

    NodeExecution pipelineNodeEx =
        mongoTemplate.findOne(query(where(NodeExecutionKeys.uuid).is(pipelineNodeId)), NodeExecution.class);
    assertThat(pipelineNodeEx).isNotNull();
    assertThat(pipelineNodeEx.getStatus()).isEqualTo(Status.PAUSED);

    NodeExecution stageNodeEx =
        mongoTemplate.findOne(query(where(NodeExecutionKeys.uuid).is(stageNodeId)), NodeExecution.class);
    assertThat(stageNodeEx).isNotNull();
    assertThat(stageNodeEx.getStatus()).isEqualTo(Status.PAUSED);

    NodeExecution forkNodeEx =
        mongoTemplate.findOne(query(where(NodeExecutionKeys.uuid).is(forkNodeId)), NodeExecution.class);
    assertThat(forkNodeEx).isNotNull();
    assertThat(forkNodeEx.getStatus()).isEqualTo(Status.PAUSED);
  }

  /**
   * Setup:
   *
   * PlanExecution : INTERVENTION_WAITING
   *
   * pipeline (running)
   *  - stage (running)
   *    -fork (running)
   *      -child1 (PAUSED) ----> This gets paused
   *      -child2 (SUCCEEDED)
   *      -child3 (RUNNING)
   *    -fork (running)
   *      -child4 (running)
   *
   * PausedStepStatusUpdate should mark the Fork1 as Paused others should be running
   */

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStatusUpdateRunning() {
    String planExecutionId = generateUuid();
    String pipelineNodeId = generateUuid() + "plNode";
    String stageNodeId = generateUuid() + "stageNode";
    String forkNodeId = generateUuid() + "forkNode";
    String child1Id = generateUuid() + "child1Node";
    String child2Id = generateUuid() + "child2Node";
    String child3Id = generateUuid() + "child3Node";
    String fork2Id = generateUuid() + "fork2Node";
    String child4Id = generateUuid() + "child4Node";

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    planExecutionMetadataService.save(planExecutionMetadata);
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();

    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder().setPlanExecutionId(planExecutionId);

    PlanNode pipelinePlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("PIPELINE")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.PIPELINE).build())
            .serviceName("CD")
            .build();
    NodeExecution pipelineNode =
        NodeExecution.builder()
            .uuid(pipelineNodeId)
            .status(Status.RUNNING)
            .ambiance(
                ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode)).build())
            .nodeId(pipelinePlanNode.getUuid())
            .name(pipelinePlanNode.getName())
            .stepType(pipelinePlanNode.getStepType())
            .identifier(pipelinePlanNode.getIdentifier())
            .module(pipelinePlanNode.getServiceName())
            .skipGraphType(pipelinePlanNode.getSkipGraphType())
            .build();

    PlanNode stagePlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("STAGE")
            .stepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
            .serviceName("CD")
            .build();

    NodeExecution stageNode =
        NodeExecution.builder()
            .uuid(stageNodeId)
            .status(Status.RUNNING)
            .parentId(pipelineNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .build())
            .nodeId(stagePlanNode.getUuid())
            .name(stagePlanNode.getName())
            .stepType(stagePlanNode.getStepType())
            .identifier(stagePlanNode.getIdentifier())
            .module(stagePlanNode.getServiceName())
            .skipGraphType(stagePlanNode.getSkipGraphType())
            .build();

    PlanNode forkPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("FORK")
            .stepType(StepType.newBuilder().setType("FORK").setStepCategory(StepCategory.FORK).build())
            .serviceName("CD")
            .build();
    NodeExecution forkNode =
        NodeExecution.builder()
            .uuid(forkNodeId)
            .status(Status.RUNNING)
            .parentId(stageNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(forkNodeId, forkPlanNode))
                          .build())
            .nodeId(forkPlanNode.getUuid())
            .name(forkPlanNode.getName())
            .stepType(forkPlanNode.getStepType())
            .identifier(forkPlanNode.getIdentifier())
            .module(forkPlanNode.getServiceName())
            .skipGraphType(forkPlanNode.getSkipGraphType())
            .build();
    // This node is getting transitioned to SUCCEEDED from MANUAL_INTERVENTION
    PlanNode child1Node =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("CHILD1")
            .stepType(StepType.newBuilder().setType("Step").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .build();
    NodeExecution child1 =
        NodeExecution.builder()
            .uuid(child1Id)
            .status(Status.PAUSED)
            .parentId(forkNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(forkNodeId, forkPlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(child1Id, child1Node))
                          .build())
            .nodeId(child1Node.getUuid())
            .name(child1Node.getName())
            .stepType(child1Node.getStepType())
            .identifier(child1Node.getIdentifier())
            .module(child1Node.getServiceName())
            .skipGraphType(child1Node.getSkipGraphType())
            .build();

    PlanNode child2Node =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("CHILD2")
            .stepType(StepType.newBuilder().setType("Step").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .build();
    NodeExecution child2 =
        NodeExecution.builder()
            .uuid(child2Id)
            .status(Status.SUCCEEDED)
            .parentId(forkNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(forkNodeId, forkPlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(child2Id, child2Node))
                          .build())
            .nodeId(child2Node.getUuid())
            .name(child2Node.getName())
            .stepType(child2Node.getStepType())
            .identifier(child2Node.getIdentifier())
            .module(child2Node.getServiceName())
            .skipGraphType(child2Node.getSkipGraphType())
            .build();

    PlanNode child3Node =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("CHILD3")
            .stepType(StepType.newBuilder().setType("Step").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .build();
    NodeExecution child3 =
        NodeExecution.builder()
            .uuid(child3Id)
            .status(Status.SUCCEEDED)
            .parentId(forkNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(forkNodeId, forkPlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(child3Id, child3Node))
                          .build())
            .nodeId(child3Node.getUuid())
            .name(child3Node.getName())
            .stepType(child3Node.getStepType())
            .identifier(child3Node.getIdentifier())
            .module(child3Node.getServiceName())
            .skipGraphType(child3Node.getSkipGraphType())
            .build();

    PlanNode fork2PlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("FORK")
            .stepType(StepType.newBuilder().setType("FORK").setStepCategory(StepCategory.FORK).build())
            .serviceName("CD")
            .build();
    NodeExecution fork2Node =
        NodeExecution.builder()
            .uuid(fork2Id)
            .status(Status.RUNNING)
            .parentId(stageNode.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(fork2Id, fork2PlanNode))
                          .build())
            .nodeId(fork2PlanNode.getUuid())
            .name(fork2PlanNode.getName())
            .stepType(fork2PlanNode.getStepType())
            .identifier(fork2PlanNode.getIdentifier())
            .module(fork2PlanNode.getServiceName())
            .skipGraphType(fork2PlanNode.getSkipGraphType())

            .build();

    PlanNode child4Node =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("CHILD3")
            .stepType(StepType.newBuilder().setType("Step").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .build();
    NodeExecution child4 =
        NodeExecution.builder()
            .uuid(child4Id)
            .status(Status.RUNNING)
            .parentId(fork2Node.getUuid())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(pipelineNodeId, pipelinePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeId, stagePlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(fork2Id, fork2PlanNode))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(child4Id, child4Node))
                          .build())
            .nodeId(child4Node.getUuid())
            .name(child4Node.getName())
            .stepType(child4Node.getStepType())
            .identifier(child4Node.getIdentifier())
            .module(child4Node.getServiceName())
            .skipGraphType(child4Node.getSkipGraphType())
            .build();

    mongoTemplate.save(planExecution);
    mongoTemplate.save(pipelineNode);
    mongoTemplate.save(stageNode);
    mongoTemplate.save(forkNode);
    mongoTemplate.save(child1);
    mongoTemplate.save(child2);
    mongoTemplate.save(child3);
    mongoTemplate.save(fork2Node);
    mongoTemplate.save(child4);

    stepStatusUpdate.handleNodeStatusUpdate(NodeUpdateInfo.builder()
                                                .nodeExecution(child1)

                                                .build());

    PlanExecution updated =
        mongoTemplate.findOne(query(where(PlanExecutionKeys.uuid).is(planExecutionId)), PlanExecution.class);
    assertThat(updated).isNotNull();
    assertThat(updated.getStatus()).isEqualTo(Status.RUNNING);

    NodeExecution pipelineNodeEx =
        mongoTemplate.findOne(query(where(NodeExecutionKeys.uuid).is(pipelineNodeId)), NodeExecution.class);
    assertThat(pipelineNodeEx).isNotNull();
    assertThat(pipelineNodeEx.getStatus()).isEqualTo(Status.RUNNING);

    NodeExecution stageNodeEx =
        mongoTemplate.findOne(query(where(NodeExecutionKeys.uuid).is(stageNodeId)), NodeExecution.class);
    assertThat(stageNodeEx).isNotNull();
    assertThat(stageNodeEx.getStatus()).isEqualTo(Status.RUNNING);

    NodeExecution forkNodeEx =
        mongoTemplate.findOne(query(where(NodeExecutionKeys.uuid).is(forkNodeId)), NodeExecution.class);
    assertThat(forkNodeEx).isNotNull();
    assertThat(forkNodeEx.getStatus()).isEqualTo(Status.PAUSED);
  }
}
