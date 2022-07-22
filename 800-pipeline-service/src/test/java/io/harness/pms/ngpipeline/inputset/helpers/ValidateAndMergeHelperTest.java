/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
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
    InputSetEntity invalidEntity = InputSetEntity.builder().identifier(invalidIdentifier).build();
    doReturn(Optional.of(invalidEntity))
        .when(pmsInputSetService)
        .get(accountId, orgId, projectId, pipelineId, invalidIdentifier, false);

    String validIdentifier = "validIdentifier";
    InputSetEntity validEntity = InputSetEntity.builder().identifier(validIdentifier).build();
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
