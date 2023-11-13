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
import io.harness.licensing.beans.modules.ChaosModuleLicenseDTO;
import io.harness.licensing.entities.modules.ChaosModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CHAOS)
@Singleton
public class ChaosLicenseObjectMapper implements LicenseObjectMapper<ChaosModuleLicense, ChaosModuleLicenseDTO> {
  @Override
  public ChaosModuleLicenseDTO toDTO(ChaosModuleLicense entity) {
    return ChaosModuleLicenseDTO.builder()
        .totalChaosExperimentRuns(entity.getTotalChaosExperimentRuns())
        .totalChaosInfrastructures(entity.getTotalChaosInfrastructures())
        .build();
  }

  @Override
  public ChaosModuleLicense toEntity(ChaosModuleLicenseDTO chaosModuleLicenseDTO) {
    validateModuleLicenseDTO(chaosModuleLicenseDTO);

    return ChaosModuleLicense.builder()
        .totalChaosExperimentRuns(chaosModuleLicenseDTO.getTotalChaosExperimentRuns())
        .totalChaosInfrastructures(chaosModuleLicenseDTO.getTotalChaosInfrastructures())
        .build();
  }

  @Override
  public void validateModuleLicenseDTO(ChaosModuleLicenseDTO chaosModuleLicenseDTO) {
    if (!isDeveloperLicensingFeatureEnabled(chaosModuleLicenseDTO.getAccountIdentifier())) {
      if (chaosModuleLicenseDTO.getDeveloperLicenseCount() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }

    if (chaosModuleLicenseDTO.getDeveloperLicenseCount() != null) {
      if (chaosModuleLicenseDTO.getTotalChaosExperimentRuns() != null
          || chaosModuleLicenseDTO.getTotalChaosInfrastructures() != null) {
        throw new InvalidRequestException(
            "Both developerLicenses and totalChaosExperimentRuns/totalChaosInfrastructures cannot be part of the input!");
      }

      // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
      Integer mappingRatio = 1;
      chaosModuleLicenseDTO.setTotalChaosExperimentRuns(
          mappingRatio * chaosModuleLicenseDTO.getDeveloperLicenseCount());
      chaosModuleLicenseDTO.setTotalChaosExperimentRuns(
          mappingRatio * chaosModuleLicenseDTO.getDeveloperLicenseCount());
    }
  }
}
