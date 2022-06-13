/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.InputSetReferenceProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.utils.GitEntityFilePath;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.EntityObjectIdUtils;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.inputset.gitsync.InputSetYamlDTOMapper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.repositories.inputset.PMSInputSetRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PMSInputSetServiceImpl implements PMSInputSetService {
  @Inject private PMSInputSetRepository inputSetRepository;
  @Inject private GitSyncSdkService gitSyncSdkService;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "Input set [%s] under Project[%s], Organization [%s] for Pipeline [%s] already exists";

  @Override
  public InputSetEntity create(InputSetEntity inputSetEntity) {
    try {
      if (gitSyncSdkService.isGitSyncEnabled(inputSetEntity.getAccountIdentifier(), inputSetEntity.getOrgIdentifier(),
              inputSetEntity.getProjectIdentifier())) {
        return inputSetRepository.saveForOldGitSync(inputSetEntity, InputSetYamlDTOMapper.toDTO(inputSetEntity));
      } else {
        return inputSetRepository.save(inputSetEntity);
      }
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format(DUP_KEY_EXP_FORMAT_STRING, inputSetEntity.getIdentifier(), inputSetEntity.getProjectIdentifier(),
              inputSetEntity.getOrgIdentifier(), inputSetEntity.getPipelineIdentifier()),
          USER_SRE, ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while creating Input Set " + inputSetEntity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving input set [%s]", inputSetEntity.getIdentifier()), e);
      throw new InvalidRequestException(
          String.format("Error while saving input set [%s]: %s", inputSetEntity.getIdentifier(), e.getMessage()));
    }
  }

  @Override
  public Optional<InputSetEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean deleted) {
    Optional<InputSetEntity> optionalInputSetEntity;
    try {
      if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
        optionalInputSetEntity = inputSetRepository.findForOldGitSync(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, !deleted);
      } else {
        optionalInputSetEntity = inputSetRepository.find(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, !deleted, false);
      }
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while retrieving pipeline [%s]", identifier), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while retrieving input set [%s]", identifier), e);
      throw new InvalidRequestException(
          String.format("Error while retrieving input set [%s]: %s", identifier, e.getMessage()));
    }
    return optionalInputSetEntity;
  }

  @Override
  public InputSetEntity update(InputSetEntity inputSetEntity, ChangeType changeType) {
    if (gitSyncSdkService.isGitSyncEnabled(inputSetEntity.getAccountIdentifier(), inputSetEntity.getOrgIdentifier(),
            inputSetEntity.getProjectIdentifier())) {
      return updateForOldGitSync(inputSetEntity, changeType);
    }
    return makeInputSetUpdateCall(inputSetEntity, changeType, false);
  }

  private InputSetEntity updateForOldGitSync(InputSetEntity inputSetEntity, ChangeType changeType) {
    if (GitContextHelper.getGitEntityInfo() != null && GitContextHelper.getGitEntityInfo().isNewBranch()) {
      return makeInputSetUpdateCall(inputSetEntity, changeType, true);
    }
    Optional<InputSetEntity> optionalOriginalEntity =
        get(inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(), inputSetEntity.getProjectIdentifier(),
            inputSetEntity.getPipelineIdentifier(), inputSetEntity.getIdentifier(), false);
    if (!optionalOriginalEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] doesn't exist.",
              inputSetEntity.getIdentifier(), inputSetEntity.getPipelineIdentifier(),
              inputSetEntity.getProjectIdentifier(), inputSetEntity.getOrgIdentifier()));
    }

    InputSetEntity originalEntity = optionalOriginalEntity.get();
    if (inputSetEntity.getVersion() != null && !inputSetEntity.getVersion().equals(originalEntity.getVersion())) {
      throw new InvalidRequestException(format(
          "Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] is not on the correct version.",
          inputSetEntity.getIdentifier(), inputSetEntity.getPipelineIdentifier(), inputSetEntity.getProjectIdentifier(),
          inputSetEntity.getOrgIdentifier()));
    }
    InputSetEntity entityToUpdate = originalEntity.withYaml(inputSetEntity.getYaml())
                                        .withName(inputSetEntity.getName())
                                        .withDescription(inputSetEntity.getDescription())
                                        .withTags(inputSetEntity.getTags())
                                        .withInputSetReferences(inputSetEntity.getInputSetReferences())
                                        .withIsInvalid(false)
                                        .withIsEntityInvalid(false);

    return makeInputSetUpdateCall(entityToUpdate, changeType, true);
  }

  @Override
  public InputSetEntity syncInputSetWithGit(EntityDetailProtoDTO entityDetail) {
    InputSetReferenceProtoDTO inputSetRef = entityDetail.getInputSetRef();
    String accountId = StringValueUtils.getStringFromStringValue(inputSetRef.getAccountIdentifier());
    String orgId = StringValueUtils.getStringFromStringValue(inputSetRef.getOrgIdentifier());
    String projectId = StringValueUtils.getStringFromStringValue(inputSetRef.getProjectIdentifier());
    String pipelineId = StringValueUtils.getStringFromStringValue(inputSetRef.getPipelineIdentifier());
    String inputSetId = StringValueUtils.getStringFromStringValue(inputSetRef.getIdentifier());
    Optional<InputSetEntity> optionalInputSetEntity;
    try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(null, false)) {
      optionalInputSetEntity = get(accountId, orgId, projectId, pipelineId, inputSetId, false);
    }
    if (!optionalInputSetEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] doesn't exist.", inputSetId,
              pipelineId, projectId, orgId));
    }
    return makeInputSetUpdateCall(optionalInputSetEntity.get().withStoreType(null), ChangeType.ADD, true);
  }

  @Override
  public boolean switchValidationFlag(InputSetEntity entity, boolean isInvalid) {
    Criteria criteria = new Criteria();
    criteria.and(InputSetEntityKeys.accountId)
        .is(entity.getAccountId())
        .and(InputSetEntityKeys.orgIdentifier)
        .is(entity.getOrgIdentifier())
        .and(InputSetEntityKeys.projectIdentifier)
        .is(entity.getProjectIdentifier())
        .and(InputSetEntityKeys.pipelineIdentifier)
        .is(entity.getPipelineIdentifier())
        .and(InputSetEntityKeys.identifier)
        .is(entity.getIdentifier());
    if (entity.getYamlGitConfigRef() != null) {
      criteria.and(InputSetEntityKeys.yamlGitConfigRef)
          .is(entity.getYamlGitConfigRef())
          .and(InputSetEntityKeys.branch)
          .is(entity.getBranch());
    }

    Update update = new Update();
    update.set(InputSetEntityKeys.isInvalid, isInvalid);
    InputSetEntity inputSetEntity = inputSetRepository.update(criteria, update);
    return inputSetEntity != null;
  }

  @Override
  public boolean markGitSyncedInputSetInvalid(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, String invalidYaml) {
    Optional<InputSetEntity> optionalInputSetEntity =
        get(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, false);
    if (!optionalInputSetEntity.isPresent()) {
      log.warn(String.format(
          "Marking input set [%s] as invalid failed as it does not exist or has been deleted", identifier));
      return false;
    }
    InputSetEntity existingInputSet = optionalInputSetEntity.get();
    InputSetEntity updatedInputSet = existingInputSet.withYaml(invalidYaml)
                                         .withObjectIdOfYaml(EntityObjectIdUtils.getObjectIdOfYaml(invalidYaml))
                                         .withIsEntityInvalid(true);
    makeInputSetUpdateCall(updatedInputSet, ChangeType.NONE, true);
    return true;
  }

  private InputSetEntity makeInputSetUpdateCall(InputSetEntity entity, ChangeType changeType, boolean isOldFlow) {
    try {
      InputSetEntity updatedEntity;
      if (isOldFlow) {
        updatedEntity = inputSetRepository.updateForOldGitSync(entity, InputSetYamlDTOMapper.toDTO(entity), changeType);
      } else {
        updatedEntity = inputSetRepository.update(entity);
      }
      if (updatedEntity == null) {
        throw new InvalidRequestException(
            format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] could not be updated.",
                entity.getIdentifier(), entity.getPipelineIdentifier(), entity.getProjectIdentifier(),
                entity.getOrgIdentifier()));
      }
      return updatedEntity;
    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while updating Input Set " + entity.getIdentifier(), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while updating input set [%s]", entity.getIdentifier()), e);
      throw new InvalidRequestException(
          String.format("Error while updating input set [%s]: %s", entity.getIdentifier(), e.getMessage()));
    }
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String identifier, Long version) {
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      return deleteForOldGitSync(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, version);
    }
    try {
      inputSetRepository.delete(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier);
      return true;
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("InputSet [%s] for Pipeline [%s] under Project[%s], Organization [%s] could not be deleted.",
              identifier, pipelineIdentifier, projectIdentifier, orgIdentifier));
    }
  }

  private boolean deleteForOldGitSync(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, Long version) {
    Optional<InputSetEntity> optionalOriginalEntity =
        get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, false);
    if (!optionalOriginalEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] doesn't exist.", identifier,
              pipelineIdentifier, projectIdentifier, orgIdentifier));
    }
    InputSetEntity existingEntity = optionalOriginalEntity.get();
    if (version != null && !version.equals(existingEntity.getVersion())) {
      throw new InvalidRequestException(format(
          "Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] is not on the correct version.",
          identifier, pipelineIdentifier, projectIdentifier, orgIdentifier));
    }
    InputSetEntity entityWithDelete = existingEntity.withDeleted(true);
    try {
      inputSetRepository.deleteForOldGitSync(entityWithDelete, InputSetYamlDTOMapper.toDTO(entityWithDelete));
      return true;
    } catch (Exception e) {
      log.error(String.format("Error while deleting input set [%s]", identifier), e);
      throw new InvalidRequestException(
          format("Input Set [%s], for pipeline [%s], under Project[%s], Organization [%s] couldn't be deleted.",
              identifier, pipelineIdentifier, projectIdentifier, orgIdentifier));
    }
  }

  @Override
  public Page<InputSetEntity> list(
      Criteria criteria, Pageable pageable, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return inputSetRepository.findAll(criteria, pageable, accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public void deleteInputSetsOnPipelineDeletion(PipelineEntity pipelineEntity) {
    Criteria criteria = new Criteria();
    criteria.and(InputSetEntityKeys.accountId)
        .is(pipelineEntity.getAccountId())
        .and(InputSetEntityKeys.orgIdentifier)
        .is(pipelineEntity.getOrgIdentifier())
        .and(InputSetEntityKeys.projectIdentifier)
        .is(pipelineEntity.getProjectIdentifier())
        .and(InputSetEntityKeys.pipelineIdentifier)
        .is(pipelineEntity.getIdentifier());
    Query query = new Query(criteria);
    try {
      inputSetRepository.deleteAllInputSetsWhenPipelineDeleted(query);
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("InputSets for Pipeline [%s] under Project[%s], Organization [%s] couldn't be deleted.",
              pipelineEntity.getIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier()),
          e);
    }
  }

  @Override
  public InputSetEntity updateGitFilePath(InputSetEntity inputSetEntity, String newFilePath) {
    Criteria criteria = Criteria.where(InputSetEntityKeys.accountId)
                            .is(inputSetEntity.getAccountId())
                            .and(InputSetEntityKeys.orgIdentifier)
                            .is(inputSetEntity.getOrgIdentifier())
                            .and(InputSetEntityKeys.projectIdentifier)
                            .is(inputSetEntity.getProjectIdentifier())
                            .and(InputSetEntityKeys.pipelineIdentifier)
                            .is(inputSetEntity.getPipelineIdentifier())
                            .and(InputSetEntityKeys.identifier)
                            .is(inputSetEntity.getIdentifier());

    GitEntityFilePath gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(newFilePath);
    Update update = new Update()
                        .set(InputSetEntityKeys.filePath, gitEntityFilePath.getFilePath())
                        .set(InputSetEntityKeys.rootFolder, gitEntityFilePath.getRootFolder());
    return inputSetRepository.update(inputSetEntity.getAccountId(), inputSetEntity.getOrgIdentifier(),
        inputSetEntity.getProjectIdentifier(), criteria, update);
  }

  @Override
  public boolean checkForInputSetsForPipeline(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return inputSetRepository.existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, true);
  }
}
