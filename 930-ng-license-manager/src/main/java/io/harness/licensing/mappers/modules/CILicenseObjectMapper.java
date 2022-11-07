/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class CILicenseObjectMapper implements LicenseObjectMapper<CIModuleLicense, CIModuleLicenseDTO> {
  @Override
  public CIModuleLicenseDTO toDTO(CIModuleLicense entity) {
    return CIModuleLicenseDTO.builder()
        .numberOfCommitters(entity.getNumberOfCommitters())
        .hostingCredits(entity.getHostingCredits())
        .build();
  }

  @Override
  public CIModuleLicense toEntity(CIModuleLicenseDTO dto) {
    return CIModuleLicense.builder()
        .numberOfCommitters(dto.getNumberOfCommitters())
        .hostingCredits(dto.getHostingCredits())
        .build();
  }
}
