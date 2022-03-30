/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.mappers.SecretManagerConfigMapper.ngMetaDataFromDto;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SecretManagementException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.azureblob.AzureBlobConfigDTO;
import io.harness.secretmanagerclient.dto.azureblob.AzureBlobConfigUpdateDTO;

import software.wings.beans.AzureBlobConfig;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class AzureBlobConfigMapper {
  public static AzureBlobConfig fromDTO(AzureBlobConfigDTO azureBlobConfigDTO) {
    AzureBlobConfig azureBlobConfig = AzureBlobConfig.builder()
                                          .name(azureBlobConfigDTO.getName())
                                          .clientId(azureBlobConfigDTO.getClientId())
                                          .secretKey(azureBlobConfigDTO.getSecretKey())
                                          .subscription(azureBlobConfigDTO.getSubscription())
                                          .tenantId(azureBlobConfigDTO.getTenantId())
                                          .connectionString(azureBlobConfigDTO.getConnectionString())
                                          .containerName(azureBlobConfigDTO.getContainerName())
                                          .keyId(azureBlobConfigDTO.getKeyId())
                                          .keyName(azureBlobConfigDTO.getKeyName())
                                          .azureEnvironmentType(azureBlobConfigDTO.getAzureEnvironmentType())
                                          .vaultName(azureBlobConfigDTO.getVaultName())
                                          .delegateSelectors(azureBlobConfigDTO.getDelegateSelectors())
                                          .build();
    azureBlobConfig.setNgMetadata(ngMetaDataFromDto(azureBlobConfigDTO));
    azureBlobConfig.setAccountId(azureBlobConfigDTO.getAccountIdentifier());
    azureBlobConfig.setEncryptionType(azureBlobConfigDTO.getEncryptionType());
    azureBlobConfig.setDefault(azureBlobConfigDTO.isDefault());
    return azureBlobConfig;
  }

  private static void checkEqualValues(Object x, Object y, String fieldName) {
    if (x != null && !x.equals(y)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          String.format(
              "Cannot change the value of %s since there are secrets already present in azure key blob. Please delete or migrate them and try again.",
              fieldName),
          USER);
    }
  }

  public static AzureBlobConfig applyUpdate(
      AzureBlobConfig blobConfig, AzureBlobConfigUpdateDTO updateDTO, boolean secretsPresentInBlob) {
    if (secretsPresentInBlob) {
      checkEqualValues(blobConfig.getVaultName(), updateDTO.getVaultName(), "vault name");
    }
    blobConfig.setClientId(updateDTO.getClientId());
    blobConfig.setSubscription(updateDTO.getSubscription());
    blobConfig.setAzureEnvironmentType(updateDTO.getAzureEnvironmentType());
    blobConfig.setTenantId(updateDTO.getTenantId());
    blobConfig.setVaultName(updateDTO.getVaultName());
    if (Optional.ofNullable(updateDTO.getSecretKey()).isPresent()) {
      blobConfig.setSecretKey(updateDTO.getSecretKey());
    }
    blobConfig.setDefault(updateDTO.isDefault());
    blobConfig.setName(updateDTO.getName());

    if (!Optional.ofNullable(blobConfig.getNgMetadata()).isPresent()) {
      blobConfig.setNgMetadata(NGSecretManagerMetadata.builder().build());
    }
    blobConfig.getNgMetadata().setTags(TagMapper.convertToList(updateDTO.getTags()));
    blobConfig.getNgMetadata().setDescription(updateDTO.getDescription());
    return blobConfig;
  }
}
