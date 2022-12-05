/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.TasInstanceInfoDTO;
import io.harness.entities.instanceinfo.TasInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class TasInstanceInfoMapper {
  public TasInstanceInfoDTO toDTO(TasInstanceInfo tasInstanceInfo) {
    return TasInstanceInfoDTO.builder()
        .id(tasInstanceInfo.getId())
        .instanceIndex(tasInstanceInfo.getInstanceIndex())
        .space(tasInstanceInfo.getSpace())
        .organization(tasInstanceInfo.getOrganization())
        .tasApplicationName(tasInstanceInfo.getTasApplicationName())
        .tasApplicationGuid(tasInstanceInfo.getTasApplicationGuid())
        .build();
  }
  public TasInstanceInfo toEntity(TasInstanceInfoDTO tasInstanceInfoDTO) {
    return TasInstanceInfo.builder()
        .id(tasInstanceInfoDTO.getId())
        .instanceIndex(tasInstanceInfoDTO.getInstanceIndex())
        .space(tasInstanceInfoDTO.getSpace())
        .organization(tasInstanceInfoDTO.getOrganization())
        .tasApplicationName(tasInstanceInfoDTO.getTasApplicationName())
        .tasApplicationGuid(tasInstanceInfoDTO.getTasApplicationGuid())
        .build();
  }
}
