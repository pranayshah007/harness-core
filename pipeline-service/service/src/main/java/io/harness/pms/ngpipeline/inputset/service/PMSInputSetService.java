/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.git.model.ChangeType;
import io.harness.pms.inputset.InputSetMoveConfigOperationDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.gitsync.PMSUpdateGitDetailsParams;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public interface PMSInputSetService {
  // pipeline branch and repo ID are needed for old git sync
  InputSetEntity create(InputSetEntity inputSetEntity, boolean hasNewYamlStructure);

  Optional<InputSetEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean deleted, String pipelineBranch, String pipelineRepoID,
      boolean hasNewYamlStructure, boolean loadFromFallbackBranch, boolean loadFromCache);

  Optional<InputSetEntity> getWithoutValidations(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean deleted, boolean loadFromFallbackBranch,
      boolean loadFromCache);

  Optional<InputSetEntity> getMetadataWithoutValidations(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String identifier, boolean deleted,
      boolean loadFromFallbackBranch, boolean getMetadata);

  InputSetEntity getMetadata(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean deleted, boolean loadFromFallbackBranch,
      boolean getMetadata);

  // pipeline branch and repo ID are needed for old git sync
  InputSetEntity update(ChangeType changeType, InputSetEntity inputSetEntity, boolean hasNewYamlStructure);

  InputSetEntity syncInputSetWithGit(EntityDetailProtoDTO entityDetail);

  boolean switchValidationFlag(InputSetEntity entity, boolean isInvalid);

  boolean markGitSyncedInputSetInvalid(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, String invalidYaml);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String identifier, Long version);

  Page<InputSetEntity> list(
      Criteria criteria, Pageable pageable, String accountIdentifier, String orgIdentifier, String projectIdentifier);

  List<InputSetEntity> list(Criteria criteria);

  void deleteInputSetsOnPipelineDeletion(PipelineEntity pipelineEntity);

  InputSetEntity updateGitFilePath(InputSetEntity inputSetEntity, String newFilePath);

  boolean checkForInputSetsForPipeline(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  InputSetEntity importInputSetFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, InputSetImportRequestDTO inputSetImportRequestDTO,
      boolean isForceImport);

  InputSetEntity moveConfig(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String inputSetIdentifier, InputSetMoveConfigOperationDTO inputSetMoveConfigOperationDTO);

  PMSInputSetListRepoResponse getListOfRepos(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  String updateGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, PMSUpdateGitDetailsParams updateGitDetailsParams);
}
