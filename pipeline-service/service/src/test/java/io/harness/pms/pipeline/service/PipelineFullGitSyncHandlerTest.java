/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.ScopeDetails;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class PipelineFullGitSyncHandlerTest extends CategoryTest {
  @InjectMocks PipelineFullGitSyncHandler pipelineFullGitSyncHandler;
  @Mock private PMSPipelineService pipelineService;
  @Mock private PMSPipelineServiceHelper pipelineServiceHelper;
  @Mock private EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;

  private final String acc = "account_id";
  private final String org = "orgId";
  private final String proj = "projId";
  PipelineEntity pipelineEntity;
  EntityDetailProtoDTO entityDetailProtoDTO;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    String yaml = "pipeline:\n"
        + "  name: name\n"
        + "  identifier: identifier";
    pipelineEntity = PipelineEntity.builder()
                         .accountId(acc)
                         .orgIdentifier(org)
                         .projectIdentifier(proj)
                         .identifier("pipeline")
                         .yaml(yaml)
                         .build();
    entityDetailProtoDTO = EntityDetailProtoDTO.newBuilder().setType(EntityTypeProtoEnum.PIPELINES).build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFileChangesForFullSync() {
    Criteria criteria = Criteria.where("myKey").is("myValue");
    doReturn(criteria).when(pipelineServiceHelper).formCriteria(acc, org, proj, null, null, false, null, null);
    Criteria actualCriteria = criteria.and(PipelineEntityKeys.yamlGitConfigRef).is(null);
    PageRequest pageRequest = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));
    Page<PipelineEntity> pipelinesPage = new PageImpl<>(Collections.singletonList(pipelineEntity), pageRequest, 1);
    doReturn(pipelinesPage).when(pipelineService).list(actualCriteria, pageRequest, acc, org, proj, false);
    doReturn(entityDetailProtoDTO)
        .when(entityDetailRestToProtoMapper)
        .createEntityDetailDTO(PMSPipelineDtoMapper.toEntityDetail(pipelineEntity));

    EntityScopeInfo scope = EntityScopeInfo.newBuilder()
                                .setAccountId(acc)
                                .setOrgId(StringValue.of(org))
                                .setProjectId(StringValue.of(proj))
                                .build();
    ScopeDetails scopeDetails = ScopeDetails.newBuilder().setEntityScope(scope).build();
    List<FileChange> fileChangesForFullSync = pipelineFullGitSyncHandler.getFileChangesForFullSync(scopeDetails);
    assertThat(fileChangesForFullSync).hasSize(1);
    FileChange fileChange = fileChangesForFullSync.get(0);
    assertThat(fileChange.getFilePath()).isEqualTo("pipelines/pipeline.yaml");
    assertThat(fileChange.getEntityDetail()).isEqualTo(entityDetailProtoDTO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSyncEntity() {
    doReturn(pipelineEntity).when(pipelineService).syncPipelineEntityWithGit(entityDetailProtoDTO);
    PipelineConfig pipelineConfig = pipelineFullGitSyncHandler.syncEntity(entityDetailProtoDTO);
    assertThat(pipelineConfig).isNotNull();
  }
}
