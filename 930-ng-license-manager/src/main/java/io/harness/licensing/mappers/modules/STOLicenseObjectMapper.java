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
import io.harness.licensing.beans.modules.STOModuleLicenseDTO;
import io.harness.licensing.entities.modules.STOModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.STO)
@Singleton
public class STOLicenseObjectMapper implements LicenseObjectMapper<STOModuleLicense, STOModuleLicenseDTO> {
  @Inject private FeatureFlagService featureFlagService;
  @Override
  public STOModuleLicenseDTO toDTO(STOModuleLicense entity) {
    return STOModuleLicenseDTO.builder().numberOfDevelopers(entity.getNumberOfDevelopers()).build();
  }

  @Override
  public STOModuleLicense toEntity(STOModuleLicenseDTO stoModuleLicenseDTO) {
    validateModuleLicenseDTO(stoModuleLicenseDTO);

    return STOModuleLicense.builder().numberOfDevelopers(stoModuleLicenseDTO.getNumberOfDevelopers()).build();
  }

  @Override
  public void validateModuleLicenseDTO(STOModuleLicenseDTO stoModuleLicenseDTO) {
    if (featureFlagService.isEnabled(FeatureName.PLG_DEVELOPER_LICENSING, stoModuleLicenseDTO.getAccountIdentifier())) {
      if (stoModuleLicenseDTO.getDeveloperLicenses() != null) {
        if (stoModuleLicenseDTO.getNumberOfDevelopers() != null) {
          throw new InvalidRequestException(
              "Both developerLicenses and numberOfDevelopers cannot be part of the input!");
        }

        // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
        Integer mappingRatio = 1;
        stoModuleLicenseDTO.setNumberOfDevelopers(mappingRatio * stoModuleLicenseDTO.getDeveloperLicenses());
      }
    } else {
      if (stoModuleLicenseDTO.getDeveloperLicenses() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }
  }
}
