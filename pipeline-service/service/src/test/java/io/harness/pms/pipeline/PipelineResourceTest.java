/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SATYAM;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionNode;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationAdjacencyListDTO;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JsonSchemaValidationException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.execution.NodeExecution;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.PipelineMoveConfigRequestDTO;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.mappers.NodeExecutionToExecutioNodeMapper;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.pms.pipeline.service.PipelineGetResult;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.rule.Owner;
import io.harness.steps.template.TemplateStepNode;
import io.harness.steps.template.stage.TemplateStageNode;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(PIPELINE)
public class PipelineResourceTest extends CategoryTest {
  PipelineResourceImpl pipelineResource;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock NodeExecutionToExecutioNodeMapper nodeExecutionToExecutioNodeMapper;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock VariableCreatorMergeService variableCreatorMergeService;
  @Mock PipelineCloneHelper pipelineCloneHelper;
  @Mock PmsFeatureFlagHelper featureFlagHelper;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PipelineAsyncValidationService pipelineAsyncValidationService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private final String PIPELINE_NAME = "basichttpFail";
  private final String STAGE = "qaStage";
  private String yaml;
  private String simplifiedYaml;
  private String simplifiedYamlWithoutName;

  PipelineEntity entity;
  PipelineEntity simplifiedEntity;
  PipelineEntity entityWithVersion;
  PipelineEntity simplifiedEntityWithVersion;
  PipelineExecutionSummaryEntity executionSummaryEntity;
  OrchestrationGraphDTO orchestrationGraph;
  EntityGitDetails entityGitDetails;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    pipelineResource = new PipelineResourceImpl(pmsPipelineService, pmsPipelineServiceHelper, nodeExecutionService,
        nodeExecutionToExecutioNodeMapper, pipelineTemplateHelper, featureFlagHelper, variableCreatorMergeService,
        pipelineCloneHelper, pipelineMetadataService, pipelineAsyncValidationService);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "failure-strategy.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    entity = PipelineEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(PIPELINE_IDENTIFIER)
                 .name(PIPELINE_IDENTIFIER)
                 .yaml(yaml)
                 .isDraft(false)
                 .allowStageExecutions(false)
                 .build();

