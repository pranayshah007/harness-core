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
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogSystemEntity;
import io.harness.ng.core.dto.ProjectDTO;

import java.util.ArrayList;

@OwnedBy(HarnessTeam.IDP)
public class HarnessProjectToBackstageSystem
    implements HarnessEntityToBackstageEntity<ProjectDTO, BackstageCatalogSystemEntity> {
  @Override
  public BackstageCatalogSystemEntity map(ProjectDTO projectDTO) {
    BackstageCatalogSystemEntity backstageCatalogSystemEntity = new BackstageCatalogSystemEntity();

    BackstageCatalogEntity.Metadata metadata = new BackstageCatalogEntity.Metadata();
    metadata.setMetadata(projectDTO.getIdentifier(), projectDTO.getOrgIdentifier() + "-" + projectDTO.getIdentifier(),
        projectDTO.getName(), projectDTO.getDescription(), new ArrayList<>(projectDTO.getTags().values()), null);
    backstageCatalogSystemEntity.setMetadata(metadata);

    BackstageCatalogSystemEntity.Spec spec = new BackstageCatalogSystemEntity.Spec();
    spec.setOwner(ENTITY_UNKNOWN_OWNER);
    spec.setDomain(projectDTO.getOrgIdentifier());
    backstageCatalogSystemEntity.setSpec(spec);

    return backstageCatalogSystemEntity;
  }
}
