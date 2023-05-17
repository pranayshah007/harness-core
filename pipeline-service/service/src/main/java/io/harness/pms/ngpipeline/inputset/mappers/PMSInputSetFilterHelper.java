/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;

import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@UtilityClass
public class PMSInputSetFilterHelper {
  public Criteria createCriteriaForGetListForBranchAndRepo(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, InputSetListTypePMS type) {
    Criteria criteria =
        createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, type, null, false);
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo != null) {
      Criteria gitSyncCriteria = new Criteria()
                                     .and(InputSetEntityKeys.branch)
                                     .is(gitEntityInfo.getBranch())
                                     .and(InputSetEntityKeys.yamlGitConfigRef)
                                     .is(gitEntityInfo.getYamlGitConfigId());
      criteria.andOperator(gitSyncCriteria);
    }
    return criteria;
  }

  public Criteria createCriteriaForGetList(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, InputSetListTypePMS type, String searchTerm, boolean deleted) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      criteria.and(InputSetEntityKeys.accountId).is(accountId);
    }
    if (isNotEmpty(orgIdentifier)) {
      criteria.and(InputSetEntityKeys.orgIdentifier).is(orgIdentifier);
    }
    if (isNotEmpty(projectIdentifier)) {
      criteria.and(InputSetEntityKeys.projectIdentifier).is(projectIdentifier);
    }
    if (isNotEmpty(pipelineIdentifier)) {
      criteria.and(InputSetEntityKeys.pipelineIdentifier).is(pipelineIdentifier);
    }
    criteria.and(InputSetEntityKeys.deleted).is(deleted);

    if (type != InputSetListTypePMS.ALL) {
      criteria.and(InputSetEntityKeys.inputSetEntityType).is(getInputSetType(type));
    }

    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(
          where(InputSetEntityKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(InputSetEntityKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      criteria.andOperator(searchCriteria);
    }

    return criteria;
  }

  private InputSetEntityType getInputSetType(InputSetListTypePMS inputSetListType) {
    if (inputSetListType == InputSetListTypePMS.INPUT_SET) {
      return InputSetEntityType.INPUT_SET;
    }
    return InputSetEntityType.OVERLAY_INPUT_SET;
  }

  public Update getUpdateOperations(InputSetEntity inputSetEntity, long timeOfUpdate) {
    Update update = new Update();
    update.set(InputSetEntityKeys.yaml, inputSetEntity.getYaml());
    update.set(InputSetEntityKeys.name, inputSetEntity.getName());
    update.set(InputSetEntityKeys.description, inputSetEntity.getDescription());
    update.set(InputSetEntityKeys.tags, inputSetEntity.getTags());
    update.set(InputSetEntityKeys.inputSetReferences, inputSetEntity.getInputSetReferences());
    update.set(InputSetEntityKeys.lastUpdatedAt, timeOfUpdate);
    update.set(InputSetEntityKeys.deleted, false);
    update.set(InputSetEntityKeys.isInvalid, false);
    return update;
  }

  public InputSetEntity updateFieldsInDBEntry(
      InputSetEntity entityFromDB, InputSetEntity fieldsToUpdate, long timeOfUpdate) {
    return entityFromDB.withYaml(fieldsToUpdate.getYaml())
        .withName(fieldsToUpdate.getName())
        .withDescription(fieldsToUpdate.getDescription())
        .withTags(fieldsToUpdate.getTags())
        .withInputSetReferences(fieldsToUpdate.getInputSetReferences())
        .withLastUpdatedAt(timeOfUpdate)
        .withDeleted(false)
        .withIsInvalid(false)
        .withVersion(entityFromDB.getVersion() == null ? 1 : entityFromDB.getVersion() + 1);
  }

  public Update getUpdateOperationsForOnboardingToInline() {
    Update update = new Update();
    update.set(InputSetEntityKeys.storeType, StoreType.INLINE);
    return update;
  }

  public Update getUpdateOperationsForDelete() {
    Update update = new Update();
    update.set(InputSetEntityKeys.deleted, true);
    return update;
  }

  public Criteria getCriteriaForFind(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean notDeleted) {
    return Criteria.where(InputSetEntityKeys.deleted)
        .is(!notDeleted)
        .and(InputSetEntityKeys.accountId)
        .is(accountId)
        .and(InputSetEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(InputSetEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(InputSetEntityKeys.pipelineIdentifier)
        .is(pipelineIdentifier)
        .and(InputSetEntityKeys.identifier)
        .is(identifier);
  }

  public Criteria buildCriteriaForRepoListing(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return Criteria.where(InputSetEntityKeys.accountId)
        .is(accountId)
        .and(InputSetEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(InputSetEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(InputSetEntityKeys.pipelineIdentifier)
        .is(pipelineIdentifier);
  }

  public Update getUpdateWithGitMetadata(PMSUpdateGitDetailsParams updateGitDetailsParams) {
    Update update = new Update();

    if (updateGitDetailsParams.getConnectorRef() != null) {
      update.set(InputSetEntityKeys.connectorRef, updateGitDetailsParams.getConnectorRef());
    }
    if (updateGitDetailsParams.getRepoName() != null) {
      update.set(InputSetEntityKeys.repo, updateGitDetailsParams.getRepoName());
    }
    if (updateGitDetailsParams.getFilePath() != null) {
      update.set(InputSetEntityKeys.filePath, updateGitDetailsParams.getFilePath());
    }
    return update;
  }
}
