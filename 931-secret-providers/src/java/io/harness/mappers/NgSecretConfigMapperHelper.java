/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import io.harness.ng.core.mapper.TagMapper;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NgSecretConfigMapperHelper {
  public static NGSecretManagerMetadata ngMetaDataFromDto(SecretManagerConfigDTO dto) {
    return NGSecretManagerMetadata.builder()
        .identifier(dto.getIdentifier())
        .projectIdentifier(dto.getProjectIdentifier())
        .orgIdentifier(dto.getOrgIdentifier())
        .accountIdentifier(dto.getAccountIdentifier())
        .description(dto.getDescription())
        .tags(TagMapper.convertToList(dto.getTags()))
        .harnessManaged(dto.isHarnessManaged())
        .build();
  }
}
