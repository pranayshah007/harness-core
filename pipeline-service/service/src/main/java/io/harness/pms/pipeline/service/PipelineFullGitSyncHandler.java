/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.common.GitSyncFileConstants;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.mappers.PipelineYamlDtoMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineFullGitSyncHandler {
  @Inject private PMSPipelineService pipelineService;
  @Inject private EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  @Inject private PMSPipelineServiceHelper pipelineServiceHelper;

  public List<FileChange> getFileChangesForFullSync(ScopeDetails scopeDetails) {
    List<FileChange> fileChanges = new LinkedList<>();

    EntityScopeInfo scope = scopeDetails.getEntityScope();
    String accountId = scope.getAccountId();
    String orgId = StringValueUtils.getStringFromStringValue(scope.getOrgId());
    String projectId = StringValueUtils.getStringFromStringValue(scope.getProjectId());
    Criteria criteria = getCriteriaForFullSync(accountId, orgId, projectId);

    Page<PipelineEntity> currentPage = null;
    do {
      int pageNumber = currentPage == null ? 0 : currentPage.getNumber() + 1;
      int pageSize = 200;
      PageRequest pageRequest =
          PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.lastUpdatedAt));
      currentPage = pipelineService.list(criteria, pageRequest, accountId, orgId, projectId, false);
      fileChanges.addAll(currentPage
                             .map(entity
                                 -> FileChange.newBuilder()
                                        .setFilePath(getFilePath(entity.getIdentifier()))
                                        .setEntityDetail(entityDetailRestToProtoMapper.createEntityDetailDTO(
                                            PMSPipelineDtoMapper.toEntityDetail(entity)))
                                        .build())
                             .stream()
                             .collect(Collectors.toList()));

    } while (currentPage.hasNext());
    return fileChanges;
  }

  private Criteria getCriteriaForFullSync(String accountId, String orgId, String projectId) {
    Criteria criteria = pipelineServiceHelper.formCriteria(accountId, orgId, projectId, null, null, false, null, null);
    return criteria.and(PipelineEntityKeys.yamlGitConfigRef).is(null);
  }

  private String getFilePath(String identifier) {
    return "pipelines/" + identifier + GitSyncFileConstants.YAML_EXTENSION;
  }

  public PipelineConfig syncEntity(EntityDetailProtoDTO entityDetail) {
    PipelineEntity syncedEntity = pipelineService.syncPipelineEntityWithGit(entityDetail);
    return PipelineYamlDtoMapper.toDto(syncedEntity.getYaml());
  }
}
