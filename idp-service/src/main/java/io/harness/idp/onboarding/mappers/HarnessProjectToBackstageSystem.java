/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.mappers;

import static io.harness.idp.backstagebeans.Constants.ENTITY_UNKNOWN_OWNER;
import static io.harness.idp.common.CommonUtils.truncateEntityName;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.backstagebeans.BackstageCatalogSystemEntity;
import io.harness.ng.core.dto.ProjectDTO;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class HarnessProjectToBackstageSystem
    implements HarnessEntityToBackstageEntity<ProjectDTO, BackstageCatalogSystemEntity> {
  public final List<String> entityNamesSeenSoFar = new ArrayList<>();

  @Override
  public BackstageCatalogSystemEntity map(ProjectDTO projectDTO) {
    BackstageCatalogSystemEntity backstageCatalogSystemEntity = new BackstageCatalogSystemEntity();

    BackstageCatalogEntity.Metadata metadata = new BackstageCatalogEntity.Metadata();
    metadata.setMetadata(projectDTO.getIdentifier(), projectDTO.getOrgIdentifier() + "-" + projectDTO.getIdentifier(),
        truncateEntityName(projectDTO.getIdentifier()), projectDTO.getName(), projectDTO.getDescription(),
        getTags(projectDTO.getTags()), null);
    backstageCatalogSystemEntity.setMetadata(metadata);

    BackstageCatalogSystemEntity.Spec spec = new BackstageCatalogSystemEntity.Spec();
    spec.setOwner(ENTITY_UNKNOWN_OWNER);
    spec.setDomain(truncateEntityName(projectDTO.getOrgIdentifier()));
    backstageCatalogSystemEntity.setSpec(spec);

    if (entityNamesSeenSoFar.contains(projectDTO.getIdentifier())) {
      backstageCatalogSystemEntity.getMetadata().setName(
          truncateEntityName(backstageCatalogSystemEntity.getMetadata().getAbsoluteIdentifier()));
    }

    entityNamesSeenSoFar.add(projectDTO.getIdentifier());

    return backstageCatalogSystemEntity;
  }
}
