/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.secret;

import io.harness.delegate.core.beans.EncryptedDataRecord;
import io.harness.delegate.core.beans.Secret;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class RunnerDecryptionService {
  private final DelegateDecryptionService decryptionService;
  @Named("referenceFalseKryoSerializer") private final KryoSerializer kryoSerializer; // TODO: remove named

  @Deprecated
  public Map<String, char[]> decrypt(final Secret secret) {
    final EncryptionConfig secretConfig =
        (EncryptionConfig) kryoSerializer.asInflatedObject(secret.getConfig().getBinaryData().toByteArray());
    final List<EncryptedRecord> kryoSecrets = (List<EncryptedRecord>) kryoSerializer.asInflatedObject(
        secret.getEncryptedRecord().getBinaryData().toByteArray());
    final var decrypt = decryptionService.decrypt(Map.of(secretConfig, kryoSecrets));
    log.info("Decrypted secrets are: {}", decrypt);
    return decrypt;
  }

  /**
   * Decrypts a secret described by Secret object.
   * @param secret A protobuf definition of Secret to be decrypted. Refer to
   *               955-delegate-beans/src/main/proto/io/harness/delegate/core/beans/secret.proto
   * @return decrypted char value. Null if decryption fails
   * @throws InvalidProtocolBufferException
   */
  public char[] decryptProtoBytes(final Secret secret) throws InvalidProtocolBufferException {
    final EncryptedDataRecord encryptedDataRecord =
        EncryptedDataRecord.parseFrom(secret.getEncryptedRecord().getBinaryData().toByteArray());
    EncryptedRecordData mappedRecordData = EncryptedDataRecordProtoPojoMapper.map(encryptedDataRecord);
    final io.harness.delegate.core.beans.EncryptionConfig encryptionConfig =
        io.harness.delegate.core.beans.EncryptionConfig.parseFrom(secret.getConfig().getBinaryData().toByteArray());
    EncryptionConfig mappedEncryptionConfig = EncryptionConfigProtoPojoMapper.map(encryptionConfig);
    List<EncryptedRecord> encryptedRecordList = new ArrayList<>();
    encryptedRecordList.add(mappedRecordData);
    assert mappedEncryptionConfig != null;
    // DelegateDecryptionService doesn't use cache
    final var decrypt = decryptionService.decrypt(Map.of(mappedEncryptionConfig, encryptedRecordList));
    if (!decrypt.containsKey(mappedRecordData.getUuid())) {
      log.error("After decryption, cannot find decrypted secret for encryption record {}", mappedRecordData.getUuid());
      return null;
    } else {
      return decrypt.get(mappedRecordData.getUuid());
    }
  }
}
