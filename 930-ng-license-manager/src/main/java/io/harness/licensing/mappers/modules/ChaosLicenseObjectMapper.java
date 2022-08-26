/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.ChaosModuleLicenseDTO;
import io.harness.licensing.entities.modules.ChaosModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CHAOS)
@Singleton
public class ChaosLicenseObjectMapper implements LicenseObjectMapper<ChaosModuleLicense, ChaosModuleLicenseDTO> {
  @Override
  public ChaosModuleLicenseDTO toDTO(ChaosModuleLicense entity) {
    return ChaosModuleLicenseDTO.builder()
        .totalChaosScenarioRun(entity.getTotalChaosScenarioRun())
        .totalChaosDelegates(entity.getTotalChaosDelegates())
        .build();
  }

  @Override
  public ChaosModuleLicense toEntity(ChaosModuleLicenseDTO dto) {
    return ChaosModuleLicense.builder()
        .totalChaosScenarioRun(dto.getTotalChaosScenarioRun())
        .totalChaosDelegates(dto.getTotalChaosDelegates())
        .build();
  }
}
