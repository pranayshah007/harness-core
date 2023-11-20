/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.licensing.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.DeveloperMappingDTO;
import io.harness.licensing.entities.developer.DeveloperMapping;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class DeveloperMappingObjectConverter {
  public DeveloperMappingDTO toDTO(DeveloperMapping developerMapping) {
    DeveloperMappingDTO developerMappingDTO = DeveloperMappingDTO.builder().build();
    developerMappingDTO.setId(developerMapping.getId());
    developerMappingDTO.setAccountIdentifier(developerMapping.getAccountIdentifier());
    developerMappingDTO.setModuleType(developerMapping.getModuleType());
    developerMappingDTO.setDeveloperCount(developerMapping.getDeveloperCount());
    developerMappingDTO.setSecondaryEntitlement(developerMapping.getSecondaryEntitlement());
    developerMappingDTO.setSecondaryEntitlementCount(developerMapping.getSecondaryEntitlementCount());
    return developerMappingDTO;
  }

  public DeveloperMapping toEntity(DeveloperMappingDTO developerMappingDTO) {
    DeveloperMapping developerMapping = DeveloperMapping.builder().build();
    developerMapping.setId(developerMappingDTO.getId());
    developerMapping.setAccountIdentifier(developerMappingDTO.getAccountIdentifier());
    developerMapping.setModuleType(developerMappingDTO.getModuleType());
    developerMapping.setDeveloperCount(developerMappingDTO.getDeveloperCount());
    developerMapping.setSecondaryEntitlement(developerMappingDTO.getSecondaryEntitlement());
    developerMapping.setSecondaryEntitlementCount(developerMappingDTO.getSecondaryEntitlementCount());
    return developerMapping;
  }
}
