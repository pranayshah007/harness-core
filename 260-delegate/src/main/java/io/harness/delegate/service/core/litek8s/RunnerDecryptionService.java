/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import io.harness.delegate.core.beans.TaskSecret;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RunnerDecryptionService {
  private final DelegateDecryptionService decryptionService;
  @Named("referenceFalseKryoSerializer") private final KryoSerializer kryoSerializer; // TODO: remove named

  public Map<String, char[]> decrypt(final TaskSecret secret) {
    final var kryoSecrets = (Map<EncryptionConfig, List<EncryptedRecord>>) kryoSerializer.asObject(secret.getSecrets().getBinaryData().toByteArray());
    return decryptionService.decrypt(kryoSecrets);
  }
}
