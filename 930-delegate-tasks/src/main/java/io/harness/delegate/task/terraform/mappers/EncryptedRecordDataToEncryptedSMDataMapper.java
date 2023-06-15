/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedSMData;
import io.harness.security.encryption.EncryptedRecordData;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class EncryptedRecordDataToEncryptedSMDataMapper {
  public EncryptedSMData toEncryptedSMData(EncryptedRecordData encryptedRecordData) {
    return EncryptedSMData.builder()
        .uuid(encryptedRecordData.getUuid())
        .name(encryptedRecordData.getName())
        .path(encryptedRecordData.getPath())
        .parameters(encryptedRecordData.getParameters())
        .backupEncryptedValue(encryptedRecordData.getBackupEncryptedValue())
        .encryptedValue(encryptedRecordData.getEncryptedValue())
        .encryptionKey(encryptedRecordData.getEncryptionKey())
        .kmsId(encryptedRecordData.getKmsId())
        .additionalMetadata(encryptedRecordData.getAdditionalMetadata())
        .backupEncryptionKey(encryptedRecordData.getBackupEncryptionKey())
        .backupKmsId(encryptedRecordData.getBackupKmsId())
        .encryptionType(encryptedRecordData.getEncryptionType())
        .backupEncryptionType(encryptedRecordData.getBackupEncryptionType())
        .base64Encoded(encryptedRecordData.isBase64Encoded())
        .build();
  }
}
