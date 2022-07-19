/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.mongo.index.MongoIndex;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.rule.Owner;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineExecutionSummaryTest extends OrchestrationVisualizationTestBase {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testIsLatestExecution() {
    PipelineExecutionSummaryEntity.PipelineExecutionSummaryEntityBuilder entityBuilder =
        PipelineExecutionSummaryEntity.builder();
    // case1: entity does not contain isLatest
    assertThat(entityBuilder.build().isLatestExecution()).isTrue();

    // case2: entity contains isLatest as false
    PipelineExecutionSummaryEntity entity = entityBuilder.isLatestExecution(false).build();
    assertThat(entity.isLatestExecution()).isEqualTo(false);

    // case3: entity contains isLatest as false
    entity = entityBuilder.isLatestExecution(false).build();
    assertThat(entity.isLatestExecution()).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetRetryExecutionMetadata() {
    PipelineExecutionSummaryEntity.PipelineExecutionSummaryEntityBuilder entityBuilder =
        PipelineExecutionSummaryEntity.builder().planExecutionId("planId");

    PipelineExecutionSummaryEntity entity;

    // case1: retryExecutionMetadata as null
    entity = entityBuilder.build();
    assertThat(entity.getRetryExecutionMetadata()).isNotNull();
    assertThat(entity.getRetryExecutionMetadata().getRootExecutionId()).isEqualTo("planId");
    assertThat(entity.getRetryExecutionMetadata().getParentExecutionId()).isEqualTo("planId");

    // case2: retryExecutionMetadata is not null
    RetryExecutionMetadata retryExecutionMetadata =
        RetryExecutionMetadata.builder().rootExecutionId("rootId").parentExecutionId("parentId").build();
    entity = entityBuilder.retryExecutionMetadata(retryExecutionMetadata).build();
    assertThat(entity.getRetryExecutionMetadata()).isEqualTo(retryExecutionMetadata);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStatus() {
    PipelineExecutionSummaryEntity.PipelineExecutionSummaryEntityBuilder entityBuilder =
        PipelineExecutionSummaryEntity.builder().status(ExecutionStatus.EXPIRED);

    PipelineExecutionSummaryEntity entity;

    // case1: internal status is null
    entity = entityBuilder.build();
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.EXPIRED);

    // case2: internal status is No-op
    entity = entityBuilder.internalStatus(Status.NO_OP).build();
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.NOTSTARTED);

    // case3: internal status is  ABORTED
    entity = entityBuilder.internalStatus(Status.ABORTED).build();
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.ABORTED);

    // case4: internal status is Success
    entity = entityBuilder.internalStatus(Status.SUCCEEDED).build();
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testIsStagesExecutionAllowed() {
    PipelineExecutionSummaryEntity.PipelineExecutionSummaryEntityBuilder entityBuilder =
        PipelineExecutionSummaryEntity.builder();

    PipelineExecutionSummaryEntity entity;

    // allowStageExecution is null
    entity = entityBuilder.build();
    assertThat(entity.isStagesExecutionAllowed()).isFalse();

    // allowStageExecution is true
    entity = entityBuilder.allowStagesExecution(true).build();
    assertThat(entity.isStagesExecutionAllowed()).isTrue();

    // allowStageExecution is false
    entity = entityBuilder.allowStagesExecution(false).build();
    assertThat(entity.isStagesExecutionAllowed()).isFalse();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestMongoIndexes() {
    List<MongoIndex> mongoIndices = PipelineExecutionSummaryEntity.mongoIndexes();

    assertThat(mongoIndices.size()).isEqualTo(7);
    assertThat(mongoIndices.stream().map(MongoIndex::getName).collect(Collectors.toSet()).size()).isEqualTo(7);
  }
}
