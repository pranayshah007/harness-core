/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.secret;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.core.beans.EncryptionType;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.LocalEncryptionConfig;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@OwnedBy(PL)
@Slf4j
public class EncryptionConfigProtoPojoMapper {
  public static EncryptionConfig map(final io.harness.delegate.core.beans.EncryptionConfig config) {
    // TODO: After implementing all encryption types, remove this check
    if (!config.getEncryptionType().equals(EncryptionType.LOCAL)) {
      log.warn("Encryption type {} not implemented", config.getEncryptionType().name());
      return null;
    }
    return LocalEncryptionConfig.builder()
        .uuid(config.getUuid())
        .accountId(config.getAccountId())
        .name(config.getName())
        .encryptionType(io.harness.security.encryption.EncryptionType.LOCAL)
        .build();
  }
}
