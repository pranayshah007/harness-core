/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import com.fasterxml.jackson.databind.JsonNode;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.template.entity.TemplateEntity;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
public interface NGTemplateService {
  TemplateEntity create(TemplateEntity templateEntity, boolean setDefaultTemplate, String comments);

  TemplateEntity updateTemplateEntity(
      TemplateEntity templateEntity, ChangeType changeType, boolean setDefaultTemplate, String comments);

  Optional<TemplateEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, boolean deleted);

  Optional<TemplateEntity> getOrThrowExceptionIfInvalid(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean deleted);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier,
      String versionLabel, Long version, String comments);

  boolean deleteTemplates(String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier,
      Set<String> templateVersions, String comments);

  Page<TemplateEntity> list(Criteria criteria, Pageable pageable, String accountId, String orgIdentifier,
      String projectIdentifier, Boolean getDistinctFromBranches);

  TemplateEntity updateStableTemplateVersion(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String newStableTemplateVersion, String comments);

  boolean updateTemplateSettings(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, Scope currentScope, Scope updateScope, String updateStableTemplateVersion,
      Boolean getDistinctFromBranches);

  boolean markEntityInvalid(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, String invalidYaml);

  TemplateEntity fullSyncTemplate(EntityDetailProtoDTO entityDetailProtoDTO);

  boolean validateIdentifierIsUnique(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel);

  TemplateEntity updateGitFilePath(TemplateEntity templateEntity, String newFilePath);

    JsonNode getTemplateSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier, String yamlGroup, Scope scope, EntityType entityType, TemplateEntityType templateEntityType);
}
