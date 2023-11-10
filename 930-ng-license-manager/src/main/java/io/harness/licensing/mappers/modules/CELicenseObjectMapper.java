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
import io.harness.licensing.beans.modules.CEModuleLicenseDTO;
import io.harness.licensing.entities.modules.CEModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class CELicenseObjectMapper implements LicenseObjectMapper<CEModuleLicense, CEModuleLicenseDTO> {
  @Override
  public CEModuleLicenseDTO toDTO(CEModuleLicense entity) {
    return CEModuleLicenseDTO.builder().spendLimit(entity.getSpendLimit()).build();
  }

  @Override
  public CEModuleLicense toEntity(CEModuleLicenseDTO ceModuleLicenseDTO) {
    validateModuleLicenseDTO(ceModuleLicenseDTO);

    return CEModuleLicense.builder().spendLimit(ceModuleLicenseDTO.getSpendLimit()).build();
  }

  @Override
  public void validateModuleLicenseDTO(CEModuleLicenseDTO ceModuleLicenseDTO) {
    // CCM will never have primary entitlements
    if (ceModuleLicenseDTO.getDeveloperLicenses() != null) {
      throw new InvalidRequestException(
          "Cloud Cost Management License will never have primary entitlements i.e. developerLicenses");
    }
  }
}
