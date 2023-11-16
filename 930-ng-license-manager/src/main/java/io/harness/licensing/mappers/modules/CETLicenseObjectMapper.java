/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.beans.modules.CETModuleLicenseDTO;
import io.harness.licensing.entities.modules.CETModuleLicense;
import io.harness.licensing.helpers.ModuleLicenseHelper;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CET)
@Singleton
public class CETLicenseObjectMapper implements LicenseObjectMapper<CETModuleLicense, CETModuleLicenseDTO> {
  @Inject private ModuleLicenseHelper moduleLicenseHelper;

  @Override
  public CETModuleLicenseDTO toDTO(CETModuleLicense moduleLicense) {
    return CETModuleLicenseDTO.builder().numberOfAgents(moduleLicense.getNumberOfAgents()).build();
  }

  @Override
  public CETModuleLicense toEntity(CETModuleLicenseDTO cetModuleLicenseDTO) {
    validateModuleLicenseDTO(cetModuleLicenseDTO);

    return CETModuleLicense.builder().numberOfAgents(cetModuleLicenseDTO.getNumberOfAgents()).build();
  }

  @Override
  public void validateModuleLicenseDTO(CETModuleLicenseDTO cetModuleLicenseDTO) {
    if (!moduleLicenseHelper.isDeveloperLicensingFeatureEnabled(cetModuleLicenseDTO.getAccountIdentifier())) {
      if (cetModuleLicenseDTO.getDeveloperLicenseCount() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }

    if (cetModuleLicenseDTO.getDeveloperLicenseCount() != null) {
      if (cetModuleLicenseDTO.getNumberOfAgents() != null) {
        throw new InvalidRequestException("Both developerLicenses and numberOfAgents cannot be part of the input!");
      }

      // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
      Integer mappingRatio = 1;
      cetModuleLicenseDTO.setNumberOfAgents(mappingRatio * cetModuleLicenseDTO.getDeveloperLicenseCount());
    }
  }
}
