/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.pipeline.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.NAMAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.pms.pipeline.service.PipelineGetResult;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.GitMoveDetails;
import io.harness.spec.server.pipeline.v1.model.MoveConfigOperationType;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineListResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineMoveConfigRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineMoveConfigResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineUpdateRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationUUIDResponseBody;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@OwnedBy(PIPELINE)
public class PipelinesApiImplTest extends CategoryTest {
  PipelinesApiImpl pipelinesApiImpl;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineServiceHelper pipelineServiceHelper;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PipelineAsyncValidationService pipelineAsyncValidationService;

  String identifier = "basichttpFail";
  String name = "basichttpFail";
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String branch = randomAlphabetic(10);
  String repo = randomAlphabetic(10);
  String connectorRef = randomAlphabetic(10);
  int page = 0;
  int limit = 1;
  PipelineEntity entity;
  PipelineEntity entityModified;
  private String yaml;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.openMocks(this);
    pipelinesApiImpl = new PipelinesApiImpl(pmsPipelineService, pipelineServiceHelper, pipelineTemplateHelper,
        pipelineMetadataService, pipelineAsyncValidationService);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "simplified-yaml.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    entity = PipelineEntity.builder()
                 .accountId(account)
                 .orgIdentifier(org)
                 .projectIdentifier(project)
                 .identifier(identifier)
                 .name(name)
                 .yaml(yaml)
                 .isDraft(false)
                 .allowStageExecutions(false)
                 .build();

