/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.beans.modules.CFModuleLicenseDTO;
import io.harness.licensing.entities.modules.CFModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class CFLicenseObjectMapper implements LicenseObjectMapper<CFModuleLicense, CFModuleLicenseDTO> {
  @Override
  public CFModuleLicenseDTO toDTO(CFModuleLicense entity) {
    return CFModuleLicenseDTO.builder()
        .numberOfUsers(entity.getNumberOfUsers())
        .numberOfClientMAUs(entity.getNumberOfClientMAUs())
        .build();
  }

  @Override
  public CFModuleLicense toEntity(CFModuleLicenseDTO cfModuleLicenseDTO) {
    validateModuleLicenseDTO(cfModuleLicenseDTO);

    return CFModuleLicense.builder()
        .numberOfClientMAUs(cfModuleLicenseDTO.getNumberOfClientMAUs())
        .numberOfUsers(cfModuleLicenseDTO.getNumberOfUsers())
        .build();
  }

  @Override
  public void validateModuleLicenseDTO(CFModuleLicenseDTO cfModuleLicenseDTO) {
    if (cfModuleLicenseDTO.getDeveloperLicenses() != null) {
      if (cfModuleLicenseDTO.getNumberOfUsers() != null || cfModuleLicenseDTO.getNumberOfClientMAUs() != null) {
        throw new InvalidRequestException(
            "Both developerLicenses and numberOfUsers/numberOfClientMAUs cannot be part of the input!");
      }

      // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
      Integer mappingRatio = 1;
      cfModuleLicenseDTO.setNumberOfUsers(mappingRatio * cfModuleLicenseDTO.getDeveloperLicenses());
      cfModuleLicenseDTO.setNumberOfClientMAUs(((long) mappingRatio * cfModuleLicenseDTO.getDeveloperLicenses()));
    }
  }
}
