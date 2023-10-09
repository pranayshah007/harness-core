/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.events.TemplateUpdateEventType;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
public interface NGGlobalTemplateRepositoryCustom {
  boolean globalTemplateExistByIdentifierWithoutVersionLabel(String templateIdentifier);
  boolean globalTemplateExistByIdentifierAndVersionLabel(String templateIdentifier, String versionLabel);
  Optional<GlobalTemplateEntity> findGlobalTemplateByIdentifierAndIsStableAndDeletedNot(
      String templateIdentifier, boolean notDeleted, boolean getMetadataOnly);
  Optional<GlobalTemplateEntity> getGlobalEntityUsingVersionLabel(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean notDeleted,
      boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch);
  GlobalTemplateEntity save(GlobalTemplateEntity templateToSave, String comments) throws InvalidRequestException;

  Page<GlobalTemplateEntity> findAllGlobalTemplateAndDeletedNot(
      boolean notDeleted, boolean getMetadataOnly, Pageable pageable);
  GlobalTemplateEntity updateIsStableTemplate(GlobalTemplateEntity globalTemplateEntity, boolean value);
  Page<GlobalTemplateEntity> findAll(String accountIdentifier, Criteria criteria, Pageable pageable);
  GlobalTemplateEntity updateTemplateInDb(GlobalTemplateEntity templateEntity, GlobalTemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits);
  Optional<GlobalTemplateEntity> findByFilePath(String filePath);
}
