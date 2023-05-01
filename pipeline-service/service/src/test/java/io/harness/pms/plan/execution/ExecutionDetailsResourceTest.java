/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.RepresentationStrategy;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationAdjacencyListDTO;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineExecutionNotesDTO;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Criteria;

public class ExecutionDetailsResourceTest extends CategoryTest {
  @InjectMocks ExecutionDetailsResource executionDetailsResource;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock AccessControlClient accessControlClient;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";

  private final List<String> PIPELINE_IDENTIFIER_LIST = Arrays.asList(PIPELINE_IDENTIFIER);
  private final String PLAN_EXECUTION_ID = "planId";
  private final String STAGE_NODE_ID = "stageNodeId";

  PipelineEntity entity;
  PipelineEntity entityWithVersion;
  PipelineExecutionSummaryEntity executionSummaryEntity;
  OrchestrationGraphDTO orchestrationGraph;
  EntityGitDetails entityGitDetails;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    entity = PipelineEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(PIPELINE_IDENTIFIER)
                 .name(PIPELINE_IDENTIFIER)
                 .build();

    entityGitDetails = EntityGitDetails.builder()
                           .branch("branch")
                           .repoIdentifier("repo")
                           .filePath("file.yaml")
                           .rootFolder("root/.harness/")
                           .build();

    entityWithVersion = PipelineEntity.builder()
                            .accountId(ACCOUNT_ID)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .identifier(PIPELINE_IDENTIFIER)
                            .name(PIPELINE_IDENTIFIER)
                            .stageCount(1)
                            .stageName("qaStage")
                            .version(1L)
                            .build();

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                 .planExecutionId(PLAN_EXECUTION_ID)
                                 .name(PLAN_EXECUTION_ID)
                                 .runSequence(0)
                                 .entityGitDetails(entityGitDetails)
                                 .build();

