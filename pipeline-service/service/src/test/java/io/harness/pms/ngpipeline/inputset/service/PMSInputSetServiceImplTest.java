/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.inputset.gitsync.InputSetYamlDTO;
import io.harness.pms.inputset.gitsync.InputSetYamlDTOMapper;
import io.harness.pms.ngpipeline.inputset.api.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.repositories.inputset.PMSInputSetRepository;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@PrepareForTest({InputSetValidationHelper.class})
@OwnedBy(PIPELINE)
public class PMSInputSetServiceImplTest extends PipelineServiceTestBase {
  @Mock GitXSettingsHelper gitXSettingsHelper;
  @Inject PMSInputSetServiceImpl pmsInputSetService;
  @Spy @InjectMocks PMSInputSetServiceImpl pmsInputSetServiceMock;
  @Mock private PMSInputSetRepository inputSetRepository;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock private GitAwareEntityHelper gitAwareEntityHelper;
  @Mock private PMSPipelineService pipelineService;
  @Mock private InputSetsApiUtils inputSetsApiUtils;

  String ACCOUNT_ID = "account_id";
  String ORG_IDENTIFIER = "orgId";
  String PROJ_IDENTIFIER = "projId";
  String PIPELINE_IDENTIFIER = "pipeline_identifier";

  String INPUT_SET_IDENTIFIER = "identifier";
  String NAME = "identifier";
  String YAML;
  String YAMLV1;

  InputSetEntity inputSetEntity;
  InputSetEntity inputSetEntityV1;

  String OVERLAY_INPUT_SET_IDENTIFIER = "overlay-identifier";
  List<String> inputSetReferences = ImmutableList.of("inputSet2", "inputSet22");
  String OVERLAY_YAML;

  InputSetEntity overlayInputSetEntity;
  PipelineEntity pipelineEntity;

  String REPO_NAME = "testRepo";
  String REPO_NAME2 = "testRepo2";

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    YAML = "inputSet:\n"
        + "  identifier: input1\n"
        + "  name: this name\n"
        + "  description: this has a description too\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projId\n"
        + "  tags:\n"
        + "    company: harness\n"
        + "    kind : normal\n"
        + "  pipeline:\n"
        + "    identifier: \"Test_Pipline11\"\n"
        + "    stages:\n"
        + "      - stage:\n"
        + "          identifier: \"qaStage\"\n"
        + "          spec:\n"
        + "            execution:\n"
        + "              steps:\n"
        + "                - step:\n"
        + "                    identifier: \"httpStep1\"\n"
        + "                    spec:\n"
        + "                      url: www.bing.com";
    inputSetEntity = InputSetEntity.builder()
                         .identifier(INPUT_SET_IDENTIFIER)
                         .name(NAME)
                         .yaml(YAML)
                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                         .storeType(StoreType.INLINE)
                         .build();

    OVERLAY_YAML = "overlayInputSet:\n"
        + "  identifier: overlay1\n"
        + "  name : thisName\n"
        + "  tags:\n"
        + "    isOverlaySet : yes it is\n"
        + "  description: this is an overlay input set\n"
        + "  inputSetReferences:\n"
        + "    - inputSet2\n"
        + "    - inputSet22";
    overlayInputSetEntity = InputSetEntity.builder()
                                .identifier(OVERLAY_INPUT_SET_IDENTIFIER)
                                .name(NAME)
                                .yaml(OVERLAY_YAML)
                                .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                .inputSetReferences(inputSetReferences)
                                .storeType(StoreType.INLINE)
                                .build();

