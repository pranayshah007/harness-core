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
import io.harness.cd.CDLicenseType;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class CDLicenseObjectMapper implements LicenseObjectMapper<CDModuleLicense, CDModuleLicenseDTO> {
  @Override
  public CDModuleLicenseDTO toDTO(CDModuleLicense entity) {
    CDModuleLicenseDTO dto = CDModuleLicenseDTO.builder().build();
    dto.setCdLicenseType(entity.getCdLicenseType());
    dto.setServiceInstances(entity.getServiceInstances());
    dto.setWorkloads(entity.getWorkloads());
    return dto;
  }

  @Override
  public CDModuleLicense toEntity(CDModuleLicenseDTO cdModuleLicenseDTO) {
    validateModuleLicenseDTO(cdModuleLicenseDTO);

    CDModuleLicense entity = CDModuleLicense.builder().build();
    entity.setCdLicenseType(cdModuleLicenseDTO.getCdLicenseType());
    entity.setServiceInstances(cdModuleLicenseDTO.getServiceInstances());
    entity.setWorkloads(cdModuleLicenseDTO.getWorkloads());
    return entity;
  }

  @Override
  public void validateModuleLicenseDTO(CDModuleLicenseDTO cdModuleLicenseDTO) {
    if (!isDeveloperLicensingFeatureEnabled(cdModuleLicenseDTO.getAccountIdentifier())) {
      if (cdModuleLicenseDTO.getDeveloperLicenseCount() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }

    if (cdModuleLicenseDTO.getDeveloperLicenseCount() != null) {
      if (cdModuleLicenseDTO.getWorkloads() != null || cdModuleLicenseDTO.getServiceInstances() != null) {
        throw new InvalidRequestException(
            "Both developerLicenses and workloads/serviceInstances cannot be part of the input!");
      }

      // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
      Integer mappingRatio = 1;
      if (CDLicenseType.SERVICES.equals(cdModuleLicenseDTO.getCdLicenseType())) {
        cdModuleLicenseDTO.setWorkloads(mappingRatio * cdModuleLicenseDTO.getDeveloperLicenseCount());
      } else if (CDLicenseType.SERVICE_INSTANCES.equals(cdModuleLicenseDTO.getCdLicenseType())) {
        cdModuleLicenseDTO.setServiceInstances(mappingRatio * cdModuleLicenseDTO.getDeveloperLicenseCount());
      } else {
        throw new InvalidRequestException("CDLicenseType has to be either SERVICES or SERVICE_INSTANCES");
      }
    }
  }
}
