/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.secret;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.core.beans.EncryptedDataRecord;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@OwnedBy(PL)
@Slf4j
public class EncryptedDataRecordProtoPojoMapper {
  public static EncryptedRecordData map(final EncryptedDataRecord data) {
    return EncryptedRecordData.builder()
        .uuid(data.getUuid())
        .name(data.getName())
        .path(data.getPath())
        .encryptedValue(new String(data.getEncryptedValue().toByteArray(), StandardCharsets.UTF_8).toCharArray())
        .parameters(
            data.getParamsList()
                .stream()
                .map(params -> EncryptedDataParams.builder().name(params.getName()).value(params.getValue()).build())
                .collect(Collectors.toSet()))
        .encryptionKey(data.getEncryptionKey())
        .encryptionType(EncryptionTypeProtoPojoMapper.map(data.getEncryptionType()))
        .kmsId(data.getKmsId())
        .backupEncryptionKey(data.getBackupEncryptionKey())
        .backupEncryptionType(EncryptionTypeProtoPojoMapper.map(data.getBackupEncryptionType()))
        .backupEncryptedValue(
            new String(data.getBackupEncryptedValue().toByteArray(), StandardCharsets.UTF_8).toCharArray())
        .backupKmsId(data.getBackupKmsId())
        .base64Encoded(data.getBase64Encoded())
        .additionalMetadata(AdditionalMetadata.builder().values(data.getAdditionalMetadataMap()).build())
        .build();
  }
}
