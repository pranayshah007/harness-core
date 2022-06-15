/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.ng.core.EntityDetail.EntityDetailKeys;
import static io.harness.ng.core.Resource.ResourceKeys;
import static io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage.EntitySetupUsageKeys;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.beans.SearchPageParams;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.FileReferenceService;
import io.harness.filestore.service.FileStructureService;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class FileReferenceServiceImpl implements FileReferenceService {
  public static final String REFERRED_BY_IDENTIFIER_KEY =
      EntitySetupUsageKeys.referredByEntity + "." + EntityDetailKeys.entityRef + "." + ResourceKeys.identifier;

  private final EntitySetupUsageService entitySetupUsageService;
  private final FileStructureService fileStructureService;

  @Override
  public Long countEntitiesReferencingFile(NGFile file) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(
        file.getIdentifier(), file.getAccountIdentifier(), file.getOrgIdentifier(), file.getProjectIdentifier());
    String referredEntityFQN = identifierRef.getFullyQualifiedName();
    try {
      return entitySetupUsageService.referredByEntityCount(
          file.getAccountIdentifier(), referredEntityFQN, EntityType.FILES);
    } catch (Exception ex) {
      log.error("Encountered exception while requesting the Entity Reference records of [{}], with exception.",
          file.getIdentifier(), ex);
      throw new UnexpectedException("Error while verifying file is referenced by other entities.", ex);
    }
  }

  @Override
  public Page<EntitySetupUsageDTO> getReferencedBy(SearchPageParams pageParams, NGFile file, EntityType entityType) {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(file.getAccountIdentifier())
                                      .orgIdentifier(file.getOrgIdentifier())
                                      .projectIdentifier(file.getProjectIdentifier())
                                      .identifier(file.getIdentifier())
                                      .build();
    String referredEntityFQN = identifierRef.getFullyQualifiedName();
    return entitySetupUsageService.listAllEntityUsage(pageParams.getPage(), pageParams.getSize(),
        file.getAccountIdentifier(), referredEntityFQN, EntityType.FILES, entityType, pageParams.getSearchTerm(),
        Sort.by(Sort.Direction.ASC, REFERRED_BY_IDENTIFIER_KEY));
  }

  @Override
  public void validateReferenceByAndThrow(NGFile fileOrFolder) {
    if (NGFileType.FOLDER.equals(fileOrFolder.getType())) {
      List<String> folderChildrenFQNs = fileStructureService.listFolderChildrenFQNs(fileOrFolder);

      Long count = entitySetupUsageService.countReferredByEntitiesByFQNsIn(
          fileOrFolder.getAccountIdentifier(), folderChildrenFQNs);
      if (count > 0L) {
        throw new ReferencedEntityException(format(
            "Folder [%s], or its subfolders, contain file(s) referenced by %s other entities and can not be deleted.",
            fileOrFolder.getIdentifier(), count));
      }
    } else {
      Long count = countEntitiesReferencingFile(fileOrFolder);

      if (count > 0L) {
        throw new ReferencedEntityException(
            format("File [%s] is referenced by %s other entities and can not be deleted.", fileOrFolder.getIdentifier(),
                count));
      }
    }
  }

  public List<EntitySetupUsageDTO> getAllReferencedByInScope(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SearchPageParams pageParams, EntityType entityType, String referredByEntityName) {
    String referredEntityFQScope = IdentifierRef.builder()
                                       .accountIdentifier(accountIdentifier)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build()
                                       .getFullyQualifiedScopeIdentifier();
    return entitySetupUsageService.listAllEntityUsagePerReferredEntityScope(
        Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), referredEntityFQScope, EntityType.FILES,
        entityType, referredByEntityName, Sort.by(Sort.Direction.ASC, EntitySetupUsageKeys.referredByEntityName));
  }

  public List<String> getAllFileIdentifiersReferencedByInScope(
      Scope scope, EntityType entityType, String referredByEntityName) {
    String referredEntityFQScope = IdentifierRef.builder()
                                       .accountIdentifier(scope.getAccountIdentifier())
                                       .orgIdentifier(scope.getOrgIdentifier())
                                       .projectIdentifier(scope.getProjectIdentifier())
                                       .build()
                                       .getFullyQualifiedScopeIdentifier();
    List<EntitySetupUsageDTO> referredFiles =
        entitySetupUsageService.listAllEntityUsagePerReferredEntityScope(scope, referredEntityFQScope, EntityType.FILES,
            entityType, referredByEntityName, Sort.by(Sort.Direction.ASC, EntitySetupUsageKeys.referredByEntityName));

    return referredFiles.stream()
        .filter(i -> i.getReferredEntity() != null && i.getReferredEntity().getEntityRef() != null)
        .map(i -> i.getReferredEntity().getEntityRef().getIdentifier())
        .distinct()
        .collect(Collectors.toList());
  }

  public List<String> listAllReferredFileUsageIdentifiers(String accountIdentifier, String referredByEntityFQN) {
    try {
      return entitySetupUsageService.listAllReferredUsages(accountIdentifier, referredByEntityFQN, EntityType.FILES)
          .stream()
          .map(entity -> entity.getReferredEntity().getEntityRef().getIdentifier())
          .collect(Collectors.toList());

    } catch (Exception e) {
      log.error("Cannot fetch file identifiers from referenced object.", e);
      return Collections.emptyList();
    }
  }
}
