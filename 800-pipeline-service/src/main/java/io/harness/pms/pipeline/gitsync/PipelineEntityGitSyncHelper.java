/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.gitsync;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.common.EntityReference;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.EntityNotFoundException;
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
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.contracts.governance.PolicySetMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.mappers.PipelineYamlDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.service.PipelineFullGitSyncHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PipelineEntityGitSyncHelper extends AbstractGitSdkEntityHandler<PipelineEntity, PipelineConfig>
    implements GitSdkEntityHandlerInterface<PipelineEntity, PipelineConfig> {
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineServiceHelper pipelineServiceHelper;
  private final PipelineFullGitSyncHandler pipelineFullGitSyncHandler;
  private final PmsFeatureFlagService pmsFeatureFlagService;

  @Inject
  public PipelineEntityGitSyncHelper(PMSPipelineService pmsPipelineService,
      PMSPipelineServiceHelper pipelineServiceHelper, PipelineFullGitSyncHandler pipelineFullGitSyncHandler,
      PmsFeatureFlagService pmsFeatureFlagService) {
    this.pmsPipelineService = pmsPipelineService;
    this.pipelineServiceHelper = pipelineServiceHelper;
    this.pipelineFullGitSyncHandler = pipelineFullGitSyncHandler;
    this.pmsFeatureFlagService = pmsFeatureFlagService;
  }

  @Override
  public EntityType getEntityType() {
    return EntityType.PIPELINES;
  }

  @Override
  public Supplier<PipelineConfig> getYamlFromEntity(PipelineEntity entity) {
    return () -> PipelineYamlDtoMapper.toDto(entity);
  }

  @Override
  public Supplier<PipelineEntity> getEntityFromYaml(PipelineConfig yaml, String accountIdentifier) {
    return () -> PipelineYamlDtoMapper.toEntity(yaml, accountIdentifier);
  }

  @Override
  public EntityDetail getEntityDetail(PipelineEntity entity) {
    return PMSPipelineDtoMapper.toEntityDetail(entity);
  }

  @Override
  public PipelineConfig save(String accountIdentifier, String yaml) {
    PipelineEntity entity = PMSPipelineDtoMapper.toPipelineEntity(accountIdentifier, yaml);
    validate(accountIdentifier, entity);
    PipelineEntity pipelineEntity = pmsPipelineService.create(entity);
    return PipelineYamlDtoMapper.toDto(pipelineEntity);
  }

  @Override
  public PipelineConfig update(String accountIdentifier, String yaml, ChangeType changeType) {
    PipelineEntity entity = PMSPipelineDtoMapper.toPipelineEntity(accountIdentifier, yaml);
    validate(accountIdentifier, entity);
    PipelineEntity pipelineEntity = pmsPipelineService.updatePipelineYaml(entity, changeType);
    return PipelineYamlDtoMapper.toDto(pipelineEntity);
  }

  private void validate(String accountIdentifier, PipelineEntity entity) {
    boolean isGovernanceEnabled =
        pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    GovernanceMetadata governanceMetadata =
        pipelineServiceHelper.validatePipelineYamlAndSetTemplateRefIfAny(entity, isGovernanceEnabled);
    if (governanceMetadata.getDeny()) {
      List<String> denyingPolicySetIds = governanceMetadata.getDetailsList()
                                             .stream()
                                             .filter(PolicySetMetadata::getDeny)
                                             .map(PolicySetMetadata::getIdentifier)
                                             .collect(Collectors.toList());
      throw new InvalidRequestException(
          "Pipeline does not follow the Policies in these Policy Sets: " + denyingPolicySetIds.toString());
    }
  }

  @Override
  public boolean markEntityInvalid(String accountIdentifier, EntityReference entityReference, String erroneousYaml) {
    return pmsPipelineService.markEntityInvalid(accountIdentifier, entityReference.getOrgIdentifier(),
        entityReference.getProjectIdentifier(), entityReference.getIdentifier(), erroneousYaml);
  }

  @Override
  public boolean delete(EntityReference entityReference) {
    try {
      return pmsPipelineService.delete(entityReference.getAccountIdentifier(), entityReference.getOrgIdentifier(),
          entityReference.getProjectIdentifier(), entityReference.getIdentifier(), null);
    } catch (EventsFrameworkDownException ex) {
      throw new UnexpectedException("Producer shutdown: " + ExceptionUtils.getMessage(ex));
    }
  }

  @Override
  public String getObjectIdOfYamlKey() {
    return PipelineEntityKeys.objectIdOfYaml;
  }

  @Override
  public String getIsFromDefaultBranchKey() {
    return PipelineEntityKeys.isFromDefaultBranch;
  }

  @Override
  public String getYamlGitConfigRefKey() {
    return PipelineEntityKeys.yamlGitConfigRef;
  }

  @Override
  public String getUuidKey() {
    return PipelineEntityKeys.uuid;
  }

  @Override
  public String getBranchKey() {
    return PipelineEntityKeys.branch;
  }

  @Override
  public List<FileChange> listAllEntities(ScopeDetails scopeDetails) {
    return pipelineFullGitSyncHandler.getFileChangesForFullSync(scopeDetails);
  }

  @Override
  protected PipelineConfig updateEntityFilePath(String accountIdentifier, String yaml, String newFilePath) {
    PipelineEntity entity = PMSPipelineDtoMapper.toPipelineEntity(accountIdentifier, yaml);
    validate(accountIdentifier, entity);
    PipelineEntity pipelineEntity = pmsPipelineService.updateGitFilePath(entity, newFilePath);
    return PipelineYamlDtoMapper.toDto(pipelineEntity);
  }

  @Override
  public PipelineConfig fullSyncEntity(FullSyncChangeSet fullSyncChangeSet) {
    try (GlobalContextManager.GlobalContextGuard ignore = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(createGitEntityInfo(fullSyncChangeSet));
      return pipelineFullGitSyncHandler.syncEntity(fullSyncChangeSet.getEntityDetail());
    }
  }

  @Override
  public Optional<EntityGitDetails> getEntityDetailsIfExists(String accountIdentifier, String yaml) {
    final PipelineConfig pipelineConfig = getYamlDTO(yaml);
    final PipelineInfoConfig pipelineInfoConfig = pipelineConfig.getPipelineInfoConfig();
    try {
      final Optional<PipelineEntity> pipelineEntity =
          pmsPipelineService.get(accountIdentifier, pipelineInfoConfig.getOrgIdentifier(),
              pipelineInfoConfig.getProjectIdentifier(), pipelineInfoConfig.getIdentifier(), false);
      return pipelineEntity.map(EntityGitDetailsMapper::mapEntityGitDetails);
    } catch (EntityNotFoundException e) {
      return Optional.empty();
    }
  }

  @Override
  public PipelineConfig getYamlDTO(String yaml) {
    return PipelineYamlDtoMapper.toDto(yaml);
  }

  @Override
  public String getYamlFromEntityRef(EntityDetailProtoDTO entityReference) {
    final IdentifierRefProtoDTO identifierRef = entityReference.getIdentifierRef();
    final Optional<PipelineEntity> pipelineEntity =
        pmsPipelineService.get(StringValueUtils.getStringFromStringValue(identifierRef.getAccountIdentifier()),
            StringValueUtils.getStringFromStringValue(identifierRef.getOrgIdentifier()),
            StringValueUtils.getStringFromStringValue(identifierRef.getProjectIdentifier()),
            StringValueUtils.getStringFromStringValue(identifierRef.getIdentifier()), false);
    if (!pipelineEntity.isPresent()) {
      throw new InvalidRequestException(PipelineCRUDErrorResponse.errorMessageForPipelineNotFound(
          StringValueUtils.getStringFromStringValue(identifierRef.getOrgIdentifier()),
          StringValueUtils.getStringFromStringValue(identifierRef.getProjectIdentifier()),
          StringValueUtils.getStringFromStringValue(identifierRef.getIdentifier())));
    }
    return pipelineEntity.get().getYaml();
  }
}
