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
import io.harness.licensing.beans.modules.SRMModuleLicenseDTO;
import io.harness.licensing.entities.modules.SRMModuleLicense;
import io.harness.licensing.helpers.ModuleLicenseHelper;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class SRMLicenseObjectMapper implements LicenseObjectMapper<SRMModuleLicense, SRMModuleLicenseDTO> {
  @Inject private ModuleLicenseHelper moduleLicenseHelper;

  @Override
  public SRMModuleLicenseDTO toDTO(SRMModuleLicense moduleLicense) {
    return SRMModuleLicenseDTO.builder().numberOfServices(moduleLicense.getNumberOfServices()).build();
  }

  @Override
  public SRMModuleLicense toEntity(SRMModuleLicenseDTO srmModuleLicenseDTO) {
    validateModuleLicenseDTO(srmModuleLicenseDTO);

    return SRMModuleLicense.builder().numberOfServices(srmModuleLicenseDTO.getNumberOfServices()).build();
  }

  @Override
  public void validateModuleLicenseDTO(SRMModuleLicenseDTO srmModuleLicenseDTO) {
    if (!moduleLicenseHelper.isDeveloperLicensingFeatureEnabled(srmModuleLicenseDTO.getAccountIdentifier())) {
      if (srmModuleLicenseDTO.getDeveloperLicenseCount() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }

    if (srmModuleLicenseDTO.getDeveloperLicenseCount() != null) {
      if (srmModuleLicenseDTO.getNumberOfServices() != null) {
        throw new InvalidRequestException("Both developerLicenses and numberOfServices cannot be part of the input!");
      }

      // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
      Integer mappingRatio = 1;
      srmModuleLicenseDTO.setNumberOfServices(mappingRatio * srmModuleLicenseDTO.getDeveloperLicenseCount());
    }
  }
}
