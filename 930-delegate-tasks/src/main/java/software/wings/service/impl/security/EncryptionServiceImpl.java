/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.encryption.EncryptionReflectUtils.getEncryptedRefField;
import static io.harness.eraro.ErrorCode.ENCRYPT_DECRYPT_ERROR;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.reflection.ReflectionUtils.getFieldByName;
import static io.harness.security.SimpleEncryption.CHARSET;
import static io.harness.security.encryption.SecretManagerType.CUSTOM;
import static io.harness.security.encryption.SecretManagerType.KMS;
import static io.harness.security.encryption.SecretManagerType.VAULT;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.exception.DelegateRetryableException;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.exception.SecretManagementException;
import io.harness.secrets.SecretsDelegateCacheService;
import io.harness.security.encryption.EncryptableSettingWithEncryptionDetails;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 10/18/17.
 */
@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EncryptionServiceImpl implements EncryptionService {
  private final ExecutorService threadPoolExecutor;
  private final VaultEncryptorsRegistry vaultEncryptorsRegistry;
  private final KmsEncryptorsRegistry kmsEncryptorsRegistry;
  private final CustomEncryptorsRegistry customEncryptorsRegistry;
  private final SecretsDelegateCacheService secretsDelegateCacheService;

  @Inject
  public EncryptionServiceImpl(VaultEncryptorsRegistry vaultEncryptorsRegistry,
      KmsEncryptorsRegistry kmsEncryptorsRegistry, CustomEncryptorsRegistry customEncryptorsRegistry,
      @Named("asyncExecutor") ExecutorService threadPoolExecutor,
      SecretsDelegateCacheService secretsDelegateCacheService) {
    this.threadPoolExecutor = threadPoolExecutor;
    this.vaultEncryptorsRegistry = vaultEncryptorsRegistry;
    this.kmsEncryptorsRegistry = kmsEncryptorsRegistry;
    this.customEncryptorsRegistry = customEncryptorsRegistry;
    this.secretsDelegateCacheService = secretsDelegateCacheService;
  }

  @Override
  public EncryptableSetting decrypt(
      EncryptableSetting object, List<EncryptedDataDetail> encryptedDataDetails, boolean fromCache) {
    log.debug("Decrypting a secret");
    if (object.isDecrypted() || isEmpty(encryptedDataDetails)) {
      return object;
    }

    for (EncryptedDataDetail encryptedDataDetail : encryptedDataDetails) {
      try {
        char[] decryptedValue;

        Field f = getFieldByName(object.getClass(), encryptedDataDetail.getFieldName());
        if (f == null) {
          log.warn("Could not find field {} in class {}", encryptedDataDetail.getFieldName(), object.getClass());
          continue;
        }
        Preconditions.checkNotNull(f, "could not find " + encryptedDataDetail.getFieldName() + " in " + object);
        f.setAccessible(true);

        decryptedValue = getDecryptedValue(encryptedDataDetail, fromCache);
        f.set(object, decryptedValue);
        Field encryptedRefField = getEncryptedRefField(f, object);
        encryptedRefField.setAccessible(true);
        encryptedRefField.set(object, null);
      } catch (DelegateRetryableException | SecretManagementDelegateException e) {
        throw e;
      } catch (Exception e) {
        // Log the root cause exception of failed decryption attempts.
        log.error("Failed to decrypt encrypted settings.", e);
        throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, ExceptionUtils.getMessage(e), USER);
      }
    }
    object.setDecrypted(true);
    return object;
  }

  @Override
  public List<EncryptableSettingWithEncryptionDetails> decrypt(
      List<EncryptableSettingWithEncryptionDetails> encryptableSettingWithEncryptionDetailsList, boolean fromCache) {
    List<Future<EncryptableSetting>> futures = new ArrayList<>();
    for (EncryptableSettingWithEncryptionDetails encryptableSettingWithEncryptionDetails :
        encryptableSettingWithEncryptionDetailsList) {
      EncryptableSetting encryptableSetting = encryptableSettingWithEncryptionDetails.getEncryptableSetting();
      futures.add(threadPoolExecutor.submit(
          ()
              -> decrypt(
                  encryptableSetting, encryptableSettingWithEncryptionDetails.getEncryptedDataDetails(), fromCache)));
    }

    for (int i = 0; i < encryptableSettingWithEncryptionDetailsList.size(); i++) {
      try {
        EncryptableSetting encryptableSetting = futures.get(i).get();
        encryptableSettingWithEncryptionDetailsList.get(i).setEncryptableSetting(encryptableSetting);
      } catch (ExecutionException e) {
        log.error("Failed to batch process decryption request of encrypted settings.", e.getCause());
        throw new SecretManagementException(
            ENCRYPT_DECRYPT_ERROR, "Failed to batch process decryption request of encrypted settings", USER);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Thread was interrupted during execution", e);
        throw new SecretManagementException(
            ENCRYPT_DECRYPT_ERROR, "Failed to batch process decryption request of encrypted settings", USER);
      }
    }

    return encryptableSettingWithEncryptionDetailsList;
  }

  @Override
  public char[] getDecryptedValue(EncryptedDataDetail encryptedDataDetail, boolean fromCache) {
    if (fromCache && encryptedDataDetail.getEncryptionConfig().getType() == CUSTOM) {
      return secretsDelegateCacheService.get(encryptedDataDetail.getIdentifier(),
          secretUniqueIdentifier -> getDecryptedValueInternal(encryptedDataDetail));
    }
    char[] value = getDecryptedValueInternal(encryptedDataDetail);
    if (isNotEmpty(value)) {
      secretsDelegateCacheService.put(encryptedDataDetail.getIdentifier(), value);
    }
    return value;
  }

  private char[] getDecryptedValueInternal(EncryptedDataDetail encryptedDataDetail) {
    EncryptedRecord record = encryptedDataDetail.getEncryptedData();
    EncryptionConfig config = encryptedDataDetail.getEncryptionConfig();
    char[] decryptedValue;

    if (config.getType().equals(KMS)) {
      KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(config);
      decryptedValue = kmsEncryptor.fetchSecretValue(config.getAccountId(), record, config);
    } else if (config.getType().equals(VAULT)) {
      VaultEncryptor vaultEncryptor = vaultEncryptorsRegistry.getVaultEncryptor(config.getEncryptionType());
      decryptedValue = vaultEncryptor.fetchSecretValue(config.getAccountId(), record, config);
    } else if (config.getType().equals(CUSTOM)) {
      log.info("CUSTOM_SECRET_MANAGER: Getting secret from secret manager for secretId {}",
          encryptedDataDetail.getEncryptedData().getUuid());
      CustomEncryptor customEncryptor = customEncryptorsRegistry.getCustomEncryptor(config.getEncryptionType());
      decryptedValue = customEncryptor.fetchSecretValue(config.getAccountId(), record, config);
    } else {
      throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR,
          String.format("Encryptor for fetch secret task for encryption config %s not configured", config.getName()),
          USER);
    }
    if (decryptedValue != null && encryptedDataDetail.getEncryptedData().isBase64Encoded()) {
      byte[] decodedBytes = EncodingUtils.decodeBase64(decryptedValue);
      decryptedValue = CHARSET.decode(ByteBuffer.wrap(decodedBytes)).array();
    }
    return decryptedValue;
  }
}
