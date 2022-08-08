/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote;

import static io.harness.audit.mapper.AuditSettingsMapper.toDTO;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditSettingsService;
import io.harness.audit.beans.AuditSettingsDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class AuditSettingsResourceImpl implements AuditSettingsResource {
  @Inject private final AuditSettingsService auditSettingsService;

  @InternalApi
  public ResponseDTO<AuditSettingsDTO> update(String accountIdentifier, AuditSettingsDTO auditSettingsDTO) {
    AuditSettingsDTO updatedAuditSettingsDTO =
        toDTO(auditSettingsService.update(accountIdentifier, auditSettingsDTO.getRetentionPeriodInMonths()));
    return ResponseDTO.newResponse(updatedAuditSettingsDTO);
  }
}
