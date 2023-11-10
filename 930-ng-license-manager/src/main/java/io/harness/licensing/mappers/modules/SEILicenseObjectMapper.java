/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.licensing.beans.modules.SEIModuleLicenseDTO;
import io.harness.licensing.entities.modules.SEIModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
@OwnedBy(HarnessTeam.SEI)
@Singleton
public class SEILicenseObjectMapper implements LicenseObjectMapper<SEIModuleLicense, SEIModuleLicenseDTO> {
  @Inject private FeatureFlagService featureFlagService;
  @Override
  public SEIModuleLicenseDTO toDTO(SEIModuleLicense moduleLicense) {
    return SEIModuleLicenseDTO.builder().numberOfContributors(moduleLicense.getNumberOfContributors()).build();
  }

  @Override
  public SEIModuleLicense toEntity(SEIModuleLicenseDTO seiModuleLicenseDTO) {
    validateModuleLicenseDTO(seiModuleLicenseDTO);

    return SEIModuleLicense.builder().numberOfContributors(seiModuleLicenseDTO.getNumberOfContributors()).build();
  }

  @Override
  public void validateModuleLicenseDTO(SEIModuleLicenseDTO seiModuleLicenseDTO) {
    if (featureFlagService.isEnabled(FeatureName.PLG_DEVELOPER_LICENSING, seiModuleLicenseDTO.getAccountIdentifier())) {
      if (seiModuleLicenseDTO.getDeveloperLicenses() != null) {
        if (seiModuleLicenseDTO.getNumberOfContributors() != null) {
          throw new InvalidRequestException(
              "Both developerLicenses and numberOfContributors cannot be part of the input!");
        }

        // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
        Integer mappingRatio = 1;
        seiModuleLicenseDTO.setNumberOfContributors(mappingRatio * seiModuleLicenseDTO.getDeveloperLicenses());
      } else {
        if (seiModuleLicenseDTO.getDeveloperLicenses() != null) {
          throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
        }
      }
    }
  }
}
