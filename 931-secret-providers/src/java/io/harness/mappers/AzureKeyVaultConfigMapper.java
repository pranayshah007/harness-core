/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.mappers.NgSecretConfigMapperHelper.ngMetaDataFromDto;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SecretManagementException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigUpdateDTO;

import software.wings.beans.AzureVaultConfig;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class AzureKeyVaultConfigMapper {
  public static AzureVaultConfig fromDTO(AzureKeyVaultConfigDTO azureKeyVaultConfigDTO) {
    AzureVaultConfig azureVaultConfig =
        AzureVaultConfig.builder()
            .name(azureKeyVaultConfigDTO.getName())
            .clientId(azureKeyVaultConfigDTO.getClientId())
            .secretKey(azureKeyVaultConfigDTO.getSecretKey())
            .subscription(azureKeyVaultConfigDTO.getSubscription())
            .tenantId(azureKeyVaultConfigDTO.getTenantId())
            .azureEnvironmentType(azureKeyVaultConfigDTO.getAzureEnvironmentType())
            .vaultName(azureKeyVaultConfigDTO.getVaultName())
            .delegateSelectors(azureKeyVaultConfigDTO.getDelegateSelectors())
            .useManagedIdentity(azureKeyVaultConfigDTO.getUseManagedIdentity())
            .azureManagedIdentityType(azureKeyVaultConfigDTO.getAzureManagedIdentityType())
            .managedClientId(azureKeyVaultConfigDTO.getManagedClientId())
            .build();
    azureVaultConfig.setNgMetadata(ngMetaDataFromDto(azureKeyVaultConfigDTO));
    azureVaultConfig.setAccountId(azureKeyVaultConfigDTO.getAccountIdentifier());
    azureVaultConfig.setEncryptionType(azureKeyVaultConfigDTO.getEncryptionType());
    azureVaultConfig.setDefault(azureKeyVaultConfigDTO.isDefault());
    return azureVaultConfig;
  }

  private static void checkEqualValues(Object x, Object y, String fieldName) {
    if (x != null && !x.equals(y)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          String.format(
              "Cannot change the value of %s since there are secrets already present in azure key vault. Please delete or migrate them and try again.",
              fieldName),
          USER);
    }
  }

  public static AzureVaultConfig applyUpdate(
      AzureVaultConfig vaultConfig, AzureKeyVaultConfigUpdateDTO updateDTO, boolean secretsPresentInVault) {
    if (secretsPresentInVault) {
      checkEqualValues(vaultConfig.getVaultName(), updateDTO.getVaultName(), "vault name");
    }
    vaultConfig.setClientId(updateDTO.getClientId());
    vaultConfig.setSubscription(updateDTO.getSubscription());
    vaultConfig.setAzureEnvironmentType(updateDTO.getAzureEnvironmentType());
    vaultConfig.setTenantId(updateDTO.getTenantId());
    vaultConfig.setVaultName(updateDTO.getVaultName());
    if (Optional.ofNullable(updateDTO.getSecretKey()).isPresent()) {
      vaultConfig.setSecretKey(updateDTO.getSecretKey());
    }
    vaultConfig.setDefault(updateDTO.isDefault());
    vaultConfig.setName(updateDTO.getName());

    if (!Optional.ofNullable(vaultConfig.getNgMetadata()).isPresent()) {
      vaultConfig.setNgMetadata(NGSecretManagerMetadata.builder().build());
    }
    vaultConfig.getNgMetadata().setTags(TagMapper.convertToList(updateDTO.getTags()));
    vaultConfig.getNgMetadata().setDescription(updateDTO.getDescription());
    return vaultConfig;
  }
}
