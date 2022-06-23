/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationAdjacencyListDTO;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JsonSchemaValidationException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.interceptor.GitImportInfoDTO;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.helpers.PipelineCloneHelper;
import io.harness.pms.helpers.PmsFeatureFlagHelper;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.mappers.NodeExecutionToExecutioNodeMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.rule.Owner;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
  PipelineResource pipelineResource;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock NodeExecutionToExecutioNodeMapper nodeExecutionToExecutioNodeMapper;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock VariableCreatorMergeService variableCreatorMergeService;
  @Mock AccessControlClient accessControlClient;
  @Mock PipelineCloneHelper pipelineCloneHelper;
  @Mock PmsFeatureFlagHelper featureFlagHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private String yaml;

  PipelineEntity entity;
  PipelineEntity entityWithVersion;
  PipelineExecutionSummaryEntity executionSummaryEntity;
  OrchestrationGraphDTO orchestrationGraph;
  EntityGitDetails entityGitDetails;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    pipelineResource = new PipelineResource(pmsPipelineService, pmsPipelineServiceHelper, nodeExecutionService,
        nodeExecutionToExecutioNodeMapper, pipelineTemplateHelper, featureFlagHelper, variableCreatorMergeService,
        pipelineCloneHelper);
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
                 .allowStageExecutions(false)
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
                            .stageName("qaStage")
                            .version(1L)
                            .allowStageExecutions(false)
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
        .create(entity);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(entity);
    ResponseDTO<String> identifier =
        pipelineResource.createPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, yaml);
    assertThat(identifier.getData()).isNotEmpty();
    assertThat(identifier.getData()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreatePipelineV2() {
    doReturn(true).when(featureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(PipelineCRUDResult.builder()
                 .pipelineEntity(entityWithVersion)
                 .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(true).build())
                 .build())
        .when(pmsPipelineService)
        .create(entity);
    ResponseDTO<PipelineSaveResponse> responseDTO =
        pipelineResource.createPipelineV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipeline() {
    doReturn(Optional.of(entityWithVersion))
        .when(pmsPipelineService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(any());
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
    assertThat(responseDTO.getData().getYamlSchemaErrorWrapper()).isNull();
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNull();
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
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);

    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true);
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
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);

    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, true);
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

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, false);

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineByIdentifier(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, null, true))
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
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    assertThatThrownBy(()
                           -> pipelineResource.updatePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, null, null, null, yaml))
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
    doReturn(pipelineCRUDResult).when(pmsPipelineService).updatePipelineYaml(entity, ChangeType.MODIFY);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    ResponseDTO<String> responseDTO = pipelineResource.updatePipeline(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, yaml);
    assertThat(responseDTO.getData()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpdatePipelineV2() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityWithVersion).build();
    doReturn(pipelineCRUDResult).when(pmsPipelineService).updatePipelineYaml(entity, ChangeType.MODIFY);
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  @Ignore("Ignored till Schema validation is behind FF")
  public void testUpdatePipelineWithSchemaErrors() {
    doThrow(JsonSchemaValidationException.class).when(pmsPipelineService).updatePipelineYaml(entity, ChangeType.MODIFY);
    assertThatThrownBy(()
                           -> pipelineResource.updatePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, null, null, null, yaml))
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
        .getWithoutPerformingValidations(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);
    ResponseDTO<PMSPipelineSummaryResponseDTO> pipelineSummary =
        pipelineResource.getPipelineSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(pipelineSummary.getData().getName()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(pipelineSummary.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(pipelineSummary.getData().getDescription()).isNull();
    assertThat(pipelineSummary.getData().getTags()).isEmpty();
    assertThat(pipelineSummary.getData().getVersion()).isEqualTo(1L);
    assertThat(pipelineSummary.getData().getNumOfStages()).isEqualTo(1L);
    assertThat(pipelineSummary.getData().getStageNames().get(0)).isEqualTo("qaStage");
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineSummaryInvalidPipelineId() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getWithoutPerformingValidations(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, false);

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineSummary(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, null))
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
    assertThat(responseDTO.getStageNames().get(0)).isEqualTo("qaStage");
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
  public void testImportPipelineFromGit() {
    GitImportInfoDTO gitImportInfoDTO = GitImportInfoDTO.builder().branch("br").build();
    PipelineImportRequestDTO pipelineImportRequestDTO = PipelineImportRequestDTO.builder().build();
    doReturn(PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).build())
        .when(pmsPipelineService)
        .importPipelineFromRemote(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, pipelineImportRequestDTO);
    ResponseDTO<PipelineSaveResponse> importPipelineFromGit = pipelineResource.importPipelineFromGit(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, gitImportInfoDTO, pipelineImportRequestDTO);
    assertThat(importPipelineFromGit.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
  }
}