    orchestrationGraph = OrchestrationGraphDTO.builder()
                             .planExecutionId(PLAN_EXECUTION_ID)
                             .rootNodeIds(Collections.singletonList(STAGE_NODE_ID))
                             .adjacencyList(OrchestrationAdjacencyListDTO.builder()
                                                .graphVertexMap(Collections.emptyMap())
                                                .adjacencyMap(Collections.emptyMap())
                                                .build())
                             .build();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetListOfExecutions() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        new PageImpl<>(Collections.singletonList(executionSummaryEntity), pageable, 1);
    doReturn(pipelineExecutionSummaryEntities)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(any(), any());
    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pmsPipelineService)
        .getAndValidatePipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    doReturn(null).when(pmsGitSyncHelper).getGitSyncBranchContextBytesThreadLocal();

    Page<PipelineExecutionSummaryDTO> content =
        executionDetailsResource
            .getListOfExecutions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, 0, 10, null, null, null, null,
                null, false, GitEntityFindInfoDTO.builder().build())
            .getData();
    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);

    PipelineExecutionSummaryDTO responseDTO = content.toList().get(0);
    assertThat(responseDTO.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(responseDTO.getRunSequence()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsForRemotePipeline() {
    ByteString gitSyncBranchContext = ByteString.copyFromUtf8("random byte string");
    doReturn(gitSyncBranchContext).when(pmsGitSyncHelper).getGitSyncBranchContextBytesThreadLocal();

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(pmsExecutionService)
        .formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, null,
            false, false, true);

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        new PageImpl<>(Collections.singletonList(executionSummaryEntity), pageable, 1);
    doReturn(pipelineExecutionSummaryEntities)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(criteria, pageable);

    Page<PipelineExecutionSummaryDTO> content =
        executionDetailsResource
            .getListOfExecutions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, PIPELINE_IDENTIFIER, 0, 10, null,
                null, null, null, null, false, GitEntityFindInfoDTO.builder().branch("branchName").build())
            .getData();
    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);

    PipelineExecutionSummaryDTO responseDTO = content.toList().get(0);
    assertThat(responseDTO.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(responseDTO.getRunSequence()).isEqualTo(0);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsForRemotePipelines() {
    ByteString gitSyncBranchContext = ByteString.copyFromUtf8("random byte string");
    doReturn(gitSyncBranchContext).when(pmsGitSyncHelper).getGitSyncBranchContextBytesThreadLocal();

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(pmsExecutionService)
        .formCriteriaOROperatorOnModules(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, null, null);

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PipelineExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        new PageImpl<>(Collections.singletonList(executionSummaryEntity), pageable, 1);
    doReturn(pipelineExecutionSummaryEntities)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(criteria, pageable);

    Page<PipelineExecutionSummaryDTO> content = executionDetailsResource
                                                    .getListOfExecutionsWithOrOperator(ACCOUNT_ID, ORG_IDENTIFIER,
                                                        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, 0, 10, null, null)
                                                    .getData();
    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);

    PipelineExecutionSummaryDTO responseDTO = content.toList().get(0);
    assertThat(responseDTO.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(responseDTO.getRunSequence()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetExecutionDetail() {
    doReturn(executionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID, false);
    doReturn(orchestrationGraph)
        .when(pmsExecutionService)
        .getOrchestrationGraph(STAGE_NODE_ID, PLAN_EXECUTION_ID, null);

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());

    ResponseDTO<PipelineExecutionDetailDTO> executionDetails = executionDetailsResource.getExecutionDetail(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, STAGE_NODE_ID, null, PLAN_EXECUTION_ID);

    assertThat(executionDetails.getData().getPipelineExecutionSummary().getPipelineIdentifier())
        .isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(executionDetails.getData().getPipelineExecutionSummary().getPlanExecutionId())
        .isEqualTo(PLAN_EXECUTION_ID);
    assertThat(executionDetails.getData().getPipelineExecutionSummary().getRunSequence()).isEqualTo(0);
    assertThat(executionDetails.getData().getExecutionGraph().getRootNodeId()).isEqualTo(STAGE_NODE_ID);
    assertThat(executionDetails.getData().getExecutionGraph().getNodeMap().size()).isEqualTo(0);
    assertThat(executionDetails.getData().getExecutionGraph().getRepresentationStrategy())
        .isEqualTo(RepresentationStrategy.CAMELCASE);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetExecutionDetailWithInvalidExecutionId() {
    String invalidPlanExecutionId = "invalidId";
    doThrow(InvalidRequestException.class)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, invalidPlanExecutionId, false);

    assertThatThrownBy(()
                           -> executionDetailsResource.getExecutionDetail(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               STAGE_NODE_ID, null, invalidPlanExecutionId))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetListOfRepos() {
    List<String> repoList = new ArrayList<>();
    repoList.add("testRepo");
    repoList.add("testRepo2");

    PMSPipelineListRepoResponse pmsPipelineListRepoResponse =
        PMSPipelineListRepoResponse.builder().repositories(repoList).build();

    doReturn(pmsPipelineListRepoResponse).when(pmsExecutionService).getListOfRepo(any());

    PMSPipelineListRepoResponse uniqueRepos =
        executionDetailsResource.getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "").getData();
    assertEquals(uniqueRepos.getRepositories(), repoList);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetListOfBranch() {
    List<String> branchList = new ArrayList<>();
    branchList.add("main");
    branchList.add("main-patch");

    PMSPipelineListBranchesResponse pmsPipelineListBranchesResponse =
        PMSPipelineListBranchesResponse.builder().branches(branchList).build();

    doReturn(pmsPipelineListBranchesResponse).when(pmsExecutionService).getListOfBranches(any());

    PMSPipelineListBranchesResponse uniqueBranches =
        executionDetailsResource
            .getListOfBranches(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "testRepo")
            .getData();
    assertEquals(uniqueBranches.getBranches(), branchList);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetNotesForPlanExecution() {
    doReturn("Notes").when(planExecutionMetadataService).getNotesForExecution("executionId");
    PipelineExecutionNotesDTO pipelineExecutionNotesResponse =
        executionDetailsResource.getNotesForPlanExecution(ACCOUNT_ID, "executionId").getData();
    assertEquals(pipelineExecutionNotesResponse.getNotes(), "Notes");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateNotesForPlanExecution() {
    doReturn("newNotes").when(planExecutionMetadataService).updateNotesForExecution("executionId", "newNotes");
    PipelineExecutionNotesDTO pipelineExecutionNotesResponse =
        executionDetailsResource
            .updateNotesForPlanExecution(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "newNotes", "executionId")
            .getData();
    assertEquals(pipelineExecutionNotesResponse.getNotes(), "newNotes");
  }
}
