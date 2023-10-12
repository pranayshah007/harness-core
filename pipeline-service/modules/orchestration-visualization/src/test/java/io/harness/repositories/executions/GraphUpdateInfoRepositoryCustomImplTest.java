/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.repositories.executions;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.beans.ExecutionSummaryUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo.GraphUpdateInfoKeys;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class GraphUpdateInfoRepositoryCustomImplTest extends OrchestrationVisualizationTestBase {
  @Inject MongoTemplate mongoTemplate;
  @Inject @InjectMocks GraphUpdateInfoRepositoryCustom graphUpdateInfoRepositoryCustom;

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdate() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Query query = query(where(GraphUpdateInfoKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(GraphUpdateInfoKeys.stepCategory)
                            .is(StepCategory.STAGE)
                            .and(GraphUpdateInfoKeys.nodeExecutionId)
                            .is(nodeExecutionId));
    Update update = new Update().set("executionSummaryUpdateInfo.stageUuid", "stageId");
    assertNull(graphUpdateInfoRepositoryCustom.update(query, update));
    mongoTemplate.save(
        GraphUpdateInfo.builder()
            .planExecutionId(planExecutionId)
            .nodeExecutionId(nodeExecutionId)
            .executionSummaryUpdateInfo(ExecutionSummaryUpdateInfo.builder().stepCategory(StepCategory.STAGE).build())
            .build());
    GraphUpdateInfo graphUpdateInfo = graphUpdateInfoRepositoryCustom.update(query, update);
    assertEquals(graphUpdateInfo.getExecutionSummaryUpdateInfo().getStageUuid(), "stageId");
  }
}
