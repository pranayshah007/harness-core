/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import com.google.protobuf.ByteString;
import io.harness.delegate.core.beans.EncryptedDataParams;
import io.harness.delegate.core.beans.EncryptedDataRecord;
import io.harness.delegate.core.beans.EncryptionType;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecordData;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class EncryptedDataRecordPojoProtoMapper {
    public static EncryptedDataRecord map(final EncryptedRecordData data) {
        return EncryptedDataRecord.newBuilder()
                .setUuid(data.getUuid())
                .setName(data.getName())
                .setPath(data.getPath())
                .addAllParams(data.getParameters().stream().map(params ->
                        EncryptedDataParams.newBuilder()
                                .setName(params.getName())
                                .setValue(params.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .setEncryptionKey(data.getEncryptionKey())
                .setEncryptionType(EncryptionType.valueOf(data.getEncryptionType().name()))
                .setKmsId(data.getKmsId())
                .setBackupEncryptionKey(data.getBackupEncryptionKey())
                .setBackupEncryptedValue(
                        ByteString.copyFrom(
                                new String(data.getBackupEncryptedValue()).getBytes(StandardCharsets.UTF_8)))
                .setBackupKmsId(data.getBackupKmsId())
                .setBackupEncryptionType(EncryptionType.valueOf(data.getBackupEncryptionType().name()))
                .setBase64Encoded(data.isBase64Encoded())
                .putAllAdditionalMetadata(mapAdditionalMetadata(data.getAdditionalMetadata()))
                .build();
    }

    /**
     * Converts AdditionalMetadata to Map<String, String>. It uses toString to convert an Object to String
     * @param metadata
     * @return
     */
    private static Map<String, String> mapAdditionalMetadata(AdditionalMetadata metadata) {
        var data = metadata.getValues();
        Map<String, String> ret = new HashMap<>();
        data.entrySet().forEach(
                stringObjectEntry -> ret.put(stringObjectEntry.getKey(), stringObjectEntry.getValue().toString()));
        return ret;
    }
}
