/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mappers.SecretManagerConfigMapper.ngMetaDataFromDto;

import io.harness.annotations.dev.OwnedBy;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;

import software.wings.beans.LocalEncryptionConfig;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class LocalConfigMapper {
  public static LocalEncryptionConfig fromDTO(LocalConfigDTO localConfigDTO) {
    LocalEncryptionConfig localConfig = LocalEncryptionConfig.builder().name(localConfigDTO.getName()).build();
    localConfig.setNgMetadata(ngMetaDataFromDto(localConfigDTO));
    localConfig.setAccountId(localConfigDTO.getAccountIdentifier());
    localConfig.setEncryptionType(localConfigDTO.getEncryptionType());
    localConfig.setDefault(localConfigDTO.isDefault());
    return localConfig;
  }
}
