/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.repositories.executions.PmsExecutionSummaryRespository;
import io.harness.rule.Owner;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class PipelineStatusUpdateEventHandlerTest extends PipelineServiceTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PmsExecutionSummaryRespository pmsExecutionSummaryRepository;
  private PipelineStatusUpdateEventHandler pipelineStatusUpdateEventHandler;

  @Before
  public void setUp() throws Exception {
    pipelineStatusUpdateEventHandler =
        new PipelineStatusUpdateEventHandler(planExecutionService, pmsExecutionSummaryRepository, null);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnPlanStatusUpdate() {
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).status(Status.SUCCEEDED).endTs(11L).build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build();

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);

    when(planExecutionService.get(anyString())).thenReturn(planExecution);

    when(pmsExecutionSummaryRepository.update(queryArgumentCaptor.capture(), updateArgumentCaptor.capture()))
        .thenReturn(null);

    pipelineStatusUpdateEventHandler.onPlanStatusUpdate(ambiance);

    Query query = queryArgumentCaptor.getValue();
    assertThat(query).isNotNull();
    assertThat(query.getQueryObject())
        .isEqualTo(new Document().append("planExecutionId", ambiance.getPlanExecutionId()));

    Update update = updateArgumentCaptor.getValue();
    assertThat(update).isNotNull();
    assertThat(update.getUpdateObject())
        .isEqualTo(new Document().append("$set",
            new Document()
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.internalStatus, Status.SUCCEEDED)
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status, ExecutionStatus.SUCCESS)
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs, planExecution.getEndTs())));
  }
}
