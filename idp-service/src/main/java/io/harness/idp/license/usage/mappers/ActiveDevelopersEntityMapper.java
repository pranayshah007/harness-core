/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersEntityMapper {
  public ActiveDevelopersEntity fromDto(IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCaptureDTO) {
    return ActiveDevelopersEntity.builder()
        .accountIdentifier(idpLicenseUsageUserCaptureDTO.getAccountIdentifier())
        .userIdentifier(idpLicenseUsageUserCaptureDTO.getUserIdentifier())
        .email(idpLicenseUsageUserCaptureDTO.getEmail())
        .userName(idpLicenseUsageUserCaptureDTO.getUserName())
        .lastAccessedAt(idpLicenseUsageUserCaptureDTO.getAccessedAt())
        .createdAt(System.currentTimeMillis())
        .build();
  }

  public IDPLicenseUsageUserCaptureDTO toDto(ActiveDevelopersEntity activeDevelopersEntity) {
    return IDPLicenseUsageUserCaptureDTO.builder()
        .accountIdentifier(activeDevelopersEntity.getAccountIdentifier())
        .userIdentifier(activeDevelopersEntity.getUserIdentifier())
        .email(activeDevelopersEntity.getEmail())
        .userName(activeDevelopersEntity.getUserName())
        .accessedAt(activeDevelopersEntity.getLastAccessedAt())
        .build();
  }
}
