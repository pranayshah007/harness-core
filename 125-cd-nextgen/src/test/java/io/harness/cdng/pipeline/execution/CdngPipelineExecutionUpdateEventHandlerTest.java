/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.execution;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.executions.CdngPipelineExecutionUpdateEventHandler;
import io.harness.cdng.rollback.service.RollbackDataServiceImpl;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.rule.Owner;
import io.harness.utils.StageStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CdngPipelineExecutionUpdateEventHandlerTest extends CategoryTest {
  private static final String STAGE_EXECUTION_ID = "stageExecutionId";
  @Mock private RollbackDataServiceImpl rollbackDataService;
  @InjectMocks private CdngPipelineExecutionUpdateEventHandler cdngPipelineExecutionUpdateEventHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleSuccessfulEvent() {
    cdngPipelineExecutionUpdateEventHandler.handleEvent(
        OrchestrationEvent.builder()
            .status(Status.SUCCEEDED)
            .ambiance(Ambiance.newBuilder()
                          .setStageExecutionId(STAGE_EXECUTION_ID)
                          .addLevels(Level.newBuilder()
                                         .setStepType(StepType.newBuilder()
                                                          .setType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName())
                                                          .build())
                                         .build())
                          .build())
            .build());
    verify(rollbackDataService).updateStatus(eq(STAGE_EXECUTION_ID), eq(StageStatus.SUCCEEDED));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleFailedEvent() {
    cdngPipelineExecutionUpdateEventHandler.handleEvent(
        OrchestrationEvent.builder()
            .status(Status.FAILED)
            .ambiance(Ambiance.newBuilder()
                          .setStageExecutionId(STAGE_EXECUTION_ID)
                          .addLevels(Level.newBuilder()
                                         .setStepType(StepType.newBuilder()
                                                          .setType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName())
                                                          .build())
                                         .build())
                          .build())
            .build());
    verify(rollbackDataService).updateStatus(eq(STAGE_EXECUTION_ID), eq(StageStatus.FAILED));
  }
}
