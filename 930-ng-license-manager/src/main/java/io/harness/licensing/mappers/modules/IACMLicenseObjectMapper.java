/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.licensing.beans.modules.IACMModuleLicenseDTO;
import io.harness.licensing.entities.modules.IACMModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.IACM)
@Singleton
public class IACMLicenseObjectMapper implements LicenseObjectMapper<IACMModuleLicense, IACMModuleLicenseDTO> {
  @Inject private FeatureFlagService featureFlagService;
  @Override
  public IACMModuleLicenseDTO toDTO(IACMModuleLicense entity) {
    return IACMModuleLicenseDTO.builder().numberOfDevelopers(entity.getNumberOfDevelopers()).build();
  }

  @Override
  public IACMModuleLicense toEntity(IACMModuleLicenseDTO iacmModuleLicenseDTO) {
    validateModuleLicenseDTO(iacmModuleLicenseDTO);

    return IACMModuleLicense.builder().numberOfDevelopers(iacmModuleLicenseDTO.getNumberOfDevelopers()).build();
  }

  @Override
  public void validateModuleLicenseDTO(IACMModuleLicenseDTO iacmModuleLicenseDTO) {
    if (featureFlagService.isEnabled(
            FeatureName.PLG_DEVELOPER_LICENSING, iacmModuleLicenseDTO.getAccountIdentifier())) {
      if (iacmModuleLicenseDTO.getDeveloperLicenses() != null) {
        if (iacmModuleLicenseDTO.getNumberOfDevelopers() != null) {
          throw new InvalidRequestException(
              "Both developerLicenses and workloads/serviceInstances cannot be part of the input!");
        }

        // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
        Integer mappingRatio = 1;
        iacmModuleLicenseDTO.setNumberOfDevelopers(mappingRatio * iacmModuleLicenseDTO.getDeveloperLicenses());
      }
    } else {
      if (iacmModuleLicenseDTO.getDeveloperLicenses() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }
  }
}
