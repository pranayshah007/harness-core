/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAMARTH;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.repositories.inputset.PMSInputSetRepository;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PIPELINE)
public class PMSInputSetServiceImplTest extends PipelineServiceTestBase {
  @Inject PMSInputSetServiceImpl pmsInputSetService;
  @InjectMocks PMSInputSetServiceImpl pmsInputSetServiceMock;
  @Mock private PMSInputSetRepository inputSetRepository;
  @Mock private GitSyncSdkService gitSyncSdkService;

  String ACCOUNT_ID = "account_id";
  String ORG_IDENTIFIER = "orgId";
  String PROJ_IDENTIFIER = "projId";
  String PIPELINE_IDENTIFIER = "pipeline_identifier";

  String INPUT_SET_IDENTIFIER = "identifier";
  String NAME = "identifier";
  String YAML;

  InputSetEntity inputSetEntity;

  String OVERLAY_INPUT_SET_IDENTIFIER = "overlay-identifier";
  List<String> inputSetReferences = ImmutableList.of("inputSet2", "inputSet22");
  String OVERLAY_YAML;

  InputSetEntity overlayInputSetEntity;
  PipelineEntity pipelineEntity;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    YAML = "inputSet:\n"
        + "  identifier: input1\n"
        + "  name: this name\n"
        + "  description: this has a description too\n"
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
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    List<InputSetEntity> inputSets = ImmutableList.of(inputSetEntity, overlayInputSetEntity);

    for (InputSetEntity entity : inputSets) {
      InputSetEntity createdInputSet = pmsInputSetService.create(entity);
      assertThat(createdInputSet).isNotNull();
      assertThat(createdInputSet.getAccountId()).isEqualTo(entity.getAccountId());
      assertThat(createdInputSet.getOrgIdentifier()).isEqualTo(entity.getOrgIdentifier());
      assertThat(createdInputSet.getProjectIdentifier()).isEqualTo(entity.getProjectIdentifier());
      assertThat(createdInputSet.getIdentifier()).isEqualTo(entity.getIdentifier());
      assertThat(createdInputSet.getName()).isEqualTo(entity.getName());
      assertThat(createdInputSet.getYaml()).isEqualTo(entity.getYaml());
      assertThat(createdInputSet.getVersion()).isEqualTo(0L);

      Optional<InputSetEntity> getInputSet = pmsInputSetService.get(
          ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, entity.getIdentifier(), false);
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
      InputSetEntity updatedInputSet = pmsInputSetService.update(updateInputSetEntity, ChangeType.MODIFY);
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
      assertThatThrownBy(() -> pmsInputSetService.update(incorrectInputSetEntity, ChangeType.MODIFY))
          .isInstanceOf(InvalidRequestException.class);

      boolean delete = pmsInputSetService.delete(
          ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, entity.getIdentifier(), 1L);
      assertThat(delete).isTrue();

      Optional<InputSetEntity> deletedInputSet = pmsInputSetService.get(
          ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, entity.getIdentifier(), false);
      assertThat(deletedInputSet.isPresent()).isFalse();
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testList() {
    pmsInputSetService.create(inputSetEntity);
    pmsInputSetService.create(overlayInputSetEntity);

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

    pmsInputSetService.create(inputSetEntity2);
    Page<InputSetEntity> list2 =
        pmsInputSetService.list(criteriaFromFilter, pageRequest, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertThat(list2.getContent()).isNotNull();
    assertThat(list2.getContent().size()).isEqualTo(3);
    assertThat(list2.getContent().get(0).getIdentifier()).isEqualTo(inputSetEntity.getIdentifier());
    assertThat(list2.getContent().get(1).getIdentifier()).isEqualTo(overlayInputSetEntity.getIdentifier());
    assertThat(list2.getContent().get(2).getIdentifier()).isEqualTo(inputSetEntity2.getIdentifier());
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
}
