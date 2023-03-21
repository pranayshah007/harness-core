/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.yamlhandlers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.ENCRYPT_DECRYPT_ERROR;
import static io.harness.eraro.ErrorCode.INVALID_FORMAT;
import static io.harness.eraro.ErrorCode.RESOURCE_NOT_FOUND;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.security.encryption.EncryptionType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretText;
import io.harness.exception.SecretManagementException;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.SecretService;
import io.harness.secrets.SecretsDao;
import io.harness.utils.featureflaghelper.CGFeatureFlagHelperServiceImpl;

import software.wings.beans.VaultConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@ValidateOnExecution
@Singleton
@OwnedBy(PL)
@Slf4j
public class SecretYamlHandlerImpl implements SecretYamlHandler {
  private final SecretService secretService;
  private final SecretManagerConfigService secretManagerConfigService;
  private final SecretsDao secretsDao;

  @Inject private CGFeatureFlagHelperServiceImpl smFeatureFlagHelper;

  @Inject
  public SecretYamlHandlerImpl(
      SecretService secretService, SecretManagerConfigService secretManagerConfigService, SecretsDao secretsDao) {
    this.secretService = secretService;
    this.secretManagerConfigService = secretManagerConfigService;
    this.secretsDao = secretsDao;
  }

  @Override
  public String toYaml(String accountId, String secretId) {
    EncryptedData encryptedData;
    Optional<EncryptedData> optionalEncryptedData = secretsDao.getSecretById(accountId, secretId);
    if (optionalEncryptedData.isPresent()) {
      encryptedData = optionalEncryptedData.get();
    } else {
      encryptedData = secretsDao.getSecretByName(accountId, secretId).<SecretManagementException>orElseThrow(() -> {
        throw new SecretManagementException(
            SECRET_MANAGEMENT_ERROR, "The secret does not exist or you are unauthorized to view the secret.", USER);
      });
    }
    return toYaml(encryptedData);
  }

  @Override
  public String toYaml(EncryptedData encryptedData) {
    if (encryptedData.getEncryptionType() == VAULT
        && !smFeatureFlagHelper.isEnabled(
            encryptedData.getAccountId(), FeatureName.SPG_CHANGE_SECRET_VAULT_PATTERN_ON_YAML)) {
      return encryptedData.getEncryptionType().getYamlName() + ":" + getVaultSecretRefUrl(encryptedData);
    } else {
      return encryptedData.getEncryptionType().getYamlName() + ":" + encryptedData.getName();
    }
  }

  private String getVaultSecretRefUrl(EncryptedData encryptedData) {
    VaultConfig vaultConfig = (VaultConfig) secretManagerConfigService.getSecretManager(
        encryptedData.getAccountId(), encryptedData.getKmsId(), true);
    String basePath = vaultConfig.getBasePath() == null
        ? VaultConfig.DEFAULT_BASE_PATH
        : VaultConfig.PATH_SEPARATOR.concat(StringUtils.strip(vaultConfig.getBasePath(), VaultConfig.PATH_SEPARATOR));
    String vaultPath = encryptedData.isInlineSecret() ? basePath + VaultConfig.PATH_SEPARATOR
            + encryptedData.getEncryptionKey() + VaultConfig.KEY_SPEARATOR + VaultConfig.DEFAULT_KEY_NAME
                                                      : encryptedData.getPath();
    return URL_ROOT_PREFIX + vaultConfig.getName() + vaultPath;
  }