    String inputSetV1YamlFileName = "inputSetV1.yaml";
    YAMLV1 = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(inputSetV1YamlFileName)), StandardCharsets.UTF_8);

    inputSetEntityV1 = InputSetEntity.builder()
                           .identifier(INPUT_SET_IDENTIFIER)
                           .name(NAME)
                           .yaml(YAMLV1)
                           .inputSetEntityType(InputSetEntityType.INPUT_SET)
                           .accountId(ACCOUNT_ID)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .projectIdentifier(PROJ_IDENTIFIER)
                           .pipelineIdentifier(PIPELINE_IDENTIFIER)
                           .storeType(StoreType.INLINE)
                           .harnessVersion(PipelineVersion.V1)
                           .build();

    String pipelineYamlFileName = "failure-strategy.yaml";
    String pipelineYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipelineYamlFileName)), StandardCharsets.UTF_8);

    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .name(PIPELINE_IDENTIFIER)
                         .yaml(pipelineYaml)
                         .build();
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    on(pmsInputSetService).set("inputSetsApiUtils", inputSetsApiUtils);
    on(pmsInputSetService).set("gitXSettingsHelper", gitXSettingsHelper);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    List<InputSetEntity> inputSets = ImmutableList.of(inputSetEntity, overlayInputSetEntity);
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    for (InputSetEntity entity : inputSets) {
      InputSetEntity createdInputSet = pmsInputSetService.create(entity, false);
      assertThat(createdInputSet).isNotNull();
      assertThat(createdInputSet.getAccountId()).isEqualTo(entity.getAccountId());
      assertThat(createdInputSet.getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
      assertThat(createdInputSet.getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
      assertThat(createdInputSet.getIdentifier()).isEqualTo(entity.getIdentifier());
      assertThat(createdInputSet.getName()).isEqualTo(entity.getName());
      assertThat(createdInputSet.getYaml()).isEqualTo(entity.getYaml());
      assertThat(createdInputSet.getVersion()).isEqualTo(0L);

      Optional<InputSetEntity> getInputSet = pmsInputSetService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
          PIPELINE_IDENTIFIER, entity.getIdentifier(), false, null, null, false, false, false);
      assertThat(getInputSet).isPresent();
      assertThat(getInputSet.get().getAccountId()).isEqualTo(createdInputSet.getAccountId());
      assertThat(getInputSet.get().getOrgIdentifier()).isEqualTo(createdInputSet.getOrgIdentifier());
      assertThat(getInputSet.get().getProjectIdentifier()).isEqualTo(createdInputSet.getProjectIdentifier());
      assertThat(getInputSet.get().getIdentifier()).isEqualTo(createdInputSet.getIdentifier());
      assertThat(getInputSet.get().getName()).isEqualTo(createdInputSet.getName());
      assertThat(getInputSet.get().getYaml()).isEqualTo(createdInputSet.getYaml());
      assertThat(getInputSet.get().getVersion()).isEqualTo(0L);

      String DESCRIPTION = "Added a description here";
      InputSetEntity updateInputSetEntity = InputSetEntity.builder()
                                                .identifier(entity.getIdentifier())
                                                .name(NAME)
                                                .description(DESCRIPTION)
                                                .yaml(YAML)
                                                .inputSetEntityType(entity.getInputSetEntityType())
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_IDENTIFIER)
                                                .projectIdentifier(PROJ_IDENTIFIER)
                                                .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                .inputSetReferences(entity.getInputSetReferences())
                                                .build();
      InputSetEntity updatedInputSet = pmsInputSetService.update(ChangeType.MODIFY, updateInputSetEntity, false);
      assertThat(updatedInputSet.getAccountId()).isEqualTo(updateInputSetEntity.getAccountId());
      assertThat(updatedInputSet.getOrgIdentifier()).isEqualTo(updateInputSetEntity.getOrgIdentifier());
      assertThat(updatedInputSet.getProjectIdentifier()).isEqualTo(updateInputSetEntity.getProjectIdentifier());
      assertThat(updatedInputSet.getIdentifier()).isEqualTo(updateInputSetEntity.getIdentifier());
      assertThat(updatedInputSet.getName()).isEqualTo(updateInputSetEntity.getName());
      assertThat(updatedInputSet.getDescription()).isEqualTo(updateInputSetEntity.getDescription());
      assertThat(updatedInputSet.getYaml()).isEqualTo(updateInputSetEntity.getYaml());
      assertThat(updatedInputSet.getVersion()).isEqualTo(1L);

      InputSetEntity incorrectInputSetEntity = InputSetEntity.builder()
                                                   .identifier(entity.getIdentifier())
                                                   .name(NAME)
                                                   .description(DESCRIPTION)
                                                   .yaml(YAML)
                                                   .inputSetEntityType(entity.getInputSetEntityType())
                                                   .accountId("newAccountID")
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJ_IDENTIFIER)
                                                   .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                   .inputSetReferences(entity.getInputSetReferences())
                                                   .build();
      assertThatThrownBy(() -> pmsInputSetService.update(ChangeType.MODIFY, incorrectInputSetEntity, false))
          .isInstanceOf(InvalidRequestException.class);

      boolean delete = pmsInputSetService.delete(
          ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, entity.getIdentifier(), 1L);
      assertThat(delete).isTrue();

      assertThatThrownBy(()
                             -> pmsInputSetService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
                                 entity.getIdentifier(), false, null, null, false, false, false))
          .isInstanceOf(EntityNotFoundException.class);
    }
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testList() {
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    when(inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(any())).thenReturn(false);
    doNothing().when(gitXSettingsHelper).enforceGitExperienceIfApplicable(any(), any(), any());
    pmsInputSetService.create(inputSetEntity, false);
    pmsInputSetService.create(overlayInputSetEntity, false);

    Criteria criteriaFromFilter = PMSInputSetFilterHelper.createCriteriaForGetList(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, InputSetListTypePMS.ALL, "", false);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);

    Page<InputSetEntity> list =
        pmsInputSetService.list(criteriaFromFilter, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);
    assertThat(list.getContent().get(0).getIdentifier()).isEqualTo(inputSetEntity.getIdentifier());
    assertThat(list.getContent().get(1).getIdentifier()).isEqualTo(overlayInputSetEntity.getIdentifier());

    InputSetEntity inputSetEntity2 = InputSetEntity.builder()
                                         .identifier(INPUT_SET_IDENTIFIER + "2")
                                         .name(NAME)
                                         .yaml(YAML)
                                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJ_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .build();

    pmsInputSetService.create(inputSetEntity2, false);
    Page<InputSetEntity> list2 =
        pmsInputSetService.list(criteriaFromFilter, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertThat(list2.getContent()).isNotNull();
    assertThat(list2.getContent().size()).isEqualTo(3);
    assertThat(list2.getContent().get(0).getIdentifier()).isEqualTo(inputSetEntity.getIdentifier());
    assertThat(list2.getContent().get(1).getIdentifier()).isEqualTo(overlayInputSetEntity.getIdentifier());
    assertThat(list2.getContent().get(2).getIdentifier()).isEqualTo(inputSetEntity2.getIdentifier());
    mockSettings.close();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testDeleteInputSetsOnPipelineDeletion() {
    Criteria criteria = new Criteria();
    criteria.and("accountId")
        .is(ACCOUNT_ID)
        .and("orgIdentifier")
        .is(ORG_IDENTIFIER)
        .and("projectIdentifier")
        .is(PROJ_IDENTIFIER)
        .and("pipelineIdentifier")
        .is(PIPELINE_IDENTIFIER);
    Query query = new Query(criteria);

    pmsInputSetServiceMock.deleteInputSetsOnPipelineDeletion(pipelineEntity);

    verify(inputSetRepository, times(1)).deleteAllInputSetsWhenPipelineDeleted(query);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testDeleteInputSetsOnPipelineDeletionWhenDeleteFailed() {
    Criteria criteria = new Criteria();
    criteria.and("accountId")
        .is(ACCOUNT_ID)
        .and("orgIdentifier")
        .is(ORG_IDENTIFIER)
        .and("projectIdentifier")
        .is(PROJ_IDENTIFIER)
        .and("pipelineIdentifier")
        .is(PIPELINE_IDENTIFIER);
    Query query = new Query(criteria);

    doThrow(new InvalidRequestException("random exception"))
        .when(inputSetRepository)
        .deleteAllInputSetsWhenPipelineDeleted(query);

    assertThatThrownBy(() -> pmsInputSetServiceMock.deleteInputSetsOnPipelineDeletion(pipelineEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("InputSets for Pipeline [%s] under Project[%s], Organization [%s] couldn't be deleted.",
                PIPELINE_IDENTIFIER, PROJ_IDENTIFIER, ORG_IDENTIFIER));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSwitchValidationFlag() {
    on(pmsInputSetService).set("inputSetRepository", inputSetRepository);
    when(inputSetRepository.update(any(), any())).thenReturn(InputSetEntity.builder().build());
    assertTrue(pmsInputSetService.switchValidationFlag(inputSetEntity, true));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCheckForInputSetsForPipeline() {
    doReturn(true)
        .when(inputSetRepository)
        .existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, true);
    assertThat(pmsInputSetServiceMock.checkForInputSetsForPipeline(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER))
        .isTrue();

    doReturn(false)
        .when(inputSetRepository)
        .existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, true);
    assertThat(pmsInputSetServiceMock.checkForInputSetsForPipeline(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER))
        .isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  @Deprecated
  public void testImportInputSetFromRemote() {
    String identifier = "input1";
    String name = "this name";
    String description = "this has a description too";
    String pipelineIdentifier = "Test_Pipline11";
    when(inputSetsApiUtils.inputSetVersion(ACCOUNT_ID, YAML)).thenReturn(PipelineVersion.V0);
    doReturn(YAML).when(gitAwareEntityHelper).fetchYAMLFromRemote(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, true);
    InputSetEntity inBetweenEntity = PMSInputSetElementMapper.toInputSetEntity(ACCOUNT_ID, YAML);
    InputSetImportRequestDTO inputSetImportRequest =
        InputSetImportRequestDTO.builder().inputSetName(name).inputSetDescription(description).build();
    doReturn(inputSetEntity).when(inputSetRepository).saveForImportedYAML(inBetweenEntity);
    doReturn("repoUrl")
        .when(pmsInputSetServiceMock)
        .getRepoUrlAndCheckForFileUniqueness(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);
    InputSetEntity savedEntity = pmsInputSetServiceMock.importInputSetFromRemote(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineIdentifier, identifier, inputSetImportRequest, true);
    assertThat(savedEntity).isEqualTo(inputSetEntity);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateForOldGitSync() {
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    pmsInputSetServiceMock.create(inputSetEntity, false);
    verify(inputSetRepository, times(1)).saveForOldGitSync(inputSetEntity, InputSetYamlDTOMapper.toDTO(inputSetEntity));
    verify(inputSetRepository, times(0)).save(any());
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateWithExceptions() {
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doThrow(new DuplicateKeyException("msg")).when(inputSetRepository).save(inputSetEntity);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false))
        .isInstanceOf(DuplicateFieldException.class);

    doThrow(new ExplanationException("msg", null)).when(inputSetRepository).save(inputSetEntity);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false))
        .isInstanceOf(ExplanationException.class);
    doThrow(new HintException("msg", null)).when(inputSetRepository).save(inputSetEntity);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false)).isInstanceOf(HintException.class);
    doThrow(new ScmException(ErrorCode.DEFAULT_ERROR_CODE)).when(inputSetRepository).save(inputSetEntity);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false)).isInstanceOf(ScmException.class);

    doThrow(new NullPointerException()).when(inputSetRepository).save(inputSetEntity);
    assertThatThrownBy(() -> pmsInputSetServiceMock.create(inputSetEntity, false))
        .isInstanceOf(InvalidRequestException.class);

    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetForOldGitSync() {
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(Optional.of(inputSetEntity))
        .when(inputSetRepository)
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);
    pmsInputSetServiceMock.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER,
        false, null, null, false, false, false);
    verify(inputSetRepository, times(1))
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);
    verify(inputSetRepository, times(0))
        .find(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetWithExceptions() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    doThrow(new ExplanationException("msg", null))
        .when(inputSetRepository)
        .find(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false,
            false, false);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false))
        .isInstanceOf(ExplanationException.class);
    doThrow(new HintException("msg", null))
        .when(inputSetRepository)
        .find(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false,
            false, false);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false))
        .isInstanceOf(HintException.class);
    doThrow(new ScmException(ErrorCode.DEFAULT_ERROR_CODE))
        .when(inputSetRepository)
        .find(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false,
            false, false);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false))
        .isInstanceOf(ScmException.class);

    doThrow(new NullPointerException())
        .when(inputSetRepository)
        .find(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false,
            false, false);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(Optional.of(inputSetEntity.withStoreType(StoreType.REMOTE)))
        .when(inputSetRepository)
        .find(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false,
            false, false);
    // without mocks this will throw an exception
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false));
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    // no exception with the mock
    pmsInputSetServiceMock.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER,
        false, null, null, false, false, false);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateForOldGitSync() {
    InputSetYamlDTO inputSetYamlDTO = InputSetYamlDTOMapper.toDTO(inputSetEntity);
    ChangeType c = ChangeType.MODIFY;
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    setupGitContext(GitEntityInfo.builder().isNewBranch(true).branch("newBranch").yamlGitConfigId("repo").build());
    doReturn(inputSetEntity).when(inputSetRepository).updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c);
    InputSetEntity updateIntoNewBranch = pmsInputSetServiceMock.update(c, inputSetEntity, false);
    assertThat(updateIntoNewBranch).isEqualTo(inputSetEntity);
    verify(inputSetRepository, times(0)).findForOldGitSync(any(), any(), any(), any(), any(), anyBoolean());

    setupGitContext(GitEntityInfo.builder().isNewBranch(false).branch("branch").yamlGitConfigId("repo").build());

    doReturn(Optional.empty())
        .when(inputSetRepository)
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false))
        .isInstanceOf(InvalidRequestException.class);

    doReturn(Optional.of(inputSetEntity.withVersion(3L)))
        .when(inputSetRepository)
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity.withVersion(10L), false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("is not on the correct version.");

    doReturn(Optional.of(inputSetEntity))
        .when(inputSetRepository)
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);
    doReturn(inputSetEntity)
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity.withIsEntityInvalid(false).withIsInvalid(false), inputSetYamlDTO, c);
    InputSetEntity simpleUpdatedEntity = pmsInputSetServiceMock.update(c, inputSetEntity, false);
    assertThat(simpleUpdatedEntity).isEqualTo(inputSetEntity);

    verify(inputSetRepository, times(0)).update(any());
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateForOldGitSyncWithErrors() {
    InputSetYamlDTO inputSetYamlDTO = InputSetYamlDTOMapper.toDTO(inputSetEntity);
    ChangeType c = ChangeType.MODIFY;
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    setupGitContext(GitEntityInfo.builder().isNewBranch(true).branch("newBranch").yamlGitConfigId("repo").build());

    doReturn(null).when(inputSetRepository).updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("could not be updated.");

    doThrow(new ExplanationException("e", null))
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false))
        .isInstanceOf(ExplanationException.class);
    doThrow(new HintException("e")).when(inputSetRepository).updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false)).isInstanceOf(HintException.class);
    doThrow(new ScmException("e", null))
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false)).isInstanceOf(ScmException.class);

    doThrow(new NullPointerException())
        .when(inputSetRepository)
        .updateForOldGitSync(inputSetEntity, inputSetYamlDTO, c);
    assertThatThrownBy(() -> pmsInputSetServiceMock.update(c, inputSetEntity, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Error while updating input set");

    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeleteWithError() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doThrow(new NullPointerException())
        .when(inputSetRepository)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("could not be deleted.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeleteForOldGitSync() {
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    doReturn(Optional.empty())
        .when(inputSetRepository)
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("doesn't exist.");

    doReturn(Optional.of(inputSetEntity.withVersion(2L)))
        .when(inputSetRepository)
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, 9L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(" is not on the correct version.");

    doReturn(Optional.of(inputSetEntity))
        .when(inputSetRepository)
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);

    InputSetEntity withDeleted = inputSetEntity.withDeleted(true);
    InputSetYamlDTO inputSetYamlDTO = InputSetYamlDTOMapper.toDTO(withDeleted);
    boolean delete = pmsInputSetServiceMock.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null);
    assertThat(delete).isTrue();

    doThrow(new NullPointerException()).when(inputSetRepository).deleteForOldGitSync(withDeleted, inputSetYamlDTO);
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("couldn't be deleted");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSyncInputSetWithGit() {
    InputSetReferenceProtoDTO inputSetReferenceProtoDTO =
        InputSetReferenceProtoDTO.newBuilder()
            .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
            .setOrgIdentifier(StringValue.of(ORG_IDENTIFIER))
            .setProjectIdentifier(StringValue.of(PROJ_IDENTIFIER))
            .setPipelineIdentifier(StringValue.of(PIPELINE_IDENTIFIER))
            .setIdentifier(StringValue.of(INPUT_SET_IDENTIFIER))
            .build();
    EntityDetailProtoDTO entityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder().setInputSetRef(inputSetReferenceProtoDTO).build();
    doReturn(Optional.empty())
        .when(inputSetRepository)
        .findForOldGitSync(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true);
    assertThatThrownBy(() -> pmsInputSetServiceMock.syncInputSetWithGit(entityDetailProtoDTO))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateGitFilePath() {
    String newFilePath = "folder/.harness/file.yaml";
    doReturn(inputSetEntity.withDescription("after update dummy description"))
        .when(inputSetRepository)
        .update(any(), any(), any(), any(), any());
    InputSetEntity inputSetEntityUpdated = pmsInputSetServiceMock.updateGitFilePath(inputSetEntity, newFilePath);
    assertThat(inputSetEntityUpdated.getDescription()).isEqualTo("after update dummy description");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildInvalidYamlException() {
    InvalidYamlException invalidYamlException =
        pmsInputSetService.buildInvalidYamlException("error msg from test", "yaml: this");
    assertThat(invalidYamlException.getYaml()).isEqualTo("yaml: this");
    YamlSchemaErrorWrapperDTO metadata = (YamlSchemaErrorWrapperDTO) invalidYamlException.getMetadata();
    List<YamlSchemaErrorDTO> schemaErrors = metadata.getSchemaErrors();
    assertThat(schemaErrors).hasSize(1);
    YamlSchemaErrorDTO yamlSchemaErrorDTO = schemaErrors.get(0);
    assertThat(yamlSchemaErrorDTO.getMessage()).isEqualTo("error msg from test");
    assertThat(yamlSchemaErrorDTO.getFqn()).isEqualTo("$.inputSet");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testListWithCriteria() {
    Criteria randomCriteria = Criteria.where("thisKey").is("thisValue");
    doReturn(Collections.singletonList(InputSetEntity.builder().identifier("thisId").build()))
        .when(inputSetRepository)
        .findAll(randomCriteria);
    List<InputSetEntity> list = pmsInputSetServiceMock.list(randomCriteria);
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getIdentifier()).isEqualTo("thisId");
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetRepoUrlAndCheckForFileUniqueness() {
    String repoUrl = "repoUrl123";
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().filePath("filePath").build();
    MockedStatic<GitAwareContextHelper> utilities = Mockito.mockStatic(GitAwareContextHelper.class);
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);

    doReturn(repoUrl).when(gitAwareEntityHelper).getRepoUrl(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(true)
        .when(inputSetRepository)
        .checkIfInputSetWithGivenFilePathExists(ACCOUNT_ID, repoUrl, gitEntityInfo.getFilePath());
    assertThatThrownBy(()
                           -> pmsInputSetServiceMock.getRepoUrlAndCheckForFileUniqueness(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false))
        .isInstanceOf(DuplicateFileImportException.class);
    assertThat(pmsInputSetServiceMock.getRepoUrlAndCheckForFileUniqueness(
                   ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, true))
        .isEqualTo(repoUrl);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testImportInputSetsValidationChecks() {
    String importedInputSetYaml = "inputSet:\n"
        + "  identifier: \"inputSet2\"\n"
        + "  pipeline:\n"
        + "    identifier: \"asdfasdfsadfadsfsaf\"\n"
        + "    stages:\n"
        + "    - stage:\n"
        + "        identifier: \"asdfasdf\"\n"
        + "        type: \"Approval\"\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "            - step:\n"
        + "                identifier: \"sdfasdfasfda\"\n"
        + "                type: \"HarnessApproval\"\n"
        + "                spec:\n"
        + "                  approvers:\n"
        + "                    minimumCount: 1\n"
        + "                    userGroups:\n"
        + "                    - \"account.ug3\"\n"
        + "  name: \"inputSet2\"\n"
        + "  orgIdentifier: \"default\"\n"
        + "  projectIdentifier: \"GitX_Remote\"\n";
    String orgIdentifier = "default";
    String projectIdentifier = "GitX_Remote";
    String pipelineIdentifier = "asdfasdfsadfadsfsaf";
    String inputSetIdentifier = "inputSet2";
    InputSetImportRequestDTO requestDTO = InputSetImportRequestDTO.builder()
                                              .inputSetName("inputSet2")
                                              .inputSetDescription("junk value description")
                                              .build();

    Assertions.assertDoesNotThrow(
        ()
            -> pmsInputSetServiceMock.checkAndThrowMismatchInImportedInputSetMetadata(orgIdentifier, projectIdentifier,
                pipelineIdentifier, inputSetIdentifier, requestDTO, importedInputSetYaml));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateInputSetV1() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    doReturn(inputSetEntityV1).when(inputSetRepository).save(inputSetEntityV1);
    InputSetEntity inputSetEntity = pmsInputSetServiceMock.create(inputSetEntityV1, false);
    assertThat(inputSetEntity).isNotNull();
    assertThat(inputSetEntityV1.getYaml()).isEqualTo(YAMLV1);
    assertThat(inputSetEntityV1.getHarnessVersion()).isEqualTo(PipelineVersion.V1);
    mockSettings.close();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateInputSetV1() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(inputSetEntityV1).when(inputSetRepository).update(inputSetEntityV1);
    InputSetEntity inputSetEntity = pmsInputSetServiceMock.update(ChangeType.MODIFY, inputSetEntityV1, false);
    assertThat(inputSetEntity).isNotNull();
    assertThat(inputSetEntityV1.getYaml()).isEqualTo(YAMLV1);
    assertThat(inputSetEntityV1.getHarnessVersion()).isEqualTo(PipelineVersion.V1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetV1() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(Optional.of(inputSetEntityV1))
        .when(inputSetRepository)
        .find(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, true, false,
            false, false);
    Optional<InputSetEntity> optionalInputSetEntity = pmsInputSetServiceMock.get(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, false, null, null, false, false, false);
    assertThat(optionalInputSetEntity.isPresent()).isTrue();
    InputSetEntity inputSetEntity = optionalInputSetEntity.get();
    assertThat(inputSetEntity.getYaml()).isEqualTo(YAMLV1);
    assertThat(inputSetEntity.getHarnessVersion()).isEqualTo(PipelineVersion.V1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testDeleteInputSetV1() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doNothing()
        .when(inputSetRepository)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER);
    boolean deleted = pmsInputSetServiceMock.delete(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_IDENTIFIER, null);
    assertThat(deleted).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testImportInputSetV1FromRemote() {
    String identifier = "set1";
    String name = "set1";
    String description = "this has a description too";
    when(inputSetsApiUtils.inputSetVersion(ACCOUNT_ID, YAMLV1)).thenReturn(PipelineVersion.V1);
    doReturn(YAMLV1).when(gitAwareEntityHelper).fetchYAMLFromRemote(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, true);
    InputSetEntity inBetweenEntity = PMSInputSetElementMapper.toInputSetEntityV1(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, YAMLV1, InputSetEntityType.INPUT_SET);
    InputSetImportRequestDTO inputSetImportRequest =
        InputSetImportRequestDTO.builder().inputSetName(name).inputSetDescription(description).build();
    doReturn(inputSetEntityV1).when(inputSetRepository).saveForImportedYAML(inBetweenEntity);
    doReturn("repoUrl")
        .when(pmsInputSetServiceMock)
        .getRepoUrlAndCheckForFileUniqueness(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);
    InputSetEntity savedEntity = pmsInputSetServiceMock.importInputSetFromRemote(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, identifier, inputSetImportRequest, true);
    assertThat(savedEntity).isEqualTo(inputSetEntityV1);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfigInlineToRemote() {
    doReturn("repoUrl").when(gitAwareEntityHelper).getRepoUrl(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder()
            .moveConfigOperationType(MoveConfigOperationType.INLINE_TO_REMOTE)
            .build();
    doReturn(inputSetEntity).when(inputSetRepository).updateInputSetEntity(any(), any(), any(), any());
    InputSetEntity movedInputSet = pmsInputSetServiceMock.moveInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, inputSetMoveConfigOperationDTO, inputSetEntity);
    assertEquals(movedInputSet.getIdentifier(), INPUT_SET_IDENTIFIER);
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertEquals(StoreType.REMOTE, gitEntityInfo.getStoreType());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfigRemoteToInline() {
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder()
            .moveConfigOperationType(MoveConfigOperationType.REMOTE_TO_INLINE)
            .build();
    doReturn(inputSetEntity).when(inputSetRepository).updateInputSetEntity(any(), any(), any(), any());
    InputSetEntity movedInputSet = pmsInputSetServiceMock.moveInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, inputSetMoveConfigOperationDTO, inputSetEntity);
    assertEquals(movedInputSet.getIdentifier(), INPUT_SET_IDENTIFIER);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidateIndependentInputSetSettingIsOffForSameRepo() {
    InputSetEntity inputSet = InputSetEntity.builder()
                                  .accountId(ACCOUNT_ID)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .identifier(INPUT_SET_IDENTIFIER)
                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                  .build();
    when(inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(any())).thenReturn(false);
    PipelineEntity pipeline = PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).repo(REPO_NAME).build();

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName(REPO_NAME)
                                      .connectorRef("connectorRef")
                                      .isNewBranch(true)
                                      .branch("branch")
                                      .build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    Assertions.assertDoesNotThrow(() -> pmsInputSetServiceMock.validateInputSetSetting(inputSet, pipeline));
  }
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidateIndependentInputSetSettingIsOffForDiffRepo() {
    InputSetEntity inputSet = InputSetEntity.builder()
                                  .accountId(ACCOUNT_ID)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .identifier(INPUT_SET_IDENTIFIER)
                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                  .build();
    when(inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(any())).thenReturn(false);
    PipelineEntity pipeline = PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).repo(REPO_NAME).build();

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName(REPO_NAME2)
                                      .connectorRef("connectorRef")
                                      .isNewBranch(true)
                                      .branch("branch")
                                      .storeType(StoreType.REMOTE)
                                      .build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    assertThrows(HintException.class, () -> pmsInputSetServiceMock.validateInputSetSetting(inputSet, pipeline));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidateIndependentInputSetSettingIsOnForDiffRepo() {
    InputSetEntity inputSet = InputSetEntity.builder()
                                  .accountId(ACCOUNT_ID)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .identifier(INPUT_SET_IDENTIFIER)
                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                  .build();
    when(inputSetsApiUtils.isDifferentRepoForPipelineAndInputSetsAccountSettingEnabled(any())).thenReturn(false);
    PipelineEntity pipeline = PipelineEntity.builder().identifier(PIPELINE_IDENTIFIER).repo(REPO_NAME).build();

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName(REPO_NAME2)
                                      .connectorRef("connectorRef")
                                      .isNewBranch(true)
                                      .branch("branch")
                                      .build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    Assertions.assertDoesNotThrow(() -> pmsInputSetServiceMock.validateInputSetSetting(inputSet, pipeline));
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testApplyGitXSettingsIfApplicable() {
    pmsInputSetService.applyGitXSettingsIfApplicable(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    InOrder inOrder = inOrder(gitXSettingsHelper);
    inOrder.verify(gitXSettingsHelper).setConnectorRefForRemoteEntity(any(), any(), any());
    inOrder.verify(gitXSettingsHelper).setDefaultStoreTypeForEntities(any(), any(), any(), any());
  }
}
