/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.SecretManagerType;

import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretManagerTypePojoProtoMapper {
  public static SecretManagerType map(io.harness.security.encryption.SecretManagerType type) {
    if (Objects.isNull(type)) {
      return SecretManagerType.SM_NOT_SET;
    }
    return SecretManagerType.valueOf(type.name());
  }
}
