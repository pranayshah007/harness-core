/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template.service;

import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.refresh.ErrorNodeSummary;
import io.harness.ng.core.template.refresh.NodeInfo;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlDiffResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class PipelineRefreshServiceTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_IDENTIFIER_WITH_TEMPLATES = "pipelineIdentifierWithTemplates";
  private static final String PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES = "pipelineIdentifierWithoutTemplates";
  private final PipelineEntity pipelineEntityWithTemplates = PipelineEntity.builder()
                                                                 .identifier(PIPELINE_IDENTIFIER_WITH_TEMPLATES)
                                                                 .name(PIPELINE_IDENTIFIER_WITH_TEMPLATES)
                                                                 .yaml("pipeline:\n"
                                                                     + "  template:\n"
                                                                     + "    templateRef: hasRef")
                                                                 .build();
  private final PipelineEntity pipelineEntityWithoutTemplates = PipelineEntity.builder()
                                                                    .identifier(PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES)
                                                                    .name(PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES)
                                                                    .yaml("pipeline: noTemplateRef\n")
                                                                    .build();

  @InjectMocks PipelineRefreshServiceImpl pipelineRefreshService;
  @Mock PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineServiceHelper pipelineServiceHelper;

  @Before
  public void setup() {
    when(pmsPipelineService.getAndValidatePipeline(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false))
        .thenReturn(Optional.of(pipelineEntityWithTemplates));
    when(pmsPipelineService.getAndValidatePipeline(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false))
        .thenReturn(Optional.of(pipelineEntityWithoutTemplates));
    when(pmsPipelineService.getPipeline(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false, false, false, false))
        .thenReturn(Optional.of(pipelineEntityWithoutTemplates));
    when(pmsPipelineService.getPipeline(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false, false, false, false))
        .thenReturn(Optional.of(pipelineEntityWithTemplates));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldFailRefreshTemplateInputsInPipelineWhenPipelineDoesnotExist() {
    String pipelineIdentifier = "someNoneExistentPipelineId";
    when(pmsPipelineService.getAndValidatePipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineIdentifier, false))
        .thenReturn(Optional.empty());

    assertThatThrownBy(()
                           -> pipelineRefreshService.refreshTemplateInputsInPipeline(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineIdentifier, "false"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Pipeline with the given id: %s does not exist or has been deleted", pipelineIdentifier));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshTemplateInputsInPipelineWithNoTemplateReferences() {
    pipelineRefreshService.refreshTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, "false");
    verify(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false, false, false, false);
    verify(pmsPipelineTemplateHelper, times(1))
        .getRefreshedYaml(anyString(), anyString(), anyString(), anyString(), any(PipelineEntity.class), anyString());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshTemplateInputsInPipelineWithTemplateReferences() {
    String refreshedYaml = "Yayy!! Updated YAML";
    when(pmsPipelineTemplateHelper.getRefreshedYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             pipelineEntityWithTemplates.getYaml(), pipelineEntityWithTemplates, "false"))
        .thenReturn(RefreshResponseDTO.builder().refreshedYaml(refreshedYaml).build());
    when(pmsPipelineService.validateAndUpdatePipeline(any(), any(), eq(true)))
        .thenReturn(PipelineCRUDResult.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    pipelineRefreshService.refreshTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, "false");
    verify(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false, false, false, false);
    verify(pmsPipelineTemplateHelper)
        .getRefreshedYaml(anyString(), anyString(), anyString(), anyString(), any(PipelineEntity.class), anyString());

    ArgumentCaptor<PipelineEntity> argumentCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    verify(pmsPipelineService).validateAndUpdatePipeline(argumentCaptor.capture(), eq(ChangeType.MODIFY), eq(true));
    PipelineEntity updatedPipeline = argumentCaptor.getValue();
    assertThat(updatedPipeline).isNotNull();
    assertThat(updatedPipeline.getYaml()).isEqualTo(refreshedYaml);
    assertThat(updatedPipeline.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER_WITH_TEMPLATES);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFailRefreshTemplateInputsInPipelineWithTemplateReferencesIfGovernanceFails() {
    when(pmsPipelineTemplateHelper.getRefreshedYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             pipelineEntityWithTemplates.getYaml(), pipelineEntityWithTemplates, "false"))
        .thenReturn(RefreshResponseDTO.builder().refreshedYaml(pipelineEntityWithTemplates.getYaml()).build());
    when(pmsPipelineService.validateAndUpdatePipeline(pipelineEntityWithTemplates, ChangeType.MODIFY, true))
        .thenThrow(
            new InvalidRequestException("Pipeline does not follow the Policies in these Policy Sets: [policy1]"));

    assertThatThrownBy(()
                           -> pipelineRefreshService.refreshTemplateInputsInPipeline(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, "false"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline does not follow the Policies in these Policy Sets: [policy1]");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetYamlDiffPipelineWithTemplateReferences() {
    String refreshedYaml = "Yayy!! Updated YAML";
    when(pmsPipelineTemplateHelper.getRefreshedYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             pipelineEntityWithTemplates.getYaml(), pipelineEntityWithTemplates, "false"))
        .thenReturn(RefreshResponseDTO.builder().refreshedYaml(refreshedYaml).build());

    YamlDiffResponseDTO responseDTO =
        pipelineRefreshService.getYamlDiff(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, "false");
    verify(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false, false, false, false);
    verify(pmsPipelineTemplateHelper)
        .getRefreshedYaml(anyString(), anyString(), anyString(), anyString(), any(PipelineEntity.class), anyString());
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getOriginalYaml()).isEqualTo(pipelineEntityWithTemplates.getYaml());
    assertThat(responseDTO.getRefreshedYaml()).isEqualTo(refreshedYaml);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsInPipelineWithTemplateReferences_ValidYaml() {
    when(pmsPipelineTemplateHelper.validateTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             pipelineEntityWithTemplates.getYaml(), pipelineEntityWithTemplates, "false"))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder().validYaml(true).build());

    ValidateTemplateInputsResponseDTO responseDTO = pipelineRefreshService.validateTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, BOOLEAN_FALSE_VALUE);
    verify(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false, false, false, false);
    verify(pmsPipelineTemplateHelper)
        .validateTemplateInputsForGivenYaml(
            anyString(), anyString(), anyString(), anyString(), any(PipelineEntity.class), anyString());
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isValidYaml()).isTrue();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsInPipelineWithTemplateReferences_ValidYaml_WithCaching() {
    when(pmsPipelineTemplateHelper.validateTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             pipelineEntityWithTemplates.getYaml(), pipelineEntityWithTemplates, "true"))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder().validYaml(true).build());

    when(pmsPipelineService.getPipeline(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false, false, false, true))
        .thenReturn(Optional.of(pipelineEntityWithoutTemplates));
    when(pmsPipelineService.getPipeline(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false, false, false, true))
        .thenReturn(Optional.of(pipelineEntityWithTemplates));

    ValidateTemplateInputsResponseDTO responseDTO = pipelineRefreshService.validateTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, "true");
    verify(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false, false, false, true);
    verify(pmsPipelineTemplateHelper)
        .validateTemplateInputsForGivenYaml(
            anyString(), anyString(), anyString(), anyString(), any(PipelineEntity.class), anyString());
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isValidYaml()).isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsInPipelineWithTemplateReferences_InValidYaml() {
    when(pmsPipelineTemplateHelper.validateTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             pipelineEntityWithTemplates.getYaml(), pipelineEntityWithTemplates, "false"))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder()
                        .validYaml(false)
                        .errorNodeSummary(ErrorNodeSummary.builder().build())
                        .build());

    ValidateTemplateInputsResponseDTO responseDTO = pipelineRefreshService.validateTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, BOOLEAN_FALSE_VALUE);
    verify(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false, false, false, false);
    verify(pmsPipelineTemplateHelper)
        .validateTemplateInputsForGivenYaml(
            anyString(), anyString(), anyString(), anyString(), any(PipelineEntity.class), anyString());
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isValidYaml()).isFalse();
    assertThat(responseDTO.getErrorNodeSummary()).isNotNull();
    assertThat(responseDTO.getErrorNodeSummary().getNodeInfo()).isNotNull();
    NodeInfo nodeInfo = responseDTO.getErrorNodeSummary().getNodeInfo();
    assertThat(nodeInfo.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER_WITH_TEMPLATES);
    assertThat(nodeInfo.getName()).isEqualTo(PIPELINE_IDENTIFIER_WITH_TEMPLATES);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshAllTemplateInputsInPipelineWithTemplateReferences() {
    String refreshedYaml = "refreshedYaml";
    when(pmsPipelineTemplateHelper.refreshAllTemplatesForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID,
             pipelineEntityWithTemplates.getYaml(), pipelineEntityWithTemplates, "false"))
        .thenReturn(YamlFullRefreshResponseDTO.builder().shouldRefreshYaml(true).refreshedYaml(refreshedYaml).build());
    when(pmsPipelineService.validateAndUpdatePipeline(any(), any(), eq(true)))
        .thenReturn(PipelineCRUDResult.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    pipelineRefreshService.recursivelyRefreshAllTemplateInputsInPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID,
        PIPELINE_IDENTIFIER_WITH_TEMPLATES, GitEntityUpdateInfoDTO.builder().build(), "false");
    verify(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false, false, false, false);
    verify(pmsPipelineTemplateHelper)
        .refreshAllTemplatesForYaml(
            anyString(), anyString(), anyString(), anyString(), any(PipelineEntity.class), anyString());

    ArgumentCaptor<PipelineEntity> argumentCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    verify(pmsPipelineService).validateAndUpdatePipeline(argumentCaptor.capture(), eq(ChangeType.MODIFY), eq(true));
    PipelineEntity updatedPipeline = argumentCaptor.getValue();
    assertThat(updatedPipeline).isNotNull();
    assertThat(updatedPipeline.getYaml()).isEqualTo(refreshedYaml);
    assertThat(updatedPipeline.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER_WITH_TEMPLATES);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshAllTemplateInputsInPipelineWithoutTemplateReferences() {
    pipelineRefreshService.recursivelyRefreshAllTemplateInputsInPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID,
        PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, GitEntityUpdateInfoDTO.builder().build(), "false");
    verify(pmsPipelineService)
        .getPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false, false, false, false);
    verify(pmsPipelineTemplateHelper, times(1))
        .refreshAllTemplatesForYaml(
            anyString(), anyString(), anyString(), anyString(), any(PipelineEntity.class), anyString());
  }
}
