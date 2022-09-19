/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.secretmanagerclient.dto.GCPSMConfigDTO;
import io.harness.secretmanagerclient.dto.GCPSMConfigUpdateDTO;

import software.wings.beans.GcpSecretsManagerConfig;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class GCPSMConfigMapper {
  public static GcpSecretsManagerConfig fromDTO(GCPSMConfigDTO configDTO) {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = GcpSecretsManagerConfig.builder()
                                                          .name(configDTO.getName())
                                                          .delegateSelectors(configDTO.getDelegateSelectors())
                                                          .credentials(configDTO.getCredentials())
                                                          .build();
    gcpSecretsManagerConfig.setNgMetadata(SecretManagerConfigMapper.ngMetaDataFromDto(configDTO));
    gcpSecretsManagerConfig.setAccountId(configDTO.getAccountIdentifier());
    gcpSecretsManagerConfig.setEncryptionType(configDTO.getEncryptionType());
    gcpSecretsManagerConfig.setDefault(configDTO.isDefault());
    return gcpSecretsManagerConfig;
  }

  public static GcpSecretsManagerConfig applyUpdate(
      GcpSecretsManagerConfig gcpSecretsManagerConfig, GCPSMConfigUpdateDTO configUpdateDTO) {
    if (isNotEmpty(configUpdateDTO.getCredentials())) {
      gcpSecretsManagerConfig.setCredentials(configUpdateDTO.getCredentials());
    }
    gcpSecretsManagerConfig.setDefault(configUpdateDTO.isDefault());
    gcpSecretsManagerConfig.setName(configUpdateDTO.getName());
    if (!Optional.ofNullable(gcpSecretsManagerConfig.getNgMetadata()).isPresent()) {
      gcpSecretsManagerConfig.setNgMetadata(NGSecretManagerMetadata.builder().build());
    }
    gcpSecretsManagerConfig.getNgMetadata().setTags(TagMapper.convertToList(configUpdateDTO.getTags()));
    gcpSecretsManagerConfig.getNgMetadata().setDescription(configUpdateDTO.getDescription());
    return gcpSecretsManagerConfig;
  }
}
