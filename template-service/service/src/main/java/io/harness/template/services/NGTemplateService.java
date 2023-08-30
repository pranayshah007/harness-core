/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.TemplateMoveConfigRequestDTO;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateWithInputsResponseDTO;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.resources.beans.FilterParamsDTO;
import io.harness.template.resources.beans.PageParamsDTO;
import io.harness.template.resources.beans.TemplateImportRequestDTO;
import io.harness.template.resources.beans.TemplateListRepoResponse;
import io.harness.template.resources.beans.TemplateMoveConfigResponse;
import io.harness.template.resources.beans.UpdateGitDetailsParams;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(CDC)
public interface NGTemplateService {
  TemplateEntity create(
      TemplateEntity templateEntity, boolean setDefaultTemplate, String comments, boolean isNewTemplate);

  TemplateEntity updateTemplateEntity(
      TemplateEntity templateEntity, ChangeType changeType, boolean setDefaultTemplate, String comments);

  TemplateEntity updateTemplateEntity(TemplateEntity templateEntity, ChangeType changeType, boolean setDefaultTemplate,
      String comments, TemplateResponseDTO templateResponse);

  Optional<TemplateEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, boolean deleted, boolean loadFromCache);

  Optional<TemplateEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, boolean deleted, boolean loadFromCache,
      boolean loadFromFallbackBranch);

  Optional<TemplateEntity> getMetadataOrThrowExceptionIfInvalid(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean deleted);

  TemplateWithInputsResponseDTO getTemplateWithInputs(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, boolean loadFromCache);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier,
      String versionLabel, Long version, String comments, boolean forceDelete);

  boolean deleteTemplates(String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier,
      Set<String> templateVersions, String comments, boolean forceDelete);

  Page<TemplateEntity> list(Criteria criteria, Pageable pageable, String accountId, String orgIdentifier,
      String projectIdentifier, Boolean getDistinctFromBranches);

  Page<TemplateEntity> listTemplateMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      FilterParamsDTO filterParamsDTO, PageParamsDTO pageParamsDTO);

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

  boolean validateIsNewTemplateIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String templateIdentifier);

  TemplateEntity updateGitFilePath(TemplateEntity templateEntity, String newFilePath);

  void checkLinkedTemplateAccess(
      String accountId, String orgId, String projectId, TemplateMergeResponseDTO templateMergeResponseDTO);

  boolean deleteAllTemplatesInAProject(String accountId, String orgId, String projectId);

  boolean deleteAllOrgLevelTemplates(String accountId, String orgId);

  TemplateEntity importTemplateFromRemote(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, TemplateImportRequestDTO templateImportRequest, boolean isForceImport);

  TemplateListRepoResponse getListOfRepos(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      boolean includeAllTemplatesAccessibleAtScope);

  PageResponse<EntitySetupUsageDTO> listTemplateReferences(int page, int size, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel, String searchTerm,
      boolean isStableTemplate);

  TemplateMoveConfigResponse moveTemplateStoreTypeConfig(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, TemplateMoveConfigRequestDTO templateMoveConfigRequestDTO);

  void updateGitDetails(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, UpdateGitDetailsParams updateGitDetailsParams);
  GovernanceMetadata validateGovernanceRules(TemplateEntity templateEntity);
  void populateSetupUsageAsync(TemplateEntity templateEntity);
}