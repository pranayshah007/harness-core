/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import static io.harness.licensing.helpers.ModuleLicenseHelper.isDeveloperLicensingFeatureEnabled;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
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
  public IDPModuleLicense toEntity(IDPModuleLicenseDTO idpModuleLicenseDTO) {
    validateModuleLicenseDTO(idpModuleLicenseDTO);

    return IDPModuleLicense.builder().numberOfDevelopers(idpModuleLicenseDTO.getNumberOfDevelopers()).build();
  }

  @Override
  public void validateModuleLicenseDTO(IDPModuleLicenseDTO idpModuleLicenseDTO) {
    if (!isDeveloperLicensingFeatureEnabled(idpModuleLicenseDTO.getAccountIdentifier())) {
      if (idpModuleLicenseDTO.getDeveloperLicenseCount() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }

    if (idpModuleLicenseDTO.getDeveloperLicenseCount() != null) {
      if (idpModuleLicenseDTO.getNumberOfDevelopers() != null) {
        throw new InvalidRequestException("Both developerLicenses and numberOfDevelopers cannot be part of the input!");
      }

      // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
      Integer mappingRatio = 1;
      idpModuleLicenseDTO.setNumberOfDevelopers(mappingRatio * idpModuleLicenseDTO.getDeveloperLicenseCount());
    }
  }
}
