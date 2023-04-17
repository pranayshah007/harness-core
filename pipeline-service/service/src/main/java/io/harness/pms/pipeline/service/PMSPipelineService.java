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
import io.harness.git.model.ChangeType;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.pipeline.ClonePipelineDTO;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.MoveConfigOperationDTO;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepPalleteFilterWrapper;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public interface PMSPipelineService {
  /**
   * Create pipeline (inline/remote) and do validation - template resolution,
   * schema validation and governance (opa) checks
   *
   * @param pipelineEntity
   * @param throwExceptionIfGovernanceFails
   * @return
   */
  PipelineCRUDResult validateAndCreatePipeline(PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceFails);

  /**
   * Clone pipeline (inline/remote) and do validation - template resolution,
   * schema validation and governance (opa) checks
   *
   * @param clonePipelineDTO
   * @param accountId
   * @return
   */
  PipelineSaveResponse validateAndClonePipeline(ClonePipelineDTO clonePipelineDTO, String accountId);

  /**
   * Get pipeline (inline/remote) and do validation - template resolution,
   * schema validation and governance (opa) checks
   *
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param identifier
   * @param deleted
   * @return
   */
  Optional<PipelineEntity> getAndValidatePipeline(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier, boolean deleted);

  PipelineGetResult getAndValidatePipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache, boolean validateAsync);

  //  TODO: the variable loadFromFallbackBranch will be enforced upon to all users and this will be removed: @Adithya
  Optional<PipelineEntity> getAndValidatePipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean loadFromFallbackBranch, boolean loadFromCache);

  /**
   * Get pipeline whether inline or remote (old/new git exp)
   *
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param identifier
   * @param deleted
   * @param getMetadataOnly
   * @return
   */
  Optional<PipelineEntity> getPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly);

  Optional<PipelineEntity> getPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache);

  PipelineEntity getPipelineMetadata(String accountId, String orgIdentifier, String projectIdentifier,
      String identifier, boolean deleted, boolean getMetadataOnly);

  /**
   * Update pipeline (inline/remote) after doing validation - template resolution,
   * schema validation and governance (opa) checks
   *
   * @param pipelineEntity
   * @param changeType
   * @param throwExceptionIfGovernanceFails
   * @return
   */
  PipelineCRUDResult validateAndUpdatePipeline(
      PipelineEntity pipelineEntity, ChangeType changeType, boolean throwExceptionIfGovernanceFails);

  PipelineEntity syncPipelineEntityWithGit(EntityDetailProtoDTO entityDetail);

  PipelineEntity updatePipelineMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, Criteria criteria, Update updateOperations);

  void saveExecutionInfo(
      String accountId, String orgId, String projectId, String pipelineId, ExecutionSummaryInfo executionSummaryInfo);

  boolean markEntityInvalid(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String invalidYaml);

  boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, Long version);

  Page<PipelineEntity> list(Criteria criteria, Pageable pageable, String accountId, String orgIdentifier,
      String projectIdentifier, Boolean getDistinctFromBranches);

  PipelineEntity importPipelineFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, Boolean isForceImport);

  Long countAllPipelines(Criteria criteria);

  StepCategory getSteps(String module, String category, String accountId);

  StepCategory getStepsV2(String accountId, StepPalleteFilterWrapper stepPalleteFilterWrapper);

  boolean deleteAllPipelinesInAProject(String accountId, String orgId, String projectId);

  String fetchExpandedPipelineJSON(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  PipelineEntity updateGitFilePath(PipelineEntity pipelineEntity, String newFilePath);

  String pipelineVersion(String accountId, String yaml);

  PMSPipelineListRepoResponse getListOfRepos(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  PipelineCRUDResult moveConfig(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, MoveConfigOperationDTO moveConfigDTO);
}
