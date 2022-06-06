/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import io.harness.EntityType;
import io.harness.beans.Scope;
import io.harness.beans.SearchPageParams;
import io.harness.exception.ReferencedEntityException;
import io.harness.filestore.entities.NGFile;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;

import java.util.List;
import org.springframework.data.domain.Page;

public interface FileReferenceService {
  /**
   * Check how many other entities have reference to the file
   *
   * @param file the file
   * @return if the file is referenced by other entities
   */
  Long countEntitiesReferencingFile(NGFile file);

  /**
   * The list of entities where the file is referenced
   *
   * @param pageParams the search page params
   * @param file the file to check reference
   * @param entityType the entity type
   * @return the list of usage DTOs where the file is referenced by
   */
  Page<EntitySetupUsageDTO> getReferencedBy(SearchPageParams pageParams, NGFile file, EntityType entityType);

  /**
   * Validates if file is referenced by other entities
   *
   * @param fileOrFolder the file to check reference
   * @throws ReferencedEntityException in case file is referenced by other entities
   */
  void validateReferenceByAndThrow(NGFile fileOrFolder);

  /**
   * The list of file identifiers referenced by entity of provided type, name and in provided scope. Used for filtering.
   *
   * @param scope Scope of files to be listed
   * @param entityType EntityType of entity that references file
   * @param referredByEntityName Name of entity that references file
   * @return the list of identifiers of files which are referenced by specified entity
   */
  List<String> getAllFileIdentifiersReferencedByInScope(
      Scope scope, EntityType entityType, String referredByEntityName);
}
