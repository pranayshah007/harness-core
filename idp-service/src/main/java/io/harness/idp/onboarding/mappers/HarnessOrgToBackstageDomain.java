/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.mappers;

import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_OWNER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.beans.BackstageCatalogDomainEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.ng.core.dto.OrganizationDTO;

import java.util.ArrayList;

@OwnedBy(HarnessTeam.IDP)
public class HarnessOrgToBackstageDomain
    implements HarnessEntityToBackstageEntity<OrganizationDTO, BackstageCatalogDomainEntity> {
  @Override
  public BackstageCatalogDomainEntity map(OrganizationDTO organizationDTO) {
    BackstageCatalogDomainEntity backstageCatalogDomainEntity = new BackstageCatalogDomainEntity();

    BackstageCatalogEntity.Metadata metadata = new BackstageCatalogEntity.Metadata();
    metadata.setMetadata(organizationDTO.getIdentifier(), organizationDTO.getName(), organizationDTO.getDescription(),
        new ArrayList<>(organizationDTO.getTags().values()), null);
    backstageCatalogDomainEntity.setMetadata(metadata);

    BackstageCatalogDomainEntity.Spec spec = new BackstageCatalogDomainEntity.Spec();
    spec.setOwner(ENTITY_UNKNOWN_OWNER);
    backstageCatalogDomainEntity.setSpec(spec);

    return backstageCatalogDomainEntity;
  }
}
