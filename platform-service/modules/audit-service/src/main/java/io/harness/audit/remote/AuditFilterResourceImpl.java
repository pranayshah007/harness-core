/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filter.FilterType.AUDIT;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.impl.AuditPermissionValidator;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class AuditFilterResourceImpl implements AuditFilterResource {
  private FilterService filterService;
  private AuditPermissionValidator auditPermissionValidator;

  public ResponseDTO<FilterDTO> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    auditPermissionValidator.validate(accountIdentifier,
        ResourceScopeDTO.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .build());
    return ResponseDTO.newResponse(
        filterService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier, AUDIT));
  }

  public ResponseDTO<PageResponse<FilterDTO>> list(
      int page, int size, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    auditPermissionValidator.validate(accountIdentifier,
        ResourceScopeDTO.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .build());
    return ResponseDTO.newResponse(getNGPageResponse(
        filterService.list(page, size, accountIdentifier, orgIdentifier, projectIdentifier, null, AUDIT)));
  }

  public ResponseDTO<FilterDTO> create(FilterDTO filterDTO, String accountIdentifier) {
    AuditFilterPropertiesDTO auditFilterProperties = (AuditFilterPropertiesDTO) filterDTO.getFilterProperties();
    if (auditFilterProperties != null && isNotEmpty(auditFilterProperties.getScopes())) {
      for (ResourceScopeDTO resourceScopeDTO : auditFilterProperties.getScopes()) {
        auditPermissionValidator.validate(accountIdentifier, resourceScopeDTO);
      }
    }
    return ResponseDTO.newResponse(filterService.create(accountIdentifier, filterDTO));
  }

  public ResponseDTO<FilterDTO> update(FilterDTO filterDTO, String accountIdentifier) {
    AuditFilterPropertiesDTO auditFilterProperties = (AuditFilterPropertiesDTO) filterDTO.getFilterProperties();
    if (auditFilterProperties != null && isNotEmpty(auditFilterProperties.getScopes())) {
      for (ResourceScopeDTO resourceScopeDTO : auditFilterProperties.getScopes()) {
        auditPermissionValidator.validate(accountIdentifier, resourceScopeDTO);
      }
    }
    return ResponseDTO.newResponse(filterService.update(accountIdentifier, filterDTO));
  }

  public ResponseDTO<Boolean> delete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    auditPermissionValidator.validate(accountIdentifier,
        ResourceScopeDTO.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .build());
    return ResponseDTO.newResponse(
        filterService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, AUDIT));
  }
}
