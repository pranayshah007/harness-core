/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS.ALL;
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
import io.harness.pms.inputset.gitsync.InputSetYamlDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
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
public class InputSetFullGitSyncHandlerTest extends CategoryTest {
  @InjectMocks InputSetFullGitSyncHandler inputSetFullGitSyncHandler;
  @Mock PMSInputSetService inputSetService;
  @Mock EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;

  private final String acc = "account_id";
  private final String org = "orgId";
  private final String proj = "projId";
  private final String pipeline = "pipelineId";
  InputSetEntity inputSetEntity;
  EntityDetailProtoDTO entityDetailProtoDTO;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    String yaml = "inputSet:\n"
        + "  name: name\n"
        + "  identifier: identifier";
    inputSetEntity = InputSetEntity.builder()
                         .accountId(acc)
                         .orgIdentifier(org)
                         .projectIdentifier(proj)
                         .pipelineIdentifier(pipeline)
                         .identifier("inputSet234")
                         .yaml(yaml)
                         .build();
    entityDetailProtoDTO = EntityDetailProtoDTO.newBuilder().setType(EntityTypeProtoEnum.PIPELINES).build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFileChangesForFullSync() {
    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetList(acc, org, proj, null, ALL, null, false);
    criteria = criteria.and(InputSetEntityKeys.yamlGitConfigRef).is(null);
    PageRequest pageRequest = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, InputSetEntityKeys.lastUpdatedAt));
    Page<InputSetEntity> inputSetPages = new PageImpl<>(Collections.singletonList(inputSetEntity), pageRequest, 1);
    doReturn(inputSetPages).when(inputSetService).list(criteria, pageRequest, acc, org, proj);
    doReturn(entityDetailProtoDTO)
        .when(entityDetailRestToProtoMapper)
        .createEntityDetailDTO(PMSInputSetElementMapper.toEntityDetail(inputSetEntity));

    EntityScopeInfo scope = EntityScopeInfo.newBuilder()
                                .setAccountId(acc)
                                .setOrgId(StringValue.of(org))
                                .setProjectId(StringValue.of(proj))
                                .build();
    ScopeDetails scopeDetails = ScopeDetails.newBuilder().setEntityScope(scope).build();
    List<FileChange> fileChangesForFullSync = inputSetFullGitSyncHandler.getFileChangesForFullSync(scopeDetails);
    assertThat(fileChangesForFullSync).hasSize(1);
    FileChange fileChange = fileChangesForFullSync.get(0);
    assertThat(fileChange.getFilePath()).isEqualTo("inputSets/pipelineId/inputSet234.yaml");
    assertThat(fileChange.getEntityDetail()).isEqualTo(entityDetailProtoDTO);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSyncEntity() {
    doReturn(inputSetEntity).when(inputSetService).syncInputSetWithGit(entityDetailProtoDTO);
    InputSetYamlDTO inputSetYamlDTO = inputSetFullGitSyncHandler.syncEntity(entityDetailProtoDTO);
    assertThat(inputSetYamlDTO).isNotNull();
  }
}
