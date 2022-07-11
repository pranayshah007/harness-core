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
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class TerminalStepStatusUpdateTest extends OrchestrationTestBase {
  @Inject TerminalStepStatusUpdate stepStatusUpdate;
  @Inject MongoTemplate mongoTemplate;

  /**
   * Setup:
   *
   * PlanExecution : RUNNING
   *
   * pipeline (RUNNING)
   *  - stage (RUNNING)
   *    -fork (RUNNING)
   *      -child1 (SUCCEEDED) ----> transitioning from RUNNING --> SUCCEEDED
   *      -child2 (SUCCEEDED)
   *      -child3 (SUCCEEDED)
   *
   * TerminalStepStatusUpdate should mark the PlanExecution as Running when child1 Transitions
   */

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStatusUpdateIntervention() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();

    NodeExecution pipelineNode =
        NodeExecution.builder().uuid("pipelineNode").status(Status.RUNNING).ambiance(ambiance).build();
    NodeExecution stageNode = NodeExecution.builder()
                                  .uuid("stageNode")
                                  .status(Status.RUNNING)
                                  .parentId(pipelineNode.getUuid())
                                  .ambiance(ambiance)
                                  .build();
    NodeExecution forkNode = NodeExecution.builder()
                                 .uuid("forkNode")
                                 .status(Status.RUNNING)
                                 .parentId(stageNode.getUuid())
                                 .ambiance(ambiance)
                                 .build();
    // This node is getting transitioned to SUCCEEDED from MANUAL_INTERVENTION
    NodeExecution child1 = NodeExecution.builder()
                               .uuid("child1")
                               .status(Status.SUCCEEDED)
                               .parentId(forkNode.getUuid())
                               .ambiance(ambiance)
                               .build();
    NodeExecution child2 = NodeExecution.builder()
                               .uuid("child2")
                               .status(Status.SUCCEEDED)
                               .parentId(forkNode.getUuid())
                               .ambiance(ambiance)
                               .build();
    NodeExecution child3 = NodeExecution.builder()
                               .uuid("child3")
                               .status(Status.SUCCEEDED)
                               .parentId(forkNode.getUuid())
                               .ambiance(ambiance)
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
    assertThat(updated.getStatus()).isEqualTo(Status.RUNNING);
  }

  /**
   * Setup:
   *
   * PlanExecution : INTERVENTION_WAITING
   *
   * pipeline (running)
   *  - stage (running)
   *    -fork (running)
   *      -child1 (SUCCEEDED) ----> transitioning from INTERVENTION_WAITING --> SUCCEEDED via MARK_SUCCESS
   *      -child2 (SUCCEEDED)
   *      -child3 (RUNNING)
   *
   * TerminalStepStatusUpdate should mark the PlanExecution as Running when child1 Transitions
   */

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  @Ignore("Will enable this test after completing TerminalStepStatusUpdate implementation.")
  public void shouldTestStatusUpdateRunning() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution =
        PlanExecution.builder().uuid(planExecutionId).status(Status.INTERVENTION_WAITING).build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();

    NodeExecution pipelineNode =
        NodeExecution.builder().uuid("pipelineNode").status(Status.RUNNING).ambiance(ambiance).build();
    NodeExecution stageNode = NodeExecution.builder()
                                  .uuid("stageNode")
                                  .status(Status.RUNNING)
                                  .parentId(pipelineNode.getUuid())
                                  .ambiance(ambiance)
                                  .build();
    NodeExecution forkNode = NodeExecution.builder()
                                 .uuid("forkNode")
                                 .status(Status.RUNNING)
                                 .parentId(stageNode.getUuid())
                                 .ambiance(ambiance)
                                 .build();
    // This node is getting transitioned to SUCCEEDED from MANUAL_INTERVENTION
    NodeExecution child1 = NodeExecution.builder()
                               .uuid("child1")
                               .status(Status.SUCCEEDED)
                               .parentId(forkNode.getUuid())
                               .ambiance(ambiance)
                               .build();
    NodeExecution child2 = NodeExecution.builder()
                               .uuid("child2")
                               .status(Status.SUCCEEDED)
                               .parentId(forkNode.getUuid())
                               .ambiance(ambiance)
                               .build();
    NodeExecution child3 = NodeExecution.builder()
                               .uuid("child3")
                               .status(Status.RUNNING)
                               .parentId(forkNode.getUuid())
                               .ambiance(ambiance)
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
    assertThat(updated.getStatus()).isEqualTo(Status.RUNNING);
  }
}
