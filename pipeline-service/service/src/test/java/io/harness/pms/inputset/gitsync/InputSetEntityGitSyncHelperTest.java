/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.gitsync;

import static io.harness.EntityType.INPUT_SETS;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.InputSetReference;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.FullSyncChangeSet;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.service.InputSetFullGitSyncHandler;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetEntityGitSyncHelperTest extends CategoryTest {
  @Mock private PMSInputSetService pmsInputSetService;
  @Mock private InputSetFullGitSyncHandler inputSetFullGitSyncHandler;
  @InjectMocks InputSetEntityGitSyncHelper inputSetEntityGitSyncHelper;
  static String accountId = "accountId";
  static String orgId = "orgId";
  static String projectId = "projectId";
  static String pipelineId = "pipelineId";
  static String name = "name";
  static String identifier = "identifier";
  static String filePath = "filePath";
  static String inputSetYaml = "inputSet:\n"
      + "  identifier: input1\n"
      + "  name: this name\n"
      + "  description: this has a description too\n"
      + "  tags:\n"
      + "    company: harness\n"
      + "    kind : normal\n"
      + "  pipeline:\n"
      + "    identifier: \"Test_Pipline11\"\n";
  static String erroneousYaml = "erroneousYaml";
  static String overLayYaml;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetEntityDetail() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .identifier(identifier)
                                        .name(name)
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .build();

    EntityDetail entityDetail = inputSetEntityGitSyncHelper.getEntityDetail(inputSetEntity);
    assertEquals(entityDetail.getName(), name);
    assertEquals(entityDetail.getType(), INPUT_SETS);
    assertEquals(entityDetail.getEntityRef().getIdentifier(), identifier);
    assertEquals(entityDetail.getEntityRef().getOrgIdentifier(), orgId);
    assertEquals(entityDetail.getEntityRef().getAccountIdentifier(), accountId);
    assertEquals(entityDetail.getEntityRef().getProjectIdentifier(), projectId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetLastObjectIdIfExists() throws IOException {
    overLayYaml = Resources.toString(this.getClass().getClassLoader().getResource("overlay1.yml"), Charsets.UTF_8);
    String objectId = "objectId";
    doReturn(Optional.of(InputSetEntity.builder().objectIdOfYaml(objectId).build()))
        .when(pmsInputSetService)
        .getWithoutValidations(anyString(), any(), any(), any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    EntityGitDetails returnedEntity =
        inputSetEntityGitSyncHelper.getEntityDetailsIfExists(accountId, inputSetYaml).get();
    verify(pmsInputSetService, times(1))
        .getWithoutValidations(anyString(), any(), any(), any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    assertEquals(returnedEntity.getObjectId(), objectId);
    returnedEntity = inputSetEntityGitSyncHelper.getEntityDetailsIfExists(accountId, overLayYaml).get();
    verify(pmsInputSetService, times(2))
        .getWithoutValidations(anyString(), any(), any(), any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    assertEquals(returnedEntity.getObjectId(), objectId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSave() throws IOException {
    setupGitContext();
    overLayYaml = Resources.toString(this.getClass().getClassLoader().getResource("overlay1.yml"), Charsets.UTF_8);
    doReturn(InputSetEntity.builder().yaml(inputSetYaml).build()).when(pmsInputSetService).create(any(), anyBoolean());
    InputSetYamlDTO inputSetYamlDTO = inputSetEntityGitSyncHelper.save(accountId, inputSetYaml);
    verify(pmsInputSetService, times(1)).create(any(), anyBoolean());
    assertEquals(inputSetYamlDTO, YamlUtils.read(inputSetYaml, InputSetYamlDTO.class));
    inputSetEntityGitSyncHelper.save(accountId, overLayYaml);
    verify(pmsInputSetService, times(2)).create(any(), anyBoolean());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException {
    setupGitContext();
    doReturn(InputSetEntity.builder().yaml(inputSetYaml).build())
        .when(pmsInputSetService)
        .update(any(), any(), anyBoolean());
    InputSetYamlDTO inputSetYamlDTO = inputSetEntityGitSyncHelper.update(accountId, inputSetYaml, ChangeType.NONE);
    verify(pmsInputSetService, times(1)).update(any(), any(), anyBoolean());
    assertEquals(inputSetYamlDTO, YamlUtils.read(inputSetYaml, InputSetYamlDTO.class));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDelete() {
    EntityReference entityReference = InputSetReference.builder()
                                          .identifier(identifier)
                                          .accountIdentifier(accountId)
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .pipelineIdentifier(pipelineId)
                                          .build();
    doReturn(true).when(pmsInputSetService).delete(accountId, orgId, projectId, pipelineId, identifier, null);
    assertTrue(inputSetEntityGitSyncHelper.delete(entityReference));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testKeyGetters() {
    assertEquals(inputSetEntityGitSyncHelper.getEntityType(), INPUT_SETS);
    assertEquals(inputSetEntityGitSyncHelper.getObjectIdOfYamlKey(), InputSetEntityKeys.objectIdOfYaml);
    assertEquals(inputSetEntityGitSyncHelper.getIsFromDefaultBranchKey(), InputSetEntityKeys.isFromDefaultBranch);
    assertEquals(inputSetEntityGitSyncHelper.getYamlGitConfigRefKey(), InputSetEntityKeys.yamlGitConfigRef);
    assertEquals(inputSetEntityGitSyncHelper.getUuidKey(), InputSetEntityKeys.uuid);
    assertEquals(inputSetEntityGitSyncHelper.getBranchKey(), InputSetEntityKeys.branch);
  }

  private void setupGitContext() {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().branch("someBranch").yamlGitConfigId("someRepoID").build();
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfo).build());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetYamlFromEntity() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .identifier(identifier)
                                        .name(name)
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .build();
    Supplier<InputSetYamlDTO> inputSetYamlDTOSupplier = () -> InputSetYamlDTOMapper.toDTO(inputSetEntity);
    Supplier<InputSetYamlDTO> result = inputSetEntityGitSyncHelper.getYamlFromEntity(inputSetEntity);
    assertThat(result.get().getInputSetInfo()).isEqualTo(inputSetYamlDTOSupplier.get().getInputSetInfo());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetEntityFromYaml() {
    PipelineInfoConfig pipelineInfoConfig = PipelineInfoConfig.builder().build();
    InputSetYamlInfoDTO inputSetYamlInfoDTO =
        InputSetYamlInfoDTO.builder().name(name).identifier(identifier).pipelineInfoConfig(pipelineInfoConfig).build();
    OverlayInputSetYamlInfoDTO overlayInputSetYamlInfoDTO =
        OverlayInputSetYamlInfoDTO.builder().name(name).identifier(identifier).build();
    InputSetYamlDTO yaml = InputSetYamlDTO.builder()
                               .inputSetInfo(inputSetYamlInfoDTO)
                               .overlayInputSetInfo(overlayInputSetYamlInfoDTO)
                               .build();
    Supplier<InputSetEntity> inputSetEntitySupplier = () -> InputSetYamlDTOMapper.toEntity(yaml, accountId);
    Supplier<InputSetEntity> result = inputSetEntityGitSyncHelper.getEntityFromYaml(yaml, accountId);
    assertThat(result.get().getYaml()).isEqualTo(inputSetEntitySupplier.get().getYaml());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMarkEntityInvalidSuccess() {
    EntityReference entityReference = mock(InputSetReference.class);
    doReturn(orgId).when(entityReference).getOrgIdentifier();
    doReturn(projectId).when(entityReference).getProjectIdentifier();
    doReturn(pipelineId).when((InputSetReference) entityReference).getPipelineIdentifier();
    doReturn(identifier).when(entityReference).getIdentifier();
    doReturn(true)
        .when(pmsInputSetService)
        .markGitSyncedInputSetInvalid(accountId, orgId, projectId, pipelineId, identifier, erroneousYaml);
    assertThat(inputSetEntityGitSyncHelper.markEntityInvalid(accountId, entityReference, erroneousYaml)).isTrue();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testMarkEntityInvalidFailure() {
    EntityReference entityReference = mock(InputSetReference.class);
    doReturn(orgId).when(entityReference).getOrgIdentifier();
    doReturn(projectId).when(entityReference).getProjectIdentifier();
    doReturn(pipelineId).when((InputSetReference) entityReference).getPipelineIdentifier();
    doReturn(identifier).when(entityReference).getIdentifier();
    doReturn(false)
        .when(pmsInputSetService)
        .markGitSyncedInputSetInvalid(accountId, orgId, projectId, pipelineId, identifier, erroneousYaml);
    assertThat(inputSetEntityGitSyncHelper.markEntityInvalid(accountId, entityReference, erroneousYaml)).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testListAllEntities() {
    ScopeDetails scopeDetails = ScopeDetails.newBuilder().build();
    FileChange fileChange = FileChange.newBuilder().build();
    List<FileChange> fileChangeList = List.of(fileChange);
    doReturn(fileChangeList).when(inputSetFullGitSyncHandler).getFileChangesForFullSync(scopeDetails);
    List<FileChange> result = inputSetEntityGitSyncHelper.listAllEntities(scopeDetails);
    assertThat(result).isEqualTo(fileChangeList);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testUpdateEntityFilePath() {
    mockStatic(PMSInputSetElementMapper.class);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .identifier(identifier)
                                        .name(name)
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .build();
    InputSetEntity updatedEntity = InputSetEntity.builder()
                                       .identifier(identifier)
                                       .name(name)
                                       .accountId(accountId)
                                       .orgIdentifier(orgId)
                                       .projectIdentifier(projectId)
                                       .pipelineIdentifier(pipelineId)
                                       .yaml(inputSetYaml)
                                       .build();
    when(PMSInputSetElementMapper.toInputSetEntity(accountId, inputSetYaml)).thenReturn(inputSetEntity);
    doReturn(updatedEntity).when(pmsInputSetService).updateGitFilePath(inputSetEntity, filePath);
    InputSetYamlDTO inputSetYamlDTO = InputSetYamlDTOMapper.toDTO(updatedEntity);
    InputSetYamlDTO result = inputSetEntityGitSyncHelper.updateEntityFilePath(accountId, inputSetYaml, filePath);
    assertThat(result).isEqualTo(inputSetYamlDTO);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testFullSyncEntity() {
    EntityDetailProtoDTO entityDetailProtoDTO = EntityDetailProtoDTO.newBuilder().build();
    InputSetYamlDTO inputSetYamlDTO = InputSetYamlDTO.builder().build();
    FullSyncChangeSet fullSyncChangeSet = FullSyncChangeSet.newBuilder().setEntityDetail(entityDetailProtoDTO).build();
    doReturn(inputSetYamlDTO).when(inputSetFullGitSyncHandler).syncEntity(fullSyncChangeSet.getEntityDetail());
    InputSetYamlDTO result = inputSetEntityGitSyncHelper.fullSyncEntity(fullSyncChangeSet);
    assertThat(result).isEqualTo(inputSetYamlDTO);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetYamlFromEntityRef() {
    InputSetReferenceProtoDTO inputSetRef =
        InputSetReferenceProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue(accountId).build())
            .setOrgIdentifier(StringValue.newBuilder().setValue(orgId).build())
            .setProjectIdentifier(StringValue.newBuilder().setValue(projectId).build())
            .setPipelineIdentifier(StringValue.newBuilder().setValue(pipelineId).build())
            .setIdentifier(StringValue.newBuilder().setValue(identifier).build())
            .build();
    EntityDetailProtoDTO entityReference = EntityDetailProtoDTO.newBuilder().setInputSetRef(inputSetRef).build();
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .identifier(identifier)
                                        .name(name)
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .build();
    Optional<InputSetEntity> optionalInputSetEntity = Optional.of(inputSetEntity);
    doReturn(optionalInputSetEntity)
        .when(pmsInputSetService)
        .getWithoutValidations(accountId, orgId, projectId, pipelineId, identifier, false, false, false);
    String result = inputSetEntityGitSyncHelper.getYamlFromEntityRef(entityReference);
    assertThat(result).isEqualTo(inputSetYaml);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetYamlFromEntityRefInvalidRequest() {
    InputSetReferenceProtoDTO inputSetRef =
        InputSetReferenceProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue(accountId).build())
            .setOrgIdentifier(StringValue.newBuilder().setValue(orgId).build())
            .setProjectIdentifier(StringValue.newBuilder().setValue(projectId).build())
            .setPipelineIdentifier(StringValue.newBuilder().setValue(pipelineId).build())
            .setIdentifier(StringValue.newBuilder().setValue(identifier).build())
            .build();
    EntityDetailProtoDTO entityReference = EntityDetailProtoDTO.newBuilder().setInputSetRef(inputSetRef).build();
    Optional<InputSetEntity> optionalInputSetEntity = Optional.empty();
    doReturn(optionalInputSetEntity)
        .when(pmsInputSetService)
        .getWithoutValidations(accountId, orgId, projectId, pipelineId, identifier, false, false, false);
    try {
      inputSetEntityGitSyncHelper.getYamlFromEntityRef(entityReference);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage())
          .isEqualTo(format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] doesn't exist.",
              identifier, pipelineId, projectId, orgId));
    }
  }
}
