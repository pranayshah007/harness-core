/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.secret;

import io.harness.delegate.core.beans.EncryptionType;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EncryptionTypeProtoPojoMapper {
  public static io.harness.security.encryption.EncryptionType map(EncryptionType type) {
    switch (type) {
      case AWS_KMS:
        return io.harness.security.encryption.EncryptionType.KMS;
      case HASHICORP_VAULT:
        return io.harness.security.encryption.EncryptionType.VAULT;
      case NOT_SET:
      case UNRECOGNIZED:
      case UNKNOWN:
        return null;
      default:
        return io.harness.security.encryption.EncryptionType.valueOf(type.name());
    }
  }
}