    filename = "simplified-pipeline.yaml";
    simplifiedYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    simplifiedYamlWithoutName = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("pipeline-without-name-v1.yaml")), StandardCharsets.UTF_8);
    simplifiedEntity = PipelineEntity.builder()
                           .accountId(ACCOUNT_ID)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .projectIdentifier(PROJ_IDENTIFIER)
                           .identifier(PIPELINE_IDENTIFIER)
                           .name(PIPELINE_IDENTIFIER)
                           .yaml(simplifiedYaml)
                           .isDraft(false)
                           .harnessVersion(PipelineVersion.V1)
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
                            .yaml(yaml)
                            .stageCount(1)
                            .stageName(STAGE)
                            .version(1L)
                            .allowStageExecutions(false)
                            .build();

    simplifiedEntityWithVersion = PipelineEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .projectIdentifier(PROJ_IDENTIFIER)
                                      .identifier(PIPELINE_IDENTIFIER)
                                      .name(PIPELINE_IDENTIFIER)
                                      .yaml(simplifiedYaml)
                                      .isDraft(false)
                                      .harnessVersion(PipelineVersion.V1)
                                      .version(1L)
                                      .build();

    String PLAN_EXECUTION_ID = "planId";
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

    String STAGE_NODE_ID = "stageNodeId";
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
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipeline() {
    doReturn(false).when(featureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(entity, true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(entity, BOOLEAN_FALSE_VALUE);
    ResponseDTO<String> identifier = pipelineResource.createPipeline(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, yaml);
    assertThat(identifier.getData()).isNotEmpty();
    assertThat(identifier.getData()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreatePipelineV2() {
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(true).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(entity, false);
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.createPipelineV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineV2WithSuccess() {
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(entity, false);
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.createPipelineV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isFalse();
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testClonePipelineWithAccessFailure() {
    ClonePipelineDTO dummy = ClonePipelineDTO.builder().build();
    doThrow(new AccessDeniedException("denied", null)).when(pipelineCloneHelper).checkAccess(dummy, ACCOUNT_ID);
    assertThatThrownBy(() -> pipelineResource.clonePipeline(ACCOUNT_ID, null, dummy))
        .hasMessage("denied")
        .isInstanceOf(AccessDeniedException.class);
    verify(pmsPipelineService, times(0)).validateAndClonePipeline(any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testClonePipeline() {
    ClonePipelineDTO dummy = ClonePipelineDTO.builder().build();
    PipelineSaveResponse dummyResponse = PipelineSaveResponse.builder().identifier("id").build();
    doReturn(dummyResponse).when(pmsPipelineService).validateAndClonePipeline(dummy, ACCOUNT_ID);
    ResponseDTO<PipelineSaveResponse> response = pipelineResource.clonePipeline(ACCOUNT_ID, null, dummy);
    assertThat(response.getData()).isEqualTo(dummyResponse);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipeline() {
    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(entityWithVersion)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false, false);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(any(), any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, BOOLEAN_FALSE_VALUE);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineWithAsyncValidations() {
    doReturn(PipelineGetResult.builder()
                 .pipelineEntity(Optional.of(entityWithVersion))
                 .asyncValidationUUID("asyncUuid")
                 .build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false, true);
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, true, BOOLEAN_FALSE_VALUE);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getValidationUuid()).isEqualTo("asyncUuid");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetPipelineFromFallbackBranch() {
    PipelineEntity pipelineEntityCreatedInNonDefaultBranch = PipelineEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .identifier(PIPELINE_IDENTIFIER)
                                                                 .name(PIPELINE_IDENTIFIER)
                                                                 .yaml(yaml)
                                                                 .stageCount(1)
                                                                 .stageName(STAGE)
                                                                 .version(1L)
                                                                 .allowStageExecutions(false)
                                                                 .build();
    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(pipelineEntityCreatedInNonDefaultBranch)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, true, false, false);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(any(), any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, true, false, BOOLEAN_FALSE_VALUE);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetPipelineLoadFromCache() {
    PipelineEntity pipelineEntityCreatedInNonDefaultBranch = PipelineEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .identifier(PIPELINE_IDENTIFIER)
                                                                 .name(PIPELINE_IDENTIFIER)
                                                                 .yaml(yaml)
                                                                 .stageCount(1)
                                                                 .stageName(STAGE)
                                                                 .version(1L)
                                                                 .allowStageExecutions(false)
                                                                 .build();

    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(pipelineEntityCreatedInNonDefaultBranch)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, true, false);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(any(), any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, "true");
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineWithUnresolvedTemplates() {
    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.of(entityWithVersion)).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false, false);
    doThrow(new InvalidRequestException("random exception"))
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, BOOLEAN_FALSE_VALUE);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
    assertThat(responseDTO.getData().getResolvedTemplatesPipelineYaml()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineWithInvalidYAML() {
    YamlSchemaErrorWrapperDTO errorWrapper =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(Collections.singletonList(YamlSchemaErrorDTO.builder().fqn("fqn").message("msg").build()))
            .build();
    doThrow(new InvalidYamlException("errorMsg", null, errorWrapper, yaml))
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false, false);

    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, BOOLEAN_FALSE_VALUE);
    PMSPipelineResponseDTO data = responseDTO.getData();
    assertThat(data.getEntityValidityDetails().isValid()).isFalse();
    assertThat(data.getEntityValidityDetails().getInvalidYaml()).isEqualTo(yaml);
    assertThat(data.getYamlPipeline()).isEqualTo(yaml);
    assertThat(data.getYamlSchemaErrorWrapper()).isEqualTo(errorWrapper);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineWithGovernanceErrors() {
    GovernanceMetadata governanceMetadata =
        GovernanceMetadata.newBuilder().setDeny(true).setAccountId(ACCOUNT_ID).build();
    doThrow(new PolicyEvaluationFailureException("errorMsg", governanceMetadata, yaml))
        .when(pmsPipelineService)
        .getAndValidatePipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false, false);

    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true, false, false, BOOLEAN_FALSE_VALUE);
    PMSPipelineResponseDTO data = responseDTO.getData();
    assertThat(data.getEntityValidityDetails().isValid()).isFalse();
    assertThat(data.getEntityValidityDetails().getInvalidYaml()).isEqualTo(yaml);
    assertThat(data.getYamlPipeline()).isEqualTo(yaml);
    assertThat(data.getGovernanceMetadata()).isEqualTo(governanceMetadata);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineWithInvalidPipelineId() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";

    doReturn(PipelineGetResult.builder().pipelineEntity(Optional.empty()).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, false, false,
            false, false, false);

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineByIdentifier(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, null, true, false, false, BOOLEAN_FALSE_VALUE))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage(String.format(
            "Pipeline with the given ID: %s does not exist or has been deleted", incorrectPipelineIdentifier));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithWrongIdentifier() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, BOOLEAN_FALSE_VALUE);
    assertThatThrownBy(()
                           -> pipelineResource.updatePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, null, null, null, null, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Expected Pipeline identifier in YAML to be [notTheIdentifierWeNeed], but was [basichttpFail]");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipeline() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityWithVersion).build();
    doReturn(pipelineCRUDResult).when(pmsPipelineService).validateAndUpdatePipeline(entity, ChangeType.MODIFY, true);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, BOOLEAN_FALSE_VALUE);
    ResponseDTO<String> responseDTO = pipelineResource.updatePipeline(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, yaml);
    assertThat(responseDTO.getData()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpdatePipelineV2() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityWithVersion).build();
    doReturn(pipelineCRUDResult).when(pmsPipelineService).validateAndUpdatePipeline(entity, ChangeType.MODIFY, false);
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineV2WithSuccess() {
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(entity, ChangeType.MODIFY, false);
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isFalse();
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  @Ignore("Ignored till Schema validation is behind FF")
  public void testUpdatePipelineWithSchemaErrors() {
    doThrow(JsonSchemaValidationException.class)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(entity, ChangeType.MODIFY, false);
    assertThatThrownBy(()
                           -> pipelineResource.updatePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, null, null, null, null, yaml))
        .isInstanceOf(JsonSchemaValidationException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeletePipeline() {
    doReturn(true)
        .when(pmsPipelineService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    ResponseDTO<Boolean> deleteResponse =
        pipelineResource.deletePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(deleteResponse.getData()).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineSummary() {
    doReturn(Optional.of(entityWithVersion))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false);
    ResponseDTO<PMSPipelineSummaryResponseDTO> pipelineSummary = pipelineResource.getPipelineSummary(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, false);
    assertThat(pipelineSummary.getData().getName()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(pipelineSummary.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(pipelineSummary.getData().getDescription()).isNull();
    assertThat(pipelineSummary.getData().getTags()).isEmpty();
    assertThat(pipelineSummary.getData().getVersion()).isEqualTo(1L);
    assertThat(pipelineSummary.getData().getNumOfStages()).isEqualTo(1L);
    assertThat(pipelineSummary.getData().getStageNames().get(0)).isEqualTo(STAGE);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineSummaryInvalidPipelineId() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, false, true);

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineSummary(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, null, false))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage(String.format(
            "Pipeline with the given ID: %s does not exist or has been deleted", incorrectPipelineIdentifier));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetListOfPipelines() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.createdAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityWithVersion), pageable, 1);
    doReturn(pipelineEntities).when(pmsPipelineService).list(any(), any(), any(), any(), any(), any());
    doReturn(Collections.emptyMap())
        .when(pipelineMetadataService)
        .getMetadataForGivenPipelineIds(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, Collections.singletonList(PIPELINE_IDENTIFIER));
    List<PMSPipelineSummaryResponseDTO> content = pipelineResource
                                                      .getListOfPipelines(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                                          0, 25, null, null, null, null, null, null, null)
                                                      .getData()
                                                      .getContent();
    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);

    PMSPipelineSummaryResponseDTO responseDTO = content.get(0);
    assertThat(responseDTO.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getName()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getNumOfStages()).isEqualTo(1L);
    assertThat(responseDTO.getStageNames().get(0)).isEqualTo(STAGE);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetExpandedPipelineJson() {
    doReturn("look, a JSON")
        .when(pmsPipelineService)
        .fetchExpandedPipelineJSON(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER);
    ResponseDTO<ExpandedPipelineJsonDTO> expandedPipelineJson = pipelineResource.getExpandedPipelineJson(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(expandedPipelineJson.getData().getExpandedJson()).isEqualTo("look, a JSON");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStepsV2() {
    StepCategory dummy = StepCategory.builder().name("dummy").build();
    StepPalleteFilterWrapper dummyRequest =
        StepPalleteFilterWrapper.builder().stepPalleteModuleInfos(Collections.emptyList()).build();
    doReturn(dummy).when(pmsPipelineService).getStepsV2(ACCOUNT_ID, dummyRequest);
    ResponseDTO<StepCategory> response = pipelineResource.getStepsV2(ACCOUNT_ID, dummyRequest);
    assertThat(response.getData()).isEqualTo(dummy);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testImportPipelineFromGit() {
    GitImportInfoDTO gitImportInfoDTO = GitImportInfoDTO.builder().branch("br").build();
    PipelineImportRequestDTO pipelineImportRequestDTO = PipelineImportRequestDTO.builder().build();
    doReturn(PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).build())
        .when(pmsPipelineService)
        .importPipelineFromRemote(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            pipelineImportRequestDTO, gitImportInfoDTO.getIsForceImport());
    ResponseDTO<PipelineSaveResponse> importPipelineFromGit = pipelineResource.importPipelineFromGit(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, gitImportInfoDTO, pipelineImportRequestDTO);
    assertThat(importPipelineFromGit.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRefreshFFCache() {
    doThrow(new InvalidRequestException("ff cache couldn't be refreshed"))
        .when(featureFlagHelper)
        .refreshCacheForGivenAccountId(ACCOUNT_ID);
    ResponseDTO<Boolean> failResponse = pipelineResource.refreshFFCache(ACCOUNT_ID);
    assertThat(failResponse.getData()).isFalse();
    doReturn(true).when(featureFlagHelper).refreshCacheForGivenAccountId(ACCOUNT_ID);
    ResponseDTO<Boolean> passResponse = pipelineResource.refreshFFCache(ACCOUNT_ID);
    assertThat(passResponse.getData()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidatePipelineByYAML() {
    pipelineResource.validatePipelineByYAML(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    verify(pmsPipelineServiceHelper, times(1))
        .resolveTemplatesAndValidatePipeline(
            PMSPipelineDtoMapper.toPipelineEntity(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml), false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidatePipelineByIdentifier() {
    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);
    assertThatThrownBy(()
                           -> pipelineResource.validatePipelineByIdentifier(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER))
        .isInstanceOf(EntityNotFoundException.class);

    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getAndValidatePipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);
    pipelineResource.validatePipelineByIdentifier(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER);
    verify(pmsPipelineServiceHelper, times(0)).resolveTemplatesAndValidatePipeline(entity, false, false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetTemplateResolvedPipelineYaml() {
    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false);
    assertThatThrownBy(()
                           -> pipelineResource.getTemplateResolvedPipelineYaml(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null))
        .isInstanceOf(EntityNotFoundException.class);

    doReturn(Optional.of(entity))
        .when(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false);

    String extraYaml = yaml + "extra";
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(extraYaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(entity, BOOLEAN_FALSE_VALUE);
    ResponseDTO<TemplatesResolvedPipelineResponseDTO> templateResolvedPipelineYaml =
        pipelineResource.getTemplateResolvedPipelineYaml(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(templateResolvedPipelineYaml.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(templateResolvedPipelineYaml.getData().getResolvedTemplatesPipelineYaml()).isEqualTo(extraYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDummyTemplateMethods() {
    assertThat(pipelineResource.getTemplateStageNode().getData().getClass()).isEqualTo(TemplateStageNode.class);
    assertThat(pipelineResource.getTemplateStepNode().getData().getClass()).isEqualTo(TemplateStepNode.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetExecutionNode() {
    NodeExecution dummyNodeExecution = NodeExecution.builder().name("dummy").build();
    doReturn(dummyNodeExecution).when(nodeExecutionService).get("id");
    ExecutionNode dummyExecutionNode = ExecutionNode.builder().name("dummy").build();
    doReturn(dummyExecutionNode)
        .when(nodeExecutionToExecutioNodeMapper)
        .mapNodeExecutionToExecutionNode(dummyNodeExecution);
    assertThat(pipelineResource.getExecutionNode(null, null, null, null)).isNull();
    ExecutionNode executionNode = pipelineResource.getExecutionNode(null, null, null, "id").getData();
    assertThat(executionNode).isEqualTo(dummyExecutionNode);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateSimplifiedPipeline() {
    doReturn(PipelineVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYaml);
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(simplifiedEntityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(simplifiedEntity, false);
    ResponseDTO<PipelineSaveResponse> response = pipelineResource.createPipelineV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, simplifiedYaml);
    assertThat(response.getData().getIdentifier()).isNotEmpty();
    assertThat(response.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateSimplifiedPipelineWithoutYamlName() {
    doReturn(PipelineVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYamlWithoutName);
    simplifiedEntityWithVersion.setYaml(simplifiedYamlWithoutName);
    simplifiedEntity.setYaml(simplifiedYamlWithoutName);
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(simplifiedEntityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                 .build())
        .when(pmsPipelineService)
        .validateAndCreatePipeline(simplifiedEntity, false);
    ResponseDTO<PipelineSaveResponse> response = pipelineResource.createPipelineV2(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, PIPELINE_NAME, null, null, null, simplifiedYamlWithoutName);
    assertThat(response.getData().getIdentifier()).isNotEmpty();
    assertThat(response.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateSimplifiedPipeline() {
    doReturn(PipelineVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYaml);
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult = PipelineCRUDResult.builder()
                                                .governanceMetadata(governanceMetadata)
                                                .pipelineEntity(simplifiedEntityWithVersion)
                                                .build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(simplifiedEntity, ChangeType.MODIFY, false);
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, simplifiedYaml);
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateSimplifiedPipelineWithoutYamlName() {
    doReturn(PipelineVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYamlWithoutName);
    simplifiedEntityWithVersion.setYaml(simplifiedYamlWithoutName);
    simplifiedEntity.setYaml(simplifiedYamlWithoutName);
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult = PipelineCRUDResult.builder()
                                                .governanceMetadata(governanceMetadata)
                                                .pipelineEntity(simplifiedEntityWithVersion)
                                                .build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(simplifiedEntity, ChangeType.MODIFY, false);
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(null, ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, PIPELINE_NAME, null, null, null, simplifiedYamlWithoutName);
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateSimplifiedPipelineWithoutYamlNameFailure() {
    doReturn(PipelineVersion.V1).when(pmsPipelineService).pipelineVersion(ACCOUNT_ID, simplifiedYamlWithoutName);
    simplifiedEntityWithVersion.setYaml(simplifiedYamlWithoutName);
    simplifiedEntity.setYaml(simplifiedYamlWithoutName);
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult = PipelineCRUDResult.builder()
                                                .governanceMetadata(governanceMetadata)
                                                .pipelineEntity(simplifiedEntityWithVersion)
                                                .build();
    doReturn(pipelineCRUDResult)
        .when(pmsPipelineService)
        .validateAndUpdatePipeline(simplifiedEntity, ChangeType.MODIFY, false);
    assertThatThrownBy(()
                           -> pipelineResource.updatePipelineV2(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, null, null, null, null, simplifiedYamlWithoutName))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetListRepos() {
    List<String> repos = new ArrayList<>();
    repos.add("testRepo");
    repos.add("testRepo2");

    PMSPipelineListRepoResponse repoResponse = PMSPipelineListRepoResponse.builder().repositories(repos).build();
    doReturn(repoResponse).when(pmsPipelineService).getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    PMSPipelineListRepoResponse pmsPipelineListRepoResponse =
        pmsPipelineService.getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertEquals(pmsPipelineListRepoResponse, repoResponse);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfigs() {
    MoveConfigOperationDTO moveConfigOperationDTO =
        MoveConfigOperationDTO.builder().moveConfigOperationType(MoveConfigOperationType.INLINE_TO_REMOTE).build();
    doReturn(PipelineCRUDResult.builder().pipelineEntity(entityWithVersion).build())
        .when(pmsPipelineService)
        .moveConfig(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, moveConfigOperationDTO);

    PipelineMoveConfigRequestDTO pipelineMoveConfigRequestDTO =
        PipelineMoveConfigRequestDTO.builder()
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .isNewBranch(false)
            .moveConfigOperationType(io.harness.gitaware.helper.MoveConfigOperationType.INLINE_TO_REMOTE)
            .build();

    ResponseDTO<MoveConfigResponse> responseDTO = pipelineResource.moveConfig(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, pipelineMoveConfigRequestDTO);

    assertEquals(responseDTO.getData().getPipelineIdentifier(), PIPELINE_IDENTIFIER);
  }
}
