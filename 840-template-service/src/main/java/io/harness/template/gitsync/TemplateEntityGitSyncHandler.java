/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.gitsync;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.NGTemplateReference;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.FullSyncChangeSet;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.entityInfo.AbstractGitSdkEntityHandler;
import io.harness.gitsync.entityInfo.GitSdkEntityHandlerInterface;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.beans.yaml.NGTemplateInfoConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateEntityDetailUtils;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.services.NGTemplateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class TemplateEntityGitSyncHandler extends AbstractGitSdkEntityHandler<TemplateEntity, NGTemplateConfig>
    implements GitSdkEntityHandlerInterface<TemplateEntity, NGTemplateConfig> {
  private final NGTemplateService templateService;
  private final TemplateFullGitSyncHelper templateFullGitSyncHelper;

  @Inject
  public TemplateEntityGitSyncHandler(
      NGTemplateService templateService, TemplateFullGitSyncHelper templateFullGitSyncHelper) {
    this.templateService = templateService;
    this.templateFullGitSyncHelper = templateFullGitSyncHelper;
  }

  @Override
  public NGTemplateConfig getYamlDTO(String yaml) {
    return NGTemplateDtoMapper.toDTO(yaml);
  }

  @Override
  public Supplier<NGTemplateConfig> getYamlFromEntity(TemplateEntity entity) {
    return () -> NGTemplateDtoMapper.toDTO(entity);
  }

  @Override
  public EntityType getEntityType() {
    return EntityType.TEMPLATE;
  }

  @Override
  public Supplier<TemplateEntity> getEntityFromYaml(NGTemplateConfig templateConfig, String accountIdentifier) {
    return () -> NGTemplateDtoMapper.toTemplateEntity(accountIdentifier, templateConfig);
  }

  @Override
  public EntityDetail getEntityDetail(TemplateEntity entity) {
    return TemplateEntityDetailUtils.getEntityDetail(entity);
  }

  @Override
  public NGTemplateConfig save(String accountIdentifier, String yaml) {
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(accountIdentifier, yaml);
    TemplateEntity createdTemplate = templateService.create(templateEntity, false, "", false);
    return NGTemplateDtoMapper.toDTO(createdTemplate);
  }

  @Override
  public NGTemplateConfig update(String accountIdentifier, String yaml, ChangeType changeType) {
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(accountIdentifier, yaml);
    return NGTemplateDtoMapper.toDTO(templateService.updateTemplateEntity(templateEntity, changeType, false, ""));
  }

  @Override
  public boolean markEntityInvalid(String accountIdentifier, EntityReference entityReference, String erroneousYaml) {
    NGTemplateReference ngTemplateReference = (NGTemplateReference) entityReference;
    return templateService.markEntityInvalid(accountIdentifier, ngTemplateReference.getOrgIdentifier(),
        ngTemplateReference.getProjectIdentifier(), ngTemplateReference.getIdentifier(),
        ngTemplateReference.getVersionLabel(), erroneousYaml);
  }

  @Override
  public boolean delete(EntityReference entityReference) {
    try {
      NGTemplateReference reference = (NGTemplateReference) entityReference;
      return templateService.delete(entityReference.getAccountIdentifier(), entityReference.getOrgIdentifier(),
          entityReference.getProjectIdentifier(), entityReference.getIdentifier(), reference.getVersionLabel(), null,
          "", false);
    } catch (EventsFrameworkDownException ex) {
      throw new UnexpectedException("Producer shutdown: " + ExceptionUtils.getMessage(ex));
    }
  }

  @Override
  public String getObjectIdOfYamlKey() {
    return TemplateEntityKeys.objectIdOfYaml;
  }

  @Override
  public String getIsFromDefaultBranchKey() {
    return TemplateEntityKeys.isFromDefaultBranch;
  }

  @Override
  public String getYamlGitConfigRefKey() {
    return TemplateEntityKeys.yamlGitConfigRef;
  }

  @Override
  public String getUuidKey() {
    return TemplateEntityKeys.uuid;
  }

  @Override
  public String getBranchKey() {
    return TemplateEntityKeys.branch;
  }

  @Override
  public List<FileChange> listAllEntities(ScopeDetails scopeDetails) {
    return templateFullGitSyncHelper.getAllEntitiesForFullSync(scopeDetails);
  }

  @Override
  protected NGTemplateConfig updateEntityFilePath(String accountIdentifier, String yaml, String newFilePath) {
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(accountIdentifier, yaml);
    return NGTemplateDtoMapper.toDTO(templateService.updateGitFilePath(templateEntity, newFilePath));
  }

  @Override
  public Optional<EntityGitDetails> getEntityDetailsIfExists(String accountIdentifier, String yaml) {
    NGTemplateConfig yamlDTO = getYamlDTO(yaml);
    NGTemplateInfoConfig templateInfoConfig = yamlDTO.getTemplateInfoConfig();
    Optional<TemplateEntity> templateEntity = templateService.get(accountIdentifier,
        templateInfoConfig.getOrgIdentifier(), templateInfoConfig.getProjectIdentifier(),
        templateInfoConfig.getIdentifier(), templateInfoConfig.getVersionLabel(), false, false);
    return templateEntity.map(EntityGitDetailsMapper::mapEntityGitDetails);
  }

  @Override
  public String getYamlFromEntityRef(EntityDetailProtoDTO entityReference) {
    TemplateReferenceProtoDTO templateRef = entityReference.getTemplateRef();
    Optional<TemplateEntity> templateEntity =
        templateService.get(StringValueUtils.getStringFromStringValue(templateRef.getAccountIdentifier()),
            StringValueUtils.getStringFromStringValue(templateRef.getOrgIdentifier()),
            StringValueUtils.getStringFromStringValue(templateRef.getProjectIdentifier()),
            StringValueUtils.getStringFromStringValue(templateRef.getIdentifier()),
            StringValueUtils.getStringFromStringValue(templateRef.getVersionLabel()), false, false);
    if (!templateEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("Template for this identifier %s and versionLabel %s doesn't exist - ",
              StringValueUtils.getStringFromStringValue(templateRef.getIdentifier()),
              StringValueUtils.getStringFromStringValue(templateRef.getVersionLabel())));
    }
    return templateEntity.get().getYaml();
  }

  @Override
  public NGTemplateConfig fullSyncEntity(FullSyncChangeSet fullSyncChangeSet) {
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(createGitEntityInfo(fullSyncChangeSet));
      return templateFullGitSyncHelper.doFullGitSync(fullSyncChangeSet.getEntityDetail());
    }
  }
}
