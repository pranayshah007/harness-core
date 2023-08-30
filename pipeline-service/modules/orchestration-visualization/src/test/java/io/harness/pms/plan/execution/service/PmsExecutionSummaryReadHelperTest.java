/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PIPELINE)
public class PmsExecutionSummaryReadHelperTest extends OrchestrationVisualizationTestBase {
  @Inject PmsExecutionSummaryReadHelper pmsExecutionSummaryReadHelper;
  @Inject MongoTemplate mongoTemplate;
  PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity1;
  PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity2;
  PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity3;
  Query query;
  String REPO_NAME = "repo1";
  String BRANCH_NAME = "branch1";

  @Before
  public void setup() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    pipelineExecutionSummaryEntity1 =
        PipelineExecutionSummaryEntity.builder()
            .uuid(generateUuid())
            .accountId(ACCOUNT_ID)
            .projectIdentifier(PROJECT_ID)
            .orgIdentifier(ORG_ID)
            .pipelineIdentifier("pip1")
            .planExecutionId("planExecutionId1")
            .entityGitDetails(EntityGitDetails.builder().branch(BRANCH_NAME).repoName(REPO_NAME).build())
            .build();
    pipelineExecutionSummaryEntity2 =
        PipelineExecutionSummaryEntity.builder()
            .uuid(generateUuid())
            .accountId(ACCOUNT_ID)
            .projectIdentifier(PROJECT_ID)
            .orgIdentifier(ORG_ID)
            .pipelineIdentifier("pip2")
            .planExecutionId("planExecutionId2")
            .entityGitDetails(EntityGitDetails.builder().branch(BRANCH_NAME).repoName(REPO_NAME).build())
            .build();
    pipelineExecutionSummaryEntity3 =
        PipelineExecutionSummaryEntity.builder()
            .uuid(generateUuid())
            .accountId(ACCOUNT_ID)
            .projectIdentifier(PROJECT_ID)
            .orgIdentifier(ORG_ID)
            .pipelineIdentifier("pip1")
            .planExecutionId("planExecutionId3")
            .entityGitDetails(EntityGitDetails.builder().branch("branch2").repoName("repo2").build())
            .build();
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.accountId)
                            .is(ACCOUNT_ID)
                            .and(PlanExecutionSummaryKeys.orgIdentifier)
                            .is(ORG_ID)
                            .and(PlanExecutionSummaryKeys.projectIdentifier)
                            .is(PROJECT_ID)
                            .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                            .is("pip1");
    query = new Query(criteria);
    mongoTemplate.save(pipelineExecutionSummaryEntity1);
    mongoTemplate.save(pipelineExecutionSummaryEntity3);
    mongoTemplate.save(pipelineExecutionSummaryEntity2);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFindCount() {
    long count = pmsExecutionSummaryReadHelper.findCount(query);
    assertEquals(count, 2);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFind() {
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityList = pmsExecutionSummaryReadHelper.find(query);
    assertEquals(pipelineExecutionSummaryEntityList.size(), 2);
    assertThat(pipelineExecutionSummaryEntityList).contains(pipelineExecutionSummaryEntity1);
    assertThat(pipelineExecutionSummaryEntityList).contains(pipelineExecutionSummaryEntity3);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchExecutionSummaryEntityFromAnalytics() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityList = new LinkedList<>();
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.accountId)
                            .is(ACCOUNT_ID)
                            .and(PlanExecutionSummaryKeys.orgIdentifier)
                            .is(ORG_ID)
                            .and(PlanExecutionSummaryKeys.projectIdentifier)
                            .is(PROJECT_ID)
                            .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                            .is("pip1");
    Query q = new Query(criteria);
    q.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    try (CloseableIterator<PipelineExecutionSummaryEntity> iterator =
             pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromAnalytics(q)) {
      while (iterator.hasNext()) {
        pipelineExecutionSummaryEntityList.add(iterator.next());
      }
    }
    assertThat(pipelineExecutionSummaryEntityList.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchExecutionSummaryEntityFromSecondary() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityList = new LinkedList<>();
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.accountId)
                            .is(ACCOUNT_ID)
                            .and(PlanExecutionSummaryKeys.orgIdentifier)
                            .is(ORG_ID)
                            .and(PlanExecutionSummaryKeys.projectIdentifier)
                            .is(PROJECT_ID)
                            .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                            .is("pip1");
    Query q = new Query(criteria);
    q.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    try (CloseableIterator<PipelineExecutionSummaryEntity> iterator =
             pmsExecutionSummaryReadHelper.fetchExecutionSummaryEntityFromSecondary(q)) {
      while (iterator.hasNext()) {
        pipelineExecutionSummaryEntityList.add(iterator.next());
      }
    }
    assertThat(pipelineExecutionSummaryEntityList.size()).isEqualTo(2);
  }
}
