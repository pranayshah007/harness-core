/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.pipeline.resume;

import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.beans.ExecutionStatus.resumableStatuses;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.VIKAS_S;

import static software.wings.api.EnvStateExecutionData.Builder.anEnvStateExecutionData;
import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;
import static software.wings.service.impl.pipeline.resume.PipelineResumeUtils.ERROR_MSG_PIPELINE_STAGE_DOES_NOT_EXISTS;
import static software.wings.service.impl.pipeline.resume.PipelineResumeUtils.PIPELINE_INVALID;
import static software.wings.service.impl.pipeline.resume.PipelineResumeUtils.PIPELINE_RESUME_ERROR_INVALID_STATUS;
import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.APPROVAL_RESUME;
import static software.wings.sm.StateType.ENV_LOOP_RESUME_STATE;
import static software.wings.sm.StateType.ENV_LOOP_STATE;
import static software.wings.sm.StateType.ENV_RESUME_STATE;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_STAGE_ELEMENT_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ApprovalStateExecutionData;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineExecution;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.PipelineStageExecution.PipelineStageExecutionBuilder;
import software.wings.beans.PipelineStageGroupedInfo;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.PipelineService;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.states.ApprovalResumeState.ApprovalResumeStateKeys;
import software.wings.sm.states.EnvLoopResumeState.EnvLoopResumeStateKeys;
import software.wings.sm.states.EnvResumeState.EnvResumeStateKeys;
import software.wings.sm.states.EnvState.EnvStateKeys;
import software.wings.sm.states.ForkState.ForkStateExecutionData;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PipelineResumeUtilsTest extends WingsBaseTest {
  @Mock private WingsPersistence mockWingsPersistence;
  @Mock private PipelineService pipelineService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AuthHandler authHandler;

  @Inject @InjectMocks private PipelineResumeUtils pipelineResumeUtils;

  private static final String pipelineId = generateUuid();
  private static final String pipelineResumeId = generateUuid();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetPipelineForResume() {
    String wfId2 = generateUuid();
    String wfId3 = generateUuid();
    PipelineStage stage1 = PipelineStage.builder()
                               .name("ps1")
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .type(APPROVAL.name())
                                                                                    .name("pse1")
                                                                                    .uuid(PIPELINE_STAGE_ELEMENT_ID)
                                                                                    .parallelIndex(1)
                                                                                    .build()))
                               .build();
    PipelineStage stage2 = PipelineStage.builder()
                               .name("ps2")
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder()
                                       .uuid(PIPELINE_STAGE_ELEMENT_ID + 1)
                                       .type(ENV_STATE.name())
                                       .name("pse2")
                                       .properties(Collections.singletonMap(EnvStateKeys.workflowId, wfId2))
                                       .parallelIndex(2)
                                       .build()))
                               .build();
    PipelineStage stage3 = PipelineStage.builder()
                               .name("ps3")
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder()
                                       .uuid(PIPELINE_STAGE_ELEMENT_ID + 2)
                                       .type(ENV_STATE.name())
                                       .name("pse3")
                                       .properties(Collections.singletonMap(EnvStateKeys.workflowId, wfId3))
                                       .parallelIndex(3)
                                       .build()))
                               .build();
    PipelineStage stage4 = PipelineStage.builder()
                               .name("ps4")
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .uuid(PIPELINE_STAGE_ELEMENT_ID + 3)
                                                                                    .type(ENV_STATE.name())
                                                                                    .name("pse4")
                                                                                    .parallelIndex(4)
                                                                                    .build()))
                               .build();

    String instanceId1 = generateUuid();
    String instanceId2 = generateUuid();
    String instanceId3 = generateUuid();
    StateExecutionInstance instance1 = mock(StateExecutionInstance.class);
    when(instance1.fetchStateExecutionData()).thenReturn(ApprovalStateExecutionData.builder().build());
    when(instance1.getUuid()).thenReturn(instanceId1);
    StateExecutionInstance instance2 = mock(StateExecutionInstance.class);
    when(instance2.fetchStateExecutionData()).thenReturn(anEnvStateExecutionData().build());
    when(instance2.getUuid()).thenReturn(instanceId2);
    StateExecutionInstance instance3 = mock(StateExecutionInstance.class);
    when(instance3.fetchStateExecutionData()).thenReturn(anEnvStateExecutionData().build());
    when(instance3.getUuid()).thenReturn(instanceId3);

    ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap =
        ImmutableMap.<String, StateExecutionInstance>builder()
            .put("pse1", instance1)
            .put("pse2", instance2)
            .put("pse3", instance3)
            .build();

    String wfExecutionId2 = generateUuid();
    String wfExecutionId3 = generateUuid();
    Pipeline pipeline = Pipeline.builder().pipelineStages(asList(stage1, stage2, stage3, stage4)).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder()
                                                 .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID)
                                                 .status(ExecutionStatus.SUCCESS)
                                                 .build();
    PipelineStageExecution stageExecution2 =
        PipelineStageExecution.builder()
            .status(ExecutionStatus.SUCCESS)
            .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 1)
            .workflowExecutions(Collections.singletonList(
                WorkflowExecution.builder().uuid(wfExecutionId2).name(wfExecutionId2).workflowId(wfId2).build()))
            .build();
    PipelineStageExecution stageExecution3 =
        PipelineStageExecution.builder()
            .status(ExecutionStatus.FAILED)
            .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 2)
            .workflowExecutions(Collections.singletonList(
                WorkflowExecution.builder().uuid(wfExecutionId3).name(wfExecutionId3).workflowId(wfId3).build()))
            .build();
    PipelineStageExecution stageExecution4 =
        PipelineStageExecution.builder().pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 3).build();
    workflowExecution.setPipelineExecution(
        aPipelineExecution()
            .withPipelineStageExecutions(asList(stageExecution1, stageExecution2, stageExecution3, stageExecution4))
            .build());

    when(pipelineService.readPipelineResolvedVariablesLoopedInfo(any(), any(), any(), anyBoolean()))
        .thenReturn(pipeline);
    Pipeline resumePipeline =
        pipelineResumeUtils.getPipelineForResume(APP_ID, 3, workflowExecution, stateExecutionInstanceMap);
    assertThat(resumePipeline).isNotNull();
    assertThat(resumePipeline.getPipelineStages()).isNotNull();
    assertThat(resumePipeline.getPipelineStages().size()).isEqualTo(4);

    PipelineStageElement pse = resumePipeline.getPipelineStages().get(0).getPipelineStageElements().get(0);
    assertThat(pse.getType()).isEqualTo(APPROVAL_RESUME.name());
    assertThat(pse.getProperties().size()).isEqualTo(2);
    assertThat(pse.getProperties().get(ApprovalResumeStateKeys.prevStateExecutionId)).isEqualTo(instanceId1);
    assertThat(pse.getProperties().get(ApprovalResumeStateKeys.prevPipelineExecutionId)).isEqualTo(pipelineId);

    pse = resumePipeline.getPipelineStages().get(1).getPipelineStageElements().get(0);
    assertThat(pse.getType()).isEqualTo(ENV_RESUME_STATE.name());
    assertThat(pse.getProperties().size()).isEqualTo(3);
    assertThat(pse.getProperties().get(EnvResumeStateKeys.prevStateExecutionId)).isEqualTo(instanceId2);
    assertThat(pse.getProperties().get(EnvResumeStateKeys.prevPipelineExecutionId)).isEqualTo(pipelineId);
    assertThat((List<String>) (pse.getProperties().get(EnvResumeStateKeys.prevWorkflowExecutionIds)))
        .containsExactly(wfExecutionId2);

    pse = resumePipeline.getPipelineStages().get(2).getPipelineStageElements().get(0);
    assertThat(pse.getType()).isEqualTo(ENV_STATE.name());

    pse = resumePipeline.getPipelineStages().get(3).getPipelineStageElements().get(0);
    assertThat(pse.getType()).isEqualTo(ENV_STATE.name());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetPipelineForResumeInsufficientStageExecutions() {
    PipelineStage stage1 =
        PipelineStage.builder()
            .name("ps1")
            .pipelineStageElements(Collections.singletonList(
                PipelineStageElement.builder().type(APPROVAL.name()).name("pse1").parallelIndex(1).build()))
            .build();
    PipelineStage stage2 =
        PipelineStage.builder()
            .name("ps2")
            .pipelineStageElements(Collections.singletonList(
                PipelineStageElement.builder().type(ENV_STATE.name()).name("pse2").parallelIndex(2).build()))
            .build();

    String instanceId1 = generateUuid();
    String instanceId2 = generateUuid();
    StateExecutionInstance instance1 = mock(StateExecutionInstance.class);
    when(instance1.fetchStateExecutionData()).thenReturn(ApprovalStateExecutionData.builder().build());
    when(instance1.getUuid()).thenReturn(instanceId1);
    StateExecutionInstance instance2 = mock(StateExecutionInstance.class);
    when(instance2.fetchStateExecutionData()).thenReturn(anEnvStateExecutionData().build());
    when(instance2.getUuid()).thenReturn(instanceId2);

    ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap =
        ImmutableMap.<String, StateExecutionInstance>builder().put("pse1", instance1).put("pse2", instance2).build();

    Pipeline pipeline = Pipeline.builder().pipelineStages(asList(stage1, stage2)).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder().status(ExecutionStatus.SUCCESS).build();
    workflowExecution.setPipelineExecution(
        aPipelineExecution().withPipelineStageExecutions(Collections.singletonList(stageExecution1)).build());

    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    pipelineResumeUtils.getPipelineForResume(APP_ID, 3, workflowExecution, stateExecutionInstanceMap);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testGetPipelineForResumeEmpty() {
    Pipeline pipeline = Pipeline.builder().pipelineStages(Collections.emptyList()).build();

    String instanceId1 = generateUuid();
    StateExecutionInstance instance1 = mock(StateExecutionInstance.class);
    when(instance1.fetchStateExecutionData()).thenReturn(anEnvStateExecutionData().build());
    when(instance1.getUuid()).thenReturn(instanceId1);
    ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap =
        ImmutableMap.<String, StateExecutionInstance>builder().put("pse1", instance1).build();

    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder().status(ExecutionStatus.SUCCESS).build();
    workflowExecution.setPipelineExecution(
        aPipelineExecution().withPipelineStageExecutions(Collections.singletonList(stageExecution1)).build());

    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    pipelineResumeUtils.getPipelineForResume(APP_ID, 3, workflowExecution, stateExecutionInstanceMap);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testGetPipelineForResumeWithLoopedStageResumed() {
    String wfId2 = generateUuid();
    String wfId3 = generateUuid();
    PipelineStage stage1 = PipelineStage.builder()
                               .name("ps1")
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .uuid("pse1")
                                                                                    .type(APPROVAL.name())
                                                                                    .name("pse1")
                                                                                    .parallelIndex(1)
                                                                                    .build()))
                               .build();
    PipelineStage stage2 = PipelineStage.builder()
                               .name("ps2")
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder()
                                       .type(ENV_LOOP_STATE.name())
                                       .uuid("pse2")
                                       .name("pse2")
                                       .properties(Collections.singletonMap(EnvStateKeys.workflowId, wfId2))
                                       .parallelIndex(2)
                                       .build()))
                               .build();
    PipelineStage stage3 = PipelineStage.builder()
                               .name("ps3")
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder()
                                       .uuid("pse3")
                                       .type(ENV_STATE.name())
                                       .name("pse3")
                                       .properties(Collections.singletonMap(EnvStateKeys.workflowId, wfId3))
                                       .parallelIndex(3)
                                       .build()))
                               .build();

    String instanceId1 = generateUuid();
    String instanceId2 = generateUuid();
    String instanceId3 = generateUuid();
    String instanceId4 = generateUuid();
    String instanceId5 = generateUuid();

    ForkStateExecutionData forkStateExecutionData = new ForkStateExecutionData();
    forkStateExecutionData.setForkStateNames(asList("pse2_1", "pse2_2"));
    forkStateExecutionData.setElements(asList("pse2_1", "pse2_2"));

    String wfExecutionId2 = generateUuid();
    String wfExecutionId3 = generateUuid();
    String wfExecutionId4 = generateUuid();

    StateExecutionInstance instance1 = mock(StateExecutionInstance.class);
    when(instance1.fetchStateExecutionData()).thenReturn(ApprovalStateExecutionData.builder().build());
    when(instance1.getUuid()).thenReturn(instanceId1);
    StateExecutionInstance instance2 = mock(StateExecutionInstance.class);
    when(instance2.fetchStateExecutionData()).thenReturn(forkStateExecutionData);
    when(instance2.getUuid()).thenReturn(instanceId2);
    StateExecutionInstance instance3 = mock(StateExecutionInstance.class);
    when(instance3.fetchStateExecutionData())
        .thenReturn(anEnvStateExecutionData().withWorkflowExecutionId(wfExecutionId2).build());
    when(instance3.getUuid()).thenReturn(instanceId3);
    StateExecutionInstance instance4 = mock(StateExecutionInstance.class);
    when(instance4.fetchStateExecutionData())
        .thenReturn(anEnvStateExecutionData().withWorkflowExecutionId(wfExecutionId3).build());
    when(instance4.getUuid()).thenReturn(instanceId4);
    StateExecutionInstance instance5 = mock(StateExecutionInstance.class);
    when(instance5.fetchStateExecutionData()).thenReturn(anEnvStateExecutionData().build());
    when(instance5.getUuid()).thenReturn(instanceId5);

    ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap =
        ImmutableMap.<String, StateExecutionInstance>builder()
            .put("pse1", instance1)
            .put("pse2", instance2)
            .put("pse2_1", instance3)
            .put("pse2_2", instance4)
            .put("pse3", instance5)
            .build();

    Pipeline pipeline = Pipeline.builder().accountId(ACCOUNT_ID).pipelineStages(asList(stage1, stage2, stage3)).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 =
        PipelineStageExecution.builder().pipelineStageElementId("pse1").status(ExecutionStatus.SUCCESS).build();
    PipelineStageExecution stageExecution2 =
        PipelineStageExecution.builder()
            .looped(true)
            .pipelineStageElementId("pse2")
            .status(ExecutionStatus.SUCCESS)
            .workflowExecutions(Collections.singletonList(
                WorkflowExecution.builder().uuid(wfExecutionId2).name(wfExecutionId2).workflowId(wfId2).build()))
            .build();
    PipelineStageExecution stageExecution3 =
        PipelineStageExecution.builder()
            .looped(true)
            .pipelineStageElementId("pse2")
            .status(ExecutionStatus.SUCCESS)
            .workflowExecutions(Collections.singletonList(
                WorkflowExecution.builder().uuid(wfExecutionId3).name(wfExecutionId3).workflowId(wfId2).build()))
            .build();
    PipelineStageExecution stageExecution4 =
        PipelineStageExecution.builder()
            .pipelineStageElementId("pse3")
            .status(ExecutionStatus.FAILED)
            .workflowExecutions(Collections.singletonList(
                WorkflowExecution.builder().uuid(wfExecutionId4).name(wfExecutionId4).workflowId(wfId3).build()))
            .build();
    workflowExecution.setPipelineExecution(
        aPipelineExecution()
            .withPipelineStageExecutions(asList(stageExecution1, stageExecution2, stageExecution3, stageExecution4))
            .build());

    when(pipelineService.readPipelineResolvedVariablesLoopedInfo(any(), any(), any(), anyBoolean()))
        .thenReturn(pipeline);
    Pipeline resumePipeline =
        pipelineResumeUtils.getPipelineForResume(APP_ID, 3, workflowExecution, stateExecutionInstanceMap);
    assertThat(resumePipeline).isNotNull();
    assertThat(resumePipeline.getPipelineStages()).isNotNull();
    assertThat(resumePipeline.getPipelineStages().size()).isEqualTo(3);

    PipelineStageElement pse = resumePipeline.getPipelineStages().get(0).getPipelineStageElements().get(0);
    assertThat(pse.getType()).isEqualTo(APPROVAL_RESUME.name());
    assertThat(pse.getProperties().size()).isEqualTo(2);
    assertThat(pse.getProperties().get(ApprovalResumeStateKeys.prevStateExecutionId)).isEqualTo(instanceId1);
    assertThat(pse.getProperties().get(ApprovalResumeStateKeys.prevPipelineExecutionId)).isEqualTo(pipelineId);

    pse = resumePipeline.getPipelineStages().get(1).getPipelineStageElements().get(0);
    assertThat(pse.getType()).isEqualTo(ENV_LOOP_RESUME_STATE.name());
    assertThat(pse.getProperties().size()).isEqualTo(3);
    assertThat(pse.getProperties().get(EnvLoopResumeStateKeys.prevStateExecutionId)).isEqualTo(instanceId2);
    assertThat(pse.getProperties().get(EnvLoopResumeStateKeys.prevPipelineExecutionId)).isEqualTo(pipelineId);
    Map<String, String> workflowInstanceIdMap =
        (Map<String, String>) pse.getProperties().get(EnvLoopResumeStateKeys.workflowExecutionIdWithStateExecutionIds);
    assertThat(workflowInstanceIdMap.size()).isEqualTo(2);

    pse = resumePipeline.getPipelineStages().get(2).getPipelineStageElements().get(0);
    assertThat(pse.getType()).isEqualTo(ENV_STATE.name());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetPipelineForResumeMismatchedTypes() {
    PipelineStage stage1 =
        PipelineStage.builder()
            .name("ps1")
            .pipelineStageElements(Collections.singletonList(
                PipelineStageElement.builder().type(APPROVAL.name()).name("pse1").parallelIndex(1).build()))
            .build();

    String instanceId1 = generateUuid();
    StateExecutionInstance instance1 = mock(StateExecutionInstance.class);
    when(instance1.fetchStateExecutionData()).thenReturn(anEnvStateExecutionData().build());
    when(instance1.getUuid()).thenReturn(instanceId1);

    ImmutableMap<String, StateExecutionInstance> stateExecutionInstanceMap =
        ImmutableMap.<String, StateExecutionInstance>builder().put("pse1", instance1).build();

    Pipeline pipeline = Pipeline.builder().pipelineStages(Collections.singletonList(stage1)).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder().status(ExecutionStatus.SUCCESS).build();
    workflowExecution.setPipelineExecution(
        aPipelineExecution().withPipelineStageExecutions(Collections.singletonList(stageExecution1)).build());

    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    pipelineResumeUtils.getPipelineForResume(APP_ID, 3, workflowExecution, stateExecutionInstanceMap);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetPipelineForResumeEmptyPipeline() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(null);
    pipelineResumeUtils.getPipelineForResume(APP_ID, 0, workflowExecution, null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetPipelineForResumeEmptyPipelineStages() {
    Pipeline pipeline = Pipeline.builder().build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    pipelineResumeUtils.getPipelineForResume(APP_ID, 0, workflowExecution, null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetPipelineForResumeEmptyPipelineStageExecutions() {
    Pipeline pipeline =
        Pipeline.builder().pipelineStages(Collections.singletonList(PipelineStage.builder().build())).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    workflowExecution.setPipelineExecution(aPipelineExecution().build());
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    pipelineResumeUtils.getPipelineForResume(APP_ID, 0, workflowExecution, null);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdatePipelineExecutionsAfterResume() {
    Query<WorkflowExecution> query = mock(Query.class);
    when(mockWingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    UpdateOperations<WorkflowExecution> updateOperations = mock(UpdateOperations.class);
    when(mockWingsPersistence.createUpdateOperations(eq(WorkflowExecution.class))).thenReturn(updateOperations);

    WorkflowExecution currWorkflowExecution = prepareFailedPipelineExecution();
    WorkflowExecution prevWorkflowExecution = prepareFailedPipelineExecution();
    verifyUpdatePipelineExecutionsAfterResume(pipelineId, currWorkflowExecution, prevWorkflowExecution);
    verify(mockWingsPersistence, times(2)).update(any(Query.class), any(UpdateOperations.class));

    currWorkflowExecution = prepareFailedPipelineExecution();
    prevWorkflowExecution = prepareLatestResumedFailedPipelineExecution();
    verifyUpdatePipelineExecutionsAfterResume(pipelineResumeId, currWorkflowExecution, prevWorkflowExecution);
    verify(mockWingsPersistence, times(4)).update(any(Query.class), any(UpdateOperations.class));
  }

  private void verifyUpdatePipelineExecutionsAfterResume(
      String pipelineResumeId, WorkflowExecution currWorkflowExecution, WorkflowExecution prevWorkflowExecution) {
    pipelineResumeUtils.updatePipelineExecutionsAfterResume(currWorkflowExecution, prevWorkflowExecution);
    assertThat(currWorkflowExecution.getPipelineResumeId()).isEqualTo(pipelineResumeId);
    assertThat(currWorkflowExecution.isLatestPipelineResume()).isTrue();
    assertThat(prevWorkflowExecution.getPipelineResumeId()).isEqualTo(pipelineResumeId);
    assertThat(prevWorkflowExecution.isLatestPipelineResume()).isFalse();
  }

  private PipelineStage createPipelineStage(String stateName, int index) {
    return PipelineStage.builder()
        .name(stateName)
        .pipelineStageElements(asList(PipelineStageElement.builder().parallelIndex(index).build()))
        .build();
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testGetParallelIndexFromPipelineStageName() {
    int group1Index = 1;
    String group1Stage1Name = "group1Stage1Name";
    String group1Stage2Name = "group1Stage2Name";

    int group2Index = 2;
    String group2Stage1Name = "group2Stage1Name";

    int group3Index = 3;
    String group3Stage1Name = "group3Stage1Name";
    String group3Stage2Name = "group3Stage2Name";
    String group3Stage3Name = "group3Stage3Name";

    String invalidStageName = "invalidStageName";

    PipelineStage group1Stage1 = createPipelineStage(group1Stage1Name, 1);
    PipelineStage group1Stage2 = createPipelineStage(group1Stage2Name, 1);

    PipelineStage group2Stage1 = createPipelineStage(group2Stage1Name, 2);

    PipelineStage group3Stage1 = createPipelineStage(group3Stage1Name, 3);
    PipelineStage group3Stage2 = createPipelineStage(group3Stage2Name, 3);
    PipelineStage group3Stage3 = createPipelineStage(group3Stage3Name, 3);

    String pipelineExecutionId = "pipelineExecutionId";
    Pipeline pipeline = Pipeline.builder()
                            .pipelineStages(Arrays.asList(
                                group1Stage1, group1Stage2, group2Stage1, group3Stage1, group3Stage2, group3Stage3))
                            .build();

    // Testing PipelineStage which is not parallel to any other is resolved correctly.
    assertThat(pipelineResumeUtils.getParallelIndexFromPipelineStageName(group2Stage1Name, pipeline))
        .isEqualTo(group2Index);

    // Testing PipelinesStage with parallel stages are resolved correctly.
    assertThat(pipelineResumeUtils.getParallelIndexFromPipelineStageName(group1Stage1Name, pipeline))
        .isEqualTo(group1Index);
    assertThat(pipelineResumeUtils.getParallelIndexFromPipelineStageName(group1Stage2Name, pipeline))
        .isEqualTo(group1Index);

    assertThat(pipelineResumeUtils.getParallelIndexFromPipelineStageName(group3Stage1Name, pipeline))
        .isEqualTo(group3Index);
    assertThat(pipelineResumeUtils.getParallelIndexFromPipelineStageName(group3Stage2Name, pipeline))
        .isEqualTo(group3Index);
    assertThat(pipelineResumeUtils.getParallelIndexFromPipelineStageName(group3Stage3Name, pipeline))
        .isEqualTo(group3Index);

    // Testing Error is reported for Non existing or non started Stages.
    assertThatThrownBy(() -> pipelineResumeUtils.getParallelIndexFromPipelineStageName(invalidStageName, pipeline))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(ERROR_MSG_PIPELINE_STAGE_DOES_NOT_EXISTS, invalidStageName);
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testGetPipelineFromWorkflowExecution() {
    String appId = "appId";
    String pipelineId = "pipelineId";
    Pipeline pipeline = Pipeline.builder().build();
    {
      // Case when WorkflowExecution has an instance of Pipeline.
      WorkflowExecution workflowExecution =
          WorkflowExecution.builder()
              .pipelineExecution(PipelineExecution.Builder.aPipelineExecution().withPipeline(pipeline).build())
              .build();

      assertThat(pipelineResumeUtils.getPipelineFromWorkflowExecution(workflowExecution, appId)).isEqualTo(pipeline);
    }

    {
      // Case when WorkflowExecution doesn't have an instance of Pipeline. but has a existing pipelineSummary.id.
      WorkflowExecution workflowExecution =
          WorkflowExecution.builder().pipelineSummary(PipelineSummary.builder().pipelineId(pipelineId).build()).build();
      when(pipelineService.getPipeline(eq(appId), eq(pipelineId))).thenReturn(pipeline);
      assertThat(pipelineResumeUtils.getPipelineFromWorkflowExecution(workflowExecution, appId)).isEqualTo(pipeline);
    }

    {
      // Case when WorkflowExecution doesn't have both an instance of Pipeline and pipelineSummary
      WorkflowExecution workflowExecution = WorkflowExecution.builder().build();
      when(pipelineService.getPipeline(eq(appId), eq(pipelineId))).thenReturn(null);
      assertThatThrownBy(() -> pipelineResumeUtils.getPipelineFromWorkflowExecution(workflowExecution, appId))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage(PIPELINE_INVALID);
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeStages() {
    PipelineStage stage1 = PipelineStage.builder()
                               .name("ps1")
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .uuid(PIPELINE_STAGE_ELEMENT_ID)
                                                                                    .name("pse1")
                                                                                    .type(APPROVAL.name())
                                                                                    .parallelIndex(1)
                                                                                    .build()))
                               .build();
    PipelineStage stage21 = PipelineStage.builder()
                                .name("ps2")
                                .pipelineStageElements(asList(PipelineStageElement.builder()
                                                                  .uuid(PIPELINE_STAGE_ELEMENT_ID + 1)
                                                                  .name("pse211")
                                                                  .parallelIndex(2)
                                                                  .build(),
                                    PipelineStageElement.builder()
                                        .uuid(PIPELINE_STAGE_ELEMENT_ID + 1)
                                        .name("pse212")
                                        .parallelIndex(2)
                                        .build()))
                                .build();
    PipelineStage stage22 =
        PipelineStage.builder()
            .name("ps2")
            .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                 .uuid(PIPELINE_STAGE_ELEMENT_ID + 2)
                                                                 .name("pse22")
                                                                 .parallelIndex(2)
                                                                 .build()))
            .parallel(true)
            .build();
    PipelineStage stage23 =
        PipelineStage.builder()
            .name("ps2")
            .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                 .uuid(PIPELINE_STAGE_ELEMENT_ID + 3)
                                                                 .name("pse23")
                                                                 .parallelIndex(2)
                                                                 .build()))
            .parallel(true)
            .build();
    PipelineStage stage3 = PipelineStage.builder()
                               .name("ps3")
                               .pipelineStageElements(Collections.singletonList(PipelineStageElement.builder()
                                                                                    .uuid(PIPELINE_STAGE_ELEMENT_ID + 4)
                                                                                    .name("pse3")
                                                                                    .parallelIndex(2)
                                                                                    .build()))
                               .build();
    Pipeline pipeline = Pipeline.builder().pipelineStages(asList(stage1, stage21, stage22, stage23, stage3)).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder()
                                                 .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID)
                                                 .status(ExecutionStatus.SUCCESS)
                                                 .build();
    PipelineStageExecution stageExecution21 = PipelineStageExecution.builder()
                                                  .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 1)
                                                  .status(ExecutionStatus.SUCCESS)
                                                  .build();
    PipelineStageExecution stageExecution22 = PipelineStageExecution.builder()
                                                  .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 2)
                                                  .status(ExecutionStatus.FAILED)
                                                  .build();
    PipelineStageExecution stageExecution23 = PipelineStageExecution.builder()
                                                  .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 3)
                                                  .status(ExecutionStatus.FAILED)
                                                  .build();
    PipelineStageExecution stageExecution3 =
        PipelineStageExecution.builder().pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 4).build();
    workflowExecution.setPipelineExecution(aPipelineExecution()
                                               .withPipelineStageExecutions(asList(stageExecution1, stageExecution21,
                                                   stageExecution22, stageExecution23, stageExecution3))
                                               .build());
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    List<PipelineStageGroupedInfo> groupedInfoList = pipelineResumeUtils.getResumeStages(APP_ID, workflowExecution);
    assertThat(groupedInfoList).isNotNull();
    assertThat(groupedInfoList.size()).isEqualTo(2);
    PipelineStageGroupedInfo groupedInfo1 = groupedInfoList.get(0);
    assertThat(groupedInfo1.getName()).isEqualTo("ps1");
    assertThat(groupedInfo1.getPipelineStageElementNames()).containsExactly("Approval");
    assertThat(groupedInfo1.getParallelIndex()).isEqualTo(1);
    PipelineStageGroupedInfo groupedInfo2 = groupedInfoList.get(1);
    assertThat(groupedInfo2.getName()).isEqualTo("ps2");
    assertThat(groupedInfo2.getPipelineStageElementNames()).containsExactly("pse211", "pse212", "pse22", "pse23");
    assertThat(groupedInfo2.getParallelIndex()).isEqualTo(2);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeStagesWithSkippedStage() {
    PipelineStage stage1 =
        PipelineStage.builder()
            .name("ps1")
            .pipelineStageElements(Collections.singletonList(prepareEnvStatePipelineStageElement(1, 1)))
            .build();
    PipelineStage stage2 =
        PipelineStage.builder()
            .name("ps2")
            .pipelineStageElements(Collections.singletonList(prepareEnvStatePipelineStageElement(2, 2)))
            .build();
    PipelineStage stage3 =
        PipelineStage.builder()
            .name("ps3")
            .pipelineStageElements(Collections.singletonList(prepareEnvStatePipelineStageElement(3, 3)))
            .build();
    Pipeline pipeline = Pipeline.builder().pipelineStages(asList(stage1, stage2, stage3)).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder()
                                                 .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 1)
                                                 .status(ExecutionStatus.SKIPPED)
                                                 .build();
    PipelineStageExecution stageExecution2 =
        PipelineStageExecution.builder()
            .status(ExecutionStatus.SUCCESS)
            .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 2)
            .workflowExecutions(Collections.singletonList(WorkflowExecution.builder().workflowId("wf2").build()))
            .build();
    PipelineStageExecution stageExecution3 =
        PipelineStageExecution.builder()
            .status(ExecutionStatus.FAILED)
            .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 3)
            .workflowExecutions(Collections.singletonList(WorkflowExecution.builder().workflowId("wf3").build()))
            .build();
    PipelineStageExecution stageExecution4 =
        PipelineStageExecution.builder().pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 4).build();
    workflowExecution.setPipelineExecution(
        aPipelineExecution()
            .withPipelineStageExecutions(asList(stageExecution1, stageExecution2, stageExecution3, stageExecution4))
            .build());
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    List<PipelineStageGroupedInfo> groupedInfoList = pipelineResumeUtils.getResumeStages(APP_ID, workflowExecution);
    assertThat(groupedInfoList).isNotNull();
    assertThat(groupedInfoList.size()).isEqualTo(3);
    PipelineStageGroupedInfo groupedInfo1 = groupedInfoList.get(0);
    assertThat(groupedInfo1.getName()).isEqualTo("ps1");
    assertThat(groupedInfo1.getPipelineStageElementNames()).containsExactly("pse1_1");
    assertThat(groupedInfo1.getParallelIndex()).isEqualTo(1);
    PipelineStageGroupedInfo groupedInfo2 = groupedInfoList.get(1);
    assertThat(groupedInfo2.getName()).isEqualTo("ps2");
    assertThat(groupedInfo2.getPipelineStageElementNames()).containsExactly("pse2_2");
    assertThat(groupedInfo2.getParallelIndex()).isEqualTo(2);
    PipelineStageGroupedInfo groupedInfo3 = groupedInfoList.get(2);
    assertThat(groupedInfo3.getName()).isEqualTo("ps3");
    assertThat(groupedInfo3.getPipelineStageElementNames()).containsExactly("pse3_3");
    assertThat(groupedInfo3.getParallelIndex()).isEqualTo(3);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeStagesInsufficientStageExecutions() {
    PipelineStage stage1 = PipelineStage.builder()
                               .name("ps1")
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder().name("pse1").parallelIndex(1).build()))
                               .build();
    PipelineStage stage2 = PipelineStage.builder()
                               .name("ps2")
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder().name("pse2").parallelIndex(1).build()))
                               .build();
    PipelineStage stage3 = PipelineStage.builder()
                               .name("ps3")
                               .pipelineStageElements(Collections.singletonList(
                                   PipelineStageElement.builder().name("pse3").parallelIndex(1).build()))
                               .build();
    Pipeline pipeline = Pipeline.builder().pipelineStages(asList(stage1, stage2, stage3)).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder().status(ExecutionStatus.SUCCESS).build();
    workflowExecution.setPipelineExecution(
        aPipelineExecution().withPipelineStageExecutions(Collections.singletonList(stageExecution1)).build());
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    pipelineResumeUtils.getResumeStages(APP_ID, workflowExecution);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeStagesEmptyPipeline() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(null);
    pipelineResumeUtils.getResumeStages(APP_ID, workflowExecution);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeStagesEmptyPipelineStages() {
    Pipeline pipeline = Pipeline.builder().build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    pipelineResumeUtils.getResumeStages(APP_ID, workflowExecution);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeStagesEmptyPipelineStageExecutions() {
    Pipeline pipeline =
        Pipeline.builder().pipelineStages(Collections.singletonList(PipelineStage.builder().build())).build();
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    workflowExecution.setPipelineExecution(aPipelineExecution().build());
    when(pipelineService.readPipelineWithResolvedVariables(any(), any(), any(), anyBoolean())).thenReturn(pipeline);
    pipelineResumeUtils.getResumeStages(APP_ID, workflowExecution);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetResumeHistory() {
    Query<WorkflowExecution> query = mock(Query.class);
    when(mockWingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    when(query.project(any(), anyBoolean())).thenReturn(query);
    when(query.order(anyString())).thenReturn(query);
    when(query.asList())
        .thenReturn(asList(prepareFailedPipelineExecution(), prepareNonLatestResumedFailedPipelineExecution(),
            prepareLatestResumedFailedPipelineExecution()));

    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    List<WorkflowExecution> history = pipelineResumeUtils.getResumeHistory(APP_ID, workflowExecution);
    assertThat(history).isNotNull();
    assertThat(history).isEmpty();

    workflowExecution = prepareNonLatestResumedFailedPipelineExecution();
    history = pipelineResumeUtils.getResumeHistory(APP_ID, workflowExecution);
    assertThat(history).isNotNull();
    assertThat(history.size()).isEqualTo(3);

    workflowExecution = prepareLatestResumedFailedPipelineExecution();
    history = pipelineResumeUtils.getResumeHistory(APP_ID, workflowExecution);
    assertThat(history).isNotNull();
    assertThat(history.size()).isEqualTo(3);
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeAvailable() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecutionWithNotActiveExecutions();
    pipelineResumeUtils.checkPipelineResumeAvailable(workflowExecution);

    workflowExecution = prepareLatestResumedFailedPipelineExecution();
    pipelineResumeUtils.checkPipelineResumeAvailable(workflowExecution);
  }

  @Test(expected = Test.None.class)
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeAvailableForResumableStatuses() {
    WorkflowExecution abortedWorkflowExecution = preparePipelineExecution(WorkflowType.PIPELINE, ABORTED);
    pipelineResumeUtils.checkPipelineResumeAvailable(abortedWorkflowExecution);

    WorkflowExecution failedWorkflowExecution = preparePipelineExecution(WorkflowType.PIPELINE, FAILED);
    pipelineResumeUtils.checkPipelineResumeAvailable(failedWorkflowExecution);

    WorkflowExecution rejectedWorkflowExecution = preparePipelineExecution(WorkflowType.PIPELINE, REJECTED);
    pipelineResumeUtils.checkPipelineResumeAvailable(rejectedWorkflowExecution);

    WorkflowExecution expiredWorkflowExecution = preparePipelineExecution(WorkflowType.PIPELINE, EXPIRED);
    pipelineResumeUtils.checkPipelineResumeAvailable(expiredWorkflowExecution);

    WorkflowExecution errorWorkflowExecution = preparePipelineExecution(WorkflowType.PIPELINE, ERROR);
    pipelineResumeUtils.checkPipelineResumeAvailable(errorWorkflowExecution);
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeAvailableForUnResumableStatuses() {
    WorkflowExecution abortedWorkflowExecution = preparePipelineExecution(WorkflowType.PIPELINE, RUNNING);
    assertThatThrownBy(() -> pipelineResumeUtils.checkPipelineResumeAvailable(abortedWorkflowExecution))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(PIPELINE_RESUME_ERROR_INVALID_STATUS, abortedWorkflowExecution.getUuid(),
            ExecutionStatus.resumableStatuses.toString());

    WorkflowExecution failedWorkflowExecution = preparePipelineExecution(WorkflowType.PIPELINE, WAITING);
    assertThatThrownBy(() -> pipelineResumeUtils.checkPipelineResumeAvailable(failedWorkflowExecution))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(PIPELINE_RESUME_ERROR_INVALID_STATUS, failedWorkflowExecution.getUuid(),
            ExecutionStatus.resumableStatuses.toString());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeAvailableForSuccessfulExecution() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    workflowExecution.setStatus(ExecutionStatus.SUCCESS);
    pipelineResumeUtils.checkPipelineResumeAvailable(workflowExecution);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeAvailableForNonLatestExecution() {
    WorkflowExecution workflowExecution = prepareNonLatestResumedFailedPipelineExecution();
    pipelineResumeUtils.checkPipelineResumeAvailable(workflowExecution);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeAvailableForTypeWorkflow() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution(WorkflowType.ORCHESTRATION);
    pipelineResumeUtils.checkPipelineResumeAvailable(workflowExecution);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeUnavailableForPipelineFailedDuringArtifactCollection() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder().status(ExecutionStatus.QUEUED).build();
    PipelineStageExecution stageExecution2 = PipelineStageExecution.builder().status(ExecutionStatus.QUEUED).build();
    workflowExecution.setPipelineExecution(
        aPipelineExecution().withPipelineStageExecutions(asList(stageExecution1, stageExecution2)).build());
    pipelineResumeUtils.checkPipelineResumeAvailable(workflowExecution);
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeHistoryAvailable() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    pipelineResumeUtils.checkPipelineResumeHistoryAvailable(workflowExecution);

    workflowExecution = prepareLatestResumedFailedPipelineExecution();
    pipelineResumeUtils.checkPipelineResumeHistoryAvailable(workflowExecution);

    workflowExecution = prepareNonLatestResumedFailedPipelineExecution();
    pipelineResumeUtils.checkPipelineResumeHistoryAvailable(workflowExecution);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckPipelineResumeHistoryAvailableForTypeWorkflow() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution(WorkflowType.ORCHESTRATION);
    pipelineResumeUtils.checkPipelineResumeHistoryAvailable(workflowExecution);
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckStageAndStageExecution() {
    PipelineStage stage = PipelineStage.builder()
                              .pipelineStageElements(asList(prepareEnvStatePipelineStageElement(1),
                                  prepareApprovalPipelineStageElement(), prepareEnvStatePipelineStageElement(2)))
                              .build();
    PipelineStageExecution stageExecution =
        PipelineStageExecution.builder()
            .workflowExecutions(asList(prepareSimpleWorkflowExecution(1), prepareSimpleWorkflowExecution(2)))
            .build();
    pipelineResumeUtils.checkStageAndStageExecutions(stage, Collections.singletonList(stageExecution));
  }

  /**
   * Testing check should pass when PipelineStageExecution failed before corresponding Workflow was not created,
   * It could be due to -
   * 1. failure in evaluating Skip Condition.
   * 2. Stage being aborted while it is waiting on Runtime inputs.
   */
  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testCheckStageAndStageExecutionWhen() {
    PipelineStage stage = PipelineStage.builder()
                              .pipelineStageElements(asList(prepareEnvStatePipelineStageElement(1),
                                  prepareApprovalPipelineStageElement(), prepareEnvStatePipelineStageElement(2)))
                              .build();
    PipelineStageExecutionBuilder stageExecution = PipelineStageExecution.builder();
    //.workflowExecutions() // Null workflow;
    for (ExecutionStatus executionStatus : resumableStatuses) {
      // Check should pass for all Resumeble Statuses.
      pipelineResumeUtils.checkStageAndStageExecutions(
          stage, Collections.singletonList(stageExecution.status(executionStatus).build()));
    }

    Set<ExecutionStatus> nonResumableStatuses = Sets.newHashSet(ExecutionStatus.values());
    nonResumableStatuses.removeAll(resumableStatuses);
    nonResumableStatuses.remove(SKIPPED);
    for (ExecutionStatus executionStatus : nonResumableStatuses) {
      // Check should fail for all Non Resumeble Statuses.
      assertThatThrownBy(()
                             -> pipelineResumeUtils.checkStageAndStageExecutions(
                                 stage, Collections.singletonList(stageExecution.status(executionStatus).build())))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckStageAndStageExecutionDifferentWorkflowIds() {
    PipelineStage stage = PipelineStage.builder()
                              .pipelineStageElements(Collections.singletonList(prepareEnvStatePipelineStageElement(1)))
                              .build();
    PipelineStageExecution stageExecution =
        PipelineStageExecution.builder()
            .workflowExecutions(Collections.singletonList(prepareSimpleWorkflowExecution(2)))
            .build();
    pipelineResumeUtils.checkStageAndStageExecutions(stage, Collections.singletonList(stageExecution));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testCheckStageAndStageExecutionLooped() {
    PipelineStage stage =
        PipelineStage.builder()
            .pipelineStageElements(Collections.singletonList(prepareEnvLoopedStatePipelineStageElement(1)))
            .build();
    // looped is not set to true
    PipelineStageExecution stageExecution =
        PipelineStageExecution.builder()
            .workflowExecutions(Collections.singletonList(prepareSimpleWorkflowExecution(2)))
            .build();
    PipelineStageExecution stageExecution2 =
        PipelineStageExecution.builder()
            .workflowExecutions(Collections.singletonList(prepareSimpleWorkflowExecution(2)))
            .build();
    pipelineResumeUtils.checkStageAndStageExecutions(stage, asList(stageExecution, stageExecution2));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testCheckStageAndStageExecutionApprovalToWorkflow() {
    PipelineStage stage = PipelineStage.builder()
                              .pipelineStageElements(Collections.singletonList(prepareEnvStatePipelineStageElement(1)))
                              .build();
    PipelineStageExecution stageExecution =
        PipelineStageExecution.builder().stateExecutionData(ApprovalStateExecutionData.builder().build()).build();
    pipelineResumeUtils.checkStageAndStageExecutions(stage, Collections.singletonList(stageExecution));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testCheckStageAndStageExecutionWorkflowToApproval() {
    PipelineStage stage = PipelineStage.builder()
                              .pipelineStageElements(Collections.singletonList(prepareApprovalPipelineStageElement()))
                              .build();
    PipelineStageExecution stageExecution =
        PipelineStageExecution.builder()
            .workflowExecutions(Collections.singletonList(prepareSimpleWorkflowExecution(2)))
            .build();
    pipelineResumeUtils.checkStageAndStageExecutions(stage, Collections.singletonList(stageExecution));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckStageAndStageExecutionInsufficientStageElement() {
    PipelineStage stage = PipelineStage.builder()
                              .pipelineStageElements(Collections.singletonList(prepareEnvStatePipelineStageElement(1)))
                              .build();
    PipelineStageExecution stageExecution = PipelineStageExecution.builder()
                                                .workflowExecutions(asList(prepareSimpleWorkflowExecution(1)))
                                                .looped(true)
                                                .build();
    PipelineStageExecution stageExecution1 = PipelineStageExecution.builder()
                                                 .workflowExecutions(asList(prepareSimpleWorkflowExecution(1)))
                                                 .looped(false)
                                                 .build();
    pipelineResumeUtils.checkStageAndStageExecutions(stage, asList(stageExecution, stageExecution1));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckStageAndStageExecutionInsufficientWorkflowExecutions() {
    PipelineStage stage =
        PipelineStage.builder().pipelineStageElements(asList(prepareEnvStatePipelineStageElement(1))).build();
    PipelineStageExecution stageExecution =
        PipelineStageExecution.builder()
            .workflowExecutions(Collections.singletonList(prepareSimpleWorkflowExecution(2)))
            .build();
    pipelineResumeUtils.checkStageAndStageExecutions(stage, Collections.singletonList(stageExecution));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testAddLatestPipelineResumeFilter() {
    PageRequest<WorkflowExecution> pageRequest = aPageRequest().addFilter(ACCOUNT_ID, Operator.EQ, ACCOUNT_ID).build();

    PipelineResumeUtils.addLatestPipelineResumeFilter(pageRequest);
    assertThat(pageRequest.getFilters().size()).isEqualTo(2);

    SearchFilter searchFilter = pageRequest.getFilters().get(1);
    assertThat(searchFilter.getFieldName()).isEqualTo("");
    assertThat(searchFilter.getOp()).isEqualTo(Operator.OR);

    SearchFilter searchFilter1 = (SearchFilter) searchFilter.getFieldValues()[0];
    assertThat(searchFilter1.getFieldName()).isEqualTo(WorkflowExecutionKeys.pipelineResumeId);
    assertThat(searchFilter1.getOp()).isEqualTo(Operator.NOT_EXISTS);

    SearchFilter searchFilter2 = (SearchFilter) searchFilter.getFieldValues()[1];
    assertThat(searchFilter2.getFieldName()).isEqualTo(WorkflowExecutionKeys.latestPipelineResume);
    assertThat(searchFilter2.getOp()).isEqualTo(Operator.EQ);
    assertThat(searchFilter2.getFieldValues()).containsExactly(Boolean.TRUE);
  }

  private WorkflowExecution prepareFailedPipelineExecution(WorkflowType workflowType) {
    return WorkflowExecution.builder()
        .uuid(pipelineId)
        .accountId(ACCOUNT_ID)
        .workflowType(workflowType)
        .status(ExecutionStatus.FAILED)
        .executionArgs(new ExecutionArgs())
        .pipelineExecution(PipelineExecution.Builder.aPipelineExecution().build())
        .build();
  }

  private WorkflowExecution preparePipelineExecution(WorkflowType workflowType, ExecutionStatus status) {
    return WorkflowExecution.builder()
        .uuid(pipelineId)
        .accountId(ACCOUNT_ID)
        .workflowType(workflowType)
        .status(status)
        .executionArgs(new ExecutionArgs())
        .pipelineExecution(
            aPipelineExecution()
                .withPipelineStageExecutions(asList(PipelineStageExecution.builder().status(FAILED).build()))
                .build())
        .build();
  }

  private WorkflowExecution prepareFailedPipelineExecution() {
    return prepareFailedPipelineExecution(WorkflowType.PIPELINE);
  }

  private WorkflowExecution prepareFailedPipelineExecutionWithNotActiveExecutions() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution(WorkflowType.PIPELINE);
    workflowExecution.getPipelineExecution().getPipelineStageExecutions().add(
        PipelineStageExecution.builder().status(FAILED).build());
    return workflowExecution;
  }

  private WorkflowExecution prepareLatestResumedFailedPipelineExecution() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    workflowExecution.setPipelineResumeId(pipelineResumeId);
    workflowExecution.setLatestPipelineResume(true);
    workflowExecution.getPipelineExecution().getPipelineStageExecutions().add(
        PipelineStageExecution.builder().status(FAILED).build());
    return workflowExecution;
  }

  private WorkflowExecution prepareNonLatestResumedFailedPipelineExecution() {
    WorkflowExecution workflowExecution = prepareFailedPipelineExecution();
    workflowExecution.setPipelineResumeId(pipelineResumeId);
    workflowExecution.setLatestPipelineResume(false);
    return workflowExecution;
  }

  private PipelineStageElement prepareEnvStatePipelineStageElement(int workflowIdx) {
    return prepareEnvStatePipelineStageElement(workflowIdx, 0);
  }

  private PipelineStageElement prepareEnvLoopedStatePipelineStageElement(int workflowIdx) {
    return PipelineStageElement.builder()
        .type(ENV_LOOP_STATE.name())
        .name("pse" + workflowIdx + "_" + 0)
        .properties(Collections.singletonMap(EnvStateKeys.workflowId, "wf" + workflowIdx))
        .parallelIndex(0)
        .build();
  }

  private PipelineStageElement prepareEnvStatePipelineStageElement(int workflowIdx, int parallelIdx) {
    return PipelineStageElement.builder()
        .type(ENV_STATE.name())
        .uuid(PIPELINE_STAGE_ELEMENT_ID + workflowIdx)
        .name("pse" + workflowIdx + "_" + parallelIdx)
        .properties(Collections.singletonMap(EnvStateKeys.workflowId, "wf" + workflowIdx))
        .parallelIndex(parallelIdx)
        .build();
  }

  private PipelineStageElement prepareApprovalPipelineStageElement() {
    return PipelineStageElement.builder().type(APPROVAL.name()).build();
  }

  private WorkflowExecution prepareSimpleWorkflowExecution(int workflowIdx) {
    return WorkflowExecution.builder()
        .uuid(pipelineId)
        .accountId(ACCOUNT_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .workflowId("wf" + workflowIdx)
        .name("wfn" + workflowIdx)
        .status(ExecutionStatus.SUCCESS)
        .executionArgs(new ExecutionArgs())
        .build();
  }
}
