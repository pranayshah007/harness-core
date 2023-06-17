/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.pms.filter.creation.FilterCreatorMergeService;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.references.PipelineSetupUsageCreationHelper;
import io.harness.pms.pipeline.validation.PipelineValidationResponse;
import io.harness.pms.pipeline.validation.service.PipelineValidationService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.InvalidYamlException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceHelperTest extends PipelineServiceTestBase {
  @Mock FilterService filterService;
  @Mock FilterCreatorMergeService filterCreatorMergeService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock PMSPipelineRepository pmsPipelineRepository;
  @Mock PipelineValidationService pipelineValidationService;
  @Mock PipelineGovernanceService pipelineGovernanceService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PipelineSetupUsageCreationHelper pipelineSetupUsageCreationHelper;
  @Spy @InjectMocks PMSPipelineServiceHelper pmsPipelineServiceHelper;

  String accountIdentifier = "account";
  String orgIdentifier = "org";
  String projectIdentifier = "project";
  String pipelineIdentifier = "pipeline";

  String repoName = "testRepo";

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(
        ()
            -> PMSPipelineServiceHelper.validatePresenceOfRequiredFields(PipelineEntity.builder()
                                                                             .accountId(accountIdentifier)
                                                                             .orgIdentifier(orgIdentifier)
                                                                             .identifier(pipelineIdentifier)
                                                                             .build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Required field [projectId] is either null or empty.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineEqualityCriteria() {
    Criteria criteria = PMSPipelineServiceHelper.getPipelineEqualityCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, false, 2L);
    assertThat(criteria).isNotNull();
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(PipelineEntityKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.identifier)).isEqualTo(pipelineIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.deleted)).isEqualTo(false);
    assertThat(criteriaObject.get(PipelineEntityKeys.version)).isEqualTo(2L);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfo() throws IOException {
    FilterCreatorMergeServiceResponse response =
        FilterCreatorMergeServiceResponse.builder()
            .stageCount(1)
            .stageNames(Collections.singletonList("stage-1"))
            .filters(Collections.singletonMap("whatKey?", "{\"some\" : \"value\"}"))
            .build();
    doReturn(response).when(filterCreatorMergeService).getPipelineInfo(any());
    PipelineEntity entity = PipelineEntity.builder().build();
    PipelineEntity updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(entity, PipelineVersion.V0);
    assertThat(updatedEntity.getStageCount()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().size()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().contains("stage-1")).isTrue();
    assertThat(updatedEntity.getFilters().size()).isEqualTo(1);
    assertThat(updatedEntity.getFilters().containsKey("whatKey?")).isTrue();
    assertThat(updatedEntity.getFilters().containsValue(Document.parse("{\"some\" : \"value\"}"))).isTrue();

    response = FilterCreatorMergeServiceResponse.builder()
                   .stageCount(1)
                   .stageNames(Collections.singletonList("stage-1"))
                   .build();
    doReturn(response).when(filterCreatorMergeService).getPipelineInfo(any());
    updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(updatedEntity, PipelineVersion.V0);
    assertThat(updatedEntity.getStageCount()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().size()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().contains("stage-1")).isTrue();
    assertThat(updatedEntity.getFilters().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoWithEmptyFilterValue() throws IOException {
    FilterCreatorMergeServiceResponse response = FilterCreatorMergeServiceResponse.builder()
                                                     .stageCount(1)
                                                     .stageNames(Collections.singletonList("stage-1"))
                                                     .filters(Collections.singletonMap("whatKey?", ""))
                                                     .build();
    doReturn(response).when(filterCreatorMergeService).getPipelineInfo(any());
    PipelineEntity entity = PipelineEntity.builder().build();
    PipelineEntity updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(entity, PipelineVersion.V0);
    assertThat(updatedEntity.getStageCount()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().size()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().contains("stage-1")).isTrue();
    assertThat(updatedEntity.getFilters().size()).isEqualTo(1);
    assertThat(updatedEntity.getFilters().containsKey("whatKey?")).isTrue();
    assertThat(updatedEntity.getFilters().containsValue(Document.parse("{}"))).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoWithInvalidFilterValue() throws IOException {
    FilterCreatorMergeServiceResponse response = FilterCreatorMergeServiceResponse.builder()
                                                     .stageCount(1)
                                                     .stageNames(Collections.singletonList("stage-1"))
                                                     .filters(Collections.singletonMap("whatKey?", "-`6^!"))
                                                     .build();
    doReturn(response).when(filterCreatorMergeService).getPipelineInfo(any());
    PipelineEntity entity = PipelineEntity.builder().build();
    PipelineEntity updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(entity, PipelineVersion.V0);
    assertThat(updatedEntity.getStageCount()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().size()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().contains("stage-1")).isTrue();
    assertThat(updatedEntity.getFilters().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testPopulateFilterUsingIdentifier() {
    String filterIdentifier = "filterIdentifier";
    FilterDTO filterDTO =
        FilterDTO.builder()
            .filterProperties(PipelineFilterPropertiesDto.builder()
                                  .name(pipelineIdentifier)
                                  .description("some description")
                                  .pipelineTags(Collections.singletonList(NGTag.builder().key("c").value("h").build()))
                                  .pipelineIdentifiers(Collections.singletonList(pipelineIdentifier))
                                  .build())
            .build();
    doReturn(null)
        .when(filterService)
        .get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINESETUP);
    assertThatThrownBy(()
                           -> pmsPipelineServiceHelper.populateFilterUsingIdentifier(
                               new Criteria(), accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier))
        .isInstanceOf(InvalidRequestException.class);
    doReturn(filterDTO)
        .when(filterService)
        .get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINESETUP);
    Criteria criteria = new Criteria();
    pmsPipelineServiceHelper.populateFilterUsingIdentifier(
        criteria, accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(PipelineEntityKeys.name)).isEqualTo(pipelineIdentifier);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.identifier)).get("$in")).size())
        .isEqualTo(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.identifier)).get("$in"))
                   .contains(pipelineIdentifier))
        .isTrue();
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.tags)).get("$in")).size()).isEqualTo(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.tags)).get("$in"))
                   .contains(NGTag.builder().key("c").value("h").build()))
        .isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testPopulateFilter() {
    Criteria criteria = new Criteria();
    PipelineFilterPropertiesDto pipelineFilter =
        PipelineFilterPropertiesDto.builder()
            .name(pipelineIdentifier)
            .description("some description")
            .pipelineTags(Collections.singletonList(NGTag.builder().key("c").value("h").build()))
            .pipelineIdentifiers(Collections.singletonList(pipelineIdentifier))
            .build();
    PMSPipelineServiceHelper.populateFilter(criteria, pipelineFilter);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(PipelineEntityKeys.name)).isEqualTo(pipelineIdentifier);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.identifier)).get("$in")).size())
        .isEqualTo(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.identifier)).get("$in"))
                   .contains(pipelineIdentifier))
        .isTrue();
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.tags)).get("$in")).size()).isEqualTo(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.tags)).get("$in"))
                   .contains(NGTag.builder().key("c").value("h").build()))
        .isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidatePipelineYamlInternal() {
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .build();
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(templateReferenceSummaryList)
                                                            .build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, false, false);

    Mockito.when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(PipelineValidationResponse.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());
    GovernanceMetadata governanceMetadata =
        pmsPipelineServiceHelper.resolveTemplatesAndValidatePipelineYaml(pipelineEntity, true, false);
    assertThat(governanceMetadata.getDeny()).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testValidatePipelineYamlInternalForV1Pipeline() {
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .harnessVersion(PipelineVersion.V1)
                                        .build();
    GovernanceMetadata governanceMetadata =
        pmsPipelineServiceHelper.resolveTemplatesAndValidatePipelineYaml(pipelineEntity, true, false);
    assertThat(governanceMetadata.getDeny()).isFalse();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testFormCriteria() {
    Criteria form = pmsPipelineServiceHelper.formCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, null, null, false, null, null);

    assertThat(form.getCriteriaObject().get("accountId").toString().contentEquals(accountIdentifier)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("orgIdentifier").toString().contentEquals(orgIdentifier)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("projectIdentifier").toString().contentEquals(projectIdentifier))
        .isEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("status")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("deleted")).isEqualTo(false);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFormCriteriaRepoFilter() {
    PipelineFilterPropertiesDto filterProperties = PipelineFilterPropertiesDto.builder().repoName(repoName).build();
    Criteria criteria = pmsPipelineServiceHelper.formCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, null, filterProperties, false, null, null);

    assertThat(criteria.getCriteriaObject().get(PipelineEntityKeys.repo).toString()).isEqualTo(repoName);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildInvalidYamlException() {
    String error = "this error message";
    String yaml = "yaml";
    InvalidYamlException invalidYamlException = PMSPipelineServiceHelper.buildInvalidYamlException(error, yaml);
    assertThat(invalidYamlException.getYaml()).isEqualTo(yaml);
    assertThatThrownBy(() -> { throw invalidYamlException; }).hasMessage(error);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetRepoUrlAndCheckForFileUniqueness() {
    String repoUrl = "repoUrl";
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().filePath("filePath").build();
    MockedStatic<GitAwareContextHelper> utilities = Mockito.mockStatic(GitAwareContextHelper.class);
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);

    doReturn(repoUrl).when(gitAwareEntityHelper).getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(10L)
        .when(pmsPipelineRepository)
        .countFileInstances(accountIdentifier, repoUrl, gitEntityInfo.getFilePath());
    assertThatThrownBy(()
                           -> pmsPipelineServiceHelper.getRepoUrlAndCheckForFileUniqueness(
                               accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, false))
        .isInstanceOf(DuplicateFileImportException.class);
    assertThat(pmsPipelineServiceHelper.getRepoUrlAndCheckForFileUniqueness(
                   accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, true))
        .isEqualTo(repoUrl);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testImportPipelineValidationChecks() {
    String importedPipeline = "pipeline:\n"
        + "  name: abcPipelineImport\n"
        + "  identifier: abcPipelineImport\n"
        + "  projectIdentifier: GitX_Remote\n"
        + "  orgIdentifier: default\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: zd\n"
        + "        identifier: zd\n"
        + "        description: \"\"\n"
        + "        type: Approval\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: dsf\n"
        + "                  identifier: dsf\n"
        + "                  type: HarnessApproval\n"
        + "                  timeout: 1d\n"
        + "                  spec:\n"
        + "                    approvalMessage: |-\n"
        + "                      Please review the following information\n"
        + "                      and approve the pipeline progression\n"
        + "                    includePipelineExecutionHistory: true\n"
        + "                    approvers:\n"
        + "                      minimumCount: 1\n"
        + "                      disallowPipelineExecutor: false\n"
        + "                      userGroups: <+input>\n"
        + "                    approverInputs: []\n"
        + "        tags: {}";
    String orgIdentifier = "default";
    String projectIdentifier = "GitX_Remote";
    String pipelineIdentifier = "abcPipelineImport";
    PipelineImportRequestDTO pipelineImportRequest = PipelineImportRequestDTO.builder()
                                                         .pipelineName("abcPipelineImport")
                                                         .pipelineDescription("junk pipeline description")
                                                         .build();
    Assertions.assertDoesNotThrow(
        ()
            -> PMSPipelineServiceHelper.checkAndThrowMismatchInImportedPipelineMetadataInternal(
                orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineImportRequest, importedPipeline));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testResolveTemplatesAndValidatePipeline() {
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .build();
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(templateReferenceSummaryList)
                                                            .build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, false, false);

    Mockito.when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(PipelineValidationResponse.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName("repoName")
                                      .connectorRef("connectorRef")
                                      .isNewBranch(true)
                                      .branch("branch")
                                      .build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    GovernanceMetadata governanceMetadata =
        pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(pipelineEntity, true, false);
    GitEntityInfo gitEntityInfo1 = GitContextHelper.getGitEntityInfo();

    assertEquals(gitEntityInfo1.getBranch(), gitEntityInfo.getBranch());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testComputePipelineReferencesForInlinePipeline() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().storeType(StoreType.INLINE).build();
    boolean loadFromCache = false;
    pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity, loadFromCache);
    verify(pipelineSetupUsageCreationHelper, times(0)).submitTask(any());
  }
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testComputePipelineReferencesForRemotePipelineLoadedFromCache() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().storeType(StoreType.REMOTE).build();
    boolean loadFromCache = true;
    pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity, loadFromCache);
    verify(pipelineSetupUsageCreationHelper, times(0)).submitTask(any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testComputePipelineReferencesForRemotePipelineLoadedFromGitForDefaultBranch() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().storeType(StoreType.REMOTE).build();
    boolean loadFromCache = false;
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().repoName("repoName").branch("").isDefaultBranch(true).build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity, loadFromCache);
    verify(pipelineSetupUsageCreationHelper, times(1)).submitTask(any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testComputePipelineReferencesForRemotePipelineLoadedFromGitForNonDefaultBranch() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().storeType(StoreType.REMOTE).build();
    boolean loadFromCache = false;
    GitEntityInfo gitEntityInfo =
        GitEntityInfo.builder().repoName("repoName").branch("main-patch").isDefaultBranch(false).build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    pmsPipelineServiceHelper.computePipelineReferences(pipelineEntity, loadFromCache);
    verify(pipelineSetupUsageCreationHelper, times(0)).submitTask(any());
  }
}
