/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
  @Owner(developers = VIVEK_DIXIT)
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

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFindAll() {
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder().planExecutionId("planExecutionId1").build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder().planExecutionId("planExecutionId2").build());

    Page<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustom.findAll(criteria, PageRequest.of(0, 10));

    assertThat(response.stream().count()).isEqualTo(planExecutionIds.size());
    assertThat(planExecutionIds.contains(response.getContent().get(0).getPlanExecutionId())).isTrue();
    assertThat(planExecutionIds.contains(response.getContent().get(1).getPlanExecutionId())).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFindAllWithProjections() {
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));

    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId1")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId2")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());

    List<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustom
            .findAllWithProjection(criteria, PageRequest.of(0, 10),
                List.of(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.accountId,
                    PlanExecutionSummaryKeys.projectIdentifier))
            .stream()
            .collect(Collectors.toList());

    assertThat((long) response.size()).isEqualTo(planExecutionIds.size());
    assertThat(planExecutionIds.contains(response.get(0).getPlanExecutionId())).isTrue();
    assertThat(planExecutionIds.contains(response.get(1).getPlanExecutionId())).isTrue();

    assertThat(response.get(0).getPlanExecutionId()).isNotNull();
    assertThat(response.get(0).getAccountId()).isNotNull();
    assertThat(response.get(0).getProjectIdentifier()).isNotNull();
    // orgId is null because it was not in the projections.
    assertThat(response.get(0).getOrgIdentifier()).isNull();
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFindAllWithRequiredProjectionUsingAnalyticsNode() {
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));

    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .uuid(generateUuid())
                           .planExecutionId("planExecutionId1")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .pipelineIdentifier("pipelineId")
                           .createdAt(System.currentTimeMillis())
                           .lastUpdatedAt(System.currentTimeMillis())
                           .name("name")
                           .build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .uuid(generateUuid())
                           .planExecutionId("planExecutionId2")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .pipelineIdentifier("pipelineId")
                           .createdAt(System.currentTimeMillis())
                           .lastUpdatedAt(System.currentTimeMillis())
                           .name("name")
                           .build());

    List<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustom
            .findAllWithRequiredProjectionUsingAnalyticsNode(
                criteria, PageRequest.of(0, 10), Collections.singletonList(PlanExecutionSummaryKeys.planExecutionId))
            .stream()
            .collect(Collectors.toList());

    assertThat(response.size()).isEqualTo(planExecutionIds.size());
    assertThat(planExecutionIds.contains(response.get(0).getPlanExecutionId())).isTrue();
    assertThat(planExecutionIds.contains(response.get(1).getPlanExecutionId())).isTrue();

    assertThat(response.get(0).getPlanExecutionId()).isNotNull();
    assertThat(response.get(0).getAccountId()).isNotNull();
    assertThat(response.get(0).getProjectIdentifier()).isNotNull();
    assertThat(response.get(0).getOrgIdentifier()).isNotNull();
    assertThat(response.get(0).getRunSequence()).isNotNull();
    assertThat(response.get(0).getPipelineIdentifier()).isNotNull();
    assertThat(response.get(0).getName()).isNotNull();
    assertThat(response.get(0).getUuid()).isNotNull();
    assertThat(response.get(0).getCreatedAt()).isNotNull();
    assertThat(response.get(0).getLastUpdatedAt()).isNotNull();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetCountOfExecutionSummary() {
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is("planExecutionId1"),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is("planExecutionId2"));
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId1")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId("planExecutionId2")
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .build());

    assertThat(pmsExecutionSummaryRepositoryCustom.getCountOfExecutionSummary(criteria)).isEqualTo(2L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFindListOfRepositories() {
    List<String> planExecutionIds = List.of("planExecutionId1", "planExecutionId2");
    Criteria criteria =
        new Criteria().orOperator(Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(0)),
            Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionIds.get(1)));
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionIds.get(0))
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .orgIdentifier("orgId")
                           .entityGitDetails(EntityGitDetails.builder().repoName("repo1").build())
                           .build());
    mongoTemplate.save(PipelineExecutionSummaryEntity.builder()
                           .planExecutionId(planExecutionIds.get(1))
                           .accountId("accId")
                           .projectIdentifier("projId")
                           .entityGitDetails(EntityGitDetails.builder().repoName("repo1").build())
                           .orgIdentifier("orgId")
                           .build());

    List<PipelineExecutionSummaryEntity> response =
        pmsExecutionSummaryRepositoryCustom.findListOfRepositories(criteria).stream().collect(Collectors.toList());
    assertThat(response.size()).isEqualTo(planExecutionIds.size());
    assertThat(response.get(0).getPlanExecutionId()).isNull();

    assertThat(response.get(0).getEntityGitDetails()).isNotNull();
    assertThat(response.get(0).getEntityGitDetails().getRepoName()).isEqualTo("repo1");
  }
  public static <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}