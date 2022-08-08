/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote;

import static io.harness.audit.mapper.YamlDiffRecordMapper.toDTO;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.AuditYamlService;
import io.harness.audit.api.impl.AuditPermissionValidator;
import io.harness.audit.beans.YamlDiffRecordDTO;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.mapper.ResourceScopeMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.PL)
public class AuditYamlResourceImpl implements AuditYamlResource {
  private final AuditService auditService;
  private final AuditYamlService auditYamlService;
  private final AuditPermissionValidator auditPermissionValidator;

  @Inject
  public AuditYamlResourceImpl(
      AuditService auditService, AuditYamlService auditYamlService, AuditPermissionValidator auditPermissionValidator) {
    this.auditService = auditService;
    this.auditYamlService = auditYamlService;
    this.auditPermissionValidator = auditPermissionValidator;
  }

  public ResponseDTO<YamlDiffRecordDTO> get(String accountIdentifier, String auditId) {
    AuditEvent auditEvent =
        auditService.get(accountIdentifier, auditId)
            .orElseThrow(() -> new InvalidRequestException(String.format("No audit found with auditId %s", auditId)));
    auditPermissionValidator.validate(accountIdentifier, ResourceScopeMapper.toDTO(auditEvent.getResourceScope()));
    return ResponseDTO.newResponse(toDTO(auditYamlService.get(auditId)));
  }
}
