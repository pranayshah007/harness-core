/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.file;

import io.harness.cdng.manifest.yaml.harness.HarnessStoreFile;
import io.harness.common.ParameterFieldHelper;
import io.harness.common.ParameterRuntimeFiledHelper;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.service.FileStoreService;
import io.harness.filters.FilterCreatorHelper;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileEntityVisitorHelper implements EntityReferenceExtractor {
  @Inject private FileStoreService fileStoreService;

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return null;
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    if (!(object instanceof HarnessStoreFile)) {
      throw new InvalidRequestException(
          String.format("Not supported implementation for %s class type for extracting file references",
              object.getClass().toString()));
    }

    HarnessStoreFile file = (HarnessStoreFile) object;
    Set<EntityDetailProtoDTO> result = new HashSet<>();

    Optional<String> filePathOpt = ParameterFieldHelper.getParameterFieldFinalValue(file.getPath());
    Optional<Scope> fileScopeOpt = ParameterRuntimeFiledHelper.getScopeParameterFieldFinalValue(file.getScope());

    if (!filePathOpt.isPresent() || !fileScopeOpt.isPresent()) {
      log.error("Not set file path or scope on HarnessStoreFile object, uuid: {}", file.getUuid());
      return result;
    }

    Scope fileScope = fileScopeOpt.get();
    io.harness.beans.Scope scope =
        io.harness.beans.Scope.of(accountIdentifier, orgIdentifier, projectIdentifier, fileScope);
    Optional<FileDTO> fileDTO = fileStoreService.getByPath(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), filePathOpt.get());

    if (!fileDTO.isPresent()) {
      log.error("Not found file in File Store, path: {}, scope: {}", filePathOpt, fileScope);
      return result;
    }

    String fullQualifiedDomainName = VisitorParentPathUtils.getFullQualifiedDomainName(contextMap);
    String fileScopedIdentifier = fixIdentifierScope(fileScope, fileDTO.get().getIdentifier());

    result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier, projectIdentifier,
        fullQualifiedDomainName, ParameterField.createValueField(fileScopedIdentifier), EntityTypeProtoEnum.FILES));

    return result;
  }

  private String fixIdentifierScope(Scope fileScope, String fileIdentifier) {
    if (Scope.PROJECT != fileScope) {
      fileIdentifier = String.format("%s.%s", fileScope.getYamlRepresentation(), fileIdentifier);
    }
    return fileIdentifier;
  }
}
