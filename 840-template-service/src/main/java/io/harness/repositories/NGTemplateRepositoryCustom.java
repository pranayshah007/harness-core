/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.events.TemplateUpdateEventType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(CDC)
public interface NGTemplateRepositoryCustom {
  TemplateEntity saveForOldGitSync(TemplateEntity templateToSave, String comments);

  TemplateEntity save(TemplateEntity templateToSave, String comments) throws InvalidRequestException;

  Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel,
      boolean notDeleted);

  Optional<TemplateEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel,
      boolean notDeleted, boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch);

  Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNotForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted);

  Optional<TemplateEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted,
      boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch);

  Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNotForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted);

  Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(String accountId,
      String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted,
      boolean getMetadataOnly);

  TemplateEntity updateTemplateYamlForOldGitSync(TemplateEntity templateEntity, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits);

  TemplateEntity updateTemplateInDb(TemplateEntity templateEntity, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits);

  TemplateEntity updateTemplateYaml(TemplateEntity templateEntity, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits);

  void hardDeleteTemplateForOldGitSync(TemplateEntity templateEntity, String comments, boolean forceDelete);

  void deleteTemplate(TemplateEntity templateEntity, String comments, boolean forceDelete);

  Page<TemplateEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, boolean getDistinctFromBranches);

  Page<TemplateEntity> findAll(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Pageable pageable);

  boolean existsByAccountIdAndOrgIdAndProjectIdAndIdentifierAndVersionLabel(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel);

  boolean existsByAccountIdAndOrgIdAndProjectIdAndIdentifierWithoutVersionLabel(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier);

  TemplateEntity update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update);

  TemplateEntity updateIsStableTemplate(TemplateEntity templateEntity, boolean value);

  TemplateEntity updateIsLastUpdatedTemplate(TemplateEntity templateEntity, boolean value);

  boolean deleteAllTemplatesInAProject(String accountId, String orgId, String projectId);

  boolean deleteAllOrgLevelTemplates(String accountId, String orgId);

  Long countFileInstances(String accountIdentifier, String repoURL, String filePath);

  TemplateEntity importFlowSaveTemplate(TemplateEntity templateEntity, String comments);

  List<String> getListOfRepos(Criteria criteria);
}
