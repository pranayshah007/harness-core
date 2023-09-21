/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.filters;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
public class PMSPipelineFilterHelper {
  public Update getUpdateOperationsForV1(
      PipelineEntity pipelineEntity, long timestamp, Map<String, Object> fieldsToUpdate) {
    Update update = new Update();
    update.set(PipelineEntityKeys.yaml, pipelineEntity.getYaml());
    update.set(PipelineEntityKeys.lastUpdatedAt, timestamp);
    update.set(PipelineEntityKeys.deleted, false);
    if (fieldsToUpdate != null) {
      if (fieldsToUpdate.containsKey("name")) {
        update.set(PipelineEntityKeys.name, pipelineEntity.getName());
      }
      if (fieldsToUpdate.containsKey("description")) {
        update.set(PipelineEntityKeys.description, pipelineEntity.getName());
      }
      if (fieldsToUpdate.containsKey("tags")) {
        update.set(PipelineEntityKeys.tags, pipelineEntity.getName());
      }
      if (fieldsToUpdate.containsKey("filters")) {
        update.set(PipelineEntityKeys.filters, pipelineEntity.getName());
      }
      if (fieldsToUpdate.containsKey("stageCount")) {
        update.set(PipelineEntityKeys.stageCount, pipelineEntity.getName());
      }
      if (fieldsToUpdate.containsKey("stageNames")) {
        update.set(PipelineEntityKeys.stageNames, pipelineEntity.getName());
      }
    }
    update.set(PipelineEntityKeys.harnessVersion, pipelineEntity.getHarnessVersion());
    return update;
  }

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

  public PipelineEntity updateFieldsInDBEntryForV1(PipelineEntity entityFromDB, PipelineEntity newPipelineEntity,
      long timeOfUpdate, Map<String, Object> fieldsToUpdate) {
    PipelineEntity pipelineEntity =
        entityFromDB.withYaml(newPipelineEntity.getYaml())
            .withLastUpdatedAt(timeOfUpdate)
            .withVersion(entityFromDB.getVersion() == null ? 1 : entityFromDB.getVersion() + 1);
    if (fieldsToUpdate != null) {
      if (fieldsToUpdate.containsKey("name")) {
        pipelineEntity = pipelineEntity.withName(newPipelineEntity.getName());
      }
      if (fieldsToUpdate.containsKey("description")) {
        pipelineEntity = pipelineEntity.withDescription(newPipelineEntity.getDescription());
      }
      if (fieldsToUpdate.containsKey("tags")) {
        pipelineEntity = pipelineEntity.withTags(newPipelineEntity.getTags());
      }
      if (fieldsToUpdate.containsKey("filters")) {
        pipelineEntity = pipelineEntity.withFilters(newPipelineEntity.getFilters());
      }
      if (fieldsToUpdate.containsKey("stageCount")) {
        pipelineEntity = pipelineEntity.withStageCount(newPipelineEntity.getStageCount());
      }
      if (fieldsToUpdate.containsKey("stageNames")) {
        pipelineEntity = pipelineEntity.withStageNames(newPipelineEntity.getStageNames());
      }
    }
    return pipelineEntity;
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
