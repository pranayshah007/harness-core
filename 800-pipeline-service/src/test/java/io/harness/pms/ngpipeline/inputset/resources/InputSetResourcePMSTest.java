/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SAMARTH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PIPELINE)
public class InputSetResourcePMSTest extends PipelineServiceTestBase {
  InputSetResourcePMS inputSetResourcePMS;
  @Mock PMSInputSetService pmsInputSetService;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String PIPELINE_IDENTIFIER = "pipeId";
  private static final String INPUT_SET_ID = "inputSetId";
  private static final String INVALID_INPUT_SET_ID = "invalidInputSetId";
  private static final String OVERLAY_INPUT_SET_ID = "overlayInputSetId";
  private static final String INVALID_OVERLAY_INPUT_SET_ID = "invalidOverlayInputSetId";
  private String inputSetYaml;
  private String overlayInputSetYaml;
  private String pipelineYaml;

  InputSetEntity inputSetEntity;
  InputSetEntity overlayInputSetEntity;

  List<String> stages =
      Arrays.asList("using", "a", "list", "to", "ensure", "that", "this", "param", "is", "not", "ignored");

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    inputSetResourcePMS = new InputSetResourcePMS(pmsInputSetService, validateAndMergeHelper);

    String inputSetFilename = "inputSet1.yml";
    inputSetYaml = readFile(inputSetFilename);
    String overlayInputSetFilename = "overlay1.yml";
    overlayInputSetYaml = readFile(overlayInputSetFilename);
    String pipelineYamlFileName = "pipeline.yml";
    pipelineYaml = readFile(pipelineYamlFileName);

    inputSetEntity = InputSetEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                         .identifier(INPUT_SET_ID)
                         .name(INPUT_SET_ID)
                         .yaml(inputSetYaml)
                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                         .version(1L)
                         .build();

