/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.secret.beans.dto.EnvironmentVariableDTO;
import io.harness.idp.secret.beans.entity.EnvironmentVariable;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class EnvironmentVariableMapper {
  public EnvironmentVariableDTO toDTO(EnvironmentVariable environmentVariable) {
    return EnvironmentVariableDTO.builder()
        .envName(environmentVariable.getEnvName())
        .secretIdentifier(environmentVariable.getSecretIdentifier())
        .accountIdentifier(environmentVariable.getAccountIdentifier())
        .createdAt(environmentVariable.getCreatedAt())
        .lastModifiedAt(environmentVariable.getLastModifiedAt())
        .isDeleted(environmentVariable.isDeleted())
        .deletedAt(environmentVariable.getDeletedAt())
        .build();
  }

  public EnvironmentVariable fromDTO(EnvironmentVariableDTO environmentVariableDTO) {
    return EnvironmentVariable.builder()
        .envName(environmentVariableDTO.getEnvName())
        .secretIdentifier(environmentVariableDTO.getSecretIdentifier())
        .accountIdentifier(environmentVariableDTO.getAccountIdentifier())
        .createdAt(environmentVariableDTO.getCreatedAt())
        .lastModifiedAt(environmentVariableDTO.getLastModifiedAt())
        .isDeleted(environmentVariableDTO.isDeleted())
        .deletedAt(environmentVariableDTO.getDeletedAt())
        .build();
  }
}
