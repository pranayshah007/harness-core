/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS.ALL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.common.GitSyncFileConstants;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.pms.inputset.gitsync.InputSetYamlDTO;
import io.harness.pms.inputset.gitsync.InputSetYamlDTOMapper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;

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
public class InputSetFullGitSyncHandler {
  @Inject private PMSInputSetService inputSetService;
  @Inject private EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;

  public List<FileChange> getFileChangesForFullSync(ScopeDetails scopeDetails) {
    List<FileChange> fileChanges = new LinkedList<>();

    EntityScopeInfo scope = scopeDetails.getEntityScope();
    String accountId = scope.getAccountId();
    String orgId = StringValueUtils.getStringFromStringValue(scope.getOrgId());
    String projectId = StringValueUtils.getStringFromStringValue(scope.getProjectId());
    Criteria criteria = getCriteriaForFullSync(accountId, orgId, projectId);

    Page<InputSetEntity> currentPage = null;
    do {
      int pageNumber = currentPage == null ? 0 : currentPage.getNumber() + 1;
      int pageSize = 200;
      PageRequest pageRequest =
          PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, InputSetEntityKeys.lastUpdatedAt));
      currentPage = inputSetService.list(criteria, pageRequest, accountId, orgId, projectId);
      fileChanges.addAll(currentPage
                             .map(entity
                                 -> FileChange.newBuilder()
                                        .setFilePath(getFilePath(entity))
                                        .setEntityDetail(entityDetailRestToProtoMapper.createEntityDetailDTO(
                                            PMSInputSetElementMapper.toEntityDetail(entity)))
                                        .build())
                             .stream()
                             .collect(Collectors.toList()));
    } while (currentPage.hasNext());
    return fileChanges;
  }

  private Criteria getCriteriaForFullSync(String accountId, String orgId, String projectId) {
    Criteria criteria =
        PMSInputSetFilterHelper.createCriteriaForGetList(accountId, orgId, projectId, null, ALL, null, false);
    return criteria.and(InputSetEntityKeys.yamlGitConfigRef).is(null);
  }

  private String getFilePath(InputSetEntity entity) {
    return "inputSets/" + entity.getPipelineIdentifier() + "/" + entity.getIdentifier()
        + GitSyncFileConstants.YAML_EXTENSION;
  }

  public InputSetYamlDTO syncEntity(EntityDetailProtoDTO entityDetail) {
    InputSetEntity syncedEntity = inputSetService.syncInputSetWithGit(entityDetail);
    return InputSetYamlDTOMapper.toDTO(syncedEntity.getYaml());
  }
}