    overlayInputSetEntity = InputSetEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                .identifier(OVERLAY_INPUT_SET_ID)
                                .name(OVERLAY_INPUT_SET_ID)
                                .yaml(overlayInputSetYaml)
                                .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                .version(1L)
                                .build();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSet() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, false);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMS.getInputSet(
        INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(responseDTO.getData().getName(), INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSetWithInvalidInputSetId() {
    doReturn(Optional.empty())
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INVALID_INPUT_SET_ID, false);

    assertThatThrownBy(()
                           -> inputSetResourcePMS.getInputSet(INVALID_INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("InputSet with the given ID: %s does not exist or has been deleted", INVALID_INPUT_SET_ID));
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetOverlayInputSet() {
    doReturn(Optional.of(overlayInputSetEntity))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, OVERLAY_INPUT_SET_ID, false);

    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMS.getOverlayInputSet(
        OVERLAY_INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getOverlayInputSetYaml()).isEqualTo(overlayInputSetYaml);
    getCallAssertions(responseDTO.getData().getName(), OVERLAY_INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  private void getCallAssertions(String name, String inputSetId, String identifier, String pipelineIdentifier,
      String projectIdentifier, String orgIdentifier, String accountId) {
    assertThat(name).isEqualTo(inputSetId);
    assertThat(identifier).isEqualTo(inputSetId);
    assertThat(pipelineIdentifier).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(projectIdentifier).isEqualTo(PROJ_IDENTIFIER);
    assertThat(orgIdentifier).isEqualTo(ORG_IDENTIFIER);
    assertThat(accountId).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetOverlayInputSetWithInvalidInputSetId() {
    doReturn(Optional.empty())
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INVALID_OVERLAY_INPUT_SET_ID, false);

    assertThatThrownBy(()
                           -> inputSetResourcePMS.getOverlayInputSet(INVALID_OVERLAY_INPUT_SET_ID, ACCOUNT_ID,
                               ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "InputSet with the given ID: %s does not exist or has been deleted", INVALID_OVERLAY_INPUT_SET_ID));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).create(any());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMS.createInputSet(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, inputSetYaml);
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYaml);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateOverlayInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).create(any());
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMS.createOverlayInputSet(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, overlayInputSetYaml);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).update(any(), any());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMS.updateInputSet(null, INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, inputSetYaml);
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYaml);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateOverlayInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).update(any(), any());
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMS.updateOverlayInputSet(null,
        INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, overlayInputSetYaml);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDeleteInputSet() {
    doReturn(true)
        .when(pmsInputSetService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, null);
    ResponseDTO<Boolean> responseDTO = inputSetResourcePMS.delete(
        null, INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertTrue(responseDTO.getData());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testListInputSetsForPipeline() {
    doReturn(PageableExecutionUtils.getPage(Collections.singletonList(inputSetEntity),
                 PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InputSetEntityKeys.createdAt)), () -> 1L))
        .when(pmsInputSetService)
        .list(any(), any(), eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER));
    ResponseDTO<PageResponse<InputSetSummaryResponseDTOPMS>> responseDTO = inputSetResourcePMS.listInputSetsForPipeline(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null);
    assertEquals(responseDTO.getStatus(), Status.SUCCESS);
    assertEquals(responseDTO.getData().getPageIndex(), 0);
    assertEquals(responseDTO.getData().getPageItemCount(), 1);
    assertEquals(responseDTO.getData().getPageSize(), 10);
    assertEquals(responseDTO.getData().getTotalItems(), 1);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetTemplateFromPipeline() {
    doReturn(InputSetTemplateResponseDTOPMS.builder().inputSetTemplateYaml(inputSetYaml).build())
        .when(validateAndMergeHelper)
        .getInputSetTemplateResponseDTO(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, Collections.emptyList());
    ResponseDTO<InputSetTemplateResponseDTOPMS> inputSetTemplateResponseDTO =
        inputSetResourcePMS.getTemplateFromPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null);
    assertEquals(inputSetTemplateResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(inputSetTemplateResponseDTO.getData().getInputSetTemplateYaml(), inputSetYaml);

    doReturn(InputSetTemplateResponseDTOPMS.builder().inputSetTemplateYaml(inputSetYaml).build())
        .when(validateAndMergeHelper)
        .getInputSetTemplateResponseDTO(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, stages);
    inputSetTemplateResponseDTO =
        inputSetResourcePMS.getTemplateFromPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            null, InputSetTemplateRequestDTO.builder().stageIdentifiers(stages).build());
    assertEquals(inputSetTemplateResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(inputSetTemplateResponseDTO.getData().getInputSetTemplateYaml(), inputSetYaml);
    verify(validateAndMergeHelper, times(1))
        .getInputSetTemplateResponseDTO(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, stages);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplate() {
    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            Collections.emptyList(), null, null, null);
    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .mergeInputSetIntoPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, pipelineYaml, null, null, null);
    MergeInputSetRequestDTOPMS inputSetRequestDTOPMS = MergeInputSetRequestDTOPMS.builder()
                                                           .withMergedPipelineYaml(true)
                                                           .inputSetReferences(Collections.emptyList())
                                                           .build();
    ResponseDTO<MergeInputSetResponseDTOPMS> mergeInputSetResponseDTOPMSResponseDTO =
        inputSetResourcePMS.getMergeInputSetFromPipelineTemplate(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTOPMS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getCompletePipelineYaml(), pipelineYaml);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getPipelineYaml(), pipelineYaml);

    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            Collections.emptyList(), null, null, stages);

    MergeInputSetRequestDTOPMS inputSetRequestDTOPMSWithStages = MergeInputSetRequestDTOPMS.builder()
                                                                     .withMergedPipelineYaml(false)
                                                                     .inputSetReferences(Collections.emptyList())
                                                                     .stageIdentifiers(stages)
                                                                     .build();
    mergeInputSetResponseDTOPMSResponseDTO = inputSetResourcePMS.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTOPMSWithStages);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getPipelineYaml(), pipelineYaml);
    verify(validateAndMergeHelper, times(1))
        .getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
            Collections.emptyList(), null, null, stages);
  }
}
