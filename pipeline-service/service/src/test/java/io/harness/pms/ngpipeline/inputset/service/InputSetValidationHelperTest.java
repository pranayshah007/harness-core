/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

@PrepareForTest({OverlayInputSetValidationHelper.class, InputSetSanitizer.class})
@OwnedBy(PIPELINE)
public class InputSetValidationHelperTest extends CategoryTest {
  @Mock PMSInputSetService inputSetService;
  @Mock PMSPipelineService pipelineService;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "Test_Pipline11";
  String pipelineYaml;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    String pipelineFile = "pipeline-extensive.yml";
    pipelineYaml = readFile(pipelineFile);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetForNonExistentPipeline() {
    doReturn(Optional.empty()).when(pipelineService).get(accountId, orgId, projectId, pipelineId, false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .build();
    assertThatThrownBy(
        () -> InputSetValidationHelper.validateInputSet(inputSetService, pipelineService, inputSetEntity, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(orgId, projectId, pipelineId));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetForInvalidStoreType() {
    doReturn(Optional.of(PipelineEntity.builder().storeType(StoreType.INLINE).build()))
        .when(pipelineService)
        .get(accountId, orgId, projectId, pipelineId, false);
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .build();
    assertThatThrownBy(
        () -> InputSetValidationHelper.validateInputSet(inputSetService, pipelineService, inputSetEntity, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Input Set should have the same Store Type as the Pipeline it is for");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetWithoutIdentifier() {
    doReturn(Optional.of(PipelineEntity.builder().storeType(StoreType.INLINE).build()))
        .when(pipelineService)
        .get(accountId, orgId, projectId, pipelineId, false);
    String yaml = "inputSet:\n"
        + "  name: abc";
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(yaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> InputSetValidationHelper.validateInputSet(null, pipelineService, inputSetEntity, false))
        .hasMessage("Identifier cannot be empty");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testForLengthCheckOnInputSetIdentifiers() {
    doReturn(Optional.of(PipelineEntity.builder().storeType(StoreType.INLINE).build()))
        .when(pipelineService)
        .get(accountId, orgId, projectId, pipelineId, false);
    String yaml = "inputSet:\n"
        + "  identifier: abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghij";
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(yaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> InputSetValidationHelper.validateInputSet(null, pipelineService, inputSetEntity, false))
        .hasMessage("Input Set identifier length cannot be more that 63 characters.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetWithNoOrgAndProjectID() {
    doReturn(Optional.of(PipelineEntity.builder().storeType(StoreType.INLINE).build()))
        .when(pipelineService)
        .get(accountId, orgId, projectId, pipelineId, false);

    String inputSetFileWithNoProjOrOrg = "inputSet1.yml";
    String inputSetYamlWithNoProjOrOrg = readFile(inputSetFileWithNoProjOrOrg);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYamlWithNoProjOrOrg)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(
        () -> InputSetValidationHelper.validateInputSet(inputSetService, pipelineService, inputSetEntity, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Organization identifier is missing in the YAML. Please give a valid Organization identifier");

    String inputSetFileWithNoProj = "inputset1-with-org-id.yaml";
    String inputSetYamlWithNoProj = readFile(inputSetFileWithNoProj);
    InputSetEntity inputSetEntity1 = InputSetEntity.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .pipelineIdentifier(pipelineId)
                                         .yaml(inputSetYamlWithNoProj)
                                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                         .build();
    assertThatThrownBy(
        () -> InputSetValidationHelper.validateInputSet(inputSetService, pipelineService, inputSetEntity1, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Project identifier is missing in the YAML. Please give a valid Project identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetWithNoErrors() {
    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).build());
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.REMOTE).build();
    doReturn(Optional.of(pipelineEntity)).when(pipelineService).get(accountId, orgId, projectId, pipelineId, false);

    String inputSetFile = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, pipelineService, inputSetEntity, true);

    setupGitContext(GitEntityInfo.builder().storeType(StoreType.REMOTE).isNewBranch(true).baseBranch("br").build());
    // no exception should be thrown
    InputSetValidationHelper.validateInputSet(inputSetService, pipelineService, inputSetEntity, true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetWithErrors() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.INLINE).build();
    doReturn(Optional.of(pipelineEntity)).when(pipelineService).get(accountId, orgId, projectId, pipelineId, false);

    String inputSetFile = "inputSetWrong1.yml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(
        () -> InputSetValidationHelper.validateInputSet(inputSetService, pipelineService, inputSetEntity, true))
        .isInstanceOf(InvalidInputSetException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetForOldGitSync() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).build();
    doReturn(Optional.of(pipelineEntity)).when(pipelineService).get(accountId, orgId, projectId, pipelineId, false);

    String inputSetFile = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();

    // no exception should be thrown here
    InputSetValidationHelper.validateInputSetForOldGitSync(inputSetService, pipelineService, inputSetEntity, "", "");
    InputSetValidationHelper.validateInputSetForOldGitSync(
        inputSetService, pipelineService, inputSetEntity, "branch", "repo");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetForOldGitSyncWithErrors() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).storeType(StoreType.INLINE).build();
    doReturn(Optional.of(pipelineEntity)).when(pipelineService).get(accountId, orgId, projectId, pipelineId, false);

    String inputSetFile = "inputSetWrong1.yml";
    String inputSetYaml = readFile(inputSetFile);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(()
                           -> InputSetValidationHelper.validateInputSetForOldGitSync(
                               inputSetService, pipelineService, inputSetEntity, "", ""))
        .isInstanceOf(InvalidInputSetException.class);
    assertThatThrownBy(()
                           -> InputSetValidationHelper.validateInputSetForOldGitSync(
                               inputSetService, pipelineService, inputSetEntity, "branch", "repo"))
        .isInstanceOf(InvalidInputSetException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSetForOldGitSyncWithNonExistentPipeline() {
    doReturn(Optional.empty()).when(pipelineService).get(accountId, orgId, projectId, pipelineId, false);
    String inputSetYaml = "anything";
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(inputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.INPUT_SET)
                                        .build();
    assertThatThrownBy(()
                           -> InputSetValidationHelper.validateInputSetForOldGitSync(
                               inputSetService, pipelineService, inputSetEntity, "", ""))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForNonExistentInputSet() {
    doReturn(Optional.empty())
        .when(inputSetService)
        .getWithoutValidations(accountId, orgId, projectId, pipelineId, "inputSetId", false);
    assertThatThrownBy(()
                           -> InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, inputSetService, null, null,
                               accountId, orgId, projectId, pipelineId, "inputSetId", null, null))
        .hasMessageContaining("does not exist or has been deleted")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForOverlayInputSet() {
    MockedStatic<OverlayInputSetValidationHelper> mockSettings =
        Mockito.mockStatic(OverlayInputSetValidationHelper.class);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(Optional.of(PipelineEntity.builder().yaml("pipeline: yaml").build()))
        .when(pipelineService)
        .get(accountId, orgId, projectId, pipelineId, false);
    InputSetEntity overlayEntity =
        InputSetEntity.builder().inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET).build();
    doReturn(Optional.of(overlayEntity))
        .when(inputSetService)
        .getWithoutValidations(accountId, orgId, projectId, pipelineId, "inputSetId", false);
    when(OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
             gitSyncSdkService, inputSetService, overlayEntity, "pipeline: yaml"))
        .thenReturn(InputSetYamlDiffDTO.builder().oldYAML("old: yaml").newYAML("new: yaml").build());
    InputSetYamlDiffDTO yamlDiffDTO = InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, inputSetService,
        pipelineService, null, accountId, orgId, projectId, pipelineId, "inputSetId", null, null);
    assertThat(yamlDiffDTO.getOldYAML()).isEqualTo("old: yaml");
    assertThat(yamlDiffDTO.getNewYAML()).isEqualTo("new: yaml");
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForRemoteOverlayInputSet() {
    GitAwareContextHelper.updateScmGitMetaData(ScmGitMetaData.builder().branchName("thisBranch").build());

    MockedStatic<OverlayInputSetValidationHelper> mockSettings =
        Mockito.mockStatic(OverlayInputSetValidationHelper.class);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(Optional.of(PipelineEntity.builder().yaml("pipeline: yaml").build()))
        .when(pipelineService)
        .get(accountId, orgId, projectId, pipelineId, false);
    InputSetEntity overlayEntity = InputSetEntity.builder()
                                       .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                       .storeType(StoreType.REMOTE)
                                       .build();
    doReturn(Optional.of(overlayEntity))
        .when(inputSetService)
        .getWithoutValidations(accountId, orgId, projectId, pipelineId, "inputSetId", false);
    when(OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
             gitSyncSdkService, inputSetService, overlayEntity, "pipeline: yaml"))
        .thenReturn(InputSetYamlDiffDTO.builder().oldYAML("old: yaml").newYAML("new: yaml").build());
    InputSetYamlDiffDTO yamlDiffDTO = InputSetValidationHelper.getYAMLDiff(gitSyncSdkService, inputSetService,
        pipelineService, null, accountId, orgId, projectId, pipelineId, "inputSetId", null, null);
    assertThat(yamlDiffDTO.getOldYAML()).isEqualTo("old: yaml");
    assertThat(yamlDiffDTO.getNewYAML()).isEqualTo("new: yaml");
    assertThat(yamlDiffDTO.getGitDetails().getBranch()).isEqualTo("thisBranch");
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForInputSet() {
    MockedStatic<InputSetSanitizer> mockSettings = Mockito.mockStatic(InputSetSanitizer.class);
    when(InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML("pipeline: yaml", "input: set"))
        .thenReturn("input: setNew");
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml("input: set")
                                        .build();
    InputSetYamlDiffDTO yamlDiffForInputSet =
        InputSetValidationHelper.getYAMLDiffForInputSet(null, inputSetEntity, null, null, "pipeline: yaml");
    assertThat(yamlDiffForInputSet.getOldYAML()).isEqualTo("input: set");
    assertThat(yamlDiffForInputSet.getNewYAML()).isEqualTo("input: setNew");
    assertThat(yamlDiffForInputSet.isInputSetEmpty()).isEqualTo(false);
    assertThat(yamlDiffForInputSet.isNoUpdatePossible()).isEqualTo(false);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForInputSetWithNoUpdatePossible() {
    MockedStatic<InputSetSanitizer> mockSettings = Mockito.mockStatic(InputSetSanitizer.class);
    when(InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML("pipeline: yaml", "input: set")).thenReturn(null);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml("input: set")
                                        .build();
    InputSetYamlDiffDTO yamlDiffForInputSet = InputSetValidationHelper.getYAMLDiffForInputSet(
        validateAndMergeHelper, inputSetEntity, null, null, "pipeline: yaml");
    assertThat(yamlDiffForInputSet.isInputSetEmpty()).isEqualTo(true);
    assertThat(yamlDiffForInputSet.isNoUpdatePossible()).isEqualTo(true);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffForInputSetWithUpdatePossible() {
    MockedStatic<InputSetSanitizer> mockSettings = Mockito.mockStatic(InputSetSanitizer.class);
    when(InputSetSanitizer.sanitizeInputSetAndUpdateInputSetYAML("pipeline: yaml", "input: set")).thenReturn(null);
    doReturn("new: template")
        .when(validateAndMergeHelper)
        .getPipelineTemplate(accountId, orgId, projectId, pipelineId, null, null, null);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml("input: set")
                                        .build();
    InputSetYamlDiffDTO yamlDiffForInputSet = InputSetValidationHelper.getYAMLDiffForInputSet(
        validateAndMergeHelper, inputSetEntity, null, null, "pipeline: yaml");
    assertThat(yamlDiffForInputSet.isInputSetEmpty()).isEqualTo(true);
    assertThat(yamlDiffForInputSet.isNoUpdatePossible()).isEqualTo(false);
    mockSettings.close();
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}
