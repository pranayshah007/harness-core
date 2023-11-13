/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import static io.harness.licensing.helpers.ModuleLicenseHelper.isDeveloperLicensingFeatureEnabled;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class CILicenseObjectMapper implements LicenseObjectMapper<CIModuleLicense, CIModuleLicenseDTO> {
  @Override
  public CIModuleLicenseDTO toDTO(CIModuleLicense entity) {
    return CIModuleLicenseDTO.builder()
        .numberOfCommitters(entity.getNumberOfCommitters())
        .cacheAllowance(entity.getCacheAllowance())
        .hostingCredits(entity.getHostingCredits())
        .build();
  }

  @Override
  public CIModuleLicense toEntity(CIModuleLicenseDTO ciModuleLicenseDTO) {
    validateModuleLicenseDTO(ciModuleLicenseDTO);

    return CIModuleLicense.builder()
        .numberOfCommitters(ciModuleLicenseDTO.getNumberOfCommitters())
        .cacheAllowance(ciModuleLicenseDTO.getCacheAllowance())
        .hostingCredits(ciModuleLicenseDTO.getHostingCredits())
        .build();
  }

  @Override
  public void validateModuleLicenseDTO(CIModuleLicenseDTO ciModuleLicenseDTO) {
    if (!isDeveloperLicensingFeatureEnabled(ciModuleLicenseDTO.getAccountIdentifier())) {
      if (ciModuleLicenseDTO.getDeveloperLicenseCount() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }

    if (ciModuleLicenseDTO.getDeveloperLicenseCount() != null) {
      if (ciModuleLicenseDTO.getNumberOfCommitters() != null) {
        throw new InvalidRequestException(
            "Both developerLicenses and workloads/serviceInstances cannot be part of the input!");
      }

      // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
      Integer mappingRatio = 1;
      ciModuleLicenseDTO.setNumberOfCommitters(mappingRatio * ciModuleLicenseDTO.getDeveloperLicenseCount());
    }
  }
}
