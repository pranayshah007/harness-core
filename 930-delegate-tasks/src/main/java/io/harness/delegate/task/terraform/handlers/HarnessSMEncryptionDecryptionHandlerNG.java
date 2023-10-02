/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.handlers;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.encoding.EncodingUtils.decodeBase64;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptedRecord;
import io.harness.beans.DelegateFileEncryptedDataPackage;
import io.harness.beans.DelegateFileEncryptedRecordDataPackage;
import io.harness.beans.DelegateFileMetadata;
import io.harness.beans.EncryptData;
import io.harness.beans.EncryptedSMData;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.terraform.mappers.EncryptedRecordDataToEncryptedSMDataMapper;
import io.harness.managerclient.DelegateManagerEncryptionDecryptionHarnessSMClient;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(PL)
@Slf4j
public class HarnessSMEncryptionDecryptionHandlerNG {
  @Inject protected DelegateFileManagerBase delegateFileManager;
  @Inject private DelegateManagerEncryptionDecryptionHarnessSMClient delegateManagerEncryptionDecryptionHarnessSMClient;
  public static final String ON_FILE_STORAGE = "onFileStorage";

  public EncryptedRecord encryptContent(byte[] content, EncryptionConfig config) throws IOException {
    long startTime = Instant.now().getEpochSecond();
    log.info("Making api call for encryption of TF plan for Harness SM");
    EncryptedRecordData record;
    try {
      EncryptData encryptData = EncryptData.builder().content(content).build();
      record = execute(delegateManagerEncryptionDecryptionHarnessSMClient.encryptHarnessSMSecretNG(
                           config.getAccountId(), encryptData))
                   .getResource();

    } catch (Exception e) {
      log.error(format("Not able to encrypt harness SM secrets: %s", e.getMessage()));
      throw e;
    }
    long endTime = Instant.now().getEpochSecond();
    log.info(format("Encryption of TF plan completed with total time taken in seconds : %s", endTime - startTime));

    record.setUuid(generateUuid());
    return record;
  }

  public EncryptedRecord encryptFile(byte[] content, EncryptionConfig config, DelegateFile delegateFile)
      throws IOException {
    return encryptContentWithFileUpload(content, config, delegateFile);
  }

  private EncryptedRecord encryptContentWithFileUpload(
      byte[] content, EncryptionConfig config, DelegateFile delegateFile) throws IOException {
    long startTime = Instant.now().getEpochSecond();
    log.info("Making api call for encryption of TF plan for Harness SM with file upload");
    EncryptedRecordData record;
    try {
      EncryptData encryptData = EncryptData.builder().content(content).build();
      DelegateFileMetadata delegateFileMetadata = DelegateFileMetadata.builder()
                                                      .accountId(delegateFile.getAccountId())
                                                      .bucket(delegateFile.getBucket())
                                                      .fileName(delegateFile.getFileName())
                                                      .build();
      DelegateFileEncryptedDataPackage delegateFileEncryptedDataPackage = DelegateFileEncryptedDataPackage.builder()
                                                                              .delegateFile(delegateFileMetadata)
                                                                              .encryptData(encryptData)
                                                                              .build();
      DelegateFileEncryptedRecordDataPackage delegateFileEncryptedRecordDataPackage =
          execute(delegateManagerEncryptionDecryptionHarnessSMClient.encryptHarnessSMSecretNGWithFileUpload(
                      config.getAccountId(), delegateFileEncryptedDataPackage))
              .getResource();
      record = delegateFileEncryptedRecordDataPackage.getEncryptedRecordData();
      delegateFile.setFileId(delegateFileEncryptedRecordDataPackage.getDelegateFileId());
    } catch (Exception e) {
      log.error(format("Not able to encrypt harness SM secrets: %s", e.getMessage()));
      throw e;
    }
    long endTime = Instant.now().getEpochSecond();
    log.info(format("Encryption of TF plan completed with total time taken in seconds : %s", endTime - startTime));

    record.setUuid(generateUuid());
    return record;
  }

  public byte[] getDecryptedContent(EncryptionConfig config, EncryptedRecordData record) throws IOException {
    char[] decryptedContent = decryptDataInManager(record, config).getDecryptedValue();
    return decodeBase64(decryptedContent);
  }

  public byte[] getDecryptedContent(EncryptionConfig config, EncryptedRecordData record, String accountId)
      throws IOException {
    if (record.getAdditionalMetadata() != null
        && record.getAdditionalMetadata().getValues().get(ON_FILE_STORAGE).equals(TRUE)) {
      String fileId = new String(record.getEncryptedValue());
      InputStream inputStream = delegateFileManager.downloadByFileId(FileBucket.TERRAFORM_PLAN, fileId, accountId);
      record.setEncryptedValue(IOUtils.toCharArray(inputStream, Charsets.UTF_8));
      byte[] decryptedContent = getDecryptedContent(config, record);
      record.setEncryptedValue(fileId.toCharArray());
      return decryptedContent;
    } else {
      return getDecryptedContent(config, record);
    }
  }

  private DecryptedRecord decryptDataInManager(EncryptedRecordData record, EncryptionConfig config) throws IOException {
    log.info("Making api call for decryption of TF plan for Harness SM");
    long startTime = Instant.now().getEpochSecond();
    DecryptedRecord decryptedRecord;

    try {
      EncryptedSMData encryptedSMData = EncryptedRecordDataToEncryptedSMDataMapper.toEncryptedSMData(record);
      decryptedRecord = execute(delegateManagerEncryptionDecryptionHarnessSMClient.decryptHarnessSMSecretNG(
                                    config.getAccountId(), encryptedSMData))
                            .getResource();
    } catch (Exception e) {
      log.error(format("Not able to decrypt harness SM secrets : %s", e.getMessage()));
      throw e;
    }
    long endTime = Instant.now().getEpochSecond();
    log.info(format("Encryption of TF plan completed with total time taken in seconds : %s", endTime - startTime));
    return decryptedRecord;
  }
}
