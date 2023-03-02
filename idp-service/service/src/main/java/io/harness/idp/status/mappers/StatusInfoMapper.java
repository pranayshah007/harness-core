/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.status.beans.StatusInfoEntity;
import io.harness.idp.status.enums.StatusType;
import io.harness.spec.server.idp.v1.model.StatusInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class StatusInfoMapper {
  public StatusInfo toDTO(StatusInfoEntity statusInfoEntity) {
    StatusInfo statusInfo = new StatusInfo();
    statusInfo.setCurrentStatus(statusInfoEntity.getStatus());
    statusInfo.setReason(statusInfoEntity.getReason());
    statusInfo.setUpdatedAt(statusInfoEntity.getLastModifiedAt());
    return statusInfo;
  }

  public StatusInfoEntity fromDTO(StatusInfo statusInfo, String accountIdentifier, String type) {
    return StatusInfoEntity.builder()
        .accountIdentifier(accountIdentifier)
        .type(StatusType.valueOf(type.toUpperCase()))
        .status(statusInfo.getCurrentStatus())
        .reason(statusInfo.getReason())
        .build();
  }
}
