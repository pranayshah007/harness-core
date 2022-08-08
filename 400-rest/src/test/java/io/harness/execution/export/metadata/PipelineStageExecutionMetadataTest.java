/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.metadata;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.sm.StateType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PipelineStageExecutionMetadataTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testAccept() {
    SimpleVisitor simpleVisitor = new SimpleVisitor();
    PipelineStageExecutionMetadata.builder().build().accept(simpleVisitor);
    assertThat(simpleVisitor.getVisited()).isEmpty();

    simpleVisitor = new SimpleVisitor();
    PipelineStageExecutionMetadata.builder()
        .workflowExecution(WorkflowExecutionMetadata.builder()
                               .executionGraph(Collections.singletonList(GraphNodeMetadata.builder().id("id1").build()))
                               .build())
        .build()
        .accept(simpleVisitor);
    assertThat(simpleVisitor.getVisited()).containsExactly("id1");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromPipelineExecution() {
    assertThat(PipelineStageExecutionMetadata.fromPipelineExecution(null)).isNull();
    assertThat(PipelineStageExecutionMetadata.fromPipelineExecution(aPipelineExecution().build())).isNull();

    assertThat(PipelineStageExecutionMetadata.fromPipelineExecution(
                   aPipelineExecution()
                       .withPipeline(Pipeline.builder().build())
                       .withPipelineStageExecutions(Collections.singletonList(PipelineStageExecution.builder().build()))
                       .build()))
        .isNull();

    List<PipelineStageExecutionMetadata> pipelineStageExecutionMetadataList =
        PipelineStageExecutionMetadata.fromPipelineExecution(
            aPipelineExecution()
                .withPipeline(Pipeline.builder()
                                  .pipelineStages(asList(PipelineStage.builder().parallel(true).build(),
                                      PipelineStage.builder().parallel(true).build(), null))
                                  .build())
                .withPipelineStageExecutions(asList(PipelineStageExecution.builder().build(),
                    PipelineStageExecution.builder().build(), PipelineStageExecution.builder().build()))
                .build());

    assertThat(pipelineStageExecutionMetadataList).isNotNull();
    assertThat(pipelineStageExecutionMetadataList.size()).isEqualTo(2);
    assertThat(pipelineStageExecutionMetadataList.get(0).isParallelWithPreviousStage()).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromPipelineStageExecution() {
    assertThat(PipelineStageExecutionMetadata.fromPipelineStageExecution(null, PipelineStage.builder().build(), false))
        .isNull();
    assertThat(PipelineStageExecutionMetadata.fromPipelineStageExecution(
                   PipelineStageExecution.builder().build(), null, false))
        .isNull();

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Instant minuteAgo = now.minus(1, ChronoUnit.MINUTES);
    PipelineStageExecutionMetadata pipelineStageExecutionMetadata =
        PipelineStageExecutionMetadata.fromPipelineStageExecution(
            PipelineStageExecution.builder()
                .stateName("n")
                .stateType(StateType.APPROVAL.name())
                .status(ExecutionStatus.SUCCESS)
                .stateExecutionData(ApprovalStateExecutionData.builder().comments("c").build())
                .startTs(minuteAgo.toEpochMilli())
                .endTs(now.toEpochMilli())
                .build(),
            PipelineStage.builder().name("stg").parallel(false).build(), false);
    assertThat(pipelineStageExecutionMetadata).isNotNull();
    assertThat(pipelineStageExecutionMetadata.getStageName()).isEqualTo("stg");
    assertThat(pipelineStageExecutionMetadata.getName()).isEqualTo("n");
    assertThat(pipelineStageExecutionMetadata.getType()).isEqualTo(StateType.APPROVAL.name());
    assertThat(pipelineStageExecutionMetadata.isParallelWithPreviousStage()).isFalse();
    assertThat(pipelineStageExecutionMetadata.getSkipCondition()).isNull();
    assertThat(pipelineStageExecutionMetadata.getApprovalData()).isNotNull();
    assertThat(pipelineStageExecutionMetadata.getApprovalData().getComments()).isEqualTo("c");
    assertThat(pipelineStageExecutionMetadata.getWorkflowExecution()).isNull();
    assertThat(pipelineStageExecutionMetadata.getTiming()).isNotNull();
    assertThat(pipelineStageExecutionMetadata.getTiming().getStartTime().toInstant()).isEqualTo(minuteAgo);
    assertThat(pipelineStageExecutionMetadata.getTiming().getEndTime().toInstant()).isEqualTo(now);
    assertThat(pipelineStageExecutionMetadata.getTiming().getDuration().toMinutes()).isEqualTo(1);

    pipelineStageExecutionMetadata = PipelineStageExecutionMetadata.fromPipelineStageExecution(
        PipelineStageExecution.builder()
            .stateName("n")
            .stateType(StateType.ENV_STATE.name())
            .status(ExecutionStatus.SUCCESS)
            .workflowExecutions(Collections.singletonList(
                WorkflowExecution.builder().uuid("id").workflowType(WorkflowType.ORCHESTRATION).build()))
            .stateExecutionData(ApprovalStateExecutionData.builder().comments("c").build())
            .build(),
        PipelineStage.builder().parallel(true).build(), true);
    assertThat(pipelineStageExecutionMetadata).isNotNull();
    assertThat(pipelineStageExecutionMetadata.getName()).isEqualTo("n");
    assertThat(pipelineStageExecutionMetadata.getType()).isEqualTo("WORKFLOW_EXECUTION");
    assertThat(pipelineStageExecutionMetadata.isParallelWithPreviousStage()).isFalse();
    assertThat(pipelineStageExecutionMetadata.getSkipCondition()).isNull();
    assertThat(pipelineStageExecutionMetadata.getApprovalData()).isNull();
    assertThat(pipelineStageExecutionMetadata.getWorkflowExecution()).isNotNull();
    assertThat(pipelineStageExecutionMetadata.getWorkflowExecution().getId()).isEqualTo("id");
    assertThat(pipelineStageExecutionMetadata.getTiming()).isNull();
  }
}
