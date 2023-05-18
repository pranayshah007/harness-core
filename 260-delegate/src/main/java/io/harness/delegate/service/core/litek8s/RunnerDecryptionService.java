/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import io.harness.delegate.core.beans.Secret;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class RunnerDecryptionService {
  private final DelegateDecryptionService decryptionService;
  @Named("referenceFalseKryoSerializer") private final KryoSerializer kryoSerializer; // TODO: remove named

  public Map<String, char[]> decrypt(final Secret secret) {
    final EncryptionConfig secretConfig =
        (EncryptionConfig) kryoSerializer.asInflatedObject(secret.getConfig().getBinaryData().toByteArray());
    final List<EncryptedRecord> kryoSecrets =
        (List<EncryptedRecord>) kryoSerializer.asInflatedObject(secret.getSecrets().getBinaryData().toByteArray());
    final var decrypt = decryptionService.decrypt(Map.of(secretConfig, kryoSecrets));
    log.info("Decrypted secrets are: {}", decrypt);
    return decrypt;
  }
}
