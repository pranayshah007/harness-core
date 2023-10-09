/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.IDPModuleLicenseDTO;
import io.harness.licensing.entities.modules.IDPModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.IDP)
@Singleton
public class IDPLicenseObjectMapper implements LicenseObjectMapper<IDPModuleLicense, IDPModuleLicenseDTO> {
  @Override
  public IDPModuleLicenseDTO toDTO(IDPModuleLicense moduleLicense) {
    return IDPModuleLicenseDTO.builder().numberOfDevelopers(moduleLicense.getNumberOfDevelopers()).build();
  }

  @Override
  public IDPModuleLicense toEntity(IDPModuleLicenseDTO moduleLicenseDTO) {
    return IDPModuleLicense.builder().numberOfDevelopers(moduleLicenseDTO.getNumberOfDevelopers()).build();
  }
}
