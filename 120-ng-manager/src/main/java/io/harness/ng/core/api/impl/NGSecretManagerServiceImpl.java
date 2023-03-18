/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;
import static io.harness.remote.client.CGRestUtils.getResponse;
import static io.harness.security.encryption.AccessType.APP_ROLE;
import static io.harness.security.encryption.EncryptionType.AWS_SECRETS_MANAGER;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.CUSTOM_NG;
import static io.harness.security.encryption.EncryptionType.GCP_SECRETS_MANAGER;
import static io.harness.security.encryption.EncryptionType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.CustomSecretManagerHelper;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.connector.services.NGVaultService;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.WingsException;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestDTO;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import software.wings.beans.VaultConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class NGSecretManagerServiceImpl implements NGSecretManagerService {
  private final SecretManagerClient secretManagerClient;
  private final NGConnectorSecretManagerService ngConnectorSecretManagerService;
  private final KmsEncryptorsRegistry kmsEncryptorsRegistry;
  private final VaultEncryptorsRegistry vaultEncryptorsRegistry;
  private final CustomEncryptorsRegistry customEncryptorsRegistry;
  private final NGVaultService ngVaultService;
  private final CustomSecretManagerHelper customSecretManagerHelper;
  private final RetryConfig config = RetryConfig.custom()
                                         .maxAttempts(5)
                                         .retryExceptions(Exception.class)
                                         .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2))
                                         .build();
  ;
  private final RetryRegistry registry = RetryRegistry.of(config);
  private final Retry retry = registry.retry("cgManagerSecretService", config);
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Override
  public SecretManagerConfigDTO createSecretManager(@NotNull SecretManagerConfigDTO secretManagerConfig) {
    return getResponse(secretManagerClient.createSecretManager(secretManagerConfig));
  }

  @Override
  public SecretManagerConfigDTO updateSecretManager(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String identifier,
      @NotNull SecretManagerConfigUpdateDTO secretManagerConfigUpdateDTO) {
    return getResponse(secretManagerClient.updateSecretManager(
        identifier, accountIdentifier, orgIdentifier, projectIdentifier, secretManagerConfigUpdateDTO));
  }

  @Override
  public SecretManagerConfigDTO getSecretManager(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String identifier, boolean maskSecrets) {
    return ngConnectorSecretManagerService.getUsingIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, maskSecrets);
  }

  @Override
  public boolean validateNGSecretManager(
      @NotNull String accountIdentifier, SecretManagerConfigDTO secretManagerConfigDTO) {
    boolean validationResult = false;
    if (null != secretManagerConfigDTO) {
      EncryptionConfig encryptionConfig = SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO);
      try {
        switch (encryptionConfig.getType()) {
          case VAULT:
            Set<EncryptionType> vaultSet = EnumSet.of(AZURE_VAULT, AWS_SECRETS_MANAGER, GCP_SECRETS_MANAGER);
            if (vaultSet.contains(encryptionConfig.getEncryptionType())) {
              validationResult = vaultEncryptorsRegistry.getVaultEncryptor(encryptionConfig.getEncryptionType())
                                     .validateSecretManagerConfiguration(accountIdentifier, encryptionConfig);
            } else {
              VaultConfig vaultConfig = (VaultConfig) encryptionConfig;
              if (APP_ROLE.equals(vaultConfig.getAccessType())
                  && (ngFeatureFlagHelperService.isEnabled(
                      accountIdentifier, FeatureName.DO_NOT_RENEW_APPROLE_TOKEN))) {
                vaultConfig.setRenewAppRoleToken(false);
              }
              if (!vaultConfig.isReadOnly()) {
                validationResult = vaultEncryptorsRegistry.getVaultEncryptor(VAULT).validateSecretManagerConfiguration(
                    accountIdentifier, vaultConfig);
              } else {
                validationResult = true;
              }
            }
            break;
          case KMS:
            KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(encryptionConfig);
            validationResult = kmsEncryptor.validateKmsConfiguration(encryptionConfig.getAccountId(), encryptionConfig);
            break;
          case CUSTOM:
            CustomSecretManagerConfigDTO customNGSecretManagerConfigDTO =
                (CustomSecretManagerConfigDTO) secretManagerConfigDTO;

            Set<EncryptedDataParams> encryptedDataParamsSet =
                customSecretManagerHelper.prepareEncryptedDataParamsSet(customNGSecretManagerConfigDTO);
            encryptionConfig.setEncryptionType(CUSTOM_NG);
            CustomEncryptor customEncryptor = customEncryptorsRegistry.getCustomEncryptor(CUSTOM_NG);
            validationResult =
                customEncryptor.validateReference(accountIdentifier, encryptedDataParamsSet, encryptionConfig);
            break;
          default:
            String errorMessage = " Encryptor for validate reference task for encryption config"
                + encryptionConfig.getName() + " not configured";
            log.error("Validation for Secret Manager/KMS failed: " + encryptionConfig.getName() + errorMessage);
        }
      } catch (WingsException wingsException) {
        throw wingsException;
      } catch (Exception exception) {
        log.error("Validation for Secret Manager/KMS failed: " + encryptionConfig.getName(), exception);
        throw exception;
      }
    }
    return validationResult;
  }

  @Override
  public SecretManagerConfigDTO getGlobalSecretManager(String accountIdentifier) {
    try {
      return ngConnectorSecretManagerService.getUsingIdentifier(
          GLOBAL_ACCOUNT_ID, null, null, HARNESS_SECRET_MANAGER_IDENTIFIER, true);
    } catch (Exception e) {
      log.error("Global Secret manager Not found in NG. Calling CG.");
    }
    return getGlobalSecretManagerFromCGWithRetry(accountIdentifier);
  }

  @Override
  public SecretManagerConfigDTO getGlobalSecretManagerFromCG(String accountIdentifier) {
    return getGlobalSecretManagerFromCGWithRetry(accountIdentifier);
  }

  private SecretManagerConfigDTO getGlobalSecretManagerFromCGWithRetry(String accountIdentifier) {
    log.info("[GetGlobalSecretManagerFromCGWithRetry]: Getting global secret manager from CG for account:{}",
        accountIdentifier);
    Supplier<SecretManagerConfigDTO> retryingSecretManagerConfigDTOSupplier =
        Retry.decorateSupplier(retry, () -> getResponse(secretManagerClient.getGlobalSecretManager(accountIdentifier)));
    SecretManagerConfigDTO globalSecretManagerFromCG = retryingSecretManagerConfigDTOSupplier.get();
    log.info("[GetGlobalSecretManagerFromCGWithRetry]: Got back global secret manager from CG {} for account: {}",
        globalSecretManagerFromCG, accountIdentifier);
    return globalSecretManagerFromCG;
  }

  @Override
  public SecretManagerMetadataDTO getMetadata(
      @NotNull String accountIdentifier, SecretManagerMetadataRequestDTO requestDTO) {
    return ngVaultService.getListOfEngines(accountIdentifier, requestDTO);
  }

  @Override
  public ConnectorValidationResult validate(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String identifier) {
    ConnectivityStatus connectivityStatus = ConnectivityStatus.FAILURE;
    SecretManagerConfigDTO secretManagerConfigDTO = null;
    try {
      secretManagerConfigDTO = ngConnectorSecretManagerService.getUsingIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
      if (validateNGSecretManager(accountIdentifier, secretManagerConfigDTO)) {
        connectivityStatus = ConnectivityStatus.SUCCESS;
      }
    } catch (WingsException wingsException) {
      throw wingsException;
    } catch (Exception exception) {
      log.error("An error occurred when attempting to obtain a Connector. False validation.", exception);
      throw exception;
    }
    return ConnectorValidationResult.builder().status(connectivityStatus).build();
  }

  @Override
  public boolean deleteSecretManager(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String identifier) {
    return getResponse(
        secretManagerClient.deleteSecretManager(identifier, accountIdentifier, orgIdentifier, projectIdentifier));
  }
}
