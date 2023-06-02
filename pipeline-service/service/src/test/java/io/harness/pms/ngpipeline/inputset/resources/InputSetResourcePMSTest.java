/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.inputset.MergeInputSetForRerunRequestDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.api.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.service.InputSetValidationHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PIPELINE)
@PrepareForTest({InputSetValidationHelper.class})
public class InputSetResourcePMSTest extends PipelineServiceTestBase {
  InputSetResourcePMSImpl inputSetResourcePMSImpl;
  @Mock PMSInputSetService pmsInputSetService;
  @Mock PMSPipelineService pipelineService;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock InputSetsApiUtils inputSetsApiUtils;
  @Mock PMSExecutionService executionService;

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
  private String inputSetYamlV1;

  InputSetEntity inputSetEntity;
  InputSetEntity overlayInputSetEntity;
  PipelineEntity pipelineEntity;

  InputSetEntity inputSetEntityV1;

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
    inputSetResourcePMSImpl = new InputSetResourcePMSImpl(pmsInputSetService, pipelineService, gitSyncSdkService,
        validateAndMergeHelper, inputSetsApiUtils, executionService);

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

    String inputSetV1Filename = "inputSetV1.yaml";
    inputSetYamlV1 = readFile(inputSetV1Filename);
    inputSetEntityV1 = InputSetEntity.builder()
                           .accountId(ACCOUNT_ID)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .projectIdentifier(PROJ_IDENTIFIER)
                           .pipelineIdentifier(PIPELINE_IDENTIFIER)
                           .identifier(INPUT_SET_ID)
                           .name(INPUT_SET_ID)
                           .yaml(inputSetYamlV1)
                           .inputSetEntityType(InputSetEntityType.INPUT_SET)
                           .harnessVersion(PipelineVersion.V1)
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
    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .yaml(pipelineYaml)
                         .version(1L)
                         .build();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSet() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false,
            false, false);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false");

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(responseDTO.getData().getName(), INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetInputSetWithCaching() {
    doReturn(Optional.of(inputSetEntity))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false,
            false, true);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "true");

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(responseDTO.getData().getName(), INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInlineAndRemoteInputSet() {
    InputSetEntity inlineInputSetEntity = inputSetEntity.withStoreType(StoreType.INLINE);
    doReturn(Optional.of(inlineInputSetEntity))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false,
            false, false);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false");

    InputSetResponseDTOPMS data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(data.getName(), INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(data.getConnectorRef()).isNull();
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());
    InputSetEntity remoteInputSetEntity = inputSetEntity.withStoreType(StoreType.REMOTE);
    remoteInputSetEntity.setRepo("repoName");
    remoteInputSetEntity.setConnectorRef("conn");
    doReturn(Optional.of(remoteInputSetEntity))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false,
            false, false);

    responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, null, null, false, null, "false");

    data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getInputSetYaml()).isEqualTo(inputSetYaml);
    getCallAssertions(data.getName(), INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(data.getConnectorRef()).isEqualTo("conn");
    EntityGitDetails gitDetails = data.getGitDetails();
    assertThat(gitDetails.getRepoName()).isEqualTo("repoName");
    assertThat(gitDetails.getBranch()).isEqualTo("brName");
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSetWithInvalidInputSetId() {
    doReturn(Optional.empty())
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INVALID_INPUT_SET_ID, false, null, null,
            false, false, false);

    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.getInputSet(INVALID_INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false"))
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
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, OVERLAY_INPUT_SET_ID, false, null, null,
            false, false, false);

    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.getOverlayInputSet(OVERLAY_INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, false, null, "false");

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getOverlayInputSetYaml()).isEqualTo(overlayInputSetYaml);
    getCallAssertions(responseDTO.getData().getName(), OVERLAY_INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInlineAndRemoteOverlayInputSet() {
    InputSetEntity inlineInputSetEntity = overlayInputSetEntity.withStoreType(StoreType.INLINE);
    doReturn(Optional.of(inlineInputSetEntity))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false,
            false, false);

    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getOverlayInputSet(INPUT_SET_ID,
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false");

    OverlayInputSetResponseDTOPMS data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getOverlayInputSetYaml()).isEqualTo(overlayInputSetYaml);
    getCallAssertions(data.getName(), OVERLAY_INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(data.getConnectorRef()).isNull();
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());
    InputSetEntity remoteInputSetEntity = overlayInputSetEntity.withStoreType(StoreType.REMOTE);
    remoteInputSetEntity.setRepo("repoName");
    remoteInputSetEntity.setConnectorRef("conn");
    doReturn(Optional.of(remoteInputSetEntity))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false,
            false, false);

    responseDTO = inputSetResourcePMSImpl.getOverlayInputSet(INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PIPELINE_IDENTIFIER, null, null, false, null, "false");

    data = responseDTO.getData();
    assertThat(data.getVersion()).isEqualTo(1L);
    assertThat(data.getOverlayInputSetYaml()).isEqualTo(overlayInputSetYaml);
    getCallAssertions(data.getName(), OVERLAY_INPUT_SET_ID, data.getIdentifier(), data.getPipelineIdentifier(),
        data.getProjectIdentifier(), data.getOrgIdentifier(), data.getAccountId());
    assertThat(data.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(data.getConnectorRef()).isEqualTo("conn");
    EntityGitDetails gitDetails = data.getGitDetails();
    assertThat(gitDetails.getRepoName()).isEqualTo("repoName");
    assertThat(gitDetails.getBranch()).isEqualTo("brName");
    assertThat(data.getEntityValidityDetails().isValid()).isTrue();
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
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INVALID_OVERLAY_INPUT_SET_ID, false,
            null, null, false, false, false);

    assertThatThrownBy(()
                           -> inputSetResourcePMSImpl.getOverlayInputSet(INVALID_OVERLAY_INPUT_SET_ID, ACCOUNT_ID,
                               ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "InputSet with the given ID: %s does not exist or has been deleted", INVALID_OVERLAY_INPUT_SET_ID));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateInputSet() {
    doReturn(PipelineVersion.V0).when(inputSetsApiUtils).inputSetVersion(any(), any());
    doReturn(inputSetEntity).when(pmsInputSetService).create(any(), anyBoolean());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.createInputSet(
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
    doReturn(inputSetEntity).when(pmsInputSetService).create(any(), anyBoolean());
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.createOverlayInputSet(
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
    doReturn(PipelineVersion.V0).when(inputSetsApiUtils).inputSetVersion(any(), any());
    doReturn(inputSetEntity).when(pmsInputSetService).update(any(), any(), anyBoolean());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.updateInputSet(null, INPUT_SET_ID,
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
  public void testUpdateOverlayInputSet() {
    doReturn(inputSetEntity).when(pmsInputSetService).update(any(), any(), anyBoolean());
    ResponseDTO<OverlayInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.updateOverlayInputSet(null,
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
    ResponseDTO<Boolean> responseDTO = inputSetResourcePMSImpl.delete(
        null, INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertTrue(responseDTO.getData());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testListInputSetsForPipeline() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(PageableExecutionUtils.getPage(Collections.singletonList(inputSetEntity),
                 PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InputSetEntityKeys.createdAt)), () -> 1L))
        .when(pmsInputSetService)
        .list(any(), any(), eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER));
    Mockito.mockStatic(InputSetValidationHelper.class);

    ResponseDTO<PageResponse<InputSetSummaryResponseDTOPMS>> responseDTO =
        inputSetResourcePMSImpl.listInputSetsForPipeline(
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
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, Collections.emptyList(), false);
    ResponseDTO<InputSetTemplateResponseDTOPMS> inputSetTemplateResponseDTO =
        inputSetResourcePMSImpl.getTemplateFromPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, "false");
    assertEquals(inputSetTemplateResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(inputSetTemplateResponseDTO.getData().getInputSetTemplateYaml(), inputSetYaml);

    doReturn(InputSetTemplateResponseDTOPMS.builder().inputSetTemplateYaml(inputSetYaml).build())
        .when(validateAndMergeHelper)
        .getInputSetTemplateResponseDTO(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, stages, false);
    inputSetTemplateResponseDTO =
        inputSetResourcePMSImpl.getTemplateFromPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, InputSetTemplateRequestDTO.builder().stageIdentifiers(stages).build(), "false");
    assertEquals(inputSetTemplateResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(inputSetTemplateResponseDTO.getData().getInputSetTemplateYaml(), inputSetYaml);
    verify(validateAndMergeHelper, times(1))
        .getInputSetTemplateResponseDTO(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, stages, false);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplate() {
    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(ACCOUNT_ID, ORG_IDENTIFIER,
            PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, Collections.emptyList(), null, null, null, null, false);
    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .mergeInputSetIntoPipeline(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, pipelineYaml, null, null, null, false);
    MergeInputSetRequestDTOPMS inputSetRequestDTOPMS = MergeInputSetRequestDTOPMS.builder()
                                                           .withMergedPipelineYaml(true)
                                                           .inputSetReferences(Collections.emptyList())
                                                           .build();
    ResponseDTO<MergeInputSetResponseDTOPMS> mergeInputSetResponseDTOPMSResponseDTO =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTOPMS, "false");
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getCompletePipelineYaml(), pipelineYaml);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getPipelineYaml(), pipelineYaml);

    doReturn(pipelineYaml)
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(ACCOUNT_ID, ORG_IDENTIFIER,
            PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, Collections.emptyList(), null, null, stages, null, false);

    MergeInputSetRequestDTOPMS inputSetRequestDTOPMSWithStages = MergeInputSetRequestDTOPMS.builder()
                                                                     .withMergedPipelineYaml(false)
                                                                     .inputSetReferences(Collections.emptyList())
                                                                     .stageIdentifiers(stages)
                                                                     .build();
    mergeInputSetResponseDTOPMSResponseDTO =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTOPMSWithStages, "false");
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getStatus(), Status.SUCCESS);
    assertEquals(mergeInputSetResponseDTOPMSResponseDTO.getData().getPipelineYaml(), pipelineYaml);
    verify(validateAndMergeHelper, times(1))
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(ACCOUNT_ID, ORG_IDENTIFIER,
            PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, Collections.emptyList(), null, null, stages, null, false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetMergeInputSetFromPipelineTemplateWithErrors() {
    List<String> inputSetReferences = Arrays.asList("is1", "is2", "ois3");
    InputSetErrorWrapperDTOPMS dummyErrorResponse =
        InputSetErrorWrapperDTOPMS.builder().uuidToErrorResponseMap(Collections.singletonMap("fqn", null)).build();
    doThrow(new InvalidInputSetException("merging error", dummyErrorResponse))
        .when(validateAndMergeHelper)
        .getMergedYamlFromInputSetReferencesAndRuntimeInputYamlWithDefaultValues(ACCOUNT_ID, ORG_IDENTIFIER,
            PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetReferences, null, null, null, null, false);
    MergeInputSetRequestDTOPMS inputSetRequestDTO = MergeInputSetRequestDTOPMS.builder()
                                                        .withMergedPipelineYaml(true)
                                                        .inputSetReferences(inputSetReferences)
                                                        .build();
    ResponseDTO<MergeInputSetResponseDTOPMS> responseDTO =
        inputSetResourcePMSImpl.getMergeInputSetFromPipelineTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, null, null, null, inputSetRequestDTO, "false");
    MergeInputSetResponseDTOPMS data = responseDTO.getData();
    assertThat(data.isErrorResponse()).isTrue();
    assertThat(data.getInputSetErrorWrapper()).isEqualTo(dummyErrorResponse);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInputSetYAMLDiff() {
    MockedStatic<InputSetValidationHelper> mockSettings = Mockito.mockStatic(InputSetValidationHelper.class);
    when(InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, pmsInputSetService, pipelineService,
             validateAndMergeHelper, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID,
             "branch", "repo"))
        .thenReturn(InputSetYamlDiffDTO.builder().oldYAML("old: yaml").newYAML("new: yaml").build());
    ResponseDTO<InputSetYamlDiffDTO> inputSetYAMLDiff = inputSetResourcePMSImpl.getInputSetYAMLDiff(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, "branch", "repo", null);
    assertThat(inputSetYAMLDiff.getData().getOldYAML()).isEqualTo("old: yaml");
    assertThat(inputSetYAMLDiff.getData().getNewYAML()).isEqualTo("new: yaml");
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testImportInputSetFromGit() {
    doReturn(inputSetEntity)
        .when(pmsInputSetService)
        .importInputSetFromRemote(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, null, true);
    GitImportInfoDTO gitImportInfoDTO = GitImportInfoDTO.builder().isForceImport(true).build();
    ResponseDTO<InputSetImportResponseDTO> inputSetImportResponse = inputSetResourcePMSImpl.importInputSetFromGit(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, gitImportInfoDTO, null);
    assertThat(inputSetImportResponse.getData().getIdentifier()).isEqualTo(INPUT_SET_ID);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetV1() {
    doReturn(Optional.of(inputSetEntityV1))
        .when(pmsInputSetService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, false, null, null, false,
            false, false);

    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getInputSet(INPUT_SET_ID, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, false, null, "false");

    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getInputSetYaml()).isEqualTo(inputSetYamlV1);
    getCallAssertions(responseDTO.getData().getName(), INPUT_SET_ID, responseDTO.getData().getIdentifier(),
        responseDTO.getData().getPipelineIdentifier(), responseDTO.getData().getProjectIdentifier(),
        responseDTO.getData().getOrgIdentifier(), responseDTO.getData().getAccountId());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateInputSetV1() {
    doReturn(inputSetEntityV1).when(pmsInputSetService).create(any(), anyBoolean());
    doReturn(PipelineVersion.V1).when(inputSetsApiUtils).inputSetVersion(any(), any());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.createInputSet(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, inputSetYamlV1);
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYamlV1);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateInputSetV1() {
    doReturn(inputSetEntityV1).when(pmsInputSetService).update(any(), any(), anyBoolean());
    doReturn(PipelineVersion.V1).when(inputSetsApiUtils).inputSetVersion(any(), any());
    ResponseDTO<InputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.updateInputSet(null, INPUT_SET_ID,
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, inputSetYamlV1);
    assertEquals(responseDTO.getData().getInputSetYaml(), inputSetYamlV1);
    assertEquals(responseDTO.getData().getAccountId(), inputSetEntity.getAccountIdentifier());
    assertEquals(responseDTO.getData().getOrgIdentifier(), inputSetEntity.getOrgIdentifier());
    assertEquals(responseDTO.getData().getProjectIdentifier(), inputSetEntity.getProjectIdentifier());
    assertEquals(responseDTO.getData().getName(), inputSetEntity.getName());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testDeleteInputSetV1() {
    doReturn(true)
        .when(pmsInputSetService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, INPUT_SET_ID, null);
    ResponseDTO<Boolean> responseDTO = inputSetResourcePMSImpl.delete(
        null, INPUT_SET_ID, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertTrue(responseDTO.getData());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMoveConfig() {
    InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO =
        InputSetMoveConfigOperationDTO.builder()
            .moveConfigOperationType(MoveConfigOperationType.INLINE_TO_REMOTE)
            .build();

    doReturn(inputSetEntity)
        .when(pmsInputSetService)
        .moveConfig(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INPUT_SET_ID, inputSetMoveConfigOperationDTO);

    InputSetMoveConfigRequestDTO inputSetMoveConfigRequestDTO =
        InputSetMoveConfigRequestDTO.builder()
            .inputSetIdentifier(INPUT_SET_ID)
            .moveConfigOperationType(io.harness.gitaware.helper.MoveConfigOperationType.INLINE_TO_REMOTE)
            .isNewBranch(false)
            .build();

    ResponseDTO<InputSetMoveConfigResponseDTO> movedInputSet = inputSetResourcePMSImpl.moveConfig(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, INPUT_SET_ID, inputSetMoveConfigRequestDTO);

    assertEquals(movedInputSet.getData().getIdentifier(), INPUT_SET_ID);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testInputSetRepoListing() {
    List<String> repos = new ArrayList<>();
    repos.add("testRepo");
    repos.add("testRepo2");

    PMSInputSetListRepoResponse repoResponse = PMSInputSetListRepoResponse.builder().repositories(repos).build();
    doReturn(repoResponse)
        .when(pmsInputSetService)
        .getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER);

    PMSInputSetListRepoResponse pmsPipelineListRepoResponse =
        pmsInputSetService.getListOfRepos(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER);
    assertEquals(pmsPipelineListRepoResponse, repoResponse);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetMergeInputSetForRerun() {
    doReturn("mergedYaml")
        .when(executionService)
        .mergeRuntimeInputIntoPipelineForRerun(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "plan", "", "", Collections.emptyList());
    ResponseDTO<MergeInputSetResponseDTOPMS> responseDTO = inputSetResourcePMSImpl.getMergeInputSetForRerun(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, "", "", null,
        MergeInputSetForRerunRequestDTO.builder()
            .planExecutionId("plan")
            .stageIdentifiers(Collections.emptyList())
            .build());
    assertEquals("mergedYaml", responseDTO.getData().getPipelineYaml());
  }
}
