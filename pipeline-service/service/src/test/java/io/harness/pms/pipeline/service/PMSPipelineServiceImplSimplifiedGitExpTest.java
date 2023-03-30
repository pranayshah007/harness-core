/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.NoopPipelineSettingServiceImpl;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.governance.GovernanceMetadata;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.helper.PipelineAsyncValidationHelper;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;
import io.harness.pms.pipeline.validation.service.PipelineValidationService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.InvalidYamlException;

import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceImplSimplifiedGitExpTest extends CategoryTest {
  PMSPipelineServiceImpl pipelineService;
  @Mock private PMSPipelineServiceHelper pipelineServiceHelper;
  @Mock private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private PMSPipelineRepository pipelineRepository;
  @Mock private EntitySetupUsageClient entitySetupUsageClient;
  @Mock private PipelineAsyncValidationService pipelineAsyncValidationService;
  @Mock private PipelineValidationService pipelineValidationService;
  @Mock private ProjectClient projectClient;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String pipelineId = "pipeline";
  String pipelineYaml = "pipeline: yaml";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    pipelineService =
        new PMSPipelineServiceImpl(pipelineRepository, null, pipelineServiceHelper, pmsPipelineTemplateHelper, null,
            null, gitSyncSdkService, null, null, null, new NoopPipelineSettingServiceImpl(), entitySetupUsageClient,
            pipelineAsyncValidationService, pipelineValidationService, projectClient, pmsFeatureFlagService);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineServiceHelper)
        .validatePipeline(any(), any(), anyBoolean());
    doReturn(TemplateMergeResponseDTO.builder().build())
        .when(pmsPipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), anyBoolean(), anyBoolean());
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean());
    doReturn(false).when(pmsFeatureFlagService).isEnabled(anyString(), (FeatureName) any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipeline() throws IOException {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(PipelineVersion.V0)
                                        .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToSave.withStageCount(0);
    PipelineEntity pipelineEntitySaved = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(pipelineToSave, PipelineVersion.V0);
    doReturn(pipelineEntitySaved).when(pipelineRepository).save(pipelineToSaveWithUpdatedInfo);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    PipelineEntity pipelineEntity =
        pipelineService.validateAndCreatePipeline(pipelineToSave, false).getPipelineEntity();
    assertThat(pipelineEntity).isEqualTo(pipelineEntitySaved);
    verify(pipelineServiceHelper, times(1))
        .sendPipelineSaveTelemetryEvent(pipelineEntitySaved, "creating new pipeline");
    verify(pipelineAsyncValidationService, times(1))
        .createRecordForSuccessfulSyncValidation(
            pipelineEntitySaved, null, GovernanceMetadata.newBuilder().build(), Action.CRUD);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineWith__DEFAULT__InGitContext() throws IOException {
    setupGitContext(GitEntityInfo.builder().branch(GitAwareContextHelper.DEFAULT).build());
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(PipelineVersion.V0)
                                        .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToSave.withStageCount(0);
    PipelineEntity pipelineEntitySaved = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(pipelineToSave, PipelineVersion.V0);
    doReturn(pipelineEntitySaved).when(pipelineRepository).save(pipelineToSaveWithUpdatedInfo);

    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    PipelineEntity pipelineEntity =
        pipelineService.validateAndCreatePipeline(pipelineToSave, false).getPipelineEntity();
    assertThat(pipelineEntity).isEqualTo(pipelineEntitySaved);
    verify(pipelineServiceHelper, times(1))
        .sendPipelineSaveTelemetryEvent(pipelineEntitySaved, "creating new pipeline");
    verify(pipelineAsyncValidationService, times(1))
        .createRecordForSuccessfulSyncValidation(
            pipelineEntitySaved, "", GovernanceMetadata.newBuilder().build(), Action.CRUD);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineWithGovernanceDeny() throws IOException {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(eq(pipelineToSave), anyBoolean(), anyBoolean());
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    PipelineCRUDResult pipelineCRUDResult = pipelineService.validateAndCreatePipeline(pipelineToSave, true);
    assertThat(pipelineCRUDResult.getPipelineEntity()).isNull();
    assertThat(pipelineCRUDResult.getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineServiceHelper, times(0)).updatePipelineInfo(any(), eq(PipelineVersion.V0));
    verify(pipelineRepository, times(0)).saveForOldGitSync(any());
    verify(pipelineRepository, times(0)).save(any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineWithSchemaErrors() {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .build();
    doThrow(new InvalidYamlException("msg", null, pipelineYaml))
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(eq(pipelineToSave), anyBoolean(), anyBoolean());
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    assertThatThrownBy(() -> pipelineService.validateAndCreatePipeline(pipelineToSave, true))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("msg");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipelineWithHintException() throws IOException {
    PipelineEntity pipelineToSave = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .harnessVersion(PipelineVersion.V0)
                                        .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToSave.withStageCount(0);
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(pipelineToSave, PipelineVersion.V0);
    doThrow(new HintException("this is a hint")).when(pipelineRepository).save(pipelineToSaveWithUpdatedInfo);

    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    assertThatThrownBy(() -> pipelineService.validateAndCreatePipeline(pipelineToSave, true))
        .isInstanceOf(HintException.class)
        .hasMessage("this is a hint");
    verify(pipelineServiceHelper, times(0)).sendPipelineSaveTelemetryEvent(any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInlinePipeline() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.INLINE)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false);
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    Optional<PipelineEntity> optionalPipelineEntity =
        pipelineService.getAndValidatePipeline(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(pipelineEntity);
    verify(pipelineServiceHelper, times(0)).resolveTemplatesAndValidatePipelineEntity(any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetRemotePipeline() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false);
    Optional<PipelineEntity> optionalPipelineEntity =
        pipelineService.getAndValidatePipeline(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(pipelineEntity);
    verify(pipelineServiceHelper, times(1)).resolveTemplatesAndValidatePipelineEntity(any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetRemotePipelineWithNoData() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false);
    assertThatThrownBy(()
                           -> pipelineService.getAndValidatePipeline(
                               accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false))
        .isInstanceOf(InvalidYamlException.class);
    verify(pipelineServiceHelper, times(0)).resolveTemplatesAndValidatePipelineEntity(any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineAndAsyncValidationId() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .harnessVersion("V0")
                                        .yaml(pipelineYaml)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, true);

    String fqn = PipelineAsyncValidationHelper.buildFQN(pipelineEntity, "");
    doReturn(Optional.of(PipelineValidationEvent.builder().uuid("validationUuid").build()))
        .when(pipelineAsyncValidationService)
        .getLatestEventByFQNAndAction(fqn, Action.CRUD);
    PipelineGetResult pipelineGetResult = pipelineService.getPipelineAndAsyncValidationId(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false, true);
    assertThat(pipelineGetResult.getPipelineEntity().isPresent()).isTrue();
    assertThat(pipelineGetResult.getPipelineEntity().get()).isEqualTo(pipelineEntity);
    assertThat(pipelineGetResult.getAsyncValidationUUID()).isEqualTo("validationUuid");
    verify(pipelineValidationService, times(1))
        .validateYamlWithUnresolvedTemplates(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, "0");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineAndAsyncValidationIdWithSchemaError() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .harnessVersion("V0")
                                        .yaml(pipelineYaml)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, true);
    doThrow(new InvalidYamlException("msg", null, pipelineYaml))
        .when(pipelineValidationService)
        .validateYamlWithUnresolvedTemplates(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, "0");
    assertThatThrownBy(()
                           -> pipelineService.getPipelineAndAsyncValidationId(
                               accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false, true))
        .isInstanceOf(InvalidYamlException.class);
    verify(pipelineValidationService, times(1))
        .validateYamlWithUnresolvedTemplates(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, "0");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineAndAsyncValidationIdWhenLoadingFromGit() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(pipelineId)
                                        .harnessVersion("V0")
                                        .storeType(StoreType.REMOTE)
                                        .yaml(pipelineYaml)
                                        .build();
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false);

    doReturn(PipelineValidationEvent.builder().uuid("validationUuid").build())
        .when(pipelineAsyncValidationService)
        .startEvent(pipelineEntity, null, Action.CRUD, false);
    PipelineGetResult pipelineGetResult = pipelineService.getPipelineAndAsyncValidationId(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false, false);
    assertThat(pipelineGetResult.getPipelineEntity().isPresent()).isTrue();
    assertThat(pipelineGetResult.getPipelineEntity().get()).isEqualTo(pipelineEntity);
    assertThat(pipelineGetResult.getAsyncValidationUUID()).isEqualTo("validationUuid");
    verify(pipelineValidationService, times(1))
        .validateYamlWithUnresolvedTemplates(accountIdentifier, orgIdentifier, projectIdentifier, pipelineYaml, "0");
    verify(pipelineAsyncValidationService, times(0)).getLatestEventByFQNAndAction(any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetNonExistentPipeline() {
    doReturn(Optional.empty())
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false, false, false);
    assertThatThrownBy(()
                           -> pipelineService.getAndValidatePipeline(
                               accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false))
        .isInstanceOf(EntityNotFoundException.class);
    verify(pipelineServiceHelper, times(0)).resolveTemplatesAndValidatePipelineEntity(any(), anyBoolean());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipeline() throws IOException {
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .yaml(pipelineYaml)
                                          .harnessVersion(PipelineVersion.V0)
                                          .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToUpdate.withStageCount(0);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineServiceHelper)
        .validatePipeline(eq(pipelineToUpdate), any(), anyBoolean());
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(pipelineToUpdate, PipelineVersion.V0);

    PipelineEntity pipelineEntityUpdated = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineEntityUpdated).when(pipelineRepository).updatePipelineYaml(pipelineToSaveWithUpdatedInfo);

    PipelineEntity pipelineEntity =
        pipelineService.validateAndUpdatePipeline(pipelineToUpdate, null, true).getPipelineEntity();
    assertThat(pipelineEntity).isEqualTo(pipelineEntityUpdated);
    verify(pipelineServiceHelper, times(1))
        .sendPipelineSaveTelemetryEvent(pipelineEntityUpdated, "updating existing pipeline");
    verify(pipelineAsyncValidationService, times(1))
        .createRecordForSuccessfulSyncValidation(
            pipelineEntityUpdated, null, GovernanceMetadata.newBuilder().build(), Action.CRUD);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWith__DEFAULT__InGitContext() throws IOException {
    setupGitContext(GitEntityInfo.builder().branch(GitAwareContextHelper.DEFAULT).build());
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .yaml(pipelineYaml)
                                          .harnessVersion(PipelineVersion.V0)
                                          .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToUpdate.withStageCount(0);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineServiceHelper)
        .validatePipeline(eq(pipelineToUpdate), any(), anyBoolean());
    doReturn(pipelineToSaveWithUpdatedInfo)
        .when(pipelineServiceHelper)
        .updatePipelineInfo(pipelineToUpdate, PipelineVersion.V0);

    PipelineEntity pipelineEntityUpdated = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineEntityUpdated).when(pipelineRepository).updatePipelineYaml(pipelineToSaveWithUpdatedInfo);

    PipelineEntity pipelineEntity =
        pipelineService.validateAndUpdatePipeline(pipelineToUpdate, null, true).getPipelineEntity();
    assertThat(pipelineEntity).isEqualTo(pipelineEntityUpdated);
    verify(pipelineServiceHelper, times(1))
        .sendPipelineSaveTelemetryEvent(pipelineEntityUpdated, "updating existing pipeline");
    verify(pipelineAsyncValidationService, times(1))
        .createRecordForSuccessfulSyncValidation(
            pipelineEntityUpdated, "", GovernanceMetadata.newBuilder().build(), Action.CRUD);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithGovernanceDeny() throws IOException {
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .yaml(pipelineYaml)
                                          .build();
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(eq(pipelineToUpdate), anyBoolean(), anyBoolean());
    PipelineCRUDResult pipelineCRUDResult = pipelineService.validateAndUpdatePipeline(pipelineToUpdate, null, true);
    assertThat(pipelineCRUDResult.getPipelineEntity()).isNull();
    assertThat(pipelineCRUDResult.getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineServiceHelper, times(0)).updatePipelineInfo(any(), eq(PipelineVersion.V0));
    verify(pipelineRepository, times(0)).updatePipelineYaml(any());
    verify(pipelineRepository, times(0)).updatePipelineYamlForOldGitSync(any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithSchemaErrors() {
    PipelineEntity pipelineToUpdate = PipelineEntity.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .identifier(pipelineId)
                                          .yaml(pipelineYaml)
                                          .build();
    doThrow(new InvalidYamlException("msg", null, pipelineYaml))
        .when(pipelineServiceHelper)
        .resolveTemplatesAndValidatePipeline(eq(pipelineToUpdate), anyBoolean(), anyBoolean());
    assertThatThrownBy(() -> pipelineService.validateAndUpdatePipeline(pipelineToUpdate, null, true))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("msg");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeletePipeline() {
    doReturn(getResponseDTOCall(false)).when(entitySetupUsageClient).isEntityReferenced(any(), any(), any());
    boolean delete = pipelineService.delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, null);
    assertThat(delete).isTrue();

    doThrow(new InvalidRequestException("anything actually"))
        .when(pipelineRepository)
        .delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId);

    assertThatThrownBy(
        () -> pipelineService.delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  private Call<ResponseDTO<Boolean>> getResponseDTOCall(boolean setValue) {
    Call<ResponseDTO<Boolean>> request = mock(Call.class);
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(setValue)));
    } catch (IOException ex) {
    }
    return request;
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}
