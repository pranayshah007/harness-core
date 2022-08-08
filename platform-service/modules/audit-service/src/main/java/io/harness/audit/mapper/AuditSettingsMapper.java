/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditSettingsDTO;
import io.harness.audit.entities.AuditSettings;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class AuditSettingsMapper {
  public static AuditSettings fromDTO(AuditSettingsDTO auditSettingsDTO) {
    return AuditSettings.builder().retentionPeriodInMonths(auditSettingsDTO.getRetentionPeriodInMonths()).build();
  }

  public static AuditSettingsDTO toDTO(AuditSettings auditSettings) {
    return AuditSettingsDTO.builder().retentionPeriodInMonths(auditSettings.getRetentionPeriodInMonths()).build();
  }
}
