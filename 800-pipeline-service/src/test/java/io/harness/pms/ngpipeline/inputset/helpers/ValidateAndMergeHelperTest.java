/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType.INPUT_SET;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.VED;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class ValidateAndMergeHelperTest extends PipelineServiceTestBase {
  @InjectMocks ValidateAndMergeHelper validateAndMergeHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSInputSetService pmsInputSetService;
  @Mock GitSyncSdkService gitSyncSdkService;

  private static final String accountId = "accountId";
  private static final String orgId = "orgId";
  private static final String projectId = "projectId";
  private static final String pipelineId = "Test_Pipline11";
  private static final String branch = null;
  private static final String repoId = null;

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateInputSet() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    String pipelineFile = "pipeline-extensive.yml";
    String pipelineYaml = readFile(pipelineFile);
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).build();
    String inputSetFileWithNoProjOrOrg = "inputSet1.yml";
    String inputSetYamlWithNoProjOrOrg = readFile(inputSetFileWithNoProjOrOrg);

    String wrongPipelineId = "wrongPipeline";
    assertThatThrownBy(()
                           -> validateAndMergeHelper.validateInputSet(accountId, orgId, projectId, wrongPipelineId,
                               inputSetYamlWithNoProjOrOrg, branch, repoId))
        .hasMessage("Pipeline identifier in input set does not match");

    assertThatThrownBy(()
                           -> validateAndMergeHelper.validateInputSet(
                               accountId, orgId, projectId, pipelineId, inputSetYamlWithNoProjOrOrg, branch, repoId))
        .hasMessage("Organization identifier is missing in the YAML. Please give a valid Organization identifier");

    String inputSetFileWithNoProj = "inputset1-with-org-id.yaml";
    String inputSetYamlWithNoProj = readFile(inputSetFileWithNoProj);
    assertThatThrownBy(()
                           -> validateAndMergeHelper.validateInputSet(
                               accountId, orgId, projectId, pipelineId, inputSetYamlWithNoProj, branch, repoId))
        .hasMessage("Project identifier is missing in the YAML. Please give a valid Project identifier");

    String inputSetFile = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml = readFile(inputSetFile);
    doReturn(Optional.of(pipelineEntity)).when(pmsPipelineService).get(accountId, orgId, projectId, pipelineId, false);
    InputSetErrorWrapperDTOPMS nullDto =
        validateAndMergeHelper.validateInputSet(accountId, orgId, projectId, pipelineId, inputSetYaml, branch, repoId);
    assertThat(nullDto).isNull();
    verify(pmsPipelineService, times(1)).get(accountId, orgId, projectId, pipelineId, false);

    String inputSetFileWrong = "inputSetWrong1.yml";
    String inputSetYamlWrong = readFile(inputSetFileWrong);
    InputSetErrorWrapperDTOPMS nonNullDto = validateAndMergeHelper.validateInputSet(
        accountId, orgId, projectId, pipelineId, inputSetYamlWrong, branch, repoId);
    assertThat(nonNullDto).isNotNull();
    assertThat(nonNullDto.getUuidToErrorResponseMap().size()).isEqualTo(1);
    assertThat(nonNullDto.getUuidToErrorResponseMap().containsKey(
                   "pipeline.stages.qaStage.spec.execution.steps.httpStep1.spec.method"))
        .isTrue();
    verify(pmsPipelineService, times(2)).get(accountId, orgId, projectId, pipelineId, false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateOverlayInputSet() {
    String overlayInputSetYamlWithoutReferences = getOverlayInputSetWithAllIds(false);
    assertThatThrownBy(()
                           -> validateAndMergeHelper.validateOverlayInputSet(
                               accountId, orgId, projectId, pipelineId, overlayInputSetYamlWithoutReferences))
        .hasMessage("Input Set References can't be empty");

    String overlayInputSetYamlWithoutOrgId = getOverlayInputSetYaml(true, false, true, true, false);
    assertThatThrownBy(()
                           -> validateAndMergeHelper.validateOverlayInputSet(
                               accountId, orgId, projectId, pipelineId, overlayInputSetYamlWithoutOrgId))
        .hasMessage("Organization identifier is missing in the YAML. Please give a valid Organization identifier");

    String overlayInputSetYamlWithoutProjectId = getOverlayInputSetYaml(true, true, false, true, false);
    assertThatThrownBy(()
                           -> validateAndMergeHelper.validateOverlayInputSet(
                               accountId, orgId, projectId, pipelineId, overlayInputSetYamlWithoutProjectId))
        .hasMessage("Project identifier is missing in the YAML. Please give a valid Project identifier");

    String overlayInputSetYamlWithoutPipelineId = getOverlayInputSetYaml(true, true, true, false, false);
    assertThatThrownBy(()
                           -> validateAndMergeHelper.validateOverlayInputSet(
                               accountId, orgId, projectId, pipelineId, overlayInputSetYamlWithoutPipelineId))
        .hasMessage("Pipeline identifier is missing in the YAML. Please give a valid Pipeline identifier");

    String inputSetFile1 = "inputset1-with-org-proj-id.yaml";
    String inputSetYaml1 = readFile(inputSetFile1);
    String identifier1 = "input1";
    InputSetEntity inputSetEntity1 = InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml1).build();
    doReturn(Optional.of(inputSetEntity1))
        .when(pmsInputSetService)
        .get(accountId, orgId, projectId, pipelineId, identifier1, false);

    String inputSetFile2 = "inputSetWrong1.yml";
    String inputSetYaml2 = readFile(inputSetFile2);
    String identifier2 = "thisInputSetIsWrong";
    InputSetEntity inputSetEntity2 = InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml2).build();
    doReturn(Optional.of(inputSetEntity2))
        .when(pmsInputSetService)
        .get(accountId, orgId, projectId, pipelineId, identifier2, false);

    String overlayInputSetYaml = getOverlayInputSetWithAllIds(true);
    Map<String, String> noInvalidReferences =
        validateAndMergeHelper.validateOverlayInputSet(accountId, orgId, projectId, pipelineId, overlayInputSetYaml);
    assertThat(noInvalidReferences.size()).isEqualTo(0);
    verify(pmsInputSetService, times(1)).get(accountId, orgId, projectId, pipelineId, identifier1, false);
    verify(pmsInputSetService, times(1)).get(accountId, orgId, projectId, pipelineId, identifier2, false);

    InputSetEntity inputSetEntityInvalid =
        InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml2).isInvalid(true).build();
    doReturn(Optional.of(inputSetEntityInvalid))
        .when(pmsInputSetService)
        .get(accountId, orgId, projectId, pipelineId, identifier2, false);
    Map<String, String> oneInvalidReference =
        validateAndMergeHelper.validateOverlayInputSet(accountId, orgId, projectId, pipelineId, overlayInputSetYaml);
    assertThat(oneInvalidReference.size()).isEqualTo(1);
    verify(pmsInputSetService, times(2)).get(accountId, orgId, projectId, pipelineId, identifier1, false);
    verify(pmsInputSetService, times(2)).get(accountId, orgId, projectId, pipelineId, identifier2, false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateNonExistentReferencesInOverlayInputSet() {
    String nonExistentReference = "doesNotExist";
    String overlayYaml = getOverlayInputSetWithNonExistentReference();
    doReturn(Optional.empty())
        .when(pmsInputSetService)
        .get(accountId, orgId, projectId, pipelineId, nonExistentReference, false);
    Map<String, String> nonExistentReferenceMap =
        validateAndMergeHelper.validateOverlayInputSet(accountId, orgId, projectId, pipelineId, overlayYaml);
    assertThat(nonExistentReferenceMap).hasSize(1);
    assertThat(nonExistentReferenceMap.get(nonExistentReference)).isEqualTo("Reference does not exist");
    verify(pmsInputSetService, times(1)).get(accountId, orgId, projectId, pipelineId, nonExistentReference, false);
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

    assertThatThrownBy(()
                           -> validateAndMergeHelper.validateOverlayInputSet(
                               accountId, orgId, projectId, pipelineId, emptyReferencesOverlay))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Empty Input Set Identifier not allowed in Input Set References");
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

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineTemplate() {
    String pipelineStart = "pipeline:\n"
        + "  stages:\n";
    String stage1 = "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "      myField: \"<+input>\"\n";
    String stage2 = "  - stage:\n"
        + "      identifier: \"s2\"\n"
        + "      myField: \"<+input>\"\n";
    String pipelineYaml = pipelineStart + stage1 + stage2;

    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).build();
    doReturn(Optional.of(pipelineEntity)).when(pmsPipelineService).get(accountId, orgId, projectId, pipelineId, false);
    String pipelineTemplate = validateAndMergeHelper.getPipelineTemplate(accountId, orgId, projectId, pipelineId, null);
    assertThat(pipelineTemplate).isEqualTo(pipelineYaml);

    String s1Template = validateAndMergeHelper.getPipelineTemplate(
        accountId, orgId, projectId, pipelineId, Collections.singletonList("s1"));
    assertThat(s1Template).isEqualTo(pipelineStart + stage1);

    doReturn(Optional.empty()).when(pmsPipelineService).get(accountId, orgId, projectId, pipelineId, false);
    assertThatThrownBy(() -> validateAndMergeHelper.getPipelineTemplate(accountId, orgId, projectId, pipelineId, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(format("Pipeline [%s] under Project[%s], Organization [%s] doesn't exist or has been deleted.",
            pipelineId, projectId, orgId));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeInputSetForInvalidInputSets() {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, orgId, projectId);
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      description: <+input>\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      description: <+input>\n";
    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(pipelineYaml).build();
    doReturn(Optional.of(pipelineEntity)).when(pmsPipelineService).get(accountId, orgId, projectId, pipelineId, false);

    String invalidIdentifier = "invalidIdentifier";
    InputSetEntity invalidEntity = InputSetEntity.builder().isInvalid(true).identifier(invalidIdentifier).build();
    doReturn(Optional.of(invalidEntity))
        .when(pmsInputSetService)
        .get(accountId, orgId, projectId, pipelineId, invalidIdentifier, false);

    String validIdentifier = "validIdentifier";
    InputSetEntity validEntity = InputSetEntity.builder().isInvalid(false).identifier(validIdentifier).build();
    doReturn(Optional.of(validEntity))
        .when(pmsInputSetService)
        .get(accountId, orgId, projectId, pipelineId, validIdentifier, false);

    assertThatThrownBy(()
                           -> validateAndMergeHelper.getMergeInputSetFromPipelineTemplate(accountId, orgId, projectId,
                               pipelineId, Arrays.asList(invalidIdentifier, validIdentifier), branch, repoId, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("invalidIdentifier is invalid. Pipeline update has made this input set outdated");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testForLengthCheckOnInputSetIdentifiers() {
    String yaml1 = getInputSetYamlWithLongIdentifier();
    String yaml2 = addOrgIdentifier(yaml1);
    String yaml3 = addProjectIdentifier(yaml2);
    String yaml4 = addPipelineIdentifier(yaml3);
    assertThatThrownBy(
        () -> validateAndMergeHelper.validateInputSet(accountId, orgId, projectId, pipelineId, yaml4, branch, repoId))
        .hasMessage("Input Set identifier length cannot be more that 63 characters.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithNoRuntime() {
    doReturn(Optional.empty()).when(pmsPipelineService).get(accountId, orgId, projectId, pipelineId, false);
    assertThatThrownBy(
        () -> validateAndMergeHelper.getInputSetTemplateResponseDTO(accountId, orgId, projectId, pipelineId, null))
        .isInstanceOf(InvalidRequestException.class);
    String pipelineYamlWithNoRuntime = getPipelineYamlWithNoRuntime();
    PipelineEntity pipelineEntityWithNoRuntime =
        PipelineEntity.builder().yaml(pipelineYamlWithNoRuntime).filters(Collections.singletonMap("pms", null)).build();
    doReturn(Optional.of(pipelineEntityWithNoRuntime))
        .when(pmsPipelineService)
        .get(accountId, orgId, projectId, "no_runtime", false);
    doReturn(false).when(pmsInputSetService).checkForInputSetsForPipeline(accountId, orgId, projectId, "no_runtime");
    InputSetTemplateResponseDTOPMS responseWithNoRuntime =
        validateAndMergeHelper.getInputSetTemplateResponseDTO(accountId, orgId, projectId, "no_runtime", null);
    assertThat(responseWithNoRuntime.getHasInputSets()).isFalse();
    assertThat(responseWithNoRuntime.getModules()).containsExactly("pms");
    assertThat(responseWithNoRuntime.getReplacedExpressions()).isNull();
    assertThat(responseWithNoRuntime.getInputSetTemplateYaml()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInputSetTemplateResponseDTOWithRuntime() {
    String pipelineYamlWithRuntime = getPipelineYamlWithRuntime();
    PipelineEntity pipelineEntityWithRuntime =
        PipelineEntity.builder().yaml(pipelineYamlWithRuntime).filters(Collections.singletonMap("pms", null)).build();
    doReturn(Optional.of(pipelineEntityWithRuntime))
        .when(pmsPipelineService)
        .get(accountId, orgId, projectId, "has_runtime", false);
    doReturn(true).when(pmsInputSetService).checkForInputSetsForPipeline(accountId, orgId, projectId, "has_runtime");
    InputSetTemplateResponseDTOPMS responseWithNoRuntime =
        validateAndMergeHelper.getInputSetTemplateResponseDTO(accountId, orgId, projectId, "has_runtime", null);
    assertThat(responseWithNoRuntime.getHasInputSets()).isTrue();
    assertThat(responseWithNoRuntime.getModules()).containsExactly("pms");
    assertThat(responseWithNoRuntime.getReplacedExpressions()).isNull();
    assertThat(responseWithNoRuntime.getInputSetTemplateYaml()).isEqualTo(getRuntimeTemplate());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testForLengthCheckOnOverlayInputSetIdentifiers() {
    String yaml1 = getOverlayInputSetYamlWithLongIdentifier();
    String yaml2 = addOrgIdentifier(yaml1);
    String yaml3 = addProjectIdentifier(yaml2);
    String yaml4 = addPipelineIdentifier(yaml3);
    assertThatThrownBy(
        () -> validateAndMergeHelper.validateOverlayInputSet(accountId, orgId, projectId, pipelineId, yaml4))
        .hasMessage("Overlay Input Set identifier length cannot be more that 63 characters.");
  }

  private String getInputSetYamlWithLongIdentifier() {
    String base = "inputSet:\n"
        + "  name: n1\n"
        + "  identifier: abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghij\n";
    String pipelineComponent = "  pipeline:\n"
        + "    name: n2\n"
        + "    identifier: n2\n";
    return base + pipelineComponent;
  }

  private String getOverlayInputSetYamlWithLongIdentifier() {
    String base = "overlayInputSet:\n"
        + "  name: n1\n"
        + "  identifier: abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghij\n";
    String references = "  inputSetReferences:\n"
        + "    - s1\n"
        + "    - s2\n";
    return base + references;
  }

  private String addOrgIdentifier(String yaml) {
    String orgId = "  orgIdentifier: o1\n";
    return yaml + orgId;
  }

  private String addProjectIdentifier(String yaml) {
    String projectId = "  projectIdentifier: p1\n";
    return yaml + projectId;
  }

  private String addPipelineIdentifier(String yaml) {
    String pipelineId = "  pipelineIdentifier: n2\n";
    return yaml + pipelineId;
  }

  private String getPipelineYamlWithNoRuntime() {
    return "pipeline:\n"
        + "  name: no runtime\n"
        + "  identifier: no_runtime\n"
        + "  projectIdentifier: namantest\n"
        + "  orgIdentifier: default\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      name: a1\n"
        + "      identifier: a1\n"
        + "      type: Approval\n"
        + "      spec:\n"
        + "        execution:\n"
        + "          steps:\n"
        + "          - step:\n"
        + "              name: Approval\n"
        + "              identifier: approval\n"
        + "              type: HarnessApproval\n"
        + "              timeout: 1d\n"
        + "              spec:\n"
        + "                approvalMessage: Please review\n"
        + "                includePipelineExecutionHistory: true\n"
        + "                approvers:\n"
        + "                  minimumCount: 1\n"
        + "                  disallowPipelineExecutor: false\n"
        + "                  userGroups:\n"
        + "                  - account.Dashboards\n";
  }

  private String getPipelineYamlWithRuntime() {
    return "pipeline:\n"
        + "  name: has runtime\n"
        + "  identifier: has_runtime\n"
        + "  projectIdentifier: namantest\n"
        + "  orgIdentifier: default\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      name: a1\n"
        + "      identifier: a1\n"
        + "      type: Approval\n"
        + "      spec:\n"
        + "        execution:\n"
        + "          steps:\n"
        + "          - step:\n"
        + "              name: Approval\n"
        + "              identifier: approval\n"
        + "              type: HarnessApproval\n"
        + "              timeout: 1d\n"
        + "              spec:\n"
        + "                approvalMessage: <+input>\n"
        + "                includePipelineExecutionHistory: true\n"
        + "                approvers:\n"
        + "                  minimumCount: 1\n"
        + "                  disallowPipelineExecutor: false\n"
        + "                  userGroups:\n"
        + "                  - account.Dashboards\n";
  }

  private String getRuntimeTemplate() {
    return "pipeline:\n"
        + "  identifier: \"has_runtime\"\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"a1\"\n"
        + "      type: \"Approval\"\n"
        + "      spec:\n"
        + "        execution:\n"
        + "          steps:\n"
        + "          - step:\n"
        + "              identifier: \"approval\"\n"
        + "              type: \"HarnessApproval\"\n"
        + "              spec:\n"
        + "                approvalMessage: \"<+input>\"\n";
  }
}
