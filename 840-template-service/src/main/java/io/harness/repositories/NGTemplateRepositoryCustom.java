/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.git.model.ChangeType;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.events.TemplateUpdateEventType;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(CDC)
public interface NGTemplateRepositoryCustom {
  TemplateEntity save(TemplateEntity templateToSave, String comments);

  Optional<TemplateEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel,
      boolean notDeleted);

  Optional<TemplateEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted);

  Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted);

  TemplateEntity updateTemplateYaml(TemplateEntity templateEntity, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits);

  TemplateEntity deleteTemplate(TemplateEntity templateToDelete, String comments);

  void hardDeleteTemplate(TemplateEntity templateEntity, String comments);

  Page<TemplateEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, boolean getDistinctFromBranches);

  boolean existsByAccountIdAndOrgIdAndProjectIdAndIdentifierAndVersionLabel(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel);

  TemplateEntity update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update);
}
