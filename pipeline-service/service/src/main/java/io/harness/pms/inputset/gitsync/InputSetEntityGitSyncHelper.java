/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.gitsync;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.InputSetReference;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.exception.InvalidRequestException;
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
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.ngpipeline.inputset.service.InputSetFullGitSyncHandler;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class InputSetEntityGitSyncHelper extends AbstractGitSdkEntityHandler<InputSetEntity, InputSetYamlDTO>
    implements GitSdkEntityHandlerInterface<InputSetEntity, InputSetYamlDTO> {
  private final PMSInputSetService pmsInputSetService;
  private final InputSetFullGitSyncHandler inputSetFullGitSyncHandler;

  @Inject
  public InputSetEntityGitSyncHelper(
      PMSInputSetService pmsInputSetService, InputSetFullGitSyncHandler inputSetFullGitSyncHandler) {
    this.pmsInputSetService = pmsInputSetService;
    this.inputSetFullGitSyncHandler = inputSetFullGitSyncHandler;
  }

  @Override
  public Supplier<InputSetYamlDTO> getYamlFromEntity(InputSetEntity entity) {
    return () -> InputSetYamlDTOMapper.toDTO(entity);
  }

  @Override
  public EntityType getEntityType() {
    return EntityType.INPUT_SETS;
  }

  @Override
  public Supplier<InputSetEntity> getEntityFromYaml(InputSetYamlDTO yaml, String accountIdentifier) {
    return () -> InputSetYamlDTOMapper.toEntity(yaml, accountIdentifier);
  }

  @Override
  public EntityDetail getEntityDetail(InputSetEntity entity) {
    return PMSInputSetElementMapper.toEntityDetail(entity);
  }

  @Override
  public InputSetYamlDTO save(String accountIdentifier, String yaml) {
    InputSetEntity initEntity = PMSInputSetElementMapper.toInputSetEntity(accountIdentifier, yaml);
    InputSetEntity savedEntity = pmsInputSetService.create(initEntity, false);
    return InputSetYamlDTOMapper.toDTO(savedEntity);
  }

  @Override
  public InputSetYamlDTO update(String accountIdentifier, String yaml, ChangeType changeType) {
    InputSetEntity inputSetEntity = PMSInputSetElementMapper.toInputSetEntity(accountIdentifier, yaml);
    InputSetEntity updatedEntity = pmsInputSetService.update(changeType, inputSetEntity, false);
    return InputSetYamlDTOMapper.toDTO(updatedEntity);
  }

  @Override
  public boolean markEntityInvalid(String accountIdentifier, EntityReference entityReference, String erroneousYaml) {
    InputSetReference inputSetReference = (InputSetReference) entityReference;
    return pmsInputSetService.markGitSyncedInputSetInvalid(accountIdentifier, entityReference.getOrgIdentifier(),
        entityReference.getProjectIdentifier(), inputSetReference.getPipelineIdentifier(),
        entityReference.getIdentifier(), erroneousYaml);
  }

  @Override
  public boolean delete(EntityReference entityReference) {
    return pmsInputSetService.delete(entityReference.getAccountIdentifier(), entityReference.getOrgIdentifier(),
        entityReference.getProjectIdentifier(), ((InputSetReference) entityReference).getPipelineIdentifier(),
        entityReference.getIdentifier(), null);
  }

  @Override
  public String getObjectIdOfYamlKey() {
    return InputSetEntityKeys.objectIdOfYaml;
  }

  @Override
  public String getIsFromDefaultBranchKey() {
    return InputSetEntityKeys.isFromDefaultBranch;
  }

  @Override
  public String getYamlGitConfigRefKey() {
    return InputSetEntityKeys.yamlGitConfigRef;
  }

  @Override
  public String getUuidKey() {
    return InputSetEntityKeys.uuid;
  }

  @Override
  public String getBranchKey() {
    return InputSetEntityKeys.branch;
  }

  @Override
  public List<FileChange> listAllEntities(ScopeDetails scopeDetails) {
    return inputSetFullGitSyncHandler.getFileChangesForFullSync(scopeDetails);
  }

  @Override
  protected InputSetYamlDTO updateEntityFilePath(String accountIdentifier, String yaml, String newFilePath) {
    InputSetEntity inputSetEntity = PMSInputSetElementMapper.toInputSetEntity(accountIdentifier, yaml);
    InputSetEntity updatedEntity = pmsInputSetService.updateGitFilePath(inputSetEntity, newFilePath);
    return InputSetYamlDTOMapper.toDTO(updatedEntity);
  }

  @Override
  public InputSetYamlDTO fullSyncEntity(FullSyncChangeSet fullSyncChangeSet) {
    try (GlobalContextManager.GlobalContextGuard ignore = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(createGitEntityInfo(fullSyncChangeSet));
      return inputSetFullGitSyncHandler.syncEntity(fullSyncChangeSet.getEntityDetail());
    }
  }

  @Override
  public Optional<EntityGitDetails> getEntityDetailsIfExists(String accountIdentifier, String yaml) {
    final InputSetYamlDTO inputSetYamlDTO = getYamlDTO(yaml);
    final Optional<InputSetEntity> inputSetEntity;
    if (inputSetYamlDTO.getInputSetInfo() != null) {
      final InputSetYamlInfoDTO inputSetInfo = inputSetYamlDTO.getInputSetInfo();
      inputSetEntity = pmsInputSetService.getWithoutValidations(accountIdentifier, inputSetInfo.getOrgIdentifier(),
          inputSetInfo.getProjectIdentifier(), inputSetInfo.getPipelineInfoConfig().getIdentifier(),
          inputSetInfo.getIdentifier(), false, false, false);
    } else {
      final OverlayInputSetYamlInfoDTO overlayInputSetInfo = inputSetYamlDTO.getOverlayInputSetInfo();
      inputSetEntity = pmsInputSetService.getWithoutValidations(accountIdentifier,
          overlayInputSetInfo.getOrgIdentifier(), overlayInputSetInfo.getProjectIdentifier(),
          overlayInputSetInfo.getPipelineIdentifier(), overlayInputSetInfo.getIdentifier(), false, false, false);
    }
    return inputSetEntity.map(EntityGitDetailsMapper::mapEntityGitDetails);
  }

  @Override
  public InputSetYamlDTO getYamlDTO(String yaml) {
    return InputSetYamlDTOMapper.toDTO(yaml);
  }

  @Override
  public String getYamlFromEntityRef(EntityDetailProtoDTO entityReference) {
    final InputSetReferenceProtoDTO inputSetRef = entityReference.getInputSetRef();
    final Optional<InputSetEntity> inputSetEntity = pmsInputSetService.getWithoutValidations(
        StringValueUtils.getStringFromStringValue(inputSetRef.getAccountIdentifier()),
        StringValueUtils.getStringFromStringValue(inputSetRef.getOrgIdentifier()),
        StringValueUtils.getStringFromStringValue(inputSetRef.getProjectIdentifier()),
        StringValueUtils.getStringFromStringValue(inputSetRef.getPipelineIdentifier()),
        StringValueUtils.getStringFromStringValue(inputSetRef.getIdentifier()), false, false, false);
    if (!inputSetEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] doesn't exist.",
              StringValueUtils.getStringFromStringValue(inputSetRef.getIdentifier()),
              StringValueUtils.getStringFromStringValue(inputSetRef.getPipelineIdentifier()),
              StringValueUtils.getStringFromStringValue(inputSetRef.getProjectIdentifier()),
              StringValueUtils.getStringFromStringValue(inputSetRef.getOrgIdentifier())));
    }
    return inputSetEntity.get().getYaml();
  }
}
