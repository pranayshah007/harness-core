/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.EncryptedDataParams;
import io.harness.delegate.core.beans.EncryptedDataRecord;
import io.harness.delegate.core.beans.EncryptionType;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecordData;

import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EncryptedDataRecordPojoProtoMapper {
  public static EncryptedDataRecord map(final EncryptedRecordData data) {
    var builder = EncryptedDataRecord.newBuilder()
                      .setUuid(data.getUuid())
                      .setName(Objects.toString(data.getName(), ""))
                      .setPath(Objects.toString(data.getPath(), ""))
                      .setEncryptionKey(Objects.toString(data.getEncryptionKey(), ""))
                      .setEncryptionType(EncryptionTypePojoProtoMapper.map(data.getEncryptionType()))
                      .setKmsId(Objects.toString(data.getKmsId(), ""))
                      .setBackupEncryptionKey(Objects.toString(data.getBackupEncryptionKey(), ""))
                      .setBackupKmsId(Objects.toString(data.getBackupKmsId(), ""))
                      .setBackupEncryptionType(EncryptionTypePojoProtoMapper.map(data.getBackupEncryptionType()))
                      .setBase64Encoded(data.isBase64Encoded())
                      .putAllAdditionalMetadata(mapAdditionalMetadata(data.getAdditionalMetadata()));
    if (Objects.nonNull(data.getParameters())) {
      builder.addAllParams(data.getParameters()
                               .stream()
                               .map(params
                                   -> EncryptedDataParams.newBuilder()
                                          .setName(Objects.toString(params.getName(), ""))
                                          .setValue(Objects.toString(params.getValue(), ""))
                                          .build())
                               .collect(Collectors.toList()));
    }
    if (Objects.nonNull(data.getEncryptedValue())) {
      builder.setEncryptedValue(
          ByteString.copyFrom(new String(data.getEncryptedValue()).getBytes(StandardCharsets.UTF_8)));
    }
    if (Objects.nonNull(data.getBackupEncryptedValue())) {
      builder.setBackupEncryptedValue(
          ByteString.copyFrom(new String(data.getBackupEncryptedValue()).getBytes(StandardCharsets.UTF_8)));
    }
    return builder.build();
  }

  /**
   * Converts AdditionalMetadata to Map<String, String>. It uses toString to convert an Object to String
   * @param metadata
   * @return
   */
  private static Map<String, String> mapAdditionalMetadata(AdditionalMetadata metadata) {
    if (Objects.isNull(metadata)) {
      return Map.of();
    }
    var data = metadata.getValues();
    Map<String, String> ret = new HashMap<>();
    data.entrySet().forEach(
        stringObjectEntry -> ret.put(stringObjectEntry.getKey(), stringObjectEntry.getValue().toString()));
    return ret;
  }
}
