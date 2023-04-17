/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.PipelineServiceTestBase;
import io.harness.PipelineSettingsService;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.filter.service.FilterService;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.pms.filter.creation.FilterCreatorMergeService;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.governance.ExpansionRequestsExtractor;
import io.harness.pms.governance.JsonExpander;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;

public class PipelineServiceFormCriteriaTest extends PipelineServiceTestBase {
  @Mock private PMSPipelineServiceHelper pmsPipelineServiceHelperMocked;
  @Mock private OutboxServiceImpl outboxService;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Inject private PipelineMetadataService pipelineMetadataService;

  @Mock private PipelineSettingsService pipelineSettingsService;
  @InjectMocks private PMSPipelineServiceImpl pmsPipelineService;
  @Inject private PMSPipelineRepository pmsPipelineRepository;

  @InjectMocks PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock FilterService filterService;
  @Mock FilterCreatorMergeService filterCreatorMergeService;
  @Mock private PmsGitSyncHelper gitSyncHelper;
  @Mock private ExpansionRequestsExtractor expansionRequestsExtractor;
  @Mock private JsonExpander jsonExpander;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock private ProjectClient projectClient;

  private final String accountId = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "myPipeline";

  PipelineEntity pipelineEntity;
  PipelineEntity updatedPipelineEntity;
  OutboxEvent outboxEvent = OutboxEvent.builder().build();

  @Before
  public void setup() {
    String yaml = "yaml: pipeline";
    pipelineEntity = PipelineEntity.builder()
                         .accountId(accountId)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .name(PIPELINE_IDENTIFIER)
                         .yaml(yaml)
                         .harnessVersion(PipelineVersion.V0)
                         .stageCount(1)
                         .stageName("qaStage")
                         .version(null)
                         .deleted(false)
                         .createdAt(System.currentTimeMillis())
                         .lastUpdatedAt(System.currentTimeMillis())
                         .build();

    updatedPipelineEntity = pipelineEntity.withStageCount(1).withStageNames(Collections.singletonList("qaStage"));
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testFormCriteriaWithActualData() throws IOException {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity)
        .when(pmsPipelineServiceHelperMocked)
        .updatePipelineInfo(pipelineEntity, PipelineVersion.V0);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelperMocked)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean());
    doReturn(TemplateMergeResponseDTO.builder().build())
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), anyBoolean(), anyBoolean());
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    pmsPipelineService.validateAndCreatePipeline(pipelineEntity, true);

    Criteria criteria = pmsPipelineServiceHelper.formCriteria(
        accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, false, "cd", "my");

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.createdAt));

    List<PipelineEntity> list =
        pmsPipelineService.list(criteria, pageable, accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER, null).getContent();

    assertThat(list.size()).isEqualTo(1);
    PipelineEntity queriedPipelineEntity = list.get(0);
    assertThat(queriedPipelineEntity.getAccountId()).isEqualTo(updatedPipelineEntity.getAccountId());
    assertThat(queriedPipelineEntity.getOrgIdentifier()).isEqualTo(updatedPipelineEntity.getOrgIdentifier());
    assertThat(queriedPipelineEntity.getIdentifier()).isEqualTo(updatedPipelineEntity.getIdentifier());
    assertThat(queriedPipelineEntity.getName()).isEqualTo(updatedPipelineEntity.getName());
    assertThat(queriedPipelineEntity.getYaml()).isEqualTo(updatedPipelineEntity.getYaml());
    assertThat(queriedPipelineEntity.getStageCount()).isEqualTo(updatedPipelineEntity.getStageCount());
    assertThat(queriedPipelineEntity.getStageNames()).isEqualTo(updatedPipelineEntity.getStageNames());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testFormCriteriaInvalidModuleType() throws IOException {
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(accountId, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    doReturn(Optional.empty()).when(pipelineMetadataService).getMetadata(any(), any(), any(), any());
    on(pmsPipelineService).set("pmsPipelineRepository", pmsPipelineRepository);
    doReturn(outboxEvent).when(outboxService).save(any());
    doReturn(updatedPipelineEntity)
        .when(pmsPipelineServiceHelperMocked)
        .updatePipelineInfo(pipelineEntity, PipelineVersion.V0);
    doReturn(GovernanceMetadata.newBuilder().setDeny(false).build())
        .when(pmsPipelineServiceHelperMocked)
        .resolveTemplatesAndValidatePipeline(any(), anyBoolean(), anyBoolean());
    doReturn(TemplateMergeResponseDTO.builder().build())
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), anyBoolean(), anyBoolean());
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    aStatic.when(() -> NGRestUtils.getResponse(projectClient.getProject(any(), any(), any()), any()))
        .thenReturn(projDTOCall);
    pmsPipelineService.validateAndCreatePipeline(pipelineEntity, true);

    final Throwable ex = catchThrowable(()
                                            -> pmsPipelineServiceHelper.formCriteria(accountId, ORG_IDENTIFIER,
                                                PROJ_IDENTIFIER, null, null, false, "cn", "my"));
    assertThat(ex).isInstanceOf(HintException.class);
    assertThat(ex.getMessage()).isEqualTo("Invalid module type [cn]");
  }
}
