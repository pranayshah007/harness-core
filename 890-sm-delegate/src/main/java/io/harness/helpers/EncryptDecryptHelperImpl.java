/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers;

import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.encoding.EncodingUtils.encodeBase64ToByteArray;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.security.SimpleEncryption.CHARSET;
import static io.harness.security.encryption.SecretManagerType.KMS;
import static io.harness.security.encryption.SecretManagerType.VAULT;

import static java.lang.Boolean.TRUE;

import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;

@Singleton
@Slf4j
public class EncryptDecryptHelperImpl implements EncryptDecryptHelper {
  @Inject protected KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Inject protected VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Inject protected DelegateFileManagerBase delegateFileManager;

  public static final String ON_FILE_STORAGE = "onFileStorage";

  @Override
  public EncryptedRecord encryptContent(byte[] content, String name, EncryptionConfig config) {
    String value = new String(CHARSET.decode(ByteBuffer.wrap(encodeBase64ToByteArray(content))).array());
    EncryptedRecordData record;
    if (KMS == config.getType()) {
      record = (EncryptedRecordData) encryptKmsSecret(value, config);
    } else if (VAULT == config.getType()) {
      record = (EncryptedRecordData) encryptVaultSecret(name, value, config);
    } else {
      throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR,
          String.format("Encryptor for fetch secret task for encryption config %s not configured", config.getName()),
          USER);
    }
    record.setUuid(generateUuid());
    return record;
  }

  @Override
  public EncryptedRecord encryptFile(byte[] content, String name, EncryptionConfig config, DelegateFile delegateFile)
      throws IOException {
    EncryptedRecord encryptedRecord = encryptContent(content, name, config);
    if (KMS == config.getType()) {
      uploadEncryptedValueToFileStorage((EncryptedRecordData) encryptedRecord, delegateFile);
    }
    return encryptedRecord;
  }

  private EncryptedRecord encryptKmsSecret(String value, EncryptionConfig encryptionConfig) {
    KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(encryptionConfig);
    return kmsEncryptor.encryptSecret(encryptionConfig.getAccountId(), value, encryptionConfig);
  }

  private EncryptedRecord encryptVaultSecret(String name, String value, EncryptionConfig encryptionConfig) {
    VaultEncryptor vaultEncryptor = vaultEncryptorsRegistry.getVaultEncryptor(encryptionConfig.getEncryptionType());
    return vaultEncryptor.createSecret(encryptionConfig.getAccountId(), name, value, encryptionConfig);
  }

  @Override
  public byte[] getDecryptedContent(EncryptionConfig config, EncryptedRecord record) {
    char[] decryptedContent;
    if (KMS == config.getType()) {
      decryptedContent = fetchKmsSecretValue(record, config);
    } else if (VAULT == config.getType()) {
      decryptedContent = fetchVaultSecretValue(record, config);
    } else {
      throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR,
          String.format("Encryptor for fetch secret task for encryption config %s not configured", config.getName()),
          USER);
    }
    return decodeBase64(decryptedContent);
  }

  @Override
  public byte[] getDecryptedContent(EncryptionConfig config, EncryptedRecord record, String accountId)
      throws IOException {
    if (record.getAdditionalMetadata() != null
        && record.getAdditionalMetadata().getValues().get(ON_FILE_STORAGE).equals(TRUE)) {
      String fileId = new String(record.getEncryptedValue());
      InputStream inputStream = delegateFileManager.downloadByFileId(FileBucket.TERRAFORM_PLAN, fileId, accountId);
      ((EncryptedRecordData) record).setEncryptedValue(IOUtils.toCharArray(inputStream, Charsets.UTF_8));
      byte[] decryptedContent = getDecryptedContent(config, record);
      ((EncryptedRecordData) record).setEncryptedValue(fileId.toCharArray());
      return decryptedContent;
    } else {
      return getDecryptedContent(config, record);
    }
  }

  private char[] fetchKmsSecretValue(EncryptedRecord record, EncryptionConfig config) {
    KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(config);
    return kmsEncryptor.fetchSecretValue(config.getAccountId(), record, config);
  }

  private char[] fetchVaultSecretValue(EncryptedRecord record, EncryptionConfig config) {
    VaultEncryptor vaultEncryptor = vaultEncryptorsRegistry.getVaultEncryptor(config.getEncryptionType());
    return vaultEncryptor.fetchSecretValue(config.getAccountId(), record, config);
  }

  @Override
  public boolean deleteEncryptedRecord(EncryptionConfig encryptionConfig, EncryptedRecord record) {
    // Only for Vault type Secret Manager, Plan is saved in Secret Manager
    if (VAULT == encryptionConfig.getType()) {
      VaultEncryptor vaultEncryptor = vaultEncryptorsRegistry.getVaultEncryptor(encryptionConfig.getEncryptionType());
      return vaultEncryptor.deleteSecret(encryptionConfig.getAccountId(), record, encryptionConfig);
    }
    return false;
  }

  private void uploadEncryptedValueToFileStorage(EncryptedRecordData encryptedRecordData, DelegateFile delegateFile)
      throws IOException {
    CharArrayReader charArrayReader = new CharArrayReader(encryptedRecordData.getEncryptedValue());
    try (InputStream inputStream = new ReaderInputStream(charArrayReader, Charsets.UTF_8)) {
      delegateFileManager.upload(delegateFile, inputStream);
    }
    encryptedRecordData.setEncryptedValue(delegateFile.getFileId().toCharArray());
    encryptedRecordData.setAdditionalMetadata(AdditionalMetadata.builder().value(ON_FILE_STORAGE, TRUE).build());
  }
}
