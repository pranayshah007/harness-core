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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.yaml.validator.InvalidYamlException;

import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceImplSimplifiedGitExpTest extends CategoryTest {
  PMSPipelineServiceImpl pipelineService;
  @Mock private PMSPipelineServiceHelper pipelineServiceHelper;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private PMSPipelineRepository pipelineRepository;

  String accountIdentifier = "acc";
  String orgIdentifier = "org";
  String projectIdentifier = "proj";
  String pipelineId = "pipeline";
  String pipelineYaml = "pipeline: yaml";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pipelineService = new PMSPipelineServiceImpl(
        pipelineRepository, null, pipelineServiceHelper, null, gitSyncSdkService, null, null);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineServiceHelper)
        .validatePipelineYaml(any());
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
                                        .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToSave.withStageCount(0);
    PipelineEntity pipelineEntitySaved = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineToSaveWithUpdatedInfo).when(pipelineServiceHelper).updatePipelineInfo(pipelineToSave);
    doReturn(pipelineEntitySaved).when(pipelineRepository).save(pipelineToSaveWithUpdatedInfo);

    PipelineEntity pipelineEntity = pipelineService.create(pipelineToSave).getPipelineEntity();
    assertThat(pipelineEntity).isEqualTo(pipelineEntitySaved);
    verify(pipelineServiceHelper, times(1))
        .sendPipelineSaveTelemetryEvent(pipelineEntitySaved, "creating new pipeline");
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
        .validatePipelineYaml(pipelineToSave);
    PipelineCRUDResult pipelineCRUDResult = pipelineService.create(pipelineToSave);
    assertThat(pipelineCRUDResult.getPipelineEntity()).isNull();
    assertThat(pipelineCRUDResult.getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineServiceHelper, times(0)).updatePipelineInfo(any());
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
    doThrow(new InvalidYamlException("msg", null)).when(pipelineServiceHelper).validatePipelineYaml(pipelineToSave);
    assertThatThrownBy(() -> pipelineService.create(pipelineToSave))
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
                                        .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToSave.withStageCount(0);
    doReturn(pipelineToSaveWithUpdatedInfo).when(pipelineServiceHelper).updatePipelineInfo(pipelineToSave);
    doThrow(new HintException("this is a hint")).when(pipelineRepository).save(pipelineToSaveWithUpdatedInfo);

    assertThatThrownBy(() -> pipelineService.create(pipelineToSave))
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
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false);
    Optional<PipelineEntity> optionalPipelineEntity =
        pipelineService.get(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(pipelineEntity);
    verify(pipelineServiceHelper, times(0)).validatePipelineFromRemote(any());
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
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false);
    Optional<PipelineEntity> optionalPipelineEntity =
        pipelineService.get(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false);
    assertThat(optionalPipelineEntity.isPresent()).isTrue();
    assertThat(optionalPipelineEntity.get()).isEqualTo(pipelineEntity);
    verify(pipelineServiceHelper, times(1)).validatePipelineFromRemote(any());
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
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false);
    assertThatThrownBy(
        () -> pipelineService.get(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false))
        .isInstanceOf(InvalidYamlException.class);
    verify(pipelineServiceHelper, times(0)).validatePipelineFromRemote(any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetNonExistentPipeline() {
    doReturn(Optional.empty())
        .when(pipelineRepository)
        .find(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, true, false);
    assertThatThrownBy(
        () -> pipelineService.get(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, false))
        .isInstanceOf(EntityNotFoundException.class);
    verify(pipelineServiceHelper, times(0)).validatePipelineFromRemote(any());
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
                                          .build();
    PipelineEntity pipelineToSaveWithUpdatedInfo = pipelineToUpdate.withStageCount(0);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pipelineServiceHelper)
        .validatePipelineYaml(pipelineToUpdate);
    doReturn(pipelineToSaveWithUpdatedInfo).when(pipelineServiceHelper).updatePipelineInfo(pipelineToUpdate);

    PipelineEntity pipelineEntityUpdated = pipelineToSaveWithUpdatedInfo.withVersion(0L);
    doReturn(pipelineEntityUpdated).when(pipelineRepository).updatePipelineYaml(pipelineToSaveWithUpdatedInfo);

    PipelineEntity pipelineEntity = pipelineService.updatePipelineYaml(pipelineToUpdate, null).getPipelineEntity();
    assertThat(pipelineEntity).isEqualTo(pipelineEntityUpdated);
    verify(pipelineServiceHelper, times(1))
        .sendPipelineSaveTelemetryEvent(pipelineEntityUpdated, "updating existing pipeline");
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
        .validatePipelineYaml(pipelineToUpdate);
    PipelineCRUDResult pipelineCRUDResult = pipelineService.updatePipelineYaml(pipelineToUpdate, null);
    assertThat(pipelineCRUDResult.getPipelineEntity()).isNull();
    assertThat(pipelineCRUDResult.getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineServiceHelper, times(0)).updatePipelineInfo(any());
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
    doThrow(new InvalidYamlException("msg", null)).when(pipelineServiceHelper).validatePipelineYaml(pipelineToUpdate);
    assertThatThrownBy(() -> pipelineService.updatePipelineYaml(pipelineToUpdate, null))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("msg");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeletePipeline() {
    boolean delete = pipelineService.delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, null);
    assertThat(delete).isTrue();

    doThrow(new InvalidRequestException("anything actually"))
        .when(pipelineRepository)
        .delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId);

    assertThatThrownBy(
        () -> pipelineService.delete(accountIdentifier, orgIdentifier, projectIdentifier, pipelineId, null))
        .isInstanceOf(InvalidRequestException.class);
  }
}
