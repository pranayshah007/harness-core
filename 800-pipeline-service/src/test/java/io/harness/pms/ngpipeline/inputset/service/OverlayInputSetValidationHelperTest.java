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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.exceptions.InvalidOverlayInputSetException;
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
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class OverlayInputSetValidationHelperTest extends CategoryTest {
  @Mock PMSInputSetService inputSetService;

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
        .get(accountId, orgId, projectId, pipelineId, identifier1, false);

    String inputSetFile2 = "inputSetWrong1.yml";
    String inputSetYaml2 = readFile(inputSetFile2);
    String identifier2 = "thisInputSetIsWrong";
    InputSetEntity inputSetEntity2 = InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml2).build();
    doReturn(Optional.of(inputSetEntity2))
        .when(inputSetService)
        .get(accountId, orgId, projectId, pipelineId, identifier2, false);

    String overlayInputSetYaml = getOverlayInputSetWithAllIds(true);
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .pipelineIdentifier(pipelineId)
                                        .yaml(overlayInputSetYaml)
                                        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
                                        .build();
    OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity);

    InputSetEntity inputSetEntityInvalid =
        InputSetEntity.builder().inputSetEntityType(INPUT_SET).yaml(inputSetYaml2).isInvalid(true).build();
    doReturn(Optional.of(inputSetEntityInvalid))
        .when(inputSetService)
        .get(accountId, orgId, projectId, pipelineId, identifier2, false);
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity))
        .isInstanceOf(InvalidOverlayInputSetException.class);
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
        .get(accountId, orgId, projectId, pipelineId, nonExistentReference, false);
    assertThatThrownBy(() -> OverlayInputSetValidationHelper.validateOverlayInputSet(inputSetService, inputSetEntity))
        .isInstanceOf(InvalidOverlayInputSetException.class);
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
}
