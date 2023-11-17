/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.secret;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.core.beans.EncryptionType;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.AzureVaultConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.VaultConfig;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@OwnedBy(PL)
@Slf4j
public class EncryptionConfigProtoPojoMapper {
  public static EncryptionConfig map(final io.harness.delegate.core.beans.EncryptionConfig config) {
    // TODO: After implementing all encryption types, remove this check
    //    if (!config.getEncryptionType().equals(EncryptionType.LOCAL)) {
    //      log.warn("Encryption type {} not implemented", config.getEncryptionType().name());
    //      return null;
    //    }
    if (config.getEncryptionType().equals(EncryptionType.HASHICORP_VAULT)) {
      return VaultConfig.builder()
          .uuid(config.getUuid())
          .accountId(config.getAccountId())
          .encryptionType(EncryptionTypeProtoPojoMapper.map(config.getEncryptionType()))
          .vaultUrl(config.getEncryptionServiceUrl())
          .authToken(config.getVaultConfig().getAuthToken())
          .secretEngineName(config.getVaultConfig().getSecretEngineName())
          .secretEngineVersion(config.getVaultConfig().getSecretEngineVersion())
          .basePath(config.getVaultConfig().getBasePath())
          .namespace(config.getVaultConfig().getNamespace())
          .isReadOnly(config.getVaultConfig().getIsReadOnly())
          .appRoleId(config.getVaultConfig().getAppRoleId())
          .secretId(config.getVaultConfig().getSecretId())
          .renewAppRoleToken(false)
          .useVaultAgent(config.getVaultConfig().getUseVaultAgent())
          .sinkPath(config.getVaultConfig().getSinkPath())
          .useAwsIam(config.getVaultConfig().getUseAwsIam())
          .vaultAwsIamRole(config.getVaultConfig().getVaultAwsIamRole())
          .awsRegion(config.getVaultConfig().getAwsRegion())
          .xVaultAwsIamServerId(config.getVaultConfig().getXVaultAwsIamServerId())
          .useK8sAuth(config.getVaultConfig().getUseK8SAuth())
          .vaultK8sAuthRole(config.getVaultConfig().getVaultK8SAuthRole())
          .serviceAccountTokenPath(config.getVaultConfig().getServiceAccountTokenPath())
          .k8sAuthEndpoint(config.getVaultConfig().getK8SAuthEndpoint())
          .build();
    } else if (config.getEncryptionType().equals(EncryptionType.AZURE_VAULT)) {
      return AzureVaultConfig.builder()
          .clientId(config.getAzureVaultConfig().getClientId())
          .secretKey(config.getAzureVaultConfig().getSecretKey())
          .tenantId(config.getAzureVaultConfig().getTenantId())
          .vaultName(config.getAzureVaultConfig().getVaultName())
          .subscription(config.getAzureVaultConfig().getSubscription())
          .useManagedIdentity(config.getAzureVaultConfig().getUseManagedIdentity())
          .managedClientId(config.getAzureVaultConfig().getManagedClientId())
          .build();
    }
    return LocalEncryptionConfig.builder()
        .uuid(config.getUuid())
        .accountId(config.getAccountId())
        .name(config.getName())
        .encryptionType(EncryptionTypeProtoPojoMapper.map(config.getEncryptionType()))
        .build();
  }
}
