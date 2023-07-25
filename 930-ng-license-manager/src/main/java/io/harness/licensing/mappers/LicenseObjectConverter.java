/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.mappers;
import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.entities.modules.ModuleLicense;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(HarnessTeam.GTM)
@Singleton
public class LicenseObjectConverter {
  @Inject Map<ModuleType, LicenseObjectMapper> mapperMap;

  public <T extends ModuleLicenseDTO> T toDTO(ModuleLicense moduleLicense) {
    ModuleType moduleType = moduleLicense.getModuleType();
    ModuleLicenseDTO moduleLicenseDTO = mapperMap.get(moduleType).toDTO(moduleLicense);
    moduleLicenseDTO.setId(moduleLicense.getId());
    moduleLicenseDTO.setAccountIdentifier(moduleLicense.getAccountIdentifier());
    moduleLicenseDTO.setModuleType(moduleLicense.getModuleType());
    moduleLicenseDTO.setEdition(moduleLicense.getEdition());
    moduleLicenseDTO.setLicenseType(moduleLicense.getLicenseType());
    moduleLicenseDTO.setSelfService(moduleLicense.isSelfService());
    moduleLicenseDTO.setStatus(moduleLicense.getStatus());
    moduleLicenseDTO.setStartTime(moduleLicense.getStartTime());
    moduleLicenseDTO.setExpiryTime(moduleLicense.getExpiryTime());
    moduleLicenseDTO.setPremiumSupport(moduleLicense.isPremiumSupport());
    moduleLicenseDTO.setCreatedAt(moduleLicense.getCreatedAt());
    moduleLicenseDTO.setLastModifiedAt(moduleLicense.getLastUpdatedAt());
    moduleLicenseDTO.setTrialExtended(moduleLicense.getTrialExtended());
    return (T) moduleLicenseDTO;
  }

  public <T extends ModuleLicense> T toEntity(ModuleLicenseDTO moduleLicenseDTO) {
    ModuleType moduleType = moduleLicenseDTO.getModuleType();
    ModuleLicense moduleLicense = mapperMap.get(moduleType).toEntity(moduleLicenseDTO);
    moduleLicense.setId(moduleLicenseDTO.getId());
    moduleLicense.setAccountIdentifier(moduleLicenseDTO.getAccountIdentifier());
    moduleLicense.setModuleType(moduleLicenseDTO.getModuleType());
    moduleLicense.setEdition(moduleLicenseDTO.getEdition());
    moduleLicense.setLicenseType(moduleLicenseDTO.getLicenseType());
    moduleLicense.setSelfService(moduleLicenseDTO.isSelfService());
    moduleLicense.setStatus(moduleLicenseDTO.getStatus());
    moduleLicense.setStartTime(moduleLicenseDTO.getStartTime());
    moduleLicense.setExpiryTime(moduleLicenseDTO.getExpiryTime());
    moduleLicense.setTrialExtended(moduleLicenseDTO.getTrialExtended());
    return (T) moduleLicense;
  }
}
