/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.SRMModuleLicenseDTO;
import io.harness.licensing.entities.modules.SRMModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class SRMLicenseObjectMapper implements LicenseObjectMapper<SRMModuleLicense, SRMModuleLicenseDTO> {
  @Override
  public SRMModuleLicenseDTO toDTO(SRMModuleLicense moduleLicense) {
    return SRMModuleLicenseDTO.builder().numberOfServices(moduleLicense.getNumberOfServices()).build();
  }

  @Override
  public SRMModuleLicense toEntity(SRMModuleLicenseDTO moduleLicenseDTO) {
    return SRMModuleLicense.builder().numberOfServices(moduleLicenseDTO.getNumberOfServices()).build();
  }
}
