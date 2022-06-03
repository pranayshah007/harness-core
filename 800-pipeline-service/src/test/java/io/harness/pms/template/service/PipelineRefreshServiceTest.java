/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template.service;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.contracts.governance.PolicySetMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PipelineCRUDResult;
import io.harness.rule.Owner;
import io.harness.template.beans.refresh.ErrorNodeSummary;
import io.harness.template.beans.refresh.NodeInfo;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.template.beans.refresh.YamlDiffResponseDTO;
import io.harness.template.beans.refresh.YamlFullRefreshResponseDTO;

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
    when(pmsPipelineService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false))
        .thenReturn(Optional.of(pipelineEntityWithTemplates));
    when(pmsPipelineService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false))
        .thenReturn(Optional.of(pipelineEntityWithoutTemplates));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldFailRefreshTemplateInputsInPipelineWhenPipelineDoesnotExist() {
    String pipelineIdentifier = "someNoneExistentPipelineId";
    when(pmsPipelineService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineIdentifier, false))
        .thenReturn(Optional.empty());

    assertThatThrownBy(()
                           -> pipelineRefreshService.refreshTemplateInputsInPipeline(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Pipeline with the given id: %s does not exist or has been deleted", pipelineIdentifier));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshTemplateInputsInPipelineWithNoTemplateReferences() {
    pipelineRefreshService.refreshTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper, never()).getRefreshedYaml(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshTemplateInputsInPipelineWithTemplateReferences() {
    String refreshedYaml = "Yayy!! Updated YAML";
    when(pmsPipelineTemplateHelper.getRefreshedYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntityWithTemplates.getYaml()))
        .thenReturn(RefreshResponseDTO.builder().refreshedYaml(refreshedYaml).build());
    when(pmsPipelineService.updatePipelineYaml(any(), any()))
        .thenReturn(PipelineCRUDResult.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    pipelineRefreshService.refreshTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper).getRefreshedYaml(anyString(), anyString(), anyString(), anyString());

    ArgumentCaptor<PipelineEntity> argumentCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    verify(pmsPipelineService).updatePipelineYaml(argumentCaptor.capture(), eq(ChangeType.MODIFY));
    PipelineEntity updatedPipeline = argumentCaptor.getValue();
    assertThat(updatedPipeline).isNotNull();
    assertThat(updatedPipeline.getYaml()).isEqualTo(refreshedYaml);
    assertThat(updatedPipeline.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER_WITH_TEMPLATES);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFailRefreshTemplateInputsInPipelineWithTemplateReferencesIfGovernanceFails() {
    when(pmsPipelineTemplateHelper.getRefreshedYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntityWithTemplates.getYaml()))
        .thenReturn(RefreshResponseDTO.builder().refreshedYaml(pipelineEntityWithTemplates.getYaml()).build());
    when(pmsPipelineService.updatePipelineYaml(pipelineEntityWithTemplates, ChangeType.MODIFY))
        .thenReturn(
            PipelineCRUDResult.builder()
                .governanceMetadata(
                    GovernanceMetadata.newBuilder()
                        .setDeny(true)
                        .addDetails(PolicySetMetadata.newBuilder().setDeny(true).setIdentifier("policy1").build())
                        .build())
                .build());

    assertThatThrownBy(()
                           -> pipelineRefreshService.refreshTemplateInputsInPipeline(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline does not follow the Policies in these Policy Sets: [policy1]");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetYamlDiffPipelineWithNoTemplateReferences() {
    YamlDiffResponseDTO responseDTO =
        pipelineRefreshService.getYamlDiff(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper, never()).getRefreshedYaml(anyString(), anyString(), anyString(), anyString());
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getOriginalYaml()).isEqualTo(pipelineEntityWithoutTemplates.getYaml());
    assertThat(responseDTO.getRefreshedYaml()).isEqualTo(pipelineEntityWithoutTemplates.getYaml());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetYamlDiffPipelineWithTemplateReferences() {
    String refreshedYaml = "Yayy!! Updated YAML";
    when(pmsPipelineTemplateHelper.getRefreshedYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntityWithTemplates.getYaml()))
        .thenReturn(RefreshResponseDTO.builder().refreshedYaml(refreshedYaml).build());

    YamlDiffResponseDTO responseDTO =
        pipelineRefreshService.getYamlDiff(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper).getRefreshedYaml(anyString(), anyString(), anyString(), anyString());
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getOriginalYaml()).isEqualTo(pipelineEntityWithTemplates.getYaml());
    assertThat(responseDTO.getRefreshedYaml()).isEqualTo(refreshedYaml);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsInPipelineWithNoTemplateReferences() {
    ValidateTemplateInputsResponseDTO responseDTO = pipelineRefreshService.validateTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper, never())
        .validateTemplateInputsForGivenYaml(anyString(), anyString(), anyString(), anyString());
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isValidYaml()).isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsInPipelineWithTemplateReferences_ValidYaml() {
    when(pmsPipelineTemplateHelper.validateTemplateInputsForGivenYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntityWithTemplates.getYaml()))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder().validYaml(true).build());

    ValidateTemplateInputsResponseDTO responseDTO = pipelineRefreshService.validateTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper)
        .validateTemplateInputsForGivenYaml(anyString(), anyString(), anyString(), anyString());
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isValidYaml()).isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsInPipelineWithTemplateReferences_InValidYaml() {
    when(pmsPipelineTemplateHelper.validateTemplateInputsForGivenYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntityWithTemplates.getYaml()))
        .thenReturn(ValidateTemplateInputsResponseDTO.builder()
                        .validYaml(false)
                        .errorNodeSummary(ErrorNodeSummary.builder().build())
                        .build());

    ValidateTemplateInputsResponseDTO responseDTO = pipelineRefreshService.validateTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper)
        .validateTemplateInputsForGivenYaml(anyString(), anyString(), anyString(), anyString());
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
    when(pmsPipelineTemplateHelper.refreshAllTemplatesForYaml(
             ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntityWithTemplates.getYaml()))
        .thenReturn(YamlFullRefreshResponseDTO.builder().shouldRefreshYaml(true).refreshedYaml(refreshedYaml).build());
    when(pmsPipelineService.updatePipelineYaml(any(), any()))
        .thenReturn(PipelineCRUDResult.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    pipelineRefreshService.recursivelyRefreshAllTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITH_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper).refreshAllTemplatesForYaml(anyString(), anyString(), anyString(), anyString());

    ArgumentCaptor<PipelineEntity> argumentCaptor = ArgumentCaptor.forClass(PipelineEntity.class);
    verify(pmsPipelineService).updatePipelineYaml(argumentCaptor.capture(), eq(ChangeType.MODIFY));
    PipelineEntity updatedPipeline = argumentCaptor.getValue();
    assertThat(updatedPipeline).isNotNull();
    assertThat(updatedPipeline.getYaml()).isEqualTo(refreshedYaml);
    assertThat(updatedPipeline.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER_WITH_TEMPLATES);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRecursivelyRefreshAllTemplateInputsInPipelineWithoutTemplateReferences() {
    pipelineRefreshService.recursivelyRefreshAllTemplateInputsInPipeline(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES);
    verify(pmsPipelineService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_IDENTIFIER_WITHOUT_TEMPLATES, false);
    verify(pmsPipelineTemplateHelper, never())
        .refreshAllTemplatesForYaml(anyString(), anyString(), anyString(), anyString());
  }
}
