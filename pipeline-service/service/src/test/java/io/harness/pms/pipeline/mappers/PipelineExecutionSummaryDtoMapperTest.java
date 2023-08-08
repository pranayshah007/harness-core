/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.AbortedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionIdentifierSummaryDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineExecutionSummaryDtoMapperTest extends CategoryTest {
  String accountId = "acc";
  String orgId = "org";
  String projId = "proj";
  String pipelineId = "pipelineId";
  String planId = "plan-random";

  String branch = "branch";
  String repo = "repo";
  String objectId = "o";
  String rootFolder = "folder/.harness/";
  String file = "file.yaml";

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForGitDetails() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNull();

    EntityGitDetails entityGitDetails =
        EntityGitDetails.builder().branch(branch).repoIdentifier(repo).objectId(objectId).build();
    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails().getBranch()).isEqualTo(branch);
    assertThat(executionSummaryDTO.getGitDetails().getRepoIdentifier()).isEqualTo(repo);
    assertThat(executionSummaryDTO.getGitDetails().getObjectId()).isEqualTo(objectId);
    assertThat(executionSummaryDTO.getGitDetails().getFilePath()).isNull();
    assertThat(executionSummaryDTO.getGitDetails().getRootFolder()).isNull();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();

    entityGitDetails = EntityGitDetails.builder()
                           .branch(branch)
                           .repoIdentifier(repo)
                           .objectId(objectId)
                           .rootFolder("__default__")
                           .filePath("__default__")
                           .build();
    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails().getBranch()).isEqualTo(branch);
    assertThat(executionSummaryDTO.getGitDetails().getRepoIdentifier()).isEqualTo(repo);
    assertThat(executionSummaryDTO.getGitDetails().getObjectId()).isEqualTo(objectId);
    assertThat(executionSummaryDTO.getGitDetails().getFilePath()).isNull();
    assertThat(executionSummaryDTO.getGitDetails().getRootFolder()).isNull();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();

    entityGitDetails = EntityGitDetails.builder()
                           .branch(branch)
                           .repoIdentifier(repo)
                           .objectId(objectId)
                           .rootFolder(rootFolder)
                           .filePath(file)
                           .build();
    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails().getBranch()).isEqualTo(branch);
    assertThat(executionSummaryDTO.getGitDetails().getRepoIdentifier()).isEqualTo(repo);
    assertThat(executionSummaryDTO.getGitDetails().getObjectId()).isEqualTo(objectId);
    assertThat(executionSummaryDTO.getGitDetails().getFilePath()).isEqualTo(file);
    assertThat(executionSummaryDTO.getGitDetails().getRootFolder()).isEqualTo(rootFolder);
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void toExecutionIdentifierDto() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .runSequence(32)
                                                                        .projectIdentifier(projId)
                                                                        .orgIdentifier(orgId)
                                                                        .pipelineIdentifier(pipelineId)
                                                                        .planExecutionId(planId)
                                                                        .status(ExecutionStatus.ABORTED)
                                                                        .build();
    PipelineExecutionIdentifierSummaryDTO pipelineExecutionIdentifierSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toExecutionIdentifierDto(pipelineExecutionSummaryEntity);

    assertThat(pipelineExecutionIdentifierSummaryDTO).isNotNull();
    assertThat(pipelineExecutionIdentifierSummaryDTO.getPipelineIdentifier()).isEqualTo(pipelineId);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getPlanExecutionId()).isEqualTo(planId);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getProjectIdentifier()).isEqualTo(projId);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getRunSequence()).isEqualTo(32);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getStatus()).isEqualTo(ExecutionStatus.ABORTED);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForInlinePipeline() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .runSequence(1)
                                                                .executionInputConfigured(false)
                                                                .planExecutionId(planId)
                                                                .storeType(StoreType.INLINE)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null);
    assertThat(executionSummaryDTO.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();
    assertThat(executionSummaryDTO.getExecutionInputConfigured()).isEqualTo(false);

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .runSequence(1)
                                 .planExecutionId(planId)
                                 .storeType(StoreType.INLINE)
                                 .executionInputConfigured(true)
                                 .build();

    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null);
    assertThat(executionSummaryDTO.getExecutionInputConfigured()).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForRemotePipeline() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .storeType(StoreType.REMOTE)
                                                                .connectorRef("conn")
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null);
    assertThat(executionSummaryDTO.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(executionSummaryDTO.getConnectorRef()).isEqualTo("conn");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForStagesExecutionMetadata() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null);
    assertThat(executionSummaryDTO.isStagesExecution()).isFalse();
    assertThat(executionSummaryDTO.getStagesExecuted()).isNull();
    assertThat(executionSummaryDTO.isAllowStageExecutions()).isFalse();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();

    PipelineExecutionSummaryEntity executionSummaryEntityWithStages =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .runSequence(1)
            .planExecutionId(planId)
            .stagesExecutionMetadata(StagesExecutionMetadata.builder()
                                         .isStagesExecution(true)
                                         .stageIdentifiers(Collections.singletonList("s1"))
                                         .fullPipelineYaml(getPipelineYaml())
                                         .build())
            .allowStagesExecution(true)
            .build();
    PipelineExecutionSummaryDTO executionSummaryDTOWithStages =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithStages, null);
    assertThat(executionSummaryDTOWithStages.isStagesExecution()).isTrue();
    assertThat(executionSummaryDTOWithStages.getStagesExecuted()).hasSize(1);
    assertThat(executionSummaryDTOWithStages.getStagesExecuted().contains("s1")).isTrue();
    assertThat(executionSummaryDTOWithStages.getStagesExecutedNames()).hasSize(1);
    assertThat(executionSummaryDTOWithStages.getStagesExecutedNames().get("s1")).isEqualTo("s one");
    assertThat(executionSummaryDTOWithStages.isAllowStageExecutions()).isTrue();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();
  }

  private String getPipelineYaml() {
    return "pipeline:\n"
        + "  identifier: p1\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      name: s one\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      name: s two";
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testToDtoForRetryHistory() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .build();

    // when isLatest is notSet (default value is true)
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null);
    assertThat(executionSummaryDTO.isCanRetry()).isEqualTo(true);
    assertThat(executionSummaryDTO.isShowRetryHistory()).isEqualTo(false);

    // added rootParentId and setting isLatest false
    PipelineExecutionSummaryEntity executionSummaryEntityWithRootParentId =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .runSequence(1)
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootParentId").build())
            .isLatestExecution(false)
            .planExecutionId(planId)
            .build();
    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithRootParentId, null);
    assertThat(executionSummaryDTO.isCanRetry()).isEqualTo(false);
    assertThat(executionSummaryDTO.isShowRetryHistory()).isEqualTo(true);

    // isLatestTrue
    PipelineExecutionSummaryEntity executionSummaryEntityWithIsLatest =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .runSequence(1)
            .isLatestExecution(true)
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootParentId").build())
            .planExecutionId(planId)
            .build();
    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithIsLatest, null);
    assertThat(executionSummaryDTO.isCanRetry()).isEqualTo(true);
    assertThat(executionSummaryDTO.isShowRetryHistory()).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStagesCount() {
    String startingNodeId = "SQBPoxJCTi6k_gxILqb7SA";
    String otherNodeId = "MQ5AFrizSeesedKbw-lZeQ";

    GraphLayoutNodeDTO startingNode =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.singletonList(otherNodeId)).build())
            .build();
    GraphLayoutNodeDTO otherNode =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.emptyList()).build())
            .build();
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, startingNode);
    layoutNodeDTOMap.put(otherNodeId, otherNode);
    int stagesCount = PipelineExecutionSummaryDtoMapper.getStagesCount(layoutNodeDTOMap, startingNodeId);
    assertThat(stagesCount).isEqualTo(2);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStagesCountWithExecutionStatus() {
    String startingNodeId = "SQBPoxJCTi6k_gxILqb7SA";
    String otherNodeId = "MQ5AFrizSeesedKbw-lZeQ";

    GraphLayoutNodeDTO startingNode =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.singletonList(otherNodeId)).build())
            .status(ExecutionStatus.SUCCESS)
            .build();
    GraphLayoutNodeDTO otherNode =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.emptyList()).build())
            .status(ExecutionStatus.ABORTED)
            .build();
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, startingNode);
    layoutNodeDTOMap.put(otherNodeId, otherNode);
    int stagesCount =
        PipelineExecutionSummaryDtoMapper.getStagesCount(layoutNodeDTOMap, startingNodeId, ExecutionStatus.ABORTED);
    assertThat(stagesCount).isEqualTo(1);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToDtoForParentStageInfo() {
    AbortedBy abortedBy =
        AbortedBy.builder().userName("user1").email("email").createdAt(System.currentTimeMillis()).build();
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .abortedBy(abortedBy)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getParentStageInfo()).isNull();
    assertEquals(executionSummaryDTO.getAbortedBy(), abortedBy);
    PipelineStageInfo pipelineStageInfo = PipelineStageInfo.newBuilder()
                                              .setHasParentPipeline(true)
                                              .setExecutionId("executionId")
                                              .setIdentifier("id")
                                              .setStageNodeId("stageNodeId")
                                              .setOrgId("orgId")
                                              .setProjectId("projectId")
                                              .setRunSequence(4556)
                                              .build();
    PipelineExecutionSummaryEntity executionSummaryWithParentStage = PipelineExecutionSummaryEntity.builder()
                                                                         .accountId(accountId)
                                                                         .orgIdentifier(orgId)
                                                                         .projectIdentifier(projId)
                                                                         .pipelineIdentifier(pipelineId)
                                                                         .runSequence(1)
                                                                         .planExecutionId(planId)
                                                                         .parentStageInfo(pipelineStageInfo)
                                                                         .build();
    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(executionSummaryWithParentStage, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getParentStageInfo()).isNotNull();
    assertEquals(executionSummaryDTO.getParentStageInfo(), pipelineStageInfo);
  }
}
