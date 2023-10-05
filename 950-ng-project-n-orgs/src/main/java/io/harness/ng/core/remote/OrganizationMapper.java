/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.entities.Organization;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class OrganizationMapper {
  public static Organization toOrganization(OrganizationDTO dto) {
    return Organization.builder()
        .tags(convertToList(dto.getTags()))
        .description(Optional.ofNullable(dto.getDescription()).orElse(""))
        .identifier(dto.getIdentifier())
        .name(dto.getName())
        .harnessManaged(dto.isHarnessManaged())
        .version(dto.getVersion())
        .build();
  }

  public static OrganizationDTO writeDto(Organization organization) {
    OrganizationDTO organizationDTO = OrganizationDTO.builder()
                                          .description(organization.getDescription())
                                          .identifier(organization.getIdentifier())
                                          .name(organization.getName())
                                          .tags(convertToMap(organization.getTags()))
                                          .build();
    organizationDTO.setHarnessManaged(Boolean.TRUE.equals(organization.getHarnessManaged()));
    return organizationDTO;
  }

  public static OrganizationResponse toResponseWrapper(Organization organization) {
    return OrganizationResponse.builder()
        .createdAt(organization.getCreatedAt())
        .lastModifiedAt(organization.getLastModifiedAt())
        .harnessManaged(Boolean.TRUE.equals(organization.getHarnessManaged()))
        .organization(writeDto(organization))
        .build();
  }
}
