/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.GcpKmsConfigDTO;
import io.harness.secretmanagerclient.dto.GcpKmsConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.GcpSecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.GcpSecretManagerConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMConfigDTO;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigUpdateDTO;

import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.VaultConfig;

import java.util.Collections;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class SecretManagerConfigMapper {
  public static SecretManagerConfig fromDTO(SecretManagerConfigDTO dto) {
    if (null == dto) {
      return null;
    }
    switch (dto.getEncryptionType()) {
      case VAULT:
        return VaultConfigMapper.fromDTO((VaultConfigDTO) dto);
      case AZURE_VAULT:
        return AzureKeyVaultConfigMapper.fromDTO((AzureKeyVaultConfigDTO) dto);
      case GCP_KMS:
        return GcpKmsConfigMapper.fromDTO((GcpKmsConfigDTO) dto);
      case KMS:
        return AwsKmsConfigMapper.fromDTO((AwsKmsConfigDTO) dto);
      case AWS_SECRETS_MANAGER:
        return AwsSMConfigMapper.fromDTO((AwsSMConfigDTO) dto);
      case LOCAL:
        return LocalConfigMapper.fromDTO((LocalConfigDTO) dto);
      case CUSTOM_NG:
        return CustomConfigMapper.fromDTO((CustomSecretManagerConfigDTO) dto);
      case GCP_SECRETS_MANAGER:
        return GcpSecretManagerConfigMapper.fromDTO((GcpSecretManagerConfigDTO) dto);
      default:
        throw new UnsupportedOperationException("Secret Manager not supported");
    }
  }

  public static SecretManagerConfig applyUpdate(SecretManagerConfig secretManagerConfig,
      SecretManagerConfigUpdateDTO dto, boolean secretsPresentInSecretManager) {
    switch (dto.getEncryptionType()) {
      case VAULT:
        return VaultConfigMapper.applyUpdate(
            (VaultConfig) secretManagerConfig, (VaultConfigUpdateDTO) dto, secretsPresentInSecretManager);
      case AZURE_VAULT:
        return AzureKeyVaultConfigMapper.applyUpdate(
            (AzureVaultConfig) secretManagerConfig, (AzureKeyVaultConfigUpdateDTO) dto, secretsPresentInSecretManager);
      case GCP_KMS:
        return GcpKmsConfigMapper.applyUpdate((GcpKmsConfig) secretManagerConfig, (GcpKmsConfigUpdateDTO) dto);
      case KMS:
        return AwsKmsConfigMapper.applyUpdate((KmsConfig) secretManagerConfig, (AwsKmsConfigUpdateDTO) dto);
      case AWS_SECRETS_MANAGER:
        return AwsSMConfigMapper.applyUpdate((AwsSecretsManagerConfig) secretManagerConfig, (AwsSMConfigUpdateDTO) dto);
      case GCP_SECRETS_MANAGER:
        return GcpSecretManagerConfigMapper.applyUpdate(
            (GcpSecretsManagerConfig) secretManagerConfig, (GcpSecretManagerConfigUpdateDTO) dto);
      default:
        throw new UnsupportedOperationException("Secret Manager not supported");
    }
  }

  public static Set<String> getDelegateSelectors(SecretManagerConfigDTO dto) {
    if (null == dto) {
      return Collections.emptySet();
    }
    switch (dto.getEncryptionType()) {
      case VAULT:
        return ((VaultConfigDTO) dto).getDelegateSelectors();
      case AZURE_VAULT:
        return ((AzureKeyVaultConfigDTO) dto).getDelegateSelectors();
      case GCP_KMS:
        return ((GcpKmsConfigDTO) dto).getDelegateSelectors();
      case KMS:
        return ((AwsKmsConfigDTO) dto).getBaseAwsKmsConfigDTO().getDelegateSelectors();
      case AWS_SECRETS_MANAGER:
        return ((AwsSMConfigDTO) dto).getBaseAwsSMConfigDTO().getDelegateSelectors();
      case CUSTOM_NG:
        return ((CustomSecretManagerConfigDTO) dto).getDelegateSelectors();
      case GCP_SECRETS_MANAGER:
        return ((GcpSecretManagerConfigDTO) dto).getDelegateSelectors();
      default:
        return Collections.emptySet();
    }
  }
}
