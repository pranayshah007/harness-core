/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filestore.entities.NGFile.NGFiles;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.filestore.dto.filter.FilesFilterPropertiesDTO;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.filestore.dto.FileFilterDTO;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.utils.URLDecoderUtility;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class FileStoreRepositoryCriteriaCreator {
  public static Criteria createCriteriaByScopeAndParentIdentifier(Scope scope, final String parentIdentifier) {
    Criteria scopeCriteria = createScopeCriteria(scope);
    return scopeCriteria.and(NGFiles.parentIdentifier).is(parentIdentifier);
  }

  public static Criteria createScopeCriteria(Scope scope) {
    Criteria criteria = new Criteria();
    criteria.and(NGFiles.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(NGFiles.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(NGFiles.projectIdentifier).is(scope.getProjectIdentifier());
    return criteria;
  }

  public static Criteria createFilesFilterCriteria(
      Scope scope, FilesFilterPropertiesDTO filterProperties, String searchTerm, List<String> fileIdentifiers) {
    Criteria criteria = createScopeCriteria(scope);

    searchTerm = URLDecoderUtility.getDecodedString(searchTerm);

    if (isNotEmpty(searchTerm)) {
      criteria.and(NGFiles.name).regex(searchTerm, CASE_INSENSITIVE_MONGO_OPTIONS);
    }

    if (filterProperties != null && filterProperties.getFileUsage() != null) {
      criteria.and(NGFiles.fileUsage).is(filterProperties.getFileUsage().name());
    }

    if (filterProperties != null && filterProperties.getCreatedBy() != null) {
      criteria.orOperator(Criteria.where(NGFiles.CREATED_BY_NAME).is(filterProperties.getCreatedBy().getName()),
          Criteria.where(NGFiles.CREATED_BY_EMAIL).is(filterProperties.getCreatedBy().getEmail()));
    }

    if (filterProperties != null && !isEmpty(filterProperties.getTags())) {
      criteria.and(NGFiles.tags).in(TagMapper.convertToList(filterProperties.getTags()));
    }

    if (filterProperties != null && null != filterProperties.getReferencedBy()
        && null != filterProperties.getReferencedBy().getType()) {
      criteria.and(NGFiles.identifier).in(fileIdentifiers);
    }

    return criteria;
  }

  public static Criteria createFilesAndFoldersFilterCriteria(Scope scope, FileFilterDTO fileFilterDTO) {
    Criteria criteria = createScopeCriteria(scope);

    if (fileFilterDTO == null) {
      return criteria;
    }

    String searchTerm = URLDecoderUtility.getDecodedString(fileFilterDTO.getSearchTerm());

    if (isNotEmpty(searchTerm)) {
      criteria.orOperator(Criteria.where(NGFiles.name).regex(searchTerm, CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(NGFiles.identifier).regex(searchTerm, CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(NGFiles.tags + "." + NGTagKeys.key).regex(searchTerm, CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(NGFiles.tags + "." + NGTagKeys.value).regex(searchTerm, CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    if (isNotEmpty(fileFilterDTO.getIdentifiers())) {
      criteria.and(NGFiles.identifier).in(fileFilterDTO.getIdentifiers());
    }

    return criteria;
  }

  public static Sort createSortByLastModifiedAtDesc() {
    return Sort.by(Sort.Direction.DESC, NGFiles.lastModifiedAt);
  }

  public static Sort createSortByName(Sort.Direction direction) {
    return Sort.by(direction, NGFiles.name);
  }
}
