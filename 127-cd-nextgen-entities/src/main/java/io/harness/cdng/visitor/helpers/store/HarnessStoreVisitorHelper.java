/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.store;

import static io.harness.cdng.visitor.YamlTypes.PATH_CONNECTOR;

import io.harness.beans.FileReference;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.service.FileStoreService;
import io.harness.filters.FilterCreatorHelper;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HarnessStoreVisitorHelper implements EntityReferenceExtractor {
  @Inject private FileStoreService fileStoreService;

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return null;
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    if (!(object instanceof HarnessStore)) {
      throw new InvalidRequestException(
          String.format("Not supported implementation for %s class type for extracting file references",
              object.getClass().toString()));
    }

    HarnessStore harnessStore = (HarnessStore) object;
    Set<EntityDetailProtoDTO> result;
    result = getEntityDetailsProtoDTO(harnessStore.getFiles(), accountIdentifier, orgIdentifier, projectIdentifier,
        contextMap, YAMLFieldNameConstants.FILES);

    List<String> secretFiles = ParameterFieldHelper.getParameterFieldListValue(harnessStore.getSecretFiles(), false);
    secretFiles.forEach(secretFile -> {
      String fullQualifiedDomainName = VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR
          + YAMLFieldNameConstants.SECRET_FILES;
      result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier, projectIdentifier,
          fullQualifiedDomainName, ParameterField.createValueField(secretFile), EntityTypeProtoEnum.SECRETS));
    });

    return result;
  }

  public Set<EntityDetailProtoDTO> getEntityDetailsProtoDTO(ParameterField<List<String>> valuesFiles,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Map<String, Object> contextMap,
      String overridePathFieldName) {
    List<String> files = ParameterFieldHelper.getParameterFieldValue(valuesFiles) instanceof List
        ? ParameterFieldHelper.getParameterFieldListValue(valuesFiles, false)
        : Collections.emptyList();
    Set<EntityDetailProtoDTO> result = new HashSet<>(files.size());
    files.stream().filter(EmptyPredicate::isNotEmpty).forEach(scopedFilePath -> {
      FileReference fileReference =
          FileReference.of(scopedFilePath, accountIdentifier, orgIdentifier, projectIdentifier);
      Optional<FileDTO> fileDTO = fileStoreService.getByPath(fileReference.getAccountIdentifier(),
          fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath());

      if (fileDTO.isPresent()) {
        String fullQualifiedDomainName =
            VisitorParentPathUtils.getFullQualifiedDomainName(contextMap) + PATH_CONNECTOR + overridePathFieldName;
        String fileScopedIdentifier =
            FileReference.getScopedFileIdentifier(fileReference.getScope(), fileDTO.get().getIdentifier());

        result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier,
            projectIdentifier, fullQualifiedDomainName, ParameterField.createValueField(fileScopedIdentifier),
            EntityTypeProtoEnum.FILES));
      }
    });

    return result;
  }
}
