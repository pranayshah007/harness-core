/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({UUIDGenerator.class})
@OwnedBy(PIPELINE)
public class PipelineExecutorTest extends CategoryTest {
  @InjectMocks PipelineExecutor pipelineExecutor;
  @Mock ExecutionHelper executionHelper;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;
  @Mock PipelineTelemetryHelper pipelineTelemetryHelper;
  @Mock PlanExecutionService planExecutionService;
  @Mock RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "pipelineId";
  String moduleType = "cd";
  String runtimeInputYaml = "pipeline:\n"
      + "  variables:\n"
      + "  - name: a\n"
      + "    type: String\n"
      + "    value: c";
  List<String> stageIdentifiers = Arrays.asList("a1", "a2", "s1");
  RunStageRequestDTO runStageRequestDTO = RunStageRequestDTO.builder()
                                              .runtimeInputYaml(runtimeInputYaml)
                                              .stageIdentifiers(stageIdentifiers)
                                              .expressionValues(Collections.emptyMap())
                                              .build();
  String originalExecutionId = "originalExecutionId";
  boolean useV2 = false;
  List<String> inputSetReferences = Arrays.asList("i1", "i2", "i3");
  String pipelineBranch = null;
  String pipelineRepoId = null;
  boolean isDebug = false;

  PipelineEntity pipelineEntity = PipelineEntity.builder().allowStageExecutions(true).build();
  ExecutionTriggerInfo executionTriggerInfo =
      ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();
  ExecutionMetadata metadata = ExecutionMetadata.newBuilder().setTriggerInfo(executionTriggerInfo).build();
  PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().planExecutionId("planId").build();
  ExecArgs execArgs = ExecArgs.builder().metadata(metadata).planExecutionMetadata(planExecutionMetadata).build();
  PlanExecution planExecution = PlanExecution.builder().build();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYaml() {
    doReturnStatementsForFreshRun(null, false, null);

    PlanExecutionResponseDto planExecutionResponse = pipelineExecutor.runPipelineWithInputSetPipelineYaml(
        accountId, orgId, projectId, pipelineId, moduleType, runtimeInputYaml, useV2, false);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(null, false, null);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetReferencesList() {
    doReturnStatementsForFreshRun(null, true, null);

    PlanExecutionResponseDto planExecutionResponse = pipelineExecutor.runPipelineWithInputSetReferencesList(
        accountId, orgId, projectId, pipelineId, moduleType, inputSetReferences, pipelineBranch, pipelineRepoId);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verify(validateAndMergeHelper, times(1))
        .getMergeInputSetFromPipelineTemplate(
            accountId, orgId, projectId, pipelineId, inputSetReferences, pipelineBranch, pipelineRepoId, null);
    verifyStatementsForFreshRun(null, true, null);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunStagesWithRuntimeInputYaml() {
    doReturnStatementsForFreshRun(null, false, stageIdentifiers);

    PlanExecutionResponseDto planExecutionResponse = pipelineExecutor.runStagesWithRuntimeInputYaml(
        accountId, orgId, projectId, pipelineId, moduleType, runStageRequestDTO, useV2);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(null, false, stageIdentifiers);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRerunStagesWithRuntimeInputYaml() {
    doReturnStatementsForFreshRun(originalExecutionId, false, stageIdentifiers);

    PlanExecutionResponseDto planExecutionResponse = pipelineExecutor.rerunStagesWithRuntimeInputYaml(
        accountId, orgId, projectId, pipelineId, moduleType, originalExecutionId, runStageRequestDTO, useV2, isDebug);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(originalExecutionId, false, stageIdentifiers);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetPipelineYaml() {
    doReturnStatementsForFreshRun(originalExecutionId, false, null);

    PlanExecutionResponseDto planExecutionResponse = pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(
        accountId, orgId, projectId, pipelineId, moduleType, originalExecutionId, runtimeInputYaml, useV2, false);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(originalExecutionId, false, null);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRerunPipelineWithInputSetReferencesList() {
    doReturnStatementsForFreshRun(originalExecutionId, true, null);

    PlanExecutionResponseDto planExecutionResponse =
        pipelineExecutor.rerunPipelineWithInputSetReferencesList(accountId, orgId, projectId, pipelineId, moduleType,
            originalExecutionId, inputSetReferences, pipelineBranch, pipelineRepoId, false);
    assertThat(planExecutionResponse.getPlanExecution()).isEqualTo(planExecution);
    assertThat(planExecutionResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());

    verifyStatementsForFreshRun(originalExecutionId, true, null);
  }

  private void doReturnStatementsForFreshRun(
      String originalExecutionId, boolean addValidateAndMergeHelperDoReturn, List<String> stageIdentifiers) {
    if (addValidateAndMergeHelperDoReturn) {
      doReturn(runtimeInputYaml)
          .when(validateAndMergeHelper)
          .getMergeInputSetFromPipelineTemplate(
              accountId, orgId, projectId, pipelineId, inputSetReferences, pipelineBranch, pipelineRepoId, null);
    }

    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    doReturn(pipelineEntity).when(executionHelper).fetchPipelineEntity(accountId, orgId, projectId, pipelineId);
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(originalExecutionId);
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      doReturn(execArgs)
          .when(executionHelper)
          .buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, Collections.emptyList(),
              Collections.emptyMap(), executionTriggerInfo, originalExecutionId, retryExecutionParameters, false,
              false);
    } else {
      doReturn(execArgs)
          .when(executionHelper)
          .buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, stageIdentifiers, Collections.emptyMap(),
              executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false);
    }

    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(accountId, orgId, projectId, metadata, planExecutionMetadata, false, null, null, null);
  }

  private void verifyStatementsForFreshRun(
      String originalExecutionId, boolean verifyValidateAndMergeHelper, List<String> stageIdentifiers) {
    if (verifyValidateAndMergeHelper) {
      verify(validateAndMergeHelper, times(1))
          .getMergeInputSetFromPipelineTemplate(
              accountId, orgId, projectId, pipelineId, inputSetReferences, pipelineBranch, pipelineRepoId, null);
    }

    RetryExecutionParameters retryExecutionParameters = RetryExecutionParameters.builder().isRetry(false).build();
    verify(executionHelper, times(1)).fetchPipelineEntity(accountId, orgId, projectId, pipelineId);
    verify(executionHelper, times(1)).buildTriggerInfo(originalExecutionId);
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      verify(executionHelper, times(1))
          .buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, Collections.emptyList(),
              Collections.emptyMap(), executionTriggerInfo, originalExecutionId, retryExecutionParameters, false,
              false);
    } else {
      verify(executionHelper, times(1))
          .buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, stageIdentifiers, Collections.emptyMap(),
              executionTriggerInfo, originalExecutionId, retryExecutionParameters, false, false);
    }
    verify(executionHelper, times(1))
        .startExecution(accountId, orgId, projectId, metadata, planExecutionMetadata, false, null, null, null);
    verify(executionHelper, times(0))
        .startExecutionV2(anyString(), anyString(), anyString(), any(), any(), anyBoolean(), any(), any(), any());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testBuildRetryExecutionParameters() {
    // isRetry: false
    RetryExecutionParameters retryExecutionParameters =
        pipelineExecutor.buildRetryExecutionParameters(false, null, null, null);
    assertThat(retryExecutionParameters.isRetry()).isEqualTo(false);

    String processedYaml = "This is a processed Yaml";
    List<String> stagesIdentifier = Arrays.asList("stage1", "stage2");
    List<String> identifierOfSkippedStages = Collections.singletonList("stage1");
    retryExecutionParameters = pipelineExecutor.buildRetryExecutionParameters(
        true, processedYaml, stagesIdentifier, identifierOfSkippedStages);
    assertThat(retryExecutionParameters.isRetry()).isEqualTo(true);
    assertThat(retryExecutionParameters.getRetryStagesIdentifier()).isEqualTo(stagesIdentifier);
    assertThat(retryExecutionParameters.getIdentifierOfSkipStages()).isEqualTo(identifierOfSkippedStages);
    assertThat(retryExecutionParameters.getPreviousProcessedYaml()).isEqualTo(processedYaml);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testDraftExecution() {
    pipelineEntity.setIsDraft(true);
    doReturn(pipelineEntity).when(executionHelper).fetchPipelineEntity(accountId, orgId, projectId, pipelineId);
    assertThatThrownBy(()
                           -> pipelineExecutor.runPipelineWithInputSetPipelineYaml(
                               accountId, orgId, projectId, pipelineId, moduleType, runtimeInputYaml, useV2, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s", pipelineId, projectId));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartPostExecutionRollback() {
    MockedStatic<UUIDGenerator> mockSettings = Mockito.mockStatic(UUIDGenerator.class);
    when(UUIDGenerator.generateUuid()).thenReturn("planId");
    doReturn(executionTriggerInfo).when(executionHelper).buildTriggerInfo(null);
    ExecutionMetadata originalExecutionMetadata =
        ExecutionMetadata.newBuilder()
            .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK).build())
            .build();
    doReturn(PlanExecution.builder().metadata(originalExecutionMetadata).build())
        .when(planExecutionService)
        .get(originalExecutionId);
    doReturn(metadata)
        .when(rollbackModeExecutionHelper)
        .transformExecutionMetadata(
            originalExecutionMetadata, "planId", executionTriggerInfo, accountId, orgId, projectId);
    PlanExecutionMetadata originalPlanExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(originalExecutionId).build();
    doReturn(Optional.of(originalPlanExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(originalExecutionId);
    doReturn(planExecutionMetadata)
        .when(rollbackModeExecutionHelper)
        .transformPlanExecutionMetadata(originalPlanExecutionMetadata, "planId");
    doReturn(planExecution)
        .when(executionHelper)
        .startExecution(
            accountId, orgId, projectId, metadata, planExecutionMetadata, false, null, originalExecutionId, null);
    assertThat(pipelineExecutor.startPostExecutionRollback(accountId, orgId, projectId, originalExecutionId))
        .isEqualTo(planExecution);
    mockSettings.close();
  }
}
