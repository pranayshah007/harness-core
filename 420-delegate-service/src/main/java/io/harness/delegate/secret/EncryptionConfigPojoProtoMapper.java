/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.EncryptionConfig;
import io.harness.delegate.core.beans.SecretManagerType;
import io.harness.security.encryption.EncryptionType;

import java.util.Objects;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class EncryptionConfigPojoProtoMapper {
  public static EncryptionConfig map(final io.harness.security.encryption.EncryptionConfig config) {
    // TODO: After implementing all encryption types, remove this check
    if (!config.getEncryptionType().equals(EncryptionType.LOCAL)) {
      log.warn("Encryption type {} not implemented", config.getEncryptionType().name());
      return null;
    }
    return EncryptionConfig.newBuilder()
        .setUuid(Objects.toString(config.getUuid(), ""))
        .setAccountId(Objects.toString(config.getAccountId(), ""))
        .setName(Objects.toString(config.getName(), ""))
        .setIsGloblKms(config.isGlobalKms())
        .setEncryptionType(EncryptionTypePojoProtoMapper.map(config.getEncryptionType()))
        .setSecretManagerType(SecretManagerTypePojoProtoMapper.map(config.getType()))
        .setEncryptionServiceUrl(Objects.toString(config.getEncryptionServiceUrl(), ""))
        .build();
  }
}
