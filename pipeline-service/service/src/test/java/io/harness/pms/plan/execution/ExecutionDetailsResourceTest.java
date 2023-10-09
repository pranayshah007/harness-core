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
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineExecutionNotesDTO;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetailDTO;
import io.harness.pms.plan.execution.beans.dto.NodeExecutionSubGraphResponse;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionIdentifierSummaryDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.pms.plan.execution.service.ExecutionGraphService;
import io.harness.pms.plan.execution.service.ExpressionEvaluatorService;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
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
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock ExecutionHelper executionHelper;
  @Mock ExecutionGraphService executionGraphService;
  @Mock ExpressionEvaluatorService expressionEvaluatorService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";

  private final List<String> PIPELINE_IDENTIFIER_LIST = List.of(PIPELINE_IDENTIFIER);
  private final String PLAN_EXECUTION_ID = "planId";
  private final String STAGE_NODE_ID = "stageNodeId";
  private final String CHILD_STAGE_NODE_ID = "childStageNodeId";
  private final Boolean RENDER_FULL_BOTTOM_GRAPH = true;
  private final String NODE_EXECUTION_ID = "nodeExecutionId";
  private final String YAML = "yamlContent";

  PipelineEntity entity;
  PipelineEntity entityWithVersion;
  PipelineExecutionSummaryEntity executionSummaryEntity;
  OrchestrationGraphDTO orchestrationGraph;
  EntityGitDetails entityGitDetails;
  PipelineExecutionIdentifierSummaryDTO pipelineExecutionIdentifierSummaryDTO;

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

    pipelineExecutionIdentifierSummaryDTO = PipelineExecutionIdentifierSummaryDTO.builder()
                                                .orgIdentifier(ORG_IDENTIFIER)
                                                .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                .projectIdentifier(PROJ_IDENTIFIER)
                                                .runSequence(2)
                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                .status(ExecutionStatus.ABORTED)
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
            .getListOfExecutions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, 0, 10, null, null, false,
                null, null, null, false, GitEntityFindInfoDTO.builder().build())
            .getData();
    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);

    PipelineExecutionSummaryDTO responseDTO = content.toList().get(0);
    assertThat(responseDTO.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(responseDTO.getRunSequence()).isZero();
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
            false, false, false);

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        new PageImpl<>(Collections.singletonList(executionSummaryEntity), pageable, 1);
    doReturn(pipelineExecutionSummaryEntities)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(criteria, pageable);

    Page<PipelineExecutionSummaryDTO> content =
        executionDetailsResource
            .getListOfExecutions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, PIPELINE_IDENTIFIER, 0, 10, null,
                null, false, null, null, null, false, GitEntityFindInfoDTO.builder().branch("branchName").build())
            .getData();
    assertThat(content).isNotEmpty();
    assertThat(content.getNumberOfElements()).isEqualTo(1);

    PipelineExecutionSummaryDTO responseDTO = content.toList().get(0);
    assertThat(responseDTO.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(responseDTO.getRunSequence()).isZero();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsInvalidPage() {
    try {
      executionDetailsResource
          .getListOfExecutions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, -1, 10, null, null, false, null,
              null, null, false, GitEntityFindInfoDTO.builder().build())
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsInvalidSizeEQ0() {
    try {
      executionDetailsResource
          .getListOfExecutions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, 0, 0, null, null, false, null,
              null, null, false, GitEntityFindInfoDTO.builder().build())
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsInvalidSizeGT1000() {
    try {
      executionDetailsResource
          .getListOfExecutions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, 0, 1001, null, null, false,
              null, null, null, false, GitEntityFindInfoDTO.builder().build())
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsEmptyList() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        new PageImpl<>(Collections.emptyList(), pageable, 0);
    doReturn(pipelineExecutionSummaryEntities)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(any(), any());
    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pmsPipelineService)
        .getAndValidatePipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    doReturn(null).when(pmsGitSyncHelper).getGitSyncBranchContextBytesThreadLocal();

    Page<PipelineExecutionSummaryDTO> content =
        executionDetailsResource
            .getListOfExecutions(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, 0, 10, null, null, false,
                null, null, null, false, GitEntityFindInfoDTO.builder().build())
            .getData();
    assertThat(content).isEmpty();
    assertThat(content.getNumberOfElements()).isZero();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testExecutionIdAndStatus() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        new PageImpl<>(Collections.singletonList(executionSummaryEntity), pageable, 1);

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(pmsExecutionService)
        .formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null,
            ExecutionStatus.getListExecutionStatus(StatusUtils.finalStatuses()), false, false, true);

    List<String> projections =
        Arrays.asList(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.runSequence,
            PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.pipelineIdentifier,
            PlanExecutionSummaryKeys.projectIdentifier, PlanExecutionSummaryKeys.status);

    doReturn(pipelineExecutionSummaryEntities)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntityWithProjection(criteria, pageable, projections);

    Page<PipelineExecutionIdentifierSummaryDTO> content =
        executionDetailsResource
            .getListOfExecutionIdentifier(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, 0, 10, null)
            .getData();
    assertThat(content).isNotEmpty();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testExecutionIdAndStatusInvalidPage() {
    try {
      executionDetailsResource
          .getListOfExecutionIdentifier(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, -1, 10, null)
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testExecutionIdAndStatusInvalidSizeEQ0() {
    try {
      executionDetailsResource
          .getListOfExecutionIdentifier(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, 0, 0, null)
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testExecutionIdAndStatusInvalidPageGT1000() {
    try {
      executionDetailsResource
          .getListOfExecutionIdentifier(
              ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, 0, 10001, null)
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testExecutionIdAndStatusEmptyList() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        new PageImpl<>(Collections.emptyList(), pageable, 0);

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(pmsExecutionService)
        .formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null,
            ExecutionStatus.getListExecutionStatus(StatusUtils.finalStatuses()), false, false, true);

    List<String> projections =
        Arrays.asList(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.runSequence,
            PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.pipelineIdentifier,
            PlanExecutionSummaryKeys.projectIdentifier, PlanExecutionSummaryKeys.status);

    doReturn(pipelineExecutionSummaryEntities)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntityWithProjection(criteria, pageable, projections);

    Page<PipelineExecutionIdentifierSummaryDTO> content =
        executionDetailsResource
            .getListOfExecutionIdentifier(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, 0, 10, null)
            .getData();
    assertThat(content).isEmpty();
    assertThat(content.getNumberOfElements()).isZero();
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
    assertThat(responseDTO.getRunSequence()).isZero();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsForRemotePipelinesInvalidPage() {
    try {
      executionDetailsResource
          .getListOfExecutionsWithOrOperator(
              ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, -1, 10, null, null)
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsForRemotePipelinesInvalidSizeEQ0() {
    try {
      executionDetailsResource
          .getListOfExecutionsWithOrOperator(
              ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, 0, 0, null, null)
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsForRemotePipelinesInvalidSizeGT1000() {
    try {
      executionDetailsResource
          .getListOfExecutionsWithOrOperator(
              ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, 0, 1001, null, null)
          .getData();
    } catch (InvalidRequestException invalidRequestException) {
      assertEquals(invalidRequestException.getMessage(),
          "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and <=1000");
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsWithOrOperator() {
    Criteria criteria = Criteria.where("a").is("b");

    Page<PipelineExecutionSummaryEntity> mockPage = mock(Page.class);
    when(pmsExecutionService.formCriteriaOROperatorOnModules(any(), any(), any(), any(), any(), any()))
        .thenReturn(criteria);
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(eq(criteria), any(Pageable.class))).thenReturn(mockPage);

    Pageable pageRequest = PageRequest.of(0, 10, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryDTO> planExecutionSummaryDTOS =
        pmsExecutionService.getPipelineExecutionSummaryEntity(criteria, pageRequest)
            .map(e
                -> PipelineExecutionSummaryDtoMapper.toDto(e,
                    e.getEntityGitDetails() != null
                        ? e.getEntityGitDetails()
                        : pmsGitSyncHelper.getEntityGitDetailsFromBytes(e.getGitSyncBranchContext())));

    ResponseDTO<Page<PipelineExecutionSummaryDTO>> responseDTO = ResponseDTO.newResponse(planExecutionSummaryDTOS);
    assertThat(responseDTO).isNotNull();
    assertEquals("SUCCESS", responseDTO.getStatus().toString());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetExecutionDetailV2() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(executionSummaryEntity);
    when(pmsGitSyncHelper.getEntityGitDetailsFromBytes(any())).thenReturn(entityGitDetails);

    PipelineExecutionDetailDTO pipelineExecutionDetailDTO = mock(PipelineExecutionDetailDTO.class);
    when(executionHelper.getResponseDTO(any(), any(), any(), anyBoolean(), any(), any()))
        .thenReturn(pipelineExecutionDetailDTO);

    ResponseDTO<PipelineExecutionDetailDTO> responseDTO =
        executionDetailsResource.getExecutionDetailV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, STAGE_NODE_ID,
            STAGE_NODE_ID, CHILD_STAGE_NODE_ID, RENDER_FULL_BOTTOM_GRAPH, PLAN_EXECUTION_ID);

    assertThat(responseDTO).isNotNull();
    assertEquals("SUCCESS", responseDTO.getStatus().toString());
    assertEquals(pipelineExecutionDetailDTO, responseDTO.getData());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetExecutionSubGraphForNodeExecution() {
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(executionSummaryEntity);

    NodeExecutionSubGraphResponse nodeExecutionSubGraphResponse = NodeExecutionSubGraphResponse.builder().build();
    when(executionGraphService.getNodeExecutionSubGraph(any(), any())).thenReturn(nodeExecutionSubGraphResponse);

    ResponseDTO<NodeExecutionSubGraphResponse> responseDTO =
        executionDetailsResource.getExecutionSubGraphForNodeExecution(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, NODE_EXECUTION_ID, PLAN_EXECUTION_ID);

    assertThat(responseDTO).isNotNull();
    assertEquals("SUCCESS", responseDTO.getStatus().toString());
    assertEquals(nodeExecutionSubGraphResponse, responseDTO.getData());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetExpressionEvaluated() {
    ExpressionEvaluationDetailDTO mockEvaluationDetailDTO = ExpressionEvaluationDetailDTO.builder().build();
    when(expressionEvaluatorService.evaluateExpression(eq(PLAN_EXECUTION_ID), eq(YAML)))
        .thenReturn(mockEvaluationDetailDTO);

    ResponseDTO<ExpressionEvaluationDetailDTO> responseDTO = executionDetailsResource.getExpressionEvaluated(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, YAML);

    assertThat(responseDTO).isNotNull();
    assertEquals("SUCCESS", responseDTO.getStatus().toString());
    assertEquals(mockEvaluationDetailDTO, responseDTO.getData());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsForRemotePipelinesEmptyList() {
    ByteString gitSyncBranchContext = ByteString.copyFromUtf8("random byte string");
    doReturn(gitSyncBranchContext).when(pmsGitSyncHelper).getGitSyncBranchContextBytesThreadLocal();

    Criteria criteria = Criteria.where("a").is("b");
    doReturn(criteria)
        .when(pmsExecutionService)
        .formCriteriaOROperatorOnModules(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, null, null);

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PipelineExecutionSummaryKeys.startTs));
    Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        new PageImpl<>(Collections.emptyList(), pageable, 0);
    doReturn(pipelineExecutionSummaryEntities)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(criteria, pageable);

    Page<PipelineExecutionSummaryDTO> content = executionDetailsResource
                                                    .getListOfExecutionsWithOrOperator(ACCOUNT_ID, ORG_IDENTIFIER,
                                                        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, 0, 10, null, null)
                                                    .getData();
    assertThat(content).isEmpty();
    assertThat(content.getNumberOfElements()).isZero();
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
    assertThat(executionDetails.getData().getPipelineExecutionSummary().getRunSequence()).isZero();
    assertThat(executionDetails.getData().getExecutionGraph().getRootNodeId()).isEqualTo(STAGE_NODE_ID);
    assertThat(executionDetails.getData().getExecutionGraph().getNodeMap().size()).isZero();
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
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetExecutions() {
    ExecutionDataResponseDTO mockExecutionDataResponseDTO = ExecutionDataResponseDTO.builder().build();
    when(pmsExecutionService.getExecutionData(eq(PLAN_EXECUTION_ID))).thenReturn(mockExecutionDataResponseDTO);

    ResponseDTO<ExecutionDataResponseDTO> responseDTO =
        executionDetailsResource.getExecutions(ACCOUNT_ID, PLAN_EXECUTION_ID);

    assertThat(responseDTO).isNotNull();
    assertEquals("SUCCESS", responseDTO.getStatus().toString());
    assertEquals(mockExecutionDataResponseDTO, responseDTO.getData());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetExecutionsDetails() {
    ExecutionMetaDataResponseDetailsDTO mockExecutionMetaDataResponseDetailsDTO =
        ExecutionMetaDataResponseDetailsDTO.builder().build();
    when(pmsExecutionService.getExecutionDataDetails(eq(PLAN_EXECUTION_ID)))
        .thenReturn(mockExecutionMetaDataResponseDetailsDTO);

    ResponseDTO<ExecutionMetaDataResponseDetailsDTO> responseDTO =
        executionDetailsResource.getExecutionsDetails(ACCOUNT_ID, PLAN_EXECUTION_ID);

    assertThat(responseDTO).isNotNull();
    assertEquals("SUCCESS", responseDTO.getStatus().toString());
    assertEquals(mockExecutionMetaDataResponseDetailsDTO, responseDTO.getData());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetInputsetYaml() {
    boolean resolveExpressions = false;
    String inputSetYaml = "inputSetYaml";

    InputSetYamlWithTemplateDTO inputSetYamlWithTemplateDTO =
        InputSetYamlWithTemplateDTO.builder().inputSetYaml(inputSetYaml).build();
    when(
        pmsExecutionService.getInputSetYamlWithTemplate(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER),
            eq(PLAN_EXECUTION_ID), eq(false), eq(resolveExpressions), eq(ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS)))
        .thenReturn(inputSetYamlWithTemplateDTO);

    String result = executionDetailsResource.getInputsetYaml(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        resolveExpressions, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, PLAN_EXECUTION_ID);

    assertThat(result).isNotNull();
    assertEquals(inputSetYaml, result);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetInputsetYamlV2() {
    boolean resolveExpressions = false;
    String inputSetYaml = "inputSetYaml";

    InputSetYamlWithTemplateDTO inputSetYamlWithTemplateDTO =
        InputSetYamlWithTemplateDTO.builder().inputSetYaml(inputSetYaml).build();
    when(
        pmsExecutionService.getInputSetYamlWithTemplate(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER),
            eq(PLAN_EXECUTION_ID), eq(false), eq(resolveExpressions), eq(ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS)))
        .thenReturn(inputSetYamlWithTemplateDTO);

    ResponseDTO<InputSetYamlWithTemplateDTO> response =
        executionDetailsResource.getInputsetYamlV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, resolveExpressions,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, PLAN_EXECUTION_ID);

    assertThat(response).isNotNull();
    assertEquals("SUCCESS", response.getStatus().toString());
    assertEquals(inputSetYamlWithTemplateDTO, response.getData());
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
