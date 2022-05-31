/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.STOModuleLicenseDTO;
import io.harness.licensing.entities.modules.STOModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.STO)
@Singleton
public class STOLicenseObjectMapper implements LicenseObjectMapper<STOModuleLicense, STOModuleLicenseDTO> {
  @Override
  public STOModuleLicenseDTO toDTO(STOModuleLicense entity) {
    return STOModuleLicenseDTO.builder().numberOfDevelopers(entity.getNumberOfDevelopers()).build();
  }

  @Override
  public STOModuleLicense toEntity(STOModuleLicenseDTO dto) {
    return STOModuleLicense.builder().numberOfDevelopers(dto.getNumberOfDevelopers()).build();
  }
}
