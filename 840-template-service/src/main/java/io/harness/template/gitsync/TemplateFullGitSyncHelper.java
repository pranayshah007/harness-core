/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.gitsync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.common.GitSyncFileConstants;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateEntityDetailUtils;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class TemplateFullGitSyncHelper {
  private final NGTemplateService templateService;
  private final EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  private final NGTemplateServiceHelper templateServiceHelper;

  @Inject
  public TemplateFullGitSyncHelper(NGTemplateService templateService,
      EntityDetailRestToProtoMapper entityDetailRestToProtoMapper, NGTemplateServiceHelper templateServiceHelper) {
    this.templateService = templateService;
    this.entityDetailRestToProtoMapper = entityDetailRestToProtoMapper;
    this.templateServiceHelper = templateServiceHelper;
  }

  public List<FileChange> getAllEntitiesForFullSync(ScopeDetails scope) {
    List<TemplateEntity> templatesListForFullSync = getTemplatesListForFullSync(scope);
    return templatesListForFullSync.stream()
        .map(templateEntity
            -> FileChange.newBuilder()
                   .setFilePath(getFilePathForTemplateInFullSync(templateEntity))
                   .setEntityDetail(entityDetailRestToProtoMapper.createEntityDetailDTO(
                       TemplateEntityDetailUtils.getEntityDetail(templateEntity)))
                   .build())
        .collect(Collectors.toList());
  }

  @NotNull
  private String getFilePathForTemplateInFullSync(TemplateEntity templateEntity) {
    return "templates"
        + "/" + templateEntity.getIdentifier() + "_" + templateEntity.getVersionLabel()
        + GitSyncFileConstants.YAML_EXTENSION;
  }

  private List<TemplateEntity> getTemplatesListForFullSync(ScopeDetails scope) {
    final EntityScopeInfo entityScope = scope.getEntityScope();
    Page<TemplateEntity> pagedTemplatesList = null;
    List<TemplateEntity> templateEntities = new LinkedList<>();
    do {
      Criteria criteria = getCriteriaForFullSync(entityScope);
      PageRequest pageRequest = PageRequest.of(pagedTemplatesList == null ? 0 : pagedTemplatesList.getNumber() + 1, 200,
          Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
      pagedTemplatesList = templateService.list(criteria, pageRequest, entityScope.getAccountId(),
          StringValueUtils.getStringFromStringValue(entityScope.getOrgId()),
          StringValueUtils.getStringFromStringValue(entityScope.getProjectId()), false);
      templateEntities.addAll(pagedTemplatesList.stream().collect(Collectors.toList()));
    } while (pagedTemplatesList.hasNext());

    return templateEntities;
  }

  private Criteria getCriteriaForFullSync(EntityScopeInfo entityScope) {
    Criteria criteria = templateServiceHelper.formCriteria(entityScope.getAccountId(),
        StringValueUtils.getStringFromStringValue(entityScope.getOrgId()),
        StringValueUtils.getStringFromStringValue(entityScope.getProjectId()), null, null, false, null, false);
    return criteria.and(TemplateEntityKeys.yamlGitConfigRef).is(null);
  }

  public NGTemplateConfig doFullGitSync(EntityDetailProtoDTO entityDetailProtoDTO) {
    return NGTemplateDtoMapper.toDTO(templateService.fullSyncTemplate(entityDetailProtoDTO));
  }
}
