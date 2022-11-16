/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class ExecutionInfoUpdateEventHandlerTest extends PipelineServiceTestBase {
  @Mock private PMSPipelineService pmsPipelineService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private RecentExecutionsInfoHelper recentExecutionsInfoHelper;

  private ExecutionInfoUpdateEventHandler executionInfoUpdateEventHandler;

  @Before
  public void setUp() {
    executionInfoUpdateEventHandler =
        new ExecutionInfoUpdateEventHandler(planExecutionService, recentExecutionsInfoHelper);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnPlanStatusUpdate() {
    String planExecutionId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder().uuid(generateUuid()).build();

    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    when(planExecutionService.get(anyString())).thenReturn(PlanExecution.builder().status(Status.FAILED).build());

    executionInfoUpdateEventHandler.onPlanStatusUpdate(ambiance);
    verify(recentExecutionsInfoHelper, times(1)).onExecutionUpdate(any(), any());
  }
}
