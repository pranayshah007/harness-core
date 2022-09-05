/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.mappers.SecretManagerConfigMapper.ngMetaDataFromDto;

import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO;

import software.wings.beans.CustomSecretNGManagerConfig;

public class CustomConfigMapper {
  public static CustomSecretNGManagerConfig fromDTO(CustomSecretManagerConfigDTO customSecretManagerConfigDTO) {
    CustomSecretNGManagerConfig customSecretNGManagerConfig =
        CustomSecretNGManagerConfig.builder()
            .delegateSelectors(customSecretManagerConfigDTO.getDelegateSelectors())
            .onDelegate(customSecretManagerConfigDTO.isOnDelegate())
            .connectorRef(customSecretManagerConfigDTO.getConnectorRef())
            .host(customSecretManagerConfigDTO.getHost())
            .workingDirectory(customSecretManagerConfigDTO.getWorkingDirectory())
            .template(customSecretManagerConfigDTO.getTemplate())
            .script(customSecretManagerConfigDTO.getScript())
            .build();

    customSecretNGManagerConfig.setNgMetadata(ngMetaDataFromDto(customSecretManagerConfigDTO));
    customSecretNGManagerConfig.setAccountId(customSecretManagerConfigDTO.getAccountIdentifier());
    customSecretNGManagerConfig.setEncryptionType(customSecretManagerConfigDTO.getEncryptionType());
    customSecretNGManagerConfig.setDefault(customSecretManagerConfigDTO.isDefault());
    return customSecretNGManagerConfig;
  }
}