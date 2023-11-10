/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.InputData;
import io.harness.delegate.core.beans.Secret;
import io.harness.delegate.core.beans.SecretConfig;
import io.harness.delegate.core.beans.SecretRef;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.protobuf.ByteString;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EncryptedDataDetailToSecretMapper {
  public Secret map(String scopedSecretId, EncryptedDataDetail dataDetail) {
    return Secret.newBuilder()
        .setEncryptedRecord(
            InputData.newBuilder()
                .setBinaryData(ByteString.copyFrom(
                    EncryptedDataRecordPojoProtoMapper.INSTANCE.map(dataDetail.getEncryptedData()).toByteArray()))
                .build())
        .setConfig(
            SecretConfig.newBuilder()
                .setBinaryData(ByteString.copyFrom(
                    EncryptionConfigPojoProtoMapper.INSTANCE.map(dataDetail.getEncryptionConfig()).toByteArray()))
                .build())
        .setSecretRef(SecretRef.newBuilder().setScopedSecretId(scopedSecretId).build())
        .build();
  }
}
