/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.filters;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.gitsync.beans.StoreType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;

import java.util.LinkedList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@UtilityClass
public class PMSPipelineFilterHelper {
  public Update getUpdateOperations(PipelineEntity pipelineEntity, long timestamp) {
    Update update = new Update();
    update.set(PipelineEntityKeys.yaml, pipelineEntity.getYaml());
    update.set(PipelineEntityKeys.lastUpdatedAt, timestamp);
    update.set(PipelineEntityKeys.deleted, false);
    update.set(PipelineEntityKeys.name, pipelineEntity.getName());
    update.set(PipelineEntityKeys.description, pipelineEntity.getDescription());
    update.set(PipelineEntityKeys.tags, pipelineEntity.getTags());
    update.set(PipelineEntityKeys.filters, pipelineEntity.getFilters());
    update.set(PipelineEntityKeys.stageCount, pipelineEntity.getStageCount());
    update.set(PipelineEntityKeys.stageNames, pipelineEntity.getStageNames());
    update.set(PipelineEntityKeys.allowStageExecutions, pipelineEntity.getAllowStageExecutions());
    update.set(PipelineEntityKeys.harnessVersion, pipelineEntity.getHarnessVersion());
    return update;
  }

  public PipelineEntity updateFieldsInDBEntry(
      PipelineEntity entityFromDB, PipelineEntity fieldsToUpdate, long timeOfUpdate) {
    return entityFromDB.withYaml(fieldsToUpdate.getYaml())
        .withLastUpdatedAt(timeOfUpdate)
        .withName(fieldsToUpdate.getName())
        .withDescription(fieldsToUpdate.getDescription())
        .withTags(fieldsToUpdate.getTags())
        .withFilters(fieldsToUpdate.getFilters())
        .withStageCount(fieldsToUpdate.getStageCount())
        .withStageNames(fieldsToUpdate.getStageNames())
        .withAllowStageExecutions(fieldsToUpdate.getAllowStageExecutions())
        .withVersion(entityFromDB.getVersion() == null ? 1 : entityFromDB.getVersion() + 1);
  }

  public Update getUpdateOperationsForOnboardingToInline() {
    Update update = new Update();
    update.set(PipelineEntityKeys.storeType, StoreType.INLINE);
    return update;
  }

  public Criteria getCriteriaForFind(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier, boolean notDeleted) {
    return Criteria.where(PipelineEntityKeys.accountId)
        .is(accountId)
        .and(PipelineEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(PipelineEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(PipelineEntityKeys.identifier)
        .is(identifier)
        .and(PipelineEntityKeys.deleted)
        .is(!notDeleted);
  }

  public Criteria getCriteriaForAllPipelinesInProject(
      String accountId, String orgIdentifier, String projectIdentifier) {
    return Criteria.where(PipelineEntityKeys.accountId)
        .is(accountId)
        .and(PipelineEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(PipelineEntityKeys.projectIdentifier)
        .is(projectIdentifier);
  }

  public Criteria getCriteriaForFileUniquenessCheck(String accountId, String repoURl, String filePath) {
    return Criteria.where(PipelineEntityKeys.accountId)
        .is(accountId)
        .and(PipelineEntityKeys.repoURL)
        .is(repoURl)
        .and(PipelineEntityKeys.filePath)
        .is(filePath);
  }

  public List<String> getPipelineNonMetadataFields() {
    List<String> fields = new LinkedList<>();
    fields.add(PipelineEntityKeys.yaml);
    return fields;
  }

  public Update getUpdateWithGitMetadata(PMSUpdateGitDetailsParams updateGitDetailsParams) {
    Update update = new Update();

    if (isNotEmpty(updateGitDetailsParams.getConnectorRef())) {
      update.set(PipelineEntityKeys.connectorRef, updateGitDetailsParams.getConnectorRef());
    }
    if (isNotEmpty(updateGitDetailsParams.getRepoName())) {
      update.set(PipelineEntityKeys.repo, updateGitDetailsParams.getRepoName());
    }
    if (isNotEmpty(updateGitDetailsParams.getFilePath())) {
      update.set(PipelineEntityKeys.filePath, updateGitDetailsParams.getFilePath());
    }
    if (!update.getUpdateObject().isEmpty()) {
      update.set(PipelineEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    }
    return update;
  }
}
