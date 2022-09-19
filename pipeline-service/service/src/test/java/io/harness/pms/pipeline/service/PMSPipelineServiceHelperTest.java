/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.GovernanceService;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.filter.creation.FilterCreatorMergeService;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.governance.ExpansionRequest;
import io.harness.pms.governance.ExpansionRequestsExtractor;
import io.harness.pms.governance.JsonExpander;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceHelperTest extends CategoryTest {
  PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock FilterService filterService;
  @Mock FilterCreatorMergeService filterCreatorMergeService;
  @Mock private PmsGitSyncHelper gitSyncHelper;
  @Mock private ExpansionRequestsExtractor expansionRequestsExtractor;
  @Mock private JsonExpander jsonExpander;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock TelemetryReporter telemetryReporter;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock PMSYamlSchemaService yamlSchemaService;
  @Mock GovernanceService governanceService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock PMSPipelineRepository pmsPipelineRepository;

  String accountIdentifier = "account";
  String orgIdentifier = "org";
  String projectIdentifier = "project";
  String pipelineIdentifier = "pipeline";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pmsPipelineServiceHelper = new PMSPipelineServiceHelper(filterService, filterCreatorMergeService, yamlSchemaService,
        pipelineTemplateHelper, governanceService, jsonExpander, expansionRequestsExtractor, pmsFeatureFlagService,
        gitSyncHelper, telemetryReporter, gitAwareEntityHelper, pmsPipelineRepository);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> PMSPipelineServiceHelper.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
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
    PipelineEntity updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(entity);
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
    updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(updatedEntity);
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
    PipelineEntity updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(entity);
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
    PipelineEntity updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(entity);
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
        .resolveTemplateRefsInPipeline(pipelineEntity, false);
    GovernanceMetadata governanceMetadata =
        pmsPipelineServiceHelper.validatePipelineYamlInternal(pipelineEntity, false);
    assertThat(governanceMetadata.getDeny()).isFalse();
    verify(yamlSchemaService, times(1)).validateYamlSchema(accountIdentifier, orgIdentifier, projectIdentifier, yaml);
    verify(yamlSchemaService, times(1)).validateUniqueFqn(yaml);
    verify(governanceService, times(0)).evaluateGovernancePolicies(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchExpandedPipelineJSONFromYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    String dummyYaml = "don't really need a proper yaml cuz only testing the flow";
    ByteString randomByteString = ByteString.copyFromUtf8("sss");
    ExpansionRequestMetadata expansionRequestMetadata = ExpansionRequestMetadata.newBuilder()
                                                            .setAccountId(accountIdentifier)
                                                            .setOrgId(orgIdentifier)
                                                            .setProjectId(projectIdentifier)
                                                            .setGitSyncBranchContext(randomByteString)
                                                            .setYaml(ByteString.copyFromUtf8(dummyYaml))
                                                            .build();
    ExpansionRequest dummyRequest = ExpansionRequest.builder().fqn("fqn").build();
    Set<ExpansionRequest> dummyRequestSet = Collections.singleton(dummyRequest);
    doReturn(randomByteString).when(gitSyncHelper).getGitSyncBranchContextBytesThreadLocal();
    doReturn(dummyRequestSet).when(expansionRequestsExtractor).fetchExpansionRequests(dummyYaml);
    ExpansionResponseProto dummyResponse =
        ExpansionResponseProto.newBuilder().setSuccess(false).setErrorMessage("just because").build();
    ExpansionResponseBatch dummyResponseBatch =
        ExpansionResponseBatch.newBuilder().addExpansionResponseProto(dummyResponse).build();
    Set<ExpansionResponseBatch> dummyResponseSet = Collections.singleton(dummyResponseBatch);
    doReturn(dummyResponseSet).when(jsonExpander).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);
    pmsPipelineServiceHelper.fetchExpandedPipelineJSONFromYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, dummyYaml, false);
    verify(pmsFeatureFlagService, times(1)).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    verify(gitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal();
    verify(expansionRequestsExtractor, times(1)).fetchExpansionRequests(dummyYaml);
    verify(jsonExpander, times(1)).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);

    doReturn(false).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    String noExp = pmsPipelineServiceHelper.fetchExpandedPipelineJSONFromYaml(
        accountIdentifier, orgIdentifier, projectIdentifier, dummyYaml, false);
    assertThat(noExp).isEqualTo(dummyYaml);
    verify(pmsFeatureFlagService, times(2)).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    verify(gitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal();
    verify(expansionRequestsExtractor, times(1)).fetchExpansionRequests(dummyYaml);
    verify(jsonExpander, times(1)).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);
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
}