  @Override
  public EncryptedData fromYaml(String accountId, String yamlRef) {
    String[] tags = yamlRef.split(":");
    String encryptionTypeYamlName = tags[0];
    String encryptedDataRef;
    try {
      encryptedDataRef = tags[1];
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new SecretManagementException(INVALID_FORMAT, "SecretReference format is not valid", USER);
    }

    if (VAULT.getYamlName().equals(encryptionTypeYamlName) && encryptedDataRef.startsWith(URL_ROOT_PREFIX)) {
      if (!encryptedDataRef.contains(VaultConfig.KEY_SPEARATOR)) {
        throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR,
            "No key name separator # found in the Vault secret reference " + encryptedDataRef, USER);
      }
      // This is a new Vault path based reference
      ParsedVaultSecretRef vaultSecretRef = parse(encryptedDataRef, accountId);
      Optional<EncryptedData> encryptedDataOptional = secretsDao.getSecretByKeyOrPath(
          accountId, VAULT, vaultSecretRef.getRelativePath(), vaultSecretRef.getFullPath());
      return encryptedDataOptional.orElseGet(
          () -> createNewSecretTextFromVaultPathReference(vaultSecretRef, accountId));
    } else {
      // This is an old id based reference
      Optional<EncryptedData> optionalEncryptedData = secretsDao.getSecretById(accountId, encryptedDataRef);
      if (optionalEncryptedData.isPresent()) {
        return optionalEncryptedData.get();
      } else {
        return secretsDao.getSecretByName(accountId, encryptedDataRef).<SecretManagementException>orElseThrow(() -> {
          throw new SecretManagementException(RESOURCE_NOT_FOUND,
              String.format("Could not find secret: %s in account: %s", encryptedDataRef, accountId), USER);
        });
      }
    }
  }

  private ParsedVaultSecretRef parse(String encryptedDataRef, String accountId) {
    if (!encryptedDataRef.startsWith(URL_ROOT_PREFIX) || !encryptedDataRef.contains(VaultConfig.KEY_SPEARATOR)) {
      throw new SecretManagementException(
          ENCRYPT_DECRYPT_ERROR, "Vault secret reference '" + encryptedDataRef + "' has illegal format", USER);
    } else {
      String secretMangerNameAndPath = encryptedDataRef.substring(2);

      int index = secretMangerNameAndPath.indexOf(VaultConfig.PATH_SEPARATOR);
      String fullPath = secretMangerNameAndPath.substring(index);
      String secretManagerName = secretMangerNameAndPath.substring(0, index);
      VaultConfig vaultConfig =
          (VaultConfig) secretManagerConfigService.getSecretManagerByName(accountId, secretManagerName, VAULT, true);
      if (vaultConfig == null) {
        throw new SecretManagementException(
            ENCRYPT_DECRYPT_ERROR, "Vault secret manager '" + secretManagerName + "' doesn't exist", USER);
      }
      String basePath = vaultConfig.getBasePath() == null
          ? VaultConfig.DEFAULT_BASE_PATH
          : VaultConfig.PATH_SEPARATOR.concat(StringUtils.strip(vaultConfig.getBasePath(), VaultConfig.PATH_SEPARATOR));
      index = fullPath.indexOf(VaultConfig.KEY_SPEARATOR);
      String keyName = fullPath.substring(index + 1);

      String vaultPath = null;
      if (fullPath.startsWith(basePath)) {
        vaultPath = fullPath.substring(basePath.length() + 1, index);
      }

      return ParsedVaultSecretRef.builder()
          .secretManagerName(secretManagerName)
          .vaultConfigId(vaultConfig.getUuid())
          .vaultConfig(vaultConfig)
          .basePath(basePath)
          .fullPath(fullPath)
          .relativePath(vaultPath)
          .keyName(keyName)
          .build();
    }
  }

  private EncryptedData createNewSecretTextFromVaultPathReference(
      ParsedVaultSecretRef vaultSecretRef, String accountId) {
    String secretName = getEncryptedDataNameFromRef(vaultSecretRef.fullPath);
    SecretText secretText = SecretText.builder()
                                .path(vaultSecretRef.fullPath)
                                .name(secretName)
                                .kmsId(vaultSecretRef.vaultConfigId)
                                .usageRestrictions(secretManagerConfigService.getMaximalAllowedScopes(
                                    accountId, vaultSecretRef.vaultConfigId))
                                .scopedToAccount(false)
                                .build();

    return secretService.createSecret(accountId, secretText, true);
  }

  private String getEncryptedDataNameFromRef(String fullVaultPath) {
    return YAML_PREFIX
        + fullVaultPath.replaceAll(VaultConfig.PATH_SEPARATOR, "_").replaceAll(VaultConfig.KEY_SPEARATOR, "_");
  }

  @Getter
  @Builder
  private static class ParsedVaultSecretRef {
    private final String secretManagerName;
    private final String vaultConfigId;
    private final VaultConfig vaultConfig;
    private final String basePath;
    private final String relativePath;
    private final String fullPath;
    private final String keyName;
  }
}
