/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.mappers;

import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_LIFECYCLE;
import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_OWNER;
import static io.harness.idp.onboarding.utils.Constants.SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.beans.BackstageCatalogComponentEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.ng.core.service.dto.ServiceResponseDTO;

import java.util.ArrayList;

@OwnedBy(HarnessTeam.IDP)
public class HarnessServiceToBackstageComponent
    implements HarnessEntityToBackstageEntity<ServiceResponseDTO, BackstageCatalogComponentEntity> {
  @Override
  public BackstageCatalogComponentEntity map(ServiceResponseDTO serviceResponseDTO) {
    BackstageCatalogComponentEntity backstageCatalogComponentEntity = new BackstageCatalogComponentEntity();

    BackstageCatalogEntity.Metadata metadata = new BackstageCatalogEntity.Metadata();
    metadata.setMetadata(serviceResponseDTO.getIdentifier(), serviceResponseDTO.getName(),
        serviceResponseDTO.getDescription(), new ArrayList<>(serviceResponseDTO.getTags().values()));
    backstageCatalogComponentEntity.setMetadata(metadata);

    BackstageCatalogComponentEntity.Spec spec = new BackstageCatalogComponentEntity.Spec();
    spec.setType(SERVICE);
    spec.setLifecycle(ENTITY_UNKNOWN_LIFECYCLE);
    spec.setOwner(ENTITY_UNKNOWN_OWNER);
    spec.setDomain(serviceResponseDTO.getOrgIdentifier());
    spec.setSystem(serviceResponseDTO.getProjectIdentifier());
    backstageCatalogComponentEntity.setSpec(spec);

    return backstageCatalogComponentEntity;
  }
}
