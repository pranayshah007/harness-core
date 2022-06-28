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
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.pipeline.PipelineEntity;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public interface PMSInputSetService {
  InputSetEntity create(InputSetEntity inputSetEntity);

  Optional<InputSetEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean deleted);

  InputSetEntity update(InputSetEntity inputSetEntity, ChangeType changeType);

  InputSetEntity syncInputSetWithGit(EntityDetailProtoDTO entityDetail);

  boolean switchValidationFlag(InputSetEntity entity, boolean isInvalid);

  boolean markGitSyncedInputSetInvalid(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, String invalidYaml);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String identifier, Long version);

  Page<InputSetEntity> list(
      Criteria criteria, Pageable pageable, String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void deleteInputSetsOnPipelineDeletion(PipelineEntity pipelineEntity);

  InputSetEntity updateGitFilePath(InputSetEntity inputSetEntity, String newFilePath);

  boolean checkForInputSetsForPipeline(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  InputSetEntity importInputSetFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetIdentifier, InputSetImportRequestDTO inputSetImportRequestDTO);
}
