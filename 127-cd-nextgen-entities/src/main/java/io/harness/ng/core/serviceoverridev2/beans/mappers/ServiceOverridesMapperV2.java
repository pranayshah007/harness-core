/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverridev2.beans.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;

import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class ServiceOverridesMapperV2 {
  public NGServiceOverridesEntity toEntity(@NotNull String accountId, @NonNull ServiceOverridesResponseDTOV2 dto) {
    return NGServiceOverridesEntity.builder()
        .identifier(dto.getIdentifier())
        .accountId(accountId)
        .orgIdentifier(dto.getOrgIdentifier())
        .projectIdentifier(dto.getProjectIdentifier())
        .environmentRef(dto.getEnvironmentRef())
        .serviceRef(dto.getServiceRef())
        .infraIdentifier(dto.getInfraIdentifier())
        .spec(dto.getSpec())
        .type(dto.getType())
        .build();
  }

  public ServiceOverridesResponseDTOV2 toResponseDTO(@NonNull NGServiceOverridesEntity entity) {
    return ServiceOverridesResponseDTOV2.builder()
        .identifier(entity.getIdentifier())
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .environmentRef(entity.getEnvironmentRef())
        .serviceRef(entity.getServiceRef())
        .infraIdentifier(entity.getInfraIdentifier())
        .spec(entity.getSpec())
        .type(entity.getType())
        .build();
  }
}
