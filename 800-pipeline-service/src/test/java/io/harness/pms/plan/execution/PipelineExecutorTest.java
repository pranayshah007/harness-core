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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineExecutorTest extends CategoryTest {
  @InjectMocks PipelineExecutor pipelineExecutor;
  @Mock ExecutionHelper executionHelper;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;
  @Mock PipelineTelemetryHelper pipelineTelemetryHelper;

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

  PipelineEntity pipelineEntity = PipelineEntity.builder().allowStageExecutions(true).build();
  ExecutionTriggerInfo executionTriggerInfo = ExecutionTriggerInfo.newBuilder().build();
  ExecutionMetadata metadata = ExecutionMetadata.newBuilder().build();
  PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
  ExecArgs execArgs = ExecArgs.builder().metadata(metadata).planExecutionMetadata(planExecutionMetadata).build();
  PlanExecution planExecution = PlanExecution.builder().build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRunPipelineWithInputSetPipelineYaml() {
    doReturnStatementsForFreshRun(null, false, null);

    PlanExecutionResponseDto planExecutionResponse = pipelineExecutor.runPipelineWithInputSetPipelineYaml(
        accountId, orgId, projectId, pipelineId, moduleType, runtimeInputYaml, useV2);
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
        accountId, orgId, projectId, pipelineId, moduleType, originalExecutionId, runStageRequestDTO, useV2);
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
        accountId, orgId, projectId, pipelineId, moduleType, originalExecutionId, runtimeInputYaml, useV2);
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
            originalExecutionId, inputSetReferences, pipelineBranch, pipelineRepoId);
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
              Collections.emptyMap(), executionTriggerInfo, originalExecutionId, retryExecutionParameters);
    } else {
      doReturn(execArgs)
          .when(executionHelper)
          .buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, stageIdentifiers, Collections.emptyMap(),
              executionTriggerInfo, originalExecutionId, retryExecutionParameters);
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
              Collections.emptyMap(), executionTriggerInfo, originalExecutionId, retryExecutionParameters);
    } else {
      verify(executionHelper, times(1))
          .buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, stageIdentifiers, Collections.emptyMap(),
              executionTriggerInfo, originalExecutionId, retryExecutionParameters);
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
}