    entityModified = PipelineEntity.builder()
                         .accountId(account)
                         .orgIdentifier(org)
                         .projectIdentifier(project)
                         .identifier(identifier)
                         .name(name)
                         .yaml(yaml)
                         .stageCount(1)
                         .stageName("qaStage")
                         .allowStageExecutions(false)
                         .build();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineCreate() {
    PipelineCreateRequestBody pipelineRequestBody = new PipelineCreateRequestBody();
    pipelineRequestBody.setPipelineYaml(yaml);
    pipelineRequestBody.setIdentifier(identifier);
    pipelineRequestBody.setName(name);
    when(pmsPipelineService.validateAndCreatePipeline(any(), eq(false)))
        .thenReturn(PipelineCRUDResult.builder()
                        .pipelineEntity(entity)
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());
    Response response = pipelinesApiImpl.createPipeline(pipelineRequestBody, org, project, account);
    PipelineCreateResponseBody responseBody = (PipelineCreateResponseBody) response.getEntity();
    assertEquals(identifier, responseBody.getIdentifier());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineDelete() {
    doReturn(true).when(pmsPipelineService).delete(account, org, project, identifier, null);
    Response deleteResponse = pipelinesApiImpl.deletePipeline(org, project, identifier, account);
    assertThat(deleteResponse.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineDeleteFail() {
    doReturn(false).when(pmsPipelineService).delete(account, org, project, identifier, null);
    try {
      pipelinesApiImpl.deletePipeline(org, project, identifier, account);
    } catch (InvalidRequestException e) {
      assertEquals(e.getMessage(), String.format("Pipeline with identifier %s cannot be deleted.", identifier));
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineUpdate() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityModified).build();
    doReturn(pipelineCRUDResult).when(pmsPipelineService).validateAndUpdatePipeline(entity, ChangeType.MODIFY, false);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(account, org, project, yaml, BOOLEAN_FALSE_VALUE);
    PipelineUpdateRequestBody requestBody = new PipelineUpdateRequestBody();
    requestBody.setPipelineYaml(yaml);
    requestBody.setIdentifier(identifier);
    requestBody.setName(name);
    Response response = pipelinesApiImpl.updatePipeline(requestBody, org, project, identifier, account);
    PipelineCreateResponseBody responseBody = (PipelineCreateResponseBody) response.getEntity();
    assertEquals(identifier, responseBody.getIdentifier());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineUpdateFail() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(true).build();
    PipelineCRUDResult pipelineCRUDResult =
        PipelineCRUDResult.builder().governanceMetadata(governanceMetadata).pipelineEntity(entityModified).build();
    doReturn(pipelineCRUDResult).when(pmsPipelineService).validateAndUpdatePipeline(entity, ChangeType.MODIFY, false);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(account, org, project, yaml, BOOLEAN_FALSE_VALUE);
    PipelineUpdateRequestBody requestBody = new PipelineUpdateRequestBody();
    requestBody.setPipelineYaml(yaml);
    requestBody.setIdentifier(identifier);
    requestBody.setName(name);
    try {
      pipelinesApiImpl.updatePipeline(requestBody, org, project, identifier, account);
    } catch (PolicyEvaluationFailureException e) {
      assertEquals(e.getMessage(), "Policy Evaluation Failure");
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineGetNoTemplates() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    doReturn(PipelineGetResult.builder().pipelineEntity(optional).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(account, org, project, identifier, false, false, false, false, false);
    Response response = pipelinesApiImpl.getPipeline(
        org, project, identifier, account, null, false, null, null, BOOLEAN_FALSE_VALUE, false, false);
    PipelineGetResponseBody responseBody = (PipelineGetResponseBody) response.getEntity();
    assertEquals(yaml, responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(org, responseBody.getOrg());
    assertEquals(project, responseBody.getProject());
    assertEquals(true, responseBody.isValid().booleanValue());
  }
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testNPEInPipelineGet() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    doReturn(PipelineGetResult.builder().pipelineEntity(optional).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(account, org, project, identifier, false, false, false, false, false);
    Response response = pipelinesApiImpl.getPipeline(
        org, project, identifier, account, null, false, null, null, BOOLEAN_FALSE_VALUE, null, false);
    PipelineGetResponseBody responseBody = (PipelineGetResponseBody) response.getEntity();
    assertEquals(yaml, responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(org, responseBody.getOrg());
    assertEquals(project, responseBody.getProject());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineGetWithTemplates() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    doReturn(PipelineGetResult.builder().pipelineEntity(optional).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(account, org, project, identifier, false, false, false, false, false);
    String extraYaml = yaml + "extra";
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(extraYaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(entity, BOOLEAN_FALSE_VALUE);
    Response response = pipelinesApiImpl.getPipeline(
        org, project, identifier, account, null, true, null, null, BOOLEAN_FALSE_VALUE, false, false);
    PipelineGetResponseBody responseBody = (PipelineGetResponseBody) response.getEntity();
    assertEquals(extraYaml, responseBody.getTemplateAppliedPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(org, responseBody.getOrg());
    assertEquals(project, responseBody.getProject());
    assertEquals(true, responseBody.isValid().booleanValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineGetFailPolicyEvaluation() {
    doThrow(PolicyEvaluationFailureException.class)
        .when(pmsPipelineService)
        .getAndValidatePipeline(account, org, project, identifier, false, false, false, false, false);
    PipelineGetResponseBody response =
        (PipelineGetResponseBody) pipelinesApiImpl
            .getPipeline(org, project, identifier, account, null, false, null, null, BOOLEAN_FALSE_VALUE, false, false)
            .getEntity();
    assertEquals(false, response.isValid().booleanValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineGetFailInvalidYaml() {
    doThrow(InvalidYamlException.class)
        .when(pmsPipelineService)
        .getAndValidatePipeline(account, org, project, identifier, false, false, false, false, false);
    PipelineGetResponseBody response =
        (PipelineGetResponseBody) pipelinesApiImpl
            .getPipeline(org, project, identifier, account, null, false, null, null, BOOLEAN_FALSE_VALUE, false, false)
            .getEntity();
    assertEquals(false, response.isValid().booleanValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineList() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, PipelineEntityKeys.lastUpdatedAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityModified), pageable, 1);
    doReturn(pipelineEntities).when(pmsPipelineService).list(any(), any(), any(), any(), any(), any());
    doReturn(Collections.emptyMap())
        .when(pipelineMetadataService)
        .getMetadataForGivenPipelineIds(account, org, project, Collections.singletonList(identifier));
    List<PipelineListResponseBody> content = (List<PipelineListResponseBody>) pipelinesApiImpl
                                                 .listPipelines(org, project, account, 0, 25, null, null, null, null,
                                                     null, null, null, null, null, null, null, null, null)
                                                 .getEntity();
    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);

    PipelineListResponseBody responseBody = content.get(0);
    assertThat(responseBody.getIdentifier()).isEqualTo(identifier);
    assertThat(responseBody.getName()).isEqualTo(name);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartPipelineValidationEvent() {
    doReturn(Optional.of(entity)).when(pmsPipelineService).getPipeline(account, org, project, "pipeline", false, false);
    doReturn(PipelineValidationEvent.builder().uuid("abc1").build())
        .when(pipelineAsyncValidationService)
        .startEvent(entity, null, Action.CRUD, false);
    Response response =
        pipelinesApiImpl.startPipelineValidationEvent(org, project, "pipeline", account, null, null, null, false, null);
    PipelineValidationUUIDResponseBody responseBody = (PipelineValidationUUIDResponseBody) response.getEntity();
    assertThat(responseBody.getUuid()).isEqualTo("abc1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineValidateResult() {
    doReturn(Optional.of(PipelineValidationEvent.builder()
                             .status(ValidationStatus.IN_PROGRESS)
                             .result(ValidationResult.builder().build())
                             .build()))
        .when(pipelineAsyncValidationService)
        .getEventByUuid("uuid1");

    Response response = pipelinesApiImpl.getPipelineValidateResult(null, null, "uuid1", null);
    PipelineValidationResponseBody responseBody = (PipelineValidationResponseBody) response.getEntity();
    assertThat(responseBody.getStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPipelineGetNoTemplatesWithCaching() {
    Optional<PipelineEntity> optional = Optional.ofNullable(entity);
    doReturn(PipelineGetResult.builder().pipelineEntity(optional).build())
        .when(pmsPipelineService)
        .getAndValidatePipeline(account, org, project, identifier, false, false, false, true, false);
    Response response =
        pipelinesApiImpl.getPipeline(org, project, identifier, account, null, false, null, null, "true", false, false);
    PipelineGetResponseBody responseBody = (PipelineGetResponseBody) response.getEntity();
    assertEquals(yaml, responseBody.getPipelineYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(org, responseBody.getOrg());
    assertEquals(project, responseBody.getProject());
    assertTrue(responseBody.isValid());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfig() {
    GitMoveDetails gitMoveDetails = new GitMoveDetails();
    gitMoveDetails.setBranchName(branch);
    gitMoveDetails.setRepoName(repo);
    gitMoveDetails.setConnectorRef(connectorRef);
    PipelineMoveConfigRequestBody pipelineMoveConfigRequestBody = new PipelineMoveConfigRequestBody();
    pipelineMoveConfigRequestBody.setGitDetails(gitMoveDetails);
    pipelineMoveConfigRequestBody.setMoveConfigOperationType(MoveConfigOperationType.REMOTE_TO_INLINE);
    pipelineMoveConfigRequestBody.setPipelineIdentifier(identifier);
    doReturn(PipelineCRUDResult.builder().pipelineEntity(entity).build())
        .when(pmsPipelineService)
        .moveConfig(any(), any(), any(), any(), any());
    Response response = pipelinesApiImpl.moveConfig(org, project, identifier, pipelineMoveConfigRequestBody, account);
    PipelineMoveConfigResponseBody responseBody = (PipelineMoveConfigResponseBody) response.getEntity();
    assertEquals(identifier, responseBody.getPipelineIdentifier());
  }
}