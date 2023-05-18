/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.git.model.ChangeType;
import io.harness.pms.inputset.gitsync.InputSetYamlDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.pipeline.MoveConfigOperationType;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public interface PMSInputSetRepositoryCustom {
  List<InputSetEntity> findAll(Criteria criteria);

  Page<InputSetEntity> findAll(
      Criteria criteria, Pageable pageable, String accountIdentifier, String orgIdentifier, String projectIdentifier);

  InputSetEntity saveForOldGitSync(InputSetEntity entityToSave, InputSetYamlDTO yamlDTO);

  InputSetEntity save(InputSetEntity entityToSave);

  InputSetEntity saveForImportedYAML(InputSetEntity entityToSave);

  Optional<InputSetEntity> findForOldGitSync(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean notDeleted);

  Optional<InputSetEntity> find(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean notDeleted, boolean getMetadataOnly,
      boolean loadFromFallbackBranch, boolean loadFromCache);

  InputSetEntity updateForOldGitSync(InputSetEntity entityToUpdate, InputSetYamlDTO yamlDTO, ChangeType changeType);

  InputSetEntity update(InputSetEntity entityToUpdate);

  InputSetEntity update(Criteria criteria, Update update);

  InputSetEntity update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update);

  void deleteForOldGitSync(InputSetEntity entityToDelete, InputSetYamlDTO yamlDTO);

  void delete(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String identifier);

  void deleteAllInputSetsWhenPipelineDeleted(Query query);

  boolean existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted);

  boolean checkIfInputSetWithGivenFilePathExists(String accountId, String repoURL, String filePath);

  InputSetEntity updateInputSetEntity(
      InputSetEntity inputSetToMove, Criteria criteria, Update update, MoveConfigOperationType moveConfigOperationType);

  List<String> findAllUniqueInputSetRepos(@NotNull Criteria criteria);

  InputSetEntity updateEntity(Criteria criteria, Update update);
}
