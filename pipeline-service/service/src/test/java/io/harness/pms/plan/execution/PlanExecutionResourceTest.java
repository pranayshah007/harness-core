/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIE_GET_FILE_CONTENT_ONLY;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.execution.PlanExecution;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitx.USER_FLOW;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.stages.StageExecutionResponse;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ThreadOperationContextHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PlanExecutionResourceTest extends CategoryTest {
  @InjectMocks PlanExecutionResourceImpl planExecutionResource;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PipelineExecutor pipelineExecutor;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock RetryExecutionHelper retryExecutionHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;

  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "p1";
  private final String PLAN_EXECUTION_ID = "planExecutionId";

  String yaml = "pipeline:\n"
      + "  identifier: p1\n"
      + "  name: p1\n"
      + "  allowStageExecutions: true\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: qaStage\n"
      + "      type: Approval\n"
      + "      name: qa stage\n"
      + "  - stage:\n"
      + "      identifier: qaStage2\n"
      + "      type: Deployment\n"
      + "      name: qa stage 2";

  PipelineEntity entity;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    entity = PipelineEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(PIPELINE_IDENTIFIER)
                 .name(PIPELINE_IDENTIFIER)
                 .yaml(yaml)
                 .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStagesExecutionList() {
    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(stagesExecutionList.getData()).hasSize(2);
    StageExecutionResponse stage0Data = stagesExecutionList.getData().get(0);
    assertThat(stage0Data.getStageIdentifier()).isEqualTo("qaStage");
    assertThat(stage0Data.getStageName()).isEqualTo("qa stage");
    assertThat(stage0Data.getMessage()).isEqualTo("Running an approval stage individually can be redundant");
    assertThat(stage0Data.getStagesRequired()).hasSize(0);
    StageExecutionResponse stage1Data = stagesExecutionList.getData().get(1);
    assertThat(stage1Data.getStageIdentifier()).isEqualTo("qaStage2");
    assertThat(stage1Data.getStageName()).isEqualTo("qa stage 2");
    assertThat(stage1Data.getMessage()).isNull();
    assertThat(stage1Data.getStagesRequired()).hasSize(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetStagesExecutionListWhenFfIsOn() {
    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, BOOLEAN_FALSE_VALUE);
    ResponseDTO<List<StageExecutionResponse>> stagesExecutionList = planExecutionResource.getStagesExecutionList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(stagesExecutionList.getData()).hasSize(2);
    StageExecutionResponse stage0Data = stagesExecutionList.getData().get(0);
    assertThat(stage0Data.getStageIdentifier()).isEqualTo("qaStage");
    assertThat(stage0Data.getStageName()).isEqualTo("qa stage");
    assertThat(stage0Data.getMessage()).isEqualTo("Running an approval stage individually can be redundant");
    assertThat(stage0Data.getStagesRequired()).hasSize(0);
    StageExecutionResponse stage1Data = stagesExecutionList.getData().get(1);
    assertThat(stage1Data.getStageIdentifier()).isEqualTo("qaStage2");
    assertThat(stage1Data.getStageName()).isEqualTo("qa stage 2");
    assertThat(stage1Data.getMessage()).isNull();
    assertThat(stage1Data.getStagesRequired()).hasSize(0);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunStagesWithRuntimeInputYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            RunStageRequestDTO.builder().build(), false, null);
    ResponseDTO<PlanExecutionResponseDto> dto =
        planExecutionResource.runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, null, false, RunStageRequestDTO.builder().build(), null);
    assertThat(dto.getData()).isEqualTo(planExecutionResponseDto);
    verify(pipelineExecutor, times(1))
        .runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            RunStageRequestDTO.builder().build(), false, null);
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetRetryHistory() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "planExecutionId", false))
        .thenReturn(
            PipelineExecutionSummaryEntity.builder()
                .uuid("uuid")
                .planExecutionId("planExecutionId")
                .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootExecutionId").build())
                .build());
    planExecutionResource.getRetryHistory(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "planExecutionId");
    verify(retryExecutionHelper, times(1)).getRetryHistory("rootExecutionId", "planExecutionId");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetLatestExecutionId() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "planExecutionId", false))
        .thenReturn(
            PipelineExecutionSummaryEntity.builder()
                .uuid("uuid")
                .planExecutionId("planExecutionId")
                .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootExecutionId").build())
                .build());
    planExecutionResource.getRetryLatestExecutionId(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "planExecutionId");
    verify(retryExecutionHelper, times(1)).getRetryLatestExecutionId("rootExecutionId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunPostExecutionRollback() {
    doReturn(PlanExecution.builder().planId("planId123").build())
        .when(pipelineExecutor)
        .startPostExecutionRollback(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "originalPlanId",
            Collections.singletonList("stageNodeExecutionId"), null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.runPostExecutionRollback(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "originalPlanId", "stageNodeExecutionId", null);
    PlanExecutionResponseDto data = response.getData();
    assertThat(data.getPlanExecution().getPlanId()).isEqualTo("planId123");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd", yaml, false, false, null);
    planExecutionResource.runPipelineWithInputSetPipelineYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, false, false, yaml, null);
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleStageAndPipelineInterrupt() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(PipelineExecutionSummaryEntity.builder().pipelineIdentifier(PIPELINE_IDENTIFIER).build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, false);
    doReturn(InterruptDTO.builder()
                 .id("interruptUuid")
                 .planExecutionId(PLAN_EXECUTION_ID)
                 .type(PlanExecutionInterruptType.ABORTALL)
                 .build())
        .when(pmsExecutionService)
        .registerInterrupt(PlanExecutionInterruptType.ABORTALL, PLAN_EXECUTION_ID, null);
    ArgumentCaptor<PlanExecutionInterruptType> interruptTypeArgumentCaptor1 =
        ArgumentCaptor.forClass(PlanExecutionInterruptType.class);
    planExecutionResource.handleInterrupt(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PlanExecutionInterruptTypePipeline.ABORTALL, PLAN_EXECUTION_ID);
    verify(pmsExecutionService, times(1)).registerInterrupt(interruptTypeArgumentCaptor1.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor1.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);
    doReturn(InterruptDTO.builder()
                 .id("interruptUuid")
                 .planExecutionId(PLAN_EXECUTION_ID)
                 .type(PlanExecutionInterruptType.ABORTALL)
                 .build())
        .when(pmsExecutionService)
        .registerInterrupt(PlanExecutionInterruptType.ABORTALL, PLAN_EXECUTION_ID, "nodeExecutionId");
    ArgumentCaptor<PlanExecutionInterruptType> interruptTypeArgumentCaptor2 =
        ArgumentCaptor.forClass(PlanExecutionInterruptType.class);
    planExecutionResource.handleStageInterrupt(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PlanExecutionInterruptTypeStage.ABORTALL, PLAN_EXECUTION_ID, "nodeExecutionId");
    verify(pmsExecutionService, times(2)).registerInterrupt(interruptTypeArgumentCaptor2.capture(), any(), any());
    assertThat(interruptTypeArgumentCaptor2.getValue()).isEqualTo(PlanExecutionInterruptType.ABORTALL);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYamlWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd", yaml, false, false, null);
    planExecutionResource.runPipelineWithInputSetPipelineYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, false, false, yaml, null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYamlV2() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd", yaml, true, false, null);
    planExecutionResource.runPipelineWithInputSetPipelineYamlV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, false, null, yaml);
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYamlV2WithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd", yaml, true, false, null);
    planExecutionResource.runPipelineWithInputSetPipelineYamlV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, false, null, yaml);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunStagesWithRuntimeInputYamlWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            RunStageRequestDTO.builder().build(), false, null);
    planExecutionResource.runStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, null, false, RunStageRequestDTO.builder().build(), null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunStagesWithRuntimeInputYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", RunStageRequestDTO.builder().build(), false, false, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, "originalExecutionId", null, false, RunStageRequestDTO.builder().build(), null);
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
    assertEquals("someId", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunStagesWithRuntimeInputYamlWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", RunStageRequestDTO.builder().build(), false, false, null);
    planExecutionResource.rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, "originalExecutionId", null, false, RunStageRequestDTO.builder().build(), null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, false, false, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null);
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYamlWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, false, false, null);
    planExecutionResource.rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYamlV2() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, true, false, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.rerunPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null);
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYamlV2WithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, true, false, null);
    planExecutionResource.rerunPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml, null);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugStagesWithRuntimeInputYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", RunStageRequestDTO.builder().build(), false, true, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.debugStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, "originalExecutionId", null, false, RunStageRequestDTO.builder().build());
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
    assertEquals("someId", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugStagesWithRuntimeInputYamlWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    PlanExecutionResponseDto planExecutionResponseDto =
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("someId").build()).build();
    doReturn(planExecutionResponseDto)
        .when(pipelineExecutor)
        .rerunStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", RunStageRequestDTO.builder().build(), false, true, null);
    planExecutionResource.debugStagesWithRuntimeInputYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
        PIPELINE_IDENTIFIER, "originalExecutionId", null, false, RunStageRequestDTO.builder().build());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugPipelineWithInputSetPipelineYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, false, true, null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.debugPipelineWithInputSetPipelineYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, null, false, yaml);
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
    assertEquals(Status.SUCCESS, response.getStatus());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugPipelineWithInputSetPipelineYamlWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, false, true, null);
    planExecutionResource.debugPipelineWithInputSetPipelineYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", PIPELINE_IDENTIFIER, null, null, false, yaml);
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugPipelineWithInputSetPipelineYamlV2() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, true, true, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.debugPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml);
    assertEquals(Status.SUCCESS, response.getStatus());
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDebugPipelineWithInputSetPipelineYamlV2WithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetPipelineYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", yaml, true, true, null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.debugPipelineWithInputSetPipelineYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            "originalExecutionId", PIPELINE_IDENTIFIER, null, false, yaml);
    assertEquals(Status.SUCCESS, response.getStatus());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetRetryStages() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        RetryInfo.builder().isResumable(true).groups(Collections.singletonList(RetryGroup.builder().build())).build())
        .when(retryExecutionHelper)
        .validateRetry(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "PlanExecutionId");
    ResponseDTO<RetryInfo> response = planExecutionResource.getRetryStages(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "PlanExecutionId", null);
    assertTrue(response.getData().isResumable());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetRetryStagesWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    doReturn(
        RetryInfo.builder().isResumable(true).groups(Collections.singletonList(RetryGroup.builder().build())).build())
        .when(retryExecutionHelper)
        .validateRetry(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "PlanExecutionId");
    ResponseDTO<RetryInfo> response = planExecutionResource.getRetryStages(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "PlanExecutionId", null);
    assertTrue(response.getData().isResumable());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetIdentifierList() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    MergeInputSetRequestDTOPMS mergeInputSetRequest = MergeInputSetRequestDTOPMS.builder().build();
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetReferencesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            mergeInputSetRequest, "main", "repoId", null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.runPipelineWithInputSetIdentifierList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, GitEntityFindInfoDTO.builder().branch("main").yamlGitConfigId("repoId").build(), false,
            mergeInputSetRequest, null);
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetIdentifierListWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    MergeInputSetRequestDTOPMS mergeInputSetRequest = MergeInputSetRequestDTOPMS.builder().build();
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .runPipelineWithInputSetReferencesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            mergeInputSetRequest, "main", "repoId", null);
    ResponseDTO<PlanExecutionResponseDto> response =
        planExecutionResource.runPipelineWithInputSetIdentifierList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd",
            PIPELINE_IDENTIFIER, GitEntityFindInfoDTO.builder().branch("main").yamlGitConfigId("repoId").build(), false,
            mergeInputSetRequest, null);
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetIdentifierList() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    MergeInputSetRequestDTOPMS mergeInputSetRequest = MergeInputSetRequestDTOPMS.builder().build();
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetReferencesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", mergeInputSetRequest, "main", "repoId", false, null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.rerunPipelineWithInputSetIdentifierList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", "originalExecutionId", PIPELINE_IDENTIFIER,
        GitEntityFindInfoDTO.builder().branch("main").yamlGitConfigId("repoId").build(), false, mergeInputSetRequest,
        null);
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
    assertEquals(USER_FLOW.EXECUTION, ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetIdentifierListWithoutUserFlowContext() {
    doReturn(false).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, PIE_GET_FILE_CONTENT_ONLY);
    MergeInputSetRequestDTOPMS mergeInputSetRequest = MergeInputSetRequestDTOPMS.builder().build();
    doReturn(
        PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().planId("planId123").build()).build())
        .when(pipelineExecutor)
        .rerunPipelineWithInputSetReferencesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "cd",
            "originalExecutionId", mergeInputSetRequest, "main", "repoId", false, null);
    ResponseDTO<PlanExecutionResponseDto> response = planExecutionResource.rerunPipelineWithInputSetIdentifierList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "cd", "originalExecutionId", PIPELINE_IDENTIFIER,
        GitEntityFindInfoDTO.builder().branch("main").yamlGitConfigId("repoId").build(), false, mergeInputSetRequest,
        null);
    assertEquals("planId123", response.getData().getPlanExecution().getPlanId());
    assertNull(ThreadOperationContextHelper.getThreadOperationContextUserFlow());
  }
}
