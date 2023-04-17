/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinerollback;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineRollbackStageStepTest extends CategoryTest {
  @InjectMocks PipelineRollbackStageStep step;
  @Mock PipelineExecutor pipelineExecutor;
  @Mock PmsExecutionSummaryService executionSummaryService;

  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";
  String currentPlanExecutionId = "curr";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac() {
    PlanExecution planExecution = PlanExecution.builder().uuid("rbUuid").build();
    doReturn(planExecution)
        .when(pipelineExecutor)
        .startPipelineRollback(accountId, orgId, projectId, currentPlanExecutionId,
            PipelineStageInfo.newBuilder().setHasParentPipeline(false).setStageNodeId("setupId").build());
    doNothing().when(executionSummaryService).update(any(), any());
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .setPlanExecutionId(currentPlanExecutionId)
                            .addLevels(Level.newBuilder().setSetupId("setupId"))
                            .build();
    AsyncExecutableResponse asyncExecutableResponse = step.executeAsyncAfterRbac(ambiance, null, null);
    assertThat(asyncExecutableResponse.getCallbackIdsCount()).isEqualTo(1);
    assertThat(asyncExecutableResponse.getCallbackIds(0)).isEqualTo("rbUuid");
  }
}