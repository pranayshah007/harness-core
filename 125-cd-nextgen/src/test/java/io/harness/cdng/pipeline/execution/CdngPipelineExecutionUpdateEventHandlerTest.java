/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.execution;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.pipeline.executions.CdngPipelineExecutionUpdateEventHandler;
import io.harness.data.structure.UUIDGenerator;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.StageStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CdngPipelineExecutionUpdateEventHandlerTest extends CategoryTest {
  private static final String STAGE_EXECUTION_ID = "stageExecutionId";
  @Mock private StageExecutionInfoService stageExecutionInfoService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @InjectMocks private CdngPipelineExecutionUpdateEventHandler cdngPipelineExecutionUpdateEventHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(ngFeatureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
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
    verify(stageExecutionInfoService).updateStatus(any(), eq(STAGE_EXECUTION_ID), eq(StageStatus.SUCCEEDED));
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
    verify(stageExecutionInfoService).updateStatus(any(), eq(STAGE_EXECUTION_ID), eq(StageStatus.FAILED));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testDeploymentStatusUpdateEventWithFFDisabled() {
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
    verify(stageExecutionInfoService, times(1)).update(any(Scope.class), anyString(), any(Map.class));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testDeploymentStatusUpdateEventWithFFEnabled() {
    when(ngFeatureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    cdngPipelineExecutionUpdateEventHandler.handleEvent(
        OrchestrationEvent.builder().status(Status.SUCCEEDED).ambiance(buildAmbiance()).endTs(80L).build());
    Map<String, Object> updates = new HashMap<>();
    verify(stageExecutionInfoService, times(1)).updateStatus(any(), eq(STAGE_EXECUTION_ID), eq(StageStatus.SUCCEEDED));
    updates.put(StageExecutionInfoKeys.status, Status.SUCCEEDED);
    updates.put(StageExecutionInfoKeys.endts, 80L);
    verify(stageExecutionInfoService)
        .update(Scope.builder()
                    .accountIdentifier("ACCOUNT_ID")
                    .orgIdentifier("ORG_ID")
                    .projectIdentifier("PROJECT_ID")
                    .build(),
            STAGE_EXECUTION_ID, updates);
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList();
    levels.add(
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setType(ExecutionNodeType.DEPLOYMENT_STAGE_STEP.getName()).build())
            .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(UUIDGenerator.generateUuid())
        .putAllSetupAbstractions(Map.of("accountId", "ACCOUNT_ID", "projectIdentifier", "PROJECT_ID", "orgIdentifier",
            "ORG_ID", "pipelineId", "PIPELINE_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234L)
        .setStageExecutionId(STAGE_EXECUTION_ID)
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier("PIPELINE_ID")
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal("prinicipal")
                                               .setPrincipalType(io.harness.pms.contracts.plan.PrincipalType.USER)
                                               .build())
                         .build())
        .build();
  }
}
