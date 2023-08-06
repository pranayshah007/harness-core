/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.encodeBase64ToByteArray;
import static io.harness.helpers.EncryptDecryptHelperImpl.ON_FILE_STORAGE;
import static io.harness.security.SimpleEncryption.CHARSET;

import static java.lang.Boolean.TRUE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.FileMetadata;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.DelegateFileEncryptedRecordDataPackage;
import software.wings.beans.DecryptedRecord;
import software.wings.beans.DelegateFileMetadata;
import software.wings.service.intfc.DelegateManagerEncryptionDecryptionHarnessSMService;
import software.wings.service.intfc.FileService;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReaderInputStream;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(CDP)
@Slf4j
public class DelegateManagerEncryptionDecryptionHarnessSMServiceImpl
    implements DelegateManagerEncryptionDecryptionHarnessSMService {
  @Inject private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private FileService fileService;

  public static final String HARNESS_SECRET_MANAGER_IDENTIFIER = "harnessSecretManager";

  @Override
  public EncryptedRecordData encryptData(String accountId, byte[] content) {
    String value = new String(CHARSET.decode(ByteBuffer.wrap(encodeBase64ToByteArray(content))).array());
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getGlobalSecretManager(accountId);
    return (EncryptedRecordData) encryptKmsSecret(value, secretManagerConfig);
  }

  @Override
  public DecryptedRecord decryptData(String accountId, EncryptedRecordData encryptedRecord) {
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getGlobalSecretManager(accountId);
    return DecryptedRecord.builder().decryptedValue(decryptSecretValue(encryptedRecord, secretManagerConfig)).build();
  }

  @Override
  public DelegateFileEncryptedRecordDataPackage encryptDataWithFileUpload(
      String accountId, byte[] content, DelegateFileMetadata delegateFile) throws IOException {
    String value = new String(CHARSET.decode(ByteBuffer.wrap(encodeBase64ToByteArray(content))).array());
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getGlobalSecretManager(accountId);
    EncryptedRecordData encryptedRecordData = (EncryptedRecordData) encryptKmsSecret(value, secretManagerConfig);

    String fileId = uploadEncryptedValueToFileStorage(encryptedRecordData, delegateFile);

    encryptedRecordData.setEncryptedValue(fileId.toCharArray());
    encryptedRecordData.setAdditionalMetadata(AdditionalMetadata.builder().value(ON_FILE_STORAGE, TRUE).build());

    return DelegateFileEncryptedRecordDataPackage.builder()
        .encryptedRecordData(encryptedRecordData)
        .delegateFileId(fileId)
        .build();
  }

  private EncryptedRecord encryptKmsSecret(String value, EncryptionConfig encryptionConfig) {
    KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(encryptionConfig);
    return kmsEncryptor.encryptSecret(encryptionConfig.getAccountId(), value, encryptionConfig);
  }

  private char[] decryptSecretValue(EncryptedRecordData encryptedRecord, EncryptionConfig config) {
    KmsEncryptor kmsEncryptor = kmsEncryptorsRegistry.getKmsEncryptor(config);
    return kmsEncryptor.fetchSecretValue(config.getAccountId(), encryptedRecord, config);
  }

  private String uploadEncryptedValueToFileStorage(
      EncryptedRecordData encryptedRecordData, DelegateFileMetadata delegateFile) throws IOException {
    CharArrayReader charArrayReader = new CharArrayReader(encryptedRecordData.getEncryptedValue());
    try (InputStream inputStream = new ReaderInputStream(charArrayReader, Charsets.UTF_8)) {
      return upload(delegateFile, inputStream);
    }
  }

  private String upload(DelegateFileMetadata delegateFile, InputStream inputStream) {
    FileMetadata fileMetadata = FileMetadata.builder()
                                    .fileName(delegateFile.getFileName())
                                    .accountId(delegateFile.getAccountId())
                                    .fileUuid(UUIDGenerator.generateUuid())
                                    .build();
    String fileId = fileService.saveFile(fileMetadata, inputStream, delegateFile.getBucket());
    log.info("fileId: {} and fileName {}", fileId, fileMetadata.getFileName());
    return fileId;
  }
}
