/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.SEIModuleLicenseDTO;
import io.harness.licensing.entities.modules.SEIModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;
@OwnedBy(HarnessTeam.SEI)
@Singleton
public class SEILicenseObjectMapper implements LicenseObjectMapper<SEIModuleLicense, SEIModuleLicenseDTO> {
  @Override
  public SEIModuleLicenseDTO toDTO(SEIModuleLicense moduleLicense) {
    return SEIModuleLicenseDTO.builder().numberOfContributors(moduleLicense.getNumberOfContributors()).build();
  }

  @Override
  public SEIModuleLicense toEntity(SEIModuleLicenseDTO moduleLicenseDTO) {
    return SEIModuleLicense.builder().numberOfContributors(moduleLicenseDTO.getNumberOfContributors()).build();
  }
}
