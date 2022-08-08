/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.GcpKmsConfigDTO;
import io.harness.secretmanagerclient.dto.GcpKmsConfigUpdateDTO;

import software.wings.beans.GcpKmsConfig;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class GcpKmsConfigMapper {
  public static GcpKmsConfig fromDTO(GcpKmsConfigDTO gcpKmsConfigDTO) {
    GcpKmsConfig gcpKmsConfig = new GcpKmsConfig(gcpKmsConfigDTO.getName(), gcpKmsConfigDTO.getProjectId(),
        gcpKmsConfigDTO.getRegion(), gcpKmsConfigDTO.getKeyRing(), gcpKmsConfigDTO.getKeyName(),
        gcpKmsConfigDTO.getCredentials(), gcpKmsConfigDTO.getDelegateSelectors());
    gcpKmsConfig.setNgMetadata(SecretManagerConfigMapper.ngMetaDataFromDto(gcpKmsConfigDTO));
    gcpKmsConfig.setAccountId(gcpKmsConfigDTO.getAccountIdentifier());
    gcpKmsConfig.setEncryptionType(gcpKmsConfigDTO.getEncryptionType());
    gcpKmsConfig.setDefault(gcpKmsConfigDTO.isDefault());
    return gcpKmsConfig;
  }

  public static GcpKmsConfig applyUpdate(GcpKmsConfig gcpKmsConfig, GcpKmsConfigUpdateDTO gcpKmsConfigDTO) {
    gcpKmsConfig.setProjectId(gcpKmsConfigDTO.getProjectId());
    if (isNotEmpty(gcpKmsConfigDTO.getCredentials())) {
      gcpKmsConfig.setCredentials(gcpKmsConfigDTO.getCredentials());
    }
    gcpKmsConfig.setKeyName(gcpKmsConfigDTO.getKeyName());
    gcpKmsConfig.setKeyRing(gcpKmsConfigDTO.getKeyRing());
    gcpKmsConfig.setDefault(gcpKmsConfigDTO.isDefault());
    gcpKmsConfig.setName(gcpKmsConfigDTO.getName());
    gcpKmsConfig.setRegion(gcpKmsConfigDTO.getRegion());
    if (!Optional.ofNullable(gcpKmsConfig.getNgMetadata()).isPresent()) {
      gcpKmsConfig.setNgMetadata(NGSecretManagerMetadata.builder().build());
    }
    gcpKmsConfig.getNgMetadata().setTags(TagMapper.convertToList(gcpKmsConfigDTO.getTags()));
    gcpKmsConfig.getNgMetadata().setDescription(gcpKmsConfigDTO.getDescription());
    return gcpKmsConfig;
  }
}
