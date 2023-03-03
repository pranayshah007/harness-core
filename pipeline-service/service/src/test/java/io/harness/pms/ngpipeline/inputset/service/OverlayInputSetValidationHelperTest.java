/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType.INPUT_SET;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidOverlayInputSetException;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.data.mongodb.core.query.Criteria;

@PrepareForTest({InputSetErrorsHelper.class, PMSInputSetFilterHelper.class})
@OwnedBy(PIPELINE)
public class OverlayInputSetValidationHelperTest extends CategoryTest {
  @Mock PMSInputSetService inputSetService;
  @Mock GitSyncSdkService gitSyncSdkService;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "Test_Pipline11";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetWithNoReferences() {
    String overlayInputSetYamlWithoutReferences = getOverlayInputSetWithAllIds(false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYamlWithoutReferences)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity))
        .hasMessage("Input Set References can't be empty");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetWithoutOrgID() {
    String overlayInputSetYamlWithoutOrgId = getOverlayInputSetYaml(true, false, true, true, false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYamlWithoutOrgId)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity))
        .hasMessage("Organization identifier is missing in the YAML. Please give a valid Organization identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetWithoutProjectID() {
    String overlayInputSetYamlWithoutProjectId = getOverlayInputSetYaml(true, true, false, true, false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYamlWithoutProjectId)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity))
        .hasMessage("Project identifier is missing in the YAML. Please give a valid Project identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetWithoutPipelineID() {
    String overlayInputSetYamlWithoutPipelineId = getOverlayInputSetYaml(true, true, true, false, false);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYamlWithoutPipelineId)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity))
        .hasMessage("Pipeline identifier is missing in the YAML. Please give a valid Pipeline identifier");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSet() {
    String inputSetFile1 = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml1 = readFile(inputSetFile1);
    String identifier1 = "input1";
    InputSetEntity inputSetEntity1 = InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml1).build();
    doReturn(Optional.of(inputSetEntity1))
        .when(inputSetService)
        .getMetadataWithoutValidations(accountId, orgId, projectId, pipelineId, identifier1, false, false, true);

    String inputSetFile2 = "inputSetWrong1.yml";
    String inputSetYaml2 = readFile(inputSetFile2);
    String identifier2 = "thisInputSetIsWrong";
    InputSetEntity inputSetEntity2 = InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml2).build();
    doReturn(Optional.of(inputSetEntity2))
        .when(inputSetService)
        .getMetadataWithoutValidations(accountId, orgId, projectId, pipelineId, identifier2, false, false, true);

    String overlayInputSetYaml = getOverlayInputSetWithAllIds(true);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();
    MockedStatic<InputSetErrorsHelper> mockSettings = Mockito.mockStatic(InputSetErrorsHelper.class);
    when(InputSetErrorsHelper.getInvalidInputSetReferences(any(), any(), any())).thenCallRealMethod();
    when(InputSetErrorsHelper.getErrorMap(any(), any(), any())).thenReturn(null);
    OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateNonExistentReferencesInOverlayInputSet() {
    String nonExistentReference = "doesNotExist";
    String overlayYaml = getOverlayInputSetWithNonExistentReference();
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayYaml)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();
    doReturn(Optional.empty())
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, nonExistentReference, false, false, true);
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity))
        .isInstanceOf(InvalidOverlayInputSetException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateEmptyReferencesInOverlayInputSet() {
    String emptyReferencesOverlay = "overlayInputSet:\n"
        + "  identifier: a\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projectId\n"
        + "  pipelineIdentifier: Test_Pipline11\n"
        + "  inputSetReferences:\n"
        + "    - \"\"\n"
        + "    - \"\"";

    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(emptyReferencesOverlay)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();

    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty Input Set Identifier not allowed in Input Set References");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testForLengthCheckOnOverlayInputSetIdentifiers() {
    String yaml = "overlayInputSet:\n"
        + "  identifier: abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijdsalksdajsdnanfnoaniondna12213123034r78978987879897jkkljasad";
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(yaml)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(null, inputSetEntity))
        .hasMessage("Overlay Input Set identifier length cannot be more that 127 characters.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffWithOneInvalidReference() {
    String yaml = getOverlayInputSetWithAllIds(true);
    doReturn(Optional.of(InputSetEntity.builder().inputSetEntityType(INPUT_SET).isInvalid(false).build()))
        .when(inputSetService)
        .getMetadataWithoutValidations(accountId, orgId, projectId, pipelineId, "input1", false, false, true);
    doReturn(Optional.empty())
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, "thisInputSetIsWrong", false, false, true);

    MockedStatic<InputSetErrorsHelper> mockSettings = Mockito.mockStatic(InputSetErrorsHelper.class);
    when(InputSetErrorsHelper.getInvalidInputSetReferences(any(), any(), any())).thenCallRealMethod();
    when(InputSetErrorsHelper.getErrorMap("randomPipelineYaml", null, null)).thenReturn(null);
    InputSetYamlDiffDTO yamlDiffForOverlayInputSet =
        OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(null, inputSetService,
            InputSetEntity.builder()
                .accountId(accountId)
                .orgIdentifier(orgId)
                .projectIdentifier(projectId)
                .pipelineIdentifier(pipelineId)
                .yaml(yaml)
                .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                .build(),
            "randomPipelineYaml");
    assertThat(yamlDiffForOverlayInputSet.getOldYAML()).isEqualTo(yaml);
    List<String> newReferences =
        InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yamlDiffForOverlayInputSet.getNewYAML());
    assertThat(newReferences).containsExactly("input1");
    assertThat(yamlDiffForOverlayInputSet.isInputSetEmpty()).isFalse();
    assertThat(yamlDiffForOverlayInputSet.isNoUpdatePossible()).isFalse();
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYAMLDiffWithAllInvalidReferences() {
    String yaml = getOverlayInputSetWithAllIds(true);
    doReturn(Optional.empty())
        .when(inputSetService)
        .getMetadataWithoutValidations(accountId, orgId, projectId, pipelineId, "input1", false, false, true);
    doReturn(Optional.empty())
        .when(inputSetService)
        .getMetadataWithoutValidations(
            accountId, orgId, projectId, pipelineId, "thisInputSetIsWrong", false, false, true);
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    doReturn(Collections.emptyList()).when(inputSetService).list(any());
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(yaml)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();

    InputSetYamlDiffDTO yamlDiffForOverlayInputSet = OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
        gitSyncSdkService, inputSetService, inputSetEntity, null);
    assertThat(yamlDiffForOverlayInputSet.isInputSetEmpty()).isTrue();
    assertThat(yamlDiffForOverlayInputSet.isNoUpdatePossible()).isTrue();

    doReturn(Collections.singletonList(inputSetEntity)).when(inputSetService).list(any());
    yamlDiffForOverlayInputSet = OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
        gitSyncSdkService, inputSetService, inputSetEntity, null);
    assertThat(yamlDiffForOverlayInputSet.isInputSetEmpty()).isTrue();
    assertThat(yamlDiffForOverlayInputSet.isNoUpdatePossible()).isFalse();

    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    setupGitContext(GitEntityInfo.builder().branch("random").yamlGitConfigId("random").build());
    yamlDiffForOverlayInputSet = OverlayInputSetValidationHelper.getYAMLDiffForOverlayInputSet(
        gitSyncSdkService, inputSetService, inputSetEntity, null);
    assertThat(yamlDiffForOverlayInputSet.isInputSetEmpty()).isTrue();
    assertThat(yamlDiffForOverlayInputSet.isNoUpdatePossible()).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSetsForGivenInputSet() {
    MockedStatic<PMSInputSetFilterHelper> mockSettings = Mockito.mockStatic(PMSInputSetFilterHelper.class);
    Criteria dummyCriteria = Criteria.where("myKey").is("myValue");
    when(PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(
             accountId, orgId, projectId, pipelineId, InputSetListTypePMS.OVERLAY_INPUT_SET))
        .thenReturn(dummyCriteria);
    String yaml1 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i1\n";
    InputSetEntity overlay1 = InputSetEntity.builder().yaml(yaml1).isInvalid(true).build();
    String yaml2 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i2\n";
    InputSetEntity overlay2 = InputSetEntity.builder().yaml(yaml2).isInvalid(true).build();
    String yaml3 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i3\n";
    InputSetEntity overlay3 = InputSetEntity.builder().yaml(yaml3).isInvalid(false).build();
    doReturn(Arrays.asList(overlay1, overlay2, overlay3)).when(inputSetService).list(dummyCriteria);

    InputSetEntity updatedInputSet = InputSetEntity.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .pipelineIdentifier(pipelineId)
                                         .identifier("i1")
                                         .storeType(StoreType.INLINE)
                                         .inputSetEntityType(INPUT_SET)
                                         .build();
    doReturn(Optional.of(updatedInputSet))
        .when(inputSetService)
        .getMetadataWithoutValidations(accountId, orgId, projectId, pipelineId, "i1", false, false, true);
    OverlayInputSetValidationHelper.validateOverlayInputSetsForGivenInputSet(inputSetService, updatedInputSet);
    verify(inputSetService, times(1)).switchValidationFlag(overlay1, false);
    verify(inputSetService, times(0)).switchValidationFlag(overlay2, false);
    verify(inputSetService, times(0)).switchValidationFlag(overlay3, false);
    mockSettings.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testInvalidateOverlayInputSetsReferringDeletedInputSet() {
    MockedStatic<PMSInputSetFilterHelper> mockSettings = Mockito.mockStatic(PMSInputSetFilterHelper.class);
    Criteria dummyCriteria = Criteria.where("myKey").is("myValue");
    when(PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(
             accountId, orgId, projectId, pipelineId, InputSetListTypePMS.OVERLAY_INPUT_SET))
        .thenReturn(dummyCriteria);

    String yaml1 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i1\n"
        + "  - i2\n";
    InputSetEntity overlay1 = InputSetEntity.builder().yaml(yaml1).isInvalid(false).build();
    String yaml2 = "overlayInputSet:\n"
        + "  inputSetReferences:\n"
        + "  - i2\n"
        + "  - i3\n";
    InputSetEntity overlay2 = InputSetEntity.builder().yaml(yaml2).isInvalid(false).build();
    InputSetEntity overlay3 = InputSetEntity.builder().yaml(yaml2).isInvalid(true).build();
    doReturn(Arrays.asList(overlay1, overlay2, overlay3)).when(inputSetService).list(dummyCriteria);
    InputSetEntity deletedInputSet = InputSetEntity.builder()
                                         .accountId(accountId)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .pipelineIdentifier(pipelineId)
                                         .identifier("i1")
                                         .storeType(StoreType.INLINE)
                                         .inputSetEntityType(INPUT_SET)
                                         .build();

    OverlayInputSetValidationHelper.invalidateOverlayInputSetsReferringDeletedInputSet(
        inputSetService, deletedInputSet);
    verify(inputSetService, times(1)).switchValidationFlag(overlay1, true);
    verify(inputSetService, times(0)).switchValidationFlag(overlay2, true);
    verify(inputSetService, times(0)).switchValidationFlag(overlay3, true);

    OverlayInputSetValidationHelper.invalidateOverlayInputSetsReferringDeletedInputSet(inputSetService, overlay1);
    verify(inputSetService, times(1)).switchValidationFlag(any(), anyBoolean());
    mockSettings.close();
  }

  private String getOverlayInputSetWithNonExistentReference() {
    return getOverlayInputSetYaml(true, true, true, true, true);
  }

  private String getOverlayInputSetWithAllIds(boolean hasReferences) {
    return getOverlayInputSetYaml(hasReferences, true, true, true, false);
  }

  private String getOverlayInputSetYaml(
      boolean hasReferences, boolean hasOrg, boolean hasProj, boolean hasPipeline, boolean nonExistentReference) {
    String base = "overlayInputSet:\n"
        + "  identifier: overlay1\n"
        + "  name : thisName\n";
    String orgId = "  orgIdentifier: orgId\n";
    String projectId = "  projectIdentifier: projectId\n";
    String pipelineId = "  pipelineIdentifier: Test_Pipline11\n";
    String references = "  inputSetReferences:\n"
        + (nonExistentReference ? "    - doesNotExist"
                                : ("    - input1\n"
                                    + "    - thisInputSetIsWrong"));
    String noReferences = "  inputSetReferences: []\n";

    return base + (hasOrg ? orgId : "") + (hasProj ? projectId : "") + (hasPipeline ? pipelineId : "")
        + (hasReferences ? references : noReferences);
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
