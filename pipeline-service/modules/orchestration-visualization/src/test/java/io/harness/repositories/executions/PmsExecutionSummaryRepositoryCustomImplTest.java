/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsExecutionSummaryRepositoryCustomImplTest extends OrchestrationVisualizationTestBase {
  @Inject MongoTemplate mongoTemplate;

  @Inject @InjectMocks PmsExecutionSummaryRepositoryCustom pmsExecutionSummaryRepositoryCustom;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdate() {
    String planExecutionId = generateUuid();
    Query query = query(where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId));
    Update update = new Update().set("status", ExecutionStatus.FAILED);
    assertNull(pmsExecutionSummaryRepositoryCustom.update(query, update));
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionId)
                           .status(ExecutionStatus.SKIPPED)
                           .build());
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionSummaryRepositoryCustom.update(query, update);
    assertEquals(pipelineExecutionSummaryEntity.getStatus(), ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testDeleteAllExecutionsWhenPipelineDeleted() {
    String planExecutionId = generateUuid();
    Query query = query(where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId));
    Update update = new Update().set("pipelineDeleted", Boolean.TRUE);
    UpdateResult updateResult =
        pmsExecutionSummaryRepositoryCustom.deleteAllExecutionsWhenPipelineDeleted(query, update);
    assertEquals(updateResult.getMatchedCount(), 0);
    assertEquals(updateResult.getModifiedCount(), 0);
    assertNull(updateResult.getUpsertedId());
    assertTrue(updateResult.wasAcknowledged());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionId)
                           .status(ExecutionStatus.SKIPPED)
                           .build());
    UpdateResult updateResult1 =
        pmsExecutionSummaryRepositoryCustom.deleteAllExecutionsWhenPipelineDeleted(query, update);
    assertEquals(updateResult1.getMatchedCount(), 1);
    assertEquals(updateResult1.getModifiedCount(), 1);
    assertTrue(updateResult1.wasAcknowledged());
    assertNull(updateResult1.getUpsertedId());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchRootRetryExecutionId() {
    String planExecutionId = generateUuid();
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionId)
                           .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("root").build())
                           .build());
    assertEquals(pmsExecutionSummaryRepositoryCustom.fetchRootRetryExecutionId(planExecutionId), "root");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchPipelineSummaryEntityFromRootParentId() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .pipelineIdentifier("test")
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("root").build())
            .status(ExecutionStatus.SKIPPED)
            .build();
    mongoTemplate.save(pipelineExecutionSummaryEntity);
    try (
        CloseableIterator<PipelineExecutionSummaryEntity> iterator =
            pmsExecutionSummaryRepositoryCustom.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo("root")) {
      int count = 0;
      while (iterator.hasNext()) {
        count++;
        iterator.next();
      }
      assertEquals(count, 1);
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummaryWithProjections() {
    String planExecutionId = generateUuid();
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionId)
                           .status(ExecutionStatus.SKIPPED)
                           .build());
    PipelineExecutionSummaryEntity entity =
        pmsExecutionSummaryRepositoryCustom.getPipelineExecutionSummaryWithProjections(
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId),
            Sets.newHashSet(PlanExecutionSummaryKeys.planExecutionId));
    assertThat(entity).isNotNull();
    assertThat(entity.getStatus()).isNull();
    assertThat(entity.getPlanExecutionId()).isEqualTo(planExecutionId);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFindListOfBranches() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .pipelineIdentifier("test")
            .accountId("account")
            .orgIdentifier("orgIdentifier")
            .projectIdentifier("projectIdentifier")
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("root").build())
            .entityGitDetails(EntityGitDetails.builder().branch("main").repoName("test-repo").build())
            .status(ExecutionStatus.SKIPPED)
            .build();
    mongoTemplate.save(pipelineExecutionSummaryEntity);

    Criteria criteria = new Criteria();
    criteria.and(PlanExecutionSummaryKeys.accountId).is("account");
    criteria.and(PlanExecutionSummaryKeys.orgIdentifier).is("orgIdentifier");
    criteria.and(PlanExecutionSummaryKeys.projectIdentifier).is("projectIdentifier");
    criteria.and(PlanExecutionSummaryKeys.pipelineIdentifier).is("test");
    criteria.and(PlanExecutionSummaryKeys.entityGitDetailsRepoName).is("test-repo");

    try (CloseableIterator<PipelineExecutionSummaryEntity> iterator =
             pmsExecutionSummaryRepositoryCustom.findListOfBranches(criteria)) {
      int count = 0;
      while (iterator.hasNext()) {
        count++;
        iterator.next();
      }
      assertEquals(count, 1);
    }
  }
}
